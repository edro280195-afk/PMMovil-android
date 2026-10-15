package com.example.pmmovil.worker

import com.example.pmmovil.data.model.RackCensusStatus
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.IOException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Reglas comprobables sin Android para que el censo no pierda lecturas ni
 * entre en bucle. Mismo criterio que [LabelCaptureSyncPolicy]: lo transitorio
 * se reintenta con el mismo identificador, lo de configuración no.
 */
internal object RackCensusSyncPolicy {
    /** Cuántas lecturas se mandan por petición durante el barrido. */
    const val OBSERVATION_BATCH = 50

    private val sessionIdPattern =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val configurationFailures = setOf("MIGRATION_REQUIRED", "PERMISSION_DENIED")
    private val comparisonFailures = setOf("SNAPSHOT_CONFLICT", "SESSION_CONFLICT", "MOVEMENTS_PENDING")

    fun isValidSessionId(sessionId: String): Boolean = sessionIdPattern.matches(sessionId)

    fun isOwner(owner: String, currentUser: String?): Boolean = owner.isNotBlank() &&
        owner.trim().lowercase(Locale.ROOT) == currentUser?.trim()?.lowercase(Locale.ROOT)

    fun canFreeze(status: String, reconcileRequestJson: String?): Boolean =
        status == RackCensusStatus.OPEN && reconcileRequestJson == null

    /** Un conflicto obliga a volver a comparar con el servidor, no a reintentar igual. */
    fun requiresNewComparison(serverCode: String?): Boolean = serverCode in comparisonFailures

    fun isRetryableHttp(status: Int, serverCode: String?): Boolean =
        serverCode !in configurationFailures && !requiresNewComparison(serverCode) &&
            (status == 408 || status == 429 || status in 500..599)

    /** Se llama únicamente alrededor de la petición; un IOException del disco no es red. */
    fun isRetryableNetwork(error: Exception): Boolean = error is IOException &&
        generateSequence<Throwable>(error) { it.cause }.take(8).none {
            it is SSLHandshakeException || it is SSLPeerUnverifiedException ||
                it is JsonParseException || it is MalformedJsonException
        }

    /** La respuesta tiene que hablar de la misma sesión que se envió. */
    fun matchesSession(expectedSessionId: String, actualSessionId: String?): Boolean =
        actualSessionId != null && expectedSessionId.equals(actualSessionId, ignoreCase = true)

    /**
     * Una conciliación se acepta solo si el servidor confirma la misma sesión y
     * entrega conteos; sin eso se reintenta el mismo envío.
     */
    fun matchesReconcile(
        expectedSessionId: String,
        actualSessionId: String?,
        hasCounts: Boolean,
        closedAtMillis: Long
    ): Boolean = matchesSession(expectedSessionId, actualSessionId) && hasCounts && closedAtMillis > 0L
}
