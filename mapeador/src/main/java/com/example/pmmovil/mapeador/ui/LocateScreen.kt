package com.example.pmmovil.mapeador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
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
import com.example.pmmovil.mapeador.core.DemoData
import com.example.pmmovil.mapeador.core.DemoEntry
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.session.LocateController
import com.example.pmmovil.mapeador.session.LocateStatus
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun LocateScreen(store: MapStore, mapId: String, onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(mapId) {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        LocateController(store, mapId, File(base, "logs"))
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    BackHandler { if (controller.selectedCode != null) controller.selectedCode = null else onExit() }

    ArScreenHost(listener = controller, handle = controller.arHandle, overlay = controller.overlay) {
        LocateOverlay(controller, onBack = onExit)
    }
}

@Composable
private fun BoxScope.LocateOverlay(c: LocateController, onBack: () -> Unit) {
    var debug by remember { mutableStateOf(false) }
    val hud = c.hud
    val entriesCache = remember { HashMap<String, List<DemoEntry>>() }
    val entriesOf = { code: String -> entriesCache.getOrPut(code) { DemoData.entriesFor(code) } }

    LocationMarkers(
        items = c.projected,
        cardFor = { code -> cardDataFor(code, entriesOf(code), c.audit[code]) },
        selectedCode = c.selectedCode,
        guideCode = c.guideCode,
        compact = false,
        dimmed = hud.status == LocateStatus.STALE,
        onTap = { c.selectedCode = it }
    )

    val message = c.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            if (c.message == message) c.message = null
        }
    }

    val pillColor = when (hud.status) {
        LocateStatus.LOCKED -> Good
        LocateStatus.SEARCHING, LocateStatus.STALE -> Warn
        LocateStatus.NO_TRACKING -> Bad
        LocateStatus.LOADING -> Muted
    }

    Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
        Surface(color = PanelColor, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(hud.statusText, pillColor, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { debug = !debug }) { Text(if (debug) "Ocultar" else "Datos") }
                    TextButton(onClick = onBack) { Text("Salir") }
                }
                if (c.mapName.isNotEmpty()) Text("${c.mapName} · ${hud.mapPoints} puntos", color = Muted, fontSize = 12.sp)
                val pct = if (hud.attempts > 0) hud.rawOk * 100 / hud.attempts else 0
                Text(
                    "Intentos: ${hud.attempts} · bien: ${hud.rawOk} ($pct %) · aceptadas: ${hud.accepted}",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
                if (debug) {
                    Text(
                        "coincidencias ${hud.lastPairs} · inliers ${hud.lastInliers} · emparejar ${hud.lastMatchMs} ms · PnP ${hud.lastSolveMs} ms · " +
                            "estimación previa: ${if (hud.usedPrior) "sí" else "no"}",
                        color = Muted, fontSize = 12.sp
                    )
                    Text("último intento: ${hud.lastReason}", color = Muted, fontSize = 12.sp)
                    val dm = hud.lastDeltaM
                    val dd = hud.lastDeltaDeg
                    if (dm != null && dd != null) {
                        Text(String.format(Locale.US, "cambio de la última fijación: %.0f cm, %.1f°", dm * 100, dd), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState())
        ) {
            LayerChip("Superficies", SurfaceDot, c.showSurfaces, { c.enableSurfaces(!c.showSurfaces) })
            LayerChip("Anclas", AnchorDot, c.showAnchors, { c.enableAnchors(!c.showAnchors) })
            LayerChip("Oclusión (beta)", Good, c.occlusionOn, { c.enableOcclusion(!c.occlusionOn) })
            LayerChip("Mapa de prof.", Warn, c.heatmapOn, { c.enableHeatmap(!c.heatmapOn) })
        }
        c.loadError?.let {
            Spacer(Modifier.height(8.dp))
            Surface(color = Bad.copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp)) {
                Text(it, color = Color.White, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
            }
        }
    }

    c.guide?.let { g ->
        GuideBadge(g, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 150.dp))
    }

    Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
        message?.let {
            Surface(color = PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        val selected = c.selectedCode
        if (selected != null) {
            LocationDetailPanel(
                code = selected,
                distanceM = c.projected.firstOrNull { it.code == selected }?.distanceM,
                entries = entriesOf(selected),
                audit = c.audit[selected],
                guided = c.guideCode == selected,
                onAudit = { c.recordAudit(selected, it) },
                onGuide = { c.toggleGuide(selected) },
                onClose = { c.selectedCode = null }
            )
        } else {
            val chips = if (c.projected.isNotEmpty()) {
                c.projected.sortedBy { it.distanceM }.map { it.code to String.format(Locale.US, "%.1f m", it.distanceM) }
            } else {
                c.locations.map { it.code to "" }
            }
            LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(chips) { (code, distance) ->
                    FilterChip(
                        selected = c.guideCode == code,
                        onClick = { c.selectedCode = code },
                        label = { Text(if (distance.isEmpty()) code else "$code · $distance") }
                    )
                }
            }
        }
    }
}
