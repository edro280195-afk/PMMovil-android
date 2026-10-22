package com.example.pmmovil.mapeador.session

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.pmmovil.mapeador.ar.ArFrameInfo
import com.example.pmmovil.mapeador.ar.ArFrameListener
import com.example.pmmovil.mapeador.ar.AnchorLayer
import com.example.pmmovil.mapeador.ar.ArHandle
import com.example.pmmovil.mapeador.ar.MeshLayer
import com.example.pmmovil.mapeador.ar.ScanOverlay
import com.example.pmmovil.mapeador.core.CameraFrame
import com.example.pmmovil.mapeador.core.MapLocation
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.core.PointCloud
import com.example.pmmovil.mapeador.core.PoseFusion
import com.example.pmmovil.mapeador.core.ProjectedLocation
import com.example.pmmovil.mapeador.core.Projection
import com.example.pmmovil.mapeador.core.Relocalizer
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.vision.VisionKit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2

enum class LocateStatus { LOADING, NO_TRACKING, SEARCHING, LOCKED, STALE }

data class LocateHud(
    val status: LocateStatus = LocateStatus.LOADING,
    val statusText: String = "Cargando mapa…",
    val attempts: Int = 0,
    val rawOk: Int = 0,
    val accepted: Int = 0,
    val lastInliers: Int = 0,
    val lastPairs: Int = 0,
    val lastMatchMs: Long = 0,
    val lastSolveMs: Long = 0,
    val lastReason: String = "",
    val lastDeltaM: Double? = null,
    val lastDeltaDeg: Double? = null,
    val usedPrior: Boolean = false,
    val mapPoints: Int = 0
)

/** Flecha de «llévame a…»: ángulo en pantalla (0 = arriba, + = derecha) y distancia horizontal. */
class GuideState(val code: String, val angleDeg: Float, val distanceM: Float, val onScreen: Boolean, val arrived: Boolean)

/**
 * Modo «Ubicar»: carga un mapa, intenta ubicar la cámara contra él cada ~0.4 s en un hilo aparte,
 * fusiona cada fijación con el movimiento de ARCore y proyecta las ubicaciones marcadas.
 */
class LocateController(
    private val store: MapStore,
    private val mapId: String,
    private val logsDir: File
) : ArFrameListener, AutoCloseable {
    val arHandle = ArHandle()

    var hud by mutableStateOf(LocateHud())
        private set
    var mapName by mutableStateOf("")
        private set
    var locations by mutableStateOf<List<MapLocation>>(emptyList())
        private set
    var projected by mutableStateOf<List<ProjectedLocation>>(emptyList())
        private set
    var guideCode by mutableStateOf<String?>(null)
    var guide by mutableStateOf<GuideState?>(null)
        private set
    var selectedCode by mutableStateOf<String?>(null)
    var loadError by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
    val audit = mutableStateMapOf<String, AuditResult>()

    /** Capas de GL: la geometría y las anclas guardadas, ya transformadas al marco de esta sesión. */
    val overlay = ScanOverlay()

    @Volatile
    private var meshLayer: MeshLayer? = null

    @Volatile
    private var anchorLayer: AnchorLayer? = null
    var showSurfaces by mutableStateOf(true)
        private set
    var showAnchors by mutableStateOf(false)
        private set
    var occlusionOn by mutableStateOf(false)
        private set
    var heatmapOn by mutableStateOf(false)
        private set

    private val hudLock = Any()

    /** Varios hilos escriben el HUD (GL y visión): cada cambio se lee y se escribe junto, sin pisarse. */
    private fun updateHud(change: (LocateHud) -> LocateHud) {
        synchronized(hudLock) { hud = change(hud) }
    }

    fun enableSurfaces(on: Boolean) {
        showSurfaces = on
    }

    fun enableAnchors(on: Boolean) {
        showAnchors = on
    }

    fun enableOcclusion(on: Boolean) {
        occlusionOn = on
        overlay.occlusion = on
    }

    fun enableHeatmap(on: Boolean) {
        heatmapOn = on
        overlay.depthHeatmap = on
    }

    private val fusion = PoseFusion()
    private val worker = visionExecutor("mapeador-ubicar")
    private val busy = AtomicBoolean(false)
    private val visionHolder = lazy { VisionKit() }
    private var relocalizer: Relocalizer? = null
    private var lastAttemptMs = 0L
    private var attempts = 0
    private var rawOk = 0
    private var accepted = 0

    @Volatile
    private var ready = false

    @Volatile
    private var closed = false

    private val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private val relocLog = CsvLog(
        File(logsDir, "ubicar-$stamp.csv"),
        "t_ms,ok,aceptada,rasgos,pares,inliers,reproy_px,match_ms,pnp_ms,con_estimacion_previa,delta_m,delta_grados,razon"
    )
    private val auditLog = CsvLog(
        File(logsDir, "auditoria.csv"),
        "fecha,mapa,ubicacion,resultado,distancia_m,estado_ubicacion"
    )

    init {
        worker.execute { load() }
    }

    private fun load() {
        try {
            val saved = store.load(mapId)
            val vision = visionHolder.value
            relocalizer = Relocalizer(saved.landmarks, vision.matcher, vision.solver)
            mapName = saved.meta.name
            locations = saved.locations

            saved.mesh?.let { mesh ->
                meshLayer = MeshLayer(mesh).also {
                    it.alpha = 0.85f
                    it.visible = false
                    overlay.addMesh(it)
                }
            }
            val anchors = PointCloud(maxPoints = maxOf(saved.landmarks.pointCount, 1), initialCapacity = maxOf(saved.landmarks.pointCount, 1))
            for (i in 0 until saved.landmarks.pointCount) {
                val p = saved.landmarks.pointPosition(i)
                anchors.add(p.x.toFloat(), p.y.toFloat(), p.z.toFloat(), 0f, 1f, 0f, -1000f)
            }
            anchorLayer = AnchorLayer(anchors).also {
                it.visible = false
                overlay.addAnchors(it)
            }
            ready = true
            updateHud {
                it.copy(
                    status = LocateStatus.SEARCHING,
                    statusText = "Mapa cargado: ${saved.landmarks.pointCount} puntos",
                    mapPoints = saved.landmarks.pointCount
                )
            }
            Log.i(
                TAG,
                "mapa «${saved.meta.name}» cargado: ${saved.landmarks.pointCount} puntos, " +
                    "${saved.landmarks.entryCount} vistas, ${saved.locations.size} ubicaciones"
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir el mapa", e)
            loadError = "No se pudo abrir el mapa: ${e.message}"
        }
    }

    // ---------- hilo de GL ----------

    override fun onFrame(info: ArFrameInfo) {
        if (closed) return
        val nowMs = SystemClock.elapsedRealtime()
        val fix = fusion.mapFromSession
        val ageMs = nowMs - fusion.lastAcceptedAtMs
        val fresh = fix != null && ageMs < LOST_AFTER_MS

        val status = when {
            !ready -> LocateStatus.LOADING
            !info.tracking -> LocateStatus.NO_TRACKING
            fix == null -> LocateStatus.SEARCHING
            ageMs > STALE_AFTER_MS -> LocateStatus.STALE
            else -> LocateStatus.LOCKED
        }
        val text = when (status) {
            LocateStatus.LOADING -> "Cargando mapa…"
            LocateStatus.NO_TRACKING -> info.trackingReason.ifEmpty { "ARCore sin rastro: mueve el teléfono despacio" }
            LocateStatus.SEARCHING ->
                if (hud.mapPoints in 1 until TINY_MAP_POINTS) {
                    "El mapa tiene solo ${hud.mapPoints} puntos: casi no alcanza. Mapea más y vuelve a probar."
                } else {
                    "Buscando dónde estás… mira el techo, los muros y los muebles"
                }
            LocateStatus.LOCKED -> "Ubicado ✓"
            LocateStatus.STALE -> "Reconfirmando… alza la vista"
        }
        if (hud.status != status || hud.statusText != text) updateHud { it.copy(status = status, statusText = text) }

        val locked = fix != null && fresh && info.tracking
        if (locked) {
            val sessionFromMap = fix!!.inverse()
            // La geometría y las anclas guardadas viven en el marco del mapa: se llevan al de esta sesión.
            val model = sessionFromMap.toColumnMajor4()
            meshLayer?.let {
                it.model = model
                it.visible = showSurfaces
            }
            anchorLayer?.let {
                it.model = model
                it.visible = showAnchors
            }
            if (locations.isNotEmpty()) {
                projected = Projection.projectLocations(
                    locations, sessionFromMap, info.viewProjection, info.cameraPosition, info.viewportWidth, info.viewportHeight
                )
                guide = computeGuide(info, sessionFromMap)
            }
        } else {
            meshLayer?.visible = false
            anchorLayer?.visible = false
            if (projected.isNotEmpty()) projected = emptyList()
            if (guide != null) guide = null
        }

        if (ready && info.tracking && nowMs - lastAttemptMs >= ATTEMPT_INTERVAL_MS && busy.compareAndSet(false, true)) {
            val frame = info.acquireImage()
            if (frame == null) {
                busy.set(false)
            } else {
                lastAttemptMs = nowMs
                // Estimación previa de la pose de la cámara en el mapa: mapa←sesión · sesión←cámara.
                val prior = fix?.compose(frame.worldFromCamera)
                worker.execute {
                    try {
                        attempt(frame, prior)
                    } catch (e: Exception) {
                        Log.e(TAG, "Falló un intento de ubicación", e)
                    } finally {
                        busy.set(false)
                    }
                }
            }
        }
    }

    private fun computeGuide(info: ArFrameInfo, sessionFromMap: Se3): GuideState? {
        val code = guideCode ?: return null
        val loc = locations.firstOrNull { it.code == code } ?: return null
        val target = sessionFromMap.apply(loc.position)
        val distance = Projection.horizontalDistance(info.cameraPosition, target)
        val cam = Projection.toCameraSpace(info.viewMatrix, target)
        val angle = Math.toDegrees(atan2(cam.x, cam.y)).toFloat()
        val sp = Projection.toScreen(info.viewProjection, target, info.viewportWidth, info.viewportHeight)
        val onScreen = sp != null && sp.x in 0f..info.viewportWidth.toFloat() && sp.y in 0f..info.viewportHeight.toFloat()
        return GuideState(code, angle, distance.toFloat(), onScreen, distance <= ARRIVED_M)
    }

    // ---------- hilo de trabajo ----------

    private fun attempt(frame: CameraFrame, prior: Se3?) {
        val vision = visionHolder.value
        val reloc = relocalizer ?: return
        val features = vision.extractor.extract(frame.gray)
        val res = reloc.locate(features, frame.intrinsics, prior)
        attempts++

        var isAccepted = false
        var dM: Double? = null
        var dDeg: Double? = null
        val pose = res.mapFromCamera
        if (res.ok && pose != null) {
            rawOk++
            val mapFromSession = pose.compose(frame.worldFromCamera.inverse())
            val update = fusion.update(mapFromSession, res.inliers, SystemClock.elapsedRealtime())
            isAccepted = update.accepted
            if (isAccepted) accepted++
            dM = update.deltaM
            dDeg = update.deltaDeg
        }

        relocLog.append(
            String.format(
                Locale.US, "%d,%b,%b,%d,%d,%d,%.2f,%d,%d,%b,%s,%s,%s",
                SystemClock.elapsedRealtime(), res.ok, isAccepted, res.queryCount, res.pairs, res.inliers,
                res.meanReprojPx, res.matchMs, res.solveMs, res.usedPrior,
                dM?.let { String.format(Locale.US, "%.3f", it) } ?: "",
                dDeg?.let { String.format(Locale.US, "%.2f", it) } ?: "",
                csv(res.reason)
            )
        )
        Log.i(
            TAG,
            "ubicar #$attempts ok=${res.ok} acept=$isAccepted rasgos=${res.queryCount} pares=${res.pairs} inliers=${res.inliers} " +
                "match=${res.matchMs}ms pnp=${res.solveMs}ms previa=${res.usedPrior} ${res.reason}"
        )
        updateHud {
            it.copy(
                attempts = attempts,
                rawOk = rawOk,
                accepted = accepted,
                lastInliers = res.inliers,
                lastPairs = res.pairs,
                lastMatchMs = res.matchMs,
                lastSolveMs = res.solveMs,
                lastReason = res.reason,
                lastDeltaM = dM ?: it.lastDeltaM,
                lastDeltaDeg = dDeg ?: it.lastDeltaDeg,
                usedPrior = res.usedPrior
            )
        }
    }

    // ---------- acciones de la pantalla (hilo principal) ----------

    fun toggleGuide(code: String) {
        guideCode = if (guideCode == code) null else code
    }

    fun recordAudit(code: String, result: AuditResult) {
        audit[code] = result
        val distance = projected.firstOrNull { it.code == code }?.distanceM
        val entries = com.example.pmmovil.mapeador.core.DemoData.entriesFor(code)
        auditLog.append(
            String.format(
                Locale.US, "%s,%s,%s,%s,%s,%s",
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()),
                csv(mapName), csv(code), csv(result.label),
                distance?.let { String.format(Locale.US, "%.1f", it) } ?: "",
                csv(com.example.pmmovil.mapeador.core.DemoData.summary(entries))
            )
        )
        message = "«$code»: ${result.label}"
    }

    override fun close() {
        closed = true
        worker.execute {
            if (visionHolder.isInitialized()) runCatching { visionHolder.value.close() }
        }
        worker.shutdown()
    }

    private companion object {
        const val ATTEMPT_INTERVAL_MS = 400L
        const val STALE_AFTER_MS = 6_000L
        const val LOST_AFTER_MS = 30_000L
        const val ARRIVED_M = 1.5
        const val TINY_MAP_POINTS = 500
    }
}
