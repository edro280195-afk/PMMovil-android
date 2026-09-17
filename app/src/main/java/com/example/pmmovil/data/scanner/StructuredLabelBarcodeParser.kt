package com.example.pmmovil.data.scanner

/**
 * Parser genérico para etiquetas de mercancía que codifican varios campos en
 * un solo código como segmentos "+PREFIJO valor" pegados sin separador entre
 * el prefijo y el valor (estilo GS1-128/ANSI MH10.8.2 con Data Identifiers).
 *
 * Ejemplo real (etiqueta de compuesto químico Freudenberg):
 *   "+30S0049106577+Q0049.200+H2613004019+2D16102026+2X565321082026+P"
 *   -> 30S = número de artículo, Q = cantidad, H = lote,
 *      2D = fecha de caducidad, 2X = número de contrato, P = sin dato visto.
 *
 * No es un parser GS1 completo: es un lector de un diccionario conocido de
 * prefijos ([KNOWN_PREFIXES]). Es deliberadamente genérico (no depende de
 * cliente/proveedor) para que cualquier otra empresa que use el mismo estilo
 * de etiqueta se beneficie sin tocar código -- basta con que el prefijo que
 * use ya esté en el diccionario, o agregar uno nuevo ahí.
 */
object StructuredLabelBarcodeParser {

    enum class FieldType { ARTICULO, CANTIDAD, LOTE, FECHA, CONTRATO, DESCONOCIDO }

    /** Del prefijo más largo al más corto -- evita que "2D"/"2X" se lean como "2" + resto. */
    private val KNOWN_PREFIXES: List<Pair<String, FieldType>> = listOf(
        "30S" to FieldType.ARTICULO,
        "2D" to FieldType.FECHA,
        "2X" to FieldType.CONTRATO,
        "H" to FieldType.LOTE,
        "Q" to FieldType.CANTIDAD,
        "P" to FieldType.DESCONOCIDO
    ).sortedByDescending { (prefijo, _) -> prefijo.length }

    data class ParsedLabel(
        val articulo: String? = null,
        val cantidad: Double? = null,
        val lote: String? = null
    )

    /**
     * Devuelve null si [raw] no tiene forma de etiqueta segmentada (no
     * empieza con "+" o no trae al menos 2 segmentos), o si ninguno de sus
     * segmentos trae un número de artículo reconocible -- así quien llama
     * puede caer de vuelta al comportamiento anterior (buscar el texto tal
     * cual) sin falsos positivos sobre códigos de barras simples.
     */
    fun parse(raw: String): ParsedLabel? {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("+")) return null

        val segments = trimmed.uppercase().split("+").filter(String::isNotBlank)
        if (segments.size < 2) return null

        var articulo: String? = null
        var cantidad: Double? = null
        var lote: String? = null

        for (segment in segments) {
            val (prefijo, tipo) = KNOWN_PREFIXES.firstOrNull { (prefijo, _) ->
                segment.startsWith(prefijo)
            } ?: continue
            val valor = segment.removePrefix(prefijo).trim()
            if (valor.isEmpty()) continue
            when (tipo) {
                FieldType.ARTICULO -> articulo = valor
                FieldType.CANTIDAD -> cantidad = valor.toDoubleOrNull()
                FieldType.LOTE -> lote = valor
                FieldType.FECHA, FieldType.CONTRATO, FieldType.DESCONOCIDO -> {}
            }
        }

        if (articulo == null) return null
        return ParsedLabel(articulo, cantidad, lote)
    }
}
