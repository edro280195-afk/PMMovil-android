package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Origen
import com.example.pmmovil.data.ocr.LabelFieldExtractor.QuantityScope
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

    @Test
    fun `GS1 con AIM y separadores conserva lote serie fecha y cantidad`() {
        val code = "]C1011061414199999610Lot-A12\u001D21Serial-b45\u001D1727123130190"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("Lot-A12", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("Serial-b45", fields.first { it.campo == Campo.NUMERO_SERIE }.valor)
        assertEquals("271231", fields.first { it.campo == Campo.CADUCIDAD }.valor)
        assertEquals("190", fields.first { it.campo == Campo.CANTIDAD }.valor)
        assertTrue(fields.all { it.rawCode == code })
    }

    @Test
    fun `DataMatrix y QR GS1 reconocen sus prefijos AIM`() {
        listOf("]d2", "]Q3").forEach { aim ->
            assertEquals("LOT123", valor("", Campo.LOTE, listOf("${aim}10LOT123")))
        }
    }

    @Test
    fun `GS1 sin AIM necesita encabezado identificable o separador`() {
        assertEquals("LOT123", valor("", Campo.LOTE, listOf("011061414199999610LOT123")))
        assertEquals("LOT123", valor("", Campo.LOTE, listOf("10LOT123\u001D21SER456")))
        assertTrue(LabelFieldExtractor.extraer("", listOf("102625100027")).isEmpty())
    }

    @Test
    fun `un lote GS1 variable no se divide por numeros que parecen otro AI`() {
        val code = "]C110ABC212345"
        assertEquals("ABC212345", valor("", Campo.LOTE, listOf(code)))
        assertNull(valor("", Campo.NUMERO_SERIE, listOf(code)))
    }

    @Test
    fun `GS1 rechaza campos truncados desconocidos o demasiado largos`() {
        listOf("]C101123", "]C110123456789012345678901", "]C110LOT123\u001D881234")
            .forEach { code -> assertTrue(code, LabelFieldExtractor.extraer("", listOf(code)).isEmpty()) }
    }

    @Test
    fun `GS1 parentesis preserva ceros puntuacion y el codigo original`() {
        val code = "(01)10614141999996(10)00Ab-12/3(21)000s01(37)40"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("00Ab-12/3", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("000s01", fields.first { it.campo == Campo.NUMERO_SERIE }.valor)
        val pieces = fields.first { it.campo == Campo.PIEZAS_POR_EMPAQUE }
        assertEquals("40", pieces.valor)
        assertEquals(QuantityScope.PACKAGE_CONTENT, pieces.quantityScope)
        assertEquals(code, pieces.rawCode)
    }

    @Test
    fun `OCR de identificador impreso no agrega el AI al lote`() {
        val field = LabelFieldExtractor.extraer("LOT NO (10) 2625100027").first { it.campo == Campo.LOTE }
        assertEquals("2625100027", field.valor)
        assertEquals("(10) 2625100027", field.rawValue)
    }

    @Test
    fun `codigos contradictorios no seleccionan el primero silenciosamente`() {
        val result = LabelFieldExtractor.extractDetailed("", listOf("(10)LOT123", "(10)LOT999"))
        assertTrue(result.fields.none { it.campo == Campo.LOTE })
        assertEquals(Campo.LOTE, result.conflicts.single().field)
        assertEquals(setOf("LOT123", "LOT999"), result.conflicts.single().observations.map { it.valor }.toSet())
        assertEquals(2, result.observations.size)
    }

    @Test
    fun `contradiccion entre texto y codigo queda visible para decision humana`() {
        val result = LabelFieldExtractor.extractDetailed("LOT: LOT999", listOf("(10)LOT123"))
        assertEquals(Campo.LOTE, result.conflicts.single().field)
        assertTrue(LabelFieldExtractor.extraer("LOT: LOT999", listOf("(10)LOT123")).none { it.campo == Campo.LOTE })
    }

    @Test
    fun `fuentes coincidentes conservan procedencia sin crear conflicto`() {
        val result = LabelFieldExtractor.extractDetailed("LOT: LOT123", listOf("(10)LOT123"))
        assertTrue(result.conflicts.isEmpty())
        assertEquals(Origen.GS1, result.fields.single().origen)
        assertEquals(2, result.observations.size)
    }

    @Test
    fun `dos lotes distintos en el texto tampoco se ocultan`() {
        val result = LabelFieldExtractor.extractDetailed("LOT: ABC123\nLOT: ABC999")
        assertEquals(Campo.LOTE, result.conflicts.single().field)
        assertTrue(result.fields.none { it.campo == Campo.LOTE })
    }

    @Test
    fun `etiqueta estructurada alimenta parte lote y cantidad sin cambiar valores`() {
        val code = "+30S0049106577+Q0049.200+HlotAb12+2D16102026+P"
        val fields = LabelFieldExtractor.extraer("", listOf(code))
        assertEquals("0049106577", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("lotAb12", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("0049.200", fields.first { it.campo == Campo.CANTIDAD }.valor)
        assertTrue(fields.all { it.origen == Origen.STRUCTURED_BARCODE && it.rawCode == code })
    }

    @Test
    fun `la clave leida con y sin ceros no es un conflicto y gana el codigo`() {
        // El código trae el artículo con relleno y el renglón impreso, sin él.
        val code = "+30S0075875114+Q000210+H0426090326"
        val result = LabelFieldExtractor.extractDetailed("Part Number 75875114", listOf(code))

        assertTrue(result.conflicts.none { it.field == Campo.NUMERO_PARTE })
        val parte = result.fields.single { it.campo == Campo.NUMERO_PARTE }
        assertEquals("0075875114", parte.valor)
        assertEquals(Origen.STRUCTURED_BARCODE, parte.origen)
    }

    @Test
    fun `dos claves realmente distintas siguen siendo conflicto`() {
        val result = LabelFieldExtractor.extractDetailed("Part Number 12345678", listOf("+30S0075875114+Q000210+H0426090326"))

        assertEquals(Campo.NUMERO_PARTE, result.conflicts.single().field)
    }

    @Test
    fun `la caja de Freudenberg entrega parte lote cantidad y caducidad del PDF417`() {
        val code = "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("0049263333", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("0031704192", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("00000600", fields.first { it.campo == Campo.CANTIDAD }.valor)
        assertEquals("09/2036", fields.first { it.campo == Campo.CADUCIDAD }.valor)
        assertEquals("092036", fields.first { it.campo == Campo.CADUCIDAD }.rawValue)
    }

    @Test
    fun `una fecha de ocho digitos se imprime con dia mes y anio`() {
        val fields = LabelFieldExtractor.extraer("", listOf("+30S0049106577+Q0049.200+H2613004019+2D16102026+P"))

        assertEquals("16/10/2026", fields.first { it.campo == Campo.CADUCIDAD }.valor)
    }

    @Test
    fun `lo que otro lector ubico con certeza entra como observacion`() {
        val delCodigo = LabelFieldExtractor.CampoLeido(Campo.NUMERO_PARTE, "75183644", Origen.CODIGO_PLANO, "código de barras", 99)
        val result = LabelFieldExtractor.extractDetailed("", barcodeObservations = listOf(delCodigo))

        assertEquals("75183644", result.fields.single().valor)
        assertEquals(Origen.CODIGO_PLANO, result.fields.single().origen)
    }

    @Test
    fun `en una etiqueta de codigos por campo el texto no decide lote ni cantidad`() {
        // Así salió de la bolsa real: el rótulo «QTY» queda seguido del PO y «Batch» seguido de la cantidad.
        val text = "QTY\n4505747759\nBatch\n200\nPart Number\n75183644"

        val conTexto = LabelFieldExtractor.extractDetailed(text)
        assertEquals("4505747759", conTexto.fields.single { it.campo == Campo.CANTIDAD }.valor)
        assertEquals("200", conTexto.fields.single { it.campo == Campo.LOTE }.valor)

        val sinTexto = LabelFieldExtractor.extractDetailed(text, ignoreOcrFor = setOf(Campo.LOTE, Campo.CANTIDAD))
        assertTrue(sinTexto.fields.none { it.campo == Campo.LOTE || it.campo == Campo.CANTIDAD })
        assertEquals("75183644", sinTexto.fields.single { it.campo == Campo.NUMERO_PARTE }.valor)
    }

    @Test
    fun `siguiente ancla en el mismo renglon delimita el dato`() {
        val text = "LOT: ABC123 SERIAL: S999 PART #: P123 ORDER #: PO99"
        assertEquals("ABC123", valor(text, Campo.LOTE))
        assertEquals("S999", valor(text, Campo.NUMERO_SERIE))
        assertEquals("P123", valor(text, Campo.NUMERO_PARTE))
    }

    @Test
    fun `otra ancla debajo no se captura como lote`() {
        val text = "Lot Number\nItem: F123"
        assertNull(valor(text, Campo.LOTE))
        assertEquals("F123", valor(text, Campo.NUMERO_PARTE))
    }

    @Test
    fun `anclas necesitan tambien frontera derecha`() {
        assertNull(valor("LOTTERY 123", Campo.LOTE))
        assertNull(valor("QUANTITYLESS 123", Campo.CANTIDAD))
        assertNull(valor("MODELLED A123", Campo.MODELO))
    }

    @Test
    fun `grupo de embalaje solo acepta romanos explicitos validos`() {
        listOf("I", "II", "III").forEach { group ->
            assertEquals(group, valor("Packing Group: $group", Campo.GRUPO_EMBALAJE))
        }
        listOf("L", "IIII", "IV", "3", "LLL").forEach { group ->
            assertNull(group, valor("Packing Group: $group", Campo.GRUPO_EMBALAJE))
        }
    }

    @Test
    fun `clase exige formato de clase o division sin deducirla de UN`() {
        assertEquals("3", valor("Hazard Class: 3", Campo.CLASE_HAZMAT))
        assertEquals("4.1", valor("Hazard Class: 4.1", Campo.CLASE_HAZMAT))
        listOf("L", "10", "3 LIQUIDO", ".3").forEach { hazardClass ->
            assertNull(hazardClass, valor("Hazard Class: $hazardClass", Campo.CLASE_HAZMAT))
        }
        assertNull(valor("UN1866\n3", Campo.CLASE_HAZMAT))
    }

    @Test
    fun `ordinal de empaque conserva total y no se presenta como conteo simple`() {
        val field = LabelFieldExtractor.extraer("Qty: 1 of 2").single()
        assertEquals("1", field.valor)
        assertEquals("1 of 2", field.rawValue)
        assertEquals("2", field.packageTotal)
        assertEquals(QuantityScope.PACKAGE_ORDINAL, field.quantityScope)
        assertNull(valor("Qty: 3 of 2", Campo.CANTIDAD))
    }

    @Test
    fun `marca y modelo requieren ancla o marca exacta en renglon propio`() {
        val fields = LabelFieldExtractor.extraer("BRAND: Acme\nMODEL: A-123\nLOT: X123")
        assertEquals("Acme", fields.first { it.campo == Campo.MARCA }.valor)
        assertEquals("A-123", fields.first { it.campo == Campo.MODELO }.valor)
        val brand = LabelFieldExtractor.extraer("BRIKKSEN").single()
        assertEquals("BRIKKSEN", brand.valor)
        assertEquals(Origen.KNOWN_BRAND, brand.origen)
        assertNull(valor("Description: compatible with SKF bearings", Campo.MARCA))
    }

    @Test
    fun `marca explicita y perfil distinto requieren resolver conflicto`() {
        val result = LabelFieldExtractor.extractDetailed("BRAND: SKF\nVORTEX")
        assertEquals(Campo.MARCA, result.conflicts.single().field)
        assertTrue(result.fields.none { it.campo == Campo.MARCA })
    }

    @Test
    fun `espacios dentro del valor no se usan como separador inventado`() {
        assertEquals("Acme  Industries", valor("BRAND: Acme  Industries MODEL: A123", Campo.MARCA))
        assertEquals("A123", valor("BRAND: Acme  Industries MODEL: A123", Campo.MODELO))
    }

    // ── SIGMA-ALDRICH / MERCK / SUPELCO: etiquetas reales de cliente ─────────
    @Test
    fun `etiqueta Supelco entrega parte lote y serie desde DataMatrix`() {
        val code = "(10)11368(21)300708-03(240)58298"
        val text = "Supelco.\nSUPELCOSIL LC-18\nCat#: 58298\nCol: 300708-03\nBL: 11368"
        val fields = LabelFieldExtractor.extraer(text, listOf(code))

        assertEquals("58298", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("11368", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("300708-03", fields.first { it.campo == Campo.NUMERO_SERIE }.valor)
        assertEquals("SUPELCO", fields.first { it.campo == Campo.MARCA }.valor)
    }

    @Test
    fun `sobre Sigma-Aldrich entrega parte y lote desde DataMatrix delimitado por coma`() {
        val code = "Z506729-2EA,3110"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("Z506729-2EA", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("3110", fields.first { it.campo == Campo.LOTE }.valor)
    }

    @Test
    fun `frasco Sigma-Aldrich buffer entrega parte lote y pais de origen USA con AI 422`() {
        val code = "(01)04061838260246(10)0000585854(240)T4415-1L(422)840"
        val fields = LabelFieldExtractor.extraer("Sigma-Aldrich\nREF No T4415-1L\nLot 0000585854", listOf(code))

        assertEquals("T4415-1L", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("0000585854", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("USA", fields.first { it.campo == Campo.PAIS_ORIGEN }.valor)
        assertEquals("SIGMA-ALDRICH", fields.first { it.campo == Campo.MARCA }.valor)
    }

    @Test
    fun `bolsa Sigma-Aldrich con delimitador FNC1 en crudo entrega parte y lote alfanumerico`() {
        val rawDelimited = "0104061832565750\u001D10MKCX3293\u001D240537535-1L\u001D422840"
        val fields = LabelFieldExtractor.extraer("", listOf(rawDelimited))

        assertEquals("537535-1L", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("MKCX3293", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("USA", fields.first { it.campo == Campo.PAIS_ORIGEN }.valor)
    }

    @Test
    fun `frasco TEMED de Sigma-Aldrich entrega parte lote y China con la clave del catalogo`() {
        // Código real leído de la foto del frasco T9281-50ML (11-nov-2026).
        val code = "(01)04061838260420(10)BCCP5095(240)T9281-50ML(422)156"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("T9281-50ML", fields.first { it.campo == Campo.NUMERO_PARTE }.valor)
        assertEquals("BCCP5095", fields.first { it.campo == Campo.LOTE }.valor)
        assertEquals("CHN", fields.first { it.campo == Campo.PAIS_ORIGEN }.valor)
    }

    @Test
    fun `AI 422 traduce a la clave del catalogo los origenes de las facturas inter-company`() {
        // Número ISO 3166-1 -> clave de `paises`. Países Bajos y Rumania usan ZYA y ROM en el
        // catálogo (el ISO sería NLD y ROU), por eso no se pueden deducir del estándar.
        val esperados = mapOf(
            "356" to "IND", "756" to "CHE", "376" to "ISR", "380" to "ITA", "528" to "ZYA",
            "752" to "SWE", "458" to "MYS", "040" to "AUT", "036" to "AUS", "056" to "BEL",
            "440" to "LTU", "642" to "ROM"
        )
        esperados.forEach { (numero, clave) ->
            val code = "(01)04061838260246(10)0000585854(240)T4415-1L(422)$numero"
            val fields = LabelFieldExtractor.extraer("", listOf(code))
            assertEquals("422=$numero", clave, fields.first { it.campo == Campo.PAIS_ORIGEN }.valor)
        }
    }

    @Test
    fun `AI 422 de un pais fuera de la tabla no se traduce`() {
        // Si el catálogo no lo tiene (o no se ha agregado), el número queda crudo y
        // CountryCatalogMatcher no lo empareja: no se captura solo.
        val code = "(01)04061838260246(10)0000585854(240)T4415-1L(422)999"
        val fields = LabelFieldExtractor.extraer("", listOf(code))

        assertEquals("999", fields.first { it.campo == Campo.PAIS_ORIGEN }.valor)
    }
}
