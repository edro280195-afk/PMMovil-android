package com.example.pmmovil.mapeador.core

/**
 * Decide cuándo un cuadro merece ser fotograma clave: que la cámara se haya movido o girado lo
 * suficiente desde el último, que haya pasado un mínimo de tiempo y que no vaya girando tan
 * rápido que la imagen salga movida.
 */
class KeyframePolicy(
    private val minIntervalNs: Long = 250_000_000L,
    private val minTranslationM: Double = 0.20,
    private val minRotationDeg: Double = 10.0,
    private val maxAngularSpeedDegPerS: Double = 80.0
) {
    private var lastKeyPose: Se3? = null
    private var lastKeyTs = 0L
    private var prevPose: Se3? = null
    private var prevTs = 0L

    /** Velocidad angular estimada entre los dos últimos cuadros (°/s). */
    var angularSpeedDegPerS = 0.0
        private set

    /** Llamar con cada cuadro en rastreo. */
    fun shouldCapture(pose: Se3, tsNs: Long): Boolean {
        val prev = prevPose
        if (prev != null && tsNs > prevTs) {
            angularSpeedDegPerS = prev.rotationAngleTo(pose) / ((tsNs - prevTs) / 1e9)
        }
        prevPose = pose
        prevTs = tsNs

        val key = lastKeyPose ?: return true
        if (tsNs - lastKeyTs < minIntervalNs) return false
        if (angularSpeedDegPerS > maxAngularSpeedDegPerS) return false
        return key.translationDistanceTo(pose) >= minTranslationM || key.rotationAngleTo(pose) >= minRotationDeg
    }

    fun markCaptured(pose: Se3, tsNs: Long) {
        lastKeyPose = pose
        lastKeyTs = tsNs
    }

    /** Al perder el rastro no se puede estimar la velocidad entre cuadros. */
    fun onTrackingLost() {
        prevPose = null
        angularSpeedDegPerS = 0.0
    }
}
