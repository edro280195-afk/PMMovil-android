package com.example.pmmovil.data.model

import com.google.gson.annotations.SerializedName

data class CasetaEntradaCatalogItem(
    @SerializedName(value = "id", alternate = ["Id", "value", "Value"])
    val id: String = "",
    @SerializedName(value = "nombre", alternate = ["Nombre", "text", "Text"])
    val nombre: String = ""
) {
    val label: String
        get() = listOf(id, nombre)
            .filter { it.isNotBlank() }
            .joinToString(" - ")
}

data class CasetaEntradaCatalogosResponse(
    val success: Boolean = false,
    val message: String? = null,
    val bodegas: List<CasetaEntradaCatalogItem> = emptyList(),
    val tiposCaja: List<CasetaEntradaCatalogItem> = emptyList(),
    val lineasCamion: List<CasetaEntradaCatalogItem> = emptyList(),
    val lineasCaja: List<CasetaEntradaCatalogItem> = emptyList(),
    val clientes: List<CasetaEntradaCatalogItem> = emptyList(),
    val paises: List<CasetaEntradaCatalogItem> = emptyList()
)

data class CasetaEntradaLookupResponse(
    val success: Boolean = false,
    val message: String? = null,
    val items: List<CasetaEntradaCatalogItem> = emptyList()
)

data class CasetaEntradaFrequentItem(
    val id: String = "",
    val nombre: String = "",
    val uso: Int = 0,
    val ultimoUso: String? = null
) {
    val catalogItem: CasetaEntradaCatalogItem
        get() = CasetaEntradaCatalogItem(id = id, nombre = nombre)
}

data class CasetaEntradaFrequentResponse(
    val success: Boolean = false,
    val message: String? = null,
    val items: List<CasetaEntradaFrequentItem> = emptyList()
)

data class CasetaEntradaPlateOcrRequest(
    val base64Image: String = ""
)

data class CasetaEntradaOcrCandidate(
    @SerializedName(value = "value", alternate = ["placa", "numCaja", "candidate"])
    val value: String = "",
    val confidence: Double? = null,
    val source: String = "",
    val reason: String? = null
)

data class CasetaEntradaPlateOcrResponse(
    val success: Boolean = false,
    @SerializedName(value = "placa", alternate = ["value"])
    val placa: String = "",
    val confidence: Double? = null,
    val message: String? = null,
    val source: String = "",
    val candidates: List<CasetaEntradaOcrCandidate> = emptyList()
)

data class CasetaEntradaBoxOcrRequest(
    val base64Image: String = ""
)

data class CasetaEntradaBoxOcrResponse(
    val success: Boolean = false,
    @SerializedName(value = "numCaja", alternate = ["value"])
    val numCaja: String = "",
    val confidence: Double? = null,
    val message: String? = null,
    val source: String = "",
    val candidates: List<CasetaEntradaOcrCandidate> = emptyList()
)

data class CasetaEntradaGuardarRequest(
    val usuario: String = "",
    val nombreUsuario: String = "",
    val planta: String = "",
    val idBodega: String = "",
    val bodega: String = "",
    val cliente: String = "",
    val idCliente: String = "",
    val nombreCliente: String = "",
    val operacion: String = "",
    val movimiento: String = "",
    val idTipoCaja: String = "",
    val tipoCaja: String = "",
    val descTipoCaja: String = "",
    val operador: String = "",
    val noCamion: String = "",
    val lineaCamion: String = "",
    val idLineaCamion: String = "",
    val nombreLineaCamion: String = "",
    val numCaja: String = "",
    val lineaCaja: String = "",
    val idLineaCaja: String = "",
    val nombreLineaCaja: String = "",
    val idPais: String = "",
    val pais: String = "",
    val placas: String = "",
    val sellos: String = "",
    val observaciones: String = "",
    val combustible: String = "",
    val temperaturaOculta: String = "",
    val pedimento: String = "",
    val guia: String = "",
    val fotos: List<String> = emptyList(),
    /** Cuando es true, el API guarda primero la entrada y el correo se envía al terminar la cola de fotos. */
    val diferirCorreo: Boolean = false,
    /**
     * Posiciones de las fotos nuevas que se subiran despues de guardar (con [diferirCorreo]). El servidor avisa solo
     * cuando llegan todas y, si la sincronizacion se queda a medias, manda el aviso por su cuenta a los pocos minutos.
     */
    val indicesFotosEsperadas: List<Int> = emptyList(),
    val requiereImpo: Boolean = false,
    val casetaid: String = "",
    val idcaseta: String = "",
    val actualizado: Boolean = false,
    // Dato de procedencia (vino de un QR de pre-registro escaneado en garita).
    // Ya NO decide que campos son obligatorios -- eso lo hace tipoRegistro.
    val origenPreRegistro: Boolean = false,
    // Identidad explicita del QR. El API la usa para cerrar el folio de forma
    // idempotente aun cuando el intento de cierre del cliente falle.
    val preregistroFolio: String = "",
    val placasNoCoinciden: Boolean = false,
    // "CAJA" (default) o "TRACTOCAMION". El backend exime Cliente, Tipo de Caja,
    // Linea de Caja y Numero de Caja cuando es "TRACTOCAMION" -- ver TipoRegistro.CAJA.
    val tipoRegistro: String = TipoRegistro.CAJA
)

data class CasetaEntradaFotoUpload(
    val indice: Int,
    val base64Image: String
)

data class CasetaEntradaFotosRequest(
    val fotos: List<CasetaEntradaFotoUpload> = emptyList(),
    val finalizar: Boolean = false,
    val actualizado: Boolean = false
)

data class CasetaEntradaFotosResponse(
    val success: Boolean = false,
    val message: String = "",
    val fotosGuardadas: Int = 0
)

/** Valores validos para [CasetaEntradaGuardarRequest.tipoRegistro] / [CasetaEntradaDetalle.tipoRegistro]. */
object TipoRegistro {
    const val CAJA = "CAJA"
    const val TRACTOCAMION = "TRACTOCAMION"
}

data class CasetaEntradaGuardarResponse(
    val success: Boolean = false,
    val exito: Boolean = success,
    val message: String = "",
    val mensaje: String = message,
    val casetaId: String = "",
    val casetaIds: List<String> = emptyList()
) {
    val isSuccessful: Boolean
        get() = success || exito

    val displayMessage: String
        get() = message.ifBlank { mensaje }
}

data class CasetaEntradaDetalle(
    val idCaseta: Int = 0,
    val casetaId: String = "",
    val idBodega: String = "",
    val bodega: String = "",
    val tipoOp: String = "",
    val tipoMov: String = "",
    val tipoRegistro: String = TipoRegistro.CAJA,
    val idUnidad: String = "",
    val tipouni: String = "",
    val numUnidad: String = "",
    val idTransporteTractor: String = "",
    val transpor: String = "",
    val lineaCamion: String = "",
    val nequipo: String = "",
    val idTransporteCaja: String = "",
    val transporte: String = "",
    val lineaCaja: String = "",
    val idPais: String = "",
    val pais: String = "",
    val placas: String = "",
    val sellos: String = "",
    val operador: String = "",
    val idCliente: String = "",
    val nombreCliente: String = "",
    val cliente: String = "",
    val observaciones: String = "",
    val temperatura: String = "",
    val pedimento: String = "",
    val guias: Int = 0,
    val combustible: String = "",
    val planta: String = "",
    val actualizado: Boolean = false
)

data class CasetaEntradaFotoInfo(
    val indice: Int = 0,
    val nombreArchivo: String = ""
)

data class CasetaEntradaObtenerResponse(
    val success: Boolean = false,
    val message: String? = null,
    val datos: CasetaEntradaDetalle? = null,
    val fotosBase64: List<String> = emptyList(),
    val fotos: List<CasetaEntradaFotoInfo> = emptyList()
)

data class CasetaEntradaBusquedaItem(
    val casetaid: String = "",
    val nequipo: String = "",
    val placas: String = "",
    val nunidad: String = "",
    val transporu: String = "",
    val transpore: String = "",
    val cliente: String = "",
    val idCliente: String = "",
    val tipoOp: String = "",
    val tipoMov: String = "",
    val tipoRegistro: String = TipoRegistro.CAJA,
    val farribo: String? = null,
    val fotos: Int = 0,
    val matchField: String = "",
    val matchScore: Int = 0,
    val matchReason: String = ""
)

data class CasetaEntradaBusquedaResponse(
    val success: Boolean = false,
    val message: String? = null,
    val items: List<CasetaEntradaBusquedaItem> = emptyList()
)

data class CasetaEntradaConteosData(
    val entradas: Int = 0,
    val salidas: Int = 0,
    val fechaReferencia: String? = null
)

data class CasetaEntradaConteosResponse(
    val success: Boolean = false,
    val message: String? = null,
    val data: CasetaEntradaConteosData? = null
)

// ── Pre-registro de camiones sin caja (escaneo en garita) ──────────────────

/** Estatus posibles: VIGENTE, USADO, EXPIRADO, CANCELADO, NO_ENCONTRADO. */
data class CasetaPreregistroValidarResponse(
    val success: Boolean = false,
    val estatus: String = "",
    val message: String = "",
    val folio: String = "",
    val nunidad: String = "",
    val placas: String = "",
    val operador: String = "",
    val idTransporu: String = "",
    val nombreLinea: String = "",
    val idBodega: String = "",
    val fechaExpira: String? = null,
    val fechaUso: String? = null,
    val usuarioUso: String? = null,
    val casetaId: String? = null
)

data class CasetaPreregistroUsarRequest(
    val casetaId: String,
    val placasNoCoinciden: Boolean = false
)

data class CasetaPreregistroUsarResponse(
    val success: Boolean = false,
    val message: String = ""
)

data class CasetaPreregistroEnCaminoItem(
    val folio: String = "",
    val nunidad: String = "",
    val placas: String = "",
    val nombreLinea: String = "",
    val operador: String = "",
    val idBodega: String = "",
    val etaMinutos: Int? = null,
    val etaKm: Double? = null,
    val fechaUbicacion: String? = null,
    val lat: Double? = null,
    val lon: Double? = null
) {
    /** true solo si hay ambas coordenadas -- un solo valor null no ubica nada en el mapa. */
    val tieneUbicacion: Boolean get() = lat != null && lon != null
}

/** Coordenadas configuradas de una bodega/planta, para centrar el mapa de "Camiones en camino". */
data class BodegaCoordenadas(
    val success: Boolean = false,
    val message: String = "",
    val lat: Double? = null,
    val lon: Double? = null
) {
    val tieneUbicacion: Boolean get() = success && lat != null && lon != null
}
