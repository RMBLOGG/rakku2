package com.rakku.app.ui.components

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.rakku.app.data.model.VideoSource
import com.rakku.app.network.VideoExtractor

private const val DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/124.0.0.0 Safari/537.36"

// Referer default buat WebView (embed page) - pakai domain sumbernya langsung
// (animeinweb), bukan domain website Vercel. storages.animein.net (CDN-nya)
// nolak request tanpa Referer domain asli.
private const val REFERER_ORIGIN = "https://animeinweb.com/"

/**
 * Blogger video.g?...&origin=xxx.blogspot.com - Blogger cuma mau nampilin videonya
 * kalau request keliatan datang dari halaman blogspot yang PUNYA video itu sendiri
 * (proteksi hotlink berbasis param `origin`, dicek terhadap Referer request). Kalau
 * dikirim Referer lain (mis. domain Rakku sendiri), videonya diam-diam gak dirender -
 * gak ada error JS/network sama sekali, cuma blank (ini konfirmasi dari debug dialog:
 * console cuma nunjukin warning Self-XSS bawaan Chrome, gak ada error asli).
 * Jadi utk link Blogger, Referer WAJIB disamain ke domain `origin`-nya sendiri.
 */
private fun refererFor(videoUrl: String): String {
    if (videoUrl.contains("blogger.com") || videoUrl.contains("blogspot.com")) {
        val origin = Regex("""[?&]origin=([^&]+)""").find(videoUrl)?.groupValues?.get(1)
        if (!origin.isNullOrBlank()) return "https://$origin/"
    }
    return REFERER_ORIGIN
}

/**
 * source: hasil resolve VideoExtractor (lihat AnimeViewModel.loadEpisodePlayer /
 *   changeStreamServer) - sama persis cara kerja Kuroflix:
 *   - source == null -> masih resolving, tampilin loading spinner.
 *   - source.isEmbed == false -> link video langsung (mp4/m3u8), diputar native
 *     pakai ExoPlayer (NativePlayerView) dengan header yang wajib disertain.
 *   - source.isEmbed == true -> host belum dikenal VideoExtractor, fallback ke
 *     render halaman embed-nya di WebView (EmbedWebPlayer), seperti sebelumnya.
 */
@Composable
fun VideoPlayer(
    source: VideoSource?,
    isFullscreen: Boolean = false,
    onFullscreenToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (source == null) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color.White)
        }
        return
    }

    if (source.isEmbed) {
        EmbedWebPlayer(source.url, modifier)
    } else {
        NativePlayerView(source.url, source.headers, isFullscreen, onFullscreenToggle, modifier)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativePlayerView(
    sourceUrl: String,
    headers: Map<String, String>,
    isFullscreen: Boolean,
    onFullscreenToggle: (Boolean) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current

    // Pakai OkHttpDataSource (bukan DefaultHttpDataSource bawaan Media3) supaya
    // request streaming manifest/segment ExoPlayer ikut kirim header Referer/
    // Origin/User-Agent yang sama kayak dipakai VideoExtractor pas resolve, dan
    // ikut lewat OkHttpClient yang punya fallback DNS-over-HTTPS. Tanpa ini,
    // kebanyakan host bakal nolak request ExoPlayer (403) walau link-nya bener.
    val player = remember(sourceUrl) {
        val httpDataSourceFactory = OkHttpDataSource.Factory(VideoExtractor.streamingHttpClient)
            .setDefaultRequestProperties(headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
                    .build()
            )
            .setMediaSourceFactory(
                if (sourceUrl.contains(".m3u8"))
                    HlsMediaSource.Factory(dataSourceFactory)
                else
                    ProgressiveMediaSource.Factory(dataSourceFactory)
            )
            .build()
            .apply { playWhenReady = true }
    }

    LaunchedEffect(sourceUrl) {
        player.setMediaItem(MediaItem.Builder().setUri(sourceUrl).build())
        player.prepare()
        player.play()
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = if (isFullscreen) {
            modifier.fillMaxSize().background(Color.Black)
        } else {
            modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        }
    ) {
        AndroidView(
            factory = { ctx ->
                // PlayerView(ctx) langsung (tanpa inflate XML) defaultnya pakai
                // SurfaceView buat render frame video. SurfaceView "melubangi"
                // window-nya sendiri di luar hierarki View biasa, jadi kalau
                // ditaruh di dalam Compose AndroidView (apalagi ada Column/Box
                // lain di atasnya kayak di sini) seringkali gak ke-composite:
                // AUDIO tetap jalan normal (gak butuh surface), tapi video-nya
                // gak pernah nongol - blank hitam terus walau player-nya jalan.
                // Fix: paksa PlayerView pakai TextureView (surface_type di XML),
                // yang berupa View biasa dan komposit normal di Compose.
                val playerView = LayoutInflater.from(ctx)
                    .inflate(com.rakku.app.R.layout.view_native_player, null) as PlayerView
                // Tombol fullscreen bawaan Media3 PlayerView (ikon di pojok kanan
                // bawah controller) - munculnya OTOMATIS begitu listener ini di-set.
                // Klik tombolnya cuma minta ganti state via callback ini, PlayerView
                // sendiri gak ngurus rotasi layar/immersive mode - itu semua diatur
                // dari AnimePlayerScreen.kt (yang punya akses ke Activity).
                playerView.setFullscreenButtonClickListener { requestedFullscreen ->
                    onFullscreenToggle(requestedFullscreen)
                }
                playerView
            },
            update = { view ->
                view.player = player
                view.setFullscreenButtonState(isFullscreen)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedWebPlayer(videoUrl: String, modifier: Modifier) {
    var isLoading by remember(videoUrl) { mutableStateOf(true) }
    var loadFailed by remember(videoUrl) { mutableStateOf(false) }

    // Debug info tanpa perlu adb/Logcat - otomatis muncul begitu halaman selesai
    // dimuat, isinya URL final yang kepakai + pesan console JS (kalau host-nya
    // nge-log error lewat console.error/warn, mis. "blocked by CORS", "ads failed",
    // dll). Tinggal screenshot dialognya kalau video-nya blank/gak jalan.
    // Ada juga tombol "Debug" kecil di pojok buat buka ulang dialognya kapan aja.
    var showDebugDialog by remember(videoUrl) { mutableStateOf(false) }
    var lastLoadedUrl by remember(videoUrl) { mutableStateOf(videoUrl) }
    val consoleLog = remember(videoUrl) { mutableStateListOf<String>() }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false },
            title = { Text("Debug Player") },
            text = {
                Text(
                    "URL dimuat:\n$lastLoadedUrl\n\n" +
                        "Console log (${consoleLog.size}):\n" +
                        consoleLog.takeLast(20).joinToString("\n").ifBlank { "(kosong)" }
                )
            },
            confirmButton = {
                TextButton(onClick = { showDebugDialog = false }) { Text("Tutup") }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // Banyak player (Vidhide/Filemoon/dll) butuh cookie yang dianggap
                    // "third-party" oleh WebView (mis. buat verifikasi/token sesi) - tanpa
                    // ini WebView Android default MENOLAK cookie itu diam-diam, akibatnya
                    // halaman kebuka normal (onPageFinished tetap kepanggil, gak ada error)
                    // tapi video-nya sendiri gak pernah render/mulai (blank/hitam polos).
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString = DESKTOP_USER_AGENT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                            message?.let {
                                consoleLog.add("[${it.messageLevel()}] ${it.message()} (line ${it.lineNumber()})")
                            }
                            return true
                        }
                    }
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            url?.let { lastLoadedUrl = it }
                            // Delay dikit (2 detik) biar player di dalam halaman sempat
                            // coba render/autoplay dulu sebelum dialog debug nongol -
                            // biar gak nutupin video pas kebetulan videonya jalan normal.
                            view?.postDelayed({ showDebugDialog = true }, 2000)
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: android.webkit.WebResourceRequest?,
                            error: android.webkit.WebResourceError?
                        ) {
                            // Cuma anggap gagal kalau ini request UTAMA halamannya (bukan
                            // sub-resource kayak gambar/iklan/tracker yang emang sering
                            // ke-block/gagal tapi gak ngaruh ke video utamanya)
                            if (request?.isForMainFrame == true) {
                                isLoading = false
                                loadFailed = true
                                consoleLog.add("WebResourceError: ${error?.description} (${error?.errorCode}) @ ${request.url}")
                            }
                        }
                    }
                    // PENTING: WebView.loadUrl() TANPA header tambahan gak ngirim
                    // "Referer" sama sekali - beda dengan <iframe> di website (browser
                    // otomatis kirim Referer = domain website itu sendiri). Banyak host
                    // video nolak nampilin konten (halaman kebuka tapi kosong/blank,
                    // tanpa error) kalau Referer-nya kosong, dianggap hotlink langsung.
                    // Kirim Referer = domain website Rakku biar dianggap "datang dari"
                    // situ, sama seperti browser asli.
                    loadUrl(videoUrl, mapOf("Referer" to refererFor(videoUrl)))
                }
            },
            update = { webView -> webView.loadUrl(videoUrl, mapOf("Referer" to refererFor(videoUrl))) },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = { showDebugDialog = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.5f)),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        ) {
            Text("Debug", color = Color.White, fontSize = 11.sp)
        }

        if (loadFailed) {
            Text(text = "Gagal memuat server ini. Coba server/link lain.", color = Color.White)
        } else if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}
