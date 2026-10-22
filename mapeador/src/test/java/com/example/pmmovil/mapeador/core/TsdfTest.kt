package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.random.Random

/** Un plano para fabricar imágenes de profundidad exactas: punto y normal (apuntando hacia donde está la cámara). */
private class Plane(val point: Vec3, val normal: Vec3)

private val K = Intrinsics(120.0, 120.0, 80.0, 45.0, 160, 90)

/** Profundidad (profundidad de cámara, como ARCore) de lo más cercano que vería la cámara entre los planos dados. */
private fun depthOf(pose: Se3, planes: List<Plane>, noiseM: Double = 0.0, seed: Int = 1): DepthFrame {
    val cam = CamPose(pose, K)
    val rnd = Random(seed)
    val d = ShortArray(K.width * K.height)
    for (v in 0 until K.height) {
        for (u in 0 until K.width) {
            val ray = cam.ray(u + 0.5, v + 0.5)
            var best = Double.MAX_VALUE
            for (p in planes) {
                val denom = ray.dot(p.normal)
                if (abs(denom) < 1e-9) continue
                val t = (p.point - cam.center).dot(p.normal) / denom
                if (t <= 0.0) continue
                val z = (ray * t).dot(cam.forward)
                if (z > 0.0 && z < best) best = z
            }
            if (best < Double.MAX_VALUE) {
                var mm = best * 1000.0
                if (noiseM > 0.0) {
                    val u1 = rnd.nextDouble().coerceAtLeast(1e-12)
                    mm += sqrt(-2.0 * ln(u1)) * cos(2.0 * Math.PI * rnd.nextDouble()) * noiseM * 1000.0
                }
                d[v * K.width + u] = Math.round(mm).toInt().toShort()
            }
        }
    }
    return DepthFrame(K.width, K.height, d, null, K, pose, 0L)
}

/** Cámara a 1.4 m de altura en x, girada `yawDeg` y con `pitchDeg` (negativo = mira hacia abajo). */
private fun poseAt(x: Double, yawDeg: Double = 0.0, pitchDeg: Double = 0.0): Se3 {
    val yaw = Se3.rotationAboutY(yawDeg)
    val pitch = Se3.rotationAboutX(pitchDeg)
    return Se3(yaw.compose(pitch).r, Vec3(x, 1.4, 0.0))
}

private val wall = Plane(Vec3(0.0, 0.0, -3.0), Vec3(0.0, 0.0, 1.0))
private val floor = Plane(Vec3(0.0, 0.0, 0.0), Vec3(0.0, 1.0, 0.0))

private fun scan(planes: List<Plane>, poses: List<Se3>, noiseM: Double = 0.0, config: TsdfConfig = TsdfConfig()): TsdfMesher {
    val mesher = TsdfMesher(MeshBuffer(), config)
    for ((i, p) in poses.withIndex()) mesher.integrate(depthOf(p, planes, noiseM, seed = 100 + i), 1f)
    return mesher
}

/** Configuración fina (5 cm, un rayo por píxel) para las pruebas de exactitud geométrica. */
private val FINE = TsdfConfig(voxelM = 0.05, truncM = 0.10, stride = 1)

/** Un recorrido más ancho y con giros distintos: la persona camina y mira desde muchos lugares. */
private val wideWalk = (0 until 24).map { i -> poseAt(-0.6 + 0.07 * i, yawDeg = ((i % 5) - 2) * 3.0) }

private val sideToSide = listOf(-0.3, -0.1, 0.1, 0.3, 0.5, 0.7, 0.2, 0.0).mapIndexed { i, x -> poseAt(x, yawDeg = (i % 3 - 1) * 2.0) }

class TsdfMesherTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun aFlatWallBecomesAThinCleanSurfaceFacingTheCamera() {
        val m = scan(listOf(wall), sideToSide, config = FINE)
        val mesh = m.mesh
        assertTrue("vértices ${mesh.vertexCount}", mesh.vertexCount > 300)
        assertTrue("cuadriláteros ${mesh.quadCount}", mesh.quadCount > 300)

        var towardsCamera = 0
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.position(i)
            assertEquals("la pared está en z = -3 (vértice $i)", -3.0, p.z, 0.02)
            val n = mesh.normal(i)
            assertTrue("normal $n", n.z > 0.97)
            if (n.dot(Vec3(0.0, 1.4, 0.0) - p) > 0.0) towardsCamera++
        }
        assertTrue("las normales miran a la cámara", towardsCamera >= mesh.vertexCount * 0.98)
        // Lo que ve la cámara de la pared a 3 m es unos 4 × 2.25 m.
        assertTrue("área ${m.areaM2} m²", m.areaM2 in 4.0..12.0)
    }

    @Test
    fun theDefaultConfigurationKeepsASurfaceWithinThreeCentimetersWithDepthImageNormals() {
        val mesh = scan(listOf(wall), sideToSide).mesh
        assertTrue("vértices ${mesh.vertexCount}", mesh.vertexCount > 100)
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.position(i)
            assertEquals("la pared está en z = -3 (vértice $i)", -3.0, p.z, 0.035)
            assertTrue("normal ${mesh.normal(i)}", mesh.normal(i).z > 0.97)
        }
    }

    @Test
    fun floorNormalsStayUpEvenWithNoisyDepthBecauseTheyComeFromTheDepthImages() {
        val poses = listOf(-0.2, 0.0, 0.2, 0.4, 0.1, 0.3, 0.0, -0.1).map { poseAt(it, pitchDeg = -35.0) }
        val mesh = scan(listOf(floor), poses, noiseM = 0.03).mesh
        assertTrue("vértices ${mesh.vertexCount}", mesh.vertexCount > 100)
        var up = 0
        for (i in 0 until mesh.vertexCount) if (mesh.normal(i).y > 0.9) up++
        assertTrue("normales hacia arriba: $up de ${mesh.vertexCount}", up >= mesh.vertexCount * 0.9)
    }

    @Test
    fun groundAreaCountsTheFootprintOnTheFloorNotTheSurface() {
        val wallOnly = scan(listOf(wall), sideToSide)
        assertTrue("una pared sola ocupa muy poco piso: ${wallOnly.groundAreaM2} m²", wallOnly.groundAreaM2 < 3.0)
        assertTrue("pero tiene área de malla: ${wallOnly.areaM2} m²", wallOnly.areaM2 > 3.0)

        val poses = listOf(-0.2, 0.0, 0.2, 0.4, 0.1, 0.3).map { poseAt(it, pitchDeg = -35.0) }
        val floorOnly = scan(listOf(floor), poses)
        assertTrue("un tramo de piso ocupa piso: ${floorOnly.groundAreaM2} m²", floorOnly.groundAreaM2 in 4.0..30.0)
        val ratio = floorOnly.groundAreaM2 / floorOnly.areaM2
        assertTrue("en un piso plano la planta y la malla son casi iguales: $ratio", ratio in 0.7..1.3)
    }

    @Test
    fun noisyDepthAveragesOutInsteadOfLeavingSeveralLayers() {
        val poses = sideToSide + sideToSide.reversed()
        val m = scan(listOf(wall), poses, noiseM = 0.025)
        val mesh = m.mesh
        assertTrue("vértices ${mesh.vertexCount}", mesh.vertexCount > 300)
        val errors = (0 until mesh.vertexCount).map { abs(mesh.position(it).z + 3.0) }.sorted()
        val mean = errors.average()
        val p95 = errors[(errors.size * 0.95).toInt()]
        assertTrue("error medio $mean m", mean < 0.015)
        assertTrue("percentil 95 $p95 m", p95 < 0.045)
    }

    @Test
    fun oneFrameAloneIsNotEnoughToTrustASurface() {
        val mesher = TsdfMesher()
        mesher.integrate(depthOf(poseAt(0.0), listOf(wall)), 1f)
        assertEquals("con un solo cuadro no hay superficie", 0, mesher.mesh.quadCount)
        mesher.integrate(depthOf(poseAt(0.05), listOf(wall)), 1f)
        assertTrue("con dos cuadros ya sale", mesher.mesh.quadCount > 100)
    }

    @Test
    fun theFloorSeenFromAboveLandsOnYZeroWithUpNormals() {
        val poses = listOf(-0.2, 0.0, 0.2, 0.4, 0.1, 0.3).map { poseAt(it, pitchDeg = -35.0) }
        val m = scan(listOf(floor), poses, config = FINE)
        val mesh = m.mesh
        assertTrue("vértices ${mesh.vertexCount}", mesh.vertexCount > 300)
        for (i in 0 until mesh.vertexCount) {
            assertEquals("el piso está en y = 0 (vértice $i)", 0.0, mesh.position(i).y, 0.012)
            assertTrue("normal ${mesh.normal(i)}", mesh.normal(i).y > 0.95)
        }
    }

    @Test
    fun aCornerKeepsBothWallsWithTheirOwnNormals() {
        val rightWall = Plane(Vec3(1.0, 0.0, 0.0), Vec3(-1.0, 0.0, 0.0))
        val m = scan(listOf(wall, rightWall), sideToSide)
        val mesh = m.mesh
        var onBack = 0
        var onRight = 0
        for (i in 0 until mesh.vertexCount) {
            val p = mesh.position(i)
            val n = mesh.normal(i)
            if (abs(p.z + 3.0) < 0.03 && n.z > 0.85) onBack++
            if (abs(p.x - 1.0) < 0.03 && n.x < -0.85) onRight++
        }
        assertTrue("fondo $onBack", onBack > 100)
        assertTrue("pared derecha $onRight", onRight > 50)
        assertTrue("casi todo está en alguno de los dos planos", onBack + onRight >= mesh.vertexCount * 0.9)
    }

    @Test
    fun theMeshIndicesAreAlwaysValid() {
        val mesh = scan(listOf(wall, floor), sideToSide.map { poseAt(it.t.x, pitchDeg = -15.0) }).mesh
        assertTrue(mesh.quadCount > 100)
        for (q in 0 until mesh.quadCount) {
            val idx = mesh.quad(q)
            assertEquals("las 4 esquinas son distintas", 4, idx.toSet().size)
            for (v in idx) assertTrue("índice $v de ${mesh.vertexCount}", v in 0 until mesh.vertexCount)
        }
    }

    @Test
    fun tooFarOrMissingDepthBuildsNothing() {
        val far = Plane(Vec3(0.0, 0.0, -8.0), Vec3(0.0, 0.0, 1.0))
        assertEquals(0, scan(listOf(far), sideToSide).voxelCount)
        assertEquals(0, scan(emptyList(), sideToSide).voxelCount)
    }

    @Test
    fun zeroConfidenceDepthIsIgnored() {
        val f = depthOf(poseAt(0.0), listOf(wall))
        val none = DepthFrame(f.width, f.height, f.depthMm, ByteArray(f.width * f.height), f.intrinsics, f.worldFromCamera, 0L)
        val mesher = TsdfMesher()
        repeat(3) { mesher.integrate(none, 1f) }
        assertEquals(0, mesher.voxelCount)
    }

    /**
     * Imagen como las del Pixel en un muro liso: un parche de profundidad CRUDA (medida de verdad, a 3 m) y todo lo demás
     * «relleno» con un error que crece con la distancia al parche (como mide ARCore: sin error junto a los píxeles crudos,
     * +25 % desde ~9 px de ellos).
     */
    private fun anchoredFrame(pose: Se3): DepthFrame {
        val f = depthOf(pose, listOf(wall))
        val n = f.width * f.height
        val depth = ShortArray(n)
        val conf = ByteArray(n)
        for (v in 0 until f.height) {
            for (u in 0 until f.width) {
                val i = v * f.width + u
                val truth = f.depthMm[i].toInt() and 0xFFFF
                if (truth == 0) continue
                if (u in 66..94 && v in 30..60) {
                    depth[i] = truth.toShort()
                    conf[i] = 250.toByte()
                } else {
                    val du = maxOf(66 - u, u - 94, 0).toDouble()
                    val dv = maxOf(30 - v, v - 60, 0).toDouble()
                    val d = Math.hypot(du, dv)
                    val bias = 1.0 + 0.25 * ((d - 3.0) / 6.0).coerceIn(0.0, 1.0)
                    depth[i] = Math.round(truth * bias).toInt().toShort()
                    conf[i] = DepthMerge.FILL_CONFIDENCE.toByte()
                }
            }
        }
        return DepthFrame(f.width, f.height, depth, conf, f.intrinsics, f.worldFromCamera, 0L)
    }

    /** Distancia media de los vértices a la pared verdadera (z = -3). */
    private fun meanWallError(mesh: MeshBuffer): Double {
        if (mesh.vertexCount == 0) return 0.0
        var sum = 0.0
        for (i in 0 until mesh.vertexCount) sum += abs(mesh.position(i).z - (-3.0))
        return sum / mesh.vertexCount
    }

    @Test
    fun fillDepthFarFromRawDataDoesNotPullTheSurfaceWhenAnchoringIsOn() {
        val frames = wideWalk.map { anchoredFrame(it) }
        val anchored = TsdfMesher(MeshBuffer(), TsdfConfig(anchorSigmaPx = 6f, anchorFloor = 0f))
        for (f in frames) anchored.integrate(f, 1f)
        val plain = TsdfMesher(MeshBuffer(), TsdfConfig(anchorSigmaPx = 0f))
        for (f in frames) plain.integrate(f, 1f)
        assertTrue("con anclaje hay superficie medida: ${anchored.mesh.vertexCount}", anchored.mesh.vertexCount > 20)
        val a = meanWallError(anchored.mesh)
        val p = meanWallError(plain.mesh)
        assertTrue("sin anclaje la pared se corre (error medio $p m)", p > 0.30)
        assertTrue("con anclaje queda cerca de la pared real: error medio $a m contra $p m sin anclaje", a < 0.5 * p)
    }

    @Test
    fun aFrameWithoutAnyRawDataCreatesNoSurfaceWhenAnchoring() {
        // Un cuadro de puro relleno (ARCore sin datos crudos, como en un muro liso): con anclaje no pesa nada.
        val f = anchoredFrame(poseAt(0.0))
        val rawLess = DepthFrame(f.width, f.height, f.depthMm, ByteArray(f.width * f.height) { DepthMerge.FILL_CONFIDENCE.toByte() }, f.intrinsics, f.worldFromCamera, 0L)
        val mesher = TsdfMesher(MeshBuffer(), TsdfConfig(anchorSigmaPx = 6f, anchorFloor = 0f))
        repeat(4) { mesher.integrate(rawLess, 1f) }
        assertEquals("sin datos crudos y sin piso no se integra nada", 0, mesher.voxelCount)

        // Con un piso de peso el relleno lejano sí entra, pero con mucha menos autoridad.
        val soft = TsdfMesher(MeshBuffer(), TsdfConfig(anchorSigmaPx = 6f, anchorFloor = 0.15f))
        repeat(4) { soft.integrate(rawLess, 1f) }
        assertTrue("con piso sí entra: ${soft.voxelCount}", soft.voxelCount > 0)
    }

    @Test
    fun theMeshFileRoundTripKeepsGeometryAndOnlyTheLiveQuads() {
        val mesh = scan(listOf(wall), sideToSide).mesh
        val live = (0 until mesh.quadCount).filter { !mesh.isDead(it) }
        assertEquals(mesh.liveQuadCount, live.size)
        val file = tmp.newFile("mesh.bin")
        MeshIO.write(file, mesh, 0.08f)
        val back = MeshIO.read(file)
        assertEquals("solo se guardan los vigentes", live.size, back.quadCount)
        assertTrue("sin vértices huérfanos: ${back.vertexCount} de ${mesh.vertexCount}", back.vertexCount <= mesh.vertexCount)
        for ((n, q) in live.withIndex()) {
            if (n % 17 != 0) continue
            val a = mesh.quad(q).map { mesh.position(it) }
            val b = back.quad(n).map { back.position(it) }
            for (k in 0 until 4) {
                assertEquals(a[k].x, b[k].x, 1e-5)
                assertEquals(a[k].y, b[k].y, 1e-5)
                assertEquals(a[k].z, b[k].z, 1e-5)
            }
        }
    }

    /** Cuadriláteros vigentes cuyo centro está en z ≈ `z` (±`tol`). */
    private fun liveQuadsNear(mesh: MeshBuffer, z: Double, tol: Double = 0.12): Int {
        var n = 0
        for (q in 0 until mesh.quadCount) {
            if (mesh.isDead(q)) continue
            val idx = mesh.quad(q)
            val zc = idx.sumOf { mesh.position(it).z } / 4.0
            if (abs(zc - z) < tol) n++
        }
        return n
    }

    @Test
    fun aGhostSurfaceDisappearsWhenLaterFramesSeeThroughIt() {
        val ghost = Plane(Vec3(0.0, 0.0, -1.5), Vec3(0.0, 0.0, 1.0))
        val mesher = TsdfMesher()
        for ((i, p) in sideToSide.withIndex()) mesher.integrate(depthOf(p, listOf(ghost), 0.0, 100 + i), 1f)
        val ghostBefore = liveQuadsNear(mesher.mesh, -1.5)
        assertTrue("el fantasma llegó a emitirse: $ghostBefore", ghostBefore > 40)

        // Ahora todos los cuadros ven la pared real, 1.5 m más atrás: están viendo a través del fantasma.
        for ((i, p) in wideWalk.withIndex()) mesher.integrate(depthOf(p, listOf(wall), 0.0, 200 + i), 1f)
        val ghostAfter = liveQuadsNear(mesher.mesh, -1.5)
        val wallNow = liveQuadsNear(mesher.mesh, -3.0)
        assertTrue("el fantasma se borra: antes $ghostBefore, después $ghostAfter", ghostAfter <= ghostBefore * 0.15)
        assertTrue("la pared real aparece: $wallNow", wallNow > 40)
        assertTrue("se quitaron cuadriláteros", mesher.removedQuads > 0)
    }

    @Test
    fun aRealSurfaceSurvivesAFewFramesThatSeeThroughIt() {
        val farWall = Plane(Vec3(0.0, 0.0, -3.9), Vec3(0.0, 0.0, 1.0))
        val mesher = TsdfMesher()
        var seed = 300
        repeat(2) { for (p in sideToSide) mesher.integrate(depthOf(p, listOf(wall), 0.0, seed++), 1f) }
        val before = liveQuadsNear(mesher.mesh, -3.0)
        assertTrue("la pared llegó a emitirse: $before", before > 100)

        // Tres cuadros de la pared de más atrás (la profundidad «se equivocó» y vio más lejos)…
        for (p in sideToSide.take(3)) mesher.integrate(depthOf(p, listOf(farWall), 0.0, seed++), 1f)
        // …y de nuevo la pared real.
        repeat(2) { for (p in sideToSide) mesher.integrate(depthOf(p, listOf(wall), 0.0, seed++), 1f) }
        val after = liveQuadsNear(mesher.mesh, -3.0)
        assertTrue("la pared real sobrevive: antes $before, después $after", after >= before * 0.8)
    }

    @Test
    fun aMovingObstacleLeavesNoGhostBehind() {
        val obstacle = Plane(Vec3(0.0, 0.0, -2.0), Vec3(0.0, 0.0, 1.0))
        val mesher = TsdfMesher()
        var seed = 400
        // Una «persona» a 2 m delante de la pared durante varios cuadros…
        repeat(2) { for (p in sideToSide) mesher.integrate(depthOf(p, listOf(obstacle), 0.0, seed++), 1f) }
        assertTrue(liveQuadsNear(mesher.mesh, -2.0) > 40)
        // …se va, y la cámara sigue viendo la pared.
        for (p in wideWalk) mesher.integrate(depthOf(p, listOf(wall), 0.0, seed++), 1f)
        assertTrue("sin rastro de la persona: ${liveQuadsNear(mesher.mesh, -2.0)}", liveQuadsNear(mesher.mesh, -2.0) < 10)
    }
}

/** Centro en z de los cuadriláteros VIGENTES cercanos a `z` (lo que de verdad se ve en pantalla). */
private fun liveMeanZ(mesh: MeshBuffer, z: Double, tol: Double = 0.25): Pair<Double, Int> {
    var sum = 0.0
    var n = 0
    for (q in 0 until mesh.quadCount) {
        if (mesh.isDead(q)) continue
        val idx = mesh.quad(q)
        val zc = idx.sumOf { mesh.position(it).z } / 4.0
        if (abs(zc - z) < tol) {
            sum += zc
            n++
        }
    }
    return Pair(if (n == 0) Double.NaN else sum / n, n)
}

/**
 * Lo que Eduardo describió el 22-oct: «la malla no se reconstruye correctamente después del primer reconocimiento».
 * Un lugar se ve primero con la profundidad equivocada por unos centímetros —lo normal de lejos y con el relleno de la
 * suave— y después se ve bien muchas veces. El volumen de distancias con signo SÍ se corrige (promedia los últimos
 * ~10–40 cuadros); la pregunta es si la malla que se DIBUJA lo sigue.
 */
class TsdfRefinementTest {

    private val wallOff = Plane(Vec3(0.0, 0.0, -2.93), Vec3(0.0, 0.0, 1.0))

    @Test
    fun aSurfaceSeenWrongAtFirstMovesToWhereLaterFramesSeeIt() {
        val mesher = TsdfMesher()
        var seed = 700
        // Primer reconocimiento: dos cuadros con la pared 7 cm adelantada (dentro del mismo voxel de 10 cm).
        for (p in sideToSide.take(2)) mesher.integrate(depthOf(p, listOf(wallOff), 0.0, seed++), 1f)
        val (zFirst, nFirst) = liveMeanZ(mesher.mesh, -3.0)
        assertTrue("la pared se emitió en el primer reconocimiento: $nFirst", nFirst > 50)

        // Después: 24 cuadros viendo la pared en su lugar real (z = -3.00).
        repeat(3) { for (p in sideToSide) mesher.integrate(depthOf(p, listOf(wall), 0.0, seed++), 1f) }
        val (zAfter, nAfter) = liveMeanZ(mesher.mesh, -3.0)

        // Lo que el volumen cree AHORA en el punto donde está la pared real: el cruce de signo debe caer en -3.00.
        val sdfAtTrue = mesher.probe(0.0, 1.4, -3.00)?.get(0)
        val sdfAtOld = mesher.probe(0.0, 1.4, -2.93)?.get(0)

        // Reparto de lo que se ve: ¿cuánto quedó en el lugar del primer reconocimiento y cuánto en el real?
        var stuck = 0
        var right = 0
        for (q in 0 until mesher.mesh.quadCount) {
            if (mesher.mesh.isDead(q)) continue
            val zc = mesher.mesh.quad(q).sumOf { mesher.mesh.position(it).z } / 4.0
            if (abs(zc + 3.00) > 0.25) continue
            if (zc > -2.965) stuck++ else right++
        }
        println("malla: primer reconocimiento z=$zFirst ($nFirst cuads) -> después z=$zAfter ($nAfter cuads)")
        println("reparto tras 24 cuadros buenos: $stuck cuadrilateros clavados en el lugar viejo, $right en el real")
        println("volumen: sdf(-3.00)=$sdfAtTrue  sdf(-2.93)=$sdfAtOld")

        assertTrue(
            "después de 24 cuadros buenos la malla debe caer en z = -3.00, no quedarse en -2.93: $zAfter",
            abs(zAfter + 3.00) < 0.025,
        )
    }
}
