package com.example.pmmovil.ui.salidacaseta

import com.example.pmmovil.data.model.SalidaCarril
import com.example.pmmovil.data.model.SalidaPatioCaja
import com.example.pmmovil.data.model.SalidaPatioItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalidaCasetaLogicTest {

    private fun caja(n: String) = SalidaPatioCaja(casetaId = "E-$n", numCaja = n)

    private fun item(id: String, carril: String, minutosEspera: Int? = null, horas: Int = 0, vararg cajas: String) =
        SalidaPatioItem(
            id = id,
            carril = carril,
            relno = if (carril == "MANUAL") "" else id,
            cajas = cajas.map(::caja).ifEmpty { listOf(caja(id)) },
            minutosEspera = minutosEspera,
            horasEnPatio = horas
        )

    private val patio = SalidaUiState(
        items = listOf(
            item("M1", "MANUAL", horas = 5),
            item("L1", "LISTA", minutosEspera = 30),
            item("C1", "CERRAR"),
            item("P1", "PROCESO"),
            item("L2", "LISTA", minutosEspera = 4300),
            item("M2", "MANUAL", horas = 50)
        ),
        isLoading = false
    )

    @Test
    fun `orden por carril y dentro de cada carril lo que mas lleva esperando va primero`() {
        assertEquals(listOf("L2", "L1", "M2", "M1", "P1", "C1"), patio.visibles().map { it.id })
    }

    @Test
    fun `filtro de carril deja solo ese carril`() {
        val filtrado = patio.copy(carrilFiltro = SalidaCarril.MANUAL)
        assertEquals(listOf("M2", "M1"), filtrado.visibles().map { it.id })
    }

    @Test
    fun `la busqueda encuentra una caja dentro de una relacion de varias cajas`() {
        val estado = patio.copy(items = patio.items + item("L3", "LISTA", 10, 0, "52701", "52702", "52703"))
        assertEquals(listOf("L3"), estado.copy(query = "52702").visibles().map { it.id })
    }

    @Test
    fun `los conteos por carril salen del patio completo`() {
        assertEquals(6, patio.conteo(null))
        assertEquals(2, patio.conteo(SalidaCarril.LISTA))
        assertEquals(2, patio.conteo(SalidaCarril.MANUAL))
        assertEquals(1, patio.conteo(SalidaCarril.PROCESO))
        assertEquals(1, patio.conteo(SalidaCarril.CERRAR))
    }

    @Test
    fun `cada carril exige su propio permiso y en proceso nunca se puede actuar`() {
        val soloRelacion = patio.copy(relModificar = true, manualModificar = false)
        val soloManual = patio.copy(relModificar = false, manualModificar = true)
        val lista = patio.items.first { it.id == "L1" }
        val manual = patio.items.first { it.id == "M1" }
        val proceso = patio.items.first { it.id == "P1" }
        val cerrar = patio.items.first { it.id == "C1" }

        assertTrue(soloRelacion.puedeActuar(lista))
        assertTrue(soloRelacion.puedeActuar(cerrar))
        assertFalse(soloRelacion.puedeActuar(manual))
        assertTrue(soloManual.puedeActuar(manual))
        assertFalse(soloManual.puedeActuar(lista))
        assertFalse(soloRelacion.copy(manualModificar = true).puedeActuar(proceso))
    }

    @Test
    fun `un carril desconocido se trata como en proceso para nunca ofrecer una salida por error`() {
        assertEquals(SalidaCarril.PROCESO, SalidaCarril.desde("ALGO_NUEVO"))
        assertEquals(SalidaCarril.PROCESO, SalidaCarril.desde(null))
    }

    @Test
    fun `posicion del paso en el ciclo de la relacion`() {
        assertEquals(0 to false, posicionPaso("", SalidaCarril.PROCESO))
        assertEquals(1 to false, posicionPaso("CARGANDO", SalidaCarril.PROCESO))
        assertEquals(2 to true, posicionPaso("DEMASIADO RAPIDO", SalidaCarril.PROCESO))
        assertEquals(3 to false, posicionPaso("AUTORIZADA", SalidaCarril.LISTA))
        assertEquals(4 to false, posicionPaso("SALIDA", SalidaCarril.CERRAR))
    }

    @Test
    fun `formato de minutos y fechas`() {
        assertEquals("38 min", formatoMinutos(38))
        assertEquals("2 h 41 min", formatoMinutos(161))
        assertEquals("2 d 23 h", formatoMinutos(4300))
        assertEquals("07/08 11:37", formatoFechaCorta("2026-08-07T11:37:40"))
        assertEquals("—", formatoFechaCorta(null))
    }

    @Test
    fun `planta 2 es Atlanta y cualquier otra IMEX`() {
        assertEquals("ATLANTA", nombrePlanta("2"))
        assertEquals("IMEX", nombrePlanta("1"))
        assertEquals(null, nombrePlanta(""))
    }

    // Una relación autorizada cuya caja nunca se registró en caseta (01I26106352, caja 702466 en producción): la caja que
    // trae es la que dice la relación y no tiene casetaId.
    private val sinCaja = SalidaPatioItem(
        id = "01I26106352",
        carril = "LISTA",
        relno = "01I26106352",
        cajas = listOf(SalidaPatioCaja(casetaId = "", numCaja = "702466")),
        minutosEspera = 7345,
        sinCajaEnCaseta = true
    )

    @Test
    fun `una relacion sin caja en caseta se lista, se cuenta y se busca por relacion o por la caja que dice`() {
        val estado = patio.copy(items = patio.items + sinCaja)

        assertTrue(estado.visibles().any { it.id == "01I26106352" })
        assertEquals(3, estado.conteo(SalidaCarril.LISTA))
        assertEquals(listOf("01I26106352"), estado.copy(query = "01I26106352").visibles().map { it.id })
        assertEquals(listOf("01I26106352"), estado.copy(query = "702466").visibles().map { it.id })
    }

    @Test
    fun `una relacion sin caja en caseta se puede dar de salida como cualquier relacion lista`() {
        val estado = patio.copy(relModificar = true, manualModificar = false, items = patio.items + sinCaja)

        assertTrue(estado.puedeActuar(sinCaja))
        // Lleva más tiempo esperando que las demás listas: va primero entre ellas.
        assertEquals("01I26106352", estado.visibles().first().id)
    }

    @Test
    fun `el campo sinCajaEnCaseta se lee del API y un API viejo que no lo manda deja false`() {
        val gson = com.google.gson.Gson()
        val nuevo = gson.fromJson(
            """{"id":"01I26106352","carril":"LISTA","relno":"01I26106352","sinCajaEnCaseta":true,
               "cajas":[{"casetaId":"","numCaja":"702466"}]}""",
            SalidaPatioItem::class.java
        )
        val viejo = gson.fromJson(
            """{"id":"01I26106453","carril":"LISTA","relno":"01I26106453","cajas":[{"casetaId":"E-1","numCaja":"20073"}]}""",
            SalidaPatioItem::class.java
        )

        assertTrue(nuevo.sinCajaEnCaseta)
        assertEquals(SalidaCarril.LISTA, nuevo.carrilEnum)
        assertEquals("702466", nuevo.cajas.single().numCaja)
        assertEquals("", nuevo.cajas.single().casetaId)
        assertFalse(viejo.sinCajaEnCaseta)
    }
}
