package com.example.pmmovil.data.entryar

import com.example.pmmovil.data.scanner.ScannedCode
import com.example.pmmovil.data.scanner.ScannedCodeClassifier

/** Qué clase de QR sigue el AR. Cada una tiene su propia tarjeta. */
enum class ArItemKind {
    /** Etiqueta de entrada (carpeta o bulto): tarjeta con revisión, facturas y partidas. */
    Entry,

    /** Caja de caseta: ficha compacta con La Línea. */
    Caja,

    /** Relación de salida: ficha compacta con La Línea. */
    Relacion
}

/** Un QR que el AR sabe seguir: de qué clase es y su identidad (la llave con la que se ancla y se consulta). */
data class ArCode(val kind: ArItemKind, val code: EntryArQrCode)

/**
 * Decide si una lectura es de un QR que el AR sigue. Una entrada sigue con su parser de siempre; una caja o una
 * relación se reconocen por su forma. Todo lo demás (un gafete, una etiqueta de producto, un pre-registro, texto
 * cualquiera) se descarta aquí, antes de anclar nada: el gafete lleva una credencial y no debe llegar a ninguna parte.
 */
object ArCodeResolver {
    fun resolve(raw: String?): ArCode? {
        EntryArQrCodeParser.parse(raw)?.let { return ArCode(ArItemKind.Entry, it) }
        return when (val classified = ScannedCodeClassifier.classify(raw)) {
            is ScannedCode.Caja -> ArCode(ArItemKind.Caja, plain(classified.normalized, raw))
            is ScannedCode.Relacion -> ArCode(ArItemKind.Relacion, plain(classified.normalized, raw))
            else -> null
        }
    }

    // Una caja o una relación no tienen ordinal ni total ni son carpeta: su referencia es el propio código.
    private fun plain(normalized: String, raw: String?) = EntryArQrCode(
        normalized = normalized,
        raw = raw.orEmpty(),
        reference = normalized,
        printedOrdinal = null,
        declaredTotal = null,
        isFolderLabel = false
    )
}
