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
    val fotosBase64: List<String>? = null,
    /**
     * Cuántas fotos ya se subieron por lotes ([SubirFotosSalidaCasetaRequest]). Con valor, [fotosBase64] va vacío y el
     * servidor solo comprueba que estén todas. Nulo = envío único de siempre (servidor sin el endpoint de lotes).
     */
    val totalFotos: Int? = null
)

/** Un lote de fotos de la salida: pocas por envío para que una red lenta no deje la salida colgada. */
data class SubirFotosSalidaCasetaRequest(
    /** Posición (desde 1) de la primera foto del lote dentro de las fotos de la salida. */
    val indiceInicial: Int,
    val fotosBase64: List<String>
)

data class ConfirmarSalidaCasetaResponse(
    val success: Boolean = false,
    val message: String? = null
)
