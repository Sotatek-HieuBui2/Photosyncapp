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

    @Query("SELECT * FROM media_items WHERE googlePhotosId = :googleId LIMIT 1")
    suspend fun getByGoogleId(googleId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE isSynced = 1 AND isLocal = 1")
    suspend fun getSyncedLocalItems(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE tags LIKE '%' || :query || '%' ORDER BY dateAdded DESC")
    suspend fun searchByTags(query: String): List<MediaItemEntity>

    @Query("UPDATE media_items SET isLocal = :isLocal WHERE id = :id")
    suspend fun updateLocalStatus(id: String, isLocal: Boolean)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Update
    suspend fun updateAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isSynced = :isSynced, googlePhotosId = :googleId, lastSyncedAt = :timestamp WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, googleId: String?, timestamp: Long)

    @Query("UPDATE media_items SET tags = :tags WHERE id = :id")
    suspend fun updateTags(id: String, tags: String)
}
