package com.example.pmmovil.mapeador.core

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Vector 3D en doble precisión. */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    fun dot(o: Vec3): Double = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun norm(): Double = sqrt(dot(this))
    fun normalized(): Vec3 {
        val n = norm()
        return if (n < 1e-12) this else Vec3(x / n, y / n, z / n)
    }
    fun distanceTo(o: Vec3): Double = (this - o).norm()

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)

        /** El mundo de ARCore está alineado con la gravedad: +Y es arriba. */
        val UP = Vec3(0.0, 1.0, 0.0)
    }
}

/**
 * Transformación rígida p' = R·p + t.
 *
 * Convención de nombres: `aFromB` lleva coordenadas de B a A (p_a = aFromB · p_b).
 * La rotación se guarda por filas (9 valores).
 */
class Se3(val r: DoubleArray, val t: Vec3) {
    init {
        require(r.size == 9) { "La rotación necesita 9 valores." }
    }

    fun rotate(p: Vec3) = Vec3(
        r[0] * p.x + r[1] * p.y + r[2] * p.z,
        r[3] * p.x + r[4] * p.y + r[5] * p.z,
        r[6] * p.x + r[7] * p.y + r[8] * p.z
    )

    fun apply(p: Vec3): Vec3 = rotate(p) + t

    /** this ∘ other: primero other, después this. */
    fun compose(o: Se3): Se3 {
        val m = DoubleArray(9)
        for (i in 0..2) {
            for (j in 0..2) {
                m[i * 3 + j] = r[i * 3] * o.r[j] + r[i * 3 + 1] * o.r[3 + j] + r[i * 3 + 2] * o.r[6 + j]
            }
        }
        return Se3(m, rotate(o.t) + t)
    }

    fun inverse(): Se3 {
        val rt = doubleArrayOf(r[0], r[3], r[6], r[1], r[4], r[7], r[2], r[5], r[8])
        val inv = Se3(rt, Vec3.ZERO)
        return Se3(rt, inv.rotate(t) * -1.0)
    }

    /** Ángulo (en grados) de la rotación que lleva this a other. */
    fun rotationAngleTo(o: Se3): Double {
        var trace = 0.0
        for (i in 0..8) trace += r[i] * o.r[i]
        val c = ((trace - 1.0) / 2.0).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(c))
    }

    fun translationDistanceTo(o: Se3): Double = t.distanceTo(o.t)

    /** Matriz 4x4 de OpenGL (columna-mayor) para usarla como matriz de modelo. */
    fun toColumnMajor4(): FloatArray = floatArrayOf(
        r[0].toFloat(), r[3].toFloat(), r[6].toFloat(), 0f,
        r[1].toFloat(), r[4].toFloat(), r[7].toFloat(), 0f,
        r[2].toFloat(), r[5].toFloat(), r[8].toFloat(), 0f,
        t.x.toFloat(), t.y.toFloat(), t.z.toFloat(), 1f
    )

    /** Cuaternión [x, y, z, w] de la rotación. */
    fun toQuaternionXyzw(): DoubleArray {
        val r00 = r[0]
        val r01 = r[1]
        val r02 = r[2]
        val r10 = r[3]
        val r11 = r[4]
        val r12 = r[5]
        val r20 = r[6]
        val r21 = r[7]
        val r22 = r[8]
        val trace = r00 + r11 + r22
        val x: Double
        val y: Double
        val z: Double
        val w: Double
        if (trace > 0.0) {
            val s = sqrt(trace + 1.0) * 2.0
            w = 0.25 * s
            x = (r21 - r12) / s
            y = (r02 - r20) / s
            z = (r10 - r01) / s
        } else if (r00 > r11 && r00 > r22) {
            val s = sqrt(1.0 + r00 - r11 - r22) * 2.0
            w = (r21 - r12) / s
            x = 0.25 * s
            y = (r01 + r10) / s
            z = (r02 + r20) / s
        } else if (r11 > r22) {
            val s = sqrt(1.0 + r11 - r00 - r22) * 2.0
            w = (r02 - r20) / s
            x = (r01 + r10) / s
            y = 0.25 * s
            z = (r12 + r21) / s
        } else {
            val s = sqrt(1.0 + r22 - r00 - r11) * 2.0
            w = (r10 - r01) / s
            x = (r02 + r20) / s
            y = (r12 + r21) / s
            z = 0.25 * s
        }
        val n = sqrt(x * x + y * y + z * z + w * w)
        return doubleArrayOf(x / n, y / n, z / n, w / n)
    }

    companion object {
        val IDENTITY = Se3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0), Vec3.ZERO)

        fun fromQuaternion(qx: Double, qy: Double, qz: Double, qw: Double, t: Vec3): Se3 {
            val n = sqrt(qx * qx + qy * qy + qz * qz + qw * qw)
            val x = qx / n
            val y = qy / n
            val z = qz / n
            val w = qw / n
            val m = doubleArrayOf(
                1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
                2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
                2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)
            )
            return Se3(m, t)
        }

        fun rotationAboutY(deg: Double, t: Vec3 = Vec3.ZERO): Se3 {
            val a = Math.toRadians(deg)
            val c = cos(a)
            val s = sin(a)
            return Se3(doubleArrayOf(c, 0.0, s, 0.0, 1.0, 0.0, -s, 0.0, c), t)
        }

        fun rotationAboutX(deg: Double, t: Vec3 = Vec3.ZERO): Se3 {
            val a = Math.toRadians(deg)
            val c = cos(a)
            val s = sin(a)
            return Se3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, c, -s, 0.0, s, c), t)
        }

        /** Interpolación normalizada de cuaterniones (sirve para diferencias pequeñas). */
        fun blend(a: Se3, b: Se3, alpha: Double): Se3 {
            val qa = a.toQuaternionXyzw()
            val qb = b.toQuaternionXyzw()
            var dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]
            val sign = if (dot < 0.0) -1.0 else 1.0
            dot *= sign
            val q = DoubleArray(4) { (1.0 - alpha) * qa[it] + alpha * sign * qb[it] }
            val t = a.t * (1.0 - alpha) + b.t * alpha
            return fromQuaternion(q[0], q[1], q[2], q[3], t)
        }

        fun average(poses: List<Se3>): Se3 {
            require(poses.isNotEmpty())
            val ref = poses[0].toQuaternionXyzw()
            val acc = DoubleArray(4)
            var t = Vec3.ZERO
            for (p in poses) {
                val q = p.toQuaternionXyzw()
                val sign = if (q[0] * ref[0] + q[1] * ref[1] + q[2] * ref[2] + q[3] * ref[3] < 0.0) -1.0 else 1.0
                for (i in 0..3) acc[i] += sign * q[i]
                t += p.t
            }
            return fromQuaternion(acc[0], acc[1], acc[2], acc[3], t * (1.0 / poses.size))
        }
    }
}
