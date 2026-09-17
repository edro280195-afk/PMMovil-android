package com.example.pmmovil.data.model

data class CasetaDetalle(
    val relno: String = "",
    val estatus: String = "",
    val estatusCarga: String = "",
    val transfer: String = "",
    val operador: String = "",
    val caja: String = "",
    val sellos: String = "",
    val tractor: String = "",
    val gafete: String = "",
    val cliente: String = "",
    val idCliente: String = ""
)

data class CasetaDetalleResponse(
    val success: Boolean = false,
    val message: String? = null,
    val detalle: CasetaDetalle? = null
)

data class ConfirmarSalidaCasetaRequest(
    val usuario: String,
    val observaciones: String = "",
    val fotosBase64: List<String>? = null
)

data class ConfirmarSalidaCasetaResponse(
    val success: Boolean = false,
    val message: String? = null
)
