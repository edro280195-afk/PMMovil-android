package com.example.pmmovil.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferencesFactory {
    private const val LEGACY_PREFS = "PMMovilPrefs"
    private const val SECURE_PREFS = "PMMovilSecurePrefs"

    private val sensitiveKeys = setOf(
        "AUTH_TOKEN",
        "AUTH_USER",
        "AUTH_NAME",
        "AUTH_LUGAR",
        "AUTH_PLANTA"
    )

    fun create(context: Context): SharedPreferences {
        val appContext = context.applicationContext
        return runCatching {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val securePrefs = EncryptedSharedPreferences.create(
                appContext,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateLegacySession(appContext, securePrefs)
            securePrefs
        }.getOrElse { error ->
            // Nunca degradar a SharedPreferences sin cifrar: eso expondria el
            // token JWT precisamente cuando el Keystore del dispositivo falla.
            throw IllegalStateException(
                "No se pudo inicializar el almacenamiento seguro de la sesion",
                error
            )
        }
    }

    private fun migrateLegacySession(
        context: Context,
        securePrefs: SharedPreferences
    ) {
        val legacyPrefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (legacyPrefs.all.isEmpty()) return

        securePrefs.edit {
            legacyPrefs.all.forEach { (key, value) ->
                if (securePrefs.contains(key)) return@forEach
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(key, value as? Set<String>)
                    }
                }
            }
        }

        legacyPrefs.edit {
            sensitiveKeys.forEach(::remove)
        }
    }
}
