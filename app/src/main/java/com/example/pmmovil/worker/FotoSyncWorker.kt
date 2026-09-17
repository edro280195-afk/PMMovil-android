package com.example.pmmovil.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.model.SubirFotoRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.example.pmmovil.data.camera.PhotoPipeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import java.io.ByteArrayOutputStream
import java.io.File

class FotoSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FotoSyncWorkerEntryPoint {
        fun fotoDao(): FotoDao
        fun pmMovilApi(): PMMovilApi
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            FotoSyncWorkerEntryPoint::class.java
        )

        val dao = entryPoint.fotoDao()
        val api = entryPoint.pmMovilApi()

        var hasPermanentFailure = false
        do {
            val pendientes = dao.getFotosPendientes().firstOrNull() ?: emptyList()
            if (pendientes.isEmpty()) {
                return if (hasPermanentFailure) Result.failure() else Result.success()
            }

            var hasRetryableFailure = false
            for (foto in pendientes) {
                val file = File(foto.localFilePath)
                try {
                    if (!file.exists()) {
                        val message = "No se encontró el archivo local de la evidencia."
                        dao.markFailed(foto.id, message, blocked = true)
                        hasPermanentFailure = true
                        Log.e("FotoSyncWorker", "$message Ruta: ${foto.localFilePath}")
                        continue
                    }

                    val base64Image = compressAndEncodeToBase64(file)
                    if (base64Image == null) {
                        val message = "No se pudo procesar la imagen local."
                        dao.markFailed(foto.id, message, blocked = true)
                        hasPermanentFailure = true
                        Log.e("FotoSyncWorker", message)
                        continue
                    }

                    val request = SubirFotoRequest(
                        creferencia = foto.crefAgrupada,
                        cclienteid = foto.cclienteid,
                        image = base64Image,
                        extension = "jpg",
                        esDiscrepancia = foto.esDiscrepancia
                    )

                    val response = api.subirFoto(request)
                    val body = response.body()
                    if (response.isSuccessful && body?.success == true) {
                        // Primero se deja la fila marcada como sincronizada y
                        // después se retira. Si la segunda escritura falla, la
                        // fila no vuelve a enviarse en el siguiente intento.
                        dao.markAsSynced(foto.id, body.id)
                        dao.deleteFoto(foto.id)
                        runCatching { file.delete() }
                    } else {
                        val message = response.body()?.message
                            ?: "Error HTTP ${response.code()} al subir la evidencia."
                        val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                        dao.markFailed(foto.id, message, blocked = !retryable)
                        if (retryable) hasRetryableFailure = true else hasPermanentFailure = true
                        Log.e("FotoSyncWorker", message)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    hasRetryableFailure = true
                    val message = e.message ?: "Error de red al sincronizar la evidencia."
                    dao.markFailed(foto.id, message, blocked = false)
                    Log.e("FotoSyncWorker", message, e)
                }
            }

            if (hasRetryableFailure) return Result.retry()
        } while (true)
    }

    /**
     * Antes esto aplicaba `inSampleSize = 2` fijo "para ahorrar memoria": la
     * mitad de lo que midiera la foto, sin mirar cuanto media. Una foto ya
     * pequena quedaba diminuta y una grande seguia siendo grande. Ahora el
     * tamano lo decide [PhotoPipeline] a partir del objetivo real.
     */
    private fun compressAndEncodeToBase64(file: File): String? =
        PhotoPipeline.aBase64(file.absolutePath)
}
