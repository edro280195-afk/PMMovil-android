package com.example.pmmovil.ui.casetaentrada

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasetaEntradaDraftRulesTest {

    private val empty = CasetaEntradaForm()

    @Test
    fun `an untouched capture has nothing unsaved`() {
        assertFalse(CasetaEntradaDraftRules.isUnsaved(empty, empty))
    }

    @Test
    fun `the default warehouse the catalogs put in does not count as a change`() {
        val withDefaultWarehouse = empty.copy(idBodega = "02", bodega = "Nuevo Laredo")
        assertFalse(CasetaEntradaDraftRules.isUnsaved(withDefaultWarehouse, empty))
    }

    @Test
    fun `typing a plate, a customer or taking a photo is something to lose`() {
        assertTrue(CasetaEntradaDraftRules.isUnsaved(empty.copy(plates = "ABC123"), empty))
        assertTrue(CasetaEntradaDraftRules.isUnsaved(empty.copy(customerName = "ACME"), empty))
        assertTrue(CasetaEntradaDraftRules.isUnsaved(empty.copy(localPhotoPaths = listOf("/data/foto.jpg")), empty))
    }

    @Test
    fun `opening a saved record and leaving it untouched is not a change`() {
        val loaded = empty.copy(casetaId = "C-0001", plates = "ABC123", boxNumber = "CAJA1")
        assertFalse(CasetaEntradaDraftRules.isUnsaved(loaded, loaded))
    }

    @Test
    fun `editing any field of a saved record is a change, even one a new capture would ignore`() {
        val loaded = empty.copy(casetaId = "C-0001", plates = "ABC123")
        val edited = loaded.copy(observations = "Sello roto")
        assertTrue(CasetaEntradaDraftRules.isUnsaved(edited, loaded))
    }

    @Test
    fun `a new capture with only values nobody backs up has nothing to lose`() {
        // Observaciones y temperatura no cuentan como dato respaldable en una captura nueva.
        val onlyObservations = empty.copy(observations = "nota", temperature = "4")
        assertFalse(CasetaEntradaDraftRules.isUnsaved(onlyObservations, empty))
    }

    @Test
    fun `a resumed draft is unsaved against an empty baseline`() {
        val resumed = empty.copy(boxNumber = "CAJA1", localPhotoPaths = listOf("/data/foto.jpg"))
        assertTrue(CasetaEntradaDraftRules.isUnsaved(resumed, CasetaEntradaForm()))
    }

    @Test
    fun `after saving, the empty form against an empty baseline is clean again`() {
        assertFalse(CasetaEntradaDraftRules.isUnsaved(CasetaEntradaForm(), CasetaEntradaForm()))
    }
}
