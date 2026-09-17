package com.example.pmmovil.ui.salida.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.revision.BarcodeCameraPanel
import com.example.pmmovil.ui.salida.SalidaCaptureMode
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmYellow
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Escenario de escaneo panorámico y dominante.
 * Cumple con el estándar de la industria (Scandit / Google Material 3) ocupando
 * el viewport superior completo, evitando recuadros pequeños empotrados.
 */
@Composable
fun SalidaScannerStage(
    captureMode: SalidaCaptureMode,
    onBarcodeScanned: (String) -> Unit,
    onSwitchToCamera: () -> Unit,
    barcodeFormats: Int = Barcode.FORMAT_ALL_FORMATS,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF070B14))
    ) {
        if (captureMode == SalidaCaptureMode.CAMERA) {
            CameraActiveStage(
                onBarcodeScanned = onBarcodeScanned,
                barcodeFormats = barcodeFormats
            )
        } else {
            HardwareLaserRadarStage(
                onSwitchToCamera = onSwitchToCamera
            )
        }
    }
}

/**
 * Vista activa de cámara con overlay de retícula inteligente y láser animado.
 */
@Composable
private fun CameraActiveStage(
    onBarcodeScanned: (String) -> Unit,
    barcodeFormats: Int
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Feed real de cámara CameraX + ML Kit
        BarcodeCameraPanel(
            onBarcodeScanned = onBarcodeScanned,
            barcodeFormats = barcodeFormats,
            subtitle = "",
            modifier = Modifier.fillMaxSize()
        )

        // Overlay con retícula y láser dinámico
        ScannerReticleOverlay(modifier = Modifier.fillMaxSize())

        // Píldora translúcida flotante con instrucción rápida
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
            color = Color(0xCC0F172A),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PmmYellow)
                )
                Text(
                    text = "Apunta al código QR o de barras",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Retícula de 4 esquinas abiertas (Scandit Style) con barrido láser animado.
 */
@Composable
private fun ScannerReticleOverlay(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserSweep"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Dimensiones del marco de encuadre (ancho cómodo 76%, alto 46%)
        val frameW = w * 0.76f
        val frameH = h * 0.46f
        val left = (w - frameW) / 2f
        val top = (h - frameH) / 2f
        val right = left + frameW
        val bottom = top + frameH

        val bracketLength = 32.dp.toPx()
        val strokeWidth = 3.5.dp.toPx()
        val cornerColor = Color(0xFFF8E71C)

        // Esquina Superior Izquierda
        drawLine(cornerColor, Offset(left, top), Offset(left + bracketLength, top), strokeWidth)
        drawLine(cornerColor, Offset(left, top), Offset(left, top + bracketLength), strokeWidth)

        // Esquina Superior Derecha
        drawLine(cornerColor, Offset(right, top), Offset(right - bracketLength, top), strokeWidth)
        drawLine(cornerColor, Offset(right, top), Offset(right, top + bracketLength), strokeWidth)

        // Esquina Inferior Izquierda
        drawLine(cornerColor, Offset(left, bottom), Offset(left + bracketLength, bottom), strokeWidth)
        drawLine(cornerColor, Offset(left, bottom), Offset(left, bottom - bracketLength), strokeWidth)

        // Esquina Inferior Derecha
        drawLine(cornerColor, Offset(right, bottom), Offset(right - bracketLength, bottom), strokeWidth)
        drawLine(cornerColor, Offset(right, bottom), Offset(right, bottom - bracketLength), strokeWidth)

        // Línea Láser Animada
        val laserY = top + (frameH * laserProgress)
        val laserInset = frameW * 0.05f
        val laserStart = Offset(left + laserInset, laserY)
        val laserEnd = Offset(right - laserInset, laserY)

        val laserBrush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                cornerColor.copy(alpha = 0.95f),
                cornerColor,
                cornerColor.copy(alpha = 0.95f),
                Color.Transparent
            ),
            start = laserStart,
            end = laserEnd
        )

        drawLine(
            brush = laserBrush,
            start = laserStart,
            end = laserEnd,
            strokeWidth = 2.5.dp.toPx()
        )
    }
}

/**
 * Escenario para lector láser físico o terminal industrial Zebra (TC26 / TC57).
 * Muestra un radar pulsante estético para transmitir actividad de lectura continua
 * sin consumir la batería del sensor de cámara.
 */
@Composable
private fun HardwareLaserRadarStage(
    onSwitchToCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val pulse1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse1"
    )
    val pulse2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PmmNavyDark,
                        Color(0xFF030712)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ondas de radar pulsantes
        Canvas(modifier = Modifier.size(240.dp)) {
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.width / 2f

            // Onda 1
            drawCircle(
                color = PmmBlueLight.copy(alpha = (1f - pulse1) * 0.35f),
                radius = maxRadius * pulse1,
                center = centerOffset,
                style = Stroke(width = 2.dp.toPx())
            )

            // Onda 2
            drawCircle(
                color = PmmYellow.copy(alpha = (1f - pulse2) * 0.40f),
                radius = maxRadius * pulse2,
                center = centerOffset,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(20.dp)
        ) {
            // Icono central pulsante
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(2.dp, PmmYellow.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = null,
                    tint = PmmYellow,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "ESCÁNER LÁSER LISTO",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Presiona el gatillo físico de la Zebra\no lector Bluetooth",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(14.dp))

            // Botón para activar cámara a 1-tap
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onSwitchToCamera),
                color = Color.White.copy(alpha = 0.14f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = PmmYellow,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Encender Cámara",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
