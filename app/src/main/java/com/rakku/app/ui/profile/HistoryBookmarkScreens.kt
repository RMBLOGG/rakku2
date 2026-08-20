package com.rakku.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.data.model.BookmarkItem
import com.rakku.app.data.model.HistoryItem
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit
) {
    val allHistory by viewModel.history.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<HistoryItem?>(null) }
    // 0 = Anime, 1 = Manga - riwayat dipisah biar gak ketuker, sama kayak
    // Bookmark yang juga punya dua jenis konten.
    var selectedTab by remember { mutableStateOf(0) }
    val contentType = if (selectedTab == 0) "anime" else "manga"
    val history = remember(allHistory, selectedTab) { allHistory.filter { it.content_type == contentType } }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Riwayat Tontonan", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                    }
                },
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus semua", tint = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    "Anime",
                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedTab == 0) CyanAccent else TextSecondary,
                    modifier = Modifier.clickable { selectedTab = 0 }
                )
                Text(
                    "Manga",
                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedTab == 1) CyanAccent else TextSecondary,
                    modifier = Modifier.clickable { selectedTab = 1 }
                )
            }

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (selectedTab == 0) "Belum ada riwayat tontonan anime." else "Belum ada riwayat baca manga.",
                        color = TextSecondary
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) {
                    items(history, key = { it.id ?: it.hashCode().toLong() }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (item.content_type == "manga") onSelectMangaDetail(item.ref_id)
                                    else onSelectAnimeDetail(item.ref_id)
                                },
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = item.thumb,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                    if (!item.progress_name.isNullOrEmpty()) {
                                        Text("Terakhir: ${item.progress_name}", fontSize = 11.sp, color = CyanAccent)
                                    }
                                }
                                IconButton(onClick = { itemToDelete = item }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = TextSecondary)
                                }
                            }
                        }
                }
            }
        }
        }
    }

    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus riwayat?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("\"${target.title}\" akan dihapus dari riwayat tontonan.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    target.id?.let { viewModel.deleteHistoryItem(it) }
                    itemToDelete = null
                }) { Text("Hapus", color = VioletPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Batal", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Hapus semua riwayat?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Semua riwayat ${if (selectedTab == 0) "tontonan anime" else "baca manga"} (${history.size} item) akan dihapus permanen.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllHistory(contentType)
                    showClearAllConfirm = false
                }) { Text("Hapus Semua", color = VioletPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Batal", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSelectAnimeDetail: (String) -> Unit,
    onSelectMangaDetail: (String) -> Unit
) {
    val bookmarks by viewModel.bookmarks.collectAsState()
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<BookmarkItem?>(null) }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Bookmark Saya", color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                    }
                },
                actions = {
                    if (bookmarks.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirm = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Hapus semua", tint = TextSecondary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Belum ada bookmark tersimpan.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
            ) {
                items(bookmarks, key = { it.id ?: it.hashCode().toLong() }) { item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                if (item.content_type == "anime") onSelectAnimeDetail(item.ref_id)
                                else onSelectMangaDetail(item.ref_id)
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = item.thumb,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 13.sp)
                                Text(item.content_type.uppercase(), fontSize = 10.sp, color = CyanAccent)
                            }
                            IconButton(onClick = { itemToDelete = item }) {
                                Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Hapus bookmark?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("\"${target.title}\" akan dihapus dari bookmark.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    target.id?.let { viewModel.deleteBookmark(it) }
                    itemToDelete = null
                }) { Text("Hapus", color = VioletPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Batal", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("Hapus semua bookmark?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Semua bookmark (${bookmarks.size} item) akan dihapus permanen.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllBookmarks()
                    showClearAllConfirm = false
                }) { Text("Hapus Semua", color = VioletPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) { Text("Batal", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}
