package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.random.Random

class DepthLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val k = Intrinsics(125.0, 124.5, 80.2, 44.8, 160, 90)

    private fun parts(seed: Int): Triple<ShortArray, ByteArray, ShortArray> {
        val rnd = Random(seed)
        val n = k.width * k.height
        val raw = ShortArray(n) { if (rnd.nextInt(5) == 0) 0 else (500 + rnd.nextInt(3500)).toShort() }
        val conf = ByteArray(n) { rnd.nextInt(256).toByte() }
        val smooth = ShortArray(n) { (400 + rnd.nextInt(4000)).toShort() }
        return Triple(raw, conf, smooth)
    }

    private fun pose(i: Int) = Se3.fromQuaternion(0.1 * i, 0.2, -0.3, 0.9, Vec3(1.0 + i, 1.4, -2.5 * i))

    @Test
    fun framesWithPartsRoundTripExactly() {
        val file = tmp.newFile("profundidad.bin")
        val written = (0 until 5).map { i ->
            val (raw, conf, smooth) = parts(i)
            val merged = DepthMerge.merge(DepthMode.MIXED, raw, conf, smooth)!!
            DepthFrame(k.width, k.height, merged.depthMm, merged.confidence, k, pose(i), 1000L + i, raw, conf, smooth)
        }
        DepthLogWriter(file).use { w -> written.forEach { w.write(it) } }

        val back = DepthLogReader.readAll(file)
        assertEquals(5, back.size)
        for (i in 0 until 5) {
            val a = written[i]
            val b = back[i]
            assertEquals(a.timestampNs, b.timestampNs)
            assertEquals(a.width, b.width)
            assertEquals(a.height, b.height)
            assertArrayEquals(a.rawMm, b.rawMm)
            assertArrayEquals(a.rawConfidence, b.rawConfidence)
            assertArrayEquals(a.smoothMm, b.smoothMm)
            assertArrayEquals("la mixta se rearma igual", a.depthMm, b.depthMm)
            assertArrayEquals(a.confidence, b.confidence)
            assertEquals(a.intrinsics, b.intrinsics)
            assertEquals(0.0, a.worldFromCamera.t.distanceTo(b.worldFromCamera.t), 1e-12)
            assertEquals(0.0, a.worldFromCamera.rotationAngleTo(b.worldFromCamera), 1e-6)
        }
    }

    @Test
    fun framesWithoutPartsAreStoredAsTheyAre() {
        val file = tmp.newFile("sola.bin")
        val (raw, conf, _) = parts(7)
        val frame = DepthFrame(k.width, k.height, raw, conf, k, pose(1), 55L)
        DepthLogWriter(file).use { it.write(frame) }
        val back = DepthLogReader.readAll(file).single()
        assertArrayEquals(raw, back.depthMm)
        assertArrayEquals(conf, back.confidence)
        assertNull(back.rawMm)
        assertNull(back.smoothMm)
    }

    @Test
    fun aTruncatedTailIsIgnoredAndTheRestIsKept() {
        val file = tmp.newFile("cortado.bin")
        DepthLogWriter(file).use { w ->
            for (i in 0 until 4) {
                val (raw, conf, smooth) = parts(i)
                w.write(DepthFrame(k.width, k.height, raw, conf, k, pose(i), i.toLong(), raw, conf, smooth))
            }
        }
        val bytes = file.readBytes()
        file.writeBytes(bytes.copyOf(bytes.size - 5000))
        val back = DepthLogReader.readAll(file)
        assertEquals(3, back.size)
    }

    @Test
    fun theLogIsMuchSmallerThanTheRawArrays() {
        val file = tmp.newFile("tamano.bin")
        val n = k.width * k.height
        // Una pared lisa con un poco de ruido: lo típico, y comprime bien.
        val rnd = Random(3)
        val raw = ShortArray(n) { (2000 + rnd.nextInt(30)).toShort() }
        val conf = ByteArray(n) { (200 + rnd.nextInt(50)).toByte() }
        val smooth = ShortArray(n) { (2000 + rnd.nextInt(10)).toShort() }
        val writer = DepthLogWriter(file)
        repeat(10) { writer.write(DepthFrame(k.width, k.height, raw, conf, k, pose(it), it.toLong(), raw, conf, smooth)) }
        writer.close()
        assertEquals(10, writer.frames)
        assertTrue("archivo ${file.length()} bytes", file.length() < 10L * n * 5 / 2)
    }

    @Test
    fun mixedModeUsesRawWhereConfidentAndSmoothToFillTheRest() {
        val raw = shortArrayOf(1000, 0, 2000, 3000)
        // La cruda sirve desde la confianza 1; el píxel 2 (confianza 0: ARCore no midió) se rellena con la suave.
        val conf = byteArrayOf(200.toByte(), 250.toByte(), 0, 120)
        val smooth = shortArrayOf(1100, 1500, 2100, 0)
        val m = DepthMerge.merge(DepthMode.MIXED, raw, conf, smooth)!!
        assertArrayEquals(shortArrayOf(1000, 1500, 2100, 3000), m.depthMm)
        assertEquals(200, m.confidence!![0].toInt() and 0xFF)
        assertEquals(DepthMerge.FILL_CONFIDENCE, m.confidence!![1].toInt() and 0xFF)
        assertEquals(DepthMerge.FILL_CONFIDENCE, m.confidence!![2].toInt() and 0xFF)
        assertEquals(120, m.confidence!![3].toInt() and 0xFF)

        // Solo cruda: se descarta la de confianza 0 (píxel 2).
        assertArrayEquals(shortArrayOf(1000, 0, 0, 3000), DepthMerge.merge(DepthMode.RAW, raw, conf, smooth)!!.depthMm)
        assertNull(DepthMerge.merge(DepthMode.SMOOTH, raw, conf, null))
        assertNull(DepthMerge.merge(DepthMode.RAW, null, null, smooth))
        assertNotNull(DepthMerge.merge(DepthMode.MIXED, null, null, smooth))
        assertArrayEquals(smooth, DepthMerge.merge(DepthMode.SMOOTH, raw, conf, smooth)!!.depthMm)
    }
}
