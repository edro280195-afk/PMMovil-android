package com.example.pmmovil.data.printing

import kotlin.math.abs
import kotlin.math.roundToInt

/** Rectángulo de un elemento en dots, sin depender de Compose ni de Android. */
data class SnapBox(val x: Int, val y: Int, val width: Int, val height: Int) {
    val left: Int get() = x
    val right: Int get() = x + width
    val centerX: Int get() = x + width / 2
    val top: Int get() = y
    val bottom: Int get() = y + height
    val centerY: Int get() = y + height / 2
}

/**
 * Posición ya ajustada más las guías que hay que pintar para explicar por qué se ajustó.
 * Las guías vienen en dots del lienzo; la UI solo las multiplica por su escala.
 */
data class SnapResult(
    val x: Int,
    val y: Int,
    val verticalGuides: List<Int> = emptyList(),
    val horizontalGuides: List<Int> = emptyList()
) {
    val snapped: Boolean get() = verticalGuides.isNotEmpty() || horizontalGuides.isNotEmpty()
}

/**
 * Alineación asistida al arrastrar elementos en el diseñador.
 *
 * Cuando un borde (o el centro) del elemento que se mueve queda a menos de cierta distancia del
 * borde de otro elemento, del centro del lienzo o del margen de la etiqueta, la posición se
 * "pega" a ese valor exacto y se devuelve la guía correspondiente para dibujarla. Es lo que
 * permite dejar dos textos perfectamente alineados sin pelear con el dedo en una tablet.
 *
 * Lógica pura a propósito: así se puede probar sin dispositivo ni Compose.
 */
object LabelSnapEngine {

    /**
     * @param moving posición propuesta por el arrastre, antes de ajustar.
     * @param others resto de elementos visibles con los que se puede alinear.
     * @param canvasWidth/[canvasHeight] tamaño de la etiqueta en dots.
     * @param margin margen estándar de la plantilla, para que "pegar al margen" sea una opción.
     */
    fun snap(
        moving: SnapBox,
        others: List<SnapBox>,
        canvasWidth: Int,
        canvasHeight: Int,
        margin: Int = 0,
        tolerance: Int = defaultTolerance(canvasWidth)
    ): SnapResult {
        if (tolerance <= 0) return SnapResult(moving.x, moving.y)

        // Cada candidato dice: "si mueves el elemento a esta X, coincide una guía en este dot".
        val verticalCandidates = mutableListOf<Candidate>()
        val horizontalCandidates = mutableListOf<Candidate>()

        fun addVertical(guide: Int) {
            verticalCandidates += Candidate(targetPosition = guide, guide = guide, distance = abs(moving.left - guide))
            verticalCandidates += Candidate(
                targetPosition = guide - moving.width,
                guide = guide,
                distance = abs(moving.right - guide)
            )
            verticalCandidates += Candidate(
                targetPosition = guide - moving.width / 2,
                guide = guide,
                distance = abs(moving.centerX - guide)
            )
        }

        fun addHorizontal(guide: Int) {
            horizontalCandidates += Candidate(targetPosition = guide, guide = guide, distance = abs(moving.top - guide))
            horizontalCandidates += Candidate(
                targetPosition = guide - moving.height,
                guide = guide,
                distance = abs(moving.bottom - guide)
            )
            horizontalCandidates += Candidate(
                targetPosition = guide - moving.height / 2,
                guide = guide,
                distance = abs(moving.centerY - guide)
            )
        }

        // Referencias del propio lienzo: márgenes, bordes y centro.
        listOf(0, margin, canvasWidth / 2, canvasWidth - margin, canvasWidth)
            .filter { it in 0..canvasWidth }
            .distinct()
            .forEach(::addVertical)
        listOf(0, margin, canvasHeight / 2, canvasHeight - margin, canvasHeight)
            .filter { it in 0..canvasHeight }
            .distinct()
            .forEach(::addHorizontal)

        // Referencias de los demás elementos: sus bordes y su centro.
        others.forEach { other ->
            addVertical(other.left)
            addVertical(other.centerX)
            addVertical(other.right)
            addHorizontal(other.top)
            addHorizontal(other.centerY)
            addHorizontal(other.bottom)
        }

        val bestX = verticalCandidates.filter { it.distance <= tolerance }.minByOrNull { it.distance }
        val bestY = horizontalCandidates.filter { it.distance <= tolerance }.minByOrNull { it.distance }

        val resolvedX = bestX?.targetPosition ?: moving.x
        val resolvedY = bestY?.targetPosition ?: moving.y

        // Solo se reportan las guías que efectivamente quedaron alineadas tras el ajuste, para
        // no pintar líneas que no explican nada.
        val snappedBox = moving.copy(x = resolvedX, y = resolvedY)
        val verticalGuides = bestX
            ?.let { candidate ->
                listOf(candidate.guide).filter {
                    it == snappedBox.left || it == snappedBox.right || it == snappedBox.centerX
                }
            }
            .orEmpty()
        val horizontalGuides = bestY
            ?.let { candidate ->
                listOf(candidate.guide).filter {
                    it == snappedBox.top || it == snappedBox.bottom || it == snappedBox.centerY
                }
            }
            .orEmpty()

        return SnapResult(
            x = resolvedX,
            y = resolvedY,
            verticalGuides = verticalGuides,
            horizontalGuides = horizontalGuides
        )
    }

    /**
     * Tolerancia proporcional al tamaño de la etiqueta: la de caseta (675 dots) necesita un
     * imán más fino que la de bodega (1800 dots) para no sentirse pegajosa.
     */
    fun defaultTolerance(canvasWidth: Int): Int =
        (canvasWidth * TOLERANCE_RATIO).roundToInt().coerceIn(MIN_TOLERANCE, MAX_TOLERANCE)

    private data class Candidate(val targetPosition: Int, val guide: Int, val distance: Int)

    private const val TOLERANCE_RATIO = 0.008f
    private const val MIN_TOLERANCE = 4
    private const val MAX_TOLERANCE = 18
}
