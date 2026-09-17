package com.example.pmmovil.ui.casetahub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.data.model.CasetaPreregistroEnCaminoItem
import com.example.pmmovil.data.model.SalidaCajaItem
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.CasetaEntradaRepository
import com.example.pmmovil.data.printing.LabelZplFactory
import com.example.pmmovil.data.printing.ZebraLabelData
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraPrinterPreferences
import com.example.pmmovil.data.printing.ZebraPrinterService
import com.example.pmmovil.data.repository.SalidaCasetaRepository
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Las tres facetas por las que se puede acotar el patio desde el hub. */
enum class CasetaHubFacet(val label: String) {
    CLIENTE("Cliente"),
    LINEA("Línea"),
    MOVIMIENTO("Movimiento")
}

/** Una opción de filtro con cuántas cajas caerían si se elige. */
data class CasetaHubFacetOption(
    val value: String,
    val count: Int
)

data class CasetaHubUiState(
    // Pulso de la planta
    val entradasHoy: Int = 0,
    val salidasHoy: Int = 0,

    // Patio: lo que hay adentro ahora mismo
    val enPlanta: List<SalidaCajaItem> = emptyList(),
    val visibles: List<SalidaCajaItem> = emptyList(),

    // Camiones de pre-registro que vienen en camino
    val enCamino: List<CasetaPreregistroEnCaminoItem> = emptyList(),

    // Búsqueda y filtros
    val query: String = "",
    val filtroCliente: String? = null,
    val filtroLinea: String? = null,
    val filtroMovimiento: String? = null,

    // Búsqueda en histórico (solo cuando el patio no tiene la caja)
    val historico: List<CasetaEntradaBusquedaItem> = emptyList(),
    val isSearchingHistorico: Boolean = false,
    val historicoBuscado: Boolean = false,

    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val lastUpdated: String = "",
    val userName: String = "",
    val planta: String = "",
    val puedePreregistro: Boolean = false,
    val puedeNuevaEntrada: Boolean = false,
    val puedeSalidaRelacion: Boolean = false,
    val puedeSalidaFisica: Boolean = false,
    val feedbackSeq: Int = 0
) {
    val hayFiltros: Boolean
        get() = query.isNotBlank() ||
            filtroCliente != null ||
            filtroLinea != null ||
            filtroMovimiento != null

    val totalEnPlanta: Int get() = enPlanta.size

    fun filtroDe(facet: CasetaHubFacet): String? = when (facet) {
        CasetaHubFacet.CLIENTE -> filtroCliente
        CasetaHubFacet.LINEA -> filtroLinea
        CasetaHubFacet.MOVIMIENTO -> filtroMovimiento
    }

    /**
     * Opciones para una faceta, contadas sobre el patio ya acotado por el texto
     * y por las OTRAS dos facetas -- nunca por ella misma. Así la hoja no
     * ofrece opciones que darían cero resultados, pero tampoco se vacía sola
     * cuando ya elegiste algo en ella.
     */
    fun opcionesDe(facet: CasetaHubFacet): List<CasetaHubFacetOption> {
        val base = enPlanta.filter { item ->
            coincideTexto(item, query) &&
                (facet == CasetaHubFacet.CLIENTE || filtroCliente == null || item.nombreCliente == filtroCliente) &&
                (facet == CasetaHubFacet.LINEA || filtroLinea == null || lineaDe(item) == filtroLinea) &&
                (facet == CasetaHubFacet.MOVIMIENTO || filtroMovimiento == null || item.tipoMovimiento == filtroMovimiento)
        }
        return base
            .map { valorDe(it, facet) }
            .filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
            .map { (value, count) -> CasetaHubFacetOption(value, count) }
            .sortedWith(compareByDescending<CasetaHubFacetOption> { it.count }.thenBy { it.value })
    }

    companion object {
        fun valorDe(item: SalidaCajaItem, facet: CasetaHubFacet): String = when (facet) {
            CasetaHubFacet.CLIENTE -> item.nombreCliente.trim()
            CasetaHubFacet.LINEA -> lineaDe(item)
            CasetaHubFacet.MOVIMIENTO -> item.tipoMovimiento.trim()
        }

        /**
         * La línea de la caja es lo que se ve en el patio; si viene vacía (pasa
         * con los tractocamiones de pre-registro, que no traen caja) se cae al
         * número de unidad para no dejar la fila muda.
         */
        fun lineaDe(item: SalidaCajaItem): String =
            item.lineaCaja.trim().ifBlank { item.numCamion.trim() }

        fun coincideTexto(item: SalidaCajaItem, query: String): Boolean {
            val q = query.trim()
            if (q.isBlank()) return true
            val needle = normalizar(q)
            return listOf(
                item.numCaja,
                item.nombreCliente,
                lineaDe(item),
                item.tipoMovimiento,
                item.placas,
                item.numCamion,
                item.casetaId
            ).any { normalizar(it).contains(needle) }
        }

        fun normalizar(value: String): String = value.trim().uppercase(Locale.ROOT)
    }
}

@HiltViewModel
class CasetaHubViewModel @Inject constructor(
    private val repository: CasetaEntradaRepository,
    private val salidaRepository: SalidaCasetaRepository,
    private val zebraPrinterPreferences: ZebraPrinterPreferences,
    private val zebraPrinterService: ZebraPrinterService,
    private val zebraLabelLayoutPreferences: ZebraLabelLayoutPreferences,
    private val labelZplFactory: LabelZplFactory,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val puedePreregistro = authRepository.hasPermission(MobileModuleIds.GATE, "Agregar")
    private val puedeNuevaEntrada = authRepository.hasPermission(MobileModuleIds.GATE, "Agregar")
    private val puedeSalidaRelacion = authRepository.hasPermission(MobileModuleIds.GATE_RELATION_EXIT, "Lectura")
    private val puedeSalidaFisica = authRepository.hasPermission(MobileModuleIds.GATE_PHYSICAL_EXIT, "Lectura")

    private val _uiState = MutableStateFlow(
        CasetaHubUiState(
            userName = authRepository.getName().orEmpty(),
            planta = authRepository.getPlanta().orEmpty(),
            puedePreregistro = puedePreregistro,
            puedeNuevaEntrada = puedeNuevaEntrada,
            puedeSalidaRelacion = puedeSalidaRelacion,
            puedeSalidaFisica = puedeSalidaFisica
        )
    )
    val uiState: StateFlow<CasetaHubUiState> = _uiState.asStateFlow()

    private var historicoJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            var entradasHoy = _uiState.value.entradasHoy
            var salidasHoy = _uiState.value.salidasHoy
            var enPlanta = _uiState.value.enPlanta
            var enCamino = _uiState.value.enCamino
            var errorMessage: String? = null

            repository.getConteosHoy()
                .onSuccess { data ->
                    entradasHoy = data["entradas"] ?: 0
                    salidasHoy = data["salidas"] ?: 0
                }
                .onFailure {
                    errorMessage = it.message ?: "No se pudieron actualizar los conteos de caseta."
                }

            // Esta es la lista de trabajo del hub: las cajas que siguen adentro
            // de la planta (sin fecha de salida). Trae cliente, línea, tipo de
            // movimiento y días en patio, así que el filtrado es local e
            // instantáneo -- no hay que volver al servidor por cada tecla.
            if (puedeSalidaFisica) {
                salidaRepository.getPendientes()
                    .onSuccess { pendientes -> enPlanta = pendientes }
                    .onFailure {
                        if (errorMessage == null) {
                            errorMessage = it.message ?: "No se pudo cargar el patio."
                        }
                    }
            }

            // Pre-registros vigentes con ubicacion (camiones sin caja en camino).
            // Falla en silencio: no es critico para el resto del hub.
            repository.getEnCamino()
                .onSuccess { items -> enCamino = items }
                .onFailure { }

            _uiState.update { current ->
                current.copy(
                    entradasHoy = entradasHoy,
                    salidasHoy = salidasHoy,
                    enPlanta = enPlanta,
                    enCamino = enCamino,
                    isLoading = false,
                    errorMessage = errorMessage,
                    lastUpdated = nowLabel()
                ).recalcular()
            }
        }
    }

    fun setQuery(value: String) {
        _uiState.update {
            it.copy(
                query = value,
                historico = emptyList(),
                historicoBuscado = false
            ).recalcular()
        }
    }

    fun clearQuery() = setQuery("")

    /**
     * Lectura del escáner (cámara o lector físico). Se queda con el primer
     * token del código y lo mete tal cual en la búsqueda: en el hub el escaneo
     * es para *encontrar* una caja, no para capturarla.
     */
    fun onScannerInput(raw: String) {
        val clean = BarcodeValueNormalizer.normalize(raw)
            .split(Regex("\\s+"))
            .firstOrNull()
            .orEmpty()
            .trim(',', ';')
            .uppercase(Locale.ROOT)
        if (clean.isBlank()) return
        setQuery(clean)
    }

    fun toggleFiltro(facet: CasetaHubFacet, value: String?) {
        _uiState.update { current ->
            // Volver a elegir la misma opción la quita: el chip es un interruptor.
            val nuevo = if (value != null && current.filtroDe(facet) == value) null else value
            val actualizado = when (facet) {
                CasetaHubFacet.CLIENTE -> current.copy(filtroCliente = nuevo)
                CasetaHubFacet.LINEA -> current.copy(filtroLinea = nuevo)
                CasetaHubFacet.MOVIMIENTO -> current.copy(filtroMovimiento = nuevo)
            }
            actualizado.copy(historico = emptyList(), historicoBuscado = false).recalcular()
        }
    }

    fun limpiarFiltros() {
        _uiState.update {
            it.copy(
                query = "",
                filtroCliente = null,
                filtroLinea = null,
                filtroMovimiento = null,
                historico = emptyList(),
                historicoBuscado = false
            ).recalcular()
        }
    }

    /**
     * La caja no está en el patio: puede haber salido hace días. Esto va al
     * servidor con el mismo texto y los mismos filtros de la pantalla.
     */
    fun buscarEnHistorico() {
        val state = _uiState.value
        if (state.query.isBlank() && !state.hayFiltros) return

        historicoJob?.cancel()
        historicoJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearchingHistorico = true) }
            delay(120) // deja respirar al toque antes de disparar la red
            repository.searchRecords(
                term = state.query.trim(),
                limit = 30,
                tipoMov = state.filtroMovimiento,
                cliente = state.filtroCliente,
                linea = state.filtroLinea
            )
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            historico = items,
                            isSearchingHistorico = false,
                            historicoBuscado = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            historico = emptyList(),
                            isSearchingHistorico = false,
                            historicoBuscado = true,
                            errorMessage = error.message ?: "No se pudo buscar en el histórico.",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearInfoMessage() {
        _uiState.update { it.copy(infoMessage = null) }
    }

    /**
     * Reimprime la etiqueta de caseta de una caja que ya esta en patio. Es lo
     * unico que hacia el buscador viejo y que el hub no cubria: se trajo aqui
     * para que la hoja de acciones de la caja quede completa.
     */
    fun imprimirEtiqueta(caja: SalidaCajaItem, copies: Int = 1) {
        viewModelScope.launch {
            val config = zebraPrinterPreferences.getConfig(ZebraLabelType.CASETA)
            if (!config.isHostConfigured) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Impresora de caseta no configurada.",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                return@launch
            }
            try {
                val labelData = ZebraLabelData(
                    qrValue = caja.casetaId,
                    numeroEntrada = caja.numCaja.ifBlank { caja.numCamion },
                    fechaArribo = caja.fechaArribo.orEmpty(),
                    cliente = caja.nombreCliente,
                    copies = copies
                )
                val layout = zebraLabelLayoutPreferences.getLayout(ZebraLabelType.CASETA)
                val zpl = labelZplFactory.buildLabels(
                    listOf(labelData),
                    compress = true,
                    layoutOverride = layout
                )
                zebraPrinterService.printZpl(config, zpl, copies)
                _uiState.update { it.copy(infoMessage = "Etiqueta enviada a impresión") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        errorMessage = "Error de impresión: " + (e.message ?: "desconocido"),
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
        }
    }

    /** Reaplica texto y filtros sobre el patio. Se llama en cada cambio de estado. */
    private fun CasetaHubUiState.recalcular(): CasetaHubUiState {
        val visibles = enPlanta.filter { item ->
            CasetaHubUiState.coincideTexto(item, query) &&
                (filtroCliente == null || item.nombreCliente == filtroCliente) &&
                (filtroLinea == null || CasetaHubUiState.lineaDe(item) == filtroLinea) &&
                (filtroMovimiento == null || item.tipoMovimiento == filtroMovimiento)
        }
        return copy(visibles = visibles)
    }

    private fun nowLabel(): String {
        return SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-MX")).format(Date())
    }
}
