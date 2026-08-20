package com.rakku.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.model.Announcement
import com.rakku.app.data.model.AnimeItem
import com.rakku.app.data.model.MangaItem
import com.rakku.app.data.remote.RakkuApiRepository
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class HomeUiState {
    object Loading : HomeUiState()
    data class Success(
        val announcements: List<Announcement>,
        val latestAnime: List<AnimeItem>,
        val latestManga: List<MangaItem>
    ) : HomeUiState()
    data class Error(val message: String) : HomeUiState()
}

class HomeViewModel(
    private val rakkuApiRepository: RakkuApiRepository,
    private val supabaseRepository: SupabaseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState

    private var dismissedAnnouncementIds = mutableSetOf<String>()

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading
            try {
                val announcements = supabaseRepository.getActiveAnnouncements()
                    .filter { !dismissedAnnouncementIds.contains(it.id) }
                
                val animeHome = rakkuApiRepository.getAnimeHome()
                val mangaHome = rakkuApiRepository.getMangaHome()

                val animeList = animeHome.animes ?: animeHome.latest ?: animeHome.anime ?: animeHome.ongoing ?: emptyList()
                val mangaList = mangaHome.latest ?: mangaHome.data ?: emptyList()

                _uiState.value = HomeUiState.Success(
                    announcements = announcements,
                    latestAnime = animeList,
                    latestManga = mangaList
                )
            } catch (e: Exception) {
                _uiState.value = HomeUiState.Error(e.message ?: "Gagal memuat data beranda")
            }
        }
    }

    fun dismissAnnouncement(id: String) {
        dismissedAnnouncementIds.add(id)
        val current = _uiState.value
        if (current is HomeUiState.Success) {
            _uiState.value = current.copy(
                announcements = current.announcements.filter { it.id != id }
            )
        }
    }
}
