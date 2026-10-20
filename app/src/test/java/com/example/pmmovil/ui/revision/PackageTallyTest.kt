package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.BolsaFreudenbergFixture
import com.example.pmmovil.data.ocr.LabelBarcodeInterpreter
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.ocr.LineaDeTexto
import com.example.pmmovil.ui.revision.PackageTally.Ledger
import com.example.pmmovil.ui.revision.PackageTally.Result
import com.example.pmmovil.ui.revision.PackageTally.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageTallyTest {
    // Partidas reales de la entrada 01C263606600000: línea 48 (bolsa de Taiwán) y línea 33 (bolsa de O-rings).
    private val bolsaTaiwan = partida(3056061, "75183644", cantidad = 2.0, unidad = "CNT")
    private val bolsaOrings = partida(3056046, "75875114", cantidad = 2.1, unidad = "CNT", alias = "75 FKM B1OR72")

    // Sintética: la caja 49263333 vive en otras entradas, en PZA (63,600 / 6,000 / 33,600 / 26,400, todo múltiplo de 600).
    private val piezasCaja = partida(900, "49263333", cantidad = 1200.0, unidad = "PZA", recibida = 1200.0)

    private val claves = listOf("75183644", "75875114", "49263333")
    private val paises = setOf("TAIWAN", "VIETNAM")

    private val pdf417Caja = "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003"
    private val pdf417Orings = "+30S0075875114+Q000210+H0426090326+2D082036+2X566405082026+P0000000000075875"

    /** La misma cadena que sigue la captura: códigos → intérprete de códigos planos → extractor → conteo. */
    private fun leer(codigos: List<String>, lineas: List<LineaDeTexto> = emptyList()): PackageTally.Reading {
        val interpretacion = LabelBarcodeInterpreter.interpretar(codigos, claves, { it.uppercase() in paises }, lineas)
        val extraccion = LabelFieldExtractor.extractDetailed("", codigos, interpretacion.campos, interpretacion.camposSinOcr)
        return PackageTally.read(extraccion.fields, codigos)
    }

    private fun etiqueta(codigos: List<String>, lineas: List<LineaDeTexto> = emptyList()): PackageTally.Label =
        (leer(codigos, lineas) as PackageTally.Reading.Ready).label

    // ---- Leer la etiqueta ----

    @Test
    fun `la bolsa de Taiwan se lee con clave, piezas e identificador`() {
        val label = etiqueta(BolsaFreudenbergFixture.codigos, BolsaFreudenbergFixture.lineas)

        assertEquals("75183644", label.key)
        assertEquals(200.0, label.pieces!!, 0.0)
        assertEquals("1Y-00000050337428", label.packageId)
        assertEquals("75183644|200|5003814158|", label.fingerprint)
    }

    @Test
    fun `la bolsa de O-rings se lee del PDF417 y no tiene identificador`() {
        val label = etiqueta(listOf(pdf417Orings))

        assertEquals("0075875114", label.key)
        assertEquals(210.0, label.pieces!!, 0.0)
        assertNull(label.packageId)
        assertEquals("75875114|210|0426090326|08/2036", label.fingerprint)
    }

    @Test
    fun `la caja se lee con su identificador del Code 39`() {
        val label = etiqueta(listOf("S279239665", pdf417Caja))

        assertEquals("0049263333", label.key)
        assertEquals(600.0, label.pieces!!, 0.0)
        assertEquals("S279239665", label.packageId)
    }

    @Test
    fun `sin la cantidad en un codigo el empaque se reconoce pero no se cuenta`() {
        // Con la foto reducida salen seis de los ocho códigos y falta el de la cantidad.
        val reducida = listOf("4505747759", "667", "2Q26", "75183644", "5003814158", "TAIWAN")

        val label = etiqueta(reducida, BolsaFreudenbergFixture.lineas)
        val resultado = PackageTally.process(listOf(bolsaTaiwan), Ledger(), label)

        assertNull(label.pieces)
        assertTrue(resultado is Result.MissingQuantity)
    }

    @Test
    fun `un dato leido por texto nunca se suma solo`() {
        val porTexto = listOf(
            CampoLeido(Campo.NUMERO_PARTE, "75183644", Origen.ANCLA_MISMA_LINEA, "Part Number", 90),
            CampoLeido(Campo.CANTIDAD, "200", Origen.ANCLA_MISMA_LINEA, "QTY", 90)
        )

        // La clave no salió de un código de barras: no se cuenta sola.
        assertEquals(PackageTally.Reading.NoKey, PackageTally.read(porTexto, emptyList()))

        // La clave sí salió de un código, pero la cantidad solo del texto: se reconoce, sin piezas.
        val mixto = listOf(porTexto[0].copy(origen = Origen.CODIGO_PLANO), porTexto[1])
        val label = (PackageTally.read(mixto, emptyList()) as PackageTally.Reading.Ready).label
        assertNull(label.pieces)
    }

    @Test
    fun `dos etiquetas en el cuadro no se cuentan`() {
        val dos = BolsaFreudenbergFixture.codigos + "1Y-00000050337999"

        val lectura = leer(dos, BolsaFreudenbergFixture.lineas)

        assertTrue(lectura is PackageTally.Reading.SeveralLabels)
    }

    @Test
    fun `un numero de bulto no es una cantidad`() {
        val ordinal = listOf(
            CampoLeido(Campo.NUMERO_PARTE, "75183644", Origen.CODIGO_PLANO, "", 99),
            CampoLeido(
                Campo.CANTIDAD, "3", Origen.GS1, "(30)", 99,
                quantityScope = LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL, packageTotal = "10"
            )
        )

        val label = (PackageTally.read(ordinal, emptyList()) as PackageTally.Reading.Ready).label

        assertNull(label.pieces)
    }

    // ---- Contar ----

    @Test
    fun `la bolsa de Taiwan completa su partida de 2 cientos`() {
        val label = etiqueta(BolsaFreudenbergFixture.codigos, BolsaFreudenbergFixture.lineas)

        val resultado = PackageTally.process(listOf(bolsaTaiwan, bolsaOrings), Ledger(), label) as Result.Added

        assertEquals(3056061, resultado.partida.IdRevdet)
        assertEquals(200.0, resultado.totalAfter, 0.0)
        assertEquals("2.000 CNT son 200 piezas", 200.0, resultado.expected, 0.0)
        assertEquals(State.COMPLETE, resultado.state)
    }

    @Test
    fun `la bolsa de O-rings cuenta 210 piezas contra 2 punto 1 cientos`() {
        val label = etiqueta(listOf(pdf417Orings))

        val resultado = PackageTally.process(listOf(bolsaTaiwan, bolsaOrings), Ledger(), label) as Result.Added

        assertEquals(3056046, resultado.partida.IdRevdet)
        assertEquals(210.0, resultado.totalAfter, 0.0)
        assertEquals(210.0, resultado.expected, 0.0)
        assertEquals(State.COMPLETE, resultado.state)
    }

    @Test
    fun `dos cajas de 600 completan 1200 piezas y la tercera las pasa`() {
        var libro = Ledger()
        val estados = listOf("S279239665", "S279239666", "S279239667").map { id ->
            val resultado = PackageTally.process(listOf(piezasCaja), libro, caja(id)) as Result.Added
            libro = libro.plus(resultado.counted)
            resultado.state to resultado.totalAfter
        }

        assertEquals(listOf(State.PARTIAL to 600.0, State.COMPLETE to 1200.0, State.OVER to 1800.0), estados)
    }

    @Test
    fun `el mismo identificador no se cuenta dos veces`() {
        val primera = PackageTally.process(listOf(piezasCaja), Ledger(), caja("S279239665")) as Result.Added
        val libro = Ledger().plus(primera.counted)

        val repetida = PackageTally.process(listOf(piezasCaja), libro, caja("S279239665"))

        assertTrue(repetida is Result.AlreadyCounted)
        assertEquals(900, (repetida as Result.AlreadyCounted).where?.IdRevdet)
        assertEquals(600.0, libro.total(900), 0.0)
    }

    @Test
    fun `el identificador repetido se reconoce aunque ya se haya revisado la partida`() {
        val primera = PackageTally.process(listOf(piezasCaja), Ledger(), caja("S279239665")) as Result.Added
        val revisada = piezasCaja.copy(Revisada = true)

        val repetida = PackageTally.process(listOf(revisada), Ledger().plus(primera.counted), caja("S279239665"))

        assertTrue(repetida is Result.AlreadyCounted)
    }

    @Test
    fun `el primer empaque reemplaza la cantidad precargada y deshacerlo la restituye`() {
        val resultado = PackageTally.process(listOf(piezasCaja), Ledger(), caja("S279239665")) as Result.Added

        // La partida llegó con 1200 (la de la factura); contar un empaque la deja en 600, no en 1800.
        assertEquals(600.0, resultado.totalAfter, 0.0)
        assertEquals(1200.0, resultado.previousReceived!!, 0.0)

        val sinEl = Ledger().plus(resultado.counted).minus(resultado.counted)
        assertEquals(1200.0, PackageTally.receivedAfterUndo(sinEl, resultado.counted, resultado.previousReceived), 0.0)
    }

    @Test
    fun `deshacer el segundo empaque deja lo que sumaba el primero`() {
        val primero = PackageTally.process(listOf(piezasCaja), Ledger(), caja("S279239665")) as Result.Added
        val libro = Ledger().plus(primero.counted)
        val segundo = PackageTally.process(listOf(piezasCaja), libro, caja("S279239666")) as Result.Added
        val completo = libro.plus(segundo.counted)

        val sinElSegundo = completo.minus(segundo.counted)

        assertEquals(600.0, PackageTally.receivedAfterUndo(sinElSegundo, segundo.counted, segundo.previousReceived), 0.0)
        assertEquals(listOf(primero.counted), sinElSegundo.packages)
    }

    @Test
    fun `quitar un empaque que no esta no cambia el libro`() {
        val libro = Ledger().plus(PackageTally.CountedPackage(900, "S1", 600.0, "x"))

        assertEquals(libro, libro.minus(PackageTally.CountedPackage(900, "S2", 600.0, "y")))
    }

    @Test
    fun `una caja leida y 50 en el teclado suman 30 mil piezas`() {
        val grande = partida(901, "49263333", cantidad = 30000.0, unidad = "PZA", alias = null)

        val resultado = PackageTally.process(listOf(grande), Ledger(), caja("S279239665"), multiple = 50) as Result.Added

        assertEquals(30000.0, resultado.totalAfter, 0.0)
        assertEquals(State.COMPLETE, resultado.state)
        // El identificador queda como contado: leer la misma caja otra vez no suma otras 30 mil.
        val otra = PackageTally.process(listOf(grande), Ledger().plus(resultado.counted), caja("S279239665"))
        assertTrue(otra is Result.AlreadyCounted)
    }

    @Test
    fun `sin identificador dos lecturas iguales seguidas se cuentan y se avisan`() {
        val label = etiqueta(listOf(pdf417Orings))
        val primera = PackageTally.process(listOf(bolsaOrings), Ledger(), label) as Result.Added
        assertFalse("la primera nunca es repetida", primera.sameAsPrevious)

        val segunda = PackageTally.process(listOf(bolsaOrings), Ledger().plus(primera.counted), label) as Result.Added

        assertTrue(segunda.sameAsPrevious)
        assertEquals("se cuenta de todas formas: pueden ser dos bolsas iguales", 420.0, segunda.totalAfter, 0.0)
        assertEquals(State.OVER, segunda.state)
    }

    @Test
    fun `sin identificador un contenido distinto no se avisa`() {
        val uno = etiqueta(listOf(pdf417Orings))
        val otroLote = uno.copy(fingerprint = "75875114|210|0426090999|08/2036")
        val primera = PackageTally.process(listOf(bolsaOrings), Ledger(), uno) as Result.Added

        val segunda = PackageTally.process(listOf(bolsaOrings), Ledger().plus(primera.counted), otroLote) as Result.Added

        assertFalse(segunda.sameAsPrevious)
    }

    // ---- Lo que no se cuenta ----

    @Test
    fun `una etiqueta de otra entrada no se cuenta`() {
        val resultado = PackageTally.process(listOf(bolsaTaiwan, bolsaOrings), Ledger(), caja("S279239665"))

        assertEquals(Result.NotInEntry("0049263333"), resultado)
    }

    @Test
    fun `la misma clave en dos partidas la decide el revisador`() {
        val otra = piezasCaja.copy(IdRevdet = 901, Cnofact = "FACT-2")

        val resultado = PackageTally.process(listOf(piezasCaja, otra), Ledger(), caja("S279239665"))

        assertTrue(resultado is Result.SeveralPartidas)
        assertEquals(listOf(900, 901), (resultado as Result.SeveralPartidas).candidates.map { it.IdRevdet })
    }

    @Test
    fun `el alias del cliente tambien identifica la partida`() {
        val conAlias = partida(902, "INTERNA-1", cantidad = 1200.0, unidad = "PZA", alias = "49263333")

        val resultado = PackageTally.process(listOf(conAlias), Ledger(), caja("S279239665"))

        assertTrue(resultado is Result.Added)
    }

    @Test
    fun `la unica partida de la lista no basta si la clave no es la suya`() {
        val resultado = PackageTally.process(listOf(bolsaTaiwan), Ledger(), caja("S279239665"))

        assertTrue(resultado is Result.NotInEntry)
    }

    @Test
    fun `una partida ya revisada no se toca`() {
        val revisada = piezasCaja.copy(Revisada = true)

        val resultado = PackageTally.process(listOf(revisada), Ledger(), caja("S279239665"))

        assertEquals(Result.AlreadyReviewed(revisada), resultado)
    }

    @Test
    fun `una factura en kilos no se cuenta por piezas`() {
        val kilos = partida(903, "49263333", cantidad = 49.2, unidad = "KG")

        val resultado = PackageTally.process(listOf(kilos), Ledger(), caja("S279239665"))

        assertEquals(Result.UnsupportedUnit(kilos, "KG"), resultado)
    }

    @Test
    fun `sin unidad en la factura tampoco se cuenta`() {
        val sinUnidad = partida(904, "49263333", cantidad = 1200.0, unidad = null)

        assertTrue(PackageTally.process(listOf(sinUnidad), Ledger(), caja("S279239665")) is Result.UnsupportedUnit)
    }

    // ---- Avance ----

    @Test
    fun `el avance separa revisadas, sin contar, parciales, completas y las que se pasaron`() {
        val partidas = listOf(
            partida(1, "A", 1200.0, "PZA"),
            partida(2, "B", 1200.0, "PZA"),
            partida(3, "C", 1200.0, "PZA"),
            partida(4, "D", 1200.0, "PZA"),
            partida(5, "E", 49.2, "KG"),
            partida(6, "F", 1200.0, "PZA", revisada = true)
        )
        val libro = Ledger(
            listOf(
                PackageTally.CountedPackage(2, "S2", 600.0, "b"),
                PackageTally.CountedPackage(3, "S3", 1200.0, "c"),
                PackageTally.CountedPackage(4, "S4", 1800.0, "d")
            )
        )

        val avance = PackageTally.progress(partidas, libro)

        assertEquals(
            PackageTally.Progress(total = 6, reviewed = 1, uncounted = 1, partial = 1, complete = 1, over = 1, notComparable = 1),
            avance
        )
    }

    @Test
    fun `los cientos y los millares se comparan en piezas`() {
        assertEquals(State.COMPLETE, PackageTally.stateOf(210.0, PackageTally.expectedPieces(bolsaOrings)))
        assertEquals(State.PARTIAL, PackageTally.stateOf(100.0, PackageTally.expectedPieces(bolsaOrings)))
        assertEquals(State.NOT_COMPARABLE, PackageTally.stateOf(5.0, null))
        val millar = partida(905, "M", cantidad = 1.5, unidad = "MLL")
        assertEquals(1500.0, PackageTally.expectedPieces(millar)!!, 0.0)
    }

    // ---- El libro en disco ----

    @Test
    fun `el libro se guarda y se recupera igual`() {
        val libro = Ledger(
            listOf(
                PackageTally.CountedPackage(3056061, "1Y-00000050337428", 200.0, "75183644|200|5003814158|"),
                PackageTally.CountedPackage(3056046, null, 210.0, "75875114|210|0426090326|08/2036")
            )
        )

        assertEquals(libro, PackageLedgerCodec.decode(PackageLedgerCodec.encode(libro)))
    }

    @Test
    fun `un renglon dañado se ignora y no tira el libro`() {
        val texto = "3056061\t200.0\t1Y-1\thuella\n" +
            "esto no es un renglon\n" +
            "abc\t200.0\t\thuella\n" +
            "3056046\t-5.0\t\thuella\n" +
            "3056046\t210.0\t\tbuena"

        val libro = PackageLedgerCodec.decode(texto)

        assertEquals(listOf(3056061, 3056046), libro.packages.map { it.partidaId })
        assertEquals("buena", libro.packages.last().fingerprint)
    }

    @Test
    fun `un libro vacio o ausente es un libro vacio`() {
        assertEquals(Ledger(), PackageLedgerCodec.decode(null))
        assertEquals(Ledger(), PackageLedgerCodec.decode(""))
        assertEquals("", PackageLedgerCodec.encode(Ledger()))
    }

    @Test
    fun `los tabuladores y saltos que traiga un codigo no rompen el formato`() {
        val libro = Ledger(listOf(PackageTally.CountedPackage(1, "S\t1\n2", 5.0, "a\tb\nc")))

        val recuperado = PackageLedgerCodec.decode(PackageLedgerCodec.encode(libro))

        assertEquals(1, recuperado.packages.size)
        assertEquals("S 1 2", recuperado.packages.single().packageId)
    }

    // ---- Apoyo ----

    private fun caja(id: String) = PackageTally.Label(
        key = "0049263333", pieces = 600.0, packageId = id, fingerprint = "49263333|600|0031704192|09/2036"
    )

    // ── El peso de los empaques ─────────────────────────────────────────────

    @Test
    fun `el peso del empaque se lee del renglon impreso y se guarda en kilos`() {
        val reading = PackageTally.read(
            listOf(
                CampoLeido(Campo.NUMERO_PARTE, "75370203", Origen.STRUCTURED_BARCODE, "30S", 99),
                CampoLeido(Campo.CANTIDAD, "20000", Origen.STRUCTURED_BARCODE, "Q", 99),
                CampoLeido(Campo.PESO_NETO, "8", Origen.ANCLA_MISMA_LINEA, "NET WT", 90, unidad = "KG")
            ),
            listOf("S279544448")
        ) as PackageTally.Reading.Ready
        assertEquals(8.0, reading.label.weightKg!!, 0.0001)
    }

    @Test
    fun `un peso en libras se guarda convertido a kilos`() {
        val reading = PackageTally.read(
            listOf(
                CampoLeido(Campo.NUMERO_PARTE, "75370203", Origen.STRUCTURED_BARCODE, "30S", 99),
                CampoLeido(Campo.CANTIDAD, "20000", Origen.STRUCTURED_BARCODE, "Q", 99),
                CampoLeido(Campo.PESO_NETO, "100", Origen.ANCLA_MISMA_LINEA, "NET WT", 90, unidad = "LB")
            ),
            emptyList()
        ) as PackageTally.Reading.Ready
        assertEquals(45.36, reading.label.weightKg!!, 0.0001)
    }

    @Test
    fun `los 30 empaques de la partida real suman 240 kilos contra 242 declarados`() {
        // La partida 3055981: 6000 CNT = 600 000 piezas, npeso 534.392 lb = 242.4 kg, y 30 etiquetas
        // de 20 000 piezas y 8 kg cada una. El conteo de piezas cierra exacto; el de kilos no, y por
        // eso no se clasifica como «completo».
        val partidas = listOf(partida(3055981, "75370203", 6000.0, "CNT", pesoLibras = 534.392))
        var ledger = Ledger()
        var ultimo: Result.Added? = null
        repeat(30) { i ->
            val label = PackageTally.Label("75370203", 20000.0, "S2795444" + (48 + i), "f" + i, weightKg = 8.0)
            val r = PackageTally.process(partidas, ledger, label) as Result.Added
            ledger = ledger.plus(r.counted)
            ultimo = r
        }
        assertEquals(600000.0, ultimo!!.totalAfter, 0.0001)
        assertEquals(State.COMPLETE, ultimo!!.state)
        assertEquals(240.0, ultimo!!.totalWeightKg, 0.0001)
        assertEquals(242.4, ultimo!!.expectedWeightKg!!, 0.01)
        assertEquals(30, ledger.weighedCount(3055981))
    }

    @Test
    fun `un empaque ya contado no vuelve a sumar su peso`() {
        val partidas = listOf(partida(1, "75370203", 6000.0, "CNT", pesoLibras = 534.392))
        val label = PackageTally.Label("75370203", 20000.0, "S279544448", "f", weightKg = 8.0)
        val primero = PackageTally.process(partidas, Ledger(), label) as Result.Added
        val ledger = Ledger().plus(primero.counted)
        assertTrue(PackageTally.process(partidas, ledger, label) is Result.AlreadyCounted)
        assertEquals(8.0, ledger.totalWeightKg(1), 0.0001)
    }

    @Test
    fun `una etiqueta sin peso cuenta piezas pero no suma kilos`() {
        val partidas = listOf(partida(1, "75370203", 6000.0, "CNT", pesoLibras = 534.392))
        val label = PackageTally.Label("75370203", 20000.0, "S279544448", "f", weightKg = null)
        val r = PackageTally.process(partidas, Ledger(), label) as Result.Added
        assertEquals(20000.0, r.totalAfter, 0.0001)
        assertEquals(0.0, r.totalWeightKg, 0.0001)
    }

    @Test
    fun `una caja leida y por 50 multiplica tambien el peso`() {
        val partidas = listOf(partida(1, "75370203", 6000.0, "CNT", pesoLibras = 534.392))
        val label = PackageTally.Label("75370203", 600.0, null, "f", weightKg = 8.0)
        val r = PackageTally.process(partidas, Ledger(), label, multiple = 50) as Result.Added
        assertEquals(30000.0, r.counted.pieces, 0.0001)
        assertEquals(400.0, r.counted.weightKg, 0.0001)
    }

    @Test
    fun `sin peso declarado la partida no tiene con que comparar`() {
        val partidas = listOf(partida(1, "75370203", 6000.0, "CNT"))
        val label = PackageTally.Label("75370203", 20000.0, "S279544448", "f", weightKg = 8.0)
        val r = PackageTally.process(partidas, Ledger(), label) as Result.Added
        assertEquals(8.0, r.totalWeightKg, 0.0001)
        assertNull(r.expectedWeightKg)
    }

    @Test
    fun `un libro guardado sin peso se sigue leyendo entero`() {
        // Cuatro columnas: el formato anterior a que el peso existiera.
        val viejo = "1\t20000.0\tS279544448\t75370203|20000||"
        val ledger = PackageLedgerCodec.decode(viejo)
        assertEquals(1, ledger.packages.size)
        assertEquals(20000.0, ledger.packages[0].pieces, 0.0001)
        assertEquals(0.0, ledger.packages[0].weightKg, 0.0001)
    }

    @Test
    fun `el peso sobrevive a guardar y volver a leer el libro`() {
        val ledger = Ledger(listOf(PackageTally.CountedPackage(1, "S279544448", 20000.0, "f", 8.0)))
        val vuelta = PackageLedgerCodec.decode(PackageLedgerCodec.encode(ledger))
        assertEquals(8.0, vuelta.packages[0].weightKg, 0.0001)
        assertEquals(8.0, vuelta.totalWeightKg(1), 0.0001)
    }

    private fun partida(
        id: Int,
        clave: String,
        cantidad: Double,
        unidad: String?,
        alias: String? = null,
        recibida: Double? = null,
        revisada: Boolean = false,
        pesoLibras: Double? = null
    ) = PartidaRevisionDto(
        IdRevdet = id, Cnofact = "2656195421", CnoParte = clave, cDescripcion = null, Marca = null, Modelo = null,
        NumSer = null, NumLote = null, nCantFactura = cantidad, nCantOut = recibida, cPaisOrigen = null,
        CptCveTmateria = null, CptDescTmateria = null, Revisada = revisada, Obsrevpartida = null,
        Mnotapartida = null, nPeso = pesoLibras, Cclienteid = null, UnidadFactura = unidad, ParteCte = alias
    )
}
