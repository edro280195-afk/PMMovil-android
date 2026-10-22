package com.example.pmmovil.mapeador.core

class FusionConfig(
    /** Una fijación nueva más lejos que esto de la estimación actual se considera dudosa. */
    val acceptDistanceM: Double = 0.6,
    val acceptAngleDeg: Double = 10.0,
    /** Con tantos inliers basta una sola fijación para fijar la ubicación. */
    val lockInliers: Int = 60,
    /** Dos fijaciones que coinciden dentro de este margen se confirman entre sí. */
    val agreeDistanceM: Double = 0.3,
    val agreeAngleDeg: Double = 5.0,
    /** Peso de la fijación nueva al suavizar: base + ganancia · min(1, inliers / 60). */
    val alphaBase: Double = 0.15,
    val alphaGain: Double = 0.35
)

class FusionUpdate(val accepted: Boolean, val relocked: Boolean, val deltaM: Double, val deltaDeg: Double)

/**
 * Mantiene la transformación mapa←sesión: dónde está el marco de ARCore de ESTA sesión dentro del
 * mapa. ARCore sigue el movimiento; cada fijación visual la corrige. Rechaza fijaciones aisladas
 * que contradicen a las demás y solo cambia de opinión cuando varias coinciden entre sí.
 */
class PoseFusion(private val cfg: FusionConfig = FusionConfig()) {
    @Volatile
    var mapFromSession: Se3? = null
        private set

    @Volatile
    var lastAcceptedAtMs: Long = 0L
        private set

    private val pending = ArrayList<Se3>()
    private val rejected = ArrayList<Se3>()

    @Synchronized
    fun update(fix: Se3, inliers: Int, nowMs: Long): FusionUpdate {
        val current = mapFromSession
        if (current == null) {
            if (inliers >= cfg.lockInliers) return lock(fix, nowMs, 0.0, 0.0)
            for (p in pending) {
                if (agree(p, fix)) {
                    pending.clear()
                    return lock(Se3.average(listOf(p, fix)), nowMs, 0.0, 0.0)
                }
            }
            pending.add(fix)
            if (pending.size > 4) pending.removeAt(0)
            return FusionUpdate(false, false, 0.0, 0.0)
        }

        val dM = current.translationDistanceTo(fix)
        val dDeg = current.rotationAngleTo(fix)
        if (dM <= cfg.acceptDistanceM && dDeg <= cfg.acceptAngleDeg) {
            val w = (inliers / 60.0).coerceIn(0.0, 1.0)
            mapFromSession = Se3.blend(current, fix, cfg.alphaBase + cfg.alphaGain * w)
            lastAcceptedAtMs = nowMs
            rejected.clear()
            return FusionUpdate(true, false, dM, dDeg)
        }

        // Discrepa de la estimación actual: solo se reemplaza si las últimas tres se confirman entre sí.
        rejected.add(fix)
        if (rejected.size > 4) rejected.removeAt(0)
        if (rejected.size >= 3) {
            val last = rejected.takeLast(3)
            if (agree(last[0], last[1]) && agree(last[1], last[2]) && agree(last[0], last[2])) {
                rejected.clear()
                return lock(Se3.average(last), nowMs, dM, dDeg)
            }
        }
        return FusionUpdate(false, false, dM, dDeg)
    }

    @Synchronized
    fun reset() {
        mapFromSession = null
        lastAcceptedAtMs = 0L
        pending.clear()
        rejected.clear()
    }

    private fun lock(pose: Se3, nowMs: Long, dM: Double, dDeg: Double): FusionUpdate {
        mapFromSession = pose
        lastAcceptedAtMs = nowMs
        return FusionUpdate(true, true, dM, dDeg)
    }

    private fun agree(a: Se3, b: Se3): Boolean =
        a.translationDistanceTo(b) <= cfg.agreeDistanceM && a.rotationAngleTo(b) <= cfg.agreeAngleDeg
}
