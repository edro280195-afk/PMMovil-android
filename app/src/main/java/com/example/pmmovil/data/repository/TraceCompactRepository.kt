package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.data.model.TraceCompactRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo consultado hace poco, en memoria. El AR vuelve a preguntar cada vez que un QR reaparece en cuadro (y un reflejo
 * basta para que «desaparezca»), así que sin esto la misma caja se pediría al servidor decenas de veces por minuto.
 *
 * Es solo memoria: una ficha de caja cambia en minutos y no vale la pena guardarla en el aparato. Sirve también para
 * lo último visto cuando no hay red, mientras la app siga abierta.
 *
 * @param now reloj en milisegundos; se recibe para poder probarlo.
 */
class TraceCompactCache(private val now: () -> Long = System::currentTimeMillis) {
    private data class Entry(val item: TraceCompactItemDto, val fetchedAtMillis: Long)

    private val entries = ConcurrentHashMap<String, Entry>()

    /** Las fichas de [codes] consultadas hace menos de [FRESH_WINDOW_MS]. */
    fun fresh(codes: Collection<String>): Map<String, TraceCompactItemDto> {
        val current = now()
        return codes.mapNotNull { code ->
            entries[code]?.takeIf { current - it.fetchedAtMillis in 0..FRESH_WINDOW_MILLIS }?.let { code to it.item }
        }.toMap()
    }

    /** Lo último que se sepa de [codes], por viejo que sea. */
    fun latest(codes: Collection<String>): Map<String, TraceCompactItemDto> =
        codes.mapNotNull { code -> entries[code]?.let { code to it.item } }.toMap()

    fun put(items: Collection<TraceCompactItemDto>) {
        val current = now()
        items.filter { it.code.isNotBlank() }.forEach { entries[it.code] = Entry(it, current) }
        // Una sesión de AR toca pocos códigos; esto solo evita que la memoria crezca sin límite en un turno largo.
        if (entries.size > MAX_ENTRIES) {
            entries.entries.sortedBy { it.value.fetchedAtMillis }.take(entries.size - MAX_ENTRIES).forEach { entries.remove(it.key) }
        }
    }

    companion object {
        const val FRESH_WINDOW_MILLIS = 30_000L
        const val MAX_ENTRIES = 200
    }
}

@Singleton
class TraceCompactRepository @Inject constructor(
    private val api: PMMovilApi
) {
    private val cache = TraceCompactCache()

    /**
     * La ficha compacta de cada código, por código. Lo consultado hace menos de 30 s no se vuelve a pedir; si todo estaba
     * fresco no hay petición. Un código que el servidor no devuelve queda fuera del resultado.
     */
    suspend fun get(codes: List<String>): Result<Map<String, TraceCompactItemDto>> = withContext(Dispatchers.IO) {
        try {
            val wanted = codes.distinct().take(MAX_CODES)
            require(wanted.isNotEmpty()) { "Falta al menos un código." }
            val fresh = cache.fresh(wanted)
            val missing = wanted.filterNot(fresh::containsKey)
            if (missing.isEmpty()) return@withContext Result.success(fresh)

            val response = api.resolveTraceCompact(TraceCompactRequest(missing))
            val body = response.body()
            if (!response.isSuccessful || body?.success != true) {
                return@withContext Result.failure(IllegalStateException("No se pudo consultar las fichas (${response.code()})."))
            }
            cache.put(body.items)
            Result.success(fresh + body.items.filter { it.code in missing }.associateBy { it.code })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Lo último que se vio de estos códigos, para cuando no hay red. */
    fun latest(codes: List<String>): Map<String, TraceCompactItemDto> = cache.latest(codes.distinct())

    private companion object {
        const val MAX_CODES = 6
    }
}
