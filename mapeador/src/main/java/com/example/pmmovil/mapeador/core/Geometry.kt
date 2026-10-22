package com.example.pmmovil.mapeador.core

/** Mapa de Long a Int con direccionamiento abierto y sin cajas: se usa para cientos de miles de voxeles. */
class LongIntMap(initialCapacity: Int = 1024) {
    private var capacity = 16
    private var keys: LongArray
    private var values: IntArray

    var size: Int = 0
        private set

    init {
        while (capacity < initialCapacity * 2) capacity = capacity shl 1
        keys = LongArray(capacity) { EMPTY }
        values = IntArray(capacity)
    }

    /** El valor de la llave, o -1 si no está. */
    fun get(key: Long): Int {
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == key) return values[i]
            if (k == EMPTY) return -1
            i = (i + 1) and (capacity - 1)
        }
    }

    fun put(key: Long, value: Int) {
        require(key != EMPTY) { "Llave reservada." }
        if ((size + 1) * 2 > capacity) grow()
        var i = slot(key)
        while (true) {
            val k = keys[i]
            if (k == EMPTY) {
                keys[i] = key
                values[i] = value
                size++
                return
            }
            if (k == key) {
                values[i] = value
                return
            }
            i = (i + 1) and (capacity - 1)
        }
    }

    private fun slot(key: Long): Int = ((key * MIX) ushr 32).toInt() and (capacity - 1)

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        capacity = capacity shl 1
        keys = LongArray(capacity) { EMPTY }
        values = IntArray(capacity)
        size = 0
        for (i in oldKeys.indices) {
            if (oldKeys[i] != EMPTY) put(oldKeys[i], oldValues[i])
        }
    }

    private companion object {
        const val EMPTY = Long.MIN_VALUE
        const val MIX = -7046029254386353131L
    }
}

/**
 * Nube de puntos que solo crece. Cada punto guarda 7 floats intercalados (x, y, z, nx, ny, nz, nacido)
 * para subirlos tal cual a la GPU. Se escribe desde un hilo de trabajo y se lee desde el hilo de GL.
 */
class PointCloud(val maxPoints: Int = 400_000, initialCapacity: Int = 16_384) {
    private var data = FloatArray(initialCapacity * STRIDE)

    @Volatile
    var count: Int = 0
        private set

    val isFull: Boolean get() = count >= maxPoints

    /** Devuelve el índice del punto, o -1 si la nube ya está llena. */
    @Synchronized
    fun add(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, bornSeconds: Float): Int {
        if (count >= maxPoints) return -1
        val need = (count + 1) * STRIDE
        if (need > data.size) data = data.copyOf(minOf(maxOf(need, data.size * 2), maxPoints * STRIDE))
        val o = count * STRIDE
        data[o] = x
        data[o + 1] = y
        data[o + 2] = z
        data[o + 3] = nx
        data[o + 4] = ny
        data[o + 5] = nz
        data[o + 6] = bornSeconds
        return count++
    }

    /** Copia los puntos [from, to) (intercalados) a `out`, empezando en `outOffset`. */
    @Synchronized
    fun copyRange(from: Int, to: Int, out: FloatArray, outOffset: Int = 0) {
        System.arraycopy(data, from * STRIDE, out, outOffset, (to - from) * STRIDE)
    }

    @Synchronized
    fun position(i: Int) = Vec3(data[i * STRIDE].toDouble(), data[i * STRIDE + 1].toDouble(), data[i * STRIDE + 2].toDouble())

    @Synchronized
    fun normal(i: Int) = Vec3(data[i * STRIDE + 3].toDouble(), data[i * STRIDE + 4].toDouble(), data[i * STRIDE + 5].toDouble())

    companion object {
        const val STRIDE = 7
    }
}

/**
 * Una imagen de profundidad de ARCore con su pose. `depthMm` va en milímetros (0 = sin dato) y se lee
 * sin signo; `confidence` es 0..255 (null = todo confiable). Los intrínsecos ya están escalados al
 * tamaño de ESTA imagen de profundidad.
 */
class DepthFrame(
    val width: Int,
    val height: Int,
    val depthMm: ShortArray,
    val confidence: ByteArray?,
    val intrinsics: Intrinsics,
    /** Pose de la cámara (convención ARCore, orientada al sensor) en el instante de la profundidad. */
    val worldFromCamera: Se3,
    val timestampNs: Long,
    /** Las imágenes de ARCore tal como llegaron (para registrarlas y repetir la reconstrucción en la PC). */
    val rawMm: ShortArray? = null,
    val rawConfidence: ByteArray? = null,
    val smoothMm: ShortArray? = null
)

/** Historial corto de poses para saber dónde estaba la cámara en el instante exacto de una imagen de profundidad. */
class PoseHistory(private val capacity: Int = 24) {
    private val stamps = ArrayList<Long>()
    private val poses = ArrayList<Se3>()

    @Synchronized
    fun add(timestampNs: Long, pose: Se3) {
        if (stamps.isNotEmpty() && timestampNs <= stamps.last()) return
        stamps.add(timestampNs)
        poses.add(pose)
        if (stamps.size > capacity) {
            stamps.removeAt(0)
            poses.removeAt(0)
        }
    }

    /**
     * Pose interpolada en `timestampNs`. Las marcas fuera del historial se acotan a la más cercana, salvo
     * que se pasen más de `slackNs` del borde: entonces no hay forma de saberlo y devuelve null.
     */
    @Synchronized
    fun poseAt(timestampNs: Long, slackNs: Long = Long.MAX_VALUE): Se3? {
        if (stamps.isEmpty()) return null
        if (timestampNs <= stamps.first()) {
            return if (stamps.first() - timestampNs > slackNs) null else poses.first()
        }
        if (timestampNs >= stamps.last()) {
            return if (timestampNs - stamps.last() > slackNs) null else poses.last()
        }
        for (i in 0 until stamps.size - 1) {
            if (timestampNs >= stamps[i] && timestampNs <= stamps[i + 1]) {
                val span = (stamps[i + 1] - stamps[i]).toDouble()
                return Se3.blend(poses[i], poses[i + 1], (timestampNs - stamps[i]) / span)
            }
        }
        return poses.last()
    }
}
