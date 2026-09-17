package com.example.pmmovil.ui.scanner

import kotlin.math.abs
import kotlin.math.max

/** Que esta haciendo el auto-zoom ahora mismo. Solo alimenta la UI. */
enum class AutoZoomPhase {
    /** Sin intervenir: el operador toco el zoom, o se acaba de leer. */
    Idle,

    /** Hay un codigo a la vista y se esta ajustando su tamano. */
    Tracking,

    /** No se ve ningun codigo; el zoom barre buscando uno lejano. */
    Searching
}

/**
 * Auto-zoom del escaner: decide a que zoom debe estar la camara.
 *
 * Sustituye al `ZoomSuggestionOptions` de ML Kit, que en campo no servia por
 * dos motivos de fondo:
 *
 * 1. **Solo sugiere zoom sobre codigos que ya vio.** Un QR lejano no genera
 *    ninguna deteccion, asi que el callback no se dispara nunca y el zoom se
 *    queda quieto: justo el caso en el que hace falta. Aqui eso lo cubre el
 *    barrido de busqueda ([onIdle]).
 * 2. **ML Kit asume que la camara arranca en 1.0x** y que solo el se mueve.
 *    Las pantallas de bodega arrancan en 1.8x, y ademas hay pellizco, botones
 *    1x/2x/4x y reset tras cada lectura: su idea del zoom actual queda
 *    desfasada y sus sugerencias salen calculadas contra una base falsa
 *    (llegaba a pedir *alejar* estando ya corto de zoom).
 *
 * Este controlador es de lazo cerrado sobre una sola magnitud: **que fraccion
 * del lado corto del frame ocupa el codigo** ([BarcodeVisualCandidate.minSideFraction]).
 * ML Kit necesita ~2 pixeles por modulo para decodificar, y el lado corto es
 * el que se queda sin pixeles primero, asi que llevar esa fraccion a
 * [targetSize] es exactamente lo que hace que el codigo se lea.
 *
 * Es la unica fuente de zoom automatico del escaner. Tener dos sistemas
 * decidiendo el zoom a la vez ya provoco antes oscilaciones de acercar/alejar
 * en cada frame.
 *
 * La clase es pura (sin Android) para poder probarla, y esta sincronizada
 * porque la alimentan dos hilos: el executor del analyzer (candidatos) y el
 * hilo principal (tick de inactividad).
 */
class AutoZoomController(
    /** Tamano al que se intenta llevar el codigo. */
    private val targetSize: Float = TARGET_SIZE,
    /** Por debajo de esto el codigo es muy chico para leerse comodo. */
    private val minSize: Float = MIN_SIZE,
    /** Por encima de esto el codigo se sale del encuadre. */
    private val maxSize: Float = MAX_SIZE,
    private val adjustCooldownMs: Long = ADJUST_COOLDOWN_MS,
    private val searchDelayMs: Long = SEARCH_DELAY_MS,
    private val searchStepMs: Long = SEARCH_STEP_MS,
    private val searchStep: Float = SEARCH_STEP,
    private val searchRestMs: Long = SEARCH_REST_MS,
    private val maxSearchCycles: Int = MAX_SEARCH_CYCLES,
    private val manualPauseMs: Long = MANUAL_PAUSE_MS,
    private val scanPauseMs: Long = SCAN_PAUSE_MS
) {

    /** Zoom de reposo de la pantalla; a el vuelve el barrido en cada ciclo. */
    var restingZoom: Float = 1f
        set(value) {
            field = value.coerceAtLeast(1f)
        }

    /**
     * Techo del automatico. Se fija al enlazar la camara con el maximo real
     * del hardware, acotado por [AUTO_ZOOM_CEILING]: mas alla de ahi el zoom
     * digital ya solo interpola y deja de aportar detalle al decodificador.
     */
    var ceilingZoom: Float = AUTO_ZOOM_CEILING
        set(value) {
            field = value.coerceIn(1f, AUTO_ZOOM_CEILING)
        }

    @Volatile
    private var phaseValue: AutoZoomPhase = AutoZoomPhase.Idle

    /** Lectura sin bloqueo para la UI. */
    val phase: AutoZoomPhase
        get() = phaseValue

    private var pausedUntil: Long = 0L
    private var idleSince: Long = 0L
    private var lastAdjustAt: Long = 0L
    private var lastSize: Float = 0f
    private var stableFrames: Int = 0
    private var lastSearchStepAt: Long = 0L
    private var searchResting: Boolean = false
    private var searchCycles: Int = 0

    /**
     * Hay un codigo (leido o solo detectado) en el frame.
     *
     * @param currentZoom zoom real al que se capturo el frame.
     * @param sizeFraction lado corto del codigo respecto al lado corto del frame.
     * @param decoded ML Kit ya saco el valor: no hace falta tocar nada mas.
     * @param canAdjust la camara ya asento el zoom anterior. Si es false se
     *   sigue midiendo (para no perder la racha de estabilidad) pero no se
     *   mueve nada: medir a mitad de una animacion daria un lazo inestable.
     * @return zoom objetivo, o null si no hay que moverse.
     */
    @Synchronized
    fun onCandidate(
        now: Long,
        currentZoom: Float,
        sizeFraction: Float,
        decoded: Boolean,
        canAdjust: Boolean
    ): Float? {
        // Ver un codigo cancela la busqueda: ya encontramos a que apuntar.
        idleSince = 0L
        resetSearch()

        if (isPaused(now)) {
            phaseValue = AutoZoomPhase.Idle
            return null
        }
        phaseValue = AutoZoomPhase.Tracking
        if (sizeFraction <= 0f) return null

        // El bounding box de un codigo aun sin decodificar tiembla bastante
        // frame a frame, y con `enableAllPotentialBarcodes()` cualquier textura
        // regular puede colarse un frame suelto. Se exigen dos medidas seguidas
        // coherentes antes de mover la camara.
        val coherent = lastSize > 0f &&
            abs(sizeFraction - lastSize) <= lastSize * SIZE_COHERENCE
        stableFrames = if (coherent) stableFrames + 1 else 1
        lastSize = sizeFraction
        if (stableFrames < MIN_STABLE_FRAMES) return null

        if (!canAdjust) return null
        if (now - lastAdjustAt < adjustCooldownMs) return null

        // Si ya se decodifico no se toca el zoom: la lectura esta hecha y
        // moverse justo en ese instante solo puede estropearla.
        if (decoded) return null
        val tooSmall = sizeFraction < minSize
        val tooBig = sizeFraction > maxSize
        if (!tooSmall && !tooBig) return null

        val factor = (targetSize / sizeFraction).coerceIn(MIN_STEP_FACTOR, MAX_STEP_FACTOR)
        val next = (currentZoom * factor).coerceIn(1f, ceilingZoom)
        if (abs(next - currentZoom) < currentZoom * MIN_RELATIVE_CHANGE) return null

        lastAdjustAt = now
        return next
    }

    /**
     * No hay ningun codigo a la vista. Tras [searchDelayMs] el zoom empieza a
     * subir en escalones cortos hasta el techo de busqueda, vuelve al reposo y
     * repite: es lo que permite enganchar una etiqueta lejana sin que el
     * operador tenga que acercar a mano.
     *
     * Se llama periodicamente desde la UI.
     *
     * @return zoom objetivo, o null si toca esperar.
     */
    @Synchronized
    fun onIdle(now: Long, currentZoom: Float): Float? {
        if (isPaused(now)) {
            phaseValue = AutoZoomPhase.Idle
            idleSince = 0L
            return null
        }
        if (idleSince == 0L) {
            idleSince = now
            // La racha de candidatos no sobrevive a perder el codigo de vista.
            lastSize = 0f
            stableFrames = 0
        }
        if (now - idleSince < searchDelayMs) {
            phaseValue = AutoZoomPhase.Idle
            return null
        }

        val top = searchTop()
        // Sin recorrido util (equipo sin zoom) no se barre: solo seria ruido.
        if (top <= restingZoom * MIN_SEARCH_SPAN) {
            phaseValue = AutoZoomPhase.Idle
            return null
        }
        // Tras varios ciclos sin encontrar nada se deja de mover la camara:
        // el problema no era el zoom y una preview que nunca para molesta.
        if (searchCycles >= maxSearchCycles) {
            phaseValue = AutoZoomPhase.Idle
            return null
        }

        phaseValue = AutoZoomPhase.Searching
        if (searchResting) {
            if (now - lastSearchStepAt < searchRestMs) return null
            searchResting = false
            lastSearchStepAt = now
            return null
        }
        if (lastSearchStepAt > 0L && now - lastSearchStepAt < searchStepMs) return null

        lastSearchStepAt = now
        if (currentZoom >= top - SEARCH_TOP_EPSILON) {
            // Techo alcanzado sin enganchar nada: se vuelve al reposo para
            // recuperar campo de vision y se reintenta desde abajo.
            searchResting = true
            searchCycles += 1
            return restingZoom
        }
        return (currentZoom + searchStep).coerceAtMost(top)
    }

    /** El operador uso pellizco o los botones de zoom: mandan sus dedos. */
    @Synchronized
    fun onManualZoom(now: Long) {
        pausedUntil = now + manualPauseMs
        resetTracking()
        phaseValue = AutoZoomPhase.Idle
    }

    /** Se leyo un codigo: pausa corta para no pelear con el feedback visual. */
    @Synchronized
    fun onScanned(now: Long) {
        pausedUntil = now + scanPauseMs
        resetTracking()
        phaseValue = AutoZoomPhase.Idle
    }

    /** Vuelve al estado inicial (cambio de pantalla, re-enlace de camara). */
    @Synchronized
    fun reset() {
        pausedUntil = 0L
        resetTracking()
        phaseValue = AutoZoomPhase.Idle
    }

    private fun isPaused(now: Long): Boolean = pausedUntil > 0L && now < pausedUntil

    private fun resetTracking() {
        idleSince = 0L
        lastAdjustAt = 0L
        lastSize = 0f
        stableFrames = 0
        resetSearch()
    }

    private fun resetSearch() {
        lastSearchStepAt = 0L
        searchResting = false
        searchCycles = 0
    }

    /**
     * Techo del barrido. Se acota respecto al reposo para que una pantalla que
     * ya arranca acercada no se dispare, pero garantizando un minimo util en
     * las que arrancan en 1x.
     */
    private fun searchTop(): Float =
        max(restingZoom * SEARCH_SPAN, SEARCH_MIN_TOP).coerceAtMost(ceilingZoom)

    companion object {
        /**
         * Fraccion del lado corto del frame que se busca que ocupe el codigo.
         * A 1080p de analisis son ~324 px de lado: ~8 px por modulo en un QR
         * de 41 modulos, con margen de sobra para movimiento y sombras.
         */
        const val TARGET_SIZE = 0.26f

        /** Por debajo de esto se acerca. ~4 px por modulo: al limite. */
        const val MIN_SIZE = 0.18f

        /** Por encima de esto se aleja: el codigo empieza a no caber entero. */
        const val MAX_SIZE = 0.50f

        /**
         * Techo absoluto del automatico. Por encima el zoom digital solo
         * interpola pixeles y estrecha el campo de vision, que es peor para
         * encontrar el codigo. El operador si puede pasar de aqui a mano.
         */
        const val AUTO_ZOOM_CEILING = 5f

        /** Cuanto puede cambiar el zoom de una sola vez. */
        const val MAX_STEP_FACTOR = 1.22f
        const val MIN_STEP_FACTOR = 0.84f

        /** Cambios mas chicos que esto no se piden: no se notan y marean. */
        const val MIN_RELATIVE_CHANGE = 0.05f

        /** Medidas seguidas coherentes antes de hacer caso a un candidato. */
        const val MIN_STABLE_FRAMES = 3

        /** Cuanto puede variar el tamano entre frames y seguir siendo el mismo codigo. */
        const val SIZE_COHERENCE = 0.45f

        /** Espera entre correcciones, para medir ya con el zoom nuevo aplicado. */
        const val ADJUST_COOLDOWN_MS = 650L

        /** Sin ver nada durante este tiempo, empieza el barrido de busqueda. */
        const val SEARCH_DELAY_MS = 1_100L

        /**
         * Cadencia y tamano del escalon del barrido: lento y controlado,
         * repartido en escalones con reposo entre medio en vez de un
         * desplazamiento continuo. Con el recorte del sensor moviendose sin
         * parar, cada frame que analiza ML Kit sale con arrastre y detecta
         * peor: justo lo contrario de lo que busca el barrido. Cada escalon
         * tarda ~0,25 s en asentarse y deja otros ~0,25 s de imagen quieta.
         */
        const val SEARCH_STEP_MS = 700L
        const val SEARCH_STEP = 0.18f

        /** Pausa en el reposo antes de volver a subir. */
        const val SEARCH_REST_MS = 900L

        /** Ciclos completos de barrido sin encontrar nada antes de rendirse. */
        const val MAX_SEARCH_CYCLES = 2

        /** Cuanto manda el operador tras tocar el zoom. */
        const val MANUAL_PAUSE_MS = 4_000L

        /** Pausa tras una lectura. */
        const val SCAN_PAUSE_MS = 700L

        /** El barrido llega a este multiplo del reposo... */
        const val SEARCH_SPAN = 1.9f

        /** ...o a este zoom absoluto, lo que sea mayor. */
        const val SEARCH_MIN_TOP = 2.4f

        /** Recorrido minimo para que valga la pena barrer. */
        const val MIN_SEARCH_SPAN = 1.15f

        private const val SEARCH_TOP_EPSILON = 0.02f
    }
}
