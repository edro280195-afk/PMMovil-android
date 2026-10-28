package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanActionsRouteTest {

    @Test
    fun `a box travels in the route with its slash escaped`() {
        assertEquals("acciones?code=E-017846%2F26", ScanActionsRoute.forScan("E-017846/26"))
    }

    @Test
    fun `an official code travels with its entry or release and never with the rest of its text`() {
        assertEquals("acciones?code=01C263562840000", ScanActionsRoute.forScan("01C2635628400001454  100"))
        assertEquals("acciones?code=01C263605020000", ScanActionsRoute.forScan("01C263605020000CL7480100"))
        assertEquals("acciones?code=01I26106418", ScanActionsRoute.forScan("01I26106418-1"))
        assertEquals("acciones?code=01I26106070", ScanActionsRoute.forScan("R-01I26106070  0000  101"))
    }

    @Test
    fun `the other side classifies what an official code left in the route`() {
        assertEquals(ScannedCode.Carpeta("01C263562840000"), ScanActionsRoute.codeFromArgument("01C263562840000"))
        assertEquals(ScannedCode.Relacion("01I26106418"), ScanActionsRoute.codeFromArgument("01I26106418"))
    }

    @Test
    fun `a badge never travels in the route`() {
        val route = ScanActionsRoute.forScan("jperez;clave-secreta-123")

        assertEquals("acciones", route)
        assertFalse(route.contains("jperez"))
        assertFalse(route.contains("clave"))
    }

    @Test
    fun `a badge with surrounding spaces and a line break is still caught`() {
        assertEquals("acciones", ScanActionsRoute.forScan("  jperez;clave-secreta-123\r\n"))
    }

    @Test
    fun `the route keeps the group separator of a structured label so it is still recognised`() {
        val route = ScanActionsRoute.forScan("[)>\u001E06\u001DP12345")

        assertTrue(route.startsWith("acciones?code="))
        assertTrue(route.contains("%1D"))
    }

    @Test
    fun `a very long read is cut before it goes into the route`() {
        val route = ScanActionsRoute.forScan("A".repeat(5_000))

        assertEquals("acciones?code=".length + 200, route.length)
    }

    @Test
    fun `no argument means a badge and a handmade route with a badge inside is treated the same`() {
        assertEquals(ScannedCode.Gafete, ScanActionsRoute.codeFromArgument(null))
        assertEquals(ScannedCode.Gafete, ScanActionsRoute.codeFromArgument("jperez;clave-secreta-123"))
    }

    @Test
    fun `the argument is classified again on the other side`() {
        assertEquals(ScannedCode.Caja("E-017846/26"), ScanActionsRoute.codeFromArgument("E-017846/26"))
        assertEquals(
            ScannedCode.Bulto("01C263582800000", 3, 12),
            ScanActionsRoute.codeFromArgument("01C263582800000 3-12")
        )
    }

    @Test
    fun `the pattern accepts the plain route and the one with a code`() {
        assertEquals("acciones?code={code}", ScanActionsRoute.PATTERN)
    }
}
