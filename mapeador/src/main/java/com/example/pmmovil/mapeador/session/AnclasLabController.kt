package com.example.pmmovil.mapeador.session

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pmmovil.mapeador.ar.ArFrameInfo
import com.example.pmmovil.mapeador.ar.ArFrameListener
import com.example.pmmovil.mapeador.ar.ArHandle
import com.example.pmmovil.mapeador.core.CameraFrame
import com.example.pmmovil.mapeador.core.MarkerBook
import com.example.pmmovil.mapeador.core.MarkerCsv
import com.example.pmmovil.mapeador.core.MarkerFamily
import com.example.pmmovil.mapeador.core.MarkerGeometry
import com.example.pmmovil.mapeador.core.MarkerObservation
import com.example.pmmovil.mapeador.core.Projection
import com.example.pmmovil.mapeador.vision.MarkerDetector
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/** Intervalo mínimo entre lecturas (~10 por segundo): suficiente para medir y no calienta el teléfono. */
private const val MIN_INTERVAL_MS = 100L

/** Cuánto se sigue dibujando un marcador después de la última vez que se leyó. */
private const val HOLD_MS = 1500L

/** Un marcador leído hace menos que esto se muestra «vivo»; más viejo, atenuado. */
private const val FRESH_MS = 600L

data class AnclasHud(
    val tracking: Boolean = false,
    val trackingText: String = "Iniciando ARCore…",
    /** Lo que tardó la última lectura (detección y pose), en milisegundos. */
    val detectMs: Long = 0,
    /** Marcadores leídos en el último cuadro procesado. */
    val seenNow: Int = 0
)

/** Lo que se muestra de un marcador en el panel. */
class MarkerReadout(
    val key: String,
    val label: String,
    val distanceM: Double,
    val depthM: Double,
    val angleDeg: Double,
    val sizePx: Double,
    val reprojectionPx: Double,
    val ambiguity: Double,
    val samples: Int,
    /** Dispersión (cm) de la posición en el mundo: la precisión, sin necesitar la distancia real. */
    val scatterCm: Double?,
    /** Distancia leída menos la distancia real tecleada (cm), si hay una. */
    val errorVsRealCm: Double?,
    val fresh: Boolean
)

/** El contorno de un marcador ya proyectado a la pantalla (píxeles de la vista) para dibujarlo sobre la cámara. */
class ProjectedQuad(
    val key: String,
    val label: String,
    /** Cuatro esquinas (x, y), 8 valores. */
    val points: FloatArray,
    val centerX: Float,
    val centerY: Float,
    val fresh: Boolean
)

private class Held(val obs: MarkerObservation, val seenAtMs: Long)

/**
 * Laboratorio de anclas (L4, docs/mapeador-bodega.md §12.9): lee AprilTag, ArUco o QR de lado conocido en cada cuadro de
 * ARCore, calcula su pose con PnP y la compone con la pose de ARCore. Muestra y graba, por lectura: distancia, ángulo
 * de vista, tamaño en píxeles, error de reproyección, ambigüedad y posición en el mundo; además la dispersión de esa
 * posición (precisión) y, si se teclea la distancia real medida con láser, el error de exactitud.
 *
 * NO escribe en los mapas ni toca nada de PMMovil: solo la bitácora `logs/anclas-<fecha>.csv`.
 */
class AnclasLabController(private val logsDir: File) : ArFrameListener, AutoCloseable {
    val arHandle = ArHandle()

    var hud by mutableStateOf(AnclasHud())
        private set
    var readouts by mutableStateOf<List<MarkerReadout>>(emptyList())
        private set
    var quads by mutableStateOf<List<ProjectedQuad>>(emptyList())
        private set

    var family by mutableStateOf(MarkerFamily.APRILTAG_36H11)
    var sideCm by mutableStateOf(20.0)

    /** Distancia real de la cámara al centro del marcador, medida con láser (null = no se sabe). */
    var realMeters by mutableStateOf<Double?>(null)
    var tag by mutableStateOf("")
    var message by mutableStateOf<String?>(null)

    var recording by mutableStateOf(false)
        private set
    var rows by mutableIntStateOf(0)
        private set

    private val worker = visionExecutor("mapeador-anclas")
    private val busy = AtomicBoolean(false)
    private val detectorHolder = lazy { MarkerDetector() }
    private val book = MarkerBook()
    private val hudLock = Any()

    @Volatile
    private var latest: Map<String, Held> = emptyMap()

    @Volatile
    private var closed = false
    private var lastAttemptMs = 0L
    private var log: CsvLog? = null
    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private fun updateHud(change: (AnclasHud) -> AnclasHud) {
        synchronized(hudLock) { hud = change(hud) }
    }

    // ---------- hilo de GL ----------

    override fun onFrame(info: ArFrameInfo) {
        if (closed) return
        val text = if (info.tracking) "Rastreo de ARCore: bien" else info.trackingReason.ifEmpty { "Sin rastro" }
        if (hud.tracking != info.tracking || hud.trackingText != text) {
            updateHud { it.copy(tracking = info.tracking, trackingText = text) }
        }

        val now = SystemClock.elapsedRealtime()
        val held = latest
        if (held.isEmpty()) {
            if (quads.isNotEmpty()) quads = emptyList()
        } else {
            quads = projectQuads(held, now, info)
        }

        if (!info.tracking) return
        if (now - lastAttemptMs < MIN_INTERVAL_MS) return
        if (!busy.compareAndSet(false, true)) return
        val frame = info.acquireImage()
        if (frame == null) {
            busy.set(false)
            return
        }
        lastAttemptMs = now
        val currentFamily = family
        val sideM = sideCm / 100.0
        worker.execute {
            try {
                process(frame, currentFamily, sideM)
            } catch (e: Exception) {
                Log.e(TAG, "Falló la lectura de marcadores", e)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun projectQuads(held: Map<String, Held>, now: Long, info: ArFrameInfo): List<ProjectedQuad> {
        val out = ArrayList<ProjectedQuad>(held.size)
        for ((key, h) in held) {
            if (now - h.seenAtMs > HOLD_MS) continue
            val o = h.obs
            val corners = MarkerGeometry.worldCorners(o.worldFromMarker, o.sideM)
            val pts = FloatArray(8)
            var ok = true
            for ((i, c) in corners.withIndex()) {
                val s = Projection.toScreen(info.viewProjection, c, info.viewportWidth, info.viewportHeight)
                if (s == null) {
                    ok = false
                    break
                }
                pts[i * 2] = s.x
                pts[i * 2 + 1] = s.y
            }
            if (!ok) continue
            val cx = (pts[0] + pts[2] + pts[4] + pts[6]) / 4f
            val cy = (pts[1] + pts[3] + pts[5] + pts[7]) / 4f
            out.add(ProjectedQuad(key, o.id, pts, cx, cy, now - h.seenAtMs <= FRESH_MS))
        }
        return out
    }

    // ---------- hilo de visión ----------

    private fun process(frame: CameraFrame, fam: MarkerFamily, sideM: Double) {
        val t0 = SystemClock.elapsedRealtime()
        val detector = detectorHolder.value
        val seen = ArrayList<MarkerObservation>()
        for (raw in detector.detect(frame.gray, fam)) {
            val pose = detector.solve(raw.cornersPx, sideM, frame.intrinsics) ?: continue
            val world = MarkerGeometry.worldFromMarker(frame.worldFromCamera, pose.cvFromMarker)
            seen.add(
                MarkerObservation(
                    frame.timestampNs, fam, raw.id, sideM, raw.cornersPx, pose.cvFromMarker, world,
                    frame.worldFromCamera.t, pose.reprojectionPx, pose.ambiguity
                )
            )
        }

        val now = SystemClock.elapsedRealtime()
        val next = LinkedHashMap<String, Held>()
        for ((key, h) in latest) if (now - h.seenAtMs <= HOLD_MS) next[key] = h
        for (o in seen) {
            book.add(o)
            next[o.key] = Held(o, now)
        }
        latest = next
        publishReadouts(next, now)
        if (recording) for (o in seen) writeRow(o)
        updateHud { it.copy(detectMs = now - t0, seenNow = seen.size) }
    }

    private fun publishReadouts(held: Map<String, Held>, now: Long) {
        val real = realMeters
        readouts = held.values.sortedBy { it.obs.distanceM }.map { h ->
            val o = h.obs
            val track = book.get(o.key)
            MarkerReadout(
                key = o.key,
                label = "${o.family.label} ${o.id}",
                distanceM = o.distanceM,
                depthM = o.depthM,
                angleDeg = o.viewAngleDeg,
                sizePx = o.sizePx,
                reprojectionPx = o.reprojectionPx,
                ambiguity = o.ambiguity,
                samples = track?.samples ?: 0,
                scatterCm = track?.scatter?.rmsM()?.times(100.0),
                errorVsRealCm = real?.let { (o.distanceM - it) * 100.0 },
                fresh = now - h.seenAtMs <= FRESH_MS
            )
        }
    }

    private fun writeRow(o: MarkerObservation) {
        val l = log ?: return
        l.append(MarkerCsv.row(timeFormat.format(Date()), o, realMeters, tag))
        rows++
    }

    // ---------- acciones de la pantalla ----------

    fun toggleRecording() {
        if (recording) {
            recording = false
            message = "Grabación detenida: $rows filas en logs/anclas-$stamp.csv"
            return
        }
        if (log == null) log = CsvLog(File(logsDir, "anclas-$stamp.csv"), MarkerCsv.HEADER)
        recording = true
        message = "Grabando en logs/anclas-$stamp.csv"
    }

    /** Vuelve a empezar la estadística (dispersión y conteos) sin tocar la bitácora. */
    fun resetStats() {
        worker.execute {
            book.clear()
            latest = emptyMap()
            readouts = emptyList()
        }
        message = "Estadística reiniciada"
    }

    override fun close() {
        closed = true
        worker.execute { if (detectorHolder.isInitialized()) runCatching { detectorHolder.value.close() } }
        worker.shutdown()
    }
}
