package com.example.pmmovil.ui.relacion

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.model.RelacionPendiente
import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.data.model.SalidaRelacion
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
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

private enum class LabelFilter { ALL, SCANNED, PENDING, BLOCKED }

@Composable
fun RelacionScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit = {},
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: RelacionViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 72) }

    HardwareScannerListener(viewModel::openDetail)
    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.openDetail(it)
            onBarcodeScanConsumed()
        }
    }

    LaunchedEffect(state.feedbackSeq) {
        if (state.feedbackSeq == 0) return@LaunchedEffect
        if (state.audioEnabled) {
            val error = state.message.orEmpty().startsWith("✗") || state.message.orEmpty().startsWith("⚠")
            runCatching {
                tone.startTone(
                    if (error) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK,
                    130
                )
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    state.confirmation?.let {
        RelacionConfirmationDialog(
            confirmation = it,
            processing = state.isProcessing,
            onConfirm = viewModel::confirmAction,
            onDismiss = viewModel::dismissConfirmation
        )
    }

    val detail = state.detail
    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbar) },
        topBar = {
            RelacionHeader(
                title = detail?.relacion?.relno ?: "Relaciones de Salida",
                subtitle = if (detail == null) {
                    "Supervisión y autorización de cargas"
                } else {
                    "Revisión operativa antes de autorizar"
                },
                isDetail = detail != null,
                audioEnabled = state.audioEnabled,
                onBack = if (detail == null) onNavigateBack else viewModel::closeDetail,
                onAudio = viewModel::toggleAudio,
                onRefresh = viewModel::refresh
            )
        },
        bottomBar = {
            detail?.relacion?.let { relation ->
                val canAuthorize = RelacionRules.canAuthorize(relation, state.currentUser)
                RelationAuthorizationBar(
                    enabled = canAuthorize && viewModel.canModify() && !state.isProcessing,
                    processing = state.isProcessing,
                    status = RelacionRules.statusLabel(relation, state.currentUser),
                    onAuthorize = { viewModel.requestAction(RelacionAction.AUTHORIZE) }
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val maxContentWidth = if (maxWidth >= 840.dp) 1100.dp else maxWidth
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = maxContentWidth)
                    .align(Alignment.TopCenter)
            ) {
                if (detail == null) {
                    RelationListContent(state, viewModel, onNavigateToScanner)
                } else {
                    RelationDetailContent(state, viewModel)
                }
                if (state.isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = PmmBlueDeep,
                            strokeWidth = 4.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RelacionHeader(
    title: String,
    subtitle: String,
    isDetail: Boolean,
    audioEnabled: Boolean,
    onBack: () -> Unit,
    onAudio: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        color = PmmNavyDark,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(PmmNavyDark, PmmBlueDeep)))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        title,
                        color = Color.White,
                        fontSize = if (isDetail) 20.sp else 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = if (isDetail) FontFamily.Monospace else FontFamily.Default,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isDetail) PmmYellow else Color.White.copy(alpha = 0.2f)
                    ) {
                        Text(
                            if (isDetail) "DETALLE" else "SUPERVISOR",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = if (isDetail) PmmNavyDark else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1
                        )
                    }
                }
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            IconButton(
                onClick = onAudio,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    if (audioEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                    contentDescription = if (audioEnabled) "Desactivar audio" else "Activar audio",
                    tint = if (audioEnabled) PmmYellow else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun RelationListContent(
    state: RelacionUiState,
    viewModel: RelacionViewModel,
    onNavigateToScanner: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        val totalCount = state.pending.size + state.gatePending.size
        val readyCount = state.pending.count {
            RelacionRules.canAuthorize(
                SalidaRelacion(
                    relno = it.relno,
                    estatus = "LISTA",
                    estatusCarga = it.estatusCarga,
                    usucargaMcia = it.usucargaMcia
                ),
                state.currentUser
            )
        }
        val blockedCount = state.pending.count { it.estatusCarga.uppercase().contains("BLOQUEO") }

        RelationStatsStrip(
            total = totalCount,
            ready = readyCount,
            blocked = blockedCount
        )

        RelationTabs(
            selected = state.tab,
            authorizationCount = state.pending.size,
            gateCount = state.gatePending.size,
            onSelect = viewModel::setTab
        )

        if (state.tab == RelacionTab.AUTHORIZATION) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            "Buscar relación, caja o cliente...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = PmmTextMuted
                        )
                    },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = PmmBgSurface,
                        unfocusedContainerColor = PmmBgSurface,
                        focusedBorderColor = PmmBlueDeep,
                        unfocusedBorderColor = PmmNavyBorder
                    )
                )
                Button(
                    onClick = onNavigateToScanner,
                    modifier = Modifier.height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmNavyVariant,
                        contentColor = PmmBlueDeep
                    ),
                    border = BorderStroke(1.dp, PmmNavyBorder)
                ) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = "Escanear QR con cámara",
                        tint = PmmBlueDeep
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Escáner",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    )
                }
            }
        }

        when {
            state.isLoading -> RelationListSkeleton()
            state.tab == RelacionTab.AUTHORIZATION && state.pending.isEmpty() ->
                RelationEmptyState(
                    icon = Icons.Default.CheckCircle,
                    title = "Sin relaciones por autorizar",
                    message = "Las cargas pendientes de autorización aparecerán aquí."
                )
            state.tab == RelacionTab.GATE && state.gatePending.isEmpty() ->
                RelationEmptyState(
                    icon = Icons.Default.LocalShipping,
                    title = "Sin salidas pendientes",
                    message = "No hay relaciones autorizadas esperando salida en caseta."
                )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.tab == RelacionTab.AUTHORIZATION) {
                    items(state.pending, key = { it.relno }) { item ->
                        RelationPendingRow(item) { viewModel.openDetail(item.relno) }
                    }
                } else {
                    items(state.gatePending, key = { it.relno }) { item ->
                        GatePendingRow(item)
                    }
                }
            }
        }
    }
}

@Composable
private fun RelationStatsStrip(total: Int, ready: Int, blocked: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgSurface)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard("TOTAL", total.toString(), PmmTextPrimary, PmmBgCard, Modifier.weight(1f))
        StatCard("LISTAS", ready.toString(), PmmGreen, PmmGreen.copy(alpha = 0.1f), Modifier.weight(1f))
        StatCard("BLOQUEADAS", blocked.toString(), PmmRed, PmmRed.copy(alpha = 0.1f), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, textColor: Color, bgColor: Color, modifier: Modifier) {
    Surface(
        modifier = modifier,
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                color = textColor,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                label,
                color = PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp
            )
        }
    }
}

@Composable
private fun RelationTabs(
    selected: RelacionTab,
    authorizationCount: Int,
    gateCount: Int,
    onSelect: (RelacionTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgSurface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RelationTabButton(
            text = "Por autorizar",
            icon = Icons.Default.Verified,
            count = authorizationCount,
            selected = selected == RelacionTab.AUTHORIZATION,
            onClick = { onSelect(RelacionTab.AUTHORIZATION) },
            modifier = Modifier.weight(1f)
        )
        RelationTabButton(
            text = "Pendientes caseta",
            icon = Icons.Default.LocalShipping,
            count = gateCount,
            selected = selected == RelacionTab.GATE,
            onClick = { onSelect(RelacionTab.GATE) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RelationTabButton(
    text: String,
    icon: ImageVector,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        color = if (selected) PmmBlueDeep else PmmNavyVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) PmmBlueDeep else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else PmmBlueDeep,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = if (selected) Color.White else PmmBlueDeep,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                shape = CircleShape,
                color = if (selected) Color.White.copy(alpha = 0.25f) else PmmBgSurface
            ) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    color = if (selected) Color.White else PmmBlueDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun RelationPendingRow(item: RelacionPendiente, onClick: () -> Unit) {
    val statusColor = relationStatusColor(item.estatusCarga)
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = PmmBgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, PmmNavyBorder),
        shadowElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    item.relno,
                    color = PmmTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
                )
                RelationStatusBadge(
                    item.estatusCarga.ifBlank { "Pendiente" },
                    statusColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.cliente.ifBlank { "CLIENTE NO DISPONIBLE" },
                color = PmmBlueDeep,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PmmBgCard,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Caja: ${item.caja.ifBlank { "N/D" }}",
                        color = PmmTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Etiquetas: ${item.etiquetasCount}",
                        color = PmmTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Cargó: ${item.usucargaMcia.ifBlank { "N/D" }}",
                        color = PmmTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "Ver detalle",
                    color = PmmBlueDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GatePendingRow(item: CasetaPendiente) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PmmBgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, PmmNavyBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = PmmNavyVariant,
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = PmmBlueDeep,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        item.relno,
                        color = PmmTextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    RelationStatusBadge("CASETA", PmmAmber)
                }
                Text(
                    item.cliente.ifBlank { "CLIENTE NO DISPONIBLE" },
                    color = PmmBlueDeep,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp)
                )
                Text(
                    "Caja ${item.caja.ifBlank { "N/D" }} · Autorizó: ${item.usuAutoriza.ifBlank { "N/D" }}",
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationDetailContent(state: RelacionUiState, viewModel: RelacionViewModel) {
    val response = state.detail ?: return
    val relation = response.relacion ?: return
    val labels = response.etiquetas.orEmpty()
    val scanned = labels.count(SalidaEtiqueta::escaneado)
    val blocked = labels.count(RelacionRules::isBlocked)
    val pending = (labels.size - scanned).coerceAtLeast(0)
    val progress = if (labels.isEmpty()) 0f else scanned.toFloat() / labels.size
    val canAuthorize = RelacionRules.canAuthorize(relation, state.currentUser)
    val relationBlocked = relation.estatusCarga.uppercase().contains("BLOQUEO") || blocked > 0

    var selectedFilter by remember { mutableStateOf(LabelFilter.ALL) }
    var labelQuery by remember { mutableStateOf("") }

    val filteredLabels = remember(labels, selectedFilter, labelQuery) {
        labels.filter { label ->
            val matchesFilter = when (selectedFilter) {
                LabelFilter.ALL -> true
                LabelFilter.SCANNED -> label.escaneado && !RelacionRules.isBlocked(label)
                LabelFilter.PENDING -> !label.escaneado && !RelacionRules.isBlocked(label)
                LabelFilter.BLOCKED -> RelacionRules.isBlocked(label)
            }
            val matchesSearch = labelQuery.isBlank() ||
                label.textoPagina.contains(labelQuery, ignoreCase = true) ||
                label.textoEtiqueta.contains(labelQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DetailStatusBanner(
                blocked = relationBlocked,
                canAuthorize = canAuthorize,
                status = RelacionRules.statusLabel(relation, state.currentUser)
            )
        }
        item {
            Surface(
                color = PmmBgSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.5.dp, PmmNavyBorder),
                shadowElevation = 3.dp
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "AVANCE DE CARGA",
                            color = PmmTextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "${(progress * 100).toInt()}%",
                            color = PmmBlueDeep,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp),
                        color = if (relationBlocked) PmmRed else PmmGreen,
                        trackColor = PmmNavyVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DetailMetric("Escaneadas", "$scanned/${labels.size}", PmmGreen, Modifier.weight(1f))
                        DetailMetric("Pendientes", pending.toString(), PmmAmber, Modifier.weight(1f))
                        DetailMetric("Bloqueadas", blocked.toString(), PmmRed, Modifier.weight(1f))
                    }
                }
            }
        }

        item { RelationDataPanel(relation) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "ETIQUETAS DE LA RELACIÓN (${labels.size})",
                    color = PmmTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == LabelFilter.ALL,
                            onClick = { selectedFilter = LabelFilter.ALL },
                            label = { Text("Todas (${labels.size})", fontWeight = FontWeight.Black, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PmmBlueDeep,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == LabelFilter.SCANNED,
                            onClick = { selectedFilter = LabelFilter.SCANNED },
                            label = { Text("Escaneadas ($scanned)", fontWeight = FontWeight.Black, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PmmGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == LabelFilter.PENDING,
                            onClick = { selectedFilter = LabelFilter.PENDING },
                            label = { Text("Pendientes ($pending)", fontWeight = FontWeight.Black, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PmmAmber,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedFilter == LabelFilter.BLOCKED,
                            onClick = { selectedFilter = LabelFilter.BLOCKED },
                            label = { Text("Bloqueadas ($blocked)", fontWeight = FontWeight.Black, fontSize = 13.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PmmRed,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        if (filteredLabels.isEmpty()) {
            item {
                RelationEmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "Sin etiquetas coincidentes",
                    message = "No se encontraron etiquetas con el filtro seleccionado."
                )
            }
        } else {
            items(filteredLabels, key = { "${it.id}-${it.textoEtiqueta}" }) { label ->
                RelationLabelRow(label)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (relationBlocked && viewModel.canModify()) {
                    Button(
                        onClick = { viewModel.requestAction(RelacionAction.UNLOCK) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PmmYellow,
                            contentColor = PmmNavyDark
                        )
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Desbloquear tras revisión física",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                    }
                }
                if (viewModel.canModify()) {
                    OutlinedButton(
                        onClick = { viewModel.requestAction(RelacionAction.RETURN_TO_WAITING) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, PmmNavyBorder)
                    ) {
                    Icon(
                        Icons.Default.SettingsBackupRestore,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Retornar a espera",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    }
                }
                if (viewModel.canDelete()) {
                    OutlinedButton(
                        onClick = { viewModel.requestAction(RelacionAction.DELETE_LOAD) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmRed),
                        border = BorderStroke(1.5.dp, PmmRed.copy(alpha = 0.5f))
                    ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Eliminar carga registrada",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp
                    )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailStatusBanner(
    blocked: Boolean,
    canAuthorize: Boolean,
    status: String
) {
    val color = when {
        blocked -> PmmRed
        canAuthorize -> PmmGreen
        else -> PmmAmber
    }
    val icon = when {
        blocked -> Icons.Default.Lock
        canAuthorize -> Icons.Default.CheckCircle
        else -> Icons.Default.WarningAmber
    }
    val message = when {
        blocked -> "Carga bloqueada. Requiere revisión física antes de desbloquear."
        !canAuthorize -> status
        else -> "Carga completa y lista para autorización del supervisor."
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, color.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                message,
                color = PmmTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 21.sp
            )
        }
    }
}

@Composable
private fun DetailMetric(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = PmmBgCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                value,
                color = color,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                label,
                color = PmmTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun RelationDataPanel(relation: SalidaRelacion) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.5.dp, PmmNavyBorder),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RelationField("Cliente", relation.cliente.ifBlank { "N/D" }, Modifier.fillMaxWidth(), isProminent = true)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RelationField("Caja N°", relation.caja.ifBlank { "N/D" }, Modifier.weight(1f), isProminent = true)
                RelationField("Estado Carga", relation.estatusCarga.ifBlank { relation.estatus }, Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                RelationField("Cargó", relation.usucargaMcia.ifBlank { "N/D" }, Modifier.weight(1f))
                RelationField("Planta", relation.planta.ifBlank { "N/D" }, Modifier.weight(1f))
                RelationField("Versión Hoja", relation.versionHoja.ifBlank { "N/D" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RelationField(label: String, value: String, modifier: Modifier = Modifier, isProminent: Boolean = false) {
    Column(modifier = modifier) {
        Text(
            label.uppercase(),
            color = PmmTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp
        )
        Text(
            value,
            color = if (isProminent) PmmBlueDeep else PmmTextPrimary,
            fontSize = if (isProminent) 16.sp else 15.sp,
            fontWeight = FontWeight.Black,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
private fun RelationLabelRow(label: SalidaEtiqueta) {
    val blocked = RelacionRules.isBlocked(label)
    val color = when {
        blocked -> PmmRed
        label.escaneado -> PmmGreen
        else -> PmmTextMuted
    }
    Surface(
        color = if (blocked) PmmRed.copy(alpha = 0.06f) else PmmBgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, if (blocked) PmmRed.copy(alpha = 0.45f) else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when {
                            blocked -> Icons.Default.Lock
                            label.escaneado -> Icons.Default.Check
                            else -> Icons.Default.Inventory2
                        },
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label.textoPagina.ifBlank { label.textoEtiqueta },
                    color = PmmTextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                label.timeScan?.let {
                    Text(
                        "Escaneado: ${formatTime(it)}",
                        color = PmmTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            RelationStatusBadge(
                when {
                    blocked -> "BLOQUEO"
                    label.estatus.isNotBlank() -> label.estatus
                    label.escaneado -> "CARGADO"
                    else -> "PENDIENTE"
                },
                color
            )
        }
    }
}

@Composable
private fun RelationAuthorizationBar(
    enabled: Boolean,
    processing: Boolean,
    status: String,
    onAuthorize: () -> Unit
) {
    Surface(
        color = PmmBgSurface,
        shadowElevation = 14.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (enabled) "Lista para autorizar" else "Autorización no disponible",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
                if (!enabled) {
                    Text(
                        status,
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Button(
                onClick = onAuthorize,
                enabled = enabled,
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmGreen,
                    contentColor = Color.White
                )
            ) {
                if (processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.5.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "AUTORIZAR RELACIÓN",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun RelacionConfirmationDialog(
    confirmation: RelacionConfirmation,
    processing: Boolean,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var reviewed by remember(confirmation) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                when (confirmation.action) {
                    RelacionAction.AUTHORIZE -> Icons.Default.CheckCircle
                    RelacionAction.UNLOCK -> Icons.Default.LockOpen
                    RelacionAction.RETURN_TO_WAITING -> Icons.Default.SettingsBackupRestore
                    RelacionAction.DELETE_LOAD -> Icons.Default.DeleteOutline
                },
                contentDescription = null,
                tint = if (confirmation.action == RelacionAction.DELETE_LOAD) PmmRed else PmmBlueDeep,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                confirmation.title,
                fontWeight = FontWeight.Black,
                fontSize = 19.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    confirmation.message,
                    color = PmmTextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 21.sp
                )
                if (confirmation.requiresPhysicalReview) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reviewed = !reviewed },
                        color = if (reviewed) PmmNavyVariant else PmmBgCard,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, if (reviewed) PmmBlueDeep else PmmNavyBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = reviewed,
                                onCheckedChange = { reviewed = it }
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Revisé físicamente la carga y confirmo que la mercancía puede liberarse.",
                                color = PmmTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reviewed) },
                enabled = !processing && (!confirmation.requiresPhysicalReview || reviewed),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmation.action == RelacionAction.DELETE_LOAD) {
                        PmmRed
                    } else {
                        PmmBlueDeep
                    },
                    contentColor = Color.White
                )
            ) {
                Text(
                    confirmation.confirmLabel,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancelar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    )
}

@Composable
private fun RelationStatusBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun RelationListSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(5) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                color = PmmBgSurface,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Skeleton(width = 180.dp, height = 20.dp)
                    Skeleton(width = 280.dp, height = 16.dp)
                    Skeleton(height = 14.dp)
                }
            }
        }
    }
}

@Composable
private fun RelationEmptyState(icon: ImageVector, title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = PmmNavyVariant
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier
                    .padding(20.dp)
                    .size(40.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            message,
            color = PmmTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

private fun relationStatusColor(status: String): Color {
    val clean = status.uppercase()
    return when {
        clean.contains("BLOQU") || clean.contains("ERROR") -> PmmRed
        clean.contains("INCOMPLETA") || clean.contains("DIFERENCIA") ||
            clean.contains("ESPERA") -> PmmAmber
        else -> PmmGreen
    }
}

private fun formatTime(value: String): String {
    val time = value.substringAfter('T', value.substringAfter(' ', value)).take(5)
    return time.takeIf(String::isNotBlank).orEmpty()
}
