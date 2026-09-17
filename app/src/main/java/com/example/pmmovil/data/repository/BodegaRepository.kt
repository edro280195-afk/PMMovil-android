package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.BodegaEntradaDto
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

class BodegaApiException(val statusCode: Int, message: String) : Exception(message)

@Singleton
class BodegaRepository @Inject constructor(private val api: PMMovilApi) {

    suspend fun getLista(): Result<List<BodegaEntradaDto>> = io {
        val response = api.getBodegaLista()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items.orEmpty()
        } else {
            throw failure(response, body?.message, "No se pudo cargar la lista de entradas.")
        }
    }

    suspend fun buscar(q: String): Result<List<BodegaEntradaDto>> = io {
        val response = api.buscarBodega(q)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items.orEmpty()
        } else {
            throw failure(response, body?.message, "No se pudo realizar la búsqueda.")
        }
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private fun failure(
        response: Response<*>,
        bodyMessage: String?,
        fallback: String
    ): BodegaApiException {
        val message = bodyMessage?.takeIf(String::isNotBlank)
            ?: parseError(response)
            ?: fallback
        return BodegaApiException(response.code(), message)
    }

    private fun parseError(response: Response<*>): String? {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return null
        return runCatching {
            JsonParser.parseString(raw).asJsonObject.get("message")?.asString
        }.getOrNull()
    }
}
