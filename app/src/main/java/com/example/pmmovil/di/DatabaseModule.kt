package com.example.pmmovil.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pmmovil.data.db.AppDatabase
import com.example.pmmovil.data.db.AsignacionDao
import com.example.pmmovil.data.db.CasetaEntradaDraftDao
import com.example.pmmovil.data.db.CasetaEntradaPhotoSyncDao
import com.example.pmmovil.data.db.PdfAnnotationDraftDao
import com.example.pmmovil.data.db.EntradaPhotoSyncDao
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.db.LabelCaptureDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Scope que vive lo que vive el proceso. Sirve para escrituras que NO pueden
 * morir con la pantalla: p. ej. respaldar el borrador en `onCleared()`, cuando
 * `viewModelScope` ya viene cancelado y un `launch` ahi no correria nunca.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val migration3To4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE fotos_sync ADD COLUMN remotePhotoId INTEGER"
            )
        }
    }

    private val migration4To5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS entrada_fotos_sync (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    reference TEXT NOT NULL,
                    localFilePath TEXT NOT NULL,
                    synced INTEGER NOT NULL,
                    errorMessage TEXT,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    // OJO: sin esta migracion, una actualizacion podria perder las tablas
    // locales -- incluidas las fotos que aun no se suben al servidor.
    private val migration5To6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS caseta_entrada_drafts (
                    id TEXT PRIMARY KEY NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastEditAt INTEGER NOT NULL,
                    casetaId TEXT NOT NULL,
                    idCaseta TEXT NOT NULL,
                    idBodega TEXT NOT NULL,
                    bodega TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    movement TEXT NOT NULL,
                    idBoxType TEXT NOT NULL,
                    boxType TEXT NOT NULL,
                    boxTypeDescription TEXT NOT NULL,
                    truckNumber TEXT NOT NULL,
                    idTruckLine TEXT NOT NULL,
                    truckLineName TEXT NOT NULL,
                    boxNumber TEXT NOT NULL,
                    idBoxLine TEXT NOT NULL,
                    boxLineName TEXT NOT NULL,
                    idCustomer TEXT NOT NULL,
                    customerName TEXT NOT NULL,
                    operatorName TEXT NOT NULL,
                    plates TEXT NOT NULL,
                    seals TEXT NOT NULL,
                    idCountry TEXT NOT NULL,
                    countryName TEXT NOT NULL,
                    observations TEXT NOT NULL,
                    temperature TEXT NOT NULL,
                    fuel TEXT NOT NULL,
                    pedimento TEXT NOT NULL,
                    guideCount TEXT NOT NULL,
                    requiresImport INTEGER NOT NULL,
                    updated INTEGER NOT NULL,
                    localPhotoPaths TEXT NOT NULL,
                    origenPreRegistro INTEGER NOT NULL,
                    isExisting INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    // La version 7 no cambio el esquema. Se declara explicitamente para que
    // Room pueda llegar a la migracion 7->8 sin borrar borradores locales.
    private val migration6To7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) = Unit
    }

    // Sin esta migracion, una actualizacion podria perder los borradores de
    // Entrada Caseta que aun no se mandaron al servidor.
    private val migration7To8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE caseta_entrada_drafts ADD COLUMN tipoRegistro TEXT NOT NULL DEFAULT 'CAJA'"
            )
        }
    }

    // Sin esta migracion, una actualizacion podria perder las fotos locales
    // aun no sincronizadas (igual que 5->6). Agrega la bandera que distingue
    // evidencia de discrepancia (ntipodocto=103) de evidencia normal (107).
    private val migration8To9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE fotos_sync ADD COLUMN esDiscrepancia INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    // Sin esta migracion, actualizar la app borraria los borradores de
    // Entrada Caseta y las fotos aun no sincronizadas. Agrega la tabla donde el
    // visor de PDF guarda solo las anotaciones que el revisor lleva hechas.
    private val migration9To10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pdf_annotation_drafts (
                    documentKey TEXT PRIMARY KEY NOT NULL,
                    crefAgrupada TEXT NOT NULL,
                    annotationsJson TEXT NOT NULL,
                    annotatedFilePath TEXT,
                    lastEditAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    // Conserva las fotos que no se pudieron procesar o subir y permite
    // distinguir un error definitivo de un fallo transitorio de red.
    private val migration10To11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            val entradaColumns = database.query("PRAGMA table_info(entrada_fotos_sync)")
            val hasKindColumn = entradaColumns.use { cursor ->
                val nameIndex = cursor.getColumnIndex("name")
                generateSequence {
                    if (cursor.moveToNext()) cursor.getString(nameIndex) else null
                }.any { it == "kind" }
            }
            if (!hasKindColumn) {
                database.execSQL(
                    "ALTER TABLE entrada_fotos_sync ADD COLUMN kind TEXT NOT NULL DEFAULT 'EVIDENCE'"
                )
            }
            database.execSQL(
                "ALTER TABLE fotos_sync ADD COLUMN errorMessage TEXT"
            )
            database.execSQL(
                "ALTER TABLE fotos_sync ADD COLUMN syncBlocked INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                "ALTER TABLE entrada_fotos_sync ADD COLUMN syncBlocked INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    private val migration11To12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS caseta_entrada_fotos_sync (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    casetaId TEXT NOT NULL,
                    localFilePath TEXT NOT NULL,
                    photoIndex INTEGER NOT NULL,
                    notifyWhenFinished INTEGER NOT NULL DEFAULT 0,
                    synced INTEGER NOT NULL DEFAULT 0,
                    errorMessage TEXT,
                    syncBlocked INTEGER NOT NULL DEFAULT 0,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    private val migration12To13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE caseta_entrada_fotos_sync ADD COLUMN notifyAsUpdate INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "pmmovil_database"
        )
        .addMigrations(
            migration3To4,
            migration4To5,
            migration5To6,
            migration6To7,
            migration7To8,
            migration8To9,
            migration9To10,
            migration10To11,
            migration11To12,
            migration12To13,
            migration13To14
        )
        .build()
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Provides
    fun provideAsignacionDao(database: AppDatabase): AsignacionDao {
        return database.asignacionDao()
    }

    @Provides
    fun provideFotoDao(database: AppDatabase): FotoDao {
        return database.fotoDao()
    }

    @Provides
    fun provideEntradaPhotoSyncDao(database: AppDatabase): EntradaPhotoSyncDao {
        return database.entradaPhotoSyncDao()
    }

    @Provides
    fun provideCasetaEntradaPhotoSyncDao(database: AppDatabase): CasetaEntradaPhotoSyncDao {
        return database.casetaEntradaPhotoSyncDao()
    }

    @Provides
    fun provideCasetaEntradaDraftDao(database: AppDatabase): CasetaEntradaDraftDao {
        return database.casetaEntradaDraftDao()
    }

    @Provides
    fun providePdfAnnotationDraftDao(database: AppDatabase): PdfAnnotationDraftDao {
        return database.pdfAnnotationDraftDao()
    }

    @Provides
    fun provideLabelCaptureDao(database: AppDatabase): LabelCaptureDao = database.labelCaptureDao()

    private val migration13To14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS label_capture_drafts (
                    captureId TEXT NOT NULL PRIMARY KEY,
                    reference TEXT NOT NULL,
                    owner TEXT NOT NULL,
                    photoPath TEXT NOT NULL,
                    stateJson TEXT NOT NULL,
                    requestJson TEXT,
                    uploadPath TEXT,
                    status TEXT NOT NULL,
                    errorMessage TEXT,
                    responseJson TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }
}
