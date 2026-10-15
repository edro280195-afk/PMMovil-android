package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.pmmovil.data.model.RackCensusObservationEntity
import com.example.pmmovil.data.model.RackCensusSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RackCensusDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSession(session: RackCensusSessionEntity)

    @Query("SELECT * FROM rack_census_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): RackCensusSessionEntity?

    @Query("SELECT * FROM rack_census_sessions WHERE sessionId = :sessionId")
    fun observeSession(sessionId: String): Flow<RackCensusSessionEntity?>

    /** Sesiones abiertas del usuario, para ofrecer continuar el recorrido. */
    @Query("SELECT * FROM rack_census_sessions WHERE owner = :owner AND status IN ('OPEN','RECONCILING','BLOCKED') ORDER BY updatedAt DESC")
    fun observeActiveSessions(owner: String): Flow<List<RackCensusSessionEntity>>

    @Query("SELECT * FROM rack_census_sessions WHERE owner = :owner AND status IN ('OPEN','RECONCILING','BLOCKED') ORDER BY updatedAt DESC")
    suspend fun activeSessions(owner: String): List<RackCensusSessionEntity>

    @Query("UPDATE rack_census_sessions SET decisionsJson = :decisionsJson, acknowledgedJson = :acknowledgedJson, updatedAt = :updatedAt WHERE sessionId = :sessionId AND owner = :owner AND status = 'OPEN'")
    suspend fun updateDecisions(
        sessionId: String,
        owner: String,
        decisionsJson: String,
        acknowledgedJson: String,
        updatedAt: Long
    ): Int

    /**
     * Solo un envío puede congelar la conciliación, aunque se confirme dos veces.
     * El mismo criterio que usa la captura de etiqueta para no rearmar la petición.
     */
    @Query("UPDATE rack_census_sessions SET reconcileRequestJson = :requestJson, status = 'RECONCILING', errorMessage = NULL, reconcileResponseJson = NULL, updatedAt = :updatedAt WHERE sessionId = :sessionId AND owner = :owner AND status = 'OPEN' AND reconcileRequestJson IS NULL")
    suspend fun freezeReconcile(sessionId: String, owner: String, requestJson: String, updatedAt: Long): Int

    @Query("UPDATE rack_census_sessions SET status = :status, errorMessage = :message, reconcileResponseJson = :responseJson, updatedAt = :updatedAt WHERE sessionId = :sessionId AND owner = :owner AND status = :expectedStatus")
    suspend fun transition(
        sessionId: String,
        owner: String,
        expectedStatus: String,
        status: String,
        message: String?,
        responseJson: String?,
        updatedAt: Long
    ): Int

    /** Reabre una conciliación bloqueada para que el revisor pueda corregir y volver a enviar. */
    @Query("UPDATE rack_census_sessions SET status = 'OPEN', reconcileRequestJson = NULL, reconcileResponseJson = NULL, errorMessage = :message, updatedAt = :updatedAt WHERE sessionId = :sessionId AND owner = :owner AND status = 'BLOCKED'")
    suspend fun reopen(sessionId: String, owner: String, message: String?, updatedAt: Long): Int

    @Query("DELETE FROM rack_census_sessions WHERE sessionId = :sessionId AND owner = :owner AND status = :expectedStatus")
    suspend fun deleteSession(sessionId: String, owner: String, expectedStatus: String): Int

    @Query("DELETE FROM rack_census_observations WHERE sessionId = :sessionId")
    suspend fun deleteObservations(sessionId: String)

    @Transaction
    suspend fun discardSession(sessionId: String, owner: String, expectedStatus: String): Int {
        val removed = deleteSession(sessionId, owner, expectedStatus)
        if (removed > 0) deleteObservations(sessionId)
        return removed
    }

    /** IGNORE hace idempotente un reintento con el mismo `observationId`. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertObservation(observation: RackCensusObservationEntity): Long

    @Query("SELECT * FROM rack_census_observations WHERE sessionId = :sessionId ORDER BY observedAtMillis ASC")
    fun observeObservations(sessionId: String): Flow<List<RackCensusObservationEntity>>

    @Query("SELECT * FROM rack_census_observations WHERE sessionId = :sessionId ORDER BY observedAtMillis ASC")
    suspend fun observations(sessionId: String): List<RackCensusObservationEntity>

    /** Lecturas previas del mismo código impreso, para no contar dos veces. */
    @Query("SELECT * FROM rack_census_observations WHERE sessionId = :sessionId AND normalizedCode = :normalizedCode")
    suspend fun observationsForCode(sessionId: String, normalizedCode: String): List<RackCensusObservationEntity>

    @Query("SELECT * FROM rack_census_observations WHERE sessionId = :sessionId AND syncStatus = 'PENDING' ORDER BY observedAtMillis ASC LIMIT :limit")
    suspend fun pendingObservations(sessionId: String, limit: Int): List<RackCensusObservationEntity>

    @Query("SELECT COUNT(*) FROM rack_census_observations WHERE sessionId = :sessionId AND syncStatus = 'PENDING'")
    fun observePendingCount(sessionId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM rack_census_observations WHERE sessionId = :sessionId AND syncStatus = 'PENDING'")
    suspend fun pendingCount(sessionId: String): Int

    @Query("UPDATE rack_census_observations SET syncStatus = :syncStatus, resolution = :resolution, packageId = :packageId, labelId = :labelId, errorMessage = :message WHERE observationId = :observationId")
    suspend fun markObservation(
        observationId: String,
        syncStatus: String,
        resolution: String,
        packageId: String?,
        labelId: Long?,
        message: String?
    ): Int
}
