package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modo de revisión preferido por entrada (Normal vs Barcode).
 *
 * Guarda la última elección del usuario para que la próxima vez que abra
 * esa misma entrada, la app sepa qué modo sugerir (sin modal).
 *
 * Storage:
 *   "pmm_last_mode_{crefAgrupada}" → "normal" | "barcode"
 */
@Singleton
class ModePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    enum class Mode { NORMAL, BARCODE }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PMMovilModes", Context.MODE_PRIVATE)

    fun get(crefAgrupada: String): Mode {
        val raw = prefs.getString(key(crefAgrupada), null) ?: return Mode.NORMAL
        return runCatching { Mode.valueOf(raw.uppercase()) }.getOrDefault(Mode.NORMAL)
    }

    fun set(crefAgrupada: String, mode: Mode) {
        prefs.edit { putString(key(crefAgrupada), mode.name) }
    }

    private fun key(crefAgrupada: String) = "pmm_last_mode_$crefAgrupada"
}
