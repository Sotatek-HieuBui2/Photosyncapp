package com.example.photosync.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
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
    // Các quyền cần thiết
    // Quay lại sử dụng quyền Granular (Cụ thể) để đảm bảo an toàn và khớp với API
    val scopeRead = Scope("https://www.googleapis.com/auth/photoslibrary.readonly")
    val scopeAppend = Scope("https://www.googleapis.com/auth/photoslibrary.appendonly")
    val masterScope = Scope("https://www.googleapis.com/auth/photoslibrary")
    
    // Cấu hình GSO chung
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
        try {
            GoogleAuthUtil.clearToken(context, token)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getAccessToken(account: GoogleSignInAccount): String? = withContext(Dispatchers.IO) {
        try {
            val granted = account.grantedScopes.joinToString { it.scopeUri }
            android.util.Log.d("AuthManager", "Account has scopes: $granted")

            // Try multiple scope combinations and pick the first token whose tokeninfo contains Photos scopes
            val scopeCombos = listOf(
                // preferred: granular read + append
                "oauth2:https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/photoslibrary.appendonly",
                // master scope
                "oauth2:https://www.googleapis.com/auth/photoslibrary",
                // include userinfo full URIs
                "oauth2:https://www.googleapis.com/auth/userinfo.email https://www.googleapis.com/auth/userinfo.profile openid https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/photoslibrary.appendonly",
                // single scopes
                "oauth2:https://www.googleapis.com/auth/photoslibrary.readonly",
                "oauth2:https://www.googleapis.com/auth/photoslibrary.appendonly"
            )

            var lastTokenInfoBody: String? = null
            for (scopes in scopeCombos) {
                android.util.Log.d("AuthManager", "Attempting token request for scopes: $scopes")
                try {
                    // clear any cached token for this exact scope string
                    try {
                        val old = GoogleAuthUtil.getToken(context, account.account!!, scopes)
                        if (old != null) {
                            GoogleAuthUtil.clearToken(context, old)
                        }
                    } catch (e: Exception) {
                        // ignore
                    }

                    val token = GoogleAuthUtil.getToken(context, account.account!!, scopes)
                    android.util.Log.d("AuthManager", "Token for scopes [$scopes]: ${token?.take(10)}...")
                    if (!token.isNullOrEmpty()) {
                        val (hasPhotos, infoBody) = validateAccessTokenForPhotosAndBody(token)
                        lastTokenInfoBody = infoBody
                        if (hasPhotos) {
                            android.util.Log.d("AuthManager", "Selected token for scopes: $scopes")
                            return@withContext token
                        } else {
                            android.util.Log.d("AuthManager", "Token does not contain Photos scopes for scopes: $scopes")
                            try { GoogleAuthUtil.clearToken(context, token) } catch (_: Exception) {}
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AuthManager", "Error requesting token for scopes: $scopes", e)
                }
            }

            android.util.Log.e("AuthManager", "No token with Photos scopes obtained after trying combos")
            // Save diagnostic tokeninfo body so UI can display it
            if (!lastTokenInfoBody.isNullOrEmpty()) {
                try {
                    tokenManager.saveDiagnostic("tokeninfo", lastTokenInfoBody)
                } catch (e: Exception) {
                    android.util.Log.e("AuthManager", "Failed saving diagnostic", e)
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AuthManager", "Error getting access token", e)
            null
        }
    }

    // Diagnostic helper: call OAuth2 tokeninfo endpoint and return whether it contains Photos scopes
    // Return pair(hasPhotosScope, tokeninfoBody)
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

    // Public helper is available on the companion object for testability.

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
