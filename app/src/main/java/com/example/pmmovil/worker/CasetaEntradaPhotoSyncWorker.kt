package com.example.pmmovil.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.CasetaEntradaPhotoSyncDao
import com.example.pmmovil.data.model.CasetaEntradaFotoUpload
import com.example.pmmovil.data.model.CasetaEntradaFotosRequest
import com.example.pmmovil.data.model.CasetaEntradaFotosResponse
import com.example.pmmovil.data.model.CasetaEntradaPhotoSyncEntity
import com.example.pmmovil.data.camera.PhotoPipeline
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.File

/** Sube evidencia de Caseta en lotes pequeños sin bloquear el guardado del folio. */
class CasetaEntradaPhotoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface CasetaEntradaPhotoSyncWorkerEntryPoint {
        fun casetaEntradaPhotoSyncDao(): CasetaEntradaPhotoSyncDao
        fun pmMovilApi(): PMMovilApi
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CasetaEntradaPhotoSyncWorkerEntryPoint::class.java
        )
        val dao = entryPoint.casetaEntradaPhotoSyncDao()
        val api = entryPoint.pmMovilApi()
        var hasPermanentFailure = false

        val recoveredRows = dao.unblockLegacyBadRequestRows()
        if (recoveredRows > 0) {
            Log.i(TAG, "Se reintentaran $recoveredRows fotos bloqueadas por el 400 opaco anterior.")
        }

        while (true) {
            val pending = dao.getPendingOnce()
            if (pending.isEmpty()) {
                return if (hasPermanentFailure) Result.failure() else Result.success()
            }

            var hasRetryableFailure = false
            pending.groupBy { it.casetaId }.forEach { (casetaId, photos) ->
                val orderedPhotos = photos.sortedBy { it.photoIndex }
                var stopCurrentCaseta = false
                orderedPhotos.chunked(PHOTOS_PER_REQUEST).forEachIndexed { batchIndex, batch ->
                    if (stopCurrentCaseta) return@forEachIndexed

                    val encoded = batch.mapNotNull { photo ->
                        val file = File(photo.localFilePath)
                        if (!file.exists()) {
                            dao.markFailed(photo.id, "No se encontró el archivo local de la foto de caseta.", true)
                            hasPermanentFailure = true
                            null
                        } else {
                            PhotoPipeline.aDataUri(file.absolutePath)?.let { dataUri -> photo to dataUri }
                                ?: run {
                                    dao.markFailed(photo.id, "No se pudo procesar la foto de caseta.", true)
                                    hasPermanentFailure = true
                                    null
                                }
                        }
                    }

                    if (encoded.isEmpty()) {
                        stopCurrentCaseta = true
                        return@forEachIndexed
                    }
                    if (encoded.size != batch.size) {
                        // No se manda una tanda incompleta: si falta una
                        // evidencia no se debe marcar como enviado el resto ni
                        // disparar el correo final con un expediente parcial.
                        stopCurrentCaseta = true
                        return@forEachIndexed
                    }

                    val isFinalBatch = batchIndex == orderedPhotos.lastIndex / PHOTOS_PER_REQUEST
                    try {
                        val response = api.uploadCasetaEntradaPhotos(
                            casetaId,
                            CasetaEntradaFotosRequest(
                                fotos = encoded.map { (photo, dataUri) ->
                                    CasetaEntradaFotoUpload(photo.photoIndex, dataUri)
                                },
                                finalizar = isFinalBatch && encoded.size == batch.size &&
                                    batch.lastOrNull()?.notifyWhenFinished == true,
                                actualizado = batch.lastOrNull()?.notifyAsUpdate == true
                            )
                        )
                        val body = response.body()
                        if (response.isSuccessful && body?.success == true) {
                            encoded.forEach { (photo, _) ->
                                dao.markSynced(photo.id)
                                runCatching { fileFor(photo).delete() }
                            }
                        } else {
                            val errorBody = response.errorBody()?.string().orEmpty()
                            val message = body?.message
                                ?.takeIf(String::isNotBlank)
                                ?: parseErrorMessage(errorBody)
                                ?: "Error HTTP ${response.code()} al subir fotos de caseta."
                            val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                            encoded.forEach { (photo, _) -> dao.markFailed(photo.id, message, !retryable) }
                            if (retryable) hasRetryableFailure = true else hasPermanentFailure = true
                            stopCurrentCaseta = true
                            Log.e(
                                TAG,
                                "HTTP ${response.code()} al subir fotos. " +
                                    "casetaId=$casetaId indices=${batch.joinToString(",") { it.photoIndex.toString() }} " +
                                    "finalizar=${batch.lastOrNull()?.notifyWhenFinished == true}. $message"
                            )
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hasRetryableFailure = true
                        val message = e.message ?: "Error al sincronizar fotos de caseta."
                        encoded.forEach { (photo, _) -> dao.markFailed(photo.id, message, false) }
                        stopCurrentCaseta = true
                        Log.e(TAG, message, e)
                    }
                }
            }

            if (hasRetryableFailure) return Result.retry()
            if (hasPermanentFailure) return Result.failure()
        }
    }

    private fun fileFor(photo: CasetaEntradaPhotoSyncEntity): File =
        File(photo.localFilePath)

    private fun parseErrorMessage(errorBody: String): String? = runCatching {
        if (errorBody.isBlank()) return@runCatching null
        Gson().fromJson(errorBody, CasetaEntradaFotosResponse::class.java)
            ?.message
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    private companion object {
        const val TAG = "CasetaPhotoSync"
        const val PHOTOS_PER_REQUEST = 4
    }
}
