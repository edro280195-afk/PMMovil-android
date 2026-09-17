package com.example.pmmovil.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val startXFraction: Float,
    val delayFraction: Float,
    val fallDurationFraction: Float,
    val driftAmplitude: Float,
    val rotationTurns: Float,
    val color: Color,
    val pieceWidth: Float,
    val pieceHeight: Float
)

// Paleta de marca -- se ve festivo sin caer en algo infantil.
private val ConfettiColors = listOf(
    Color(0xFF16A34A), // verde éxito
    Color(0xFFBECC00), // amarillo PMM
    Color(0xFF003D8C), // azul PMM
    Color(0xFFF59E0B)  // ámbar
)

/**
 * Ráfaga de confeti de un solo tiro, pensada para cubrir toda la pantalla
 * brevemente (p. ej. al finalizar una revisión) antes de navegar. Sin
 * dependencia externa: son rectángulos animados con Canvas, calculados
 * analíticamente a partir de un único progreso 0f→1f.
 */
@Composable
fun ConfettiOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    pieceCount: Int = 70,
    durationMillis: Int = 1400
) {
    if (!visible) return

    val pieces = remember(visible) {
        List(pieceCount) {
            ConfettiPiece(
                startXFraction = Random.nextFloat(),
                delayFraction = Random.nextFloat() * 0.3f,
                fallDurationFraction = 0.65f + Random.nextFloat() * 0.35f,
                driftAmplitude = (Random.nextFloat() - 0.5f) * 0.35f,
                rotationTurns = 1f + Random.nextFloat() * 3f,
                color = ConfettiColors[Random.nextInt(ConfettiColors.size)],
                pieceWidth = 5f + Random.nextFloat() * 5f,
                pieceHeight = 9f + Random.nextFloat() * 7f
            )
        }
    }

    val progress = remember(visible) { Animatable(0f) }
    LaunchedEffect(visible) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(durationMillis, easing = LinearEasing))
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        pieces.forEach { piece ->
            val local = ((progress.value - piece.delayFraction) / piece.fallDurationFraction)
                .coerceIn(0f, 1f)
            if (local <= 0f) return@forEach

            val y = -piece.pieceHeight + h * local * 1.15f
            val x = w * piece.startXFraction + w * piece.driftAmplitude * sin(local * 6f)
            val alpha = when {
                local < 0.08f -> local / 0.08f
                local > 0.82f -> ((1f - local) / 0.18f).coerceIn(0f, 1f)
                else -> 1f
            }

            rotate(degrees = local * 360f * piece.rotationTurns, pivot = Offset(x, y)) {
                drawRect(
                    color = piece.color.copy(alpha = alpha),
                    topLeft = Offset(x - piece.pieceWidth / 2, y - piece.pieceHeight / 2),
                    size = Size(piece.pieceWidth, piece.pieceHeight)
                )
            }
        }
    }
}
