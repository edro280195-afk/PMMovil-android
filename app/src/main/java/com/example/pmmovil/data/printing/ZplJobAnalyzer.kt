package com.example.pmmovil.data.printing

/**
 * Cuenta cuántas etiquetas de un trabajo ZPL ya se entregaron a la red cuando un envío se corta.
 *
 * Un trabajo de Entrada es una sucesión de formatos `^XA ... ^XZ`, cada uno con su `^PQ n` (copias). El Base64 del
 * `^GF` no puede contener `^`, así que `^XZ` solo aparece como cierre de formato. Con esto el mensaje de error
 * puede decir "salieron unas 12 de 18 etiquetas" en vez de dejar a la persona sin saber qué reimprimir.
 */
internal object ZplJobAnalyzer {

    /** Un formato completo: dónde termina (en bytes del trabajo) y cuántas etiquetas físicas produce. */
    data class Block(val endOffset: Int, val copies: Int)

    private val END_OF_FORMAT = byteArrayOf('^'.code.toByte(), 'X'.code.toByte(), 'Z'.code.toByte())
    private val COPIES = Regex("""\^PQ(\d+)""")

    fun blocks(job: ByteArray): List<Block> {
        val result = ArrayList<Block>()
        var blockStart = 0
        var index = 0
        while (index <= job.size - END_OF_FORMAT.size) {
            if (job.matchesAt(index, END_OF_FORMAT)) {
                val end = index + END_OF_FORMAT.size
                val text = String(job, blockStart, end - blockStart, Charsets.ISO_8859_1)
                val copies = COPIES.find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                result += Block(endOffset = end, copies = copies)
                blockStart = end
                index = end
            } else {
                index++
            }
        }
        return result
    }

    fun totalLabels(blocks: List<Block>): Int = blocks.sumOf { it.copies }

    /** Etiquetas cuyo formato completo ya está dentro de los primeros [sentBytes] bytes. */
    fun labelsSent(blocks: List<Block>, sentBytes: Int): Int =
        blocks.takeWhile { it.endOffset <= sentBytes }.sumOf { it.copies }

    private fun ByteArray.matchesAt(index: Int, pattern: ByteArray): Boolean {
        for (i in pattern.indices) if (this[index + i] != pattern[i]) return false
        return true
    }
}
