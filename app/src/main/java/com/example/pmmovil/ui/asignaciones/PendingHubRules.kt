package com.example.pmmovil.ui.asignaciones

import com.example.pmmovil.data.model.EntradaPendienteDto
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Las reglas del hub de Revisión: qué se muestra como pendiente, cómo se busca entre ellos sin ir al servidor y cómo se
 * cuenta el avance. Son puras (sin Android ni red) para poder probarlas; la búsqueda en toda la base la hace el servidor.
 */

/** Con menos de esto no se busca en todas las entradas: coincidiría con demasiadas. Igual que el servidor. */
const val SEARCH_MIN_CHARS = 3

/** Lo que se espera tras la última tecla antes de preguntar al servidor, para no mandar una consulta por letra. */
const val SEARCH_DEBOUNCE_MS = 120L

/** Cuántos resultados se piden: el servidor devuelve ocho, no cientos. */
const val SEARCH_LIMIT = 8

/** Quita acentos, pasa a mayúsculas y deja solo letras y dígitos separados por un espacio. */
fun normalizeForSearch(text: String?): String {
    if (text.isNullOrBlank()) return ""
    val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
    val builder = StringBuilder(decomposed.length)
    var pendingSpace = false
    for (char in decomposed) {
        if (Character.getType(char) == Character.NON_SPACING_MARK.toInt()) continue
        if (char.isLetterOrDigit()) {
            if (pendingSpace && builder.isNotEmpty()) builder.append(' ')
            pendingSpace = false
            builder.append(char.uppercaseChar())
        } else {
            pendingSpace = true
        }
    }
    return builder.toString()
}

fun searchTokens(query: String?): List<String> =
    normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }

/** ¿Alcanza la consulta para buscar en todas las entradas? Cuenta caracteres útiles, sin espacios. */
fun isSearchable(query: String?): Boolean = searchTokens(query).sumOf { it.length } >= SEARCH_MIN_CHARS

/**
 * ¿Coincide una entrada de «mis pendientes» con lo escrito? Cada parte de la consulta debe estar en la referencia, el
 * cliente, el supervisor o la nota. Es una búsqueda simple a propósito: son unas cuantas entradas ya en la tableta, así
 * que responde al instante; la tolerante a errores corre en el servidor sobre todas las demás.
 */
fun matchesPendingQuery(entry: EntradaPendienteDto, query: String?): Boolean {
    val tokens = searchTokens(query)
    if (tokens.isEmpty()) return true
    val haystack = normalizeForSearch(
        listOf(
            entry.crefAgrupada,
            entry.nombreCliente,
            entry.clienteId,
            entry.asignSupervisor,
            entry.asignNota
        ).joinToString(" ") { it.orEmpty() }
    )
    return tokens.all { haystack.contains(it) }
}

/** Una entrada terminada no es trabajo pendiente: deja de ocupar la lista (se encuentra buscándola). */
fun isPendingWork(entry: EntradaPendienteDto): Boolean =
    calculateEntryStatus(entry).category != EntryCategory.COMPLETED

/** Lo que el revisador tiene por hacer, ya separado como se le muestra. */
data class PendingHub(
    /** Las que le asignó un supervisor, por prioridad. */
    val assigned: List<EntradaPendienteDto>,
    /** Las que abrió y dejó sin terminar: primero las que siguen en curso y luego las pausadas. */
    val open: List<EntradaPendienteDto>
) {
    val total: Int get() = assigned.size + open.size
    val inProgressCount: Int get() = (assigned + open).count { category(it) == EntryCategory.IN_PROGRESS }
    val pausedCount: Int get() = (assigned + open).count { category(it) == EntryCategory.PAUSED }
    val isEmpty: Boolean get() = total == 0

    private fun category(entry: EntradaPendienteDto) = calculateEntryStatus(entry).category
}

fun buildPendingHub(entries: List<EntradaPendienteDto>): PendingHub {
    val pending = entries.filter(::isPendingWork)
    val (assigned, open) = pending.partition { it.esAsignada == true }
    return PendingHub(
        assigned = assigned.sortedWith(
            compareByDescending<EntradaPendienteDto> { it.asignPrioridad ?: -1 }
                .thenByDescending { it.asignFecha.orEmpty() }
        ),
        open = open.sortedWith(
            compareBy<EntradaPendienteDto> { openOrder(it) }
                .thenByDescending { activityTimestamp(it).orEmpty() }
        )
    )
}

private fun openOrder(entry: EntradaPendienteDto): Int = when (calculateEntryStatus(entry).category) {
    EntryCategory.IN_PROGRESS -> 0
    EntryCategory.PAUSED -> 1
    else -> 2
}

/**
 * El avance que ve el revisador. Con partidas se cuenta por partidas revisadas: con una sola factura de 40 partidas, el
 * avance por facturas completas saltaba de 0 % a 100 % y no decía nada. Sin partidas (aún no se cargan) cae a facturas.
 */
fun progressPercent(entry: EntradaPendienteDto): Int {
    if (entry.totalPartidas > 0) {
        return (entry.partidasRevisadas.coerceIn(0, entry.totalPartidas) * 100f / entry.totalPartidas)
            .let { Math.round(it) }
            .coerceIn(0, 100)
    }
    return calculateEntryStatus(entry).progressPercent
}

/** ¿El avance está contado por partidas? La tarjeta lo dice para que no se confunda con el de facturas. */
fun progressIsByParts(entry: EntradaPendienteDto): Boolean = entry.totalPartidas > 0

/** El momento de la última actividad de la revisión: el fin si está pausada, el inicio si sigue en curso. */
fun activityTimestamp(entry: EntradaPendienteDto): String? =
    entry.finRevision?.takeIf { it.isNotBlank() } ?: entry.inicioRevision?.takeIf { it.isNotBlank() }

private val serverFormats = listOf(
    DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
    DateTimeFormatter.ISO_LOCAL_DATE_TIME
)

/** Lee la hora tal como la manda el servidor (sin zona). `null` si no se reconoce. */
fun parseServerDateTime(raw: String?): LocalDateTime? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    for (format in serverFormats) {
        try {
            return LocalDateTime.parse(text, format)
        } catch (_: DateTimeParseException) {
            // Se prueba el siguiente formato.
        }
    }
    return null
}

/**
 * Cuándo fue la última actividad, sin convertir de zona (el servidor no manda zona): hoy solo la hora, otro día
 * «3 nov 14:20». Así nunca dice «hace 5 horas» por una diferencia de reloj entre el servidor y la tableta.
 */
fun formatActivity(raw: String?, today: LocalDateTime, locale: Locale = Locale.getDefault()): String? {
    val moment = parseServerDateTime(raw) ?: return null
    val pattern = if (moment.toLocalDate() == today.toLocalDate()) "HH:mm" else "d MMM HH:mm"
    return DateTimeFormatter.ofPattern(pattern, locale).format(moment).replace(".", "")
}

/**
 * Los tramos de [text] que coinciden con las partes de la búsqueda, para resaltarlos. No distingue acentos ni
 * mayúsculas (buscar «valvulas» marca «Válvulas»), y devuelve los índices del texto ORIGINAL, ya unidos si se tocan.
 */
fun matchRanges(text: String, tokens: List<String>): List<IntRange> {
    if (text.isEmpty() || tokens.isEmpty()) return emptyList()

    // Cada letra normalizada recuerda de qué posición del texto original salió (una «ß» da dos, un acento no da ninguna).
    val normalized = StringBuilder(text.length)
    val origin = ArrayList<Int>(text.length)
    text.forEachIndexed { index, char ->
        Normalizer.normalize(char.toString(), Normalizer.Form.NFD)
            .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }
            .uppercase()
            .forEach {
                normalized.append(it)
                origin.add(index)
            }
    }

    val marked = BooleanArray(text.length)
    for (token in tokens) {
        if (token.length < 2) continue
        var from = 0
        while (true) {
            val at = normalized.indexOf(token, from)
            if (at < 0) break
            for (k in at until at + token.length) marked[origin[k]] = true
            from = at + token.length
        }
    }

    val ranges = mutableListOf<IntRange>()
    var start = -1
    for (i in marked.indices) {
        if (marked[i] && start < 0) start = i
        if (!marked[i] && start >= 0) {
            ranges.add(start until i)
            start = -1
        }
    }
    if (start >= 0) ranges.add(start until marked.size)
    return ranges
}
