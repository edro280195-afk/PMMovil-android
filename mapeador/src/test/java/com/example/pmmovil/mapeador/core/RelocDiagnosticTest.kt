package com.example.pmmovil.mapeador.core

import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Por qué Ubicar encuentra la mitad de coincidencias que el 20-oct (ver `docs/mapeador-bodega.md` §8.7).
 *
 * No hace falta la cámara ni OpenCV: un mapa guardado ya trae descriptores reales y de qué fotograma salió
 * cada uno. Se toma un fotograma, se usan SUS descriptores como si fueran los rasgos de la imagen y se
 * emparejan contra el resto del mapa **quitando del mapa las entradas de ese mismo fotograma** (si no, cada
 * descriptor se encontraría a sí mismo con distancia 0). Eso imita a una cámara que vuelve a ver los mismos
 * lugares desde otro sitio, con las mismas reglas de `Relocalizer`.
 *
 * Lo que se cuenta es por qué se cae cada rasgo: pasa el umbral de parecido, lo tumba la prueba de razón, o
 * lo tumba el umbral estricto de «no hay con qué comparar» (`maxHammingUnambiguous`).
 *
 * `PMM_RELOC_DIR=<carpeta con files/maps/...>` y, opcional, `PMM_MAP=<texto del id>` y `PMM_KF=<cuántos
 * fotogramas>` (por omisión 30).
 */
class RelocDiagnosticTest {

    private class Tally(val name: String) {
        var queries = 0
        var pairs = 0
        var tooFar = 0          // ni el mejor vecino se parece lo suficiente (maxHamming)
        var strictCut = 0       // no apareció un vecino de OTRO lugar y no pasó maxHammingUnambiguous
        var ratioCut = 0        // había con qué comparar y no destacó lo suficiente
        var noNeighbor = 0
        var ambiguous = 0       // casos en que NO apareció vecino de otro lugar (pasen o no)

        fun line(): String = "%-34s rasgos=%5d coincidencias=%5d (%5.1f%%) | sin parecido=%5d  corte estricto=%5d  prueba de razon=%5d | sin vecino de otro lugar=%5d (%4.1f%%)".format(
            name, queries, pairs, 100.0 * pairs / queries.coerceAtLeast(1), tooFar, strictCut, ratioCut,
            ambiguous, 100.0 * ambiguous / queries.coerceAtLeast(1)
        )
    }

    private fun run(lm: LandmarkSet, kfEntries: Map<Int, List<Int>>, kfIds: List<Int>, config: RelocConfig, name: String): Tally {
        val t = Tally(name)
        val matcher = BruteForceMatcher()
        for (kf in kfIds) {
            val qEntries = kfEntries.getValue(kf)
            val query = ByteArray(qEntries.size * LandmarkSet.DESC_BYTES)
            for ((n, e) in qEntries.withIndex()) {
                System.arraycopy(lm.descriptors, e * LandmarkSet.DESC_BYTES, query, n * LandmarkSet.DESC_BYTES, LandmarkSet.DESC_BYTES)
            }
            // El mapa sin las entradas de este fotograma.
            val subset = IntArray(lm.entryCount)
            var m = 0
            for (e in 0 until lm.entryCount) if (lm.entryKeyframe[e] != kf) subset[m++] = e
            val train = ByteArray(m * LandmarkSet.DESC_BYTES)
            for (n in 0 until m) {
                System.arraycopy(lm.descriptors, subset[n] * LandmarkSet.DESC_BYTES, train, n * LandmarkSet.DESC_BYTES, LandmarkSet.DESC_BYTES)
            }

            val kn = config.neighbors
            val knn = matcher.knn(query, qEntries.size, train, m, kn)
            for (qi in qEntries.indices) {
                t.queries++
                val base = qi * kn
                val l0 = knn.index[base]
                if (l0 < 0) { t.noNeighbor++; continue }
                val d0 = knn.dist[base]
                if (d0 > config.maxHamming) { t.tooFar++; continue }
                val point0 = lm.entryPoint[subset[l0]]
                var d2 = Int.MAX_VALUE
                for (j in 1 until kn) {
                    val lj = knn.index[base + j]
                    if (lj < 0) break
                    val pj = lm.entryPoint[subset[lj]]
                    if (pj == point0 || samePlace(lm, point0, pj, config.distinctM)) continue
                    d2 = knn.dist[base + j]
                    break
                }
                if (d2 == Int.MAX_VALUE) {
                    t.ambiguous++
                    if (d0 > config.maxHammingUnambiguous) { t.strictCut++; continue }
                } else if (d0 >= config.ratio * d2) {
                    t.ratioCut++; continue
                }
                t.pairs++
            }
        }
        return t
    }

    private fun samePlace(lm: LandmarkSet, a: Int, b: Int, distinctM: Double): Boolean {
        val p = lm.pointPositions
        val dx = (p[a * 3] - p[b * 3]).toDouble()
        val dy = (p[a * 3 + 1] - p[b * 3 + 1]).toDouble()
        val dz = (p[a * 3 + 2] - p[b * 3 + 2]).toDouble()
        return dx * dx + dy * dy + dz * dz < distinctM * distinctM
    }

    @Test
    fun whichRuleThrowsAwayTheMatches() {
        val dir = System.getenv("PMM_RELOC_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_RELOC_DIR no hay mapa real que revisar.", dir != null && dir.isDirectory)
        val mapsRoot = dir!!.walkTopDown().firstOrNull { it.isDirectory && it.name == "maps" }
        Assume.assumeTrue("No hay carpeta maps en $dir", mapsRoot != null)
        val want = System.getenv("PMM_MAP") ?: ""
        val mapDir = mapsRoot!!.listFiles { f -> f.isDirectory && f.name.startsWith("map-") && f.name.contains(want) }
            ?.maxByOrNull { it.name }
        Assume.assumeTrue("No hay map-* que contenga «$want» en $mapsRoot", mapDir != null)

        val saved = MapStore(mapsRoot).load(mapDir!!.name)
        val lm = saved.landmarks
        val byKf = HashMap<Int, MutableList<Int>>()
        for (e in 0 until lm.entryCount) byKf.getOrPut(lm.entryKeyframe[e]) { ArrayList() }.add(e)
        val howMany = (System.getenv("PMM_KF") ?: "30").toInt()
        // Fotogramas con más entradas: los que de verdad aportan rasgos.
        val kfIds = byKf.entries.filter { it.value.size >= 20 }.sortedByDescending { it.value.size }.take(howMany).map { it.key }
        Assume.assumeTrue("El mapa no tiene fotogramas con suficientes entradas", kfIds.isNotEmpty())

        println("mapa ${mapDir.name}: ${lm.pointCount} puntos, ${lm.entryCount} vistas (${"%.2f".format(lm.entryCount.toDouble() / lm.pointCount)} por punto)")
        println("fotogramas usados: ${kfIds.size}, con ${kfIds.sumOf { byKf.getValue(it).size }} rasgos en total")

        val base = RelocConfig()
        val variantes = listOf(
            "1 como esta hoy" to base,
            "2 sin el corte estricto" to RelocConfig(maxHammingUnambiguous = base.maxHamming),
            "3 vecinos 8" to RelocConfig(neighbors = 8),
            "4 vecinos 16" to RelocConfig(neighbors = 16),
            "5 distinctM 0.1" to RelocConfig(distinctM = 0.1),
            "6 distinctM 0" to RelocConfig(distinctM = 0.0),
            "7 vecinos 8 + distinctM 0.1" to RelocConfig(neighbors = 8, distinctM = 0.1),
            "8 corte estricto 70" to RelocConfig(maxHammingUnambiguous = 70),
            "9 razon 0.9" to RelocConfig(ratio = 0.9),
        )
        for ((name, cfg) in variantes) println(run(lm, byKf, kfIds, cfg, name).line())
    }
}
