package com.example.pmmovil.data.api

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FotosMultipartTest {

    @Test
    fun `un API anterior sin la ruta de archivos se reconoce para mandar base64`() {
        assertTrue(FotosMultipart.sinRutaDeArchivos(404))
        assertTrue(FotosMultipart.sinRutaDeArchivos(405))
        assertTrue(FotosMultipart.sinRutaDeArchivos(415))
    }

    @Test
    fun `los demas errores no cambian a base64`() {
        // 400 (foto inválida), 401/403 (sesión o permiso), 409 y 5xx se contestan igual por cualquiera de las dos rutas.
        listOf(200, 400, 401, 403, 409, 500, 503).forEach { codigo ->
            assertFalse("HTTP $codigo", FotosMultipart.sinRutaDeArchivos(codigo))
        }
    }

    @Test
    fun `los campos de texto viajan sin comillas`() {
        val buffer = Buffer()
        FotosMultipart.texto("true").writeTo(buffer)
        assertEquals("true", buffer.readUtf8())
    }
}
