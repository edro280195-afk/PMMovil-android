package com.example.pmmovil.mapeador.core

import kotlin.math.sqrt

/**
 * Cuánto «baila» la posición de un marcador que está QUIETO: guarda las últimas posiciones en el mundo y mide su
 * dispersión alrededor del centro. Es la PRECISIÓN (no necesita saber la distancia real). Ojo: si ARCore se desvía
 * durante la prueba, esa deriva también entra aquí; por eso solo cuentan las últimas [maxSamples] muestras.
 */
class PositionScatter(private val maxSamples: Int = 300) {
    private val samples = ArrayDeque<Vec3>()

    val count: Int get() = samples.size

    fun add(p: Vec3) {
        samples.addLast(p)
        if (samples.size > maxSamples) samples.removeFirst()
    }

    fun clear() = samples.clear()

    fun centroid(): Vec3? {
        if (samples.isEmpty()) return null
        var sum = Vec3.ZERO
        for (p in samples) sum += p
        return sum * (1.0 / samples.size)
    }

    /** Desviación cuadrática media respecto al centro, en metros. Null con menos de 2 muestras. */
    fun rmsM(): Double? {
        if (samples.size < 2) return null
        val c = centroid() ?: return null
        var sum = 0.0
        for (p in samples) {
            val d = p - c
            sum += d.dot(d)
        }
        return sqrt(sum / samples.size)
    }
}

/** Historial de un marcador: cuántas veces se vio, su dispersión y su última lectura. */
class MarkerTrack(val key: String) {
    val scatter = PositionScatter()
    var samples = 0
        private set
    var last: MarkerObservation? = null
        private set

    fun add(o: MarkerObservation) {
        samples++
        scatter.add(o.worldCenter)
        last = o
    }
}

/** Todos los marcadores vistos en la sesión, por clave (familia:id). Solo lo escribe el hilo de visión. */
class MarkerBook {
    private val tracks = LinkedHashMap<String, MarkerTrack>()

    fun add(o: MarkerObservation): MarkerTrack = tracks.getOrPut(o.key) { MarkerTrack(o.key) }.also { it.add(o) }

    fun get(key: String): MarkerTrack? = tracks[key]

    fun clear() = tracks.clear()
}
