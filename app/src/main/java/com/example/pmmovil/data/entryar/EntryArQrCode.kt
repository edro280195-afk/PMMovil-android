package com.example.pmmovil.data.entryar

import com.example.pmmovil.data.scanner.LabelCodeShapes
import com.example.pmmovil.data.scanner.OfficialQr
import com.example.pmmovil.data.scanner.OfficialQrParser
import java.util.Locale

/**
 * Lectura de un código impreso en una etiqueta IMEX.
 *
 * El QR que imprime `ZebraZplBuilder` contiene `"<referencia> <ordinal>-<total>"`
 * para cada bulto y solo `"<referencia>"` para la etiqueta de carpeta. Por eso el
 * ordinal **no** es una identidad: es la posición dentro de un total declarado al
 * momento de imprimir. Si la entrada se reimprime con otro total, los códigos
 * viejos que siguen pegados en el piso dejan de corresponder.
 */
data class EntryArQrCode(
    /** Código normalizado tal como se compara contra `etiqueta_mcia.textoetiqueta`. */
    val normalized: String,
    /** Texto exacto que entregó el lector; se conserva para la auditoría. */
    val raw: String,
    val reference: String,
    /** Posición impresa. Informativa: nunca se usa como conteo. */
    val printedOrdinal: Int?,
    /** Total impreso en la etiqueta. Informativo: no prueba cuántos bultos hay. */
    val declaredTotal: Int?,
    /** La etiqueta de carpeta no es un bulto; identifica el expediente. */
    val isFolderLabel: Boolean
)

/**
 * Convierte una lectura cruda en un código comparable. No inventa referencias,
 * no completa ordinales y no deduce la ubicación: el QR no la trae.
 */
object EntryArQrCodeParser {
    private val bodyPattern = Regex("^([A-Z0-9][A-Z0-9_/-]{2,29})(?:\\s+(\\d{1,6})-(\\d{1,6}))?$")

    /** Mismo criterio de normalización que usa el servidor sobre `textoetiqueta`. */
    fun normalize(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value
            .replace(' ', ' ')
            .filterNot(Char::isISOControl)
            .trim()
            .replace(Regex("\\s+"), " ")
            .uppercase(Locale.ROOT)
    }

    /** Devuelve null cuando la lectura no tiene la forma de una etiqueta IMEX. */
    fun parse(raw: String?): EntryArQrCode? {
        val normalized = normalize(raw)
        if (normalized.isEmpty() || normalized.length > 120) return null
        // Una caja o una relación tienen forma de referencia, pero no son una entrada: sin esto el AR las pedía al
        // servidor como entradas y las rotulaba «No existe» aunque el código fuera válido.
        if (LabelCodeShapes.noEsEntrada(normalized)) return null
        // La etiqueta de carpeta de siempre trae la entrada y el cliente pegados: la entrada es lo único que se sigue.
        (OfficialQrParser.parseNormalized(normalized) as? OfficialQr.Carpeta)?.let { carpeta ->
            return EntryArQrCode(
                normalized = carpeta.entrada,
                raw = raw.orEmpty(),
                reference = carpeta.entrada,
                printedOrdinal = null,
                declaredTotal = null,
                isFolderLabel = true
            )
        }
        val match = bodyPattern.matchEntire(normalized) ?: return null
        val reference = match.groupValues[1]
        val ordinal = match.groupValues[2].toIntOrNull()
        val total = match.groupValues[3].toIntOrNull()
        // Un ordinal mayor al total o un total en cero son una lectura mutilada:
        // se rechaza en lugar de registrar un bulto que no se puede ubicar.
        if (ordinal != null && (total == null || total <= 0 || ordinal <= 0 || ordinal > total)) return null
        return EntryArQrCode(
            normalized = normalized,
            raw = raw.orEmpty(),
            reference = reference,
            printedOrdinal = ordinal,
            declaredTotal = total,
            isFolderLabel = ordinal == null
        )
    }
}
