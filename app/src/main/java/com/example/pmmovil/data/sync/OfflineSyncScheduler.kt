package com.example.pmmovil.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pmmovil.worker.EntradaPhotoSyncWorker
import com.example.pmmovil.worker.CasetaEntradaPhotoSyncWorker
import com.example.pmmovil.worker.FotoSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
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

    fun scheduleCasetaEntradaPhotos() {
        val request = OneTimeWorkRequestBuilder<CasetaEntradaPhotoSyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "sync_caseta_entrada_photos",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}
