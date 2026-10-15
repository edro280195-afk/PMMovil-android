package com.example.pmmovil.data.model

import com.example.pmmovil.data.printing.ZebraPrinterConfig
import com.example.pmmovil.data.printing.ZebraPrinterPrintSettings

data class ZebraPrinterDto(
    val id: Int = 0,
    val name: String = "",
    val host: String = "",
    val port: Int = 9100,
    val location: String? = null,
    val terminalName: String? = null,
    val connectTimeoutMillis: Int = 4_000,
    val writeTimeoutMillis: Int = 8_000,
    val printDarkness: Float? = null,
    val printSpeedIps: Float? = null,
    val printMethod: String? = null,
    val mediaType: String? = null,
    val labelTop: Int? = null,
    val leftPosition: Int? = null,
    val isDefault: Boolean = false,
    val isActive: Boolean = true
) {
    val displayName: String
        get() = name.ifBlank { terminalName.orEmpty().ifBlank { "Zebra $host" } }

    val endpoint: String
        get() = "$host:$port"

    fun toConfig(): ZebraPrinterConfig =
        ZebraPrinterConfig(
            printerId = id.takeIf { it > 0 },
            host = host,
            port = port,
            printerName = displayName,
            connectTimeoutMillis = connectTimeoutMillis,
            writeTimeoutMillis = writeTimeoutMillis
        )

    fun toSavedPrintSettings(): ZebraPrinterPrintSettings =
        ZebraPrinterPrintSettings(
            darkness = printDarkness,
            speedIps = printSpeedIps,
            printMethod = printMethod,
            mediaType = mediaType,
            labelTop = labelTop,
            leftPosition = leftPosition
        )
}

data class ZebraPrinterListResponse(
    val success: Boolean = false,
    val message: String = "",
    val items: List<ZebraPrinterDto> = emptyList()
)

data class ZebraPrinterSaveRequest(
    val id: Int = 0,
    val name: String = "",
    val host: String = "",
    val port: Int = 9100,
    val location: String? = null,
    val terminalName: String? = null,
    val connectTimeoutMillis: Int = 4_000,
    val writeTimeoutMillis: Int = 8_000,
    val printDarkness: Float? = null,
    val printSpeedIps: Float? = null,
    val printMethod: String? = null,
    val mediaType: String? = null,
    val labelTop: Int? = null,
    val leftPosition: Int? = null,
    val isActive: Boolean = true
)

data class ZebraPrinterSaveResponse(
    val success: Boolean = false,
    val message: String = "",
    val item: ZebraPrinterDto? = null
)

fun ZebraPrinterConfig.toPrinterDto(): ZebraPrinterDto =
    ZebraPrinterDto(
        id = printerId ?: 0,
        name = printerName.ifBlank { "Zebra local" },
        host = host,
        port = port,
        location = "Configuracion local",
        terminalName = printerName,
        connectTimeoutMillis = connectTimeoutMillis,
        writeTimeoutMillis = writeTimeoutMillis,
        isDefault = false,
        isActive = true
    )

data class ZebraLabelLayoutDto(
    val id: Int = 0,
    val layoutType: String = "",
    val layoutJson: String = "",
    val version: Int = 1,
    val updatedBy: String? = null,
    val updatedAt: String? = null
)

data class ZebraLabelLayoutListResponse(
    val success: Boolean = false,
    val message: String = "",
    val items: List<ZebraLabelLayoutDto> = emptyList()
)

data class ZebraLabelLayoutSaveRequest(
    val layoutType: String,
    val layoutJson: String
)

data class ZebraLabelLayoutSaveResponse(
    val success: Boolean = false,
    val message: String = "",
    val item: ZebraLabelLayoutDto? = null
)

