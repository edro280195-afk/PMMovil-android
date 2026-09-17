package com.example.pmmovil.ui.asignaciones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.AsignacionEntity
import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.data.repository.AsignacionRepository
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.data.scanner.ScanEventBus
import com.example.pmmovil.data.signalr.WarehouseHubService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DrawerTab { MIAS, DISPO }

enum class FilterStatus { TOTAL, PENDIENTE, REVISION, PAUSADA, FINALIZADO }

enum class PendingMessageKind { INFO, SUCCESS, WARNING, ERROR }

sealed interface AsignacionesEvent {
    data class ShowMessage(
        val message: String,
        val kind: PendingMessageKind
    ) : AsignacionesEvent

    data class NavigateToRevision(
        val crefAgrupada: String,
        val mode: ModePreferences.Mode
    ) : AsignacionesEvent

    // Distinto de ShowMessage: dispara el pitido + voz "Entrada autorizada"
    // además del snackbar. Un mensaje genérico no debía cargar con eso.
    data class UnlockApproved(val supervisor: String) : AsignacionesEvent

    // Igual, pero para "Revisión asignada" -- pasivo a propósito: no
    // interrumpe si el revisor está trabajando en otra pantalla (este
    // ViewModel solo vive mientras el listado está en pantalla), el aviso
    // sonoro es lo único que se lleva de inmediato; la tarjeta con la
    // asignación nueva ya aparece sola al volver al listado.
    data object RevisionAssigned : AsignacionesEvent
}

data class AsignacionesUiState(
    val drawerTab: DrawerTab = DrawerTab.MIAS,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isSearching: Boolean = false,
    val isOpeningEntry: Boolean = false,
    val isSubmittingUnlock: Boolean = false,
    val misActivas: List<EntradaPendienteDto> = emptyList(),
    val misAsignaciones: List<AsignacionEntity> = emptyList(),
    val todasEntradas: List<EntradaPendienteDto> = emptyList(),
    val filtroTexto: String = "",
    val statusActivo: FilterStatus = FilterStatus.TOTAL,
    val selectedEntry: EntradaPendienteDto? = null,
    val desbloqueoDialog: EntradaPendienteDto? = null,
    val unlockRequests: Set<String> = emptySet(),
    val removedAssignmentReferences: Set<String> = emptySet(),
    val truncated: Boolean = false
)

@HiltViewModel
class AsignacionesViewModel @Inject constructor(
    private val asignacionRepository: AsignacionRepository,
    private val revisionRepository: RevisionRepository,
    private val authRepository: AuthRepository,
    private val hubService: WarehouseHubService,
    private val scanBus: ScanEventBus,
    private val modePrefs: ModePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(AsignacionesUiState())
    val uiState: StateFlow<AsignacionesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AsignacionesEvent>(extraBufferCapacity = 12)
    val events = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        hubService.startConnection()
        observeAsignaciones()
        observeSignalR()
        observeScans()
        loadData()
    }

    private fun observeAsignaciones() {
        viewModelScope.launch {
            asignacionRepository.getAsignaciones().collect { assignments ->
                _uiState.value = _uiState.value.copy(misAsignaciones = assignments)
            }
        }
    }

    private fun observeSignalR() {
        // Cada collector corre en su propio viewModelScope.launch sin
        // CoroutineExceptionHandler: un mensaje de SignalR con un dato
        // inesperado (o una pantalla abierta justo cuando llega, como el
        // modal "Preparando la revisión...") podía tronar toda la app en
        // vez de solo perderse ese evento en vivo. El try/catch aísla cada
        // mensaje -- lo peor que pasa ahora es que ese evento puntual no
        // se procese, no que la app se cierre.
        viewModelScope.launch {
            hubService.actividadFlow.collect {
                runCatching { loadData(showLoading = false) }
            }
        }
        viewModelScope.launch {
            hubService.nuevaAsignacionFlow.collect { assignment ->
                runCatching {
                    _uiState.value = _uiState.value.copy(
                        removedAssignmentReferences = _uiState.value.removedAssignmentReferences - assignment.crefAgrupada
                    )
                    asignacionRepository.syncAsignaciones()
                    loadData(showLoading = false)
                    _events.tryEmit(AsignacionesEvent.RevisionAssigned)
                    showMessage(
                        message = "Revisión asignada: ${assignment.crefAgrupada}. Ya está en tu lista.",
                        kind = PendingMessageKind.WARNING
                    )
                }
            }
        }
        viewModelScope.launch {
            hubService.asignacionRetiradaFlow.collect { assignment ->
                runCatching {
                    asignacionRepository.removeAssignment(assignment.idAsignacion)
                    removeAssignmentFromPending(assignment.idAsignacion, assignment.crefAgrupada)
                    loadData(showLoading = false)
                    showMessage(
                        message = "La asignación ${assignment.crefAgrupada} fue retirada.",
                        kind = PendingMessageKind.INFO
                    )
                }
            }
        }
        viewModelScope.launch {
            hubService.desbloqueoAprobadoFlow.collect { message ->
                runCatching {
                    val reference = message.cnofact.entryReferenceOrNull() ?: return@runCatching
                    removeUnlockRequest(reference)
                    loadData(showLoading = false)
                    _events.tryEmit(AsignacionesEvent.UnlockApproved(message.supervisor))
                    showMessage(
                        message = "Entrada autorizada — ${message.supervisor} te dio acceso. Ya puedes continuar.",
                        kind = PendingMessageKind.SUCCESS
                    )
                }
            }
        }
        viewModelScope.launch {
            hubService.desbloqueoRechazadoFlow.collect { message ->
                runCatching {
                    val reference = message.cnofact.entryReferenceOrNull() ?: return@runCatching
                    removeUnlockRequest(reference)
                    showMessage(
                        message = "Solicitud rechazada por ${message.supervisor}. La entrada sigue bloqueada.",
                        kind = PendingMessageKind.ERROR
                    )
                }
            }
        }
    }

    private fun removeAssignmentFromPending(idAsignacion: Int, crefAgrupada: String) {
        val current = _uiState.value
        _uiState.value = current.copy(
            misActivas = current.misActivas.filterNot { entry ->
                entry.idAsignacion == idAsignacion || entry.crefAgrupada == crefAgrupada
            },
            misAsignaciones = current.misAsignaciones.filterNot { assignment ->
                assignment.idAsignacion == idAsignacion || assignment.crefAgrupada == crefAgrupada
            },
            removedAssignmentReferences = current.removedAssignmentReferences + crefAgrupada
        )
    }

    private fun observeScans() {
        viewModelScope.launch {
            scanBus.scans.collect(::findEntry)
        }
    }

    fun searchExactReference(rawValue: String) {
        viewModelScope.launch {
            findEntry(rawValue)
        }
    }

    private suspend fun findEntry(rawValue: String) {
        val reference = normalizeScannedReference(rawValue)
        if (reference.isNullOrBlank()) {
            showMessage(
                message = "No se pudo leer la referencia. Vuelve a escanear.",
                kind = PendingMessageKind.ERROR
            )
            return
        }
        if (reference.length < 2) {
            showMessage(
                message = "La referencia es demasiado corta.",
                kind = PendingMessageKind.WARNING
            )
            return
        }

        _uiState.value = _uiState.value.copy(isSearching = true)
        revisionRepository.buscarPorCreferencia(reference)
            .onSuccess { entry ->
                _uiState.value = _uiState.value.copy(isSearching = false)
                if (entry == null) {
                    showMessage(
                        message = "La referencia $reference no existe o no es una entrada agrupada.",
                        kind = PendingMessageKind.ERROR
                    )
                } else {
                    selectEntry(entry)
                }
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(isSearching = false)
                showMessage(networkMessage(error), PendingMessageKind.ERROR)
            }
    }

    fun loadData(showLoading: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = showLoading && _uiState.value.todasEntradas.isEmpty(),
                isRefreshing = !showLoading || _uiState.value.todasEntradas.isNotEmpty()
            )

            val results = coroutineScope {
                val assignments = async { asignacionRepository.syncAsignaciones() }
                val mine = async { revisionRepository.getMisActivas() }
                val pending = async { revisionRepository.getEntradasPendientesPage() }
                Triple(assignments.await(), mine.await(), pending.await())
            }

            val mineResult = results.second
            val pendingResult = results.third
            val current = _uiState.value

            _uiState.value = current.copy(
                isLoading = false,
                isRefreshing = false,
                misActivas = mineResult.getOrElse { current.misActivas },
                todasEntradas = pendingResult.getOrNull()?.items ?: current.todasEntradas,
                truncated = pendingResult.getOrNull()?.truncated ?: current.truncated
            )

            if (mineResult.isFailure || pendingResult.isFailure) {
                val error = mineResult.exceptionOrNull() ?: pendingResult.exceptionOrNull()
                showMessage(
                    message = networkMessage(error),
                    kind = PendingMessageKind.ERROR
                )
            } else if (showLoading && current.todasEntradas.isNotEmpty()) {
                showMessage(
                    message = "Lista actualizada.",
                    kind = PendingMessageKind.SUCCESS
                )
            }
        }
    }

    fun setDrawerTab(tab: DrawerTab) {
        _uiState.value = _uiState.value.copy(drawerTab = tab)
    }

    fun setFiltroTexto(texto: String) {
        _uiState.value = _uiState.value.copy(filtroTexto = texto)
    }

    fun setStatusActivo(status: FilterStatus) {
        _uiState.value = _uiState.value.copy(statusActivo = status)
    }

    fun clearFilters() {
        _uiState.value = _uiState.value.copy(
            filtroTexto = "",
            statusActivo = FilterStatus.TOTAL
        )
    }

    fun selectEntry(entry: EntradaPendienteDto) {
        if (isEntryLocked(entry, getUsername())) {
            _uiState.value = _uiState.value.copy(desbloqueoDialog = entry)
            val activeOwner = entry.usuarioRevisando?.takeIf { it.isNotBlank() }
                ?: entry.usuarioTomoDocumento
            showMessage(
                message = "Esta entrada está siendo utilizada por ${activeOwner ?: "otro usuario"}.",
                kind = PendingMessageKind.WARNING
            )
            return
        }
        _uiState.value = _uiState.value.copy(selectedEntry = entry)
    }

    fun dismissModeSelector() {
        if (_uiState.value.isOpeningEntry) return
        _uiState.value = _uiState.value.copy(selectedEntry = null)
    }

    fun openSelectedEntry(mode: ModePreferences.Mode) {
        val entry = _uiState.value.selectedEntry ?: return
        if (_uiState.value.isOpeningEntry) return

        if (isEntryLocked(entry, getUsername())) {
            _uiState.value = _uiState.value.copy(
                selectedEntry = null,
                desbloqueoDialog = entry
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isOpeningEntry = true)

            val assignmentId = entry.idAsignacion
            if (assignmentId != null && entry.asignEstatus == 0) {
                // Marcar como vista es telemetría secundaria. No debe detener
                // la apertura de una revisión que sí puede cargarse ahora.
                viewModelScope.launch {
                    asignacionRepository.markViewed(assignmentId)
                        .onFailure {
                            showMessage(
                                message = "La revisión se abrió, pero no se pudo marcar la asignación como vista.",
                                kind = PendingMessageKind.WARNING
                            )
                        }
                }
                // El Monitor necesita saber que el revisor la va a tomar
                // ahora, aunque marcarla como vista haya fallado -- es el
                // mismo criterio que usa el panel web ("Tomar ahora").
                getUsername()?.let { user ->
                    hubService.informarDecisionAsignacion(entry.crefAgrupada, user, "tomar")
                }
            }

            revisionRepository.registrarTomaDocumento(entry.crefAgrupada)
                .onSuccess {
                    // Result.onSuccess no atrapa excepciones del bloque -- si algo
                    // aquí truena (p.ej. al reaccionar a este evento en pantalla)
                    // no debe tirar la app completa mientras el usuario ve
                    // "Preparando la revisión...".
                    try {
                        modePrefs.set(entry.crefAgrupada, mode)
                        _uiState.value = _uiState.value.copy(
                            isOpeningEntry = false,
                            selectedEntry = null
                        )
                        _events.emit(
                            AsignacionesEvent.NavigateToRevision(
                                crefAgrupada = entry.crefAgrupada,
                                mode = mode
                            )
                        )
                    } catch (e: Exception) {
                        _uiState.value = _uiState.value.copy(isOpeningEntry = false)
                        showMessage(
                            message = "No se pudo abrir la entrada ${entry.crefAgrupada}. Intenta de nuevo.",
                            kind = PendingMessageKind.ERROR
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isOpeningEntry = false)
                    showMessage(
                        message = networkMessage(error),
                        kind = PendingMessageKind.ERROR
                    )
                }
        }
    }

    fun showDesbloqueoDialog(entry: EntradaPendienteDto) {
        _uiState.value = _uiState.value.copy(desbloqueoDialog = entry)
    }

    fun hideDesbloqueoDialog() {
        if (_uiState.value.isSubmittingUnlock) return
        _uiState.value = _uiState.value.copy(desbloqueoDialog = null)
    }

    fun solicitarDesbloqueo(motivo: String) {
        val entry = _uiState.value.desbloqueoDialog ?: return
        val cleanReason = motivo.trim()
        if (cleanReason.length < 5) {
            showMessage(
                message = "Escribe un motivo de al menos 5 caracteres.",
                kind = PendingMessageKind.WARNING
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmittingUnlock = true)
            revisionRepository.solicitarDesbloqueo(
                cnofact = "ENTRY_${entry.crefAgrupada}",
                motivo = cleanReason
            ).onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSubmittingUnlock = false,
                    desbloqueoDialog = null,
                    unlockRequests = _uiState.value.unlockRequests + entry.crefAgrupada
                )
                showMessage(
                    message = "Solicitud enviada. Te avisaremos cuando el supervisor responda.",
                    kind = PendingMessageKind.SUCCESS
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(isSubmittingUnlock = false)
                showMessage(
                    message = error.message ?: "No se pudo enviar la solicitud.",
                    kind = PendingMessageKind.ERROR
                )
            }
        }
    }

    val misRevisionesTotales = _uiState
        .map { state -> applyFilters(buildMyEntries(state), state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entradasFiltradas = _uiState
        .map { state -> applyFilters(buildAvailableEntries(state), state) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mineCount = _uiState
        .map { buildMyEntries(it).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // A diferencia de mineCount (el total, incluye entradas ya finalizadas),
    // este es el que de verdad "requiere tu atención" -- se usa para el
    // badge de arriba, que antes contaba entradas cerradas como pendientes.
    val minePendingCount = _uiState
        .map { state ->
            buildMyEntries(state).count {
                calculateEntryStatus(it).category != EntryCategory.COMPLETED
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val availableCount = _uiState
        .map { buildAvailableEntries(it).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val counts = _uiState
        .map { state ->
            val all = if (state.drawerTab == DrawerTab.MIAS) {
                buildMyEntries(state)
            } else {
                buildAvailableEntries(state)
            }
            mapOf(
                FilterStatus.TOTAL to all.size,
                FilterStatus.PENDIENTE to all.count {
                    calculateEntryStatus(it).category == EntryCategory.PENDING
                },
                FilterStatus.REVISION to all.count {
                    calculateEntryStatus(it).category == EntryCategory.IN_PROGRESS
                },
                FilterStatus.PAUSADA to all.count {
                    calculateEntryStatus(it).category == EntryCategory.PAUSED
                },
                FilterStatus.FINALIZADO to all.count {
                    calculateEntryStatus(it).category == EntryCategory.COMPLETED
                }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private fun buildMyEntries(state: AsignacionesUiState): List<EntradaPendienteDto> {
        val username = getUsername()
        val entriesByReference = linkedMapOf<String, EntradaPendienteDto>()

        state.misActivas.forEach { entry ->
            if (entry.crefAgrupada !in state.removedAssignmentReferences) {
                entriesByReference[entry.crefAgrupada] = entry
            }
        }

        state.todasEntradas.forEach { entry ->
            val belongsToUser = entry.usuarioRevisando.sameUser(username) ||
                entry.usuarioTomoDocumento.sameUser(username)
            if (belongsToUser && entry.crefAgrupada !in state.removedAssignmentReferences) {
                entriesByReference.putIfAbsent(entry.crefAgrupada, entry)
            }
        }

        state.misAsignaciones
            .filterNot { it.crefAgrupada in state.removedAssignmentReferences }
            .forEach { assignment ->
                val baseEntry = entriesByReference[assignment.crefAgrupada]
                    ?: state.todasEntradas.firstOrNull {
                        it.crefAgrupada == assignment.crefAgrupada
                    }
                entriesByReference[assignment.crefAgrupada] =
                    baseEntry?.withAssignment(assignment) ?: assignment.toPendingProxy()
            }

        return entriesByReference.values.sortedWith(entryComparator)
    }

    private fun buildAvailableEntries(state: AsignacionesUiState): List<EntradaPendienteDto> {
        val myReferences = buildMyEntries(state)
            .mapTo(hashSetOf()) { it.crefAgrupada }
        return state.todasEntradas
            .filterNot { it.crefAgrupada in myReferences }
            .sortedWith(entryComparator)
    }

    private fun applyFilters(
        entries: List<EntradaPendienteDto>,
        state: AsignacionesUiState
    ): List<EntradaPendienteDto> {
        return entries.filter { entry ->
            matchesEntryFilter(
                entry = entry,
                query = state.filtroTexto,
                status = state.statusActivo
            )
        }
    }

    fun lastModeFor(crefAgrupada: String): ModePreferences.Mode = modePrefs.get(crefAgrupada)

    fun getUsername(): String? = authRepository.getUsername()

    private fun removeUnlockRequest(reference: String) {
        _uiState.value = _uiState.value.copy(
            unlockRequests = _uiState.value.unlockRequests - reference
        )
    }

    private fun showMessage(message: String, kind: PendingMessageKind) {
        _events.tryEmit(AsignacionesEvent.ShowMessage(message, kind))
    }

    private fun networkMessage(error: Throwable?): String {
        return when (error) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException -> "No hay conexión con el servidor. Verifica tu red e inténtalo de nuevo."

            null -> "No se pudo completar la operación."
            else -> error.message?.takeIf { it.isNotBlank() }
                ?: "No se pudo completar la operación."
        }
    }

    private companion object {
        val entryComparator = compareByDescending<EntradaPendienteDto> {
            it.esAsignada == true
        }.thenByDescending {
            it.asignPrioridad ?: -1
        }.thenByDescending {
            it.asignFecha.orEmpty()
        }.thenByDescending {
            it.dFecha.orEmpty()
        }
    }
}

private fun String?.sameUser(other: String?): Boolean {
    return !this.isNullOrBlank() && !other.isNullOrBlank() &&
        this.trim().equals(other.trim(), ignoreCase = true)
}

private fun String.entryReferenceOrNull(): String? {
    return takeIf { it.startsWith("ENTRY_", ignoreCase = true) }
        ?.drop(6)
        ?.takeIf { it.isNotBlank() }
}

private fun EntradaPendienteDto.withAssignment(
    assignment: AsignacionEntity
): EntradaPendienteDto {
    return copy(
        esAsignada = true,
        asignPrioridad = assignment.prioridad,
        idAsignacion = assignment.idAsignacion,
        asignSupervisor = assignment.supervisor,
        asignNota = assignment.nota,
        asignEstatus = assignment.estatus,
        asignFecha = assignment.fechaAsignacion
    )
}

private fun AsignacionEntity.toPendingProxy(): EntradaPendienteDto {
    return EntradaPendienteDto(
        crefAgrupada = crefAgrupada,
        nombreReferencia = crefAgrupada,
        clienteId = null,
        nombreCliente = nombreCliente,
        dFecha = fechaAsignacion,
        totalFacturas = totalFacturas,
        facturasCompletas = facturasCompletas,
        totalPartidas = 0,
        partidasRevisadas = 0,
        inicioRevision = null,
        finRevision = null,
        usuarioRevisando = null,
        usuarioTomoDocumento = null,
        todasRelacionCompleta = false,
        esAsignada = true,
        asignPrioridad = prioridad,
        idAsignacion = idAsignacion,
        asignSupervisor = supervisor,
        asignNota = nota,
        asignEstatus = estatus,
        asignFecha = fechaAsignacion
    )
}
