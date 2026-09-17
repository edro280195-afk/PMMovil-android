package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pmmovil.data.model.CasetaEntradaPhotoSyncEntity

@Dao
interface CasetaEntradaPhotoSyncDao {
    @Insert
    suspend fun insert(photo: CasetaEntradaPhotoSyncEntity)

    @Query(
        """
        SELECT * FROM caseta_entrada_fotos_sync
        WHERE synced = 0
          AND syncBlocked = 0
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPendingOnce(): List<CasetaEntradaPhotoSyncEntity>

    /** Recupera filas bloqueadas por el antiguo 400 sin cuerpo de respuesta. */
    @Query(
        """
        UPDATE caseta_entrada_fotos_sync
        SET errorMessage = NULL,
            syncBlocked = 0
        WHERE synced = 0
          AND syncBlocked = 1
          AND errorMessage = 'Error HTTP 400 al subir fotos de caseta.'
        """
    )
    suspend fun unblockLegacyBadRequestRows(): Int

    @Query("UPDATE caseta_entrada_fotos_sync SET synced = 1, errorMessage = NULL, syncBlocked = 0 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query(
        """
        UPDATE caseta_entrada_fotos_sync
        SET errorMessage = :message,
            syncBlocked = :blocked
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Int, message: String, blocked: Boolean)

    @Query("DELETE FROM caseta_entrada_fotos_sync WHERE id = :id")
    suspend fun delete(id: Int)
}
