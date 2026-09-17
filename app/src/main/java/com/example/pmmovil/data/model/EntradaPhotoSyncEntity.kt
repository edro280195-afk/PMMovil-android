package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Distingue la cola de evidencia general de la cola de fotos exclusivas de daño (ntipodocto 100 vs 103). */
object EntradaPhotoKind {
    const val EVIDENCE = "EVIDENCE"
    const val DAMAGE = "DAMAGE"
}

@Entity(tableName = "entrada_fotos_sync")
data class EntradaPhotoSyncEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val reference: String,
    val localFilePath: String,
    val kind: String = EntradaPhotoKind.EVIDENCE,
    val synced: Boolean = false,
    val errorMessage: String? = null,
    val syncBlocked: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
