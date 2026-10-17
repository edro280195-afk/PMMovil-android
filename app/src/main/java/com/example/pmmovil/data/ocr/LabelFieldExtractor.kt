package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.data.scanner.StructuredLabelBarcodeParser
import java.util.Locale

/**
 * Extrae datos explícitos de etiquetas, conserva su procedencia y separa los
 * valores contradictorios. La lectura propone; el revisador confirma.
 * No depende de Android para poder comprobar regresiones con etiquetas reales.
 */
object LabelFieldExtractor {

    enum class Campo {
        LOTE, NUMERO_PARTE, NUMERO_SERIE, CANTIDAD, PIEZAS_POR_EMPAQUE,
        PAIS_ORIGEN, CADUCIDAD, UN_HAZMAT, CLASE_HAZMAT, GRUPO_EMBALAJE,
        MARCA, MODELO
    }

    enum class Origen {
        ANCLA_MISMA_LINEA, ANCLA_LINEA_SIGUIENTE, GS1, ROMBO_HAZMAT,
        STRUCTURED_BARCODE, KNOWN_BRAND,

        /** Un código de barras simple, sin nombre de campo, que el [LabelBarcodeInterpreter] supo ubicar. */
        CODIGO_PLANO
    }

    /** Un ordinal identifica un empaque; no representa cantidad de factura. */
    enum class QuantityScope { UNSPECIFIED, PACKAGE_CONTENT, PACKAGE_ORDINAL }

    data class CampoLeido(
        val campo: Campo,
        val valor: String,
        val origen: Origen,
        val ancla: String,
        val confianza: Int,
        val rawValue: String = valor,
        val rawCode: String? = null,
        val quantityScope: QuantityScope = QuantityScope.UNSPECIFIED,
        val packageTotal: String? = null
    )

    data class FieldConflict(val field: Campo, val observations: List<CampoLeido>)

    data class ExtractionResult(
        /** Solo campos sin contradicciones; los ambiguos requieren resolución humana. */
        val fields: List<CampoLeido>,
        val observations: List<CampoLeido>,
        val conflicts: List<FieldConflict>
    )

    private val anchors: Map<Campo, List<String>> = mapOf(
        Campo.LOTE to listOf(
            "BATCH/LOT#", "BATCH/LOT", "LOT NUMBER", "LOT NO", "LOT #", "LOT#",
            "NO. DE LOTE", "NUM. LOTE", "NUMERO DE LOTE", "LOTE", "BATCH", "LOT"
        ),
        Campo.NUMERO_PARTE to listOf(
            "PART NUMBER", "PART NO", "PART #", "PART#", "PRODUCT CODE",
            "ITEM NO", "ITEM #", "ITEM#", "ITEM", "NO. PARTE", "NUM. PARTE", "NO PARTE", "P/N"
        ),
        Campo.NUMERO_SERIE to listOf(
            "SERIAL NUMBER", "SERIAL NO", "SERIAL #", "SERIAL", "S/N",
            "NO. SERIE", "NUM. SERIE", "NO SERIE"
        ),
        Campo.CANTIDAD to listOf("QUANTITY", "QTY", "CANTIDAD", "CANT"),
        Campo.PIEZAS_POR_EMPAQUE to listOf(
            "UNITS/PALLET", "UNITS PER PALLET", "PER BAG", "PER BOX", "PZS/CAJA", "PIEZAS POR"
        ),
        Campo.PAIS_ORIGEN to listOf(
            "COO", "COUNTRY OF ORIGIN", "MADE IN", "HECHO EN", "PAIS DE ORIGEN", "ORIGEN"
        ),
        Campo.CADUCIDAD to listOf(
            "EXP DATE", "EXP. DATE", "EXPIRY DATE", "EXPIRY", "EXP", "CADUCIDAD", "FECHA DE CADUCIDAD"
        ),
        Campo.CLASE_HAZMAT to listOf("HAZARD CLASS", "CLASE DE PELIGRO", "CLASE"),
        Campo.GRUPO_EMBALAJE to listOf("PACKING GROUP", "GRUPO DE EMBALAJE", "GRUPO EMBALAJE"),
        Campo.MARCA to listOf("BRAND", "MARCA", "MANUFACTURER", "FABRICANTE"),
        Campo.MODELO to listOf("MODEL", "MODELO", "MOD", "TYPE")
    )

    // También delimitan valores, aunque no sean campos que se vayan a capturar.
    private val boundaryAnchors = listOf(
        "ORDER #", "ORDER NO", "ORDER", "P/O", "PO", "DESCRIPTION", "CUSTOMER",
        "NET WGT", "NET WT", "GROSS WT", "UNIT WT", "REV"
    )

    // Se exige renglón completo: una marca mencionada en una dirección o una
    // descripción no demuestra quién fabricó la mercancía.
    private val knownBrandLines = listOf(
        "SKF", "BRIKKSEN", "ENERPAC", "VORTEX", "ADM", "ALLNEX", "LANXESS",
        "RTP", "BASF", "KAMIN", "PATCO", "FREUDENBERG", "IMERYS", "CELITE"
    ).associateWith { it } + mapOf("PATCO PRODUCTS" to "PATCO", "RTP COMPANY" to "RTP")

    private val unPattern = Regex("""\bUN\s*(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val ordinalPattern = Regex("""^(\d+)\s*(?:of|de)\s*(\d+)$""", RegexOption.IGNORE_CASE)
    private val loosePiecesPattern = Regex("""^(\d{1,8})\s*(PCS|PZS|PIEZAS|PIECES)\b""", RegexOption.IGNORE_CASE)
    private val quantityPattern = Regex("""^(\d+(?:[.,]\d+)?)(?:\s+[A-Za-z]{1,12})?$""")
    private val hazardClassPattern = Regex("""^[1-9](?:\.[1-9])?$""")
    private val printedAiPattern = Regex("""^\((\d{2,4})\)\s*""")

    /** Un punto pegado a un dígito es parte del número, no puntuación del ancla. */
    private val leadingDecimalFragment = Regex("""^\.+\d""")

    /** Compatibilidad con los consumidores actuales: nunca entrega un campo ambiguo. */
    fun extraer(textoOcr: String, codigos: List<String> = emptyList()): List<CampoLeido> =
        extractDetailed(textoOcr, codigos).fields

    /**
     * Conserva observaciones y conflictos para que la pantalla permita resolverlos.
     *
     * [barcodeObservations] son campos que otro lector (el [LabelBarcodeInterpreter]) ya ubicó con certeza a
     * partir de códigos de barras; [ignoreOcrFor] son los campos en los que el texto no debe opinar porque,
     * en esa etiqueta, el orden en que el OCR entrega los renglones no dice qué valor va con qué rótulo.
     */
    fun extractDetailed(
        text: String,
        codes: List<String> = emptyList(),
        barcodeObservations: List<CampoLeido> = emptyList(),
        ignoreOcrFor: Set<Campo> = emptySet()
    ): ExtractionResult {
        val observations = mutableListOf<CampoLeido>()
        codes.distinct().forEach { rawCode ->
            val code = BarcodeValueNormalizer.normalizeLabel(rawCode)
            observations += readGs1(code, rawCode)
            observations += readStructuredCode(code, rawCode)
        }
        observations += barcodeObservations

        val lines = text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        lines.forEachIndexed { index, line ->
            val lineAnchors = findAnchors(line)
            lineAnchors.forEachIndexed anchorLoop@{ anchorIndex, anchor ->
                val field = anchor.field ?: return@anchorLoop
                if (field in ignoreOcrFor) return@anchorLoop
                val end = lineAnchors.getOrNull(anchorIndex + 1)?.start ?: line.length
                val sameLine = cleanValue(line.substring(anchor.end, end))
                val nextLine = lines.getOrNull(index + 1)
                val rawValue = if (sameLine.isNotEmpty()) sameLine else {
                    // Si otro campo sigue en este renglón, su valor no está abajo.
                    if (anchorIndex != lineAnchors.lastIndex || nextLine == null) return@anchorLoop
                    val nextAnchors = findAnchors(nextLine)
                    if (nextAnchors.firstOrNull()?.start == 0) return@anchorLoop
                    cleanValue(nextLine.substring(0, nextAnchors.firstOrNull()?.start ?: nextLine.length))
                }
                readAnchoredValue(field, rawValue)?.let { parsed ->
                    observations += parsed.copy(
                        origen = if (sameLine.isNotEmpty()) Origen.ANCLA_MISMA_LINEA else Origen.ANCLA_LINEA_SIGUIENTE,
                        ancla = line.substring(anchor.start, anchor.end),
                        confianza = if (sameLine.isNotEmpty()) 90 else 70
                    )
                }
            }
            unPattern.findAll(line).forEach { match ->
                observations += CampoLeido(
                    Campo.UN_HAZMAT, match.groupValues[1], Origen.ROMBO_HAZMAT,
                    match.value, 95, rawValue = match.value
                )
            }
            loosePiecesPattern.find(line)?.let { match ->
                observations += CampoLeido(
                    Campo.PIEZAS_POR_EMPAQUE, match.groupValues[1], Origen.ANCLA_MISMA_LINEA,
                    match.value, 80, rawValue = match.value, quantityScope = QuantityScope.PACKAGE_CONTENT
                )
            }
            knownBrandLines[line.uppercase(Locale.ROOT)]?.let { brand ->
                observations += CampoLeido(Campo.MARCA, brand, Origen.KNOWN_BRAND, line, 80, rawValue = line)
            }
        }

        val distinctObservations = observations.distinct()
        val fields = mutableListOf<CampoLeido>()
        val conflicts = mutableListOf<FieldConflict>()
        distinctObservations.groupBy { it.campo }.forEach { (field, candidates) ->
            val alternatives = candidates.groupBy(::comparisonKey)
            if (alternatives.size > 1) {
                conflicts += FieldConflict(field, candidates)
            } else {
                fields += candidates.maxBy { it.confianza }
            }
        }
        return ExtractionResult(fields, distinctObservations, conflicts)
    }

    private data class AnchorMatch(val field: Campo?, val start: Int, val end: Int)

    private fun findAnchors(line: String): List<AnchorMatch> {
        val matches = mutableListOf<AnchorMatch>()
        val definitions = anchors.flatMap { (field, values) -> values.map { field to it } } +
            boundaryAnchors.map { null to it }
        definitions.forEach { (field, value) ->
            var from = 0
            while (from < line.length) {
                val start = line.indexOf(value, from, ignoreCase = true)
                if (start < 0) break
                val end = start + value.length
                if ((start == 0 || !line[start - 1].isLetterOrDigit()) &&
                    (end == line.length || !line[end].isLetterOrDigit())) {
                    matches += AnchorMatch(field, start, end)
                }
                from = start + 1
            }
        }
        val accepted = mutableListOf<AnchorMatch>()
        matches.sortedWith(compareBy<AnchorMatch> { it.start }.thenByDescending { it.end - it.start })
            .forEach { match ->
                if (accepted.none { match.start < it.end && match.end > it.start }) accepted += match
            }
        return accepted
    }

    // Quitar el punto inicial de ".3" lo dejaria en la clase 3, cuando en realidad es un
    // 4.3 o 5.3 al que el OCR le comio el primer digito. Se conserva para que no pase.
    private fun cleanValue(raw: String): String {
        val head = raw.trim().trimStart(':', '#', '-', '=', ' ', '\u00A0')
        if (leadingDecimalFragment.containsMatchIn(head)) return head.trimEnd(':', ',', ';', '.', ' ')
        return raw.trim()
            .trimStart(':', '#', '-', '=', '.', ' ', '\u00A0')
            .trim()
            .trimEnd(':', ',', ';', '.', ' ')
    }

    private fun readAnchoredValue(field: Campo, raw: String): CampoLeido? {
        var value = raw
        printedAiPattern.find(value)?.let { marker ->
            if (gs1Specs[marker.groupValues[1]]?.field == field) value = value.substring(marker.range.last + 1).trim()
        }
        if (value.isBlank() || value.length > 60 || value.none(Char::isLetterOrDigit)) return null
        var scope = if (field == Campo.PIEZAS_POR_EMPAQUE) QuantityScope.PACKAGE_CONTENT else QuantityScope.UNSPECIFIED
        var packageTotal: String? = null
        when (field) {
            Campo.CANTIDAD, Campo.PIEZAS_POR_EMPAQUE -> {
                val ordinal = ordinalPattern.matchEntire(value)
                if (ordinal != null) {
                    val position = ordinal.groupValues[1].toLongOrNull() ?: return null
                    val total = ordinal.groupValues[2].toLongOrNull() ?: return null
                    if (position <= 0 || total < position) return null
                    value = ordinal.groupValues[1]
                    packageTotal = ordinal.groupValues[2]
                    scope = QuantityScope.PACKAGE_ORDINAL
                } else {
                    value = quantityPattern.matchEntire(value)?.groupValues?.get(1) ?: return null
                }
            }
            Campo.CLASE_HAZMAT -> if (!hazardClassPattern.matches(value)) return null
            Campo.GRUPO_EMBALAJE -> {
                value = value.uppercase(Locale.ROOT)
                if (value !in setOf("I", "II", "III")) return null
            }
            Campo.PAIS_ORIGEN -> if (value.length !in 2..40 || value.none(Char::isLetter)) return null
            Campo.LOTE, Campo.NUMERO_PARTE, Campo.NUMERO_SERIE ->
                if (value.length < 3 || value.none(Char::isDigit)) return null
            Campo.MARCA, Campo.MODELO -> if (value.length > 40) return null
            else -> Unit
        }
        return CampoLeido(field, value, Origen.ANCLA_MISMA_LINEA, "", 90, rawValue = raw, quantityScope = scope, packageTotal = packageTotal)
    }

    /** «0049263333» y «49263333» son la misma clave: los ceros a la izquierda son relleno del proveedor. */
    private fun sinCerosSiNumerico(valor: String): String =
        if (valor.length > 1 && valor.all { it in '0'..'9' }) valor.trimStart('0').ifEmpty { "0" } else valor

    private fun comparisonKey(observation: CampoLeido): String {
        val value = when (observation.campo) {
            Campo.NUMERO_PARTE -> sinCerosSiNumerico(observation.valor)
            Campo.MARCA, Campo.PAIS_ORIGEN, Campo.GRUPO_EMBALAJE -> observation.valor.uppercase(Locale.ROOT)
            Campo.CANTIDAD, Campo.PIEZAS_POR_EMPAQUE -> observation.valor.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: observation.valor
            else -> observation.valor
        }
        return "${observation.quantityScope}|${observation.packageTotal}|$value"
    }

    private fun readStructuredCode(code: String, rawCode: String): List<CampoLeido> {
        val parsed = StructuredLabelBarcodeParser.parse(code) ?: return emptyList()
        return listOfNotNull(
            parsed.articulo?.let { CampoLeido(Campo.NUMERO_PARTE, it, Origen.STRUCTURED_BARCODE, "+30S", 99, rawCode = rawCode) },
            parsed.lote?.let { CampoLeido(Campo.LOTE, it, Origen.STRUCTURED_BARCODE, "+H", 99, rawCode = rawCode) },
            parsed.rawQuantity?.let { CampoLeido(Campo.CANTIDAD, it, Origen.STRUCTURED_BARCODE, "+Q", 99, rawCode = rawCode) },
            parsed.fecha?.let { CampoLeido(Campo.CADUCIDAD, fechaImpresa(it), Origen.STRUCTURED_BARCODE, "+2D", 99, rawValue = it, rawCode = rawCode) }
        )
    }

    /** `092036` es septiembre de 2036; `16102026` es el 16 de octubre de 2026. Otra forma se deja tal cual. */
    private fun fechaImpresa(valor: String): String = when {
        valor.length == 6 && valor.all { it in '0'..'9' } -> "${valor.substring(0, 2)}/${valor.substring(2)}"
        valor.length == 8 && valor.all { it in '0'..'9' } -> "${valor.substring(0, 2)}/${valor.substring(2, 4)}/${valor.substring(4)}"
        else -> valor
    }

    private data class Gs1Spec(val field: Campo? = null, val fixedLength: Int? = null, val maxLength: Int, val numeric: Boolean = false)

    // Diccionario explícito. Un AI desconocido provoca rechazo: no se adivinan
    // límites de campos variables buscando números dentro del lote o la serie.
    private val gs1Specs: Map<String, Gs1Spec> = buildMap {
        put("00", Gs1Spec(fixedLength = 18, maxLength = 18, numeric = true))
        listOf("01", "02").forEach { put(it, Gs1Spec(fixedLength = 14, maxLength = 14, numeric = true)) }
        put("10", Gs1Spec(Campo.LOTE, maxLength = 20))
        listOf("11", "12", "13", "15", "16", "17").forEach {
            put(it, Gs1Spec(if (it == "17") Campo.CADUCIDAD else null, 6, 6, true))
        }
        put("20", Gs1Spec(fixedLength = 2, maxLength = 2, numeric = true))
        put("21", Gs1Spec(Campo.NUMERO_SERIE, maxLength = 20))
        put("22", Gs1Spec(maxLength = 20))
        put("30", Gs1Spec(Campo.CANTIDAD, maxLength = 8, numeric = true))
        put("37", Gs1Spec(Campo.PIEZAS_POR_EMPAQUE, maxLength = 8, numeric = true))
        listOf("240", "241").forEach { put(it, Gs1Spec(Campo.NUMERO_PARTE, maxLength = 30)) }
        listOf("250", "251", "400").forEach { put(it, Gs1Spec(maxLength = 30)) }
        (410..417).forEach { put(it.toString(), Gs1Spec(fixedLength = 13, maxLength = 13, numeric = true)) }
        (90..99).forEach { put(it.toString(), Gs1Spec(maxLength = 30)) }
        (3100..3169).forEach { put(it.toString(), Gs1Spec(fixedLength = 6, maxLength = 6, numeric = true)) }
    }

    private val aiMarkerPattern = Regex("""\((\d{2,4})\)""")
    private val gs1AimPrefixes = setOf("]C1", "]d2", "]Q3")

    private fun readGs1(code: String, rawCode: String): List<CampoLeido> {
        val hasAim = gs1AimPrefixes.any { code.startsWith(it) }
        val payload = if (hasAim) code.substring(3) else code
        if (payload.startsWith("(")) return readParenthesizedGs1(payload, rawCode)
        // Un código simple que empieza por 10 no demuestra que sea GS1. Sin AIM
        // ni GS se exige el encabezado fijo de GTIN o SSCC y otra observación.
        if (!hasAim && '\u001D' !in payload && !payload.startsWith("01") && !payload.startsWith("00")) return emptyList()
        return readDelimitedGs1(payload, rawCode)
    }

    private fun readParenthesizedGs1(payload: String, rawCode: String): List<CampoLeido> {
        val markers = aiMarkerPattern.findAll(payload).toList()
        if (markers.firstOrNull()?.range?.first != 0) return emptyList()
        val results = mutableListOf<CampoLeido>()
        markers.forEachIndexed { index, marker ->
            val ai = marker.groupValues[1]
            val spec = gs1Specs[ai] ?: return emptyList()
            val end = markers.getOrNull(index + 1)?.range?.first ?: payload.length
            val value = payload.substring(marker.range.last + 1, end).trim().trim('\u001D').trim()
            if (!isValidGs1Value(value, spec)) return emptyList()
            spec.field?.let { results += gs1Observation(it, ai, value, rawCode) }
        }
        return results
    }

    private fun readDelimitedGs1(payload: String, rawCode: String): List<CampoLeido> {
        val results = mutableListOf<CampoLeido>()
        var position = 0
        while (position < payload.length) {
            if (payload[position] == '\u001D') { position++; continue }
            val ai = listOf(4, 3, 2).firstNotNullOfOrNull { length ->
                payload.substring(position, (position + length).coerceAtMost(payload.length))
                    .takeIf { it.length == length && gs1Specs.containsKey(it) }
            } ?: return emptyList()
            val spec = gs1Specs.getValue(ai)
            position += ai.length
            val end = if (spec.fixedLength != null) position + spec.fixedLength else {
                payload.indexOf('\u001D', position).takeIf { it >= 0 } ?: payload.length
            }
            if (end > payload.length) return emptyList()
            val value = payload.substring(position, end)
            if (!isValidGs1Value(value, spec)) return emptyList()
            spec.field?.let { results += gs1Observation(it, ai, value, rawCode) }
            position = end
        }
        return results
    }

    private fun isValidGs1Value(value: String, spec: Gs1Spec): Boolean =
        value.isNotEmpty() && value.length <= spec.maxLength &&
            (spec.fixedLength == null || value.length == spec.fixedLength) &&
            value.none(Char::isISOControl) && (!spec.numeric || value.all { it in '0'..'9' })

    private fun gs1Observation(field: Campo, ai: String, value: String, rawCode: String) = CampoLeido(
        field, value, Origen.GS1, "($ai)", 99, rawCode = rawCode,
        quantityScope = if (field == Campo.PIEZAS_POR_EMPAQUE) QuantityScope.PACKAGE_CONTENT else QuantityScope.UNSPECIFIED
    )
}
