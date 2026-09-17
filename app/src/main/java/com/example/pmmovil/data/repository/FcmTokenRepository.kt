package com.example.pmmovil.data.repository

import android.util.Log
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.FcmTokenRequest
import com.example.pmmovil.data.model.FcmUnregisterRequest
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registra/retira el token FCM de este dispositivo contra el backend.
 *
 * El token se obtiene una sola vez por instalación (o cuando Firebase lo
 * rota -- ver PmmFirebaseMessagingService.onNewToken) y se asocia al usuario
 * que tiene la sesión activa en ESE momento. Por eso se debe reintentar
 * después de cada login (un dispositivo compartido puede cambiar de dueño).
 */
@Singleton
class FcmTokenRepository @Inject constructor(
    private val api: PMMovilApi
) {
    suspend fun registerCurrentToken(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            registerToken(token).getOrThrow()
        }
    }

    suspend fun registerToken(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (token.isBlank()) throw Exception("Token FCM vacío")
            val r = api.registerFcmToken(FcmTokenRequest(token = token, platform = "android"))
            if (!(r.isSuccessful && r.body()?.success == true)) {
                throw Exception("No se pudo registrar el token FCM (HTTP ${r.code()})")
            }
        }.onFailure { e ->
            Log.w("FcmTokenRepository", "No se pudo registrar token FCM: ${e.message}")
        }
    }

    suspend fun unregisterCurrentToken(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            api.unregisterFcmToken(FcmUnregisterRequest(token))
            Unit
        }.onFailure { e ->
            Log.w("FcmTokenRepository", "No se pudo retirar token FCM: ${e.message}")
        }
    }
}
