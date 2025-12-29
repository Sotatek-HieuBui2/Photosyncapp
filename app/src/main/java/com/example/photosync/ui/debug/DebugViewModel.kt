package com.example.photosync.ui.debug

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photosync.data.local.MediaDao
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val mediaDao: MediaDao,
    private val tokenManager: TokenManager,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _debugInfo = MutableStateFlow(DebugInfo())
    val debugInfo: StateFlow<DebugInfo> = _debugInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val logs = mutableListOf<String>()

    private fun log(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        logs.add("[$timestamp] $message")
    }

    fun loadDebugInfo(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            logs.clear()
            
            try {
                log("Starting debug info collection...")
                
                // Get last scan time
                val lastScanTime = tokenManager.getLastScanTime()
                log("Last scan time: $lastScanTime")

                // Get DB counts
                val dbItems = mediaDao.getAllMediaItems().first()
                val dbItemCount = dbItems.size
                val dbImageCount = dbItems.count { it.mimeType.startsWith("image/") }
                val dbVideoCount = dbItems.count { it.mimeType.startsWith("video/") }
                log("DB items: total=$dbItemCount, images=$dbImageCount, videos=$dbVideoCount")

                // Get MediaStore counts
                val (imageCount, imageSamples) = withContext(Dispatchers.IO) {
                    queryMediaStoreCount(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                }
                log("MediaStore images: $imageCount")

                val (videoCount, videoSamples) = withContext(Dispatchers.IO) {
                    queryMediaStoreCount(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                }
                log("MediaStore videos: $videoCount")

                // Get sample DB items
                val sampleDbItems = dbItems.take(10).map { item ->
                    "${item.fileName} | ${item.mimeType} | ${item.id.takeLast(30)}"
                }

                _debugInfo.value = DebugInfo(
                    lastScanTime = lastScanTime,
                    dbItemCount = dbItemCount,
                    dbImageCount = dbImageCount,
                    dbVideoCount = dbVideoCount,
                    mediaStoreImageCount = imageCount,
                    mediaStoreVideoCount = videoCount,
                    sampleMediaStoreItems = imageSamples + videoSamples,
                    sampleDbItems = sampleDbItems,
                    logs = logs.toList()
                )

                log("Debug info loaded successfully")

            } catch (e: Exception) {
                log("ERROR: ${e.message}")
                _debugInfo.value = _debugInfo.value.copy(logs = logs.toList())
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun queryMediaStoreCount(context: Context, uri: android.net.Uri): Pair<Int, List<String>> {
        val samples = mutableListOf<String>()
        var count = 0

        try {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE
            )

            context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use { cursor ->
                count = cursor.count

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)

                var sampleCount = 0
                while (cursor.moveToNext() && sampleCount < 5) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown"
                    val mime = cursor.getString(mimeColumn) ?: "?"
                    val size = cursor.getLong(sizeColumn)

                    val contentUri = android.content.ContentUris.withAppendedId(uri, id)
                    samples.add("$name | $mime | ${size / 1024}KB | ${contentUri.toString().takeLast(20)}")
                    sampleCount++
                }
            }
        } catch (e: Exception) {
            log("Error querying $uri: ${e.message}")
        }

        return Pair(count, samples)
    }

    fun forceFullRescan(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            logs.clear()
            
            try {
                log("Starting force full rescan...")
                
                // Reset scan state
                tokenManager.resetScanState()
                log("Reset scan state (lastScanTime = 0)")
                
                // Perform scan
                mediaRepository.scanLocalMedia()
                log("Scan completed")
                
                // Reload debug info
                loadDebugInfo(context)
                
            } catch (e: Exception) {
                log("ERROR during rescan: ${e.message}")
                _debugInfo.value = _debugInfo.value.copy(logs = logs.toList())
                _isLoading.value = false
            }
        }
    }

    fun clearDatabase() {
        viewModelScope.launch {
            logs.clear()
            
            try {
                log("Clearing database...")
                
                // Get all items and delete them
                val items = mediaDao.getAllMediaItems().first()
                items.forEach { item ->
                    mediaDao.deleteById(item.id)
                }
                
                // Also reset scan time
                tokenManager.resetScanState()
                
                log("Database cleared. ${items.size} items deleted.")
                log("Scan state reset.")
                
                _debugInfo.value = DebugInfo(logs = logs.toList())
                
            } catch (e: Exception) {
                log("ERROR clearing DB: ${e.message}")
                _debugInfo.value = _debugInfo.value.copy(logs = logs.toList())
            }
        }
    }
}
