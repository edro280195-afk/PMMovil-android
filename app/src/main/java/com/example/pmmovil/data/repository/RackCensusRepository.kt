package com.example.pmmovil.data.repository

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.RackCensusDao
import com.example.pmmovil.data.inventory.InventoryMissingConfirmation
import com.example.pmmovil.data.inventory.RackCensusMovementReview
import com.example.pmmovil.data.inventory.RackCensusNotSeenDecision
import com.example.pmmovil.data.inventory.RackCensusPackage
import com.example.pmmovil.data.inventory.RackCensusReport
import com.example.pmmovil.data.inventory.RackCensusResolution
import com.example.pmmovil.data.inventory.RackCensusRules
import com.example.pmmovil.data.inventory.RackCensusScan
import com.example.pmmovil.data.inventory.RackCensusSnapshot
import com.example.pmmovil.data.model.RackCensusMissingDto
import com.example.pmmovil.data.model.RackCensusMovementDto
import com.example.pmmovil.data.model.RackCensusObservationDto
import com.example.pmmovil.data.model.RackCensusObservationEntity
import com.example.pmmovil.data.model.RackCensusObservationSync
import com.example.pmmovil.data.model.RackCensusObserveRequest
import com.example.pmmovil.data.model.RackCensusOpenRequest
import com.example.pmmovil.data.model.RackCensusOpenResponse
import com.example.pmmovil.data.model.RackCensusPackageDto
import com.example.pmmovil.data.model.RackCensusReconcileRequest
import com.example.pmmovil.data.model.RackCensusReconcileResponse
import com.example.pmmovil.data.model.RackCensusSessionEntity
import com.example.pmmovil.data.model.RackCensusStatus
import com.example.pmmovil.worker.RackCensusReconcileWorker
import com.example.pmmovil.worker.RackCensusSyncPolicy
import com.example.pmmovil.worker.RackCensusSyncWorker
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Qué pasó con una lectura del barrido, para marcarla en la pantalla. */
enum class RackCensusScanOutcome {
    /** Primera vez que se ve este bulto, y está donde se esperaba. */
    NEW_IN_PLACE,

    /** Primera vez que se ve, pero se esperaba en otra ubicación. */
    NEW_ELSEWHERE,

    /** Ya se había leído en esta misma ubicación: no se cuenta otra vez. */
    ALREADY_SEEN,

    /** Mismo código leído antes en otra ubicación del alcance: requiere revisión. */
    CONFLICTING_LOCATION,

    /** Etiqueta de carpeta: identifica el expediente, no un bulto. */
    FOLDER_LABEL,

    /** Código válido que no corresponde a ninguna etiqueta del inventario. */
    UNIDENTIFIED,

    /** La lectura no tiene la forma de una etiqueta IMEX. */
    MALFORMED
}

data class RackCensusScanFeedback(
    val outcome: RackCensusScanOutcome,
    val normalizedCode: String,
    val packageId: String? = null,
    val reference: String = "",
    val expectedLocationId: String = "",
    val message: String = ""
)

/**
 * Conserva el recorrido completo: snapshot al abrir, lecturas acumuladas y la
 * conciliación congelada, igual que la captura de etiqueta. Nada depende de
 * tener red durante el barrido.
 */
@Singleton
class RackCensusRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: RackCensusDao,
    private val api: PMMovilApi,
    private val auth: AuthRepository
) {
    private val gson = Gson()
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
    private val packagesType = object : TypeToken<List<RackCensusPackageDto>>() {}.type
    private val stringsType = object : TypeToken<List<String>>() {}.type
    private val decisionsType = object : TypeToken<Map<String, String>>() {}.type

    private fun lockFor(sessionId: String): Mutex = sessionLocks.getOrPut(sessionId) { Mutex() }

    private fun currentSession(): AuthRepository.SessionCredentials? = auth.getSessionCredentials()?.let { session ->
        val username = session.username.substringBefore(';').trim()
        if (username.isBlank() || session.token.isBlank()) null
        else AuthRepository.SessionCredentials(username, session.token)
    }

    private fun normalizedOwner(username: String): String = username.trim().lowercase(Locale.ROOT)

    private fun authorizationHeader(token: String): String = "Bearer ${token.trim()}"

    fun currentOwner(): String = currentSession()?.username?.let(::normalizedOwner).orEmpty()

    fun observeActiveSessions(): Flow<List<RackCensusSessionEntity>> = dao.observeActiveSessions(currentOwner())

    fun observeSession(sessionId: String): Flow<RackCensusSessionEntity?> = dao.observeSession(sessionId)

    fun observeObservations(sessionId: String): Flow<List<RackCensusObservationEntity>> =
        dao.observeObservations(sessionId)

    fun observePendingCount(sessionId: String): Flow<Int> = dao.observePendingCount(sessionId)

    // ── Abrir ────────────────────────────────────────────────────────────────

    /**
     * Abre la sesión y guarda el inventario esperado. El identificador se genera
     * aquí para que un reintento llegue al servidor con el mismo UUID.
     */
    suspend fun openSession(warehouseId: String, locationIds: List<String>): Result<RackCensusSessionEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = requireNotNull(currentSession()) { "Inicia sesión antes de abrir un censo." }
                val scope = locationIds.map { RackCensusRules.normalizeLocation(it) }.filter { it.isNotEmpty() }.distinct()
                require(scope.isNotEmpty()) { "Selecciona al menos una ubicación para el censo." }
                val sessionId = UUID.randomUUID().toString()
                val response = api.openRackCensusSession(
                    RackCensusOpenRequest(sessionId, warehouseId.trim().uppercase(Locale.ROOT), scope),
                    authorizationHeader(session.token)
                )
                val body = response.body() ?: response.errorBody()?.string()?.let(::parseOpenResponse)
                if (!response.isSuccessful || body?.success != true ||
                    !RackCensusSyncPolicy.matchesSession(sessionId, body.sessionId)
                ) {
                    throw IllegalStateException(body?.message ?: "No se pudo abrir el censo de la ubicación.")
                }
                val entity = RackCensusSessionEntity(
                    sessionId = sessionId,
                    owner = normalizedOwner(session.username),
                    warehouseId = body.warehouseId.ifBlank { warehouseId },
                    scopeJson = gson.toJson(body.scopeLocationIds.ifEmpty { scope }),
                    snapshotJson = gson.toJson(body.packages),
                    snapshotVersion = body.snapshotVersion,
                    capturedAtMillis = body.capturedAtMillis,
                    foreignCatalogTruncated = body.foreignCatalogTruncated,
                    dataWarningsJson = gson.toJson(body.dataWarnings)
                )
                dao.insertSession(entity)
                entity
            }
        }

    suspend fun getSession(sessionId: String): RackCensusSessionEntity? =
        withContext(Dispatchers.IO) { dao.getSession(sessionId) }

    /** Reconstruye el snapshot guardado para poder resolver códigos sin red. */
    fun snapshotOf(entity: RackCensusSessionEntity): RackCensusSnapshot {
        val packages: List<RackCensusPackageDto> =
            runCatching { gson.fromJson<List<RackCensusPackageDto>>(entity.snapshotJson, packagesType) }
                .getOrNull().orEmpty()
        val scope: List<String> =
            runCatching { gson.fromJson<List<String>>(entity.scopeJson, stringsType) }.getOrNull().orEmpty()
        val warnings: List<String> =
            runCatching { gson.fromJson<List<String>>(entity.dataWarningsJson, stringsType) }.getOrNull().orEmpty()
        return RackCensusSnapshot(
            sessionId = entity.sessionId,
            warehouseId = entity.warehouseId,
            version = entity.snapshotVersion,
            capturedAtMillis = entity.capturedAtMillis,
            scopeLocationIds = scope.map(RackCensusRules::normalizeLocation).toSet(),
            packages = packages
                // Una etiqueta sin fila válida no puede identificar un bulto.
                .filter { it.packageId.isNotBlank() && it.labelIds.any { id -> id > 0L } }
                .map {
                    RackCensusPackage(
                        packageId = it.packageId,
                        labelIds = it.labelIds.filter { id -> id > 0L }.toSet(),
                        expectedLocationId = it.expectedLocationId,
                        reference = it.reference,
                        line = it.line,
                        estatus = it.estatus,
                        cliente = it.cliente,
                        descripcion = it.descripcion,
                        declaredTotal = it.declaredTotal,
                        printedOrdinal = it.printedOrdinal,
                        inScope = it.inScope
                    )
                },
            foreignCatalogTruncated = entity.foreignCatalogTruncated,
            dataWarnings = warnings
        )
    }

    fun decisionsOf(entity: RackCensusSessionEntity): Map<String, RackCensusNotSeenDecision> =
        runCatching { gson.fromJson<Map<String, String>>(entity.decisionsJson, decisionsType) }
            .getOrNull().orEmpty()
            .mapNotNull { (key, value) ->
                runCatching { RackCensusNotSeenDecision.valueOf(value) }.getOrNull()?.let { key to it }
            }
            .toMap()

    fun acknowledgedOf(entity: RackCensusSessionEntity): Set<String> =
        runCatching { gson.fromJson<List<String>>(entity.acknowledgedJson, stringsType) }
            .getOrNull().orEmpty().toSet()

    // ── Barrer ───────────────────────────────────────────────────────────────

    /**
     * Registra una lectura. Repetir el mismo código en la misma ubicación no
     * agrega nada: el bulto ya quedó observado y no se vuelve a contar.
     */
    suspend fun recordScan(
        sessionId: String,
        rawCode: String,
        observedLocationId: String,
        observedAtMillis: Long = System.currentTimeMillis()
    ): RackCensusScanFeedback = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = requireNotNull(dao.getSession(sessionId)) { "No se encontró la sesión de censo." }
            requireOwner(entity)
            check(entity.status == RackCensusStatus.OPEN) { "La sesión ya no admite lecturas." }
            val snapshot = snapshotOf(entity)
            val location = RackCensusRules.normalizeLocation(observedLocationId)
            require(location in snapshot.scopeLocationIds) { "La ubicación leída está fuera del alcance del censo." }
            // La observación nunca es anterior al snapshot: el reloj del equipo
            // puede ir atrasado y el dominio rechazaría la lectura.
            val observedAt = maxOf(observedAtMillis, entity.capturedAtMillis)
            val scan = RackCensusRules.resolveScan(
                snapshot = snapshot,
                observationId = UUID.randomUUID().toString(),
                rawCode = rawCode,
                observedLocationId = location,
                observedAtMillis = observedAt
            )
            val previous = dao.observationsForCode(sessionId, scan.normalizedCode)
            val sameLocation = previous.firstOrNull { it.observedLocationId == location }
            val pkg = scan.packageId?.let { id -> snapshot.packages.firstOrNull { it.packageId == id } }

            if (sameLocation != null) {
                return@withContext RackCensusScanFeedback(
                    outcome = RackCensusScanOutcome.ALREADY_SEEN,
                    normalizedCode = scan.normalizedCode,
                    packageId = scan.packageId,
                    reference = pkg?.reference.orEmpty(),
                    expectedLocationId = pkg?.expectedLocationId.orEmpty(),
                    message = "Ya leído en $location. No se cuenta dos veces."
                )
            }

            dao.insertObservation(
                RackCensusObservationEntity(
                    observationId = scan.observationId,
                    sessionId = sessionId,
                    owner = entity.owner,
                    rawCode = scan.rawCode,
                    normalizedCode = scan.normalizedCode,
                    observedLocationId = scan.observedLocationId,
                    observedAtMillis = scan.observedAtMillis,
                    resolution = scan.resolution.name,
                    packageId = scan.packageId,
                    labelId = scan.labelId,
                    printedOrdinal = scan.printedOrdinal,
                    declaredTotal = scan.declaredTotal
                )
            )
            scheduleObservationSync(sessionId)

            val outcome = when {
                previous.isNotEmpty() && scan.resolution == RackCensusResolution.RESOLVED ->
                    RackCensusScanOutcome.CONFLICTING_LOCATION
                scan.resolution == RackCensusResolution.MALFORMED -> RackCensusScanOutcome.MALFORMED
                scan.resolution == RackCensusResolution.FOLDER_LABEL -> RackCensusScanOutcome.FOLDER_LABEL
                scan.resolution == RackCensusResolution.UNKNOWN_CODE -> RackCensusScanOutcome.UNIDENTIFIED
                pkg != null && pkg.expectedLocationId.equals(location, ignoreCase = true) ->
                    RackCensusScanOutcome.NEW_IN_PLACE
                else -> RackCensusScanOutcome.NEW_ELSEWHERE
            }
            RackCensusScanFeedback(
                outcome = outcome,
                normalizedCode = scan.normalizedCode,
                packageId = scan.packageId,
                reference = pkg?.reference.orEmpty(),
                expectedLocationId = pkg?.expectedLocationId.orEmpty(),
                message = when (outcome) {
                    RackCensusScanOutcome.NEW_IN_PLACE -> "Coincide con la ubicación esperada."
                    RackCensusScanOutcome.NEW_ELSEWHERE ->
                        "Se esperaba en ${pkg?.expectedLocationId.orEmpty()}; se registró el hallazgo."
                    RackCensusScanOutcome.CONFLICTING_LOCATION ->
                        "El mismo código ya se leyó en otra ubicación. Queda para revisión."
                    RackCensusScanOutcome.FOLDER_LABEL -> "Es la etiqueta de carpeta, no un bulto."
                    RackCensusScanOutcome.UNIDENTIFIED ->
                        if (snapshot.foreignCatalogTruncated) "Código fuera del catálogo local. Falta resolver en línea."
                        else "El código no corresponde a ninguna etiqueta del inventario."
                    RackCensusScanOutcome.MALFORMED -> "La lectura no parece una etiqueta IMEX."
                    RackCensusScanOutcome.ALREADY_SEEN -> ""
                }
            )
        }
    }

    /** Arma el reporte local con lo que hay guardado; no necesita red. */
    suspend fun report(
        sessionId: String,
        movements: List<RackCensusMovementReview> = emptyList()
    ): RackCensusReport? = withContext(Dispatchers.IO) {
        val entity = dao.getSession(sessionId) ?: return@withContext null
        val snapshot = snapshotOf(entity)
        val decisions = decisionsOf(entity)
        val acknowledged = acknowledgedOf(entity)
        val scans = dao.observations(sessionId).map { it.toScan() }
        val confirmations = decisions
            .filterValues { it == RackCensusNotSeenDecision.CONFIRM_MISSING }
            .keys
            .map {
                InventoryMissingConfirmation(
                    packageId = it,
                    confirmedBy = entity.owner,
                    confirmedAtMillis = maxOf(entity.updatedAt, entity.capturedAtMillis)
                )
            }
        RackCensusRules.reconcile(
            snapshot = snapshot,
            scans = scans,
            movements = movements,
            decisions = decisions,
            missingConfirmations = confirmations,
            acknowledgedMovementPackageIds = acknowledged
        )
    }

    suspend fun saveDecisions(
        sessionId: String,
        decisions: Map<String, RackCensusNotSeenDecision>,
        acknowledged: Set<String>
    ): Int = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = requireNotNull(dao.getSession(sessionId)) { "No se encontró la sesión de censo." }
            requireOwner(entity)
            dao.updateDecisions(
                sessionId = sessionId,
                owner = entity.owner,
                decisionsJson = gson.toJson(decisions.mapValues { it.value.name }),
                acknowledgedJson = gson.toJson(acknowledged.toList()),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    // ── Sincronizar lecturas ─────────────────────────────────────────────────

    fun scheduleObservationSync(sessionId: String) {
        if (!RackCensusSyncPolicy.isValidSessionId(sessionId)) return
        val work = OneTimeWorkRequestBuilder<RackCensusSyncWorker>()
            .setInputData(workDataOf("sessionId" to sessionId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rack-census-observations-$sessionId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work
        )
    }

    /** Devuelve true cuando WorkManager debe volver a intentar. */
    suspend fun syncObservations(sessionId: String): Boolean = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = dao.getSession(sessionId) ?: return@withContext false
            val session = currentSession() ?: return@withContext false
            if (!RackCensusSyncPolicy.isOwner(entity.owner, session.username)) return@withContext false
            val pending = dao.pendingObservations(sessionId, RackCensusSyncPolicy.OBSERVATION_BATCH)
            if (pending.isEmpty()) return@withContext false
            try {
                val response = api.sendRackCensusObservations(
                    sessionId,
                    RackCensusObserveRequest(
                        sessionId = sessionId,
                        observations = pending.map {
                            RackCensusObservationDto(
                                observationId = it.observationId,
                                rawCode = it.rawCode,
                                observedLocationId = it.observedLocationId,
                                observedAtMillis = it.observedAtMillis
                            )
                        }
                    ),
                    authorizationHeader(session.token)
                )
                val body = response.body()
                if (response.isSuccessful && body?.success == true &&
                    RackCensusSyncPolicy.matchesSession(sessionId, body.sessionId)
                ) {
                    val results = body.results.associateBy { it.observationId }
                    pending.forEach { observation ->
                        val result = results[observation.observationId]
                        if (result == null) {
                            // Sin resultado explícito la lectura sigue pendiente;
                            // el mismo identificador la vuelve idempotente.
                            return@forEach
                        }
                        dao.markObservation(
                            observationId = observation.observationId,
                            syncStatus = if (result.accepted) RackCensusObservationSync.SYNCED
                            else RackCensusObservationSync.REJECTED,
                            resolution = result.resolution.ifBlank { observation.resolution },
                            packageId = result.packageId ?: observation.packageId,
                            labelId = result.labelId ?: observation.labelId,
                            message = result.message
                        )
                    }
                    dao.pendingCount(sessionId) > 0
                } else {
                    val retryable = RackCensusSyncPolicy.isRetryableHttp(response.code(), body?.code)
                    if (!retryable) {
                        dao.transition(
                            sessionId, entity.owner, entity.status, RackCensusStatus.BLOCKED,
                            body?.message ?: "No se pudieron registrar las lecturas (HTTP ${response.code()}).",
                            null, System.currentTimeMillis()
                        )
                    }
                    retryable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                RackCensusSyncPolicy.isRetryableNetwork(error)
            } catch (error: Exception) {
                dao.transition(
                    sessionId, entity.owner, entity.status, RackCensusStatus.BLOCKED,
                    "No se pudieron registrar las lecturas: ${error.message.orEmpty()}", null,
                    System.currentTimeMillis()
                )
                false
            }
        }
    }

    // ── Conciliar ────────────────────────────────────────────────────────────

    /** Consulta los movimientos ocurridos después del snapshot. Requiere red. */
    suspend fun refreshMovements(sessionId: String): Result<List<RackCensusMovementDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val session = requireNotNull(currentSession()) { "Inicia sesión para revisar movimientos." }
                val response = api.getRackCensusMovements(sessionId, authorizationHeader(session.token))
                val body = response.body()
                if (!response.isSuccessful || body?.success != true ||
                    !RackCensusSyncPolicy.matchesSession(sessionId, body.sessionId)
                ) {
                    throw IllegalStateException(body?.message ?: "No se pudieron consultar los movimientos del recorrido.")
                }
                body.movements
            }
        }

    /**
     * Congela la conciliación y la deja en manos de WorkManager. Un reintento
     * manda exactamente la misma petición con el mismo identificador de sesión.
     */
    suspend fun queueReconcile(sessionId: String, request: RackCensusReconcileRequest) =
        lockFor(sessionId).withLock {
            withContext(Dispatchers.IO) {
                val entity = requireNotNull(dao.getSession(sessionId)) { "No se encontró la sesión de censo." }
                requireOwner(entity)
                require(request.sessionId == sessionId) { "La conciliación no corresponde a esta sesión." }
                require(request.snapshotVersion == entity.snapshotVersion) {
                    "El inventario de referencia cambió. Vuelve a revisar antes de conciliar."
                }
                check(RackCensusSyncPolicy.canFreeze(entity.status, entity.reconcileRequestJson)) {
                    "Esta conciliación ya se envió; espera su resultado."
                }
                check(dao.pendingCount(sessionId) == 0) {
                    "Faltan lecturas por sincronizar. Conéctate antes de conciliar."
                }
                val frozen = dao.freezeReconcile(
                    sessionId, entity.owner, gson.toJson(request), System.currentTimeMillis()
                )
                check(frozen == 1) { "La sesión cambió mientras se preparaba el envío. Revisa de nuevo." }
                scheduleReconcile(sessionId)
            }
        }

    fun scheduleReconcile(sessionId: String) {
        if (!RackCensusSyncPolicy.isValidSessionId(sessionId)) return
        val work = OneTimeWorkRequestBuilder<RackCensusReconcileWorker>()
            .setInputData(workDataOf("sessionId" to sessionId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "rack-census-reconcile-$sessionId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work
        )
    }

    suspend fun syncReconcile(sessionId: String): Boolean = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = dao.getSession(sessionId) ?: return@withContext false
            if (entity.status != RackCensusStatus.RECONCILING) return@withContext false
            val session = currentSession() ?: return@withContext false
            if (!RackCensusSyncPolicy.isOwner(entity.owner, session.username)) return@withContext false
            val request = runCatching {
                gson.fromJson(requireNotNull(entity.reconcileRequestJson), RackCensusReconcileRequest::class.java)
            }.getOrNull()
            if (request == null || request.sessionId != sessionId) {
                dao.transition(
                    sessionId, entity.owner, RackCensusStatus.RECONCILING, RackCensusStatus.BLOCKED,
                    "No se encontró una conciliación válida guardada. Revisa el recorrido y vuelve a enviarla.",
                    null, System.currentTimeMillis()
                )
                return@withContext false
            }
            try {
                val response = api.reconcileRackCensus(sessionId, request, authorizationHeader(session.token))
                val body = response.body() ?: response.errorBody()?.string()?.let(::parseReconcileResponse)
                if (response.isSuccessful && body?.success == true &&
                    RackCensusSyncPolicy.matchesReconcile(
                        sessionId, body.sessionId, body.counts != null, body.closedAtMillis
                    )
                ) {
                    dao.transition(
                        sessionId, entity.owner, RackCensusStatus.RECONCILING, RackCensusStatus.RECONCILED,
                        null, gson.toJson(body), System.currentTimeMillis()
                    )
                    false
                } else {
                    val retryable = RackCensusSyncPolicy.isRetryableHttp(response.code(), body?.code)
                    dao.transition(
                        sessionId, entity.owner, RackCensusStatus.RECONCILING,
                        if (retryable) RackCensusStatus.RECONCILING else RackCensusStatus.BLOCKED,
                        body?.message ?: "No se pudo conciliar el censo (HTTP ${response.code()}).",
                        body?.let(gson::toJson), System.currentTimeMillis()
                    )
                    retryable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                val retryable = RackCensusSyncPolicy.isRetryableNetwork(error)
                dao.transition(
                    sessionId, entity.owner, RackCensusStatus.RECONCILING,
                    if (retryable) RackCensusStatus.RECONCILING else RackCensusStatus.BLOCKED,
                    if (retryable) "Conciliación pendiente de enviar. Se conservó el recorrido completo."
                    else "No se pudo establecer una conexión segura: ${error.message.orEmpty()}",
                    null, System.currentTimeMillis()
                )
                retryable
            } catch (error: Exception) {
                dao.transition(
                    sessionId, entity.owner, RackCensusStatus.RECONCILING, RackCensusStatus.BLOCKED,
                    "No se pudo procesar la conciliación: ${error.message.orEmpty()}", null,
                    System.currentTimeMillis()
                )
                false
            }
        }
    }

    /** Reabre una conciliación bloqueada para corregirla sin perder las lecturas. */
    suspend fun reopen(sessionId: String) = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = requireNotNull(dao.getSession(sessionId)) { "No se encontró la sesión de censo." }
            requireOwner(entity)
            check(entity.status == RackCensusStatus.BLOCKED) { "Solo una conciliación bloqueada se puede corregir." }
            val reopened = dao.reopen(
                sessionId, entity.owner,
                "La conciliación anterior no se aceptó. Revisa los hallazgos y vuelve a enviarla.",
                System.currentTimeMillis()
            )
            check(reopened == 1) { "El estado de la sesión cambió. Vuelve a consultarla." }
        }
    }

    /** Retira una sesión que ya no se va a conciliar. No borra nada del servidor. */
    suspend fun discard(sessionId: String) = lockFor(sessionId).withLock {
        withContext(Dispatchers.IO) {
            val entity = dao.getSession(sessionId) ?: return@withContext
            requireOwner(entity)
            check(entity.status != RackCensusStatus.RECONCILING) {
                "Espera el resultado de la conciliación antes de retirar el censo."
            }
            dao.discardSession(sessionId, entity.owner, entity.status)
        }
    }

    private fun requireOwner(entity: RackCensusSessionEntity) {
        val session = requireNotNull(currentSession()) { "Inicia sesión con el usuario que abrió este censo." }
        check(RackCensusSyncPolicy.isOwner(entity.owner, session.username)) {
            "El censo pertenece a otro usuario."
        }
    }

    private fun parseOpenResponse(json: String): RackCensusOpenResponse? =
        runCatching { gson.fromJson(json, RackCensusOpenResponse::class.java) }.getOrNull()

    private fun parseReconcileResponse(json: String): RackCensusReconcileResponse? =
        runCatching { gson.fromJson(json, RackCensusReconcileResponse::class.java) }.getOrNull()
}

/** La clasificación guardada vuelve a ser una lectura comparable. */
internal fun RackCensusObservationEntity.toScan(): RackCensusScan = RackCensusScan(
    observationId = observationId,
    rawCode = rawCode,
    normalizedCode = normalizedCode,
    observedLocationId = observedLocationId,
    observedAtMillis = observedAtMillis,
    resolution = runCatching { RackCensusResolution.valueOf(resolution) }
        .getOrDefault(RackCensusResolution.MALFORMED),
    packageId = packageId,
    labelId = labelId,
    printedOrdinal = printedOrdinal,
    declaredTotal = declaredTotal
)

/** Un movimiento del servidor se vuelve una fila de la compuerta de revisión. */
internal fun RackCensusMovementDto.toReview(): RackCensusMovementReview = RackCensusMovementReview(
    packageId = packageId,
    reference = reference,
    expectedLocationId = expectedLocationId,
    kind = runCatching { com.example.pmmovil.data.inventory.RackCensusMovementKind.valueOf(kind) }
        .getOrDefault(com.example.pmmovil.data.inventory.RackCensusMovementKind.STATUS_CHANGED),
    fromLocationId = fromLocationId,
    toLocationId = toLocationId,
    detectedVersion = detectedVersion
)

/** Las decisiones humanas viajan al servidor como confirmaciones explícitas. */
internal fun Map<String, RackCensusNotSeenDecision>.toMissingDtos(confirmedAtMillis: Long): List<RackCensusMissingDto> =
    filterValues { it == RackCensusNotSeenDecision.CONFIRM_MISSING }
        .keys
        .map { RackCensusMissingDto(it, confirmedAtMillis) }
