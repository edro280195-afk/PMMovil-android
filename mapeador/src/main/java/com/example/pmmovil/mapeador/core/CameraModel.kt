package com.example.pmmovil.mapeador.core

/** Parámetros del modelo pinhole de la imagen de CPU de ARCore (en píxeles). */
data class Intrinsics(
    val fx: Double,
    val fy: Double,
    val cx: Double,
    val cy: Double,
    val width: Int,
    val height: Int
)

object CameraConventions {
    /**
     * ARCore/OpenGL: X derecha, Y arriba, -Z hacia donde mira la cámara.
     * OpenCV:        X derecha, Y abajo,  +Z hacia donde mira la cámara.
     * Pasar de una a otra invierte Y y Z. Es su propia inversa.
     */
    val FLIP_YZ = Se3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, -1.0), Vec3.ZERO)

    /** Pose de cámara estilo OpenCV a partir de la de ARCore (estilo OpenGL). */
    fun worldFromCv(worldFromGl: Se3): Se3 = worldFromGl.compose(FLIP_YZ)

    /** Pose de cámara estilo ARCore a partir de la de OpenCV. */
    fun worldFromGl(worldFromCv: Se3): Se3 = worldFromCv.compose(FLIP_YZ)
}

/**
 * Pose de una cámara con todo lo que se reutiliza al proyectar y triangular.
 *
 * `worldFromGl` es exactamente `camera.pose` de ARCore, que está orientada al sensor y por eso
 * coincide con la imagen de CPU y con sus intrínsecos (no con la orientación de la pantalla).
 */
class CamPose(val worldFromGl: Se3, val k: Intrinsics) {
    val worldFromCv: Se3 = CameraConventions.worldFromCv(worldFromGl)
    val cvFromWorld: Se3 = worldFromCv.inverse()
    val center: Vec3 = worldFromGl.t

    /** Dirección en la que mira la cámara, en el mundo. */
    val forward: Vec3 = worldFromGl.rotate(Vec3(0.0, 0.0, -1.0))

    /** Proyecta un punto del mundo: [u, v, profundidad] o null si queda detrás de la cámara. */
    fun project(p: Vec3): DoubleArray? {
        val c = cvFromWorld.apply(p)
        if (c.z < 1e-6) return null
        return doubleArrayOf(k.fx * c.x / c.z + k.cx, k.fy * c.y / c.z + k.cy, c.z)
    }

    /** Rayo unitario (en el mundo) que pasa por el píxel (u, v). */
    fun ray(u: Double, v: Double): Vec3 =
        worldFromCv.rotate(Vec3((u - k.cx) / k.fx, (v - k.cy) / k.fy, 1.0)).normalized()
}
