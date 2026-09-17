package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CargarRelacionResponse
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.model.RelacionPendiente
import com.example.pmmovil.data.model.SalidaOperacionResponse
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

@Singleton
class RelacionRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getPending(query: String): Result<List<RelacionPendiente>> = io {
        val response = api.getRelacionesPendientes(query.takeIf(String::isNotBlank))
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.pendientes
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las relaciones.")
        }
    }

    suspend fun getGatePending(): Result<List<CasetaPendiente>> = io {
        val response = api.getCasetaPendientes()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.pendientes
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las salidas de caseta.")
        }
    }

    suspend fun getDetail(relno: String): Result<CargarRelacionResponse> = io {
        val response = api.getRelacionSupervisor(relno)
        response.body() ?: throw apiFailure(
            response,
            null,
            "No se pudo consultar la relación."
        )
    }

    suspend fun authorize(relno: String): Result<SalidaOperacionResponse> =
        operation("No se pudo autorizar la relación.") { api.autorizarRelacion(relno) }

    suspend fun returnToWaiting(relno: String): Result<SalidaOperacionResponse> =
        operation("No se pudo retornar la relación.") { api.retornarRelacionEspera(relno) }

    suspend fun deleteLoad(relno: String): Result<SalidaOperacionResponse> =
        operation("No se pudo eliminar la carga.") { api.eliminarCargaRelacion(relno) }

    suspend fun unlock(relno: String): Result<SalidaOperacionResponse> =
        operation("No se pudo desbloquear la relación.") { api.desbloquearRelacion(relno) }

    private suspend fun operation(
        fallback: String,
        call: suspend () -> Response<SalidaOperacionResponse>
    ): Result<SalidaOperacionResponse> = io {
        val response = call()
        response.body() ?: throw apiFailure(response, null, fallback)
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private fun apiFailure(
        response: Response<*>,
        bodyMessage: String?,
        fallback: String
    ): Exception {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val parsed = raw?.let {
            runCatching {
                JsonParser.parseString(it).asJsonObject.get("message")?.asString
            }.getOrNull()
        }
        return IllegalStateException(
            bodyMessage?.takeIf(String::isNotBlank)
                ?: parsed?.takeIf(String::isNotBlank)
                ?: fallback
        )
    }
}
