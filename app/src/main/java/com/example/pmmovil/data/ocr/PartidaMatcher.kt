package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.model.PartidaRevisionDto
import java.util.Locale

/**
 * Propone una partida sin convertir similitudes de texto o cantidades en identidad.
 * Solo una clave exacta, propia o alias explícito, puede confirmar una identidad única.
 * La confirmación del revisador sigue siendo necesaria antes de guardar.
 */
object PartidaMatcher {
    enum class MatchKind {
        EXACT_PART_NUMBER,
        EXACT_CUSTOMER_ALIAS,
        NORMALIZED_NUMBER_SUGGESTION,
        DESCRIPTION_SUPPORT,
        SINGLE_PARTIDA_SUGGESTION
    }

    data class Candidate(
        val partida: PartidaRevisionDto,
        val reasons: List<String>,
        val exactKeyMatch: Boolean,
        val matchKind: MatchKind
    )

    data class MatchResult(
        val candidates: List<Candidate>,
        val suggestedPartidaId: Int?,
        val requiresSelection: Boolean
    )

    /**
     * [rawText] y la descripción solo aportan contexto. Se conservan signos y ceros
     * de las claves; quitar separadores sirve únicamente para sugerir alternativas.
     * No compara cantidades: sin unidad y alcance de empaque serían engañosas.
     */
    fun match(
        partidas: List<PartidaRevisionDto>,
        fields: List<LabelFieldExtractor.CampoLeido>,
        rawText: String = ""
    ): MatchResult {
        if (partidas.isEmpty()) return MatchResult(emptyList(), null, false)

        val partNumbers = fields
            .filter { it.campo == LabelFieldExtractor.Campo.NUMERO_PARTE }
            .map { canonicalKey(it.valor) }
            .filter(String::isNotEmpty)
            .distinct()
        // Los proveedores rellenan con ceros a la izquierda («0049263333») y el catálogo guarda la clave sin
        // ellos («49263333»). Se quitan solo los de la etiqueta y solo en claves puramente numéricas; el
        // catálogo nunca se toca, así que «12» de la etiqueta sigue sin igualar a «00012» del catálogo.
        val withoutPadding = partNumbers.mapNotNull(::withoutLeadingZeros)
        val hasConflictingNumbers = partNumbers.map { withoutLeadingZeros(it) ?: it }.distinct().size > 1
        val duplicateIds = partidas.groupingBy { it.IdRevdet }.eachCount().any { it.value > 1 }
        val rawWords = descriptionWords(rawText)

        val suggestions = partidas.mapNotNull { partida ->
            val catalogNumber = canonicalKey(partida.CnoParte.orEmpty())
            val customerAlias = canonicalKey(partida.ParteCte.orEmpty())
            val ownPadded = catalogNumber.isNotEmpty() && catalogNumber !in partNumbers && catalogNumber in withoutPadding
            val aliasPadded = customerAlias.isNotEmpty() && customerAlias !in partNumbers && customerAlias in withoutPadding
            val ownExact = catalogNumber.isNotEmpty() && (catalogNumber in partNumbers || catalogNumber in withoutPadding)
            val aliasExact = customerAlias.isNotEmpty() && (customerAlias in partNumbers || customerAlias in withoutPadding)
            val relaxed = partNumbers.any { number ->
                listOf(catalogNumber, customerAlias).any { key ->
                    key.isNotEmpty() && relaxedKey(key) == relaxedKey(number)
                }
            }
            val codeInRawText = listOf(catalogNumber, customerAlias).any { key ->
                key.isNotEmpty() && containsWholeKey(rawText, key)
            }
            val commonWords = descriptionWords(partida.cDescripcion.orEmpty()).intersect(rawWords)
            val descriptionSupport = codeInRawText || commonWords.size >= 2
            val kind = when {
                ownExact -> MatchKind.EXACT_PART_NUMBER
                aliasExact -> MatchKind.EXACT_CUSTOMER_ALIAS
                relaxed -> MatchKind.NORMALIZED_NUMBER_SUGGESTION
                descriptionSupport -> MatchKind.DESCRIPTION_SUPPORT
                partidas.size == 1 -> MatchKind.SINGLE_PARTIDA_SUGGESTION
                else -> return@mapNotNull null
            }
            val reasons = buildList {
                add(when (kind) {
                    MatchKind.EXACT_PART_NUMBER ->
                        if (ownPadded) "Número de parte igual a la clave de catálogo, sin los ceros a la izquierda de la etiqueta."
                        else "Número de parte igual a la clave de catálogo."
                    MatchKind.EXACT_CUSTOMER_ALIAS ->
                        if (aliasPadded) "Número de parte igual al alias del cliente, sin los ceros a la izquierda de la etiqueta."
                        else "Número de parte igual al alias explícito del cliente."
                    MatchKind.NORMALIZED_NUMBER_SUGGESTION -> "La clave solo coincide al quitar separadores; verifica los signos."
                    MatchKind.DESCRIPTION_SUPPORT -> "El texto aporta una pista; no verifica la identidad de la partida."
                    MatchKind.SINGLE_PARTIDA_SUGGESTION -> "Es la única partida disponible; su identidad no está verificada."
                })
                if (descriptionSupport && kind != MatchKind.DESCRIPTION_SUPPORT) {
                    add("También hay coincidencias en el texto de apoyo.")
                }
                if (hasConflictingNumbers) add("Se leyeron números de parte diferentes; selecciona la partida.")
                if (duplicateIds) add("La lista contiene identificadores de partida repetidos; verifica la información.")
                if (kind == MatchKind.SINGLE_PARTIDA_SUGGESTION && partNumbers.isNotEmpty()) {
                    add("La clave leída no coincide con la clave ni con el alias de esta partida.")
                }
            }
            Candidate(partida, reasons, exactKeyMatch = false, matchKind = kind)
        }.sortedWith(compareBy<Candidate> { it.matchKind.ordinal }.thenBy { it.partida.IdRevdet })

        val exactMatches = suggestions.filter { it.matchKind.isExact() }
        val uniqueExactKeyId = exactMatches.singleOrNull()?.partida?.IdRevdet
            ?.takeUnless { hasConflictingNumbers || duplicateIds }
        val candidates = suggestions.map { candidate ->
            candidate.copy(exactKeyMatch = candidate.partida.IdRevdet == uniqueExactKeyId)
        }
        return MatchResult(
            candidates = candidates,
            suggestedPartidaId = uniqueExactKeyId ?: candidates.singleOrNull()?.partida?.IdRevdet,
            requiresSelection = candidates.isNotEmpty() && uniqueExactKeyId == null
        )
    }

    private fun MatchKind.isExact(): Boolean =
        this == MatchKind.EXACT_PART_NUMBER || this == MatchKind.EXACT_CUSTOMER_ALIAS

    private fun canonicalKey(value: String): String =
        value.trim().uppercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun relaxedKey(value: String): String = value.replace(Regex("[\\s./_-]"), "")

    /** La clave sin ceros a la izquierda, o null si no los tiene o no es puramente numérica. */
    private fun withoutLeadingZeros(key: String): String? =
        if (key.length > 1 && key[0] == '0' && key.all { it in '0'..'9' }) key.trimStart('0').ifEmpty { "0" } else null

    private fun descriptionWords(value: String): Set<String> =
        Regex("[\\p{L}]{4,}").findAll(value.uppercase(Locale.ROOT)).map { it.value }.toSet()

    private fun containsWholeKey(rawText: String, key: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(key)}(?![\\p{L}\\p{N}])")
            .containsMatchIn(canonicalKey(rawText))
}
