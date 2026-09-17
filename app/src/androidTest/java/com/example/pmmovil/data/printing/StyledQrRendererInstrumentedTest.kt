package com.example.pmmovil.data.printing

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba en un dispositivo real que los estilos de QR del diseñador aguantan la verificación
 * de lectura con los tamaños y valores que se imprimen de verdad.
 *
 * Existe porque la promesa "se garantiza la lectura" no se puede sostener con teoría: [StyledQrRenderer]
 * degrada el estilo cuando el QR no se decodifica, y hacía falta saber CUÁNTO degrada en la práctica.
 * Si un estilo se degradara siempre, el selector del diseñador estaría ofreciendo algo que nunca sale
 * impreso. El test corre en dispositivo porque el motor dibuja con android.graphics (Canvas/Path),
 * que no existe en los unit tests de JVM.
 */
@RunWith(AndroidJUnit4::class)
class StyledQrRendererInstrumentedTest {

    private val renderer = StyledQrRenderer()

    private val logo by lazy {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        BitmapFactory.decodeResource(context.resources, LabelImageAsset.IMEX_LOGO.drawableRes)
    }

    /** Tamaños reales del elemento QR en las dos plantillas de fábrica. */
    private val casetaSide = ZebraLabelLayout.casetaCompact().qr.width
    private val bodegaSide = ZebraLabelLayout.reference().qr.width

    /** Valores representativos: folio corto, folio con parcial y el caso más largo que se imprime. */
    private val sampleValues = listOf(
        "356398-00",
        "356398-00 12-40",
        "987654-99 100-100"
    )

    @Test
    fun todos_los_presets_se_leen_en_la_etiqueta_de_caseta() {
        assertQrSurvives(casetaSide, withLogo = true, label = "caseta")
    }

    @Test
    fun todos_los_presets_se_leen_en_la_etiqueta_de_bodega() {
        assertQrSurvives(bodegaSide, withLogo = false, label = "bodega")
    }

    @Test
    fun el_estilo_premium_con_logo_conserva_su_aspecto_en_caseta() {
        // El preset con el que se estrena caseta: si este se degradara, el diseñador estaría
        // enseñando una vista previa que no coincide con lo impreso.
        val style = QrStylePreset.PREMIUM.apply(QrStyleOptions()).copy(logoEnabled = true)

        sampleValues.forEach { value ->
            val result = renderer.render(value, casetaSide, style, logo, LabelImageAsset.IMEX_LOGO.aspectRatio)
            assertNotNull("No se generó QR para \"$value\"", result)
            val report = result!!.report
            Log.i(TAG, "premium/caseta \"$value\": módulos=${report.moduleCount} " +
                "dotsPorModulo=${report.moduleSizeDots} degradado=${report.downgraded}")
            assertTrue("El estilo premium se degradó con \"$value\"", !report.downgraded)
            assertTrue(report.verified)
            result.bitmap.recycle()
        }
    }

    @Test
    fun el_peor_caso_nunca_devuelve_un_qr_ilegible() {
        // Un QR diminuto con un valor largo y el estilo más agresivo: aunque no quepa el estilo,
        // el contrato es que lo que salga sea legible o que no salga nada — nunca basura.
        val style = QrStyleOptions(
            moduleShape = QrModuleShape.DOT,
            eyeFrameShape = QrEyeShape.CIRCLE,
            eyeBallShape = QrEyeShape.LEAF,
            logoEnabled = true,
            logoScale = QrStyleOptions.LOGO_SCALE_MAX
        )

        val result = renderer.render(
            "987654-99 100-100 EXTRA LARGO PARA FORZAR VERSION ALTA DEL QR",
            120,
            style,
            logo,
            LabelImageAsset.IMEX_LOGO.aspectRatio
        )

        if (result != null) {
            assertTrue("Se devolvió un QR que no pasó la verificación", result.report.verified)
            result.bitmap.recycle()
        }
    }

    private fun assertQrSurvives(side: Int, withLogo: Boolean, label: String) {
        QrStylePreset.values().forEach { preset ->
            val style = preset.apply(QrStyleOptions()).copy(logoEnabled = withLogo)
            sampleValues.forEach { value ->
                val result = renderer.render(value, side, style, logo, LabelImageAsset.IMEX_LOGO.aspectRatio)
                assertNotNull("No se generó QR para $preset / \"$value\" en $label", result)
                val report = result!!.report
                Log.i(TAG, "$label/${preset.name} \"$value\": módulos=${report.moduleCount} " +
                    "dotsPorModulo=${report.moduleSizeDots} degradado=${report.downgraded} " +
                    "aplicado=${report.applied.moduleShape}/${report.applied.eyeFrameShape}")
                assertTrue(
                    "El QR de $preset con \"$value\" en $label no pasó la verificación",
                    report.verified
                )
                result.bitmap.recycle()
            }
        }
    }

    private companion object {
        const val TAG = "StyledQrRendererTest"
    }
}
