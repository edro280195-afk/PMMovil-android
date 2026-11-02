package com.example.pmmovil.data.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class StillQualityTest {

    private fun checker(w: Int, h: Int, cell: Int, lo: Int = 40, hi: Int = 215): GrayPlane {
        val data = ByteArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            data[y * w + x] = (if (((x / cell) + (y / cell)) % 2 == 0) lo else hi).toByte()
        }
        return GrayPlane(w, h, data)
    }

    private fun flat(w: Int, h: Int, value: Int) = GrayPlane(w, h, ByteArray(w * h) { value.toByte() })

    private fun boxBlur(p: GrayPlane, radius: Int): GrayPlane {
        fun pass(src: GrayPlane, horizontal: Boolean): GrayPlane {
            val out = ByteArray(src.data.size)
            for (y in 0 until src.height) for (x in 0 until src.width) {
                var sum = 0
                var n = 0
                for (k in -radius..radius) {
                    val xx = if (horizontal) (x + k).coerceIn(0, src.width - 1) else x
                    val yy = if (horizontal) y else (y + k).coerceIn(0, src.height - 1)
                    sum += src.at(xx, yy)
                    n++
                }
                out[y * src.width + x] = (sum / n).toByte()
            }
            return GrayPlane(src.width, src.height, out)
        }
        return pass(pass(p, true), false)
    }

    private fun texture(w: Int, h: Int, seed: Long): GrayPlane {
        val random = Random(seed)
        val noise = GrayPlane(w, h, ByteArray(w * h) { random.nextInt(256).toByte() })
        return boxBlur(noise, 2)
    }

    private fun shifted(p: GrayPlane, dx: Int, dy: Int): GrayPlane {
        val out = ByteArray(p.data.size)
        for (y in 0 until p.height) for (x in 0 until p.width) {
            out[y * p.width + x] = p.at((x - dx).coerceIn(0, p.width - 1), (y - dy).coerceIn(0, p.height - 1)).toByte()
        }
        return GrayPlane(p.width, p.height, out)
    }

    // --- Nitidez ---

    @Test
    fun `una foto con bordes duros es mucho mas nitida que la misma desenfocada`() {
        val sharp = StillQuality.sharpness(checker(1280, 960, 8))
        val blurred = StillQuality.sharpness(boxBlur(checker(1280, 960, 8), 5))
        assertTrue("nitida=$sharp desenfocada=$blurred", sharp > blurred * 20)
        assertTrue("la desenfocada no es cero: $blurred", blurred > 0.0)
        assertTrue("la desenfocada queda bajo el umbral de reintento: $blurred", StillBurstPolicy.needsRetry(blurred))
        assertTrue("la nitida queda sobre el umbral: $sharp", !StillBurstPolicy.needsRetry(sharp))
    }

    @Test
    fun `una superficie lisa mide cero`() {
        assertEquals(0.0, StillQuality.sharpness(flat(1280, 960, 128)), 0.0)
    }

    @Test
    fun `una imagen diminuta no se mide`() {
        assertEquals(0.0, StillQuality.sharpness(flat(6, 6, 100)), 0.0)
    }

    @Test
    fun `una sola zona nitida no basta pero tres si, igual que en el servidor`() {
        // Rejilla 4x4 y percentil 90: con una zona nitida de 16 el percentil cae en las lisas.
        val w = 1280
        val h = 960
        fun withSharpTiles(tiles: List<Pair<Int, Int>>): GrayPlane {
            val data = flat(w, h, 128).data.copyOf()
            val base = checker(w, h, 8)
            for ((col, row) in tiles) {
                for (y in row * h / 4 until (row + 1) * h / 4) for (x in col * w / 4 until (col + 1) * w / 4) {
                    data[y * w + x] = base.data[y * w + x]
                }
            }
            return GrayPlane(w, h, data)
        }

        val full = StillQuality.sharpness(checker(w, h, 8))
        val one = StillQuality.sharpness(withSharpTiles(listOf(1 to 1)))
        val three = StillQuality.sharpness(withSharpTiles(listOf(1 to 1, 2 to 1, 1 to 2)))
        // Las zonas vecinas reciben algo de borde de la nitida, pero sigue siendo ruido frente a una foto nitida.
        assertTrue("una zona nitida de 16 no alcanza: $one vs $full", one < full * 0.01)
        assertTrue("tres zonas nitidas casi igualan a la foto entera: $three vs $full", three > full * 0.8)
    }

    // --- Reduccion y gris ---

    @Test
    fun `reducir a 1280 conserva proporcion y no amplia lo que ya es chico`() {
        val big = flat(2560, 1920, 90).scaledToLongSide(1280)
        assertEquals(1280, big.width)
        assertEquals(960, big.height)
        assertEquals(90, big.at(10, 10))

        val small = flat(1000, 500, 70)
        assertSame(small, small.scaledToLongSide(1280))
    }

    @Test
    fun `reducir con proporcion no entera pesa los pixeles de borde por la fraccion que cubren`() {
        // 3 pixeles a 2: cada uno cubre 1.5 de origen. El primero toma el pixel 0 entero y la mitad del 1; el segundo, la
        // mitad del 1 y el 2 entero. Con cajas enteras traslapadas daba otra cosa y suavizaba de mas.
        val out = GrayPlane(3, 1, byteArrayOf(0, 90, 180.toByte())).resizedTo(2, 1)
        assertEquals(30, out.at(0, 0))
        assertEquals(150, out.at(1, 0))
    }

    @Test
    fun `reducir a la mitad promedia bloques de 2x2`() {
        val out = GrayPlane(
            4, 2, byteArrayOf(10, 20, 100, 110, 30, 40, 120, 130.toByte())
        ).resizedTo(2, 1)
        assertEquals(25, out.at(0, 0))
        assertEquals(115, out.at(1, 0))
    }

    @Test
    fun `reducir no cambia el brillo medio`() {
        val noise = texture(640, 480, 5)
        val small = noise.scaledToLongSide(250)
        fun mean(p: GrayPlane) = p.data.sumOf { it.toInt() and 0xFF }.toDouble() / p.data.size
        assertEquals(mean(noise), mean(small), 1.5)
    }

    @Test
    fun `reducir vertical conserva proporcion`() {
        val p = flat(1920, 2560, 50).scaledToLongSide(1280)
        assertEquals(960, p.width)
        assertEquals(1280, p.height)
    }

    @Test
    fun `la luminancia de ARGB sigue los pesos de OpenCV`() {
        val gray = GrayPlane.fromArgb(
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt()), 4, 1
        )
        assertEquals(255, gray.at(0, 0))
        assertEquals(76, gray.at(1, 0))
        assertEquals(150, gray.at(2, 0))
        assertEquals(29, gray.at(3, 0))
    }

    // --- Parecido entre escenas ---

    @Test
    fun `la misma escena es identica a si misma`() {
        val t = texture(64, 48, 1)
        assertTrue(StillQuality.similarity(t, t) > 0.99)
    }

    @Test
    fun `la misma escena con la mano movida unos pixeles sigue pareciendose`() {
        val t = texture(64, 48, 1)
        assertTrue(StillQuality.similarity(t, shifted(t, 3, 2)) > 0.9)
        assertTrue(StillQuality.similarity(t, shifted(t, -4, 5)) > 0.9)
    }

    @Test
    fun `la misma escena movida y desenfocada sigue pasando el umbral`() {
        val t = texture(64, 48, 1)
        val blurry = boxBlur(shifted(t, 2, 1), 1)
        assertTrue(StillQuality.similarity(t, blurry) >= StillBurstPolicy.MIN_SIMILARITY)
    }

    @Test
    fun `otra escena no pasa el umbral`() {
        for (seed in 2L..12L) {
            val other = StillQuality.similarity(texture(64, 48, 1), texture(64, 48, seed))
            assertTrue("semilla $seed: $other", other < StillBurstPolicy.MIN_SIMILARITY - 0.1)
        }
    }

    @Test
    fun `una escena y su negativo no se parecen`() {
        val t = texture(64, 48, 1)
        val negative = GrayPlane(t.width, t.height, ByteArray(t.data.size) { (255 - t.at(it % t.width, it / t.width)).toByte() })
        assertTrue(StillQuality.similarity(t, negative) < 0.3)
    }

    @Test
    fun `miniaturas de tamano distinto no se comparan`() {
        assertEquals(0.0, StillQuality.similarity(texture(64, 48, 1), texture(48, 64, 1)), 0.0)
    }

    @Test
    fun `dos superficies lisas del mismo tono se parecen y de tono distinto no`() {
        assertEquals(1.0, StillQuality.similarity(flat(64, 48, 120), flat(64, 48, 124)), 0.0)
        assertEquals(0.0, StillQuality.similarity(flat(64, 48, 120), flat(64, 48, 40)), 0.0)
        assertEquals(0.0, StillQuality.similarity(flat(64, 48, 120), texture(64, 48, 1)), 0.0)
    }

    @Test
    fun `la miniatura mide 64 de lado mayor`() {
        val thumb = StillQuality.thumbnail(flat(2560, 1920, 100))
        assertEquals(64, thumb.width)
        assertEquals(48, thumb.height)
    }
}
