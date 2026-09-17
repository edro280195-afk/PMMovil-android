package com.example.pmmovil.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.components.SkeletonLine
import com.example.pmmovil.ui.components.SkeletonTitle
import com.example.pmmovil.ui.home.HomeLoadPhase
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlinx.coroutines.delay

// ═════════════════════════════════════════════════════════════════════════════
// ESTADO DE CARGA DEL HOME
//
// Mientras no sepamos qué módulos tiene el usuario, la grilla no se puede
// pintar. En vez de dejar la pantalla vacía, mostramos la silueta de lo que
// va a llegar y una píldora que dice en qué va el arranque.
// ═════════════════════════════════════════════════════════════════════════════

/** Frases del arranque, en el orden en que ocurren las cosas. */
private val loadingPhrases = listOf(
    "Verificando tu sesión",
    "Revisando tus permisos",
    "Preparando tus módulos",
    "Personalizando tu experiencia",
    "Un momento más"
)

/**
 * Silueta del home + indicador de progreso.
 *
 * Espera [appearDelayMillis] antes de dibujarse: cuando el servidor responde
 * rápido nadie ve un parpadeo de skeletons, sólo aparecen los módulos.
 */
@Composable
fun HomePreparingModules(
    phase: HomeLoadPhase,
    modifier: Modifier = Modifier,
    appearDelayMillis: Long = 180L
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(appearDelayMillis)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)),
        exit = fadeOut(animationSpec = tween(Motion.DurationQuick))
    ) {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val horizontalPad = if (screenWidth >= 600) 22.dp else 14.dp
        val gap = if (screenWidth >= 600) 14.dp else 10.dp

        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(22.dp))

            PreparingPill(
                phase = phase,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = horizontalPad)
            )

            Spacer(Modifier.height(24.dp))

            // ── Silueta: encabezado + tarjeta destacada
            SkeletonSectionHeader(modifier = Modifier.padding(horizontal = horizontalPad + 4.dp))
            Spacer(Modifier.height(10.dp))
            SkeletonModuleCard(
                height = if (screenWidth >= 600) 110.dp else 92.dp,
                modifier = Modifier.padding(horizontal = horizontalPad)
            )

            Spacer(Modifier.height(22.dp))

            // ── Silueta: encabezado + grilla 2x2
            SkeletonSectionHeader(modifier = Modifier.padding(horizontal = horizontalPad + 4.dp))
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPad),
                verticalArrangement = Arrangement.spacedBy(gap)
            ) {
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        SkeletonModuleCard(height = 104.dp, modifier = Modifier.weight(1f))
                        SkeletonModuleCard(height = 104.dp, modifier = Modifier.weight(1f))
                    }
                }
                SkeletonModuleCard(height = 88.dp, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Píldora con puntos animados y el mensaje de la etapa actual.
 *
 * El mensaje avanza solo cada [PHRASE_INTERVAL] aunque el servidor siga
 * callado: así el usuario ve movimiento y no siente que la app se colgó. Las
 * etapas reales empujan el mensaje hacia adelante cuando van más rápido.
 */
@Composable
private fun PreparingPill(
    phase: HomeLoadPhase,
    modifier: Modifier = Modifier
) {
    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (index < loadingPhrases.lastIndex) {
            delay(PHRASE_INTERVAL)
            index++
        }
    }

    LaunchedEffect(phase) {
        val piso = when (phase) {
            HomeLoadPhase.Sesion   -> 0
            HomeLoadPhase.Permisos -> 1
            HomeLoadPhase.Listo    -> 2
        }
        if (index < piso) index = piso
    }

    Surface(
        modifier = modifier.shadow(
            elevation = 2.dp,
            shape = CircleShape,
            clip = false,
            ambientColor = PmmBlueDeep.copy(alpha = 0.10f),
            spotColor = PmmBlueDeep.copy(alpha = 0.14f)
        ),
        shape = CircleShape,
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            BouncingDots(color = PmmBlueDeep)

            AnimatedContent(
                targetState = loadingPhrases[index],
                transitionSpec = {
                    (fadeIn(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) +
                        slideInVertically { it / 2 }) togetherWith
                        (fadeOut(tween(Motion.DurationQuick)) + slideOutVertically { -it / 2 })
                },
                label = "frase-carga"
            ) { texto ->
                Text(
                    text = texto,
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.1).sp
                )
            }
        }
    }
}

/**
 * Cuando no hubo forma de saber qué módulos mostrar (sin red y sin caché).
 * Antes esto dejaba el home vacío y sin explicación.
 */
@Composable
fun HomeModulesError(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPad = if (screenWidth >= 600) 22.dp else 14.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = PmmTextMuted,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "No pudimos cargar tus módulos",
            color = PmmTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Revisa tu conexión o la VPN e inténtalo de nuevo.",
            color = PmmTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        Surface(
            modifier = Modifier.clickable(onClick = onRetry),
            shape = CircleShape,
            color = PmmBlueDeep
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = PmmBgSurface,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    text = "Reintentar",
                    color = PmmBgSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Estado explícito para una cuenta autenticada sin módulos de PMMovil. */
@Composable
fun HomeNoModulesAssigned(
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val horizontalPad = if (screenWidth >= 600) 22.dp else 14.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPad, vertical = 28.dp),
        shape = RoundedCornerShape(22.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = PmmBlueDeep.copy(alpha = 0.10f)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.padding(14.dp).size(30.dp)
                )
            }
            Text(
                text = "Sin módulos asignados",
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tu usuario no tiene permisos para operar PMMovil en este momento. Solicita acceso al área de Sistemas.",
                color = PmmTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Cuando te asignen un permiso, cierra sesión e inicia nuevamente para actualizar el menú.",
                color = PmmTextMuted,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Tres puntos que laten en secuencia. */
@Composable
private fun BouncingDots(color: Color) {
    val transition = rememberInfiniteTransition(label = "puntos")
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { i ->
            val escala by transition.animateFloat(
                initialValue = 0.45f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(520, easing = Motion.Standard),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(i * 170)
                ),
                label = "punto$i"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        scaleX = escala
                        scaleY = escala
                        alpha = escala
                    }
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/** Encabezado de sección en versión silueta (título + badge). */
@Composable
private fun SkeletonSectionHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Skeleton(width = 96.dp, height = 18.dp, shape = RoundedCornerShape(8.dp))
        Skeleton(width = 22.dp, height = 15.dp, shape = RoundedCornerShape(6.dp))
    }
}

/**
 * Tarjeta de módulo en versión silueta. Reproduce la estructura real
 * (recuadro de ícono + título + subtítulo) para que la transición al
 * contenido definitivo no mueva el layout.
 */
@Composable
private fun SkeletonModuleCard(
    height: Dp,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp
    val isTablet = screenWidth >= 600
    val iconBoxSize = if (isTablet) 64.dp else 52.dp
    val paddingH = if (isTablet) 20.dp else 14.dp

    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = paddingH, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Skeleton(
                width = iconBoxSize,
                height = iconBoxSize,
                shape = RoundedCornerShape(14.dp)
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.fillMaxWidth(0.62f)) { SkeletonTitle() }
                Box(Modifier.fillMaxWidth(0.42f)) { SkeletonLine() }
            }
        }
    }
}

private const val PHRASE_INTERVAL = 1500L
