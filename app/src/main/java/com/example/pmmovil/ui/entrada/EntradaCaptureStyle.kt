package com.example.pmmovil.ui.entrada

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmTextPrimary

/**
 * Cómo se pinta cada campo del formulario de Entrada según [CaptureStatus]. Un solo lugar para que todos los campos
 * (cajas de texto, tarjetas de selección, fecha) se vean igual:
 *  - capturado: fondo verde claro y borde verde, fijos (nada parpadea);
 *  - por capturar: borde azul marcado;
 *  - el que sigue: borde azul fuerte con un destello suave y lento.
 */
internal val CaptureGreenFill = Color(0xFFE6F7EC)
internal val CaptureGreenLine = Color(0xFF3CBF6E)
private val CapturePendingLine = PmmBlueDeep.copy(alpha = 0.55f)

internal fun captureContainerColor(status: CaptureStatus, base: Color): Color =
    if (status == CaptureStatus.CAPTURED) CaptureGreenFill else base

internal fun captureBorderColor(status: CaptureStatus, base: Color): Color = when (status) {
    CaptureStatus.CAPTURED -> CaptureGreenLine
    CaptureStatus.NEXT -> PmmBlueDeep
    CaptureStatus.PENDING -> CapturePendingLine
    CaptureStatus.NONE -> base
}

internal fun captureBorderWidth(status: CaptureStatus): Dp = when (status) {
    CaptureStatus.NEXT -> 2.dp
    CaptureStatus.PENDING -> 1.5.dp
    else -> 1.dp
}

/** Colores de un `OutlinedTextField` según el estado de captura. Con el foco, el campo conserva su borde normal. */
@Composable
internal fun captureTextFieldColors(status: CaptureStatus): TextFieldColors = when (status) {
    CaptureStatus.NONE -> OutlinedTextFieldDefaults.colors()
    CaptureStatus.CAPTURED -> OutlinedTextFieldDefaults.colors(
        unfocusedContainerColor = CaptureGreenFill,
        focusedContainerColor = CaptureGreenFill,
        disabledContainerColor = CaptureGreenFill,
        unfocusedBorderColor = CaptureGreenLine,
        disabledBorderColor = CaptureGreenLine,
        disabledTextColor = PmmTextPrimary,
        disabledLabelColor = PmmGreenDark
    )
    CaptureStatus.NEXT -> OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = PmmBlueDeep,
        unfocusedLabelColor = PmmBlueDeep
    )
    CaptureStatus.PENDING -> OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = CapturePendingLine
    )
}

/**
 * Destello del campo que sigue: un halo azul que respira despacio. Solo se anima el campo [CaptureStatus.NEXT], uno a
 * la vez, para no recargar la pantalla ni gastar batería en tabletas viejas. Los demás estados no dibujan nada.
 */
@Composable
internal fun Modifier.captureGlow(status: CaptureStatus, cornerRadius: Dp = 12.dp): Modifier {
    if (status != CaptureStatus.NEXT) return this
    val transition = rememberInfiniteTransition(label = "capture-glow")
    val pulse by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "capture-glow-pulse"
    )
    return this.drawBehind {
        val radius = cornerRadius.toPx()
        listOf(2.dp, 5.dp, 8.dp).forEachIndexed { index, spread ->
            val d = spread.toPx()
            drawRoundRect(
                color = PmmBlueLight.copy(alpha = pulse * (0.42f - index * 0.12f)),
                topLeft = Offset(-d, -d),
                size = Size(size.width + 2 * d, size.height + 2 * d),
                cornerRadius = CornerRadius(radius + d),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}

/** Rótulo de un campo capturado: la palomita verde delante del nombre, para quien no distingue bien los colores. */
@Composable
internal fun CaptureLabel(
    label: String,
    status: CaptureStatus,
    baseColor: Color,
    errorColor: Color? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (status == CaptureStatus.CAPTURED) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Capturado",
                tint = CaptureGreenLine,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            color = errorColor ?: if (status == CaptureStatus.CAPTURED) PmmGreenDark else baseColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

internal val CaptureCardShape = RoundedCornerShape(12.dp)
