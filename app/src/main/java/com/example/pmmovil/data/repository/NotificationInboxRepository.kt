package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.NotificationActionResponse
import com.example.pmmovil.data.model.NotificationInboxResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationInboxRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun getInbox(
        onlyUnread: Boolean = false,
        limit: Int = 50
    ): Result<NotificationInboxResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.getNotificationInbox(onlyUnread = onlyUnread, limit = limit)
            val body = response.body()
            if (!response.isSuccessful || body?.success != true) {
                throw Exception("No se pudo cargar la bandeja de notificaciones (HTTP ${response.code()})")
            }
            body
        }
    }

    suspend fun markRead(id: Long): Result<NotificationActionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.markNotificationRead(id)
            val body = response.body()
            if (!response.isSuccessful || body?.success != true) {
                throw Exception("No se pudo marcar la notificación como leída (HTTP ${response.code()})")
            }
            body
        }
    }

    suspend fun markAllRead(): Result<NotificationActionResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val response = api.markAllNotificationsRead()
            val body = response.body()
            if (!response.isSuccessful || body?.success != true) {
                throw Exception("No se pudieron marcar las notificaciones como leídas (HTTP ${response.code()})")
            }
            body
        }
    }
}
