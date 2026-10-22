package com.example.pmmovil.mapeador.core

import kotlin.math.atan2
import kotlin.math.hypot

class RelocConfig(
    val ratio: Double = 0.8,
    val maxHamming: Int = 80,
    /**
     * Si entre los vecinos no aparece ninguno de OTRO lugar (mapa chico o punto muy visto), la prueba de
     * razón no puede descartar nada: entonces se exige un parecido mayor para no aceptar coincidencias flojas.
     */
    val maxHammingUnambiguous: Int = 56,
    /** Vecinos que se piden por rasgo para encontrar el «segundo mejor» de OTRO lugar. */
    val neighbors: Int = 4,
    /** Dos puntos más cerca que esto se toman como el mismo lugar al aplicar la prueba de razón. */
    val distinctM: Double = 0.5,
    val minQueryFeatures: Int = 40,
    val minPairs: Int = 15,
    val minInliers: Int = 20,
    val maxMeanReprojPx: Double = 3.0,
    /** Con una estimación previa solo se comparan las vistas que deberían verse desde ahí. */
    val cullMaxDepthM: Double = 25.0,
    val cullMaxAngleDeg: Double = 65.0,
    val cullMinSubset: Int = 150
)

class FixResult(
    val ok: Boolean,
    /** Pose de la cámara (convención ARCore) en el marco del mapa. */
    val mapFromCamera: Se3?,
    val queryCount: Int,
    val pairs: Int,
    val inliers: Int,
    val meanReprojPx: Double,
    val matchMs: Long,
    val solveMs: Long,
    val usedPrior: Boolean,
    val reason: String
)

/**
 * Ubica una cámara en el mapa: compara los rasgos de la imagen con las vistas guardadas de los
 * puntos 3D y resuelve la pose con PnP + RANSAC.
 *
 * La prueba de razón compara el mejor vecino contra el mejor vecino de OTRO lugar: varias vistas de
 * un mismo punto parecen entre sí por construcción y no deben contar como ambigüedad.
 */
class Relocalizer(
    private val landmarks: LandmarkSet,
    private val matcher: DescriptorMatcher,
    private val solver: PoseSolver,
    private val config: RelocConfig = RelocConfig()
) {
    /**
     * @param prior estimación previa de la pose de la cámara en el mapa (si se tiene), para limitar
     * la búsqueda a lo que debería verse. Si con ella no se logra, se reintenta con todo el mapa.
     */
    fun locate(f: Features, k: Intrinsics, prior: Se3? = null): FixResult {
        if (landmarks.entryCount < config.minPairs) return fail(f.count, "el mapa casi no tiene puntos", prior != null)
        if (f.count < config.minQueryFeatures) return fail(f.count, "pocos rasgos en la imagen (${f.count})", prior != null)
        val first = attempt(f, k, prior)
        if (first.ok || prior == null) return first
        val second = attempt(f, k, null)
        return if (second.ok) second else first
    }

    private fun attempt(f: Features, k: Intrinsics, prior: Se3?): FixResult {
        val t0 = System.nanoTime()
        var subset: IntArray? = null
        if (prior != null) {
            val s = cull(CamPose(prior, k))
            if (s.size >= config.cullMinSubset) subset = s
        }
        val trainCount = subset?.size ?: landmarks.entryCount
        val train: ByteArray = if (subset == null) {
            landmarks.descriptors
        } else {
            val b = ByteArray(trainCount * LandmarkSet.DESC_BYTES)
            for ((n, e) in subset.withIndex()) {
                System.arraycopy(landmarks.descriptors, e * LandmarkSet.DESC_BYTES, b, n * LandmarkSet.DESC_BYTES, LandmarkSet.DESC_BYTES)
            }
            b
        }

        val kn = config.neighbors
        val knn = matcher.knn(f.descriptors, f.count, train, trainCount, kn)
        val objectPoints = FloatArray(f.count * 3)
        val imagePoints = FloatArray(f.count * 2)
        var pairs = 0
        for (qi in 0 until f.count) {
            val base = qi * kn
            val local0 = knn.index[base]
            if (local0 < 0) continue
            val d0 = knn.dist[base]
            if (d0 > config.maxHamming) continue
            val entry0 = if (subset == null) local0 else subset[local0]
            val point0 = landmarks.entryPoint[entry0]

            // Mejor vecino que sea de otro lugar (no otra vista del mismo punto).
            var d2 = Int.MAX_VALUE
            for (j in 1 until kn) {
                val lj = knn.index[base + j]
                if (lj < 0) break
                val ej = if (subset == null) lj else subset[lj]
                val pj = landmarks.entryPoint[ej]
                if (pj == point0 || samePlace(point0, pj)) continue
                d2 = knn.dist[base + j]
                break
            }
            if (d2 == Int.MAX_VALUE) {
                if (d0 > config.maxHammingUnambiguous) continue
            } else if (d0 >= config.ratio * d2) {
                continue
            }

            objectPoints[pairs * 3] = landmarks.pointPositions[point0 * 3]
            objectPoints[pairs * 3 + 1] = landmarks.pointPositions[point0 * 3 + 1]
            objectPoints[pairs * 3 + 2] = landmarks.pointPositions[point0 * 3 + 2]
            imagePoints[pairs * 2] = f.xy[qi * 2]
            imagePoints[pairs * 2 + 1] = f.xy[qi * 2 + 1]
            pairs++
        }
        val t1 = System.nanoTime()
        val matchMs = (t1 - t0) / 1_000_000
        val usedPrior = subset != null
        if (pairs < config.minPairs) {
            return FixResult(false, null, f.count, pairs, 0, 0.0, matchMs, 0, usedPrior, "pocas coincidencias ($pairs)")
        }

        val pnp = solver.solve(objectPoints, imagePoints, pairs, k)
        val solveMs = (System.nanoTime() - t1) / 1_000_000
        if (pnp == null) {
            return FixResult(false, null, f.count, pairs, 0, 0.0, matchMs, solveMs, usedPrior, "PnP sin consenso")
        }
        if (pnp.inliers.size < config.minInliers) {
            return FixResult(false, null, f.count, pairs, pnp.inliers.size, pnp.meanReprojPx, matchMs, solveMs, usedPrior, "pocos inliers (${pnp.inliers.size})")
        }
        if (pnp.meanReprojPx > config.maxMeanReprojPx) {
            return FixResult(false, null, f.count, pairs, pnp.inliers.size, pnp.meanReprojPx, matchMs, solveMs, usedPrior, "error de reproyección alto")
        }
        // PnP entrega mapa→cámara en convención OpenCV; ARCore usa cámara→mapa en convención OpenGL.
        val mapFromCamera = CameraConventions.worldFromGl(pnp.cvFromMap.inverse())
        return FixResult(true, mapFromCamera, f.count, pairs, pnp.inliers.size, pnp.meanReprojPx, matchMs, solveMs, usedPrior, "ok")
    }

    private fun samePlace(a: Int, b: Int): Boolean {
        val p = landmarks.pointPositions
        return hypot(
            hypot((p[a * 3] - p[b * 3]).toDouble(), (p[a * 3 + 1] - p[b * 3 + 1]).toDouble()),
            (p[a * 3 + 2] - p[b * 3 + 2]).toDouble()
        ) < config.distinctM
    }

    /** Índices de las entradas cuyo punto debería verse desde la pose estimada. */
    private fun cull(cam: CamPose): IntArray {
        val m = cam.cvFromWorld
        val out = IntArray(landmarks.entryCount)
        var n = 0
        val p = landmarks.pointPositions
        for (e in 0 until landmarks.entryCount) {
            val pt = landmarks.entryPoint[e]
            val x = p[pt * 3].toDouble()
            val y = p[pt * 3 + 1].toDouble()
            val z = p[pt * 3 + 2].toDouble()
            val cz = m.r[6] * x + m.r[7] * y + m.r[8] * z + m.t.z
            if (cz < 0.3 || cz > config.cullMaxDepthM) continue
            val cx = m.r[0] * x + m.r[1] * y + m.r[2] * z + m.t.x
            val cy = m.r[3] * x + m.r[4] * y + m.r[5] * z + m.t.y
            if (Math.toDegrees(atan2(hypot(cx, cy), cz)) > config.cullMaxAngleDeg) continue
            out[n++] = e
        }
        return out.copyOf(n)
    }

    private fun fail(queryCount: Int, reason: String, usedPrior: Boolean) =
        FixResult(false, null, queryCount, 0, 0, 0.0, 0, 0, usedPrior, reason)
}
