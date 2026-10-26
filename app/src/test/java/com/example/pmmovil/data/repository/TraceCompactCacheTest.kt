package com.example.pmmovil.data.repository

import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.data.model.TraceCompactResponseDto
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceCompactCacheTest {

    private var clock = 1_000L
    private val cache = TraceCompactCache { clock }
    private fun item(code: String) = TraceCompactItemDto(code = code, kind = "CAJA", found = true)

    @Test
    fun `what was just asked is fresh and what was never asked is absent`() {
        cache.put(listOf(item("E-1/26")))

        assertEquals(setOf("E-1/26"), cache.fresh(listOf("E-1/26", "E-2/26")).keys)
    }

    @Test
    fun `an answer stops being fresh after thirty seconds but is still the latest known`() {
        cache.put(listOf(item("E-1/26")))
        clock += 29_999
        assertEquals(setOf("E-1/26"), cache.fresh(listOf("E-1/26")).keys)

        clock += 2
        assertTrue(cache.fresh(listOf("E-1/26")).isEmpty())
        // Sin red, lo último que se vio sigue sirviendo.
        assertEquals(setOf("E-1/26"), cache.latest(listOf("E-1/26")).keys)
    }

    @Test
    fun `a clock that goes backwards does not freeze an answer as fresh forever`() {
        cache.put(listOf(item("E-1/26")))
        clock = 10

        assertTrue(cache.fresh(listOf("E-1/26")).isEmpty())
    }

    @Test
    fun `an item without a code is never stored`() {
        cache.put(listOf(item("")))

        assertTrue(cache.latest(listOf("")).isEmpty())
    }

    @Test
    fun `the cache does not grow without limit`() {
        repeat(TraceCompactCache.MAX_ENTRIES + 20) { index ->
            clock += 1
            cache.put(listOf(item("E-$index/26")))
        }

        val kept = cache.latest((0 until TraceCompactCache.MAX_ENTRIES + 20).map { "E-$it/26" })
        assertEquals(TraceCompactCache.MAX_ENTRIES, kept.size)
        assertTrue("Se van primero los más viejos", "E-0/26" !in kept)
        assertTrue(kept.containsKey("E-${TraceCompactCache.MAX_ENTRIES + 19}/26"))
    }

    @Test
    fun `the json the server sends reads into the model`() {
        val json = """
            {"success":true,"items":[
              {"code":"E-17840/26","kind":"CAJA","found":true,"reason":null,"signal":"AVISO",
               "states":["HECHA","HECHA","SIN_DATO","EN_CURSO","PENDIENTE"],
               "flags":["ENTRADA_POR_RELACION","HAZMAT"],"customer":"KATHION","box":"1657-2","relno":"01I26104549"},
              {"code":"","kind":"","found":false,"reason":"GAFETE_NO_SE_CONSULTA","signal":"SIN_DATO",
               "states":["SIN_DATO","SIN_DATO","SIN_DATO","SIN_DATO","SIN_DATO"],"flags":[],"customer":"","box":"","relno":""}
            ]}
        """.trimIndent()

        val response = Gson().fromJson(json, TraceCompactResponseDto::class.java)

        assertTrue(response.success)
        assertEquals(2, response.items.size)
        val first = response.items.first()
        assertEquals("E-17840/26", first.code)
        assertEquals("CAJA", first.kind)
        assertEquals("AVISO", first.signal)
        assertEquals(5, first.states.size)
        assertEquals("EN_CURSO", first.states[3])
        assertEquals(listOf("ENTRADA_POR_RELACION", "HAZMAT"), first.flags)
        assertEquals("1657-2", first.box)
        assertEquals("01I26104549", first.relno)
        // Un código rechazado vuelve sin valor: nunca se guarda ni se pinta.
        assertEquals("", response.items[1].code)
    }
}
