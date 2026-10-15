package com.example.pmmovil.ui.revision

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import com.example.pmmovil.R
import com.example.pmmovil.data.diagnostics.RevisionDiagnostics
import com.example.pmmovil.data.model.FacturaRevisionDto
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.model.PaisDto
import com.example.pmmovil.data.model.PaisFrecuenteDto
import com.example.pmmovil.data.model.TipoMaterialDto
import com.example.pmmovil.data.model.UnidadComercialDto
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.ui.components.ConfettiOverlay
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.PmmStatusBar
import com.example.pmmovil.ui.components.rememberUnlockApprovedAlert
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.pmmovil.ui.pdf.InlinePdfWorkspace
import com.example.pmmovil.ui.theme.*
import com.example.pmmovil.ui.localization.asString
import java.util.Locale

private data class ReviewClosureSummary(
    val incompleteInvoices: List<String>,
    val pendingPartDescriptions: List<String>
) {
    val hasWarnings: Boolean
        get() = incompleteInvoices.isNotEmpty() || pendingPartDescriptions.isNotEmpty()
}

private fun buildReviewClosureSummary(
    invoices: List<FacturaRevisionDto>,
    parts: List<PartidaRevisionDto>,
    unknownInvoiceLabel: String,
    unknownPartLabel: String
): ReviewClosureSummary {
    val incompleteInvoices = invoices
        .filter { it.StatusRev != 1 && it.StatusRev != 2 }
        .map { it.Cnofact.trim() }
        .filter(String::isNotEmpty)

    val pendingPartDescriptions = parts
        .filterNot(PartidaRevisionDto::Revisada)
        .map { part ->
            val invoice = part.Cnofact.trim().ifEmpty { unknownInvoiceLabel }
            val number = part.CnoParte?.trim().orEmpty().ifEmpty { unknownPartLabel }
            "$invoice · $number"
        }

    return ReviewClosureSummary(incompleteInvoices, pendingPartDescriptions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleRevisionScreen(
    onNavigateBack: () -> Unit,
    onNavigateAfterForcedClose: () -> Unit = onNavigateBack,
    onNavigateToCamera: (String, String, Boolean) -> Unit,
    onNavigateToGallery: (String, String) -> Unit = { _, _ -> },
    onNavigateToScanner: () -> Unit,
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    photosChanged: Boolean = false,
    onPhotosChangedConsumed: () -> Unit = {},
    viewModel: DetalleRevisionViewModel = hiltViewModel()
) {
    val facturas      by viewModel.facturas.collectAsState()
    val partidas      by viewModel.partidasFiltradas.collectAsState()
    val todasLasPartidas by viewModel.todasLasPartidas.collectAsState()
    val isLoading     by viewModel.isLoading.collectAsState()
    val error         by viewModel.error.collectAsState()
    val toast         by viewModel.toast.collectAsState()
    val ultimaAcumulacion by viewModel.ultimaAcumulacion.collectAsState()
    val isSharingLink by viewModel.isSharingLink.collectAsState()
    val shareUrl      by viewModel.shareUrl.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val timerSeconds  by viewModel.timerSeconds.collectAsState()
    val timerActivo   by viewModel.timerActivo.collectAsState()
    val selectedIndex by viewModel.selectedFacturaIndex.collectAsState()
    val navegarAtras  by viewModel.navegarAtras.collectAsState()
    val cierreForzoso by viewModel.cierreForzoso.collectAsState()

    // --- Fase 3 State ---
    val paisesList    by viewModel.paises.collectAsState()
    val paisesFrecuentesList by viewModel.paisesFrecuentes.collectAsState()
    val tiposMaterialList by viewModel.tiposMaterial.collectAsState()
    val unidadesComercialesList by viewModel.unidadesComerciales.collectAsState()
    val isEspectador  by viewModel.isEspectador.collectAsState()
    val entradaCerrada by viewModel.entradaCerrada.collectAsState()
    val relacionCerrada by viewModel.relacionCerrada.collectAsState()
    val supervisorObservando by viewModel.supervisorObservando.collectAsState()
    val validation    by viewModel.validationState.collectAsState()
    val pendingPhotoCount by viewModel.pendingPhotoCount.collectAsState()
    val autoFinishPrompt by viewModel.autoFinishPrompt.collectAsState()
    val unlockApprovedSeq by viewModel.unlockApprovedSeq.collectAsState()
    val playUnlockApprovedAlert = rememberUnlockApprovedAlert()
    val authorizedAlertText = stringResource(R.string.notification_entry_authorized)
    val undoActionLabel = stringResource(R.string.revision_undo)
    val toastText = toast?.asString()
    val ultimaAcumulacionText = ultimaAcumulacion?.mensaje?.asString()

    LaunchedEffect(unlockApprovedSeq) {
        if (unlockApprovedSeq > 0) {
            playUnlockApprovedAlert(authorizedAlertText)
        }
    }

    val activePartidaIndex by viewModel.partidaActivaIndex.collectAsState()
    val navDirection       by viewModel.navDirection.collectAsState()
    val filterPartidas     by viewModel.filtroPartidas.collectAsState()
    val busquedaParte      by viewModel.busquedaParte.collectAsState()
    val resultadosBusqueda by viewModel.resultadosBusquedaParte.collectAsState()
    val jumpPulseSeq       by viewModel.jumpPulseSeq.collectAsState()
    val observacionGeneral by viewModel.observacionGeneral.collectAsState()

    val facturaActual = facturas.getOrNull(selectedIndex)
    val puedeAgregarRevision = viewModel.canCreate()
    val puedeModificarRevision = viewModel.canEdit()
    val puedeBorrarRevision = viewModel.canDelete()
    val revisionBloqueada = facturaActual?.let { viewModel.isBloqueada(it) } ?: true
    // Lectura permite consultar el formulario, pero no debe dejar controles de
    // escritura activos. El bloqueo de negocio sigue siendo independiente del
    // permiso para que Agregar/Borrar continúen siendo permisos distintos.
    val isFacturaLocked = revisionBloqueada || !puedeModificarRevision

    val snackbarHostState  = remember { SnackbarHostState() }
    var showTerminarDialog by remember { mutableStateOf(false) }
    var finishCountdown by remember { mutableIntStateOf(0) }
    var showDiscrepanciaModal by remember { mutableStateOf(false) }
    var isPdfOpen by rememberSaveable { mutableStateOf(false) }
    var isPdfExpanded by rememberSaveable { mutableStateOf(false) }
    var pdfPaneFraction by rememberSaveable { mutableFloatStateOf(0.48f) }

    val reviewClosureSummary = remember(facturas, todasLasPartidas) {
        buildReviewClosureSummary(
            facturas,
            todasLasPartidas,
            context.getString(R.string.revision_unknown_invoice),
            context.getString(R.string.revision_part_without_number)
        )
    }

    LaunchedEffect(showTerminarDialog, reviewClosureSummary.hasWarnings) {
        if (showTerminarDialog && reviewClosureSummary.hasWarnings) {
            finishCountdown = 5
            while (finishCountdown > 0) {
                delay(1000)
                finishCountdown--
            }
        } else {
            finishCountdown = 0
        }
    }

    PmmStatusBar(color = PmmBlueDeep, darkIcons = false)
    
    // El destino debe sobrevivir mientras la pantalla de cámara está encima
    // de esta pantalla. Con remember simple se perdía al salir de composición
    // y la lectura regresaba sin saber si era marca, modelo, serie o búsqueda.
    var fieldBeingScanned by rememberSaveable { mutableStateOf<String?>(null) }

    val celebrateOnExit by viewModel.celebrateOnExit.collectAsState()
    var showConfetti by remember { mutableStateOf(false) }
    // Lectura de etiqueta por foto. Vive en su propio ViewModel para no
    // engordar DetalleRevisionViewModel, que ya carga facturas, partidas,
    // paises, fotos, hazmat y permisos.
    val labelCaptureViewModel: LabelCaptureViewModel = hiltViewModel()
    val labelCaptureEstado by labelCaptureViewModel.estado.collectAsState()
    val labelCaptureDrafts by labelCaptureViewModel.drafts.collectAsState()
    var capturandoEtiqueta by remember { mutableStateOf(false) }
    var partidaParaEtiqueta by remember { mutableStateOf<PartidaRevisionDto?>(null) }
    var showCaptureDrafts by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.crefAgrupada, todasLasPartidas, paisesList) {
        labelCaptureViewModel.configure(viewModel.crefAgrupada, todasLasPartidas, paisesList)
    }
    LaunchedEffect(labelCaptureDrafts.filter { it.status == com.example.pmmovil.data.model.LabelCaptureStatus.CONFIRMED }.map { it.captureId }) {
        if (labelCaptureDrafts.any { it.status == com.example.pmmovil.data.model.LabelCaptureStatus.CONFIRMED }) {
            viewModel.loadData()
            viewModel.cargarFotos()
        }
    }

    LaunchedEffect(navegarAtras) {
        if (navegarAtras) {
            if (celebrateOnExit) {
                showConfetti = true
                delay(1300)
            }
            viewModel.consumeNavBack()
            onNavigateBack()
        }
    }

    LaunchedEffect(toast, toastText) {
        toastText?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearToast()
        }
    }

    // Snackbar con "Deshacer" tras un escaneo que acumuló cantidad solo
    // (ver DetalleRevisionViewModel.procesarCodigoEscaneado): le da al
    // revisor una salida inmediata si el escaneo pegó en la partida
    // equivocada, sin tener que ir a corregir el número a mano.
    LaunchedEffect(ultimaAcumulacion) {
        ultimaAcumulacion?.let { evento ->
            val resultado = snackbarHostState.showSnackbar(
                message = ultimaAcumulacionText.orEmpty(),
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Long
            )
            if (resultado == SnackbarResult.ActionPerformed) {
                viewModel.deshacerAcumulacion(evento)
            }
            viewModel.consumeUltimaAcumulacion()
        }
    }

    // Abre el share sheet nativo de Android con el link de seguimiento --
    // mismo patrón que en Entrada, pero apuntando a la página pública de
    // "Seguimiento de revisión" (contenido distinto: estatus por factura y
    // discrepancias, no los datos de la entrada).
    LaunchedEffect(shareUrl) {
        shareUrl?.let { url ->
            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    android.content.Intent.EXTRA_TEXT,
                    context.getString(R.string.revision_share_subject, viewModel.crefAgrupada, url)
                )
            }
            context.startActivity(
                android.content.Intent.createChooser(
                    sendIntent,
                    context.getString(R.string.revision_share_chooser)
                )
            )
            viewModel.consumeShareUrl()
        }
    }

    // Camera Scan results handling
    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf { it.isNotBlank() }?.let { code ->
            val cleanCode = BarcodeValueNormalizer.normalize(code)
            val field = fieldBeingScanned
            val activePartida = partidas.getOrNull(activePartidaIndex - 1)
            if (field == "buscarParte") {
                // El buscador reutiliza el mismo flujo de escaneo que ya
                // existe para los campos de la partida, en vez de montar un
                // lector aparte -- así el lector físico también sirve para
                // buscar por número de parte. procesarCodigoEscaneado detecta
                // si el código trae la forma de una etiqueta segmentada
                // (ej. compuesto químico Freudenberg: "+30S...+Q...+H...") y,
                // de ser así, salta directo a la partida y acumula la
                // cantidad; si no, cae de vuelta a la búsqueda de texto normal.
                viewModel.procesarCodigoEscaneado(cleanCode)
            } else if (activePartida != null && field != null) {
                viewModel.actualizarDatoPartida(activePartida, field, cleanCode)
                snackbarHostState.showSnackbar(
                    context.getString(R.string.revision_field_updated, field),
                    duration = SnackbarDuration.Short
                )
            } else {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.revision_scanned, cleanCode),
                    duration = SnackbarDuration.Short
                )
            }
            fieldBeingScanned = null
            onBarcodeScanConsumed()
        }
    }

    LaunchedEffect(photosChanged) {
        if (photosChanged) {
            viewModel.cargarFotos()
            onPhotosChangedConsumed()
        }
    }

    // --- DIALOGS & MODALS ---

    cierreForzoso?.let { cierre ->
        AlertDialog(
            onDismissRequest = { /* El revisor debe confirmar que recibió el aviso. */ },
            containerColor = PmmNavySurface,
            title = {
                Text(
                    stringResource(R.string.revision_force_closed_title),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        cierre.mensaje?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.revision_force_closed_message),
                        color = PmmTextSecondary
                    )
                    Text(
                        stringResource(
                            R.string.revision_supervisor,
                            cierre.supervisor?.takeIf(String::isNotBlank)
                                ?: stringResource(R.string.revision_not_indicated)
                        ),
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(
                            R.string.revision_reason,
                            cierre.motivo?.takeIf(String::isNotBlank)
                                ?: stringResource(R.string.revision_reason_not_indicated)
                        ),
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
                    Text(
                        stringResource(R.string.common_understood),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    if (showTerminarDialog) {
        AlertDialog(
            onDismissRequest = { showTerminarDialog = false },
            containerColor = PmmNavySurface,
            title = {
                Text(
                    stringResource(R.string.revision_finish_title),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (reviewClosureSummary.hasWarnings) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.revision_pending_information),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (reviewClosureSummary.incompleteInvoices.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.revision_incomplete_invoices,
                                    reviewClosureSummary.incompleteInvoices.joinToString(", ")
                                ),
                                color = PmmTextSecondary
                            )
                            Text(
                                stringResource(R.string.revision_finish_without_email),
                                color = PmmYellow
                            )
                        }
                        if (reviewClosureSummary.pendingPartDescriptions.isNotEmpty()) {
                            Text(
                                stringResource(
                                    R.string.revision_unreviewed_parts,
                                    reviewClosureSummary.pendingPartDescriptions.size,
                                    reviewClosureSummary.pendingPartDescriptions.take(8).joinToString(", ")
                                ),
                                color = PmmTextSecondary
                            )
                        }
                        Text(
                            stringResource(R.string.revision_review_before_continue),
                            color = PmmTextSecondary
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.revision_all_invoices_complete),
                        color = PmmTextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (finishCountdown == 0) {
                            showTerminarDialog = false
                            viewModel.terminarRevision()
                        }
                    },
                    enabled = finishCountdown == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = PmmGreen)
                ) {
                    if (finishCountdown > 0) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.revision_read_notice, finishCountdown))
                    } else {
                        Text(stringResource(R.string.revision_finish))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTerminarDialog = false }) {
                    Text(stringResource(R.string.common_cancel), color = PmmTextSecondary)
                }
            }
        )
    }

    if (autoFinishPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAutoFinish() },
            containerColor = PmmNavySurface,
            title = {
                Text(
                    stringResource(R.string.revision_all_done),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.revision_all_complete_confirm),
                    color = PmmTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAutoFinish() },
                    colors = ButtonDefaults.buttonColors(containerColor = PmmGreen)
                ) {
                    Text(stringResource(R.string.revision_finish), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAutoFinish() }) {
                    Text(stringResource(R.string.revision_not_yet), color = PmmTextSecondary)
                }
            }
        )
    }

    if (showDiscrepanciaModal) {
        var discText by remember { mutableStateOf(facturaActual?.Discrepancias ?: "") }
        AlertDialog(
            onDismissRequest = { showDiscrepanciaModal = false },
            containerColor = PmmNavySurface,
            title = {
                Text(
                    stringResource(R.string.revision_register_discrepancy),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(R.string.revision_describe_problem),
                        color = PmmTextSecondary,
                        fontSize = 16.sp
                    )
                    OutlinedTextField(
                        value = discText,
                        onValueChange = { discText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PmmYellow,
                            unfocusedBorderColor = PmmNavyBorder,
                            focusedTextColor = PmmTextPrimary,
                            unfocusedTextColor = PmmTextPrimary,
                            cursorColor = PmmYellow
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        facturaActual?.let { f ->
                            viewModel.guardarCampoFactura(f.Cnofact, "discrepancias", discText)
                            viewModel.setStatusFactura(f.Cnofact, 2)
                        }
                        showDiscrepanciaModal = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                ) {
                    Text(
                        stringResource(R.string.revision_report_discrepancy),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscrepanciaModal = false }) {
                    Text(stringResource(R.string.common_cancel), color = PmmTextSecondary)
                }
            }
        )
    }

    validation?.let { valState ->
        when (valState) {
            is CompletaValidation.BultosDiferencia -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    containerColor = PmmNavySurface,
                    title = {
                        Text(
                            stringResource(R.string.revision_package_difference),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                R.string.revision_package_difference_message,
                                valState.bultos,
                                valState.bultosDoc
                            ),
                            color = PmmTextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmBultosDiferencia(valState.cnofact) },
                            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
                        ) { Text(stringResource(R.string.revision_complete), fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissValidationDialog() }) {
                            Text(stringResource(R.string.common_cancel), color = PmmTextSecondary)
                        }
                    }
                )
            }
            is CompletaValidation.EvidenciaDiscrepanciaSinMarcar -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    containerColor = PmmNavySurface,
                    title = {
                        Text(
                            stringResource(R.string.revision_discrepancy_evidence_title),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                R.string.revision_discrepancy_evidence_message,
                                valState.cantidadFotos
                            ),
                            color = PmmTextSecondary
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.dismissValidationDialog()
                                showDiscrepanciaModal = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                        ) {
                            Text(
                                stringResource(R.string.revision_mark_discrepancy),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onConfirmEvidenciaDiscrepancia(valState.cnofact) }) {
                            Text(
                                stringResource(R.string.revision_continue_complete),
                                color = PmmTextSecondary
                            )
                        }
                    }
                )
            }
            is CompletaValidation.HazmatAlerta -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    containerColor = PmmNavySurface,
                    title = {
                        Text(
                            stringResource(R.string.revision_hazmat_title),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column {
                            Text(
                                stringResource(R.string.revision_missing_safety_sheets),
                                color = PmmTextSecondary
                            )
                            valState.problemas.forEach { prob ->
                                Text("• $prob", color = PmmAmber, fontSize = 15.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.revision_resolve_issues),
                                color = PmmTextSecondary
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmHazmatAlerta(valState.cnofact) },
                            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
                        ) {
                            Text(
                                stringResource(R.string.common_understood),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                )
            }
            is CompletaValidation.PartidasSinRevisar -> {
                var motivo by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    containerColor = PmmNavySurface,
                    title = {
                        Text(
                            stringResource(R.string.revision_unreviewed_parts_title),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(
                                    R.string.revision_unreviewed_parts_message,
                                    valState.totalSinRevisar,
                                    valState.ejemplos.joinToString(", ")
                                ),
                                color = PmmTextSecondary
                            )
                            OutlinedTextField(
                                value = motivo,
                                onValueChange = { motivo = it },
                                label = {
                                    Text(
                                        stringResource(R.string.revision_incomplete_close_reason),
                                        color = PmmTextMuted,
                                        fontSize = 15.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PmmYellow,
                                    unfocusedBorderColor = PmmNavyBorder,
                                    focusedTextColor = PmmTextPrimary,
                                    unfocusedTextColor = PmmTextPrimary,
                                    cursorColor = PmmYellow
                                )
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmPartidasSinRevisar(valState.cnofact, motivo) },
                            enabled = motivo.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmBlueDeep)
                        ) {
                            Text(
                                stringResource(R.string.revision_send_request),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissValidationDialog() }) {
                            Text(stringResource(R.string.common_cancel), color = PmmTextSecondary)
                        }
                    }
                )
            }
            is CompletaValidation.DiscrepanciasAclarar -> {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissValidationDialog() },
                    containerColor = PmmNavySurface,
                    title = {
                        Text(
                            stringResource(R.string.revision_captured_discrepancies),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            stringResource(
                                R.string.revision_captured_discrepancies_message,
                                valState.discrepancias
                            ),
                            color = PmmTextSecondary
                        )
                    },
                    confirmButton = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { viewModel.onConfirmAclararDiscrepancias(valState.cnofact, false) },
                                colors = ButtonDefaults.buttonColors(containerColor = PmmRed),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.revision_keep_discrepancy),
                                    color = Color.White
                                )
                            }
                            Button(
                                onClick = { viewModel.onConfirmAclararDiscrepancias(valState.cnofact, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = PmmGreen),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.revision_clarified),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.dismissValidationDialog() }) {
                            Text(stringResource(R.string.common_cancel), color = PmmTextSecondary)
                        }
                    }
                )
            }
        }
    }

    val assignedUser by viewModel.usuarioAsignado.collectAsState()
    val desbloqueoSolicitado by viewModel.desbloqueoSolicitado.collectAsState()
    val desbloqueoAprobado by viewModel.desbloqueoAprobado.collectAsState()
    val fotos by viewModel.fotos.collectAsState()
    val cantidadFotosDiscrepancia by viewModel.fotosDiscrepanciaFacturaActual.collectAsState()
    var motivoDesbloqueo by remember { mutableStateOf("") }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val hasInvoicePane = maxWidth >= 600.dp
    // Con el PDF abierto en pantalla dividida, el banner de contexto y la barra
    // de progreso se mudan al panel izquierdo. Hablan de la revision, no del
    // documento, y cruzando la pantalla completa le quitaban 124 dp de alto al
    // visor, que es justo la dimension que le faltaba.
    val bandsInPane = hasInvoicePane && isPdfOpen && !isPdfExpanded
    // Ampliar deja de ser solo "mas ancho": el panel ya ocupaba todo el ancho y
    // el documento no crecia ni un pixel porque seguia mandando el alto. A
    // pantalla completa se retira el resto del cromo para ganar ese alto.
    // En celular el PDF ya ocupa toda el area de contenido, asi que entra
    // directo en modo inmersivo: se cierra con la X del propio visor.
    val immersivePdf = isPdfOpen && (isPdfExpanded || !hasInvoicePane)
    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbarHostState) },
        topBar = {
            if (!immersivePdf) {
            RevisionTopBar(
                reference = viewModel.crefAgrupada,
                timer = formatTimer(timerSeconds),
                showTimer = timerActivo || timerSeconds > 0,
                isConnected = viewModel.isHubConnected(),
                photoCount = fotos.size + pendingPhotoCount,
                hasActiveInvoice = facturaActual != null,
                canAddPhoto = puedeAgregarRevision,
                isPdfOpen = isPdfOpen,
                onBack = onNavigateBack,
                onPhotos = {
                    // Top bar = evidencia NORMAL de revisión (ntipodocto=107).
                    facturaActual?.let { invoice ->
                        onNavigateToCamera(
                            invoice.Cnofact,
                            invoice.Cclienteid.orEmpty(),
                            false
                        )
                    }
                },
                onGallery = {
                    // Entrada directa a la galería, sin pasar por la cámara:
                    // ver lo ya tomado no debería costar encender el sensor.
                    facturaActual?.let { invoice ->
                        onNavigateToGallery(invoice.Cnofact, invoice.Cclienteid.orEmpty())
                    }
                },
                onPdf = {
                    if (isPdfOpen) isPdfExpanded = false
                    isPdfOpen = !isPdfOpen
                },
                onCompartir = viewModel::compartirEntrada,
                isSharingLink = isSharingLink,
                canReadLabel = puedeModificarRevision && !isEspectador && todasLasPartidas.isNotEmpty(),
                captureDraftCount = labelCaptureDrafts.size,
                onReadLabel = {
                    if (labelCaptureDrafts.isNotEmpty()) showCaptureDrafts = true
                    else {
                        partidaParaEtiqueta = null
                        capturandoEtiqueta = true
                    }
                }
            )
            }
        },
        bottomBar = {
            if (facturas.isNotEmpty() && !immersivePdf && !bandsInPane) {
                RevisionCompletionBar(
                    facturas = facturas,
                    enabled = !entradaCerrada && !isEspectador && puedeModificarRevision,
                    isSpectator = isEspectador,
                    onFinish = { showTerminarDialog = true }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!bandsInPane && !immersivePdf) {
            RevisionContextBanner(
                supervisor = supervisorObservando,
                isSpectator = isEspectador,
                isClosed = entradaCerrada,
                relacionCerrada = relacionCerrada,
                desbloqueoAprobado = desbloqueoAprobado,
                assignedUser = assignedUser,
                unlockRequested = desbloqueoSolicitado,
                unlockReason = motivoDesbloqueo,
                onUnlockReasonChange = { motivoDesbloqueo = it },
                onRequestUnlock = {
                    viewModel.solicitarDesbloqueo(motivoDesbloqueo)
                    motivoDesbloqueo = ""
                },
                onCancelUnlock = viewModel::cancelarDesbloqueo
            )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val availableWidth = maxWidth
                val showInvoicePane = availableWidth >= 600.dp

                when {
                    isLoading && facturas.isEmpty() -> {
                        RevisionLoadingState()
                    }

                    facturas.isEmpty() -> {
                        RevisionEmptyState(error?.asString())
                    }

                    showInvoicePane -> {
                        if (isPdfOpen && isPdfExpanded) {
                            InlinePdfWorkspace(
                                crefAgrupada = viewModel.crefAgrupada,
                                initialReference = facturaActual?.Creferencia,
                                onClose = {
                                    isPdfExpanded = false
                                    isPdfOpen = false
                                },
                                isExpanded = true,
                                showExpandButton = true,
                                onToggleExpanded = { isPdfExpanded = false },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else Row(modifier = Modifier.fillMaxSize()) {
                            val showSidebar = !isPdfOpen ||
                                availableWidth >= 1100.dp
                            if (showSidebar) {
                                InvoiceSidebar(
                                    facturasCrudas = facturas,
                                    selectedIndex = selectedIndex,
                                    onSelect = viewModel::selectFactura,
                                    modifier = Modifier
                                        .width(if (isPdfOpen) 190.dp else 220.dp)
                                        .fillMaxHeight()
                                )
                                VerticalDivider(color = PmmNavyBorder)
                            }
                            Column(
                                modifier = Modifier.weight(
                                    if (isPdfOpen) {
                                        (1f - pdfPaneFraction).coerceIn(0.30f, 0.70f)
                                    } else {
                                        1f
                                    }
                                )
                            ) {
                            if (bandsInPane) {
                                RevisionContextBanner(
                                    supervisor = supervisorObservando,
                                    isSpectator = isEspectador,
                                    isClosed = entradaCerrada,
                                    relacionCerrada = relacionCerrada,
                desbloqueoAprobado = desbloqueoAprobado,
                                    assignedUser = assignedUser,
                                    unlockRequested = desbloqueoSolicitado,
                                    unlockReason = motivoDesbloqueo,
                                    onUnlockReasonChange = { motivoDesbloqueo = it },
                                    onRequestUnlock = {
                                        viewModel.solicitarDesbloqueo(motivoDesbloqueo)
                                        motivoDesbloqueo = ""
                                    },
                                    onCancelUnlock = viewModel::cancelarDesbloqueo
                                )
                            }
                            RevisionMainContent(
                                facturas = facturas,
                                selectedIndex = selectedIndex,
                                factura = facturaActual,
                                partidas = partidas,
                                activePartidaIndex = activePartidaIndex,
                                filtroPartidas = filterPartidas,
                                paises = paisesList,
                                paisesFrecuentes = paisesFrecuentesList,
                                tiposMaterial = tiposMaterialList,
                                unidadesComerciales = unidadesComercialesList,
                                error = error?.asString(),
                                isLocked = isFacturaLocked,
                                isWorkflowLocked = revisionBloqueada,
                                canAddPartida = puedeAgregarRevision,
                                canDeletePartida = puedeBorrarRevision,
                                canAddPhoto = puedeAgregarRevision,
                                showInvoiceSelector = !showSidebar,
                                cantidadFotosDiscrepancia = cantidadFotosDiscrepancia,
                                busquedaParte = busquedaParte,
                                resultadosBusqueda = resultadosBusqueda,
                                jumpPulseSeq = jumpPulseSeq,
                                observacionGeneral = observacionGeneral,
                                onObservacionGeneralChange = viewModel::onObservacionGeneralChange,
                                onCommitObservacionGeneral = viewModel::guardarObservacionGeneral,
                                onSelectFactura = viewModel::selectFactura,
                                onPhoto = {
                                    // Botón "Evidencia de discrepancia" (antes redundante con
                                    // "Registrar discrepancia", ya removido) = evidencia de
                                    // DISCREPANCIA (ntipodocto=103).
                                    facturaActual?.let { invoice ->
                                        onNavigateToCamera(
                                            invoice.Cnofact,
                                            invoice.Cclienteid.orEmpty(),
                                            true
                                        )
                                    }
                                },
                                onSetStatus = { status ->
                                    facturaActual?.let {
                                        viewModel.setStatusFactura(it.Cnofact, status)
                                    }
                                },
                                onRegisterDiscrepancy = {
                                     showDiscrepanciaModal = true
                                 },
                                 onOpenPdf = {
                                     isPdfExpanded = false
                                     isPdfOpen = true
                                 },
                                 onEditInvoice = { field, value ->
                                    facturaActual?.let {
                                        viewModel.guardarCampoFactura(
                                            it.Cnofact,
                                            field,
                                            value
                                        )
                                    }
                                },
                                onFilterPartidas = viewModel::setFiltroPartidas,
                                onBusquedaChange = viewModel::setBusquedaParte,
                                onBusquedaResultClick = viewModel::saltarAResultadoBusqueda,
                                onBusquedaScan = {
                                    fieldBeingScanned = "buscarParte"
                                    onNavigateToScanner()
                                },
                                onJumpPartida = viewModel::saltarAPartida,
                                onUpdatePartida = { partida, field, value ->
                                    viewModel.actualizarDatoPartida(
                                        partida,
                                        field,
                                        value
                                    )
                                },
                                onUpdateCantidadComercial = { partida, cantidad, unidad ->
                                    viewModel.actualizarCantidadComercial(partida, cantidad, unidad)
                                },
                                onQuickFill = { partida, field, value ->
                                    viewModel.quickFillPartida(
                                        partida,
                                        field,
                                        value
                                    )
                                },
                                onScan = { field ->
                                    fieldBeingScanned = field
                                    onNavigateToScanner()
                                },
                                onCapturarEtiqueta = { partida ->
                                    partidaParaEtiqueta = partida
                                    capturandoEtiqueta = true
                                },
                                onTogglePartida = viewModel::toggleRevisadoPartida,
                                onDeletePartida = viewModel::eliminarPartida,
                                onAddPartida = {
                                    facturaActual?.let {
                                        viewModel.agregarPartida(it.Cnofact)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (bandsInPane && facturas.isNotEmpty()) {
                                RevisionCompletionBar(
                                    facturas = facturas,
                                     enabled = !entradaCerrada && !isEspectador && puedeModificarRevision,
                                    isSpectator = isEspectador,
                                    onFinish = { showTerminarDialog = true }
                                )
                            }
                            }
                            if (isPdfOpen) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(18.dp)
                                        .pointerInput(availableWidth) {
                                            detectHorizontalDragGestures { change, dragAmount ->
                                                change.consume()
                                                val totalWidth = availableWidth.toPx().coerceAtLeast(1f)
                                                pdfPaneFraction = (
                                                    pdfPaneFraction - dragAmount / totalWidth
                                                ).coerceIn(0.30f, 0.70f)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    VerticalDivider(color = PmmNavyBorder)
                                    Box(
                                        modifier = Modifier
                                            .width(3.dp)
                                            .fillMaxHeight(0.18f)
                                            .background(PmmYellow, RoundedCornerShape(3.dp))
                                    )
                                }
                                InlinePdfWorkspace(
                                    crefAgrupada = viewModel.crefAgrupada,
                                    initialReference = facturaActual?.Creferencia,
                                    onClose = {
                                        isPdfExpanded = false
                                        isPdfOpen = false
                                    },
                                    isExpanded = false,
                                    showExpandButton = true,
                                    onToggleExpanded = { isPdfExpanded = true },
                                    modifier = Modifier
                                        .weight(pdfPaneFraction)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }

                    else -> {
                        if (isPdfOpen) {
                            InlinePdfWorkspace(
                                crefAgrupada = viewModel.crefAgrupada,
                                initialReference = facturaActual?.Creferencia,
                                onClose = {
                                    isPdfExpanded = false
                                    isPdfOpen = false
                                },
                                isExpanded = true,
                                showExpandButton = false,
                                onToggleExpanded = {},
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            RevisionMainContent(
                            facturas = facturas,
                            selectedIndex = selectedIndex,
                            factura = facturaActual,
                            partidas = partidas,
                            activePartidaIndex = activePartidaIndex,
                            filtroPartidas = filterPartidas,
                            paises = paisesList,
                            paisesFrecuentes = paisesFrecuentesList,
                            tiposMaterial = tiposMaterialList,
                            unidadesComerciales = unidadesComercialesList,
                            error = error?.asString(),
                            isLocked = isFacturaLocked,
                            isWorkflowLocked = revisionBloqueada,
                            canAddPartida = puedeAgregarRevision,
                            canDeletePartida = puedeBorrarRevision,
                            canAddPhoto = puedeAgregarRevision,
                            showInvoiceSelector = true,
                            cantidadFotosDiscrepancia = cantidadFotosDiscrepancia,
                            busquedaParte = busquedaParte,
                            resultadosBusqueda = resultadosBusqueda,
                            jumpPulseSeq = jumpPulseSeq,
                            observacionGeneral = observacionGeneral,
                            onObservacionGeneralChange = viewModel::onObservacionGeneralChange,
                            onCommitObservacionGeneral = viewModel::guardarObservacionGeneral,
                            onSelectFactura = viewModel::selectFactura,
                            onPhoto = {
                                // Botón "Evidencia de discrepancia" (antes redundante con
                                // "Registrar discrepancia", ya removido) = evidencia de
                                // DISCREPANCIA (ntipodocto=103).
                                facturaActual?.let { invoice ->
                                    onNavigateToCamera(
                                        invoice.Cnofact,
                                        invoice.Cclienteid.orEmpty(),
                                        true
                                    )
                                }
                            },
                            onSetStatus = { status ->
                                facturaActual?.let {
                                    viewModel.setStatusFactura(it.Cnofact, status)
                                }
                            },
                             onRegisterDiscrepancy = {
                                 showDiscrepanciaModal = true
                             },
                             onOpenPdf = {
                                 isPdfExpanded = false
                                 isPdfOpen = true
                             },
                             onEditInvoice = { field, value ->
                                facturaActual?.let {
                                    viewModel.guardarCampoFactura(
                                        it.Cnofact,
                                        field,
                                        value
                                    )
                                }
                            },
                            onFilterPartidas = viewModel::setFiltroPartidas,
                            onBusquedaChange = viewModel::setBusquedaParte,
                            onBusquedaResultClick = viewModel::saltarAResultadoBusqueda,
                            onBusquedaScan = {
                                fieldBeingScanned = "buscarParte"
                                onNavigateToScanner()
                            },
                            onJumpPartida = viewModel::saltarAPartida,
                            onUpdatePartida = { partida, field, value ->
                                viewModel.actualizarDatoPartida(
                                    partida,
                                    field,
                                    value
                                )
                            },
                            onUpdateCantidadComercial = { partida, cantidad, unidad ->
                                viewModel.actualizarCantidadComercial(partida, cantidad, unidad)
                            },
                            onQuickFill = { partida, field, value ->
                                viewModel.quickFillPartida(
                                    partida,
                                    field,
                                    value
                                )
                            },
                            onScan = { field ->
                                fieldBeingScanned = field
                                onNavigateToScanner()
                            },
                            onCapturarEtiqueta = { partida ->
                                partidaParaEtiqueta = partida
                                capturandoEtiqueta = true
                            },
                            onTogglePartida = viewModel::toggleRevisadoPartida,
                            onDeletePartida = viewModel::eliminarPartida,
                            onAddPartida = {
                                facturaActual?.let {
                                    viewModel.agregarPartida(it.Cnofact)
                                }
                            },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

        ConfettiOverlay(
            visible = showConfetti,
            modifier = Modifier.fillMaxSize()
        )

        // ── Lectura de etiqueta por foto ────────────────────────────────────
        if (capturandoEtiqueta) {
            // Captura guiada en vivo, no la camara de evidencias: el revisador
            // ve las marcas sobre la etiqueta antes de que salga la foto, y los
            // codigos que el lector alcance a decodificar viajan con ella --
            // un codigo le gana al texto porque es exacto.
            LabelCaptureLiveScreen(
                titulo = stringResource(R.string.revision_read_label_title),
                subtitulo = stringResource(R.string.revision_read_label_hint),
                onFotoTomada = { path, codigosEnVivo ->
                    capturandoEtiqueta = false
                    labelCaptureViewModel.analizar(
                        path = path,
                        codigosEnVivo = codigosEnVivo,
                        catalogoPaises = paisesList,
                        initialPartidaId = partidaParaEtiqueta?.IdRevdet
                    )
                },
                onCerrar = { capturandoEtiqueta = false }
            )
        }

        if (labelCaptureEstado.fotoPath != null) {
            LabelCaptureSheet(
                estado = labelCaptureEstado,
                onAlternar = labelCaptureViewModel::alternar,
                onEdit = labelCaptureViewModel::editField,
                onSelectPartida = labelCaptureViewModel::selectPartida,
                onIdentityAccepted = labelCaptureViewModel::acceptIdentity,
                onPhysicalReviewConfirmed = labelCaptureViewModel::confirmPhysicalReview,
                onSave = labelCaptureViewModel::save,
                onImprove = labelCaptureViewModel::improveReading,
                onRetry = labelCaptureViewModel::retry,
                onReloadContext = labelCaptureViewModel::reloadContext,
                onDiscard = { labelCaptureViewModel.discardDraft() },
                onRepetir = {
                    labelCaptureViewModel.discardDraft { capturandoEtiqueta = true }
                },
                onCerrar = {
                    labelCaptureViewModel.limpiar()
                    partidaParaEtiqueta = null
                }
            )
        }

        if (showCaptureDrafts) {
            AlertDialog(
                onDismissRequest = { showCaptureDrafts = false },
                title = { Text(stringResource(R.string.label_capture_saved_drafts)) },
                text = {
                    LazyColumn {
                        items(labelCaptureDrafts, key = { it.captureId }) { draft ->
                            TextButton(onClick = {
                                showCaptureDrafts = false
                                labelCaptureViewModel.restoreDraft(draft.captureId)
                            }) {
                                val status = when (draft.status) {
                                    "CONFIRMED" -> R.string.label_capture_draft_confirmed
                                    "PENDING" -> R.string.label_capture_draft_pending
                                    "BLOCKED" -> R.string.label_capture_draft_blocked
                                    else -> R.string.label_capture_draft_saved
                                }
                                Text("${stringResource(status)} · ${draft.captureId.take(8)}")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showCaptureDrafts = false
                        partidaParaEtiqueta = null
                        capturandoEtiqueta = true
                    }) { Text(stringResource(R.string.label_capture_new)) }
                },
                dismissButton = {
                    TextButton(onClick = { showCaptureDrafts = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            )
        }
    }
}

private fun formatTimer(seconds: Int): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@Composable
private fun RevisionTopBar(
    reference: String,
    timer: String,
    showTimer: Boolean,
    isConnected: Boolean,
    photoCount: Int,
    hasActiveInvoice: Boolean,
    canAddPhoto: Boolean,
    isPdfOpen: Boolean,
    onBack: () -> Unit,
    onPhotos: () -> Unit,
    onGallery: () -> Unit,
    onPdf: () -> Unit,
    onCompartir: () -> Unit,
    isSharingLink: Boolean,
    canReadLabel: Boolean,
    captureDraftCount: Int,
    onReadLabel: () -> Unit
) {
    Surface(
        color = PmmBlueDeep,
        shadowElevation = 4.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // ~360dp es el ancho típico de un celular de 6" en vertical. Ahí
            // ya no cabe todo con texto: se compacta a solo íconos y se
            // recorta "Revisión normal" para no repetir información obvia.
            val compact = maxWidth < 400.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = reference,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 16.sp else 19.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!compact) {
                                Text(
                                    text = stringResource(R.string.revision_normal_mode),
                                    color = Color.White.copy(alpha = 0.66f),
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected) {
                                            Color(0xFF4ADE80)
                                        } else {
                                            Color.White.copy(alpha = 0.35f)
                                        }
                                    )
                            )
                            if (!compact) {
                                Text(
                                    text = stringResource(
                                        if (isConnected) R.string.revision_online
                                        else R.string.revision_reconnecting
                                    ),
                                    color = if (isConnected) {
                                        Color(0xFF86EFAC)
                                    } else {
                                        Color.White.copy(alpha = 0.58f)
                                    },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.wrapContentWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showTimer) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.18f))
                                .padding(
                                    horizontal = if (compact) 7.dp else 10.dp,
                                    vertical = 8.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = stringResource(R.string.revision_timer_content_description),
                                tint = PmmYellow,
                                modifier = Modifier.size(15.dp)
                            )
                            if (!compact) {
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = timer,
                                    color = PmmYellow,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.width(4.dp))
                    }

                    IconButton(onClick = onReadLabel, enabled = canReadLabel || captureDraftCount > 0) {
                        BadgedBox(badge = {
                            if (captureDraftCount > 0) Badge { Text(captureDraftCount.toString()) }
                        }) {
                            Icon(Icons.Default.DocumentScanner,
                                contentDescription = stringResource(R.string.revision_read_label_title),
                                tint = if (canReadLabel || captureDraftCount > 0) PmmYellow else PmmTextMuted)
                        }
                    }

                    // Fotos de la revisión normal (ntipodocto=107). En celular angosto
                    // se conserva el acceso y el contador, pero se oculta la etiqueta.
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Color.Black.copy(
                                    alpha = if (hasActiveInvoice) 0.18f else 0.08f
                                )
                            )
                            .clickable(
                                enabled = hasActiveInvoice && canAddPhoto,
                                onClick = onPhotos
                            )
                            .padding(
                                horizontal = if (compact) 8.dp else 10.dp,
                                vertical = 9.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                                contentDescription = stringResource(R.string.revision_photos_content_description),
                                tint = if (hasActiveInvoice && canAddPhoto) {
                                    Color.White
                                } else {
                                Color.White.copy(alpha = 0.35f)
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        if (!compact) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                            text = stringResource(R.string.revision_photos),
                                color = if (hasActiveInvoice && canAddPhoto) {
                                    Color.White
                                } else {
                                    Color.White.copy(alpha = 0.35f)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        if (photoCount > 0) {
                            Spacer(Modifier.width(if (compact) 4.dp else 6.dp))
                            Surface(color = PmmYellow, shape = CircleShape) {
                                Text(
                                    text = photoCount.coerceAtMost(99).toString(),
                                    color = PmmBlueDeep,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(
                                        horizontal = 6.dp,
                                        vertical = 2.dp
                                    )
                                )
                            }
                        }
                    }

                    if (!compact) Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onGallery,
                        enabled = hasActiveInvoice
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = stringResource(R.string.revision_gallery_content_description),
                            tint = if (hasActiveInvoice) {
                                Color.White
                            } else {
                                Color.White.copy(alpha = 0.35f)
                            }
                        )
                    }
                    IconButton(
                        onClick = onPdf,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPdfOpen) {
                                PmmYellow.copy(alpha = 0.18f)
                            } else {
                                Color.Transparent
                            }
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = if (isPdfOpen) {
                                stringResource(R.string.common_close_message)
                            } else {
                                stringResource(R.string.revision_open_pdf)
                            },
                            tint = if (isPdfOpen) PmmYellow else Color.White
                        )
                    }
                    IconButton(onClick = onCompartir, enabled = !isSharingLink) {
                        if (isSharingLink) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.revision_share_entry_content_description),
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionCompletionBar(
    facturas: List<FacturaRevisionDto>,
    enabled: Boolean,
    isSpectator: Boolean = false,
    onFinish: () -> Unit
) {
    val completed = facturas.count { it.StatusRev == 1 }
    val total = facturas.size
    val progress = if (total > 0) completed.toFloat() / total else 0f

    Surface(
        color = PmmBgSurface,
        shadowElevation = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.revision_progress),
                        color = PmmTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.revision_invoice_progress, completed, total),
                        color = if (progress >= 1f) PmmGreen else PmmBlueDeep,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progress >= 1f) PmmGreen else PmmBlueDeep,
                    trackColor = PmmNavyBorder
                )
            }
            Button(
                onClick = onFinish,
                enabled = enabled,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmYellow,
                    contentColor = PmmBlueDeep
                ),
                contentPadding = PaddingValues(
                    horizontal = 18.dp,
                    vertical = 12.dp
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = when {
                        enabled -> stringResource(R.string.revision_finish_short)
                        isSpectator -> stringResource(R.string.revision_read_only)
                        else -> stringResource(R.string.revision_finished)
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Antes había dos franjas apiladas: un banner amarillo fijo "Revisión
 * finalizada" (siempre que isClosed) y, debajo, un banner rosa con el
 * formulario/estado de solicitud (siempre que isSpectator || isClosed).
 * Cuando ambas condiciones se cumplían a la vez -- el caso normal de entrar
 * a pedir desbloqueo -- se apilaban dos títulos + dos subtítulos diciendo
 * básicamente lo mismo ("ya fue finalizada"), y en horizontal de tableta eso
 * se comía una tajada grande del alto disponible. Ahora es una sola franja:
 * el título ya explica el estado y la acción (formulario / esperando /
 * relación cerrada) vive al lado en vez de en un segundo bloque.
 */
@Composable
private fun RevisionContextBanner(
    supervisor: String?,
    isSpectator: Boolean,
    isClosed: Boolean,
    relacionCerrada: Boolean,
    desbloqueoAprobado: Boolean,
    assignedUser: String?,
    unlockRequested: Boolean,
    unlockReason: String,
    onUnlockReasonChange: (String) -> Unit,
    onRequestUnlock: () -> Unit,
    onCancelUnlock: () -> Unit
) {
    Column {
        supervisor?.let { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PmmNavyVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.revision_observing, name),
                    color = PmmBlueDeep,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val bannerState = when {
            isClosed && desbloqueoAprobado -> "closed_authorized"
            (isSpectator || isClosed) && !desbloqueoAprobado -> when {
                relacionCerrada -> "relacion_cerrada"
                unlockRequested -> "esperando"
                else -> "formulario"
            }
            else -> "none"
        }

        AnimatedContent(
            targetState = bannerState,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 })
                    .togetherWith(fadeOut(tween(120)))
            },
            label = "revisionContextBanner"
        ) { state ->
            when (state) {
                "none" -> Spacer(Modifier.height(0.dp))

                // Ya tiene autorización vigente: seguir diciendo "disponible
                // en modo de consulta" sería falso, porque en este momento
                // SÍ puede editar. Un aviso simple de una línea basta -- no
                // hay ninguna acción que ofrecer al lado.
                "closed_authorized" -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF7D6))
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = PmmAmberDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.revision_finished),
                            color = PmmAmberDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.revision_special_authorization),
                            color = PmmTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                else -> {
                    val titulo = if (isClosed) {
                        stringResource(R.string.revision_finished)
                    } else {
                        assignedUser?.let {
                            stringResource(R.string.assignments_in_use, it)
                        } ?: stringResource(R.string.revision_in_use_by_other)
                    }
                    UnlockActionBanner(titulo = titulo, mostrarPulso = state == "esperando") {
                        when (state) {
                            // A diferencia de "revisión finalizada", una
                            // relación ya cerrada es un cierre físico/
                            // logístico definitivo -- no hay nada que
                            // solicitar. El backend también rechaza esto (ver
                            // RevisionService.SolicitarDesbloqueoAsync), pero
                            // mostrarlo aquí evita que el usuario escriba un
                            // motivo para una solicitud que de todos modos
                            // será rechazada.
                            "relacion_cerrada" -> RelacionCerradaChip()
                            "esperando" -> EsperandoAutorizacionChip(onCancelUnlock)
                            else -> SolicitarAccesoForm(unlockReason, onUnlockReasonChange, onRequestUnlock)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Franja fusionada de aviso + acción: insignia con candado, título y -- si
 * hay ancho -- el contenido de acción en el mismo renglón, separado por un
 * divisor sutil. Sin ancho suficiente (celular en vertical) se apila en
 * columna, pero sigue siendo un solo bloque, no dos.
 */
@Composable
private fun UnlockActionBanner(
    titulo: String,
    mostrarPulso: Boolean,
    contenido: @Composable () -> Unit
) {
    val fondo = Brush.linearGradient(
        colors = listOf(Color(0xFFFFF6D2), Color(0xFFFFF0E8), Color(0xFFFFECEC))
    )
    val bordeInferior = PmmAmberDark.copy(alpha = 0.14f)
    val acentoInicio = Color(0xFFEAB308)
    val acentoMedio = Color(0xFFF59E0B)
    val acentoFin = PmmRed

    // OJO: BoxWithConstraints es un SubcomposeLayout -- no admite vivir dentro
    // de un Modifier.height(IntrinsicSize.Min) (eso fue justo lo que crasheó
    // la pantalla: "Asking for intrinsic measurements of SubcomposeLayout
    // layouts is not supported"). Por eso la barra de acento lateral y el
    // borde inferior se pintan con drawBehind -- usa el tamaño ya calculado
    // en la fase de dibujo, no pide medidas intrínsecas a nadie.
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(fondo)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(listOf(acentoInicio, acentoMedio, acentoFin)),
                    size = Size(3.5.dp.toPx(), size.height)
                )
                drawLine(
                    color = bordeInferior,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .padding(start = 3.5.dp)
    ) {
        val esAncho = maxWidth >= 460.dp
        if (esAncho) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TituloConInsignia(titulo, mostrarPulso)
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    PmmAmberDark.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Box(modifier = Modifier.weight(1f)) { contenido() }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TituloConInsignia(titulo, mostrarPulso)
                contenido()
            }
        }
    }
}

@Composable
private fun TituloConInsignia(titulo: String, pulsando: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        InsigniaCandado(pulsando)
        Text(
            text = titulo,
            color = Color(0xFF92400E),
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Candado en una insignia circular en vez de un ícono suelto -- le da peso
 * visual al punto focal del banner. Cuando hay una solicitud en curso, un
 * anillo respira alrededor muy lento (2.4s) y sin parpadeo: comunica "esto
 * sigue en curso" sin ser ruidoso.
 */
@Composable
private fun InsigniaCandado(pulsando: Boolean) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(30.dp)) {
        if (pulsando) {
            val transition = rememberInfiniteTransition(label = "unlockPulse")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "unlockPulseProgress"
            )
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .scale(0.85f + 0.5f * progress)
                    .alpha((1f - progress) * 0.7f)
                    .border(1.5.dp, PmmAmber.copy(alpha = 0.45f), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0xFFFDE68A), Color(0xFFFCA5A5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF92400E),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun RelacionCerradaChip() {
    // Antes el texto iba en blanco sobre un tinte casi transparente -- sobre
    // el fondo rosa claro del banner era prácticamente invisible. Ahora usa
    // el mismo rojo oscuro sólido que el resto del banner, con su propio
    // fondo y borde para que se lea como un chip, no como texto suelto.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(PmmRedDark.copy(alpha = 0.09f), PmmRedDark.copy(alpha = 0.04f))
                )
            )
            .border(1.dp, PmmRedDark.copy(alpha = 0.22f), RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = PmmRedDark,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.revision_closed_no_edit),
            color = PmmRedDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EsperandoAutorizacionChip(onCancelUnlock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(
                Brush.linearGradient(colors = listOf(Color(0xFFFFFBEB), Color(0xFFFFF3D6)))
            )
            .border(1.dp, PmmAmber.copy(alpha = 0.28f), RoundedCornerShape(9.dp))
            .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            color = PmmAmber,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.revision_waiting_authorization),
            color = PmmAmberDark,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onCancelUnlock,
            colors = ButtonDefaults.textButtonColors(
                contentColor = PmmAmberDark,
                containerColor = PmmAmberDark.copy(alpha = 0.08f)
            ),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                stringResource(R.string.common_cancel),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SolicitarAccesoForm(
    unlockReason: String,
    onUnlockReasonChange: (String) -> Unit,
    onRequestUnlock: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = unlockReason,
            onValueChange = onUnlockReasonChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = PmmRed.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            },
            placeholder = {
                Text(
                    text = stringResource(R.string.revision_access_reason_hint),
                    fontSize = 13.sp
                )
            },
            shape = RoundedCornerShape(9.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PmmRed,
                unfocusedBorderColor = Color(0xFFF2D2D2),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
        Button(
            onClick = onRequestUnlock,
            enabled = unlockReason.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = PmmRed),
            shape = RoundedCornerShape(9.dp),
            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp),
            modifier = Modifier.shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(9.dp),
                ambientColor = PmmRed.copy(alpha = 0.4f),
                spotColor = PmmRed.copy(alpha = 0.4f)
            )
        ) {
            Text(
                stringResource(R.string.common_request),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RevisionLoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PmmBlueDeep)
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.common_preparing_review),
                color = PmmTextSecondary,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun RevisionEmptyState(error: String?) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = null,
                tint = PmmTextMuted,
                modifier = Modifier.size(46.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.common_no_invoices),
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold
            )
            error?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = it,
                    color = PmmRedDark,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// Red de seguridad: Cnofact es la key del LazyColumn/LazyRow de facturas, y
// Compose truena con IllegalArgumentException si dos items comparten key. Ya
// se corrigió el origen real (GROUP BY duplicaba filas en GetFacturasEntradaAsync
// cuando una factura tenía partidas de mas de un proveedor), pero esta función
// evita que la pantalla vuelva a caerse en producción si algún día otro
// endpoint repite el mismo tipo de bug -- en vez de crash, se descarta el
// duplicado y se deja rastro en RevisionDiagnostics para poder investigarlo.
@Composable
private fun List<FacturaRevisionDto>.dedupedByCnofact(origen: String): List<FacturaRevisionDto> {
    val context = LocalContext.current
    return remember(this) {
        val unicas = distinctBy { it.Cnofact }
        if (unicas.size != size) {
            RevisionDiagnostics.record(
                context,
                "$origen: se descartaron ${size - unicas.size} factura(s) con Cnofact duplicado (revisar backend)"
            )
        }
        unicas
    }
}

@Composable
private fun InvoiceSidebar(
    facturasCrudas: List<FacturaRevisionDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val facturas = facturasCrudas.dedupedByCnofact("InvoiceSidebar")
    val completed = facturas.count { it.StatusRev == 1 }
    val bultos = facturas.sumOf { it.TotBultos }
    val weight = facturas.sumOf { it.nPesoKgs }
    val progress = if (facturas.isNotEmpty()) {
        completed.toFloat() / facturas.size
    } else {
        0f
    }

    Surface(
        modifier = modifier,
        color = PmmBgSurface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.revision_invoices),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp
                )
                Text(
                    text = stringResource(
                        R.string.assignments_progress_count,
                        completed,
                        facturas.size
                    ),
                    color = PmmTextSecondary,
                    fontSize = 14.sp
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (progress >= 1f) PmmGreen else PmmBlueDeep,
                    trackColor = PmmNavyBorder
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RevisionMetric(
                        label = stringResource(R.string.revision_physical_packages),
                        value = bultos.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    RevisionMetric(
                        label = stringResource(R.string.revision_weight_kg),
                        value = "${String.format(Locale.US, "%.0f", weight)} kg",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            HorizontalDivider(color = PmmNavyBorder)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                items(
                    items = facturas.withIndex().toList(),
                    key = { indexed -> indexed.value.Cnofact }
                ) { indexed ->
                    val index = indexed.index
                    val invoice = indexed.value
                    InvoiceSidebarItem(
                        invoice = invoice,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) }
                    )
                }
                if (facturas.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.common_no_invoices),
                            color = PmmTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RevisionMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(PmmNavyVariant)
            .padding(horizontal = 8.dp, vertical = 7.dp)
    ) {
        Text(
            text = value,
            color = PmmBlueDeep,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
        )
        Text(
            text = label,
            color = PmmTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun InvoiceSidebarItem(
    invoice: FacturaRevisionDto,
    selected: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (invoice.StatusRev) {
        1 -> PmmGreen
        2 -> PmmRed
        3 -> PmmAmber
        else -> PmmTextMuted
    }
    val statusLabel = when (invoice.StatusRev) {
        1 -> stringResource(R.string.assignments_status_review_finished)
        2 -> stringResource(R.string.revision_status_discrepancy)
        3 -> stringResource(R.string.assignments_status_in_progress_label)
        else -> stringResource(R.string.assignments_status_pending)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PmmNavyVariant else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) PmmBlueDeep.copy(alpha = 0.35f) else Color.Transparent
        )
    ) {
        Column(
            modifier = Modifier.padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = invoice.Cnofact,
                    color = if (selected) PmmBlueDeep else PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Text(
                text = invoice.NombreProveedor ?: stringResource(R.string.revision_supplier),
                color = PmmTextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = statusLabel,
                color = statusColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun RevisionMainContent(
    facturas: List<FacturaRevisionDto>,
    selectedIndex: Int,
    factura: FacturaRevisionDto?,
    partidas: List<PartidaRevisionDto>,
    activePartidaIndex: Int,
    filtroPartidas: String,
    paises: List<PaisDto>,
    paisesFrecuentes: List<PaisFrecuenteDto>,
    tiposMaterial: List<TipoMaterialDto>,
    unidadesComerciales: List<UnidadComercialDto>,
    error: String?,
    isLocked: Boolean,
    isWorkflowLocked: Boolean,
    canAddPartida: Boolean,
    canDeletePartida: Boolean,
    canAddPhoto: Boolean,
    showInvoiceSelector: Boolean,
    cantidadFotosDiscrepancia: Int,
    busquedaParte: String,
    resultadosBusqueda: List<ResultadoBusquedaRevision>,
    jumpPulseSeq: Int,
    observacionGeneral: String,
    onObservacionGeneralChange: (String) -> Unit,
    onCommitObservacionGeneral: () -> Unit,
    onSelectFactura: (Int) -> Unit,
    onPhoto: () -> Unit,
    onSetStatus: (Int) -> Unit,
    onRegisterDiscrepancy: () -> Unit,
    onOpenPdf: () -> Unit,
    onEditInvoice: (String, Any) -> Unit,
    onFilterPartidas: (String) -> Unit,
    onBusquedaChange: (String) -> Unit,
    onBusquedaResultClick: (ResultadoBusquedaRevision) -> Unit,
    onBusquedaScan: () -> Unit,
    onJumpPartida: (Int) -> Unit,
    onUpdatePartida: (PartidaRevisionDto, String, Any) -> Unit,
    onUpdateCantidadComercial: (PartidaRevisionDto, Double, String) -> Unit,
    onQuickFill: (PartidaRevisionDto, String, String) -> Unit,
    onScan: (String) -> Unit,
    onCapturarEtiqueta: ((PartidaRevisionDto) -> Unit)? = null,
    onTogglePartida: (PartidaRevisionDto) -> Unit,
    onDeletePartida: (Int) -> Unit,
    onAddPartida: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val partidasItemIndex =
        (if (error != null) 1 else 0) +
            (if (showInvoiceSelector) 1 else 0) +
            2 // Buscador global + detalle de factura

    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        if (error != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF1F2))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = PmmRedDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error,
                        color = PmmRedDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showInvoiceSelector) {
            item {
                FacturaCarrusel(
                    facturasCrudas = facturas,
                    selectedIndex = selectedIndex,
                    onSelect = onSelectFactura
                )
            }
        }

        if (factura != null) {
            item(key = "buscador-global-${factura.Cnofact}") {
                BuscadorPartidaBar(
                    query = busquedaParte,
                    resultados = resultadosBusqueda,
                    onQueryChange = onBusquedaChange,
                    onResultClick = { resultado ->
                        onBusquedaResultClick(resultado)
                        coroutineScope.launch {
                            listState.animateScrollToItem(partidasItemIndex)
                        }
                    },
                    onScan = onBusquedaScan
                )
            }
        }

        factura?.let { currentInvoice ->
            // 1. Header + Documento (info general, se queda arriba)
            item(key = "invoice-${currentInvoice.Cnofact}") {
                FacturaDetalle(
                    factura = currentInvoice,
                    isLocked = isLocked,
                    onOpenPdf = onOpenPdf
                )
            }

            // 2. Partidas: primero lo que revisan, agrupado en su propia caja
            item(key = "partidas-${currentInvoice.Cnofact}") {
                PartidasSection(
                    partidas = partidas,
                    activeIndex = activePartidaIndex,
                    filtroPartidas = filtroPartidas,
                    paises = paises,
                    paisesFrecuentes = paisesFrecuentes,
                    tiposMaterial = tiposMaterial,
                    unidadesComerciales = unidadesComerciales,
                    isLocked = isLocked,
                    canAddPartida = canAddPartida,
                    canDeletePartida = canDeletePartida,
                    jumpPulseSeq = jumpPulseSeq,
                    onFiltroChange = onFilterPartidas,
                    onJump = onJumpPartida,
                    onValueChange = onUpdatePartida,
                    onCantidadComercialChange = onUpdateCantidadComercial,
                    onQuickFill = onQuickFill,
                    onScan = onScan,
                    onCapturarEtiqueta = onCapturarEtiqueta,
                    onTogglePartida = { partida ->
                        onTogglePartida(partida)
                        if (!partida.Revisada) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(partidasItemIndex)
                            }
                        }
                    },
                    onDeletePartida = onDeletePartida,
                    onAddPartida = onAddPartida
                )
            }

            // 3. Inspección física: datos generales capturados en bodega
            item {
                InspeccionFisicaCard(
                    factura = currentInvoice,
                    isLocked = isLocked,
                    onEditCampo = onEditInvoice
                )
            }

            item {
                DiscrepanciaInfoBanners(factura = currentInvoice)
            }

            item {
                EvidenciaDiscrepanciaButton(
                    isLocked = isWorkflowLocked,
                    canAddPhoto = canAddPhoto,
                    cantidadFotos = cantidadFotosDiscrepancia,
                    onClick = onPhoto
                )
            }

            currentInvoice.UsuEdit?.takeIf { it.isNotBlank() }?.let { editor ->
                item { EditorActivoBadge(editor = editor) }
            }

            // 3.5 Observación general de TODA la revisión (traf3.observacion_revision,
            // no por factura) -- a propósito arriba de Estado de revisión: es lo
            // último que el revisor ve antes de marcar el resultado de la factura.
            item(key = "observacion-general") {
                ObservacionGeneralCard(
                    valor = observacionGeneral,
                    isLocked = isLocked,
                    onValueChange = onObservacionGeneralChange,
                    onCommit = onCommitObservacionGeneral
                )
            }

            // 4. Literalmente lo último de la pantalla: marcar el resultado
            item {
                EstadoRevisionCard(
                    factura = currentInvoice,
                    isLocked = isLocked,
                    onSetStatus = onSetStatus,
                    onRegistrarDiscrepancia = onRegisterDiscrepancy
                )
            }
        }
    }
}

@Composable
private fun PartidasSection(
    partidas: List<PartidaRevisionDto>,
    activeIndex: Int,
    filtroPartidas: String,
    paises: List<PaisDto>,
    paisesFrecuentes: List<PaisFrecuenteDto>,
    tiposMaterial: List<TipoMaterialDto>,
    unidadesComerciales: List<UnidadComercialDto>,
    isLocked: Boolean,
    canAddPartida: Boolean,
    canDeletePartida: Boolean,
    jumpPulseSeq: Int,
    onFiltroChange: (String) -> Unit,
    onJump: (Int) -> Unit,
    onValueChange: (PartidaRevisionDto, String, Any) -> Unit,
    onCantidadComercialChange: (PartidaRevisionDto, Double, String) -> Unit,
    onQuickFill: (PartidaRevisionDto, String, String) -> Unit,
    onScan: (String) -> Unit,
    onCapturarEtiqueta: ((PartidaRevisionDto) -> Unit)? = null,
    onTogglePartida: (PartidaRevisionDto) -> Unit,
    onDeletePartida: (Int) -> Unit,
    onAddPartida: () -> Unit
) {
    // Todo el bloque de Partidas vive en su propia "cajita" (fondo + borde
    // distintivo) para que se sienta como una zona aparte de la pantalla:
    // primero revisan partidas, y hasta el final marcan el estado general.
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = PmmNavyVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.5.dp, PmmBlueDeep.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PartidasHeaderSection(
                total = partidas.size,
                revisadas = partidas.count { it.Revisada },
                filtroPartidas = filtroPartidas,
                onFiltroChange = onFiltroChange
            )

            if (partidas.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = PmmBgSurface,
                    border = BorderStroke(1.dp, PmmNavyBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = null,
                            tint = PmmTextMuted,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.revision_no_parts_in_filter),
                            color = PmmTextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                PartidaNavigatorAgrupado(
                    partidas = partidas,
                    activeIndex = activeIndex,
                    onJump = onJump
                )

                // Cuando el salto viene del buscador, un resplandor breve en
                // el borde confirma "aquí está" -- si no, con 100+ partidas
                // el formulario simplemente cambia y no queda claro que sí
                // saltó a donde se pidió.
                var pulsandoSalto by remember { mutableStateOf(false) }
                LaunchedEffect(jumpPulseSeq) {
                    if (jumpPulseSeq > 0) {
                        pulsandoSalto = true
                        delay(700)
                        pulsandoSalto = false
                    }
                }
                val colorPulso by animateColorAsState(
                    targetValue = if (pulsandoSalto) PmmBlueDeep else Color.Transparent,
                    label = "partida-jump-pulse"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, colorPulso, RoundedCornerShape(18.dp))
                        .padding(2.dp)
                ) {
                    AnimatedContent(
                        targetState = activeIndex,
                        transitionSpec = {
                            if (targetState > initialState) {
                                slideInHorizontally { it / 3 } + fadeIn() togetherWith
                                    slideOutHorizontally { -it / 4 } + fadeOut()
                            } else {
                                slideInHorizontally { -it / 3 } + fadeIn() togetherWith
                                    slideOutHorizontally { it / 4 } + fadeOut()
                            }
                        },
                        label = "partida"
                    ) { targetIndex ->
                        partidas.getOrNull(targetIndex - 1)?.let { partida ->
                            PartidaForm(
                                partida = partida,
                                paises = paises,
                                paisesFrecuentes = paisesFrecuentes,
                                tiposMaterial = tiposMaterial,
                                unidadesComerciales = unidadesComerciales,
                                isLocked = isLocked,
                                onValueChange = { field, value ->
                                    onValueChange(partida, field, value)
                                },
                                onCantidadComercialChange = { cantidad, unidad ->
                                    onCantidadComercialChange(partida, cantidad, unidad)
                                },
                                onQuickFill = { field, value ->
                                    onQuickFill(partida, field, value)
                                },
                                onTriggerScan = onScan,
                                onCapturarEtiqueta = onCapturarEtiqueta?.let { cb -> { cb(partida) } },
                                onToggleRevisada = {
                                    onTogglePartida(partida)
                                }
                            )
                        }
                    }
                }

                PartidaActions(
                    canAdd = canAddPartida,
                    canDelete = canDeletePartida,
                    onDelete = {
                        partidas.getOrNull(activeIndex - 1)?.IdRevdet?.let(onDeletePartida)
                    },
                    onAdd = onAddPartida
                )
            }
        }
    }
}

// Color según estado de la partida -- mismo criterio en el chip de número
// (PartidaNavigatorAgrupado) y en el resultado del buscador (ResultadoBusquedaRow):
// verde = revisada, ámbar = tiene algo capturado pero no se marcó revisada,
// gris = todavía sin tocar.
private fun colorEstadoPartida(partida: PartidaRevisionDto): Color = when {
    partida.Revisada -> PmmGreen
    !partida.Marca.isNullOrBlank() ||
        !partida.Modelo.isNullOrBlank() ||
        !partida.NumSer.isNullOrBlank() -> PmmAmber
    else -> PmmTextMuted
}

@Composable
private fun BuscadorPartidaBar(
    query: String,
    resultados: List<ResultadoBusquedaRevision>,
    onQueryChange: (String) -> Unit,
    onResultClick: (ResultadoBusquedaRevision) -> Unit,
    onScan: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var resultadosExpandidos by rememberSaveable { mutableStateOf(false) }

    val resultadosFactura = remember(resultados) {
        resultados.filter { it.tipo == TipoResultadoBusqueda.FACTURA }
    }
    val resultadosProducto = remember(resultados) {
        resultados.filter { it.tipo == TipoResultadoBusqueda.PRODUCTO }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(17.dp)
            )
            Text(
                text = stringResource(R.string.revision_search_all),
                color = PmmBlueDeep,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { value ->
                resultadosExpandidos = value.isNotBlank()
                onQueryChange(value)
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && query.isNotBlank()) {
                        resultadosExpandidos = true
                    }
                },
            singleLine = true,
            label = {
                Text(stringResource(R.string.revision_invoice_product_supplier), fontSize = 12.sp)
            },
            placeholder = {
                Text(stringResource(R.string.revision_search_help), fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        resultadosExpandidos = true
                        onScan()
                    }) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.revision_scan_part_number),
                        )
                    }
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            resultadosExpandidos = false
                            onQueryChange("")
                        }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_search)
                            )
                        }
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    resultados.firstOrNull()?.let {
                        resultadosExpandidos = false
                        onResultClick(it)
                    }
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PmmBlueDeep,
                unfocusedBorderColor = PmmBlueMid.copy(alpha = 0.55f),
                focusedContainerColor = PmmBgSurface,
                unfocusedContainerColor = PmmBgSurface,
                focusedTextColor = PmmTextPrimary,
                unfocusedTextColor = PmmTextPrimary,
                focusedPlaceholderColor = PmmTextSecondary,
                unfocusedPlaceholderColor = PmmTextSecondary,
                focusedLabelColor = PmmBlueDeep,
                unfocusedLabelColor = PmmTextSecondary,
                focusedLeadingIconColor = PmmBlueDeep,
                unfocusedLeadingIconColor = PmmBlueMid,
                focusedTrailingIconColor = PmmBlueDeep,
                unfocusedTrailingIconColor = PmmBlueMid,
                cursorColor = PmmBlueDeep
            ),
            shape = RoundedCornerShape(13.dp)
        )

        AnimatedVisibility(visible = query.isNotBlank() && resultadosExpandidos) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = PmmBgSurface,
                border = BorderStroke(1.dp, PmmNavyBorder),
                shadowElevation = 6.dp
            ) {
                if (resultados.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = PmmTextMuted,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.revision_no_matches_for, query),
                            color = PmmTextSecondary,
                            fontSize = 13.sp
                        )
                        Text(
                            stringResource(R.string.revision_check_case_or_dashes),
                            color = PmmTextMuted,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        if (resultadosFactura.isNotEmpty()) {
                            item(key = "busqueda-header-facturas") {
                                BusquedaResultadosHeader(
                                    title = stringResource(R.string.revision_search_invoices),
                                    count = resultadosFactura.size
                                )
                            }
                            itemsIndexed(
                                items = resultadosFactura,
                                key = { _, resultado -> "factura-${resultado.factura.Cnofact}" }
                            ) { index, resultado ->
                                if (index > 0) HorizontalDivider(color = PmmNavyBorder)
                                ResultadoBusquedaRow(
                                    resultado = resultado,
                                    onClick = {
                                        resultadosExpandidos = false
                                        onResultClick(resultado)
                                    }
                                )
                            }
                        }

                        if (resultadosProducto.isNotEmpty()) {
                            item(key = "busqueda-header-productos") {
                                BusquedaResultadosHeader(
                                    title = stringResource(R.string.revision_search_products),
                                    count = resultadosProducto.size
                                )
                            }
                            itemsIndexed(
                                items = resultadosProducto,
                                key = { _, resultado ->
                                    "producto-${resultado.partida?.IdRevdet ?: resultado.factura.Cnofact}"
                                }
                            ) { index, resultado ->
                                if (index > 0 || resultadosFactura.isNotEmpty()) {
                                    HorizontalDivider(color = PmmNavyBorder)
                                }
                                ResultadoBusquedaRow(
                                    resultado = resultado,
                                    onClick = {
                                        resultadosExpandidos = false
                                        onResultClick(resultado)
                                    }
                                )
                            }
                        }

                        if (resultados.size == 1) {
                            item(key = "busqueda-footer") {
                                Text(
                                    text = stringResource(R.string.revision_unique_match),
                                    color = PmmBlueMid,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PmmNavyVariant)
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BusquedaResultadosHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmNavyVariant)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = PmmBlueDeep,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            color = PmmTextMuted,
            fontSize = 10.5.sp
        )
    }
}

@Composable
private fun ResultadoBusquedaRow(
    resultado: ResultadoBusquedaRevision,
    onClick: () -> Unit
) {
    val partida = resultado.partida
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (resultado.tipo == TipoResultadoBusqueda.PRODUCTO && partida != null) {
                        colorEstadoPartida(partida)
                    } else {
                        PmmBlueDeep
                    }
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (resultado.tipo == TipoResultadoBusqueda.FACTURA) {
                    stringResource(R.string.revision_invoice_found, resultado.factura.Cnofact)
                } else {
                    partida?.CnoParte?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.revision_part_without_number)
                },
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
            Text(
                text = if (resultado.tipo == TipoResultadoBusqueda.FACTURA) {
                    resultado.factura.NombreProveedor?.takeIf(String::isNotBlank)
                        ?.let { stringResource(R.string.revision_supplier_found, it) }
                        ?: stringResource(R.string.revision_invoice_result)
                } else {
                    partida?.cDescripcion ?: stringResource(R.string.revision_without_description)
                },
                color = PmmTextSecondary,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Surface(shape = RoundedCornerShape(999.dp), color = PmmNavyVariant) {
                Text(
                    text = if (resultado.tipo == TipoResultadoBusqueda.FACTURA) {
                        stringResource(R.string.revision_invoices).uppercase()
                    } else {
                        stringResource(R.string.revision_invoice_short, resultado.factura.Cnofact)
                    },
                    color = PmmBlueMid,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            Text(
                text = if (resultado.tipo == TipoResultadoBusqueda.FACTURA) {
                    stringResource(R.string.revision_tap_to_open)
                } else {
                    stringResource(R.string.revision_part_result, resultado.posicion ?: 0)
                },
                color = PmmTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// A partir de cuántas partidas conviene agrupar los botones de navegación en
// rangos, en vez de un botón por partida (ver PartidaNavigatorAgrupado).
private const val PARTIDAS_UMBRAL_GRUPOS_DE_10 = 20
private const val PARTIDAS_UMBRAL_GRUPOS_DE_20 = 60

@Composable
private fun PartidaNavigatorAgrupado(
    partidas: List<PartidaRevisionDto>,
    activeIndex: Int,
    onJump: (Int) -> Unit
) {
    val total = partidas.size
    val groupSize = when {
        total <= PARTIDAS_UMBRAL_GRUPOS_DE_10 -> 1
        total <= PARTIDAS_UMBRAL_GRUPOS_DE_20 -> 10
        else -> 20
    }

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (groupSize == 1) {
            // Pocas partidas: un botón por partida, como siempre.
            // Key por posición (no por IdRevdet): si el API llegara a repetir
            // un IdRevdet, un key duplicado tumba la pantalla completa
            // (IllegalArgumentException de LazyRow); la posición siempre es única.
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(
                    items = partidas,
                    key = { index, _ -> index }
                ) { index, partida ->
                    val number = index + 1
                    NumberChip(
                        number = number,
                        selected = number == activeIndex,
                        statusColor = colorEstadoPartida(partida),
                        onClick = { onJump(number - activeIndex) }
                    )
                }
            }
        } else {
            val groupCount = (total + groupSize - 1) / groupSize
            var expandedGroup by remember(total, groupSize) {
                mutableStateOf((activeIndex - 1).coerceAtLeast(0) / groupSize)
            }
            // Si "Anterior"/"Siguiente" cruza a otro rango, que el rango
            // expandido lo siga (para no perder de vista dónde estás).
            LaunchedEffect(activeIndex, groupSize) {
                val currentGroup = (activeIndex - 1).coerceAtLeast(0) / groupSize
                if (currentGroup != expandedGroup) expandedGroup = currentGroup
            }

            Text(
                text = stringResource(R.string.revision_tap_range),
                color = PmmTextMuted,
                fontSize = 12.sp
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(groupCount) { g ->
                    val start = g * groupSize + 1
                    val end = minOf((g + 1) * groupSize, total)
                    val enRango = partidas.subList(start - 1, end)
                    GroupChip(
                        label = "$start–$end",
                        revisadas = enRango.count { it.Revisada },
                        total = enRango.size,
                        expanded = g == expandedGroup,
                        containsActive = activeIndex in start..end,
                        onClick = { expandedGroup = g }
                    )
                }
            }

            val start = expandedGroup * groupSize + 1
            val end = minOf((expandedGroup + 1) * groupSize, total)
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items((start..end).toList(), key = { it }) { number ->
                    val partida = partidas.getOrNull(number - 1)
                    NumberChip(
                        number = number,
                        selected = number == activeIndex,
                        statusColor = partida?.let { colorEstadoPartida(it) } ?: PmmTextMuted,
                        onClick = { onJump(number - activeIndex) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = { onJump(-1) },
                enabled = activeIndex > 1
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = null
                )
                Text(stringResource(R.string.revision_previous))
            }
            Text(
                text = stringResource(R.string.revision_part_counter, activeIndex, partidas.size),
                color = PmmTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = { onJump(1) },
                enabled = activeIndex < partidas.size
            ) {
                Text(stringResource(R.string.revision_next))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
private fun NumberChip(
    number: Int,
    selected: Boolean,
    statusColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(46.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) PmmBlueDeep else PmmBgSurface,
        border = BorderStroke(
            1.dp,
            if (selected) PmmBlueDeep else statusColor.copy(alpha = 0.45f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = number.toString(),
                color = if (selected) Color.White else statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
private fun GroupChip(
    label: String,
    revisadas: Int,
    total: Int,
    expanded: Boolean,
    containsActive: Boolean,
    onClick: () -> Unit
) {
    val completo = total > 0 && revisadas == total
    val statusColor = when {
        completo -> PmmGreen
        revisadas > 0 -> PmmAmber
        else -> PmmTextMuted
    }
    val selected = expanded || containsActive

    Surface(
        modifier = Modifier
            .widthIn(min = 76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) PmmBlueDeep else PmmBgSurface,
        border = BorderStroke(
            1.dp,
            if (selected) PmmBlueDeep else statusColor.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = if (selected) Color.White else PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = "$revisadas/$total",
                color = if (selected) Color.White.copy(alpha = 0.85f) else statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PartidaActions(
    canAdd: Boolean,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onDelete,
            enabled = canDelete,
            border = BorderStroke(1.dp, PmmRed.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PmmRed
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.revision_delete))
        }
        OutlinedButton(
            onClick = onAdd,
            enabled = canAdd,
            border = BorderStroke(1.dp, PmmBlueDeep.copy(alpha = 0.45f)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = PmmBlueDeep
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(stringResource(R.string.revision_add_part))
        }
    }
}

@Composable
private fun FacturaCarrusel(
    facturasCrudas: List<FacturaRevisionDto>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val facturas = facturasCrudas.dedupedByCnofact("FacturaCarrusel")
    val invoiceListState = rememberLazyListState()

    // La búsqueda global puede cambiar la factura activa a una que está fuera
    // del área visible. Mantener el carrusel sincronizado evita que el usuario
    // tenga que hacer un desplazamiento horizontal adicional para encontrarla.
    LaunchedEffect(selectedIndex, facturas.size) {
        if (selectedIndex in facturas.indices) {
            invoiceListState.animateScrollToItem(selectedIndex)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Description, null, modifier = Modifier.size(16.dp), tint = PmmYellow)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.revision_invoices),
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "(${facturas.size})",
                color = PmmTextSecondary,
                fontSize = 16.sp
            )
        }
        LazyRow(
            state = invoiceListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(facturas.withIndex().toList(), key = { indexed -> indexed.value.Cnofact }) { indexed ->
                val index = indexed.index
                val factura = indexed.value
                val isSelected   = index == selectedIndex
                val isRevisada   = factura.StatusRev == 1
                val isDiferencia = factura.StatusRev == 2
                val isProceso    = factura.StatusRev == 3

                Surface(
                    modifier = Modifier
                        .width(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelect(index) },
                    shape  = RoundedCornerShape(14.dp),
                    color  = if (isSelected) PmmBlue else PmmNavyCard,
                    border = BorderStroke(
                        1.5.dp,
                        when {
                            isSelected   -> PmmBlueLight
                            isDiferencia -> PmmRed.copy(alpha = 0.5f)
                            isRevisada   -> PmmGreen.copy(alpha = 0.5f)
                            isProceso    -> PmmAmber.copy(alpha = 0.5f)
                            else         -> PmmNavyBorder
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text         = factura.Cnofact,
                                color        = if (isSelected) Color.White else PmmTextPrimary,
                                fontWeight   = FontWeight.Bold,
                                fontSize     = 15.sp,
                                modifier     = Modifier.weight(1f),
                                overflow     = TextOverflow.Ellipsis,
                                maxLines     = 1
                            )
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isDiferencia -> PmmRed
                                            isRevisada   -> PmmGreen
                                            isProceso    -> PmmAmber
                                            else         -> PmmTextMuted
                                        }
                                    )
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text     = factura.NombreProveedor ?: "–",
                            color    = if (isSelected) Color.White.copy(alpha = 0.8f) else PmmTextSecondary,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Inventory2, null,
                                modifier = Modifier.size(11.dp),
                                tint = if (isSelected) PmmYellow else PmmTextMuted
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${factura.TotBultos} bts",
                                color    = if (isSelected) PmmYellow else PmmTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
        if (facturas.isEmpty()) {
            Text(
                text = stringResource(R.string.revision_no_matching_invoices),
                color = PmmTextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun FacturaDetalle(
    factura: FacturaRevisionDto,
    isLocked: Boolean,
    onOpenPdf: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                            text = stringResource(R.string.revision_invoice_found, factura.Cnofact),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        factura.dFecha,
                        factura.Cpedido?.takeIf { it.isNotBlank() }
                            ?.let { stringResource(R.string.revision_order, it) }
                    ).joinToString(" · "),
                    color = PmmTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isLocked) {
                Surface(
                    color = Color(0xFFFFF7D6),
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 9.dp,
                            vertical = 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PmmAmberDark,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.revision_read_only),
                            color = PmmAmberDark,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SectionHeading(
                    icon = Icons.Default.Description,
                    title = stringResource(R.string.revision_document),
                    subtitle = stringResource(R.string.revision_declared_invoice_info)
                )
                Button(
                    onClick = onOpenPdf,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmBlueDeep,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.revision_open_pdf), fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DataField(
                        label = stringResource(R.string.revision_supplier),
                        value = factura.NombreProveedor ?: stringResource(R.string.common_no_data),
                        modifier = Modifier.weight(1f)
                    )
                    DataField(
                        label = stringResource(R.string.revision_customer),
                        value = factura.NombreCliente ?: stringResource(R.string.common_no_data),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    DataField(
                        label = stringResource(R.string.revision_document_packages),
                        value = factura.TotBultosDoc.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    DataField(
                        label = stringResource(R.string.revision_document_weight),
                        value = "${String.format(Locale.US, "%.2f", factura.nPesoKgs)} kg",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun InspeccionFisicaCard(
    factura: FacturaRevisionDto,
    isLocked: Boolean,
    onEditCampo: (String, Any) -> Unit
) {
    var localPesoLbs by remember(factura.Cnofact, factura.nPesoLbs) { mutableStateOf(factura.nPesoLbs.toString()) }
    var localPesoKgs by remember(factura.Cnofact, factura.nPesoKgs) { mutableStateOf(factura.nPesoKgs.toString()) }
    var localBultos  by remember(factura.Cnofact, factura.TotBultos) { mutableStateOf(factura.TotBultos.toString()) }
    var localBultosDoc by remember(factura.Cnofact, factura.TotBultosDoc) { mutableStateOf(factura.TotBultosDoc.toString()) }
    var localMarcas  by remember(factura.Cnofact) { mutableStateOf(factura.Marcas ?: "") }
    var localNumeros by remember(factura.Cnofact) { mutableStateOf(factura.Numeros ?: "") }
    var localObs     by remember(factura.Cnofact) { mutableStateOf(factura.Obsrev ?: "") }

    // Reflejo en tiempo real entre libras y kilos, replicando la misma regla
    // que GuardarCampoFacturaAsync aplica en el API (idéntica a
    // frmopera_factura.SCX en VFP): libras siempre recalcula kilos, pero
    // kilos solo recalcula libras cuando libras seguía en cero -- así no se
    // pisa el peso oficial del documento con una captura física en kilos.
    // El guardado real sigue ocurriendo solo en onCommit; esto es un espejo
    // visual que refreshFactura() termina confirmando (o corrigiendo) tras
    // guardar.
    fun onPesoLbsChanged(nuevoValor: String) {
        localPesoLbs = nuevoValor
        nuevoValor.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { libras ->
            localPesoKgs = String.format(Locale.US, "%.2f", libras * 0.4536)
        }
    }

    fun onPesoKgsChanged(nuevoValor: String) {
        localPesoKgs = nuevoValor
        val librasActuales = localPesoLbs.toDoubleOrNull() ?: 0.0
        if (librasActuales == 0.0) {
            nuevoValor.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { kilos ->
                localPesoLbs = String.format(Locale.US, "%.2f", kilos / 0.4536)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionHeading(
                icon = Icons.Default.Inventory2,
                title = stringResource(R.string.revision_physical_inspection),
                subtitle = stringResource(R.string.revision_warehouse_capture)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val horizontalFields = maxWidth >= 520.dp
                if (horizontalFields) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RevisionInput(
                            label = stringResource(R.string.revision_weight_lb),
                            value = localPesoLbs,
                            onValueChange = ::onPesoLbsChanged,
                            onCommit = {
                                onEditCampo(
                                    "npesolbs",
                                    localPesoLbs.toDoubleOrNull() ?: 0.0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_weight_kg),
                            value = localPesoKgs,
                            onValueChange = ::onPesoKgsChanged,
                            onCommit = {
                                onEditCampo(
                                    "npesokgs",
                                    localPesoKgs.toDoubleOrNull() ?: 0.0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                            modifier = Modifier.weight(1f)
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_physical_packages),
                            value = localBultos,
                            onValueChange = { localBultos = it },
                            onCommit = {
                                onEditCampo(
                                    "totbultos",
                                    localBultos.toIntOrNull() ?: 0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_document_packages),
                            value = localBultosDoc,
                            onValueChange = { localBultosDoc = it },
                            onCommit = {
                                onEditCampo(
                                    "totbultosdoc",
                                    localBultosDoc.toIntOrNull() ?: 0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RevisionInput(
                                label = stringResource(R.string.revision_weight_lb),
                                value = localPesoLbs,
                                onValueChange = ::onPesoLbsChanged,
                                onCommit = {
                                    onEditCampo(
                                        "npesolbs",
                                        localPesoLbs.toDoubleOrNull() ?: 0.0
                                    )
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            RevisionInput(
                                label = stringResource(R.string.revision_weight_kg),
                                value = localPesoKgs,
                                onValueChange = ::onPesoKgsChanged,
                                onCommit = {
                                    onEditCampo(
                                        "npesokgs",
                                        localPesoKgs.toDoubleOrNull() ?: 0.0
                                    )
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        RevisionInput(
                            label = stringResource(R.string.revision_physical_packages),
                            value = localBultos,
                            onValueChange = { localBultos = it },
                            onCommit = {
                                onEditCampo(
                                    "totbultos",
                                    localBultos.toIntOrNull() ?: 0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth()
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_document_packages),
                            value = localBultosDoc,
                            onValueChange = { localBultosDoc = it },
                            onCommit = {
                                onEditCampo(
                                    "totbultosdoc",
                                    localBultosDoc.toIntOrNull() ?: 0
                                )
                            },
                            enabled = !isLocked,
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                if (maxWidth >= 440.dp) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RevisionInput(
                            label = stringResource(R.string.revision_marks),
                            value = localMarcas,
                            onValueChange = { localMarcas = it },
                            onCommit = {
                                onEditCampo("marcas", localMarcas.trim())
                            },
                            enabled = !isLocked,
                            quickLabel = "S/M",
                            onQuickFill = {
                                localMarcas = "S/M"
                                onEditCampo("marcas", "S/M")
                            },
                            modifier = Modifier.weight(1f)
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_numbers),
                            value = localNumeros,
                            onValueChange = { localNumeros = it },
                            onCommit = {
                                onEditCampo("numeros", localNumeros.trim())
                            },
                            enabled = !isLocked,
                            quickLabel = "S/N",
                            onQuickFill = {
                                localNumeros = "S/N"
                                onEditCampo("numeros", "S/N")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RevisionInput(
                            label = stringResource(R.string.revision_marks),
                            value = localMarcas,
                            onValueChange = { localMarcas = it },
                            onCommit = {
                                onEditCampo("marcas", localMarcas.trim())
                            },
                            enabled = !isLocked,
                            quickLabel = "S/M",
                            onQuickFill = {
                                localMarcas = "S/M"
                                onEditCampo("marcas", "S/M")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        RevisionInput(
                            label = stringResource(R.string.revision_numbers),
                            value = localNumeros,
                            onValueChange = { localNumeros = it },
                            onCommit = {
                                onEditCampo("numeros", localNumeros.trim())
                            },
                            enabled = !isLocked,
                            quickLabel = "S/N",
                            onQuickFill = {
                                localNumeros = "S/N"
                                onEditCampo("numeros", "S/N")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            RevisionInput(
                label = stringResource(R.string.revision_review_notes),
                value = localObs,
                onValueChange = { localObs = it },
                onCommit = {
                    onEditCampo("obsrev", localObs.trim())
                },
                enabled = !isLocked,
                minLines = 3,
                allowNewLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DiscrepanciaInfoBanners(factura: FacturaRevisionDto) {
    if (factura.Discrepancias.isNullOrBlank() && factura.Obsrev2.isNullOrBlank()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!factura.Discrepancias.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF1F2),
                border = BorderStroke(1.dp, PmmRed.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.ReportProblem,
                        contentDescription = null,
                        tint = PmmRedDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.revision_discrepancy_registered),
                            color = PmmRedDark,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = factura.Discrepancias,
                            color = PmmTextSecondary,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        if (!factura.Obsrev2.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = PmmNavyVariant
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(R.string.revision_clarification_history),
                        color = PmmBlueDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = factura.Obsrev2,
                        color = PmmTextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/** Nota única por entrada (no por factura) -- vive en traf3.observacion_revision.
 * Se guarda al perder foco, igual que "Observaciones de revisión" por factura. */
@Composable
private fun ObservacionGeneralCard(
    valor: String,
    isLocked: Boolean,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.5.dp, PmmAmber.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.revision_general_observation_title),
                    color = PmmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.revision_general_observation_subtitle),
                    color = PmmTextMuted,
                    fontSize = 13.sp
                )
            }
            RevisionInput(
                label = stringResource(R.string.revision_general_observation),
                value = valor,
                onValueChange = onValueChange,
                onCommit = onCommit,
                enabled = !isLocked,
                minLines = 3,
                allowNewLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun EstadoRevisionCard(
    factura: FacturaRevisionDto,
    isLocked: Boolean,
    onSetStatus: (Int) -> Unit,
    onRegistrarDiscrepancia: () -> Unit
) {
    val isRevisada   = factura.StatusRev == 1
    val isDiferencia = factura.StatusRev == 2
    val isProceso    = factura.StatusRev == 3

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.5.dp, PmmBlueDeep.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.revision_status_title),
                    color = PmmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.revision_status_subtitle),
                    color = PmmTextMuted,
                    fontSize = 13.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusButton(
                    label = stringResource(R.string.revision_status_in_progress),
                    color = PmmAmber,
                    isSelected = isProceso,
                    modifier = Modifier.weight(1f),
                    enabled = !isLocked
                ) {
                    onSetStatus(3)
                }
                StatusButton(
                    label = stringResource(R.string.revision_status_complete),
                    color = PmmGreen,
                    isSelected = isRevisada,
                    modifier = Modifier.weight(1f),
                    enabled = !isLocked
                ) {
                    onSetStatus(1)
                }
                StatusButton(
                    label = stringResource(R.string.revision_status_discrepancy),
                    color = PmmRed,
                    isSelected = isDiferencia,
                    modifier = Modifier.weight(1f),
                    enabled = !isLocked
                ) {
                    // Único punto de entrada para discrepancias: abre el modal que
                    // exige descripción antes de marcar status_rev=2, para no dejar
                    // nunca una discrepancia sin motivo capturado. (El botón separado
                    // "Registrar discrepancia" se quitó por ser redundante con este.)
                    onRegistrarDiscrepancia()
                }
            }
        }
    }
}

@Composable
private fun EvidenciaDiscrepanciaButton(
    isLocked: Boolean,
    canAddPhoto: Boolean,
    cantidadFotos: Int,
    onClick: () -> Unit
) {
    val tieneEvidencia = cantidadFotos > 0
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(enabled = !isLocked && canAddPhoto, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = if (tieneEvidencia) Color(0xFFFFF1F2) else PmmBgSurface,
        border = BorderStroke(
            1.5.dp,
            if (tieneEvidencia) PmmRed.copy(alpha = 0.55f) else PmmRed.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(PmmRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = PmmRedDark,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.revision_discrepancy_evidence),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (tieneEvidencia) {
                        stringResource(R.string.revision_photos_saved, cantidadFotos)
                    } else {
                        stringResource(R.string.revision_photo_problem)
                    },
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (tieneEvidencia) {
                Surface(color = PmmRed, shape = CircleShape) {
                    Text(
                        text = cantidadFotos.coerceAtMost(99).toString(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = PmmRedDark
            )
        }
    }
}

@Composable
private fun EditorActivoBadge(editor: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PmmNavyVariant)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = PmmBlueDeep,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = stringResource(R.string.revision_editor_active, editor),
            color = PmmBlueDeep,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SectionHeading(
    icon: ImageVector,
    title: String,
    subtitle: String? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PmmNavyVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun RevisionInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    minLines: Int = 1,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    // Campos de observaciones necesitan Enter = salto de línea real, no
    // "cerrar teclado". El guardado sigue ocurriendo al perder foco.
    allowNewLine: Boolean = false,
    // Botón de auto-relleno tipo "S/M"/"S/N", igual que en partidas.
    quickLabel: String? = null,
    onQuickFill: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val composeView = LocalView.current
    // onFocusChanged reporta isFocused=false también la PRIMERA vez que el
    // campo entra en composición (no solo cuando de verdad pierde el foco) --
    // y como este campo vive dentro de un item de LazyColumn, un simple scroll
    // que lo saca y lo vuelve a meter en pantalla lo recompone de cero. Sin
    // este guardado, cada una de esas recomposiciones disparaba onCommit()
    // como si el usuario hubiera editado y salido del campo. Solo confirmamos
    // que hubo una transición real enfocado -> no enfocado.
    var wasFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled || readOnly,
            readOnly = readOnly,
            minLines = minLines,
            singleLine = minLines == 1 && !allowNewLine,
            label = {
                Text(
                    text = label,
                    fontSize = 14.sp
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = if (allowNewLine) {
                    androidx.compose.ui.text.input.ImeAction.None
                } else {
                    androidx.compose.ui.text.input.ImeAction.Done
                }
            ),
            keyboardActions = if (allowNewLine) {
                KeyboardActions.Default
            } else {
                KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        // El commit real ocurre en onFocusChanged: clearFocus()
                        // dispara esa transición enfocado -> no enfocado, y
                        // llamar onCommit() aquí también duplicaba el guardado
                        // (dos "Campo actualizado" idénticos por cada Done).
                        focusManager.clearFocus()
                    }
                )
            },
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PmmBlueDeep,
                unfocusedBorderColor = PmmNavyBorder,
                disabledBorderColor = PmmNavyBorder,
                focusedTextColor = PmmTextPrimary,
                unfocusedTextColor = PmmTextPrimary,
                disabledTextColor = PmmTextSecondary,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledContainerColor = PmmNavyCard,
                cursorColor = PmmBlueDeep
            ),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && enabled && !readOnly) {
                        // Algunos equipos de bodega reportan un teclado físico
                        // aunque el usuario capture todo con la pantalla táctil.
                        // En esos equipos Android no muestra el IME con la
                        // solicitud implícita de Compose; forzamos la solicitud
                        // después de que el foco quedó aplicado.
                        keyboardController?.show()
                        composeView.post {
                            forceShowSoftwareKeyboard(composeView)
                        }
                    }
                    if (wasFocused && !focusState.isFocused && !readOnly) {
                        onCommit()
                    }
                    wasFocused = focusState.isFocused
                }
        )
        quickLabel?.let { labelText ->
            OutlinedButton(
                onClick = { onQuickFill?.invoke() },
                enabled = enabled,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp),
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Text(
                    text = labelText,
                    color = PmmBlueDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun forceShowSoftwareKeyboard(view: View) {
    val keyboard = view.context.getSystemService(Context.INPUT_METHOD_SERVICE)
        as? InputMethodManager
    keyboard?.showSoftInput(view, InputMethodManager.SHOW_FORCED)

    // MainActivity usa edge-to-edge; pedir el IME por insets cubre los
    // firmwares que ignoran la llamada directa del InputMethodManager.
    (view.context as? android.app.Activity)?.window?.let { window ->
        WindowCompat.getInsetsController(window, view)
            .show(WindowInsetsCompat.Type.ime())
    }
}

@Composable
private fun StatusButton(
    label: String,
    color: Color,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    // Antes: seleccionado vs. no-seleccionado solo se distinguía por un tinte
    // de fondo al 18% y un borde más o menos opaco -- en luz de bodega, a
    // simple vista, se veían igual. Ahora el seleccionado es RELLENO SÓLIDO
    // con texto blanco y un check, no un matiz de borde: la diferencia se ve
    // sin tener que comparar los tres botones uno junto a otro.
    val bgColor = when {
        isSelected -> color
        !enabled -> PmmNavyCard.copy(alpha = 0.5f)
        else -> PmmBgSurface
    }
    val borderColor = when {
        isSelected -> color
        !enabled -> PmmNavyBorder.copy(alpha = 0.4f)
        else -> color.copy(alpha = 0.4f)
    }
    val textColor = when {
        isSelected -> Color.White
        !enabled -> PmmTextMuted.copy(alpha = 0.4f)
        else -> PmmTextSecondary
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .then(
                if (isSelected) {
                    Modifier.shadow(4.dp, RoundedCornerShape(8.dp), spotColor = color, ambientColor = color)
                } else {
                    Modifier
                }
            )
            .border(if (isSelected) 0.dp else 1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 3.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                color      = textColor,
                fontSize   = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                textAlign  = TextAlign.Center,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DataField(label: String, value: String, modifier: Modifier = Modifier, labelColor: Color = PmmTextMuted) {
    Column(modifier = modifier) {
        Text(label.uppercase(), color = labelColor, fontSize = 12.sp, letterSpacing = 0.5.sp)
        Text(value, color = PmmTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PartidasHeaderSection(
    total: Int,
    revisadas: Int,
    filtroPartidas: String,
    onFiltroChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null, modifier = Modifier.size(16.dp), tint = PmmYellow)
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.revision_parts_title),
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
            Spacer(Modifier.width(6.dp))
            Text("($revisadas/$total)", color = PmmTextSecondary, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            val progress = if (total > 0) revisadas.toFloat() / total else 0f
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(PmmNavyBorder)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (progress >= 1f) PmmGreen else PmmBlueLight)
                )
            }
        }
        
        // Filter tabs / chips (Todas, Pendientes, Revisadas)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val filters = listOf(
                "todas" to stringResource(R.string.assignments_filter_all),
                "pendientes" to stringResource(R.string.assignments_filter_pending),
                "revisadas" to stringResource(R.string.revision_filter_reviewed)
            )
            filters.forEach { (key, title) ->
                val active = filtroPartidas == key
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) PmmBlue else PmmNavyCard)
                        .border(1.dp, if (active) PmmBlueLight else PmmNavyBorder, RoundedCornerShape(8.dp))
                        .clickable { onFiltroChange(key) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title,
                        color = if (active) Color.White else PmmTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// Clientes con la regla legacy de conversión de peso en libras -> ncanttarifa
// (factor 0.4536), heredada del formulario FoxPro original ("&&GGD 14-08-17").
// La unidad persistida de npeso sigue siendo libra; la captura en kilos se
// convierte antes de enviar el valor al API.
private val CLIENTES_PESO_LIBRAS = setOf("2944", "CL7168", "CL7480", "CL7481", "CL7482")

private fun unidadVisibleDePartida(
    partida: PartidaRevisionDto,
    opciones: List<UnidadComercialDto>
): String {
    val unidadFactura = partida.UnidadFactura.orEmpty().trim()
    if (unidadFactura.isNotEmpty()) return unidadFactura

    val unidadInterna = partida.UnidadComercial.orEmpty().trim()
    return opciones.firstOrNull {
        it.codigoInterno.equals(unidadInterna, ignoreCase = true)
    }?.codigo ?: unidadInterna
}

@Composable
private fun PartidaForm(
    partida: PartidaRevisionDto,
    paises: List<PaisDto>,
    paisesFrecuentes: List<PaisFrecuenteDto>,
    tiposMaterial: List<TipoMaterialDto>,
    unidadesComerciales: List<UnidadComercialDto>,
    isLocked: Boolean,
    onValueChange: (String, Any) -> Unit,
    onCantidadComercialChange: (Double, String) -> Unit,
    onQuickFill: (String, String) -> Unit,
    onTriggerScan: (String) -> Unit,
    onCapturarEtiqueta: (() -> Unit)? = null,
    onToggleRevisada: () -> Unit
) {
    var localMarca by remember(partida.IdRevdet) { mutableStateOf(partida.Marca ?: "") }
    var localModelo by remember(partida.IdRevdet) { mutableStateOf(partida.Modelo ?: "") }
    var localNumSer by remember(partida.IdRevdet) { mutableStateOf(partida.NumSer ?: "") }
    var localNumLote by remember(partida.IdRevdet) { mutableStateOf(partida.NumLote ?: "") }
    var localTipoMaterial by remember(partida.IdRevdet) { mutableStateOf(partida.TipoMaterial.orEmpty()) }
    var localCantOut by remember(partida.IdRevdet) { mutableStateOf(partida.nCantOut?.let { formatQuantity(it) } ?: "0") }
    var localCantidadComercial by remember(partida.IdRevdet) {
        mutableStateOf(partida.CantidadComercial?.let { formatQuantity(it) } ?: "0")
    }
    var localUnidadComercial by remember(partida.IdRevdet) {
        mutableStateOf(unidadVisibleDePartida(partida, unidadesComerciales))
    }
    var localObsPartida by remember(partida.IdRevdet) { mutableStateOf(partida.Obsrevpartida ?: "") }
    var localNotaPartida by remember(partida.IdRevdet) { mutableStateOf(partida.Mnotapartida ?: "") }
    var localPeso by remember(partida.IdRevdet) { mutableStateOf(partida.nPeso?.let { formatQuantity(it) } ?: "0") }
    // OJO: la key NO lleva partida.nPeso (a diferencia de como se veía antes).
    // commitPesoKgs() reescribe npeso al convertir kg->lb, así que si nPeso
    // estuviera en la key, Compose recreaba este estado en cada commit y el
    // campo de kg perdía la sesión del teclado justo al intentar enfocarlo
    // (el teclado nunca abría, aunque el borde de foco sí se veía). localPeso
    // (arriba) nunca tuvo este problema por estar bien, solo con IdRevdet.
    var localPesoKgs by remember(partida.IdRevdet) {
        mutableStateOf(
            String.format(
                Locale.US,
                "%.2f",
                RevisionWeightRules.kilosFromLibras(partida.nPeso ?: 0.0)
            )
        )
    }
    var localPesoLibras by remember(partida.IdRevdet) { mutableStateOf("") }
    var showBatchCapture by remember(partida.IdRevdet) { mutableStateOf(false) }
    var selectedCountryCode by remember(partida.IdRevdet) {
        mutableStateOf(partida.cPaisOrigen.orEmpty())
    }

    // La lectura del escáner actualiza la partida en el ViewModel. Estos
    // efectos mantienen visibles esos cambios sin interrumpir la edición
    // manual mientras el usuario todavía tiene el campo enfocado.
    LaunchedEffect(partida.IdRevdet, partida.Marca) {
        localMarca = partida.Marca.orEmpty()
    }
    LaunchedEffect(partida.IdRevdet, partida.Modelo) {
        localModelo = partida.Modelo.orEmpty()
    }
    LaunchedEffect(partida.IdRevdet, partida.NumSer) {
        localNumSer = partida.NumSer.orEmpty()
    }
    LaunchedEffect(partida.IdRevdet, partida.NumLote) {
        localNumLote = partida.NumLote.orEmpty()
    }
    LaunchedEffect(partida.IdRevdet, partida.TipoMaterial) {
        localTipoMaterial = partida.TipoMaterial.orEmpty()
    }
    LaunchedEffect(partida.IdRevdet, partida.CantidadComercial) {
        localCantidadComercial = partida.CantidadComercial?.let { formatQuantity(it) } ?: "0"
    }
    LaunchedEffect(
        partida.IdRevdet,
        partida.UnidadFactura,
        partida.UnidadComercial,
        unidadesComerciales
    ) {
        localUnidadComercial = unidadVisibleDePartida(partida, unidadesComerciales)
    }

    // Cuando la unidad comercial (cunidadfactura) es KG, la cantidad comercial
    // capturada YA es el peso en kilos -- se replica a "Peso (kg)" y de ahí se
    // deriva libras con la misma regla de RevisionWeightRules que usa el resto
    // de la pantalla. npeso sigue siendo la fuente real en libras; el campo
    // queda tan editable como siempre, esto solo precarga un valor razonable.
    fun sincronizarPesoSiUnidadKilos(cantidadTexto: String, codigoUnidadComercial: String) {
        if (!codigoUnidadComercial.trim().equals("KG", ignoreCase = true)) return
        val kilos = cantidadTexto.toDoubleOrNull() ?: return
        localPesoKgs = String.format(Locale.US, "%.2f", kilos)
        val libras = RevisionWeightRules.librasFromKilos(kilos)
        localPeso = formatQuantity(libras)
        onValueChange("npeso", libras)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (partida.Revisada) {
                Color(0xFFF0FDF4)
            } else {
                PmmBgSurface
            },
            border = BorderStroke(
                1.dp,
                if (partida.Revisada) {
                    PmmGreen.copy(alpha = 0.4f)
                } else {
                    PmmNavyBorder
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partida.cDescripcion ?: stringResource(R.string.revision_without_description),
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(
                            R.string.revision_part_number,
                            partida.CnoParte ?: stringResource(R.string.common_no_data)
                        ),
                        color = PmmTextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = PmmNavyVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.revision_document_quantity,
                                formatQuantity(partida.nCantFactura)
                            ),
                            color = PmmBlueDeep,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(
                                horizontal = 9.dp,
                                vertical = 5.dp
                            )
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = stringResource(
                            if (partida.Revisada) R.string.revision_part_reviewed
                            else R.string.assignments_status_pending
                        ),
                        color = if (partida.Revisada) PmmGreen else PmmAmber,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeading(
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    title = stringResource(R.string.revision_parts_identification),
                    subtitle = stringResource(R.string.revision_parts_identification_subtitle)
                )

                TipoMaterialSelector(
                    selectedCode = localTipoMaterial,
                    options = tiposMaterial,
                    enabled = !isLocked,
                    onSelect = { option ->
                        localTipoMaterial = option.codigo.orEmpty()
                        onValueChange("tipo_material", localTipoMaterial)
                    }
                )

                PartidaEditableField(
                    label = stringResource(R.string.revision_brand),
                    value = localMarca,
                    onValueChange = { localMarca = it },
                    onCommit = {
                        onValueChange("marca", localMarca.trim())
                    },
                    enabled = !isLocked,
                    quickLabel = "S/M",
                    onQuickFill = {
                        localMarca = "S/M"
                        onQuickFill("marca", "S/M")
                    },
                    onScan = { onTriggerScan("marca") }
                )

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 620.dp) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PartidaEditableField(
                                label = stringResource(R.string.revision_model),
                                value = localModelo,
                                onValueChange = { localModelo = it },
                                onCommit = {
                                    onValueChange("modelo", localModelo.trim())
                                },
                                enabled = !isLocked,
                                quickLabel = "S/M",
                                onQuickFill = {
                                    localModelo = "S/M"
                                    onQuickFill("modelo", "S/M")
                                },
                                onScan = { onTriggerScan("modelo") },
                                modifier = Modifier.weight(1f)
                            )
                            PartidaEditableField(
                                label = stringResource(R.string.revision_series_or_lot),
                                value = localNumSer,
                                onValueChange = { localNumSer = it },
                                onCommit = {
                                    onValueChange("numSer", localNumSer.trim())
                                },
                                enabled = !isLocked,
                                quickLabel = "S/N",
                                onQuickFill = {
                                    localNumSer = "S/N"
                                    onQuickFill("numSer", "S/N")
                                },
                                onScan = { onTriggerScan("numSer") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PartidaEditableField(
                                label = stringResource(R.string.revision_model),
                                value = localModelo,
                                onValueChange = { localModelo = it },
                                onCommit = {
                                    onValueChange("modelo", localModelo.trim())
                                },
                                enabled = !isLocked,
                                quickLabel = "S/M",
                                onQuickFill = {
                                    localModelo = "S/M"
                                    onQuickFill("modelo", "S/M")
                                },
                                onScan = { onTriggerScan("modelo") }
                            )
                            PartidaEditableField(
                                label = stringResource(R.string.revision_series_or_lot),
                                value = localNumSer,
                                onValueChange = { localNumSer = it },
                                onCommit = {
                                    onValueChange("numSer", localNumSer.trim())
                                },
                                enabled = !isLocked,
                                quickLabel = "S/N",
                                onQuickFill = {
                                    localNumSer = "S/N"
                                    onQuickFill("numSer", "S/N")
                                },
                                onScan = { onTriggerScan("numSer") }
                            )
                        }
                    }
                }

                RevisionInput(
                    label = stringResource(R.string.revision_lot_number),
                    value = localNumLote,
                    onValueChange = { localNumLote = it },
                    onCommit = {
                        onValueChange("numLote", localNumLote.trim())
                    },
                    enabled = !isLocked,
                    modifier = Modifier.fillMaxWidth()
                )

                // Captura por camara: una foto de la etiqueta llena lote y serie.
                // El dato ya viene impreso; esto solo evita teclearlo. Ver
                // docs/ia-revision-fisica.md.
                if (onCapturarEtiqueta != null && !isLocked) {
                    OutlinedButton(
                        onClick = onCapturarEtiqueta,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.revision_read_label_photo))
                    }
                }

                // Solo tiene sentido cuando hay más de 1 pieza que capturar:
                // para 1 sola serie ya alcanza con el campo de arriba.
                // Siempre en su propia fila (nunca junto a Modelo/Serie o lote)
                // para no apretar esos campos en pantallas de ~6".
                if (partida.nCantFactura > 1) {
                    BatchSeriesCaptureButton(
                        enabled = !isLocked,
                        onClick = { showBatchCapture = true }
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontal = maxWidth >= 520.dp
                    if (horizontal) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RevisionInput(
                                label = stringResource(R.string.revision_total_pieces),
                                value = localCantOut,
                                onValueChange = { localCantOut = it },
                                onCommit = {
                                    onValueChange(
                                        "nCantOut",
                                        localCantOut.toDoubleOrNull() ?: 0.0
                                    )
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            CountrySearchField(
                                selectedCode = selectedCountryCode,
                                countries = paises,
                                frequentCountries = paisesFrecuentes,
                                enabled = !isLocked,
                                onSelect = { code ->
                                    selectedCountryCode = code
                                    onValueChange("cPaisOrigen", code)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RevisionInput(
                                label = stringResource(R.string.revision_total_pieces),
                                value = localCantOut,
                                onValueChange = { localCantOut = it },
                                onCommit = {
                                    onValueChange(
                                        "nCantOut",
                                        localCantOut.toDoubleOrNull() ?: 0.0
                                    )
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.fillMaxWidth()
                            )
                            CountrySearchField(
                                selectedCode = selectedCountryCode,
                                countries = paises,
                                frequentCountries = paisesFrecuentes,
                                enabled = !isLocked,
                                onSelect = { code ->
                                    selectedCountryCode = code
                                    onValueChange("cPaisOrigen", code)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                SectionHeading(
                    icon = Icons.Default.Straighten,
                    title = stringResource(R.string.revision_commercial_quantity_title)
                )

                // Aviso de la regla legacy JCAA 2025-07-24 (frmclasdet.SCT, Combo17.Valid):
                // cuando la UMC declarada ante aduana es GLL (galones), el sistema fuerza
                // la unidad comercial a litros y convierte la cantidad x3.7853. Sin este
                // aviso el revisor ve "litros" sin saber por qué, aunque él solo tenga el
                // dato en galones (caso real reportado).
                val esConversionGalonesALitros = partida.UnidadComercial.orEmpty().trim()
                    .equals("GLL", ignoreCase = true) &&
                    localUnidadComercial.trim().equals("L", ignoreCase = true)
                if (esConversionGalonesALitros) {
                    Surface(
                        color = Color(0xFFFFF7D6),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PmmAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = PmmAmberDark,
                                modifier = Modifier
                                    .size(18.dp)
                                    .padding(top = 1.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.revision_gallons_liters_notice),
                                color = PmmAmberDark,
                                fontSize = 13.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontal = maxWidth >= 520.dp
                    if (horizontal) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            RevisionInput(
                                label = stringResource(R.string.revision_commercial_quantity),
                                value = localCantidadComercial,
                                onValueChange = { localCantidadComercial = it },
                                onCommit = {
                                    onCantidadComercialChange(
                                        localCantidadComercial.toDoubleOrNull() ?: 0.0,
                                        localUnidadComercial
                                    )
                                    sincronizarPesoSiUnidadKilos(localCantidadComercial, localUnidadComercial)
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.weight(0.9f)
                            )
                            UnidadComercialSelector(
                                selectedCode = localUnidadComercial,
                                options = unidadesComerciales,
                                enabled = !isLocked,
                                onSelect = { option ->
                                    localUnidadComercial = option.codigo
                                    onCantidadComercialChange(
                                        localCantidadComercial.toDoubleOrNull() ?: 0.0,
                                        option.codigo
                                    )
                                    sincronizarPesoSiUnidadKilos(localCantidadComercial, option.codigo)
                                },
                                modifier = Modifier.weight(1.1f)
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            RevisionInput(
                                label = stringResource(R.string.revision_commercial_quantity),
                                value = localCantidadComercial,
                                onValueChange = { localCantidadComercial = it },
                                onCommit = {
                                    onCantidadComercialChange(
                                        localCantidadComercial.toDoubleOrNull() ?: 0.0,
                                        localUnidadComercial
                                    )
                                    sincronizarPesoSiUnidadKilos(localCantidadComercial, localUnidadComercial)
                                },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.fillMaxWidth()
                            )
                            UnidadComercialSelector(
                                selectedCode = localUnidadComercial,
                                options = unidadesComerciales,
                                enabled = !isLocked,
                                onSelect = { option ->
                                    localUnidadComercial = option.codigo
                                    onCantidadComercialChange(
                                        localCantidadComercial.toDoubleOrNull() ?: 0.0,
                                        option.codigo
                                    )
                                    sincronizarPesoSiUnidadKilos(localCantidadComercial, option.codigo)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                val esClientePesoLibras = CLIENTES_PESO_LIBRAS.contains(
                    partida.Cclienteid?.trim().orEmpty()
                )
                // Reflejo en tiempo real entre libras y kilos: cada tecleo en
                // un campo recalcula el otro de inmediato (sin esperar a que
                // pierda el foco). npeso sigue siendo la única fuente real --
                // kilos es puramente derivado en esta pantalla -- así que este
                // espejo es sólo visual, nunca dispara un guardado por sí solo.
                fun onPesoLibrasChanged(nuevoValor: String) {
                    localPeso = nuevoValor
                    nuevoValor.toDoubleOrNull()?.let { libras ->
                        localPesoKgs = String.format(
                            Locale.US,
                            "%.2f",
                            RevisionWeightRules.kilosFromLibras(libras)
                        )
                    }
                }

                fun commitPeso() {
                    onValueChange("npeso", localPeso.toDoubleOrNull() ?: 0.0)
                }

                fun onPesoKgsChanged(nuevoValor: String) {
                    localPesoKgs = nuevoValor
                    nuevoValor.toDoubleOrNull()?.let { kilos ->
                        localPeso = formatQuantity(RevisionWeightRules.librasFromKilos(kilos))
                    }
                }

                fun commitPesoKgs() {
                    val kilos = localPesoKgs.toDoubleOrNull() ?: 0.0
                    val libras = RevisionWeightRules.librasFromKilos(kilos)
                    localPeso = formatQuantity(libras)
                    onValueChange("npeso", libras)
                }

                fun commitPesoTarifa() {
                    // Este campo NO es una segunda unidad de peso -- npeso (arriba) ya
                    // es la libra real. Aquí solo se retecla la misma libra para
                    // disparar, server-side, la regla legacy de estos 5 clientes que
                    // recalcula ncanttarifa con el factor 0.4536 (GGD 14-08-17). Antes
                    // esto convertía a kg y sobreescribía npeso con el resultado,
                    // corrompiéndolo -- npeso se deja tal cual, en libras.
                    val libras = localPesoLibras.toDoubleOrNull() ?: return
                    localPeso = formatQuantity(libras)
                    onValueChange("npeso", libras)
                    onValueChange("npesolibras", libras)
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontal = maxWidth >= 520.dp
                    if (horizontal) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RevisionInput(
                                    label = stringResource(R.string.revision_weight_lb),
                                value = localPeso,
                                onValueChange = ::onPesoLibrasChanged,
                                onCommit = { commitPeso() },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.weight(1f)
                            )
                            if (esClientePesoLibras) {
                                RevisionInput(
                                    label = stringResource(R.string.revision_weight_tariff),
                                    value = localPesoLibras,
                                    onValueChange = { localPesoLibras = it },
                                    onCommit = { commitPesoTarifa() },
                                    enabled = !isLocked,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                RevisionInput(
                                    label = stringResource(R.string.revision_weight_kg),
                                    value = localPesoKgs,
                                    onValueChange = ::onPesoKgsChanged,
                                    onCommit = { commitPesoKgs() },
                                    enabled = !isLocked,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RevisionInput(
                                    label = stringResource(R.string.revision_weight_lb),
                                value = localPeso,
                                onValueChange = ::onPesoLibrasChanged,
                                onCommit = { commitPeso() },
                                enabled = !isLocked,
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (esClientePesoLibras) {
                                RevisionInput(
                                    label = stringResource(R.string.revision_weight_tariff),
                                    value = localPesoLibras,
                                    onValueChange = { localPesoLibras = it },
                                    onCommit = { commitPesoTarifa() },
                                    enabled = !isLocked,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                RevisionInput(
                                    label = stringResource(R.string.revision_weight_kg),
                                    value = localPesoKgs,
                                    onValueChange = ::onPesoKgsChanged,
                                    onCommit = { commitPesoKgs() },
                                    enabled = !isLocked,
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionHeading(
                    icon = Icons.AutoMirrored.Filled.FactCheck,
                    title = stringResource(R.string.revision_part_observations),
                    subtitle = stringResource(R.string.revision_part_observations_subtitle)
                )

                RevisionInput(
                    label = stringResource(R.string.revision_review_notes),
                    value = localObsPartida,
                    onValueChange = { localObsPartida = it },
                    onCommit = {
                        onValueChange("obsrevpartida", localObsPartida.trim())
                    },
                    enabled = !isLocked,
                    minLines = 2,
                    allowNewLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                RevisionInput(
                    label = stringResource(R.string.revision_part_note),
                    value = localNotaPartida,
                    onValueChange = { localNotaPartida = it },
                    onCommit = {
                        onValueChange("mnotapartida", localNotaPartida.trim())
                    },
                    enabled = !isLocked,
                    minLines = 2,
                    allowNewLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        val haptic = LocalHapticFeedback.current
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isLocked) {
                    haptic.performHapticFeedback(
                        HapticFeedbackType.LongPress
                    )
                    onToggleRevisada()
                },
            shape = RoundedCornerShape(14.dp),
            color = if (partida.Revisada) {
                Color(0xFFE8F8ED)
            } else {
                PmmNavyVariant
            },
            border = BorderStroke(
                1.dp,
                if (partida.Revisada) PmmGreen else PmmBlueDeep.copy(alpha = 0.24f)
            )
        ) {
            Row(
                modifier = Modifier.padding(15.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (partida.Revisada) {
                        Icons.Default.CheckCircle
                    } else {
                        Icons.Default.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = if (partida.Revisada) PmmGreen else PmmBlueDeep,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (partida.Revisada) {
                        stringResource(R.string.revision_part_reviewed)
                    } else {
                        stringResource(R.string.revision_mark_part_reviewed)
                    },
                    color = if (partida.Revisada) PmmGreenDark else PmmBlueDeep,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }

    if (showBatchCapture) {
        SeriesBatchCaptureDialog(
            cantidadSugerida = (partida.nCantOut?.takeIf { it > 0 } ?: partida.nCantFactura)
                .let { kotlin.math.round(it).toInt() }
                .coerceAtLeast(1),
            notaActual = localNotaPartida,
            onDismiss = { showBatchCapture = false },
            onApply = { nuevaNota, primeraSerie ->
                localNotaPartida = nuevaNota
                onValueChange("mnotapartida", nuevaNota)
                primeraSerie?.let {
                    localNumSer = it
                    onValueChange("numSer", it)
                }
                showBatchCapture = false
            }
        )
    }
}

/**
 * Botón de entrada a la captura en lote de series. Vive SIEMPRE en su propia
 * fila (ancho completo), nunca junto a Modelo/Serie o lote: con 3-4 botones
 * de acción en la misma fila un celular de ~6" se aprieta mal.
 */
@Composable
private fun BatchSeriesCaptureButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = PmmNavyVariant,
        border = BorderStroke(1.dp, PmmBlueDeep.copy(alpha = if (enabled) 0.4f else 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = if (enabled) PmmBlueDeep else PmmBlueDeep.copy(alpha = 0.35f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                text = stringResource(R.string.revision_batch_series_title),
                color = if (enabled) PmmBlueDeep else PmmBlueDeep.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (enabled) PmmBlueDeep else PmmBlueDeep.copy(alpha = 0.35f),
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

/** Los dos modos del diálogo de captura en lote: [[CONSECUTIVA]] genera N series
 *  seguidas a partir de un número inicial; [[MANUAL]] captura una por una porque
 *  en la realidad casi nunca vienen en orden (500123, 500234, 500657…). */
private enum class SeriesCaptureModo { CONSECUTIVA, MANUAL }

/**
 * Arma la Nota de partida a partir de una lista de series ya resueltas -- NUNCA
 * sobrescribe lo que ya hubiera escrito el revisor, solo anexa. En modo
 * consecutiva reemplaza el truco de Excel (repetir el prefijo 100 veces y
 * llenar el sufijo a mano renglón por renglón); en modo manual reemplaza
 * escribirlas una a una directo en la Nota, sin poder corregir un typo sin
 * borrar todo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesBatchCaptureDialog(
    cantidadSugerida: Int,
    notaActual: String,
    onDismiss: () -> Unit,
    onApply: (nuevaNota: String, primeraSerie: String?) -> Unit
) {
    val localizedContext = LocalContext.current
    var modo by remember { mutableStateOf(SeriesCaptureModo.CONSECUTIVA) }
    var tambienNumSer by remember { mutableStateOf(true) }

    // --- estado modo Consecutiva ---
    var prefijo by remember { mutableStateOf("") }
    var inicio by remember { mutableStateOf("") }
    var cantidadTexto by remember { mutableStateOf(cantidadSugerida.toString()) }
    var mantenerCeros by remember { mutableStateOf(false) }
    var formatoRango by remember { mutableStateOf(false) }

    val cantidad = (cantidadTexto.toIntOrNull() ?: 1).coerceIn(1, 999)
    val inicioNum = inicio.toIntOrNull()
    val pad = if (mantenerCeros) inicio.length else 0

    val seriesConsecutiva = remember(prefijo, inicioNum, cantidad, pad) {
        if (prefijo.isBlank() || inicioNum == null) {
            emptyList()
        } else {
            (0 until cantidad).map { i ->
                val n = inicioNum + i
                prefijo + if (pad > 0) n.toString().padStart(pad, '0') else n.toString()
            }
        }
    }

    // --- estado modo Manual ---
    var prefijoManual by remember { mutableStateOf("") }
    var quickValue by remember { mutableStateOf("") }
    var manualChips by remember { mutableStateOf(listOf<String>()) }
    var dupWarning by remember { mutableStateOf<String?>(null) }
    var mostrarPegado by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    var pasteFeedback by remember { mutableStateOf<String?>(null) }
    val quickFocusRequester = remember { FocusRequester() }

    fun tryAddManual(raw: String) {
        val valor = (prefijoManual.trim() + raw.trim()).trim()
        if (valor.isEmpty()) return
        if (manualChips.contains(valor)) {
            dupWarning = localizedContext.getString(R.string.revision_duplicate_series, valor)
            return
        }
        dupWarning = null
        manualChips = manualChips + valor
        quickValue = ""
        quickFocusRequester.requestFocus()
    }

    fun procesarPegado() {
        val tokens = pasteText.split(Regex("[\\s,;]+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            pasteFeedback = localizedContext.getString(R.string.revision_paste_at_least_one)
            return
        }
        val actualizadas = manualChips.toMutableList()
        var agregadas = 0
        var duplicadas = 0
        tokens.forEach { token ->
            if (actualizadas.contains(token)) duplicadas++ else { actualizadas.add(token); agregadas++ }
        }
        manualChips = actualizadas
        pasteFeedback = if (duplicadas > 0) {
            localizedContext.getString(
                R.string.revision_paste_added_with_duplicates,
                agregadas,
                duplicadas
            )
        } else {
            localizedContext.getString(R.string.revision_paste_added, agregadas)
        }
        pasteText = ""
    }

    val series = if (modo == SeriesCaptureModo.CONSECUTIVA) seriesConsecutiva else manualChips

    val bloqueTexto = when {
        series.isEmpty() -> ""
        modo == SeriesCaptureModo.CONSECUTIVA && formatoRango -> if (series.size == 1) {
            localizedContext.getString(R.string.revision_note_single_series, series.first())
        } else {
            localizedContext.getString(
                R.string.revision_note_series_range,
                series.first(),
                series.last()
            )
        }
        else -> localizedContext.getString(
            R.string.revision_note_series_list,
            series.joinToString(",")
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PmmBlueDeep),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.revision_batch_series_title),
                            color = PmmTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (modo == SeriesCaptureModo.CONSECUTIVA) {
                                stringResource(R.string.revision_batch_series_subtitle)
                            } else {
                                stringResource(R.string.revision_manual_series_subtitle)
                            },
                            color = PmmTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                HorizontalDivider(color = PmmNavyBorder)

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SeriesModoOption(
                            icon = "🔢",
                            label = stringResource(R.string.revision_consecutive),
                            hint = stringResource(R.string.revision_consecutive_hint),
                            selected = modo == SeriesCaptureModo.CONSECUTIVA,
                            modifier = Modifier.weight(1f),
                            onClick = { modo = SeriesCaptureModo.CONSECUTIVA }
                        )
                        SeriesModoOption(
                            icon = "✍️",
                            label = stringResource(R.string.revision_manual),
                            hint = stringResource(R.string.revision_manual_hint),
                            selected = modo == SeriesCaptureModo.MANUAL,
                            modifier = Modifier.weight(1f),
                            onClick = { modo = SeriesCaptureModo.MANUAL }
                        )
                    }

                    if (modo == SeriesCaptureModo.CONSECUTIVA) {
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            if (maxWidth >= 380.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    BatchTextField(
                                        label = stringResource(R.string.revision_common_prefix),
                                        value = prefijo,
                                        onValueChange = { prefijo = it },
                                        placeholder = stringResource(R.string.revision_series_example_prefix),
                                        modifier = Modifier.weight(1f)
                                    )
                                    BatchTextField(
                                        label = stringResource(R.string.revision_initial_number),
                                        value = inicio,
                                        onValueChange = { inicio = it.filter(Char::isDigit) },
                                        placeholder = stringResource(R.string.revision_series_example_number),
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    BatchTextField(
                                        label = stringResource(R.string.revision_common_prefix),
                                        value = prefijo,
                                        onValueChange = { prefijo = it },
                                        placeholder = stringResource(R.string.revision_series_example_prefix),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    BatchTextField(
                                        label = stringResource(R.string.revision_initial_number),
                                        value = inicio,
                                        onValueChange = { inicio = it.filter(Char::isDigit) },
                                        placeholder = stringResource(R.string.revision_series_example_number),
                                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }

                        BatchTextField(
                            label = stringResource(R.string.revision_series_count),
                            value = cantidadTexto,
                            onValueChange = { cantidadTexto = it.filter(Char::isDigit).take(3) },
                            placeholder = stringResource(R.string.revision_series_example_count),
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth(0.55f)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = mantenerCeros,
                                onCheckedChange = { mantenerCeros = it },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = PmmBlueDeep)
                            )
                            Text(
                                text = stringResource(R.string.revision_keep_leading_zeroes),
                                color = PmmTextSecondary,
                                fontSize = 12.5.sp
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PmmNavyCard)
                                .padding(3.dp)
                        ) {
                            SegmentedOption(
                                label = stringResource(R.string.revision_complete_list),
                                selected = !formatoRango,
                                modifier = Modifier.weight(1f),
                                onClick = { formatoRango = false }
                            )
                            SegmentedOption(
                                label = stringResource(R.string.revision_range_list),
                                selected = formatoRango,
                                modifier = Modifier.weight(1f),
                                onClick = { formatoRango = true }
                            )
                        }
                    } else {
                        BatchTextField(
                            label = stringResource(R.string.revision_optional_prefix),
                            value = prefijoManual,
                            onValueChange = { prefijoManual = it },
                            placeholder = stringResource(R.string.revision_prefix_hint),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = stringResource(R.string.revision_prefix_help),
                            color = PmmTextMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            BatchTextField(
                                label = stringResource(R.string.revision_series),
                                value = quickValue,
                                onValueChange = { quickValue = it; dupWarning = null },
                                placeholder = stringResource(
                                    R.string.revision_manual_series_placeholder,
                                    prefijoManual
                                ),
                                imeAction = ImeAction.Done,
                                onImeAction = { tryAddManual(quickValue) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(quickFocusRequester)
                            )
                            Surface(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = quickValue.isNotBlank()) { tryAddManual(quickValue) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (quickValue.isNotBlank()) PmmBlueDeep else PmmNavyBorder
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.revision_add_series),
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                        dupWarning?.let {
                            Text(text = it, color = PmmRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.4.dp, PmmNavyBorder)
                        ) {
                            if (manualChips.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(R.string.revision_no_series),
                                        color = PmmTextMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            } else {
                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(11.dp),
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    verticalArrangement = Arrangement.spacedBy(7.dp)
                                ) {
                                    manualChips.forEach { valor ->
                                        SeriesChip(value = valor, onRemove = { manualChips = manualChips - valor })
                                    }
                                }
                            }
                        }

                        Text(
                            text = stringResource(
                                if (mostrarPegado) R.string.revision_hide_multi_paste
                                else R.string.revision_paste_multiple
                            ),
                            color = PmmBlueDeep,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            modifier = Modifier.clickable { mostrarPegado = !mostrarPegado }
                        )
                        if (mostrarPegado) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = pasteText,
                                    onValueChange = { pasteText = it; pasteFeedback = null },
                                    placeholder = {
                                        Text(
                                            stringResource(R.string.revision_paste_hint),
                                            fontSize = 12.sp
                                        )
                                    },
                                    minLines = 2,
                                    maxLines = 4,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PmmBlueDeep,
                                        unfocusedBorderColor = PmmNavyBorder,
                                        focusedTextColor = PmmTextPrimary,
                                        unfocusedTextColor = PmmTextPrimary,
                                        cursorColor = PmmBlueDeep
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = stringResource(R.string.revision_paste_as_is),
                                    color = PmmTextMuted,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                                OutlinedButton(
                                    onClick = { procesarPegado() },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.4.dp, PmmBlueDeep),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmBlueDeep)
                                ) {
                                    Text(
                                        stringResource(R.string.revision_process_list),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                }
                                pasteFeedback?.let {
                                    Text(
                                        text = it,
                                        color = if (it.startsWith("+")) PmmGreenDark else PmmTextMuted,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = PmmNavyCard
                        ) {
                            Text(
                                text = stringResource(R.string.revision_not_consecutive),
                                color = PmmTextMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(11.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = PmmNavyVariant,
                        border = BorderStroke(1.dp, PmmBlueDeep.copy(alpha = 0.35f))
                    ) {
                        Column(modifier = Modifier.padding(13.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.revision_preview_title),
                                    color = PmmBlueDeep,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = stringResource(
                                        R.string.revision_series_count_label,
                                        series.size,
                                        if (series.size == 1) "" else "s"
                                    ),
                                    color = PmmTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = bloqueTexto.ifBlank {
                                    if (modo == SeriesCaptureModo.CONSECUTIVA) {
                                        stringResource(R.string.revision_complete_prefix_preview)
                                    } else {
                                        stringResource(R.string.revision_add_series_preview)
                                    }
                                },
                                color = if (bloqueTexto.isBlank()) PmmTextMuted else PmmTextPrimary,
                                fontSize = 12.5.sp,
                                lineHeight = 17.sp,
                                fontStyle = if (bloqueTexto.isBlank()) {
                                    androidx.compose.ui.text.font.FontStyle.Italic
                                } else {
                                    androidx.compose.ui.text.font.FontStyle.Normal
                                }
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = tambienNumSer,
                            onCheckedChange = { tambienNumSer = it },
                            colors = androidx.compose.material3.CheckboxDefaults.colors(checkedColor = PmmBlueDeep)
                        )
                        Text(
                            text = stringResource(R.string.revision_save_first_series),
                            color = PmmTextSecondary,
                            fontSize = 12.5.sp
                        )
                    }
                }

                HorizontalDivider(color = PmmNavyBorder)
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp)
                    ) { Text(stringResource(R.string.common_cancel)) }
                    Button(
                        onClick = {
                            val nuevaNota = if (notaActual.isNotBlank()) {
                                "$notaActual\n\n$bloqueTexto"
                            } else {
                                bloqueTexto
                            }
                            onApply(nuevaNota, if (tambienNumSer) series.firstOrNull() else null)
                        },
                        enabled = series.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(11.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PmmBlueDeep,
                            contentColor = Color.White,
                            disabledContainerColor = PmmBlueDeep.copy(alpha = 0.35f),
                            disabledContentColor = Color.White
                        )
                    ) {
                        Text(
                            text = stringResource(
                                R.string.revision_add_series_count,
                                series.size,
                                if (series.size == 1) "" else "s"
                            ),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesModoOption(
    icon: String,
    label: String,
    hint: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = if (selected) PmmNavyVariant else PmmNavyCard,
        border = BorderStroke(1.4.dp, if (selected) PmmBlueDeep else PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "$icon $label",
                color = if (selected) PmmBlueDeep else PmmTextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = hint,
                color = if (selected) PmmBlueDeep.copy(alpha = 0.85f) else PmmTextMuted,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeriesChip(value: String, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = PmmNavyVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 5.dp, top = 5.dp, bottom = 5.dp)
        ) {
            Text(
                text = value,
                color = PmmBlueDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp
            )
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(PmmBlueDeep.copy(alpha = 0.14f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.revision_remove),
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(11.dp)
                )
            }
        }
    }
}

@Composable
private fun BatchTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        placeholder = { Text(placeholder, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onDone = {
                keyboardController?.hide()
                focusManager.clearFocus()
                onImeAction?.invoke()
            }
        ),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PmmBlueDeep,
            unfocusedBorderColor = PmmNavyBorder,
            focusedTextColor = PmmTextPrimary,
            unfocusedTextColor = PmmTextPrimary,
            cursorColor = PmmBlueDeep
        ),
        modifier = modifier
    )
}

@Composable
private fun SegmentedOption(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) PmmBlueDeep else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else PmmTextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PartidaEditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    enabled: Boolean,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    quickLabel: String? = null,
    onQuickFill: (() -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Mismo guardado que en RevisionInput: sin esto, un scroll que recicla
    // este item del LazyColumn (Marca/Modelo/Serie de la partida activa)
    // dispara onCommit() en la primera composición, como si el usuario
    // hubiera editado y salido del campo sin haberlo tocado.
    var wasFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                label = { Text(label) },
                placeholder = { Text(stringResource(R.string.revision_write_here)) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder,
                    disabledBorderColor = PmmNavyBorder,
                    focusedTextColor = PmmTextPrimary,
                    unfocusedTextColor = PmmTextPrimary,
                    disabledTextColor = PmmTextSecondary,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = PmmNavyCard,
                    cursorColor = PmmBlueDeep
                ),
                keyboardOptions = KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        // onFocusChanged hace el commit real al detectar la
                        // transición de foco que clearFocus() provoca.
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (wasFocused && !focusState.isFocused) {
                            onCommit()
                        }
                        wasFocused = focusState.isFocused
                    }
            )
            quickLabel?.let { labelText ->
                OutlinedButton(
                    onClick = { onQuickFill?.invoke() },
                    enabled = enabled,
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder)
                ) {
                    Text(
                        text = labelText,
                        color = PmmBlueDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
            FilledTonalIconButton(
                onClick = onScan,
                enabled = enabled,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = PmmNavyVariant,
                    contentColor = PmmBlueDeep
                )
            ) {
                Icon(
                imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = stringResource(R.string.revision_scan_field, label),
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnidadComercialSelector(
    selectedCode: String,
    options: List<UnidadComercialDto>,
    enabled: Boolean,
    onSelect: (UnidadComercialDto) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = options.firstOrNull {
        it.codigo.equals(selectedCode.trim(), ignoreCase = true)
    }
    val displayedValue = selected?.let { "${it.codigo} · ${it.nombre}" }
        ?: selectedCode.ifBlank { stringResource(R.string.revision_select_unit) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // ExposedDropdownMenuBox en vez de un Box + clickable a mano: un
        // OutlinedTextField de solo lectura intercepta el toque para su propio
        // manejo de selección de texto/cursor, así que un Modifier.clickable
        // por fuera no siempre alcanzaba a disparar (el usuario reportó que
        // el tap no desplegaba nada). menuAnchor() es el mecanismo de
        // Material3 pensado justo para este patrón.
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it }
        ) {
            OutlinedTextField(
                value = displayedValue,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                singleLine = true,
                label = { Text(stringResource(R.string.revision_commercial_unit)) },
                trailingIcon = {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(
                            if (expanded) R.string.revision_close_units
                            else R.string.revision_open_units
                        ),
                        tint = PmmBlueDeep
                    )
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder,
                    disabledBorderColor = PmmNavyBorder,
                    focusedTextColor = PmmTextPrimary,
                    unfocusedTextColor = PmmTextPrimary,
                    disabledTextColor = PmmTextSecondary,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = PmmNavyCard,
                    cursorColor = PmmBlueDeep
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled)
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                if (options.isEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.revision_catalog_unavailable),
                                color = PmmTextSecondary
                            )
                        },
                        onClick = { expanded = false },
                        enabled = false
                    )
                } else {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${option.codigo} · ${option.nombre}",
                                    color = PmmTextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            leadingIcon = {
                                if (option.codigo.equals(selectedCode, ignoreCase = true)) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = PmmBlueDeep
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(option)
                            }
                        )
                    }
                }
            }
        }
        if (selected == null && options.isEmpty()) {
            Text(
                text = stringResource(R.string.revision_units_catalog_error),
                color = PmmAmber,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun TipoMaterialSelector(
    selectedCode: String,
    options: List<TipoMaterialDto>,
    enabled: Boolean,
    onSelect: (TipoMaterialDto) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = stringResource(R.string.revision_material_type_optional),
            color = PmmTextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = stringResource(R.string.revision_material_type_hint),
            color = PmmTextSecondary,
            fontSize = 12.sp
        )
        if (options.isEmpty()) {
            Text(
                text = stringResource(R.string.revision_no_options),
                color = PmmTextSecondary,
                fontSize = 13.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    val code = option.codigo.orEmpty()
                    val description = option.descrip.orEmpty().ifBlank { code }
                    val isSelected = code.equals(selectedCode, ignoreCase = true)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onSelect(option) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) PmmBlueDeep.copy(alpha = 0.14f) else Color.White,
                        border = BorderStroke(1.dp, if (isSelected) PmmBlueDeep else PmmNavyBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) PmmBlueDeep else PmmTextMuted,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = description,
                                color = PmmTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (code.isNotBlank()) {
                                Text(code, color = PmmTextMuted, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountrySearchField(
    selectedCode: String,
    countries: List<PaisDto>,
    frequentCountries: List<PaisFrecuenteDto>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedName = countries.firstOrNull {
        it.codigo?.trim()?.equals(selectedCode.trim(), ignoreCase = true) == true
    }?.descrip.orEmpty()
    var query by remember(selectedCode, selectedName) {
        mutableStateOf(selectedName.ifBlank { selectedCode })
    }
    val normalizedQuery = query.trim()
    val normalizedSelectedName = selectedName.trim()
    val isSearching = normalizedQuery.isNotBlank() &&
        !normalizedQuery.equals(normalizedSelectedName, ignoreCase = true)
    val matchingCountries = countries
        .filter { country ->
            isSearching &&
                (country.descrip.orEmpty().contains(normalizedQuery, ignoreCase = true) ||
                    country.codigo.orEmpty().contains(normalizedQuery, ignoreCase = true))
        }
        .take(6)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(
            1.dp,
            if (selectedCode.isNotBlank()) PmmBlueDeep.copy(alpha = 0.65f) else PmmNavyBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                enabled = enabled,
                singleLine = true,
                label = { Text(stringResource(R.string.revision_country)) },
                placeholder = { Text(stringResource(R.string.revision_country_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(
                            onClick = {
                                query = ""
                                onSelect("")
                            },
                            enabled = enabled
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.common_clear_search),
                                tint = PmmTextSecondary
                            )
                        }
                    } else if (selectedCode.isNotBlank() && !isSearching) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = stringResource(R.string.revision_selected_country),
                            tint = PmmGreen
                        )
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PmmBlueDeep,
                    unfocusedBorderColor = PmmNavyBorder,
                    disabledBorderColor = PmmNavyBorder,
                    focusedTextColor = PmmTextPrimary,
                    unfocusedTextColor = PmmTextPrimary,
                    disabledTextColor = PmmTextSecondary,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = PmmNavyCard,
                    cursorColor = PmmBlueDeep
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (frequentCountries.isNotEmpty()) {
                Text(
                        text = stringResource(R.string.revision_quick_suggestions),
                    color = PmmTextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(frequentCountries.take(5), key = { it.codigo.orEmpty() }) { country ->
                        val code = country.codigo.orEmpty()
                        val description = country.descrip.orEmpty().ifBlank { code }
                        AssistChip(
                            onClick = { onSelect(code) },
                            enabled = enabled,
                            label = {
                                Text(
                                    text = description,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }
                }
            }

            if (isSearching) {
                if (matchingCountries.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.revision_results),
                        color = PmmTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        matchingCountries.forEach { country ->
                            val code = country.codigo.orEmpty()
                            val description = country.descrip.orEmpty().ifBlank { code }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = enabled) { onSelect(code) }
                                    .padding(horizontal = 8.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = description,
                                    color = PmmTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                if (code.isNotBlank()) {
                                    Text(code, color = PmmTextMuted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.revision_country_not_found),
                        color = PmmTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.2f", value)
    }
}
