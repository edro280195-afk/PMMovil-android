package com.example.pmmovil.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmYellow

enum class StatusKind { Online, Offline, Syncing }

@Composable
fun StatusPill(
    kind: StatusKind,
    modifier: Modifier = Modifier
) {
    val (icon, label, accent, container) = when (kind) {
        StatusKind.Online  -> StatusStyle(Icons.Default.CloudDone, "EN LÍNEA", PmmGreen,  Color(0xFFDCFCE7))
        StatusKind.Offline -> StatusStyle(Icons.Default.CloudOff,  "SIN RED",  PmmRed,    Color(0xFFFFEDED))
        StatusKind.Syncing -> StatusStyle(Icons.Default.Sync,      "SYNC",     PmmYellow, Color(0xFFFEF9C3))
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (kind == StatusKind.Syncing) {
            SpinningIcon(icon = icon, accent = accent)
        } else {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun SpinningIcon(icon: ImageVector, accent: Color) {
    val transition = rememberInfiniteTransition(label = "spin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = Motion.Standard),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier
            .size(11.dp)
            .rotate(rotation)
    )
}

private data class StatusStyle(
    val icon: ImageVector,
    val label: String,
    val accent: Color,
    val container: Color
)
