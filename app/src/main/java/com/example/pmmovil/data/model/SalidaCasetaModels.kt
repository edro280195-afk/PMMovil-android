package com.example.pmmovil.data.model

// Salida de caja de CASETA (legacy Salidas.aspx). Se da salida fisica a una caja
// en patio por su casetaid. Distinta de la salida de bodega por relacion.

data class SalidaCajaItem(
    val casetaId: String = "",
    val numCaja: String = "",
    val lineaCaja: String = "",
    val numCamion: String = "",
    val placas: String = "",
    val nombreCliente: String = "",
    val tipoUnidad: String = "",
    val tipoMovimiento: String = "",
    val tipoOp: String = "",
    val planta: String = "",
    val fechaArribo: String? = null,
    val diasEnPatio: Int = 0
)

data class SalidaCajaPendientesResponse(
    val success: Boolean = false,
    val pendientes: List<SalidaCajaItem> = emptyList(),
    val message: String? = null
)

data class RevisarRelacionResponse(
    val success: Boolean = false,
    val relacion: String = "",
    val message: String? = null
)

data class RegistrarSalidaCajaRequest(
    val casetaId: String = "",
    val observaciones: String = ""
)

data class RegistrarSalidaCajaResponse(
    val success: Boolean = false,
    val message: String? = null
)
