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
