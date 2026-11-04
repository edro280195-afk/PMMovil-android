package com.example.pmmovil.ui.caseta

import com.example.pmmovil.data.model.CasetaDetalle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasetaRulesTest {

    @Test
    fun `una relacion autorizada esta lista para salida`() {
        val detail = CasetaDetalle(estatus = "COMPLETA", estatusCarga = "AUTORIZADA")

        assertTrue(CasetaRules.isReadyForExit(detail))
    }

    @Test
    fun `una relacion ya salida no se puede confirmar otra vez`() {
        val detail = CasetaDetalle(estatus = "COMPLETA", estatusCarga = "SALIDA")

        assertFalse(CasetaRules.isReadyForExit(detail))
    }

    @Test
    fun `una relacion ocupada no se puede confirmar`() {
        val detail = CasetaDetalle(estatus = "RELACIÓN OCUPADA", estatusCarga = "AUTORIZADA")

        assertFalse(CasetaRules.isReadyForExit(detail))
    }

    @Test
    fun `normaliza una lectura dejando solo el primer token`() {
        assertEquals("REL001", CasetaRules.normalizeScannedRelation(" REL001 \t CAJA01 "))
    }

    @Test
    fun `la requisicion de cruce da la relacion y no su primer pedazo`() {
        // Textos reales (28-oct-2026). Antes quedaba "R-01I26106070", que el servidor no encuentra.
        assertEquals("01I26106070", CasetaRules.normalizeScannedRelation("R-01I26106070  0000  101"))
        assertEquals("01I26106070", CasetaRules.normalizeScannedRelation("  r-01i26106070     0000   101\r\n"))
    }

    @Test
    fun `la orden de carga da la relacion sin su version`() {
        // Antes quedaba "01I26106418-1".
        assertEquals("01I26106418", CasetaRules.normalizeScannedRelation("01I26106418-1"))
    }

    @Test
    fun `una relacion escrita o leida sin mas texto se queda como esta`() {
        assertEquals("01I26106418", CasetaRules.normalizeScannedRelation(" 01I26106418 "))
    }
}
