package com.example.pmmovil.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogIndexTest {

    private val catalogo = listOf(
        CatalogoItem("3300", "WATSON PHILLIPS"),
        CatalogoItem("33", "TREINTA Y TRES, S.A."),
        CatalogoItem("CL8400", "FREUDENBERG-NOK"),
        CatalogoItem("CL7575", "TC HEARTLAND"),
        CatalogoItem("N1", "NIÑO Y COMPAÑÍA"),
        CatalogoItem("T1", "TRANSPORTES WATSON DEL NORTE")
    )

    private val indice = CatalogIndex(catalogo, { it.id }, { it.nombre })

    @Test
    fun `sin texto devuelve los primeros en el orden del catalogo`() {
        assertEquals(catalogo.take(3), indice.search("", 3))
        assertEquals(catalogo.take(2), indice.search("   ", 2))
    }

    @Test
    fun `encuentra por cualquier parte del nombre sin importar mayusculas`() {
        assertEquals(listOf("3300", "T1"), indice.search("watson", 10).map { it.id })
    }

    @Test
    fun `encuentra por el codigo`() {
        assertEquals(listOf("CL8400"), indice.search("cl84", 10).map { it.id })
    }

    @Test
    fun `no distingue acentos ni la enie`() {
        assertEquals(listOf("N1"), indice.search("nino", 10).map { it.id })
        assertEquals(listOf("N1"), indice.search("COMPANIA", 10).map { it.id })
        assertEquals(listOf("N1"), indice.search("niño compañía", 10).map { it.id })
    }

    @Test
    fun `todas las palabras escritas deben aparecer`() {
        assertEquals(listOf("T1"), indice.search("watson norte", 10).map { it.id })
        assertTrue(indice.search("watson sur", 10).isEmpty())
    }

    @Test
    fun `el codigo exacto sale primero aunque este despues en el catalogo`() {
        // "33" tambien esta contenido en "3300", pero quien teclea 33 busca el cliente 33.
        assertEquals(listOf("33", "3300"), indice.search("33", 10).map { it.id })
    }

    @Test
    fun `los que empiezan con lo escrito salen antes que los que lo traen en medio`() {
        // "WATSON PHILLIPS" empieza con watson; "TRANSPORTES WATSON..." lo trae en medio.
        assertEquals(listOf("3300", "T1"), indice.search("watson", 10).map { it.id })
        val invertido = CatalogIndex(catalogo.reversed(), { it.id }, { it.nombre })
        assertEquals(listOf("3300", "T1"), invertido.search("watson", 10).map { it.id })
    }

    @Test
    fun `respeta el limite`() {
        assertEquals(1, indice.search("watson", 1).size)
        assertTrue(indice.search("watson", 0).isEmpty())
    }

    @Test
    fun `un catalogo vacio no truena`() {
        val vacio = CatalogIndex(emptyList<CatalogoItem>(), { it.id }, { it.nombre })
        assertTrue(vacio.search("lo que sea", 5).isEmpty())
        assertEquals(0, vacio.size)
    }
}
