package com.example.pmmovil.data.scanner

import android.content.Intent

enum class HardwareScanSource {
    DATAWEDGE,
    HONEYWELL,
    HID_BRIDGE,
    GENERIC_INTENT
}

data class HardwareScanEvent(
    val value: String,
    val source: HardwareScanSource,
    val sourceLabel: String,
    val labelType: String? = null,
    val intentAction: String? = null
)

object HardwareScannerIntents {
    const val ACTION_PMMOVIL_SCAN = "com.pmmovil.SCAN"
    const val EXTRA_PMMOVIL_SOURCE = "source"
    const val EXTRA_PMMOVIL_LABEL_TYPE = "label_type"

    const val EXTRA_DATAWEDGE_VALUE = "com.symbol.datawedge.data_string"
    const val EXTRA_DATAWEDGE_LABEL_TYPE = "com.symbol.datawedge.label_type"
    const val EXTRA_DATAWEDGE_SOURCE = "com.symbol.datawedge.source"

    val scannerActions = listOf(
        ACTION_PMMOVIL_SCAN,
        "com.symbol.datawedge.data_string",
        "com.symbol.datawedge.ACTION",
        "com.symbol.datawedge.SCANNER_RESULT",
        "com.honeywell.decode.intent.action.EDIT_DATA",
        "com.honeywell.aidc.action.ACTION_BARCODE_READ_EVENT",
        "android.intent.action.SCANRESULT",
        "scan.rcv.message"
    )

    private val barcodeExtraKeys = listOf(
        EXTRA_DATAWEDGE_VALUE,
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
        Intent.EXTRA_TEXT
    )

    fun parseScanEvent(intent: Intent?): HardwareScanEvent? {
        if (intent == null || DataWedgeProfileManager.isResultIntent(intent)) {
            return null
        }

        val value = extractBarcode(intent).takeIf(String::isNotBlank) ?: return null
        val source = detectSource(intent)
        val labelType = intent.getStringExtra(EXTRA_DATAWEDGE_LABEL_TYPE)
            ?: intent.getStringExtra(EXTRA_PMMOVIL_LABEL_TYPE)
        return HardwareScanEvent(
            value = value,
            source = source,
            sourceLabel = source.displayName(labelType),
            labelType = labelType,
            intentAction = intent.action
        )
    }

    private fun extractBarcode(intent: Intent): String {
        barcodeExtraKeys.forEach { key ->
            val value = intent.extras?.get(key)?.toString()
            val clean = BarcodeValueNormalizer.normalize(value)
            if (clean.isNotBlank()) return clean
        }

        val fallback = intent.extras
            ?.keySet()
            ?.asSequence()
            ?.filterNot { key ->
                key == "RESULT_INFO" ||
                    key == "RESULT" ||
                    key == "COMMAND_IDENTIFIER"
            }
            ?.mapNotNull { key -> intent.extras?.get(key)?.toString() }
            ?.firstOrNull { value ->
                val clean = BarcodeValueNormalizer.normalize(value)
                clean.length >= 3 && clean.any(Char::isLetterOrDigit)
            }

        return fallback.orEmpty()
    }

    private fun detectSource(intent: Intent): HardwareScanSource {
        val action = intent.action.orEmpty()
        val dataWedgeSource = intent.getStringExtra(EXTRA_DATAWEDGE_SOURCE)
        val pmmovilSource = intent.getStringExtra(EXTRA_PMMOVIL_SOURCE)
        return when {
            !dataWedgeSource.isNullOrBlank() ||
                intent.hasExtra(EXTRA_DATAWEDGE_VALUE) ||
                action.startsWith("com.symbol.datawedge") -> HardwareScanSource.DATAWEDGE
            action.startsWith("com.honeywell") -> HardwareScanSource.HONEYWELL
            pmmovilSource.equals("HID", ignoreCase = true) -> HardwareScanSource.HID_BRIDGE
            else -> HardwareScanSource.GENERIC_INTENT
        }
    }

    private fun HardwareScanSource.displayName(labelType: String?): String {
        val symbology = labelType
            ?.removePrefix("LABEL-TYPE-")
            ?.replace('_', ' ')
            ?.takeIf(String::isNotBlank)
        return when (this) {
            HardwareScanSource.DATAWEDGE ->
                listOfNotNull("DataWedge", symbology).joinToString(" · ")
            HardwareScanSource.HONEYWELL ->
                listOfNotNull("Honeywell", symbology).joinToString(" · ")
            HardwareScanSource.HID_BRIDGE -> "Lector físico HID"
            HardwareScanSource.GENERIC_INTENT -> "Lector físico"
        }
    }
}
