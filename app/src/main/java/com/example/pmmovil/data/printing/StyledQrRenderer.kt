package com.example.pmmovil.data.printing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import kotlin.math.min

/** QR ya dibujado junto con el reporte de qué estilo terminó aplicándose. */
data class StyledQrResult(
    val bitmap: Bitmap,
    val report: QrRenderReport
)

/**
 * Dibuja códigos QR con estilo (módulos redondeados/fundidos, ojos con forma propia, logo al
 * centro) **garantizando que el resultado se pueda leer**.
 *
 * La garantía no descansa en el porcentaje teórico que tolera la corrección de errores: cada QR
 * generado se vuelve a decodificar con el lector de ZXing sobre el bitmap ya dibujado, tal como
 * lo vería un escáner. Si el estilo pedido no pasa esa prueba, se degrada por pasos —primero lo
 * cosmético, al final lo estructural— hasta encontrar uno que sí. En el peor caso se cae al QR
 * cuadrado de siempre, nunca a uno roto.
 */
class StyledQrRenderer {

    /**
     * Genera el QR de [value] para un recuadro de [targetSideDots] dots de lado.
     * Devuelve null solo si ni el QR más conservador se pudo generar (valor imposible de
     * codificar), caso en el que no se debe dibujar nada.
     */
    fun render(
        value: String,
        targetSideDots: Int,
        style: QrStyleOptions,
        logoBitmap: Bitmap?,
        logoAspectRatio: Float,
        verify: Boolean = true
    ): StyledQrResult? {
        if (value.isBlank() || targetSideDots <= 0) return null
        val requested = style.sanitized()

        // Modo preview: las miniaturas del selector de estilos son demasiado pequeñas para
        // decodificarse (y se regeneran a cada toque), así que se dibujan tal cual sin verificar.
        // La verificación real siempre corre en el render de impresión.
        if (!verify) {
            val draft = runCatching {
                drawQr(value, targetSideDots, requested, logoBitmap, logoAspectRatio, errorCorrectionFor(requested))
            }.getOrNull() ?: return null
            return StyledQrResult(
                bitmap = draft.bitmap,
                report = QrRenderReport(
                    requested = requested,
                    applied = requested,
                    downgraded = false,
                    verified = false,
                    moduleCount = draft.moduleCount,
                    moduleSizeDots = draft.moduleSize
                )
            )
        }

        for (candidate in degradationChain(requested, logoBitmap)) {
            val hasLogo = candidate.logoEnabled && logoBitmap != null
            val levels = QrErrorCorrectionPlan.levelsToTry(errorCorrectionFor(candidate), hasLogo)
            for ((levelIndex, level) in levels.withIndex()) {
                val attempt = runCatching {
                    drawQr(value, targetSideDots, candidate, logoBitmap, logoAspectRatio, level)
                }.getOrElse {
                    Log.w(TAG, "No se pudo dibujar el QR con $candidate y corrección $level: ${it.message}")
                    null
                } ?: continue

                if (decodesTo(attempt.bitmap, value)) {
                    if (levelIndex > 0) {
                        // El nivel preferido no pasó pero otro sí: es el falso negativo de ZXing que describe
                        // QrErrorCorrectionPlan. Queda en el log para poder contarlos.
                        Log.i(TAG, "El QR de \"$value\" no se leyó con ${levels.first()}; se imprime con $level")
                    }
                    return StyledQrResult(
                        bitmap = attempt.bitmap,
                        report = QrRenderReport(
                            requested = requested,
                            applied = candidate,
                            downgraded = candidate != requested,
                            verified = true,
                            moduleCount = attempt.moduleCount,
                            moduleSizeDots = attempt.moduleSize,
                            errorCorrection = level.name
                        )
                    )
                }
                Log.w(TAG, "El QR con estilo $candidate y corrección $level no pasó la verificación de lectura")
                attempt.bitmap.recycle()
            }
            Log.w(TAG, "Ningún nivel de corrección pasó con el estilo $candidate; se prueba el siguiente estilo")
        }
        return null
    }

    /**
     * Estilos a probar, del más vistoso al más seguro. El orden degrada primero lo que menos
     * cuesta perder: el tamaño del logo, luego la forma de los módulos, luego la de los ojos y
     * al final el logo completo.
     */
    private fun degradationChain(style: QrStyleOptions, logoBitmap: Bitmap?): List<QrStyleOptions> {
        val hasLogo = style.logoEnabled && logoBitmap != null
        val base = if (hasLogo) style else style.copy(logoEnabled = false)
        val chain = LinkedHashSet<QrStyleOptions>()

        chain += base
        if (hasLogo && base.logoScale > QrStyleOptions.LOGO_SCALE_MIN) {
            chain += base.copy(logoScale = (base.logoScale * 0.82f).coerceAtLeast(QrStyleOptions.LOGO_SCALE_MIN))
        }
        if (base.moduleShape != QrModuleShape.SQUARE) {
            // FLUID y ROUNDED conservan más tinta que DOT, así que son el escalón intermedio natural.
            if (base.moduleShape == QrModuleShape.DOT) chain += base.copy(moduleShape = QrModuleShape.ROUNDED)
            chain += base.copy(moduleShape = QrModuleShape.SQUARE)
        }
        if (base.eyeFrameShape != QrEyeShape.SQUARE || base.eyeBallShape != QrEyeShape.SQUARE) {
            chain += base.copy(
                moduleShape = QrModuleShape.SQUARE,
                eyeFrameShape = QrEyeShape.SQUARE,
                eyeBallShape = QrEyeShape.SQUARE
            )
        }
        if (hasLogo) {
            chain += QrStyleOptions(
                moduleShape = QrModuleShape.SQUARE,
                eyeFrameShape = QrEyeShape.SQUARE,
                eyeBallShape = QrEyeShape.SQUARE,
                logoEnabled = false,
                quietZoneModules = base.quietZoneModules
            )
        }
        return chain.toList()
    }

    private class DrawResult(val bitmap: Bitmap, val moduleCount: Int, val moduleSize: Int)

    private fun drawQr(
        value: String,
        targetSideDots: Int,
        style: QrStyleOptions,
        logoBitmap: Bitmap?,
        logoAspectRatio: Float,
        level: ErrorCorrectionLevel
    ): DrawResult {
        val matrix = Encoder.encode(value, level).matrix
        val moduleCount = matrix.width
        val quiet = style.quietZoneModules
        val totalModules = moduleCount + quiet * 2

        // Se escala con un factor entero de dots por módulo: así todos los módulos miden
        // exactamente lo mismo y ninguno se parte a la mitad de un dot al imprimir.
        val moduleSize = (targetSideDots / totalModules).coerceAtLeast(1)
        val canvasSide = moduleSize * totalModules

        val bitmap = Bitmap.createBitmap(canvasSide, canvasSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.style = Paint.Style.FILL
        }
        val originPx = quiet * moduleSize
        fun px(moduleIndex: Int): Float = (originPx + moduleIndex * moduleSize).toFloat()

        val logoRect = logoBitmap
            ?.takeIf { style.logoEnabled }
            ?.let { logoClearRect(style, logoAspectRatio, originPx.toFloat(), moduleCount * moduleSize.toFloat()) }

        // 1) Módulos de datos: se acumulan en un solo Path para que las formas fundidas queden
        //    sin costuras entre módulos vecinos (dibujarlos uno por uno deja hilos blancos).
        val dataPath = Path()
        for (row in 0 until moduleCount) {
            for (col in 0 until moduleCount) {
                if (!matrix.isOn(col, row)) continue
                if (isEyeArea(row, col, moduleCount)) continue
                val cell = RectF(px(col), px(row), px(col) + moduleSize, px(row) + moduleSize)
                if (style.clearModulesUnderLogo && logoRect != null && RectF.intersects(logoRect, cell)) continue
                appendModule(dataPath, cell, style.moduleShape, matrix, row, col, moduleCount)
            }
        }
        canvas.drawPath(dataPath, ink)

        // 2) Ojos: marco (anillo 7x7) y bola (3x3) con su propia forma.
        val eyeOrigins = listOf(0 to 0, 0 to (moduleCount - 7), (moduleCount - 7) to 0)
        for ((eyeRow, eyeCol) in eyeOrigins) {
            val frameOuter = RectF(px(eyeCol), px(eyeRow), px(eyeCol) + 7f * moduleSize, px(eyeRow) + 7f * moduleSize)
            val frameInner = RectF(
                px(eyeCol) + moduleSize,
                px(eyeRow) + moduleSize,
                px(eyeCol) + 6f * moduleSize,
                px(eyeRow) + 6f * moduleSize
            )
            // El contorno interior va en sentido contrario al exterior: así el hueco del anillo se respeta con
            // cualquier regla de relleno. Con los dos en el mismo sentido, el motor gráfico del Honeywell CT60
            // (Android 9, build 88.00.13) pintaba el marco como un cuadro macizo aunque el path fuera EVEN_ODD,
            // el QR salía con los tres ojos rellenos, la verificación de lectura fallaba en los cuatro niveles y
            // Entrada Bodega no imprimía nada en ese equipo.
            val framePath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                appendEyeShape(this, frameOuter, style.eyeFrameShape, Path.Direction.CW)
                appendEyeShape(this, frameInner, style.eyeFrameShape, Path.Direction.CCW)
            }
            canvas.drawPath(framePath, ink)

            val ball = RectF(
                px(eyeCol) + 2f * moduleSize,
                px(eyeRow) + 2f * moduleSize,
                px(eyeCol) + 5f * moduleSize,
                px(eyeRow) + 5f * moduleSize
            )
            val ballPath = Path().apply { appendEyeShape(this, ball, style.eyeBallShape, Path.Direction.CW) }
            canvas.drawPath(ballPath, ink)
        }

        // 3) Placa + logo al centro.
        if (logoBitmap != null && style.logoEnabled && logoRect != null) {
            drawLogo(canvas, logoBitmap, logoRect, style)
        }

        return DrawResult(bitmap, moduleCount, moduleSize)
    }

    /**
     * Rectángulo que ocupa el logo, con la proporción real de la imagen para no dejar espacio
     * blanco muerto arriba y abajo (el logo de IMEX es una tira ~2.4 veces más ancha que alta).
     */
    private fun logoClearRect(
        style: QrStyleOptions,
        aspectRatio: Float,
        origin: Float,
        qrPixelSide: Float
    ): RectF {
        val safeRatio = if (aspectRatio > 0f) aspectRatio else 1f
        val width = qrPixelSide * style.logoScale
        val height = width / safeRatio
        val center = origin + qrPixelSide / 2f
        val padding = min(width, height) * PLATE_PADDING_RATIO
        return if (style.logoPlate == QrLogoPlate.CIRCLE) {
            // El disco debe cubrir la diagonal del logo para que no se salga por los lados.
            val radius = min(
                (Math.hypot(width.toDouble(), height.toDouble()) / 2.0).toFloat() + padding,
                qrPixelSide * MAX_PLATE_SIDE_RATIO / 2f
            )
            RectF(center - radius, center - radius, center + radius, center + radius)
        } else {
            RectF(
                center - width / 2f - padding,
                center - height / 2f - padding,
                center + width / 2f + padding,
                center + height / 2f + padding
            )
        }
    }

    private fun drawLogo(canvas: Canvas, logo: Bitmap, plateRect: RectF, style: QrStyleOptions) {
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.style = Paint.Style.FILL
        }
        when (style.logoPlate) {
            QrLogoPlate.NONE -> Unit
            QrLogoPlate.TIGHT -> canvas.drawRect(plateRect, platePaint)
            QrLogoPlate.ROUNDED -> {
                val radius = min(plateRect.width(), plateRect.height()) * PLATE_CORNER_RATIO
                canvas.drawRoundRect(plateRect, radius, radius, platePaint)
            }
            QrLogoPlate.CIRCLE -> canvas.drawOval(plateRect, platePaint)
        }

        // El logo se dibuja centrado dentro de la placa, con su proporción intacta.
        val padding = min(plateRect.width(), plateRect.height()) * PLATE_PADDING_RATIO
        val available = RectF(
            plateRect.left + padding,
            plateRect.top + padding,
            plateRect.right - padding,
            plateRect.bottom - padding
        )
        val scale = min(available.width() / logo.width, available.height() / logo.height)
        val drawWidth = logo.width * scale
        val drawHeight = logo.height * scale
        val destination = RectF(
            plateRect.centerX() - drawWidth / 2f,
            plateRect.centerY() - drawHeight / 2f,
            plateRect.centerX() + drawWidth / 2f,
            plateRect.centerY() + drawHeight / 2f
        )
        canvas.drawBitmap(logo, null, destination, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
    }

    /** Agrega un módulo de datos al path con la forma pedida. */
    private fun appendModule(
        path: Path,
        cell: RectF,
        shape: QrModuleShape,
        matrix: ByteMatrix,
        row: Int,
        col: Int,
        moduleCount: Int
    ) {
        val size = cell.width()
        when (shape) {
            QrModuleShape.SQUARE -> path.addRect(cell, Path.Direction.CW)
            QrModuleShape.ROUNDED -> {
                val radius = size * ROUNDED_MODULE_RATIO
                path.addRoundRect(cell, radius, radius, Path.Direction.CW)
            }
            QrModuleShape.DOT -> path.addOval(cell, Path.Direction.CW)
            QrModuleShape.FLUID -> {
                // Cada esquina se redondea solo si los dos vecinos que la forman están apagados.
                // Así los módulos en fila quedan pegados formando barras continuas y solo las
                // puntas libres se suavizan — el efecto "gota" de los QR de marca.
                val radius = size * FLUID_MODULE_RATIO
                val up = matrix.isOn(col, row - 1, moduleCount)
                val down = matrix.isOn(col, row + 1, moduleCount)
                val left = matrix.isOn(col - 1, row, moduleCount)
                val right = matrix.isOn(col + 1, row, moduleCount)
                val radii = floatArrayOf(
                    cornerRadius(radius, up, left), cornerRadius(radius, up, left),
                    cornerRadius(radius, up, right), cornerRadius(radius, up, right),
                    cornerRadius(radius, down, right), cornerRadius(radius, down, right),
                    cornerRadius(radius, down, left), cornerRadius(radius, down, left)
                )
                path.addRoundRect(cell, radii, Path.Direction.CW)
            }
        }
    }

    private fun cornerRadius(radius: Float, neighborA: Boolean, neighborB: Boolean): Float =
        if (!neighborA && !neighborB) radius else 0f

    private fun appendEyeShape(path: Path, rect: RectF, shape: QrEyeShape, direction: Path.Direction) {
        val size = min(rect.width(), rect.height())
        when (shape) {
            QrEyeShape.SQUARE -> path.addRect(rect, direction)
            QrEyeShape.ROUNDED -> {
                val radius = size * EYE_ROUNDED_RATIO
                path.addRoundRect(rect, radius, radius, direction)
            }
            QrEyeShape.CIRCLE -> path.addOval(rect, direction)
            QrEyeShape.LEAF -> {
                // Dos esquinas opuestas muy redondeadas y las otras dos rectas: forma de hoja.
                val radius = size * EYE_LEAF_RATIO
                val radii = floatArrayOf(radius, radius, 0f, 0f, radius, radius, 0f, 0f)
                path.addRoundRect(rect, radii, direction)
            }
        }
    }

    /**
     * Corrección de errores según lo exigente que sea el estilo: alta con logo (hay módulos
     * tapados que reconstruir), intermedia con formas no cuadradas (los bordes suavizados dejan
     * menos tinta por módulo) y la estándar cuando el QR es clásico.
     */
    private fun errorCorrectionFor(style: QrStyleOptions): ErrorCorrectionLevel = when {
        style.logoEnabled -> ErrorCorrectionLevel.H
        style.moduleShape != QrModuleShape.SQUARE ||
            style.eyeFrameShape != QrEyeShape.SQUARE ||
            style.eyeBallShape != QrEyeShape.SQUARE -> ErrorCorrectionLevel.Q
        else -> ErrorCorrectionLevel.M
    }

    /** Los 7x7 módulos de cada ojo: los dibuja [drawQr] aparte, con su propia forma. */
    private fun isEyeArea(row: Int, col: Int, moduleCount: Int): Boolean {
        val topLeft = row < 7 && col < 7
        val topRight = row < 7 && col >= moduleCount - 7
        val bottomLeft = row >= moduleCount - 7 && col < 7
        return topLeft || topRight || bottomLeft
    }

    /** Decodifica el bitmap ya dibujado y confirma que reproduce exactamente [expected]. */
    private fun decodesTo(bitmap: Bitmap, expected: String): Boolean = runCatching {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        val decoded = QRCodeReader().decode(
            BinaryBitmap(HybridBinarizer(source)),
            mapOf(DecodeHintType.TRY_HARDER to true)
        )
        decoded.text == expected
    }.getOrDefault(false)

    private fun ByteMatrix.isOn(x: Int, y: Int): Boolean = get(x, y).toInt() == 1

    private fun ByteMatrix.isOn(x: Int, y: Int, moduleCount: Int): Boolean =
        x in 0 until moduleCount && y in 0 until moduleCount && isOn(x, y)

    private companion object {
        const val TAG = "StyledQrRenderer"
        const val ROUNDED_MODULE_RATIO = 0.30f
        const val FLUID_MODULE_RATIO = 0.50f
        const val EYE_ROUNDED_RATIO = 0.28f
        const val EYE_LEAF_RATIO = 0.50f
        const val PLATE_PADDING_RATIO = 0.16f
        const val PLATE_CORNER_RATIO = 0.22f
        const val MAX_PLATE_SIDE_RATIO = 0.42f
    }
}
