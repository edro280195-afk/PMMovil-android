package com.example.pmmovil.ui.rostro

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceMlKitGateTest {

    @Test
    fun `acepta una cara centrada con tamano util`() {
        val result = FaceMlKitGate.evaluate(
            detections = listOf(deteccion(left = 210, top = 130, right = 430, bottom = 390)),
            imageWidth = 640,
            imageHeight = 480
        )

        assertEquals(FaceGateStatus.READY, result.status)
    }

    @Test
    fun `rechaza ausencia y mas de una cara`() {
        assertEquals(
            FaceGateStatus.NO_FACE,
            FaceMlKitGate.evaluate(emptyList(), 640, 480).status
        )
        assertEquals(
            FaceGateStatus.MULTIPLE_FACES,
            FaceMlKitGate.evaluate(
                listOf(
                    deteccion(200, 100, 360, 300),
                    deteccion(380, 100, 540, 300)
                ),
                640,
                480
            ).status
        )
    }

    @Test
    fun `rechaza cara demasiado pequena grande o fuera del encuadre`() {
        assertEquals(
            FaceGateStatus.TOO_SMALL,
            FaceMlKitGate.evaluate(listOf(deteccion(270, 210, 360, 330)), 640, 480).status
        )
        assertEquals(
            FaceGateStatus.TOO_LARGE,
            FaceMlKitGate.evaluate(listOf(deteccion(20, 40, 620, 440)), 640, 480).status
        )
        assertEquals(
            FaceGateStatus.OUT_OF_FRAME,
            FaceMlKitGate.evaluate(listOf(deteccion(420, 110, 600, 330)), 640, 480).status
        )
    }

    @Test
    fun `una cara que llena el circulo ya no obliga a alejarse`() {
        // 480 px de ancho sobre un cuadro de 640: es la tableta a distancia
        // comoda. Con el limite viejo de 430 esto pedia "alejate un poco" y
        // habia que estirar el brazo.
        assertEquals(
            FaceGateStatus.READY,
            FaceMlKitGate.evaluate(listOf(deteccion(80, 40, 560, 440)), 640, 480).status
        )
    }

    private fun deteccion(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): FaceGateDetection = FaceGateDetection(
        bounds = FaceGateBounds(left, top, right, bottom),
        pitch = 0f,
        yaw = 0f,
        roll = 0f
    )
}
