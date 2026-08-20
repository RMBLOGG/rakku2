package com.rakku.app.ui.anime

import android.widget.Toast

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.EpisodeComment
import com.rakku.app.ui.components.RoleBadge
import com.rakku.app.ui.components.VideoPlayer
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@Composable
fun AnimePlayerScreen(
    animeSlug: String,
    episodeSlug: String,
    viewModel: AnimeViewModel,
    onBack: () -> Unit
) {
    val playerState by viewModel.playerState.collectAsState()
    val currentUserId = viewModel.sessionManager.getUserId()
    val userProfile by viewModel.sessionManager.currentUserProfile.collectAsState()
    val expToastAmount by viewModel.expToast.collectAsState()
    val context = LocalContext.current

    var commentInput by remember { mutableStateOf("") }

    var reportCommentTarget by remember { mutableStateOf<EpisodeComment?>(null) }
    var reportCategory by remember { mutableStateOf("spam") }
    var reportDesc by remember { mutableStateOf("") }

    LaunchedEffect(animeSlug, episodeSlug) {
        viewModel.loadEpisodePlayer(animeSlug, episodeSlug)
    }

    // Timer EXP per-menit cuma boleh jalan selagi layar ini kebuka - begitu user
    // keluar (back/navigasi lain), timernya WAJIB di-stop, kalau enggak EXP bakal
    // terus nambah di background padahal user udah gak nonton.
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopExpTimer()
            viewModel.stopWatchMinutesTimer()
        }
    }

    // Layar HP jangan sampe auto-mati/dim gara-gara timeout walau user gak
    // nyentuh layar sama sekali (kan lagi nonton doang, bukan ngetik).
    // FLAG_KEEP_SCREEN_ON ini dipasang begitu halaman player kebuka, dan WAJIB
    // dicopot lagi pas keluar dari halaman ini (di dalam onDispose) - kalau
    // lupa dicopot, flag-nya bakal nempel terus ke Activity dan bikin layar
    // gak pernah mati lagi walau udah pindah ke halaman lain / keluar app.
    val activity = remember(context) { context.findActivity() }
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(expToastAmount) {
        if (expToastAmount != null) {
            Toast.makeText(context, "+$expToastAmount EXP", Toast.LENGTH_SHORT).show()
            viewModel.consumeExpToast()
        }
    }

    // ===== FULLSCREEN (ala YouTube) =====
    var isFullscreen by remember { mutableStateOf(false) }

    // Tombol back HP: kalau lagi fullscreen, keluar dari fullscreen dulu (balik ke
    // mode normal), BUKAN langsung keluar dari layar player. Sama kayak behaviour
    // YouTube.
    androidx.activity.compose.BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    DisposableEffect(isFullscreen) {
        if (activity != null) {
            val window = activity.window
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            if (isFullscreen) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        // PENTING: onDispose di sini sengaja DIKOSONGIN. DisposableEffect ini
        // di-key ke isFullscreen, jadi tiap kali isFullscreen ganti (baik ke
        // true maupun ke false), Compose bakal manggil onDispose dari effect
        // LAMA sebelum ngejalanin effect BARU. Kalau di onDispose ini ada
        // logic yang baca `isFullscreen`, dia bakal baca nilai yang UDAH
        // BERUBAH (bukan nilai pas effect ini disetup) - jadi misal pas masuk
        // fullscreen (false->true), onDispose punya effect lama sempet
        // kepanggil dan salah baca isFullscreen=true, jadinya balikin lagi ke
        // portrait sepersekian detik sebelum di-set landscape lagi -> keliatan
        // "gagal fullscreen, balik ke potrait" kayak yang dilaporin. Safety
        // net buat kasus keluar dari layar ini dipisah ke effect Unit di
        // bawah, yang cuma jalan sekali pas composable ini bener-bener hilang
        // dari komposisi (bukan tiap toggle).
        onDispose { }
    }

    // Safety net: KHUSUS buat kasus layar ini ditinggalkan (navigasi ke layar
    // lain) SELAGI masih dalam mode fullscreen. Di-key ke Unit supaya cuma
    // jalan SEKALI pas composable ini beneran keluar dari komposisi, BUKAN
    // tiap kali isFullscreen berubah (beda sama effect di atas).
    DisposableEffect(Unit) {
        onDispose {
            if (activity != null && isFullscreen) {
                activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = activity.window
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Report Comment Dialog
    if (reportCommentTarget != null) {
        AlertDialog(
            onDismissRequest = { reportCommentTarget = null },
            title = { Text("Laporkan Komentar", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Pilih kategori laporan:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    listOf("spam" to "Spam", "promosi" to "Promosi", "18+" to "Konten 18+", "lainnya" to "Lainnya").forEach { (catKey, catLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reportCategory = catKey }
                        ) {
                            RadioButton(
                                selected = reportCategory == catKey,
                                onClick = { reportCategory = catKey }
                            )
                            Text(text = catLabel, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportDesc,
                        onValueChange = { reportDesc = it },
                        placeholder = { Text("Deskripsi tambahan (opsional)", color = TextSecondary) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        reportCommentTarget?.id?.let { commentId ->
                            viewModel.reportComment(commentId, reportCategory, reportDesc) {
                                reportCommentTarget = null
                                reportDesc = ""
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Text("Kirim Laporan")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportCommentTarget = null }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header - disembunyiin pas fullscreen biar video bener-bener full-screen
        // kayak YouTube, gak ada elemen UI lain yang nutupin.
        if (!isFullscreen) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                }
                Text(
                    text = "Streaming Episode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        when (val state = playerState) {
            is AnimePlayerUiState.Loading, AnimePlayerUiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is AnimePlayerUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = state.message, color = Color.Red, fontSize = 14.sp)
                }
            }
            is AnimePlayerUiState.Success -> {
                if (isFullscreen) {
                    // Mode fullscreen: cuma video, gak ada elemen lain sama sekali.
                    Box(modifier = Modifier.fillMaxSize()) {
                        VideoPlayer(
                            source = state.activeSource,
                            isFullscreen = true,
                            onFullscreenToggle = { isFullscreen = it }
                        )
                    }
                } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Video Player Component - source null berarti masih resolve
                    // (VideoExtractor lagi coba dapetin link mp4/m3u8 langsung).
                    // Kalau berhasil -> ExoPlayer native. Kalau host gak dikenal
                    // -> fallback WebView (isEmbed = true), sama kayak sebelumnya.
                    VideoPlayer(
                        source = state.activeSource,
                        isFullscreen = false,
                        onFullscreenToggle = { isFullscreen = it }
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        item {
                            Text(
                                text = state.episode.title ?: "Episode",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Server Switcher
                            val servers = state.episode.streamServers ?: emptyList()
                            if (servers.isNotEmpty()) {
                                Text("Pilih Server Stream:", fontSize = 12.sp, color = TextSecondary)
                                Spacer(modifier = Modifier.height(6.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(servers) { srv ->
                                        val isSelected = srv.url == state.selectedServerUrl
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isSelected) CyanAccent else DarkSurfaceVariant,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { srv.url?.let { viewModel.changeStreamServer(it) } }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text(
                                                text = srv.name ?: "Server",
                                                color = if (isSelected) Color.Black else TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // Comments Header
                            Text(
                                text = "Komentar Episode (${state.comments.size})",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (state.comments.isEmpty()) {
                            item {
                                Text("Belum ada komentar. Jadilah yang pertama berkomentar!", color = TextSecondary, fontSize = 12.sp)
                            }
                        } else {
                            items(state.comments) { c ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                AsyncImage(
                                                    model = c.avatar_url,
                                                    contentDescription = null,
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .clip(CircleShape)
                                                        .background(DarkSurfaceVariant),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = c.username ?: "User",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                RoleBadge(role = c.role)
                                            }

                                            // Options (Delete / Report)
                                            val isOwner = c.user_id == currentUserId
                                            val isStaff = userProfile?.role in listOf("admin", "moderator")
                                            if (isOwner || isStaff) {
                                                IconButton(
                                                    onClick = {
                                                        c.id?.let { viewModel.deleteComment(animeSlug, episodeSlug, it) }
                                                    },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = { reportCommentTarget = c },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Default.Flag, contentDescription = "Laporkan", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = c.message,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Comment Input Bar
                    if (currentUserId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = commentInput,
                                onValueChange = { commentInput = it },
                                placeholder = { Text("Tulis komentar...", color = TextSecondary, fontSize = 13.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CyanAccent,
                                    unfocusedBorderColor = DarkBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    focusedContainerColor = DarkBackground,
                                    unfocusedContainerColor = DarkBackground
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (commentInput.isNotBlank()) {
                                        viewModel.postComment(animeSlug, episodeSlug, commentInput)
                                        commentInput = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(CyanAccent, shape = CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Kirim", tint = Color.Black)
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Silakan login untuk menulis komentar", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
                }
            }
        }
    }
}
