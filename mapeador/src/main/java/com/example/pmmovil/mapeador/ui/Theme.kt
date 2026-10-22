package com.example.pmmovil.mapeador.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0E1116)
val PanelColor = Color(0xE6141A22)
val Accent = Color(0xFF26C6DA)
val Good = Color(0xFF43A047)
val Warn = Color(0xFFFFB300)
val Bad = Color(0xFFE53935)
val Muted = Color(0xFFB0BEC5)

/** Colores con los que se dibujan las capas de GL (para los puntos de los interruptores). */
val SurfaceDot = Color(0xFF1AE0DB)
val AnchorDot = Color(0xFFFFD12E)

@Composable
fun MapeadorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.Black,
            background = Ink,
            surface = Color(0xFF161B22),
            onSurface = Color.White,
            onBackground = Color.White
        ),
        content = content
    )
}
