package com.example.pmmovil.ui.revision

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pmmovil.R
import com.example.pmmovil.data.preferences.ModePreferences.Mode
import com.example.pmmovil.ui.theme.PMMovilTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La hoja de modos con sus tres opciones. La prueba de la altura reproduce un equipo de 640 dp de alto, como el
 * Honeywell CT60: ahí, sin desplazamiento, «Por códigos de barras» quedaba cortado.
 * Las capturas quedan en `Android/data/<paquete>/files/modos/`.
 */
@RunWith(AndroidJUnit4::class)
class RevisionModeSelectorInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val contexto get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val elegidos = mutableListOf<Mode>()

    @Test
    fun muestraLosTresModos() {
        mostrar(preferido = Mode.NORMAL)

        composeRule.onNodeWithText(texto(R.string.revision_normal_mode)).assertIsDisplayed()
        composeRule.onNodeWithText(texto(R.string.revision_labels_mode)).assertIsDisplayed()
        composeRule.onNodeWithText(texto(R.string.revision_labels_mode_description)).assertIsDisplayed()
        composeRule.onNodeWithText(texto(R.string.revision_barcode_mode)).assertIsDisplayed()
        capturar("1-tres-modos")
    }

    @Test
    fun soloElPreferidoLlevaLaInsignia() {
        mostrar(preferido = Mode.LABELS)

        composeRule.onAllNodesWithText(texto(R.string.revision_preferred)).assertCountEquals(1)
        capturar("2-preferido-etiquetas")
    }

    @Test
    fun tocarEscaneoDeEtiquetasAvisaElModoNuevo() {
        mostrar(preferido = Mode.NORMAL)

        composeRule.onNodeWithText(texto(R.string.revision_labels_mode)).performClick()

        assertEquals(listOf(Mode.LABELS), elegidos)
    }

    @Test
    fun tocarLosOtrosDosModosSigueIgual() {
        mostrar(preferido = Mode.NORMAL)

        composeRule.onNodeWithText(texto(R.string.revision_normal_mode)).performClick()
        composeRule.onNodeWithText(texto(R.string.revision_barcode_mode)).performClick()

        assertEquals(listOf(Mode.NORMAL, Mode.BARCODE), elegidos)
    }

    @Test
    fun enUnEquipoDe640dpElUltimoModoSeAlcanzaDesplazando() {
        // 640 dp menos la barra de estado y la de navegación deja unos 560 dp para la hoja.
        mostrar(preferido = Mode.NORMAL, alto = 560.dp)

        composeRule.onNodeWithText(texto(R.string.revision_barcode_mode)).performScrollTo().assertIsDisplayed()
        capturar("3-640dp-desplazada")
    }

    private fun mostrar(preferido: Mode, alto: Dp? = null) {
        elegidos.clear()
        composeRule.setContent {
            PMMovilTheme {
                Box(if (alto != null) Modifier.height(alto) else Modifier) {
                    RevisionModeSelectorContent(
                        crefAgrupada = "01C263606600000",
                        contextLabel = "FREUDENBERG-NOK",
                        facturas = "0/3",
                        partidas = "0/123",
                        preferredMode = preferido,
                        isOpening = false,
                        onModeSelected = { elegidos += it }
                    )
                }
            }
        }
    }

    private fun capturar(nombre: String) {
        composeRule.waitForIdle()
        Thread.sleep(400)
        val captura = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val carpeta = File(contexto.getExternalFilesDir(null), "modos").apply { mkdirs() }
        File(carpeta, "$nombre.png").outputStream().use { captura.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun texto(id: Int, vararg args: Any): String = contexto.getString(id, *args)
}
