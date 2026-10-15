package com.example.pmmovil.mapspike

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Metros locales (x este, y norte) a lon/lat. El origen es sintético (Laredo, TX): NO es la bodega real. */
object Geo {
    const val ORIGIN_LON = -99.4803
    const val ORIGIN_LAT = 27.5306
    private const val M_PER_DEG = 111_319.49
    private val cosLat = cos(ORIGIN_LAT * PI / 180.0)

    fun lon(x: Double): Double = ORIGIN_LON + x / (M_PER_DEG * cosLat)
    fun lat(y: Double): Double = ORIGIN_LAT + y / M_PER_DEG
}

data class SceneStats(
    val units: Int,
    val slots: Int,
    val zones: Int,
    val walls: Int,
    val labels: Int,
    val geoJsonBytes: Long
)

/** Fuentes GeoJSON ya serializadas, más los datos que necesitan la cámara y la ruta. */
class SceneData(
    val zones: String,
    val slots: String,
    val walls: String,
    val units: String,
    val aisles: String,
    val labels: String,
    /** Ruta de ejemplo en metros locales: [x, y]. */
    val route: List<DoubleArray>,
    /** minX, minY, maxX, maxY en metros locales. */
    val bounds: DoubleArray,
    /** Códigos de las zonas de tarimas (para probar la selección). */
    val palletZones: List<String>,
    val stats: SceneStats
)

/**
 * Bodega de piso sintética para el spike: bloques de tarimas GMA (48x40 in), tinas IBC,
 * huacales a medida, cunas de tubos y áreas de lote. No hay racks. `replicas` copia la
 * bodega completa para probar cargas mayores.
 */
object FloorScene {
    private class Cls(val color: String, val dim: String)

    private val CLASSES: List<Cls> = listOf(
        "#9aa2ad", "#b08968", "#2c9c8f", "#7b8a99", "#a97142",
        "#8a5cc0", "#3b6ea5", "#d6a960", "#6b5bd0", "#2b6cb0"
    ).map { Cls(it, mix(it, "#E9EEF5", 0.72)) }

    // Tarima GMA: 48 x 40 in = 1.219 x 1.016 m. Paso con holgura de 0.10 m.
    private const val PALLET_W = 1.22
    private const val PALLET_D = 1.02
    private const val PITCH_X = 1.32
    private const val PITCH_Y = 1.12

    private class Fc {
        val sb = StringBuilder("{\"type\":\"FeatureCollection\",\"features\":[")
        var n = 0

        fun poly(xy: DoubleArray, props: String) {
            if (n++ > 0) sb.append(',')
            sb.append("{\"type\":\"Feature\",\"properties\":{").append(props)
                .append("},\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[")
            var i = 0
            while (i < xy.size) {
                if (i > 0) sb.append(',')
                sb.append('[').append(Geo.lon(xy[i])).append(',').append(Geo.lat(xy[i + 1])).append(']')
                i += 2
            }
            sb.append(",[").append(Geo.lon(xy[0])).append(',').append(Geo.lat(xy[1])).append("]]]}}")
        }

        fun line(xy: DoubleArray, props: String = "") {
            if (n++ > 0) sb.append(',')
            sb.append("{\"type\":\"Feature\",\"properties\":{").append(props)
                .append("},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            var i = 0
            while (i < xy.size) {
                if (i > 0) sb.append(',')
                sb.append('[').append(Geo.lon(xy[i])).append(',').append(Geo.lat(xy[i + 1])).append(']')
                i += 2
            }
            sb.append("]}}")
        }

        fun point(x: Double, y: Double, props: String) {
            if (n++ > 0) sb.append(',')
            sb.append("{\"type\":\"Feature\",\"properties\":{").append(props)
                .append("},\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                .append(Geo.lon(x)).append(',').append(Geo.lat(y)).append("]}}")
        }

        fun finish(): String = sb.append("]}").toString()
    }

    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double) =
        doubleArrayOf(x0, y0, x1, y0, x1, y1, x0, y1)

    private fun rotRect(cx: Double, cy: Double, w: Double, h: Double, deg: Double): DoubleArray {
        val a = deg * PI / 180.0
        val c = cos(a)
        val s = sin(a)
        val corners = doubleArrayOf(-w / 2, -h / 2, w / 2, -h / 2, w / 2, h / 2, -w / 2, h / 2)
        val out = DoubleArray(8)
        for (i in 0 until 4) {
            val x = corners[i * 2]
            val y = corners[i * 2 + 1]
            out[i * 2] = cx + x * c - y * s
            out[i * 2 + 1] = cy + x * s + y * c
        }
        return out
    }

    private fun unitProps(code: String, zone: String, kind: String, cls: Cls, base: Double, top: Double) =
        "\"code\":\"$code\",\"zone\":\"$zone\",\"kind\":\"$kind\",\"color\":\"${cls.color}\",\"dim\":\"${cls.dim}\",\"base\":$base,\"top\":$top"

    fun build(replicas: Int, seed: Int = 7): SceneData {
        val zones = Fc()
        val slots = Fc()
        val walls = Fc()
        val units = Fc()
        val aisles = Fc()
        val labels = Fc()
        val palletZones = ArrayList<String>()

        var minX = Double.MAX_VALUE
        var minY = Double.MAX_VALUE
        var maxX = -Double.MAX_VALUE
        var maxY = -Double.MAX_VALUE

        for (r in 0 until replicas) {
            val ox = (r % 4) * 135.0
            val oy = (r / 4) * 105.0
            val p = if (r == 0) "" else "R$r-"
            val rnd = Random(seed + r * 101)

            // --- Suelo, patio, andén, oficina ---
            zones.poly(rect(ox + -8, oy + -42, ox + 120, oy + 0), "\"code\":\"${p}PATIO\",\"kind\":\"yard\"")
            zones.poly(rect(ox + 0, oy + 0, ox + 110, oy + 70), "\"code\":\"${p}PISO\",\"kind\":\"floor\"")
            zones.poly(rect(ox + 2, oy + 0.8, ox + 108, oy + 7.5), "\"code\":\"${p}ANDEN\",\"kind\":\"dock\"")
            zones.poly(rect(ox + 90, oy + 42, ox + 108, oy + 68), "\"code\":\"${p}OFNA\",\"kind\":\"office\"")
            minX = minOf(minX, ox - 8); minY = minOf(minY, oy - 42)
            maxX = maxOf(maxX, ox + 120); maxY = maxOf(maxY, oy + 70)

            // --- Muros, oficina extruida y puertas de andén ---
            walls.poly(rect(ox + 0, oy + 0, ox + 110, oy + 0.4), "\"h\":1.8")
            walls.poly(rect(ox + 0, oy + 69.6, ox + 110, oy + 70), "\"h\":1.8")
            walls.poly(rect(ox + 0, oy + 0, ox + 0.4, oy + 70), "\"h\":1.8")
            walls.poly(rect(ox + 109.6, oy + 0, ox + 110, oy + 70), "\"h\":1.8")
            walls.poly(rect(ox + 90, oy + 42, ox + 108, oy + 68), "\"h\":4.2")
            for (k in 0 until 20) {
                val cx = ox + 4 + k * 5.2
                walls.poly(rect(cx - 1.4, oy + 0, cx + 1.4, oy + 0.7), "\"h\":3.4")
            }

            // --- 6 bloques de tarimas GMA (12 columnas x 8 filas, hasta 3 niveles) ---
            var blockNo = 0
            for (j in 0 until 2) for (i in 0 until 3) {
                blockNo++
                val zoneCode = "${p}P$blockNo"
                if (r == 0) palletZones.add(zoneCode)
                val x0 = ox + 6 + i * (12 * PITCH_X + 3.6)
                val y0 = oy + 12 + j * (8 * PITCH_Y + 3.6)
                zones.poly(rect(x0 - 0.3, y0 - 0.3, x0 + 12 * PITCH_X + 0.3 - 0.1, y0 + 8 * PITCH_Y + 0.3 - 0.1), "\"code\":\"$zoneCode\",\"kind\":\"pallets\"")
                labels.point(x0 + 6 * PITCH_X, y0 + 4 * PITCH_Y, "\"code\":\"$zoneCode\",\"kind\":\"zone\"")
                val main = CLASSES[(blockNo + r) % CLASSES.size]
                for (rw in 0 until 8) for (c in 0 until 12) {
                    val idx = rw * 12 + c + 1
                    val code = "$zoneCode-${idx.toString().padStart(2, '0')}"
                    val px = x0 + c * PITCH_X
                    val py = y0 + rw * PITCH_Y
                    slots.poly(rect(px, py, px + PALLET_W, py + PALLET_D), "\"code\":\"$code\"")
                    val roll = rnd.nextInt(100)
                    val levels = when {
                        roll < 20 -> 0
                        roll < 55 -> 1
                        roll < 85 -> 2
                        else -> 3
                    }
                    if (levels > 0) {
                        val cls = if (rnd.nextInt(100) < 75) main else CLASSES[rnd.nextInt(CLASSES.size)]
                        val levelH = 1.10 + rnd.nextDouble() * 0.40
                        units.poly(
                            rect(px + 0.04, py + 0.04, px + PALLET_W - 0.04, py + PALLET_D - 0.04),
                            unitProps(code, zoneCode, "pallet", cls, 0.0, 0.15 + levels * levelH)
                        )
                    }
                    if (c % 3 == 1 && rw % 2 == 0) {
                        labels.point(px + PALLET_W / 2, py + PALLET_D / 2, "\"code\":\"$code\",\"kind\":\"unit\"")
                    }
                }
            }

            // --- Tinas IBC (48 x 40 x 46 in): 2 bloques de 4 x 6 ---
            for (b in 0 until 2) {
                val zoneCode = "${p}I${b + 1}"
                val x0 = ox + 66 + b * (4 * PITCH_X + 3.6)
                val y0 = oy + 12
                zones.poly(rect(x0 - 0.3, y0 - 0.3, x0 + 4 * PITCH_X + 0.2, y0 + 6 * PITCH_Y + 0.2), "\"code\":\"$zoneCode\",\"kind\":\"ibc\"")
                labels.point(x0 + 2 * PITCH_X, y0 + 3 * PITCH_Y, "\"code\":\"$zoneCode\",\"kind\":\"zone\"")
                for (rw in 0 until 6) for (c in 0 until 4) {
                    val code = "$zoneCode-${(rw * 4 + c + 1).toString().padStart(2, '0')}"
                    val px = x0 + c * PITCH_X
                    val py = y0 + rw * PITCH_Y
                    slots.poly(rect(px, py, px + PALLET_W, py + PALLET_D), "\"code\":\"$code\"")
                    if (rnd.nextInt(100) < 85) {
                        val high = if (rnd.nextInt(100) < 20) 2 else 1
                        units.poly(
                            rect(px + 0.03, py + 0.03, px + PALLET_W - 0.03, py + PALLET_D - 0.03),
                            unitProps(code, zoneCode, "ibc", CLASSES[2], 0.0, 1.17 * high)
                        )
                    }
                }
            }

            // --- Huacales a medida: 22 de 24 celdas de 6.0 x 3.67 m ---
            zones.poly(rect(ox + 84, oy + 12, ox + 108, oy + 34), "\"code\":\"${p}H1\",\"kind\":\"crates\"")
            labels.point(ox + 96, oy + 35.4, "\"code\":\"${p}HUACALES\",\"kind\":\"zone\"")
            var crateNo = 0
            for (gy in 0 until 6) for (gx in 0 until 4) {
                if (gx * 6 + gy == 5 || gx * 6 + gy == 14) continue
                crateNo++
                val len = 2.0 + rnd.nextDouble() * 3.2
                val wid = 1.2 + rnd.nextDouble() * 1.8
                val hei = 1.2 + rnd.nextDouble() * 1.4
                val cx = ox + 84 + gx * 6.0 + 3.0
                val cy = oy + 12 + gy * (22.0 / 6) + (22.0 / 12)
                val wood = CLASSES[4]
                units.poly(
                    rotRect(cx, cy, len, wid, -6 + rnd.nextDouble() * 12),
                    unitProps("${p}H1-${crateNo.toString().padStart(2, '0')}", "${p}H1", "crate", wood, 0.0, hei)
                )
            }

            // --- Cunas de tubos de 12.2 m: 8 cunas, 3 a 6 camas de 6 tubos ---
            zones.poly(rect(ox + 2, oy + 37.5, ox + 36, oy + 60), "\"code\":\"${p}T1\",\"kind\":\"tubes\"")
            labels.point(ox + 19, oy + 61.4, "\"code\":\"${p}TUBOS\",\"kind\":\"zone\"")
            var cradleNo = 0
            for (i in 0 until 2) for (j in 0 until 4) {
                cradleNo++
                val x0 = ox + 4 + i * 17.5
                val y0 = oy + 39.5 + j * 5.2
                val layers = 3 + rnd.nextInt(4)
                for (layer in 0 until layers) for (q in 0 until 6) {
                    val base = layer * 0.34 + 0.1
                    units.poly(
                        rect(x0 + 0.1, y0 + 0.1 + q * 0.4, x0 + 12.3, y0 + 0.1 + q * 0.4 + 0.34),
                        unitProps("${p}T1-$cradleNo", "${p}T1", "pipe", CLASSES[3], base, base + 0.32)
                    )
                }
            }

            // --- Áreas de lote (lógicas) con bultos mixtos dentro ---
            val lotAreas = arrayOf(
                doubleArrayOf(44.0, 40.0, 60.0, 40.0, 62.0, 50.0, 46.0, 52.0),
                doubleArrayOf(64.0, 40.0, 82.0, 40.0, 82.0, 52.0, 66.0, 54.0),
                doubleArrayOf(44.0, 55.0, 60.0, 55.0, 60.0, 66.0, 44.0, 66.0),
                doubleArrayOf(64.0, 57.0, 82.0, 57.0, 82.0, 67.0, 64.0, 67.0)
            )
            for ((idx, poly) in lotAreas.withIndex()) {
                val code = "${p}L${idx + 1}"
                val shifted = DoubleArray(poly.size) { k -> if (k % 2 == 0) ox + poly[k] else oy + poly[k] }
                zones.poly(shifted, "\"code\":\"$code\",\"kind\":\"lots\"")
                var cx = 0.0
                var cy = 0.0
                for (k in 0 until 4) { cx += shifted[k * 2] / 4; cy += shifted[k * 2 + 1] / 4 }
                labels.point(cx, cy, "\"code\":\"LOTE ${4580 + idx}\",\"kind\":\"zone\"")
                val n = 7 + rnd.nextInt(6)
                for (b in 0 until n) {
                    val bx = cx + (rnd.nextDouble() - 0.5) * 9
                    val by = cy + (rnd.nextDouble() - 0.5) * 6
                    val cls = CLASSES[rnd.nextInt(CLASSES.size)]
                    units.poly(
                        rotRect(bx, by, 0.6 + rnd.nextDouble() * 1.1, 0.6 + rnd.nextDouble() * 0.9, rnd.nextDouble() * 90),
                        unitProps("$code-${b + 1}", code, "lot", cls, 0.0, 0.5 + rnd.nextDouble() * 0.9)
                    )
                }
            }

            // --- Pasillos (guías) ---
            aisles.line(doubleArrayOf(ox + 4, oy + 9.75, ox + 106, oy + 9.75))
            aisles.line(doubleArrayOf(ox + 6, oy + 22.76, ox + 62, oy + 22.76))
            aisles.line(doubleArrayOf(ox + 23.64, oy + 9.75, ox + 23.64, oy + 36))
            aisles.line(doubleArrayOf(ox + 43.08, oy + 9.75, ox + 43.08, oy + 36))
            aisles.line(doubleArrayOf(ox + 63, oy + 9.75, ox + 63, oy + 36))
            aisles.line(doubleArrayOf(ox + 83, oy + 9.75, ox + 83, oy + 36))
            labels.point(ox + 55, oy + 4.2, "\"code\":\"${p}ANDEN\",\"kind\":\"zone\"")
            labels.point(ox + 55, oy + -22, "\"code\":\"${p}PATIO\",\"kind\":\"zone\"")
            labels.point(ox + 99, oy + 55, "\"code\":\"${p}OFICINA\",\"kind\":\"zone\"")
        }

        // Ruta de ejemplo: puerta de andén 3 -> pasillo cruzado -> posición profunda del bloque P5.
        val route = listOf(
            doubleArrayOf(14.0, 3.5), doubleArrayOf(14.0, 9.75), doubleArrayOf(23.64, 9.75),
            doubleArrayOf(23.64, 22.76), doubleArrayOf(33.36, 22.76), doubleArrayOf(33.36, 26.0)
        )

        val zonesJson = zones.finish()
        val slotsJson = slots.finish()
        val wallsJson = walls.finish()
        val unitsJson = units.finish()
        val aislesJson = aisles.finish()
        val labelsJson = labels.finish()
        val bytes = (zonesJson.length + slotsJson.length + wallsJson.length + unitsJson.length +
            aislesJson.length + labelsJson.length).toLong()

        return SceneData(
            zones = zonesJson, slots = slotsJson, walls = wallsJson, units = unitsJson,
            aisles = aislesJson, labels = labelsJson, route = route,
            bounds = doubleArrayOf(minX, minY, maxX, maxY),
            palletZones = palletZones,
            stats = SceneStats(units.n, slots.n, zones.n, walls.n, labels.n, bytes)
        )
    }

    private fun mix(a: String, b: String, t: Double): String {
        fun ch(s: String, i: Int) = s.substring(1 + i * 2, 3 + i * 2).toInt(16)
        val out = StringBuilder("#")
        for (i in 0..2) {
            val v = (ch(a, i) + (ch(b, i) - ch(a, i)) * t).toInt().coerceIn(0, 255)
            out.append(v.toString(16).padStart(2, '0'))
        }
        return out.toString()
    }
}
