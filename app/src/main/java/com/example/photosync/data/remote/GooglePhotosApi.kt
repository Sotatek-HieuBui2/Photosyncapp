package com.example.photosync.data.remote

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface GooglePhotosApi {

    // Lấy danh sách media từ Google Photos
    @GET("v1/mediaItems")
    suspend fun listMediaItems(
        @Header("Authorization") token: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): ListMediaItemsResponse

    // Bước 1: Upload binary data để lấy uploadToken
    @POST("v1/uploads")
    suspend fun uploadMediaBytes(
        @Header("Authorization") token: String,
        @Header("Content-type") contentType: String = "application/octet-stream",
        @Header("X-Goog-Upload-Content-Type") mimeType: String,
        @Header("X-Goog-Upload-Protocol") protocol: String = "raw",
        @Body fileBytes: RequestBody
    ): ResponseBody // Trả về ResponseBody để xử lý raw string

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
data class MediaItem(
    val id: String, 
    val productUrl: String,
    val baseUrl: String? = null,
    val mimeType: String? = null,
    val filename: String? = null,
    val mediaMetadata: MediaMetadata? = null
)

data class ListMediaItemsResponse(
    val mediaItems: List<MediaItem>?,
    val nextPageToken: String?
)

data class MediaMetadata(
    val creationTime: String,
    val width: String,
    val height: String
)
