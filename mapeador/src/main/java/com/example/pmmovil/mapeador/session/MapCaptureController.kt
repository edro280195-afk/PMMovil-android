package com.example.pmmovil.mapeador.session

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pmmovil.mapeador.ar.AnchorLayer
import com.example.pmmovil.mapeador.ar.ArFrameInfo
import com.example.pmmovil.mapeador.ar.ArFrameListener
import com.example.pmmovil.mapeador.ar.ArHandle
import com.example.pmmovil.mapeador.ar.HitInfo
import com.example.pmmovil.mapeador.ar.HitKind
import com.example.pmmovil.mapeador.ar.MeshLayer
import com.example.pmmovil.mapeador.ar.OverlayClock
import com.example.pmmovil.mapeador.ar.ScanOverlay
import com.example.pmmovil.mapeador.core.CameraFrame
import com.example.pmmovil.mapeador.core.DepthFrame
import com.example.pmmovil.mapeador.core.DepthLogWriter
import com.example.pmmovil.mapeador.core.DepthMerge
import com.example.pmmovil.mapeador.core.DepthMode
import com.example.pmmovil.mapeador.core.Features
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.KeyframePolicy
import com.example.pmmovil.mapeador.core.MapBuilder
import com.example.pmmovil.mapeador.core.MapLocation
import com.example.pmmovil.mapeador.core.MapMeta
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.core.PointCloud
import com.example.pmmovil.mapeador.core.Projection
import com.example.pmmovil.mapeador.core.ProjectedLocation
import com.example.pmmovil.mapeador.core.Relocalizer
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.TsdfMesher
import com.example.pmmovil.mapeador.vision.JpegEncoder
import com.example.pmmovil.mapeador.vision.VisionKit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class CaptureHud(
    val tracking: Boolean = false,
    val trackingText: String = "Iniciando ARCore…",
    val keyframes: Int = 0,
    /** Puntos de anclaje (los que sirven para reconocer el lugar al ubicarse). */
    val landmarks: Int = 0,
    val skipped: Int = 0,
    val lastAdded: Int = 0,
    val lastMs: Long = 0,
    val selfCheck: String = "esperando puntos…",
    /** Fotogramas clave seguidos sin ningún fotograma previo con desplazamiento suficiente (giro sin avanzar). */
    val noBaselineStreak: Int = 0,
    /** Superficie escaneada con la profundidad: cuadriláteros de la malla, piso cubierto (planta) y estado de la profundidad. */
    val surfacePoints: Int = 0,
    val groundAreaM2: Double = 0.0,
    val meshAreaM2: Double = 0.0,
    val depthState: String = "esperando profundidad…",
    /**
     * Aviso de ritmo: ARCore deja de dar profundidad cuando la persona se queda parada, gira sin avanzar o camina muy rápido
     * (medido: 16 s sin profundidad a 1.4 m/s y 70 °/s). Vacío = todo bien.
     */
    val paceHint: String = ""
)

/**
 * Modo «Mapear»: con cada cuadro decide si es fotograma clave, extrae rasgos en un hilo aparte, triangula
 * puntos de anclaje con las poses de ARCore y, por otro lado, convierte la profundidad de ARCore en una nube
 * de superficies que se dibuja sobre la cámara: se ve cómo el escaneo va cubriendo el espacio.
 * Mientras mapea corre una «autoprueba»: intenta ubicar el fotograma nuevo contra el mapa que ya existe y
 * compara con la pose real de ARCore; si los ejes estuvieran mal, el error se dispara.
 */
class MapCaptureController(private val store: MapStore, private val logsDir: File) : ArFrameListener, AutoCloseable {
    val arHandle = ArHandle()

    // ---- escaneo de superficies (TSDF + malla) y capas que se dibujan con GL ----
    private val mesher = TsdfMesher()
    private val anchorCloud = PointCloud(maxPoints = 60_000, initialCapacity = 4096)
    private val meshLayer = MeshLayer(mesher.mesh)
    private val anchorLayer = AnchorLayer(anchorCloud).also { it.visible = false }
    val overlay = ScanOverlay().also {
        it.addMesh(meshLayer)
        it.addAnchors(anchorLayer)
    }

    var hud by mutableStateOf(CaptureHud())
        private set
    var locations by mutableStateOf<List<MapLocation>>(emptyList())
        private set
    var projected by mutableStateOf<List<ProjectedLocation>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
    var pendingHit by mutableStateOf<HitInfo?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var savedMapId by mutableStateOf<String?>(null)
        private set

    var showSurfaces by mutableStateOf(true)
        private set
    /** Las anclas (amarillas) se ocultan al inicio: la atención es la geometría; se encienden con el interruptor. */
    var showAnchors by mutableStateOf(false)
        private set
    var depthMode by mutableStateOf(DepthMode.MIXED)
        private set

    /** La malla se oculta donde la profundidad actual de ARCore dice que hay una superficie real más cercana (apagada: esconde de más). */
    var occlusionOn by mutableStateOf(false)
        private set

    /** Mapa de calor de la profundidad de ARCore sobre la cámara (rojo cerca, azul lejos): para verificar la alineación. */
    var heatmapOn by mutableStateOf(false)
        private set

    private val worker = visionExecutor("mapeador-captura")
    private val geometryWorker = visionExecutor("mapeador-geometria")
    private val busy = AtomicBoolean(false)
    private val depthBusy = AtomicBoolean(false)
    private val policy = KeyframePolicy()
    private val visionHolder = lazy { VisionKit() }
    private var builder: MapBuilder? = null
    private var lastIntrinsics: Intrinsics? = null
    private var skipped = 0
    private var selfOk = 0
    private var selfTotal = 0

    private var lastDepthPose: Se3? = null
    private var lastAttemptMs = 0L

    /** Última vez que llegó una imagen de profundidad NUEVA y utilizable, y su marca de tiempo (para no integrar repetidas). */
    private var lastGoodDepthMs = SystemClock.elapsedRealtime()
    private var lastIntegratedStampNs = 0L
    private var staleLoggedAtMs = 0L
    private var lastRestartMs = 0L
    private var restarts = 0
    private var paceKind = PaceKind.OK

    // Giro y avance de la cámara entre cuadros consecutivos: con giros rápidos la profundidad sale mala, y ARCore la pierde
    // por completo si la persona se queda parada, gira sin avanzar o camina muy rápido.
    private var prevFramePose: Se3? = null
    private var prevFrameNs = 0L
    private var angularSpeedDegPerSec = 0.0
    private var turnEma = 0.0
    private var speedEma = 0.0
    private var spinSkips = 0

    // Registro de la profundidad para repetir la reconstrucción en la PC, y estadística del escaneo para el log.
    private var depthLog: DepthLogWriter? = null
    private var depthLogFailed = false
    private var geometryFrames = 0
    private var geometryMsSum = 0L
    private var lastGeometryLogAt = 0L

    @Volatile
    private var closed = false

    /** Marca de la sesión: la comparten la grabación de profundidad y la de planos para poder emparejarlas. */
    private val sessionStamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private var planeLog: java.io.BufferedWriter? = null
    private var planeLogFailed = false

    init {
        store.clearDraft()
        arHandle.planeSink = { line -> geometryWorker.execute { writePlaneLine(line) } }
    }

    /** Guarda una línea con los planos de ARCore (JSON por línea); si falla una vez, deja de intentarlo. */
    private fun writePlaneLine(line: String) {
        if (planeLogFailed) return
        try {
            val w = planeLog ?: File(logsDir, "planos-$sessionStamp.jsonl").bufferedWriter().also { planeLog = it }
            w.write(line)
            w.newLine()
            w.flush()
        } catch (e: Exception) {
            planeLogFailed = true
            Log.w(TAG, "No se pudieron registrar los planos: ${e.message}")
        }
    }

    private val hudLock = Any()

    /** Varios hilos escriben el HUD (GL, visión, geometría): cada cambio se lee y se escribe junto, sin pisarse. */
    private fun updateHud(change: (CaptureHud) -> CaptureHud) {
        synchronized(hudLock) { hud = change(hud) }
    }

    fun enableSurfaces(on: Boolean) {
        showSurfaces = on
        meshLayer.visible = on
    }

    fun enableAnchors(on: Boolean) {
        showAnchors = on
        anchorLayer.visible = on
    }

    /** Planos que detecta ARCore (piso, muros, techo): para ver si cubren los muros lisos que la profundidad no mide. */
    var showPlanes by mutableStateOf(false)
        private set

    fun enablePlanes(on: Boolean) {
        showPlanes = on
        overlay.planesVisible = on
    }

    fun enableOcclusion(on: Boolean) {
        occlusionOn = on
        overlay.occlusion = on
    }

    fun enableHeatmap(on: Boolean) {
        heatmapOn = on
        overlay.depthHeatmap = on
    }

    /** Cambia entre profundidad mixta, cruda y suave para comparar cuál cubre mejor. */
    fun cycleDepthMode() {
        depthMode = depthMode.next()
        // La suave sola no tiene dato crudo al que anclarse: se integra toda con el mismo peso (como en la v7), para comparar.
        mesher.anchoringEnabled = depthMode != DepthMode.SMOOTH
    }

    // ---------- hilo de GL ----------

    override fun onFrame(info: ArFrameInfo) {
        if (closed) return
        val text = if (info.tracking) "Rastreo de ARCore: bien" else info.trackingReason.ifEmpty { "Sin rastro" }
        if (hud.tracking != info.tracking || hud.trackingText != text) {
            if (hud.trackingText != text) Log.i(TAG, "rastreo: $text")
            updateHud { it.copy(tracking = info.tracking, trackingText = text) }
        }

        // Al mapear, el marco de la sesión ES el marco del mapa: no hace falta transformar nada.
        val items = locations
        projected = if (items.isEmpty()) emptyList() else Projection.projectLocations(
            items, null, info.viewProjection, info.cameraPosition, info.viewportWidth, info.viewportHeight
        )

        if (!info.tracking) {
            policy.onTrackingLost()
            prevFramePose = null
            // Sin rastreo no hay profundidad que esperar: al recuperarlo se le da un margen antes de avisar.
            lastGoodDepthMs = SystemClock.elapsedRealtime()
            return
        }
        if (saving) return

        trackAngularSpeed(info)
        sampleDepth(info)

        if (!policy.shouldCapture(info.worldFromCamera, info.timestampNs)) return
        if (!busy.compareAndSet(false, true)) return
        val frame = info.acquireImage()
        if (frame == null) {
            busy.set(false)
            return
        }
        policy.markCaptured(info.worldFromCamera, info.timestampNs)
        worker.execute {
            try {
                processKeyframe(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Falló un fotograma clave", e)
            } finally {
                busy.set(false)
            }
        }
    }

    /**
     * Toma una imagen de profundidad unas 6 veces por segundo y solo si la cámara se movió. Se descartan las que no sirven:
     * las repetidas (ARCore actualiza la profundidad a ~6 Hz y la misma imagen se integraría dos veces), las viejas (si ARCore
     * se atasca devuelve la última con su marca de tiempo vieja, que se pegaría en el lugar equivocado) y las vacías.
     */
    private fun sampleDepth(info: ArFrameInfo) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAttemptMs < DEPTH_INTERVAL_MS) return
        if (angularSpeedDegPerSec > MAX_SPIN_DEG_PER_SEC) {
            spinSkips++
            updatePace(now)
            return
        }
        val pose = info.worldFromCamera
        val last = lastDepthPose
        val moved = last == null || last.translationDistanceTo(pose) > 0.02 || last.rotationAngleTo(pose) > 1.5 ||
            now - lastAttemptMs > 1000L
        if (!moved) {
            updatePace(now)
            return
        }
        if (!depthBusy.compareAndSet(false, true)) return
        lastAttemptMs = now
        val depth = info.acquireDepth(depthMode)
        val usable = depth != null && depth.timestampNs != lastIntegratedStampNs && validFraction(depth) >= MIN_VALID_DEPTH
        if (!usable || depth == null) {
            depthBusy.set(false)
            updatePace(now)
            return
        }
        lastIntegratedStampNs = depth.timestampNs
        lastGoodDepthMs = now
        lastDepthPose = pose
        updatePace(now)
        geometryWorker.execute {
            try {
                integrateDepth(depth)
            } catch (e: Exception) {
                Log.e(TAG, "Falló la integración de profundidad", e)
            } finally {
                depthBusy.set(false)
            }
        }
    }

    // ---------- hilos de trabajo ----------

    private fun trackAngularSpeed(info: ArFrameInfo) {
        val prev = prevFramePose
        val dtNs = info.timestampNs - prevFrameNs
        if (prev != null && dtNs > 0L) {
            val dt = dtNs / 1e9
            angularSpeedDegPerSec = prev.rotationAngleTo(info.worldFromCamera) / dt
            // Promedios móviles (~0.3 s) para que el aviso de ritmo no parpadee con cada cuadro.
            turnEma += 0.1 * (angularSpeedDegPerSec - turnEma)
            speedEma += 0.1 * (prev.translationDistanceTo(info.worldFromCamera) / dt - speedEma)
        }
        prevFramePose = info.worldFromCamera
        prevFrameNs = info.timestampNs
    }

    /** Parte de los píxeles de la imagen de profundidad que trae dato (ya mezclada la cruda con la suave). */
    private fun validFraction(depth: DepthFrame): Double {
        var valid = 0
        for (v in depth.depthMm) if (v.toInt() != 0) valid++
        return valid.toDouble() / depth.depthMm.size.coerceAtLeast(1)
    }

    private enum class PaceKind { OK, FAST, SPINNING, STILL, NO_TEXTURE }

    /**
     * Qué decirle a la persona: ARCore deja de dar profundidad si se queda parada (sin paralaje no hay cómo), si gira sin
     * avanzar, si camina muy rápido (la imagen sale movida) o si solo ve muro liso. Se avisa antes de que ocurra cuando va
     * muy rápido, y con la causa probable cuando ya lleva más de 1.5 s sin profundidad nueva.
     */
    private fun updatePace(now: Long) {
        val stalledMs = now - lastGoodDepthMs
        val stalled = stalledMs > STALL_HINT_MS
        val kind = when {
            speedEma > FAST_MPS || turnEma > FAST_TURN_DEG_PER_SEC -> PaceKind.FAST
            stalled && speedEma < 0.12 && turnEma > 15.0 -> PaceKind.SPINNING
            stalled && speedEma < 0.08 -> PaceKind.STILL
            stalled -> PaceKind.NO_TEXTURE
            else -> PaceKind.OK
        }
        // Si lleva mucho sin profundidad nueva se intenta destrabar ARCore apagando y prendiendo su modo de profundidad (como mucho cada 15 s).
        if (stalledMs > RESTART_AFTER_MS && now - lastRestartMs > RESTART_EVERY_MS) {
            lastRestartMs = now
            restarts++
            Log.i(TAG, "profundidad: intento de reinicio #$restarts tras ${stalledMs / 1000} s sin datos nuevos")
            arHandle.restartDepth()
        }
        if (stalled && now - staleLoggedAtMs > 4000L) {
            staleLoggedAtMs = now
            Log.i(
                TAG,
                String.format(
                    Locale.US, "profundidad: SIN DATOS NUEVOS desde hace %.1f s | avance %.2f m/s, giro %.0f °/s (%s)",
                    stalledMs / 1000.0, speedEma, turnEma, kind
                )
            )
        }
        if (kind != paceKind) {
            if (paceKind != PaceKind.OK && !stalled) {
                Log.i(TAG, String.format(Locale.US, "profundidad: de vuelta (avance %.2f m/s, giro %.0f °/s)", speedEma, turnEma))
            }
            paceKind = kind
        }
        val text = when (kind) {
            PaceKind.OK -> ""
            PaceKind.FAST -> "Más despacio: si caminas o giras rápido, ARCore pierde la profundidad."
            PaceKind.SPINNING -> "Giras sin avanzar y ARCore no calcula profundidad: da pasos hacia un lado."
            PaceKind.STILL -> "Parado, ARCore no calcula profundidad: camina despacio."
            PaceKind.NO_TEXTURE -> "ARCore no está dando profundidad: sigue caminando despacio y apunta a muebles, puertas o cuadros (un muro liso no se puede medir)."
        }
        val state = if (stalled) String.format(Locale.US, "sin datos hace %d s", stalledMs / 1000) else "ok"
        val h = hud
        // La memoria llena es un aviso más grave y no se pisa; el resto se actualiza solo si cambió (como mucho una vez por segundo).
        if (h.paceHint != text || (!h.depthState.startsWith("memoria") && h.depthState != state)) {
            updateHud { it.copy(paceHint = text, depthState = if (it.depthState.startsWith("memoria")) it.depthState else state) }
        }
    }

    private fun integrateDepth(depth: DepthFrame) {
        val t0 = SystemClock.elapsedRealtime()
        val added = mesher.integrate(depth, OverlayClock.seconds())
        val ms = SystemClock.elapsedRealtime() - t0
        recordDepth(depth)

        val state = if (mesher.isFull) "memoria de superficies llena: ya no entran superficies nuevas" else "ok"
        if (added > 0 || hud.depthState != state) {
            updateHud { it.copy(surfacePoints = mesher.mesh.liveQuadCount, groundAreaM2 = mesher.groundAreaM2, meshAreaM2 = mesher.areaM2, depthState = state) }
        }
        logGeometry(depth, ms)
    }

    /** Guarda la imagen de profundidad en el registro (se abre al primer cuadro); si falla una vez, deja de intentarlo. */
    private fun recordDepth(depth: DepthFrame) {
        if (depthLogFailed) return
        var log = depthLog
        if (log == null) {
            val name = "profundidad-$sessionStamp.bin"
            val file = File(logsDir, name)
            log = runCatching { DepthLogWriter(file) }.getOrNull()
            if (log == null) {
                depthLogFailed = true
                Log.w(TAG, "No se pudo abrir el registro de profundidad en ${file.absolutePath}")
                return
            }
            depthLog = log
            Log.i(TAG, "registro de profundidad: ${file.absolutePath}")
        }
        if (log.bytesWritten > DEPTH_LOG_MAX_BYTES) return
        runCatching { log.write(depth) }.onFailure {
            depthLogFailed = true
            Log.w(TAG, "No se pudo registrar la profundidad: ${it.message}")
        }
    }

    /** Cada ~2 s deja en el log cómo va el escaneo: tamaño de la malla, tiempo por cuadro y qué tanta profundidad es confiable. */
    private fun logGeometry(depth: DepthFrame, ms: Long) {
        geometryFrames++
        geometryMsSum += ms
        val now = SystemClock.elapsedRealtime()
        if (now - lastGeometryLogAt < GEOMETRY_LOG_MS) return
        lastGeometryLogAt = now

        val n = depth.width * depth.height
        var rawOk = 0
        var fill = 0
        var empty = 0
        var sum = 0.0
        var valid = 0
        for (i in 0 until n) {
            val mm = depth.depthMm[i].toInt() and 0xFFFF
            if (mm == 0) {
                empty++
                continue
            }
            val conf = depth.confidence?.get(i)?.toInt()?.and(0xFF)
            if (conf == DepthMerge.FILL_CONFIDENCE) fill++ else rawOk++
            sum += mm
            valid++
        }
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "geometria: cuadros=%d voxeles=%d bloques=%d (%.1f MB) vertices=%d cuadrilateros=%d (quitados %d) " +
                    "planta=%.1f m2 malla=%.1f m2 integra=%d ms/cuadro (voxeles %d + libre %d [%d votos] + superficie %d) " +
                    "cruda=%d%% relleno=%d%% vacio=%d%% prof_media=%.2f m giro=%.0f°/s saltados_por_giro=%d (%s)",
                geometryFrames, mesher.voxelCount, mesher.blockCount, mesher.voxelBytes / 1e6,
                mesher.mesh.vertexCount, mesher.mesh.liveQuadCount, mesher.removedQuads, mesher.groundAreaM2, mesher.areaM2,
                if (geometryFrames > 0) geometryMsSum / geometryFrames else 0L,
                mesher.lastIntegrateNs / 1_000_000, mesher.lastCarveNs / 1_000_000, mesher.lastFreeVotes, mesher.lastEmitNs / 1_000_000,
                100 * rawOk / n, 100 * fill / n, 100 * empty / n,
                if (valid > 0) sum / valid / 1000.0 else 0.0,
                angularSpeedDegPerSec, spinSkips, depthMode.label
            )
        )
        geometryFrames = 0
        geometryMsSum = 0L
    }

    private fun processKeyframe(frame: CameraFrame) {
        val t0 = SystemClock.elapsedRealtime()
        val vision = visionHolder.value
        val b = builder ?: MapBuilder(vision.matcher).also { builder = it }

        val features = vision.extractor.extract(frame.gray)
        if (features.count < MIN_FEATURES) {
            skipped++
            updateHud { it.copy(skipped = skipped) }
            Log.i(TAG, "fotograma descartado: solo ${features.count} rasgos")
            return
        }

        val check = selfCheck(b, vision, features, frame)
        val pointsBefore = b.landmarks.pointCount
        val result = b.addKeyframe(frame.worldFromCamera, frame.intrinsics, features, frame.timestampNs)
        lastIntrinsics = frame.intrinsics
        saveJpeg(result.keyframeId, frame.gray)

        // Los puntos de anclaje nuevos se dibujan en amarillo.
        val born = OverlayClock.seconds()
        for (i in pointsBefore until b.landmarks.pointCount) {
            val p = b.landmarks.pointPosition(i)
            anchorCloud.add(p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), 0f, 1f, 0f, born)
        }

        val ms = SystemClock.elapsedRealtime() - t0
        updateHud {
            it.copy(
                keyframes = b.keyframes.size,
                landmarks = b.landmarks.pointCount,
                skipped = skipped,
                lastAdded = result.newPoints,
                lastMs = ms,
                selfCheck = check ?: it.selfCheck,
                noBaselineStreak = if (result.pairsUsed == 0) it.noBaselineStreak + 1 else 0
            )
        }
        Log.i(
            TAG,
            "kf#${result.keyframeId} rasgos=${features.count} pares=${result.pairsUsed} emp=${result.matched} " +
                "tri=${result.triangulated} puntosNuevos=${result.newPoints} sumados=${result.merged} " +
                "vistasNuevas=${result.newEntries} conflictos=${result.conflicts} " +
                "puntos=${b.landmarks.pointCount} vistas=${b.landmarks.entryCount} ${ms}ms"
        )
    }

    /** Ubica el fotograma nuevo en el mapa que ya existe y compara con la pose real de ARCore. */
    private fun selfCheck(b: MapBuilder, vision: VisionKit, f: Features, frame: CameraFrame): String? {
        if (b.landmarks.pointCount < SELF_CHECK_MIN_POINTS) return null
        if ((b.keyframes.size + 1) % SELF_CHECK_EVERY != 0) return null
        val fix = Relocalizer(b.landmarks.snapshot(), vision.matcher, vision.solver).locate(f, frame.intrinsics)
        selfTotal++
        val pose = fix.mapFromCamera
        if (!fix.ok || pose == null) {
            Log.i(TAG, "autoprueba: no ubicó (${fix.reason})")
            return "$selfOk/$selfTotal · ${fix.reason}"
        }
        val errM = pose.translationDistanceTo(frame.worldFromCamera)
        val errDeg = pose.rotationAngleTo(frame.worldFromCamera)
        if (errM <= 0.30 && errDeg <= 3.0) selfOk++
        Log.i(TAG, String.format(Locale.US, "autoprueba: error %.3f m %.2f° inliers=%d reproy=%.2f px", errM, errDeg, fix.inliers, fix.meanReprojPx))
        return String.format(Locale.US, "%d/%d bien · último %.0f cm, %.1f°", selfOk, selfTotal, errM * 100, errDeg)
    }

    private fun saveJpeg(id: Int, gray: GrayImage) {
        val dir = store.draftDir
        dir.mkdirs()
        File(dir, MapStore.keyframeFileName(id)).writeBytes(JpegEncoder.encode(gray))
    }

    // ---------- acciones de la pantalla (hilo principal) ----------

    /**
     * Paso 1 de marcar: mide AHORA el punto al que apunta la mira (antes de pedir el código, para que
     * moverse mientras se escribe no cambie el lugar). Si sale bien, `pendingHit` queda lleno.
     */
    fun beginMark() {
        if (!hud.tracking) {
            message = "ARCore no tiene rastro ahora: espera a que diga «bien» y reintenta."
            return
        }
        arHandle.hitTest { hit ->
            if (hit == null) {
                message = "No se pudo medir: apunta a una superficie con textura y reintenta."
            } else {
                pendingHit = hit
            }
        }
    }

    /** Paso 2 de marcar: le pone código al punto medido. */
    fun commitMark(code: String, description: String = "") {
        val hit = pendingHit ?: return
        val clean = code.trim().uppercase(Locale.ROOT)
        if (clean.isEmpty()) {
            message = "Escribe un código de ubicación."
            return
        }
        val loc = MapLocation(
            code = clean,
            description = description.trim(),
            position = hit.position,
            cardHeightM = if (hit.kind == HitKind.WALL) 0.2 else 1.2,
            surface = hit.kind.label,
            createdAtMs = System.currentTimeMillis()
        )
        locations = locations.filterNot { it.code == clean } + loc
        pendingHit = null
        message = String.format(Locale.US, "«%s» marcada a %.1f m (%s).", clean, hit.distance, hit.kind.label)
    }

    fun cancelMark() {
        pendingHit = null
    }

    fun removeLocation(code: String) {
        locations = locations.filterNot { it.code == code }
    }

    fun save(name: String) {
        if (saving) return
        val cleanName = name.trim().ifEmpty { "Mapa sin nombre" }
        saving = true
        worker.execute {
            try {
                val b = builder
                if (b == null || b.landmarks.pointCount == 0) {
                    message = "Todavía no hay puntos: camina un poco más antes de guardar."
                    return@execute
                }
                val id = "map-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val items = locations
                val meta = MapMeta(
                    id, cleanName, System.currentTimeMillis(), b.keyframes.size, b.landmarks.pointCount, items.size,
                    lastIntrinsics, mesher.mesh.liveQuadCount, mesher.groundAreaM2
                )
                store.save(meta, b.landmarks, items, b.keyframes, mesher.mesh, mesher.config.voxelM.toFloat())
                message = String.format(
                    Locale.US, "Mapa «%s» guardado: %d puntos de anclaje, %.0f m² de piso cubierto, %d ubicaciones.",
                    cleanName, b.landmarks.pointCount, mesher.groundAreaM2, items.size
                )
                savedMapId = id
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo guardar el mapa", e)
                message = "No se pudo guardar: ${e.message}"
            } finally {
                saving = false
            }
        }
    }

    override fun close() {
        closed = true
        worker.execute {
            if (savedMapId == null) store.draftDir.deleteRecursively()
            if (visionHolder.isInitialized()) runCatching { visionHolder.value.close() }
        }
        worker.shutdown()
        geometryWorker.execute {
            runCatching { depthLog?.close() }
            runCatching { planeLog?.close() }
        }
        geometryWorker.shutdown()
    }

    private companion object {
        const val MIN_FEATURES = 150
        const val SELF_CHECK_MIN_POINTS = 300
        const val SELF_CHECK_EVERY = 3
        const val DEPTH_INTERVAL_MS = 150L

        /** Una imagen de profundidad con menos de esta parte de píxeles con dato no se integra (ARCore a veces da una vacía). */
        const val MIN_VALID_DEPTH = 0.01

        /** Sin profundidad nueva por más de esto, se le avisa a la persona con la causa probable. */
        const val STALL_HINT_MS = 1500L

        /** Pasado este tiempo sin profundidad nueva se reinicia el modo de profundidad de ARCore (y no más de una vez cada 15 s). */
        const val RESTART_AFTER_MS = 4000L
        const val RESTART_EVERY_MS = 15_000L

        /** Desde aquí (medido: ARCore perdió la profundidad por 16 s a 1.4 m/s y ~70 °/s; con 0.55 m/s y 28 °/s iba bien). */
        const val FAST_MPS = 0.95
        const val FAST_TURN_DEG_PER_SEC = 65.0

        /** Con giros más rápidos que esto la imagen sale movida y la profundidad no sirve para integrar. */
        const val MAX_SPIN_DEG_PER_SEC = 90.0
        const val GEOMETRY_LOG_MS = 2000L
        const val DEPTH_LOG_MAX_BYTES = 150_000_000L
    }
}
