package com.example.pmmovil.worker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.api.FotosMultipart
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.camera.FotoParaSubir
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.model.FotoSyncEntity
import com.example.pmmovil.data.model.SubirFotoRequest
import com.example.pmmovil.data.model.SubirFotoResponse
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import retrofit2.Response
import java.io.File
import java.util.Base64

/**
 * Sube las fotos de evidencia de revisión que se tomaron en el dispositivo, una por una.
 *
 * La foto ya viene preparada desde que se tomó ([FotoParaSubir]: 2560 px, calidad 92) y viaja como archivo (~25 % menos
 * que en base64 por el internet de la oficina). Un API anterior no tiene la ruta de archivos: contesta 404 y desde ahí se
 * manda como base64, como antes ([FotosMultipart.sinRutaDeArchivos]).
 */
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
        var comoArchivo = true
        val carpetas = mutableSetOf<File>()
        try {
            do {
                val pendientes = dao.getFotosPendientes().firstOrNull() ?: emptyList()
                if (pendientes.isEmpty()) {
                    return if (hasPermanentFailure) Result.failure() else Result.success()
                }

                var hasRetryableFailure = false
                for (foto in pendientes) {
                    val file = File(foto.localFilePath)
                    file.parentFile?.let(carpetas::add)
                    try {
                        if (!file.exists()) {
                            val message = "No se encontró el archivo local de la evidencia."
                            dao.markFailed(foto.id, message, blocked = true)
                            hasPermanentFailure = true
                            Log.e(TAG, "$message Ruta: ${foto.localFilePath}")
                            continue
                        }

                        val bytes = FotoParaSubir.bytes(file.absolutePath)
                        if (bytes == null) {
                            val message = "No se pudo procesar la imagen local."
                            dao.markFailed(foto.id, message, blocked = true)
                            hasPermanentFailure = true
                            Log.e(TAG, message)
                            continue
                        }

                        val startedAt = SystemClock.elapsedRealtime()
                        var response: Response<SubirFotoResponse>? = null
                        if (comoArchivo) {
                            response = subirComoArchivo(api, foto, bytes)
                            if (FotosMultipart.sinRutaDeArchivos(response.code())) {
                                Log.i(TAG, "El servidor no recibe fotos como archivo (HTTP ${response.code()}): se mandan como base64.")
                                comoArchivo = false
                                response = null
                            }
                        }
                        if (response == null) {
                            response = api.subirFoto(
                                SubirFotoRequest(
                                    creferencia = foto.crefAgrupada,
                                    cclienteid = foto.cclienteid,
                                    image = Base64.getEncoder().encodeToString(bytes),
                                    extension = "jpg",
                                    esDiscrepancia = foto.esDiscrepancia
                                )
                            )
                        }

                        val body = response.body()
                        if (response.isSuccessful && body?.success == true) {
                            // Primero se deja la fila marcada como sincronizada y
                            // después se retira. Si la segunda escritura falla, la
                            // fila no vuelve a enviarse en el siguiente intento.
                            dao.markAsSynced(foto.id, body.id)
                            dao.deleteFoto(foto.id)
                            runCatching { file.delete() }
                            FotoParaSubir.olvidar(foto.localFilePath)
                            Log.i(
                                TAG,
                                "cref=${foto.crefAgrupada} kb=${bytes.size / 1024} comoArchivo=$comoArchivo " +
                                    "envio=${SystemClock.elapsedRealtime() - startedAt} ms"
                            )
                        } else {
                            val message = response.body()?.message
                                ?: "Error HTTP ${response.code()} al subir la evidencia."
                            val retryable = PhotoSyncPolicy.isRetryableHttp(response.code())
                            dao.markFailed(foto.id, message, blocked = !retryable)
                            if (retryable) hasRetryableFailure = true else hasPermanentFailure = true
                            Log.e(TAG, message)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        hasRetryableFailure = true
                        val message = e.message ?: "Error de red al sincronizar la evidencia."
                        dao.markFailed(foto.id, message, blocked = false)
                        Log.e(TAG, message, e)
                    }
                }

                if (hasRetryableFailure) return Result.retry()
            } while (true)
        } finally {
            // Preparadas de fotos que ya no existen (la app se cerró entre subir y borrar, o se borró la foto).
            carpetas.forEach { runCatching { FotoParaSubir.limpiarHuerfanas(it) } }
        }
    }

    private suspend fun subirComoArchivo(api: PMMovilApi, foto: FotoSyncEntity, bytes: ByteArray): Response<SubirFotoResponse> =
        api.subirFotoArchivo(
            creferencia = FotosMultipart.texto(foto.crefAgrupada),
            cclienteid = FotosMultipart.texto(foto.cclienteid),
            extension = FotosMultipart.texto("jpg"),
            esDiscrepancia = FotosMultipart.texto(foto.esDiscrepancia.toString()),
            foto = FotosMultipart.foto("foto", "${foto.id}.jpg", bytes)
        )

    private companion object {
        const val TAG = "FotoSyncWorker"
    }
}
