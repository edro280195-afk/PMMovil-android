package com.example.pmmovil.ui.casetahub

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.data.model.CasetaPreregistroEnCaminoItem
import com.example.pmmovil.data.model.SalidaCajaItem
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.pmmPressable
import com.example.pmmovil.ui.components.pmmTap
import com.example.pmmovil.ui.components.rememberPmmHaptics
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.PmmYellowSoft
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.delay

// ── Paleta local de la cúpula ────────────────────────────────────────────────
// El azul de la cúpula es más profundo que PmmBlueDeep a propósito: es la
// superficie "instrumento" del módulo, tiene que separarse del azul de acción.
private val DomeTop = Color(0xFF003875)
private val DomeBottom = Color(0xFF001A38)
private val DomeMid = Color(0xFF00224A)
private val SkyAccent = Color(0xFF38BDF8)
private val DomeLabel = Color(0xFF93A9C7)
private val DomeChipText = Color(0xFFCBD5E1)

private const val TABLET_MIN_WIDTH_DP = 760

@Composable
fun CasetaHubScreen(
    onNavigateBack: () -> Unit,
    onPreregistro: () -> Unit,
    onNuevaEntrada: () -> Unit,
    onSalidaRelacion: () -> Unit,
    onSalidaFisica: () -> Unit,
    onAbrirCaja: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onVerMapaCamiones: () -> Unit,
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: CasetaHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptics = rememberPmmHaptics()

    var facetSheet by remember { mutableStateOf<CasetaHubFacet?>(null) }
    var cajaSheet by remember { mutableStateOf<SalidaCajaItem?>(null) }

    // Lector físico de la tableta: en el hub escanear es buscar.
    HardwareScannerListener(viewModel::onScannerInput)

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.onScannerInput(it)
            onBarcodeScanConsumed()
        }
    }

    LaunchedEffect(state.errorMessage, state.feedbackSeq) {
        state.errorMessage?.let { message ->
            haptics.reject()
            snackbar.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.infoMessage) {
        state.infoMessage?.let { message ->
            haptics.confirm()
            snackbar.showSnackbar(message)
            viewModel.clearInfoMessage()
        }
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbar) }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingValues.calculateBottomPadding())
                .background(PmmBgPrimary)
        ) {
            val isTablet = maxWidth >= TABLET_MIN_WIDTH_DP.dp

            if (isTablet) {
                TabletHub(
                    state = state,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onPreregistro = onPreregistro,
                    onNuevaEntrada = onNuevaEntrada,
                    onSalidaRelacion = onSalidaRelacion,
                    onSalidaFisica = onSalidaFisica,
                    puedePreregistro = state.puedePreregistro,
                    puedeNuevaEntrada = state.puedeNuevaEntrada,
                    puedeSalidaRelacion = state.puedeSalidaRelacion,
                    puedeSalidaFisica = state.puedeSalidaFisica,
                    onNavigateToScanner = onNavigateToScanner,
                    onVerMapaCamiones = onVerMapaCamiones,
                    onFacetTap = { facetSheet = it },
                    onCajaTap = { cajaSheet = it },
                    onAbrirCaja = onAbrirCaja
                )
            } else {
                MobileHub(
                    state = state,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onPreregistro = onPreregistro,
                    onNuevaEntrada = onNuevaEntrada,
                    onSalidaRelacion = onSalidaRelacion,
                    onSalidaFisica = onSalidaFisica,
                    puedePreregistro = state.puedePreregistro,
                    puedeNuevaEntrada = state.puedeNuevaEntrada,
                    puedeSalidaRelacion = state.puedeSalidaRelacion,
                    puedeSalidaFisica = state.puedeSalidaFisica,
                    onNavigateToScanner = onNavigateToScanner,
                    onVerMapaCamiones = onVerMapaCamiones,
                    onFacetTap = { facetSheet = it },
                    onCajaTap = { cajaSheet = it },
                    onAbrirCaja = onAbrirCaja
                )
            }
        }
    }

    facetSheet?.let { facet ->
        FacetSheet(
            facet = facet,
            options = state.opcionesDe(facet),
            selected = state.filtroDe(facet),
            onPick = { value ->
                viewModel.toggleFiltro(facet, value)
                facetSheet = null
            },
            onClear = {
                viewModel.toggleFiltro(facet, null)
                facetSheet = null
            },
            onDismiss = { facetSheet = null }
        )
    }

    cajaSheet?.let { caja ->
        CajaAccionesSheet(
            caja = caja,
            onEditar = {
                cajaSheet = null
                onAbrirCaja(caja.casetaId)
            },
            onImprimir = {
                cajaSheet = null
                viewModel.imprimirEtiqueta(caja)
            },
            onDismiss = { cajaSheet = null }
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  TELÉFONO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun MobileHub(
    state: CasetaHubUiState,
    viewModel: CasetaHubViewModel,
    onNavigateBack: () -> Unit,
    onPreregistro: () -> Unit,
    onNuevaEntrada: () -> Unit,
    onSalidaRelacion: () -> Unit,
    onSalidaFisica: () -> Unit,
    puedePreregistro: Boolean,
    puedeNuevaEntrada: Boolean,
    puedeSalidaRelacion: Boolean,
    puedeSalidaFisica: Boolean,
    onNavigateToScanner: () -> Unit,
    onVerMapaCamiones: () -> Unit,
    onFacetTap: (CasetaHubFacet) -> Unit,
    onCajaTap: (SalidaCajaItem) -> Unit,
    onAbrirCaja: (String) -> Unit
) {
    val listState = rememberLazyListState()

    // La barra compacta aparece cuando la cúpula ya se fue: el buscador nunca
    // deja de estar a un toque, que es la razón de subirlo al top.
    val showCompactSearch by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 320
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "dome") {
                HubDome(
                    state = state,
                    onBack = onNavigateBack,
                    onRefresh = viewModel::refresh,
                    onQueryChange = viewModel::setQuery,
                    onClearQuery = viewModel::clearQuery,
                    onScan = onNavigateToScanner,
                    onFacetTap = onFacetTap,
                    onFacetClear = { viewModel.toggleFiltro(it, null) }
                )
            }

            item(key = "acciones") {
                AccionesCaseta(
                    pendientes = state.totalEnPlanta,
                    onPreregistro = onPreregistro,
                    onEntrada = onNuevaEntrada,
                    onSalidaRelacion = onSalidaRelacion,
                    onSalidaFisica = onSalidaFisica,
                    puedePreregistro = puedePreregistro,
                    puedeNuevaEntrada = puedeNuevaEntrada,
                    puedeSalidaRelacion = puedeSalidaRelacion,
                    puedeSalidaFisica = puedeSalidaFisica,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
                )
            }

            if (state.enCamino.isNotEmpty()) {
                item(key = "encamino") {
                    EnCaminoStrip(
                        items = state.enCamino,
                        onClick = onVerMapaCamiones,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            item(key = "listhead") {
                ListHeader(
                    visibles = state.visibles.size,
                    total = state.totalEnPlanta,
                    hayFiltros = state.hayFiltros,
                    onLimpiar = viewModel::limpiarFiltros,
                    modifier = Modifier.padding(start = 18.dp, end = 14.dp, top = 22.dp, bottom = 10.dp)
                )
            }

            itemsIndexedPatio(
                items = state.visibles,
                query = state.query,
                onTap = onCajaTap
            )

            if (state.visibles.isEmpty()) {
                item(key = "vacio") {
                    PatioVacio(
                        state = state,
                        onBuscarHistorico = viewModel::buscarEnHistorico,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                }
            }

            if (state.historico.isNotEmpty()) {
                item(key = "histhead") {
                    Text(
                        text = "Fuera de planta",
                        color = PmmTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 18.dp, top = 26.dp, bottom = 10.dp)
                    )
                }
                items(state.historico, key = { "h-" + it.casetaid }) { item ->
                    HistoricoRow(
                        item = item,
                        query = state.query,
                        onClick = { onAbrirCaja(item.casetaid) },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                }
            }

            item(key = "bottom") {
                Spacer(Modifier.navigationBarsPadding().height(16.dp))
            }
        }

        AnimatedVisibility(
            visible = showCompactSearch,
            enter = slideInVertically(
                animationSpec = tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)
            ) { -it } + fadeIn(tween(Motion.DurationQuick)),
            exit = slideOutVertically(
                animationSpec = tween(Motion.DurationQuick, easing = Motion.EmphasizedAccelerate)
            ) { -it } + fadeOut(tween(Motion.DurationQuick))
        ) {
            CompactSearchBar(
                state = state,
                onQueryChange = viewModel::setQuery,
                onClearQuery = viewModel::clearQuery,
                onScan = onNavigateToScanner,
                onBack = onNavigateBack
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  TABLETA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun TabletHub(
    state: CasetaHubUiState,
    viewModel: CasetaHubViewModel,
    onNavigateBack: () -> Unit,
    onPreregistro: () -> Unit,
    onNuevaEntrada: () -> Unit,
    onSalidaRelacion: () -> Unit,
    onSalidaFisica: () -> Unit,
    puedePreregistro: Boolean,
    puedeNuevaEntrada: Boolean,
    puedeSalidaRelacion: Boolean,
    puedeSalidaFisica: Boolean,
    onNavigateToScanner: () -> Unit,
    onVerMapaCamiones: () -> Unit,
    onFacetTap: (CasetaHubFacet) -> Unit,
    onCajaTap: (SalidaCajaItem) -> Unit,
    onAbrirCaja: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Franja superior: el buscador ocupa todo el ancho, arriba de todo.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(DomeTop, DomeMid, DomeBottom)))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DomeIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    onClick = onNavigateBack
                )
                SearchPill(
                    query = state.query,
                    onQueryChange = viewModel::setQuery,
                    onClear = viewModel::clearQuery,
                    onScan = onNavigateToScanner,
                    height = 52.dp,
                    modifier = Modifier.weight(1f)
                )
                PlantaChip(planta = state.planta)
                DomeIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    loading = state.isLoading,
                    onClick = viewModel::refresh
                )
            }

            FacetChips(
                state = state,
                onFacetTap = onFacetTap,
                onFacetClear = { viewModel.toggleFiltro(it, null) }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // El riel scrollea: en la Armor Pad en horizontal el pulso mas las
            // tres acciones no caben, y sin scroll el boton de Salida quedaba
            // fuera de pantalla, inalcanzable.
            Column(
                modifier = Modifier
                    .width(356.dp)
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                PulsoDePlanta(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFF00306B), DomeBottom)))
                        .padding(horizontal = 14.dp, vertical = 16.dp)
                )


                AccionesCaseta(
                    pendientes = state.totalEnPlanta,
                    onPreregistro = onPreregistro,
                    onEntrada = onNuevaEntrada,
                    onSalidaRelacion = onSalidaRelacion,
                    onSalidaFisica = onSalidaFisica,
                    puedePreregistro = puedePreregistro,
                    puedeNuevaEntrada = puedeNuevaEntrada,
                    puedeSalidaRelacion = puedeSalidaRelacion,
                    puedeSalidaFisica = puedeSalidaFisica,
                    vertical = true,
                    entradasHoy = state.entradasHoy
                )


                if (state.enCamino.isNotEmpty()) {
                    EnCaminoStrip(items = state.enCamino, onClick = onVerMapaCamiones)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ListHeader(
                    visibles = state.visibles.size,
                    total = state.totalEnPlanta,
                    hayFiltros = state.hayFiltros,
                    onLimpiar = viewModel::limpiarFiltros,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                if (state.visibles.isEmpty()) {
                    PatioVacio(
                        state = state,
                        onBuscarHistorico = viewModel::buscarEnHistorico
                    )
                    if (state.historico.isNotEmpty()) {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            items(state.historico, key = { "h-" + it.casetaid }) { item ->
                                HistoricoRow(
                                    item = item,
                                    query = state.query,
                                    onClick = { onAbrirCaja(item.casetaid) }
                                )
                            }
                        }
                    }
                } else {
                    // Adaptive y no Fixed(2): con el riel de 356dp quedan ~440dp
                    // utiles y a dos columnas el nombre del cliente se cortaba a
                    // media palabra. En tabletas mas anchas si entran dos.
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 320.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(state.visibles, key = { it.casetaId }) { item ->
                            PatioRow(
                                item = item,
                                query = state.query,
                                onClick = { onCajaTap(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  CÚPULA (topbar + buscador + chips + pulso)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun HubDome(
    state: CasetaHubUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onScan: () -> Unit,
    onFacetTap: (CasetaHubFacet) -> Unit,
    onFacetClear: (CasetaHubFacet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(Brush.verticalGradient(listOf(DomeTop, DomeMid, DomeBottom)))
            .statusBarsPadding()
            .padding(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DomeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                onClick = onBack
            )
            PlantaChip(planta = state.planta)
            DomeIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = "Actualizar",
                loading = state.isLoading,
                onClick = onRefresh
            )
        }

        SearchPill(
            query = state.query,
            onQueryChange = onQueryChange,
            onClear = onClearQuery,
            onScan = onScan,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        FacetChips(
            state = state,
            onFacetTap = onFacetTap,
            onFacetClear = onFacetClear,
            contentPadding = PaddingValues(horizontal = 14.dp)
        )

        PulsoDePlanta(
            state = state,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PlantaChip(planta: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color.White.copy(alpha = 0.09f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.13f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LivePulseDot()
            Text(
                text = planta.takeIf(String::isNotBlank)?.let { "PATIO ${it.uppercase(Locale.ROOT)}" }
                    ?: "PATIO",
                color = Color(0xFFDBEAFE),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/** Punto verde: la senal de "hay conexion". Estatico, no late. */
@Composable
private fun LivePulseDot() {
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(Color(0xFF4ADE80))
    )
}

@Composable
private fun DomeIconButton(
    icon: ImageVector,
    contentDescription: String,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.11f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .pmmPressable(enabled = !loading, pressedScale = 0.88f, onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier.height(height),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PmmTextMuted,
                modifier = Modifier.size(20.dp)
            )

            // BasicTextField y no TextField: el de Material trae 56dp de alto
            // minimo y padding propio, que en una pildora de 48dp deja el texto
            // recortado. Aqui el alto lo manda el contenedor.
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PmmTextPrimary
                    ),
                    cursorBrush = SolidColor(PmmBlueDeep),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                    )
                )
                if (query.isEmpty()) {
                    Text(
                        text = "Caja, cliente, línea, placas…",
                        color = PmmTextMuted,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (query.isNotBlank()) {
                Surface(
                    shape = CircleShape,
                    color = PmmNavyBorder,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .pmmPressable(pressedScale = 0.85f) {
                            onClear()
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Limpiar búsqueda",
                            tint = PmmTextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(13.dp),
                color = Color(0xFF002E63),
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .pmmPressable(pressedScale = 0.86f, onClick = onScan)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Escanear caja",
                        tint = PmmYellow,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSearchBar(
    state: CasetaHubUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onScan: () -> Unit,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(DomeTop, DomeMid)))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DomeIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                onClick = onBack
            )
            SearchPill(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                onScan = onScan,
                height = 48.dp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FacetChips(
    state: CasetaHubUiState,
    onFacetTap: (CasetaHubFacet) -> Unit,
    onFacetClear: (CasetaHubFacet) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding
    ) {
        items(CasetaHubFacet.entries.toList(), key = { it.name }) { facet ->
            val selected = state.filtroDe(facet)
            FacetChip(
                label = selected?.let { abreviar(it) } ?: facet.label,
                active = selected != null,
                onClick = {
                    if (selected != null) onFacetClear(facet) else onFacetTap(facet)
                }
            )
        }
    }
}

@Composable
private fun FacetChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (active) PmmYellow else Color.White.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            if (active) PmmYellow else Color.White.copy(alpha = 0.16f)
        ),
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .pmmPressable(pressedScale = 0.93f, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                color = if (active) Color(0xFF3F2D00) else DomeChipText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Icon(
                imageVector = if (active) Icons.Default.Close else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = if (active) Color(0xFF3F2D00).copy(alpha = 0.8f) else DomeChipText.copy(alpha = 0.65f),
                modifier = Modifier.size(if (active) 13.dp else 15.dp)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  PULSO DE PLANTA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun PulsoDePlanta(
    state: CasetaHubUiState,
    modifier: Modifier = Modifier
) {
    val total = state.entradasHoy + state.salidasHoy
    val fraccionEntradas = if (total == 0) 0.5f else state.entradasHoy.toFloat() / total
    val fraccionAnimada by animateFloatAsState(
        targetValue = fraccionEntradas,
        animationSpec = tween(Motion.DurationEmphasized * 2, easing = Motion.EmphasizedDecelerate),
        label = "flowSplit"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlowSide(
                icon = Icons.Default.ArrowDownward,
                iconTint = SkyAccent,
                value = state.entradasHoy,
                label = "ENTRADAS",
                modifier = Modifier.weight(1f)
            )

            NucleoEnPlanta(
                value = state.visibles.size,
                total = state.totalEnPlanta,
                mostrarTotal = state.hayFiltros
            )

            FlowSide(
                icon = Icons.Default.ArrowUpward,
                iconTint = PmmYellowSoft,
                value = state.salidasHoy,
                label = "SALIDAS",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(13.dp))

        // Balance del día: entradas empujando desde la izquierda, salidas desde
        // la derecha. Una gráfica completa aquí sería ruido; esto se lee de un ojo.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.11f))
        ) {
            // Sin movimientos hoy la barra se queda vacia. Pintar 50/50 seria
            // inventarse un balance que no existe.
            if (total > 0) {
                Box(
                    modifier = Modifier
                        .weight(max(0.001f, fraccionAnimada))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(SkyAccent, Color(0xFF0EA5E9))))
                )
                Box(
                    modifier = Modifier
                        .weight(max(0.001f, 1f - fraccionAnimada))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(PmmYellowSoft, Color(0xFFEAB308))))
                )
            }
        }

        Spacer(Modifier.height(7.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "FLUJO DE HOY",
                color = DomeLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = "${state.entradasHoy} / ${state.salidasHoy}",
                color = DomeLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FlowSide(
    icon: ImageVector,
    iconTint: Color,
    value: Int,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.height(3.dp))
            CountingNumber(
                target = value,
                fontSize = 30.sp,
                color = Color.White
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = label,
                color = DomeLabel,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.3.sp
            )
        }
    }
}

@Composable
private fun NucleoEnPlanta(value: Int, total: Int, mostrarTotal: Boolean) {
    // Sin aro giratorio: una animacion infinita aqui invalida el arbol en cada
    // cuadro, tambien mientras se hace scroll de la lista. El nucleo ya se
    // distingue por el amarillo y por el contador que sube.
    Box(
        modifier = Modifier.size(118.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(33.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF0B2A55),
                            Color(0xFF03152F)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CountingNumber(
                    target = value,
                    fontSize = 44.sp,
                    color = PmmYellow
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "EN PLANTA",
                    color = Color(0xFFE3D27A),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.4.sp
                )
                if (mostrarTotal) {
                    Text(
                        text = "de $total",
                        color = DomeLabel,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/** Contador que sube desde el valor anterior. Es lo que hace que el hub "arranque". */
@Composable
private fun CountingNumber(
    target: Int,
    fontSize: TextUnit,
    color: Color
) {
    val animated by androidx.compose.animation.core.animateIntAsState(
        targetValue = target,
        animationSpec = tween(
            durationMillis = Motion.DurationEmphasized * 2,
            easing = Motion.EmphasizedDecelerate
        ),
        label = "count"
    )
    Text(
        text = animated.toString(),
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-1).sp,
        maxLines = 1
    )
}

// ═══════════════════════════════════════════════════════════════════════════
//  ACCIONES
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun AccionesCaseta(
    pendientes: Int,
    onPreregistro: () -> Unit,
    onEntrada: () -> Unit,
    onSalidaRelacion: () -> Unit,
    onSalidaFisica: () -> Unit,
    puedePreregistro: Boolean,
    puedeNuevaEntrada: Boolean,
    puedeSalidaRelacion: Boolean,
    puedeSalidaFisica: Boolean,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    entradasHoy: Int = 0
) {
    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (puedePreregistro) {
                AccionAncha(
                    icon = Icons.Default.QrCodeScanner,
                    titulo = "Pre-registro",
                    detalle = "QR",
                    variant = AccionVariant.PRE,
                    onClick = onPreregistro
                )
            }
            if (puedeNuevaEntrada) {
                AccionAncha(
                    icon = Icons.Default.ArrowDownward,
                    titulo = "Entrada",
                    detalle = if (entradasHoy > 0) "$entradasHoy hoy" else "",
                    variant = AccionVariant.ENTRADA,
                    onClick = onEntrada
                )
            }
            if (puedeSalidaRelacion) {
                AccionAncha(
                    icon = Icons.Default.ArrowUpward,
                    titulo = "Salida de relación",
                    detalle = "Autorizada",
                    variant = AccionVariant.SALIDA_RELACION,
                    onClick = onSalidaRelacion
                )
            }
            if (puedeSalidaFisica) {
                AccionAncha(
                    icon = Icons.Default.LocalShipping,
                    titulo = "Salida física de caja",
                    detalle = if (pendientes > 0) "$pendientes en patio" else "Patio",
                    variant = AccionVariant.SALIDA_FISICA,
                    onClick = onSalidaFisica
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (puedePreregistro) {
                    AccionTile(Icons.Default.QrCodeScanner, "Pre-registro", AccionVariant.PRE, onPreregistro, Modifier.weight(1f))
                }
                if (puedeNuevaEntrada) {
                    AccionTile(Icons.Default.ArrowDownward, "Entrada", AccionVariant.ENTRADA, onEntrada, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (puedeSalidaRelacion) {
                    AccionTile(Icons.Default.ArrowUpward, "Salida de relación", AccionVariant.SALIDA_RELACION, onSalidaRelacion, Modifier.weight(1f))
                }
                if (puedeSalidaFisica) {
                    AccionTile(Icons.Default.LocalShipping, "Salida física", AccionVariant.SALIDA_FISICA, onSalidaFisica, Modifier.weight(1f), pendientes.takeIf { it > 0 })
                }
            }
        }
    }
}

private enum class AccionVariant { PRE, ENTRADA, SALIDA_RELACION, SALIDA_FISICA }

@Composable
private fun AccionTile(
    icon: ImageVector,
    titulo: String,
    variant: AccionVariant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int? = null
) {
    val esEntrada = variant == AccionVariant.ENTRADA
    Surface(
        modifier = modifier
            .height(106.dp)
            .clip(RoundedCornerShape(22.dp))
            .pmmPressable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = if (esEntrada) Color.Transparent else Color.White,
        border = when (variant) {
            AccionVariant.PRE -> BorderStroke(1.5.dp, Color(0xFFEAB308))
            AccionVariant.SALIDA_RELACION, AccionVariant.SALIDA_FISICA -> BorderStroke(1.dp, PmmNavyBorder)
            AccionVariant.ENTRADA -> null
        },
        shadowElevation = if (esEntrada) 8.dp else 3.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (esEntrada) {
                        Modifier.background(
                            Brush.verticalGradient(listOf(PmmBlueDeep, Color(0xFF002A73)))
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AccionGlyph(icon = icon, variant = variant)
                Spacer(Modifier.height(9.dp))
                Text(
                    text = titulo,
                    color = if (esEntrada) Color.White else PmmTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }

            badge?.let {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = PmmTextPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(9.dp)
                ) {
                    Text(
                        text = it.toString(),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccionAncha(
    icon: ImageVector,
    titulo: String,
    detalle: String,
    variant: AccionVariant,
    onClick: () -> Unit
) {
    val esEntrada = variant == AccionVariant.ENTRADA
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(74.dp)
            .clip(RoundedCornerShape(20.dp))
            .pmmPressable(pressedScale = 0.975f, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (esEntrada) Color.Transparent else Color.White,
        border = when (variant) {
            AccionVariant.PRE -> BorderStroke(1.5.dp, Color(0xFFEAB308))
            AccionVariant.SALIDA_RELACION, AccionVariant.SALIDA_FISICA -> BorderStroke(1.dp, PmmNavyBorder)
            AccionVariant.ENTRADA -> null
        },
        shadowElevation = if (esEntrada) 8.dp else 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (esEntrada) {
                        Modifier.background(
                            Brush.horizontalGradient(listOf(PmmBlueDeep, Color(0xFF002A73)))
                        )
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AccionGlyph(icon = icon, variant = variant)
            Text(
                text = titulo,
                color = if (esEntrada) Color.White else PmmTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f)
            )
            if (detalle.isNotBlank()) {
                Text(
                    text = detalle,
                    color = if (esEntrada) Color(0xFF9DBBEA) else PmmTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AccionGlyph(
    icon: ImageVector,
    variant: AccionVariant
) {
    val (fondo, tinte) = when (variant) {
        AccionVariant.PRE -> Color(0xFFFDE68A) to Color(0xFF854D0E)
        AccionVariant.ENTRADA -> Color.White.copy(alpha = 0.16f) to Color.White
        AccionVariant.SALIDA_RELACION -> Color(0xFFE0E7FF) to PmmBlueDeep
        AccionVariant.SALIDA_FISICA -> Color(0xFFD1FAE5) to Color(0xFF15803D)
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(fondo),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tinte,
            modifier = Modifier.size(24.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  EN CAMINO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EnCaminoStrip(
    items: List<CasetaPreregistroEnCaminoItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val proximo = items.minByOrNull { it.etaMinutos ?: Int.MAX_VALUE }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pmmPressable(pressedScale = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color(0xFFE0F2FE)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = Color(0xFF0369A1),
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${items.size} en camino",
                    color = PmmTextPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                proximo?.let {
                    Text(
                        text = listOf(it.nunidad, it.nombreLinea)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        color = PmmTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            proximo?.etaMinutos?.let { eta ->
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFFDCFCE7)
                ) {
                    Text(
                        text = "$eta min",
                        color = Color(0xFF15803D),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  LISTA DE PATIO
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ListHeader(
    visibles: Int,
    total: Int,
    hayFiltros: Boolean,
    onLimpiar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "En planta",
            color = PmmTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold
        )

        if (hayFiltros) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White,
                border = BorderStroke(1.dp, PmmNavyBorder),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .pmmPressable(pressedScale = 0.92f, onClick = onLimpiar)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "$visibles de $total",
                        color = PmmTextSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Quitar filtros",
                        tint = PmmTextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        } else {
            Text(
                text = total.toString(),
                color = PmmTextMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

private fun LazyListScope.itemsIndexedPatio(
    items: List<SalidaCajaItem>,
    query: String,
    onTap: (SalidaCajaItem) -> Unit
) {
    items(items, key = { it.casetaId }) { item ->
        PatioRow(
            item = item,
            query = query,
            onClick = { onTap(item) },
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.5.dp)
        )
    }
}

@Composable
private fun PatioRow(
    item: SalidaCajaItem,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val acento = when {
        item.diasEnPatio >= 7 -> PmmRed
        item.diasEnPatio >= 3 -> Color(0xFFEAB308)
        else -> PmmBlueLight
    }

    // Sin shadowElevation ni clip extra: con 100+ cajas en patio cada sombra es
    // una pasada de dibujo mas por fila, y el borde ya separa la tarjeta.
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pmmTap(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(acento)
            )

            // Un tractocamion no trae caja: el titulo cae al numero de unidad y
            // lineaDe() tambien -- sin esto la fila decia "T-4521 / T-4521".
            val titulo = item.numCaja.trim().ifBlank { item.numCamion.trim() }
            val subtitulo = listOf(item.nombreCliente, CasetaHubUiState.lineaDe(item))
                .map(String::trim)
                .filter { it.isNotBlank() && !it.equals(titulo, ignoreCase = true) }
                .distinct()
                .joinToString(" · ")
                .ifBlank {
                    if (item.placas.isNotBlank()) "Placas " + item.placas else "Sin cliente asignado"
                }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resaltar(titulo, query),
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = resaltar(subtitulo, query),
                    color = PmmTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                MovimientoPill(movimiento = item.tipoMovimiento, tipoOp = item.tipoOp)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = diasLabel(item.diasEnPatio),
                    color = when {
                        item.diasEnPatio >= 7 -> PmmRed
                        item.diasEnPatio >= 3 -> PmmAmberDark
                        else -> PmmTextMuted
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun MovimientoPill(movimiento: String, tipoOp: String) {
    val esExport = tipoOp.equals("EXPO", ignoreCase = true) ||
        tipoOp.equals("EXPORTACION", ignoreCase = true)
    val fondo = if (esExport) Color(0xFFFEF3C7) else Color(0xFFEFF6FF)
    val borde = if (esExport) Color(0xFFFDE68A) else Color(0xFFBFDBFE)
    val tinta = if (esExport) Color(0xFF92400E) else Color(0xFF1D4ED8)

    Surface(
        shape = RoundedCornerShape(7.dp),
        color = fondo,
        border = BorderStroke(1.dp, borde)
    ) {
        Text(
            text = abreviarMovimiento(movimiento),
            color = tinta,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.4.sp,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun HistoricoRow(
    item: CasetaEntradaBusquedaItem,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pmmTap(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = resaltar(item.nequipo.ifBlank { item.placas.ifBlank { item.casetaid } }, query),
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = resaltar(
                        listOf(item.cliente, item.transpore.ifBlank { item.transporu })
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .ifBlank { item.casetaid },
                        query
                    ),
                    color = PmmTextSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PmmTextMuted,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun PatioVacio(
    state: CasetaHubUiState,
    onBuscarHistorico: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    state.isLoading -> "Cargando el patio…"
                    state.hayFiltros -> "Nada en planta con eso"
                    else -> "El patio está vacío"
                },
                color = PmmTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )

            if (state.hayFiltros && !state.isLoading) {
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = PmmBlueDeep,
                    modifier = Modifier
                        .heightIn(min = 46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .pmmPressable(
                            enabled = !state.isSearchingHistorico,
                            pressedScale = 0.96f,
                            onClick = onBuscarHistorico
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        if (state.isSearchingHistorico) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = "Buscar en histórico",
                            color = Color.White,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                if (state.historicoBuscado && state.historico.isEmpty() && !state.isSearchingHistorico) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Tampoco hay registros anteriores.",
                        color = PmmTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  HOJAS INFERIORES
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FacetSheet(
    facet: CasetaHubFacet,
    options: List<CasetaHubFacetOption>,
    selected: String?,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberPmmHaptics()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            Text(
                text = facet.label,
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options, key = { it.value }) { option ->
                    val activa = option.value == selected
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .pmmPressable(pressedScale = 0.98f) {
                                haptics.select()
                                onPick(option.value)
                            },
                        shape = RoundedCornerShape(16.dp),
                        color = if (activa) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                        border = BorderStroke(
                            1.5.dp,
                            if (activa) PmmBlueLight else Color.Transparent
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (facet == CasetaHubFacet.MOVIMIENTO) {
                                    abreviarMovimiento(option.value)
                                } else {
                                    option.value
                                },
                                color = PmmTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = option.count.toString(),
                                color = PmmTextMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            if (selected != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .pmmPressable(pressedScale = 0.97f, onClick = onClear),
                    shape = RoundedCornerShape(15.dp),
                    color = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, PmmNavyBorder)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "Quitar filtro",
                            color = PmmTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CajaAccionesSheet(
    caja: SalidaCajaItem,
    onEditar: () -> Unit,
    onImprimir: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = caja.numCaja.ifBlank { caja.numCamion.ifBlank { caja.casetaId } },
                color = PmmTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = listOf(
                    caja.nombreCliente,
                    CasetaHubUiState.lineaDe(caja),
                    abreviarMovimiento(caja.tipoMovimiento),
                    diasLabel(caja.diasEnPatio)
                ).filter(String::isNotBlank).joinToString(" · "),
                color = PmmTextSecondary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(18.dp))

            AccionDeCaja(
                icon = Icons.Default.Edit,
                titulo = "Abrir registro",
                onClick = onEditar
            )
            Spacer(Modifier.height(9.dp))
            AccionDeCaja(
                icon = Icons.Default.Print,
                titulo = "Imprimir etiqueta",
                onClick = onImprimir
            )
        }
    }
}

@Composable
private fun AccionDeCaja(
    icon: ImageVector,
    titulo: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .pmmPressable(pressedScale = 0.98f, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(21.dp)
            )
            Text(
                text = titulo,
                color = PmmTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
//  UTILERÍAS DE PRESENTACIÓN
// ═══════════════════════════════════════════════════════════════════════════

/** Resalta la coincidencia del texto buscado, como lo hace el buscador del sistema. */
private fun resaltar(texto: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isBlank()) return AnnotatedString(texto)
    val index = texto.uppercase(Locale.ROOT).indexOf(q.uppercase(Locale.ROOT))
    if (index < 0) return AnnotatedString(texto)

    return buildAnnotatedString {
        append(texto.substring(0, index))
        withStyle(SpanStyle(background = PmmYellow.copy(alpha = 0.55f))) {
            append(texto.substring(index, index + q.length))
        }
        append(texto.substring(index + q.length))
    }
}

private fun diasLabel(dias: Int): String = when {
    dias <= 0 -> "hoy"
    else -> "${dias}d"
}

/** Nombres cortos para que la fila no se parta en dos renglones. */
private fun abreviarMovimiento(movimiento: String): String = when (movimiento.trim().uppercase(Locale.ROOT)) {
    "DESCARGAS" -> "DESCARGA"
    "CONSOLIDADO/MENSAJERIA" -> "CONSOL."
    "COLOCACION/VACIO" -> "VACÍO"
    "RECOLECCION/PICKUP" -> "PICKUP"
    "TRANSBORDO" -> "TRANSB."
    else -> movimiento.trim().ifBlank { "SIN MOV." }
}

/** El chip activo enseña solo la primera palabra: cabe y se reconoce igual. */
private fun abreviar(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= 12) return trimmed
    val primera = trimmed.substringBefore(' ')
    return if (primera.length in 1..12) "$primera…" else trimmed.take(11) + "…"
}
