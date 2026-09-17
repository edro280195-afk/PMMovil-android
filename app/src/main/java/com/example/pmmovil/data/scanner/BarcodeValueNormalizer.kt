package com.example.pmmovil.data.scanner

object BarcodeValueNormalizer {
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""

        return value
            .replace('\u00A0', ' ')
            .filterNot(Char::isISOControl)
            .trim { it <= ' ' || it == '\uFEFF' }
    }
}
