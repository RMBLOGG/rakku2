package com.rakku.app.ui.clan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.ClanDetail
import com.rakku.app.data.model.ClanMemberInfo
import com.rakku.app.data.model.ClanSummary
import com.rakku.app.data.model.MyClanMembership
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Biaya bikin clan. Ditegakkan ulang di server (RPC create_clan), nilai di
// sini cuma buat ditampilkan di UI (dialog konfirmasi, dsb).
const val CLAN_CREATE_COST = 5500

sealed class ClanLeaderboardUiState {
    object Loading : ClanLeaderboardUiState()
    data class Success(val clans: List<ClanSummary>) : ClanLeaderboardUiState()
    data class Error(val message: String) : ClanLeaderboardUiState()
}

sealed class ClanDetailUiState {
    object Loading : ClanDetailUiState()
    data class Success(val detail: ClanDetail, val members: List<ClanMemberInfo>) : ClanDetailUiState()
    data class Error(val message: String) : ClanDetailUiState()
}

// Event sekali-tayang buat nampilin Snackbar/Toast hasil aksi (bikin,
// gabung, donasi, klaim, dst) tanpa nge-trigger ulang tiap kali state
// di-collect lagi (mis. rotasi layar).
sealed class ClanEvent {
    data class Message(val text: String) : ClanEvent()
}

class ClanViewModel(
    private val sessionManager: SessionManager,
    private val supabaseRepository: SupabaseRepository
) : ViewModel() {

    private val _leaderboardState = MutableStateFlow<ClanLeaderboardUiState>(ClanLeaderboardUiState.Loading)
    val leaderboardState: StateFlow<ClanLeaderboardUiState> = _leaderboardState

    private val _detailState = MutableStateFlow<ClanDetailUiState>(ClanDetailUiState.Loading)
    val detailState: StateFlow<ClanDetailUiState> = _detailState

    // Keanggotaan clan milik user sendiri - dipakai buat tau apakah user
    // sudah punya clan (kalau sudah, tombol "Bikin Clan"/"Gabung" diganti
    // jadi shortcut ke clan-nya sendiri).
    private val _myMembership = MutableStateFlow<MyClanMembership?>(null)
    val myMembership: StateFlow<MyClanMembership?> = _myMembership

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy

    private val _events = MutableStateFlow<ClanEvent?>(null)
    val events: StateFlow<ClanEvent?> = _events

    fun consumeEvent() {
        _events.value = null
    }

    fun loadLeaderboard(query: String? = null) {
        viewModelScope.launch {
            _leaderboardState.value = ClanLeaderboardUiState.Loading
            try {
                val clans = supabaseRepository.searchClans(query)
                _leaderboardState.value = ClanLeaderboardUiState.Success(clans)
            } catch (e: Exception) {
                _leaderboardState.value = ClanLeaderboardUiState.Error(e.message ?: "Gagal memuat leaderboard clan")
            }
        }
    }

    fun refreshMyMembership() {
        val userId = sessionManager.getUserId() ?: run {
            _myMembership.value = null
            return
        }
        viewModelScope.launch {
            _myMembership.value = supabaseRepository.getMyClanMembership(userId)
        }
    }

    fun loadClanDetail(clanId: String) {
        viewModelScope.launch {
            _detailState.value = ClanDetailUiState.Loading
            try {
                val detail = supabaseRepository.getClanDetail(clanId)
                if (detail == null) {
                    _detailState.value = ClanDetailUiState.Error("Clan tidak ditemukan")
                    return@launch
                }
                val members = supabaseRepository.getClanMembers(clanId)
                _detailState.value = ClanDetailUiState.Success(detail, members)
            } catch (e: Exception) {
                _detailState.value = ClanDetailUiState.Error(e.message ?: "Gagal memuat detail clan")
            }
        }
    }

    fun createClan(name: String, description: String?, tag: String?, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val (result, clanId) = supabaseRepository.createClan(name, description, tag, null)
            _isBusy.value = false
            _events.value = ClanEvent.Message(clanActionMessage(result, forCreate = true))
            if (result is SupabaseRepository.ClanActionResult.Success && clanId != null) {
                refreshMyMembership()
                onResult(clanId)
            } else {
                onResult(null)
            }
        }
    }

    fun joinClan(clanId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = supabaseRepository.joinClan(clanId)
            _isBusy.value = false
            _events.value = ClanEvent.Message(clanActionMessage(result))
            val success = result is SupabaseRepository.ClanActionResult.Success
            if (success) refreshMyMembership()
            onResult(success)
        }
    }

    fun leaveClan(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = supabaseRepository.leaveClan()
            _isBusy.value = false
            _events.value = ClanEvent.Message(clanActionMessage(result))
            val success = result is SupabaseRepository.ClanActionResult.Success
            if (success) {
                _myMembership.value = null
            }
            onResult(success)
        }
    }

    fun donateToClan(clanId: String, amount: Int) {
        viewModelScope.launch {
            _isBusy.value = true
            val (result, newLevel) = supabaseRepository.donateToClan(amount)
            _isBusy.value = false
            val message = when (result) {
                is SupabaseRepository.ClanActionResult.Success ->
                    if (newLevel != null) "Donasi berhasil! Clan sekarang Level $newLevel." else "Donasi berhasil!"
                else -> clanActionMessage(result)
            }
            _events.value = ClanEvent.Message(message)
            if (result is SupabaseRepository.ClanActionResult.Success) {
                refreshMyMembership()
                loadClanDetail(clanId)
            }
        }
    }

    fun claimDailyReward() {
        viewModelScope.launch {
            _isBusy.value = true
            val (result, reward) = supabaseRepository.claimDailyClanReward()
            _isBusy.value = false
            val message = when (result) {
                is SupabaseRepository.ClanActionResult.Success ->
                    if (reward != null) "Daily Claim berhasil! +$reward RC" else "Daily Claim berhasil!"
                else -> clanActionMessage(result)
            }
            _events.value = ClanEvent.Message(message)
            if (result is SupabaseRepository.ClanActionResult.Success) {
                refreshMyMembership()
            }
        }
    }

    private fun clanActionMessage(result: SupabaseRepository.ClanActionResult, forCreate: Boolean = false): String {
        return when (result) {
            is SupabaseRepository.ClanActionResult.Success -> if (forCreate) "Clan berhasil dibuat!" else "Berhasil!"
            is SupabaseRepository.ClanActionResult.InsufficientCoin -> "Rakku Coin kamu gak cukup."
            is SupabaseRepository.ClanActionResult.AlreadyInClan -> "Kamu sudah tergabung di sebuah clan."
            is SupabaseRepository.ClanActionResult.NotInClan -> "Kamu belum tergabung di clan manapun."
            is SupabaseRepository.ClanActionResult.ClanFull -> "Clan ini sudah penuh."
            is SupabaseRepository.ClanActionResult.ClanNotFound -> "Clan tidak ditemukan."
            is SupabaseRepository.ClanActionResult.NameTaken -> "Nama clan sudah dipakai, coba nama lain."
            is SupabaseRepository.ClanActionResult.InvalidName -> "Nama clan minimal 3 karakter."
            is SupabaseRepository.ClanActionResult.TagTaken -> "Tag clan sudah dipakai, coba tag lain."
            is SupabaseRepository.ClanActionResult.InvalidTag -> "Tag clan 2-5 karakter, huruf/angka aja."
            is SupabaseRepository.ClanActionResult.AlreadyClaimedToday -> "Kamu sudah klaim Daily Claim hari ini."
            is SupabaseRepository.ClanActionResult.Error -> "Terjadi kesalahan, coba lagi."
        }
    }
}
