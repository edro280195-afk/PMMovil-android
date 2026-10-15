package com.example.pmmovil.data.scanner

import java.util.Locale

class HardwareBarcodeDecoder(
    private val inactivityTimeoutMillis: Long = 1_500L,
    private val maxLength: Int = 512
) {
    private val buffer = StringBuilder()
    private var firstCharacterAt: Long? = null
    private var lastCharacterAt: Long? = null

    fun append(character: Char, eventTimeMillis: Long) {
        val previousCharacterAt = lastCharacterAt
        if (
            previousCharacterAt != null &&
            eventTimeMillis - previousCharacterAt > inactivityTimeoutMillis
        ) {
            reset()
        }

        if (!character.isISOControl() && buffer.length < maxLength) {
            if (buffer.isEmpty()) {
                firstCharacterAt = eventTimeMillis
            }
            buffer.append(character)
            lastCharacterAt = eventTimeMillis
        }
    }

    fun complete(): String? {
        val code = BarcodeValueNormalizer.normalize(buffer.toString()).takeIf(String::isNotEmpty)
        reset()
        return code
    }

    /**
     * Cierra el buffer solo si el patron de tecleo parece una pistola y no
     * una persona escribiendo. Ademas de la duracion total se exige un
     * intervalo medio entre caracteres muy corto ([maxAverageIntervalMillis]):
     * con solo la duracion total, escribir 6 caracteres rapido en un campo y
     * pulsar Enter disparaba un scan fantasma desde un teclado Bluetooth.
     */
    fun completeIfLikelyScan(
        minLength: Int = 6,
        maxDurationMillis: Long = 1_200L,
        maxAverageIntervalMillis: Long = 60L
    ): String? {
        val code = BarcodeValueNormalizer.normalize(buffer.toString())
        val first = firstCharacterAt
        val last = lastCharacterAt
        val duration = if (first != null && last != null) last - first else Long.MAX_VALUE
        val gaps = (code.length - 1).coerceAtLeast(1)
        val averageInterval = if (duration == Long.MAX_VALUE) Long.MAX_VALUE else duration / gaps
        reset()
        return code.takeIf {
            it.length >= minLength &&
                duration in 0..maxDurationMillis &&
                averageInterval <= maxAverageIntervalMillis
        }
    }

    /**
     * Indica si lo acumulado hasta ahora llega a la velocidad de un lector
     * (decenas de milisegundos entre caracteres) y no de una persona tecleando.
     * Sirve para reconocer el "keyboard wedge" interno de equipos como Honeywell,
     * que inyecta las teclas desde un dispositivo virtual sin nombre de lector.
     */
    fun isBurstSoFar(minChars: Int = 3, maxAverageIntervalMillis: Long = 40L): Boolean {
        val first = firstCharacterAt ?: return false
        val last = lastCharacterAt ?: return false
        if (buffer.length < minChars) return false
        val averageInterval = (last - first) / (buffer.length - 1).coerceAtLeast(1)
        return averageInterval <= maxAverageIntervalMillis
    }

    fun hasContent(): Boolean = buffer.isNotEmpty()

    fun reset() {
        buffer.setLength(0)
        firstCharacterAt = null
        lastCharacterAt = null
    }
}

fun isLikelyBarcodeScanner(deviceName: String?, isVirtual: Boolean): Boolean {
    if (isVirtual || deviceName.isNullOrBlank()) return false

    val normalizedName = deviceName.lowercase(Locale.ROOT)
    return normalizedName.contains("scanner") ||
        normalizedName.contains("barcode") ||
        normalizedName.contains("datawedge") ||
        normalizedName.contains("scan") && normalizedName.contains("hid")
}
