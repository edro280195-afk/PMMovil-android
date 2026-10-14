package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.CountryCatalogMatcher.PaisCatalogo
import com.example.pmmovil.data.ocr.CountryCatalogMatcher.Via
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * El catalogo de estas pruebas son los nombres **tal cual** estan en
 * `pmmtraf.paises`, copiados de la base. Si alguien los "arregla" para que se
 * vean mas bonitos, las pruebas dejan de representar la realidad.
 *
 * Los paises elegidos son los de mas trafico real: USA 57.8% y China 21.7% de
 * las partidas del ultimo ano.
 */
class CountryCatalogMatcherTest {

    private val catalogo = listOf(
        PaisCatalogo("USA", "ESTADOS UNIDOS DE AMERICA"),
        PaisCatalogo("CHN", "CHINA (REPUBLICA POPULAR)"),
        PaisCatalogo("TWN", "TAIWAN (REPUBLICA DE CHINA)"),
        PaisCatalogo("IND", "INDIA (REPUBLICA DE)"),
        PaisCatalogo("ITA", "ITALIA (REPUBLICA ITALIANA)"),
        PaisCatalogo("JPN", "JAPON"),
        PaisCatalogo("VNM", "VIETNAM (REPUBLICA SOCIALISTA DE)"),
        PaisCatalogo("MEX", "MEXICO (ESTADOS UNIDOS MEXICANOS)"),
        PaisCatalogo("DEU", "ALEMANIA (REPUBLICA FEDERAL DE)"),
        PaisCatalogo("THA", "TAILANDIA (REINO DE)"),
        PaisCatalogo("BRA", "BRASIL (REPUBLICA FEDERATIVA DE)"),
        PaisCatalogo("CAN", "CANADA"),
        PaisCatalogo("ESP", "ESPAÑA (REINO DE)"),
        PaisCatalogo("GBR", "REINO UNIDO DE LA GRAN BRETAÑA E IRLANDA DEL N"),
        PaisCatalogo("KOR", "COREA (REPUBLICA DE) (COREA DEL SUR)")
    )

    private fun codigo(texto: String) =
        CountryCatalogMatcher.emparejar(texto, catalogo)?.codigo

    @Test
    fun `el caso real de la bolsa Brikksen`() {
        // La etiqueta dice literalmente "COO:  THAILAND" y el catalogo
        // "TAILANDIA (REINO DE)". Sin traduccion esto nunca emparejaria.
        assertEquals("THA", codigo("COO:  THAILAND"))
    }

    @Test
    fun `nombres en ingles de los paises con mas trafico`() {
        assertEquals("USA", codigo("MADE IN USA"))
        assertEquals("CHN", codigo("CHINA"))
        assertEquals("DEU", codigo("Country of Origin: GERMANY"))
        assertEquals("VNM", codigo("PRODUCT OF VIETNAM"))
        assertEquals("KOR", codigo("SOUTH KOREA"))
    }

    @Test
    fun `no confunde Taiwan con China`() {
        // Trampa real: en el catalogo Taiwan es "TAIWAN (REPUBLICA DE CHINA)".
        // Un contains() ingenuo lo daria como China.
        assertEquals("TWN", codigo("TAIWAN"))
        assertEquals("CHN", codigo("CHINA"))
        assertEquals("TWN", codigo("MADE IN TAIWAN"))
    }

    @Test
    fun `acepta el codigo ISO de tres letras tal cual`() {
        val r = CountryCatalogMatcher.emparejar("DEU", catalogo)
        assertEquals("DEU", r?.codigo)
        assertEquals(Via.CODIGO_ISO3, r?.via)
    }

    @Test
    fun `acepta el codigo de dos letras`() {
        val r = CountryCatalogMatcher.emparejar("TH", catalogo)
        assertEquals("THA", r?.codigo)
        assertEquals(Via.CODIGO_ISO2, r?.via)
    }

    @Test
    fun `acepta el nombre en espanol del propio catalogo`() {
        // "ALEMANIA" no esta en el diccionario de ingles: tiene que resolverlo
        // contra el catalogo vivo, comparando antes del parentesis.
        val r = CountryCatalogMatcher.emparejar("HECHO EN ALEMANIA", catalogo)
        assertEquals("DEU", r?.codigo)
        assertEquals(Via.NOMBRE_CATALOGO, r?.via)
    }

    @Test
    fun `aguanta la puntuacion de las siglas`() {
        assertEquals("USA", codigo("U.S.A."))
        assertEquals("USA", codigo("Made in U.S.A."))
    }

    @Test
    fun `no empareja un pais metido dentro de otra palabra`() {
        // "INDIANA" contiene "INDIA".
        assertNull(codigo("INDIANA"))
        assertNull(codigo("MADE IN INDIANAPOLIS"))
    }

    @Test
    fun `con dos paises en la misma linea no adivina`() {
        assertNull(codigo("CHINA / TAIWAN"))
        assertNull(codigo("ASSEMBLED IN MEXICO WITH PARTS FROM CHINA"))
    }

    @Test
    fun `lo que no reconoce lo deja vacio`() {
        assertNull(codigo("ATLANTIS"))
        assertNull(codigo(""))
        assertNull(codigo("   "))
        assertNull(codigo("LOTE 2504298-135"))
    }

    @Test
    fun `sin catalogo no inventa`() {
        assertNull(CountryCatalogMatcher.emparejar("THAILAND", emptyList()))
    }
}
