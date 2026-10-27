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
    fun `su partida va en kilos y se cuenta en kilos cuando la hoja imprime KG`() {
        // Los kilos se cuentan desde el 27-oct-2026 (ver EtiquetaQuimicaKilosTest). Por piezas siguen sin contarse.
        assertNull(CountUnits.piecesPerUnit("KG"))
        assertEquals(74.5, PackageTally.expectedQuantity(partidaEnKilos)!!, 0.0)

        val lectura = PackageTally.read(
            camposDe(quimica), listOf(quimica),
            "CHEMICAL COMPOUND ART. NO (30S) 0049379668 QTY/Menge (Q) UNIT/ME 73.500 KG Batch No. 2612012010 Made in US"
        ) as PackageTally.Reading.Ready
        val resultado = PackageTally.process(listOf(partidaEnKilos), PackageTally.Ledger(), lectura.label)

        assertTrue("se esperaba contar la caja, salió $resultado", resultado is PackageTally.Result.Added)
        assertEquals(73.5, (resultado as PackageTally.Result.Added).totalAfter, 0.0)
        assertEquals(PackageTally.State.PARTIAL, resultado.state)
    }

    @Test
    fun `con el texto de otra etiqueta que tambien dice piezas no se cuenta`() {
        // El texto genérico de las otras pruebas nombra PC y KG a la vez: la unidad de la Q no es clara.
        val lectura = PackageTally.read(camposDe(quimica), listOf(quimica), "QTY 200 PC  Q 73.500 KG  Part 49379668") as PackageTally.Reading.Ready
        val resultado = PackageTally.process(listOf(partidaEnKilos), PackageTally.Ledger(), lectura.label)

        assertTrue("salió $resultado", resultado is PackageTally.Result.UnitNotConfirmed)
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
