package com.example.pmmovil.mapeador.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.mapeador.core.MarkerFamily
import com.example.pmmovil.mapeador.session.AnclasLabController
import com.example.pmmovil.mapeador.session.MarkerReadout
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

private val SIDE_CHOICES_CM = listOf(10.0, 15.0, 20.0, 30.0, 40.0)
private val REAL_CHOICES_M = listOf(2.0, 4.0, 6.0, 8.0, 10.0, 12.0, 15.0)

/**
 * Laboratorio de anclas (L4): lee marcadores con la cámara y muestra y graba su distancia, ángulo, tamaño y precisión.
 * No guarda nada en los mapas. Ver docs/mapeador-bodega.md §12.9.
 */
@Composable
fun AnclasLabScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val controller = remember {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        AnclasLabController(File(base, "logs"))
    }
    DisposableEffect(controller) { onDispose { controller.close() } }
    BackHandler { onExit() }

    ArScreenHost(listener = controller, handle = controller.arHandle) {
        AnclasOverlay(controller, onExit)
    }
}

@Composable
private fun BoxScope.AnclasOverlay(c: AnclasLabController, onExit: () -> Unit) {
    var settings by remember { mutableStateOf(false) }

    // Contorno de cada marcador, dibujado proyectando su pose estimada: si los ejes o el orden de las esquinas estuvieran
    // mal, el recuadro NO caería sobre el marcador real. La esquina amarilla es la «arriba-izquierda» del marcador.
    Canvas(Modifier.fillMaxSize()) {
        for (q in c.quads) {
            val color = if (q.fresh) Good else Muted
            val p = q.points
            val path = Path().apply {
                moveTo(p[0], p[1])
                lineTo(p[2], p[3])
                lineTo(p[4], p[5])
                lineTo(p[6], p[7])
                close()
            }
            drawPath(path, color.copy(alpha = if (q.fresh) 0.25f else 0.10f))
            drawPath(path, color, style = Stroke(width = 3.dp.toPx()))
            drawCircle(Warn, radius = 5.dp.toPx(), center = Offset(p[0], p[1]))
        }
    }
    for (q in c.quads) {
        Surface(
            color = PanelColor,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.offset { IntOffset(q.centerX.roundToInt() - 30, q.centerY.roundToInt() - 14) }
        ) {
            Text(
                q.label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }

    val message = c.message
    LaunchedEffect(message) {
        if (message != null) {
            delay(4500)
            if (c.message == message) c.message = null
        }
    }

    val hud = c.hud
    Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(12.dp)) {
        Surface(color = PanelColor, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(hud.trackingText, if (hud.tracking) Good else Warn, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onExit) { Text("Salir", maxLines = 1, softWrap = false) }
                }
                val real = c.realMeters?.let { String.format(Locale.US, "%.2f m", it) } ?: "—"
                Text(
                    String.format(Locale.US, "%s · lado %.0f cm · distancia real %s", c.family.label, c.sideCm, real),
                    color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    if (c.readouts.isEmpty()) "Apunta a un marcador de esa familia y ese lado." else
                        String.format(Locale.US, "Lectura: %d ms · %d visible(s)", hud.detectMs, hud.seenNow),
                    color = Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        c.readouts.take(3).forEach { r ->
            ReadoutCard(r)
            Spacer(Modifier.height(6.dp))
        }
    }

    Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp)) {
        message?.let {
            Surface(color = PanelColor, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
            }
            Spacer(Modifier.height(8.dp))
        }
        if (settings) {
            SettingsPanel(c)
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = { settings = !settings }, modifier = Modifier.weight(1f)) {
                Text("Ajustes", fontSize = 14.sp, maxLines = 1)
            }
            OutlinedButton(onClick = { c.resetStats() }, modifier = Modifier.weight(1f)) {
                Text("Reiniciar", fontSize = 14.sp, maxLines = 1)
            }
            Button(onClick = { c.toggleRecording() }, modifier = Modifier.weight(1.4f).height(52.dp)) {
                Text(if (c.recording) "Detener (${c.rows})" else "Grabar", fontSize = 15.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReadoutCard(r: MarkerReadout) {
    Surface(color = PanelColor, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(r.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.padding(horizontal = 6.dp))
                Text(
                    String.format(Locale.US, "%.2f m", r.distanceM),
                    color = if (r.fresh) Good else Muted, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
            }
            Text(
                String.format(Locale.US, "z %.2f m · ángulo %.0f° · %.0f px", r.depthM, r.angleDeg, r.sizePx),
                color = Color.White, fontSize = 12.sp
            )
            val ambiguity = if (r.ambiguity.isFinite()) String.format(Locale.US, "%.1f", r.ambiguity) else "—"
            Text(
                String.format(Locale.US, "reproyección %.2f px · ambigüedad %s · n %d", r.reprojectionPx, ambiguity, r.samples),
                color = Muted, fontSize = 12.sp
            )
            val scatter = r.scatterCm?.let { String.format(Locale.US, "%.1f cm", it) } ?: "—"
            val error = r.errorVsRealCm?.let { String.format(Locale.US, "%+.1f cm", it) } ?: "—"
            Text("dispersión de la posición $scatter · error contra lo real $error", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsPanel(c: AnclasLabController) {
    var sideText by remember { mutableStateOf(String.format(Locale.US, "%.0f", c.sideCm)) }
    var realText by remember { mutableStateOf(c.realMeters?.let { String.format(Locale.US, "%.2f", it) } ?: "") }

    Surface(color = PanelColor, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Familia del marcador", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarkerFamily.entries.forEach { f ->
                    LayerChip(f.label, Accent, c.family == f, { c.family = f })
                }
            }

            Text("Lado impreso (cm): del borde negro exterior; en QR, del cuadrado de módulos sin margen", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SIDE_CHOICES_CM.forEach { v ->
                    LayerChip(String.format(Locale.US, "%.0f", v), Accent, c.sideCm == v, {
                        c.sideCm = v
                        sideText = String.format(Locale.US, "%.0f", v)
                    })
                }
            }
            NumberField("Otro lado (cm)", sideText) { text ->
                sideText = text
                parsePositive(text)?.let { c.sideCm = it }
            }

            Text("Distancia real a la cámara (m), con láser; vacío = no se sabe", color = Muted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LayerChip("—", Muted, c.realMeters == null, {
                    c.realMeters = null
                    realText = ""
                })
                REAL_CHOICES_M.forEach { v ->
                    LayerChip(String.format(Locale.US, "%.0f", v), Warn, c.realMeters == v, {
                        c.realMeters = v
                        realText = String.format(Locale.US, "%.2f", v)
                    })
                }
            }
            NumberField("Otra distancia (m)", realText) { text ->
                realText = text
                c.realMeters = parsePositive(text)
            }

            OutlinedTextField(
                value = c.tag,
                onValueChange = { c.tag = it },
                label = { Text("Etiqueta de la prueba (pasillo A, luz baja…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Número positivo con punto o coma decimal; null si está vacío o no es válido. */
private fun parsePositive(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
