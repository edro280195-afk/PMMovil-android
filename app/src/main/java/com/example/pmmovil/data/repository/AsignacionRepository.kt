package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.AsignacionDao
import com.example.pmmovil.data.model.AsignacionEntity
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
