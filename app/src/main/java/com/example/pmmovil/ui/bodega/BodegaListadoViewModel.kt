package com.example.pmmovil.ui.bodega

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.BodegaEntradaDto
import com.example.pmmovil.data.repository.BodegaRepository
import com.example.pmmovil.data.scanner.OfficialQrParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BodegaListadoUiState(
    val isLoading: Boolean = false,
    val items: List<BodegaEntradaDto> = emptyList(),
    val query: String = "",
    val error: String? = null,
    val isFromScanner: Boolean = false,
    val navigatedCref: String? = null
)

@HiltViewModel
class BodegaListadoViewModel @Inject constructor(
    private val repository: BodegaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BodegaListadoUiState())
    val uiState: StateFlow<BodegaListadoUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        cargar()
    }

    fun cargar() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                query = "",
                error = null,
                isFromScanner = false,
                navigatedCref = null
            )
        }
        searchJob = viewModelScope.launch {
            performLoad()
        }
    }

    fun onQueryChange(value: String) {
        _uiState.update { it.copy(query = value) }
        debouncedSearch(value)
    }

    fun buscar(q: String) {
        val clean = q.trim()
        if (clean.isBlank()) {
            cargar()
            return
        }
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true,
                query = clean,
                error = null,
                isFromScanner = false,
                navigatedCref = null
            )
        }
        searchJob = viewModelScope.launch {
            performSearch(clean, isFromScanner = false)
        }
    }

    private suspend fun performSearch(clean: String, isFromScanner: Boolean) {
        repository.buscar(clean)
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            items = items,
                            error = null,
                            isFromScanner = isFromScanner,
                            navigatedCref = if (isFromScanner) {
                                items.singleOrNull()?.creferencia
                            } else {
                                null
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "No se pudo realizar la búsqueda."
                        )
                    }
                }
    }

    fun onScannerInput(raw: String): String? {
        val clean = OfficialQrParser.entradaOf(raw) ?: raw.trim().split(" ", "\t").first()
        val cref = if (clean.length >= 15 && clean.startsWith("01")) clean.take(15) else null
        if (cref != null) {
            _uiState.update { it.copy(isFromScanner = true) }
            buscarExact(cref)
        } else {
            _uiState.update {
                it.copy(
                    error = "El código escaneado no parece una referencia de entrada válida.",
                    isFromScanner = false
                )
            }
        }
        return cref
    }

    private fun buscarExact(cref: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null, query = cref) }
        searchJob = viewModelScope.launch {
            // Sólo una búsqueda exacta puede disparar navegación; un
            // resultado parcial nunca debe abrir una entrada ajena.
            performSearch(cref, isFromScanner = true)
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(navigatedCref = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun debouncedSearch(value: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350L)
            val clean = value.trim()
            if (clean.isBlank()) {
                performLoad()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        isFromScanner = false,
                        navigatedCref = null
                    )
                }
                performSearch(clean, isFromScanner = false)
            }
        }
    }

    private suspend fun performLoad() {
        repository.getLista()
            .onSuccess { items ->
                _uiState.update { it.copy(isLoading = false, items = items, error = null) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "No se pudo cargar la lista."
                    )
                }
            }
    }
}
