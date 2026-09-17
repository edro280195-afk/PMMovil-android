package com.example.pmmovil.ui.rostro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * La guia del enrolamiento: ocho direcciones, de una en una.
 *
 * La primera version pedia llenar 24 sectores en GRADOS ABSOLUTOS y era un
 * callejon sin salida. El estimador de pose comprime el cabeceo -- solvePnP con
 * distancia focal sin calibrar subestima el pitch bastante -- asi que si el
 * pitch de una persona nunca pasa de unos 10 grados, los sectores de arriba y
 * abajo son IMPOSIBLES de alcanzar por mucho que mueva la cabeza. Se quedaba
 * en 22 de 24 sin nada que hacer.
 *
 * Esta version arregla las dos causas:
 *
 *  1. **Direcciones relativas.** El yaw y el pitch se normalizan contra lo que
 *     esa persona, con esa camara, de verdad alcanza. "Arriba" deja de ser
 *     "pitch >= 8 grados" y pasa a ser "lo mas arriba que puedes". Nadie queda
 *     fuera por una limitacion del estimador.
 *
 *  2. **Una meta a la vez.** En vez de cazar sectores sueltos, la pantalla
 *     pide una direccion concreta y espera. Siempre hay exactamente una cosa
 *     que hacer, y si una direccion no sale, se salta sola.
 */
object GuiaRostro {

    /** Ocho direcciones, empezando arriba y girando como las manecillas. */
    const val METAS = 8

    /**
     * Orden en que se piden. Arranca a la derecha y da la vuelta completa, para
     * que sea un solo movimiento continuo -- el mismo "dibuja un circulo con la
     * nariz" de iOS -- y no ocho sacudidas sueltas.
     */
    val ORDEN: List<Int> = listOf(2, 3, 4, 5, 6, 7, 0, 1)

    /** Cuantas metas hay que lograr (mas la frontal) para dar el alta por buena. */
    const val METAS_MINIMAS = 5

    /** Si una direccion no sale en este tiempo, se salta y sigue la siguiente. */
    const val MS_PARA_SALTAR = 6_000L

    /**
     * Referencias de cuanto alcanza esta persona. Se van ajustando solas con lo
     * que se observa, con topes para que un giro exagerado no deje la vara tan
     * alta que despues no se pueda alcanzar nada.
     */
    data class Calibracion(
        val yawMax: Float = 0f,
        val pitchMax: Float = 0f
    ) {
        fun con(yaw: Float, pitch: Float) = Calibracion(
            yawMax = max(yawMax, abs(yaw)),
            pitchMax = max(pitchMax, abs(pitch))
        )

        /**
         * La vara: 70 % de lo maximo observado. Si ya demostraste que llegas a
         * X, se te pide comodamente menos que X.
         */
        val yawRef: Float get() = min(max(yawMax * 0.7f, YAW_REF_MINIMO), YAW_REF_MAXIMO)
        val pitchRef: Float get() = min(max(pitchMax * 0.7f, PITCH_REF_MINIMO), PITCH_REF_MAXIMO)
    }

    /**
     * Direccion normalizada de la pose, en el plano donde 0 es arriba y el
     * angulo crece como las manecillas.
     *
     * El pitch entra negado porque la pantalla crece hacia abajo y el motor
     * entrega pitch positivo hacia arriba.
     */
    fun direccion(yaw: Float, pitch: Float, cal: Calibracion): Float {
        val x = yaw / cal.yawRef
        val y = -pitch / cal.pitchRef
        // Sin corrimiento: atan2 ya devuelve el angulo en el mismo marco que
        // [anguloDe] -- 0 a la derecha, -PI/2 arriba, creciendo como las
        // manecillas. Sumarle PI/2 aqui giraba TODAS las direcciones 90 grados:
        // girabas a la derecha y se encendia la marca de abajo.
        return atan2(y, x)
    }

    /** Que tan lejos del centro esta la pose, ya normalizada. 1.0 = en la vara. */
    fun avance(yaw: Float, pitch: Float, cal: Calibracion): Float =
        hypot(yaw / cal.yawRef, pitch / cal.pitchRef)

    /**
     * De frente: sirve para la toma frontal y la confirmación final.
     *
     * El pitch absoluto de ML Kit cambia según la altura/inclinación de la
     * cámara. Por eso no se compara contra cero; el encuadre ML Kit ya valida
     * que la cara esté dentro del círculo y aquí sólo limitamos giros claros.
     */
    fun esFrontal(yaw: Float, pitch: Float, cal: Calibracion): Boolean =
        abs(yaw) <= YAW_FRONTAL_MAX && abs(pitch) <= PITCH_FRONTAL_MAX

    /** Angulo del centro de una meta, en radianes. 0 = arriba. */
    fun anguloDe(meta: Int): Float =
        (meta.toFloat() / METAS) * 2f * PI.toFloat() - (PI.toFloat() / 2f)

    /**
     * Que tan cerca esta la pose de la meta pedida, de 0 a 1.
     *
     * Cuenta como lograda cuando pasa de [CERCANIA_LOGRADA]. Se combina lo
     * lejos que esta la cabeza del centro con que tan bien apunta a la
     * direccion: girar mucho hacia otro lado no acerca a la meta.
     */
    fun cercania(yaw: Float, pitch: Float, meta: Int, cal: Calibracion): Float {
        val avance = avance(yaw, pitch, cal)
        if (avance < AVANCE_MINIMO) return 0f

        val objetivo = anguloDe(meta)
        val actual = direccion(yaw, pitch, cal)

        // Diferencia angular mas corta entre las dos direcciones.
        var delta = abs(normalizar(actual - objetivo))
        if (delta > PI.toFloat()) delta = 2f * PI.toFloat() - delta

        // Tolerancia amplia a proposito: con ocho metas separadas 45 grados,
        // pedir punteria fina solo lograria que la gente se pelee con el aro.
        val apunta = (1f - delta / TOLERANCIA).coerceIn(0f, 1f)
        val alcance = (avance / AVANCE_META).coerceIn(0f, 1f)

        return apunta * alcance
    }

    fun lograda(yaw: Float, pitch: Float, meta: Int, cal: Calibracion): Boolean =
        cercania(yaw, pitch, meta, cal) >= CERCANIA_LOGRADA

    private fun normalizar(radianes: Float): Float {
        var a = radianes
        while (a > PI.toFloat()) a -= 2f * PI.toFloat()
        while (a < -PI.toFloat()) a += 2f * PI.toFloat()
        return a
    }

    /** Texto de la direccion, para quien prefiera leerlo antes que ver la flecha. */
    fun nombreDe(meta: Int): String = when (meta) {
        0 -> "Mira hacia arriba"
        1 -> "Arriba y a la derecha"
        2 -> "Mira a la derecha"
        3 -> "Abajo y a la derecha"
        4 -> "Mira hacia abajo"
        5 -> "Abajo y a la izquierda"
        6 -> "Mira a la izquierda"
        7 -> "Arriba y a la izquierda"
        else -> "Muévete despacio"
    }

    // Pisos y topes de la calibracion. En grados.
    private const val YAW_REF_MINIMO = 9f
    private const val YAW_REF_MAXIMO = 24f
    private const val PITCH_REF_MINIMO = 5f
    private const val PITCH_REF_MAXIMO = 18f

    /** Tolerancias de frente, independientes del montaje de la cámara. */
    private const val YAW_FRONTAL_MAX = 18f
    private const val PITCH_FRONTAL_MAX = 28f

    /** Debajo de esto no se considera que apunte a ningun lado. */
    private const val AVANCE_MINIMO = 0.42f

    /** Alcance con el que una meta se da por completamente lograda. */
    private const val AVANCE_META = 0.80f

    /** Media apertura del cono de cada meta, en radianes (~50 grados). */
    private const val TOLERANCIA = 0.88f

    private const val CERCANIA_LOGRADA = 0.62f
}
