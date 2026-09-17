package com.example.pmmovil.data.ocr

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.google.android.gms.tasks.Tasks
import java.nio.ByteBuffer

class LiveTextOcrAnalyzer(
    private val shouldSkipFrame: () -> Boolean = { false },
    /**
     * Se llama con CADA imagen real que entrega CameraX a este analizador,
     * antes de cualquier descarte por `shouldSkipFrame` o el limitador de
     * cadencia. Es la prueba de vida de la sesión que no depende de los
     * metadatos de captura de Camera2 -- ver la nota en
     * `CameraWarmupTracker.onLuminance` sobre por qué eso importa en el HAL
     * de MediaTek de la Armor Pad Pro.
     */
    private val onFrameSeen: () -> Unit = {},
    private val onResult: (LocalTextOcrResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Esta pantalla ya corre OCR de texto sobre cada frame, asi que el
    // escaner de codigos se limita a las simbologias que imprime la empresa.
    // Con FORMAT_ALL_FORMATS se pagaban trece detectores por frame en la
    // pantalla mas pesada de la app, que es justo donde el HAL se ahogaba.
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(BarcodeFormatSets.PMM_LABELS)
            .build()
    )

    @Volatile
    private var lastProcessedTime = 0L

    @Volatile
    private var closed = false
    private var frameCount = 0

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }

        onFrameSeen()

        // Con captura/análisis estático en curso, soltar el frame DE INMEDIATO: si se encola
        // en ML Kit (ocupado con la foto), el ImageProxy queda retenido, el stream de
        // ImageAnalysis se queda sin buffers y el HAL desploma la preview completa
        // ("Failed to get stream buffer from SBC manager" → cámara a 3-4fps).
        if (shouldSkipFrame()) {
            imageProxy.close()
            return
        }

        // Throttle: procesar maximo 4 veces por segundo
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < 250L) {
            imageProxy.close()
            return
        }

        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }

        lastProcessedTime = now
        frameCount++

        val rotation = imageProxy.imageInfo.rotationDegrees

        // Analizar texto y barcode en paralelo (sin copias de bitmap)
        val input = InputImage.fromMediaImage(media, rotation)
        val textTask = recognizer.process(input)
        val barcodeTask = barcodeScanner.process(input)

        Tasks.whenAllComplete(textTask, barcodeTask)
            .addOnCompleteListener {
                try {
                    if (closed) return@addOnCompleteListener

                    val textResult = textTask.result
                    val barcodes = barcodeTask.result

                    val normalResult = textResult?.toLocalResult()
                        ?: LocalTextOcrResult("", emptyList())
                    val barcodeValues = barcodes
                        ?.mapNotNull { it.rawValue ?: it.displayValue }
                        ?.map(BarcodeValueNormalizer::normalize)
                        ?.filter(String::isNotBlank)
                        ?: emptyList()

                    val combinedResult = normalResult.copy(barcodes = barcodeValues)
                    Log.d("OCR_LIVE", "candidates=${normalResult.candidates.size} barcodes=${barcodeValues.size}")

                    // Análisis vertical de texto rotado (stencil) — cada 6 frames
                    if (frameCount % 6 == 0 && barcodeValues.isEmpty()) {
                        val yuvBitmap = yuvToGrayscaleBitmap(media)
                        if (yuvBitmap != null) {
                            val uprightBitmap = rotateBitmap(yuvBitmap, rotation.toFloat())
                            try {
                                val verticalComposite = extractVerticalNumberAsHorizontal(uprightBitmap)
                                if (verticalComposite != null) {
                                    val vInput = InputImage.fromBitmap(verticalComposite, 0)
                                    recognizer.process(vInput)
                                        .addOnSuccessListener { vText ->
                                            if (closed) return@addOnSuccessListener
                                            val vResult = vText.toLocalResult()
                                            val merged = mergeLocalResults(combinedResult, vResult)
                                            runCatching { onResult(merged) }
                                        }
                                        .addOnFailureListener {
                                            runCatching { onResult(combinedResult) }
                                        }
                                        .addOnCompleteListener {
                                            verticalComposite.recycle()
                                            if (uprightBitmap !== yuvBitmap) uprightBitmap.recycle()
                                            yuvBitmap.recycle()
                                            imageProxy.close()
                                        }
                                    return@addOnCompleteListener
                                } else {
                                    if (uprightBitmap !== yuvBitmap) uprightBitmap.recycle()
                                    yuvBitmap.recycle()
                                }
                            } catch (e: Exception) {
                                if (uprightBitmap !== yuvBitmap) uprightBitmap.recycle()
                                yuvBitmap.recycle()
                                throw e
                            }
                        }
                    }

                    runCatching { onResult(combinedResult) }
                } catch (e: Exception) {
                    Log.e("OCR_LIVE", "Error en análisis de frame", e)
                } finally {
                    imageProxy.close()
                }
            }
    }

    fun close() {
        closed = true
        runCatching { recognizer.close() }
        runCatching { barcodeScanner.close() }
    }

    private fun yuvToGrayscaleBitmap(image: android.media.Image): Bitmap? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val planes = image.planes
        if (planes.size < 3) return null

        val yPlane = planes[0]
        val yBuffer: ByteBuffer = yPlane.buffer
        val w = image.width
        val h = image.height
        val pixelStride = yPlane.pixelStride
        val rowStride = yPlane.rowStride

        val pixels = IntArray(w * h)
        val rowBuf = ByteArray(rowStride)

        for (y in 0 until h) {
            val pos = y * rowStride
            yBuffer.position(pos)
            yBuffer.get(rowBuf, 0, rowStride.coerceAtMost(yBuffer.remaining()))
            for (x in 0 until w) {
                val gray = rowBuf[x * pixelStride].toInt() and 0xFF
                pixels[y * w + x] = 0xFF000000.toInt() or (gray shl 16) or (gray shl 8) or gray
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}
