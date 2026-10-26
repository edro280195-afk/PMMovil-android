package com.example.pmmovil.ui.scanner

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.data.scanner.ScannedCode
import com.example.pmmovil.data.scanner.lockedCodeOf
import com.example.pmmovil.ui.scanactions.kindLabel
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.SignageType
import com.google.ar.core.ArCoreApk
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

/** Cuánto sigue rotulado un código después de la última vez que el visor lo vio. */
private const val LOCK_HOLD_MS = 900L

/** Cuánto se espera, ya sin cámara de CameraX, antes de que ARCore abra la suya. */
private const val AR_HANDOFF_DELAY_MS = 450L

/**
 * El escáner del inicio: la cámara de siempre (con su marco que se cierra sobre el código, aquí en amarillo) y encima
 * lo que hace «universal» al escáner: el tipo del código que tiene fijado y el cambio a «Varios (AR)».
 *
 * Solo acepta QR. Entrega la lectura tal cual y quien la recibe decide qué hacer; aquí no se consulta nada.
 *
 * @param onOpenAr abre el AR de entradas; `null` si el puesto no tiene Revisión y no se ofrece el modo.
 */
@Composable
fun UniversalScannerScreen(
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit,
    onOpenAr: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val arOffered = remember(context, onOpenAr != null) { onOpenAr != null && isArCoreSupported(context) }
    val currentOnOpenAr = rememberUpdatedState(onOpenAr)

    var locked by remember { mutableStateOf<ScannedCode?>(null) }
    val lastSeenAt = remember { AtomicLong(0L) }
    var goingToAr by remember { mutableStateOf(false) }

    // El rótulo se quita solo cuando el visor deja de ver el código; se mide con un reloj que no recompone.
    LaunchedEffect(Unit) {
        while (true) {
            delay(250)
            if (locked != null && SystemClock.elapsedRealtime() - lastSeenAt.get() > LOCK_HOLD_MS) locked = null
        }
    }

    // Al pasar a AR se quita primero la cámara de este visor (su salida de composición la suelta) y se espera un poco:
    // ARCore abre la suya y no puede si la de CameraX sigue abierta.
    LaunchedEffect(goingToAr) {
        if (goingToAr) {
            delay(AR_HANDOFF_DELAY_MS)
            currentOnOpenAr.value?.invoke()
        }
    }

    // El gatillo del lector de mano también funciona con la cámara abierta.
    if (!goingToAr) HardwareScannerListener(onBarcodeScanned)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!goingToAr) {
            SmartBarcodeCameraView(
                onBarcodeScanned = onBarcodeScanned,
                onPotentialBarcodes = { candidates ->
                    val code = lockedCodeOf(candidates.map { it.value })
                    if (code != null) {
                        lastSeenAt.set(SystemClock.elapsedRealtime())
                        if (code != locked) locked = code
                    }
                },
                title = stringResource(R.string.scanner_universal_title),
                subtitle = stringResource(R.string.scanner_universal_subtitle),
                barcodeFormats = ScanProfile.QrOnly.formats,
                continuous = false,
                // Igual que ScannerScreen: la lectura dispara una búsqueda y un cambio de pantalla, así que se exige
                // ver el mismo valor en dos cuadros seguidos.
                minConsecutiveReads = 2,
                showClose = true,
                onClose = onClose,
                compact = false,
                scanDistance = ScanDistance.Far,
                lockedAccent = PmmYellow,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 92.dp, start = 16.dp, end = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (arOffered) ScanModeToggle(onMany = { goingToAr = true })
                locked?.let { LockedCodeChip(it) }
            }
        }
    }
}

/** «Uno» (este visor) y «Varios (AR)». Estar en este visor es estar en «Uno»; tocar el otro cambia de pantalla. */
@Composable
private fun ScanModeToggle(onMany: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.scanner_mode_description)
    Surface(
        modifier = modifier.semantics { contentDescription = description },
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.48f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .selectableGroup()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            ModeSegment(text = stringResource(R.string.scanner_mode_one), selected = true, onClick = {})
            ModeSegment(text = stringResource(R.string.scanner_mode_many), selected = false, onClick = onMany)
        }
    }
}

@Composable
private fun ModeSegment(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .background(
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(999.dp)
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) PmmNavyDark else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

/** El tipo y el valor del código que el visor tiene fijado, en amarillo como su marco. */
@Composable
private fun LockedCodeChip(code: ScannedCode, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = PmmYellow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (code == ScannedCode.Gafete) {
                // Un gafete no tiene valor que mostrar: nunca se conserva.
                Text(
                    text = stringResource(R.string.scanner_lock_badge),
                    color = PmmNavyDark,
                    style = SignageType.Caption.copy(fontSize = 12.sp)
                )
            } else {
                Text(
                    text = stringResource(kindLabel(code)),
                    color = PmmNavyDark,
                    style = SignageType.Caption.copy(fontSize = 12.sp)
                )
                Text(
                    text = code.normalized,
                    color = PmmNavyDark,
                    fontFamily = SignageType.Mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** ARCore no descarta este equipo (instalado, por instalar o por actualizar). Los estados transitorios cuentan como sí. */
internal fun isArCoreSupported(context: Context): Boolean =
    runCatching { !ArCoreApk.getInstance().checkAvailability(context).isUnsupported }
        .getOrDefault(false)
