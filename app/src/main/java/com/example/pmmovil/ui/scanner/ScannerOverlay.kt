package com.example.pmmovil.ui.scanner

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Overlay reutilizable para las pantallas con camara: marco de captura,
 * marco pulsante durante el auto-zoom, mensajes contextuales segun el
 * estado del frame y hotkey de doble tap para reset.
 */
@Composable
fun ScannerOverlay(
    state: FrameState,
    isAutoZooming: Boolean,
    currentZoomRatio: Float,
    onDoubleTap: () -> Unit,
    onRequestFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        ScannerFrame(
            state = state,
            isAutoZooming = isAutoZooming,
            onDoubleTap = onDoubleTap,
            modifier = Modifier.fillMaxSize()
        )

        if (state != FrameState.Searching) {
            CameraStatusBanner(
                state = state,
                onRequestFlash = onRequestFlash,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp)
            )
        }

        if (isAutoZooming) {
            ZoomIndicator(
                zoom = currentZoomRatio,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
            )
        }
    }
}

@Composable
private fun ScannerFrame(
    state: FrameState,
    isAutoZooming: Boolean,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = when (state) {
        FrameState.TooDark, FrameState.TooBright -> PmmAmber
        FrameState.NoContent, FrameState.Blurry -> PmmRed
        FrameState.AlmostThere -> PmmGreenDark
        FrameState.Sweeping -> PmmBlueDeep
        FrameState.Searching -> Color.White
    }

    val infinite = rememberInfiniteTransition(label = "scan-frame-pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan-frame-pulse-alpha"
    )

    val effectiveAlpha = if (isAutoZooming) pulse else 1f

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .alpha(effectiveAlpha)
        ) {
            val frameWidth = size.width * 0.72f
            val frameHeight = size.height * 0.34f
            val topLeft = Offset(
                (size.width - frameWidth) / 2f,
                (size.height - frameHeight) / 2f
            )
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.24f),
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(18.dp.toPx())
            )
            drawRoundRect(
                color = Color.White.copy(alpha = 0.34f),
                topLeft = topLeft,
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.96f),
                topLeft = topLeft,
                size = Size(frameWidth, frameHeight),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 2.5.dp.toPx())
            )
            drawLine(
                color = accent.copy(alpha = 0.96f),
                start = Offset(topLeft.x + 18.dp.toPx(), size.height / 2f),
                end = Offset(
                    topLeft.x + frameWidth - 18.dp.toPx(),
                    size.height / 2f
                ),
                strokeWidth = 1.5.dp.toPx()
            )
        }
    }
}

@Composable
private fun CameraStatusBanner(
    state: FrameState,
    onRequestFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = state.toBannerInfo()
    Surface(
        modifier = modifier,
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = info.icon,
                contentDescription = null,
                tint = info.tint,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(
                    text = info.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = info.subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.width(6.dp))
            if (info.showFlashButton) {
                IconButton(
                    onClick = onRequestFlash,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(PmmAmber)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Encender flash",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

private data class BannerInfo(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val showFlashButton: Boolean
)

private fun FrameState.toBannerInfo(): BannerInfo = when (this) {
    FrameState.Searching -> BannerInfo(
        title = "Buscando...",
        subtitle = "Apunta el codigo dentro del marco",
        icon = Icons.Default.Search,
        tint = Color.White,
        showFlashButton = false
    )
    FrameState.AlmostThere -> BannerInfo(
        title = "Casi...",
        subtitle = "Mantelo un momento, ajustare el zoom",
        icon = Icons.Default.Visibility,
        tint = PmmGreenDark,
        showFlashButton = false
    )
    FrameState.NoContent -> BannerInfo(
        title = "No veo nada",
        subtitle = "Acercate o encuadra el codigo",
        icon = Icons.Default.PanTool,
        tint = PmmRed,
        showFlashButton = false
    )
    FrameState.TooDark -> BannerInfo(
        title = "Muy oscuro",
        subtitle = "Enciende el flash o mejora la luz",
        icon = Icons.Default.LightMode,
        tint = PmmAmber,
        showFlashButton = true
    )
    FrameState.TooBright -> BannerInfo(
        title = "Muy iluminado",
        subtitle = "Alejate de la luz directa o baja el brillo",
        icon = Icons.Default.LightMode,
        tint = PmmAmber,
        showFlashButton = false
    )
    FrameState.Blurry -> BannerInfo(
        title = "Imagen borrosa",
        subtitle = "Estabiliza la camara antes de continuar",
        icon = Icons.Default.PanTool,
        tint = PmmRed,
        showFlashButton = false
    )
    FrameState.Sweeping -> BannerInfo(
        title = "Buscando activamente",
        subtitle = "Estoy probando distintos acercamientos",
        icon = Icons.Default.Search,
        tint = PmmBlueDeep,
        showFlashButton = false
    )
}

@Composable
private fun ZoomIndicator(
    zoom: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PmmBlueDeep.copy(alpha = 0.9f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = "%.1fx".format(zoom),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private val PmmRed = Color(0xFFDC2626)
private val PmmAmber = Color(0xFFF59E0B)
private val PmmBlueDeep = Color(0xFF0E3F75)
private val PmmGreenDark = Color(0xFF047857)
