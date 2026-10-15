package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.model.PartidaRevisionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PartidaMatcherTest {
    @Test
    fun `exact unique catalog number confirms only that row`() {
        val result = PartidaMatcher.match(listOf(partida(2, "ABC-20"), partida(1, "ABC-10")), listOf(part(" abc-10 ")))

        assertEquals(1, result.suggestedPartidaId)
        assertTrue(result.candidates.single().exactKeyMatch)
        assertFalse(result.requiresSelection)
    }

    @Test
    fun `customer alias must be explicitly supplied by catalog`() {
        val result = PartidaMatcher.match(listOf(partida(1, "INTERNAL-10", alias = "SUPPLIER-20")), listOf(part("SUPPLIER-20")))

        assertTrue(result.candidates.single().exactKeyMatch)
        assertEquals(PartidaMatcher.MatchKind.EXACT_CUSTOMER_ALIAS, result.candidates.single().matchKind)
    }

    @Test
    fun `same catalog number in two invoice rows requires selection`() {
        val result = PartidaMatcher.match(listOf(partida(1, "ABC-10"), partida(2, "ABC-10")), listOf(part("ABC-10")))

        assertTrue(result.requiresSelection)
        assertNull(result.suggestedPartidaId)
        assertTrue(result.candidates.none { it.exactKeyMatch })
    }

    @Test
    fun `alias colliding with another catalog number does not pick a winner`() {
        val result = PartidaMatcher.match(listOf(partida(1, "FIRST", alias = "ABC-10"), partida(2, "ABC-10")), listOf(part("ABC-10")))

        assertTrue(result.requiresSelection)
        assertTrue(result.candidates.none { it.exactKeyMatch })
    }

    @Test
    fun `removing separators never verifies identity and preserves collisions`() {
        val result = PartidaMatcher.match(listOf(partida(1, "AB-12"), partida(2, "AB12")), listOf(part("AB.12")))

        assertEquals(2, result.candidates.size)
        assertTrue(result.requiresSelection)
        assertTrue(result.candidates.none { it.exactKeyMatch })
    }

    @Test
    fun `exact separator spelling wins over a relaxed suggestion`() {
        val result = PartidaMatcher.match(listOf(partida(1, "AB-12"), partida(2, "AB12")), listOf(part("AB-12")))

        assertEquals(1, result.suggestedPartidaId)
        assertTrue(result.candidates.first().exactKeyMatch)
        assertFalse(result.candidates.last().exactKeyMatch)
    }

    @Test
    fun `leading zeros and letter digit substitutions are not silently changed`() {
        val result = PartidaMatcher.match(listOf(partida(1, "00012"), partida(2, "ABO12")), listOf(part("12"), part("AB012")))

        assertTrue(result.candidates.isEmpty())
        assertNull(result.suggestedPartidaId)
    }

    @Test
    fun `only one row remains a suggestion without verified identity`() {
        val result = PartidaMatcher.match(listOf(partida(1, "ABC-10")), emptyList())

        assertEquals(1, result.suggestedPartidaId)
        assertFalse(result.candidates.single().exactKeyMatch)
        assertTrue(result.requiresSelection)
    }

    @Test
    fun `conflicting read part numbers prevent automatic identity even if one matches`() {
        val result = PartidaMatcher.match(listOf(partida(1, "ABC-10")), listOf(part("ABC-10"), part("XYZ-99")))

        assertFalse(result.candidates.single().exactKeyMatch)
        assertTrue(result.requiresSelection)
    }

    @Test
    fun `description and raw text provide suggestions without proving identity`() {
        val result = PartidaMatcher.match(
            listOf(partida(1, "ABC-10", description = "SELLO INDUSTRIAL METALICO"), partida(2, "XYZ-99")),
            emptyList(),
            "SELLO INDUSTRIAL ABC-10"
        )

        assertEquals(1, result.candidates.size)
        assertEquals(PartidaMatcher.MatchKind.DESCRIPTION_SUPPORT, result.candidates.single().matchKind)
        assertFalse(result.candidates.single().exactKeyMatch)
    }

    @Test
    fun `quantity of pieces never selects a row whose invoice may be kilograms`() {
        val quantity = LabelFieldExtractor.CampoLeido(
            LabelFieldExtractor.Campo.CANTIDAD, "10 PCS", LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA, "QTY", 90
        )
        val result = PartidaMatcher.match(listOf(partida(1, "ABC-10"), partida(2, "XYZ-99")), listOf(quantity), "Qty: 10 PCS")

        assertTrue(result.candidates.isEmpty())
    }

    @Test
    fun `duplicate row identifiers cannot confirm identity`() {
        val result = PartidaMatcher.match(listOf(partida(1, "ABC-10"), partida(1, "XYZ-99")), listOf(part("ABC-10")))

        assertFalse(result.candidates.single().exactKeyMatch)
        assertTrue(result.requiresSelection)
    }

    private fun part(value: String) = LabelFieldExtractor.CampoLeido(
        LabelFieldExtractor.Campo.NUMERO_PARTE, value, LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA, "PART #", 90
    )

    private fun partida(id: Int, part: String, alias: String? = null, description: String? = null) = PartidaRevisionDto(
        IdRevdet = id,
        Cnofact = "FACT-$id",
        CnoParte = part,
        cDescripcion = description,
        Marca = null,
        Modelo = null,
        NumSer = null,
        NumLote = null,
        nCantFactura = 10.0,
        nCantOut = null,
        cPaisOrigen = null,
        CptCveTmateria = null,
        CptDescTmateria = null,
        Revisada = false,
        Obsrevpartida = null,
        Mnotapartida = null,
        nPeso = null,
        Cclienteid = null,
        ParteCte = alias
    )
}
