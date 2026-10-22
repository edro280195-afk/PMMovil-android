package com.example.pmmovil.mapeador.core

import kotlin.math.floor

/** Lista de enteros que crece sin cajas (la usa la rejilla espacial). */
private class IntList {
    var data = IntArray(4)
    var size = 0

    fun add(v: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
}

/**
 * Mapa de puntos 3D fijos.
 *
 * - Un **punto** es un lugar físico (posición promediada de todas las veces que se triangula).
 * - Cada punto tiene una o varias **entradas**: el descriptor ORB (32 bytes) con el que se ve desde
 *   distintas direcciones. Mantener varias vistas por punto es lo que permite reconocer el lugar
 *   desde un ángulo parecido a cualquiera de los del recorrido.
 *
 * Todo en arreglos planos para que decenas de miles de entradas no pesen ni se recorran lento.
 */
class LandmarkSet(initialPoints: Int = 2048, initialEntries: Int = 4096) {
    var pointCount: Int = 0
        private set
    var entryCount: Int = 0
        private set

    var pointPositions: FloatArray = FloatArray(initialPoints * 3)
        private set
    var pointObservations: IntArray = IntArray(initialPoints)
        private set
    var descriptors: ByteArray = ByteArray(initialEntries * DESC_BYTES)
        private set
    var entryPoint: IntArray = IntArray(initialEntries)
        private set
    var entryKeyframe: IntArray = IntArray(initialEntries)
        private set

    // Lista enlazada de las entradas de cada punto (guardan índice + 1; 0 = ninguna).
    private var pointFirstEntry = IntArray(initialPoints)
    private var pointEntryCount = IntArray(initialPoints)
    private var entryNext = IntArray(initialEntries)

    // Rejilla de celdas del tamaño del radio de duplicado, por punto.
    private val grid = HashMap<Long, IntList>()

    fun pointPosition(i: Int) = Vec3(
        pointPositions[i * 3].toDouble(), pointPositions[i * 3 + 1].toDouble(), pointPositions[i * 3 + 2].toDouble()
    )

    fun entryPosition(e: Int): Vec3 = pointPosition(entryPoint[e])

    private fun ensurePoints(needed: Int) {
        if (needed <= pointObservations.size) return
        val cap = maxOf(needed, pointObservations.size * 2)
        pointPositions = pointPositions.copyOf(cap * 3)
        pointObservations = pointObservations.copyOf(cap)
        pointFirstEntry = pointFirstEntry.copyOf(cap)
        pointEntryCount = pointEntryCount.copyOf(cap)
    }

    private fun ensureEntries(needed: Int) {
        if (needed <= entryPoint.size) return
        val cap = maxOf(needed, entryPoint.size * 2)
        descriptors = descriptors.copyOf(cap * DESC_BYTES)
        entryPoint = entryPoint.copyOf(cap)
        entryKeyframe = entryKeyframe.copyOf(cap)
        entryNext = entryNext.copyOf(cap)
    }

    fun addPoint(p: Vec3): Int {
        ensurePoints(pointCount + 1)
        val i = pointCount++
        pointPositions[i * 3] = p.x.toFloat()
        pointPositions[i * 3 + 1] = p.y.toFloat()
        pointPositions[i * 3 + 2] = p.z.toFloat()
        pointObservations[i] = 1
        grid.getOrPut(cellKey(p.x, p.y, p.z)) { IntList() }.add(i)
        return i
    }

    fun addEntry(point: Int, desc: ByteArray, descOffset: Int, keyframeId: Int): Int {
        ensureEntries(entryCount + 1)
        val e = entryCount++
        System.arraycopy(desc, descOffset, descriptors, e * DESC_BYTES, DESC_BYTES)
        entryPoint[e] = point
        entryKeyframe[e] = keyframeId
        entryNext[e] = pointFirstEntry[point]
        pointFirstEntry[point] = e + 1
        pointEntryCount[point]++
        return e
    }

    /** Suma una observación de la posición del punto (promedio móvil). */
    fun observe(point: Int, p: Vec3) {
        val n = pointObservations[point]
        pointPositions[point * 3] = ((pointPositions[point * 3] * n + p.x) / (n + 1)).toFloat()
        pointPositions[point * 3 + 1] = ((pointPositions[point * 3 + 1] * n + p.y) / (n + 1)).toFloat()
        pointPositions[point * 3 + 2] = ((pointPositions[point * 3 + 2] * n + p.z) / (n + 1)).toFloat()
        pointObservations[point] = n + 1
    }

    fun entriesOf(point: Int): Int = pointEntryCount[point]

    /** Distancia de Hamming mínima entre `desc` y las entradas ya guardadas del punto. */
    fun minHammingTo(point: Int, desc: ByteArray, descOffset: Int): Int {
        var best = Int.MAX_VALUE
        var e = pointFirstEntry[point]
        while (e != 0) {
            val d = hamming(descriptors, (e - 1) * DESC_BYTES, desc, descOffset)
            if (d < best) best = d
            e = entryNext[e - 1]
        }
        return best
    }

    /** El punto más cercano a `p` dentro de `radiusM` (máximo [CELL_M]), o -1 si no hay. */
    fun findNearPoint(p: Vec3, radiusM: Double): Int {
        val cx = cell(p.x)
        val cy = cell(p.y)
        val cz = cell(p.z)
        var best = -1
        val radius = minOf(radiusM, CELL_M)
        var bestD2 = radius * radius
        for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
            val list = grid[key(cx + dx, cy + dy, cz + dz)] ?: continue
            for (n in 0 until list.size) {
                val i = list.data[n]
                val ex = pointPositions[i * 3] - p.x
                val ey = pointPositions[i * 3 + 1] - p.y
                val ez = pointPositions[i * 3 + 2] - p.z
                val d2 = ex * ex + ey * ey + ez * ez
                if (d2 <= bestD2) {
                    bestD2 = d2
                    best = i
                }
            }
        }
        return best
    }

    /** Copia congelada para usarla en otro hilo mientras el mapa sigue creciendo. */
    fun snapshot(): LandmarkSet = fromArrays(
        pointPositions.copyOf(pointCount * 3),
        pointObservations.copyOf(pointCount),
        descriptors.copyOf(entryCount * DESC_BYTES),
        entryPoint.copyOf(entryCount),
        entryKeyframe.copyOf(entryCount),
        pointCount,
        entryCount
    )

    private fun cell(v: Double): Int = floor(v / CELL_M).toInt()

    private fun cellKey(x: Double, y: Double, z: Double): Long = key(cell(x), cell(y), cell(z))

    private fun key(cx: Int, cy: Int, cz: Int): Long =
        ((cx.toLong() and MASK) shl 42) or ((cy.toLong() and MASK) shl 21) or (cz.toLong() and MASK)

    companion object {
        const val DESC_BYTES = 32

        /** Tamaño de celda de la rejilla: con ±1 celda se cubre cualquier radio de búsqueda hasta este valor. */
        const val CELL_M = 0.25
        private const val MASK = 0x1FFFFFL

        fun hamming(a: ByteArray, ao: Int, b: ByteArray, bo: Int): Int {
            var d = 0
            for (i in 0 until DESC_BYTES) {
                d += Integer.bitCount((a[ao + i].toInt() xor b[bo + i].toInt()) and 0xFF)
            }
            return d
        }

        fun fromArrays(
            positions: FloatArray,
            observations: IntArray,
            descriptors: ByteArray,
            entryPoint: IntArray,
            entryKeyframe: IntArray,
            pointCount: Int,
            entryCount: Int
        ): LandmarkSet {
            val s = LandmarkSet(1, 1)
            s.pointPositions = positions
            s.pointObservations = observations
            s.descriptors = descriptors
            s.entryPoint = entryPoint
            s.entryKeyframe = entryKeyframe
            s.pointCount = pointCount
            s.entryCount = entryCount
            s.pointFirstEntry = IntArray(maxOf(pointCount, 1))
            s.pointEntryCount = IntArray(maxOf(pointCount, 1))
            s.entryNext = IntArray(maxOf(entryCount, 1))
            for (e in 0 until entryCount) {
                val p = entryPoint[e]
                s.entryNext[e] = s.pointFirstEntry[p]
                s.pointFirstEntry[p] = e + 1
                s.pointEntryCount[p]++
            }
            for (i in 0 until pointCount) {
                s.grid.getOrPut(s.cellKey(positions[i * 3].toDouble(), positions[i * 3 + 1].toDouble(), positions[i * 3 + 2].toDouble())) { IntList() }.add(i)
            }
            return s
        }
    }
}
