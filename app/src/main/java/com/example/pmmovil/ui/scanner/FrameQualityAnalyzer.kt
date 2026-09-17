package com.example.pmmovil.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Analizador que estima la calidad del frame (luminancia, bordes,
 * varianza) para decidir si conviene hacer zoom, sugerir flash o
 * pedir mejor enfoque.
 *
 * Se procesa el plano Y (luminancia) en lugar de un Bitmap para no
 * gastar memoria. Submuestreamos a una grilla pequena (~80x60) para
 * que el costo sea despreciable.
 *
 * No cierra el [ImageProxy] que recibe: el ciclo de vida lo maneja
 * el contenedor (e.g. [CombinedAnalyzer]). Esto permite que el mismo
 * frame se procese por multiples analyzers sin doble `close()`.
 */
class FrameQualityAnalyzer(
    private val onQuality: (FrameQuality) -> Unit
) : ImageAnalysis.Analyzer {

    private var lastAnalysisAt: Long = 0L

    /**
     * Se ejecuta a ~5 fps para no consumir CPU. CameraX usa
     * STRATEGY_KEEP_ONLY_LATEST y entrega el frame mas reciente.
     */
    private val minIntervalMs: Long = 200L

    private val gridSize: Int = 6

    override fun analyze(imageProxy: ImageProxy) {
        processFrame(imageProxy)
    }

    /**
     * Procesa un frame. NO cierra el [imageProxy]: el contenedor
     * es responsable del ciclo de vida. Si el frame es descartado
     * por el throttle, tampoco lo cierra (queda vivo para que el
     * siguiente analyzer pueda procesarlo).
     */
    fun processFrame(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisAt < minIntervalMs) {
            return
        }
        lastAnalysisAt = now

        val quality = runCatching { analyzeInternal(imageProxy) }
            .getOrDefault(FrameQuality.Empty)
        try {
            onQuality(quality)
        } catch (_: Throwable) {
            // El callback nunca debe romper el pipeline.
        }
    }

    private fun analyzeInternal(image: ImageProxy): FrameQuality {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        if (buffer.remaining() == 0 || width <= 0 || height <= 0) {
            return FrameQuality.Empty
        }

        val sampleStepX = maxOf(1, width / TARGET_SAMPLE_WIDTH)
        val sampleStepY = maxOf(1, height / TARGET_SAMPLE_HEIGHT)
        val sampleW = width / sampleStepX
        val sampleH = height / sampleStepY
        val totalSamples = sampleW * sampleH
        if (totalSamples <= 0) return FrameQuality.Empty

        val samples = ByteArray(totalSamples)
        var index = 0
        for (y in 0 until sampleH) {
            val srcY = y * sampleStepY
            val rowOffset = srcY * rowStride
            for (x in 0 until sampleW) {
                val srcX = x * sampleStepX
                samples[index++] = buffer.get(rowOffset + srcX * pixelStride)
            }
        }

        return analyzeLuminanceBuffer(samples, sampleW, sampleH, gridSize)
    }

    companion object {
        const val TARGET_SAMPLE_WIDTH = 80
        const val TARGET_SAMPLE_HEIGHT = 60
        const val EDGE_PIXEL_DELTA = 30

        /**
         * Pure function expuesta para tests: toma un array de luminancia
         * submuestreado y devuelve el [FrameQuality] sin tocar Android.
         */
        fun analyzeLuminanceBuffer(
            samples: ByteArray,
            sampleW: Int,
            sampleH: Int,
            gridSize: Int = 6
        ): FrameQuality {
            if (samples.isEmpty() || sampleW <= 0 || sampleH <= 0) {
                return FrameQuality.Empty
            }
            val totalSamples = sampleW * sampleH
            val histogram = IntArray(256)
            var luminanceSum = 0L
            for (i in 0 until totalSamples) {
                val v = samples[i].toInt() and 0xFF
                histogram[v]++
                luminanceSum += v
            }
            val avg = luminanceSum.toDouble() / totalSamples
            var varianceAccum = 0.0
            for (i in 0 until 256) {
                val count = histogram[i]
                if (count == 0) continue
                val diff = i - avg
                varianceAccum += diff * diff * count
            }
            val variance = varianceAccum / totalSamples
            val normalizedVariance = (variance / (255.0 * 255.0)).coerceIn(0.0, 1.0)

            var edgeCount = 0
            val edgeThreshold = EDGE_PIXEL_DELTA
            for (y in 1 until sampleH) {
                val rowStart = y * sampleW
                val prevRowStart = (y - 1) * sampleW
                for (x in 1 until sampleW) {
                    val v = samples[rowStart + x].toInt() and 0xFF
                    val right = samples[rowStart + x - 1].toInt() and 0xFF
                    val up = samples[prevRowStart + x].toInt() and 0xFF
                    if (abs(v - right) > edgeThreshold || abs(v - up) > edgeThreshold) {
                        edgeCount++
                    }
                }
            }
            val edgeRatio = edgeCount.toDouble() / totalSamples

            val cellWidth = sampleW / gridSize
            val cellHeight = sampleH / gridSize
            val cellActive = IntArray(gridSize * gridSize)
            if (cellWidth > 0 && cellHeight > 0) {
                for (gy in 0 until gridSize) {
                    for (gx in 0 until gridSize) {
                        var cellMin = 255
                        var cellMax = 0
                        val xStart = gx * cellWidth
                        val xEnd = (gx + 1) * cellWidth
                        val yStart = gy * cellHeight
                        val yEnd = (gy + 1) * cellHeight
                        for (y in yStart until yEnd) {
                            for (x in xStart until xEnd) {
                                val v = samples[y * sampleW + x].toInt() and 0xFF
                                if (v < cellMin) cellMin = v
                                if (v > cellMax) cellMax = v
                            }
                        }
                        val cellRange = cellMax - cellMin
                        cellActive[gy * gridSize + gx] = if (cellRange >= 40) 1 else 0
                    }
                }
            }
            var activeCells = 0
            for (i in cellActive.indices) {
                activeCells += cellActive[i]
            }

            var minGx = gridSize
            var maxGx = -1
            var minGy = gridSize
            var maxGy = -1
            for (gy in 0 until gridSize) {
                for (gx in 0 until gridSize) {
                    if (cellActive[gy * gridSize + gx] == 1) {
                        if (gx < minGx) minGx = gx
                        if (gx > maxGx) maxGx = gx
                        if (gy < minGy) minGy = gy
                        if (gy > maxGy) maxGy = gy
                    }
                }
            }
            val hasContent = activeCells >= 2 && maxGx >= minGx && maxGy >= minGy
            val centerX: Float
            val centerY: Float
            val sizeRelative: Float
            if (hasContent) {
                centerX = (minGx + maxGx + 1) / (2f * gridSize)
                centerY = (minGy + maxGy + 1) / (2f * gridSize)
                sizeRelative = ((maxGx - minGx + 1) * (maxGy - minGy + 1)).toFloat() /
                    (gridSize * gridSize)
            } else {
                centerX = 0.5f
                centerY = 0.5f
                sizeRelative = 0f
            }

            val avgLuminance = (avg / 255.0).coerceIn(0.0, 1.0)
            val isTooDark = avgLuminance < 0.15
            val isTooBright = avgLuminance > 0.92
            val isUniform = normalizedVariance < 0.012 && edgeRatio < 0.015
            val isBlurry = edgeRatio < 0.015 && !isUniform

            return FrameQuality(
                avgLuminance = avgLuminance,
                variance = normalizedVariance,
                edgeRatio = edgeRatio,
                isUniform = isUniform,
                isTooDark = isTooDark,
                isTooBright = isTooBright,
                isBlurry = isBlurry,
                hasContent = hasContent,
                contentCenterX = centerX,
                contentCenterY = centerY,
                contentSize = sizeRelative
            )
        }
    }
}

internal fun laplacianEstimate(values: ByteArray, width: Int, height: Int): Double {
    if (width < 3 || height < 3) return 0.0
    var sum = 0.0
    var count = 0
    for (y in 1 until height - 1) {
        val rowStart = y * width
        for (x in 1 until width - 1) {
            val center = values[rowStart + x].toInt() and 0xFF
            val left = values[rowStart + x - 1].toInt() and 0xFF
            val right = values[rowStart + x + 1].toInt() and 0xFF
            val up = values[(y - 1) * width + x].toInt() and 0xFF
            val down = values[(y + 1) * width + x].toInt() and 0xFF
            val laplacian = abs(4 * center - left - right - up - down)
            sum += laplacian * laplacian
            count++
        }
    }
    if (count == 0) return 0.0
    return sqrt(sum / count)
}
