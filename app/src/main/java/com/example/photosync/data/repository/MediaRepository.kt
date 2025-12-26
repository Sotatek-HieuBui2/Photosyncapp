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
        Log.d(TAG, "Starting scanLocalMedia... Last scan: $lastScanTime")
        
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
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
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)

                val newItems = mutableListOf<MediaItemEntity>()
                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(pathColumn)
                    val mime = cursor.getString(mimeColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    // Chỉ thêm vào DB nếu chưa tồn tại (Room OnConflictStrategy.IGNORE sẽ xử lý)
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
                }
                Log.d(TAG, "Scanned $count new items from $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media: $uri", e)
        }
    }
}
