package com.example.pmmovil.ui.rostro

import com.example.pmmovil.ui.rostro.FaceOrientationTracker.Companion.CELDAS_ARO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceOrientationTrackerTest {

    @Test
    fun `mapea arriba derecha abajo e izquierda a los sectores esperados`() {
        val tracker = FaceOrientationTracker(requiredStableFrames = 1, smoothingFactor = 1f)

        // 12 sectores de 30 grados: arriba 0, derecha 3, abajo 6, izquierda 9.
        assertEquals(0, tracker.update(0f, 15f, 24f, 18f).sector)
        assertEquals(3, tracker.update(24f, 0f, 24f, 18f).sector)
        assertEquals(6, tracker.update(0f, -18f, 24f, 18f).sector)
        assertEquals(9, tracker.update(-24f, 0f, 24f, 18f).sector)
    }

    @Test
    fun `la celda del aro es tres veces mas fina que el sector`() {
        val tracker = FaceOrientationTracker(requiredStableFrames = 1, smoothingFactor = 1f)

        // 36 marcas de 10 grados: arriba 0, derecha 9, abajo 18, izquierda 27.
        assertEquals(0, tracker.update(0f, 15f, 24f, 18f).celda)
        assertEquals(9, tracker.update(24f, 0f, 24f, 18f).celda)
        assertEquals(18, tracker.update(0f, -18f, 24f, 18f).celda)
        assertEquals(27, tracker.update(-24f, 0f, 24f, 18f).celda)
    }

    @Test
    fun `de frente no hay direccion que pintar`() {
        val tracker = FaceOrientationTracker(requiredStableFrames = 1, smoothingFactor = 1f)

        val muestra = tracker.update(0f, 0f, 24f, 18f)

        assertEquals(null, muestra.celda)
        assertEquals(null, muestra.sector)
        assertEquals(null, muestra.angulo)
    }

    @Test
    fun `no marca un sector hasta mantenerlo varios frames`() {
        val tracker = FaceOrientationTracker(requiredStableFrames = 3)

        assertFalse(tracker.isStable(tracker.update(24f, 0f, 24f, 18f)))
        assertFalse(tracker.isStable(tracker.update(24f, 0f, 24f, 18f)))
        assertTrue(tracker.isStable(tracker.update(24f, 0f, 24f, 18f)))
    }

    @Test
    fun `suaviza un cambio brusco de orientacion`() {
        val tracker = FaceOrientationTracker(requiredStableFrames = 1, smoothingFactor = 0.25f)

        tracker.update(0f, 15f, 24f, 18f)
        val sample = tracker.update(24f, 15f, 24f, 18f)

        assertEquals(6f, sample.yaw, 0.001f)
        assertEquals(15f, sample.pitch, 0.001f)
    }

    // ── El camino entre celdas: es lo que evita que el aro salga con huecos ──

    @Test
    fun `rellena las celdas intermedias de un giro corto`() {
        assertEquals(listOf(10, 11, 12), FaceOrientationTracker.caminoEntre(10, 13))
    }

    @Test
    fun `rellena hacia atras cuando el giro va en sentido contrario`() {
        assertEquals(listOf(13, 12, 11), FaceOrientationTracker.caminoEntre(13, 10))
    }

    @Test
    fun `cruza el cero por el lado corto`() {
        assertEquals(
            listOf(CELDAS_ARO - 2, CELDAS_ARO - 1, 0),
            FaceOrientationTracker.caminoEntre(CELDAS_ARO - 2, 1)
        )
    }

    @Test
    fun `un salto largo no se rellena porque no fue un giro`() {
        assertTrue(FaceOrientationTracker.caminoEntre(0, CELDAS_ARO / 2).isEmpty())
    }
}
