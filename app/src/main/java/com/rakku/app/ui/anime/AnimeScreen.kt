package com.rakku.app.ui.anime

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakku.app.ui.home.AnimeCardItem
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkBorder
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.IndigoSecondary
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@Composable
fun AnimeScreen(
    viewModel: AnimeViewModel,
    onSelectAnime: (String) -> Unit,
    onNavigateToSchedule: () -> Unit = {}
) {
    val listState by viewModel.listState.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val selectedGenre by viewModel.selectedGenre.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var queryInput by remember { mutableStateOf("") }

    val tabs = listOf(
        "ongoing" to "Ongoing",
        "completed" to "Completed",
        "movies" to "Movies",
        "latest" to "Terbaru",
        "schedule" to "Jadwal"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search & Header Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = queryInput,
                onValueChange = {
                    queryInput = it
                    viewModel.searchAnime(it)
                },
                placeholder = { Text("Cari judul anime...", color = TextSecondary, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent) },
                trailingIcon = {
                    if (queryInput.isNotEmpty()) {
                        IconButton(onClick = {
                            queryInput = ""
                            viewModel.searchAnime("")
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanAccent,
                    unfocusedBorderColor = DarkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = DarkBackground,
                    unfocusedContainerColor = DarkBackground
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Tabs Row
        ScrollableTabRow(
            selectedTabIndex = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0),
            containerColor = DarkSurface,
            contentColor = TextPrimary,
            edgePadding = 16.dp,
            indicator = { tabPositions ->
                val index = tabs.indexOfFirst { it.first == currentTab }.coerceAtLeast(0)
                if (index in tabPositions.indices) {
                    SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[index]),
                        color = CyanAccent
                    )
                }
            }
        ) {
            tabs.forEach { (typeKey, label) ->
                Tab(
                    selected = currentTab == typeKey && selectedGenre == null,
                    onClick = {
                        if (typeKey == "schedule") {
                            onNavigateToSchedule()
                        } else {
                            queryInput = ""
                            viewModel.loadAnimeList(typeKey)
                        }
                    },
                    text = {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (currentTab == typeKey && selectedGenre == null) FontWeight.Bold else FontWeight.Medium,
                            color = if (currentTab == typeKey && selectedGenre == null) CyanAccent else TextSecondary
                        )
                    }
                )
            }
        }

        // Genre Filter Chips
        when (val state = listState) {
            is AnimeListUiState.Success -> {
                if (state.genres.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.genres) { genre ->
                            val isSelected = selectedGenre == genre.slug
                            Box(
                                modifier = Modifier
                                    .background(
                                        brush = if (isSelected) Brush.horizontalGradient(listOf(VioletPrimary, IndigoSecondary))
                                        else Brush.horizontalGradient(listOf(DarkSurface, DarkSurface)),
                                        shape = RoundedCornerShape(20.dp)
                                    )
                                    .clickable {
                                        if (isSelected) viewModel.loadAnimeList(currentTab)
                                        else viewModel.filterByGenre(genre.slug)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = genre.name,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else TextSecondary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        // Content Grid
        when (val state = listState) {
            is AnimeListUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is AnimeListUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = state.message, color = Color.Red, fontSize = 14.sp)
                }
            }
            is AnimeListUiState.Success -> {
                if (state.animeList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Tidak ada anime ditemukan", color = TextSecondary)
                    }
                } else {
                    val gridState = rememberLazyGridState()
                    // Trigger otomatis begitu card terakhir hampir/udah keliatan -
                    // sama persis pola yang dipake Explore screen di Aniku.
                    val shouldLoadMore by remember {
                        derivedStateOf {
                            val layoutInfo = gridState.layoutInfo
                            val totalItems = layoutInfo.totalItemsCount
                            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                            totalItems > 0 && lastVisible >= totalItems - 2
                        }
                    }
                    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoadingMore) {
                        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) {
                            viewModel.loadMoreAnime()
                        }
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.animeList) { anime ->
                            AnimeCardItem(
                                anime = anime,
                                onClick = { anime.slug?.let { onSelectAnime(it) } }
                            )
                        }
                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = VioletPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
