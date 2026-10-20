package com.example.pmmovil.ui.revision

import java.util.Locale
import kotlin.math.abs

/**
 * Las unidades de la factura en las que se puede contar por piezas, y cuánto vale cada una en piezas.
 *
 * En el catálogo `unidades`, CNT es CIENTOS y MLL es MILLAR: una partida de «2.100 CNT» son 210 piezas, que es lo
 * que dice la etiqueta de la bolsa. Medido en `revdet` (partidas revisadas, respaldo local de octubre de 2026): las
 * de PZA guardan la cantidad recibida igual a la de la factura (342 de 342), las de MLL la guardan en piezas (37 de
 * 42, por mil) y las 2 de CNT también (por cien). O sea, **`ncantout` va en piezas**; es `ncantfactura` la que va
 * en la unidad de la factura. Las demás unidades (kilos, litros, metros…) no se cuentan por etiqueta y no tienen
 * factor.
 */
object CountUnits {
    private val PIECES_PER_UNIT = mapOf("PZA" to 1.0, "CNT" to 100.0, "MLL" to 1000.0)

    /** Misma tolerancia que usa la tarjeta de la etiqueta al comparar cantidades. */
    private const val TOLERANCE = 0.0005

    /** Cuántas piezas vale una unidad de [unit], o null si no es una unidad en la que se cuente por piezas. */
    fun piecesPerUnit(unit: String?): Double? =
        unit?.trim()?.uppercase(Locale.ROOT)?.let(PIECES_PER_UNIT::get)

    /** [quantity] de la unidad de la factura, expresada en piezas; null si la unidad no se cuenta por piezas. */
    fun toPieces(quantity: Double, unit: String?): Double? = piecesPerUnit(unit)?.let { quantity * it }

    /** Dos cantidades en la misma unidad son iguales si difieren menos que la tolerancia (más el error relativo de un Double). */
    fun areEqual(a: Double, b: Double): Boolean = abs(a - b) <= TOLERANCE + abs(b) * 1e-9
}
