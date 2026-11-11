package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaFormSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaStepsTest {

    private fun completeForm() = EntradaFormSnapshot(
        ref4 = "004217",
        fecha = "2026-11-10T14:32",
        codCliente = "C001",
        manejo = "MC",
        detalles = listOf(EntradaDetail(12.0, descripcion = "Cajas"))
    )

    private fun status(form: EntradaFormSnapshot, section: EntradaSection) =
        EntradaSteps.statuses(form).first { it.section == section }

    @Test
    fun `entrada en blanco pide generar, cliente, manejo y un concepto`() {
        val form = EntradaFormSnapshot()

        assertEquals("Sin generar", status(form, EntradaSection.REFERENCE).text)
        assertEquals("Faltan 2", status(form, EntradaSection.ARRIVAL).text)
        assertEquals("Falta 1", status(form, EntradaSection.MERCHANDISE).text)
        assertEquals("Opcional", status(form, EntradaSection.CLOSE).text)
        assertEquals(EntradaStepTone.Missing, status(form, EntradaSection.ARRIVAL).tone)
    }

    @Test
    fun `la fecha en blanco se reclama en Referencia, que es donde se captura`() {
        val form = completeForm().copy(fecha = "")

        assertEquals(1, EntradaSteps.missingBySection(form)[EntradaSection.REFERENCE])
        assertEquals("Falta 1", status(form, EntradaSection.REFERENCE).text)
    }

    @Test
    fun `con todo capturado los pasos quedan en Listo y Cierre sigue opcional`() {
        val form = completeForm()

        assertEquals("Listo", status(form, EntradaSection.REFERENCE).text)
        assertEquals("Listo", status(form, EntradaSection.ARRIVAL).text)
        assertEquals("1 concepto", status(form, EntradaSection.MERCHANDISE).text)
        assertTrue(status(form, EntradaSection.MERCHANDISE).complete)
        assertEquals("Opcional", status(form, EntradaSection.CLOSE).text)
        assertFalse(status(form, EntradaSection.CLOSE).complete)
    }

    @Test
    fun `los conceptos se cuentan en plural`() {
        val form = completeForm().copy(
            detalles = listOf(EntradaDetail(1.0, descripcion = "A"), EntradaDetail(2.0, descripcion = "B"))
        )

        assertEquals("2 conceptos", status(form, EntradaSection.MERCHANDISE).text)
    }

    @Test
    fun `Cierre se marca hecho solo cuando la entrada ya existe`() {
        val form = completeForm().copy(isExisting = true)

        assertTrue(status(form, EntradaSection.CLOSE).complete)
    }

    @Test
    fun `hazmat, dano e in-bond suman faltantes solo a Arribo`() {
        val form = completeForm().copy(hazmat = true, danada = true, inBond = true)

        // Clase y UN, descripcion y foto del dano, valor in-bond.
        assertEquals(5, EntradaSteps.missingBySection(form)[EntradaSection.ARRIVAL])
        assertEquals("Faltan 5", status(form, EntradaSection.ARRIVAL).text)
        assertEquals("Listo", status(form, EntradaSection.REFERENCE).text)
    }

    @Test
    fun `las fotos del dano ya subidas cuentan si se pasa el total`() {
        val form = completeForm().copy(
            danada = true,
            danadaDescripcion = "Caja aplastada"
        )

        assertEquals(1, EntradaSteps.missingBySection(form)[EntradaSection.ARRIVAL])
        assertEquals(null, EntradaSteps.missingBySection(form, damagePhotoCount = 2)[EntradaSection.ARRIVAL])
    }

    @Test
    fun `sin consecutivo y en Referencia el boton genera la entrada`() {
        val action = EntradaSteps.primaryAction(EntradaFormSnapshot(), EntradaSection.REFERENCE, isNewEntry = true)

        assertEquals(EntradaStepAction.Generate, action)
    }

    @Test
    fun `con faltantes el boton propone el paso que sigue y nunca bloquea`() {
        val form = completeForm().copy(manejo = "", detalles = emptyList())

        assertEquals(
            EntradaStepAction.Next(EntradaSection.ARRIVAL),
            EntradaSteps.primaryAction(form, EntradaSection.REFERENCE, isNewEntry = true)
        )
        assertEquals(
            EntradaStepAction.Next(EntradaSection.MERCHANDISE),
            EntradaSteps.primaryAction(form, EntradaSection.ARRIVAL, isNewEntry = true)
        )
        assertEquals(
            EntradaStepAction.Next(EntradaSection.CLOSE),
            EntradaSteps.primaryAction(form, EntradaSection.MERCHANDISE, isNewEntry = true)
        )
    }

    @Test
    fun `en el ultimo paso con faltantes el boton lleva al primer paso incompleto`() {
        val form = completeForm().copy(manejo = "", detalles = emptyList())

        val action = EntradaSteps.primaryAction(form, EntradaSection.CLOSE, isNewEntry = true)

        assertEquals(EntradaStepAction.GoTo(EntradaSection.ARRIVAL, missing = 2), action)
    }

    @Test
    fun `sin faltantes el boton hace la accion final desde cualquier paso`() {
        val form = completeForm()

        EntradaSection.entries.forEach { section ->
            assertEquals(
                EntradaStepAction.Finish,
                EntradaSteps.primaryAction(form, section, isNewEntry = true)
            )
        }
    }

    @Test
    fun `una entrada que ya existe conserva su boton de siempre aunque falte algo`() {
        val form = completeForm().copy(manejo = "", isExisting = true)

        EntradaSection.entries.forEach { section ->
            assertEquals(
                EntradaStepAction.Finish,
                EntradaSteps.primaryAction(form, section, isNewEntry = false)
            )
        }
    }

    @Test
    fun `sin consecutivo pero fuera de Referencia no se pide generar desde ahi, se lleva a Referencia`() {
        val form = EntradaFormSnapshot()

        val action = EntradaSteps.primaryAction(form, EntradaSection.CLOSE, isNewEntry = true)

        assertTrue(action is EntradaStepAction.GoTo)
        assertEquals(EntradaSection.REFERENCE, (action as EntradaStepAction.GoTo).target)
    }
}
