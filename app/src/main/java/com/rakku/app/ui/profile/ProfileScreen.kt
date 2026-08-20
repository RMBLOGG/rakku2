package com.rakku.app.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.rakku.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.ui.components.RoleBadge
import com.rakku.app.ui.theme.AdminRed
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
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onNavigateToLogin: () -> Unit,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onOpenShop: () -> Unit,
    onOpenMyBorders: () -> Unit,
    onOpenClan: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val userProfile by viewModel.userProfile.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val myStats by viewModel.myStats.collectAsState()

    // Refresh data profil (EXP/level/coin) tiap layar ini kebuka - biar kalau ada
    // perubahan dari layar lain (mis. abis dapet EXP nonton anime), langsung
    // kelihatan update di sini tanpa perlu restart app.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshProfile()
    }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showTopupDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

    if (userProfile == null) {
        // Guest view
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Anda Belum Login",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Login untuk menyimpan bookmark, riwayat, koin, dan ikut obrolan komunitas.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToLogin,
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Masuk / Daftar Akun")
                }
            }
        }
        return
    }

    val profile = userProfile!!

    // Edit Profile Dialog Component
    if (showEditProfileDialog) {
        var newUsername by remember { mutableStateOf(profile.username ?: "") }
        var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
        var isUpdating by remember { mutableStateOf(false) }

        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri -> selectedImageUri = uri }

        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profil", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(
                                model = selectedImageUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            AsyncImage(
                                model = profile.avatar_url,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        }
                    }
                    Text("Ketuk foto untuk mengganti avatar", fontSize = 11.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newUsername,
                        onValueChange = { newUsername = it },
                        label = { Text("Username", color = TextSecondary) },
                        singleLine = true,
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
                        isUpdating = true
                        viewModel.updateProfileInfo(context, newUsername, selectedImageUri) {
                            isUpdating = false
                            showEditProfileDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    enabled = !isUpdating
                ) {
                    if (isUpdating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    else Text("Simpan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditProfileDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Topup Dialog
    if (showTopupDialog) {
        val proofPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "image/*"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        putExtra(Intent.EXTRA_TEXT, "Bukti transfer topup Rakku Koin.\nUser: ${profile.username}\nID: #${profile.user_number ?: "-"}")
                        // "jid" bikin WA langsung buka chat ke nomor ini (skip contact picker).
                        // Kalau versi WA-nya gak support extra ini, dia bakal fallback ke picker kontak biasa.
                        putExtra("jid", "6288973461209@s.whatsapp.net")
                        setPackage("com.whatsapp")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(sendIntent)
                } catch (e: Exception) {
                    Toast.makeText(context, "WhatsApp gak ketemu, pastiin udah keinstall", Toast.LENGTH_SHORT).show()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showTopupDialog = false },
            title = { Text("Top Up Rakku Koin", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "1. Tap tombol di bawah buat top up lewat SocialBuzz.\n2. Setelah transfer selesai, tap tombol \"Kirim Bukti\" dan kirim screenshot bukti pembayarannya ke WA admin.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Nominal koin:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val packages = listOf(
                        100 to "Rp 10.000",
                        300 to "Rp 28.000",
                        600 to "Rp 50.000",
                        1200 to "Rp 95.000"
                    )
                    packages.forEach { (coinCount, price) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_rakku_coin),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(16.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("$coinCount Koin", fontSize = 12.sp, color = TextPrimary)
                            }
                            Text(price, fontSize = 12.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://sociabuzz.com/rakku/tribe"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_rakku_coin),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Top Up via SocialBuzz")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { proofPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Kirim Bukti Pembayaran", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTopupDialog = false }) {
                    Text("Tutup", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        var feedbackType by remember { mutableStateOf("saran") }
        var feedbackMsg by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Saran & Laporan", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Row {
                        RadioButton(selected = feedbackType == "saran", onClick = { feedbackType = "saran" })
                        Text("Saran", color = TextPrimary, modifier = Modifier.align(Alignment.CenterVertically))
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = feedbackType == "laporan", onClick = { feedbackType = "laporan" })
                        Text("Laporan Bug", color = TextPrimary, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = feedbackMsg,
                        onValueChange = { feedbackMsg = it },
                        placeholder = { Text("Tuliskan masukan atau masalah aplikasi...", color = TextSecondary) },
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
                        if (feedbackMsg.isNotBlank()) {
                            viewModel.submitFeedback(feedbackType, feedbackMsg) {
                                showFeedbackDialog = false
                                Toast.makeText(context, "Saran/Laporan terkirim", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                ) {
                    Text("Kirim")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }

    // (Admin Panel sekarang halaman terpisah, lihat onOpenAdminPanel di bawah)

    // Main Profile Screen View
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(VioletPrimary, CyanAccent)))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    // Avatar + border (frame) yang lagi dipasang user, kalau ada.
                    // Border digambar lebih besar dari avatar & nempel di belakangnya
                    // supaya efeknya kayak bingkai melingkari foto profil.
                    Box(
                        modifier = Modifier.size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = profile.avatar_url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        if (!profile.active_border_url.isNullOrBlank()) {
                            AsyncImage(
                                model = profile.active_border_url,
                                contentDescription = null,
                                modifier = Modifier.size(96.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    IconButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier
                            .size(28.dp)
                            .background(CyanAccent, CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = profile.username ?: "User",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleBadge(role = profile.role)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ID: #${profile.user_number ?: "-"}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(profile.id))
                            Toast.makeText(context, "User ID disalin", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Salin ID", tint = CyanAccent, modifier = Modifier.size(14.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Level & EXP Progress Bar
                val lvl = profile.level ?: 1
                val exp = profile.exp ?: 0
                val targetExp = lvl * 100
                val progress = (exp.toFloat() / targetExp.toFloat()).coerceIn(0f, 1f)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Level $lvl", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                    Text("$exp / $targetExp EXP", color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = CyanAccent,
                    trackColor = DarkSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rakku Coin Balance Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_rakku_coin),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Rakku Coin", fontSize = 12.sp, color = TextSecondary)
                        Text("${profile.rakku_coin ?: 0} Koin", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    }
                }
                Button(
                    onClick = { showTopupDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Top Up")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Kartu Statistik: hari bergabung, total komentar, total menit nonton
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val joinedDays = ProfileDateUtils.daysSince(profile.created_at)
                ProfileStatItem(
                    value = joinedDays?.toString() ?: "-",
                    label = "Hari Bergabung"
                )
                ProfileStatItem(
                    value = (myStats?.total_comments ?: 0).toString(),
                    label = "Total Komentar"
                )
                ProfileStatItem(
                    value = ProfileDateUtils.formatMinutes(myStats?.total_watch_minutes ?: profile.total_watch_minutes),
                    label = "Menit Nonton"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Profile Menu Options
        ProfileMenuItem(icon = Icons.Default.Storefront, label = "Toko Border", onClick = onOpenShop)
        ProfileMenuItem(icon = Icons.Default.CheckCircle, label = "Border Saya", onClick = onOpenMyBorders)
        ProfileMenuItem(icon = Icons.Default.Groups, label = "Clan", onClick = onOpenClan)
        ProfileMenuItem(icon = Icons.Default.Bookmark, label = "Bookmark Saya", onClick = onOpenBookmarks)
        ProfileMenuItem(icon = Icons.Default.History, label = "Riwayat Tontonan", onClick = onOpenHistory)
        ProfileMenuItem(icon = Icons.Default.Feedback, label = "Saran & Laporan", onClick = { showFeedbackDialog = true })

        // Staff Admin Panel Entry (visible if admin or moderator)
        val isStaff = profile.role in listOf("admin", "moderator")
        if (isStaff) {
            Spacer(modifier = Modifier.height(12.dp))
            ProfileMenuItem(
                icon = Icons.Default.AdminPanelSettings,
                label = "Admin Panel Control",
                tint = AdminRed,
                onClick = {
                    viewModel.loadAdminData()
                    onOpenAdminPanel()
                }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Logout
        Button(
            onClick = { viewModel.logout() },
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Keluar (Logout)", color = Color.Red)
        }
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = CyanAccent
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = TextSecondary
        )
    }
}

@Composable
fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = CyanAccent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
        }
    }
}

