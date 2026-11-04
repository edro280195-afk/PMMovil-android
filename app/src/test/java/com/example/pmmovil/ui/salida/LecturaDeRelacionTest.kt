package com.example.pmmovil.ui.salida

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Lo que Salida (carga) hace con cada QR cuando espera una relación. Los textos son los de las hojas reales
 * (28-oct-2026): la orden de carga 01-I-26-106418 y la requisición de cruce de la relación 01-I-26-106070.
 */
class LecturaDeRelacionTest {

    private companion object {
        const val ORDEN_DE_CARGA = "01I26106418-1"
        const val REQUISICION = "R-01I26106070  0000  101"
        const val CARPETA = "01C2635628400001454  100"
    }

    @Test
    fun `the load order gives the relation and its version`() {
        assertEquals(LecturaDeRelacion.Relacion("01I26106418", "1"), leerRelacion(ORDEN_DE_CARGA))
        assertEquals(LecturaDeRelacion.Relacion("01I26106418", "2"), leerRelacion("01I26106418-2"))
    }

    @Test
    fun `the crossing requisition gives the relation even though it is longer than fifteen characters`() {
        // Antes medía más de 15 y se mandaba a validar como QR de caseta.
        assertEquals(LecturaDeRelacion.Relacion("01I26106070", VERSION_POR_OMISION), leerRelacion(REQUISICION))
        assertEquals(LecturaDeRelacion.Relacion("01I26106070", VERSION_POR_OMISION), leerRelacion("R-01I26106070     0000   101"))
        assertEquals(LecturaDeRelacion.Relacion("01I26106070", VERSION_POR_OMISION), leerRelacion("  r-01i26106070  0000  101\r\n"))
    }

    @Test
    fun `a folder label is reported as a folder so it is not searched as a relation`() {
        assertEquals(LecturaDeRelacion.Carpeta("01C263562840000"), leerRelacion(CARPETA))
        assertEquals(LecturaDeRelacion.Carpeta("01C263605020000"), leerRelacion("01C263605020000CL7480100"))
    }

    @Test
    fun `a typed relation and a gate QR are left to the usual validation`() {
        assertEquals(LecturaDeRelacion.Texto("01i26106418"), leerRelacion(" 01i26106418 "))
        // Una relación todavía en captura termina en P y no es un QR oficial.
        assertEquals(LecturaDeRelacion.Texto("01I26106418P"), leerRelacion("01I26106418P"))
        // El QR de caseta lleva guion y diagonal.
        assertEquals(LecturaDeRelacion.Texto("E-020468/26"), leerRelacion("E-020468/26"))
    }

    @Test
    fun `an entry is read from a folder label and a typed entry is kept`() {
        assertEquals("01C263562840000", leerEntrada(CARPETA))
        assertEquals("01C263605020000", leerEntrada("01C263605020000CL7480100"))
        assertEquals("01C263562840000", leerEntrada(" 01C263562840000 "))
    }
}
