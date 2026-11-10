package com.example.pmmovil.ui.casetaentrada

import com.example.pmmovil.data.model.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CasetaEntradaStepsTest {

    /** Formulario con todo lo obligatorio de una entrada normal (sin condicionales). */
    private fun completeForm() = CasetaEntradaForm(
        idBodega = "01",
        operation = CasetaEntradaRules.OP_IMPORT,
        movement = "DESCARGAS",
        idBoxType = "56",
        boxTypeDescription = "CAJA CERRADA 53'",
        idCustomer = "C001",
        customerName = "CLIENTE PRUEBA",
        idBoxLine = "T001",
        boxLineName = "LINEA CAJA",
        idTruckLine = "T002",
        truckLineName = "LINEA CAMION",
        boxNumber = "BOX123"
    )

    @Test
    fun an_empty_form_reports_what_is_missing_in_each_step() {
        val missing = CasetaEntradaSteps.missingByStep(CasetaEntradaForm())

        // Camión: línea de camión y número de caja.
        assertEquals(2, missing.getValue(CasetaStep.Camion))
        // Caja: movimiento, cliente, tipo de caja y línea de caja (la operación ya viene en IMPORTACION).
        assertEquals(4, missing.getValue(CasetaStep.Caja))
        assertEquals(0, missing.getValue(CasetaStep.Evidencia))
    }

    @Test
    fun every_validation_key_belongs_to_the_step_where_it_is_captured() {
        val camion = listOf("idBodega", "truckLine", "boxNumber", "plates")
        val caja = listOf(
            "operation", "movement", "idBoxType", "customer", "boxLine",
            "idCountry", "guideCount", "pedimento", "temperature", "fuel"
        )
        camion.forEach { assertEquals(it, CasetaStep.Camion, CasetaEntradaSteps.stepOf(it)) }
        caja.forEach { assertEquals(it, CasetaStep.Caja, CasetaEntradaSteps.stepOf(it)) }
        assertEquals(CasetaStep.Evidencia, CasetaEntradaSteps.stepOf("photos"))
    }

    @Test
    fun direct_movement_asks_for_plates_in_camion_and_country_in_caja() {
        val missing = CasetaEntradaSteps.missingByStep(completeForm().copy(movement = "DIRECTO"))

        assertEquals(1, missing.getValue(CasetaStep.Camion)) // placas
        assertEquals(1, missing.getValue(CasetaStep.Caja)) // país de origen
    }

    @Test
    fun refrigerated_box_asks_for_temperature_and_fuel_in_caja() {
        val missing = CasetaEntradaSteps.missingByStep(
            completeForm().copy(boxTypeDescription = "CAJA REFRIGERADA 53'")
        )

        assertEquals(0, missing.getValue(CasetaStep.Camion))
        assertEquals(2, missing.getValue(CasetaStep.Caja))
    }

    @Test
    fun a_truck_without_box_only_needs_movement_and_truck_line() {
        val form = CasetaEntradaForm(tipoRegistro = TipoRegistro.TRACTOCAMION)

        val missing = CasetaEntradaSteps.missingByStep(form)

        assertEquals(1, missing.getValue(CasetaStep.Camion)) // línea de camión
        assertEquals(1, missing.getValue(CasetaStep.Caja)) // movimiento
    }

    @Test
    fun steps_agree_with_what_saving_demands() {
        val form = completeForm()
        assertTrue(CasetaEntradaRules.validate(form).isEmpty())
        assertEquals(0, CasetaEntradaSteps.totalMissing(form))

        val broken = form.copy(idCustomer = "", customerName = "")
        assertEquals(CasetaEntradaRules.validate(broken).size, CasetaEntradaSteps.totalMissing(broken))
    }

    @Test
    fun next_moves_forward_without_blocking_even_if_the_current_step_is_incomplete() {
        val form = CasetaEntradaForm()

        assertEquals(
            CasetaStepAction.Next(CasetaStep.Caja),
            CasetaEntradaSteps.primaryAction(form, CasetaStep.Camion)
        )
        assertEquals(
            CasetaStepAction.Next(CasetaStep.Evidencia),
            CasetaEntradaSteps.primaryAction(form, CasetaStep.Caja)
        )
    }

    @Test
    fun last_step_sends_the_guard_back_to_the_first_step_that_is_missing_something() {
        // Falta el cliente (paso Caja) y el número de caja (paso Camión): el primero es Camión.
        val form = completeForm().copy(idCustomer = "", customerName = "", boxNumber = "")

        val action = CasetaEntradaSteps.primaryAction(form, CasetaStep.Evidencia)

        assertEquals(CasetaStepAction.GoTo(CasetaStep.Camion, 2), action)
    }

    @Test
    fun last_step_goes_to_caja_when_only_caja_is_missing() {
        val form = completeForm().copy(idCustomer = "", customerName = "")

        val action = CasetaEntradaSteps.primaryAction(form, CasetaStep.Evidencia)

        assertEquals(CasetaStepAction.GoTo(CasetaStep.Caja, 1), action)
    }

    @Test
    fun save_is_offered_from_any_step_once_everything_required_is_there() {
        val form = completeForm()

        CasetaStep.entries.forEach { step ->
            assertEquals(CasetaStepAction.Save, CasetaEntradaSteps.primaryAction(form, step))
        }
    }

    @Test
    fun status_text_distinguishes_missing_done_and_untouched() {
        val empty = CasetaEntradaSteps.statuses(CasetaEntradaForm())
        assertEquals("Faltan 2", empty[0].text)
        assertEquals(CasetaStepTone.Missing, empty[0].tone)
        assertEquals("Faltan 4", empty[1].text)

        val done = CasetaEntradaSteps.statuses(completeForm())
        assertEquals("Listo", done[0].text)
        assertEquals(CasetaStepTone.Done, done[0].tone)
        assertEquals("Listo", done[1].text)
    }

    @Test
    fun a_step_with_nothing_missing_shows_done_only_when_something_was_chosen() {
        // Tractocamión sin caja: en Caja solo se exige el movimiento. Con él elegido, Caja queda «Listo»;
        // Camión sigue pidiendo la línea.
        val chosen = CasetaEntradaSteps.statuses(
            CasetaEntradaForm(tipoRegistro = TipoRegistro.TRACTOCAMION, movement = "RECOLECCION/PICKUP")
        )
        assertEquals(CasetaStepTone.Missing, chosen[0].tone)
        assertEquals(CasetaStepTone.Done, chosen[1].tone)

        // Sin nada capturado en Caja, lo que falta manda («Faltan 1»), no «Sin datos».
        val untouched = CasetaEntradaSteps.statuses(CasetaEntradaForm(tipoRegistro = TipoRegistro.TRACTOCAMION))
        assertEquals(CasetaStepTone.Missing, untouched[1].tone)
        assertEquals("Faltan 1", untouched[1].text)
    }

    @Test
    fun evidence_is_always_optional_and_reports_the_photo_count() {
        val none = CasetaEntradaSteps.statuses(completeForm())[2]
        assertEquals("Opcional", none.text)
        assertEquals(0, none.missing)
        assertEquals(CasetaStepTone.Neutral, none.tone)

        val one = CasetaEntradaSteps.statuses(completeForm().copy(localPhotoPaths = listOf("a.jpg")))[2]
        assertEquals("1 foto", one.text)

        val three = CasetaEntradaSteps.statuses(
            completeForm().copy(localPhotoPaths = listOf("a.jpg", "b.jpg", "c.jpg"))
        )[2]
        assertEquals("3 fotos", three.text)
    }

    @Test
    fun the_default_operation_does_not_count_as_touching_the_caja_step() {
        assertFalse(CasetaEntradaSteps.touched(CasetaEntradaForm(), CasetaStep.Caja))
        assertTrue(CasetaEntradaSteps.touched(CasetaEntradaForm(movement = "DIRECTO"), CasetaStep.Caja))
    }
}
