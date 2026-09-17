package com.example.pmmovil.ui.casetaentrada

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Environment
import android.util.Size as AndroidSize
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pmmovil.data.model.CasetaEntradaOcrCandidate
import com.example.pmmovil.data.ocr.LiveTextOcrAnalyzer
import androidx.compose.foundation.gestures.awaitFirstDown
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.collectAsState
import com.example.pmmovil.ui.camera.CameraErrorView
import com.example.pmmovil.ui.camera.CameraOwner
import com.example.pmmovil.ui.camera.CameraWarmupCurtain
import com.example.pmmovil.ui.camera.CameraWarmupMonitor
import com.example.pmmovil.ui.camera.rememberCameraRetry

private const val TAG_CASETA_OCR_CAMERA = "caseta-ocr-camera"

/**
 * Valor neutro para avisar "llegó una imagen real" a [CameraWarmupMonitor]
 * sin calcular su luminancia de verdad (esta pantalla no exige
 * `requireLuminance`, así que el número en sí no importa; sólo cuenta como
 * prueba de vida). Ver [SmartCaptureLiveScreen]'s uso de `onFrameSeen`.
 */
private const val NEUTRAL_LUMINANCE = 0.5
private val MonoFamily = FontFamily.Monospace
private val CameraBg = Color(0xFF020617) // Azul oscuro profundo
private val CapturePanel = Color(0xFFFFFFFF) // Fondo blanco para bottom sheet
private val CapturePanelSoft = Color(0xFFF1F5FA) // Fondo azul grisáceo muy claro
private val CaptureInk = Color(0xFF0F172A) // Texto oscuro (Slate 900)
private val CaptureMuted = Color(0xFF64748B) // Texto secundario (Slate 500)
private val CaptureLine = Color(0x1F003DA5) // Borde azul muy tenue
private val CaptureScan = Color(0xFF3B82F6) // Azul claro PMM para visor y acentos
private val CaptureMint = Color(0xFF003DA5) // Azul profundo PMM para éxito/confirmado

/**
 * Captura inteligente en vivo: apunta a la caja/placa y el número se detecta y
 * se fija solo (sin botón de disparo). Todo ocurre en esta superficie — sin
 * modal que bloquee ni sheet aparte. Respaldos: "Tomar foto" (OCR + servidor)
 * y "Escribir a mano".
 */
@Composable
fun SmartCaptureLiveScreen(
    initialMode: CasetaEntradaCaptureMode,
    casetaId: String,
    ocrState: CasetaEntradaOcrState,
    onConfirmValue: (CasetaEntradaCaptureMode, String) -> Unit,
    onClose: () -> Unit,
    onCapturePhoto: (CasetaEntradaCaptureMode, String) -> Unit,
    onDismissOcr: () -> Unit
) {
    // Sin esto, el boton de atras del sistema no lo intercepta esta pantalla:
    // sale de todo el modulo (ej. Entrada Caseta) y se pierde la captura en curso.
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var mode by remember { mutableStateOf(initialMode) }
    var liveCandidates by remember { mutableStateOf<List<CasetaEntradaOcrCandidate>>(emptyList()) }
    var locked by remember { mutableStateOf<String?>(null) }
    var lockedCandidates by remember { mutableStateOf<List<CasetaEntradaOcrCandidate>>(emptyList()) }
    var manual by remember { mutableStateOf("") }
    var showManual by remember { mutableStateOf(false) }
    var torch by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // Tapa el visor hasta que la exposicion automatica converge tras abrir la
    // camara (ver CameraWarmupMonitor). Con plazo de seguridad.
    val warmup = remember { CameraWarmupMonitor("captura-caseta") }
    val warmupReady by warmup.isReady.collectAsState()
    val cameraRetry = rememberCameraRetry(warmup)
    LaunchedEffect(warmupReady, cameraRetry.attempt, cameraRetry.failed) {
        while (!warmupReady && !cameraRetry.failed) {
            delay(200L)
            warmup.tick()
        }
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var secondsElapsed by remember { mutableStateOf(0) }
    var showAnalysisDialog by remember { mutableStateOf(false) }
    var analysisFinishedMessage by remember { mutableStateOf<String?>(null) }
    var showShutterFlash by remember { mutableStateOf(false) }
    var capturedPhotoPath by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    // Tap-to-focus state
    // Creada en la composición: el efecto de enlace no puede depender de un
    // estado que el factory del AndroidView rellene después (eso producía dos
    // enlaces por apertura; ver SmartBarcodeCameraView).
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView: CameraX conserva la superficie hasta que la sesión
            // termina de usarla; salir de la pantalla no destruye una
            // superficie con cuadros en vuelo.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var focusTapOffset by remember { mutableStateOf<Offset?>(null) }
    var focusRingVisible by remember { mutableStateOf(false) }


    val stability = remember { StabilityLock() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val modeState = rememberUpdatedState(mode)
    val lockedState = rememberUpdatedState(locked)

    fun resetScan() {
        stability.reset()
        liveCandidates = emptyList()
        locked = null
        lockedCandidates = emptyList()
        onDismissOcr()
    }

    fun takePhotoAndAnalyze() {
        val capture = imageCapture ?: return
        isCapturing = true
        showAnalysisDialog = true
        analysisFinishedMessage = null
        showShutterFlash = true
        coroutineScope.launch {
            delay(80)
            showShutterFlash = false
        }
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "caseta_ocr_temp"
        ).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
        val file = File(directory, "ocr_${mode.name.lowercase()}_$timestamp.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    isCapturing = false
                    capturedPhotoPath = file.absolutePath
                    onCapturePhoto(mode, file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    showAnalysisDialog = false
                }
            }
        )
    }

    LaunchedEffect(mode, locked) {
        secondsElapsed = 0
        if (locked == null) {
            while (true) {
                delay(1000)
                secondsElapsed += 1
            }
        }
    }

    LaunchedEffect(ocrState.isLoading) {
        if (showAnalysisDialog && !ocrState.isLoading) {
            val count = ocrState.candidates.size
            analysisFinishedMessage = if (count > 0) {
                "$count coincidencias encontradas"
            } else {
                "0 coincidencias encontradas"
            }
            delay(1500)
            showAnalysisDialog = false
            analysisFinishedMessage = null

            if (ocrState.outcome == CasetaEntradaOcrOutcome.AutoApplied && ocrState.primaryValue.isNotBlank()) {
                onConfirmValue(mode, ocrState.primaryValue)
            } else if (ocrState.candidates.isNotEmpty()) {
                lockedCandidates = ocrState.candidates
                locked = ocrState.primaryValue.ifBlank { ocrState.candidates.first().value }
            } else if (ocrState.phase == CasetaEntradaOcrPhase.Failed) {
                resetScan()
            }
        }
    }

    val isCapturingState = rememberUpdatedState(isCapturing)
    val isLoadingState = rememberUpdatedState(ocrState.isLoading)
    val dialogActiveState = rememberUpdatedState(showAnalysisDialog)

    val analyzer = remember {
        LiveTextOcrAnalyzer(
            shouldSkipFrame = {
                isCapturingState.value || isLoadingState.value ||
                    dialogActiveState.value || lockedState.value != null
            },
            // Prueba de vida independiente de los metadatos de captura: ver
            // la nota en CameraWarmupTracker.onLuminance sobre la sesion que
            // el HAL declaraba muda mientras seguia entregando imagenes
            // reales al analizador.
            onFrameSeen = { warmup.onFrameLuminance(NEUTRAL_LUMINANCE) }
        ) { result ->
            if (isCapturingState.value || isLoadingState.value || dialogActiveState.value) return@LiveTextOcrAnalyzer
            if (lockedState.value != null) return@LiveTextOcrAnalyzer
            val ranked = if (modeState.value == CasetaEntradaCaptureMode.Box) {
                BoxOcrRanker.rank(result)
            } else {
                PlateOcrRanker.rank(result)
            }
            if (ranked.isEmpty()) return@LiveTextOcrAnalyzer
            liveCandidates = ranked.take(3)
            val lockedNow = stability.offer(
                ranked.first().value,
                ranked.first().confidence,
                System.currentTimeMillis()
            )
            if (lockedNow != null) {
                lockedCandidates = ranked.take(4)
                locked = lockedNow
            }
        }
    }


    LaunchedEffect(locked) {
        if (locked != null) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
        }
    }

    // El analizador OCR y su executor viven lo que vive la pantalla.
    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    LaunchedEffect(hasPermission, cameraRetry.attempt) {
        if (!hasPermission) return@LaunchedEffect
        try {
            val preview = Preview.Builder()
                .also { warmup.attachTo(it) }
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(AndroidSize(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analyzer) }
            // Esta pantalla necesita capturar mientras el vehículo puede
            // moverse: conserva el modo de baja latencia y fija la calidad del
            // JPEG para que el resultado no dependa del valor por defecto.
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setJpegQuality(95)
                .build()
            val boundCamera = CameraOwner.bind(
                tag = TAG_CASETA_OCR_CAMERA,
                context = context,
                lifecycleOwner = lifecycleOwner,
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCases = listOf(preview, analysis, capture),
                onBeforeBind = { warmup.onBound() },
                onError = { error ->
                    Log.e("SmartCaptureLive", "Error al iniciar cámara", error)
                    cameraRetry.forceFail()
                }
            )
            if (boundCamera != null) {
                imageCapture = capture
                camera = boundCamera
                boundCamera.cameraControl.setZoomRatio(1f)
                awaitCancellation()
            }
        } finally {
            withContext(NonCancellable) { CameraOwner.release(TAG_CASETA_OCR_CAMERA) }
            imageCapture = null
            camera = null
        }
    }

    val titulo = if (mode == CasetaEntradaCaptureMode.Box) "caja" else "placa"

    val isLocked = locked != null
    val activeColor = if (isLocked) CaptureMint else Color.White.copy(alpha = 0.52f)
    
    val stabilityScale by animateFloatAsState(
        targetValue = if (isLocked) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "visorScale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "laserSweep")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserProgress"
    )

    Box(modifier = Modifier.fillMaxSize().background(CameraBg)) {
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(CaptureScan.copy(alpha = 0.12f))
                        .border(1.dp, CaptureScan.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = CaptureScan,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Permiso de cámara requerido",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Para iniciar la detección inteligente de cajas y placas, necesitamos acceso a la cámara.",
                    color = CaptureMuted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CaptureScan, contentColor = CameraBg),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Permitir cámara", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            CameraWarmupCurtain(
                visible = !warmupReady && !cameraRetry.failed,
                modifier = Modifier.fillMaxSize()
            )

            if (cameraRetry.failed) {
                CameraErrorView(
                    onRetry = cameraRetry.retry,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Overlay táctil: tap-to-focus
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val pv = previewView
                                val cam = camera
                                if (cam != null) {
                                    val point = pv.meteringPointFactory.createPoint(down.position.x, down.position.y)
                                    val action = FocusMeteringAction.Builder(point)
                                        .addPoint(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                        .build()
                                    cam.cameraControl.startFocusAndMetering(action)
                                    focusTapOffset = down.position
                                    focusRingVisible = true
                                }
                            }
                        }
                    }
            )

            // Anillo de enfoque animado en el punto de toque
            focusTapOffset?.let { tapPos ->
                LaunchedEffect(tapPos) {
                    delay(1800)
                    focusRingVisible = false
                }
                val ringAlpha by animateFloatAsState(
                    targetValue = if (focusRingVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 220),
                    label = "focusRingAlpha"
                )
                val ringScale by animateFloatAsState(
                    targetValue = if (focusRingVisible) 1f else 1.3f,
                    animationSpec = spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "focusRingScale"
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = ringAlpha
                        }
                ) {
                    val radius = 44.dp.toPx()
                    drawCircle(
                        color = Color.White,
                        radius = radius * ringScale,
                        center = tapPos,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    val gap = 18.dp.toPx()
                    val len = 10.dp.toPx()
                    val r = radius * ringScale
                    // Top
                    drawLine(Color.White, Offset(tapPos.x, tapPos.y - r - len), Offset(tapPos.x, tapPos.y - r + gap), 1.6.dp.toPx())
                    // Bottom
                    drawLine(Color.White, Offset(tapPos.x, tapPos.y + r - gap), Offset(tapPos.x, tapPos.y + r + len), 1.6.dp.toPx())
                    // Left
                    drawLine(Color.White, Offset(tapPos.x - r - len, tapPos.y), Offset(tapPos.x - r + gap, tapPos.y), 1.6.dp.toPx())
                    // Right
                    drawLine(Color.White, Offset(tapPos.x + r - gap, tapPos.y), Offset(tapPos.x + r + len, tapPos.y), 1.6.dp.toPx())
                }
            }

        }

        if (showShutterFlash) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.82f))
            )
        }

        // Vignette gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.58f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.72f)
                        )
                    )
                )
        )

        // Viewfinder
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.82f)
                .aspectRatio(1.7f)
                .graphicsLayer {
                    scaleX = stabilityScale
                    scaleY = stabilityScale
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val strokePx = 1.5.dp.toPx()
                val lenPx = 20.dp.toPx()

                // Outline base
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.08f),
                    topLeft = Offset.Zero,
                    size = size,
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )

                // L-Brackets
                // Top-Left
                drawLine(activeColor, Offset(0f, 0f), Offset(lenPx, 0f), strokePx)
                drawLine(activeColor, Offset(0f, 0f), Offset(0f, lenPx), strokePx)
                // Top-Right
                drawLine(activeColor, Offset(w, 0f), Offset(w - lenPx, 0f), strokePx)
                drawLine(activeColor, Offset(w, 0f), Offset(w, lenPx), strokePx)
                // Bottom-Left
                drawLine(activeColor, Offset(0f, h), Offset(lenPx, h), strokePx)
                drawLine(activeColor, Offset(0f, h), Offset(0f, h - lenPx), strokePx)
                // Bottom-Right
                drawLine(activeColor, Offset(w, h), Offset(w - lenPx, h), strokePx)
                drawLine(activeColor, Offset(w, h), Offset(w, h - lenPx), strokePx)

                // Laser scan line
                if (!isLocked) {
                    val y = h * laserProgress
                    drawLine(
                        color = CaptureScan.copy(alpha = 0.58f),
                        start = Offset(6.dp.toPx(), y),
                        end = Offset(w - 6.dp.toPx(), y),
                        strokeWidth = 1.2.dp.toPx()
                    )
                    drawLine(
                        color = CaptureScan.copy(alpha = 0.18f),
                        start = Offset(12.dp.toPx(), y - 3.dp.toPx()),
                        end = Offset(w - 12.dp.toPx(), y - 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = CaptureScan.copy(alpha = 0.18f),
                        start = Offset(12.dp.toPx(), y + 3.dp.toPx()),
                        end = Offset(w - 12.dp.toPx(), y + 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            locked?.let {
                AnimatedTextLockView(
                    text = it,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Float Guidance text
        Surface(
            color = Color.Black.copy(alpha = 0.65f),
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 120.dp)
        ) {
            Text(
                text = if (locked != null) "Detectado · revísalo abajo" else "Apunta a la $titulo · se detecta solo",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }

        // Top Navigation Floating Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Close Button
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar cámara",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Mode Capsule Switcher
            Row(
                modifier = Modifier
                    .width(160.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (mode == CasetaEntradaCaptureMode.Box) Color.White else Color.Transparent)
                        .clickable {
                            if (mode != CasetaEntradaCaptureMode.Box) {
                                mode = CasetaEntradaCaptureMode.Box
                                resetScan()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Caja",
                        color = if (mode == CasetaEntradaCaptureMode.Box) CaptureMint else Color.White.copy(alpha = 0.76f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (mode == CasetaEntradaCaptureMode.Plate) Color.White else Color.Transparent)
                        .clickable {
                            if (mode != CasetaEntradaCaptureMode.Plate) {
                                mode = CasetaEntradaCaptureMode.Plate
                                resetScan()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Placa",
                        color = if (mode == CasetaEntradaCaptureMode.Plate) CaptureMint else Color.White.copy(alpha = 0.76f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            // Flash Toggle Button
            val hasFlash = camera?.cameraInfo?.hasFlashUnit() == true
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (torch) Color.White else Color.Black.copy(alpha = 0.42f))
                    .border(1.dp, Color.White.copy(alpha = if (torch) 0f else 0.15f), CircleShape)
                    .clickable(enabled = hasFlash) {
                        torch = !torch
                        camera?.cameraControl?.enableTorch(torch)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = "Linterna",
                    tint = if (torch) CaptureMint else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom Result Panel (Animated Bottom Sheet)
        AnimatedVisibility(
            visible = locked != null || showManual || liveCandidates.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = spring(stiffness = Spring.StiffnessLow)) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xF2FFFFFF), // Fondo blanco translúcido minimalista
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                border = BorderStroke(1.dp, Color(0x12003DA5)), // Borde azul muy tenue
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when {
                        showManual -> {
                            OutlinedTextField(
                                value = manual,
                                onValueChange = { manual = it.uppercase() },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                label = { Text("Escribir $titulo a mano") },
                                textStyle = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Bold, color = CaptureInk),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    manual.trim().takeIf(String::isNotBlank)?.let { onConfirmValue(mode, it) }
                                }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = CaptureScan,
                                    unfocusedBorderColor = Color(0xFFCBD5E1),
                                    focusedLabelColor = CaptureMint,
                                    unfocusedLabelColor = CaptureMuted,
                                    cursorColor = CaptureScan,
                                    focusedTextColor = CaptureInk,
                                    unfocusedTextColor = CaptureInk
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { showManual = false },
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CaptureInk),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) { Text("Volver", fontWeight = FontWeight.Bold) }
                                Button(
                                    onClick = { manual.trim().takeIf(String::isNotBlank)?.let { onConfirmValue(mode, it) } },
                                    enabled = manual.isNotBlank(),
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CaptureMint, contentColor = Color.White)
                                ) { Text("Usar", fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                            }
                        }

                        locked != null -> {
                            if (lockedCandidates.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    lockedCandidates.forEach { cand ->
                                        CandidateChip(
                                            value = cand.value,
                                            selected = cand.value == locked,
                                            onClick = { locked = cand.value }
                                        )
                                    }
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { resetScan() },
                                    modifier = Modifier
                                        .weight(0.38f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CaptureInk),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(7.dp))
                                    Text("Reintentar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Button(
                                    onClick = { locked?.let { onConfirmValue(mode, it) } },
                                    modifier = Modifier
                                        .weight(0.62f)
                                        .height(52.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = CaptureMint, contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Usar ${locked.orEmpty()}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            AnalizarIaLink(onClick = { takePhotoAndAnalyze() })
                        }

                        else -> {
                            if (liveCandidates.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Leyendo:", color = CaptureMuted, fontSize = 12.sp)
                                    liveCandidates.forEach { cand ->
                                        CandidateChip(
                                            value = cand.value,
                                            selected = false,
                                            onClick = { onConfirmValue(mode, cand.value) }
                                        )
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (secondsElapsed >= 4) {
                                    Button(
                                        onClick = { takePhotoAndAnalyze() },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = CaptureMint, contentColor = Color.White)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color.White)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Tomar Foto para Análisis IA", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { showManual = true },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CaptureInk),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Escribir a mano", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAnalysisDialog) {
            val effectivePhase = if (isCapturing) CasetaEntradaOcrPhase.Captured else ocrState.phase
            AiVisionAnalysisOverlay(
                imagePath = capturedPhotoPath,
                ocrState = ocrState.copy(phase = effectivePhase),
                finishedMessage = analysisFinishedMessage,
                onDismiss = {
                    showAnalysisDialog = false
                    onDismissOcr()
                }
            )
        }
    }
}

@Composable
private fun LiveModeTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) CaptureScan else Color.Transparent
    ) {
        Text(
            text = label,
            color = if (selected) CameraBg else CaptureInk.copy(alpha = 0.84f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun CandidateChip(
    value: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) CaptureScan else CapturePanelSoft,
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Text(
            text = value,
            color = if (selected) Color.White else CaptureInk,
            fontFamily = MonoFamily,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun AnalizarIaLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = CaptureScan,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Analizar con IA",
            color = CaptureScan,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Auto-lock por votación dentro de una ventana de tiempo. Pensado para tunear en
 * hardware: ML Kit "tiembla" (52094 / 5209A / 52O94), así que no basta repetir el
 * mismo texto — se cuenta cuál gana y por cuánta ventaja.
 *
 * Reglas:
 *  - Candidato de ALTA confianza del ranker → fija con [strongVotes] lecturas.
 *  - Candidato normal → requiere [minVotes] lecturas.
 *  - El ganador debe superar al segundo por al menos [lead] (evita rebote entre
 *    dos lecturas parecidas y misreads).
 *
 * Tuning en dispositivo:
 *  - Fija de más / con valores equivocados → sube [minVotes] o [lead].
 *  - Tarda en fijar (poca luz, lejos) → baja [minVotes] o sube [windowMs].
 */
private class StabilityLock(
    private val windowMs: Long = 1500L,
    private val minVotes: Int = 3,
    private val strongVotes: Int = 2,
    private val strongConfidence: Double = 0.80,
    private val lead: Int = 1
) {
    private data class Obs(val value: String, val confidence: Double, val time: Long)

    private val window = ArrayDeque<Obs>()

    fun offer(value: String, confidence: Double?, now: Long): String? {
        if (value.isBlank()) return null
        window.addLast(Obs(value, confidence ?: 0.0, now))
        while (window.isNotEmpty() && now - window.first().time > windowMs) {
            window.removeFirst()
        }

        val counts = window.groupingBy { it.value }.eachCount()
        val ranked = counts.entries.sortedByDescending { it.value }
        val top = ranked.firstOrNull() ?: return null
        val runnerUp = ranked.getOrNull(1)?.value ?: 0

        val topConfidence = window.filter { it.value == top.key }.maxOf { it.confidence }
        val needed = if (topConfidence >= strongConfidence) strongVotes else minVotes

        return if (top.value >= needed && top.value - runnerUp >= lead) top.key else null
    }

    fun reset() {
        window.clear()
    }
}

@Composable
private fun AnimatedTextLockView(
    text: String,
    modifier: Modifier = Modifier
) {
    var animStart by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        animStart = true
    }

    val scale by animateFloatAsState(
        targetValue = if (animStart) 1.0f else 1.22f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "textScale"
    )
    val opacity by animateFloatAsState(
        targetValue = if (animStart) 1.0f else 0.0f,
        animationSpec = tween(280),
        label = "textOpacity"
    )

    val pulseTransition = rememberInfiniteTransition(label = "pulseGlow")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = opacity
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val w = size.width
            val h = size.height
            val strokePx = 2.dp.toPx()
            val lenPx = 8.dp.toPx()
            val borderClr = Color.White.copy(alpha = 0.16f)
            val cornerClr = CaptureScan.copy(alpha = pulseAlpha)

            // Outline
            drawRoundRect(
                color = borderClr,
                topLeft = Offset.Zero,
                size = size,
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // L-Brackets
            // Top-Left
            drawLine(cornerClr, Offset(0f, 0f), Offset(lenPx, 0f), strokePx)
            drawLine(cornerClr, Offset(0f, 0f), Offset(0f, lenPx), strokePx)
            // Top-Right
            drawLine(cornerClr, Offset(w, 0f), Offset(w - lenPx, 0f), strokePx)
            drawLine(cornerClr, Offset(w, 0f), Offset(w, lenPx), strokePx)
            // Bottom-Left
            drawLine(cornerClr, Offset(0f, h), Offset(lenPx, h), strokePx)
            drawLine(cornerClr, Offset(0f, h), Offset(0f, h - lenPx), strokePx)
            // Bottom-Right
            drawLine(cornerClr, Offset(w, h), Offset(w - lenPx, h), strokePx)
            drawLine(cornerClr, Offset(w, h), Offset(w, h - lenPx), strokePx)
        }

        Surface(
            color = Color.Black.copy(alpha = 0.85f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.padding(2.dp)
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MonoFamily,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }
    }
}

