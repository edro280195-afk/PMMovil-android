package com.example.pmmovil.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScannedCodeClassifierTest {

    @Test
    fun `a gate box id is a box, never an entry`() {
        assertEquals(ScannedCode.Caja("E-017846/26"), ScannedCodeClassifier.classify("E-017846/26"))
        assertEquals(ScannedCode.Caja("E-00119/20"), ScannedCodeClassifier.classify("E-00119/20"))
        assertEquals(ScannedCode.Caja("E-17843/26"), ScannedCodeClassifier.classify("E-17843/26"))
    }

    @Test
    fun `a release number is a release, never an entry`() {
        assertEquals(ScannedCode.Relacion("01I26104546"), ScannedCodeClassifier.classify("01I26104546"))
        assertEquals(ScannedCode.Relacion("01E17028336"), ScannedCodeClassifier.classify("01E17028336"))
        assertEquals(ScannedCode.Relacion("01C26104546"), ScannedCodeClassifier.classify("01C26104546"))
    }

    @Test
    fun `an entry reference alone is the folder label`() {
        assertEquals(ScannedCode.Carpeta("01C263582800000"), ScannedCodeClassifier.classify("01C263582800000"))
        assertEquals(ScannedCode.Carpeta("01I1431460700R1"), ScannedCodeClassifier.classify("01I1431460700R1"))
    }

    @Test
    fun `an entry reference with a position is a package label`() {
        val code = ScannedCodeClassifier.classify("01C263582800000 3-12") as ScannedCode.Bulto

        assertEquals("01C263582800000", code.reference)
        assertEquals(3, code.ordinal)
        assertEquals(12, code.total)
        assertEquals("01C263582800000 3-12", code.normalized)
    }

    @Test
    fun `lowercase padded and control characters read as the printed code`() {
        assertEquals(
            ScannedCode.Bulto("01C263582800000", 7, 9),
            ScannedCodeClassifier.classify("  01c263582800000  7-9 ")
        )
        assertEquals(ScannedCode.Caja("E-017846/26"), ScannedCodeClassifier.classify("e-017846/26\r\n"))
    }

    @Test
    fun `a mutilated position is rejected instead of accepting an unplaceable package`() {
        assertTrue(ScannedCodeClassifier.classify("01C263582800000 18-17") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("01C263582800000 0-17") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("01C263582800000 3-0") is ScannedCode.Desconocido)
    }

    @Test
    fun `the three official PMM codes classify by what they identify`() {
        // Las lecturas son las de una carpeta, una orden de carga y una requisición de cruce reales.
        assertEquals(ScannedCode.Carpeta("01C263562840000"), ScannedCodeClassifier.classify("01C2635628400001454  100"))
        // La segunda carpeta real: el cliente llena su campo y no queda espacio. Antes salía como «referencia dudosa».
        assertEquals(ScannedCode.Carpeta("01C263605020000"), ScannedCodeClassifier.classify("01C263605020000CL7480100"))
        assertEquals(ScannedCode.Relacion("01I26106418"), ScannedCodeClassifier.classify("01I26106418-1"))
        assertEquals(ScannedCode.Relacion("01I26106070"), ScannedCodeClassifier.classify("R-01I26106070  0000  101"))
    }

    @Test
    fun `a load order is no longer a doubtful reference`() {
        assertTrue(ScannedCodeClassifier.classify("01I26106418-1") !is ScannedCode.Candidato)
    }

    @Test
    fun `a package label and a mutilated one keep their old reading next to the official folder`() {
        assertEquals(ScannedCode.Bulto("01C263562840000", 3, 12), ScannedCodeClassifier.classify("01C263562840000 3-12"))
        assertTrue(ScannedCodeClassifier.classify("01C263562840000 18-17") is ScannedCode.Desconocido)
    }

    @Test
    fun `a pre registration code keeps its prefix`() {
        assertEquals(
            ScannedCode.PreRegistro("PMMPR|7K2Q-4481"),
            ScannedCodeClassifier.classify("PMMPR|7K2Q-4481")
        )
    }

    @Test
    fun `a badge read is a badge and keeps no value`() {
        val code = ScannedCodeClassifier.classify("usuario.prueba;clave-qr-1234")

        assertEquals(ScannedCode.Gafete, code)
        assertEquals("", code.normalized)
        assertFalse(code.toString().contains("clave", ignoreCase = true))
        assertFalse(code.toString().contains("usuario", ignoreCase = true))
    }

    @Test
    fun `a badge is never mistaken for another code even when it looks like one`() {
        assertEquals(ScannedCode.Gafete, ScannedCodeClassifier.classify("01C263582800000;abc"))
        assertEquals(ScannedCode.Gafete, ScannedCodeClassifier.classify("E-017846/26;abc"))
    }

    @Test
    fun `customer product labels are recognised apart from PMM labels`() {
        assertTrue(ScannedCodeClassifier.classify("+30S77120045") is ScannedCode.ProductoCliente)
        assertTrue(ScannedCodeClassifier.classify("[)>\u001E06\u001D30S77120045") is ScannedCode.ProductoCliente)
        assertTrue(ScannedCodeClassifier.classify("30S77120045\u001DQ00000600") is ScannedCode.ProductoCliente)
    }

    @Test
    fun `an old short reference is a candidate for the server to decide`() {
        assertEquals(ScannedCode.Candidato("PECE0511COL"), ScannedCodeClassifier.classify("PECE0511COL"))
        assertEquals(ScannedCode.Candidato("ZX-0042"), ScannedCodeClassifier.classify("ZX-0042"))
    }

    @Test
    fun `reads that are not codes PMM uses are unknown`() {
        assertTrue(ScannedCodeClassifier.classify(null) is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("   ") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("A") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("../ruta") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("https://pmm.example/seguimiento/01C26") is ScannedCode.Desconocido)
        assertTrue(ScannedCodeClassifier.classify("X".repeat(121)) is ScannedCode.Desconocido)
    }

    @Test
    fun `the eleven mockup labels classify as the mockup shows`() {
        val esperado = mapOf(
            "E-017846/26" to ScannedCode.Caja("E-017846/26"),
            "E-017799/26" to ScannedCode.Caja("E-017799/26"),
            "E-017831/26" to ScannedCode.Caja("E-017831/26"),
            "E-017702/26" to ScannedCode.Caja("E-017702/26"),
            "01C264395500000 3-12" to ScannedCode.Bulto("01C264395500000", 3, 12),
            "01I264412700000" to ScannedCode.Carpeta("01I264412700000"),
            "01I26104546" to ScannedCode.Relacion("01I26104546"),
            "PMMPR|7K2Q-4481" to ScannedCode.PreRegistro("PMMPR|7K2Q-4481"),
            "+30S77120045" to ScannedCode.ProductoCliente("+30S77120045"),
            "ZX-0042" to ScannedCode.Candidato("ZX-0042")
        )

        esperado.forEach { (lectura, tipo) ->
            assertEquals("Lectura $lectura", tipo, ScannedCodeClassifier.classify(lectura))
        }
    }

    @Test
    fun `shapes do not overlap between box release and entry`() {
        val cajas = listOf("E-00119/20", "E-016278/26", "E-17843/26")
        val relaciones = listOf("01I26104546", "01E17028336", "01C26104546")
        val entradas = listOf("01C263582800000", "01I1431460700R1", "05I1901843500A1")

        cajas.forEach { assertFalse(it, LabelCodeShapes.relacion.matches(it) || LabelCodeShapes.entrada.matches(it)) }
        relaciones.forEach { assertFalse(it, LabelCodeShapes.caja.matches(it) || LabelCodeShapes.entrada.matches(it)) }
        entradas.forEach { assertFalse(it, LabelCodeShapes.caja.matches(it) || LabelCodeShapes.relacion.matches(it)) }
    }
}
