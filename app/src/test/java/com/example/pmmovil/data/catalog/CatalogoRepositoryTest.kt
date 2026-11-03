package com.example.pmmovil.data.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogoRepositoryTest {

    private class FuenteFalsa : CatalogoFuente {
        var versiones: RespuestaCatalogos<Map<String, String>> = RespuestaCatalogos.Ok(emptyMap())
        var catalogos: MutableMap<String, CatalogoDescargado> = mutableMapOf()
        var descargaFalla: String? = null
        var llamadasVersiones = 0
        val descargas = mutableListOf<List<String>>()

        fun publica(nombre: String, version: String, vararg items: CatalogoItem) {
            catalogos[nombre] = CatalogoDescargado(nombre, version, items.toList())
            val actuales = (versiones as? RespuestaCatalogos.Ok)?.valor.orEmpty()
            versiones = RespuestaCatalogos.Ok(actuales + (nombre to version))
        }

        override suspend fun versiones(): RespuestaCatalogos<Map<String, String>> {
            llamadasVersiones++
            return versiones
        }

        override suspend fun descargar(nombres: List<String>): RespuestaCatalogos<List<CatalogoDescargado>> {
            descargas += nombres
            descargaFalla?.let { return RespuestaCatalogos.Falla(it) }
            return RespuestaCatalogos.Ok(nombres.mapNotNull { catalogos[it] })
        }
    }

    private class AlmacenFalso : CatalogoAlmacen {
        val guardado = mutableMapOf<String, Pair<String, List<CatalogoItem>>>()
        var lecturas = 0

        override suspend fun versiones(): Map<String, String> = guardado.mapValues { it.value.first }

        override suspend fun items(catalogo: String): List<CatalogoItem> {
            lecturas++
            return guardado[catalogo]?.second.orEmpty()
        }

        override suspend fun reemplazar(catalogo: String, items: List<CatalogoItem>, version: String) {
            guardado[catalogo] = version to items
        }
    }

    private var reloj = 1_000_000L
    private val fuente = FuenteFalsa()
    private val almacen = AlmacenFalso()
    private val advertencias = mutableListOf<String>()
    private val repo = CatalogoRepository(fuente, almacen, ahora = { reloj }, advertir = { advertencias += it })

    private val cliente = CatalogoItem("3300", "WATSON PHILLIPS", "C")
    private val otro = CatalogoItem("CL1", "OTRO CLIENTE")

    @Test
    fun `la primera vez baja lo que falta y avisa que cambio`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        fuente.publica("lineas", "1-bbb", CatalogoItem("L1", "TRANSPORTES UNO"))

        val resultado = repo.sincronizar(listOf("clientes", "lineas"))

        assertEquals(ResultadoSync.Actualizado(setOf("clientes", "lineas")), resultado)
        assertEquals(listOf(cliente, otro), repo.items("clientes"))
        assertEquals(1, fuente.descargas.size)
        assertEquals(listOf("clientes", "lineas"), fuente.descargas.single())
    }

    @Test
    fun `con la misma version no baja nada`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1

        val resultado = repo.sincronizar(listOf("clientes"))

        assertEquals(ResultadoSync.SinCambios, resultado)
        assertEquals("Solo se pregunto la version, sin descargar de nuevo", 1, fuente.descargas.size)
        assertEquals(2, fuente.llamadasVersiones)
    }

    @Test
    fun `solo baja el catalogo que cambio`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        fuente.publica("lineas", "1-bbb", CatalogoItem("L1", "TRANSPORTES UNO"))
        repo.sincronizar(listOf("clientes", "lineas"))
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1
        fuente.publica("lineas", "2-ccc", CatalogoItem("L1", "TRANSPORTES UNO"), CatalogoItem("L2", "TRANSPORTES DOS"))

        val resultado = repo.sincronizar(listOf("clientes", "lineas"))

        assertEquals(ResultadoSync.Actualizado(setOf("lineas")), resultado)
        assertEquals(listOf("lineas"), fuente.descargas.last())
        assertEquals(2, repo.items("lineas").size)
        assertEquals("Los clientes no se tocaron", listOf(cliente, otro), repo.items("clientes"))
    }

    @Test
    fun `no vuelve a preguntar antes de un minuto salvo que se fuerce`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))

        val enseguida = repo.sincronizar(listOf("clientes"))
        assertEquals(ResultadoSync.SinCambios, enseguida)
        assertEquals("No hizo ninguna llamada nueva", 1, fuente.llamadasVersiones)

        repo.sincronizar(listOf("clientes"), forzar = true)
        assertEquals("Forzado si pregunta", 2, fuente.llamadasVersiones)
    }

    @Test
    fun `sin red conserva lo guardado y avisa que no estuvo disponible`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1
        fuente.versiones = RespuestaCatalogos.Falla("sin red")

        val resultado = repo.sincronizar(listOf("clientes"))

        assertEquals(ResultadoSync.NoDisponible("sin red", servidorSinCatalogos = false), resultado)
        assertEquals("Sigue mostrando lo guardado", listOf(cliente, otro), repo.items("clientes"))
    }

    @Test
    fun `una descarga que falla a medias no borra nada y se reintenta`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        fuente.descargaFalla = "timeout"

        val primero = repo.sincronizar(listOf("clientes"))
        assertTrue(primero is ResultadoSync.NoDisponible)
        assertTrue("No quedo nada guardado", repo.items("clientes").isEmpty())

        fuente.descargaFalla = null
        val segundo = repo.sincronizar(listOf("clientes"))
        assertEquals("No espero un minuto: no quedo marcado como revisado", ResultadoSync.Actualizado(setOf("clientes")), segundo)
    }

    @Test
    fun `un servidor anterior se reconoce y no se insiste en cada pantalla`() = runBlocking {
        fuente.versiones = RespuestaCatalogos.NoSoportado

        val primero = repo.sincronizar(listOf("clientes"))
        assertEquals(ResultadoSync.NoDisponible("El servidor aún no tiene catálogos maestros.", servidorSinCatalogos = true), primero)

        repo.sincronizar(listOf("lineas"))
        assertEquals("No volvio a preguntar", 1, fuente.llamadasVersiones)

        reloj += CatalogoRepository.REINTENTO_SERVIDOR_ANTERIOR_MS + 1
        fuente.publica("clientes", "2-aaa", cliente)
        val despues = repo.sincronizar(listOf("clientes"))
        assertEquals("Cuando se despliega el API, lo detecta", ResultadoSync.Actualizado(setOf("clientes")), despues)
    }

    @Test
    fun `un catalogo que llega vacio no borra el que ya estaba`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1
        fuente.publica("clientes", "0-vacio")

        val resultado = repo.sincronizar(listOf("clientes"))

        assertEquals(ResultadoSync.SinCambios, resultado)
        assertEquals(listOf(cliente, otro), repo.items("clientes"))
        assertEquals(1, advertencias.size)
    }

    @Test
    fun `un catalogo que el servidor no pudo leer se queda como estaba`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1
        // El servidor ya no lista "clientes" (fallo su consulta): la tableta no lo pierde.
        fuente.versiones = RespuestaCatalogos.Ok(emptyMap())

        val resultado = repo.sincronizar(listOf("clientes"))

        assertEquals(ResultadoSync.SinCambios, resultado)
        assertEquals(listOf(cliente, otro), repo.items("clientes"))
    }

    @Test
    fun `lee de la base una sola vez y luego de memoria`() = runBlocking {
        almacen.guardado["clientes"] = "2-aaa" to listOf(cliente, otro)

        repo.items("clientes")
        repo.items("clientes")
        repo.items("clientes")

        assertEquals(1, almacen.lecturas)
    }

    @Test
    fun `un catalogo nuevo reemplaza al anterior y se vuelve a leer`() = runBlocking {
        fuente.publica("clientes", "2-aaa", cliente, otro)
        repo.sincronizar(listOf("clientes"))
        repo.items("clientes")
        reloj += CatalogoRepository.REVISION_MINIMA_MS + 1
        fuente.publica("clientes", "1-zzz", CatalogoItem("9", "UNICO"))

        repo.sincronizar(listOf("clientes"))

        assertEquals(listOf(CatalogoItem("9", "UNICO")), repo.items("clientes"))
        assertFalse(repo.items("clientes").contains(cliente))
    }
}
