package com.example.pmmovil.agent.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.pmmovil.agent.AgentConstants
import java.io.File
import java.io.FileInputStream

class ApkInstaller(private val context: Context) {

    fun install(apkFile: File, release: ReleaseManifest) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(AgentConstants.PMMOVIL_PACKAGE)
            setInstallReason(android.content.pm.PackageManager.INSTALL_REASON_POLICY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }

        val sessionId = packageInstaller.createSession(params)
        var committed = false
        try {
            packageInstaller.openSession(sessionId).use { session ->
                FileInputStream(apkFile).use { input ->
                    session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }

                val statusIntent = Intent(context, InstallStatusReceiver::class.java).apply {
                    action = InstallStatusReceiver.ACTION_INSTALL_STATUS
                    setPackage(context.packageName)
                    putExtra(InstallStatusReceiver.EXTRA_VERSION_CODE, release.versionCode)
                    putExtra(InstallStatusReceiver.EXTRA_APK_PATH, apkFile.absolutePath)
                }
                val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        PendingIntent.FLAG_MUTABLE
                    } else {
                        0
                    }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    statusIntent,
                    pendingIntentFlags
                )
                session.commit(pendingIntent.intentSender)
                committed = true
            }
        } finally {
            if (!committed) packageInstaller.abandonSession(sessionId)
        }
    }
}
