package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.FotosMultipart
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

/** El servidor aun no recibe las fotos como archivos: se mandan como texto base64, como antes. */
class EnvioComoArchivoNoDisponible : Exception("El servidor no admite fotos como archivo.")

/**
 * Lo que la salida con fotos necesita del servidor. [CasetaRepository] lo cumple; las pruebas de
 * [com.example.pmmovil.ui.salidacaseta.SalidaFotosEnvio] usan uno falso.
 */
interface SalidaFotosServidor {
    /** Un lote como archivos. Falla con [EnvioComoArchivoNoDisponible] si el servidor no tiene esa ruta. */
    suspend fun subirFotosSalidaArchivos(
        relno: String,
        indiceInicial: Int,
        fotos: List<ByteArray>
    ): Result<ConfirmarSalidaCasetaResponse>

    /** Un lote como texto base64. Falla con [EnvioPorLotesNoDisponible] si el servidor no tiene envío por lotes. */
    suspend fun subirFotosSalida(
        relno: String,
        indiceInicial: Int,
        fotosBase64: List<String>
    ): Result<ConfirmarSalidaCasetaResponse>

    suspend fun confirmarSalida(
        relno: String,
        usuario: String,
        observaciones: String = "",
        fotosBase64: List<String>? = null,
        totalFotos: Int? = null
    ): Result<ConfirmarSalidaCasetaResponse>
}

@Singleton
class CasetaRepository @Inject constructor(
    private val api: PMMovilApi
) : SalidaFotosServidor {
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
    override suspend fun confirmarSalida(
        relno: String,
        usuario: String,
        observaciones: String,
        fotosBase64: List<String>?,
        totalFotos: Int?
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
    override suspend fun subirFotosSalida(
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

    /**
     * El mismo lote, como archivos. Un servidor anterior no tiene la ruta (404/405/415): falla con
     * [EnvioComoArchivoNoDisponible] y quien llama vuelve a [subirFotosSalida].
     */
    override suspend fun subirFotosSalidaArchivos(
        relno: String,
        indiceInicial: Int,
        fotos: List<ByteArray>
    ): Result<ConfirmarSalidaCasetaResponse> = io {
        val response = api.subirFotosSalidaCasetaArchivos(
            relno = relno,
            indiceInicial = FotosMultipart.texto(indiceInicial.toString()),
            fotos = fotos.mapIndexed { i, bytes -> FotosMultipart.foto("fotos", "${indiceInicial + i}.jpg", bytes) }
        )
        if (response.code() == 404 || response.code() == 405 || response.code() == 415) throw EnvioComoArchivoNoDisponible()
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
