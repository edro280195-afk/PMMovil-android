package com.example.pmmovil.ui.warehouse

import androidx.compose.ui.graphics.Color
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.DEFAULT_CLASE_MODELS
import com.example.pmmovil.data.model.FALLBACK_CLASE_MODEL
import com.example.pmmovil.data.model.LocationOccupancy
import java.util.Locale

/**
 * Estilo compartido del mapa de bodega (paridad con la web).
 * Los colores replican warehouse-map-canvas / warehouse-map-3d de Angular:
 * el status manda; si no hay status se usa el fill pastel de la zona y su
 * stroke fuerte derivado (PRESET_STROKES o darken).
 */

internal val MAP_SELECTED_STROKE = Color(0xFF003DA5)
internal const val MAP_DEFAULT_FILL = "#f1f5f9"

internal val MAP_PRESET_STROKES = mapOf(
    "#f1f5f9" to "#94a3b8",
    "#dbeafe" to "#2563eb",
    "#d1fae5" to "#16a34a",
    "#fee2e2" to "#e11d48",
    "#fef9c3" to "#ca8a04",
    "#f3e8ff" to "#7e22ce",
    "#ffedd5" to "#ea580c"
)

internal val MAP_STATUS_FILL = mapOf(
    "libre" to "#f0fdf4", "ocupada" to "#fff1f2", "parcial" to "#fefce8", "bloqueada" to "#f1f5f9"
)

internal val MAP_STATUS_STROKE = mapOf(
    "libre" to "#22c55e", "ocupada" to "#f43f5e", "parcial" to "#eab308", "bloqueada" to "#94a3b8"
)

internal fun parseHexColor(hex: String?, fallback: Color = Color(0xFFF1F5F9)): Color {
    if (hex.isNullOrBlank()) return fallback
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
}

internal fun zoneFillColor(fill: String?, status: String?): Color {
    if (status != null) MAP_STATUS_FILL[status]?.let { return parseHexColor(it) }
    return parseHexColor(fill ?: MAP_DEFAULT_FILL)
}

internal fun zoneStrokeColor(fill: String?, status: String?): Color {
    if (status != null) MAP_STATUS_STROKE[status]?.let { return parseHexColor(it) }
    val f = fill ?: MAP_DEFAULT_FILL
    MAP_PRESET_STROKES[f]?.let { return parseHexColor(it) }
    return darkenColor(parseHexColor(f))
}

internal fun darkenColor(c: Color, factor: Float = 0.55f): Color =
    Color(red = c.red * factor, green = c.green * factor, blue = c.blue * factor, alpha = c.alpha)

// --- Clase -> Modelo de carga ---

internal fun normalizeClaseId(clase: String?): String =
    clase.orEmpty().trim().uppercase(Locale.ROOT)

internal fun claseModelIndex(items: List<ClaseModelSpec>): Map<String, ClaseModelSpec> {
    val source = items.ifEmpty { DEFAULT_CLASE_MODELS }
    return source.associateBy { normalizeClaseId(it.claseId) }
}

internal fun resolveClaseModel(
    clase: String?,
    index: Map<String, ClaseModelSpec>
): ClaseModelSpec {
    val clean = clase.orEmpty().trim()
    return index[normalizeClaseId(clean)] ?: FALLBACK_CLASE_MODEL.copy(claseId = clean)
}

internal fun claseColor(clase: String?, index: Map<String, ClaseModelSpec>): Color =
    parseHexColor(resolveClaseModel(clase, index).color, fallback = Color(0xFF9CA3AF))

internal fun shortClase(clase: String): String =
    clase.substringBefore("(").trim().ifBlank { clase }

internal fun dominantClase(occupancy: LocationOccupancy?): String =
    occupancy?.clasePrincipal?.takeIf { it.isNotBlank() }
        ?: occupancy?.clases?.maxByOrNull { it.bultos }?.clase.orEmpty()

// --- Etiquetas de estatus (fuente unica: canvas + paneles de detalle) ---

internal fun statusLabel(status: String?): String = when (status) {
    "ocupada" -> "Ocupada"
    "parcial" -> "Parcial"
    "bloqueada" -> "Bloqueada"
    else -> "Libre"
}

internal fun entryStatusLabel(status: String): String = when (status) {
    "parcial" -> "Parcial"
    "directa" -> "Directa sin etiqueta"
    "sin_etiqueta" -> "Sin etiqueta"
    else -> "En bodega"
}

/** Color de texto de alto contraste por estatus (para chips/labels sobre fondo claro). */
internal fun statusTextColor(status: String?): Color = when (status) {
    "ocupada" -> Color(0xFFBE123C)
    "parcial" -> Color(0xFFA16207)
    "bloqueada" -> Color(0xFF475569)
    else -> Color(0xFF15803D)
}
