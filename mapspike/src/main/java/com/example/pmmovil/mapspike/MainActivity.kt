package com.example.pmmovil.mapspike

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.maplibre.android.MapLibre
import org.maplibre.android.RenderingEngine
import org.maplibre.android.WellKnownTileServer
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Pantalla del spike. Extras del intent (para automatizar desde adb):
 *  - engine: "vulkan" (defecto) | "opengl"
 *  - stress: 1..16, copias de la bodega completa
 *  - autorun: true para correr el benchmark solo
 *  - exit: true para cerrar la app al terminar el benchmark
 */
class MainActivity : Activity() {

    private lateinit var mapView: MapView
    private lateinit var spike: SpikeMap
    private lateinit var hud: TextView
    private var benchmark: Benchmark? = null
    private var engineName = "vulkan"
    private var replicas = 1
    private var tilted = true
    private var selectedIndex = 0
    private var liveFrames = 0
    private var liveFps = 0
    private var statsOn = false
    private var benchRunning = false
    private var lastBenchLine = ""

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        engineName = (intent.getStringExtra("engine") ?: "vulkan").lowercase()
        replicas = intent.getIntExtra("stress", 1).coerceIn(1, 16)
        val engine = if (engineName == "opengl" || engineName == "gl") {
            engineName = "opengl"
            RenderingEngine.Type.OPENGL
        } else {
            engineName = "vulkan"
            RenderingEngine.Type.VULKAN
        }

        // Orden obligatorio: el cliente HTTP necesita la instancia de MapLibre ya creada.
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre, engine)
        Net.install()

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.addView(mapView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0xB3000000.toInt())
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setPadding(dp(8), dp(6), dp(8), dp(6))
        }
        root.addView(
            hud,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START)
                .apply { topMargin = dp(28); leftMargin = dp(6) }
        )

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xCCFFFFFF.toInt())
            setPadding(dp(6), dp(4), dp(6), dp(4))
        }
        bar.addView(button("2D/3D") { toggleTilt() })
        bar.addView(button("Zona") { cycleSelection() })
        bar.addView(button("Consulta") { queryCenter() })
        bar.addView(button("Stats") { toggleStats() })
        bar.addView(button("Bench") { startBenchmark() })
        root.addView(
            bar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM)
        )
        setContentView(root)

        mapView.addOnDidFinishRenderingFrameListener(MapView.OnDidFinishRenderingFrameListener { _, _, _ -> liveFrames++ })

        spike = SpikeMap(this, mapView, replicas)
        spike.start {
            spike.setTilt(58.0)
            spike.map.addOnMapClickListener(MapLibreMap.OnMapClickListener { point ->
                val p = spike.map.projection.toScreenLocation(point)
                val zone = spike.queryUnitCodeAt(p.x, p.y)
                if (zone != null) {
                    select(zone)
                    true
                } else {
                    false
                }
            })
            if (intent.getBooleanExtra("autorun", false)) waitIdleThenBench()
        }
        handler.post(hudTick)
    }

    private fun waitIdleThenBench() {
        if (spike.firstIdleMs > 0L) {
            handler.postDelayed({ startBenchmark() }, 1500)
        } else {
            handler.postDelayed({ waitIdleThenBench() }, 250)
        }
    }

    private fun startBenchmark() {
        if (benchRunning) return
        benchRunning = true
        lastBenchLine = "Benchmark en curso…"
        benchmark = Benchmark(this, spike, engineName) { summary ->
            benchRunning = false
            val phases = summary.getJSONArray("phases")
            val sb = StringBuilder("Benchmark listo\n")
            for (i in 0 until phases.length()) {
                val p = phases.getJSONObject(i)
                sb.append(p.getString("name")).append(": ").append(p.optDouble("renderFps")).append(" fps, jank ")
                    .append(p.optDouble("uiJankPct")).append("%\n")
            }
            lastBenchLine = sb.toString()
            if (intent.getBooleanExtra("exit", false)) handler.postDelayed({ finish() }, 800)
        }.also { it.start() }
    }

    private fun toggleTilt() {
        tilted = !tilted
        spike.setTilt(if (tilted) 58.0 else 0.0)
    }

    private fun cycleSelection() {
        val zones = spike.scene.palletZones
        select(zones[selectedIndex % zones.size])
        selectedIndex++
    }

    private fun select(code: String) {
        spike.select(code)
        val b = spike.scene.bounds
        // Vuelo suave hacia el bloque elegido (los centros de bloque están en la escena base).
        val blockNo = code.removePrefix("P").substringBefore('-').toIntOrNull() ?: 1
        val i = (blockNo - 1) % 3
        val j = (blockNo - 1) / 3
        val cx = 6 + i * (12 * 1.32 + 3.6) + 8.0
        val cy = 12 + j * (8 * 1.12 + 3.6) + 4.5
        if (b.isNotEmpty()) spike.fly(cx, cy, 20.3, if (tilted) 58.0 else 0.0, -8.0, 900)
    }

    private fun queryCenter() {
        val t0 = System.nanoTime()
        val n = spike.queryUnitsAt(mapView.width / 2f, mapView.height / 2f)
        val ms = (System.nanoTime() - t0) / 1e6
        Toast.makeText(this, "Consulta: $n unidades en %.2f ms".format(ms), Toast.LENGTH_SHORT).show()
    }

    private fun toggleStats() {
        statsOn = !statsOn
        spike.statsView(statsOn)
    }

    private val hudTick = object : Runnable {
        override fun run() {
            liveFps = liveFrames * 2
            liveFrames = 0
            val s = spike.scene.stats
            val mi = Debug.MemoryInfo()
            Debug.getMemoryInfo(mi)
            hud.text = buildString {
                append("MapLibre 13.6.1 · ").append(engineName.uppercase()).append(" · ").append(Build.MODEL).append('\n')
                append("unidades ").append(s.units).append(" · slots ").append(s.slots).append(" · x").append(replicas)
                append(" · GeoJSON %.1f MB".format(s.geoJsonBytes / 1048576.0)).append('\n')
                append("inicio: escena ").append(spike.sceneBuildMs).append(" ms · estilo ").append(spike.styleLoadMs)
                append(" ms · datos ").append(spike.dataSetMs).append(" ms · idle ").append(spike.firstIdleMs).append(" ms\n")
                append("red: ").append(Net.count.get()).append(" peticiones · FPS mapa ").append(liveFps)
                append(" · PSS %.0f MB".format(mi.totalPss / 1024.0))
                if (lastBenchLine.isNotEmpty()) append('\n').append(lastBenchLine)
            }
            handler.postDelayed(this, 500)
        }
    }

    // --- Utilidades de interfaz ---

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        setOnClickListener { onClick() }
    }

    // --- Ciclo de vida del MapView ---

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
}
