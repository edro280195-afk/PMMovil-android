package com.example.pmmovil.data.printing

import android.content.SharedPreferences
import androidx.core.content.edit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guarda la impresora local por tipo de etiqueta y por dispositivo.
 *
 * La API conserva un catalogo comun, pero la predeterminada no se guarda en el servidor:
 * cada instalacion puede tener una impresora distinta para Entrada y Caseta.
 */
@Singleton
class ZebraPrinterPreferences @Inject constructor(
    private val prefs: SharedPreferences
) {
    /** Devuelve la predeterminada local o la ultima configuracion disponible como respaldo. */
    fun getConfig(type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA): ZebraPrinterConfig {
        return getDefaultConfig(type)
            ?: getLastConfig(type)
            ?: readLegacyConfig().also { legacy ->
                saveConfig(type, legacy)
            }
    }

    fun getDefaultConfig(type: ZebraLabelType): ZebraPrinterConfig? {
        val keys = Keys(type, DEFAULT_SUFFIX)
        if (!prefs.getBoolean(keys.configured, false)) return null
        return readConfig(keys)
    }

    fun getLastConfig(type: ZebraLabelType): ZebraPrinterConfig? {
        val keys = Keys(type, LAST_SUFFIX)
        if (!prefs.getBoolean(keys.configured, false)) return null
        return readConfig(keys)
    }

    /** Guarda la ultima impresora usada en el editor, sin convertirla en predeterminada. */
    fun saveConfig(
        type: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA,
        config: ZebraPrinterConfig
    ) {
        saveConfig(Keys(type, LAST_SUFFIX), config)
    }

    /** Guarda la predeterminada del modulo y tambien la deja como respaldo local. */
    fun saveDefaultConfig(type: ZebraLabelType, config: ZebraPrinterConfig) {
        saveConfig(Keys(type, DEFAULT_SUFFIX), config)
        saveConfig(Keys(type, LAST_SUFFIX), config)
    }

    fun clearDefaultConfig(type: ZebraLabelType) {
        val keys = Keys(type, DEFAULT_SUFFIX)
        prefs.edit {
            remove(keys.configured)
            remove(keys.id)
            remove(keys.host)
            remove(keys.port)
            remove(keys.name)
            remove(keys.connectTimeout)
            remove(keys.writeTimeout)
        }
    }

    fun isDefault(type: ZebraLabelType, printerId: Int, host: String, port: Int): Boolean {
        val defaultConfig = getDefaultConfig(type) ?: return false
        return defaultConfig.printerId?.let { it > 0 && it == printerId } == true ||
            (defaultConfig.host.equals(host, ignoreCase = true) && defaultConfig.port == port)
    }

    private fun readConfig(keys: Keys): ZebraPrinterConfig {
        return ZebraPrinterConfig(
            printerId = prefs.getInt(keys.id, 0).takeIf { it > 0 },
            host = prefs.getString(keys.host, DEFAULT_HOST).orEmpty(),
            port = prefs.getInt(keys.port, DEFAULT_PORT),
            printerName = prefs.getString(keys.name, DEFAULT_NAME).orEmpty(),
            connectTimeoutMillis = prefs.getInt(keys.connectTimeout, DEFAULT_CONNECT_TIMEOUT),
            writeTimeoutMillis = prefs.getInt(keys.writeTimeout, DEFAULT_WRITE_TIMEOUT)
        )
    }

    private fun readLegacyConfig(): ZebraPrinterConfig {
        return ZebraPrinterConfig(
            host = prefs.getString(LEGACY_HOST, DEFAULT_HOST).orEmpty(),
            port = prefs.getInt(LEGACY_PORT, DEFAULT_PORT),
            printerName = prefs.getString(LEGACY_NAME, DEFAULT_NAME).orEmpty()
        )
    }

    private fun saveConfig(keys: Keys, config: ZebraPrinterConfig) {
        prefs.edit {
            putBoolean(keys.configured, true)
            if (config.printerId != null && config.printerId > 0) {
                putInt(keys.id, config.printerId)
            } else {
                remove(keys.id)
            }
            putString(keys.host, config.host.trim())
            putInt(keys.port, config.port)
            putString(keys.name, config.printerName.trim())
            putInt(keys.connectTimeout, config.connectTimeoutMillis)
            putInt(keys.writeTimeout, config.writeTimeoutMillis)
        }
    }

    private data class Keys(
        val modulePrefix: String,
        val suffix: String
    ) {
        private val prefix: String get() = "$modulePrefix$suffix"
        val configured: String get() = "${prefix}CONFIGURED"
        val id: String get() = "${prefix}ID"
        val host: String get() = "${prefix}HOST"
        val port: String get() = "${prefix}PORT"
        val name: String get() = "${prefix}NAME"
        val connectTimeout: String get() = "${prefix}CONNECT_TIMEOUT"
        val writeTimeout: String get() = "${prefix}WRITE_TIMEOUT"
    }

    private companion object {
        const val ENTRADA_PREFIX = "ZEBRA_PRINTER_ENTRADA_"
        const val CASETA_PREFIX = "ZEBRA_PRINTER_CASETA_"
        const val DEFAULT_SUFFIX = "DEFAULT_"
        const val LAST_SUFFIX = "LAST_"
        const val LEGACY_HOST = "ZEBRA_PRINTER_HOST"
        const val LEGACY_PORT = "ZEBRA_PRINTER_PORT"
        const val LEGACY_NAME = "ZEBRA_PRINTER_NAME"
        const val DEFAULT_HOST = "192.168.20.37"
        const val DEFAULT_PORT = 9100
        const val DEFAULT_NAME = "Zebra ZT411"
        const val DEFAULT_CONNECT_TIMEOUT = 4_000
        const val DEFAULT_WRITE_TIMEOUT = 8_000
    }

    private fun Keys(type: ZebraLabelType, suffix: String): Keys = Keys(
        modulePrefix = when (type) {
            ZebraLabelType.ENTRADA_BODEGA -> ENTRADA_PREFIX
            ZebraLabelType.CASETA -> CASETA_PREFIX
        },
        suffix = suffix
    )
}
