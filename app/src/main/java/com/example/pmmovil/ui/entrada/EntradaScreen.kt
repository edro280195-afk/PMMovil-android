package com.example.pmmovil.ui.entrada

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pmmovil.ui.common.SavingProgressState
import com.example.pmmovil.ui.common.SavingProgressStep
import com.example.pmmovil.ui.common.SavingStepStatus
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.example.pmmovil.data.model.EntradaCatalogItem
import com.example.pmmovil.ui.warehouse.WarehouseMapPickerSheet
import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaDraft
import com.example.pmmovil.data.model.EntradaFormSnapshot
import com.example.pmmovil.data.model.EntradaFrequentItem
import com.example.pmmovil.data.model.EntradaLookupItem
import com.example.pmmovil.data.model.EntradaPhotoInfo
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.printing.ZebraSendProgress
import com.example.pmmovil.di.NetworkModule
import com.example.pmmovil.ui.camera.LocalEvidenceCameraScreen
import com.example.pmmovil.ui.camera.LocalEvidencePhoto
import com.example.pmmovil.ui.components.UnsavedChangesDialog
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntradaScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    barcodeScanResult: String?,
    onBarcodeScanConsumed: () -> Unit,
    crefToLoad: String? = null,
    // true cuando en el modal del listado se eligió «Escanear QR de caseta»: la captura abre directo el lector.
    startWithScan: Boolean = false,
    // Se usa al terminar la evidencia de una entrada nueva. Default = onNavigateBack
    // para que las rutas que no lo personalizan (p. ej. entrada_editar) sigan funcionando.
    onNavigateToList: () -> Unit = onNavigateBack,
    viewModel: EntradaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val shareContext = androidx.compose.ui.platform.LocalContext.current
    var showCamera by remember { mutableStateOf(false) }
    var showDamageCamera by remember { mutableStateOf(false) }
    // Confirmaciones cuando hay algo capturado que todavía no se guarda: al volver atrás o al empezar otra entrada.
    var showExitConfirm by rememberSaveable { mutableStateOf(false) }
    var showNewEntryConfirm by rememberSaveable { mutableStateOf(false) }
    val requestExit = {
        if (viewModel.hasUnsavedChanges()) showExitConfirm = true else onNavigateBack()
    }
    val requestNewEntry = {
        if (viewModel.hasUnsavedChanges()) showNewEntryConfirm = true else viewModel.startNewEntry()
    }
    // El talón/chip de caseta es exclusivo de una captura nueva. crefToLoad != null es señal
    // explícita de "vengo a editar" — se excluye aquí y no solo vía isEditMode/form.isExisting
    // porque loadEntradaExistente() es async: sin este guard, el arranque parpadea un frame
    // antes de que la carga resuelva (bug reportado en QA al abrir una entrada existente).
    val isNewEntryFlow = crefToLoad.isNullOrBlank() && !state.form.isExisting && !state.isEditMode

    HardwareScannerListener(viewModel::processGateScan)

    // rememberSaveable (y no remember) para que, al volver del lector con el código, no se abra otra vez.
    var pendingAutoScan by rememberSaveable { mutableStateOf(startWithScan) }
    LaunchedEffect(pendingAutoScan) {
        if (pendingAutoScan) {
            pendingAutoScan = false
            onNavigateToScanner()
        }
    }

    LaunchedEffect(crefToLoad) {
        crefToLoad?.takeIf(String::isNotBlank)?.let { viewModel.loadEntradaExistente(it) }
    }

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.processGateScan(it)
            onBarcodeScanConsumed()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    // Abre el share sheet nativo de Android con el link de seguimiento --
    // así el revisor lo manda por lo que ya usa (WhatsApp, correo), no algo
    // nuevo que aprender.
    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "Seguimiento de tu entrada ${state.form.referenceDisplay()}: $url")
            }
            shareContext.startActivity(Intent.createChooser(sendIntent, "Compartir seguimiento"))
            viewModel.consumeShareUrl()
        }
    }

    // Con la entrada ya guardada, cada foto entra sola a la cola de subida (sin botón «Guardar»); la cámara muestra
    // también esas fotos para que el contador y las miniaturas sigan reflejando lo que se lleva tomado.
    val canTakePhotos = state.puedeAgregar || (state.isEditMode && state.puedeModificar)

    if (showCamera) {
        LocalEvidenceCameraScreen(
            title = "Evidencia de entrada",
            subtitle = state.form.referenceDisplay().ifBlank { "Nueva entrada" },
            photos = entradaLocalEvidencePhotos(state),
            maxPhotos = 15,
            directoryName = "entrada_photos",
            filePrefix = "entrada",
            canCapture = canTakePhotos,
            onPhotoCaptured = viewModel::addLocalPhoto,
            onDeletePhoto = { photo ->
                when {
                    photo.id.startsWith("local-") -> viewModel.deleteLocalPhoto(photo.id.removePrefix("local-"))
                    photo.id.startsWith("queued-") ->
                        state.queuedPhotos.firstOrNull { "queued-${it.id}" == photo.id }
                            ?.let(viewModel::deleteQueuedPhoto)
                }
            },
            onClose = { showCamera = false }
        )
        return
    }

    if (showDamageCamera) {
        LocalEvidenceCameraScreen(
            title = "Evidencia de daño",
            subtitle = state.form.referenceDisplay().ifBlank { "Nueva entrada" },
            photos = entradaLocalDamagePhotos(state),
            maxPhotos = 15,
            directoryName = "entrada_damage_photos",
            filePrefix = "entrada_danio",
            canCapture = canTakePhotos,
            onPhotoCaptured = viewModel::addLocalDamagePhoto,
            onDeletePhoto = { photo ->
                when {
                    photo.id.startsWith("local-") -> viewModel.deleteLocalDamagePhoto(photo.id.removePrefix("local-"))
                    photo.id.startsWith("queued-") ->
                        state.queuedDamagePhotos.firstOrNull { "queued-${it.id}" == photo.id }
                            ?.let(viewModel::deleteQueuedPhoto)
                }
            },
            onClose = { showDamageCamera = false }
        )
        return
    }

    if (state.savingProgress.isVisible) {
        EntradaSavingProgressOverlay(
            state = state.savingProgress,
            onClose = viewModel::closeSavingOverlay
        )
    }

    // Atrás (gesto, botón o flecha) no tira lo capturado: si hay cambios sin guardar, pregunta primero.
    BackHandler(onBack = requestExit)

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                when (state.workflowStage) {
                                    EntradaWorkflowStage.PRINT_LABELS -> "Entrada guardada"
                                    EntradaWorkflowStage.TAKE_PHOTOS ->
                                        if (state.isEditMode) "Entrada actualizada" else "Finalizar recepción"
                                    EntradaWorkflowStage.COMPLETE -> "Recepción lista"
                                    null -> if (state.isEditMode) "Editar Entrada" else "Nueva Entrada"
                                },
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                state.form.referenceDisplay(),
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.72f)
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = requestExit) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
                        }
                    },
                    actions = {
                        if (state.workflowStage == null && state.lastDraftSavedAt != null) {
                            Icon(Icons.Default.CloudDone, null, tint = PmmYellow)
                            Spacer(Modifier.width(8.dp))
                        }
                        // Solo tiene sentido compartir una entrada que ya existe --
                        // una nueva sin guardar todavía no tiene cref que enlazar.
                        if (state.workflowStage == null && state.isEditMode) {
                            IconButton(onClick = viewModel::compartirEntrada, enabled = !state.isSharingLink) {
                                if (state.isSharingLink) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Share, "Compartir seguimiento")
                                }
                            }
                        }
                        if (state.workflowStage == null) {
                            IconButton(onClick = requestNewEntry, enabled = state.puedeAgregar) {
                                Icon(Icons.Default.Refresh, "Nueva entrada")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PmmNavyDark,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        },
        bottomBar = {
            if (state.workflowStage == null) {
                EntradaBottomBar(
                    state = state,
                    onPrimary = viewModel::executePrimaryAction,
                    onPrint = viewModel::requestPrint
                )
            } else {
                EntradaWorkflowBottomBar(
                    state = state,
                    onPrint = viewModel::requestPrint,
                    onTakePhotos = { showCamera = true },
                    onSavePhotos = viewModel::executePrimaryAction,
                    onFinish = {
                        // Terminó la captura: se avisa al servidor (en segundo plano) y se vuelve al listado.
                        viewModel.finishCapture()
                        onNavigateToList()
                    }
                )
            }
        },
        snackbarHost = { PmmSnackbarHost(snackbar) }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // El Scaffold ya reservó la barra de abajo en `padding`. Sin esto, el imePadding del formulario
                // vuelve a sumarla encima del teclado y queda un bloque en blanco tapando parte de la pantalla.
                .consumeWindowInsets(padding)
        ) {
            if (state.workflowStage != null) {
                EntradaWorkflowContent(
                    state = state,
                    viewModel = viewModel,
                    onTakePhoto = { showCamera = true },
                    onTakeDamagePhoto = { showDamageCamera = true },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (maxWidth >= 840.dp) {
                Row(Modifier.fillMaxSize()) {
                    EntradaSidePanel(
                        state = state,
                        isNewEntryFlow = isNewEntryFlow,
                        onScanCamera = onNavigateToScanner,
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                    )
                    EntradaFormContent(
                        state = state,
                        viewModel = viewModel,
                        showScanHeader = false,
                        // El panel lateral ya lista los pasos con sus palomitas: no se repite la barra de pasos.
                        showStepBar = false,
                        isNewEntryFlow = isNewEntryFlow,
                        onScanCamera = onNavigateToScanner,
                        onTakeDamagePhoto = { showDamageCamera = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                EntradaFormContent(
                    state = state,
                    viewModel = viewModel,
                    showScanHeader = true,
                    isNewEntryFlow = isNewEntryFlow,
                    onScanCamera = onNavigateToScanner,
                    onTakeDamagePhoto = { showDamageCamera = true },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    if (state.showDraftRecovery) {
        DraftRecoveryDialog(
            drafts = state.drafts,
            onResume = viewModel::resumeDraft,
            onDelete = viewModel::deleteDraft,
            onNew = viewModel::startNewEntry,
            onDismiss = viewModel::closeDraftRecovery
        )
    }
    if (showExitConfirm) {
        UnsavedChangesDialog(
            title = "¿Seguro que quieres salir?",
            message = "Lo que llevas capturado todavía no se ha guardado. Si sales ahora, se pierde.",
            discardLabel = "Salir sin guardar",
            onDiscard = {
                showExitConfirm = false
                viewModel.discardCurrentDraft()
                onNavigateBack()
            },
            onStay = { showExitConfirm = false }
        )
    }
    if (showNewEntryConfirm) {
        UnsavedChangesDialog(
            title = "¿Empezar una entrada nueva?",
            message = "Lo que llevas capturado todavía no se ha guardado. Si empiezas otra, se pierde.",
            discardLabel = "Empezar de nuevo",
            onDiscard = {
                showNewEntryConfirm = false
                viewModel.discardCurrentDraft()
                viewModel.startNewEntry()
            },
            onStay = { showNewEntryConfirm = false }
        )
    }
    state.printerPicker?.let { picker ->
        PrinterPickerDialog(
            pickerState = picker,
            isBusy = state.isBusy,
            onDismiss = viewModel::dismissPrinterPicker,
            onPrint = viewModel::printToSelectedPrinter,
            // Recién guardada la entrada, una impresora caída no debe detener la recepción.
            onSkip = if (state.workflowStage == EntradaWorkflowStage.PRINT_LABELS) {
                viewModel::skipPrinting
            } else {
                null
            }
        )
    }
    state.galleryOpenIndex?.let { startIndex ->
        val galleryItems = entradaGalleryItems(state, viewModel)
        if (galleryItems.isNotEmpty()) {
            PhotoGalleryDialog(
                items = galleryItems,
                startIndex = startIndex.coerceIn(0, galleryItems.lastIndex),
                onClose = viewModel::closeGallery
            )
        }
    }
    state.damageGalleryOpenIndex?.let { startIndex ->
        val damageGalleryItems = entradaDamageGalleryItems(state, viewModel)
        if (damageGalleryItems.isNotEmpty()) {
            PhotoGalleryDialog(
                items = damageGalleryItems,
                startIndex = startIndex.coerceIn(0, damageGalleryItems.lastIndex),
                onClose = viewModel::closeDamageGallery
            )
        }
    }
}

@Composable
private fun PrinterPickerDialog(
    pickerState: EntradaPrinterPickerState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onPrint: (ZebraPrinterDto) -> Unit,
    onSkip: (() -> Unit)? = null
) {
    var selectedPrinter by remember(pickerState.printers) {
        mutableStateOf(pickerState.printers.first())
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Selecciona impresora", fontWeight = FontWeight.Black)
                Text(
                    pickerState.form.referenceDisplay(),
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                pickerState.catalogMessage?.takeIf(String::isNotBlank)?.let { message ->
                    Surface(
                        color = PmmAmber.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, PmmAmber.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = message,
                            color = PmmTextPrimary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pickerState.printers) { printer ->
                        PrinterOptionRow(
                            printer = printer,
                            selected = printer == selectedPrinter,
                            isLocalDefault = printer.id == pickerState.defaultPrinterId,
                            enabled = !isBusy,
                            onClick = { selectedPrinter = printer }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onPrint(selectedPrinter) },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Imprimir")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onSkip != null) {
                    TextButton(onClick = onSkip, enabled = !isBusy) {
                        Text("Continuar sin imprimir")
                    }
                }
                TextButton(onClick = onDismiss, enabled = !isBusy) {
                    Text("Cancelar")
                }
            }
        }
    )
}

/** Círculo de espera con lo que está pasando con la impresora, para que nadie crea que la app se congeló. */
@Composable
private fun BusyOverlayContent(progress: ZebraSendProgress?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        CircularProgressIndicator(color = PmmBlueDeep)
        val lines = when (progress) {
            is ZebraSendProgress.Preparing ->
                listOf(
                    "Preparando etiquetas… ${progress.done} de ${progress.total}",
                    "Se envían a la impresora en cuanto estén listas."
                )
            is ZebraSendProgress.WaitingForPrinter ->
                listOf(
                    "Esperando turno de la impresora…",
                    "Otra tableta o un lote anterior la está usando (${progress.waitedSeconds} s)."
                )
            is ZebraSendProgress.Sending ->
                listOf(
                    "Enviando etiquetas a la impresora… ${progress.percent} %",
                    "${progress.labelsSent} de ${progress.labelCount} entregadas. No cierres la pantalla."
                )
            null -> emptyList()
        }
        lines.forEachIndexed { index, line ->
            Text(
                text = line,
                color = if (index == 0) PmmTextPrimary else PmmTextSecondary,
                fontSize = if (index == 0) 14.sp else 12.sp,
                fontWeight = if (index == 0) FontWeight.Black else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PrinterOptionRow(
    printer: ZebraPrinterDto,
    selected: Boolean,
    isLocalDefault: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val subtitle = listOfNotNull(
        printer.location?.takeIf(String::isNotBlank),
        printer.terminalName?.takeIf(String::isNotBlank)
    ).joinToString(" · ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        color = if (selected) PmmBlueLight.copy(alpha = 0.34f) else PmmBgSurface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) PmmBlueDeep else PmmNavyBorder
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Print,
                contentDescription = null,
                tint = if (selected) PmmGreen else PmmBlueDeep,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        printer.displayName,
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isLocalDefault) {
                        Surface(
                            color = PmmGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                "Predeterminada",
                                color = PmmGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    printer.endpoint,
                    color = PmmTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = PmmTextSecondary,
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
private fun EntradaSidePanel(
    state: EntradaUiState,
    isNewEntryFlow: Boolean,
    onScanCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Mismo modelo que en teléfono: talón verde o chip discreto, según si ya se leyó una caseta.
    // En edición/entrada existente no aplica ninguno.
    Column(
        modifier = modifier
            .background(
                Brush.verticalGradient(listOf(PmmNavyDark, PmmBlueDeep))
            )
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isNewEntryFlow) {
            if (state.form.casetaId != null) {
                GateLoadedTicket(state = state, onRescan = onScanCamera)
            } else {
                GateScanChip(state = state, onScan = onScanCamera)
            }
        }
        ContextSummary(state.form, state)
        Text(
            "CAPTURA",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
        EntradaSection.entries.forEach { section ->
            FlowOverviewItem(
                section = section,
                complete = sectionComplete(section, state.form)
            )
        }
    }
}

/** Talón "Leído de caseta" — estado #2: entra al formulario con los datos de caseta ya cargados. */
@Composable
private fun GateLoadedTicket(state: EntradaUiState, onRescan: () -> Unit) {
    val form = state.form
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, PmmGreen),
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(PmmGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    "Leído de caseta",
                    modifier = Modifier.weight(1f),
                    color = PmmGreenDark,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
                Text(
                    "Re-escanear",
                    modifier = Modifier.clickable(enabled = !state.isBusy, onClick = onRescan),
                    color = PmmTextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GateInfoCell("CLIENTE", form.nomCliente.ifBlank { "—" }, Modifier.weight(1f))
                    GateInfoCell("CAJA", form.cajaArribo.ifBlank { "—" }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GateInfoCell("TRANSPORTE", form.nomTransporte.ifBlank { "—" }, Modifier.weight(1f))
                    GateInfoCell("PLANTA", bodegaLabel(state), Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun GateInfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = PmmTextMuted, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            color = PmmTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun bodegaLabel(state: EntradaUiState): String {
    val match = state.catalogs.bodegas.firstOrNull { it.value == state.form.bodega }
    return match?.text?.takeIf(String::isNotBlank) ?: state.form.bodega.ifBlank { "—" }
}

/** Chip "¿La caja pasó por caseta?" — estado #3: formulario limpio, con opción de escanear después. */
@Composable
private fun GateScanChip(state: EntradaUiState, onScan: () -> Unit) {
    val scanning = state.scanStatus == EntradaScanStatus.SCANNING
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PmmBgSurface)
            .dashedBorder(PmmTextMuted, 14.dp)
            .clickable(enabled = !scanning && !state.isBusy, onClick = onScan)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(Icons.Default.QrCodeScanner, null, tint = PmmTextMuted, modifier = Modifier.size(20.dp))
        Text(
            "¿La caja pasó por caseta?",
            modifier = Modifier.weight(1f),
            color = PmmTextSecondary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp
        )
        if (scanning) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = PmmAmber)
        } else {
            Text("Escanear", color = PmmBlueDeep, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ContextSummary(form: EntradaFormSnapshot, state: EntradaUiState) {
    val completed = EntradaSection.entries.count { sectionComplete(it, form) }
    Surface(
        color = Color.White.copy(alpha = 0.08f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(form.referenceDisplay(), color = PmmYellow, fontWeight = FontWeight.Black)
            SummaryLine("Cliente", form.nomCliente.ifBlank { "Pendiente" })
            SummaryLine("Bultos", formatNumber(form.totalReceived()))
            SummaryLine("Avance", "$completed de 4 secciones")
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.58f), fontSize = 11.sp)
        Text(
            value,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun FlowOverviewItem(
    section: EntradaSection,
    complete: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White.copy(alpha = if (complete) 0.14f else 0.08f),
        shape = RoundedCornerShape(13.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            complete -> PmmGreen
                            else -> Color.White.copy(alpha = 0.13f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (complete) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                } else {
                    Text(
                        "${section.ordinal + 1}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text(
                section.label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun EntradaFormContent(
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    showScanHeader: Boolean,
    isNewEntryFlow: Boolean,
    onScanCamera: () -> Unit,
    onTakeDamagePhoto: () -> Unit,
    modifier: Modifier = Modifier,
    showStepBar: Boolean = true
) {
    val formEditable = if (state.form.isExisting || state.isEditMode) {
        state.puedeModificar
    } else {
        state.puedeAgregar
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // El talón/chip de caseta solo aplica al capturar una entrada nueva (no en edición/existente).
    val showHeaderItem = showScanHeader && isNewEntryFlow
    val headerOffset = if (showHeaderItem) 1 else 0
    // La primera sección se muestra desde arriba del todo, con el talón/chip de caseta a la vista: antes se
    // saltaba directo a la sección y el chip quedaba cortado bajo la barra.
    fun listIndexOf(section: EntradaSection) = if (section.ordinal == 0) 0 else section.ordinal + headerOffset
    LaunchedEffect(state.section, showHeaderItem) {
        listState.animateScrollToItem(listIndexOf(state.section))
    }
    // La sección que se está viendo, para resaltarla en la barra de pasos.
    val currentSection by remember {
        derivedStateOf {
            if (!listState.canScrollForward) {
                EntradaSection.entries.last()
            } else {
                val index = (listState.firstVisibleItemIndex - headerOffset).coerceIn(0, EntradaSection.entries.lastIndex)
                EntradaSection.entries[index]
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showStepBar) {
            EntradaStepBar(
                form = state.form,
                current = currentSection,
                onSelect = { section ->
                    viewModel.setSection(section)
                    scope.launch { listState.animateScrollToItem(listIndexOf(section)) }
                }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showHeaderItem) {
                    item(key = "gate-status") {
                        if (state.form.casetaId != null) {
                            GateLoadedTicket(state = state, onRescan = onScanCamera)
                        } else {
                            GateScanChip(state = state, onScan = onScanCamera)
                        }
                    }
                }
                items(EntradaSection.entries, key = { it.name }) { section ->
                    FlowSection(
                        section = section,
                        complete = sectionComplete(section, state.form),
                        last = section == EntradaSection.CLOSE
                    ) {
                        when (section) {
                            EntradaSection.REFERENCE -> ReferenceSection(state, viewModel, formEditable)
                            EntradaSection.ARRIVAL -> ArrivalSection(state, viewModel, onTakeDamagePhoto, formEditable)
                            EntradaSection.MERCHANDISE -> MerchandiseSection(state, viewModel, formEditable)
                            EntradaSection.CLOSE -> CloseSection(state, viewModel, formEditable)
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
            if (state.isLoading || state.isBusy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.58f)),
                    contentAlignment = Alignment.Center
                ) {
                    BusyOverlayContent(state.printProgress)
                }
            }
        }
    }
}

/**
 * Barra de pasos fija arriba del formulario (teléfono y tableta de pie): los cuatro pasos de la captura en una sola
 * fila, con palomita verde en los completos y el actual resaltado; tocar uno salta a esa sección. Sustituye al hilo
 * azul que corría por la izquierda del formulario y le quitaba ancho al contenido.
 */
@Composable
private fun EntradaStepBar(
    form: EntradaFormSnapshot,
    current: EntradaSection,
    onSelect: (EntradaSection) -> Unit
) {
    val last = EntradaSection.entries.lastIndex
    Surface(color = PmmBgSurface, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            EntradaSection.entries.forEachIndexed { index, section ->
                val complete = sectionComplete(section, form)
                val isCurrent = section == current
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(section) }
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
                        StepBadge(number = section.ordinal + 1, complete = complete, highlighted = isCurrent, size = 28.dp)
                    }
                    Text(
                        section.label,
                        color = if (isCurrent) PmmBlueDeep else PmmTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Círculo de un paso: palomita verde si está completo, su número en azul si no (más fuerte si es el actual). */
@Composable
private fun StepBadge(number: Int, complete: Boolean, size: Dp, highlighted: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                when {
                    complete -> PmmGreen
                    highlighted -> PmmBlueDeep
                    else -> PmmBlueDeep.copy(alpha = 0.55f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (complete) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.58f))
        } else {
            Text("$number", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
private fun FlowSection(
    section: EntradaSection,
    complete: Boolean,
    last: Boolean,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (last) 0.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // El número (o la palomita) va en línea con el título: ya no hay un riel a la izquierda que le reste ancho
        // al contenido. La barra de pasos de arriba (o el panel lateral) da la vista de conjunto.
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepBadge(number = section.ordinal + 1, complete = complete, size = 32.dp, highlighted = true)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    section.label,
                    color = PmmTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    sectionSubtitle(section),
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
        content()
    }
}

/** «01-C-26-····-00-00»: los seis tramos de la referencia, con puntos donde falta el consecutivo. */
private fun referencePreview(form: EntradaFormSnapshot): String =
    listOf(form.ref1, form.ref2, form.ref3, form.ref4.ifBlank { "····" }, form.ref5, form.ref6).joinToString("-")

private fun sectionSubtitle(section: EntradaSection): String = when (section) {
    EntradaSection.REFERENCE -> "Genera la referencia de la nueva entrada."
    EntradaSection.ARRIVAL -> "Identifica cliente, transporte y condiciones de arribo."
    EntradaSection.MERCHANDISE -> "Captura cada concepto sin abandonar este flujo."
    EntradaSection.CLOSE -> "Confirma pesos, madera y evidencia fotográfica."
}

@Composable
private fun ReferenceSection(state: EntradaUiState, viewModel: EntradaViewModel, enabled: Boolean) {
    val canGenerate =
        enabled && !state.isBusy &&
            !state.consecutiveGenerated &&
            state.form.ref4.isBlank() &&
            !state.form.isExisting
    val guide = remember(state.form) { EntradaCaptureGuide.of(state.form) }
    val consecutiveStatus = guide.status(CaptureField.CONSECUTIVO)
    FormCard(title = "Referencia PMM", icon = Icons.Default.AutoAwesome) {
        // La referencia son seis tramos pero solo el consecutivo se teclea; los demás ya vienen puestos. Se muestran como
        // una vista previa debajo del campo, en lugar de seis cajitas que en una pantalla angosta partían la etiqueta.
        OutlinedTextField(
            value = state.form.ref4,
            enabled = enabled,
            onValueChange = { value ->
                viewModel.updateForm { it.copy(ref4 = value.filter(Char::isDigit), isExisting = false) }
            },
            colors = captureTextFieldColors(consecutiveStatus),
            label = { Text("Consecutivo") },
            supportingText = {
                Text(
                    "Referencia ${referencePreview(state.form)}",
                    color = PmmTextSecondary,
                    fontWeight = FontWeight.Bold
                )
            },
            isError = "ref4" in state.invalidFields,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .fillMaxWidth()
                .captureGlow(consecutiveStatus, cornerRadius = 4.dp)
        )
        Button(
            onClick = viewModel::generateConsecutive,
            enabled = canGenerate,
            colors = ButtonDefaults.buttonColors(
                containerColor = PmmYellow,
                contentColor = PmmNavyDark
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.AutoAwesome, null)
            Spacer(Modifier.width(7.dp))
            Text(
                if (canGenerate) "Generar entrada" else "Entrada cargada",
                fontWeight = FontWeight.Black
            )
        }
    }
    FormCard(title = "Contexto de captura", icon = Icons.Default.Inventory2) {
        EntradaCatalogPickerField(
            label = "Bodega / planta",
            value = state.form.bodega,
            items = state.catalogs.bodegas,
            enabled = enabled,
            captureStatus = guide.status(CaptureField.BODEGA),
            onSelect = { item -> viewModel.updateForm { it.copy(bodega = item.value) } }
        )
        ArrivalDateTimeField(
            value = state.form.fecha,
            error = "fecha" in state.invalidFields,
            enabled = enabled,
            captureStatus = guide.status(CaptureField.FECHA),
            onValueChange = { value ->
                viewModel.updateForm { it.copy(fecha = value) }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrivalDateTimeField(
    value: String,
    error: Boolean,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    captureStatus: CaptureStatus = CaptureStatus.NONE
) {
    val current = remember(value) {
        runCatching { LocalDateTime.parse(value.take(16)) }
            .getOrDefault(LocalDateTime.now())
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var selectedDate by remember { mutableStateOf(current.toLocalDate()) }
    val dateState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = current.toLocalDate()
            .atStartOfDay()
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    )
    val timeState = androidx.compose.material3.rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = true
    )

    Surface(
        onClick = { showDatePicker = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .captureGlow(captureStatus),
        color = if (error) Color(0xFFFFF1F1) else captureContainerColor(captureStatus, PmmBgSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            captureBorderWidth(captureStatus),
            if (error) PmmRed else captureBorderColor(captureStatus, PmmNavyBorder)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PmmNavyVariant, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = PmmBlueDeep
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Fecha y hora de arribo",
                    color = if (error) PmmRed else PmmTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    current.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm")),
                    color = PmmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Text(
                "Cambiar",
                color = PmmBlueDeep,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    if (error) {
        Text(
            "Selecciona la fecha y hora de arribo.",
            color = PmmRed,
            fontSize = 11.sp
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = dateState.selectedDateMillis?.let {
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        } ?: current.toLocalDate()
                        showDatePicker = false
                        showTimePicker = true
                    }
                ) {
                    Text("Continuar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(
                color = PmmBgSurface,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Hora de arribo",
                        color = PmmTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    TimePicker(state = timeState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar")
                        }
                        Button(
                            onClick = {
                                val selected = LocalDateTime.of(
                                    selectedDate,
                                    LocalTime.of(timeState.hour, timeState.minute)
                                )
                                onValueChange(
                                    selected.format(
                                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                                    )
                                )
                                showTimePicker = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PmmBlueDeep,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Aplicar", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivalSection(
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    onTakeDamagePhoto: () -> Unit,
    enabled: Boolean
) {
    val guide = remember(state.form) { EntradaCaptureGuide.of(state.form) }
    FormCard(title = "Cliente y transporte", icon = Icons.Default.Search) {
        Text(
            if (state.form.casetaId.isNullOrBlank()) {
                "Captura o busca los datos de arribo."
            } else {
                "Datos recuperados de caseta. Puedes corregirlos antes de guardar."
            },
            color = PmmTextSecondary,
            fontSize = 12.sp
        )
        EntradaEntitySearchField(
            type = EntradaLookupType.CUSTOMER,
            label = "Cliente",
            placeholder = "Buscar por código o nombre",
            code = state.form.codCliente,
            name = state.form.nomCliente,
            error = "cliente" in state.invalidFields,
            state = state,
            enabled = enabled,
            frequentItems = state.frequentCustomers,
            onSearch = viewModel::searchLookup,
            onSelect = viewModel::selectLookup,
            onClear = viewModel::clearLookupSelection,
            captureStatus = guide.status(CaptureField.CLIENTE)
        )
        EntradaEntitySearchField(
            type = EntradaLookupType.TRANSPORT,
            label = "Transporte",
            placeholder = "Buscar por código o nombre",
            code = state.form.codTransporte,
            name = state.form.nomTransporte,
            state = state,
            enabled = enabled,
            frequentItems = state.frequentTransports,
            onSearch = viewModel::searchLookup,
            onSelect = viewModel::selectLookup,
            onClear = viewModel::clearLookupSelection,
            captureStatus = guide.status(CaptureField.TRANSPORTE)
        )
        EntradaEntitySearchField(
            type = EntradaLookupType.BOX,
            label = "Caja de arribo",
            placeholder = "Buscar caja disponible",
            code = state.form.cajaArribo,
            name = "",
            state = state,
            enabled = enabled,
            onSearch = viewModel::searchLookup,
            onSelect = viewModel::selectLookup,
            onClear = viewModel::clearLookupSelection,
            captureStatus = guide.status(CaptureField.CAJA)
        )
        EntradaCatalogPickerField(
            label = "Tipo de manejo",
            value = state.form.manejo,
            items = state.catalogs.manejos,
            error = "manejo" in state.invalidFields,
            enabled = enabled,
            captureStatus = guide.status(CaptureField.MANEJO),
            onSelect = { item -> viewModel.updateForm { it.copy(manejo = item.value) } }
        )
        OutlinedTextField(
            value = state.form.guia,
            enabled = enabled,
            onValueChange = { value -> viewModel.updateForm { it.copy(guia = value) } },
            colors = captureTextFieldColors(guide.status(CaptureField.GUIA)),
            label = { Text("Guía / B.L.") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .captureGlow(guide.status(CaptureField.GUIA), cornerRadius = 4.dp)
        )
    }
    FormCard(title = "Condiciones especiales", icon = Icons.Default.Warning) {
        ToggleRow("Material HAZMAT", state.form.hazmat, enabled = enabled) {
            viewModel.updateForm { form ->
                form.copy(
                    hazmat = it,
                    claseHazmat = if (it) form.claseHazmat else "",
                    un = if (it) form.un else ""
                )
            }
        }
        if (state.form.hazmat) {
            EntradaCatalogPickerField(
                label = "Clase HAZMAT",
                value = state.form.claseHazmat,
                items = state.catalogs.clasesHazmat,
                error = "claseHazmat" in state.invalidFields,
                enabled = enabled,
                captureStatus = guide.status(CaptureField.HAZMAT_CLASE),
                onSelect = { item -> viewModel.updateForm { it.copy(claseHazmat = item.value) } }
            )
            OutlinedTextField(
                value = state.form.un,
                enabled = enabled,
                onValueChange = { value -> viewModel.updateForm { it.copy(un = value) } },
                colors = captureTextFieldColors(guide.status(CaptureField.HAZMAT_UN)),
                label = { Text("Número UN") },
                isError = "un" in state.invalidFields,
                modifier = Modifier
                    .fillMaxWidth()
                    .captureGlow(guide.status(CaptureField.HAZMAT_UN), cornerRadius = 4.dp)
            )
        }
        ToggleRow("Mercancía dañada", state.form.danada, enabled = enabled) { checked ->
            viewModel.updateForm { form ->
                form.copy(danada = checked, danadaDescripcion = if (checked) form.danadaDescripcion else "")
            }
        }
        if (state.form.danada) {
            OutlinedTextField(
                value = state.form.danadaDescripcion,
                onValueChange = { value -> viewModel.updateForm { it.copy(danadaDescripcion = value) } },
                colors = captureTextFieldColors(guide.status(CaptureField.DANO_DESCRIPCION)),
                label = { Text("Describe el daño") },
                placeholder = { Text("Ej. caja aplastada en una esquina, mercancía mojada...") },
                isError = "danadaDescripcion" in state.invalidFields,
                minLines = 2,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .captureGlow(guide.status(CaptureField.DANO_DESCRIPCION), cornerRadius = 4.dp)
            )
            DamagePhotoSection(state, viewModel, onTakeDamagePhoto, enabled)
            if ("danadaFotos" in state.invalidFields) {
                Text(
                    "Agrega al menos una foto del daño.",
                    color = PmmRed,
                    fontSize = 11.sp
                )
            }
        }
        ToggleRow("Operación IN-BOND", state.form.inBond, enabled = enabled) {
            viewModel.updateForm { form ->
                form.copy(inBond = it, inBondVal = if (it) form.inBondVal else "")
            }
        }
        if (state.form.inBond) {
            OutlinedTextField(
                value = state.form.inBondVal,
                enabled = enabled,
                onValueChange = { value -> viewModel.updateForm { it.copy(inBondVal = value) } },
                colors = captureTextFieldColors(guide.status(CaptureField.INBOND_VALOR)),
                label = { Text("Número IN-BOND") },
                isError = "inBondVal" in state.invalidFields,
                modifier = Modifier
                    .fillMaxWidth()
                    .captureGlow(guide.status(CaptureField.INBOND_VALOR), cornerRadius = 4.dp)
            )
        }
    }
}

/** Fotos exclusivas del daño — separadas de la evidencia general (Cierre), con su propia captura. */
@Composable
private fun DamagePhotoSection(
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    onTakePhoto: () -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        // Solo se cuentan los grupos que tienen algo: con la entrada guardada las fotos entran solas a la subida, así
        // que «0 por guardar» sería ruido.
        val summaryParts = listOfNotNull(
            state.form.localDamagePhotoPaths.size.takeIf { it > 0 }?.let { "$it por guardar" },
            state.queuedDamagePhotos.size.takeIf { it > 0 }?.let { "$it subiéndose" },
            state.remoteDamagePhotos.size.takeIf { it > 0 }?.let { "$it guardadas" }
        )
        Text(
            if (summaryParts.isEmpty()) "Aún sin fotos del daño" else summaryParts.joinToString(" · "),
            fontSize = 12.sp,
            color = PmmTextSecondary
        )
        OutlinedButton(
            onClick = onTakePhoto,
            enabled = enabled,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, PmmRed),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(Icons.Default.CameraAlt, null, tint = PmmRed, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Foto del daño", fontWeight = FontWeight.Black, color = PmmRed)
        }
        DamagePhotoStrip(state, viewModel)
    }
}

@Composable
private fun DamagePhotoStrip(state: EntradaUiState, viewModel: EntradaViewModel) {
    val items = entradaDamageGalleryItems(state, viewModel)
    if (items.isEmpty()) {
        EmptyState("Sin fotos del daño", "Toma al menos una foto antes de guardar.")
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
            PhotoTile(item = item, onOpen = { viewModel.openDamageGallery(index) })
        }
    }
}

private fun entradaLocalDamagePhotos(state: EntradaUiState): List<LocalEvidencePhoto> {
    val local = state.form.localDamagePhotoPaths.map { path ->
        LocalEvidencePhoto(
            id = "local-$path",
            model = File(path),
            label = "Nueva",
            status = "Nueva",
            canDelete = state.puedeBorrar
        )
    }
    // Las que ya entraron a la cola de subida (la entrada ya estaba guardada) siguen a la vista en la cámara.
    val queued = state.queuedDamagePhotos.map { photo ->
        LocalEvidencePhoto(
            id = "queued-${photo.id}",
            model = File(photo.localFilePath),
            label = "Subiendo",
            status = "Subiendo",
            canDelete = state.puedeBorrar
        )
    }
    return local + queued
}

/** Construye la lista unificada de fotos de daño (locales + en cola + guardadas) en orden visual. */
private fun entradaDamageGalleryItems(state: EntradaUiState, viewModel: EntradaViewModel): List<EntradaGalleryItem> {
    val reference = state.form.reference()
    return buildList {
        state.form.localDamagePhotoPaths.forEach { path ->
            add(EntradaGalleryItem(
                key = "local-danio-$path",
                model = File(path),
                label = "Por guardar",
                pending = true,
                onDelete = if (state.puedeBorrar) ({ viewModel.deleteLocalDamagePhoto(path) }) else null
            ))
        }
        state.queuedDamagePhotos.forEach { photo ->
            add(EntradaGalleryItem(
                key = "queued-danio-${photo.id}",
                model = File(photo.localFilePath),
                label = "Subiendo",
                pending = true,
                onDelete = if (state.puedeBorrar) ({ viewModel.deleteQueuedPhoto(photo) }) else null
            ))
        }
        state.remoteDamagePhotos.forEach { photo ->
            add(
                EntradaGalleryItem(
                    key = "remote-danio-${photo.idFotos}",
                    model = "${PMM_API_BASE}Foto/entrada-danio/ver/$reference/${photo.nLineNo}",
                    label = "Daño ${photo.nLineNo}",
                    pending = false,
                    onDelete = if (state.puedeBorrar) ({ viewModel.deleteRemoteDamagePhoto(photo) }) else null
                )
            )
        }
    }
}

@Composable
private fun MerchandiseSection(state: EntradaUiState, viewModel: EntradaViewModel, enabled: Boolean) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var quantity by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var detailClass by rememberSaveable { mutableStateOf("") }
    var bundles by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var showMap by rememberSaveable { mutableStateOf(false) }

    fun clearEditor() {
        editingIndex = null
        quantity = ""
        description = ""
        detailClass = ""
        bundles = ""
        location = ""
    }

    // Guía de captura: mientras no haya ningún concepto, cantidad y descripción son lo que sigue (en ese orden) y el
    // botón «Agregar» destella cuando ambas ya están. Con conceptos ya agregados, el editor queda neutro: un concepto
    // nuevo es opcional y no tiene por qué llamar la atención.
    val guide = remember(state.form) { EntradaCaptureGuide.of(state.form) }
    val quantityOk = (quantity.replace(",", "").toDoubleOrNull() ?: 0.0) > 0.0
    val descriptionOk = description.isNotBlank()
    val needsFirstConcept = state.form.detalles.isEmpty()
    val merchandiseIsNext = guide.next == CaptureField.MERCANCIA
    val quantityStatus = when {
        quantityOk -> CaptureStatus.CAPTURED
        needsFirstConcept && merchandiseIsNext -> CaptureStatus.NEXT
        needsFirstConcept -> CaptureStatus.PENDING
        else -> CaptureStatus.NONE
    }
    val descriptionStatus = when {
        descriptionOk -> CaptureStatus.CAPTURED
        needsFirstConcept && merchandiseIsNext && quantityOk -> CaptureStatus.NEXT
        needsFirstConcept -> CaptureStatus.PENDING
        else -> CaptureStatus.NONE
    }
    val addButtonStatus =
        if (merchandiseIsNext && quantityOk && descriptionOk) CaptureStatus.NEXT else CaptureStatus.NONE

    FormCard(title = "Mercancía recibida", icon = Icons.Default.Inventory2) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "${state.form.detalles.size} conceptos",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    "${formatNumber(state.form.totalReceived())} bultos",
                    color = PmmBlueDeep,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Surface(
                color = if (editingIndex == null) Color(0xFFEEF3FF) else PmmAmber.copy(alpha = 0.13f),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    if (editingIndex == null) "NUEVO CONCEPTO" else "EDITANDO #${editingIndex!! + 1}",
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = if (editingIndex == null) PmmBlueDeep else PmmAmber,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = quantity,
                enabled = enabled,
                onValueChange = { quantity = it },
                colors = captureTextFieldColors(quantityStatus),
                label = { Text("Cantidad") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .weight(0.8f)
                    .captureGlow(quantityStatus, cornerRadius = 4.dp)
            )
            Box(Modifier.weight(1.2f)) {
                EntradaCatalogPickerField(
                    label = "Clase",
                    value = detailClass,
                    items = state.catalogs.clases,
                    enabled = enabled,
                    captureStatus = if (detailClass.isNotBlank()) CaptureStatus.CAPTURED else CaptureStatus.NONE,
                    onSelect = { detailClass = it.value }
                )
            }
        }
        OutlinedTextField(
            value = description,
            enabled = enabled,
            onValueChange = { description = it },
            colors = captureTextFieldColors(descriptionStatus),
            label = { Text("Descripción de mercancía") },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .captureGlow(descriptionStatus, cornerRadius = 4.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = bundles,
                enabled = enabled,
                onValueChange = { bundles = it },
                colors = captureTextFieldColors(
                    if (bundles.isNotBlank()) CaptureStatus.CAPTURED else CaptureStatus.NONE
                ),
                label = { Text("Atados / observación") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.weight(1f)) {
                EntradaCatalogPickerField(
                    label = "Ubicación",
                    value = location,
                    items = state.catalogs.ubicaciones,
                    enabled = enabled,
                    captureStatus = if (location.isNotBlank()) CaptureStatus.CAPTURED else CaptureStatus.NONE,
                    onSelect = { location = it.value }
                )
            }
        }
        OutlinedButton(
            onClick = { showMap = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Map, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Ver mapa de bodega", fontWeight = FontWeight.Black)
        }
        if (showMap) {
            WarehouseMapPickerSheet(
                currentLocation = location,
                onDismiss = { showMap = false },
                onSelect = { selected ->
                    location = selected
                    showMap = false
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    val detail = EntradaDetail(
                        cantidad = quantity.replace(",", "").toDoubleOrNull() ?: 0.0,
                        clase = detailClass,
                        descripcion = description.trim(),
                        atados = bundles.trim(),
                        ubicacion = location
                    )
                    if (viewModel.addDetail(detail, editingIndex)) clearEditor()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmBlueDeep,
                    contentColor = Color.White
                ),
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .captureGlow(addButtonStatus, cornerRadius = 20.dp)
            ) {
                Icon(if (editingIndex == null) Icons.Default.Add else Icons.Default.Check, null)
                Spacer(Modifier.width(6.dp))
                Text(
                    if (editingIndex == null) "Agregar mercancía" else "Actualizar mercancía",
                    fontWeight = FontWeight.Black
                )
            }
            if (editingIndex != null) {
                OutlinedButton(onClick = ::clearEditor, enabled = enabled) {
                    Icon(Icons.Default.Close, null)
                    Spacer(Modifier.width(5.dp))
                    Text("Cancelar")
                }
            }
        }
        if ("detalles" in state.invalidFields) {
            Text(
                "Agrega al menos un artículo de mercancía.",
                color = PmmRed,
                fontSize = 12.sp
            )
        }
        if (state.form.detalles.isEmpty()) {
            EmptyState(
                title = "Sin mercancía capturada",
                subtitle = "Agrega el primer concepto para continuar."
            )
        } else {
            state.form.detalles.forEachIndexed { index, detail ->
                DetailCard(
                    detail = detail,
                    onEdit = {
                        editingIndex = index
                        quantity = formatNumber(detail.cantidad)
                        description = detail.descripcion
                        detailClass = detail.clase
                        bundles = detail.atados
                        location = detail.ubicacion
                    },
                    onDelete = {
                        viewModel.deleteDetail(index)
                        if (editingIndex == index) clearEditor()
                    },
                    canEdit = enabled,
                    canDelete = state.puedeBorrar
                )
            }
        }
    }
    FormCard(title = "Peso total", icon = Icons.Default.Inventory2) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeightField(
                label = "Libras",
                value = state.form.pesoLbs,
                onValue = viewModel::updatePounds,
                enabled = enabled,
                captureStatus = guide.status(CaptureField.PESO),
                modifier = Modifier.weight(1f)
            )
            WeightField(
                label = "Kilogramos",
                value = state.form.pesoKgs,
                onValue = viewModel::updateKilos,
                enabled = enabled,
                captureStatus = guide.status(CaptureField.PESO),
                modifier = Modifier.weight(1f)
            )
        }
        if (state.form.pesoLbs > EntradaRules.MAX_RECOMMENDED_LBS) {
            StatusBanner(
                color = PmmAmber,
                text = "El peso supera 44,500 lb. Confirma la captura antes de guardar."
            )
        }
    }
}

@Composable
private fun CloseSection(
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    enabled: Boolean
) {
    FormCard(title = "Cierre de recepción", icon = Icons.Default.CheckCircle) {
        // «Recibió» no se captura: es quien guarda la entrada. El API guarda su nombre completo (no su clave).
        ReadOnlyCapturedField(
            label = "Recibió",
            value = state.form.recibio.trim().uppercase(),
            hint = "Se registra con tu usuario"
        )
        OutlinedTextField(
            value = state.form.observaciones,
            enabled = enabled,
            onValueChange = { value -> viewModel.updateForm { it.copy(observaciones = value) } },
            colors = captureTextFieldColors(
                if (state.form.observaciones.isNotBlank()) CaptureStatus.CAPTURED else CaptureStatus.NONE
            ),
            label = { Text("Observaciones") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
    }
    FormCard(title = "Control de madera", icon = Icons.Default.Inventory2) {
        WoodCounter("Madera con sello", state.form.maderaSello, state.form.maderaSelloCant, enabled) { enabled, count ->
            viewModel.updateForm { it.copy(maderaSello = enabled, maderaSelloCant = count) }
        }
        WoodCounter("Madera sin sello", state.form.maderaSinSello, state.form.maderaSinSelloCant, enabled) { enabled, count ->
            viewModel.updateForm { it.copy(maderaSinSello = enabled, maderaSinSelloCant = count) }
        }
        WoodCounter("Aserrín", state.form.aserrin, state.form.aserrinCant, enabled) { enabled, count ->
            viewModel.updateForm { it.copy(aserrin = enabled, aserrinCant = count) }
        }
        WoodCounter("No requiere sello", state.form.noSello, state.form.noSelloCant, enabled) { enabled, count ->
            viewModel.updateForm { it.copy(noSello = enabled, noSelloCant = count) }
        }
    }
    // La evidencia fotográfica ya no se captura aquí, al fondo del formulario: se toma en el paso «Fotos» que sigue a
    // guardar (o a actualizar), como en el mockup «Registro Entrada Bodega v3». Las fotos del daño sí se toman en
    // «Condiciones especiales», porque son obligatorias antes de poder guardar.
}

@Composable
private fun PhotoStrip(state: EntradaUiState, viewModel: EntradaViewModel) {
    val items = entradaGalleryItems(state, viewModel)
    if (items.isEmpty()) {
        EmptyState("Sin fotografías", "Toma evidencia y revísala aquí antes de guardar.")
        return
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(items, key = { _, it -> it.key }) { index, item ->
            PhotoTile(item = item, onOpen = { viewModel.openGallery(index) })
        }
    }
}

/** Foto en la galería de Entrada: local (archivo) o remota/caseta (URL autenticada Coil). */
private data class EntradaGalleryItem(
    val key: String,
    val model: Any,
    val label: String,
    val pending: Boolean,
    val onDelete: (() -> Unit)?
)

// Antes apuntaba fijo a https://revisionapi.grupopmm.com/api/ (producción),
// ignorando el BASE_URL de development. Ahora usa el mismo host que el resto
// de las llamadas Retrofit (NetworkModule.BASE_URL).
private val PMM_API_BASE: String = NetworkModule.BASE_URL

private fun entradaLocalEvidencePhotos(state: EntradaUiState): List<LocalEvidencePhoto> {
    val local = state.form.localPhotoPaths.map { path ->
        LocalEvidencePhoto(
            id = "local-$path",
            model = File(path),
            label = "Nueva",
            status = "Nueva",
            canDelete = state.puedeBorrar
        )
    }
    // Las que ya entraron a la cola de subida (la entrada ya estaba guardada) siguen a la vista en la cámara.
    val queued = state.queuedPhotos.map { photo ->
        LocalEvidencePhoto(
            id = "queued-${photo.id}",
            model = File(photo.localFilePath),
            label = "Subiendo",
            status = "Subiendo",
            canDelete = state.puedeBorrar
        )
    }
    return local + queued
}

/** Construye la lista unificada de fotos (locales + guardadas + caseta) en orden visual. */
private fun entradaGalleryItems(state: EntradaUiState, viewModel: EntradaViewModel): List<EntradaGalleryItem> {
    val reference = state.form.reference()
    val casetaId = state.form.casetaId
    return buildList {
        state.form.localPhotoPaths.forEach { path ->
            add(EntradaGalleryItem(
                key = "local-$path",
                model = File(path),
                label = "Por guardar",
                pending = true,
                onDelete = if (state.puedeBorrar) ({ viewModel.deleteLocalPhoto(path) }) else null
            ))
        }
        state.queuedPhotos.forEach { photo ->
            add(EntradaGalleryItem(
                key = "queued-${photo.id}",
                model = File(photo.localFilePath),
                label = "Subiendo",
                pending = true,
                onDelete = if (state.puedeBorrar) ({ viewModel.deleteQueuedPhoto(photo) }) else null
            ))
        }
        state.remotePhotos.forEach { photo ->
            add(
                EntradaGalleryItem(
                    key = "remote-${photo.idFotos}",
                    model = "${PMM_API_BASE}Foto/entrada/ver/$reference/${photo.nLineNo}",
                    label = "Guardada ${photo.nLineNo}",
                    pending = false,
                    onDelete = if (state.puedeBorrar) ({ viewModel.deleteRemotePhoto(photo) }) else null
                )
            )
        }
        if (!casetaId.isNullOrBlank()) {
            state.gatePhotos.forEach { photo ->
                add(
                    EntradaGalleryItem(
                        key = "gate-${photo.indice}",
                        model = "${PMM_API_BASE}Foto/caseta/ver?casetaId=${Uri.encode(casetaId)}&indice=${photo.indice}",
                        label = "Caseta ${photo.indice}",
                        pending = false,
                        onDelete = null
                    )
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(item: EntradaGalleryItem, onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .size(132.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(PmmBgCard)
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = item.model,
            contentDescription = item.label,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text(
                item.label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (item.pending) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                color = PmmAmber,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    "POR GUARDAR",
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    fontSize = 8.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }
        }
        item.onDelete?.let { delete ->
            IconButton(
                onClick = delete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Delete, "Eliminar foto", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PhotoGalleryDialog(
    items: List<EntradaGalleryItem>,
    startIndex: Int,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = startIndex) { items.size }
        val scope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.97f))) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                ZoomableImage(model = items[page].model, modifier = Modifier.fillMaxSize())
            }

            // Barra superior: contador + eliminar + cerrar
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        "${pagerState.currentPage + 1} / ${items.size}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.weight(1f))
                val delete = items.getOrNull(pagerState.currentPage)?.onDelete
                if (delete != null) {
                    IconButton(
                        onClick = { delete(); onClose() },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }

            // Tira de miniaturas para saltar entre fotos sin cerrar
            if (items.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(items, key = { _, it -> "thumb-${it.key}" }) { i, item ->
                        val selected = i == pagerState.currentPage
                        AsyncImage(
                            model = item.model,
                            contentDescription = item.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    2.dp,
                                    if (selected) PmmYellow else Color.White.copy(alpha = 0.25f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                        )
                    }
                }
            }
        }
    }
}

/** Imagen con pinch-to-zoom, doble-tap para acercar y arrastre para desplazar. */
@Composable
private fun ZoomableImage(model: Any, modifier: Modifier = Modifier) {
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
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Fotografía",
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

@Composable
private fun EntradaBottomBar(
    state: EntradaUiState,
    onPrimary: () -> Unit,
    onPrint: () -> Unit
) {
    val form = state.form
    val hasLocalPhotos = form.localPhotoPaths.isNotEmpty() || form.localDamagePhotoPaths.isNotEmpty()
    val label = when {
        state.isBusy -> "Procesando..."
        state.consecutiveGenerated && form.ref4.isBlank() -> "Entrada ya generada"
        state.isEditMode -> "Actualizar"
        form.ref4.isBlank() -> "Generar entrada"
        form.isExisting && hasLocalPhotos -> "Guardar fotografías"
        form.isExisting -> "Imprimir etiquetas"
        else -> "Guardar recepción"
    }
    val showPrintButton = form.isExisting && label != "Imprimir etiquetas"
    val canExecutePrimary = !state.isBusy && !(state.consecutiveGenerated && form.ref4.isBlank()) &&
        when {
            state.isEditMode -> state.puedeModificar
            form.isExisting && hasLocalPhotos -> state.puedeAgregar
            form.isExisting -> state.puedeModificar
            else -> state.puedeAgregar
        }
    Surface(
        color = PmmBgSurface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compactPhone = maxWidth < 380.dp
            val wideLayout = maxWidth >= 600.dp
            val summary = entradaFooterSummary(state)
            if (wideLayout) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = summary,
                        color = PmmTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showPrintButton) {
                            OutlinedButton(
                                onClick = onPrint,
                                enabled = !state.isBusy,
                                modifier = Modifier.heightIn(min = 48.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("Imprimir", fontWeight = FontWeight.Black)
                            }
                        }
                        EntradaPrimaryActionButton(
                            label = label,
                            enabled = canExecutePrimary,
                            isBusy = state.isBusy,
                            onClick = onPrimary,
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = if (compactPhone) 12.dp else 16.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = summary,
                        color = PmmTextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showPrintButton && compactPhone) {
                            IconButton(
                                onClick = onPrint,
                                enabled = !state.isBusy,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "Imprimir etiquetas")
                            }
                        } else if (showPrintButton) {
                            OutlinedButton(
                                onClick = onPrint,
                                enabled = !state.isBusy,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null)
                                Spacer(Modifier.width(7.dp))
                                Text("Imprimir", fontWeight = FontWeight.Black, maxLines = 1)
                            }
                        }
                        EntradaPrimaryActionButton(
                            label = label,
                            enabled = canExecutePrimary,
                            isBusy = state.isBusy,
                            onClick = onPrimary,
                            modifier = Modifier
                                .weight(if (showPrintButton && compactPhone) 1.0f else 1.2f)
                                .heightIn(min = 48.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntradaPrimaryActionButton(
    label: String,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = PmmYellow,
            contentColor = PmmNavyDark
        ),
        shape = RoundedCornerShape(13.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PmmNavyDark
            )
        } else {
            Icon(Icons.Default.CheckCircle, contentDescription = null)
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EntradaWorkflowBottomBar(
    state: EntradaUiState,
    onPrint: () -> Unit,
    onTakePhotos: () -> Unit,
    onSavePhotos: () -> Unit,
    onFinish: () -> Unit
) {
    val stage = state.workflowStage ?: return
    val hasLocalPhotos = state.form.localPhotoPaths.isNotEmpty() || state.form.localDamagePhotoPaths.isNotEmpty()
    val photos = entradaEvidencePhotoCount(state)
    // Las fotos se guardan solas al tomarlas: sin fotos el pie ofrece «Tomar fotos»; con al menos una, «Finalizar y
    // volver». «Guardar fotografías» queda solo como red de seguridad para fotos que siguieran locales.
    val action = EntradaPhotoPolicy.primaryAction(stage, hasLocalPhotos, photos)
    val label = when (action) {
        EntradaPhotoPolicy.WorkflowAction.PRINT_LABELS -> "Imprimir etiquetas"
        EntradaPhotoPolicy.WorkflowAction.TAKE_PHOTOS -> "Tomar fotos"
        EntradaPhotoPolicy.WorkflowAction.SAVE_LOCAL_PHOTOS -> "Guardar fotografías"
        EntradaPhotoPolicy.WorkflowAction.FINISH -> "Finalizar y volver"
    }
    val enabled = !state.isBusy && when (action) {
        EntradaPhotoPolicy.WorkflowAction.PRINT_LABELS -> state.puedeAgregar || state.puedeModificar
        // Al editar, quien tiene permiso de Modificar también puede agregar fotos a la entrada que está corrigiendo.
        EntradaPhotoPolicy.WorkflowAction.TAKE_PHOTOS,
        EntradaPhotoPolicy.WorkflowAction.SAVE_LOCAL_PHOTOS ->
            state.puedeAgregar || (state.isEditMode && state.puedeModificar)
        EntradaPhotoPolicy.WorkflowAction.FINISH -> true
    }
    val onPrimary = when (action) {
        EntradaPhotoPolicy.WorkflowAction.PRINT_LABELS -> onPrint
        EntradaPhotoPolicy.WorkflowAction.TAKE_PHOTOS -> onTakePhotos
        EntradaPhotoPolicy.WorkflowAction.SAVE_LOCAL_PHOTOS -> onSavePhotos
        EntradaPhotoPolicy.WorkflowAction.FINISH -> onFinish
    }
    val icon = when (action) {
        EntradaPhotoPolicy.WorkflowAction.PRINT_LABELS -> Icons.Default.Print
        EntradaPhotoPolicy.WorkflowAction.TAKE_PHOTOS -> Icons.Default.CameraAlt
        EntradaPhotoPolicy.WorkflowAction.SAVE_LOCAL_PHOTOS -> Icons.Default.Check
        EntradaPhotoPolicy.WorkflowAction.FINISH -> Icons.Default.CheckCircle
    }
    val summary = when (stage) {
        EntradaWorkflowStage.PRINT_LABELS -> "${entradaConceptCount(state.form.detalles.size)} · ${workflowPendingPhotoSummary(photos)}"
        EntradaWorkflowStage.TAKE_PHOTOS -> "${entradaConceptCount(state.form.detalles.size)} · ${entradaPhotoCount(photos)} capturadas"
        EntradaWorkflowStage.COMPLETE -> "${entradaConceptCount(state.form.detalles.size)} · evidencia guardada"
    }

    Surface(
        color = PmmBgSurface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        summary,
                        modifier = Modifier.weight(1f),
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    EntradaWorkflowButton(label, icon, enabled, state.isBusy, onPrimary, Modifier.widthIn(min = 220.dp))
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        summary,
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    EntradaWorkflowButton(
                        label = label,
                        icon = icon,
                        enabled = enabled,
                        isBusy = state.isBusy,
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun EntradaWorkflowButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PmmYellow,
            contentColor = PmmNavyDark
        ),
        shape = RoundedCornerShape(13.dp)
    ) {
        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = PmmNavyDark
            )
        } else {
            Icon(icon, contentDescription = null)
        }
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EntradaWorkflowContent(
    state: EntradaUiState,
    viewModel: EntradaViewModel,
    onTakePhoto: () -> Unit,
    onTakeDamagePhoto: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stage = state.workflowStage ?: return
    val photoCount = entradaEvidencePhotoCount(state)
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EntradaWorkflowProgress(stage, isEdit = state.isEditMode)
            EntradaWorkflowStatus(stage, isEdit = state.isEditMode)
            val cardTitle = when (stage) {
                EntradaWorkflowStage.PRINT_LABELS -> "Imprime las etiquetas"
                EntradaWorkflowStage.TAKE_PHOTOS -> "Toma las fotos"
                EntradaWorkflowStage.COMPLETE -> "Recepción lista"
            }
            val cardIcon = when (stage) {
                EntradaWorkflowStage.PRINT_LABELS -> Icons.Default.Print
                EntradaWorkflowStage.TAKE_PHOTOS -> Icons.Default.CameraAlt
                EntradaWorkflowStage.COMPLETE -> Icons.Default.CheckCircle
            }
            FormCard(title = cardTitle, icon = cardIcon) {
                val detail = when (stage) {
                    EntradaWorkflowStage.PRINT_LABELS ->
                        "Coloca una etiqueta en cada bulto. Al terminar, vuelve aquí para fotografiar la mercancía."
                    EntradaWorkflowStage.TAKE_PHOTOS -> if (state.isEditMode) {
                        "Los datos ya quedaron actualizados. Si hace falta, agrega fotos de la mercancía a esta entrada."
                    } else {
                        "Pega primero las etiquetas en los bultos. Después fotografía la mercancía para dejar evidencia."
                    }
                    EntradaWorkflowStage.COMPLETE ->
                        "La recepción quedó registrada y las fotografías están listas para sincronizar."
                }
                Text(detail, color = PmmTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = PmmNavyBorder)
                EntradaWorkflowSummaryLine("Referencia", state.form.referenceDisplay())
                EntradaWorkflowSummaryLine("Cliente", state.form.nomCliente.ifBlank { "Sin cliente" })
                EntradaWorkflowSummaryLine("Mercancía", entradaConceptCount(state.form.detalles.size))
                when (stage) {
                    EntradaWorkflowStage.PRINT_LABELS ->
                        EntradaWorkflowSummaryLine("Evidencia", workflowPendingPhotoSummary(photoCount))
                    EntradaWorkflowStage.TAKE_PHOTOS ->
                        EntradaWorkflowSummaryLine("Evidencia", "${entradaPhotoCount(photoCount)} capturadas")
                    EntradaWorkflowStage.COMPLETE ->
                        EntradaWorkflowSummaryLine("Evidencia", "Guardada en la cola de sincronización")
                }
                if (stage == EntradaWorkflowStage.PRINT_LABELS) {
                    // Si la impresora falla o no está, la recepción sigue: las etiquetas se sacan después.
                    OutlinedButton(
                        onClick = viewModel::skipPrinting,
                        enabled = !state.isBusy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(13.dp)
                    ) {
                        Text("Continuar sin imprimir", fontWeight = FontWeight.Black)
                    }
                }
                if (stage != EntradaWorkflowStage.PRINT_LABELS) {
                    PhotoStrip(state, viewModel)
                    // Con al menos una foto el pie pasa a «Finalizar y volver»; para seguir tomando está este botón.
                    if (stage == EntradaWorkflowStage.TAKE_PHOTOS && photoCount > 0) {
                        OutlinedButton(
                            onClick = onTakePhoto,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(13.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Agregar más fotos", fontWeight = FontWeight.Black)
                        }
                    }
                    EntradaPhotoSyncNote(state)
                }
            }
            // Mercancía dañada: sus fotos van aparte de la evidencia, en su propio grupo, para identificarlas (también
            // así aparecen en el enlace de seguimiento).
            if (stage != EntradaWorkflowStage.PRINT_LABELS && state.form.danada) {
                FormCard(title = "Mercancía dañada", icon = Icons.Default.Warning) {
                    if (state.form.danadaDescripcion.isNotBlank()) {
                        Text(
                            state.form.danadaDescripcion,
                            color = PmmTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    DamagePhotoSection(
                        state = state,
                        viewModel = viewModel,
                        onTakePhoto = onTakeDamagePhoto,
                        enabled = state.puedeAgregar || (state.isEditMode && state.puedeModificar)
                    )
                }
            }
        }
        if (state.isBusy) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center
            ) {
                BusyOverlayContent(state.printProgress)
            }
        }
    }
}

/**
 * Aviso pasivo (no pide nada): las fotos ya tomadas se están subiendo solas. Solo aparece mientras haya alguna en la cola.
 */
@Composable
private fun EntradaPhotoSyncNote(state: EntradaUiState) {
    val uploading = state.queuedPhotos.size + state.queuedDamagePhotos.size
    if (uploading == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(18.dp))
        Text(
            "${entradaPhotoCount(uploading)} subiéndose. Se guardan solas; puedes salir.",
            color = PmmBlueDeep,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EntradaWorkflowProgress(stage: EntradaWorkflowStage, isEdit: Boolean = false) {
    // Al editar no hay paso de etiquetas (se reimprimen con «Imprimir» del formulario): solo Datos → Fotos.
    val labels = if (isEdit) listOf("Datos", "Fotos") else listOf("Datos", "Etiquetas", "Fotos")
    val activeIndex = when (stage) {
        EntradaWorkflowStage.PRINT_LABELS -> 1
        EntradaWorkflowStage.TAKE_PHOTOS -> labels.lastIndex
        EntradaWorkflowStage.COMPLETE -> labels.lastIndex
    }
    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            labels.forEachIndexed { index, label ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StepBadge(
                        number = index + 1,
                        complete = index < activeIndex || stage == EntradaWorkflowStage.COMPLETE,
                        highlighted = index == activeIndex && stage != EntradaWorkflowStage.COMPLETE,
                        size = 28.dp
                    )
                    Text(
                        label,
                        color = when {
                            index < activeIndex || stage == EntradaWorkflowStage.COMPLETE -> PmmGreenDark
                            index == activeIndex -> PmmBlueDeep
                            else -> PmmTextSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = if (index == activeIndex) FontWeight.Black else FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun EntradaWorkflowStatus(stage: EntradaWorkflowStage, isEdit: Boolean = false) {
    val message = when (stage) {
        EntradaWorkflowStage.PRINT_LABELS -> "Datos guardados correctamente"
        EntradaWorkflowStage.TAKE_PHOTOS ->
            if (isEdit) "Datos actualizados correctamente" else "Etiquetas enviadas a imprimir"
        EntradaWorkflowStage.COMPLETE -> "Fotos guardadas y listas para sincronizar"
    }
    Surface(
        color = PmmGreen.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PmmGreen.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PmmGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
            }
            Text(message, color = PmmGreenDark, fontSize = 12.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun EntradaWorkflowSummaryLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = PmmTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun entradaEvidencePhotoCount(state: EntradaUiState): Int =
    state.form.localPhotoPaths.size + state.queuedPhotos.size + state.remotePhotos.size

private fun entradaConceptCount(count: Int): String =
    "$count ${if (count == 1) "concepto" else "conceptos"}"

private fun entradaPhotoCount(count: Int): String =
    "$count ${if (count == 1) "foto" else "fotos"}"

private fun workflowPendingPhotoSummary(count: Int): String =
    if (count == 0) "0 fotos por tomar" else "${entradaPhotoCount(count)} registradas"

private fun entradaFooterSummary(state: EntradaUiState): String {
    val concepts = entradaConceptCount(state.form.detalles.size)
    // Una entrada nueva todavía no tiene fotos de evidencia: se toman después de guardar. Decir «0 fotos» ahí
    // confunde, así que solo se cuentan las del daño (que sí se toman antes) cuando se declaró mercancía dañada.
    if (!state.isEditMode && !state.form.isExisting) {
        return if (state.form.danada) {
            "$concepts · ${entradaPhotoCount(state.form.localDamagePhotoPaths.size)} de daño"
        } else {
            concepts
        }
    }
    return "$concepts · ${entradaPhotoCount(entradaEvidencePhotoCount(state))}"
}

@Composable
private fun FormCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFEEF3FF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
            }
            HorizontalDivider(color = PmmNavyBorder)
            content()
        }
    }
}

// ─── Selectores grandes sin dropdown (portados de CasetaEntradaScreen) ──────────────
// Un solo patrón de interacción en toda la pantalla: se toca una tarjeta grande
// (SelectionDisplayCard) y se abre una hoja inferior con opciones grandes y tocables.
// Para catálogos con búsqueda en servidor (Cliente/Transporte/Caja) la hoja muestra
// "Frecuentes" + resultados; para catálogos chicos y estáticos (Bodega, Manejo, etc.)
// la hoja muestra una grilla de tarjetas de selección.

@Composable
private fun EntradaEntitySearchField(
    type: EntradaLookupType,
    label: String,
    placeholder: String,
    code: String,
    name: String,
    error: Boolean = false,
    state: EntradaUiState,
    enabled: Boolean = true,
    frequentItems: List<EntradaFrequentItem> = emptyList(),
    onSearch: (EntradaLookupType, String) -> Unit,
    onSelect: (EntradaLookupItem) -> Unit,
    onClear: (EntradaLookupType) -> Unit,
    captureStatus: CaptureStatus = CaptureStatus.NONE
) {
    var openSearch by remember(type) { mutableStateOf(false) }
    val selectedLabel = listOf(code, name).filter(String::isNotBlank).joinToString(" · ")

    SelectionDisplayCard(
        label = label,
        value = selectedLabel,
        placeholder = placeholder,
        error = if (error) "Selecciona un resultado válido." else null,
        enabled = enabled,
        onClick = {
            openSearch = true
            onSearch(type, "")
        },
        onClear = if (enabled && selectedLabel.isNotBlank()) ({ onClear(type) }) else null,
        status = captureStatus
    )

    if (openSearch) {
        EntradaCatalogSearchDialog(
            title = label,
            placeholder = placeholder,
            type = type,
            results = if (state.lookupType == type) state.lookupItems else emptyList(),
            frequentItems = frequentItems,
            isSearching = state.isLookupLoading && state.lookupType == type,
            onSearch = onSearch,
            onDismiss = { openSearch = false },
            onSelect = {
                onSelect(it)
                openSearch = false
            }
        )
    }
}

@Composable
private fun SelectionDisplayCard(
    label: String,
    value: String,
    placeholder: String,
    error: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
    status: CaptureStatus = CaptureStatus.NONE
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .captureGlow(status)
            .clickable(enabled = enabled, onClick = onClick),
        color = captureContainerColor(status, PmmBgSurface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            captureBorderWidth(status),
            if (error != null) PmmRed else captureBorderColor(status, PmmNavyBorder)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = if (status == CaptureStatus.CAPTURED) CaptureGreenLine else PmmTextMuted)
            Column(modifier = Modifier.weight(1f)) {
                CaptureLabel(
                    label = label,
                    status = status,
                    baseColor = PmmTextSecondary,
                    errorColor = if (error != null) PmmRed else null
                )
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
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Limpiar $label", tint = PmmTextSecondary, modifier = Modifier.size(18.dp))
                }
            } else {
                Icon(Icons.Default.ExpandMore, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(24.dp))
            }
        }
    }
    if (error != null) {
        Text(error, color = PmmRed, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp, start = 4.dp))
    }
}

@Composable
private fun EntradaCatalogSearchDialog(
    title: String,
    placeholder: String,
    type: EntradaLookupType,
    results: List<EntradaLookupItem>,
    frequentItems: List<EntradaFrequentItem>,
    isSearching: Boolean,
    onSearch: (EntradaLookupType, String) -> Unit,
    onDismiss: () -> Unit,
    onSelect: (EntradaLookupItem) -> Unit
) {
    var query by remember(type) { mutableStateOf("") }

    LaunchedEffect(type, query) {
        delay(280)
        onSearch(type, query)
    }

    BackHandler(onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = PmmBgSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogDragHandle()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            Text(title, color = PmmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Frecuentes y catálogo", color = PmmTextSecondary, fontSize = 12.sp)
                        }
                        DialogCloseButton(onDismiss)
                    }
                    OutlinedTextField(
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
                        keyboardActions = KeyboardActions(onSearch = { onSearch(type, query) }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    val scroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(scroll),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (frequentItems.isNotEmpty() && query.isBlank()) {
                            Text("Frecuentes", color = PmmTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            EntradaResultGrid { columns ->
                                frequentItems.chunked(columns).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { item ->
                                            EntradaCatalogResultCard(
                                                item = item.lookupItem,
                                                usage = item.uso,
                                                onClick = { onSelect(item.lookupItem) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }

                        Text("Resultados", color = PmmTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (results.isEmpty() && !isSearching) {
                            EntradaEmptyLookupState()
                        } else {
                            EntradaResultGrid { columns ->
                                results.chunked(columns).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { item ->
                                            EntradaCatalogResultCard(
                                                item = item,
                                                usage = null,
                                                onClick = { onSelect(item) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntradaResultGrid(content: @Composable (columns: Int) -> Unit) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 620.dp) 2 else 1
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content(columns) }
    }
}

@Composable
private fun EntradaCatalogResultCard(
    item: EntradaLookupItem,
    usage: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val name = item.nombre.ifBlank { "Sin nombre" }
    val initials = name.split(" ")
        .filter { it.isNotBlank() }
        .map { it.first() }
        .take(2)
        .joinToString("")
        .uppercase()
        .ifBlank { "•" }

    Surface(
        modifier = modifier
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick),
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(PmmNavyVariant, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = initials, color = PmmBlueDeep, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOf(item.id, item.extra).filter(String::isNotBlank).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            usage?.let {
                Text("$it usos", color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EntradaEmptyLookupState() {
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
            Text("Sin resultados. Prueba con otro código o nombre.", color = PmmTextSecondary)
        }
    }
}

/** Catálogo chico y estático (Bodega, Manejo, Clase, Ubicación...): tarjetas grandes, sin escribir. */
@Composable
private fun EntradaCatalogPickerField(
    label: String,
    placeholder: String = "Seleccionar",
    value: String,
    items: List<EntradaCatalogItem>,
    error: Boolean = false,
    enabled: Boolean = true,
    captureStatus: CaptureStatus = CaptureStatus.NONE,
    onSelect: (EntradaCatalogItem) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val selected = items.firstOrNull { it.value == value }
    val displayValue = selected?.let { "${it.value} · ${it.text}" } ?: value

    SelectionDisplayCard(
        label = label,
        value = displayValue,
        placeholder = placeholder,
        error = if (error) "Selecciona una opción." else null,
        enabled = enabled,
        onClick = { open = true },
        onClear = null,
        status = captureStatus
    )

    if (open) {
        EntradaCatalogPickerDialog(
            title = label,
            items = items,
            selectedValue = value,
            onDismiss = { open = false },
            onSelect = { item ->
                onSelect(item)
                open = false
            }
        )
    }
}

@Composable
private fun EntradaCatalogPickerDialog(
    title: String,
    items: List<EntradaCatalogItem>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelect: (EntradaCatalogItem) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(items, query) {
        if (query.isBlank()) items
        else items.filter { it.value.contains(query, true) || it.text.contains(query, true) }
    }

    BackHandler(onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                color = PmmBgSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    DialogDragHandle()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(title, color = PmmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        DialogCloseButton(onDismiss)
                    }
                    if (items.size > 8) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Buscar en $title") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (filtered.isEmpty()) {
                            EntradaEmptyLookupState()
                        } else {
                            EntradaResultGrid { columns ->
                                filtered.chunked(columns).forEach { rowItems ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        rowItems.forEach { item ->
                                            DecisionCard(
                                                title = item.text.ifBlank { item.value },
                                                subtitle = item.value,
                                                selected = item.value == selectedValue,
                                                onClick = { onSelect(item) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat(columns - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DecisionCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background by animateColorAsState(
        targetValue = if (selected) PmmBlueDeep else PmmBgCard,
        animationSpec = tween(durationMillis = 180),
        label = "decision-bg"
    )
    val contentColor = if (selected) Color.White else PmmTextPrimary

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        color = background,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.Inventory2,
                contentDescription = null,
                tint = if (selected) Color.White else PmmBlueDeep,
                modifier = Modifier.size(20.dp)
            )
            Column(Modifier.weight(1f)) {
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
private fun DialogDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(40.dp)
                .height(4.dp)
                .background(PmmNavyBorder, RoundedCornerShape(999.dp))
        )
    }
}

@Composable
private fun DialogCloseButton(onDismiss: () -> Unit) {
    Surface(shape = CircleShape, color = PmmBgCard, modifier = Modifier.size(36.dp)) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = PmmTextSecondary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChecked: (Boolean) -> Unit) {
    Surface(
        color = if (checked) Color(0xFFEEF3FF) else PmmBgCard,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (checked) PmmBlueLight else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onChecked(!checked) }
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
        }
    }
}

@Composable
private fun DetailCard(
    detail: EntradaDetail,
    canEdit: Boolean = true,
    canDelete: Boolean = true,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = PmmBgCard,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = PmmBlueDeep, shape = RoundedCornerShape(10.dp)) {
                Text(
                    formatNumber(detail.cantidad),
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    detail.descripcion,
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOf(detail.clase, detail.ubicacion, detail.atados)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                        .ifBlank { "Sin datos adicionales" },
                    color = PmmTextSecondary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = onEdit, enabled = canEdit) {
                Icon(Icons.Default.Edit, "Editar", tint = PmmBlueDeep)
            }
            IconButton(onClick = onDelete, enabled = canDelete) {
                Icon(Icons.Default.Delete, "Eliminar", tint = PmmRed)
            }
        }
    }
}

/** Dato que la app llena sola y no se puede editar: mismo verde de «capturado», con candado. */
@Composable
private fun ReadOnlyCapturedField(label: String, value: String, hint: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        color = CaptureGreenFill,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CaptureGreenLine)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = CaptureGreenLine)
            Column(modifier = Modifier.weight(1f)) {
                CaptureLabel(label = label, status = CaptureStatus.CAPTURED, baseColor = PmmTextSecondary)
                Text(
                    text = value.ifBlank { "—" },
                    color = PmmTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(hint, color = PmmTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun WeightField(
    label: String,
    value: Double,
    onValue: (Double) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    captureStatus: CaptureStatus = CaptureStatus.NONE
) {
    var text by remember(value) {
        mutableStateOf(if (value == 0.0) "" else formatNumber(value))
    }
    OutlinedTextField(
        value = text,
        enabled = enabled,
        onValueChange = {
            text = it
            it.replace(",", "").toDoubleOrNull()?.let(onValue)
            if (it.isBlank()) onValue(0.0)
        },
        colors = captureTextFieldColors(captureStatus),
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun WoodCounter(
    label: String,
    checked: Boolean,
    count: Int,
    enabled: Boolean = true,
    onChange: (Boolean, Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        ToggleRow(label, checked, enabled = enabled) { changed ->
            onChange(changed, if (changed) maxOf(1, count) else 0)
        }
        AnimatedVisibility(visible = checked) {
            OutlinedTextField(
                value = count.toString(),
                enabled = enabled,
                onValueChange = { value ->
                    onChange(true, value.filter(Char::isDigit).toIntOrNull() ?: 0)
                },
                label = { Text("Cantidad de $label") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StatusBanner(color: Color, text: String) {
    Surface(
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = color)
            Text(text, color = PmmTextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgCard, RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Inventory2, null, tint = PmmTextMuted, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(8.dp))
        Text(title, fontWeight = FontWeight.Bold, color = PmmTextPrimary)
        Text(subtitle, color = PmmTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun DraftRecoveryDialog(
    drafts: List<EntradaDraft>,
    onResume: (EntradaDraft) -> Unit,
    onDelete: (EntradaDraft) -> Unit,
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
                Icon(Icons.Default.CloudDone, null, tint = PmmGreen, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Continúa donde te quedaste", fontSize = 22.sp, fontWeight = FontWeight.Black)
                Text(
                    "Estas capturas se quedaron a medias. Incluyen mercancía y fotografías.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(drafts, key = { it.id }) { draft ->
                        DraftCard(draft, onResume, onDelete)
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
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Iniciar entrada nueva", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: EntradaDraft,
    onResume: (EntradaDraft) -> Unit,
    onDelete: (EntradaDraft) -> Unit
) {
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
                Text(draft.form.referenceDisplay(), color = PmmBlueDeep, fontWeight = FontWeight.Black)
                Text(
                    draft.form.nomCliente.ifBlank { draft.form.cajaArribo.ifBlank { "Captura iniciada" } },
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${draft.form.detalles.size} conceptos · ${draft.form.localPhotoPaths.size} fotos · ${
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(draft.lastEditAt))
                    }",
                    color = PmmTextSecondary,
                    fontSize = 10.sp
                )
            }
            IconButton(onClick = { onDelete(draft) }) {
                Icon(Icons.Default.Delete, "Descartar", tint = PmmRed)
            }
            Button(onClick = { onResume(draft) }) { Text("Continuar") }
        }
    }
}

@Composable
private fun PhotoPreviewDialog(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(Color.Black, RoundedCornerShape(20.dp))
        ) {
            AsyncImage(
                model = File(path),
                contentDescription = "Vista de fotografía",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
            }
        }
    }
}

private fun sectionComplete(section: EntradaSection, form: EntradaFormSnapshot): Boolean =
    when (section) {
        EntradaSection.REFERENCE -> form.ref4.isNotBlank()
        EntradaSection.ARRIVAL ->
            form.ref4.isNotBlank() &&
                EntradaRules.validate(form).none { it.section == EntradaSection.ARRIVAL }
        EntradaSection.MERCHANDISE -> form.detalles.isNotEmpty()
        EntradaSection.CLOSE -> form.isExisting
    }


private fun formatNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.2f", value)

/** Borde punteado (no disponible como BorderStroke nativo) para el chip "¿La caja pasó por caseta?". */
private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 1.dp,
    dashLength: Dp = 6.dp,
    gapLength: Dp = 4.dp
): Modifier = this.drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f)
        )
    )
}

@Composable
private fun EntradaSavingProgressOverlay(
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
                    text = "Módulo Bodega · Guardando recepción",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(18.dp))

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
                                        imageVector = Icons.Default.Close,
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
