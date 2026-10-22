package com.example.pmmovil.mapeador.core

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Registro binario de las imágenes de profundidad que se integran al mapear (cruda, confianza y suave, con la pose
 * y los intrínsecos de cada una). Sirve para repetir en la PC, con datos reales del teléfono, la reconstrucción de
 * superficies y ajustarla sin tener que caminar otra vez.
 *
 * Formato (big-endian): «PMMD», versión; después, por cada imagen, `largo sin comprimir`, `largo comprimido` y el
 * bloque comprimido con deflate: marca de tiempo, pose (tx ty tz qx qy qz qw), intrínsecos (fx fy cx cy), ancho,
 * alto, banderas (1 cruda, 2 confianza, 4 suave, 8 solo la integrada) y los arreglos.
 */
object DepthLog {
    const val MAGIC = 0x504D4D44 // "PMMD"
    const val VERSION = 1

    internal const val HAS_RAW = 1
    internal const val HAS_CONFIDENCE = 2
    internal const val HAS_SMOOTH = 4
    internal const val MERGED_ONLY = 8
    internal const val HEADER_BYTES = 8 + 7 * 8 + 4 * 8 + 4 + 4 + 1
}

class DepthLogWriter(file: File) : Closeable {
    private val out: DataOutputStream
    private val deflater = Deflater(Deflater.BEST_SPEED)
    private var payload = ByteArray(0)
    private var packed = ByteArray(0)

    @Volatile
    var frames: Int = 0
        private set

    @Volatile
    var bytesWritten: Long = 0
        private set

    init {
        file.parentFile?.mkdirs()
        out = DataOutputStream(BufferedOutputStream(FileOutputStream(file), 1 shl 16))
        out.writeInt(DepthLog.MAGIC)
        out.writeInt(DepthLog.VERSION)
    }

    @Synchronized
    fun write(f: DepthFrame) {
        val n = f.width * f.height
        val hasParts = f.rawMm != null || f.smoothMm != null
        val raw = if (hasParts) f.rawMm else f.depthMm
        val conf = if (hasParts) f.rawConfidence else f.confidence
        val smooth = if (hasParts) f.smoothMm else null
        var flags = 0
        if (raw != null) flags = flags or DepthLog.HAS_RAW
        if (conf != null) flags = flags or DepthLog.HAS_CONFIDENCE
        if (smooth != null) flags = flags or DepthLog.HAS_SMOOTH
        if (!hasParts) flags = flags or DepthLog.MERGED_ONLY

        val size = DepthLog.HEADER_BYTES + (if (raw != null) n * 2 else 0) + (if (conf != null) n else 0) +
            (if (smooth != null) n * 2 else 0)
        if (payload.size < size) payload = ByteArray(size)
        val buf = ByteBuffer.wrap(payload, 0, size)
        buf.putLong(f.timestampNs)
        val q = f.worldFromCamera.toQuaternionXyzw()
        val t = f.worldFromCamera.t
        buf.putDouble(t.x).putDouble(t.y).putDouble(t.z)
        buf.putDouble(q[0]).putDouble(q[1]).putDouble(q[2]).putDouble(q[3])
        val k = f.intrinsics
        buf.putDouble(k.fx).putDouble(k.fy).putDouble(k.cx).putDouble(k.cy)
        buf.putInt(f.width).putInt(f.height)
        buf.put(flags.toByte())
        if (raw != null) for (i in 0 until n) buf.putShort(raw[i])
        if (conf != null) buf.put(conf, 0, n)
        if (smooth != null) for (i in 0 until n) buf.putShort(smooth[i])

        val limit = size + size / 8 + 64
        if (packed.size < limit) packed = ByteArray(limit)
        deflater.reset()
        deflater.setInput(payload, 0, size)
        deflater.finish()
        var total = 0
        while (!deflater.finished() && total < packed.size) total += deflater.deflate(packed, total, packed.size - total)

        out.writeInt(size)
        out.writeInt(total)
        out.write(packed, 0, total)
        frames++
        bytesWritten += 8L + total
    }

    @Synchronized
    override fun close() {
        runCatching { out.flush() }
        runCatching { out.close() }
        deflater.end()
    }
}

object DepthLogReader {
    /** Recorre las imágenes del archivo en orden; un final truncado (la app se cerró a media escritura) se ignora. */
    fun forEach(file: File, action: (DepthFrame) -> Unit) {
        DataInputStream(BufferedInputStream(FileInputStream(file), 1 shl 16)).use { input ->
            require(input.readInt() == DepthLog.MAGIC) { "El archivo no es un registro de profundidad." }
            val version = input.readInt()
            require(version == DepthLog.VERSION) { "Versión de registro no soportada ($version)." }
            val inflater = Inflater()
            try {
                while (true) {
                    val size: Int
                    val total: Int
                    val packed: ByteArray
                    try {
                        size = input.readInt()
                        total = input.readInt()
                        packed = ByteArray(total)
                        input.readFully(packed)
                    } catch (_: EOFException) {
                        return
                    }
                    val payload = ByteArray(size)
                    inflater.reset()
                    inflater.setInput(packed)
                    try {
                        var done = 0
                        while (done < size) {
                            val got = inflater.inflate(payload, done, size - done)
                            if (got == 0 && (inflater.finished() || inflater.needsInput())) break
                            done += got
                        }
                        if (done < size) return
                    } catch (_: DataFormatException) {
                        return
                    }
                    action(decode(payload))
                }
            } finally {
                inflater.end()
            }
        }
    }

    fun readAll(file: File, maxFrames: Int = Int.MAX_VALUE): List<DepthFrame> {
        val list = ArrayList<DepthFrame>()
        forEach(file) { if (list.size < maxFrames) list.add(it) }
        return list
    }

    private fun decode(payload: ByteArray): DepthFrame {
        val buf = ByteBuffer.wrap(payload)
        val stamp = buf.getLong()
        val t = Vec3(buf.getDouble(), buf.getDouble(), buf.getDouble())
        val pose = Se3.fromQuaternion(buf.getDouble(), buf.getDouble(), buf.getDouble(), buf.getDouble(), t)
        val fx = buf.getDouble()
        val fy = buf.getDouble()
        val cx = buf.getDouble()
        val cy = buf.getDouble()
        val w = buf.getInt()
        val h = buf.getInt()
        val flags = buf.get().toInt()
        val n = w * h
        val raw = if (flags and DepthLog.HAS_RAW != 0) ShortArray(n) { buf.getShort() } else null
        val conf = if (flags and DepthLog.HAS_CONFIDENCE != 0) ByteArray(n).also { buf.get(it) } else null
        val smooth = if (flags and DepthLog.HAS_SMOOTH != 0) ShortArray(n) { buf.getShort() } else null
        val k = Intrinsics(fx, fy, cx, cy, w, h)
        if (flags and DepthLog.MERGED_ONLY != 0) return DepthFrame(w, h, raw ?: ShortArray(n), conf, k, pose, stamp)
        val merged = DepthMerge.merge(DepthMode.MIXED, raw, conf, smooth)
        return DepthFrame(
            w, h, merged?.depthMm ?: ShortArray(n), merged?.confidence, k, pose, stamp,
            rawMm = raw, rawConfidence = conf, smoothMm = smooth
        )
    }
}
