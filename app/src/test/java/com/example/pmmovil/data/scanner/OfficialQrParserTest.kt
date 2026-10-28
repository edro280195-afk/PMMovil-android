package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los tres QR oficiales de Grupo PMM. Los textos son los que decodificó un lector de las etiquetas y hojas reales
 * (28-oct-2026): la carpeta de ExxonMobil, la orden de carga 01-I-26-106418 y la requisición de cruce del 12-oct.
 */
class OfficialQrParserTest {

    private companion object {
        const val CARPETA = "01C2635628400001454  100"
        // Escaneada con la cámara el 28-oct: el cliente (CL7480) ocupa las 6 posiciones y no queda espacio antes del 100.
        const val CARPETA_CLIENTE_LARGO = "01C263605020000CL7480100"
        const val ORDEN_DE_CARGA = "01I26106418-1"
        const val REQUISICION = "R-01I26106070  0000  101"
    }

    @Test
    fun `the folder label keeps the entry and drops the client`() {
        val carpeta = OfficialQrParser.parse(CARPETA) as OfficialQr.Carpeta

        assertEquals("01C263562840000", carpeta.entrada)
        assertEquals("01C263562840000", carpeta.identity)
    }

    @Test
    fun `the folder label reads the same with a client that fills its field and leaves no space`() {
        val carpeta = OfficialQrParser.parse(CARPETA_CLIENTE_LARGO) as OfficialQr.Carpeta

        assertEquals("01C263605020000", carpeta.entrada)
        assertEquals("01C263605020000", OfficialQrParser.entradaOf(CARPETA_CLIENTE_LARGO))
        assertEquals("01C263605020000", OfficialQrParser.entradaOf("  01c263605020000cl7480100\r\n"))
    }

    @Test
    fun `the entry is always the first fifteen characters whatever follows`() {
        listOf(
            "01C2635628400001454  100",
            "01C263562840000CL7480100",
            "01C263562840000      100",
            "01C263562840000ABCD123456 100",
            "01C263562840000 100"
        ).forEach { assertEquals("Lectura «$it»", "01C263562840000", OfficialQrParser.entradaOf(it)) }
    }

    @Test
    fun `the load order is the release and its version`() {
        val orden = OfficialQrParser.parse(ORDEN_DE_CARGA) as OfficialQr.OrdenDeCarga

        assertEquals("01I26106418", orden.relacion)
        assertEquals("1", orden.version)
        assertEquals("01I26106418", orden.identity)
        assertEquals("2", (OfficialQrParser.parse("01E17028336-2") as OfficialQr.OrdenDeCarga).version)
    }

    @Test
    fun `the crossing requisition is the release behind the R prefix`() {
        val requisicion = OfficialQrParser.parse(REQUISICION) as OfficialQr.RequisicionDeCruce

        assertEquals("01I26106070", requisicion.relacion)
        assertEquals("01I26106070", requisicion.identity)
        assertEquals("01I26106070", OfficialQrParser.relacionOf("R-01I26106070"))
        assertEquals("01C26106070", OfficialQrParser.relacionOf("r-01c26106070 5"))
    }

    @Test
    fun `padding case line breaks and spacing do not change what is read`() {
        assertEquals("01C263562840000", OfficialQrParser.entradaOf("  01c2635628400001454   100\r\n"))
        assertEquals("01C263562840000", OfficialQrParser.entradaOf("01C2635628400001454 100"))
        assertEquals("01I26106418", OfficialQrParser.relacionOf(" 01i26106418-1\r\n"))
        assertEquals("01I26106070", OfficialQrParser.relacionOf("R-01I26106070     0000   101\r\n"))
    }

    @Test
    fun `identity is the entry for a folder and the release for the other two`() {
        assertEquals("01C263562840000", OfficialQrParser.identityOf(CARPETA))
        assertEquals("01I26106418", OfficialQrParser.identityOf(ORDEN_DE_CARGA))
        assertEquals("01I26106070", OfficialQrParser.identityOf(REQUISICION))
    }

    @Test
    fun `each helper answers only for its own kind`() {
        assertNull(OfficialQrParser.relacionOf(CARPETA))
        assertNull(OfficialQrParser.entradaOf(ORDEN_DE_CARGA))
        assertNull(OfficialQrParser.entradaOf(REQUISICION))
    }

    @Test
    fun `codes that are not one of the three are left alone`() {
        listOf(
            "01C263582800000", // entrada sola: la carpeta que imprime esta app
            "01C263582800000 3-12", // etiqueta de bulto
            "01C263582800000 18-17", // bulto mutilado: se sigue rechazando
            "01C263582800000 3", // una posición sin total no es una carpeta
            "01C2635828000000", // una entrada con un carácter de más: la decide el servidor
            "01C263582800000AB", // dos caracteres de más tampoco bastan
            "01I26104546", // relación sola
            "E-017846/26", // caja de caseta
            "PMMPR|7K2Q-4481", // pre-registro
            "usuario;clave-qr-1234", // gafete
            "+30S77120045", // etiqueta de proveedor
            "ZX-0042", // referencia vieja
            "R-01X26106070", // la tercera letra de una relación es I, E o C
            "R-0126106070", // una relación con un dígito de menos
            "01I26106418-", // versión vacía
            "01I26106418-1234", // versión de cuatro dígitos
            ""
        ).forEach { assertNull("Lectura «$it»", OfficialQrParser.parse(it)) }
        assertNull(OfficialQrParser.parse(null))
    }

    @Test
    fun `a product barcode that starts with 01 is not taken for a folder`() {
        // GS1: «01» + GTIN de 14 dígitos. Una entrada siempre tiene una letra en la tercera posición.
        assertNull(OfficialQrParser.parse("0100012345678905 17261231"))
        assertNull(OfficialQrParser.parse("010001234567890517261231 100"))
    }

    @Test
    fun `a long read is not one of the three`() {
        assertNull(OfficialQrParser.parse("R-01I26106070 " + "X".repeat(120)))
    }

    @Test
    fun `none of the three shapes overlaps with the other codes the app reads`() {
        val oficiales = listOf(CARPETA, CARPETA_CLIENTE_LARGO, ORDEN_DE_CARGA, REQUISICION)
        oficiales.forEach {
            val normalizado = com.example.pmmovil.data.entryar.EntryArQrCodeParser.normalize(it)
            assertTrue(it, !LabelCodeShapes.caja.matches(normalizado))
            assertTrue(it, !LabelCodeShapes.relacion.matches(normalizado))
            assertTrue(it, !LabelCodeShapes.entrada.matches(normalizado))
            assertTrue(it, LabelCodeShapes.conPosicion.matchEntire(normalizado) == null)
        }
    }
}
