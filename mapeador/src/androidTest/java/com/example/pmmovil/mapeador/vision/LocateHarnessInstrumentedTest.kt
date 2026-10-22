package com.example.pmmovil.mapeador.vision

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pmmovil.mapeador.core.FixResult
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.LandmarkSet
import com.example.pmmovil.mapeador.core.MapStore
import com.example.pmmovil.mapeador.core.RelocConfig
import com.example.pmmovil.mapeador.core.Relocalizer
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.Vec3
import org.json.JSONArray
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.CvType
import org.opencv.imgcodecs.Imgcodecs
import java.io.File
import kotlin.math.acos
import kotlin.math.min

private const val TAG = "MAPEADOR"

/**
 * Lo que `ReplayTest` es para la malla, pero para **Ubicar**: repite la ubicación sobre un mapa YA GUARDADO,
 * con la cadena completa de verdad (ORB real, emparejado de OpenCV, PnP con RANSAC) y sin volver a caminar.
 *
 * El material lo trae el propio mapa: las imagenes de `keyframes/` son fotos reales de ese lugar y `keyframes.json` guarda
 * la pose de ARCore de cada una. Así que cada fotograma sirve de **consulta con respuesta conocida**: se le pide
 * al relocalizador que diga dónde está y se compara con su pose guardada. Eso es lo que la app en vivo NO puede
 * saber: si una fijación que aceptó era correcta.
 *
 * Para que no sea trampa, al buscar en el mapa se quitan las entradas de los fotogramas que estaban a menos de
 * `excluir` metros de la consulta (no solo el propio: el siguiente se tomo medio segundo despues y es casi la misma
 * foto). Subiendo esa distancia se mide lo que de verdad importa: cuanto aguanta Ubicar cuando la persona NO esta
 * parada justo donde paso al mapear.
 *
 * Dos modos, porque los `ubicar-*.csv` mostraron que es lo que separa una sesión buena de una mala:
 * - **frío**: sin estimación previa, como el primer intento al abrir un mapa. Nunca ha funcionado (0–11 %).
 * - **enganchado**: con la pose del fotograma anterior como estimación previa, como cuando va siguiendo.
 *
 * Cómo correrla (el mapa tiene que estar en el teléfono, en `files/maps/`):
 * ```
 * adb -s <serie> shell am instrument -w \
 *   -e class com.example.pmmovil.mapeador.vision.LocateHarnessInstrumentedTest \
 *   -e mapa 095901 -e fotogramas 40 \
 *   com.example.pmmovil.mapeador.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 * Argumentos: `mapa` (texto del id, por omisión el más reciente), `fotogramas` (cuántos, por omisión 40),
 * `variantes` (texto que deben contener los nombres), `modo` (`frio`, `enganchado` o ambos) y `excluir`
 * (lista de metros, por omision `0,0.5,1,2`).
 */
@RunWith(AndroidJUnit4::class)
class LocateHarnessInstrumentedTest {

    private class Keyframe(val id: Int, val pose: Se3, val k: Intrinsics, val file: String)

    private class Stats(val name: String) {
        var n = 0
        var accepted = 0
        var wrong = 0
        val pairs = ArrayList<Int>()
        val inliers = ArrayList<Int>()
        val errM = ArrayList<Double>()
        val errDeg = ArrayList<Double>()
        val ms = ArrayList<Int>()
        val reasons = HashMap<String, Int>()

        fun add(r: FixResult, truth: Se3) {
            n++
            pairs.add(r.pairs)
            ms.add((r.matchMs + r.solveMs).toInt())
            if (!r.ok || r.mapFromCamera == null) {
                reasons[r.reason] = (reasons[r.reason] ?: 0) + 1
                return
            }
            accepted++
            inliers.add(r.inliers)
            val d = r.mapFromCamera.t.distanceTo(truth.t)
            errM.add(d)
            errDeg.add(angleBetween(r.mapFromCamera, truth))
            if (d > 0.5) wrong++
        }

        fun line(): String {
            fun med(v: List<Int>) = if (v.isEmpty()) -1 else v.sorted()[v.size / 2]
            fun medD(v: List<Double>) = if (v.isEmpty()) -1.0 else v.sorted()[v.size / 2]
            fun p90(v: List<Double>) = if (v.isEmpty()) -1.0 else v.sorted()[min(v.size - 1, (v.size * 0.9).toInt())]
            val top = reasons.entries.sortedByDescending { it.value }.take(2).joinToString(", ") { "${it.key} x${it.value}" }
            return "%-40s n=%3d aceptadas=%3d (%3.0f%%) MALAS=%2d | pares=%4d inliers=%3d | err %5.2f m (p90 %5.2f) %4.1f deg | %4d ms | %s"
                .format(
                    name, n, accepted, 100.0 * accepted / n.coerceAtLeast(1), wrong,
                    med(pairs), med(inliers), medD(errM), p90(errM), medD(errDeg), med(ms), top
                )
        }
    }

    @Before
    fun loadOpenCv() {
        org.junit.Assert.assertTrue("OpenCV no cargó", OpenCv.ensureLoaded())
    }

    @Test
    fun sweepLocateThresholdsOnASavedMap() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val args = InstrumentationRegistry.getArguments()
        val mapsRoot = File(ctx.getExternalFilesDir(null), "maps")
        Assume.assumeTrue("No hay $mapsRoot en el teléfono", mapsRoot.isDirectory)
        val want = args.getString("mapa") ?: ""
        val mapDir = mapsRoot.listFiles { f -> f.isDirectory && f.name.startsWith("map-") && f.name.contains(want) }
            ?.maxByOrNull { it.name }
        Assume.assumeTrue("No hay map-* con «$want» en $mapsRoot", mapDir != null)

        val saved = MapStore(mapsRoot).load(mapDir!!.name)
        val full = saved.landmarks
        val kfs = readKeyframes(File(mapDir, "keyframes.json")).filter { File(mapDir, "keyframes/${it.file}").exists() }
        Assume.assumeTrue("El mapa no trae fotogramas con imagen", kfs.size >= 4)

        val howMany = (args.getString("fotogramas") ?: "40").toInt()
        // Repartidos por todo el recorrido, no los primeros (el recorrido empieza y acaba en el mismo rincón).
        val step = (kfs.size / howMany).coerceAtLeast(1)
        val chosen = kfs.filterIndexed { i, _ -> i % step == 0 }.take(howMany)

        val base = RelocConfig()
        val variants = buildList {
            add("1 como esta hoy (razon .80, inliers 20)" to base)
            add("2 razon .85" to RelocConfig(ratio = 0.85))
            add("3 razon .90" to RelocConfig(ratio = 0.90))
            add("4 razon .95" to RelocConfig(ratio = 0.95))
            add("5 razon .90 inliers 25" to RelocConfig(ratio = 0.90, minInliers = 25))
            add("6 razon .90 inliers 30" to RelocConfig(ratio = 0.90, minInliers = 30))
            add("7 razon .90 inliers 40" to RelocConfig(ratio = 0.90, minInliers = 40))
            add("8 inliers 15" to RelocConfig(minInliers = 15))
            add("9 inliers 30" to RelocConfig(minInliers = 30))
            add("10 razon .90 sin recorte" to RelocConfig(ratio = 0.90, cullMinSubset = Int.MAX_VALUE))
            add("11 sin recorte" to RelocConfig(cullMinSubset = Int.MAX_VALUE))
            add("12 razon .90 recorte 45 grados" to RelocConfig(ratio = 0.90, cullMaxAngleDeg = 45.0))
        }.filter { (name, _) -> (args.getString("variantes") ?: "").let { it.isEmpty() || name.contains(it) } }

        val modo = args.getString("modo") ?: ""
        val doCold = modo.isEmpty() || modo.contains("frio")
        val doWarm = modo.isEmpty() || modo.contains("engan")

        Log.i(TAG, "UBICAR mapa=${mapDir.name} puntos=${full.pointCount} vistas=${full.entryCount} fotogramas=${chosen.size} de ${kfs.size}")
        println("UBICAR mapa=${mapDir.name}: ${full.pointCount} puntos, ${full.entryCount} vistas; ${chosen.size} fotogramas de ${kfs.size}")

        // Los rasgos de cada imagen no dependen de la variante ni de la exclusión: se extraen una sola vez.
        val features = HashMap<Int, com.example.pmmovil.mapeador.core.Features>()
        OrbFeatureExtractor().use { orb ->
            for (kf in chosen) {
                val gray = readGray(File(mapDir, "keyframes/${kf.file}")) ?: continue
                features[kf.id] = orb.extract(gray)
            }
        }
        val usable = chosen.filter { features.containsKey(it.id) }
        println("rasgos por imagen: mediana ${usable.map { features.getValue(it.id).count }.sorted()[usable.size / 2]}")

        // A qué distancia se le quita al mapa la vista más parecida. 0 = solo el propio fotograma (demasiado fácil:
        // el vecino es casi la misma foto). Es lo que de verdad decide si el problema es el umbral o la novedad.
        val exclusions = (args.getString("excluir") ?: "0,0.5,1,2").split(",").map { it.trim().toDouble() }

        val kit = VisionKit()
        try {
            for (ex in exclusions) {
                val mapsWithout = HashMap<Int, LandmarkSet>()
                var dropped = 0
                for (kf in usable) {
                    val drop = HashSet<Int>()
                    drop.add(kf.id)
                    if (ex > 0.0) for (other in kfs) if (other.pose.t.distanceTo(kf.pose.t) < ex) drop.add(other.id)
                    dropped += drop.size
                    mapsWithout[kf.id] = without(full, drop)
                }
                println("--- se le quita al mapa todo lo visto a menos de $ex m (en promedio ${dropped / usable.size} fotogramas de ${kfs.size}) ---")
                Log.i(TAG, "UBICAR --- exclusion $ex m, ${dropped / usable.size} fotogramas quitados de ${kfs.size} ---")
                for ((name, cfg) in variants) {
                    val cold = Stats("$name  [frio]")
                    val warm = Stats("$name  [enganchado]")
                    for ((i, kf) in usable.withIndex()) {
                        val f = features.getValue(kf.id)
                        val reloc = Relocalizer(mapsWithout.getValue(kf.id), kit.matcher, kit.solver, cfg)
                        if (doCold) cold.add(reloc.locate(f, kf.k, null), kf.pose)
                        if (doWarm) {
                            // Estimación previa realista: donde estaba un momento antes (el fotograma anterior).
                            val prior = if (i > 0) usable[i - 1].pose else null
                            if (prior != null) warm.add(reloc.locate(f, kf.k, prior), kf.pose)
                        }
                    }
                    if (doCold) { println(cold.line()); Log.i(TAG, "UBICAR ${cold.line()}") }
                    if (doWarm) { println(warm.line()); Log.i(TAG, "UBICAR ${warm.line()}") }
                }
                mapsWithout.clear()
            }
        } finally {
            kit.close()
        }
    }

    /**
     * El mapa sin las entradas de los fotogramas `drop` (los puntos se quedan). Quitar solo el fotograma de la
     * consulta no basta: el siguiente se tomó medio segundo después y es casi la misma foto, así que reconocerlo
     * sería trivial. Se quitan todos los que estaban a menos de `excluirM` de donde se hace la consulta, y así la
     * vista más parecida que queda en el mapa está de verdad a esa distancia.
     */
    private fun without(lm: LandmarkSet, drop: Set<Int>): LandmarkSet {
        var m = 0
        for (e in 0 until lm.entryCount) if (lm.entryKeyframe[e] !in drop) m++
        val desc = ByteArray(m * LandmarkSet.DESC_BYTES)
        val point = IntArray(m)
        val keyframe = IntArray(m)
        var n = 0
        for (e in 0 until lm.entryCount) {
            if (lm.entryKeyframe[e] in drop) continue
            System.arraycopy(lm.descriptors, e * LandmarkSet.DESC_BYTES, desc, n * LandmarkSet.DESC_BYTES, LandmarkSet.DESC_BYTES)
            point[n] = lm.entryPoint[e]
            keyframe[n] = lm.entryKeyframe[e]
            n++
        }
        return LandmarkSet.fromArrays(
            lm.pointPositions.copyOf(lm.pointCount * 3), lm.pointObservations.copyOf(lm.pointCount),
            desc, point, keyframe, lm.pointCount, m
        )
    }

    private fun readGray(file: File): GrayImage? {
        val mat = Imgcodecs.imread(file.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        try {
            if (mat.empty() || mat.type() != CvType.CV_8UC1) return null
            val data = ByteArray(mat.rows() * mat.cols())
            mat.get(0, 0, data)
            return GrayImage(mat.cols(), mat.rows(), data)
        } finally {
            mat.release()
        }
    }

    private fun readKeyframes(file: File): List<Keyframe> {
        val arr = JSONArray(file.readText())
        val out = ArrayList<Keyframe>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val q = o.getJSONArray("q")
            val t = o.getJSONArray("t")
            val k = o.getJSONObject("k")
            out.add(
                Keyframe(
                    o.getInt("id"),
                    Se3.fromQuaternion(
                        q.getDouble(0), q.getDouble(1), q.getDouble(2), q.getDouble(3),
                        Vec3(t.getDouble(0), t.getDouble(1), t.getDouble(2))
                    ),
                    Intrinsics(
                        k.getDouble("fx"), k.getDouble("fy"), k.getDouble("cx"), k.getDouble("cy"),
                        k.getInt("width"), k.getInt("height")
                    ),
                    o.getString("file")
                )
            )
        }
        return out
    }
}

/** Ángulo (grados) entre las rotaciones de dos poses. */
private fun angleBetween(a: Se3, b: Se3): Double {
    // trazas de Ra^T * Rb
    var tr = 0.0
    for (i in 0 until 3) for (j in 0 until 3) {
        if (i == j) {
            var s = 0.0
            for (k in 0 until 3) s += a.r[k * 3 + i] * b.r[k * 3 + j]
            tr += s
        }
    }
    val c = ((tr - 1.0) / 2.0).coerceIn(-1.0, 1.0)
    return Math.toDegrees(acos(c))
}
