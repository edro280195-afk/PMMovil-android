package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun assertPose(expected: Se3, actual: Se3, metersTol: Double, degTol: Double, label: String = "") {
    assertEquals("$label traslación", 0.0, expected.translationDistanceTo(actual), metersTol)
    assertEquals("$label rotación", 0.0, expected.rotationAngleTo(actual), degTol)
}

class Se3Test {
    private val pose = Se3.fromQuaternion(0.1, 0.5, -0.3, 0.8, Vec3(1.0, -2.0, 3.5))

    @Test
    fun composeWithInverseIsIdentity() {
        assertPose(Se3.IDENTITY, pose.compose(pose.inverse()), 1e-9, 1e-6)
        assertPose(Se3.IDENTITY, pose.inverse().compose(pose), 1e-9, 1e-6)
    }

    @Test
    fun quaternionRoundTrip() {
        val q = pose.toQuaternionXyzw()
        val back = Se3.fromQuaternion(q[0], q[1], q[2], q[3], pose.t)
        assertPose(pose, back, 1e-9, 1e-6)
    }

    @Test
    fun rotationAngleMatchesKnownRotation() {
        assertEquals(30.0, Se3.IDENTITY.rotationAngleTo(Se3.rotationAboutY(30.0)), 1e-9)
        assertEquals(90.0, Se3.IDENTITY.rotationAngleTo(Se3.rotationAboutX(90.0)), 1e-9)
    }

    @Test
    fun blendHalfwayIsHalfTheRotation() {
        val half = Se3.blend(Se3.IDENTITY, Se3.rotationAboutY(40.0, Vec3(2.0, 0.0, 0.0)), 0.5)
        assertEquals(20.0, Se3.IDENTITY.rotationAngleTo(half), 0.05)
        assertEquals(1.0, half.t.x, 1e-9)
    }

    @Test
    fun averageOfSamePoseIsThatPose() {
        assertPose(pose, Se3.average(listOf(pose, pose, pose)), 1e-9, 1e-6)
    }

    @Test
    fun applyThenInverseApplyRestoresPoint() {
        val p = Vec3(0.3, 4.0, -2.0)
        val back = pose.inverse().apply(pose.apply(p))
        assertEquals(0.0, p.distanceTo(back), 1e-9)
    }
}

class CameraConventionsTest {
    private val k = Intrinsics(500.0, 500.0, 320.0, 240.0, 640, 480)

    @Test
    fun flipIsItsOwnInverse() {
        assertPose(Se3.IDENTITY, CameraConventions.FLIP_YZ.compose(CameraConventions.FLIP_YZ), 1e-12, 1e-9)
    }

    @Test
    fun pointStraightAheadHitsThePrincipalPoint() {
        val cam = CamPose(Se3.IDENTITY, k) // ARCore: mira hacia -Z
        val uv = cam.project(Vec3(0.0, 0.0, -5.0))!!
        assertEquals(320.0, uv[0], 1e-9)
        assertEquals(240.0, uv[1], 1e-9)
        assertEquals(5.0, uv[2], 1e-9)
    }

    @Test
    fun rightIsRightAndUpIsUpInTheImage() {
        val cam = CamPose(Se3.IDENTITY, k)
        val right = cam.project(Vec3(1.0, 0.0, -5.0))!!
        val up = cam.project(Vec3(0.0, 1.0, -5.0))!!
        assertTrue("a la derecha del mundo → u mayor", right[0] > 320.0)
        assertTrue("arriba en el mundo → v menor (el origen de la imagen está arriba)", up[1] < 240.0)
        assertEquals(500.0 * 1.0 / 5.0, right[0] - 320.0, 1e-9)
    }

    @Test
    fun pointBehindTheCameraIsNotProjected() {
        assertNull(CamPose(Se3.IDENTITY, k).project(Vec3(0.0, 0.0, 5.0)))
    }

    @Test
    fun forwardFollowsTheRotation() {
        val cam = CamPose(Se3.rotationAboutY(90.0), k)
        assertEquals(0.0, cam.forward.distanceTo(Vec3(-1.0, 0.0, 0.0)), 1e-9)
        val uv = cam.project(Vec3(-5.0, 0.0, 0.0))!!
        assertEquals(320.0, uv[0], 1e-6)
        assertEquals(240.0, uv[1], 1e-6)
    }

    @Test
    fun rayThroughAPixelPointsAtTheProjectedPoint() {
        val cam = CamPose(Se3.rotationAboutY(25.0, Vec3(1.0, 1.4, 0.5)), k)
        val target = Vec3(-2.0, 2.0, -6.0)
        val uv = cam.project(target)!!
        val ray = cam.ray(uv[0], uv[1])
        val expected = (target - cam.center).normalized()
        assertEquals(0.0, ray.distanceTo(expected), 1e-9)
    }
}

class TriangulatorTest {
    private val scene = SyntheticScene()
    private val a = CamPose(scene.poseAt(-0.4, 3.0), scene.k)
    private val b = CamPose(scene.poseAt(0.4, -3.0), scene.k)
    private val target = Vec3(1.0, 1.6, -5.0)

    @Test
    fun exactPixelsGiveTheExactPoint() {
        val pa = a.project(target)!!
        val pb = b.project(target)!!
        val r = Triangulator.midpoint(a, pa[0], pa[1], b, pb[0], pb[1])
        assertNotNull(r)
        assertEquals(0.0, r!!.point.distanceTo(target), 1e-6)
    }

    @Test
    fun halfAPixelOfNoiseStaysWithinAFewCentimeters() {
        val pa = a.project(target)!!
        val pb = b.project(target)!!
        val r = Triangulator.midpoint(a, pa[0] + 0.4, pa[1] - 0.3, b, pb[0] - 0.3, pb[1] + 0.2)
        assertNotNull(r)
        assertTrue("error ${r!!.point.distanceTo(target)}", r.point.distanceTo(target) < 0.12)
    }

    @Test
    fun withoutBaselineThereIsNothingToTriangulate() {
        val same = CamPose(scene.poseAt(0.0), scene.k)
        val p = same.project(target)!!
        assertNull(Triangulator.midpoint(same, p[0], p[1], same, p[0], p[1]))
    }

    @Test
    fun epipolarDistanceIsZeroForATrueMatchAndGrowsWhenShifted() {
        val pa = a.project(target)!!
        val pb = b.project(target)!!
        assertEquals(0.0, Epipolar.distancePx(a, pa[0], pa[1], b, pb[0], pb[1]), 1e-6)
        // La recta epipolar es casi horizontal (cámaras desplazadas en X): moverse en vertical la rompe.
        val off = Epipolar.distancePx(a, pa[0], pa[1], b, pb[0], pb[1] + 10.0)
        assertTrue("distancia $off", off > 8.0 && off < 12.0)
    }
}

class LandmarkSetTest {
    private fun desc(seed: Int) = ByteArray(LandmarkSet.DESC_BYTES) { (it * 31 + seed).toByte() }

    @Test
    fun hammingCountsDifferingBits() {
        val a = ByteArray(LandmarkSet.DESC_BYTES)
        val b = ByteArray(LandmarkSet.DESC_BYTES)
        b[0] = 0b0000_0111
        b[31] = 0b1000_0000.toByte()
        assertEquals(4, LandmarkSet.hamming(a, 0, b, 0))
    }

    @Test
    fun observationsAverageThePointPosition() {
        val set = LandmarkSet()
        val p = set.addPoint(Vec3(1.0, 1.0, 1.0))
        set.observe(p, Vec3(1.2, 1.0, 1.0))
        set.observe(p, Vec3(1.1, 1.0, 1.0))
        assertEquals(1.1, set.pointPosition(p).x, 1e-6)
        assertEquals(3, set.pointObservations[p])
    }

    @Test
    fun findNearPointNeedsProximity() {
        val set = LandmarkSet()
        val p = set.addPoint(Vec3(1.0, 1.0, 1.0))
        assertEquals(p, set.findNearPoint(Vec3(1.02, 1.0, 1.0), 0.05))
        assertEquals("muy lejos", -1, set.findNearPoint(Vec3(1.5, 1.0, 1.0), 0.05))
    }

    @Test
    fun findNearPointPicksTheClosestAndWorksAcrossCellsAndNegativeCoordinates() {
        val set = LandmarkSet()
        set.addPoint(Vec3(-0.001, -3.999, 2.0))
        val near = set.addPoint(Vec3(0.001, -4.000, 2.0))
        assertEquals(near, set.findNearPoint(Vec3(0.002, -4.001, 2.0), 0.05))
    }

    @Test
    fun entriesAreChainedPerPoint() {
        val set = LandmarkSet()
        val a = set.addPoint(Vec3(0.0, 0.0, 0.0))
        val b = set.addPoint(Vec3(5.0, 0.0, 0.0))
        set.addEntry(a, desc(1), 0, 1)
        set.addEntry(b, desc(50), 0, 2)
        set.addEntry(a, desc(2), 0, 3)
        assertEquals(2, set.entriesOf(a))
        assertEquals(1, set.entriesOf(b))
        assertEquals(0, set.minHammingTo(a, desc(1), 0))
        assertEquals(0, set.minHammingTo(a, desc(2), 0))
        assertTrue("no debe mirar las entradas de otro punto", set.minHammingTo(a, desc(50), 0) > 0)
        assertEquals(Int.MAX_VALUE, LandmarkSet().also { it.addPoint(Vec3.ZERO) }.minHammingTo(0, desc(1), 0))
        assertEquals(5.0, set.entryPosition(1).x, 1e-6)
    }

    @Test
    fun growsPastItsInitialCapacityAndSnapshotIsIndependent() {
        val set = LandmarkSet(2, 2)
        repeat(100) {
            val p = set.addPoint(Vec3(it.toDouble(), 0.0, 0.0))
            set.addEntry(p, desc(it), 0, it)
            set.addEntry(p, desc(it + 1000), 0, it)
        }
        assertEquals(100, set.pointCount)
        assertEquals(200, set.entryCount)
        val snap = set.snapshot()
        set.addPoint(Vec3(500.0, 0.0, 0.0))
        assertEquals(100, snap.pointCount)
        assertEquals(101, set.pointCount)
        assertEquals(42.0, snap.pointPosition(42).x, 1e-6)
        assertEquals("la copia conserva las cadenas de entradas", 2, snap.entriesOf(42))
        assertEquals(42, snap.findNearPoint(Vec3(42.01, 0.0, 0.0), 0.05))
    }
}

class KeyframePolicyTest {
    private val ms = 1_000_000L

    @Test
    fun firstFrameIsAlwaysCaptured() {
        assertTrue(KeyframePolicy().shouldCapture(Se3.IDENTITY, 0))
    }

    @Test
    fun smallMovesAreIgnoredAndBigOnesCaptured() {
        val p = KeyframePolicy()
        assertTrue(p.shouldCapture(Se3.IDENTITY, 0))
        p.markCaptured(Se3.IDENTITY, 0)
        assertFalse(p.shouldCapture(Se3.fromQuaternion(0.0, 0.0, 0.0, 1.0, Vec3(0.05, 0.0, 0.0)), 400 * ms))
        assertTrue(p.shouldCapture(Se3.fromQuaternion(0.0, 0.0, 0.0, 1.0, Vec3(0.30, 0.0, 0.0)), 800 * ms))
    }

    @Test
    fun rotationAloneCanTriggerACapture() {
        val p = KeyframePolicy()
        p.shouldCapture(Se3.IDENTITY, 0)
        p.markCaptured(Se3.IDENTITY, 0)
        assertTrue(p.shouldCapture(Se3.rotationAboutY(15.0), 1000 * ms))
    }

    @Test
    fun spinningTooFastSkipsTheFrameAndMinIntervalIsRespected() {
        val p = KeyframePolicy()
        p.shouldCapture(Se3.IDENTITY, 0)
        p.markCaptured(Se3.IDENTITY, 0)
        // 30° en 100 ms = 300°/s: borroso.
        p.shouldCapture(Se3.rotationAboutY(15.0), 900 * ms)
        assertFalse(p.shouldCapture(Se3.rotationAboutY(45.0), 1000 * ms))
        // Menos de 250 ms desde el último fotograma clave.
        val q = KeyframePolicy()
        q.shouldCapture(Se3.IDENTITY, 0)
        q.markCaptured(Se3.IDENTITY, 0)
        assertFalse(q.shouldCapture(Se3.fromQuaternion(0.0, 0.0, 0.0, 1.0, Vec3(1.0, 0.0, 0.0)), 100 * ms))
    }
}

class PoseFusionTest {
    private val base = Se3.rotationAboutY(20.0, Vec3(3.0, 0.0, -1.0))

    private fun near(dx: Double = 0.05) = Se3.fromQuaternion(
        base.toQuaternionXyzw()[0], base.toQuaternionXyzw()[1], base.toQuaternionXyzw()[2], base.toQuaternionXyzw()[3],
        Vec3(base.t.x + dx, base.t.y, base.t.z)
    )

    @Test
    fun aStrongFirstFixLocksImmediately() {
        val f = PoseFusion()
        val u = f.update(base, inliers = 80, nowMs = 1000)
        assertTrue(u.accepted && u.relocked)
        assertNotNull(f.mapFromSession)
    }

    @Test
    fun twoWeakFixesThatAgreeLockButAloneTheyDoNot() {
        val f = PoseFusion()
        assertFalse(f.update(base, inliers = 30, nowMs = 1000).accepted)
        assertNull(f.mapFromSession)
        assertTrue(f.update(near(0.1), inliers = 30, nowMs = 1500).accepted)
        assertNotNull(f.mapFromSession)
    }

    @Test
    fun anIsolatedWildFixIsRejectedAndDoesNotMoveTheEstimate() {
        val f = PoseFusion()
        f.update(base, 80, 1000)
        val wild = Se3.rotationAboutY(120.0, Vec3(40.0, 0.0, 0.0))
        val u = f.update(wild, 80, 1500)
        assertFalse(u.accepted)
        assertPose(base, f.mapFromSession!!, 1e-9, 1e-6)
    }

    @Test
    fun threeAgreeingOutliersRelockTheEstimate() {
        val f = PoseFusion()
        f.update(base, 80, 1000)
        val moved = Se3.rotationAboutY(90.0, Vec3(20.0, 0.0, 5.0))
        f.update(moved, 40, 1200)
        f.update(moved, 40, 1400)
        val u = f.update(moved, 40, 1600)
        assertTrue(u.relocked)
        assertPose(moved, f.mapFromSession!!, 0.05, 0.5)
    }

    @Test
    fun goodFixesPullTheEstimateTowardThem() {
        val f = PoseFusion()
        f.update(base, 80, 1000)
        val target = near(0.3)
        repeat(30) { f.update(target, 80, 2000L + it * 400) }
        assertEquals(0.0, f.mapFromSession!!.translationDistanceTo(target), 0.02)
    }
}

class ProjectionTest {
    /** Perspectiva estilo OpenGL (columna-mayor) para 60° de campo vertical. */
    private fun perspective(aspect: Float): FloatArray {
        val f = (1.0 / Math.tan(Math.toRadians(30.0))).toFloat()
        val n = 0.05f
        val fa = 100f
        val m = FloatArray(16)
        m[0] = f / aspect
        m[5] = f
        m[10] = (fa + n) / (n - fa)
        m[11] = -1f
        m[14] = 2 * fa * n / (n - fa)
        return m
    }

    private val identityView = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    @Test
    fun multiplyByIdentityChangesNothing() {
        val p = perspective(0.5f)
        val r = Projection.multiply(p, identityView)
        for (i in 0..15) assertEquals(p[i], r[i], 1e-6f)
    }

    @Test
    fun aPointStraightAheadLandsInTheMiddleOfTheScreen() {
        val vp = Projection.multiply(perspective(0.5f), identityView)
        val s = Projection.toScreen(vp, Vec3(0.0, 0.0, -4.0), 1080, 2160)!!
        assertEquals(540f, s.x, 0.5f)
        assertEquals(1080f, s.y, 0.5f)
        assertEquals(4f, s.depth, 1e-3f)
    }

    @Test
    fun upIsUpOnScreenAndBehindIsNull() {
        val vp = Projection.multiply(perspective(0.5f), identityView)
        val up = Projection.toScreen(vp, Vec3(0.0, 1.0, -4.0), 1080, 2160)!!
        assertTrue(up.y < 1080f)
        assertNull(Projection.toScreen(vp, Vec3(0.0, 0.0, 4.0), 1080, 2160))
    }

    @Test
    fun cameraSpaceOfAPointAheadHasNegativeZ() {
        val c = Projection.toCameraSpace(identityView, Vec3(1.0, 2.0, -3.0))
        assertEquals(1.0, c.x, 1e-6)
        assertEquals(2.0, c.y, 1e-6)
        assertEquals(-3.0, c.z, 1e-6)
    }

    @Test
    fun locationsAheadAreVisibleAndOnesBehindAreNot() {
        val vp = Projection.multiply(perspective(0.5f), identityView)
        val ahead = MapLocation("A1", "", Vec3(0.0, -0.5, -4.0), 1.2, "piso", 0)
        val behind = MapLocation("B2", "", Vec3(0.0, -0.5, 4.0), 1.2, "piso", 0)
        val out = Projection.projectLocations(listOf(ahead, behind), null, vp, Vec3.ZERO, 1080, 2160)
        assertTrue(out[0].visible)
        assertFalse(out[1].visible)
        assertTrue("la cabeza flota sobre la base", out[0].headY < out[0].baseY)
        assertEquals(4f, out[0].distanceM, 1e-3f)
    }

    @Test
    fun locationsAreMovedByTheSessionTransform() {
        val vp = Projection.multiply(perspective(0.5f), identityView)
        val loc = MapLocation("A1", "", Vec3(0.0, 0.0, -2.0), 0.0, "piso", 0)
        // El mapa está 2 m más atrás que la sesión: la ubicación queda a 4 m de la cámara.
        val sessionFromMap = Se3(Se3.IDENTITY.r, Vec3(0.0, 0.0, -2.0))
        val out = Projection.projectLocations(listOf(loc), sessionFromMap, vp, Vec3.ZERO, 1080, 2160)
        assertEquals(4f, out[0].distanceM, 1e-3f)
    }
}

class DemoDataTest {
    private val today = java.time.LocalDate.of(2026, 10, 20)

    @Test
    fun sameCodeAlwaysGivesTheSameEntries() {
        val a = DemoData.entriesFor("F1", today)
        val b = DemoData.entriesFor(" f1 ", today)
        assertEquals(a.size, b.size)
        a.zip(b).forEach { (x, y) ->
            assertEquals(x.reference, y.reference)
            assertEquals(x.packages, y.packages)
        }
    }

    @Test
    fun entriesLookLikeTheRealOnes() {
        val all = listOf("A1", "A2", "B1", "B2", "F1", "H1", "ANDEN 3", "PATIO", "E1", "C5").flatMap { DemoData.entriesFor(it, today) }
        assertTrue(all.isNotEmpty())
        all.forEach {
            assertTrue(it.reference.startsWith("01C26") && it.reference.length == 15)
            assertTrue(it.packages in 1..40)
            assertTrue(it.state == "ocupada" || it.state == "parcial")
            assertTrue(it.arrival.isBefore(today))
        }
    }

    @Test
    fun summaryReadsNaturally() {
        assertEquals("Libre · sin entradas", DemoData.summary(emptyList()))
        val one = listOf(DemoEntry("01C26", "SKF", "x", 5, "ocupada", today, "CAJA"))
        assertEquals("1 entrada · 5 bultos", DemoData.summary(one))
        assertEquals("2 entradas · 10 bultos", DemoData.summary(one + one))
    }
}
