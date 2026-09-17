package com.example.pmmovil.ui.rostro

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.example.pmmovil.ui.rostro.FaceOrientationTracker.Companion.CELDAS_ARO
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * El aro del enrolamiento: marcas radiales que se van pintando, como Face ID.
 *
 * **La regla que ordena el diseño: lo que se dibuja es lo que se pide.** El aro
 * de la versión anterior dibujaba 48 marcas y terminaba a las 10, dejando 38
 * apagadas sin explicación. Aquí hay tantas marcas como hacen falta: cuando
 * están todas pintadas terminó, y si queda un hueco, el hueco ES la instrucción.
 *
 * **Por qué marcas y no un trazo con halo.** La versión de trazo continuo se
 * veía bien en el navegador y se arrastraba en la tableta: cada cuadro pintaba
 * 22 arcos de estela, creaba un degradado radial nuevo (objeto nuevo, y basura
 * que recoger) y daba dos pasadas por cada tramo. Aquí se dibujan [CELDAS_ARO]
 * líneas y nada más — sin degradados, sin capas de brillo, sin asignaciones por
 * cuadro. En una pantalla que se mira fijamente mientras se mueve la cabeza, ir
 * fluido importa más que cualquier efecto.
 */
@Composable
fun AroEnrolamiento(
    cobertura: Set<Int>,
    celdaActual: Int?,
    progresoSello: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        val lado = size.minDimension
        val radioDentro = lado * 0.404f
        val radioFuera = lado * 0.462f
        val grosor = lado * 0.017f
        val sellando = progresoSello > 0f
        val frente = progresoSello * CELDAS_ARO
        val paso = 2f * PI.toFloat() / CELDAS_ARO

        for (i in 0 until CELDAS_ARO) {
            val pintada = cobertura.contains(i)
            val sellada = sellando && pintada && i < frente

            // La marca hacia donde apunta la cara se estira y se aclara. Es la
            // guía entera: sin flecha aparte y sin estela que dibujar.
            val realce = when {
                sellando -> 0f
                celdaActual == null -> 0f
                else -> when (distanciaCircular(i, celdaActual)) {
                    0 -> 1f
                    1 -> 0.45f
                    else -> 0f
                }
            }

            val color = when {
                sellada -> VERDE
                pintada -> AZUL
                realce > 0f -> AZUL_TENUE
                else -> RIEL
            }

            val angulo = -PI.toFloat() / 2f + i * paso
            val cos = cos(angulo)
            val sen = sin(angulo)
            val desde = radioDentro - realce * lado * 0.014f
            val hasta = radioFuera + realce * lado * 0.020f

            drawLine(
                color = color,
                start = Offset(centro.x + cos * desde, centro.y + sen * desde),
                end = Offset(centro.x + cos * hasta, centro.y + sen * hasta),
                strokeWidth = if (pintada || realce > 0f) grosor * 1.3f else grosor,
                cap = StrokeCap.Round
            )
        }
    }
}

/** Distancia en marcas entre dos posiciones del aro, por el lado corto. */
private fun distanciaCircular(a: Int, b: Int): Int {
    val d = kotlin.math.abs(a - b) % CELDAS_ARO
    return minOf(d, CELDAS_ARO - d)
}

private val RIEL = Color(0xFFC3D2E8)
private val AZUL = Color(0xFF1A56C4)
private val AZUL_TENUE = Color(0xFF93B4E8)
private val VERDE = Color(0xFF16A34A)
