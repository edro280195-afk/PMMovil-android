package com.example.pmmovil.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageIdTest {
    // Los códigos reales de las etiquetas de la entrada 01C263606600000 (octubre de 2026).
    private val caja = listOf("S279239665", "+30S0049263333+Q00000600+H0031704192+2D092036+2X497608092026+P049879/003")
    private val bolsaDeOrings = listOf("+30S0075875114+Q000210+H0426090326+2D082036+2X566405082026+P0000000000075875")

    @Test
    fun `la bolsa de Taiwan trae su identificador entre los ocho codigos`() {
        val lectura = PackageId.read(BolsaFreudenbergFixture.codigos)

        assertEquals(PackageId.Reading.Single("1Y-00000050337428", PackageId.Kind.BAG), lectura)
    }

    @Test
    fun `la caja trae el identificador en su Code 39 y no se confunde con el PDF417`() {
        assertEquals(PackageId.Reading.Single("S279239665", PackageId.Kind.BOX), PackageId.read(caja))
    }

    @Test
    fun `la bolsa de O-rings solo trae el PDF417 y no tiene identificador`() {
        assertEquals(PackageId.Reading.None, PackageId.read(bolsaDeOrings))
    }

    @Test
    fun `ninguna otra cosa de la bolsa se toma por identificador`() {
        // El PO, la clave, la cantidad, el proveedor, el curado, el origen y el lote.
        val sinElId = BolsaFreudenbergFixture.codigos - "1Y-00000050337428"

        assertEquals(PackageId.Reading.None, PackageId.read(sinElId))
    }

    @Test
    fun `dos identificadores distintos son dos etiquetas en el cuadro`() {
        val dosEtiquetas = BolsaFreudenbergFixture.codigos + "1Y-00000050337999"

        val lectura = PackageId.read(dosEtiquetas)

        assertTrue(lectura is PackageId.Reading.Multiple)
        assertEquals(listOf("1Y-00000050337428", "1Y-00000050337999"), (lectura as PackageId.Reading.Multiple).values)
    }

    @Test
    fun `el mismo identificador leido dos veces es uno solo`() {
        val lectura = PackageId.read(listOf("S279239665", "S279239665", " S279239665\n"))

        assertEquals(PackageId.Reading.Single("S279239665", PackageId.Kind.BOX), lectura)
    }

    @Test
    fun `formas parecidas pero distintas no cuentan`() {
        listOf("1Y-123", "1y-00000050337428", "S12", "S123456789012345", "SX279239665", "1Y00000050337428").forEach { codigo ->
            assertEquals("«$codigo» no debe tomarse por identificador", PackageId.Reading.None, PackageId.read(listOf(codigo)))
        }
    }

    @Test
    fun `sin codigos no hay identificador`() {
        assertEquals(PackageId.Reading.None, PackageId.read(emptyList()))
    }
}
