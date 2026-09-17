package com.example.pmmovil.ui.salidacaseta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.SalidaCajaItem
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.SalidaCasetaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SalidaCasetaUiState(
    val pending: List<SalidaCajaItem> = emptyList(),
    val query: String = "",
    val selected: SalidaCajaItem? = null,
    val observations: String = "",
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    // Numero de relacion si la caja ya tiene una (prefijo 01); dispara la advertencia.
    val relacionWarning: String? = null,
    val relacionCountdown: Int = 0,
    val currentUser: String = "",
    val puedeModificar: Boolean = false,
    val message: String? = null,
    val feedbackSeq: Int = 0
) {
    val filteredPending: List<SalidaCajaItem>
        get() {
            val clean = query.trim()
            if (clean.isBlank()) return pending
            return pending.filter { item ->
                item.numCaja.contains(clean, ignoreCase = true) ||
                    item.placas.contains(clean, ignoreCase = true) ||
                    item.nombreCliente.contains(clean, ignoreCase = true) ||
                    item.casetaId.contains(clean, ignoreCase = true)
            }
        }

    val canConfirmRelacion: Boolean get() = relacionWarning != null && relacionCountdown <= 0
}

@HiltViewModel
class SalidaCasetaViewModel @Inject constructor(
    private val repository: SalidaCasetaRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SalidaCasetaUiState(
            currentUser = authRepository.getUsername().orEmpty(),
            puedeModificar = authRepository.hasPermission(166, "Modificar")
        )
    )
    val uiState: StateFlow<SalidaCasetaUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        cancelCountdown()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selected = null,
                    observations = "",
                    relacionWarning = null,
                    relacionCountdown = 0
                )
            }
            repository.getPendientes()
                .onSuccess { list ->
                    _uiState.update { it.copy(pending = list, isLoading = false) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudieron cargar las cajas en patio."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    fun onScannerInput(raw: String) {
        val code = raw.trim().split(Regex("\\s+")).firstOrNull().orEmpty().trim(',', ';')
        if (code.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "⚠ Lectura vacía. Vuelve a escanear la caja.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(query = code) }
        val match = _uiState.value.pending.firstOrNull {
            it.numCaja.equals(code, ignoreCase = true) || it.casetaId.equals(code, ignoreCase = true)
        }
        if (match != null) openDetail(match)
    }

    fun openDetail(item: SalidaCajaItem) {
        if (_uiState.value.isProcessing) return
        cancelCountdown()
        _uiState.update {
            it.copy(selected = item, observations = "", relacionWarning = null, relacionCountdown = 0)
        }
    }

    fun closeDetail() {
        cancelCountdown()
        _uiState.update {
            it.copy(selected = null, observations = "", relacionWarning = null, relacionCountdown = 0)
        }
    }

    fun updateObservations(value: String) {
        _uiState.update { it.copy(observations = value.take(500)) }
    }

    // Tap "Dar salida": revisa relación -> confirma o advierte.
    fun attemptSalida() {
        val state = _uiState.value
        val item = state.selected ?: return
        if (state.isProcessing) return
        if (!state.puedeModificar) {
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para dar salida física de caja.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        if (state.currentUser.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "✗ No se encontró el usuario de sesión. Inicia sesión nuevamente.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true) }
            val result = repository.revisarRelacion(item.casetaId)
            if (result.isFailure) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        message = "✗ ${result.exceptionOrNull()?.message ?: "No se pudo revisar la relación."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                return@launch
            }
            val relacion = result.getOrNull().orEmpty()
            if (relacion.isNotBlank()) {
                // La caja ya tiene relacion: advertir y exigir confirmacion (countdown).
                _uiState.update { it.copy(isProcessing = false, relacionWarning = relacion) }
                startCountdown()
            } else {
                darSalida(item)
            }
        }
    }

    // Confirmar la salida tras la advertencia de relacion.
    fun confirmRelacionWarning() {
        val item = _uiState.value.selected ?: return
        if (!_uiState.value.canConfirmRelacion) return
        cancelCountdown()
        _uiState.update { it.copy(relacionWarning = null) }
        viewModelScope.launch { darSalida(item) }
    }

    fun dismissRelacionWarning() {
        cancelCountdown()
        _uiState.update { it.copy(relacionWarning = null, relacionCountdown = 0, isProcessing = false) }
    }

    private suspend fun darSalida(item: SalidaCajaItem) {
        _uiState.update { it.copy(isProcessing = true) }
        repository.registrarSalida(item.casetaId, _uiState.value.observations.trim())
            .onSuccess { response ->
                if (response.success) {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            selected = null,
                            observations = "",
                            message = "✓ ${response.message ?: "Salida registrada correctamente."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                    refresh()
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            message = "✗ ${response.message ?: "No se pudo registrar la salida."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        message = "✗ ${error.message ?: "Error al registrar la salida."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
    }

    private fun startCountdown() {
        cancelCountdown()
        countdownJob = viewModelScope.launch {
            for (n in RELACION_COUNTDOWN downTo 0) {
                _uiState.update { it.copy(relacionCountdown = n) }
                if (n > 0) delay(1000)
            }
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        private const val RELACION_COUNTDOWN = 5
    }
}
