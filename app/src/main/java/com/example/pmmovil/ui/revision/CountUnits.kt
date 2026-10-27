package com.example.pmmovil.ui.revision

import java.util.Locale
import kotlin.math.abs

/**
 * Las unidades de la factura en las que se puede contar por etiqueta, y cuánto vale cada una en su unidad base.
 *
 * Hay dos clases de cuenta ([Dimension]):
 *
 * - **Piezas.** En el catálogo `unidades`, CNT es CIENTOS y MLL es MILLAR: una partida de «2.100 CNT» son 210 piezas, que
 *   es lo que dice la etiqueta de la bolsa. Medido en `revdet` (partidas revisadas, respaldo local de octubre de 2026):
 *   las de PZA guardan la cantidad recibida igual a la de la factura (342 de 342), las de MLL la guardan en piezas (37 de
 *   42, por mil) y las 2 de CNT también (por cien). O sea, **`ncantout` va en piezas**; es `ncantfactura` la que va en la
 *   unidad de la factura.
 * - **Kilos.** Las partidas de compuesto químico de Freudenberg van en KG y la `Q` de su etiqueta son kilos: cinco cajas de
 *   77.6, 78.4, 78.7, 79.2 y 78.0 suman exactamente los 391.9 KG de la partida 49118845 de la entrada 01C263591850000. Aquí
 *   `ncantout` va en kilos, igual que la factura (59 % de las partidas en KG ya lo traen así).
 *
 * Las demás unidades (litros, metros, toneladas…) no se cuentan por etiqueta y no tienen factor.
 */
object CountUnits {
    /** En qué se cuenta: lo que suma una etiqueta y lo que declara la factura se comparan solo dentro de la misma clase. */
    enum class Dimension { PIECES, KILOS }

    /** Cuánto vale una unidad de la factura en la unidad base de su [dimension] (piezas o kilos). */
    data class Countable(val factor: Double, val dimension: Dimension)

    /** Lo que una etiqueta imprime como unidad, para no mezclar kilos con piezas ni con libras. */
    enum class PrintedUnit { PIECES, KILOS, POUNDS }

    private val COUNTABLE = mapOf(
        "PZA" to Countable(1.0, Dimension.PIECES),
        "CNT" to Countable(100.0, Dimension.PIECES),
        "MLL" to Countable(1000.0, Dimension.PIECES),
        "KG" to Countable(1.0, Dimension.KILOS)
    )

    /** Misma tolerancia que usa la tarjeta de la etiqueta al comparar cantidades. */
    private const val TOLERANCE = 0.0005

    /** Palabras sueltas que una etiqueta usa como unidad. Sin lookbehind de dígitos: «77.600KG» también cuenta. */
    private val PRINTED_UNIT_WORD = Regex("""(?<![A-Za-z])(KGS?|KGM|PCS?|PZS?|PZA|PIECES|PIEZAS|EA|LBS?)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    /** Cómo se cuenta una partida en [unit], o null si su unidad no se cuenta por etiqueta. */
    fun countable(unit: String?): Countable? = unit?.trim()?.uppercase(Locale.ROOT)?.let(COUNTABLE::get)

    /** Cuántas piezas vale una unidad de [unit], o null si no es una unidad en la que se cuente por piezas. */
    fun piecesPerUnit(unit: String?): Double? =
        countable(unit)?.takeIf { it.dimension == Dimension.PIECES }?.factor

    /** [quantity] de la unidad de la factura, expresada en piezas; null si la unidad no se cuenta por piezas. */
    fun toPieces(quantity: Double, unit: String?): Double? = piecesPerUnit(unit)?.let { quantity * it }

    /**
     * Las unidades que el texto impreso de una etiqueta nombra como palabra suelta («UNIT/ME KG», «QTY 200 PC»). Vacío si
     * no nombra ninguna: con cero margen de error, no saber la unidad no es lo mismo que saber que son kilos.
     */
    fun printedUnits(printedText: String): Set<PrintedUnit> =
        PRINTED_UNIT_WORD.findAll(printedText).mapNotNull { match ->
            when (match.value.uppercase(Locale.ROOT)) {
                "KG", "KGS", "KGM" -> PrintedUnit.KILOS
                "LB", "LBS" -> PrintedUnit.POUNDS
                "PC", "PCS", "PZ", "PZS", "PZA", "PIECES", "PIEZAS", "EA" -> PrintedUnit.PIECES
                else -> null
            }
        }.toSet()

    /** Dos cantidades en la misma unidad son iguales si difieren menos que la tolerancia (más el error relativo de un Double). */
    fun areEqual(a: Double, b: Double): Boolean = abs(a - b) <= TOLERANCE + abs(b) * 1e-9
}
