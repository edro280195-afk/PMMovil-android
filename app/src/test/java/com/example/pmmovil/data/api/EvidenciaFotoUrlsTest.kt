package com.example.pmmovil.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenciaFotoUrlsTest {

    private val base = "https://revisionapi.grupopmm.com/api/"

    @Test
    fun `revision pide la miniatura con su version para guardarse para siempre`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/ver/4521?tam=mini&v=11a9-6554f1a2",
            EvidenciaFotoUrls.revision(base, 4521, CasetaFotoUrls.Tamano.Mini, "11a9-6554f1a2")
        )
    }

    @Test
    fun `el original de revision sin version es la direccion de siempre`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/ver/4521",
            EvidenciaFotoUrls.revision(base, 4521, CasetaFotoUrls.Tamano.Original, "  ")
        )
    }

    @Test
    fun `entrada y danio van cada una a su ruta`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/entrada/ver/01I26106450/3?tam=vista&v=a-b",
            EvidenciaFotoUrls.entrada(base, "01I26106450", 3, danio = false, tamano = CasetaFotoUrls.Tamano.Vista, version = "a-b")
        )
        assertEquals(
            "http://localhost:5000/api/Foto/entrada-danio/ver/01I26106450/1?v=a-b",
            EvidenciaFotoUrls.entrada("http://localhost:5000/api", " 01I26106450 ", 1, danio = true, tamano = CasetaFotoUrls.Tamano.Original, version = "a-b")
        )
    }

    @Test
    fun `con version hay tres tamanos distintos`() {
        val urls = EvidenciaFotoUrls.revision(base, 77, "4d-1")
        assertEquals("https://revisionapi.grupopmm.com/api/Foto/ver/77?tam=mini&v=4d-1", urls.mini)
        assertEquals("https://revisionapi.grupopmm.com/api/Foto/ver/77?tam=vista&v=4d-1", urls.vista)
        assertEquals("https://revisionapi.grupopmm.com/api/Foto/ver/77?v=4d-1", urls.original)
    }

    @Test
    fun `sin version (servidor anterior) se usa solo el original para bajarlo una vez y no tres`() {
        val urls = EvidenciaFotoUrls.entrada(base, "01I26106450", 2, danio = false, version = null)
        assertEquals("https://revisionapi.grupopmm.com/api/Foto/entrada/ver/01I26106450/2", urls.mini)
        assertEquals(urls.mini, urls.vista)
        assertNull(urls.original)

        val enBlanco = EvidenciaFotoUrls.revision(base, 9, "   ")
        assertEquals("https://revisionapi.grupopmm.com/api/Foto/ver/9", enBlanco.vista)
        assertNull(enBlanco.original)
    }
}
