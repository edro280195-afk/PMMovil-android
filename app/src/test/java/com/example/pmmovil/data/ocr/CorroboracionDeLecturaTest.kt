package com.example.pmmovil.data.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La corroboración entre el código de barras y lo impreso, con lo medido en el Pixel el 20-oct-2026.
 *
 * De la **misma** etiqueta de envío VDA, en lecturas distintas, el código de la cantidad salió
 * `Q20000`, `Q2000000` y `Q20000000`. Lo impreso siempre dice 20000. Dos de las tres veces el valor
 * malo coincidió en la foto y en el video, así que compararlos entre sí no lo habría detectado.
 */
class CorroboracionDeLecturaTest {

    /** El texto que el lector saca de la etiqueta, con la cantidad y el lote impresos. */
    private val impreso = """
        DELIVERY NOTE NUMBER (N) 81706661
        PART NUMBER CUSTOMER (P) 000000000075370203
        QUANTITY (Q) 20000 PC
        VENDOR NUMBER (V) 0000000305
        PACKAGE NO. (S) 279544448
        BATCH NO. (H) 5004029781
        NET WT KG 8   GROSS WT KG 9
    """.trimIndent()

    @Test
    fun `la cantidad buena esta impresa`() {
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("20000", impreso))
    }

    @Test
    fun `la cantidad con un cero de mas no esta impresa`() {
        assertFalse(CorroboracionDeLectura.cantidadApareceImpresa("200000", impreso))
    }

    @Test
    fun `los dos valores malos que de verdad salieron se rechazan`() {
        assertFalse("Q2000000 del 20-oct 11:33", CorroboracionDeLectura.cantidadApareceImpresa("2000000", impreso))
        assertFalse("Q20000000 del 20-oct 11:26", CorroboracionDeLectura.cantidadApareceImpresa("20000000", impreso))
    }

    @Test
    fun `una cantidad mas chica tampoco cuela`() {
        // El error peligroso: cae dentro de lo plausible y nadie lo notaría.
        assertFalse(CorroboracionDeLectura.cantidadApareceImpresa("2000", impreso))
    }

    @Test
    fun `la cantidad rellena de ceros si corrobora con la impresa`() {
        // El PDF417 de la caja trae Q00000600 y debajo dice 600; el del químico, Q0073.500 y 73.500.
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("00000600", "QTY 600 PC"))
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("0073.500", "QTY / Menge (Q) 73.500 KG"))
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("000210", "QTY 210"))
    }

    @Test
    fun `los decimales se comparan por valor, no por como se escriben`() {
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("73.5", "73.500 KG"))
        assertTrue(CorroboracionDeLectura.cantidadApareceImpresa("73.500", "73,5 KG"))
        assertFalse(CorroboracionDeLectura.cantidadApareceImpresa("735", "73.500 KG"))
    }

    @Test
    fun `el lote y el empaque impresos corroboran`() {
        assertTrue(CorroboracionDeLectura.apareceImpreso("5004029781", impreso))
        assertTrue(CorroboracionDeLectura.apareceImpreso("279544448", impreso))
    }

    @Test
    fun `un lote con un digito de mas no corrobora`() {
        assertFalse(CorroboracionDeLectura.apareceImpreso("50040297810", impreso))
    }

    @Test
    fun `los separadores de lo impreso no estorban`() {
        assertTrue(CorroboracionDeLectura.apareceImpreso("5004029781", "Batch: 5004 029 781"))
    }

    @Test
    fun `la clave corrobora con o sin los ceros de relleno`() {
        assertTrue(CorroboracionDeLectura.apareceImpresoSinCeros("000000000075370203", impreso))
        assertTrue(CorroboracionDeLectura.apareceImpresoSinCeros("0075370203", "SUPPLIER REF 75370203"))
    }

    @Test
    fun `sin texto no se corrobora nada`() {
        // No saber es igual que no coincidir: no se da por bueno.
        assertFalse(CorroboracionDeLectura.apareceImpreso("20000", ""))
    }

    @Test
    fun `un valor muy corto no se corrobora`() {
        // Dos dígitos aparecen por casualidad en cualquier etiqueta.
        assertFalse(CorroboracionDeLectura.apareceImpreso("20", impreso))
    }
}
