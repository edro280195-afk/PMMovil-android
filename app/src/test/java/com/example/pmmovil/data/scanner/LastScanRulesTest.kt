package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LastScanRulesTest {

    private val now = 1_000_000_000L
    private val user = "israel.magana"

    @Test
    fun `the four kinds that have a card leave a memory with their own code`() {
        val box = LastScanRules.remember(ScannedCode.Caja("E-017848/26"), user, now)
        val release = LastScanRules.remember(ScannedCode.Relacion("01I26104549"), user, now)
        val folder = LastScanRules.remember(ScannedCode.Carpeta("01C263582800000"), user, now)
        val package3 = LastScanRules.remember(ScannedCode.Bulto("01C263582800000", 3, 12), user, now)

        assertEquals(LastScan("E-017848/26", LastScanKind.Caja, user, now), box)
        assertEquals(LastScanKind.Relacion, release?.kind)
        assertEquals("01C263582800000", folder?.code)
        // Del bulto se guarda la lectura completa: abrirla otra vez debe dar el mismo bulto, no la carpeta.
        assertEquals("01C263582800000 3-12", package3?.code)
        assertEquals(LastScanKind.Bulto, package3?.kind)
    }

    @Test
    fun `a badge a pre registration and the rest never leave a memory`() {
        // El gafete lleva una credencial y el pre-registro un token: no se guardan en ningún lado.
        assertNull(LastScanRules.remember(ScannedCode.Gafete, user, now))
        assertNull(LastScanRules.remember(ScannedCode.PreRegistro("PMMPR|7K2Q-4481|A1B2C3"), user, now))
        assertNull(LastScanRules.remember(ScannedCode.ProductoCliente("+30S77120045"), user, now))
        assertNull(LastScanRules.remember(ScannedCode.Candidato("PECE0511COL"), user, now))
        assertNull(LastScanRules.remember(ScannedCode.Desconocido("HOLA"), user, now))
    }

    @Test
    fun `without a user nothing is remembered`() {
        assertNull(LastScanRules.remember(ScannedCode.Caja("E-017848/26"), null, now))
        assertNull(LastScanRules.remember(ScannedCode.Caja("E-017848/26"), "  ", now))
    }

    private val scan = LastScan("E-017848/26", LastScanKind.Caja, user, now)

    @Test
    fun `it is shown to the same user until the shift ends`() {
        assertNotNull(LastScanRules.visibleTo(scan, user, now))
        assertNotNull(LastScanRules.visibleTo(scan, " ISRAEL.MAGANA ", now + 1_000))
        assertNotNull(LastScanRules.visibleTo(scan, user, now + LastScanRules.VALID_FOR_MILLIS))
        assertNull(LastScanRules.visibleTo(scan, user, now + LastScanRules.VALID_FOR_MILLIS + 1))
    }

    @Test
    fun `another user on a shared tablet does not see it`() {
        assertNull(LastScanRules.visibleTo(scan, "eduardo.rdz", now))
        assertNull(LastScanRules.visibleTo(scan, null, now))
        assertNull(LastScanRules.visibleTo(scan, "", now))
        assertNull(LastScanRules.visibleTo(null, user, now))
    }

    @Test
    fun `a clock that goes backwards does not keep it alive`() {
        assertNull(LastScanRules.visibleTo(scan, user, now - 1))
    }

    @Test
    fun `the elapsed time rounds down to minutes and then to hours`() {
        fun at(millis: Long) = LastScanRules.elapsed(scan, now + millis)

        assertEquals(LastScanElapsed.JustNow, at(0))
        assertEquals(LastScanElapsed.JustNow, at(59_999))
        assertEquals(LastScanElapsed.Minutes(1), at(60_000))
        assertEquals(LastScanElapsed.Minutes(59), at(59 * 60_000L + 59_000))
        assertEquals(LastScanElapsed.Hours(1), at(60 * 60_000L))
        assertEquals(LastScanElapsed.Hours(11), at(11 * 60 * 60_000L + 59 * 60_000L))
        // Un reloj hacia atrás se lee como «ahora», nunca como un tiempo negativo.
        assertEquals(LastScanElapsed.JustNow, at(-5_000))
    }
}
