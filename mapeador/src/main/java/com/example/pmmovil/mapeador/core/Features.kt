package com.example.pmmovil.mapeador.core

/** Imagen en escala de grises, fila por fila y sin relleno. */
class GrayImage(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(data.size >= width * height) { "La imagen no trae todos sus píxeles." }
    }
}

/**
 * Un cuadro listo para visión: imagen + pose de ARCore (estilo OpenGL, orientada al sensor)
 * + intrínsecos de esa imagen.
 */
class CameraFrame(
    val timestampNs: Long,
    val worldFromCamera: Se3,
    val intrinsics: Intrinsics,
    val gray: GrayImage
)

/** Rasgos de una imagen: coordenadas (x, y) en píxeles de la imagen original y descriptores de 32 bytes. */
class Features(val count: Int, val xy: FloatArray, val response: FloatArray, val descriptors: ByteArray) {
    companion object {
        val EMPTY = Features(0, FloatArray(0), FloatArray(0), ByteArray(0))
    }
}

interface FeatureExtractor {
    fun extract(image: GrayImage): Features
}

/**
 * Los `k` vecinos más cercanos (distancia de Hamming) de cada descriptor de consulta, ordenados de
 * menor a mayor. `index` y `dist` guardan `k` valores por consulta; si faltan vecinos, el índice es
 * -1 y la distancia Int.MAX_VALUE.
 */
class KnnResult(val k: Int, val index: IntArray, val dist: IntArray)

interface DescriptorMatcher {
    fun knn(query: ByteArray, queryCount: Int, train: ByteArray, trainCount: Int, k: Int): KnnResult
}

/** Resultado de PnP: pose mapa→cámara en convención OpenCV, índices de los inliers y error medio. */
class PnpResult(val cvFromMap: Se3, val inliers: IntArray, val meanReprojPx: Double)

interface PoseSolver {
    /** `objectPoints` va de 3 en 3 (x, y, z) e `imagePoints` de 2 en 2 (u, v). */
    fun solve(objectPoints: FloatArray, imagePoints: FloatArray, count: Int, k: Intrinsics): PnpResult?
}

/** Emparejador en Kotlin puro: lento, pero sirve para pruebas en la JVM. */
class BruteForceMatcher : DescriptorMatcher {
    override fun knn(query: ByteArray, queryCount: Int, train: ByteArray, trainCount: Int, k: Int): KnnResult {
        val index = IntArray(queryCount * k) { -1 }
        val dist = IntArray(queryCount * k) { Int.MAX_VALUE }
        for (q in 0 until queryCount) {
            val base = q * k
            for (t in 0 until trainCount) {
                val d = LandmarkSet.hamming(query, q * LandmarkSet.DESC_BYTES, train, t * LandmarkSet.DESC_BYTES)
                if (d >= dist[base + k - 1]) continue
                // Inserción ordenada en los k mejores.
                var pos = k - 1
                while (pos > 0 && dist[base + pos - 1] > d) {
                    dist[base + pos] = dist[base + pos - 1]
                    index[base + pos] = index[base + pos - 1]
                    pos--
                }
                dist[base + pos] = d
                index[base + pos] = t
            }
        }
        return KnnResult(k, index, dist)
    }
}
