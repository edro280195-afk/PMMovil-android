package com.example.pmmovil.data.model

/**
 * La ficha en una línea de una caja o una relación, para las marcas del AR (`POST Trazabilidad/compactas`). Todos los
 * campos tienen valor por omisión para que Gson, que no pasa por el constructor, no deje uno nulo si el servidor cambia.
 */
data class TraceCompactRequest(
    /** De uno a seis códigos de caja o de relación. */
    val codes: List<String>
)

data class TraceCompactItemDto(
    /** Código normalizado, el mismo con el que se pidió. Vacío si el servidor no lo reconoció. */
    val code: String = "",
    /** `CAJA` o `RELACION`; vacío si no se reconoció. */
    val kind: String = "",
    val found: Boolean = false,
    val reason: String? = null,
    /** `ALTO`, `ATENCION`, `LISTO`, `AVISO`, `CERRADO` o `SIN_DATO`. */
    val signal: String = "SIN_DATO",
    /**
     * Estado de las cinco estaciones originales, en su orden (caseta, entrada, revisión, relación, salida). La carga viaja
     * aparte en [carga]: así un servidor anterior a «Carga» (que solo manda estas cinco) sigue entendiéndose.
     */
    val states: List<String> = emptyList(),
    /** Estado de «Carga»; `SIN_DATO` si el servidor no la manda. */
    val carga: String = "SIN_DATO",
    val flags: List<String> = emptyList(),
    val customer: String = "",
    val box: String = "",
    val relno: String = ""
)

data class TraceCompactResponseDto(
    val success: Boolean = false,
    val items: List<TraceCompactItemDto> = emptyList()
)
