package com.example.photosync.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.photosync.data.local.MediaDao
import com.example.photosync.data.local.MediaItemEntity
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.remote.GooglePhotosApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val tokenManager: TokenManager,
    private val api: GooglePhotosApi
) {
    private val TAG = "MediaRepository"

    val allMediaItems = mediaDao.getAllMediaItems()

    suspend fun syncCloudMedia() = withContext(Dispatchers.IO) {
        if (!tokenManager.isCloudSyncEnabled()) {
            Log.d(TAG, "Cloud sync disabled. Skipping cloud media fetch.")
            return@withContext
        }

        val token = tokenManager.getAccessToken()
        if (token.isNullOrBlank()) {
            Log.w(TAG, "Cannot sync cloud media: missing access token.")
            return@withContext
        }

        try {
            var pageToken: String? = null
            var totalFetched = 0

            do {
                val response = api.listMediaItems(
                    token = "Bearer $token",
                    pageSize = 100,
                    pageToken = pageToken
                )
                val remoteItems = response.mediaItems.orEmpty()
                if (remoteItems.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val entities = remoteItems.map { media ->
                        val googleId = media.id
                        val createdAt = parseGoogleDate(media.mediaMetadata?.creationTime)

                        MediaItemEntity(
                            id = "remote:$googleId",
                            fileName = media.filename ?: "Cloud item $googleId",
                            filePath = "",
                            mimeType = media.mimeType ?: "image/jpeg",
                            fileSize = 0L,
                            dateAdded = createdAt,
                            isSynced = true,
                            isLocal = false,
                            remoteUrl = media.baseUrl,
                            googlePhotosId = googleId,
                            lastSyncedAt = now
                        )
                    }

                    mediaDao.insertAll(entities)
                    totalFetched += entities.size
                }

                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrEmpty())

            Log.d(TAG, "Cloud media sync inserted/merged $totalFetched items.")
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401 || e.code() == 403) {
                throw com.example.photosync.auth.ReAuthRequiredException(
                    "REAUTH_REQUIRED: Cloud media access denied (${e.code()})"
                )
            }
            throw e
        }
    }

    private fun parseGoogleDate(dateString: String?): Long {
        if (dateString == null) return System.currentTimeMillis() / 1000
        return try {
            // Format: 2023-10-25T10:00:00Z
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            (sdf.parse(dateString)?.time ?: System.currentTimeMillis()) / 1000
        } catch (e: Exception) {
            System.currentTimeMillis() / 1000
        }
    }

    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        val lastScanTime = tokenManager.getLastScanTime()
        Log.d(TAG, "Starting scanLocalMedia... Last scan: $lastScanTime")
        
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED
        )

        // Scan Images
        scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, lastScanTime)
        
        // Scan Videos
        scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, lastScanTime)
        
        // Update last scan time to now (in seconds)
        tokenManager.saveLastScanTime(System.currentTimeMillis() / 1000)
        Log.d(TAG, "scanLocalMedia completed.")
        Unit
    }

    suspend fun getSyncedLocalItems(): List<MediaItemEntity> {
        return mediaDao.getSyncedLocalItems()
    }

    suspend fun markAsDeletedLocally(ids: List<String>) {
        ids.forEach { id ->
            val item = mediaDao.getById(id) ?: return@forEach

            if (item.isSynced) {
                // Keep record for cloud-backed item, only mark local file removed.
                mediaDao.updateLocalStatus(id, false)
            } else {
                // Not uploaded yet and deleted locally -> remove stale DB entry.
                mediaDao.deleteById(id)
            }
        }
    }
    
    suspend fun updateLocalStatus(id: String, isLocal: Boolean) {
        mediaDao.updateLocalStatus(id, isLocal)
    }

    suspend fun deleteFromDb(id: String) {
        mediaDao.deleteById(id)
    }

    private suspend fun scanMediaStore(uri: android.net.Uri, projection: Array<String>, lastScanTime: Long) {
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastScanTime.toString())

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                val newItems = mutableListOf<MediaItemEntity>()
                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val mime = cursor.getString(mimeColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    // Chỉ thêm vào DB nếu chưa tồn tại (Room OnConflictStrategy.IGNORE sẽ xử lý)
                    val entity = MediaItemEntity(
                        id = contentUri.toString(), // Dùng URI làm ID
                        fileName = name ?: "Unknown",
                        // Avoid relying on deprecated/blocked filesystem path columns under scoped storage.
                        filePath = "",
                        mimeType = mime ?: "application/octet-stream",
                        fileSize = size,
                        dateAdded = dateAdded
                    )
                    newItems.add(entity)
                    count++
                }
                
                if (newItems.isNotEmpty()) {
                    mediaDao.insertAll(newItems)
                }
                Log.d(TAG, "Scanned $count new items from $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media: $uri", e)
        }
    }
}

