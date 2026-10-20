package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Qué alcanza a leer **hoy** la app de la etiqueta de envío VDA de Freudenberg ([EnvioVdaFreudenbergFixture]).
 *
 * No es una prueba de reglas nuevas: es la **medición** que justifica rehacer el lector, y deja fijado lo que
 * se encontró el 19-oct-2026 para que se note en cuanto cambie. Cada caso dice qué debería leerse y qué se lee.
 *
 * Resumen de lo medido:
 *  1. Renglones separados (lo más probable): solo sale el número de parte; se pierden cantidad, lote y país,
 *     y el emparejador le adjudica la clave al rótulo «DESCRIPTION».
 *  2. Rótulo pegado a su valor: salen parte y país; siguen faltando cantidad y lote.
 *  3. Foto pobre (pocos códigos): el lote sale **con el rótulo adentro**, «NO. (H) 5004029781».
 *  4. Si los Code 128 traen su identificador MH10 adentro, como manda la VDA 4902: **no se lee nada**.
 */
class EnvioVdaFreudenbergDiagnosticoTest {

    private val claves = listOf(EnvioVdaFreudenbergFixture.CLAVE, "70 NBR S1OR71")

    private fun esPais(valor: String): Boolean =
        CountryCatalogMatcher.emparejar(
            valor,
            listOf(
                CountryCatalogMatcher.PaisCatalogo("THA", "TAILANDIA (REINO DE)"),
                CountryCatalogMatcher.PaisCatalogo("TWN", "TAIWAN (REPUBLICA DE CHINA)"),
                CountryCatalogMatcher.PaisCatalogo("MEX", "MEXICO")
            )
        ) != null

    private fun leer(codigos: List<String>, lineas: List<LineaDeTexto>): List<LabelFieldExtractor.CampoLeido> {
        val interpretacion = LabelBarcodeInterpreter.interpretar(codigos, claves, ::esPais, lineas)
        return LabelFieldExtractor.extractDetailed(
            text = EnvioVdaFreudenbergFixture.texto(lineas),
            codes = codigos,
            barcodeObservations = interpretacion.campos,
            ignoreOcrFor = interpretacion.camposSinOcr
        ).fields
    }

    private fun List<LabelFieldExtractor.CampoLeido>.valorDe(campo: Campo): String? =
        firstOrNull { it.campo == campo }?.valor

    @Test
    fun `con renglones separados solo sale el numero de parte`() {
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosSinIdentificador,
            EnvioVdaFreudenbergFixture.lineasSeparadas
        )
        assertEquals(EnvioVdaFreudenbergFixture.PARTE_CLIENTE, campos.valorDe(Campo.NUMERO_PARTE))
        // Lo que debería leerse y no se lee: los 20 000 PC, el lote y el país.
        assertEquals(null, campos.valorDe(Campo.CANTIDAD))
        assertEquals(null, campos.valorDe(Campo.LOTE))
        assertEquals(null, campos.valorDe(Campo.PAIS_ORIGEN))
    }

    @Test
    fun `con renglones separados el emparejador cree que la clave es la descripcion`() {
        val pares = LabelCaptionPairing.emparejar(
            EnvioVdaFreudenbergFixture.lineasSeparadas,
            EnvioVdaFreudenbergFixture.codigosSinIdentificador
        )
        // Ninguno de los rótulos de esta etiqueta («QUANTITY (Q)», «BATCH NO. (H)», …) se reconoce, porque el
        // identificador entre paréntesis rompe los patrones; el único que empareja es el equivocado.
        assertEquals(listOf(LabelCaptionPairing.Rotulo.DESCRIPCION), pares.map { it.rotulo })
        assertEquals(EnvioVdaFreudenbergFixture.PARTE_CLIENTE, pares.single().valor)
    }

    @Test
    fun `con el rotulo pegado al valor se gana el pais pero no la cantidad ni el lote`() {
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosSinIdentificador,
            EnvioVdaFreudenbergFixture.lineasPegadas
        )
        assertEquals(EnvioVdaFreudenbergFixture.PARTE_CLIENTE, campos.valorDe(Campo.NUMERO_PARTE))
        assertEquals("TH", campos.valorDe(Campo.PAIS_ORIGEN))
        assertEquals(null, campos.valorDe(Campo.CANTIDAD))
        assertEquals(null, campos.valorDe(Campo.LOTE))
    }

    @Test
    fun `con pocos codigos legibles el lote sale con el rotulo adentro`() {
        val campos = leer(
            listOf(EnvioVdaFreudenbergFixture.CLAVE, EnvioVdaFreudenbergFixture.LOTE),
            EnvioVdaFreudenbergFixture.lineasPegadas
        )
        // Con menos de cuatro códigos el texto vuelve a opinar y escribe el rótulo dentro del valor.
        assertEquals("NO. (H) ${EnvioVdaFreudenbergFixture.LOTE}", campos.valorDe(Campo.LOTE))
    }

    @Test
    fun `con los codigos reales de la etiqueta ahora sale parte, cantidad y lote`() {
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosReales,
            EnvioVdaFreudenbergFixture.lineasSeparadas
        )
        // Este es el caso de verdad: Code 39 con el identificador adentro, confirmado en el Pixel el
        // 19-oct. Antes de que existiera LabelDataIdentifiers esto devolvía CERO campos — el
        // «campos= vacío con 6 códigos decodificados» que vio el revisador.
        assertEquals(EnvioVdaFreudenbergFixture.PARTE_CLIENTE, campos.valorDe(Campo.NUMERO_PARTE))
        assertEquals(EnvioVdaFreudenbergFixture.CANTIDAD, campos.valorDe(Campo.CANTIDAD))
        assertEquals(EnvioVdaFreudenbergFixture.LOTE, campos.valorDe(Campo.LOTE))
    }

    @Test
    fun `con los codigos reales el lote ya no sale con el rotulo adentro`() {
        // El texto deja de opinar sobre lo que el código ya dijo, así que no vuelve a aparecer
        // «NO. (H) 5004029781» ni la cantidad tomada del peso del renglón de abajo.
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosReales,
            EnvioVdaFreudenbergFixture.lineasPegadas
        )
        assertEquals(EnvioVdaFreudenbergFixture.LOTE, campos.valorDe(Campo.LOTE))
        assertEquals(EnvioVdaFreudenbergFixture.CANTIDAD, campos.valorDe(Campo.CANTIDAD))
    }

    @Test
    fun `el peso neto de la etiqueta se lee con su unidad`() {
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosReales,
            EnvioVdaFreudenbergFixture.lineasSeparadas
        )
        val peso = campos.firstOrNull { it.campo == Campo.PESO_NETO }
        assertEquals("8", peso?.valor)
        assertEquals("KG", peso?.unidad)
    }

    @Test
    fun `se leen los dos pesos y cada uno queda en su campo`() {
        // La etiqueta trae NET WT KG 8 y GROSS WT KG 9. La base NO distingue neto de bruto — no hay
        // columna que lo diga ni en revdet ni en fact, y la pantalla rotula «Peso lb» y «Peso kg» a
        // secas —, así que el lector no elige: entrega los dos y que decida quien conoce el negocio.
        val campos = leer(
            EnvioVdaFreudenbergFixture.codigosReales,
            EnvioVdaFreudenbergFixture.lineasSeparadas
        )
        assertEquals("8", campos.valorDe(Campo.PESO_NETO))
        assertEquals("9", campos.valorDe(Campo.PESO_BRUTO))
    }
}
