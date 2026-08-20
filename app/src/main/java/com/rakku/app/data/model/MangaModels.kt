package com.rakku.app.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// CATATAN: class-class manga di file ini TIDAK LAGI di-deserialize langsung
// dari JSON upstream - selalu dibangun manual lewat mapper di
// SankaComicMappers.kt (toMangaItem()/toMangaDetailResponse()/dst), biar
// UI manga (MangaScreen, MangaDetailScreen, MangaReaderScreen, MangaViewModel)
// gak perlu diubah sama sekali walau sumber data-nya ganti (dulu proxy
// Vercel, sekarang Sanka Comic API). @Json(name=...) di bawah jadi
// tidak lagi relevan/terpakai, tapi dibiarkan (tidak mengganggu) untuk
// jaga-jaga kalau suatu saat class ini di-parse langsung lagi.
@JsonClass(generateAdapter = true)
data class MangaItem(
    val title: String = "",
    @Json(name = "href") val url: String = "",
    val thumb: String? = null,
    @Json(name = "lastChapter") val chapter: String? = null,
    val rating: String? = null,
    val type: String? = null
)

@JsonClass(generateAdapter = true)
data class MangaHomeResponse(
    val status: String? = null,
    val latest: List<MangaItem>? = null,
    val popular: List<MangaItem>? = null,
    val data: List<MangaItem>? = null
)

@JsonClass(generateAdapter = true)
data class MangaChapterItem(
    @Json(name = "name") val title: String = "",
    @Json(name = "link") val url: String = "",
    val date: String? = null
)

// Nama class & field SENGAJA dipertahankan sama seperti sebelumnya supaya
// layar (MangaDetailScreen.kt) tidak perlu diubah sama sekali.
@JsonClass(generateAdapter = true)
data class MangaDetailResponse(
    val title: String? = null,
    val thumb: String? = null,
    val synopsis: String? = null,
    val author: String? = null,
    val type: String? = null,
    val rating: String? = null,
    @Json(name = "genre") val genres: List<String>? = null,
    val chapters: List<MangaChapterItem>? = null,
    val totalChapters: Int? = null,
    val status: String? = null
)

@JsonClass(generateAdapter = true)
data class MangaDownloadResponse(
    val status: String? = null,
    val title: String? = null,
    val images: List<String>? = null,
    val nextUrl: String? = null,
    val prevUrl: String? = null
)
