package com.example.pmmovil.mapeador.ar

import android.os.SystemClock
import com.example.pmmovil.mapeador.core.CameraFrame
import com.example.pmmovil.mapeador.core.DepthFrame
import com.example.pmmovil.mapeador.core.DepthMode
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.Vec3

enum class ArStartResult { STARTED, INSTALLING, UNAVAILABLE }

enum class HitKind(val label: String) {
    FLOOR("piso"),
    WALL("muro"),
    CEILING("techo"),
    DEPTH("profundidad"),
    POINT("punto"),
    RAY("a 1.5 m")
}

/** Punto medido con la mira central: posición en el marco de ARCore y distancia en metros. */
class HitInfo(val position: Vec3, val kind: HitKind, val distance: Double)

/**
 * Datos de un cuadro de ARCore. SOLO es válido dentro de [ArFrameListener.onFrame] (hilo de GL):
 * `acquireImage` y `acquireDepth` no se pueden llamar después.
 */
class ArFrameInfo(
    val timestampNs: Long,
    val tracking: Boolean,
    val trackingReason: String,
    /** `camera.pose`: cámara→mundo, convención OpenGL, orientada al sensor (la de la imagen de CPU). */
    val worldFromCamera: Se3,
    val cameraPosition: Vec3,
    /** Matriz de vista (columna-mayor), orientada a la pantalla. */
    val viewMatrix: FloatArray,
    /** Proyección · vista (columna-mayor), orientada a la pantalla: para dibujar. */
    val viewProjection: FloatArray,
    val viewportWidth: Int,
    val viewportHeight: Int,
    private val imageProvider: () -> CameraFrame?,
    private val depthProvider: (DepthMode) -> DepthFrame?
) {
    /** Copia la imagen de CPU de este cuadro (o null si ARCore aún no la tiene). */
    fun acquireImage(): CameraFrame? = imageProvider()

    /** Copia la imagen de profundidad (null si aún no hay) con el modo pedido. */
    fun acquireDepth(mode: DepthMode = DepthMode.MIXED): DepthFrame? = depthProvider(mode)
}

interface ArFrameListener {
    /** Se llama en el hilo de GL con cada cuadro: no hacer trabajo pesado aquí. */
    fun onFrame(info: ArFrameInfo)
}

/**
 * La profundidad actual de ARCore como textura de GL (RG8: byte bajo en R, alto en G, en milímetros) y cómo pasar de
 * coordenadas de pantalla a las de la textura: `st = origen + (ndc.x + 1) · dx + (ndc.y + 1) · dy`, con ndc en [-1, 1].
 */
class DepthView(
    val textureId: Int,
    val originS: Float,
    val originT: Float,
    val dxS: Float,
    val dxT: Float,
    val dyS: Float,
    val dyT: Float
)

/**
 * Un plano que detectó ARCore (piso o mesa, muro, techo) listo para dibujar: un abanico de triángulos en el mundo. Cada
 * vértice trae x, y, z del mundo y x, z locales del plano (en metros: de ahí sale la cuadrícula).
 */
class PlaneGeometry(val id: Int, val kind: HitKind, val vertices: FloatArray) {
    val vertexCount: Int get() = vertices.size / FLOATS_PER_VERTEX

    companion object {
        const val FLOATS_PER_VERTEX = 5
    }
}

/** Lo que una capa dibujada con GL necesita saber de cada cuadro. */
class OverlayFrame(
    val viewportWidth: Int,
    val viewportHeight: Int,
    /** Proyección · vista (columna-mayor), orientada a la pantalla. */
    val viewProjection: FloatArray,
    /** Posición de la cámara en el marco de la sesión de ARCore. */
    val cameraPosition: Vec3,
    /** Distancia focal vertical en píxeles de la vista (para dimensionar los puntos). */
    val focalPx: Float,
    val nowSeconds: Float,
    /** Profundidad actual para ocultar lo que queda detrás de superficies reales; null si ARCore aún no la da. */
    val depth: DepthView? = null,
    /** Planos que tiene detectados ARCore en este momento. */
    val planes: List<PlaneGeometry> = emptyList()
)

/** Capa que se dibuja con GL encima de la imagen de la cámara (en el hilo de GL). */
interface GlOverlay {
    /** Se llama cuando se (re)crea el contexto de GL: hay que volver a crear todo lo que viva en la GPU. */
    fun onSurfaceCreated()

    fun draw(frame: OverlayFrame)
}

/** Reloj común (segundos desde que arrancó el proceso) para marcar cuándo nació cada punto y animar su brillo. */
object OverlayClock {
    private val base = SystemClock.elapsedRealtime()

    fun seconds(): Float = ((SystemClock.elapsedRealtime() - base) / 1000.0).toFloat()
}

/** Puente para que un controlador pida medidas (hit-test) al visor sin conocerlo. */
class ArHandle {
    @Volatile
    var view: ArView? = null
        set(value) {
            field = value
            value?.planeSink = planeSink
        }

    /** Recibe (como mucho dos veces por segundo) una línea JSON con los planos que tiene ARCore: se guardan junto a la profundidad para repetirlos en la PC. */
    @Volatile
    var planeSink: ((String) -> Unit)? = null
        set(value) {
            field = value
            view?.planeSink = value
        }

    fun hitTest(callback: (HitInfo?) -> Unit) {
        val v = view
        if (v == null) callback(null) else v.requestHitTest(callback)
    }

    /** Apaga y vuelve a prender la profundidad de ARCore (para sacarla de un atasco): ver [ArView.restartDepth]. */
    fun restartDepth() {
        view?.restartDepth()
    }
}
