package com.example.pmmovil.ui.warehouse

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

private val Backdrop = Color(0xFFEEF2F7)
private val OverlineBlue = Color(0xFF9FC0FF)

/**
 * Selector visual de ubicacion para Entrada. El operador TOCA una ubicacion para
 * previsualizarla y CONFIRMA para asignarla. Solo lectura: la edicion del mapa
 * vive en la web (Configuracion).
 */
@Composable
fun WarehouseMapPickerSheet(
    currentLocation: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: WarehouseMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var selectedId by remember(state.layout) { mutableStateOf(currentLocation) }
    var focusZoneId by remember(state.layout) { mutableStateOf(currentLocation.takeIf { it.isNotBlank() }) }
    var focusToken by remember(state.layout) { mutableStateOf(0) }

    fun selectAndFocus(zoneId: String) {
        selectedId = zoneId
        focusZoneId = zoneId
        focusToken++
    }

    val zones = state.layout?.zones.orEmpty()
    val selectedZone = zones.firstOrNull { it.id == selectedId }
    val selectedOccupancy = state.occupancy[selectedId.trim().uppercase()]
    val hasZones = zones.isNotEmpty()
    val claseIndex = remember(state.claseModels) { claseModelIndex(state.claseModels) }
    val stats = remember(state.layout, state.occupancy) { buildStats(state.layout, state.occupancy) }
    val recommendedFreeZones = remember(zones, state.occupancy) {
        zones.filter { zone ->
            val occ = state.occupancy[zone.id.trim().uppercase()]
            occ == null && (zone.status == null || zone.status == "libre")
        }.take(3)
    }

    val isCurrentLocation = selectedZone?.id?.equals(currentLocation, ignoreCase = true) == true &&
        currentLocation.isNotBlank()
    val isOccupied = selectedOccupancy != null && selectedOccupancy.status != "libre"
    val canConfirm = selectedZone != null && (!isOccupied || isCurrentLocation)
    val assignAction = LocationAssignAction(
        enabled = canConfirm,
        label = if (canConfirm) "Confirmar ubicación" else "Ubicación ocupada",
        onClick = { selectedZone?.let { onSelect(it.id) } }
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Backdrop) {
            Column(modifier = Modifier.fillMaxSize()) {

                PickerHeader(
                    title = state.layout?.name?.ifBlank { "Bodega Maquila" } ?: "Bodega Maquila",
                    isRefreshing = state.isRefreshingOccupancy,
                    lastRefreshMillis = state.lastOccupancyRefreshMillis,
                    onDismiss = onDismiss,
                    onRefresh = { viewModel.refreshOccupancy() }
                )
                AssigningBanner()

                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.isLoading -> LoadingState(Modifier.align(Alignment.Center))

                        state.layout == null -> ErrorState(
                            message = state.error ?: "No se pudo cargar el mapa de bodega.",
                            onRetry = { viewModel.load() },
                            modifier = Modifier.align(Alignment.Center)
                        )

                        !hasZones -> EmptyState(Modifier.align(Alignment.Center))

                        else -> {
                            WarehouseMapCanvas(
                                layout = state.layout!!,
                                selectedZoneId = selectedId,
                                occupancy = state.occupancy,
                                claseModels = state.claseModels,
                                focusZoneId = focusZoneId,
                                focusToken = focusToken,
                                focusInsetBottom = if (isLandscape || selectedId.isBlank()) 0.dp else 380.dp,
                                onZoneTap = { selectAndFocus(it.id) },
                                modifier = Modifier.fillMaxSize()
                            )
                            if (state.occupancy.isNotEmpty() || zones.any { it.status != null }) {
                                LegendCard(modifier = Modifier.align(Alignment.TopStart).padding(16.dp))
                            }

                            state.occupancyError?.let {
                                OccupancyWarning(it, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
                            }

                            if (isLandscape) {
                                LocationDetailPanel(
                                    zone = selectedZone,
                                    occupancy = selectedOccupancy,
                                    claseIndex = claseIndex,
                                    stats = stats,
                                    onDismiss = { selectedId = "" },
                                    shape = RoundedCornerShape(18.dp),
                                    emptyTitle = "Toca una ubicación para elegirla",
                                    assignAction = assignAction,
                                    recommendedFreeZones = recommendedFreeZones,
                                    onZoneShortcut = { zone -> selectAndFocus(zone.id) },
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .widthIn(max = 380.dp)
                                        .heightIn(max = 440.dp)
                                )
                            }
                        }
                    }
                }

                if (!isLandscape && hasZones && state.layout != null) {
                    LocationDetailPanel(
                        zone = selectedZone,
                        occupancy = selectedOccupancy,
                        claseIndex = claseIndex,
                        stats = stats,
                        onDismiss = { selectedId = "" },
                        emptyTitle = "Toca una ubicación para elegirla",
                        assignAction = assignAction,
                        recommendedFreeZones = recommendedFreeZones,
                        onZoneShortcut = { zone -> selectAndFocus(zone.id) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(
    title: String,
    isRefreshing: Boolean,
    lastRefreshMillis: Long,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(
                Brush.verticalGradient(listOf(PmmBlueDeep, Color(0xFF012F82))),
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "MAPA DE BODEGA",
                    color = OverlineBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.2.sp
                )
                Text(
                    title,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
            Spacer(Modifier.width(10.dp))
            LiveStatusPill(isRefreshing = isRefreshing, lastRefreshMillis = lastRefreshMillis)
            Spacer(Modifier.width(8.dp))
            RoundIconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun AssigningBanner() {
    Column {
        Surface(color = Color(0xFFFFFBEB)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color(0xFFFDE68A), shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = "ASIGNANDO",
                        color = Color(0xFFB45309),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Asignando ubicación · Toca una ubicación libre",
                    color = Color(0xFF92400E),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFCE8B2)))
    }
}

@Composable
private fun OccupancyWarning(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFFFBEB).copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309))
            Spacer(Modifier.width(8.dp))
            Text(message, color = Color(0xFF92400E), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun LegendCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            LegendRow(Color(0xFF22C55E), "Libre")
            LegendRow(Color(0xFFF43F5E), "Ocupada")
            LegendRow(Color(0xFFEAB308), "Parcial")
            LegendRow(Color(0xFF94A3B8), "Bloqueada")
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(9.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(7.dp))
        Text(label, color = PmmTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CircularProgressIndicator(color = PmmBlueDeep, strokeWidth = 4.dp)
        Text("Cargando mapa…", color = PmmTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(
            "Trayendo el plano publicado de la bodega.",
            color = PmmTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    StateBlock(
        modifier = modifier,
        icon = { Icon(Icons.Default.LocationOff, contentDescription = null, tint = PmmBlueDeep) },
        iconBg = Color(0xFFEAF1FF),
        title = "Aún no hay ubicaciones",
        body = "El plano se dibuja desde la web, en Configuración → Mapa de bodega."
    )
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(Color(0xFFFEE2E2), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.CloudOff, contentDescription = null, tint = PmmRed)
        }
        Text("No se pudo cargar", color = PmmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(message, color = PmmTextSecondary, fontSize = 13.5.sp)
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep, contentColor = Color.White)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Reintentar", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StateBlock(
    modifier: Modifier,
    icon: @Composable () -> Unit,
    iconBg: Color,
    title: String,
    body: String
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(72.dp).background(iconBg, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Text(title, color = PmmTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(body, color = PmmTextSecondary, fontSize = 13.5.sp)
    }
}
