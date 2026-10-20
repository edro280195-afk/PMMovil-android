package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Modo de revisión preferido por entrada (Normal, Barcode o Escaneo de etiquetas).
 *
 * Guarda la última elección del usuario para que la próxima vez que abra
 * esa misma entrada, la app sepa qué modo sugerir (sin modal).
 *
 * Storage:
 *   "pmm_last_mode_{crefAgrupada}" → "normal" | "barcode" | "labels"
 */
@Singleton
class ModePreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    /**
     * [LABELS] no es una pantalla aparte: abre la misma revisión que [NORMAL] (mismas partidas, cronómetro,
     * candados y cierre) con la cámara de etiqueta ya abierta. Por eso se puede cambiar de uno a otro dentro de la
     * misma entrada, a diferencia de [BARCODE], que lleva su propia cuenta.
     */
    enum class Mode { NORMAL, BARCODE, LABELS }

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
