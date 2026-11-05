package com.example.pmmovil.ui.salida

import com.example.pmmovil.data.model.SalidaEtiqueta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lo que Cargar mercancía decide sola y lo que muestra de cada bulto (rediseño del 5-nov-2026). */
class SalidaCargaTest {

    private fun bulto(texto: String, escaneado: Boolean = false, timeScan: String? = null) =
        SalidaEtiqueta(textoEtiqueta = texto, escaneado = escaneado, timeScan = timeScan)

    // ── Qué se escaneó: relación o entrada directa ─────────────────────────────

    @Test
    fun `a folder qr loads that entry without a relation`() {
        assertEquals("01C2635628400001454".take(15), entradaDirectaDe("01C2635628400001454  100"))
    }

    @Test
    fun `a bare entry reference and one of its package labels load that entry`() {
        assertEquals("01C263614050000", entradaDirectaDe("01C263614050000"))
        assertEquals("01C263614050000", entradaDirectaDe("01C263614050000 9-17"))
        assertEquals("01c263614050000", entradaDirectaDe("  01c263614050000  "))
    }

    @Test
    fun `a relation in any of its forms is not a direct entry`() {
        assertNull(entradaDirectaDe("01I26106418-1"))
        assertNull(entradaDirectaDe("R-01I26106070  0000  101"))
        assertNull(entradaDirectaDe("01I26105252"))
        // Una relación de importación (tercer carácter C) mide 11, no 15: no es una entrada.
        assertNull(entradaDirectaDe("01C26104526"))
    }

    @Test
    fun `a gate qr and unknown text are not direct entries`() {
        assertNull(entradaDirectaDe("E-17846/26"))
        assertNull(entradaDirectaDe("E-017846/26-1657-2"))
        assertNull(entradaDirectaDe("hola"))
        assertNull(entradaDirectaDe(""))
    }

    // ── Cómo se lee cada bulto ─────────────────────────────────────────────────

    @Test
    fun `a package label reads as bulto n of m with its entry underneath`() {
        val texto = textoDeEtiqueta(bulto("01C263614050000 9-17"))

        assertEquals("Bulto 9 de 17", texto.titulo)
        assertEquals("01C263614050000", texto.detalle)
    }

    @Test
    fun `a label in another format is shown as it is and never invents a position`() {
        val texto = textoDeEtiqueta(SalidaEtiqueta(textoEtiqueta = "PROV-778-ABC", textoPagina = "", creferencia = "01C263614050000"))

        assertEquals("PROV-778-ABC", texto.titulo)
        assertEquals("01C263614050000", texto.detalle)
    }

    @Test
    fun `the page text is used as the title when the label is not a package`() {
        val texto = textoDeEtiqueta(SalidaEtiqueta(textoEtiqueta = "X1", textoPagina = "Pallet 4"))

        assertEquals("Pallet 4", texto.titulo)
    }

    @Test
    fun `a scanned code that is not in the list is also described`() {
        assertEquals("Bulto 2 de 6", textoDeCodigo("01C263699990000 2-6").titulo)
        assertEquals("ZZZ", textoDeCodigo(" ZZZ ").titulo)
    }

    @Test
    fun `a badge qr is never drawn on screen`() {
        assertEquals("Código no reconocido", textoDeCodigo("eduardo.rdz;abc123").titulo)
        assertEquals("", textoDeCodigo("eduardo.rdz;abc123").detalle)
    }

    @Test
    fun `scan time keeps only the clock and hides anything it does not understand`() {
        assertEquals("09:41:07", horaDeEscaneo("2026-11-05T09:41:07"))
        assertEquals("09:41:07", horaDeEscaneo("2026-11-05 09:41:07"))
        assertEquals("09:41", horaDeEscaneo("2026-11-05T09:41"))
        assertNull(horaDeEscaneo(null))
        assertNull(horaDeEscaneo(""))
        assertNull(horaDeEscaneo("no es fecha"))
    }

    // ── Historial en orden de escaneo ──────────────────────────────────────────

    @Test
    fun `packages scanned here are numbered in the order they were read`() {
        val etiquetas = listOf(
            bulto("A 1-3", escaneado = true),
            bulto("A 2-3", escaneado = true),
            bulto("A 3-3", escaneado = false)
        )
        // Se leyó primero el 2 y luego el 1.
        val locales = listOf(SalidaEscaneoLocal("A 2-3", "09:00:01"), SalidaEscaneoLocal("A 1-3", "09:00:09"))

        val historial = escaneadosEnOrden(etiquetas, locales)

        assertEquals(listOf("A 2-3", "A 1-3"), historial.map { it.etiqueta.textoEtiqueta })
        assertEquals(listOf(1, 2), historial.map { it.numero })
        assertEquals(listOf("09:00:01", "09:00:09"), historial.map { it.hora })
    }

    @Test
    fun `packages that came already scanned from the server go first by their scan time`() {
        val etiquetas = listOf(
            bulto("B 1-4", escaneado = true, timeScan = "2026-11-05T08:10:00"),
            bulto("B 2-4", escaneado = true, timeScan = "2026-11-05T08:05:00"),
            bulto("B 3-4", escaneado = true), // la que se acaba de leer aquí
            bulto("B 4-4")
        )
        val locales = listOf(SalidaEscaneoLocal("B 3-4", "09:30:00"))

        val historial = escaneadosEnOrden(etiquetas, locales)

        assertEquals(listOf("B 2-4", "B 1-4", "B 3-4"), historial.map { it.etiqueta.textoEtiqueta })
        assertEquals(listOf(1, 2, 3), historial.map { it.numero })
        assertEquals("08:05:00", historial[0].hora)
        assertEquals("09:30:00", historial[2].hora)
    }

    @Test
    fun `pending packages come in natural order per entry and not as text`() {
        val desordenadas = listOf(
            bulto("B 2-3"), bulto("A 10-10"), bulto("A 2-10"), bulto("A 1-10"), bulto("B 1-3"), bulto("A 9-10"),
            SalidaEtiqueta(textoEtiqueta = "SUELTA", creferencia = "A")
        )

        assertEquals(
            listOf("A 1-10", "A 2-10", "A 9-10", "A 10-10", "SUELTA", "B 1-3", "B 2-3"),
            enOrdenNatural(desordenadas).map { it.textoEtiqueta }
        )
    }

    @Test
    fun `nothing scanned means an empty history and pending ones never show up`() {
        assertEquals(emptyList<SalidaEscaneado>(), escaneadosEnOrden(listOf(bulto("C 1-2"), bulto("C 2-2")), emptyList()))
    }

    @Test
    fun `the pending list and the history never share a package`() {
        val estado = SalidaUiState(
            etiquetas = listOf(bulto("D 1-3", escaneado = true), bulto("D 2-3"), bulto("D 3-3")),
            escaneosLocales = listOf(SalidaEscaneoLocal("D 1-3", "10:00:00"))
        )

        assertEquals(listOf("D 2-3", "D 3-3"), estado.porEscanear.map { it.textoEtiqueta })
        assertEquals(listOf("D 1-3"), estado.historial.map { it.etiqueta.textoEtiqueta })
        assertEquals(1, estado.escaneados)
        assertEquals(2, estado.pendientes)
    }
}
