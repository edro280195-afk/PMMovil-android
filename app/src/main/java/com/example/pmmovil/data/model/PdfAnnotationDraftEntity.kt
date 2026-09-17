package com.example.pmmovil.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Anotaciones que el revisor lleva hechas sobre un documento y que todavia no
 * se exportan a una copia del PDF.
 *
 * Antes vivian solo en memoria dentro del ViewModel: sobrevivian a ocultar y
 * reabrir el visor, pero se perdian sin aviso si el revisor salia de la
 * pantalla o si Android mataba la app en bodega. Aqui se guardan solas conforme
 * se dibuja, y la fila se borra cuando las marcas ya quedaron grabadas en la
 * copia anotada.
 *
 * [documentKey] identifica el documento dentro de la entrada, con el mismo
 * formato que usa `PdfWorkspaceViewModel`: "creferencia|tipo".
 */
@Entity(tableName = "pdf_annotation_drafts")
data class PdfAnnotationDraftEntity(
    @PrimaryKey val documentKey: String,
    val crefAgrupada: String,
    val annotationsJson: String,
    /**
     * Ruta de la ultima copia anotada que se exporto de este documento. Se
     * conserva para que al volver se abra esa copia y no el original limpio.
     * Es nula mientras no se haya exportado ninguna.
     */
    val annotatedFilePath: String?,
    val lastEditAt: Long
)
