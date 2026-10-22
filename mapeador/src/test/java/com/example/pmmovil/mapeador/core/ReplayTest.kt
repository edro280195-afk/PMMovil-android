package com.example.pmmovil.mapeador.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Repite en la PC lo que midió el teléfono: lee `profundidad-*.bin` (y, si se guardó el mapa, sus fotogramas clave),
 * compara configuraciones de la malla, mide qué tanto coincide con los puntos de anclaje (que salen de la imagen) y
 * deja las mallas y las vistas para dibujarlas sobre las fotos reales con `tools/render_mesh.py`.
 *
 * Solo corre con la variable de entorno PMM_REPLAY_DIR apuntando a una carpeta con los archivos sacados del teléfono
 * (`adb pull /sdcard/Android/data/com.example.pmmovil.mapeador/files/ .`); sin ella se omite.
 * Los resultados quedan en `<carpeta>/out/` (mallas, views.json y png/).
 */
class ReplayTest {
    private class Keyframe(val timestampNs: Long, val pose: Se3, val k: Intrinsics, val file: File)

    private class Variant(val name: String, val config: TsdfConfig, val mode: DepthMode)

    @Test
    fun replayRealDepth() {
        val dir = System.getenv("PMM_REPLAY_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_REPLAY_DIR no hay datos reales que repetir.", dir != null && dir.isDirectory)
        dir!!

        val logFile = dir.walkTopDown().filter { it.isFile && it.name.startsWith("profundidad-") && it.name.endsWith(".bin") && it.name.contains(System.getenv("PMM_LOG") ?: "") }
            .maxByOrNull { it.name }
        Assume.assumeTrue("No hay profundidad-*.bin en $dir", logFile != null)
        logFile!!
        val out = File(dir, "out").also { it.mkdirs() }

        val frames = dedupe(DepthLogReader.readAll(logFile))
        println("== ${logFile.name}: ${frames.size} cuadros de profundidad (${logFile.length() / 1_000_000} MB)")
        if (frames.isEmpty()) return
        describeData(frames)

        val keyframes = loadKeyframes(dir)
        println("== fotogramas clave con foto: ${keyframes.size}")

        // ---- comparación de configuraciones ----
        println()
        println("%-34s %8s %7s %9s %9s %8s %9s".format("configuración", "bloques", "MB", "voxeles", "cuadril.", "área m²", "ms/cuadro"))
        val variants = listOf(
            Variant("v4: 5 cm / banda 10 cm, mixta", TsdfConfig(), DepthMode.MIXED),
            Variant("v3: 4 cm / banda 12 cm, mixta", TsdfConfig(voxelM = 0.04, truncM = 0.12, minWeight = 1.5f), DepthMode.MIXED),
            Variant("6 cm / banda 12 cm, mixta", TsdfConfig(voxelM = 0.06, truncM = 0.12), DepthMode.MIXED),
            Variant("5 cm, solo cruda", TsdfConfig(), DepthMode.RAW),
            Variant("5 cm, solo suave", TsdfConfig(), DepthMode.SMOOTH),
            Variant("5 cm, mixta, alcance 3 m", TsdfConfig(maxDepthM = 3.0), DepthMode.MIXED),
            Variant("5 cm, mixta, paso 2 px", TsdfConfig(stride = 2), DepthMode.MIXED)
        )
        val views = JSONArray()
        var main: TsdfMesher? = null
        for ((i, v) in variants.withIndex()) {
            val mesher = TsdfMesher(MeshBuffer(), v.config)
            val snapshots = if (i == 0) listOf(frames.size / 3, 2 * frames.size / 3) else emptyList()
            val t0 = System.nanoTime()
            var n = 0
            for ((fi, f) in frames.withIndex()) {
                val use = remerge(f, v.mode) ?: continue
                mesher.integrate(use, 1f)
                n++
                if (fi in snapshots) {
                    val name = "malla-${fi * 100 / frames.size}pct.bin"
                    MeshIO.write(File(out, name), mesher.mesh, v.config.voxelM.toFloat())
                    addViewNear(views, name, "parcial-${fi * 100 / frames.size}pct", f.timestampNs, keyframes, frames)
                }
            }
            val ms = (System.nanoTime() - t0) / 1e6 / n.coerceAtLeast(1)
            println(
                "%-34s %8d %7.1f %9d %9d %8.1f %9.1f".format(
                    v.name, mesher.blockCount, mesher.voxelBytes / 1e6, mesher.voxelCount, mesher.mesh.liveQuadCount, mesher.areaM2, ms
                )
            )
            if (i == 0) main = mesher
            if (i == 1 || i == 3 || i == 4) {
                val name = "malla-variante-$i.bin"
                MeshIO.write(File(out, name), mesher.mesh, v.config.voxelM.toFloat())
                addViewNear(views, name, "variante-$i", frames[frames.size / 2].timestampNs, keyframes, frames)
            }
        }

        val mesher = main ?: return
        MeshIO.write(File(out, "malla-final.bin"), mesher.mesh, mesher.config.voxelM.toFloat())
        addViewNear(views, "malla-final.bin", "final-fin", frames.last().timestampNs, keyframes, frames)
        val step = (keyframes.size / 10).coerceAtLeast(1)
        for (i in keyframes.indices step step) {
            val kf = keyframes[i]
            views.put(view("malla-final.bin", "final-kf-%03d".format(i), kf.pose, kf.k, kf.file))
        }
        File(out, "views.json").writeText(views.toString(1))
        renderWithPython(out)
        landmarkAgreement(dir, mesher.mesh.compacted())
        println("\nResultados en $out (png/ tiene las imágenes)")
    }

    // ---------------------------------------------------------------- datos

    private fun remerge(f: DepthFrame, mode: DepthMode, rawMin: Int = DepthMerge.RAW_MIN_CONFIDENCE): DepthFrame? {
        if (f.rawMm == null && f.smoothMm == null) return if (mode == DepthMode.MIXED) f else null
        val merged = DepthMerge.merge(mode, f.rawMm, f.rawConfidence, f.smoothMm, rawMin) ?: return null
        return DepthFrame(f.width, f.height, merged.depthMm, merged.confidence, f.intrinsics, f.worldFromCamera, f.timestampNs)
    }

    private companion object {
        /** Desde esta confianza ARCore considera «confiable» su profundidad cruda (para describir los datos). */
        const val ARCORE_CONFIDENT = 90
    }

    /** Lo que hace el teléfono: la misma imagen de profundidad (misma marca de tiempo) no se integra dos veces. */
    private fun dedupe(frames: List<DepthFrame>): List<DepthFrame> {
        val out = ArrayList<DepthFrame>()
        var last = Long.MIN_VALUE
        for (f in frames) {
            if (f.timestampNs == last) continue
            last = f.timestampNs
            out.add(f)
        }
        return out
    }

    /** Parte de los píxeles con dato; el teléfono no integra imágenes con menos de 1 %. */
    private fun validFraction(f: DepthFrame): Double = f.depthMm.count { it.toInt() != 0 }.toDouble() / f.depthMm.size.coerceAtLeast(1)

    private fun describeData(frames: List<DepthFrame>) {
        val seconds = (frames.last().timestampNs - frames.first().timestampNs) / 1e9
        println("duración %.1f s, %.1f cuadros/s".format(seconds, frames.size / seconds.coerceAtLeast(1e-9)))

        var rawValid = 0.0
        var rawSeen = 0.0
        var fill = 0.0
        var empty = 0.0
        val confHist = IntArray(5)
        val ratios = ArrayList<Double>()
        for (f in frames) {
            val n = f.width * f.height
            val raw = f.rawMm
            val conf = f.rawConfidence
            val smooth = f.smoothMm
            var rv = 0
            var rs = 0
            var fl = 0
            var em = 0
            for (i in 0 until n) {
                val r = if (raw != null) raw[i].toInt() and 0xFFFF else 0
                val c = if (conf != null) conf[i].toInt() and 0xFF else 255
                val s = if (smooth != null) smooth[i].toInt() and 0xFFFF else 0
                if (r > 0) {
                    rs++
                    confHist[when { c < 50 -> 0; c < 90 -> 1; c < 150 -> 2; c < 200 -> 3; else -> 4 }]++
                }
                if (r > 0 && c >= ARCORE_CONFIDENT) {
                    rv++
                    if (s > 0 && ratios.size < 400_000 && i % 7 == 0) ratios.add(s.toDouble() / r)
                } else if (s > 0) fl++ else em++
            }
            rawValid += rv.toDouble() / n
            rawSeen += rs.toDouble() / n
            fill += fl.toDouble() / n
            empty += em.toDouble() / n
        }
        val m = frames.size.toDouble()
        println(
            "píxeles: cruda confiable %.0f%% (cruda con dato %.0f%%), relleno con suave %.0f%%, vacío %.0f%%".format(
                100 * rawValid / m, 100 * rawSeen / m, 100 * fill / m, 100 * empty / m
            )
        )
        val total = confHist.sum().coerceAtLeast(1)
        println(
            "confianza de la cruda: <50 %.0f%%, 50-89 %.0f%%, 90-149 %.0f%%, 150-199 %.0f%%, >=200 %.0f%%".format(
                100.0 * confHist[0] / total, 100.0 * confHist[1] / total, 100.0 * confHist[2] / total,
                100.0 * confHist[3] / total, 100.0 * confHist[4] / total
            )
        )
        if (ratios.isNotEmpty()) {
            ratios.sort()
            println(
                "suave / cruda donde ambas son confiables: mediana %.3f, p10 %.3f, p90 %.3f (1.0 = coinciden)".format(
                    ratios[ratios.size / 2], ratios[ratios.size / 10], ratios[ratios.size * 9 / 10]
                )
            )
        }

        // Movimiento entre cuadros consecutivos.
        val speeds = ArrayList<Double>()
        val spins = ArrayList<Double>()
        for (i in 1 until frames.size) {
            val dt = (frames[i].timestampNs - frames[i - 1].timestampNs) / 1e9
            if (dt <= 0.0) continue
            speeds.add(frames[i].worldFromCamera.translationDistanceTo(frames[i - 1].worldFromCamera) / dt)
            spins.add(frames[i].worldFromCamera.rotationAngleTo(frames[i - 1].worldFromCamera) / dt)
        }
        speeds.sort()
        spins.sort()
        if (speeds.isNotEmpty()) {
            println(
                "velocidad: mediana %.2f m/s, p90 %.2f | giro: mediana %.0f °/s, p90 %.0f, p99 %.0f".format(
                    speeds[speeds.size / 2], speeds[speeds.size * 9 / 10],
                    spins[spins.size / 2], spins[spins.size * 9 / 10], spins[(spins.size * 99 / 100).coerceAtMost(spins.size - 1)]
                )
            )
        }
    }

    // ---------------------------------------------------------------- vistas y dibujo

    private fun loadKeyframes(dir: File): List<Keyframe> {
        val jsons = dir.walkTopDown().filter { it.isFile && it.name == "keyframes.json" && it.parentFile.name.contains(System.getenv("PMM_MAP") ?: "") }.sortedBy { it.parentFile.name }.toList()
        val json = jsons.lastOrNull() ?: return emptyList()
        val a = JSONArray(json.readText())
        val list = ArrayList<Keyframe>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val q = o.getJSONArray("q")
            val t = o.getJSONArray("t")
            val kk = o.getJSONObject("k")
            val file = File(json.parentFile, "keyframes/" + o.getString("file"))
            if (!file.exists()) continue
            list.add(
                Keyframe(
                    o.getLong("timestampNs"),
                    Se3.fromQuaternion(q.getDouble(0), q.getDouble(1), q.getDouble(2), q.getDouble(3), Vec3(t.getDouble(0), t.getDouble(1), t.getDouble(2))),
                    Intrinsics(kk.getDouble("fx"), kk.getDouble("fy"), kk.getDouble("cx"), kk.getDouble("cy"), kk.getInt("width"), kk.getInt("height")),
                    file
                )
            )
        }
        return list
    }

    private fun view(mesh: String, name: String, pose: Se3, k: Intrinsics, image: File?): JSONObject {
        val q = pose.toQuaternionXyzw()
        return JSONObject().apply {
            put("name", name)
            put("mesh", mesh)
            put("q", JSONArray(listOf(q[0], q[1], q[2], q[3])))
            put("t", JSONArray(listOf(pose.t.x, pose.t.y, pose.t.z)))
            put(
                "k", JSONObject().apply {
                    put("fx", k.fx); put("fy", k.fy); put("cx", k.cx); put("cy", k.cy); put("width", k.width); put("height", k.height)
                }
            )
            put("image", image?.absolutePath ?: JSONObject.NULL)
        }
    }

    /** Una vista de la malla desde el fotograma clave más cercano en el tiempo (o, sin fotos, desde la pose de la profundidad). */
    private fun addViewNear(views: JSONArray, mesh: String, name: String, stampNs: Long, keyframes: List<Keyframe>, frames: List<DepthFrame>) {
        val kf = keyframes.minByOrNull { abs(it.timestampNs - stampNs) }
        if (kf != null) {
            views.put(view(mesh, name, kf.pose, kf.k, kf.file))
            return
        }
        val f = frames.minByOrNull { abs(it.timestampNs - stampNs) } ?: return
        val k = Intrinsics(f.intrinsics.fx * 8, f.intrinsics.fy * 8, f.intrinsics.cx * 8, f.intrinsics.cy * 8, 1280, 720)
        views.put(view(mesh, name, f.worldFromCamera, k, null))
    }

    private fun renderWithPython(out: File) {
        val script = listOf(File("tools/render_mesh.py"), File("mapeador/tools/render_mesh.py")).firstOrNull { it.exists() }
        if (script == null) {
            println("No encontré tools/render_mesh.py: las vistas quedaron en views.json.")
            return
        }
        try {
            val p = ProcessBuilder("python", script.absolutePath, out.absolutePath).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor(300, TimeUnit.SECONDS)
            println(text.trim())
        } catch (e: Exception) {
            println("No se pudo correr Python para dibujar (${e.message}); corre a mano: python ${script.path} $out")
        }
    }

    private class Agreement(val total: Int, val near: Int, val medianCm: Double, val p90Cm: Double, val rmsCm: Double, val biasCm: Double)

    private fun loadLandmarks(dir: File): LandmarkSet? {
        val mapsRoot = dir.walkTopDown().filter { it.isDirectory && it.name == "maps" }.firstOrNull() ?: return null
        val store = MapStore(mapsRoot)
        val wanted = System.getenv("PMM_MAP") ?: ""
        val meta = store.listMaps().firstOrNull { it.id.contains(wanted) } ?: return null
        return store.load(meta.id).landmarks
    }

    /**
     * Independiente de la profundidad: los puntos de anclaje salen de la imagen (ORB + poses de ARCore). Si la malla y los
     * puntos describen las mismas superficies, la distancia de cada punto a la malla debe ser de unos pocos centímetros.
     */
    private fun agreement(lm: LandmarkSet, mesh: MeshBuffer): Agreement {
        val cell = 0.10
        val grid = HashMap<Long, MutableList<Int>>()
        fun key(x: Int, y: Int, z: Int) = (x.toLong() and 0x1FFFFF shl 42) or (y.toLong() and 0x1FFFFF shl 21) or (z.toLong() and 0x1FFFFF)
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.position(i)
            grid.getOrPut(key(floor(p.x / cell).toInt(), floor(p.y / cell).toInt(), floor(p.z / cell).toInt())) { ArrayList() }.add(i)
        }
        val dist = ArrayList<Double>()
        val signed = ArrayList<Double>()
        for (pi in 0 until lm.pointCount) {
            val p = lm.pointPosition(pi)
            val cx = floor(p.x / cell).toInt()
            val cy = floor(p.y / cell).toInt()
            val cz = floor(p.z / cell).toInt()
            var best = Double.MAX_VALUE
            var bestV = -1
            for (dx in -3..3) for (dy in -3..3) for (dz in -3..3) {
                val list = grid[key(cx + dx, cy + dy, cz + dz)] ?: continue
                for (vi in list) {
                    val d = mesh.position(vi).distanceTo(p)
                    if (d < best) {
                        best = d
                        bestV = vi
                    }
                }
            }
            if (bestV >= 0 && best < 0.30) {
                dist.add(best)
                signed.add((p - mesh.position(bestV)).dot(mesh.normal(bestV)))
            }
        }
        if (dist.isEmpty()) return Agreement(lm.pointCount, 0, 0.0, 0.0, 0.0, 0.0)
        dist.sort()
        signed.sort()
        val rms = sqrt(signed.sumOf { it * it } / signed.size)
        return Agreement(
            lm.pointCount, dist.size, dist[dist.size / 2] * 100, dist[dist.size * 9 / 10] * 100, rms * 100, signed[signed.size / 2] * 100
        )
    }

    private fun landmarkAgreement(dir: File, mesh: MeshBuffer) {
        val lm = loadLandmarks(dir) ?: return
        val a = agreement(lm, mesh)
        println("\n== acuerdo entre los puntos de anclaje (de la imagen) y la malla (de la profundidad)")
        println("puntos de anclaje: ${a.total}; con malla a menos de 30 cm: ${a.near} (%.0f%%)".format(100.0 * a.near / a.total.coerceAtLeast(1)))
        println("distancia a la malla: mediana %.1f cm, p90 %.1f cm; desfase con signo: mediana %.1f cm; RMS %.1f cm".format(a.medianCm, a.p90Cm, a.biasCm, a.rmsCm))
    }

    // ---------------------------------------------------------------- exploración de parámetros

    private class Roughness(val medianCm: Double, val p90Cm: Double)

    /** Qué tan lejos queda cada vértice del plano que mejor ajusta a sus vecinos a 15 cm (0 = plano perfecto). */
    private fun roughness(mesh: MeshBuffer): Roughness {
        val r = 0.15
        val grid = HashMap<Long, MutableList<Int>>()
        fun key(x: Int, y: Int, z: Int) = (x.toLong() and 0x1FFFFF shl 42) or (y.toLong() and 0x1FFFFF shl 21) or (z.toLong() and 0x1FFFFF)
        val n = mesh.vertexCount
        val px = DoubleArray(n)
        val py = DoubleArray(n)
        val pz = DoubleArray(n)
        for (i in 0 until n) {
            val p = mesh.position(i)
            px[i] = p.x; py[i] = p.y; pz[i] = p.z
            grid.getOrPut(key(floor(p.x / r).toInt(), floor(p.y / r).toInt(), floor(p.z / r).toInt())) { ArrayList() }.add(i)
        }
        val res = ArrayList<Double>()
        var i = 0
        while (i < n) {
            val cx = floor(px[i] / r).toInt()
            val cy = floor(py[i] / r).toInt()
            val cz = floor(pz[i] / r).toInt()
            var cnt = 0
            var mx = 0.0; var my = 0.0; var mz = 0.0
            val nb = ArrayList<Int>()
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val list = grid[key(cx + dx, cy + dy, cz + dz)] ?: continue
                for (j in list) {
                    val d2 = (px[j] - px[i]) * (px[j] - px[i]) + (py[j] - py[i]) * (py[j] - py[i]) + (pz[j] - pz[i]) * (pz[j] - pz[i])
                    if (d2 <= r * r) { nb.add(j); mx += px[j]; my += py[j]; mz += pz[j]; cnt++ }
                }
            }
            if (cnt >= 12) {
                mx /= cnt; my /= cnt; mz /= cnt
                var xx = 0.0; var xy = 0.0; var xz = 0.0; var yy = 0.0; var yz = 0.0; var zz = 0.0
                for (j in nb) {
                    val dx = px[j] - mx; val dy = py[j] - my; val dz = pz[j] - mz
                    xx += dx * dx; xy += dx * dy; xz += dx * dz; yy += dy * dy; yz += dy * dz; zz += dz * dz
                }
                val lam = smallestEigenvalue(doubleArrayOf(xx / cnt, xy / cnt, xz / cnt, yy / cnt, yz / cnt, zz / cnt))
                res.add(sqrt(lam.coerceAtLeast(0.0)))
            }
            i += 4
        }
        if (res.isEmpty()) return Roughness(0.0, 0.0)
        res.sort()
        return Roughness(res[res.size / 2] * 100, res[res.size * 9 / 10] * 100)
    }

    /** Menor autovalor de una matriz simétrica 3×3 [xx, xy, xz, yy, yz, zz] (fórmula cerrada). */
    private fun smallestEigenvalue(a: DoubleArray): Double {
        val p1 = a[1] * a[1] + a[2] * a[2] + a[4] * a[4]
        if (p1 < 1e-20) return minOf(a[0], minOf(a[3], a[5]))
        val q = (a[0] + a[3] + a[5]) / 3.0
        val p2 = (a[0] - q) * (a[0] - q) + (a[3] - q) * (a[3] - q) + (a[5] - q) * (a[5] - q) + 2 * p1
        val p = sqrt(p2 / 6.0)
        val b00 = (a[0] - q) / p; val b11 = (a[3] - q) / p; val b22 = (a[5] - q) / p
        val b01 = a[1] / p; val b02 = a[2] / p; val b12 = a[4] / p
        val det = b00 * (b11 * b22 - b12 * b12) - b01 * (b01 * b22 - b12 * b02) + b02 * (b01 * b12 - b11 * b02)
        val rr = (det / 2.0).coerceIn(-1.0, 1.0)
        val phi = Math.acos(rr) / 3.0
        return q + 2 * p * Math.cos(phi + 2 * Math.PI / 3)
    }

    private class FloorMetrics(val cellStdCm: Double, val layers: Double, val vertices: Int)

    /** Qué tan grueso y apilado queda el piso: desviación de la altura por celda de 1 m y vértices por columna de voxel. */
    private fun floorMetrics(mesh: MeshBuffer, voxel: Double): FloorMetrics {
        val y0 = System.getenv("PMM_FLOOR_Y0")?.toDoubleOrNull() ?: -1.35
        val y1 = System.getenv("PMM_FLOOR_Y1")?.toDoubleOrNull() ?: -0.75
        val cells = HashMap<Long, MutableList<Double>>()
        val columns = HashSet<Long>()
        var count = 0
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.position(i)
            val n = mesh.normal(i)
            if (n.y < 0.8 || p.y < y0 || p.y > y1) continue
            count++
            val key = (floor(p.x).toLong() and 0xFFFFFF shl 24) or (floor(p.z).toLong() and 0xFFFFFF)
            cells.getOrPut(key) { ArrayList() }.add(p.y)
            columns.add((floor(p.x / voxel).toLong() and 0xFFFFFF shl 24) or (floor(p.z / voxel).toLong() and 0xFFFFFF))
        }
        val stds = cells.values.filter { it.size >= 40 }.map { v ->
            val m = v.average()
            sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
        }.sorted()
        val layers = if (columns.isEmpty()) 0.0 else count.toDouble() / columns.size
        return FloorMetrics(if (stds.isEmpty()) 0.0 else stds[stds.size / 2] * 100, layers, count)
    }

    @Test
    fun exploreStabilityOnRealDepth() {
        val dir = System.getenv("PMM_REPLAY_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_REPLAY_DIR no hay datos reales que repetir.", dir != null && dir.isDirectory)
        Assume.assumeTrue("Exploración desactivada: define PMM_EXPLORE=1.", System.getenv("PMM_EXPLORE") == "1")
        dir!!
        val logFile = dir.walkTopDown().filter { it.isFile && it.name.startsWith("profundidad-") && it.name.endsWith(".bin") && it.name.contains(System.getenv("PMM_LOG") ?: "") }.maxByOrNull { it.name }
        Assume.assumeTrue("No hay profundidad-*.bin en $dir", logFile != null)
        val frames = dedupe(DepthLogReader.readAll(logFile!!))
        val lm = loadLandmarks(dir)
        val out = File(dir, "out").also { it.mkdirs() }
        val keyframes = loadKeyframes(dir)
        val views = JSONArray()

        println("%-58s %8s %8s %6s %7s %6s %6s %6s %6s".format("variante", "vivos", "quitados", "q/viv", "planta", "cobert", "dist50", "rms", "ms-PC"))
        val variants = listOf(
            "v6: sin normalizar, max 40, min 1.0, sin histéresis" to TsdfConfig(normalizeByRays = false, maxWeight = 40f, minWeight = 1.0f, removeAfterMisses = 1),
            "v6 + histéresis 2" to TsdfConfig(normalizeByRays = false, maxWeight = 40f, minWeight = 1.0f, removeAfterMisses = 2),
            "v6 + histéresis 3" to TsdfConfig(normalizeByRays = false, maxWeight = 40f, minWeight = 1.0f, removeAfterMisses = 3),
            "norm, max 6, min 0.3, hist 2" to TsdfConfig(normalizeByRays = true, maxWeight = 6f, minWeight = 0.3f, removeAfterMisses = 2),
            "norm, max 10, min 0.3, hist 2" to TsdfConfig(normalizeByRays = true, maxWeight = 10f, minWeight = 0.3f, removeAfterMisses = 2),
            "norm, max 20, min 0.3, hist 2" to TsdfConfig(normalizeByRays = true, maxWeight = 20f, minWeight = 0.3f, removeAfterMisses = 2),
            "norm, max 10, min 0.3, hist 1" to TsdfConfig(normalizeByRays = true, maxWeight = 10f, minWeight = 0.3f, removeAfterMisses = 1),
            "norm, max 10, min 0.2, hist 2" to TsdfConfig(normalizeByRays = true, maxWeight = 10f, minWeight = 0.2f, removeAfterMisses = 2)
        )
        for ((name, config) in variants) {
            val mesher = TsdfMesher(MeshBuffer(), config)
            val t0 = System.nanoTime()
            for (f in frames) mesher.integrate(f, 1f)
            val ms = (System.nanoTime() - t0) / 1e6 / frames.size
            val compact = mesher.mesh.compacted()
            val a = if (lm != null) agreement(lm, compact) else null
            val live = mesher.mesh.liveQuadCount
            println(
                "%-58s %8d %8d %6.1f %7.1f %5.0f%% %6.1f %6.1f %6.1f".format(
                    name, live, mesher.removedQuads, mesher.removedQuads.toDouble() / live.coerceAtLeast(1), mesher.groundAreaM2,
                    if (a != null) 100.0 * a.near / a.total else 0.0, a?.medianCm ?: 0.0, a?.rmsCm ?: 0.0, ms
                )
            )
            val file = "estab-" + name.replace(" ", "_").replace(",", "").replace(".", "p") + ".bin"
            MeshIO.write(File(out, file), mesher.mesh, config.voxelM.toFloat())
            val kf = keyframes.getOrNull(keyframes.size * 3 / 4)
            if (kf != null) views.put(view(file, file.removeSuffix(".bin"), kf.pose, kf.k, kf.file))
        }
        File(out, "views-estab.json").writeText(views.toString(1))
    }

    /**
     * Mide la malla contra mediciones que NO vio al armarse: los cuadros se reparten en bloques de 8 que alternan
     * entrenamiento (con ellos se arma la malla) y prueba (la profundidad cruda confiable de esos cuadros es la verdad:
     * `tools/eval_variants.py` lanza rayos y compara la primera superficie de cada malla contra esa medida).
     */
    @Test
    fun exploreAnchoredOnRealDepth() {
        val dir = System.getenv("PMM_REPLAY_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_REPLAY_DIR no hay datos reales que repetir.", dir != null && dir.isDirectory)
        Assume.assumeTrue("Exploración desactivada: define PMM_EXPLORE=1.", System.getenv("PMM_EXPLORE") == "1")
        dir!!
        val logFile = dir.walkTopDown().filter { it.isFile && it.name.startsWith("profundidad-") && it.name.endsWith(".bin") && it.name.contains(System.getenv("PMM_LOG") ?: "") }.maxByOrNull { it.name }
        Assume.assumeTrue("No hay profundidad-*.bin en $dir", logFile != null)
        val frames = DepthLogReader.readAll(logFile!!)
        val out = File(dir, "out").also { it.mkdirs() }
        // Cuadros con la pose de su marca de tiempo (tools/frame_validity.py); sin el archivo, todos. Los atascados de ARCore
        // traen una imagen vieja con la pose de AHORA: como verdad (y para armar la malla) no sirven.
        val validFile = File(out, "valid-frames.txt")
        val valid: Set<Int> = if (validFile.exists()) validFile.readText().split(",").filter { it.isNotBlank() }.map { it.trim().toInt() }.toSet() else frames.indices.toSet()
        println("cuadros con pose buena: ${valid.size} de ${frames.size}")
        File(out, "acc-test-frames.txt").writeText(frames.indices.filter { (it / 8) % 2 == 1 && it in valid }.joinToString(","))
        // Se borran las mallas de corridas anteriores: eval_variants.py evalúa todo lo que empieza con «acc-».
        out.listFiles { f -> f.name.startsWith("acc-") && f.name.endsWith(".bin") }?.forEach { it.delete() }

        println("%-64s %7s %7s %6s".format("variante", "vivos", "planta", "ms-PC"))
        class Acc(val name: String, val config: TsdfConfig, val rawMin: Int, val validOnly: Boolean = true)
        val variants = ArrayList<Acc>()
        val batch = System.getenv("PMM_BATCH") ?: "1"
        if (batch == "8") {
            // Lote 8: la v8.1 instalada (el vértice de una celda se fija con los PRIMEROS dos cuadros que la vieron) contra
            // recalcularlo con lo que el volumen cree ahora. Es la queja de Eduardo del 22-oct: «la malla no se reconstruye
            // correctamente después del primer reconocimiento».
            variants.add(Acc("vertice congelado (v8 instalada)", TsdfConfig(refreshVertices = false), DepthMerge.RAW_MIN_CONFIDENCE))
            variants.add(Acc("vertice recalculado", TsdfConfig(refreshVertices = true), DepthMerge.RAW_MIN_CONFIDENCE))
            // El voxel fino se descartó porque «solo dibuja el ruido»: con el vértice congelado, el ruido del primer
            // reconocimiento se quedaba clavado. Vale volver a medirlo ahora.
            variants.add(Acc("recalculado + voxel 6 banda 12", TsdfConfig(refreshVertices = true, voxelM = 0.06, truncM = 0.12), DepthMerge.RAW_MIN_CONFIDENCE))
            variants.add(Acc("congelado + voxel 6 banda 12", TsdfConfig(refreshVertices = false, voxelM = 0.06, truncM = 0.12), DepthMerge.RAW_MIN_CONFIDENCE))
        } else if (batch == "7") {
            // Lote 7: la v7 tal cual la corría el teléfono contra la v8 que se instala (valores por omisión).
            val v7 = TsdfConfig(
                voxelM = 0.08, truncM = 0.16, rawWeight = 0.35f, rawLowWeight = 1f, anchorSigmaPx = 0f, minWeight = 0.3f,
                freeVoteWeight = 1.0f
            )
            variants.add(Acc("v7 tal cual la corria el telefono", v7, 91, validOnly = false))
            variants.add(Acc("v8 (valores por omision)", TsdfConfig(), DepthMerge.RAW_MIN_CONFIDENCE))
        } else if (batch == "6") {
            // Lote 6 (con cuadros de pose buena): qué tanto anclar. Sin anclaje, σ y piso del relleno lejano.
            fun e(sigma: Float, floor: Float = 0f, fill: Float = 0.35f, minWeight: Float = 0.1f) =
                TsdfConfig(anchorSigmaPx = sigma, anchorFloor = floor, fillWeight = fill, minWeight = minWeight)
            variants.add(Acc("E sin anclaje", e(0f), 1))
            variants.add(Acc("E sigma 6 piso 0", e(6f), 1))
            variants.add(Acc("E sigma 6 piso .15", e(6f, 0.15f), 1))
            variants.add(Acc("E sigma 6 piso .3", e(6f, 0.3f), 1))
            variants.add(Acc("E sigma 10 piso 0", e(10f), 1))
            variants.add(Acc("E sigma 10 piso .15", e(10f, 0.15f), 1))
            variants.add(Acc("E sigma 16 piso 0", e(16f), 1))
            variants.add(Acc("E sigma 6 piso .15 relleno .5", e(6f, 0.15f, 0.5f), 1))
            variants.add(Acc("E sigma 10 piso .15 minPeso .2", e(10f, 0.15f, minWeight = 0.2f), 1))
            variants.add(Acc("E sigma 6 piso .15 minPeso .2", e(6f, 0.15f, minWeight = 0.2f), 1))
        } else if (batch == "5") {
            // Lote 5: la v7 contra la v8 (valores por omisión), con las mismas mediciones de prueba (solo cuadros buenos).
            val v7 = TsdfConfig(
                voxelM = 0.08, truncM = 0.16, rawWeight = 0.35f, rawLowWeight = 1f, anchorSigmaPx = 0f, minWeight = 0.3f,
                freeVoteWeight = 1.0f
            )
            variants.add(Acc("v7 tal cual la corria el telefono (todos los cuadros)", v7, 91, validOnly = false))
            variants.add(Acc("v7 pero solo con cuadros de pose buena", v7, 91))
            variants.add(Acc("v8 sin anclaje (solo cuadros buenos y pesos de cruda)", TsdfConfig(anchorSigmaPx = 0f), DepthMerge.RAW_MIN_CONFIDENCE))
            variants.add(Acc("v8 (valores por omision)", TsdfConfig(), DepthMerge.RAW_MIN_CONFIDENCE))
            variants.add(Acc("v8 en todos los cuadros (con los atascados)", TsdfConfig(), DepthMerge.RAW_MIN_CONFIDENCE, validOnly = false))
        } else if (batch == "4") {
            // Lote 4: limpieza visual (rugosidad, capas) frente a exactitud: umbral de la cruda, cuadros mínimos y voxel más grande.
            fun d(voxel: Double = 0.08, trunc: Double = 0.16, minFrames: Int = 2, minWeight: Float = 0.1f, sigma: Float = 6f, misses: Int = 2) =
                TsdfConfig(voxelM = voxel, truncM = trunc, minFrames = minFrames, minWeight = minWeight, anchorSigmaPx = sigma, removeAfterMisses = misses)
            variants.add(Acc("D base (cruda1, vox 8)", d(), 1))
            variants.add(Acc("D cruda 20", d(), 20))
            variants.add(Acc("D cruda 40", d(), 40))
            variants.add(Acc("D cuadros 3", d(minFrames = 3), 1))
            variants.add(Acc("D cuadros 4", d(minFrames = 4), 1))
            variants.add(Acc("D voxel 10 banda 20", d(0.10, 0.20), 1))
            variants.add(Acc("D voxel 12 banda 24", d(0.12, 0.24), 1))
            variants.add(Acc("D voxel 10 cuadros 3", d(0.10, 0.20, minFrames = 3), 1))
            variants.add(Acc("D voxel 10 cruda 20", d(0.10, 0.20), 20))
            variants.add(Acc("D voxel 10 minPeso .2", d(0.10, 0.20, minWeight = 0.2f), 1))
            variants.add(Acc("D voxel 8 banda 12", d(trunc = 0.12), 1))
            variants.add(Acc("D voxel 10 ancla 4", d(0.10, 0.20, sigma = 4f), 1))
            variants.add(Acc("D voxel 10 banda 30", d(0.10, 0.30), 1))
        } else if (batch == "3") {
            // Lote 3: alrededor de «cruda ×2, votos libres 0.5» (la mejor del lote 2 en las dos grabaciones).
            fun b3(raw: Float = 2f, fill: Float = 0.35f, sigma: Float = 6f, minWeight: Float = 0.15f, vote: Float = 0.5f, floor: Float = 0f, trunc: Double = 0.16) =
                TsdfConfig(rawWeight = raw, fillWeight = fill, anchorSigmaPx = sigma, minWeight = minWeight, freeVoteWeight = vote, anchorFloor = floor, truncM = trunc)
            variants.add(Acc("C base (cruda 2, votos .5)", b3(), 1))
            variants.add(Acc("C cruda 3", b3(raw = 3f), 1))
            variants.add(Acc("C cruda 4", b3(raw = 4f), 1))
            variants.add(Acc("C cruda 6", b3(raw = 6f), 1))
            variants.add(Acc("C relleno .2", b3(fill = 0.2f), 1))
            variants.add(Acc("C relleno .5", b3(fill = 0.5f), 1))
            variants.add(Acc("C ancla 5", b3(sigma = 5f), 1))
            variants.add(Acc("C ancla 8", b3(sigma = 8f), 1))
            variants.add(Acc("C minPeso .25", b3(minWeight = 0.25f), 1))
            variants.add(Acc("C minPeso .4", b3(minWeight = 0.4f), 1))
            variants.add(Acc("C minPeso .08", b3(minWeight = 0.08f), 1))
            variants.add(Acc("C piso .2", b3(floor = 0.2f), 1))
            variants.add(Acc("C banda 20", b3(trunc = 0.20), 1))
            variants.add(Acc("C cruda 3 relleno .2 minPeso .25", b3(raw = 3f, fill = 0.2f, minWeight = 0.25f), 1))
            variants.add(Acc("C cruda 4 relleno .5 ancla 8", b3(raw = 4f, fill = 0.5f, sigma = 8f), 1))
        } else if (batch == "1") {
            // La v7 (cruda y relleno pesaban igual, la cruda solo desde confianza 91).
            variants.add(Acc("v7 cruda91 = relleno", TsdfConfig(rawWeight = 0.35f, rawLowWeight = 1f), 91))
            variants.add(Acc("cruda91 relleno .35", TsdfConfig(rawLowWeight = 1f), 91))
            variants.add(Acc("cruda50 relleno .35", TsdfConfig(), 50))
            variants.add(Acc("cruda20 relleno .35", TsdfConfig(), 20))
            variants.add(Acc("cruda1 relleno .35", TsdfConfig(), 1))
            variants.add(Acc("cruda20 relleno .15", TsdfConfig(fillWeight = 0.15f), 20))
            variants.add(Acc("cruda20 sin relleno", TsdfConfig(fillWeight = 0f), 20))
            variants.add(Acc("cruda1 sin relleno", TsdfConfig(fillWeight = 0f), 1))
            variants.add(Acc("cruda20 ancla4", TsdfConfig(anchorSigmaPx = 4f), 20))
            variants.add(Acc("cruda20 ancla8", TsdfConfig(anchorSigmaPx = 8f), 20))
            variants.add(Acc("cruda20 ancla8 piso.3", TsdfConfig(anchorSigmaPx = 8f, anchorFloor = 0.3f), 20))
            variants.add(Acc("cruda20 ancla6 minPeso.15", TsdfConfig(anchorSigmaPx = 6f, minWeight = 0.15f), 20))
            variants.add(Acc("cruda20 ancla6 minPeso.15 relleno 1-4.5m", TsdfConfig(anchorSigmaPx = 6f, minWeight = 0.15f, fillMinDepthM = 1.0, fillMaxDepthM = 4.5), 20))
            variants.add(Acc("cruda1 ancla6 minPeso.15", TsdfConfig(anchorSigmaPx = 6f, minWeight = 0.15f), 1))
            variants.add(Acc("cruda20 relleno 1-4.5m", TsdfConfig(fillMinDepthM = 1.0, fillMaxDepthM = 4.5), 20))
        } else {
            // Lote 2: alrededor de la mejor del lote 1 (cruda desde 1, ancla 6 px, peso mínimo 0.15).
            fun base(
                voxel: Double = 0.08, trunc: Double = 0.16, sigma: Float = 6f, minWeight: Float = 0.15f, minFrames: Int = 2, maxWeight: Float = 10f,
                freeVote: Float = 1f, misses: Int = 2, rawWeight: Float = 1f, stride: Int = 2, maxDepth: Double = 6.0, minDepth: Double = 0.4
            ) = TsdfConfig(
                voxelM = voxel, truncM = trunc, anchorSigmaPx = sigma, minWeight = minWeight, minFrames = minFrames, maxWeight = maxWeight,
                freeVoteWeight = freeVote, removeAfterMisses = misses, rawWeight = rawWeight, stride = stride, maxDepthM = maxDepth, minDepthM = minDepth
            )
            variants.add(Acc("B base", base(), 1))
            variants.add(Acc("B sin votos libres", base(freeVote = 0f), 1))
            variants.add(Acc("B votos libres 0.5", base(freeVote = 0.5f), 1))
            variants.add(Acc("B histeresis 1", base(misses = 1), 1))
            variants.add(Acc("B histeresis 4", base(misses = 4), 1))
            variants.add(Acc("B maxPeso 20", base(maxWeight = 20f), 1))
            variants.add(Acc("B maxPeso 5", base(maxWeight = 5f), 1))
            variants.add(Acc("B minCuadros 3", base(minFrames = 3), 1))
            variants.add(Acc("B banda 12 cm", base(trunc = 0.12), 1))
            variants.add(Acc("B banda 24 cm", base(trunc = 0.24), 1))
            variants.add(Acc("B voxel 6 cm banda 12", base(voxel = 0.06, trunc = 0.12), 1))
            variants.add(Acc("B voxel 10 cm banda 20", base(voxel = 0.10, trunc = 0.20), 1))
            variants.add(Acc("B cruda x2", base(rawWeight = 2f), 1))
            variants.add(Acc("B paso 1", base(stride = 1), 1))
            variants.add(Acc("B ancla 4", base(sigma = 4f), 1))
            variants.add(Acc("B ancla 10", base(sigma = 10f), 1))
            variants.add(Acc("B alcance 5 m", base(maxDepth = 5.0), 1))
            variants.add(Acc("B minimo 0.6 m", base(minDepth = 0.6), 1))
        }
        for (variant in variants) {
            val name = variant.name
            val config = variant.config
            val mesher = TsdfMesher(MeshBuffer(), config)
            val t0 = System.nanoTime()
            var n = 0
            var lastStamp = Long.MIN_VALUE
            for ((i, f) in frames.withIndex()) {
                if ((i / 8) % 2 == 1) continue
                // «Como lo hacía el teléfono hasta la v7»: todo se integra, también lo atascado y lo repetido.
                if (variant.validOnly) {
                    if (i !in valid) continue
                    if (f.timestampNs == lastStamp) continue
                    lastStamp = f.timestampNs
                }
                val use = remerge(f, DepthMode.MIXED, variant.rawMin) ?: continue
                if (validFraction(use) < 0.01) continue
                mesher.integrate(use, 1f)
                n++
            }
            val ms = (System.nanoTime() - t0) / 1e6 / n.coerceAtLeast(1)
            val compact = mesher.mesh.compacted()
            val rg = roughness(compact)
            val fm = floorMetrics(compact, config.voxelM)
            println(
                "%-64s %7d %7.1f %6.1f | rugosidad %4.1f cm (p90 %4.1f) | piso: grosor %4.1f cm, %4.2f capas | quitados %7d (%4.2f x)".format(
                    name, mesher.mesh.liveQuadCount, mesher.groundAreaM2, ms, rg.medianCm, rg.p90Cm, fm.cellStdCm, fm.layers,
                    mesher.removedQuads, mesher.removedQuads.toDouble() / mesher.mesh.liveQuadCount.coerceAtLeast(1)
                )
            )
            val file = "acc-" + name.replace(" ", "_").replace(",", "").replace(".", "p").replace("=", "") + ".bin"
            MeshIO.write(File(out, file), mesher.mesh, config.voxelM.toFloat())
        }
    }

    @Test
    fun exploreBandOnRealDepth() {
        val dir = System.getenv("PMM_REPLAY_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_REPLAY_DIR no hay datos reales que repetir.", dir != null && dir.isDirectory)
        Assume.assumeTrue("Exploración desactivada: define PMM_EXPLORE=1.", System.getenv("PMM_EXPLORE") == "1")
        dir!!
        val logFile = dir.walkTopDown().filter { it.isFile && it.name.startsWith("profundidad-") && it.name.endsWith(".bin") && it.name.contains(System.getenv("PMM_LOG") ?: "") }.maxByOrNull { it.name }
        Assume.assumeTrue("No hay profundidad-*.bin en $dir", logFile != null)
        val frames = dedupe(DepthLogReader.readAll(logFile!!))
        val lm = loadLandmarks(dir)
        val out = File(dir, "out").also { it.mkdirs() }
        val keyframes = loadKeyframes(dir)
        val views = JSONArray()

        println("%-52s %8s %8s %7s %6s %6s %6s %6s %7s %7s".format("variante", "bloques", "cuadril.", "area", "ms-PC", "cobert", "dist50", "rugos", "pisoStd", "capas"))
        val variants = ArrayList<Pair<String, TsdfConfig>>()
        for (voxel in listOf(0.05, 0.08, 0.10)) {
            for (mult in listOf(2.0, 3.0, 4.0)) {
                for (fillWeight in listOf(0.35f, 0.15f)) {
                    variants.add(
                        "vox ${(voxel * 100).toInt()} cm banda x${mult.toInt()} relleno $fillWeight" to
                            TsdfConfig(voxelM = voxel, truncM = mult * voxel, stride = 2, fillWeight = fillWeight)
                    )
                }
            }
        }
        for ((name, config) in variants) {
            val mesher = TsdfMesher(MeshBuffer(), config)
            val t0 = System.nanoTime()
            for (f in frames) mesher.integrate(f, 1f)
            val ms = (System.nanoTime() - t0) / 1e6 / frames.size
            val compact = mesher.mesh.compacted()
            val a = if (lm != null) agreement(lm, compact) else null
            val rg = roughness(compact)
            val fm = floorMetrics(compact, config.voxelM)
            println(
                "%-52s %8d %8d %7.1f %6.1f %5.0f%% %6.1f %6.1f %7.1f %7.2f".format(
                    name, mesher.blockCount, mesher.mesh.liveQuadCount, mesher.areaM2, ms,
                    if (a != null) 100.0 * a.near / a.total else 0.0, a?.medianCm ?: 0.0, rg.medianCm, fm.cellStdCm, fm.layers
                )
            )
            val file = "banda-" + name.replace(" ", "_").replace(".", "p") + ".bin"
            MeshIO.write(File(out, file), mesher.mesh, config.voxelM.toFloat())
            val kf = keyframes.getOrNull(keyframes.size * 3 / 4)
            if (kf != null) views.put(view(file, file.removeSuffix(".bin"), kf.pose, kf.k, kf.file))
        }
        File(out, "views-banda.json").writeText(views.toString(1))
    }

    @Test
    fun exploreParametersOnRealDepth() {
        val dir = System.getenv("PMM_REPLAY_DIR")?.let { File(it) }
        Assume.assumeTrue("Sin PMM_REPLAY_DIR no hay datos reales que repetir.", dir != null && dir.isDirectory)
        Assume.assumeTrue("Exploración desactivada: define PMM_EXPLORE=1.", System.getenv("PMM_EXPLORE") == "1")
        dir!!
        val logFile = dir.walkTopDown().filter { it.isFile && it.name.startsWith("profundidad-") && it.name.endsWith(".bin") && it.name.contains(System.getenv("PMM_LOG") ?: "") }.maxByOrNull { it.name }
        Assume.assumeTrue("No hay profundidad-*.bin en $dir", logFile != null)
        val frames = dedupe(DepthLogReader.readAll(logFile!!))
        val lm = loadLandmarks(dir)
        val out = File(dir, "out").also { it.mkdirs() }
        val keyframes = loadKeyframes(dir)
        val views = JSONArray()

        println("%-46s %8s %8s %7s %6s %6s %6s %6s %6s %6s".format("variante", "bloques", "cuadril.", "area", "ms-PC", "cobert", "dist50", "dist90", "rugos50", "rugos90"))
        val variants = ArrayList<Pair<String, TsdfConfig>>()
        for (voxel in listOf(0.05, 0.06, 0.08)) {
            for (stride in listOf(1, 2)) {
                for (minFrames in listOf(2, 4)) {
                    for (minWeight in listOf(1.0f, 2.5f)) {
                        variants.add(
                            "vox ${(voxel * 100).toInt()} cm paso $stride frames>=$minFrames peso>=$minWeight" to
                                TsdfConfig(voxelM = voxel, truncM = 2 * voxel, stride = stride, minFrames = minFrames, minWeight = minWeight)
                        )
                    }
                }
            }
        }
        for ((name, config) in variants) {
            val mesher = TsdfMesher(MeshBuffer(), config)
            val t0 = System.nanoTime()
            for (f in frames) mesher.integrate(f, 1f)
            val ms = (System.nanoTime() - t0) / 1e6 / frames.size
            val compact = mesher.mesh.compacted()
            val a = if (lm != null) agreement(lm, compact) else null
            val rg = roughness(compact)
            println(
                "%-46s %8d %8d %7.1f %6.1f %5.0f%% %6.1f %6.1f %6.1f %6.1f".format(
                    name, mesher.blockCount, mesher.mesh.liveQuadCount, mesher.areaM2, ms,
                    if (a != null) 100.0 * a.near / a.total else 0.0, a?.medianCm ?: 0.0, a?.p90Cm ?: 0.0, rg.medianCm, rg.p90Cm
                )
            )
            val file = "explora-" + name.replace(" ", "_").replace(">=", "ge").replace(".", "p") + ".bin"
            MeshIO.write(File(out, file), mesher.mesh, config.voxelM.toFloat())
            val kf = keyframes.getOrNull(keyframes.size * 3 / 4)
            if (kf != null) views.put(view(file, file.removeSuffix(".bin"), kf.pose, kf.k, kf.file))
        }
        File(out, "views-explora.json").writeText(views.toString(1))
    }
}
