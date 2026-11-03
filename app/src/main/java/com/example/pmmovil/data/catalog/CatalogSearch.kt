package com.example.pmmovil.data.catalog

import java.text.Normalizer

/** Un renglón de un catálogo maestro guardado en la tableta. [extra] es el dato que un módulo necesita además del nombre. */
data class CatalogoItem(
    val id: String,
    val nombre: String,
    val extra: String = ""
)

object CatalogSearch {
    private val marcas = Regex("\\p{M}+")
    private val espacios = Regex("\\s+")

    /** Mayúsculas y sin acentos: "Niño" y "NINO" son lo mismo al buscar. */
    fun normalize(value: String): String =
        marcas.replace(Normalizer.normalize(value.uppercase(), Normalizer.Form.NFD), "")

    fun tokens(query: String): List<String> =
        normalize(query).trim().split(espacios).filter(String::isNotBlank)
}

/**
 * Buscador en memoria sobre un catálogo. Normaliza cada renglón UNA vez al armarlo (antes se normalizaba todo el catálogo
 * con cada letra tecleada) y encuentra por cualquier parte del código o del nombre; todas las palabras escritas deben
 * aparecer. Primero salen el código exacto, luego los que empiezan con lo escrito y al final el resto, cada grupo en el
 * orden original del catálogo.
 */
class CatalogIndex<T>(
    items: List<T>,
    private val id: (T) -> String,
    private val nombre: (T) -> String
) {
    private class Fila<T>(val item: T, val id: String, val nombre: String, val texto: String)

    private val filas: List<Fila<T>> = items.map {
        val codigo = CatalogSearch.normalize(id(it))
        val descripcion = CatalogSearch.normalize(nombre(it))
        Fila(it, codigo, descripcion, "$codigo $descripcion")
    }

    val size: Int get() = filas.size

    fun search(query: String, limit: Int): List<T> {
        if (limit <= 0) return emptyList()
        val tokens = CatalogSearch.tokens(query)
        if (tokens.isEmpty()) return filas.asSequence().take(limit).map { it.item }.toList()

        val primero = tokens.first()
        val completa = tokens.joinToString(" ")
        return filas.asSequence()
            .filter { fila -> tokens.all { fila.texto.contains(it) } }
            .sortedBy { fila ->
                when {
                    fila.id == completa -> 0
                    fila.id.startsWith(primero) -> 1
                    fila.nombre.startsWith(primero) -> 2
                    else -> 3
                }
            }
            .take(limit)
            .map { it.item }
            .toList()
    }
}
