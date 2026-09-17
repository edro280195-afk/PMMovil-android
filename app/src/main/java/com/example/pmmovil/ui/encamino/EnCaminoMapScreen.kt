package com.example.pmmovil.ui.encamino

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.CasetaPreregistroEnCaminoItem
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

// Centro por defecto antes de tener datos: Laredo, TX, sede de la mayoría de
// las bodegas de Grupo PMM (ver bodegaMail en CasetaService.cs).
private val DefaultMapCenter = LatLng(27.5064, -99.5075)

/**
 * Mapa de "Camiones en camino": pre-registros vigentes (tractocamión sin caja)
 * con última ubicación conocida, sobre Google Maps. Reemplaza la sola lista de
 * texto de la tarjeta del hub cuando el guardia quiere ver de dónde vienen de
 * verdad -- ver [[registro-tractocamiones-tiporegistro]] para el flujo de
 * pre-registro completo.
 */
@Composable
fun EnCaminoMapScreen(
    onNavigateBack: () -> Unit,
    viewModel: EnCaminoMapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        EnCaminoMapHeader(
            countConUbicacion = state.itemsConUbicacion.size,
            countTotal = state.items.size,
            isLoading = state.isLoading,
            lastUpdated = state.lastUpdated,
            onNavigateBack = onNavigateBack,
            onRefresh = viewModel::refresh
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isTablet = maxWidth >= 760.dp
            if (isTablet) {
                Row(modifier = Modifier.fillMaxSize()) {
                    EnCaminoGoogleMap(
                        state = state,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    VerticalDivider(color = PmmNavyBorder.copy(alpha = 0.4f))
                    EnCaminoList(
                        state = state,
                        onSelect = viewModel::selectItem,
                        modifier = Modifier.width(360.dp).fillMaxHeight()
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    EnCaminoGoogleMap(
                        state = state,
                        modifier = Modifier.fillMaxWidth().height(320.dp)
                    )
                    EnCaminoList(
                        state = state,
                        onSelect = viewModel::selectItem,
                        modifier = Modifier.fillMaxWidth().fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun EnCaminoMapHeader(
    countConUbicacion: Int,
    countTotal: Int,
    isLoading: Boolean,
    lastUpdated: String,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF003875), Color(0xFF001A38))))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(38.dp)) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camiones en camino", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                val subtitle = if (isLoading) {
                    "Actualizando…"
                } else {
                    "$countConUbicacion de $countTotal con ubicación · $lastUpdated"
                }
                Text(subtitle, color = Color(0xFF93C5FD), fontSize = 11.sp)
            }

            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.15f), modifier = Modifier.size(38.dp)) {
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EnCaminoGoogleMap(
    state: EnCaminoMapUiState,
    modifier: Modifier = Modifier
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultMapCenter, 10f)
    }
    var mapLoaded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
            onMapLoaded = { mapLoaded = true }
        ) {
            state.bodegas.forEach { bodega ->
                Marker(
                    state = MarkerState(LatLng(bodega.lat, bodega.lon)),
                    title = "Bodega ${bodega.idBodega}",
                    snippet = "Destino",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )
            }
            state.itemsConUbicacion.forEach { item ->
                Marker(
                    state = MarkerState(LatLng(item.lat!!, item.lon!!)),
                    title = item.nunidad.ifBlank { item.placas.ifBlank { "Tractocamión" } },
                    snippet = etaLabel(item.etaMinutos),
                    icon = BitmapDescriptorFactory.defaultMarker(hueForEta(item.etaMinutos))
                )
            }
        }

        // Ajusta cámara: si hay un camión seleccionado desde la lista, hace zoom
        // ahí; si no, encuadra todos los puntos (camiones + bodegas) a la vez.
        // Nunca antes de onMapLoaded -- newLatLngBounds necesita un mapa con tamaño ya medido.
        LaunchedEffect(mapLoaded, state.selectedFolio, state.itemsConUbicacion, state.bodegas) {
            if (!mapLoaded) return@LaunchedEffect

            val seleccionado = state.itemsConUbicacion.firstOrNull { it.folio == state.selectedFolio }
            if (seleccionado != null) {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(LatLng(seleccionado.lat!!, seleccionado.lon!!), 13f)
                )
                return@LaunchedEffect
            }

            val puntos = buildList {
                addAll(state.itemsConUbicacion.map { LatLng(it.lat!!, it.lon!!) })
                addAll(state.bodegas.map { LatLng(it.lat, it.lon) })
            }
            when {
                puntos.isEmpty() -> Unit
                puntos.size == 1 -> cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(puntos.first(), 11f))
                else -> {
                    val bounds = LatLngBounds.Builder().apply { puntos.forEach(::include) }.build()
                    runCatching {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 140))
                    }
                }
            }
        }

        if (state.itemsConUbicacion.isEmpty() && !state.isLoading) {
            Surface(
                color = Color.White.copy(alpha = 0.94f),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 2.dp,
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.LocationOff, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(18.dp))
                    Text(
                        "Ningún camión en camino reportó ubicación todavía",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EnCaminoList(
    state: EnCaminoMapUiState,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Color.White) {
        // Column explícita: Surface por sí sola superpone a sus hijos (como un
        // Box) en vez de apilarlos -- sin esto, el aviso de error quedaría
        // encimado sobre la lista en vez de aparecer debajo.
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.isLoading) "Cargando camiones en camino…" else "No hay camiones en camino",
                        color = PmmTextMuted,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.items, key = { it.folio.ifBlank { it.nunidad + it.placas } }) { item ->
                        EnCaminoDetailRow(
                            item = item,
                            selected = item.folio.isNotBlank() && item.folio == state.selectedFolio,
                            onClick = { if (item.tieneUbicacion) onSelect(item.folio) }
                        )
                        HorizontalDivider(color = PmmNavyBorder.copy(alpha = 0.4f))
                    }
                }
            }

            state.errorMessage?.let { message ->
                Surface(color = Color(0xFFFFF7ED), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EnCaminoDetailRow(
    item: CasetaPreregistroEnCaminoItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val etaMin = item.etaMinutos
    val accent = when {
        !item.tieneUbicacion -> PmmTextMuted
        etaMin == null -> PmmTextMuted
        etaMin <= 30 -> PmmRed
        etaMin <= 120 -> Color(0xFFD97706)
        else -> PmmBlueDeep
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(0.dp))
            .background(if (selected) PmmBlueDeep.copy(alpha = 0.07f) else Color.Transparent)
            .clickable(enabled = item.tieneUbicacion, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.nunidad.ifBlank { "Sin unidad" }, color = PmmTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                listOf(item.placas, item.nombreLinea, item.operador).filter(String::isNotBlank).joinToString(" · "),
                color = PmmTextMuted,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.tieneUbicacion) {
                Text("Sin ubicación reportada", color = PmmTextMuted, fontSize = 10.5.sp)
            }
        }
        Text(etaLabel(item.etaMinutos), color = accent, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun etaLabel(etaMin: Int?): String = when {
    etaMin == null -> "Sin ubicación"
    etaMin < 60 -> "$etaMin min"
    else -> "${etaMin / 60} h ${etaMin % 60} min"
}

private fun hueForEta(etaMin: Int?): Float = when {
    etaMin == null -> BitmapDescriptorFactory.HUE_YELLOW
    etaMin <= 30 -> BitmapDescriptorFactory.HUE_RED
    etaMin <= 120 -> BitmapDescriptorFactory.HUE_ORANGE
    else -> BitmapDescriptorFactory.HUE_AZURE
}
