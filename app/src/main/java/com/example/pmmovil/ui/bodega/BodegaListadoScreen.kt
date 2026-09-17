package com.example.pmmovil.ui.bodega

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.BodegaEntradaDto
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.composed
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Edit
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BodegaListadoScreen(
    onNavigateBack: () -> Unit,
    onNuevaEntrada: () -> Unit,
    onEntradaSeleccionada: (String) -> Unit,
    onNavigateToScanner: () -> Unit = {},
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: BodegaListadoViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    
    // Estados locales para rediseño y responsividad
    var activeFilter by rememberSaveable { mutableStateOf("all") }
    var selectedCref by rememberSaveable { mutableStateOf<String?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var activeDetailItem by remember { mutableStateOf<BodegaEntradaDto?>(null) }

    HardwareScannerListener(onBarcodeScanned = viewModel::onScannerInput)

    val filteredItems = remember(state.items, activeFilter) {
        when (activeFilter) {
            "hazmat" -> state.items.filter { it.hazmat }
            "inbond" -> state.items.filter { it.inbond }
            else -> state.items
        }
    }

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.onScannerInput(it)
            onBarcodeScanConsumed()
        }
    }
    LaunchedEffect(state.navigatedCref) {
        state.navigatedCref?.let {
            viewModel.consumeNavigation()
            onEntradaSeleccionada(it)
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar("✗ $it", duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbar) },
        topBar = {
            BodegaHeader(
                count = filteredItems.size,
                isLoading = state.isLoading,
                onBack = onNavigateBack,
                onRefresh = viewModel::cargar
            )
        },
        bottomBar = {
            BoxWithConstraints {
                if (maxWidth < 900.dp) {
                    Surface(
                        color = PmmBgSurface,
                        shadowElevation = 10.dp,
                        border = BorderStroke(1.dp, PmmNavyBorder)
                    ) {
                        Button(
                            onClick = onNuevaEntrada,
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PmmYellow,
                                contentColor = PmmNavyDark
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Registrar nueva entrada", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTabletLandscape = maxWidth >= 900.dp
            val isTabletPortrait = maxWidth >= 600.dp && maxWidth < 900.dp
            val contentWidth = if (maxWidth >= 1200.dp) 1150.dp else maxWidth

            LaunchedEffect(filteredItems, isTabletLandscape) {
                if (isTabletLandscape) {
                    if (selectedCref == null || !filteredItems.any { it.creferencia == selectedCref }) {
                        selectedCref = filteredItems.firstOrNull()?.creferencia
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentWidth)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // PANEL IZQUIERDO (Lista / Master)
                Column(
                    modifier = Modifier
                        .weight(if (isTabletLandscape) 0.4f else 1f)
                        .fillMaxHeight()
                ) {
                    SearchPanel(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        onClear = { viewModel.buscar("") },
                        onCameraClick = onNavigateToScanner
                    )
                    
                    BodegaFilterChips(
                        selectedFilter = activeFilter,
                        onFilterSelected = { activeFilter = it },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    if (state.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            color = PmmYellow,
                            trackColor = PmmNavyBorder
                        )
                    } else {
                        Spacer(Modifier.height(3.dp))
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            state.isLoading && state.items.isEmpty() -> BodegaListSkeleton()
                            filteredItems.isEmpty() -> BodegaEmptyState(
                                filtered = state.query.isNotBlank() || state.isFromScanner || activeFilter != "all",
                                onClear = {
                                    activeFilter = "all"
                                    viewModel.buscar("")
                                }
                            )
                            else -> LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 18.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (state.query.isBlank()) "MOVIMIENTOS RECIENTES" else "RESULTADOS",
                                            color = PmmTextMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "${filteredItems.size} entradas",
                                            color = PmmTextSecondary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (isTabletPortrait) {
                                    val rows = filteredItems.chunked(2)
                                    items(rows) { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowItems.forEach { entry ->
                                                Box(modifier = Modifier.weight(1f)) {
                                                    BodegaManifestRow(
                                                        entry = entry,
                                                        isSelected = false,
                                                        onClick = {
                                                            activeDetailItem = entry
                                                            showBottomSheet = true
                                                        }
                                                    )
                                                }
                                            }
                                            if (rowItems.size < 2) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                } else {
                                    items(filteredItems, key = { it.creferencia }) { entry ->
                                        val isSelected = if (isTabletLandscape) entry.creferencia == selectedCref else false
                                        BodegaManifestRow(
                                            entry = entry,
                                            isSelected = isSelected,
                                            onClick = {
                                                if (isTabletLandscape) {
                                                    selectedCref = entry.creferencia
                                                } else {
                                                    activeDetailItem = entry
                                                    showBottomSheet = true
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // En tableta horizontal el botón de Nueva Entrada se coloca al final de la columna izquierda
                    if (isTabletLandscape) {
                        Surface(
                            color = PmmBgSurface,
                            tonalElevation = 1.dp,
                            border = BorderStroke(1.dp, PmmNavyBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            Button(
                                onClick = onNuevaEntrada,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .height(50.dp),
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PmmYellow,
                                    contentColor = PmmNavyDark
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Registrar nueva entrada", fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }

                // PANEL DERECHO (Detalle - Solo en Tableta Landscape)
                if (isTabletLandscape) {
                    val entryForDetail = remember(filteredItems, selectedCref) {
                        filteredItems.find { it.creferencia == selectedCref }
                    }
                    Box(
                        modifier = Modifier
                            .weight(0.6f)
                            .fillMaxHeight()
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp, horizontal = 8.dp)
                    ) {
                        BodegaManifestDetailPanel(
                            entry = entryForDetail,
                            onEditClick = onEntradaSeleccionada
                        )
                    }
                }
            }

            if (state.isLoading && state.isFromScanner) {
                ScanSearchOverlay(query = state.query)
            }
        }
    }

    // Bottom Sheet para detalle de entrada (móvil y tableta vertical)
    if (showBottomSheet && activeDetailItem != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = PmmBgPrimary,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(vertical = 11.dp),
                    color = PmmTextMuted.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(5.dp)
                ) {
                    Spacer(modifier = Modifier.size(width = 36.dp, height = 4.dp))
                }
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                BodegaManifestDetailPanel(
                    entry = activeDetailItem,
                    onEditClick = { cref ->
                        showBottomSheet = false
                        onEntradaSeleccionada(cref)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanSearchOverlay(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PmmNavyDark.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 30.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CircularProgressIndicator(color = PmmYellow, strokeWidth = 3.dp)
                Text(
                    "Buscando entrada…",
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
                if (query.isNotBlank()) {
                    Text(
                        formatCref(query),
                        color = PmmBlueDeep,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun BodegaHeader(
    count: Int,
    isLoading: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(color = PmmNavyDark, shadowElevation = 5.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(PmmNavyDark, PmmBlueDeep)))
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint = Color.White
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Entradas de bodega",
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        if (isLoading) "Actualizando operación..." else "$count registros visibles",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Actualizar", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun SearchPanel(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onCameraClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (value.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                label = { Text("Referencia, cliente o proveedor") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PmmBgSurface,
                    unfocusedContainerColor = PmmBgSurface,
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder
                )
            )
            ScanCameraButton(onClick = onCameraClick)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "Puedes disparar el lector físico sin tocar la pantalla.",
                color = PmmTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun BodegaManifestRow(
    entry: BodegaEntradaDto,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .shadow(
                elevation = if (isSelected) 6.dp else 2.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false
            ),
        color = if (isSelected) PmmNavyVariant else PmmBgSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) PmmBlueDeep else PmmNavyBorder
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatCref(entry.creferencia),
                        color = PmmBlueDeep,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entry.cliente?.takeIf(String::isNotBlank) ?: "Cliente no disponible",
                        color = PmmTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (entry.hazmat) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PmmAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PmmAmber)
                        ) {
                            Text(
                                "HAZMAT",
                                color = Color(0xFF78350F),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (entry.inbond) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PmmBlueDeep.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PmmBlueDeep)
                        ) {
                            Text(
                                "IN-BOND",
                                color = PmmBlueDeep,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmTextMuted
                )
            }
            entry.proveedor?.takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 9.dp),
                color = PmmNavyBorder.copy(alpha = 0.6f)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManifestMeta(
                    icon = Icons.Default.CalendarMonth,
                    text = formatFechaArribo(entry.fArribo),
                    modifier = Modifier.weight(1.2f)
                )
                ManifestMeta(
                    icon = Icons.Default.Inventory2,
                    text = entry.caja?.takeIf(String::isNotBlank)?.let { "Caja $it" } ?: "Sin caja",
                    modifier = Modifier.weight(0.8f)
                )
                ManifestMeta(
                    icon = Icons.Default.LocalShipping,
                    text = entry.guia?.takeIf(String::isNotBlank)?.let { "Guía $it" } ?: "Sin guía",
                    modifier = Modifier.weight(0.9f)
                )
            }
        }
    }
}

@Composable
private fun ManifestMeta(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(
            text,
            color = PmmTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun OperationalBadge(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = background) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = foreground,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun BodegaListSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(6) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(118.dp),
                color = PmmBgSurface,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Skeleton(width = 190.dp, height = 18.dp)
                    Skeleton(width = 280.dp, height = 14.dp)
                    Spacer(Modifier.height(2.dp))
                    Skeleton(height = 1.dp, shape = RoundedCornerShape(0.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Skeleton(modifier = Modifier.weight(1f), height = 13.dp)
                        Skeleton(modifier = Modifier.weight(1f), height = 13.dp)
                        Skeleton(modifier = Modifier.weight(1f), height = 13.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodegaEmptyState(filtered: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = PmmNavyVariant) {
            Icon(
                if (filtered) Icons.Default.Search else Icons.Default.Inventory2,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.padding(18.dp).size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "No encontramos coincidencias" else "La bandeja está al día",
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            if (filtered) {
                "Prueba con otra referencia, cliente o proveedor."
            } else {
                "No hay entradas registradas durante los últimos 10 días."
            },
            color = PmmTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (filtered) {
            Button(
                onClick = onClear,
                modifier = Modifier.padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("Ver recientes", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatCref(cref: String): String {
    val clean = cref.trim()
    return if (clean.length == 15) {
        "${clean.substring(0, 2)}-${clean.substring(2, 3)}-${clean.substring(3, 5)}-" +
            "${clean.substring(5, 11)}-${clean.substring(11, 13)}-${clean.substring(13, 15)}"
    } else {
        clean
    }
}

private fun formatFechaArribo(raw: String?): String {
    if (raw.isNullOrBlank()) return "Sin fecha"
    val date = raw.substringBefore('T').split("-")
    val formatted = if (date.size == 3) "${date[2]}/${date[1]}/${date[0]}" else raw.take(10)
    val time = raw.substringAfter('T', "").take(5)
    return if (time.isBlank()) formatted else "$formatted $time"
}

// --- EXTENSIONES Y COMPONENTES PARA EL REDISEÑO RESPONSIVO ---

fun Modifier.bounceClick(onClick: () -> Unit) = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    try {
                        isPressed = true
                        awaitRelease()
                    } finally {
                        isPressed = false
                    }
                },
                onTap = { onClick() }
            )
        }
}

@Composable
fun ScanCameraButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .drawBehind {
                drawRoundRect(
                    color = PmmBlueDeep,
                    alpha = alpha,
                    size = this.size * scale,
                    topLeft = Offset(
                        x = (this.size.width - this.size.width * scale) / 2f,
                        y = (this.size.height - this.size.height * scale) / 2f
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx())
                )
            }
            .background(
                brush = Brush.linearGradient(listOf(PmmBlueDeep, PmmNavyDark)),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = "Escanear con cámara",
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BodegaFilterChips(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val filters = listOf(
        Triple("all", "Todos", ""),
        Triple("hazmat", "HAZMAT", ""),
        Triple("inbond", "IN-BOND", "")
    )
    
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(filters) { (key, label, _) ->
            val selected = selectedFilter == key
            Surface(
                onClick = { onFilterSelected(key) },
                shape = RoundedCornerShape(20.dp),
                color = if (selected) PmmNavyVariant else PmmBgSurface,
                border = BorderStroke(
                    width = 1.dp,
                    color = if (selected) PmmBlueDeep.copy(alpha = 0.3f) else PmmNavyBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = if (selected) PmmBlueDeep else PmmTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}


@Composable
private fun DetailField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgCard, RoundedCornerShape(10.dp))
            .border(BorderStroke(1.dp, PmmNavyBorder), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(label, color = PmmTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(value, color = PmmTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BodegaManifestDetailPanel(
    entry: BodegaEntradaDto?,
    onEditClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entry == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(PmmBgSurface, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, PmmNavyBorder), RoundedCornerShape(16.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Selecciona un registro",
                    color = PmmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Toca cualquier entrada de bodega para visualizar el manifiesto completo.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(PmmBgSurface, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, PmmNavyBorder), RoundedCornerShape(16.dp))
                .padding(20.dp)
        ) {
            Text(
                text = "MANIFIESTO DE CARGA",
                color = PmmBlueDeep,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(12.dp))
            
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PmmBgCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Text(
                    text = entry.creferencia,
                    color = PmmNavyDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entry.hazmat) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PmmAmber.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, PmmAmber)
                        ) {
                            Text(
                                "HAZMAT",
                                color = Color(0xFF78350F),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    if (entry.inbond) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PmmBlueDeep.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PmmBlueDeep)
                        ) {
                            Text(
                                "IN-BOND",
                                color = PmmBlueDeep,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
                
                DetailField(label = "CLIENTE", value = entry.cliente?.takeIf(String::isNotBlank) ?: "No disponible")
                DetailField(label = "PROVEEDOR", value = entry.proveedor?.takeIf(String::isNotBlank) ?: "No disponible")
                DetailField(label = "FECHA ARRIBO", value = formatFechaArribo(entry.fArribo))
                
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DetailField(label = "CAJA BODEGA", value = entry.caja?.takeIf(String::isNotBlank) ?: "Sin caja")
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        DetailField(label = "GUÍA / MANIFIESTO", value = entry.guia?.takeIf(String::isNotBlank) ?: "Sin guía")
                    }
                }
                
                DetailField(label = "TRANSPORTISTA", value = entry.transportista?.takeIf(String::isNotBlank) ?: "No disponible")
                DetailField(label = "REGISTRADO POR", value = entry.recibio?.takeIf(String::isNotBlank) ?: "No disponible")
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onEditClick(entry.creferencia) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Ver Detalle / Editar Entrada", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
