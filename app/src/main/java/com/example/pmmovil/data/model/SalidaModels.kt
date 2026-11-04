package com.example.pmmovil.data.model

/**
 * Modelos de Salida de Bodega.
 *
 * Los nombres de campo coinciden en camelCase con el JSON que devuelve
 * `SalidaController` del API .NET (mismo contrato que consume el Angular
 * `salida.service.ts`). Gson mapea por nombre, así que NO se necesita
 * @SerializedName mientras los nombres coincidan.
 */

data class SalidaEtiqueta(
    val id: Int = 0,
    val textoEtiqueta: String = "",
    val textoPagina: String = "",
    val creferencia: String = "",
    val nlineno: Int = 0,
    val total: Int = 0,
    val escaneado: Boolean = false,
    val estatus: String = "",
    val usucarga: String? = null,
    val timeScan: String? = null,
    val manejo: String? = null,
    val historial: String? = null,
    /** La etiqueta es una reimpresión: al escanearla la relación se bloquea hasta que un supervisor la libere. */
    val reimpresa: Boolean = false
)

data class SalidaRelacion(
    val relno: String = "",
    val estatus: String = "",
    val estatusCarga: String = "",
    val manejo: String = "",
    val cliente: String = "",
    val idCliente: String = "",
    val cidTransfer: String = "",
    val opTransfer: String = "",
    val caja: String = "",
    val usucargaMcia: String = "",
    val usuAutoriza: String = "",
    val sellos: String = "",
    val numTractor: String = "",
    val gafeteOp: String = "",
    val planta: String = "",
    val versionHoja: String = ""
)

data class CargarRelacionResponse(
    val success: Boolean = false,
    val message: String? = null,
    val relacion: SalidaRelacion? = null,
    val etiquetas: List<SalidaEtiqueta>? = null,
    val estatusCarga: String? = null,
    val totBultos: Int = 0,
    val versionHoja: String? = null
)

data class EtiquetasDescargaResponse(
    val success: Boolean = false,
    val message: String? = null,
    val etiquetas: List<SalidaEtiqueta>? = null
)

/** Resultado de escaneo (carga y descarga). `tipo` ∈ NO_PERTENECE / BLOQUEADO / OK… */
data class SalidaScanResponse(
    val success: Boolean = false,
    val message: String? = null,
    val tipo: String? = null,
    val relno: String? = null,
    val entradaCref: String? = null,
    val estatusCarga: String? = null,
    val bloqueoMotivo: String? = null,
    val etiquetasBloqueadas: Int? = null,
    val requiereCasetaQr: Boolean = true
)

data class SalidaCasetaQrResponse(
    val success: Boolean = false,
    val message: String? = null,
    val tipo: String? = null,
    val relno: String? = null,
    val casetaId: String? = null,
    val autorizacionToken: String? = null,
    val expiraEnSegundos: Int = 0,
    val relacion: SalidaRelacion? = null,
    val etiquetas: List<SalidaEtiqueta>? = null,
    val estatusCarga: String? = null,
    val totBultos: Int = 0,
    val versionHoja: String? = null
)

data class SalidaOperacionResponse(
    val success: Boolean = false,
    val message: String? = null
)

// ── Requests ──────────────────────────────────────────────────────────────────
// El backend completa Usuario/IpAddress en el servidor; el cliente solo envía estos campos.

data class SalidaScanRequest(
    val relno: String,
    val textoEtiqueta: String,
    val autorizacionToken: String
)

data class SalidaCasetaQrRequest(
    val relno: String = "",
    val entrada: String = "",
    val casetaId: String
)

data class SalidaDescargarRequest(
    val relno: String,
    val textoEtiqueta: String
)

data class SalidaSinRelacionScanRequest(
    val entrada: String,
    val textoEtiqueta: String,
    val autorizacionToken: String
)
