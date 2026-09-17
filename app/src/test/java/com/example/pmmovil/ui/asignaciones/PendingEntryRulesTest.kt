package com.example.pmmovil.ui.asignaciones

import com.example.pmmovil.data.model.EntradaPendienteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingEntryRulesTest {

    @Test
    fun `calcula proceso terminado con todas las condiciones de cierre`() {
        val status = calculateEntryStatus(
            entry(
                totalFacturas = 3,
                facturasCompletas = 3,
                finRevision = "2026-06-13T10:00:00",
                todasRelacionCompleta = true
            )
        )

        assertEquals(EntryLifecycle.PROCESS_COMPLETED, status.lifecycle)
        assertEquals(EntryCategory.COMPLETED, status.category)
        assertEquals("Proceso terminado", status.label)
        assertEquals(100, status.progressPercent)
    }

    @Test
    fun `distingue sesion pausada de revision terminada`() {
        val paused = calculateEntryStatus(
            entry(
                totalFacturas = 4,
                facturasCompletas = 2,
                finRevision = "2026-06-13T10:00:00"
            )
        )
        val completed = calculateEntryStatus(
            entry(
                totalFacturas = 4,
                facturasCompletas = 4,
                finRevision = "2026-06-13T10:00:00"
            )
        )

        assertEquals("Sesión pausada", paused.label)
        assertEquals(EntryCategory.PAUSED, paused.category)
        assertEquals("Revisión terminada", completed.label)
    }

    @Test
    fun `respeta prioridad de relacion cerrada revision cien y en proceso`() {
        val relationClosed = calculateEntryStatus(entry(todasRelacionCompleta = true))
        val reviewComplete = calculateEntryStatus(
            entry(totalFacturas = 2, facturasCompletas = 2)
        )
        val inProgress = calculateEntryStatus(entry(usuarioTomoDocumento = "EDUARDO"))

        assertEquals(EntryLifecycle.RELATION_CLOSED, relationClosed.lifecycle)
        assertEquals(EntryLifecycle.REVIEW_COMPLETE, reviewComplete.lifecycle)
        assertEquals(EntryLifecycle.IN_PROGRESS, inProgress.lifecycle)
    }

    @Test
    fun `normaliza qr de bulto y descarta contenido vacio`() {
        assertEquals(
            "01C2635625400000",
            normalizeScannedReference("01c2635625400000 1-3\r\n")
        )
        assertEquals("ABC123", normalizeScannedReference("  abc123  "))
        assertEquals(
            "01C2433408700000",
            normalizeScannedReference("01c2433408700000")
        )
        assertNull(normalizeScannedReference(" \r\n\t "))
    }

    @Test
    fun `filtra por referencia cliente supervisor nota y estado`() {
        val assigned = entry(
            crefAgrupada = "REF-900",
            nombreCliente = "Cliente del Norte",
            asignSupervisor = "SUPERVISOR01",
            asignNota = "Liberar antes del corte"
        )
        val inProgress = entry(
            crefAgrupada = "REF-901",
            usuarioRevisando = "EDUARDO"
        )

        assertTrue(matchesEntryFilter(assigned, "norte", FilterStatus.TOTAL))
        assertTrue(matchesEntryFilter(assigned, "supervisor01", FilterStatus.TOTAL))
        assertTrue(matchesEntryFilter(assigned, "corte", FilterStatus.PENDIENTE))
        assertTrue(matchesEntryFilter(inProgress, "", FilterStatus.REVISION))
        assertFalse(matchesEntryFilter(inProgress, "", FilterStatus.FINALIZADO))
    }

    @Test
    fun `bloquea cuando otro usuario tiene la revision o la toma`() {
        val entry = entry(usuarioRevisando = "OPERADOR02")

        assertTrue(isEntryLocked(entry, "EDUARDO"))
        assertFalse(isEntryLocked(entry, "operador02"))
        assertTrue(isEntryLocked(entry(usuarioTomoDocumento = "OPERADOR02"), "EDUARDO"))
        assertFalse(isEntryLocked(entry(usuarioTomoDocumento = "OPERADOR02"), "operador02"))
    }

    private fun entry(
        crefAgrupada: String = "REF-001",
        nombreCliente: String? = "Cliente prueba",
        totalFacturas: Int = 1,
        facturasCompletas: Int = 0,
        inicioRevision: String? = null,
        finRevision: String? = null,
        usuarioRevisando: String? = null,
        usuarioTomoDocumento: String? = null,
        todasRelacionCompleta: Boolean = false,
        asignSupervisor: String? = null,
        asignNota: String? = null
    ): EntradaPendienteDto {
        return EntradaPendienteDto(
            crefAgrupada = crefAgrupada,
            nombreReferencia = crefAgrupada,
            clienteId = "C-001",
            nombreCliente = nombreCliente,
            dFecha = null,
            totalFacturas = totalFacturas,
            facturasCompletas = facturasCompletas,
            totalPartidas = 0,
            partidasRevisadas = 0,
            inicioRevision = inicioRevision,
            finRevision = finRevision,
            usuarioRevisando = usuarioRevisando,
            usuarioTomoDocumento = usuarioTomoDocumento,
            todasRelacionCompleta = todasRelacionCompleta,
            esAsignada = asignSupervisor != null,
            asignPrioridad = null,
            idAsignacion = null,
            asignSupervisor = asignSupervisor,
            asignNota = asignNota
        )
    }
}
