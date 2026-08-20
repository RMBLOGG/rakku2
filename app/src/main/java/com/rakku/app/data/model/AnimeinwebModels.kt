package com.rakku.app.data.model

import com.squareup.moshi.JsonClass

/**
 * Model mentah buat animeinweb-api (Vercel wrapper punya Fayy sendiri buat
 * animeinweb.com). Sengaja dipisah dari AnimeModels.kt (model lama Sanka) biar
 * gampang dibedain, dan supaya AnimeModels.kt (yang dipake luas di seluruh UI)
 * gak perlu diubah sama sekali. Konversi ke AnimeItem/AnimeDetailResponse/dst
 * dilakuin lewat fungsi to*() di bawah.
 */

@JsonClass(generateAdapter = true)
data class AnimeinwebItem(
    val id: String,
    val title: String,
    val synonyms: String? = null,
    val synopsis: String? = null,
    val genre: String? = null,
    val status: String? = null,
    val type: String? = null,
    val year: String? = null,
    val day: String? = null,
    val views: String? = null,
    val favorites: String? = null,
    val image_poster: String? = null,
    val image_cover: String? = null,
    val aired_start: String? = null,
    val studio: String? = null,
    // Timestamp lengkap "yyyy-MM-dd HH:mm:ss" - dipakai di ScheduleScreen buat
    // nampilin jam tayang (bagian HH:mm-nya doang) di timeline.
    val key_time: String? = null
) {
    fun toAnimeItem() = AnimeItem(
        title = title,
        slug = id,
        thumb = image_poster?.takeIf { it.isNotBlank() } ?: image_cover,
        episode = null,
        rating = null,
        type = type,
        status = status,
        statusOrDay = day?.takeIf { it.isNotBlank() && it != "RANDOM" } ?: status,
        day = day
    )

    fun toAnimeDetailResponse(episodes: List<AnimeinwebEpisodeItem>) = AnimeDetailResponse(
        title = title,
        slug = id,
        thumb = image_poster?.takeIf { it.isNotBlank() } ?: image_cover,
        synopsis = synopsis,
        rating = null,
        type = type,
        studio = studio,
        status_anime = status,
        genres = genre?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() },
        episodes = episodes.map { it.toAnimeEpisodeItem() }
    )
}

@JsonClass(generateAdapter = true)
data class AnimeinwebHomeResponse(
    val hot: List<AnimeinwebItem>? = null,
    val new: List<AnimeinwebItem>? = null,
    val today: List<AnimeinwebItem>? = null,
    val popular: List<AnimeinwebItem>? = null,
    val trailer: List<AnimeinwebItem>? = null,
    val random: List<AnimeinwebItem>? = null,
    val waiting: List<AnimeinwebItem>? = null
)

@JsonClass(generateAdapter = true)
data class AnimeinwebSearchResponse(
    val query: String? = null,
    val page: String? = null,
    val results: List<AnimeinwebItem>? = null,
    val next_page: Int? = null
)

// Bentuk item /api/genres belum dikonfirmasi persis dari upstream, jadi semua
// field dibikin nullable + beberapa alias nama biar toleran macam-macam shape JSON.
@JsonClass(generateAdapter = true)
data class AnimeinwebGenreItem(
    val id: String? = null,
    val name: String? = null,
    val title: String? = null,
    val slug: String? = null,
    val value: String? = null
) {
    fun toGenreItem() = GenreItem(
        name = (name ?: title ?: slug ?: value ?: "").trim(),
        slug = (slug ?: value ?: id ?: name ?: "").trim()
    )
}

@JsonClass(generateAdapter = true)
data class AnimeinwebEpisodeItem(
    val id: String,
    val id_movie: String? = null,
    val image: String? = null,
    val index: String? = null,
    val is_new: String? = null,
    val key_time: String? = null,
    val title: String? = null,
    val views: String? = null
) {
    fun toAnimeEpisodeItem() = AnimeEpisodeItem(
        title = title?.takeIf { it.isNotBlank() } ?: "Episode ${index ?: id}",
        slug = id,
        date = key_time
    )
}

@JsonClass(generateAdapter = true)
data class AnimeinwebStreamServer(
    val id: String? = null,
    val name: String? = null,
    val quality: String? = null,
    val link: String? = null,
    val type: String? = null,
    val server_id: String? = null
) {
    fun toStreamServerItem() = StreamServerItem(
        name = name ?: quality ?: "Server",
        url = link
    )
}

@JsonClass(generateAdapter = true)
data class AnimeinwebEpisodeDetail(
    val id: String? = null,
    val title: String? = null,
    val index: String? = null
)

@JsonClass(generateAdapter = true)
data class AnimeinwebStreamResponse(
    val episode: AnimeinwebEpisodeDetail? = null,
    val episodeNext: AnimeinwebEpisodeDetail? = null,
    val servers: List<AnimeinwebStreamServer>? = null
) {
    fun toAnimeEpisodeDetailResponse(): AnimeEpisodeDetailResponse {
        val serverItems = servers?.map { it.toStreamServerItem() }
        return AnimeEpisodeDetailResponse(
            status = "success",
            message = null,
            title = episode?.title,
            streamUrl = serverItems?.firstOrNull()?.url,
            streamServers = serverItems,
            nextEpisodeSlug = episodeNext?.id,
            // Upstream animeinweb cuma ngasih "episodeNext", gak ada "episodePrev" -
            // field ini dibiarin null (dicek: gak dipakai di UI manapun juga).
            prevEpisodeSlug = null
        )
    }
}
