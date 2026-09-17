package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameQualityAnalyzerTest {

    @Test
    fun `empty buffer returns Empty quality`() {
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = ByteArray(0),
            sampleW = 0,
            sampleH = 0
        )
        assertEquals(FrameQuality.Empty, quality)
    }

    @Test
    fun `uniform white image is detected as TooBright and Uniform`() {
        val samples = ByteArray(80 * 60) { 240.toByte() }
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = samples,
            sampleW = 80,
            sampleH = 60
        )
        assertTrue("expected too bright but avg=${quality.avgLuminance}", quality.isTooBright)
        assertTrue(quality.isUniform)
        assertFalse(quality.hasContent)
    }

    @Test
    fun `uniform dark image is detected as TooDark`() {
        val samples = ByteArray(80 * 60) { 12.toByte() }
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = samples,
            sampleW = 80,
            sampleH = 60
        )
        assertTrue(quality.isTooDark)
        assertTrue(quality.isUniform)
        assertFalse(quality.hasContent)
    }

    @Test
    fun `flat mid gray is uniform but not dark or bright`() {
        val samples = ByteArray(80 * 60) { 128.toByte() }
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = samples,
            sampleW = 80,
            sampleH = 60
        )
        assertTrue(quality.isUniform)
        assertFalse(quality.isTooDark)
        assertFalse(quality.isTooBright)
        assertFalse(quality.hasContent)
    }

    @Test
    fun `random noise produces edges and content`() {
        val rng = java.util.Random(42)
        val samples = ByteArray(80 * 60) { (rng.nextInt(256)).toByte() }
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = samples,
            sampleW = 80,
            sampleH = 60
        )
        assertFalse(quality.isUniform)
        assertTrue("edgeRatio=${quality.edgeRatio}", quality.edgeRatio > 0.05)
        assertTrue(quality.hasContent)
    }

    @Test
    fun `rectangle in center is detected with correct center and size`() {
        val samples = ByteArray(80 * 60) { 60.toByte() }
        val gridSize = 4
        val cellWidth = 80 / gridSize
        val cellHeight = 60 / gridSize
        for (gy in 1 until 3) {
            for (gx in 1 until 3) {
                val xStart = gx * cellWidth
                val yStart = gy * cellHeight
                val xEnd = (gx + 1) * cellWidth
                val yEnd = (gy + 1) * cellHeight
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val isBorder = x < xStart + 2 || x >= xEnd - 2 ||
                            y < yStart + 2 || y >= yEnd - 2
                        samples[y * 80 + x] = if (isBorder) 220.toByte() else 60.toByte()
                    }
                }
            }
        }
        val quality = FrameQualityAnalyzer.analyzeLuminanceBuffer(
            samples = samples,
            sampleW = 80,
            sampleH = 60,
            gridSize = gridSize
        )
        assertTrue(quality.hasContent)
        assertTrue(
            "expected contentCenterX near 0.5 but was ${quality.contentCenterX}",
            kotlin.math.abs(quality.contentCenterX - 0.5f) < 0.2f
        )
        assertTrue(
            "expected contentCenterY near 0.5 but was ${quality.contentCenterY}",
            kotlin.math.abs(quality.contentCenterY - 0.5f) < 0.2f
        )
    }

    @Test
    fun `frameState mapping covers all branches`() {
        val darkUniform = FrameQuality.Empty.copy(isTooDark = true, isUniform = true)
        assertEquals(FrameState.TooDark, darkUniform.toFrameState())
        val brightUniform = FrameQuality.Empty.copy(isTooBright = true, isUniform = true)
        assertEquals(FrameState.TooBright, brightUniform.toFrameState())
        val blurry = FrameQuality.Empty.copy(
            isUniform = false,
            isBlurry = true,
            edgeRatio = 0.005,
            hasContent = true
        )
        assertEquals(FrameState.Blurry, blurry.toFrameState())
        val noContent = FrameQuality.Empty.copy(hasContent = false)
        assertEquals(FrameState.NoContent, noContent.toFrameState())
        val almostThere = FrameQuality.Empty.copy(
            hasContent = true,
            edgeRatio = 0.1,
            variance = 0.1
        )
        assertEquals(FrameState.AlmostThere, almostThere.toFrameState())
        val searching = FrameQuality.Empty.copy(
            hasContent = true,
            edgeRatio = 0.5,
            variance = 0.5
        )
        assertEquals(FrameState.Searching, searching.toFrameState())
    }
}
