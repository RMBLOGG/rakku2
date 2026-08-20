package com.rakku.app.data.model

/**
 * Hasil akhir resolve satu server stream, siap dikasih ke player.
 *
 * - isEmbed = false -> `url` adalah link video langsung (mp4/m3u8) hasil ekstraksi
 *   VideoExtractor, diputar native pakai ExoPlayer, dengan header (Referer/Origin/
 *   User-Agent) di `headers` yang WAJIB ikut disertakan di tiap request ExoPlayer
 *   (manifest maupun segment) supaya gak ditolak (403) oleh host videonya.
 * - isEmbed = true -> VideoExtractor gak bisa/gak kenal host-nya, `url` adalah
 *   fallback (redirect sudah di-follow) yang dirender apa adanya di WebView,
 *   sama seperti sebelumnya.
 */
data class VideoSource(
    val url: String,
    val label: String,
    val headers: Map<String, String> = emptyMap(),
    val isEmbed: Boolean
)
