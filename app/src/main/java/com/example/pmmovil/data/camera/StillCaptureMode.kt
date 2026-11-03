package com.example.pmmovil.data.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraInfo
import androidx.camera.core.ImageCapture
import com.example.pmmovil.BuildConfig
import java.io.File

/**
 * Modo de captura de la camara de evidencia (Entrada, Caseta, Salida y Revision).
 *
 * Cada modo cambia cuanto espera la camara entre el toque y el obturador, y cuanto procesa despues:
 *  - MAXIMIZE_QUALITY: antes de disparar hace una secuencia de enfoque y exposicion (con plazo de hasta ~1 s si el enfoque
 *    no cierra) y pide al procesador de imagen el camino completo. Es el que mas tarda.
 *  - MINIMIZE_LATENCY: dispara casi al instante, con menos procesamiento.
 *  - ZERO_SHUTTER_LAG: toma la foto de un buffer de cuadros que la camara ya tenia; si el aparato no lo soporta CameraX
 *    cae solo a MINIMIZE_LATENCY.
 *
 * Por omision va MINIMIZE_LATENCY, junto con la espera de enfoque de [FocusGate]. Medido el 3-nov-2026 en un Pixel 9 Pro
 * XL, con el mismo cartel QR de cerca y el telefono en la mano:
 *  - del toque al obturador: ~175 ms contra ~430 ms (y hasta 1345 ms en otras escenas) con MAXIMIZE_QUALITY;
 *  - listo para la siguiente foto: ~464 ms contra ~668 ms;
 *  - nitidez del QR a 100 %: 10 de 14 fotos nitidas contra 1 de 11 (p = 0.003). El enfoque previo de MAXIMIZE_QUALITY
 *    dejaba el cartel blando de forma sistematica; el enfoque continuo del visor lo tenia bien.
 * Solo se midio en ese telefono: falta verlo en las tabletas (Samsung, Tab A11+, Armor). Para volver al modo anterior
 * basta cambiar [DEFAULT].
 *
 * En un build debug se puede forzar el modo sin recompilar, para comparar en el aparato: crear
 * `Android/data/com.example.pmmovil/files/pmm_capture_mode.txt` con `max`, `min` o `zsl` y volver a abrir la camara.
 * En release siempre manda [DEFAULT].
 */
object StillCaptureMode {
    private const val TAG = "PMM-STILL"
    private const val OVERRIDE_FILE = "pmm_capture_mode.txt"

    const val DEFAULT = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY

    fun resolve(context: Context): Int {
        if (!BuildConfig.DEBUG) return DEFAULT
        val text = runCatching {
            val file = File(context.getExternalFilesDir(null), OVERRIDE_FILE)
            if (file.exists()) file.readText().trim().lowercase() else null
        }.getOrNull()
        val mode = when (text) {
            "max" -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
            "min" -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
            "zsl" -> ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG
            else -> DEFAULT
        }
        Log.i(TAG, "modo de captura: ${name(mode)}${if (text != null) " (forzado por archivo)" else ""}")
        return mode
    }

    /** Anota en el log si el aparato soporta ZSL: decide si vale la pena usarlo ahi. */
    fun logSupport(info: CameraInfo) {
        Log.i(TAG, "ZSL soportado por la camara: ${info.isZslSupported}")
    }

    fun name(mode: Int): String = when (mode) {
        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY -> "MAXIMIZE_QUALITY"
        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY -> "MINIMIZE_LATENCY"
        ImageCapture.CAPTURE_MODE_ZERO_SHUTTER_LAG -> "ZERO_SHUTTER_LAG"
        else -> "desconocido($mode)"
    }
}
