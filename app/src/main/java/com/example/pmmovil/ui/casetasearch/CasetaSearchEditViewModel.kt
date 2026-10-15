package com.example.pmmovil.ui.casetasearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraLabelData
import com.example.pmmovil.data.repository.CasetaEntradaRepository
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CasetaSearchEditUiState(
    val query: String = "",
    val results: List<CasetaEntradaBusquedaItem> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val message: String? = null,
    val feedbackSeq: Int = 0
)

@HiltViewModel
class CasetaSearchEditViewModel @Inject constructor(
    private val repository: CasetaEntradaRepository,
    private val zebraPrinterPreferences: com.example.pmmovil.data.printing.ZebraPrinterPreferences,
    private val zebraPrinterService: com.example.pmmovil.data.printing.ZebraPrinterService,
    private val labelZplFactory: com.example.pmmovil.data.printing.LabelZplFactory,
    private val zebraLabelLayoutPreferences: ZebraLabelLayoutPreferences
) : ViewModel() {
    private val _uiState = MutableStateFlow(CasetaSearchEditUiState())
    val uiState: StateFlow<CasetaSearchEditUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        searchNow()
        viewModelScope.launch {
            zebraLabelLayoutPreferences.syncWithServer()
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value, message = null) }
        queueSearch()
    }

    fun clearQuery() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", results = emptyList(), hasSearched = false, message = null) }
        searchNow()
    }

    fun searchNow() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            performSearch(_uiState.value.query.trim())
        }
    }

    fun onScannerInput(raw: String) {
        val clean = BarcodeValueNormalizer.normalize(raw)
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()
            .trim(',', ';')
            .uppercase()
        if (clean.isBlank()) return
        _uiState.update { it.copy(query = clean) }
        searchNow()
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun queueSearch() {
        searchJob?.cancel()
        val term = _uiState.value.query.trim()
        if (term.isNotBlank() && term.length < MIN_SEARCH_LENGTH) {
            _uiState.update { it.copy(results = emptyList(), hasSearched = false, isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(320)
            performSearch(term)
        }
    }

    private suspend fun performSearch(term: String) {
        _uiState.update { it.copy(isLoading = true, hasSearched = true) }
        val limit = if (term.isBlank()) RECENT_LIMIT else SEARCH_LIMIT
        repository.searchRecords(term = term, limit = limit)
            .onSuccess { items ->
                _uiState.update {
                    it.copy(
                        results = items,
                        isLoading = false,
                        message = null
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        isLoading = false,
                        message = error.message ?: "No se pudieron buscar registros.",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
    }

    fun printCasetaLabel(casetaId: String, caja: String, copies: Int = 1) {
        viewModelScope.launch {
            val config = zebraPrinterPreferences.getConfig(
                com.example.pmmovil.data.printing.ZebraLabelType.CASETA
            )
            if (!config.isHostConfigured) {
                _uiState.update { it.copy(message = "Impresora no configurada", feedbackSeq = it.feedbackSeq + 1) }
                return@launch
            }
            try {
                val labelData = ZebraLabelData(
                    qrValue = casetaId,
                    numeroEntrada = caja,
                    fechaArribo = "",
                    cliente = "",
                    copies = copies
                )
                val layout = zebraLabelLayoutPreferences.getLayout(ZebraLabelType.CASETA)
                val zpl = labelZplFactory.buildLabels(listOf(labelData), compress = true, layoutOverride = layout)
                zebraPrinterService.printZpl(config, zpl, copies)
                _uiState.update { it.copy(message = "Etiqueta enviada a impresión") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Error de impresión: ${e.message}", feedbackSeq = it.feedbackSeq + 1) }
            }
        }
    }

    private companion object {
        const val MIN_SEARCH_LENGTH = 2
        const val RECENT_LIMIT = 20
        const val SEARCH_LIMIT = 30
    }
}
