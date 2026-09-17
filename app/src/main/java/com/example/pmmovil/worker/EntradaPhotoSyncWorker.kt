package com.example.pmmovil.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.EntradaPhotoSyncDao
import com.example.pmmovil.data.model.EntradaPhotoKind
import com.example.pmmovil.data.model.EntradaPhotosRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.example.pmmovil.data.camera.PhotoPipeline
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.File

class EntradaPhotoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntradaPhotoSyncWorkerEntryPoint {
        fun entradaPhotoSyncDao(): EntradaPhotoSyncDao
        fun pmMovilApi(): PMMovilApi
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            EntradaPhotoSyncWorkerEntryPoint::class.java
        )
        val dao = entryPoint.entradaPhotoSyncDao()
        val api = entryPoint.pmMovilApi()
        var hasPermanentFailure = false
        while (true) {
            val pending = dao.getPendingOnce()
            if (pending.isEmpty()) {
                return if (hasPermanentFailure) Result.failure() else Result.success()
            }

            var hasRetryableFailure = false
            pending.groupBy { it.reference to it.kind }.forEach { (key, photos) ->
                val (reference, kind) = key
                val etiqueta = if (kind == EntradaPhotoKind.DAMAGE) "de daño" else "de entrada"
                photos.chunked(PHOTOS_PER_REQUEST).forEach { photoBatch ->
                    val encoded = photoBatch.mapNotNull { photo ->
                        val file = File(photo.localFilePath)
                        if (!file.exists()) {
                            dao.markFailed(
                                photo.id,
                                "No se encontró el archivo local de la evidencia.",
                                blocked = true
                            )
                            hasPermanentFailure = true
                            null
                        } else {
                            encodePhoto(file).also { encodedPhoto ->
                                if (encodedPhoto == null) {
                                    dao.markFailed(
                                        photo.id,
                                        "No se pudo procesar la imagen local.",
                                        blocked = true
                                    )
                                    hasPermanentFailure = true
                                }
                            }?.let { encodedPhoto -> photo to encodedPhoto }
                        }
                    }

                    if (encoded.isEmpty()) return@forEach

                    try {
                        val request = EntradaPhotosRequest(
                            referencia = reference,
                            fotos = encoded.map { it.second }
                        )
                        val response = if (kind == EntradaPhotoKind.DAMAGE) {
                            api.saveEntradaDamagePhotos(request)
                        } else {
                            api.saveEntradaPhotos(request)
                        }
                        val body = response.body()
                        if (response.isSuccessful && body?.success == true) {
                            encoded.forEach { (photo, _) ->
                                dao.markSynced(photo.id)
                                runCatching { File(photo.localFilePath).delete() }
                            }
                        } else {
                            val message = body?.message
                                ?: "Error HTTP ${response.code()} al subir fotos $etiqueta."
                            val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                            encoded.forEach { (photo, _) ->
                                dao.markFailed(photo.id, message, blocked = !retryable)
                            }
                            if (retryable) hasRetryableFailure = true else hasPermanentFailure = true
                            Log.e("EntradaPhotoSync", message)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hasRetryableFailure = true
                        val message = e.message ?: "Error al sincronizar fotos $etiqueta."
                        encoded.forEach { (photo, _) ->
                            dao.markFailed(photo.id, message, blocked = false)
                        }
                        Log.e("EntradaPhotoSync", message, e)
                    }
                }
            }

            if (hasRetryableFailure) return Result.retry()
        }
    }

    /**
     * Antes decodificaba la foto COMPLETA a memoria antes de escalarla: con el
     * sensor de 48 MP de la tableta eso es un OutOfMemory esperando su turno.
     * [PhotoPipeline] baja primero con inSampleSize y despues ajusta fino.
     */
    private fun encodePhoto(file: File): String? =
        PhotoPipeline.aDataUri(file.absolutePath)

    private companion object {
        // Mantiene cada request por debajo de un tamaño razonable y limita la
        // cantidad de Base64 viva en memoria al mismo tiempo.
        const val PHOTOS_PER_REQUEST = 4
    }
}
