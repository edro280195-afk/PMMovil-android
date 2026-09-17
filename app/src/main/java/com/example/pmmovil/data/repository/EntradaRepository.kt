package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.CasetaScanResponse
import com.example.pmmovil.data.model.EntradaCatalogs
import com.example.pmmovil.data.model.EntradaConsecutiveResponse
import com.example.pmmovil.data.model.EntradaEntityResponse
import com.example.pmmovil.data.model.EntradaFrequentItem
import com.example.pmmovil.data.model.EntradaLookupItem
import com.example.pmmovil.data.model.EntradaOperationResponse
import com.example.pmmovil.data.model.EntradaPhotoInfo
import com.example.pmmovil.data.model.EntradaPhotosRequest
import com.example.pmmovil.data.model.EntradaReferenceResponse
import com.example.pmmovil.data.model.EntradaSaveRequest
import com.example.pmmovil.data.model.EntradaShareLinkResponse
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

class EntradaApiException(val statusCode: Int, message: String) : Exception(message)

@Singleton
class EntradaRepository @Inject constructor(private val api: PMMovilApi) {

    suspend fun getCatalogs(): Result<EntradaCatalogs> = io {
        val response = api.getEntradaCatalogs()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.catalogos
        else throw failure(response, body?.message, "No se pudieron cargar los catálogos.")
    }

    suspend fun getConsecutive(): Result<EntradaConsecutiveResponse> = io {
        val response = api.getEntradaConsecutive()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudo generar la entrada.")
    }

    suspend fun getReference(reference: String): Result<EntradaReferenceResponse> = io {
        val response = api.getEntradaReference(reference)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "Referencia no encontrada.")
    }

    suspend fun compartir(cref: String): Result<EntradaShareLinkResponse> = io {
        val response = api.compartirEntrada(cref)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudo generar el enlace.")
    }

    suspend fun scanGate(code: String): Result<CasetaScanResponse> = io {
        val response = api.getEntradaGate(code)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se encontró el registro en caseta.")
    }

    suspend fun getAvailableBoxes(): Result<List<EntradaLookupItem>> = io {
        val response = api.getEntradaAvailableBoxes()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron cargar las cajas.")
    }

    suspend fun searchCustomers(filter: String?): Result<List<EntradaLookupItem>> = io {
        val response = api.searchEntradaCustomers(filter)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron buscar clientes.")
    }

    suspend fun searchTransports(filter: String?): Result<List<EntradaLookupItem>> = io {
        val response = api.searchEntradaTransports(filter)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron buscar transportes.")
    }

    suspend fun findCustomer(code: String): Result<EntradaEntityResponse> = io {
        val response = api.getEntradaCustomer(code)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, null, "Cliente no encontrado.")
    }

    suspend fun findTransport(code: String): Result<EntradaEntityResponse> = io {
        val response = api.getEntradaTransport(code)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, null, "Transporte no encontrado.")
    }

    suspend fun save(request: EntradaSaveRequest): Result<EntradaOperationResponse> = io {
        val response = api.saveEntrada(request)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudo guardar la recepción.")
    }

    suspend fun update(cref: String, request: EntradaSaveRequest): Result<EntradaOperationResponse> = io {
        val response = api.updateEntrada(cref, request)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudo actualizar la recepción.")
    }

    suspend fun savePhotos(
        reference: String,
        photos: List<String>
    ): Result<EntradaOperationResponse> = io {
        val response = api.saveEntradaPhotos(EntradaPhotosRequest(reference, photos))
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudieron guardar las fotos.")
    }

    suspend fun getPhotos(reference: String): Result<List<EntradaPhotoInfo>> = io {
        val response = api.getEntradaPhotos(reference)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron cargar las fotos.")
    }

    suspend fun deletePhoto(id: Int): Result<Unit> = io {
        val response = api.deleteEntradaPhoto(id)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) Unit
        else throw failure(response, body?.message, "No se pudo eliminar la foto.")
    }

    suspend fun downloadPhoto(reference: String, line: Int): Result<ByteArray> = io {
        val response = api.downloadEntradaPhoto(reference, line)
        if (response.isSuccessful) {
            response.body()?.bytes()
                ?: throw EntradaApiException(response.code(), "La foto está vacía.")
        } else {
            throw failure(response, null, "No se pudo abrir la foto.")
        }
    }

    suspend fun saveDamagePhotos(
        reference: String,
        photos: List<String>
    ): Result<EntradaOperationResponse> = io {
        val response = api.saveEntradaDamagePhotos(EntradaPhotosRequest(reference, photos))
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body
        else throw failure(response, body?.message, "No se pudieron guardar las fotos de daño.")
    }

    suspend fun getDamagePhotos(reference: String): Result<List<EntradaPhotoInfo>> = io {
        val response = api.getEntradaDamagePhotos(reference)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron cargar las fotos de daño.")
    }

    suspend fun downloadDamagePhoto(reference: String, line: Int): Result<ByteArray> = io {
        val response = api.downloadEntradaDamagePhoto(reference, line)
        if (response.isSuccessful) {
            response.body()?.bytes()
                ?: throw EntradaApiException(response.code(), "La foto está vacía.")
        } else {
            throw failure(response, null, "No se pudo abrir la foto de daño.")
        }
    }

    suspend fun getFrequentItems(
        tipo: String,
        limite: Int = 8,
        dias: Int = 90
    ): Result<List<EntradaFrequentItem>> = io {
        val response = api.getEntradaFrecuentes(tipo, limite, dias)
        val body = response.body()
        if (response.isSuccessful && body?.success == true) body.items
        else throw failure(response, body?.message, "No se pudieron cargar frecuentes.")
    }

    suspend fun downloadGatePhoto(casetaId: String, index: Int): Result<ByteArray> = io {
        val response = api.downloadEntradaGatePhoto(casetaId, index)
        if (response.isSuccessful) {
            response.body()?.bytes()
                ?: throw EntradaApiException(response.code(), "La foto está vacía.")
        } else {
            throw failure(response, null, "No se pudo abrir la foto de caseta.")
        }
    }

    private suspend fun <T> io(block: suspend () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching { block() } }

    private fun failure(
        response: Response<*>,
        bodyMessage: String?,
        fallback: String
    ): EntradaApiException {
        val message = bodyMessage?.takeIf(String::isNotBlank)
            ?: parseError(response)
            ?: fallback
        return EntradaApiException(response.code(), message)
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
