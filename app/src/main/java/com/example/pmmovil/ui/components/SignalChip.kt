package com.example.pmmovil.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.SignageType

/**
 * Estado de una pieza en el vocabulario de la señalética, tomado de los paneles de señal de seguridad (ANSI Z535):
 * una palabra, una forma y un color. Las tres cosas dicen lo mismo a propósito: quien no distingue colores, o ve la
 * pantalla bajo el sol de la puerta de carga, lo lee por la palabra y por la forma.
 *
 * El amarillo no está aquí: es de la acción principal de cada pantalla.
 */
enum class SignalKind(
    @param:StringRes val wordRes: Int,
    val container: Color,
    val content: Color,
    val outlined: Boolean = false
) {
    /** Retenido o con problema: discrepancia, daño. Octágono, como la señal de alto. */
    Alto(R.string.signal_alto, PmmRedDark, Color.White),

    /** Necesita atención pero no está detenido: en patio sin entrada, en revisión. Triángulo. */
    Atencion(R.string.signal_atencion, PmmAmberDark, Color.White),

    /** Puede avanzar: lista para salir, autorizada, revisada. Círculo con palomita. */
    Listo(R.string.signal_listo, PmmGreenDark, Color.White),

    /** Información sin urgencia: sin revisar, vigente, se lee en una revisión. Cuadrado con «i». */
    Aviso(R.string.signal_aviso, PmmBlueDeep, Color.White),

    /** Terminó su recorrido. Círculo vacío con palomita. */
    Cerrado(R.string.signal_cerrado, PmmTextSecondary, Color.White),

    /** No hay dato que mostrar. Círculo punteado con «?» y sin relleno. */
    SinDato(R.string.signal_sin_dato, Color.White, PmmTextSecondary, outlined = true)
}

/**
 * Panel de señal: la forma, la palabra y, si hace falta, un detalle corto («En patio sin entrada»).
 *
 * @param large versión de 34 dp para el sello de la ficha; la normal mide 26 dp y entra en una fila de lista.
 */
@Composable
fun SignalChip(
    kind: SignalKind,
    modifier: Modifier = Modifier,
    detail: String? = null,
    large: Boolean = false
) {
    val shape = RoundedCornerShape(7.dp)
    val height = if (large) 34.dp else 26.dp
    val word = stringResource(kind.wordRes).uppercase()

    Row(
        // Alto mínimo y no fijo: con la letra del sistema al máximo el detalle pasa a dos renglones y debe poder crecer.
        modifier = modifier
            .heightIn(min = height)
            .clip(shape)
            .background(kind.container)
            .then(if (kind.outlined) Modifier.dashedBorder(kind.content) else Modifier)
            .padding(
                start = if (large) 9.dp else 7.dp,
                end = if (large) 13.dp else 10.dp,
                top = 3.dp,
                bottom = 3.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SignalShape(kind = kind, size = if (large) 18.dp else 15.dp)
        Spacer(Modifier.width(7.dp))
        Text(
            text = word,
            color = kind.content,
            style = SignageType.Caption.copy(fontSize = if (large) 12.sp else 10.5.sp),
            maxLines = 1
        )
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier
                    .width(1.dp)
                    .height(if (large) 16.dp else 12.dp)
                    .background(kind.content.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = detail,
                // Sin lineHeight propio el tema le pone 24 sp de alto de línea y el chip se ensancha de más.
                modifier = Modifier.weight(1f, fill = false),
                color = kind.content,
                fontSize = if (large) 13.5.sp else 11.5.sp,
                lineHeight = if (large) 16.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Solo la forma de la señal, para usos donde no cabe la palabra (por ejemplo, junto a un número). */
@Composable
fun SignalShape(kind: SignalKind, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            when (kind) {
                SignalKind.Alto -> drawAlto()
                SignalKind.Atencion -> drawAtencion()
                SignalKind.Listo -> drawCheckedCircle(filled = true, mark = PmmGreenDark)
                SignalKind.Aviso -> drawAviso()
                SignalKind.Cerrado -> drawCheckedCircle(filled = false, mark = Color.White)
                SignalKind.SinDato -> drawSinDato(kind.content)
            }
        }
        if (kind == SignalKind.SinDato) {
            Text(
                text = "?",
                color = kind.content,
                fontSize = (size.value * 0.62f).sp,
                fontWeight = FontWeight.Bold,
                lineHeight = (size.value * 0.62f).sp
            )
        }
    }
}

// Las formas se dibujan sobre una cuadrícula de 16 x 16 unidades, la misma del mockup.
private fun DrawScope.unit(): Float = size.width / 16f

private fun DrawScope.mark(from: Offset, to: Offset, color: Color) {
    val u = unit()
    drawLine(
        color = color,
        start = Offset(from.x * u, from.y * u),
        end = Offset(to.x * u, to.y * u),
        strokeWidth = 1.8f * u,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawAlto() {
    val u = unit()
    val octagon = Path().apply {
        moveTo(5f * u, 1f * u)
        lineTo(11f * u, 1f * u)
        lineTo(15f * u, 5f * u)
        lineTo(15f * u, 11f * u)
        lineTo(11f * u, 15f * u)
        lineTo(5f * u, 15f * u)
        lineTo(1f * u, 11f * u)
        lineTo(1f * u, 5f * u)
        close()
    }
    drawPath(octagon, Color.White)
    mark(Offset(8f, 4.4f), Offset(8f, 8.7f), PmmRedDark)
    mark(Offset(8f, 10.6f), Offset(8f, 11.5f), PmmRedDark)
}

private fun DrawScope.drawAtencion() {
    val u = unit()
    val triangle = Path().apply {
        moveTo(8f * u, 1.5f * u)
        lineTo(15f * u, 14f * u)
        lineTo(1f * u, 14f * u)
        close()
    }
    drawPath(triangle, Color.White)
    drawPath(triangle, Color.White, style = Stroke(width = 1f * u, join = StrokeJoin.Round))
    mark(Offset(8f, 6f), Offset(8f, 9.6f), PmmAmberDark)
    mark(Offset(8f, 11.6f), Offset(8f, 12.3f), PmmAmberDark)
}

private fun DrawScope.drawCheckedCircle(filled: Boolean, mark: Color) {
    val u = unit()
    if (filled) {
        drawCircle(Color.White, radius = 7f * u, center = Offset(8f * u, 8f * u))
    } else {
        drawCircle(
            Color.White,
            radius = 7f * u,
            center = Offset(8f * u, 8f * u),
            style = Stroke(width = 1.6f * u)
        )
    }
    val check = Path().apply {
        moveTo(4.8f * u, 8.2f * u)
        lineTo(7f * u, 10.4f * u)
        lineTo(11.2f * u, 5.8f * u)
    }
    drawPath(
        check,
        mark,
        style = Stroke(width = 1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawAviso() {
    val u = unit()
    drawRoundRect(
        Color.White,
        topLeft = Offset(1.5f * u, 1.5f * u),
        size = Size(13f * u, 13f * u),
        cornerRadius = CornerRadius(3.5f * u)
    )
    drawLine(PmmBlueDeep, Offset(8f * u, 7f * u), Offset(8f * u, 11.4f * u), 1.9f * u, StrokeCap.Round)
    drawLine(PmmBlueDeep, Offset(8f * u, 4.6f * u), Offset(8f * u, 5.4f * u), 1.9f * u, StrokeCap.Round)
}

private fun DrawScope.drawSinDato(color: Color) {
    val u = unit()
    drawCircle(
        color,
        radius = 6.5f * u,
        center = Offset(8f * u, 8f * u),
        style = Stroke(
            width = 1.4f * u,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.4f * u, 2f * u))
        )
    )
}

/** Borde punteado de 1 dp, para la señal que no tiene relleno. */
private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val strokeWidth = 1.dp.toPx()
    val radius = 7.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
        size = Size(size.width - strokeWidth, size.height - strokeWidth),
        cornerRadius = CornerRadius(radius),
        style = Stroke(
            width = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
        )
    )
}
