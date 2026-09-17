package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardwareBarcodeDecoderTest {

    @Test
    fun `complete returns the scanned code and clears the buffer`() {
        val decoder = HardwareBarcodeDecoder()

        "01ABC123".forEachIndexed { index, character ->
            decoder.append(character, eventTimeMillis = index * 10L)
        }

        assertEquals("01ABC123", decoder.complete())
        assertNull(decoder.complete())
    }

    @Test
    fun `append resets an incomplete code after inactivity`() {
        val decoder = HardwareBarcodeDecoder(inactivityTimeoutMillis = 1_500L)

        decoder.append('A', eventTimeMillis = 100L)
        decoder.append('B', eventTimeMillis = 200L)
        decoder.append('C', eventTimeMillis = 2_000L)

        assertEquals("C", decoder.complete())
    }

    @Test
    fun `control characters are ignored`() {
        val decoder = HardwareBarcodeDecoder()

        decoder.append('A', eventTimeMillis = 100L)
        decoder.append('\n', eventTimeMillis = 110L)
        decoder.append('B', eventTimeMillis = 120L)

        assertEquals("AB", decoder.complete())
    }

    @Test
    fun `completeIfLikelyScan accepts fast external keyboard bursts`() {
        val decoder = HardwareBarcodeDecoder()

        "QR987654".forEachIndexed { index, character ->
            decoder.append(character, eventTimeMillis = index * 20L)
        }

        assertEquals("QR987654", decoder.completeIfLikelyScan())
        assertNull(decoder.complete())
    }

    @Test
    fun `completeIfLikelyScan rejects slow manual keyboard input`() {
        val decoder = HardwareBarcodeDecoder()

        "QR987654".forEachIndexed { index, character ->
            decoder.append(character, eventTimeMillis = index * 300L)
        }

        assertNull(decoder.completeIfLikelyScan())
        assertNull(decoder.complete())
    }

    @Test
    fun `ITScannerHID is recognized without treating virtual keyboards as scanners`() {
        assertTrue(isLikelyBarcodeScanner("ITScannerHID", isVirtual = false))
        assertTrue(isLikelyBarcodeScanner("USB Barcode Reader", isVirtual = false))
        assertFalse(isLikelyBarcodeScanner("Bluetooth Keyboard", isVirtual = false))
        assertFalse(isLikelyBarcodeScanner("ITScannerHID", isVirtual = true))
    }
}
