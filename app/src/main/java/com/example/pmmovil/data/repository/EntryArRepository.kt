package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.EntryArCacheDao
import com.example.pmmovil.data.model.EntryArBatchRequest
import com.example.pmmovil.data.model.EntryArCacheEntity
import com.example.pmmovil.data.model.EntryArCardDto
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntryArRepository @Inject constructor(
    private val api: PMMovilApi,
    private val cacheDao: EntryArCacheDao
) {
    private val gson = Gson()

    /**
     * Resumen de cada referencia, con **caché corta**: lo consultado hace menos de [FRESH_WINDOW_MS] se da por bueno y
     * solo se piden al servidor las referencias que faltan.
     *
     * Hace falta porque la pantalla vuelve a consultar cada vez que un QR reaparece, y un QR se da por ido en cuanto deja
     * de verse un segundo (un reflejo basta). Medido en el Pixel el 22-oct: 16 peticiones en tres minutos por las mismas
     * seis referencias, a 0.7–0.9 s cada una. Con varios equipos en la nave eso es carga real sobre el API.
     */
    suspend fun getCards(references: List<String>): Result<List<EntryArCardDto>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(references.size in 1..MAX_REFERENCES) {
                    "La consulta AR debe incluir de una a seis referencias."
                }
                val wanted = references.distinct()
                val now = System.currentTimeMillis()
                val fresh = cacheDao.getByReferences(wanted)
                    .filter { now - it.fetchedAtMillis in 0..FRESH_WINDOW_MS }
                    .mapNotNull { cached ->
                        runCatching { gson.fromJson(cached.payloadJson, EntryArCardDto::class.java) }.getOrNull()
                    }
                    .associateBy { it.reference }

                val missing = wanted.filterNot { fresh.containsKey(it) }
                if (missing.isEmpty()) return@runCatching wanted.mapNotNull { fresh[it] }

                val response = api.getEntryArCards(EntryArBatchRequest(missing))
                val body = response.body()
                if (!response.isSuccessful || body?.success != true) {
                    throw IllegalStateException(
                        body?.message ?: "No se pudo consultar la información de las entradas."
                    )
                }

                val items = body.items
                cacheDao.upsert(
                    items.map { item ->
                        EntryArCacheEntity(
                            reference = item.reference,
                            payloadJson = gson.toJson(item),
                            fetchedAtMillis = System.currentTimeMillis()
                        )
                    }
                )
                val recibidas = items.associateBy { it.reference }
                wanted.mapNotNull { recibidas[it] ?: fresh[it] }
            }
        }

    suspend fun getCachedCards(references: List<String>): List<EntryArCardDto> =
        withContext(Dispatchers.IO) {
            if (references.isEmpty()) return@withContext emptyList()
            cacheDao.getByReferences(references.distinct())
                .mapNotNull { cached ->
                    runCatching {
                        gson.fromJson(cached.payloadJson, EntryArCardDto::class.java)
                    }.getOrNull()
                }
        }

    private companion object {
        const val MAX_REFERENCES = 6

        /**
         * Cuánto vale una respuesta antes de volver a preguntar. El estado de revisión cambia en minutos, no en segundos,
         * así que 30 s no muestra nada viejo y quita casi todas las peticiones repetidas.
         */
        const val FRESH_WINDOW_MS = 30_000L
    }
}
