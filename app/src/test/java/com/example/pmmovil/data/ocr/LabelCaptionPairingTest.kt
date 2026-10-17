package com.example.pmmovil.data.ocr

import com.example.pmmovil.data.ocr.BolsaFreudenbergFixture.codigos
import com.example.pmmovil.data.ocr.BolsaFreudenbergFixture.girada
import com.example.pmmovil.data.ocr.BolsaFreudenbergFixture.linea
import com.example.pmmovil.data.ocr.BolsaFreudenbergFixture.lineas
import com.example.pmmovil.data.ocr.LabelCaptionPairing.Rotulo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelCaptionPairingTest {
    private fun valores(pares: List<LabelCaptionPairing.Par>) = pares.associate { it.rotulo to it.valor }

    @Test
    fun `la bolsa real empareja cada valor con su rotulo`() {
        val pares = valores(LabelCaptionPairing.emparejar(lineas, codigos))

        assertEquals("5003814158", pares[Rotulo.LOTE])
        assertEquals("200", pares[Rotulo.CANTIDAD])
        assertEquals("75183644", pares[Rotulo.PARTE])
        assertEquals("4505747759", pares[Rotulo.PO])
        assertEquals("667", pares[Rotulo.PROVEEDOR])
        assertEquals("2Q26", pares[Rotulo.CURADO])
    }

    @Test
    fun `un rotulo cuyo valor no se leyo como texto se queda sin valor`() {
        // El renglón «TAIWAN» no salió del OCR: «origin» no debe quedarse con el valor de otro rótulo cercano.
        val pares = valores(LabelCaptionPairing.emparejar(lineas, codigos))

        assertNull(pares[Rotulo.ORIGEN])
        assertNull(pares[Rotulo.DESCRIPCION])
    }

    @Test
    fun `da igual hacia donde este girada la etiqueta`() {
        listOf(90.0, 180.0, 270.0, 33.0).forEach { grados ->
            val pares = valores(LabelCaptionPairing.emparejar(girada(lineas, grados), codigos))

            assertEquals("lote a $grados°", "5003814158", pares[Rotulo.LOTE])
            assertEquals("cantidad a $grados°", "200", pares[Rotulo.CANTIDAD])
            assertEquals("parte a $grados°", "75183644", pares[Rotulo.PARTE])
        }
    }

    @Test
    fun `tambien funciona cuando el valor queda debajo de su rotulo`() {
        // Etiqueta derecha, rótulo arriba y valor abajo: lo más común en una etiqueta cualquiera.
        val derecha = listOf(
            linea("Batch", 10f, 10f, 70f, 10f, 70f, 24f, 10f, 24f),
            linea("5003814158", 10f, 34f, 150f, 34f, 150f, 56f, 10f, 56f),
            linea("QTY", 200f, 10f, 240f, 10f, 240f, 24f, 200f, 24f),
            linea("200", 200f, 34f, 245f, 34f, 245f, 56f, 200f, 56f)
        )

        val pares = valores(LabelCaptionPairing.emparejar(derecha, listOf("5003814158", "200")))

        assertEquals("5003814158", pares[Rotulo.LOTE])
        assertEquals("200", pares[Rotulo.CANTIDAD])
    }

    @Test
    fun `un valor demasiado lejos del rotulo no se empareja`() {
        val lejos = listOf(
            linea("Batch", 10f, 10f, 70f, 10f, 70f, 24f, 10f, 24f),
            linea("5003814158", 10f, 400f, 150f, 400f, 150f, 422f, 10f, 422f)
        )

        assertTrue(LabelCaptionPairing.emparejar(lejos, listOf("5003814158")).isEmpty())
    }

    @Test
    fun `un valor desalineado con el rotulo no se empareja`() {
        // Cerca en vertical pero en otra columna de la etiqueta.
        val otraColumna = listOf(
            linea("Batch", 10f, 10f, 70f, 10f, 70f, 24f, 10f, 24f),
            linea("5003814158", 500f, 34f, 640f, 34f, 640f, 56f, 500f, 56f)
        )

        assertTrue(LabelCaptionPairing.emparejar(otraColumna, listOf("5003814158")).isEmpty())
    }

    @Test
    fun `solo cuenta como valor un renglon que es exactamente un codigo leido`() {
        val mal = lineas.map { if (it.texto == "5003814158") it.copy(texto = "5OO3814158") else it }

        val pares = valores(LabelCaptionPairing.emparejar(mal, codigos))

        assertNull("el lote mal leído no se empareja", pares[Rotulo.LOTE])
        assertEquals("200", pares[Rotulo.CANTIDAD])
    }

    @Test
    fun `espacios y guiones no estorban al reconocer el valor`() {
        val conEspacio = lineas.map { if (it.texto.startsWith("1Y-")) it.copy(texto = "1Y-0000005 0337428") else it }

        // No hay rótulo para el ID, pero no debe romper el resto.
        assertEquals("5003814158", valores(LabelCaptionPairing.emparejar(conEspacio, codigos))[Rotulo.LOTE])
    }

    @Test
    fun `un valor lo toma un solo rotulo y es el mas cercano`() {
        // «Lot» queda un poco más cerca del valor que «Batch»: se lo queda y el otro rótulo no recibe nada.
        val dosRotulos = listOf(
            linea("Batch", 10f, 10f, 70f, 10f, 70f, 24f, 10f, 24f),
            linea("Lot", 100f, 14f, 130f, 14f, 130f, 28f, 100f, 28f),
            linea("5003814158", 10f, 34f, 150f, 34f, 150f, 56f, 10f, 56f)
        )

        val pares = LabelCaptionPairing.emparejar(dosRotulos, listOf("5003814158"))

        assertEquals(1, pares.size)
        assertEquals("Lot", pares.single().textoDelRotulo)
    }

    @Test
    fun `renglones que corren en otra direccion no se emparejan`() {
        // El rótulo está de lado y el valor derecho: son bloques distintos de la etiqueta.
        val cruzados = listOf(
            linea("Batch", 328f, 535f, 328f, 573f, 312f, 573f, 312f, 535f),
            linea("5003814158", 340f, 540f, 480f, 540f, 480f, 562f, 340f, 562f)
        )

        assertTrue(LabelCaptionPairing.emparejar(cruzados, listOf("5003814158")).isEmpty())
    }

    @Test
    fun `sin renglones o sin codigos no hay nada que emparejar`() {
        assertTrue(LabelCaptionPairing.emparejar(emptyList(), codigos).isEmpty())
        assertTrue(LabelCaptionPairing.emparejar(lineas, emptyList()).isEmpty())
    }

    @Test
    fun `los rotulos se reconocen con sus variantes habituales`() {
        fun rotuloDe(texto: String): Rotulo? {
            val l = listOf(
                linea(texto, 10f, 10f, 80f, 10f, 80f, 24f, 10f, 24f),
                linea("VALOR1", 10f, 34f, 100f, 34f, 100f, 56f, 10f, 56f)
            )
            return LabelCaptionPairing.emparejar(l, listOf("VALOR1")).singleOrNull()?.rotulo
        }

        assertEquals(Rotulo.LOTE, rotuloDe("Batch"))
        assertEquals(Rotulo.LOTE, rotuloDe("LOT NO."))
        assertEquals(Rotulo.PARTE, rotuloDe("Part Number"))
        assertEquals(Rotulo.CANTIDAD, rotuloDe("QTY"))
        assertEquals(Rotulo.CANTIDAD, rotuloDe("Quantity"))
        assertEquals(Rotulo.PO, rotuloDe("P.O.NUMBER"))
        assertEquals(Rotulo.ORIGEN, rotuloDe("origin"))
        assertNull(rotuloDe("Lote de producción"))
    }
}
