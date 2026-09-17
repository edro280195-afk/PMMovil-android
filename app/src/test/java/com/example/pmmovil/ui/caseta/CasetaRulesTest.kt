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
}
