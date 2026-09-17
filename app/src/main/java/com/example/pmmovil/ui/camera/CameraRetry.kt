package com.example.pmmovil.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import com.example.pmmovil.ui.theme.PmmBlueDeep

/**
 * Estado de reintento compartido por toda pantalla que enlaza cámara.
 *
 * [attempt] es la clave que cada pantalla usa en su `LaunchedEffect` de
 * enlace: cambiarlo relanza el efecto y reabre la cámara. Nunca cambia por
 * sí solo salvo que [CameraWarmupTracker] declare la sesión muerta (el HAL
 * aceptó la sesión pero no entregó ni un cuadro utilizable, ver
 * [CameraWarmupMonitor.isFailed]).
 */
class CameraRetryState internal constructor(
    val attempt: Int,
    val failed: Boolean,
    val retry: () -> Unit,
    /**
     * Para el fallo que no pasa por [CameraWarmupTracker]: `CameraOwner.bind`
     * devolvió null o lanzó. Ahí no tiene sentido reintentar solo -- ya falló
     * el enlace mismo, no la convergencia -- así que salta directo al estado
     * fallido sin gastar los reintentos automáticos.
     */
    val forceFail: () -> Unit
)

/**
 * Reintenta automáticamente hasta [maxAttempts] veces cuando el monitor
 * declara la sesión muerta, y después se rinde: `failed` pasa a `true` y la
 * pantalla debe mostrar [CameraErrorView] en vez de dejar la cortina de
 * "Preparando cámara" puesta para siempre.
 *
 * Antes de esto, cada pantalla de cámara tenía su cortina atada solamente a
 * `isReady`: si una sesión se declaraba fallida, `isReady` nunca se ponía en
 * `true` (son estados mutuamente excluyentes en el tracker) y la cortina se
 * quedaba ahí sin que nada la moviera. Sólo `SmartBarcodeCameraView` tenía
 * su propia lógica de reintento; esta función la generaliza para las cuatro
 * pantallas de cámara.
 */
@Composable
fun rememberCameraRetry(
    warmup: CameraWarmupMonitor,
    maxAttempts: Int = 2
): CameraRetryState {
    val warmupFailed by warmup.isFailed.collectAsState()
    var attempt by remember { mutableIntStateOf(0) }
    var automaticRetries by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(warmupFailed) {
        if (!warmupFailed) return@LaunchedEffect
        if (automaticRetries < maxAttempts) {
            automaticRetries += 1
            warmup.reset()
            attempt += 1
        } else {
            failed = true
        }
    }

    return CameraRetryState(
        attempt = attempt,
        failed = failed,
        retry = {
            failed = false
            automaticRetries = 0
            warmup.reset()
            attempt += 1
        },
        forceFail = { failed = true }
    )
}

/**
 * La cámara no arranca: se agotaron los reintentos automáticos de
 * [rememberCameraRetry], o el enlace falló de entrada. Se prefiere decirlo a
 * dejar un visor negro o una cortina puesta para siempre.
 */
@Composable
fun CameraErrorView(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.CameraAlt,
    title: String = "La cámara no respondió",
    detail: String = "Se intentó abrirla varias veces sin recibir imagen.",
    compact: Boolean = false
) {
    Box(
        modifier = modifier
            .background(Color(0xFF070B14))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(if (compact) 26.dp else 34.dp)
            )
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 13.sp else 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = detail,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = if (compact) 11.sp else 12.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                )
            ) {
                Text("Reintentar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
