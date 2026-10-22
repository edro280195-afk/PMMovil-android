package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Un cuarto de 8 × 6 × 2.7 m recorrido como lo haría una persona: sirve para medir el tamaño real del volumen. */
internal object RoomScan {
    class Wall(val point: Vec3, val normal: Vec3)

    val K = Intrinsics(125.0, 125.0, 80.0, 45.0, 160, 90)
    const val W = 8.0
    const val D = 6.0
    const val H = 2.7

    val walls = listOf(
        Wall(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0)),
        Wall(Vec3(0.0, H, 0.0), Vec3(0.0, -1.0, 0.0)),
        Wall(Vec3(0.0, 0.0, 0.0), Vec3(1.0, 0.0, 0.0)),
        Wall(Vec3(W, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0)),
        Wall(Vec3(0.0, 0.0, -D), Vec3(0.0, 0.0, 1.0)),
        Wall(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, -1.0))
    )

    const val REAL_AREA_M2 = 2 * W * D + 2 * W * H + 2 * D * H

    fun render(pose: Se3, noiseM: Double, rnd: Random, maxRangeM: Double = 8.0): DepthFrame {
        val cam = CamPose(pose, K)
        val d = ShortArray(K.width * K.height)
        for (v in 0 until K.height) {
            for (u in 0 until K.width) {
                val ray = cam.ray(u + 0.5, v + 0.5)
                var best = Double.MAX_VALUE
                for (p in walls) {
                    val denom = ray.dot(p.normal)
                    if (abs(denom) < 1e-9) continue
                    val t = (p.point - cam.center).dot(p.normal) / denom
                    if (t <= 0.0) continue
                    val z = (ray * t).dot(cam.forward)
                    if (z > 0.0 && z < best) best = z
                }
                if (best < maxRangeM) {
                    var mm = best * 1000.0
                    if (noiseM > 0.0) {
                        val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
                        // El ruido de ARCore crece con la distancia.
                        mm += sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * rnd.nextDouble()) * noiseM * 1000.0 * (0.4 + best / 3.0)
                    }
                    d[v * K.width + u] = Math.round(mm).toInt().toShort()
                }
            }
        }
        return DepthFrame(K.width, K.height, d, null, K, pose, 0L)
    }

    /**
     * Recorrido: un rectángulo a 1.3 m de las paredes, a 0.7 m/s, mirando hacia donde camina con barridos de ±50° de
     * giro y ±20° de cabeceo, 8 cuadros por segundo. Devuelve las poses con un poco de jitter (el de ARCore).
     */
    fun walkLoop(seconds: Double, fps: Double = 8.0, seed: Int = 5): List<Se3> {
        val rnd = Random(seed)
        val cx0 = 1.3
        val cx1 = W - 1.3
        val cz0 = -1.3
        val cz1 = -D + 1.3
        val perimeter = 2 * ((cx1 - cx0) + (cz0 - cz1))
        val poses = ArrayList<Se3>()
        val n = (seconds * fps).toInt()
        for (i in 0 until n) {
            val t = i / fps
            val s = (t * 0.7) % perimeter
            // Posición sobre el rectángulo y rumbo de marcha.
            val a = cx1 - cx0
            val b = cz0 - cz1
            val x: Double
            val z: Double
            val heading: Double // grados, 0 = hacia -z
            when {
                s < a -> { x = cx0 + s; z = cz0; heading = -90.0 }
                s < a + b -> { x = cx1; z = cz0 - (s - a); heading = 180.0 }
                s < 2 * a + b -> { x = cx1 - (s - a - b); z = cz1; heading = 90.0 }
                else -> { x = cx0; z = cz1 + (s - 2 * a - b); heading = 0.0 }
            }
            val yaw = heading + 50.0 * sin(t * 0.9) + 25.0 * sin(t * 2.3 + 1.0)
            val pitch = 20.0 * sin(t * 1.3 + 0.5) - 5.0
            val r = Se3.rotationAboutY(yaw).compose(Se3.rotationAboutX(pitch))
            val jitter = Vec3(rnd.nextDouble(-0.01, 0.01), rnd.nextDouble(-0.01, 0.01), rnd.nextDouble(-0.01, 0.01))
            poses.add(Se3(r.r, Vec3(x, 1.4 + 0.03 * sin(t * 2.0), z) + jitter))
        }
        return poses
    }
}

class RoomScanTest {
    /**
     * El defecto que se vio en el teléfono: el almacén de voxeles se llenaba en ~30 s en una oficina y la malla dejaba
     * de crecer. Un cuarto de 8 × 6 m recorrido un minuto debe caber con mucho margen y quedar cerca de su área real.
     */
    @Test
    fun aWalkedRoomFitsInMemoryAndMatchesItsRealArea() {
        val rnd = Random(11)
        val poses = RoomScan.walkLoop(seconds = 60.0)
        val mesher = TsdfMesher(MeshBuffer(), TsdfConfig())
        var worstMs = 0.0
        for ((i, pose) in poses.withIndex()) {
            val f0 = System.nanoTime()
            mesher.integrate(RoomScan.render(pose, 0.02, rnd), 1f)
            val ms = (System.nanoTime() - f0) / 1e6
            if (ms > worstMs) worstMs = ms
            if (i % 120 == 119) println(
                "t=%4.0fs voxeles=%,d bloques=%d (%.1f MB) cuadrilateros=%,d area=%.1f m2 (real %.1f)".format(
                    (i + 1) / 8.0, mesher.voxelCount, mesher.blockCount, mesher.voxelBytes / 1e6, mesher.mesh.quadCount, mesher.areaM2,
                    RoomScan.REAL_AREA_M2
                )
            )
        }
        println("peor cuadro: %.1f ms (incluye generar la profundidad sintética)".format(worstMs))

        assertTrue("voxeles ${mesher.voxelCount}", mesher.voxelCount < 1_000_000)
        assertTrue("memoria de voxeles ${mesher.voxelBytes / 1e6} MB", mesher.voxelBytes < 25_000_000L)
        assertTrue("no debe llenarse", !mesher.isFull)
        assertTrue("vértices ${mesher.mesh.vertexCount}", mesher.mesh.vertexCount < 120_000)
        // Cubre la mayor parte del cuarto (171.6 m²) sin inventar superficies de más.
        assertTrue("área ${mesher.areaM2} m²", mesher.areaM2 in 100.0..200.0)
    }

    @Test
    fun whenTheVolumeFillsUpItStopsGrowingWithoutBreaking() {
        val rnd = Random(3)
        val poses = RoomScan.walkLoop(seconds = 20.0)
        val mesher = TsdfMesher(MeshBuffer(), TsdfConfig(maxBlocks = 60))
        for (pose in poses) mesher.integrate(RoomScan.render(pose, 0.02, rnd), 1f)
        assertTrue("debe reportar que se llenó", mesher.isFull)
        assertEquals(60, mesher.blockCount)
        val mesh = mesher.mesh
        assertTrue("sigue habiendo malla de lo que sí cupo", mesh.quadCount > 0)
        for (q in 0 until mesh.quadCount) {
            for (v in mesh.quad(q)) assertTrue("índice $v de ${mesh.vertexCount}", v in 0 until mesh.vertexCount)
        }
    }

    /** Con PMM_SYNTH_OUT escribe un registro de profundidad sintético (cuarto recorrido) para probar la herramienta de repetición. */
    @Test
    fun writeSyntheticLogWhenAsked() {
        val dir = System.getenv("PMM_SYNTH_OUT")
        Assume.assumeTrue("Sin PMM_SYNTH_OUT.", dir != null)
        val rnd = Random(21)
        val poses = RoomScan.walkLoop(seconds = 40.0)
        DepthLogWriter(File(dir!!, "profundidad-sintetica.bin")).use { w ->
            for ((i, pose) in poses.withIndex()) {
                val clean = RoomScan.render(pose, 0.0, rnd)
                val n = clean.depthMm.size
                val raw = ShortArray(n)
                val conf = ByteArray(n)
                val smooth = ShortArray(n)
                for (j in 0 until n) {
                    val mm = clean.depthMm[j].toInt() and 0xFFFF
                    if (mm == 0) continue
                    val noise = rnd.nextDouble(-1.0, 1.0) * 25.0
                    if (rnd.nextInt(100) < 70) {
                        raw[j] = (mm + noise).toInt().toShort()
                        conf[j] = (150 + rnd.nextInt(100)).toByte()
                    } else {
                        raw[j] = (mm + noise * 3).toInt().toShort()
                        conf[j] = rnd.nextInt(80).toByte()
                    }
                    smooth[j] = (mm * 1.02 + rnd.nextDouble(-1.0, 1.0) * 30.0).toInt().toShort()
                }
                val merged = DepthMerge.merge(DepthMode.MIXED, raw, conf, smooth)!!
                w.write(
                    DepthFrame(
                        clean.width, clean.height, merged.depthMm, merged.confidence, clean.intrinsics, pose,
                        1_000_000_000L + i * 125_000_000L, raw, conf, smooth
                    )
                )
            }
        }
    }
}
