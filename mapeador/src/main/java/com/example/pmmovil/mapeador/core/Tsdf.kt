package com.example.pmmovil.mapeador.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Malla de vértices intercalados (x, y, z, nx, ny, nz, nacido) y cuadriláteros (4 índices). Los vértices y los
 * cuadriláteros solo se agregan; un cuadrilátero contradicho por cuadros posteriores se MARCA como quitado (su
 * lugar queda, pero ya no se dibuja ni se guarda). Se escribe desde un hilo de trabajo y se lee desde el hilo de
 * GL, que la sube a la GPU por tramos.
 */
class MeshBuffer(val maxVertices: Int = 600_000, initialVertices: Int = 16_384) {
    private var vertices = FloatArray(initialVertices * STRIDE)
    private var quadIndices = IntArray(initialVertices * 4)

    @Volatile
    var vertexCount: Int = 0
        private set

    /** Lugares de cuadrilátero usados (los quitados también cuentan: sus índices no se reutilizan). */
    @Volatile
    var quadCount: Int = 0
        private set

    /** Cuadriláteros que siguen vigentes. */
    @Volatile
    var liveQuadCount: Int = 0
        private set

    /** Sube cada vez que se quita un cuadrilátero: quien dibuja sabe que debe refrescar sus índices. */
    @Volatile
    var removalVersion: Int = 0
        private set

    /**
     * Sube cada vez que un vértice YA CREADO cambia de lugar porque cuadros nuevos afinaron la superficie: quien dibuja
     * sabe que debe volver a subir las posiciones que ya tenía en la GPU (si no, se queda viendo la primera versión).
     */
    @Volatile
    var moveVersion: Int = 0
        private set

    private val dead = java.util.BitSet()

    val isFull: Boolean get() = vertexCount >= maxVertices

    /** Devuelve el índice del vértice, o -1 si ya no caben más. */
    @Synchronized
    fun addVertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, bornSeconds: Float): Int {
        if (vertexCount >= maxVertices) return -1
        val need = (vertexCount + 1) * STRIDE
        if (need > vertices.size) vertices = vertices.copyOf(min(max(need, vertices.size * 2), maxVertices * STRIDE))
        val o = vertexCount * STRIDE
        vertices[o] = x
        vertices[o + 1] = y
        vertices[o + 2] = z
        vertices[o + 3] = nx
        vertices[o + 4] = ny
        vertices[o + 5] = nz
        vertices[o + 6] = bornSeconds
        return vertexCount++
    }

    /**
     * Mueve un vértice ya creado (misma celda, superficie afinada por cuadros nuevos). No toca `bornSeconds`: el brillo de
     * «recién escaneado» no debe reiniciarse porque la posición se corrigió.
     */
    @Synchronized
    fun moveVertex(v: Int, x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
        if (v < 0 || v >= vertexCount) return
        val o = v * STRIDE
        if (vertices[o] == x && vertices[o + 1] == y && vertices[o + 2] == z &&
            vertices[o + 3] == nx && vertices[o + 4] == ny && vertices[o + 5] == nz
        ) {
            return
        }
        vertices[o] = x
        vertices[o + 1] = y
        vertices[o + 2] = z
        vertices[o + 3] = nx
        vertices[o + 4] = ny
        vertices[o + 5] = nz
        moveVersion++
    }

    /** Área (m²) de los cuadriláteros vigentes, sumada al momento: los vértices se mueven, así que no se puede acumular. */
    @Synchronized
    fun liveAreaM2(): Double {
        var sum = 0.0
        for (q in 0 until quadCount) {
            if (dead.get(q)) continue
            val o = q * 4
            sum += quadArea(quadIndices[o], quadIndices[o + 1], quadIndices[o + 2], quadIndices[o + 3])
        }
        return sum
    }

    @Synchronized
    fun addQuad(a: Int, b: Int, c: Int, d: Int) {
        val need = (quadCount + 1) * 4
        if (need > quadIndices.size) quadIndices = quadIndices.copyOf(max(need, quadIndices.size * 2))
        val o = quadCount * 4
        quadIndices[o] = a
        quadIndices[o + 1] = b
        quadIndices[o + 2] = c
        quadIndices[o + 3] = d
        quadCount++
        liveQuadCount++
    }

    /** Quita el cuadrilátero `q` (deja de dibujarse y de guardarse). Devuelve false si ya estaba quitado. */
    @Synchronized
    fun removeQuad(q: Int): Boolean {
        if (q < 0 || q >= quadCount || dead.get(q)) return false
        dead.set(q)
        liveQuadCount--
        removalVersion++
        return true
    }

    @Synchronized
    fun isDead(q: Int): Boolean = dead.get(q)

    /**
     * Superficie de PISO cubierta (m²): celdas de `cellM` con al menos un cuadrilátero vigente que mira hacia arriba (normal y ≥ 0.8)
     * a la altura del piso. El piso se estima como el 8 % más bajo de las superficies que miran hacia arriba (mesas y repisas
     * quedan más arriba) y cuenta de 15 cm abajo a 20 cm arriba. Antes se contaba cualquier celda con malla encima (muros, techo,
     * muebles): marcaba 84 m² en un recorrido de ~41 m² de piso.
     */
    @Synchronized
    fun floorCoverageM2(cellM: Double): Double {
        val hist = IntArray(FLOOR_BINS)
        var up = 0
        for (q in 0 until quadCount) {
            if (dead.get(q)) continue
            val o = quadIndices[q * 4] * STRIDE
            if (vertices[o + 4] < FLOOR_MIN_NY) continue
            hist[((vertices[o + 1] + FLOOR_RANGE_M) / FLOOR_BIN_M).toInt().coerceIn(0, FLOOR_BINS - 1)]++
            up++
        }
        if (up < 30) return 0.0
        val target = max(1, (up * 0.08).toInt())
        var acc = 0
        var floorBin = 0
        for (b in 0 until FLOOR_BINS) {
            acc += hist[b]
            if (acc >= target) {
                floorBin = b
                break
            }
        }
        val floorY = floorBin * FLOOR_BIN_M - FLOOR_RANGE_M
        val cells = HashSet<Long>()
        for (q in 0 until quadCount) {
            if (dead.get(q)) continue
            val o = quadIndices[q * 4] * STRIDE
            if (vertices[o + 4] < FLOOR_MIN_NY) continue
            val y = vertices[o + 1]
            if (y < floorY - FLOOR_BELOW_M || y > floorY + FLOOR_ABOVE_M) continue
            val cx = floor(vertices[o] / cellM).toLong() and 0xFFFFFFL
            val cz = floor(vertices[o + 2] / cellM).toLong() and 0xFFFFFFL
            cells.add((cx shl 24) or cz)
        }
        return cells.size * cellM * cellM
    }

    /** Copia solo lo vigente: los cuadriláteros que no se quitaron y los vértices que usan (con índices renumerados). */
    @Synchronized
    fun compacted(): MeshBuffer {
        val used = BooleanArray(vertexCount)
        for (q in 0 until quadCount) {
            if (dead.get(q)) continue
            for (k in 0 until 4) used[quadIndices[q * 4 + k]] = true
        }
        val liveVertices = used.count { it }
        val out = MeshBuffer(maxVertices = max(liveVertices, 1), initialVertices = max(liveVertices, 1))
        val map = IntArray(vertexCount) { -1 }
        for (v in 0 until vertexCount) {
            if (!used[v]) continue
            val o = v * STRIDE
            map[v] = out.addVertex(vertices[o], vertices[o + 1], vertices[o + 2], vertices[o + 3], vertices[o + 4], vertices[o + 5], vertices[o + 6])
        }
        for (q in 0 until quadCount) {
            if (dead.get(q)) continue
            out.addQuad(map[quadIndices[q * 4]], map[quadIndices[q * 4 + 1]], map[quadIndices[q * 4 + 2]], map[quadIndices[q * 4 + 3]])
        }
        return out
    }

    /** Copia los vértices [from, to) (intercalados) a `out`. */
    @Synchronized
    fun copyVertices(from: Int, to: Int, out: FloatArray, outOffset: Int = 0) {
        System.arraycopy(vertices, from * STRIDE, out, outOffset, (to - from) * STRIDE)
    }

    /** Copia los cuadriláteros [from, to) (4 índices cada uno) a `out`; los quitados salen como ceros (no dibujan nada). */
    @Synchronized
    fun copyQuads(from: Int, to: Int, out: IntArray, outOffset: Int = 0) {
        System.arraycopy(quadIndices, from * 4, out, outOffset, (to - from) * 4)
        var q = dead.nextSetBit(from)
        while (q in 0 until to) {
            val o = outOffset + (q - from) * 4
            out[o] = 0
            out[o + 1] = 0
            out[o + 2] = 0
            out[o + 3] = 0
            q = dead.nextSetBit(q + 1)
        }
    }

    @Synchronized
    fun position(v: Int) = Vec3(vertices[v * STRIDE].toDouble(), vertices[v * STRIDE + 1].toDouble(), vertices[v * STRIDE + 2].toDouble())

    @Synchronized
    fun normal(v: Int) = Vec3(vertices[v * STRIDE + 3].toDouble(), vertices[v * STRIDE + 4].toDouble(), vertices[v * STRIDE + 5].toDouble())

    @Synchronized
    fun quad(q: Int) = intArrayOf(quadIndices[q * 4], quadIndices[q * 4 + 1], quadIndices[q * 4 + 2], quadIndices[q * 4 + 3])

    /** Área (m²) del cuadrilátero de vértices a, b, c, d como dos triángulos (a, b, c) y (a, c, d). */
    @Synchronized
    fun quadArea(a: Int, b: Int, c: Int, d: Int): Double {
        val v = vertices
        val ao = a * STRIDE
        val bo = b * STRIDE
        val co = c * STRIDE
        val d0 = d * STRIDE
        val abx = (v[bo] - v[ao]).toDouble()
        val aby = (v[bo + 1] - v[ao + 1]).toDouble()
        val abz = (v[bo + 2] - v[ao + 2]).toDouble()
        val acx = (v[co] - v[ao]).toDouble()
        val acy = (v[co + 1] - v[ao + 1]).toDouble()
        val acz = (v[co + 2] - v[ao + 2]).toDouble()
        val adx = (v[d0] - v[ao]).toDouble()
        val ady = (v[d0 + 1] - v[ao + 1]).toDouble()
        val adz = (v[d0 + 2] - v[ao + 2]).toDouble()
        val t1 = sqrt(square(aby * acz - abz * acy) + square(abz * acx - abx * acz) + square(abx * acy - aby * acx))
        val t2 = sqrt(square(acy * adz - acz * ady) + square(acz * adx - acx * adz) + square(acx * ady - acy * adx))
        return 0.5 * (t1 + t2)
    }

    private fun square(x: Double) = x * x

    companion object {
        const val FLOOR_MIN_NY = 0.8f
        const val FLOOR_BIN_M = 0.05f
        const val FLOOR_RANGE_M = 10f
        const val FLOOR_BINS = 400
        const val FLOOR_BELOW_M = 0.15f
        const val FLOOR_ABOVE_M = 0.20f
        const val STRIDE = 7
    }
}

/** Guardado de la malla: posiciones en float, normales a un byte por eje y los cuadriláteros. */
object MeshIO {
    private const val MAGIC = 0x484D4D50 // "PMMH" en little-endian
    private const val VERSION = 1

    fun write(file: File, source: MeshBuffer, voxelM: Float) {
        // Solo lo vigente: ni los cuadriláteros quitados ni los vértices que quedaron sin uso.
        val mesh = source.compacted()
        val n = mesh.vertexCount
        val q = mesh.quadCount
        val all = FloatArray(n * MeshBuffer.STRIDE)
        mesh.copyVertices(0, n, all)
        val quads = IntArray(q * 4)
        mesh.copyQuads(0, q, quads)
        val buf = ByteBuffer.allocate(20 + n * 12 + n * 3 + q * 16).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC).putInt(VERSION).putInt(n).putInt(q).putFloat(voxelM)
        for (i in 0 until n) {
            buf.putFloat(all[i * MeshBuffer.STRIDE]).putFloat(all[i * MeshBuffer.STRIDE + 1]).putFloat(all[i * MeshBuffer.STRIDE + 2])
        }
        for (i in 0 until n) {
            for (k in 3..5) buf.put(Math.round(all[i * MeshBuffer.STRIDE + k].coerceIn(-1f, 1f) * 127f).toByte())
        }
        for (v in quads) buf.putInt(v)
        file.writeBytes(buf.array())
    }

    /** Los vértices cargados llevan un «nacido» muy antiguo para que no brillen como recién escaneados. */
    fun read(file: File): MeshBuffer {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt() == MAGIC) { "El archivo de malla no es de este programa." }
        val version = buf.getInt()
        require(version == VERSION) { "Versión de malla no soportada ($version)." }
        val n = buf.getInt()
        val q = buf.getInt()
        buf.getFloat() // tamaño de voxel: informativo
        val pos = FloatArray(n * 3) { buf.getFloat() }
        val mesh = MeshBuffer(maxVertices = max(n, 1), initialVertices = max(n, 1))
        for (i in 0 until n) {
            var nx = buf.get() / 127f
            var ny = buf.get() / 127f
            var nz = buf.get() / 127f
            val len = sqrt(nx * nx + ny * ny + nz * nz)
            if (len > 1e-6f) {
                nx /= len
                ny /= len
                nz /= len
            }
            mesh.addVertex(pos[i * 3], pos[i * 3 + 1], pos[i * 3 + 2], nx, ny, nz, -1000f)
        }
        for (i in 0 until q) mesh.addQuad(buf.getInt(), buf.getInt(), buf.getInt(), buf.getInt())
        return mesh
    }
}


class TsdfConfig(
    /**
     * 10 cm: con la profundidad de ARCore (±4–10 % de error) un voxel más fino solo dibuja el ruido. Medido contra mediciones
     * que la malla no vio, 10 cm es igual de exacto que 8 (bien 52 % contra 53 %; 60 % contra 59 % en la grabación de Eduardo),
     * con un tercio menos de cuadriláteros y láminas más conectadas (la mayor junta el 67 % de los cuadriláteros, contra 37 %).
     */
    val voxelM: Double = 0.10,
    /** Ancho de la banda a cada lado de la superficie en la que se guarda la distancia con signo (2 voxeles). */
    val truncM: Double = 0.20,
    /** Con los rayos normalizados un cuadro aporta ~1 de peso: 10 = el promedio mira los últimos ~10–40 cuadros. */
    val maxWeight: Float = 10f,
    /** Un voxel cuenta para la malla con al menos este peso y visto en al menos estos cuadros distintos. */
    val minWeight: Float = 0.1f,
    val minFrames: Int = 2,
    /** Más cerca de esto casi siempre es la mano o el cuerpo de quien escanea (y la profundidad suave inventa ahí). */
    val minDepthM: Double = 0.40,
    /** Hasta dónde se integra: los muros y ventanas del fondo de una oficina o nave están a 4–6 m (con poco peso: más ruido). */
    val maxDepthM: Double = 6.0,
    /** Confianza mínima (1..250) con la que cuenta un píxel que no es relleno; el umbral de la cruda ya lo puso `DepthMerge`. */
    val minConfidence: Int = 1,
    /** Cada cuántos píxeles de profundidad se lanza un rayo: con 2, el costo baja ~4× y la cobertura casi no cambia. */
    val stride: Int = 2,
    /** Una arista entre voxeles vecinos con una diferencia de distancia mayor que esto no es una superficie creíble. */
    val maxEdgeJump: Float = 1.2f,
    /** Tope de bloques de 8×8×8 voxeles (cada uno pesa unos 4 KB): 16,000 bloques son ~65 MB. */
    val maxBlocks: Int = 16_000,
    /** Peso relativo de la profundidad de relleno (la suave) frente a la cruda confiable, que es más consistente. */
    val fillWeight: Float = 0.35f,
    /** Peso de la profundidad cruda de confianza alta (la que ARCore mide de verdad, ±2–4 cm). */
    val rawWeight: Float = 3f,
    /**
     * La cruda de confianza baja pesa menos: va de `rawLowWeight` (confianza 1) a `rawWeight` (confianza ≥ `rawFullConfidence`),
     * en proporción al error medido (mediana 2.6 % con confianza ≥ 150 contra 5–7 % con menos de 90).
     */
    val rawLowWeight: Float = 0.2f,
    val rawFullConfidence: Int = 150,
    /**
     * Rango en el que se cree el relleno (la suave): de cerca se pasa de largo (+14…+45 % lejos de todo dato crudo) y de lejos
     * se queda corto (−3…−8 %). Fuera de este rango el relleno no cuenta (la cruda sí, hasta `maxDepthM`).
     */
    val fillMinDepthM: Double = 0.0,
    val fillMaxDepthM: Double = 99.0,
    /**
     * La profundidad suave de ARCore solo es confiable cerca de un píxel de cruda confiable (el modelo la ancla ahí): en el
     * Pixel, a ≤ 3 px de uno el error pasa de 10 % en ~10–25 % de los píxeles; a más de 8 px pasa en 50–70 %, con sesgo de
     * +10…+45 % de cerca y −3…−8 % de lejos. Su peso decae como exp(−d²/2σ²) con la distancia d (en píxeles de la imagen de
     * profundidad) al dato crudo más cercano de ese mismo cuadro. 0 = sin decaimiento.
     */
    val anchorSigmaPx: Float = 10f,
    /**
     * Peso mínimo (como fracción) que conserva el relleno lejos de todo dato crudo; 0 = lejos del dato crudo no pesa nada.
     * Con el piso en 0.15 un muro liso sin dato crudo igual se levanta, pero con 7× menos autoridad: tarda más cuadros en
     * aparecer y un solo cuadro engañado no lo sostiene. (Medido con cuadros de pose buena, anclar da +2–4 puntos de precisión y
     * cuesta 3–8 de cobertura; la verdad solo existe donde ARCore midió, así que no mide justo lo que más importa del anclaje.)
     */
    val anchorFloor: Float = 0.15f,
    /** Un rayo cuyo peso queda abajo de esto no se integra (ahorra tiempo en el relleno que no se cree). */
    val minPixelWeight: Float = 0.004f,
    /**
     * Votos de espacio libre: cada cuadro revisa los voxeles de superficie que caen en su campo de visión; si ve MÁS
     * LEJOS que un voxel (más que la banda y que esta holgura), ese voxel no puede ser superficie y recibe un voto de
     * «libre» con este peso relativo al de una observación. Así se borran las superficies fantasma de la profundidad
     * suave (y lo que ya se movió), que de otro modo se quedan flotando para siempre.
     */
    val freeVoteWeight: Float = 0.5f,
    val minFreeGapM: Double = 0.20,
    /** Cada cuántos cuadros se revisa un voxel de superficie (1 = en todos). */
    val carveEvery: Int = 1,
    /**
     * Cada voxel recibe varios rayos por cuadro (muchos si está cerca). Con true, el peso de cada rayo se divide entre
     * cuántos cruzan el voxel, así que UN cuadro aporta ~1 de peso sea cual sea la distancia: el tope de peso significa
     * «los últimos N cuadros» y la superficie deja de seguir el ruido de los últimos 6 cuadros.
     */
    val normalizeByRays: Boolean = true,
    /**
     * Histéresis al quitar: un cuadrilátero se quita cuando su arista deja de cruzar la superficie esta cantidad de veces
     * seguidas (1 = a la primera). Evita el parpadeo cuando el cruce salta una arista de ida y vuelta por el ruido.
     */
    val removeAfterMisses: Int = 2,
    /**
     * Vuelve a calcular el vértice de una celda cuando cuadros nuevos cambian su distancia con signo. Sin esto, el vértice
     * se fija con los PRIMEROS dos cuadros que vieron el lugar (`minFrames`) y ya no se mueve nunca: el volumen se afina
     * (promedia los últimos ~10–40 cuadros) pero la malla que se dibuja sigue siendo la del primer reconocimiento, que es
     * la peor (de lejos y de reojo). Dentro de una misma celda eso deja el error clavado: medido en `TsdfRefinementTest`,
     * 86 % de la malla se quedaba en el lugar equivocado después de 24 cuadros buenos.
     */
    val refreshVertices: Boolean = true,
)

/**
 * Reconstrucción de superficies como en un escáner: cada imagen de profundidad actualiza una distancia con signo
 * (positiva delante de la superficie, negativa detrás) en los voxeles que cruza cada rayo, y esa distancia se PROMEDIA
 * entre cuadros: el ruido de la profundidad se cancela en vez de dejar varias capas del mismo muro.
 *
 * Los voxeles viven en bloques densos de 8×8×8 (un mapa de hash solo para los bloques): ~7 bytes por voxel en vez de
 * ~50, y los voxeles contiguos de un rayo caen casi siempre en el mismo bloque, sin búsquedas.
 *
 * La superficie se saca con «surface nets»: un vértice por cada celda donde cambia el signo (en el promedio de los
 * cruces de sus aristas) y un cuadrilátero por cada arista que cruza la superficie. Se emite de forma incremental, solo
 * cuando los voxeles ya fueron vistos en varios cuadros, y los vértices ya emitidos no se mueven.
 * Es Kotlin puro para poder probarlo en la JVM.
 */
class TsdfMesher(
    val mesh: MeshBuffer = MeshBuffer(),
    val config: TsdfConfig = TsdfConfig()
) {
    /**
     * 512 voxeles. `sdf` va normalizada a la banda (−1..1) × 32767 y `weight` × 128. `nx/ny/nz` es el promedio ponderado
     * de las normales que sacó cada imagen de profundidad de sus píxeles vecinos (hacia la cámara, en el mundo): la
     * profundidad suave de ARCore se equivoca en la altura pero de forma suave en el espacio, así que sus normales
     * son mucho más confiables que el gradiente de la distancia con signo.
     */
    private class Block(val bx: Int, val by: Int, val bz: Int) {
        val sdf = ShortArray(BLOCK_VOXELS)
        val weight = ShortArray(BLOCK_VOXELS)
        val frames = ByteArray(BLOCK_VOXELS)
        val last = ShortArray(BLOCK_VOXELS)
        val nx = ShortArray(BLOCK_VOXELS)
        val ny = ShortArray(BLOCK_VOXELS)
        val nz = ShortArray(BLOCK_VOXELS)

        /** Bit de cada eje: la arista que sale de este voxel hacia +x, +y o +z ya emitió su cuadrilátero. */
        val edges = ByteArray(BLOCK_VOXELS)
    }

    private val blocks = ArrayList<Block>()
    private val blockIndex = LongIntMap(1 shl 10)
    private var cacheBx = Int.MIN_VALUE
    private var cacheBy = 0
    private var cacheBz = 0
    private var cacheIdx = -1

    private var voxels = 0
    private val minWeightFixed = (config.minWeight * WEIGHT_SCALE).toInt()
    private val nearSurface = (config.voxelM / config.truncM * 1.15).toFloat()

    private val cellVertex = LongIntMap(1 shl 14)

    /** Último cuadro en que se recalculó el vértice de cada celda: así una celda se refresca una sola vez por cuadro. */
    private val cellRefreshed = LongIntMap(1 shl 14)
    private val refreshVerts = IntArray(4)
    private var frameStamp = 0
    private var touched = IntArray(1 shl 14)
    private var touchedCount = 0

    /** Distancia (px) de cada píxel del cuadro actual al dato crudo confiable más cercano; solo con `anchorSigmaPx` > 0. */
    private var anchorDist = FloatArray(0)

    /**
     * Apaga el anclaje (el relleno pesa lo mismo en todas partes, como en la v7): para comparar y para el modo «suave» pura,
     * que por definición no tiene dato crudo al que anclarse.
     */
    @Volatile
    var anchoringEnabled: Boolean = true
    private val anchoring: Boolean get() = config.anchorSigmaPx > 0f && anchoringEnabled
    private var areaCache = 0.0
    private var areaCacheNs = 0L
    private var groundCache = 0.0
    private var groundCacheNs = 0L
    private val edgeQuad = LongIntMap(1 shl 14)

    /** Cuadriláteros quitados hasta ahora, y cuánto tardó (ns) el último voto de espacio libre. */
    @Volatile
    var removedQuads: Int = 0
        private set

    @Volatile
    var lastCarveNs: Long = 0
        private set

    @Volatile
    var lastFreeVotes: Int = 0
        private set

    /** Cuánto tardó (ns) la última integración de profundidad y la última extracción de superficie. */
    @Volatile
    var lastIntegrateNs: Long = 0
        private set

    @Volatile
    var lastEmitNs: Long = 0
        private set

    /** Voxeles que algún cuadro llegó a tocar. */
    val voxelCount: Int get() = voxels

    val blockCount: Int get() = blocks.size

    /** Memoria aproximada de los voxeles, en bytes. */
    val voxelBytes: Long get() = blocks.size.toLong() * BLOCK_BYTES

    /** Ya no caben bloques nuevos: lo que se vea de aquí en adelante no se reconstruye. */
    val isFull: Boolean get() = blocks.size >= config.maxBlocks

    /**
     * Área de la malla (m²): la suma de sus cuadriláteros vigentes. Con profundidad ruidosa se infla (varias capas del
     * mismo muro). Se recalcula (en caché 500 ms) porque los vértices se mueven cuando la superficie se afina.
     */
    val areaM2: Double
        get() {
            val now = System.nanoTime()
            if (areaCacheNs == 0L || now - areaCacheNs > 500_000_000L) {
                areaCache = mesh.liveAreaM2()
                areaCacheNs = now
            }
            return areaCache
        }

    /** Piso cubierto (m²): celdas de 25 cm a la altura del piso con malla que mira hacia arriba (ver `MeshBuffer.floorCoverageM2`). Es el número que se le muestra a la persona. */
    val groundAreaM2: Double
        get() {
            val now = System.nanoTime()
            if (groundCacheNs == 0L || now - groundCacheNs > 500_000_000L) {
                groundCache = mesh.floorCoverageM2(GROUND_CELL_M)
                groundCacheNs = now
            }
            return groundCache
        }

    /** Distancia con signo normalizada (−1..1) y peso del voxel que contiene el punto del mundo; null si no existe. Para pruebas y diagnóstico. */
    @Synchronized
    fun probe(x: Double, y: Double, z: Double): FloatArray? {
        val v = config.voxelM
        val ix = floor(x / v).toInt()
        val iy = floor(y / v).toInt()
        val iz = floor(z / v).toInt()
        val slot = slotAt(ix, iy, iz)
        if (slot < 0) return null
        val b = blocks[slot ushr 9]
        val l = slot and 511
        return floatArrayOf(b.sdf[l] / SDF_SCALE, b.weight[l] / WEIGHT_SCALE, (b.frames[l].toInt() and 0xFF).toFloat())
    }

    /** Integra una imagen de profundidad y devuelve cuántos cuadriláteros nuevos salieron. */
    @Synchronized
    fun integrate(frame: DepthFrame, bornSeconds: Float): Int {
        frameStamp = if (frameStamp >= 0xFFFF) 1 else frameStamp + 1
        touchedCount = 0
        val t0 = System.nanoTime()
        if (anchoring) computeAnchorDistance(frame)
        integrateDepth(frame)
        val t1 = System.nanoTime()
        carveFreeSpace(frame)
        val t2 = System.nanoTime()
        val quads = emitSurface(bornSeconds)
        lastIntegrateNs = t1 - t0
        lastCarveNs = t2 - t1
        lastEmitNs = System.nanoTime() - t2
        return quads
    }

    // ---------- bloques ----------

    private fun blockKey(bx: Int, by: Int, bz: Int): Long =
        ((bx.toLong() and MASK21) shl 42) or ((by.toLong() and MASK21) shl 21) or (bz.toLong() and MASK21)

    /** El índice del bloque o −1 si todavía no existe. */
    private fun blockOrNone(bx: Int, by: Int, bz: Int): Int {
        if (bx == cacheBx && by == cacheBy && bz == cacheBz) return cacheIdx
        val idx = blockIndex.get(blockKey(bx, by, bz))
        if (idx >= 0) {
            cacheBx = bx
            cacheBy = by
            cacheBz = bz
            cacheIdx = idx
        }
        return idx
    }

    /** El índice del bloque, creándolo si hace falta; −1 si ya no caben más. */
    private fun blockOrCreate(bx: Int, by: Int, bz: Int): Int {
        val existing = blockOrNone(bx, by, bz)
        if (existing >= 0) return existing
        if (blocks.size >= config.maxBlocks) return -1
        blocks.add(Block(bx, by, bz))
        val idx = blocks.size - 1
        blockIndex.put(blockKey(bx, by, bz), idx)
        cacheBx = bx
        cacheBy = by
        cacheBz = bz
        cacheIdx = idx
        return idx
    }

    private fun localOf(ix: Int, iy: Int, iz: Int): Int = (ix and 7) or ((iy and 7) shl 3) or ((iz and 7) shl 6)

    /** El «slot» (bloque·512 + posición dentro del bloque) del voxel, o −1 si su bloque no existe. */
    private fun slotAt(ix: Int, iy: Int, iz: Int): Int {
        val b = blockOrNone(ix shr 3, iy shr 3, iz shr 3)
        return if (b < 0) -1 else (b shl 9) or localOf(ix, iy, iz)
    }

    private fun sdfOf(slot: Int): Float = blocks[slot ushr 9].sdf[slot and 511] / SDF_SCALE

    private fun valid(slot: Int): Boolean {
        if (slot < 0) return false
        val b = blocks[slot ushr 9]
        val l = slot and 511
        return b.weight[l] >= minWeightFixed && (b.frames[l].toInt() and 0xFF) >= config.minFrames
    }

    // ---------- integración de la profundidad ----------

    private fun integrateDepth(f: DepthFrame) {
        val k = f.intrinsics
        val cam = CamPose(f.worldFromCamera, k)
        val r = cam.worldFromCv.r
        val c = cam.worldFromCv.t
        val voxel = config.voxelM
        val trunc = config.truncM
        val st = config.stride

        var bv = 0
        while (bv < f.height) {
            var bu = 0
            while (bu < f.width) {
                // Con paso > 1 cada rayo representa un bloque de píxeles: si el bloque trae uno de cruda confiable, se usa ese.
                var u = bu
                var v = bv
                if (st > 1) {
                    val alt = rawPixelInBlock(f, bu, bv, st)
                    if (alt >= 0) {
                        u = alt % f.width
                        v = alt / f.width
                    }
                }
                val depth = depthAt(f, u, v)
                if (depth > 0.0) {
                    val rx = ((u + 0.5) - k.cx) / k.fx
                    val ry = ((v + 0.5) - k.cy) / k.fy
                    val rayLen = sqrt(rx * rx + ry * ry + 1.0)

                    // Coseno entre el rayo y la normal de la superficie (de los vecinos de la propia profundidad):
                    // la distancia con signo se mide AL PLANO, no a lo largo del rayo; si no, los rayos oblicuos
                    // inclinan el gradiente y las normales salen torcidas en los bordes.
                    var cosTheta = 1.0 / rayLen // sin vecinos se supone una superficie de frente a la cámara
                    // Normal del píxel en el mundo, hacia la cámara; sin vecinos válidos no se acumula normal (el rayo al
                    // revés sería una mala estimación en los bordes de la imagen).
                    var wnx = 0f
                    var wny = 0f
                    var wnz = 0f
                    var haveNormal = false
                    val depthRight = if (u + st < f.width) depthAt(f, u + st, v) else 0.0
                    val depthDown = if (v + st < f.height) depthAt(f, u, v + st) else 0.0
                    if (depthRight > 0.0 && depthDown > 0.0) {
                        val jump = EDGE_JUMP_ABS_M + EDGE_JUMP_REL * depth
                        // Borde de profundidad: esos píxeles mezclan fondo y objeto; se descartan.
                        if (abs(depthRight - depth) > jump || abs(depthDown - depth) > jump) {
                            bu += st
                            continue
                        }
                        val x0 = rx * depth
                        val y0 = ry * depth
                        val ax = ((u + st + 0.5) - k.cx) / k.fx * depthRight - x0
                        val ay = ((v + 0.5) - k.cy) / k.fy * depthRight - y0
                        val az = depthRight - depth
                        val bx = ((u + 0.5) - k.cx) / k.fx * depthDown - x0
                        val by = ((v + st + 0.5) - k.cy) / k.fy * depthDown - y0
                        val bz = depthDown - depth
                        val nx = ay * bz - az * by
                        val ny = az * bx - ax * bz
                        val nz = ax * by - ay * bx
                        val nl = sqrt(nx * nx + ny * ny + nz * nz)
                        if (nl > 1e-12) {
                            cosTheta = max(abs(nx * x0 + ny * y0 + nz * depth) / (nl * depth * rayLen), MIN_COS)
                            // Orientada hacia la cámara (la que ve el punto (x0, y0, depth) desde el origen).
                            val sign = if (nx * x0 + ny * y0 + nz * depth > 0.0) -1.0 / nl else 1.0 / nl
                            val cx = nx * sign
                            val cy = ny * sign
                            val cz = nz * sign
                            wnx = (r[0] * cx + r[1] * cy + r[2] * cz).toFloat()
                            wny = (r[3] * cx + r[4] * cy + r[5] * cz).toFloat()
                            wnz = (r[6] * cx + r[7] * cy + r[8] * cz).toFloat()
                            haveNormal = true
                        }
                    }
                    cosTheta = max(cosTheta, MIN_COS)

                    var obsWeight = observationWeight(f, u, v, depth) * cosTheta.toFloat()
                    if (obsWeight < config.minPixelWeight) {
                        bu += st
                        continue
                    }
                    if (config.normalizeByRays) {
                        val voxelPx = voxel * max(k.fx, k.fy) / depth / st
                        obsWeight /= max(1.0, voxelPx * voxelPx).toFloat()
                    }
                    // Dirección del rayo en el mundo con z de cámara = 1 (así «t» es profundidad).
                    val dx = r[0] * rx + r[1] * ry + r[2]
                    val dy = r[3] * rx + r[4] * ry + r[5]
                    val dz = r[6] * rx + r[7] * ry + r[8]
                    val scale = rayLen * cosTheta // de «profundidad a lo largo del rayo» a distancia al plano
                    val band = trunc / scale
                    val t0 = max(config.minDepthM * 0.5, depth - band)
                    val t1 = depth + band
                    val step = voxel / 2.0 / rayLen

                    var cx = Int.MIN_VALUE
                    var cy = 0
                    var cz = 0
                    var sum = 0.0
                    var n = 0
                    var t = t0
                    while (t <= t1) {
                        val ix = floor((c.x + dx * t) / voxel).toInt()
                        val iy = floor((c.y + dy * t) / voxel).toInt()
                        val iz = floor((c.z + dz * t) / voxel).toInt()
                        if (ix != cx || iy != cy || iz != cz) {
                            if (n > 0) update(cx, cy, cz, sum / n, obsWeight, wnx, wny, wnz, haveNormal)
                            cx = ix
                            cy = iy
                            cz = iz
                            sum = 0.0
                            n = 0
                        }
                        sum += ((depth - t) * scale / trunc).coerceIn(-1.0, 1.0)
                        n++
                        t += step
                    }
                    if (n > 0) update(cx, cy, cz, sum / n, obsWeight, wnx, wny, wnz, haveNormal)
                }
                bu += st
            }
            bv += st
        }
    }

    /** Índice del primer píxel de cruda confiable del bloque de `st`×`st` que empieza en (bu, bv), o −1 si no hay. */
    private fun rawPixelInBlock(f: DepthFrame, bu: Int, bv: Int, st: Int): Int {
        val conf = f.confidence ?: return -1
        var best = -1
        var bestConf = -1
        for (dv in 0 until st) {
            val y = bv + dv
            if (y >= f.height) break
            for (du in 0 until st) {
                val x = bu + du
                if (x >= f.width) break
                val i = y * f.width + x
                if (f.depthMm[i].toInt() == 0) continue
                val c = conf[i].toInt() and 0xFF
                if (c == DepthMerge.FILL_CONFIDENCE || c < config.minConfidence || c <= bestConf) continue
                if (depthAt(f, x, y) <= 0.0) continue
                best = i
                bestConf = c
            }
        }
        return best
    }

    /** Distancia (px) de cada píxel al de profundidad cruda confiable más cercano de este cuadro (chaflán 1–√2, dos pasadas). */
    private fun computeAnchorDistance(f: DepthFrame) {
        val w = f.width
        val h = f.height
        val n = w * h
        if (anchorDist.size != n) anchorDist = FloatArray(n)
        val d = anchorDist
        val conf = f.confidence
        for (i in 0 until n) {
            val raw = f.depthMm[i].toInt() != 0 &&
                (conf == null || ((conf[i].toInt() and 0xFF).let { it != DepthMerge.FILL_CONFIDENCE && it >= config.minConfidence }))
            d[i] = if (raw) 0f else FAR_PX
        }
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var best = d[row + x]
                if (x > 0) best = min(best, d[row + x - 1] + 1f)
                if (y > 0) {
                    val up = row - w
                    best = min(best, d[up + x] + 1f)
                    if (x > 0) best = min(best, d[up + x - 1] + DIAGONAL)
                    if (x < w - 1) best = min(best, d[up + x + 1] + DIAGONAL)
                }
                d[row + x] = best
            }
        }
        for (y in h - 1 downTo 0) {
            val row = y * w
            for (x in w - 1 downTo 0) {
                var best = d[row + x]
                if (x < w - 1) best = min(best, d[row + x + 1] + 1f)
                if (y < h - 1) {
                    val dn = row + w
                    best = min(best, d[dn + x] + 1f)
                    if (x < w - 1) best = min(best, d[dn + x + 1] + DIAGONAL)
                    if (x > 0) best = min(best, d[dn + x - 1] + DIAGONAL)
                }
                d[row + x] = best
            }
        }
    }

    /** Suma una observación (distancia con signo ya promediada a lo largo del rayo dentro del voxel). */
    private fun update(
        ix: Int, iy: Int, iz: Int, observed: Double, obsWeight: Float, wnx: Float, wny: Float, wnz: Float, haveNormal: Boolean
    ) {
        val bi = blockOrCreate(ix shr 3, iy shr 3, iz shr 3)
        if (bi < 0) return
        val b = blocks[bi]
        val l = localOf(ix, iy, iz)
        val w = b.weight[l] / WEIGHT_SCALE
        val s = b.sdf[l] / SDF_SCALE
        val inv = 1f / (w + obsWeight)
        val merged = ((s * w + observed.toFloat() * obsWeight) * inv).coerceIn(-1f, 1f)
        b.sdf[l] = Math.round(merged * SDF_SCALE).toShort()
        if (haveNormal) {
            b.nx[l] = Math.round(((b.nx[l] / SDF_SCALE * w + wnx * obsWeight) * inv).coerceIn(-1f, 1f) * SDF_SCALE).toShort()
            b.ny[l] = Math.round(((b.ny[l] / SDF_SCALE * w + wny * obsWeight) * inv).coerceIn(-1f, 1f) * SDF_SCALE).toShort()
            b.nz[l] = Math.round(((b.nz[l] / SDF_SCALE * w + wnz * obsWeight) * inv).coerceIn(-1f, 1f) * SDF_SCALE).toShort()
        }
        b.weight[l] = Math.round(min(w + obsWeight, config.maxWeight) * WEIGHT_SCALE).toShort()
        if ((b.last[l].toInt() and 0xFFFF) != frameStamp) {
            b.last[l] = frameStamp.toShort()
            val seen = b.frames[l].toInt() and 0xFF
            if (seen == 0) voxels++
            if (seen < 255) b.frames[l] = (seen + 1).toByte()
            if (touchedCount == touched.size) touched = touched.copyOf(touched.size * 2)
            touched[touchedCount++] = (bi shl 9) or l
        }
        if (abs(merged) <= nearSurface && b.weight[l] >= minWeightFixed / 2) markSurface(b, l)
    }

    /** Marca que el voxel estuvo cerca de la superficie: puede ser dueño de aristas emitidas (hay que revisarlo al quitar). */
    private fun markSurface(b: Block, l: Int) {
        if ((b.edges[l].toInt() and IN_SURFACE) == 0) b.edges[l] = (b.edges[l].toInt() or IN_SURFACE).toByte()
    }

    /**
     * Votos de espacio libre (como en KinectFusion, pero solo sobre los voxeles ya reservados): para cada voxel observado
     * que cae en el campo de visión de este cuadro, si TODOS los píxeles de su vecindario de 3×3 ven más lejos que el
     * voxel (más que la banda y la holgura mínima), la cámara está viendo A TRAVÉS de él: ahí no hay superficie. Recibe un
     * voto de «libre» (+1) con el peso de una observación a esa distancia. Los votos se promedian con las observaciones de
     * superficie: una superficie real que unos cuantos cuadros ven mal sobrevive, y un fantasma (de la profundidad
     * suave, o de algo que ya se movió) que casi todos ven atravesado se desvanece, capa de atrás incluida.
     */
    private fun carveFreeSpace(f: DepthFrame) {
        val k = f.intrinsics
        val inv = CamPose(f.worldFromCamera, k).cvFromWorld
        val r = inv.r
        val t = inv.t
        val voxel = config.voxelM
        val baseMargin = max(config.truncM, config.minFreeGapM)
        val every = max(1, config.carveEvery)
        val blockRadius = BLOCK_HALF_DIAGONAL * voxel
        var votes = 0
        for (bi in 0 until blocks.size) {
            if (every > 1 && (bi + frameStamp) % every != 0) continue
            val b = blocks[bi]
            // Centro del bloque en el cuerpo de la cámara, para descartar los que quedan fuera del campo de visión.
            val bx = ((b.bx shl 3) + 4) * voxel
            val by = ((b.by shl 3) + 4) * voxel
            val bz = ((b.bz shl 3) + 4) * voxel
            val bcz = r[6] * bx + r[7] * by + r[8] * bz + t.z
            if (bcz + blockRadius < config.minDepthM || bcz - blockRadius > config.maxDepthM) continue
            val bcx = r[0] * bx + r[1] * by + r[2] * bz + t.x
            val bcy = r[3] * bx + r[4] * by + r[5] * bz + t.y
            val zSafe = max(bcz, blockRadius)
            val margPx = blockRadius * max(k.fx, k.fy) / zSafe + 2.0
            val bu = k.fx * bcx / zSafe + k.cx
            val bv = k.fy * bcy / zSafe + k.cy
            if (bu < -margPx || bu > f.width + margPx || bv < -margPx || bv > f.height + margPx) continue

            // Cuerpo de cámara del voxel (0, 0, 0) del bloque y cuánto cambia con cada paso local.
            val ox = (b.bx shl 3) * voxel + 0.5 * voxel
            val oy = (b.by shl 3) * voxel + 0.5 * voxel
            val oz = (b.bz shl 3) * voxel + 0.5 * voxel
            val c0x = r[0] * ox + r[1] * oy + r[2] * oz + t.x
            val c0y = r[3] * ox + r[4] * oy + r[5] * oz + t.y
            val c0z = r[6] * ox + r[7] * oy + r[8] * oz + t.z
            for (lz in 0 until 8) {
                for (ly in 0 until 8) {
                    for (lx in 0 until 8) {
                        val l = lx or (ly shl 3) or (lz shl 6)
                        val wt = b.weight[l]
                        if (wt <= MIN_VOTE_WEIGHT) continue
                        val sRaw = b.sdf[l]
                        if (sRaw >= FREE_ENOUGH) continue // ya es espacio libre
                        val cz = c0z + lx * r[6] * voxel + ly * r[7] * voxel + lz * r[8] * voxel
                        if (cz < config.minDepthM || cz > config.maxDepthM) continue
                        val cx = c0x + lx * r[0] * voxel + ly * r[1] * voxel + lz * r[2] * voxel
                        val cy = c0y + lx * r[3] * voxel + ly * r[4] * voxel + lz * r[5] * voxel
                        val pu = floor(k.fx * cx / cz + k.cx).toInt()
                        val pv = floor(k.fy * cy / cz + k.cy).toInt()
                        if (pu < 1 || pu >= f.width - 1 || pv < 1 || pv >= f.height - 1) continue

                        // Primero el píxel central (descarta casi todo): si no ve más lejos, no hay nada que decidir.
                        val dc = depthAt(f, pu, pv)
                        // La holgura crece con la distancia: la profundidad suave se equivoca ~10 % de lo que mide.
                        if (dc <= 0.0 || dc - cz <= max(baseMargin, FREE_GAP_REL * dc)) continue
                        // Después los 8 vecinos: un solo píxel inválido o que ve cerca del voxel basta para no concluir nada.
                        var nearest = dc
                        var known = true
                        loop@ for (dv in -1..1) {
                            for (du in -1..1) {
                                if (du == 0 && dv == 0) continue
                                val d = depthAt(f, pu + du, pv + dv)
                                if (d <= 0.0) {
                                    known = false
                                    break@loop
                                }
                                if (d < nearest) nearest = d
                            }
                        }
                        if (!known || nearest - cz <= max(baseMargin, FREE_GAP_REL * nearest)) continue

                        // Pesa lo que habría pesado ver una superficie AHÍ (a la distancia del voxel): el voto de «aquí no
                        // hay nada» es tan firme como lo fue cada observación que levantó el fantasma.
                        // Y como cada voxel recibe varios rayos por cuadro (y de cerca, muchos), el voto de UN cuadro pesa
                        // lo que pesarían todos esos rayos: si no, el voto sería muy débil frente a la evidencia a favor.
                        val voxelPx = voxel * max(k.fx, k.fy) / cz / max(1, config.stride)
                        val raysPerFrame = if (config.normalizeByRays) 1.0 else max(1.0, voxelPx * voxelPx)
                        val wf = (config.freeVoteWeight * observationWeight(f, pu, pv, cz) * raysPerFrame).toFloat()
                        val w = wt / WEIGHT_SCALE
                        val s0 = sRaw / SDF_SCALE
                        val mergedSdf = ((s0 * w + wf) / (w + wf)).coerceIn(-1f, 1f)
                        b.sdf[l] = Math.round(mergedSdf * SDF_SCALE).toShort()
                        b.weight[l] = Math.round(min(w + wf, config.maxWeight) * WEIGHT_SCALE).toShort()
                        // Solo se revisa al emitir lo que puede tener (o tener pronto) un cuadrilátero.
                        if ((b.last[l].toInt() and 0xFFFF) != frameStamp &&
                            ((b.edges[l].toInt() and IN_SURFACE) != 0 || abs(mergedSdf) <= nearSurface)
                        ) {
                            b.last[l] = frameStamp.toShort()
                            if (touchedCount == touched.size) touched = touched.copyOf(touched.size * 2)
                            touched[touchedCount++] = (bi shl 9) or l
                        }
                        votes++
                    }
                }
            }
        }
        lastFreeVotes = votes
    }

    private fun depthAt(f: DepthFrame, u: Int, v: Int): Double {
        val i = v * f.width + u
        val mm = f.depthMm[i].toInt() and 0xFFFF
        if (mm == 0) return 0.0
        val d = mm / 1000.0
        if (d < config.minDepthM || d > config.maxDepthM) return 0.0
        val conf = f.confidence
        if (conf != null) {
            val c = conf[i].toInt() and 0xFF
            if (c < config.minConfidence) return 0.0
            if (c == DepthMerge.FILL_CONFIDENCE && (d < config.fillMinDepthM || d > config.fillMaxDepthM)) return 0.0
        }
        return d
    }

    /**
     * Peso de una observación: la cruda confiable pesa `rawWeight`; el relleno (la suave), `fillWeight` y, si hay
     * `anchorSigmaPx`, además decae con la distancia al dato crudo más cercano del cuadro (solo ahí es creíble); y las de
     * lejos (más ruidosas) pesan menos.
     */
    private fun observationWeight(f: DepthFrame, u: Int, v: Int, depth: Double): Float {
        val conf = f.confidence
        var base = config.rawWeight
        if (conf != null) {
            val i = v * f.width + u
            val c = conf[i].toInt() and 0xFF
            if (c == DepthMerge.FILL_CONFIDENCE) {
                base = config.fillWeight
                if (anchoring && i < anchorDist.size) {
                    val d = anchorDist[i] / config.anchorSigmaPx
                    base *= max(exp(-0.5f * d * d), config.anchorFloor)
                }
            } else {
                val ramp = min(1f, c / config.rawFullConfidence.toFloat())
                base = config.rawWeight * (config.rawLowWeight + (1f - config.rawLowWeight) * ramp)
            }
        }
        val dist = (depth / 2.5)
        return (base / (1.0 + dist * dist)).toFloat()
    }

    // ---------- superficie ----------

    private fun emitSurface(born: Float): Int {
        var quads = 0
        for (i in 0 until touchedCount) {
            val slot = touched[i]
            val b = blocks[slot ushr 9]
            val l = slot and 511
            // Una arista que cruza la superficie tiene a sus dos voxeles cerca de la superficie.
            val near = abs(b.sdf[l] / SDF_SCALE) <= nearSurface
            // Un voxel que nunca estuvo cerca de la superficie no pudo emitir nada: no hay qué emitir ni qué quitar.
            if (!near && (b.edges[l].toInt() and IN_SURFACE) == 0) continue
            val x = (b.bx shl 3) + (l and 7)
            val y = (b.by shl 3) + ((l shr 3) and 7)
            val z = (b.bz shl 3) + ((l shr 6) and 7)
            // Lejos de la superficie ya no se emite, pero sí se retira lo que se había emitido (si los cuadros lo contradijeron).
            quads += tryEdge(x, y, z, 0, born, near)
            quads += tryEdge(x, y, z, 1, born, near)
            quads += tryEdge(x, y, z, 2, born, near)
            quads += tryEdge(x - 1, y, z, 0, born, near)
            quads += tryEdge(x, y - 1, z, 1, born, near)
            quads += tryEdge(x, y, z - 1, 2, born, near)
        }
        return quads
    }

    /**
     * Arista que va del voxel (lx, ly, lz) al siguiente sobre el eje `axis`. Si cruza la superficie y todavía no tiene su
     * cuadrilátero, lo emite (devuelve 1). Si ya lo tenía y dejó de cruzar porque otros cuadros la contradijeron, lo quita.
     */
    private fun tryEdge(lx: Int, ly: Int, lz: Int, axis: Int, born: Float, allowEmit: Boolean): Int {
        val a = slotAt(lx, ly, lz)
        if (a < 0) return 0
        val ownerBlock = blocks[a ushr 9]
        val ownerLocal = a and 511
        val emitted = (ownerBlock.edges[ownerLocal].toInt() shr axis) and 1 != 0
        if (!emitted && !allowEmit) return 0
        val ex = if (axis == 0) 1 else 0
        val ey = if (axis == 1) 1 else 0
        val ez = if (axis == 2) 1 else 0
        val b = slotAt(lx + ex, ly + ey, lz + ez)
        var crossing = false
        if (valid(a) && valid(b)) {
            val sa = sdfOf(a)
            val sb = sdfOf(b)
            crossing = (sa >= 0f) != (sb >= 0f) && abs(sa - sb) <= config.maxEdgeJump
        }
        if (emitted) {
            val pendingBit = PENDING_REMOVAL shl axis
            if (crossing) {
                if ((ownerBlock.edges[ownerLocal].toInt() and pendingBit) != 0) {
                    ownerBlock.edges[ownerLocal] = (ownerBlock.edges[ownerLocal].toInt() and pendingBit.inv()).toByte()
                }
                // La arista ya tiene su cuadrilátero, pero sus vértices se calcularon con los primeros cuadros que vieron
                // el lugar: se vuelven a calcular con lo que el volumen cree AHORA.
                if (config.refreshVertices) edgeCellVerts(lx, ly, lz, axis, born, refreshVerts)
                return 0
            }
            if (config.removeAfterMisses > 1 && (ownerBlock.edges[ownerLocal].toInt() and pendingBit) == 0) {
                // Primera falta: se espera a la siguiente vez que se evalúe esta arista.
                ownerBlock.edges[ownerLocal] = (ownerBlock.edges[ownerLocal].toInt() or pendingBit).toByte()
                return 0
            }
            removeEdgeQuad(lx, ly, lz, axis, ownerBlock, ownerLocal)
            return 0
        }
        if (!crossing) return 0

        val verts = IntArray(4)
        if (!edgeCellVerts(lx, ly, lz, axis, born, verts)) return 0
        val q = mesh.quadCount
        mesh.addQuad(verts[0], verts[1], verts[2], verts[3])
        edgeQuad.put(edgeKey(lx, ly, lz, axis), q)
        ownerBlock.edges[ownerLocal] = (ownerBlock.edges[ownerLocal].toInt() or (1 shl axis)).toByte()
        return 1
    }

    /**
     * Las cuatro celdas que comparten la arista (eje `axis`), recorridas en ciclo alrededor de ella. Deja sus vértices en
     * `out` y devuelve false si alguna no da vértice. Crea los que falten y **recalcula** los que ya existían.
     */
    private fun edgeCellVerts(lx: Int, ly: Int, lz: Int, axis: Int, born: Float, out: IntArray): Boolean {
        val bAxis = (axis + 1) % 3
        val cAxis = (axis + 2) % 3
        for (i in 0 until 4) {
            val jb = CELL_OFFSETS[i * 2]
            val jc = CELL_OFFSETS[i * 2 + 1]
            var cx = lx
            var cy = ly
            var cz = lz
            when (bAxis) {
                0 -> cx += jb
                1 -> cy += jb
                else -> cz += jb
            }
            when (cAxis) {
                0 -> cx += jc
                1 -> cy += jc
                else -> cz += jc
            }
            val vi = cellVertexAt(cx, cy, cz, born)
            if (vi < 0) return false
            out[i] = vi
        }
        return true
    }

    /** Quita el cuadrilátero de una arista (y libera la arista para que pueda volver a emitirse si la superficie regresa). */
    private fun removeEdgeQuad(lx: Int, ly: Int, lz: Int, axis: Int, block: Block, local: Int) {
        block.edges[local] = (block.edges[local].toInt() and ((1 shl axis) or (PENDING_REMOVAL shl axis)).inv()).toByte()
        val key = edgeKey(lx, ly, lz, axis)
        val q = edgeQuad.get(key)
        if (q < 0) return
        edgeQuad.put(key, -1)
        if (mesh.removeQuad(q)) removedQuads++
    }

    private fun edgeKey(ix: Int, iy: Int, iz: Int, axis: Int): Long =
        ((ix.toLong() and MASK20) shl 42) or ((iy.toLong() and MASK20) shl 22) or ((iz.toLong() and MASK20) shl 2) or axis.toLong()

    /**
     * El vértice de la celda cuya esquina mínima es (cx, cy, cz); lo crea si sus 8 voxeles ya son confiables y, si ya
     * existía, lo **mueve** a donde el volumen dice ahora que está la superficie (una vez por cuadro como máximo).
     */
    private fun cellVertexAt(cx: Int, cy: Int, cz: Int, born: Float): Int {
        val ck = voxelKey(cx, cy, cz)
        val existing = cellVertex.get(ck)
        if (existing >= 0) {
            if (!config.refreshVertices) return existing
            if (cellRefreshed.get(ck) == frameStamp) return existing
            cellRefreshed.put(ck, frameStamp)
        }

        val s = FloatArray(8)
        var snx = 0f
        var sny = 0f
        var snz = 0f
        for (i in 0 until 8) {
            val slot = slotAt(cx + (i and 1), cy + ((i shr 1) and 1), cz + ((i shr 2) and 1))
            // Al refrescar, si la celda ya no es confiable se deja el vértice donde estaba: quitar el cuadrilátero es
            // decisión de `tryEdge` (con su histéresis), no de aquí.
            if (!valid(slot)) return existing
            s[i] = sdfOf(slot)
            val blk = blocks[slot ushr 9]
            val l = slot and 511
            val wt = blk.weight[l] / WEIGHT_SCALE
            snx += blk.nx[l] / SDF_SCALE * wt
            sny += blk.ny[l] / SDF_SCALE * wt
            snz += blk.nz[l] / SDF_SCALE * wt
        }

        // Promedio de los cruces de las 12 aristas de la celda (en coordenadas de centro de voxel).
        var px = 0.0
        var py = 0.0
        var pz = 0.0
        var crossings = 0
        for (e in CELL_EDGES) {
            val i0 = e[0]
            val i1 = e[1]
            val s0 = s[i0]
            val s1 = s[i1]
            if ((s0 >= 0f) == (s1 >= 0f) || abs(s0 - s1) > config.maxEdgeJump) continue
            val t = (s0 / (s0 - s1)).toDouble()
            px += (cx + (i0 and 1)) + t * ((i1 and 1) - (i0 and 1))
            py += (cy + ((i0 shr 1) and 1)) + t * (((i1 shr 1) and 1) - ((i0 shr 1) and 1))
            pz += (cz + ((i0 shr 2) and 1)) + t * (((i1 shr 2) and 1) - ((i0 shr 2) and 1))
            crossings++
        }
        if (crossings == 0) return existing
        val v = config.voxelM
        val x = (px / crossings + 0.5) * v
        val y = (py / crossings + 0.5) * v
        val z = (pz / crossings + 0.5) * v

        // Normal: la de las imágenes de profundidad (suave y confiable); si no hay, el gradiente de la distancia con
        // signo (apunta hacia el lado positivo: el de la cámara).
        val gx = (s[1] + s[3] + s[5] + s[7]) - (s[0] + s[2] + s[4] + s[6])
        val gy = (s[2] + s[3] + s[6] + s[7]) - (s[0] + s[1] + s[4] + s[5])
        val gz = (s[4] + s[5] + s[6] + s[7]) - (s[0] + s[1] + s[2] + s[3])
        val len = sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()
        val glen = sqrt((snx * snx + sny * sny + snz * snz).toDouble()).toFloat()
        val nx: Float
        val ny: Float
        val nz: Float
        if (glen > 1e-3f && (len < 1e-6f || (snx * gx + sny * gy + snz * gz) >= 0f)) {
            nx = snx / glen
            ny = sny / glen
            nz = snz / glen
        } else {
            nx = if (len > 1e-6f) gx / len else 0f
            ny = if (len > 1e-6f) gy / len else 1f
            nz = if (len > 1e-6f) gz / len else 0f
        }

        if (existing >= 0) {
            mesh.moveVertex(existing, x.toFloat(), y.toFloat(), z.toFloat(), nx, ny, nz)
            return existing
        }
        val vi = mesh.addVertex(x.toFloat(), y.toFloat(), z.toFloat(), nx, ny, nz, born)
        if (vi < 0) return -1
        cellVertex.put(ck, vi)
        return vi
    }

    private fun voxelKey(ix: Int, iy: Int, iz: Int): Long =
        ((ix.toLong() and MASK21) shl 42) or ((iy.toLong() and MASK21) shl 21) or (iz.toLong() and MASK21)

    private companion object {
        const val MASK21 = 0x1FFFFFL
        const val MASK20 = 0xFFFFFL

        /** Bit del byte `edges` de cada voxel: estuvo cerca de la superficie (puede tener aristas emitidas). */
        const val IN_SURFACE = 8

        /** Bits 4, 5 y 6 del byte `edges` (uno por eje): la arista falló una vez y se quitará si vuelve a fallar. */
        const val PENDING_REMOVAL = 16

        /** Holgura relativa para decir «se ve a través»: el 12 % de la distancia a la que se ve la superficie. */
        const val FREE_GAP_REL = 0.12

        /** Mitad de la diagonal de un bloque de 8×8×8 voxeles, en voxeles. */
        const val BLOCK_HALF_DIAGONAL = 6.93

        /** Voxeles con menos peso que esto (×128) no votan; y con la distancia normalizada sobre esto (×32767) ya son «libres». */
        const val MIN_VOTE_WEIGHT = 12
        const val FREE_ENOUGH = 31_000
        const val BLOCK_VOXELS = 512
        const val BLOCK_BYTES = BLOCK_VOXELS * (2 + 2 + 1 + 2 + 1 + 6)
        const val GROUND_CELL_M = 0.25
        const val SDF_SCALE = 32767f
        const val WEIGHT_SCALE = 128f

        /** Distancia «infinita» (px) para los píxeles sin ningún dato crudo confiable en el cuadro, y paso diagonal del chaflán. */
        const val FAR_PX = 1e4f
        const val DIAGONAL = 1.4142f

        /** Las superficies más rasantes que esto (coseno con el rayo) pesan poco y no se estiran más la banda. */
        const val MIN_COS = 0.30
        const val EDGE_JUMP_ABS_M = 0.03
        const val EDGE_JUMP_REL = 0.06

        /** Desplazamientos (en los dos ejes que rodean a una arista) de las cuatro celdas que la comparten. */
        val CELL_OFFSETS = intArrayOf(-1, -1, 0, -1, 0, 0, -1, 0)

        /** Pares de esquinas (bit 0 = x, bit 1 = y, bit 2 = z) que forman las 12 aristas de una celda. */
        val CELL_EDGES = arrayOf(
            intArrayOf(0, 1), intArrayOf(2, 3), intArrayOf(4, 5), intArrayOf(6, 7),
            intArrayOf(0, 2), intArrayOf(1, 3), intArrayOf(4, 6), intArrayOf(5, 7),
            intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
        )
    }
}
