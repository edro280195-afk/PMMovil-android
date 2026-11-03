package com.example.pmmovil.data.repository

import com.example.pmmovil.data.model.CasetaEntradaCatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TiposCajaCacheTest {

    private val tipos = listOf(
        CasetaEntradaCatalogItem(id = "56", nombre = "CAJA CERRADA 53'"),
        CasetaEntradaCatalogItem(id = "58", nombre = "CAJA REFRIGERADA 53'")
    )

    @Test
    fun `lo guardado se lee igual`() {
        val json = TiposCajaCache.serializar(tipos)

        assertEquals(tipos, TiposCajaCache.deserializar(json))
    }

    @Test
    fun `sin respaldo no hay tipos`() {
        assertTrue(TiposCajaCache.deserializar(null).isEmpty())
        assertTrue(TiposCajaCache.deserializar("").isEmpty())
    }

    @Test
    fun `un respaldo ilegible se ignora en vez de tirar la pantalla`() {
        assertTrue(TiposCajaCache.deserializar("esto no es json").isEmpty())
        assertTrue(TiposCajaCache.deserializar("{\"a\":1}").isEmpty())
    }

    @Test
    fun `los renglones sin id o sin nombre no se ofrecen`() {
        val sucio = TiposCajaCache.serializar(
            tipos + listOf(
                CasetaEntradaCatalogItem(id = "", nombre = "SIN ID"),
                CasetaEntradaCatalogItem(id = "99", nombre = "  ")
            )
        )

        assertEquals(tipos, TiposCajaCache.deserializar(sucio))
    }
}
