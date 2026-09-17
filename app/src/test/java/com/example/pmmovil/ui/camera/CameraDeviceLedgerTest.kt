package com.example.pmmovil.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraDeviceLedgerTest {

    @Test
    fun `sin historial todo esta libre`() {
        val ledger = CameraDeviceLedger()
        assertTrue(ledger.readyToOpen("0"))
        assertTrue(ledger.readyToOpen(null))
        assertEquals("libre", ledger.describeBlockers("0"))
    }

    @Test
    fun `una camara abierta bloquea reabrir ese mismo dispositivo hasta que Android confirma el cierre`() {
        val ledger = CameraDeviceLedger()
        ledger.onUnavailable("0") // la abrimos

        assertFalse(ledger.readyToOpen("0"))
        assertEquals("ocupada=0", ledger.describeBlockers("0"))

        ledger.onAvailable("0") // Android confirma que el HAL ya cerro
        assertTrue(ledger.readyToOpen("0"))
    }

    @Test
    fun `el cierre de un dispositivo NO bloquea abrir un dispositivo distinto`() {
        // Este es el caso que rompia el relevo rostro (camara 1) -> QR (camara 0)
        // en el login: la trasera no tiene por que esperar a que la frontal
        // termine de cerrarse, son sensores independientes.
        val ledger = CameraDeviceLedger()
        ledger.onUnavailable("1") // la frontal esta abierta / cerrandose

        assertTrue(ledger.readyToOpen("0"))
        assertFalse(ledger.readyToOpen("1"))
    }

    @Test
    fun `un dispositivo desconocido siempre esta listo`() {
        val ledger = CameraDeviceLedger()
        ledger.onUnavailable("0")
        assertTrue(ledger.readyToOpen(null))
    }

    @Test
    fun `una camara ocupada por otra app tambien bloquea reabrirla`() {
        val ledger = CameraDeviceLedger()
        ledger.onUnavailable("1")
        assertFalse(ledger.readyToOpen("1"))
        assertTrue(ledger.readyToOpen("0"))
    }

    @Test
    fun `olvidar destraba solo el dispositivo indicado`() {
        val ledger = CameraDeviceLedger()
        ledger.onUnavailable("0")
        ledger.onUnavailable("1")

        ledger.forget("0")
        assertTrue(ledger.readyToOpen("0"))
        assertFalse(ledger.readyToOpen("1"))
    }
}
