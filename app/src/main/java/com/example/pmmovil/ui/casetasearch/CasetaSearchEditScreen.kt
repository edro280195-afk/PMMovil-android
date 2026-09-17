package com.example.pmmovil.ui.casetasearch

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

private val Mono = FontFamily.Monospace

@Composable
fun CasetaSearchEditScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    onEditRecord: (String) -> Unit,
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: CasetaSearchEditViewModel = hiltViewModel()
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

    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }

    LaunchedEffect(state.feedbackSeq) {
        if (state.feedbackSeq == 0) return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_NACK, 140) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFEF08A).copy(alpha = 0.35f), Color.Transparent),
                        center = Offset(w * 0.95f, 0f),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(w * 0.95f, 0f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDBEAFE).copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(0f, h * 0.35f),
                        radius = w * 0.85f
                    ),
                    radius = w * 0.85f,
                    center = Offset(0f, h * 0.35f)
                )
            },
        snackbarHost = { PmmSnackbarHost(hostState = snackbar) },
        topBar = {
            CasetaSearchHeader(
                onBack = onNavigateBack,
                onRefresh = viewModel::searchNow,
                refreshing = state.isLoading
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val contentWidth = if (maxWidth >= 760.dp) 680.dp else maxWidth
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = contentWidth)
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CasetaSearchPanel(
                        query = state.query,
                        isLoading = state.isLoading,
                        onQueryChange = viewModel::setQuery,
                        onSearch = viewModel::searchNow,
                        onClear = viewModel::clearQuery,
                        onScanner = onNavigateToScanner
                    )
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        val sectionTitle = if (state.query.isBlank()) "ULTIMOS 20 MOVIMIENTOS" else "RESULTADOS"
                        Text(
                            text = sectionTitle,
                            color = PmmTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFE2E8F0)) {
                            Text(
                                text = state.results.size.toString(),
                                color = Color(0xFF003DA5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                when {
                    state.isLoading && state.results.isEmpty() -> {
                        items(5) { CasetaSearchSkeleton() }
                    }
                    state.results.isEmpty() -> {
                        item {
                            CasetaSearchEmptyState(
                                hasQuery = state.query.isNotBlank(),
                                onRefresh = viewModel::searchNow,
                                onClear = viewModel::clearQuery
                            )
                        }
                    }
                    else -> {
                        items(state.results, key = { it.casetaid }) { item ->
                            CasetaSearchResultCard(
                                item = item,
                                onClick = { onEditRecord(item.casetaid) },
                                onPrint = { viewModel.printCasetaLabel(item.casetaid, item.nequipo) }
                            )
                        }
                    }
                }

                item { Spacer(Modifier.navigationBarsPadding().height(8.dp)) }
            }
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────

@Composable
private fun CasetaSearchHeader(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.size(42.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = PmmTextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "CASETA · REGISTROS",
                color = PmmTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Buscar / Editar",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PmmTextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.size(42.dp)
        ) {
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PmmBlueDeep, strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Actualizar", tint = PmmTextPrimary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ── Panel de busqueda ──────────────────────────────────────────────────────

@Composable
private fun CasetaSearchPanel(
    query: String,
    isLoading: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onScanner: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, CircleShape)
            .background(Color.White, CircleShape),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted)
        },
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
                IconButton(onClick = onScanner, enabled = !isLoading) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = Color(0xFF003DA5))
                }
            }
        },
        placeholder = { Text("Caja, placa, cliente o folio", color = PmmTextMuted) },
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

// ── Tarjeta de resultado ───────────────────────────────────────────────────

@Composable
private fun CasetaSearchResultCard(
    item: CasetaEntradaBusquedaItem,
    onClick: () -> Unit,
    onPrint: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(shape = CircleShape, color = Color(0xFFEEF3FF)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = Color(0xFF003DA5),
                        modifier = Modifier.padding(10.dp).size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.casetaid.ifBlank { "Sin folio" },
                        color = PmmTextPrimary,
                        fontFamily = Mono,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.cliente.ifBlank { "Cliente sin nombre" },
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OperationPill(item.tipoOp)
                IconButton(onClick = onPrint, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Reimprimir Etiqueta",
                        tint = PmmTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(color = Color(0xFFF1F5FA))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ResultField(label = "CAJA", value = item.nequipo.ifBlank { "Pendiente" }, mono = true, modifier = Modifier.weight(1f))
                ResultField(label = "PLACAS", value = item.placas.ifBlank { "Pendiente" }, mono = true, modifier = Modifier.weight(1f))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = formatSearchDate(item.farribo), color = PmmTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Normal)
                if (item.fotos > 0) {
                    Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFEEF3FF)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF003DA5), modifier = Modifier.size(13.dp))
                            Text(item.fotos.toString(), color = Color(0xFF003DA5), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ── ResultField ────────────────────────────────────────────────────────────

@Composable
private fun ResultField(
    label: String,
    value: String,
    mono: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Color(0xFFF1F5FA), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = label, color = PmmTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.7.sp)
            Text(
                text = value,
                color = PmmTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (mono) Mono else FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Pills ──────────────────────────────────────────────────────────────────

@Composable
private fun OperationPill(tipoOp: String) {
    val short = shortOperation(tipoOp)
    val isExpo = short == "EXPO"
    val bg = if (isExpo) Color(0xFFFFFBEB) else Color(0xFFEEF3FF)
    val fg = if (isExpo) PmmAmberDark else Color(0xFF003DA5)
    Surface(shape = RoundedCornerShape(999.dp), color = bg) {
        Text(text = short, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// ── Skeleton ───────────────────────────────────────────────────────────────

@Composable
private fun CasetaSearchSkeleton() {
    Surface(color = Color.White, shape = RoundedCornerShape(20.dp), shadowElevation = 1.dp) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 40.dp, height = 40.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Skeleton(width = 120.dp, height = 16.dp)
                    Skeleton(width = 190.dp, height = 12.dp)
                }
            }
            HorizontalDivider(color = Color(0xFFF1F5FA))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(modifier = Modifier.weight(1f), height = 40.dp)
                Skeleton(modifier = Modifier.weight(1f), height = 40.dp)
            }
        }
    }
}

// ── Estado vacio ───────────────────────────────────────────────────────────

@Composable
private fun CasetaSearchEmptyState(
    hasQuery: Boolean,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = Color(0xFFEEF3FF)) {
            Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color(0xFF003DA5), modifier = Modifier.padding(20.dp).size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (hasQuery) "Sin coincidencias" else "Sin registros recientes",
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (hasQuery) "Prueba con caja, placas, cliente o folio." else "Actualiza para consultar los ultimos registros de caseta.",
            color = PmmTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(20.dp))
        if (hasQuery) {
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Text("Limpiar busqueda", fontWeight = FontWeight.Bold, color = PmmTextSecondary)
            }
        } else {
            Button(
                onClick = onRefresh,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003DA5))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Actualizar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────

private fun shortOperation(value: String): String {
    val clean = value.trim().uppercase()
    return when {
        clean.startsWith("EXP") || clean == "EXPO" -> "EXPO"
        clean.startsWith("IMP") || clean == "IMPO" -> "IMPO"
        clean.isNotBlank() -> clean.take(4)
        else -> "--"
    }
}

private fun formatSearchDate(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return "Sin fecha"
    val normalized = clean.replace("T", " ")
    val datePart = normalized.substringBefore(" ")
    val timePart = normalized.substringAfter(" ", "").take(5)
    val pieces = datePart.split("-")
    val date = if (pieces.size == 3) "${pieces[2]}/${pieces[1]}" else datePart
    return listOf(date, timePart).filter(String::isNotBlank).joinToString(" ")
}
