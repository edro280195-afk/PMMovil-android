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
    val intentAction: String? = null,
    val dataExtraKey: String? = null
)

object HardwareScannerIntents {
    const val ACTION_PMMOVIL_SCAN = "com.pmmovil.SCAN"
    const val ACTION_HONEYWELL_SCAN_RESULT = "com.honeywell.intent.action.SCAN_RESULT"
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
        ACTION_HONEYWELL_SCAN_RESULT,
        "com.honeywell.decode.intent.action.EDIT_DATA",
        "com.honeywell.aidc.action.ACTION_BARCODE_READ_EVENT",
        "android.intent.action.SCANRESULT",
        "scan.rcv.message"
    )

    fun parseScanEvent(intent: Intent?): HardwareScanEvent? {
        if (intent == null || DataWedgeProfileManager.isResultIntent(intent)) {
            return null
        }

        val extras = intent.extras?.let { bundle ->
            bundle.keySet().associateWith { key -> bundle.get(key) }
        }.orEmpty()
        val extracted = HardwareBarcodePayloadExtractor.extract(extras) ?: return null
        val source = detectSource(intent)
        val labelType = intent.getStringExtra(EXTRA_DATAWEDGE_LABEL_TYPE)
            ?: intent.getStringExtra(EXTRA_PMMOVIL_LABEL_TYPE)
        return HardwareScanEvent(
            value = extracted.value,
            source = source,
            sourceLabel = source.displayName(labelType),
            labelType = labelType,
            intentAction = intent.action,
            dataExtraKey = extracted.extraKey
        )
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
