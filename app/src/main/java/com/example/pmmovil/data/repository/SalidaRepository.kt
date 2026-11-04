package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CargarRelacionResponse
import com.example.pmmovil.data.model.EtiquetasDescargaResponse
import com.example.pmmovil.data.model.SalidaDescargarRequest
import com.example.pmmovil.data.model.SalidaCasetaQrRequest
import com.example.pmmovil.data.model.SalidaCasetaQrResponse
import com.example.pmmovil.data.model.SalidaOperacionResponse
import com.example.pmmovil.data.model.SalidaScanRequest
import com.example.pmmovil.data.model.SalidaScanResponse
import com.example.pmmovil.data.model.SalidaSinRelacionScanRequest
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

class SalidaApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

/**
 * Repositorio de Salida de Bodega.
 *
 * A diferencia de otros repos, NO lanza excepción cuando `success == false`:
 * la lógica de negocio de Salida necesita inspeccionar el cuerpo completo
 * (p.ej. `tipo == NO_PERTENECE` o `BLOQUEADO` son resultados válidos, no
 * errores). Solo lanza en fallo HTTP real (cuerpo nulo / no-2xx) o de red
 * (IOException, capturada por runCatching → Result.failure).
 */
@Singleton
class SalidaRepository @Inject constructor(private val api: PMMovilApi) {

    suspend fun cargarRelacion(relno: String, version: String?): Result<CargarRelacionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.cargarRelacionSalida(relno, version?.takeIf(String::isNotBlank))
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudo cargar la relación.")
                )
            }
        }

    suspend fun cargarSinRelacion(entrada: String): Result<CargarRelacionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.cargarSinRelacionSalida(entrada)
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudo cargar la entrada.")
                )
            }
        }

    suspend fun getEtiquetasDescarga(relno: String): Result<EtiquetasDescargaResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.getEtiquetasDescargaSalida(relno)
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudieron obtener los bultos a descargar.")
                )
            }
        }

    suspend fun validarCasetaQr(
        relno: String,
        entrada: String,
        casetaId: String
    ): Result<SalidaCasetaQrResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.validarCasetaSalida(SalidaCasetaQrRequest(relno, entrada, casetaId))
            r.body() ?: throw SalidaApiException(
                r.code(),
                errorMessage(r, "No se pudo validar el QR de caseta.")
            )
        }
    }

    suspend fun scan(
        relno: String,
        textoEtiqueta: String,
        autorizacionToken: String
    ): Result<SalidaScanResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.scanSalida(SalidaScanRequest(relno, textoEtiqueta, autorizacionToken))
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudo registrar el escaneo.")
                )
            }
        }

    suspend fun scanSinRelacion(entrada: String, textoEtiqueta: String, autorizacionToken: String): Result<SalidaScanResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.scanSinRelacionSalida(SalidaSinRelacionScanRequest(entrada, textoEtiqueta, autorizacionToken))
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudo registrar el escaneo.")
                )
            }
        }

    suspend fun descargar(relno: String, textoEtiqueta: String): Result<SalidaScanResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.descargarSalida(SalidaDescargarRequest(relno, textoEtiqueta))
                r.body() ?: throw SalidaApiException(
                    r.code(),
                    errorMessage(r, "No se pudo descargar el bulto.")
                )
            }
        }

    suspend fun finalizar(relno: String): Result<SalidaOperacionResponse> =
        operacion("No se pudo finalizar la salida.") { api.finalizarSalida(relno) }

    suspend fun pausar(relno: String): Result<SalidaOperacionResponse> =
        operacion("No se pudo pausar la operación.") { api.pausarSalida(relno) }

    /** [restaurar]: estado con el que llegó la relación ("EN ESPERA" si venía pausada) para no perder la pausa al cancelar. */
    suspend fun cancelar(relno: String, restaurar: String? = null): Result<SalidaOperacionResponse> =
        operacion("No se pudo cancelar la operación.") { api.cancelarSalida(relno, restaurar?.takeIf(String::isNotBlank)) }

    /** Cierra una carga sin relación (entrada directa/transbordo): la entrada no es una relación, tiene su propio cierre. */
    suspend fun finalizarSinRelacion(entrada: String): Result<SalidaOperacionResponse> =
        operacion("No se pudo finalizar la carga.") { api.finalizarSinRelacionSalida(entrada) }

    suspend fun solicitarAutorizacion(relno: String): Result<SalidaOperacionResponse> =
        operacion("No se pudo solicitar la autorización.") { api.solicitarAutorizacionSalida(relno) }

    suspend fun finalizarDescarga(relno: String): Result<SalidaOperacionResponse> =
        operacion("No se pudo finalizar la descarga.") { api.finalizarDescargaSalida(relno) }

    private suspend fun operacion(
        fallback: String,
        call: suspend () -> Response<SalidaOperacionResponse>
    ): Result<SalidaOperacionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = call()
            r.body() ?: throw SalidaApiException(r.code(), errorMessage(r, fallback))
        }
    }

    private fun errorMessage(response: Response<*>, fallback: String): String {
        val rawBody = runCatching { response.errorBody()?.string() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: return fallback
        return runCatching {
            JsonParser.parseString(rawBody)
                .asJsonObject
                .get("message")
                ?.asString
        }.getOrNull()?.takeIf(String::isNotBlank) ?: fallback
    }
}
