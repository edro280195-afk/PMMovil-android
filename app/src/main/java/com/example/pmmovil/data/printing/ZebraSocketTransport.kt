package com.example.pmmovil.data.printing

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

/**
 * La conexión se abrió pero la impresora dejó de aceptar datos a mitad del envío. Se distingue de un timeout de
 * conexión (que es un [SocketTimeoutException] pelón) porque aquí SÍ se conoce el avance y tiene sentido
 * preguntarle a la Zebra qué le pasa.
 */
internal class ZebraSendStalledException(message: String) : SocketTimeoutException(message)

/**
 * La conexión se abrió pero la impresora no contestó nada. Es la firma de una Zebra ocupada atendiendo OTRA
 * conexión (por ejemplo, otra tableta con un lote en curso) o atorada: el sistema operativo de la impresora
 * acepta la conexión TCP, pero su aplicación no la atiende. Se distingue de no poder conectar (apagada, sin red).
 */
internal class ZebraSilentPrinterException(message: String) : SocketTimeoutException(message)

/**
 * Entrada/salida cruda hacia el puerto 9100 de una Zebra. Kotlin/JVM puro (sin Android) para poder probarlo
 * contra una impresora simulada en un ServerSocket local.
 *
 * Una Zebra no acepta datos más rápido de lo que imprime: cuando su memoria de recepción se llena cierra la
 * ventana TCP y el emisor se queda esperando. Eso es contrapresión normal, no una falla. Por eso el límite de
 * espera es por INACTIVIDAD (tiempo sin que la impresora acepte un solo byte) y no un plazo fijo para todo el
 * trabajo: un lote de 20 etiquetas tarda lo que tarda en imprimirse, y con el plazo fijo de 8 s anterior se
 * cortaba a medias aunque la impresora estuviera funcionando.
 */
internal object ZebraSocketTransport {

    /** Tope absoluto de un envío; solo protege contra un trabajo que avance eternamente a cuentagotas. */
    const val DEFAULT_MAX_TOTAL_MILLIS = 10 * 60 * 1000L

    private const val ETX: Byte = 0x03
    private const val MAX_STATUS_BYTES = 1024

    /**
     * Envía [bytes] y cierra la conexión. [onProgress] recibe el total de bytes aceptados por la impresora hasta
     * el momento, para poder decir "se enviaron X de Y" si el envío se corta.
     *
     * @throws SocketTimeoutException si la impresora no acepta datos durante [idleTimeoutMillis] seguidos
     *   (o no completa la conexión en [connectTimeoutMillis]).
     */
    fun send(
        host: String,
        port: Int,
        bytes: ByteArray,
        connectTimeoutMillis: Int,
        idleTimeoutMillis: Int,
        maxTotalMillis: Long = DEFAULT_MAX_TOTAL_MILLIS,
        sendBufferBytes: Int? = null,
        onProgress: (Int) -> Unit = {}
    ) {
        SocketChannel.open().use { channel ->
            channel.configureBlocking(false)
            channel.socket().tcpNoDelay = true
            if (sendBufferBytes != null) channel.socket().sendBufferSize = sendBufferBytes
            if (!channel.connect(InetSocketAddress(host, port))) {
                finishConnect(channel, connectTimeoutMillis)
            }
            writeFully(channel, ByteBuffer.wrap(bytes), idleTimeoutMillis, maxTotalMillis, onProgress)
        }
    }

    /**
     * Pide `~HS` y devuelve el texto recibido (hasta las tres líneas con ETX). Si la impresora contesta solo una
     * parte antes de [readTimeoutMillis], devuelve lo recibido para que el analizador decida; si no contesta nada,
     * lanza [SocketTimeoutException].
     */
    fun queryHostStatus(
        host: String,
        port: Int,
        connectTimeoutMillis: Int,
        readTimeoutMillis: Int
    ): String {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = readTimeoutMillis
            socket.connect(InetSocketAddress(host, port), connectTimeoutMillis)
            socket.getOutputStream().apply {
                write("~HS".toByteArray(Charsets.US_ASCII))
                flush()
            }

            val received = ByteArrayOutputStream()
            val chunk = ByteArray(256)
            var endMarkers = 0
            val input = socket.getInputStream()
            try {
                while (endMarkers < 3 && received.size() < MAX_STATUS_BYTES) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    for (i in 0 until read) if (chunk[i] == ETX) endMarkers++
                    received.write(chunk, 0, read)
                }
            } catch (timeout: SocketTimeoutException) {
                if (received.size() == 0) {
                    throw ZebraSilentPrinterException("La impresora aceptó la conexión pero no contestó el estado.")
                }
            }
            if (received.size() == 0) throw IOException("La impresora cerró la conexión sin responder al estado.")
            return received.toString(Charsets.US_ASCII.name())
        }
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

    private fun writeFully(
        channel: SocketChannel,
        buffer: ByteBuffer,
        idleTimeoutMillis: Int,
        maxTotalMillis: Long,
        onProgress: (Int) -> Unit
    ) {
        val startedAt = nowMillis()
        var idleDeadline = startedAt + idleTimeoutMillis
        Selector.open().use { selector ->
            channel.register(selector, SelectionKey.OP_WRITE)
            while (buffer.hasRemaining()) {
                if (channel.write(buffer) > 0) {
                    // Avance real: la impresora está consumiendo. El plazo de inactividad vuelve a empezar.
                    onProgress(buffer.position())
                    idleDeadline = nowMillis() + idleTimeoutMillis
                    continue
                }

                val now = nowMillis()
                if (now - startedAt > maxTotalMillis) {
                    throw ZebraSendStalledException("El envío a la impresora Zebra excedió el tiempo máximo.")
                }
                val remaining = idleDeadline - now
                if (remaining <= 0 || selector.select(remaining) == 0) {
                    throw ZebraSendStalledException("La impresora Zebra dejó de aceptar datos.")
                }
                selector.selectedKeys().clear()
            }
        }
    }

    private fun nowMillis(): Long = System.nanoTime() / 1_000_000L
}
