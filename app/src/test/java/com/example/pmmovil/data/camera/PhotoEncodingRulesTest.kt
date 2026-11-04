package com.example.pmmovil.data.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class PhotoEncodingRulesTest {

    @Test
    fun `conserva todas las fotos cuando su codificacion termino`() {
        assertEquals(
            listOf("foto-1", "foto-2"),
            PhotoEncodingRules.requireAllEncoded(listOf("foto-1", "foto-2"))
        )
    }

    @Test
    fun `rechaza codificacion parcial para no confirmar una salida sin toda la evidencia`() {
        try {
            PhotoEncodingRules.requireAllEncoded(listOf("foto-1", null, "foto-3"))
            fail("La codificacion parcial debe detener la confirmacion.")
        } catch (e: IllegalStateException) {
            assertEquals(
                "No se pudo preparar la foto 2. Vuelve a tomarla o elimínala antes de confirmar la salida.",
                e.message
            )
        }
    }
}
