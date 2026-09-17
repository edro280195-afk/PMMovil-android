package com.example.pmmovil.ui.camera

/**
 * Decide cuándo la imagen de la cámara ya es de fiar después de (re)abrirla,
 * y cuándo la sesión nació muerta y hay que reabrirla.
 *
 * Al arrancar el sensor, la exposición automática (AE) del HAL parte del
 * estado en que quedó la sesión anterior y tarda entre unos cuadros y un par
 * de segundos en converger. En ese lapso los cuadros salen negros, quemados o
 * a medio camino, y cualquier orden que la app mande a la cámara en ese
 * momento (enfoque, medición, zoom) alarga o congela la convergencia. En la
 * Ulefone Armor Pad Pro (MediaTek MT8788) eso se veía como 2 s de negro y 2 s
 * de blanco cada vez que se abría un escáner.
 *
 * Esta clase es lógica pura (sin Android) para poder probarla. La alimenta
 * [CameraWarmupMonitor] con el estado AE de cada cuadro y, si existe, con la
 * luminancia medida por el analizador de imagen.
 *
 * Reglas, en orden:
 * 1. Con AE reportado: listo cuando hay al menos [minFrames] cuadros y
 *    [stableFrames] consecutivos convergidos, siempre que la luminancia
 *    conocida no sea de un cuadro negro o quemado. Con [requireLuminance]
 *    la medida de luminancia es obligatoria: el HAL de esta tableta llega a
 *    reportar "convergida" con el cuadro todavía blanco, y la única prueba
 *    de que la imagen es real es mirarla.
 * 2. Si el HAL nunca reporta el estado AE: listo tras [framesWithoutAeState]
 *    cuadros (no se puede esperar a algo que no va a llegar).
 * 3. Red de seguridad: listo a más tardar [frameTimeoutMs] después del primer
 *    cuadro, aunque la AE no haya convergido (la cámara funciona; la escena
 *    puede ser genuinamente oscura o brillante).
 * 4. Si pasan [bindTimeoutMs] desde el enlace **sin un solo cuadro**, la
 *    sesión está muerta: se declara [isFailed] en vez de levantar la cortina
 *    sobre un visor que no va a mostrar nada bueno. Quien escucha reabre.
 *
 * QUÉ CUENTA COMO "UN CUADRO". No sólo los resultados de captura de Camera2
 * ([onFrame], metadatos vía `CaptureCallback`) -- también un cuadro de
 * imagen real que haya llegado al analizador ([onLuminance]). Se necesitaron
 * los dos: en la Armor Pad Pro se midió una sesión donde la app declaraba
 * "sin-cuadros" (cero resultados de captura en 2 s) mientras el escáner de
 * códigos, alimentado por el MISMO `ImageAnalysis`, decodificaba un QR real
 * -- el HAL entregaba las imágenes pero se quedaba mudo en los metadatos. Con
 * un solo camino para "hay cuadro" [isFailed] disparaba sobre una sesión que
 * en realidad funcionaba, y la pantalla la mataba y reabría a media lectura.
 * Ahora cualquiera de las dos señales cuenta como prueba de vida: sin
 * metadatos pero con imágenes reales, la sesión se declara lista por el
 * plazo de enlace ([bindTimeoutMs]) en vez de fallida.
 */
class CameraWarmupTracker(
    private val minFrames: Int = MIN_FRAMES,
    private val stableFrames: Int = STABLE_FRAMES,
    private val framesWithoutAeState: Int = FRAMES_WITHOUT_AE_STATE,
    private val frameTimeoutMs: Long = FRAME_TIMEOUT_MS,
    private val bindTimeoutMs: Long = BIND_TIMEOUT_MS,
    private val luminanceMaxAgeMs: Long = LUMINANCE_MAX_AGE_MS,
    private val requireLuminance: Boolean = false
) {
    var isReady: Boolean = false
        private set

    /** La sesión no entregó ningún cuadro en el plazo: hay que reabrir. */
    var isFailed: Boolean = false
        private set

    /** Motivo por el que se declaró listo (o fallido); útil en la traza. */
    var readyReason: String? = null
        private set

    var framesSeen: Int = 0
        private set

    var firstFrameAtMs: Long = NONE
        private set

    var boundAtMs: Long = NONE
        private set

    private var consecutiveConverged: Int = 0
    private var aeStateReported: Boolean = false
    private var lastLuminance: Double = Double.NaN
    private var lastLuminanceAtMs: Long = NONE

    /** Se enlazó la cámara: arranca el reloj de la red de seguridad. */
    fun onBound(nowMs: Long) {
        reset()
        boundAtMs = nowMs
    }

    fun reset() {
        isReady = false
        isFailed = false
        readyReason = null
        framesSeen = 0
        firstFrameAtMs = NONE
        boundAtMs = NONE
        consecutiveConverged = 0
        aeStateReported = false
        lastLuminance = Double.NaN
        lastLuminanceAtMs = NONE
    }

    /**
     * Luminancia media (0..1) de un cuadro analizado. Llega por otro camino y a
     * otra cadencia que los resultados de captura, por eso se guarda con su
     * marca de tiempo y se usa solo si es reciente.
     *
     * TAMBIÉN cuenta como prueba de vida de la sesión, igual que [onFrame]:
     * es una imagen real que salió del sensor y llegó al analizador, venga o
     * no acompañada de metadatos de captura. Ver la nota de la clase sobre
     * por qué esto es necesario y no un detalle menor.
     */
    fun onLuminance(nowMs: Long, luminance: Double) {
        lastLuminance = luminance
        lastLuminanceAtMs = nowMs
        if (!isReady && !isFailed && firstFrameAtMs == NONE) firstFrameAtMs = nowMs
    }

    /**
     * Resultado de captura de un cuadro.
     *
     * @param aeState valor de `CONTROL_AE_STATE`, o null si el HAL no lo entrega.
     * @return true solo en la llamada en la que se pasa a listo.
     */
    fun onFrame(nowMs: Long, aeState: Int?): Boolean {
        if (isReady || isFailed) return false
        framesSeen += 1
        if (firstFrameAtMs == NONE) firstFrameAtMs = nowMs

        if (aeState != null) {
            aeStateReported = true
            consecutiveConverged = if (aeState in CONVERGED_STATES) consecutiveConverged + 1 else 0
        }

        val converged = aeStateReported &&
            framesSeen >= minFrames &&
            consecutiveConverged >= stableFrames
        if (converged && luminanceLooksSane(nowMs)) {
            return markReady("ae-convergida")
        }
        if (!aeStateReported && framesSeen >= framesWithoutAeState && luminanceLooksSane(nowMs)) {
            return markReady("sin-estado-ae")
        }
        return checkTimeouts(nowMs)
    }

    /**
     * Para vencer los tiempos límite aunque no lleguen cuadros.
     *
     * @return true en la llamada en la que se pasa a listo O a fallido.
     */
    fun tick(nowMs: Long): Boolean {
        if (isReady || isFailed) return false
        return checkTimeouts(nowMs)
    }

    private fun checkTimeouts(nowMs: Long): Boolean {
        if (firstFrameAtMs != NONE && nowMs - firstFrameAtMs >= frameTimeoutMs) {
            return markReady("tiempo-limite-cuadros")
        }
        if (boundAtMs != NONE && nowMs - boundAtMs >= bindTimeoutMs) {
            // firstFrameAtMs (no framesSeen) es la prueba de vida correcta:
            // lo pone tanto un resultado de captura como una imagen real
            // analizada, y una sesión con imágenes pero sin metadatos SÍ
            // sirve para leer códigos aunque nunca reporte AE.
            return if (firstFrameAtMs == NONE) {
                markFailed("sin-cuadros")
            } else {
                markReady("tiempo-limite-enlace")
            }
        }
        return false
    }

    private fun luminanceLooksSane(nowMs: Long): Boolean {
        val fresh = lastLuminanceAtMs != NONE && nowMs - lastLuminanceAtMs <= luminanceMaxAgeMs
        if (!fresh) {
            // Sin medida reciente: si la pantalla mide, hay que esperarla; si
            // no mide (fotos sin análisis), no hay motivo para dudar del HAL.
            return !requireLuminance
        }
        return lastLuminance > LUMINANCE_BLACK && lastLuminance < LUMINANCE_BURNT
    }

    private fun markReady(reason: String): Boolean {
        isReady = true
        readyReason = reason
        return true
    }

    private fun markFailed(reason: String): Boolean {
        isFailed = true
        readyReason = reason
        return true
    }

    companion object {
        private const val NONE = Long.MIN_VALUE

        /** `CONTROL_AE_STATE_CONVERGED`, `_LOCKED` y `_FLASH_REQUIRED`. */
        val CONVERGED_STATES: Set<Int> = setOf(2, 3, 4)

        const val MIN_FRAMES = 3
        const val STABLE_FRAMES = 2
        const val FRAMES_WITHOUT_AE_STATE = 8
        const val FRAME_TIMEOUT_MS = 2_500L

        /**
         * Sin un solo cuadro en este plazo la sesión está muerta. Medido en
         * la Armor Pad Pro: el primer cuadro llega 0.6-0.7 s después de
         * enlazar (abrir ~30 ms, configurar sesión ~300 ms, arranque del
         * sensor ~300 ms); una sesión que el HAL acepta pero nunca alimenta
         * se reconoce así en 2 s en vez de dejar la cortina puesta.
         */
        const val BIND_TIMEOUT_MS = 2_000L
        const val LUMINANCE_MAX_AGE_MS = 600L

        /** Debajo de esto el cuadro es prácticamente negro. */
        const val LUMINANCE_BLACK = 0.05

        /** Encima de esto el cuadro está quemado. */
        const val LUMINANCE_BURNT = 0.95
    }
}
