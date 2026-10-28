package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.entryar.EntryArQrCodeParser

/**
 * Los tres QR oficiales de Grupo PMM, tal como salen impresos. Cada uno trae más texto que lo que identifica a la pieza,
 * y quien lo lea tiene que quedarse solo con la entrada o la relación: buscar el texto completo no encuentra nada.
 */
sealed interface OfficialQr {
    /** Lo que se busca en el sistema: la entrada o la relación, sin el resto del texto del QR. */
    val identity: String

    /** Etiqueta de carpeta: la entrada, seguida del cliente y un cierre que no se interpretan (`01C2635628400001454  100`). */
    data class Carpeta(val entrada: String) : OfficialQr {
        override val identity: String get() = entrada
    }

    /** Orden de carga, la que se escanea para iniciar una carga (`01I26106418-1`). */
    data class OrdenDeCarga(val relacion: String, val version: String) : OfficialQr {
        override val identity: String get() = relacion
    }

    /** Requisición de cruce, la que se escanea en caseta para dar la salida (`R-01I26106070  0000  101`). */
    data class RequisicionDeCruce(val relacion: String) : OfficialQr {
        override val identity: String get() = relacion
    }
}

/**
 * Lector de los QR oficiales. Solo reconoce las tres formas completas: una entrada o una relación sueltas, una caja, una
 * etiqueta de bulto o de producto no son de aquí y devuelven `null`; el resto de la app las sigue leyendo como siempre.
 */
object OfficialQrParser {
    private const val MAX_LENGTH = 120

    /** Lee un texto crudo del lector (se normaliza igual que en el resto del escaneo). */
    fun parse(raw: String?): OfficialQr? {
        val normalized = EntryArQrCodeParser.normalize(raw)
        if (normalized.isEmpty() || normalized.length > MAX_LENGTH) return null
        return parseNormalized(normalized)
    }

    /** Lo mismo para un texto ya normalizado (mayúsculas y espacios simples). */
    fun parseNormalized(normalized: String): OfficialQr? {
        LabelCodeShapes.ordenDeCarga.matchEntire(normalized)?.let {
            return OfficialQr.OrdenDeCarga(relacion = it.groupValues[1], version = it.groupValues[2])
        }
        LabelCodeShapes.requisicionDeCruce.matchEntire(normalized)?.let {
            return OfficialQr.RequisicionDeCruce(relacion = it.groupValues[1])
        }
        LabelCodeShapes.carpetaConCliente.matchEntire(normalized)?.let {
            return OfficialQr.Carpeta(entrada = it.groupValues[1])
        }
        return null
    }

    /** La entrada o la relación que identifica el QR oficial, o `null` si la lectura no es uno. */
    fun identityOf(raw: String?): String? = parse(raw)?.identity

    /** La relación de una orden de carga o de una requisición de cruce; `null` en cualquier otro caso. */
    fun relacionOf(raw: String?): String? = when (val official = parse(raw)) {
        is OfficialQr.OrdenDeCarga -> official.relacion
        is OfficialQr.RequisicionDeCruce -> official.relacion
        is OfficialQr.Carpeta, null -> null
    }

    /** La entrada de una etiqueta de carpeta con cliente; `null` en cualquier otro caso. */
    fun entradaOf(raw: String?): String? = (parse(raw) as? OfficialQr.Carpeta)?.entrada
}
