package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.AsignacionDao
import com.example.pmmovil.data.model.AsignacionEntity
import com.example.pmmovil.data.model.TomarCarpetaResponse
import com.example.pmmovil.data.model.toEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AsignacionRepository @Inject constructor(
    private val api: PMMovilApi,
    private val dao: AsignacionDao
) {
    fun getAsignaciones(): Flow<List<AsignacionEntity>> = dao.getAllAsignaciones()

    suspend fun removeAssignment(idAsignacion: Int): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                dao.deleteById(idAsignacion)
            }
        }
    }

    suspend fun syncAsignaciones(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getMisAsignaciones()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.success && body.items != null) {
                        val entities = body.items.map { it.toEntity() }
                        // Limpiamos los que ya están sincronizados y guardamos los nuevos
                        dao.clearSyncedAsignaciones()
                        dao.insertAll(entities)
                        Result.success(Unit)
                    } else {
                        Result.failure(Exception(body.message ?: "Error desconocido"))
                    }
                } else {
                    Result.failure(Exception("Error de servidor: ${response.code()}"))
                }
            } catch (e: Exception) {
                // Si falla la red, el flujo asíncrono (Flow) de Room seguirá mostrando 
                // las asignaciones cacheadas, logrando el modo Offline.
                Result.failure(e)
            }
        }
    }

    /**
     * «Tomar carpeta». Un fallo de red es `Result.failure`; cualquier respuesta del servidor (incluso «ya la tiene otra
     * persona» o «ya está revisada») es `Result.success` con el código para que la pantalla decida qué decir.
     */
    suspend fun tomarCarpeta(crefAgrupada: String, confirmar: Boolean): Result<TomarCarpetaResponse> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = api.tomarCarpeta(crefAgrupada, confirmar)
                response.body()
                    ?: throw Exception("No se pudo tomar la carpeta (HTTP ${response.code()}).")
            }
        }
    }

    /** El revisador devuelve una carpeta que tomó por error. `true` si había una toma suya que soltar. */
    suspend fun soltarCarpeta(crefAgrupada: String): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = api.soltarCarpeta(crefAgrupada)
                val body = response.body()
                    ?: throw Exception("No se pudo devolver la carpeta (HTTP ${response.code()}).")
                body.success
            }
        }
    }

    suspend fun markViewed(idAsignacion: Int): Result<Boolean> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val response = api.markAssignmentViewed(idAsignacion)
                val body = response.body()
                if (!response.isSuccessful || body?.success != true) {
                    throw Exception(body?.message ?: "No se pudo marcar la asignación como vista.")
                }
                dao.updateEstatus(idAsignacion, 1)
                body.cambio == true
            }
        }
    }
}
