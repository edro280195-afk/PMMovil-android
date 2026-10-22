package com.example.pmmovil.mapeador.ar

import android.app.Activity
import android.content.Context
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import com.example.pmmovil.mapeador.core.CameraFrame
import com.example.pmmovil.mapeador.core.DepthFrame
import com.example.pmmovil.mapeador.core.DepthMerge
import com.example.pmmovil.mapeador.core.DepthMode
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.PoseHistory
import com.example.pmmovil.mapeador.core.Projection
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.Vec3
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Camera
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.EnumSet
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

private const val TAG = "MAPEADOR"

/**
 * Visor de cámara con ARCore: dibuja el fondo, entrega cada cuadro (pose, matrices y, a demanda,
 * la imagen de CPU) al [ArFrameListener] y atiende mediciones con la mira central.
 * Las tarjetas se dibujan aparte con Compose, encima de esta vista.
 */
class ArView(context: Context) : GLSurfaceView(context) {
    private val renderer = ArRenderer(context.applicationContext)
    private var arSession: Session? = null
    private var installRequested = false
    private var surfaceResumed = false

    var frameListener: ArFrameListener?
        get() = renderer.listener
        set(value) {
            renderer.listener = value
        }

    /** Capa que se dibuja con GL encima de la cámara (nubes de puntos). */
    var overlay: GlOverlay?
        get() = renderer.overlay
        set(value) {
            renderer.overlay = value
        }

    init {
        // ES 3: la malla usa índices de 32 bits. Los sombreadores siguen siendo de la versión 100 y funcionan igual.
        setEGLContextClientVersion(3)
        // Profundidad de 24 bits: la malla escaneada se dibuja con prueba de profundidad para que lo cercano tape a lo lejano.
        setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun requestHitTest(callback: (HitInfo?) -> Unit) = renderer.requestHitTest(callback)

    /** Ver [ArHandle.planeSink]. */
    var planeSink: ((String) -> Unit)?
        get() = renderer.planeSink
        set(value) {
            renderer.planeSink = value
        }

    /**
     * Apaga la profundidad de ARCore y, unos cuadros después, la vuelve a prender. Se pide cuando lleva varios segundos sin dar
     * imágenes nuevas (en las grabaciones se atasca hasta 18 s, aun con la persona caminando despacio): a ver si así se destraba.
     */
    fun restartDepth() = renderer.restartDepth()

    /** Crea o reanuda la sesión. Distingue «falta instalar ARCore» de «equipo no compatible». */
    fun resumeAr(activity: Activity): ArStartResult {
        if (surfaceResumed) return ArStartResult.STARTED
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return ArStartResult.INSTALLING
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            if (arSession == null) {
                val session = Session(activity)
                selectCameraConfig(session)
                val config = Config(session)
                config.focusMode = Config.FocusMode.AUTO
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                config.lightEstimationMode = Config.LightEstimationMode.DISABLED
                if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
                session.configure(config)
                arSession = session
                renderer.attachSession(session)
            }
            arSession?.resume()
            super.onResume()
            surfaceResumed = true
            return ArStartResult.STARTED
        } catch (e: Exception) {
            closeSession()
            val message = when (e) {
                is UnavailableArcoreNotInstalledException -> "Falta instalar Google Play Services for AR."
                is UnavailableApkTooOldException -> "Google Play Services for AR necesita actualizarse."
                is UnavailableDeviceNotCompatibleException -> "Este equipo no es compatible con ARCore."
                else -> "No se pudo iniciar ARCore (${e.javaClass.simpleName})."
            }
            Log.e(TAG, message, e)
            unavailableMessage = message
            return ArStartResult.UNAVAILABLE
        }
    }

    /** Motivo de la última falla al iniciar ARCore (para mostrarlo en pantalla). */
    var unavailableMessage: String? = null
        private set

    fun pauseAr() {
        runCatching { arSession?.pause() }
        if (surfaceResumed) {
            super.onPause()
            surfaceResumed = false
        }
    }

    fun close() {
        pauseAr()
        closeSession()
    }

    private fun closeSession() {
        runCatching { arSession?.close() }
        arSession = null
        renderer.attachSession(null)
    }

    /**
     * Una imagen de CPU más grande da más rasgos, pero cuesta más. Se prefiere la mayor entre
     * 800 y 1280 píxeles de ancho; si no hay, se queda la configuración por omisión de ARCore.
     */
    private fun selectCameraConfig(session: Session) {
        val filter = CameraConfigFilter(session).setTargetFps(EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30))
        val configs = session.getSupportedCameraConfigs(filter)
        configs.forEach {
            Log.i(TAG, "CameraConfig: imagen=${it.imageSize} textura=${it.textureSize} fps=${it.fpsRange}")
        }
        val best = configs
            .filter { it.imageSize.width in 800..1280 }
            .maxByOrNull { it.imageSize.width * it.imageSize.height }
        if (best != null) session.cameraConfig = best
        Log.i(TAG, "CameraConfig elegida: imagen=${session.cameraConfig.imageSize} textura=${session.cameraConfig.textureSize}")
    }
}

private class ArRenderer(private val appContext: Context) : GLSurfaceView.Renderer {
    @Volatile
    var listener: ArFrameListener? = null

    @Volatile
    var overlay: GlOverlay? = null
    private var initializedOverlay: GlOverlay? = null
    private val poseHistory = PoseHistory()
    private var loggedDepth = false
    private var lastErrorLogAt = 0L

    @Volatile
    private var session: Session? = null
    private var textureConfiguredFor: Session? = null
    private var cameraTextureId = 0
    private var cameraProgram = 0
    private var positionLocation = -1
    private var texCoordLocation = -1
    private var samplerLocation = -1
    private var viewportWidth = 1
    private var viewportHeight = 1

    @Volatile
    private var geometryDirty = true
    private var lastRotation = -1
    private val mainHandler = Handler(Looper.getMainLooper())
    private val hitRequests = ConcurrentLinkedQueue<(HitInfo?) -> Unit>()

    private val quadPositions: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val quadNdc: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val quadTexCoords: FloatBuffer = floatBuffer(FloatArray(8))
    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)

    // Profundidad actual como textura (RG8, milímetros en dos bytes) para ocultar la malla detrás de superficies reales.
    private var depthTextureId = 0
    private var depthStaging: ByteBuffer = ByteBuffer.allocateDirect(160 * 90 * 2).order(ByteOrder.nativeOrder())
    private val uvIn: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f))
    private val uvOut: FloatBuffer = floatBuffer(FloatArray(6))
    private var lastOcclusionLogAt = 0L

    // Planos que detecta ARCore (piso, muros, techo): se arman a ~4 Hz y se dibujan aparte de la malla.
    private var planeCache: List<PlaneGeometry> = emptyList()
    private var lastPlaneUpdateMs = 0L
    private var lastPlaneLogMs = 0L
    private var lastPlaneSinkMs = 0L

    @Volatile
    var planeSink: ((String) -> Unit)? = null
    private var lastLagLogMs = 0L

    fun attachSession(newSession: Session?) {
        session = newSession
        textureConfiguredFor = null
    }

    fun requestHitTest(callback: (HitInfo?) -> Unit) {
        hitRequests.add(callback)
    }

    private val sessionActions = ConcurrentLinkedQueue<(Session) -> Unit>()
    private var depthRestartFramesLeft = 0

    fun restartDepth() {
        sessionActions.add { s ->
            val config = s.config
            if (config.depthMode != Config.DepthMode.DISABLED) {
                config.depthMode = Config.DepthMode.DISABLED
                s.configure(config)
                depthRestartFramesLeft = DEPTH_RESTART_FRAMES
                Log.i(TAG, "profundidad: se apagó el modo de profundidad para reiniciarlo")
            }
        }
    }

    /** Acciones sobre la sesión que se piden desde otros hilos: solo se ejecutan aquí, entre un cuadro y otro. */
    private fun runSessionActions(s: Session) {
        while (true) {
            val action = sessionActions.poll() ?: break
            runCatching { action(s) }.onFailure { Log.w(TAG, "Acción sobre la sesión de ARCore falló: ${it.message}") }
        }
        if (depthRestartFramesLeft > 0 && --depthRestartFramesLeft == 0) {
            runCatching {
                val config = s.config
                if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) config.depthMode = Config.DepthMode.AUTOMATIC
                s.configure(config)
                Log.i(TAG, "profundidad: modo automático reactivado")
            }.onFailure { Log.w(TAG, "No se pudo reactivar la profundidad: ${it.message}") }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        cameraTextureId = createExternalTexture()
        cameraProgram = createCameraProgram()
        positionLocation = GLES20.glGetAttribLocation(cameraProgram, "a_Position")
        texCoordLocation = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord")
        samplerLocation = GLES20.glGetUniformLocation(cameraProgram, "sTexture")
        depthTextureId = createDepthTexture()
        textureConfiguredFor = null
        // El contexto de GL es nuevo: la capa debe volver a crear lo que vivía en la GPU.
        initializedOverlay = null
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        geometryDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        try {
            if (textureConfiguredFor !== s) {
                s.setCameraTextureNames(intArrayOf(cameraTextureId))
                textureConfiguredFor = s
            }
            updateDisplayGeometry(s)
            runSessionActions(s)
            val frame = s.update()
            drawCamera(frame)
            publish(frame)
        } catch (e: Exception) {
            // ARCore puede omitir cuadros al reanudar o al cambiar la superficie; se anota (como mucho una vez
            // por segundo) para que un fallo de dibujo no pase en silencio.
            val now = System.currentTimeMillis()
            if (now - lastErrorLogAt > 1000L) {
                lastErrorLogAt = now
                Log.w(TAG, "Cuadro omitido: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updateDisplayGeometry(s: Session) {
        val rotation = (appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        if (geometryDirty || rotation != lastRotation) {
            s.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            lastRotation = rotation
            geometryDirty = false
        }
    }

    private fun drawCamera(frame: Frame) {
        quadNdc.position(0)
        quadTexCoords.position(0)
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadNdc,
            Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords
        )
        quadTexCoords.position(0)
        quadPositions.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(samplerLocation, 0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, quadPositions)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(texCoordLocation)
    }

    private fun publish(frame: Frame) {
        val camera = frame.camera
        val tracking = camera.trackingState == TrackingState.TRACKING
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.05f, 100f)
        val view = viewMatrix.copyOf()
        val viewProjection = Projection.multiply(projMatrix, view)
        val worldFromCamera = camera.pose.toSe3()
        poseHistory.add(frame.timestamp, worldFromCamera)

        listener?.onFrame(
            ArFrameInfo(
                timestampNs = frame.timestamp,
                tracking = tracking,
                trackingReason = reasonText(camera),
                worldFromCamera = worldFromCamera,
                cameraPosition = worldFromCamera.t,
                viewMatrix = view,
                viewProjection = viewProjection,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
                imageProvider = { acquireGray(frame, camera, worldFromCamera) },
                depthProvider = { mode -> acquireDepth(frame, camera, worldFromCamera, mode) }
            )
        )

        // Las nubes de puntos se dibujan después del fondo y de que el controlador actualizó sus transformaciones.
        val layer = overlay
        if (layer != null && tracking) {
            if (initializedOverlay !== layer) {
                layer.onSurfaceCreated()
                initializedOverlay = layer
            }
            val focalPx = projMatrix[5] * viewportHeight * 0.5f
            val depthView = runCatching { updateDepthView(frame) }.getOrNull()
            runCatching { updatePlanes(frame.timestamp) }
            layer.draw(
                OverlayFrame(
                    viewportWidth, viewportHeight, viewProjection, worldFromCamera.t, focalPx, OverlayClock.seconds(), depthView,
                    planeCache
                )
            )
        }

        if (!hitRequests.isEmpty()) {
            val result = computeHit(frame)
            while (true) {
                val callback = hitRequests.poll() ?: break
                mainHandler.post { callback(result) }
            }
        }
    }

    /**
     * Arma, cada 250 ms, la geometría de los planos que ARCore tiene detectados (sin los absorbidos por otro más grande) y
     * cada 2 s deja en el log cuántos hay: es la medida de si ARCore ve los muros lisos que la profundidad no mide.
     */
    private fun updatePlanes(frameTimestampNs: Long) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPlaneUpdateMs < PLANE_UPDATE_MS) return
        lastPlaneUpdateMs = now
        val s = session ?: return
        val list = ArrayList<PlaneGeometry>()
        var floors = 0
        var walls = 0
        var ceilings = 0
        var area = 0.0
        val point = FloatArray(3)
        val sink = planeSink
        val emit = sink != null && now - lastPlaneSinkMs >= PLANE_SINK_MS
        val json = if (emit) StringBuilder("{\"t\":").append(frameTimestampNs).append(",\"planos\":[") else null
        for (plane in s.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) continue
            val polygon = plane.polygon
            val n = polygon.remaining() / 2
            if (n < 3 || n > MAX_PLANE_POLYGON) continue
            val xz = FloatArray(n * 2)
            polygon.get(xz)
            val kind = when (plane.type) {
                Plane.Type.HORIZONTAL_UPWARD_FACING -> HitKind.FLOOR
                Plane.Type.HORIZONTAL_DOWNWARD_FACING -> HitKind.CEILING
                else -> HitKind.WALL
            }
            when (kind) {
                HitKind.FLOOR -> floors++
                HitKind.CEILING -> ceilings++
                else -> walls++
            }
            area += plane.extentX * plane.extentZ
            val center = plane.centerPose
            val out = FloatArray(n * 3 * PlaneGeometry.FLOATS_PER_VERTEX)
            var o = 0
            fun put(lx: Float, lz: Float) {
                point[0] = lx
                point[1] = 0f
                point[2] = lz
                val w = center.transformPoint(point)
                out[o++] = w[0]
                out[o++] = w[1]
                out[o++] = w[2]
                out[o++] = lx
                out[o++] = lz
            }
            // Abanico desde el centro del plano: el polígono de ARCore es convexo.
            for (i in 0 until n) {
                val j = (i + 1) % n
                put(0f, 0f)
                put(xz[i * 2], xz[i * 2 + 1])
                put(xz[j * 2], xz[j * 2 + 1])
            }
            list.add(PlaneGeometry(plane.hashCode(), kind, out))
            if (json != null) {
                if (json[json.length - 1] != '[') json.append(',')
                json.append("{\"id\":").append(plane.hashCode()).append(",\"k\":\"").append(kind.name).append("\",\"q\":[")
                    .append(center.qx()).append(',').append(center.qy()).append(',').append(center.qz()).append(',').append(center.qw())
                    .append("],\"p\":[").append(center.tx()).append(',').append(center.ty()).append(',').append(center.tz())
                    .append("],\"ex\":").append(plane.extentX).append(",\"ez\":").append(plane.extentZ).append(",\"poly\":[")
                for (i in xz.indices) {
                    if (i > 0) json.append(',')
                    json.append(xz[i])
                }
                json.append("]}")
            }
            if (list.size >= MAX_PLANES) break
        }
        planeCache = list
        if (json != null && sink != null) {
            lastPlaneSinkMs = now
            json.append("]}")
            sink(json.toString())
        }
        if (now - lastPlaneLogMs > PLANE_LOG_MS) {
            lastPlaneLogMs = now
            Log.i(
                TAG,
                String.format(
                    Locale.US, "planos de ARCore: %d (piso/mesa %d, muros %d, techo %d), extensión suma %.0f m² | estado térmico: %s",
                    list.size, floors, walls, ceilings, area, thermalText()
                )
            )
        }
    }

    /** Estado térmico del teléfono: si ARCore pierde la profundidad por calor, aquí se ve. */
    private fun thermalText(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "?"
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return "?"
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "leve"
            PowerManager.THERMAL_STATUS_MODERATE -> "moderado"
            PowerManager.THERMAL_STATUS_SEVERE -> "fuerte"
            PowerManager.THERMAL_STATUS_CRITICAL -> "crítico"
            else -> "grave"
        }
    }

    private fun createDepthTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    /**
     * Sube la profundidad de este cuadro a la textura y calcula cómo pasar de coordenadas de pantalla (NDC) a las de la
     * imagen de profundidad (que tiene la orientación de la imagen de CPU). Devuelve null mientras ARCore no la da.
     */
    private fun updateDepthView(frame: Frame): DepthView? {
        if (depthTextureId == 0) return null
        val image = runCatching { frame.acquireDepthImage16Bits() }.getOrNull() ?: return null
        try {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val src = plane.buffer
            val rowBytes = w * 2
            val need = rowBytes * h
            if (depthStaging.capacity() < need) depthStaging = ByteBuffer.allocateDirect(need).order(ByteOrder.nativeOrder())
            depthStaging.clear()
            for (y in 0 until h) {
                src.limit(src.capacity())
                src.position(y * plane.rowStride)
                src.limit(y * plane.rowStride + rowBytes)
                depthStaging.put(src)
            }
            depthStaging.flip()

            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTextureId)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)
            GLES30.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES30.GL_RG8, w, h, 0, GLES30.GL_RG, GLES20.GL_UNSIGNED_BYTE, depthStaging
            )
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

            uvIn.position(0)
            uvOut.position(0)
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, uvIn, Coordinates2d.IMAGE_NORMALIZED, uvOut
            )
            val o0s = uvOut.get(0)
            val o0t = uvOut.get(1)
            val view = DepthView(
                depthTextureId, o0s, o0t,
                (uvOut.get(2) - o0s) / 2f, (uvOut.get(3) - o0t) / 2f,
                (uvOut.get(4) - o0s) / 2f, (uvOut.get(5) - o0t) / 2f
            )
            logOcclusionCheck(frame, view, w, h)
            return view
        } finally {
            image.close()
        }
    }

    /**
     * Cada ~2 s anota la profundidad en el centro de la pantalla (con la misma transformación que usa el sombreador)
     * junto a la distancia que mide el hit-test: si coinciden, la textura y la transformación están bien alineadas.
     */
    private fun logOcclusionCheck(frame: Frame, view: DepthView, w: Int, h: Int) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOcclusionLogAt < 2000L) return
        lastOcclusionLogAt = now
        val s = view.originS + view.dxS + view.dyS
        val t = view.originT + view.dxT + view.dyT
        val px = (s * w).toInt().coerceIn(0, w - 1)
        val py = (t * h).toInt().coerceIn(0, h - 1)
        val i = (py * w + px) * 2
        val mm = (depthStaging.get(i).toInt() and 0xFF) or ((depthStaging.get(i + 1).toInt() and 0xFF) shl 8)
        val hit = runCatching { frame.hitTest(viewportWidth / 2f, viewportHeight / 2f) }.getOrNull()?.firstOrNull()
        val hitM = hit?.distance
        val verdict = when {
            mm <= 0 || hitM == null -> "sin comparar"
            mm / 1000.0 / hitM in 0.75..1.33 -> "coincide"
            else -> "NO coincide"
        }
        Log.i(
            TAG,
            String.format(
                Locale.US, "oclusion: centro prof=%.2f m hit=%s uv=(%.2f, %.2f) %s",
                mm / 1000.0, if (hitM != null) String.format(Locale.US, "%.2f m", hitM) else "—", s, t, verdict
            )
        )
    }

    private fun acquireGray(frame: Frame, camera: Camera, worldFromCamera: Se3): CameraFrame? {
        val image = try {
            frame.acquireCameraImage()
        } catch (_: NotYetAvailableException) {
            return null
        } catch (_: Exception) {
            return null
        }
        try {
            val w = image.width
            val h = image.height
            val plane = image.planes[0]
            val rowStride = plane.rowStride
            val buffer = plane.buffer
            val out = ByteArray(w * h)
            if (rowStride == w) {
                buffer.rewind()
                buffer.get(out, 0, w * h)
            } else {
                for (y in 0 until h) {
                    buffer.position(y * rowStride)
                    buffer.get(out, y * w, w)
                }
            }
            val intrinsics = camera.imageIntrinsics
            val focal = intrinsics.focalLength
            val principal = intrinsics.principalPoint
            return CameraFrame(
                timestampNs = frame.timestamp,
                worldFromCamera = worldFromCamera,
                intrinsics = Intrinsics(focal[0].toDouble(), focal[1].toDouble(), principal[0].toDouble(), principal[1].toDouble(), w, h),
                gray = GrayImage(w, h, out)
            )
        } finally {
            image.close()
        }
    }

    /**
     * Copia la imagen de profundidad de ARCore. La profundidad llega unos cuadros después de la imagen, así
     * que se busca dónde estaba la cámara en la marca de tiempo de la propia profundidad. Los intrínsecos de
     * la imagen de CPU se escalan al tamaño de la profundidad (tienen el mismo campo de visión).
     */
    private fun acquireDepth(frame: Frame, camera: Camera, poseNow: Se3, mode: DepthMode): DepthFrame? {
        var rawImage: Image? = null
        var confidenceImage: Image? = null
        var smoothImage: Image? = null
        try {
            // Siempre se piden las tres: el modo solo decide cuál se integra, y el registro las guarda completas.
            rawImage = runCatching { frame.acquireRawDepthImage16Bits() }.getOrNull()
            if (rawImage != null) confidenceImage = runCatching { frame.acquireRawDepthConfidenceImage() }.getOrNull()
            smoothImage = runCatching { frame.acquireDepthImage16Bits() }.getOrNull()
            // Si ARCore se atasca devuelve la última imagen con su marca de tiempo vieja: integrarla la pegaría donde está la
            // cámara AHORA (la pose de esa marca ya salió del historial) y dejaría geometría flotando en el lugar equivocado.
            // Cada imagen se revisa por separado: la cruda puede seguir viva cuando la suave ya se atascó.
            fun fresh(image: Image?): Boolean = image != null && (image.timestamp <= 0L || frame.timestamp - image.timestamp <= STALE_DEPTH_NS)
            val rawFresh = fresh(rawImage)
            val smoothFresh = fresh(smoothImage)
            val nowMs = SystemClock.elapsedRealtime()
            if (nowMs - lastLagLogMs > LAG_LOG_MS) {
                lastLagLogMs = nowMs
                Log.i(
                    TAG,
                    String.format(
                        Locale.US, "profundidad: retraso cruda %s ms, suave %s ms (viva: cruda %s, suave %s)",
                        rawImage?.let { ((frame.timestamp - it.timestamp) / 1_000_000L).toString() } ?: "—",
                        smoothImage?.let { ((frame.timestamp - it.timestamp) / 1_000_000L).toString() } ?: "—", rawFresh, smoothFresh
                    )
                )
            }
            val base = (if (rawFresh) rawImage else null) ?: (if (smoothFresh) smoothImage else null) ?: return null
            val stamp = base.timestamp

            val w = base.width
            val h = base.height
            val raw = if (rawFresh) rawImage?.let { readDepth16(it, w, h) } else null
            val smooth = if (smoothFresh) smoothImage?.let { if (it.width == w && it.height == h) readDepth16(it, w, h) else null } else null
            val rawConfidence = if (rawFresh) confidenceImage?.let { if (it.width == w && it.height == h) readBytes(it, w, h) else null } else null

            val merged = DepthMerge.merge(mode, raw, rawConfidence, smooth) ?: return null
            val depth = merged.depthMm
            val confidence = merged.confidence

            val intrinsics = camera.imageIntrinsics
            val dims = intrinsics.imageDimensions
            val sx = w.toDouble() / dims[0]
            val sy = h.toDouble() / dims[1]
            val focal = intrinsics.focalLength
            val principal = intrinsics.principalPoint
            val scaled = Intrinsics(focal[0] * sx, focal[1] * sy, principal[0] * sx, principal[1] * sy, w, h)

            val pose = (if (stamp > 0L) poseHistory.poseAt(stamp, POSE_SLACK_NS) else null) ?: poseNow
            if (!loggedDepth) {
                loggedDepth = true
                Log.i(
                    TAG,
                    "profundidad: ${w}x$h (${mode.label}), imagen de CPU ${dims[0]}x${dims[1]}, " +
                        "marca ${stamp} contra cuadro ${frame.timestamp}, confianza=${confidence != null}"
                )
            }
            return DepthFrame(
                w, h, depth, confidence, scaled, pose, stamp,
                rawMm = raw, rawConfidence = rawConfidence, smoothMm = smooth
            )
        } catch (_: Exception) {
            // Todavía no hay profundidad (ARCore necesita un poco de movimiento) o se agotaron los recursos.
            return null
        } finally {
            rawImage?.close()
            confidenceImage?.close()
            smoothImage?.close()
        }
    }

    private fun readDepth16(image: Image, w: Int, h: Int): ShortArray {
        val plane = image.planes[0]
        val buffer = plane.buffer.order(ByteOrder.nativeOrder())
        val out = ShortArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * plane.rowStride
            for (x in 0 until w) out[y * w + x] = buffer.getShort(rowOffset + x * plane.pixelStride)
        }
        return out
    }

    private fun readBytes(image: Image, w: Int, h: Int): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * plane.rowStride
            for (x in 0 until w) out[y * w + x] = buffer.get(rowOffset + x * plane.pixelStride)
        }
        return out
    }

    private fun computeHit(frame: Frame): HitInfo? {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return null
        val hits = runCatching { frame.hitTest(viewportWidth / 2f, viewportHeight / 2f) }.getOrDefault(emptyList())
        val tracked = hits.filter { it.trackable.trackingState == TrackingState.TRACKING }
        val hit = tracked.firstOrNull { val t = it.trackable; t is Plane && t.isPoseInPolygon(it.hitPose) }
            ?: tracked.firstOrNull { it.trackable is DepthPoint }
            ?: tracked.firstOrNull()
        if (hit != null) {
            val kind = when (val t = hit.trackable) {
                is Plane -> when (t.type) {
                    Plane.Type.VERTICAL -> HitKind.WALL
                    Plane.Type.HORIZONTAL_DOWNWARD_FACING -> HitKind.CEILING
                    else -> HitKind.FLOOR
                }
                is DepthPoint -> HitKind.DEPTH
                else -> HitKind.POINT
            }
            val p = hit.hitPose
            return HitInfo(Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble()), kind, hit.distance.toDouble())
        }
        // Sin superficie medida: un punto a 1.5 m sobre el rayo central.
        val ahead = camera.displayOrientedPose.transformPoint(floatArrayOf(0f, 0f, -1.5f))
        return HitInfo(Vec3(ahead[0].toDouble(), ahead[1].toDouble(), ahead[2].toDouble()), HitKind.RAY, 1.5)
    }

    private fun reasonText(camera: Camera): String = when (camera.trackingFailureReason) {
        TrackingFailureReason.NONE -> if (camera.trackingState == TrackingState.TRACKING) "" else "Buscando rastro…"
        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Poca luz"
        TrackingFailureReason.EXCESSIVE_MOTION -> "Te mueves muy rápido"
        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Poca textura: mira muebles o cuadros"
        TrackingFailureReason.BAD_STATE -> "ARCore en mal estado"
        TrackingFailureReason.CAMERA_UNAVAILABLE -> "Cámara no disponible"
    }

    private fun Pose.toSe3(): Se3 = Se3.fromQuaternion(
        qx().toDouble(), qy().toDouble(), qz().toDouble(), qw().toDouble(),
        Vec3(tx().toDouble(), ty().toDouble(), tz().toDouble())
    )

    private fun createExternalTexture(): Int {
        val names = IntArray(1)
        GLES20.glGenTextures(1, names, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, names[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return names[0]
    }

    private fun createCameraProgram(): Int {
        val vertex = compileShader(
            GLES20.GL_VERTEX_SHADER,
            """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
            """.trimIndent()
        )
        val fragment = compileShader(
            GLES20.GL_FRAGMENT_SHADER,
            """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES sTexture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
            """.trimIndent()
        )
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "No se pudo preparar el visor de cámara." }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "No se pudo preparar el visor de cámara." }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(values); position(0) }

    private companion object {
        /** Si la marca de la profundidad cae a más de esto fuera del historial de poses, no se le puede creer. */
        const val POSE_SLACK_NS = 150_000_000L

        /** La profundidad sana llega con la marca de su cuadro (±100 ms); más vieja que esto es una imagen atascada. */
        const val STALE_DEPTH_NS = 600_000_000L

        const val PLANE_UPDATE_MS = 250L
        const val PLANE_LOG_MS = 2000L
        const val PLANE_SINK_MS = 500L
        const val LAG_LOG_MS = 3000L
        const val DEPTH_RESTART_FRAMES = 8
        const val MAX_PLANES = 24
        const val MAX_PLANE_POLYGON = 64
    }
}
