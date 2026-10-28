package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.entryar.EntryArQrCodeParser

/**
 * Qué es un código leído. Es solo una pista local, sin red: sirve para decidir a dónde ir, qué rotular en la
 * pantalla y qué pedir al servidor. Que una caja tenga forma de caja no prueba que exista; eso lo dice el servidor.
 */
sealed interface ScannedCode {
    /** Valor normalizado, tal como se compara contra la base. Vacío en [Gafete]: nunca se conserva. */
    val normalized: String

    /** Caja de caseta (`E-017846/26`). */
    data class Caja(override val normalized: String) : ScannedCode

    /** Relación de salida (`01I26104546`). */
    data class Relacion(override val normalized: String) : ScannedCode

    /** Etiqueta de carpeta: la referencia sola; identifica el expediente, no un bulto. */
    data class Carpeta(val reference: String) : ScannedCode {
        override val normalized: String get() = reference
    }

    /**
     * Etiqueta de bulto. La posición es la impresa al imprimir y no prueba cuántos bultos hay: si la entrada se
     * reimprime con otro total, las viejas siguen pegadas en el piso.
     */
    data class Bulto(val reference: String, val ordinal: Int, val total: Int) : ScannedCode {
        override val normalized: String get() = "$reference $ordinal-$total"
    }

    /** QR de pre-registro de un camión sin caja (`PMMPR|…`). */
    data class PreRegistro(override val normalized: String) : ScannedCode

    /**
     * QR de gafete (`usuario;clave`). Lleva una credencial: por eso no tiene valor, y quien lo reciba no debe
     * consultarlo, guardarlo ni escribirlo en ningún registro.
     */
    data object Gafete : ScannedCode {
        override val normalized: String = ""
    }

    /** Etiqueta de producto del cliente (códigos estructurados); se lee dentro de una revisión. */
    data class ProductoCliente(override val normalized: String) : ScannedCode

    /** Tiene forma de referencia pero no la de una entrada de 15 caracteres: lo decide el servidor. */
    data class Candidato(override val normalized: String) : ScannedCode

    /** No tiene la forma de nada que PMM use. */
    data class Desconocido(override val normalized: String) : ScannedCode
}

object ScannedCodeClassifier {
    private const val MAX_LENGTH = 120
    private const val GROUP_SEPARATOR = '\u001D'
    private const val ISO_15434_HEADER = "[)>"

    fun classify(raw: String?): ScannedCode {
        val normalized = EntryArQrCodeParser.normalize(raw)
        if (normalized.isEmpty() || normalized.length > MAX_LENGTH) return ScannedCode.Desconocido(normalized)
        if (LabelCodeShapes.esGafete(normalized)) return ScannedCode.Gafete
        if (normalized.startsWith(LabelCodeShapes.PRE_REGISTRO_PREFIX)) return ScannedCode.PreRegistro(normalized)
        // La normalización quita el separador de grupo, así que el rastro de un código estructurado se busca antes.
        if (esEstructurado(raw, normalized)) return ScannedCode.ProductoCliente(normalized)
        if (LabelCodeShapes.caja.matches(normalized)) return ScannedCode.Caja(normalized)
        if (LabelCodeShapes.relacion.matches(normalized)) return ScannedCode.Relacion(normalized)
        // Los tres QR oficiales traen más texto que su entrada o relación: se reconocen completos y se queda lo que
        // identifica la pieza. Va antes de la referencia genérica, que tomaba la orden de carga (`01I26106418-1`) por
        // una referencia dudosa y dejaba la carpeta y la requisición como código desconocido.
        when (val oficial = OfficialQrParser.parseNormalized(normalized)) {
            is OfficialQr.Carpeta -> return ScannedCode.Carpeta(oficial.entrada)
            is OfficialQr.OrdenDeCarga -> return ScannedCode.Relacion(oficial.relacion)
            is OfficialQr.RequisicionDeCruce -> return ScannedCode.Relacion(oficial.relacion)
            null -> Unit
        }

        val partes = LabelCodeShapes.conPosicion.matchEntire(normalized)
        val referencia = partes?.groupValues?.get(1) ?: normalized
        if (LabelCodeShapes.entrada.matches(referencia)) {
            if (partes == null) return ScannedCode.Carpeta(referencia)
            val ordinal = partes.groupValues[2].toIntOrNull()
            val total = partes.groupValues[3].toIntOrNull()
            // Una posición mayor al total o un total en cero es una lectura mutilada: se rechaza en lugar de
            // aceptar un bulto que no se puede ubicar (mismo criterio que EntryArQrCodeParser).
            return if (ordinal != null && total != null && ordinal in 1..total) {
                ScannedCode.Bulto(referencia, ordinal, total)
            } else {
                ScannedCode.Desconocido(normalized)
            }
        }
        return if (LabelCodeShapes.referenciaGenerica.matches(referencia)) {
            ScannedCode.Candidato(normalized)
        } else {
            ScannedCode.Desconocido(normalized)
        }
    }

    private fun esEstructurado(raw: String?, normalized: String): Boolean =
        raw?.contains(GROUP_SEPARATOR) == true ||
            raw?.trimStart()?.startsWith(ISO_15434_HEADER) == true ||
            // Los códigos de etiqueta de proveedor empiezan con `+` y un identificador (`+30S77120045`).
            Regex("^\\+\\d{1,2}[A-Z]").containsMatchIn(normalized)
}
