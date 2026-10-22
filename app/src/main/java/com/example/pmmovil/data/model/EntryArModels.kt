package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

data class EntryArBatchRequest(
    val references: List<String>
)

data class EntryArBatchResponse(
    val success: Boolean = false,
    val items: List<EntryArCardDto> = emptyList(),
    val message: String? = null
)

/** Resumen de una entrada para la tarjeta anclada al QR. */
data class EntryArCardDto(
    val reference: String = "",
    val exists: Boolean = true,
    val customerName: String = "",
    val description: String = "",
    val location: String = "",
    val arrivalDate: String? = null,
    val reviewStatus: String = "SIN_REVISAR",
    val totalInvoices: Int = 0,
    val completedInvoices: Int = 0,
    val totalLines: Int = 0,
    val reviewedLines: Int = 0,
    val hasHazmat: Boolean = false,
    val hasDamage: Boolean = false,
    val damageDescription: String = ""
)

/** Último resumen consultado; se conserva para el modo sin conexión. */
@Entity(tableName = "entry_ar_cache")
data class EntryArCacheEntity(
    @PrimaryKey val reference: String,
    val payloadJson: String,
    val fetchedAtMillis: Long
)

