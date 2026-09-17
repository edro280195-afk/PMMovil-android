package com.example.pmmovil.worker

/** Reglas comunes para decidir si un fallo de subida merece reintento. */
internal object PhotoSyncPolicy {
    fun isRetryableHttp(code: Int): Boolean = code == 408 || code == 429 || code >= 500
}
