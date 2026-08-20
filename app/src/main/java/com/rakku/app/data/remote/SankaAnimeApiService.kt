package com.rakku.app.data.remote

import com.rakku.app.data.model.AnimeDetailApiEnvelope
import com.rakku.app.data.model.AnimeEpisodeDetailResponse
import com.rakku.app.data.model.AnimeHomeResponse
import com.rakku.app.data.model.GenreListResponse
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Manggil LANGSUNG ke API sumber anime (sankavollerei/animasu), tanpa lewat proxy
 * "/api/anime/..." di Vercel. Endpoint ini publik & tidak butuh apikey (sudah dicek dari
 * source proxy-nya sendiri: cuma "fetch(url)" polos, tanpa header/apikey tambahan),
 * jadi aman dipanggil langsung dari app. Manfaatnya: 1 titik gagal lebih sedikit
 * (gak tergantung Vercel function-nya nyala/gak error), dan biasanya lebih cepat.
 */
interface SankaAnimeApiService {

    // path: home / ongoing / completed / movies / latest / schedule
    @GET("{type}")
    suspend fun getAnimeHome(
        @Path("type") type: String,
        @Query("page") page: Int? = null
    ): AnimeHomeResponse

    @GET("search/{query}")
    suspend fun searchAnime(@Path("query") query: String): AnimeHomeResponse

    @GET("detail/{slug}")
    suspend fun getAnimeDetail(@Path("slug") slug: String): AnimeDetailApiEnvelope

    @GET("episode/{slug}")
    suspend fun getAnimeEpisode(@Path("slug") slug: String): AnimeEpisodeDetailResponse

    @GET("genres")
    suspend fun getAnimeGenres(): GenreListResponse

    @GET("genre/{slug}")
    suspend fun getAnimeByGenre(@Path("slug") slug: String): AnimeHomeResponse

    companion object {
        const val BASE_URL = "https://www.sankavollerei.com/anime/animasu/"

        // Samain dengan TYPE_PATH di api/anime/home.js (website) - kalau "type"
        // dari UI gak dikenali/null, fallback ke "home", persis kayak backend lama.
        fun resolveTypePath(type: String?): String = when (type) {
            "ongoing", "completed", "movies", "latest", "schedule" -> type
            else -> "home"
        }
    }
}
