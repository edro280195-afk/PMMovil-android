package com.example.pmmovil.ui.salidacaseta

import android.util.Log
import com.example.pmmovil.data.model.ConfirmarSalidaCasetaResponse
import com.example.pmmovil.data.repository.CasetaRepository
import com.example.pmmovil.data.repository.EnvioPorLotesNoDisponible
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Manda al servidor las fotos de una salida de caseta de pocas en pocas, ANTES de confirmar la salida.
 *
 * Lo comparten las dos pantallas que dan salida con fotos (la unificada y la de relación de siempre) para que ninguna
 * vuelva a mandar las 8 o 10 fotos en una sola petición: con la red lenta de la caseta eso dejaba al guardia mirando un
 * spinner sin avance y, si algo fallaba, todo empezaba de cero. Aquí cada lote se reintenta solo, lo que ya llegó no se
 * repite y siempre se informa cuántas van.
 *
 * Una instancia por pantalla: recuerda qué fotos ya están en el servidor para que un reintento continúe donde se quedó.
 */
class SalidaFotosEnvio(
    private val casetaRepository: CasetaRepository,
    private val tag: String
) {

    sealed interface Resultado {
        /** Todas las fotos están en el servidor: falta confirmar la salida con [CasetaRepository.confirmarSalida]. */
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

    /** Una foto que se quitó o se descartó: si vuelve a existir otra con esa ruta, debe mandarse de nuevo. */
    fun olvidar(ruta: String) {
        subidasPorFoto.remove(ruta)
    }

    fun olvidarTodas() = subidasPorFoto.clear()

    /**
     * Da la salida de la relación con sus fotos: las prepara con [codificar], las manda por lotes y confirma. Ninguna
     * espera es infinita. Si [rutas] está vacía solo confirma. [codificar] puede lanzar si una foto no se pudo procesar.
     */
    suspend fun confirmarConFotos(
        relno: String,
        usuario: String,
        observaciones: String,
        rutas: List<String>,
        codificar: suspend (List<String>) -> List<String>,
        alAvanzar: (String) -> Unit
    ): Confirmacion {
        if (rutas.isEmpty()) {
            return confirmar(relno, usuario, observaciones, fotosBase64 = emptyList(), totalFotos = null, TIEMPO_CONFIRMAR_MS)
        }

        alAvanzar("Preparando ${rutas.size} foto(s)…")
        val codificadas = withTimeoutOrNull(TIEMPO_PREPARAR_FOTOS_MS) { codificar(rutas) }
            ?: return Confirmacion.SinConfirmar(
                "Preparar las fotos tardó demasiado. Tus fotos siguen guardadas: toca Confirmar para intentar de nuevo."
            )

        return when (val envio = enviar(relno, rutas, codificadas, alAvanzar)) {
            Resultado.Listo -> {
                alAvanzar("Confirmando la salida…")
                confirmar(relno, usuario, observaciones, fotosBase64 = null, totalFotos = rutas.size, TIEMPO_CONFIRMAR_MS)
            }
            // Servidor sin el envío por lotes: todo junto en la confirmación, como antes (con límite de tiempo).
            Resultado.NoDisponible -> {
                alAvanzar("Enviando fotos y confirmando la salida…")
                confirmar(relno, usuario, observaciones, fotosBase64 = codificadas, totalFotos = null, TIEMPO_ENVIO_UNICO_MS)
            }
            is Resultado.Fallo -> Confirmacion.SinConfirmar(envio.mensaje)
        }
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
            casetaRepository.confirmarSalida(
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
     * [rutas] y [codificadas] van en el mismo orden; la posición de cada foto en la lista es su número en el servidor.
     * [alAvanzar] recibe el texto de avance ("Enviando fotos: 3 de 8") para mostrarlo mientras se trabaja.
     */
    suspend fun enviar(
        relno: String,
        rutas: List<String>,
        codificadas: List<String>,
        alAvanzar: (String) -> Unit
    ): Resultado {
        val total = rutas.size
        val pendientes = SalidaFotosPlan.pendientes(rutas, subidasPorFoto)
        var enviadas = total - pendientes.sumOf { it.cantidad }

        for (lote in pendientes) {
            alAvanzar(SalidaFotosPlan.textoAvance(enviadas, total))
            var subido = false
            var motivo = "La red no respondió a tiempo."

            for (intento in 1..INTENTOS_POR_LOTE) {
                val respuesta = withTimeoutOrNull(TIEMPO_LOTE_MS) {
                    casetaRepository.subirFotosSalida(relno, lote.indiceInicial, codificadas.subList(lote.desde, lote.hastaExclusivo))
                }

                if (respuesta == null) {
                    motivo = "La red no respondió a tiempo."
                } else {
                    val error = respuesta.exceptionOrNull()
                    when {
                        error is EnvioPorLotesNoDisponible -> return Resultado.NoDisponible
                        error != null -> motivo = error.message ?: "No se pudieron enviar las fotos."
                        respuesta.getOrNull()?.success == true -> subido = true
                        // El servidor las rechazó a propósito (por ejemplo, la relación ya salió): reintentar no cambia nada.
                        else -> return Resultado.Fallo(respuesta.getOrNull()?.message ?: "El servidor rechazó las fotos.")
                    }
                }

                if (subido) break
                Log.w(tag, "lote ${lote.indiceInicial} de $relno: intento $intento/$INTENTOS_POR_LOTE falló ($motivo)")
                if (intento < INTENTOS_POR_LOTE) delay(ESPERA_REINTENTO_MS * intento)
            }

            if (!subido) {
                return Resultado.Fallo(
                    "$motivo Van $enviadas de $total fotos enviadas; las demás siguen guardadas en la tableta. " +
                        "Toca Confirmar para continuar."
                )
            }

            for (i in lote.desde until lote.hastaExclusivo) subidasPorFoto[rutas[i]] = i + 1
            enviadas += lote.cantidad
            alAvanzar(SalidaFotosPlan.textoAvance(enviadas, total))
        }
        return Resultado.Listo
    }

    private companion object {
        // Ninguna espera es infinita: el guardia siempre termina viendo un resultado o un aviso con qué hacer.
        const val TIEMPO_PREPARAR_FOTOS_MS = 120_000L
        const val TIEMPO_CONFIRMAR_MS = 90_000L
        const val TIEMPO_ENVIO_UNICO_MS = 300_000L
        const val TIEMPO_LOTE_MS = 120_000L
        const val INTENTOS_POR_LOTE = 3
        const val ESPERA_REINTENTO_MS = 2_000L
    }
}
