package com.example.pmmovil.mapeador.core

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

class TriangulationConfig(
    val minParallaxDeg: Double = 1.5,
    val minDepthM: Double = 0.25,
    val maxDepthM: Double = 25.0,
    val maxReprojPx: Double = 2.5,
    /** Separación máxima entre los dos rayos en su punto más cercano: base + por metro de profundidad. */
    val gapBaseM: Double = 0.03,
    val gapPerMeterM: Double = 0.012
)

class TriangulationResult(val point: Vec3, val parallaxDeg: Double, val reprojPx: Double)

/**
 * Triangulación con poses CONOCIDAS (las de ARCore): punto medio entre los dos rayos más cercanos,
 * validado por paralaje, profundidad y error de reproyección en las dos imágenes.
 * Es Kotlin puro para poder probarlo en la JVM.
 */
object Triangulator {
    fun midpoint(
        a: CamPose, ua: Double, va: Double,
        b: CamPose, ub: Double, vb: Double,
        cfg: TriangulationConfig = TriangulationConfig()
    ): TriangulationResult? {
        val dA = a.ray(ua, va)
        val dB = b.ray(ub, vb)
        val w0 = a.center - b.center
        val bb = dA.dot(dB)
        val d = dA.dot(w0)
        val e = dB.dot(w0)
        val denom = 1.0 - bb * bb
        if (denom < 1e-9) return null
        val s = (bb * e - d) / denom
        val t = (e - bb * d) / denom
        if (s <= 0.0 || t <= 0.0) return null

        val pA = a.center + dA * s
        val pB = b.center + dB * t
        if (pA.distanceTo(pB) > cfg.gapBaseM + cfg.gapPerMeterM * s) return null
        val p = (pA + pB) * 0.5

        val parallax = Math.toDegrees(acos(bb.coerceIn(-1.0, 1.0)))
        if (parallax < cfg.minParallaxDeg) return null

        val pa = a.project(p) ?: return null
        val pb = b.project(p) ?: return null
        if (pa[2] < cfg.minDepthM || pa[2] > cfg.maxDepthM) return null
        if (pb[2] < cfg.minDepthM || pb[2] > cfg.maxDepthM) return null
        val reproj = max(hypot(pa[0] - ua, pa[1] - va), hypot(pb[0] - ub, pb[1] - vb))
        if (reproj > cfg.maxReprojPx) return null
        return TriangulationResult(p, parallax, reproj)
    }
}

object Epipolar {
    /**
     * Distancia, en píxeles de B, del punto (ub, vb) a la recta epipolar que genera el punto (ua, va)
     * de A. Con las poses de ARCore sirve para descartar emparejamientos falsos antes de triangular.
     */
    fun distancePx(a: CamPose, ua: Double, va: Double, b: CamPose, ub: Double, vb: Double): Double {
        // Pose relativa A→B en convención OpenCV: X_b = R·X_a + t.
        val bFromA = b.cvFromWorld.compose(a.worldFromCv)
        val nA = Vec3((ua - a.k.cx) / a.k.fx, (va - a.k.cy) / a.k.fy, 1.0)
        val nB = Vec3((ub - b.k.cx) / b.k.fx, (vb - b.k.cy) / b.k.fy, 1.0)
        // Recta en la imagen de B: l = E·nA = t × (R·nA).
        val l = bFromA.t.cross(bFromA.rotate(nA))
        val den = sqrt(l.x * l.x + l.y * l.y)
        if (den < 1e-12) return Double.MAX_VALUE
        return abs(nB.dot(l)) / den * ((b.k.fx + b.k.fy) / 2.0)
    }
}
