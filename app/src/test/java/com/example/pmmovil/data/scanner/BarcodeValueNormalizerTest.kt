package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeValueNormalizerTest {
    @Test
    fun `etiqueta conserva separador GS y prefijo AIM`() {
        val code = "]C110Lot12\u001D21Serial34"
        assertEquals(code, BarcodeValueNormalizer.normalizeLabel("\r\n$code\r\n"))
    }

    @Test
    fun `separador al inicio o final de etiqueta tampoco se borra`() {
        val code = "\u001D10LOT123\u001D"
        assertEquals(code, BarcodeValueNormalizer.normalizeLabel(code))
    }

    @Test
    fun `normalizacion historica sigue quitando controles para busquedas simples`() {
        assertEquals("ABC123", BarcodeValueNormalizer.normalize(" ABC\u001D123\r\n"))
    }

    @Test
    fun `etiqueta vacia y nula no genera datos`() {
        assertEquals("", BarcodeValueNormalizer.normalizeLabel(null))
        assertEquals("", BarcodeValueNormalizer.normalizeLabel("\r\n "))
    }
}
