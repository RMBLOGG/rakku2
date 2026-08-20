package com.rakku.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.ui.components.RoleBadge
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

// Halaman profil user LAIN, dibuka dari klik nama/avatar pengirim pesan di
// Obrolan Global (lihat onOpenPublicProfile di ChatScreen.kt). Data
// diambil lewat RPC get_public_profile_stats & get_public_user_history
// (SECURITY DEFINER), bukan dari cache profil sendiri.
@Composable
fun PublicProfileScreen(
    userId: String,
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit
) {
    val state by viewModel.publicProfileState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.loadPublicProfile(userId)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = { PublicProfileTopBar(onBack = onBack) }
    ) { padding ->
        when (val s = state) {
            is PublicProfileUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is PublicProfileUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(s.message, color = Color.Red, fontSize = 14.sp)
                }
            }
            is PublicProfileUiState.Success -> {
                PublicProfileContent(
                    profile = s.profile,
                    history = s.history,
                    padding = padding,
                    onSelectAnimeDetail = onSelectAnimeDetail,
                    onSelectMangaDetail = onSelectMangaDetail
                )
            }
        }
    }
}

// Top bar custom (BUKAN Material3 CenterAlignedTopAppBar) - dibuat sendiri
// pakai Row biasa dengan padding kecil, karena TopAppBar bawaan Material3
// punya tinggi minimum yang cukup besar (~64dp) dan bikin header kelihatan
// "kegedean" dibanding konten di bawahnya.
@Composable
private fun PublicProfileTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
        }
        Text(
            text = "Profil Pengguna",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun PublicProfileContent(
    profile: com.rakku.app.data.model.PublicProfileStats,
    history: List<com.rakku.app.data.model.HistoryItem>,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            ProfileHeaderCard(profile)
            Spacer(modifier = Modifier.height(14.dp))
            ProfileStatsCard(profile)
            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Riwayat Terbaru",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (history.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Belum ada riwayat tontonan/bacaan.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        } else {
            items(history, key = { it.id ?: it.hashCode().toLong() }) { item ->
                HistoryRow(
                    item = item,
                    onClick = {
                        if (item.content_type == "manga") onSelectMangaDetail(item.ref_id)
                        else onSelectAnimeDetail(item.ref_id)
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileHeaderCard(profile: com.rakku.app.data.model.PublicProfileStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(20.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(VioletPrimary, CyanAccent))
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = profile.avatar_url,
                    contentDescription = null,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant),
                    contentScale = ContentScale.Crop
                )
                if (!profile.active_border_url.isNullOrBlank()) {
                    AsyncImage(
                        model = profile.active_border_url,
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = profile.username ?: "User",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(6.dp))

            // "title" pengguna = badge peran (USER/MODERATOR/ADMIN) + ID
            // publiknya, sama seperti yang ditampilkan di profil sendiri &
            // di Obrolan Global.
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoleBadge(role = profile.role)
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text = "ID #${profile.user_number ?: "-"}", fontSize = 11.sp, color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyanAccent.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Lv. ${profile.level ?: 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatsCard(profile: com.rakku.app.data.model.PublicProfileStats) {
    val joinedDays = ProfileDateUtils.daysSince(profile.created_at)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem(
                icon = Icons.Default.CalendarMonth,
                value = joinedDays?.toString() ?: "-",
                label = "Hari Bergabung"
            )
            StatDivider()
            StatItem(
                icon = Icons.Default.ChatBubble,
                value = (profile.total_comments ?: 0).toString(),
                label = "Total Komentar"
            )
            StatDivider()
            StatItem(
                icon = Icons.Default.Timer,
                value = ProfileDateUtils.formatMinutes(profile.total_watch_minutes),
                label = "Menit Nonton"
            )
        }
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(48.dp)
            .background(DarkBorder)
    )
}

@Composable
private fun StatItem(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun HistoryRow(item: com.rakku.app.data.model.HistoryItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                AsyncImage(
                    model = item.thumb,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                // Badge kecil pojok kanan-bawah thumbnail buat nandain jenis
                // kontennya (anime/manga) tanpa perlu teks tambahan.
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (item.content_type == "manga") Icons.Default.MenuBook else Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = 1
                )
                if (!item.progress_name.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Article, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(11.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Terakhir: ${item.progress_name}", fontSize = 11.sp, color = CyanAccent, maxLines = 1)
                    }
                }
            }
        }
    }
}
