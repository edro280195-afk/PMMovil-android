package com.example.pmmovil.worker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoSyncPolicyTest {
    @Test
    fun `errores transitorios se reintentan`() {
        assertTrue(PhotoSyncPolicy.isRetryableHttp(408))
        assertTrue(PhotoSyncPolicy.isRetryableHttp(429))
        assertTrue(PhotoSyncPolicy.isRetryableHttp(500))
        assertTrue(PhotoSyncPolicy.isRetryableHttp(503))
    }

    @Test
    fun `errores de solicitud no se reintentan automaticamente`() {
        assertFalse(PhotoSyncPolicy.isRetryableHttp(400))
        assertFalse(PhotoSyncPolicy.isRetryableHttp(401))
        assertFalse(PhotoSyncPolicy.isRetryableHttp(404))
        assertFalse(PhotoSyncPolicy.isRetryableHttp(422))
    }
}
