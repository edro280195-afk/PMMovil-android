package com.example.pmmovil.ui.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraPrinterPreferences
import com.example.pmmovil.data.repository.ZebraPrinterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrinterCatalogState(
    val printers: List<ZebraPrinterDto> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val lastSuccess: Boolean? = null
)

/**
 * Lista de impresoras Zebra registradas en pmmtools. La "predeterminada" ya no es un
 * interruptor separado del formulario: se calcula aquí para mostrarse como insignia en cada
 * fila, comparando contra la preferencia local guardada por tipo de etiqueta
 * ([ZebraPrinterPreferences], por dispositivo, nunca en el servidor).
 */
@HiltViewModel
class PrinterCatalogViewModel @Inject constructor(
    private val repository: ZebraPrinterRepository,
    private val printerPreferences: ZebraPrinterPreferences,
    private val layoutPreferences: ZebraLabelLayoutPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterCatalogState())
    val uiState: StateFlow<PrinterCatalogState> = _uiState.asStateFlow()

    init {
        loadPrinters()
        viewModelScope.launch {
            layoutPreferences.syncWithServer()
        }
    }

    fun loadPrinters() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, lastSuccess = null) }
            repository.getAllPrinters()
                .onSuccess { printers ->
                    _uiState.update {
                        it.copy(
                            printers = printers.sortedBy(ZebraPrinterDto::displayName),
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "No se pudo cargar el catálogo Zebra.",
                            lastSuccess = false
                        )
                    }
                }
        }
    }

    /** Tipos de etiqueta para los que esta impresora es la predeterminada en este dispositivo. */
    fun defaultLabelTypes(printer: ZebraPrinterDto): List<ZebraLabelType> =
        ZebraLabelType.values().filter { type ->
            printerPreferences.isDefault(type, printer.id, printer.host, printer.port)
        }
}
