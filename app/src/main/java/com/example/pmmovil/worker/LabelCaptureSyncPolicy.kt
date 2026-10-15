package com.example.pmmovil.worker

import com.example.pmmovil.data.model.LabelCaptureStatus
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** Reglas comprobables sin Android para conservar el envío aceptado y evitar bucles. */
internal object LabelCaptureSyncPolicy {
    private val captureIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val configurationFailures = setOf("MIGRATION_REQUIRED", "IMAGE_VALIDATION_UNAVAILABLE", "RECOVERY_REQUIRED")
    private val comparisonFailures = setOf("SNAPSHOT_CONFLICT", "CAPTURE_CONFLICT")

    fun isValidCaptureId(captureId: String): Boolean = captureIdPattern.matches(captureId)

    fun isOwner(owner: String, currentUser: String?): Boolean = owner.isNotBlank() &&
        owner.trim().lowercase(Locale.ROOT) == currentUser?.trim()?.lowercase(Locale.ROOT)

    fun canFreeze(status: String, requestJson: String?, uploadPath: String?): Boolean =
        status == LabelCaptureStatus.DRAFT && requestJson == null && uploadPath == null

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

    fun matchesConfirmation(
        expectedCaptureId: String,
        expectedPartId: Int,
        actualCaptureId: String?,
        actualPartId: Int,
        photoId: Int?,
        hasSnapshot: Boolean
    ): Boolean = expectedCaptureId == actualCaptureId && expectedPartId == actualPartId &&
        (photoId ?: 0) > 0 && hasSnapshot

    /** Canonicalizar impide usar un enlace o ../ para salir del directorio privado. */
    fun isDirectChild(directory: File, candidate: File): Boolean =
        candidate.canonicalFile.parentFile == directory.canonicalFile
}
