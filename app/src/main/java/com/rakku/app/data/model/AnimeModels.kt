package com.rakku.app.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AnimeItem(
    val title: String? = null,
    val slug: String? = null,
    @Json(name = "poster") val thumb: String? = null,
    val episode: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val status: String? = null,
    @Json(name = "status_or_day") val statusOrDay: String? = null,
    val day: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimePagination(
    val hasNext: Boolean? = null,
    val hasPrev: Boolean? = null,
    val currentPage: Int? = null
)

@JsonClass(generateAdapter = true)
data class AnimeHomeResponse(
    val status: String? = null,
    val message: String? = null,
    // "animes" adalah key asli dari API (dipakai untuk home/ongoing/completed/movies/latest/search).
    // Field lain di bawah ini adalah fallback yang dipakai website (lihat extractArray() di anime.js)
    // untuk kasus endpoint genre yang key list-nya beda-beda.
    val animes: List<AnimeItem>? = null,
    val anime: List<AnimeItem>? = null,
    val latest: List<AnimeItem>? = null,
    val ongoing: List<AnimeItem>? = null,
    val completed: List<AnimeItem>? = null,
    val movies: List<AnimeItem>? = null,
    val data: List<AnimeItem>? = null,
    val animeList: List<AnimeItem>? = null,
    val recent: List<AnimeItem>? = null,
    val result: List<AnimeItem>? = null,
    val list: List<AnimeItem>? = null,
    val pagination: AnimePagination? = null
)

@JsonClass(generateAdapter = true)
data class AnimeEpisodeItem(
    // key asli di JSON adalah "name", bukan "title" (lihat anime.js: ep.name)
    @Json(name = "name") val title: String = "",
    val slug: String = "",
    val date: String? = null
)

/**
 * Ini merepresentasikan objek "detail" di dalam response asli:
 * { "status": ..., "detail": { title, slug, poster, synopsis, ... } }
 * Nama class & field SENGAJA dipertahankan sama seperti sebelumnya supaya
 * layar (AnimeDetailScreen.kt) tidak perlu diubah sama sekali.
 */
@JsonClass(generateAdapter = true)
data class AnimeDetailResponse(
    val title: String? = null,
    val slug: String? = null,
    @Json(name = "poster") val thumb: String? = null,
    val synopsis: String? = null,
    val rating: String? = null,
    val type: String? = null,
    val studio: String? = null,
    // key asli JSON untuk status publikasi adalah "status" (di dalam objek detail ini)
    @Json(name = "status") val status_anime: String? = null,
    // genre bisa berupa array of string ATAU array of object {name}, keduanya di-handle
    val genres: List<String>? = null,
    val episodes: List<AnimeEpisodeItem>? = null
)

/**
 * Wrapper mentah dari /api/anime/detail: { status, detail: {...} }.
 * Cuma dipakai internal di RakkuApiService/Repository untuk unwrap ".detail".
 */
@JsonClass(generateAdapter = true)
data class AnimeDetailApiEnvelope(
    val status: String? = null,
    val message: String? = null,
    val detail: AnimeDetailResponse? = null
)

@JsonClass(generateAdapter = true)
data class StreamServerItem(
    val name: String? = null,
    val url: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimeEpisodeDetailResponse(
    val status: String? = null,
    val message: String? = null,
    val title: String? = null,
    val streamUrl: String? = null,
    // key asli JSON adalah "streams", bukan "streamServers"
    @Json(name = "streams") val streamServers: List<StreamServerItem>? = null,
    val nextEpisodeSlug: String? = null,
    val prevEpisodeSlug: String? = null
)

@JsonClass(generateAdapter = true)
data class GenreItem(
    val name: String = "",
    val slug: String = ""
)

@JsonClass(generateAdapter = true)
data class GenreListResponse(
    val status: String? = null,
    val genres: List<GenreItem>? = null,
    val data: List<GenreItem>? = null,
    val list: List<GenreItem>? = null
)
