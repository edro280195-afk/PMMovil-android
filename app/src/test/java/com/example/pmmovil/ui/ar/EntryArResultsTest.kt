package com.example.pmmovil.ui.ar

import com.example.pmmovil.data.entryar.ArItemKind
import com.example.pmmovil.data.entryar.EntryArQrCode
import com.example.pmmovil.data.model.EntryArCardDto
import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.components.StationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryArResultsTest {

    private fun code(normalized: String) = EntryArQrCode(normalized, normalized, normalized.substringBefore(' '), null, null, false)

    private fun item(normalized: String, kind: ArItemKind) = EntryArOverlayItem(
        key = normalized,
        code = code(normalized),
        card = EntryArCardDto(reference = normalized, exists = false),
        kind = kind
    )

    private val entry = item("01C263582800000", ArItemKind.Entry)
    private val box = item("E-17848/26", ArItemKind.Caja)
    private val release = item("01I26104549", ArItemKind.Relacion)

    private val card = EntryArCardDto(reference = "01C263582800000", exists = true, customerName = "SKF")
    private val compactBox = TraceCompactItemDto(code = "E-17848/26", kind = "CAJA", found = true, signal = "AVISO", customer = "KATHION")

    @Test
    fun `each kind of code only receives its own data`() {
        val merged = mergeArItems(
            listOf(entry, box, release),
            ArLookup(mapOf(card.reference to card), ArFetchOutcome.Fresh),
            ArLookup(mapOf(compactBox.code to compactBox), ArFetchOutcome.Fresh)
        )

        assertEquals("SKF", merged[0].card.customerName)
        assertNull("La entrada no recibe ficha compacta", merged[0].compact)
        assertEquals("KATHION", merged[1].compact?.customer)
        assertEquals("La caja no se pinta como entrada", false, merged[1].card.exists)
        assertNull("La relación no vino: queda sin ficha", merged[2].compact)
    }

    @Test
    fun `an entry the server does not return reads as non existent`() {
        val merged = mergeArItems(listOf(entry), ArLookup(emptyMap(), ArFetchOutcome.Fresh), ArLookup())

        assertFalse(merged.single().card.exists)
        assertEquals("01C263582800000", merged.single().card.reference)
    }

    @Test
    fun `a query that was not made leaves its codes as they were`() {
        val withCard = entry.copy(card = card)

        val merged = mergeArItems(
            listOf(withCard, box),
            ArLookup(),
            ArLookup(mapOf(compactBox.code to compactBox), ArFetchOutcome.Fresh)
        )

        assertEquals("SKF", merged[0].card.customerName)
        assertEquals(compactBox, merged[1].compact)
    }

    @Test
    fun `offline marks only what really came from saved data`() {
        val merged = mergeArItems(
            listOf(box, release),
            ArLookup(),
            ArLookup(mapOf(compactBox.code to compactBox), ArFetchOutcome.Offline)
        )

        assertTrue("La caja tenía ficha guardada", merged[0].fromCache)
        assertNotNull(merged[0].compact)
        assertFalse("La relación no tenía nada guardado", merged[1].fromCache)
        assertNull(merged[1].compact)
    }

    @Test
    fun `no message when both queries went fine or were not made`() {
        assertNull(arOfflineMessage(ArLookup(), ArLookup()))
        assertNull(arOfflineMessage(ArLookup(emptyMap(), ArFetchOutcome.Fresh), ArLookup(emptyMap(), ArFetchOutcome.Fresh)))
    }

    @Test
    fun `the offline message says whether there is something saved`() {
        val none = arOfflineMessage(ArLookup<EntryArCardDto>(), ArLookup<TraceCompactItemDto>(emptyMap(), ArFetchOutcome.Offline))
        val some = arOfflineMessage(ArLookup(mapOf(card.reference to card), ArFetchOutcome.Offline), ArLookup())

        assertEquals("Sin conexión. No hay datos guardados para estos códigos.", none)
        assertEquals("Sin conexión. Se muestran los últimos datos consultados.", some)
    }

    @Test
    fun `with only entries the old wording is kept`() {
        assertEquals(
            "Sin conexión. No hay datos guardados para estas entradas.",
            arOfflineMessage(ArLookup<EntryArCardDto>(emptyMap(), ArFetchOutcome.Offline), ArLookup())
        )
    }

    @Test
    fun `the compact line always has five stations even if the server sends fewer`() {
        val states = compactStates(TraceCompactItemDto(states = listOf("HECHA", "EN_CURSO")))

        assertEquals(Station.entries.size, states.size)
        assertEquals(StationState.Done, states[0])
        assertEquals(StationState.Now, states[1])
        assertEquals(StationState.Unknown, states[4])
    }

    @Test
    fun `a full set of states maps in the order of the line`() {
        val states = compactStates(
            TraceCompactItemDto(states = listOf("HECHA", "SIN_DATO", "SIN_DATO", "PENDIENTE", "NO_APLICA"))
        )

        assertEquals(
            listOf(StationState.Done, StationState.Unknown, StationState.Unknown, StationState.Todo, StationState.NotApplicable),
            states
        )
    }
}
