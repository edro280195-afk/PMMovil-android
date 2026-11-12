package com.example.pmmovil.mapeador.core

import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.sqrt

/** Familias de marcadores que el laboratorio de anclas sabe leer. */
enum class MarkerFamily(val label: String) {
    APRILTAG_36H11("AprilTag 36h11"),
    ARUCO_4X4("ArUco 4x4"),
    QR("QR")
}

/**
 * Lo que se midió de UN marcador en UN cuadro, con la pose de ARCore ya compuesta.
 *
 * Marco del marcador (convención de OpenCV para marcadores cuadrados): origen en el centro, X a la derecha, Y hacia
 * arriba y Z hacia quien lo mira. Sus cuatro esquinas, en orden, son arriba-izquierda, arriba-derecha, abajo-derecha y
 * abajo-izquierda.
 */
class MarkerObservation(
    val timestampNs: Long,
    val family: MarkerFamily,
    /** Número del marcador (ArUco/AprilTag) o el texto del QR. */
    val id: String,
    /** Lado real impreso, en metros: del borde negro exterior (QR: del cuadrado de módulos, sin el margen blanco). */
    val sideM: Double,
    /** Esquinas en la imagen (u, v en píxeles; 8 valores) en el orden de arriba. */
    val cornersPx: DoubleArray,
    /** Marcador → cámara, con la cámara estilo OpenCV (X derecha, Y abajo, Z adelante). */
    val cvFromMarker: Se3,
    /** Marcador → mundo de la sesión de ARCore. */
    val worldFromMarker: Se3,
    /** Posición de la cámara en el mundo de la sesión, en este cuadro. */
    val cameraPosition: Vec3,
    /** Error de reproyección (RMS) de las cuatro esquinas con la pose elegida, en píxeles. */
    val reprojectionPx: Double,
    /** Peor error ÷ mejor error entre las dos soluciones del PnP plano. Cerca de 1 = no se distinguen (pose dudosa). */
    val ambiguity: Double
) {
    val key: String get() = "${family.name}:$id"

    /** Distancia de la cámara al centro del marcador. */
    val distanceM: Double get() = cvFromMarker.t.norm()

    /** Profundidad a lo largo del eje óptico (lo que mediría un láser pegado a la cámara y perpendicular). */
    val depthM: Double get() = cvFromMarker.t.z

    val viewAngleDeg: Double get() = MarkerGeometry.viewAngleDeg(cvFromMarker)
    val sizePx: Double get() = MarkerGeometry.sizePx(cornersPx)
    val worldCenter: Vec3 get() = worldFromMarker.t
}

/** Pose elegida entre las candidatas del PnP, con su error y qué tan clara fue la elección. */
class SquarePoseChoice(val cvFromMarker: Se3, val reprojectionPx: Double, val ambiguity: Double)

/** Geometría de un marcador cuadrado: solo matemática, sin OpenCV (se prueba en la JVM). */
object MarkerGeometry {
    /** Un marcador de visto por detrás o casi de canto no se mide: más allá de este ángulo se descarta. */
    const val MAX_VIEW_ANGLE_DEG = 85.0

    /** Esquinas del marcador en su propio marco, en el orden de OpenCV. */
    fun objectCorners(sideM: Double): List<Vec3> {
        val h = sideM / 2.0
        return listOf(Vec3(-h, h, 0.0), Vec3(h, h, 0.0), Vec3(h, -h, 0.0), Vec3(-h, -h, 0.0))
    }

    /** Proyecta las cuatro esquinas con la pose dada: 8 valores (u, v) o null si alguna queda detrás de la cámara. */
    fun project(cvFromMarker: Se3, sideM: Double, k: Intrinsics): DoubleArray? {
        val out = DoubleArray(8)
        for ((i, p) in objectCorners(sideM).withIndex()) {
            val c = cvFromMarker.apply(p)
            if (c.z < 1e-6) return null
            out[i * 2] = k.fx * c.x / c.z + k.cx
            out[i * 2 + 1] = k.fy * c.y / c.z + k.cy
        }
        return out
    }

    /** Error de reproyección RMS (píxeles) de la pose contra las esquinas vistas. Infinito si la pose no sirve. */
    fun reprojectionRmsPx(cvFromMarker: Se3, sideM: Double, k: Intrinsics, cornersPx: DoubleArray): Double {
        val proj = project(cvFromMarker, sideM, k) ?: return Double.POSITIVE_INFINITY
        var sum = 0.0
        for (i in 0 until 4) {
            val du = proj[i * 2] - cornersPx[i * 2]
            val dv = proj[i * 2 + 1] - cornersPx[i * 2 + 1]
            sum += du * du + dv * dv
        }
        return sqrt(sum / 4.0)
    }

    /** Ángulo entre la normal del marcador (hacia quien lo mira) y la línea marcador→cámara. 0° = de frente. */
    fun viewAngleDeg(cvFromMarker: Se3): Double {
        val normal = cvFromMarker.rotate(Vec3(0.0, 0.0, 1.0))
        val toCamera = cvFromMarker.t * -1.0
        val n = toCamera.norm()
        if (n < 1e-9) return 0.0
        return Math.toDegrees(acos((normal.dot(toCamera) / n).coerceIn(-1.0, 1.0)))
    }

    /** Una pose es creíble si el marcador queda delante de la cámara y de frente a ella (no de espaldas ni de canto). */
    fun isPlausible(cvFromMarker: Se3): Boolean =
        cvFromMarker.t.z > 1e-6 && viewAngleDeg(cvFromMarker) < MAX_VIEW_ANGLE_DEG

    /** Lado medio del cuadrilátero visto, en píxeles. */
    fun sizePx(cornersPx: DoubleArray): Double {
        var sum = 0.0
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            sum += hypot(cornersPx[i * 2] - cornersPx[j * 2], cornersPx[i * 2 + 1] - cornersPx[j * 2 + 1])
        }
        return sum / 4.0
    }

    /** Pose del marcador en el mundo de ARCore: la cámara de ARCore (estilo OpenGL) se pasa al estilo OpenCV. */
    fun worldFromMarker(worldFromGl: Se3, cvFromMarker: Se3): Se3 =
        CameraConventions.worldFromCv(worldFromGl).compose(cvFromMarker)

    /** Las cuatro esquinas del marcador en el mundo, para dibujarlas sobre la cámara. */
    fun worldCorners(worldFromMarker: Se3, sideM: Double): List<Vec3> =
        objectCorners(sideM).map { worldFromMarker.apply(it) }

    /**
     * El PnP de un cuadrado plano devuelve dos poses posibles. Se descartan las imposibles (de espaldas, de canto o
     * detrás de la cámara) y se elige la de menor error de reproyección; `ambiguity` dice cuánto peor es la otra.
     */
    fun choosePose(candidates: List<Se3>, sideM: Double, k: Intrinsics, cornersPx: DoubleArray): SquarePoseChoice? {
        val scored = candidates
            .filter { isPlausible(it) }
            .map { it to reprojectionRmsPx(it, sideM, k, cornersPx) }
            .filter { it.second.isFinite() }
            .sortedBy { it.second }
        if (scored.isEmpty()) return null
        val best = scored[0]
        val ambiguity = if (scored.size > 1 && best.second > 1e-9) scored[1].second / best.second else Double.POSITIVE_INFINITY
        return SquarePoseChoice(best.first, best.second, ambiguity)
    }
}
