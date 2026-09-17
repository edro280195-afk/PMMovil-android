package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.pmmovil.data.model.PdfAnnotationDraftEntity

@Dao
interface PdfAnnotationDraftDao {

    @Query("SELECT * FROM pdf_annotation_drafts WHERE crefAgrupada = :crefAgrupada")
    suspend fun forEntry(crefAgrupada: String): List<PdfAnnotationDraftEntity>

    @Upsert
    suspend fun upsert(draft: PdfAnnotationDraftEntity)

    @Query("DELETE FROM pdf_annotation_drafts WHERE documentKey = :documentKey")
    suspend fun deleteByKey(documentKey: String)
}
