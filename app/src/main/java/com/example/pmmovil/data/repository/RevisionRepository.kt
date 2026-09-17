package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RevisionRepository @Inject constructor(private val api: PMMovilApi) {

    data class PendingEntriesPage(
        val items: List<EntradaPendienteDto>,
        val truncated: Boolean
    )

    // ── Entradas ──────────────────────────────────────────────────────────────

    suspend fun getEntradasPendientes(filtro: String = "") = withContext(Dispatchers.IO) {
        getEntradasPendientesPage(filtro).map { it.items }
    }

    suspend fun getEntradasPendientesPage(filtro: String = "") = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getEntradasPendientes(filtro.ifBlank { null })
            val body = r.body()
            if (r.isSuccessful && body?.success == true) {
                PendingEntriesPage(
                    items = body.items ?: emptyList(),
                    truncated = body.truncado == true
                )
            } else {
                throw Exception(errorMessage(r, "No se pudieron obtener las entradas pendientes."))
            }
        }
    }

    suspend fun getMisActivas() = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getMisActivas()
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body.items ?: emptyList()
            else throw Exception(errorMessage(r, "No se pudieron obtener tus revisiones."))
        }
    }

    suspend fun registrarTomaDocumento(creferencia: String) = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.registrarTomaDocumento(creferencia)
            if (!response.isSuccessful) {
                throw Exception(errorMessage(response, "No se pudo registrar la toma del documento."))
            }
        }
    }

    /**
     * Búsqueda exacta por creferencia. Se usa cuando el usuario escanea
     * un QR / código (hardware, bluetooth o cámara): si la entrada existe
     * en BD, devolvemos su info para abrir el modal de selección de modo.
     */
    suspend fun buscarPorCreferencia(crefAgrupada: String): Result<com.example.pmmovil.data.model.EntradaPendienteDto?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.buscarPorCreferencia(crefAgrupada)
                val body = r.body()
                if (r.isSuccessful && body != null && body.success) {
                    body.item
                } else if (r.code() == 404) {
                    null
                } else {
                    throw Exception(errorMessage(r, body?.message ?: "Error al buscar la entrada."))
                }
            }
        }

    /** Genera el link público de "Seguimiento de revisión" -- página separada de la de Entrada. */
    suspend fun compartir(crefAgrupada: String): Result<EntradaShareLinkResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.compartirRevision(crefAgrupada)
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw Exception(errorMessage(r, body?.message ?: "No se pudo generar el enlace."))
        }
    }

    suspend fun solicitarDesbloqueo(cnofact: String, motivo: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.solicitarDesbloqueo(SolicitarDesbloqueoRequest(cnofact, motivo))
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.id ?: 0
            else throw Exception(errorMessage(r, "Error al solicitar desbloqueo."))
        }
    }

    suspend fun verificarDesbloqueo(cnofact: String): Result<VerificarDesbloqueoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.verificarDesbloqueo(cnofact)
            r.body() ?: throw Exception("HTTP ${r.code()}")
        }
    }

    // ── Facturas ──────────────────────────────────────────────────────────────

    suspend fun getFacturas(crefAgrupada: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getFacturasEntrada(crefAgrupada)
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.items ?: emptyList()
            else throw Exception("Error al obtener facturas")
        }
    }

    suspend fun getEntradaCompleta(crefAgrupada: String): Result<EntradaCompletaRevisionResponse> =
        withContext(Dispatchers.IO) {
            runCatching {
                val r = api.getEntradaCompletaRevision(crefAgrupada)
                val body = r.body()
                if (r.isSuccessful && body?.success == true) {
                    body
                } else {
                    throw Exception(errorMessage(r, body?.message ?: "Error al obtener la entrada completa."))
                }
            }
        }

    suspend fun getRevisionFactura(cnofact: String): Result<FacturaRevisionDto> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getRevisionFactura(cnofact)
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.data!!
            else throw Exception("HTTP ${r.code()}")
        }
    }

    suspend fun guardarCampoFactura(cnofact: String, campo: String, valor: Any) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.guardarCampoFactura(GuardarCampoFacturaRequest(cnofact, campo, valor))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo guardar el campo '$campo'."))
        }
    }

    suspend fun guardarObservacionEntrada(crefAgrupada: String, valor: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.guardarObservacionEntrada(GuardarObservacionEntradaRequest(crefAgrupada, valor))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo guardar la observación general."))
        }
    }

    suspend fun limpiarRevisor(cnofact: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.limpiarRevisor(CnofactRequest(cnofact))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo limpiar el revisor."))
        }
    }

    suspend fun aclararDiscrepancias(cnofact: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.aclararDiscrepancias(CnofactRequest(cnofact))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudieron aclarar las discrepancias."))
        }
    }

    suspend fun registrarEditor(cnofact: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.registrarEditor(CnofactRequest(cnofact))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo registrar el editor."))
        }
    }

    suspend fun limpiarEditor(cnofact: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.limpiarEditor(CnofactRequest(cnofact))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo limpiar el editor."))
        }
    }

    suspend fun limpiarEditoresEntrada(cref: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.limpiarEditoresEntrada(cref)
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudieron limpiar los editores de la entrada."))
        }
    }

    suspend fun validarCierre(creferencia: String): Result<ValidarCierreResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.validarCierre(creferencia)
            r.body() ?: throw Exception("HTTP ${r.code()}")
        }
    }

    // ── Partidas ──────────────────────────────────────────────────────────────

    suspend fun getPartidas(crefAgrupada: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getPartidasEntrada(crefAgrupada)
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.items ?: emptyList()
            else throw Exception("Error al obtener partidas")
        }
    }

    suspend fun guardarCampoPartida(id: Int, campo: String, valor: Any) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.guardarCampoPartida(GuardarCampoPartidaRequest(id, campo, valor))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo guardar el campo '$campo'."))
        }
    }

    suspend fun agregarPartida(cnofact: String, cref: String): Result<AgregarPartidaResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.agregarPartida(AgregarPartidaRequest(cnofact, cref))
            if (r.isSuccessful && r.body()?.success == true) r.body()!!
            else throw Exception("HTTP ${r.code()}")
        }
    }

    suspend fun eliminarPartida(id: Int) = withContext(Dispatchers.IO) {
        runCatching { api.eliminarPartida(id) }
    }

    // ── Catálogos / HazMat / Fotos ────────────────────────────────────────────

    suspend fun getPaises() = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getPaises()
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body
            else throw Exception(
                body?.message?.takeIf { it.isNotBlank() }
                    ?: errorMessage(r, "Error al cargar países")
            )
        }
    }

    suspend fun getPaisesFrecuentes() = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getPaisesFrecuentes()
            val body = r.body()
            if (r.isSuccessful && body?.success == true) body.items ?: emptyList()
            else throw Exception(
                body?.message?.takeIf { it.isNotBlank() }
                    ?: errorMessage(r, "Error al cargar países más usados")
            )
        }
    }

    suspend fun validarHazmat(cnofact: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.validarHazmat(cnofact)
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.problemas ?: emptyList()
            else throw Exception("Error al validar HazMat")
        }
    }

    suspend fun getFotos(creference: String) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getFotos(creference)
            if (r.isSuccessful && r.body()?.success == true) r.body()!!.items ?: emptyList()
            else throw Exception("Error al cargar fotos")
        }
    }

    suspend fun downloadFoto(id: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.downloadFoto(id)
            if (r.isSuccessful) r.body()?.bytes() ?: throw Exception("Cuerpo vacío")
            else throw Exception("Error al descargar foto")
        }
    }

    suspend fun subirFoto(
        cref: String,
        cclienteid: String,
        base64Image: String,
        extension: String,
        esDiscrepancia: Boolean = false
    ): Result<SubirFotoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.subirFoto(SubirFotoRequest(cref, cclienteid, base64Image, extension, esDiscrepancia))
            if (r.isSuccessful && r.body()?.success == true) r.body()!!
            else throw Exception(r.body()?.message ?: "Error al subir foto")
        }
    }

    suspend fun eliminarFoto(id: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.eliminarFoto(id)
            when {
                r.isSuccessful && r.body()?.success == true -> r.body()!!
                // 404 = la foto ya no existía en el servidor. Para quien pidió
                // borrarla el resultado es el mismo (ya no está), así que no
                // debe bloquear la limpieza del lado del dispositivo -- eso
                // era justo lo que dejaba "basura" local sin forma de quitarla.
                r.code() == 404 -> SimpleSuccessResponse(
                    success = true,
                    message = "La foto ya no existía en el servidor"
                )
                else -> throw Exception(r.body()?.message ?: "Error al eliminar foto")
            }
        }
    }

    // ── Timer ─────────────────────────────────────────────────────────────────

    suspend fun getEstadoTimer(creferencia: String): Result<TimerEstadoResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.getEstadoTimer(creferencia)
            r.body() ?: throw Exception("HTTP ${r.code()}")
        }
    }

    suspend fun iniciarTimer(creferencia: String): Result<TimerIniciarResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.iniciarTimer(creferencia)
            r.body() ?: throw Exception("HTTP ${r.code()}")
        }
    }

    suspend fun finalizarTimer(creferencia: String, statusFinal: Int, esFinal: Boolean) = withContext(Dispatchers.IO) {
        runCatching {
            val r = api.finalizarTimer(TimerFinalizarRequest(creferencia, statusFinal, esFinal))
            if (r.isSuccessful && r.body()?.success == true) Unit
            else throw Exception(errorMessage(r, "No se pudo finalizar el temporizador de revisión."))
        }
    }

    private fun <T> errorMessage(response: Response<T>, fallback: String): String {
        val raw = runCatching { response.errorBody()?.string() }.getOrNull()
        val apiMessage = raw?.let {
            runCatching {
                JsonParser.parseString(it)
                    .asJsonObject
                    .get("message")
                    ?.asString
            }.getOrNull()
        }
        return apiMessage?.takeIf { it.isNotBlank() } ?: when (response.code()) {
            401 -> "Tu sesión expiró. Inicia sesión nuevamente."
            404 -> "No se encontró la información solicitada."
            else -> "HTTP ${response.code()}: $fallback"
        }
    }
}
