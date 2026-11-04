package com.example.pmmovil.ui.salidacaseta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaFotosBorradorTest {

    private val todasExisten: (String) -> Boolean = { true }

    @Test
    fun `lo que se guarda se lee igual`() {
        val fotos = mapOf(
            "01I26106450" to listOf("/data/salidas/salida_1.jpg", "/data/salidas/salida_2.jpg"),
            "01I26106466" to listOf("/data/salidas/salida_3.jpg")
        )

        assertEquals(fotos, SalidaFotosBorrador.leer(SalidaFotosBorrador.serializar(fotos), todasExisten))
    }

    @Test
    fun `una relacion sin fotos no se guarda`() {
        val json = SalidaFotosBorrador.serializar(mapOf("01I26106450" to emptyList(), "01I26106466" to listOf("/a.jpg")))

        assertEquals(mapOf("01I26106466" to listOf("/a.jpg")), SalidaFotosBorrador.leer(json, todasExisten))
    }

    @Test
    fun `las fotos cuyo archivo ya no existe se descartan`() {
        val json = SalidaFotosBorrador.serializar(mapOf("01I26106450" to listOf("/a.jpg", "/b.jpg"), "01I26106466" to listOf("/c.jpg")))

        val leido = SalidaFotosBorrador.leer(json) { it != "/b.jpg" && it != "/c.jpg" }

        assertEquals(mapOf("01I26106450" to listOf("/a.jpg")), leido)
    }

    @Test
    fun `un respaldo ilegible o vacio se ignora sin romper la pantalla`() {
        assertTrue(SalidaFotosBorrador.leer(null, todasExisten).isEmpty())
        assertTrue(SalidaFotosBorrador.leer("", todasExisten).isEmpty())
        assertTrue(SalidaFotosBorrador.leer("{ esto no es json", todasExisten).isEmpty())
        assertTrue(SalidaFotosBorrador.leer("[1, 2, 3]", todasExisten).isEmpty())
    }
}
