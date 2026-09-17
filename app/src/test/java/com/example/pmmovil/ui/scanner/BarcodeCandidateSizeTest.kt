package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Cubre la medida que alimenta el auto-zoom.
 *
 * El caso que la motivo: `widthRatio` y `heightRatio` estan normalizados
 * contra ejes distintos, asi que un QR cuadrado de 200 px en un frame de
 * 1080x1920 daba 0.185 de ancho y 0.104 de alto. Usar cualquiera de los dos
 * como "tamano del codigo" hacia que el lazo del zoom persiguiera un objetivo
 * que dependia de la orientacion del telefono.
 */
class BarcodeCandidateSizeTest {

    private fun candidate(
        codeWidthPx: Float,
        codeHeightPx: Float,
        imageWidth: Int,
        imageHeight: Int
    ) = BarcodeVisualCandidate(
        centerX = 0.5f,
        centerY = 0.5f,
        widthRatio = codeWidthPx / imageWidth,
        heightRatio = codeHeightPx / imageHeight,
        hasDecodedValue = false,
        sourceAspectRatio = imageWidth.toFloat() / imageHeight.toFloat()
    )

    @Test
    fun `un QR cuadrado mide lo mismo en vertical que en horizontal`() {
        val portrait = candidate(200f, 200f, imageWidth = 1080, imageHeight = 1920)
        val landscape = candidate(200f, 200f, imageWidth = 1920, imageHeight = 1080)

        // 200 / 1080 = 0.185 en los dos casos: el lado corto del frame es 1080
        // se sostenga el equipo como se sostenga.
        assertEquals(0.185f, portrait.minSideFraction, 0.001f)
        assertEquals(0.185f, landscape.minSideFraction, 0.001f)
    }

    @Test
    fun `se queda con el lado mas corto del codigo`() {
        // Un Code 128 es mucho mas ancho que alto: manda su altura, que es la
        // que se queda sin pixeles primero.
        val barcode = candidate(600f, 120f, imageWidth = 1080, imageHeight = 1920)

        assertEquals(120f / 1080f, barcode.minSideFraction, 0.001f)
    }

    @Test
    fun `un frame sin dimensiones no produce basura`() {
        val broken = BarcodeVisualCandidate(
            centerX = 0.5f,
            centerY = 0.5f,
            widthRatio = 0.2f,
            heightRatio = 0.2f,
            hasDecodedValue = false,
            sourceAspectRatio = 0f
        )

        assertEquals(0f, broken.minSideFraction, 0.0001f)
    }
}
