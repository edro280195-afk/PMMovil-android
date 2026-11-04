package com.example.pmmovil.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.pmmovil.worker.EntradaPhotoSyncWorker
import com.example.pmmovil.worker.CasetaEntradaPhotoSyncWorker
import com.example.pmmovil.worker.FotoSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    fun scheduleRevisionPhotos() {
        val request = OneTimeWorkRequestBuilder<FotoSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_revision_photos",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleEntradaPhotos() {
        val request = OneTimeWorkRequestBuilder<EntradaPhotoSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_entrada_photos",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Pone a subir las fotos de caseta que estan en cola. Con una ejecucion en curso no la toca; pero si la anterior
     * fallo y esta ESPERANDO su reintento (30 s, 1, 2, 4... min, hasta horas), las fotos nuevas y el aviso por correo
     * que cuelgan de ellas esperarian detras de ese tiempo: ahi se reemplaza por una ejecucion inmediata.
     */
    suspend fun scheduleCasetaEntradaPhotos() {
        val manager = WorkManager.getInstance(context)
        val esperandoReintento = withContext(Dispatchers.IO) {
            runCatching {
                manager.getWorkInfosForUniqueWork(CASETA_PHOTOS_WORK).get()
                    .any { it.state == WorkInfo.State.ENQUEUED && it.runAttemptCount > 0 }
            }.getOrDefault(false)
        }

        val request = OneTimeWorkRequestBuilder<CasetaEntradaPhotoSyncWorker>()
            .setConstraints(networkConstraints)
            // 10 s es lo minimo que admite WorkManager: una foto de caseta no debe esperar medio minuto por un tropiezo de red.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        manager.enqueueUniqueWork(
            CASETA_PHOTOS_WORK,
            if (esperandoReintento) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val CASETA_PHOTOS_WORK = "sync_caseta_entrada_photos"
    }
}
