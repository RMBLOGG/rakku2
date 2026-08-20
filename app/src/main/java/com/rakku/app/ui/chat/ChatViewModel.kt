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

class ChatViewModel(
    private val supabaseRepository: SupabaseRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Loading)
    val uiState: StateFlow<ChatUiState> = _uiState

    init {
        startPollingChat()
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

    fun sendMessage(msgText: String) {
        if (msgText.isBlank()) return
        viewModelScope.launch {
            val success = supabaseRepository.sendGlobalChatMessage(msgText)
            if (success) {
                fetchMessages()
            }
        }
    }
}
