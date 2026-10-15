package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.ZebraLabelLayoutDto
import com.example.pmmovil.data.model.ZebraLabelLayoutSaveRequest
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.model.ZebraPrinterSaveRequest
import com.google.gson.JsonParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response

@Singleton
class ZebraPrinterRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getPrinters(): Result<List<ZebraPrinterDto>> = io {
        val response = api.getZebraPrinters()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items.filter { it.isActive && it.host.isNotBlank() }
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las impresoras Zebra.")
        }
    }

    suspend fun getAllPrinters(): Result<List<ZebraPrinterDto>> = io {
        val response = api.getAllZebraPrinters()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudo cargar el catalogo Zebra.")
        }
    }

    suspend fun savePrinter(request: ZebraPrinterSaveRequest): Result<ZebraPrinterDto> = io {
        val response = if (request.id > 0) {
            api.updateZebraPrinter(request.id, request)
        } else {
            api.createZebraPrinter(request)
        }
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.item != null) {
            body.item
        } else {
            throw apiFailure(response, body?.message, "No se pudo guardar la impresora Zebra.")
        }
    }

    suspend fun getLabelLayouts(): Result<List<ZebraLabelLayoutDto>> = io {
        val response = api.getZebraLabelLayouts()
        val body = response.body()
        if (response.isSuccessful && body?.success == true) {
            body.items
        } else {
            throw apiFailure(response, body?.message, "No se pudieron cargar las plantillas de etiquetas.")
        }
    }

    suspend fun saveLabelLayout(type: String, layoutJson: String): Result<ZebraLabelLayoutDto> = io {
        val response = api.saveZebraLabelLayout(type, ZebraLabelLayoutSaveRequest(type, layoutJson))
        val body = response.body()
        if (response.isSuccessful && body?.success == true && body.item != null) {
            body.item
        } else {
            throw apiFailure(response, body?.message, "No se pudo guardar la plantilla de etiqueta.")
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
