package com.example.pmmovil.ui.rostro

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pmmovil.data.camera.CameraTrace
import com.example.pmmovil.ui.camera.CameraOwner
import com.example.pmmovil.ui.camera.CameraWarmupMonitor
import com.example.pmmovil.ui.camera.rememberCameraRetry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.NonCancellable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * La cámara frontal para el reconocimiento facial, en dos sabores.
 *
 * El del login NO enlaza `Preview`: no es que se esconda una vista, es que la
 * vista no existe. CameraX puede enlazar sólo el análisis, así que no hay
 * superficie que administrar, gasta menos y no queda ninguna forma accidental
 * de que la imagen aparezca en pantalla.
 *
 * Las dos rutas analizan EL MISMO cuadro sin espejar, que es como lo entrega
 * el sensor. Eso importa: si el enrolamiento y el login espejaran distinto,
 * los vectores no se parecerían y todo el umbral medido dejaría de valer.
 */

/**
 * Nombres con los que esta pantalla pide la cámara al [CameraOwner]. Los dos
 * sabores llevan tag distinto porque pueden convivir un instante durante una
 * transición, y el dueño necesita saber cuál está soltando.
 */
private const val TAG_ROSTRO_ANALISIS = "rostro-login"
private const val TAG_ROSTRO_PREVIEW = "rostro-enrolamiento"

private const val WARMUP_TICK_MS = 120L
private const val RESOLUCION_ANALISIS_ANCHO = 640
private const val RESOLUCION_ANALISIS_ALTO = 480

/** Lo que se ve, no lo que se analiza. El circulo mide ~650 px en la tableta. */
private const val RESOLUCION_PREVIEW_ANCHO = 960
private const val RESOLUCION_PREVIEW_ALTO = 720

/**
 * Cámara con preview circular, para el enrolamiento. Aquí SÍ se ve: la persona
 * necesita encuadrarse, igual que en Face ID.
 */
@Composable
fun CamaraRostroConPreview(
    onCuadro: (RostroFrame) -> Unit,
    modifier: Modifier = Modifier,
    onListaCambio: (Boolean) -> Unit = {},
    onError: (String) -> Unit = {},
    contenidoEncima: @Composable BoxScope.() -> Unit = {}
) {
    CamaraRostroBase(
        conPreview = true,
        onCuadro = onCuadro,
        onListaCambio = onListaCambio,
        onError = onError,
        modifier = modifier,
        contenidoEncima = contenidoEncima
    )
}

/**
 * Cámara sin preview, para el login. Lo único que delata que está encendida es
 * el indicador verde de Android -- que no se puede ocultar, ni debería poderse.
 */
@Composable
fun CamaraRostroSinPreview(
    activa: Boolean,
    onCuadro: (RostroFrame) -> Unit,
    onListaCambio: (Boolean) -> Unit = {}
) {
    if (!activa) return
    CamaraRostroBase(
        conPreview = false,
        onCuadro = onCuadro,
        onListaCambio = onListaCambio,
        onError = {},
        modifier = Modifier
    )
}

@Composable
@SuppressLint("UnsafeOptInUsageError")
private fun CamaraRostroBase(
    conPreview: Boolean,
    onCuadro: (RostroFrame) -> Unit,
    onListaCambio: (Boolean) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    contenidoEncima: @Composable BoxScope.() -> Unit = {}
) {
    val contexto = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ejecutor = remember { Executors.newSingleThreadExecutor() }
    // Los callbacks de ML Kit NO pueden ir al hilo principal: es ahí donde el
    // consumidor invoca bitmapProvider, y convertir YUV a RGB y rotar un cuadro
    // de 640x480 bloquea la UI cada vez que se pide un embedding. Iba al main
    // executor porque los callbacks pueden llegar después de desmontar la
    // cámara y un executor ya cerrado los rechaza; eso se resuelve con
    // DiscardPolicy, que tira en silencio lo que llegue tarde.
    val executorCallbacks = remember {
        ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(),
            ThreadPoolExecutor.DiscardPolicy()
        )
    }
    val detector = remember {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                // NONE a proposito: nadie lee sonrisa ni ojos abiertos --
                // FaceGateDetection solo lleva el marco y los angulos -- y
                // calcularlo en cada cuadro se paga en la tableta.
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build()
        )
    }
    val analisisEnCurso = remember { AtomicBoolean(false) }
    val ultimoCuadroEnviadoMs = remember { AtomicLong(0L) }
    val ultimaTrazaMs = remember { AtomicLong(0L) }

    // Tapa el arranque hasta que la exposición converge. En el Armor Pad Pro
    // los primeros cuadros salen negros y un login que "no ve" durante segundo
    // y medio se siente roto (ver CameraWarmupMonitor).
    val warmup = remember { CameraWarmupMonitor(if (conPreview) "rostro-enrol" else "rostro-login") }
    val warmupReady by warmup.isReady.collectAsState()
    // Reintenta sola si la sesión nace muerta; agotados los intentos, avisa
    // por el mismo canal de error que ya usan el permiso y el enlace fallido
    // -- este flujo tiene otros canales de acceso (NFC, QR, manual), así que
    // no hace falta una pantalla de error propia aquí.
    val cameraRetry = rememberCameraRetry(warmup)
    var avisoFalloEnviado by remember { mutableStateOf(false) }

    LaunchedEffect(warmupReady, cameraRetry.attempt, cameraRetry.failed) {
        onListaCambio(warmupReady)
        while (!warmupReady && !cameraRetry.failed) {
            delay(WARMUP_TICK_MS)
            warmup.tick()
        }
    }
    LaunchedEffect(cameraRetry.failed) {
        if (cameraRetry.failed && !avisoFalloEnviado) {
            avisoFalloEnviado = true
            onError("No se pudo abrir la cámara frontal: sin respuesta del sensor")
        }
    }

    var permisoConcedido by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val lanzador = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permisoConcedido = it
    }
    LaunchedEffect(Unit) { if (!permisoConcedido) lanzador.launch(Manifest.permission.CAMERA) }
    LaunchedEffect(permisoConcedido) {
        if (!permisoConcedido) onError("Se necesita permiso de cámara para registrar el rostro.")
    }
    DisposableEffect(detector) {
        onDispose {
            detector.close()
            ejecutor.shutdown()
            executorCallbacks.shutdown()
        }
    }

    if (!permisoConcedido) {
        if (conPreview) {
            Box(modifier.background(Color(0xFF0A0F1A)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Se necesita permiso de cámara", color = Color.White)
                    TextButton(onClick = { lanzador.launch(Manifest.permission.CAMERA) }) {
                        Text("Conceder permiso", color = Color.White)
                    }
                }
            }
        }
        return
    }

    val analizador = remember(onCuadro, detector) {
        ImageAnalysis.Analyzer { imagen ->
            val ahora = SystemClock.elapsedRealtime()
            val ultimo = ultimoCuadroEnviadoMs.get()
            if (ahora - ultimo < MIN_INTERVALO_ANALISIS_MS ||
                !analisisEnCurso.compareAndSet(false, true)
            ) {
                imagen.close()
                return@Analyzer
            }

            ultimoCuadroEnviadoMs.set(ahora)
            val mediaImage = imagen.image
            if (mediaImage == null) {
                analisisEnCurso.set(false)
                imagen.close()
                return@Analyzer
            }

            try {
                warmup.onFrameLuminance(luminanciaDe(imagen))
                val rotation = imagen.imageInfo.rotationDegrees
                val uprightWidth = if (rotation == 90 || rotation == 270) imagen.height else imagen.width
                val uprightHeight = if (rotation == 90 || rotation == 270) imagen.width else imagen.height
                val input = InputImage.fromMediaImage(mediaImage, rotation)
                val inicio = SystemClock.elapsedRealtime()

                detector.process(input)
                    .addOnSuccessListener(executorCallbacks) { caras ->
                        val detecciones = caras.map { cara ->
                            FaceGateDetection(
                                bounds = FaceGateBounds.from(cara.boundingBox),
                                pitch = cara.headEulerAngleX,
                                yaw = cara.headEulerAngleY,
                                roll = cara.headEulerAngleZ
                            )
                        }
                        val resultado = FaceMlKitGate.evaluate(
                            detections = detecciones,
                            imageWidth = uprightWidth,
                            imageHeight = uprightHeight,
                            elapsedMs = (SystemClock.elapsedRealtime() - inicio).toInt()
                        )
                        trazarResultado(resultado, ultimaTrazaMs)

                        if (resultado.status != FaceGateStatus.READY) {
                            onCuadro(RostroFrame(bitmap = null, gate = resultado))
                            return@addOnSuccessListener
                        }

                        // ML Kit es el filtro rápido. El cuadro pesado se
                        // convierte únicamente si el consumidor pide un
                        // embedding nuevo mediante bitmapProvider.
                        onCuadro(
                            RostroFrame(
                                bitmap = null,
                                gate = resultado,
                                bitmapProvider = {
                                    runCatching { rotar(imagen.toBitmap(), rotation) }
                                        .getOrNull()
                                }
                            )
                        )
                    }
                    .addOnFailureListener(executorCallbacks) { error ->
                        val resultado = FaceGateResult(
                            status = FaceGateStatus.ERROR,
                            elapsedMs = (SystemClock.elapsedRealtime() - inicio).toInt(),
                            detail = error.message ?: "No se pudo analizar el rostro"
                        )
                        trazarResultado(resultado, ultimaTrazaMs)
                        onCuadro(RostroFrame(bitmap = null, gate = resultado))
                    }
                    .addOnCompleteListener(executorCallbacks) {
                        analisisEnCurso.set(false)
                        imagen.close()
                    }
            } catch (error: Exception) {
                analisisEnCurso.set(false)
                imagen.close()
                onCuadro(
                    RostroFrame(
                        bitmap = null,
                        gate = FaceGateResult(
                            status = FaceGateStatus.ERROR,
                            detail = error.message ?: "No se pudo iniciar el detector facial"
                        )
                    )
                )
            }
        }
    }

    if (!conPreview) {
        // Sin vista: sólo se enlaza el análisis y se suelta al salir.
        //
        // El enlace pasa por [CameraOwner] y no por el proveedor directo: en el
        // login esta pantalla se turna la cámara con el lector de QR, y quien
        // coordina ese relevo es el dueño único. Aquí no se llama nunca a
        // `unbindAll()` -- soltaba también los use cases del lector y provocaba
        // el carrusel de aperturas que hacía eterno el arranque.
        LaunchedEffect(cameraRetry.attempt) {
            try {
                val analisis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(RESOLUCION_ANALISIS_ANCHO, RESOLUCION_ANALISIS_ALTO))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .also { warmup.attachTo(it) }
                    .build()
                    .also { it.setAnalyzer(ejecutor, analizador) }

                CameraOwner.bind(
                    tag = TAG_ROSTRO_ANALISIS,
                    context = contexto,
                    lifecycleOwner = lifecycleOwner,
                    selector = CameraSelector.DEFAULT_FRONT_CAMERA,
                    useCases = listOf(analisis),
                    // El reloj del calentamiento arranca cuando de verdad
                    // empieza la apertura, ya libre la cámara anterior.
                    onBeforeBind = { warmup.onBound() },
                    onError = {
                        onError("No se pudo abrir la cámara frontal: ${it.message ?: "cámara no disponible"}")
                    }
                )
                awaitCancellation()
            } finally {
                // Cancelar la corrutina corta el enlace si la pantalla se fue a
                // media apertura; el release condicional del dueño cubre el
                // caso en que ya se había enlazado. NonCancellable porque
                // release() suspende en un mutex y aquí ya estamos cancelados.
                withContext(NonCancellable) { CameraOwner.release(TAG_ROSTRO_ANALISIS) }
                warmup.reset()
            }
        }
        return
    }

    // La vista se crea en la composición y el efecto de enlace no depende de
    // ningún estado que el factory rellene después: así hay exactamente UN
    // enlace por pantalla (ver SmartBarcodeCameraView para el doble enlace
    // que esto evita).
    val previewView = remember {
        PreviewView(contexto).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE usa TextureView. Es obligatorio dentro de Compose: el
            // SurfaceView del modo PERFORMANCE se pinta en su propia capa e
            // ignora el layout, así que se comería el anillo que va encima.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(cameraRetry.attempt) {
        try {
            val preview = Preview.Builder()
                // El preview se compone en el hilo de UI (TextureView), así
                // que se pide lo justo para el círculo en pantalla. Sin esto
                // CameraX elegía cerca de 1080p y cada cuadro se pagaba en la UI.
                .setTargetResolution(Size(RESOLUCION_PREVIEW_ANCHO, RESOLUCION_PREVIEW_ALTO))
                .also { warmup.attachTo(it) }
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analisis = ImageAnalysis.Builder()
                .setTargetResolution(Size(RESOLUCION_ANALISIS_ANCHO, RESOLUCION_ANALISIS_ALTO))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(ejecutor, analizador) }

            CameraOwner.bind(
                tag = TAG_ROSTRO_PREVIEW,
                context = contexto,
                lifecycleOwner = lifecycleOwner,
                selector = CameraSelector.DEFAULT_FRONT_CAMERA,
                useCases = listOf(preview, analisis),
                onBeforeBind = { warmup.onBound() },
                onError = {
                    onError("No se pudo abrir la cámara frontal: ${it.message ?: "cámara no disponible"}")
                }
            )
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { CameraOwner.release(TAG_ROSTRO_PREVIEW) }
            warmup.reset()
        }
    }

    Box(modifier = modifier.background(Color(0xFF0A0F1A))) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        contenidoEncima()
    }
}

/**
 * Luminancia media 0..1 del plano Y, muestreada cada 64 bytes.
 *
 * Alimenta al monitor de arranque para que sepa distinguir "AE convergida" de
 * "AE dice que convergió pero el cuadro está negro", que es exactamente lo que
 * hacía el HAL de esta tableta.
 */
private fun luminanciaDe(imagen: ImageProxy): Double {
    return runCatching {
        val buffer = imagen.planes[0].buffer
        buffer.rewind()
        var suma = 0L
        var muestras = 0
        var i = 0
        while (i < buffer.remaining()) {
            suma += (buffer.get(i).toInt() and 0xFF)
            muestras++
            i += 64
        }
        if (muestras == 0) 0.0 else (suma.toDouble() / muestras) / 255.0
    }.getOrDefault(0.0)
}

private fun rotar(bitmap: Bitmap, grados: Int): Bitmap {
    if (grados == 0) return bitmap
    val matriz = Matrix().apply { postRotate(grados.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matriz, true)
}

private fun trazarResultado(resultado: FaceGateResult, ultimaTrazaMs: AtomicLong) {
    val ahora = SystemClock.elapsedRealtime()
    val anterior = ultimaTrazaMs.get()
    if (ahora - anterior < TRAZA_INTERVALO_MS || !ultimaTrazaMs.compareAndSet(anterior, ahora)) return

    CameraTrace.d(
        "mlkit status=${resultado.status} mlkitMs=${resultado.elapsedMs} " +
            "faceWidth=${resultado.bounds?.width ?: 0} " +
            "yaw=${"%.1f".format(java.util.Locale.US, resultado.yaw)} " +
            "pitch=${"%.1f".format(java.util.Locale.US, resultado.pitch)}"
    )
}

private const val MIN_INTERVALO_ANALISIS_MS = 100L
private const val TRAZA_INTERVALO_MS = 1000L
