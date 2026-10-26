package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LockedCodeTest {

    @Test
    fun `a frame with nothing decoded locks nothing`() {
        assertNull(lockedCodeOf(emptyList()))
        assertNull(lockedCodeOf(listOf(null, "", "   ")))
    }

    @Test
    fun `the first decoded candidate wins and potential ones are skipped`() {
        assertEquals(
            ScannedCode.Caja("E-017846/26"),
            lockedCodeOf(listOf(null, "E-017846/26", "01I26104546"))
        )
    }

    @Test
    fun `each family is told apart`() {
        assertEquals(ScannedCode.Relacion("01I26104546"), lockedCodeOf(listOf("01I26104546")))
        assertEquals(ScannedCode.Carpeta("01C263582800000"), lockedCodeOf(listOf("01C263582800000")))
        assertEquals(
            ScannedCode.Bulto("01C263582800000", 3, 12),
            lockedCodeOf(listOf("01C263582800000 3-12"))
        )
        assertEquals(ScannedCode.PreRegistro("PMMPR|7K2Q-4481"), lockedCodeOf(listOf("PMMPR|7K2Q-4481")))
    }

    @Test
    fun `a badge is locked without keeping its value`() {
        val locked = lockedCodeOf(listOf("jperez;clave-secreta-123"))

        assertEquals(ScannedCode.Gafete, locked)
        assertEquals("", locked?.normalized)
    }
}
