package com.example.pmmovil.data.printing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Dibuja cualquier etiqueta como bitmap. Todas las coordenadas provienen de
 * [ZebraLabelLayout] y se interpretan en la orientación definida por la plantilla.
 */
@Singleton
class LabelBitmapRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Bitmaps de cada imagen del catálogo [LabelImageAsset], decodificados una sola vez. */
    private val imageAssets: Map<String, Bitmap> by lazy {
        LabelImageAsset.values().mapNotNull { asset ->
            runCatching { BitmapFactory.decodeResource(context.resources, asset.drawableRes) }
                .getOrNull()
                ?.let { asset.id to it }
        }.toMap()
    }

    private val styledQrRenderer = StyledQrRenderer()

    /**
     * Renderiza la etiqueta en la misma orientación que usa el diseñador visual. No rota el
     * bitmap; el resultado conserva las dimensiones de la plantilla.
     *
     * [onQrReport] recibe el resultado de la verificación de lectura del QR (qué estilo terminó
     * imprimiéndose y si hubo que degradarlo). El diseñador lo usa para avisar en vivo; el resto
     * de las pantallas simplemente lo ignora.
     */
    fun renderReadingOrientation(
        label: ZebraLabelData,
        layout: ZebraLabelLayout,
        onQrReport: ((QrRenderReport) -> Unit)? = null
    ): Bitmap {
        val reading = Bitmap.createBitmap(layout.widthDots, layout.heightDots, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(reading).apply { drawColor(Color.WHITE) }

        drawLogo(canvas, layout.logo)
        drawQr(canvas, layout.qr, label.qrValue, layout.qrStyle, onQrReport)

        drawText(canvas, layout.arrivalLabel, "F. ARRIBO:")
        drawText(canvas, layout.arrivalDate, label.fechaArribo)
        drawText(canvas, layout.reference, label.numeroEntrada)
        drawText(canvas, layout.customerLabel, "CLIENTE:")
        drawText(canvas, layout.customerValue, label.cliente)
        drawText(canvas, layout.boxLabel, "CAJA:")
        drawText(canvas, layout.boxValue, label.caja)
        drawText(canvas, layout.guideLabel, "GUIA:")
        drawText(canvas, layout.guideValue, label.guia)
        drawText(canvas, layout.locationLabel, "UBICA:")
        drawText(canvas, layout.locationValue, label.ubicaciones)
        drawCounter(canvas, layout.counter, label, layout)
        layout.customTexts.forEach { customText ->
            drawText(canvas, customText.element, customText.text)
        }
        layout.customImages.forEach { customImage ->
            drawCustomImage(canvas, customImage)
        }

        return reading
    }

    /** Renderiza una etiqueta portrait 1200 x 1800 lista para codificarse como ^GF. */
    fun render(label: ZebraLabelData, layout: ZebraLabelLayout): Bitmap {
        val reading = renderReadingOrientation(label, layout)
        return try {
            rotateToPortrait(reading, layout)
        } finally {
            reading.recycle()
        }
    }

    /** Mantiene compatibilidad para herramientas de diagnóstico que no reciben un layout. */
    @Deprecated("Pasa el layout activo para que la salida respete el diseñador")
    fun render(label: ZebraLabelData): Bitmap = render(label, ZebraLabelLayout.reference())

    private fun drawLogo(canvas: Canvas, element: ZebraLabelElement) {
        if (!element.visible) return
        val bitmap = imageAssets[LabelImageAsset.IMEX_LOGO.id] ?: return
        drawFittedBitmap(canvas, bitmap, element)
    }

    /** Dibuja una imagen libre (agregada desde la pestaña Imágenes del diseñador). */
    private fun drawCustomImage(canvas: Canvas, customImage: ZebraCustomImage) {
        val element = customImage.element
        if (!element.visible) return
        val bitmap = imageAssets[customImage.assetId] ?: return
        drawFittedBitmap(canvas, bitmap, element)
    }

    /** Ajusta [bitmap] dentro de [element] conservando su proporción, centrado. */
    private fun drawFittedBitmap(canvas: Canvas, bitmap: Bitmap, element: ZebraLabelElement) {
        val scale = min(element.width / bitmap.width.toFloat(), element.height / bitmap.height.toFloat())
        val drawWidth = bitmap.width * scale
        val drawHeight = bitmap.height * scale
        val left = element.x + (element.width - drawWidth) / 2f
        val top = element.y + (element.height - drawHeight) / 2f
        val destination = RectF(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true })
    }

    /**
     * Dibuja el código QR delegando en [StyledQrRenderer], que se encarga del estilo (forma de
     * módulos, ojos, logo) y —sobre todo— de verificar decodificando el resultado antes de
     * devolverlo. Si ni el QR más conservador resultó legible, no se dibuja nada: es preferible
     * una etiqueta sin QR (que salta a la vista) que una con un QR roto que nadie nota hasta
     * que falla el escaneo en bodega.
     */
    private fun drawQr(
        canvas: Canvas,
        element: ZebraLabelElement,
        rawValue: String,
        style: QrStyleOptions,
        onQrReport: ((QrRenderReport) -> Unit)?
    ) {
        if (!element.visible) return
        val value = rawValue.trim()
        if (value.isEmpty()) return
        val side = min(element.width, element.height)
        if (side <= 0) return

        val result = styledQrRenderer.render(
            value = value,
            targetSideDots = side,
            style = style,
            logoBitmap = imageAssets[LabelImageAsset.IMEX_LOGO.id],
            logoAspectRatio = LabelImageAsset.IMEX_LOGO.aspectRatio
        )
        if (result == null) {
            Log.w(TAG, "Ningún estilo de QR resultó legible para \"$value\"; no se dibuja nada")
            return
        }
        onQrReport?.invoke(result.report)

        val qr = result.bitmap
        val left = element.x + (element.width - qr.width) / 2
        val top = element.y + (element.height - qr.height) / 2
        canvas.drawBitmap(qr, left.toFloat(), top.toFloat(), null)
        qr.recycle()
    }

    private fun drawCounter(canvas: Canvas, element: ZebraLabelElement, label: ZebraLabelData, layout: ZebraLabelLayout) {
        val isFolder = label.tipoEtiqueta.equals("CARPETA", ignoreCase = true)
        val text = if (isFolder) "CARPETA" else label.parcial
        if (text.isBlank() || !element.visible) return

        val counterElement = if (isFolder) {
            val fontHeight = (element.fontHeight * FOLDER_COUNTER_SCALE).roundToInt().coerceAtLeast(70)
            val fontWidth = (element.fontWidth * FOLDER_COUNTER_SCALE).roundToInt().coerceAtLeast(62)
            val top = element.y + (element.height * FOLDER_COUNTER_TOP_OFFSET).roundToInt()
            element.copy(
                y = top,
                height = (layout.heightDots - top).coerceAtLeast(fontHeight),
                fontHeight = fontHeight,
                fontWidth = fontWidth
            ).constrained(layout.widthDots, layout.heightDots)
        } else {
            element
        }
        drawText(canvas, counterElement, text)
    }

    private fun drawText(canvas: Canvas, element: ZebraLabelElement, text: String) {
        if (!element.visible || text.isBlank()) return

        canvas.save()

        val centerX = element.x + element.width / 2f
        val centerY = element.y + element.height / 2f
        if (element.rotation != 0) {
            canvas.rotate(element.rotation.toFloat(), centerX, centerY)
        }

        val rect = RectF(element.x.toFloat(), element.y.toFloat(), (element.x + element.width).toFloat(), (element.y + element.height).toFloat())

        if (element.inverted) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            canvas.drawRect(rect, bgPaint)
        }

        if (element.borderWidth > 0) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (element.inverted) Color.WHITE else Color.BLACK
                style = Paint.Style.STROKE
                strokeWidth = element.borderWidth.toFloat()
            }
            canvas.drawRect(rect, borderPaint)
        }

        val fontHeight = element.fontHeight.coerceAtLeast(MIN_TEXT_SIZE)
        val widthScale = element.fontWidth
            .takeIf { it > 0 }
            ?.toFloat()
            ?.div(fontHeight)
            ?: 1f
        val paint = TextPaint().apply {
            isAntiAlias = true
            color = if (element.inverted) Color.WHITE else Color.BLACK
            typeface = element.toTypeface()
            textSize = fontHeight.toFloat()
            textScaleX = widthScale
            textAlign = element.alignment.toPaintAlign()
            letterSpacing = element.letterSpacing
            if (element.underline) flags = flags or Paint.UNDERLINE_TEXT_FLAG
        }
        val shown = TextUtils.ellipsize(text, paint, element.width.toFloat(), TextUtils.TruncateAt.END).toString()
        val baseline = element.y - paint.fontMetrics.ascent
        val drawX = when (element.alignment) {
            LabelAlignment.LEFT -> element.x.toFloat()
            LabelAlignment.CENTER -> centerX
            LabelAlignment.RIGHT -> (element.x + element.width).toFloat()
        }

        canvas.clipRect(rect)
        canvas.drawText(shown, drawX, baseline, paint)
        canvas.restore()
    }

    /** Negrita/cursiva ahora son independientes de la familia tipográfica elegida. */
    private fun ZebraLabelElement.toTypeface(): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(fontFamily.familyName, style)
    }

    private fun LabelAlignment.toPaintAlign(): Paint.Align = when (this) {
        LabelAlignment.LEFT -> Paint.Align.LEFT
        LabelAlignment.CENTER -> Paint.Align.CENTER
        LabelAlignment.RIGHT -> Paint.Align.RIGHT
    }

    private fun rotateToPortrait(reading: Bitmap, layout: ZebraLabelLayout): Bitmap {
        if (!layout.rotate) {
            return Bitmap.createBitmap(reading)
        }
        val matrix = Matrix().apply { postRotate(-90f) }
        return Bitmap.createBitmap(reading, 0, 0, layout.widthDots, layout.heightDots, matrix, false)
    }

    private companion object {
        const val TAG = "LabelBitmapRenderer"
        const val MIN_TEXT_SIZE = 12
        const val FOLDER_COUNTER_SCALE = 0.42f
        const val FOLDER_COUNTER_TOP_OFFSET = 0.36f
    }
}
