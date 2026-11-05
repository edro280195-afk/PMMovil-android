package com.example.pmmovil.data.printing

import android.os.SystemClock
import android.util.Log
import java.io.BufferedInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext

@Singleton
class ZebraPrinterService @Inject constructor(
    private val labelLayoutPreferences: ZebraLabelLayoutPreferences
) {

    /** Lee únicamente los dpi de la cabeza para crear el raster del tamaño físico correcto. */
    suspend fun readPrintheadResolutionDpi(config: ZebraPrinterConfig): Result<Int> =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) {
                return@withContext Result.failure(IllegalArgumentException(validation.userMessage()))
            }

            val host = config.host.trim()
            try {
                val dpi = requestSgdValue(
                    host = host,
                    port = config.port,
                    key = SGD_PRINTHEAD_DPI,
                    connectTimeoutMillis = config.connectTimeoutMillis,
                    readTimeoutMillis = config.writeTimeoutMillis
                ).toZebraInt() ?: throw IOException(
                    "La Zebra no reportó los dpi de su cabeza de impresión."
                )

                if (dpi !in ZebraLabelLayout.SUPPORTED_PRINTHEAD_DPI) {
                    throw IOException(
                        "La Zebra reportó $dpi dpi; PMMovil admite cabezales de 203 o 300 dpi. No se imprimió."
                    )
                }

                Result.success(dpi)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "No se pudo leer la resolución Zebra host=$host port=${config.port}", error)
                Result.failure(error)
            }
        }

    /**
     * Consulta exclusivamente las preferencias seguras de impresión. Nunca aplica valores
     * predeterminados de PMMovil: lo mostrado proviene de la Zebra seleccionada.
     */
    suspend fun readPrintSettings(config: ZebraPrinterConfig): ZebraPrinterSettingsResult =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) {
                return@withContext ZebraPrinterSettingsResult.Failure(validation.userMessage())
            }

            val host = config.host.trim()
            try {
                val replies = SGD_QUERY_KEYS.associateWith { key ->
                    runCatching {
                        requestSgdValue(
                            host = host,
                            port = config.port,
                            key = key,
                            connectTimeoutMillis = config.connectTimeoutMillis,
                            readTimeoutMillis = config.writeTimeoutMillis
                        )
                    }.getOrNull()
                }

                val unavailable = replies
                    .filterValues { it.isNullOrBlank() || isSgdError(it) }
                    .keys
                    .toList()

                val settings = ZebraPrinterPrintSettings(
                    darkness = replies[SGD_PRINT_DARKNESS]?.toZebraFloat(),
                    speedIps = replies[SGD_PRINT_SPEED]?.toZebraFloat(),
                    printMethod = ZebraPrinterSettingsValidation.normalizePrintMethod(
                        replies[SGD_PRINT_METHOD]
                    ),
                    mediaType = ZebraPrinterSettingsValidation.normalizeMediaType(
                        replies[SGD_MEDIA_TYPE]
                    ),
                    labelTop = replies[SGD_LABEL_TOP]?.toZebraInt(),
                    leftPosition = replies[SGD_LEFT_POSITION]?.toZebraInt()
                )

                if (!settings.hasAnyValue) {
                    return@withContext ZebraPrinterSettingsResult.Failure(
                        "La Zebra respondio, pero no expuso preferencias de impresion compatibles. " +
                            "Verifica firmware Link-OS, puerto ${config.port} y restricciones SGD."
                    )
                }

                ZebraPrinterSettingsResult.Success(
                    ZebraPrinterLiveSettings(
                        productName = replies[SGD_PRODUCT_NAME].toCleanSgdValue(),
                        firmwareVersion = replies[SGD_FIRMWARE_VERSION].toCleanSgdValue(),
                        printheadResolutionDpi = replies[SGD_PRINTHEAD_DPI]?.toZebraInt(),
                        printSettings = settings,
                        unavailableSettings = unavailable
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(TAG, "No se pudieron leer preferencias Zebra host=$host port=${config.port}", error)
                ZebraPrinterSettingsResult.Failure(
                    mapError(error, host, config.port, connected = false).userMessage(),
                    error
                )
            }
        }

    /**
     * Aplica valores confirmados por la persona usuaria, los guarda en la memoria permanente
     * de la Zebra y vuelve a leerlos. La verificacion final evita reportar exito a ciegas.
     */
    suspend fun applyPrintSettings(
        config: ZebraPrinterConfig,
        settings: ZebraPrinterPrintSettings
    ): ZebraPrinterSettingsResult = withContext(Dispatchers.IO) {
        val configValidation = validateConfig(config)
        if (configValidation != null) {
            return@withContext ZebraPrinterSettingsResult.Failure(configValidation.userMessage())
        }

        val settingsValidation = ZebraPrinterSettingsValidation.validate(settings)
        if (settingsValidation != null) {
            return@withContext ZebraPrinterSettingsResult.Failure(settingsValidation)
        }

        val host = config.host.trim()
        val normalizedPrintMethod = ZebraPrinterSettingsValidation.normalizePrintMethod(settings.printMethod)
            ?: return@withContext ZebraPrinterSettingsResult.Failure("Metodo de impresion invalido.")
        val normalizedMediaType = ZebraPrinterSettingsValidation.normalizeMediaType(settings.mediaType)
            ?: return@withContext ZebraPrinterSettingsResult.Failure("Tipo de material invalido.")

        val commands = listOf(
            SGD_PRINT_DARKNESS to settings.darkness!!.toZebraNumber(),
            SGD_PRINT_SPEED to settings.speedIps!!.toZebraNumber(),
            SGD_PRINT_METHOD to normalizedPrintMethod,
            SGD_MEDIA_TYPE to normalizedMediaType,
            SGD_LABEL_TOP to settings.labelTop!!.toString(),
            SGD_LEFT_POSITION to settings.leftPosition!!.toString()
        )

        try {
            commands.forEach { (key, value) ->
                sendSgdSetCommand(
                    host = host,
                    port = config.port,
                    key = key,
                    value = value,
                    connectTimeoutMillis = config.connectTimeoutMillis,
                    writeTimeoutMillis = config.writeTimeoutMillis
                )
            }

            // ^JUS es un trabajo ZPL separado: consolida la configuracion actual de la Zebra.
            writeZplBlock(
                host = host,
                port = config.port,
                bytes = SAVE_CONFIGURATION_ZPL.toByteArray(Charsets.US_ASCII),
                connectTimeoutMillis = config.connectTimeoutMillis,
                writeTimeoutMillis = config.writeTimeoutMillis
            )

            when (val readBack = readPrintSettings(config)) {
                is ZebraPrinterSettingsResult.Failure -> readBack
                is ZebraPrinterSettingsResult.Success -> {
                    val actual = readBack.settings.printSettings
                    if (!settings.matches(actual)) {
                        ZebraPrinterSettingsResult.Failure(
                            "La Zebra no confirmo todos los cambios. Revisa el material cargado, " +
                                "las opciones instaladas y si el equipo tiene comandos restringidos."
                        )
                    } else {
                        readBack
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Log.e(TAG, "No se pudieron aplicar preferencias Zebra host=$host port=${config.port}", error)
            ZebraPrinterSettingsResult.Failure(
                mapError(error, host, config.port, connected = false).userMessage(),
                error
            )
        }
    }

    suspend fun testConnection(config: ZebraPrinterConfig): ZebraPrintResult =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) return@withContext validation

            val host = config.host.trim()
            val start = SystemClock.elapsedRealtime()
            Log.i(TAG, "Probando conexion Zebra host=$host port=${config.port}")
            try {
                Socket().use { socket ->
                    socket.tcpNoDelay = true
                    socket.soTimeout = config.writeTimeoutMillis
                    socket.connect(
                        InetSocketAddress(host, config.port),
                        config.connectTimeoutMillis
                    )
                }
                val elapsed = SystemClock.elapsedRealtime() - start
                Log.i(TAG, "Conexion Zebra correcta host=$host port=${config.port} elapsed=${elapsed}ms")
                ZebraPrintResult.Success(
                    ZebraPrintStats(
                        host = host,
                        port = config.port,
                        labelCount = 0,
                        zplBytes = 0,
                        elapsedMillis = elapsed
                    )
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                mapError(error, host, config.port, connected = false)
            }
        }

    suspend fun printLabels(
        config: ZebraPrinterConfig,
        labels: List<ZebraLabelData>
    ): ZebraPrintResult {
        if (labels.isEmpty()) {
            return ZebraPrintResult.EmptyPayload("No hay etiquetas para imprimir.")
        }
        val zpl = ZebraZplBuilder.buildLabels(
            labels = labels,
            layout = labelLayoutPreferences.getLayout()
        )
        val labelCount = labels.sumOf { it.copies.coerceAtLeast(1) }
        return printZpl(config = config, zpl = zpl, labelCount = labelCount)
    }

    suspend fun printZpl(
        config: ZebraPrinterConfig,
        zpl: String,
        labelCount: Int
    ): ZebraPrintResult = withContext(Dispatchers.IO) {
        val validation = validateConfig(config)
        if (validation != null) return@withContext validation
        if (zpl.isBlank()) {
            return@withContext ZebraPrintResult.EmptyPayload("El bloque ZPL esta vacio.")
        }

        val host = config.host.trim()
        val bytes = zpl.toByteArray(Charsets.UTF_8)
        val start = SystemClock.elapsedRealtime()
        var connected = false

        Log.i(
            TAG,
            "Enviando ZPL host=$host port=${config.port} labels=$labelCount bytes=${bytes.size}"
        )

        try {
            connected = writeZplBlock(
                host = host,
                port = config.port,
                bytes = bytes,
                connectTimeoutMillis = config.connectTimeoutMillis,
                writeTimeoutMillis = config.writeTimeoutMillis
            )

            val elapsed = SystemClock.elapsedRealtime() - start
            Log.i(
                TAG,
                "ZPL enviado host=$host port=${config.port} labels=$labelCount bytes=${bytes.size} elapsed=${elapsed}ms"
            )
            ZebraPrintResult.Success(
                ZebraPrintStats(
                    host = host,
                    port = config.port,
                    labelCount = labelCount,
                    zplBytes = bytes.size,
                    elapsedMillis = elapsed
                )
            )
        } catch (error: TimeoutCancellationException) {
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.e(
                TAG,
                "Timeout ZPL host=$host port=${config.port} labels=$labelCount bytes=${bytes.size} elapsed=${elapsed}ms",
                error
            )
            mapError(error, host, config.port, connected)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            val elapsed = SystemClock.elapsedRealtime() - start
            Log.e(
                TAG,
                "Fallo ZPL host=$host port=${config.port} labels=$labelCount bytes=${bytes.size} elapsed=${elapsed}ms",
                error
            )
            mapError(error, host, config.port, connected)
        }
    }

    private fun validateConfig(config: ZebraPrinterConfig): ZebraPrintResult? {
        val host = config.host.trim()
        if (host.isBlank()) {
            return ZebraPrintResult.InvalidAddress("Configura la IP de la impresora Zebra.")
        }
        if (host.any(Char::isWhitespace)) {
            return ZebraPrintResult.InvalidAddress("La IP de la impresora no debe tener espacios.")
        }
        if (config.port !in 1..65_535) {
            return ZebraPrintResult.InvalidPort("El puerto de la impresora debe estar entre 1 y 65535.")
        }
        return null
    }

    private fun mapError(
        error: Throwable,
        host: String,
        port: Int,
        connected: Boolean
    ): ZebraPrintResult {
        return when (error) {
            is UnknownHostException ->
                ZebraPrintResult.InvalidAddress("IP o host invalido para Zebra: $host.")
            is SocketTimeoutException,
            is TimeoutCancellationException ->
                ZebraPrintResult.Timeout("Tiempo agotado al comunicar con Zebra $host:$port.", error)
            is ConnectException ->
                ZebraPrintResult.ConnectionError("No se pudo conectar a Zebra $host:$port.", error)
            is NoRouteToHostException ->
                ZebraPrintResult.ConnectionError("No hay ruta de red hacia Zebra $host:$port.", error)
            is IOException ->
                if (connected) {
                    ZebraPrintResult.WriteError("Error al enviar ZPL a Zebra $host:$port.", error)
                } else {
                    ZebraPrintResult.ConnectionError("Puerto Zebra no disponible en $host:$port.", error)
                }
            else ->
                ZebraPrintResult.UnknownError("Error inesperado al imprimir en Zebra: ${error.message}", error)
        }
    }

    private fun writeZplBlock(
        host: String,
        port: Int,
        bytes: ByteArray,
        connectTimeoutMillis: Int,
        writeTimeoutMillis: Int
    ): Boolean {
        SocketChannel.open().use { channel ->
            channel.configureBlocking(false)
            channel.socket().tcpNoDelay = true
            if (!channel.connect(InetSocketAddress(host, port))) {
                finishConnect(channel, connectTimeoutMillis)
            }
            writeFully(channel, ByteBuffer.wrap(bytes), writeTimeoutMillis)
        }
        return true
    }

    private fun finishConnect(channel: SocketChannel, timeoutMillis: Int) {
        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_CONNECT)
            if (selector.select(timeoutMillis.toLong()) == 0) {
                throw SocketTimeoutException("Timeout conectando con impresora Zebra.")
            }
            selector.selectedKeys().clear()
            if (!channel.finishConnect()) {
                throw ConnectException("No se completo la conexion con la impresora Zebra.")
            }
        }
    }

    private fun writeFully(channel: SocketChannel, buffer: ByteBuffer, timeoutMillis: Int) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_WRITE)
            while (buffer.hasRemaining()) {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) {
                    throw SocketTimeoutException("Timeout escribiendo ZPL en impresora Zebra.")
                }
                if (channel.write(buffer) > 0) continue

                if (selector.select(remaining) == 0) {
                    throw SocketTimeoutException("Timeout escribiendo ZPL en impresora Zebra.")
                }
                selector.selectedKeys().clear()
            }
        }
    }

    private fun requestSgdValue(
        host: String,
        port: Int,
        key: String,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int
    ): String {
        return Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = readTimeoutMillis
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            writeAsciiCommand(socket.getOutputStream(), "! U1 getvar \"$key\"")
            readAsciiLine(BufferedInputStream(socket.getInputStream()))
        }
    }

    private fun sendSgdSetCommand(
        host: String,
        port: Int,
        key: String,
        value: String,
        connectTimeoutMillis: Int,
        writeTimeoutMillis: Int
    ) {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = writeTimeoutMillis
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            writeAsciiCommand(socket.getOutputStream(), "! U1 setvar \"$key\" \"$value\"")
        }
    }

    private fun writeAsciiCommand(output: OutputStream, command: String) {
        output.write(command.toByteArray(Charsets.US_ASCII))
        output.write(CRLF)
        output.flush()
    }

    private fun readAsciiLine(input: BufferedInputStream): String {
        val bytes = ArrayList<Byte>(128)
        while (bytes.size < MAX_SGD_RESPONSE_BYTES) {
            val value = input.read()
            if (value == -1) break
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes += value.toByte()
        }
        if (bytes.isEmpty()) {
            throw IOException("La impresora no devolvio respuesta al comando SGD.")
        }
        return bytes.toByteArray().toString(Charsets.US_ASCII)
    }

    private fun String?.toCleanSgdValue(): String? {
        val cleaned = this.orEmpty().trim().removeSurrounding("\"")
        return cleaned.takeIf { it.isNotBlank() && !isSgdError(it) }
    }

    private fun String.toZebraFloat(): Float? = toCleanSgdValue()
        ?.replace(',', '.')
        ?.toFloatOrNull()

    private fun String.toZebraInt(): Int? = toCleanSgdValue()
        ?.toIntOrNull()
        ?: toCleanSgdValue()?.toFloatOrNull()?.toInt()

    private fun Float.toZebraNumber(): String = String.format(Locale.US, "%.1f", this)

    private fun ZebraPrinterPrintSettings.matches(actual: ZebraPrinterPrintSettings): Boolean {
        return darkness == actual.darkness &&
            speedIps == actual.speedIps &&
            ZebraPrinterSettingsValidation.normalizePrintMethod(printMethod) == actual.printMethod &&
            ZebraPrinterSettingsValidation.normalizeMediaType(mediaType) == actual.mediaType &&
            labelTop == actual.labelTop &&
            leftPosition == actual.leftPosition
    }

    private fun isSgdError(value: String): Boolean {
        val normalized = value.trim().lowercase(Locale.US)
        return normalized == "?" || normalized.startsWith("error") || normalized.startsWith("unknown")
    }

    private companion object {
        const val TAG = "ZebraPrinterService"
        const val MAX_SGD_RESPONSE_BYTES = 4_096
        val CRLF: ByteArray = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        const val SAVE_CONFIGURATION_ZPL = "^XA\n^JUS\n^XZ\n"

        const val SGD_PRODUCT_NAME = "device.product_name"
        const val SGD_FIRMWARE_VERSION = "appl.name"
        const val SGD_PRINTHEAD_DPI = "head.resolution.in_dpi"
        const val SGD_PRINT_DARKNESS = "print.tone"
        const val SGD_PRINT_SPEED = "media.speed"
        const val SGD_PRINT_METHOD = "ezpl.print_method"
        const val SGD_MEDIA_TYPE = "ezpl.media_type"
        const val SGD_LABEL_TOP = "zpl.label_top"
        const val SGD_LEFT_POSITION = "zpl.left_position"

        val SGD_QUERY_KEYS = listOf(
            SGD_PRODUCT_NAME,
            SGD_FIRMWARE_VERSION,
            SGD_PRINTHEAD_DPI,
            SGD_PRINT_DARKNESS,
            SGD_PRINT_SPEED,
            SGD_PRINT_METHOD,
            SGD_MEDIA_TYPE,
            SGD_LABEL_TOP,
            SGD_LEFT_POSITION
        )
    }
}
