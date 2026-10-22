package com.example.pmmovil.mapeador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.session.MapCaptureController
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private const val TARGET_POINTS = 2000
private const val MIN_RECOMMENDED_POINTS = 500
private val SUGGESTED_CODES = listOf("A1", "A2", "B1", "B2", "F1", "H1", "ANDEN 3", "PATIO")

@Composable
fun CaptureScreen(store: MapStore, onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        MapCaptureController(store, File(base, "logs"))
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    var confirmExit by remember { mutableStateOf(false) }

    val requestExit = {
        if (controller.hud.keyframes > 0 && controller.savedMapId == null) confirmExit = true else onExit()
    }
    BackHandler { requestExit() }
    LaunchedEffect(controller.savedMapId) {
        if (controller.savedMapId != null) {
            delay(1500)
            onExit()
        }
    }

    ArScreenHost(listener = controller, handle = controller.arHandle, overlay = controller.overlay) {
        CaptureOverlay(controller, onBack = { requestExit() })
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("¿Salir sin guardar?") },
            text = { Text("Lo que mapeaste hasta ahora se pierde.") },
            confirmButton = { TextButton(onClick = onExit) { Text("Salir", color = Bad) } },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("Seguir mapeando") } }
        )
    }
}

@Composable
private fun BoxScope.CaptureOverlay(c: MapCaptureController, onBack: () -> Unit) {
    var showSave by remember { mutableStateOf(false) }
    var showList by remember { mutableStateOf(false) }
    val hud = c.hud

    LocationMarkers(
        items = c.projected,
        cardFor = { code -> CardData(code, "ubicación marcada", "", CardState.FREE, null, false) },
        selectedCode = null,
        guideCode = null,
        compact = true,
        dimmed = false,
        onTap = {},
        topReserved = 200.dp,
        bottomReserved = 190.dp
    )
    Reticle(Modifier.align(Alignment.Center))

    val message = c.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(4500)
            if (c.message == message) c.message = null
        }
    }

    val spinning = hud.tracking && hud.noBaselineStreak >= 4
    val hint = when {
        hud.tracking && hud.paceHint.isNotEmpty() -> hud.paceHint
        !hud.tracking -> "Mueve el teléfono despacio hacia una zona con muebles o cuadros."
        spinning -> "Estás girando sin avanzar: da pasos hacia los lados para que se formen puntos."
        hud.landmarks < MIN_RECOMMENDED_POINTS -> "Camina despacio y mira muros, muebles y techo."
        hud.landmarks < TARGET_POINTS -> "Sigue recorriendo: faltan puntos para un mapa sólido."
        else -> "Buen mapa. Marca las ubicaciones y guarda."
    }

    // Panel compacto: lo importante en una línea y el detalle (autoprueba, fotogramas) al tocarlo, para no tapar el escaneo.
    var details by remember { mutableStateOf(false) }
    Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
        Surface(
            color = PanelColor,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.clickable { details = !details }
        ) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(hud.trackingText, if (hud.tracking) Good else Warn, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onBack) { Text("Salir", maxLines = 1, softWrap = false) }
                }
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        String.format(Locale.US, "%.0f m²", hud.groundAreaM2),
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1
                    )
                    Text(" de piso cubierto", color = Muted, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                    Spacer(Modifier.weight(1f))
                    Text("${hud.landmarks} anclas", color = Muted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                }
                if (hud.depthState != "ok") {
                    Text("Profundidad: ${hud.depthState}", color = Warn, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
                }
                LinearProgressIndicator(
                    progress = { (hud.landmarks / TARGET_POINTS.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp)
                )
                Text(
                    hint,
                    color = if (hud.tracking && !spinning && hud.paceHint.isEmpty()) Color.White else Warn,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                )
                if (details) {
                    Text(
                        String.format(Locale.US, "Superficie de la malla: %.0f m² · fotogramas: %d", hud.meshAreaM2, hud.keyframes),
                        color = Muted, fontSize = 12.sp
                    )
                    Text("Autoprueba: ${hud.selfCheck}", color = Muted, fontSize = 12.sp)
                    if (hud.skipped > 0) Text("Fotogramas con pocos rasgos descartados: ${hud.skipped}", color = Muted, fontSize = 12.sp)
                    Text("Toca el panel para ocultar el detalle.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())
        ) {
            LayerChip("Superficies", SurfaceDot, c.showSurfaces, { c.enableSurfaces(!c.showSurfaces) })
            LayerChip("Anclas", AnchorDot, c.showAnchors, { c.enableAnchors(!c.showAnchors) })
            LayerChip("Planos de ARCore", Good, c.showPlanes, { c.enablePlanes(!c.showPlanes) })
            LayerChip("Oclusión (beta)", Good, c.occlusionOn, { c.enableOcclusion(!c.occlusionOn) })
            LayerChip(c.depthMode.label, Accent, true, { c.cycleDepthMode() })
            LayerChip("Mapa de prof.", Warn, c.heatmapOn, { c.enableHeatmap(!c.heatmapOn) })
        }
    }

    Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
        message?.let {
            Surface(color = PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (showList) {
            Surface(color = PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    if (c.locations.isEmpty()) {
                        Text("Aún no hay ubicaciones. Apunta la mira y toca «Marcar ubicación».", color = Muted, fontSize = 13.sp)
                    }
                    c.locations.forEach { loc ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${loc.code} · ${loc.surface}", color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { c.removeLocation(loc.code) }) { Text("Quitar", color = Bad) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick = { c.beginMark() }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Marcar ubicación aquí", fontSize = 16.sp, maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { showList = !showList }, modifier = Modifier.weight(1f)) {
                Text("Lista (${c.locations.size})", fontSize = 14.sp, maxLines = 1)
            }
            Button(onClick = { showSave = true }, enabled = hud.landmarks > 0 && !c.saving, modifier = Modifier.weight(1f)) {
                Text(if (c.saving) "Guardando…" else "Guardar mapa", fontSize = 14.sp, maxLines = 1)
            }
        }
    }

    val hit = c.pendingHit
    if (hit != null) {
        var code by remember(hit) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { c.cancelMark() },
            title = { Text("Código de la ubicación") },
            text = {
                Column {
                    Text(
                        String.format(Locale.US, "Punto medido a %.1f m (%s).", hit.distance, hit.kind.label),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.uppercase(Locale.ROOT) },
                        label = { Text("Código (A1, F1, ANDEN 3…)") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SUGGESTED_CODES.forEach { s -> AssistChip(onClick = { code = s }, label = { Text(s) }) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { c.commitMark(code) }, enabled = code.isNotBlank()) { Text("Marcar") }
            },
            dismissButton = { TextButton(onClick = { c.cancelMark() }) { Text("Cancelar") } }
        )
    }

    if (showSave) {
        var name by remember { mutableStateOf("Oficina") }
        AlertDialog(
            onDismissRequest = { showSave = false },
            title = { Text("Guardar mapa") },
            text = {
                Column {
                    Text("${hud.landmarks} puntos · ${c.locations.size} ubicaciones", fontSize = 13.sp)
                    if (hud.landmarks < MIN_RECOMMENDED_POINTS) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ojo: con tan pocos puntos casi no podrá ubicarse. Conviene seguir recorriendo antes de guardar.",
                            color = Warn, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del mapa") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { c.save(name); showSave = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancelar") } }
        )
    }
}
