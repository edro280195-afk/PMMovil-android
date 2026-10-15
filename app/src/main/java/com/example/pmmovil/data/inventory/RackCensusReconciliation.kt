package com.example.pmmovil.data.inventory

import java.util.Locale

/**
 * Censo de racks: capa de conciliación sobre las reglas de dominio de
 * [RackInventoryRules]. El dominio decide el estado de cada bulto; aquí se
 * traduce ese estado a lo que el revisor tiene que decidir, y se separa la
 * **observación física** del **ajuste de existencia**.
 *
 * Tres reglas vienen de la evaluación y se respetan literalmente:
 *
 * 1. «No visto» no demuestra «faltante». Un bulto sin lectura queda sin decidir
 *    hasta que una persona confirme el faltante o lo deje pendiente a propósito.
 * 2. El `UBICA` impreso puede estar desactualizado, así que no se lee: la
 *    ubicación esperada sale del snapshot y la observada, del rack escaneado.
 * 3. Un `14/17` es un ordinal dentro de un total declarado. Ningún conteo se
 *    deriva de él; solo cuentan identidades físicas distintas observadas.
 */

/** Por qué una lectura quedó o no asociada a un bulto del inventario. */
enum class RackCensusResolution {
    /** El código corresponde a una etiqueta del inventario esperado. */
    RESOLVED,

    /** La lectura no tiene la forma de una etiqueta IMEX. */
    MALFORMED,

    /** Etiqueta de carpeta (QR con solo la referencia): identifica el expediente, no un bulto. */
    FOLDER_LABEL,

    /** Con forma válida, pero ninguna etiqueta vigente tiene ese código. */
    UNKNOWN_CODE
}

/** Un bulto del inventario esperado, con lo que hace falta para reconocerlo y mostrarlo. */
data class RackCensusPackage(
    /** Identidad física: el código impreso normalizado. */
    val packageId: String,
    /** Todas las filas de `etiqueta_mcia` que llevan ese código impreso. */
    val labelIds: Set<Long>,
    val expectedLocationId: String,
    val reference: String,
    val line: Int = 0,
    val estatus: String = "",
    val cliente: String = "",
    val descripcion: String = "",
    /** Total impreso en la etiqueta. Informativo. */
    val declaredTotal: Int? = null,
    /** Posición impresa. Informativa. */
    val printedOrdinal: Int? = null,
    /** true cuando su ubicación esperada está dentro del alcance de la sesión. */
    val inScope: Boolean = true
) {
    /**
     * Representante estable para las reglas de dominio, que trabajan con un
     * identificador numérico por observación. Un código reimpreso puede tener
     * varias filas; el censo usa la menor y conserva el conjunto completo.
     */
    val representativeLabelId: Long get() = labelIds.min()
}

/** Instantánea del inventario esperado al abrir la sesión. */
data class RackCensusSnapshot(
    val sessionId: String,
    val warehouseId: String,
    val version: Long,
    val capturedAtMillis: Long,
    val scopeLocationIds: Set<String>,
    val packages: List<RackCensusPackage>,
    /**
     * true cuando el catálogo de etiquetas de otras ubicaciones no cupo completo.
     * Entonces un código ajeno puede no resolverse estando el equipo sin red, y
     * el hallazgo se marca como pendiente de resolver en línea, no como ajeno.
     */
    val foreignCatalogTruncated: Boolean = false,
    /** Etiquetas excluidas por datos contradictorios; no entran al censo. */
    val dataWarnings: List<String> = emptyList()
)

/** Una lectura del barrido, ya clasificada contra el snapshot. */
data class RackCensusScan(
    val observationId: String,
    val rawCode: String,
    val normalizedCode: String,
    val observedLocationId: String,
    val observedAtMillis: Long,
    val resolution: RackCensusResolution,
    val packageId: String? = null,
    val labelId: Long? = null,
    val printedOrdinal: Int? = null,
    val declaredTotal: Int? = null
)

/** Qué cambió en un bulto después del snapshot. */
enum class RackCensusMovementKind {
    /** Cambió `trafdet.ubicacion` de su partida. */
    LOCATION_CHANGED,

    /** Quedó cargado o salió: ya no ocupa ubicación. */
    LEFT_WAREHOUSE,

    /** Su fila de etiqueta ya no existe. */
    LABEL_REMOVED,

    /** Otro cambio de estatus que el censo no interpreta por su cuenta. */
    STATUS_CHANGED
}

/** Movimiento posterior al snapshot. Exige que una persona lo reconozca. */
data class RackCensusMovementReview(
    val packageId: String,
    val reference: String,
    val expectedLocationId: String,
    val kind: RackCensusMovementKind,
    val fromLocationId: String? = null,
    val toLocationId: String? = null,
    val detectedVersion: Long = 0L,
    val wasObserved: Boolean = false,
    val acknowledged: Boolean = false
)

/** Las cuatro cosas distintas que la pantalla de conciliación no debe mezclar. */
enum class RackCensusBucket {
    /** Esperado aquí y visto aquí. */
    MATCHED,

    /** Visto, pero en una ubicación distinta a la esperada. */
    FOUND_ELSEWHERE,

    /** Esperado aquí y sin lectura. No equivale a faltante. */
    NOT_SEEN,

    /** Se leyó algo que no corresponde a ningún bulto del inventario. */
    UNIDENTIFIED
}

/** Decisión humana sobre un bulto no visto. Sin ella no se puede conciliar. */
enum class RackCensusNotSeenDecision {
    /** Nadie ha decidido todavía. */
    UNDECIDED,

    /** Una persona afirma el faltante y se hace responsable del registro. */
    CONFIRM_MISSING,

    /** Se deja sin resolver a propósito: no se afirma que falte. */
    KEEP_PENDING
}

data class RackCensusPackageFinding(
    val bucket: RackCensusBucket,
    val packageId: String,
    val reference: String,
    val line: Int,
    val expectedLocationId: String,
    val observedLocationIds: Set<String>,
    val observedLabelIds: Set<Long>,
    val status: InventoryReconciliationStatus,
    val cliente: String,
    val descripcion: String,
    /** Total impreso en la etiqueta. Se muestra como dato de la etiqueta, no como conteo. */
    val declaredTotal: Int?,
    val printedOrdinal: Int?,
    val decision: RackCensusNotSeenDecision = RackCensusNotSeenDecision.UNDECIDED,
    val missingConfirmedBy: String? = null,
    /** Cuántas lecturas distintas cayeron en este mismo bulto (reimpresiones incluidas). */
    val scanCount: Int = 0
)

data class RackCensusUnidentifiedFinding(
    val normalizedCode: String,
    val rawCode: String,
    val resolution: RackCensusResolution,
    val occurrences: Int,
    val firstSeenAtMillis: Long,
    val observationIds: Set<String>,
    val printedOrdinal: Int?,
    val declaredTotal: Int?,
    /** true cuando el catálogo local venía recortado y falta resolver con el servidor. */
    val pendingOnlineResolution: Boolean
)

/**
 * Ajuste propuesto. Ninguno se aplica solo: todos nacen de una decisión
 * explícita, y ninguno revierte una salida ni descuenta existencia.
 */
enum class RackCensusAdjustmentKind {
    /** Registrar la última observación física del bulto (`time_inv`/`usu_inv`). */
    STAMP_OBSERVATION,

    /**
     * Dejar constancia de que el bulto apareció en otra ubicación. No reescribe
     * `trafdet.ubicacion`: esa columna es de la partida completa y no puede
     * representar un solo bulto movido.
     */
    REPORT_WRONG_LOCATION,

    /** Dejar constancia del faltante confirmado. No descuenta existencia. */
    REPORT_MISSING
}

data class RackCensusAdjustment(
    val kind: RackCensusAdjustmentKind,
    val packageId: String,
    val description: String
)

data class RackCensusCounts(
    val expectedInScope: Int,
    /** Identidades físicas distintas observadas. El único conteo válido. */
    val uniqueObservedPackages: Int,
    val matched: Int,
    val foundElsewhere: Int,
    val notSeen: Int,
    val missingConfirmed: Int,
    val unidentifiedCodes: Int,
    val movementsPendingReview: Int,
    val totalScans: Int
)

data class RackCensusReport(
    val sessionId: String,
    val snapshotVersion: Long,
    val counts: RackCensusCounts,
    val matched: List<RackCensusPackageFinding>,
    val foundElsewhere: List<RackCensusPackageFinding>,
    val notSeen: List<RackCensusPackageFinding>,
    val unidentified: List<RackCensusUnidentifiedFinding>,
    val movements: List<RackCensusMovementReview>,
    val adjustments: List<RackCensusAdjustment>,
    val canConfirm: Boolean,
    val blockReasons: List<String>,
    val dataWarnings: List<String>
)

object RackCensusRules {
    /** Normaliza una ubicación igual que el dominio, para comparar sin sorpresas. */
    fun normalizeLocation(value: String): String = value.trim().uppercase(Locale.ROOT)

    /**
     * Clasifica una lectura contra el snapshot. No registra nada: devuelve qué es
     * esa lectura para que el barrido pueda marcarla y, si hace falta, descartarla.
     */
    fun resolveScan(
        snapshot: RackCensusSnapshot,
        observationId: String,
        rawCode: String,
        observedLocationId: String,
        observedAtMillis: Long
    ): RackCensusScan {
        val parsed = RackCensusCodeParser.parse(rawCode)
        val normalized = parsed?.normalized ?: RackCensusCodeParser.normalize(rawCode)
        val base = RackCensusScan(
            observationId = observationId,
            rawCode = rawCode,
            normalizedCode = normalized,
            observedLocationId = normalizeLocation(observedLocationId),
            observedAtMillis = observedAtMillis,
            resolution = RackCensusResolution.MALFORMED,
            printedOrdinal = parsed?.printedOrdinal,
            declaredTotal = parsed?.declaredTotal
        )
        if (parsed == null) return base
        if (parsed.isFolderLabel) {
            // Un código sin ordinal solo es la etiqueta de carpeta cuando su
            // referencia existe en el inventario; si no, es un código ajeno y
            // decirle «carpeta» sería adivinar.
            val knownReference = snapshot.packages.any { it.reference.equals(parsed.reference, ignoreCase = true) }
            return base.copy(
                resolution = if (knownReference) RackCensusResolution.FOLDER_LABEL
                else RackCensusResolution.UNKNOWN_CODE
            )
        }
        val match = snapshot.packages.firstOrNull { it.packageId == parsed.normalized }
            ?: return base.copy(resolution = RackCensusResolution.UNKNOWN_CODE)
        return base.copy(
            resolution = RackCensusResolution.RESOLVED,
            packageId = match.packageId,
            labelId = match.representativeLabelId
        )
    }

    /**
     * Arma la sesión de dominio con las lecturas resueltas. Las demás no entran:
     * un código sin identidad no puede convertirse en un bulto observado.
     */
    fun toDomainSession(
        snapshot: RackCensusSnapshot,
        scans: List<RackCensusScan>,
        missingConfirmations: List<InventoryMissingConfirmation> = emptyList()
    ): RackInventorySession = RackInventorySession(
        sessionId = snapshot.sessionId,
        snapshot = InventorySnapshot(
            version = snapshot.version,
            capturedAtMillis = snapshot.capturedAtMillis,
            packages = snapshot.packages.map {
                InventoryPackage(it.packageId, it.labelIds, it.expectedLocationId)
            }
        ),
        scopeLocationIds = snapshot.scopeLocationIds.map(::normalizeLocation).toSet(),
        observations = scans
            .filter { it.resolution == RackCensusResolution.RESOLVED && it.labelId != null }
            .map {
                InventoryObservation(
                    observationId = it.observationId,
                    labelId = it.labelId!!,
                    observedLocationId = it.observedLocationId,
                    observedAtMillis = it.observedAtMillis
                )
            },
        missingConfirmations = missingConfirmations
    )

    /**
     * Construye el reporte de las cuatro categorías más la compuerta de
     * movimientos. Las listas son disjuntas y cubren todo lo observado y lo
     * esperado dentro del alcance; nada se reparte en dos lugares a la vez.
     */
    fun reconcile(
        snapshot: RackCensusSnapshot,
        scans: List<RackCensusScan>,
        movements: List<RackCensusMovementReview> = emptyList(),
        decisions: Map<String, RackCensusNotSeenDecision> = emptyMap(),
        missingConfirmations: List<InventoryMissingConfirmation> = emptyList(),
        acknowledgedMovementPackageIds: Set<String> = emptySet()
    ): RackCensusReport {
        val packagesById = snapshot.packages.associateBy { it.packageId }
        val resolvedScans = scans.filter { it.resolution == RackCensusResolution.RESOLVED }
        val domainReport = RackInventoryRules.reconcile(
            toDomainSession(snapshot, scans, missingConfirmations),
            movements.map {
                InventoryMovement(
                    packageId = it.packageId,
                    version = it.detectedVersion,
                    fromLocationId = it.fromLocationId,
                    toLocationId = it.toLocationId
                )
            }
        )
        val scanCountByPackage = resolvedScans.groupingBy { it.packageId.orEmpty() }.eachCount()
        val confirmationsByPackage = missingConfirmations.associateBy { it.packageId }
        // Un movimiento anterior o igual al snapshot ya está contemplado en él y
        // no abre compuerta; solo los posteriores exigen revisión, igual que en
        // el dominio.
        val concurrentMovements = movements.filter { it.detectedVersion > snapshot.version }

        val findings = domainReport.items
            // Los movimientos se resuelven en su propia compuerta: no se mezclan
            // con las cuatro categorías para no presentar como coincidencia algo
            // que cambió mientras se recorría el rack.
            .filterNot { it.status == InventoryReconciliationStatus.MOVEMENT_REVIEW }
            .map { item ->
                val pkg = packagesById[item.packageId]
                val bucket = when (item.status) {
                    InventoryReconciliationStatus.MATCHED -> RackCensusBucket.MATCHED
                    InventoryReconciliationStatus.WRONG_LOCATION,
                    InventoryReconciliationStatus.MULTIPLE_OBSERVED_LOCATIONS -> RackCensusBucket.FOUND_ELSEWHERE
                    InventoryReconciliationStatus.NOT_SEEN,
                    InventoryReconciliationStatus.MISSING_CONFIRMED -> RackCensusBucket.NOT_SEEN
                    // Ya quedaron fuera por el filtro anterior; el dominio solo
                    // marca este estado cuando hay un movimiento posterior.
                    InventoryReconciliationStatus.MOVEMENT_REVIEW -> RackCensusBucket.NOT_SEEN
                }
                val confirmation = confirmationsByPackage[item.packageId]
                val decision = when {
                    confirmation != null -> RackCensusNotSeenDecision.CONFIRM_MISSING
                    else -> decisions[item.packageId] ?: RackCensusNotSeenDecision.UNDECIDED
                }
                RackCensusPackageFinding(
                    bucket = bucket,
                    packageId = item.packageId,
                    reference = pkg?.reference.orEmpty(),
                    line = pkg?.line ?: 0,
                    expectedLocationId = item.expectedLocationId,
                    observedLocationIds = item.observedLocationIds,
                    observedLabelIds = item.observedLabelIds,
                    status = item.status,
                    cliente = pkg?.cliente.orEmpty(),
                    descripcion = pkg?.descripcion.orEmpty(),
                    declaredTotal = pkg?.declaredTotal,
                    printedOrdinal = pkg?.printedOrdinal,
                    decision = if (bucket == RackCensusBucket.NOT_SEEN) decision else RackCensusNotSeenDecision.UNDECIDED,
                    missingConfirmedBy = confirmation?.confirmedBy,
                    scanCount = scanCountByPackage[item.packageId] ?: 0
                )
            }

        val unidentified = scans
            .filter { it.resolution != RackCensusResolution.RESOLVED }
            .groupBy { it.normalizedCode }
            .map { (code, group) ->
                val first = group.minByOrNull { it.observedAtMillis } ?: group.first()
                RackCensusUnidentifiedFinding(
                    normalizedCode = code,
                    rawCode = first.rawCode,
                    resolution = first.resolution,
                    occurrences = group.size,
                    firstSeenAtMillis = first.observedAtMillis,
                    observationIds = group.map { it.observationId }.toSet(),
                    printedOrdinal = first.printedOrdinal,
                    declaredTotal = first.declaredTotal,
                    pendingOnlineResolution = snapshot.foreignCatalogTruncated &&
                        first.resolution == RackCensusResolution.UNKNOWN_CODE
                )
            }
            .sortedBy { it.firstSeenAtMillis }

        val reviewedMovements = concurrentMovements.map {
            it.copy(
                wasObserved = resolvedScans.any { scan -> scan.packageId == it.packageId },
                acknowledged = it.packageId in acknowledgedMovementPackageIds
            )
        }

        val matched = findings.filter { it.bucket == RackCensusBucket.MATCHED }
        val elsewhere = findings.filter { it.bucket == RackCensusBucket.FOUND_ELSEWHERE }
        val notSeen = findings.filter { it.bucket == RackCensusBucket.NOT_SEEN }
        val pendingMovements = reviewedMovements.filterNot { it.acknowledged }
        val undecided = notSeen.count { it.decision == RackCensusNotSeenDecision.UNDECIDED }

        val blockReasons = buildList {
            if (pendingMovements.isNotEmpty()) {
                add("Hay ${pendingMovements.size} bulto(s) con movimientos durante el recorrido. Revísalos antes de conciliar.")
            }
            if (undecided > 0) {
                add("Hay $undecided bulto(s) no vistos sin decidir. Confirma el faltante o déjalo pendiente a propósito.")
            }
            if (unidentified.any { it.pendingOnlineResolution }) {
                add("Hay códigos pendientes de resolver con el servidor. Sincroniza antes de conciliar.")
            }
        }

        return RackCensusReport(
            sessionId = snapshot.sessionId,
            snapshotVersion = domainReport.snapshotVersion,
            counts = RackCensusCounts(
                expectedInScope = snapshot.packages.count { it.inScope },
                uniqueObservedPackages = domainReport.uniqueObservedPackages,
                matched = matched.size,
                foundElsewhere = elsewhere.size,
                notSeen = notSeen.size,
                missingConfirmed = notSeen.count { it.decision == RackCensusNotSeenDecision.CONFIRM_MISSING },
                unidentifiedCodes = unidentified.size,
                movementsPendingReview = pendingMovements.size,
                totalScans = scans.size
            ),
            matched = matched,
            foundElsewhere = elsewhere,
            notSeen = notSeen,
            unidentified = unidentified,
            movements = reviewedMovements,
            adjustments = buildAdjustments(matched, elsewhere, notSeen, reviewedMovements),
            canConfirm = blockReasons.isEmpty(),
            blockReasons = blockReasons,
            dataWarnings = snapshot.dataWarnings
        )
    }

    /**
     * Traduce los hallazgos a ajustes. Deliberadamente corto: el censo registra
     * observaciones y hallazgos; mover mercancía o corregir existencias sigue
     * siendo una decisión de bodega con su propio flujo.
     */
    private fun buildAdjustments(
        matched: List<RackCensusPackageFinding>,
        elsewhere: List<RackCensusPackageFinding>,
        notSeen: List<RackCensusPackageFinding>,
        movements: List<RackCensusMovementReview>
    ): List<RackCensusAdjustment> = buildList {
        matched.forEach {
            add(RackCensusAdjustment(
                RackCensusAdjustmentKind.STAMP_OBSERVATION, it.packageId,
                "Registrar la observación física en ${it.expectedLocationId}."
            ))
        }
        elsewhere.forEach { finding ->
            add(RackCensusAdjustment(
                RackCensusAdjustmentKind.STAMP_OBSERVATION, finding.packageId,
                "Registrar la observación física en ${finding.observedLocationIds.sorted().joinToString(", ")}."
            ))
            add(RackCensusAdjustment(
                RackCensusAdjustmentKind.REPORT_WRONG_LOCATION, finding.packageId,
                "Dejar constancia de que se esperaba en ${finding.expectedLocationId}. No se reescribe la ubicación de la partida."
            ))
        }
        notSeen.filter { it.decision == RackCensusNotSeenDecision.CONFIRM_MISSING }.forEach {
            add(RackCensusAdjustment(
                RackCensusAdjustmentKind.REPORT_MISSING, it.packageId,
                "Registrar el faltante confirmado por ${it.missingConfirmedBy ?: "el revisor"}. No descuenta existencia."
            ))
        }
        // Una lectura sobre un bulto que ya salió deja constancia de la
        // observación y nada más: la salida posterior al snapshot no se revierte.
        movements.filter { it.acknowledged && it.wasObserved }.forEach {
            add(RackCensusAdjustment(
                RackCensusAdjustmentKind.STAMP_OBSERVATION, it.packageId,
                "Registrar la observación física. No revierte el movimiento ya registrado."
            ))
        }
    }
}
