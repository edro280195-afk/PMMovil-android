package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.RegistrarSalidaCajaRequest
import com.example.pmmovil.data.model.RegistrarSalidaCajaResponse
import com.example.pmmovil.data.model.SalidaCajaItem
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

@Singleton
class SalidaCasetaRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getPendientes(): Result<List<SalidaCajaItem>> = io {
        val response = api.getSalidaCasetaPendientes()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.pendientes
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las cajas en patio.")
        }
    }

    suspend fun revisarRelacion(casetaId: String): Result<String> = io {
        val response = api.revisarSalidaCasetaRelacion(casetaId)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.relacion
        } else {
            throw apiFailure(response, body?.message, "No se pudo revisar la relación de la caja.")
        }
    }

    suspend fun registrarSalida(
        casetaId: String,
        observaciones: String
    ): Result<RegistrarSalidaCajaResponse> = io {
        val response = api.registrarSalidaCaseta(
            RegistrarSalidaCajaRequest(casetaId = casetaId, observaciones = observaciones)
        )
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudo registrar la salida."
        )
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
