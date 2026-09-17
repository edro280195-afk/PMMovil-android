package com.example.pmmovil.ui.entrada

import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaFormSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntradaRulesTest {

    @Test
    fun `requires the same core fields as web`() {
        val errors = EntradaRules.validate(EntradaFormSnapshot())

        assertTrue(errors.any { it.field == "ref4" })
        assertTrue(errors.any { it.field == "fecha" })
        assertTrue(errors.any { it.field == "cliente" })
        assertTrue(errors.any { it.field == "manejo" })
        assertTrue(errors.any { it.field == "detalles" })
    }

    @Test
    fun `hazmat and in bond require their conditional values`() {
        val errors = EntradaRules.validate(
            EntradaFormSnapshot(
                ref4 = "1234",
                fecha = "2026-06-15T10:00",
                codCliente = "C001",
                manejo = "1",
                hazmat = true,
                inBond = true,
                detalles = listOf(EntradaDetail(1.0, descripcion = "Caja"))
            )
        )

        assertTrue(errors.any { it.field == "claseHazmat" })
        assertTrue(errors.any { it.field == "un" })
        assertTrue(errors.any { it.field == "inBondVal" })
    }

    @Test
    fun `weight conversion matches web constants`() {
        assertEquals(45.3592, EntradaRules.kilosFromPounds(100.0), 0.0001)
        assertEquals(220.462, EntradaRules.poundsFromKilos(100.0), 0.001)
    }

    @Test
    fun `detail requires description and positive quantity`() {
        assertEquals(
            "Captura la descripción de la mercancía.",
            EntradaRules.validateDetail(EntradaDetail(cantidad = 1.0))
        )
        assertEquals(
            "Captura una cantidad mayor a cero.",
            EntradaRules.validateDetail(EntradaDetail(descripcion = "Tarima"))
        )
    }
}
