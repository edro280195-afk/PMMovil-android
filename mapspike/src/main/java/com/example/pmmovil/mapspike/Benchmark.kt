package com.example.pmmovil.mapspike

import android.app.Activity
import android.os.Build
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

/**
 * Benchmark automático del mapa. Recorre fases (reposo, paneo 2D, inclinación y giro 3D,
 * vista general, actualización de fuente a 30 Hz, cambios de estilo por cuadro y consultas)
 * y mide: cuadros de la interfaz (Choreographer), cuadros de render del mapa, memoria y
 * peticiones de red. Guarda un JSON y lo imprime en logcat con la etiqueta "MapSpike".
 */
class Benchmark(
    private val activity: Activity,
    private val spike: SpikeMap,
    private val engine: String,
    private val onFinished: (JSONObject) -> Unit
) {
    companion object {
        const val TAG = "MapSpike"
    }

    /** `record = false` deja asentar el mapa (tiles y cámara) sin guardar la fase. */
    private class Phase(
        val name: String,
        val ms: Long,
        val start: () -> Unit,
        val tick: (Long) -> Unit,
        val record: Boolean = true
    )

    private val handler = Handler(Looper.getMainLooper())
    private val choreographer = Choreographer.getInstance()

    @Suppress("DEPRECATION")
    private val refreshHz: Float = activity.windowManager.defaultDisplay.refreshRate
    private val refreshMs = 1000.0 / refreshHz

    // Acumuladores de la fase en curso
    private val uiDeltas = ArrayList<Double>(8192)
    private var renderFrames = 0
    private var renderRawSum = 0.0
    private var encodeRawSum = 0.0
    private var drawCallsSum = 0L
    private var phaseExtra: JSONObject? = null

    private val phaseResults = ArrayList<JSONObject>()
    private var running = false
    private var phaseIndex = -1
    private var phaseStartMs = 0L
    private var lastFrameNs = 0L
    private var frameCounter = 0
    private var lastDotUpdate = 0L
    private var ovLat = 0.0
    private var ovLon = 0.0
    private var ovZoom = 17.0

    private val phases = ArrayList<Phase>()

    private val statsListener = MapView.OnDidFinishRenderingFrameWithStatsListener { _, stats ->
        if (running) {
            renderFrames++
            renderRawSum += stats.renderingTime
            encodeRawSum += stats.encodingTime
            drawCallsSum += stats.numDrawCalls.toLong()
        }
    }

    init {
        val route = spike.scene.route
        // El mapa dibuja los tiles nuevos y luego se detiene: "idle_steady" debe dar ~0 cuadros.
        phases += Phase("settle", 2500, {
            spike.select(null)
            spike.setDot(route[0][0], route[0][1])
            spike.camera(55.0, 30.0, 19.2, 45.0, -8.0)
        }, { }, record = false)
        phases += Phase("idle_steady", 4000, { }, { })

        phases += Phase("pan_zoom_2d", 8000, {
            spike.camera(20.0, 30.0, 19.4, 0.0, 0.0)
        }, { t ->
            val s = t / 8000.0
            spike.camera(20.0 + 70.0 * s, 30.0 + 10.0 * sin(s * 2 * PI), 19.4 + 0.9 * sin(s * 3 * PI), 0.0, 0.0)
        })

        phases += Phase("tilt_rotate_3d", 8000, {
            spike.camera(55.0, 25.0, 19.8, 58.0, 0.0)
        }, { t ->
            val s = t / 8000.0
            spike.camera(55.0 + 12.0 * sin(s * 2 * PI), 25.0 + 6.0 * cos(s * 2 * PI), 19.8, 58.0, 180.0 * s)
        })

        phases += Phase("overview_all_features", 6000, {
            spike.fitAll(0)
            val c = spike.map.cameraPosition
            c.target?.let { ovLat = it.latitude; ovLon = it.longitude }
            ovZoom = c.zoom
        }, { t ->
            val s = t / 6000.0
            spike.map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(LatLng(ovLat + 0.00025 * sin(s * 2 * PI), ovLon + 0.0004 * cos(s * 2 * PI)))
                        .zoom(ovZoom).tilt(40.0).bearing(10.0 * sin(s * 2 * PI))
                        .build()
                )
            )
        })

        phases += Phase("source_update_30hz", 6000, {
            spike.camera(55.0, 25.0, 19.6, 50.0, -10.0)
            lastDotUpdate = 0L
        }, { t ->
            if (t - lastDotUpdate >= 33) {
                lastDotUpdate = t
                val p = spike.pointAlongRoute(t * 0.012)
                spike.setDot(p[0], p[1])
            }
        })

        phases += Phase("paint_update_per_frame", 5000, {
            spike.camera(45.0, 22.0, 19.7, 52.0, 0.0)
        }, { t ->
            val zones = spike.scene.palletZones
            spike.select(zones[(frameCounter++) % zones.size])
            spike.halo(0.5 + 0.5 * sin(t / 300.0))
        })

        phases += Phase("query_stress", 2500, {
            spike.select(null)
            spike.camera(45.0, 22.0, 19.6, 50.0, 0.0)
            // Se espera a que el mapa dibuje la cámara nueva antes de consultar.
            handler.postDelayed({ runQueryStress() }, 1200)
        }, { })
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastFrameNs != 0L) uiDeltas.add((frameTimeNanos - lastFrameNs) / 1e6)
            lastFrameNs = frameTimeNanos

            val phase = phases[phaseIndex]
            val elapsed = SystemClock.elapsedRealtime() - phaseStartMs
            if (elapsed >= phase.ms) {
                endPhase(phase, elapsed)
                if (!nextPhase()) return
            } else {
                phase.tick(elapsed)
            }
            choreographer.postFrameCallback(this)
        }
    }

    fun start() {
        if (running) return
        Log.i(TAG, "BENCH_START engine=$engine replicas=${spike.replicas} refreshHz=$refreshHz")
        spike.mapView.addOnDidFinishRenderingFrameListener(statsListener)
        running = true
        phaseIndex = -1
        phaseResults.clear()
        if (nextPhase()) choreographer.postFrameCallback(frameCallback)
    }

    private fun nextPhase(): Boolean {
        phaseIndex++
        if (phaseIndex >= phases.size) {
            finish()
            return false
        }
        uiDeltas.clear()
        renderFrames = 0
        renderRawSum = 0.0
        encodeRawSum = 0.0
        drawCallsSum = 0L
        phaseExtra = null
        lastFrameNs = 0L
        phaseStartMs = SystemClock.elapsedRealtime()
        phases[phaseIndex].start()
        return true
    }

    private fun runQueryStress() {
        val w = spike.mapView.width.toFloat()
        val h = spike.mapView.height.toFloat()
        val rnd = Random(3)
        val times = DoubleArray(300)
        var hits = 0
        for (i in times.indices) {
            val x = rnd.nextFloat() * w
            val y = rnd.nextFloat() * h
            val t0 = System.nanoTime()
            hits += spike.queryUnitsAt(x, y)
            times[i] = (System.nanoTime() - t0) / 1e6
        }
        times.sort()
        phaseExtra = JSONObject()
            .put("queries", times.size)
            .put("avgMs", r(times.average()))
            .put("p95Ms", r(times[(times.size * 0.95).toInt()]))
            .put("maxMs", r(times.last()))
            .put("avgHits", r(hits.toDouble() / times.size))
    }

    private fun endPhase(phase: Phase, elapsed: Long) {
        if (!phase.record) return
        val sorted = uiDeltas.sorted()
        val n = sorted.size
        fun pct(q: Double) = if (n == 0) 0.0 else sorted[((n - 1) * q).toInt()]
        // Irregularidad relativa a la mediana de la propia fase: el sistema baja el refresco
        // en reposo y comparar contra el máximo (8.3 ms a 120 Hz) daría falsos positivos.
        val jankLimit = pct(0.5) * 1.5
        val jank = if (n == 0) 0.0 else uiDeltas.count { it > jankLimit } * 100.0 / n
        val mi = Debug.MemoryInfo()
        Debug.getMemoryInfo(mi)
        val rt = Runtime.getRuntime()

        val o = JSONObject()
            .put("name", phase.name)
            .put("seconds", r(elapsed / 1000.0))
            .put("uiFrames", n)
            .put("uiAvgMs", r(if (n == 0) 0.0 else uiDeltas.average()))
            .put("uiP95Ms", r(pct(0.95)))
            .put("uiP99Ms", r(pct(0.99)))
            .put("uiJankPct", r(jank))
            .put("renderFrames", renderFrames)
            .put("renderFps", r(renderFrames * 1000.0 / elapsed))
            .put("renderAvgRaw", if (renderFrames == 0) 0.0 else renderRawSum / renderFrames)
            .put("encodeAvgRaw", if (renderFrames == 0) 0.0 else encodeRawSum / renderFrames)
            .put("drawCallsAvg", r(if (renderFrames == 0) 0.0 else drawCallsSum.toDouble() / renderFrames))
            .put("pssMb", r(mi.totalPss / 1024.0))
            .put("graphicsMb", r((mi.getMemoryStat("summary.graphics")?.toDoubleOrNull() ?: 0.0) / 1024.0))
            .put("nativeHeapMb", r(Debug.getNativeHeapAllocatedSize() / 1048576.0))
            .put("javaHeapMb", r((rt.totalMemory() - rt.freeMemory()) / 1048576.0))
        phaseExtra?.let { o.put("extra", it) }
        phaseResults.add(o)
        Log.i(TAG, "PHASE $o")
    }

    private fun finish() {
        running = false
        spike.mapView.removeOnDidFinishRenderingFrameListener(statsListener)

        // MapLibre entrega los tiempos de render en segundos o milisegundos según la versión:
        // si el máximo es < 0.5 se interpretan como segundos.
        var maxRaw = 0.0
        for (p in phaseResults) maxRaw = maxOf(maxRaw, p.optDouble("renderAvgRaw"), p.optDouble("encodeAvgRaw"))
        val factor = if (maxRaw in 0.0..0.5) 1000.0 else 1.0
        val phasesJson = JSONArray()
        for (p in phaseResults) {
            p.put("renderAvgMs", r(p.optDouble("renderAvgRaw") * factor))
            p.put("encodeAvgMs", r(p.optDouble("encodeAvgRaw") * factor))
            phasesJson.put(p)
        }

        val s = spike.scene.stats
        val summary = JSONObject()
            .put("maplibre", "13.6.1")
            .put("engine", engine)
            .put("engineActual", org.maplibre.android.RenderingEngine.getCurrentType().name)
            .put("replicas", spike.replicas)
            .put("device", JSONObject()
                .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                .put("sdk", Build.VERSION.SDK_INT)
                .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
                .put("refreshHz", r(refreshHz.toDouble())))
            .put("scene", JSONObject()
                .put("units", s.units).put("slots", s.slots).put("zones", s.zones)
                .put("walls", s.walls).put("labels", s.labels)
                .put("geoJsonMb", r(s.geoJsonBytes / 1048576.0)))
            .put("startup", JSONObject()
                .put("sceneBuildMs", spike.sceneBuildMs)
                .put("styleLoadMs", spike.styleLoadMs)
                .put("dataSetMs", spike.dataSetMs)
                .put("firstIdleMs", spike.firstIdleMs))
            .put("network", JSONObject()
                .put("requests", Net.count.get())
                .put("urls", JSONArray(Net.urls.toList())))
            .put("phases", phasesJson)

        runCatching {
            File(activity.filesDir, "spike-$engine-x${spike.replicas}.json").writeText(summary.toString(2))
        }
        val text = summary.toString()
        var i = 0
        var part = 0
        while (i < text.length) {
            val end = minOf(text.length, i + 3500)
            Log.i(TAG, "SUMMARY[$part] ${text.substring(i, end)}")
            i = end
            part++
        }
        Log.i(TAG, "DONE parts=$part network=${Net.count.get()}")
        onFinished(summary)
    }

    private fun r(v: Double): Double = (v * 100.0).roundToLong() / 100.0
}
