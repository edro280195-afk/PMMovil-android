package com.example.pmmovil.mapeador.core

import java.util.Locale

/**
 * Formato de la bitácora `anclas-*.csv` del laboratorio de anclas. Lo lee `tools/anchors_analysis.py` por NOMBRE de
 * columna: si se cambia el orden o los nombres, hay que cambiar también ese script. Está aquí, en Kotlin puro, para
 * probar que la cabecera y cada fila cuadran (un desplazamiento de columnas estropearía todos los análisis sin avisar).
 */
object MarkerCsv {
    const val HEADER = "hora,ts_ns,familia,id,lado_cm,dist_m,z_m,angulo_deg,tam_px,reproj_px,ambiguedad," +
        "mundo_x,mundo_y,mundo_z,cam_x,cam_y,cam_z,real_m,etiqueta"

    /** Una fila por lectura. `realMeters` es la distancia real tecleada (null = no se sabe). */
    fun row(time: String, o: MarkerObservation, realMeters: Double?, tag: String): String {
        val w = o.worldCenter
        val c = o.cameraPosition
        val ambiguity = if (o.ambiguity.isFinite()) String.format(Locale.US, "%.2f", o.ambiguity) else ""
        val real = realMeters?.let { String.format(Locale.US, "%.3f", it) } ?: ""
        return String.format(
            Locale.US,
            "%s,%d,%s,%s,%.1f,%.4f,%.4f,%.2f,%.1f,%.3f,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s",
            time, o.timestampNs, o.family.name, quote(o.id), o.sideM * 100.0,
            o.distanceM, o.depthM, o.viewAngleDeg, o.sizePx, o.reprojectionPx, ambiguity,
            w.x, w.y, w.z, c.x, c.y, c.z, real, quote(tag)
        )
    }

    /** Celda de texto entre comillas, con las comillas internas duplicadas. */
    fun quote(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
