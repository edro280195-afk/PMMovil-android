package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.hypot

private fun buildMap(scene: SyntheticScene, keyframes: Int): MapBuilder {
    val builder = MapBuilder(BruteForceMatcher())
    scene.path(keyframes).forEachIndexed { i, pose ->
        builder.addKeyframe(pose, scene.k, scene.observe(pose), i * 300_000_000L)
    }
    return builder
}

private fun errorsOf(scene: SyntheticScene, lm: LandmarkSet): List<Double> =
    (0 until lm.pointCount).map { scene.nearest(lm.pointPosition(it)).second }.sorted()

class MapBuilderTest {
    @Test
    fun exactObservationsGiveExactPoints() {
        // Sin ruido ni bits cambiados la geometría tiene que cuadrar al milímetro: si los ejes o los
        // signos estuvieran mal, aquí se vería de inmediato.
        val scene = SyntheticScene(seed = 11)
        val builder = MapBuilder(BruteForceMatcher())
        scene.path(24).forEachIndexed { i, pose ->
            builder.addKeyframe(pose, scene.k, scene.observe(pose, noisePx = 0.0, bitFlips = 0), i * 300_000_000L)
        }
        val lm = builder.landmarks
        assertTrue("puntos: ${lm.pointCount}", lm.pointCount >= 150)
        val errors = errorsOf(scene, lm)
        assertTrue("mediana ${errors[errors.size / 2]} m", errors[errors.size / 2] < 1e-4)
        assertTrue("percentil 99 ${errors[(errors.size * 0.99).toInt()]} m", errors[(errors.size * 0.99).toInt()] < 1e-3)
    }

    @Test
    fun noisyObservationsStayCloseToTheTruth() {
        val scene = SyntheticScene(seed = 11)
        val lm = buildMap(scene, 24).landmarks
        assertTrue("puntos: ${lm.pointCount}", lm.pointCount >= 150)
        val errors = errorsOf(scene, lm)
        val median = errors[errors.size / 2]
        val p90 = errors[(errors.size * 0.9).toInt()]
        assertTrue("mediana $median m", median < 0.10)
        assertTrue("percentil 90 $p90 m", p90 < 0.35)
    }

    @Test
    fun pointsAreSharedAcrossViewsInsteadOfDuplicated() {
        val scene = SyntheticScene(seed = 11)
        val lm = buildMap(scene, 24).landmarks
        assertTrue("puntos ${lm.pointCount} para ${scene.points.size} lugares reales", lm.pointCount <= scene.points.size * 1.2)
        assertTrue("cada punto tiene al menos una vista", lm.entryCount >= lm.pointCount)
        for (p in 0 until lm.pointCount) assertTrue("el punto $p no tiene vistas", lm.entriesOf(p) >= 1)
    }

    @Test
    fun viewsThatLookDifferentAreKeptAsExtraEntriesWithACap() {
        // En la vida real el descriptor cambia con el ángulo de vista; aquí se simula con muchos bits cambiados.
        val scene = SyntheticScene(seed = 17)
        val builder = MapBuilder(BruteForceMatcher())
        scene.path(24).forEachIndexed { i, pose ->
            builder.addKeyframe(pose, scene.k, scene.observe(pose, noisePx = 0.3, bitFlips = 30), i * 300_000_000L)
        }
        val lm = builder.landmarks
        assertTrue("puntos: ${lm.pointCount}", lm.pointCount >= 100)
        assertTrue("vistas ${lm.entryCount} para ${lm.pointCount} puntos", lm.entryCount > lm.pointCount * 1.5)
        var maxEntries = 0
        for (p in 0 until lm.pointCount) maxEntries = maxOf(maxEntries, lm.entriesOf(p))
        assertTrue("máximo de vistas por punto $maxEntries", maxEntries <= BuilderConfig().maxEntriesPerPoint)
    }

    @Test
    fun aStandingStillCameraAddsNothing() {
        val scene = SyntheticScene(seed = 5)
        val builder = MapBuilder(BruteForceMatcher())
        val pose = scene.poseAt(0.0)
        repeat(4) { builder.addKeyframe(pose, scene.k, scene.observe(pose), it * 300_000_000L) }
        assertEquals("sin desplazamiento no hay paralaje", 0, builder.landmarks.pointCount)
    }

    @Test
    fun repeatingTheLastStretchBarelyAddsPoints() {
        val scene = SyntheticScene(seed = 9)
        val builder = buildMap(scene, 12)
        val before = builder.landmarks.pointCount
        // Repetir el último tramo: los lugares ya existen y deben sumarse, no duplicarse.
        scene.path(12).takeLast(4).forEachIndexed { i, pose ->
            builder.addKeyframe(pose, scene.k, scene.observe(pose), (100 + i) * 300_000_000L)
        }
        assertTrue("antes $before, después ${builder.landmarks.pointCount}", builder.landmarks.pointCount < before * 1.25)
    }
}

class RelocalizerTest {
    /**
     * Resuelve «PnP» con la verdad conocida: acepta las coincidencias que reproyectan bien con la pose
     * real. Así se prueba el emparejado, los índices y la conversión de ejes sin necesitar OpenCV.
     */
    private class OracleSolver(private val truth: CamPose) : PoseSolver {
        var pairsSeen = 0
        var goodPairs = 0

        override fun solve(objectPoints: FloatArray, imagePoints: FloatArray, count: Int, k: Intrinsics): PnpResult? {
            pairsSeen = count
            val good = ArrayList<Int>()
            var sum = 0.0
            for (i in 0 until count) {
                val p = truth.project(Vec3(objectPoints[i * 3].toDouble(), objectPoints[i * 3 + 1].toDouble(), objectPoints[i * 3 + 2].toDouble()))
                    ?: continue
                val e = hypot(p[0] - imagePoints[i * 2], p[1] - imagePoints[i * 2 + 1])
                if (e < 3.0) {
                    good.add(i)
                    sum += e
                }
            }
            goodPairs = good.size
            if (good.size < 15) return null
            return PnpResult(truth.cvFromWorld, good.toIntArray(), sum / good.size)
        }
    }

    private val scene = SyntheticScene(seed = 21)
    private val map = buildMap(scene, 24).landmarks
    private val queryPose = scene.poseAt(0.15, 2.0)
    private val truth = CamPose(queryPose, scene.k)
    private val query = scene.observe(queryPose)

    @Test
    fun matchesLandmarksToTheRightPixelsAndReturnsTheTruePose() {
        val solver = OracleSolver(truth)
        val fix = Relocalizer(map, BruteForceMatcher(), solver).locate(query, scene.k)
        assertTrue(fix.reason, fix.ok)
        assertTrue("pares ${solver.pairsSeen}", solver.pairsSeen >= 60)
        assertTrue("buenos ${solver.goodPairs} de ${solver.pairsSeen}", solver.goodPairs >= solver.pairsSeen * 0.7)
        val pose = fix.mapFromCamera!!
        assertEquals(0.0, pose.translationDistanceTo(queryPose), 1e-6)
        assertEquals(0.0, pose.rotationAngleTo(queryPose), 1e-4)
    }

    @Test
    fun aGoodPriorNarrowsTheSearchAndStillSucceeds() {
        val prior = scene.poseAt(0.30, 5.0)
        val fix = Relocalizer(map, BruteForceMatcher(), OracleSolver(truth)).locate(query, scene.k, prior)
        assertTrue(fix.reason, fix.ok)
        assertTrue("debió usar la estimación previa", fix.usedPrior)
    }

    @Test
    fun aWrongPriorFallsBackToTheWholeMap() {
        val wrong = scene.poseAt(0.0, 170.0) // mirando hacia atrás
        val fix = Relocalizer(map, BruteForceMatcher(), OracleSolver(truth)).locate(query, scene.k, wrong)
        assertTrue(fix.reason, fix.ok)
        assertNotNull(fix.mapFromCamera)
    }

    @Test
    fun anImageWithTooFewFeaturesIsRefusedWithAReason() {
        val fix = Relocalizer(map, BruteForceMatcher(), OracleSolver(truth)).locate(Features.EMPTY, scene.k)
        assertFalse(fix.ok)
        assertTrue(fix.reason.contains("pocos rasgos"))
    }

    @Test
    fun anUnrelatedImageDoesNotLocalize() {
        val other = SyntheticScene(seed = 999)
        val fix = Relocalizer(map, BruteForceMatcher(), OracleSolver(truth)).locate(other.observe(queryPose), scene.k)
        assertFalse("no debe ubicar con una escena distinta", fix.ok)
    }
}

class MapStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun savesLoadsExportsAndDeletes() {
        val store = MapStore(tmp.newFolder("maps"))
        store.clearDraft()
        File(store.draftDir, MapStore.keyframeFileName(1)).writeBytes(byteArrayOf(1, 2, 3))

        val scene = SyntheticScene(seed = 3)
        val builder = buildMap(scene, 10)
        val locations = listOf(
            MapLocation("F1", "escritorio", Vec3(1.0, 0.0, -3.0), 1.2, "piso", 123L),
            MapLocation("ANDEN 3", "", Vec3(-2.5, 0.4, -6.0), 0.2, "muro", 456L)
        )
        val meta = MapMeta("map-test", "Oficina", 1000L, builder.keyframes.size, builder.landmarks.pointCount, locations.size, scene.k)
        store.save(meta, builder.landmarks, locations, builder.keyframes)

        val listed = store.listMaps()
        assertEquals(1, listed.size)
        assertEquals("Oficina", listed[0].name)
        assertEquals(builder.landmarks.pointCount, listed[0].landmarks)

        val loaded = store.load("map-test")
        val original = builder.landmarks
        assertEquals(original.pointCount, loaded.landmarks.pointCount)
        assertEquals(original.entryCount, loaded.landmarks.entryCount)
        for (i in 0 until original.pointCount * 3) assertEquals(original.pointPositions[i], loaded.landmarks.pointPositions[i], 0f)
        for (i in 0 until original.pointCount) assertEquals(original.pointObservations[i], loaded.landmarks.pointObservations[i])
        val e = original.entryCount
        assertTrue(original.descriptors.copyOf(e * 32).contentEquals(loaded.landmarks.descriptors.copyOf(e * 32)))
        for (i in 0 until e) {
            assertEquals(original.entryPoint[i], loaded.landmarks.entryPoint[i])
            assertEquals(original.entryKeyframe[i], loaded.landmarks.entryKeyframe[i])
        }
        // Lo cargado queda completo: las cadenas de entradas y la rejilla se reconstruyen.
        assertEquals(original.entriesOf(0), loaded.landmarks.entriesOf(0))
        assertEquals(0, loaded.landmarks.findNearPoint(original.pointPosition(0), 0.01))
        assertEquals(2, loaded.locations.size)
        assertEquals("ANDEN 3", loaded.locations[1].code)
        assertEquals(-2.5, loaded.locations[1].position.x, 1e-12)
        assertEquals("muro", loaded.locations[1].surface)
        assertEquals(scene.k, loaded.meta.intrinsics)

        // El borrador de JPEG se movió a la carpeta del mapa.
        assertTrue(File(store.mapDir("map-test"), "keyframes/${MapStore.keyframeFileName(1)}").exists())
        assertFalse(store.draftDir.exists())

        val out = store.exportColmap("map-test")
        val cameras = File(out, "cameras.txt").readLines().filter { !it.startsWith("#") && it.isNotBlank() }
        assertEquals("todas las imágenes comparten cámara", 1, cameras.size)
        assertTrue(cameras[0].startsWith("1 PINHOLE 640 480 500.00 500.00 320.00 240.00"))
        val images = File(out, "images.txt").readLines().filter { !it.startsWith("#") && it.isNotBlank() }
        assertEquals(builder.keyframes.size, images.size)

        // La pose de COLMAP es mundo→cámara: el centro de la cámara debe caer en el origen de la cámara.
        val f = images[0].split(" ")
        assertEquals(10, f.size)
        val worldToCam = Se3.fromQuaternion(f[2].toDouble(), f[3].toDouble(), f[4].toDouble(), f[1].toDouble(), Vec3(f[5].toDouble(), f[6].toDouble(), f[7].toDouble()))
        val center = builder.keyframes[0].pose.center
        assertEquals(0.0, worldToCam.apply(center).norm(), 1e-6)

        store.delete("map-test")
        assertTrue(store.listMaps().isEmpty())
    }

    @Test
    fun aHalfWrittenMapIsNotListed() {
        val root = tmp.newFolder("maps2")
        File(root, "map-roto.tmp").mkdirs()
        File(root, "_borrador").mkdirs()
        File(root, "map-sin-meta").mkdirs()
        assertTrue(MapStore(root).listMaps().isEmpty())
    }
}
