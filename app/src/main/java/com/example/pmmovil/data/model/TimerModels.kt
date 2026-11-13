package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class TimerEstadoResponse(
    val success: Boolean,
    val activo: Boolean,
    val inicioSesionActual: String?,
    val segundosAcumulados: Int,
    val finRevision: Boolean,
    val usuarioAsignado: String?
)

data class TimerIniciarResponse(
    val success: Boolean,
    val inicioSesionActual: String?,
    val segundosAcumulados: Int
)

data class TimerFinalizarRequest(
    val creferencia: String,
    val statusFinal: Int,
    val esFinal: Boolean
)

/**
 * Respuesta de «Terminar». La sesión se cierra siempre, pero la entrada solo queda marcada como revisada físicamente
 * cuando está completa; [revisionCompleta] dice cuál de las dos pasó. Los campos son opcionales para convivir con un
 * API anterior que solo respondía `success` (en ese caso se asume que quedó completa, como antes).
 */
data class TimerFinalizarResponse(
    val success: Boolean,
    val message: String? = null,
    val estatusFinal: Int? = null,
    val revisionCompleta: Boolean? = null,
    val facturasIncompletas: Int? = null,
    val facturasConPartidasPendientes: Int? = null
)

data class ValidarCierreResponse(
    val success: Boolean,
    val facturasSinRevisor: List<String>?
)

data class GuardarCampoFacturaRequest(
    val cnofact: String,
    val campo: String,
    val valor: Any,
    // El número de factura se repite entre entradas: con la entrada, el API sabe de cuál factura se trata y no
    // modifica las que comparten el número en otras entradas.
    val crefAgrupada: String? = null
)

data class GuardarCampoPartidaRequest(
    val id: Int,
    val campo: String,
    val valor: Any
)

data class GuardarCantidadComercialRequest(
    val id: Int,
    val cantidad: Double,
    val unidad: String
)

data class CnofactRequest(val cnofact: String, val crefAgrupada: String? = null)

data class AgregarPartidaRequest(val cnofact: String, val cref: String)

data class AgregarPartidaResponse(val success: Boolean, val id: Int)

data class SimpleSuccessResponse(val success: Boolean, val message: String?)

data class FacturaSingleResponse(val success: Boolean, val data: FacturaRevisionDto?)

data class ServerTimeResponse(val success: Boolean, val serverTime: String?)
