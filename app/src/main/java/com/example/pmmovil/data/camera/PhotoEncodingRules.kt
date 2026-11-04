package com.example.pmmovil.data.camera

/** Reglas para no confirmar una salida con evidencia fotográfica parcial. */
object PhotoEncodingRules {
    fun requireAllEncoded(encodedPhotos: List<String?>): List<String> {
        val failedIndex = encodedPhotos.indexOfFirst { it.isNullOrBlank() }
        if (failedIndex >= 0) {
            throw IllegalStateException(
                "No se pudo preparar la foto ${failedIndex + 1}. Vuelve a tomarla o elimínala antes de confirmar la salida."
            )
        }

        return encodedPhotos.map { checkNotNull(it) }
    }
}
