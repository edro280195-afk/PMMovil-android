package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.scanner.StructuredLabelBarcodeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Lo único que la revisión necesita de una etiqueta, en palabras de Eduardo (19-oct): **número de parte,
 * cantidad y peso**, para capturarlos en la partida como hoy se capturan a mano.
 *
 * Esta prueba mide, etiqueta real por etiqueta real, cuánto de esos tres datos sale hoy.
 *
 * Los cuatro formatos conocidos de Freudenberg:
 *  1. Compuesto químico (cliente **CL7480**): un PDF417 `+30S…+Q…+H…+2D…+2Y…+P`. Cantidad en **KG**.
 *  2. Caja de México (CL8292): el mismo PDF417 pero con `2X` en vez de `2Y`, más un Code 39 `S…`.
 *  3. Bolsa de Taiwán (CL8292): ocho Code 128 sin nombre de campo.
 *  4. Envío VDA de NOK (CL8292): ocho Code 39, cada uno con su identificador MH10 adentro.
 */
class FreudenbergTresDatosTest {

    /** La etiqueta de compuesto químico que mandó Eduardo, tal cual viene impresa arriba del PDF417. */
    private val quimica = "+30S0049379668+Q0073.500+H2612012010+2D10102027+2Y565310102026+P"

    /** La caja de México, de la misma familia pero con el contrato en `2X`. */
    private val caja = "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003"

    @Test
    fun `la etiqueta quimica entrega parte cantidad lote y caducidad`() {
        val leido = StructuredLabelBarcodeParser.parse(quimica)!!
        assertEquals("0049379668", leido.articulo)      // ART. NO. (30S); en la base es 49379668, sin ceros
        assertEquals(73.5, leido.cantidad!!, 0.0001)    // 73.500 KG
        assertEquals("0073.500", leido.rawQuantity)
        assertEquals("2612012010", leido.lote)          // Batch No.
        assertEquals("10102027", leido.fecha)           // Best Before 10/10/2027
    }

    @Test
    fun `el contrato en 2Y se pierde porque el diccionario solo conoce 2X`() {
        val leido = StructuredLabelBarcodeParser.parse(quimica)!!
        // La caja sí lo entrega, la química no: mismo dato, prefijo distinto.
        assertEquals("497608092026", StructuredLabelBarcodeParser.parse(caja)!!.contrato)
        assertNull(leido.contrato)
    }

    @Test
    fun `la cantidad de la etiqueta quimica es tambien el peso, pero en kilos`() {
        val leido = StructuredLabelBarcodeParser.parse(quimica)!!
        // Medido en la base para la clave 49379668 (cliente CL7480): la factura va en KG y `npeso` guarda
        // LIBRAS — 74.500 KG ↔ 164.242 lb, factor 2.20462. La etiqueta da kilos, así que el peso de la
        // partida sale de multiplicar, no de copiar.
        val kilos = leido.cantidad!!
        assertEquals(162.04, kilos * 2.20462, 0.01)
    }

    @Test
    fun `el lector ya sabe nombrar los dos pesos`() {
        // Antes no había dónde poner los «NET WT KG 8» de la etiqueta VDA ni los kilos de la química,
        // aunque el peso es el campo que el revisador llena SIEMPRE, en todos los clientes.
        val campos = LabelFieldExtractor.Campo.entries.map { it.name }
        assertEquals(listOf("PESO_NETO", "PESO_BRUTO"), campos.filter { "PESO" in it })
    }

    @Test
    fun `cada peso se lee en su campo con su unidad`() {
        val leido = LabelFieldExtractor.extraer("NET WT KG 8\nGROSS WT KG 9")
        val neto = leido.single { it.campo == LabelFieldExtractor.Campo.PESO_NETO }
        val bruto = leido.single { it.campo == LabelFieldExtractor.Campo.PESO_BRUTO }
        assertEquals("8", neto.valor)
        assertEquals("KG", neto.unidad)
        assertEquals("9", bruto.valor)
        assertEquals("KG", bruto.unidad)
    }

    @Test
    fun `la unidad se toma del valor cuando no esta en el rotulo`() {
        val leido = LabelFieldExtractor.extraer("PESO NETO 12,5 KG")
        val peso = leido.single { it.campo == LabelFieldExtractor.Campo.PESO_NETO }
        assertEquals("12.5", peso.valor)
        assertEquals("KG", peso.unidad)
    }
}
