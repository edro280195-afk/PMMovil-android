package com.example.pmmovil.ui.ar

import android.app.Activity
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.Surface
import com.example.pmmovil.data.inventory.RackCensusCodeParser
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class ArStartResult { STARTED, INSTALLING, UNAVAILABLE }

/** Visor de cámara ARCore; las tarjetas de Compose se proyectan desde sus anclas locales. */
class EntryArSpatialView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    private val onQrSeen: (String, Float, Float) -> Unit,
    private val onQrObserved: (String) -> Unit,
    private val onProjected: (String, Float, Float) -> Unit,
    private val unavailableListener: (String) -> Unit
) : GLSurfaceView(context, attrs) {

    private val renderer = EntryArRenderer(
        context = context.applicationContext,
        onQrSeen = onQrSeen,
        onQrObserved = onQrObserved,
        onProjected = onProjected
    )
    @Volatile private var arSession: Session? = null
    private var installRequested = false
    private var surfaceResumed = false
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    /** Inicializa o reanuda ARCore y distingue instalación pendiente de equipo no compatible. */
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
                val createdSession = Session(activity)
                val config = Config(createdSession)
                config.focusMode = Config.FocusMode.AUTO
                if (createdSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                }
                createdSession.configure(config)
                arSession = createdSession
                renderer.attachSession(createdSession)
            }
            arSession?.resume()
            super.onResume()
            surfaceResumed = true
            return ArStartResult.STARTED
        } catch (exception: Exception) {
            closeSession()
            val message = when (exception) {
                is com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException ->
                    "Falta instalar Google Play Services for AR. Se abrirá el visor asistido."
                is com.google.ar.core.exceptions.UnavailableApkTooOldException ->
                    "Google Play Services for AR necesita actualizarse. Se abrirá el visor asistido."
                is com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException ->
                    "Este equipo no es compatible con ARCore. Se abrirá el visor asistido."
                else -> "No se pudo iniciar ARCore (${exception.javaClass.simpleName}). Se abrirá el visor asistido."
            }
            publishUnavailable(message)
            return ArStartResult.UNAVAILABLE
        }
    }

    fun pauseAr() {
        runCatching { arSession?.pause() }
        if (surfaceResumed) {
            super.onPause()
            surfaceResumed = false
        }
    }

    fun clearAnchors() {
        renderer.clearAnchors()
    }

    fun close() {
        pauseAr()
        renderer.close()
        closeSession()
    }

    private fun closeSession() {
        runCatching { arSession?.close() }
        arSession = null
    }

    private fun publishUnavailable(message: String) {
        mainHandler.post { unavailableListener.invoke(message) }
    }
}

private data class QrDetection(
    val normalizedCode: String,
    val rawValue: String,
    val imageX: Float,
    val imageY: Float,
    val detectedAt: Long
)

private class EntryArRenderer(
    context: Context,
    private val onQrSeen: (String, Float, Float) -> Unit,
    private val onQrObserved: (String) -> Unit,
    private val onProjected: (String, Float, Float) -> Unit
) : GLSurfaceView.Renderer {
    private val appContext = context
    private val mainHandler = Handler(Looper.getMainLooper())
    private val qrScanner: BarcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    )
    private val processingImage = AtomicBoolean(false)
    private val detections = ConcurrentLinkedQueue<QrDetection>()
    private val anchors = LinkedHashMap<String, com.google.ar.core.Anchor>()
    private val ignoredAfterClear = HashSet<String>()
    private val lastSeenAt = HashMap<String, Long>()
    private val lastQrDetectedAt = HashMap<String, Long>()
    private val lastPublishedAt = HashMap<String, Long>()
    private val quadPositions = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val inputTextureCoordinates = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
    private val inputTextureCoordinateBuffer = floatBuffer(inputTextureCoordinates)
    private val textureCoordinates = floatBuffer(inputTextureCoordinates)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val viewProjectionMatrix = FloatArray(16)
    private val clipCoordinates = FloatArray(4)
    private val imagePoints = FloatArray(2)
    private val viewPoints = FloatArray(2)
    @Volatile private var session: Session? = null
    private var textureConfiguredFor: Session? = null
    private var cameraTextureId = 0
    private var cameraProgram = 0
    private var positionLocation = -1
    private var textureLocation = -1
    private var samplerLocation = -1
    private var viewportWidth = 1
    private var viewportHeight = 1

    /** Proyección de la cámara para calcular la dirección de un QR cuando el hit-test no da nada. */
    private val fallbackProjection = FloatArray(16)
    private var lastImageAt = 0L
    private var lastSeenPrunedAt = 0L
    private var lastInvisibleAnchorPrunedAt = 0L
    private var cameraRotationDegrees = 0
    private var lastDisplayRotation = -1
    @Volatile private var shouldClearAnchors = false
    @Volatile private var stopped = false

    fun attachSession(newSession: Session) {
        session = newSession
    }

    fun clearAnchors() {
        shouldClearAnchors = true
    }

    fun close() {
        stopped = true
        runCatching { qrScanner.close() }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        cameraTextureId = createExternalTexture()
        cameraProgram = createCameraProgram()
        positionLocation = GLES20.glGetAttribLocation(cameraProgram, "a_Position")
        textureLocation = GLES20.glGetAttribLocation(cameraProgram, "a_TexCoord")
        samplerLocation = GLES20.glGetUniformLocation(cameraProgram, "sTexture")
        session?.let { arSession ->
            if (runCatching { arSession.setCameraTextureNames(intArrayOf(cameraTextureId)) }.isSuccess) {
                textureConfiguredFor = arSession
            }
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width.coerceAtLeast(1)
        viewportHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (shouldClearAnchors) {
            ignoredAfterClear.addAll(lastPublishedAt.keys)
            ignoredAfterClear.addAll(anchors.keys)
            anchors.values.forEach { runCatching { it.detach() } }
            anchors.clear()
            lastPublishedAt.keys.removeAll { key -> key !in ignoredAfterClear }
            lastSeenAt.keys.removeAll { key -> key !in ignoredAfterClear }
            shouldClearAnchors = false
        }

        val activeSession = session ?: return
        if (stopped) return
        try {
            if (textureConfiguredFor !== activeSession) {
                activeSession.setCameraTextureNames(intArrayOf(cameraTextureId))
                textureConfiguredFor = activeSession
            }
            updateDisplayGeometry(activeSession)
            val frame = activeSession.update()
            drawCamera(frame)
            scanCameraImage(frame)
            processDetections(frame, activeSession)
            projectAnchors(frame)
        } catch (_: Exception) {
            // ARCore puede omitir frames durante un cambio de superficie o al reanudar.
        }
    }

    private fun updateDisplayGeometry(activeSession: Session) {
        val rotation = (appContext.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
        if (rotation != lastDisplayRotation) {
            activeSession.setDisplayGeometry(rotation, viewportWidth, viewportHeight)
            lastDisplayRotation = rotation
        }
        val displayDegrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        cameraRotationDegrees = runCatching {
            val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = activeSession.cameraConfig.cameraId
            val sensorDegrees = cameraManager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            (sensorDegrees - displayDegrees + 360) % 360
        }.getOrDefault(90)
    }

    private fun drawCamera(frame: Frame) {
        inputTextureCoordinateBuffer.position(0)
        textureCoordinates.position(0)
        frame.transformDisplayUvCoords(inputTextureCoordinateBuffer, textureCoordinates)
        textureCoordinates.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUniform1i(samplerLocation, 0)
        GLES20.glEnableVertexAttribArray(positionLocation)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, 0, quadPositions)
        GLES20.glEnableVertexAttribArray(textureLocation)
        GLES20.glVertexAttribPointer(textureLocation, 2, GLES20.GL_FLOAT, false, 0, textureCoordinates)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionLocation)
        GLES20.glDisableVertexAttribArray(textureLocation)
    }

    private fun scanCameraImage(frame: Frame) {
        val now = System.currentTimeMillis()
        if (now - lastImageAt < IMAGE_SCAN_INTERVAL_MS || !processingImage.compareAndSet(false, true)) return
        val image = runCatching { frame.acquireCameraImage() }.getOrNull()
        if (image == null) {
            processingImage.set(false)
            return
        }
        lastImageAt = now
        val rotation = cameraRotationDegrees
        val input = try {
            InputImage.fromMediaImage(image, rotation)
        } catch (_: Exception) {
            image.close()
            processingImage.set(false)
            return
        }

        try {
            qrScanner.process(input)
                .addOnSuccessListener { barcodes ->
                    barcodes.forEach { barcode ->
                        val raw = barcode.rawValue ?: return@forEach
                        val parsed = RackCensusCodeParser.parse(raw) ?: return@forEach
                        val bounds = barcode.boundingBox ?: return@forEach
                        val rawPoint = uprightToImagePoint(
                            bounds.exactCenterX(),
                            bounds.exactCenterY(),
                            image.width,
                            image.height,
                            rotation
                        )
                        detections.offer(
                            QrDetection(
                                normalizedCode = parsed.normalized,
                                rawValue = raw,
                                imageX = rawPoint.first,
                                imageY = rawPoint.second,
                                detectedAt = System.currentTimeMillis()
                            )
                        )
                    }
                }
                .addOnCompleteListener {
                    runCatching { image.close() }
                    processingImage.set(false)
                }
        } catch (_: Exception) {
            runCatching { image.close() }
            processingImage.set(false)
        }
    }

    private fun processDetections(frame: Frame, activeSession: Session) {
        val pruneTime = System.currentTimeMillis()
        pruneSeenCodes(pruneTime)
        pruneInvisibleAnchors(pruneTime)
        while (true) {
            val detection = detections.poll() ?: break
            if (frame.camera.trackingState != TrackingState.TRACKING) continue
            imagePoints[0] = detection.imageX
            imagePoints[1] = detection.imageY
            try {
                frame.transformCoordinates2d(
                    Coordinates2d.IMAGE_PIXELS,
                    imagePoints,
                    Coordinates2d.VIEW,
                    viewPoints
                )
            } catch (_: Exception) {
                continue
            }
            val viewX = viewPoints[0].coerceIn(0f, viewportWidth.toFloat())
            val viewY = viewPoints[1].coerceIn(0f, viewportHeight.toFloat())
            val now = detection.detectedAt
            val previousDetectionAt = lastQrDetectedAt[detection.normalizedCode]
            if (detection.normalizedCode in ignoredAfterClear) {
                val previousDetectionAt = lastQrDetectedAt.put(detection.normalizedCode, now)
                if (previousDetectionAt == null || now - previousDetectionAt <= REANCHOR_AFTER_MISSING_MS) continue
                ignoredAfterClear.remove(detection.normalizedCode)
                lastPublishedAt.remove(detection.normalizedCode)
            }
            lastQrDetectedAt[detection.normalizedCode] = now
            val hasAnchor = detection.normalizedCode in anchors
            val previousSeen = if (hasAnchor) lastSeenAt.put(detection.normalizedCode, now) else null
            val needsAnchor = !hasAnchor ||
                (previousSeen != null && now - previousSeen > REANCHOR_AFTER_MISSING_MS)
            val nx = viewX / viewportWidth.toFloat()
            val ny = viewY / viewportHeight.toFloat()
            // Mientras el QR se esté viendo, su posición EN LA IMAGEN es el dato bueno: se publica siempre. El ancla
            // sirve para sostener la tarjeta cuando el código sale del cuadro, no para competir con la lectura.
            // Antes, las lecturas repetidas solo avisaban de presencia (`onQrObserved`) y mandaba la proyección del
            // ancla; si esa ancla era la supuesta a 1.5 m, la marca se iba de su QR en cuanto te movías.
            mainHandler.post { onQrSeen(detection.rawValue, nx, ny) }
            if (needsAnchor && !hasAnchor && anchors.size >= MAX_ANCHORS) {
                continue
            }
            if (needsAnchor) {
                anchors.remove(detection.normalizedCode)?.let { oldAnchor ->
                    runCatching { oldAnchor.detach() }
                }
                createLocalAnchor(frame, activeSession, viewX, viewY)?.let { anchor ->
                    anchors[detection.normalizedCode] = anchor
                    lastSeenAt[detection.normalizedCode] = now
                }
            }
        }
    }

    private fun pruneInvisibleAnchors(now: Long) {
        if (now - lastInvisibleAnchorPrunedAt < INVISIBLE_ANCHOR_PRUNE_INTERVAL_MS) return
        lastInvisibleAnchorPrunedAt = now
        val invisibleKeys = anchors.keys.filter { key ->
            val lastDetectedAt = lastQrDetectedAt[key] ?: 0L
            now - lastDetectedAt > ANCHOR_VISIBILITY_TIMEOUT_MS
        }
        invisibleKeys.forEach { key ->
            anchors.remove(key)?.let { anchor -> runCatching { anchor.detach() } }
            lastSeenAt.remove(key)
            lastPublishedAt.remove(key)
        }
        val expiredCodeKeys = lastQrDetectedAt.entries
            .asSequence()
            .filter { (key, detectedAt) ->
                now - detectedAt > SEEN_CODE_TTL_MS && key !in anchors
            }
            .map { it.key }
            .toList()
        expiredCodeKeys.forEach { key ->
            lastQrDetectedAt.remove(key)
            lastSeenAt.remove(key)
            lastPublishedAt.remove(key)
            ignoredAfterClear.remove(key)
        }
    }

    private fun pruneSeenCodes(now: Long) {
        if (now - lastSeenPrunedAt < SEEN_CODE_PRUNE_INTERVAL_MS) return
        lastSeenPrunedAt = now
        val staleKeys = lastPublishedAt.entries
            .asSequence()
            .filter { (key, seenAt) -> key !in anchors && now - seenAt > SEEN_CODE_TTL_MS }
            .map { it.key }
            .toList()
        staleKeys.forEach { key ->
            lastPublishedAt.remove(key)
            lastSeenAt.remove(key)
            ignoredAfterClear.remove(key)
        }
    }

    private fun createLocalAnchor(frame: Frame, activeSession: Session, x: Float, y: Float): com.google.ar.core.Anchor? {
        val hitResults = runCatching { frame.hitTest(x, y) }.getOrDefault(emptyList())
        val hit = hitResults.firstOrNull {
            it.trackable is com.google.ar.core.Plane && it.trackable.trackingState == TrackingState.TRACKING
        } ?: hitResults.firstOrNull {
            it.trackable is com.google.ar.core.DepthPoint && it.trackable.trackingState == TrackingState.TRACKING
        } ?: hitResults.firstOrNull { it.trackable.trackingState == TrackingState.TRACKING }
        if (hit != null) return runCatching { hit.createAnchor() }.getOrNull()

        // Algunos pasillos no tienen planos detectables, y al abrir la cámara ARCore todavía no tiene ni planos ni
        // profundidad (el log dice `hit_test: No point hit` / `Invalid depth`). Entonces se coloca el ancla a una
        // distancia supuesta, pero **en la dirección en la que se vio el QR**, no recta al frente.
        //
        // Antes esta rama usaba (0, 0, −distancia), o sea el eje de la cámara: todas las marcas caían en el MISMO
        // punto, el centro de la pantalla, hasta que el hit-test empezaba a funcionar. Eduardo lo vio al primer
        // intento el 22-oct: «todos los cuadritos se centraron en el centro de la pantalla».
        val anchoVista = viewportWidth.toFloat()
        val altoVista = viewportHeight.toFloat()
        if (anchoVista < 1f || altoVista < 1f) return null
        return runCatching {
            frame.camera.getProjectionMatrix(fallbackProjection, 0, 0.05f, 100f)
            val fx = fallbackProjection[0]
            val fy = fallbackProjection[5]
            if (fx == 0f || fy == 0f) return@runCatching null
            // Pantalla -> coordenadas normalizadas -> dirección en el marco de la cámara (mira hacia −Z).
            val ndcX = (2f * x / anchoVista) - 1f
            val ndcY = 1f - (2f * y / altoVista)
            var dx = ndcX / fx
            var dy = ndcY / fy
            var dz = -1f
            val largo = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            dx /= largo
            dy /= largo
            dz /= largo
            val d = DEFAULT_ANCHOR_DISTANCE_METERS
            activeSession.createAnchor(
                frame.camera.pose.compose(Pose.makeTranslation(dx * d, dy * d, dz * d))
            )
        }.getOrNull()
    }

    private fun projectAnchors(frame: Frame) {
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) return
        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projectionMatrix, 0, 0.05f, 100f)
        Matrix.multiplyMM(viewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        val now = System.currentTimeMillis()
        anchors.entries.toList().forEach { (key, anchor) ->
            if (anchor.trackingState != TrackingState.TRACKING) return@forEach
            anchor.pose.toMatrix(modelMatrix, 0)
            Matrix.multiplyMV(clipCoordinates, 0, viewProjectionMatrix, 0, floatArrayOf(modelMatrix[12], modelMatrix[13], modelMatrix[14], 1f), 0)
            val w = clipCoordinates[3]
            if (w <= 0f) return@forEach
            val x = (clipCoordinates[0] / w + 1f) * 0.5f
            val y = (1f - clipCoordinates[1] / w) * 0.5f
            if (x !in 0f..1f || y !in 0f..1f) return@forEach
            val last = lastPublishedAt[key] ?: 0L
            if (now - last < PROJECTION_PUBLISH_INTERVAL_MS) return@forEach
            lastPublishedAt[key] = now
            mainHandler.post { onProjected(key, x, y) }
        }
    }

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
        val vertexShader = compileShader(
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
        val fragmentShader = compileShader(
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
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val status = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
            check(status[0] == GLES20.GL_TRUE) { "No se pudo preparar el visor de cámara AR." }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] == GLES20.GL_TRUE) { "No se pudo preparar el visor de cámara AR." }
        return shader
    }

    private fun floatBuffer(values: FloatArray) = ByteBuffer.allocateDirect(values.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(values); position(0) }

    private fun uprightToImagePoint(x: Float, y: Float, imageWidth: Int, imageHeight: Int, rotation: Int): Pair<Float, Float> {
        val point = when (rotation) {
            90 -> y to (imageHeight - x)
            180 -> (imageWidth - x) to (imageHeight - y)
            270 -> (imageWidth - y) to x
            else -> x to y
        }
        return point.first.coerceIn(0f, imageWidth.toFloat()) to
            point.second.coerceIn(0f, imageHeight.toFloat())
    }

    private companion object {
        const val IMAGE_SCAN_INTERVAL_MS = 420L
        const val QR_CARD_REAPPEAR_DELAY_MS = 700L
        const val REANCHOR_AFTER_MISSING_MS = 2_500L
        const val ANCHOR_VISIBILITY_TIMEOUT_MS = 1_800L
        const val INVISIBLE_ANCHOR_PRUNE_INTERVAL_MS = 300L
        const val PROJECTION_PUBLISH_INTERVAL_MS = 50L
        const val DEFAULT_ANCHOR_DISTANCE_METERS = 1.5f
        const val MAX_ANCHORS = 6
        const val SEEN_CODE_PRUNE_INTERVAL_MS = 10_000L
        const val SEEN_CODE_TTL_MS = 30_000L
    }
}

