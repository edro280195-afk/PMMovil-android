package com.example.pmmovil.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.pmmovil.MainActivity
import com.example.pmmovil.R
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.FcmTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

const val PMM_NOTIFICATION_CHANNEL_ID = "pmm_revision"

/**
 * Recibe los pushes de Firebase. Es el respaldo "siempre llega" para avisos
 * como Revisión asignada / Entrada autorizada: SignalR ya los muestra en
 * tiempo real mientras la app está abierta, pero se cae si el sistema mata
 * la conexión en segundo plano o la app está cerrada -- este servicio cubre
 * justo ese hueco con una notificación real del sistema.
 */
@AndroidEntryPoint
class PmmFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var fcmTokenRepository: FcmTokenRepository
    @Inject lateinit var authRepository: AuthRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // El token puede rotar en cualquier momento (reinstalación, cambio de
        // cuenta de Google, limpieza de storage). Solo tiene sentido mandarlo
        // si hay alguien logueado en este dispositivo ahora mismo.
        if (authRepository.hasActiveSession()) {
            scope.launch { fcmTokenRepository.registerToken(token) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        // El servidor sólo debe enviar a tokens con sesión activa. Esta
        // segunda barrera evita mostrar un push atrasado si Firebase lo
        // entrega justo después de cerrar sesión en el dispositivo.
        if (!authRepository.hasActiveSession()) {
            return
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "PMMovil"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: return

        showNotification(title, body, message.data["type"], message.data["crefAgrupada"])
    }

    private fun showNotification(title: String, body: String, type: String?, crefAgrupada: String?) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            type?.let { putExtra("notification_type", it) }
            crefAgrupada?.let { putExtra("crefAgrupada", it) }
        }
        val pendingIntentCode = listOf(type.orEmpty(), crefAgrupada.orEmpty(), title)
            .joinToString("|")
            .hashCode()
        val pendingIntent = PendingIntent.getActivity(
            this,
            pendingIntentCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PMM_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = NotificationManagerCompat.from(this)
        // El permiso POST_NOTIFICATIONS (Android 13+) se pide al abrir la app;
        // si el usuario lo negó, el push igual llegó (se ve en la bandeja de la
        // app al volver), simplemente no hay bandeja del sistema.
        val hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasNotificationPermission || !manager.areNotificationsEnabled()) return

        runCatching { manager.notify(System.currentTimeMillis().toInt(), notification) }
    }

    companion object {
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                PMM_NOTIFICATION_CHANNEL_ID,
                "Revisión de mercancía",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Asignaciones y autorizaciones de revisión"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
