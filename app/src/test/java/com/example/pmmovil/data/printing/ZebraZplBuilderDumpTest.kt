package com.example.pmmovil.data.printing

import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaFormSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZebraZplBuilderDumpTest {

    @Test
    fun `dump zpl replicating the photographed label`() {
        val form = photographedLabelForm()
        val labels = ZebraZplBuilder.fromEntradaForm(form)
        val zpl = ZebraZplBuilder.buildLabels(labels)

        val outDir = File("build/zpl-preview")
        outDir.mkdirs()
        // ZPL completo con el logo real en bitmap hex.
        File(outDir, "etiqueta-replica-foto.zpl").writeText(zpl)

        println("=== ZPL replica foto etiqueta.jpeg ===")
        println("Etiquetas: ${labels.size} (1 carpeta + ${labels.size - 1} parciales)")
        println("Tamano: ${zpl.length} bytes")
        println("Display ref: ${labels.first().numeroEntrada}")
        println("Cliente: '${labels.first().cliente}'")
        println("QR carpeta: '${labels.first().qrValue}'")
        println("QR ultimo parcial: '${labels.last().qrValue}'")
        println("Parcial: '${labels.last().parcial}'")
        println("F. Arribo: '${labels.first().fechaArribo}'")
        println("Caja: '${labels.first().caja}'")
        println("Guia: '${labels.first().guia}'")
        println("Ubica: '${labels.first().ubicaciones}'")
    }

    @Test
    fun `zpl layout uses the configurable 300 dpi reference format`() {
        val labels = ZebraZplBuilder.fromEntradaForm(photographedLabelForm())
        val zpl = ZebraZplBuilder.buildLabels(listOf(labels.last()))

        assertEquals(7, labels.size)
        assertTrue(zpl.contains("^PW1200"))
        assertTrue(zpl.contains("^LL1800"))
        assertTrue(zpl.contains("^FO48,1275"))
        assertTrue(zpl.contains("^FO48,100^BQN,2,24"))
        assertTrue(zpl.contains("^FO365,1075^A0B,250,150"))
        assertTrue(zpl.contains("^FO735,100^A0B,40,34^FB1300,1,0,L,0^FDALLNEX MEXICO, S. DE R.L. DE C.V.CL7267^FS"))
        assertTrue(zpl.contains("^FO870,100^A0B,44,38^FB500,1,0,L,0^FD472430009^FS"))
        assertTrue(zpl.contains("^FO995,350^A0B,44,38^FB250,1,0,L,0^FDO3^FS"))
        assertTrue(zpl.contains("^FO950,50^A0B,220,210^FB560,1,0,R,0^FD6/6^FS"))
        assertFalse(zpl.contains("^BXN,35,200"))
        assertFalse(zpl.contains("^PW830"))
    }

    private fun photographedLabelForm() = EntradaFormSnapshot(
        ref1 = "50",
        ref2 = "C",
        ref3 = "20",
        ref4 = "355910",
        ref5 = "00",
        ref6 = "26",
        fecha = "2026-02-06T10:15",
        codCliente = "CL7267",
        nomCliente = "ALLNEX MEXICO, S. DE R.L. DE C.V.",
        codTransporte = "",
        nomTransporte = "",
        manejo = "",
        guia = "472430009",
        cajaArribo = "",
        detalles = listOf(
            EntradaDetail(cantidad = 6.0, descripcion = "Bultos", ubicacion = "O3")
        ),
        pesoLbs = 0.0,
        pesoKgs = 0.0,
        observaciones = "",
        recibio = ""
    )
}
