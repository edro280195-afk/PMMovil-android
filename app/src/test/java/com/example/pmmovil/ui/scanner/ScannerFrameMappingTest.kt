package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre la conversion de la posicion del codigo (fracciones de la imagen
 * analizada) a fracciones de la `PreviewView`, que recorta con FILL_CENTER.
 *
 * Es la parte del marco que persigue al codigo que no se puede validar sin
 * dispositivo, asi que se fija aqui con numeros concretos.
 */
class ScannerFrameMappingTest {

    /** Telefono 1080x2400 con analisis 9:16 (720x1280 enderezado). */
    private val viewWidth = 1080f
    private val viewHeight = 2400f
    private val portraitAspect = 720f / 1280f

    private fun candidate(
        centerX: Float,
        centerY: Float,
        widthRatio: Float = 0.2f,
        heightRatio: Float = 0.2f,
        aspect: Float = portraitAspect
    ) = BarcodeVisualCandidate(
        centerX = centerX,
        centerY = centerY,
        widthRatio = widthRatio,
        heightRatio = heightRatio,
        hasDecodedValue = false,
        sourceAspectRatio = aspect
    )

    @Test
    fun `un codigo en el centro del frame cae en el centro de la vista`() {
        val frame = candidate(centerX = 0.5f, centerY = 0.5f)
            .toViewFrame(viewWidth, viewHeight)!!

        assertEquals(0.5f, frame.centerX, 0.001f)
        assertEquals(0.5f, frame.centerY, 0.001f)
    }

    @Test
    fun `el recorte horizontal de FILL_CENTER se compensa`() {
        // La imagen se dibuja con 1350px de ancho sobre una vista de 1080,
        // asi que sobresalen 135px por lado: la fraccion 0.1 de la imagen
        // corresponde justo al borde izquierdo de la vista.
        val frame = candidate(centerX = 0.1f, centerY = 0.5f)
            .toViewFrame(viewWidth, viewHeight)!!

        assertEquals(0f, frame.centerX, 0.001f)
    }

    @Test
    fun `sin recorte vertical la coordenada Y se mapea directo`() {
        val frame = candidate(centerX = 0.5f, centerY = 0.25f)
            .toViewFrame(viewWidth, viewHeight)!!

        assertEquals(0.25f, frame.centerY, 0.001f)
    }

    @Test
    fun `el marco nunca se encoge por debajo del minimo`() {
        val frame = candidate(centerX = 0.5f, centerY = 0.5f, widthRatio = 0.01f, heightRatio = 0.01f)
            .toViewFrame(viewWidth, viewHeight)!!

        val ladoMenorPx = minOf(frame.width * viewWidth, frame.height * viewHeight)
        val minimoPx = minOf(viewWidth, viewHeight) * FRAME_LOCK_MIN_SIZE
        assertTrue(ladoMenorPx >= minimoPx - 0.5f)
    }

    @Test
    fun `un QR cuadrado se dibuja cuadrado`() {
        // Un QR real es cuadrado, asi que en la imagen 720x1280 ocupa distinta
        // fraccion de cada eje: 100px son 0.139 del ancho pero 0.078 del alto.
        val frame = candidate(
            centerX = 0.5f,
            centerY = 0.5f,
            widthRatio = 100f / 720f,
            heightRatio = 100f / 1280f
        ).toViewFrame(viewWidth, viewHeight)!!

        val anchoPx = frame.width * viewWidth
        val altoPx = frame.height * viewHeight
        assertEquals(anchoPx, altoPx, 1f)
    }

    @Test
    fun `un QR diminuto sigue siendo cuadrado despues de aplicar el minimo`() {
        // Este es el caso de la captura: el minimo se aplicaba por eje y
        // convertia el QR en un rectangulo el doble de alto que ancho.
        val frame = candidate(
            centerX = 0.5f,
            centerY = 0.5f,
            widthRatio = 20f / 720f,
            heightRatio = 20f / 1280f
        ).toViewFrame(viewWidth, viewHeight)!!

        val anchoPx = frame.width * viewWidth
        val altoPx = frame.height * viewHeight
        assertEquals(anchoPx, altoPx, 1f)
    }

    @Test
    fun `un codigo 1D ancho conserva su proporcion apaisada`() {
        val frame = candidate(
            centerX = 0.5f,
            centerY = 0.5f,
            widthRatio = 400f / 720f,
            heightRatio = 100f / 1280f
        ).toViewFrame(viewWidth, viewHeight)!!

        val anchoPx = frame.width * viewWidth
        val altoPx = frame.height * viewHeight
        assertEquals(4f, anchoPx / altoPx, 0.05f)
    }

    @Test
    fun `el marco deja holgura alrededor del codigo`() {
        val frame = candidate(centerX = 0.5f, centerY = 0.5f, widthRatio = 0.5f, heightRatio = 0.5f)
            .toViewFrame(viewWidth, viewHeight)!!

        // 0.5 de la imagen sobre la vista, mas el padding configurado.
        val sinHolgura = 0.5f * 1350f / viewWidth
        assertTrue(frame.width > sinHolgura)
    }

    @Test
    fun `una vista sin medir todavia no produce marco`() {
        assertNull(candidate(0.5f, 0.5f).toViewFrame(0f, 0f))
        assertNull(candidate(0.5f, 0.5f, aspect = 0f).toViewFrame(viewWidth, viewHeight))
    }

    @Test
    fun `con analisis apaisado el recorte se invierte al eje vertical`() {
        // Vista apaisada 2400x1080 con imagen 16:9: no hay recorte horizontal
        // pero si vertical, y el centro debe seguir cayendo en el centro.
        val frame = candidate(centerX = 0.5f, centerY = 0.5f, aspect = 1280f / 720f)
            .toViewFrame(2400f, 1080f)!!

        assertEquals(0.5f, frame.centerX, 0.001f)
        assertEquals(0.5f, frame.centerY, 0.001f)
    }
}
