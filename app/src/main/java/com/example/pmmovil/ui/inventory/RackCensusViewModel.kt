package com.example.pmmovil.ui.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.inventory.RackCensusMovementReview
import com.example.pmmovil.data.inventory.RackCensusNotSeenDecision
import com.example.pmmovil.data.inventory.RackCensusReport
import com.example.pmmovil.data.inventory.RackCensusRules
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.model.RackCensusReconcileRequest
import com.example.pmmovil.data.model.RackCensusSessionEntity
import com.example.pmmovil.data.model.RackCensusStatus
import com.example.pmmovil.data.model.WarehouseZone
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RackCensusRepository
import com.example.pmmovil.data.repository.RackCensusScanFeedback
import com.example.pmmovil.data.repository.WarehouseLayoutRepository
import com.example.pmmovil.data.repository.toMissingDtos
import com.example.pmmovil.data.repository.toReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Los tres pasos del censo: identificar la ubicación, barrer y conciliar. */
enum class RackCensusStep { LOCATION, SWEEP, RECONCILE }

/** Una lectura ya hecha, para marcarla en la lista del barrido. */
data class RackCensusSeenCode(
    val normalizedCode: String,
    val locationId: String,
    val resolved: Boolean,
    val packageId: String?,
    val reference: String,
    val synced: Boolean
)

data class RackCensusUiState(
    val step: RackCensusStep = RackCensusStep.LOCATION,
    val isLoading: Boolean = false,
    val canOpenCensus: Boolean = false,
    val canReconcile: Boolean = false,
    val zones: List<WarehouseZone> = emptyList(),
    val zonesError: String? = null,
    val selectedLocations: List<String> = emptyList(),
    val session: RackCensusSessionEntity? = null,
    val activeSessions: List<RackCensusSessionEntity> = emptyList(),
    val seenCodes: List<RackCensusSeenCode> = emptyList(),
    val pendingSync: Int = 0,
    val report: RackCensusReport? = null,
    val movements: List<RackCensusMovementReview> = emptyList(),
    val movementsCheckedAtMillis: Long = 0L,
    val movementsError: String? = null,
    val isCheckingMovements: Boolean = false,
    val lastFeedback: RackCensusScanFeedback? = null,
    /** Cambia con cada lectura para que el aviso suene una vez por lectura. */
    val feedbackToken: Int = 0,
    val error: String? = null,
    val info: String? = null
) {
    val expectedInScope: Int get() = report?.counts?.expectedInScope ?: 0
    val uniqueObserved: Int get() = report?.counts?.uniqueObservedPackages ?: 0
}

@HiltViewModel
class RackCensusViewModel @Inject constructor(
    private val repository: RackCensusRepository,
    private val layoutRepository: WarehouseLayoutRepository,
    private val auth: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RackCensusUiState())
    val uiState: StateFlow<RackCensusUiState> = _uiState.asStateFlow()
    private var sessionJob: Job? = null

    init {
        _uiState.update {
            it.copy(
                canOpenCensus = auth.hasPermission(MobileModuleIds.INVENTORY, "Agregar"),
                canReconcile = auth.hasPermission(MobileModuleIds.INVENTORY, "Modificar")
            )
        }
        loadZones()
        observeActiveSessions()
    }

    private fun loadZones() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            layoutRepository.getLayout()
                .onSuccess { layout ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            zones = layout.zones.filter { zone -> zone.id.isNotBlank() },
                            zonesError = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            zonesError = error.message ?: "No se pudo cargar el mapa de bodega."
                        )
                    }
                }
        }
    }

    private fun observeActiveSessions() {
        viewModelScope.launch {
            repository.observeActiveSessions().collect { sessions ->
                _uiState.update { it.copy(activeSessions = sessions) }
            }
        }
    }

    // ── Paso 1: identificar la ubicación ─────────────────────────────────────

    /**
     * Acepta el QR fijo del rack. El valor tiene que corresponder a una zona del
     * mapa: una ubicación que el mapa no conoce no se da por buena.
     */
    fun onLocationScanned(raw: String) {
        val value = RackCensusRules.normalizeLocation(raw.removePrefix("UBICA:").removePrefix("ubica:"))
        val zone = _uiState.value.zones.firstOrNull { it.id.equals(value, ignoreCase = true) }
        if (zone == null) {
            _uiState.update {
                it.copy(error = "El código «$value» no corresponde a una ubicación del mapa de bodega.")
            }
            return
        }
        toggleLocation(zone.id)
    }

    /** Selección en el mapa; el revisor la confirma antes de abrir la sesión. */
    fun toggleLocation(locationId: String) {
        val id = RackCensusRules.normalizeLocation(locationId)
        if (id.isEmpty()) return
        _uiState.update { state ->
            val selected = state.selectedLocations.toMutableList()
            if (!selected.remove(id)) selected.add(id)
            state.copy(selectedLocations = selected, error = null)
        }
    }

    fun clearLocations() = _uiState.update { it.copy(selectedLocations = emptyList()) }

    fun openSession() {
        val locations = _uiState.value.selectedLocations
        if (locations.isEmpty()) {
            _uiState.update { it.copy(error = "Confirma al menos una ubicación antes de abrir el censo.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            repository.openSession(WarehouseLayoutRepository.DEFAULT_WAREHOUSE, locations)
                .onSuccess { session ->
                    _uiState.update { it.copy(isLoading = false, step = RackCensusStep.SWEEP) }
                    attachSession(session.sessionId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, error = error.message ?: "No se pudo abrir el censo.")
                    }
                }
        }
    }

    /** Continúa un recorrido guardado: el barrido sobrevive al cierre de la app. */
    fun resumeSession(sessionId: String) {
        _uiState.update { it.copy(step = RackCensusStep.SWEEP, error = null) }
        attachSession(sessionId)
    }

    private fun attachSession(sessionId: String) {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            combine(
                repository.observeSession(sessionId),
                repository.observeObservations(sessionId),
                repository.observePendingCount(sessionId)
            ) { session, observations, pending -> Triple(session, observations, pending) }
                .collect { (session, observations, pending) ->
                    val seen = observations.map { observation ->
                        RackCensusSeenCode(
                            normalizedCode = observation.normalizedCode,
                            locationId = observation.observedLocationId,
                            resolved = observation.packageId != null,
                            packageId = observation.packageId,
                            reference = observation.normalizedCode.substringBefore(' '),
                            synced = observation.syncStatus != "PENDING"
                        )
                    }
                    // Al continuar un recorrido guardado, el alcance se recupera
                    // del snapshot: sin esto el barrido no sabría en qué
                    // ubicación está y rechazaría todas las lecturas.
                    val scope = session?.let { repository.snapshotOf(it).scopeLocationIds.sorted() }
                    _uiState.update { state ->
                        state.copy(
                            session = session,
                            seenCodes = seen,
                            pendingSync = pending,
                            selectedLocations = if (!scope.isNullOrEmpty()) scope else state.selectedLocations
                        )
                    }
                    refreshReport(sessionId)
                }
        }
    }

    // ── Paso 2: barrer etiquetas ─────────────────────────────────────────────

    /** El rack donde se está barriendo; con un solo alcance no hay que elegir. */
    fun sweepLocation(): String =
        _uiState.value.selectedLocations.singleOrNull()
            ?: _uiState.value.selectedLocations.firstOrNull().orEmpty()

    fun onLabelScanned(raw: String, locationId: String = sweepLocation()) {
        val sessionId = _uiState.value.session?.sessionId ?: return
        if (locationId.isBlank()) {
            _uiState.update { it.copy(error = "Indica en qué ubicación estás barriendo.") }
            return
        }
        viewModelScope.launch {
            runCatching { repository.recordScan(sessionId, raw, locationId) }
                .onSuccess { feedback ->
                    _uiState.update {
                        it.copy(lastFeedback = feedback, feedbackToken = it.feedbackToken + 1, error = null)
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "No se pudo registrar la lectura.") }
                }
        }
    }

    fun syncNow() {
        val sessionId = _uiState.value.session?.sessionId ?: return
        repository.scheduleObservationSync(sessionId)
        _uiState.update { it.copy(info = "Sincronización de lecturas en cola.") }
    }

    // ── Paso 3: conciliar ────────────────────────────────────────────────────

    fun goToReconcile() {
        val sessionId = _uiState.value.session?.sessionId ?: return
        _uiState.update { it.copy(step = RackCensusStep.RECONCILE) }
        checkMovements(sessionId)
    }

    fun backToSweep() = _uiState.update { it.copy(step = RackCensusStep.SWEEP) }

    fun checkMovements(sessionId: String = _uiState.value.session?.sessionId.orEmpty()) {
        if (sessionId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingMovements = true, movementsError = null) }
            repository.refreshMovements(sessionId)
                .onSuccess { movements ->
                    _uiState.update {
                        it.copy(
                            isCheckingMovements = false,
                            movements = movements.map { movement -> movement.toReview() },
                            movementsCheckedAtMillis = System.currentTimeMillis()
                        )
                    }
                    refreshReport(sessionId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isCheckingMovements = false,
                            movementsError = error.message
                                ?: "No se pudieron consultar los movimientos del recorrido."
                        )
                    }
                }
        }
    }

    fun decide(packageId: String, decision: RackCensusNotSeenDecision) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val decisions = repository.decisionsOf(session).toMutableMap()
            if (decision == RackCensusNotSeenDecision.UNDECIDED) decisions.remove(packageId)
            else decisions[packageId] = decision
            persistDecisions(session.sessionId, decisions, repository.acknowledgedOf(session))
        }
    }

    fun acknowledgeMovement(packageId: String, acknowledged: Boolean) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            val current = repository.acknowledgedOf(session).toMutableSet()
            if (acknowledged) current.add(packageId) else current.remove(packageId)
            persistDecisions(session.sessionId, repository.decisionsOf(session), current)
        }
    }

    private suspend fun persistDecisions(
        sessionId: String,
        decisions: Map<String, RackCensusNotSeenDecision>,
        acknowledged: Set<String>
    ) {
        runCatching { repository.saveDecisions(sessionId, decisions, acknowledged) }
            .onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "No se pudo guardar la decisión.") }
            }
        refreshReport(sessionId)
    }

    fun confirmReconciliation() {
        val session = _uiState.value.session ?: return
        val report = _uiState.value.report ?: return
        if (!report.canConfirm) {
            _uiState.update { it.copy(error = report.blockReasons.firstOrNull() ?: "Faltan decisiones por tomar.") }
            return
        }
        viewModelScope.launch {
            val decisions = repository.decisionsOf(session)
            val request = RackCensusReconcileRequest(
                sessionId = session.sessionId,
                snapshotVersion = session.snapshotVersion,
                acknowledgedMovementPackageIds = repository.acknowledgedOf(session).toList(),
                missingConfirmations = decisions.toMissingDtos(System.currentTimeMillis()),
                keptPendingPackageIds = decisions
                    .filterValues { it == RackCensusNotSeenDecision.KEEP_PENDING }.keys.toList()
            )
            runCatching { repository.queueReconcile(session.sessionId, request) }
                .onSuccess {
                    _uiState.update {
                        it.copy(info = "Conciliación enviada. Se conserva el recorrido hasta que el servidor confirme.")
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "No se pudo enviar la conciliación.") }
                }
        }
    }

    fun retryReconciliation() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            if (session.status == RackCensusStatus.BLOCKED) {
                runCatching { repository.reopen(session.sessionId) }
                    .onFailure { error ->
                        _uiState.update { it.copy(error = error.message ?: "No se pudo reabrir la conciliación.") }
                    }
            } else {
                repository.scheduleReconcile(session.sessionId)
            }
        }
    }

    fun discardSession() {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            runCatching { repository.discard(session.sessionId) }
                .onSuccess {
                    sessionJob?.cancel()
                    _uiState.update {
                        it.copy(
                            step = RackCensusStep.LOCATION, session = null, report = null,
                            seenCodes = emptyList(), movements = emptyList(), selectedLocations = emptyList(),
                            info = "Censo retirado del equipo."
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(error = error.message ?: "No se pudo retirar el censo.") }
                }
        }
    }

    private suspend fun refreshReport(sessionId: String) {
        val report = runCatching { repository.report(sessionId, _uiState.value.movements) }.getOrNull()
        _uiState.update { it.copy(report = report) }
    }

    fun consumeFeedback() = _uiState.update { it.copy(lastFeedback = null) }
    fun consumeMessages() = _uiState.update { it.copy(error = null, info = null) }
}
