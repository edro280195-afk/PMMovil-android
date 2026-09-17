package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CasetaDetalleResponse
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaRequest
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaResponse
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

@Singleton
class CasetaRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getPendientes(): Result<List<CasetaPendiente>> = io {
        val response = api.getCasetaPendientes()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.pendientes
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las salidas de caseta.")
        }
    }

    suspend fun getDetalle(relno: String): Result<CasetaDetalleResponse> = io {
        val response = api.getCasetaDetalle(relno)
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudo consultar la relación."
        )
    }

    suspend fun confirmarSalida(
        relno: String,
        usuario: String,
        observaciones: String = "",
        fotosBase64: List<String>? = null
    ): Result<ConfirmarSalidaCasetaResponse> = io {
        val response = api.confirmarSalidaCaseta(
            relno = relno,
            request = ConfirmarSalidaCasetaRequest(
                usuario = usuario,
                observaciones = observaciones,
                fotosBase64 = fotosBase64
            )
        )
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudo confirmar la salida."
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
