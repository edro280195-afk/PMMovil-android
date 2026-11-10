package com.example.pmmovil.ui.casetaentrada

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.example.pmmovil.R
import com.example.pmmovil.ui.common.SavingProgressState
import com.example.pmmovil.ui.common.SavingProgressStep
import com.example.pmmovil.ui.common.SavingStepStatus
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmBlueLight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.key
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import coil.compose.AsyncImage
import com.example.pmmovil.data.model.CasetaEntradaCatalogItem
import com.example.pmmovil.data.model.CasetaEntradaDraftEntity
import com.example.pmmovil.data.model.CasetaEntradaFrequentItem
import com.example.pmmovil.data.model.CasetaPreregistroValidarResponse
import com.example.pmmovil.data.model.TipoRegistro
import com.example.pmmovil.ui.camera.LocalEvidenceCameraScreen
import com.example.pmmovil.ui.camera.LocalEvidencePhoto
import com.example.pmmovil.ui.components.UnsavedChangesDialog
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CasetaEntradaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    casetaId: String? = null,
    onSaved: () -> Unit = {},
    onOpenLabelDesigner: () -> Unit = {},
    startWithCamera: Boolean = false,
    /**
     * Entrar directo al lector de QR de pre-registro. Lo usa el boton de
     * "Pre-registro" del hub de caseta: el camion llega con su QR y el guardia
     * no tiene por que pasar antes por el formulario vacio.
     */
    startWithPreregistroScan: Boolean = false,
    viewModel: CasetaEntradaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 76) }
    var cameraMode by rememberSaveable { mutableStateOf<CasetaEntradaCameraMode?>(null) }
    var previewPhoto by remember { mutableStateOf<CasetaEntradaPreviewPhoto?>(null) }
    var pendingDeletePhoto by remember { mutableStateOf<CasetaEntradaPreviewPhoto?>(null) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showClearConfirm by rememberSaveable { mutableStateOf(false) }
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    var showPrintDialog by rememberSaveable { mutableStateOf(false) }
    var autoHistoryMode by rememberSaveable { mutableStateOf<CasetaEntradaCameraMode?>(null) }
    var lastAutoPhotoPath by remember { mutableStateOf<String?>(null) }
    var liveCaptureMode by rememberSaveable { mutableStateOf<CasetaEntradaCaptureMode?>(if (startWithCamera && state.puedeAgregar) CasetaEntradaCaptureMode.Plate else null) }
    // Paso visible (Camión, Caja, Evidencia) y pantalla de selección abierta (cliente, líneas, tipo de caja, país).
    var step by rememberSaveable { mutableStateOf(CasetaStep.Camion) }
    var picker by rememberSaveable { mutableStateOf<CasetaPicker?>(null) }
    val editMode = !casetaId.isNullOrBlank()

    // Volver atrás (gesto, botón o flecha) no tira lo capturado: si hay cambios sin guardar, pregunta primero. Los
    // visores y diálogos de arriba registran su propio «atrás» después de este, así que ellos van primero.
    val requestExit = {
        if (viewModel.hasUnsavedChanges()) showExitConfirm = true else onNavigateBack()
    }
    BackHandler(onBack = requestExit)

    if (!editMode) {
        HardwareScannerListener(viewModel::onScannerInput)
    }

    // rememberSaveable: al volver del escaner esta pantalla se recompone, y sin
    // la bandera persistida volveriamos a lanzar la camara en un bucle.
    var preregistroScanLaunched by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startWithPreregistroScan) {
        if (startWithPreregistroScan && !editMode && !preregistroScanLaunched) {
            preregistroScanLaunched = true
            onNavigateToScanner()
        }
    }

    LaunchedEffect(barcodeScanResult) {
        if (editMode) return@LaunchedEffect
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.onScannerInput(it)
            onBarcodeScanConsumed()
        }
    }

    LaunchedEffect(casetaId) {
        if (!casetaId.isNullOrBlank()) viewModel.loadRecord(casetaId)
    }

    LaunchedEffect(state.savedSeq) {
        if (state.savedSeq > 0) {
            // Un tractocamion sin caja no tiene etiqueta que imprimir: savedCaja
            // llega como "" (no null, numCaja nunca es null en el request), asi
            // que hay que revisar que no este en blanco, no solo que no sea null.
            if (state.savedCasetaId != null && !state.savedCaja.isNullOrBlank()) {
                showPrintDialog = true
            } else {
                onSaved()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { tone.release() }
    }

    LaunchedEffect(state.feedbackSeq) {
        if (state.feedbackSeq == 0) return@LaunchedEffect
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        runCatching { tone.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    if (showPrintDialog && state.savedCasetaId != null && !state.savedCaja.isNullOrBlank()) {
        val casetaId = state.savedCasetaId!!
        val caja = state.savedCaja!!
        val bitmapPreview = remember(casetaId, caja) {
            viewModel.renderLabelPreview(casetaId, caja)
        }

        Dialog(onDismissRequest = {
            showPrintDialog = false
            onSaved()
        }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PmmBgSurface,
                border = BorderStroke(1.dp, PmmNavyBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ETIQUETA DE CASETA",
                                color = PmmTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Caja: $caja",
                                color = PmmTextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = {
                            showPrintDialog = false
                            onSaved()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PmmTextSecondary)
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, PmmNavyBorder, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bitmapPreview.asImageBitmap(),
                            contentDescription = "Vista previa de etiqueta",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = "Código CasetaID: $casetaId",
                        color = PmmTextMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(18.dp))

                    OutlinedButton(
                        onClick = {
                            onOpenLabelDesigner()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PmmNavyBorder)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Editar formato de etiqueta", fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                showPrintDialog = false
                                onSaved()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PmmNavyBorder)
                        ) {
                            Text("Cerrar", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                viewModel.printCasetaLabel(casetaId, caja)
                            },
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PmmYellow,
                                contentColor = PmmNavyDark
                            )
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Imprimir", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }

    // Pre-registro escaneado, pendiente de que el guardia coteje las placas
    // fisicas contra las del QR. Bloqueante a proposito: mientras esto este
    // abierto, el formulario no tiene ni un campo lleno todavia -- se decide
    // aqui, no en una tarjeta suelta mas abajo en el scroll.
    state.preregistroPendiente?.let { pendiente ->
        PreregistroConfirmModal(
            response = pendiente,
            onCoinciden = viewModel::confirmarPreregistroPlacasCoinciden,
            onNoCoinciden = viewModel::rechazarPreregistroPlacasNoCoinciden,
            onCancelar = viewModel::cancelarPreregistroPendiente
        )
    }

    val autoMode = autoHistoryMode
    val autoCapture = state.capture
    val autoCaptureForMode = when (autoMode) {
        CasetaEntradaCameraMode.Plate -> autoCapture.takeIf { it.mode == CasetaEntradaCaptureMode.Plate }
        CasetaEntradaCameraMode.Box -> autoCapture.takeIf { it.mode == CasetaEntradaCaptureMode.Box }
        else -> null
    }
    val autoOcrLoading = autoCaptureForMode?.isLoading == true
    val autoCandidate = autoCaptureForMode?.primaryValue.orEmpty()
    val showAutoSearch = autoMode != null && (autoOcrLoading || state.isLoadingHistory) &&
        autoCaptureForMode?.phase != CasetaEntradaOcrPhase.Idle

    LaunchedEffect(autoMode, autoOcrLoading, state.isLoadingHistory, autoCandidate, state.historySuggestions, autoCapture.phase) {
        if (autoMode == null) return@LaunchedEffect
        if (autoOcrLoading || state.isLoadingHistory) return@LaunchedEffect
        val modeCapture = autoCaptureForMode ?: return@LaunchedEffect
        if (modeCapture.phase !in setOf(
                CasetaEntradaOcrPhase.Ready,
                CasetaEntradaOcrPhase.Failed
            )
        ) return@LaunchedEffect

        if (autoCandidate.isNotBlank() || modeCapture.outcome == CasetaEntradaOcrOutcome.AutoApplied) {
            showHistorySheet = true
        }
        autoHistoryMode = null
    }

    if (state.showImportQuestion) {
        RequiresImportDialog(
            onNo = { viewModel.answerRequiresImport(false) },
            onYes = { viewModel.answerRequiresImport(true) },
            onDismiss = viewModel::dismissDialogs
        )
    }

    if (state.showConfirm) {
        SaveConfirmationDialog(
            state = state,
            updateMode = editMode,
            onDismiss = viewModel::dismissDialogs,
            onConfirm = viewModel::confirmSave
        )
    }

    if (state.savingProgress.isVisible) {
        SavingProgressOverlay(
            state = state.savingProgress,
            onClose = viewModel::closeSavingOverlay
        )
    }

    if (showClearConfirm) {
        ClearFormConfirmationDialog(
            onDismiss = { showClearConfirm = false },
            onConfirm = {
                showClearConfirm = false
                viewModel.resetForm()
                step = CasetaStep.Camion
            }
        )
    }

    if (showExitConfirm) {
        UnsavedChangesDialog(
            title = "¿Seguro que quieres salir?",
            message = if (editMode) {
                "Los cambios que hiciste a este registro todavía no se han guardado. Si sales ahora, se pierden."
            } else {
                "Lo que llevas capturado todavía no se ha guardado. Si sales ahora, se pierde."
            },
            discardLabel = "Salir sin guardar",
            onDiscard = {
                showExitConfirm = false
                viewModel.discardCurrentDraft()
                onNavigateBack()
            },
            onStay = { showExitConfirm = false }
        )
    }

    // No se muestra en modo edicion: ahi ya se esta trabajando un casetaId
    // puntual y este aviso de "capturas sin terminar" solo confundiria.
    if (!editMode && state.showDraftRecovery) {
        CasetaEntradaDraftRecoveryDialog(
            drafts = state.drafts,
            onResume = viewModel::resumeDraft,
            onDelete = viewModel::deleteDraft,
            onNew = viewModel::startNewCapture,
            onDismiss = viewModel::closeDraftRecovery
        )
    }

    pendingDeletePhoto?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDeletePhoto = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PmmRed) },
            title = { Text("Eliminar foto") },
            text = { Text("Esta evidencia se quitara del registro actual.") },
                    confirmButton = {
                        Button(
                            onClick = {
                        deletePreviewPhoto(photo, viewModel)
                        pendingDeletePhoto = null
                        // El visor sigue abierto y avanza a otra foto; solo se cierra solo
                        // cuando ya no queda ninguna (ver PhotoPreviewDialog).
                            },
                            enabled = state.puedeBorrar,
                            colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeletePhoto = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    previewPhoto?.let { photo ->
        val galleryPhotos = buildCasetaEntradaPreviewPhotos(state.form)
        val startIndex = galleryPhotos.indexOfFirst { it.id == photo.id }.coerceAtLeast(0)
        PhotoPreviewDialog(
            items = galleryPhotos,
            startIndex = startIndex,
            capture = state.capture,
            onDismiss = { previewPhoto = null },
            onReadPlate = { p ->
                if (p.localPath != null) {
                    viewModel.runSmartCaptureFromLocalPhoto(CasetaEntradaCaptureMode.Plate, p.localPath)
                } else if (p.remoteBase64 != null && p.remoteIndex != null) {
                    viewModel.runSmartCaptureFromBase64(
                        CasetaEntradaCaptureMode.Plate,
                        p.remoteBase64,
                        p.remoteIndex
                    )
                }
            },
            onReadBox = { p ->
                if (p.localPath != null) {
                    viewModel.runSmartCaptureFromLocalPhoto(CasetaEntradaCaptureMode.Box, p.localPath)
                } else if (p.remoteBase64 != null && p.remoteIndex != null) {
                    viewModel.runSmartCaptureFromBase64(
                        CasetaEntradaCaptureMode.Box,
                        p.remoteBase64,
                        p.remoteIndex
                    )
                }
            },
            onApplyCandidate = viewModel::applyCaptureCandidate,
            onRetry = viewModel::retryCapture,
            onDismissOcr = viewModel::dismissCapture,
            onDelete = { p -> pendingDeletePhoto = p },
            allowOcr = !editMode && state.puedeAgregar,
            canDelete = state.puedeBorrar
        )
    }

    if (showAutoSearch) {
        AiVisionAnalysisOverlay(
            imagePath = lastAutoPhotoPath,
            ocrState = autoCaptureForMode ?: state.capture,
            finishedMessage = null,
            onDismiss = {
                autoHistoryMode = null
                viewModel.dismissCapture()
            }
        )
    }

    cameraMode?.let { mode ->
        LocalEvidenceCameraScreen(
            title = when (mode) {
                CasetaEntradaCameraMode.Plate -> "Foto de placa"
                CasetaEntradaCameraMode.Box -> "Foto de caja"
                CasetaEntradaCameraMode.Evidence -> "Evidencia de caseta"
            },
            subtitle = state.form.casetaId.ifBlank { "Nuevo registro" },
            photos = casetaEntradaEvidencePhotos(state),
            maxPhotos = 15,
            canCapture = state.puedeAgregar,
            directoryName = "caseta_entrada_photos",
            filePrefix = "caseta_entrada",
            onPhotoCaptured = { path ->
                viewModel.addPhoto(path)
                when (mode) {
                    CasetaEntradaCameraMode.Plate -> {
                        autoHistoryMode = mode
                        lastAutoPhotoPath = path
                        cameraMode = null
                        viewModel.runSmartCaptureFromLocalPhoto(CasetaEntradaCaptureMode.Plate, path)
                    }
                    CasetaEntradaCameraMode.Box -> {
                        autoHistoryMode = mode
                        lastAutoPhotoPath = path
                        cameraMode = null
                        viewModel.runSmartCaptureFromLocalPhoto(CasetaEntradaCaptureMode.Box, path)
                    }
                    CasetaEntradaCameraMode.Evidence -> Unit
                }
            },
            onDeletePhoto = { photo ->
                when {
                    photo.id.startsWith("remote-") -> {
                        photo.id.removePrefix("remote-").toIntOrNull()?.let(viewModel::removeRemotePhoto)
                    }
                    photo.id.startsWith("local-") -> {
                        viewModel.removeLocalPhoto(photo.id.removePrefix("local-"))
                    }
                }
            },
            onClose = { cameraMode = null }
        )
        return
    }

    liveCaptureMode?.let { lm ->
        SmartCaptureLiveScreen(
            initialMode = lm,
            casetaId = state.form.casetaId,
            ocrState = state.capture,
            onConfirmValue = { capMode, value ->
                viewModel.updateText(
                    if (capMode == CasetaEntradaCaptureMode.Box) "boxNumber" else "plates",
                    value
                )
                viewModel.searchHistoryNow()
                liveCaptureMode = null
                showHistorySheet = true
            },
            onClose = {
                liveCaptureMode = null
                viewModel.dismissCapture()
            },
            onCapturePhoto = { capMode, path ->
                viewModel.runSmartCaptureFromLocalPhoto(capMode, path)
            },
            onDismissOcr = {
                viewModel.dismissCapture()
            }
        )
        return
    }

    Scaffold(
        snackbarHost = { PmmSnackbarHost(hostState = snackbar) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Fondo base minimalista y limpio
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
                        colors = listOf(Color(0xFFDBEAFE).copy(alpha = 0.5f), Color.Transparent),
                        center = Offset(0f, height * 0.35f),
                        radius = width * 0.85f
                    ),
                    radius = width * 0.85f,
                    center = Offset(0f, height * 0.35f)
                )
                // Destello inferior derecho (Verde suave característico de PMM)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDCFCE7).copy(alpha = 0.4f), Color.Transparent),
                        center = Offset(width, height),
                        radius = width * 0.7f
                    ),
                    radius = width * 0.7f,
                    center = Offset(width, height)
                )
            }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 840.dp
            val shortScreen = maxHeight < 700.dp
            val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
            val focusChain = remember { CasetaFormFocusChain() }
            val focusManager = LocalFocusManager.current
            val editable = if (editMode) state.puedeModificar else state.puedeAgregar
            val canSubmit = editable && !state.isLoadingCatalogs && !state.isSaving
            val statuses = remember(state.form) { CasetaEntradaSteps.statuses(state.form) }
            val stepAction = remember(state.form, step) { CasetaEntradaSteps.primaryAction(state.form, step) }
            val openPicker: (CasetaPicker) -> Unit = { kind ->
                when (kind) {
                    CasetaPicker.Customer -> viewModel.searchLookup(CasetaEntradaLookupType.Customer, "")
                    CasetaPicker.TruckLine -> viewModel.searchLookup(CasetaEntradaLookupType.TruckLine, "")
                    CasetaPicker.BoxLine -> viewModel.searchLookup(CasetaEntradaLookupType.BoxLine, "")
                    CasetaPicker.BoxType, CasetaPicker.Country -> Unit
                }
                picker = kind
            }

            // Si al guardar el sistema marca errores en un paso que no es el que se está viendo, lleva al primero.
            // Solo cuando aparecen (no al ir corrigiéndolos, para no saltar de paso a media captura).
            val hasFieldErrors = state.fieldErrors.isNotEmpty()
            LaunchedEffect(hasFieldErrors) {
                if (!hasFieldErrors) return@LaunchedEffect
                val errorSteps = state.fieldErrors.keys.map(CasetaEntradaSteps::stepOf)
                if (step !in errorSteps) step = errorSteps.first()
            }

            // El teclado empuja todo hacia arriba (imePadding): el paso se encoge y el campo enfocado no queda tapado.
            Column(modifier = Modifier.fillMaxSize().imePadding()) {
                CasetaEntradaHeader(
                    state = state,
                    updateMode = editMode,
                    onNavigateBack = requestExit,
                    onRefresh = {
                        viewModel.loadCatalogs(force = true)
                        viewModel.loadBoxTypes()
                        viewModel.loadFrequentCatalogs()
                    },
                    onClear = if (editMode) null else ({ showClearConfirm = true })
                )
                val waitingForRecord = editMode && (
                    state.isLoadingRecord ||
                        (state.form.casetaId.isBlank() && state.recordLoadError == null)
                    )
                if (waitingForRecord) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EditRecordLoadingCard(
                            casetaId = casetaId.orEmpty(),
                            hint = state.recordLoadingHint
                        )
                    }
                } else if (editMode && state.recordLoadError != null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EditRecordErrorCard(
                            message = state.recordLoadError.orEmpty(),
                            onRetry = { viewModel.loadRecord(casetaId.orEmpty()) },
                            onCancel = onNavigateBack
                        )
                    }
                } else {
                    // Con el teclado abierto en una pantalla baja (tableta acostada) la barra de pasos sobra: se
                    // esconde para darle el alto al formulario.
                    if (!(imeVisible && shortScreen)) {
                        CasetaStepBar(statuses = statuses, current = step, onSelect = { step = it })
                    }
                    key(step) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (step) {
                                CasetaStep.Camion -> CasetaStepCamion(
                                    state = state,
                                    viewModel = viewModel,
                                    editable = editable,
                                    editMode = editMode,
                                    wide = wide,
                                    focusChain = focusChain,
                                    focusManager = focusManager,
                                    onOpenSmartCapture = { liveCaptureMode = CasetaEntradaCaptureMode.Box },
                                    onScan = onNavigateToScanner,
                                    onOpenHistory = {
                                        // Si ya hay caja, placa o texto de búsqueda se busca de una vez; si no, la hoja
                                        // se abre vacía para escribir ahí (sin el aviso de «captura algo primero»).
                                        val term = state.historySearch
                                            .ifBlank { state.form.boxNumber }
                                            .ifBlank { state.form.plates }
                                            .trim()
                                        if (term.length >= 3) viewModel.searchHistoryNow()
                                        showHistorySheet = true
                                    },
                                    onOpenPicker = openPicker
                                )
                                CasetaStep.Caja -> CasetaStepCaja(
                                    state = state,
                                    viewModel = viewModel,
                                    editable = editable,
                                    wide = wide,
                                    focusChain = focusChain,
                                    focusManager = focusManager,
                                    onOpenPicker = openPicker
                                )
                                CasetaStep.Evidencia -> CasetaStepEvidencia(
                                    state = state,
                                    viewModel = viewModel,
                                    editable = editable,
                                    wide = wide,
                                    focusChain = focusChain,
                                    focusManager = focusManager,
                                    onTakePhoto = { cameraMode = CasetaEntradaCameraMode.Evidence },
                                    onPreviewPhoto = { previewPhoto = it },
                                    onDeletePhoto = { pendingDeletePhoto = it }
                                )
                            }
                        }
                    }
                    // Con el teclado abierto la barra de abajo se esconde: no hay lugar para ella.
                    if (!imeVisible) {
                        CasetaStepBottomBar(
                            action = stepAction,
                            step = step,
                            editMode = editMode,
                            isSaving = state.isSaving,
                            canSubmit = canSubmit,
                            onBack = when {
                                step != CasetaStep.Camion -> ({ step = CasetaStep.entries[step.ordinal - 1] })
                                editMode -> requestExit
                                else -> null
                            },
                            onAction = {
                                when (val current = stepAction) {
                                    is CasetaStepAction.Next -> step = current.target
                                    is CasetaStepAction.GoTo -> step = current.target
                                    is CasetaStepAction.Save -> viewModel.requestReview(updateMode = editMode)
                                }
                            }
                        )
                    }
                }
            }

            // Pantallas de selección y hojas de búsqueda: dentro de esta misma ventana para que respeten el teclado.
            if (showHistorySheet) {
                HistorySuggestionsSheet(
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { showHistorySheet = false }
                )
            }
            picker?.let { kind ->
                CasetaPickerPanel(
                    kind = kind,
                    state = state,
                    viewModel = viewModel,
                    onDismiss = { picker = null }
                )
            }
        }
    }
}

private enum class CasetaEntradaCameraMode {
    Plate,
    Box,
    Evidence
}

@Composable
private fun EditModeNotice() {
    Surface(
        color = PmmNavyVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFD7E3FF)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(20.dp)
            )
            Text(
                "Modo edicion: sin escaneo de caja ni sugerencias. Corrige los datos y guarda.",
                color = PmmTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun EditRecordLoadingCard(
    casetaId: String,
    hint: String
) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = PmmNavyVariant, shape = RoundedCornerShape(16.dp)) {
                Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = PmmBlueDeep,
                        strokeWidth = 4.dp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Cargando registro completo",
                    color = PmmTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    casetaId.ifBlank { "Preparando edición" },
                    color = PmmBlueDeep,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                hint.ifBlank { "Trayendo información y evidencia fotográfica..." },
                color = PmmTextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = PmmBlueDeep,
                trackColor = PmmNavyVariant
            )
        }
    }
}

@Composable
private fun EditRecordErrorCard(
    message: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = PmmRed.copy(alpha = 0.1f), shape = RoundedCornerShape(16.dp)) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = PmmRed,
                    modifier = Modifier.padding(16.dp).size(34.dp)
                )
            }
            Text(
                "No se pudo cargar el registro",
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                message,
                color = PmmTextSecondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(50.dp)
                ) {
                    Text("Cancelar", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onRetry,
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reintentar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

/**
 * Modal bloqueante que se abre justo despues de escanear un QR de pre-registro
 * VIGENTE. El formulario NO tiene ningun dato del pre-registro todavia -- eso
 * se decide aqui: "Si, coinciden" llena camion/linea/operador/placas; "No
 * coinciden" deja la pantalla en modo Tractocamion lista para captura manual,
 * sin precargar datos que ya se sabe que no corresponden al camion fisico.
 * Reemplaza la tarjeta que antes vivia suelta en medio del formulario con los
 * datos ya cargados de antemano -- el cotejo dejaba de ser un filtro real.
 */
@Composable
private fun PreregistroConfirmModal(
    response: CasetaPreregistroValidarResponse,
    onCoinciden: () -> Unit,
    onNoCoinciden: () -> Unit,
    onCancelar: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancelar,
        properties = DialogProperties(dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = PmmNavyDark,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = PmmYellow,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "PRE-REGISTRO ESCANEADO",
                    color = PmmYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "¿Las placas coinciden con el camión?",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            response.placas.ifBlank { "SIN PLACAS" },
                            color = Color.White,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.sp
                        )
                        val detalle = listOf(response.nunidad, response.operador)
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                        if (detalle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(detalle, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        if (response.folio.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(response.folio, color = Color.White.copy(alpha = 0.45f), fontSize = 11.sp)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onNoCoinciden,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmYellow),
                        border = BorderStroke(1.dp, PmmYellow)
                    ) {
                        Text("No coinciden", textAlign = TextAlign.Center, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onCoinciden,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmGreenDark)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Sí, coinciden", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    "Cancelar escaneo",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancelar)
                        .padding(6.dp)
                )
            }
        }
    }
}

/**
 * Recordatorio compacto (ya no interactivo -- el cotejo ya se resolvio en
 * PreregistroConfirmModal) de como quedo el cotejo de placas de este folio.
 */
@Composable
private fun PreregistroFolioStatusRow(folio: String, confirmadas: Boolean?) {
    if (folio.isBlank() || confirmadas == null) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (confirmadas) Color(0xFFECFDF5) else Color(0xFFFFFBEB),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                if (confirmadas) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (confirmadas) PmmGreenDark else PmmAmberDark,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (confirmadas) "Placas confirmadas contra el pre-registro $folio"
                       else "Placas NO coinciden con el pre-registro $folio · reportar a bodega",
                color = if (confirmadas) PmmGreenDark else PmmAmberDark,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun LegacyHistoryPromptCard(
    state: CasetaEntradaUiState,
    onOpen: () -> Unit
) {
    val count = state.historySuggestions.size
    Surface(
        color = PmmNavyVariant,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFD5E3FF)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(color = PmmBlueDeep, shape = RoundedCornerShape(8.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(7.dp).size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count > 0) "$count sugerencias historicas" else "Buscar sugerencias",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = state.historySearch.ifBlank { "Caja, placa, cliente o CasetaId" },
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = PmmBlueDeep)
        }
    }
}

@Composable
private fun LegacyHistorySuggestionsSection(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onDone: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sugerencias", color = PmmTextPrimary, fontWeight = FontWeight.SemiBold)
                Text("Reutiliza datos base sin copiar fotos", color = PmmTextSecondary, fontSize = 12.sp)
            }
            if (state.templateSource.isNotBlank()) {
                StatusChip(text = "Base ${state.templateSource}", tone = ChipTone.Success)
            }
        }

        HistoricalReuseGuide(state, onReview = onDone)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            CasetaTextField(
                value = state.historySearch,
                onValueChange = { viewModel.updateText("historySearch", it) },
                label = { Text("Buscar historico") },
                placeholder = { Text("Caja, placa, cliente") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.searchHistoryNow() })
            )
            Button(
                onClick = viewModel::searchHistoryNow,
                enabled = !state.isLoadingHistory,
                modifier = Modifier.height(56.dp),
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
            ) {
                if (state.isLoadingHistory) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
            }
        }

        if (state.historySuggestions.isEmpty() && !state.isLoadingHistory) {
            Surface(
                color = PmmBgCard,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, PmmNavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Toma foto, escanea o busca para mostrar coincidencias.",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.historySuggestions.take(5).forEach { item ->
                    LegacyHistorySuggestionCard(
                        item = item,
                        isLoading = state.isLoadingRecord,
                        onApply = {
                            viewModel.applyHistorySuggestion(item.casetaid) {
                                onDone?.invoke()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LegacyHistoricalReuseGuide(
    state: CasetaEntradaUiState,
    onReview: (() -> Unit)? = null
) {
    val hasTemplate = state.templateSource.isNotBlank()
    Surface(
        color = if (hasTemplate) Color(0xFFEFFAF3) else PmmNavyVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (hasTemplate) Color(0xFFB7E4C7) else Color(0xFFD7E3FF)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = if (hasTemplate) PmmGreenDark else PmmBlueDeep,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (hasTemplate) Icons.Default.CheckCircle else Icons.Default.Summarize,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasTemplate) {
                            "Registro historico cargado"
                        } else {
                            "Usa el historico para llenar datos"
                        },
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (hasTemplate) {
                            "Revisa los datos sugeridos antes de guardar la nueva entrada."
                        } else {
                            "Copia datos operativos, no copia folio ni fotos anteriores."
                        },
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryGuideCheck("Fecha de entrada nueva", true)
                HistoryGuideCheck("Cliente, Movimiento, TipoOperación y transportes requieren revision visual", false)
                HistoryGuideCheck("Puedes modificar cualquier campo antes de guardar", true)
            }

            if (hasTemplate && onReview != null) {
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmGreenDark,
                        contentColor = Color.White
                    )
                ) {
                    Text("Revisar formulario", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun LegacyHistoryGuideCheck(text: String, positive: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (positive) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
            contentDescription = null,
            tint = if (positive) PmmGreenDark else PmmAmber,
            modifier = Modifier.size(16.dp)
        )
        Text(text, color = PmmTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun LegacyHistorySuggestionsSheet(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                color = PmmBgSurface,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(color = PmmBlueDeep, shape = RoundedCornerShape(10.dp)) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(8.dp).size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Sugerencias historicas",
                                color = PmmTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Usa datos base sin copiar fotos ni folio anterior.",
                                color = PmmTextSecondary,
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PmmTextSecondary)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            HistorySuggestionsSection(
                                state = state,
                                viewModel = viewModel,
                                onDone = onDismiss
                            )
                        }
                    }
                }
            }

            if (state.isLoadingRecord) {
                HistoricalTemplateLoadingOverlay(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
        }
    }
}

@Composable
private fun LegacyHistoricalTemplateLoadingOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PmmBlueDeep,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = Color.White,
                strokeWidth = 3.dp
            )
            Text(
                "Trayendo datos historicos",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Text(
                "Preparando registro historico...",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 12.sp
            )
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.18f)
            )
        }
    }
}

@Composable
private fun LegacyHistorySuggestionCard(
    item: CasetaEntradaBusquedaItem,
    isLoading: Boolean,
    onApply: () -> Unit
) {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(text = item.casetaid.ifBlank { "S/C" }, tone = ChipTone.Info)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Caja " + item.nequipo.ifBlank { item.placas.ifBlank { "Sin caja" } },
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val opMov = listOf(item.tipoOp, item.tipoMov)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    if (opMov.isNotBlank()) {
                        Text(
                            text = opMov,
                            color = PmmBlueDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val clienteLabel = listOf(item.cliente, item.idCliente)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Cliente no definido" }
                    Text(
                        text = "Cliente: $clienteLabel",
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            HistorySummaryLine("Placas", item.placas.ifBlank { "Pendiente" })
            HistorySummaryLine("Caja", item.nequipo.ifBlank { "Pendiente" })
            HistorySummaryLine("Linea caja", item.transpore.ifBlank { "Pendiente" })
            Button(
                onClick = onApply,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmGreenDark),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Usar datos", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun LegacyHistorySummaryLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = PmmTextMuted, fontSize = 12.sp)
        Text(
            value,
            color = PmmTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
    }
}

@Composable
private fun HistoryPromptCard(
    state: CasetaEntradaUiState,
    onOpen: () -> Unit
) {
    val count = state.historySuggestions.size
    val query = state.historySearch
        .ifBlank { state.form.boxNumber }
        .ifBlank { state.form.plates }
        .ifBlank { "Caja, placa, cliente o CasetaId" }

    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFC9D8F6)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = PmmBlueDeep, shape = RoundedCornerShape(13.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = if (count > 0) "Historico listo" else "Buscar historico",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp
                )
                Text(
                    text = query,
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (count > 0) {
                HistoryCountPill("$count")
            }
            Icon(Icons.Default.ExpandMore, contentDescription = null, tint = PmmBlueDeep)
        }
    }
}

@Composable
private fun HistorySuggestionsSection(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onDone: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HistoricalReuseGuide(state, onReview = onDone)

        Surface(
            color = PmmBgSurface,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, PmmNavyBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Buscar coincidencias", color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                        Text("Usa caja, placas, cliente o CasetaId.", color = PmmTextSecondary, fontSize = 12.sp)
                    }
                    if (state.historySuggestions.isNotEmpty()) {
                        HistoryCountPill("${state.historySuggestions.size}")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    CasetaTextField(
                        value = state.historySearch,
                        onValueChange = { viewModel.updateText("historySearch", it) },
                        label = { Text("Historico") },
                        placeholder = { Text("Ej. 521094 o ABC123") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.searchHistoryNow() })
                    )
                    Button(
                        onClick = viewModel::searchHistoryNow,
                        enabled = !state.isLoadingHistory,
                        modifier = Modifier.height(56.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                    ) {
                        if (state.isLoadingHistory) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                        }
                    }
                }
            }
        }

        when {
            state.isLoadingHistory -> HistorySearchingCard(state.historySearch)
            state.historySuggestions.isEmpty() -> HistoryEmptyState()
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Coincidencias recientes", color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text("Max. 5", color = PmmTextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.historySuggestions.take(5).forEach { item ->
                        HistorySuggestionCard(
                            item = item,
                            isLoading = state.isLoadingRecord,
                            onApply = {
                                viewModel.applyHistorySuggestion(item.casetaid) {
                                    onDone?.invoke()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoricalReuseGuide(
    state: CasetaEntradaUiState,
    onReview: (() -> Unit)? = null
) {
    val hasTemplate = state.templateSource.isNotBlank()
    val accent = if (hasTemplate) PmmGreenDark else PmmBlueDeep
    val bg = if (hasTemplate) Color(0xFFEFFAF3) else PmmNavyVariant
    val border = if (hasTemplate) Color(0xFFB7E4C7) else Color(0xFFC9D8F6)

    Surface(
        color = bg,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Surface(color = accent, shape = RoundedCornerShape(14.dp)) {
                    Icon(
                        imageVector = if (hasTemplate) Icons.Default.CheckCircle else Icons.Default.Summarize,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(9.dp).size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasTemplate) "Registro historico cargado" else "Registro sugerido del historico",
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (hasTemplate) {
                            "Ya copiamos los datos base. Revisa antes de guardar."
                        } else {
                            "Copia datos operativos, no folio ni fotos anteriores."
                        },
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HistoryGuideCheck("Nueva entrada", true, Modifier.weight(1f))
                HistoryGuideCheck("Sin fotos viejas", true, Modifier.weight(1f))
            }
            HistoryGuideCheck(
                "Cliente, movimiento y transportes requieren revision visual",
                false,
                Modifier.fillMaxWidth()
            )

            if (hasTemplate && onReview != null) {
                Button(
                    onClick = onReview,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmGreenDark,
                        contentColor = Color.White
                    )
                ) {
                    Text("Revisar formulario", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryGuideCheck(
    text: String,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.70f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.60f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Icon(
                imageVector = if (positive) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (positive) PmmGreenDark else PmmAmber,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                color = PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistorySuggestionsSheet(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onDismiss: () -> Unit
) {
    // Dentro de la ventana de la pantalla (no un Dialog) y levantado sobre el teclado: con el teclado abierto la
    // lista se encoge en vez de quedar tapada.
    CasetaOverlayHost(
        onDismiss = onDismiss,
        top = false,
        maxHeight = 690.dp,
        overlay = if (state.isLoadingRecord) {
            {
                HistoricalTemplateLoadingOverlay(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
        } else {
            null
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(PmmBlueDeep, PmmNavyDark)))
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(14.dp)) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.padding(9.dp).size(21.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Historico de entradas",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp
                                )
                                Text(
                                    "Encuentra registros parecidos y reutiliza solo los datos base.",
                                    color = Color.White.copy(alpha = 0.76f),
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HistoryHeaderMetric("Busqueda", state.historySearch.ifBlank { "Pendiente" }, Modifier.weight(1f))
                            HistoryHeaderMetric("Resultados", state.historySuggestions.size.toString(), Modifier.weight(1f))
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            HistorySuggestionsSection(
                                state = state,
                                viewModel = viewModel,
                                onDone = onDismiss
                            )
                        }
                    }
        }
    }
}

@Composable
private fun HistoricalTemplateLoadingOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = PmmBgSurface,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color(0xFFC9D8F6)),
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(color = PmmNavyVariant, shape = RoundedCornerShape(18.dp)) {
                Box(modifier = Modifier.padding(14.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = PmmBlueDeep,
                        strokeWidth = 4.dp
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Trayendo datos historicos",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    "Preparando registro historico para esta entrada.",
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PmmBlueDeep,
                trackColor = PmmNavyVariant
            )
        }
    }
}

@Composable
private fun HistorySuggestionCard(
    item: CasetaEntradaBusquedaItem,
    isLoading: Boolean,
    onApply: () -> Unit
) {
    val title = item.nequipo.ifBlank { item.placas.ifBlank { "Sin unidad" } }
    val clienteLabel = listOf(item.cliente, item.idCliente)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
        .ifBlank { "Cliente no definido" }

    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFD5E3FF)),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = PmmNavyVariant, shape = RoundedCornerShape(13.dp)) {
                    Text(
                        text = item.casetaid.ifBlank { "S/C" },
                        color = PmmBlueDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = clienteLabel,
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(color = Color(0xFFEFF6FF), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = historyOperationLabel(item),
                        color = PmmBlueDeep,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                HistoryFieldTile("Caja", item.nequipo.ifBlank { "Pendiente" }, Modifier.weight(1f))
                HistoryFieldTile("Placas", item.placas.ifBlank { "Pendiente" }, Modifier.weight(1f))
            }
            HistoryFieldTile("Linea caja", item.transpore.ifBlank { "Pendiente" }, Modifier.fillMaxWidth())

            Button(
                onClick = onApply,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Text("Usar datos", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HistoryHeaderMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.White.copy(alpha = 0.12f),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
            Text(label.uppercase(), color = Color.White.copy(alpha = 0.66f), fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text(
                value,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryFieldTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp)) {
            Text(label.uppercase(), color = PmmTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text(
                text = value,
                color = PmmTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HistoryCountPill(text: String) {
    Surface(
        color = PmmBlueDeep,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun HistorySearchingCard(term: String) {
    Surface(
        color = Color(0xFFEFF6FF),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFC9D8F6)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = PmmBlueDeep, strokeWidth = 3.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text("Buscando historico", color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text(
                    text = term.ifBlank { "Consultando registros anteriores..." },
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(color = PmmNavyVariant, shape = RoundedCornerShape(14.dp)) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.padding(10.dp).size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Sin coincidencias cargadas", color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                Text("Toma foto, escanea o busca para mostrar sugerencias.", color = PmmTextSecondary, fontSize = 12.sp)
            }
        }
    }
}

private fun historyOperationLabel(item: CasetaEntradaBusquedaItem): String {
    return listOf(item.tipoOp, item.tipoMov)
        .firstOrNull { it.isNotBlank() }
        ?.take(18)
        ?: "Historico"
}

@Composable
private fun HistorySummaryLine(label: String, value: String) {
    LegacyHistorySummaryLine(label, value)
}

@Composable
private fun SmartCaptureCard(
    state: CasetaEntradaOcrState,
    onApply: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (state.outcome) {
        CasetaEntradaOcrOutcome.AutoApplied -> Color(0xFFECFDF5) to Color(0xFFA7F3D0)
        CasetaEntradaOcrOutcome.NeedsChoice -> Color(0xFFFFF7ED) to Color(0xFFFED7AA)
        CasetaEntradaOcrOutcome.ManualRequired,
        CasetaEntradaOcrOutcome.Error -> Color(0xFFFEF2F2) to Color(0xFFFECACA)
        CasetaEntradaOcrOutcome.Pending -> Color(0xFFEFF6FF) to Color(0xFFBFDBFE)
    }
    val titleText = when (state.mode) {
        CasetaEntradaCaptureMode.Box -> "Captura de caja"
        CasetaEntradaCaptureMode.Plate -> "Captura de placa"
        null -> "Captura inteligente"
    }
    Surface(
        color = accent.first,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.second),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PmmBlueDeep)
                } else {
                    Icon(
                        imageVector = when (state.outcome) {
                            CasetaEntradaOcrOutcome.AutoApplied -> Icons.Default.CheckCircle
                            CasetaEntradaOcrOutcome.NeedsChoice -> Icons.Default.WarningAmber
                            CasetaEntradaOcrOutcome.ManualRequired,
                            CasetaEntradaOcrOutcome.Error -> Icons.Default.ErrorOutline
                            CasetaEntradaOcrOutcome.Pending -> Icons.Default.PhotoCamera
                        },
                        contentDescription = null,
                        tint = when (state.outcome) {
                            CasetaEntradaOcrOutcome.AutoApplied -> PmmGreenDark
                            CasetaEntradaOcrOutcome.NeedsChoice -> PmmAmber
                            CasetaEntradaOcrOutcome.ManualRequired,
                            CasetaEntradaOcrOutcome.Error -> PmmRed
                            CasetaEntradaOcrOutcome.Pending -> PmmBlueDeep
                        }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = titleText,
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.message ?: state.phase.label,
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            SmartCapturePhaseTimeline(state = state)

            if (state.primaryValue.isNotBlank() && state.outcome != CasetaEntradaOcrOutcome.AutoApplied) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, accent.second)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Mejor candidato",
                            color = PmmTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = state.primaryValue,
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        state.confidence?.let { conf ->
                            ConfidenceBar(confidence = conf)
                        }
                    }
                }
            }

            if (state.candidates.isNotEmpty() && state.outcome != CasetaEntradaOcrOutcome.AutoApplied) {
                Text(
                    text = "Otras opciones",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.candidates
                        .filter { it.value != state.primaryValue }
                        .forEach { candidate ->
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, accent.second),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onApply(candidate.value) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = candidate.value,
                                            color = PmmTextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = candidate.reason ?: candidate.source,
                                            color = PmmTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    candidate.confidence?.let { conf ->
                                        ConfidenceBar(confidence = conf, compact = true)
                                    }
                                }
                            }
                        }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.outcome == CasetaEntradaOcrOutcome.AutoApplied) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cerrar")
                    }
                } else if (state.outcome == CasetaEntradaOcrOutcome.NeedsChoice && state.candidates.isNotEmpty()) {
                    Button(
                        onClick = { onApply(state.primaryValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = PmmGreenDark),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Text(if (state.mode == CasetaEntradaCaptureMode.Box) "Usar caja" else "Usar placa")
                    }
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reintentar")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                } else {
                    Button(
                        onClick = onRetry,
                        enabled = !state.isLoading,
                        colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                        modifier = Modifier.weight(1.4f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartCapturePhaseTimeline(state: CasetaEntradaOcrState) {
    val orderedPhases = listOf(
        CasetaEntradaOcrPhase.Captured,
        CasetaEntradaOcrPhase.PreparingImage,
        CasetaEntradaOcrPhase.ReadingLocal,
        CasetaEntradaOcrPhase.ValidatingCandidates,
        CasetaEntradaOcrPhase.ValidatingServer,
        CasetaEntradaOcrPhase.SearchingHistory,
        CasetaEntradaOcrPhase.Ready
    )
    val reachedIndex = orderedPhases.indexOf(state.phase).let { if (it < 0) 0 else it }
    val isFailed = state.phase == CasetaEntradaOcrPhase.Failed
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        orderedPhases.forEachIndexed { index, phase ->
            val active = index <= reachedIndex && !isFailed
            val current = index == reachedIndex && state.isLoading
            SmartCapturePhaseRow(
                label = phase.label,
                description = if (current) phase.description else null,
                state = when {
                    isFailed && index == reachedIndex -> SmartCapturePhaseState.Failed
                    current -> SmartCapturePhaseState.Active
                    active -> SmartCapturePhaseState.Done
                    else -> SmartCapturePhaseState.Pending
                }
            )
        }
    }
}

private enum class SmartCapturePhaseState {
    Pending,
    Active,
    Done,
    Failed
}

@Composable
private fun SmartCapturePhaseRow(
    label: String,
    description: String?,
    state: SmartCapturePhaseState
) {
    val (icon, tint) = when (state) {
        SmartCapturePhaseState.Pending -> Icons.Default.PhotoCamera to PmmTextMuted
        SmartCapturePhaseState.Active -> Icons.Default.Refresh to PmmBlueDeep
        SmartCapturePhaseState.Done -> Icons.Default.CheckCircle to PmmGreenDark
        SmartCapturePhaseState.Failed -> Icons.Default.ErrorOutline to PmmRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state == SmartCapturePhaseState.Active) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = tint)
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (state == SmartCapturePhaseState.Pending) PmmTextMuted else PmmTextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            description?.let {
                Text(
                    text = it,
                    color = PmmTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ConfidenceBar(confidence: Double, compact: Boolean = false) {
    val safe = confidence.coerceIn(0.0, 1.0)
    val tone = when {
        safe >= 0.78 -> PmmGreenDark
        safe >= 0.55 -> PmmAmber
        else -> PmmRed
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Confianza", color = PmmTextMuted, fontSize = 11.sp)
                Text(
                    "${(safe * 100).toInt()}%",
                    color = tone,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        LinearProgressIndicator(
            progress = { safe.toFloat() },
            color = tone,
            trackColor = tone.copy(alpha = 0.16f),
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 4.dp else 6.dp)
        )
    }
}

/**
 * Tarjeta grande con emoji real de camión (no un ícono abstracto) para elegir
 * el tipo de registro. Va al inicio del formulario -- el usuario pidió
 * explícitamente que esta elección viviera aquí, no en una pantalla aparte
 * antes de llegar al formulario (ver registro-tractocamiones-tiporegistro).
 */
@Composable
private fun TipoRegistroCard(
    iconRes: Int,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) Color(0xFF003DA5) else Color(0xFFF1F5FA),
        animationSpec = tween(durationMillis = 180),
        label = "tipo-registro-bg"
    )
    val contentColor = if (selected) Color.White else PmmTextPrimary

    Surface(
        modifier = modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clickable(enabled = enabled, onClick = onClick),
        color = background,
        shape = RoundedCornerShape(16.dp),
        border = if (selected) null else BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // La placa blanca ya viene "horneada" en el PNG (ver
            // registro-tractocamiones-tiporegistro) -- asi el camion siempre
            // contrasta, tambien cuando la tarjeta esta seleccionada en azul.
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.height(48.dp)
            )
            Text(
                text = title,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (selected) Color.White.copy(alpha = 0.86f) else PmmTextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PhotosSection(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onTakePhoto: () -> Unit,
    onPreviewPhoto: (CasetaEntradaPreviewPhoto) -> Unit,
    onDeletePhoto: (CasetaEntradaPreviewPhoto) -> Unit,
    canAdd: Boolean,
    canDelete: Boolean
) {
    val photoCount = state.form.photoCount
    FormCard(
        title = "Evidencia fotográfica",
        subtitle = "$photoCount/15 capturadas",
        icon = Icons.Default.PhotoCamera,
        action = {
            Button(
                onClick = onTakePhoto,
                enabled = canAdd && photoCount < 15,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF15803D), // Verde premium
                    disabledContainerColor = Color(0xFFE2E8F0)
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("+ Foto", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    ) {
        if (state.fieldErrors["photos"] != null) {
            ErrorText(state.fieldErrors["photos"].orEmpty())
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoadingRemotePhotos) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = PmmBlueDeep
                )
                Text(
                    text = "El registro ya está disponible; cargando fotos en segundo plano.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        if (state.remotePhotosLoadIncomplete && !state.isLoadingRemotePhotos) {
            Text(
                text = "Algunas fotos no pudieron mostrarse. El registro sigue disponible y las fotos guardadas se conservarán.",
                color = PmmAmberDark,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
        }

        if (photoCount > 0) {
            val previewPhotos = buildCasetaEntradaPreviewPhotos(state.form)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(previewPhotos, key = { it.id }) { photo ->
                    PhotoTile(
                        photo = photo,
                        isReading = state.capture.isLoading && state.capture.sourceId == photo.id,
                        onOpen = { onPreviewPhoto(photo) },
                        onDelete = { onDeletePhoto(photo) },
                        canDelete = canDelete
                    )
                }
            }
        } else {
            Surface(
                color = Color(0xFFF8FAFC),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = PmmTextMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Sin fotos. Toma evidencia si aplica.",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ObservationsSection(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    focusChain: CasetaFormFocusChain,
    focusManager: FocusManager,
    enabled: Boolean
) {
    FormCard(title = "Observaciones", subtitle = "Opcional", icon = Icons.Default.Summarize) {
        // Ultimo campo de la cadena: se deja el salto de linea por defecto del
        // teclado (es multilinea), solo se registra el FocusRequester para que
        // "Combustible"/"Pedimento"/etc puedan saltar aqui con Siguiente.
        CasetaTextField(
            value = state.form.observations,
            onValueChange = { viewModel.updateText("observations", it) },
            enabled = enabled,
            label = { Text("Observaciones") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.observations)
        )
    }
}

private enum class ChipTone {
    Neutral,
    Info,
    Success,
    Warning
}

@Composable
private fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier
) {
    val (background, border, content) = when (tone) {
        ChipTone.Neutral -> Triple(PmmBgCard, PmmNavyBorder, PmmTextSecondary)
        ChipTone.Info -> Triple(Color(0xFFEFF6FF), Color(0xFFBFDBFE), PmmBlueDeep)
        ChipTone.Success -> Triple(Color(0xFFECFDF5), Color(0xFFA7F3D0), PmmGreenDark)
        ChipTone.Warning -> Triple(Color(0xFFFFF7ED), Color(0xFFFED7AA), PmmAmber)
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, border),
        modifier = modifier.wrapContentWidth()
    ) {
        Text(
            text = text,
            color = content,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun EntradaCasetaBottomBar(
    isSaving: Boolean,
    canSave: Boolean,
    onReset: () -> Unit,
    onReview: () -> Unit
) {
    Surface(
        color = PmmBgSurface,
        tonalElevation = 2.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onReset,
                enabled = !isSaving,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Limpiar")
            }
            Button(
                onClick = onReview,
                enabled = canSave && !isSaving,
                modifier = Modifier.weight(1.4f),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                }
                Spacer(Modifier.width(6.dp))
                Text(if (isSaving) "Guardando" else "Validar y guardar")
            }
        }
    }
}

@Composable
private fun FormCard(
    title: String,
    icon: ImageVector,
    subtitle: String? = null,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val isPhoto = title.contains("Foto", ignoreCase = true) || title.contains("Evidencia", ignoreCase = true)
    val iconBgColor = if (isPhoto) Color(0xFFDCFCE7) else Color(0xFFEEF3FF)
    val iconColor = if (isPhoto) Color(0xFF15803D) else Color(0xFF003DA5)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(iconBgColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, color = PmmTextPrimary, fontSize = 16.sp)
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(it, color = PmmTextSecondary, fontSize = 12.sp)
                    }
                }
                if (action != null) {
                    action()
                }
            }
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            content()
        }
    }
}

@Composable
private fun CasetaEntradaPermissionNotice(
    message: String,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (enabled) Color(0xFFEFF6FF) else Color(0xFFFFFBEB),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (enabled) Color(0xFFBFDBFE) else Color(0xFFFDE68A))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (enabled) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                contentDescription = null,
                tint = if (enabled) PmmBlueDeep else PmmAmberDark,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                color = PmmTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun DecisionCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val background by animateColorAsState(
        targetValue = if (selected) Color(0xFF003DA5) else Color(0xFFF1F5FA),
        animationSpec = tween(durationMillis = 180),
        label = "decision-bg"
    )
    val contentColor = if (selected) Color.White else PmmTextPrimary
    
    Surface(
        modifier = modifier
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = background,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (compact) Arrangement.Center else Arrangement.spacedBy(8.dp)
        ) {
            if (!compact) {
                Icon(
                    imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = if (selected) Color.White else Color(0xFF003DA5),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                horizontalAlignment = if (compact) Alignment.CenterHorizontally else Alignment.Start,
                modifier = if (compact) Modifier else Modifier.weight(1f)
            ) {
                Text(
                    text = title.ifBlank { "Sin nombre" },
                    color = contentColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.takeIf(String::isNotBlank)?.let {
                    Text(
                        text = it,
                        color = if (selected) Color.White.copy(alpha = 0.86f) else PmmTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionDisplayCard(
    label: String,
    value: String,
    placeholder: String,
    error: String?,
    pending: Boolean = false,
    missing: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onClear: (() -> Unit)?
) {
    val pendingActive = pending && error == null
    // Obligatorio sin elegir: raya ámbar y la palabra «Falta» en lugar de la flecha.
    val missingActive = missing && error == null && !pendingActive
    val borderColor = when {
        error != null -> PmmRed
        pendingActive || missingActive -> PmmAmber
        else -> Color.Transparent
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = if (missingActive) Color(0xFFFFFBEB) else pendingPulseColor(pendingActive),
        shape = RoundedCornerShape(14.dp),
        border = if (error != null || pendingActive || missingActive) BorderStroke(1.dp, borderColor) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted)
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = value.ifBlank { placeholder },
                    color = if (value.isBlank()) PmmTextMuted else PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = if (value.isBlank()) FontWeight.Medium else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onClear != null) {
                IconButton(onClick = onClear, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = PmmTextSecondary, modifier = Modifier.size(18.dp))
                }
            } else if (missingActive) {
                Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = "Falta",
                        color = PmmAmberDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
                    )
                }
            } else {
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun EmptyLookupState() {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted)
            Text("Sin resultados. Prueba con otro codigo o nombre.", color = PmmTextSecondary)
        }
    }
}

/**
 * Lo que se ve en lugar de las tarjetas de «Tipo caja» cuando todavia no hay tipos: cargando, o sin red con un boton
 * para reintentar. Antes el selector quedaba en blanco, sin ningun aviso, y parecia que la pantalla no tenia ese campo.
 */
@Composable
private fun BoxTypesUnavailable(
    selectedLabel: String,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tipo caja", color = PmmTextSecondary, fontWeight = FontWeight.SemiBold)
        if (selectedLabel.isNotBlank()) {
            // Edicion de un registro: el tipo que ya tenia se sigue viendo aunque el catalogo no haya llegado.
            DecisionCard(
                title = selectedLabel,
                subtitle = null,
                selected = true,
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = PmmBlueDeep
                )
                Text("Cargando tipos de caja…", color = PmmTextSecondary)
            }
        } else {
            Text(
                "No se pudieron cargar los tipos de caja. Revisa la conexión e intenta de nuevo.",
                color = PmmRed,
                fontSize = 13.sp
            )
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }
        if (error != null) ErrorText(error)
    }
}

@Composable
private fun PhotoTile(
    photo: CasetaEntradaPreviewPhoto,
    isReading: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Box(
        modifier = Modifier
            .width(132.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(PmmBgCard)
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = photo.model,
            contentDescription = photo.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Surface(
            color = Color.Black.copy(alpha = 0.56f),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Text(
                text = photo.label,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (isReading) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(bottomEnd = 8.dp))
                    .padding(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
            }
        }
        IconButton(
            onClick = onDelete,
            enabled = canDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(42.dp)
                .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(bottomStart = 8.dp))
        ) {
            Icon(Icons.Default.Delete, contentDescription = "Eliminar foto", tint = Color.White)
        }
    }
}

private data class CasetaEntradaPreviewPhoto(
    val id: String,
    val model: Any,
    val label: String,
    val localPath: String? = null,
    val remoteIndex: Int? = null,
    val remoteBase64: String? = null
)

private fun buildCasetaEntradaPreviewPhotos(form: CasetaEntradaForm): List<CasetaEntradaPreviewPhoto> {
    val remote = form.remotePhotos.mapIndexed { index, remotePhoto ->
        CasetaEntradaPreviewPhoto(
            id = "remote-$index",
            model = remotePhoto.model,
            label = remotePhoto.label.ifBlank { "Guardada ${index + 1}" },
            remoteIndex = index,
            remoteBase64 = remotePhoto.base64.takeIf(String::isNotBlank)
        )
    }
    val local = form.localPhotoPaths.map { path ->
        CasetaEntradaPreviewPhoto(
            id = "local-$path",
            model = File(path),
            label = "Nueva",
            localPath = path
        )
    }
    return remote + local
}

@Composable
private fun PhotoPreviewDialog(
    items: List<CasetaEntradaPreviewPhoto>,
    startIndex: Int,
    capture: CasetaEntradaOcrState,
    onDismiss: () -> Unit,
    onReadPlate: (CasetaEntradaPreviewPhoto) -> Unit,
    onReadBox: (CasetaEntradaPreviewPhoto) -> Unit,
    onApplyCandidate: (String) -> Unit,
    onRetry: () -> Unit,
    onDismissOcr: () -> Unit,
    onDelete: (CasetaEntradaPreviewPhoto) -> Unit,
    allowOcr: Boolean,
    canDelete: Boolean
) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        ) { items.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(items.isEmpty()) {
            if (items.isEmpty()) onDismiss()
        }
        if (items.isEmpty()) return@Dialog

        val currentPhoto = items[pagerState.currentPage.coerceIn(0, items.size - 1)]
        val matchesPhoto = capture.sourceId == currentPhoto.id

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .fillMaxHeight(0.92f),
            color = PmmBgSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { items[it].id }
                    ) { page ->
                        ZoomableCasetaImage(
                            model = items[page].model,
                            contentDescription = items[page].label,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            if (items.size > 1) "${pagerState.currentPage + 1} / ${items.size}" else currentPhoto.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    if (items.size > 1) {
                        LazyRow(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(items, key = { _, it -> "thumb-${it.id}" }) { i, item ->
                                val selected = i == pagerState.currentPage
                                AsyncImage(
                                    model = item.model,
                                    contentDescription = item.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            2.dp,
                                            if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(visible = matchesPhoto && capture.phase != CasetaEntradaOcrPhase.Idle) {
                    SmartCaptureCard(
                        state = capture,
                        onApply = onApplyCandidate,
                        onRetry = onRetry,
                        onDismiss = onDismissOcr
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (allowOcr) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { onDelete(currentPhoto) },
                                enabled = canDelete,
                                modifier = Modifier.weight(1f).height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmRed)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Eliminar")
                            }
                            OutlinedButton(
                                onClick = { onReadBox(currentPhoto) },
                                enabled = !capture.isLoading,
                                modifier = Modifier.weight(1.2f).height(52.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmGreenDark)
                            ) {
                                Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Leer caja")
                            }
                            Button(
                                onClick = { onReadPlate(currentPhoto) },
                                enabled = !capture.isLoading,
                                modifier = Modifier.weight(1.4f).height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                            ) {
                                if (capture.isLoading && capture.sourceId == currentPhoto.id) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Leer placa")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onDelete(currentPhoto) },
                            enabled = canDelete,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmRed)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Eliminar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableCasetaImage(
    model: Any,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
                // Deteccion manual (no detectTransformGestures): esa funcion consume CUALQUIER
                // arrastre desde el primer dedo, incluso a escala 1x, y le gana el gesto al
                // HorizontalPager antes de que pueda pasar de foto. Aqui solo consumimos el
                // arrastre cuando hay pellizco (2+ dedos) o cuando ya estamos en zoom; un
                // arrastre de un dedo a 1x se deja sin consumir para que el pager lo reciba.
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (event.changes.size > 1 || scale > 1.02f) {
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + panChange else Offset.Zero
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

private fun deletePreviewPhoto(
    photo: CasetaEntradaPreviewPhoto,
    viewModel: CasetaEntradaViewModel
) {
    when {
        photo.localPath != null -> viewModel.removeLocalPhoto(photo.localPath)
        photo.remoteIndex != null -> viewModel.removeRemotePhoto(photo.remoteIndex)
    }
}

@Composable
private fun RequiresImportDialog(
    onNo: () -> Unit,
    onYes: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = PmmAmber) },
        title = { Text("¿Esta caja requiere importacion?") },
        text = {
            Text("Si respondes Si, el sistema guardara tambien un segundo registro de IMPORTACION como lo hace Caseta Web.")
        },
        confirmButton = {
            Button(onClick = onYes, colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)) {
                Text("Si requiere")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onNo) { Text("No requiere") }
        }
    )
}

@Composable
private fun ClearFormConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = PmmAmber) },
        title = { Text("Limpiar datos") },
        text = {
            Text("Se borraran los datos capturados del formulario actual y las fotos se quitaran de esta entrada.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
            ) {
                Text("Si, limpiar")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
private fun CasetaEntradaDraftRecoveryDialog(
    drafts: List<CasetaEntradaDraftEntity>,
    onResume: (CasetaEntradaDraftEntity) -> Unit,
    onDelete: (CasetaEntradaDraftEntity) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // Mide lo que pidan sus borradores (con tope): con uno solo ya no ocupa media pantalla vacía.
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
            color = PmmBgSurface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = PmmGreenDark, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Continúa donde te quedaste", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "Estas capturas de entrada a caseta se quedaron a medias. Incluyen datos y fotografías.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(drafts, key = { it.id }) { draft ->
                        CasetaEntradaDraftCard(draft, onResume, onDelete)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onNew,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmYellow,
                        contentColor = PmmNavyDark
                    )
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Iniciar captura nueva", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun CasetaEntradaDraftCard(
    draft: CasetaEntradaDraftEntity,
    onResume: (CasetaEntradaDraftEntity) -> Unit,
    onDelete: (CasetaEntradaDraftEntity) -> Unit
) {
    val subtitle = draft.customerName.ifBlank {
        draft.plates.ifBlank { draft.truckNumber.ifBlank { "Captura iniciada" } }
    }
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    draft.boxNumber.ifBlank { "Sin número de caja" },
                    color = PmmBlueDeep,
                    fontWeight = FontWeight.Black
                )
                Text(
                    subtitle,
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${draft.localPhotoPaths.size} fotos · ${
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(draft.lastEditAt))
                    }",
                    color = PmmTextSecondary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = { onDelete(draft) }) {
                Icon(Icons.Default.Delete, contentDescription = "Descartar", tint = PmmRed)
            }
            Button(onClick = { onResume(draft) }) { Text("Continuar") }
        }
    }
}

@Composable
private fun SaveConfirmationDialog(
    state: CasetaEntradaUiState,
    updateMode: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Summarize, contentDescription = null, tint = PmmBlueDeep) },
        title = { Text(if (updateMode) "Confirmar actualizacion" else "Confirmar entrada") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DialogSummaryRow("Operacion", state.form.operation)
                DialogSummaryRow("Movimiento", state.form.movement)
                if (!state.form.esTractocamion) {
                    DialogSummaryRow("Cliente", state.form.customerLabel)
                    DialogSummaryRow("Caja", state.form.boxNumber)
                    DialogSummaryRow("Linea caja", state.form.boxLineLabel)
                }
                // Los cuatro datos del tractor: se resaltan si el guardia los dejo vacios.
                DialogSummaryRow("Linea camion", state.form.truckLineLabel, pending = true)
                DialogSummaryRow("No camion", state.form.truckNumber, pending = true)
                DialogSummaryRow("Operador", state.form.operatorName, pending = true)
                DialogSummaryRow("Sellos", state.form.seals, pending = true)
                DialogSummaryRow("Fotos", state.form.photoCount.toString())
                if (state.form.operation == CasetaEntradaRules.OP_EXPORT) {
                    DialogSummaryRow("Requiere importacion", if (state.form.requiresImport) "Si" else "No")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !state.isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
            ) {
                Text(if (updateMode) "Actualizar" else "Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.isSaving) { Text("Revisar") }
        }
    )
}

@Composable
private fun DialogSummaryRow(label: String, value: String, pending: Boolean = false) {
    val highlight = pending && value.isBlank()
    Column {
        Text(label, color = PmmTextMuted, fontSize = 12.sp)
        Text(
            text = value.ifBlank { "Pendiente" },
            color = if (highlight) PmmAmber else PmmTextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = PmmRed, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
}

private fun errorText(message: String?): @Composable (() -> Unit)? {
    return message?.let { { ErrorText(it) } }
}

// ── Campos pendientes (ambar pulsante) ────────────────────────────────────────
// Marcan los datos del tractor que la plantilla ya no hereda. Recuerdan al guardia
// que faltan sin bloquear el guardado: no son obligatorios.

private val PendingPulseLow = Color(0xFFFEF3C7)
private val PendingPulseHigh = Color(0xFFFDE047)
private val PendingFieldContainer = Color(0xFFF1F5FA)

@Composable
private fun pendingPulseColor(active: Boolean, base: Color = PendingFieldContainer): Color {
    if (!active) return base
    val transition = rememberInfiniteTransition(label = "pendingPulse")
    val pulse by transition.animateColor(
        initialValue = PendingPulseLow,
        targetValue = PendingPulseHigh,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pendingPulseColor"
    )
    return pulse
}

@Composable
private fun PendingHintText() {
    Text(
        text = "Falta capturar",
        color = PmmAmber,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun pendingHint(active: Boolean): @Composable (() -> Unit)? {
    return if (active) ({ PendingHintText() }) else null
}

private fun casetaEntradaEvidencePhotos(state: CasetaEntradaUiState): List<LocalEvidencePhoto> {
    return buildList {
        state.form.remotePhotos.forEachIndexed { index, photo ->
            add(
                LocalEvidencePhoto(
                    id = "remote-$index",
                    model = photo.model,
                    label = photo.label.ifBlank { "Guardada ${index + 1}" },
                    status = "Guardada"
                )
            )
        }
        state.form.localPhotoPaths.forEach { path ->
            add(
                LocalEvidencePhoto(
                    id = "local-$path",
                    model = File(path),
                    label = "Nueva",
                    status = "Nueva"
                )
            )
        }
    }
}

@Composable
private fun CasetaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    isError: Boolean = false,
    pending: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Marco grueso para los campos que el guardia no debe pasar por alto (No camion, Operador, Sellos, Placas). */
    resaltado: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (resaltado) {
        CasetaTextFieldResaltado(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            label = label,
            placeholder = placeholder,
            singleLine = singleLine,
            isError = isError,
            pending = pending,
            supportingText = supportingText,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = modifier
        )
        return
    }

    // El error manda sobre el pendiente: si el campo esta mal, no lo pintamos de ambar.
    val pendingActive = pending && !isError
    val container = pendingPulseColor(pendingActive)
    val borderColor = if (pendingActive) PmmAmber else Color.Transparent
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = label,
        placeholder = placeholder,
        singleLine = singleLine,
        isError = isError,
        supportingText = supportingText ?: pendingHint(pendingActive),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = container,
            unfocusedContainerColor = container,
            disabledContainerColor = Color(0xFFF8FAFC),
            errorContainerColor = Color(0xFFFFF7ED),
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor,
            disabledBorderColor = Color.Transparent,
            errorBorderColor = Color.Transparent
        ),
        modifier = modifier
    )
}

/**
 * [CasetaTextField] con un marco grueso: azul institucional de 2 dp y de 3 dp al enfocarlo, ambar si falta capturarlo y
 * rojo si tiene error. El OutlinedTextField de Material no deja cambiar el grosor del borde, asi que se arma con
 * BasicTextField y la caja decorativa de Material (misma etiqueta flotante, mismo texto de apoyo).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CasetaTextFieldResaltado(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: @Composable (() -> Unit)?,
    placeholder: @Composable (() -> Unit)?,
    singleLine: Boolean,
    isError: Boolean,
    pending: Boolean,
    supportingText: @Composable (() -> Unit)?,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    modifier: Modifier
) {
    val pendingActive = pending && !isError
    val container = pendingPulseColor(pendingActive)
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = Color(0xFFF8FAFC),
        errorContainerColor = Color(0xFFFFF7ED),
        focusedBorderColor = if (pendingActive) PmmAmber else PmmBlueDeep,
        unfocusedBorderColor = if (pendingActive) PmmAmber else PmmNavyDark,
        disabledBorderColor = Color(0xFFCBD5E1),
        errorBorderColor = PmmRed
    )
    val textColor = when {
        !enabled -> colors.disabledTextColor
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        // Los 8 dp de arriba son el hueco que Material le deja a la etiqueta cuando flota sobre el marco.
        modifier = modifier
            .then(if (label != null) Modifier.padding(top = 8.dp) else Modifier)
            .defaultMinSize(minWidth = OutlinedTextFieldDefaults.MinWidth, minHeight = OutlinedTextFieldDefaults.MinHeight),
        enabled = enabled,
        textStyle = LocalTextStyle.current.merge(TextStyle(color = textColor)),
        cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        singleLine = singleLine,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                isError = isError,
                label = label,
                placeholder = placeholder,
                supportingText = supportingText ?: pendingHint(pendingActive),
                colors = colors,
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = isError,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = RoundedCornerShape(14.dp),
                        focusedBorderThickness = 3.dp,
                        unfocusedBorderThickness = 2.dp
                    )
                }
            )
        }
    )
}

@Composable
private fun SavingProgressOverlay(
    state: SavingProgressState,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = { if (state.hasError || state.isFinished) onClose() },
        properties = DialogProperties(dismissOnBackPress = state.hasError || state.isFinished, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF0F172A),
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Anillo de progreso / Indicador circular
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(76.dp)
                ) {
                    if (state.isFinished) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF166534),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(16.dp).fillMaxSize()
                            )
                        }
                    } else if (state.hasError) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF991B1B),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(16.dp).fillMaxSize()
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            progress = { state.percent / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = Color(0xFF38BDF8),
                            trackColor = Color(0xFF1E293B),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "${state.percent}%",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = state.currentTitle,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "Módulo Caseta · Guardado en progreso",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(18.dp))

                // Pasos del proceso
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E293B))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    state.steps.forEach { step ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier.size(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                when (step.status) {
                                    SavingStepStatus.SUCCESS -> Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF22C65E),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    SavingStepStatus.ERROR -> Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    SavingStepStatus.ACTIVE -> CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = Color(0xFF38BDF8),
                                        strokeWidth = 2.dp
                                    )
                                    SavingStepStatus.PENDING -> Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF64748B))
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = step.label,
                                    color = if (step.status == SavingStepStatus.PENDING) Color(0xFF64748B) else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = step.detail,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (state.hasError) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.errorMessage,
                        color = Color(0xFFFCA5A5),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                    ) {
                        Text("Cerrar")
                    }
                } else if (state.isFinished) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C65E))
                    ) {
                        Text("Listo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Orden de «siguiente campo» cuando el usuario da Enter/Siguiente en el teclado, DENTRO de cada paso. Cada paso es una
 * pantalla distinta: pasarle el foco a un campo de otro paso rompería (su FocusRequester no está compuesto).
 * Los campos condicionales solo entran a la cadena cuando su sección está visible.
 */
private class CasetaFormFocusChain {
    val boxNumber = FocusRequester()
    val plates = FocusRequester()
    val truckNumber = FocusRequester()
    val operatorName = FocusRequester()
    val seals = FocusRequester()
    val guideCount = FocusRequester()
    val pedimento = FocusRequester()
    val temperature = FocusRequester()
    val fuel = FocusRequester()
    val observations = FocusRequester()

    private fun order(step: CasetaStep, state: CasetaEntradaUiState): List<FocusRequester> = when (step) {
        CasetaStep.Camion -> buildList {
            if (!state.form.esTractocamion) add(boxNumber)
            add(plates)
            add(truckNumber)
            add(operatorName)
            add(seals)
        }
        CasetaStep.Caja -> buildList {
            if (state.requiresGuide) add(guideCount)
            if (state.form.operation == CasetaEntradaRules.OP_EXPORT) add(pedimento)
            if (state.isRefrigerated && !state.form.esTractocamion) {
                add(temperature)
                add(fuel)
            }
        }
        CasetaStep.Evidencia -> listOf(observations)
    }

    fun next(step: CasetaStep, state: CasetaEntradaUiState, current: FocusRequester, focusManager: FocusManager) {
        val chain = order(step, state)
        val target = chain.getOrNull(chain.indexOf(current) + 1)
        if (target != null) target.requestFocus() else focusManager.clearFocus()
    }
}

/** Encabezado compacto: el avance ya no vive aquí sino en la barra de pasos de abajo. */
@Composable
private fun CasetaEntradaHeader(
    state: CasetaEntradaUiState,
    updateMode: Boolean = false,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF003875), Color(0xFF001A38))))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(40.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
        ) {
            Text(
                text = if (updateMode) "EDICIÓN DE REGISTRO" else "ENTRADA A CASETA",
                fontSize = 16.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (updateMode) {
                Text(
                    text = state.form.casetaId.ifBlank { "Modificando datos" },
                    color = Color(0xFF93C5FD),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (state.lastDraftSavedAt != null) {
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Borrador guardado localmente",
                tint = Color(0xFF4ADE80),
                modifier = Modifier.size(18.dp)
            )
        }
        Surface(
            color = Color(0xFFEF4444),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                text = state.form.bodega.ifBlank { "B1 LAREDO" },
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        if (onClear != null) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(onClick = onClear, enabled = !state.isSaving) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Limpiar formulario",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.15f),
            modifier = Modifier.size(36.dp)
        ) {
            IconButton(onClick = onRefresh, enabled = !state.isLoadingCatalogs) {
                if (state.isLoadingCatalogs) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar catálogos",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Pasos de la captura: Camión → Caja → Evidencia
// Las reglas (qué falta en cada paso, qué hace el botón de abajo) viven en CasetaEntradaSteps.kt.
// ═══════════════════════════════════════════════════════════════════════════

/** Catálogos que se eligen en una pantalla de selección: ni lista desplegable ni hoja pegada abajo. */
private enum class CasetaPicker { Customer, TruckLine, BoxLine, BoxType, Country }

@Composable
private fun CasetaStepBar(
    statuses: List<CasetaStepStatus>,
    current: CasetaStep,
    onSelect: (CasetaStep) -> Unit
) {
    val last = statuses.lastIndex
    Surface(color = PmmBgSurface, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            statuses.forEachIndexed { index, status ->
                val isCurrent = status.step == current
                val statusColor = when (status.tone) {
                    CasetaStepTone.Done -> PmmGreenDark
                    CasetaStepTone.Missing -> PmmAmberDark
                    CasetaStepTone.Neutral -> PmmTextSecondary
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Tab) { onSelect(status.step) }
                        .semantics { selected = isCurrent }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .drawBehind {
                                // El hilo une los círculos pasando por detrás de cada uno, sin tocarlos.
                                val y = size.height / 2
                                val gap = 17.dp.toPx()
                                val stroke = 2.dp.toPx()
                                val color = PmmBlueLight.copy(alpha = 0.45f)
                                if (index > 0) drawLine(color, Offset(0f, y), Offset(size.width / 2 - gap, y), stroke)
                                if (index < last) drawLine(color, Offset(size.width / 2 + gap, y), Offset(size.width, y), stroke)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        CasetaStepBadge(
                            number = index + 1,
                            done = status.tone == CasetaStepTone.Done,
                            highlighted = isCurrent
                        )
                    }
                    Text(
                        text = status.step.label,
                        color = if (isCurrent) PmmBlueDeep else PmmTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = status.text,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Círculo de un paso: palomita verde si está completo, su número en azul si no (más fuerte si es el actual). */
@Composable
private fun CasetaStepBadge(number: Int, done: Boolean, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                when {
                    done -> PmmGreen
                    highlighted -> PmmBlueDeep
                    else -> PmmBlueDeep.copy(alpha = 0.55f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        } else {
            Text("$number", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp)
        }
    }
}

/**
 * Barra de abajo: «Atrás» (o «Cancelar» al editar) y el botón principal, que cambia según lo que falte:
 * «Siguiente», «Faltan N · ir a…» o «Guardar entrada». Avanzar nunca se bloquea; solo guardar exige lo obligatorio.
 */
@Composable
private fun CasetaStepBottomBar(
    action: CasetaStepAction,
    step: CasetaStep,
    editMode: Boolean,
    isSaving: Boolean,
    canSubmit: Boolean,
    onBack: (() -> Unit)?,
    onAction: () -> Unit
) {
    val saving = action is CasetaStepAction.Save
    val warn = action is CasetaStepAction.GoTo
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
            if (onBack != null) {
                OutlinedButton(
                    onClick = onBack,
                    enabled = !isSaving,
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmTextSecondary)
                ) {
                    if (step != CasetaStep.Camion) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Atrás", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Text("Cancelar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Button(
                onClick = onAction,
                enabled = if (saving) canSubmit else true,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
                border = if (warn) BorderStroke(2.dp, PmmAmber) else null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        saving -> PmmGreenDark
                        warn -> Color.White
                        else -> PmmBlueDeep
                    },
                    contentColor = if (warn) PmmAmberDark else Color.White,
                    disabledContainerColor = Color(0xFFE2E8F0)
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    when (action) {
                        is CasetaStepAction.Save -> {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (editMode) "Actualizar" else "Guardar entrada",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        is CasetaStepAction.Next -> {
                            Text("Siguiente: ${action.target.label}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        is CasetaStepAction.GoTo -> {
                            Text(
                                "Faltan ${action.missing} · ir a ${action.target.label}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Dos columnas en pantalla ancha (tableta acostada); una sola, una encima de otra, en cualquier otro caso. */
@Composable
private fun StepPanes(
    wide: Boolean,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit
) {
    if (wide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = left)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = right)
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            left()
            right()
        }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, color = PmmTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

/** Aviso corto de por qué aparece un campo condicional. */
@Composable
private fun StepHint(text: String) {
    Surface(
        color = Color(0xFFFFFBEB),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = PmmAmberDark, modifier = Modifier.size(16.dp))
            Text(text, color = PmmAmberDark, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Campos que solo aplican por lo elegido arriba: llevan una raya ámbar a la izquierda. */
@Composable
private fun StepConditional(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = PmmAmber,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 3.dp.toPx()
                )
            }
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** Tarjeta que abre una pantalla de selección. Los obligatorios vacíos dicen «Falta». */
@Composable
private fun PickerField(
    label: String,
    value: String,
    required: Boolean,
    missing: Boolean,
    error: String?,
    enabled: Boolean,
    onOpen: () -> Unit,
    pending: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SelectionDisplayCard(
            label = if (required) "$label *" else label,
            value = value,
            placeholder = "Toca para elegir",
            error = error,
            pending = pending,
            missing = missing && value.isBlank(),
            enabled = enabled,
            onClick = onOpen,
            onClear = if (value.isNotBlank() && onClear != null) onClear else null
        )
        if (error != null) ErrorText(error) else if (pending) PendingHintText()
    }
}

@Composable
private fun StepActionTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clickable(enabled = enabled, onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 2.dp,
        border = if (highlighted) null else BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (highlighted) {
                        Modifier.background(Brush.linearGradient(listOf(Color(0xFFE2ECFB), Color(0xFFFFFBEB))))
                    } else {
                        Modifier
                    }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(if (highlighted) PmmBlueDeep else Color(0xFF0284C7), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = title,
                        color = if (enabled) PmmTextPrimary else PmmTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (badge != null) {
                        Surface(color = Color(0xFFFDE047), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                text = badge,
                                color = Color(0xFF854D0E),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = subtitle,
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Paso 1: lo que el guardia ve en la puerta. */
@Composable
private fun CasetaStepCamion(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    editable: Boolean,
    editMode: Boolean,
    wide: Boolean,
    focusChain: CasetaFormFocusChain,
    focusManager: FocusManager,
    onOpenSmartCapture: () -> Unit,
    onScan: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPicker: (CasetaPicker) -> Unit
) {
    val form = state.form
    val tr = form.esTractocamion
    val errors = remember(form) { CasetaEntradaRules.validate(form) }

    if (!editable) {
        CasetaEntradaPermissionNotice(
            message = "Tienes acceso de lectura. Los campos y acciones que guardan cambios están deshabilitados.",
            enabled = false
        )
    }
    if (editMode) EditModeNotice()
    if (form.origenPreRegistro) {
        PreregistroFolioStatusRow(folio = state.preregistroFolio, confirmadas = state.preregistroPlacasConfirmadas)
    }

    val boxField: @Composable () -> Unit = {
        CasetaTextField(
            value = form.boxNumber,
            onValueChange = { viewModel.updateText("boxNumber", it) },
            enabled = editable,
            label = { Text("Número de caja *") },
            placeholder = { Text("Escríbelo o léelo con la cámara") },
            singleLine = true,
            isError = state.fieldErrors.containsKey("boxNumber"),
            supportingText = errorText(state.fieldErrors["boxNumber"]),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusChain.next(CasetaStep.Camion, state, focusChain.boxNumber, focusManager)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.boxNumber)
        )
    }
    val platesField: @Composable () -> Unit = {
        CasetaTextField(
            value = form.plates,
            onValueChange = { viewModel.updateText("plates", it) },
            enabled = editable,
            label = { Text(if (state.requiresCountry) "Placas *" else "Placas") },
            resaltado = true,
            singleLine = true,
            isError = state.fieldErrors.containsKey("plates"),
            supportingText = errorText(state.fieldErrors["plates"]),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusChain.next(CasetaStep.Camion, state, focusChain.plates, focusManager)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.plates)
        )
    }
    val truckField: @Composable () -> Unit = {
        CasetaTextField(
            value = form.truckNumber,
            onValueChange = { viewModel.updateText("truckNumber", it) },
            enabled = editable,
            label = { Text("No. de camión") },
            resaltado = true,
            singleLine = true,
            pending = "truckNumber" in state.pendingTruckFields,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusChain.next(CasetaStep.Camion, state, focusChain.truckNumber, focusManager)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.truckNumber)
        )
    }
    val operatorField: @Composable () -> Unit = {
        CasetaTextField(
            value = form.operatorName,
            onValueChange = { viewModel.updateText("operatorName", it) },
            enabled = editable,
            label = { Text("Operador") },
            resaltado = true,
            singleLine = true,
            pending = "operatorName" in state.pendingTruckFields,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                focusChain.next(CasetaStep.Camion, state, focusChain.operatorName, focusManager)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.operatorName)
        )
    }
    val sealsField: @Composable () -> Unit = {
        CasetaTextField(
            value = form.seals,
            onValueChange = { viewModel.updateText("seals", it) },
            enabled = editable,
            label = { Text("Sellos") },
            resaltado = true,
            singleLine = true,
            pending = "seals" in state.pendingTruckFields,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = {
                focusChain.next(CasetaStep.Camion, state, focusChain.seals, focusManager)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusChain.seals)
        )
    }

    StepPanes(
        wide = wide,
        left = {
            StepLabel("¿Qué registras?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TipoRegistroCard(
                    iconRes = R.drawable.ic_caseta_con_caja,
                    title = "Con caja",
                    subtitle = "Camión + caja/remolque",
                    selected = !tr,
                    onClick = { viewModel.setTipoRegistro(TipoRegistro.CAJA) },
                    enabled = editable,
                    modifier = Modifier.weight(1f)
                )
                // Desactivado a propósito: un tractocamión sin caja solo entra hoy por un pre-registro con QR, que
                // pone este modo por su cuenta (y entonces la tarjeta se ve elegida pero sin poder tocarse).
                TipoRegistroCard(
                    iconRes = R.drawable.ic_caseta_tractocamion,
                    title = "Solo tractocamión",
                    subtitle = "Sin caja, viene a recoger",
                    selected = tr,
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                )
            }
            if (!editMode) {
                val historyCount = state.historySuggestions.size
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (tr) {
                        StepActionTile(
                            icon = Icons.Default.QrCodeScanner,
                            title = "Leer QR",
                            subtitle = "Pre-registro del chofer",
                            enabled = state.puedeAgregar && !state.isSaving,
                            highlighted = true,
                            onClick = onScan,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        StepActionTile(
                            icon = Icons.Default.Camera,
                            title = "Cámara",
                            subtitle = "Lee caja y placa",
                            enabled = state.puedeAgregar && form.photoCount < 15 && !state.isSaving,
                            highlighted = true,
                            badge = "IA",
                            onClick = onOpenSmartCapture,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    StepActionTile(
                        icon = Icons.Default.History,
                        title = "Reusar anterior",
                        subtitle = when {
                            historyCount > 0 -> "Reusar ($historyCount)"
                            tr -> "Reusar datos"
                            else -> "Llena la caja y la línea"
                        },
                        enabled = !state.isSaving,
                        highlighted = false,
                        onClick = onOpenHistory,
                        modifier = Modifier.weight(1f)
                    )
                }
                // En pantalla ancha el avance de la lectura con IA sigue a la vista, como en la mesa de captura de antes.
                if (wide) {
                    AnimatedVisibility(visible = state.capture.phase != CasetaEntradaOcrPhase.Idle) {
                        SmartCaptureCard(
                            state = state.capture,
                            onApply = viewModel::applyCaptureCandidate,
                            onRetry = viewModel::retryCapture,
                            onDismiss = viewModel::dismissCapture
                        )
                    }
                }
            }
        },
        right = {
            PickerField(
                label = "Línea de camión",
                value = form.truckLineLabel,
                required = true,
                missing = errors.containsKey("truckLine"),
                error = state.fieldErrors["truckLine"],
                pending = "truckLine" in state.pendingTruckFields,
                enabled = editable,
                onOpen = { onOpenPicker(CasetaPicker.TruckLine) },
                onClear = { viewModel.clearSelection(CasetaEntradaLookupType.TruckLine) }
            )
            if (!tr) boxField()
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 400.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) { platesField() }
                            Box(modifier = Modifier.weight(1f)) { truckField() }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(modifier = Modifier.weight(1f)) { operatorField() }
                            Box(modifier = Modifier.weight(1f)) { sealsField() }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        platesField()
                        truckField()
                        operatorField()
                        sealsField()
                    }
                }
            }
            if (tr) {
                Text(
                    "Un tractocamión sin caja no lleva cliente, caja ni tipo de caja.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    )
}

/** Paso 2: lo que dice el papel. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CasetaStepCaja(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    editable: Boolean,
    wide: Boolean,
    focusChain: CasetaFormFocusChain,
    focusManager: FocusManager,
    onOpenPicker: (CasetaPicker) -> Unit
) {
    val form = state.form
    val tr = form.esTractocamion
    val errors = remember(form) { CasetaEntradaRules.validate(form) }

    StepPanes(
        wide = wide,
        left = {
            StepLabel("Operación")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(CasetaEntradaRules.OP_IMPORT, CasetaEntradaRules.OP_EXPORT).forEach { op ->
                    DecisionCard(
                        title = op,
                        subtitle = null,
                        selected = form.operation == op,
                        onClick = { viewModel.updateOperation(op) },
                        enabled = editable,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            state.fieldErrors["operation"]?.let { ErrorText(it) }

            if (form.operation.isNotBlank()) {
                StepLabel("Movimiento")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.availableMovements.forEach { movement ->
                        DecisionCard(
                            title = movement,
                            subtitle = null,
                            selected = form.movement == movement,
                            onClick = { viewModel.updateMovement(movement) },
                            enabled = editable
                        )
                    }
                }
                state.fieldErrors["movement"]?.let { ErrorText(it) }
            }

            val showPedimento = form.operation == CasetaEntradaRules.OP_EXPORT
            if (state.requiresCountry || state.requiresGuide || showPedimento) {
                StepConditional {
                    if (state.requiresCountry) {
                        StepHint("Por ${form.movement} se piden placas (en Camión) y país de origen.")
                        PickerField(
                            label = "País de origen",
                            value = form.countryLabel,
                            required = true,
                            missing = errors.containsKey("idCountry"),
                            error = state.fieldErrors["idCountry"],
                            enabled = editable,
                            onOpen = { onOpenPicker(CasetaPicker.Country) }
                        )
                    }
                    if (state.requiresGuide) {
                        StepHint("Por ${form.movement} se pide la cantidad de guías.")
                        CasetaTextField(
                            value = form.guideCount,
                            onValueChange = { viewModel.updateText("guideCount", it) },
                            enabled = editable,
                            label = { Text("Cantidad de guías *") },
                            singleLine = true,
                            isError = state.fieldErrors.containsKey("guideCount"),
                            supportingText = errorText(state.fieldErrors["guideCount"]),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusChain.next(CasetaStep.Caja, state, focusChain.guideCount, focusManager)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusChain.guideCount)
                        )
                    }
                    if (showPedimento) {
                        CasetaTextField(
                            value = form.pedimento,
                            onValueChange = { viewModel.updateText("pedimento", it) },
                            enabled = editable,
                            label = { Text("Pedimento (solo exportación)") },
                            placeholder = { Text("7 dígitos si aplica") },
                            singleLine = true,
                            isError = state.fieldErrors.containsKey("pedimento"),
                            supportingText = errorText(state.fieldErrors["pedimento"]),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusChain.next(CasetaStep.Caja, state, focusChain.pedimento, focusManager)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusChain.pedimento)
                        )
                    }
                }
            }
        },
        right = {
            if (!tr) {
                PickerField(
                    label = "Cliente",
                    value = form.customerLabel,
                    required = true,
                    missing = errors.containsKey("customer"),
                    error = state.fieldErrors["customer"],
                    enabled = editable,
                    onOpen = { onOpenPicker(CasetaPicker.Customer) },
                    onClear = { viewModel.clearSelection(CasetaEntradaLookupType.Customer) }
                )
                if (state.boxTypes.isNotEmpty()) {
                    PickerField(
                        label = "Tipo de caja",
                        value = form.boxTypeDescription,
                        required = true,
                        missing = errors.containsKey("idBoxType"),
                        error = state.fieldErrors["idBoxType"],
                        enabled = editable,
                        onOpen = { onOpenPicker(CasetaPicker.BoxType) }
                    )
                } else {
                    BoxTypesUnavailable(
                        selectedLabel = form.boxTypeDescription,
                        loading = state.isLoadingBoxTypes || state.isLoadingCatalogs,
                        error = state.fieldErrors["idBoxType"],
                        onRetry = viewModel::loadBoxTypes
                    )
                }
                if (state.isRefrigerated) {
                    StepConditional {
                        StepHint("Por ${form.boxTypeDescription} se piden temperatura y combustible.")
                        CasetaTextField(
                            value = form.temperature,
                            onValueChange = { viewModel.updateText("temperature", it) },
                            enabled = editable,
                            label = { Text("Temperatura *") },
                            singleLine = true,
                            isError = state.fieldErrors.containsKey("temperature"),
                            supportingText = errorText(state.fieldErrors["temperature"]),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusChain.next(CasetaStep.Caja, state, focusChain.temperature, focusManager)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusChain.temperature)
                        )
                        CasetaTextField(
                            value = form.fuel,
                            onValueChange = { viewModel.updateText("fuel", it) },
                            enabled = editable,
                            label = { Text("Combustible *") },
                            singleLine = true,
                            isError = state.fieldErrors.containsKey("fuel"),
                            supportingText = errorText(state.fieldErrors["fuel"]),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = {
                                focusChain.next(CasetaStep.Caja, state, focusChain.fuel, focusManager)
                            }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusChain.fuel)
                        )
                    }
                }
                PickerField(
                    label = "Línea de caja",
                    value = form.boxLineLabel,
                    required = true,
                    missing = errors.containsKey("boxLine"),
                    error = state.fieldErrors["boxLine"],
                    enabled = editable,
                    onOpen = { onOpenPicker(CasetaPicker.BoxLine) },
                    onClear = { viewModel.clearSelection(CasetaEntradaLookupType.BoxLine) }
                )
            } else {
                Text(
                    "Un tractocamión sin caja no lleva cliente, caja ni tipo de caja.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    )
}

/** Paso 3: fotos y observaciones (opcional). */
@Composable
private fun CasetaStepEvidencia(
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    editable: Boolean,
    wide: Boolean,
    focusChain: CasetaFormFocusChain,
    focusManager: FocusManager,
    onTakePhoto: () -> Unit,
    onPreviewPhoto: (CasetaEntradaPreviewPhoto) -> Unit,
    onDeletePhoto: (CasetaEntradaPreviewPhoto) -> Unit
) {
    StepPanes(
        wide = wide,
        left = {
            PhotosSection(
                state = state,
                viewModel = viewModel,
                onTakePhoto = onTakePhoto,
                onPreviewPhoto = onPreviewPhoto,
                onDeletePhoto = onDeletePhoto,
                canAdd = state.puedeAgregar,
                canDelete = state.puedeBorrar
            )
        },
        right = {
            ObservationsSection(
                state = state,
                viewModel = viewModel,
                focusChain = focusChain,
                focusManager = focusManager,
                enabled = editable
            )
        }
    )
}

// ───────────────────────────────────────────────────────────────────────────
// Pantallas de selección y hojas de búsqueda
//
// Van DENTRO de la ventana de la pantalla (no en un Dialog) y suben desde arriba, o se levantan sobre el teclado:
// con el teclado abierto la lista se encoge en vez de quedar tapada. En un Dialog la ventana es otra y el teclado no
// le avisa a la lista, que era justo lo que pasaba con la hoja pegada abajo.
// ───────────────────────────────────────────────────────────────────────────

@Composable
private fun CasetaOverlayHost(
    onDismiss: () -> Unit,
    top: Boolean,
    modifier: Modifier = Modifier,
    maxHeight: Dp = 760.dp,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    BackHandler(onBack = onDismiss)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .statusBarsPadding()
            .then(
                if (top) {
                    Modifier.imePadding()
                } else {
                    Modifier.windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                }
            ),
        contentAlignment = if (top) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Los toques dentro del panel no lo cierran
                ),
            color = PmmBgSurface,
            shape = if (top) {
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
            } else {
                RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            },
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
        overlay?.invoke(this)
    }
}

@Composable
private fun CasetaPickerPanel(
    kind: CasetaPicker,
    state: CasetaEntradaUiState,
    viewModel: CasetaEntradaViewModel,
    onDismiss: () -> Unit
) {
    val form = state.form
    when (kind) {
        CasetaPicker.Customer -> CatalogSearchPanel(
            title = "Cliente",
            placeholder = "Buscar cliente",
            type = CasetaEntradaLookupType.Customer,
            results = state.customerResults,
            frequentItems = state.frequentCustomers,
            isSearching = state.isSearching,
            selectedId = form.idCustomer,
            onSearch = viewModel::searchLookup,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectCustomer(it)
                onDismiss()
            }
        )
        CasetaPicker.TruckLine -> CatalogSearchPanel(
            title = "Línea de camión",
            placeholder = "Buscar línea de camión",
            type = CasetaEntradaLookupType.TruckLine,
            results = state.truckLineResults,
            frequentItems = state.frequentTruckLines,
            isSearching = state.isSearching,
            selectedId = form.idTruckLine,
            onSearch = viewModel::searchLookup,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectTruckLine(it)
                onDismiss()
            }
        )
        CasetaPicker.BoxLine -> CatalogSearchPanel(
            title = "Línea de caja",
            placeholder = "Buscar línea de caja",
            type = CasetaEntradaLookupType.BoxLine,
            results = state.boxLineResults,
            frequentItems = state.frequentBoxLines,
            isSearching = state.isSearching,
            selectedId = form.idBoxLine,
            onSearch = viewModel::searchLookup,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectBoxLine(it)
                onDismiss()
            }
        )
        CasetaPicker.BoxType -> CatalogChoicePanel(
            title = "Tipo de caja",
            items = state.boxTypes,
            selectedId = form.idBoxType,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectBoxType(it)
                onDismiss()
            }
        )
        CasetaPicker.Country -> CatalogChoicePanel(
            title = "País de origen",
            items = state.countries,
            selectedId = form.idCountry,
            onDismiss = onDismiss,
            onSelect = {
                viewModel.selectCountry(it)
                onDismiss()
            }
        )
    }
}

@Composable
private fun CasetaPanelHeader(title: String, trailing: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = PmmTextSecondary)
        }
        Text(
            text = title,
            color = PmmTextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Búsqueda en catálogo grande (clientes, líneas): campo arriba, frecuentes en una fila y los resultados debajo. */
@Composable
private fun CatalogSearchPanel(
    title: String,
    placeholder: String,
    type: CasetaEntradaLookupType,
    results: List<CasetaEntradaCatalogItem>,
    frequentItems: List<CasetaEntradaFrequentItem>,
    isSearching: Boolean,
    selectedId: String,
    onSearch: (CasetaEntradaLookupType, String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (CasetaEntradaCatalogItem) -> Unit
) {
    var query by remember(type) { mutableStateOf("") }

    LaunchedEffect(type, query) {
        delay(280)
        onSearch(type, query)
    }

    // Al desplazar la lista el teclado se esconde, para ver todo lo que hay sin cerrar la pantalla. Una lista nueva
    // por cada búsqueda: así cada búsqueda arranca arriba sin mover nada por código (eso también cuenta como scroll).
    val listState = remember(query) { LazyListState() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) {
                keyboard?.hide()
                focus.clearFocus()
            }
        }
    }

    CasetaOverlayHost(onDismiss = onDismiss, top = true) {
        CasetaPanelHeader(
            title = title,
            trailing = if (query.isNotBlank() && !isSearching) "${results.size} resultados" else null,
            onDismiss = onDismiss
        )
        CasetaTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PmmBlueDeep)
                } else if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Limpiar búsqueda", tint = PmmTextSecondary)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                onSearch(type, query)
                keyboard?.hide()
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        )
        if (frequentItems.isNotEmpty() && query.isBlank()) {
            Text(
                "Frecuentes",
                color = PmmTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(frequentItems) { frequent ->
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clickable { onSelect(frequent.catalogItem) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                frequent.nombre.take(26),
                                color = Color(0xFF1E3A8A),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text("${frequent.uso}", color = PmmTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                Text(
                    if (query.isBlank()) "Todos" else "Resultados",
                    color = PmmTextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            if (results.isEmpty() && !isSearching) {
                item { EmptyLookupState() }
            } else {
                itemsIndexed(results) { _, item ->
                    CatalogResultRow(
                        item = item,
                        query = query,
                        selected = selectedId.isNotBlank() && item.id == selectedId,
                        onClick = { onSelect(item) }
                    )
                }
            }
        }
    }
}

/** Catálogo corto (tipos de caja, países): toda la lista a la vista, sin campo de búsqueda ni teclado. */
@Composable
private fun CatalogChoicePanel(
    title: String,
    items: List<CasetaEntradaCatalogItem>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelect: (CasetaEntradaCatalogItem) -> Unit
) {
    CasetaOverlayHost(onDismiss = onDismiss, top = true) {
        CasetaPanelHeader(title = title, trailing = null, onDismiss = onDismiss)
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(items) { _, item ->
                CatalogResultRow(
                    item = item,
                    query = "",
                    selected = selectedId.isNotBlank() && item.id == selectedId,
                    onClick = { onSelect(item) }
                )
            }
        }
    }
}

@Composable
private fun CatalogResultRow(
    item: CasetaEntradaCatalogItem,
    query: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val name = item.nombre.ifBlank { item.label.ifBlank { "Sin nombre" } }
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .map { it.first() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifBlank { "CS" }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFFEEF3FF) else Color.White,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) PmmBlueDeep else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0xFFEEF3FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials, color = PmmBlueDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = highlightedName(name, query),
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.id.ifBlank { "SIN ID" },
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Elegido", tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** El nombre con lo que se escribió resaltado, para ver de un vistazo por qué salió ese resultado. */
private fun highlightedName(name: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(name)
    val start = name.indexOf(q, ignoreCase = true)
    if (start < 0) return AnnotatedString(name)
    return buildAnnotatedString {
        append(name.substring(0, start))
        withStyle(SpanStyle(background = Color(0xFFFDE68A))) {
            append(name.substring(start, start + q.length))
        }
        append(name.substring(start + q.length))
    }
}
