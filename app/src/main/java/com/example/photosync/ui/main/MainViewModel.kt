package com.example.photosync.ui.main

import android.app.Application
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.photosync.auth.AuthManager
import com.example.photosync.data.local.TokenManager
import com.example.photosync.data.repository.MediaRepository
import com.example.photosync.workers.SyncWorker
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.work.WorkInfo
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.photosync.data.local.MediaItemEntity

sealed class MediaUiItem {
    data class Header(val title: String) : MediaUiItem()
    data class Media(val item: MediaItemEntity) : MediaUiItem()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val tokenManager: TokenManager,
    private val mediaRepository: MediaRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        checkLoginStatus()
        checkAutoSyncStatus()
        observeMediaItems()
    }

    private fun checkLoginStatus() {
        val email = tokenManager.getEmail()
        val token = tokenManager.getAccessToken()
        if (!email.isNullOrEmpty() && !token.isNullOrEmpty()) {
            _uiState.value = _uiState.value.copy(
                isSignedIn = true,
                userEmail = email
            )
        }
    }

    private fun checkAutoSyncStatus() {
        val isEnabled = tokenManager.isAutoSyncEnabled()
        val isWifiOnly = tokenManager.isWifiOnly()
        _uiState.value = _uiState.value.copy(
            isAutoSyncEnabled = isEnabled,
            isWifiOnly = isWifiOnly
        )
        
        // Only setup periodic sync if enabled AND user is signed in
        val token = tokenManager.getAccessToken()
        if (isEnabled && !token.isNullOrEmpty() && tokenManager.isCloudSyncEnabled()) {
            setupPeriodicSync()
        }
    }

    private fun observeMediaItems() {
        viewModelScope.launch {
            mediaRepository.allMediaItems.collect { items ->
                val groupedItems = groupMediaItems(items)
                _uiState.value = _uiState.value.copy(mediaItems = groupedItems)
            }
        }
    }

    fun prepareFreeUpSpace(onResult: (List<MediaItemEntity>) -> Unit) {
        viewModelScope.launch {
            val items = mediaRepository.getSyncedLocalItems()
            onResult(items)
        }
    }

    fun handleFreeUpSpaceSuccess(deletedIds: List<String>) {
        handleLocalDeletionSuccess(deletedIds)
    }

    fun handleLocalDeletionSuccess(deletedIds: List<String>) {
        viewModelScope.launch {
            mediaRepository.markAsDeletedLocally(deletedIds)
        }
    }

    private fun groupMediaItems(items: List<MediaItemEntity>): List<MediaUiItem> {
        if (items.isEmpty()) return emptyList()

        val sortedItems = items.sortedByDescending { it.dateAdded }
        val grouped = sortedItems.groupBy { item ->
            formatDate(item.dateAdded * 1000) // dateAdded is in seconds
        }

        val uiItems = mutableListOf<MediaUiItem>()
        grouped.forEach { (date, mediaList) ->
            uiItems.add(MediaUiItem.Header(date))
            mediaList.forEach { media ->
                uiItems.add(MediaUiItem.Media(media))
            }
        }
        return uiItems
    }

    private fun formatDate(timestamp: Long): String {
        val date = Date(timestamp)
        val now = Date()
        val oneDay = 24 * 60 * 60 * 1000L

        return when {
            isSameDay(date, now) -> "Today"
            isSameDay(date, Date(now.time - oneDay)) -> "Yesterday"
            else -> SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(date)
        }
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        return fmt.format(date1) == fmt.format(date2)
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            if (!authManager.hasPermissions(account)) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Missing permissions. Please sign in again and grant all permissions."
                )
                return@launch
            }

            val token = authManager.getAccessToken(account)
            if (token != null) {
                tokenManager.saveAccessToken(token)
                tokenManager.saveEmail(account.email ?: "")
                
                _uiState.value = _uiState.value.copy(
                    isSignedIn = true,
                    userEmail = account.email,
                    isLoading = false,
                    error = null
                )

                // Sau khi login thành công, quét media và sync only if cloud sync feature is enabled
                if (tokenManager.isCloudSyncEnabled()) {
                    startSyncProcess()
                } else {
                    _uiState.value = _uiState.value.copy(statusMessage = "Cloud sync disabled")
                }
            } else {
                // Try to surface diagnostic tokeninfo saved by AuthManager
                val diag = tokenManager.getDiagnostic("tokeninfo")
                val errMsg = if (!diag.isNullOrEmpty()) {
                    "Failed to retrieve access token. tokeninfo: $diag"
                } else {
                    "Failed to retrieve access token"
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = errMsg
                )
            }
        }
    }

    fun setError(msg: String) {
        _uiState.value = _uiState.value.copy(error = msg)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearTriggerSignIn() {
        _uiState.value = _uiState.value.copy(triggerSignIn = false)
    }

    fun startSyncProcess() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Scanning media...")
            
            // 1. Quét file local
            mediaRepository.scanLocalMedia()
            
            // 2. Sync cloud media info (optional by setting)
            if (tokenManager.isCloudSyncEnabled()) {
                try {
                    _uiState.value = _uiState.value.copy(statusMessage = "Fetching cloud library...")
                    mediaRepository.syncCloudMedia()
                } catch (e: Exception) {
                    e.printStackTrace()

                    // Handle re-auth required separately
                    if (e is com.example.photosync.auth.ReAuthRequiredException || (e.message?.contains("REAUTH_REQUIRED") == true)) {
                        handleReAuthRequired()
                        return@launch
                    }

                    _uiState.value = _uiState.value.copy(
                        error = "Cloud sync failed: ${e.message}. Try signing out and in again."
                    )
                }
            } else {
                _uiState.value = _uiState.value.copy(statusMessage = "Cloud sync skipped")
            }
            
            // 3. Kích hoạt Worker
            _uiState.value = _uiState.value.copy(statusMessage = "Starting background sync...")
            
            val networkType = if (_uiState.value.isWifiOnly) {
                androidx.work.NetworkType.UNMETERED
            } else {
                androidx.work.NetworkType.CONNECTED
            }
            
            val constraints = androidx.work.Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .build()
                
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    androidx.work.BackoffPolicy.EXPONENTIAL,
                    30,
                    java.util.concurrent.TimeUnit.SECONDS
                )
                .build()
                
            WorkManager.getInstance(application).enqueue(syncRequest)
            
            observeSyncProgress(syncRequest.id)
        }
    }

    private fun observeSyncProgress(workId: UUID) {
        viewModelScope.launch {
            WorkManager.getInstance(application).getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo != null) {
                    val progress = workInfo.progress
                    val current = progress.getInt("Current", 0)
                    val total = progress.getInt("Total", 0)
                    val percent = progress.getInt("Progress", 0)
                    val estTime = progress.getLong("EstTime", 0)

                    val status = when (workInfo.state) {
                        WorkInfo.State.RUNNING -> {
                            val hours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(estTime)
                            val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(estTime) % 60
                            val seconds = java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(estTime) % 60
                            val formattedTime = String.format("%02d:%02d:%02d", hours, minutes, seconds)
                            
                            val timeString = if (estTime > 0) "ETA: $formattedTime" else "Calculating time..."
                            "Syncing $current/$total ($percent%) • $timeString"
                        }
                        WorkInfo.State.SUCCEEDED -> "Sync Completed!"
                        WorkInfo.State.FAILED -> "Sync Failed"
                        else -> "Sync Status: ${workInfo.state}"
                    }

                    _uiState.value = _uiState.value.copy(
                        isLoading = workInfo.state == WorkInfo.State.RUNNING,
                        statusMessage = status,
                        progress = percent / 100f
                    )
                }
            }
        }
    }
    
    fun signOut() {
        viewModelScope.launch {
            authManager.signOut()
            tokenManager.clear()
            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                userEmail = null,
                statusMessage = null,
                error = null
            )
        }
    }

    // Public handler to perform re-auth flow: clear tokens, sign out and prompt UI to request sign-in.
    fun handleReAuthRequired() {
        viewModelScope.launch {
            tokenManager.clear()
            try {
                authManager.signOut()
            } catch (ex: Exception) {
                ex.printStackTrace()
            }

            _uiState.value = _uiState.value.copy(
                isSignedIn = false,
                userEmail = null,
                isLoading = false,
                error = "Authentication required or insufficient permissions. Please sign in and grant Photos scopes.",
                triggerSignIn = true
            )
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            tokenManager.saveAutoSyncState(enabled)
            _uiState.value = _uiState.value.copy(isAutoSyncEnabled = enabled)
            
            if (enabled) {
                setupPeriodicSync()
            } else {
                cancelPeriodicSync()
            }
        }
    }

    fun toggleWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            tokenManager.saveWifiOnly(enabled)
            _uiState.value = _uiState.value.copy(isWifiOnly = enabled)
            
            // If auto sync is enabled, we need to update the periodic work with new constraints
            if (_uiState.value.isAutoSyncEnabled) {
                setupPeriodicSync()
            }
        }
    }

    private fun setupPeriodicSync() {
        val networkType = if (_uiState.value.isWifiOnly) {
            androidx.work.NetworkType.UNMETERED
        } else {
            androidx.work.NetworkType.CONNECTED
        }

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val periodicWork = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(
            15, java.util.concurrent.TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(application).enqueueUniquePeriodicWork(
            "AutoSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    private fun cancelPeriodicSync() {
        WorkManager.getInstance(application).cancelUniqueWork("AutoSyncWork")
    }
}

data class MainUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val progress: Float = 0f,
    val isAutoSyncEnabled: Boolean = false,
    val isWifiOnly: Boolean = true,
    val mediaItems: List<MediaUiItem> = emptyList(),
    val triggerSignIn: Boolean = false
)
