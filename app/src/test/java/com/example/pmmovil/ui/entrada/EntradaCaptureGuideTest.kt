package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaFormSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntradaCaptureGuideTest {

    private val empty = EntradaFormSnapshot(fecha = "2026-11-05T09:30")

    private fun detail() = EntradaDetail(cantidad = 3.0, descripcion = "Cajas")

    @Test
    fun `una entrada vacia marca el consecutivo como lo siguiente`() {
        val guide = EntradaCaptureGuide.of(empty)

        assertEquals(CaptureField.CONSECUTIVO, guide.next)
        assertEquals(CaptureStatus.NEXT, guide.status(CaptureField.CONSECUTIVO))
        assertEquals(CaptureStatus.PENDING, guide.status(CaptureField.CLIENTE))
        // La bodega ya viene puesta y la fecha también.
        assertEquals(CaptureStatus.CAPTURED, guide.status(CaptureField.BODEGA))
        assertEquals(CaptureStatus.CAPTURED, guide.status(CaptureField.FECHA))
    }

    @Test
    fun `el siguiente avanza en el orden de la pantalla`() {
        var form = empty.copy(ref4 = "123456")
        assertEquals(CaptureField.CLIENTE, EntradaCaptureGuide.of(form).next)

        form = form.copy(codCliente = "CL7480", nomCliente = "FREUDENBERG")
        assertEquals(CaptureField.MANEJO, EntradaCaptureGuide.of(form).next)

        form = form.copy(manejo = "1")
        assertEquals(CaptureField.MERCANCIA, EntradaCaptureGuide.of(form).next)

        form = form.copy(detalles = listOf(detail()))
        assertNull(EntradaCaptureGuide.of(form).next)
    }

    @Test
    fun `los datos opcionales vacios quedan por capturar pero nunca son el siguiente`() {
        val form = empty.copy(
            ref4 = "1", codCliente = "CL1", manejo = "1", detalles = listOf(detail())
        )
        val guide = EntradaCaptureGuide.of(form)

        assertNull(guide.next)
        assertEquals(CaptureStatus.PENDING, guide.status(CaptureField.TRANSPORTE))
        assertEquals(CaptureStatus.PENDING, guide.status(CaptureField.CAJA))
        assertEquals(CaptureStatus.PENDING, guide.status(CaptureField.GUIA))
        assertEquals(CaptureStatus.PENDING, guide.status(CaptureField.PESO))
    }

    @Test
    fun `los datos condicionales solo cuentan cuando aplican`() {
        val base = empty.copy(ref4 = "1", codCliente = "CL1", manejo = "1")
        val sinHazmat = EntradaCaptureGuide.of(base)
        assertEquals(CaptureStatus.NONE, sinHazmat.status(CaptureField.HAZMAT_CLASE))
        assertEquals(CaptureStatus.NONE, sinHazmat.status(CaptureField.DANO_DESCRIPCION))
        assertEquals(CaptureStatus.NONE, sinHazmat.status(CaptureField.INBOND_VALOR))

        val conHazmat = EntradaCaptureGuide.of(base.copy(hazmat = true))
        assertEquals(CaptureField.HAZMAT_CLASE, conHazmat.next)
        assertEquals(CaptureStatus.PENDING, conHazmat.status(CaptureField.HAZMAT_UN))

        val hazmatCompleto = EntradaCaptureGuide.of(base.copy(hazmat = true, claseHazmat = "3", un = "1203"))
        assertEquals(CaptureField.MERCANCIA, hazmatCompleto.next)
        assertEquals(CaptureStatus.CAPTURED, hazmatCompleto.status(CaptureField.HAZMAT_UN))
    }

    @Test
    fun `solo hay un campo siguiente a la vez`() {
        val guide = EntradaCaptureGuide.of(empty)
        val next = CaptureField.entries.count { guide.status(it) == CaptureStatus.NEXT }
        assertEquals(1, next)
    }

    @Test
    fun `el peso en cualquiera de las dos unidades cuenta como capturado`() {
        assertEquals(CaptureStatus.CAPTURED, EntradaCaptureGuide.of(empty.copy(pesoKgs = 12.0)).status(CaptureField.PESO))
        assertEquals(CaptureStatus.CAPTURED, EntradaCaptureGuide.of(empty.copy(pesoLbs = 30.0)).status(CaptureField.PESO))
    }
}
