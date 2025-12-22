package com.example.photosync.data.remote

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface GooglePhotosApi {

    // Bước 1: Upload binary data để lấy uploadToken
    @POST("v1/uploads")
    suspend fun uploadMediaBytes(
        @Header("Authorization") token: String,
        @Header("Content-type") contentType: String = "application/octet-stream",
        @Header("X-Goog-Upload-Content-Type") mimeType: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
        @Body fileBytes: RequestBody
    ): String // Trả về uploadToken (dạng raw string)

    // Bước 2: Tạo Media Item từ uploadToken
    @POST("v1/mediaItems:batchCreate")
    suspend fun createMediaItems(
        @Header("Authorization") token: String,
        @Body request: BatchCreateRequest
    ): BatchCreateResponse
}

// Data classes cho Request/Response
data class BatchCreateRequest(val newMediaItems: List<NewMediaItem>)
data class NewMediaItem(val description: String, val simpleMediaItem: SimpleMediaItem)
data class SimpleMediaItem(val uploadToken: String)
data class BatchCreateResponse(val newMediaItemResults: List<MediaItemResult>)
data class MediaItemResult(val uploadToken: String, val status: Status, val mediaItem: MediaItem?)
data class Status(val message: String)
data class MediaItem(val id: String, val productUrl: String)
