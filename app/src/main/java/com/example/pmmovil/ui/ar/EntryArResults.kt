package com.example.pmmovil.ui.ar

import com.example.pmmovil.data.entryar.ArItemKind
import com.example.pmmovil.data.model.EntryArCardDto
import com.example.pmmovil.data.model.TraceCompactItemDto

/** Cómo salió una de las dos consultas del AR (la de entradas y la de cajas y relaciones). */
enum class ArFetchOutcome {
    /** No había QR de esa clase a la vista: no se consultó y sus tarjetas no se tocan. */
    NotAsked,

    /** Respuesta del servidor. Lo que no vino se marca como inexistente. */
    Fresh,

    /** El servidor no contestó: lo que haya son los últimos datos vistos, quizá ninguno. */
    Offline
}

/** Lo que dejó una consulta del AR: los datos por clave y cómo salió. */
data class ArLookup<T>(val values: Map<String, T> = emptyMap(), val outcome: ArFetchOutcome = ArFetchOutcome.NotAsked)

/**
 * Pone en cada QR a la vista lo que trajeron las dos consultas. Cada clase de QR solo recibe lo suyo: una entrada nunca
 * se pinta con la ficha de una caja ni al revés, y si una de las dos consultas no se hizo, esos QR quedan como estaban.
 */
internal fun mergeArItems(
    items: List<EntryArOverlayItem>,
    entries: ArLookup<EntryArCardDto>,
    compact: ArLookup<TraceCompactItemDto>
): List<EntryArOverlayItem> = items.map { item ->
    when (item.kind) {
        ArItemKind.Entry -> when (entries.outcome) {
            ArFetchOutcome.NotAsked -> item
            else -> item.copy(
                card = entries.values[item.code.reference] ?: EntryArCardDto(reference = item.code.reference, exists = false),
                fromCache = entries.outcome == ArFetchOutcome.Offline && entries.values.containsKey(item.code.reference)
            )
        }

        ArItemKind.Caja, ArItemKind.Relacion -> when (compact.outcome) {
            ArFetchOutcome.NotAsked -> item
            else -> item.copy(
                compact = compact.values[item.code.normalized],
                fromCache = compact.outcome == ArFetchOutcome.Offline && compact.values.containsKey(item.code.normalized)
            )
        }
    }
}

/** El aviso de falta de red, o `null` si las dos consultas salieron bien. Distingue si hay algo guardado que mostrar. */
internal fun arOfflineMessage(entries: ArLookup<EntryArCardDto>, compact: ArLookup<TraceCompactItemDto>): String? {
    val offline = entries.outcome == ArFetchOutcome.Offline || compact.outcome == ArFetchOutcome.Offline
    if (!offline) return null
    val anyCached = (entries.outcome == ArFetchOutcome.Offline && entries.values.isNotEmpty()) ||
        (compact.outcome == ArFetchOutcome.Offline && compact.values.isNotEmpty())
    val noun = if (compact.outcome == ArFetchOutcome.NotAsked) "estas entradas" else "estos códigos"
    return if (anyCached) {
        "Sin conexión. Se muestran los últimos datos consultados."
    } else {
        "Sin conexión. No hay datos guardados para $noun."
    }
}
