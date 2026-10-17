package com.example.pmmovil.data.ocr

import kotlin.math.cos
import kotlin.math.sin

/**
 * La bolsa real de la partida línea 48 (clave 75183644), tal como la leyó el Pixel el 16-oct-2026:
 * los renglones de ML Kit con las esquinas de cada uno (arriba-izquierda, arriba-derecha, abajo-derecha,
 * abajo-izquierda del propio texto) y los códigos de barras decodificados. La etiqueta salió girada 90°.
 * El renglón «TAIWAN» impreso junto al código de origen no lo detectó el OCR.
 */
object BolsaFreudenbergFixture {
    fun linea(texto: String, vararg p: Float) =
        LineaDeTexto(texto, (0 until 4).map { EsquinaDeTexto(p[it * 2], p[it * 2 + 1]) })

    val lineas: List<LineaDeTexto> = listOf(
        linea("1Y-00000050337428", 304f, 719f, 307f, 855f, 292f, 855f, 289f, 719f),
        linea("QTY", 328f, 535f, 328f, 573f, 312f, 573f, 312f, 535f),
        linea("Batch", 341f, 1035f, 341f, 1094f, 324f, 1094f, 324f, 1035f),
        linea("200", 396f, 535f, 395f, 578f, 373f, 577f, 374f, 534f),
        linea("5003814158", 404f, 940f, 409f, 1081f, 384f, 1082f, 379f, 941f),
        linea("Description", 438f, 533f, 439f, 634f, 419f, 634f, 418f, 533f),
        linea("1.750 X 2.125 X0.212 WIPER 90", 464f, 533f, 467f, 714f, 448f, 714f, 445f, 533f),
        linea("origin", 441f, 1031f, 441f, 1085f, 420f, 1085f, 420f, 1031f),
        linea("Part Number", 512f, 533f, 512f, 642f, 497f, 642f, 497f, 533f),
        linea("Cure Date", 540f, 993f, 541f, 1084f, 523f, 1084f, 522f, 993f),
        linea("75183644", 582f, 533f, 582f, 657f, 560f, 657f, 560f, 533f),
        linea("P.O.NUMBER", 625f, 527f, 625f, 648f, 609f, 648f, 609f, 527f),
        linea("2Q26", 609f, 977f, 609f, 1039f, 585f, 1039f, 585f, 977f),
        linea("Vendor Number", 643f, 936f, 642f, 1087f, 624f, 1086f, 625f, 935f),
        linea("4505747759", 698f, 551f, 697f, 704f, 674f, 703f, 675f, 550f),
        linea("667", 710f, 1000f, 710f, 1042f, 687f, 1042f, 687f, 1000f)
    )

    /** Los ocho códigos de la etiqueta (la foto reducida solo deja salir seis; con ellos basta). */
    val codigos = listOf(
        "4505747759", "75183644", "200", "1Y-00000050337428", "667", "2Q26", "TAIWAN", "5003814158"
    )

    /** La misma etiqueta con la foto girada [grados]: las esquinas cambian de lugar, no de significado. */
    fun girada(origen: List<LineaDeTexto>, grados: Double): List<LineaDeTexto> {
        val c = cos(Math.toRadians(grados)).toFloat()
        val s = sin(Math.toRadians(grados)).toFloat()
        return origen.map { linea ->
            linea.copy(esquinas = linea.esquinas.map { EsquinaDeTexto(it.x * c - it.y * s, it.x * s + it.y * c) })
        }
    }
}
