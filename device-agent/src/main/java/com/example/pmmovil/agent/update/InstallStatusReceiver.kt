package com.example.pmmovil.agent.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.example.pmmovil.agent.AgentConstants
import java.io.File

class InstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?: "sin detalle"
        val state = AgentStateStore(context)
        state.clearInstallationInProgress()

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                state.recordResult("instalación completada")
                intent.getStringExtra(EXTRA_APK_PATH)?.let { File(it).delete() }
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val userActionIntent = getUserActionIntent(intent)
                if (userActionIntent == null) {
                    state.recordError("Android solicitó interacción, pero no entregó la pantalla: $message")
                } else {
                    userActionIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(userActionIntent)
                        state.recordResult("actualización pendiente de confirmación")
                    } catch (exception: Exception) {
                        state.recordError(
                            "No se pudo abrir la confirmación de instalación: " +
                                (exception.message ?: message)
                        )
                    }
                }
            }

            else -> {
                val requestedVersionCode = intent.getLongExtra(EXTRA_VERSION_CODE, 0L)
                val installedVersionCode = runCatching {
                    @Suppress("DEPRECATION")
                    context.packageManager
                        .getPackageInfo(AgentConstants.PMMOVIL_PACKAGE, 0)
                        .longVersionCode
                }.getOrDefault(0L)

                if (requestedVersionCode > 0L && installedVersionCode >= requestedVersionCode) {
                    state.recordResult("instalación completada")
                    intent.getStringExtra(EXTRA_APK_PATH)?.let { File(it).delete() }
                } else {
                    state.recordError("Instalación fallida ($status): $message")
                }
            }
        }
    }

    private fun getUserActionIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "com.example.pmmovil.agent.INSTALL_STATUS"
        const val EXTRA_VERSION_CODE = "versionCode"
        const val EXTRA_APK_PATH = "apkPath"
    }
}
