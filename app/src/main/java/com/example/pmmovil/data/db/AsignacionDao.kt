package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pmmovil.data.model.AsignacionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AsignacionDao {
    @Query("SELECT * FROM asignaciones ORDER BY prioridad DESC, fechaAsignacion DESC")
    fun getAllAsignaciones(): Flow<List<AsignacionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(asignaciones: List<AsignacionEntity>)

    @Query("DELETE FROM asignaciones WHERE requiresSync = 0")
    suspend fun clearSyncedAsignaciones()

    @Query("DELETE FROM asignaciones WHERE idAsignacion = :id")
    suspend fun deleteById(id: Int)
    
    @Query("UPDATE asignaciones SET estatus = :estatus, requiresSync = 1 WHERE idAsignacion = :id")
    suspend fun updateEstatusOffline(id: Int, estatus: Int)

    @Query("UPDATE asignaciones SET estatus = :estatus, requiresSync = 0 WHERE idAsignacion = :id")
    suspend fun updateEstatus(id: Int, estatus: Int)
}
