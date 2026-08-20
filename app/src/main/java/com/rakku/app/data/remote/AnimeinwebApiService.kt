package com.rakku.app.data.remote

import com.rakku.app.data.model.AnimeinwebEpisodeItem
import com.rakku.app.data.model.AnimeinwebGenreItem
import com.rakku.app.data.model.AnimeinwebHomeResponse
import com.rakku.app.data.model.AnimeinwebItem
import com.rakku.app.data.model.AnimeinwebSearchResponse
import com.rakku.app.data.model.AnimeinwebStreamResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Manggil ke animeinweb-api (Vercel wrapper punya Fayy sendiri buat animeinweb.com).
 * Ini GANTI TOTAL dari SankaAnimeApiService — Rakku sekarang pakai sumber data
 * yang sama persis kayak "Dayynime-v5" di Aniku.
 */
interface AnimeinwebApiService {

    @GET("homepage")
    suspend fun getHome(@Query("limit") limit: Int? = 10): AnimeinwebHomeResponse

    @GET("search")
    suspend fun search(
        @Query("q") keyword: String = "",
        @Query("page") page: Int? = null,
        @Query("sort") sort: String? = null,
        @Query("genre_in") genreIn: String? = null,
        @Query("status") status: String? = null,
        @Query("type") type: String? = null
    ): AnimeinwebSearchResponse

    @GET("anime/{id}")
    suspend fun getDetail(@Path("id") id: String): AnimeinwebItem

    @GET("anime/{id}/episodes")
    suspend fun getEpisodes(
        @Path("id") id: String,
        @Query("page") page: Int? = null
    ): List<AnimeinwebEpisodeItem>

    @GET("episode/{episodeId}/stream")
    suspend fun getEpisodeStream(@Path("episodeId") episodeId: String): AnimeinwebStreamResponse

    @GET("schedule")
    suspend fun getSchedule(@Query("day") day: String): List<AnimeinwebItem>

    @GET("genres")
    suspend fun getGenres(): List<AnimeinwebGenreItem>

    companion object {
        const val BASE_URL = "https://animeinweb-api.vercel.app/api/"
    }
}
