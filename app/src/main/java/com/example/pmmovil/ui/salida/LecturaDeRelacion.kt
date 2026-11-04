package com.example.pmmovil.ui.salida

import com.example.pmmovil.data.scanner.OfficialQr
import com.example.pmmovil.data.scanner.OfficialQrParser

/**
 * Lo que la pantalla de Salida (carga) entiende de una lectura cuando espera una relación.
 *
 * La orden de carga (`01I26106418-1`) y la requisición de cruce (`R-01I26106070  0000  101`) traen más texto que la
 * relación. Antes la requisición medía más de 15 caracteres y se mandaba a validar como QR de caseta, y la carpeta se
 * buscaba con todo y cliente: ninguna de las dos llegaba a ser una relación.
 */
internal sealed interface LecturaDeRelacion {
    /** Una relación lista para cargar. [version] es la de la orden de carga, o vacía cuando el QR no trae ninguna (no se valida). */
    data class Relacion(val relno: String, val version: String) : LecturaDeRelacion

    /** El QR de una carpeta: identifica una entrada, no una relación. */
    data class Carpeta(val entrada: String) : LecturaDeRelacion

    /** Cualquier otra cosa (una relación escrita a mano, un QR de caseta): se valida como siempre. */
    data class Texto(val valor: String) : LecturaDeRelacion
}

/**
 * Versión cuando el QR no la trae (requisición de cruce o captura a mano). Va vacía a propósito: antes iba "1" y el
 * ~19 % de las relaciones, que ya van en versión 2 o más, salía con un falso «VERSIÓN NO ACTUALIZADA». La versión solo
 * se valida cuando viene en la orden de carga, que es la hoja impresa que puede estar vieja.
 */
internal const val VERSION_POR_OMISION = ""

internal fun leerRelacion(raw: String): LecturaDeRelacion = when (val oficial = OfficialQrParser.parse(raw)) {
    is OfficialQr.OrdenDeCarga -> LecturaDeRelacion.Relacion(oficial.relacion, oficial.version)
    is OfficialQr.RequisicionDeCruce -> LecturaDeRelacion.Relacion(oficial.relacion, VERSION_POR_OMISION)
    is OfficialQr.Carpeta -> LecturaDeRelacion.Carpeta(oficial.entrada)
    null -> LecturaDeRelacion.Texto(raw.trim())
}

/** La entrada de una carpeta leída con su cliente; si no es una carpeta, el texto tal cual. */
internal fun leerEntrada(raw: String): String = OfficialQrParser.entradaOf(raw) ?: raw.trim()
