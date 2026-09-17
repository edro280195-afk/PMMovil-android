package com.example.pmmovil.ui.salida

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.salida.components.SalidaKpiBanner
import com.example.pmmovil.ui.salida.components.SalidaPersistentBottomSheet
import com.example.pmmovil.ui.salida.components.SalidaScannerStage
import com.example.pmmovil.ui.salida.components.SalidaTopHeader
import com.example.pmmovil.ui.salida.components.SheetDisplayMode
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.google.mlkit.vision.barcode.common.Barcode

private val MonoFamily = FontFamily.Monospace

@Composable
fun SalidaScreen(
    onNavigateBack: () -> Unit,
    viewModel: SalidaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }

    // Lector físico / Bluetooth (HID) o gatillo Zebra: siempre escuchando.
    HardwareScannerListener(viewModel::onScannerInput)

    // Mantener pantalla encendida mientras se opera en bodega.
    val keepOn = state.stage == SalidaStage.ACTIVE || state.stage == SalidaStage.DESCARGA
    DisposableEffect(keepOn) {
        view.keepScreenOn = keepOn
        onDispose { view.keepScreenOn = false }
    }
    DisposableEffect(Unit) { onDispose { tone.release() } }

    // Feedback periférico sensorial: tono de confirmación + vibración háptica.
    LaunchedEffect(state.feedbackSeq) {
        if (state.feedbackSeq == 0) return@LaunchedEffect
        if (state.flash == SalidaFlash.ERROR) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching { tone.startTone(ToneGenerator.TONE_PROP_NACK, 160) }
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            runCatching { tone.startTone(ToneGenerator.TONE_PROP_ACK, 120) }
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearMessage()
        }
    }

    state.confirm?.let { confirm ->
        SalidaConfirmDialog(
            confirm = confirm,
            processing = state.isProcessingAction,
            onConfirm = viewModel::confirmAction,
            onDismiss = viewModel::dismissConfirm
        )
    }

    val toggleCaptureMode = {
        val nextMode = if (state.captureMode == SalidaCaptureMode.CAMERA) {
            SalidaCaptureMode.PHYSICAL
        } else {
            SalidaCaptureMode.CAMERA
        }
        viewModel.setCaptureMode(nextMode)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PmmBgPrimary,
            snackbarHost = { PmmSnackbarHost(snackbarHostState) },
            topBar = {
                val rel = state.relacion
                val headerTitle = if (state.mode == SalidaMode.SIN_RELACION) {
                    "Salida · Directa"
                } else if (rel != null) {
                    if (rel.caja.isNotBlank()) "${rel.relno} · C-${rel.caja}" else rel.relno
                } else {
                    "Salida Bodega"
                }
                SalidaTopHeader(
                    titulo = headerTitle,
                    usuario = state.usuario,
                    captureMode = state.captureMode,
                    onToggleCaptureMode = toggleCaptureMode,
                    onBack = onNavigateBack
                )
            },
            bottomBar = {
                // En etapas no manejadas por el persistent bottom sheet, se muestra barra si aplica
                if (state.stage == SalidaStage.DESCARGA) {
                    DescargaBottomBar(
                        processing = state.isProcessingAction,
                        enabled = state.etiquetasDescarga.all { it.escaneado } && state.descargaTotal > 0,
                        onConfirm = viewModel::finalizeDescarga
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (state.stage) {
                    SalidaStage.IDLE -> IdleContent(state, viewModel, toggleCaptureMode)
                    SalidaStage.LOADING -> LoadingContent()
                    SalidaStage.ACTIVE -> ActiveContent(state, viewModel, toggleCaptureMode)
                    SalidaStage.DESCARGA -> DescargaContent(state, viewModel, toggleCaptureMode)
                    SalidaStage.BLOCKED -> BlockedContent(state, viewModel::limpiar)
                    SalidaStage.FINISHED -> FinishedContent(state, viewModel::nuevaOperacion)
                }
            }
        }

        FlashOverlay(flash = state.flash)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// IDLE — Arranque inteligente con Escenario Superior Dominante
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun IdleContent(
    state: SalidaUiState,
    viewModel: SalidaViewModel,
    onToggleCaptureMode: () -> Unit
) {
    val esNormal = state.mode == SalidaMode.NORMAL

    Column(modifier = Modifier.fillMaxSize()) {
        // Escenario Superior de Escaneo (Cámara amplia panorámica o Radar Láser)
        SalidaScannerStage(
            captureMode = state.captureMode,
            onBarcodeScanned = viewModel::onScannerInput,
            onSwitchToCamera = { viewModel.setCaptureMode(SalidaCaptureMode.CAMERA) },
            barcodeFormats = Barcode.FORMAT_ALL_FORMATS,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
        )

        // Panel inferior con selectores y entrada manual rápida
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = PmmBgPrimary,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector de modo de carga
                ModeToggle(mode = state.mode, onSelect = viewModel::setMode)

                // Captura manual con botón de acción rápida
                Surface(
                    color = PmmBgSurface,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (esNormal) "NÚMERO DE RELACIÓN" else "NÚMERO DE ENTRADA",
                            color = PmmTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = if (esNormal) state.relnoInput else state.entradaInput,
                                onValueChange = if (esNormal) viewModel::onRelnoInput else viewModel::onEntradaInput,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = MonoFamily,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PmmTextPrimary
                                ),
                                placeholder = {
                                    Text(
                                        text = if (esNormal) "Ej: 01I26105252" else "Ej: 01C260001...",
                                        fontFamily = MonoFamily,
                                        fontSize = 14.sp,
                                        color = PmmTextMuted
                                    )
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(
                                    onGo = { if (esNormal) viewModel.submitRelacion() else viewModel.submitEntrada() }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PmmBlueDeep,
                                    unfocusedBorderColor = PmmNavyBorder,
                                    cursorColor = PmmBlueDeep
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = { if (esNormal) viewModel.submitRelacion() else viewModel.submitEntrada() },
                                enabled = !state.isLoadingRelacion && (if (esNormal) state.relnoInput.isNotBlank() else state.entradaInput.isNotBlank()),
                                modifier = Modifier.height(54.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PmmBlueDeep,
                                    contentColor = Color.White
                                )
                            ) {
                                if (state.isLoadingRelacion) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                } else {
                                    Text("Cargar", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Instrucción visual PMM
                InfoAlert(
                    text = if (esNormal)
                        "Enfoca el código QR de la relación o aprieta el gatillo del lector Zebra."
                    else
                        "Entrada directa / transbordo: escanea o escribe el número de entrada."
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// ACTIVE — Arquitectura Scandit / Material 3: Cámara + Bottom Sheet
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveContent(
    state: SalidaUiState,
    viewModel: SalidaViewModel,
    onToggleCaptureMode: () -> Unit
) {
    var sheetMode by remember { mutableStateOf(SheetDisplayMode.PEEK) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalHeight = maxHeight

        // Altura dinámica según el estado del sheet para garantizar visibilidad directa de bultos
        val peekHeight = (totalHeight * 0.48f).coerceIn(360.dp, 430.dp)
        val halfHeight = (totalHeight * 0.65f).coerceAtLeast(470.dp)
        val expandedHeight = totalHeight - 44.dp

        val targetHeight = when (sheetMode) {
            SheetDisplayMode.PEEK -> peekHeight
            SheetDisplayMode.HALF -> halfHeight
            SheetDisplayMode.EXPANDED -> expandedHeight
        }

        val animatedSheetHeight by animateDpAsState(
            targetValue = targetHeight,
            animationSpec = tween(350),
            label = "sheetHeight"
        )

        // Fondo y Capas Superiores: La cámara ocupa el escenario libremente sin banners estorbosos
        Column(modifier = Modifier.fillMaxSize()) {
            if (state.errorBanner != null) ErrorBanner(text = state.errorBanner)
            if (state.estatusMsg.isNotBlank()) StatusAlert(text = state.estatusMsg)

            // Cámara / Escenario
            SalidaScannerStage(
                captureMode = state.captureMode,
                onBarcodeScanned = viewModel::onScannerInput,
                onSwitchToCamera = { viewModel.setCaptureMode(SalidaCaptureMode.CAMERA) },
                barcodeFormats = Barcode.FORMAT_ALL_FORMATS,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        // Persistent Bottom Sheet Deslizable (Scandit / Material 3)
        SalidaPersistentBottomSheet(
            sheetMode = sheetMode,
            onToggleSheetMode = {
                sheetMode = when (sheetMode) {
                    SheetDisplayMode.PEEK -> SheetDisplayMode.HALF
                    SheetDisplayMode.HALF -> SheetDisplayMode.EXPANDED
                    SheetDisplayMode.EXPANDED -> SheetDisplayMode.PEEK
                }
            },
            onSetSheetMode = { sheetMode = it },
            sheetHeight = animatedSheetHeight,
            escaneados = state.escaneados,
            faltan = state.pendientes,
            total = state.total,
            progreso = state.progreso,
            lastScanned = state.lastScanned,
            isProcessingScan = state.isProcessingScan,
            captureStep = state.captureStep,
            casetaQrSecondsRemaining = state.casetaQrSecondsRemaining,
            etiquetas = state.etiquetas,
            todoEscaneado = state.todoEscaneado,
            isProcessingAction = state.isProcessingAction,
            onManualSubmit = viewModel::onScannerInput,
            onCancel = viewModel::requestCancel,
            onPause = viewModel::requestPause,
            onFinalize = viewModel::attemptFinalize,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// DESCARGA
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DescargaContent(
    state: SalidaUiState,
    viewModel: SalidaViewModel,
    onToggleCaptureMode: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = PmmRedDark) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "${state.descargaTotal - state.descargados}",
                            color = Color.White,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = MonoFamily
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "POR RE-ESCANEAR",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = "${state.relacion?.relno.orEmpty()} · relación pausada",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = MonoFamily
                    )
                }
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = PmmYellow, modifier = Modifier.size(36.dp))
            }
        }

        if (state.errorBanner != null) ErrorBanner(text = state.errorBanner)

        SalidaScannerStage(
            captureMode = state.captureMode,
            onBarcodeScanned = viewModel::onScannerInput,
            onSwitchToCamera = { viewModel.setCaptureMode(SalidaCaptureMode.CAMERA) },
            barcodeFormats = Barcode.FORMAT_ALL_FORMATS,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.1f),
            color = PmmBgSurface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            shadowElevation = 8.dp
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = state.etiquetasDescarga, key = { it.textoEtiqueta }) { et ->
                    EtiquetaRow(et = et, scanned = et.escaneado)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Componentes Auxiliares
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ModeToggle(mode: SalidaMode, onSelect: (SalidaMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PmmNavyVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SegmentButton(
            label = "Con Relación",
            icon = Icons.Filled.CheckCircle,
            selected = mode == SalidaMode.NORMAL,
            onClick = { onSelect(SalidaMode.NORMAL) },
            modifier = Modifier.weight(1f)
        )
        SegmentButton(
            label = "Sin Relación",
            icon = Icons.Filled.QrCode2,
            selected = mode == SalidaMode.SIN_RELACION,
            onClick = { onSelect(SalidaMode.SIN_RELACION) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SegmentButton(
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) PmmBlueDeep else PmmTextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) PmmBlueDeep else PmmTextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EtiquetaRow(et: com.example.pmmovil.data.model.SalidaEtiqueta, scanned: Boolean) {
    val bg = if (scanned) Color(0xFFF0FDF4) else Color(0xFFFEFCE8)
    val border = if (scanned) PmmGreen.copy(alpha = 0.30f) else PmmAmber.copy(alpha = 0.35f)
    val dot = if (scanned) PmmGreen else PmmAmber
    val textColor = if (scanned) PmmGreenDark else Color(0xFF713F12)

    Surface(color = bg, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, border)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scanned) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = dot, modifier = Modifier.size(16.dp))
            } else {
                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dot))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                text = et.textoPagina.ifBlank { et.textoEtiqueta },
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DescargaBottomBar(processing: Boolean, enabled: Boolean, onConfirm: () -> Unit) {
    Surface(color = PmmBgSurface, shadowElevation = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Button(
                onClick = onConfirm,
                enabled = enabled && !processing,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmNavyDark)
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PmmNavyDark)
                } else {
                    Text("Confirmar re-escaneo", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = PmmBlueDeep)
        Spacer(Modifier.height(14.dp))
        Text("Cargando relación...", color = PmmTextSecondary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BlockedContent(state: SalidaUiState, onBack: () -> Unit) {
    val b = state.bloqueo
    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFFFF7F7)).padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(18.dp),
            color = PmmBgSurface,
            border = BorderStroke(2.dp, Color(0xFFFECACA))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)).background(PmmRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("ESCANEO DETENIDO", color = PmmRedDark, fontSize = 11.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Relación bloqueada", color = Color(0xFF7F1D1D), fontSize = 24.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = b?.motivo ?: "Escaneo rápido detectado. Contacta al supervisor.",
                    color = Color(0xFF4B1F1F),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Volver a inicio", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun FinishedContent(state: SalidaUiState, onNueva: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 460.dp)
        ) {
            Box(
                modifier = Modifier.size(88.dp).clip(CircleShape).background(PmmGreen),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("¡Salida registrada!", color = PmmTextPrimary, fontSize = 23.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "La relación ${state.relacion?.relno.orEmpty()} fue procesada correctamente.",
                color = PmmTextSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onNueva,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Nueva operación", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun SalidaConfirmDialog(
    confirm: SalidaConfirm,
    processing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val accent = when (confirm.kind) {
        SalidaConfirmKind.DANGER -> PmmRed
        SalidaConfirmKind.WARNING -> PmmAmber
        SalidaConfirmKind.NEUTRAL -> PmmBlueDeep
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 460.dp),
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface
        ) {
            Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(confirm.title, color = PmmTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(confirm.message, color = PmmTextSecondary, fontSize = 14.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Volver")
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !processing,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent)
                    ) {
                        if (processing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(confirm.confirmLabel, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(text: String) {
    Surface(color = PmmRed) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun StatusAlert(text: String) {
    Surface(color = Color(0xFFFFFBEB)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = PmmAmber, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                color = Color(0xFF92400E),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun InfoAlert(text: String) {
    Surface(
        color = Color(0xFFEFF6FF),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFBFDBFE))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, color = Color(0xFF1E40AF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FlashOverlay(flash: SalidaFlash) {
    AnimatedVisibility(
        visible = flash != SalidaFlash.NONE,
        enter = fadeIn(tween(90)),
        exit = fadeOut(tween(260))
    ) {
        val color = if (flash == SalidaFlash.ERROR) PmmRed else PmmGreen
        Box(modifier = Modifier.fillMaxSize().background(color.copy(alpha = 0.42f)))
    }
}
