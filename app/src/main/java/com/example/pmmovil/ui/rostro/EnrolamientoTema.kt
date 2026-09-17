package com.example.pmmovil.ui.rostro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellowDark

/**
 * Paleta del enrolamiento: la misma que Ajustes y el resto de la app.
 *
 * El login facial ([RostroTema]) sí va oscuro a pantalla completa, y con razón:
 * ahí la pantalla hace de lámpara y no hay nada que leer. El enrolamiento es lo
 * contrario -- se lee, se acepta un aviso legal y se entra desde Ajustes -- así
 * que vive en claro, con azul PMM sobre blanco. El verde aparece una sola vez,
 * al sellar, y por eso significa algo.
 */
object EnrolamientoTema {
    val Fondo = Color(0xFFF6F9FE)
    val Superficie = PmmBgSurface
    val SuperficieAlta = Color(0xFFFFFFFF)
    val Linea = Color(0xFFE7EEF8)
    val Riel = PmmNavyBorder

    /** Azul institucional. Trazo del aro, botones y acentos. */
    val Glifo = PmmBlueDeep
    val Brillo = PmmBlueLight

    /** Detrás del preview mientras la cámara arranca. */
    val Preview = Color(0xFFCBD9EC)

    val Ok = PmmGreen
    val OkOscuro = PmmGreenDark
    val OkTexto = Color(0xFF3F6B52)
    val OkBorde = Color(0xFFBBF7D0)
    val Aviso = PmmYellowDark
    val AvisoOscuro = Color(0xFFB45309)
    val Malo = Color(0xFFB91C1C)

    /** Fondos suaves de los iconos del aviso. */
    val AzulTenue = Color(0xFFEEF3FF)
    val VerdeTenue = Color(0xFFF0FDF4)
    val VerdeMedio = Color(0xFFDCFCE7)
    val AmbarTenue = Color(0xFFFEF9E7)

    val Texto = PmmTextPrimary
    val TextoTenue = PmmTextSecondary
    val TextoMudo = PmmTextMuted
}

/**
 * Fondo del enrolamiento: blanco arriba, azul muy claro abajo, con dos halos
 * apenas perceptibles. Nada de grano — eso hace falta sobre degradados oscuros,
 * donde un panel de 8 bits produce anillos visibles; en claro no hay bandeo que
 * romper y el ruido sólo ensuciaría.
 */
@Composable
fun EnrolamientoFondo(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF4F8FF),
                            EnrolamientoTema.Fondo
                        )
                    )
                )
                drawCircle(
                    color = EnrolamientoTema.Glifo.copy(alpha = 0.055f),
                    radius = size.maxDimension * 0.62f,
                    center = Offset(size.width * 0.5f, -size.height * 0.12f)
                )
                drawCircle(
                    color = EnrolamientoTema.Glifo.copy(alpha = 0.035f),
                    radius = size.maxDimension * 0.45f,
                    center = Offset(size.width * 0.5f, size.height * 1.08f)
                )
            },
        content = content
    )
}
