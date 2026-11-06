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
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class ZebraPrinterService @Inject constructor(
    private val labelLayoutPreferences: ZebraLabelLayoutPreferences
) {

    /**
     * Una Zebra atiende UNA conexión raw a la vez: si una segunda llega mientras la primera sigue enviando,
     * queda sin atender hasta que la otra termine y el usuario ve "timeout". Todo lo que toca una misma
     * impresora (consultas, prueba, envío) pasa por el mismo candado y se hace en fila.
     */
    private val printerLocks = ConcurrentHashMap<String, Mutex>()

    private fun printerKey(config: ZebraPrinterConfig): String =
        "${config.host.trim().lowercase(Locale.ROOT)}:${config.port}"

    private fun lockFor(config: ZebraPrinterConfig): Mutex =
        printerLocks.getOrPut(printerKey(config)) { Mutex() }

    /** Los dpi de la cabeza no cambian entre un envío y otro: se recuerdan para no abrir una conexión de más. */
    private class CachedDpi(val dpi: Int, val readAtMillis: Long)

    private val dpiCache = ConcurrentHashMap<String, CachedDpi>()

    /**
     * Impresoras que ya contestaron alguna vez a `~HS`. Si una de ellas deja de contestar, es que está ocupada o
     * atorada. Si nunca contestó, no se puede saber si es que no sabe responderlo: ahí no se bloquea el envío.
     */
    private val printersThatAnsweredStatus = ConcurrentHashMap.newKeySet<String>()

    private fun nowMillis(): Long = SystemClock.elapsedRealtime()

    /**
     * Lee los dpi de la cabeza para crear el raster del tamaño físico correcto. Es el primer contacto con la
     * impresora en todos los flujos de impresión, así que también espera su turno si está ocupada y avisa si no
     * puede imprimir. Un valor leído hace menos de 30 minutos se reutiliza sin tocar la impresora, y si la Zebra
     * no contesta se usa el último conocido en vez de detener el trabajo.
     */
    suspend fun readPrintheadResolutionDpi(
        config: ZebraPrinterConfig,
        onProgress: ((ZebraSendProgress) -> Unit)? = null
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) {
                return@withContext Result.failure(IllegalArgumentException(validation.userMessage()))
            }

            val host = config.host.trim()
            val key = printerKey(config)
            dpiCache[key]
                ?.takeIf { nowMillis() - it.readAtMillis < DPI_CACHE_TTL_MILLIS }
                ?.let { return@withContext Result.success(it.dpi) }

            lockFor(config).withLock {
                // Si la Zebra no puede imprimir (pausa, sin etiquetas, cabezal abierto...) se dice tal cual, en
                // vez de esperar un "timeout" sin explicación. Si está atendiendo otro envío, se espera su turno.
                val outcome = awaitPrinterResponse(config, onWaiting = { seconds ->
                    onProgress?.invoke(ZebraSendProgress.WaitingForPrinter(seconds))
                })
                if (outcome is StatusOutcome.Read) {
                    outcome.status.blockingProblem()?.let { problem ->
                        Log.w(TAG, "Zebra no lista host=$host port=${config.port}: $problem")
                        return@withContext Result.failure(IOException(problem))
                    }
                }
                if (outcome is StatusOutcome.Failed) {
                    if (outcome.error !is ZebraSilentPrinterException) {
                        // Sin red, apagada o fuera de alcance: se dice ya, sin gastar otro intento ni dibujar las
                        // etiquetas para nada.
                        Log.e(TAG, "Zebra inalcanzable host=$host port=${config.port}", outcome.error)
                        return@withContext Result.failure(
                            IOException(mapError(outcome.error, host, config.port, connected = false).userMessage(), outcome.error)
                        )
                    }
                    if (printerKey(config) in printersThatAnsweredStatus) {
                        Log.w(TAG, "Zebra sin atender tras esperar host=$host port=${config.port}")
                        return@withContext Result.failure(IOException(BUSY_PRINTER_MESSAGE, outcome.error))
                    }
                }

                val reading: Int? = try {
                    requestSgdValue(
                        host = host,
                        port = config.port,
                        key = SGD_PRINTHEAD_DPI,
                        connectTimeoutMillis = config.connectTimeoutMillis,
                        readTimeoutMillis = config.writeTimeoutMillis
                    ).toZebraInt()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    // Falla de comunicación: un dato viejo y confirmado vale más que detener el trabajo de la planta.
                    val lastKnown = dpiCache[key]
                    if (lastKnown != null) {
                        Log.w(TAG, "No se pudo releer la resolución; se usa la última conocida (${lastKnown.dpi} dpi)", error)
                        return@withContext Result.success(lastKnown.dpi)
                    }
                    return@withContext if (error is SocketTimeoutException) {
                        Log.e(TAG, "La Zebra aceptó la conexión pero no respondió host=$host port=${config.port}", error)
                        Result.failure(IOException(UNRESPONSIVE_PRINTER_MESSAGE, error))
                    } else {
                        Log.e(TAG, "No se pudo leer la resolución Zebra host=$host port=${config.port}", error)
                        Result.failure(error)
                    }
                }

                // La Zebra SÍ contestó: lo que diga es la respuesta, no se sustituye por un valor guardado.
                val dpi = reading ?: return@withContext Result.failure(
                    IOException("La Zebra no reportó los dpi de su cabeza de impresión.")
                )
                if (dpi !in ZebraLabelLayout.SUPPORTED_PRINTHEAD_DPI) {
                    return@withContext Result.failure(
                        IOException(
                            "La Zebra reportó $dpi dpi; PMMovil admite cabezales de 203 o 300 dpi. No se imprimió."
                        )
                    )
                }

                dpiCache[key] = CachedDpi(dpi, nowMillis())
                Result.success(dpi)
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
            lockFor(config).withLock { readPrintSettingsLocked(config) }
        }

    /** Cuerpo de [readPrintSettings]; quien lo llama ya validó la configuración y tiene el candado. */
    private fun readPrintSettingsLocked(config: ZebraPrinterConfig): ZebraPrinterSettingsResult {
        val host = config.host.trim()
        return try {
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
                return ZebraPrinterSettingsResult.Failure(
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

        lockFor(config).withLock {
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
                ZebraSocketTransport.send(
                    host = host,
                    port = config.port,
                    bytes = SAVE_CONFIGURATION_ZPL.toByteArray(Charsets.US_ASCII),
                    connectTimeoutMillis = config.connectTimeoutMillis,
                    idleTimeoutMillis = config.writeTimeoutMillis
                )

                when (val readBack = readPrintSettingsLocked(config)) {
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
    }

    /**
     * Comprueba el puerto Y el estado de la Zebra. Abrir el puerto 9100 responde aunque la impresora esté en
     * pausa, sin etiquetas o con un envío anterior atorado, así que ya no se da por buena solo con eso.
     */
    suspend fun testConnection(config: ZebraPrinterConfig): ZebraPrintResult =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) return@withContext validation

            val host = config.host.trim()
            lockFor(config).withLock {
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

                    val statusOutcome = readStatus(config)
                    val elapsed = SystemClock.elapsedRealtime() - start
                    Log.i(TAG, "Conexion Zebra correcta host=$host port=${config.port} elapsed=${elapsed}ms")
                    val stats = ZebraPrintStats(
                        host = host,
                        port = config.port,
                        labelCount = 0,
                        zplBytes = 0,
                        elapsedMillis = elapsed,
                        printerStatus = (statusOutcome as? StatusOutcome.Read)?.status
                    )

                    when (statusOutcome) {
                        is StatusOutcome.Read ->
                            statusOutcome.status.blockingProblem()
                                ?.let { ZebraPrintResult.PrinterNotReady(it, statusOutcome.status) }
                                ?: ZebraPrintResult.Success(stats)
                        // Contestó algo que no se entiende: no se inventa un problema.
                        StatusOutcome.Unreadable -> ZebraPrintResult.Success(stats)
                        is StatusOutcome.Failed -> ZebraPrintResult.PrinterNotReady(
                            message = "La Zebra aceptó la conexión pero no respondió a la consulta de estado. " +
                                "Si está imprimiendo, espera a que termine; si no, puede tener un envío " +
                                "anterior atorado: apágala 10 segundos y vuelve a encenderla.",
                            cause = statusOutcome.error
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Throwable) {
                    mapError(error, host, config.port, connected = false)
                }
            }
        }

    /** Lee el estado de la Zebra con `~HS`. Útil para mostrarlo en pantalla o para un diagnóstico. */
    suspend fun readPrinterStatus(config: ZebraPrinterConfig): Result<ZebraPrinterStatus> =
        withContext(Dispatchers.IO) {
            val validation = validateConfig(config)
            if (validation != null) {
                return@withContext Result.failure(IllegalArgumentException(validation.userMessage()))
            }
            lockFor(config).withLock {
                when (val outcome = readStatus(config)) {
                    is StatusOutcome.Read -> Result.success(outcome.status)
                    StatusOutcome.Unreadable ->
                        Result.failure(IOException("La Zebra respondió, pero no se entendió su estado."))
                    is StatusOutcome.Failed -> Result.failure(outcome.error)
                }
            }
        }

    suspend fun printLabels(
        config: ZebraPrinterConfig,
        labels: List<ZebraLabelData>,
        onProgress: ((ZebraSendProgress) -> Unit)? = null
    ): ZebraPrintResult {
        if (labels.isEmpty()) {
            return ZebraPrintResult.EmptyPayload("No hay etiquetas para imprimir.")
        }
        val zpl = ZebraZplBuilder.buildLabels(
            labels = labels,
            layout = labelLayoutPreferences.getLayout()
        )
        val labelCount = labels.sumOf { it.copies.coerceAtLeast(1) }
        return printZpl(config = config, zpl = zpl, labelCount = labelCount, onProgress = onProgress)
    }

    /**
     * Envía un trabajo ZPL. Antes de mandar un solo byte espera a que la Zebra conteste (si otra tableta le está
     * mandando un lote, su turno llega cuando termine) y comprueba que pueda imprimir. [onProgress] se llama desde
     * un hilo de fondo para informar la espera y el avance.
     */
    suspend fun printZpl(
        config: ZebraPrinterConfig,
        zpl: String,
        labelCount: Int,
        onProgress: ((ZebraSendProgress) -> Unit)? = null
    ): ZebraPrintResult = withContext(Dispatchers.IO) {
        val validation = validateConfig(config)
        if (validation != null) return@withContext validation
        if (zpl.isBlank()) {
            return@withContext ZebraPrintResult.EmptyPayload("El bloque ZPL esta vacio.")
        }

        val host = config.host.trim()
        val bytes = zpl.toByteArray(Charsets.UTF_8)
        val blocks = ZplJobAnalyzer.blocks(bytes)
        val jobLabels = ZplJobAnalyzer.totalLabels(blocks).takeIf { it > 0 } ?: labelCount

        // Un trabajo a la vez por impresora: si otro envío sigue en curso este espera su turno.
        lockFor(config).withLock {
            val start = SystemClock.elapsedRealtime()
            var previousStatus: ZebraPrinterStatus? = null

            // Un envío a una Zebra detenida no imprime: se queda en su memoria y sale todo junto (y repetido)
            // cuando alguien la reanuda. Y mandarle datos a una Zebra que atiende otra conexión los deja a medias
            // en su memoria. Por eso primero se espera a que conteste y se comprueba que pueda imprimir.
            when (val outcome = awaitPrinterResponse(config, onWaiting = { seconds ->
                onProgress?.invoke(ZebraSendProgress.WaitingForPrinter(seconds))
            })) {
                is StatusOutcome.Read -> {
                    previousStatus = outcome.status
                    outcome.status.blockingProblem()?.let { problem ->
                        Log.w(TAG, "Envio omitido host=$host port=${config.port}: $problem")
                        return@withContext ZebraPrintResult.PrinterNotReady(problem, outcome.status)
                    }
                }
                StatusOutcome.Unreadable -> Log.w(TAG, "Estado Zebra ilegible host=$host; se intenta imprimir")
                is StatusOutcome.Failed -> {
                    if (outcome.error !is ZebraSilentPrinterException) {
                        // No hay red, está apagada o rechaza la conexión: no tiene caso intentar el envío.
                        Log.e(TAG, "Zebra inalcanzable host=$host port=${config.port}", outcome.error)
                        return@withContext mapError(outcome.error, host, config.port, connected = false)
                    }
                    if (printerKey(config) in printersThatAnsweredStatus) {
                        // Ya contestó antes y ahora calla tras esperar su turno: ocupada o atorada. Mandarle datos
                        // solo le dejaría una etiqueta a medias.
                        Log.w(TAG, "Zebra sin atender tras esperar host=$host port=${config.port}")
                        return@withContext ZebraPrintResult.PrinterNotReady(
                            message = BUSY_PRINTER_MESSAGE,
                            cause = outcome.error
                        )
                    }
                    // Nunca contestó a ~HS: puede que simplemente no lo soporte. Se intenta imprimir como antes.
                    Log.w(TAG, "Zebra sin respuesta a ~HS host=$host (nunca ha contestado); se intenta imprimir")
                }
            }

            Log.i(
                TAG,
                "Enviando ZPL host=$host port=${config.port} labels=$jobLabels bytes=${bytes.size} " +
                    "estadoPrevio=[${previousStatus?.describe() ?: "desconocido"}]"
            )

            var sentBytes = 0
            var lastPercent = -1
            try {
                ZebraSocketTransport.send(
                    host = host,
                    port = config.port,
                    bytes = bytes,
                    connectTimeoutMillis = config.connectTimeoutMillis,
                    idleTimeoutMillis = maxOf(config.writeTimeoutMillis, MIN_SEND_IDLE_TIMEOUT_MILLIS),
                    onProgress = { accepted ->
                        sentBytes = accepted
                        val percent = (accepted.toLong() * 100 / bytes.size).toInt().coerceIn(0, 100)
                        // La red avisa por cada trozo aceptado; a la pantalla solo se le avisa cuando cambia el %.
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress?.invoke(
                                ZebraSendProgress.Sending(
                                    percent = percent,
                                    labelsSent = ZplJobAnalyzer.labelsSent(blocks, accepted),
                                    labelCount = jobLabels
                                )
                            )
                        }
                    }
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
                        elapsedMillis = elapsed,
                        printerStatus = previousStatus
                    )
                )
            } catch (error: ZebraSendStalledException) {
                val elapsed = SystemClock.elapsedRealtime() - start
                Log.e(
                    TAG,
                    "Envio detenido host=$host port=${config.port} labels=$labelCount " +
                        "enviados=$sentBytes/${bytes.size} elapsed=${elapsed}ms",
                    error
                )
                ZebraPrintResult.Timeout(
                    stalledMessage(
                        config = config,
                        sentBytes = sentBytes,
                        totalBytes = bytes.size,
                        labelsSent = ZplJobAnalyzer.labelsSent(blocks, sentBytes),
                        labelCount = jobLabels
                    ),
                    error
                )
            } catch (error: TimeoutCancellationException) {
                val elapsed = SystemClock.elapsedRealtime() - start
                Log.e(
                    TAG,
                    "Timeout ZPL host=$host port=${config.port} labels=$labelCount bytes=${bytes.size} elapsed=${elapsed}ms",
                    error
                )
                mapError(error, host, config.port, connected = sentBytes > 0)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val elapsed = SystemClock.elapsedRealtime() - start
                Log.e(
                    TAG,
                    "Fallo ZPL host=$host port=${config.port} labels=$labelCount bytes=${bytes.size} elapsed=${elapsed}ms",
                    error
                )
                if (error is IOException && sentBytes > 0) {
                    // La conexión se perdió con el envío a medias (la Zebra la cerró o se cayó la red): se dice
                    // cuánto se alcanzó a entregar y cómo está la impresora, no solo "error al enviar".
                    ZebraPrintResult.WriteError(
                        abortedSendMessage(
                            config = config,
                            headline = "Se perdió la conexión con la Zebra a media impresión " +
                                "(${sentBytes / 1024} de ${bytes.size / 1024} KB; se alcanzaron a entregar " +
                                "${ZplJobAnalyzer.labelsSent(blocks, sentBytes)} de $jobLabels etiquetas).",
                        ),
                        error
                    )
                } else {
                    mapError(error, host, config.port, connected = sentBytes > 0)
                }
            }
        }
    }

    /**
     * Mensaje cuando la Zebra dejó de aceptar datos a mitad del envío. Consulta su estado en una conexión nueva
     * para que la persona sepa si es pausa, falta de etiquetas, memoria llena o un envío anterior atorado.
     */
    private fun stalledMessage(
        config: ZebraPrinterConfig,
        sentBytes: Int,
        totalBytes: Int,
        labelsSent: Int,
        labelCount: Int
    ): String {
        val headline = if (sentBytes <= 0) {
            "La Zebra no aceptó ningún dato del envío."
        } else {
            "La Zebra dejó de recibir datos después de ${sentBytes / 1024} de ${totalBytes / 1024} KB " +
                "(se alcanzaron a entregar $labelsSent de $labelCount etiquetas)."
        }
        return abortedSendMessage(config, headline)
    }

    /** [headline] + estado actual de la Zebra + la advertencia de no duplicar etiquetas al reintentar. */
    private fun abortedSendMessage(config: ZebraPrinterConfig, headline: String): String {
        val diagnosis = when (val outcome = readStatus(config)) {
            is StatusOutcome.Read -> outcome.status.describe()
            StatusOutcome.Unreadable -> "No se entendió la respuesta de estado de la Zebra."
            is StatusOutcome.Failed ->
                "Tampoco respondió a la consulta de estado: puede tener un envío anterior atorado. " +
                    "Apágala 10 segundos y vuelve a encenderla."
        }
        return "$headline $diagnosis Pudieron salir algunas etiquetas: revisa la impresora antes de reintentar " +
            "para no duplicarlas."
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
        val unreachableAdvice = "Revisa que esté encendida y que esta tableta esté en la misma red que la impresora."
        return when (error) {
            is UnknownHostException ->
                ZebraPrintResult.InvalidAddress("IP o host invalido para Zebra: $host.")
            is SocketTimeoutException,
            is TimeoutCancellationException ->
                if (connected) {
                    ZebraPrintResult.Timeout("Tiempo agotado al comunicar con Zebra $host:$port.", error)
                } else {
                    ZebraPrintResult.Timeout(
                        "No se pudo conectar con la Zebra $host:$port: no contesta en la red. $unreachableAdvice",
                        error
                    )
                }
            is ConnectException ->
                ZebraPrintResult.ConnectionError(
                    "No se pudo conectar con la Zebra $host:$port${error.shortReason()}. $unreachableAdvice",
                    error
                )
            is NoRouteToHostException ->
                ZebraPrintResult.ConnectionError(
                    "No hay ruta de red hacia la Zebra $host:$port. $unreachableAdvice",
                    error
                )
            is IOException ->
                if (connected) {
                    ZebraPrintResult.WriteError("Error al enviar ZPL a Zebra $host:$port.", error)
                } else {
                    ZebraPrintResult.ConnectionError(
                        "Puerto Zebra no disponible en $host:$port. $unreachableAdvice",
                        error
                    )
                }
            else ->
                ZebraPrintResult.UnknownError("Error inesperado al imprimir en Zebra: ${error.message}", error)
        }
    }

    /** «(Network is unreachable)» / «(Connection refused)» del sistema, para que el mensaje diga por qué. */
    private fun Throwable.shortReason(): String {
        val reason = message?.substringAfterLast("connect failed: ", "")?.trim().orEmpty()
        return if (reason.isBlank()) "" else " ($reason)"
    }

    /** Resultado de preguntarle su estado a la Zebra (`~HS`). */
    private sealed class StatusOutcome {
        class Read(val status: ZebraPrinterStatus) : StatusOutcome()

        /** Contestó, pero con algo que no tiene la forma documentada. */
        object Unreadable : StatusOutcome()

        /** No se pudo conectar o no contestó a tiempo. */
        class Failed(val error: Throwable) : StatusOutcome()
    }

    /**
     * Consulta el estado y, si la Zebra acepta la conexión pero no contesta (atiende otra conexión, por ejemplo la
     * de otra tableta), vuelve a preguntar cada pocos segundos hasta [BUSY_PATIENCE_MILLIS]. Mientras no conteste
     * no se le manda nada. Solo espera si esa impresora ya había contestado antes: si nunca lo hizo, es más probable
     * que no sepa responder `~HS` que esté ocupada.
     */
    private suspend fun awaitPrinterResponse(
        config: ZebraPrinterConfig,
        onWaiting: ((Int) -> Unit)?
    ): StatusOutcome {
        val startedAt = nowMillis()
        while (true) {
            val outcome = readStatus(config)
            val silent = outcome is StatusOutcome.Failed && outcome.error is ZebraSilentPrinterException
            val waited = nowMillis() - startedAt
            if (!silent || printerKey(config) !in printersThatAnsweredStatus || waited >= BUSY_PATIENCE_MILLIS) {
                return outcome
            }
            val seconds = (waited / 1000).toInt()
            Log.i(TAG, "Zebra ocupada o sin atender host=${config.host.trim()}; esperando su turno (${seconds}s)")
            onWaiting?.invoke(seconds)
            delay(BUSY_POLL_MILLIS)
        }
    }

    private fun readStatus(config: ZebraPrinterConfig): StatusOutcome {
        val host = config.host.trim()
        return try {
            val raw = ZebraSocketTransport.queryHostStatus(
                host = host,
                port = config.port,
                connectTimeoutMillis = config.connectTimeoutMillis,
                readTimeoutMillis = STATUS_READ_TIMEOUT_MILLIS
            )
            // Contestó (aunque sea algo ilegible): esta impresora sí atiende consultas de estado.
            printersThatAnsweredStatus += printerKey(config)
            val status = ZebraHostStatusParser.parse(raw)
            if (status == null) {
                Log.w(TAG, "~HS no interpretable host=$host respuesta=${raw.replace(NON_PRINTABLE, "·")}")
                StatusOutcome.Unreadable
            } else {
                StatusOutcome.Read(status)
            }
        } catch (error: Exception) {
            Log.w(TAG, "~HS sin respuesta host=$host port=${config.port}", error)
            StatusOutcome.Failed(error)
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

        /** La consulta de estado es corta: si la Zebra no contesta en 3 s, ya es una señal. */
        const val STATUS_READ_TIMEOUT_MILLIS = 3_000

        /**
         * Tiempo SIN AVANCE tolerado mientras se envía un trabajo. Una Zebra va aceptando datos al ritmo al que
         * imprime (≈2 s por etiqueta de 6"), así que un lote grande avanza a golpes; solo se corta si pasa este
         * tiempo sin que acepte un byte, que es una impresora detenida y no una ocupada.
         */
        const val MIN_SEND_IDLE_TIMEOUT_MILLIS = 30_000

        /** Cuánto se espera a que una Zebra ocupada con otra conexión conteste, y cada cuánto se le pregunta. */
        const val BUSY_PATIENCE_MILLIS = 60_000L
        const val BUSY_POLL_MILLIS = 2_000L

        /** Vigencia de los dpi recordados: de sobra para un turno, y acota el riesgo si cambian la impresora. */
        const val DPI_CACHE_TTL_MILLIS = 30 * 60 * 1000L

        const val BUSY_PRINTER_MESSAGE =
            "La Zebra no contesta desde hace más de un minuto: sigue atendiendo otro envío (otra tableta) o está " +
                "atorada. No se mandó nada para no dejarle una etiqueta a medias. Espera a que termine y reintenta; " +
                "si no avanza, apágala 10 segundos y vuelve a encenderla."

        val NON_PRINTABLE = Regex("[^\\x20-\\x7E]")
        val CRLF: ByteArray = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())
        const val SAVE_CONFIGURATION_ZPL = "^XA\n^JUS\n^XZ\n"

        const val UNRESPONSIVE_PRINTER_MESSAGE =
            "La Zebra aceptó la conexión pero no respondió. Revisa su pantalla: puede estar en pausa, " +
                "sin etiquetas o sin ribbon, con el cabezal abierto, ocupada con un envío anterior o con una " +
                "etiqueta a medias (apágala 10 segundos y vuelve a encenderla)."

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
