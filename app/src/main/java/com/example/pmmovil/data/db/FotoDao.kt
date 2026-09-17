package com.example.pmmovil.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.pmmovil.data.model.FotoSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FotoDao {
    @Insert
    suspend fun insertFoto(foto: FotoSyncEntity)

    @Query("SELECT * FROM fotos_sync WHERE synced = 0 AND syncBlocked = 0 ORDER BY timestamp ASC")
    fun getFotosPendientes(): Flow<List<FotoSyncEntity>>

    @Query(
        """
        SELECT * FROM fotos_sync
        WHERE synced = 0
          AND syncBlocked = 0
          AND crefAgrupada = :crefAgrupada
          AND (:cnofact = '' OR cnofact = :cnofact)
        ORDER BY timestamp DESC
        """
    )
    fun getFotosPendientes(
        crefAgrupada: String,
        cnofact: String
    ): Flow<List<FotoSyncEntity>>

    @Query(
        """
        SELECT * FROM fotos_sync
        WHERE crefAgrupada = :crefAgrupada
          AND (:cnofact = '' OR cnofact = :cnofact)
        ORDER BY timestamp DESC
        """
    )
    fun getFotosLocales(
        crefAgrupada: String,
        cnofact: String
    ): Flow<List<FotoSyncEntity>>

    @Query(
        """
        UPDATE fotos_sync
        SET synced = 1,
            remotePhotoId = :remotePhotoId,
            errorMessage = NULL,
            syncBlocked = 0
        WHERE id = :id
        """
    )
    suspend fun markAsSynced(id: Int, remotePhotoId: Int?)

    @Query(
        """
        UPDATE fotos_sync
        SET errorMessage = :message,
            syncBlocked = :blocked
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Int, message: String, blocked: Boolean)

    @Query("DELETE FROM fotos_sync WHERE id = :id")
    suspend fun deleteFoto(id: Int)

    @Query("SELECT * FROM fotos_sync WHERE remotePhotoId = :remotePhotoId LIMIT 1")
    suspend fun getFotoByRemotePhotoId(remotePhotoId: Int): FotoSyncEntity?

    // Cuando una foto ya sincronizada se borra del servidor (deleteRemotePhoto),
    // su registro local (synced=true, remotePhotoId=X) se queda huérfano si no
    // se limpia aquí: el filtro de "localPhotos visibles" en CameraScreen la
    // vuelve a mostrar como si fuera una foto pendiente, porque ya no encuentra
    // ese remotePhotoId en la lista de remotas. Eso causaba el conteo fantasma
    // reportado (badge dice 1, la galería sigue mostrando 2).
    @Query("DELETE FROM fotos_sync WHERE remotePhotoId = :remotePhotoId")
    suspend fun deleteFotoByRemotePhotoId(remotePhotoId: Int)

    // Bajo el flujo correcto (tomar -> guardar local -> sincronizar -> borrar
    // local) ya NUNCA se vuelve a marcar synced=1 de forma persistente: en
    // cuanto el worker confirma la subida, borra el registro de una vez
    // (ver FotoSyncWorker). Cualquier fila con synced=1 que exista hoy es
    // basura de instalaciones/pruebas previas a ese cambio -- se limpia sola
    // la primera vez que se abre cámara o galería tras actualizar.
    @Query("SELECT * FROM fotos_sync WHERE synced = 1")
    suspend fun getFotosSincronizadasHuerfanas(): List<FotoSyncEntity>

    @Query("DELETE FROM fotos_sync WHERE synced = 1")
    suspend fun limpiarSincronizadasHuerfanas()
}
