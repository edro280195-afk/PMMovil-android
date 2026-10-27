package com.example.pmmovil.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Hasta dónde crece la letra dentro de una superficie de tamaño fijo, como las tarjetas que flotan sobre la cámara del
 * AR: su alto va en dp y su texto en sp, así que con la letra del sistema al máximo el texto se sale y se recorta. Todo
 * lo demás de la app crece con la letra del usuario; esto es solo para lo que no puede crecer.
 */
const val FIXED_SURFACE_MAX_FONT_SCALE = 1.15f

/** La escala de letra que se aplica: la del usuario, sin pasar de [max]. Un valor inválido cuenta como 1. */
internal fun cappedFontScale(current: Float, max: Float): Float =
    if (!current.isFinite() || current <= 0f) 1f else current.coerceAtMost(max)

/** Pone un tope a la letra dentro de [content]. Con la escala normal no cambia nada. */
@Composable
fun LimitFontScale(max: Float = FIXED_SURFACE_MAX_FONT_SCALE, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val capped = cappedFontScale(density.fontScale, max)
    if (capped == density.fontScale) {
        content()
    } else {
        CompositionLocalProvider(LocalDensity provides Density(density.density, capped)) { content() }
    }
}
