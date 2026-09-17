package com.example.pmmovil.ui.revision

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.FinalizarBarcodeResponse
import com.example.pmmovil.data.model.LecturaResponse
import com.example.pmmovil.data.model.ProductoResumen
import com.example.pmmovil.data.model.ResumenBarcodeResponse
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.PmmStatusBar
import com.example.pmmovil.ui.scanner.HardwareScannerListenerWithDiagnostics
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun RevisionBarcodeScreen(
    onNavigateBack: () -> Unit,
    onNavigateAfterForcedClose: () -> Unit = onNavigateBack,
    onNavigateToEvidence: (String) -> Unit,
    photosChanged: Boolean = false,
    onPhotosChangedConsumed: () -> Unit = {},
    viewModel: RevisionBarcodeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val localEvidenceCount by viewModel.localEvidenceCount.collectAsState()
    val evidenceCount = state.remoteEvidenceCount + localEvidenceCount
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 72) }
    val speaker = rememberBarcodeSpeaker()

    PmmStatusBar(color = PmmNavyDark, darkIcons = false)

    HardwareScannerListenerWithDiagnostics(
        onBarcodeScanned = viewModel::registerReading,
        onScanEvent = viewModel::registerHardwareScan,
        onDataWedgeStatus = viewModel::onDataWedgeStatus
    )

    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
            tone.release()
        }
    }

    LaunchedEffect(photosChanged) {
        if (photosChanged) {
            viewModel.refreshEvidenceCount()
            onPhotosChangedConsumed()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    // Abre el share sheet nativo de Android con el link de seguimiento --
    // mismo patrón que en Entrada y en la revisión normal.
    val shareContext = LocalContext.current
    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, "Seguimiento de tu entrada ${viewModel.crefAgrupada}: $url")
            }
            shareContext.startActivity(android.content.Intent.createChooser(sendIntent, "Compartir seguimiento"))
            viewModel.consumeShareUrl()
        }
    }

    state.cierreForzoso?.let { cierre ->
        AlertDialog(
            onDismissRequest = { /* El revisor debe confirmar que recibió el aviso. */ },
            containerColor = PmmBgSurface,
            title = {
                Text(
                    "Revisión cerrada por supervisión",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        cierre.mensaje?.takeIf(String::isNotBlank)
                            ?: "Tu sesión de revisión fue cerrada desde el panel de supervisión.",
                        color = PmmTextSecondary
                    )
                    Text(
                        "Supervisor: ${cierre.supervisor?.takeIf(String::isNotBlank) ?: "No indicado"}",
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Motivo: ${cierre.motivo?.takeIf(String::isNotBlank) ?: "No se indicó un motivo."}",
                        color = PmmTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.consumeCierreForzoso()
                        onNavigateAfterForcedClose()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    LaunchedEffect(state.lastReadingSeq) {
        val reading = state.lastReading ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        tone.startTone(
            if (reading.existe) {
                ToneGenerator.TONE_PROP_ACK
            } else {
                ToneGenerator.TONE_PROP_NACK
            },
            120
        )
        val message = if (reading.existe) {
            reading.descripcion?.takeIf(String::isNotBlank)
                ?: reading.cidproduct.orEmpty()
        } else {
            "No reconocido: ${reading.cidproduct.orEmpty()}"
        }
        speaker?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "barcode-lectura")
        if (state.captureMode != BarcodeCaptureMode.CAMERA) {
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    state.removalRequest?.let { request ->
        RemovalDialog(
            product = request.product,
            allowRemoveAll = request.allowRemoveAll,
            onRemoveOne = { viewModel.removeOne(request.product) },
            onRemoveAll = { viewModel.removeAll(request.product) },
            onDismiss = viewModel::dismissRemoval
        )
    }

    state.finalizePreview?.let { preview ->
        FinalizeDialog(
            preview = preview,
            onDismiss = viewModel::closeFinalizePreview,
            onFinalize = viewModel::finalizeSession
        )
    }

    if (state.isFinalizing) {
        FinalizingDialog()
    }

    state.finalizeResult?.let { result ->
        FinalResultDialog(result = result, onClose = onNavigateBack)
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbarHostState) },
        topBar = {
            BarcodeHeader(
                crefAgrupada = viewModel.crefAgrupada,
                elapsedSeconds = state.elapsedSeconds,
                pendingCount = state.pendingCount,
                offline = state.isOffline,
                evidenceCount = evidenceCount,
                onBack = onNavigateBack,
                onEvidence = {
                    onNavigateToEvidence(state.config?.cidcte.orEmpty())
                },
                onCompartir = viewModel::compartirEntrada,
                isSharingLink = state.isSharingLink
            )
        },
        bottomBar = {
            val total = state.products.sumOf(ProductoResumen::cantidad)
            val finalizeBlocker = barcodeFinalizeBlocker(
                totalProducts = total,
                pendingCount = state.pendingCount,
                isLoading = state.isLoading,
                isFinalizing = state.isFinalizing,
                hasError = state.error != null
            )
            BarcodeBottomBar(
                total = total,
                pending = state.pendingCount,
                preparing = state.isLoading,
                blocker = finalizeBlocker,
                canModify = state.puedeModificar,
                onFinalize = viewModel::prepareFinalize
            )
        }
    ) { padding ->
        when {
            state.isLoading && state.sessionId == null -> {
                LoadingState(Modifier.fillMaxSize().padding(padding))
            }
            state.error != null -> {
                ErrorState(
                    message = state.error,
                    onRetry = viewModel::retryLoad,
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
            else -> {
                BarcodeWorkspace(
                    state = state,
                    onCameraRead = { code ->
                        viewModel.registerReadingFromSource(
                            rawCode = code,
                            source = BarcodeInputSource.CAMERA,
                            sourceLabel = "Cámara ML Kit"
                        )
                    },
                    onManualRead = { code ->
                        viewModel.registerReadingFromSource(
                            rawCode = code,
                            source = BarcodeInputSource.MANUAL,
                            sourceLabel = "Captura manual"
                        )
                    },
                    onCaptureMode = viewModel::setCaptureMode,
                    onFilter = viewModel::setFilter,
                    onRemove = viewModel::requestRemove,
                    onUndo = viewModel::undoLastReading,
                    onAdd = { product ->
                        viewModel.registerReadingFromSource(
                            rawCode = product.codigoRaw?.takeIf(String::isNotBlank)
                                ?: product.cidproduct,
                            source = BarcodeInputSource.MANUAL,
                            sourceLabel = "Corrección manual"
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(padding)
                )
            }
        }
    }
}

@Composable
private fun rememberBarcodeSpeaker(): TextToSpeech? {
    val context = LocalContext.current
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.let { tts ->
                    val result = tts.setLanguage(Locale.forLanguageTag("es-MX"))
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts.setLanguage(Locale.forLanguageTag("es"))
                    }
                    speaker = tts
                }
            }
        }
        onDispose {
            speaker = null
            instance?.stop()
            instance?.shutdown()
        }
    }
    return speaker
}

@Composable
private fun BarcodeHeader(
    crefAgrupada: String,
    elapsedSeconds: Long,
    pendingCount: Int,
    offline: Boolean,
    evidenceCount: Int,
    onBack: () -> Unit,
    onEvidence: () -> Unit,
    onCompartir: () -> Unit,
    isSharingLink: Boolean
) {
    Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Revisión por barcode",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = crefAgrupada,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp
                )
            }
            HeaderStatusPill(
                icon = if (offline) Icons.Default.CloudOff else Icons.Default.CloudDone,
                text = when {
                    offline -> "Sin conexión"
                    pendingCount > 0 -> "$pendingCount pendientes"
                    else -> "Sincronizado"
                },
                tint = when {
                    offline -> Color(0xFFFFC46B)
                    pendingCount > 0 -> PmmYellow
                    else -> Color(0xFF7CE6A2)
                }
            )
            Spacer(Modifier.width(6.dp))
            HeaderStatusPill(
                icon = Icons.Default.Schedule,
                text = formatDuration(elapsedSeconds),
                tint = Color.White
            )
            Spacer(Modifier.width(4.dp))
            Box {
                IconButton(onClick = onEvidence) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "Evidencias",
                        tint = Color.White
                    )
                }
                if (evidenceCount > 0) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp),
                        shape = CircleShape,
                        color = PmmYellow
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = evidenceCount.coerceAtMost(99).toString(),
                                color = PmmBlueDeep,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onCompartir, enabled = !isSharingLink) {
                if (isSharingLink) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Compartir seguimiento de la entrada",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderStatusPill(
    icon: ImageVector,
    text: String,
    tint: Color
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BarcodeBottomBar(
    total: Int,
    pending: Int,
    preparing: Boolean,
    blocker: String?,
    canModify: Boolean,
    onFinalize: () -> Unit
) {
    val enabled = canModify && blocker == null && !preparing
    Surface(color = PmmBgSurface, shadowElevation = 10.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (pending > 0) {
                        "$pending pendientes"
                    } else {
                        "$total lecturas"
                    },
                    color = PmmTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = blocker ?: "Revisa los detalles antes de confirmar",
                    color = if (blocker != null || pending > 0) PmmAmber else PmmTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Button(
                onClick = onFinalize,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(15.dp),
                contentPadding = PaddingValues(horizontal = 18.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    disabledContainerColor = PmmNavyBorder
                )
            ) {
                if (preparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(19.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (preparing) "Preparando resumen..." else "Revisar y confirmar",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BarcodeWorkspace(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    onFilter: (BarcodeProductFilter) -> Unit,
    onRemove: (ProductoResumen) -> Unit,
    onUndo: () -> Unit,
    onAdd: (ProductoResumen) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.padding(12.dp)) {
        // La tableta SM-X210 llega en horizontal como 1280dp x 800dp. El
        // breakpoint anterior podia mandar algunos equipos a la composicion
        // vertical, donde la lectura quedaba fuera del espacio visible.
        val isTabletLandscape = maxWidth >= 600.dp && maxWidth > maxHeight
        val isTabletPortrait = maxWidth >= 600.dp && maxHeight >= maxWidth
        when {
            isTabletLandscape -> TabletLandscapeBarcodeWorkspace(
                state = state,
                onCameraRead = onCameraRead,
                onManualRead = onManualRead,
                onCaptureMode = onCaptureMode,
                onFilter = onFilter,
                onRemove = onRemove,
                onUndo = onUndo,
                onAdd = onAdd
            )
            isTabletPortrait -> TabletPortraitBarcodeWorkspace(
                state = state,
                onCameraRead = onCameraRead,
                onManualRead = onManualRead,
                onCaptureMode = onCaptureMode,
                onFilter = onFilter,
                onRemove = onRemove,
                onUndo = onUndo,
                onAdd = onAdd
            )
            else -> MobileBarcodeWorkspace(
                state = state,
                onCameraRead = onCameraRead,
                onManualRead = onManualRead,
                onCaptureMode = onCaptureMode,
                onFilter = onFilter,
                onRemove = onRemove,
                onUndo = onUndo,
                onAdd = onAdd
            )
        }
    }
}

@Composable
private fun TabletLandscapeBarcodeWorkspace(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    onFilter: (BarcodeProductFilter) -> Unit,
    onRemove: (ProductoResumen) -> Unit,
    onUndo: () -> Unit,
    onAdd: (ProductoResumen) -> Unit
) {
    var manualCode by remember { mutableStateOf("") }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1.62f).fillMaxHeight(),
            color = Color.Black,
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color.Black.copy(alpha = 0.22f))
        ) {
            when (state.captureMode) {
                    BarcodeCaptureMode.CAMERA -> CameraCaptureArea(
                        onCameraRead = onCameraRead,
                        lastReading = state.lastReading,
                        lastReadingSeq = state.lastReadingSeq,
                        modifier = Modifier.fillMaxSize()
                    )
                    BarcodeCaptureMode.PHYSICAL -> PhysicalScannerCard(
                        isProcessing = state.isProcessingQueue,
                        modifier = Modifier.fillMaxSize()
                    )
                    BarcodeCaptureMode.MANUAL -> ManualCaptureStage(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        expectedLength = state.config?.barcodeLong ?: 0,
                        onSubmit = { cleanCode ->
                            onManualRead(cleanCode)
                            manualCode = ""
                        },
                        modifier = Modifier.fillMaxSize()
                    )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactSessionContext(state)
            CaptureModeSelector(
                selected = state.captureMode,
                onSelected = onCaptureMode
            )
            if (state.captureMode == BarcodeCaptureMode.MANUAL) {
                ManualBarcodeField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    expectedLength = state.config?.barcodeLong ?: 0,
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = { cleanCode ->
                        onManualRead(cleanCode)
                        manualCode = ""
                    }
                )
            }
            ProductColumn(
                state = state,
                onFilter = onFilter,
                onRemove = onRemove,
                onAdd = onAdd,
                modifier = Modifier.fillMaxWidth().height(350.dp)
            )
        }
    }
}

@Composable
private fun TabletPortraitBarcodeWorkspace(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    onFilter: (BarcodeProductFilter) -> Unit,
    onRemove: (ProductoResumen) -> Unit,
    onUndo: () -> Unit,
    onAdd: (ProductoResumen) -> Unit
) {
    var manualCode by remember { mutableStateOf("") }
    val cameraHeight = 360.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "portrait-session") { SessionInfoCard(state) }
        item(key = "portrait-camera") {
            when (state.captureMode) {
                BarcodeCaptureMode.CAMERA -> CameraCaptureArea(
                    onCameraRead = onCameraRead,
                    lastReading = state.lastReading,
                    lastReadingSeq = state.lastReadingSeq,
                    modifier = Modifier.fillMaxWidth().height(cameraHeight)
                )
                BarcodeCaptureMode.PHYSICAL -> PhysicalScannerCard(
                    isProcessing = state.isProcessingQueue,
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                )
                BarcodeCaptureMode.MANUAL -> ManualCaptureStage(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    expectedLength = state.config?.barcodeLong ?: 0,
                    onSubmit = { cleanCode ->
                        onManualRead(cleanCode)
                        manualCode = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                )
            }
        }
        item(key = "portrait-operation") {
            Surface(
                color = PmmBgSurface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CaptureModeSelector(selected = state.captureMode, onSelected = onCaptureMode)
                    if (state.captureMode == BarcodeCaptureMode.MANUAL) {
                        ManualBarcodeField(
                            value = manualCode,
                            onValueChange = { manualCode = it },
                            expectedLength = state.config?.barcodeLong ?: 0,
                            modifier = Modifier.fillMaxWidth(),
                            onSubmit = { cleanCode ->
                                onManualRead(cleanCode)
                                manualCode = ""
                            }
                        )
                    }
                }
            }
        }
        item(key = "portrait-history") {
            ProductColumn(
                state = state,
                onFilter = onFilter,
                onRemove = onRemove,
                onAdd = onAdd,
                modifier = Modifier.fillMaxWidth().height(460.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun MobileBarcodeWorkspace(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    onFilter: (BarcodeProductFilter) -> Unit,
    onRemove: (ProductoResumen) -> Unit,
    onUndo: () -> Unit,
    onAdd: (ProductoResumen) -> Unit
) {
    var manualCode by remember { mutableStateOf("") }
    val total = state.products.sumOf(ProductoResumen::cantidad)
    val recognized = state.products.filter(ProductoResumen::existe)
        .sumOf(ProductoResumen::cantidad)
    val unknown = state.products
        .filter { !it.existe && it.pendientes < it.cantidad }
        .sumOf(ProductoResumen::cantidad)
    val filtered = filterBarcodeProducts(state.products, state.filter)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "session") { SessionInfoCard(state) }
        item(key = "scanner") {
            MobileCaptureStage(
                state = state,
                onCameraRead = onCameraRead,
                onManualRead = onManualRead,
                onCaptureMode = onCaptureMode,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (state.captureMode == BarcodeCaptureMode.MANUAL) {
            item(key = "manual") {
                ManualBarcodeField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    expectedLength = state.config?.barcodeLong ?: 0,
                    modifier = Modifier.fillMaxWidth(),
                    onSubmit = { cleanCode ->
                        onManualRead(cleanCode)
                        manualCode = ""
                    }
                )
            }
        }
        item(key = "summary") {
            Surface(
                color = PmmBgSurface,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column {
                    MetricStrip(
                        total = total,
                        recognized = recognized,
                        unknown = unknown,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                    HorizontalDivider(color = PmmNavyBorder)
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            "Historial de lectura",
                            color = PmmTextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            BarcodeFilterChip(state.filter, BarcodeProductFilter.ALL, "Todos", onFilter)
                            BarcodeFilterChip(state.filter, BarcodeProductFilter.RECOGNIZED, "Catálogo", onFilter)
                            BarcodeFilterChip(state.filter, BarcodeProductFilter.UNKNOWN, "Inválidos", onFilter)
                        }
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item(key = "empty-products") {
                EmptyProducts(modifier = Modifier.fillMaxWidth().height(190.dp))
            }
        } else {
            items(
                items = filtered,
                key = { "${it.cidproduct}-${it.existe}" }
            ) { product ->
                ProductRow(
                    product = product,
                    onRemove = { onRemove(product) },
                    onAdd = { onAdd(product) }
                )
            }
        }
        item(key = "keyboard-space") {
            Spacer(
                Modifier.height(
                    WindowInsets.ime.asPaddingValues().calculateBottomPadding()
                )
            )
        }
    }
}

@Composable
private fun MobileCaptureStage(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (state.captureMode) {
            BarcodeCaptureMode.CAMERA -> CameraCaptureArea(
                onCameraRead = onCameraRead,
                lastReading = state.lastReading,
                lastReadingSeq = state.lastReadingSeq,
                modifier = Modifier.fillMaxWidth().height(430.dp)
            )
            BarcodeCaptureMode.PHYSICAL -> PhysicalScannerCard(
                isProcessing = state.isProcessingQueue,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
            BarcodeCaptureMode.MANUAL -> Surface(
                modifier = Modifier.fillMaxWidth().height(180.dp),
                color = PmmBgSurface,
                shape = RoundedCornerShape(26.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "El campo de captura aparece debajo para no cubrir el teclado.",
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (state.captureMode == BarcodeCaptureMode.CAMERA) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(10.dp),
                color = PmmBgSurface.copy(alpha = 0.96f),
                shape = RoundedCornerShape(18.dp),
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptureModeSelector(selected = state.captureMode, onSelected = onCaptureMode)
                }
            }
        } else {
            CaptureModeSelector(
                selected = state.captureMode,
                onSelected = onCaptureMode,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun CaptureColumn(
    state: BarcodeUiState,
    onCameraRead: (String) -> Unit,
    onManualRead: (String) -> Unit,
    onCaptureMode: (BarcodeCaptureMode) -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    var manualCode by remember { mutableStateOf("") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SessionInfoCard(state)
        CaptureModeSelector(
            selected = state.captureMode,
            onSelected = onCaptureMode
        )
        when (state.captureMode) {
            BarcodeCaptureMode.CAMERA -> CameraCaptureArea(
                onCameraRead = onCameraRead,
                lastReading = state.lastReading,
                lastReadingSeq = state.lastReadingSeq,
                modifier = Modifier.fillMaxWidth().height(410.dp)
            )
            BarcodeCaptureMode.PHYSICAL -> PhysicalScannerCard(
                isProcessing = state.isProcessingQueue,
                modifier = Modifier.fillMaxWidth().height(164.dp)
            )
            BarcodeCaptureMode.MANUAL -> ManualBarcodeField(
                value = manualCode,
                onValueChange = { manualCode = it },
                expectedLength = state.config?.barcodeLong ?: 0,
                modifier = Modifier.fillMaxWidth(),
                onSubmit = { cleanCode ->
                    onManualRead(cleanCode)
                    manualCode = ""
                }
            )
        }
    }
}

@Composable
private fun WorkspaceSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = PmmNavyVariant) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.padding(8.dp).size(20.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PmmTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = PmmTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(shape = RoundedCornerShape(999.dp), color = PmmGreen.copy(alpha = 0.12f)) {
            Text(
                text = "En vivo",
                color = PmmGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ManualCaptureStage(
    value: String,
    onValueChange: (String) -> Unit,
    expectedLength: Int,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PmmBgSurface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            ManualBarcodeField(
                value = value,
                onValueChange = onValueChange,
                expectedLength = expectedLength,
                onSubmit = onSubmit,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SessionInfoCard(state: BarcodeUiState) {
    val config = state.config
    val proveedor = config?.nombreProveedor?.takeIf(String::isNotBlank) ?: "Proveedor"
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = PmmNavyVariant) {
                Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = proveedor.take(1).uppercase(),
                        color = PmmBlueDeep,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = proveedor,
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = config?.nombreCliente?.takeIf(String::isNotBlank)
                        ?: "Cliente",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Código: ${config?.barcodeLong ?: 0} caracteres · " +
                        "Catálogo: ${config?.totalCatalogo ?: 0}",
                    color = PmmTextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            state.sessionId?.let {
                Spacer(Modifier.width(8.dp))
                InfoTag("#$it")
            }
        }
    }
}

@Composable
private fun CompactSessionContext(state: BarcodeUiState) {
    val config = state.config
    val provider = config?.nombreProveedor?.takeIf(String::isNotBlank) ?: "Proveedor"
    val client = config?.nombreCliente?.takeIf(String::isNotBlank) ?: "Cliente"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider,
                color = PmmTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = client,
                color = PmmTextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        state.sessionId?.let {
            Text(
                text = "Sesión #$it",
                color = PmmBlueDeep,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoTag(text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = PmmNavyVariant) {
        Text(
            text = text,
            color = PmmBlueDeep,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun CaptureModeSelector(
    selected: BarcodeCaptureMode,
    onSelected: (BarcodeCaptureMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PmmNavyVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CaptureModeButton(
            label = "Lector",
            icon = Icons.Default.QrCodeScanner,
            selected = selected == BarcodeCaptureMode.PHYSICAL,
            onClick = { onSelected(BarcodeCaptureMode.PHYSICAL) },
            modifier = Modifier.weight(1f)
        )
        CaptureModeButton(
            label = "Cámara",
            icon = Icons.Default.CameraAlt,
            selected = selected == BarcodeCaptureMode.CAMERA,
            onClick = { onSelected(BarcodeCaptureMode.CAMERA) },
            modifier = Modifier.weight(1f)
        )
        CaptureModeButton(
            label = "Manual",
            icon = Icons.Default.Keyboard,
            selected = selected == BarcodeCaptureMode.MANUAL,
            onClick = { onSelected(BarcodeCaptureMode.MANUAL) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CaptureModeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) PmmBgSurface else Color.Transparent,
        shadowElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PmmBlueDeep else PmmTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                color = if (selected) PmmBlueDeep else PmmTextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun ManualBarcodeField(
    value: String,
    onValueChange: (String) -> Unit,
    expectedLength: Int,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val status = barcodeManualInputStatus(value, expectedLength)
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        label = { Text("Captura manual") },
        placeholder = { Text("Escribe o pega un código") },
        leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null) },
        trailingIcon = {
            IconButton(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onSubmit(status.cleanValue)
                },
                enabled = status.canSubmit
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        },
        supportingText = {
            Text(
                text = status.helperText,
                color = when {
                    status.isExactLength || status.isOverLength -> PmmGreen
                    value.isBlank() -> PmmTextSecondary
                    else -> PmmAmber
                },
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(
            onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
                if (status.canSubmit) {
                    onSubmit(status.cleanValue)
                }
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PmmBlueDeep,
            focusedLabelColor = PmmBlueDeep,
            cursorColor = PmmBlueDeep
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun BarcodeFilterChip(
    selectedFilter: BarcodeProductFilter,
    filter: BarcodeProductFilter,
    label: String,
    onFilter: (BarcodeProductFilter) -> Unit
) {
    FilterChip(
        selected = selectedFilter == filter,
        onClick = { onFilter(filter) },
        label = { Text(label, fontSize = 10.sp) },
        colors = barcodeFilterColors()
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ScannerDiagnosticCard(state: BarcodeUiState) {
    val scan = state.scanDiagnostic
    val dataWedge = state.dataWedgeDiagnostic
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Diagnóstico",
                    color = PmmTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (scan.accepted) "Aceptada" else scan.reason ?: "En espera",
                    color = when {
                        scan.accepted -> PmmGreen
                        scan.atMillis > 0L -> PmmAmber
                        else -> PmmTextSecondary
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DiagnosticTag("Fuente", scan.sourceLabel)
                DiagnosticTag(
                    "Código",
                    scan.normalizedCode?.take(22) ?: "Sin lectura"
                )
                DiagnosticTag(
                    "Longitud",
                    if (scan.expectedLength > 0) {
                        "${scan.normalizedLength}/${scan.expectedLength}"
                    } else {
                        "${scan.normalizedLength}"
                    }
                )
                scan.labelType?.takeIf(String::isNotBlank)?.let {
                    DiagnosticTag("Formato", it.removePrefix("LABEL-TYPE-"))
                }
            }
            HorizontalDivider(color = PmmNavyBorder)
            Text(
                text = when (dataWedge.state) {
                    BarcodeDataWedgeState.READY -> dataWedge.message
                    BarcodeDataWedgeState.ERROR ->
                        "${dataWedge.message}: ${dataWedge.details.orEmpty()}"
                    BarcodeDataWedgeState.AVAILABLE -> dataWedge.message
                },
                color = when (dataWedge.state) {
                    BarcodeDataWedgeState.READY -> PmmGreen
                    BarcodeDataWedgeState.ERROR -> PmmAmber
                    BarcodeDataWedgeState.AVAILABLE -> PmmTextSecondary
                },
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiagnosticTag(label: String, value: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = PmmNavyVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                color = PmmTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = value,
                color = PmmBlueDeep,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CameraCaptureArea(
    onCameraRead: (String) -> Unit,
    lastReading: LecturaResponse?,
    lastReadingSeq: Long,
    modifier: Modifier = Modifier
) {
    var flash by remember { mutableStateOf<LecturaResponse?>(null) }
    var candidateAnnouncementCount by remember { mutableStateOf(0) }
    var candidateAnnouncementSeq by remember { mutableStateOf(0L) }
    val cameraSpeaker = rememberBarcodeSpeaker()

    LaunchedEffect(lastReadingSeq) {
        val reading = lastReading ?: return@LaunchedEffect
        flash = reading
        delay(2_200L)
        flash = null
    }

    LaunchedEffect(candidateAnnouncementSeq) {
        if (candidateAnnouncementSeq == 0L || candidateAnnouncementCount < 2) return@LaunchedEffect
        cameraSpeaker?.speak(
            "Se detectaron $candidateAnnouncementCount códigos. Elige uno de los códigos para continuar.",
            TextToSpeech.QUEUE_FLUSH,
            null,
            "barcode-candidate-selection"
        )
    }

    Surface(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        color = Color.Black,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(23.dp))) {
            BarcodeCameraPanel(
                onBarcodeScanned = onCameraRead,
                onCandidateSelectionRequired = { count ->
                    candidateAnnouncementCount = count
                    candidateAnnouncementSeq = System.nanoTime()
                },
                scanCooldownMillis = 1_350L,
                modifier = Modifier.fillMaxSize()
            )
            AnimatedVisibility(
                visible = flash != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
                exit = fadeOut(tween(160))
            ) {
                flash?.let { CameraScanConfirmationCard(it) }
            }
        }
    }
}

@Composable
private fun LegacyCameraCaptureArea(
    onCameraRead: (String) -> Unit,
    lastReading: LecturaResponse?,
    lastReadingSeq: Long,
    modifier: Modifier = Modifier
) {
    var flash by remember { mutableStateOf<LecturaResponse?>(null) }
    var candidateCount by remember { mutableStateOf(0) }
    LaunchedEffect(lastReadingSeq) {
        val reading = lastReading ?: return@LaunchedEffect
        flash = reading
        delay(2_200L)
        flash = null
    }
    Surface(
        modifier = modifier.clip(RoundedCornerShape(26.dp)),
        color = PmmBgCard,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(PmmNavyVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Cámara de revisión",
                        tint = PmmBlueLight,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (candidateCount > 1) {
                            "$candidateCount códigos detectados"
                        } else {
                            "Cámara lista"
                        },
                        color = PmmTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (candidateCount > 1) {
                            "Toca el marco correcto para continuar"
                        } else {
                            "Centra la etiqueta dentro del marco"
                        },
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = if (candidateCount > 1) {
                        PmmAmber.copy(alpha = 0.14f)
                    } else if (flash == null) {
                        PmmGreen.copy(alpha = 0.12f)
                    } else {
                        PmmBlueLight.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = when {
                            candidateCount > 1 -> "Elige uno"
                            flash == null -> "Activa"
                            else -> "Leída"
                        },
                        color = when {
                            candidateCount > 1 -> PmmAmber
                            flash == null -> PmmGreen
                            else -> PmmBlueDeep
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
            ) {
                BarcodeCameraPanel(
                    onBarcodeScanned = onCameraRead,
                    onPotentialBarcodes = { candidates ->
                        candidateCount = candidates.size
                    },
                    scanCooldownMillis = 1_350L,
                    modifier = Modifier.fillMaxSize()
                )
            }

        AnimatedVisibility(
            visible = flash != null,
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { it / 3 },
            exit = fadeOut(tween(160))
        ) {
            flash?.let { CameraScanConfirmationCard(it) }
        }
        }
    }
}

@Composable
private fun CameraScanConfirmationCard(reading: LecturaResponse) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PmmBgSurface,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 14.dp
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (reading.existe) {
                    PmmGreen.copy(alpha = 0.14f)
                } else {
                    PmmRed.copy(alpha = 0.12f)
                }
            ) {
                Icon(
                    imageVector = if (reading.existe) Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (reading.existe) PmmGreen else PmmRed,
                    modifier = Modifier.padding(9.dp).size(19.dp)
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reading.cidproduct.orEmpty(),
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (reading.existe) {
                        reading.descripcion?.takeIf(String::isNotBlank)
                            ?: "Producto en catálogo"
                    } else {
                        "No encontrado en catálogo"
                    },
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PhysicalScannerCard(
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = PmmBgSurface,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(PmmNavyVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isProcessing) Icons.Default.Sync else Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun LastReadingCard(
    state: BarcodeUiState,
    onUndo: () -> Unit
) {
    val last = state.products.firstOrNull()
    if (last == null) return
    val isPending = last.pendientes > 0
    Surface(
        color = when {
            isPending -> Color(0xFFFFF8E1)
            last.existe -> Color(0xFFECFDF3)
            else -> Color(0xFFFFF1F1)
        },
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            when {
                isPending -> PmmAmber.copy(alpha = 0.35f)
                last.existe -> PmmGreen.copy(alpha = 0.3f)
                else -> PmmRed.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    isPending -> Icons.Default.Sync
                    last.existe -> Icons.Default.CheckCircle
                    else -> Icons.Default.WarningAmber
                },
                contentDescription = null,
                tint = when {
                    isPending -> PmmAmber
                    last.existe -> PmmGreen
                    else -> PmmRed
                }
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Última lectura",
                    color = PmmTextSecondary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = last.cidproduct,
                    color = PmmTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = last.descripcion.orEmpty(),
                    color = PmmTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "×${last.cantidad}",
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(6.dp))
            TextButton(onClick = onUndo) {
                Text(
                    text = "Deshacer",
                    color = PmmBlueDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ProductColumn(
    state: BarcodeUiState,
    onFilter: (BarcodeProductFilter) -> Unit,
    onRemove: (ProductoResumen) -> Unit,
    onAdd: (ProductoResumen) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = state.products.sumOf(ProductoResumen::cantidad)
    val recognized = state.products.filter(ProductoResumen::existe)
        .sumOf(ProductoResumen::cantidad)
    val unknown = state.products
        .filter { !it.existe && it.pendientes < it.cantidad }
        .sumOf(ProductoResumen::cantidad)
    val filtered = filterBarcodeProducts(state.products, state.filter)

    Surface(
        modifier = modifier,
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MetricStrip(
                total = total,
                recognized = recognized,
                unknown = unknown,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
            HorizontalDivider(color = PmmNavyBorder)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                Text(
                    text = "Historial de lectura",
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BarcodeFilterChip(state.filter, BarcodeProductFilter.ALL, "Todos", onFilter)
                    BarcodeFilterChip(state.filter, BarcodeProductFilter.RECOGNIZED, "Catálogo", onFilter)
                    BarcodeFilterChip(state.filter, BarcodeProductFilter.UNKNOWN, "Inválidos", onFilter)
                }
            }

            if (filtered.isEmpty()) {
                EmptyProducts(modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(
                        items = filtered,
                        key = { "${it.cidproduct}-${it.existe}" }
                    ) { product ->
                        ProductRow(
                            product = product,
                            onRemove = { onRemove(product) },
                            onAdd = { onAdd(product) }
                        )
                    }
                    item {
                        Spacer(
                            Modifier.height(
                                WindowInsets.ime.asPaddingValues()
                                    .calculateBottomPadding()
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value.toString(),
                color = color,
                fontSize = 19.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = label,
                color = PmmTextSecondary,
                fontSize = 9.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MetricStrip(
    total: Int,
    recognized: Int,
    unknown: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MetricCell("Total", total, PmmBlueDeep, Modifier.weight(1f))
        MetricDivider()
        MetricCell("Catálogo", recognized, PmmGreen, Modifier.weight(1f))
        MetricDivider()
        MetricDivider()
        MetricCell("Inválidos", unknown, PmmRed, Modifier.weight(1f))
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value.toString(),
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = label.uppercase(),
            color = PmmTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(30.dp)
            .background(PmmNavyBorder)
    )
}

@Composable
private fun ProductRow(
    product: ProductoResumen,
    onRemove: () -> Unit,
    onAdd: () -> Unit
) {
    val pendingOnly = !product.existe && product.pendientes == product.cantidad
    Surface(
        color = if (product.existe || product.pendientes > 0) PmmBgCard else Color(0xFFFFF8F8),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            1.dp,
            when {
                product.pendientes > 0 -> PmmAmber.copy(alpha = 0.35f)
                product.existe -> PmmNavyBorder
                else -> PmmRed.copy(alpha = 0.32f)
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = when {
                    product.pendientes > 0 -> PmmAmber.copy(alpha = 0.12f)
                    product.existe -> PmmGreen.copy(alpha = 0.12f)
                    else -> PmmRed.copy(alpha = 0.1f)
                }
            ) {
                Icon(
                    imageVector = when {
                        product.pendientes > 0 -> Icons.Default.Sync
                        product.existe -> Icons.Default.Inventory2
                        else -> Icons.Default.WarningAmber
                    },
                    contentDescription = null,
                    tint = when {
                        product.pendientes > 0 -> PmmAmber
                        product.existe -> PmmGreen
                        else -> PmmRed
                    },
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = product.cidproduct,
                        color = PmmTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (product.pendientes > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${product.pendientes} pendiente(s)",
                            color = PmmAmber,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = when {
                        pendingOnly -> "Validando contra catálogo"
                        product.existe -> product.descripcion?.takeIf(String::isNotBlank)
                            ?: "Producto en catálogo"
                        else -> "Código no encontrado en el catálogo"
                    },
                    color = PmmTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(38.dp)
                    .background(PmmNavyVariant, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = "Quitar una",
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = product.cantidad.toString(),
                color = PmmTextPrimary,
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.width(38.dp)
            )
            IconButton(
                onClick = onAdd,
                modifier = Modifier
                    .size(38.dp)
                    .background(PmmBlueDeep, RoundedCornerShape(10.dp))
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Agregar una",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyProducts(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(shape = CircleShape, color = PmmNavyVariant) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.padding(18.dp).size(34.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Listo para la primera lectura",
            color = PmmTextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Usa el lector físico, la cámara o captura el código manualmente.",
            color = PmmTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = PmmBlueDeep)
        Spacer(Modifier.height(12.dp))
        Text("Preparando sesión de barcode", color = PmmTextSecondary)
    }
}

@Composable
private fun ErrorState(
    message: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.WarningAmber,
            contentDescription = null,
            tint = PmmRed,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message ?: "No se pudo abrir la sesión.",
            color = PmmTextPrimary,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun RemovalDialog(
    product: ProductoResumen,
    allowRemoveAll: Boolean,
    onRemoveOne: () -> Unit,
    onRemoveAll: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = null,
                    tint = PmmRed,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = if (allowRemoveAll) {
                        "¿Qué deseas corregir?"
                    } else {
                        "Eliminar última lectura"
                    },
                    color = PmmTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${product.cidproduct} tiene ${product.cantidad} lectura(s).",
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
                if (allowRemoveAll) {
                    Button(
                        onClick = onRemoveOne,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                    ) {
                        Text("Quitar solo una")
                    }
                    OutlinedButton(
                        onClick = onRemoveAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmRed)
                    ) {
                        Text("Eliminar todas las lecturas")
                    }
                } else {
                    Button(
                        onClick = onRemoveAll,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                    ) {
                        Text("Eliminar lectura")
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
private fun FinalizeReadingRow(product: ProductoResumen) {
    val pending = product.pendientes > 0
    val invalid = !product.existe && !pending
    val status = when {
        pending -> "Pendiente"
        invalid -> "Inválido"
        else -> "Catálogo"
    }
    val statusColor = when {
        pending -> PmmAmber
        invalid -> PmmRed
        else -> PmmGreen
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (invalid) Color(0xFFFFF7F7) else PmmBgCard,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when {
                    invalid -> Icons.Default.WarningAmber
                    pending -> Icons.Default.Sync
                    else -> Icons.Default.CheckCircle
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.cidproduct,
                    color = PmmTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = product.descripcion?.takeIf(String::isNotBlank)
                        ?: if (invalid) "No pertenece al catálogo" else "Producto en catálogo",
                    color = PmmTextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "x${product.cantidad}",
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = status,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FinalizeDialog(
    preview: ResumenBarcodeResponse,
    onDismiss: () -> Unit,
    onFinalize: (Boolean, String) -> Unit
) {
    var observations by remember {
        mutableStateOf(preview.sesion?.observaciones.orEmpty())
    }
    var sendEmail by remember { mutableStateOf(true) }
    val products = preview.productos.orEmpty()
    val total = products.sumOf(ProductoResumen::cantidad)
    val catalog = products.filter(ProductoResumen::existe)
        .sumOf(ProductoResumen::cantidad)
    val unknown = products.filter { !it.existe && it.pendientes == 0 }
        .sumOf(ProductoResumen::cantidad)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.90f)
                .widthIn(max = 760.dp),
            shape = RoundedCornerShape(22.dp),
            color = PmmBgSurface
        ) {
            Column(
                modifier = Modifier.padding(22.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "Confirmar cierre de sesión",
                    color = PmmTextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Este resumen viene directamente del servidor.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Lecturas", total, PmmBlueDeep, Modifier.weight(1f))
                    MetricCard("Catálogo", catalog, PmmGreen, Modifier.weight(1f))
                    MetricCard("Inválidos", unknown, PmmRed, Modifier.weight(1f))
                }
                if (unknown > 0) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        color = Color(0xFFFFF1F1),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, null, tint = PmmRed)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                "$unknown lectura(s) no coinciden con el catálogo.",
                                color = PmmRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Detalle de lecturas",
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                if (products.isEmpty()) {
                    Text(
                        text = "No hay lecturas en el resumen.",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    products.forEach { product ->
                        FinalizeReadingRow(product)
                        Spacer(Modifier.height(6.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = observations,
                    onValueChange = { observations = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Observaciones") },
                    minLines = 3,
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PmmBlueDeep,
                        focusedLabelColor = PmmBlueDeep
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = sendEmail,
                        onCheckedChange = { sendEmail = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PmmBlueDeep,
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = "Enviar reporte por correo",
                        color = PmmTextPrimary,
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("Seguir corrigiendo")
                    }
                    Button(
                        onClick = { onFinalize(sendEmail, observations.trim()) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                    ) {
                        Text("Finalizar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FinalizingDialog() {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.84f).widthIn(max = 440.dp),
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(34.dp),
                    strokeWidth = 3.dp,
                    color = PmmBlueDeep
                )
                Column {
                    Text(
                        text = "Finalizando revisión",
                        color = PmmTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cerrando la sesión y preparando el reporte...",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun FinalResultDialog(
    result: FinalizarBarcodeResponse,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.9f).widthIn(max = 620.dp),
            shape = RoundedCornerShape(24.dp),
            color = PmmBgSurface
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = PmmGreen.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = PmmGreen,
                        modifier = Modifier.padding(14.dp).size(42.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Sesión finalizada",
                    color = PmmTextPrimary,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "El conteo quedó guardado correctamente.",
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
                result.correo?.takeIf { it.intentado }?.let { mail ->
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = PmmNavyVariant,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (mail.enProceso) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = PmmBlueDeep
                                )
                            } else {
                                Icon(
                                    if (mail.enviado) {
                                        Icons.Default.Mail
                                    } else {
                                        Icons.Default.WarningAmber
                                    },
                                    contentDescription = null,
                                    tint = if (mail.enviado) PmmBlueDeep else PmmAmber
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = when {
                                        mail.enviado -> "Correo enviado"
                                        mail.enProceso -> "Enviando correo"
                                        else -> "No se pudo enviar el correo"
                                    },
                                    color = PmmTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = when {
                                        mail.destinatarios.isNotEmpty() ->
                                            mail.destinatarios.joinToString(", ")
                                        mail.enProceso ->
                                            "Puedes salir; el envío continúa en el servidor."
                                        else ->
                                            "Contacta a soporte si este error persiste."
                                    },
                                    color = PmmTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
                result.analisisSemantico
                    .orEmpty()
                    .filter { it.veredicto != "mismo_producto" }
                    .takeIf(List<*>::isNotEmpty)
                    ?.let { findings ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFFFF8E1),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    "Observaciones del análisis",
                                    color = PmmAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                findings.take(3).forEach { finding ->
                                    Text(
                                        "• ${finding.cidproduct}: ${finding.explicacion}",
                                        color = PmmTextSecondary,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(top = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Text("Volver a pendientes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun barcodeFilterColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = PmmBlueDeep,
    selectedLabelColor = Color.White,
    containerColor = PmmBgSurface,
    labelColor = PmmTextSecondary
)

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val remaining = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remaining)
    } else {
        "%02d:%02d".format(minutes, remaining)
    }
}
