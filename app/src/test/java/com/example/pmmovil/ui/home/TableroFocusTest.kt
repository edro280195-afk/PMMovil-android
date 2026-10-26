package com.example.pmmovil.ui.home

import com.example.pmmovil.ui.components.Station
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TableroFocusTest {

    @Test
    fun `review wins when the user has it`() {
        assertEquals(
            Station.Revision,
            tableroFocus(listOf(Station.Caseta, Station.Entrada, Station.Revision, Station.Salida))
        )
    }

    @Test
    fun `gate comes next when there is no review`() {
        assertEquals(Station.Caseta, tableroFocus(listOf(Station.Caseta, Station.Salida)))
    }

    @Test
    fun `otherwise the first station the user has`() {
        assertEquals(Station.Relacion, tableroFocus(listOf(Station.Relacion, Station.Salida)))
        assertEquals(Station.Entrada, tableroFocus(listOf(Station.Entrada)))
    }

    @Test
    fun `no station means nothing to highlight`() {
        assertNull(tableroFocus(emptyList()))
    }
}
