package com.example.pmmovil.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.data.repository.LabelCaptureRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class LabelCaptureSyncWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun labelCaptureRepository(): LabelCaptureRepository
    }

    override suspend fun doWork(): Result {
        val captureId = inputData.getString("captureId") ?: return Result.failure()
        val repository = EntryPointAccessors.fromApplication(applicationContext, Dependencies::class.java)
            .labelCaptureRepository()
        return if (repository.sync(captureId)) Result.retry() else Result.success()
    }
}
