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
            
            _uiState.value = _uiState.value.copy(isLoading = false, statusMessage = "Sync started in background")
        }
    }
    
    fun signOut() {
        tokenManager.clear()
        _uiState.value = MainUiState() // Reset state
    }
}

data class MainUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val statusMessage: String? = null
)
