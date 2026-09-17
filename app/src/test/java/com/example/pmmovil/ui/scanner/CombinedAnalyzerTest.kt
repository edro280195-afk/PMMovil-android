package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CombinedAnalyzerTest {

    @Test
    fun `runPipeline calls quality first so barcode sees the latest state`() {
        val order = mutableListOf<String>()

        CombinedAnalyzer.runPipeline(
            onQuality = { order.add("quality") },
            onBarcode = { order.add("barcode") },
            closer = {}
        )

        assertEquals(listOf("quality", "barcode"), order)
    }

    @Test
    fun `runPipeline closes even when quality throws`() {
        var closerCount = 0
        try {
            CombinedAnalyzer.runPipeline(
                onQuality = { error("boom") },
                onBarcode = { error("should not run") },
                closer = { closerCount += 1 }
            )
        } catch (_: IllegalStateException) {
        }
        assertEquals(1, closerCount)
    }

    @Test
    fun `runPipeline still calls barcode when quality throws`() {
        var barcodeCalled = false
        var closerCount = 0
        try {
            CombinedAnalyzer.runPipeline(
                onQuality = { error("boom") },
                onBarcode = { barcodeCalled = true },
                closer = { closerCount += 1 }
            )
        } catch (_: IllegalStateException) {
        }
        assertTrue(barcodeCalled)
        assertEquals(1, closerCount)
    }

    @Test
    fun `runPipeline closes even when barcode throws`() {
        var closerCount = 0
        try {
            CombinedAnalyzer.runPipeline(
                onQuality = { },
                onBarcode = { error("boom") },
                closer = { closerCount += 1 }
            )
        } catch (_: IllegalStateException) {
        }
        assertEquals(1, closerCount)
    }

    @Test
    fun `runPipeline closes when both delegates complete`() {
        var qualityCalled = false
        var barcodeCalled = false
        var closerCount = 0

        CombinedAnalyzer.runPipeline(
            onQuality = { qualityCalled = true },
            onBarcode = { barcodeCalled = true },
            closer = { closerCount += 1 }
        )

        assertTrue(qualityCalled)
        assertTrue(barcodeCalled)
        assertEquals(1, closerCount)
    }

    @Test
    fun `runPipeline calls closer exactly once even if both delegates throw`() {
        var closerCount = 0
        try {
            CombinedAnalyzer.runPipeline(
                onQuality = { error("q") },
                onBarcode = { error("b") },
                closer = { closerCount += 1 }
            )
        } catch (_: IllegalStateException) {
        }
        assertEquals(1, closerCount)
    }
}
