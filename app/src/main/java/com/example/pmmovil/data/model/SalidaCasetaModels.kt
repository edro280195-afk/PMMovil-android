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

// ── Pantalla unificada de salida de caseta ───────────────────────────────────
// El servidor clasifica cada caja del patio en un carril; la app solo lo pinta.

enum class SalidaCarril(val clave: String) {
    /** Relación AUTORIZADA, libre y sin cruce: sale por confirmación de relación. */
    LISTA("LISTA"),

    /** Sin relación: salida manual. */
    MANUAL("MANUAL"),

    /** Relación en espera/cargando/cargada/bloqueada: no puede salir todavía. */
    PROCESO("PROCESO"),

    /** La relación ya salió pero la caja sigue en el patio: solo se cierra el registro. */
    CERRAR("CERRAR");

    companion object {
        fun desde(valor: String?): SalidaCarril =
            entries.firstOrNull { it.clave.equals(valor, ignoreCase = true) } ?: PROCESO
    }
}

data class SalidaPatioCaja(
    val casetaId: String = "",
    val numCaja: String = "",
    val lineaCaja: String = "",
    val numCamion: String = "",
    val placas: String = "",
    val tipoUnidad: String = "",
    val estatusCaseta: String = "",
    /** Índices de las fotos de llegada (fotos.nlineno, tipo 106) para Foto/caseta/ver. */
    val fotosEntrada: List<Int> = emptyList()
)

data class SalidaPatioItem(
    /** relno en relaciones; casetaId en salidas manuales. */
    val id: String = "",
    val carril: String = "",
    val relno: String = "",
    val estatusCarga: String = "",
    val motivo: String = "",
    val cajas: List<SalidaPatioCaja> = emptyList(),
    val clientes: List<String> = emptyList(),
    val tipoOp: String = "",
    val tipoMovimiento: String = "",
    val esTractocamion: Boolean = false,
    val planta: String = "",
    val fechaArribo: String? = null,
    val diasEnPatio: Int = 0,
    val horasEnPatio: Int = 0,
    val transfer: String = "",
    val operador: String = "",
    val tractor: String = "",
    val gafete: String = "",
    val sellos: String = "",
    val cajaRelacion: String = "",
    val usuAutoriza: String = "",
    val usuCarga: String = "",
    val usuEdit: String = "",
    val autorizaCarga: String? = null,
    val minutosEspera: Int? = null,
    val inicioCarga: String? = null,
    val minutosCargando: Int? = null,
    val salidaCaseta: String? = null,
    val bultosCargados: Int = 0,
    val bultosTotal: Int = 0
) {
    val carrilEnum: SalidaCarril get() = SalidaCarril.desde(carril)
}

data class SalidaPatioHoy(
    val conRelacion: Int = 0,
    val manuales: Int = 0
)

data class SalidaPatioResponse(
    val success: Boolean = false,
    val message: String? = null,
    val items: List<SalidaPatioItem> = emptyList(),
    val hoy: SalidaPatioHoy = SalidaPatioHoy()
)

data class CerrarRegistroRequest(
    val relno: String = ""
)
