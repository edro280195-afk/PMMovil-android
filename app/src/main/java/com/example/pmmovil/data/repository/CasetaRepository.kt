package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CasetaDetalleResponse
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaRequest
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaResponse
import com.example.pmmovil.data.model.SubirFotosSalidaCasetaRequest
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

/** El servidor aun no tiene el envio de fotos por lotes: se manda todo junto en la confirmacion, como antes. */
class EnvioPorLotesNoDisponible : Exception("El servidor no admite el envío de fotos por lotes.")

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

    /**
     * [totalFotos] con valor = las fotos ya se subieron por lotes con [subirFotosSalida] y aqui solo se confirma;
     * sin valor, las fotos viajan en [fotosBase64] en un solo envio, como antes.
     */
    suspend fun confirmarSalida(
        relno: String,
        usuario: String,
        observaciones: String = "",
        fotosBase64: List<String>? = null,
        totalFotos: Int? = null
    ): Result<ConfirmarSalidaCasetaResponse> = io {
        val response = api.confirmarSalidaCaseta(
            relno = relno,
            request = ConfirmarSalidaCasetaRequest(
                usuario = usuario,
                observaciones = observaciones,
                fotosBase64 = fotosBase64,
                totalFotos = totalFotos
            )
        )
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudo confirmar la salida."
        )
    }

    /**
     * Sube un lote de fotos de la salida antes de confirmarla. Si el servidor todavia no tiene el endpoint (404/405)
     * falla con [EnvioPorLotesNoDisponible] y quien llama vuelve al envio unico.
     */
    suspend fun subirFotosSalida(
        relno: String,
        indiceInicial: Int,
        fotosBase64: List<String>
    ): Result<ConfirmarSalidaCasetaResponse> = io {
        val response = api.subirFotosSalidaCaseta(
            relno = relno,
            request = SubirFotosSalidaCasetaRequest(indiceInicial = indiceInicial, fotosBase64 = fotosBase64)
        )
        if (response.code() == 404 || response.code() == 405) throw EnvioPorLotesNoDisponible()
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudieron enviar las fotos."
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
