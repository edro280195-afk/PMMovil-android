package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre a que codigo se engancha el visor cuando hay varios en el frame.
 *
 * El caso que motivo estos tests: con `enableAllPotentialBarcodes()` una
 * persiana metalica al fondo generaba un candidato potencial mucho mas grande
 * que el QR real, y al ordenar por area el marco se iba encima de la persiana.
 */
class BarcodeCandidateScoreTest {

    private val imageWidth = 720
    private val imageHeight = 1280

    private fun distanceAt(fractionX: Float, fractionY: Float): Float =
        normalizedCenterDistance(
            centerX = fractionX * imageWidth,
            centerY = fractionY * imageHeight,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )

    @Test
    fun `el centro exacto tiene distancia cero y la esquina uno`() {
        assertEquals(0f, distanceAt(0.5f, 0.5f), 0.001f)
        assertEquals(1f, distanceAt(0f, 0f), 0.001f)
        assertEquals(1f, distanceAt(1f, 1f), 0.001f)
    }

    @Test
    fun `un codigo decodificado gana siempre a uno solo potencial`() {
        // Aunque el decodificado este en una esquina y el potencial centrado.
        val decodificadoLejos = scoreOf(hasRawValue = true, centerDistance = 1f)
        val potencialCentrado = scoreOf(hasRawValue = false, centerDistance = 0f)

        assertTrue(decodificadoLejos > potencialCentrado)
    }

    @Test
    fun `entre potenciales gana el mas centrado`() {
        val qrCentrado = scoreOf(hasRawValue = false, centerDistance = distanceAt(0.57f, 0.45f))
        val persianaPeriferica = scoreOf(hasRawValue = false, centerDistance = distanceAt(0.85f, 0.30f))

        assertTrue(qrCentrado > persianaPeriferica)
    }

    @Test
    fun `el tamano ya no decide - un candidato enorme no desplaza al codigo leido`() {
        // Antes el area (hasta 2.073.600 a 1080p) superaba el bonus de 1.000.000
        // que marcaba "decodificado", asi que un falso positivo grande ganaba.
        val codigoLeidoPequeno = scoreOf(hasRawValue = true, centerDistance = 0.5f)
        val falsoPositivoEnorme = scoreOf(hasRawValue = false, centerDistance = 0f)

        assertTrue(codigoLeidoPequeno > falsoPositivoEnorme)
    }

    @Test
    fun `el filtro deja pasar lo que esta dentro del encuadre util`() {
        // Un QR razonablemente centrado arrastra el marco.
        assertTrue(distanceAt(0.57f, 0.45f) <= MAX_CANDIDATE_CENTER_DISTANCE)
        // Una textura pegada al borde no.
        assertTrue(distanceAt(0.95f, 0.10f) > MAX_CANDIDATE_CENTER_DISTANCE)
    }
}
