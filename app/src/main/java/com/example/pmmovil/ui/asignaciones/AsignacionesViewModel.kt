package com.example.pmmovil.ui.asignaciones

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.R
import com.example.pmmovil.data.model.AsignacionEntity
import com.example.pmmovil.data.model.EntradaBusquedaHitDto
import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.data.repository.AsignacionRepository
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.data.scanner.OfficialQrParser
import com.example.pmmovil.data.scanner.ScanEventBus
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.example.pmmovil.ui.localization.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PendingMessageKind { INFO, SUCCESS, WARNING, ERROR }

sealed interface AsignacionesEvent {
    data class ShowMessage(
        val message: UiText,
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

/** Lo que muestra la zona de resultados mientras hay algo escrito en el buscador. */
sealed interface SearchState {
    /** Sin consulta (o muy corta para buscar en todo): se ven los pendientes, no resultados. */
    data object Idle : SearchState

    /** La consulta salió al servidor y se espera la respuesta; mientras tanto ya se ven las coincidencias locales. */
    data object Searching : SearchState

    data class Results(
        val hits: List<EntradaBusquedaHitDto>,
        val total: Int,
        val tolerant: Boolean,
        /** Lo que tardó el servidor en contestar, para que se vea que es instantáneo. */
        val elapsedMillis: Long
    ) : SearchState

    /** No se pudo buscar en todo (sin conexión o falla): las coincidencias locales siguen disponibles. */
    data class Unavailable(val message: UiText) : SearchState
}

/** Otra persona ya tenía tomada la carpeta: se pregunta si se la quiere quedar (quedan registrados los dos). */
data class TakeConflict(
    val entry: EntradaPendienteDto,
    val tomadaPor: String,
    /** Cuándo la tomó (hora del servidor, sin zona). */
    val desde: String?
)

data class AsignacionesUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Hay una lectura de pistola o cámara resolviéndose contra el servidor. */
    val isSearching: Boolean = false,
    val isOpeningEntry: Boolean = false,
    val isSubmittingUnlock: Boolean = false,
    /** Ya llegó (o falló) la primera carga de «mis pendientes»: distingue «cargando» de «no tienes nada». */
    val hasLoadedMine: Boolean = false,
    val misActivas: List<EntradaPendienteDto> = emptyList(),
    val misAsignaciones: List<AsignacionEntity> = emptyList(),
    val query: String = "",
    val search: SearchState = SearchState.Idle,
    val selectedEntry: EntradaPendienteDto? = null,
    val desbloqueoDialog: EntradaPendienteDto? = null,
    val unlockRequests: Set<String> = emptySet(),
    val removedAssignmentReferences: Set<String> = emptySet(),
    /** Referencia de la carpeta que se está tomando o devolviendo ahora (evita el doble toque). */
    val takingReference: String? = null,
    val takeConflict: TakeConflict? = null
)

/**
 * El hub de Revisión. Al entrar solo pide lo del revisador (sus asignaciones y lo que dejó abierto): ya no baja la lista
 * de todas las entradas. Todo lo demás se encuentra con el buscador, que pregunta al servidor por ocho coincidencias.
 */
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
    private var searchJob: Job? = null
    private var activityJob: Job? = null

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
                _uiState.update { it.copy(misAsignaciones = assignments) }
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
            hubService.actividadFlow.collect { activity ->
                runCatching { onActivity(activity.creferencia) }
            }
        }
        viewModelScope.launch {
            hubService.nuevaAsignacionFlow.collect { assignment ->
                runCatching {
                    _uiState.update {
                        it.copy(removedAssignmentReferences = it.removedAssignmentReferences - assignment.crefAgrupada)
                    }
                    asignacionRepository.syncAsignaciones()
                    loadData(showLoading = false)
                    _events.tryEmit(AsignacionesEvent.RevisionAssigned)
                    showMessage(
                        message = resource(R.string.message_assignment_received, assignment.crefAgrupada),
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
                        message = resource(R.string.message_assignment_removed, assignment.crefAgrupada),
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
                        message = resource(R.string.message_unlock_approved, message.supervisor),
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
                        message = resource(R.string.message_unlock_rejected, message.supervisor),
                        kind = PendingMessageKind.ERROR
                    )
                }
            }
        }
    }

    /**
     * Alguien movió una entrada. Antes esto volvía a bajar TODA la lista de pendientes en cada tableta; ahora solo
     * importa si esa entrada está en mi lista o en los resultados que estoy viendo, y aun así se espera un momento para
     * juntar ráfagas (una revisión activa manda un aviso por partida).
     */
    private fun onActivity(reference: String) {
        val state = _uiState.value
        val inMine = buildMyEntries(state).any { it.crefAgrupada.equals(reference, ignoreCase = true) }
        val inResults = (state.search as? SearchState.Results)?.hits?.any {
            it.item.crefAgrupada.equals(reference, ignoreCase = true)
        } == true
        if (!inMine && !inResults) return

        activityJob?.cancel()
        activityJob = viewModelScope.launch {
            delay(ACTIVITY_REFRESH_DELAY_MS)
            if (inMine) loadData(showLoading = false)
            if (inResults) runSearch(_uiState.value.query, silent = true)
        }
    }

    private fun removeAssignmentFromPending(idAsignacion: Int, crefAgrupada: String) {
        _uiState.update { current ->
            current.copy(
                misActivas = current.misActivas.filterNot { entry ->
                    entry.idAsignacion == idAsignacion || entry.crefAgrupada == crefAgrupada
                },
                misAsignaciones = current.misAsignaciones.filterNot { assignment ->
                    assignment.idAsignacion == idAsignacion || assignment.crefAgrupada == crefAgrupada
                },
                removedAssignmentReferences = current.removedAssignmentReferences + crefAgrupada
            )
        }
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
                message = resource(R.string.message_reference_unreadable),
                kind = PendingMessageKind.ERROR
            )
            return
        }
        if (reference.length < 2) {
            showMessage(
                message = resource(R.string.message_reference_short),
                kind = PendingMessageKind.WARNING
            )
            return
        }

        _uiState.update { it.copy(isSearching = true) }
        revisionRepository.buscarPorCreferencia(reference)
            .onSuccess { entry ->
                _uiState.update { it.copy(isSearching = false) }
                if (entry == null) {
                    showMessage(
                        message = resource(R.string.message_reference_not_found, reference),
                        kind = PendingMessageKind.ERROR
                    )
                } else {
                    selectEntry(entry)
                }
            }
            .onFailure { error ->
                _uiState.update { it.copy(isSearching = false) }
                showMessage(networkMessage(error), PendingMessageKind.ERROR)
            }
    }

    /**
     * Carga solo lo del revisador: sus asignaciones y lo que dejó abierto. Son un puñado de filas (no miles), así que
     * es rápido y se puede repetir sin cuidado cuando llega un aviso en vivo.
     */
    fun loadData(showLoading: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val firstLoad = !_uiState.value.hasLoadedMine
            _uiState.update {
                it.copy(
                    isLoading = showLoading && firstLoad,
                    isRefreshing = !(showLoading && firstLoad)
                )
            }

            val mineResult = coroutineScope {
                val assignments = async { asignacionRepository.syncAsignaciones() }
                val mine = async { revisionRepository.getMisActivas() }
                assignments.await()
                mine.await()
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    hasLoadedMine = true,
                    misActivas = mineResult.getOrElse { _ -> it.misActivas }
                )
            }

            if (mineResult.isFailure) {
                showMessage(
                    message = networkMessage(mineResult.exceptionOrNull()),
                    kind = PendingMessageKind.ERROR
                )
            } else if (showLoading && !firstLoad) {
                showMessage(
                    message = resource(R.string.message_list_updated),
                    kind = PendingMessageKind.SUCCESS
                )
            }
        }
    }

    /**
     * Al volver a la pantalla (de una revisión, de la cámara) se renuevan mis pendientes y, si hay algo escrito, sus
     * resultados: son pocas filas, así que el avance y los candados que se ven siempre son los reales. La primera vez
     * no hace nada porque la carga de arranque ya está en camino.
     */
    fun refreshOnResume() {
        if (!_uiState.value.hasLoadedMine) return
        loadData(showLoading = false)
        val query = _uiState.value.query
        if (isSearchable(query)) {
            viewModelScope.launch { runSearch(query, silent = true) }
        }
    }

    // ── Tomar carpeta ────────────────────────────────────────────────────────

    /**
     * «Tomar carpeta»: el revisador registra que agarró la carpeta física sin abrir la revisión ni arrancar el cronómetro.
     * El Monitor de revisadores la muestra desde ese momento. Si otra persona ya la tenía, se le pregunta antes de pasarla
     * a su nombre ([confirmar] = `true`).
     */
    fun takeFolder(entry: EntradaPendienteDto, confirmar: Boolean = false) {
        if (_uiState.value.takingReference != null) return
        _uiState.update { it.copy(takingReference = entry.crefAgrupada) }
        viewModelScope.launch {
            asignacionRepository.tomarCarpeta(entry.crefAgrupada, confirmar)
                .onSuccess { respuesta ->
                    when (val resultado = CarpetaToma.interpretar(respuesta)) {
                        is TomaResultado.Tomada -> {
                            _uiState.update { it.copy(takingReference = null, takeConflict = null) }
                            showMessage(
                                message = resultado.mensaje?.let(UiText::Raw) ?: resource(R.string.assignments_take_done),
                                kind = PendingMessageKind.SUCCESS
                            )
                            refreshAfterTake()
                        }

                        is TomaResultado.Conflicto -> _uiState.update {
                            it.copy(
                                takingReference = null,
                                takeConflict = TakeConflict(entry, resultado.tomadaPor, resultado.desde)
                            )
                        }

                        is TomaResultado.NoSePuede -> {
                            _uiState.update { it.copy(takingReference = null) }
                            showMessage(
                                message = resultado.mensaje?.let(UiText::Raw) ?: resource(R.string.message_operation_failed),
                                kind = PendingMessageKind.WARNING
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(takingReference = null) }
                    showMessage(networkMessage(error), PendingMessageKind.ERROR)
                }
        }
    }

    /** El revisador confirmó que se queda con una carpeta que otra persona había tomado. */
    fun confirmTakeOver() {
        val conflict = _uiState.value.takeConflict ?: return
        _uiState.update { it.copy(takeConflict = null) }
        takeFolder(conflict.entry, confirmar = true)
    }

    fun dismissTakeConflict() {
        _uiState.update { it.copy(takeConflict = null) }
    }

    /** El revisador devuelve una carpeta que tomó por error (solo las suyas). */
    fun releaseFolder(entry: EntradaPendienteDto) {
        if (_uiState.value.takingReference != null) return
        _uiState.update { it.copy(takingReference = entry.crefAgrupada) }
        viewModelScope.launch {
            asignacionRepository.soltarCarpeta(entry.crefAgrupada)
                .onSuccess { soltada ->
                    _uiState.update { it.copy(takingReference = null) }
                    showMessage(
                        message = resource(if (soltada) R.string.assignments_release_done else R.string.assignments_release_nothing),
                        kind = if (soltada) PendingMessageKind.SUCCESS else PendingMessageKind.INFO
                    )
                    refreshAfterTake()
                }
                .onFailure { error ->
                    _uiState.update { it.copy(takingReference = null) }
                    showMessage(networkMessage(error), PendingMessageKind.ERROR)
                }
        }
    }

    /** Tras tomar o devolver cambian mis pendientes y lo que dicen los resultados («tomada por…»). */
    private fun refreshAfterTake() {
        loadData(showLoading = false)
        val query = _uiState.value.query
        if (isSearchable(query)) {
            viewModelScope.launch { runSearch(query, silent = true) }
        }
    }

    // ── Buscador ─────────────────────────────────────────────────────────────

    /**
     * Cada tecla actualiza al instante las coincidencias entre mis pendientes (ya están en la tableta). Con tres
     * caracteres útiles, y una vez que se deja de teclear un instante, se pregunta además al servidor por el resto.
     */
    fun setQuery(raw: String) {
        // La etiqueta de carpeta trae la entrada y el cliente pegados (`01C2635628400001454  100`): se busca la entrada.
        val text = OfficialQrParser.entradaOf(raw) ?: raw
        _uiState.update { it.copy(query = text) }

        searchJob?.cancel()
        if (!isSearchable(text)) {
            _uiState.update { it.copy(search = SearchState.Idle) }
            return
        }
        _uiState.update { it.copy(search = SearchState.Searching) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            runSearch(text, silent = false)
        }
    }

    fun clearQuery() = setQuery("")

    private suspend fun runSearch(query: String, silent: Boolean) {
        if (!isSearchable(query)) return
        val startedAt = System.currentTimeMillis()
        revisionRepository.buscarRapido(query.trim(), SEARCH_LIMIT)
            .onSuccess { page ->
                // Si siguió escribiendo mientras llegaba la respuesta, esta ya no corresponde a lo que ve.
                if (_uiState.value.query.trim() != query.trim()) return@onSuccess
                _uiState.update {
                    it.copy(
                        search = SearchState.Results(
                            hits = page.hits,
                            total = page.total,
                            tolerant = page.tolerant,
                            elapsedMillis = System.currentTimeMillis() - startedAt
                        )
                    )
                }
            }
            .onFailure { error ->
                if (_uiState.value.query.trim() != query.trim()) return@onFailure
                // Una actualización silenciosa que falla no debe tapar los resultados que ya se ven.
                if (silent && _uiState.value.search is SearchState.Results) return@onFailure
                _uiState.update { it.copy(search = SearchState.Unavailable(networkMessage(error))) }
            }
    }

    /** Enter en el teclado: si lo escrito es una entrada completa, se abre; si no, se elige el único resultado. */
    fun submitQuery() {
        val state = _uiState.value
        val typed = state.query.trim()
        if (typed.isEmpty()) return

        val local = localMatches(state)
        val exactLocal = local.firstOrNull { it.crefAgrupada.equals(typed, ignoreCase = true) }
        if (exactLocal != null) {
            selectEntry(exactLocal)
            return
        }
        val results = state.search as? SearchState.Results
        val hits = results?.hits.orEmpty()
        val exactHit = hits.firstOrNull { it.item.crefAgrupada.equals(typed, ignoreCase = true) }
        val only = hits.singleOrNull()?.takeIf { local.isEmpty() }
        when {
            exactHit != null -> selectEntry(exactHit.item)
            only != null -> selectEntry(only.item)
            looksLikeReference(typed) -> searchExactReference(typed)
        }
    }

    /** Una referencia completa son 15 caracteres: si están, no hace falta buscar, se pregunta directo por ella. */
    private fun looksLikeReference(text: String): Boolean =
        normalizeForSearch(text).replace(" ", "").length >= REFERENCE_LENGTH

    // ── Selección y apertura ─────────────────────────────────────────────────

    fun selectEntry(entry: EntradaPendienteDto) {
        if (isEntryLocked(entry, getUsername())) {
            _uiState.update { it.copy(desbloqueoDialog = entry) }
            val activeOwner = entry.usuarioRevisando?.takeIf { it.isNotBlank() }
                ?: entry.usuarioTomoDocumento
            showMessage(
                message = activeOwner?.let {
                    resource(R.string.message_entry_in_use, it)
                } ?: resource(R.string.message_entry_in_use_other),
                kind = PendingMessageKind.WARNING
            )
            return
        }
        _uiState.update { it.copy(selectedEntry = entry) }
    }

    fun dismissModeSelector() {
        if (_uiState.value.isOpeningEntry) return
        _uiState.update { it.copy(selectedEntry = null) }
    }

    fun openSelectedEntry(mode: ModePreferences.Mode) {
        val entry = _uiState.value.selectedEntry ?: return
        if (_uiState.value.isOpeningEntry) return

        if (isEntryLocked(entry, getUsername())) {
            _uiState.update { it.copy(selectedEntry = null, desbloqueoDialog = entry) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isOpeningEntry = true) }

            val assignmentId = entry.idAsignacion
            if (assignmentId != null && entry.asignEstatus == 0) {
                // Marcar como vista es telemetría secundaria. No debe detener
                // la apertura de una revisión que sí puede cargarse ahora.
                viewModelScope.launch {
                    asignacionRepository.markViewed(assignmentId)
                        .onFailure {
                            showMessage(
                                message = resource(R.string.message_open_warning),
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
                        _uiState.update { it.copy(isOpeningEntry = false, selectedEntry = null) }
                        _events.emit(
                            AsignacionesEvent.NavigateToRevision(
                                crefAgrupada = entry.crefAgrupada,
                                mode = mode
                            )
                        )
                    } catch (e: Exception) {
                        _uiState.update { it.copy(isOpeningEntry = false) }
                        showMessage(
                            message = resource(R.string.message_open_failed, entry.crefAgrupada),
                            kind = PendingMessageKind.ERROR
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isOpeningEntry = false) }
                    showMessage(
                        message = networkMessage(error),
                        kind = PendingMessageKind.ERROR
                    )
                }
        }
    }

    fun showDesbloqueoDialog(entry: EntradaPendienteDto) {
        _uiState.update { it.copy(desbloqueoDialog = entry) }
    }

    fun hideDesbloqueoDialog() {
        if (_uiState.value.isSubmittingUnlock) return
        _uiState.update { it.copy(desbloqueoDialog = null) }
    }

    fun solicitarDesbloqueo(motivo: String) {
        val entry = _uiState.value.desbloqueoDialog ?: return
        val cleanReason = motivo.trim()
        if (cleanReason.length < 5) {
            showMessage(
                message = resource(R.string.message_reason_required),
                kind = PendingMessageKind.WARNING
            )
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingUnlock = true) }
            revisionRepository.solicitarDesbloqueo(
                cnofact = "ENTRY_${entry.crefAgrupada}",
                motivo = cleanReason
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmittingUnlock = false,
                        desbloqueoDialog = null,
                        unlockRequests = it.unlockRequests + entry.crefAgrupada
                    )
                }
                showMessage(
                    message = resource(R.string.message_unlock_request_sent),
                    kind = PendingMessageKind.SUCCESS
                )
            }.onFailure { error ->
                _uiState.update { it.copy(isSubmittingUnlock = false) }
                showMessage(
                    message = error.message?.takeIf(String::isNotBlank)?.let(UiText::Raw)
                        ?: resource(R.string.message_unlock_request_failed),
                    kind = PendingMessageKind.ERROR
                )
            }
        }
    }

    // ── Lo que ve la pantalla ────────────────────────────────────────────────

    /** Mis pendientes, ya separados en asignadas y abiertas. Las terminadas no aparecen. */
    val pendingHub: StateFlow<PendingHub> = _uiState
        .map { state -> buildPendingHub(buildMyEntries(state)) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PendingHub(emptyList(), emptyList()))

    /** Coincidencias con lo escrito entre MIS revisiones (incluidas las terminadas): salen sin ir al servidor. */
    val localResults: StateFlow<List<EntradaPendienteDto>> = _uiState
        .map { state -> localMatches(state) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun localMatches(state: AsignacionesUiState): List<EntradaPendienteDto> {
        if (searchTokens(state.query).isEmpty()) return emptyList()
        return buildMyEntries(state).filter { matchesPendingQuery(it, state.query) }
    }

    private fun buildMyEntries(state: AsignacionesUiState): List<EntradaPendienteDto> {
        val entriesByReference = linkedMapOf<String, EntradaPendienteDto>()

        state.misActivas.forEach { entry ->
            if (entry.crefAgrupada !in state.removedAssignmentReferences) {
                entriesByReference[entry.crefAgrupada] = entry
            }
        }

        state.misAsignaciones
            .filterNot { it.crefAgrupada in state.removedAssignmentReferences }
            .forEach { assignment ->
                val baseEntry = entriesByReference[assignment.crefAgrupada]
                entriesByReference[assignment.crefAgrupada] =
                    baseEntry?.withAssignment(assignment) ?: assignment.toPendingProxy()
            }

        return entriesByReference.values.toList()
    }

    fun lastModeFor(crefAgrupada: String): ModePreferences.Mode = modePrefs.get(crefAgrupada)

    fun getUsername(): String? = authRepository.getUsername()

    private fun removeUnlockRequest(reference: String) {
        _uiState.update { it.copy(unlockRequests = it.unlockRequests - reference) }
    }

    private fun showMessage(message: UiText, kind: PendingMessageKind) {
        _events.tryEmit(AsignacionesEvent.ShowMessage(message, kind))
    }

    private fun networkMessage(error: Throwable?): UiText {
        return when (error) {
            is UnknownHostException,
            is ConnectException,
            is SocketTimeoutException,
            is IOException -> resource(R.string.message_no_connection)

            null -> resource(R.string.message_operation_failed)
            else -> error.message?.takeIf { it.isNotBlank() }?.let(UiText::Raw)
                ?: resource(R.string.message_operation_failed)
        }
    }

    private fun resource(@StringRes id: Int, vararg args: Any): UiText =
        UiText.Resource(id, args.toList())

    private companion object {
        /** Cuánto se espera tras un aviso en vivo para juntar ráfagas antes de refrescar. */
        const val ACTIVITY_REFRESH_DELAY_MS = 1_500L

        /** Largo de una referencia de entrada completa (`01C263607340000`). */
        const val REFERENCE_LENGTH = 15
    }
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
