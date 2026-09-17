package com.example.pmmovil.ui.encamino

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CasetaPreregistroEnCaminoItem
import com.example.pmmovil.data.repository.CasetaEntradaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Destino en el mapa: la bodega/planta a la que se dirige uno o más camiones en camino. */
data class BodegaMarker(
    val idBodega: String,
    val lat: Double,
    val lon: Double
)

data class EnCaminoMapUiState(
    val items: List<CasetaPreregistroEnCaminoItem> = emptyList(),
    val bodegas: List<BodegaMarker> = emptyList(),
    val selectedFolio: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val lastUpdated: String = ""
) {
    /** Solo los que tienen coordenada real -- los demás se listan pero no se pintan. */
    val itemsConUbicacion: List<CasetaPreregistroEnCaminoItem>
        get() = items.filter(CasetaPreregistroEnCaminoItem::tieneUbicacion)
}

/**
 * Estado y datos para la pantalla de mapa de "Camiones en camino" (pre-registro
 * sin caja). Reusa [CasetaEntradaRepository.getEnCamino] -- misma fuente que ya
 * alimentaba la tarjeta compacta del hub -- y agrega las coordenadas de cada
 * bodega destino (una llamada por idBodega distinto encontrado en la lista,
 * casi siempre solo una).
 */
@HiltViewModel
class EnCaminoMapViewModel @Inject constructor(
    private val repository: CasetaEntradaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(EnCaminoMapUiState())
    val uiState: StateFlow<EnCaminoMapUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            repository.getEnCamino()
                .onSuccess { items ->
                    val bodegas = cargarBodegas(items)
                    _uiState.update {
                        it.copy(
                            items = items,
                            bodegas = bodegas,
                            isLoading = false,
                            lastUpdated = nowLabel()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "No se pudieron cargar los camiones en camino."
                        )
                    }
                }
        }
    }

    /** Tocar una fila de la lista centra el mapa en ese camión (si tiene ubicación). */
    fun selectItem(folio: String) {
        _uiState.update { it.copy(selectedFolio = folio) }
    }

    private suspend fun cargarBodegas(items: List<CasetaPreregistroEnCaminoItem>): List<BodegaMarker> {
        val idsBodega = items.map { it.idBodega }.filter(String::isNotBlank).distinct()
        if (idsBodega.isEmpty()) return emptyList()

        return kotlinx.coroutines.coroutineScope {
            idsBodega
                .map { idBodega -> async { idBodega to repository.getBodegaCoordenadas(idBodega).getOrNull() } }
                .awaitAll()
                .mapNotNull { (idBodega, coords) ->
                    if (coords != null && coords.tieneUbicacion) {
                        BodegaMarker(idBodega = idBodega, lat = coords.lat!!, lon = coords.lon!!)
                    } else {
                        null
                    }
                }
        }
    }

    private fun nowLabel(): String =
        SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-MX")).format(Date())
}
