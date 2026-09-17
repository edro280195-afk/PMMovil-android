package com.example.pmmovil.data.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraPrinterSettingsValidationTest {

    @Test
    fun `acepta el perfil seguro de una ZT411`() {
        val validation = ZebraPrinterSettingsValidation.validate(
            ZebraPrinterPrintSettings(
                darkness = 18f,
                speedIps = 4f,
                printMethod = "thermal_transfer",
                mediaType = "gap_notch",
                labelTop = 0,
                leftPosition = 0
            )
        )

        assertNull(validation)
    }

    @Test
    fun `normaliza las respuestas habituales de la Zebra`() {
        assertEquals("thermal_transfer", ZebraPrinterSettingsValidation.normalizePrintMethod("THERMAL TRANSFER"))
        assertEquals("direct_thermal", ZebraPrinterSettingsValidation.normalizePrintMethod("direct thermal"))
        assertEquals("gap_notch", ZebraPrinterSettingsValidation.normalizeMediaType("gap/notch"))
    }

    @Test
    fun `rechaza compensaciones peligrosas antes de enviarlas`() {
        val validation = ZebraPrinterSettingsValidation.validate(
            ZebraPrinterPrintSettings(
                darkness = 18f,
                speedIps = 4f,
                printMethod = "thermal_transfer",
                mediaType = "gap_notch",
                labelTop = 121,
                leftPosition = 0
            )
        )

        assertTrue(validation!!.contains("vertical"))
    }
}
