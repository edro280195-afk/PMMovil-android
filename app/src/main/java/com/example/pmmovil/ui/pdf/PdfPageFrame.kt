package com.example.pmmovil.ui.pdf

/**
 * Traduce las coordenadas normalizadas de una anotación (relativas a la página
 * **tal como se ve** en el visor, con el origen arriba a la izquierda) al
 * espacio de usuario del PDF, donde el origen está abajo a la izquierda.
 *
 * Hay dos detalles que no se pueden ignorar o la marca acaba en otro lugar del
 * documento guardado:
 *
 * - el visor dibuja el CropBox, que no siempre empieza en (0, 0);
 * - el visor aplica la rotación `/Rotate` de la página, así que en documentos
 *   escaneados apaisados el ancho y el alto vienen intercambiados respecto a
 *   lo que reporta PDFBox.
 *
 * @param originX esquina izquierda del recuadro visible, en puntos.
 * @param originY esquina inferior del recuadro visible, en puntos.
 * @param boxWidth ancho del recuadro visible sin rotar.
 * @param boxHeight alto del recuadro visible sin rotar.
 * @param rotationDegrees grados que el visor gira la página en sentido horario.
 */
internal class PdfPageFrame(
    private val originX: Float,
    private val originY: Float,
    private val boxWidth: Float,
    private val boxHeight: Float,
    rotationDegrees: Int
) {

    private val rotation: Int = ((rotationDegrees % 360) + 360) % 360
    private val isQuarterTurn: Boolean = rotation == 90 || rotation == 270

    /** Ancho de la página como la ve el usuario en pantalla. */
    val displayWidth: Float = if (isQuarterTurn) boxHeight else boxWidth

    /** Alto de la página como la ve el usuario en pantalla. */
    val displayHeight: Float = if (isQuarterTurn) boxWidth else boxHeight

    /** Giro que hay que aplicar al texto para que se lea derecho. */
    val textRotationRadians: Double = Math.toRadians(rotation.toDouble())

    fun x(point: PdfNormalizedPoint): Float = originX + when (rotation) {
        90 -> point.y * boxWidth
        180 -> (1f - point.x) * boxWidth
        270 -> (1f - point.y) * boxWidth
        else -> point.x * boxWidth
    }

    fun y(point: PdfNormalizedPoint): Float = originY + when (rotation) {
        90 -> point.x * boxHeight
        180 -> point.y * boxHeight
        270 -> (1f - point.x) * boxHeight
        else -> (1f - point.y) * boxHeight
    }
}
