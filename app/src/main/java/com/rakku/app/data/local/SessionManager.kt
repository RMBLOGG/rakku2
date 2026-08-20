package com.rakku.app.data.local

import android.content.Context
import android.content.SharedPreferences
import com.rakku.app.data.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rakku_session", Context.MODE_PRIVATE)

    private val _accessToken = MutableStateFlow<String?>(getAccessToken())
    val accessToken: StateFlow<String?> = _accessToken

    private val _userId = MutableStateFlow<String?>(getUserId())
    val userId: StateFlow<String?> = _userId

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile

    fun saveSession(token: String, uId: String) {
        prefs.edit().putString("access_token", token).putString("user_id", uId).apply()
        _accessToken.value = token
        _userId.value = uId
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)
    fun getUserId(): String? = prefs.getString("user_id", null)

    fun updateProfile(profile: UserProfile?) {
        _currentUserProfile.value = profile
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        _accessToken.value = null
        _userId.value = null
        _currentUserProfile.value = null
    }

    fun isLoggedIn(): Boolean {
        return !getAccessToken().isNullOrEmpty() && !getUserId().isNullOrEmpty()
    }
}
