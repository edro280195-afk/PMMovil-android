package com.example.pmmovil.ui.theme

import android.graphics.Typeface
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Tipografía de la señalética del inicio y la ficha. No se empaca ninguna fuente: las tres familias vienen en Android
 * (Roboto, Roboto Condensed como `sans-serif-condensed` y la monoespaciada del sistema), así que el APK no crece y la
 * Honeywell CT60 con Android 9 las tiene igual que el Pixel.
 *
 * La monoespaciada es para cualquier valor que se compare carácter por carácter: códigos de caja, relación y entrada.
 */
object SignageType {
    val Condensed: FontFamily = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))
    val Mono: FontFamily = FontFamily.Monospace

    /** Cifra grande del Tablero y de los contadores. Los dígitos de Roboto ya son tabulares. */
    val Numeral = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 40.sp
    )

    /** Número chico dentro de una estación. */
    val StationNumber = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 12.sp
    )

    /** Rótulo en versalitas de las señales y los encabezados de sección. */
    val Caption = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.sp
    )

    /** Código de una etiqueta, en el tamaño de la ficha. */
    val Code = TextStyle(
        fontFamily = Mono,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.6).sp
    )
}

/**
 * Dice si el usuario dejó las animaciones del sistema encendidas. Con «Quitar animaciones» o la escala en cero, los
 * contadores y las entradas aparecen ya en su lugar en vez de rodar.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
