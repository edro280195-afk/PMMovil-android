package com.example.pmmovil.worker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.api.FotosMultipart
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.camera.FotoParaSubir
import com.example.pmmovil.data.db.EntradaPhotoSyncDao
import com.example.pmmovil.data.model.EntradaOperationResponse
import com.example.pmmovil.data.model.EntradaPhotoKind
import com.example.pmmovil.data.model.EntradaPhotoSyncEntity
import com.example.pmmovil.data.model.EntradaPhotosRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import retrofit2.Response
import java.io.File
import java.util.Base64

/**
 * Sube las fotos de Entrada Bodega (evidencia y daño) que esperan en la cola del dispositivo, en lotes de
 * [PHOTOS_PER_REQUEST] por referencia y tipo.
 *
 * Las fotos ya vienen preparadas desde que se tomaron ([FotoParaSubir]: 2560 px, calidad 92) y viajan como archivos (~25 %
 * menos que en base64 por el internet de la oficina). Un API anterior no tiene la ruta de archivos: contesta 404 y desde ahí
 * se mandan como base64, como antes ([FotosMultipart.sinRutaDeArchivos]).
 */
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
        var comoArchivo = true
        val carpetas = mutableSetOf<File>()
        try {
            while (true) {
                val pending = dao.getPendingOnce()
                if (pending.isEmpty()) {
                    return if (hasPermanentFailure) Result.failure() else Result.success()
                }

                var hasRetryableFailure = false
                pending.groupBy { it.reference to it.kind }.forEach { (key, photos) ->
                    val (reference, kind) = key
                    val danio = kind == EntradaPhotoKind.DAMAGE
                    val etiqueta = if (danio) "de daño" else "de entrada"
                    photos.chunked(PHOTOS_PER_REQUEST).forEach { photoBatch ->
                        val prepared = photoBatch.mapNotNull { photo ->
                            val file = File(photo.localFilePath)
                            file.parentFile?.let(carpetas::add)
                            if (!file.exists()) {
                                dao.markFailed(
                                    photo.id,
                                    "No se encontró el archivo local de la evidencia.",
                                    blocked = true
                                )
                                hasPermanentFailure = true
                                null
                            } else {
                                val bytes = FotoParaSubir.bytes(file.absolutePath)
                                if (bytes == null) {
                                    dao.markFailed(
                                        photo.id,
                                        "No se pudo procesar la imagen local.",
                                        blocked = true
                                    )
                                    hasPermanentFailure = true
                                }
                                bytes?.let { photo to it }
                            }
                        }

                        if (prepared.isEmpty()) return@forEach

                        val startedAt = SystemClock.elapsedRealtime()
                        try {
                            var response: Response<EntradaOperationResponse>? = null
                            if (comoArchivo) {
                                response = subirComoArchivos(api, reference, danio, prepared)
                                if (FotosMultipart.sinRutaDeArchivos(response.code())) {
                                    Log.i(TAG, "El servidor no recibe fotos como archivo (HTTP ${response.code()}): se mandan como base64.")
                                    comoArchivo = false
                                    response = null
                                }
                            }
                            if (response == null) {
                                val request = EntradaPhotosRequest(
                                    referencia = reference,
                                    fotos = prepared.map { (_, bytes) -> dataUri(bytes) }
                                )
                                response = if (danio) api.saveEntradaDamagePhotos(request) else api.saveEntradaPhotos(request)
                            }

                            val body = response.body()
                            if (response.isSuccessful && body?.success == true) {
                                prepared.forEach { (photo, _) ->
                                    dao.markSynced(photo.id)
                                    runCatching { File(photo.localFilePath).delete() }
                                    FotoParaSubir.olvidar(photo.localFilePath)
                                }
                                Log.i(
                                    TAG,
                                    "ref=$reference tipo=$kind fotos=${prepared.size} kb=${prepared.sumOf { it.second.size } / 1024} " +
                                        "comoArchivo=$comoArchivo lote=${SystemClock.elapsedRealtime() - startedAt} ms"
                                )
                            } else {
                                val message = body?.message
                                    ?: "Error HTTP ${response.code()} al subir fotos $etiqueta."
                                val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                                prepared.forEach { (photo, _) ->
                                    dao.markFailed(photo.id, message, blocked = !retryable)
                                }
                                if (retryable) hasRetryableFailure = true else hasPermanentFailure = true
                                Log.e(TAG, message)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            hasRetryableFailure = true
                            val message = e.message ?: "Error al sincronizar fotos $etiqueta."
                            prepared.forEach { (photo, _) ->
                                dao.markFailed(photo.id, message, blocked = false)
                            }
                            Log.e(TAG, message, e)
                        }
                    }
                }

                if (hasRetryableFailure) return Result.retry()
            }
        } finally {
            // Preparadas de fotos que ya no existen (la app se cerró entre subir y borrar, o se descartó la captura).
            carpetas.forEach { runCatching { FotoParaSubir.limpiarHuerfanas(it) } }
        }
    }

    private suspend fun subirComoArchivos(
        api: PMMovilApi,
        reference: String,
        danio: Boolean,
        fotos: List<Pair<EntradaPhotoSyncEntity, ByteArray>>
    ): Response<EntradaOperationResponse> {
        val referencia = FotosMultipart.texto(reference)
        val partes = fotos.map { (photo, bytes) -> FotosMultipart.foto("fotos", "${photo.id}.jpg", bytes) }
        return if (danio) api.saveEntradaDamagePhotoFiles(referencia, partes) else api.saveEntradaPhotoFiles(referencia, partes)
    }

    private fun dataUri(bytes: ByteArray): String = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)

    private companion object {
        const val TAG = "EntradaPhotoSync"

        // Mantiene cada request por debajo de un tamaño razonable y limita la
        // cantidad de fotos vivas en memoria al mismo tiempo.
        const val PHOTOS_PER_REQUEST = 4
    }
}
