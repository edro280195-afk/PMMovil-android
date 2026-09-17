package com.example.pmmovil.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmBlueLight

/**
 * Cortina que tapa el visor mientras la cámara arranca y su exposición
 * converge (ver [CameraWarmupMonitor]). Aparece de golpe y se desvanece al
 * quedar lista, así el operador nunca ve los cuadros negros o quemados del
 * arranque; solo un "preparando" breve.
 */
@Composable
fun CameraWarmupCurtain(
    visible: Boolean,
    modifier: Modifier = Modifier,
    message: String = "Preparando cámara…",
    compact: Boolean = false
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = snap()),
        exit = fadeOut(animationSpec = tween(CURTAIN_FADE_OUT_MS)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CurtainBackground),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = PmmBlueLight,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(if (compact) 26.dp else 34.dp)
                )
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
                Text(
                    text = message,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = if (compact) 11.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private val CurtainBackground = Color(0xFF070B14)
private const val CURTAIN_FADE_OUT_MS = 280
