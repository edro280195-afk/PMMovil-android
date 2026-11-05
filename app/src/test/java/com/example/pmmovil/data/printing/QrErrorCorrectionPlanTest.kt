package com.example.pmmovil.data.printing

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cubre el hallazgo de la etiqueta 9 de 17 que salió sin QR (5-nov-2026): ZXing codifica un QR válido que su propio
 * decodificador no lee de vuelta con cierto nivel de corrección, y el estilo clásico solo probaba un nivel.
 */
class QrErrorCorrectionPlanTest {

    /** Dibuja la matriz tal cual la dibuja StyledQrRenderer con módulos cuadrados (1 módulo de silencio) y la decodifica. */
    private fun decodes(value: String, level: ErrorCorrectionLevel): Boolean {
        val matrix = Encoder.encode(value, level).matrix
        val modules = matrix.width
        val quiet = 1
        val total = modules + quiet * 2
        val moduleSize = (520 / total).coerceAtLeast(1)
        val side = moduleSize * total
        val pixels = IntArray(side * side) { 0xFFFFFFFF.toInt() }
        for (row in 0 until modules) {
            for (col in 0 until modules) {
                if (matrix.get(col, row).toInt() != 1) continue
                for (dy in 0 until moduleSize) {
                    for (dx in 0 until moduleSize) {
                        pixels[((quiet + row) * moduleSize + dy) * side + (quiet + col) * moduleSize + dx] = 0xFF000000.toInt()
                    }
                }
            }
        }
        return runCatching {
            val source = RGBLuminanceSource(side, side, pixels)
            val result = QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(source)),
                mapOf(DecodeHintType.TRY_HARDER to true)
            )
            result.text == value
        }.getOrDefault(false)
    }

    @Test
    fun `con logo solo se prueba el nivel alto`() {
        assertEquals(
            listOf(ErrorCorrectionLevel.H),
            QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.H, hasLogo = true)
        )
    }

    @Test
    fun `sin logo el preferido va primero y se prueban los cuatro niveles`() {
        val fromM = QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false)
        assertEquals(ErrorCorrectionLevel.M, fromM.first())
        assertEquals(ErrorCorrectionLevel.values().toSet(), fromM.toSet())
        assertEquals(4, fromM.size)

        val fromQ = QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.Q, hasLogo = false)
        assertEquals(ErrorCorrectionLevel.Q, fromQ.first())
        assertEquals(4, fromQ.size)
    }

    @Test
    fun `el nivel bajo es el ultimo recurso`() {
        assertEquals(ErrorCorrectionLevel.L, QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false).last())
        assertEquals(ErrorCorrectionLevel.L, QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.Q, hasLogo = false).last())
    }

    @Test
    fun `un QR que ZXing no lee con el nivel M se rescata con otro nivel`() {
        // Valor real del hallazgo: version 2, mascara 3 con M; otro lector (OpenCV) sí lo lee, ZXing no.
        val value = "01C2636546600000 9-17"
        assertFalse("Si esto pasa, ZXing ya corrigió el falso negativo y la prueba debe actualizarse", decodes(value, ErrorCorrectionLevel.M))

        val rescued = QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false)
            .firstOrNull { decodes(value, it) }
        assertTrue("Ningún nivel de corrección logró verificar el QR", rescued != null)
    }

    @Test
    fun `la etiqueta 9 de la entrada que salio sin QR se rescata`() {
        // La entrada real del 4-nov-2026 (01C263614050000, 17 bultos): su etiqueta 9 fue la que salió sin QR.
        val value = "01C263614050000 9-17"
        assertFalse("ZXing ya no falla con este valor: la prueba debe actualizarse", decodes(value, ErrorCorrectionLevel.M))
        assertTrue(
            QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false).any { decodes(value, it) }
        )
        // Las otras 17 etiquetas de esa entrada (carpeta y bultos) sí pasaban con M o se rescatan igual.
        (listOf("01C263614050000") + (1..17).map { "01C263614050000 $it-17" }).forEach { label ->
            assertTrue(
                "Sin nivel que verifique para $label",
                QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false).any { decodes(label, it) }
            )
        }
    }

    @Test
    fun `una tanda de 17 etiquetas siempre encuentra un nivel que verifica`() {
        // Barrido determinista de entradas de 17 bultos: el viejo camino (solo M) fallaba en alguna; el nuevo, nunca.
        var failuresWithOnlyM = 0
        for (seed in 0 until 600) {
            val reference = "01C2636%08d".format(seed * 163_841 % 100_000_000)
            val values = listOf(reference) + (1..17).map { "$reference $it-17" }
            for (value in values) {
                if (!decodes(value, ErrorCorrectionLevel.M)) failuresWithOnlyM++
                val rescued = QrErrorCorrectionPlan.levelsToTry(ErrorCorrectionLevel.M, hasLogo = false)
                    .any { decodes(value, it) }
                assertTrue("Sin nivel que verifique para \"$value\"", rescued)
            }
        }
        // Documenta que el problema existe (si llega a 0, el barrido ya no demuestra nada y hay que ampliarlo).
        assertTrue("El barrido no encontró ningún falso negativo de ZXing con M", failuresWithOnlyM > 0)
    }
}
