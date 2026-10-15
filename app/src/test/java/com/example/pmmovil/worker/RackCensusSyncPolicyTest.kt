package com.example.pmmovil.worker

import com.example.pmmovil.data.model.RackCensusStatus
import com.google.gson.JsonParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class RackCensusSyncPolicyTest {
    private val sessionId = "3583d20a-27cb-4af6-9f59-3e840ba7b8ae"

    @Test
    fun `only a uuid identifies a census session`() {
        assertTrue(RackCensusSyncPolicy.isValidSessionId(sessionId))
        assertFalse(RackCensusSyncPolicy.isValidSessionId("R1"))
        assertFalse(RackCensusSyncPolicy.isValidSessionId("$sessionId "))
        assertFalse(RackCensusSyncPolicy.isValidSessionId(""))
    }

    @Test
    fun `the session belongs to the user that opened it`() {
        assertTrue(RackCensusSyncPolicy.isOwner("revisor", " REVISOR "))
        assertFalse(RackCensusSyncPolicy.isOwner("revisor", "otro"))
        assertFalse(RackCensusSyncPolicy.isOwner("revisor", null))
        assertFalse(RackCensusSyncPolicy.isOwner("", "revisor"))
    }

    @Test
    fun `a reconciliation is frozen once`() {
        assertTrue(RackCensusSyncPolicy.canFreeze(RackCensusStatus.OPEN, null))
        assertFalse(RackCensusSyncPolicy.canFreeze(RackCensusStatus.OPEN, "{}"))
        assertFalse(RackCensusSyncPolicy.canFreeze(RackCensusStatus.RECONCILING, null))
        assertFalse(RackCensusSyncPolicy.canFreeze(RackCensusStatus.RECONCILED, null))
    }

    @Test
    fun `a conflict demands comparing again instead of retrying the same payload`() {
        assertTrue(RackCensusSyncPolicy.requiresNewComparison("SNAPSHOT_CONFLICT"))
        assertTrue(RackCensusSyncPolicy.requiresNewComparison("SESSION_CONFLICT"))
        assertTrue(RackCensusSyncPolicy.requiresNewComparison("MOVEMENTS_PENDING"))
        assertFalse(RackCensusSyncPolicy.requiresNewComparison("CENSUS_FAILED"))
        assertFalse(RackCensusSyncPolicy.requiresNewComparison(null))
    }

    @Test
    fun `transient server failures retry and configuration failures do not loop`() {
        assertTrue(RackCensusSyncPolicy.isRetryableHttp(503, "CENSUS_FAILED"))
        assertTrue(RackCensusSyncPolicy.isRetryableHttp(429, null))
        assertTrue(RackCensusSyncPolicy.isRetryableHttp(408, null))
        assertFalse(RackCensusSyncPolicy.isRetryableHttp(503, "MIGRATION_REQUIRED"))
        assertFalse(RackCensusSyncPolicy.isRetryableHttp(403, "PERMISSION_DENIED"))
        assertFalse(RackCensusSyncPolicy.isRetryableHttp(409, "SNAPSHOT_CONFLICT"))
        assertFalse(RackCensusSyncPolicy.isRetryableHttp(401, null))
        assertFalse(RackCensusSyncPolicy.isRetryableHttp(400, null))
    }

    @Test
    fun `network retries exclude certificate and parsing failures wrapped as IO`() {
        assertTrue(RackCensusSyncPolicy.isRetryableNetwork(SocketTimeoutException("sin red")))
        assertFalse(RackCensusSyncPolicy.isRetryableNetwork(SSLHandshakeException("certificado")))
        assertFalse(RackCensusSyncPolicy.isRetryableNetwork(IOException(JsonParseException("json roto"))))
        assertFalse(RackCensusSyncPolicy.isRetryableNetwork(IllegalStateException("no es de red")))
    }

    @Test
    fun `a response about another session is not accepted`() {
        assertTrue(RackCensusSyncPolicy.matchesSession(sessionId, sessionId.uppercase()))
        assertFalse(RackCensusSyncPolicy.matchesSession(sessionId, null))
        assertFalse(RackCensusSyncPolicy.matchesSession(sessionId, "3583d20a-27cb-4af6-9f59-000000000000"))
    }

    @Test
    fun `a reconciliation counts as confirmed only with counts and a closing time`() {
        assertTrue(RackCensusSyncPolicy.matchesReconcile(sessionId, sessionId, hasCounts = true, closedAtMillis = 10L))
        assertFalse(RackCensusSyncPolicy.matchesReconcile(sessionId, sessionId, hasCounts = false, closedAtMillis = 10L))
        assertFalse(RackCensusSyncPolicy.matchesReconcile(sessionId, sessionId, hasCounts = true, closedAtMillis = 0L))
        assertFalse(RackCensusSyncPolicy.matchesReconcile(sessionId, "otra", hasCounts = true, closedAtMillis = 10L))
    }

    @Test
    fun `the sweep uploads in bounded batches`() {
        assertEquals(50, RackCensusSyncPolicy.OBSERVATION_BATCH)
        assertTrue(RackCensusSyncPolicy.OBSERVATION_BATCH in 1..200)
    }
}
