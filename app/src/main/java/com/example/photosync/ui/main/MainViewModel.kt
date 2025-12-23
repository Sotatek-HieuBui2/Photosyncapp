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
                    isLoading = false
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
                            val timeString = if (estTime > 0) "${estTime / 1000}s remaining" else "Calculating..."
                            "Syncing $current/$total ($percent%) - $timeString"
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
            _uiState.value = MainUiState() // Reset state
        }
    }
}

data class MainUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val error: String? = null,
    val progress: Float = 0f
)
