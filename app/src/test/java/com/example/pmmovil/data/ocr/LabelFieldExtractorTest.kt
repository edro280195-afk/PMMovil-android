package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los textos de estas pruebas NO son inventados: salieron de las fotos de
 * evidencia de revisiones reales (enlaces publicos de seguimiento, 14-oct-2026).
 * Cada prueba dice de que cliente viene. Si alguna falla, es que el extractor
 * dejo de leer una etiqueta que de verdad llega a la bodega.
 */
class LabelFieldExtractorTest {

    private fun valor(texto: String, campo: Campo, codigos: List<String> = emptyList()): String? =
        LabelFieldExtractor.extraer(texto, codigos).firstOrNull { it.campo == campo }?.valor

    // ── WIAMEC: bolsa Brikksen dentro de caja McMaster-Carr ──────────────────
    private val brikksen = """
        1 PACK
        10 PCS
        PART #  PPMSSSCH4C.25-B10
        4-40 X 1/4 PHIL PAN MS SS/CHROME - 10
        PER BAG
        BRIKKSEN
        COO:  THAILAND
        LOT #:  2504298-135
    """.trimIndent()

    @Test
    fun `bolsa Brikksen entrega lote, parte, pais y piezas`() {
        assertEquals("2504298-135", valor(brikksen, Campo.LOTE))
        assertEquals("PPMSSSCH4C.25-B10", valor(brikksen, Campo.NUMERO_PARTE))
        assertEquals("THAILAND", valor(brikksen, Campo.PAIS_ORIGEN))
        assertEquals("10", valor(brikksen, Campo.PIEZAS_POR_EMPAQUE))
    }

    @Test
    fun `el lote de Brikksen conserva su guion tal cual`() {
        // La regla de no fabricar ni corregir formatos: el valor sale como esta impreso.
        val leido = LabelFieldExtractor.extraer(brikksen).first { it.campo == Campo.LOTE }
        assertEquals("2504298-135", leido.valor)
        assertEquals(Origen.ANCLA_MISMA_LINEA, leido.origen)
        assertTrue(leido.ancla.uppercase().startsWith("LOT"))
    }

    // ── SILOS Y CAMIONES: etiqueta VORTEX, puro texto sin codigo ─────────────
    @Test
    fun `etiqueta VORTEX entrega item y resuelve el 1 of 2`() {
        val vortex = """
            VORTEX
            Item: QC10-03
            Customer: SILOS Y CAMIONES S.A.DE C.V.
            Qty: 1 of 2
            Order #: VL00003614    Rev: 1
            Description: LOAD SEAL 30+/-5 DURO WHITE SILICONE RUBBER
        """.trimIndent()

        assertEquals("QC10-03", valor(vortex, Campo.NUMERO_PARTE))
        // "1 of 2" significa pieza 1 de 2, no una cantidad de "1 of 2".
        assertEquals("1", valor(vortex, Campo.CANTIDAD))
    }

    // ── ALLNEX: bote de resina ───────────────────────────────────────────────
    @Test
    fun `etiqueta allnex entrega codigo de producto y numero de lote`() {
        val allnex = """
            allnex
            SETAL® 41-1449
            Product Code  058585A01
            Lot Number  WL05816B2
            GAL
        """.trimIndent()

        assertEquals("058585A01", valor(allnex, Campo.NUMERO_PARTE))
        assertEquals("WL05816B2", valor(allnex, Campo.LOTE))
    }

    // ── TC HEARTLAND: etiqueta ADM con Batch/Lot# ────────────────────────────
    @Test
    fun `etiqueta ADM entrega el Batch slash Lot`() {
        val adm = """
            ADM
            N&A PASSIONFRUIT SD FL color
            Item#: FATI051
            Batch/Lot#: F26083046C
            AMBIENT (55-75 F) (13-24 C)
        """.trimIndent()

        assertEquals("F26083046C", valor(adm, Campo.LOTE))
        assertEquals("FATI051", valor(adm, Campo.NUMERO_PARTE))
    }

    // ── QUIMI CORP: PATCO, con identificador GS1 en el codigo ────────────────
    @Test
    fun `el codigo GS1 le gana al texto`() {
        val patco = """
            PATIONIC® 1042K
            ITEM NO: (99) PATIONIC 1042K
            LOT NO (10) 2625100027
            NET WGT: 2000  50 LBS.
            PATCO PRODUCTS
        """.trimIndent()

        val leido = LabelFieldExtractor.extraer(patco, listOf("(01)10614141999996(10)2625100027"))
            .first { it.campo == Campo.LOTE }

        assertEquals("2625100027", leido.valor)
        assertEquals(Origen.GS1, leido.origen)
        assertEquals(99, leido.confianza)
    }

    // ── ALLNEX: rombo de mercancia peligrosa ─────────────────────────────────
    @Test
    fun `el rombo entrega UN, clase y grupo de embalaje por separado`() {
        val rombo = """
            CYMEL® 1158 RESIN
            011331A01D04
            Flash Point:
            47 °C  117 °F
            3
            UN1866
            RESIN SOLUTION
            Hazard Class: 3
            Packing Group: III
        """.trimIndent()

        // El punto del caso: tres datos distintos, en tres campos distintos.
        // Hoy la base los guarda revueltos como "1866 3,III".
        assertEquals("1866", valor(rombo, Campo.UN_HAZMAT))
        assertEquals("3", valor(rombo, Campo.CLASE_HAZMAT))
        assertEquals("III", valor(rombo, Campo.GRUPO_EMBALAJE))
    }

    // ── HERRAMIENTAS ELECTRICAS: etiqueta ENERPAC escrita a maquina ──────────
    @Test
    fun `etiqueta ENERPAC entrega la cantidad`() {
        val enerpac = """
            ENERPAC SPANISH
            PB E331 INDUSTRIAL TOOL
            Quantity: 14
        """.trimIndent()

        assertEquals("14", valor(enerpac, Campo.CANTIDAD))
    }

    // ── WATSON PHILLIPS: saco IMERYS, impresion sin ancla ────────────────────
    @Test
    fun `el saco sin ancla no entrega lote inventado`() {
        // "NEOGEN 2000 50LB 26907AF31": el lote esta ahi, pero no hay nada que
        // diga cual de los tres numeros es. Preferimos no capturar a adivinar;
        // este caso se resuelve con un patron por cliente, no con anclas.
        val saco = "NEOGEN 2000 50LB 26907AF31"
        assertNull(valor(saco, Campo.LOTE))
    }

    @Test
    fun `no confunde un ancla metida dentro de otra palabra`() {
        // "PILOT" contiene "LOT" y "DESCANT" contiene "CANT".
        val trampa = """
            PILOT VALVE ASSEMBLY 4471
            DESCANT 9920
        """.trimIndent()

        assertNull(valor(trampa, Campo.LOTE))
        assertNull(valor(trampa, Campo.CANTIDAD))
    }

    @Test
    fun `lee el valor del renglon de abajo cuando el ancla queda sola`() {
        val partido = """
            Lot Number
            WL0597931
        """.trimIndent()

        val leido = LabelFieldExtractor.extraer(partido).first { it.campo == Campo.LOTE }
        assertEquals("WL0597931", leido.valor)
        assertEquals(Origen.ANCLA_LINEA_SIGUIENTE, leido.origen)
        assertTrue("debe bajar la confianza", leido.confianza < 90)
    }

    @Test
    fun `texto vacio no entrega nada`() {
        assertTrue(LabelFieldExtractor.extraer("").isEmpty())
        assertTrue(LabelFieldExtractor.extraer("   \n  \n ").isEmpty())
    }
}
