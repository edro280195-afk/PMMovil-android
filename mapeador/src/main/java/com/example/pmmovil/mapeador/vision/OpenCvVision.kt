package com.example.pmmovil.mapeador.vision

import android.util.Log
import com.example.pmmovil.mapeador.core.DescriptorMatcher
import com.example.pmmovil.mapeador.core.FeatureExtractor
import com.example.pmmovil.mapeador.core.Features
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.Intrinsics
import com.example.pmmovil.mapeador.core.KnnResult
import com.example.pmmovil.mapeador.core.LandmarkSet
import com.example.pmmovil.mapeador.core.PnpResult
import com.example.pmmovil.mapeador.core.PoseSolver
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.Vec3
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Size
import org.opencv.features2d.BFMatcher
import org.opencv.features2d.ORB
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import kotlin.math.hypot

private const val TAG = "MAPEADOR"

object OpenCv {
    @Volatile
    private var loaded = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (loaded) return true
        return try {
            System.loadLibrary("opencv_java4")
            loaded = true
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "No se pudo cargar OpenCV: ${e.message}")
            false
        }
    }
}

/** Rasgos ORB. Solo se debe usar desde un hilo a la vez (el objeto de OpenCV no es seguro entre hilos). */
class OrbFeatureExtractor(
    maxFeatures: Int = 1000,
    private val maxWidth: Int = 960
) : FeatureExtractor, AutoCloseable {
    private val orb = ORB.create(maxFeatures, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 12)

    override fun extract(image: GrayImage): Features {
        val full = Mat(image.height, image.width, CvType.CV_8UC1)
        val kps = MatOfKeyPoint()
        val desc = Mat()
        var work = full
        var scaled: Mat? = null
        try {
            full.put(0, 0, image.data)
            var scale = 1.0
            if (image.width > maxWidth) {
                scale = image.width.toDouble() / maxWidth
                scaled = Mat()
                Imgproc.resize(full, scaled, Size(maxWidth.toDouble(), image.height / scale), 0.0, 0.0, Imgproc.INTER_AREA)
                work = scaled
            }
            orb.detectAndCompute(work, Mat(), kps, desc)
            val points = kps.toArray()
            val n = minOf(points.size, desc.rows())
            if (n == 0) return Features.EMPTY
            val xy = FloatArray(n * 2)
            val response = FloatArray(n)
            for (i in 0 until n) {
                xy[i * 2] = (points[i].pt.x * scale).toFloat()
                xy[i * 2 + 1] = (points[i].pt.y * scale).toFloat()
                response[i] = points[i].response
            }
            val bytes = ByteArray(desc.rows() * LandmarkSet.DESC_BYTES)
            desc.get(0, 0, bytes)
            return Features(n, xy, response, bytes)
        } finally {
            full.release()
            scaled?.release()
            kps.release()
            desc.release()
        }
    }

    override fun close() {
        orb.clear()
    }
}

/** Fuerza bruta con distancia de Hamming (SIMD de OpenCV). */
class OpenCvMatcher : DescriptorMatcher, AutoCloseable {
    private val matcher = BFMatcher.create(Core.NORM_HAMMING, false)

    override fun knn(query: ByteArray, queryCount: Int, train: ByteArray, trainCount: Int, k: Int): KnnResult {
        val index = IntArray(queryCount * k) { -1 }
        val dist = IntArray(queryCount * k) { Int.MAX_VALUE }
        if (queryCount == 0 || trainCount == 0) return KnnResult(k, index, dist)

        val q = Mat(queryCount, LandmarkSet.DESC_BYTES, CvType.CV_8U)
        val t = Mat(trainCount, LandmarkSet.DESC_BYTES, CvType.CV_8U)
        val knn = ArrayList<MatOfDMatch>()
        try {
            q.put(0, 0, exact(query, queryCount * LandmarkSet.DESC_BYTES))
            t.put(0, 0, exact(train, trainCount * LandmarkSet.DESC_BYTES))
            matcher.knnMatch(q, t, knn, k)
            for (m in knn) {
                val arr = m.toArray()
                if (arr.isEmpty()) continue
                val qi = arr[0].queryIdx
                if (qi !in 0 until queryCount) continue
                for (j in 0 until minOf(k, arr.size)) {
                    index[qi * k + j] = arr[j].trainIdx
                    dist[qi * k + j] = arr[j].distance.toInt()
                }
            }
        } finally {
            q.release()
            t.release()
            knn.forEach { it.release() }
        }
        return KnnResult(k, index, dist)
    }

    private fun exact(src: ByteArray, size: Int): ByteArray = if (src.size == size) src else src.copyOf(size)

    override fun close() {
        matcher.clear()
    }
}

/** PnP robusto: RANSAC con AP3P y refinamiento Levenberg-Marquardt sobre los inliers. */
class OpenCvPnp(
    private val iterations: Int = 500,
    private val reprojectionErrorPx: Float = 3.0f,
    private val confidence: Double = 0.995
) : PoseSolver {
    override fun solve(objectPoints: FloatArray, imagePoints: FloatArray, count: Int, k: Intrinsics): PnpResult? {
        if (count < 6) return null
        val objMat = Mat(count, 1, CvType.CV_32FC3)
        val imgMat = Mat(count, 1, CvType.CV_32FC2)
        val kMat = Mat(3, 3, CvType.CV_64F)
        val rvec = Mat()
        val tvec = Mat()
        val inliersMat = Mat()
        val dist = MatOfDouble()
        var objIn: Mat? = null
        var imgIn: Mat? = null
        try {
            objMat.put(0, 0, objectPoints.copyOf(count * 3))
            imgMat.put(0, 0, imagePoints.copyOf(count * 2))
            kMat.put(0, 0, k.fx, 0.0, k.cx, 0.0, k.fy, k.cy, 0.0, 0.0, 1.0)

            val ok = Calib3d.solvePnPRansac(
                MatOfPoint3f(objMat), MatOfPoint2f(imgMat), kMat, dist, rvec, tvec,
                false, iterations, reprojectionErrorPx, confidence, inliersMat, Calib3d.SOLVEPNP_AP3P
            )
            if (!ok || inliersMat.rows() < 6) return null

            val idx = IntArray(inliersMat.rows())
            inliersMat.get(0, 0, idx)

            objIn = Mat(idx.size, 1, CvType.CV_32FC3)
            imgIn = Mat(idx.size, 1, CvType.CV_32FC2)
            val o = FloatArray(idx.size * 3)
            val im = FloatArray(idx.size * 2)
            for ((n, i) in idx.withIndex()) {
                o[n * 3] = objectPoints[i * 3]
                o[n * 3 + 1] = objectPoints[i * 3 + 1]
                o[n * 3 + 2] = objectPoints[i * 3 + 2]
                im[n * 2] = imagePoints[i * 2]
                im[n * 2 + 1] = imagePoints[i * 2 + 1]
            }
            objIn.put(0, 0, o)
            imgIn.put(0, 0, im)
            Calib3d.solvePnPRefineLM(objIn, imgIn, kMat, dist, rvec, tvec)

            val rmat = Mat()
            try {
                Calib3d.Rodrigues(rvec, rmat)
                val r = DoubleArray(9)
                rmat.get(0, 0, r)
                val t = DoubleArray(3)
                tvec.get(0, 0, t)
                val cvFromMap = Se3(r, Vec3(t[0], t[1], t[2]))
                return PnpResult(cvFromMap, idx, meanReprojection(cvFromMap, k, o, im))
            } finally {
                rmat.release()
            }
        } finally {
            objMat.release()
            imgMat.release()
            kMat.release()
            rvec.release()
            tvec.release()
            inliersMat.release()
            dist.release()
            objIn?.release()
            imgIn?.release()
        }
    }

    private fun meanReprojection(cvFromMap: Se3, k: Intrinsics, obj: FloatArray, img: FloatArray): Double {
        val n = obj.size / 3
        var sum = 0.0
        var used = 0
        for (i in 0 until n) {
            val c = cvFromMap.apply(Vec3(obj[i * 3].toDouble(), obj[i * 3 + 1].toDouble(), obj[i * 3 + 2].toDouble()))
            if (c.z < 1e-6) continue
            val u = k.fx * c.x / c.z + k.cx
            val v = k.fy * c.y / c.z + k.cy
            sum += hypot(u - img[i * 2], v - img[i * 2 + 1])
            used++
        }
        return if (used == 0) Double.MAX_VALUE else sum / used
    }
}

object JpegEncoder {
    /** Imagen en gris → bytes JPEG. */
    fun encode(image: GrayImage, quality: Int = 85): ByteArray {
        val mat = Mat(image.height, image.width, CvType.CV_8UC1)
        val out = MatOfByte()
        val params = MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, quality)
        try {
            mat.put(0, 0, image.data)
            Imgcodecs.imencode(".jpg", mat, out, params)
            return out.toArray()
        } finally {
            mat.release()
            out.release()
            params.release()
        }
    }
}

/** Todo lo de visión que necesita un hilo de trabajo, creado de una vez (después de cargar OpenCV). */
class VisionKit : AutoCloseable {
    init {
        check(OpenCv.ensureLoaded()) { "No se pudo cargar OpenCV." }
    }

    val extractor = OrbFeatureExtractor()
    val matcher = OpenCvMatcher()
    val solver = OpenCvPnp()

    override fun close() {
        extractor.close()
        matcher.close()
    }
}
