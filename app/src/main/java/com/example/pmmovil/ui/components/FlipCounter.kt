package com.example.pmmovil.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.SignageType
import com.example.pmmovil.ui.theme.rememberAnimationsEnabled
import kotlinx.coroutines.delay

/** Duración del giro de un dígito, de un tablero de aletas que se asienta con peso. */
private const val ROLL_MILLIS = 800

/**
 * Cifra que rueda como un tablero de aletas: cada dígito recorre los números intermedios, uno por unidad, y se
 * asienta en su valor. Los de la izquierda arrancan un poco después, así la cifra no se resuelve de golpe.
 *
 * Solo dibuja: el desplazamiento se lee dentro del bloque de dibujo, no en la composición, así que girar no vuelve a
 * componer nada. Con las animaciones del sistema apagadas muestra el valor ya asentado.
 *
 * Es para pocas cifras (las cinco del Tablero). No va dentro de una lista que se recicla.
 */
@Composable
fun FlipCounter(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = SignageType.Numeral,
    color: Color = PmmTextPrimary,
    animate: Boolean = rememberAnimationsEnabled(),
    staggerMillis: Int = 55
) {
    val digits = digitsOf(value)
    val measurer = rememberTextMeasurer()
    val layouts = remember(style, color) {
        (0..9).map { measurer.measure(AnnotatedString(it.toString()), style.copy(color = color)) }
    }
    val cell = layouts.first().size
    val description = value.toString()

    Row(modifier = modifier.semantics { contentDescription = description }) {
        digits.forEachIndexed { index, digit ->
            // La clave es la posición contada desde la derecha: si la cifra pasa de 9 a 10, las unidades conservan
            // su estado y solo aparece la columna nueva.
            key(digits.size - 1 - index) {
                DigitColumn(
                    target = digit,
                    layouts = layouts,
                    cell = cell,
                    animate = animate,
                    delayMillis = index * staggerMillis
                )
            }
        }
    }
}

@Composable
private fun DigitColumn(
    target: Int,
    layouts: List<TextLayoutResult>,
    cell: IntSize,
    animate: Boolean,
    delayMillis: Int
) {
    val position = remember { Animatable(if (animate) 0f else target.toFloat()) }
    LaunchedEffect(target, animate) {
        if (!animate) {
            position.snapTo(target.toFloat())
        } else {
            delay(delayMillis.toLong())
            position.animateTo(target.toFloat(), tween(ROLL_MILLIS, easing = Motion.EmphasizedDecelerate))
        }
    }
    val density = LocalDensity.current
    Canvas(
        modifier = Modifier
            .size(with(density) { cell.width.toDp() }, with(density) { cell.height.toDp() })
            .clearAndSetSemantics { }
    ) {
        clipRect {
            for (digit in 0..9) {
                val y = (digit - position.value) * cell.height
                // Solo se dibuja lo que cae dentro de la celda: a lo más dos dígitos a la vez.
                if (y > -cell.height && y < cell.height) {
                    drawText(layouts[digit], topLeft = Offset(0f, y))
                }
            }
        }
    }
}

/** Los dígitos de un entero no negativo, de izquierda a derecha. Un valor negativo se muestra como cero. */
internal fun digitsOf(value: Int): List<Int> =
    value.coerceAtLeast(0).toString().map { it - '0' }
