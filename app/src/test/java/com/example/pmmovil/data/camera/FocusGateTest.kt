package com.example.pmmovil.data.camera

import android.hardware.camera2.CaptureResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusGateTest {

    private val scan = CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN
    private val focused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED

    /** Reloj y planificador simulados: [advance] corre las tareas que ya vencieron. */
    private class Sim {
        var clock = 0L
        private val pending = mutableListOf<Pair<Long, () -> Unit>>()
        fun now() = clock
        fun post(delayMs: Long, action: () -> Unit) {
            pending += (clock + delayMs) to action
        }
        fun runAll() {
            while (pending.isNotEmpty()) {
                val next = pending.minByOrNull { it.first }!!
                pending.remove(next)
                clock = next.first
                next.second()
            }
        }
    }

    private fun gate(sim: Sim, state: () -> Int?, log: MutableList<String> = mutableListOf()) =
        FocusGate(state, sim::now, sim::post) { log += it }

    @Test
    fun `solo se espera mientras el lente esta buscando`() {
        assertTrue(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN))
        assertTrue(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN))
        assertFalse(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED))
        assertFalse(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED))
        // Ya se dio por vencido o no hay AF: esperar no lo arregla.
        assertFalse(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED))
        assertFalse(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED))
        assertFalse(FocusGatePolicy.isScanning(CaptureResult.CONTROL_AF_STATE_INACTIVE))
        assertFalse(FocusGatePolicy.isScanning(null))
    }

    @Test
    fun `con el enfoque asentado dispara en el acto`() {
        val sim = Sim()
        var fired = 0
        gate(sim, { focused }).awaitSettled { fired++ }

        assertEquals(1, fired)
        assertEquals(0L, sim.clock)
    }

    @Test
    fun `sin dato de enfoque dispara en el acto`() {
        val sim = Sim()
        var fired = 0
        gate(sim, { null }).awaitSettled { fired++ }

        assertEquals(1, fired)
    }

    @Test
    fun `espera a que el lente deje de buscar y dispara una sola vez`() {
        val sim = Sim()
        var fired = 0
        val log = mutableListOf<String>()
        // Busca durante 100 ms y luego enfoca.
        gate(sim, { if (sim.clock < 100) scan else focused }, log).awaitSettled { fired++ }
        sim.runAll()

        assertEquals(1, fired)
        assertTrue(sim.clock in 100..(100 + FocusGatePolicy.POLL_MS))
        assertTrue(log.single(), log.single().contains("PASSIVE_FOCUSED"))
    }

    @Test
    fun `si nunca enfoca dispara al vencer el plazo`() {
        val sim = Sim()
        var fired = 0
        val log = mutableListOf<String>()
        gate(sim, { scan }, log).awaitSettled { fired++ }
        sim.runAll()

        assertEquals(1, fired)
        assertTrue(sim.clock >= FocusGatePolicy.MAX_WAIT_MS)
        assertTrue(sim.clock < FocusGatePolicy.MAX_WAIT_MS + FocusGatePolicy.POLL_MS)
        assertTrue(log.single(), log.single().contains("vencio el plazo"))
    }
}
