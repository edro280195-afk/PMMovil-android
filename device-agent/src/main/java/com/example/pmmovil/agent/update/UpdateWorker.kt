package com.example.pmmovil.agent.update

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pmmovil.agent.AgentConstants
import com.example.pmmovil.agent.BuildConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdateWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = updateMutex.withLock {
        doWorkLocked()
    }

    private suspend fun doWorkLocked(): Result {
        val state = AgentStateStore(applicationContext)
        if (state.installationInProgressVersionCode != null) return Result.success()
        state.recordCheckStarted()

        return runCatching {
            currentCoroutineContext().ensureActive()
            val currentVersionCode = currentPmmovilVersionCode()
            val currentVersionName = currentPmmovilVersionName()
            val client = UpdateApiClient(applicationContext)
            runCatching {
                client.sendHeartbeat(currentVersionCode, currentVersionName)
            }
            val release = client.fetchReleaseManifest()
            if (release.versionCode <= currentVersionCode) {
                state.recordResult("sin actualización")
                return@runCatching Result.success()
            }
            val endpointHost = java.net.URI(BuildConfig.PMM_UPDATE_BASE_URL).host
                ?: error("La URL base del agente no tiene host")

            ReleaseManifestValidator.validate(
                release = release,
                currentVersionCode = currentVersionCode,
                expectedPackageName = AgentConstants.PMMOVIL_PACKAGE,
                expectedHost = endpointHost
            )
            currentCoroutineContext().ensureActive()

            val apkFile = client.downloadApk(release)
            ApkIntegrityVerifier(applicationContext).verify(
                apkFile = apkFile,
                release = release,
                expectedCertificateSha256 = BuildConfig.PMMOVIL_SIGNING_CERTIFICATE_SHA256
            )
            currentCoroutineContext().ensureActive()

            state.recordInstallationRequested(release.versionCode)
            ApkInstaller(applicationContext).install(apkFile, release)
            Result.success()
        }.getOrElse { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            state.clearInstallationInProgress()
            state.recordError(error.message ?: error.javaClass.simpleName)
            Result.retry()
        }
    }

    @Suppress("DEPRECATION")
    private fun currentPmmovilVersionCode(): Long = runCatching {
        applicationContext.packageManager
            .getPackageInfo(AgentConstants.PMMOVIL_PACKAGE, 0)
            .longVersionCode
    }.getOrDefault(0L)

    @Suppress("DEPRECATION")
    private fun currentPmmovilVersionName(): String? = runCatching {
        applicationContext.packageManager
            .getPackageInfo(AgentConstants.PMMOVIL_PACKAGE, 0)
            .versionName
    }.getOrNull()

    companion object {
        private val updateMutex = Mutex()
    }
}
