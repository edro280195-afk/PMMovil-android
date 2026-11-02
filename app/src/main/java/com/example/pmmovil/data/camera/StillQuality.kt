package com.example.pmmovil.data.camera

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Plano de luminancia de 8 bits (0-255), fila tras fila. Sin dependencias de Android para poder probarlo en JVM. */
class GrayPlane(val width: Int, val height: Int, val data: ByteArray) {
    init {
        require(width > 0 && height > 0 && data.size == width * height) { "Plano inconsistente: ${width}x$height con ${data.size} bytes" }
    }

    fun at(x: Int, y: Int): Int = data[y * width + x].toInt() and 0xFF

    /**
     * Mismo plano reducido (o ampliado) a [w] x [h] con promedio por AREA EXACTA: cada pixel nuevo es el promedio de todo
     * lo que cubre, contando solo la fraccion que cubre de los pixeles del borde. Es lo que hace INTER_AREA de OpenCV, que
     * es con lo que mide el servidor.
     *
     * Por que no cajas enteras: una primera version promediaba cajas de pixeles completos que se traslapaban, y eso
     * suavizaba de mas. En 10 fotos reales de 12 MP la app medio de 2 a 3 veces MENOS nitidez que OpenCV sobre la misma foto,
     * y reintentaba fotos que estaban bien.
     */
    fun resizedTo(w: Int, h: Int): GrayPlane {
        if (w == width && h == height) return this
        val horizontal = AxisMap.of(width, w)
        val vertical = AxisMap.of(height, h)

        // Primero a lo ancho: w x height, en flotante para no redondear dos veces.
        val wide = FloatArray(w * height)
        for (y in 0 until height) {
            val row = y * width
            for (dx in 0 until w) {
                val first = horizontal.first[dx]
                val weights = horizontal.weights[dx]
                var sum = 0f
                for (k in weights.indices) sum += (data[row + first + k].toInt() and 0xFF) * weights[k]
                wide[y * w + dx] = sum
            }
        }

        val out = ByteArray(w * h)
        for (dy in 0 until h) {
            val first = vertical.first[dy]
            val weights = vertical.weights[dy]
            for (x in 0 until w) {
                var sum = 0f
                for (k in weights.indices) sum += wide[(first + k) * w + x] * weights[k]
                out[dy * w + x] = (sum + 0.5f).toInt().coerceIn(0, 255).toByte()
            }
        }
        return GrayPlane(w, h, out)
    }

    /** Para cada indice de destino: el primer pixel de origen que cubre y cuanto pesa cada uno (suman 1). */
    private class AxisMap(val first: IntArray, val weights: Array<FloatArray>) {
        companion object {
            fun of(source: Int, target: Int): AxisMap {
                val scale = source.toDouble() / target
                val first = IntArray(target)
                val weights = Array(target) { FloatArray(0) }
                for (d in 0 until target) {
                    val start = d * scale
                    val end = min(start + scale, source.toDouble())
                    val i0 = floor(start).toInt().coerceIn(0, source - 1)
                    val i1 = (ceil(end).toInt() - 1).coerceIn(i0, source - 1)
                    val raw = DoubleArray(i1 - i0 + 1) { k ->
                        (min(end, i0 + k + 1.0) - max(start, (i0 + k).toDouble())).coerceAtLeast(1e-9)
                    }
                    val total = raw.sum()
                    first[d] = i0
                    weights[d] = FloatArray(raw.size) { k -> (raw[k] / total).toFloat() }
                }
                return AxisMap(first, weights)
            }
        }
    }

    /** Reduce para que el lado mayor mida [longSide]; si ya es menor no lo toca (nunca se amplia). */
    fun scaledToLongSide(longSide: Int): GrayPlane {
        val current = max(width, height)
        if (current <= longSide) return this
        val ratio = longSide.toDouble() / current
        return resizedTo(max(1, (width * ratio).roundToInt()), max(1, (height * ratio).roundToInt()))
    }

    companion object {
        /** Desde pixeles ARGB_8888 de Android. Luminancia igual a OpenCV BGR2GRAY: 0.299 R + 0.587 G + 0.114 B. */
        fun fromArgb(pixels: IntArray, width: Int, height: Int): GrayPlane {
            val out = ByteArray(width * height)
            for (i in out.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                out[i] = ((299 * r + 587 * g + 114 * b + 500) / 1000).toByte()
            }
            return GrayPlane(width, height, out)
        }
    }
}

/**
 * Cuan nitida es una foto y si dos fotos son de la misma escena. Es el MISMO medidor que usa el servidor
 * (FotoMejoraService, "nitidez"), a proposito: la cifra 200 significa lo mismo en la tableta y en el API.
 *
 * Nitidez = varianza del Laplaciano de la MEJOR zona de una rejilla de 4x4 (percentil 90), sobre la foto en gris
 * reducida a 1280 px de lado mayor, para que no dependa de la resolucion de la camara. Se mide la mejor zona y no
 * el promedio porque una foto con una parte nitida (la etiqueta) no esta movida aunque el fondo sea liso.
 *
 * Limite conocido: una superficie lisa (papel, la cara de una caja) da nitidez baja aunque este bien enfocada. Aqui
 * eso solo cuesta un disparo extra, nunca descarta una foto buena (ver [StillBurstPolicy]).
 */
object StillQuality {
    const val MEASURE_LONG_SIDE = 1280
    private const val THUMBNAIL_LONG_SIDE = 64
    private const val MAX_SHIFT = 6

    fun sharpness(plane: GrayPlane): Double {
        val w = plane.width
        val h = plane.height
        val tileW = w / 4
        val tileH = h / 4
        if (tileW < 2 || tileH < 2) return 0.0

        val data = plane.data
        val sums = LongArray(16)
        val sumsOfSquares = LongArray(16)
        for (y in 0 until 4 * tileH) {
            val up = reflect(y - 1, h) * w
            val down = reflect(y + 1, h) * w
            val row = y * w
            val tileRow = (y / tileH) * 4
            for (x in 0 until 4 * tileW) {
                val left = reflect(x - 1, w)
                val right = reflect(x + 1, w)
                val laplacian = (data[up + x].toInt() and 0xFF) +
                    (data[down + x].toInt() and 0xFF) +
                    (data[row + left].toInt() and 0xFF) +
                    (data[row + right].toInt() and 0xFF) -
                    4 * (data[row + x].toInt() and 0xFF)
                val tile = tileRow + x / tileW
                sums[tile] += laplacian.toLong()
                sumsOfSquares[tile] += laplacian.toLong() * laplacian
            }
        }

        val count = (tileW * tileH).toDouble()
        val variances = DoubleArray(16) { t ->
            val mean = sums[t] / count
            sumsOfSquares[t] / count - mean * mean
        }
        variances.sort()
        val position = 0.9 * (variances.size - 1)
        val low = floor(position).toInt()
        val high = min(low + 1, variances.size - 1)
        return variances[low] + (variances[high] - variances[low]) * (position - low)
    }

    /** Version diminuta (lado mayor 64 px) para comparar escenas. */
    fun thumbnail(plane: GrayPlane): GrayPlane = plane.scaledToLongSide(THUMBNAIL_LONG_SIDE)

    /**
     * Parecido entre dos miniaturas del mismo tamano: la mejor correlacion de Pearson tolerando un desplazamiento de
     * hasta [MAX_SHIFT] px (unos 10 % del encuadre) por el temblor de la mano entre un disparo y otro. Cerca de 1 =
     * misma escena; cerca de 0 = otra cosa (el usuario ya bajo la tableta).
     */
    fun similarity(a: GrayPlane, b: GrayPlane): Double {
        if (a.width != b.width || a.height != b.height) return 0.0
        var best = -1.0
        for (dy in -MAX_SHIFT..MAX_SHIFT) {
            for (dx in -MAX_SHIFT..MAX_SHIFT) {
                best = max(best, correlation(a, b, dx, dy))
            }
        }
        return best.coerceIn(-1.0, 1.0)
    }

    /** Correlacion de a(x, y) con b(x - dx, y - dy) sobre la zona en que ambas existen. */
    private fun correlation(a: GrayPlane, b: GrayPlane, dx: Int, dy: Int): Double {
        val w = a.width
        val h = a.height
        val x0 = max(0, dx)
        val x1 = min(w, w + dx)
        val y0 = max(0, dy)
        val y1 = min(h, h + dy)
        val n = (x1 - x0).toLong() * (y1 - y0)
        if (n < 16) return -1.0

        var sa = 0L
        var sb = 0L
        var saa = 0L
        var sbb = 0L
        var sab = 0L
        for (y in y0 until y1) {
            val rowA = y * w
            val rowB = (y - dy) * w - dx
            for (x in x0 until x1) {
                val va = a.data[rowA + x].toInt() and 0xFF
                val vb = b.data[rowB + x].toInt() and 0xFF
                sa += va
                sb += vb
                saa += va.toLong() * va
                sbb += vb.toLong() * vb
                sab += va.toLong() * vb
            }
        }

        val varA = n * saa - sa * sa
        val varB = n * sbb - sb * sb
        if (varA <= n * n / 4 || varB <= n * n / 4) {
            // Plano casi liso (desviacion < 0.5 niveles): solo "se parece" si ambos son lisos y del mismo tono.
            val bothFlat = varA <= n * n / 4 && varB <= n * n / 4
            return if (bothFlat && kotlin.math.abs(sa.toDouble() / n - sb.toDouble() / n) < 12.0) 1.0 else 0.0
        }
        return (n * sab - sa * sb) / sqrt(varA.toDouble() * varB.toDouble())
    }

    /** Indice con reflejo sin repetir el borde (BORDER_REFLECT_101 de OpenCV), que es el del servidor. */
    private fun reflect(i: Int, n: Int): Int = when {
        i < 0 -> -i
        i >= n -> 2 * n - 2 - i
        else -> i
    }
}
