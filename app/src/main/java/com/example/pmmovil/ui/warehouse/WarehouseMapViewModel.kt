package com.example.pmmovil.ui.warehouse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.DEFAULT_CLASE_MODELS
import com.example.pmmovil.data.model.LocationOccupancy
import com.example.pmmovil.data.model.WarehouseLayout
import com.example.pmmovil.data.repository.WarehouseLayoutRepository
import com.example.pmmovil.data.signalr.WarehouseHubService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WarehouseMapUiState(
    val isLoading: Boolean = true,
    val isRefreshingOccupancy: Boolean = false,
    val layout: WarehouseLayout? = null,
    val occupancy: Map<String, LocationOccupancy> = emptyMap(),
    val claseModels: List<ClaseModelSpec> = DEFAULT_CLASE_MODELS,
    val occupancyError: String? = null,
    val lastOccupancyRefreshMillis: Long = 0L,
    val error: String? = null
)

@HiltViewModel
class WarehouseMapViewModel @Inject constructor(
    private val repository: WarehouseLayoutRepository,
    private val warehouseHubService: WarehouseHubService
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarehouseMapUiState())
    val uiState: StateFlow<WarehouseMapUiState> = _uiState.asStateFlow()
    private var isObservingBodegaEvents = false

    init {
        load()
    }

    fun load(warehouseId: String = WarehouseLayoutRepository.DEFAULT_WAREHOUSE) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            repository.getLayoutWithOccupancy(warehouseId)
                .onSuccess {
                    _uiState.value = WarehouseMapUiState(
                        isLoading = false,
                        layout = it.layout,
                        occupancy = it.occupancy,
                        claseModels = it.claseModels,
                        occupancyError = it.occupancyError,
                        lastOccupancyRefreshMillis = System.currentTimeMillis()
                    )
                }
                .onFailure {
                    _uiState.value = WarehouseMapUiState(
                        isLoading = false,
                        error = it.message ?: "No se pudo cargar el mapa de bodega."
                    )
                }
        }
    }

    fun startBodegaEventRefresh() {
        if (isObservingBodegaEvents) return
        isObservingBodegaEvents = true
        warehouseHubService.startConnection()
        viewModelScope.launch {
            warehouseHubService.actividadFlow.collect {
                refreshOccupancy()
            }
        }
    }

    fun refreshOccupancy(warehouseId: String = WarehouseLayoutRepository.DEFAULT_WAREHOUSE) {
        if (_uiState.value.isRefreshingOccupancy) return
        _uiState.update { it.copy(isRefreshingOccupancy = true, occupancyError = null) }
        viewModelScope.launch {
            repository.getOccupancy(warehouseId)
                .onSuccess { locations ->
                    _uiState.update {
                        it.copy(
                            isRefreshingOccupancy = false,
                            occupancy = locations,
                            occupancyError = null,
                            lastOccupancyRefreshMillis = System.currentTimeMillis()
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRefreshingOccupancy = false,
                            occupancyError = error.message ?: "No se pudo actualizar la ocupacion."
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
