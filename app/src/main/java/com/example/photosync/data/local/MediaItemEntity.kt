package com.example.photosync.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_items",
    indices = [
        Index(value = ["isSynced"]),
        Index(value = ["isLocal"]),
        Index(value = ["googlePhotosId"], unique = true),
        Index(value = ["dateAdded"]) // For sorting
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String, // Local URI hoặc ID của file
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long,
    val dateAdded: Long = 0,
    val isSynced: Boolean = false,
    val isLocal: Boolean = true, // True nếu file có trên máy
    val remoteUrl: String? = null, // URL ảnh trên cloud (baseUrl)
    val googlePhotosId: String? = null, // ID trả về từ Google sau khi upload
    val lastSyncedAt: Long? = null,
    val tags: String? = null // Comma separated tags from ML Kit
)
