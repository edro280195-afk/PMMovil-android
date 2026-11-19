package com.example.pmmovil.ui.salidacaseta

import android.util.Log
import com.example.pmmovil.data.camera.FotoParaSubir
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaResponse
import com.example.pmmovil.data.repository.EnvioComoArchivoNoDisponible
import com.example.pmmovil.data.repository.EnvioPorLotesNoDisponible
import com.example.pmmovil.data.repository.SalidaFotosServidor
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manda al servidor las fotos de una salida de caseta de pocas en pocas, ANTES de confirmar la salida.
 *
 * Lo comparten las dos pantallas que dan salida con fotos (la unificada y la de relación de siempre).
 *
 * Desde el 18-nov-2026 las fotos suben EN SEGUNDO PLANO en cuanto el guardia las toma ([subirEnSegundoPlano]): para
 * cuando toca Confirmar ya están en el servidor y solo falta un mensaje chico. Antes se mandaban todas hasta ese momento y
 * el guardia esperaba a que cruzaran el internet de la oficina (~8 MB de una salida de 8 fotos). Viajan como archivos
 * (~25 % menos que en base64); con un servidor anterior se mandan como base64 y, si ni siquiera tiene el envío por lotes,
 * todas juntas en la confirmación, como antes.
 *
 * Al confirmar ([confirmarConFotos]) el envío de fondo se detiene y manda la confirmación: lo que ya subió no se repite,
 * cada lote se reintenta solo y siempre se informa cuántas van.
 *
 * Una instancia por pantalla. Se usa desde el hilo principal (viewModelScope): eso evita carreras entre el envío de fondo
 * y la pantalla sin candados.
 */
class SalidaFotosEnvio(
    private val servidor: SalidaFotosServidor,
    private val tag: String,
    /** Bytes listos para subir de cada foto. En la app, [FotoParaSubir] (preparada una vez y guardada junto a la original). */
    private val preparar: suspend (String) -> ByteArray? = { ruta -> withContext(Dispatchers.Default) { FotoParaSubir.bytes(ruta) } },
    /** Espera antes de empezar a subir: junta en un mismo lote las fotos que se toman seguidas. */
    private val esperaAntesDeSubirMs: Long = ESPERA_ANTES_DE_SUBIR_MS,
    /** Bitácora (logcat en la app; nada en las pruebas, donde android.util.Log no existe). */
    private val registrar: (String) -> Unit = { mensaje -> Log.i(tag, mensaje) }
) {

    sealed interface Resultado {
        /** Todas las fotos están en el servidor: falta confirmar la salida. */
        object Listo : Resultado

        /** El servidor es una versión sin el envío por lotes: las fotos van todas juntas en la confirmación. */
        object NoDisponible : Resultado

        /** No se pudieron enviar todas. [mensaje] ya dice cuántas van y qué hacer; las fotos siguen en la tableta. */
        data class Fallo(val mensaje: String) : Resultado
    }

    sealed interface Confirmacion {
        /** La confirmación se intentó: [resultado] trae la respuesta del servidor (éxito o rechazo) o el fallo de red. */
        data class Terminada(val resultado: Result<ConfirmarSalidaCasetaResponse>) : Confirmacion

        /**
         * Las fotos no se pudieron preparar o enviar, así que NO se confirmó nada. [mensaje] dice qué hacer; las fotos
         * siguen en la tableta para reintentar.
         */
        data class SinConfirmar(val mensaje: String) : Confirmacion
    }

    /** Fotos que ya están en el servidor (ruta -> posición en que se mandaron). */
    private val subidasPorFoto = mutableMapOf<String, Int>()

    /** False cuando el servidor no recibe archivos: desde ahí, base64. */
    private var comoArchivo = true

    /** False cuando el servidor ni siquiera tiene envío por lotes: no hay nada que subir antes de confirmar. */
    private var lotesDisponibles = true

    private var fondo: Job? = null
    private var pedidoFondo: PedidoFondo? = null

    private class PedidoFondo(val relno: String, val fotos: () -> List<String>)

    /** Una foto que se quitó o se descartó: si vuelve a existir otra con esa ruta, debe mandarse de nuevo. */
    fun olvidar(ruta: String) {
        subidasPorFoto.remove(ruta)
    }

    fun olvidarTodas() = subidasPorFoto.clear()

    /** Cuántas de [rutas] ya están en el servidor, cada una en su posición. */
    fun enServidor(rutas: List<String>): Int = rutas.indices.count { subidasPorFoto[rutas[it]] == it + 1 }

    /**
     * Sube en segundo plano lo que falte de la salida de [relno] mientras el guardia sigue con lo suyo. [fotos] se vuelve
     * a leer antes de cada lote: una foto nueva o una borrada (que recorre a las demás) entra en el siguiente. Un fallo no
     * molesta al guardia: se intenta de nuevo con la siguiente foto o al confirmar, que sí avisa.
     */
    fun subirEnSegundoPlano(scope: CoroutineScope, relno: String, fotos: () -> List<String>) {
        if (!lotesDisponibles || relno.isBlank()) return
        pedidoFondo = PedidoFondo(relno, fotos)
        if (fondo?.isActive == true) return

        fondo = scope.launch {
            delay(esperaAntesDeSubirMs)
            while (isActive) {
                val pedido = pedidoFondo ?: break
                val rutas = pedido.fotos()
                val lote = SalidaFotosPlan.pendientes(rutas, subidasPorFoto).firstOrNull() ?: break
                val subida = withTimeoutOrNull(TIEMPO_LOTE_MS) { subirLote(pedido.relno, rutas, lote) }
                if (subida !is Subida.Lista) {
                    registrar("envío de fondo de ${pedido.relno} se detiene en el lote ${lote.indiceInicial}: ${subida ?: "sin respuesta a tiempo"}")
                    break
                }
            }
        }
    }

    /**
     * Da la salida de la relación con sus fotos: detiene el envío de fondo, manda por lotes lo que falte (lo ya subido no
     * se repite) y confirma. Ninguna espera es infinita. Si [rutas] está vacía solo confirma.
     */
    suspend fun confirmarConFotos(
        relno: String,
        usuario: String,
        observaciones: String,
        rutas: List<String>,
        alAvanzar: (String) -> Unit
    ): Confirmacion {
        detenerFondo()
        if (rutas.isEmpty()) {
            return confirmar(relno, usuario, observaciones, fotosBase64 = emptyList(), totalFotos = null, TIEMPO_CONFIRMAR_MS)
        }

        return when (val envio = enviar(relno, rutas, alAvanzar)) {
            Resultado.Listo -> {
                alAvanzar("Confirmando la salida…")
                confirmar(relno, usuario, observaciones, fotosBase64 = null, totalFotos = rutas.size, TIEMPO_CONFIRMAR_MS)
            }
            // Servidor sin el envío por lotes: todo junto en la confirmación, como antes (con límite de tiempo).
            Resultado.NoDisponible -> {
                alAvanzar("Preparando ${rutas.size} foto(s)…")
                val todas = withTimeoutOrNull(TIEMPO_PREPARAR_FOTOS_MS) { rutas.map { preparar(it) } }
                    ?: return Confirmacion.SinConfirmar(
                        "Preparar las fotos tardó demasiado. Tus fotos siguen guardadas: toca Confirmar para intentar de nuevo."
                    )
                val fallida = todas.indexOfFirst { it == null }
                if (fallida >= 0) return Confirmacion.SinConfirmar(mensajeFotoMala(fallida + 1))
                alAvanzar("Enviando fotos y confirmando la salida…")
                confirmar(
                    relno,
                    usuario,
                    observaciones,
                    fotosBase64 = todas.map { comoDataUri(it!!) },
                    totalFotos = null,
                    limiteMs = TIEMPO_ENVIO_UNICO_MS
                )
            }
            is Resultado.Fallo -> Confirmacion.SinConfirmar(envio.mensaje)
        }
    }

    /** Las fotos en curso se descartan (se abandonó la relación sin confirmar): el envío de fondo deja de mandarlas. */
    fun detenerEnvioDeFondo() {
        pedidoFondo = null
        fondo?.cancel()
        fondo = null
    }

    /** El envío de fondo se detiene (y espera a que suelte la red) antes de que la confirmación mande lo suyo. */
    private suspend fun detenerFondo() {
        pedidoFondo = null
        fondo?.cancelAndJoin()
        fondo = null
    }

    private suspend fun confirmar(
        relno: String,
        usuario: String,
        observaciones: String,
        fotosBase64: List<String>?,
        totalFotos: Int?,
        limiteMs: Long
    ): Confirmacion {
        val respuesta = withTimeoutOrNull(limiteMs) {
            servidor.confirmarSalida(
                relno = relno,
                usuario = usuario,
                observaciones = observaciones,
                fotosBase64 = fotosBase64,
                totalFotos = totalFotos
            )
        } ?: return Confirmacion.Terminada(
            Result.failure(
                IllegalStateException(
                    "El servidor no respondió a tiempo. Revisa si la salida ya quedó registrada antes de volver a confirmar; " +
                        "tus fotos siguen guardadas."
                )
            )
        )
        return Confirmacion.Terminada(respuesta)
    }

    /**
     * Manda por lotes lo que falte de [rutas], con reintentos y avance visible. La posición de cada foto en la lista es
     * su número en el servidor. [alAvanzar] recibe el texto de avance ("Enviando fotos: 3 de 8").
     */
    suspend fun enviar(
        relno: String,
        rutas: List<String>,
        alAvanzar: (String) -> Unit
    ): Resultado {
        if (!lotesDisponibles) return Resultado.NoDisponible
        val total = rutas.size
        val pendientes = SalidaFotosPlan.pendientes(rutas, subidasPorFoto)
        var enviadas = total - pendientes.sumOf { it.cantidad }

        for (lote in pendientes) {
            alAvanzar(SalidaFotosPlan.textoAvance(enviadas, total))
            var motivo = "La red no respondió a tiempo."
            var subido = false

            for (intento in 1..INTENTOS_POR_LOTE) {
                when (val subida = withTimeoutOrNull(TIEMPO_LOTE_MS) { subirLote(relno, rutas, lote) }) {
                    null -> motivo = "La red no respondió a tiempo."
                    Subida.Lista -> subido = true
                    Subida.NoDisponible -> return Resultado.NoDisponible
                    is Subida.Rechazada -> return Resultado.Fallo(subida.mensaje)
                    is Subida.FotoMala -> return Resultado.Fallo(mensajeFotoMala(subida.posicion))
                    is Subida.ErrorDeRed -> motivo = subida.mensaje
                }

                if (subido) break
                registrar("lote ${lote.indiceInicial} de $relno: intento $intento/$INTENTOS_POR_LOTE falló ($motivo)")
                if (intento < INTENTOS_POR_LOTE) delay(ESPERA_REINTENTO_MS * intento)
            }

            if (!subido) {
                return Resultado.Fallo(
                    "$motivo Van $enviadas de $total fotos enviadas; las demás siguen guardadas en la tableta. " +
                        "Toca Confirmar para continuar."
                )
            }

            enviadas += lote.cantidad
            alAvanzar(SalidaFotosPlan.textoAvance(enviadas, total))
        }
        return Resultado.Listo
    }

    private sealed interface Subida {
        object Lista : Subida
        object NoDisponible : Subida
        data class Rechazada(val mensaje: String) : Subida
        data class FotoMala(val posicion: Int) : Subida
        data class ErrorDeRed(val mensaje: String) : Subida
    }

    /** Un intento de un lote: prepara sus fotos, las manda como archivos (o base64 con un servidor anterior) y las anota. */
    private suspend fun subirLote(relno: String, rutas: List<String>, lote: SalidaFotosPlan.Lote): Subida {
        val bytes = (lote.desde until lote.hastaExclusivo).map { i -> preparar(rutas[i]) ?: return Subida.FotoMala(i + 1) }

        val respuesta = mandar(relno, lote.indiceInicial, bytes)
        val error = respuesta.exceptionOrNull()
        return when {
            error is EnvioPorLotesNoDisponible -> {
                lotesDisponibles = false
                Subida.NoDisponible
            }
            error != null -> Subida.ErrorDeRed(error.message ?: "No se pudieron enviar las fotos.")
            respuesta.getOrNull()?.success == true -> {
                for (i in lote.desde until lote.hastaExclusivo) subidasPorFoto[rutas[i]] = i + 1
                Subida.Lista
            }
            // El servidor las rechazó a propósito (por ejemplo, la relación ya salió): reintentar no cambia nada.
            else -> Subida.Rechazada(respuesta.getOrNull()?.message ?: "El servidor rechazó las fotos.")
        }
    }

    private suspend fun mandar(relno: String, indiceInicial: Int, bytes: List<ByteArray>): Result<ConfirmarSalidaCasetaResponse> {
        if (comoArchivo) {
            val respuesta = servidor.subirFotosSalidaArchivos(relno, indiceInicial, bytes)
            if (respuesta.exceptionOrNull() !is EnvioComoArchivoNoDisponible) return respuesta
            comoArchivo = false
            registrar("el servidor no recibe fotos como archivo: se mandan como base64")
        }
        return servidor.subirFotosSalida(relno, indiceInicial, bytes.map(::comoDataUri))
    }

    private fun mensajeFotoMala(posicion: Int) =
        "No se pudo preparar la foto $posicion. Vuelve a tomarla o elimínala antes de confirmar la salida."

    private companion object {
        // Ninguna espera es infinita: el guardia siempre termina viendo un resultado o un aviso con qué hacer.
        const val TIEMPO_PREPARAR_FOTOS_MS = 120_000L
        const val TIEMPO_CONFIRMAR_MS = 90_000L
        const val TIEMPO_ENVIO_UNICO_MS = 300_000L
        const val TIEMPO_LOTE_MS = 120_000L
        const val INTENTOS_POR_LOTE = 3
        const val ESPERA_REINTENTO_MS = 2_000L
        const val ESPERA_ANTES_DE_SUBIR_MS = 1_500L

        fun comoDataUri(bytes: ByteArray): String = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
    }
}
