package com.example.photosync.workers

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.photosync.R
import com.example.photosync.data.local.MediaItemEntity
import com.example.photosync.data.local.AppDatabase
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.remote.GooglePhotosApi
import com.example.photosync.data.remote.SimpleMediaItem
import com.example.photosync.data.remote.NewMediaItem
import com.example.photosync.data.remote.BatchCreateRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import kotlin.math.roundToInt

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val api: GooglePhotosApi,
    private val tokenManager: TokenManager,
    private val mediaRepository: com.example.photosync.data.repository.MediaRepository
) : CoroutineWorker(context, params) {

    private val TAG = "SyncWorker"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting SyncWorker...")
        
        // 0. Check authentication first
        val token = tokenManager.getAccessToken()
        if (token.isNullOrEmpty()) {
            Log.w(TAG, "Access token is missing. Cannot sync.")
            return@withContext Result.failure()
        }
        val accessToken = "Bearer $token"
        
        // Scan for new media before syncing
        try {
            mediaRepository.scanLocalMedia()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scan local media", e)
        }
        
        createNotificationChannel()
        
        // Đánh dấu là Foreground Service để không bị kill khi chạy lâu
        try {
            setForeground(createForegroundInfo(0, "Starting sync..."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set foreground service", e)
        }

        try {
            // 1. Lấy danh sách file chưa đồng bộ từ DB
            val unsyncedItems = database.mediaDao().getUnsyncedItems()
            val totalItems = unsyncedItems.size
            Log.d(TAG, "Found $totalItems unsynced items.")
            
            if (unsyncedItems.isEmpty()) {
                return@withContext Result.success()
            }

            var syncedCount = 0
            val startTime = System.currentTimeMillis()
            
            // Calculate total size for ETA
            val totalSizeBytes = unsyncedItems.sumOf { it.fileSize }
            var accumulatedBytes = 0L
            var currentFileBytesUploaded = 0L
            
            // Launch a coroutine to update progress periodically
            val progressJob = launch {
                while (isActive) {
                    delay(1000)
                    
                    val totalUploaded = accumulatedBytes + currentFileBytesUploaded
                    val timeElapsed = System.currentTimeMillis() - startTime
                    
                    if (timeElapsed > 0 && totalUploaded > 0) {
                        val speed = totalUploaded.toDouble() / timeElapsed // bytes per ms
                        val remainingBytes = totalSizeBytes - totalUploaded
                        val estimatedTimeRemaining = (remainingBytes / speed).toLong()
                        
                        val progress = ((totalUploaded.toDouble() / totalSizeBytes) * 100).roundToInt()
                        
                        val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(estimatedTimeRemaining)
                        val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(estimatedTimeRemaining) % 60
                        val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(estimatedTimeRemaining) % 60
                        val timeString = String.format("%02d:%02d:%02d", hours, minutes, seconds)

                        val statusMsg = "Syncing... ETA: $timeString"
                        try {
                            setForeground(createForegroundInfo(progress, statusMsg))
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update foreground notification", e)
                        }
                        
                        setProgress(workDataOf(
                            "Progress" to progress,
                            "Current" to syncedCount + 1, // Approximate
                            "Total" to totalItems,
                            "EstTime" to estimatedTimeRemaining
                        ))
                    }
                }
            }

            // Chunk items to respect Google Photos API batch limit (50 items per batch usually, but let's stick to smaller chunks for safety, e.g., 20)
            val batchSize = 20
            val chunks = unsyncedItems.chunked(batchSize)

            for (chunk in chunks) {
                if (isStopped) break

                val newMediaItems = mutableListOf<NewMediaItem>()
                val successfulItems = mutableListOf<MediaItemEntity>()
                val failedOrSkippedItems = mutableListOf<MediaItemEntity>()

                // 1. Upload bytes for each item in chunk
                for (item in chunk) {
                    if (isStopped) break

                    Log.d(TAG, "Processing item: ${item.fileName} (ID: ${item.id})")
                    
                    val contentUri = Uri.parse(item.id)
                    
                    // Check if file exists/is accessible
                    try {
                        applicationContext.contentResolver.openInputStream(contentUri)?.close() ?: run {
                            Log.w(TAG, "File not found or inaccessible: $contentUri. Marking as skipped.")
                            // Mark as synced (or handle differently) to prevent infinite retry loop
                            failedOrSkippedItems.add(item.copy(isSynced = true, lastSyncedAt = System.currentTimeMillis()))
                            return@run
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking file existence: $contentUri", e)
                        failedOrSkippedItems.add(item.copy(isSynced = true, lastSyncedAt = System.currentTimeMillis()))
                        continue
                    }

                    // Sử dụng ContentResolver để đọc file thay vì File path trực tiếp (Scoped Storage)
                    val requestBody = try {
                        createRequestBodyFromUri(applicationContext, contentUri, item.mimeType) { bytesWritten ->
                            currentFileBytesUploaded = bytesWritten
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to open URI: $contentUri", e)
                        continue
                    }

                    if (requestBody == null) {
                        Log.e(TAG, "RequestBody is null for $contentUri")
                        continue
                    }

                    // 3. Upload Bytes
                    Log.d(TAG, "Uploading bytes for ${item.fileName}...")
                    val uploadToken = try {
                        val responseBody = api.uploadMediaBytes(
                            token = accessToken,
                            mimeType = item.mimeType,
                            fileBytes = requestBody
                        )
                        responseBody.string()
                    } catch (e: Exception) {
                        Log.e(TAG, "Upload failed for ${item.fileName}", e)
                        continue
                    }
                    Log.d(TAG, "Upload successful. Token: ${uploadToken.take(20)}...")
                    
                    // Update accumulated bytes after successful upload (or attempt)
                    accumulatedBytes += item.fileSize
                    currentFileBytesUploaded = 0

                    // 4. Prepare for Batch Create
                    val simpleMediaItem = SimpleMediaItem(uploadToken)
                    newMediaItems.add(NewMediaItem(description = "Uploaded via PhotoSync", simpleMediaItem = simpleMediaItem))
                    successfulItems.add(item)
                }
                
                // Update skipped items in DB so they don't block future syncs
                if (failedOrSkippedItems.isNotEmpty()) {
                    database.mediaDao().updateAll(failedOrSkippedItems)
                }

                // 5. Batch create media items
                if (newMediaItems.isNotEmpty()) {
                    Log.d(TAG, "Creating batch of ${newMediaItems.size} media items...")
                    try {
                        val batchRequest = BatchCreateRequest(newMediaItems = newMediaItems)
                        val response = api.createMediaItems(accessToken, batchRequest)
                        
                        // 6. Process response and update DB
                        val updates = mutableListOf<MediaItemEntity>()
                        
                        response.newMediaItemResults.forEachIndexed { index, result ->
                             val item = successfulItems.getOrNull(index) ?: return@forEachIndexed
                             
                             if (result.status.message == "Success" || result.status.message == "OK" || result.mediaItem != null) {
                                 val googleId = result.mediaItem?.id
                                 Log.d(TAG, "Sync success for ${item.fileName}. Google ID: $googleId")
                                 
                                 val updatedItem = item.copy(
                                     isSynced = true,
                                     googlePhotosId = googleId,
                                     lastSyncedAt = System.currentTimeMillis()
                                 )
                                 updates.add(updatedItem)
                                 syncedCount++
                             } else {
                                 Log.e(TAG, "Batch create failed for ${item.fileName}: ${result.status.message}")
                             }
                        }
                        
                        if (updates.isNotEmpty()) {
                            database.mediaDao().updateAll(updates)
                        }
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Batch create API call failed", e)
                    }
                }
            }
            
            progressJob.cancel()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed with exception", e)
            // Don't retry blindly on generic exceptions to avoid infinite loops.
            // Only retry if it's clearly a transient network issue, but for now, let's return failure/success
            // so the user can manually retry.
            Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "sync_channel"
            val title = "Photo Sync"
            val channel = NotificationChannel(channelId, title, NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(progress: Int, message: String): ForegroundInfo {
        val channelId = "sync_channel"
        val title = "Photo Sync"
        
        // Channel creation moved to createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher) // Use app icon instead of system icon
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(1, notification)
        }
    }

    private fun createRequestBodyFromUri(context: Context, uri: Uri, mimeType: String, onProgress: (Long) -> Unit): RequestBody? {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return mimeType.toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.source().use { source ->
                        var totalBytesWritten = 0L
                        val buffer = okio.Buffer()
                        var readCount: Long
                        while (source.read(buffer, 8192).also { readCount = it } != -1L) {
                            sink.write(buffer, readCount)
                            totalBytesWritten += readCount
                            onProgress(totalBytesWritten)
                        }
                    }
                } ?: throw IOException("Could not open input stream for $uri")
            }
        }
    }
}
