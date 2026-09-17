package com.example.pmmovil.ui.casetaentrada

import com.example.pmmovil.data.model.CasetaEntradaOcrCandidate
import com.example.pmmovil.data.ocr.LocalTextOcrResult

internal object PlateOcrRanker {

    // Patrones mexicanos conocidos. NO se usan como filtro: sirven solo para dar un
    // pequeño bono de desempate y para describir/preservar el formato con guiones cuando
    // ML Kit realmente leyo los separadores. Cualquier placa plausible (US, estatal,
    // vanity, etc.) se acepta igual aunque no calce con ninguno de estos.
    private val trailerPlatePattern = Regex("^\\d{2}-[A-Z]{2}-\\d[A-Z]$") // ej. 52-AB-3C
    private val federalPlatePattern1 = Regex("^\\d{3}-[A-Z]{2}-\\d$") // ej. 123-AB-4
    private val federalPlatePattern2 = Regex("^\\d{2}-[A-Z]{2}-\\d{2}$") // ej. 12-AB-34
    private val statePlatePattern1 = Regex("^[A-Z]{2}-\\d{2}-\\d{3}$") // ej. NL-12-345
    private val statePlatePattern2 = Regex("^[A-Z]{2}-\\d{4}-[A-Z]$") // ej. TM-1234-A
    private val statePlatePattern3 = Regex("^[A-Z]{3}-\\d{2}-\\d{2}$") // ej. ABC-12-34
    private val statePlatePattern4 = Regex("^[A-Z]{3}-\\d{3}-[A-Z]$") // ej. ABC-123-A

    // Longitud plausible de una placa (sin contar guiones). Cubre placas cortas de 5 y
    // placas largas de hasta 8 caracteres.
    private val plausibleLengthRange = 5..8

    private val plateBlacklist = setOf(
        "STOP",
        "DANGER",
        "CAUTION",
        "WARNING",
        "PMM"
    )

    // Palabras que aparecen en el marco/placa pero NO son el numero (estado, tipo de
    // servicio, leyendas). Se filtran aunque traigan algun digito suelto.
    private val plateWordBlacklist = setOf(
        "MEXICO",
        "REMOLQUE",
        "TRANSPORTE",
        "FEDERAL",
        "NACIONAL",
        "PRIVADO",
        "PARTICULAR",
        "CARGA",
        "PUBLICO",
        "FRONTERIZO",
        "TAMAULIPAS",
        "TEXAS",
        "APPORTIONED",
        // Acronimos de autoridad/calcomania que van junto al numero en placas federales
        // (ej. "SCT 2021" se puede leer pegado como "SCT2021"). No son el numero de placa.
        "SCT",
        "SAF",
        "SAT"
    )

    fun rank(result: LocalTextOcrResult?): List<CasetaEntradaOcrCandidate> {
        if (result == null) return emptyList()

        val barcodeCandidates = result.barcodes
            .flatMap { plateCandidateVariants(it).asSequence() }
            .filter { it.hasLetterAndDigit() }
            .filterNot(::isPlateNoise)
            .distinct()
            .map { value ->
                CasetaEntradaOcrCandidate(
                    value = value,
                    confidence = 1.0,
                    source = "barcode",
                    reason = "Código de barras (${describePlateCandidate(value)})"
                )
            }

        val ranked = result.candidates
            .asSequence()
            .flatMap(::plateCandidateVariants)
            .filter { it.hasLetterAndDigit() }
            .filterNot(::isPlateNoise)
            .distinct()
            .map { value -> value to scorePlateCandidate(value) }
            .filter { it.second > 0 }
            // Colapsa lecturas del mismo numero que solo difieren en guiones/espaciado
            // (ej. "01-UT-8E" vs "01UT8E"): se queda la mejor puntuada por numero. Asi la
            // camara vota siempre sobre un unico candidato y se fija rapido y estable.
            .groupBy { compactKey(it.first) }
            .map { (_, group) -> group.maxByOrNull { it.second }!! }
            .sortedByDescending { it.second }

        val ocrCandidates = if (ranked.isEmpty()) {
            emptyList()
        } else {
            val topScore: Int = ranked.first().second
            ranked.map { entry: Pair<String, Int> ->
                val value: String = entry.first
                val score: Int = entry.second
                CasetaEntradaOcrCandidate(
                    value = value,
                    confidence = normalizePlateConfidence(value, score, topScore),
                    source = "local",
                    reason = describePlateCandidate(value)
                )
            }
        }

        return (barcodeCandidates + ocrCandidates).distinctBy { compactKey(it.value) }
    }

    fun normalizeSingle(value: String): String =
        plateCandidateVariants(value)
            .filter { it.hasLetterAndDigit() }
            .filterNot(::isPlateNoise)
            .distinct()
            .maxByOrNull(::scorePlateCandidate)
            .orEmpty()

    fun isNoise(value: String): Boolean = isPlateNoise(value)

    fun score(value: String): Int = scorePlateCandidate(value)

    fun isMexicanPlate(value: String): Boolean {
        return trailerPlatePattern.matches(value) ||
                federalPlatePattern1.matches(value) ||
                federalPlatePattern2.matches(value) ||
                statePlatePattern1.matches(value) ||
                statePlatePattern2.matches(value) ||
                statePlatePattern3.matches(value) ||
                statePlatePattern4.matches(value)
    }

    /**
     * Una placa "plausible" es cualquier texto alfanumerico con forma de placa: 5 a 8
     * caracteres (sin contar guiones), con al menos una letra y un digito, y que no sea
     * ruido. Deliberadamente NO exige un formato/pais especifico: placas US de remolque
     * (STG2722), estatales, federales, particulares y vanity califican por igual.
     */
    fun isPlausiblePlate(value: String): Boolean {
        if (isPlateNoise(value)) return false
        val compact = compactKey(value)
        if (compact.length !in plausibleLengthRange) return false
        if (!compact.all(Char::isLetterOrDigit)) return false
        return compact.any(Char::isLetter) && compact.any(Char::isDigit)
    }

    private fun compactKey(value: String): String =
        value.replace("-", "").uppercase()

    private fun String.hasLetterAndDigit(): Boolean =
        any(Char::isLetter) && any(Char::isDigit)

    private fun plateCandidateVariants(value: String): Sequence<String> = sequence {
        val normalized = CasetaEntradaRules.normalizePlateCandidate(value)
        if (normalized.isBlank()) return@sequence
        // Se conserva EXACTAMENTE lo que se leyo (con guiones si ML Kit los vio). No se
        // fabrican separadores: inventar guiones convertia placas US como STG2722 en
        // "STG-27-22" y corrompia el dato.
        yield(normalized)
    }

    private fun isPlateNoise(value: String): Boolean {
        if (value.isBlank()) return true
        val compact = value.uppercase()
        val letters = compact.replace(Regex("[^A-Z]"), "")
        if (compact in plateBlacklist) return true
        if (letters in plateWordBlacklist) return true
        if (compactKey(value).length < 5) return true
        if (compact.startsWith("STOP") || compact.startsWith("DANGER") || compact.startsWith("CAUTION")) return true
        return false
    }

    private fun describePlateCandidate(value: String): String = when {
        trailerPlatePattern.matches(value) -> "Placa remolque/federal (SCT)"
        federalPlatePattern1.matches(value) || federalPlatePattern2.matches(value) -> "Placa federal carga (SCT)"
        statePlatePattern1.matches(value) || statePlatePattern2.matches(value) -> "Placa estatal carga (Mex)"
        statePlatePattern3.matches(value) || statePlatePattern4.matches(value) -> "Placa particular (Mex)"
        isPlausiblePlate(value) -> "Placa detectada"
        else -> "Texto con forma de placa"
    }

    private fun scorePlateCandidate(value: String): Int {
        if (isPlateNoise(value)) return -100
        var score = 0
        val plausible = isPlausiblePlate(value)
        if (plausible) score += 6
        // Bono de desempate: si la lectura calza EXACTA con un patron mexicano con guiones,
        // gana a su gemela compacta y asi se conserva el formato oficial (ej. "01-UT-8E"
        // por encima de "01UT8E"). Nunca es requisito para aceptar la placa.
        if (isMexicanPlate(value)) score += 5
        val compactLen = compactKey(value).length
        score += when (compactLen) {
            6, 7 -> 3
            5, 8 -> 2
            else -> 0
        }
        if (value.count(Char::isDigit) in 1..5) score += 1
        if (value.count(Char::isLetter) in 1..5) score += 1
        return score
    }

    private fun normalizePlateConfidence(value: String, score: Int, topScore: Int): Double {
        val compactLen = compactKey(value).length
        val base = when {
            // Patron mexicano exacto: maxima confianza (comportamiento historico).
            isMexicanPlate(value) -> 0.96
            // Cualquier placa con forma valida (incluye US/estatal/vanity): alta confianza,
            // suficiente para que la camara se fije con 2 lecturas iguales igual que las
            // mexicanas. Se mantiene por debajo del atajo local (0.90) para que la foto
            // estatica siga pidiendo refuerzo al servidor de OCR.
            isPlausiblePlate(value) -> 0.88
            // Alfanumerico con forma dudosa (muy largo/corto): requiere mas votos.
            value.hasLetterAndDigit() && compactLen in 5..9 -> 0.78
            else -> 0.6
        }
        val ratio = if (topScore <= 0) 1.0 else (score.toDouble() / topScore.toDouble()).coerceIn(0.5, 1.0)
        return (base * ratio).coerceIn(0.2, 0.98)
    }
}
