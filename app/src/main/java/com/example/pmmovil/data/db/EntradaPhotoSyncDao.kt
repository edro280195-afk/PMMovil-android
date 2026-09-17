package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pmmovil.data.model.EntradaPhotoKind
import com.example.pmmovil.data.model.EntradaPhotoSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EntradaPhotoSyncDao {
    @Insert
    suspend fun insert(photo: EntradaPhotoSyncEntity)

    @Query(
        """
        SELECT * FROM entrada_fotos_sync
        WHERE synced = 0
          AND syncBlocked = 0
        ORDER BY timestamp ASC
        """
    )
    suspend fun getPendingOnce(): List<EntradaPhotoSyncEntity>

    @Query(
        """
        SELECT * FROM entrada_fotos_sync
        WHERE reference = :reference
          AND kind = :kind
          AND synced = 0
        ORDER BY timestamp DESC
        """
    )
    fun observeByReference(
        reference: String,
        kind: String = EntradaPhotoKind.EVIDENCE
    ): Flow<List<EntradaPhotoSyncEntity>>

    @Query("UPDATE entrada_fotos_sync SET synced = 1, errorMessage = NULL, syncBlocked = 0 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query(
        """
        UPDATE entrada_fotos_sync
        SET errorMessage = :message,
            syncBlocked = :blocked
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Int, message: String, blocked: Boolean)

    @Query("DELETE FROM entrada_fotos_sync WHERE id = :id")
    suspend fun delete(id: Int)
}
