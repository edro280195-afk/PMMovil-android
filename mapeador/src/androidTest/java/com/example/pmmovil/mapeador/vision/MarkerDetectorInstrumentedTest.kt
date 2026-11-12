package com.example.pmmovil.mapeador.vision

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.MarkerFamily
import com.example.pmmovil.mapeador.core.MarkerGeometry
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.Objdetect
import org.opencv.objdetect.QRCodeEncoder
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Prueba del detector de anclas con OpenCV de verdad (corre en el teléfono, no en la JVM): dibuja un marcador con una pose
 * conocida, lo lee y resuelve su pose, y compara. Es la misma comprobación que `tools/anchors_simulation.py` hace en la
 * PC, pero ejercitando el código del APK (lectura de las matrices de esquinas, PnP, orden de las esquinas).
 */
@RunWith(AndroidJUnit4::class)
class MarkerDetectorInstrumentedTest {
    private val k = Intrinsics(921.2, 922.1, 641.3, 359.6, 1280, 720)

    @Before
    fun loadOpenCv() {
        assertTrue("OpenCV no cargó", OpenCv.ensureLoaded())
    }

    private fun mul3(a: DoubleArray, b: DoubleArray): DoubleArray {
        val out = DoubleArray(9)
        for (i in 0..2) for (j in 0..2) out[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j]
        return out
    }

    /**
     * Dibuja `marker` (cuadrado blanco y negro con su propio borde) impreso sobre una hoja blanca de margen `quietPx`, de
     * lado real `sideM`, visto por una cámara en el origen con la pose `cvFromMarker` (marcador→cámara, ejes de OpenCV).
     */
    private fun render(marker: Mat, sideM: Double, cvFromMarker: Se3, quietPx: Int): GrayImage {
        val px = marker.rows()
        val full = px + 2 * quietPx
        val sheet = Mat(full, full, CvType.CV_8UC1, Scalar(255.0))
        val roi = sheet.submat(quietPx, quietPx + px, quietPx, quietPx + px)
        marker.copyTo(roi)

        val scale = sideM / px
        val c = full / 2.0
        // Píxel de la hoja -> punto del marco del marcador (X derecha, Y arriba): x = (u-c)·s, y = -(v-c)·s.
        val toPlane = doubleArrayOf(scale, 0.0, -c * scale, 0.0, -scale, c * scale, 0.0, 0.0, 1.0)
        val r = cvFromMarker.r
        val t = cvFromMarker.t
        val extrinsics = doubleArrayOf(r[0], r[1], t.x, r[3], r[4], t.y, r[6], r[7], t.z) // columnas r1, r2, t
        val kMatrix = doubleArrayOf(k.fx, 0.0, k.cx, 0.0, k.fy, k.cy, 0.0, 0.0, 1.0)
        val h = mul3(kMatrix, mul3(extrinsics, toPlane))
        val hMat = Mat(3, 3, CvType.CV_64F)
        hMat.put(0, 0, *h)

        val dst = Mat(k.height, k.width, CvType.CV_8UC1, Scalar(110.0))
        try {
            Imgproc.warpPerspective(sheet, dst, hMat, Size(k.width.toDouble(), k.height.toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_TRANSPARENT, Scalar(0.0))
            Imgproc.GaussianBlur(dst, dst, Size(0.0, 0.0), 0.7)
            val bytes = ByteArray(k.width * k.height)
            dst.get(0, 0, bytes)
            return GrayImage(k.width, k.height, bytes)
        } finally {
            roi.release()
            sheet.release()
            hMat.release()
            dst.release()
        }
    }

    /** Un marcador que mira de frente a la cámara (rotación de 180° sobre X) y gira `yawDeg` sobre la vertical. */
    private fun pose(x: Double, y: Double, d: Double, yawDeg: Double): Se3 {
        val rot = Se3.rotationAboutY(yawDeg).compose(Se3.rotationAboutX(180.0))
        return Se3(rot.r, Vec3(x, y, d))
    }

    private fun arucoImage(dictionaryId: Int, id: Int, modules: Int): Mat {
        val img = Mat()
        Objdetect.generateImageMarker(Objdetect.getPredefinedDictionary(dictionaryId), id, modules * 50, img, 1)
        return img
    }

    private fun assertDetectedPose(detector: MarkerDetector, image: GrayImage, family: MarkerFamily, id: String, side: Double, truth: Se3) {
        val found = detector.detect(image, family)
        assertEquals("marcadores leídos: ${found.map { it.id }}", 1, found.size)
        assertEquals(id, found[0].id)

        // Orden de las esquinas: arriba-izquierda, arriba-derecha, abajo-derecha, abajo-izquierda del MARCADOR.
        val expected = MarkerGeometry.project(truth, side, k)!!
        for (i in 0 until 8) assertEquals("esquina ${i / 2}, eje ${i % 2}", expected[i], found[0].cornersPx[i], 2.5)

        val choice = detector.solve(found[0].cornersPx, side, k)
        assertNotNull("sin pose", choice)
        val got = choice!!.cvFromMarker.t
        val error = got.distanceTo(truth.t)
        val ray = truth.t.normalized()
        val along = (got - truth.t).dot(ray)
        val lateral = (got - truth.t - ray * along).norm()
        assertTrue("error 3D ${error * 100} cm (distancia ${abs(along) * 100}, lateral ${lateral * 100})", error < 0.08)
        assertTrue("error lateral ${lateral * 100} cm", lateral < 0.02)
        assertTrue("reproyección ${choice.reprojectionPx} px", choice.reprojectionPx < 2.0)
    }

    @Test
    fun aprilTagIsReadAndItsPoseMatchesTheRenderedOne() {
        val marker = arucoImage(Objdetect.DICT_APRILTAG_36h11, 7, 8)
        try {
            MarkerDetector().use { detector ->
                for (yaw in listOf(0.0, 30.0)) {
                    val truth = pose(0.3, 0.05, 4.0, yaw)
                    val image = render(marker, 0.20, truth, quietPx = 200)
                    assertDetectedPose(detector, image, MarkerFamily.APRILTAG_36H11, "7", 0.20, truth)
                }
            }
        } finally {
            marker.release()
        }
    }

    @Test
    fun arucoFourByFourIsReadAndItsPoseMatches() {
        val marker = arucoImage(Objdetect.DICT_4X4_50, 3, 6)
        try {
            MarkerDetector().use { detector ->
                val truth = pose(-0.4, 0.1, 3.0, 20.0)
                val image = render(marker, 0.20, truth, quietPx = 150)
                assertDetectedPose(detector, image, MarkerFamily.ARUCO_4X4, "3", 0.20, truth)
            }
        } finally {
            marker.release()
        }
    }

    @Test
    fun qrIsDecodedAndItsCornersFollowTheSymbol() {
        // El codificador agrega su propio margen: se recorta al cuadrado de módulos (a eso se refiere el «lado» del QR).
        val raw = Mat()
        QRCodeEncoder.create().encode("ANCLA-0001", raw)
        val bytes = ByteArray(raw.rows() * raw.cols())
        raw.get(0, 0, bytes)
        var minX = raw.cols()
        var minY = raw.rows()
        var maxX = -1
        var maxY = -1
        for (y in 0 until raw.rows()) for (x in 0 until raw.cols()) {
            if ((bytes[y * raw.cols() + x].toInt() and 0xFF) < 128) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        val crop = raw.submat(minY, maxY + 1, minX, maxX + 1)
        val symbol = Mat()
        Imgproc.resize(crop, symbol, Size(400.0, 400.0), 0.0, 0.0, Imgproc.INTER_NEAREST)
        try {
            MarkerDetector().use { detector ->
                val truth = pose(0.2, 0.0, 2.5, 0.0)
                val image = render(symbol, 0.20, truth, quietPx = 120)
                assertDetectedPose(detector, image, MarkerFamily.QR, "ANCLA-0001", 0.20, truth)
            }
        } finally {
            crop.release()
            symbol.release()
            raw.release()
        }
    }

    @Test
    fun anEmptyImageHasNoMarkers() {
        MarkerDetector().use { detector ->
            val blank = GrayImage(k.width, k.height, ByteArray(k.width * k.height) { 110 })
            for (family in MarkerFamily.entries) {
                assertTrue("falsos positivos en $family", detector.detect(blank, family).isEmpty())
            }
        }
    }

    @Test
    fun theSizeInPixelsOfTheCornersMatchesTheFocalLengthRule() {
        val marker = arucoImage(Objdetect.DICT_APRILTAG_36h11, 1, 8)
        try {
            MarkerDetector().use { detector ->
                val truth = pose(0.0, 0.0, 5.0, 0.0)
                val found = detector.detect(render(marker, 0.30, truth, quietPx = 200), MarkerFamily.APRILTAG_36H11)
                assertEquals(1, found.size)
                val size = MarkerGeometry.sizePx(found[0].cornersPx)
                assertEquals(k.fx * 0.30 / 5.0, size, 2.0)
                assertTrue(hypot(found[0].cornersPx[0] - found[0].cornersPx[2], found[0].cornersPx[1] - found[0].cornersPx[3]) > 20.0)
            }
        } finally {
            marker.release()
        }
    }
}
