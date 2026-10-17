package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareBarcodePayloadExtractorTest {

    @Test
    fun `Intent Honeywell y camara entregan la misma cadena normalizada`() {
        val qr = "operador;claveQr\r\n"

        val hardware = HardwareBarcodePayloadExtractor.extract(mapOf("data" to qr))

        assertEquals(BarcodeValueNormalizer.normalizeScan(qr), hardware?.value)
        assertEquals("data", hardware?.extraKey)
        assertEquals("operador;claveQr", hardware?.value)
    }

    @Test
    fun `Intent Honeywell usa dataBytes cuando no hay data como texto`() {
        val bytes = "ENTRADA123\r\n".toByteArray(Charsets.UTF_8)

        val extracted = HardwareBarcodePayloadExtractor.extract(mapOf("dataBytes" to bytes))

        assertEquals("ENTRADA123", extracted?.value)
        assertEquals("dataBytes", extracted?.extraKey)
    }

    @Test
    fun `el extra de codigo tiene prioridad sobre metadatos de simbologia`() {
        val extracted = HardwareBarcodePayloadExtractor.extract(
            mapOf(
                "aimId" to "]Q3",
                "codeId" to "QR",
                "data" to "CAJA-123"
            )
        )

        assertEquals("CAJA-123", extracted?.value)
        assertEquals("data", extracted?.extraKey)
    }

    @Test
    fun `no convierte arreglos de bytes desconocidos a su referencia de objeto`() {
        val extracted = HardwareBarcodePayloadExtractor.extract(
            mapOf("vendorPayload" to byteArrayOf(65, 66, 67))
        )

        assertNull(extracted)
    }
}
