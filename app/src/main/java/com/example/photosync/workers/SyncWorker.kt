package com.example.photosync.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photosync.data.local.AppDatabase
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.remote.GooglePhotosApi
import com.example.photosync.data.remote.SimpleMediaItem // Import thiếu
import com.example.photosync.data.remote.NewMediaItem // Import thiếu
import com.example.photosync.data.remote.BatchCreateRequest // Import thiếu
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val database: AppDatabase,
    private val api: GooglePhotosApi,
    private val tokenManager: TokenManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // 1. Lấy danh sách file chưa đồng bộ từ DB
            val unsyncedItems = database.mediaDao().getUnsyncedItems()
            
            // 2. Lấy Access Token từ TokenManager
            val token = tokenManager.getAccessToken()
            if (token.isNullOrEmpty()) {
                // Nếu không có token, báo lỗi hoặc trigger re-login (ở đây return failure tạm)
                return@withContext Result.failure()
            }
            val accessToken = "Bearer $token"

            for (item in unsyncedItems) {
                val file = File(item.filePath)
                if (!file.exists()) continue

                // 3. Upload Bytes
                val requestBody = file.asRequestBody(item.mimeType.toMediaTypeOrNull())
                val uploadToken = api.uploadMediaBytes(
                    token = accessToken,
                    mimeType = item.mimeType,
                    fileBytes = requestBody
                )

                // 4. Tạo Media Item trên Google Photos
                // Lưu ý: Nên gom nhóm (batch) 50 items để tối ưu, ở đây làm mẫu từng cái
                val simpleMediaItem = SimpleMediaItem(uploadToken)
                val newMediaItem = NewMediaItem(description = "Uploaded via PhotoSync", simpleMediaItem = simpleMediaItem)
                val batchRequest = BatchCreateRequest(newMediaItems = listOf(newMediaItem))
                
                val response = api.createMediaItems(accessToken, batchRequest)
                
                // 5. Cập nhật trạng thái trong DB
                val result = response.newMediaItemResults.firstOrNull()
                if (result?.status?.message == "Success" || result?.mediaItem != null) {
                    val googleId = result?.mediaItem?.id
                    database.mediaDao().updateSyncStatus(
                        id = item.id,
                        isSynced = true,
                        googleId = googleId,
                        timestamp = System.currentTimeMillis()
                    )
                }
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
