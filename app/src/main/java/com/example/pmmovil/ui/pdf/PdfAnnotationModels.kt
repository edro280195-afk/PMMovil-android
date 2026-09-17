package com.example.pmmovil.ui.pdf

/**
 * Cómo se encaja la página dentro del panel.
 *
 * El panel es apaisado y las facturas son verticales, así que [PAGE] deja la
 * hoja pequeña con mucho margen a los lados: sirve para ubicarse. [WIDTH] la
 * dibuja al ancho del panel y se recorre en vertical, que es como se leen las
 * partidas.
 */
enum class PdfFitMode {
    PAGE,
    WIDTH
}

/** Herramientas disponibles en el visor de documentos. */
enum class PdfAnnotationTool {
    PAN,
    INK,
    HIGHLIGHT,
    TEXT
}

data class PdfNormalizedPoint(
    val x: Float,
    val y: Float
)

/**
 * Coordenadas normalizadas respecto a la página: (0, 0) es la esquina
 * superior izquierda y (1, 1) la esquina inferior derecha.
 */
data class PdfAnnotation(
    val pageIndex: Int,
    val tool: PdfAnnotationTool,
    val points: List<PdfNormalizedPoint>,
    val color: Int,
    val widthFraction: Float = 0.006f,
    val text: String? = null
)
