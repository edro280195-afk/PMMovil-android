package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pmmovil.data.model.LabelCaptureDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelCaptureDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun save(draft: LabelCaptureDraftEntity)

    @Query("SELECT * FROM label_capture_drafts WHERE captureId = :captureId")
    suspend fun get(captureId: String): LabelCaptureDraftEntity?

    @Query("UPDATE label_capture_drafts SET stateJson = :stateJson, updatedAt = :updatedAt WHERE captureId = :captureId AND owner = :owner AND status = 'DRAFT' AND requestJson IS NULL AND uploadPath IS NULL")
    suspend fun updateState(captureId: String, owner: String, stateJson: String, updatedAt: Long): Int

    @Query("SELECT * FROM label_capture_drafts WHERE reference = :reference AND owner = :owner ORDER BY createdAt DESC")
    fun observe(reference: String, owner: String): Flow<List<LabelCaptureDraftEntity>>

    @Query("SELECT COUNT(*) FROM label_capture_drafts WHERE reference = :reference AND owner = :owner AND status != 'CONFIRMED'")
    fun observePendingCount(reference: String, owner: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM label_capture_drafts WHERE reference = :reference AND owner = :owner AND status != 'CONFIRMED'")
    suspend fun pendingCount(reference: String, owner: String): Int

    /** Solo un envío puede congelar el borrador, aunque se confirme dos veces. */
    @Query("UPDATE label_capture_drafts SET requestJson = :requestJson, uploadPath = :uploadPath, status = 'PENDING', errorMessage = NULL, responseJson = NULL, updatedAt = :updatedAt WHERE captureId = :captureId AND owner = :owner AND status = 'DRAFT' AND requestJson IS NULL AND uploadPath IS NULL")
    suspend fun freeze(captureId: String, owner: String, requestJson: String, uploadPath: String, updatedAt: Long): Int

    @Query("UPDATE label_capture_drafts SET status = :status, errorMessage = :message, responseJson = :responseJson, updatedAt = :updatedAt WHERE captureId = :captureId AND owner = :owner AND status = :expectedStatus")
    suspend fun transition(captureId: String, owner: String, expectedStatus: String, status: String, message: String?, responseJson: String?, updatedAt: Long): Int

    @Query("DELETE FROM label_capture_drafts WHERE captureId = :captureId AND owner = :owner AND status = :expectedStatus AND status != 'PENDING'")
    suspend fun delete(captureId: String, owner: String, expectedStatus: String): Int
}
