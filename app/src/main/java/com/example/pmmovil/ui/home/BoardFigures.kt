package com.example.pmmovil.ui.home

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.example.pmmovil.R
import com.example.pmmovil.data.model.HomeStationSummaryDto
import com.example.pmmovil.data.model.HomeSummaryDto
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.scanactions.signalKindOf

/**
 * Los casos que el Tablero sabe explicar bajo una estación. Cada uno trae su frase (con singular y plural). Un código que
 * esta versión de la app no conoce se omite: es mejor no mostrar nada que un código crudo.
 *
 * Los siete días de [OldInYard] son los del servidor (`HomeSummaryRules.OldYardDays`); si cambian allá, cambia el texto.
 */
enum class BoardExceptionCode(@param:PluralsRes val phrase: Int) {
    OldInYard(R.plurals.home_exception_old_in_yard),
    Discrepancies(R.plurals.home_exception_discrepancies),
    Blocked(R.plurals.home_exception_blocked),
    ReadyToExit(R.plurals.home_exception_ready_to_exit)
}

/** La excepción de una estación: con qué sello (palabra, forma y color) y cuántos casos hay. */
data class BoardException(val signal: SignalKind, val code: BoardExceptionCode, val count: Int)

/** La cifra de una estación y su excepción, si la tiene. */
data class BoardFigure(val count: Int, val exception: BoardException? = null)

/** Qué muestra la cifra de cada estación mientras no hay respuesta, o si no llegó. */
data class HomeSummaryUiState(
    /** Ya llegó al menos una respuesta del servidor. */
    val loaded: Boolean = false,
    /** La última consulta falló y no hay nada anterior que mostrar. */
    val failed: Boolean = false,
    val figures: Map<Station, BoardFigure?> = emptyMap()
) {
    /** Todavía se espera la primera respuesta: el Tablero pinta un esqueleto en lugar de la cifra. */
    val loading: Boolean get() = !loaded && !failed
}

internal fun exceptionCodeOf(code: String): BoardExceptionCode? = when (code) {
    "OLD_IN_YARD" -> BoardExceptionCode.OldInYard
    "DISCREPANCIAS" -> BoardExceptionCode.Discrepancies
    "BLOQUEADAS" -> BoardExceptionCode.Blocked
    "LISTAS_PARA_SALIR" -> BoardExceptionCode.ReadyToExit
    else -> null
}

internal fun boardFigureOf(dto: HomeStationSummaryDto?): BoardFigure? {
    if (dto == null) return null
    val exception = dto.exception
        ?.takeIf { it.count > 0 }
        ?.let { raw ->
            exceptionCodeOf(raw.code)?.let { code -> BoardException(signalKindOf(raw.kind), code, raw.count) }
        }
    return BoardFigure(count = dto.count.coerceAtLeast(0), exception = exception)
}

/** Las seis cifras del servidor, por estación. Una estación sin cifra (sin módulo o con falla) queda en `null`. */
internal fun boardFigures(dto: HomeSummaryDto): Map<Station, BoardFigure?> = mapOf(
    Station.Caseta to boardFigureOf(dto.caseta),
    Station.Entrada to boardFigureOf(dto.entrada),
    Station.Revision to boardFigureOf(dto.revision),
    Station.Carga to boardFigureOf(dto.carga),
    Station.Relacion to boardFigureOf(dto.relacion),
    Station.Salida to boardFigureOf(dto.salida)
)

/** El rótulo de lo que cuenta cada cifra, que sustituye a la descripción de la estación cuando ya hay cifra. */
@StringRes
internal fun stationUnit(station: Station): Int = when (station) {
    Station.Caseta -> R.string.home_new_unit_caseta
    Station.Entrada -> R.string.home_new_unit_entrada
    Station.Revision -> R.string.home_new_unit_revision
    Station.Carga -> R.string.home_new_unit_carga
    Station.Relacion -> R.string.home_new_unit_relacion
    Station.Salida -> R.string.home_new_unit_salida
}

/**
 * Si toca volver a pedir las cifras. El inicio vuelve a primer plano muchas veces (al regresar de un módulo, de la
 * cámara, de una notificación) y las cifras valen unos segundos: sin esto cada regreso sería una petición.
 */
internal fun shouldRefreshSummary(
    lastRequestAtMillis: Long?,
    nowMillis: Long,
    minIntervalMillis: Long = SUMMARY_MIN_INTERVAL_MILLIS
): Boolean = lastRequestAtMillis == null || nowMillis - lastRequestAtMillis !in 0 until minIntervalMillis

internal const val SUMMARY_MIN_INTERVAL_MILLIS = 20_000L
