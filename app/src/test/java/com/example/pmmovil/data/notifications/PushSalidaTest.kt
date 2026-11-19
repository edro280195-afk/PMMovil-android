package com.example.pmmovil.data.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSalidaTest {

    @Test
    fun `los tipos son los mismos que manda el API`() {
        // Si alguno cambia aquí o en SalidaReglas del API, los avisos dejan de abrir su pantalla.
        assertEquals("salida_bloqueo", PushSalida.TIPO_BLOQUEO)
        assertEquals("salida_desbloqueo", PushSalida.TIPO_DESBLOQUEO)
    }

    @Test
    fun `solo los avisos de salida son de salida`() {
        assertTrue(PushSalida.esDeSalida("salida_bloqueo"))
        assertTrue(PushSalida.esDeSalida("salida_desbloqueo"))
        assertFalse(PushSalida.esDeSalida("revision_asignada"))
        assertFalse(PushSalida.esDeSalida(null))
        assertFalse(PushSalida.esDeSalida(""))
    }

    @Test
    fun `los avisos de salida usan su canal y el resto el que ya tenian`() {
        assertEquals("pmm_bloqueos", PushSalida.canalPara("salida_bloqueo", "pmm_revision"))
        assertEquals("pmm_bloqueos", PushSalida.canalPara("salida_desbloqueo", "pmm_revision"))
        assertEquals("pmm_revision", PushSalida.canalPara("revision_asignada", "pmm_revision"))
        assertEquals("pmm_revision", PushSalida.canalPara(null, "pmm_revision"))
    }

    @Test
    fun `el bloqueo abre la relacion en Autorizar relaciones`() {
        assertEquals(
            "relacion?barcode=01I26106418-1",
            PushSalida.rutaDestino("salida_bloqueo", "01I26106418-1")
        )
    }

    @Test
    fun `el desbloqueo abre la relacion en Cargar mercancia`() {
        assertEquals(
            "salida?barcode=01I26106418-1",
            PushSalida.rutaDestino("salida_desbloqueo", "01I26106418-1")
        )
    }

    @Test
    fun `la relacion va recortada y codificada en la ruta`() {
        assertEquals(
            "relacion?barcode=01I1",
            PushSalida.rutaDestino("salida_bloqueo", "  01I1  ")
        )
        assertEquals(
            "salida?barcode=A%20B%2FC",
            PushSalida.rutaDestino("salida_desbloqueo", "A B/C")
        )
    }

    @Test
    fun `sin relacion o con otro tipo no hay ruta`() {
        assertNull(PushSalida.rutaDestino("salida_bloqueo", null))
        assertNull(PushSalida.rutaDestino("salida_bloqueo", "   "))
        assertNull(PushSalida.rutaDestino("revision_asignada", "01I26106418-1"))
        assertNull(PushSalida.rutaDestino(null, "01I26106418-1"))
    }

    @Test
    fun `el bloqueo se suprime solo si Autorizar relaciones esta abierta`() {
        assertTrue(PushSalida.debeSuprimir("salida_bloqueo", "R1", relacionAbierta = true, salidaBloqueadaRelno = null))
        assertFalse(PushSalida.debeSuprimir("salida_bloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = null))
        // La pantalla de bloqueo del cargador no es el aviso del supervisor.
        assertFalse(PushSalida.debeSuprimir("salida_bloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = "R1"))
    }

    @Test
    fun `el desbloqueo se suprime solo si el cargador ve la pantalla de esa relacion`() {
        assertTrue(PushSalida.debeSuprimir("salida_desbloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = "R1"))
        assertTrue(PushSalida.debeSuprimir("salida_desbloqueo", " r1 ", relacionAbierta = false, salidaBloqueadaRelno = "R1 "))
        assertFalse(PushSalida.debeSuprimir("salida_desbloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = "R2"))
        assertFalse(PushSalida.debeSuprimir("salida_desbloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = null))
        assertFalse(PushSalida.debeSuprimir("salida_desbloqueo", "R1", relacionAbierta = false, salidaBloqueadaRelno = "  "))
        // Estar en Autorizar relaciones no sustituye el aviso al cargador.
        assertFalse(PushSalida.debeSuprimir("salida_desbloqueo", "R1", relacionAbierta = true, salidaBloqueadaRelno = null))
    }

    @Test
    fun `otros avisos nunca se suprimen`() {
        assertFalse(PushSalida.debeSuprimir("revision_asignada", "R1", relacionAbierta = true, salidaBloqueadaRelno = "R1"))
        assertFalse(PushSalida.debeSuprimir(null, null, relacionAbierta = true, salidaBloqueadaRelno = "R1"))
    }
}
