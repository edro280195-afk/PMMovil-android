package com.example.pmmovil.worker

import com.example.pmmovil.worker.EntradaCaptureNoticePolicy.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaCaptureNoticePolicyTest {

    private val minute = 60_000L

    @Test
    fun `waits for the photos of the entry while there are pending ones`() {
        assertTrue(EntradaCaptureNoticePolicy.shouldWaitForPhotos(pendingPhotos = 3, elapsedMillis = 30_000L))
    }

    @Test
    fun `does not wait when every photo is already up`() {
        assertFalse(EntradaCaptureNoticePolicy.shouldWaitForPhotos(pendingPhotos = 0, elapsedMillis = 0L))
    }

    @Test
    fun `stops waiting for photos after fifteen minutes`() {
        assertTrue(EntradaCaptureNoticePolicy.shouldWaitForPhotos(1, 14 * minute))
        assertFalse(EntradaCaptureNoticePolicy.shouldWaitForPhotos(1, 15 * minute))
        assertFalse(EntradaCaptureNoticePolicy.shouldWaitForPhotos(1, 60 * minute))
    }

    @Test
    fun `a successful response finishes the work`() {
        assertEquals(Outcome.DONE, EntradaCaptureNoticePolicy.outcomeForHttp(200, 0L))
        assertEquals(Outcome.DONE, EntradaCaptureNoticePolicy.outcomeForHttp(204, 0L))
    }

    @Test
    fun `an API without the endpoint yet is not retried`() {
        // El API de producción sin publicar contesta 404 a la ruta nueva: reintentar no arregla nada.
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(404, 0L))
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(405, 0L))
    }

    @Test
    fun `permission and validation errors are not retried`() {
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(401, 0L))
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(403, 0L))
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(400, 0L))
    }

    @Test
    fun `server hiccups are retried`() {
        assertEquals(Outcome.RETRY, EntradaCaptureNoticePolicy.outcomeForHttp(500, minute))
        assertEquals(Outcome.RETRY, EntradaCaptureNoticePolicy.outcomeForHttp(503, minute))
        assertEquals(Outcome.RETRY, EntradaCaptureNoticePolicy.outcomeForHttp(429, minute))
        assertEquals(Outcome.RETRY, EntradaCaptureNoticePolicy.outcomeForHttp(408, minute))
    }

    @Test
    fun `a network failure is retried until six hours have passed`() {
        assertEquals(Outcome.RETRY, EntradaCaptureNoticePolicy.outcomeForNetworkFailure(5 * 60 * minute))
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForNetworkFailure(6 * 60 * minute))
    }

    @Test
    fun `server errors also stop being retried after six hours`() {
        assertEquals(Outcome.GIVE_UP, EntradaCaptureNoticePolicy.outcomeForHttp(500, 7 * 60 * minute))
    }
}
