package com.example.photosync.data.remote

import retrofit2.http.GET
import retrofit2.http.Header

interface GoogleDriveApi {
    @GET("files?fields=storageQuota")
    suspend fun getStorageQuota(
        @Header("Authorization") token: String
    ): DriveAboutResponse
}

data class DriveAboutResponse(val storageQuota: StorageQuota)
data class StorageQuota(val limit: Long, val usage: Long, val usageInDrive: Long, val usageInTrash: Long)
