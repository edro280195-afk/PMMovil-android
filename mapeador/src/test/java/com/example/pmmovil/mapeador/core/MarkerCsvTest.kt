package com.example.pmmovil.mapeador.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** El CSV de anclas lo lee `tools/anchors_analysis.py` por nombre de columna: cabecera y filas tienen que cuadrar. */
class MarkerCsvTest {
    private val k = Intrinsics(921.2, 922.1, 641.3, 359.6, 1280, 720)

    /** Lector de una línea CSV con comillas (las comillas internas van duplicadas). */
    private fun parse(line: String): List<String> {
        val out = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                inQuotes && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    cell.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    out.add(cell.toString())
                    cell.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        out.add(cell.toString())
        return out
    }

    private fun observation(id: String, ambiguity: Double): MarkerObservation {
        val worldFromGl = Se3.rotationAboutY(10.0, Vec3(0.5, 1.5, 0.0))
        val cvFromMarker = Se3(Se3.rotationAboutX(180.0).r, Vec3(0.3, -0.1, 4.0))
        val corners = MarkerGeometry.project(cvFromMarker, 0.20, k)!!
        return MarkerObservation(
            123456789L, MarkerFamily.APRILTAG_36H11, id, 0.20, corners, cvFromMarker,
            MarkerGeometry.worldFromMarker(worldFromGl, cvFromMarker), worldFromGl.t, 0.31, ambiguity
        )
    }

    @Test
    fun headerAndRowHaveTheSameColumnsAndTheValuesLandInTheirOwn() {
        val header = MarkerCsv.HEADER.split(",")
        val o = observation("7", 1.8)
        val cells = parse(MarkerCsv.row("12:00:00.000", o, 4.0, "pasillo A"))
        assertEquals(header.size, cells.size)

        fun cell(name: String) = cells[header.indexOf(name)]
        assertEquals("12:00:00.000", cell("hora"))
        assertEquals("123456789", cell("ts_ns"))
        assertEquals("APRILTAG_36H11", cell("familia"))
        assertEquals("7", cell("id"))
        assertEquals("20.0", cell("lado_cm"))
        assertEquals(o.distanceM, cell("dist_m").toDouble(), 1e-4)
        assertEquals(4.0, cell("z_m").toDouble(), 1e-4)
        assertEquals(o.viewAngleDeg, cell("angulo_deg").toDouble(), 0.01)
        assertEquals(o.sizePx, cell("tam_px").toDouble(), 0.05)
        assertEquals("0.310", cell("reproj_px"))
        assertEquals("1.80", cell("ambiguedad"))
        assertEquals(o.worldCenter.x, cell("mundo_x").toDouble(), 1e-4)
        assertEquals(o.worldCenter.y, cell("mundo_y").toDouble(), 1e-4)
        assertEquals(o.worldCenter.z, cell("mundo_z").toDouble(), 1e-4)
        assertEquals(0.5, cell("cam_x").toDouble(), 1e-4)
        assertEquals(1.5, cell("cam_y").toDouble(), 1e-4)
        assertEquals("4.000", cell("real_m"))
        assertEquals("pasillo A", cell("etiqueta"))
    }

    @Test
    fun unknownRealDistanceAndInfiniteAmbiguityLeaveTheirCellsEmpty() {
        val header = MarkerCsv.HEADER.split(",")
        val cells = parse(MarkerCsv.row("12:00:00.000", observation("7", Double.POSITIVE_INFINITY), null, ""))
        assertEquals(header.size, cells.size)
        assertEquals("", cells[header.indexOf("real_m")])
        assertEquals("", cells[header.indexOf("ambiguedad")])
        assertEquals("", cells[header.indexOf("etiqueta")])
    }

    @Test
    fun anIdWithCommasAndQuotesStaysInOneCell() {
        val header = MarkerCsv.HEADER.split(",")
        val tricky = "A,\"B\",C"
        val cells = parse(MarkerCsv.row("12:00:00.000", observation(tricky, 1.5), 2.0, "luz \"baja\", sol"))
        assertEquals(header.size, cells.size)
        assertEquals(tricky, cells[header.indexOf("id")])
        assertEquals("luz \"baja\", sol", cells[header.indexOf("etiqueta")])
    }

    @Test
    fun rowsAreIndependentOfTheDeviceLocale() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-MX"))
            val cells = parse(MarkerCsv.row("12:00:00.000", observation("7", 1.8), 4.5, ""))
            // Con coma decimal el CSV se rompería: tiene que seguir saliendo con punto.
            assertEquals(MarkerCsv.HEADER.split(",").size, cells.size)
            assertEquals("4.500", cells[MarkerCsv.HEADER.split(",").indexOf("real_m")])
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
