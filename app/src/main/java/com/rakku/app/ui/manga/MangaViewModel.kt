package com.rakku.app.ui.manga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.MangaDetailResponse
import com.rakku.app.data.model.MangaDownloadResponse
import com.rakku.app.data.model.MangaItem
import com.rakku.app.data.remote.RakkuApiRepository
import com.rakku.app.data.remote.SupabaseRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class MangaListUiState {
    object Loading : MangaListUiState()
    data class Success(val latest: List<MangaItem>, val popular: List<MangaItem>) : MangaListUiState()
    data class Error(val message: String) : MangaListUiState()
}

sealed class MangaDetailUiState {
    object Idle : MangaDetailUiState()
    object Loading : MangaDetailUiState()
    // "url" ditambahin di sini (bukan cuma di parameter loadMangaDetail) supaya
    // pas loadChapter() dipanggil dari halaman reader, kita masih bisa tau ini
    // chapter dari manga yang mana buat disimpan sebagai ref_id riwayat baca.
    data class Success(val url: String, val detail: MangaDetailResponse, val isBookmarked: Boolean) : MangaDetailUiState()
    data class Error(val message: String) : MangaDetailUiState()
}

sealed class MangaReaderUiState {
    object Idle : MangaReaderUiState()
    object Loading : MangaReaderUiState()
    data class Success(val chapterData: MangaDownloadResponse) : MangaReaderUiState()
    data class Error(val message: String) : MangaReaderUiState()
}

class MangaViewModel(
    private val rakkuApiRepository: RakkuApiRepository,
    private val supabaseRepository: SupabaseRepository,
    val sessionManager: SessionManager
) : ViewModel() {

    private val _listState = MutableStateFlow<MangaListUiState>(MangaListUiState.Loading)
    val listState: StateFlow<MangaListUiState> = _listState

    private val _detailState = MutableStateFlow<MangaDetailUiState>(MangaDetailUiState.Idle)
    val detailState: StateFlow<MangaDetailUiState> = _detailState

    private val _readerState = MutableStateFlow<MangaReaderUiState>(MangaReaderUiState.Idle)
    val readerState: StateFlow<MangaReaderUiState> = _readerState

    var searchQuery = MutableStateFlow("")

    // Debounce job buat search - biar gak nembak API tiap keystroke (ini yang
    // bikin manga gampang kena HTTP 429, soalnya Sanka API gak dikasih cache
    // kayak animeinwebApi). Job lama di-cancel tiap kali user ngetik lagi.
    private var searchJob: Job? = null

    init {
        loadMangaList()
    }

    fun loadMangaList() {
        searchQuery.value = ""
        viewModelScope.launch {
            _listState.value = MangaListUiState.Loading
            try {
                val res = rakkuApiRepository.getMangaHome()
                val latest = res.data ?: res.latest ?: emptyList()
                val popular = res.popular ?: emptyList()
                _listState.value = MangaListUiState.Success(latest, popular)
            } catch (e: Exception) {
                _listState.value = MangaListUiState.Error(e.message ?: "Gagal memuat manga")
            }
        }
    }

    fun searchManga(query: String) {
        searchQuery.value = query
        searchJob?.cancel() // batalin pencarian sebelumnya yang belum sempet jalan
        if (query.isBlank()) {
            loadMangaList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(400) // debounce - tunggu user berenti ngetik dulu baru fire API
            _listState.value = MangaListUiState.Loading
            try {
                val res = rakkuApiRepository.searchManga(query)
                val searchResults = res.data ?: res.latest ?: emptyList()
                _listState.value = MangaListUiState.Success(searchResults, emptyList())
            } catch (e: Exception) {
                _listState.value = MangaListUiState.Error(e.message ?: "Gagal mencari manga")
            }
        }
    }

    fun loadMangaDetail(url: String) {
        viewModelScope.launch {
            _detailState.value = MangaDetailUiState.Loading
            try {
                val detail = rakkuApiRepository.getMangaDetail(url)
                val userId = sessionManager.getUserId()
                var isBookmarked = false
                if (userId != null) {
                    val bookmarks = supabaseRepository.getBookmarks(userId)
                    isBookmarked = bookmarks.any { it.ref_id == url && it.content_type == "manga" }
                }
                _detailState.value = MangaDetailUiState.Success(url, detail, isBookmarked)
            } catch (e: Exception) {
                _detailState.value = MangaDetailUiState.Error(e.message ?: "Gagal memuat detail manga")
            }
        }
    }

    fun toggleBookmark(url: String, title: String, thumb: String?) {
        val userId = sessionManager.getUserId() ?: return
        val current = _detailState.value
        if (current is MangaDetailUiState.Success) {
            viewModelScope.launch {
                if (current.isBookmarked) {
                    val bookmarks = supabaseRepository.getBookmarks(userId)
                    val target = bookmarks.firstOrNull { it.ref_id == url && it.content_type == "manga" }
                    target?.id?.let { supabaseRepository.removeBookmark(it) }
                    _detailState.value = current.copy(isBookmarked = false)
                } else {
                    supabaseRepository.addBookmark(userId, "manga", url, title, thumb)
                    _detailState.value = current.copy(isBookmarked = true)
                }
            }
        }
    }

    fun loadChapter(chapterUrl: String) {
        viewModelScope.launch {
            _readerState.value = MangaReaderUiState.Loading
            try {
                val res = rakkuApiRepository.getMangaChapter(chapterUrl)
                _readerState.value = MangaReaderUiState.Success(res)

                // Save Manga Reading History - judul, poster, & ref_id (url manga) diambil
                // dari detailState (halaman detail manga yang dikunjungi sebelum masuk ke
                // reader ini), sama persis pola yang dipakai AnimeViewModel buat riwayat
                // tontonan. Nama chapter dicari dari daftar chapters di detail, dicocokin
                // lewat url-nya.
                val userId = sessionManager.getUserId()
                val currentDetail = _detailState.value
                if (userId != null && currentDetail is MangaDetailUiState.Success) {
                    val chapterName = currentDetail.detail.chapters
                        ?.firstOrNull { it.url == chapterUrl }
                        ?.title
                        ?: "Chapter"
                    supabaseRepository.saveMangaHistory(
                        userId = userId,
                        refId = currentDetail.url,
                        title = currentDetail.detail.title ?: currentDetail.url,
                        thumb = currentDetail.detail.thumb,
                        progressId = chapterUrl,
                        progressName = chapterName
                    )
                }
            } catch (e: Exception) {
                _readerState.value = MangaReaderUiState.Error(e.message ?: "Gagal memuat chapter manga")
            }
        }
    }
}
