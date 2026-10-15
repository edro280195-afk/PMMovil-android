package com.example.pmmovil.data.inventory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RackCensusCodeParserTest {
    @Test
    fun `printed ordinal and declared total are read but kept as label data`() {
        val code = RackCensusCodeParser.parse("01C260000123 14-17")!!

        assertEquals("01C260000123 14-17", code.normalized)
        assertEquals("01C260000123", code.reference)
        assertEquals(14, code.printedOrdinal)
        assertEquals(17, code.declaredTotal)
        assertFalse(code.isFolderLabel)
    }

    @Test
    fun `reference only is the folder label, never a package`() {
        val code = RackCensusCodeParser.parse("01C260000123")!!

        assertTrue(code.isFolderLabel)
        assertNull(code.printedOrdinal)
        assertNull(code.declaredTotal)
    }

    @Test
    fun `lowercase and padded reads normalize to the printed code`() {
        assertEquals(
            "01C260000123 7-9",
            RackCensusCodeParser.parse("  01c260000123  7-9 ")!!.normalized
        )
    }

    @Test
    fun `control characters are removed instead of becoming part of the code`() {
        assertEquals("01C260000123 1-2", RackCensusCodeParser.parse("01C260000123\u001D 1-2")!!.normalized)
    }

    @Test
    fun `a mutilated ordinal is rejected instead of registering an unplaceable package`() {
        assertNull(RackCensusCodeParser.parse("01C260000123 18-17"))
        assertNull(RackCensusCodeParser.parse("01C260000123 0-17"))
        assertNull(RackCensusCodeParser.parse("01C260000123 3-0"))
    }

    @Test
    fun `reads that are not IMEX labels are rejected`() {
        assertNull(RackCensusCodeParser.parse(null))
        assertNull(RackCensusCodeParser.parse("   "))
        assertNull(RackCensusCodeParser.parse("../ruta"))
        assertNull(RackCensusCodeParser.parse("https://pmm.example/seguimiento/01C26"))
        assertNull(RackCensusCodeParser.parse("A"))
        assertNull(RackCensusCodeParser.parse("X".repeat(121)))
    }

    @Test
    fun `normalize matches the comparison the server does over textoetiqueta`() {
        assertEquals("01C260000123 14-17", RackCensusCodeParser.normalize(" 01c260000123   14-17 "))
        assertEquals("", RackCensusCodeParser.normalize(null))
    }
}
