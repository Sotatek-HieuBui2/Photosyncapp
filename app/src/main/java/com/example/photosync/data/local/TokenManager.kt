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

    fun saveDiagnostic(key: String, value: String) {
        val sanitized = try {
            // Try to parse JSON and redact well-known sensitive fields
            val obj = org.json.JSONObject(value)
            val sensitive = listOf("access_token", "id_token", "refresh_token")
            for (s in sensitive) {
                if (obj.has(s)) obj.put(s, "<redacted>")
            }
            obj.toString()
        } catch (e: Exception) {
            // Not JSON or parsing failed — store a truncated version to avoid leaking long tokens
            if (value.length > 2000) value.substring(0, 2000) else value
        }

        settingsPrefs.edit().putString("diag_" + key, sanitized).apply()
    }

    fun getDiagnostic(key: String): String? {
        return settingsPrefs.getString("diag_" + key, null)
    }

    fun saveAutoSyncState(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("auto_sync_enabled", isEnabled).apply()
    }

    fun isAutoSyncEnabled(): Boolean {
        return settingsPrefs.getBoolean("auto_sync_enabled", false)
    }

    fun saveWifiOnly(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("wifi_only", isEnabled).apply()
    }

    fun isWifiOnly(): Boolean {
        return settingsPrefs.getBoolean("wifi_only", true) // Default to true for safety
    }

    fun saveLastScanTime(timestamp: Long) {
        settingsPrefs.edit().putLong("last_scan_time", timestamp).apply()
    }

    fun getLastScanTime(): Long {
        return settingsPrefs.getLong("last_scan_time", 0L)
    }

    // Cloud sync feature flag (user can disable cloud sync entirely)
    fun saveCloudSyncEnabled(isEnabled: Boolean) {
        settingsPrefs.edit().putBoolean("cloud_sync_enabled", isEnabled).apply()
    }

    fun isCloudSyncEnabled(): Boolean {
        return settingsPrefs.getBoolean("cloud_sync_enabled", true)
    }
}
