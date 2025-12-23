package com.example.photosync.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Các quyền cần thiết
    private val scopePhotos = Scope("https://www.googleapis.com/auth/photoslibrary.appendonly")
    private val scopeDrive = Scope("https://www.googleapis.com/auth/drive.readonly")

    fun getSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(scopePhotos, scopeDrive)
            .build()
        
        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(scopePhotos, scopeDrive)
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        client.signOut()
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val scopes = "oauth2:https://www.googleapis.com/auth/photoslibrary.appendonly https://www.googleapis.com/auth/drive.readonly"
            // Hàm này gọi network để lấy token string thực sự
            GoogleAuthUtil.getToken(context, account.account!!, scopes)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
