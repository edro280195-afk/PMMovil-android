package com.example.pmmovil.ui.casetaentrada

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlinx.coroutines.delay

private val RefrigeratedCalmContainer = Color(0xFFECFDF5)
private val RefrigeratedAlertLow = Color(0xFFFFF7ED)
private val RefrigeratedAlertHigh = Color(0xFFFEE2E2)

/**
 * Tarjeta de la temperatura y el combustible de una caja refrigerada.
 *
 * Mientras falte alguno de los dos ([pending]) la tarjeta destella (borde y fondo van de ámbar a rojo y el borde
 * engorda), vibra una vez al aparecer y manda el cursor a la temperatura ([onAttention]) para que el guardia no pueda
 * pasarla por alto: en producción las refrigeradas se guardaban con la temperatura vacía o con «N/A». Con los dos datos
 * buenos se calma y se pinta de verde. Si se intentó guardar con el dato mal ([hasError]) el destello se vuelve más
 * rápido.
 *
 * Para llamarla a mano en una prueba o vista previa basta [pending] y [content]; [onAttention] puede fallar si el campo
 * todavía no existe (se atrapa).
 */
@Composable
internal fun RefrigeratedCaptureCard(
    boxTypeLabel: String,
    pending: Boolean,
    hasError: Boolean,
    onAttention: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Al aparecer (o al volver a este paso) con datos pendientes: una vibración y el cursor en la temperatura.
    LaunchedEffect(Unit) {
        if (pending) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // El campo entra en la misma pasada de composición; el respiro deja que ya esté listo y que se cierre
            // la hoja de selección del tipo de caja antes de abrir el teclado.
            delay(350)
            runCatching { onAttention() }
        }
    }

    val phase = refrigeratedFlashPhase(active = pending, fast = hasError)
    val borderColor = if (pending) lerp(PmmAmber, PmmRed, phase) else PmmGreenDark
    val container = if (pending) lerp(RefrigeratedAlertLow, RefrigeratedAlertHigh, phase) else RefrigeratedCalmContainer
    val borderWidth: Dp = if (pending) (2.dp + 2.dp * phase) else 1.5.dp

    Surface(
        color = container,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (pending) Icons.Default.AcUnit else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = if (pending) PmmRed else PmmGreenDark,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = if (pending) "CAJA REFRIGERADA · CAPTURA OBLIGATORIA" else "CAJA REFRIGERADA · DATOS CAPTURADOS",
                    color = if (pending) PmmRed else PmmGreenDark,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = if (pending) {
                    "Por ${boxTypeLabel.ifBlank { "esta caja" }} anota la temperatura que marca el termo y su combustible. " +
                        "Sin los dos no se puede guardar."
                } else {
                    "Temperatura y combustible del termo listos."
                },
                color = if (pending) PmmAmberDark else PmmTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            content()
        }
    }
}

/**
 * Fase 0..1 del destello. Sin [active] no se arma la animación infinita (no gasta cuadros cuando la tarjeta ya está
 * calmada); con [fast] va más rápido.
 */
@Composable
private fun refrigeratedFlashPhase(active: Boolean, fast: Boolean): Float {
    if (!active) return 0f
    val transition = rememberInfiniteTransition(label = "refrigeradaDestello")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (fast) 450 else 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "refrigeradaDestelloFase"
    )
    return phase
}
