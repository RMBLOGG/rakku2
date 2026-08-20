package com.rakku.app.ui.home

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.Announcement
import com.rakku.app.data.model.AnimeItem
import com.rakku.app.data.model.MangaItem
import com.rakku.app.data.model.UserProfile
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.IndigoSecondary
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userProfile: UserProfile?,
    onNavigateToAnimeTab: () -> Unit,
    onNavigateToMangaTab: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header — info akun/profil, tap buat ke tab Profil
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .clickable(onClick = onNavigateToProfile)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(52.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(50))
                                .then(
                                    if (userProfile?.active_border_url.isNullOrBlank()) {
                                        Modifier.border(2.dp, CyanAccent, RoundedCornerShape(50))
                                    } else Modifier
                                )
                                .background(DarkSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (userProfile?.avatar_url != null) {
                                AsyncImage(
                                    model = userProfile.avatar_url,
                                    contentDescription = "Foto profil",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Foto profil",
                                    tint = CyanAccent
                                )
                            }
                        }
                        // Border/frame yang lagi dipasang user (kalau ada) digambar
                        // nempel di atas avatar, sama kayak di halaman Profil.
                        if (!userProfile?.active_border_url.isNullOrBlank()) {
                            AsyncImage(
                                model = userProfile?.active_border_url,
                                contentDescription = null,
                                modifier = Modifier.size(52.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userProfile?.username?.takeIf { it.isNotBlank() } ?: "Sobat Rakku",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (userProfile != null) {
                                "Level ${userProfile.level ?: 1} · ${userProfile.rakku_coin ?: 0} Koin"
                            } else {
                                "Tap untuk login / lihat profil"
                            },
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }
        }

        when (val uiState = state) {
            is HomeUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is HomeUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = uiState.message, color = Color.Red, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.loadHomeData() },
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Coba Lagi")
                        }
                    }
                }
            }
            is HomeUiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    // Active Announcement Banner
                    if (uiState.announcements.isNotEmpty()) {
                        val firstAnn = uiState.announcements.first()
                        AnnouncementCard(
                            announcement = firstAnn,
                            onDismiss = { firstAnn.id?.let { viewModel.dismissAnnouncement(it) } }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Quick Selectors (Anime & Manga 2 big cards)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        QuickCategoryCard(
                            title = "ANIME",
                            subtitle = "Streaming Sub Indo",
                            icon = Icons.Default.PlayCircle,
                            gradientColors = listOf(VioletPrimary, IndigoSecondary),
                            onClick = onNavigateToAnimeTab,
                            modifier = Modifier.weight(1f)
                        )
                        QuickCategoryCard(
                            title = "MANGA",
                            subtitle = "Baca Chapter Terjemahan",
                            icon = Icons.Default.MenuBook,
                            gradientColors = listOf(IndigoSecondary, CyanAccent),
                            onClick = onNavigateToMangaTab,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Anime Carousel
                    SectionTitle(
                        title = "Anime Terbaru",
                        onSeeAll = onNavigateToAnimeTab
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.latestAnime) { anime ->
                            AnimeCardItem(
                                anime = anime,
                                onClick = { anime.slug?.let { onSelectAnimeDetail(it) } }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Manga Carousel
                    SectionTitle(
                        title = "Manga Terbaru",
                        onSeeAll = onNavigateToMangaTab
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.latestManga) { manga ->
                            MangaCardItem(
                                manga = manga,
                                onClick = { onSelectMangaDetail(manga.url) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(VioletPrimary, CyanAccent)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Campaign,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = announcement.title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Tutup", tint = TextSecondary)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = announcement.content,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun QuickCategoryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradientColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(100.dp)
            .background(
                brush = Brush.linearGradient(gradientColors),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    onSeeAll: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            text = "Lihat Semua",
            fontSize = 12.sp,
            color = CyanAccent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { onSeeAll() }
        )
    }
}

@Composable
fun AnimeCardItem(
    anime: AnimeItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = anime.thumb,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (!anime.episode.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            brush = Brush.horizontalGradient(listOf(VioletPrimary, IndigoSecondary)),
                            shape = RoundedCornerShape(topStart = 8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = anime.episode ?: "",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = anime.title ?: "Anime",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MangaCardItem(
    manga: MangaItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
        ) {
            AsyncImage(
                model = manga.thumb,
                contentDescription = manga.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (!manga.chapter.isNullOrEmpty()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(
                            brush = Brush.horizontalGradient(listOf(IndigoSecondary, CyanAccent)),
                            shape = RoundedCornerShape(topStart = 8.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = manga.chapter ?: "",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = manga.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
