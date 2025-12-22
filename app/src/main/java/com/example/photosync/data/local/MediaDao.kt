package com.example.photosync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isSynced = 0")
    suspend fun getUnsyncedItems(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaItemEntity)

    @Query("UPDATE media_items SET isSynced = :isSynced, googlePhotosId = :googleId, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, googleId: String?, timestamp: Long)
}
