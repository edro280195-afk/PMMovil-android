package com.example.pmmovil

import android.graphics.pdf.PdfDocument
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pmmovil.ui.pdf.InlinePdfAnnotationEditor
import com.example.pmmovil.ui.theme.PMMovilTheme
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InlinePdfAnnotationEditorInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun drawsAndSavesAnnotatedCopyWithoutOpeningAnotherActivity() {
        val savedFile = AtomicReference<File?>()

        composeRule.activityRule.scenario.onActivity { activity ->
            val sourceFile = File(activity.cacheDir, "inline-editor-test.pdf")
            createTestPdf(sourceFile)
            activity.setContent {
                PMMovilTheme {
                    InlinePdfAnnotationEditor(
                        file = sourceFile,
                        selectedDocument = null,
                        onDirtyChanged = {},
                        onCancel = {},
                        onSaved = savedFile::set,
                        onError = { error(it) }
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("pdf-page-canvas-0")
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("pdf-page-canvas-0")
            .performTouchInput {
                swipe(
                    start = Offset(width * 0.25f, height * 0.35f),
                    end = Offset(width * 0.72f, height * 0.62f),
                    durationMillis = 650
                )
            }
        composeRule.onNodeWithText("Guardar")
            .assertIsEnabled()
            .performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            savedFile.get()?.let { it.exists() && it.length() > 0L } == true
        }
        assertTrue(savedFile.get()?.extension == "pdf")
    }

    @Test
    fun keepsPdfCanvasVisibleInsideTabletSidePanelWidth() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val sourceFile = File(activity.cacheDir, "inline-editor-panel-test.pdf")
            createTestPdf(sourceFile)
            activity.setContent {
                PMMovilTheme {
                    Box(
                        modifier = Modifier
                            .width(522.dp)
                            .height(530.dp)
                    ) {
                        InlinePdfAnnotationEditor(
                            file = sourceFile,
                            selectedDocument = null,
                            onDirtyChanged = {},
                            onCancel = {},
                            onSaved = {},
                            onError = { error(it) }
                        )
                    }
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 20_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.hasTestTag("pdf-page-canvas-0")
            ).fetchSemanticsNodes().isNotEmpty()
        }

        val canvasBounds = composeRule.onNodeWithTag("pdf-page-canvas-0")
            .fetchSemanticsNode()
            .boundsInRoot
        val density = composeRule.activity.resources.displayMetrics.density
        assertTrue(canvasBounds.width > 400 * density)
        assertTrue(canvasBounds.height > 300 * density)
    }

    private fun createTestPdf(file: File) {
        val document = PdfDocument()
        try {
            repeat(2) { index ->
                val page = document.startPage(
                    PdfDocument.PageInfo.Builder(612, 792, index + 1).create()
                )
                page.canvas.drawColor(android.graphics.Color.WHITE)
                page.canvas.drawText(
                    "PMMovil - Página ${index + 1}",
                    60f,
                    90f,
                    android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG
                    ).apply {
                        color = android.graphics.Color.DKGRAY
                        textSize = 28f
                    }
                )
                document.finishPage(page)
            }
            FileOutputStream(file).use(document::writeTo)
        } finally {
            document.close()
        }
    }
}
