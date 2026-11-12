package com.example.pmmovil.mapeador.vision

import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.MarkerFamily
import com.example.pmmovil.mapeador.core.MarkerGeometry
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.SquarePoseChoice
import com.example.pmmovil.mapeador.core.Vec3
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.objdetect.ArucoDetector
import org.opencv.objdetect.DetectorParameters
import org.opencv.objdetect.Objdetect
import org.opencv.objdetect.QRCodeDetectorAruco

/** Un marcador encontrado en la imagen: familia, número (o texto del QR) y sus cuatro esquinas (u, v; 8 valores). */
class RawMarker(val family: MarkerFamily, val id: String, val cornersPx: DoubleArray)

/**
 * Lee marcadores cuadrados (AprilTag 36h11, ArUco 4x4 y QR) con el OpenCV 4.13 del APK y resuelve su pose.
 * Es lo mismo que prueba `tools/anchors_simulation.py` en la PC. Solo se debe usar desde un hilo a la vez.
 *
 * Los intrínsecos de ARCore son los de la imagen tal como llega, sin coeficientes de distorsión: se resuelve sin ellos.
 */
class MarkerDetector : AutoCloseable {
    init {
        check(OpenCv.ensureLoaded()) { "No se pudo cargar OpenCV." }
    }

    private val arucoDetectors = HashMap<MarkerFamily, ArucoDetector>()
    private var qrDetector: QRCodeDetectorAruco? = null

    fun detect(image: GrayImage, family: MarkerFamily): List<RawMarker> {
        val mat = Mat(image.height, image.width, CvType.CV_8UC1)
        try {
            mat.put(0, 0, image.data)
            return if (family == MarkerFamily.QR) detectQr(mat) else detectAruco(mat, family)
        } finally {
            mat.release()
        }
    }

    private fun arucoFor(family: MarkerFamily): ArucoDetector = arucoDetectors.getOrPut(family) {
        val dictionaryId = when (family) {
            MarkerFamily.APRILTAG_36H11 -> Objdetect.DICT_APRILTAG_36h11
            MarkerFamily.ARUCO_4X4 -> Objdetect.DICT_4X4_50
            MarkerFamily.QR -> error("El QR no usa el detector ArUco.")
        }
        val params = DetectorParameters()
        // Esquinas con precisión de subpíxel: de ahí sale casi toda la exactitud de la distancia.
        params.set_cornerRefinementMethod(Objdetect.CORNER_REFINE_SUBPIX)
        ArucoDetector(Objdetect.getPredefinedDictionary(dictionaryId), params)
    }

    private fun detectAruco(mat: Mat, family: MarkerFamily): List<RawMarker> {
        val corners = ArrayList<Mat>()
        val ids = Mat()
        try {
            arucoFor(family).detectMarkers(mat, corners, ids)
            if (ids.empty() || corners.isEmpty()) return emptyList()
            val idArray = IntArray(ids.rows() * ids.cols())
            ids.get(0, 0, idArray)
            val out = ArrayList<RawMarker>(idArray.size)
            for (i in idArray.indices.take(corners.size)) {
                val c = FloatArray(8)
                corners[i].get(0, 0, c)
                out.add(RawMarker(family, idArray[i].toString(), DoubleArray(8) { c[it].toDouble() }))
            }
            return out
        } finally {
            corners.forEach { it.release() }
            ids.release()
        }
    }

    private fun detectQr(mat: Mat): List<RawMarker> {
        val detector = qrDetector ?: QRCodeDetectorAruco().also { qrDetector = it }
        val decoded = ArrayList<String>()
        val points = Mat()
        try {
            detector.detectAndDecodeMulti(mat, decoded, points)
            // Un QR que se ve pero no se logra decodificar igual sirve para medir distancia: se queda con sus esquinas.
            if (points.empty()) detector.detectMulti(mat, points)
            if (points.empty()) return emptyList()
            val total = (points.total() * points.channels()).toInt()
            val flat = FloatArray(total)
            points.get(0, 0, flat)
            val out = ArrayList<RawMarker>(total / 8)
            for (i in 0 until total / 8) {
                val text = decoded.getOrNull(i).orEmpty().ifEmpty { "(sin decodificar)" }
                out.add(RawMarker(MarkerFamily.QR, text, DoubleArray(8) { flat[i * 8 + it].toDouble() }))
            }
            return out
        } finally {
            points.release()
        }
    }

    /**
     * Pose del marcador en el marco de la cámara (OpenCV) con el PnP de un cuadrado plano (IPPE_SQUARE). Devuelve las dos
     * soluciones al elegidor de [MarkerGeometry], que descarta las imposibles y mide qué tan clara fue la elección.
     */
    fun solve(cornersPx: DoubleArray, sideM: Double, k: Intrinsics): SquarePoseChoice? {
        val objectMat = MatOfPoint3f()
        val imageMat = MatOfPoint2f()
        val cameraMat = Mat(3, 3, CvType.CV_64F)
        val distortion = MatOfDouble()
        val rvecs = ArrayList<Mat>()
        val tvecs = ArrayList<Mat>()
        try {
            objectMat.fromList(MarkerGeometry.objectCorners(sideM).map { Point3(it.x, it.y, it.z) })
            imageMat.fromList(List(4) { Point(cornersPx[it * 2], cornersPx[it * 2 + 1]) })
            cameraMat.put(0, 0, k.fx, 0.0, k.cx, 0.0, k.fy, k.cy, 0.0, 0.0, 1.0)

            val found = Calib3d.solvePnPGeneric(
                objectMat, imageMat, cameraMat, distortion, rvecs, tvecs, false, Calib3d.SOLVEPNP_IPPE_SQUARE
            )
            if (found <= 0) return null

            val candidates = ArrayList<Se3>(found)
            for (i in 0 until minOf(found, rvecs.size, tvecs.size)) {
                val rotation = Mat()
                try {
                    Calib3d.Rodrigues(rvecs[i], rotation)
                    val r = DoubleArray(9)
                    rotation.get(0, 0, r)
                    val t = DoubleArray(3)
                    tvecs[i].get(0, 0, t)
                    candidates.add(Se3(r, Vec3(t[0], t[1], t[2])))
                } finally {
                    rotation.release()
                }
            }
            return MarkerGeometry.choosePose(candidates, sideM, k, cornersPx)
        } finally {
            objectMat.release()
            imageMat.release()
            cameraMat.release()
            distortion.release()
            rvecs.forEach { it.release() }
            tvecs.forEach { it.release() }
        }
    }

    override fun close() {
        arucoDetectors.clear()
        qrDetector = null
    }
}
