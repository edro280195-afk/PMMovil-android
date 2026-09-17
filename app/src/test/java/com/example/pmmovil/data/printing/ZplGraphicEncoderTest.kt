package com.example.pmmovil.data.printing

import java.util.Base64
import java.util.zip.Inflater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prueba el nucleo del encoder ^GF sin tocar android.graphics: empaquetado 1bpp,
 * hex plano y round-trip completo de Z64 (deflate + Base64 + CRC-16).
 */
class ZplGraphicEncoderTest {

    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test
    fun `packMonochrome usa MSB primero y 1 = negro`() {
        // negro,blanco alternando -> 10101010 = 0xAA
        val row = intArrayOf(black, white, black, white, black, white, black, white)
        val packed = ZplGraphicEncoder.packMonochrome(row, width = 8, height = 1)
        assertEquals(1, packed.size)
        assertEquals(0xAA.toByte(), packed[0])
    }

    @Test
    fun `packMonochrome alinea cada fila a byte completo`() {
        // width 12 -> 2 bytes por fila (4 bits de padding en blanco)
        val row = IntArray(12) { if (it == 0 || it == 11) black else white }
        val packed = ZplGraphicEncoder.packMonochrome(row, width = 12, height = 1)
        assertEquals(2, packed.size)
        assertEquals(0x80.toByte(), packed[0]) // pixel 0 -> bit MSB
        assertEquals(0x10.toByte(), packed[1]) // pixel 11 -> 0x80 ushr (11 and 7)=0x10
    }

    @Test
    fun `graphicField sin comprimir emite hex plano`() {
        val rowBytes = byteArrayOf(0xAB.toByte(), 0xCD.toByte())
        val field = ZplGraphicEncoder.graphicField(rowBytes, width = 16, height = 1, compress = false)
        assertEquals("^GFA,2,2,2,ABCD", field)
    }

    @Test
    fun `graphicField Z64 hace round-trip por inflate y CRC`() {
        val w = 32
        val h = 32
        val pix = IntArray(w * h) { idx ->
            val x = idx % w
            val y = idx / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) black else white
        }
        val packed = ZplGraphicEncoder.packMonochrome(pix, w, h)
        val field = ZplGraphicEncoder.graphicField(packed, w, h, compress = true)

        val prefix = "^GFA,${packed.size},${packed.size},${(w + 7) / 8},:Z64:"
        assertTrue("Prefijo inesperado: $field", field.startsWith(prefix))
        val payload = field.removePrefix(prefix)
        val b64 = payload.substringBeforeLast(':')
        val crc = payload.substringAfterLast(':')

        // Inflar el Base64 debe reconstruir exactamente los bytes empaquetados.
        val deflated = Base64.getDecoder().decode(b64)
        val inflater = Inflater()
        inflater.setInput(deflated)
        val restored = ByteArray(packed.size)
        val n = inflater.inflate(restored)
        inflater.end()
        assertEquals(packed.size, n)
        assertTrue(packed.contentEquals(restored))

        // El CRC-16 del sufijo debe coincidir con el recomputado sobre el Base64.
        assertEquals(crc16(b64), crc)
    }

    private fun crc16(ascii: String): String {
        var crc = 0
        for (ch in ascii) {
            crc = crc xor ((ch.code and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return "%04X".format(crc)
    }
}
