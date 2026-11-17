package com.example.pmmovil.data.printing

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraLabelLayoutPreferencesTest {

    @Test
    fun `layout anterior completa campos nuevos con valores seguros`() {
        val legacyJson = """
            {
              "logo": { "x": 72, "y": 48, "width": 460, "height": 188 },
              "reference": { "x": 65, "y": 365, "width": 900, "height": 270, "fontHeight": 250, "fontWidth": 150 }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson)

        assertEquals(ZebraLabelLayout.CURRENT_SCHEMA_VERSION, layout.schemaVersion)
        assertEquals(72, layout.logo.x)
        assertEquals(LabelFontFamily.SANS, layout.logo.fontFamily)
        assertEquals(LabelAlignment.LEFT, layout.logo.alignment)
        assertTrue(layout.logo.visible)
        assertTrue(layout.reference.visible)
        assertEquals(LabelFontFamily.SANS, layout.reference.fontFamily)
    }

    @Test
    fun `layout de bodega guardado antes del campo Recibio lo estrena visible en su lugar por omision`() {
        val legacyJson = """
            {
              "schemaVersion": 4,
              "logo": { "x": 72, "y": 48, "width": 460, "height": 188 }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson, ZebraLabelType.ENTRADA_BODEGA)

        assertTrue(layout.receivedLabel.visible)
        assertTrue(layout.receivedValue.visible)
        assertEquals(65, layout.receivedLabel.x)
        assertEquals(995, layout.receivedLabel.y)
        assertEquals(350, layout.receivedValue.x)
        assertEquals(LabelFontFamily.SANS, layout.receivedValue.fontFamily)
        // El valor termina antes de la columna de UBICA: (x = 940) para no encimarse.
        assertTrue(layout.receivedValue.x + layout.receivedValue.width <= layout.locationLabel.x)
    }

    @Test
    fun `la etiqueta de caseta no muestra Recibio ni en la plantilla ni al migrar`() {
        val porOmision = ZebraLabelType.CASETA.defaultLayout()
        assertFalse(porOmision.receivedLabel.visible)
        assertFalse(porOmision.receivedValue.visible)

        val migrada = deserializeZebraLabelLayout("""{ "schemaVersion": 4 }""", ZebraLabelType.CASETA)
        assertFalse(migrada.receivedLabel.visible)
        assertFalse(migrada.receivedValue.visible)
    }

    @Test
    fun `al escalar a 203 dpi Recibio se escala con el resto`() {
        val escalada = ZebraLabelLayout.reference().scaledForPrintheadDpi(203)

        assertEquals(237, escalada.receivedValue.x)
        assertEquals(673, escalada.receivedLabel.y)
    }

    @Test
    fun `layout guardado antes de Cantidad y Peso los estrena visibles en la fila de abajo sin pisar a Recibio`() {
        val legacyJson = """{ "schemaVersion": 4, "logo": { "x": 72, "y": 48, "width": 460, "height": 188 } }"""

        val layout = deserializeZebraLabelLayout(legacyJson, ZebraLabelType.ENTRADA_BODEGA)

        assertTrue(layout.quantityLabel.visible && layout.quantityValue.visible)
        assertTrue(layout.weightLabel.visible && layout.weightValue.visible)
        // Misma fila, de izquierda a derecha, dentro del hueco libre (antes de la columna de UBICA: en x = 940).
        assertEquals(layout.quantityLabel.y, layout.weightValue.y)
        assertTrue(layout.quantityLabel.x + layout.quantityLabel.width <= layout.quantityValue.x)
        assertTrue(layout.quantityValue.x + layout.quantityValue.width <= layout.weightLabel.x)
        assertTrue(layout.weightLabel.x + layout.weightLabel.width <= layout.weightValue.x)
        assertTrue(layout.weightValue.x + layout.weightValue.width <= layout.locationLabel.x)
        // Debajo de Recibio, sin encimarse con él.
        assertTrue(layout.receivedValue.y + layout.receivedValue.height <= layout.quantityValue.y)
    }

    @Test
    fun `la etiqueta de caseta no muestra Cantidad ni Peso`() {
        val caseta = ZebraLabelType.CASETA.defaultLayout()
        assertFalse(caseta.quantityLabel.visible || caseta.quantityValue.visible)
        assertFalse(caseta.weightLabel.visible || caseta.weightValue.visible)

        val migrada = deserializeZebraLabelLayout("""{ "schemaVersion": 4 }""", ZebraLabelType.CASETA)
        assertFalse(migrada.quantityValue.visible || migrada.weightValue.visible)
    }

    @Test
    fun `al escalar a 203 dpi Cantidad y Peso se escalan con el resto`() {
        val escalada = ZebraLabelLayout.reference().scaledForPrintheadDpi(203)

        assertEquals(186, escalada.quantityValue.x)
        assertEquals(744, escalada.weightLabel.y)
    }

    @Test
    fun `layout vigente conserva una visibilidad falsa y los estilos elegidos`() {
        val currentJson = """
            {
              "schemaVersion": 2,
              "customerValue": {
                "x": 400,
                "y": 760,
                "width": 900,
                "height": 58,
                "fontHeight": 42,
                "fontWidth": 36,
                "fontFamily": "SERIF",
                "alignment": "CENTER",
                "visible": false
              }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(currentJson)

        assertEquals(LabelFontFamily.SERIF, layout.customerValue.fontFamily)
        assertEquals(LabelAlignment.CENTER, layout.customerValue.alignment)
        assertFalse(layout.customerValue.visible)
    }

    @Test
    fun `constrained mantiene la caja dentro del plano de lectura`() {
        val element = ZebraLabelElement(
            x = 1_790,
            y = 1_190,
            width = 200,
            height = 80
        ).constrained()

        assertEquals(1_600, element.x)
        assertEquals(1_120, element.y)
        assertEquals(1_800, element.x + element.width)
        assertEquals(1_200, element.y + element.height)
    }

    @Test
    fun `plantilla de caseta usa las dimensiones fisicas de 2 punto 25 por 1 punto 25`() {
        val layout = ZebraLabelType.CASETA.defaultLayout()

        assertEquals(675, layout.widthDots)
        assertEquals(375, layout.heightDots)
        assertFalse(layout.rotate)
        assertEquals(655, layout.reference.x + layout.reference.width)
    }

    @Test
    fun `layout conserva textos libres y fuentes nuevas`() {
        val json = """
            {
              "customTexts": [
                {
                  "id": "notice",
                  "text": "FRAGIL",
                  "element": {
                    "x": 20,
                    "y": 40,
                    "width": 600,
                    "height": 70,
                    "fontHeight": 48,
                    "fontWidth": 44,
                    "fontFamily": "SANS_CONDENSED",
                    "alignment": "CENTER"
                  }
                }
              ]
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(json)

        assertEquals(1, layout.customTexts.size)
        assertEquals("FRAGIL", layout.customTexts.single().text)
        assertEquals(LabelFontFamily.SANS_CONDENSED, layout.customTexts.single().element.fontFamily)
    }

    @Test
    fun `layout anterior sin imagenes ni logo en QR usa valores seguros`() {
        val legacyJson = """
            {
              "reference": { "x": 65, "y": 365, "width": 900, "height": 270 }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson)

        assertTrue(layout.customImages.isEmpty())
        assertFalse(layout.qrStyle.logoEnabled)
    }

    @Test
    fun `layout de caseta anterior activa el logo en QR por default al migrar`() {
        val legacyCasetaJson = """
            {
              "widthDots": 675,
              "heightDots": 375,
              "rotate": false
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyCasetaJson, ZebraLabelType.CASETA)

        assertTrue(layout.qrStyle.logoEnabled)
    }

    @Test
    fun `plantilla de caseta trae el logo de IMEX pre-agregado y el logo en el QR activo`() {
        val layout = ZebraLabelType.CASETA.defaultLayout()

        assertTrue(layout.qrStyle.logoEnabled)
        assertEquals(1, layout.customImages.size)
        assertEquals(LabelImageAsset.IMEX_LOGO.id, layout.customImages.single().assetId)
    }

    @Test
    fun `layout guardado con el booleano viejo conserva el logo y abre en estilo cuadrado`() {
        // Antes de v4 el logo del QR era un Boolean suelto y todo se imprimía en cuadrados.
        // Una plantilla ya personalizada no debe estrenar un estilo que su dueño no eligió.
        val legacyJson = """
            {
              "schemaVersion": 3,
              "qrLogoEnabled": true,
              "reference": { "x": 65, "y": 365, "width": 900, "height": 270 }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson)

        assertTrue(layout.qrStyle.logoEnabled)
        assertEquals(QrModuleShape.SQUARE, layout.qrStyle.moduleShape)
        assertEquals(QrEyeShape.SQUARE, layout.qrStyle.eyeFrameShape)
        assertEquals(QrEyeShape.SQUARE, layout.qrStyle.eyeBallShape)
    }

    @Test
    fun `estilo de QR con valores invalidos cae a los defaults sin reventar`() {
        val brokenJson = """
            {
              "qrStyle": {
                "moduleShape": "TRIANGULO",
                "eyeFrameShape": null,
                "logoScale": 9.5,
                "quietZoneModules": 99
              }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(brokenJson)

        assertEquals(QrModuleShape.SQUARE, layout.qrStyle.moduleShape)
        assertEquals(QrEyeShape.SQUARE, layout.qrStyle.eyeFrameShape)
        assertEquals(QrStyleOptions.LOGO_SCALE_MAX, layout.qrStyle.logoScale)
        assertEquals(QrStyleOptions.QUIET_ZONE_MAX, layout.qrStyle.quietZoneModules)
    }

    @Test
    fun `estilo de QR personalizado sobrevive el viaje completo a JSON y de regreso`() {
        val original = ZebraLabelLayout.reference().copy(
            qrStyle = QrStyleOptions(
                moduleShape = QrModuleShape.FLUID,
                eyeFrameShape = QrEyeShape.CIRCLE,
                eyeBallShape = QrEyeShape.LEAF,
                logoEnabled = true,
                logoScale = 0.28f,
                logoPlate = QrLogoPlate.CIRCLE,
                clearModulesUnderLogo = false,
                quietZoneModules = 3
            )
        )

        val restored = deserializeZebraLabelLayout(Gson().toJson(original))

        assertEquals(original.qrStyle, restored.qrStyle)
    }

    @Test
    fun `layout anterior sin negrita definida conserva el aspecto negrita que ya imprimia`() {
        val legacyJson = """
            {
              "reference": { "x": 65, "y": 365, "width": 900, "height": 270, "fontFamily": "SANS" }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson)

        assertTrue(layout.reference.bold)
        assertFalse(layout.reference.italic)
        assertFalse(layout.reference.underline)
        assertEquals(0f, layout.reference.letterSpacing)
    }

    @Test
    fun `layout anterior con Sans ligera conserva el aspecto sin negrita que ya imprimia`() {
        val legacyJson = """
            {
              "reference": { "x": 65, "y": 365, "width": 900, "height": 270, "fontFamily": "SANS_LIGHT" }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(legacyJson)

        assertFalse(layout.reference.bold)
    }

    @Test
    fun `layout conserva negrita cursiva subrayado y espaciado elegidos`() {
        val json = """
            {
              "reference": {
                "x": 65, "y": 365, "width": 900, "height": 270,
                "bold": false, "italic": true, "underline": true, "letterSpacing": 0.15
              }
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(json)

        assertFalse(layout.reference.bold)
        assertTrue(layout.reference.italic)
        assertTrue(layout.reference.underline)
        assertEquals(0.15f, layout.reference.letterSpacing)
    }

    @Test
    fun `layout conserva imagenes libres y corrige assetId desconocido`() {
        val json = """
            {
              "customImages": [
                {
                  "id": "logo-1",
                  "assetId": "imex_logo",
                  "element": { "x": 30, "y": 40, "width": 200, "height": 82 }
                },
                {
                  "id": "logo-1",
                  "assetId": "no_existe",
                  "element": { "x": 10, "y": 10, "width": 100, "height": 41 }
                }
              ]
            }
        """.trimIndent()

        val layout = deserializeZebraLabelLayout(json)

        assertEquals(2, layout.customImages.size)
        val ids = layout.customImages.map { it.id }
        assertEquals(2, ids.toSet().size)
        assertTrue(layout.customImages.all { LabelImageAsset.fromId(it.assetId) != null })
    }
}
