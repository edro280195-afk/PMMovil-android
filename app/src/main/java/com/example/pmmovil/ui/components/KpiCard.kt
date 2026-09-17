package com.example.pmmovil.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlue
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

/**
 * KPI card premium para home. Diseño: ícono en cápsula de color,
 * valor grande con AnimatedCounter, label, trend opcional.
 */
@Composable
fun KpiCard(
    label: String,
    value: Int,
    icon: ImageVector,
    accent: KpiAccent,
    modifier: Modifier = Modifier,
    delta: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val (accentColor, accentContainer) = accent.colors()

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick?.invoke()
        },
        enabled = onClick != null,
        modifier = modifier
            .shadow(
                elevation = 2.dp,
                shape = RoundedCornerShape(18.dp),
                clip = false,
                ambientColor = Color(0xFF0F172A).copy(alpha = 0.04f),
                spotColor = Color(0xFF0F172A).copy(alpha = 0.06f)
            ),
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (delta != null && delta != 0) {
                    TrendChip(delta = delta)
                }
            }

            Column {
                Text(
                    text = label.uppercase(),
                    color = PmmTextSecondary,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        target = value,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 30.sp,
                            letterSpacing = (-1).sp,
                            color = PmmTextPrimary
                        )
                    )
                }
            }
        }
    }
}

enum class KpiAccent { Blue, Green, Amber, Red, Yellow }

private fun KpiAccent.colors(): Pair<Color, Color> = when (this) {
    KpiAccent.Blue   -> PmmBlueDeep to Color(0xFFEEF3FF)
    KpiAccent.Green  -> PmmGreenDark to Color(0xFFDCFCE7)
    KpiAccent.Amber  -> PmmAmberDark to Color(0xFFFEF3C7)
    KpiAccent.Red    -> PmmRed to Color(0xFFFFEDED)
    KpiAccent.Yellow -> Color(0xFFB45309) to Color(0xFFFEF9C3)
}

@Composable
private fun TrendChip(delta: Int) {
    val isUp = delta > 0
    val isFlat = delta == 0
    val color = when {
        isFlat -> PmmTextMuted
        isUp   -> PmmRed
        else   -> PmmGreen
    }
    val container = when {
        isFlat -> PmmNavyBorder
        isUp   -> Color(0xFFFFEDED)
        else   -> Color(0xFFDCFCE7)
    }
    val icon = when {
        isFlat -> Icons.Default.TrendingFlat
        isUp   -> Icons.Default.TrendingUp
        else   -> Icons.Default.TrendingDown
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(container)
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(2.dp))
        Text(
            text = "${if (isUp) "+" else ""}$delta",
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black
        )
    }
}
