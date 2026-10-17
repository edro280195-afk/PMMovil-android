package com.example.pmmovil.ui.revision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pmmovil.MainActivity
import com.example.pmmovil.R
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.data.ocr.PartidaMatcher
import com.example.pmmovil.ui.revision.LabelCaptureViewModel.CampoPropuesto
import com.example.pmmovil.ui.revision.LabelCaptureViewModel.Estado
import com.example.pmmovil.ui.theme.PMMovilTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La tarjeta de un toque con los cuatro casos del mockup aprobado, sobre un dispositivo real.
 * Las capturas quedan en `Android/data/<paquete>/files/tarjeta/` para compararlas con el mockup.
 */
@RunWith(AndroidJUnit4::class)
class LabelCaptureCardInstrumentedTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var estado by mutableStateOf(Estado())
    private val guardados = mutableListOf<Boolean>()
    private var avanzo = false

    @Test
    fun todoCoincide_dejaElBotonUnicoListo() {
        mostrar(casoTodoCoincide())

        composeRule.onNodeWithText(texto(R.string.label_capture_compare_title)).assertExists()
        composeRule.onNodeWithText(texto(R.string.label_capture_primary_confirm)).assertIsEnabled()
        composeRule.onNodeWithText(texto(R.string.label_capture_pill_exact)).assertExists()
        capturar("1-todo-coincide")

        composeRule.onNodeWithText(texto(R.string.label_capture_primary_confirm)).performClick()
        assertEquals(listOf(true), guardados)
    }

    @Test
    fun unaDiferencia_exigeDecidirAntesDeConfirmar() {
        mostrar(casoUnaDiferencia())

        composeRule.onNodeWithText(texto(R.string.label_capture_primary_resolve_one)).assertIsNotEnabled()
        capturar("2-una-diferencia-sin-decidir")

        composeRule.onNodeWithText(texto(R.string.label_capture_use_value, "DEMOTEC")).performClick()
        composeRule.onNodeWithText(texto(R.string.label_capture_primary_confirm)).assertIsEnabled()
        capturar("2-una-diferencia-decidida")
    }

    @Test
    fun sinPartida_ofreceLasSugerenciasYElCotejoAparecealElegir() {
        mostrar(casoSinPartida())

        composeRule.onNodeWithText(texto(R.string.label_capture_unknown_title)).assertExists()
        composeRule.onNodeWithText(texto(R.string.label_capture_primary_pick)).assertIsNotEnabled()
        capturar("3-sin-partida")

        composeRule.onNodeWithText("Válvula de bola 3/4 in").performClick()
        composeRule.onNodeWithText(texto(R.string.label_capture_compare_title)).assertExists()
        composeRule.onNodeWithText(texto(R.string.label_capture_pill_chosen)).assertExists()
        capturar("3-elegida-por-el-revisador")
    }

    @Test
    fun rombo_cotejaUnClaseYGrupoContraElHazmatDeLaReferencia() {
        mostrar(casoRombo())

        composeRule.onNodeWithText(texto(R.string.label_capture_hazmat_scope)).assertExists()
        composeRule.onNodeWithText(texto(R.string.label_capture_primary_confirm)).assertIsEnabled()
        capturar("4-rombo")

        // Con tantas filas el resumen queda bajo el pliegue: el cuerpo tiene que poder desplazarse hasta él.
        composeRule.onNodeWithText(texto(R.string.label_capture_summary_saves), substring = true)
            .performScrollTo().assertIsDisplayed()
        capturar("4-rombo-resumen")
    }

    @Test
    fun claveDistinta_pideConfirmarLaIdentidadAntesDeSeguir() {
        mostrar(casoTodoCoincide().let { base ->
            base.copy(
                campos = base.campos.map {
                    if (it.leido.campo == Campo.NUMERO_PARTE) propuesta(Campo.NUMERO_PARTE, "ZZ-999") else it
                },
                identityAccepted = false
            )
        })

        composeRule.onNodeWithText(texto(R.string.label_capture_primary_identity)).assertIsNotEnabled()
        capturar("5-clave-distinta")

        composeRule.onNodeWithText(texto(R.string.label_capture_confirm_identity)).performClick()
        composeRule.onNodeWithText(texto(R.string.label_capture_primary_confirm)).assertIsEnabled()
    }

    @Test
    fun guardada_muestraElListoYAvanzaSola() {
        mostrar(
            casoTodoCoincide().copy(
                syncStatus = LabelCaptureStatus.PENDING, envio = LabelCaptureViewModel.Envio.GUARDADO, marcadaRevisada = true
            )
        )

        composeRule.onNodeWithText(texto(R.string.label_capture_success_reviewed)).assertExists()
        capturar("6-listo")
        composeRule.waitUntil(timeoutMillis = 5_000) { avanzo }
        assertTrue(avanzo)
    }

    @Test
    fun sinDatos_ofreceTomarOtraFoto() {
        mostrar(casoTodoCoincide().copy(campos = emptyList()))

        composeRule.onNodeWithText(texto(R.string.label_capture_nothing_read_selected)).assertExists()
        // «Tomar otra foto» es el botón principal y no se repite en el pie.
        assertEquals(1, composeRule.onAllNodesWithText(texto(R.string.label_capture_take_another)).fetchSemanticsNodes().size)
        composeRule.onNodeWithText(texto(R.string.label_capture_not_this)).assertExists()
        capturar("7-sin-datos")
    }

    // ---- Casos del mockup ----

    private fun casoTodoCoincide(): Estado {
        val p = partida(1, "HX-4410-B", "Tornillo hexagonal de acero", cant = 100.0)
        return Estado(
            fotoPath = fotoDePrueba(), captureId = "demo-1", availablePartidas = listOf(p), selectedPartida = p,
            campos = listOf(
                propuesta(Campo.NUMERO_PARTE, "HX-4410-B"), propuesta(Campo.LOTE, "2604117-A"),
                propuesta(Campo.PAIS_ORIGEN, "THAILAND", escribirComo = "THA", detalle = "THA · Tailandia"),
                propuesta(Campo.MARCA, "DEMOTEC"), propuesta(Campo.CANTIDAD, "10", raw = "10 PCS PER BAG", escribible = false)
            ),
            snapshot = LabelCaptureSnapshot(marca = "DEMOTEC"), canEdit = true, canMarkReviewed = true, identityAccepted = true
        )
    }

    private fun casoUnaDiferencia(): Estado {
        val base = casoTodoCoincide()
        return base.copy(snapshot = LabelCaptureSnapshot(marca = "ALFAPARTS"))
    }

    private fun casoSinPartida(): Estado {
        val p1 = partida(1, "VB-0500", "Válvula de bola 1/2 in", cant = 40.0)
        val p2 = partida(2, "VB-0750", "Válvula de bola 3/4 in", cant = 25.0)
        val p3 = partida(3, "QC-10-03", "Conexión rápida 10 mm", cant = 200.0)
        val candidatos = listOf(
            PartidaMatcher.Candidate(p2, listOf("Descripción casi igual: «BALL VALVE 3/4»"), false, PartidaMatcher.MatchKind.DESCRIPTION_SUPPORT),
            PartidaMatcher.Candidate(p1, listOf("Misma familia: válvula de bola"), false, PartidaMatcher.MatchKind.DESCRIPTION_SUPPORT),
            PartidaMatcher.Candidate(p3, listOf("Mismo proveedor en la factura"), false, PartidaMatcher.MatchKind.DESCRIPTION_SUPPORT)
        )
        return Estado(
            fotoPath = fotoDePrueba(), captureId = "demo-3", availablePartidas = listOf(p1, p2, p3), candidates = candidatos,
            campos = listOf(
                propuesta(Campo.LOTE, "K2610-44"), propuesta(Campo.PAIS_ORIGEN, "ITALY", escribirComo = "ITA", detalle = "ITA · Italia"),
                propuesta(Campo.MARCA, "FLOWTEC")
            )
        )
    }

    private fun casoRombo(): Estado {
        val p = partida(1, "RA-27-1316", "Resina acrílica en tambo", cant = 836.0, unidad = "KG")
        return Estado(
            fotoPath = fotoDePrueba(), captureId = "demo-4", availablePartidas = listOf(p), selectedPartida = p,
            campos = listOf(
                propuesta(Campo.NUMERO_PARTE, "RA-27-1316"), propuesta(Campo.LOTE, "LB0587650"),
                propuesta(Campo.UN_HAZMAT, "1866"), propuesta(Campo.CLASE_HAZMAT, "3"), propuesta(Campo.GRUPO_EMBALAJE, "III"),
                propuesta(Campo.CANTIDAD, "200", raw = "200 KG NET", escribible = false)
            ),
            snapshot = LabelCaptureSnapshot(hazmatUn = "1866"), canEdit = true, canMarkReviewed = true, identityAccepted = true
        )
    }

    // ---- Apoyos ----

    private fun mostrar(inicial: Estado) {
        estado = inicial
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                PMMovilTheme {
                    LabelCaptureCard(
                        estado = estado,
                        onSelectPartida = { id ->
                            val elegida = estado.availablePartidas.first { it.IdRevdet == id }
                            estado = estado.copy(
                                selectedPartida = elegida, identityAccepted = true, canEdit = true, canMarkReviewed = true,
                                snapshot = LabelCaptureSnapshot()
                            )
                        },
                        onRejectPartida = { estado = estado.copy(selectedPartida = null, snapshot = null) },
                        onAcceptIdentity = { estado = estado.copy(identityAccepted = true) },
                        onDecide = { campo, decision -> estado = estado.copy(decisiones = estado.decisiones + (campo to decision)) },
                        onEdit = { _, _ -> },
                        onSave = { revisada -> guardados += revisada },
                        onImprove = {}, onRetake = {}, onClose = {}, onRetry = {}, onReloadContext = {}, onDiscard = {},
                        onNext = { avanzo = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun capturar(nombre: String) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        val captura = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val carpeta = File(contexto.getExternalFilesDir(null), "tarjeta").apply { mkdirs() }
        File(carpeta, "$nombre.png").outputStream().use { captura.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Una «etiqueta» dibujada para que la miniatura no salga vacía. */
    private fun fotoDePrueba(): String {
        val archivo = File(contexto.cacheDir, "tarjeta-foto-demo.jpg")
        val imagen = Bitmap.createBitmap(640, 460, Bitmap.Config.ARGB_8888)
        val lienzo = Canvas(imagen)
        lienzo.drawColor(Color.rgb(184, 149, 106))
        val papel = Paint().apply { color = Color.WHITE }
        lienzo.drawRect(40f, 36f, 600f, 424f, papel)
        val tinta = Paint().apply { color = Color.BLACK; textSize = 30f; isAntiAlias = true }
        listOf("INDUSTRIAS DEMO S.A.", "PART #: HX-4410-B", "LOT #: 2604117-A", "COO: THAILAND", "BRAND: DEMOTEC")
            .forEachIndexed { i, linea -> lienzo.drawText(linea, 70f, 100f + i * 56f, tinta) }
        archivo.outputStream().use { imagen.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return archivo.absolutePath
    }

    private fun texto(id: Int, vararg args: Any): String = contexto.getString(id, *args)

    private fun leida(campo: Campo, valor: String, raw: String = valor) =
        LabelFieldExtractor.CampoLeido(campo, valor, LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA, "ANCLA", 90, rawValue = raw)

    private fun propuesta(
        campo: Campo,
        valor: String,
        escribible: Boolean = true,
        detalle: String? = null,
        escribirComo: String = valor,
        raw: String = valor
    ) = CampoPropuesto(
        leido = leida(campo, valor, raw), escribible = escribible, aceptado = escribible,
        destino = LabelCaptureViewModel.Destino.PARTIDA, valorAEscribir = escribirComo, detalle = detalle
    )

    private fun partida(id: Int, parte: String, descripcion: String, cant: Double, unidad: String = "PZA") = PartidaRevisionDto(
        IdRevdet = id, Cnofact = "DEMO-2041", CnoParte = parte, cDescripcion = descripcion, Marca = null, Modelo = null,
        NumSer = null, NumLote = null, nCantFactura = cant, nCantOut = null, cPaisOrigen = null,
        CptCveTmateria = null, CptDescTmateria = null, Revisada = false, Obsrevpartida = null,
        Mnotapartida = null, nPeso = null, Cclienteid = null, UnidadFactura = unidad
    )
}
