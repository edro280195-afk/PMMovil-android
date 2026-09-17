package com.example.pmmovil.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El borrador local es lo único que separa al revisor de perder sus marcas si
 * Android mata la app en bodega, así que lo que se guarda tiene que volver
 * igual, y un borrador corrupto nunca debe tumbar la pantalla.
 */
class PdfAnnotationCodecTest {

    private fun ink(vararg coords: Float) = PdfAnnotation(
        pageIndex = 2,
        tool = PdfAnnotationTool.INK,
        points = (coords.indices step 2).map {
            PdfNormalizedPoint(coords[it], coords[it + 1])
        },
        color = android.graphics.Color.RED,
        widthFraction = 0.006f
    )

    private fun assertSamePoints(expected: PdfAnnotation, actual: PdfAnnotation) {
        assertEquals(expected.points.size, actual.points.size)
        expected.points.forEachIndexed { index, point ->
            assertEquals(point.x, actual.points[index].x, 0.0001f)
            assertEquals(point.y, actual.points[index].y, 0.0001f)
        }
    }

    @Test
    fun `un trazo vuelve con la misma geometria`() {
        val original = ink(0.1f, 0.2f, 0.35f, 0.42f, 0.9f, 0.88f)

        val restored = decodePdfAnnotations(encodePdfAnnotations(listOf(original)))

        assertEquals(1, restored.size)
        assertEquals(original.pageIndex, restored[0].pageIndex)
        assertEquals(original.tool, restored[0].tool)
        assertEquals(original.color, restored[0].color)
        assertEquals(original.widthFraction, restored[0].widthFraction, 0.00001f)
        assertSamePoints(original, restored[0])
    }

    @Test
    fun `sobreviven las tres herramientas juntas`() {
        val original = listOf(
            ink(0.1f, 0.2f, 0.3f, 0.4f),
            PdfAnnotation(
                pageIndex = 0,
                tool = PdfAnnotationTool.HIGHLIGHT,
                points = listOf(PdfNormalizedPoint(0.2f, 0.3f), PdfNormalizedPoint(0.7f, 0.36f)),
                color = android.graphics.Color.YELLOW,
                widthFraction = 0.08f
            ),
            PdfAnnotation(
                pageIndex = 1,
                tool = PdfAnnotationTool.TEXT,
                points = listOf(PdfNormalizedPoint(0.5f, 0.5f)),
                color = android.graphics.Color.BLACK,
                text = "Faltó una tarima — revisar mañana"
            )
        )

        val restored = decodePdfAnnotations(encodePdfAnnotations(original))

        assertEquals(3, restored.size)
        assertEquals(
            listOf(PdfAnnotationTool.INK, PdfAnnotationTool.HIGHLIGHT, PdfAnnotationTool.TEXT),
            restored.map(PdfAnnotation::tool)
        )
        // El texto viaja con acentos y guion largo: la nota se escribe en español.
        assertEquals("Faltó una tarima — revisar mañana", restored[2].text)
        // Cada anotación conserva su propia página: 2, 0 y 1 respectivamente.
        assertEquals(listOf(2, 0, 1), restored.map(PdfAnnotation::pageIndex))
        assertEquals(0.08f, restored[1].widthFraction, 0.00001f)
    }

    @Test
    fun `un trazo largo se guarda compacto`() {
        val long = ink(*FloatArray(1000) { index -> (index % 100) / 100f })

        val json = encodePdfAnnotations(listOf(long))

        assertEquals(500, decodePdfAnnotations(json).first().points.size)
        // La forma larga con objetos {"x":..,"y":..} pasaría de 12 000 bytes.
        assertTrue("JSON de ${json.length} bytes", json.length < 6_000)
    }

    @Test
    fun `una lista vacia va y vuelve vacia`() {
        assertEquals(emptyList<PdfAnnotation>(), decodePdfAnnotations(encodePdfAnnotations(emptyList())))
    }

    @Test
    fun `un borrador ilegible se descarta sin reventar`() {
        listOf(null, "", "   ", "{", "no soy json", "[1,2,3]", "{\"a\":1}").forEach { bad ->
            assertEquals("entrada: $bad", emptyList<PdfAnnotation>(), decodePdfAnnotations(bad))
        }
    }

    @Test
    fun `se tiran las anotaciones a medias y se conservan las buenas`() {
        val buena = """{"page":0,"tool":"INK","points":[0.1,0.2,0.3,0.4],"color":-65536,"width":0.006}"""
        val sinPuntos = """{"page":0,"tool":"INK","color":-65536,"width":0.006}"""
        val imparesg = """{"page":0,"tool":"INK","points":[0.1,0.2,0.3],"color":-65536,"width":0.006}"""
        val herramientaRara = """{"page":0,"tool":"LASER","points":[0.1,0.2,0.3,0.4],"color":-65536,"width":0.006}"""

        val restored = decodePdfAnnotations("[$sinPuntos,$buena,$imparesg,$herramientaRara]")

        assertEquals(1, restored.size)
        assertEquals(PdfAnnotationTool.INK, restored[0].tool)
        assertEquals(2, restored[0].points.size)
    }
}
