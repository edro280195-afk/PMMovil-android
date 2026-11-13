package com.example.pmmovil.data.printing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraHostIdentificationParserTest {

    @Test
    fun `lee modelo firmware y dpi de una ZT410 de 300 dpi`() {
        val id = ZebraHostIdentificationParser.parse("\u0002ZT410-300dpi,V75.20.14Z,12,4096KB\u0003\r\n")

        assertNotNull(id)
        assertEquals("ZT410-300dpi", id!!.model)
        assertEquals("V75.20.14Z", id.firmware)
        assertEquals(12, id.dotsPerMillimeter)
        assertEquals(300, id.printheadDpi)
    }

    @Test
    fun `traduce los puntos por milimetro a dpi`() {
        fun dpi(dpmm: Int) = ZebraHostIdentificationParser.parse("\u0002ZM400,V53.17.5Z,$dpmm,8192KB\u0003")!!.printheadDpi

        assertEquals(152, dpi(6))
        assertEquals(203, dpi(8))
        assertEquals(300, dpi(12))
        assertEquals(600, dpi(24))
        assertNull(dpi(7))
    }

    @Test
    fun `si falta el campo de puntos por mm usa el dpi del nombre del modelo`() {
        val id = ZebraHostIdentificationParser.parse("\u0002ZD620-203dpi,V93.21.2Z,,\u0003")

        assertEquals(203, id!!.printheadDpi)
    }

    @Test
    fun `una respuesta que no es la identificacion no se interpreta`() {
        assertNull(ZebraHostIdentificationParser.parse(""))
        assertNull(ZebraHostIdentificationParser.parse("\u0002\u0003\r\n"))
        assertNull(ZebraHostIdentificationParser.parse("\u0002solo-un-campo\u0003"))
    }

    @Test
    fun `reconoce cuando una respuesta SGD ya esta completa`() {
        fun complete(text: String) = ZebraSocketTransport.isSgdReplyComplete(text.toByteArray(Charsets.US_ASCII))

        assertTrue(complete("\"300\""))
        assertTrue(complete("\"300\"\r\n"))
        assertTrue(complete("?\r\n"))
        assertFalse(complete(""))
        assertFalse(complete("\"Z"))
        assertFalse(complete("ZT410")) // sin comillas ni salto: se decide por silencio
    }
}
