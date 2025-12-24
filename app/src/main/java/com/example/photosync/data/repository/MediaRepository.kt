package com.example.photosync.data.repository

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import com.example.photosync.data.local.MediaDao
import com.example.photosync.data.local.MediaItemEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao
) {
    private val TAG = "MediaRepository"

    val allMediaItems = mediaDao.getAllMediaItems()

    suspend fun scanLocalMedia() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting scanLocalMedia...")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE
        )

        // Scan Images
        scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection)
        
        // Scan Videos
        scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, projection)
        Log.d(TAG, "scanLocalMedia completed.")
    }

    private suspend fun scanMediaStore(uri: android.net.Uri, projection: Array<String>) {
        try {
            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                var count = 0
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val path = cursor.getString(pathColumn)
                    val mime = cursor.getString(mimeColumn)
                    val size = cursor.getLong(sizeColumn)

                    val contentUri = ContentUris.withAppendedId(uri, id)

                    // Chỉ thêm vào DB nếu chưa tồn tại (Room OnConflictStrategy.IGNORE sẽ xử lý)
                    val entity = MediaItemEntity(
                        id = contentUri.toString(), // Dùng URI làm ID
                        fileName = name ?: "Unknown",
                        filePath = path ?: "",
                        mimeType = mime ?: "application/octet-stream",
                        fileSize = size
                    )
                    mediaDao.insert(entity)
                    count++
                }
                Log.d(TAG, "Scanned $count items from $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media: $uri", e)
        }
    }
}
