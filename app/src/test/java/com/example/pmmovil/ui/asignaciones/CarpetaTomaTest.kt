package com.example.pmmovil.ui.asignaciones

import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.model.TomarCarpetaResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarpetaTomaTest {

    private fun entry(
        tomadaPor: String? = null,
        usuarioRevisando: String? = null,
        usuarioTomoDocumento: String? = null,
        esAsignada: Boolean? = null,
        asignSupervisor: String? = null,
        totalFacturas: Int = 2,
        facturasCompletas: Int = 0,
        finRevision: String? = null
    ) = EntradaPendienteDto(
        crefAgrupada = "01C263616880000",
        nombreReferencia = null,
        clienteId = null,
        nombreCliente = "CLIENTE DE PRUEBA",
        dFecha = null,
        totalFacturas = totalFacturas,
        facturasCompletas = facturasCompletas,
        totalPartidas = 0,
        partidasRevisadas = 0,
        inicioRevision = null,
        finRevision = finRevision,
        usuarioRevisando = usuarioRevisando,
        usuarioTomoDocumento = usuarioTomoDocumento,
        todasRelacionCompleta = false,
        esAsignada = esAsignada,
        asignPrioridad = null,
        idAsignacion = null,
        asignSupervisor = asignSupervisor,
        tomadaPor = tomadaPor
    )

    // ── Lo que responde el servidor ────────────────────────────────────────────────────────────

    @Test
    fun `una toma nueva es un exito`() {
        val result = CarpetaToma.interpretar(
            TomarCarpetaResponse(success = true, codigo = "TOMADA", mensaje = "Carpeta tomada.")
        )

        assertEquals(TomaResultado.Tomada("Carpeta tomada.", yaEraTuya = false), result)
    }

    @Test
    fun `volver a tomar la propia carpeta tambien es exito y dice que ya era tuya`() {
        val result = CarpetaToma.interpretar(TomarCarpetaResponse(success = true, codigo = "YA_ERA_TUYA"))

        assertEquals(TomaResultado.Tomada(null, yaEraTuya = true), result)
    }

    @Test
    fun `si otra persona la tenia el servidor pide confirmar y la app dice quien`() {
        val result = CarpetaToma.interpretar(
            TomarCarpetaResponse(
                success = false,
                codigo = "TOMADA_POR_OTRO",
                mensaje = "ereyes la tomó a las 09:30.",
                tomadaPor = " ereyes ",
                desde = "2026-11-12T09:30:00"
            )
        )

        assertEquals(TomaResultado.Conflicto("ereyes", "2026-11-12T09:30:00", "ereyes la tomó a las 09:30."), result)
    }

    @Test
    fun `lo demas son motivos para no tomarla y nunca piden confirmar`() {
        listOf("YA_REVISADA", "EN_REVISION_POR_OTRO", "EN_REVISION_PROPIA", "NO_EXISTE", "OCUPADO", "REFERENCIA_VACIA")
            .forEach { codigo ->
                val result = CarpetaToma.interpretar(
                    TomarCarpetaResponse(success = false, codigo = codigo, mensaje = "motivo")
                )
                assertEquals(codigo, TomaResultado.NoSePuede("motivo"), result)
            }
    }

    // ── De quién es la carpeta ─────────────────────────────────────────────────────────────────

    @Test
    fun `las claves se comparan sin relleno ni mayusculas`() {
        assertTrue(CarpetaToma.esMia("shinojosa", "shinojosa"))
        assertTrue(CarpetaToma.esMia("  ShInOjOsA ", "shinojosa"))
        assertFalse(CarpetaToma.esMia("shinojosa", "alarumbe"))
        assertFalse(CarpetaToma.esMia(null, "shinojosa"))
        assertFalse(CarpetaToma.esMia("", ""))
        assertFalse(CarpetaToma.esMia("shinojosa", null))
    }

    @Test
    fun `una toma propia se reconoce por el servidor o por la copia local de la asignacion`() {
        assertTrue(CarpetaToma.esTomaPropia(entry(tomadaPor = "shinojosa"), "shinojosa"))
        // Sin red solo queda la copia local: una toma es una asignacion hecha por uno mismo.
        assertTrue(CarpetaToma.esTomaPropia(entry(esAsignada = true, asignSupervisor = "shinojosa"), "shinojosa"))
        assertFalse(CarpetaToma.esTomaPropia(entry(esAsignada = true, asignSupervisor = "supervisor1"), "shinojosa"))
        assertFalse(CarpetaToma.esTomaPropia(entry(tomadaPor = "alarumbe"), "shinojosa"))
        assertFalse(CarpetaToma.esTomaPropia(entry(), "shinojosa"))
    }

    // ── Cuándo se ofrece «Tomar carpeta» ───────────────────────────────────────────────────────

    @Test
    fun `se ofrece tomar una carpeta que nadie trae`() {
        assertTrue(CarpetaToma.puedeTomar(entry(), "shinojosa"))
    }

    @Test
    fun `se ofrece tomar una carpeta que otra persona tomo sin iniciar y el servidor pregunta`() {
        assertTrue(CarpetaToma.puedeTomar(entry(tomadaPor = "alarumbe"), "shinojosa"))
    }

    @Test
    fun `no se ofrece tomar la que ya es tuya`() {
        assertFalse(CarpetaToma.puedeTomar(entry(tomadaPor = "shinojosa"), "shinojosa"))
    }

    @Test
    fun `no se ofrece tomar la que otra persona esta revisando`() {
        assertFalse(CarpetaToma.puedeTomar(entry(usuarioRevisando = "alarumbe"), "shinojosa"))
        assertFalse(CarpetaToma.puedeTomar(entry(usuarioTomoDocumento = "alarumbe"), "shinojosa"))
    }

    @Test
    fun `no se ofrece tomar una entrada ya terminada`() {
        assertFalse(
            CarpetaToma.puedeTomar(entry(totalFacturas = 2, facturasCompletas = 2), "shinojosa")
        )
    }

    // ── Cuánto lleva sin iniciar ───────────────────────────────────────────────────────────────

    @Test
    fun `el tiempo sin iniciar se dice corto y legible`() {
        assertNull(CarpetaToma.tiempoSinIniciar(null))
        assertEquals("<1 min", CarpetaToma.tiempoSinIniciar(0))
        assertEquals("<1 min", CarpetaToma.tiempoSinIniciar(59))
        assertEquals("<1 min", CarpetaToma.tiempoSinIniciar(-30))
        assertEquals("12 min", CarpetaToma.tiempoSinIniciar(12 * 60L + 5))
        assertEquals("1 h", CarpetaToma.tiempoSinIniciar(3600))
        assertEquals("3 h 20 min", CarpetaToma.tiempoSinIniciar(3 * 3600L + 20 * 60))
        assertEquals("7 h 30 min", CarpetaToma.tiempoSinIniciar(7 * 3600L + 30 * 60))
        assertEquals("2 d", CarpetaToma.tiempoSinIniciar(2 * 24 * 3600L + 5))
    }

    @Test
    fun `el JSON del servidor se lee completo`() {
        val json = """
            {"success":false,"codigo":"TOMADA_POR_OTRO","mensaje":"ereyes la tomó a las 09:30.",
             "tomadaPor":"ereyes","desde":"2026-11-12T09:30:00","idAsignacion":41,"nombreCliente":"CLIENTE"}
        """.trimIndent()
        val parsed = com.google.gson.Gson().fromJson(json, TomarCarpetaResponse::class.java)

        assertFalse(parsed.success)
        assertEquals("TOMADA_POR_OTRO", parsed.codigo)
        assertEquals("ereyes", parsed.tomadaPor)
        assertEquals(41, parsed.idAsignacion)
        assertTrue(CarpetaToma.interpretar(parsed) is TomaResultado.Conflicto)
    }

    @Test
    fun `una entrada pendiente que trae la toma la expone`() {
        val json = """
            {"crefAgrupada":"01C263616880000","totalFacturas":1,"facturasCompletas":0,"totalPartidas":2,
             "partidasRevisadas":0,"todasRelacionCompleta":false,
             "tomadaPor":"shinojosa","tomadaDesde":"2026-11-12T09:30:00","segundosDesdeToma":27000}
        """.trimIndent()
        val parsed = com.google.gson.Gson().fromJson(json, EntradaPendienteDto::class.java)

        assertEquals("shinojosa", parsed.tomadaPor)
        assertEquals("2026-11-12T09:30:00", parsed.tomadaDesde)
        assertEquals("7 h 30 min", CarpetaToma.tiempoSinIniciar(parsed.segundosDesdeToma))
        assertTrue(CarpetaToma.esTomaPropia(parsed, "shinojosa"))
    }
}
