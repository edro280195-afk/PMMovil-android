package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cola local de evidencia de caseta. El archivo se conserva hasta confirmacion del API. */
@Entity(tableName = "caseta_entrada_fotos_sync")
data class CasetaEntradaPhotoSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val casetaId: String,
    val localFilePath: String,
    val photoIndex: Int,
    val notifyWhenFinished: Boolean = false,
    val notifyAsUpdate: Boolean = false,
    val synced: Boolean = false,
    val errorMessage: String? = null,
    val syncBlocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
