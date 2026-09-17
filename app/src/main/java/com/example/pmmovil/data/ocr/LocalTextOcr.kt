package com.example.pmmovil.data.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class LocalTextOcrResult(
    val rawText: String,
    val candidates: List<String>,
    val barcodes: List<String> = emptyList()
)

@Singleton
class LocalTextOcr @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun readFromFile(path: String): Result<LocalTextOcrResult> = runCatching {
        withContext(Dispatchers.Default) {
            val file = File(path)
            require(file.exists()) { "La foto no existe." }

            val image = InputImage.fromFilePath(context, Uri.fromFile(file))
            val directResult = recognizer.process(image).awaitResult().toLocalResult()

            // Si el escaneo directo de la foto de alta resolucion ya arrojo candidatos,
            // evitamos el costoso y redundante procesamiento de variantes (ahorro masivo de CPU y tiempo)
            if (directResult.candidates.isNotEmpty()) {
                Log.d("OCR_PHOTO", "Fast-path: directResult tiene ${directResult.candidates.size} candidatos. Evitando variantes.")
                return@withContext directResult
            }

            val bitmap = decodeSampledBitmap(path, maxSize = 1200)
            if (bitmap == null) {
                directResult
            } else {
                try {
                    mergeLocalResults(directResult, processBitmapVariants(bitmap))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    suspend fun readFromBase64(value: String): Result<LocalTextOcrResult> = runCatching {
        withContext(Dispatchers.Default) {
            val payload = value.substringAfter(",", value).trim()
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("No se pudo leer la imagen.")

            try {
                processBitmapVariants(bitmap)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private suspend fun processBitmapVariants(source: Bitmap): LocalTextOcrResult {
        val results = mutableListOf<LocalTextOcrResult>()

        suspend fun scan(bitmap: Bitmap, recycle: Boolean = true) {
            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val result = recognizer.process(image).awaitResult().toLocalResult()
                results += result
            } finally {
                if (recycle && bitmap !== source && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        }

        // 1. Escaneo del bitmap base
        scan(source, recycle = false)
        Log.d("OCR_PHOTO", "Base bitmap: candidates=${results.last().candidates.size}")

        // 2. Si sigue vacio, intentamos contraste mejorado
        if (results.flatMap { it.candidates }.isEmpty()) {
            createContrastBitmap(source)?.let {
                scan(it)
                Log.d("OCR_PHOTO", "Contrast bitmap: candidates=${results.last().candidates.size}")
            }
        }

        // 3. Si sigue vacio, intentamos el extractor de texto vertical
        if (results.flatMap { it.candidates }.isEmpty()) {
            val horizontalFromVertical = extractVerticalNumberComposites(source, maxResults = 2)
            if (horizontalFromVertical.isNotEmpty()) {
                horizontalFromVertical.forEachIndexed { index, composite ->
                    scan(composite, recycle = true)
                    Log.d("OCR_PHOTO", "Vertical-to-Horizontal[$index]: candidates=${results.last().candidates.size}")
                }
            }
        }

        val merged = mergeLocalResults(*results.toTypedArray())
        Log.d("OCR_PHOTO", "Merged final: candidates=${merged.candidates.size}")
        return merged
    }
}

internal fun addOcrCandidate(values: MutableSet<String>, value: String) {
    val clean = value
        .uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9\\-\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    if (clean.isBlank()) return

    values += clean
    values += clean.replace(" ", "")

    Regex("[A-Z0-9][A-Z0-9\\- ]{1,24}[A-Z0-9]")
        .findAll(clean)
        .forEach { match ->
            val candidate = match.value.trim()
            values += candidate
            values += candidate.replace(" ", "")
        }
}

internal fun finalizeLocalResult(
    rawText: String,
    values: Set<String>,
    barcodes: List<String> = emptyList()
): LocalTextOcrResult = LocalTextOcrResult(
    rawText = rawText,
    candidates = values
        .asSequence()
        .map(String::trim)
        .filter { it.length in 3..28 }
        .distinct()
        .toList(),
    barcodes = barcodes
)

// Para motores que ya entregan renglones enderezados (ej. PaddleOCR), sin cajas por elemento.
internal fun buildLocalResultFromLines(rawText: String, lines: List<String>): LocalTextOcrResult {
    val values = linkedSetOf<String>()
    addOcrCandidate(values, rawText)
    lines.forEach { addOcrCandidate(values, it) }
    return finalizeLocalResult(rawText, values)
}

internal fun Text.toLocalResult(): LocalTextOcrResult {
    val values = linkedSetOf<String>()

    addOcrCandidate(values, text)
    textBlocks.forEach { block ->
        addOcrCandidate(values, block.text)
        block.lines.forEach { line ->
            addOcrCandidate(values, line.text)
            line.elements.forEach { element ->
                addOcrCandidate(values, element.text)
            }
        }
    }
    addVerticalElementCandidates(values)

    return finalizeLocalResult(text, values)
}

private fun Text.addVerticalElementCandidates(values: MutableSet<String>) {
    val tokens = textBlocks
        .flatMap { block -> block.lines.flatMap { line -> line.elements } }
        .mapNotNull { element ->
            val box = element.boundingBox ?: return@mapNotNull null
            val token = cleanOcrToken(element.text)
            if (token.isBlank() || token.length > 6 || !token.all { it.isLetterOrDigit() }) return@mapNotNull null
            OcrElementToken(
                value = token,
                centerX = box.exactCenterX(),
                centerY = box.exactCenterY(),
                width = box.width().coerceAtLeast(1),
                height = box.height().coerceAtLeast(1)
            )
        }
        .sortedBy { it.centerX }

    if (tokens.size < 2) return

    val avgWidth = tokens.map { it.width }.average().toFloat().takeIf { it > 0f } ?: 24f
    val tolerance = max(28f, avgWidth * 2.4f)
    val columns = mutableListOf<MutableList<OcrElementToken>>()

    tokens.forEach { token ->
        val column = columns.firstOrNull { group ->
            abs(group.map { it.centerX }.average().toFloat() - token.centerX) <= tolerance
        }
        if (column == null) {
            columns += mutableListOf(token)
        } else {
            column += token
        }
    }

    columns
        .filter { it.size >= 2 }
        .forEach { column ->
            val ordered = column.sortedBy { it.centerY }
            val joined = ordered.joinToString("") { it.value }
            if (joined.length in 3..16) {
                values += joined
            }

            ordered.windowed(size = 3, step = 1, partialWindows = false)
                .map { window -> window.joinToString("") { it.value } }
                .filter { it.length in 3..12 }
                .forEach { values += it }
        }
}

private data class OcrElementToken(
    val value: String,
    val centerX: Float,
    val centerY: Float,
    val width: Int,
    val height: Int
)

private fun cleanOcrToken(value: String): String =
    value.uppercase(Locale.US)
        .replace(Regex("[^A-Z0-9]"), "")
        .trim()

internal fun mergeLocalResults(vararg results: LocalTextOcrResult): LocalTextOcrResult {
    val rawText = results
        .map { it.rawText }
        .filter(String::isNotBlank)
        .distinct()
        .joinToString("\n")
    val candidates = results
        .flatMap { it.candidates }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    val barcodes = results
        .flatMap { it.barcodes }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
    return LocalTextOcrResult(rawText = rawText, candidates = candidates, barcodes = barcodes)
}

internal fun decodeSampledBitmap(path: String, maxSize: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val longest = max(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (longest / sample > maxSize) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
    ) ?: return null
    val oriented = applyExifOrientation(decoded, path)
    if (oriented !== decoded) decoded.recycle()
    return oriented
}

private fun smartCrops(source: Bitmap): List<Rect> {
    val w = source.width
    val h = source.height
    if (w < 320 || h < 320) return emptyList()

    return listOf(
        Rect(0, 0, (w * 0.42f).toInt(), h),
        Rect((w * 0.58f).toInt(), 0, w, h),
        Rect((w * 0.25f).toInt(), 0, (w * 0.75f).toInt(), h),
        Rect(0, (h * 0.42f).toInt(), w, h),
        Rect(0, 0, w, (h * 0.58f).toInt())
    ).filter { it.width() > 80 && it.height() > 80 }
}

private fun smartSideCrops(source: Bitmap): List<Rect> {
    val w = source.width
    val h = source.height
    if (w < 320 || h < 320) return emptyList()

    return listOf(
        Rect(0, 0, (w * 0.36f).toInt(), h),
        Rect((w * 0.64f).toInt(), 0, w, h)
    ).filter { it.width() > 80 && it.height() > 80 }
}

internal fun extractVerticalNumberAsHorizontal(source: Bitmap): Bitmap? =
    extractVerticalNumberComposites(source, maxResults = 1).firstOrNull()

internal fun extractVerticalNumberComposites(source: Bitmap, maxResults: Int = 3): List<Bitmap> {
    val w = source.width
    val h = source.height
    if (w < 320 || h < 320 || maxResults <= 0) return emptyList()

    val stripWidth = (w * 0.18f).toInt().coerceAtLeast(56).coerceAtMost(w)
    val stripTop = (h * 0.03f).toInt()
    val stripHeight = (h * 0.78f).toInt()
    val scanStarts = listOf(
        0.02f, 0.06f, 0.10f, 0.14f, 0.18f, 0.22f, 0.26f, 0.30f, 0.34f,
        0.50f, 0.56f, 0.62f, 0.68f, 0.74f, 0.80f, 0.86f, 0.92f
    ).map { (w * it).toInt() }.distinct()

    return scanStarts
        .mapNotNull { startX -> scanStripForVerticalDigits(source, startX, stripTop, stripWidth, stripHeight) }
        .sortedByDescending { it.first }
        .mapNotNull { (score, composite) -> composite }
        .take(maxResults)
}

internal fun scanStripForVerticalDigits(
    source: Bitmap,
    startX: Int,
    startY: Int,
    stripW: Int,
    stripH: Int
): Pair<Int, Bitmap?>? {
    val w = source.width
    val h = source.height
    val safeX = startX.coerceIn(0, (w - stripW).coerceAtLeast(0))
    val safeY = startY.coerceIn(0, (h - stripH).coerceAtLeast(0))
    val safeW = stripW.coerceAtMost(w - safeX)
    val safeH = stripH.coerceAtMost(h - safeY)
    if (safeW < 30 || safeH < 80) return null

    val pixels = IntArray(safeW * safeH)
    source.getPixels(pixels, 0, safeW, safeX, safeY, safeW, safeH)

    val rowDark = BooleanArray(safeH)
    for (y in 0 until safeH) {
        val rowOffset = y * safeW
        var sumBright = 0L
        var minBright = 255
        var count = 0
        for (x in 0 until safeW) {
            val b = pixelBrightness(pixels[rowOffset + x])
            sumBright += b
            if (b < minBright) minBright = b
            count++
        }
        val avgBright = if (count > 0) (sumBright / count).toInt() else 255
        rowDark[y] = (avgBright - minBright) >= 35 && minBright < 120
    }

    val segments = mutableListOf<IntRange>()
    var segStart = -1
    for (y in 0 until safeH) {
        if (rowDark[y]) {
            if (segStart < 0) segStart = y
        } else {
            if (segStart >= 0) {
                segments += segStart until y
                segStart = -1
            }
        }
    }
    if (segStart >= 0) segments += segStart until safeH

    if (segments.isEmpty()) {
        Log.d("OCR_V2H", "strip@${startX}: 0 segmentos oscuros")
        return null
    }

    val heights = segments.map { it.last - it.first + 1 }
    val sortedHeights = heights.sorted()
    val medianH = sortedHeights[sortedHeights.size / 2]

    val digitSegments = segments.filter { seg ->
        val segH = seg.last - seg.first + 1
        segH <= medianH * 3
    }

    if (digitSegments.isEmpty()) {
        Log.d("OCR_V2H", "strip@${startX}: todos los segmentos filtrados por mediana")
        return null
    }

    val digitHeights = digitSegments.map { it.last - it.first + 1 }.sorted()
    val digitMedian = digitHeights[digitHeights.size / 2]
    val minH = (digitMedian * 0.30f).toInt().coerceAtLeast(5)
    val maxH = (digitMedian * 3.0f).toInt().coerceAtLeast(minH + 3)

    val filtered = digitSegments.filter { seg ->
        val segH = seg.last - seg.first + 1
        segH in minH..maxH
    }

    Log.d("OCR_V2H", "strip@${startX}: segmentos=${segments.size} digitSegments=${filtered.size} medianH=$medianH->digitMedian=$digitMedian minH=$minH maxH=$maxH alturas=${filtered.map { it.last - it.first + 1 }}")

    if (filtered.size < 3 || filtered.size > 12) return null

    val gaps = filtered.zipWithNext { a, b -> b.first - a.last }
    if (gaps.isEmpty()) return null
    val sortedGaps = gaps.sorted()
    val medianGap = sortedGaps[sortedGaps.size / 2]
    val consistentGaps = gaps.count { it in (medianGap * 0.3f).toInt().coerceAtLeast(2)..(medianGap * 3.0f).toInt() }
    if (consistentGaps < gaps.size * 0.35f && filtered.size < 5) {
        Log.d("OCR_V2H", "strip@${startX}: gaps inconsistentes (consistent=$consistentGaps de ${gaps.size} medianGap=$medianGap)")
        return null
    }

    val charH = digitMedian.coerceIn(28, 120)
    val padV = (charH * 0.25f).toInt().coerceAtLeast(3)
    val totalH = charH + padV * 2
    val charW = (charH * 0.95f).toInt().coerceIn(24, 132)
    val gapW = (charW * 0.3f).toInt().coerceAtLeast(6)
    val totalW = filtered.size * charW + (filtered.size + 1) * gapW

    val composite = Bitmap.createBitmap(
        totalW.coerceAtMost(3200),
        totalH.coerceAtMost(2400),
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(composite)
    canvas.drawColor(android.graphics.Color.WHITE)

    val srcBitmap = Bitmap.createBitmap(source, safeX, safeY, safeW, safeH)

    filtered.forEachIndexed { index, range ->
        val srcRect = darkBoundsForSegment(pixels, safeW, safeH, range)
            ?: Rect(0, range.first.coerceAtLeast(0), safeW, (range.last + 1).coerceAtMost(safeH))
        val dstLeft = gapW + index * (charW + gapW)
        val dstRect = Rect(dstLeft, padV, dstLeft + charW, padV + charH)
        canvas.drawBitmap(srcBitmap, srcRect, dstRect, null)
    }

    srcBitmap.recycle()
    val enhanced = createContrastBitmap(composite)
    composite.recycle()

    val score = filtered.size * 10 + consistentGaps * 5
    Log.d("OCR_V2H", "strip@${startX}: composicion creada ${enhanced?.width}x${enhanced?.height} digitos=${filtered.size} score=$score")
    return score to enhanced
}

private fun darkBoundsForSegment(
    pixels: IntArray,
    width: Int,
    height: Int,
    segment: IntRange
): Rect? {
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    val top = segment.first.coerceIn(0, height - 1)
    val bottom = segment.last.coerceIn(0, height - 1)

    for (y in top..bottom) {
        val rowOffset = y * width
        for (x in 0 until width) {
            if (pixelBrightness(pixels[rowOffset + x]) <= 145) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }

    if (maxX < minX || maxY < minY) return null

    val boxW = maxX - minX + 1
    val boxH = maxY - minY + 1
    val padX = (boxW * 0.35f).toInt().coerceAtLeast(5)
    val padY = (boxH * 0.18f).toInt().coerceAtLeast(3)
    return Rect(
        (minX - padX).coerceAtLeast(0),
        (minY - padY).coerceAtLeast(0),
        (maxX + padX + 1).coerceAtMost(width),
        (maxY + padY + 1).coerceAtMost(height)
    )
}

private fun pixelBrightness(pixel: Int): Int {
    return (((pixel shr 16) and 0xFF) * 0.299f +
        ((pixel shr 8) and 0xFF) * 0.587f +
        (pixel and 0xFF) * 0.114f).toInt()
}

private fun createContrastBitmap(source: Bitmap): Bitmap? = runCatching {
    val bitmap = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val contrast = 1.35f
    val translate = (-0.5f * contrast + 0.5f) * 255f
    val matrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(matrix)
    }
    Canvas(bitmap).drawBitmap(source, 0f, 0f, paint)
    bitmap
}.getOrNull()

private suspend fun <T> Task<T>.awaitResult(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel(CancellationException("ML Kit cancelo la lectura."))
            }
        }
    }
