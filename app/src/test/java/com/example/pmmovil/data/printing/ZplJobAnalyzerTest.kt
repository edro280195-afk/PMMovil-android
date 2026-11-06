package com.example.pmmovil.data.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZplJobAnalyzerTest {

    private fun label(copies: Int? = null, payload: String = "^FO0,0^GFA,8,8,1,:Z64:eJzLAAAAAQAB:ABCD^FS"): String =
        buildString {
            append("^XA\n^PW1200\n^LL1800\n^LH0,0\n")
            append(payload).append('\n')
            if (copies != null) append("^PQ").append(copies).append('\n')
            append("^XZ")
        }

    private fun job(vararg labels: String): ByteArray = labels.joinToString("\n").toByteArray(Charsets.UTF_8)

    @Test
    fun `cuenta un formato por etiqueta y suma las copias`() {
        val blocks = ZplJobAnalyzer.blocks(job(label(1), label(3), label()))

        assertEquals(3, blocks.size)
        assertEquals(listOf(1, 3, 1), blocks.map { it.copies })
        assertEquals("Sin ^PQ cuenta como una etiqueta", 5, ZplJobAnalyzer.totalLabels(blocks))
    }

    @Test
    fun `solo cuenta como entregada la etiqueta cuyo formato llego completo`() {
        val bytes = job(label(1), label(1), label(1))
        val blocks = ZplJobAnalyzer.blocks(bytes)

        assertEquals(0, ZplJobAnalyzer.labelsSent(blocks, 0))
        assertEquals("A mitad del primer formato todavia no hay ninguna", 0,
            ZplJobAnalyzer.labelsSent(blocks, blocks[0].endOffset - 1))
        assertEquals(1, ZplJobAnalyzer.labelsSent(blocks, blocks[0].endOffset))
        assertEquals("Dos formatos completos y el tercero a medias", 2,
            ZplJobAnalyzer.labelsSent(blocks, blocks[2].endOffset - 10))
        assertEquals(3, ZplJobAnalyzer.labelsSent(blocks, bytes.size))
    }

    @Test
    fun `las copias de una etiqueta entregada cuentan completas`() {
        val blocks = ZplJobAnalyzer.blocks(job(label(4), label(2)))

        assertEquals(4, ZplJobAnalyzer.labelsSent(blocks, blocks[0].endOffset))
        assertEquals(6, ZplJobAnalyzer.labelsSent(blocks, blocks[1].endOffset))
    }

    @Test
    fun `un trabajo sin cierres de formato no se interpreta`() {
        val blocks = ZplJobAnalyzer.blocks("^XA^FDhola^FS".toByteArray())

        assertTrue(blocks.isEmpty())
        assertEquals(0, ZplJobAnalyzer.totalLabels(blocks))
        assertEquals(0, ZplJobAnalyzer.labelsSent(blocks, 100))
    }

    @Test
    fun `un trabajo real de varios cientos de kilobytes se recorre sin perder formatos`() {
        val heavy = "A".repeat(40_000)
        val bytes = job(*Array(20) { label(1, payload = "^FO0,0^GFA,1,1,1,:Z64:$heavy:FFFF^FS") })

        val blocks = ZplJobAnalyzer.blocks(bytes)

        assertEquals(20, blocks.size)
        assertEquals(20, ZplJobAnalyzer.totalLabels(blocks))
        assertEquals(bytes.size, blocks.last().endOffset)
    }
}
