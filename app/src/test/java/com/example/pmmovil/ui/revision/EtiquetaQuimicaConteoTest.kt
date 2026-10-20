package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.scanner.LabelDataIdentifiers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué pasa hoy al contar la **etiqueta de compuesto químico** de Freudenberg, la del cliente CL7480:
 * `+30S0049379668+Q0073.500+H2612012010+2D10102027+2Y565310102026+P`.
 *
 * Su partida va en **KG**, no en piezas, y su cantidad `Q` son kilos. Esta prueba fija lo que el
 * conteo hace con ella, que no es lo mismo que con la etiqueta de piezas.
 */
class EtiquetaQuimicaConteoTest {

    private val quimica = "+30S0049379668+Q0073.500+H2612012010+2D10102027+2Y565310102026+P"

    /** La partida 2905303 real: 74.500 KG, npeso 164.242 lb. */
    private val partidaEnKilos = PartidaRevisionDto(
        IdRevdet = 2905303, Cnofact = "2656043097", CnoParte = "49379668", cDescripcion = null,
        Marca = null, Modelo = null, NumSer = null, NumLote = null,
        nCantFactura = 74.5, nCantOut = 74.5, cPaisOrigen = "USA",
        CptCveTmateria = null, CptDescTmateria = null, Revisada = false, Obsrevpartida = null,
        Mnotapartida = null, nPeso = 164.242, Cclienteid = "CL7480", UnidadFactura = "KG",
        ParteCte = null
    )

    private fun camposDe(codigo: String): List<CampoLeido> {
        val leido = LabelDataIdentifiers.leer(listOf(codigo))
        return buildList {
            leido.clave?.let {
                add(CampoLeido(Campo.NUMERO_PARTE, it, Origen.STRUCTURED_BARCODE, "30S", 99))
            }
            leido[LabelDataIdentifiers.Dato.CANTIDAD]?.let {
                add(CampoLeido(Campo.CANTIDAD, it, Origen.STRUCTURED_BARCODE, "Q", 99))
            }
        }
    }

    @Test
    fun `la etiqueta quimica se lee entera`() {
        val campos = camposDe(quimica)
        assertEquals("0049379668", campos.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("0073.500", campos.first { it.campo == Campo.CANTIDAD }.valor)
    }

    @Test
    fun `pero el conteo la rechaza porque su partida va en kilos`() {
        // CountUnits solo sabe convertir a piezas PZA, CNT y MLL. Una partida en KG no tiene factor,
        // así que el motor devuelve UnsupportedUnit y no cuenta nada.
        assertNull(CountUnits.piecesPerUnit("KG"))
        assertNull(PackageTally.expectedPieces(partidaEnKilos))

        val lectura = PackageTally.read(camposDe(quimica), listOf(quimica)) as PackageTally.Reading.Ready
        val resultado = PackageTally.process(listOf(partidaEnKilos), PackageTally.Ledger(), lectura.label)
        assertTrue(
            "se esperaba que el conteo no soportara los kilos, salió $resultado",
            resultado is PackageTally.Result.UnsupportedUnit
        )
        assertEquals("KG", (resultado as PackageTally.Result.UnsupportedUnit).unit)
    }

    @Test
    fun `y su etiqueta tampoco trae un peso rotulado que sumar`() {
        // En esta etiqueta el peso no viene como «NET WT»: la cantidad Q ES los kilos. Así que el
        // lector de campos no encuentra ningún peso y el conteo de peso tampoco aplica.
        val campos = LabelFieldExtractor.extraer(
            "ART. NO. (30S) 0049379668\nQTY / Menge (Q) 73.500\nKG\nBatch No. 2612012010"
        )
        assertNull(campos.firstOrNull { it.campo == Campo.PESO_NETO }?.valor)
        assertNull(campos.firstOrNull { it.campo == Campo.PESO_BRUTO }?.valor)
    }
}
