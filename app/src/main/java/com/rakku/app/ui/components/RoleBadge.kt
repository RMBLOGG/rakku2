package com.rakku.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rakku.app.ui.theme.AdminRed
import com.rakku.app.ui.theme.AdminRedEnd
import com.rakku.app.ui.theme.DarkSurfaceVariant
import com.rakku.app.ui.theme.ModBlue
import com.rakku.app.ui.theme.ModBlueEnd
import com.rakku.app.ui.theme.TextPrimary
import com.rakku.app.ui.theme.VioletPrimary

@Composable
fun RoleBadge(
    role: String?,
    modifier: Modifier = Modifier
) {
    val normalizedRole = role?.lowercase() ?: "user"
    val backgroundBrush = when (normalizedRole) {
        "admin" -> Brush.horizontalGradient(listOf(AdminRed, AdminRedEnd))
        "moderator" -> Brush.horizontalGradient(listOf(ModBlue, ModBlueEnd))
        else -> Brush.horizontalGradient(listOf(VioletPrimary.copy(alpha = 0.3f), DarkSurfaceVariant))
    }
    val label = when (normalizedRole) {
        "admin" -> "ADMIN"
        "moderator" -> "MODERATOR"
        else -> "USER"
    }

    Box(
        modifier = modifier
            .background(brush = backgroundBrush, shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
