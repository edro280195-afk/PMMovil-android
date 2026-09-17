package com.example.pmmovil.ui.pdf

import android.content.Context
import android.graphics.Color
import android.os.Environment
import com.example.pmmovil.data.model.DocumentoPdfDto
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

fun saveAnnotatedPdfCopy(
    context: Context,
    sourceFile: File,
    annotations: List<PdfAnnotation>,
    selectedDocument: DocumentoPdfDto?
): File {
    val directory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        "PMMovil"
    ).apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val reference = selectedDocument?.creferencia
        ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
        ?: "documento"
    val outputFile = File(directory, "${reference}_anotado_$timestamp.pdf")

    PDDocument.load(sourceFile).use { document ->
        annotations.groupBy(PdfAnnotation::pageIndex).forEach { (pageIndex, pageAnnotations) ->
            if (pageIndex !in 0 until document.numberOfPages) return@forEach
            val page = document.getPage(pageIndex)
            val frame = pageFrame(page)
            PDPageContentStream(
                document,
                page,
                PDPageContentStream.AppendMode.APPEND,
                true,
                true
            ).use { stream ->
                pageAnnotations.forEach { annotation ->
                    drawAnnotation(stream, frame, annotation)
                }
            }
        }
        document.save(outputFile)
    }
    return outputFile
}

private fun pageFrame(page: PDPage): PdfPageFrame {
    val box: PDRectangle = page.cropBox ?: page.mediaBox
    return PdfPageFrame(
        originX = box.lowerLeftX,
        originY = box.lowerLeftY,
        boxWidth = box.width,
        boxHeight = box.height,
        rotationDegrees = page.rotation
    )
}

private fun drawAnnotation(
    stream: PDPageContentStream,
    frame: PdfPageFrame,
    annotation: PdfAnnotation
) {
    if (annotation.points.isEmpty()) return

    when (annotation.tool) {
        PdfAnnotationTool.INK -> drawInk(stream, frame, annotation)
        PdfAnnotationTool.HIGHLIGHT -> drawHighlight(stream, frame, annotation)
        PdfAnnotationTool.TEXT -> drawText(stream, frame, annotation)
        PdfAnnotationTool.PAN -> Unit
    }
}

private fun drawInk(
    stream: PDPageContentStream,
    frame: PdfPageFrame,
    annotation: PdfAnnotation
) {
    val points = annotation.points
    stream.saveGraphicsState()
    stream.setStrokingColor(
        Color.red(annotation.color),
        Color.green(annotation.color),
        Color.blue(annotation.color)
    )
    stream.setLineWidth((annotation.widthFraction * frame.displayWidth).coerceAtLeast(1f))
    stream.setLineCapStyle(1)
    stream.setLineJoinStyle(1)
    val first = points.first()
    stream.moveTo(frame.x(first), frame.y(first))
    if (points.size == 1) {
        // Un punto suelto se dibuja como un segmento de longitud cero; con el
        // extremo redondeado el visor lo pinta como un punto.
        stream.lineTo(frame.x(first), frame.y(first))
    } else {
        points.drop(1).forEach { point ->
            stream.lineTo(frame.x(point), frame.y(point))
        }
    }
    stream.stroke()
    stream.restoreGraphicsState()
}

private fun drawHighlight(
    stream: PDPageContentStream,
    frame: PdfPageFrame,
    annotation: PdfAnnotation
) {
    val start = annotation.points.first()
    val end = annotation.points.getOrElse(1) { start }
    val startX = frame.x(start)
    val startY = frame.y(start)
    val endX = frame.x(end)
    val endY = frame.y(end)
    val left = min(startX, endX)
    val right = max(startX, endX)
    val bottom = min(startY, endY)
    val top = max(startY, endY)

    // El estado gráfico se guarda y restaura para que la transparencia del
    // resaltado no se contagie a las anotaciones que se dibujen después.
    stream.saveGraphicsState()
    stream.setGraphicsStateParameters(
        PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = 0.35f }
    )
    stream.setNonStrokingColor(
        Color.red(annotation.color),
        Color.green(annotation.color),
        Color.blue(annotation.color)
    )
    stream.addRect(
        left,
        bottom,
        (right - left).coerceAtLeast(2f),
        (top - bottom).coerceAtLeast(6f)
    )
    stream.fill()
    stream.restoreGraphicsState()
}

private fun drawText(
    stream: PDPageContentStream,
    frame: PdfPageFrame,
    annotation: PdfAnnotation
) {
    val text = annotation.text?.trim()?.takeIf(String::isNotEmpty) ?: return
    val point = annotation.points.first()
    val fontSize = (frame.displayWidth * PDF_TEXT_SIZE_FRACTION).coerceIn(8f, 28f)
    stream.saveGraphicsState()
    stream.setNonStrokingColor(
        Color.red(annotation.color),
        Color.green(annotation.color),
        Color.blue(annotation.color)
    )
    stream.beginText()
    stream.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
    stream.setTextMatrix(
        Matrix.getRotateInstance(
            frame.textRotationRadians,
            frame.x(point),
            frame.y(point)
        )
    )
    // Helvetica estándar usa WinAnsi, así que acentos y eñes salen bien. Si el
    // usuario pegó un carácter fuera de esa codificación se cae a texto simple
    // en vez de perder la nota completa.
    runCatching { stream.showText(text) }.onFailure {
        stream.showText(text.filter { character -> character.code in 32..126 })
    }
    stream.endText()
    stream.restoreGraphicsState()
}
