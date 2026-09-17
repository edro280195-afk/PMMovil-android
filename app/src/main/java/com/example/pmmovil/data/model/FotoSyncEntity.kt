package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "fotos_sync")
data class FotoSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val crefAgrupada: String,
    val cnofact: String?,
    val cclienteid: String,
    val localFilePath: String,
    val synced: Boolean = false,
    val remotePhotoId: Int? = null,
    val errorMessage: String? = null,
    val syncBlocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    // true = tomada dentro del flujo de "Registrar discrepancia" (se sube con
    // ntipodocto=103, igual que daño de Entrada Bodega). false = evidencia
    // normal de revisión (ntipodocto=107).
    val esDiscrepancia: Boolean = false
)
