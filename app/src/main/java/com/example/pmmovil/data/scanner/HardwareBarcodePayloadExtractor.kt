package com.example.pmmovil.data.scanner

import java.nio.charset.StandardCharsets

data class ExtractedHardwareBarcode(
    val value: String,
    val extraKey: String
)

/** Extrae sólo valores de código, sin convertir objetos arbitrarios a texto. */
object HardwareBarcodePayloadExtractor {
    private val preferredExtraKeys = listOf(
        "com.symbol.datawedge.data_string",
        "data",
        "barcode",
        "barocode",
        "value",
        "code",
        "scannerdata",
        "scan_result",
        "SCAN_BARCODE1",
        "com.motorolasolutions.emdk.datawedge.data_string",
        "com.honeywell.decode.intent.extra.BARCODE_DATA",
        "android.intent.extra.TEXT",
        "dataBytes",
        "barcodeBytes",
        "com.honeywell.decode.intent.extra.BARCODE_DATA_BYTES"
    )

    private val metadataKeyParts = listOf(
        "aimid",
        "codeid",
        "labeltype",
        "symbology",
        "timestamp",
        "source",
        "resultinfo",
        "command",
        "length"
    )

    fun extract(extras: Map<String, Any?>): ExtractedHardwareBarcode? {
        preferredExtraKeys.forEach { key ->
            val value = extras[key]?.toBarcodeText() ?: return@forEach
            val normalized = BarcodeValueNormalizer.normalizeScan(value)
            if (normalized.isNotBlank()) {
                return ExtractedHardwareBarcode(normalized, key)
            }
        }

        // Conserva compatibilidad con lectores genéricos que usan otro nombre
        // de extra, pero ignora los metadatos y escoge el texto más probable.
        return extras.entries
            .asSequence()
            .filterNot { (key, _) -> isMetadataKey(key) }
            .mapNotNull { (key, value) ->
                val normalized = value
                    ?.takeIf { it !is ByteArray }
                    ?.toBarcodeText()
                    ?.let(BarcodeValueNormalizer::normalizeScan)
                    ?.takeIf { candidate ->
                        candidate.length >= 3 && candidate.any(Char::isLetterOrDigit)
                    }
                    ?: return@mapNotNull null
                ExtractedHardwareBarcode(normalized, key)
            }
            .maxByOrNull { it.value.length }
    }

    private fun Any.toBarcodeText(): String? = when (this) {
        is String -> this
        is CharSequence -> toString()
        is ByteArray -> decodeBytes(this)
        else -> null
    }

    private fun decodeBytes(bytes: ByteArray): String {
        val utf8 = String(bytes, StandardCharsets.UTF_8)
        return if ('\uFFFD' in utf8) {
            String(bytes, StandardCharsets.ISO_8859_1)
        } else {
            utf8
        }
    }

    private fun isMetadataKey(key: String): Boolean {
        val normalized = key.lowercase()
        return metadataKeyParts.any(normalized::contains)
    }
}
