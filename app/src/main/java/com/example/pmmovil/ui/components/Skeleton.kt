package com.example.pmmovil.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.rememberAnimationsEnabled

/**
 * Skeleton con shimmer animado. Reemplaza spinners: se siente más vivo
 * y da una mejor sensación de progreso.
 *
 * @param width ancho del bloque. null = fillMaxWidth
 * @param height alto del bloque
 * @param shape forma (default rounded 8dp)
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    val sized = modifier
        .then(if (width != null) Modifier.size(width = width, height = height)
              else Modifier.fillMaxWidth().height(height))
        .clip(shape)

    // Con «Quitar animaciones» el brillo no corre: queda un bloque quieto del mismo color de fondo.
    if (!rememberAnimationsEnabled()) {
        Box(modifier = sized.background(PmmNavyVariant))
        return
    }

    val transition = rememberInfiniteTransition(label = "skeleton")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = Motion.DurationSlow, easing = Motion.Standard),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val brush = Brush.linearGradient(
        colors = listOf(
            PmmBgCard,
            PmmNavyVariant,
            PmmNavyBorder.copy(alpha = 0.4f),
            PmmNavyVariant,
            PmmBgCard
        ),
        start = Offset(translate - 200f, 0f),
        end = Offset(translate, 0f)
    )

    Box(modifier = sized.background(brush))
}

@Composable
fun SkeletonCircle(size: Dp = 40.dp) {
    Skeleton(shape = CircleShape, width = size, height = size)
}

/** Línea de texto skeleton — más alto, full width */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier) {
    Skeleton(modifier = modifier, height = 14.dp, shape = RoundedCornerShape(6.dp))
}

/** Texto de título skeleton — más grueso */
@Composable
fun SkeletonTitle(modifier: Modifier = Modifier) {
    Skeleton(modifier = modifier, height = 20.dp, shape = RoundedCornerShape(8.dp))
}
