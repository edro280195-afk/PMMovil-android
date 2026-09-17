package com.example.pmmovil.ui.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoZoomControllerTest {

    private fun controller(
        resting: Float = 1.8f,
        ceiling: Float = 5f
    ): AutoZoomController = AutoZoomController().apply {
        restingZoom = resting
        ceilingZoom = ceiling
    }

    /** Alimenta candidatos hasta cumplir la racha minima de estabilidad. */
    private fun AutoZoomController.warmUp(
        startAt: Long,
        zoom: Float,
        size: Float
    ): Long {
        var now = startAt
        repeat(AutoZoomController.MIN_STABLE_FRAMES - 1) {
            onCandidate(now, zoom, size, decoded = false, canAdjust = true)
            now += 40L
        }
        return now
    }

    @Test
    fun `un codigo pequeno hace acercar hacia el tamano objetivo`() {
        val controller = controller()
        val now = controller.warmUp(startAt = 1_000L, zoom = 1.8f, size = 0.10f)

        val target = controller.onCandidate(
            now = now,
            currentZoom = 1.8f,
            sizeFraction = 0.10f,
            decoded = false,
            canAdjust = true
        )

        assertNotNull("un QR al 10% del frame tiene que disparar acercamiento", target)
        assertTrue("debe acercar, no alejar", target!! > 1.8f)
        // 0.30 / 0.10 = 3x, acotado por MAX_STEP_FACTOR = 2.2x.
        assertEquals(1.8f * AutoZoomController.MAX_STEP_FACTOR, target, 0.001f)
        assertEquals(AutoZoomPhase.Tracking, controller.phase)
    }

    @Test
    fun `un codigo en el tamano objetivo no mueve el zoom`() {
        val controller = controller()
        val now = controller.warmUp(startAt = 1_000L, zoom = 2.4f, size = 0.30f)

        assertNull(
            controller.onCandidate(now, 2.4f, 0.30f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `un codigo desmesurado hace alejar`() {
        val controller = controller()
        val now = controller.warmUp(startAt = 1_000L, zoom = 4f, size = 0.80f)

        val target = controller.onCandidate(now, 4f, 0.80f, decoded = false, canAdjust = true)

        assertNotNull(target)
        assertTrue("un codigo que no cabe en el frame tiene que alejarse", target!! < 4f)
    }

    @Test
    fun `un codigo ya decodificado no mueve el zoom`() {
        val controller = controller()
        val now = controller.warmUp(startAt = 1_000L, zoom = 1.8f, size = 0.10f)

        assertNull(
            "si ML Kit ya leyo el valor, moverse solo puede estropear la lectura",
            controller.onCandidate(now, 1.8f, 0.10f, decoded = true, canAdjust = true)
        )
    }

    @Test
    fun `un candidato suelto no mueve la camara`() {
        val controller = controller()

        assertNull(
            "el primer frame nunca decide: con enableAllPotentialBarcodes " +
                "cualquier textura se cuela un frame",
            controller.onCandidate(1_000L, 1.8f, 0.08f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `un tamano incoherente reinicia la racha de estabilidad`() {
        val controller = controller()
        controller.onCandidate(1_000L, 1.8f, 0.10f, decoded = false, canAdjust = true)

        // Salto de tamano imposible para el mismo codigo: se descarta.
        assertNull(
            controller.onCandidate(1_040L, 1.8f, 0.40f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `no se corrige mientras el zoom sigue en movimiento`() {
        val controller = controller()
        val now = controller.warmUp(startAt = 1_000L, zoom = 1.8f, size = 0.10f)

        assertNull(
            "medir a mitad de la animacion realimenta datos viejos",
            controller.onCandidate(now, 1.8f, 0.10f, decoded = false, canAdjust = false)
        )
    }

    @Test
    fun `hay enfriamiento entre correcciones consecutivas`() {
        val controller = controller()
        var now = controller.warmUp(startAt = 1_000L, zoom = 1.8f, size = 0.10f)
        assertNotNull(controller.onCandidate(now, 1.8f, 0.10f, false, canAdjust = true))

        now += AutoZoomController.ADJUST_COOLDOWN_MS - 50L
        assertNull(controller.onCandidate(now, 2.6f, 0.14f, false, canAdjust = true))

        now += 100L
        assertNotNull(controller.onCandidate(now, 2.6f, 0.14f, false, canAdjust = true))
    }

    @Test
    fun `el zoom nunca pasa del techo del equipo`() {
        val controller = controller(resting = 1.8f, ceiling = 2.5f)
        val now = controller.warmUp(startAt = 1_000L, zoom = 1.8f, size = 0.05f)

        // 0.26 / 0.05 pide mas zoom; el paso se mantiene suave y el resultado
        // nunca supera el techo del equipo.
        val target = controller.onCandidate(now, 1.8f, 0.05f, decoded = false, canAdjust = true)

        assertEquals(1.8f * AutoZoomController.MAX_STEP_FACTOR, target!!, 0.001f)
        assertTrue(target <= 2.5f)
    }

    @Test
    fun `pegado al techo ya no se piden correcciones imperceptibles`() {
        val controller = controller(resting = 1.8f, ceiling = 2.5f)
        val now = controller.warmUp(startAt = 1_000L, zoom = 2.4f, size = 0.05f)

        assertNull(
            "de 2.4x a 2.5x no se nota nada y solo agita la preview",
            controller.onCandidate(now, 2.4f, 0.05f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `sin candidatos el zoom espera antes de empezar a buscar`() {
        val controller = controller()
        controller.onIdle(1_000L, 1.8f)

        assertNull(
            controller.onIdle(1_000L + AutoZoomController.SEARCH_DELAY_MS - 100L, 1.8f)
        )
        assertEquals(AutoZoomPhase.Idle, controller.phase)
    }

    @Test
    fun `tras la espera el barrido acerca en escalones`() {
        val controller = controller()
        controller.onIdle(1_000L, 1.8f)

        val target = controller.onIdle(1_000L + AutoZoomController.SEARCH_DELAY_MS, 1.8f)

        assertEquals(1.8f + AutoZoomController.SEARCH_STEP, target!!, 0.001f)
        assertEquals(AutoZoomPhase.Searching, controller.phase)
    }

    @Test
    fun `el barrido sube hasta el techo y despues devuelve el campo de vision`() {
        val controller = controller()
        controller.onIdle(1_000L, 1.8f)
        var now = 1_000L + AutoZoomController.SEARCH_DELAY_MS
        // 1.8 * 1.9 = 3.42 de techo de barrido.
        var zoom = 1.8f
        val targets = mutableListOf<Float>()
        repeat(12) {
            controller.onIdle(now, zoom)?.let { target ->
                targets += target
                zoom = target
            }
            now += AutoZoomController.SEARCH_STEP_MS
        }

        val peak = targets.max()
        assertTrue("el barrido tiene que recorrer hasta cerca del techo", peak >= 3.4f)
        assertEquals(
            "al agotar el recorrido tiene que devolver el campo de vision",
            1.8f,
            targets[targets.indexOf(peak) + 1],
            0.01f
        )
    }

    @Test
    fun `ver un codigo corta el barrido`() {
        val controller = controller()
        controller.onIdle(1_000L, 1.8f)
        assertNotNull(controller.onIdle(1_000L + AutoZoomController.SEARCH_DELAY_MS, 1.8f))

        controller.onCandidate(2_100L, 2f, 0.30f, decoded = false, canAdjust = true)

        assertEquals(AutoZoomPhase.Tracking, controller.phase)
        assertNull(
            "tras enganchar un codigo la busqueda arranca de cero",
            controller.onIdle(2_200L, 2f)
        )
    }

    @Test
    fun `el barrido se rinde tras varios ciclos sin encontrar nada`() {
        val controller = controller()
        controller.onIdle(1_000L, 1.8f)
        var now = 1_000L + AutoZoomController.SEARCH_DELAY_MS
        var zoom = 1.8f
        repeat(400) {
            controller.onIdle(now, zoom)?.let { zoom = it }
            now += AutoZoomController.SEARCH_STEP_MS
        }

        assertEquals(
            "una preview que nunca para de moverse molesta mas de lo que ayuda",
            AutoZoomPhase.Idle,
            controller.phase
        )
        assertNull(controller.onIdle(now, zoom))
    }

    @Test
    fun `el zoom manual congela el automatico`() {
        val controller = controller()
        controller.onManualZoom(5_000L)

        val duringPause = controller.warmUp(startAt = 5_100L, zoom = 1.8f, size = 0.08f)
        assertNull(
            controller.onCandidate(duringPause, 1.8f, 0.08f, decoded = false, canAdjust = true)
        )
        assertNull(controller.onIdle(5_100L + AutoZoomController.SEARCH_DELAY_MS, 1.8f))

        val after = 5_000L + AutoZoomController.MANUAL_PAUSE_MS + 1L
        val resumed = controller.warmUp(startAt = after, zoom = 1.8f, size = 0.08f)
        assertNotNull(
            "pasada la ventana manual el automatico vuelve a mandar",
            controller.onCandidate(resumed, 1.8f, 0.08f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `tras leer hay una pausa corta`() {
        val controller = controller()
        controller.onScanned(9_000L)

        val duringPause = controller.warmUp(startAt = 9_100L, zoom = 1.8f, size = 0.08f)
        assertNull(
            controller.onCandidate(duringPause, 1.8f, 0.08f, decoded = false, canAdjust = true)
        )

        val after = 9_000L + AutoZoomController.SCAN_PAUSE_MS + 1L
        val resumed = controller.warmUp(startAt = after, zoom = 1.8f, size = 0.08f)
        assertNotNull(
            controller.onCandidate(resumed, 1.8f, 0.08f, decoded = false, canAdjust = true)
        )
    }

    @Test
    fun `un equipo sin zoom util no barre`() {
        val controller = controller(resting = 1f, ceiling = 1f)
        controller.onIdle(1_000L, 1f)

        assertNull(controller.onIdle(1_000L + AutoZoomController.SEARCH_DELAY_MS, 1f))
        assertEquals(AutoZoomPhase.Idle, controller.phase)
    }

    @Test
    fun `una pantalla que arranca en 1x llega igual a un techo util`() {
        val controller = controller(resting = 1f, ceiling = 5f)
        controller.onIdle(1_000L, 1f)
        var now = 1_000L + AutoZoomController.SEARCH_DELAY_MS
        var zoom = 1f
        var top = 1f
        repeat(30) {
            val target = controller.onIdle(now, zoom)
            if (target != null) {
                zoom = target
                if (target > top) top = target
            }
            now += AutoZoomController.SEARCH_STEP_MS
        }

        assertEquals(
            "1 * 1.9 se queda corto: el minimo absoluto manda",
            AutoZoomController.SEARCH_MIN_TOP,
            top,
            0.001f
        )
    }
}
