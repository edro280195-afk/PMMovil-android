package com.example.pmmovil

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.R as AppCompatR
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.R as MaterialR
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfThemeInstrumentedTest {

    @Test
    fun pdfViewerLayoutInflatesWithAppTheme() {
        val baseContext = InstrumentationRegistry.getInstrumentation().targetContext
        val context = ContextThemeWrapper(baseContext, R.style.Theme_PMMovil)
        val requiredAttributes = intArrayOf(
            AppCompatR.attr.colorPrimary,
            MaterialR.attr.colorOnSurface,
            MaterialR.attr.textAppearanceBodyMedium
        )
        val values = context.obtainStyledAttributes(requiredAttributes)

        try {
            requiredAttributes.indices.forEach { index ->
                assertTrue(
                    "El tema debe resolver el atributo PDF en el índice $index",
                    values.hasValue(index)
                )
            }
        } finally {
            values.recycle()
        }

        val parent = FrameLayout(context)
        val layoutId = context.resources.getIdentifier(
            "pdf_viewer_fragment",
            "layout",
            context.packageName
        )
        assertTrue("El layout del visor PDF debe estar empaquetado", layoutId != 0)
        val view = LayoutInflater.from(context).inflate(
            layoutId,
            parent,
            false
        )

        assertNotNull(view)
    }
}
