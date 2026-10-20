package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.CampoLeido
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Qué hace la app con el peso de la etiqueta: compararlo con lo declarado y, por ahora, no escribirlo. */
class PesoDeEtiquetaTest {

    private val R_COMPARED = com.example.pmmovil.R.string.label_capture_weight_compared
    private val R_SUM_COMPARED = com.example.pmmovil.R.string.label_capture_weight_sum_compared
    private val R_SUM_PARTIAL = com.example.pmmovil.R.string.label_capture_weight_sum_partial
    private val R_SUM_ONLY = com.example.pmmovil.R.string.label_capture_weight_sum_only

    private fun peso(campo: Campo, valor: String, unidad: String?) =
        CampoLeido(campo, valor, Origen.ANCLA_MISMA_LINEA, "NET WT", 90, unidad = unidad)

    @Test
    fun `kilos van al campo de kilos`() {
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "8", "KG")), 0.0, 0.0)
        val unico = r as PesoDeEtiqueta.Resultado.Unico
        assertEquals(PesoDeEtiqueta.CAMPO_KILOS, unico.peso.campo)
        assertEquals(8.0, unico.peso.valor, 0.0001)
    }

    @Test
    fun `libras van al campo de libras`() {
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "164.242", "LB")), 0.0, 0.0)
        val unico = r as PesoDeEtiqueta.Resultado.Unico
        assertEquals(PesoDeEtiqueta.CAMPO_LIBRAS, unico.peso.campo)
        assertEquals(164.242, unico.peso.valor, 0.0001)
    }

    @Test
    fun `con neto y bruto no se elige, decide el revisador`() {
        // La etiqueta de envío VDA trae los dos. La base no distingue neto de bruto, así que elegir
        // uno sería inventarlo.
        val r = PesoDeEtiqueta.resolver(
            listOf(peso(Campo.PESO_NETO, "8", "KG"), peso(Campo.PESO_BRUTO, "9", "KG")),
            0.0, 0.0
        )
        val ambiguo = r as PesoDeEtiqueta.Resultado.Ambiguo
        assertEquals(8.0, ambiguo.neto.valor, 0.0001)
        assertEquals(9.0, ambiguo.bruto.valor, 0.0001)
    }

    @Test
    fun `no pisa un peso que la factura ya trae`() {
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "8", "KG")), 0.0, 563.371)
        val yaCapturado = r as PesoDeEtiqueta.Resultado.YaCapturado
        assertEquals(563.371, yaCapturado.actual, 0.0001)
        assertEquals(8.0, yaCapturado.peso.valor, 0.0001)
    }

    @Test
    fun `un peso en kilos no estorba si lo capturado esta en libras`() {
        // El campo destino es el de kilos; que libras tenga el peso del documento no lo bloquea.
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "8", "KG")), 1242.3, 0.0)
        assertTrue(r is PesoDeEtiqueta.Resultado.Unico)
    }

    @Test
    fun `sin unidad no se guarda nada`() {
        // La bolsa de Taiwán imprime números sueltos: meter kilos en libras cambia la cifra por 2.2.
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "8", null)), 0.0, 0.0)
        assertEquals(PesoDeEtiqueta.Resultado.Ninguno, r)
    }

    @Test
    fun `una unidad que no es de peso se ignora`() {
        val r = PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "20000", "PC")), 0.0, 0.0)
        assertEquals(PesoDeEtiqueta.Resultado.Ninguno, r)
    }

    @Test
    fun `un peso en cero o negativo no se guarda`() {
        assertEquals(
            PesoDeEtiqueta.Resultado.Ninguno,
            PesoDeEtiqueta.resolver(listOf(peso(Campo.PESO_NETO, "0", "KG")), 0.0, 0.0)
        )
    }

    @Test
    fun `sin peso en la etiqueta no hay nada que hacer`() {
        val r = PesoDeEtiqueta.resolver(
            listOf(CampoLeido(Campo.LOTE, "5004029781", Origen.STRUCTURED_BARCODE, "H", 99)),
            0.0, 0.0
        )
        assertEquals(PesoDeEtiqueta.Resultado.Ninguno, r)
    }

    // ── El aviso: comparar, no escribir ─────────────────────────────────────

    /** Resuelve los textos sin Android: devuelve el id y los argumentos, que es lo que se quiere probar. */
    private val texto: (Int, Array<Any>) -> String = { id, args -> "$id:" + args.joinToString("|") }

    @Test
    fun `compara el peso de la etiqueta contra el total que declara la partida`() {
        // Caso real: la partida 3055981 declara 534.392 lb (242.4 kg) y la etiqueta dice 8 kg, porque
        // son 30 empaques. El aviso enseña los dos números en kilos.
        val aviso = PesoDeEtiqueta.aviso(
            listOf(peso(Campo.PESO_NETO, "8", "KG")),
            pesoDeclaradoLibras = 534.392,
            texto = texto
        )
        assertEquals("${R_COMPARED}:8|242.4", aviso)
    }

    @Test
    fun `con los dos pesos usa el neto para comparar`() {
        // Medido en la partida 3055981: con el neto los 30 empaques dan 240 kg contra 242.4 declarados
        // (1 %); con el bruto darían 270 (11 %). Una sola muestra, documentada como tal.
        val aviso = PesoDeEtiqueta.aviso(
            listOf(peso(Campo.PESO_NETO, "8", "KG"), peso(Campo.PESO_BRUTO, "9", "KG")),
            pesoDeclaradoLibras = 534.392,
            texto = texto
        )
        assertEquals("${R_COMPARED}:8|242.4", aviso)
    }

    // ── El acumulado de varios empaques ─────────────────────────────────────

    @Test
    fun `los 30 empaques de la partida real suman contra lo declarado`() {
        val aviso = PesoDeEtiqueta.avisoAcumulado(
            empaquesPesados = 30, empaquesContados = 30,
            pesoAcumuladoKg = 240.0,
            pesoDeclaradoKg = 242.4,
            texto = texto
        )
        assertEquals("${R_SUM_COMPARED}:240|242.4|30|30", aviso)
    }

    @Test
    fun `si algun empaque no trae peso el acumulado se marca incompleto`() {
        val aviso = PesoDeEtiqueta.avisoAcumulado(
            empaquesPesados = 12, empaquesContados = 30,
            pesoAcumuladoKg = 96.0,
            pesoDeclaradoKg = 242.4,
            texto = texto
        )
        assertEquals("${R_SUM_PARTIAL}:96|242.4|12|30", aviso)
    }

    @Test
    fun `sin peso declarado solo se informa la suma`() {
        val aviso = PesoDeEtiqueta.avisoAcumulado(
            empaquesPesados = 3, empaquesContados = 3,
            pesoAcumuladoKg = 24.0,
            pesoDeclaradoKg = null,
            texto = texto
        )
        assertEquals("${R_SUM_ONLY}:24|3", aviso)
    }

    @Test
    fun `sin empaques pesados no se avisa del acumulado`() {
        assertEquals(
            null,
            PesoDeEtiqueta.avisoAcumulado(0, 5, 0.0, 242.4, texto)
        )
    }

    @Test
    fun `sin peso declarado no hay con que comparar y no se avisa`() {
        val aviso = PesoDeEtiqueta.aviso(
            listOf(peso(Campo.PESO_NETO, "8", "KG")),
            pesoDeclaradoLibras = 0.0,
            texto = texto
        )
        assertEquals(null, aviso)
    }

    @Test
    fun `sin peso en la etiqueta no se avisa nada`() {
        val aviso = PesoDeEtiqueta.aviso(emptyList(), pesoDeclaradoLibras = 534.392, texto = texto)
        assertEquals(null, aviso)
    }

    @Test
    fun `una etiqueta en libras se compara en kilos`() {
        val aviso = PesoDeEtiqueta.aviso(
            listOf(peso(Campo.PESO_NETO, "100", "LB")),
            pesoDeclaradoLibras = 534.392,
            texto = texto
        )
        // 100 lb × 0.4536 = 45.36 kg
        assertEquals("${R_COMPARED}:45.36|242.4", aviso)
    }

    @Test
    fun `la etiqueta VDA real entrega los dos pesos en kilos`() {
        // De punta a punta: el texto de la etiqueta tal como lo entrega el lector.
        val leido = LabelFieldExtractor.extraer("NET WT KG 8\nGROSS WT KG 9")
        val r = PesoDeEtiqueta.resolver(leido, 0.0, 0.0) as PesoDeEtiqueta.Resultado.Ambiguo
        assertEquals(PesoDeEtiqueta.CAMPO_KILOS, r.neto.campo)
        assertEquals(PesoDeEtiqueta.CAMPO_KILOS, r.bruto.campo)
        assertEquals(8.0, r.neto.valor, 0.0001)
        assertEquals(9.0, r.bruto.valor, 0.0001)
    }
}
