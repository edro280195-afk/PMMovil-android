package com.example.pmmovil.data.model

/**
 * El resumen del Tablero del inicio, tal como lo devuelve `GET Home/resumen`. Una estación llega `null` si el usuario no
 * tiene su módulo o si el servidor no pudo calcularla: el inicio nunca depende de que las cinco lleguen.
 *
 * Todos los campos tienen valor por omisión para que Gson, que construye estas clases sin pasar por el constructor, no
 * deje un campo nulo si el servidor agrega o quita uno.
 */
data class HomeSummaryDto(
    val success: Boolean = false,
    val caseta: HomeStationSummaryDto? = null,
    val entrada: HomeStationSummaryDto? = null,
    val revision: HomeStationSummaryDto? = null,
    val carga: HomeStationSummaryDto? = null,
    val relacion: HomeStationSummaryDto? = null,
    val salida: HomeStationSummaryDto? = null,
    /** Cuánto vale la respuesta del servidor antes de recalcularse. */
    val cacheSeconds: Int = 0
)

data class HomeStationSummaryDto(
    val count: Int = 0,
    /** El caso más fuerte de la estación; `null` si no hay ninguno. */
    val exception: HomeStationExceptionDto? = null
)

data class HomeStationExceptionDto(
    /** `ALTO`, `ATENCION`, `LISTO` o `AVISO`. */
    val kind: String = "",
    /** `OLD_IN_YARD`, `DISCREPANCIAS`, `BLOQUEADAS` o `LISTAS_PARA_SALIR`. */
    val code: String = "",
    val count: Int = 0
)
