package com.example.pmmovil.ui.caseta

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pmmovil.data.model.CasetaDetalle
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.ui.camera.LocalEvidenceCameraScreen
import com.example.pmmovil.ui.camera.LocalEvidencePhoto
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import java.io.File

private val Mono = FontFamily.Monospace

@Composable
fun CasetaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: CasetaViewModel = hiltViewModel()
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
        val isError = state.message.orEmpty().startsWith("✗") ||
            state.message.orEmpty().startsWith("⚠")
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        runCatching {
            tone.startTone(
                if (isError) ToneGenerator.TONE_PROP_NACK else ToneGenerator.TONE_PROP_ACK,
                if (isError) 150 else 120
            )
        }
    }

    var cameraVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    val confirmationDetail = state.detail
    if (state.confirmVisible && confirmationDetail != null) {
        CasetaConfirmDialog(
            detail = confirmationDetail,
            observations = state.confirmObservations,
            processing = state.isProcessing,
            onObservationsChange = viewModel::updateConfirmObservations,
            onDismiss = viewModel::dismissConfirm,
            onConfirm = viewModel::confirmSalida
        )
    }

    if (cameraVisible) {
        LocalEvidenceCameraScreen(
            title = "Fotografías de Salida",
            subtitle = "Captura evidencia de la unidad",
            photos = state.localPhotoPaths.mapIndexed { i, p ->
                LocalEvidencePhoto(
                    id = p,
                    model = File(p),
                    label = "Foto ${i+1}",
                    status = "Lista",
                    canDelete = state.puedeBorrar
                )
            },
            // Sin tope no era seguro: "Confirmar salida" manda TODAS las fotos
            // juntas en una sola peticion transaccional (a diferencia de
            // Entrada, que sube cada una por separado). 20 fotos de alta
            // calidad caben con margen; ilimitadas podian producir un solo
            // request de 20-30 MB justo al confirmar. Ver PhotoPipeline.
            maxPhotos = 20,
            directoryName = "salidas",
            filePrefix = "salida_",
            canCapture = state.puedeAgregar,
            onPhotoCaptured = viewModel::addPhoto,
            onDeletePhoto = { photo ->
                viewModel.removePhoto(photo.id)
            },
            onClose = { cameraVisible = false }
        )
        return
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbar) },
        topBar = {
            CasetaHeader(
                title = state.selectedRelno ?: "Salida de relación autorizada",
                subtitle = if (state.selectedRelno == null) {
                    "Relaciones autorizadas para salir"
                } else {
                    "Confirmación final de patio"
                },
                onBack = if (state.selectedRelno == null) onNavigateBack else viewModel::closeDetail,
                onRefresh = viewModel::refresh,
                refreshing = state.isLoading
            )
        },
        bottomBar = {
            val detail = state.detail
            if (detail != null) {
                CasetaBottomBar(
                    enabled = state.puedeModificar && CasetaRules.isReadyForExit(detail) && !state.isProcessing,
                    processing = state.isProcessing,
                    onBack = viewModel::closeDetail,
                    onConfirm = viewModel::requestConfirm
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
                if (state.selectedRelno == null) {
                    CasetaListContent(
                        state = state,
                        onQueryChange = viewModel::setQuery,
                        onClearQuery = viewModel::clearQuery,
                        onScanner = onNavigateToScanner,
                        onRefresh = viewModel::refresh,
                        onOpen = viewModel::openDetail
                    )
                } else {
                    CasetaDetailContent(
                        state = state,
                        onBack = viewModel::closeDetail,
                        onConfirm = viewModel::requestConfirm,
                        onOpenCamera = { cameraVisible = true },
                        onRemovePhoto = viewModel::removePhoto
                    )
                }

                if (state.isProcessing) {
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

@Composable
private fun CasetaHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean
) {
    Surface(color = PmmNavyDark, shadowElevation = 5.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BODEGA",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.76f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, enabled = !refreshing) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Actualizar",
                    tint = if (refreshing) Color.White.copy(alpha = 0.42f) else Color.White
                )
            }
        }
    }
}

@Composable
private fun CasetaListContent(
    state: CasetaUiState,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onScanner: () -> Unit,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CasetaQueuePanel(
                total = state.pending.size,
                visible = state.filteredPending.size
            )
        }
        item {
            CasetaSearchPanel(
                query = state.query,
                onQueryChange = onQueryChange,
                onClear = onClearQuery,
                onScanner = onScanner,
                onSearch = {
                    val target = state.filteredPending.firstOrNull()?.relno
                        ?: state.query.trim()
                    if (target.isNotBlank()) onOpen(target)
                }
            )
        }
        when {
            state.isLoading -> {
                items(6) {
                    CasetaPendingSkeleton()
                }
            }
            state.filteredPending.isEmpty() -> {
                item {
                    CasetaEmptyState(
                        filtered = state.query.isNotBlank(),
                        onRefresh = onRefresh,
                        onClear = onClearQuery
                    )
                }
            }
            else -> {
                item {
                    CasetaSectionTitle(
                        title = "RELACIONES AUTORIZADAS PARA SALIDA",
                        count = state.filteredPending.size
                    )
                }
                items(state.filteredPending, key = { it.relno }) { item ->
                    CasetaPendingRow(
                        item = item,
                        onClick = { onOpen(item.relno) }
                    )
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun CasetaQueuePanel(total: Int, visible: Int) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = PmmNavyDark,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(PmmNavyDark, PmmBlueDeep)
                    )
                )
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Garage,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(13.dp).size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Control de salida",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "Escanea o selecciona una relación autorizada.",
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 12.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = visible.toString(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (visible == total) "pendientes" else "de $total",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun CasetaSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onScanner: () -> Unit,
    onSearch: () -> Unit
) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Lector físico listo",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onScanner) {
                    Text("Cámara", fontWeight = FontWeight.Black)
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = "Limpiar")
                        }
                    }
                },
                placeholder = { Text("Buscar relación, caja, cliente o autorizó") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder,
                    focusedContainerColor = PmmBgSurface,
                    unfocusedContainerColor = PmmBgSurface
                )
            )
        }
    }
}

@Composable
private fun CasetaSectionTitle(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            color = PmmTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp
        )
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
private fun CasetaPendingRow(item: CasetaPendiente, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = PmmNavyVariant) {
                    Icon(
                        imageVector = Icons.Default.LocalShipping,
                        contentDescription = null,
                        tint = PmmBlueDeep,
                        modifier = Modifier.padding(10.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.relno,
                        color = PmmTextPrimary,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = item.cliente.ifBlank { "Cliente sin nombre" },
                        color = PmmTextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                CasetaStatusBadge("AUTORIZADA", PmmGreen)
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmTextMuted
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = PmmNavyBorder)
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CasetaInlineInfo(
                    icon = Icons.Default.DirectionsCar,
                    label = "Caja",
                    value = item.caja.ifBlank { "N/D" },
                    modifier = Modifier.weight(1f)
                )
                CasetaInlineInfo(
                    icon = Icons.Default.Verified,
                    label = "Autorizó",
                    value = item.usuAutoriza.ifBlank { "N/D" },
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Autorizada: ${formatDateTime(item.autorizaCarga)}",
                color = PmmTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 9.dp)
            )
        }
    }
}

@Composable
private fun CasetaInlineInfo(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Column {
            Text(
                text = label.uppercase(),
                color = PmmTextMuted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value,
                color = PmmTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CasetaDetailContent(
    state: CasetaUiState,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onOpenCamera: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    val detail = state.detail
    if (state.isLoading) {
        CasetaDetailSkeleton()
        return
    }
    if (detail == null) {
        CasetaBlockedContent(
            relno = state.selectedRelno.orEmpty(),
            message = state.blockingMessage ?: "No se puede confirmar la salida de esta relación.",
            onBack = onBack
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            CasetaReadinessBanner(detail)
        }
        item {
            CasetaUnitHero(detail)
        }
        item {
            CasetaDetailPanel(detail)
        }
        item {
            CasetaPhotoGallery(
                paths = state.localPhotoPaths,
                canAdd = state.puedeAgregar,
                canDelete = state.puedeBorrar,
                onOpenCamera = onOpenCamera,
                onRemovePhoto = onRemovePhoto
            )
        }
        item {
            Button(
                onClick = onConfirm,
                enabled = state.puedeModificar && CasetaRules.isReadyForExit(detail) && !state.isProcessing,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmGreenDark,
                    contentColor = Color.White,
                    disabledContainerColor = PmmNavyBorder,
                    disabledContentColor = PmmTextMuted
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Confirmar salida", fontWeight = FontWeight.Black)
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun CasetaPhotoGallery(
    paths: List<String>,
    canAdd: Boolean,
    canDelete: Boolean,
    onOpenCamera: () -> Unit,
    onRemovePhoto: (String) -> Unit
) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "FOTOGRAFÍAS DE SALIDA",
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.0.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpenCamera, enabled = canAdd) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cámara", fontWeight = FontWeight.Black)
                }
            }
            if (paths.isEmpty()) {
                Text(
                    text = "No se han capturado fotos de salida.",
                    color = PmmTextMuted,
                    fontSize = 12.sp,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyRow(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(paths) { path ->
                        Box(
                            modifier = Modifier
                                .size(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PmmNavyVariant)
                        ) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            IconButton(
                                onClick = { onRemovePhoto(path) },
                                enabled = canDelete,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CasetaReadinessBanner(detail: CasetaDetalle) {
    val ready = CasetaRules.isReadyForExit(detail)
    val color = if (ready) PmmGreen else PmmAmber
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (ready) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (ready) "Relación completa, lista para salida" else "Requiere revisión",
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "Al confirmar se actualiza la relación, caseta y se envía la notificación.",
                    color = PmmTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CasetaUnitHero(detail: CasetaDetalle) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = detail.relno,
                        color = PmmTextPrimary,
                        fontFamily = Mono,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = detail.transfer.ifBlank { "Transfer sin nombre" },
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                CasetaStatusBadge(detail.estatusCarga.ifBlank { detail.estatus }, PmmGreen)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CasetaHeroData("CAJA", detail.caja.ifBlank { "N/D" }, Modifier.weight(1f))
                CasetaHeroData("TRACTOR", detail.tractor.ifBlank { "N/D" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CasetaHeroData(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = PmmNavyVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = PmmBlueDeep,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value,
                color = PmmTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CasetaDetailPanel(detail: CasetaDetalle) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DATOS DE LA UNIDAD",
                color = PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.0.sp
            )
            DetailFieldGrid(
                fields = listOf(
                    "Operador" to detail.operador,
                    "Gafete" to detail.gafete,
                    "Sellos" to detail.sellos,
                    "Cliente" to detail.cliente,
                    "Transfer" to detail.transfer,
                    "Estatus" to detail.estatus
                )
            )
        }
    }
}

@Composable
private fun DetailFieldGrid(fields: List<Pair<String, String>>) {
    BoxWithConstraints {
        if (maxWidth >= 620.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, value) ->
                            CasetaDetailField(
                                label = label,
                                value = value,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.forEach { (label, value) ->
                    CasetaDetailField(label, value)
                }
            }
        }
    }
}

@Composable
private fun CasetaDetailField(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PmmBgCard,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label.uppercase(),
                color = PmmTextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Text(
                text = value.ifBlank { "N/D" },
                color = PmmTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CasetaBlockedContent(relno: String, message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = CircleShape, color = PmmAmber.copy(alpha = 0.14f)) {
            Icon(
                imageVector = Icons.Default.LockClock,
                contentDescription = null,
                tint = PmmAmber,
                modifier = Modifier.padding(18.dp).size(42.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = relno.ifBlank { "Relación" },
            color = PmmTextPrimary,
            fontFamily = Mono,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = PmmTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, PmmNavyBorder),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmTextPrimary)
        ) {
            Text("Volver", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CasetaBottomBar(
    enabled: Boolean,
    processing: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    Surface(
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(0.8f).height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Denegar", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onConfirm,
                enabled = enabled && !processing,
                modifier = Modifier.weight(1.2f).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmGreenDark,
                    contentColor = Color.White
                )
            ) {
                if (processing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Confirmar salida", fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun CasetaConfirmDialog(
    detail: CasetaDetalle,
    observations: String,
    processing: Boolean,
    onObservationsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!processing) onDismiss() },
        icon = {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PmmGreen)
        },
        title = {
            Text("Confirmar salida", fontWeight = FontWeight.Black)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "¿Confirmar salida de la relación ${detail.relno}?",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Se marcará la caja ${detail.caja.ifBlank { "N/D" }} como SALIDA y se enviará la notificación correspondiente.",
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = observations,
                    onValueChange = onObservationsChange,
                    label = { Text("Observaciones opcionales") },
                    placeholder = { Text("Dano visible, sello adicional, comentario de patio") },
                    enabled = !processing,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !processing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmGreenDark,
                    contentColor = Color.White
                )
            ) {
                Text("Confirmar", fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !processing) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CasetaStatusBadge(text: String, color: Color) {
    val effectiveColor = when {
        text.contains("SALIDA", ignoreCase = true) -> PmmGreen
        text.contains("INCOMPLETA", ignoreCase = true) -> PmmAmber
        text.contains("OCUPADA", ignoreCase = true) -> PmmRed
        else -> color
    }
    Surface(shape = RoundedCornerShape(6.dp), color = effectiveColor.copy(alpha = 0.12f)) {
        Text(
            text = text.ifBlank { "N/D" },
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            color = effectiveColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun CasetaPendingSkeleton() {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Skeleton(width = 160.dp, height = 18.dp)
            Skeleton(width = 260.dp, height = 12.dp)
            HorizontalDivider(color = PmmNavyBorder)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(modifier = Modifier.weight(1f), height = 32.dp)
                Skeleton(modifier = Modifier.weight(1f), height = 32.dp)
            }
        }
    }
}

@Composable
private fun CasetaDetailSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Skeleton(height = 66.dp)
        Skeleton(height = 142.dp)
        Skeleton(height = 260.dp)
    }
}

@Composable
private fun CasetaEmptyState(
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
            text = if (filtered) "Sin coincidencias" else "Sin relaciones pendientes de salida",
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Text(
            text = if (filtered) {
                "Limpia la búsqueda o escanea otra relación."
            } else {
                "Cuando bodega autorice una carga, aparecerá aquí para confirmar la salida de caseta."
            },
            color = PmmTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(16.dp))
        if (filtered) {
            OutlinedButton(onClick = onClear) {
                Text("Limpiar búsqueda", fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Actualizar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatDateTime(value: String?): String {
    val clean = value?.trim().orEmpty()
    if (clean.isBlank()) return "Sin fecha"
    return clean
        .replace("T", " ")
        .substringBefore(".")
        .take(16)
}
