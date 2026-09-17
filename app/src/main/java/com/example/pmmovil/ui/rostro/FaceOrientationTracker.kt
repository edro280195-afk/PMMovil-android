package com.example.pmmovil.ui.rostro

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor

/**
 * Suaviza la pose que entrega el detector y la traduce a la posición del aro.
 *
 * Reporta dos resoluciones distintas a propósito, porque sirven para cosas
 * distintas:
 *
 *  - **celda** (36 marcas de 10°) es lo que PINTA el aro. No exige estabilidad:
 *    el trazo tiene que seguir la cara como una pluma, no esperar a que se
 *    quede quieta. Si esperara, el aro iría siempre por detrás del movimiento
 *    y se sentiría roto.
 *  - **sector** (12 tramos de 30°) es lo que GUARDA una plantilla. Ahí sí se
 *    exige estabilidad, porque una toma borrosa a media sacudida contamina el
 *    banco. Doce por vuelta bastan y sobran.
 *
 * No conoce nada de Compose ni de la cámara, así que se puede probar con datos
 * sintéticos y ejecutar junto al analizador.
 */
class FaceOrientationTracker(
    private val requiredStableFrames: Int = 2,
    private val smoothingFactor: Float = 0.25f
) {
    data class Sample(
        val yaw: Float,
        val pitch: Float,
        /** Dirección continua en radianes, 0 = arriba y creciendo en horario. */
        val angulo: Float?,
        /** Tramo fino del aro, 0..[CELDAS_ARO]-1. Null cuando está de frente. */
        val celda: Int?,
        /** Tramo grueso para las plantillas, 0..[SECTORES_PLANTILLA]-1. */
        val sector: Int?,
        val stableFrames: Int
    )

    private var initialized = false
    private var smoothedYaw = 0f
    private var smoothedPitch = 0f
    private var candidateSector: Int? = null
    private var candidateFrames = 0

    fun update(yaw: Float, pitch: Float, yawReference: Float, pitchReference: Float): Sample {
        if (!initialized) {
            smoothedYaw = yaw
            smoothedPitch = pitch
            initialized = true
        } else {
            smoothedYaw = smoothedYaw * (1f - smoothingFactor) + yaw * smoothingFactor
            smoothedPitch = smoothedPitch * (1f - smoothingFactor) + pitch * smoothingFactor
        }

        val safeYawReference = yawReference.coerceAtLeast(1f)
        val safePitchReference = pitchReference.coerceAtLeast(1f)
        val normalizedYaw = smoothedYaw / safeYawReference
        val normalizedPitch = smoothedPitch / safePitchReference
        val distance = kotlin.math.hypot(normalizedYaw, normalizedPitch)

        if (distance < MIN_DISTANCE) {
            resetCandidate()
            return Sample(smoothedYaw, smoothedPitch, null, null, null, 0)
        }

        // 0 = arriba y el ángulo crece en sentido horario, igual que el aro.
        // FaceEngine expone pitch positivo cuando la persona mira hacia arriba.
        val angle = atan2(normalizedYaw, normalizedPitch)
        val normalizedAngle = if (angle < 0f) angle + 2f * PI.toFloat() else angle
        val celda = tramoDe(normalizedAngle, CELDAS_ARO)
        val sector = tramoDe(normalizedAngle, SECTORES_PLANTILLA)

        if (candidateSector == sector) {
            candidateFrames++
        } else {
            candidateSector = sector
            candidateFrames = 1
        }

        return Sample(
            yaw = smoothedYaw,
            pitch = smoothedPitch,
            angulo = normalizedAngle,
            celda = celda,
            sector = sector,
            stableFrames = candidateFrames
        )
    }

    fun reset() {
        initialized = false
        smoothedYaw = 0f
        smoothedPitch = 0f
        resetCandidate()
    }

    fun resetCandidate() {
        candidateSector = null
        candidateFrames = 0
    }

    /** Sólo aplica a la captura de plantillas; el trazo del aro no la usa. */
    fun isStable(sample: Sample): Boolean =
        sample.sector != null && sample.stableFrames >= requiredStableFrames

    companion object {
        private const val MIN_DISTANCE = 0.28f

        /**
         * Marcas que dibuja el aro. 36 × 10° es la resolucion de Face ID: se
         * leen como marcas separadas, se cierran en un giro comodo y son la
         * mitad de trabajo por cuadro que la version de 72.
         */
        const val CELDAS_ARO = 36

        /** Tramos que guardan plantilla. 12 × 30°, uno por vuelta. */
        const val SECTORES_PLANTILLA = 12

        private fun tramoDe(angulo: Float, tramos: Int): Int =
            floor(angulo / (2f * PI.toFloat()) * tramos).toInt().mod(tramos)

        /**
         * Camino entre dos celdas: al pasar de una a otra hay que pintar lo que
         * quedó en medio, porque la cara físicamente pasó por ahí aunque el
         * detector no entregara un cuadro justo en ese punto. Sin esto el aro
         * se llena de huecos de un frame que el usuario no sabe cómo tapar.
         *
         * Un salto largo no se rellena: eso no es un giro, es la cara
         * reapareciendo en otro lado.
         */
        fun caminoEntre(desde: Int, hasta: Int): List<Int> {
            var delta = (hasta - desde + CELDAS_ARO) % CELDAS_ARO
            if (delta > CELDAS_ARO / 2) delta -= CELDAS_ARO
            if (kotlin.math.abs(delta) > CELDAS_ARO / 4) return emptyList()

            val paso = if (delta >= 0) 1 else -1
            val camino = mutableListOf<Int>()
            var k = 0
            while (k != delta) {
                camino += (desde + k).mod(CELDAS_ARO)
                k += paso
            }
            return camino
        }
    }
}
