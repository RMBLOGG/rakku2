package com.rakku.app.ui.profile

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rakku.app.R
import com.rakku.app.data.model.ProfileBorder
import com.rakku.app.data.remote.SupabaseRepository
import com.rakku.app.ui.theme.CyanAccent
import com.rakku.app.ui.theme.DarkBackground
import com.rakku.app.ui.theme.DarkSurface
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.TextSecondary
import com.rakku.app.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    showOnlyOwned: Boolean = false
) {
    val context = LocalContext.current
    val shopState by viewModel.shopState.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadShop()
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (showOnlyOwned) "Border Saya" else "Toko Border", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = DarkSurface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            // Saldo koin
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_rakku_coin),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(28.dp).clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Saldo kamu: ${userProfile?.rakku_coin ?: 0} Koin",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (showOnlyOwned) "Border yang sudah kamu beli. Pasang/lepas kapan saja."
                else "Beli border buat mempercantik foto profil kamu. Border bisa dipasang/lepas kapan saja setelah dibeli.",
                fontSize = 11.sp,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (val state = shopState) {
                is ShopUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = CyanAccent)
                    }
                }
                is ShopUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(state.message, color = Color.Red)
                    }
                }
                is ShopUiState.Success -> {
                    val visibleBorders = if (showOnlyOwned) {
                        state.borders.filter { state.ownedIds.contains(it.id) }
                    } else state.borders

                    if (visibleBorders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    if (showOnlyOwned) "Kamu belum punya border. Yuk beli di Toko Border!" else "Belum ada border yang dijual",
                                    color = TextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(visibleBorders) { border ->
                                val borderId = border.id
                                val owned = state.ownedIds.contains(borderId)
                                val equipped = userProfile?.active_border_url != null &&
                                    userProfile?.active_border_url == border.image_url
                                BorderShopItem(
                                    border = border,
                                    owned = owned,
                                    equipped = equipped,
                                    onBuy = {
                                        if (borderId != null) {
                                            viewModel.buyBorder(borderId) { result ->
                                                val msg = when (result) {
                                                    is SupabaseRepository.BuyBorderResult.Success -> "Border berhasil dibeli!"
                                                    is SupabaseRepository.BuyBorderResult.InsufficientCoin -> "Koin kamu tidak cukup"
                                                    is SupabaseRepository.BuyBorderResult.AlreadyOwned -> "Border ini sudah kamu miliki"
                                                    is SupabaseRepository.BuyBorderResult.NotFound -> "Border tidak ditemukan"
                                                    is SupabaseRepository.BuyBorderResult.Error -> "Gagal membeli border"
                                                }
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    onEquip = {
                                        viewModel.equipBorder(border.id) { success ->
                                            if (success) Toast.makeText(context, "Border dipasang", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onUnequip = {
                                        viewModel.equipBorder(null) { success ->
                                            if (success) Toast.makeText(context, "Border dilepas", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BorderShopItem(
    border: ProfileBorder,
    owned: Boolean,
    equipped: Boolean,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
    onUnequip: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.62f)
                        .clip(CircleShape)
                        .background(DarkSurfaceVariant)
                )
                AsyncImage(
                    model = border.image_url,
                    contentDescription = border.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                if (equipped) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                border.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_rakku_coin),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(14.dp).clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    if (border.price_coin <= 0) "Gratis" else "${border.price_coin} Koin",
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            when {
                equipped -> {
                    Button(
                        onClick = onUnequip,
                        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text("Lepas", fontSize = 12.sp, color = TextPrimary)
                    }
                }
                owned -> {
                    Button(
                        onClick = onEquip,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text("Pasang", fontSize = 12.sp, color = Color.Black)
                    }
                }
                else -> {
                    Button(
                        onClick = onBuy,
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(34.dp)
                    ) {
                        Text("Beli", fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
