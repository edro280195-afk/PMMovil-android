package com.example.pmmovil.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.EntradaPhotoSyncDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

/**
 * Avisa al servidor que terminó la captura de una entrada (el usuario pulsó «Finalizar y volver»). El API manda entonces el
 * aviso de PRUEBA que llega solo a desarrollo3: PMMovil no manda la notificación de entrada al cliente (la manda el sistema
 * PMM). Corre en segundo plano para que no se pierda si el usuario sale de la pantalla o se queda sin red, y espera a que
 * terminen de subir las fotos de ESA entrada (hasta 15 min) para que el correo y su enlace las traigan.
 */
class EntradaCaptureNoticeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface EntradaCaptureNoticeWorkerEntryPoint {
        fun entradaPhotoSyncDao(): EntradaPhotoSyncDao
        fun pmMovilApi(): PMMovilApi
    }

    override suspend fun doWork(): Result {
        val reference = inputData.getString(KEY_REFERENCE)?.takeIf { it.isNotBlank() } ?: return Result.success()
        val isEdit = inputData.getBoolean(KEY_EDIT, false)
        val enqueuedAt = inputData.getLong(KEY_ENQUEUED_AT, System.currentTimeMillis())
        val elapsed = (System.currentTimeMillis() - enqueuedAt).coerceAtLeast(0L)

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            EntradaCaptureNoticeWorkerEntryPoint::class.java
        )

        // Las fotos primero: solo cuentan las de esta entrada que de verdad están por subir (las bloqueadas no estorban).
        val pending = entryPoint.entradaPhotoSyncDao().getPendingOnce().count { it.reference == reference }
        if (EntradaCaptureNoticePolicy.shouldWaitForPhotos(pending, elapsed)) {
            return Result.retry()
        }

        val outcome = try {
            val response = entryPoint.pmMovilApi().sendEntradaCaptureNotice(reference, isEdit)
            if (!response.isSuccessful) {
                Log.w(TAG, "Aviso de captura de $reference: HTTP ${response.code()}")
            }
            EntradaCaptureNoticePolicy.outcomeForHttp(response.code(), elapsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Aviso de captura de $reference: ${e.message}")
            EntradaCaptureNoticePolicy.outcomeForNetworkFailure(elapsed)
        }

        return when (outcome) {
            EntradaCaptureNoticePolicy.Outcome.RETRY -> Result.retry()
            EntradaCaptureNoticePolicy.Outcome.DONE,
            EntradaCaptureNoticePolicy.Outcome.GIVE_UP -> Result.success()
        }
    }

    companion object {
        const val KEY_REFERENCE = "reference"
        const val KEY_EDIT = "edit"
        const val KEY_ENQUEUED_AT = "enqueuedAt"
        private const val TAG = "EntradaCaptureNotice"
    }
}
