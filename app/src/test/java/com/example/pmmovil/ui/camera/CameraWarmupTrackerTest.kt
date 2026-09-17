package com.example.pmmovil.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraWarmupTrackerTest {

    private val searching = 1
    private val converged = 2
    private val locked = 3

    @Test
    fun `no esta listo hasta que la AE converge dos cuadros seguidos`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }

        assertFalse(tracker.onFrame(33L, searching))
        assertFalse(tracker.onFrame(66L, searching))
        assertFalse(tracker.onFrame(99L, converged))
        assertTrue(tracker.onFrame(132L, converged))
        assertTrue(tracker.isReady)
        assertEquals("ae-convergida", tracker.readyReason)
    }

    @Test
    fun `la racha de convergencia se reinicia si vuelve a buscar`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }

        tracker.onFrame(33L, converged)
        tracker.onFrame(66L, converged)
        assertFalse(tracker.isReady) // faltan cuadros minimos
        tracker.onFrame(99L, searching)
        assertFalse(tracker.onFrame(132L, converged))
        assertTrue(tracker.onFrame(165L, locked))
    }

    @Test
    fun `solo la primera transicion devuelve true`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        repeat(3) { tracker.onFrame(33L * (it + 1), converged) }
        assertTrue(tracker.isReady)

        assertFalse(tracker.onFrame(200L, converged))
        assertFalse(tracker.tick(5_000L))
    }

    @Test
    fun `un cuadro quemado reciente impide darse por listo aunque la AE diga convergida`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        tracker.onLuminance(nowMs = 90L, luminance = 0.99)

        repeat(4) { tracker.onFrame(33L * (it + 1), converged) }
        assertFalse(tracker.isReady)

        // Con luminancia normal ya se acepta.
        tracker.onLuminance(nowMs = 200L, luminance = 0.45)
        assertTrue(tracker.onFrame(210L, converged))
    }

    @Test
    fun `una luminancia vieja no bloquea si no se exige medida`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        tracker.onLuminance(nowMs = 10L, luminance = 0.01)

        repeat(2) { tracker.onFrame(33L * (it + 1), converged) }
        assertTrue(tracker.onFrame(2_000L, converged))
    }

    @Test
    fun `con requireLuminance la AE convergida no basta sin una medida reciente`() {
        val tracker = CameraWarmupTracker(requireLuminance = true).apply { onBound(nowMs = 0L) }

        repeat(6) { assertFalse(tracker.onFrame(33L * (it + 1), converged)) }
        assertFalse(tracker.isReady)

        // Llega la medida del analizador con un cuadro normal: listo.
        tracker.onLuminance(nowMs = 250L, luminance = 0.40)
        assertTrue(tracker.onFrame(264L, converged))
        assertEquals("ae-convergida", tracker.readyReason)
    }

    @Test
    fun `con requireLuminance una medida vieja tampoco vale`() {
        val tracker = CameraWarmupTracker(requireLuminance = true).apply { onBound(nowMs = 0L) }
        tracker.onLuminance(nowMs = 10L, luminance = 0.40)

        // Los cuadros llegan cuando la medida ya caducó (> LUMINANCE_MAX_AGE_MS).
        val base = CameraWarmupTracker.LUMINANCE_MAX_AGE_MS + 100L
        repeat(4) { assertFalse(tracker.onFrame(base + 33L * it, converged)) }
        assertFalse(tracker.isReady)

        tracker.onLuminance(nowMs = base + 150L, luminance = 0.40)
        assertTrue(tracker.onFrame(base + 165L, converged))
    }

    @Test
    fun `si el HAL nunca reporta AE se libera tras unos cuadros`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }

        repeat(CameraWarmupTracker.FRAMES_WITHOUT_AE_STATE - 1) {
            assertFalse(tracker.onFrame(33L * (it + 1), aeState = null))
        }
        assertTrue(tracker.onFrame(500L, aeState = null))
        assertEquals("sin-estado-ae", tracker.readyReason)
    }

    @Test
    fun `sin estado AE y con requireLuminance tambien hace falta ver un cuadro sano`() {
        val tracker = CameraWarmupTracker(requireLuminance = true).apply { onBound(nowMs = 0L) }
        repeat(CameraWarmupTracker.FRAMES_WITHOUT_AE_STATE + 2) {
            assertFalse(tracker.onFrame(33L * (it + 1), aeState = null))
        }
        tracker.onLuminance(nowMs = 400L, luminance = 0.5)
        assertTrue(tracker.onFrame(410L, aeState = null))
        assertEquals("sin-estado-ae", tracker.readyReason)
    }

    @Test
    fun `tiempo limite desde el primer cuadro`() {
        // Plazo de enlace muy largo para aislar el plazo de cuadros.
        val tracker = CameraWarmupTracker(bindTimeoutMs = 60_000L).apply { onBound(nowMs = 0L) }
        tracker.onFrame(100L, searching)

        assertFalse(tracker.tick(100L + CameraWarmupTracker.FRAME_TIMEOUT_MS - 1))
        assertTrue(tracker.tick(100L + CameraWarmupTracker.FRAME_TIMEOUT_MS))
        assertEquals("tiempo-limite-cuadros", tracker.readyReason)
        assertTrue(tracker.isReady)
        assertFalse(tracker.isFailed)
    }

    @Test
    fun `sin ningun cuadro al vencer el plazo la sesion se da por muerta, no por lista`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 1_000L) }

        assertFalse(tracker.tick(1_000L + CameraWarmupTracker.BIND_TIMEOUT_MS - 1))
        assertTrue(tracker.tick(1_000L + CameraWarmupTracker.BIND_TIMEOUT_MS))
        assertTrue(tracker.isFailed)
        assertFalse(tracker.isReady)
        assertEquals("sin-cuadros", tracker.readyReason)
        // Ya fallida, no vuelve a avisar ni cambia de opinion.
        assertFalse(tracker.tick(10_000L))
        assertFalse(tracker.onFrame(10_001L, converged))
        assertFalse(tracker.isReady)
    }

    @Test
    fun `con cuadros pero sin convergencia el plazo de enlace si levanta la cortina`() {
        val tracker = CameraWarmupTracker(frameTimeoutMs = 10_000L).apply { onBound(nowMs = 0L) }
        assertFalse(tracker.onFrame(CameraWarmupTracker.BIND_TIMEOUT_MS - 100L, searching))
        assertTrue(tracker.tick(CameraWarmupTracker.BIND_TIMEOUT_MS))
        assertTrue(tracker.isReady)
        assertFalse(tracker.isFailed)
        assertEquals("tiempo-limite-enlace", tracker.readyReason)
    }

    @Test
    fun `una imagen real sin metadatos de captura cuenta como prueba de vida`() {
        // Reproduce el bug medido en la Armor Pad Pro: el escaner leyo un QR
        // real (imagenes llegando al analizador) mientras la app declaraba
        // la sesion muerta porque CameraWarmupTracker solo escuchaba los
        // metadatos de captura, que el HAL nunca reporto en esa sesion.
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        tracker.onLuminance(nowMs = 500L, luminance = 0.4)

        assertFalse(tracker.tick(CameraWarmupTracker.BIND_TIMEOUT_MS - 1))
        assertTrue(tracker.tick(CameraWarmupTracker.BIND_TIMEOUT_MS))
        assertTrue(tracker.isReady)
        assertFalse(tracker.isFailed)
        assertEquals("tiempo-limite-enlace", tracker.readyReason)
    }

    @Test
    fun `sin metadatos ni imagenes la sesion si se declara muerta`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        assertTrue(tracker.tick(CameraWarmupTracker.BIND_TIMEOUT_MS))
        assertTrue(tracker.isFailed)
        assertEquals("sin-cuadros", tracker.readyReason)
    }

    @Test
    fun `onLuminance no cambia una sesion ya declarada muerta`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        tracker.tick(CameraWarmupTracker.BIND_TIMEOUT_MS)
        assertTrue(tracker.isFailed)

        tracker.onLuminance(nowMs = CameraWarmupTracker.BIND_TIMEOUT_MS + 100L, luminance = 0.5)
        assertTrue(tracker.isFailed)
        assertFalse(tracker.isReady)
        assertEquals("sin-cuadros", tracker.readyReason)
    }

    @Test
    fun `onBound reinicia todo`() {
        val tracker = CameraWarmupTracker().apply { onBound(nowMs = 0L) }
        repeat(3) { tracker.onFrame(33L * (it + 1), converged) }
        assertTrue(tracker.isReady)

        tracker.onBound(nowMs = 10_000L)
        assertFalse(tracker.isReady)
        assertFalse(tracker.isFailed)
        assertEquals(0, tracker.framesSeen)
        assertFalse(tracker.tick(10_000L + CameraWarmupTracker.BIND_TIMEOUT_MS - 1))
    }
}
