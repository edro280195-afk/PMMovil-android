package com.example.pmmovil.ui.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.SystemClock
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import com.example.pmmovil.data.camera.CameraTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/**
 * Observa los resultados de captura de la cámara para saber cuándo la
 * exposición automática ya convergió tras abrirla, y expone ese estado a la
 * UI como [isReady]. Si la sesión no entrega ningún cuadro en el plazo,
 * expone [isFailed] para que la pantalla reabra en vez de enseñar un visor
 * muerto.
 *
 * Se engancha al `Preview` con [attachTo] (callback de sesión de Camera2 a
 * través de `Camera2Interop`), así recibe un resultado por cuadro sin costo
 * extra: son los metadatos que el HAL ya produce. Mientras no esté listo, las
 * pantallas tapan el visor con [CameraWarmupCurtain] y el escáner no manda
 * enfoque, medición ni zoom; en la Armor Pad Pro cualquiera de esas órdenes
 * durante el arranque congelaba la exposición en negro o en blanco.
 *
 * Con la traza activa ([CameraTrace]) registra el estado AE, el tiempo de
 * exposición y la sensibilidad de cada cuadro del arranque: es la evidencia
 * para afinar los umbrales por equipo.
 *
 * @param requireLuminance las pantallas que analizan cuadros (escáner) deben
 *   exigir una medida de luminancia real antes de darse por listas: el HAL de
 *   la tableta reporta "AE convergida" con el cuadro todavía quemado.
 */
@OptIn(ExperimentalCamera2Interop::class)
@SuppressLint("UnsafeOptInUsageError")
class CameraWarmupMonitor(
    private val label: String,
    requireLuminance: Boolean = false,
    private val clock: () -> Long = SystemClock::elapsedRealtime
) {
    private val tracker = CameraWarmupTracker(requireLuminance = requireLuminance)
    private val lock = Any()
    private val _isReady = MutableStateFlow(false)
    private val _isFailed = MutableStateFlow(false)

    /** true cuando la imagen ya es de fiar (o venció la red de seguridad). */
    val isReady: StateFlow<Boolean> = _isReady

    /** true cuando la sesión no entregó cuadros en el plazo: hay que reabrir. */
    val isFailed: StateFlow<Boolean> = _isFailed

    private var lastLuminance: Double = Double.NaN

    // Ultimo estado de enfoque del visor y cuando llego. Lo lee la captura de evidencia (FocusGate) para no disparar con
    // el lente todavia buscando. Se escribe desde el hilo de la camara y se lee desde el principal.
    @Volatile private var lastAfState: Int? = null
    @Volatile private var lastAfAtMs: Long = Long.MIN_VALUE

    /**
     * Estado de enfoque (CONTROL_AF_STATE) del ultimo cuadro del visor, o null si no hay dato o es mas viejo que
     * [maxAgeMs]: un visor atorado no dice nada del lente de ahora.
     */
    fun currentAfState(maxAgeMs: Long): Int? =
        if (lastAfAtMs != Long.MIN_VALUE && clock() - lastAfAtMs <= maxAgeMs) lastAfState else null

    val captureCallback: CameraCaptureSession.CaptureCallback =
        object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: CameraCaptureSession,
                request: CaptureRequest,
                result: TotalCaptureResult
            ) {
                onResult(result)
            }
        }

    /** Registra el callback de sesión en el builder del `Preview`. */
    fun attachTo(builder: Preview.Builder): Preview.Builder {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
        return builder
    }

    /**
     * Igual, pero sobre el `ImageAnalysis`.
     *
     * Lo necesita el acceso facial: esa pantalla enlaza SOLO análisis, sin
     * `Preview`, porque a propósito no muestra la cámara. Sin este enganche el
     * estado AE no llegaría nunca y el arranque tendría que esperar la red de
     * seguridad -- una eternidad para un login.
     */
    fun attachTo(builder: ImageAnalysis.Builder): ImageAnalysis.Builder {
        Camera2Interop.Extender(builder).setSessionCaptureCallback(captureCallback)
        return builder
    }

    /** Se va a enlazar la cámara: el arranque se cuenta desde cero. */
    fun onBound() {
        synchronized(lock) {
            tracker.onBound(clock())
            lastLuminance = Double.NaN
        }
        _isReady.value = false
        _isFailed.value = false
        CameraTrace.d("[$label] camara enlazada; esperando convergencia AE")
    }

    /** Luminancia media (0..1) del último cuadro analizado, si la pantalla la mide. */
    fun onFrameLuminance(luminance: Double) {
        synchronized(lock) {
            lastLuminance = luminance
            tracker.onLuminance(clock(), luminance)
        }
    }

    private var ultimaTrazaMs = Long.MIN_VALUE

    /** Vence los tiempos límite aunque el HAL no entregue cuadros. */
    fun tick() {
        val changed = synchronized(lock) { tracker.tick(clock()) }
        if (changed) publishOutcome()
    }

    fun reset() {
        synchronized(lock) { tracker.reset() }
        _isReady.value = false
        _isFailed.value = false
    }

    private fun onResult(result: TotalCaptureResult) {
        val aeState = result.get(CaptureResult.CONTROL_AE_STATE)
        val afState = result.get(CaptureResult.CONTROL_AF_STATE)
        val exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)
        val sensitivity = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val aeLock = result.get(CaptureResult.CONTROL_AE_LOCK)
        val now = clock()
        lastAfState = afState
        lastAfAtMs = now

        val changed: Boolean
        val frame: Int
        val shouldTrace: Boolean
        val sinceBound: Long
        val luminance: Double
        synchronized(lock) {
            changed = tracker.onFrame(now, aeState)
            frame = tracker.framesSeen
            luminance = lastLuminance
            sinceBound = if (tracker.boundAtMs != Long.MIN_VALUE) now - tracker.boundAtMs else -1L
            // Cuadro a cuadro durante el arranque; después una muestra por
            // segundo REAL. El criterio es "todavía no está listo", NO el
            // contador de cuadros, que deja de crecer al declararse listo.
            val esArranque = !tracker.isReady && !tracker.isFailed
            shouldTrace = CameraTrace.enabled &&
                (esArranque || now - ultimaTrazaMs >= MS_ENTRE_TRAZAS)
            if (shouldTrace) ultimaTrazaMs = now
        }
        if (shouldTrace) {
            CameraTrace.d(
                "[$label] f=$frame t+${sinceBound}ms ae=${aeStateName(aeState)} af=${afState ?: "-"}" +
                    " exp=${formatExposure(exposureNs)} iso=${sensitivity ?: "-"}" +
                    " aeLock=${aeLock ?: "-"} lum=${formatLuminance(luminance)}"
            )
        }
        if (changed) publishOutcome()
    }

    private fun publishOutcome() {
        val reason: String?
        val frames: Int
        val elapsed: Long
        val failed: Boolean
        synchronized(lock) {
            reason = tracker.readyReason
            frames = tracker.framesSeen
            failed = tracker.isFailed
            elapsed = if (tracker.boundAtMs != Long.MIN_VALUE) clock() - tracker.boundAtMs else -1L
        }
        if (failed) {
            _isFailed.value = true
            CameraTrace.w(
                "[$label] sesion de camara muerta (${reason ?: "?"}): $frames cuadros en ${elapsed}ms; se reabre"
            )
        } else {
            _isReady.value = true
            CameraTrace.d(
                "[$label] camara lista (${reason ?: "?"}) tras $frames cuadros, ${elapsed}ms desde el enlace"
            )
        }
    }

    private fun formatExposure(exposureNs: Long?): String =
        if (exposureNs == null) "-" else String.format(Locale.US, "%.1fms", exposureNs / 1_000_000.0)

    private fun formatLuminance(luminance: Double): String =
        if (luminance.isNaN()) "-" else String.format(Locale.US, "%.2f", luminance)

    private fun aeStateName(state: Int?): String = when (state) {
        null -> "-"
        CaptureResult.CONTROL_AE_STATE_INACTIVE -> "INACTIVE"
        CaptureResult.CONTROL_AE_STATE_SEARCHING -> "SEARCHING"
        CaptureResult.CONTROL_AE_STATE_CONVERGED -> "CONVERGED"
        CaptureResult.CONTROL_AE_STATE_LOCKED -> "LOCKED"
        CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED -> "FLASH_REQ"
        CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "PRECAPTURE"
        else -> state.toString()
    }

    private companion object {
        /** Una muestra por segundo una vez pasado el arranque. */
        const val MS_ENTRE_TRAZAS = 1_000L
    }
}
