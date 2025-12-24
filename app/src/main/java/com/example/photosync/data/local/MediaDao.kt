package com.example.photosync.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items ORDER BY id DESC")
    fun getAllMediaItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isSynced = 0")
    suspend fun getUnsyncedItems(): List<MediaItemEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Update
    suspend fun updateAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isSynced = :isSynced, googlePhotosId = :googleId, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, googleId: String?, timestamp: Long)
}
