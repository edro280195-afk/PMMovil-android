package com.example.pmmovil.ui.scanner

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.hypot

/**
 * Analizador de codigos de barras para CameraX.
 *
 * Aporta tres senales:
 * 1. ML Kit detecta codigos y notifica via [onBarcodeScanned].
 * 2. ML Kit detecta codigos potenciales sin valor legible y reporta su
 *    posicion y tamano via [onPotentialBarcode]. Esa senal alimenta el
 *    autofocus y el [AutoZoomController].
 * 3. El [updateFrameState] permite a un analizador de calidad de
 *    frame (e.g. [FrameQualityAnalyzer]) enriquecer las decisiones
 *    de UI.
 *
 * Aqui **no** se configura `ZoomSuggestionOptions`: el auto-zoom lo lleva
 * entero el [AutoZoomController], que si conoce el zoom real de la camara.
 * ML Kit asume que la camara arranca en 1.0x y que solo el la mueve, algo
 * que no se cumple con zoom inicial por pantalla, pellizco y botones; sus
 * sugerencias salian calculadas contra una base equivocada.
 *
 * Esta clase cierra el [ImageProxy] cuando ML Kit termina de procesar
 * el frame. Es importante porque el procesamiento de ML Kit es asincrono:
 * cerrar el proxy antes de tiempo deja al scanner sin imagen para leer.
 */
class BarcodeAnalyzer(
    private val barcodeFormats: Int = Barcode.FORMAT_ALL_FORMATS,
    private val continuous: Boolean = false,
    private val repeatCooldownMillis: Long = 900L,
    private val minConsecutiveReads: Int = 1,
    private val requireCandidateSelectionForMultiple: Boolean = false,
    private val onPotentialBarcode: ((BarcodeVisualCandidate) -> Unit)? = null,
    private val onPotentialBarcodes: ((List<BarcodeVisualCandidate>) -> Unit)? = null,
    private val onBarcodeScanned: (String) -> Unit,
    private val onCameraState: ((FrameState) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(barcodeFormats)
        // Devuelve tambien los codigos que ML Kit ve pero todavia no puede
        // decodificar. Es la senal que permite acercar antes de poder leer.
        .enableAllPotentialBarcodes()
        .build()

    private val scanner = BarcodeScanning.getClient(options)
    private val processing = AtomicBoolean(false)
    @Volatile private var isScanning = true
    private var lastValue: String? = null
    private var lastReadAt: Long = 0L
    private var consecutiveValue: String? = null
    private var consecutiveReadCount: Int = 0
    @Volatile private var selectedCandidateValue: String? = null
    private var lastPotentialAt: Long = 0L
    private val startedAt: Long = System.currentTimeMillis()

    @Volatile
    private var latestFrameState: FrameState = FrameState.Searching

    @Volatile
    var lastFrameProcessedAt: Long = 0L
        private set

    val analyzerStartedAt: Long
        get() = startedAt

    /**
     * Alimentado por el [FrameQualityAnalyzer] paralelo para reportar el
     * estado perceptible del encuadre (oscuro, borroso, sin contenido).
     */
    fun updateFrameState(state: FrameState) {
        latestFrameState = state
    }

    override fun analyze(imageProxy: ImageProxy) {
        processFrame(imageProxy)
    }

    /**
     * Procesa un frame. NO cierra el [imageProxy]: el contenedor
     * (e.g. [CombinedAnalyzer]) es responsable del ciclo de vida.
     * Esto permite que el mismo frame se procese por multiples
     * OCR sin doble cierre.
     */
    @OptIn(markerClass = [ExperimentalGetImage::class])
    fun processFrame(imageProxy: ImageProxy) {
        if (!isScanning || !processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        // ML Kit entrega los boundingBox en coordenadas de la imagen ya
        // enderezada, pero `InputImage.getWidth()` devuelve el buffer sin
        // rotar. Con el telefono en vertical (rotacion 90) eso son 1280x720
        // contra 720x1280: normalizar con las dimensiones equivocadas
        // desplazaba el punto de enfoque y cualquier overlay que siga al codigo.
        val isSideways = rotationDegrees == 90 || rotationDegrees == 270
        val uprightWidth = if (isSideways) imageProxy.height else imageProxy.width
        val uprightHeight = if (isSideways) imageProxy.width else imageProxy.height

        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        // `scanner.process` puede lanzar de forma sincrona si el cliente de
        // ML Kit se cerro entre la comprobacion de `isScanning` y esta linea
        // (pasa al salir de la pantalla). Sin este guard el frame se quedaba
        // sin cerrar y `processing` en true: la camara se congelaba.
        val task = try {
            scanner.process(image)
        } catch (_: Throwable) {
            processing.set(false)
            imageProxy.close()
            return
        }
        task
            .addOnSuccessListener { barcodes ->
                val now = System.currentTimeMillis()
                lastFrameProcessedAt = now

                val orderedBarcodes = barcodes.sortedByDescending { barcode ->
                    barcodeScore(barcode, uprightWidth, uprightHeight)
                }
                val visualCandidates = orderedBarcodes
                    .asSequence()
                    .filter { barcode ->
                        val box = barcode.boundingBox ?: return@filter false
                        // Un codigo ya decodificado manda siempre; uno solo
                        // "potencial" tiene que estar dentro del encuadre util.
                        !barcode.rawValue.isNullOrBlank() ||
                            centerDistanceOf(box, uprightWidth, uprightHeight) <=
                                MAX_CANDIDATE_CENTER_DISTANCE
                    }
                    .mapNotNull { barcode ->
                        val value = BarcodeValueNormalizer.normalize(
                            barcode.rawValue ?: barcode.displayValue
                        )?.takeIf(String::isNotBlank)
                        barcode.toVisualCandidate(
                            imageWidth = uprightWidth,
                            imageHeight = uprightHeight,
                            hasDecodedValue = value != null,
                            value = value
                        )
                    }
                    .toList()

                onPotentialBarcodes?.invoke(visualCandidates)
                visualCandidates.firstOrNull()?.let { candidate ->
                    lastPotentialAt = now
                    onPotentialBarcode?.invoke(candidate)
                }

                val decodedCandidates = visualCandidates.filter {
                    !it.value.isNullOrBlank()
                }
                val value = if (
                    requireCandidateSelectionForMultiple && decodedCandidates.size > 1
                ) {
                    decodedCandidates.firstOrNull {
                        it.value == selectedCandidateValue
                    }?.value
                } else {
                    decodedCandidates.firstOrNull()?.value
                }

                if (value != null && isScanning) {
                    if (value == consecutiveValue) {
                        consecutiveReadCount += 1
                    } else {
                        consecutiveValue = value
                        consecutiveReadCount = 1
                    }

                    val canRepeat = value != lastValue ||
                        now - lastReadAt >= repeatCooldownMillis
                    val hasStableValue = consecutiveReadCount >=
                        minConsecutiveReads.coerceAtLeast(1)
                    if (canRepeat && hasStableValue) {
                        lastValue = value
                        lastReadAt = now
                        if (!continuous) isScanning = false
                        onBarcodeScanned(value)
                    }
                } else {
                    consecutiveValue = null
                    consecutiveReadCount = 0
                }
                publishCameraState(now)
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    private fun publishCameraState(now: Long) {
        if (onCameraState == null) return
        val state = when {
            latestFrameState == FrameState.TooDark -> FrameState.TooDark
            latestFrameState == FrameState.TooBright -> FrameState.TooBright
            latestFrameState == FrameState.Blurry -> FrameState.Blurry
            latestFrameState == FrameState.NoContent -> FrameState.NoContent
            now - lastPotentialAt <= POTENTIAL_BARCODE_WINDOW_MS -> FrameState.AlmostThere
            lastReadAt > 0L -> FrameState.AlmostThere
            else -> FrameState.Searching
        }
        onCameraState?.invoke(state)
    }

    /**
     * Suspende el procesado de frames. Se usa para el cooldown entre lecturas
     * en modo continuo: mientras esta pausado no se decodifica nada, asi que
     * el estado interno (`lastValue`, contador de lecturas consecutivas) no
     * se contamina con codigos que la UI iba a descartar de todas formas.
     */
    fun pauseScanning() {
        isScanning = false
    }

    fun resumeScanning() {
        isScanning = true
        consecutiveValue = null
        consecutiveReadCount = 0
    }

    fun selectCandidate(value: String) {
        selectedCandidateValue = value
        consecutiveValue = null
        consecutiveReadCount = 0
    }

    fun clearCandidateSelection() {
        selectedCandidateValue = null
        consecutiveValue = null
        consecutiveReadCount = 0
    }

    fun close() {
        isScanning = false
        scanner.close()
    }
}

/**
 * Ordena los codigos del frame por utilidad para el operador.
 *
 * Primero los que ML Kit ya pudo decodificar y, entre el resto, los que estan
 * mas cerca del centro del encuadre, que es a donde el usuario esta apuntando.
 *
 * Antes se ordenaba por area: con `enableAllPotentialBarcodes()` cualquier
 * superficie con lineas regulares (una persiana, una reja, un teclado) genera
 * candidatos potenciales enormes que le ganaban al codigo real, y el marco se
 * iba encima de ellos. Ademas el area a 1080p llega a 2.073.600, mas que el
 * bonus de 1.000.000 que marcaba "ya decodificado", asi que un falso positivo
 * grande podia desplazar incluso a un codigo leido de verdad.
 */
internal fun barcodeScore(barcode: Barcode, imageWidth: Int, imageHeight: Int): Long {
    val hasValue = !barcode.rawValue.isNullOrBlank()
    val box = barcode.boundingBox ?: return scoreOf(hasValue, centerDistance = 1f)
    return scoreOf(hasValue, centerDistanceOf(box, imageWidth, imageHeight))
}

/** Logica pura de puntuacion, expuesta para tests. */
internal fun scoreOf(hasRawValue: Boolean, centerDistance: Float): Long {
    val decodedBonus = if (hasRawValue) DECODED_SCORE_BONUS else 0L
    val centrality = (1f - centerDistance).coerceIn(0f, 1f)
    return decodedBonus + (centrality * CENTRALITY_SCORE_RANGE).toLong()
}

private fun centerDistanceOf(box: Rect, imageWidth: Int, imageHeight: Int): Float =
    normalizedCenterDistance(
        centerX = box.exactCenterX(),
        centerY = box.exactCenterY(),
        imageWidth = imageWidth,
        imageHeight = imageHeight
    )

/**
 * Distancia del centro del codigo al centro del frame, normalizada para que
 * 0 sea el centro exacto y 1 una esquina. Expuesta para tests.
 */
internal fun normalizedCenterDistance(
    centerX: Float,
    centerY: Float,
    imageWidth: Int,
    imageHeight: Int
): Float {
    val safeWidth = imageWidth.coerceAtLeast(1)
    val safeHeight = imageHeight.coerceAtLeast(1)
    val dx = (centerX / safeWidth) - 0.5f
    val dy = (centerY / safeHeight) - 0.5f
    return (hypot(dx, dy) / MAX_CENTER_DISTANCE).coerceIn(0f, 1f)
}

private const val DECODED_SCORE_BONUS = 1_000_000L
private const val CENTRALITY_SCORE_RANGE = 100_000L

/** Distancia del centro a una esquina en coordenadas normalizadas. */
private const val MAX_CENTER_DISTANCE = 0.7071068f

/**
 * Un codigo sin decodificar solo arrastra el marco si esta razonablemente
 * dentro del encuadre. Evita que el visor se vaya a perseguir reflejos o
 * texturas del fondo mientras el operador apunta al codigo de verdad.
 */
internal const val MAX_CANDIDATE_CENTER_DISTANCE = 0.42f

/**
 * Posicion de un codigo dentro del frame, en fracciones (0..1) de la imagen
 * **ya enderezada**. [sourceAspectRatio] (ancho / alto de esa imagen) permite
 * a la UI convertir estas fracciones al espacio de la `PreviewView`, que
 * recorta con FILL_CENTER.
 */
data class BarcodeVisualCandidate(
    val centerX: Float,
    val centerY: Float,
    val widthRatio: Float,
    val heightRatio: Float,
    val hasDecodedValue: Boolean,
    val sourceAspectRatio: Float,
    val value: String? = null
) {
    /**
     * Lado corto del codigo medido contra el **lado corto del frame**.
     *
     * Es la magnitud que decide si ML Kit puede decodificar: necesita ~2
     * pixeles por modulo y es el lado corto el que se queda sin pixeles
     * primero. [widthRatio] y [heightRatio] no sirven directamente porque
     * estan normalizados contra ejes distintos: en vertical el 10% del ancho
     * y el 10% del alto son tamanos muy diferentes.
     *
     * Es la entrada del lazo cerrado del [AutoZoomController].
     */
    val minSideFraction: Float
        get() {
            val aspect = sourceAspectRatio
            if (aspect <= 0f) return 0f
            val widthOverMinSide = if (aspect <= 1f) widthRatio else widthRatio * aspect
            val heightOverMinSide = if (aspect <= 1f) heightRatio / aspect else heightRatio
            return minOf(widthOverMinSide, heightOverMinSide).coerceIn(0f, 1f)
        }
}

private const val POTENTIAL_BARCODE_WINDOW_MS = 1_200L

private fun Barcode.toVisualCandidate(
    imageWidth: Int,
    imageHeight: Int,
    hasDecodedValue: Boolean,
    value: String? = null
): BarcodeVisualCandidate? {
    val box = boundingBox ?: return null
    val safeWidth = imageWidth.coerceAtLeast(1)
    val safeHeight = imageHeight.coerceAtLeast(1)
    return BarcodeVisualCandidate(
        centerX = (box.exactCenterX() / safeWidth).coerceIn(0f, 1f),
        centerY = (box.exactCenterY() / safeHeight).coerceIn(0f, 1f),
        widthRatio = (box.width().toFloat() / safeWidth).coerceIn(0f, 1f),
        heightRatio = (box.height().toFloat() / safeHeight).coerceIn(0f, 1f),
        hasDecodedValue = hasDecodedValue,
        sourceAspectRatio = safeWidth.toFloat() / safeHeight.toFloat(),
        value = value
    )
}
