package com.example.pmmovil.data.diagnostics

import android.util.Log
import io.reactivex.rxjava3.plugins.RxJavaPlugins

/**
 * Red de seguridad para los errores de RxJava que ya no tienen a quién avisar.
 *
 * RxJava no los pierde: los manda a [RxJavaPlugins.onError], y si nadie
 * registró un manejador termina llamando al manejador de excepciones no
 * capturadas del hilo donde ocurrieron. En Android eso mata el proceso.
 *
 * El único RxJava de la app es el que trae el cliente de SignalR, y tiene un
 * caso real. `HubConnection.stop()` se suscribe al arranque en curso y, cuando
 * este termina, ejecuta `connectionState.transport.stop()`. Si el `stop()`
 * llega mientras el negotiate sigue en vuelo y el negotiate falla (un 401 por
 * JWT vencido, un servidor caído...), el transporte nunca se creó: esa línea
 * lanza NullPointerException dentro de un callback de OkHttp y nada de lo que
 * rodea al `start()` o al `stop()` puede atraparla. Visto el 2026-10-16 como
 * FATAL EXCEPTION en el hilo "OkHttp http://127.0.0.1:5000/...", unos segundos
 * después de abrir la app con la sesión del día anterior.
 *
 * Aquí el error solo se registra. Una consecuencia: el Completable que
 * devolvió ese `stop()` no termina nunca, por eso quien lo espera debe tener
 * un límite de tiempo (ver `WarehouseHubService.stopConnection`).
 */
object RxJavaErrorHandler {

    private const val TAG = "PMM-RX"

    /**
     * Se instala una sola vez, lo antes posible en `Application.onCreate`.
     * [log] es solo para las pruebas: `android.util.Log` no existe en la JVM.
     */
    fun install(
        log: (message: String, error: Throwable) -> Unit = { message, error ->
            Log.w(TAG, message, error)
        }
    ) {
        RxJavaPlugins.setErrorHandler { error ->
            log("Error de RxJava sin destinatario; se registra y se ignora para no cerrar la app", error)
        }
    }
}
