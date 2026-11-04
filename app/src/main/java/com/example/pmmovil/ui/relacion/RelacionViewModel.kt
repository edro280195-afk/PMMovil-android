package com.example.pmmovil.ui.relacion

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.model.CargarRelacionResponse
import com.example.pmmovil.data.model.RelacionPendiente
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RelacionRepository
import com.example.pmmovil.data.scanner.OfficialQrParser
import com.example.pmmovil.data.scanner.PendingCode
import com.example.pmmovil.data.signalr.WarehouseHubService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RelacionTab { AUTHORIZATION, GATE }
enum class RelacionAction { AUTHORIZE, RETURN_TO_WAITING, DELETE_LOAD, UNLOCK }

data class RelacionConfirmation(
    val action: RelacionAction,
    val title: String,
    val message: String,
    val confirmLabel: String,
    val requiresPhysicalReview: Boolean = false
)

data class RelacionUiState(
    val tab: RelacionTab = RelacionTab.AUTHORIZATION,
    val query: String = "",
    val pending: List<RelacionPendiente> = emptyList(),
    val gatePending: List<CasetaPendiente> = emptyList(),
    val detail: CargarRelacionResponse? = null,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    val confirmation: RelacionConfirmation? = null,
    val currentUser: String = "",
    val audioEnabled: Boolean = true,
    val message: String? = null,
    val feedbackSeq: Int = 0
)

@HiltViewModel
class RelacionViewModel @Inject constructor(
    private val repository: RelacionRepository,
    private val authRepository: AuthRepository,
    private val hubService: WarehouseHubService,
    @ApplicationContext context: Context
) : ViewModel() {
    private val preferences = context.getSharedPreferences("PMMovilRelation", Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        RelacionUiState(
            currentUser = authRepository.getUsername().orEmpty(),
            audioEnabled = preferences.getBoolean("audio_enabled", true)
        )
    )
    val uiState: StateFlow<RelacionUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private val pendingCode = PendingCode()

    init {
        loadPending()
        observeSalidaEvents()
    }

    /** Avisos en vivo de la carga: una relación se bloqueó o un supervisor la desbloqueó. Se refresca la lista. */
    private fun observeSalidaEvents() {
        viewModelScope.launch {
            hubService.salidaBloqueadaFlow.collect { aviso ->
                val caja = if (aviso.caja.isNotBlank()) " (caja ${aviso.caja})" else ""
                _uiState.update {
                    it.copy(
                        message = "⚠ ${aviso.relno} bloqueada$caja: ${aviso.motivo}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                if (_uiState.value.tab == RelacionTab.AUTHORIZATION && _uiState.value.detail == null) {
                    loadPending(_uiState.value.query)
                }
            }
        }
        viewModelScope.launch {
            hubService.salidaDesbloqueadaFlow.collect {
                if (_uiState.value.tab == RelacionTab.AUTHORIZATION && _uiState.value.detail == null) {
                    loadPending(_uiState.value.query)
                }
            }
        }
    }

    fun canModify(): Boolean = authRepository.hasPermission(167, "Modificar")

    fun canDelete(): Boolean = authRepository.hasPermission(167, "Borrar")

    fun setTab(tab: RelacionTab) {
        _uiState.update { it.copy(tab = tab, detail = null) }
        if (tab == RelacionTab.AUTHORIZATION) loadPending() else loadGatePending()
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            loadPending(value)
        }
    }

    fun refresh() {
        if (_uiState.value.tab == RelacionTab.AUTHORIZATION) {
            loadPending(_uiState.value.query)
        } else {
            loadGatePending()
        }
    }

    fun openDetail(scanned: String) {
        // Lo que llega del lector puede ser la orden de carga (`01I26106418-1`) o la requisición de cruce: se abre la relación.
        val relno = OfficialQrParser.relacionOf(scanned) ?: scanned
        if (relno.isBlank()) return
        if (_uiState.value.isLoading) {
            // Llegó mientras cargaba la lista (por ejemplo, abierta desde el escáner universal): se abre al terminar.
            pendingCode.hold(relno)
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, detail = null) }
            repository.getDetail(relno.trim())
                .onSuccess { response ->
                    _uiState.update {
                        if (response.success && response.relacion != null) {
                            it.copy(isLoading = false, detail = response)
                        } else {
                            it.copy(
                                isLoading = false,
                                message = "✗ ${response.message ?: "No se encontró la relación."}",
                                feedbackSeq = it.feedbackSeq + 1
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudo consultar la relación."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
            openPendingCode()
        }
    }

    /** Si llegó un código mientras se cargaba algo, lo abre ahora que la pantalla ya puede atenderlo. */
    private fun openPendingCode() {
        pendingCode.take()?.let(::openDetail)
    }

    fun closeDetail() {
        _uiState.update { it.copy(detail = null, confirmation = null) }
        loadPending(_uiState.value.query)
    }

    fun requestAction(action: RelacionAction) {
        val allowed = when (action) {
            RelacionAction.DELETE_LOAD -> canDelete()
            else -> canModify()
        }
        if (!allowed) {
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para realizar esta acción.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        val relation = _uiState.value.detail?.relacion ?: return
        val confirmation = when (action) {
            RelacionAction.AUTHORIZE -> RelacionConfirmation(
                action,
                "Autorizar relación",
                "Confirma la autorización de la relación ${relation.relno}.",
                "Autorizar"
            )
            RelacionAction.RETURN_TO_WAITING -> RelacionConfirmation(
                action,
                "Retornar a espera",
                "La relación volverá a EN ESPERA para que la carga sea capturada nuevamente.",
                "Retornar"
            )
            RelacionAction.DELETE_LOAD -> RelacionConfirmation(
                action,
                "Eliminar carga",
                "Se eliminarán todos los escaneos registrados. Esta acción no se puede deshacer.",
                "Eliminar"
            )
            RelacionAction.UNLOCK -> RelacionConfirmation(
                action,
                "Desbloquear relación",
                "Confirma que revisaste físicamente las etiquetas bloqueadas antes de continuar.",
                "Desbloquear",
                requiresPhysicalReview = true
            )
        }
        _uiState.update { it.copy(confirmation = confirmation) }
    }

    fun dismissConfirmation() {
        _uiState.update { it.copy(confirmation = null) }
    }

    fun confirmAction(physicalReviewConfirmed: Boolean) {
        val confirmation = _uiState.value.confirmation ?: return
        val relation = _uiState.value.detail?.relacion ?: return
        if (confirmation.requiresPhysicalReview && !physicalReviewConfirmed) {
            _uiState.update {
                it.copy(
                    message = "⚠ Confirma la revisión física para desbloquear.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        if (
            confirmation.action == RelacionAction.AUTHORIZE &&
            relation.usucargaMcia == _uiState.value.currentUser
        ) {
            _uiState.update {
                it.copy(
                    confirmation = null,
                    message = "✗ No puedes autorizar una relación que tú mismo cargaste.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, confirmation = null) }
            val result = when (confirmation.action) {
                RelacionAction.AUTHORIZE -> repository.authorize(relation.relno)
                RelacionAction.RETURN_TO_WAITING -> repository.returnToWaiting(relation.relno)
                RelacionAction.DELETE_LOAD -> repository.deleteLoad(relation.relno)
                RelacionAction.UNLOCK -> repository.unlock(relation.relno)
            }
            result.onSuccess { response ->
                if (response.success) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            detail = null,
                            message = "✓ ${response.message ?: confirmation.confirmLabel + " completado."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                    loadPending(_uiState.value.query)
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            message = "✗ ${response.message ?: "No se pudo completar la operación."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        message = "✗ ${error.message ?: "No se pudo completar la operación."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
        }
    }

    fun toggleAudio() {
        val enabled = !_uiState.value.audioEnabled
        preferences.edit { putBoolean("audio_enabled", enabled) }
        _uiState.update {
            it.copy(
                audioEnabled = enabled,
                message = if (enabled) "✓ Audio activado." else "Audio desactivado.",
                feedbackSeq = it.feedbackSeq + 1
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun loadPending(query: String = _uiState.value.query) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getPending(query.trim())
                .onSuccess { pending ->
                    _uiState.update { it.copy(isLoading = false, pending = pending) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudieron cargar las relaciones."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
            openPendingCode()
        }
    }

    private fun loadGatePending() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            repository.getGatePending()
                .onSuccess { pending ->
                    _uiState.update { it.copy(isLoading = false, gatePending = pending) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudieron cargar las salidas."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
            openPendingCode()
        }
    }
}
