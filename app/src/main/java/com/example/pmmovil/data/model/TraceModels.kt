package com.example.pmmovil.data.model

/**
 * La ficha de un código, tal como la devuelve `POST Trazabilidad/resolver`. El servidor da códigos de estado y datos
 * crudos, nunca frases: el texto que ve el usuario sale de los recursos de la app, en su idioma.
 *
 * Todos los campos tienen valor por omisión para que Gson, que construye estas clases sin pasar por el constructor,
 * no deje un campo nulo si el servidor agrega o quita uno.
 */
data class TraceRequest(
    /** El código leído. El folio de caja lleva `/` y el pre-registro un token: por eso va en el cuerpo, no en la ruta. */
    val code: String
)

data class TraceStationDto(
    /** `CASETA`, `ENTRADA`, `REVISION`, `CARGA`, `RELACION` o `SALIDA`. */
    val id: String = "",
    /** `HECHA`, `EN_CURSO`, `PENDIENTE`, `ATENCION`, `SIN_DATO` o `NO_APLICA`. */
    val state: String = "SIN_DATO",
    val at: String? = null
)

data class TraceCajaDto(
    val casetaId: String = "",
    val numCaja: String = "",
    val cliente: String = "",
    val linea: String = "",
    val planta: String = "",
    val tipoOp: String = "",
    val estatus: String = "",
    val relacion: String = "",
    val arrivalAt: String? = null,
    val departureAt: String? = null
)

data class TraceEntradaDto(
    val reference: String = "",
    val cliente: String = "",
    val arrivalAt: String? = null,
    /** De dónde salió: `DIRECTA`, `RELACION` o `CODIGO`. */
    val via: String = "CODIGO",
    /** `SIN_REVISAR`, `EN_PROGRESO`, `COMPLETA` o `COMPLETA_CON_DISCREPANCIAS`. */
    val reviewStatus: String = "SIN_REVISAR",
    val totalInvoices: Int = 0,
    val completedInvoices: Int = 0,
    val totalLines: Int = 0,
    val reviewedLines: Int = 0,
    val hasHazmat: Boolean = false,
    val hasDamage: Boolean = false
)

data class TraceRelacionDto(
    val relno: String = "",
    val estatusCarga: String = "",
    val caja: String = "",
    val transfer: String = "",
    val authorizedAt: String? = null,
    val departureAt: String? = null,
    val totalEntradas: Int = 0,
    /** Cuándo se empezó a cargar; `null` si todavía no. */
    val loadStartedAt: String? = null,
    /** Cuándo se terminó de cargar; `null` si sigue pendiente o en proceso. */
    val loadFinishedAt: String? = null,
    /** Quién tiene tomada la carga; vacío si nadie. */
    val loadedBy: String = "",
    /** La relación está marcada para omitir el proceso de carga. */
    val loadNotRequired: Boolean = false
)

data class TraceBultoDto(
    val ordinal: Int = 0,
    val total: Int = 0,
    /** La etiqueta existe en la base con esa posición exacta. */
    val registered: Boolean = false,
    val labelStatus: String = "",
    val relacion: String = ""
)

data class TracePreregistroDto(
    val folio: String = "",
    val status: String = "",
    val linea: String = "",
    val unidad: String = "",
    val expiresAt: String? = null,
    val usedAt: String? = null,
    val casetaId: String = ""
)

/** Valores de [TraceResultDto.kind]. */
object TraceKind {
    const val CAJA = "CAJA"
    const val BULTO = "BULTO"
    const val CARPETA = "CARPETA"
    const val RELACION = "RELACION"
    const val PREREGISTRO = "PREREGISTRO"
}

data class TraceResultDto(
    val success: Boolean = false,
    /** `CAJA`, `BULTO`, `CARPETA`, `RELACION` o `PREREGISTRO`; vacío si el servidor no reconoció el código. */
    val kind: String = "",
    val code: String = "",
    val found: Boolean = false,
    /** Por qué no hay ficha (`NO_ENCONTRADO`, `CODIGO_NO_SOPORTADO`, `GAFETE_NO_SE_CONSULTA`…). */
    val reason: String? = null,
    /** `ALTO`, `ATENCION`, `LISTO`, `AVISO`, `CERRADO` o `SIN_DATO`. */
    val signal: String = "SIN_DATO",
    val stations: List<TraceStationDto> = emptyList(),
    val flags: List<String> = emptyList(),
    /** Acciones que el estado permite, con los nombres de [com.example.pmmovil.data.scanner.ScanActionId]; la primera es la sugerida. */
    val actions: List<String> = emptyList(),
    val caja: TraceCajaDto? = null,
    val entradas: List<TraceEntradaDto> = emptyList(),
    val relacion: TraceRelacionDto? = null,
    val bulto: TraceBultoDto? = null,
    val preregistro: TracePreregistroDto? = null
)
