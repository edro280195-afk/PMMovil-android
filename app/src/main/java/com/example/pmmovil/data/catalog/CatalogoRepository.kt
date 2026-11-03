package com.example.pmmovil.data.catalog

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.CatalogoDao
import com.example.pmmovil.data.model.CatalogoItemEntity
import com.example.pmmovil.data.model.CatalogoVersionEntity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Un catálogo recién bajado del servidor. */
data class CatalogoDescargado(val nombre: String, val version: String, val items: List<CatalogoItem>)

sealed interface RespuestaCatalogos<out T> {
    data class Ok<T>(val valor: T) : RespuestaCatalogos<T>

    /** El servidor es una versión anterior, sin catálogos maestros: la app usa el camino de siempre. */
    object NoSoportado : RespuestaCatalogos<Nothing>

    data class Falla(val motivo: String) : RespuestaCatalogos<Nothing>
}

/** De dónde vienen los catálogos (el servidor). Es una interfaz para poder probar la sincronización sin red. */
interface CatalogoFuente {
    suspend fun versiones(): RespuestaCatalogos<Map<String, String>>
    suspend fun descargar(nombres: List<String>): RespuestaCatalogos<List<CatalogoDescargado>>
}

/** Dónde se guardan en la tableta. */
interface CatalogoAlmacen {
    suspend fun versiones(): Map<String, String>
    suspend fun items(catalogo: String): List<CatalogoItem>
    suspend fun reemplazar(catalogo: String, items: List<CatalogoItem>, version: String)
}

sealed interface ResultadoSync {
    /** Se bajó al menos un catálogo nuevo: quien lo mostraba debe volver a leerlo. */
    data class Actualizado(val cambiados: Set<String>) : ResultadoSync

    /** Lo que hay en la tableta ya es lo vigente (o se revisó hace un momento). */
    object SinCambios : ResultadoSync

    /** No se pudo revisar (sin red, o servidor anterior): se sigue con lo que haya guardado. */
    data class NoDisponible(val motivo: String, val servidorSinCatalogos: Boolean) : ResultadoSync
}

/**
 * Catálogos maestros (clientes, líneas, tipos de caja, países, unidades…) guardados en la tableta.
 *
 * Antes cada pantalla pedía su lista completa al abrirse (170 KB solo en Entrada de caseta) y el guardado esperaba a
 * que llegara. Ahora la pantalla pinta al instante lo que hay guardado y [sincronizar] pregunta las versiones (unos
 * cientos de bytes) y baja únicamente los catálogos que cambiaron. Lo comparten todos los módulos.
 */
class CatalogoRepository(
    private val fuente: CatalogoFuente,
    private val almacen: CatalogoAlmacen,
    private val ahora: () -> Long = System::currentTimeMillis,
    private val advertir: (String) -> Unit = {}
) {
    private val memoria = ConcurrentHashMap<String, List<CatalogoItem>>()
    private val turno = Mutex()
    private val revisadoEn = mutableMapOf<String, Long>()
    private var noSoportadoHasta = 0L

    /** Lo guardado en la tableta, sin tocar la red. Vacío si nunca se ha sincronizado. */
    suspend fun items(catalogo: String): List<CatalogoItem> {
        memoria[catalogo]?.let { return it }
        val leidos = almacen.items(catalogo)
        memoria[catalogo] = leidos
        return leidos
    }

    /**
     * Revisa las versiones y baja lo que cambió. Nunca lanza: si no se puede, avisa con [ResultadoSync.NoDisponible] y la
     * pantalla sigue con lo que ya tiene. Varias pantallas pueden pedirlo a la vez; se atienden una por una, y un mismo
     * catálogo no se vuelve a revisar antes de un minuto (salvo con [forzar], por ejemplo el botón de recargar).
     */
    suspend fun sincronizar(catalogos: Collection<String>, forzar: Boolean = false): ResultadoSync = turno.withLock {
        val pedidos = catalogos.distinct()
        val ahoraMs = ahora()
        val porRevisar = if (forzar) pedidos else pedidos.filter { ahoraMs - (revisadoEn[it] ?: 0L) >= REVISION_MINIMA_MS }
        if (porRevisar.isEmpty()) return@withLock ResultadoSync.SinCambios

        if (!forzar && ahoraMs < noSoportadoHasta) {
            return@withLock ResultadoSync.NoDisponible("El servidor aún no tiene catálogos maestros.", servidorSinCatalogos = true)
        }

        val remotas = when (val respuesta = fuente.versiones()) {
            is RespuestaCatalogos.Ok -> respuesta.valor
            RespuestaCatalogos.NoSoportado -> {
                noSoportadoHasta = ahoraMs + REINTENTO_SERVIDOR_ANTERIOR_MS
                return@withLock ResultadoSync.NoDisponible("El servidor aún no tiene catálogos maestros.", servidorSinCatalogos = true)
            }
            is RespuestaCatalogos.Falla -> return@withLock ResultadoSync.NoDisponible(respuesta.motivo, servidorSinCatalogos = false)
        }
        noSoportadoHasta = 0L

        val locales = almacen.versiones()
        // Un catálogo que el servidor no pudo leer no aparece en `remotas`: se conserva el que ya está en la tableta.
        val distintos = porRevisar.filter { nombre -> remotas[nombre]?.let { it != locales[nombre] } == true }
        if (distintos.isEmpty()) {
            porRevisar.forEach { revisadoEn[it] = ahoraMs }
            return@withLock ResultadoSync.SinCambios
        }

        val descargados = when (val respuesta = fuente.descargar(distintos)) {
            is RespuestaCatalogos.Ok -> respuesta.valor
            RespuestaCatalogos.NoSoportado -> {
                noSoportadoHasta = ahoraMs + REINTENTO_SERVIDOR_ANTERIOR_MS
                return@withLock ResultadoSync.NoDisponible("El servidor aún no tiene catálogos maestros.", servidorSinCatalogos = true)
            }
            is RespuestaCatalogos.Falla -> return@withLock ResultadoSync.NoDisponible(respuesta.motivo, servidorSinCatalogos = false)
        }

        val cambiados = mutableSetOf<String>()
        for (catalogo in descargados) {
            // Un catálogo que de pronto llega vacío es una respuesta anómala, no un cambio real: no borra lo que sí servía.
            if (catalogo.items.isEmpty() && items(catalogo.nombre).isNotEmpty()) {
                advertir("El catálogo ${catalogo.nombre} llegó vacío; se conserva el que ya estaba guardado.")
                continue
            }
            almacen.reemplazar(catalogo.nombre, catalogo.items, catalogo.version)
            memoria.remove(catalogo.nombre)
            cambiados += catalogo.nombre
        }

        // Solo se da por revisado lo que sí quedó al día; lo que llegó vacío se vuelve a intentar en la próxima apertura.
        porRevisar.filter { it !in distintos || it in cambiados }.forEach { revisadoEn[it] = ahoraMs }
        if (cambiados.isEmpty()) ResultadoSync.SinCambios else ResultadoSync.Actualizado(cambiados)
    }

    companion object {
        /** Un catálogo ya revisado no se vuelve a preguntar antes de este tiempo. */
        const val REVISION_MINIMA_MS = 60_000L

        /** Con un servidor sin catálogos maestros no se insiste en cada pantalla; se reintenta pasado este tiempo. */
        const val REINTENTO_SERVIDOR_ANTERIOR_MS = 10 * 60_000L

        const val CLIENTES = "clientes"
        const val LINEAS = "lineas"
        const val TIPOS_CAJA = "tipos_caja"
        const val BODEGAS_CASETA = "bodegas_caseta"
        const val PAISES_CASETA = "paises_caseta"
        const val PAISES = "paises"
        const val UNIDADES_COMERCIALES = "unidades_comerciales"
        const val ENTRADA_BODEGAS = "entrada_bodegas"
        const val ENTRADA_HAZMAT = "entrada_hazmat"
        const val ENTRADA_MANEJOS = "entrada_manejos"
        const val ENTRADA_RECIBIDORES = "entrada_recibidores"
        const val ENTRADA_UBICACIONES = "entrada_ubicaciones"
        const val ENTRADA_CLASES = "entrada_clases"

        /** Marca en `extra` de un cliente que ve Caseta (el resto solo lo ve Entrada de bodega). */
        const val CLIENTE_DE_CASETA = "C"

        val CASETA = listOf(CLIENTES, LINEAS, TIPOS_CAJA, BODEGAS_CASETA, PAISES_CASETA)
        val ENTRADA = listOf(
            CLIENTES, LINEAS, ENTRADA_BODEGAS, ENTRADA_HAZMAT, ENTRADA_MANEJOS,
            ENTRADA_RECIBIDORES, ENTRADA_UBICACIONES, ENTRADA_CLASES
        )
        val REVISION = listOf(PAISES, UNIDADES_COMERCIALES)
    }
}

class CatalogoFuenteApi(private val api: PMMovilApi) : CatalogoFuente {
    override suspend fun versiones(): RespuestaCatalogos<Map<String, String>> = llamar {
        val respuesta = api.getCatalogosVersiones()
        when {
            respuesta.code() == 404 || respuesta.code() == 405 -> RespuestaCatalogos.NoSoportado
            respuesta.isSuccessful && respuesta.body()?.success == true ->
                RespuestaCatalogos.Ok(respuesta.body()?.versiones.orEmpty())
            else -> RespuestaCatalogos.Falla(respuesta.body()?.message ?: "HTTP ${respuesta.code()}")
        }
    }

    override suspend fun descargar(nombres: List<String>): RespuestaCatalogos<List<CatalogoDescargado>> = llamar {
        val respuesta = api.getCatalogos(nombres.joinToString(","))
        when {
            respuesta.code() == 404 || respuesta.code() == 405 -> RespuestaCatalogos.NoSoportado
            respuesta.isSuccessful && respuesta.body()?.success == true -> RespuestaCatalogos.Ok(
                respuesta.body()?.catalogos.orEmpty().mapNotNull { dto ->
                    val nombre = dto.nombre?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                    CatalogoDescargado(
                        nombre = nombre,
                        version = dto.version.orEmpty(),
                        items = dto.items.orEmpty().map {
                            CatalogoItem(id = it.id.orEmpty(), nombre = it.nombre.orEmpty(), extra = it.extra.orEmpty())
                        }
                    )
                }
            )
            else -> RespuestaCatalogos.Falla(respuesta.body()?.message ?: "HTTP ${respuesta.code()}")
        }
    }

    private suspend fun <T> llamar(bloque: suspend () -> RespuestaCatalogos<T>): RespuestaCatalogos<T> =
        withContext(Dispatchers.IO) {
            try {
                bloque()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RespuestaCatalogos.Falla(e.message ?: e.javaClass.simpleName)
            }
        }
}

class CatalogoAlmacenRoom(private val dao: CatalogoDao) : CatalogoAlmacen {
    override suspend fun versiones(): Map<String, String> = dao.versiones().associate { it.catalogo to it.version }

    override suspend fun items(catalogo: String): List<CatalogoItem> =
        dao.items(catalogo).map { CatalogoItem(id = it.id, nombre = it.nombre, extra = it.extra) }

    override suspend fun reemplazar(catalogo: String, items: List<CatalogoItem>, version: String) {
        // El id es la llave: si el servidor repitiera uno, queda el primero en lugar de tumbar toda la sincronización.
        val unicos = items.distinctBy { it.id }
        dao.reemplazar(
            catalogo,
            unicos.mapIndexed { indice, item ->
                CatalogoItemEntity(catalogo = catalogo, id = item.id, nombre = item.nombre, extra = item.extra, orden = indice)
            },
            CatalogoVersionEntity(catalogo = catalogo, version = version, sincronizadoMs = System.currentTimeMillis())
        )
    }
}
