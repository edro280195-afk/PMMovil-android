package com.example.pmmovil.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import com.example.pmmovil.ui.theme.Motion

/**
 * Contador animado de enteros. Cuando cambia `target`, anima desde el
 * valor anterior hasta el nuevo con un tween.
 *
 * Uso típico: KPIs en home, contadores de items, badges.
 *
 *   AnimatedCounter(target = stats.pendientes)
 */
@Composable
fun AnimatedCounter(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    durationMs: Int = Motion.DurationEmphasized
) {
    val animated by animateIntAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = durationMs, easing = Motion.EmphasizedDecelerate),
        label = "counter"
    )
    Text(text = animated.toString(), modifier = modifier, style = style)
}
