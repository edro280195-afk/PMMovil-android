package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CerrarRegistroRequest
import com.example.pmmovil.data.model.RegistrarSalidaCajaRequest
import com.example.pmmovil.data.model.RegistrarSalidaCajaResponse
import com.example.pmmovil.data.model.SalidaCajaItem
import com.example.pmmovil.data.model.SalidaPatioResponse
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

    /** Patio clasificado por carril (lista / manual / en proceso / por cerrar). */
    suspend fun getPatio(): Result<SalidaPatioResponse> = io {
        val response = api.getSalidaPatio()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudo cargar el patio.")
        }
    }

    /** La relación ya salió: cierra solo el registro de patio de sus cajas. */
    suspend fun cerrarRegistro(relno: String): Result<RegistrarSalidaCajaResponse> = io {
        val response = api.cerrarRegistroSalida(CerrarRegistroRequest(relno = relno))
        response.body() ?: throw apiFailure(
            response = response,
            bodyMessage = null,
            fallback = "No se pudo cerrar el registro de patio."
        )
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
