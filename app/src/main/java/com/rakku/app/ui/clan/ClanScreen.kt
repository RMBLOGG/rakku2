package com.rakku.app.ui.clan

import android.widget.Toast
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.ClanSummary
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextMuted
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@Composable
fun ClanScreen(
    viewModel: ClanViewModel,
    onBack: () -> Unit,
    onOpenClanDetail: (String) -> Unit
) {
    val leaderboardState by viewModel.leaderboardState.collectAsState()
    val myMembership by viewModel.myMembership.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadLeaderboard()
        viewModel.refreshMyMembership()
    }

    LaunchedEffect(events) {
        val e = events
        if (e is ClanEvent.Message) {
            Toast.makeText(context, e.text, Toast.LENGTH_SHORT).show()
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
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
                Text(text = "Clan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                // Kartu ajakan: kalau user sudah punya clan -> shortcut ke
                // clan-nya. Kalau belum -> tombol bikin clan baru.
                val membership = myMembership
                if (membership != null) {
                    MyClanCard(onClick = { onOpenClanDetail(membership.clan_id) })
                } else {
                    CreateClanCard(onClick = { showCreateDialog = true })
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Leaderboard Clan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.loadLeaderboard(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Cari nama clan...", color = TextMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanAccent
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (val s = leaderboardState) {
                is ClanLeaderboardUiState.Loading -> {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VioletPrimary)
                        }
                    }
                }
                is ClanLeaderboardUiState.Error -> {
                    item {
                        Text(s.message, color = androidx.compose.ui.graphics.Color.Red, fontSize = 13.sp)
                    }
                }
                is ClanLeaderboardUiState.Success -> {
                    if (s.clans.isEmpty()) {
                        item {
                            Text("Belum ada clan.", color = TextSecondary, fontSize = 13.sp)
                        }
                    } else {
                        itemsIndexed(s.clans) { index, clan ->
                            ClanRow(rank = index + 1, clan = clan, onClick = { onOpenClanDetail(clan.id) })
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateClanDialog(
            isBusy = isBusy,
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                viewModel.createClan(name, desc) { newClanId ->
                    showCreateDialog = false
                    if (newClanId != null) {
                        onOpenClanDetail(newClanId)
                    }
                }
            }
        )
    }
}

@Composable
private fun MyClanCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = Brush.horizontalGradient(listOf(VioletPrimary, CyanAccent))
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Clan Saya", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text("Ketuk buat lihat detail, donasi, & Daily Claim", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun CreateClanCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Kamu belum punya clan", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Buat clan sendiri dan ajak teman-teman gabung, atau cari clan di daftar bawah.",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Buat Clan ($CLAN_CREATE_COST RC)", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ClanRow(rank: Int, clan: ClanSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#$rank",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (rank) {
                        1 -> androidx.compose.ui.graphics.Color(0xFFFFD700)
                        2 -> androidx.compose.ui.graphics.Color(0xFFC0C0C0)
                        3 -> androidx.compose.ui.graphics.Color(0xFFCD7F32)
                        else -> TextSecondary
                    }
                )
            }
            Spacer(modifier = Modifier.width(6.dp))

            if (!clan.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = clan.avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(DarkSurface)
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(DarkSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(clan.name, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${clan.member_count}/${clan.capacity} anggota  •  ${formatRc(clan.total_donated)} RC",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Lv.${clan.level}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyanAccent)
            }
        }
    }
}

@Composable
private fun CreateClanDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DarkSurface,
        title = { Text("Buat Clan Baru", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Biaya pembuatan: $CLAN_CREATE_COST RC. Kamu otomatis jadi ketua (leader) clan ini.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("Nama Clan", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanAccent
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { if (it.length <= 150) description = it },
                    label = { Text("Deskripsi (opsional)", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanAccent
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && name.trim().length >= 3,
                onClick = { onConfirm(name.trim(), description.trim().ifBlank { null }) }
            ) {
                Text(if (isBusy) "Memproses..." else "Buat", color = CyanAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}

// Format angka RC pakai pemisah ribuan gaya Indonesia (titik), mis.
// 1234567 -> "1.234.567". Ditulis manual (bukan java.text.NumberFormat +
// Locale("in","ID")) supaya hasilnya konsisten di semua device tanpa
// tergantung locale sistem.
fun formatRc(value: Long): String {
    val s = value.toString()
    val negative = s.startsWith("-")
    val digits = if (negative) s.substring(1) else s
    val sb = StringBuilder()
    for ((i, c) in digits.reversed().withIndex()) {
        if (i != 0 && i % 3 == 0) sb.append('.')
        sb.append(c)
    }
    return (if (negative) "-" else "") + sb.reverse().toString()
}
