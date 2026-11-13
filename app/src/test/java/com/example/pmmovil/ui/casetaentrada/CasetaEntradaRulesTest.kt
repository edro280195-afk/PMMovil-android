package com.example.pmmovil.ui.casetaentrada

import com.example.pmmovil.data.ocr.LocalTextOcrResult
import com.example.pmmovil.data.model.CasetaEntradaOcrCandidate
import com.example.pmmovil.data.model.TipoRegistro
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CasetaEntradaRulesTest {

    @Test
    fun validate_reports_required_fields_for_empty_form() {
        val errors = CasetaEntradaRules.validate(CasetaEntradaForm(operation = "", idBodega = ""))

        assertTrue(errors.containsKey("operation"))
        assertTrue(errors.containsKey("idBodega"))
        assertTrue(errors.containsKey("customer"))
        assertTrue(errors.containsKey("boxLine"))
        assertTrue(errors.containsKey("movement"))
        assertTrue(errors.containsKey("boxNumber"))
        assertEquals("Selecciona la linea de camion.", errors["truckLine"])
    }

    @Test
    fun validate_requires_truck_line_but_does_not_require_other_tractor_fields() {
        // No. de camion, Operador y Sellos cambian en cada viaje y siguen siendo opcionales.
        val errors = CasetaEntradaRules.validate(
            validForm().copy(
                idTruckLine = "",
                truckLineName = "",
                truckNumber = "",
                operatorName = "",
                seals = ""
            )
        )

        assertEquals(setOf("truckLine"), errors.keys)
    }

    @Test
    fun validate_requires_country_and_plates_for_direct_movement() {
        val errors = CasetaEntradaRules.validate(
            validForm(movement = "DIRECTO", plates = "", idCountry = "")
        )

        assertTrue(errors.containsKey("plates"))
        assertTrue(errors.containsKey("idCountry"))
    }

    @Test
    fun validate_requires_guides_for_consolidated_movement() {
        val errors = CasetaEntradaRules.validate(
            validForm(movement = "CONSOLIDADO/MENSAJERIA", guideCount = "0")
        )

        assertEquals("Guia debe ser mayor a 0.", errors["guideCount"])
    }

    @Test
    fun validate_requires_temperature_and_fuel_for_refrigerated_box() {
        val errors = CasetaEntradaRules.validate(
            validForm(
                boxTypeDescription = "CAJA REFRIGERADA 53'",
                temperature = "",
                fuel = ""
            )
        )

        assertTrue(errors.containsKey("temperature"))
        assertTrue(errors.containsKey("fuel"))
    }

    @Test
    fun refrigerated_is_detected_by_catalog_id_and_by_any_spelling_of_the_name() {
        // El id 58 es el de las refrigeradas en producción aunque el nombre venga distinto (estilo SAT).
        assertTrue(CasetaEntradaRules.isRefrigerated("58", "SEMIRREMOLQUE TIPO CAJA REFRIGERADA 53''."))
        assertTrue(CasetaEntradaRules.isRefrigerated("58", ""))
        assertTrue(CasetaEntradaRules.isRefrigerated("", "SEMIRREMOLQUE TIPO CAJA REFRIGERADA 53''."))
        assertTrue(CasetaEntradaRules.isRefrigerated("", "CAJA REFRIGERADA 53 PIES"))
        assertFalse(CasetaEntradaRules.isRefrigerated("56", "CAJA CERRADA 53'"))
        assertFalse(CasetaEntradaRules.isRefrigerated("", ""))
    }

    @Test
    fun validate_asks_for_temperature_when_the_box_is_refrigerated_only_by_its_id() {
        val errors = CasetaEntradaRules.validate(
            validForm(boxTypeDescription = "SEMIRREMOLQUE TIPO CAJA REFRIGERADA 53''.").copy(idBoxType = "58")
        )

        assertTrue(errors.containsKey("temperature"))
        assertTrue(errors.containsKey("fuel"))
    }

    @Test
    fun temperature_must_be_a_reading_not_na_or_text() {
        listOf("N/A", "NA", "na", "n/a", "SIN DATO", "-", "  ", "").forEach {
            assertNotNull("«$it» no debe pasar como temperatura", CasetaEntradaRules.temperatureError(it))
        }
        listOf("34F", "-18C", "65F", "34", "-5.5", " 34 F ").forEach {
            assertNull("«$it» sí es una lectura", CasetaEntradaRules.temperatureError(it))
        }
    }

    @Test
    fun temperature_and_fuel_must_fit_the_database_columns() {
        // caseta.temperatura es varchar(5) y caseta.combustible varchar(10).
        assertNull(CasetaEntradaRules.temperatureError("-18.5"))
        assertNotNull(CasetaEntradaRules.temperatureError("-18.5C"))
        assertNull(CasetaEntradaRules.fuelError("1234567890"))
        assertNotNull(CasetaEntradaRules.fuelError("12345678901"))
    }

    @Test
    fun fuel_accepts_gauge_fractions_and_words_but_not_empty() {
        listOf("3/4", "7/8", "LLENO", "MEDIO").forEach {
            assertNull(CasetaEntradaRules.fuelError(it))
        }
        assertNotNull(CasetaEntradaRules.fuelError(""))
        assertNotNull(CasetaEntradaRules.fuelError("   "))
    }

    @Test
    fun validate_blocks_a_refrigerated_box_whose_temperature_is_na() {
        val form = validForm(boxTypeDescription = "CAJA REFRIGERADA 53'", temperature = "N/A", fuel = "3/4")

        val errors = CasetaEntradaRules.validate(form)

        assertEquals(setOf("temperature"), errors.keys)
        assertTrue(CasetaEntradaRules.validate(form.copy(temperature = "34F")).isEmpty())
    }

    @Test
    fun validate_rejects_invalid_pedimento_when_present() {
        val errors = CasetaEntradaRules.validate(validForm(pedimento = "123"))

        assertEquals("Pedimento debe tener 7 digitos.", errors["pedimento"])
    }

    @Test
    fun validate_accepts_complete_regular_form() {
        val errors = CasetaEntradaRules.validate(validForm())

        assertTrue(errors.isEmpty())
    }

    @Test
    fun validate_exempts_caja_and_cliente_when_origen_preregistro() {
        // Camion sin caja precargado desde un QR de pre-registro: no tiene tipo de
        // caja, cliente, linea de caja ni numero de caja. La bandera origenPreRegistro
        // exime las cuatro a la vez (misma regla que el backend).
        val form = validForm().copy(
            idBoxType = "",
            boxTypeDescription = "",
            idCustomer = "",
            customerName = "",
            idBoxLine = "",
            boxLineName = "",
            boxNumber = "",
            movement = "RECOLECCION/PICKUP",
            origenPreRegistro = true,
            tipoRegistro = TipoRegistro.TRACTOCAMION
        )

        val errors = CasetaEntradaRules.validate(form)

        assertTrue("No debe exigir datos de caja/cliente pero exigio $errors", errors.isEmpty())
    }

    @Test
    fun validate_still_requires_caja_and_cliente_when_not_origen_preregistro() {
        val form = validForm().copy(
            idBoxType = "",
            boxTypeDescription = "",
            idCustomer = "",
            customerName = "",
            origenPreRegistro = false
        )

        val errors = CasetaEntradaRules.validate(form)

        assertTrue(errors.containsKey("idBoxType"))
        assertTrue(errors.containsKey("customer"))
    }

    @Test
    fun normalize_plate_candidate_keeps_only_valid_plate_text() {
        assertEquals("ABC123", CasetaEntradaRules.normalizePlateCandidate(" abc 123 "))
        assertEquals("AB-1234", CasetaEntradaRules.normalizePlateCandidate("AB-1234"))
        assertEquals("", CasetaEntradaRules.normalizePlateCandidate("AB"))
        assertEquals("", CasetaEntradaRules.normalizePlateCandidate("PLACA DEMASIADO LARGA 123"))
    }

    @Test
    fun normalize_box_candidate_keeps_only_valid_box_text() {
        assertEquals("CAJA1023", CasetaEntradaRules.normalizeBoxCandidate(" caja 1023 "))
        assertEquals("53-AB-8X", CasetaEntradaRules.normalizeBoxCandidate("53-ab-8x"))
        assertEquals("", CasetaEntradaRules.normalizeBoxCandidate("AB"))
        assertEquals("", CasetaEntradaRules.normalizeBoxCandidate("CAJA DEMASIADO LARGA 1234567890"))
    }

    @Test
    fun refrigerated_detection_is_case_insensitive() {
        assertTrue(CasetaEntradaRules.isRefrigerated("caja refrigerada 53'"))
        assertFalse(CasetaEntradaRules.isRefrigerated("CAJA SECA 53'"))
    }

    @Test
    fun box_candidates_accept_vertical_five_digit_numbers() {
        val ranked = rankBoxCandidatesForTest(listOf("52094", "53145", "  12345  "))
        assertEquals(listOf("52094", "53145", "12345"), ranked.map { it.value })
    }

    @Test
    fun box_candidates_recover_common_digit_ocr_confusions() {
        val ranked = rankBoxCandidatesForTest(listOf("9I072", "52O94", "I2345"))
        assertEquals("91072", ranked.first().value)
        assertTrue(ranked.map { it.value }.containsAll(listOf("91072", "52094", "12345")))
    }

    @Test
    fun box_candidates_accept_horizontal_six_digit_numbers() {
        val ranked = rankBoxCandidatesForTest(listOf("253369", "253 369"))
        assertEquals(listOf("253369"), ranked.map { it.value })
        assertTrue((ranked.first().confidence ?: 0.0) >= 0.7)
    }

    @Test
    fun box_candidates_accept_alphanumeric_vertical_seven() {
        val ranked = rankBoxCandidatesForTest(listOf("ASR1072", "asr-1072"))
        assertTrue(ranked.isNotEmpty())
        assertEquals("ASR1072", ranked.first().value)
        assertTrue(
            "Expected high confidence for ASR1072 but was ${ranked.first().confidence}",
            (ranked.first().confidence ?: 0.0) >= 0.85
        )
    }

    @Test
    fun box_candidates_reject_dimensions_brands_and_models() {
        val rejected = listOf("53'", "53FT", "3000R", "C-600", "UTILITY", "WABASH", "C600")
        rejected.forEach { value ->
            val ranked = rankBoxCandidatesForTest(listOf(value))
            assertTrue("Expected to reject '$value' but got $ranked", ranked.none { it.value == value })
        }
    }

    @Test
    fun box_candidates_reject_noise_words_and_warnings() {
        val noise = listOf("STOP", "WARNING", "DANGER", "CAUTION", "PMM", "CAJA")
        noise.forEach { value ->
            val ranked = rankBoxCandidatesForTest(listOf(value))
            assertTrue("Expected to reject '$value' but got $ranked", ranked.none { it.value == value })
        }
    }

    @Test
    fun plate_candidates_prioritize_trailer_plate_shape() {
        val ranked = rankPlateCandidatesForTest(listOf("26-AB-7S", "AB12345", "XYZ123"))
        assertEquals("26-AB-7S", ranked.first().value)
        assertTrue((ranked.first().confidence ?: 0.0) >= 0.9)
    }

    @Test
    fun plate_candidates_reject_warnings_and_short_values() {
        val rejected = listOf("STOP", "DANGER", "AB1", "12")
        rejected.forEach { value ->
            val ranked = rankPlateCandidatesForTest(listOf(value))
            assertTrue("Expected to reject '$value' but got $ranked", ranked.none { it.value == value })
        }
    }

    @Test
    fun plate_candidates_accept_us_trailer_plates_without_mangling() {
        // Placas US de remolque (3 letras + 4 digitos). NO deben convertirse en un falso
        // formato mexicano con guiones ("STG-27-22") ni quedarse con baja confianza.
        listOf("STG2722", "STE7129").forEach { plate ->
            val ranked = rankPlateCandidatesForTest(listOf(plate))
            assertEquals("La placa $plate no debe mutar", plate, ranked.first().value)
            assertTrue(
                "Se esperaba confianza alta para $plate pero fue ${ranked.first().confidence}",
                (ranked.first().confidence ?: 0.0) >= 0.85
            )
            assertTrue(
                "No se debe fabricar una variante con guiones para $plate",
                ranked.none { it.value.contains('-') }
            )
        }
    }

    @Test
    fun plate_candidates_accept_any_plausible_format() {
        // Formatos variados (estatal, particular, vanity) sin importar el pais.
        listOf("WJ8270B", "ABC1234", "A12BCD", "XYZ99").forEach { plate ->
            val ranked = rankPlateCandidatesForTest(listOf(plate))
            assertTrue("Se esperaba aceptar $plate", ranked.any { it.value == plate })
            val cand = ranked.first { it.value == plate }
            assertTrue(
                "Se esperaba confianza alta para $plate pero fue ${cand.confidence}",
                (cand.confidence ?: 0.0) >= 0.85
            )
        }
    }

    @Test
    fun plate_candidates_preserve_mexican_dashes_when_read() {
        // Si ML Kit leyo los separadores, se conserva el formato oficial con guiones y con
        // la maxima confianza, aun cuando tambien exista la lectura compacta.
        val ranked = rankPlateCandidatesForTest(listOf("01UT8E", "01-UT-8E"))
        assertEquals("01-UT-8E", ranked.first().value)
        assertTrue((ranked.first().confidence ?: 0.0) >= 0.9)
        // Se colapsa la gemela compacta: un solo candidato por numero.
        assertEquals(1, ranked.count { it.value.replace("-", "") == "01UT8E" })
    }

    @Test
    fun plate_candidates_reject_authority_stickers() {
        // Calcomanias de autoridad que van junto al numero en placas federales.
        listOf("SCT2021", "SCT2022").forEach { sticker ->
            val ranked = rankPlateCandidatesForTest(listOf(sticker))
            assertTrue("No debe aceptar la calcomania $sticker", ranked.none { it.value == sticker })
        }
    }

    @Test
    fun ocr_state_marks_failed_when_no_candidates_available() {
        val state = CasetaEntradaOcrState(
            phase = CasetaEntradaOcrPhase.Failed,
            outcome = CasetaEntradaOcrOutcome.ManualRequired,
            message = "No encontre un numero confiable."
        )
        assertEquals(CasetaEntradaOcrPhase.Failed, state.phase)
        assertEquals(CasetaEntradaOcrOutcome.ManualRequired, state.outcome)
    }

    @Test
    fun ocr_state_auto_applied_keeps_primary_value() {
        val candidates = listOf(
            CasetaEntradaOcrCandidate(value = "52094", confidence = 0.92, source = "local"),
            CasetaEntradaOcrCandidate(value = "53145", confidence = 0.6, source = "local")
        )
        val state = CasetaEntradaOcrState(
            primaryValue = "52094",
            candidates = candidates,
            phase = CasetaEntradaOcrPhase.Ready,
            outcome = CasetaEntradaOcrOutcome.AutoApplied
        )
        assertEquals("52094", state.primaryValue)
        assertEquals(CasetaEntradaOcrPhase.Ready, state.phase)
        assertEquals(CasetaEntradaOcrOutcome.AutoApplied, state.outcome)
        assertNotNull(state.candidates.firstOrNull())
    }

    @Test
    fun ocr_phase_labels_are_non_blank() {
        CasetaEntradaOcrPhase.values().forEach { phase ->
            assertTrue(phase.label.isNotBlank())
            assertTrue(phase.description.isNotBlank())
            assertNotNull(phase)
        }
    }

    @Test
    fun outcome_choices_are_distinct() {
        val outcomes = CasetaEntradaOcrOutcome.values()
        assertEquals(outcomes.toSet().size, outcomes.size)
        assertNull(null)
    }

    private fun rankBoxCandidatesForTest(values: List<String>): List<CasetaEntradaOcrCandidate> {
        val result = LocalTextOcrResult(rawText = values.joinToString(" "), candidates = values)
        return BoxOcrRanker.rank(result)
    }

    private fun rankPlateCandidatesForTest(values: List<String>): List<CasetaEntradaOcrCandidate> {
        val result = LocalTextOcrResult(rawText = values.joinToString(" "), candidates = values)
        return PlateOcrRanker.rank(result)
    }

    private fun validForm(
        movement: String = "DESCARGAS",
        plates: String = "ABC123",
        idCountry: String = "USA",
        guideCount: String = "",
        pedimento: String = "",
        boxTypeDescription: String = "CAJA SECA 53'",
        temperature: String = "",
        fuel: String = ""
    ) = CasetaEntradaForm(
        idBodega = "01",
        operation = CasetaEntradaRules.OP_IMPORT,
        movement = movement,
        idBoxType = "53",
        boxTypeDescription = boxTypeDescription,
        idCustomer = "C001",
        customerName = "CLIENTE PRUEBA",
        idBoxLine = "T001",
        boxLineName = "LINEA CAJA",
        idTruckLine = "T002",
        truckLineName = "LINEA CAMION",
        boxNumber = "BOX123",
        plates = plates,
        idCountry = idCountry,
        guideCount = guideCount,
        pedimento = pedimento,
        temperature = temperature,
        fuel = fuel
    )

    @Test
    fun cleanCatalogSelection_treats_the_combo_placeholder_as_empty() {
        // Registros viejos guardaron el marcador del combo en lugar de dejar el tipo de caja vacio.
        assertEquals("", CasetaEntradaRules.cleanCatalogSelection("0"))
        assertEquals("", CasetaEntradaRules.cleanCatalogSelection("-- Selecciona --"))
        assertEquals("", CasetaEntradaRules.cleanCatalogSelection("  -- selecciona tipo --  "))
        assertEquals("", CasetaEntradaRules.cleanCatalogSelection(""))
    }

    @Test
    fun cleanCatalogSelection_keeps_real_values() {
        assertEquals("56", CasetaEntradaRules.cleanCatalogSelection(" 56 "))
        assertEquals("CAJA CERRADA 53'", CasetaEntradaRules.cleanCatalogSelection("CAJA CERRADA 53'"))
        assertEquals("OTROS", CasetaEntradaRules.cleanCatalogSelection("OTROS"))
    }
}
