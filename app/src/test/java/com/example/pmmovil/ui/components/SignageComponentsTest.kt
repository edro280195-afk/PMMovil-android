package com.example.pmmovil.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignageComponentsTest {

    @Test
    fun `digits are listed left to right`() {
        assertEquals(listOf(0), digitsOf(0))
        assertEquals(listOf(9), digitsOf(9))
        assertEquals(listOf(1, 4), digitsOf(14))
        assertEquals(listOf(3, 0, 5), digitsOf(305))
    }

    @Test
    fun `a negative value is shown as zero instead of a minus sign`() {
        assertEquals(listOf(0), digitsOf(-4))
    }

    @Test
    fun `every signal has its own word so color is never the only cue`() {
        val words = SignalKind.entries.map { it.wordRes }

        assertEquals(words.size, words.toSet().size)
    }

    @Test
    fun `no signal repeats the same look as another`() {
        val looks = SignalKind.entries.map { Triple(it.container, it.content, it.outlined) }

        assertEquals(looks.size, looks.toSet().size)
    }

    @Test
    fun `the line has five stations in the order the goods move`() {
        assertEquals(
            listOf("Caseta", "Entrada", "Revision", "Relacion", "Salida"),
            Station.entries.map { it.name }
        )
    }

    @Test
    fun `every station state has its own description for screen readers`() {
        val descriptions = StationState.entries.map { it.descriptionRes }

        assertEquals(descriptions.size, descriptions.toSet().size)
        assertTrue(StationState.entries.size == 6)
    }

    @Test
    fun `the closed and the no data signals are visually different from the live ones`() {
        assertNotEquals(SignalKind.Cerrado.container, SignalKind.Listo.container)
        assertTrue(SignalKind.SinDato.outlined)
    }
}
