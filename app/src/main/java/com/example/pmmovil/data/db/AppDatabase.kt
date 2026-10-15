package com.example.pmmovil.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pmmovil.data.model.AsignacionEntity
import com.example.pmmovil.data.model.CasetaEntradaDraftEntity
import com.example.pmmovil.data.model.CasetaEntradaPhotoSyncEntity
import com.example.pmmovil.data.model.EntradaPhotoSyncEntity
import com.example.pmmovil.data.model.FotoSyncEntity
import com.example.pmmovil.data.model.PdfAnnotationDraftEntity
import com.example.pmmovil.data.model.LabelCaptureDraftEntity
import com.example.pmmovil.data.model.RackCensusObservationEntity
import com.example.pmmovil.data.model.RackCensusSessionEntity

@Database(
    entities = [
        AsignacionEntity::class,
        FotoSyncEntity::class,
        EntradaPhotoSyncEntity::class,
        CasetaEntradaPhotoSyncEntity::class,
        CasetaEntradaDraftEntity::class,
        PdfAnnotationDraftEntity::class,
        LabelCaptureDraftEntity::class,
        RackCensusSessionEntity::class,
        RackCensusObservationEntity::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(StringListConverter::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun asignacionDao(): AsignacionDao
    abstract fun fotoDao(): FotoDao
    abstract fun entradaPhotoSyncDao(): EntradaPhotoSyncDao
    abstract fun casetaEntradaPhotoSyncDao(): CasetaEntradaPhotoSyncDao
    abstract fun casetaEntradaDraftDao(): CasetaEntradaDraftDao
    abstract fun pdfAnnotationDraftDao(): PdfAnnotationDraftDao
    abstract fun labelCaptureDao(): LabelCaptureDao
    abstract fun rackCensusDao(): RackCensusDao
}
