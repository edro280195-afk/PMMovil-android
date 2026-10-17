package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelBarcodeInterpreterTest {
    // Claves reales de la entrada 01C263606600000 (solo las que usan las pruebas).
    private val claves = listOf("75183644", "75875114", "49205490", "75095515")
    private val paises = setOf("TAIWAN", "VIETNAM", "THAILAND")
    private fun interpretar(codigos: List<String>, catalogo: List<String> = claves) =
        LabelBarcodeInterpreter.interpretar(codigos, catalogo, esPais = { it.uppercase() in paises })

    @Test
    fun `la bolsa completa entrega parte y origen y deja fuera lo que no puede asegurar`() {
        // Los ocho códigos reales de la bolsa de la partida 3056061.
        val bolsa = listOf(
            "4505747759", "75183644", "200", "1Y-00000050337428", "667", "2Q26", "TAIWAN", "5003814158"
        )

        val resultado = interpretar(bolsa)

        val parte = resultado.campos.single { it.campo == Campo.NUMERO_PARTE }
        assertEquals("75183644", parte.valor)
        assertEquals(Origen.CODIGO_PLANO, parte.origen)
        assertEquals("TAIWAN", resultado.campos.single { it.campo == Campo.PAIS_ORIGEN }.valor)
        // El lote y la cantidad no se asignan: 200, 667 y los dos números de diez dígitos son ambiguos.
        assertTrue(resultado.campos.none { it.campo == Campo.LOTE || it.campo == Campo.CANTIDAD })
        // Y el texto no puede decidirlos por su cuenta en esta etiqueta.
        assertEquals(setOf(Campo.NUMERO_PARTE, Campo.LOTE, Campo.CANTIDAD, Campo.PAIS_ORIGEN), resultado.camposSinOcr)
    }

    @Test
    fun `con la geometria del texto la bolsa entrega tambien lote y cantidad`() {
        val resultado = LabelBarcodeInterpreter.interpretar(
            BolsaFreudenbergFixture.codigos, claves, { it.uppercase() in paises }, BolsaFreudenbergFixture.lineas
        )

        val lote = resultado.campos.single { it.campo == Campo.LOTE }
        assertEquals("5003814158", lote.valor)
        assertEquals(Origen.CODIGO_PLANO, lote.origen)
        assertEquals("Batch", lote.ancla)
        val cantidad = resultado.campos.single { it.campo == Campo.CANTIDAD }
        assertEquals("200", cantidad.valor)
        assertEquals("QTY", cantidad.ancla)
        // Y lo que ya entregaba sigue igual: el PO y el proveedor no se cuelan como lote ni cantidad.
        assertEquals("75183644", resultado.campos.single { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("TAIWAN", resultado.campos.single { it.campo == Campo.PAIS_ORIGEN }.valor)
    }

    @Test
    fun `girada la bolsa se entiende igual`() {
        listOf(90.0, 180.0, 270.0).forEach { grados ->
            val resultado = LabelBarcodeInterpreter.interpretar(
                BolsaFreudenbergFixture.codigos, claves, { it.uppercase() in paises },
                BolsaFreudenbergFixture.girada(BolsaFreudenbergFixture.lineas, grados)
            )

            assertEquals("lote a $grados°", "5003814158", resultado.campos.single { it.campo == Campo.LOTE }.valor)
            assertEquals("cantidad a $grados°", "200", resultado.campos.single { it.campo == Campo.CANTIDAD }.valor)
        }
    }

    @Test
    fun `una cantidad que no es un numero no se toma`() {
        val lineas = listOf(
            BolsaFreudenbergFixture.linea("QTY", 328f, 535f, 328f, 573f, 312f, 573f, 312f, 535f),
            BolsaFreudenbergFixture.linea("12AB", 396f, 535f, 395f, 578f, 373f, 577f, 374f, 534f)
        )

        val resultado = LabelBarcodeInterpreter.interpretar(listOf("75183644", "12AB", "X1", "Y2"), claves, lineas = lineas)

        assertTrue(resultado.campos.none { it.campo == Campo.CANTIDAD })
    }

    @Test
    fun `el lote no puede ser la propia clave de la partida`() {
        val lineas = listOf(
            BolsaFreudenbergFixture.linea("Batch", 341f, 1035f, 341f, 1094f, 324f, 1094f, 324f, 1035f),
            BolsaFreudenbergFixture.linea("75183644", 404f, 940f, 409f, 1081f, 384f, 1082f, 379f, 941f)
        )

        val resultado = LabelBarcodeInterpreter.interpretar(listOf("75183644", "A1", "B2", "C3"), claves, lineas = lineas)

        assertTrue(resultado.campos.none { it.campo == Campo.LOTE })
    }

    @Test
    fun `con seis de los ocho codigos de la foto reducida pasa lo mismo`() {
        val reducida = listOf("4505747759", "667", "2Q26", "75183644", "5003814158", "TAIWAN")

        val resultado = interpretar(reducida)

        assertEquals("75183644", resultado.campos.single { it.campo == Campo.NUMERO_PARTE }.valor)
        assertTrue(Campo.LOTE in resultado.camposSinOcr)
    }

    @Test
    fun `la clave con ceros a la izquierda se reconoce contra el catalogo sin ellos`() {
        val resultado = interpretar(listOf("0075875114", "A1", "B2", "C3"))

        assertEquals("0075875114", resultado.campos.single { it.campo == Campo.NUMERO_PARTE }.valor)
    }

    @Test
    fun `sin ningun codigo igual a una clave no se asigna nada`() {
        val resultado = interpretar(listOf("4505747759", "667", "2Q26", "5003814158", "TAIWAN"))

        assertTrue(resultado.campos.isEmpty())
        assertTrue(resultado.camposSinOcr.isEmpty())
    }

    @Test
    fun `los codigos estructurados y el codigo de la caja no son codigos por campo`() {
        val caja = listOf("S279239665", "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003")

        val resultado = interpretar(caja, catalogo = listOf("49263333"))

        // El PDF417 lo lee el extractor; «S279239665» no es una clave.
        assertTrue(resultado.campos.isEmpty())
        assertTrue(resultado.camposSinOcr.isEmpty())
    }

    @Test
    fun `con pocos codigos el texto sigue pudiendo opinar`() {
        // Una etiqueta común con un solo código de barras que es la clave: no se silencia el OCR.
        val resultado = interpretar(listOf("75183644", "TAIWAN"))

        assertEquals("75183644", resultado.campos.single { it.campo == Campo.NUMERO_PARTE }.valor)
        assertTrue(resultado.camposSinOcr.isEmpty())
        assertTrue("el país solo se toma de una etiqueta de códigos por campo", resultado.campos.none { it.campo == Campo.PAIS_ORIGEN })
    }

    @Test
    fun `sin claves de la entrada no hay con que comparar`() {
        assertEquals(LabelBarcodeInterpreter.Interpretacion.VACIA, interpretar(listOf("75183644", "A", "B", "C", "D"), catalogo = emptyList()))
    }

    @Test
    fun `un pais que no esta en el catalogo no se toma`() {
        val resultado = interpretar(listOf("75183644", "ATLANTIS", "4505747759", "5003814158"))

        assertTrue(resultado.campos.none { it.campo == Campo.PAIS_ORIGEN })
        assertFalse(Campo.PAIS_ORIGEN in resultado.camposSinOcr)
    }

    @Test
    fun `el mismo codigo repetido cuenta una vez`() {
        val resultado = interpretar(listOf("75183644", "75183644", "TAIWAN", "5003814158", "200"))

        assertEquals(1, resultado.campos.count { it.campo == Campo.NUMERO_PARTE })
    }
}
