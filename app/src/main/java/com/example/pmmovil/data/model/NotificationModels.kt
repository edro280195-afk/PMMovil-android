package com.example.pmmovil.data.model

/**
 * Registro de un token FCM del dispositivo. "platform" es informativo para
 * el backend (filtros/depuración); el envío en sí es el mismo para Android
 * y Web -- una sola llamada de FirebaseMessaging.SendAsync por token.
 */
data class FcmTokenRequest(
    val token: String,
    val platform: String = "android"
)

data class FcmUnregisterRequest(
    val token: String
)

data class NotificationInboxItem(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val crefAgrupada: String? = null,
    val createdAt: String? = null,
    val readAt: String? = null
)

data class NotificationInboxResponse(
    val success: Boolean = false,
    val unreadCount: Int = 0,
    val items: List<NotificationInboxItem> = emptyList()
)

data class NotificationActionResponse(
    val success: Boolean = false,
    val actualizadas: Int = 0
)
