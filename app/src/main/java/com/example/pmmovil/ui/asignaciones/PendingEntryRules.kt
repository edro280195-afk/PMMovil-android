package com.example.pmmovil.ui.asignaciones

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
    val label: String,
    val progressPercent: Int,
    val progressLabel: String
)

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
    val label: String

    when {
        isComplete && isStopped && isRelationClosed -> {
            lifecycle = EntryLifecycle.PROCESS_COMPLETED
            category = EntryCategory.COMPLETED
            label = "Proceso terminado"
        }

        isStopped && isComplete -> {
            lifecycle = EntryLifecycle.REVIEW_STOPPED
            category = EntryCategory.COMPLETED
            label = "Revisión terminada"
        }

        isStopped -> {
            lifecycle = EntryLifecycle.REVIEW_STOPPED
            category = EntryCategory.PAUSED
            label = "Sesión pausada"
        }

        isRelationClosed -> {
            lifecycle = EntryLifecycle.RELATION_CLOSED
            category = EntryCategory.COMPLETED
            label = "Relación cerrada"
        }

        isComplete -> {
            lifecycle = EntryLifecycle.REVIEW_COMPLETE
            category = EntryCategory.COMPLETED
            label = "Revisión 100%"
        }

        isInProgress -> {
            lifecycle = EntryLifecycle.IN_PROGRESS
            category = EntryCategory.IN_PROGRESS
            label = "En proceso"
        }

        else -> {
            lifecycle = EntryLifecycle.PENDING
            category = EntryCategory.PENDING
            label = "Pendiente"
        }
    }

    return EntryStatusInfo(
        lifecycle = lifecycle,
        category = category,
        label = label,
        progressPercent = progress,
        progressLabel = "$completed/$total completas"
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
