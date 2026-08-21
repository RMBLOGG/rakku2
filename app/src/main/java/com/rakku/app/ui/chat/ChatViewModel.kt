package com.rakku.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.GlobalChatMessage
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class ChatUiState {
    object Loading : ChatUiState()
    data class Success(val messages: List<GlobalChatMessage>) : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

// Cooldown antar pesan (UX di app) - HARUS SAMA dengan v_cooldown_seconds
// di trigger enforce_chat_cooldown (chat_cooldown_migration.sql). Ini cuma
// buat nge-disable tombol kirim & kasih hitung mundur; validasi sebenarnya
// tetap di server, jadi biarpun angka di sini beda/dilewatin, server tetap
// nolak insert yang kekencengan.
const val CHAT_COOLDOWN_SECONDS = 3

class ChatViewModel(
    private val supabaseRepository: SupabaseRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    // Sisa detik cooldown sebelum boleh kirim pesan lagi. 0 = boleh kirim.
    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds

    private val _sendError = MutableStateFlow<String?>(null)
    val sendError: StateFlow<String?> = _sendError

    private var cooldownJob: kotlinx.coroutines.Job? = null

    init {
        startPollingChat()
    }

    fun consumeSendError() {
        _sendError.value = null
    }

    private fun startPollingChat() {
        viewModelScope.launch {
            while (isActive) {
                fetchMessages()
                delay(3000) // Poll every 3 seconds for realtime feel
            }
        }
    }

    fun fetchMessages() {
        viewModelScope.launch {
            try {
                val msgs = supabaseRepository.getGlobalChatMessages()
                _uiState.value = ChatUiState.Success(msgs)
            } catch (e: Exception) {
                if (_uiState.value !is ChatUiState.Success) {
                    _uiState.value = ChatUiState.Error(e.message ?: "Gagal memuat pesan chat")
                }
            }
        }
    }

    // Mulai/lanjutin hitung mundur tombol kirim. Dipanggil setelah kirim
    // sukses (pakai durasi penuh) ATAU kalau server nolak karena cooldown
    // (pakai sisa detik yang dikasih server, biar sinkron walau misal jam
    // device user ngaco).
    private fun startCooldown(seconds: Int) {
        if (seconds <= 0) return
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            _cooldownSeconds.value = seconds
            while (_cooldownSeconds.value > 0) {
                delay(1000)
                _cooldownSeconds.value = (_cooldownSeconds.value - 1).coerceAtLeast(0)
            }
        }
    }

    fun sendMessage(msgText: String) {
        if (msgText.isBlank()) return
        if (_cooldownSeconds.value > 0) return // masih cooldown, gak usah spam-request ke server
        viewModelScope.launch {
            when (val result = supabaseRepository.sendGlobalChatMessage(msgText)) {
                is SupabaseRepository.SendChatResult.Success -> {
                    startCooldown(CHAT_COOLDOWN_SECONDS)
                    fetchMessages()
                }
                is SupabaseRepository.SendChatResult.Cooldown -> {
                    // Server yang nolak (misalnya request kekirim dobel/race)
                    // - sinkronin hitung mundur app pakai sisa detik dari
                    // server, dan kasih tau usernya.
                    val remaining = result.remainingSeconds?.let { kotlin.math.ceil(it).toInt() } ?: CHAT_COOLDOWN_SECONDS
                    startCooldown(remaining)
                    _sendError.value = "Tunggu sebentar sebelum kirim pesan lagi."
                }
                is SupabaseRepository.SendChatResult.Error -> {
                    _sendError.value = result.message
                }
            }
        }
    }
}
