package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.BodegaCoordenadas
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.data.model.CasetaEntradaBoxOcrRequest
import com.example.pmmovil.data.model.CasetaEntradaBoxOcrResponse
import com.example.pmmovil.data.model.CasetaEntradaCatalogItem
import com.example.pmmovil.data.model.CasetaEntradaCatalogosResponse
import com.example.pmmovil.data.model.CasetaEntradaFrequentItem
import com.example.pmmovil.data.model.CasetaEntradaGuardarRequest
import com.example.pmmovil.data.model.CasetaEntradaGuardarResponse
import com.example.pmmovil.data.model.CasetaEntradaFotosRequest
import com.example.pmmovil.data.model.CasetaEntradaFotosResponse
import com.example.pmmovil.data.model.CasetaEntradaObtenerResponse
import com.example.pmmovil.data.model.CasetaEntradaPlateOcrRequest
import com.example.pmmovil.data.model.CasetaEntradaPlateOcrResponse
import com.example.pmmovil.data.model.CasetaPreregistroEnCaminoItem
import com.example.pmmovil.data.model.CasetaPreregistroUsarRequest
import com.example.pmmovil.data.model.CasetaPreregistroUsarResponse
import com.example.pmmovil.data.model.CasetaPreregistroValidarResponse
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

@Singleton
class CasetaEntradaRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getCatalogs(): Result<CasetaEntradaCatalogosResponse> = io {
        val response = api.getCasetaEntradaCatalogos()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar los catalogos de caseta.")
        }
    }

    suspend fun searchCustomers(filter: String, limit: Int = 50): Result<List<CasetaEntradaCatalogItem>> = io {
        val response = api.searchCasetaEntradaClientes(filter.takeIf(String::isNotBlank), limit)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron buscar clientes.")
        }
    }

    suspend fun searchTransports(filter: String, limit: Int = 50): Result<List<CasetaEntradaCatalogItem>> = io {
        val response = api.searchCasetaEntradaTransportes(filter.takeIf(String::isNotBlank), limit)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron buscar lineas transportistas.")
        }
    }

    suspend fun getFrequentItems(
        type: String,
        limit: Int = 8,
        days: Int = 90
    ): Result<List<CasetaEntradaFrequentItem>> = io {
        val response = api.getCasetaEntradaFrecuentes(type, limit, days)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar frecuentes.")
        }
    }

    suspend fun getBoxTypes(): Result<List<CasetaEntradaCatalogItem>> = io {
        val response = api.getCasetaEntradaTiposCaja()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar los tipos de caja.")
        }
    }

    /**
     * `tipoMov`, `cliente` y `linea` son filtros exactos que se suman al
     * termino -- son los mismos chips del hub de caseta, para que buscar en el
     * historico respete lo que el usuario ya eligio en pantalla.
     */
    suspend fun searchRecords(
        term: String,
        type: String = "general",
        offset: Int = 0,
        limit: Int = 20,
        tipoMov: String? = null,
        cliente: String? = null,
        linea: String? = null
    ): Result<List<CasetaEntradaBusquedaItem>> = io {
        val response = api.searchCasetaEntradas(
            term = term.takeIf(String::isNotBlank),
            type = type.takeIf(String::isNotBlank),
            offset = offset,
            limit = limit,
            tipoMov = tipoMov?.takeIf(String::isNotBlank),
            cliente = cliente?.takeIf(String::isNotBlank),
            linea = linea?.takeIf(String::isNotBlank)
        )
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron buscar registros de caseta.")
        }
    }

    suspend fun getRecord(
        casetaId: String,
        photosBase64: Boolean = true
    ): Result<CasetaEntradaObtenerResponse> = io {
        val response = api.getCasetaEntrada(casetaId, photosBase64 = photosBase64)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se encontro el registro de caseta.")
        }
    }

    suspend fun downloadGatePhoto(casetaId: String, index: Int): Result<ByteArray> = io {
        val response = api.downloadEntradaGatePhoto(casetaId, index)
        if (response.isSuccessful) {
            response.body()?.bytes()
                ?: throw IllegalStateException("La foto de caseta esta vacia.")
        } else {
            throw apiFailure(response, null, "No se pudo cargar la foto de caseta.")
        }
    }

    suspend fun getConteosHoy(): Result<Map<String, Int>> = io {
        val response = api.getCasetaEntradaConteosHoy()
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.data != null) {
            mapOf(
                "entradas" to body.data.entradas,
                "salidas" to body.data.salidas
            )
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar los conteos del día.")
        }
    }

    suspend fun save(request: CasetaEntradaGuardarRequest): Result<CasetaEntradaGuardarResponse> = io {
        val response = if (request.actualizado && request.casetaid.isNotBlank()) {
            api.updateCasetaEntrada(request.casetaid, request)
        } else {
            api.saveCasetaEntrada(request)
        }
        val body = response.body()
        if (response.isSuccessful && body?.isSuccessful == true) {
            body
        } else {
            throw apiFailure(response, body?.displayMessage, "No se pudo guardar la entrada a caseta.")
        }
    }

    suspend fun uploadPhotos(
        casetaId: String,
        request: CasetaEntradaFotosRequest
    ): Result<CasetaEntradaFotosResponse> = io {
        val response = api.uploadCasetaEntradaPhotos(casetaId, request)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudieron subir las fotos de caseta.")
        }
    }

    suspend fun readPlate(base64Image: String): Result<CasetaEntradaPlateOcrResponse> = io {
        val response = api.readCasetaEntradaPlate(CasetaEntradaPlateOcrRequest(base64Image))
        val body = response.body()
        val hasUsefulSignal = body?.success == true ||
            body?.placa?.isNotBlank() == true ||
            body?.candidates?.any { it.value.isNotBlank() } == true
        if (response.isSuccessful && body != null && hasUsefulSignal) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudo leer la placa.")
        }
    }

    suspend fun readBox(base64Image: String): Result<CasetaEntradaBoxOcrResponse> = io {
        val response = api.readCasetaEntradaBox(CasetaEntradaBoxOcrRequest(base64Image))
        val body = response.body()
        val hasUsefulSignal = body?.success == true ||
            body?.numCaja?.isNotBlank() == true ||
            body?.candidates?.any { it.value.isNotBlank() } == true
        if (response.isSuccessful && body != null && hasUsefulSignal) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudo leer la caja.")
        }
    }

    // ── Pre-registro de camiones sin caja (escaneo en garita) ────────────

    /** No lanza por estatus "de negocio" (EXPIRADO/USADO/etc): esos vienen en el body con success=false. */
    suspend fun validatePreregistro(codigo: String): Result<CasetaPreregistroValidarResponse> = io {
        val response = api.validarPreregistro(codigo)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudo validar el codigo.")
        }
    }

    suspend fun usePreregistro(folio: String, casetaId: String, placasNoCoinciden: Boolean): Result<CasetaPreregistroUsarResponse> = io {
        val response = api.usarPreregistro(folio, CasetaPreregistroUsarRequest(casetaId, placasNoCoinciden))
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body
        } else {
            throw apiFailure(response, body?.message, "No se pudo marcar el pre-registro como usado.")
        }
    }

    suspend fun getEnCamino(idBodega: String? = null): Result<List<CasetaPreregistroEnCaminoItem>> = io {
        val response = api.getPreregistroEnCamino(idBodega?.takeIf(String::isNotBlank))
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body
        } else {
            throw apiFailure(response, null, "No se pudieron cargar los camiones en camino.")
        }
    }

    /**
     * Coordenadas de la bodega para centrar el mapa. `success = false` (bodega
     * sin coordenada configurada en GoogleRoutes:Bodegas) no es un error del
     * mapa -- se pinta igual sin el marcador de destino.
     */
    suspend fun getBodegaCoordenadas(idBodega: String): Result<BodegaCoordenadas> = io {
        val response = api.getBodegaCoordenadas(idBodega)
        val body = response.body()
        if (response.isSuccessful && body != null) {
            body
        } else {
            throw apiFailure(response, null, "No se pudo obtener la ubicación de la bodega.")
        }
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
