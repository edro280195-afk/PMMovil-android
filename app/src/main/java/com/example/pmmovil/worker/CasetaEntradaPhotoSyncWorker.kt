package com.example.pmmovil.worker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.example.pmmovil.data.api.FotosMultipart
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.CasetaEntradaPhotoSyncDao
import com.example.pmmovil.data.model.CasetaEntradaFotoUpload
import com.example.pmmovil.data.model.CasetaEntradaFotosRequest
import com.example.pmmovil.data.model.CasetaEntradaFotosResponse
import com.example.pmmovil.data.model.CasetaEntradaPhotoSyncEntity
import com.example.pmmovil.data.camera.FotoParaSubir
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.Base64
import retrofit2.Response

/**
 * Sube evidencia de Caseta en lotes pequeños sin bloquear el guardado del folio.
 *
 * Las fotos ya vienen preparadas desde que se tomaron ([FotoParaSubir]) y viajan como archivos (~25 % menos que en
 * base64). Un servidor anterior no tiene la ruta de archivos: responde 404 y desde ahí se mandan como base64, como antes.
 */
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
        // Cambia a false la primera vez que el servidor no reconoce la ruta de archivos.
        var comoArchivo = true
        val carpetas = mutableSetOf<File>()

        val recoveredRows = dao.unblockLegacyBadRequestRows()
        if (recoveredRows > 0) {
            Log.i(TAG, "Se reintentaran $recoveredRows fotos bloqueadas por el 400 opaco anterior.")
        }

        try {
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

                        val prepared = batch.mapNotNull { photo ->
                            val file = File(photo.localFilePath)
                            file.parentFile?.let(carpetas::add)
                            if (!file.exists()) {
                                dao.markFailed(photo.id, "No se encontró el archivo local de la foto de caseta.", true)
                                hasPermanentFailure = true
                                null
                            } else {
                                FotoParaSubir.bytes(file.absolutePath)?.let { bytes -> photo to bytes }
                                    ?: run {
                                        dao.markFailed(photo.id, "No se pudo procesar la foto de caseta.", true)
                                        hasPermanentFailure = true
                                        null
                                    }
                            }
                        }

                        // Una foto que ya no existe en la tableta (o que no se pudo procesar) quedo marcada como falla
                        // permanente: eso no debe dejar atras las demas fotos del lote ni las de los lotes siguientes.
                        if (prepared.isEmpty()) return@forEachIndexed

                        // Con un lote incompleto NO se marca el ultimo lote (`finalizar`): el expediente esta incompleto. El
                        // servidor sabe cuantas fotos anuncio la tableta y, como no llegaran todas, manda el aviso por su
                        // respaldo a los pocos minutos con las que si llegaron.
                        val isFinalBatch = batchIndex == orderedPhotos.lastIndex / PHOTOS_PER_REQUEST
                        val finalizar = isFinalBatch && prepared.size == batch.size &&
                            batch.lastOrNull()?.notifyWhenFinished == true
                        val actualizado = batch.lastOrNull()?.notifyAsUpdate == true
                        val startedAt = SystemClock.elapsedRealtime()
                        try {
                            var response: Response<CasetaEntradaFotosResponse>? = null
                            if (comoArchivo) {
                                response = api.uploadCasetaEntradaPhotoFiles(
                                    casetaId = casetaId,
                                    indices = prepared.map { (photo, _) -> FotosMultipart.campo("indices", photo.photoIndex.toString()) },
                                    fotos = prepared.map { (photo, bytes) -> FotosMultipart.foto("fotos", "${photo.photoIndex}.jpg", bytes) },
                                    finalizar = FotosMultipart.texto(finalizar.toString()),
                                    actualizado = FotosMultipart.texto(actualizado.toString())
                                )
                                if (response.code() == 404 || response.code() == 405 || response.code() == 415) {
                                    Log.i(TAG, "El servidor no recibe fotos como archivo (HTTP ${response.code()}): se mandan como base64.")
                                    comoArchivo = false
                                    response = null
                                }
                            }
                            if (response == null) {
                                response = api.uploadCasetaEntradaPhotos(
                                    casetaId,
                                    CasetaEntradaFotosRequest(
                                        fotos = prepared.map { (photo, bytes) ->
                                            CasetaEntradaFotoUpload(photo.photoIndex, dataUri(bytes))
                                        },
                                        finalizar = finalizar,
                                        actualizado = actualizado
                                    )
                                )
                            }

                            val body = response.body()
                            if (response.isSuccessful && body?.success == true) {
                                prepared.forEach { (photo, _) ->
                                    dao.markSynced(photo.id)
                                    runCatching { fileFor(photo).delete() }
                                    FotoParaSubir.olvidar(photo.localFilePath)
                                }
                                // Tiempos para medir: lo que tardo el lote y cuanto llevan las fotos esperando desde que se guardo la entrada.
                                Log.i(
                                    TAG,
                                    "casetaId=$casetaId fotos=${prepared.size} kb=${prepared.sumOf { it.second.size } / 1024} " +
                                        "comoArchivo=$comoArchivo lote=${SystemClock.elapsedRealtime() - startedAt} ms " +
                                        "desdeGuardado=${System.currentTimeMillis() - batch.first().timestamp} ms " +
                                        "finalizar=$finalizar"
                                )
                            } else {
                                val errorBody = response.errorBody()?.string().orEmpty()
                                val message = body?.message
                                    ?.takeIf(String::isNotBlank)
                                    ?: parseErrorMessage(errorBody)
                                    ?: "Error HTTP ${response.code()} al subir fotos de caseta."
                                val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                                prepared.forEach { (photo, _) -> dao.markFailed(photo.id, message, !retryable) }
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
                            prepared.forEach { (photo, _) -> dao.markFailed(photo.id, message, false) }
                            stopCurrentCaseta = true
                            Log.e(TAG, message, e)
                        }
                    }
                }

                if (hasRetryableFailure) return Result.retry()
                if (hasPermanentFailure) return Result.failure()
            }
        } finally {
            // Preparadas de fotos que ya no existen (la app se cerró entre subir y borrar, o se descartó una captura).
            carpetas.forEach { runCatching { FotoParaSubir.limpiarHuerfanas(it) } }
        }
    }

    private fun fileFor(photo: CasetaEntradaPhotoSyncEntity): File =
        File(photo.localFilePath)

    private fun dataUri(bytes: ByteArray): String = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)

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
