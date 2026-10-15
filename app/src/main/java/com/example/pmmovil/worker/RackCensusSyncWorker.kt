package com.example.pmmovil.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.repository.RackCensusRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Sube las lecturas del barrido. El mismo `observationId` hace idempotente el reintento. */
class RackCensusSyncWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun rackCensusRepository(): RackCensusRepository
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString("sessionId") ?: return Result.failure()
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .rackCensusRepository()
        return if (repository.syncObservations(sessionId)) Result.retry() else Result.success()
    }
}

/** Envía la conciliación congelada. No rearma la petición en cada intento. */
class RackCensusReconcileWorker(context: Context, parameters: WorkerParameters) :
    CoroutineWorker(context, parameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun rackCensusRepository(): RackCensusRepository
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getString("sessionId") ?: return Result.failure()
        val repository = EntryPointAccessors
            .fromApplication(applicationContext, Dependencies::class.java)
            .rackCensusRepository()
        return if (repository.syncReconcile(sessionId)) Result.retry() else Result.success()
    }
}
