package com.example.pmmovil.ui.asignaciones

import androidx.annotation.StringRes
import com.example.pmmovil.R
import com.example.pmmovil.data.model.EntradaPendienteDto
import kotlin.math.roundToInt

enum class EntryLifecycle {
    PROCESS_COMPLETED,
    REVIEW_STOPPED,
    RELATION_CLOSED,
    REVIEW_COMPLETE,
    IN_PROGRESS,
    PENDING
}

enum class EntryCategory {
    PENDING,
    IN_PROGRESS,
    PAUSED,
    COMPLETED
}

data class EntryStatusInfo(
    val lifecycle: EntryLifecycle,
    val category: EntryCategory,
    @StringRes val labelRes: Int,
    val completedInvoices: Int,
    val totalInvoices: Int,
    val progressPercent: Int,
) {
    /** Texto estable para reglas y pruebas que no tienen acceso a Compose. */
    val label: String
        get() = when (labelRes) {
            R.string.assignments_status_process_finished -> "Proceso terminado"
            R.string.assignments_status_review_finished -> "Revisión terminada"
            R.string.assignments_status_session_paused -> "Sesión pausada"
            R.string.assignments_status_relation_closed -> "Relación cerrada"
            R.string.assignments_status_review_100 -> "Revisión al 100%"
            R.string.assignments_status_in_progress_label -> "En revisión"
            else -> "Pendiente"
        }
}

/**
 * Replica las reglas de estado usadas por la pantalla web y el sistema legacy.
 */
fun calculateEntryStatus(entry: EntradaPendienteDto): EntryStatusInfo {
    val total = entry.totalFacturas.coerceAtLeast(0)
    val completed = entry.facturasCompletas.coerceAtLeast(0)
    val progress = if (total > 0) {
        ((completed.toFloat() / total.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    } else {
        0
    }
    val isComplete = total > 0 && completed >= total
    val isStopped = !entry.finRevision.isNullOrBlank()
    val isRelationClosed = entry.todasRelacionCompleta
    val isInProgress = !entry.usuarioRevisando.isNullOrBlank() ||
        !entry.usuarioTomoDocumento.isNullOrBlank() ||
        !entry.inicioRevision.isNullOrBlank()

    val lifecycle: EntryLifecycle
    val category: EntryCategory
    var labelRes: Int

    when {
        isComplete && isStopped && isRelationClosed -> {
            lifecycle = EntryLifecycle.PROCESS_COMPLETED
            category = EntryCategory.COMPLETED
            labelRes = R.string.assignments_status_process_finished
        }

        isStopped && isComplete -> {
            lifecycle = EntryLifecycle.REVIEW_STOPPED
            category = EntryCategory.COMPLETED
            labelRes = R.string.assignments_status_review_finished
        }

        isStopped -> {
            lifecycle = EntryLifecycle.REVIEW_STOPPED
            category = EntryCategory.PAUSED
            labelRes = R.string.assignments_status_session_paused
        }

        isRelationClosed -> {
            lifecycle = EntryLifecycle.RELATION_CLOSED
            category = EntryCategory.COMPLETED
            labelRes = R.string.assignments_status_relation_closed
        }

        isComplete -> {
            lifecycle = EntryLifecycle.REVIEW_COMPLETE
            category = EntryCategory.COMPLETED
            labelRes = R.string.assignments_status_review_100
        }

        isInProgress -> {
            lifecycle = EntryLifecycle.IN_PROGRESS
            category = EntryCategory.IN_PROGRESS
            labelRes = R.string.assignments_status_in_progress_label
        }

        else -> {
            lifecycle = EntryLifecycle.PENDING
            category = EntryCategory.PENDING
            labelRes = R.string.assignments_status_pending
        }
    }

    return EntryStatusInfo(
        lifecycle = lifecycle,
        category = category,
        labelRes = labelRes,
        completedInvoices = completed,
        totalInvoices = total,
        progressPercent = progress,
    )
}

fun normalizeScannedReference(rawValue: String): String? {
    val firstToken = rawValue
        .trim()
        .split(Regex("[\\s\\r\\n\\t]+"))
        .firstOrNull()
        ?.uppercase()
        ?.takeIf { it.isNotBlank() }
        ?: return null

    // La referencia agrupada no tiene una longitud fija. Recortarla aquí
    // puede convertir un QR válido en otra referencia que no existe.
    return firstToken
}

fun isEntryLocked(entry: EntradaPendienteDto, currentUsername: String?): Boolean {
    val reviewer = entry.usuarioRevisando?.trim()
    val documentOwner = entry.usuarioTomoDocumento?.trim()
    val activeOwner = reviewer?.takeIf { it.isNotEmpty() } ?: documentOwner
    return !activeOwner.isNullOrEmpty() &&
        !activeOwner.equals(currentUsername?.trim(), ignoreCase = true)
}

fun matchesEntryFilter(
    entry: EntradaPendienteDto,
    query: String,
    status: FilterStatus
): Boolean {
    val normalizedQuery = query.trim().lowercase()
    val matchesQuery = normalizedQuery.isEmpty() ||
        entry.crefAgrupada.lowercase().contains(normalizedQuery) ||
        entry.nombreCliente.orEmpty().lowercase().contains(normalizedQuery) ||
        entry.clienteId.orEmpty().lowercase().contains(normalizedQuery) ||
        entry.asignSupervisor.orEmpty().lowercase().contains(normalizedQuery) ||
        entry.asignNota.orEmpty().lowercase().contains(normalizedQuery)

    val category = calculateEntryStatus(entry).category
    val matchesStatus = when (status) {
        FilterStatus.TOTAL -> true
        FilterStatus.PENDIENTE -> category == EntryCategory.PENDING
        FilterStatus.REVISION -> category == EntryCategory.IN_PROGRESS
        FilterStatus.PAUSADA -> category == EntryCategory.PAUSED
        FilterStatus.FINALIZADO -> category == EntryCategory.COMPLETED
    }

    return matchesQuery && matchesStatus
}
