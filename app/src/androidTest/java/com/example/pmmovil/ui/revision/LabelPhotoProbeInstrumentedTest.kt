package com.example.pmmovil.ui.revision

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelBarcodeInterpreter
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.LocalTextOcr
import com.example.pmmovil.data.ocr.PaddleOcrEngine
import com.example.pmmovil.data.ocr.PartidaMatcher
import com.example.pmmovil.data.ocr.PhotoBarcodeScanner
import com.example.pmmovil.ui.revision.LabelCaptureViewModel.CampoPropuesto
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Herramienta de diagnóstico, no una prueba de regresión: lee las fotos de `files/probe/` con la misma
 * cadena que usa la captura de etiquetas (códigos, intérprete de códigos, ML Kit, Paddle, extractor,
 * emparejador y cotejo) y deja el resultado en `files/probe/informe.json`. Sin fotos en esa carpeta no
 * hace nada.
 *
 * `partidas.json` (opcional) son las partidas de una entrada: con ellas el informe dice qué partida
 * propondría el emparejador y qué filas mostraría la tarjeta frente a una partida sin datos capturados.
 * El informe también guarda la geometría de cada renglón de texto (esquinas), que es lo que hace falta
 * para emparejar cada valor con su rótulo.
 */
@RunWith(AndroidJUnit4::class)
class LabelPhotoProbeInstrumentedTest {
    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext

    // Países que aparecen en las etiquetas de prueba; la app usa el catálogo completo de la base.
    private val paises = setOf("TAIWAN", "VIETNAM", "THAILAND", "CHINA", "MEXICO", "ITALY", "USA", "INDONESIA")

    @Test
    fun leeLasFotosDeLaCarpetaProbe() = runBlocking {
        val carpeta = File(contexto.getExternalFilesDir(null), "probe")
        val fotos = carpeta.listFiles { f -> f.extension.lowercase() in setOf("jpg", "jpeg", "png") }
            ?.sortedBy { it.name }.orEmpty()
        assumeTrue("No hay fotos en ${carpeta.path}", fotos.isNotEmpty())
        val partidas = partidasDe(File(carpeta, "partidas.json"))
        val informe = JSONObject()
        fotos.forEach { foto -> informe.put(foto.name, analizar(foto, partidas)) }
        File(carpeta, "informe.json").writeText(informe.toString(2))
    }

    private suspend fun analizar(foto: File, partidas: List<PartidaRevisionDto>): JSONObject {
        val resultado = JSONObject()
        val inicio = System.currentTimeMillis()

        // 1) Todo lo que ML Kit logra decodificar, con su simbología y sus bytes crudos.
        val todos = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS).build()
        )
        val imagen = InputImage.fromFilePath(contexto, Uri.fromFile(foto))
        val t0 = System.currentTimeMillis()
        val codigos = Tasks.await(todos.process(imagen))
        todos.close()
        resultado.put("msTodosLosCodigos", System.currentTimeMillis() - t0)
        resultado.put("codigos", JSONArray().apply {
            codigos.forEach { c ->
                put(JSONObject().apply {
                    put("simbologia", formato(c.format))
                    put("valor", visible(c.rawValue))
                    put("bytes", c.rawBytes?.size ?: 0)
                    put("caja", c.boundingBox?.flattenToString())
                })
            }
        })

        // 2) Lo que la app realmente consume hoy: formatos acotados, solo valores.
        val t1 = System.currentTimeMillis()
        val deLaApp = PhotoBarcodeScanner(contexto).leer(foto.absolutePath, BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS)
            .getOrDefault(emptyList())
        resultado.put("msCodigosDeLaApp", System.currentTimeMillis() - t1)
        resultado.put("codigosDeLaApp", JSONArray(deLaApp.map(::visible)))

        // 3) Texto: ML Kit y Paddle, cada uno por su lado.
        val t2 = System.currentTimeMillis()
        val mlKit = LocalTextOcr(contexto).readFromFile(foto.absolutePath).getOrNull()
        resultado.put("msMlKit", System.currentTimeMillis() - t2)
        val t3 = System.currentTimeMillis()
        val paddle = PaddleOcrEngine(contexto).readFromFile(foto.absolutePath).getOrNull()
        resultado.put("msPaddle", System.currentTimeMillis() - t3)
        resultado.put("textoMlKit", mlKit?.rawText.orEmpty())
        resultado.put("textoPaddle", paddle?.rawText.orEmpty())

        // 3b) Geometría de cada renglón de ML Kit: texto y esquinas en el orden del propio renglón.
        resultado.put("renglonesMlKit", renglones(imagen))

        // 4) Intérprete de códigos, extractor con su resultado y emparejador/cotejo contra las partidas.
        val claves = partidas.flatMap { listOfNotNull(it.CnoParte, it.ParteCte) }
        val interpretacion = LabelBarcodeInterpreter.interpretar(
            deLaApp, claves, esPais = { it.uppercase() in paises }, lineas = mlKit?.lineas.orEmpty()
        )
        resultado.put("interprete", JSONObject().apply {
            put("campos", JSONArray().apply {
                interpretacion.campos.forEach { put(JSONObject().put("campo", it.campo.name).put("valor", it.valor).put("origen", it.origen.name)) }
            })
            put("sinOcr", JSONArray(interpretacion.camposSinOcr.map { it.name }))
        })
        resultado.put("extraccionMlKit", extraer(mlKit?.rawText.orEmpty(), deLaApp, interpretacion, partidas))
        resultado.put("extraccionPaddle", extraer(paddle?.rawText.orEmpty(), deLaApp, interpretacion, partidas))
        resultado.put("msTotal", System.currentTimeMillis() - inicio)
        return resultado
    }

    private fun renglones(imagen: InputImage): JSONArray {
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val texto = Tasks.await(reconocedor.process(imagen))
        reconocedor.close()
        return JSONArray().apply {
            texto.textBlocks.forEach { bloque ->
                bloque.lines.forEach { linea ->
                    put(JSONObject().apply {
                        put("texto", linea.text)
                        put("esquinas", JSONArray().apply {
                            linea.cornerPoints?.forEach { p -> put(JSONArray().put(p.x).put(p.y)) }
                        })
                        put("caja", linea.boundingBox?.flattenToString())
                    })
                }
            }
        }
    }

    private fun extraer(
        texto: String,
        codigos: List<String>,
        interpretacion: LabelBarcodeInterpreter.Interpretacion,
        partidas: List<PartidaRevisionDto>
    ): JSONObject {
        val extraccion = LabelFieldExtractor.extractDetailed(texto, codigos, interpretacion.campos, interpretacion.camposSinOcr)
        val salida = JSONObject()
        salida.put("campos", JSONArray().apply {
            extraccion.fields.forEach {
                put(JSONObject().put("campo", it.campo.name).put("valor", it.valor).put("origen", it.origen.name).put("ancla", it.ancla))
            }
        })
        salida.put("conflictos", JSONArray().apply {
            extraccion.conflicts.forEach { c ->
                put(JSONObject().put("campo", c.field.name).put("valores", JSONArray(c.observations.map { it.valor })))
            }
        })
        if (partidas.isNotEmpty()) {
            val propuesta = PartidaMatcher.match(partidas, extraccion.fields, texto)
            salida.put("partidaSugerida", propuesta.suggestedPartidaId)
            salida.put("requiereElegir", propuesta.requiresSelection)
            salida.put("candidatas", JSONArray().apply {
                propuesta.candidates.take(5).forEach {
                    put(JSONObject().put("id", it.partida.IdRevdet).put("clave", it.partida.CnoParte).put("tipo", it.matchKind.name)
                        .put("claveExacta", it.exactKeyMatch))
                }
            })
            // Lo que mostraría la tarjeta frente a la partida sugerida, con la partida tal como está en la base.
            val partida = partidas.firstOrNull { it.IdRevdet == propuesta.suggestedPartidaId }
            if (partida != null) {
                val propuestos = extraccion.fields.map { lectura ->
                    val escribible = lectura.campo in ESCRIBIBLES && lectura.valor.isNotBlank()
                    CampoPropuesto(lectura, escribible, escribible, valorAEscribir = lectura.valor)
                }
                val filas = LabelCaptureCotejo.comparar(
                    propuestos, LabelCaptureSnapshot(numLote = partida.NumLote, marca = partida.Marca, cPaisOrigen = partida.cPaisOrigen),
                    partida, extraccion.conflicts, emptySet(), emptyMap()
                )
                salida.put("partida", "${partida.IdRevdet} · ${partida.CnoParte} · ${partida.nCantFactura} ${partida.UnidadFactura} · ${partida.cPaisOrigen}")
                salida.put("tarjeta", JSONArray().apply {
                    filas.forEach { f ->
                        put(
                            JSONObject().put("campo", f.campo.name).put("tipo", f.tipo.name).put("declarado", f.declarado)
                                .put("leido", f.leido).put("detalle", f.detalle).put("cantidad", f.cantidad?.resultado?.name)
                        )
                    }
                })
            }
        }
        return salida
    }

    private fun partidasDe(archivo: File): List<PartidaRevisionDto> {
        if (!archivo.isFile) return emptyList()
        val arreglo = JSONArray(archivo.readText())
        return (0 until arreglo.length()).map { i ->
            val o = arreglo.getJSONObject(i)
            PartidaRevisionDto(
                IdRevdet = o.getString("idrevdet").toInt(), Cnofact = o.getString("cnofact"), CnoParte = o.getString("cnoparte"),
                cDescripcion = o.optString("descripcion"), Marca = o.optString("marca"), Modelo = o.optString("modelo"),
                NumSer = o.optString("numser"), NumLote = o.optString("numlote"), nCantFactura = o.getString("ncantfactura").toDouble(),
                nCantOut = null, cPaisOrigen = o.optString("pais"), CptCveTmateria = null, CptDescTmateria = null,
                Revisada = o.optString("revisada") == "1", Obsrevpartida = null, Mnotapartida = null, nPeso = null,
                Cclienteid = null, UnidadFactura = o.optString("unidad"), ParteCte = o.optString("parte_cte").ifBlank { null }
            )
        }
    }

    /** Los separadores de grupo y demás caracteres de control se escriben visibles. */
    private fun visible(valor: String?): String = valor.orEmpty().map { c ->
        if (c.code < 0x20 || c.code == 0x7f) "<0x%02X>".format(c.code) else c.toString()
    }.joinToString("")

    private fun formato(codigo: Int): String = when (codigo) {
        Barcode.FORMAT_CODE_128 -> "CODE_128"
        Barcode.FORMAT_CODE_39 -> "CODE_39"
        Barcode.FORMAT_CODE_93 -> "CODE_93"
        Barcode.FORMAT_CODABAR -> "CODABAR"
        Barcode.FORMAT_DATA_MATRIX -> "DATA_MATRIX"
        Barcode.FORMAT_EAN_13 -> "EAN_13"
        Barcode.FORMAT_EAN_8 -> "EAN_8"
        Barcode.FORMAT_ITF -> "ITF"
        Barcode.FORMAT_QR_CODE -> "QR_CODE"
        Barcode.FORMAT_UPC_A -> "UPC_A"
        Barcode.FORMAT_UPC_E -> "UPC_E"
        Barcode.FORMAT_PDF417 -> "PDF417"
        Barcode.FORMAT_AZTEC -> "AZTEC"
        else -> "OTRO($codigo)"
    }

    private companion object {
        val ESCRIBIBLES = setOf(Campo.LOTE, Campo.NUMERO_SERIE, Campo.PAIS_ORIGEN, Campo.MARCA, Campo.MODELO)
    }
}
