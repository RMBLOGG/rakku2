package com.rakku.app.ui.clan

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.ClanDetail
import com.rakku.app.data.model.ClanMemberInfo
import com.rakku.app.data.model.MyClanMembership
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ClanDetailScreen(
    clanId: String,
    viewModel: ClanViewModel,
    onBack: () -> Unit
) {
    val detailState by viewModel.detailState.collectAsState()
    val myMembership by viewModel.myMembership.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val events by viewModel.events.collectAsState()
    val context = LocalContext.current

    var showDonateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(clanId) {
        viewModel.loadClanDetail(clanId)
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
                Text(text = "Detail Clan", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }
    ) { padding ->
        when (val s = detailState) {
            is ClanDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is ClanDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Color.Red, fontSize = 14.sp)
                }
            }
            is ClanDetailUiState.Success -> {
                ClanDetailContent(
                    padding = padding,
                    detail = s.detail,
                    members = s.members,
                    myMembership = myMembership,
                    isBusy = isBusy,
                    onJoin = { viewModel.joinClan(clanId) {} },
                    onLeave = { viewModel.leaveClan {} },
                    onDonateClick = { showDonateDialog = true },
                    onClaimDaily = { viewModel.claimDailyReward() }
                )
            }
        }
    }

    if (showDonateDialog) {
        DonateDialog(
            isBusy = isBusy,
            onDismiss = { showDonateDialog = false },
            onConfirm = { amount ->
                viewModel.donateToClan(clanId, amount)
                showDonateDialog = false
            }
        )
    }
}

private fun todayDateString(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
private fun ClanDetailContent(
    padding: androidx.compose.foundation.layout.PaddingValues,
    detail: ClanDetail,
    members: List<ClanMemberInfo>,
    myMembership: MyClanMembership?,
    isBusy: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onDonateClick: () -> Unit,
    onClaimDaily: () -> Unit
) {
    val isMyClan = myMembership?.clan_id == detail.id
    val isInAnotherClan = myMembership != null && !isMyClan
    val hasClaimedToday = myMembership?.last_daily_claim_date == todayDateString()
    val isFull = detail.member_count >= detail.capacity

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            ClanInfoCard(detail)
            Spacer(modifier = Modifier.height(14.dp))
            ClanLevelProgressCard(detail)
            Spacer(modifier = Modifier.height(14.dp))

            when {
                isMyClan -> {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onClaimDaily,
                            enabled = !isBusy && !hasClaimedToday,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, disabledContainerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (hasClaimedToday) "Sudah Klaim Hari Ini" else "Daily Claim (+${detail.daily_reward} RC)",
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = onDonateClick,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VolunteerActivism, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Donasi RC", fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        OutlinedButton(
                            onClick = onLeave,
                            enabled = !isBusy,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                isInAnotherClan -> {
                    Text(
                        "Kamu sudah tergabung di clan lain. Keluar dari clan itu dulu buat bisa gabung ke sini.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                isFull -> {
                    Text("Clan ini sudah penuh (${detail.member_count}/${detail.capacity}).", fontSize = 12.sp, color = TextSecondary)
                }
                else -> {
                    Button(
                        onClick = onJoin,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gabung Clan Ini", fontSize = 13.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Anggota (${detail.member_count}/${detail.capacity})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        itemsIndexed(members) { index, member ->
            ClanMemberRow(rank = index + 1, member = member)
        }
    }
}

@Composable
private fun ClanInfoCard(detail: ClanDetail) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (!detail.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = detail.avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(DarkSurfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.size(72.dp).clip(CircleShape).background(DarkSurfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (!detail.tag.isNullOrBlank()) "[${detail.tag}] ${detail.name}" else detail.name,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            if (!detail.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(detail.description, fontSize = 12.sp, color = TextSecondary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = androidx.compose.ui.graphics.Color(0xFFFFD700), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Leader: ${detail.leader_username ?: "-"}", fontSize = 11.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun ClanLevelProgressCard(detail: ClanDetail) {
    // Ambang donasi buat level SEKARANG, dihitung ulang dari rumus yang
    // sama kayak di SQL (clan_donation_required_for_level) supaya progress
    // bar-nya akurat tanpa perlu request tambahan ke server.
    val currentThreshold = ((detail.level - 1).toDouble().let { it * it } * 1000).toLong()
    val nextThreshold = detail.next_level_donation
    val progress = if (detail.level >= 100 || nextThreshold <= currentThreshold) {
        1f
    } else {
        ((detail.total_donated - currentThreshold).toFloat() / (nextThreshold - currentThreshold).toFloat()).coerceIn(0f, 1f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Level ${detail.level}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                Text(
                    if (detail.level >= 100) "MAX LEVEL" else "Level ${detail.level + 1}",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = CyanAccent,
                trackColor = DarkBorder
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (detail.level >= 100)
                    "Total Donasi: ${formatRc(detail.total_donated)} RC"
                else
                    "${formatRc(detail.total_donated)} / ${formatRc(nextThreshold)} RC menuju Level ${detail.level + 1}",
                fontSize = 11.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ClanMiniStat(label = "Kapasitas", value = "${detail.member_count}/${detail.capacity}")
                ClanMiniStat(label = "Daily Claim", value = "${detail.daily_reward} RC")
            }
        }
    }
}

@Composable
private fun ClanMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(label, fontSize = 10.sp, color = TextSecondary)
    }
}

@Composable
private fun ClanMemberRow(rank: Int, member: ClanMemberInfo) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.width(26.dp)
            )
            if (!member.avatar_url.isNullOrBlank()) {
                AsyncImage(
                    model = member.avatar_url,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(DarkSurface),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.size(38.dp).clip(CircleShape).background(DarkSurface))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.username ?: "User", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                    if (member.role == "leader") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = "Leader",
                            tint = androidx.compose.ui.graphics.Color(0xFFFFD700),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Text("Donasi: ${formatRc(member.total_donated)} RC", fontSize = 11.sp, color = CyanAccent)
            }
        }
    }
}

@Composable
private fun DonateDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    val amount = amountText.toIntOrNull() ?: 0

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = DarkSurface,
        title = { Text("Donasi RC ke Clan", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("RC yang kamu donasikan akan menambah Total Donasi clan dan bisa menaikkan Level Clan.", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 9) amountText = v },
                    label = { Text("Jumlah RC", fontSize = 12.sp) },
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isBusy && amount > 0,
                onClick = { onConfirm(amount) }
            ) {
                Text(if (isBusy) "Memproses..." else "Donasi", color = CyanAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("Batal", color = TextSecondary)
            }
        }
    )
}
