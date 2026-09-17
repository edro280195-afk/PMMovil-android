package com.example.pmmovil.ui.scanner

import android.annotation.SuppressLint
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.compose.runtime.collectAsState
import com.example.pmmovil.ui.camera.CameraErrorView
import com.example.pmmovil.ui.camera.CameraOwner
import com.example.pmmovil.ui.camera.CameraWarmupCurtain
import com.example.pmmovil.ui.camera.CameraWarmupMonitor
import com.example.pmmovil.ui.camera.rememberCameraRetry

/**
 * Rectangulo del visor en fracciones (0..1) de la `PreviewView`.
 * Se anima entre el encuadre de reposo y el codigo detectado.
 */
internal data class ScannerFrameRect(
    val centerX: Float,
    val centerY: Float,
    val width: Float,
    val height: Float
)

/**
 * Lectura pendiente de entregar. Lleva un sello unico para que el efecto de
 * confirmacion se dispare aunque se lea dos veces el mismo codigo.
 */
private data class PendingScan(val value: String, val stamp: Long)

private data class VisibleBarcodeCandidate(
    val frame: ScannerFrameRect,
    val hitFrame: ScannerFrameRect,
    val hasDecodedValue: Boolean,
    val value: String?
)

/** Nombre con el que esta vista pide la camara al [CameraOwner]. */
private const val TAG_ESCANER = "escaner"

/**
 * Rango de FPS que se le pide a la exposicion automatica en los escaneres.
 * El HAL de la Armor Pad Pro usa [5,30] por defecto: con poca luz baja a 5 fps
 * (200 ms de exposicion) y cualquier movimiento de la mano emborrona el codigo.
 * `null` deja el valor del equipo.
 */
private val SCANNER_AE_TARGET_FPS_RANGE: Range<Int>? = null

@OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("UnsafeOptInUsageError")
private fun applyScannerCaptureOptions(builder: Preview.Builder) {
    val fpsRange = SCANNER_AE_TARGET_FPS_RANGE ?: return
    Camera2Interop.Extender(builder)
        .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
}

private const val BARCODE_SELECTION_TAG = "PMM_BARCODE_SELECTION"

/**
 * Decide si la vista debe congelarse para que el operador elija un codigo.
 *
 * Despues de confirmar una seleccion, [selectedCandidateValue] sigue lleno
 * mientras el analizador espera las lecturas consecutivas del codigo elegido.
 * Sin esta condicion el siguiente frame, que todavia puede contener varios
 * codigos, volvia a congelar la camara antes de entregar la lectura.
 */
internal fun shouldFreezeForCandidateSelection(
    requireCandidateSelectionForMultiple: Boolean,
    decodedCandidateCount: Int,
    selectionFrozen: Boolean,
    selectedCandidateValue: String?
): Boolean = requireCandidateSelectionForMultiple &&
    decodedCandidateCount > 1 &&
    !selectionFrozen &&
    selectedCandidateValue == null

/** Peticion de zoom. `smooth = false` salta de golpe (pellizco). */
private data class ZoomRequest(val ratio: Float, val smooth: Boolean)

/**
 * Zoom realmente aplicado a la camara.
 *
 * Deliberadamente fuera del estado de Compose: la animacion lo escribe en
 * cada frame y el hilo del analyzer lo lee varias veces por segundo. Como
 * estado de Compose recompondria la pantalla entera 60 veces por segundo
 * mientras el zoom se mueve, que es justo cuando menos se puede perder.
 */
private class ZoomTracker(initial: Float) {
    @Volatile
    var ratio: Float = initial

    /** Hay una animacion de zoom en curso. */
    @Volatile
    var animating: Boolean = false

    /** Cuando termino el ultimo movimiento de zoom. */
    @Volatile
    var settledAt: Long = 0L
}

/**
 * Control de repeticion del autofocus. Fuera de Compose por el mismo motivo
 * que [ZoomTracker]: se escribe desde el hilo del analyzer y no lo dibuja
 * nadie. [lastX] y [lastY] estan en fracciones de la **vista**, no de la
 * imagen, porque es en la vista donde se mide si el codigo se movio.
 */
private class FocusTracker {
    @Volatile
    var lastAt: Long = 0L

    @Volatile
    var lastX: Float = 0.5f

    @Volatile
    var lastY: Float = 0.5f
}

/**
 * Convierte la posicion del codigo (fracciones de la imagen enderezada) a
 * fracciones de la `PreviewView`. La vista usa FILL_CENTER, es decir escala
 * la imagen hasta cubrirla y recorta el eje sobrante: sin compensar ese
 * recorte el marco perseguiria al codigo con un desfase creciente hacia los
 * bordes.
 */
internal fun BarcodeVisualCandidate.toViewFrame(
    viewWidth: Float,
    viewHeight: Float,
    framePadding: Float = FRAME_LOCK_PADDING,
    minimumSideFraction: Float = FRAME_LOCK_MIN_SIZE
): ScannerFrameRect? {
    if (viewWidth <= 0f || viewHeight <= 0f || sourceAspectRatio <= 0f) return null

    // Tamano con el que la imagen queda dibujada dentro de la vista.
    val scale = maxOf(viewWidth / sourceAspectRatio, viewHeight)
    val drawnHeight = scale
    val drawnWidth = scale * sourceAspectRatio
    val offsetX = (viewWidth - drawnWidth) / 2f
    val offsetY = (viewHeight - drawnHeight) / 2f

    val padding = 1f + framePadding.coerceAtLeast(0f)
    // El tamano se calcula en pixeles, no en fracciones de cada eje: una
    // fraccion del ancho y la misma fraccion del alto son medidas distintas
    // en una pantalla alargada, y aplicar ahi el minimo convertia cualquier
    // codigo pequeno en un rectangulo vertical.
    var widthPx = widthRatio * drawnWidth * padding
    var heightPx = heightRatio * drawnHeight * padding

    val minSidePx = minOf(viewWidth, viewHeight) * minimumSideFraction.coerceAtLeast(0f)
    val smallestSide = minOf(widthPx, heightPx)
    if (smallestSide > 0f && smallestSide < minSidePx) {
        // Se agranda proporcionalmente para respetar la forma real del codigo.
        val boost = minSidePx / smallestSide
        widthPx *= boost
        heightPx *= boost
    }

    return ScannerFrameRect(
        centerX = (offsetX + centerX * drawnWidth) / viewWidth,
        centerY = (offsetY + centerY * drawnHeight) / viewHeight,
        width = (widthPx / viewWidth).coerceIn(0f, 1f),
        height = (heightPx / viewHeight).coerceIn(0f, 1f)
    )
}

/**
 * Marco visual preciso para la captura congelada. El marco dibujado debe
 * seguir el bounding box real; la accesibilidad táctil se resuelve aparte con
 * [ScannerFrameRect.expandedForTouch].
 */
private fun BarcodeVisualCandidate.toPreciseViewFrame(
    viewWidth: Float,
    viewHeight: Float
): ScannerFrameRect? = toViewFrame(
    viewWidth = viewWidth,
    viewHeight = viewHeight,
    framePadding = 0.08f,
    minimumSideFraction = 0f
)

private fun ScannerFrameRect.expandedForTouch(
    minimumSideFraction: Float = 0.12f
): ScannerFrameRect {
    val minimumSide = minimumSideFraction.coerceIn(0f, 0.45f)
    val targetSide = maxOf(minOf(width, height), minimumSide)
    val expandedWidth = maxOf(width, targetSide).coerceAtMost(0.94f)
    val expandedHeight = maxOf(height, targetSide).coerceAtMost(0.94f)
    return copy(
        width = expandedWidth,
        height = expandedHeight,
        centerX = centerX.coerceIn(expandedWidth / 2f, 1f - expandedWidth / 2f),
        centerY = centerY.coerceIn(expandedHeight / 2f, 1f - expandedHeight / 2f)
    )
}

/** Holgura que se deja alrededor del codigo al cerrar el marco sobre el. */
private const val FRAME_LOCK_PADDING = 0.34f

/** El marco nunca se encoge por debajo de esto (fraccion de la vista). */
internal const val FRAME_LOCK_MIN_SIZE = 0.20f

/** Espera minima entre dos peticiones de autofocus. */
private const val FOCUS_MIN_INTERVAL_MS = 900L

/** Cuanto se tiene que mover el codigo (0..1) para justificar un reenfoque. */
private const val FOCUS_MOVE_THRESHOLD = 0.07f

/** Reenfoque de cortesia cuando el codigo lleva rato quieto y no se lee. */
private const val FOCUS_IDLE_REFRESH_MS = 2_000L

/** Espera entre reenfoques de rescate (sin codigo a la vista). */
private const val FOCUS_IDLE_INTERVAL_MS = 1_600L

/**
 * Cuanto se queda bloqueado el AF sobre el punto pedido antes de devolver el
 * control al enfoque continuo de la camara. Corto a proposito: el operador
 * mueve el equipo constantemente y un bloqueo largo deja la imagen enfocada
 * donde ya no esta el codigo.
 */
private const val FOCUS_AUTO_CANCEL_SECONDS = 2L

/**
 * Rigidez del resorte que mueve el zoom. Criticamente amortiguado (sin
 * rebote) y deliberadamente moderado: el recorrido se completa con suavidad
 * para no cambiar el encuadre antes de que ML Kit confirme el candidato.
 */
private const val ZOOM_STIFFNESS = 170f
private const val ZOOM_DAMPING = 1f

/**
 * Umbral de reposo del resorte, en espacio logaritmico: 0,002 son dos
 * milesimas de zoom relativo, imperceptibles.
 */
private const val ZOOM_VISIBILITY_THRESHOLD = 0.002f

/** Diferencia relativa por debajo de la cual no se molesta a la camara. */
private const val ZOOM_APPLY_EPSILON = 0.004f

/**
 * El HAL MediaTek de la tableta no tolera una rafaga de repeating requests.
 * Cada `setZoomRatio` reconfigura la peticion de captura, por lo que el zoom
 * se limita a unas pocas actualizaciones por segundo.
 */
private const val ZOOM_MIN_INTERVAL_MS = 120L

/** Cadencia con la que se vencen los plazos del arranque de camara. */
private const val WARMUP_TICK_MS = 200L

/** Peticion tan cercana al zoom actual que no merece animarse. */
private const val ZOOM_SNAP_EPSILON = 0.008f

/**
 * Margen tras terminar de mover el zoom antes de volver a medir el codigo.
 * El lazo del auto-zoom compara el tamano medido contra el zoom al que se
 * capturo el frame: medir a mitad de la animacion realimenta datos viejos y
 * hace oscilar la camara.
 */
private const val ZOOM_SETTLE_MS = 260L

/** Cadencia del tick que atiende el zoom y el enfoque cuando no se ve nada. */
private const val AUTO_ZOOM_TICK_MS = 300L

/** Un candidato mas reciente que esto sigue mandando sobre el tick. */
private const val CANDIDATE_FRESH_MS = 500L

/**
 * Cuanto dura el "enganche" verde tras leer un codigo.
 *
 * En un equipo lento (Moto G6) pasan varios frames entre que ML Kit ve el
 * codigo y lo decodifica, asi que el marco tiene tiempo de viajar hasta el.
 * En un Pixel eso ocurre casi en el mismo frame y el movimiento resultaba
 * imperceptible. Este tiempo minimo garantizado hace que el feedback se vea
 * igual en cualquier dispositivo, porque ya no depende de lo que tarde el
 * hardware en decodificar.
 */
private const val CAPTURE_FEEDBACK_MS = 420L

/**
 * Distancia de trabajo esperada. Solo cambia la resolucion del analisis,
 * que es lo que determina a que distancia se puede leer un codigo: ML Kit
 * necesita 2 pixeles por modulo, asi que a 1080p se lee desde una distancia
 * ~1.5x mayor que a 720p, a costa de mas trabajo por frame.
 */
enum class ScanDistance {
    /** Codigo en la mano (gafete, etiqueta de cerca). Prioriza fluidez. */
    Near,

    /** Etiquetas de bodega, cajas en estanteria. Prioriza alcance. */
    Far
}

@Composable
fun SmartBarcodeCameraView(
    onBarcodeScanned: (String) -> Unit,
    onPotentialBarcodes: ((List<BarcodeVisualCandidate>) -> Unit)? = null,
    onCandidateSelectionRequired: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    title: String = "Escanear codigo",
    subtitle: String = "Centra la etiqueta dentro del visor",
    barcodeFormats: Int = Barcode.FORMAT_ALL_FORMATS,
    continuous: Boolean = false,
    scanCooldownMillis: Long = 0L,
    minConsecutiveReads: Int = 1,
    requireCandidateSelectionForMultiple: Boolean = false,
    isPaused: Boolean = false,
    pausedMessage: String = "Validando acceso...",
    showClose: Boolean = true,
    onClose: (() -> Unit)? = null,
    compact: Boolean = false,
    showStatusOverlay: Boolean = true,
    startZoomRatio: Float = 1f,
    scanDistance: ScanDistance = ScanDistance.Far
) {
    val context = LocalContext.current
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

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Surface(
        modifier = modifier,
        shape = if (compact) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp),
        color = Color.Black,
        border = if (compact) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
        } else {
            null
        }
    ) {
        if (!hasPermission) {
            CameraPermissionView(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // La pausa ("Validando gafete...") se pinta ENCIMA de la camara,
            // sin desmontarla. Antes sustituia el contenido: eso soltaba la
            // camara y, si el gafete se rechazaba, habia que reabrirla y
            // esperar otra vez el arranque completo.
            Box(modifier = Modifier.fillMaxSize()) {
                SmartBarcodeCameraContent(
                    onBarcodeScanned = onBarcodeScanned,
                    onPotentialBarcodes = onPotentialBarcodes,
                    onCandidateSelectionRequired = onCandidateSelectionRequired,
                    title = title,
                    subtitle = subtitle,
                    barcodeFormats = barcodeFormats,
                    continuous = continuous,
                    scanCooldownMillis = scanCooldownMillis,
                    minConsecutiveReads = minConsecutiveReads,
                    requireCandidateSelectionForMultiple = requireCandidateSelectionForMultiple,
                    showClose = showClose,
                    onClose = onClose,
                    compact = compact,
                    showStatusOverlay = showStatusOverlay,
                    startZoomRatio = startZoomRatio,
                    scanDistance = scanDistance,
                    isPaused = isPaused,
                    modifier = Modifier.fillMaxSize()
                )
                if (isPaused) {
                    CameraPausedView(
                        message = pausedMessage,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartBarcodeCameraContent(
    onBarcodeScanned: (String) -> Unit,
    onPotentialBarcodes: ((List<BarcodeVisualCandidate>) -> Unit)?,
    onCandidateSelectionRequired: ((Int) -> Unit)?,
    title: String,
    subtitle: String,
    barcodeFormats: Int,
    continuous: Boolean,
    scanCooldownMillis: Long,
    minConsecutiveReads: Int,
    requireCandidateSelectionForMultiple: Boolean,
    showClose: Boolean,
    onClose: (() -> Unit)?,
    compact: Boolean,
    showStatusOverlay: Boolean,
    startZoomRatio: Float,
    scanDistance: ScanDistance,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnBarcodeScanned = rememberUpdatedState(onBarcodeScanned)
    val currentOnPotentialBarcodes = rememberUpdatedState(onPotentialBarcodes)
    val currentOnCandidateSelectionRequired = rememberUpdatedState(onCandidateSelectionRequired)
    val currentCooldownMillis = rememberUpdatedState(scanCooldownMillis)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val cameraViewSize = remember { AtomicReference(0 to 0) }
    val isDisposed = remember { AtomicBoolean(false) }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var imageAnalysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }

    // La vista se crea AQUI, en la composicion, y no en el factory del
    // AndroidView. Antes era un estado que el factory rellenaba despues, y el
    // efecto que enlazaba la camara estaba keyeado en ese estado: Compose lo
    // lanzaba con la vista en null, el factory la ponia, y el cambio de key
    // cancelaba el primer enlace (ya con la camara abierta) para lanzar un
    // segundo. Medido en la Armor Pad Pro: cada apertura costaba DOS
    // aperturas del HAL, un segundo entero de espera y -- porque el finally
    // del intento cancelado apagaba el executor del analizador -- un lector
    // que no leia nada.
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // SurfaceView usa el camino de composicion de bajo costo del
            // dispositivo. TextureView obligaba a copiar cada cuadro por GPU
            // y en la tablet provocaba frames de 800-900 ms y errores del HAL.
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var frameState by remember { mutableStateOf(FrameState.Searching) }
    var autoZoomPhase by remember { mutableStateOf(AutoZoomPhase.Idle) }
    var hasCandidate by remember { mutableStateOf(false) }
    var visibleCandidates by remember { mutableStateOf<List<VisibleBarcodeCandidate>>(emptyList()) }
    var lastCandidatesUpdateAt by remember { mutableStateOf(0L) }
    var selectionFrozen by remember { mutableStateOf(false) }
    var frozenCandidateCount by remember { mutableStateOf(0) }
    var frozenPreview by remember { mutableStateOf<ImageBitmap?>(null) }
    var lastCandidateAt by remember { mutableStateOf(0L) }
    var isWaitingNextScan by remember { mutableStateOf(false) }
    var cooldownStartedAt by remember { mutableStateOf(0L) }
    var lockedFrame by remember { mutableStateOf<ScannerFrameRect?>(null) }
    var isCaptured by remember { mutableStateOf(false) }
    var pendingScan by remember { mutableStateOf<PendingScan?>(null) }
    // Zoom que se muestra en los botones 1x/2x/4x. Es el zoom *pedido*, no el
    // instantaneo de la animacion: refrescarlo en cada frame recompondria toda
    // la pantalla 60 veces por segundo mientras el zoom se mueve.
    var displayZoomRatio by remember { mutableFloatStateOf(startZoomRatio) }

    // Estado del zoom y del enfoque fuera de Compose: lo escriben el hilo del
    // analyzer y el tick varias veces por segundo, y no lo dibuja nadie.
    val zoomTracker = remember { ZoomTracker(startZoomRatio) }
    val focusTracker = remember { FocusTracker() }
    val autoZoom = remember { AutoZoomController() }

    // Arranque de la camara: hasta que la exposicion automatica converge no se
    // manda enfoque, medicion ni zoom, y el visor queda tapado. En la Armor Pad
    // Pro (MediaTek) un AF/medicion en ese lapso congelaba la exposicion y la
    // imagen salia 2 s en negro y 2 s en blanco. Ver CameraWarmupMonitor.
    // requireLuminance: el HAL llega a reportar "AE convergida" con el cuadro
    // todavia quemado; la cortina no se levanta hasta que el analizador haya
    // visto un cuadro con luz normal.
    val warmup = remember { CameraWarmupMonitor("escaner", requireLuminance = true) }
    val warmupReady by warmup.isReady.collectAsState()
    // Reintenta sola hasta agotar los intentos automáticos; después se rinde
    // con un botón de reintentar. Ver CameraRetry.kt.
    val cameraRetry = rememberCameraRetry(warmup)

    // Espacio logaritmico: el zoom se percibe como cambio proporcional, asi
    // que interpolar el logaritmo da una velocidad aparente constante en todo
    // el recorrido. Interpolando el ratio a secas, 1x->2x se ve lento y
    // 3x->4x se ve brusco.
    val zoomAnimator = remember {
        Animatable(ln(startZoomRatio.coerceAtLeast(1f)), ZOOM_VISIBILITY_THRESHOLD)
    }
    // Una sola cola de peticiones para todas las fuentes de zoom (auto,
    // botones, pellizco, reset). replay = 1 para que la peticion inicial que
    // se emite al enlazar la camara no se pierda si el colector aun no
    // arranco.
    val zoomRequests = remember {
        MutableSharedFlow<ZoomRequest>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
    }

    LaunchedEffect(startZoomRatio) { autoZoom.restingZoom = startZoomRatio }

    /**
     * Pide un zoom. `smooth = false` solo para el pellizco, que es
     * manipulacion directa: cualquier suavizado ahi se siente como retraso
     * bajo el dedo.
     */
    fun requestZoom(targetZoom: Float, smooth: Boolean = true) {
        val boundCamera = camera ?: return
        val zoomState = boundCamera.cameraInfo.zoomState.value
        val goal = targetZoom.coerceIn(
            zoomState?.minZoomRatio ?: 1f,
            zoomState?.maxZoomRatio ?: 1f
        )
        displayZoomRatio = goal
        zoomRequests.tryEmit(ZoomRequest(goal, smooth))
    }

    /** El operador movio el zoom a mano: manda el durante unos segundos. */
    fun takeManualZoom(targetZoom: Float, smooth: Boolean = true) {
        autoZoom.onManualZoom(System.currentTimeMillis())
        autoZoomPhase = AutoZoomPhase.Idle
        requestZoom(targetZoom, smooth)
    }

    /**
     * Solo enfoque (FLAG_AF). Antes se pedian tambien regiones de AE y AWB,
     * y en el HAL de MediaTek de la Armor Pad Pro cada region de AE dispara
     * un "TouchAE" que congela la exposicion mientras corre el autofocus:
     * cada reenfoque se veia como un parpadeo de brillo. Para leer un codigo
     * la exposicion global de la escena es la correcta; lo que hace falta es
     * el foco, no la medicion.
     */
    fun focusAction(point: MeteringPoint): FocusMeteringAction =
        FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
            .setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS)
            .build()

    /**
     * Reenfoca sobre el codigo detectado. El AF solo se relanza si el codigo
     * se movio de verdad y ya paso [FOCUS_MIN_INTERVAL_MS] desde el ultimo
     * intento: con umbrales mas bajos el pulso de la mano relanzaba el
     * autofocus antes de que convergiera y la imagen nunca terminaba de
     * asentarse. Si ML Kit ya decodifico el valor no hay nada que enfocar.
     *
     * El punto se toma del marco **ya traducido a la vista**, no de la
     * posicion cruda en la imagen: la `PreviewView` recorta con FILL_CENTER,
     * asi que sin esa correccion el AF media desplazado en horizontal, cada
     * vez mas cuanto mas lejos del centro estuviera el codigo.
     */
    fun requestFocus(
        candidate: BarcodeVisualCandidate,
        frame: ScannerFrameRect,
        now: Long
    ) {
        if (candidate.hasDecodedValue) return
        if (!warmup.isReady.value) return
        val boundCamera = camera ?: return
        val view = previewView
        if (view.width <= 0 || view.height <= 0) return

        if (now - focusTracker.lastAt < FOCUS_MIN_INTERVAL_MS) return
        val movedEnough = abs(frame.centerX - focusTracker.lastX) > FOCUS_MOVE_THRESHOLD ||
            abs(frame.centerY - focusTracker.lastY) > FOCUS_MOVE_THRESHOLD
        if (!movedEnough && now - focusTracker.lastAt < FOCUS_IDLE_REFRESH_MS) return

        val x = (frame.centerX * view.width).coerceIn(0f, view.width.toFloat())
        val y = (frame.centerY * view.height).coerceIn(0f, view.height.toFloat())
        boundCamera.cameraControl.startFocusAndMetering(
            focusAction(view.meteringPointFactory.createPoint(x, y))
        )
        focusTracker.lastAt = now
        focusTracker.lastX = frame.centerX
        focusTracker.lastY = frame.centerY
    }

    /**
     * Reenfoque de rescate al centro cuando no se ve ningun codigo y la
     * imagen esta borrosa.
     *
     * Sin esto habia un punto muerto: el AF solo se pedia sobre un codigo
     * detectado, pero una imagen desenfocada no produce ninguna deteccion,
     * asi que la camara podia quedarse borrosa indefinidamente.
     *
     * Ya NO se reenfoca al terminar cada movimiento de zoom: el zoom de esta
     * camara es digital (recorte del sensor) y no mueve el plano de enfoque.
     * Ese reenfoque solo servia para disparar un AF en cada escalon del
     * barrido de busqueda, y en el HAL de MediaTek cada AF congela la
     * exposicion casi un segundo.
     */
    fun refocusWhileIdle(now: Long) {
        if (!warmup.isReady.value) return
        val boundCamera = camera ?: return
        val view = previewView
        if (view.width <= 0 || view.height <= 0) return
        if (frameState != FrameState.Blurry) return
        if (now - focusTracker.lastAt < FOCUS_IDLE_INTERVAL_MS) return

        boundCamera.cameraControl.startFocusAndMetering(
            focusAction(
                view.meteringPointFactory.createPoint(view.width / 2f, view.height / 2f)
            )
        )
        focusTracker.lastAt = now
        focusTracker.lastX = 0.5f
        focusTracker.lastY = 0.5f
    }

    // El callback de lectura necesita pausar su propio analyzer, que todavia
    // no existe cuando se construye la lambda: se resuelve por referencia.
    val analyzerRef = remember { AtomicReference<BarcodeAnalyzer?>(null) }
    var selectedCandidateValue by remember { mutableStateOf<String?>(null) }
    val frozenPreviewScale by animateFloatAsState(
        targetValue = if (selectionFrozen) 1.04f else 1f,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "frozen-preview-zoom"
    )
    val freezeProgress by animateFloatAsState(
        targetValue = if (selectionFrozen) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "freeze-progress"
    )
    val analyzer = remember(
        barcodeFormats,
        continuous,
        minConsecutiveReads,
        requireCandidateSelectionForMultiple
    ) {
        BarcodeAnalyzer(
            barcodeFormats = barcodeFormats,
            continuous = continuous,
            repeatCooldownMillis = 950L,
            minConsecutiveReads = minConsecutiveReads,
            requireCandidateSelectionForMultiple = requireCandidateSelectionForMultiple,
            onPotentialBarcodes = { candidates ->
                val now = System.currentTimeMillis()
                val measuredSize = cameraViewSize.get()
                val viewWidth = previewView.width.toFloat()
                    .takeIf { it > 0f }
                    ?: measuredSize.first.toFloat()
                val viewHeight = previewView.height.toFloat()
                    .takeIf { it > 0f }
                    ?: measuredSize.second.toFloat()
                val decodedCount = candidates.count { !it.value.isNullOrBlank() }
                val wasSelectionFrozen = selectionFrozen
                if (
                    shouldFreezeForCandidateSelection(
                        requireCandidateSelectionForMultiple = requireCandidateSelectionForMultiple,
                        decodedCandidateCount = decodedCount,
                        selectionFrozen = selectionFrozen,
                        selectedCandidateValue = selectedCandidateValue
                    )
                ) {
                    selectionFrozen = true
                    frozenCandidateCount = decodedCount
                    autoZoom.onManualZoom(now)
                    analyzerRef.get()?.pauseScanning()
                    Log.d(
                        BARCODE_SELECTION_TAG,
                        "freeze detected=$decodedCount view=${viewWidth.toInt()}x${viewHeight.toInt()}"
                    )
                    ContextCompat.getMainExecutor(context).execute {
                        currentOnCandidateSelectionRequired.value?.invoke(decodedCount)
                    }
                    frozenPreview = runCatching {
                        previewView.bitmap
                            ?.takeIf { it.width > 0 && it.height > 0 }
                            ?.asImageBitmap()
                    }.getOrNull()
                }
                val freezeJustStarted = !wasSelectionFrozen && selectionFrozen
                if (
                    freezeJustStarted ||
                    (!wasSelectionFrozen && (now - lastCandidatesUpdateAt >= 120L || candidates.isEmpty()))
                ) {
                    val mappedCandidates = candidates.mapNotNull { candidate ->
                        candidate.toPreciseViewFrame(viewWidth, viewHeight)?.let { frame ->
                            VisibleBarcodeCandidate(
                                frame = frame,
                                hitFrame = frame.expandedForTouch(),
                                hasDecodedValue = candidate.hasDecodedValue,
                                value = candidate.value
                            )
                        }
                    }
                    visibleCandidates = mappedCandidates
                    if (decodedCount > 1) {
                        Log.d(
                            BARCODE_SELECTION_TAG,
                            "freeze mapped=${mappedCandidates.size} decoded=$decodedCount"
                        )
                    }
                    mappedCandidates.firstOrNull {
                        it.value == selectedCandidateValue
                    }?.let { selected ->
                        lockedFrame = selected.frame
                    }
                    lastCandidatesUpdateAt = now
                }
                if (!selectionFrozen && selectedCandidateValue != null && candidates.none {
                        it.value == selectedCandidateValue
                    }) {
                    selectedCandidateValue = null
                    analyzerRef.get()?.clearCandidateSelection()
                }
                currentOnPotentialBarcodes.value?.invoke(candidates)
            },
            onPotentialBarcode = { candidate ->
                val now = System.currentTimeMillis()
                hasCandidate = true
                lastCandidateAt = now
                val frame = candidate.toPreciseViewFrame(
                    viewWidth = previewView.width.toFloat(),
                    viewHeight = previewView.height.toFloat()
                )
                if (selectedCandidateValue == null || candidate.value == selectedCandidateValue) {
                    lockedFrame = frame
                    if (frame != null) requestFocus(candidate, frame, now)
                }

                // Lazo cerrado sobre el tamano real del codigo. Solo se
                // corrige con el zoom ya asentado: el tamano medido solo
                // significa algo junto al zoom al que se capturo el frame.
                val canAdjust = warmup.isReady.value &&
                    !zoomTracker.animating &&
                    now - zoomTracker.settledAt >= ZOOM_SETTLE_MS
                val target = autoZoom.onCandidate(
                    now = now,
                    currentZoom = zoomTracker.ratio,
                    sizeFraction = candidate.minSideFraction,
                    decoded = candidate.hasDecodedValue,
                    canAdjust = canAdjust
                )
                autoZoomPhase = autoZoom.phase
                if (target != null) requestZoom(target)
            },
            onBarcodeScanned = { barcode ->
                // El cooldown se aplica pausando el analyzer, no descartando
                // aqui la lectura: filtrarla en la UI dejaba el codigo ya
                // registrado como "leido" dentro del analyzer y el siguiente
                // codigo distinto se perdia en silencio.
                val now = System.currentTimeMillis()
                val cooldown = currentCooldownMillis.value
                if (cooldown > 0L) {
                    analyzerRef.get()?.pauseScanning()
                    isWaitingNextScan = true
                    cooldownStartedAt = now
                }

                // En disparo unico la pantalla se cierra, asi que se devuelve
                // el zoom al reposo para que la proxima apertura arranque
                // limpia. En continuo se conserva: la siguiente caja suele
                // estar a la misma distancia y volver al reposo obligaria a
                // reconquistar el zoom en cada lectura.
                autoZoom.onScanned(now)
                autoZoomPhase = AutoZoomPhase.Idle
                if (!continuous) requestZoom(startZoomRatio)

                // El marco NO se suelta aqui: se queda enganchado al codigo
                // para que el usuario vea donde leyo. Lo libera el efecto de
                // CAPTURE_FEEDBACK_MS mas abajo.
                isCaptured = true
                pendingScan = PendingScan(barcode, System.nanoTime())
                selectedCandidateValue = null
                selectionFrozen = false
                frozenPreview = null
                frozenCandidateCount = 0
                analyzerRef.get()?.clearCandidateSelection()

                // En modo continuo el operador escanea en cadena, asi que el
                // resultado se entrega ya y el feedback corre en paralelo
                // dentro del cooldown. En disparo unico se espera a que la
                // animacion se vea, porque justo despues la pantalla se cierra.
                if (continuous) currentOnBarcodeScanned.value(barcode)
            }
        ).also(analyzerRef::set)
    }
    val frameQualityAnalyzer = remember {
        FrameQualityAnalyzer { quality ->
            val state = quality.toFrameState()
            frameState = state
            analyzer.updateFrameState(state)
            warmup.onFrameLuminance(quality.avgLuminance)
        }
    }
    val combinedAnalyzer = remember(analyzer, frameQualityAnalyzer) {
        CombinedAnalyzer(frameQualityAnalyzer, analyzer)
    }

    // Un unico coroutine posee la animacion del zoom. `collectLatest` cancela
    // la animacion en curso cuando llega una peticion nueva, y `Animatable`
    // arranca la siguiente desde el valor **y la velocidad** actuales: por eso
    // los escalones del barrido de busqueda se ven como un desplazamiento
    // continuo y no como saltos encadenados.
    LaunchedEffect(camera) {
        val boundCamera = camera ?: return@LaunchedEffect
        zoomRequests.collectLatest { request ->
            val goal = request.ratio
            if (abs(exp(zoomAnimator.value) - goal) < ZOOM_SNAP_EPSILON) {
                // Ya estamos ahi: solo se alinea el animador con la realidad.
                zoomAnimator.snapTo(ln(goal))
                zoomTracker.ratio = goal
                return@collectLatest
            }
            if (!request.smooth) {
                zoomAnimator.snapTo(ln(goal))
                if (!isDisposed.get()) boundCamera.cameraControl.setZoomRatio(goal)
                zoomTracker.ratio = goal
                zoomTracker.settledAt = System.currentTimeMillis()
                return@collectLatest
            }
            zoomTracker.animating = true
            try {
                var lastApplied = exp(zoomAnimator.value)
                var lastZoomSentAt = 0L
                zoomAnimator.animateTo(
                    targetValue = ln(goal),
                    animationSpec = spring(
                        dampingRatio = ZOOM_DAMPING,
                        stiffness = ZOOM_STIFFNESS,
                        visibilityThreshold = ZOOM_VISIBILITY_THRESHOLD
                    )
                ) {
                    val ratio = exp(value)
                    val frameNow = System.currentTimeMillis()
                    // Pedir cambios imperceptibles solo llena la cola de
                    // peticiones de la camara sin que se note nada, y mas
                    // de una peticion por cuadro tampoco aporta.
                    if (
                        !isDisposed.get() &&
                        abs(ratio - lastApplied) >= ratio * ZOOM_APPLY_EPSILON &&
                        frameNow - lastZoomSentAt >= ZOOM_MIN_INTERVAL_MS
                    ) {
                        boundCamera.cameraControl.setZoomRatio(ratio)
                        lastApplied = ratio
                        lastZoomSentAt = frameNow
                        zoomTracker.ratio = ratio
                    }
                }
                if (!isDisposed.get()) boundCamera.cameraControl.setZoomRatio(goal)
                zoomTracker.ratio = goal
            } finally {
                zoomTracker.animating = false
                zoomTracker.settledAt = System.currentTimeMillis()
            }
        }
    }

    // Tick de busqueda: mientras no se ve ningun codigo, el auto-zoom barre
    // acercando poco a poco. Es lo que ML Kit no podia hacer, porque sus
    // sugerencias solo existen sobre codigos que ya detecto: un QR lejano no
    // genera ninguna deteccion y el zoom se quedaba parado justo cuando mas
    // falta hacia. Aqui tambien vive el reenfoque de rescate.
    LaunchedEffect(camera) {
        if (camera == null) return@LaunchedEffect
        while (true) {
            delay(AUTO_ZOOM_TICK_MS)
            val now = System.currentTimeMillis()
            // Mientras la camara arranca no se barre ni se reenfoca: toda
            // orden 3A en ese lapso alarga la convergencia de la exposicion.
            if (!warmup.isReady.value) continue
            if (now - lastCandidateAt <= CANDIDATE_FRESH_MS) continue
            val target = autoZoom.onIdle(now, zoomTracker.ratio)
            autoZoomPhase = autoZoom.phase
            if (target != null) requestZoom(target)
            refocusWhileIdle(now)
        }
    }

    LaunchedEffect(lastCandidateAt) {
        if (lastCandidateAt > 0L) {
            delay(1_500L)
            if (System.currentTimeMillis() - lastCandidateAt >= 1_400L && !isCaptured) {
                hasCandidate = false
                lockedFrame = null
            }
        }
    }

    // Enganche visible tras leer: mantiene el marco sobre el codigo un tiempo
    // fijo y despues lo suelta. En disparo unico el resultado se entrega al
    // final, para que la animacion no se corte al cerrarse la pantalla.
    LaunchedEffect(pendingScan) {
        val scan = pendingScan ?: return@LaunchedEffect
        delay(CAPTURE_FEEDBACK_MS)
        isCaptured = false
        hasCandidate = false
        lockedFrame = null
        pendingScan = null
        if (!continuous) currentOnBarcodeScanned.value(scan.value)
    }

    // El regreso al zoom de reposo cuando se pierde el codigo lo decide ahora
    // el propio AutoZoomController al cerrar cada ciclo de barrido. Antes
    // habia aqui un efecto aparte que devolvia el zoom por inactividad, y con
    // la busqueda activa serian dos sistemas tirando en direcciones opuestas.

    LaunchedEffect(cooldownStartedAt) {
        if (cooldownStartedAt > 0L && scanCooldownMillis > 0L) {
            delay(scanCooldownMillis)
            isWaitingNextScan = false
            analyzer.resumeScanning()
        }
    }

    // Red de seguridad del arranque: vence los plazos del calentamiento
    // aunque el HAL no entregue cuadros. Si nunca reporta la AE convergida
    // pero si hay cuadros, la cortina se levanta sola; si no hay ni un cuadro,
    // el monitor declara la sesion muerta y CameraRetry.kt decide si reabre
    // sola o se rinde.
    LaunchedEffect(warmupReady, cameraRetry.attempt, cameraRetry.failed) {
        while (!warmupReady && !cameraRetry.failed) {
            delay(WARMUP_TICK_MS)
            warmup.tick()
        }
    }

    // Pausa (validando un gafete, por ejemplo): se deja de decodificar pero
    // la camara sigue enlazada. Al reanudar, el analizador vuelve a leer
    // aunque este en modo de disparo unico y ya haya entregado una lectura.
    LaunchedEffect(isPaused, analyzer) {
        if (isPaused) analyzer.pauseScanning() else analyzer.resumeScanning()
    }

    // Si cambian los formatos o el modo continuo se construye otro analyzer:
    // hay que reconectarlo al ImageAnalysis ya enlazado, porque `factory`
    // solo corre una vez y el use case se quedaria con el analyzer viejo.
    val appliedAnalyzer = remember { AtomicReference<CombinedAnalyzer?>(null) }
    LaunchedEffect(combinedAnalyzer, imageAnalysisUseCase) {
        val useCase = imageAnalysisUseCase ?: return@LaunchedEffect
        // El analyzer inicial ya quedo puesto al construir el use case. Volver
        // a ponerlo desactivaba y reactivaba el analisis (dos repeating
        // requests nuevos) justo mientras la camara arrancaba.
        if (appliedAnalyzer.get() === combinedAnalyzer) return@LaunchedEffect
        useCase.clearAnalyzer()
        useCase.setAnalyzer(executor, combinedAnalyzer)
        appliedAnalyzer.set(combinedAnalyzer)
    }

    // El executor y el cliente de ML Kit viven lo que vive la pantalla, no lo
    // que vive un intento de enlace: reabrir la camara no debe dejarlos
    // muertos.
    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            analyzer.close()
            executor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .onSizeChanged { size -> cameraViewSize.set(size.width to size.height) }
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom == 1f) return@detectTransformGestures
                        takeManualZoom(zoomTracker.ratio * zoom, smooth = false)
                    }
                },
            factory = { previewView }
        )

        // UN solo efecto enlaza la camara y la suelta al salir. Solo se
        // relanza para reabrir una sesion muerta (cameraRetry.attempt); nunca
        // por cambios de estado de la vista.
        LaunchedEffect(cameraRetry.attempt) {
            var imageAnalysis: ImageAnalysis? = null
            var boundCamera: Camera? = null
            try {
                if (isDisposed.get()) return@LaunchedEffect
                // Preview y analisis comparten aspecto 16:9 a proposito:
                // el marco que sigue al codigo traduce coordenadas del
                // frame analizado a la vista.
                val aspectStrategy = AspectRatioStrategy(
                    AspectRatio.RATIO_16_9,
                    AspectRatioStrategy.FALLBACK_RULE_AUTO
                )
                val preview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(aspectStrategy)
                            .build()
                    )
                    .also { builder ->
                        warmup.attachTo(builder)
                        applyScannerCaptureOptions(builder)
                    }
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                // 1280x720 conserva suficiente detalle para QR/barcode cuando
                // el auto-zoom acerca el codigo. 1920x1080 duplicaba el costo
                // de ML Kit y saturaba el HAL MediaTek sin una ganancia real
                // en campo.
                val analysisSize = Size(1280, 720)
                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(aspectStrategy)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            analysisSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(resolutionSelector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, combinedAnalyzer) }
                imageAnalysis = analysis
                imageAnalysisUseCase = analysis
                appliedAnalyzer.set(combinedAnalyzer)

                val useCases = listOf<UseCase>(preview, analysis)
                val bound = CameraOwner.bind(
                    tag = TAG_ESCANER,
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    selector = CameraSelector.DEFAULT_BACK_CAMERA,
                    useCases = useCases,
                    onBeforeBind = { warmup.onBound() },
                    onError = { error ->
                        Log.e("SmartBarcodeCamera", "Error al iniciar cámara", error)
                    }
                )
                if (bound == null) {
                    cameraRetry.forceFail()
                    return@LaunchedEffect
                }
                boundCamera = bound
                camera = bound
                val zoomState = bound.cameraInfo.zoomState.value
                autoZoom.ceilingZoom = zoomState?.maxZoomRatio ?: 1f
                autoZoom.reset()
                val targetZoom = startZoomRatio.coerceIn(
                    zoomState?.minZoomRatio ?: 1f,
                    zoomState?.maxZoomRatio ?: 1f
                )
                bound.cameraControl.setZoomRatio(targetZoom)
                zoomTracker.ratio = targetZoom
                zoomTracker.settledAt = System.currentTimeMillis()
                displayZoomRatio = targetZoom
                zoomRequests.tryEmit(ZoomRequest(targetZoom, smooth = false))
                awaitCancellation()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e("SmartBarcodeCamera", "Error al iniciar cámara", error)
                cameraRetry.forceFail()
            } finally {
                // NonCancellable: este finally corre con la corrutina ya
                // cancelada y release() suspende en un mutex; sin el
                // envoltorio, si otra pantalla estuviera pidiendo la camara
                // en ese instante, la nuestra se quedaria tomada.
                withContext(NonCancellable) { CameraOwner.release(TAG_ESCANER) }
                imageAnalysis?.clearAnalyzer()
                if (imageAnalysisUseCase === imageAnalysis) imageAnalysisUseCase = null
                if (camera === boundCamera) camera = null
            }
        }

        // Tapa los cuadros negros/quemados del arranque hasta que la
        // exposicion converge. Va debajo del degradado y de los controles.
        CameraWarmupCurtain(
            visible = !warmupReady && !cameraRetry.failed,
            compact = compact,
            modifier = Modifier.fillMaxSize()
        )

        if (cameraRetry.failed) {
            CameraErrorView(
                icon = Icons.Default.QrCodeScanner,
                title = "La cámara no respondió",
                detail = "Se intentó abrirla varias veces sin recibir imagen.",
                compact = compact,
                onRetry = cameraRetry.retry,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.24f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.76f)
                    )
                )
        )

        // La imagen congelada y sus marcos comparten la misma transformacion.
        // Antes solo crecia la imagen y los cuadros quedaban atrasados, lo
        // que hacia que la seleccion pareciera desplazada.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = frozenPreviewScale
                    scaleY = frozenPreviewScale
                }
        ) {
            frozenPreview?.let { preview ->
                Image(
                    bitmap = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (freezeProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.12f * freezeProgress))
                )
            }

        // El marco es una capa visual. Debe quedar debajo de las capas
        // interactivas para que Reanudar, la selección y los controles sigan
        // respondiendo cuando la captura está congelada.
        SmartScannerFrame(
            state = frameState,
            hasCandidate = hasCandidate && !selectionFrozen,
            autoZoomPhase = autoZoomPhase,
            isCaptured = isCaptured,
            compact = compact,
            showGuidanceOverlay = showStatusOverlay,
            lockedFrame = lockedFrame.takeUnless { selectionFrozen },
            onResetZoom = { takeManualZoom(startZoomRatio) },
            modifier = Modifier.fillMaxSize()
        )

        BarcodeCandidateOverlay(
            candidates = visibleCandidates,
            selectedValue = selectedCandidateValue,
            onCandidateSelected = { value ->
                selectedCandidateValue = value
                visibleCandidates.firstOrNull { it.value == value }?.let { selected ->
                    lockedFrame = selected.frame
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        }

        if (selectionFrozen && selectedCandidateValue == null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (compact) 56.dp else 74.dp),
                color = PmmBlueDeep.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pausa inteligente · elige uno de ${frozenCandidateCount.coerceAtLeast(visibleCandidates.count { !it.value.isNullOrBlank() })} codigos",
                        color = Color.White,
                        fontSize = if (compact) 10.sp else 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = {
                            selectedCandidateValue = null
                            selectionFrozen = false
                            frozenPreview = null
                            frozenCandidateCount = 0
                            visibleCandidates = emptyList()
                            analyzerRef.get()?.clearCandidateSelection()
                            analyzerRef.get()?.resumeScanning()
                            autoZoom.onManualZoom(System.currentTimeMillis())
                        }
                    ) {
                        Text(
                            text = "Reanudar",
                            color = PmmBlueLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (selectionFrozen && selectedCandidateValue != null) {
            CandidateSelectionSheet(
                code = selectedCandidateValue,
                candidateCount = frozenCandidateCount.coerceAtLeast(
                    visibleCandidates.count { !it.value.isNullOrBlank() }
                ),
                compact = compact,
                onConfirm = {
                    selectedCandidateValue?.let { selected ->
                        analyzerRef.get()?.selectCandidate(selected)
                        analyzerRef.get()?.resumeScanning()
                        selectionFrozen = false
                        frozenPreview = null
                        frozenCandidateCount = 0
                    }
                },
                onResume = {
                    selectedCandidateValue = null
                    selectionFrozen = false
                    frozenPreview = null
                    frozenCandidateCount = 0
                    visibleCandidates = emptyList()
                    analyzerRef.get()?.clearCandidateSelection()
                    analyzerRef.get()?.resumeScanning()
                    autoZoom.onManualZoom(System.currentTimeMillis())
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        CameraTopControls(
            title = title,
            subtitle = subtitle,
            showClose = showClose,
            onClose = onClose,
            torchEnabled = torchEnabled,
            canUseTorch = camera?.cameraInfo?.hasFlashUnit() == true,
            onToggleTorch = {
                torchEnabled = !torchEnabled
                camera?.cameraControl?.enableTorch(torchEnabled)
            },
            compact = compact,
            showText = showStatusOverlay,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        CameraBottomControls(
            status = scannerStatusText(
                frameState = frameState,
                autoZoomPhase = autoZoomPhase,
                hasCandidate = hasCandidate,
                isWaitingNextScan = isWaitingNextScan
            ),
            currentZoomRatio = displayZoomRatio,
            compact = compact,
            showStatus = showStatusOverlay,
            onZoom = { zoom -> takeManualZoom(zoom) },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        AnimatedVisibility(
            visible = isWaitingNextScan,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = PmmBlueDeep.copy(alpha = 0.94f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Text(
                        text = "Codigo capturado",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(130.dp)
                            .height(3.dp),
                        color = PmmBlueLight,
                        trackColor = Color.White.copy(alpha = 0.18f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeCandidateOverlay(
    candidates: List<VisibleBarcodeCandidate>,
    selectedValue: String?,
    onCandidateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (candidates.isEmpty()) return

    val decodedCount = candidates.count { !it.value.isNullOrBlank() }
    val selectableModifier = if (decodedCount > 1) {
        modifier.pointerInput(candidates, selectedValue) {
            detectTapGestures { offset ->
                val width = size.width.toFloat().coerceAtLeast(1f)
                val height = size.height.toFloat().coerceAtLeast(1f)
                candidates
                    .mapNotNull { candidate ->
                        if (candidate.value.isNullOrBlank()) return@mapNotNull null
                        val frame = candidate.hitFrame
                        val left = (frame.centerX - frame.width / 2f) * width
                        val top = (frame.centerY - frame.height / 2f) * height
                        val right = (frame.centerX + frame.width / 2f) * width
                        val bottom = (frame.centerY + frame.height / 2f) * height
                        if (offset.x !in left..right || offset.y !in top..bottom) {
                        return@mapNotNull null
                        }
                        val dx = offset.x - frame.centerX * width
                        val dy = offset.y - frame.centerY * height
                        (dx * dx + dy * dy) to candidate.value
                    }
                    .minByOrNull { it.first }
                    ?.second
                    ?.also { value ->
                        Log.d(BARCODE_SELECTION_TAG, "candidate tapped value=${value.take(32)}")
                    }
                    ?.let(onCandidateSelected)
            }
        }
    } else {
        modifier
    }
    BoxWithConstraints(modifier = selectableModifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            candidates.forEachIndexed { index, candidate ->
                val frame = candidate.frame
                val left = ((frame.centerX - frame.width / 2f) * size.width)
                    .coerceIn(0f, size.width)
                val top = ((frame.centerY - frame.height / 2f) * size.height)
                    .coerceIn(0f, size.height)
                val right = ((frame.centerX + frame.width / 2f) * size.width)
                    .coerceIn(left, size.width)
                val bottom = ((frame.centerY + frame.height / 2f) * size.height)
                    .coerceIn(top, size.height)
                val selected = candidate.value == selectedValue && candidate.hasDecodedValue
                val color = when {
                    selected -> PmmGreen
                    selectedValue == null && decodedCount <= 1 && index == 0 -> PmmGreen
                    selectedValue == null && decodedCount > 1 && candidate.hasDecodedValue -> PmmAmber
                    candidate.hasDecodedValue -> Color.White
                    else -> Color.White.copy(alpha = 0.46f)
                }
                if (selected) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.14f),
                        topLeft = Offset(left, top),
                        size = ComposeSize(right - left, bottom - top),
                        cornerRadius = CornerRadius(14f, 14f)
                    )
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = ComposeSize(right - left, bottom - top),
                    cornerRadius = CornerRadius(14f, 14f),
                    style = Stroke(width = if (selected) 5f else 3f)
                )
            }
        }

        val density = LocalDensity.current
        val labelHeight = with(density) { 34.dp.toPx() }
        val labelWidth = with(density) { 174.dp.toPx() }
        var decodedLabelIndex = 0
        candidates.forEachIndexed { index, candidate ->
            val value = candidate.value?.takeIf(String::isNotBlank) ?: return@forEachIndexed
            decodedLabelIndex += 1
            val frame = candidate.frame
            val left = (
                frame.centerX * constraints.maxWidth - labelWidth / 2f
            ).coerceIn(6f, constraints.maxWidth - labelWidth - 6f)
            val top = (
                (frame.centerY - frame.height / 2f) * constraints.maxHeight - labelHeight - 6f
            ).coerceAtLeast(6f)
            val badgeColor = if (candidate.value == selectedValue) PmmGreen else Color.Black
            Surface(
                modifier = Modifier.offset {
                    IntOffset(left.toInt(), top.toInt())
                },
                color = if (candidate.value == selectedValue) {
                    PmmGreen.copy(alpha = 0.94f)
                } else {
                    Color.Black.copy(alpha = 0.82f)
                },
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.42f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(shape = CircleShape, color = badgeColor.copy(alpha = 0.22f)) {
                        Text(
                            text = decodedLabelIndex.toString(),
                            color = if (candidate.value == selectedValue) PmmTextPrimary else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = value.take(14),
                        color = if (candidate.value == selectedValue) PmmTextPrimary else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateSelectionSheet(
    code: String?,
    candidateCount: Int,
    compact: Boolean,
    onConfirm: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (compact) 10.dp else 20.dp, vertical = 12.dp),
        color = Color.White.copy(alpha = 0.97f),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
    ) {
        Column(
            modifier = Modifier.padding(13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = PmmGreen.copy(alpha = 0.14f)) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PmmGreen,
                        modifier = Modifier.padding(7.dp).size(19.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Código seleccionado",
                        color = PmmTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (candidateCount > 1) {
                            "$candidateCount códigos detectados"
                        } else {
                            "Imagen capturada"
                        },
                        color = PmmTextSecondary,
                        fontSize = 9.sp
                    )
                }
                TextButton(onClick = onResume) {
                    Text(
                        text = "Elegir otro",
                        color = PmmBlueDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                text = code.orEmpty(),
                color = PmmTextPrimary,
                fontSize = if (compact) 17.sp else 21.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
            Button(
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth().height(if (compact) 43.dp else 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text("Confirmar lectura", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CameraTopControls(
    title: String,
    subtitle: String,
    showClose: Boolean,
    onClose: (() -> Unit)?,
    torchEnabled: Boolean,
    canUseTorch: Boolean,
    onToggleTorch: () -> Unit,
    compact: Boolean,
    showText: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.statusBarsPadding())
            .padding(
                horizontal = if (compact) 12.dp else 18.dp,
                vertical = if (compact) 12.dp else 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (showClose && onClose != null) {
            GlassIconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(Modifier.size(42.dp))
        }

        if (showText) {
            Surface(
                color = Color.Black.copy(alpha = 0.46f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = if (compact) 12.dp else 16.dp,
                        vertical = if (compact) 6.dp else 8.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 12.sp else 15.sp,
                        maxLines = 1
                    )
                    if (!compact) {
                        Text(
                            text = subtitle,
                            color = Color.White.copy(alpha = 0.72f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        GlassIconButton(
            enabled = canUseTorch,
            selected = torchEnabled,
            onClick = onToggleTorch
        ) {
            Icon(
                imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (torchEnabled) PmmBlueLight else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CameraBottomControls(
    status: String,
    currentZoomRatio: Float,
    compact: Boolean,
    showStatus: Boolean,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.navigationBarsPadding())
            .padding(
                horizontal = if (compact) 12.dp else 22.dp,
                vertical = if (compact) 10.dp else 20.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)
    ) {
        if (showStatus) {
            StatusPill(text = status, compact = compact)
        }
        if (!compact) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = 0.14f),
                        RoundedCornerShape(999.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuickZoomButton("1x", currentZoomRatio < 1.5f) { onZoom(1f) }
                QuickZoomButton("2x", currentZoomRatio in 1.5f..2.8f) { onZoom(2f) }
                QuickZoomButton("4x", currentZoomRatio >= 2.8f) { onZoom(4f) }
            }
        }
    }
}

@Composable
private fun SmartScannerFrame(
    state: FrameState,
    hasCandidate: Boolean,
    autoZoomPhase: AutoZoomPhase,
    isCaptured: Boolean,
    compact: Boolean,
    showGuidanceOverlay: Boolean,
    lockedFrame: ScannerFrameRect?,
    onResetZoom: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSearching = autoZoomPhase == AutoZoomPhase.Searching
    val accent by animateColorAsState(
        targetValue = when {
            isCaptured || hasCandidate -> PmmGreen
            isSearching -> PmmBlueLight
            state == FrameState.TooDark || state == FrameState.TooBright -> Color(0xFFF8B84E)
            state == FrameState.Blurry || state == FrameState.NoContent -> PmmRedDark
            else -> Color.White
        },
        animationSpec = tween(260),
        label = "frameAccent"
    )

    // Pulso de confirmacion: el marco se comprime un poco sobre el codigo y
    // vuelve, con un velo verde que entra y sale. Es lo que hace visible la
    // lectura en equipos que decodifican de inmediato.
    val captureProgress = remember { Animatable(0f) }
    LaunchedEffect(isCaptured) {
        if (isCaptured) {
            captureProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing)
            )
            captureProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 240, easing = LinearOutSlowInEasing)
            )
        } else {
            captureProgress.snapTo(0f)
        }
    }
    val capturePulse = captureProgress.value

    // Encuadre de reposo: el marco vuelve aqui cuando no hay ningun codigo.
    val restingFrame = ScannerFrameRect(
        centerX = 0.5f,
        centerY = 0.5f,
        width = if (compact) 0.72f else 0.78f,
        height = if (compact) 0.38f else 0.31f
    )
    val target = lockedFrame ?: restingFrame

    // Resorte casi critico: sigue al codigo sin rebotar ni vibrar con el
    // temblor natural del bounding box de ML Kit frame a frame.
    val frameSpring = remember {
        spring<Float>(dampingRatio = 0.85f, stiffness = 340f)
    }

    val centerX by animateFloatAsState(target.centerX, frameSpring, label = "frameX")
    val centerY by animateFloatAsState(target.centerY, frameSpring, label = "frameY")
    val width by animateFloatAsState(target.width, frameSpring, label = "frameW")
    val height by animateFloatAsState(target.height, frameSpring, label = "frameH")

    BoxWithConstraints(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onResetZoom() })
        }
    ) {
        val fullHeightPx = constraints.maxHeight.toFloat()
        val labelGapPx = with(LocalDensity.current) { 14.dp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            // El pulso encoge el marco un 6% y lo devuelve.
            val pulseScale = 1f - 0.06f * capturePulse
            val frameWidth = size.width * width * pulseScale
            val frameHeight = size.height * height * pulseScale
            // El marco se mantiene entero dentro de la pantalla aunque el
            // codigo se acerque al borde.
            val left = (size.width * centerX - frameWidth / 2f)
                .coerceIn(0f, (size.width - frameWidth).coerceAtLeast(0f))
            val top = (size.height * centerY - frameHeight / 2f)
                .coerceIn(0f, (size.height - frameHeight).coerceAtLeast(0f))
            val rectSize = ComposeSize(frameWidth, frameHeight)
            val radius = if (compact) 20.dp.toPx() else 26.dp.toPx()
            val stroke = if (compact) 3.dp.toPx() else 4.dp.toPx()
            // Las esquinas se acortan con el marco: sin esto, al cerrarse
            // sobre un QR pequeno se cruzarian unas con otras.
            val maxCorner = if (compact) 30.dp.toPx() else 42.dp.toPx()
            val corner = minOf(maxCorner, frameWidth * 0.32f, frameHeight * 0.32f)

            drawRoundRect(
                color = Color.White.copy(alpha = 0.10f),
                topLeft = Offset(left, top),
                size = rectSize,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = 1.dp.toPx())
            )

            if (capturePulse > 0f) {
                drawRoundRect(
                    color = PmmGreen.copy(alpha = 0.22f * capturePulse),
                    topLeft = Offset(left, top),
                    size = rectSize,
                    cornerRadius = CornerRadius(radius, radius)
                )
            }

            fun drawCorner(start: Offset, horizontal: Float, vertical: Float) {
                drawLine(
                    color = accent,
                    start = start,
                    end = Offset(start.x + horizontal, start.y),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = start,
                    end = Offset(start.x, start.y + vertical),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }

            drawCorner(Offset(left, top), corner, corner)
            drawCorner(Offset(left + frameWidth, top), -corner, corner)
            drawCorner(Offset(left, top + frameHeight), corner, -corner)
            drawCorner(Offset(left + frameWidth, top + frameHeight), -corner, -corner)
        }

        if (showGuidanceOverlay && (hasCandidate || isSearching)) {
            // La etiqueta viaja pegada al borde inferior del marco; anclada al
            // centro de la pantalla se quedaba descolgada cuando el marco se
            // cierra sobre el codigo.
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        val y = (centerY + height / 2f) * fullHeightPx + labelGapPx
                        IntOffset(x = 0, y = y.coerceAtMost(fullHeightPx * 0.84f).toInt())
                    }
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally),
                color = Color.Black.copy(alpha = 0.52f),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (hasCandidate) "Enfocando codigo" else "Buscando codigo",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassIconButton(
    enabled: Boolean = true,
    selected: Boolean = false,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (selected) {
                    PmmBlueDeep.copy(alpha = 0.92f)
                } else {
                    Color.Black.copy(alpha = 0.42f)
                }
            )
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) 0.16f else 0.06f),
                CircleShape
            )
    ) {
        content()
    }
}

@Composable
private fun StatusPill(text: String, compact: Boolean) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (compact) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = if (compact) 11.dp else 15.dp,
                vertical = if (compact) 6.dp else 8.dp
            )
        )
    }
}

@Composable
private fun QuickZoomButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) PmmBlueLight else Color.White.copy(alpha = 0.13f),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(46.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun CameraPermissionView(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFFF8FAFC))
            .padding(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFEFF4FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = "Permiso de camara requerido",
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Necesitamos activar la camara para leer codigos.",
                color = PmmTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                )
            ) {
                Text("Permitir camara", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CameraPausedView(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF020617), Color(0xFF0B1B3B))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                color = PmmBlueLight,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = message,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

private fun scannerStatusText(
    frameState: FrameState,
    autoZoomPhase: AutoZoomPhase,
    hasCandidate: Boolean,
    isWaitingNextScan: Boolean
): String = when {
    isWaitingNextScan -> "Codigo capturado, listo para el siguiente"
    hasCandidate -> "Codigo localizado; ajustando zoom y enfoque"
    autoZoomPhase == AutoZoomPhase.Searching -> "Buscando codigo; acercando automaticamente"
    frameState == FrameState.TooDark -> "Muy oscuro; usa el flash o mejora la luz"
    frameState == FrameState.TooBright -> "Demasiada luz; evita reflejos directos"
    frameState == FrameState.Blurry -> "Imagen borrosa; estabiliza la camara"
    frameState == FrameState.NoContent -> "Busca una etiqueta dentro del visor"
    else -> "Apunta al codigo; el zoom se ajusta solo"
}
