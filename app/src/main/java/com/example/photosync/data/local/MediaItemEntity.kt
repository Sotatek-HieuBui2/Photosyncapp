package com.example.photosync.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey val id: String, // Local URI hoặc ID của file
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val fileSize: Long,
    val isSynced: Boolean = false,
    val googlePhotosId: String? = null, // ID trả về từ Google sau khi upload
    val lastSyncedAt: Long? = null
)
