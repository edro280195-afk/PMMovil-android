package com.example.pmmovil.data.model

data class RelacionPendiente(
    val relno: String = "",
    val cliente: String = "",
    val caja: String = "",
    val usucargaMcia: String = "",
    val estatusCarga: String = "",
    val totBultos: Int = 0,
    val etiquetasCount: Int = 0,
    val planta: String = "",
    val versionHoja: String = ""
)

data class RelacionPendientesResponse(
    val success: Boolean = false,
    val pendientes: List<RelacionPendiente> = emptyList(),
    val message: String? = null
)

data class CasetaPendiente(
    val relno: String = "",
    val caja: String = "",
    val usuAutoriza: String = "",
    val autorizaCarga: String? = null,
    val cliente: String = ""
)

data class CasetaPendientesResponse(
    val success: Boolean = false,
    val pendientes: List<CasetaPendiente> = emptyList(),
    val message: String? = null
)
