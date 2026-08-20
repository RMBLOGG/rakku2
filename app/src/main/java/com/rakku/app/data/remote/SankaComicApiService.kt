package com.rakku.app.data.remote

import com.rakku.app.data.model.BacakomikChapterResponse
import com.rakku.app.data.model.BacakomikDetailResponse
import com.rakku.app.data.model.BacakomikGenreListResponse
import com.rakku.app.data.model.BacakomikListResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Manggil LANGSUNG ke API komik Sanka - endpoint bacakomik (sumber data:
 * bacakomik.my), gantiin endpoint generik lama (/comic/terbaru, /populer,
 * /comic/{slug}, /chapter/{slug} - sumber Komiku.org) yang lebih sering
 * error/gak stabil. Endpoint publik, tanpa apikey.
 *
 * Endpoint kayak /bacakomik/unlimited, /bacakomik/scroll, /bacakomik/realtime,
 * /bacakomik/comparison, /bacakomik/docs, /bacakomik/fullstats,
 * /bacakomik/trending, /bacakomik/browse SENGAJA gak dipakai - itu cuma
 * fitur showcase/benchmark performa API, bukan endpoint buat kebutuhan
 * baca komik. /bacakomik/top, /list, /recomen, /komikberwarna/{page} juga
 * belum dipakai (bentuk JSON-nya sama kayak /latest & /populer kalau
 * suatu saat mau ditambah).
 */
interface SankaComicApiService {

    @GET("bacakomik/latest")
    suspend fun getLatest(): BacakomikListResponse

    @GET("bacakomik/populer")
    suspend fun getPopular(): BacakomikListResponse

    // "query" di-percent-encode otomatis sama Retrofit (spasi -> %20)
    @GET("bacakomik/search/{query}")
    suspend fun searchComic(@Path("query") query: String): BacakomikListResponse

    @GET("bacakomik/detail/{slug}")
    suspend fun getComicDetail(@Path("slug") slug: String): BacakomikDetailResponse

    // "chapterSlug" WAJIB slug lengkap dengan nomor chapter-nya, mis.
    // "nano-machine-chapter-1" (didapat dari field "slug" di dalam
    // BacakomikDetailResponse.detail.chapters, bukan slug manga polos)
    @GET("bacakomik/chapter/{chapterSlug}")
    suspend fun getComicChapter(@Path("chapterSlug") chapterSlug: String): BacakomikChapterResponse

    @GET("bacakomik/genres")
    suspend fun getGenres(): BacakomikGenreListResponse

    @GET("bacakomik/genre/{slug}")
    suspend fun getComicByGenre(@Path("slug") slug: String): BacakomikListResponse

    companion object {
        const val BASE_URL = "https://www.sankavollerei.web.id/comic/"
    }
}
