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

data class ValidarCierreResponse(
    val success: Boolean,
    val facturasSinRevisor: List<String>?
)

data class GuardarCampoFacturaRequest(
    val cnofact: String,
    val campo: String,
    val valor: Any
)

data class GuardarCampoPartidaRequest(
    val id: Int,
    val campo: String,
    val valor: Any
)

data class CnofactRequest(val cnofact: String)

data class AgregarPartidaRequest(val cnofact: String, val cref: String)

data class AgregarPartidaResponse(val success: Boolean, val id: Int)

data class SimpleSuccessResponse(val success: Boolean, val message: String?)

data class FacturaSingleResponse(val success: Boolean, val data: FacturaRevisionDto?)

data class ServerTimeResponse(val success: Boolean, val serverTime: String?)
