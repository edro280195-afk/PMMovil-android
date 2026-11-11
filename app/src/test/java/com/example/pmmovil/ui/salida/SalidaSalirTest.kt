package com.example.pmmovil.ui.salida

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaSalirTest {

    private fun decidir(
        stage: SalidaStage,
        mode: SalidaMode = SalidaMode.NORMAL,
        relno: String? = "01I26104482",
        hayAvance: Boolean = false,
        ocupado: Boolean = false
    ) = SalidaSalir.decidir(mode, stage, relno, hayAvance, ocupado)

    @Test
    fun `cargando una relacion sin avance se libera y se sale`() {
        assertEquals(SalidaAlSalir.LIBERAR_Y_SALIR, decidir(SalidaStage.ACTIVE))
    }

    @Test
    fun `con bultos escaneados sin guardar se pregunta antes de soltar la relacion`() {
        assertEquals(SalidaAlSalir.PEDIR_CONFIRMACION, decidir(SalidaStage.ACTIVE, hayAvance = true))
    }

    @Test
    fun `la descarga tambien tiene la relacion tomada`() {
        assertEquals(SalidaAlSalir.LIBERAR_Y_SALIR, decidir(SalidaStage.DESCARGA))
        assertEquals(SalidaAlSalir.PEDIR_CONFIRMACION, decidir(SalidaStage.DESCARGA, hayAvance = true))
    }

    @Test
    fun `sin relacion tomada se sale sin mas`() {
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.IDLE))
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.BLOCKED))
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.FINISHED))
    }

    @Test
    fun `la entrada directa no es una relacion y no hay nada que liberar`() {
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.ACTIVE, mode = SalidaMode.SIN_RELACION, hayAvance = true))
    }

    @Test
    fun `sin numero de relacion no hay nada que liberar`() {
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.ACTIVE, relno = ""))
        assertEquals(SalidaAlSalir.SALIR, decidir(SalidaStage.ACTIVE, relno = null))
    }

    @Test
    fun `mientras se busca la relacion el gesto espera para no dejarla pegada`() {
        assertEquals(SalidaAlSalir.ESPERAR, decidir(SalidaStage.LOADING, relno = null))
        assertEquals(SalidaAlSalir.ESPERAR, decidir(SalidaStage.LOADING))
    }

    @Test
    fun `con una operacion en curso el gesto espera`() {
        assertEquals(SalidaAlSalir.ESPERAR, decidir(SalidaStage.ACTIVE, ocupado = true))
        assertEquals(SalidaAlSalir.ESPERAR, decidir(SalidaStage.IDLE, ocupado = true))
    }

    @Test
    fun `solo se tiene tomada la relacion al cargar o descargar con relacion`() {
        assertTrue(SalidaSalir.tieneRelacionTomada(SalidaMode.NORMAL, SalidaStage.ACTIVE, "01I26104482"))
        assertTrue(SalidaSalir.tieneRelacionTomada(SalidaMode.NORMAL, SalidaStage.DESCARGA, "01I26104482"))
        assertFalse(SalidaSalir.tieneRelacionTomada(SalidaMode.NORMAL, SalidaStage.BLOCKED, "01I26104482"))
        assertFalse(SalidaSalir.tieneRelacionTomada(SalidaMode.NORMAL, SalidaStage.FINISHED, "01I26104482"))
        assertFalse(SalidaSalir.tieneRelacionTomada(SalidaMode.SIN_RELACION, SalidaStage.ACTIVE, "01I26104482"))
        assertFalse(SalidaSalir.tieneRelacionTomada(SalidaMode.NORMAL, SalidaStage.ACTIVE, " "))
    }

    @Test
    fun `una relacion pausada vuelve a EN ESPERA y cualquier otra queda en blanco`() {
        assertEquals("EN ESPERA", SalidaSalir.restaurar("EN ESPERA"))
        assertNull(SalidaSalir.restaurar(""))
        assertNull(SalidaSalir.restaurar("CARGANDO"))
        assertNull(SalidaSalir.restaurar("INCOMPLETA"))
        assertNull(SalidaSalir.restaurar(null))
    }
}
