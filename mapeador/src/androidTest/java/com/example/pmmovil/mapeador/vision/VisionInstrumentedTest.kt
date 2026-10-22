package com.example.pmmovil.mapeador.vision

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pmmovil.mapeador.core.BruteForceMatcher
import com.example.pmmovil.mapeador.core.CamPose
import com.example.pmmovil.mapeador.core.GrayImage
import com.example.pmmovil.mapeador.core.LandmarkSet
import com.example.pmmovil.mapeador.core.MapBuilder
import com.example.pmmovil.mapeador.core.Relocalizer
import com.example.pmmovil.mapeador.core.Se3
import com.example.pmmovil.mapeador.core.SyntheticScene
import com.example.pmmovil.mapeador.core.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** Pruebas con OpenCV de verdad (corren en el emulador o en el teléfono, no en la JVM). */
@RunWith(AndroidJUnit4::class)
class VisionInstrumentedTest {
    @Before
    fun loadOpenCv() {
        assertTrue("OpenCV no cargó", OpenCv.ensureLoaded())
    }

    /** Imagen con mucha textura: cuadros de brillo al azar (da cientos de esquinas para ORB). */
    private fun texturedImage(width: Int, height: Int, seed: Int = 1, shiftX: Int = 0, shiftY: Int = 0): GrayImage {
        val rnd = Random(seed)
        val data = ByteArray(width * height) { 90 }
        repeat(500) {
            val x0 = rnd.nextInt(width - 40) + shiftX
            val y0 = rnd.nextInt(height - 40) + shiftY
            val w = 8 + rnd.nextInt(28)
            val h = 8 + rnd.nextInt(28)
            val v = rnd.nextInt(256).toByte()
            for (y in y0 until minOf(y0 + h, height)) for (x in x0 until minOf(x0 + w, width)) {
                if (x >= 0 && y >= 0) data[y * width + x] = v
            }
        }
        return GrayImage(width, height, data)
    }

    @Test
    fun orbFindsFeaturesAndReportsCoordinatesInTheOriginalImage() {
        OrbFeatureExtractor().use { extractor ->
            val small = extractor.extract(texturedImage(640, 480))
            assertTrue("rasgos ${small.count}", small.count in 100..1000)
            assertEquals(small.count * 2, small.xy.size)
            assertEquals(small.count * LandmarkSet.DESC_BYTES, small.descriptors.size)
            assertTrue(small.xy.indices.all { i -> small.xy[i] >= 0f && small.xy[i] <= (if (i % 2 == 0) 640f else 480f) })
            assertTrue("descriptores vacíos", small.descriptors.any { it.toInt() != 0 })

            // Más ancha que el tope de procesamiento: se reduce por dentro pero las coordenadas vuelven a la original.
            val wide = extractor.extract(texturedImage(1280, 720, seed = 2))
            assertTrue("rasgos ${wide.count}", wide.count > 100)
            var maxX = 0f
            for (i in 0 until wide.count) maxX = maxOf(maxX, wide.xy[i * 2])
            assertTrue("x máxima $maxX debe pasar de 960", maxX > 960f && maxX <= 1280f)
        }
    }

    @Test
    fun matchingTwoShiftedViewsRecoversTheShift() {
        OrbFeatureExtractor().use { extractor ->
            OpenCvMatcher().use { matcher ->
                val a = extractor.extract(texturedImage(640, 480, seed = 3))
                val b = extractor.extract(texturedImage(640, 480, seed = 3, shiftX = 6, shiftY = 4))
                val knn = matcher.knn(a.descriptors, a.count, b.descriptors, b.count, 2)
                val dx = ArrayList<Float>()
                val dy = ArrayList<Float>()
                for (i in 0 until a.count) {
                    val j = knn.index[i * 2]
                    if (j < 0) continue
                    val d1 = knn.dist[i * 2]
                    val d2 = knn.dist[i * 2 + 1]
                    if (d1 > 40 || (d2 != Int.MAX_VALUE && d1 >= 0.8 * d2)) continue
                    dx.add(b.xy[j * 2] - a.xy[i * 2])
                    dy.add(b.xy[j * 2 + 1] - a.xy[i * 2 + 1])
                }
                assertTrue("coincidencias ${dx.size}", dx.size >= 30)
                assertEquals(6.0, dx.sorted()[dx.size / 2].toDouble(), 1.5)
                assertEquals(4.0, dy.sorted()[dy.size / 2].toDouble(), 1.5)
            }
        }
    }

    @Test
    fun openCvMatcherAgreesWithTheKotlinReference() {
        val rnd = Random(5)
        val query = ByteArray(40 * 32).also { rnd.nextBytes(it) }
        val train = ByteArray(300 * 32).also { rnd.nextBytes(it) }
        val k = 4
        val a = OpenCvMatcher().use { it.knn(query, 40, train, 300, k) }
        val b = BruteForceMatcher().knn(query, 40, train, 300, k)
        // Los índices pueden diferir si hay empates, las distancias no.
        assertTrue(a.dist.contentEquals(b.dist))
    }

    @Test
    fun pnpRecoversAKnownPoseEvenWithOutliers() {
        val scene = SyntheticScene(seed = 2)
        val pose = scene.poseAt(0.4, 8.0)
        val cam = CamPose(pose, scene.k)
        val rnd = Random(9)
        val obj = ArrayList<Float>()
        val img = ArrayList<Float>()
        var n = 0
        for (p in scene.points) {
            val uv = cam.project(p) ?: continue
            if (uv[0] < 0 || uv[0] > 640 || uv[1] < 0 || uv[1] > 480) continue
            obj += listOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
            val outlier = rnd.nextInt(5) == 0
            img += if (outlier) listOf(rnd.nextFloat() * 640f, rnd.nextFloat() * 480f)
            else listOf((uv[0] + rnd.nextDouble(-0.4, 0.4)).toFloat(), (uv[1] + rnd.nextDouble(-0.4, 0.4)).toFloat())
            n++
        }
        assertTrue("puntos visibles $n", n > 60)
        val result = OpenCvPnp().solve(obj.toFloatArray(), img.toFloatArray(), n, scene.k)
        assertNotNull(result)
        assertTrue("inliers ${result!!.inliers.size} de $n", result.inliers.size >= n * 0.7)
        assertEquals(0.0, result.cvFromMap.translationDistanceTo(cam.cvFromWorld), 0.03)
        assertEquals(0.0, result.cvFromMap.rotationAngleTo(cam.cvFromWorld), 0.5)
        assertTrue("error de reproyección ${result.meanReprojPx}", result.meanReprojPx < 1.0)
    }

    @Test
    fun fullPipelineLocalizesAQueryAndRecoversTheSessionOffset() {
        val scene = SyntheticScene(seed = 31)
        OpenCvMatcher().use { matcher ->
            val builder = MapBuilder(matcher)
            scene.path(24).forEachIndexed { i, pose ->
                builder.addKeyframe(pose, scene.k, scene.observe(pose), i * 300_000_000L)
            }
            val map = builder.landmarks
            assertTrue("puntos ${map.pointCount}", map.pointCount >= 150)

            val relocalizer = Relocalizer(map, matcher, OpenCvPnp())
            val queryPose = scene.poseAt(0.2, 3.0) // pose real de la cámara en el marco del mapa
            val fix = relocalizer.locate(scene.observe(queryPose), scene.k)
            assertTrue(fix.reason, fix.ok)
            assertTrue("inliers ${fix.inliers}", fix.inliers >= 30)
            assertEquals(0.0, fix.mapFromCamera!!.translationDistanceTo(queryPose), 0.05)
            assertEquals(0.0, fix.mapFromCamera!!.rotationAngleTo(queryPose), 0.5)

            // Otra sesión de ARCore: su marco está desplazado y girado respecto al del mapa.
            val mapFromSession = Se3.rotationAboutY(37.0, Vec3(2.0, 0.1, -1.5))
            val sessionFromCamera = mapFromSession.inverse().compose(queryPose)
            val estimated = fix.mapFromCamera!!.compose(sessionFromCamera.inverse())
            assertEquals(0.0, estimated.translationDistanceTo(mapFromSession), 0.06)
            assertEquals(0.0, estimated.rotationAngleTo(mapFromSession), 0.6)

            // Con una estimación previa (la mantiene PoseFusion) también debe ubicar.
            val withPrior = relocalizer.locate(scene.observe(queryPose), scene.k, mapFromSession.compose(sessionFromCamera))
            assertTrue(withPrior.reason, withPrior.ok)
            assertTrue("debió usar la estimación previa", withPrior.usedPrior)
        }
    }

    @Test
    fun jpegEncoderProducesADecodableImage() {
        val bytes = JpegEncoder.encode(texturedImage(320, 240, seed = 4))
        assertTrue(bytes.size > 500)
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(bitmap)
        assertEquals(320, bitmap.width)
        assertEquals(240, bitmap.height)
    }
}
