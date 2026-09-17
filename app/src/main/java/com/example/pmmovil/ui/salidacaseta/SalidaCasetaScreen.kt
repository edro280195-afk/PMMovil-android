package com.example.pmmovil.ui.salidacaseta

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.SalidaCajaItem
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.*

private val Mono = FontFamily.Monospace

@Composable
fun SalidaCasetaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {},
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: SalidaCasetaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 76) }

    HardwareScannerListener(viewModel::onScannerInput)

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.onScannerInput(it)
            onBarcodeScanConsumed()
        }
    }

    DisposableEffect(Unit) { onDispose { tone.release() } }

    LaunchedEffect(state.feedbackSeq) {
        if (state.feedbackSeq == 0) return@LaunchedEffect
        val isError = state.message.orEmpty().startsWith("✗") || state.message.orEmpty().startsWith("⚠")
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        runCatching {
            tone.startTone(
                if (isError) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK,
                if (isError) 150 else 120
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    val relacionWarning = state.relacionWarning
    if (relacionWarning != null) {
        RelacionWarningDialog(
            relacion = relacionWarning,
            caja = state.selected?.numCaja.orEmpty(),
            countdown = state.relacionCountdown,
            canConfirm = state.canConfirmRelacion,
            processing = state.isProcessing,
            onConfirm = viewModel::confirmRelacionWarning,
            onDismiss = viewModel::dismissRelacionWarning
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC)) // Base minimalista
            .drawBehind {
                val width = size.width
                val height = size.height
                // Destello superior derecho (Amarillo cálido/Miel)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFEF08A).copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(width * 0.95f, 0f),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(width * 0.95f, 0f)
                )
                // Destello intermedio izquierdo (Azul suave)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDBEAFE).copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(0f, height * 0.35f),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(0f, height * 0.35f)
                )
            },
        snackbarHost = { PmmSnackbarHost(snackbar) },
        topBar = {
            SalidaHeader(
                title = state.selected?.let { "Caja ${it.numCaja}" } ?: "Salida física de caja",
                subtitle = if (state.selected == null) "Cajas en patio sin salida" else "Verifica y registra la salida física",
                onBack = if (state.selected == null) onNavigateBack else viewModel::closeDetail,
                onRefresh = viewModel::refresh,
                refreshing = state.isLoading,
                showRefresh = state.selected == null
            )
        },
        bottomBar = {
            if (state.selected != null) {
                SalidaBottomBar(
                    enabled = state.puedeModificar && !state.isProcessing,
                    processing = state.isProcessing,
                    onCancel = viewModel::closeDetail,
                    onConfirm = viewModel::attemptSalida
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val contentWidth = if (maxWidth >= 840.dp) 1040.dp else maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentWidth)
                    .align(Alignment.TopCenter)
            ) {
                if (state.selected == null) {
                    SalidaListContent(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onClearQuery = viewModel::clearQuery,
                        onScanner = onNavigateToScanner,
                        onRefresh = viewModel::refresh,
                        onOpen = viewModel::openDetail
                    )
                } else {
                    SalidaDetailContent(
                        state = state,
                        onObservationsChange = viewModel::updateObservations
                    )
                }

                if (state.isProcessing && state.relacionWarning == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PmmBlueDeep)
                    }
                }
            }
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────

@Composable
private fun SalidaHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    showRefresh: Boolean
) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Botón Volver circular blanco con sombra
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier.size(42.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = PmmTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Textos centrales
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "CASETA · SALIDA FÍSICA DE CAJA",
                        color = PmmTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = title,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PmmTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Botón Refresh circular (si aplica)
                if (showRefresh) {
                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                        modifier = Modifier.size(42.dp)
                    ) {
                        IconButton(onClick = onRefresh, enabled = !refreshing) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = PmmBlueDeep,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Actualizar",
                                    tint = PmmTextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.width(42.dp)) // Espaciador para centrar títulos
                }
            }
        }
    }
}

// ── Lista ─────────────────────────────────────────────────────────────────

@Composable
private fun SalidaListContent(
    state: SalidaCasetaUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onScanner: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (SalidaCajaItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SalidaSearchPanel(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                onScanner = onScanner
            )
        }
        when {
            state.isLoading -> {
                items(6) { SalidaCajaSkeleton() }
            }
            state.filteredPending.isEmpty() -> {
                item {
                    SalidaEmptyState(
                        filtered = state.query.isNotBlank(),
                        onRefresh = onRefresh,
                        onClear = onClearQuery
                    )
                }
            }
            else -> {
                item {
                    SalidaSectionTitle(title = "CAJAS EN PATIO", count = state.filteredPending.size)
                }
                items(state.filteredPending, key = { it.casetaId }) { item ->
                    SalidaCajaRow(item = item, onClick = { onOpen(item) })
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun SalidaSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScanner: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {} // dummy to check drawing context
            .shadow(2.dp, CircleShape)
            .background(Color.White, CircleShape),
        singleLine = true,
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted) },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (query.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = PmmTextSecondary)
                    }
                }
                IconButton(onClick = onScanner) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = "Escanear", tint = Color(0xFF003DA5))
                }
            }
        },
        placeholder = { Text("Buscar caja, placas o cliente", color = PmmTextMuted) },
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun SalidaSectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = PmmTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Spacer(Modifier.width(8.dp))
        Surface(shape = RoundedCornerShape(999.dp), color = PmmNavyVariant) {
            Text(
                text = count.toString(),
                color = PmmBlueDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SalidaCajaRow(item: SalidaCajaItem, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEEF3FF)
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color(0xFF003DA5),
                        modifier = Modifier
                            .padding(10.dp)
                            .size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.numCaja.ifBlank { "S/N" },
                        color = PmmTextPrimary,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.nombreCliente.ifBlank { "Cliente sin nombre" },
                        color = PmmTextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TipoOpPill(item.tipoOp)
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFFF1F5FA))
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SalidaInlineField(label = "PLACAS", value = item.placas, mono = true, modifier = Modifier.weight(1f))
                SalidaInlineField(label = "LÍNEA CAJA", value = item.lineaCaja, modifier = Modifier.weight(1.3f))
                DiasChip(item.diasEnPatio)
            }
        }
    }
}

@Composable
private fun SalidaInlineField(
    label: String,
    value: String,
    mono: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF1F5FA), // Sólido gris
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label,
                color = PmmTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp
            )
            Text(
                text = value.ifBlank { "—" },
                color = PmmTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) Mono else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiasChip(dias: Int) {
    val alerta = dias > 5
    val color = if (alerta) PmmRed else PmmGreenDark
    Surface(shape = RoundedCornerShape(999.dp), color = color.copy(alpha = 0.12f)) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (alerta) Icons.Default.WarningAmber else Icons.Default.Schedule,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("$dias d", color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TipoOpPill(tipoOp: String) {
    val isExpo = tipoOp.equals("EXPO", ignoreCase = true)
    val bg = if (isExpo) PmmAmber.copy(alpha = 0.14f) else PmmNavyVariant
    val fg = if (isExpo) PmmAmberDark else PmmBlueDeep
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(
            text = tipoOp.ifBlank { "—" }.uppercase(),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = fg,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

// ── Detalle ───────────────────────────────────────────────────────────────

@Composable
private fun SalidaDetailContent(
    state: SalidaCasetaUiState,
    onObservationsChange: (String) -> Unit
) {
    val item = state.selected ?: return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { SalidaUnitHero(item) }
        item { SalidaDataPanel(item) }
        item {
            SalidaObservationsCard(
                value = state.observations,
                enabled = !state.isProcessing,
                onValueChange = onObservationsChange
            )
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun SalidaUnitHero(item: SalidaCajaItem) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Nº DE CAJA",
                        color = Color(0xFF003DA5),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = item.numCaja.ifBlank { "S/N" },
                        color = PmmTextPrimary,
                        fontFamily = Mono,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.nombreCliente.ifBlank { "Cliente sin nombre" },
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TipoOpPill(item.tipoOp)
                    DiasChip(item.diasEnPatio)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SalidaHeroData("PLACAS", item.placas, mono = true, modifier = Modifier.weight(1f))
                SalidaHeroData("Nº CAMIÓN", item.numCamion, mono = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SalidaHeroData(label: String, value: String, mono: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFFF1F5FA),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = Color(0xFF003DA5),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value.ifBlank { "—" },
                color = PmmTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) Mono else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SalidaDataPanel(item: SalidaCajaItem) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DATOS DE LA UNIDAD",
                color = PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            )
            val fields = listOf(
                Triple("Línea caja", item.lineaCaja, false),
                Triple("Tipo unidad", item.tipoUnidad, false),
                Triple("Tipo movimiento", item.tipoMovimiento, false),
                Triple("Fecha arribo", formatFechaArribo(item.fechaArribo), false),
                Triple("Planta", item.planta, false),
                Triple("Caseta ID", item.casetaId, true)
            )
            BoxWithConstraints {
                val twoCols = maxWidth >= 380.dp
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (twoCols) {
                        fields.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { (label, value, mono) ->
                                    SalidaDetailField(label, value, mono, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                             }
                        }
                    } else {
                        fields.forEach { (label, value, mono) ->
                            SalidaDetailField(label, value, mono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalidaDetailField(label: String, value: String, mono: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFF1F5FA),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                color = PmmTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value.ifBlank { "—" },
                color = PmmTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) Mono else FontFamily.Default,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SalidaObservationsCard(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFEEF3FF)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Color(0xFF003DA5),
                        modifier = Modifier.padding(8.dp).size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "OBSERVACIONES (OPCIONAL)",
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.0.sp
                )

            }
            Spacer(Modifier.height(12.dp))
            SalidaTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                minLines = 3,
                maxLines = 5,
                placeholder = "Notas u observaciones opcionales (opcional)…"
            )
        }
    }
}

@Composable
private fun SalidaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isError: Boolean = false,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        placeholder = { Text(placeholder, color = PmmTextMuted) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF1F5FA),
            unfocusedContainerColor = Color(0xFFF1F5FA),
            disabledContainerColor = Color(0xFFF8FAFC),
            focusedBorderColor = if (isError) Color(0xFFF97316) else Color.Transparent,
            unfocusedBorderColor = if (isError) Color(0xFFF97316).copy(alpha = 0.5f) else Color.Transparent,
            disabledBorderColor = Color.Transparent,
            focusedTextColor = PmmTextPrimary,
            unfocusedTextColor = PmmTextPrimary,
            disabledTextColor = PmmTextSecondary
        )
    )
}

// ── Barra inferior ──────────────────────────────────────────────────────────

@Composable
private fun SalidaBottomBar(
    enabled: Boolean,
    processing: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !processing,
                modifier = Modifier
                    .weight(0.8f)
                    .height(48.dp),
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmTextSecondary)
            ) {
                Text("Cancelar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Button(
                onClick = onConfirm,
                enabled = enabled,
                modifier = Modifier
                    .weight(1.4f)
                    .height(48.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF15803D),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFFE2E8F0),
                    disabledContentColor = PmmTextMuted
                )
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Dar salida física", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

// ── Diálogo de advertencia de relación ──────────────────────────────────────

@Composable
private fun RelacionWarningDialog(
    relacion: String,
    caja: String,
    countdown: Int,
    canConfirm: Boolean,
    processing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!processing) onDismiss() },
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = PmmAmber) },
        title = { Text("La caja ya tiene relación", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "La caja ${caja.ifBlank { "seleccionada" }} ya tiene la relación $relacion (prefijo 01).",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Confirma que deseas darle salida de todas formas.",
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !processing,
                colors = ButtonDefaults.buttonColors(containerColor = PmmAmberDark, contentColor = Color.White)
            ) {
                Text(
                    text = if (canConfirm) "Dar salida física" else "Dar salida física ($countdown)",
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !processing) {
                Text("Cancelar")
            }
        }
    )
}

// ── Skeleton / vacío ────────────────────────────────────────────────────────

@Composable
private fun SalidaCajaSkeleton() {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Skeleton(width = 150.dp, height = 18.dp)
            Skeleton(width = 240.dp, height = 12.dp)
            HorizontalDivider(color = PmmNavyBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(modifier = Modifier.weight(1f), height = 32.dp)
                Skeleton(modifier = Modifier.weight(1f), height = 32.dp)
            }
        }
    }
}

@Composable
private fun SalidaEmptyState(
    filtered: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp, horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = PmmNavyVariant) {
            Icon(
                imageVector = if (filtered) Icons.Default.Search else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.padding(18.dp).size(36.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (filtered) "Sin coincidencias" else "Sin cajas en patio por salir",
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (filtered) {
                "Limpia la búsqueda o escanea otra caja."
            } else {
                "Cuando una caja arribe a patio y quede sin salida, aparecerá aquí."
            },
            color = PmmTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(16.dp))
        if (filtered) {
            OutlinedButton(onClick = onClear) { Text("Limpiar búsqueda", fontWeight = FontWeight.Bold) }
        } else {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Actualizar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatFechaArribo(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return "—"
    val datePart = clean.substringBefore("T")
    val timePart = clean.substringAfter("T", "").take(5)
    val parts = datePart.split("-")
    return if (parts.size == 3) {
        "${parts[2]}/${parts[1]} $timePart".trim()
    } else {
        clean.replace("T", " ").take(16)
    }
}
