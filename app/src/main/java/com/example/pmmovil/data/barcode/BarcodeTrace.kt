package com.example.pmmovil.data.barcode

import android.util.Log
import com.example.pmmovil.BuildConfig

/**
 * Traza de diagnóstico de la revisión por barcode.
 *
 * Solo se emite en builds debug. Filtrar Logcat con [TAG] permite reconstruir
 * si una lectura se aceptó localmente, se envió más de una vez o el servidor
 * la devolvió como duplicada.
 */
object BarcodeTrace {
    const val TAG = "PMM_BARCODE_TRACE"

    fun d(message: String) {
        if (BuildConfig.DEBUG || BuildConfig.BARCODE_TRACE_ENABLED) Log.i(TAG, message)
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG || BuildConfig.BARCODE_TRACE_ENABLED) Log.w(TAG, message, throwable)
    }
}
