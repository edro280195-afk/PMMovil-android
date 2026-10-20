package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.scanner.BarcodeValueNormalizer

/**
 * El identificador único que el proveedor imprime en cada empaque, para no contar dos veces el mismo.
 *
 * Solo reconoce las formas que se han visto en etiquetas reales de Freudenberg (entrada 01C263606600000, octubre
 * de 2026), una muestra de cada una:
 *  - **Bolsa de Taiwán:** un Code 128 «1Y-00000050337428», uno de los ocho códigos sin nombre de campo.
 *  - **Caja de México:** un Code 39 «S279239665», junto al PDF417 de seis campos.
 *
 * La bolsa de O-rings solo trae el PDF417 y no tiene identificador: ahí cada lectura es un empaque y el conteo
 * avisa si dos seguidas dicen exactamente lo mismo. Que estos identificadores sean únicos por empaque se probó con
 * **una sola** etiqueta de cada tipo; con más muestras se afina. Otra marca con otro formato se suma a [SHAPES].
 */
object PackageId {
    enum class Kind { BAG, BOX }

    data class Shape(val kind: Kind, val pattern: Regex)

    val SHAPES: List<Shape> = listOf(
        Shape(Kind.BAG, Regex("""^1Y-\d{8,20}$""")),
        Shape(Kind.BOX, Regex("""^S\d{6,12}$"""))
    )

    sealed interface Reading {
        /** Ningún código de la etiqueta tiene la forma de un identificador de empaque. */
        data object None : Reading

        data class Single(val value: String, val kind: Kind) : Reading

        /** Dos identificadores distintos a la vez: casi seguro hay dos etiquetas en el cuadro y no se sabe de cuál es cada dato. */
        data class Multiple(val values: List<String>) : Reading
    }

    fun read(codes: List<String>): Reading {
        val found = codes
            .map(BarcodeValueNormalizer::normalizeLabel)
            .mapNotNull { code -> SHAPES.firstOrNull { it.pattern.matches(code) }?.let { code to it.kind } }
            .distinctBy { it.first }
        return when (found.size) {
            0 -> Reading.None
            1 -> Reading.Single(found[0].first, found[0].second)
            else -> Reading.Multiple(found.map { it.first })
        }
    }
}
