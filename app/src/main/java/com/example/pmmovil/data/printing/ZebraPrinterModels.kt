package com.example.pmmovil.data.printing

import java.util.Locale

data class ZebraPrinterConfig(
    val printerId: Int? = null,
    val host: String = "192.168.20.37",
    val port: Int = 9100,
    val printerName: String = "Zebra ZT411",
    val connectTimeoutMillis: Int = 4_000,
    val writeTimeoutMillis: Int = 8_000
) {
    val isHostConfigured: Boolean get() = host.isNotBlank()
}

/**
 * Ajustes de impresión que se pueden consultar y modificar sin tocar red, seguridad,
 * calibración ni sensores. Son valores globales de la impresora, no de una sola etiqueta.
 */
data class ZebraPrinterPrintSettings(
    val darkness: Float? = null,
    val speedIps: Float? = null,
    val printMethod: String? = null,
    val mediaType: String? = null,
    val labelTop: Int? = null,
    val leftPosition: Int? = null
) {
    val hasAnyValue: Boolean
        get() = listOf(
            darkness,
            speedIps,
            printMethod,
            mediaType,
            labelTop,
            leftPosition
        ).any { it != null }
}

data class ZebraPrinterLiveSettings(
    val productName: String? = null,
    val firmwareVersion: String? = null,
    val printheadResolutionDpi: Int? = null,
    val printSettings: ZebraPrinterPrintSettings,
    val unavailableSettings: List<String> = emptyList()
)

sealed class ZebraPrinterSettingsResult {
    data class Success(val settings: ZebraPrinterLiveSettings) : ZebraPrinterSettingsResult()
    data class Failure(val message: String, val cause: Throwable? = null) : ZebraPrinterSettingsResult()

    val isSuccess: Boolean get() = this is Success
}

/** Valores y formatos que reconoce la familia ZT411 para las preferencias administradas. */
object ZebraPrinterSettingsValidation {
    private val allowedPrintMethods = setOf("thermal_transfer", "direct_thermal")
    private val allowedMediaTypes = setOf("continuous", "gap_notch", "mark")

    fun validate(settings: ZebraPrinterPrintSettings): String? {
        val darkness = settings.darkness
        if (darkness == null || darkness !in 0f..30f) {
            return "La oscuridad debe estar entre 0.0 y 30.0."
        }

        val speed = settings.speedIps
        if (speed == null || speed !in 2f..6f) {
            return "La velocidad para Zebra ZT411 debe estar entre 2 y 6 ips."
        }

        if (normalizePrintMethod(settings.printMethod) == null) {
            return "Selecciona metodo de impresion: transferencia termica o termica directa."
        }

        if (normalizeMediaType(settings.mediaType) == null) {
            return "Selecciona tipo de material: continuo, separacion/muesca o marca."
        }

        val labelTop = settings.labelTop
        if (labelTop == null || labelTop !in -120..120) {
            return "El ajuste vertical debe estar entre -120 y 120 puntos."
        }

        val leftPosition = settings.leftPosition
        if (leftPosition == null || leftPosition !in -9_999..9_999) {
            return "El ajuste horizontal debe estar entre -9999 y 9999 puntos."
        }

        return null
    }

    fun normalizePrintMethod(value: String?): String? = when (
        value.orEmpty().trim().lowercase(Locale.US).replace(' ', '_')
    ) {
        "thermal_transfer" -> "thermal_transfer"
        "direct_thermal" -> "direct_thermal"
        else -> null
    }

    fun normalizeMediaType(value: String?): String? = when (
        value.orEmpty().trim().lowercase(Locale.US).replace('/', '_').replace(' ', '_')
    ) {
        "continuous" -> "continuous"
        "gap_notch" -> "gap_notch"
        "mark" -> "mark"
        else -> null
    }
}

data class ZebraLabelData(
    val numeroEntrada: String,
    val fechaArribo: String,
    val cliente: String,
    val caja: String = "",
    val guia: String = "",
    val ubicaciones: String = "",
    val qrValue: String,
    val parcial: String = "",
    val tipoEtiqueta: String = "",
    val copies: Int = 1,
    /** NOMBRE de quien recibió la mercancía (nunca la clave de usuario). Vacío = la etiqueta no lo imprime. */
    val recibio: String = ""
)

/** Avance de un envío largo, para que la pantalla diga qué está pasando en vez de un círculo sin explicación. */
sealed interface ZebraSendProgress {
    /** Se están dibujando las etiquetas (en una tableta lenta, 20 etiquetas tardan varios segundos). */
    data class Preparing(val done: Int, val total: Int) : ZebraSendProgress

    /** La Zebra no contesta todavía (atiende otro envío): se espera su turno sin mandarle nada. */
    data class WaitingForPrinter(val waitedSeconds: Int) : ZebraSendProgress

    /** Datos aceptados por la red; la Zebra los va imprimiendo a su ritmo. */
    data class Sending(val percent: Int, val labelsSent: Int, val labelCount: Int) : ZebraSendProgress
}

data class ZebraPrintStats(
    val host: String,
    val port: Int,
    val labelCount: Int,
    val zplBytes: Int,
    val elapsedMillis: Long,
    /** Estado leído con ~HS antes de enviar o al probar la conexión; null si la Zebra no lo entregó. */
    val printerStatus: ZebraPrinterStatus? = null
)

sealed class ZebraPrintResult {
    data class Success(val stats: ZebraPrintStats) : ZebraPrintResult()
    data class InvalidAddress(val message: String) : ZebraPrintResult()
    data class InvalidPort(val message: String) : ZebraPrintResult()
    data class EmptyPayload(val message: String) : ZebraPrintResult()
    data class ConnectionError(val message: String, val cause: Throwable? = null) : ZebraPrintResult()
    data class Timeout(val message: String, val cause: Throwable? = null) : ZebraPrintResult()
    data class WriteError(val message: String, val cause: Throwable? = null) : ZebraPrintResult()

    /**
     * La Zebra respondió en la red pero no está en condiciones de imprimir (pausa, sin etiquetas, cabezal
     * abierto, sin cinta o sin responder). No se envió nada: así no se acumulan trabajos detrás de una impresora
     * detenida para que salgan todos juntos —y repetidos— cuando alguien la reanude.
     */
    data class PrinterNotReady(
        val message: String,
        val status: ZebraPrinterStatus? = null,
        val cause: Throwable? = null
    ) : ZebraPrintResult()

    data class UnknownError(val message: String, val cause: Throwable? = null) : ZebraPrintResult()

    val isSuccess: Boolean get() = this is Success

    fun userMessage(): String = when (this) {
        is Success -> "Impresion enviada: ${stats.labelCount} etiqueta(s), ${stats.zplBytes} bytes en ${stats.elapsedMillis} ms."
        is InvalidAddress -> message
        is InvalidPort -> message
        is EmptyPayload -> message
        is ConnectionError -> message
        is Timeout -> message
        is WriteError -> message
        is PrinterNotReady -> message
        is UnknownError -> message
    }
}
