package com.example.pmmovil.mapeador.core

import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Escena sintética para probar la geometría sin cámara: puntos 3D al azar frente a la cámara,
 * cada uno con un descriptor propio. `observe` simula lo que vería una cámara en una pose dada
 * (proyección con ruido en píxeles y descriptores con algunos bits cambiados, como pasa con ORB).
 */
class SyntheticScene(seed: Long = 7L, pointCount: Int = 700) {
    private val rnd = Random(seed)
    val points: List<Vec3> = List(pointCount) {
        Vec3(rnd.nextDouble(-5.0, 5.0), rnd.nextDouble(0.0, 3.0), rnd.nextDouble(-9.0, -3.0))
    }
    private val descriptors: List<ByteArray> = List(pointCount) { ByteArray(LandmarkSet.DESC_BYTES).also { rnd.nextBytes(it) } }
    val k = Intrinsics(500.0, 500.0, 320.0, 240.0, 640, 480)

    private fun gauss(): Double {
        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
        val u2 = rnd.nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * u2)
    }

    /** Pose de cámara estilo ARCore en (x, 1.4, 0) con un giro `yawDeg` alrededor de la vertical. */
    fun poseAt(x: Double, yawDeg: Double = 0.0, y: Double = 1.4, z: Double = 0.0): Se3 =
        Se3.rotationAboutY(yawDeg, Vec3(x, y, z))

    /** Índice del punto real más cercano a `p` y su distancia. */
    fun nearest(p: Vec3): Pair<Int, Double> {
        var best = -1
        var bestD = Double.MAX_VALUE
        for ((i, q) in points.withIndex()) {
            val d = q.distanceTo(p)
            if (d < bestD) {
                bestD = d
                best = i
            }
        }
        return best to bestD
    }

    fun observe(worldFromGl: Se3, noisePx: Double = 0.3, bitFlips: Int = 6): Features {
        val cam = CamPose(worldFromGl, k)
        val xy = ArrayList<Float>()
        val bytes = ArrayList<ByteArray>()
        for (i in points.indices) {
            val p = cam.project(points[i]) ?: continue
            val u = p[0] + gauss() * noisePx
            val v = p[1] + gauss() * noisePx
            if (u < 5.0 || u > k.width - 5.0 || v < 5.0 || v > k.height - 5.0) continue
            xy.add(u.toFloat())
            xy.add(v.toFloat())
            val d = descriptors[i].copyOf()
            repeat(bitFlips) {
                val bit = rnd.nextInt(LandmarkSet.DESC_BYTES * 8)
                d[bit / 8] = (d[bit / 8].toInt() xor (1 shl (bit % 8))).toByte()
            }
            bytes.add(d)
        }
        val n = bytes.size
        val all = ByteArray(n * LandmarkSet.DESC_BYTES)
        for ((i, d) in bytes.withIndex()) System.arraycopy(d, 0, all, i * LandmarkSet.DESC_BYTES, LandmarkSet.DESC_BYTES)
        return Features(n, xy.toFloatArray(), FloatArray(n), all)
    }

    /** Un recorrido recto de `count` cámaras separadas `step` metros, con un pequeño balanceo de giro. */
    fun path(count: Int, startX: Double = -2.5, step: Double = 0.2): List<Se3> =
        List(count) { i -> poseAt(startX + step * i, 6.0 * sin(i * 0.4)) }
}
