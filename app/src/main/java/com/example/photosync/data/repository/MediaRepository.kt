package com.example.photosync.data.repository

import android.content.ContentUris
import android.content.Context
import android.os.Build
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
        // Cloud sync feature disabled per user request — do nothing here to avoid auth errors.
        Log.w(TAG, "syncCloudMedia called but cloud sync is disabled. Skipping cloud operations.")
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
        val dbCount = mediaDao.countItems()
        val doFullScan = dbCount == 0 || lastScanTime == 0L
        Log.d(TAG, "Starting scanLocalMedia... Last scan: $lastScanTime, DB count: $dbCount, fullScan=$doFullScan")
        
        // On Android Q+ (API 29+), DATA column is deprecated and may be empty.
        // Use RELATIVE_PATH instead for display purposes.
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED
            )
        }

        // Scan Images
        if (doFullScan) {
            scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null)
        } else {
            scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, lastScanTime)
        }
        
        // Scan Videos
        if (doFullScan) {
            scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, null)
        } else {
            scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection, lastScanTime)
        }

        // Update last scan time to now (in seconds)
        tokenManager.saveLastScanTime(System.currentTimeMillis() / 1000)
        Log.d(TAG, "scanLocalMedia completed.")
        Unit
    }

    suspend fun forceRescanLocalMedia() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Force rescanning all local media...")
        // Reset the scan time so a full scan is triggered
        tokenManager.resetScanState()
        // Perform scan (dbCount check will trigger full scan since lastScanTime=0)
        scanLocalMedia()
    }

    suspend fun getSyncedLocalItems(): List<MediaItemEntity> {
        return mediaDao.getSyncedLocalItems()
    }

    suspend fun markAsDeletedLocally(ids: List<String>) {
        val updates = ids.mapNotNull { id ->
            // We need to fetch the item first to know if we should delete it or just update it
            // But for bulk update, we assume we just want to set isLocal = false
            // However, if it's not synced, we should probably delete it from DB entirely?
            // For "Free up space", they are synced, so we update.
            // For "Delete", if synced, update. If not synced, delete.
            // Let's handle this logic in ViewModel or here.
            // For now, let's just provide a method to update isLocal = false
            null // Placeholder
        }
    }
    
    suspend fun updateLocalStatus(id: String, isLocal: Boolean) {
        mediaDao.updateLocalStatus(id, isLocal)
    }

    suspend fun deleteFromDb(id: String) {
        mediaDao.deleteById(id)
    }

    private suspend fun scanMediaStore(uri: android.net.Uri, projection: Array<String>, lastScanTime: Long?) {
        val selection: String?
        val selectionArgs: Array<String>?

        if (lastScanTime == null) {
            selection = null
            selectionArgs = null
        } else {
            selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
            selectionArgs = arrayOf(lastScanTime.toString())
        }

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                Log.d(TAG, "Query $uri returned ${cursor.count} items (selection=$selection, args=${selectionArgs?.joinToString()})")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                // On Android Q+, use RELATIVE_PATH; on older versions, use DATA
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                }
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                val newItems = mutableListOf<MediaItemEntity>()
                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    // Safely read path/relative path — may be null or column may not exist
                    val path = if (pathColumn >= 0) cursor.getString(pathColumn) else null
                    val mime = cursor.getString(mimeColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    // Chỉ thêm vào DB nếu chưa tồn tại (Room OnConflictStrategy.IGNORE sẽ xử lý)
                    // filePath stores relative path on Q+ or absolute path on older versions;
                    // actual file access should use the content URI.
                    val entity = MediaItemEntity(
                        id = contentUri.toString(), // Dùng URI làm ID
                        fileName = name ?: "Unknown",
                        filePath = path ?: "",
                        mimeType = mime ?: "application/octet-stream",
                        fileSize = size,
                        dateAdded = dateAdded
                    )
                    newItems.add(entity)
                    count++
                }
                
                if (newItems.isNotEmpty()) {
                    mediaDao.insertAll(newItems)
                    val dbCountAfter = mediaDao.countItems()
                    Log.d(TAG, "Inserted ${newItems.size} items. DB count now: $dbCountAfter")
                }
                Log.d(TAG, "Scanned $count items from $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media: $uri", e)
        }
    }
}
