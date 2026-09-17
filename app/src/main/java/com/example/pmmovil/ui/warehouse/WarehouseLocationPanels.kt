package com.example.pmmovil.ui.warehouse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.ClaseOccupancy
import com.example.pmmovil.data.model.LocationOccupancy
import com.example.pmmovil.data.model.OccupancyEntry
import com.example.pmmovil.data.model.WarehouseLayout
import com.example.pmmovil.data.model.WarehouseZone
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Panel de detalle/vacio compartido entre WarehouseMapScreen (cajon lateral,
 * sin accion de asignar) y WarehouseMapPickerSheet (tarjeta flotante, con
 * accion de asignar). El contenedor (forma, elevacion, tamano, posicion) lo
 * decide cada pantalla via `shape`/`modifier`; este composable solo resuelve
 * el contenido.
 */

internal val ConfirmYellow = Color(0xFFFACC15)
internal val ConfirmInk = Color(0xFF3A2E00)

internal data class LocationAssignAction(
    val enabled: Boolean,
    val label: String,
    val onClick: () -> Unit
)

internal data class WarehouseMapStats(
    val totalZones: Int,
    val occupiedZones: Int,
    val freeZones: Int,
    val totalBultos: Int,
    val directasSinEtiqueta: Int
)

internal fun buildStats(
    layout: WarehouseLayout?,
    occupancy: Map<String, LocationOccupancy>
): WarehouseMapStats {
    val total = layout?.zones?.size ?: 0
    val occupied = occupancy.keys.count { key ->
        layout?.zones?.any { it.id.equals(key, ignoreCase = true) } ?: false
    }
    return WarehouseMapStats(
        totalZones = total,
        occupiedZones = occupied,
        freeZones = (total - occupied).coerceAtLeast(0),
        totalBultos = occupancy.values.sumOf { it.bultos },
        directasSinEtiqueta = occupancy.values.sumOf { it.directasSinEtiqueta }
    )
}

/** Boton circular translucido para headers con degradado (compartido entre pantallas). */
@Composable
internal fun RoundIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.size(38.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.14f)
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

/** Pastilla "en vivo" del header con el estado real de refresco (compartida entre pantallas). */
@Composable
internal fun LiveStatusPill(isRefreshing: Boolean, lastRefreshMillis: Long) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(Color(0xFF4ADE80), CircleShape))
            Spacer(Modifier.width(6.dp))
            if (isRefreshing) {
                Text("Actualizando…", color = Color(0xFFDCE7FB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            } else {
                Text("En vivo · ", color = Color(0xFFDCE7FB), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    text = formatRefreshTime(lastRefreshMillis),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }
    }
}

internal fun formatRefreshTime(millis: Long): String {
    if (millis <= 0L) return "pendiente"
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
}

@Composable
internal fun LocationDetailPanel(
    zone: WarehouseZone?,
    occupancy: LocationOccupancy?,
    claseIndex: Map<String, ClaseModelSpec>,
    stats: WarehouseMapStats,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    emptyTitle: String = "Selecciona una ubicación",
    assignAction: LocationAssignAction? = null,
    recommendedFreeZones: List<WarehouseZone> = emptyList(),
    onZoneShortcut: (WarehouseZone) -> Unit = {},
    onEntryClick: ((String) -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shadowElevation = 12.dp,
        shape = shape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (zone == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = PmmBlueDeep)
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = emptyTitle,
                            color = PmmTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = "${stats.occupiedZones} ocupadas, ${stats.freeZones} libres",
                            color = PmmTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (recommendedFreeZones.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "UBICACIONES LIBRES RECOMENDADAS",
                        color = PmmTextMuted,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        recommendedFreeZones.forEach { freeZone ->
                            RecommendedZoneRow(zone = freeZone, onClick = { onZoneShortcut(freeZone) })
                        }
                    }
                }
                return@Column
            }

            val status = occupancy?.status ?: zone.status ?: "libre"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(zoneStrokeColor(zone.fill, status), CircleShape)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = zone.label.ifBlank { zone.id },
                        color = PmmTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = zoneSubtitle(zone, occupancy),
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(color = zoneFillColor(zone.fill, status), shape = RoundedCornerShape(8.dp)) {
                    Text(
                        text = statusLabel(status),
                        color = statusTextColor(status),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar detalle", tint = PmmTextSecondary)
                }
            }

            Spacer(Modifier.height(10.dp))
            if (occupancy == null) {
                Text(
                    text = "Disponible para asignar mercancía.",
                    color = PmmGreenDark,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = occupancyHeadline(occupancy),
                    color = if (occupancy.confidence == "mixta") PmmAmberDark else PmmTextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium
                )
                occupancyConfidenceMessage(occupancy)?.let { message ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = message,
                        color = if (occupancy.confidence == "directa") PmmTextSecondary else PmmAmberDark,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (occupancy.clases.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ClaseBreakdownRow(clases = occupancy.clases, claseIndex = claseIndex)
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    occupancy.entradas.take(4).forEach { entry ->
                        OccupancyEntryCard(
                            entry = entry,
                            onClick = onEntryClick?.let { handler -> { handler(entry.creferencia) } }
                        )
                    }
                }
                if (occupancy.entradasOmitidas > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "+ ${occupancy.entradasOmitidas} entradas más",
                        color = PmmTextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (assignAction != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = assignAction.onClick,
                    enabled = assignAction.enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConfirmYellow,
                        contentColor = ConfirmInk
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(assignAction.label, fontSize = 14.5.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ClaseBreakdownRow(clases: List<ClaseOccupancy>, claseIndex: Map<String, ClaseModelSpec>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        clases.take(4).forEach { co ->
            val color = claseColor(co.clase, claseIndex)
            Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(8.dp)) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).background(color, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "${shortClase(co.clase)} ${co.bultos}",
                        color = PmmTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun OccupancyEntryCard(entry: OccupancyEntry, onClick: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Inventory, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.creferencia,
                    color = PmmTextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOf(entry.tipoOperacion, entry.cliente, entry.descripcion)
                    .filter { it.isNotBlank() }
                    .joinToString(" - ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = PmmTextSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = listOf(
                        "Línea ${entry.linea}",
                        "${entry.bultos} bulto(s)",
                        entry.clase.takeIf { it.isNotBlank() }?.let(::shortClase).orEmpty(),
                        entryStatusLabel(entry.estado)
                    ).filter { it.isNotBlank() }.joinToString(" - "),
                    color = PmmTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClick != null) {
                Spacer(Modifier.width(6.dp))
                Text("›", color = PmmTextMuted, fontSize = 17.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RecommendedZoneRow(zone: WarehouseZone, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(9.dp).background(Color(0xFF22C55E), CircleShape))
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(zone.label.ifBlank { zone.id }, color = PmmTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Black)
                zone.section?.takeIf { it.isNotBlank() }?.let {
                    Text("Sección $it", color = PmmTextSecondary, fontSize = 11.sp)
                }
            }
            Text("Elegir", color = PmmBlueDeep, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun zoneSubtitle(zone: WarehouseZone, occupancy: LocationOccupancy?): String {
    val parts = mutableListOf<String>()
    zone.type.takeIf { it.isNotBlank() }?.let { parts.add(it.replaceFirstChar(Char::uppercase)) }
    zone.section?.takeIf { it.isNotBlank() }?.let { parts.add("Sección $it") }
    occupancy?.let {
        val clase = it.clasePrincipal.takeIf(String::isNotBlank)?.let(::shortClase)
        parts.add(listOfNotNull("${it.bultos} bulto(s)", clase).joinToString(" · "))
        if (it.directasSinEtiqueta > 0) {
            parts.add("${it.directasSinEtiqueta} directa(s) sin etiqueta")
        }
    }
    return parts.joinToString(" - ")
}

private fun occupancyHeadline(occupancy: LocationOccupancy): String {
    val clase = occupancy.clasePrincipal.takeIf { it.isNotBlank() }?.let { " · ${shortClase(it)}" }.orEmpty()
    return "${occupancy.bultos} bulto(s)$clase en ${occupancy.entradasCount} entrada(s)"
}

private fun occupancyConfidenceMessage(occupancy: LocationOccupancy): String? = when (occupancy.confidence) {
    "directa" -> "Ocupada por directa sin etiqueta."
    "mixta" -> "Incluye etiquetadas pendientes y directas sin etiqueta."
    "sin_etiqueta" -> "Hay mercancía sin etiqueta; conviene confirmar físicamente."
    else -> null
}
