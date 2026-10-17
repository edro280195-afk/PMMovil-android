package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StructuredLabelBarcodeParserTest {

    @Test
    fun `parses a real Freudenberg chemical compound label`() {
        // Etiqueta 1 de la primera foto (DTM 75 HNBR G1103, 49.200 KG).
        val raw = "+30S0049106577+Q0049.200+H2613004019+2D16102026+2X565321082026+P"

        val parsed = StructuredLabelBarcodeParser.parse(raw)

        assertEquals("0049106577", parsed?.articulo)
        assertEquals(49.200, parsed?.cantidad ?: -1.0, 0.0001)
        assertEquals("2613004019", parsed?.lote)
    }

    @Test
    fun `parses a second real label with a different article and quantity`() {
        // Etiqueta 1 de la segunda foto (DTM 50 CR 304106, 77.600 KG).
        val raw = "+30S0049118845+Q0077.600+H2612010049+2D28082027+2X565328082026+P"

        val parsed = StructuredLabelBarcodeParser.parse(raw)

        assertEquals("0049118845", parsed?.articulo)
        assertEquals(77.600, parsed?.cantidad ?: -1.0, 0.0001)
        assertEquals("2612010049", parsed?.lote)
    }

    @Test
    fun `parses the real Freudenberg carton label with all six identifiers`() {
        // Caja de COVER GASKETS: PDF417 de la foto del 16-oct (600 piezas, fabricada en México).
        val raw = "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003"

        val parsed = StructuredLabelBarcodeParser.parse(raw)

        assertEquals("0049263333", parsed?.articulo)
        assertEquals(600.0, parsed?.cantidad ?: -1.0, 0.0001)
        assertEquals("00000600", parsed?.rawQuantity)
        assertEquals("0031704192", parsed?.lote)
        assertEquals("092036", parsed?.fecha)
        assertEquals("497608092026", parsed?.contrato)
        assertEquals("049879/003", parsed?.parteCliente)
    }

    @Test
    fun `parses the real Freudenberg O-ring bag label even when the photo cuts the customer part`() {
        // Bolsa de O-rings (partida línea 33): el borde de la bolsa cortó la parte del cliente.
        val raw = "+30S0075875114+Q000210+H0426090326+2D082036+2X566405082026+P0000000000075875"

        val parsed = StructuredLabelBarcodeParser.parse(raw)

        assertEquals("0075875114", parsed?.articulo)
        assertEquals(210.0, parsed?.cantidad ?: -1.0, 0.0001)
        assertEquals("0426090326", parsed?.lote)
        assertEquals("082036", parsed?.fecha)
        assertEquals("566405082026", parsed?.contrato)
    }

    @Test
    fun `an empty customer part leaves it unset`() {
        val parsed = StructuredLabelBarcodeParser.parse("+30S0049106577+Q0049.200+H2613004019+2D16102026+2X565321082026+P")

        assertEquals(null, parsed?.parteCliente)
        assertEquals("16102026", parsed?.fecha)
    }

    @Test
    fun `conflicting dates contracts or customer parts reject the label`() {
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+2D092036+2D102036"))
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+2X111+2X222"))
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+PA1+PB2"))
    }

    @Test
    fun `plain simple barcodes are left alone (no leading plus, or a single segment)`() {
        assertNull(StructuredLabelBarcodeParser.parse("7501234567890"))
        assertNull(StructuredLabelBarcodeParser.parse("0049106577DESCRIPCION LIBRE"))
        assertNull(StructuredLabelBarcodeParser.parse("+SOLOUNSEGMENTO"))
    }

    @Test
    fun `a structured code without a recognizable article prefix is ignored`() {
        // Trae segmentos, pero ninguno con un prefijo de artículo conocido.
        assertNull(StructuredLabelBarcodeParser.parse("+Q0049.200+H2613004019+P"))
    }

    @Test
    fun `is tolerant to lowercase prefixes`() {
        val parsed = StructuredLabelBarcodeParser.parse("+30s0049106577+q0049.200+p")

        assertEquals("0049106577", parsed?.articulo)
        assertEquals(49.200, parsed?.cantidad ?: -1.0, 0.0001)
    }

    @Test
    fun `preserves case and original quantity inside values`() {
        val parsed = StructuredLabelBarcodeParser.parse("+30s00Ab12+q0049.200+hLot-b12+p")
        assertEquals("00Ab12", parsed?.articulo)
        assertEquals("Lot-b12", parsed?.lote)
        assertEquals("0049.200", parsed?.rawQuantity)
    }

    @Test
    fun `rejects conflicting values inside a single structured label`() {
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+HLOT123+HLOT999"))
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+30S999+Q12"))
        assertNull(StructuredLabelBarcodeParser.parse("+30S123+Q12+Q99"))
    }

    @Test
    fun `rejects invalid quantities without replacing them with zero`() {
        listOf("NaN", "Infinity", "-12", "abc").forEach { quantity ->
            assertNull(StructuredLabelBarcodeParser.parse("+30S123+Q$quantity+HLOT123"))
        }
    }
}
