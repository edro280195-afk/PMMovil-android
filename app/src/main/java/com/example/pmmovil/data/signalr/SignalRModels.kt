package com.example.pmmovil.data.signalr

import com.example.pmmovil.data.model.FacturaRevisionDto
import com.example.pmmovil.data.model.PartidaRevisionDto

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

data class RevisionEstadoMessage(
    val creferencia: String = "",
    val usuario: String = "",
    val estado: String = "",
    val fecha: String = "",
    val estatusFinal: Int = 0
)

/** Snapshot persistido que comparten el portal Angular y Android. */
data class RevisionActualizadaMessage(
    val entidad: String = "",
    val creferencia: String = "",
    val usuario: String = "",
    val fecha: String = "",
    val cnofact: String? = null,
    val cnofacts: List<String>? = null,
    val idRevdet: Int? = null,
    val factura: FacturaRevisionDto? = null,
    val partida: PartidaRevisionDto? = null,
    val observacionRevision: String? = null
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

data class PreregistroArriboMessage(
    val type: String = "preregistro-arribo",
    val folio: String = "",
    val idBodega: String = "",
    val fecha: String = ""
)

data class PreregistroUbicacionMessage(
    val type: String = "preregistro-ubicacion",
    val folio: String = "",
    val idBodega: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
    val fecha: String = "",
    val etaMinutos: Int? = null,
    val etaKm: Double? = null,
    val distanciaKm: Double? = null,
    val dentroGeocerca: Boolean = false,
    val arriboDetectado: Boolean = false
)

data class PreregistroAlertaMessage(
    val type: String = "preregistro-alerta",
    val tipo: String = "",
    val folio: String = ""
)
