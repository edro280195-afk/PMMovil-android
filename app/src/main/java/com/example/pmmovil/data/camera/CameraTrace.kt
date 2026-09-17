package com.example.pmmovil.data.camera

import android.util.Log
import com.example.pmmovil.BuildConfig

/**
 * Traza de diagnóstico de la cámara (arranque, exposición, enfoque).
 *
 * Solo se emite en builds debug o con `BARCODE_TRACE_ENABLED`. Filtrar Logcat
 * con [TAG] permite reconstruir, cuadro a cuadro, cómo converge la exposición
 * automática del equipo tras abrir la cámara: es la evidencia con la que se
 * diagnosticó el arranque en negro/blanco de la Ulefone Armor Pad Pro.
 */
object CameraTrace {
    const val TAG = "PMM_CAMERA_TRACE"

    val enabled: Boolean
        get() = BuildConfig.DEBUG || BuildConfig.BARCODE_TRACE_ENABLED

    fun d(message: String) {
        if (enabled) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (enabled) Log.w(TAG, message, throwable)
    }
}
