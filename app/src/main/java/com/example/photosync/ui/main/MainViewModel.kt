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
        _uiState.value = _uiState.value.copy(isAutoSyncEnabled = isEnabled)
        if (isEnabled) {
            setupPeriodicSync()
        }
    }

    private fun observeMediaItems() {
        viewModelScope.launch {
            mediaRepository.allMediaItems.collect { items ->
                _uiState.value = _uiState.value.copy(mediaItems = items)
            }
        }
    }

    fun getSignInIntent(): Intent {
        return authManager.getSignInIntent()
    }

    fun handleSignInResult(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
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
                
                // Sau khi login thành công, quét media và sync
                startSyncProcess()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to retrieve access token"
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

    fun startSyncProcess() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = "Scanning media...")
            
            // 1. Quét file local
            mediaRepository.scanLocalMedia()
            
            // 2. Kích hoạt Worker
            _uiState.value = _uiState.value.copy(statusMessage = "Starting background sync...")
            val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>().build()
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

    private fun setupPeriodicSync() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
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
    val mediaItems: List<com.example.photosync.data.local.MediaItemEntity> = emptyList()
)
