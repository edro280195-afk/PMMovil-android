package com.example.pmmovil.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.model.OCRRunResult
import com.paddle.ocr.util.OpenCVUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

// El detector (DB) del SDK está pensado para hasta 4000px de lado largo (ver detMaxSideLimit);
// limitar de más aquí encoge texto delgado (dígitos verticales de placa/caja) por debajo de lo
// que el modelo puede resolver. 2600 deja margen razonable de cómputo en equipos de gama media.
private const val MAX_IMAGE_SIDE = 2600

// OCR local on-device con PaddleOCR (PP-OCRv6 small): detección + reconocimiento vía ONNX Runtime.
@Singleton
class PaddleOcrEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val initMutex = Mutex()

    @Volatile
    private var ocr: PaddleOCR? = null

    private suspend fun engine(): PaddleOCR {
        ocr?.let { return it }
        return initMutex.withLock {
            ocr?.let { return it }
            withContext(Dispatchers.IO) {
                OpenCVUtils.init(context)
                // numThreads=2: hay dos sesiones (det+rec) compartiendo un único pool global
                // (ver ORTSessionManager); detBoxThresh algo más bajo que el default (0.6) para
                // no perder texto delgado/desgastado de sellos industriales — el ranking de
                // candidatos aguas abajo (PlateOcrRanker/BoxOcrRanker) ya filtra el ruido extra.
                //
                // Detección sobre ≤960px de lado largo (el default clásico de PaddleOCR): el
                // detector solo ubica renglones y no necesita resolución alta; los boxes se
                // reescalan al original y el reconocedor recorta del bitmap COMPLETO (2600px),
                // así que la nitidez de lectura no cambia. Detectar sobre la imagen completa
                // (~5MP) saturaba el CPU varios segundos y dejaba al HAL de la cámara sin
                // buffers ("Failed to get stream buffer" → preview a 3-4fps).
                val engineConfig = EngineConfig(numThreads = 2)
                val paddleConfig = PaddleOCRConfig(
                    detLimitSideLen = 960,
                    detLimitType = "max",
                    detBoxThresh = 0.45f
                )
                PaddleOCR.create(context, paddleConfig, engineConfig)
            }.also { ocr = it }
        }
    }

    suspend fun readFromFile(path: String): Result<LocalTextOcrResult> = runCatching {
        val bitmap = withContext(Dispatchers.Default) {
            decodeSampledBitmap(path, maxSize = MAX_IMAGE_SIDE)
        } ?: error("No se pudo leer la imagen.")

        try {
            engine().recognize(bitmap).toLocalResult()
        } finally {
            bitmap.recycle()
        }
    }.onFailure { Log.e("OCR_PADDLE", "Error leyendo archivo", it) }

    suspend fun readFromBase64(value: String): Result<LocalTextOcrResult> = runCatching {
        val payload = value.substringAfter(",", value).trim()
        val bytes = Base64.decode(payload, Base64.DEFAULT)
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: error("No se pudo leer la imagen.")
        val bitmap = withContext(Dispatchers.Default) { downscaleIfNeeded(decoded, MAX_IMAGE_SIDE) }

        try {
            engine().recognize(bitmap).toLocalResult()
        } finally {
            bitmap.recycle()
        }
    }.onFailure { Log.e("OCR_PADDLE", "Error leyendo base64", it) }
}

private fun downscaleIfNeeded(source: Bitmap, maxSize: Int): Bitmap {
    val longest = maxOf(source.width, source.height)
    if (longest <= maxSize) return source
    val ratio = maxSize.toDouble() / longest
    val scaled = Bitmap.createScaledBitmap(
        source,
        (source.width * ratio).toInt().coerceAtLeast(1),
        (source.height * ratio).toInt().coerceAtLeast(1),
        true
    )
    if (scaled !== source) source.recycle()
    return scaled
}

private fun OCRRunResult.toLocalResult(): LocalTextOcrResult {
    val lines = results.map { it.text }
    val rawText = lines.joinToString("\n")
    Log.d("OCR_PADDLE", "lineas=${lines.size} det=${detectionTimeMs}ms rec=${recognitionTimeMs}ms total=${totalTimeMs}ms")
    return buildLocalResultFromLines(rawText, lines)
}
