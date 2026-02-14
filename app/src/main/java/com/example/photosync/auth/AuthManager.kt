package com.example.photosync.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tokenManager: com.example.photosync.data.local.TokenManager
) {
    // Photos Library scope set aligned with the 2025 API scope model.
    val scopeRead = Scope("https://www.googleapis.com/auth/photoslibrary.readonly.appcreateddata")
    val scopeAppend = Scope("https://www.googleapis.com/auth/photoslibrary.appendonly")

    private val gso by lazy {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestId() // openid
            .requestScopes(scopeRead, scopeAppend)
            .build()
    }

    fun getSignInIntent(): Intent {
        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    fun hasPermissions(account: GoogleSignInAccount): Boolean {
        return GoogleSignIn.hasPermissions(account, scopeRead, scopeAppend)
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        val client = GoogleSignIn.getClient(context, gso)
        try {
            Tasks.await(client.signOut())
            Tasks.await(client.revokeAccess())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun clearToken(token: String) = withContext(Dispatchers.IO) {
        // AuthorizationClient token invalidation API is not available in current dependency set.
        // Keep this as a no-op to avoid revoking account access during normal token refresh flow.
        android.util.Log.d("AuthManager", "clearToken no-op for token prefix: ${token.take(6)}")
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val accountRef = account.account
            if (accountRef == null) {
                android.util.Log.e("AuthManager", "Missing account reference from GoogleSignInAccount")
                return@withContext null
            }

            val granted = account.grantedScopes.joinToString { it.scopeUri }
            android.util.Log.d("AuthManager", "Account has scopes: $granted")

            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(scopeRead, scopeAppend))
                .setAccount(accountRef)
                .build()

            val authResult = Tasks.await(Identity.getAuthorizationClient(context).authorize(request))

            if (authResult.hasResolution()) {
                val msg = "Authorization requires user resolution. Please sign in again."
                tokenManager.saveDiagnostic("tokeninfo", msg)
                android.util.Log.w("AuthManager", msg)
                return@withContext null
            }

            val token = authResult.accessToken
            if (token.isNullOrEmpty()) {
                android.util.Log.e("AuthManager", "Authorization succeeded but access token is empty")
                return@withContext null
            }

            val (hasPhotosScope, tokenInfoBody) = validateAccessTokenForPhotosAndBody(token)
            if (!hasPhotosScope) {
                if (!tokenInfoBody.isNullOrEmpty()) {
                    tokenManager.saveDiagnostic("tokeninfo", tokenInfoBody)
                }
                android.util.Log.e("AuthManager", "Token does not contain expected Photos scopes")
                return@withContext null
            }

            token
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AuthManager", "Error getting access token", e)
            null
        }
    }

    // Return pair(hasPhotosScope, tokeninfoBody).
    private suspend fun validateAccessTokenForPhotosAndBody(token: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(token, "UTF-8")
            val url = URL("https://oauth2.googleapis.com/tokeninfo?access_token=$encoded")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            android.util.Log.d("AuthManager", "tokeninfo response code=$code body=$body")
            conn.disconnect()

            try {
                val json = org.json.JSONObject(body)
                if (json.has("error")) {
                    return@withContext Pair(false, body)
                }
                val scopeStr = json.optString("scope")
                if (scopeStr.contains("photoslibrary")) return@withContext Pair(true, body)
            } catch (e: Exception) {
                android.util.Log.e("AuthManager", "Failed parsing tokeninfo JSON", e)
            }

            Pair(false, body)
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "tokeninfo call failed", e)
            Pair(false, null)
        }
    }

    companion object {
        @JvmStatic
        fun parseTokenInfoJson(body: String?): Boolean {
            if (body.isNullOrEmpty()) return false
            return try {
                val json = org.json.JSONObject(body)
                if (json.has("error")) return false
                val scopeStr = json.optString("scope")
                scopeStr.contains("photoslibrary")
            } catch (e: Exception) {
                false
            }
        }
    }
}
