package com.example.pmmovil.ui.home

import com.example.pmmovil.R
import com.example.pmmovil.data.model.HomeStationExceptionDto
import com.example.pmmovil.data.model.HomeStationSummaryDto
import com.example.pmmovil.data.model.HomeSummaryDto
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.Station
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardFiguresTest {

    /** Exactamente lo que serializa `Tests/HomeSummary.Regression` del servidor (camelCase, nulos explícitos). */
    private val serverJson = """
        {"success":true,
         "caseta":{"count":164,"exception":{"kind":"ATENCION","code":"OLD_IN_YARD","count":103}},
         "entrada":{"count":149,"exception":null},
         "revision":null,
         "relacion":{"count":2,"exception":null},
         "salida":{"count":47,"exception":{"kind":"LISTO","code":"LISTAS_PARA_SALIR","count":11}},
         "cacheSeconds":30}
    """.trimIndent()

    private fun parse(json: String = serverJson): HomeSummaryDto = Gson().fromJson(json, HomeSummaryDto::class.java)

    @Test
    fun `the json the server sends reads into the model`() {
        val dto = parse()

        assertTrue(dto.success)
        assertEquals(164, dto.caseta?.count)
        assertEquals("ATENCION", dto.caseta?.exception?.kind)
        assertEquals("OLD_IN_YARD", dto.caseta?.exception?.code)
        assertEquals(103, dto.caseta?.exception?.count)
        assertEquals(149, dto.entrada?.count)
        assertNull(dto.entrada?.exception)
        assertNull(dto.revision)
        assertEquals(47, dto.salida?.count)
        assertEquals(30, dto.cacheSeconds)
    }

    @Test
    fun `each station gets its figure and an absent one stays null`() {
        val figures = boardFigures(parse())

        assertEquals(Station.entries.toSet(), figures.keys)
        assertEquals(164, figures.getValue(Station.Caseta)?.count)
        assertEquals(149, figures.getValue(Station.Entrada)?.count)
        assertNull(figures.getValue(Station.Revision))
        assertEquals(2, figures.getValue(Station.Relacion)?.count)
        assertEquals(47, figures.getValue(Station.Salida)?.count)
    }

    @Test
    fun `the exception carries its seal and its case count`() {
        val figures = boardFigures(parse())

        val yard = figures.getValue(Station.Caseta)?.exception
        assertNotNull(yard)
        assertEquals(SignalKind.Atencion, yard!!.signal)
        assertEquals(BoardExceptionCode.OldInYard, yard.code)
        assertEquals(103, yard.count)

        val exit = figures.getValue(Station.Salida)?.exception
        assertEquals(SignalKind.Listo, exit?.signal)
        assertEquals(BoardExceptionCode.ReadyToExit, exit?.code)
    }

    @Test
    fun `a station with no cases shows no exception`() {
        assertNull(boardFigures(parse()).getValue(Station.Entrada)?.exception)
        assertNull(boardFigures(parse()).getValue(Station.Relacion)?.exception)
    }

    @Test
    fun `an exception with a code this version does not know is left out but the figure stays`() {
        val dto = HomeStationSummaryDto(5, HomeStationExceptionDto("ALTO", "CODIGO_FUTURO", 3))

        val figure = boardFigureOf(dto)

        assertEquals(5, figure?.count)
        assertNull(figure?.exception)
    }

    @Test
    fun `an exception with no cases is never shown as zero something`() {
        val dto = HomeStationSummaryDto(5, HomeStationExceptionDto("ALTO", "DISCREPANCIAS", 0))

        assertNull(boardFigureOf(dto)?.exception)
    }

    @Test
    fun `every code the server can send has a phrase`() {
        listOf("OLD_IN_YARD", "DISCREPANCIAS", "BLOQUEADAS", "LISTAS_PARA_SALIR")
            .forEach { code -> assertNotNull("Falta la frase de $code", exceptionCodeOf(code)) }
        assertNull(exceptionCodeOf("OTRO"))
        assertEquals(R.plurals.home_exception_blocked, exceptionCodeOf("BLOQUEADAS")?.phrase)
    }

    @Test
    fun `a negative count from the server reads as zero`() {
        assertEquals(0, boardFigureOf(HomeStationSummaryDto(-4))?.count)
    }

    @Test
    fun `each station has its own unit`() {
        val units = Station.entries.map(::stationUnit)

        assertEquals(units.size, units.toSet().size)
    }

    @Test
    fun `the board shows a skeleton until the first answer and a dash only if it failed`() {
        assertTrue(HomeSummaryUiState().loading)
        assertFalse(HomeSummaryUiState(loaded = true).loading)
        assertFalse(HomeSummaryUiState(failed = true).loading)
    }

    @Test
    fun `the figures are asked for again only after the minimum interval`() {
        val interval = 20_000L

        assertTrue("La primera vez siempre se pide", shouldRefreshSummary(null, 1_000, interval))
        assertFalse("Dentro del intervalo no", shouldRefreshSummary(1_000, 15_000, interval))
        assertFalse("Un instante antes del límite tampoco", shouldRefreshSummary(1_000, 20_999, interval))
        assertTrue("Cumplido el intervalo sí", shouldRefreshSummary(1_000, 21_000, interval))
        assertTrue("Un reloj que retrocede no deja la cifra congelada", shouldRefreshSummary(50_000, 10_000, interval))
    }
}
