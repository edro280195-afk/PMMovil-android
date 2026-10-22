package com.example.pmmovil.mapeador.core

import kotlin.math.min

/**
 * De dónde sale la profundidad: la «cruda» de ARCore (con su confianza: más fiel, pero con huecos en muros
 * lisos), la «suave» (densa pero menos exacta) o la mixta (cruda donde hay dato confiable y suave para rellenar).
 */
enum class DepthMode(val label: String) {
    MIXED("Prof. mixta"),
    RAW("Prof. cruda"),
    SMOOTH("Prof. suave (sin anclar)");

    fun next(): DepthMode = entries[(ordinal + 1) % entries.size]
}

/**
 * La profundidad lista para integrar: milímetros (0 = sin dato) y confianza por píxel (null = todo confiable):
 * 0 = sin dato, 1..250 = cruda de ARCore con esa confianza, [DepthMerge.FILL_CONFIDENCE] = relleno con la suave.
 */
class MergedDepth(val depthMm: ShortArray, val confidence: ByteArray?)

/** Arma la profundidad que se integra a partir de las imágenes crudas de ARCore según el modo. */
object DepthMerge {
    /**
     * Confianza mínima (1..250) de la profundidad cruda para usarla; con 0 ARCore no midió nada. ARCore llama «confiable» a
     * partir de ~90, pero medido en el Pixel la cruda de confianza baja se equivoca poco más (error mediano 2–7 % contra
     * 1.3–4 % con confianza alta, y solo 16–36 % de los píxeles pasa de 10 %), mucho menos que la suave lejos de todo dato
     * crudo; por eso entra toda, con un peso que crece con la confianza (`TsdfConfig.rawLowWeight`).
     */
    const val RAW_MIN_CONFIDENCE = 1

    /** Confianza máxima que se conserva de la cruda (lo que está arriba es la marca del relleno). */
    const val MAX_RAW_CONFIDENCE = 250

    /** Marca exacta de lo rellenado con la profundidad suave: el integrador la distingue de la cruda por este valor. */
    const val FILL_CONFIDENCE = 251

    fun merge(
        mode: DepthMode,
        raw: ShortArray?,
        rawConfidence: ByteArray?,
        smooth: ShortArray?,
        rawMinConfidence: Int = RAW_MIN_CONFIDENCE
    ): MergedDepth? {
        val useRaw = mode != DepthMode.SMOOTH && raw != null
        val useSmooth = mode != DepthMode.RAW && smooth != null
        if (!useRaw && !useSmooth) return null
        val n = (raw ?: smooth)!!.size
        val depth = ShortArray(n)
        val confidence = ByteArray(n)
        val minConf = rawMinConfidence.coerceIn(1, MAX_RAW_CONFIDENCE)
        for (i in 0 until n) {
            if (useRaw) {
                val rawMm = raw!![i].toInt() and 0xFFFF
                val rawConf = if (rawConfidence != null) rawConfidence[i].toInt() and 0xFF else MAX_RAW_CONFIDENCE
                if (rawMm > 0 && rawConf >= minConf) {
                    depth[i] = raw[i]
                    confidence[i] = min(rawConf, MAX_RAW_CONFIDENCE).toByte()
                    continue
                }
            }
            if (useSmooth && (smooth!![i].toInt() and 0xFFFF) > 0) {
                depth[i] = smooth[i]
                confidence[i] = FILL_CONFIDENCE.toByte()
            }
        }
        return MergedDepth(depth, confidence)
    }
}
