package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// ── Contrato con el API (api/Inventario/rack-census) ─────────────────────────

data class RackCensusOpenRequest(
    val sessionId: String,
    val warehouseId: String,
    val locationIds: List<String>
)

/** Un bulto esperado. `labelIds` lleva todas las filas de `etiqueta_mcia` del código. */
data class RackCensusPackageDto(
    val packageId: String = "",
    val labelIds: List<Long> = emptyList(),
    val expectedLocationId: String = "",
    val reference: String = "",
    val line: Int = 0,
    val estatus: String = "",
    val cliente: String = "",
    val descripcion: String = "",
    val declaredTotal: Int? = null,
    val printedOrdinal: Int? = null,
    val inScope: Boolean = true
)

data class RackCensusOpenResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val warehouseId: String = "",
    val snapshotVersion: Long = 0L,
    val capturedAtMillis: Long = 0L,
    val scopeLocationIds: List<String> = emptyList(),
    val packages: List<RackCensusPackageDto> = emptyList(),
    /** true cuando el catálogo de otras ubicaciones no cupo completo. */
    val foreignCatalogTruncated: Boolean = false,
    val dataWarnings: List<String> = emptyList(),
    val replayed: Boolean = false
)

data class RackCensusObservationDto(
    val observationId: String,
    val rawCode: String,
    val observedLocationId: String,
    val observedAtMillis: Long
)

data class RackCensusObserveRequest(
    val sessionId: String,
    val observations: List<RackCensusObservationDto>
)

data class RackCensusObservationResultDto(
    val observationId: String = "",
    val accepted: Boolean = false,
    /** RESOLVED | MALFORMED | FOLDER_LABEL | UNKNOWN_CODE */
    val resolution: String = "",
    val packageId: String? = null,
    val labelId: Long? = null,
    val message: String? = null
)

data class RackCensusObserveResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val results: List<RackCensusObservationResultDto> = emptyList()
)

data class RackCensusMovementDto(
    val packageId: String = "",
    val reference: String = "",
    val expectedLocationId: String = "",
    /** LOCATION_CHANGED | LEFT_WAREHOUSE | LABEL_REMOVED | STATUS_CHANGED */
    val kind: String = "",
    val fromLocationId: String? = null,
    val toLocationId: String? = null,
    val detectedVersion: Long = 0L
)

data class RackCensusMovementsResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val snapshotVersion: Long = 0L,
    val checkedAtMillis: Long = 0L,
    val movements: List<RackCensusMovementDto> = emptyList()
)

data class RackCensusMissingDto(
    val packageId: String,
    val confirmedAtMillis: Long
)

data class RackCensusReconcileRequest(
    val sessionId: String,
    val snapshotVersion: Long,
    val acknowledgedMovementPackageIds: List<String> = emptyList(),
    /** El usuario que confirma es el autenticado; el cliente no lo elige. */
    val missingConfirmations: List<RackCensusMissingDto> = emptyList(),
    val keptPendingPackageIds: List<String> = emptyList(),
    val note: String = ""
)

data class RackCensusCountsDto(
    val expectedInScope: Int = 0,
    val uniqueObservedPackages: Int = 0,
    val matched: Int = 0,
    val foundElsewhere: Int = 0,
    val notSeen: Int = 0,
    val missingConfirmed: Int = 0,
    val unidentifiedCodes: Int = 0,
    val movementsPendingReview: Int = 0,
    val totalScans: Int = 0
)

data class RackCensusReconcileResponse(
    val success: Boolean = false,
    val code: String? = null,
    val message: String? = null,
    val sessionId: String? = null,
    val closedAtMillis: Long = 0L,
    val replayed: Boolean = false,
    val counts: RackCensusCountsDto? = null,
    val movements: List<RackCensusMovementDto> = emptyList(),
    val blockReasons: List<String> = emptyList()
)

// ── Persistencia local ───────────────────────────────────────────────────────

object RackCensusStatus {
    /** Sesión abierta: se puede seguir barriendo. */
    const val OPEN = "OPEN"

    /** Conciliación enviada y esperando confirmación del servidor. */
    const val RECONCILING = "RECONCILING"

    /** Conciliada: el servidor confirmó el cierre. */
    const val RECONCILED = "RECONCILED"

    /** Requiere intervención: el servidor rechazó algo que no se arregla reintentando. */
    const val BLOCKED = "BLOCKED"
}

object RackCensusObservationSync {
    const val PENDING = "PENDING"
    const val SYNCED = "SYNCED"

    /** Rechazada por el servidor: se conserva para que el revisor la vea. */
    const val REJECTED = "REJECTED"
}

/**
 * El recorrido sobrevive al cierre de la app y a la pérdida de red: el snapshot
 * se guarda completo al abrir y las lecturas se acumulan localmente.
 */
@Entity(tableName = "rack_census_sessions")
data class RackCensusSessionEntity(
    @PrimaryKey val sessionId: String,
    val owner: String,
    val warehouseId: String,
    val scopeJson: String,
    val snapshotJson: String,
    val snapshotVersion: Long,
    val capturedAtMillis: Long,
    val foreignCatalogTruncated: Boolean = false,
    val dataWarningsJson: String = "[]",
    /** Decisiones sobre bultos no vistos: {packageId: CONFIRM_MISSING|KEEP_PENDING}. */
    val decisionsJson: String = "{}",
    /** Movimientos ya reconocidos por una persona. */
    val acknowledgedJson: String = "[]",
    val status: String = RackCensusStatus.OPEN,
    val errorMessage: String? = null,
    /** Petición congelada de conciliación; los reintentos no la rearman. */
    val reconcileRequestJson: String? = null,
    val reconcileResponseJson: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "rack_census_observations",
    indices = [Index(value = ["sessionId", "syncStatus"]), Index(value = ["sessionId", "packageId"])]
)
data class RackCensusObservationEntity(
    @PrimaryKey val observationId: String,
    val sessionId: String,
    val owner: String,
    val rawCode: String,
    val normalizedCode: String,
    val observedLocationId: String,
    val observedAtMillis: Long,
    /** Clasificación local; el servidor la confirma o la corrige al sincronizar. */
    val resolution: String,
    val packageId: String? = null,
    val labelId: Long? = null,
    val printedOrdinal: Int? = null,
    val declaredTotal: Int? = null,
    val syncStatus: String = RackCensusObservationSync.PENDING,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
