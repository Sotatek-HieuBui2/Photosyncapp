package com.example.photosync.auth

import android.content.Context
import com.example.photosync.data.local.TokenManager
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class AuthManagerTest {

    @Test
    fun parseTokenInfo_withPhotosScope_returnsTrue() {
        val body = """
            {
              "audience": "...",
              "scope": "https://www.googleapis.com/auth/photoslibrary.readonly https://www.googleapis.com/auth/photoslibrary.appendonly",
              "expires_in": 3599
            }
        """
        val json = org.json.JSONObject(body)
        val scope = json.optString("scope")
        println("DEBUG scope: '$scope'")
        val result = AuthManager.parseTokenInfoJson(body)
        assertTrue("parse result was $result for body: $body", result)
    }

    @Test
    fun parseTokenInfo_withoutPhotosScope_returnsFalse() {
        val body = """
            {
              "audience": "...",
              "scope": "https://www.googleapis.com/auth/userinfo.email openid",
              "expires_in": 3599
            }
        """
        assertFalse(AuthManager.parseTokenInfoJson(body))
    }

    @Test
    fun parseTokenInfo_errorField_returnsFalse() {
        val body = """
            { "error": "invalid_token", "error_description": "Invalid Value" }
        """
        assertFalse(AuthManager.parseTokenInfoJson(body))
    }

    @Test
    fun parseTokenInfo_malformedJson_returnsFalse() {
        val body = "not a json"
        assertFalse(AuthManager.parseTokenInfoJson(body))
    }

    @Test
    fun parseTokenInfo_nullOrEmpty_returnsFalse() {
        assertFalse(AuthManager.parseTokenInfoJson(null))
        assertFalse(AuthManager.parseTokenInfoJson(""))
    }
}
