package com.example.pmmovil.ui.salidacaseta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaFotosPlanTest {

    @Test
    fun `por omision las fotos salen de cuatro en cuatro como en Entrada`() {
        val lotes = SalidaFotosPlan.lotes(10)

        assertEquals(listOf(4, 4, 2), lotes.map { it.cantidad })
        assertEquals(listOf(1, 5, 9), lotes.map { it.indiceInicial })
    }

    @Test
    fun `ocho fotos en lotes de tres salen tres, tres y dos`() {
        val lotes = SalidaFotosPlan.lotes(8, porLote = 3)

        assertEquals(listOf(3, 3, 2), lotes.map { it.cantidad })
        assertEquals(listOf(1, 4, 7), lotes.map { it.indiceInicial })
        assertEquals(listOf(0 to 3, 3 to 6, 6 to 8), lotes.map { it.desde to it.hastaExclusivo })
    }

    @Test
    fun `sin fotos no hay lotes y una sola foto es un lote`() {
        assertTrue(SalidaFotosPlan.lotes(0).isEmpty())
        assertTrue(SalidaFotosPlan.lotes(-2).isEmpty())
        assertEquals(1, SalidaFotosPlan.lotes(1).size)
        assertEquals(1, SalidaFotosPlan.lotes(1).single().cantidad)
    }

    @Test
    fun `un numero exacto de lotes no deja un lote vacio`() {
        assertEquals(listOf(3, 3), SalidaFotosPlan.lotes(6, porLote = 3).map { it.cantidad })
        assertEquals(listOf(4, 4), SalidaFotosPlan.lotes(8).map { it.cantidad })
    }

    @Test
    fun `un tamano de lote absurdo se trata como uno`() {
        assertEquals(listOf(1, 1, 1), SalidaFotosPlan.lotes(3, porLote = 0).map { it.cantidad })
    }

    @Test
    fun `sin nada subido faltan todos los lotes`() {
        val fotos = listOf("a", "b", "c", "d")

        assertEquals(SalidaFotosPlan.lotes(4, porLote = 2), SalidaFotosPlan.pendientes(fotos, emptyMap(), porLote = 2))
    }

    @Test
    fun `un reintento solo manda las fotos que no llegaron`() {
        val fotos = listOf("a", "b", "c", "d", "e", "f", "g", "h")
        // Ya llegaron de la a a la e; faltan f, g y h.
        val subidas = mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 4, "e" to 5)

        val pendientes = SalidaFotosPlan.pendientes(fotos, subidas, porLote = 2)

        assertEquals(listOf(5 to 7, 7 to 8), pendientes.map { it.desde to it.hastaExclusivo })
        assertEquals(listOf(6, 8), pendientes.map { it.indiceInicial })
    }

    @Test
    fun `si el guardia quita una foto del medio solo se mandan las que se recorrieron`() {
        // Se habían subido a, b, c, d, e, f. Se quitó "b": ahora son a, c, d, e, f.
        val fotos = listOf("a", "c", "d", "e", "f")
        val subidas = mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 4, "e" to 5, "f" to 6)

        val pendientes = SalidaFotosPlan.pendientes(fotos, subidas, porLote = 2)

        // "a" sigue en su posición y no se repite; c, d, e y f se recorrieron un lugar.
        assertEquals(listOf(1 to 3, 3 to 5), pendientes.map { it.desde to it.hastaExclusivo })
    }

    @Test
    fun `una cuarta foto despues de un lote de tres solo manda la cuarta`() {
        val fotos = listOf("a", "b", "c", "d")
        val subidas = mapOf("a" to 1, "b" to 2, "c" to 3)

        val pendientes = SalidaFotosPlan.pendientes(fotos, subidas)

        assertEquals(listOf(3 to 4), pendientes.map { it.desde to it.hastaExclusivo })
        assertEquals(4, pendientes.single().indiceInicial)
    }

    @Test
    fun `los huecos separados van en lotes separados`() {
        val fotos = listOf("a", "b", "c", "d", "e", "f")
        val subidas = mapOf("a" to 1, "c" to 3, "e" to 5)

        assertEquals(
            listOf(1 to 2, 3 to 4, 5 to 6),
            SalidaFotosPlan.pendientes(fotos, subidas).map { it.desde to it.hastaExclusivo }
        )
    }

    @Test
    fun `con todo subido no queda nada por mandar`() {
        val fotos = listOf("a", "b", "c", "d")
        val subidas = mapOf("a" to 1, "b" to 2, "c" to 3, "d" to 4)

        assertTrue(SalidaFotosPlan.pendientes(fotos, subidas).isEmpty())
    }

    @Test
    fun `con el tamano de lote por omision un reintento manda solo lo que falta, de cuatro en cuatro`() {
        val fotos = (1..10).map { "f$it" }
        // Ya llegaron de f1 a f6; faltan f7 a f10.
        val subidas = fotos.take(6).mapIndexed { i, f -> f to i + 1 }.toMap()

        val pendientes = SalidaFotosPlan.pendientes(fotos, subidas)

        assertEquals(listOf(6 to 10), pendientes.map { it.desde to it.hastaExclusivo })
    }

    @Test
    fun `el texto de avance dice cuantas van`() {
        assertEquals("Enviando fotos: 3 de 8", SalidaFotosPlan.textoAvance(3, 8))
    }
}
