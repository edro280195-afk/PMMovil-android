package com.example.pmmovil.ui.warehouse

import android.content.res.Configuration
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.LocationOccupancy
import com.example.pmmovil.data.model.WarehouseLayout
import com.example.pmmovil.data.model.WarehouseZone
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlinx.coroutines.delay

@Composable
fun WarehouseMapScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEntrada: (String) -> Unit,
    viewModel: WarehouseMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var selectedZoneId by remember { mutableStateOf("") }
    var focusZoneId by remember { mutableStateOf<String?>(null) }
    var focusToken by remember { mutableStateOf(0) }
    var isDrawerExpanded by rememberSaveable { mutableStateOf(true) }

    fun selectAndFocus(zoneId: String) {
        selectedZoneId = zoneId
        focusZoneId = zoneId
        focusToken++
    }

    val layout = state.layout
    val zones = layout?.zones.orEmpty()
    val selectedZone = zones.firstOrNull { it.id.equals(selectedZoneId, ignoreCase = true) }
    val selectedOccupancy = state.occupancy[selectedZoneId.trim().uppercase()]
    val claseIndex = remember(state.claseModels) { claseModelIndex(state.claseModels) }
    val searchResults = remember(query, layout, state.occupancy) {
        buildSearchResults(query, layout, state.occupancy)
    }
    val stats = remember(layout, state.occupancy) {
        buildStats(layout, state.occupancy)
    }

    LaunchedEffect(Unit) {
        viewModel.startBodegaEventRefresh()
        while (true) {
            delay(30_000)
            viewModel.refreshOccupancy()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PmmBgPrimary)
            .navigationBarsPadding()
    ) {
        WarehouseMapHeader(
            title = layout?.name?.ifBlank { "Bodega Maquila" } ?: "Bodega Maquila",
            isRefreshing = state.isRefreshingOccupancy,
            lastRefreshMillis = state.lastOccupancyRefreshMillis,
            onNavigateBack = onNavigateBack,
            onRefresh = { viewModel.refreshOccupancy() }
        )

        if (state.isRefreshingOccupancy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = PmmBlueDeep,
                trackColor = Color(0xFFDCE7F8)
            )
        }

        when {
            state.isLoading -> ScreenLoadingState(Modifier.weight(1f))

            layout == null -> ScreenErrorState(
                message = state.error ?: "No se pudo cargar el mapa de bodega.",
                onRetry = { viewModel.load() },
                modifier = Modifier.weight(1f)
            )

            else -> {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val drawerWidth by animateDpAsState(
                    targetValue = if (isDrawerExpanded) 320.dp else 0.dp,
                    label = "warehouseDrawerWidth"
                )

                if (isLandscape) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        MapWithOverlays(
                            layout = layout,
                            selectedZoneId = selectedZoneId,
                            occupancy = state.occupancy,
                            claseModels = state.claseModels,
                            focusZoneId = focusZoneId,
                            focusToken = focusToken,
                            onZoneTap = { selectAndFocus(it.id) },
                            query = query,
                            searchResults = searchResults,
                            onQueryChange = { query = it },
                            onClearQuery = { query = "" },
                            onResultClick = { result -> selectAndFocus(result.zone.id) },
                            stats = stats,
                            occupancyError = state.occupancyError,
                            showStats = true,
                            searchMaxWidth = 380.dp,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        Box(modifier = Modifier.fillMaxHeight()) {
                            Box(
                                modifier = Modifier
                                    .width(drawerWidth)
                                    .fillMaxHeight()
                                    .clipToBounds()
                            ) {
                                LocationDetailPanel(
                                    zone = selectedZone,
                                    occupancy = selectedOccupancy,
                                    claseIndex = claseIndex,
                                    stats = stats,
                                    onDismiss = { selectedZoneId = "" },
                                    onEntryClick = { cref -> onNavigateToEntrada(cref) },
                                    shape = RectangleShape,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            DrawerHandle(
                                isExpanded = isDrawerExpanded,
                                onToggle = { isDrawerExpanded = !isDrawerExpanded },
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = (-14).dp)
                            )
                        }
                    }
                } else {
                    MapWithOverlays(
                        layout = layout,
                        selectedZoneId = selectedZoneId,
                        occupancy = state.occupancy,
                        claseModels = state.claseModels,
                        focusZoneId = focusZoneId,
                        focusToken = focusToken,
                        focusInsetBottom = if (selectedZoneId.isNotBlank()) 440.dp else 0.dp,
                        onZoneTap = { selectAndFocus(it.id) },
                        query = query,
                        searchResults = searchResults,
                        onQueryChange = { query = it },
                        onClearQuery = { query = "" },
                        onResultClick = { result -> selectAndFocus(result.zone.id) },
                        stats = stats,
                        occupancyError = state.occupancyError,
                        showStats = false,
                        searchMaxWidth = 640.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        bottomPanel = {
                            LocationDetailPanel(
                                zone = selectedZone,
                                occupancy = selectedOccupancy,
                                claseIndex = claseIndex,
                                stats = stats,
                                onDismiss = { selectedZoneId = "" },
                                onEntryClick = { cref -> onNavigateToEntrada(cref) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .heightIn(max = 440.dp)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MapWithOverlays(
    layout: WarehouseLayout,
    selectedZoneId: String,
    occupancy: Map<String, LocationOccupancy>,
    claseModels: List<ClaseModelSpec>,
    focusZoneId: String?,
    focusToken: Int,
    focusInsetBottom: Dp = 0.dp,
    onZoneTap: (WarehouseZone) -> Unit,
    query: String,
    searchResults: List<WarehouseSearchResult>,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onResultClick: (WarehouseSearchResult) -> Unit,
    stats: WarehouseMapStats,
    occupancyError: String?,
    showStats: Boolean,
    searchMaxWidth: Dp,
    modifier: Modifier = Modifier,
    bottomPanel: (@Composable BoxScope.() -> Unit)? = null
) {
    Box(modifier = modifier) {
        WarehouseMapCanvas(
            layout = layout,
            selectedZoneId = selectedZoneId,
            occupancy = occupancy,
            claseModels = claseModels,
            focusZoneId = focusZoneId,
            focusToken = focusToken,
            focusInsetBottom = focusInsetBottom,
            onZoneTap = onZoneTap,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp)
                .widthIn(max = searchMaxWidth),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SearchOverlay(
                query = query,
                results = searchResults,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                onResultClick = onResultClick
            )
            occupancyError?.let { OccupancyErrorBanner(it) }
        }

        if (showStats) {
            FloatingStatsCard(
                stats = stats,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            )
        }

        LegendStrip(
            occupancy = occupancy,
            claseModels = claseModels,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp)
        )

        bottomPanel?.invoke(this)
    }
}

@Composable
private fun WarehouseMapHeader(
    title: String,
    isRefreshing: Boolean,
    lastRefreshMillis: Long,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(PmmBlueDeep, Color(0xFF012F82))))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "MAPA DE BODEGA",
                    color = Color(0xFF9DBBEE),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
private fun DrawerHandle(isExpanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .size(width = 22.dp, height = 56.dp)
            .clickable(onClick = onToggle),
        shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp),
        color = Color.White,
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (isExpanded) "›" else "‹",
                color = PmmTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun SearchOverlay(
    query: String,
    results: List<WarehouseSearchResult>,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onResultClick: (WarehouseSearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(13.dp),
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Buscar ubicación, entrada, cliente o mercancía", fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedLeadingIconColor = PmmBlueDeep,
                    unfocusedLeadingIconColor = PmmTextMuted,
                    cursorColor = PmmBlueDeep
                )
            )
        }

        if (query.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(13.dp),
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                if (results.isEmpty()) {
                    Text(
                        text = "Sin resultados",
                        color = PmmTextMuted,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(results, key = { "${it.zone.id}-${it.subtitle}" }) { result ->
                            SearchResultRow(result = result, onClick = { onResultClick(result) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: WarehouseSearchResult, onClick: () -> Unit) {
    val status = result.occupancy?.status ?: result.zone.status ?: "libre"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(status = status)
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.zone.label.ifBlank { result.zone.id },
                    color = PmmTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = result.subtitle,
                    color = PmmTextSecondary,
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = statusLabel(status),
                color = statusTextColor(status),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FloatingStatsCard(stats: WarehouseMapStats, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.95f),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatCell("Ubicaciones", stats.totalZones.toString(), PmmTextPrimary)
            StatDivider()
            StatCell("Ocupadas", stats.occupiedZones.toString(), statusTextColor("ocupada"))
            StatDivider()
            StatCell("Libres", stats.freeZones.toString(), statusTextColor("libre"))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = valueColor, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = PmmTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun StatDivider() {
    Box(Modifier.width(1.dp).height(26.dp).background(Color(0xFFE8EDF3)))
}

@Composable
private fun LegendStrip(
    occupancy: Map<String, LocationOccupancy>,
    claseModels: List<ClaseModelSpec>,
    modifier: Modifier = Modifier
) {
    val claseIndex = remember(claseModels) { claseModelIndex(claseModels) }
    val classItems = remember(occupancy, claseModels) {
        occupancy.values
            .mapNotNull { dominantClase(it).takeIf(String::isNotBlank) }
            .distinct()
            .take(4)
            .map { clase -> clase to resolveClaseModel(clase, claseIndex).color }
    }
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.94f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (classItems.isNotEmpty()) {
                classItems.forEach { (clase, color) -> LegendItem(color, shortClase(clase)) }
            } else {
                LegendItem("#22c55e", "Libre")
                LegendItem("#f43f5e", "Ocupada")
                LegendItem("#eab308", "Parcial")
            }
        }
    }
}

@Composable
private fun LegendItem(hex: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).background(parseHexColor(hex), CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(label, color = PmmTextSecondary, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun OccupancyErrorBanner(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFFFFBEB).copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = PmmAmberDark)
            Spacer(Modifier.width(7.dp))
            Text(
                text = message,
                color = PmmAmberDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScreenLoadingState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = PmmBlueDeep)
            Text("Cargando mapa de bodega", color = PmmTextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ScreenErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, PmmNavyBorder)) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = PmmRed, modifier = Modifier.size(34.dp))
                Text("No se pudo cargar", color = PmmTextPrimary, fontWeight = FontWeight.Black)
                Text(message, color = PmmTextSecondary, fontSize = 12.5.sp)
                Surface(
                    modifier = Modifier.clickable(onClick = onRetry),
                    shape = RoundedCornerShape(10.dp),
                    color = PmmBlueDeep
                ) {
                    Text(
                        text = "Reintentar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusDot(status: String, size: Int = 10) {
    Box(modifier = Modifier.size(size.dp).background(zoneStrokeColor(null, status), CircleShape))
}

internal data class WarehouseSearchResult(
    val zone: WarehouseZone,
    val occupancy: LocationOccupancy?,
    val subtitle: String
)

private fun buildSearchResults(
    query: String,
    layout: WarehouseLayout?,
    occupancy: Map<String, LocationOccupancy>
): List<WarehouseSearchResult> {
    val cleanQuery = query.trim()
    if (cleanQuery.isBlank() || layout == null) return emptyList()

    return layout.zones.mapNotNull { zone ->
        val locationOccupancy = occupancy[zone.id.trim().uppercase()]
        val zoneFields = listOf(
            zone.id,
            zone.label,
            zone.description.orEmpty(),
            zone.section.orEmpty(),
            zone.aisle.orEmpty(),
            zone.level.orEmpty()
        )
        val matchesZone = zoneFields.any { it.contains(cleanQuery, ignoreCase = true) }
        val matchedEntry = locationOccupancy?.entradas?.firstOrNull { entry ->
            listOf(
                entry.creferencia,
                entry.ref,
                entry.cliente,
                entry.clienteId,
                entry.descripcion,
                entry.clase,
                entry.tipoOperacion,
                entry.tipop
            ).any { it.contains(cleanQuery, ignoreCase = true) }
        }

        if (!matchesZone && matchedEntry == null) return@mapNotNull null

        WarehouseSearchResult(
            zone = zone,
            occupancy = locationOccupancy,
            subtitle = matchedEntry?.let {
                listOf(it.creferencia, it.cliente, it.descripcion)
                    .filter { value -> value.isNotBlank() }
                    .joinToString(" - ")
            } ?: zone.description?.takeIf { it.isNotBlank() } ?: "Ubicacion ${zone.id}"
        )
    }
        .sortedBy { it.zone.id }
        .take(30)
}
