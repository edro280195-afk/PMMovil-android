package com.example.pmmovil.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.example.pmmovil.ui.theme.Motion

/**
 * Háptica compartida de PMMóvil.
 *
 * Compose 1.7 solo expone LongPress y TextHandleMove en HapticFeedbackType, y
 * ninguno de los dos es el golpecito corto que queremos al tocar un botón. Por
 * eso vamos directo a `View.performHapticFeedback`, que sí tiene la escala
 * completa de constantes de la plataforma.
 *
 * Se usa la sobrecarga de un solo parámetro a propósito: respeta el ajuste de
 * vibración del sistema. Si el usuario apagó la háptica en la tableta, no se la
 * forzamos.
 */
@Stable
class PmmHaptics(private val view: View) {

    /** Toque normal de botón: el golpecito más corto que da la plataforma. */
    fun tap() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    /** Cambio de selección: chip, opción de una hoja, pestaña. Más seco que tap. */
    fun select() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    /** Algo salió bien y ya no hay vuelta atrás: guardado, salida registrada. */
    fun confirm() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    /** Algo se rechazó: código inválido, campo faltante. */
    fun reject() {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.REJECT
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberPmmHaptics(): PmmHaptics {
    val view = LocalView.current
    return remember(view) { PmmHaptics(view) }
}

/**
 * Toque con háptico y nada más. Es lo que va en las FILAS de una lista larga.
 *
 * `pmmPressable` crea por cada fila un MutableInteractionSource, un colector de
 * flujo y un Animatable; al reciclar decenas de filas por segundo eso se siente
 * como lag de scroll. Medido en la Armor Pad con 123 cajas en patio: 60% de
 * cuadros con jank contra 39% de una pantalla equivalente que no lo usa.
 * El hundido se queda para los botones grandes, que son pocos y no se reciclan.
 */
@Composable
fun Modifier.pmmTap(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier {
    val haptics = rememberPmmHaptics()
    return this.clickable(enabled = enabled) {
        haptics.tap()
        onClick()
    }
}

/**
 * Botón "físico": se hunde al tocarlo y vibra. Sin ripple, porque sobre las
 * tarjetas grandes de caseta el ripple se ve sucio y el hundido comunica mejor.
 *
 * Solo para elementos que NO se reciclan en una lista: ver `pmmTap`.
 *
 * `pressedScale` baja con el tamaño del elemento: una tarjeta grande necesita
 * menos escala que un chip para que el hundido se lea igual.
 */
@Composable
fun Modifier.pmmPressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.955f,
    onClick: () -> Unit
): Modifier {
    val haptics = rememberPmmHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Motion.SpringDampingLowBounce,
            stiffness = Motion.SpringStiffnessMedium
        ),
        label = "pressScale"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            haptics.tap()
            onClick()
        }
}
