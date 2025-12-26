package com.example.photosync.ui.tools

import android.app.Application
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photosync.data.local.MediaItemEntity
import com.example.photosync.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import android.net.Uri
import java.io.IOException
import java.security.MessageDigest
import java.io.FileInputStream

data class Moment(
    val date: String,
    val items: List<MediaItemEntity>
)

data class ToolsUiState(
    val isLoading: Boolean = false,
    val duplicateGroups: List<List<MediaItemEntity>> = emptyList(),
    val spaceFreed: Long = 0,
    val message: String? = null,
    val searchResults: List<MediaItemEntity> = emptyList(),
    val moments: List<Moment> = emptyList()
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val application: Application,
    private val mediaDao: com.example.photosync.data.local.MediaDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    fun scanForObjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Scanning photos for objects...") }
            
            val items = repository.allMediaItems.first().filter { it.isLocal && it.tags == null }
            val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
            var processed = 0
            
            withContext(Dispatchers.IO) {
                items.forEach { item ->
                    try {
                        val image = InputImage.fromFilePath(application, Uri.parse(item.id))
                        labeler.process(image)
                            .addOnSuccessListener { labels ->
                                val tags = labels.joinToString(",") { it.text }
                                if (tags.isNotEmpty()) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        mediaDao.updateTags(item.id, tags)
                                    }
                                }
                            }
                            .addOnFailureListener { e ->
                                e.printStackTrace()
                            }
                        processed++
                        if (processed % 10 == 0) {
                             _uiState.update { it.copy(message = "Scanning... $processed/${items.size}") }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            }
            
            _uiState.update { it.copy(isLoading = false, message = "Scan complete!") }
        }
    }
    
    fun searchByTag(query: String) {
        viewModelScope.launch {
             val allItems = repository.allMediaItems.first()
             val results = allItems.filter { it.tags?.contains(query, ignoreCase = true) == true }
             _uiState.update { it.copy(searchResults = results) }
        }
    }

    fun scanForDuplicates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Scanning for duplicates...") }
            
            val duplicates = withContext(Dispatchers.IO) {
                findDuplicates()
            }
            
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    duplicateGroups = duplicates,
                    message = if (duplicates.isEmpty()) "No duplicates found" else "Found ${duplicates.size} sets of duplicates"
                ) 
            }
        }
    }

    private suspend fun findDuplicates(): List<List<MediaItemEntity>> {
        // Get all local items
        val allItems = repository.allMediaItems.first().filter { it.isLocal }
        
        // 1. Group by size first (fastest filter)
        val sizeGroups = allItems.groupBy { it.fileSize }.filter { it.value.size > 1 }
        
        val duplicates = mutableListOf<List<MediaItemEntity>>()
        
        // 2. For items with same size, calculate MD5 hash
        sizeGroups.values.forEach { potentialDuplicates ->
            val hashGroups = potentialDuplicates.groupBy { item ->
                calculateMD5(item.id)
            }
            
            // Only keep groups where hash is identical and valid (not null)
            hashGroups.forEach { (hash, items) ->
                if (hash != null && items.size > 1) {
                    duplicates.add(items)
                }
            }
        }
        
        return duplicates
    }

    private fun calculateMD5(uriString: String): String? {
        return try {
            val uri = Uri.parse(uriString)
            val inputStream = application.contentResolver.openInputStream(uri) ?: return null
            val buffer = ByteArray(8192)
            val digest = MessageDigest.getInstance("MD5")
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
            inputStream.close()
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun deleteDuplicates(itemsToDelete: List<MediaItemEntity>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Deleting duplicates...") }
            // In a real implementation, we would invoke the deletion logic here
            // For now, we'll just simulate it or call a repository method if it existed
            // This requires the ActivityResultLauncher in the UI, so we just prepare the list
            
            _uiState.update { it.copy(isLoading = false, message = "Ready to delete") }
        }
    }

    fun generateMoments() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = "Generating moments...") }
            
            val allMedia = repository.allMediaItems.first()
            
            // Group by date (simple string format for now)
            // Assuming dateAdded is a timestamp in seconds or milliseconds
            val grouped = allMedia.groupBy { item ->
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    .format(java.util.Date(item.dateAdded * 1000)) 
            }
            
            val moments = grouped.map { (date, items) ->
                Moment(date, items)
            }.filter { moment ->
                moment.items.size >= 3 // Only keep days with enough photos for a collage
            }.sortedByDescending { moment ->
                moment.date 
            }.take(10) // Limit to top 10 recent moments
            
            _uiState.update { 
                it.copy(
                    isLoading = false, 
                    moments = moments,
                    message = if (moments.isEmpty()) "No moments found with enough photos." else null
                ) 
            }
        }
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
