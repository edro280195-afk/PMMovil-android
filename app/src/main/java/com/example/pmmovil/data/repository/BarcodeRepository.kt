package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.barcode.BarcodeTrace
import com.example.pmmovil.data.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

class BarcodeApiException(
    val statusCode: Int,
    message: String
) : Exception(message)

@Singleton
class BarcodeRepository @Inject constructor(private val api: PMMovilApi) {

    suspend fun getConfig(crefAgrupada: String): Result<BarcodeConfigResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getBarcodeConfig(crefAgrupada)
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(
                    r,
                    "No se pudo cargar la configuración de barcode."
                )
            )
        }
    }

    suspend fun getEstado(crefAgrupada: String): Result<EstadoBarcodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getEstadoBarcode(crefAgrupada)
            val body = r.body()
            BarcodeTrace.d(
                "API estado http=${r.code()} cref=$crefAgrupada activa=${body?.activa} " +
                    "sesion=${body?.idSesion} total=${body?.lecturas.orEmpty().sumOf { it.cantidad }} " +
                    "productos=${traceProducts(body?.lecturas.orEmpty())}"
            )
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "No se pudo recuperar la sesión.")
            )
        }
    }

    suspend fun iniciarSesion(crefAgrupada: String): Result<IniciarSesionBarcodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.iniciarSesionBarcode(IniciarSesionBarcodeRequest(crefAgrupada))
            val body = r.body()
            BarcodeTrace.d(
                "API iniciar http=${r.code()} cref=$crefAgrupada success=${body?.success} " +
                    "sesion=${body?.idSesion}"
            )
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "No se pudo iniciar la sesión de barcode.")
            )
        }
    }

    suspend fun registrarLectura(
        idSesion: Int,
        codigoRaw: String,
        cnofact: String = "",
        clienteScanId: String = ""
    ): Result<LecturaResponse> = withContext(Dispatchers.IO) {
        runCatching {
            BarcodeTrace.d(
                "API lectura request sesion=$idSesion scanId=$clienteScanId " +
                    "codigo=${codigoRaw.take(80)} rawLen=${codigoRaw.length}"
            )
            val r = api.registrarLectura(
                RegistrarLecturaRequest(
                    idSesion = idSesion,
                    codigoRaw = codigoRaw,
                    cnofact = cnofact,
                    clienteScanId = clienteScanId
                )
            )
            val body = r.body()
            BarcodeTrace.d(
                "API lectura response http=${r.code()} success=${body?.success} " +
                    "duplicada=${body?.duplicada} cid=${body?.cidproduct} " +
                    "existe=${body?.existe} descripcion=${body?.descripcion?.take(48)}"
            )
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "No se pudo registrar la lectura.")
            )
        }.onFailure { error ->
            BarcodeTrace.w(
                "API lectura failure sesion=$idSesion scanId=$clienteScanId " +
                    "mensaje=${error.message}",
                error
            )
        }
    }

    suspend fun getResumen(idSesion: Int): Result<ResumenBarcodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getResumenBarcode(idSesion)
            val body = r.body()
            BarcodeTrace.d(
                "API resumen http=${r.code()} sesion=$idSesion " +
                    "total=${body?.productos.orEmpty().sumOf { it.cantidad }} " +
                    "cabeceraTotal=${body?.sesion?.totalLecturas} " +
                    "productos=${traceProducts(body?.productos.orEmpty())}"
            )
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "No se pudo obtener el resumen.")
            )
        }
    }

    suspend fun restarLectura(idSesion: Int, cidproduct: String, codigoRaw: String = ""): Result<RestarLecturaResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.restarLectura(RestarLecturaRequest(idSesion, cidproduct, codigoRaw))
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                errorMessage(r, "No se pudo corregir la lectura.")
            )
        }
    }

    suspend fun eliminarProducto(idSesion: Int, cidproduct: String, codigoRaw: String = ""): Result<SimpleSuccessResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.eliminarProductoCompleto(EliminarProductoRequest(idSesion, cidproduct, codigoRaw))
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "No se pudo eliminar el producto.")
            )
        }
    }

    suspend fun finalizar(idSesion: Int, enviarCorreo: Boolean, observaciones: String): Result<FinalizarBarcodeResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.finalizarBarcode(idSesion, FinalizarBarcodeRequest(enviarCorreo, observaciones))
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw BarcodeApiException(
                r.code(),
                body?.message ?: errorMessage(r, "Error al finalizar la sesión.")
            )
        }
    }

    suspend fun getEstadoCorreo(idSesion: Int): Result<EstadoCorreoBarcodeResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getEstadoCorreoBarcode(idSesion)
                val body = response.body()
                if (response.isSuccessful && body?.success == true) body
                else throw BarcodeApiException(
                    response.code(),
                    body?.message ?: errorMessage(
                        response,
                        "No se pudo consultar el correo."
                    )
                )
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

    private fun traceProducts(products: List<ProductoResumen>): String =
        products.joinToString(separator = " | ", limit = 50, truncated = " | ...") {
            "${it.cidproduct}=${it.cantidad},ex=${it.existe},raw=${it.codigoRaw?.take(48)}"
        }.ifBlank { "<sin-productos>" }
}
