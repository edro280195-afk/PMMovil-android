package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Geometría del laboratorio de anclas, sin OpenCV: convenios de ejes, composición con la pose de ARCore, ángulo de
 * vista, elección entre las dos poses del PnP y dispersión. El detector (OpenCV) se prueba aparte, en el teléfono.
 */
class MarkerPoseTest {
    private val k = Intrinsics(921.2, 922.1, 641.3, 359.6, 1280, 720)
    private val side = 0.20

    /** Marcador de frente a una cámara que mira hacia -Z del mundo (la de ARCore sin girar), a `d` metros. */
    private fun worldFromMarkerFacingMinusZ(x: Double, y: Double, d: Double) = Se3(Se3.IDENTITY.r, Vec3(x, y, -d))

    private fun cvFromMarkerOf(worldFromGl: Se3, worldFromMarker: Se3): Se3 =
        CameraConventions.worldFromCv(worldFromGl).inverse().compose(worldFromMarker)

    @Test
    fun worldFromMarkerAgreesWithAManualChainOfFrames() {
        val worldFromGl = Se3.rotationAboutY(30.0, Vec3(1.0, 1.5, -2.0))
        val cvFromMarker = Se3.rotationAboutX(180.0).compose(Se3.rotationAboutY(20.0)).let { Se3(it.r, Vec3(0.3, -0.1, 4.0)) }
        val worldFromMarker = MarkerGeometry.worldFromMarker(worldFromGl, cvFromMarker)

        val p = Vec3(0.07, -0.04, 0.0) // un punto del marcador
        // Camino manual: marcador → cámara OpenCV → cámara OpenGL (se invierten Y y Z) → mundo.
        val viaCv = cvFromMarker.apply(p)
        val viaGl = CameraConventions.FLIP_YZ.apply(viaCv)
        val expected = worldFromGl.apply(viaGl)
        val got = worldFromMarker.apply(p)
        assertEquals(expected.x, got.x, 1e-9)
        assertEquals(expected.y, got.y, 1e-9)
        assertEquals(expected.z, got.z, 1e-9)
    }

    @Test
    fun projectionMatchesTheExistingCamPoseProjection() {
        val worldFromGl = Se3.rotationAboutY(12.0, Vec3(0.5, 1.4, 0.2))
        val worldFromMarker = Se3.rotationAboutY(-15.0, Vec3(1.1, 1.6, -5.0))
        val cvFromMarker = cvFromMarkerOf(worldFromGl, worldFromMarker)
        val ours = MarkerGeometry.project(cvFromMarker, side, k)
        assertNotNull(ours)

        val cam = CamPose(worldFromGl, k)
        val corners = MarkerGeometry.worldCorners(worldFromMarker, side)
        for ((i, c) in corners.withIndex()) {
            val uv = cam.project(c)
            assertNotNull(uv)
            assertEquals(uv!![0], ours!![i * 2], 1e-6)
            assertEquals(uv[1], ours[i * 2 + 1], 1e-6)
        }
    }

    @Test
    fun truePoseHasZeroReprojectionErrorAndAPerturbedOneDoesNot() {
        val worldFromGl = Se3.IDENTITY
        val cvFromMarker = cvFromMarkerOf(worldFromGl, worldFromMarkerFacingMinusZ(0.4, 0.1, 6.0))
        val corners = MarkerGeometry.project(cvFromMarker, side, k)!!
        assertEquals(0.0, MarkerGeometry.reprojectionRmsPx(cvFromMarker, side, k, corners), 1e-9)

        val moved = Se3(cvFromMarker.r, cvFromMarker.t + Vec3(0.05, 0.0, 0.0))
        assertTrue(MarkerGeometry.reprojectionRmsPx(moved, side, k, corners) > 1.0)
        val behind = Se3(cvFromMarker.r, Vec3(0.0, 0.0, -3.0))
        assertTrue(MarkerGeometry.reprojectionRmsPx(behind, side, k, corners).isInfinite())
    }

    @Test
    fun sizeInPixelsFollowsTheFocalLengthOverDistanceRule() {
        val cvFromMarker = cvFromMarkerOf(Se3.IDENTITY, worldFromMarkerFacingMinusZ(0.0, 0.0, 4.0))
        val corners = MarkerGeometry.project(cvFromMarker, side, k)!!
        // f·lado/d = 921.2 · 0.20 / 4 ≈ 46 px (la misma cuenta que la simulación en Python).
        assertEquals(k.fx * side / 4.0, MarkerGeometry.sizePx(corners), 0.5)
    }

    @Test
    fun viewAngleIsZeroFacingAndGrowsWithTheYaw() {
        val facing = Se3(Se3.rotationAboutX(180.0).r, Vec3(0.0, 0.0, 4.0))
        assertEquals(0.0, MarkerGeometry.viewAngleDeg(facing), 1e-6)
        assertTrue(MarkerGeometry.isPlausible(facing))

        val yawed = Se3.rotationAboutY(60.0).compose(Se3.rotationAboutX(180.0)).let { Se3(it.r, Vec3(0.0, 0.0, 4.0)) }
        assertEquals(60.0, MarkerGeometry.viewAngleDeg(yawed), 1e-6)

        val back = Se3(Se3.IDENTITY.r, Vec3(0.0, 0.0, 4.0)) // de espaldas: su normal apunta lejos de la cámara
        assertEquals(180.0, MarkerGeometry.viewAngleDeg(back), 1e-6)
        assertFalse(MarkerGeometry.isPlausible(back))

        val edgeOn = Se3.rotationAboutY(88.0).compose(Se3.rotationAboutX(180.0)).let { Se3(it.r, Vec3(0.0, 0.0, 4.0)) }
        assertFalse(MarkerGeometry.isPlausible(edgeOn))
    }

    @Test
    fun choosePoseKeepsThePlausibleOneAndReportsAmbiguity() {
        val truth = cvFromMarkerOf(Se3.IDENTITY, worldFromMarkerFacingMinusZ(0.3, 0.0, 5.0))
        val corners = MarkerGeometry.project(truth, side, k)!!

        // Una candidata de espaldas se descarta: gana la verdadera y no hay ambigüedad que medir.
        val back = Se3(Se3.IDENTITY.r, truth.t)
        val only = MarkerGeometry.choosePose(listOf(back, truth), side, k, corners)!!
        assertEquals(0.0, only.reprojectionPx, 1e-9)
        assertTrue(only.ambiguity.isInfinite())

        // Dos candidatas creíbles: gana la de menor error y la ambigüedad es peor/mejor (> 1).
        // (Un giro de solo 6° en un marcador casi de frente mueve las esquinas < 0.1 px, menos que el ruido: esa es justo la
        // ambigüedad del PnP plano. Para probar la elección se usa una alternativa claramente distinta.)
        val twisted = Se3.rotationAboutY(35.0).compose(truth).let { Se3(it.r, truth.t) }
        // Con esquinas perfectas la mejor tiene error 0 y la ambigüedad sería infinita (se distinguen sin duda); en la
        // realidad las esquinas traen ruido, así que se les mete medio píxel.
        val noisy = corners.copyOf().also { it[0] += 0.4; it[3] -= 0.3; it[5] += 0.2 }
        val both = MarkerGeometry.choosePose(listOf(twisted, truth), side, k, noisy)!!
        for (i in 0 until 9) assertEquals("gana la verdadera", truth.r[i], both.cvFromMarker.r[i], 1e-9)
        assertTrue("error de la mejor: ${both.reprojectionPx}", both.reprojectionPx in 0.05..1.0)
        assertTrue("ambigüedad: ${both.ambiguity}", both.ambiguity > 1.0 && both.ambiguity.isFinite())

        assertNull(MarkerGeometry.choosePose(listOf(back), side, k, corners))
    }

    @Test
    fun scatterMeasuresTheSpreadAndForgetsOldSamples() {
        val s = PositionScatter(maxSamples = 300)
        assertNull(s.rmsM())
        for (i in 0 until 400) s.add(Vec3(1.0 + if (i % 2 == 0) 0.01 else -0.01, 2.0, 3.0))
        assertEquals(300, s.count)
        assertEquals(0.01, s.rmsM()!!, 1e-9)
        val c = s.centroid()!!
        assertEquals(1.0, c.x, 1e-9)
        s.clear()
        assertEquals(0, s.count)
    }

    /** Marcador quieto, cámara caminando: sin ruido la posición en el mundo no se mueve; con ruido en la distancia, la dispersión lo refleja. */
    @Test
    fun aStaticMarkerSeenFromAWalkingCameraHasTheNoiseThatWasInjected() {
        val worldFromMarker = Se3(Se3.IDENTITY.r, Vec3(0.5, 1.4, -8.0))
        val rnd = Random(7)
        val clean = PositionScatter()
        val noisy = PositionScatter()
        for (step in 0 until 200) {
            val cam = Se3.rotationAboutY(0.0, Vec3(-1.0 + step * 0.02, 1.5, 0.0)) // camina 4 m hacia un lado
            val cv = cvFromMarkerOf(cam, worldFromMarker)
            clean.add(MarkerGeometry.worldFromMarker(cam, cv).t)

            // El ruido de la simulación en Python es casi todo a lo largo de la línea de vista: se inyecta ahí (σ = 5 cm).
            val ray = cv.t.normalized()
            val noisyCv = Se3(cv.r, cv.t + ray * (rnd.nextGaussian() * 0.05))
            noisy.add(MarkerGeometry.worldFromMarker(cam, noisyCv).t)
        }
        assertEquals(0.0, clean.rmsM()!!, 1e-9)
        assertTrue("dispersión con ruido: ${noisy.rmsM()}", noisy.rmsM()!! in 0.035..0.065)
    }

    @Test
    fun markerBookGroupsByFamilyAndId() {
        val book = MarkerBook()
        val cv = cvFromMarkerOf(Se3.IDENTITY, worldFromMarkerFacingMinusZ(0.0, 0.0, 3.0))
        val corners = MarkerGeometry.project(cv, side, k)!!
        fun obs(family: MarkerFamily, id: String) = MarkerObservation(
            0L, family, id, side, corners, cv, MarkerGeometry.worldFromMarker(Se3.IDENTITY, cv), Vec3.ZERO, 0.0, Double.POSITIVE_INFINITY
        )
        book.add(obs(MarkerFamily.APRILTAG_36H11, "7"))
        book.add(obs(MarkerFamily.APRILTAG_36H11, "7"))
        book.add(obs(MarkerFamily.ARUCO_4X4, "7"))
        assertEquals(2, book.get("APRILTAG_36H11:7")!!.samples)
        assertEquals(1, book.get("ARUCO_4X4:7")!!.samples)
        assertNull(book.get("QR:7"))
        book.clear()
        assertNull(book.get("APRILTAG_36H11:7"))
    }
}
