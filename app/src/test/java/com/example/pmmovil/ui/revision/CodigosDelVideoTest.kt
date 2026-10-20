package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.scanner.LabelDataIdentifiers
import com.example.pmmovil.data.scanner.LabelDataIdentifiers.Dato
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El acumulador de códigos del video, con lo medido en el Pixel el 20-oct-2026 leyendo la etiqueta de
 * envío VDA de Freudenberg-NOK (partida 3055981).
 */
class CodigosDelVideoTest {

    @Test
    fun `un codigo visto una sola vez no cuenta`() {
        val a = CodigosDelVideo()
        a.agregar(listOf("Q20000"))
        assertTrue(a.confirmados().isEmpty())
    }

    @Test
    fun `visto en dos cuadros ya cuenta`() {
        val a = CodigosDelVideo()
        a.agregar(listOf("Q20000"))
        a.agregar(listOf("Q20000"))
        assertEquals(listOf("Q20000"), a.confirmados())
    }

    @Test
    fun `la basura del video se queda fuera`() {
        // Medidos en el Pixel: el Code 39 produce estos de vez en cuando, nunca dos veces iguales.
        val a = CodigosDelVideo()
        a.agregar(listOf("30S75370203", "RTTT"))
        a.agregar(listOf("30S75370203", "2730101111111"))
        a.agregar(listOf("30S75370203"))
        assertEquals(listOf("30S75370203"), a.confirmados())
    }

    @Test
    fun `el proveedor mal leido no desplaza al bueno`() {
        // En la sesión real salió V0000305 una vez y V0000000305 varias.
        val a = CodigosDelVideo()
        a.agregar(listOf("V0000305"))
        a.agregar(listOf("V0000000305"))
        a.agregar(listOf("V0000000305"))
        assertEquals(listOf("V0000000305"), a.confirmados())
    }

    @Test
    fun `avisa solo cuando cambia el conjunto confirmado`() {
        val a = CodigosDelVideo()
        assertFalse("el primer avistamiento aún no confirma", a.agregar(listOf("Q20000")))
        assertTrue("el segundo sí", a.agregar(listOf("Q20000")))
        assertFalse("el tercero no cambia nada", a.agregar(listOf("Q20000")))
    }

    @Test
    fun `limpiar no deja rastro de la etiqueta anterior`() {
        val a = CodigosDelVideo()
        a.agregar(listOf("S279544448"))
        a.agregar(listOf("S279544448"))
        a.limpiar()
        assertTrue(a.confirmados().isEmpty())
    }

    @Test
    fun `la cantidad que el video vio rescata al empaque que la foto dejo sin contar`() {
        // La lectura real de las 11:21: el video vio Q20000 y la foto no lo trajo, así que no había
        // cantidad y el empaque no se podía contar. Con el video sumado, sí la hay.
        val deLaFoto = listOf(
            "V0000000305", "H5004029781", "30S75370203", "P000000000075370203",
            "2730101111111", "S279544448", "RTTT"
        )
        val video = CodigosDelVideo()
        video.agregar(listOf("30S75370203", "N81706661", "Q20000", "H5004029781"))
        video.agregar(listOf("30S75370203", "N81706661", "Q20000", "H5004029781"))

        assertNull("sin el video no hay cantidad", LabelDataIdentifiers.leer(deLaFoto)[Dato.CANTIDAD])

        val juntos = (deLaFoto + video.confirmados().filterNot { it in deLaFoto }).distinct()
        val leido = LabelDataIdentifiers.leer(juntos)
        assertEquals("20000", leido[Dato.CANTIDAD])
        // Y lo que ya traía la foto no se pierde.
        assertEquals("5004029781", leido[Dato.LOTE])
        assertEquals("279544448", leido[Dato.ID_EMPAQUE])
        assertEquals("75370203", LabelDataIdentifiers.sinCerosALaIzquierda(leido.clave!!))
    }

    @Test
    fun `si el video contradice a la foto ese campo se descarta`() {
        // El seguro: un Q mal leído por el video no puede imponerse sobre el de la foto.
        val deLaFoto = listOf("30S75370203", "Q20000", "H5004029781")
        val juntos = (deLaFoto + listOf("Q2000000")).distinct()
        assertNull(LabelDataIdentifiers.leer(juntos)[Dato.CANTIDAD])
    }
}
