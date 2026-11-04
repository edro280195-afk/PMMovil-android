package com.example.pmmovil.ui.asignaciones

import com.example.pmmovil.data.model.EntradaPendienteDto
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingHubRulesTest {

    // ── Normalización y mínimo para buscar ───────────────────────────────────

    @Test
    fun `normaliza acentos mayusculas y signos`() {
        assertEquals("VALVULAS NORTE", normalizeForSearch("  Válvulas   Norte "))
        assertEquals("01C263607340000", normalizeForSearch("01c263607340000"))
        assertEquals("A B", normalizeForSearch("a-b"))
        assertEquals("", normalizeForSearch(null))
        assertEquals("", normalizeForSearch("  \r\n "))
    }

    @Test
    fun `solo busca en todas las entradas con tres caracteres utiles`() {
        assertFalse(isSearchable(null))
        assertFalse(isSearchable("  "))
        assertFalse(isSearchable("36"))
        assertFalse(isSearchable("3 6"))
        assertTrue(isSearchable("360"))
        assertTrue(isSearchable("3 60"))
        assertTrue(isSearchable("val"))
    }

    // ── Búsqueda entre mis pendientes ────────────────────────────────────────

    @Test
    fun `una consulta vacia deja pasar todo`() {
        assertTrue(matchesPendingQuery(entry(), ""))
        assertTrue(matchesPendingQuery(entry(), "   "))
        assertTrue(matchesPendingQuery(entry(), null))
    }

    @Test
    fun `todas las partes de la consulta deben coincidir y sin acentos`() {
        val e = entry(crefAgrupada = "01C263607340000", nombreCliente = "Válvulas Norte")

        assertTrue(matchesPendingQuery(e, "valvulas"))
        assertTrue(matchesPendingQuery(e, "3607"))
        assertTrue(matchesPendingQuery(e, "valvulas 3607"))
        assertFalse(matchesPendingQuery(e, "valvulas 9999"))
        assertFalse(matchesPendingQuery(e, "bombas"))
    }

    // ── Qué es un pendiente y cómo se separa ─────────────────────────────────

    @Test
    fun `las terminadas no son pendientes`() {
        assertTrue(isPendingWork(entry()))
        assertTrue(isPendingWork(entry(totalFacturas = 4, facturasCompletas = 1, usuarioRevisando = "yo")))
        assertTrue(isPendingWork(entry(totalFacturas = 4, facturasCompletas = 1, finRevision = "2026/11/03 10:00:00")))
        assertFalse(isPendingWork(entry(totalFacturas = 2, facturasCompletas = 2, finRevision = "2026/11/03 10:00:00")))
        assertFalse(isPendingWork(entry(totalFacturas = 2, facturasCompletas = 2)))
        assertFalse(isPendingWork(entry(todasRelacionCompleta = true)))
    }

    @Test
    fun `separa asignadas por prioridad y abiertas con las que siguen en curso primero`() {
        val hub = buildPendingHub(
            listOf(
                entry("DONE", totalFacturas = 1, facturasCompletas = 1, finRevision = "2026/11/01 09:00:00"),
                entry("ASG-NORMAL", esAsignada = true, asignPrioridad = 0, asignFecha = "2026-11-03T08:00:00"),
                entry("ASG-URGENTE", esAsignada = true, asignPrioridad = 2, asignFecha = "2026-11-02T08:00:00"),
                entry("PAUSADA", totalFacturas = 3, facturasCompletas = 1, finRevision = "2026/11/03 11:00:00"),
                entry("EN-CURSO", usuarioRevisando = "yo", inicioRevision = "2026/11/03 15:00:00")
            )
        )

        assertEquals(listOf("ASG-URGENTE", "ASG-NORMAL"), hub.assigned.map { it.crefAgrupada })
        assertEquals(listOf("EN-CURSO", "PAUSADA"), hub.open.map { it.crefAgrupada })
        assertEquals(4, hub.total)
        assertEquals(1, hub.inProgressCount)
        assertEquals(1, hub.pausedCount)
        assertFalse(hub.isEmpty)
    }

    @Test
    fun `sin trabajo el hub esta vacio`() {
        assertTrue(buildPendingHub(emptyList()).isEmpty)
        assertTrue(buildPendingHub(listOf(entry(totalFacturas = 1, facturasCompletas = 1))).isEmpty)
    }

    @Test
    fun `una asignada ya terminada tampoco ocupa la lista`() {
        val hub = buildPendingHub(
            listOf(entry("ASG", esAsignada = true, totalFacturas = 2, facturasCompletas = 2, finRevision = "2026/11/03 10:00:00"))
        )
        assertTrue(hub.isEmpty)
    }

    @Test
    fun `entre varias abiertas gana la de actividad mas reciente`() {
        val hub = buildPendingHub(
            listOf(
                entry("VIEJA", totalFacturas = 3, facturasCompletas = 1, finRevision = "2026/10/30 10:00:00"),
                entry("NUEVA", totalFacturas = 3, facturasCompletas = 1, finRevision = "2026/11/03 10:00:00")
            )
        )
        assertEquals(listOf("NUEVA", "VIEJA"), hub.open.map { it.crefAgrupada })
    }

    // ── Avance real ──────────────────────────────────────────────────────────

    @Test
    fun `el avance se cuenta por partidas cuando las hay`() {
        val e = entry(totalFacturas = 1, facturasCompletas = 0, totalPartidas = 40, partidasRevisadas = 39)
        assertEquals(98, progressPercent(e))   // 97.5 se redondea
        assertTrue(progressIsByParts(e))
    }

    @Test
    fun `sin partidas el avance cae a facturas`() {
        val e = entry(totalFacturas = 4, facturasCompletas = 1, totalPartidas = 0, partidasRevisadas = 0)
        assertEquals(25, progressPercent(e))
        assertFalse(progressIsByParts(e))
    }

    @Test
    fun `el avance nunca se sale de cero a cien`() {
        assertEquals(100, progressPercent(entry(totalPartidas = 10, partidasRevisadas = 15)))
        assertEquals(0, progressPercent(entry(totalPartidas = 10, partidasRevisadas = -2)))
        assertEquals(0, progressPercent(entry(totalFacturas = 0, facturasCompletas = 0)))
    }

    // ── Actividad ────────────────────────────────────────────────────────────

    @Test
    fun `la actividad es el fin si esta pausada y el inicio si sigue en curso`() {
        assertEquals("2026/11/03 11:00:00", activityTimestamp(entry(inicioRevision = "2026/11/03 09:00:00", finRevision = "2026/11/03 11:00:00")))
        assertEquals("2026/11/03 09:00:00", activityTimestamp(entry(inicioRevision = "2026/11/03 09:00:00")))
        assertNull(activityTimestamp(entry()))
        assertNull(activityTimestamp(entry(inicioRevision = " ", finRevision = "")))
    }

    @Test
    fun `lee la hora del servidor en sus formatos`() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 20, 5), parseServerDateTime("2026/11/03 14:20:05"))
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 20, 5), parseServerDateTime("2026-11-03 14:20:05"))
        assertEquals(LocalDateTime.of(2026, 11, 3, 14, 20, 5), parseServerDateTime("2026-11-03T14:20:05"))
        assertNull(parseServerDateTime("ayer por la tarde"))
        assertNull(parseServerDateTime(null))
        assertNull(parseServerDateTime("  "))
    }

    @Test
    fun `hoy muestra solo la hora y otro dia la fecha corta`() {
        val today = LocalDateTime.of(2026, 11, 4, 8, 0)
        val es = Locale("es", "MX")

        assertEquals("14:20", formatActivity("2026/11/04 14:20:05", today, es))
        assertEquals("3 nov 14:20", formatActivity("2026/11/03 14:20:05", today, es))
        assertNull(formatActivity("no es fecha", today, es))
        assertNull(formatActivity(null, today, es))
    }

    // ── Resaltado ────────────────────────────────────────────────────────────

    @Test
    fun `resalta sin importar acentos ni mayusculas y devuelve indices del texto original`() {
        assertEquals(listOf(0..3), matchRanges("Válvulas Norte", listOf("VALV")))
        assertEquals(listOf(5..8), matchRanges("01C263607340000", listOf("3607")))
        assertEquals(listOf(0..3, 9..11), matchRanges("Válvulas Norte", listOf("VALV", "NOR")))
    }

    @Test
    fun `une tramos que se tocan y marca todas las apariciones`() {
        assertEquals(listOf(0..5), matchRanges("ABCDEF", listOf("ABC", "DEF")))
        assertEquals(listOf(0..1, 3..4), matchRanges("AB AB", listOf("AB")))
    }

    @Test
    fun `sin coincidencias o con partes de un solo caracter no resalta nada`() {
        assertTrue(matchRanges("Válvulas Norte", listOf("ZZZ")).isEmpty())
        assertTrue(matchRanges("Válvulas Norte", listOf("V")).isEmpty())
        assertTrue(matchRanges("", listOf("VALV")).isEmpty())
        assertTrue(matchRanges("Válvulas", emptyList()).isEmpty())
    }

    private fun entry(
        crefAgrupada: String = "REF-001",
        nombreCliente: String? = "Cliente prueba",
        totalFacturas: Int = 1,
        facturasCompletas: Int = 0,
        totalPartidas: Int = 0,
        partidasRevisadas: Int = 0,
        inicioRevision: String? = null,
        finRevision: String? = null,
        usuarioRevisando: String? = null,
        usuarioTomoDocumento: String? = null,
        todasRelacionCompleta: Boolean = false,
        esAsignada: Boolean? = null,
        asignPrioridad: Int? = null,
        asignFecha: String? = null,
        asignSupervisor: String? = null,
        asignNota: String? = null
    ): EntradaPendienteDto = EntradaPendienteDto(
        crefAgrupada = crefAgrupada,
        nombreReferencia = crefAgrupada,
        clienteId = "C-001",
        nombreCliente = nombreCliente,
        dFecha = null,
        totalFacturas = totalFacturas,
        facturasCompletas = facturasCompletas,
        totalPartidas = totalPartidas,
        partidasRevisadas = partidasRevisadas,
        inicioRevision = inicioRevision,
        finRevision = finRevision,
        usuarioRevisando = usuarioRevisando,
        usuarioTomoDocumento = usuarioTomoDocumento,
        todasRelacionCompleta = todasRelacionCompleta,
        esAsignada = esAsignada,
        asignPrioridad = asignPrioridad,
        idAsignacion = null,
        asignSupervisor = asignSupervisor,
        asignNota = asignNota,
        asignFecha = asignFecha
    )
}
