package com.example.pmmovil.ui.scanactions

import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.model.TraceCajaDto
import com.example.pmmovil.data.model.TraceKind
import com.example.pmmovil.data.model.TraceResultDto
import com.example.pmmovil.data.repository.TraceOutcome
import com.example.pmmovil.data.scanner.ScanActionId
import com.example.pmmovil.data.scanner.ScannedCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanActionsStateTest {

    private val everything: (Int, String) -> Boolean = { _, _ -> true }
    private val gateOnly: (Int, String) -> Boolean = { module, _ -> module == MobileModuleIds.GATE }
    private val box = ScannedCode.Caja("E-017848/26")

    private val found = TraceResultDto(
        success = true,
        kind = TraceKind.CAJA,
        found = true,
        actions = listOf("EXIT_GATE", "FIND_IN_YARD"),
        caja = TraceCajaDto(casetaId = "E-017848/26")
    )

    @Test
    fun `only the kinds the server covers are looked up`() {
        assertTrue(hasTrace(ScannedCode.Caja("E-017846/26")))
        assertTrue(hasTrace(ScannedCode.Relacion("01I26104546")))
        assertTrue(hasTrace(ScannedCode.Carpeta("01C263582800000")))
        assertTrue(hasTrace(ScannedCode.Bulto("01C263582800000", 3, 12)))
        assertTrue(hasTrace(ScannedCode.PreRegistro("PMMPR|7K2Q-4481|A1")))
        // El gafete lleva una credencial: nunca sale del aparato.
        assertFalse(hasTrace(ScannedCode.Gafete))
        assertFalse(hasTrace(ScannedCode.ProductoCliente("+30S77120045")))
        assertFalse(hasTrace(ScannedCode.Candidato("PECE0511COL")))
        assertFalse(hasTrace(ScannedCode.Desconocido("HOLA")))
    }

    @Test
    fun `with a card the actions come from the state of the piece`() {
        val state = stateAfter(box, "E-17848/26", TraceOutcome.Found(found), everything)

        assertEquals(TraceUiState.Ready(found), state.trace)
        assertEquals(listOf(ScanActionId.ExitGate, ScanActionId.FindInYard), state.actions.map { it.id })
        assertTrue(state.actions.first().primary)
    }

    @Test
    fun `a code that does not exist offers nothing because there is nothing to open`() {
        val state = stateAfter(box, "E-17848/26", TraceOutcome.NotFound, everything)

        assertEquals(TraceUiState.NotFound, state.trace)
        assertTrue(state.actions.isEmpty())
    }

    @Test
    fun `without a connection the actions fall back to what the code itself says`() {
        val state = stateAfter(box, "E-17848/26", TraceOutcome.Unavailable, everything)

        assertEquals(TraceUiState.Unavailable, state.trace)
        // Una caída de la red no debe dejar al guardia sin poder dar salida a una caja.
        assertEquals(
            listOf(ScanActionId.CreateEntry, ScanActionId.ExitGate, ScanActionId.FindInYard),
            state.actions.map { it.id }
        )
    }

    @Test
    fun `the fallback still respects the post permissions`() {
        val state = stateAfter(box, "E-17848/26", TraceOutcome.Unavailable, gateOnly)

        assertEquals(listOf(ScanActionId.FindInYard), state.actions.map { it.id })
    }

    @Test
    fun `when the server does not cover the code or the post cannot look it up it behaves as before`() {
        val rejected = stateAfter(box, "E-17848/26", TraceOutcome.Rejected, everything)
        val forbidden = stateAfter(box, "E-17848/26", TraceOutcome.Forbidden, everything)

        assertEquals(TraceUiState.NotRequested, rejected.trace)
        assertEquals(TraceUiState.NotRequested, forbidden.trace)
        assertEquals(3, rejected.actions.size)
        assertEquals(rejected.actions, forbidden.actions)
    }
}
