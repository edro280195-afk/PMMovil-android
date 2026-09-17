package com.example.pmmovil.data.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelSnapEngineTest {

    private val canvasWidth = 1_800
    private val canvasHeight = 1_200

    @Test
    fun `borde izquierdo se pega al borde izquierdo de otro elemento`() {
        val other = SnapBox(x = 400, y = 800, width = 300, height = 100)
        val moving = SnapBox(x = 405, y = 200, width = 200, height = 80)

        val result = LabelSnapEngine.snap(moving, listOf(other), canvasWidth, canvasHeight)

        assertEquals(400, result.x)
        assertTrue(result.verticalGuides.contains(400))
    }

    @Test
    fun `elemento lejos de cualquier referencia no se mueve`() {
        // Ojo al elegir coordenadas: el centro del lienzo (900, 600) también es referencia,
        // así que x = 900 se pegaría solo. Se usan valores libres de toda referencia.
        val other = SnapBox(x = 400, y = 800, width = 300, height = 100)
        val moving = SnapBox(x = 1_200, y = 400, width = 200, height = 80)

        val result = LabelSnapEngine.snap(moving, listOf(other), canvasWidth, canvasHeight)

        assertEquals(1_200, result.x)
        assertEquals(400, result.y)
        assertFalse(result.snapped)
    }

    @Test
    fun `centro del elemento se pega al centro del lienzo`() {
        // Centro del lienzo en X = 900; un elemento de 200 de ancho queda centrado en x = 800.
        val moving = SnapBox(x = 806, y = 500, width = 200, height = 80)

        val result = LabelSnapEngine.snap(moving, emptyList(), canvasWidth, canvasHeight)

        assertEquals(800, result.x)
        assertTrue(result.verticalGuides.contains(900))
    }

    @Test
    fun `borde derecho se pega al borde derecho de otro elemento`() {
        val other = SnapBox(x = 200, y = 100, width = 400, height = 100)
        // El borde derecho del otro está en 600; moving mide 150, así que alineado iría en 450.
        val moving = SnapBox(x = 456, y = 700, width = 150, height = 80)

        val result = LabelSnapEngine.snap(moving, listOf(other), canvasWidth, canvasHeight)

        assertEquals(450, result.x)
        assertTrue(result.verticalGuides.contains(600))
    }

    @Test
    fun `se puede pegar en los dos ejes a la vez`() {
        val other = SnapBox(x = 500, y = 300, width = 200, height = 100)
        val moving = SnapBox(x = 505, y = 296, width = 200, height = 100)

        val result = LabelSnapEngine.snap(moving, listOf(other), canvasWidth, canvasHeight)

        assertEquals(500, result.x)
        assertEquals(300, result.y)
        assertTrue(result.verticalGuides.isNotEmpty())
        assertTrue(result.horizontalGuides.isNotEmpty())
    }

    @Test
    fun `se pega al margen de la plantilla`() {
        val moving = SnapBox(x = 70, y = 500, width = 200, height = 80)

        val result = LabelSnapEngine.snap(moving, emptyList(), canvasWidth, canvasHeight, margin = 65)

        assertEquals(65, result.x)
    }

    @Test
    fun `gana la referencia mas cercana cuando hay varias en rango`() {
        val near = SnapBox(x = 502, y = 0, width = 100, height = 50)
        val far = SnapBox(x = 512, y = 0, width = 100, height = 50)
        val moving = SnapBox(x = 505, y = 600, width = 100, height = 50)

        val result = LabelSnapEngine.snap(moving, listOf(near, far), canvasWidth, canvasHeight)

        assertEquals(502, result.x)
    }

    @Test
    fun `la etiqueta de caseta usa una tolerancia mas fina que la de bodega`() {
        val casetaTolerance = LabelSnapEngine.defaultTolerance(675)
        val bodegaTolerance = LabelSnapEngine.defaultTolerance(1_800)

        assertTrue(casetaTolerance < bodegaTolerance)
        assertTrue(casetaTolerance >= 4)
    }

    @Test
    fun `no reporta guias cuando no hubo ajuste`() {
        val moving = SnapBox(x = 400, y = 300, width = 120, height = 60)

        val result = LabelSnapEngine.snap(moving, emptyList(), canvasWidth, canvasHeight)

        assertTrue(result.verticalGuides.isEmpty())
        assertTrue(result.horizontalGuides.isEmpty())
    }
}
