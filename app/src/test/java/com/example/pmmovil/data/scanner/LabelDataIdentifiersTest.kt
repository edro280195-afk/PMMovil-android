package com.example.pmmovil.data.scanner

import com.example.pmmovil.data.scanner.LabelDataIdentifiers.Dato
import com.example.pmmovil.data.scanner.LabelDataIdentifiers.Forma
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El lector de identificadores contra las cuatro etiquetas reales de Freudenberg y contra lo que NO
 * debe leer: la bolsa de valores sueltos y las lecturas malas que da el video.
 */
class LabelDataIdentifiersTest {

    // ── Las etiquetas reales ────────────────────────────────────────────────

    private val quimica = "+30S0049379668+Q0073.500+H2612012010+2D10102027+2Y565310102026+P"
    private val caja = "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003"

    /** Envío VDA de Freudenberg-NOK: ocho Code 39, cada uno con su identificador adentro. */
    private val envioVda = listOf(
        "N81706661", "P000000000075370203", "Q20000", "V0000000305",
        "S279544448", "H5004029781", "30S75370203", "D261012"
    )

    /** Bolsa de Taiwán: ocho códigos con el valor pelón, sin identificador. */
    private val bolsaTaiwan = listOf(
        "4505747759", "75183644", "200", "1Y-00000050337428", "667", "2Q26", "TAIWAN", "5003814158"
    )

    @Test
    fun `etiqueta quimica, todo en un codigo`() {
        val leido = LabelDataIdentifiers.leer(listOf(quimica))
        assertEquals(Forma.CONCATENADA, leido.forma)
        assertEquals("0049379668", leido[Dato.ARTICULO])
        assertEquals("0073.500", leido[Dato.CANTIDAD])
        assertEquals("2612012010", leido[Dato.LOTE])
        assertEquals("10102027", leido[Dato.FECHA])
        assertEquals("565310102026", leido[Dato.CONTRATO])
    }

    @Test
    fun `el contrato en 2Y ya no se pierde`() {
        // Antes solo se conocía 2X, el de la caja, y el segmento de la química se tiraba en silencio.
        assertEquals("565310102026", LabelDataIdentifiers.leer(listOf(quimica))[Dato.CONTRATO])
        assertEquals("497608092026", LabelDataIdentifiers.leer(listOf(caja))[Dato.CONTRATO])
    }

    @Test
    fun `caja de Mexico, con parte del cliente con diagonal`() {
        val leido = LabelDataIdentifiers.leer(listOf(caja))
        assertEquals("0049263333", leido[Dato.ARTICULO])
        assertEquals("00000600", leido[Dato.CANTIDAD])
        assertEquals("0031704192", leido[Dato.LOTE])
        assertEquals("092036", leido[Dato.FECHA])
        assertEquals("049879/003", leido[Dato.PARTE_CLIENTE])
    }

    @Test
    fun `envio VDA, un codigo por campo`() {
        val leido = LabelDataIdentifiers.leer(envioVda)
        assertEquals(Forma.POR_CAMPO, leido.forma)
        assertEquals("000000000075370203", leido[Dato.PARTE_CLIENTE])
        assertEquals("75370203", leido[Dato.ARTICULO])
        assertEquals("20000", leido[Dato.CANTIDAD])
        assertEquals("5004029781", leido[Dato.LOTE])
        assertEquals("279544448", leido[Dato.ID_EMPAQUE])
        assertEquals("0000000305", leido[Dato.PROVEEDOR])
        assertEquals("81706661", leido[Dato.NOTA_ENTREGA])
        assertEquals("261012", leido[Dato.FECHA_EMBARQUE])
    }

    @Test
    fun `la clave del envio VDA cuadra con la partida quitando los ceros`() {
        val leido = LabelDataIdentifiers.leer(envioVda)
        // En la base la partida 3055981 tiene cnoparte = 75370203.
        assertEquals("75370203", LabelDataIdentifiers.sinCerosALaIzquierda(leido.clave!!))
    }

    // ── Lo que NO debe leer ─────────────────────────────────────────────────

    @Test
    fun `la bolsa de valores sueltos no se interpreta como identificadores`() {
        // Ninguno de sus ocho códigos empieza con un identificador conocido, y aunque alguno lo
        // hiciera no llegarían a tres: la bolsa sigue por el camino de la geometría.
        assertTrue(LabelDataIdentifiers.leer(bolsaTaiwan).vacia)
    }

    @Test
    fun `un solo codigo con prefijo no basta`() {
        // Un lote que por casualidad empiece con S no convierte la etiqueta en una de identificadores.
        assertTrue(LabelDataIdentifiers.leer(listOf("S12345", "ALGO", "OTRO")).vacia)
    }

    @Test
    fun `dos codigos del mismo campo con valores distintos se descartan`() {
        // Dos etiquetas en el mismo cuadro: no se sabe de cuál es cada dato.
        val leido = LabelDataIdentifiers.leer(
            listOf("P000000000075370203", "Q20000", "H5004029781", "S279544448", "S279544449")
        )
        assertNull(leido[Dato.ID_EMPAQUE])
        // Los demás campos sí se conservan: solo el contradictorio se cae.
        assertEquals("20000", leido[Dato.CANTIDAD])
    }

    @Test
    fun `un codigo concatenado que se contradice no se lee`() {
        assertNull(LabelDataIdentifiers.leerConcatenado("+30S0049379668+Q0073.500+30S0049379999"))
    }

    @Test
    fun `las lecturas malas del video no pasan la validacion de forma`() {
        // Medidas en el Pixel el 19-oct: el Code 39 se equivoca en los caracteres repetidos.
        // «FGS75370203» no empieza con un identificador conocido y los falsos positivos tampoco.
        val basura = listOf("TLN7BBO", "TTLTLTG", "J.000", "FGS75370203")
        assertTrue(LabelDataIdentifiers.leer(basura).vacia)
    }

    @Test
    fun `un valor vacio detras del identificador se ignora`() {
        // La química termina en «+P» sin valor: no debe inventarse una parte de cliente vacía.
        assertNull(LabelDataIdentifiers.leer(listOf(quimica))[Dato.PARTE_CLIENTE])
    }

    @Test
    fun `una cantidad que no es numero no se acepta`() {
        assertNull(LabelDataIdentifiers.leerConcatenado("+30S0049379668+QABC+H2612012010")?.get(Dato.CANTIDAD))
    }

    @Test
    fun `los ceros a la izquierda solo se quitan en valores numericos`() {
        assertEquals("49379668", LabelDataIdentifiers.sinCerosALaIzquierda("0049379668"))
        assertEquals("049879/003", LabelDataIdentifiers.sinCerosALaIzquierda("049879/003"))
        assertEquals("0", LabelDataIdentifiers.sinCerosALaIzquierda("000"))
    }
}
