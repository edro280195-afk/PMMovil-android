package com.example.pmmovil.data.printing

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater

/**
 * Convierte un bitmap monocromo en un campo grafico ZPL ^GF.
 *
 * - Empaqueta el bitmap a 1 bit por pixel (1 = negro), filas alineadas a byte.
 * - Comprime con Z64 (DEFLATE/zlib + Base64 + CRC-16), el mismo formato que usan
 *   metafloor/zpl-image, Labelary y la propia ZT411.
 * - Alternativa sin comprimir: hex ASCII plano (util para depurar en Labelary o
 *   descartar problemas de compresion).
 *
 * El nucleo (packMonochrome / graphicField) es Kotlin/JVM puro y se puede probar
 * sin dispositivo; solo encode(Bitmap) toca android.graphics.
 */
object ZplGraphicEncoder {

    /**
     * Produce una representación visual del mismo raster de 1 bit que se empaqueta en ^GF.
     * El diseñador la usa para que el suavizado de Canvas no difiera de lo que recibe la Zebra.
     */
    fun toMonochromeBitmap(bitmap: Bitmap, threshold: Int = 128): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val source = IntArray(width * height)
        bitmap.getPixels(source, 0, width, 0, 0, width, height)
        val output = IntArray(source.size) { index ->
            if (isBlack(source[index], threshold)) BLACK else WHITE
        }
        return Bitmap.createBitmap(output, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Renderiza un bitmap ARGB completo como campo ^GFA (sin ^FO ni ^FS). */
    fun encode(bitmap: Bitmap, compress: Boolean = true, threshold: Int = 128): String {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val rowBytes = packMonochrome(pixels, width, height, threshold)
        return graphicField(rowBytes, width, height, compress)
    }

    /** ARGB -> 1 bit por pixel alineado a byte por fila (MSB = pixel izquierdo, 1 = negro). */
    fun packMonochrome(argb: IntArray, width: Int, height: Int, threshold: Int = 128): ByteArray {
        val bytesPerRow = (width + 7) / 8
        val out = ByteArray(bytesPerRow * height)
        for (y in 0 until height) {
            val rowStart = y * bytesPerRow
            val pixRow = y * width
            for (x in 0 until width) {
                if (isBlack(argb[pixRow + x], threshold)) {
                    val byteIndex = rowStart + (x ushr 3)
                    val bit = 0x80 ushr (x and 7)
                    out[byteIndex] = (out[byteIndex].toInt() or bit).toByte()
                }
            }
        }
        return out
    }

    /**
     * Envuelve los bytes 1bpp en un campo ^GFA listo para insertar en el ZPL.
     * Para Z64/hex, b y c son el conteo de bytes SIN comprimir; d es bytes por fila.
     */
    fun graphicField(rowBytes: ByteArray, width: Int, height: Int, compress: Boolean): String {
        val bytesPerRow = (width + 7) / 8
        val total = rowBytes.size
        val data = if (compress) {
            val deflated = deflate(rowBytes)
            val b64 = Base64.getEncoder().encodeToString(deflated)
            ":Z64:$b64:${crc16Hex(b64)}"
        } else {
            toHex(rowBytes)
        }
        return "^GFA,$total,$total,$bytesPerRow,$data"
    }

    private fun isBlack(pixel: Int, threshold: Int): Boolean {
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha < 128) return false // transparente = blanco
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF
        val luminance = (r * 299 + g * 587 + b * 114) / 1000
        return luminance < threshold
    }

    private fun deflate(input: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(input)
        deflater.finish()
        val buffer = ByteArray(4096)
        val out = ByteArrayOutputStream(input.size / 2 + 64)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            out.write(buffer, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    /** CRC-16-CCITT (poly 0x1021, init 0x0000), MSB-first, sobre los bytes ASCII del Base64. */
    private fun crc16Hex(ascii: String): String {
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

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789ABCDEF".toCharArray()
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
