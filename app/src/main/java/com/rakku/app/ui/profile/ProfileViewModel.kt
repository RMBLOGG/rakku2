package com.rakku.app.ui.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.Announcement
import com.rakku.app.data.model.BookmarkItem
import com.rakku.app.data.model.CommentReport
import com.rakku.app.data.model.FeedbackReport
import com.rakku.app.data.model.HistoryItem
import com.rakku.app.data.model.ProfileBorder
import com.rakku.app.data.model.PublicProfileStats
import com.rakku.app.data.model.UserProfile
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// State buat halaman "Profil User Lain" yang dibuka dari klik nama/avatar
// di Obrolan Global.
sealed class PublicProfileUiState {
    object Loading : PublicProfileUiState()
    data class Success(val profile: PublicProfileStats, val history: List<HistoryItem>) : PublicProfileUiState()
    data class Error(val message: String) : PublicProfileUiState()
}

sealed class AdminUiState {
    object Idle : AdminUiState()
    object Loading : AdminUiState()
    data class Success(
        val users: List<UserProfile>,
        val feedbackList: List<FeedbackReport>,
        val commentReports: List<CommentReport>,
        val announcements: List<Announcement>
    ) : AdminUiState()
    data class Error(val message: String) : AdminUiState()
}

sealed class ShopUiState {
    object Loading : ShopUiState()
    data class Success(val borders: List<ProfileBorder>, val ownedIds: List<Long>) : ShopUiState()
    data class Error(val message: String) : ShopUiState()
}

class ProfileViewModel(
    val sessionManager: SessionManager,
    private val supabaseRepository: SupabaseRepository
) : ViewModel() {

    val userProfile = sessionManager.currentUserProfile

    private val _bookmarks = MutableStateFlow<List<BookmarkItem>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkItem>> = _bookmarks

    private val _history = MutableStateFlow<List<HistoryItem>>(emptyList())
    val history: StateFlow<List<HistoryItem>> = _history

    // Statistik profil sendiri (total komentar, total menit nonton) - diisi
    // bareng refreshProfile(). total_watch_minutes juga ada langsung di
    // UserProfile (kolom asli), tapi total_comments cuma tersedia lewat RPC
    // get_public_profile_stats, jadi dipisah ke sini biar 1 sumber data yang
    // sama dipakai ProfileScreen (Kartu Statistik) & PublicProfileScreen.
    private val _myStats = MutableStateFlow<PublicProfileStats?>(null)
    val myStats: StateFlow<PublicProfileStats?> = _myStats

    // Profil user LAIN, dibuka dari klik nama/avatar pengirim pesan di
    // Obrolan Global.
    private val _publicProfileState = MutableStateFlow<PublicProfileUiState>(PublicProfileUiState.Loading)
    val publicProfileState: StateFlow<PublicProfileUiState> = _publicProfileState

    private val _adminState = MutableStateFlow<AdminUiState>(AdminUiState.Idle)
    val adminState: StateFlow<AdminUiState> = _adminState

    private val _shopState = MutableStateFlow<ShopUiState>(ShopUiState.Loading)
    val shopState: StateFlow<ShopUiState> = _shopState

    // Daftar border (termasuk yang nonaktif) khusus buat panel admin.
    private val _adminBorders = MutableStateFlow<List<ProfileBorder>>(emptyList())
    val adminBorders: StateFlow<List<ProfileBorder>> = _adminBorders

    init {
        refreshProfile()
    }

    fun refreshProfile() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val p = supabaseRepository.fetchUserProfile(userId)
            if (p != null) {
                _bookmarks.value = supabaseRepository.getBookmarks(userId)
                _history.value = supabaseRepository.getWatchHistory(userId)
                _myStats.value = supabaseRepository.getPublicProfileStats(userId)
            }
        }
    }

    // ================= PROFIL PUBLIK (klik user di Obrolan Global) =================

    fun loadPublicProfile(userId: String) {
        viewModelScope.launch {
            _publicProfileState.value = PublicProfileUiState.Loading
            try {
                val profile = supabaseRepository.getPublicProfileStats(userId)
                if (profile == null) {
                    _publicProfileState.value = PublicProfileUiState.Error("Profil tidak ditemukan")
                    return@launch
                }
                val history = supabaseRepository.getPublicUserHistory(userId)
                _publicProfileState.value = PublicProfileUiState.Success(profile, history)
            } catch (e: Exception) {
                _publicProfileState.value = PublicProfileUiState.Error(e.message ?: "Gagal memuat profil")
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            if (supabaseRepository.removeBookmark(id)) {
                _bookmarks.value = _bookmarks.value.filter { it.id != id }
            }
        }
    }

    fun clearAllBookmarks() {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            if (supabaseRepository.clearAllBookmarks(userId)) {
                _bookmarks.value = emptyList()
            }
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            if (supabaseRepository.deleteHistoryItem(id)) {
                _history.value = _history.value.filter { it.id != id }
            }
        }
    }

    fun clearAllHistory(contentType: String) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            if (supabaseRepository.clearAllHistory(userId, contentType)) {
                _history.value = _history.value.filter { it.content_type != contentType }
            }
        }
    }

    fun updateProfileInfo(context: Context, newUsername: String?, avatarUri: Uri?, onComplete: (Boolean) -> Unit) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            var avatarUrl: String? = null
            if (avatarUri != null) {
                avatarUrl = supabaseRepository.uploadAvatar(context, userId, avatarUri)
            }
            val success = supabaseRepository.updateUserProfile(userId, newUsername, avatarUrl)
            if (success) {
                supabaseRepository.fetchUserProfile(userId)
            }
            onComplete(success)
        }
    }

    fun sendTopupProof(context: Context, proofUri: Uri, onResult: (Boolean, String?) -> Unit) {
        val userId = sessionManager.getUserId() ?: return
        viewModelScope.launch {
            val uploadResult = supabaseRepository.uploadTopupProof(context, userId, proofUri)
            if (uploadResult is SupabaseRepository.TopupProofResult.Error) {
                onResult(false, "Upload gagal: ${uploadResult.detail}")
                return@launch
            }
            val proofUrl = (uploadResult as SupabaseRepository.TopupProofResult.Success).proofUrl
            val insertResult = supabaseRepository.createTopupRequest(proofUrl)
            if (insertResult is SupabaseRepository.TopupProofResult.Error) {
                onResult(false, "Simpan gagal: ${insertResult.detail}")
            } else {
                onResult(true, null)
            }
        }
    }

    fun submitFeedback(type: String, message: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.submitFeedback(type, message)
            onResult(success)
        }
    }

    // ================= BORDER SHOP (USER) =================

    fun loadShop() {
        viewModelScope.launch {
            _shopState.value = ShopUiState.Loading
            try {
                val borders = supabaseRepository.getActiveBorders()
                val owned = supabaseRepository.getMyBorderIds()
                _shopState.value = ShopUiState.Success(borders, owned)
            } catch (e: Exception) {
                _shopState.value = ShopUiState.Error(e.message ?: "Gagal memuat toko border")
            }
        }
    }

    fun buyBorder(borderId: Long, onResult: (SupabaseRepository.BuyBorderResult) -> Unit) {
        viewModelScope.launch {
            val result = supabaseRepository.buyBorder(borderId)
            if (result is SupabaseRepository.BuyBorderResult.Success) loadShop()
            onResult(result)
        }
    }

    fun equipBorder(borderId: Long?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.equipBorder(borderId)
            onResult(success)
        }
    }

    // ADMIN FUNCTIONS
    fun loadAdminData() {
        viewModelScope.launch {
            _adminState.value = AdminUiState.Loading
            try {
                val users = supabaseRepository.getAllProfiles()
                val feedback = supabaseRepository.getFeedbackReports()
                val commentReports = supabaseRepository.getCommentReports()
                val announcements = supabaseRepository.getAllAnnouncements()

                _adminState.value = AdminUiState.Success(
                    users = users,
                    feedbackList = feedback,
                    commentReports = commentReports,
                    announcements = announcements
                )
            } catch (e: Exception) {
                _adminState.value = AdminUiState.Error(e.message ?: "Gagal memuat data admin")
            }
        }
    }

    // ADMIN: KELOLA BORDER
    fun loadAdminBorders() {
        viewModelScope.launch {
            _adminBorders.value = supabaseRepository.getAllBordersAdmin()
        }
    }

    fun adminUploadBorder(context: Context, imageUri: Uri, name: String, priceCoin: Int, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val imageUrl = supabaseRepository.uploadBorderImage(context, imageUri)
            if (imageUrl == null) {
                onResult(false, "Upload gambar ke Cloudinary gagal. Cek apakah CLOUDINARY_CLOUD_NAME & CLOUDINARY_UPLOAD_PRESET sudah diisi di SupabaseRepository.kt")
                return@launch
            }
            val success = supabaseRepository.adminCreateBorder(name, imageUrl, priceCoin)
            if (success) loadAdminBorders()
            onResult(success, if (success) null else "Gagal menyimpan data border ke database")
        }
    }

    fun adminSetBorderActive(borderId: Long, active: Boolean) {
        viewModelScope.launch {
            if (supabaseRepository.adminSetBorderActive(borderId, active)) loadAdminBorders()
        }
    }

    fun adminDeleteBorder(borderId: Long, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = supabaseRepository.adminDeleteBorder(borderId)
            if (error == null) loadAdminBorders()
            onResult(error)
        }
    }

    fun adminBanUser(targetId: String, reason: String?, durationHours: Int?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.adminBanUser(targetId, reason, durationHours)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminUnbanUser(targetId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.adminUnbanUser(targetId)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminAddCoin(targetId: String, amount: Int, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.adminAddCoin(targetId, amount, null)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminSetRole(targetId: String, newRole: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = supabaseRepository.adminSetRole(targetId, newRole)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminSetLevel(targetId: String, newLevel: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = supabaseRepository.adminSetLevel(targetId, newLevel)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminAddExp(targetId: String, amount: Int, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = supabaseRepository.adminAddExp(targetId, amount)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun adminSetUnlimited(targetId: String, enabled: Boolean, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = supabaseRepository.adminSetUnlimited(targetId, enabled)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun createAnnouncement(title: String, content: String, active: Boolean, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.createAnnouncement(title, content, active)
            if (success) loadAdminData()
            onResult(success)
        }
    }

    fun toggleAnnouncement(id: String, active: Boolean) {
        viewModelScope.launch {
            if (supabaseRepository.toggleAnnouncement(id, active)) loadAdminData()
        }
    }

    fun deleteAnnouncement(id: String) {
        viewModelScope.launch {
            if (supabaseRepository.deleteAnnouncement(id)) loadAdminData()
        }
    }

    fun updateFeedbackStatus(id: String, status: String) {
        viewModelScope.launch {
            if (supabaseRepository.updateFeedbackStatus(id, status)) loadAdminData()
        }
    }

    fun deleteReportedComment(commentId: String) {
        viewModelScope.launch {
            if (supabaseRepository.deleteEpisodeComment(commentId)) loadAdminData()
        }
    }

    fun logout() {
        sessionManager.clearSession()
    }
}
