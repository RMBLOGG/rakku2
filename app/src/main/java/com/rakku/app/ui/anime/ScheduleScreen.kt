package com.rakku.app.ui.anime

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.AnimeinwebItem
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextMuted
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary
import kotlin.math.roundToInt

private val dayOrder = listOf(
    "SENIN" to "Senin", "SELASA" to "Selasa", "RABU" to "Rabu",
    "KAMIS" to "Kamis", "JUMAT" to "Jumat", "SABTU" to "Sabtu", "MINGGU" to "Minggu"
)

private fun dayLabel(day: String) = dayOrder.firstOrNull { it.first == day }?.second ?: day

private fun nextDayOf(day: String): String {
    val idx = dayOrder.indexOfFirst { it.first == day }.let { if (it < 0) 0 else it }
    return dayOrder[(idx + 1) % dayOrder.size].first
}

private fun prevDayOf(day: String): String {
    val idx = dayOrder.indexOfFirst { it.first == day }.let { if (it < 0) 0 else it }
    return dayOrder[(idx - 1 + dayOrder.size) % dayOrder.size].first
}

// Ambil "HH:mm" dari key_time ("yyyy-MM-dd HH:mm:ss"). Kalau gak ada/format
// aneh, balikin "--:--" kayak placeholder di desain referensi.
private fun clockTimeOf(item: AnimeinwebItem): String {
    val kt = item.key_time ?: return "--:--"
    val timePart = kt.substringAfter(" ", "")
    if (timePart.length < 5) return "--:--"
    return timePart.take(5)
}

private fun formatViews(raw: String?): String {
    val n = raw?.toLongOrNull() ?: return "0"
    return when {
        n >= 1_000_000 -> {
            val m = (n / 100_000L).toDouble() / 10.0
            if (m == m.roundToInt().toDouble()) "${m.roundToInt()}M" else "${m}M"
        }
        n >= 1_000 -> {
            val k = (n / 100L).toDouble() / 10.0
            if (k == k.roundToInt().toDouble()) "${k.roundToInt()}K" else "${k}K"
        }
        else -> n.toString()
    }
}

@Composable
fun ScheduleScreen(
    viewModel: AnimeViewModel,
    onBack: () -> Unit,
    onSelectAnime: (String) -> Unit
) {
    val state by viewModel.scheduleState.collectAsState()
    val selectedDay by viewModel.selectedScheduleDay.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadSchedule(selectedDay)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Jadwal Tayang",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Day pill + jumlah anime
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = dayLabel(selectedDay),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            val count = (state as? ScheduleUiState.Success)?.items?.size ?: 0
            Box(
                modifier = Modifier
                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "$count Anime",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (val s = state) {
                is ScheduleUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VioletPrimary)
                    }
                }
                is ScheduleUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = s.message, color = Color.Red, fontSize = 14.sp)
                    }
                }
                is ScheduleUiState.Success -> {
                    if (s.items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Gak ada anime tayang hari ini", color = TextSecondary)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            items(s.items) { anime ->
                                ScheduleTimelineRow(
                                    anime = anime,
                                    onClick = { onSelectAnime(anime.id) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(12.dp)) }
                        }
                    }
                }
            }
        }

        // Navigasi hari sebelumnya/berikutnya
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(20.dp))
                    .clickable { viewModel.goToPreviousScheduleDay() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBackIosNew, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(dayLabel(prevDayOf(selectedDay)), fontSize = 13.sp, color = TextPrimary)
            }

            Row(
                modifier = Modifier
                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(20.dp))
                    .clickable { viewModel.goToNextScheduleDay() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabel(nextDayOf(selectedDay)), fontSize = 13.sp, color = TextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.Default.ArrowForwardIos, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun ScheduleTimelineRow(
    anime: AnimeinwebItem,
    onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Kolom waktu + garis timeline
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = clockTimeOf(anime),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(CyanAccent)
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(DarkBorder)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable { onClick() },
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = anime.image_poster ?: anime.image_cover,
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurface),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = anime.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (anime.status.equals("FINISHED", ignoreCase = true)) "Tamat" else "Menunggu Update",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RemoveRedEye, contentDescription = null, tint = TextMuted, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatViews(anime.views),
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
}
