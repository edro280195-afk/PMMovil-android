package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.ProductoResumen
import com.example.pmmovil.data.model.PendingBarcodeScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RevisionBarcodeRulesTest {

    @Test
    fun `manual input requires expected length when configured`() {
        val shortStatus = barcodeManualInputStatus("ABC12", expectedLength = 8)
        val exactStatus = barcodeManualInputStatus("ABC12345", expectedLength = 8)

        assertFalse(shortStatus.canSubmit)
        assertEquals("5/8 caracteres. Faltan 3.", shortStatus.helperText)
        assertTrue(exactStatus.canSubmit)
        assertTrue(exactStatus.isExactLength)
    }

    @Test
    fun `manual input accepts longer values because backend receives raw scan`() {
        val status = barcodeManualInputStatus("ABC123456789", expectedLength = 8)

        assertTrue(status.canSubmit)
        assertTrue(status.isOverLength)
        assertEquals(12, status.length)
    }

    @Test
    fun `duplicate scanner event is ignored during the duplicate window`() {
        assertTrue(
            isBarcodeDuplicateTooSoon(
                normalizedCode = "105431",
                nowMillis = 1_500L,
                previousSameCodeAtMillis = 1_000L,
                lastAcceptedAtMillis = 1_000L
            )
        )
        assertFalse(
            isBarcodeDuplicateTooSoon(
                normalizedCode = "105431",
                nowMillis = 1_800L,
                previousSameCodeAtMillis = 1_000L,
                lastAcceptedAtMillis = 1_000L
            )
        )
    }

    @Test
    fun `different scanner event immediately after a read is also ignored`() {
        assertTrue(
            isBarcodeDuplicateTooSoon(
                normalizedCode = "105842",
                nowMillis = 1_050L,
                previousSameCodeAtMillis = null,
                lastAcceptedAtMillis = 1_000L
            )
        )
    }

    @Test
    fun `product filter separates pending recognized and unknown rows`() {
        val products = listOf(
            ProductoResumen(cidproduct = "P1", existe = true, pendientes = 0),
            ProductoResumen(cidproduct = "P2", cantidad = 1, existe = false, pendientes = 1),
            ProductoResumen(cidproduct = "P3", cantidad = 1, existe = false, pendientes = 0)
        )

        assertEquals(listOf("P2"), filterBarcodeProducts(products, BarcodeProductFilter.PENDING).map { it.cidproduct })
        assertEquals(listOf("P1"), filterBarcodeProducts(products, BarcodeProductFilter.RECOGNIZED).map { it.cidproduct })
        assertEquals(listOf("P3"), filterBarcodeProducts(products, BarcodeProductFilter.UNKNOWN).map { it.cidproduct })
    }

    @Test
    fun `pending scans from another or unknown session are discarded`() {
        val pending = listOf(
            PendingBarcodeScan("current", "P1", 100L, sessionId = 12),
            PendingBarcodeScan("old-session", "P2", 110L, sessionId = 11),
            PendingBarcodeScan("same-session", "P3", 90L, sessionId = 12),
            PendingBarcodeScan("legacy", "P4", 120L)
        )

        val result = pendingScansForSession(
            pendingScans = pending,
            sessionId = 12
        )

        assertEquals(listOf("current", "same-session"), result.map { it.id })
    }

    @Test
    fun `finalize blocker explains the first blocking condition`() {
        assertEquals(
            "Escanea al menos un producto.",
            barcodeFinalizeBlocker(
                totalProducts = 0,
                pendingCount = 0,
                isLoading = false,
                isFinalizing = false,
                hasError = false
            )
        )
        assertEquals(
            "Espera 2 lectura(s) por sincronizar.",
            barcodeFinalizeBlocker(
                totalProducts = 3,
                pendingCount = 2,
                isLoading = false,
                isFinalizing = false,
                hasError = false
            )
        )
        assertNull(
            barcodeFinalizeBlocker(
                totalProducts = 3,
                pendingCount = 0,
                isLoading = false,
                isFinalizing = false,
                hasError = false
            )
        )
    }
}
