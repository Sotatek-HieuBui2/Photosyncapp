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
import com.example.photosync.data.local.AppDatabase
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.remote.GooglePhotosApi
import com.example.photosync.data.remote.SimpleMediaItem
import com.example.photosync.data.remote.NewMediaItem
import com.example.photosync.data.remote.BatchCreateRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
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
    private val tokenManager: TokenManager
) : CoroutineWorker(context, params) {

    private val TAG = "SyncWorker"
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting SyncWorker...")
        
        createNotificationChannel()
        
        // Đánh dấu là Foreground Service để không bị kill khi chạy lâu
        try {
            setForeground(createForegroundInfo(0, 0, "Starting sync..."))
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

            // 2. Lấy Access Token từ TokenManager
            val token = tokenManager.getAccessToken()
            if (token.isNullOrEmpty()) {
                Log.e(TAG, "Access token is missing.")
                return@withContext Result.failure()
            }
            val accessToken = "Bearer $token"

            var syncedCount = 0
            val startTime = System.currentTimeMillis()
            var lastUpdateTime = 0L

            for ((index, item) in unsyncedItems.withIndex()) {
                if (isStopped) break

                // Cập nhật Progress và Notification (Throttle: mỗi 1 giây hoặc item cuối)
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime > 1000 || index == totalItems - 1) {
                    lastUpdateTime = currentTime
                    
                    val progress = ((index.toFloat() / totalItems) * 100).roundToInt()
                    
                    // Tính toán thời gian ước tính
                    val timeElapsed = currentTime - startTime
                    val itemsProcessed = index
                    val estimatedTimeRemaining = if (itemsProcessed > 0) {
                        val avgTimePerItem = timeElapsed / itemsProcessed
                        val itemsRemaining = totalItems - itemsProcessed
                        avgTimePerItem * itemsRemaining
                    } else {
                        0L
                    }

                    val statusMsg = "Syncing ${index + 1}/$totalItems"
                    try {
                        setForeground(createForegroundInfo(progress, totalItems, statusMsg))
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to update foreground notification", e)
                    }
                    
                    setProgress(workDataOf(
                        "Progress" to progress,
                        "Current" to index + 1,
                        "Total" to totalItems,
                        "EstTime" to estimatedTimeRemaining
                    ))
                }

                Log.d(TAG, "Processing item: ${item.fileName} (ID: ${item.id})")
                
                val contentUri = Uri.parse(item.id)
                
                // Sử dụng ContentResolver để đọc file thay vì File path trực tiếp (Scoped Storage)
                val requestBody = try {
                    createRequestBodyFromUri(applicationContext, contentUri, item.mimeType)
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

                // 4. Tạo Media Item trên Google Photos
                val simpleMediaItem = SimpleMediaItem(uploadToken)
                val newMediaItem = NewMediaItem(description = "Uploaded via PhotoSync", simpleMediaItem = simpleMediaItem)
                val batchRequest = BatchCreateRequest(newMediaItems = listOf(newMediaItem))
                
                Log.d(TAG, "Creating media item...")
                val response = try {
                    api.createMediaItems(accessToken, batchRequest)
                } catch (e: Exception) {
                    Log.e(TAG, "Create media item failed", e)
                    continue
                }
                
                // 5. Cập nhật trạng thái trong DB
                val result = response.newMediaItemResults.firstOrNull()
                if (result?.status?.message == "Success" || result?.mediaItem != null) {
                    val googleId = result?.mediaItem?.id
                    Log.d(TAG, "Sync success for ${item.fileName}. Google ID: $googleId")
                    database.mediaDao().updateSyncStatus(
                        id = item.id,
                        isSynced = true,
                        googleId = googleId,
                        timestamp = System.currentTimeMillis()
                    )
                    syncedCount++
                } else {
                    Log.e(TAG, "Sync failed for ${item.fileName}. Message: ${result?.status?.message}")
                }
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed with exception", e)
            Result.retry()
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

    private fun createForegroundInfo(progress: Int, total: Int, message: String): ForegroundInfo {
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

    private fun createRequestBodyFromUri(context: Context, uri: Uri, mimeType: String): RequestBody? {
        return object : RequestBody() {
            override fun contentType(): MediaType? {
                return mimeType.toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.source().use { source ->
                        sink.writeAll(source)
                    }
                } ?: throw IOException("Could not open input stream for $uri")
            }
        }
    }
}
