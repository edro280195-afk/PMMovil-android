package com.example.pmmovil.data.diagnostics

import com.microsoft.signalr.HubConnectionBuilder
import io.reactivex.rxjava3.exceptions.UndeliverableException
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import io.reactivex.rxjava3.subjects.CompletableSubject
import okhttp3.Dispatcher
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class RxJavaErrorHandlerTest {

    // Lo que en el teléfono es un "FATAL EXCEPTION": el error llegó al
    // manejador de excepciones no capturadas del hilo, que mata el proceso.
    private val uncaught = LinkedBlockingQueue<Throwable>()
    private val logged = LinkedBlockingQueue<Throwable>()
    private var previousDefaultHandler: Thread.UncaughtExceptionHandler? = null

    // Hilos de OkHttp daemon: ninguna prueba deja nada vivo al terminar.
    private val okHttpThreads = Executors.newCachedThreadPool { runnable ->
        Thread(runnable).apply { isDaemon = true }
    }

    @Before
    fun captureUncaughtExceptions() {
        previousDefaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught.add(error) }
        RxJavaPlugins.reset()
    }

    @After
    fun restoreGlobalState() {
        RxJavaPlugins.reset()
        Thread.setDefaultUncaughtExceptionHandler(previousDefaultHandler)
        okHttpThreads.shutdownNow()
    }

    @Test
    fun withoutHandler_aCallbackThatThrowsEndsAsAnUncaughtException() {
        // Control de las demás pruebas: sin el handler, el error sí llega al
        // manejador del hilo, que es lo que mataba la app.
        subscribeLikeHubConnectionStop()

        assertTrue(uncaught.poll() is NullPointerException)
    }

    @Test
    fun install_whenACallbackThrows_logsTheErrorInsteadOfCrashing() {
        RxJavaErrorHandler.install { _, error -> logged.add(error) }

        subscribeLikeHubConnectionStop()

        assertTrue(logged.poll() is NullPointerException)
        assertTrue(uncaught.isEmpty())
    }

    @Test
    fun install_containsAnyKindOfUndeliverableError() {
        RxJavaErrorHandler.install { _, error -> logged.add(error) }

        // RxJava envuelve los errores "normales" en UndeliverableException y
        // deja pasar tal cual los que considera bugs.
        RxJavaPlugins.onError(IOException("Canceled"))
        RxJavaPlugins.onError(IllegalStateException("otro"))

        assertTrue(logged.poll() is UndeliverableException)
        assertTrue(logged.poll() is IllegalStateException)
        assertTrue(uncaught.isEmpty())
    }

    @Test
    fun withoutHandler_stoppingSignalRWhileNegotiateFailsEndsAsAnUncaughtException() {
        // Es el bug del cliente de SignalR (8.0.0) que contiene el handler. Si
        // esta prueba falla porque ya no se reproduce, la librería lo corrigió
        // y RxJavaErrorHandler dejó de hacer falta para este caso.
        stopSignalRWhileNegotiateFails()

        val error = uncaught.poll(5, TimeUnit.SECONDS)
        assertNotNull("el stop() debía reventar sin el handler", error)
        assertTrue(error is NullPointerException)
    }

    @Test
    fun install_whenSignalRIsStoppedWhileNegotiateFails_doesNotKillTheProcess() {
        RxJavaErrorHandler.install { _, error -> logged.add(error) }

        stopSignalRWhileNegotiateFails()

        val error = logged.poll(5, TimeUnit.SECONDS)
        assertNotNull("el handler debía recibir el error del stop()", error)
        assertTrue(error is NullPointerException)
        assertTrue("el error no debe llegar al manejador del hilo", uncaught.isEmpty())
    }

    /**
     * La misma forma que `HubConnection.stop()`: se suscribe al arranque en
     * curso y, cuando este falla, la acción se topa con algo que es null.
     */
    private fun subscribeLikeHubConnectionStop() {
        val startTask = CompletableSubject.create()
        startTask.onErrorComplete().subscribe {
            throw NullPointerException("transport es null")
        }
        startTask.onError(IOException("negotiate 401"))
    }

    /**
     * Lo que pasó en la app, con la librería real: el negotiate sigue en vuelo
     * cuando llega el stop() (el 401 de una llamada REST cierra la sesión y
     * eso detiene el hub) y después el negotiate responde 401. No hay red: un
     * interceptor sostiene la petición hasta que el stop() ya está suscrito y
     * entonces contesta el 401.
     */
    private fun stopSignalRWhileNegotiateFails() {
        val releaseNegotiate = CountDownLatch(1)
        val hub = HubConnectionBuilder.create("http://localhost/hubs/warehouse")
            .setHttpClientBuilderCallback { builder ->
                builder
                    .dispatcher(Dispatcher(okHttpThreads))
                    .addInterceptor { chain ->
                        releaseNegotiate.await(5, TimeUnit.SECONDS)
                        Response.Builder()
                            .request(chain.request())
                            .protocol(Protocol.HTTP_1_1)
                            .code(401)
                            .message("Unauthorized")
                            .body("".toResponseBody(null))
                            .build()
                    }
            }
            .build()

        val startFailed = CountDownLatch(1)
        hub.start().subscribe({}, { startFailed.countDown() })
        hub.stop().subscribe({}, {})
        releaseNegotiate.countDown()

        assertTrue("el negotiate debía fallar con 401", startFailed.await(5, TimeUnit.SECONDS))
    }
}
