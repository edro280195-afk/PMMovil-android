package com.example.pmmovil.data.scanner

import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareScannerIntentsTest {

    @Test
    fun `Honeywell CT60 scan result action is registered`() {
        assertTrue(
            HardwareScannerIntents.scannerActions.contains(
                HardwareScannerIntents.ACTION_HONEYWELL_SCAN_RESULT
            )
        )
    }
}
