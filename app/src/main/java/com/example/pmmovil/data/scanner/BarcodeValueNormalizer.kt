package com.example.pmmovil.data.scanner

object BarcodeValueNormalizer {
    /** Entrada común para todos los lectores: cámara, lector integrado y HID. */
    fun normalizeScan(value: String?): String = normalize(value)

    /** Normalización histórica para búsquedas simples, placas y códigos internos. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""

        return value
            .replace('\u00A0', ' ')
            .filterNot(Char::isISOControl)
            .trim { it <= ' ' || it == '\uFEFF' }
    }

    /**
     * Conserva el separador GS y el identificador AIM de etiquetas estructuradas.
     * El código original debe acompañar a la observación; los separadores forman
     * parte de su significado y no se pueden reconstruir después de borrarlos.
     */
    fun normalizeLabel(value: String?): String {
        if (value.isNullOrBlank()) return ""

        return value
            .replace('\u00A0', ' ')
            .filterNot { it.isISOControl() && it != '\u001D' }
            .trim { (it.isWhitespace() && it != '\u001D') || it == '\uFEFF' }
    }
}
