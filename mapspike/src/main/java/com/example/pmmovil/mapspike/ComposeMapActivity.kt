package com.example.pmmovil.mapspike

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.maplibre.android.MapLibre
import org.maplibre.android.RenderingEngine
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapView

/**
 * Prueba de integración con Compose (mismas versiones que :app). El mapa vive en un
 * `AndroidView`; el HUD y los botones son Compose puro encima del mapa. Acepta los mismos
 * extras que MainActivity (engine, stress, autorun, exit).
 */
class ComposeMapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        var engineName = (intent.getStringExtra("engine") ?: "vulkan").lowercase()
        val engine = if (engineName == "opengl" || engineName == "gl") {
            engineName = "opengl"
            RenderingEngine.Type.OPENGL
        } else {
            engineName = "vulkan"
            RenderingEngine.Type.VULKAN
        }
        val replicas = intent.getIntExtra("stress", 1).coerceIn(1, 16)
        val autorun = intent.getBooleanExtra("autorun", false)
        val exitAfter = intent.getBooleanExtra("exit", false)

        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre, engine)
        Net.install()

        setContent {
            SpikeScreen(engineName, replicas, autorun) { if (exitAfter) finish() }
        }
    }

    @Composable
    private fun SpikeScreen(engineName: String, replicas: Int, autorun: Boolean, onBenchDone: () -> Unit) {
        val context = LocalContext.current
        val mapView = remember { MapView(context) }
        var spike by remember { mutableStateOf<SpikeMap?>(null) }
        var hudText by remember { mutableStateOf("Cargando…") }
        var benchText by remember { mutableStateOf("") }
        var tilted by remember { mutableStateOf(true) }
        var selected by remember { mutableStateOf(0) }
        var benchRunning by remember { mutableStateOf(false) }

        fun startBench() {
            val s = spike ?: return
            if (benchRunning) return
            benchRunning = true
            benchText = "Benchmark en curso…"
            Benchmark(this@ComposeMapActivity, s, "compose-$engineName") { summary ->
                benchRunning = false
                val phases = summary.getJSONArray("phases")
                val sb = StringBuilder("Benchmark listo\n")
                for (i in 0 until phases.length()) {
                    val p = phases.getJSONObject(i)
                    sb.append(p.getString("name")).append(": ").append(p.optDouble("renderFps")).append(" fps\n")
                }
                benchText = sb.toString()
                onBenchDone()
            }.start()
        }

        MapLibreView(mapView, Modifier.fillMaxSize()) {
            val s = SpikeMap(this@ComposeMapActivity, mapView, replicas)
            s.start {
                s.setTilt(58.0)
                spike = s
            }
        }

        // Arranque automático cuando el mapa ya dibujó por primera vez.
        LaunchedEffect(spike) {
            val s = spike ?: return@LaunchedEffect
            if (autorun) {
                while (s.firstIdleMs == 0L) delay(250)
                delay(1500)
                startBench()
            }
        }

        // Refresco del HUD en Compose (recompone cada 500 ms mientras el mapa renderiza).
        LaunchedEffect(spike) {
            while (true) {
                val s = spike
                if (s != null) {
                    val st = s.scene.stats
                    hudText = "COMPOSE · MapLibre 13.6.1 · ${engineName.uppercase()}\n" +
                        "unidades ${st.units} · x$replicas · idle ${s.firstIdleMs} ms · red ${Net.count.get()}"
                }
                delay(500)
            }
        }

        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                text = hudText + if (benchText.isNotEmpty()) "\n$benchText" else "",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xB3000000))
                    .padding(6.dp)
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCCFFFFFF))
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(onClick = {
                    tilted = !tilted
                    spike?.setTilt(if (tilted) 58.0 else 0.0)
                }) { Text("2D/3D") }
                Button(onClick = {
                    val s = spike ?: return@Button
                    val zones = s.scene.palletZones
                    s.select(zones[selected % zones.size])
                    selected++
                }) { Text("Zona") }
                Button(onClick = { startBench() }) { Text("Bench") }
            }
        }
    }
}

/**
 * Componente reutilizable: aloja un MapView en Compose y reenvía el ciclo de vida.
 * `onCreated` se llama una vez, cuando el MapView ya recibió onCreate.
 */
@Composable
fun MapLibreView(mapView: MapView, modifier: Modifier = Modifier, onCreated: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var created by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> {
                    mapView.onCreate(null)
                    if (!created) {
                        created = true
                        onCreated()
                    }
                }
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
