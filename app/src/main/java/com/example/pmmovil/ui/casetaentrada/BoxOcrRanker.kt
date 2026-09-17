package com.example.pmmovil.ui.casetaentrada

import android.util.Log
import com.example.pmmovil.data.model.CasetaEntradaOcrCandidate
import com.example.pmmovil.data.ocr.LocalTextOcrResult

internal enum class BoxShape {
    Numeric5,
    Numeric6,
    Numeric,
    AlphaNumeric7,
    AlphaNumeric,
    Unknown
}

internal object BoxOcrRanker {

    fun rank(result: LocalTextOcrResult?): List<CasetaEntradaOcrCandidate> {
        if (result == null) return emptyList()

        val barcodeCandidates = result.barcodes
            .map(CasetaEntradaRules::normalizeBoxCandidate)
            .filter { it.isNotBlank() }
            .filterNot { it in ignoredBoxWords }
            .filterNot(::isBoxNoise)
            .distinct()
            .map { value ->
                CasetaEntradaOcrCandidate(
                    value = value,
                    confidence = 1.0,
                    source = "barcode",
                    reason = "Código de barras (${describeBoxCandidate(value)})"
                )
            }

        val ranked = result.candidates
            .asSequence()
            .map(CasetaEntradaRules::normalizeBoxCandidate)
            .flatMap { boxCandidateVariants(it).asSequence() }
            .filter { it.isNotBlank() }
            .filterNot { it in ignoredBoxWords }
            .filterNot(::isBoxNoise)
            .distinct()
            .map { value -> value to scoreBoxCandidate(value) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .toList()

        val ocrCandidates = if (ranked.isEmpty()) {
            debugLog("BoxOcrRanker: entrada=${result.candidates.size} candidatos -> 0 resultado | raw=[${result.candidates.take(10).joinToString(", ")}]")
            emptyList()
        } else {
            val topScore = ranked.first().second
            ranked.map { (value, score) ->
                CasetaEntradaOcrCandidate(
                    value = value,
                    confidence = normalizeBoxConfidence(value, score, topScore),
                    source = "local",
                    reason = describeBoxCandidate(value)
                )
            }
        }

        val merged = (barcodeCandidates + ocrCandidates).distinctBy { it.value.uppercase() }
        debugLog("BoxOcrRanker: barcodes=${barcodeCandidates.size} ocr=${ocrCandidates.size} -> salida=${merged.size} | top=${merged.firstOrNull()?.value}(${merged.firstOrNull()?.confidence})")
        return merged
    }

    fun shape(value: String): BoxShape {
        if (value.isBlank()) return BoxShape.Unknown
        val onlyDigits = value.all(Char::isDigit)
        val onlyAlnum = value.all { it.isLetterOrDigit() || it == '-' }
        return when {
            onlyDigits && value.length == 5 -> BoxShape.Numeric5
            onlyDigits && value.length == 6 -> BoxShape.Numeric6
            onlyDigits -> BoxShape.Numeric
            onlyAlnum && value.length == 7 && value.any(Char::isLetter) && value.any(Char::isDigit) -> BoxShape.AlphaNumeric7
            onlyAlnum -> BoxShape.AlphaNumeric
            else -> BoxShape.Unknown
        }
    }

    fun isNoise(value: String): Boolean = isBoxNoise(value)

    fun describe(value: String): String = describeBoxCandidate(value)

    fun score(value: String): Int = scoreBoxCandidate(value)

    private val ignoredBoxWords = setOf(
        "CAJA",
        "TRAILER",
        "PLACAS",
        "PLACA",
        "PMM",
        "STOP",
        "CAUTION",
        "DANGER",
        "WARNING"
    )

    private val boxBrandBlacklist = setOf(
        "UTILITY",
        "WABASH",
        "GREATDANE",
        "HYUNDAI",
        "STOUGHTON",
        "VANGUARD",
        "CIMC",
        "MAERSK",
        "CRYOTEC",
        "THERMOKING",
        "CARGOKING"
    )

    private val boxModelBlacklist = setOf(
        "3000R",
        "C-600",
        "C600",
        "VS2DX",
        "DURAPLATE",
        "DOUBLE",
        "DROP",
        "DECK"
    )

    private val boxDimensionBlacklist = setOf(
        "53",
        "53FT",
        "48FT",
        "48",
        "45FT",
        "45"
    )

    private val boxWarningPattern = Regex(
        "(STICKER|ADVERTENCIA|WARNING|DANGER|CAUTION|MAX|MIN|PRESSURE|TEMP|TEMPERATURE|KEEP|CUIDADO|PELIGRO|RESBAL|FRAGIL|FRAGILE|HANDLE|WITH CARE)"
    )

    private fun isBoxNoise(value: String): Boolean {
        if (value.isBlank()) return true
        val compact = value.uppercase()
        if (compact in boxBrandBlacklist) return true
        if (compact in boxModelBlacklist) return true
        if (compact in boxDimensionBlacklist) return true
        if (boxWarningPattern.containsMatchIn(compact)) return true
        // Permite números de caja cortos de 3 dígitos (ej. "014"), pero solo si son
        // puramente numéricos: un alfanumérico de 3 (ej. "STO") sí es ruido.
        if (compact.length < 3) return true
        if (compact.length == 3 && !compact.all(Char::isDigit)) return true
        if (compact.any { it == '\'' || it == '"' }) return true
        return false
    }

    private fun describeBoxCandidate(value: String): String = when (shape(value)) {
        BoxShape.Numeric5 -> "5 digitos vertical"
        BoxShape.Numeric6 -> "6 digitos horizontal"
        BoxShape.Numeric -> "Solo digitos"
        BoxShape.AlphaNumeric7 -> "Alfanumerico 7"
        BoxShape.AlphaNumeric -> "Alfanumerico"
        BoxShape.Unknown -> "Sin forma clara"
    }

    private fun scoreBoxCandidate(value: String): Int {
        var score = when (shape(value)) {
            BoxShape.Numeric5 -> 8
            BoxShape.Numeric6 -> 7
            BoxShape.AlphaNumeric7 -> 9
            BoxShape.AlphaNumeric -> 4
            BoxShape.Numeric -> 4
            BoxShape.Unknown -> 0
        }
        if (value.length in 3..12) score += 2
        if (value.count(Char::isDigit) >= 2) score += 1
        if (value.any(Char::isLetter)) score += 1
        if ('-' in value) score += 1
        if (isBoxNoise(value)) score -= 10
        return score
    }

    private fun normalizeBoxConfidence(value: String, score: Int, topScore: Int): Double {
        val base = when (shape(value)) {
            BoxShape.AlphaNumeric7 -> 0.9
            BoxShape.Numeric5 -> 0.86
            BoxShape.Numeric6 -> 0.84
            BoxShape.AlphaNumeric -> 0.7
            BoxShape.Numeric -> 0.65
            BoxShape.Unknown -> 0.45
        }
        val ratio = if (topScore <= 0) 1.0 else (score.toDouble() / topScore.toDouble()).coerceIn(0.4, 1.0)
        return (base * ratio).coerceIn(0.2, 0.98)
    }

    private fun boxCandidateVariants(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val variants = linkedSetOf(value)
        val digitVariant = value.map { char ->
            when (char) {
                'O', 'Q', 'D' -> '0'
                'I', 'L' -> '1'
                'S' -> '5'
                'B' -> '8'
                'Z' -> '2'
                else -> char
            }
        }.joinToString("")

        if (
            digitVariant != value &&
            digitVariant.length in 3..8 &&
            digitVariant.all(Char::isDigit)
        ) {
            variants += digitVariant
        }
        return variants.toList()
    }

    private fun debugLog(message: String) {
        runCatching { Log.d("OCR_RANK", message) }
    }
}
