package com.example.pmmovil.data.repository

import android.content.SharedPreferences
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.DEFAULT_CLASE_MODELS
import com.example.pmmovil.data.model.LocationOccupancy
import com.example.pmmovil.data.model.WarehouseLayout
import com.example.pmmovil.data.model.WarehouseMapData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consume GET /api/WarehouseLayout/{id}. Cachea el ultimo layout por bodega en
 * SharedPreferences para operar offline (offline-first: si la red falla, devuelve
 * el ultimo publicado). Solo lectura por ahora; la edicion vive en la web.
 */
@Singleton
class WarehouseLayoutRepository @Inject constructor(
    private val api: PMMovilApi,
    private val prefs: SharedPreferences
) {
    private val gson = Gson()

    suspend fun getLayout(warehouseId: String = DEFAULT_WAREHOUSE): Result<WarehouseLayout> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getWarehouseLayout(warehouseId)
                val body = response.body()
                if (response.isSuccessful && body?.success == true && body.layout != null) {
                    cacheLayout(warehouseId, body.layout)
                    body.layout
                } else {
                    cachedLayout(warehouseId)
                        ?: throw IllegalStateException(
                            body?.message ?: "No se pudo cargar el mapa de bodega."
                        )
                }
            }.recoverCatching { error ->
                // Sin red: intenta con el ultimo layout cacheado.
                cachedLayout(warehouseId) ?: throw error
            }
        }

    suspend fun getLayoutWithOccupancy(
        warehouseId: String = DEFAULT_WAREHOUSE
    ): Result<WarehouseMapData> =
        withContext(Dispatchers.IO) {
            runCatching {
                val layout = getLayout(warehouseId).getOrThrow()
                val occupancyResult = getOccupancy(warehouseId)
                val claseModels = getClaseModels().getOrDefault(DEFAULT_CLASE_MODELS)
                WarehouseMapData(
                    layout = layout,
                    occupancy = occupancyResult.getOrDefault(emptyMap()),
                    claseModels = claseModels,
                    occupancyError = occupancyResult.exceptionOrNull()?.message
                )
            }
        }

    suspend fun getOccupancy(
        warehouseId: String = DEFAULT_WAREHOUSE
    ): Result<Map<String, LocationOccupancy>> =
        runCatching {
            val response = api.getWarehouseOccupancy(warehouseId)
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                normalizeOccupancy(body.locations)
            } else {
                throw IllegalStateException(
                    body?.message ?: "No se pudo cargar la ocupacion de bodega."
                )
            }
        }

    private fun normalizeOccupancy(
        locations: Map<String, LocationOccupancy>
    ): Map<String, LocationOccupancy> =
        locations.mapKeys { it.key.trim().uppercase() }

    suspend fun getClaseModels(): Result<List<ClaseModelSpec>> =
        runCatching {
            val response = api.getClaseModelos()
            val body = response.body()
            if (response.isSuccessful && body?.success == true) {
                body.catalogo?.items?.takeIf { it.isNotEmpty() } ?: DEFAULT_CLASE_MODELS
            } else {
                DEFAULT_CLASE_MODELS
            }
        }.recover { DEFAULT_CLASE_MODELS }

    private fun cacheKey(id: String) = "$CACHE_PREFIX${id.uppercase()}"

    private fun cacheLayout(id: String, layout: WarehouseLayout) {
        runCatching { prefs.edit().putString(cacheKey(id), gson.toJson(layout)).apply() }
    }

    private fun cachedLayout(id: String): WarehouseLayout? {
        val json = prefs.getString(cacheKey(id), null) ?: return null
        return runCatching { gson.fromJson(json, WarehouseLayout::class.java) }.getOrNull()
    }

    companion object {
        const val DEFAULT_WAREHOUSE = "MAQUILA"
        private const val CACHE_PREFIX = "WAREHOUSE_LAYOUT_"
    }
}
