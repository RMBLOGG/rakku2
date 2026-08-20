package com.rakku.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.UserProfile
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    // Registrasi berhasil (user kebuat di Supabase), tapi belum ada sesi login
    // karena "Confirm email" nyala - user harus klik link konfirmasi di email
    // dulu sebelum bisa login. Ini BUKAN kegagalan.
    object RegisteredCheckEmail : AuthUiState()
    data class Banned(val reason: String?, val until: String?) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(
    val sessionManager: SessionManager,
    val supabaseRepository: SupabaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Email dan password harus diisi")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val res = supabaseRepository.signIn(email, pass)
                if (res.access_token != null && res.user != null) {
                    checkUserProfileAndBan(res.user.id)
                } else {
                    _uiState.value = AuthUiState.Error(res.friendlyError ?: "Gagal login, periksa kembali email & password")
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Terjadi kesalahan jaringan")
            }
        }
    }

    fun register(email: String, pass: String, username: String) {
        if (email.isBlank() || pass.isBlank() || username.isBlank()) {
            _uiState.value = AuthUiState.Error("Semua kolom registrasi wajib diisi")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            try {
                val res = supabaseRepository.signUp(email, pass, username)
                when {
                    // Confirm email OFF (atau kasus lain yg langsung kasih sesi) -> langsung masuk
                    res.access_token != null && res.user != null -> checkUserProfileAndBan(res.user.id)
                    // Confirm email ON -> user KEBUAT tapi belum ada sesi, ini BERHASIL,
                    // bukan gagal. Bedain dari kegagalan asli pakai friendlyError == null.
                    // res.user != null nangkep bentuk response yang di-nest, res.id != null
                    // nangkep bentuk response yang gak di-nest (lihat komentar di AuthResponse).
                    (res.user != null || res.id != null) && res.friendlyError == null -> {
                        _uiState.value = AuthUiState.RegisteredCheckEmail
                    }
                    else -> {
                        _uiState.value = AuthUiState.Error(res.friendlyError ?: "Gagal pendaftaran akun baru")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = AuthUiState.Error(e.message ?: "Terjadi kesalahan jaringan")
            }
        }
    }

    private suspend fun checkUserProfileAndBan(userId: String) {
        val profile = supabaseRepository.fetchUserProfile(userId)
        if (profile == null) {
            _uiState.value = AuthUiState.Success
            return
        }

        // Check if banned
        if (profile.is_banned == true) {
            val bannedUntilStr = profile.banned_until
            val isBanExpired = isDatePassed(bannedUntilStr)

            if (isBanExpired) {
                // Clear expired ban and proceed
                supabaseRepository.clearExpiredBan(userId)
                supabaseRepository.fetchUserProfile(userId)
                _uiState.value = AuthUiState.Success
            } else {
                // Force logout and show ban state
                sessionManager.clearSession()
                _uiState.value = AuthUiState.Banned(
                    reason = profile.banned_reason ?: "Pelanggaran aturan komunitas",
                    until = profile.banned_until
                )
            }
        } else {
            _uiState.value = AuthUiState.Success
        }
    }

    private fun isDatePassed(dateStr: String?): Boolean {
        if (dateStr.isNullOrEmpty()) return false // null = permanent
        return try {
            val instant = Instant.parse(dateStr)
            Instant.now().isAfter(instant)
        } catch (e: Exception) {
            false
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}
