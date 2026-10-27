package com.example.pmmovil.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.example.pmmovil.ui.components.SignalKind
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contraste de la señalética con la fórmula de WCAG 2.x. Pide 4.5:1 para texto chico, que es el caso de la puerta de
 * carga: letra de 10 a 13 sp, a pleno sol y en una pantalla que se mueve.
 */
class ContrastTest {

    private fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun ratio(first: Color, second: Color): Double {
        val a = luminance(first)
        val b = luminance(second)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun assertReadable(label: String, text: Color, surface: Color) {
        val value = ratio(text, surface)
        assertTrue("$label: $value (hace falta 4.5)", value >= 4.5)
    }

    @Test
    fun `every signal can be read on its own background`() {
        SignalKind.entries.forEach { kind -> assertReadable(kind.name, kind.content, kind.container) }
    }

    @Test
    fun `the text colors that are read pass on the surfaces of the new screens`() {
        val surfaces = mapOf("blanco" to Color.White, "fondo de pantalla" to PmmBgPrimary)
        val texts = mapOf(
            "texto principal" to PmmTextPrimary,
            "texto secundario" to PmmTextSecondary,
            "azul institucional" to PmmBlueDeep
        )
        surfaces.forEach { (surfaceName, surface) ->
            texts.forEach { (textName, text) -> assertReadable("$textName sobre $surfaceName", text, surface) }
        }
    }

    @Test
    fun `the highlighted row of the board and the yellow button read well`() {
        assertReadable("blanco sobre la fila resaltada", Color.White, PmmNavyDark)
        // El texto que baja a 82 % de opacidad sobre la fila resaltada (el renglón de abajo de cada estación).
        assertReadable("renglón sobre la fila resaltada", Color.White.copy(alpha = 0.82f).compositeOver(PmmNavyDark), PmmNavyDark)
        assertReadable("texto sobre el botón amarillo", PmmTextPrimary, PmmYellow)
    }
}
