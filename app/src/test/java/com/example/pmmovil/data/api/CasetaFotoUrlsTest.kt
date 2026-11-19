package com.example.pmmovil.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class CasetaFotoUrlsTest {

    private val base = "https://revisionapi.grupopmm.com/api/"

    @Test
    fun `la miniatura lleva tam mini y la version para guardarse para siempre`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=3&tam=mini&v=1a2b-6554f1a2",
            CasetaFotoUrls.de(base, "E-021107/26", 3, CasetaFotoUrls.Tamano.Mini, "1a2b-6554f1a2")
        )
    }

    @Test
    fun `la vista lleva tam vista`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=1&tam=vista",
            CasetaFotoUrls.de(base, "E-021107/26", 1, CasetaFotoUrls.Tamano.Vista)
        )
    }

    @Test
    fun `el original es la misma direccion de siempre para que un servidor anterior la entienda`() {
        assertEquals(
            "https://revisionapi.grupopmm.com/api/Foto/caseta/ver?casetaId=E-021107%2F26&indice=7",
            CasetaFotoUrls.de(base, "E-021107/26", 7, CasetaFotoUrls.Tamano.Original, version = "   ")
        )
    }

    @Test
    fun `el folio con diagonal y espacios sobrantes se codifica y una base sin diagonal final no rompe la ruta`() {
        assertEquals(
            "http://localhost:5000/api/Foto/caseta/ver?casetaId=E-000001%2F26&indice=2&tam=mini",
            CasetaFotoUrls.de("http://localhost:5000/api", " E-000001/26 ", 2, CasetaFotoUrls.Tamano.Mini)
        )
    }
}
