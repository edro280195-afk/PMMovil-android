package com.example.pmmovil.ui.salidacaseta

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.pmmovil.data.model.SalidaCarril
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmGreen

/** Identidad visual de cada carril: el color le dice al guardia, de un vistazo, qué clase de salida es. */
internal data class CarrilStyle(
    val color: Color,
    val soft: Color,
    val ink: Color,
    val chip: String,
    val encabezado: String,
    val icon: ImageVector,
    val accion: String
)

internal val ColorProceso = Color(0xFF64748B)
internal val ColorCerrar = Color(0xFF7C3AED)

internal fun SalidaCarril.style(): CarrilStyle = when (this) {
    SalidaCarril.LISTA -> CarrilStyle(
        color = PmmGreen,
        soft = Color(0xFFDCFCE7),
        ink = Color(0xFF14532D),
        chip = "Listas",
        encabezado = "LISTAS CON RELACIÓN",
        icon = Icons.AutoMirrored.Filled.Logout,
        accion = "Confirmar salida"
    )
    SalidaCarril.MANUAL -> CarrilStyle(
        color = PmmAmber,
        soft = Color(0xFFFEF3C7),
        ink = Color(0xFF78350F),
        chip = "Sin relación",
        encabezado = "SIN RELACIÓN",
        icon = Icons.Default.PanTool,
        accion = "Registrar salida manual"
    )
    SalidaCarril.PROCESO -> CarrilStyle(
        color = ColorProceso,
        soft = Color(0xFFE2E8F0),
        ink = Color(0xFF1E293B),
        chip = "En proceso",
        encabezado = "EN PROCESO",
        icon = Icons.Default.Settings,
        accion = "No disponible"
    )
    SalidaCarril.CERRAR -> CarrilStyle(
        color = ColorCerrar,
        soft = Color(0xFFEDE9FE),
        ink = Color(0xFF4C1D95),
        chip = "Por cerrar",
        encabezado = "POR CERRAR",
        icon = Icons.Default.Flag,
        accion = "Cerrar registro de patio"
    )
}

internal val IconoVerificado: ImageVector get() = Icons.Default.Verified

/** 161 → "2 h 41 min", 4300 → "2 d 23 h". */
internal fun formatoMinutos(minutos: Int): String = when {
    minutos >= 1440 -> "${minutos / 1440} d ${(minutos % 1440) / 60} h"
    minutos >= 60 -> "${minutos / 60} h ${minutos % 60} min"
    else -> "$minutos min"
}

/** "2026-08-07T11:37:40" → "07/08 11:37". Devuelve "—" si no hay fecha. */
internal fun formatoFechaCorta(iso: String?): String {
    if (iso.isNullOrBlank() || iso.length < 16) return "—"
    return runCatching {
        val dia = iso.substring(8, 10)
        val mes = iso.substring(5, 7)
        val hora = iso.substring(11, 16)
        "$dia/$mes $hora"
    }.getOrDefault("—")
}

/** "531818" → "531818"; vacío → "Sin caja" (tractocamión) */
internal fun etiquetaCaja(numCaja: String): String = numCaja.ifBlank { "Sin caja" }

/** Pasos del ciclo de una relación, en orden. El índice es la posición en el indicador. */
internal val PasosRelacion = listOf("EN ESPERA", "CARGANDO", "CARGADA", "AUTORIZADA", "SALIDA")

/** Posición actual dentro de [PasosRelacion]; el bool indica si hay un problema detenido en ese paso. */
internal fun posicionPaso(estatusCarga: String, carril: SalidaCarril): Pair<Int, Boolean> {
    if (carril == SalidaCarril.CERRAR) return 4 to false
    return when (estatusCarga.uppercase()) {
        "" -> 0 to false
        "EN ESPERA" -> 0 to false
        "CARGANDO" -> 1 to false
        "CARGADA" -> 2 to false
        "AUTORIZADA" -> 3 to false
        "SALIDA" -> 4 to false
        "DEMASIADO RAPIDO", "BLOQUEO", "BLOQUEO REIMPRESION" -> 2 to true
        else -> 0 to false
    }
}

/** Misma convención que el panel de métricas: planta '2' = ATLANTA, cualquier otra = IMEX. */
internal fun nombrePlanta(planta: String): String? = when {
    planta.isBlank() -> null
    planta == "2" -> "ATLANTA"
    else -> "IMEX"
}
