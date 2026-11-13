package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "asignaciones")
data class AsignacionEntity(
    @PrimaryKey val idAsignacion: Int,
    val crefAgrupada: String,
    val supervisor: String,
    val fechaAsignacion: String,
    val prioridad: Int,
    val nota: String?,
    val estatus: Int,
    val nombreCliente: String,
    val totalFacturas: Int,
    val facturasCompletas: Int,
    val requiresSync: Boolean = false
)

data class AsignacionResponse(
    val success: Boolean,
    val message: String?,
    val items: List<AsignacionDto>?
)

/**
 * Respuesta de `POST Asignaciones/tomar/{cref}` («Tomar carpeta»). `codigo` es estable y `mensaje` es para mostrar:
 * TOMADA, YA_ERA_TUYA, TOMADA_POR_OTRO (hay que confirmar), EN_REVISION_POR_OTRO, EN_REVISION_PROPIA, YA_REVISADA,
 * NO_EXISTE, OCUPADO o REFERENCIA_VACIA. Las respuestas que no son éxito también llegan con HTTP 200.
 */
data class TomarCarpetaResponse(
    val success: Boolean,
    val codigo: String = "",
    val mensaje: String? = null,
    val tomadaPor: String? = null,
    val desde: String? = null,
    val idAsignacion: Int? = null,
    val nombreCliente: String? = null
)

/** Respuesta de `POST Asignaciones/soltar/{cref}`. */
data class SoltarCarpetaResponse(
    val success: Boolean,
    val mensaje: String? = null
)

data class AsignacionDto(
    val idAsignacion: Int,
    val crefAgrupada: String,
    val supervisor: String,
    val fechaAsignacion: String,
    val prioridad: Int,
    val nota: String?,
    val estatus: Int,
    val nombreCliente: String?,
    val totalFacturas: Int,
    val facturasCompletas: Int
)

data class MarkAssignmentViewedResponse(
    val success: Boolean,
    val cambio: Boolean?,
    val message: String?
)

fun AsignacionDto.toEntity() = AsignacionEntity(
    idAsignacion = idAsignacion,
    crefAgrupada = crefAgrupada,
    supervisor = supervisor,
    fechaAsignacion = fechaAsignacion,
    prioridad = prioridad,
    nota = nota,
    estatus = estatus,
    nombreCliente = nombreCliente ?: "",
    totalFacturas = totalFacturas,
    facturasCompletas = facturasCompletas
)
