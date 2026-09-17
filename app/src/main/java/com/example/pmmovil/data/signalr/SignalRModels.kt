package com.example.pmmovil.data.signalr

data class ActividadMessage(
    val creferencia: String,
    val usuario: String,
    val cnofact: String?,
    val item: String?
)

data class DesbloqueoMessage(
    val cnofact: String,
    val supervisor: String
)

data class AsignacionMessage(
    val idAsignacion: Int,
    val crefAgrupada: String,
    val supervisor: String,
    val prioridad: Int,
    val nota: String?
)

data class DesasignacionMessage(
    val idAsignacion: Int,
    val crefAgrupada: String,
    val supervisor: String
)

data class CompletadaMessage(
    val creferencia: String,
    val usuario: String
)

data class SupervisorObservandoMessage(
    val creferencia: String,
    val supervisor: String
)

data class CierreForzosoMessage(
    // La versión anterior del evento incluía la referencia. El monitor
    // actual cierra por usuario y envía esta información para que el app
    // pueda explicar claramente qué sucedió en la tableta.
    val creferencia: String? = null,
    val usuario: String? = null,
    val supervisor: String? = null,
    val motivo: String? = null,
    val mensaje: String? = null
)

data class PreregistroCreadoMessage(
    val type: String = "preregistro-creado",
    val folio: String = "",
    val nunidad: String = "",
    val placas: String = "",
    val operador: String = "",
    val nombreLinea: String = "",
    val idBodega: String = "",
    val fechaCrea: String? = null,
    val fechaExpira: String? = null,
    val etaMinutos: Int? = null,
    val etaKm: Double? = null
)
