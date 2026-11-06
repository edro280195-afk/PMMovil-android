package com.example.pmmovil.data.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraHostStatusParserTest {

    /** Respuesta de ~HS: tres líneas entre STX (0x02) y ETX (0x03), cada una terminada en CR LF. */
    private fun reply(first: String, second: String, third: String = "1234,0"): String =
        listOf(first, second, third).joinToString(separator = "") { "\u0002$it\u0003\r\n" }

    private val ready = "030,0,0,1200,000,0,0,0,000,0,0,0"
    private val readySecond = "000,0,0,1,2,0,0,000,00000000,1,000"

    @Test
    fun `una impresora lista no tiene impedimentos`() {
        val status = ZebraHostStatusParser.parse(reply(ready, readySecond))

        assertNotNull(status)
        assertNull(status!!.blockingProblem())
        assertFalse(status.paused)
        assertFalse(status.partialFormat)
        assertEquals(0, status.formatsInBuffer)
        assertTrue(status.describe().contains("lista"))
    }

    @Test
    fun `detecta la pausa`() {
        val status = ZebraHostStatusParser.parse(reply("030,0,1,1200,000,0,0,0,000,0,0,0", readySecond))!!

        assertTrue(status.paused)
        assertTrue(status.blockingProblem()!!.contains("pausa"))
    }

    @Test
    fun `detecta que se acabaron las etiquetas`() {
        val status = ZebraHostStatusParser.parse(reply("030,1,0,1200,000,0,0,0,000,0,0,0", readySecond))!!

        assertTrue(status.paperOut)
        assertTrue(status.blockingProblem()!!.contains("etiquetas"))
    }

    @Test
    fun `detecta el cabezal abierto antes que cualquier otra cosa`() {
        val status = ZebraHostStatusParser.parse(
            reply("030,1,1,1200,000,0,0,0,000,0,0,0", "000,1,0,1,2,0,0,000,00000000,1,000")
        )!!

        assertTrue(status.headOpen)
        assertTrue(status.blockingProblem()!!.contains("cabezal"))
    }

    @Test
    fun `sin ribbon solo importa en transferencia termica`() {
        val transfer = ZebraHostStatusParser.parse(
            reply(ready, "000,0,1,1,2,0,0,000,00000000,1,000")
        )!!
        val direct = ZebraHostStatusParser.parse(
            reply(ready, "000,0,1,0,2,0,0,000,00000000,1,000")
        )!!

        assertTrue(transfer.blockingProblem()!!.contains("ribbon"))
        assertNull("En termica directa el ribbon no aplica", direct.blockingProblem())
    }

    @Test
    fun `una etiqueta a medias o la memoria llena se informan pero no bloquean`() {
        val status = ZebraHostStatusParser.parse(
            reply("030,0,0,1200,007,1,0,1,000,0,0,0", readySecond)
        )!!

        assertNull("Un falso positivo aqui dejaria a la planta sin imprimir", status.blockingProblem())
        assertTrue(status.partialFormat)
        assertTrue(status.bufferFull)
        assertEquals(7, status.formatsInBuffer)
        val text = status.describe()
        assertTrue(text, text.contains("a medias"))
        assertTrue(text, text.contains("llena"))
        assertTrue(text, text.contains("7"))
    }

    @Test
    fun `ante una respuesta que no tiene la forma documentada no inventa nada`() {
        assertNull(ZebraHostStatusParser.parse(""))
        assertNull(ZebraHostStatusParser.parse("hola"))
        assertNull("Una sola linea no alcanza", ZebraHostStatusParser.parse("\u0002$ready\u0003\r\n"))
        assertNull(
            "Una bandera que no es 0 o 1 es una respuesta ajena",
            ZebraHostStatusParser.parse(reply("030,x,0,1200,000,0,0,0,000,0,0,0", readySecond))
        )
        assertNull(
            "Campos de menos",
            ZebraHostStatusParser.parse(reply("030,0,0", readySecond))
        )
    }

    @Test
    fun `tolera respuestas sin STX y ETX o con saltos de linea distintos`() {
        val plain = "$ready\n$readySecond\n1234,0\n"

        assertNotNull(ZebraHostStatusParser.parse(plain))
        assertNotNull(ZebraHostStatusParser.parse(plain.replace("\n", "\r\n")))
    }
}
