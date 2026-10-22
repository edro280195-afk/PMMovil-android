package com.example.pmmovil.mapeador.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class MapMeta(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val keyframes: Int,
    val landmarks: Int,
    val locations: Int,
    val intrinsics: Intrinsics?,
    /** Voxeles de superficie escaneados y el área aproximada que cubren. */
    val geometryPoints: Int = 0,
    val areaM2: Double = 0.0
)

/** Una ubicación marcada en el mapa (posición en el marco del mapa; +Y es arriba). */
class MapLocation(
    val code: String,
    val description: String,
    val position: Vec3,
    /** Altura sobre el punto a la que flota la tarjeta. */
    val cardHeightM: Double,
    /** Dónde se midió: piso, muro, profundidad… (informativo). */
    val surface: String,
    val createdAtMs: Long
)

class SavedMap(
    val meta: MapMeta,
    val landmarks: LandmarkSet,
    val locations: List<MapLocation>,
    /** Malla de superficies escaneadas con la profundidad de ARCore (null si el mapa no la trae). */
    val mesh: MeshBuffer? = null
)

/** Fotograma clave tal como se guarda y se exporta. */
class SavedKeyframe(val id: Int, val timestampNs: Long, val worldFromGl: Se3, val k: Intrinsics, val fileName: String)

/**
 * Guarda y carga mapas en una carpeta (un subdirectorio por mapa):
 * meta.json, locations.json, keyframes.json, landmarks.bin y la carpeta keyframes con los JPEG.
 * Los JPEG de un mapa en construcción viven en el borrador hasta que se guarda.
 */
class MapStore(val root: File) {
    init {
        root.mkdirs()
    }

    val draftDir: File get() = File(root, DRAFT_NAME)

    fun clearDraft() {
        draftDir.deleteRecursively()
        draftDir.mkdirs()
    }

    fun listMaps(): List<MapMeta> {
        val dirs = root.listFiles { f -> f.isDirectory && !f.name.startsWith("_") && !f.name.endsWith(".tmp") } ?: return emptyList()
        return dirs.mapNotNull { runCatching { readMeta(File(it, "meta.json")) }.getOrNull() }
            .sortedByDescending { it.createdAtMs }
    }

    fun save(
        meta: MapMeta,
        landmarks: LandmarkSet,
        locations: List<MapLocation>,
        keyframes: List<KeyframeRecord>,
        mesh: MeshBuffer? = null,
        voxelM: Float = 0.04f
    ) {
        val tmp = File(root, "${meta.id}.tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()
        writeLandmarks(File(tmp, "landmarks.bin"), landmarks)
        if (mesh != null && mesh.quadCount > 0) MeshIO.write(File(tmp, "mesh.bin"), mesh, voxelM)
        File(tmp, "locations.json").writeText(locationsToJson(locations).toString(2))
        File(tmp, "keyframes.json").writeText(keyframesToJson(keyframes).toString())
        File(tmp, "meta.json").writeText(metaToJson(meta).toString(2))

        val images = File(tmp, "keyframes")
        val draft = draftDir
        if (draft.exists()) {
            if (!draft.renameTo(images)) {
                draft.copyRecursively(images, overwrite = true)
                draft.deleteRecursively()
            }
        } else {
            images.mkdirs()
        }

        val dest = File(root, meta.id)
        dest.deleteRecursively()
        check(tmp.renameTo(dest)) { "No se pudo terminar de guardar el mapa." }
    }

    fun load(id: String): SavedMap {
        val dir = File(root, id)
        val meta = readMeta(File(dir, "meta.json"))
        val locations = locationsFromJson(JSONArray(File(dir, "locations.json").readText()))
        val landmarks = readLandmarks(File(dir, "landmarks.bin"))
        val meshFile = File(dir, "mesh.bin")
        val mesh = if (meshFile.exists()) runCatching { MeshIO.read(meshFile) }.getOrNull() else null
        return SavedMap(meta, landmarks, locations, mesh)
    }

    fun delete(id: String) {
        File(root, id).deleteRecursively()
    }

    fun mapDir(id: String): File = File(root, id)

    /** Escribe cameras.txt / images.txt / points3D.txt (formato texto de COLMAP) con las poses de ARCore. */
    fun exportColmap(id: String): File {
        val dir = File(root, id)
        val frames = keyframesFromJson(JSONArray(File(dir, "keyframes.json").readText()))
        val out = File(dir, "colmap")
        ColmapExport.write(out, frames)
        return out
    }

    // ---- binario de puntos ----

    /**
     * Formato (little-endian): magic, versión, puntos P, entradas E, bytes por descriptor;
     * luego P·3 floats (posiciones), P ints (observaciones), E·32 bytes (descriptores),
     * E ints (punto de cada entrada) y E ints (fotograma clave de cada entrada).
     */
    private fun writeLandmarks(file: File, lm: LandmarkSet) {
        val p = lm.pointCount
        val e = lm.entryCount
        val size = 20 + p * 12 + p * 4 + e * LandmarkSet.DESC_BYTES + e * 8
        val buf = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(MAGIC).putInt(VERSION).putInt(p).putInt(e).putInt(LandmarkSet.DESC_BYTES)
        for (i in 0 until p * 3) buf.putFloat(lm.pointPositions[i])
        for (i in 0 until p) buf.putInt(lm.pointObservations[i])
        buf.put(lm.descriptors, 0, e * LandmarkSet.DESC_BYTES)
        for (i in 0 until e) buf.putInt(lm.entryPoint[i])
        for (i in 0 until e) buf.putInt(lm.entryKeyframe[i])
        file.writeBytes(buf.array())
    }

    private fun readLandmarks(file: File): LandmarkSet {
        val buf = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt() == MAGIC) { "El archivo de puntos no es de este programa." }
        val version = buf.getInt()
        require(version == VERSION) { "Versión de mapa no soportada ($version)." }
        val p = buf.getInt()
        val e = buf.getInt()
        val descBytes = buf.getInt()
        require(descBytes == LandmarkSet.DESC_BYTES) { "Tamaño de descriptor distinto ($descBytes)." }
        val positions = FloatArray(p * 3) { buf.getFloat() }
        val observations = IntArray(p) { buf.getInt() }
        val descriptors = ByteArray(e * descBytes)
        buf.get(descriptors)
        val entryPoint = IntArray(e) { buf.getInt() }
        val entryKeyframe = IntArray(e) { buf.getInt() }
        return LandmarkSet.fromArrays(positions, observations, descriptors, entryPoint, entryKeyframe, p, e)
    }

    // ---- JSON ----

    private fun metaToJson(m: MapMeta) = JSONObject().apply {
        put("id", m.id)
        put("name", m.name)
        put("createdAtMs", m.createdAtMs)
        put("keyframes", m.keyframes)
        put("landmarks", m.landmarks)
        put("locations", m.locations)
        m.intrinsics?.let { put("intrinsics", intrinsicsToJson(it)) }
        put("geometryPoints", m.geometryPoints)
        put("areaM2", m.areaM2)
        put("version", VERSION)
    }

    private fun readMeta(file: File): MapMeta {
        val o = JSONObject(file.readText())
        return MapMeta(
            id = o.getString("id"),
            name = o.getString("name"),
            createdAtMs = o.getLong("createdAtMs"),
            keyframes = o.getInt("keyframes"),
            landmarks = o.getInt("landmarks"),
            locations = o.getInt("locations"),
            intrinsics = o.optJSONObject("intrinsics")?.let { intrinsicsFromJson(it) },
            geometryPoints = o.optInt("geometryPoints", 0),
            areaM2 = o.optDouble("areaM2", 0.0)
        )
    }

    private fun intrinsicsToJson(k: Intrinsics) = JSONObject().apply {
        put("fx", k.fx)
        put("fy", k.fy)
        put("cx", k.cx)
        put("cy", k.cy)
        put("width", k.width)
        put("height", k.height)
    }

    private fun intrinsicsFromJson(o: JSONObject) = Intrinsics(
        o.getDouble("fx"), o.getDouble("fy"), o.getDouble("cx"), o.getDouble("cy"), o.getInt("width"), o.getInt("height")
    )

    private fun locationsToJson(items: List<MapLocation>) = JSONArray().apply {
        for (l in items) {
            put(JSONObject().apply {
                put("code", l.code)
                put("description", l.description)
                put("x", l.position.x)
                put("y", l.position.y)
                put("z", l.position.z)
                put("cardHeightM", l.cardHeightM)
                put("surface", l.surface)
                put("createdAtMs", l.createdAtMs)
            })
        }
    }

    private fun locationsFromJson(a: JSONArray): List<MapLocation> = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        MapLocation(
            code = o.getString("code"),
            description = o.optString("description", ""),
            position = Vec3(o.getDouble("x"), o.getDouble("y"), o.getDouble("z")),
            cardHeightM = o.optDouble("cardHeightM", 1.2),
            surface = o.optString("surface", ""),
            createdAtMs = o.optLong("createdAtMs", 0L)
        )
    }

    private fun keyframesToJson(items: List<KeyframeRecord>) = JSONArray().apply {
        for (r in items) {
            val q = r.pose.worldFromGl.toQuaternionXyzw()
            val t = r.pose.worldFromGl.t
            put(JSONObject().apply {
                put("id", r.id)
                put("timestampNs", r.timestampNs)
                put("q", JSONArray(listOf(q[0], q[1], q[2], q[3])))
                put("t", JSONArray(listOf(t.x, t.y, t.z)))
                put("k", intrinsicsToJson(r.pose.k))
                put("file", keyframeFileName(r.id))
            })
        }
    }

    private fun keyframesFromJson(a: JSONArray): List<SavedKeyframe> = List(a.length()) { i ->
        val o = a.getJSONObject(i)
        val q = o.getJSONArray("q")
        val t = o.getJSONArray("t")
        SavedKeyframe(
            id = o.getInt("id"),
            timestampNs = o.getLong("timestampNs"),
            worldFromGl = Se3.fromQuaternion(
                q.getDouble(0), q.getDouble(1), q.getDouble(2), q.getDouble(3),
                Vec3(t.getDouble(0), t.getDouble(1), t.getDouble(2))
            ),
            k = intrinsicsFromJson(o.getJSONObject("k")),
            fileName = o.getString("file")
        )
    }

    companion object {
        const val DRAFT_NAME = "_borrador"
        private const val MAGIC = 0x4C4D4D50 // "PMML" en little-endian
        private const val VERSION = 2

        fun keyframeFileName(id: Int): String = String.format(Locale.US, "kf_%05d.jpg", id)
    }
}

/** Exporta las poses de ARCore al formato de texto de COLMAP (para usarlas como punto de partida en la PC). */
object ColmapExport {
    fun write(outDir: File, frames: List<SavedKeyframe>) {
        outDir.mkdirs()
        val cameras = LinkedHashMap<String, Int>()
        val camerasTxt = StringBuilder()
        camerasTxt.append("# Camera list with one line of data per camera:\n")
        camerasTxt.append("#   CAMERA_ID, MODEL, WIDTH, HEIGHT, PARAMS[]\n")
        val imagesTxt = StringBuilder()
        imagesTxt.append("# Image list with two lines of data per image:\n")
        imagesTxt.append("#   IMAGE_ID, QW, QX, QY, QZ, TX, TY, TZ, CAMERA_ID, NAME\n")
        imagesTxt.append("#   POINTS2D[] as (X, Y, POINT3D_ID)\n")

        for (f in frames) {
            val key = String.format(Locale.US, "%d %d %.2f %.2f %.2f %.2f", f.k.width, f.k.height, f.k.fx, f.k.fy, f.k.cx, f.k.cy)
            val camId = cameras.getOrPut(key) {
                val id = cameras.size + 1
                camerasTxt.append("$id PINHOLE $key\n")
                id
            }
            // COLMAP guarda la pose mundo→cámara en convención OpenCV.
            val cvFromWorld = CamPose(f.worldFromGl, f.k).cvFromWorld
            val q = cvFromWorld.toQuaternionXyzw()
            val t = cvFromWorld.t
            imagesTxt.append(
                String.format(
                    Locale.US, "%d %.9f %.9f %.9f %.9f %.9f %.9f %.9f %d %s\n\n",
                    f.id, q[3], q[0], q[1], q[2], t.x, t.y, t.z, camId, f.fileName
                )
            )
        }
        File(outDir, "cameras.txt").writeText(camerasTxt.toString())
        File(outDir, "images.txt").writeText(imagesTxt.toString())
        File(outDir, "points3D.txt").writeText("# 3D point list (vacío: se triangula en COLMAP con las poses conocidas)\n")
    }
}
