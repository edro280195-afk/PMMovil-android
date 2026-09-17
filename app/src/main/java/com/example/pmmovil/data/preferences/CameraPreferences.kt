package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Preferencia global (no por entrada) de cómo el usuario prefiere sostener la
 * tableta al fotografiar evidencia: de qué lado quedan el disparador y los
 * controles cuando la pantalla está en horizontal. Zurdos/diestros lo
 * configuran una vez y queda para toda la app.
 *
 * Storage:
 *   "pmm_shutter_side" → "left" | "right"
 */
@Singleton
class CameraPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    enum class ShutterSide { LEFT, RIGHT }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("PMMovilCamera", Context.MODE_PRIVATE)

    fun getShutterSide(): ShutterSide {
        val raw = prefs.getString(KEY_SHUTTER_SIDE, null) ?: return ShutterSide.RIGHT
        return runCatching { ShutterSide.valueOf(raw.uppercase()) }.getOrDefault(ShutterSide.RIGHT)
    }

    fun setShutterSide(side: ShutterSide) {
        prefs.edit { putString(KEY_SHUTTER_SIDE, side.name) }
    }

    private companion object {
        const val KEY_SHUTTER_SIDE = "pmm_shutter_side"
    }
}
