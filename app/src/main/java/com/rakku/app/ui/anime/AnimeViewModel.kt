package com.rakku.app.ui.anime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rakku.app.data.local.SessionManager
import com.rakku.app.data.model.AnimeDetailResponse
import com.rakku.app.data.model.AnimeEpisodeDetailResponse
import com.rakku.app.data.model.AnimeItem
import com.rakku.app.data.model.EpisodeComment
import com.rakku.app.data.model.GenreItem
import com.rakku.app.data.model.VideoSource
import com.rakku.app.data.remote.RakkuApiRepository
import com.rakku.app.data.remote.SupabaseRepository
import com.rakku.app.network.VideoExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AnimeListUiState {
    object Loading : AnimeListUiState()
    data class Success(
        val animeList: List<AnimeItem>,
        val genres: List<GenreItem>,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
        val currentPage: Int = 1
    ) : AnimeListUiState()
    data class Error(val message: String) : AnimeListUiState()
}

sealed class ScheduleUiState {
    object Loading : ScheduleUiState()
    data class Success(val items: List<com.rakku.app.data.model.AnimeinwebItem>) : ScheduleUiState()
    data class Error(val message: String) : ScheduleUiState()
}

sealed class AnimeDetailUiState {
    object Idle : AnimeDetailUiState()
    object Loading : AnimeDetailUiState()
    data class Success(val detail: AnimeDetailResponse, val isBookmarked: Boolean) : AnimeDetailUiState()
    data class Error(val message: String) : AnimeDetailUiState()
}

sealed class AnimePlayerUiState {
    object Idle : AnimePlayerUiState()
    object Loading : AnimePlayerUiState()
    data class Success(
        val episode: AnimeEpisodeDetailResponse,
        // Server yang lagi dipilih user (URL mentah dari daftar streamServers) -
        // dipakai buat highlight tombol server di UI. BUKAN yang diputar player.
        val selectedServerUrl: String?,
        // Hasil resolve VideoExtractor dari selectedServerUrl: kalau berhasil
        // dapet link mp4/m3u8 langsung -> diputar native (ExoPlayer). Kalau
        // gagal (host belum dikenal) -> fallback WebView, sama seperti sebelumnya.
        // Null selagi masih resolving.
        val activeSource: VideoSource?,
        val comments: List<EpisodeComment>
    ) : AnimePlayerUiState()
    data class Error(val message: String) : AnimePlayerUiState()
}

// Referer default buat resolve/WebView (embed page) - storages.animein.net (CDN
// animeinweb) NOLAK request tanpa Referer domain aslinya. Diganti dari
// sankavollerei.web.id karena sumber datanya udah full pindah ke animeinweb.
private const val DEFAULT_REFERER = "https://animeinweb.com/"

// Sistem EXP nonton anime - SAMA PERSIS kayak di website (anime.js):
// +10 EXP sekali pas buka episode, +2 EXP tiap 1 menit nonton (maksimal 10 menit
// per sesi buka episode, biar gak bisa di-farm dengan buka tab lama-lama).
private const val EXP_PER_EPISODE_OPEN = 10
private const val EXP_PER_MINUTE = 2
private const val EXP_MAX_MINUTES_PER_SESSION = 10

class AnimeViewModel(
    private val rakkuApiRepository: RakkuApiRepository,
    private val supabaseRepository: SupabaseRepository,
    val sessionManager: SessionManager,
    private val appContext: Context
) : ViewModel() {

    // Dipantau UI buat nampilin toast "+X EXP" pas berhasil dapet EXP baru.
    // Di-set null lagi sama UI setelah toast-nya ditampilkan.
    private val _expToast = MutableStateFlow<Int?>(null)
    val expToast: StateFlow<Int?> = _expToast

    private var expTimerJob: kotlinx.coroutines.Job? = null
    private var watchMinutesTimerJob: kotlinx.coroutines.Job? = null

    fun consumeExpToast() {
        _expToast.value = null
    }

    private fun awardExpAndNotify(eventKey: String, amount: Int) {
        viewModelScope.launch {
            val awarded = supabaseRepository.awardExp(eventKey, amount)
            if (awarded) {
                _expToast.value = amount
                // Cache profil di SessionManager (dipakai ProfileScreen, dll) gak
                // otomatis nge-refresh sendiri pas EXP nambah di database - harus
                // di-fetch ulang manual di sini, kalau enggak angka EXP di layar
                // Profil bakal keliatan "gak nambah" walau di database sebenernya
                // udah bertambah.
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    val freshProfile = supabaseRepository.fetchUserProfile(userId)
                    if (freshProfile != null) sessionManager.updateProfile(freshProfile)
                }
            }
        }
    }

    // Timer per-menit nonton, jalan selagi layar player kebuka (di-stop lewat
    // stopExpTimer() pas user keluar dari layar player - lihat DisposableEffect
    // di AnimePlayerScreen.kt). Kalau app di-minimize, coroutine ini otomatis gak
    // jalan (di-pause sistem), jadi otomatis udah sesuai maksud "tab tidak aktif,
    // jangan hitung" seperti behaviour di website.
    private fun startExpTimer(animeSlug: String, episodeSlug: String) {
        expTimerJob?.cancel()
        expTimerJob = viewModelScope.launch {
            var minuteCount = 0
            while (minuteCount < EXP_MAX_MINUTES_PER_SESSION) {
                kotlinx.coroutines.delay(60_000L)
                val key = "anime_minute:$animeSlug:$episodeSlug:$minuteCount"
                minuteCount++
                awardExpAndNotify(key, EXP_PER_MINUTE)
            }
        }
    }

    fun stopExpTimer() {
        expTimerJob?.cancel()
        expTimerJob = null
    }

    // Timer terpisah dari startExpTimer di atas: statistik "Total menit
    // menonton" di halaman Profil TIDAK dibatasi cap EXP_MAX_MINUTES_PER_SESSION,
    // jadi harus jalan sendiri (bukan numpang di loop yang sama) supaya nonton
    // lama tetap kehitung semua, bukan cuma 10 menit pertama. Jalan selagi
    // layar player kebuka, di-stop lewat stopWatchMinutesTimer() sama seperti
    // EXP timer.
    private fun startWatchMinutesTimer() {
        watchMinutesTimerJob?.cancel()
        watchMinutesTimerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                supabaseRepository.incrementWatchMinutes(1)
            }
        }
    }

    fun stopWatchMinutesTimer() {
        watchMinutesTimerJob?.cancel()
        watchMinutesTimerJob = null
    }

    private val _listState = MutableStateFlow<AnimeListUiState>(AnimeListUiState.Loading)
    val listState: StateFlow<AnimeListUiState> = _listState

    private val _detailState = MutableStateFlow<AnimeDetailUiState>(AnimeDetailUiState.Idle)
    val detailState: StateFlow<AnimeDetailUiState> = _detailState

    private val _scheduleState = MutableStateFlow<ScheduleUiState>(ScheduleUiState.Loading)
    val scheduleState: StateFlow<ScheduleUiState> = _scheduleState

    private val indoDays = listOf("SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU", "MINGGU")
    var selectedScheduleDay = MutableStateFlow(todayIndonesianDay())

    fun loadSchedule(day: String = selectedScheduleDay.value) {
        selectedScheduleDay.value = day
        viewModelScope.launch {
            _scheduleState.value = ScheduleUiState.Loading
            try {
                val items = rakkuApiRepository.getScheduleForDay(day)
                _scheduleState.value = ScheduleUiState.Success(items)
            } catch (e: Exception) {
                _scheduleState.value = ScheduleUiState.Error(e.message ?: "Gagal memuat jadwal")
            }
        }
    }

    fun goToPreviousScheduleDay() {
        val idx = indoDays.indexOf(selectedScheduleDay.value).let { if (it < 0) 0 else it }
        val prevIdx = (idx - 1 + indoDays.size) % indoDays.size
        loadSchedule(indoDays[prevIdx])
    }

    fun goToNextScheduleDay() {
        val idx = indoDays.indexOf(selectedScheduleDay.value).let { if (it < 0) 0 else it }
        val nextIdx = (idx + 1) % indoDays.size
        loadSchedule(indoDays[nextIdx])
    }

    private fun todayIndonesianDay(): String {
        val days = arrayOf("MINGGU", "SENIN", "SELASA", "RABU", "KAMIS", "JUMAT", "SABTU")
        val idx = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1
        return days.getOrElse(idx) { "MINGGU" }
    }

    private val _playerState = MutableStateFlow<AnimePlayerUiState>(AnimePlayerUiState.Idle)
    val playerState: StateFlow<AnimePlayerUiState> = _playerState

    var currentTab = MutableStateFlow("ongoing")
    var selectedGenre = MutableStateFlow<String?>(null)
    var searchQuery = MutableStateFlow("")

    init {
        loadAnimeList("ongoing")
    }

    fun loadAnimeList(type: String = currentTab.value) {
        currentTab.value = type
        selectedGenre.value = null
        searchQuery.value = ""
        viewModelScope.launch {
            _listState.value = AnimeListUiState.Loading
            try {
                val genres = rakkuApiRepository.getAnimeGenres()
                val res = rakkuApiRepository.getAnimeHome(type = type, page = 0)
                val list = res.animes ?: res.anime ?: res.latest ?: res.ongoing ?: res.completed ?: res.movies ?: emptyList()
                _listState.value = AnimeListUiState.Success(
                    animeList = list,
                    genres = genres,
                    hasMore = res.pagination?.hasNext == true,
                    currentPage = res.pagination?.currentPage ?: 0
                )
            } catch (e: Exception) {
                _listState.value = AnimeListUiState.Error(e.message ?: "Gagal memuat anime")
            }
        }
    }

    // Dipanggil pas user scroll ke bawah - nambahin ke list yang udah ada,
    // BUKAN ngeganti/reset (beda sama loadAnimeList/searchAnime/filterByGenre).
    // PENTING: "currentPage" di state itu sebenernya nyimpen next_page CURSOR
    // dari server (bisa loncat lebih dari +1 karena backend kadang nyisir
    // beberapa halaman upstream sekaligus) - jadi di sini WAJIB dipake apa
    // adanya, JANGAN di-+1 lagi, atau data bakal ke-duplikat/ke-skip.
    // Guard tambahan di luar state Compose - dicek & di-set SYNCHRONOUS (bukan
    // nunggu coroutine jalan dulu), biar kalau LaunchedEffect somehow ke-trigger
    // 2x beruntun sebelum state sempet ke-update, request kedua tetep ke-block.
    @Volatile
    private var isFetchingMore = false

    fun loadMoreAnime() {
        val current = _listState.value
        if (current !is AnimeListUiState.Success) return
        if (!current.hasMore || current.isLoadingMore || isFetchingMore) return
        isFetchingMore = true
        val cursorToUse = current.currentPage
        viewModelScope.launch {
            _listState.value = current.copy(isLoadingMore = true)
            try {
                val query = searchQuery.value
                val genre = selectedGenre.value
                val res = when {
                    query.isNotBlank() -> rakkuApiRepository.searchAnime(query, page = cursorToUse)
                    genre != null -> rakkuApiRepository.getAnimeByGenre(genre, page = cursorToUse)
                    else -> rakkuApiRepository.getAnimeHome(type = currentTab.value, page = cursorToUse)
                }
                val newItems = res.animes ?: res.anime ?: res.data ?: emptyList()
                val stateNow = _listState.value
                if (stateNow is AnimeListUiState.Success) {
                    _listState.value = stateNow.copy(
                        animeList = stateNow.animeList + newItems,
                        hasMore = res.pagination?.hasNext == true,
                        isLoadingMore = false,
                        currentPage = res.pagination?.currentPage ?: cursorToUse
                    )
                }
            } catch (e: Exception) {
                // Gagal load more gak boleh ngerusak list yang udah kebaca - cukup
                // matiin loading & hasMore, biar user gak keliatan error/list ilang.
                val stateNow = _listState.value
                if (stateNow is AnimeListUiState.Success) {
                    _listState.value = stateNow.copy(isLoadingMore = false, hasMore = false)
                }
            } finally {
                isFetchingMore = false
            }
        }
    }

    fun searchAnime(query: String) {
        searchQuery.value = query
        if (query.isBlank()) {
            loadAnimeList(currentTab.value)
            return
        }
        viewModelScope.launch {
            _listState.value = AnimeListUiState.Loading
            try {
                val genres = rakkuApiRepository.getAnimeGenres()
                val res = rakkuApiRepository.searchAnime(query, page = 0)
                val list = res.animes ?: res.anime ?: res.data ?: emptyList()
                _listState.value = AnimeListUiState.Success(
                    animeList = list,
                    genres = genres,
                    hasMore = res.pagination?.hasNext == true,
                    currentPage = res.pagination?.currentPage ?: 0
                )
            } catch (e: Exception) {
                _listState.value = AnimeListUiState.Error(e.message ?: "Gagal mencari anime")
            }
        }
    }

    fun filterByGenre(genreSlug: String) {
        selectedGenre.value = genreSlug
        viewModelScope.launch {
            _listState.value = AnimeListUiState.Loading
            try {
                val genres = rakkuApiRepository.getAnimeGenres()
                val res = rakkuApiRepository.getAnimeByGenre(genreSlug, page = 0)
                val list = res.anime ?: res.animeList ?: res.data ?: res.recent ?: res.result ?: res.animes ?: emptyList()
                _listState.value = AnimeListUiState.Success(
                    animeList = list,
                    genres = genres,
                    hasMore = res.pagination?.hasNext == true,
                    currentPage = res.pagination?.currentPage ?: 0
                )
            } catch (e: Exception) {
                _listState.value = AnimeListUiState.Error(e.message ?: "Gagal memuat genre")
            }
        }
    }

    fun loadAnimeDetail(slug: String) {
        viewModelScope.launch {
            _detailState.value = AnimeDetailUiState.Loading
            try {
                val detail = rakkuApiRepository.getAnimeDetail(slug)
                val userId = sessionManager.getUserId()
                var isBookmarked = false
                if (userId != null) {
                    val bookmarks = supabaseRepository.getBookmarks(userId)
                    isBookmarked = bookmarks.any { it.ref_id == slug && it.content_type == "anime" }
                }
                _detailState.value = AnimeDetailUiState.Success(detail, isBookmarked)
            } catch (e: Exception) {
                _detailState.value = AnimeDetailUiState.Error(e.message ?: "Gagal memuat detail anime")
            }
        }
    }

    fun toggleBookmark(slug: String, title: String, thumb: String?) {
        val userId = sessionManager.getUserId() ?: return
        val current = _detailState.value
        if (current is AnimeDetailUiState.Success) {
            viewModelScope.launch {
                if (current.isBookmarked) {
                    val bookmarks = supabaseRepository.getBookmarks(userId)
                    val target = bookmarks.firstOrNull { it.ref_id == slug && it.content_type == "anime" }
                    target?.id?.let { supabaseRepository.removeBookmark(it) }
                    _detailState.value = current.copy(isBookmarked = false)
                } else {
                    supabaseRepository.addBookmark(userId, "anime", slug, title, thumb)
                    _detailState.value = current.copy(isBookmarked = true)
                }
            }
        }
    }

    fun loadEpisodePlayer(animeSlug: String, episodeSlug: String) {
        viewModelScope.launch {
            _playerState.value = AnimePlayerUiState.Loading
            try {
                val episode = rakkuApiRepository.getAnimeEpisode(episodeSlug)
                val comments = supabaseRepository.getEpisodeComments(animeSlug, episodeSlug)

                // Save Watch History - judul & poster diambil dari ANIME (detailState),
                // bukan dari episode, soalnya episode.title cuma "Episode 6" doang dan
                // gak ada info poster sama sekali. detailState udah keisi duluan karena
                // user pasti mampir ke halaman detail anime dulu sebelum ke sini.
                val userId = sessionManager.getUserId()
                if (userId != null) {
                    val animeDetail = (_detailState.value as? AnimeDetailUiState.Success)?.detail
                    supabaseRepository.saveWatchHistory(
                        userId = userId,
                        refId = animeSlug,
                        title = animeDetail?.title ?: animeSlug,
                        thumb = animeDetail?.thumb,
                        progressId = episodeSlug,
                        progressName = episode.title ?: "Episode"
                    )
                }

                val firstUrl = episode.streamUrl ?: episode.streamServers?.firstOrNull()?.url
                // Coba ekstrak link video langsung (mp4/m3u8) via VideoExtractor -
                // sama persis cara kerja Kuroflix. Kalau host-nya gak dikenal, resolve()
                // balikin null dan VideoExtractor.resolveVideoUrl otomatis fallback
                // ke WebView (isEmbed = true) dengan redirect shortlink sudah di-follow.
                val source = if (firstUrl != null) {
                    runCatching {
                        VideoExtractor.resolveVideoUrl(firstUrl, DEFAULT_REFERER, appContext)
                    }.getOrNull()
                } else null

                _playerState.value = AnimePlayerUiState.Success(
                    episode = episode,
                    selectedServerUrl = firstUrl,
                    activeSource = source,
                    comments = comments
                )

                // EXP: +10 sekali pas buka episode ini, lalu mulai hitung per-menit
                // nonton (+2 EXP/menit, maks 10 menit). eventKey unik per anime+episode
                // biar RPC award_exp_once nolak dobel kalau episode ini dibuka lagi.
                awardExpAndNotify("anime_open:$animeSlug:$episodeSlug", EXP_PER_EPISODE_OPEN)
                startExpTimer(animeSlug, episodeSlug)
                startWatchMinutesTimer()
            } catch (e: Exception) {
                _playerState.value = AnimePlayerUiState.Error(e.message ?: "Gagal memuat player episode")
            }
        }
    }

    fun changeStreamServer(url: String) {
        val current = _playerState.value
        if (current !is AnimePlayerUiState.Success) return
        viewModelScope.launch {
            _playerState.value = current.copy(selectedServerUrl = url, activeSource = null)
            // try/catch - kalau resolve gagal/exception, tetap fallback WebView
            // dengan URL mentahnya daripada state nyangkut null selamanya.
            val source = try {
                VideoExtractor.resolveVideoUrl(url, DEFAULT_REFERER, appContext)
            } catch (e: Exception) {
                VideoSource(url = url, label = "Embed Player", isEmbed = true)
            }
            val stateAfter = _playerState.value
            if (stateAfter is AnimePlayerUiState.Success && stateAfter.selectedServerUrl == url) {
                _playerState.value = stateAfter.copy(activeSource = source)
            }
        }
    }

    fun postComment(animeSlug: String, episodeSlug: String, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            val success = supabaseRepository.postEpisodeComment(animeSlug, episodeSlug, message)
            if (success) {
                val comments = supabaseRepository.getEpisodeComments(animeSlug, episodeSlug)
                val current = _playerState.value
                if (current is AnimePlayerUiState.Success) {
                    _playerState.value = current.copy(comments = comments)
                }
            }
        }
    }

    fun deleteComment(animeSlug: String, episodeSlug: String, commentId: String) {
        viewModelScope.launch {
            val success = supabaseRepository.deleteEpisodeComment(commentId)
            if (success) {
                val comments = supabaseRepository.getEpisodeComments(animeSlug, episodeSlug)
                val current = _playerState.value
                if (current is AnimePlayerUiState.Success) {
                    _playerState.value = current.copy(comments = comments)
                }
            }
        }
    }

    fun reportComment(commentId: String, category: String, description: String?, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = supabaseRepository.reportComment(commentId, category, description)
            onResult(success)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopExpTimer()
        stopWatchMinutesTimer()
    }
}
