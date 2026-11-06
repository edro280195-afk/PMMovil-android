package com.example.pmmovil.data.printing

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Impresora Zebra simulada en un ServerSocket local. Reproduce lo que hace una real: acepta datos solo al
 * ritmo al que los "imprime" (ventana TCP chica) y, cuando está detenida, deja de leer por completo.
 */
class ZebraSocketTransportTest {

    private val serverThreads = mutableListOf<Thread>()
    private val servers = mutableListOf<ServerSocket>()
    private val release = CountDownLatch(1)

    @After
    fun tearDown() {
        release.countDown()
        servers.forEach { runCatching { it.close() } }
        serverThreads.forEach { it.join(2_000) }
    }

    /** ServerSocket con buffer de recepción mínimo, para que la contrapresión aparezca con poca data. */
    private fun fakePrinter(handler: (java.net.Socket) -> Unit): Int {
        val server = ServerSocket().apply {
            receiveBufferSize = 4_096
            bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
        }
        servers += server
        val thread = Thread {
            runCatching {
                server.accept().use { socket -> handler(socket) }
            }
        }.apply {
            isDaemon = true
            start()
        }
        serverThreads += thread
        return server.localPort
    }

    @Test
    fun `una impresora lenta que sigue consumiendo no se corta aunque el trabajo tarde mas que el plazo`() {
        val payload = ByteArray(2 * 1024 * 1024) { (it % 251).toByte() }
        val received = AtomicInteger(0)
        val port = fakePrinter { socket ->
            val input = socket.getInputStream()
            val chunk = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                received.addAndGet(read)
                Thread.sleep(100) // "imprime" una etiqueta antes de aceptar la siguiente
            }
        }

        val idleTimeout = 800
        val startedAt = System.nanoTime()
        var lastProgress = 0
        ZebraSocketTransport.send(
            host = "127.0.0.1",
            port = port,
            bytes = payload,
            connectTimeoutMillis = 2_000,
            idleTimeoutMillis = idleTimeout,
            sendBufferBytes = 4_096,
            onProgress = { lastProgress = it }
        )
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        // Si el sistema operativo se tragó todo en su buffer no hubo contrapresión y la prueba no demuestra nada.
        assumeTrue("Sin contrapresión en este equipo ($elapsedMillis ms)", elapsedMillis > idleTimeout)
        assertEquals("Debe haber entregado el trabajo completo", payload.size, lastProgress)
    }

    @Test
    fun `una impresora detenida corta por inactividad y dice cuanto alcanzo a recibir`() {
        val payload = ByteArray(8 * 1024 * 1024)
        val port = fakePrinter { socket ->
            val chunk = ByteArray(4_096)
            socket.getInputStream().read(chunk) // lee un poco y se "pausa": ya no lee nada
            release.await(20, TimeUnit.SECONDS)
        }

        var lastProgress = 0
        try {
            ZebraSocketTransport.send(
                host = "127.0.0.1",
                port = port,
                bytes = payload,
                connectTimeoutMillis = 2_000,
                idleTimeoutMillis = 600,
                sendBufferBytes = 4_096,
                onProgress = { lastProgress = it }
            )
            fail("Debio cortar: la impresora dejo de leer y el trabajo no cabe en ningun buffer")
        } catch (error: ZebraSendStalledException) {
            assertNotNull(error.message)
        }

        assertTrue("Alcanzo a enviar algo", lastProgress > 0)
        assertTrue("Pero no todo ($lastProgress de ${payload.size})", lastProgress < payload.size)
    }

    @Test
    fun `la consulta de estado devuelve las tres lineas de ~HS`() {
        val lines = "\u0002030,0,1,1200,000,0,0,0,000,0,0,0\u0003\r\n" +
            "\u0002000,0,0,1,2,0,0,000,00000000,1,000\u0003\r\n" +
            "\u00021234,0\u0003\r\n"
        val port = fakePrinter { socket ->
            val command = ByteArray(8)
            socket.getInputStream().read(command)
            socket.getOutputStream().apply {
                write(lines.toByteArray(Charsets.US_ASCII))
                flush()
            }
            release.await(5, TimeUnit.SECONDS)
        }

        val raw = ZebraSocketTransport.queryHostStatus("127.0.0.1", port, 2_000, 2_000)
        val status = ZebraHostStatusParser.parse(raw)

        assertNotNull(status)
        assertTrue("La pausa debe leerse", status!!.paused)
    }

    @Test
    fun `una impresora que acepta la conexion pero no contesta el estado agota el plazo`() {
        // Es exactamente lo que "Probar conexion" no detectaba: el puerto abre y nadie responde.
        val port = fakePrinter { _ -> release.await(10, TimeUnit.SECONDS) }

        val startedAt = System.nanoTime()
        try {
            ZebraSocketTransport.queryHostStatus("127.0.0.1", port, 2_000, 500)
            fail("Sin respuesta debia lanzar SocketTimeoutException")
        } catch (expected: SocketTimeoutException) {
            assertTrue(
                "Debe distinguirse de no poder conectar: es una impresora ocupada o atorada",
                expected is ZebraSilentPrinterException
            )
        }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000
        assertTrue("No debe esperar de mas ($elapsedMillis ms)", elapsedMillis < 3_000)
    }
}
