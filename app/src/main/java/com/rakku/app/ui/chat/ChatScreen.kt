package com.rakku.app.ui.chat

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateToLogin: () -> Unit,
    onOpenPublicProfile: (String) -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val currentUserId = viewModel.sessionManager.getUserId()
    var messageInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Obrolan Global",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Komunitas Rakku Realtime",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }

        when (val uiState = state) {
            is ChatUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = VioletPrimary)
                }
            }
            is ChatUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = uiState.message, color = Color.Red, fontSize = 14.sp)
                }
            }
            is ChatUiState.Success -> {
                val messages = uiState.messages

                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { Spacer(modifier = Modifier.height(10.dp)) }

                    items(messages) { msg ->
                        val isSelf = msg.user_id == currentUserId
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isSelf) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isSelf) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { onOpenPublicProfile(msg.user_id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = msg.avatar_url,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(DarkSurfaceVariant),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (!msg.active_border_url.isNullOrBlank()) {
                                        AsyncImage(
                                            model = msg.active_border_url,
                                            contentDescription = null,
                                            modifier = Modifier.size(36.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            Column(
                                horizontalAlignment = if (isSelf) Alignment.End else Alignment.Start
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { onOpenPublicProfile(msg.user_id) }
                                ) {
                                    Text(
                                        text = if (isSelf) "Saya" else (msg.username ?: "User"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                    if (msg.is_unlimited == true) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "\u221E",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CyanAccent
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    RoleBadge(role = msg.role)
                                    if (msg.user_number != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ID: #${msg.user_number}",
                                            fontSize = 10.sp,
                                            color = TextSecondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelf) VioletPrimary else DarkSurface
                                    ),
                                    shape = RoundedCornerShape(
                                        topStart = 12.dp,
                                        topEnd = 12.dp,
                                        bottomStart = if (isSelf) 12.dp else 2.dp,
                                        bottomEnd = if (isSelf) 2.dp else 12.dp
                                    )
                                ) {
                                    Text(
                                        text = msg.message,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(10.dp)) }
                }

                // Bottom Input
                if (currentUserId != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = messageInput,
                            onValueChange = { messageInput = it },
                            placeholder = { Text("Ketik pesan...", color = TextSecondary, fontSize = 13.sp) },
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
                                if (messageInput.isNotBlank()) {
                                    viewModel.sendMessage(messageInput)
                                    messageInput = ""
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
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = onNavigateToLogin,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary)
                        ) {
                            Text("Login untuk ikut obrolan")
                        }
                    }
                }
            }
        }
    }
}
