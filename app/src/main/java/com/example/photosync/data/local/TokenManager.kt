package com.example.photosync.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val settingsPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    }

    fun saveAccessToken(token: String) {
        sharedPreferences.edit().putString("access_token", token).apply()
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("access_token", null)
    }

    fun saveEmail(email: String) {
        sharedPreferences.edit().putString("user_email", email).apply()
    }

    fun getEmail(): String? {
        return sharedPreferences.getString("user_email", null)
    }
    
    fun clear() {
        sharedPreferences.edit().clear().apply()
        settingsPrefs.edit().clear().apply()
    }

    fun saveAutoSyncState(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("auto_sync_enabled", isEnabled).apply()
    }

    fun isAutoSyncEnabled(): Boolean {
        return settingsPrefs.getBoolean("auto_sync_enabled", false)
    }

    fun saveLastScanTime(timestamp: Long) {
        settingsPrefs.edit().putLong("last_scan_time", timestamp).apply()
    }

    fun getLastScanTime(): Long {
        return settingsPrefs.getLong("last_scan_time", 0L)
    }
}
