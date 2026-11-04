package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.scanner.LabelDataIdentifiers
import com.example.pmmovil.ui.revision.PackageTally.Ledger
import com.example.pmmovil.ui.revision.PackageTally.Result
import com.example.pmmovil.ui.revision.PackageTally.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El conteo en **kilos** con las etiquetas reales de las fotos de bodega (27-oct-2026): cinco cajas de compuesto químico
 * Freudenberg, parte 0049118845, de la entrada 01C263591850000. Sus kilos suman exactamente los 391.9 KG de la factura:
 * 77.6 + 78.4 + 78.7 + 79.2 + 78.0.
 */
class EtiquetaQuimicaKilosTest {

    /** La partida real: 391.900 KG, `npeso` 863.977 lb (= 391.9 kg) y una cantidad recibida de 2 que alguien tecleó. */
    private val partida = PartidaRevisionDto(
        IdRevdet = 3000001, Cnofact = "2656043097", CnoParte = "49118845", cDescripcion = null,
        Marca = null, Modelo = null, NumSer = null, NumLote = null,
        nCantFactura = 391.9, nCantOut = 2.0, cPaisOrigen = "USA",
        CptCveTmateria = null, CptDescTmateria = null, Revisada = false, Obsrevpartida = null,
        Mnotapartida = null, nPeso = 863.977, Cclienteid = "CL7480", UnidadFactura = "KG",
        ParteCte = null
    )

    /** Lo que el PDF417 de cada caja trae, en el orden en que salen en la foto: kilos y lote de cada una. */
    private val cajas = listOf(
        "0077.600" to "2612010049",
        "0078.400" to "2612010048",
        "0078.700" to "2612010047",
        "0079.200" to "2612010051",
        "0078.000" to "2612010050"
    )

    private fun pdf417(q: String, lote: String) = "+30S0049118845+Q$q+H$lote+2D28082027+2X565328082026+P"

    /** Lo que la hoja de la caja imprime: la unidad va pegada a la cantidad («UNIT/ME KG»). */
    private fun impreso(q: String, lote: String, unidades: String = "KG") =
        "FREUDENBERG CHEMICAL COMPOUND ART. NO (30S) 0049118845 DTM 50 CR 304106 000 000 BLACK C " +
            "QTY/Menge (Q) UNIT/ME ${q.trimStart('0')} $unidades Contr. No. 565328082026 Batch No. $lote " +
            "Batch Prod. Date 08/28/2026 Best Before 08/28/2027 Compound/Werkstoff 50 CR 304106 CUST. PART 0049118845 Made in US"

    private fun lectura(q: String, lote: String, texto: String = impreso(q, lote)): PackageTally.Reading {
        val codigo = pdf417(q, lote)
        val leido = LabelDataIdentifiers.leer(listOf(codigo))
        val campos = buildList {
            leido.clave?.let { add(CampoLeido(Campo.NUMERO_PARTE, it, Origen.STRUCTURED_BARCODE, "30S", 99)) }
            leido[LabelDataIdentifiers.Dato.CANTIDAD]?.let { add(CampoLeido(Campo.CANTIDAD, it, Origen.STRUCTURED_BARCODE, "Q", 99)) }
            leido[LabelDataIdentifiers.Dato.LOTE]?.let { add(CampoLeido(Campo.LOTE, it, Origen.STRUCTURED_BARCODE, "H", 99)) }
        }
        return PackageTally.read(campos, listOf(codigo), texto)
    }

    private fun etiqueta(q: String, lote: String, texto: String = impreso(q, lote)) =
        (lectura(q, lote, texto) as PackageTally.Reading.Ready).label

    @Test
    fun `la etiqueta de una caja se lee con su clave, sus kilos y la unidad impresa`() {
        val label = etiqueta("0077.600", "2612010049")

        assertEquals("0049118845", label.key)
        assertEquals(77.6, label.pieces!!, 0.0)
        assertEquals(setOf(CountUnits.PrintedUnit.KILOS), label.printedUnits)
    }

    @Test
    fun `las cinco cajas suman exactamente los 391 punto 9 kilos de la factura`() {
        var libro = Ledger()
        val pasos = cajas.map { (q, lote) ->
            val r = PackageTally.process(listOf(partida), libro, etiqueta(q, lote)) as Result.Added
            libro = libro.plus(r.counted)
            r
        }

        assertEquals(listOf(77.6, 156.0, 234.7, 313.9, 391.9), pasos.map { it.totalAfter })
        assertEquals(
            listOf(State.PARTIAL, State.PARTIAL, State.PARTIAL, State.PARTIAL, State.COMPLETE),
            pasos.map { it.state }
        )
        assertTrue(pasos.all { it.dimension == CountUnits.Dimension.KILOS })
        assertEquals(391.9, pasos.last().expected, 0.0)
    }

    @Test
    fun `el primer empaque reemplaza el 2 que alguien tecleo y deshacerlo lo restituye`() {
        val primera = PackageTally.process(listOf(partida), Ledger(), etiqueta("0077.600", "2612010049")) as Result.Added

        assertEquals(77.6, primera.totalAfter, 0.0)
        assertEquals(2.0, primera.previousReceived!!, 0.0)
        val sinEl = Ledger().plus(primera.counted).minus(primera.counted)
        assertEquals(2.0, PackageTally.receivedAfterUndo(sinEl, primera.counted, primera.previousReceived), 0.0)
    }

    @Test
    fun `leer otra vez la misma caja suma de mas y avisa, y la partida queda pasada`() {
        // La etiqueta de caja no trae identificador de empaque: se cuenta cada lectura y se avisa si es idéntica a la
        // anterior. El lote es distinto en cada caja, así que una relectura aparece como repetida.
        var libro = Ledger()
        cajas.forEach { (q, lote) ->
            libro = libro.plus((PackageTally.process(listOf(partida), libro, etiqueta(q, lote)) as Result.Added).counted)
        }

        val otra = PackageTally.process(listOf(partida), libro, etiqueta("0078.000", "2612010050")) as Result.Added

        assertTrue(otra.sameAsPrevious)
        assertEquals(State.OVER, otra.state)
        assertEquals(469.9, otra.totalAfter, 1e-9)
    }

    @Test
    fun `una etiqueta que ademas dice piezas no se cuenta en una partida de kilos`() {
        val texto = impreso("0077.600", "2612010049", unidades = "KG") + " QTY 200 PC"

        val resultado = PackageTally.process(listOf(partida), Ledger(), etiqueta("0077.600", "2612010049", texto))

        assertTrue(resultado is Result.UnitNotConfirmed)
        assertEquals(
            setOf(CountUnits.PrintedUnit.KILOS, CountUnits.PrintedUnit.PIECES),
            (resultado as Result.UnitNotConfirmed).printed
        )
    }

    @Test
    fun `una etiqueta en libras no se cuenta como kilos`() {
        val texto = impreso("0077.600", "2612010049", unidades = "LB")

        val resultado = PackageTally.process(listOf(partida), Ledger(), etiqueta("0077.600", "2612010049", texto))

        assertTrue(resultado is Result.UnitNotConfirmed)
    }

    @Test
    fun `una etiqueta sin unidad impresa no se cuenta en una partida de kilos`() {
        val texto = impreso("0077.600", "2612010049", unidades = "")

        val resultado = PackageTally.process(listOf(partida), Ledger(), etiqueta("0077.600", "2612010049", texto))

        assertEquals(Result.UnitNotConfirmed(partida, emptySet()), resultado)
    }

    @Test
    fun `una caja que sola trae mas kilos que toda la partida es una lectura mala`() {
        // Q0782.000 es diez veces lo que dice la hoja de una caja de 78.2: el decodificador agregó un cero.
        val resultado = PackageTally.process(listOf(partida), Ledger(), etiqueta("0782.000", "2612010049"))

        assertTrue(resultado is Result.QuantityOutOfRange)
        assertEquals(CountUnits.Dimension.KILOS, (resultado as Result.QuantityOutOfRange).dimension)
    }

    @Test
    fun `un decodificador que agrega un cero no pasa porque la hoja no dice ese numero`() {
        // El código dice 0775.000 pero la hoja imprime 77.500: la cantidad no se corrobora y no se cuenta.
        val lectura = lectura("0775.000", "2612010049", impreso("0077.500", "2612010049"))

        assertTrue(lectura is PackageTally.Reading.QuantityNotCorroborated)
    }

    @Test
    fun `con la cuenta abierta la tarjeta no ofrece marcar la partida como revisada`() {
        // Medido en el Pixel el 27-oct: guardar la caja 1 la dejaba «revisada» al 20 % y las otras cuatro no contaban.
        var libro = Ledger()
        val abiertas = cajas.map { (q, lote) ->
            val resultado = PackageTally.process(listOf(partida), libro, etiqueta(q, lote))
            libro = libro.plus((resultado as Result.Added).counted)
            PackageTally.leavesCountOpen(resultado)
        }

        // Las cuatro primeras dejan la cuenta abierta; la quinta cierra exacta y ahí sí se puede marcar.
        assertEquals(listOf(true, true, true, true, false), abiertas)
    }

    @Test
    fun `una cuenta que se pasa tampoco se marca revisada y lo que no se cuenta no la afecta`() {
        var libro = Ledger()
        cajas.forEach { (q, lote) ->
            libro = libro.plus((PackageTally.process(listOf(partida), libro, etiqueta(q, lote)) as Result.Added).counted)
        }
        val pasada = PackageTally.process(listOf(partida), libro, etiqueta("0078.000", "2612010050"))

        assertTrue(PackageTally.leavesCountOpen(pasada))
        // Una etiqueta que no se cuenta (otra parte, sin unidad, lectura nula) deja la tarjeta como siempre.
        assertFalse(PackageTally.leavesCountOpen(PackageTally.process(listOf(partida), Ledger(), etiqueta("0077.600", "2612010049", impreso("0077.600", "2612010049", unidades = "")))))
        assertFalse(PackageTally.leavesCountOpen(null))
    }

    @Test
    fun `dos etiquetas en la misma foto no se mezclan en una lectura`() {
        // Medido en el Pixel el 27-oct: la foto trajo los PDF417 de 78.7 kg (lote ...47) y 77.6 kg (lote ...49) y la
        // tarjeta enseñó 78.7 con el lote ...49.
        val caja3 = pdf417("0078.700", "2612010047")
        val caja1 = pdf417("0077.600", "2612010049")

        val lectura = PackageTally.read(emptyList(), listOf(caja3, caja1), impreso("0078.700", "2612010047"))

        assertTrue(lectura is PackageTally.Reading.SeveralLabels)
        assertEquals(listOf(caja3, caja1), (lectura as PackageTally.Reading.SeveralLabels).ids)
    }

    @Test
    fun `el mismo codigo leido dos veces o con un identificador de caja es una sola etiqueta`() {
        val caja1 = pdf417("0077.600", "2612010049")

        assertFalse(PackageTally.read(emptyList(), listOf(caja1, caja1), "") is PackageTally.Reading.SeveralLabels)
        assertFalse(PackageTally.read(emptyList(), listOf(caja1, "S279239665"), "") is PackageTally.Reading.SeveralLabels)
        assertFalse(PackageTally.read(emptyList(), listOf("0049118845", "50CR304106", "79.2"), "") is PackageTally.Reading.SeveralLabels)
    }

    @Test
    fun `el avance cuenta una partida en kilos como comparable`() {
        val libro = Ledger().plus(PackageTally.CountedPackage(partida.IdRevdet, null, 77.6, "x"))

        val avance = PackageTally.progress(listOf(partida), libro)

        assertEquals(PackageTally.Progress(total = 1, reviewed = 0, uncounted = 0, partial = 1, complete = 0, over = 0, notComparable = 0), avance)
        assertEquals(391.9, PackageTally.expectedQuantity(partida)!!, 0.0)
    }

    @Test
    fun `los litros siguen sin contarse por etiqueta`() {
        val litros = partida.copy(UnidadFactura = "L")

        assertEquals(Result.UnsupportedUnit(litros, "L"), PackageTally.process(listOf(litros), Ledger(), etiqueta("0077.600", "2612010049")))
        assertFalse(CountUnits.countable("L") != null)
        assertNotNull(CountUnits.countable("kg"))
    }

    @Test
    fun `la unidad impresa se reconoce con o sin espacio y no dentro de otra palabra`() {
        assertEquals(setOf(CountUnits.PrintedUnit.KILOS), CountUnits.printedUnits("QTY 77.600KG"))
        assertEquals(setOf(CountUnits.PrintedUnit.POUNDS), CountUnits.printedUnits("NET 517.5 LB"))
        assertEquals(setOf(CountUnits.PrintedUnit.PIECES), CountUnits.printedUnits("10 PCS PER BAG"))
        assertEquals(emptySet<CountUnits.PrintedUnit>(), CountUnits.printedUnits("CHEMICAL COMPOUND BLACK KGX PCB LBL"))
    }
}
