package com.example.pmmovil.data.inventory

import java.util.Locale

/**
 * Identidad física resuelta por el servidor. Una reimpresión puede tener otro ID de
 * etiqueta y debe conservar el mismo [packageId]. El ordinal impreso no es un ID.
 */
data class InventoryPackage(
    val packageId: String,
    val labelIds: Set<Long>,
    val expectedLocationId: String
)

/** Foto del inventario esperado al iniciar; [version] es una versión del servidor. */
data class InventorySnapshot(
    val version: Long,
    val capturedAtMillis: Long,
    val packages: List<InventoryPackage>
)

/** Observación física: la ubicación se confirma en el rack, no leyendo UBICA impresa. */
data class InventoryObservation(
    val observationId: String,
    val labelId: Long,
    val observedLocationId: String,
    val observedAtMillis: Long
)

/** Un movimiento posterior al snapshot impide conciliar esa unidad automáticamente. */
data class InventoryMovement(
    val packageId: String,
    val version: Long,
    val fromLocationId: String?,
    val toLocationId: String?
)

/** Una ausencia solo se confirma mediante una acción explícita de una persona. */
data class InventoryMissingConfirmation(
    val packageId: String,
    val confirmedBy: String,
    val confirmedAtMillis: Long
)

/** Dominio puro: no escribe ubicaciones ni movimientos en el servidor. */
data class RackInventorySession(
    val sessionId: String,
    val snapshot: InventorySnapshot,
    val scopeLocationIds: Set<String>,
    val observations: List<InventoryObservation> = emptyList(),
    val missingConfirmations: List<InventoryMissingConfirmation> = emptyList()
)

enum class InventoryReconciliationStatus {
    MATCHED,
    WRONG_LOCATION,
    NOT_SEEN,
    MISSING_CONFIRMED,
    MOVEMENT_REVIEW,
    MULTIPLE_OBSERVED_LOCATIONS
}

data class InventoryReconciliationItem(
    val packageId: String,
    val expectedLocationId: String,
    val observedLocationIds: Set<String>,
    val observedLabelIds: Set<Long>,
    val status: InventoryReconciliationStatus
)

/** [unresolvedLabelIds] no se convierten en unidades físicas ni se suman al conteo. */
data class RackInventoryReport(
    val snapshotVersion: Long,
    val items: List<InventoryReconciliationItem>,
    val uniqueObservedPackages: Int,
    val unresolvedLabelIds: Set<Long>
)

/** Reglas para barrer y comparar sin borrar mercancía que simplemente no se alcanzó a ver. */
object RackInventoryRules {
    /** Un reintento con el mismo ID y contenido es idempotente; otro contenido se rechaza. */
    fun recordObservation(session: RackInventorySession, observation: InventoryObservation): RackInventorySession {
        validateSession(session)
        validateObservation(session, observation)
        val existing = session.observations.firstOrNull { it.observationId == observation.observationId }
        require(existing == null || existing == observation) { "El ID de observación ya existe con datos diferentes." }
        return if (existing != null) session else session.copy(observations = session.observations + observation)
    }

    /**
     * Conserva el snapshot original. Cualquier movimiento más nuevo requiere revisión,
     * incluso cuando una observación parece coincidir con la ubicación anterior.
     */
    fun reconcile(session: RackInventorySession, movements: List<InventoryMovement> = emptyList()): RackInventoryReport {
        validateSession(session)
        val scope = session.scopeLocationIds.map(::normalizeLocation).toSet()
        val labelOwners = session.snapshot.packages.flatMap { item -> item.labelIds.map { it to item.packageId } }.toMap()
        val observationsByPackage = session.observations
            .mapNotNull { observation -> labelOwners[observation.labelId]?.let { it to observation } }
            .groupBy({ it.first }, { it.second })
        val movedPackages = movements.filter { it.version > session.snapshot.version }.map { it.packageId }.toSet()
        val confirmedMissing = session.missingConfirmations.map { it.packageId }.toSet()
        val items = session.snapshot.packages.mapNotNull { item ->
            val observations = observationsByPackage[item.packageId].orEmpty()
            val expected = normalizeLocation(item.expectedLocationId)
            if (expected !in scope && observations.isEmpty()) return@mapNotNull null
            val observedLocations = observations.map { normalizeLocation(it.observedLocationId) }.toSet()
            val status = when {
                item.packageId in movedPackages -> InventoryReconciliationStatus.MOVEMENT_REVIEW
                observedLocations.size > 1 -> InventoryReconciliationStatus.MULTIPLE_OBSERVED_LOCATIONS
                observedLocations.isEmpty() && item.packageId in confirmedMissing -> InventoryReconciliationStatus.MISSING_CONFIRMED
                observedLocations.isEmpty() -> InventoryReconciliationStatus.NOT_SEEN
                observedLocations.single() == expected -> InventoryReconciliationStatus.MATCHED
                else -> InventoryReconciliationStatus.WRONG_LOCATION
            }
            InventoryReconciliationItem(
                packageId = item.packageId,
                expectedLocationId = expected,
                observedLocationIds = observedLocations,
                observedLabelIds = observations.map { it.labelId }.toSet(),
                status = status
            )
        }
        return RackInventoryReport(
            snapshotVersion = session.snapshot.version,
            items = items,
            uniqueObservedPackages = observationsByPackage.size,
            unresolvedLabelIds = session.observations.filter { it.labelId !in labelOwners }.map { it.labelId }.toSet()
        )
    }

    /** Solo confirma una ausencia que siga sin observación y sin movimientos nuevos. */
    fun confirmMissing(
        session: RackInventorySession,
        confirmation: InventoryMissingConfirmation,
        movements: List<InventoryMovement> = emptyList()
    ): RackInventorySession {
        require(confirmation.confirmedBy.isNotBlank()) { "Se requiere el usuario que confirmó el faltante." }
        require(confirmation.confirmedAtMillis >= session.snapshot.capturedAtMillis) { "La confirmación es anterior al inventario." }
        val item = reconcile(session, movements).items.firstOrNull { it.packageId == confirmation.packageId }
        require(item?.status == InventoryReconciliationStatus.NOT_SEEN || item?.status == InventoryReconciliationStatus.MISSING_CONFIRMED) {
            "Solo se puede confirmar un bulto no visto y sin movimientos pendientes."
        }
        return session.copy(missingConfirmations = session.missingConfirmations.filterNot { it.packageId == confirmation.packageId } + confirmation)
    }

    private fun validateSession(session: RackInventorySession) {
        require(session.sessionId.isNotBlank()) { "Se requiere el ID de sesión." }
        require(session.snapshot.version >= 0L && session.snapshot.capturedAtMillis >= 0L) { "Snapshot inválido." }
        require(session.scopeLocationIds.isNotEmpty() && session.scopeLocationIds.none(String::isBlank)) { "Se requiere el alcance del inventario." }
        val packages = session.snapshot.packages
        require(packages.none { it.packageId.isBlank() || it.expectedLocationId.isBlank() || it.labelIds.isEmpty() || it.labelIds.any { id -> id <= 0L } }) {
            "Cada bulto requiere identidad, ubicación esperada y etiquetas válidas."
        }
        require(packages.map { it.packageId }.distinct().size == packages.size) { "Hay identidades físicas repetidas en el snapshot." }
        val labelIds = packages.flatMap { it.labelIds }
        require(labelIds.distinct().size == labelIds.size) { "Una etiqueta no puede identificar dos bultos diferentes." }
        session.observations.forEach { validateObservation(session, it) }
        require(session.observations.map { it.observationId }.distinct().size == session.observations.size) { "Hay IDs de observación repetidos." }
        val knownIds = packages.map { it.packageId }.toSet()
        require(session.missingConfirmations.all {
            it.packageId in knownIds && it.confirmedBy.isNotBlank() && it.confirmedAtMillis >= session.snapshot.capturedAtMillis
        }) { "La confirmación de faltante no es válida para este inventario." }
    }

    private fun validateObservation(session: RackInventorySession, observation: InventoryObservation) {
        require(observation.observationId.isNotBlank() && observation.labelId > 0L) { "Observación sin identidad válida." }
        require(normalizeLocation(observation.observedLocationId) in session.scopeLocationIds.map(::normalizeLocation)) {
            "La ubicación observada está fuera del alcance del inventario."
        }
        require(observation.observedAtMillis >= session.snapshot.capturedAtMillis) { "La observación es anterior al snapshot." }
    }

    private fun normalizeLocation(value: String): String = value.trim().uppercase(Locale.ROOT)
}
