package com.example.pmmovil.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifica que una anotación hecha sobre la página que ve el usuario termine en
 * el mismo lugar dentro del PDF guardado, incluyendo páginas rotadas y páginas
 * con un CropBox que no arranca en el origen.
 */
class PdfPageFrameTest {

    private val letterWidth = 612f
    private val letterHeight = 792f

    private fun letter(rotation: Int) = PdfPageFrame(
        originX = 0f,
        originY = 0f,
        boxWidth = letterWidth,
        boxHeight = letterHeight,
        rotationDegrees = rotation
    )

    private fun point(x: Float, y: Float) = PdfNormalizedPoint(x, y)

    private fun assertMapsTo(
        frame: PdfPageFrame,
        source: PdfNormalizedPoint,
        expectedX: Float,
        expectedY: Float
    ) {
        assertEquals(expectedX, frame.x(source), 0.01f)
        assertEquals(expectedY, frame.y(source), 0.01f)
    }

    @Test
    fun `pagina sin rotar conserva el ancho y voltea el eje vertical`() {
        val frame = letter(rotation = 0)

        assertEquals(letterWidth, frame.displayWidth, 0.01f)
        assertEquals(letterHeight, frame.displayHeight, 0.01f)
        // Arriba a la izquierda en pantalla es arriba a la izquierda en el PDF.
        assertMapsTo(frame, point(0f, 0f), 0f, letterHeight)
        assertMapsTo(frame, point(1f, 1f), letterWidth, 0f)
        assertMapsTo(frame, point(0.5f, 0.25f), 306f, 594f)
    }

    @Test
    fun `pagina rotada 90 grados intercambia ancho y alto`() {
        val frame = letter(rotation = 90)

        assertEquals(letterHeight, frame.displayWidth, 0.01f)
        assertEquals(letterWidth, frame.displayHeight, 0.01f)
        // El visor gira la página en sentido horario: la esquina superior
        // izquierda de la pantalla cae en la inferior izquierda del PDF.
        assertMapsTo(frame, point(0f, 0f), 0f, 0f)
        assertMapsTo(frame, point(1f, 0f), 0f, letterHeight)
        assertMapsTo(frame, point(0f, 1f), letterWidth, 0f)
        assertMapsTo(frame, point(1f, 1f), letterWidth, letterHeight)
    }

    @Test
    fun `pagina rotada 180 grados invierte los dos ejes`() {
        val frame = letter(rotation = 180)

        assertEquals(letterWidth, frame.displayWidth, 0.01f)
        assertEquals(letterHeight, frame.displayHeight, 0.01f)
        assertMapsTo(frame, point(0f, 0f), letterWidth, 0f)
        assertMapsTo(frame, point(1f, 1f), 0f, letterHeight)
    }

    @Test
    fun `pagina rotada 270 grados intercambia ancho y alto al otro lado`() {
        val frame = letter(rotation = 270)

        assertEquals(letterHeight, frame.displayWidth, 0.01f)
        assertEquals(letterWidth, frame.displayHeight, 0.01f)
        assertMapsTo(frame, point(0f, 0f), letterWidth, letterHeight)
        assertMapsTo(frame, point(1f, 0f), letterWidth, 0f)
        assertMapsTo(frame, point(0f, 1f), 0f, letterHeight)
    }

    @Test
    fun `cropbox desplazado corre la anotacion junto con la pagina`() {
        val frame = PdfPageFrame(
            originX = 36f,
            originY = 54f,
            boxWidth = 540f,
            boxHeight = 684f,
            rotationDegrees = 0
        )

        assertMapsTo(frame, point(0f, 0f), 36f, 738f)
        assertMapsTo(frame, point(1f, 1f), 576f, 54f)
        assertMapsTo(frame, point(0.5f, 0.5f), 306f, 396f)
    }

    @Test
    fun `una rotacion negativa se normaliza igual que su equivalente positiva`() {
        val negative = letter(rotation = -90)
        val positive = letter(rotation = 270)
        val sample = point(0.3f, 0.7f)

        assertEquals(positive.x(sample), negative.x(sample), 0.01f)
        assertEquals(positive.y(sample), negative.y(sample), 0.01f)
        assertEquals(positive.displayWidth, negative.displayWidth, 0.01f)
    }

    @Test
    fun `las cuatro esquinas de la pagina rotada siguen siendo las cuatro esquinas`() {
        listOf(0, 90, 180, 270).forEach { rotation ->
            val frame = letter(rotation)
            val corners = listOf(
                point(0f, 0f),
                point(1f, 0f),
                point(0f, 1f),
                point(1f, 1f)
            ).map { frame.x(it) to frame.y(it) }

            assertEquals(
                "La rotación $rotation debe mapear a esquinas distintas",
                4,
                corners.toSet().size
            )
            corners.forEach { (x, y) ->
                assertEquals("x fuera de la página en $rotation", true, x in 0f..letterWidth)
                assertEquals("y fuera de la página en $rotation", true, y in 0f..letterHeight)
            }
        }
    }
}
