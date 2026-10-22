package com.example.pmmovil.mapeador.core

import kotlin.math.acos

class BuilderConfig(
    /** Con cuántos fotogramas previos se empareja cada fotograma nuevo. */
    val maxPairs: Int = 3,
    val minBaselineM: Double = 0.10,
    val maxBaselineM: Double = 3.0,
    /** Máximo ángulo entre los ejes ópticos para que los dos fotogramas vean lo mismo. */
    val maxAxisAngleDeg: Double = 45.0,
    val ratio: Double = 0.8,
    val maxHamming: Int = 80,
    val epipolarPx: Double = 3.0,
    /** Tope de entradas nuevas (descriptores) por fotograma clave: acota el crecimiento del mapa. */
    val maxNewEntriesPerKeyframe: Int = 250,
    /**
     * Un punto nuevo cerca de uno existente (radio = base + k·profundidad, con tope) y con un
     * descriptor parecido se toma como el mismo lugar. La triangulación es menos exacta de lejos.
     */
    val dedupeBaseM: Double = 0.05,
    val dedupePerMeter: Double = 0.02,
    val dedupeMaxM: Double = LandmarkSet.CELL_M,
    val dedupeHamming: Int = 48,
    /** Una nueva triangulación se suma a un punto ya rastreado si cae dentro de base + k·profundidad. */
    val mergeBaseM: Double = 0.05,
    val mergePerMeter: Double = 0.04,
    /** Vistas (descriptores) que se guardan por punto, y qué tan distinto debe ser uno nuevo. */
    val maxEntriesPerPoint: Int = 4,
    val newEntryHamming: Int = 32,
    /** Cuántos fotogramas recientes se conservan en memoria para triangular contra ellos. */
    val recentWindow: Int = 8,
    val triangulation: TriangulationConfig = TriangulationConfig()
)

class KeyframeRecord(val id: Int, val pose: CamPose, val timestampNs: Long)

class AddResult(
    val keyframeId: Int,
    val pairsUsed: Int,
    val matched: Int,
    val triangulated: Int,
    val newPoints: Int,
    val merged: Int,
    val newEntries: Int,
    val conflicts: Int
)

/**
 * Arma el mapa con poses CONOCIDAS: empareja los rasgos de cada fotograma nuevo con los de
 * fotogramas recientes, descarta lo que no cuadra con la geometría epipolar y triangula puntos 3D.
 *
 * Cada rasgo recuerda de qué punto del mapa es («pista»). Cuando un rasgo ya pertenece a un punto,
 * la nueva triangulación se SUMA a ese punto (su posición se promedia) en vez de crear uno nuevo,
 * y el descriptor nuevo se guarda como otra vista si se ve distinto. Los puntos quedan en el marco
 * de ARCore de la sesión de mapeo, que es el marco del mapa.
 */
class MapBuilder(
    private val matcher: DescriptorMatcher,
    val config: BuilderConfig = BuilderConfig()
) {
    val landmarks = LandmarkSet()
    val keyframes = ArrayList<KeyframeRecord>()

    private class Recent(val record: KeyframeRecord, val features: Features) {
        /** Punto del mapa al que pertenece cada rasgo (-1 = ninguno todavía). */
        val pointOf = IntArray(features.count) { -1 }
    }

    private val recent = ArrayDeque<Recent>()
    private var nextId = 1

    fun addKeyframe(pose: Se3, k: Intrinsics, f: Features, timestampNs: Long): AddResult {
        val id = nextId++
        val cam = CamPose(pose, k)
        val record = KeyframeRecord(id, cam, timestampNs)
        val cur = Recent(record, f)

        var pairs = 0
        var matched = 0
        var triangulated = 0
        var newPoints = 0
        var merged = 0
        var newEntries = 0
        var conflicts = 0

        val candidates = recent.filter { isGoodPartner(cam, it.record.pose) }.takeLast(config.maxPairs).reversed()
        for (c in candidates) {
            val knn = matcher.knn(f.descriptors, f.count, c.features.descriptors, c.features.count, 2)
            pairs++
            for (qi in 0 until f.count) {
                val partner = knn.index[qi * 2]
                if (partner < 0) continue
                val d1 = knn.dist[qi * 2]
                val d2 = knn.dist[qi * 2 + 1]
                if (d1 > config.maxHamming) continue
                if (d2 != Int.MAX_VALUE && d1 >= config.ratio * d2) continue
                matched++

                val ua = f.xy[2 * qi].toDouble()
                val va = f.xy[2 * qi + 1].toDouble()
                val ub = c.features.xy[2 * partner].toDouble()
                val vb = c.features.xy[2 * partner + 1].toDouble()
                if (Epipolar.distancePx(cam, ua, va, c.record.pose, ub, vb) > config.epipolarPx) continue
                val tri = Triangulator.midpoint(cam, ua, va, c.record.pose, ub, vb, config.triangulation) ?: continue
                triangulated++

                val own = cur.pointOf[qi]
                val theirs = c.pointOf[partner]
                val offset = qi * LandmarkSet.DESC_BYTES
                val point: Int
                if (own >= 0 && theirs >= 0 && own != theirs) {
                    conflicts++
                    continue
                } else if (own >= 0 || theirs >= 0) {
                    // El rasgo ya es de un punto conocido: se suma la medición si concuerda.
                    point = if (own >= 0) own else theirs
                    val tolerance = config.mergeBaseM + config.mergePerMeter * cam.center.distanceTo(tri.point)
                    if (landmarks.pointPosition(point).distanceTo(tri.point) > tolerance) {
                        conflicts++
                        continue
                    }
                    landmarks.observe(point, tri.point)
                    merged++
                } else {
                    val radius = minOf(config.dedupeMaxM, config.dedupeBaseM + config.dedupePerMeter * cam.center.distanceTo(tri.point))
                    val near = landmarks.findNearPoint(tri.point, radius)
                    if (near >= 0 && landmarks.minHammingTo(near, f.descriptors, offset) <= config.dedupeHamming) {
                        point = near
                        landmarks.observe(point, tri.point)
                        merged++
                    } else {
                        if (newEntries >= config.maxNewEntriesPerKeyframe) continue
                        point = landmarks.addPoint(tri.point)
                        newPoints++
                    }
                }
                cur.pointOf[qi] = point
                c.pointOf[partner] = point

                if (newEntries < config.maxNewEntriesPerKeyframe &&
                    landmarks.entriesOf(point) < config.maxEntriesPerPoint &&
                    landmarks.minHammingTo(point, f.descriptors, offset) > config.newEntryHamming
                ) {
                    landmarks.addEntry(point, f.descriptors, offset, id)
                    newEntries++
                }
            }
        }

        recent.addLast(cur)
        if (recent.size > config.recentWindow) recent.removeFirst()
        keyframes.add(record)
        return AddResult(id, pairs, matched, triangulated, newPoints, merged, newEntries, conflicts)
    }

    private fun isGoodPartner(a: CamPose, b: CamPose): Boolean {
        val baseline = a.center.distanceTo(b.center)
        if (baseline < config.minBaselineM || baseline > config.maxBaselineM) return false
        val cos = a.forward.dot(b.forward).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cos)) <= config.maxAxisAngleDeg
    }
}
