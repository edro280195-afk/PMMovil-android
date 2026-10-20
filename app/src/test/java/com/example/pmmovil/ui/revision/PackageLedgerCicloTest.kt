package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.PartidaRevisionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El ciclo que vive el revisador al contar una entrada: leer etiquetas, ver el acumulado y deshacer
 * la última. Es la lógica que `DetalleRevisionViewModel.contarEmpaque` y `deshacerAcumulacion`
 * encadenan, probada sin Android.
 *
 * Lo que se vigila aquí es la trampa de este flujo: las partidas llegan con la cantidad recibida
 * **precargada** con la de la factura, así que el primer empaque tiene que **reemplazarla** y no
 * sumarse encima, y al deshacer hay que devolver exactamente lo que había.
 */
class PackageLedgerCicloTest {

    private val partida = PartidaRevisionDto(
        IdRevdet = 3055981, Cnofact = "2656195423", CnoParte = "75370203", cDescripcion = null,
        Marca = null, Modelo = null, NumSer = null, NumLote = null,
        nCantFactura = 6000.0, nCantOut = 600000.0, cPaisOrigen = null,
        CptCveTmateria = null, CptDescTmateria = null, Revisada = false, Obsrevpartida = null,
        Mnotapartida = null, nPeso = 534.392, Cclienteid = "CL8292", UnidadFactura = "CNT",
        ParteCte = null
    )

    private fun etiqueta(n: Int) =
        PackageTally.Label("75370203", 20000.0, "S27954444$n", "f$n", weightKg = 8.0)

    @Test
    fun `el primer empaque reemplaza la cantidad precargada en vez de sumarse`() {
        // La partida llega con 600 000 piezas precargadas de la factura. Si el primer empaque se
        // sumara encima quedaría en 620 000, que es justo el bug que este libro evita.
        val r = PackageTally.process(listOf(partida), PackageTally.Ledger(), etiqueta(0)) as PackageTally.Result.Added
        assertEquals(20000.0, r.totalAfter, 0.0001)
        assertEquals(600000.0, r.previousReceived!!, 0.0001)
        assertEquals(PackageTally.State.PARTIAL, r.state)
    }

    @Test
    fun `contar las 30 etiquetas deja la partida completa y 240 kilos`() {
        var ledger = PackageTally.Ledger()
        var ultimo: PackageTally.Result.Added? = null
        repeat(30) { i ->
            val r = PackageTally.process(listOf(partida), ledger, etiqueta(i)) as PackageTally.Result.Added
            ledger = ledger.plus(r.counted)
            ultimo = r
        }
        assertEquals(600000.0, ultimo!!.totalAfter, 0.0001)
        assertEquals(PackageTally.State.COMPLETE, ultimo!!.state)
        assertEquals(240.0, ledger.totalWeightKg(3055981), 0.0001)
        assertEquals(30, ledger.ofPartida(3055981).size)
    }

    @Test
    fun `deshacer el unico empaque devuelve la cantidad que la partida traia`() {
        val r = PackageTally.process(listOf(partida), PackageTally.Ledger(), etiqueta(0)) as PackageTally.Result.Added
        val conEl = PackageTally.Ledger().plus(r.counted)
        val sinEl = conEl.minus(r.counted)
        assertEquals(600000.0, PackageTally.receivedAfterUndo(sinEl, r.counted, r.previousReceived), 0.0001)
        assertTrue(sinEl.packages.isEmpty())
        assertEquals(0.0, sinEl.totalWeightKg(3055981), 0.0001)
    }

    @Test
    fun `deshacer uno de varios deja lo que suman los que quedan`() {
        var ledger = PackageTally.Ledger()
        var ultimo: PackageTally.Result.Added? = null
        repeat(3) { i ->
            val r = PackageTally.process(listOf(partida), ledger, etiqueta(i)) as PackageTally.Result.Added
            ledger = ledger.plus(r.counted)
            ultimo = r
        }
        val sinElUltimo = ledger.minus(ultimo!!.counted)
        // Quedan dos empaques: 40 000 piezas y 16 kg, no la cantidad precargada de la factura.
        assertEquals(40000.0, PackageTally.receivedAfterUndo(sinElUltimo, ultimo!!.counted, ultimo!!.previousReceived), 0.0001)
        assertEquals(16.0, sinElUltimo.totalWeightKg(3055981), 0.0001)
    }

    @Test
    fun `el libro sobrevive a cerrar la app`() {
        var ledger = PackageTally.Ledger()
        repeat(5) { i ->
            val r = PackageTally.process(listOf(partida), ledger, etiqueta(i)) as PackageTally.Result.Added
            ledger = ledger.plus(r.counted)
        }
        val vuelta = PackageLedgerCodec.decode(PackageLedgerCodec.encode(ledger))
        assertEquals(100000.0, vuelta.total(3055981), 0.0001)
        assertEquals(40.0, vuelta.totalWeightKg(3055981), 0.0001)
        // Y el sexto empaque sigue sumando sobre lo contado, no sobre lo precargado.
        val sexto = PackageTally.process(listOf(partida), vuelta, etiqueta(5)) as PackageTally.Result.Added
        assertEquals(120000.0, sexto.totalAfter, 0.0001)
    }

    @Test
    fun `una cantidad mal decodificada no se cuenta`() {
        // Medido en el Pixel el 20-oct: la FOTO decodificó Q20000000 donde la etiqueta dice Q20000.
        // Son veinte millones de piezas en una partida que declara 600 000. Contarlo habría escrito
        // esa cifra en la cantidad recibida.
        val mala = PackageTally.Label("75370203", 20000000.0, "S279544448", "f", weightKg = 8.0)
        val r = PackageTally.process(listOf(partida), PackageTally.Ledger(), mala)
        assertTrue("se esperaba rechazo, salió $r", r is PackageTally.Result.QuantityOutOfRange)
        val fuera = r as PackageTally.Result.QuantityOutOfRange
        assertEquals(20000000.0, fuera.perPackage, 0.0001)
        assertEquals(600000.0, fuera.expected, 0.0001)
    }

    @Test
    fun `un empaque que completa la partida justo si se cuenta`() {
        // El límite no debe estorbar al caso bueno: 600 000 de 600 000 es válido.
        val completo = PackageTally.Label("75370203", 600000.0, "S279544448", "f", weightKg = 240.0)
        val r = PackageTally.process(listOf(partida), PackageTally.Ledger(), completo)
        assertTrue(r is PackageTally.Result.Added)
        assertEquals(PackageTally.State.COMPLETE, (r as PackageTally.Result.Added).state)
    }

    @Test
    fun `releer una etiqueta ya contada no cambia nada`() {
        val r = PackageTally.process(listOf(partida), PackageTally.Ledger(), etiqueta(0)) as PackageTally.Result.Added
        val ledger = PackageTally.Ledger().plus(r.counted)
        val repetida = PackageTally.process(listOf(partida), ledger, etiqueta(0))
        assertTrue(repetida is PackageTally.Result.AlreadyCounted)
        assertEquals(20000.0, ledger.total(3055981), 0.0001)
        assertEquals(8.0, ledger.totalWeightKg(3055981), 0.0001)
    }

    @Test
    fun `el aviso del peso acumulado acompana al conteo`() {
        var ledger = PackageTally.Ledger()
        var ultimo: PackageTally.Result.Added? = null
        repeat(30) { i ->
            val r = PackageTally.process(listOf(partida), ledger, etiqueta(i)) as PackageTally.Result.Added
            ledger = ledger.plus(r.counted)
            ultimo = r
        }
        val aviso = PesoDeEtiqueta.avisoAcumuladoRecurso(
            empaquesPesados = ledger.weighedCount(3055981),
            empaquesContados = ledger.ofPartida(3055981).size,
            pesoAcumuladoKg = ultimo!!.totalWeightKg,
            pesoDeclaradoKg = ultimo!!.expectedWeightKg
        )!!
        assertEquals(com.example.pmmovil.R.string.label_capture_weight_sum_compared, aviso.first)
        assertEquals("240", aviso.second[0])
        assertEquals("242.4", aviso.second[1])
    }
}
