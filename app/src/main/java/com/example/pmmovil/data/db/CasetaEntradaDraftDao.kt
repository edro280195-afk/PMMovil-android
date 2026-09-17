package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.pmmovil.data.model.CasetaEntradaDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CasetaEntradaDraftDao {

    @Query("SELECT * FROM caseta_entrada_drafts ORDER BY lastEditAt DESC")
    fun observeAll(): Flow<List<CasetaEntradaDraftEntity>>

    @Upsert
    suspend fun upsert(draft: CasetaEntradaDraftEntity)

    @Query("DELETE FROM caseta_entrada_drafts WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Borradores que sobran una vez conservados los [keep] mas recientes. Se
     * devuelven las filas (no se borran aqui) para poder eliminar tambien sus
     * fotos del disco antes de tirar el registro.
     */
    @Query("SELECT * FROM caseta_entrada_drafts ORDER BY lastEditAt DESC LIMIT -1 OFFSET :keep")
    suspend fun getBeyondLimit(keep: Int): List<CasetaEntradaDraftEntity>
}
