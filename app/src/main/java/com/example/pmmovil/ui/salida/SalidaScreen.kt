package com.example.pmmovil.ui.salida

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import kotlinx.coroutines.delay

private val MonoFamily = FontFamily.Monospace

/**
 * Cargar mercancía. El cargador aprieta el gatillo del lector (o abre la cámara) y escanea: la relación y después cada
 * bulto. Lo que escanea decide si es con relación o una entrada directa; no hay campos ni selectores que llenar.
 */
@Composable
fun SalidaScreen(
    onNavigateBack: () -> Unit,
    /** La relación que llega desde el escaneo del Inicio («Cargar mercancía»): se carga como si la hubiera leído el lector. */
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    viewModel: SalidaViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) }

    LaunchedEffect(barcodeScanResult) {
        barcodeScanResult?.takeIf(String::isNotBlank)?.let {
            viewModel.onScannerInput(it)
            onBarcodeScanConsumed()
        }
    }

    // Lector físico / Bluetooth (HID) o gatillo del Honeywell/Zebra: siempre escuchando.
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

    var camaraTamano by rememberSaveable { mutableStateOf(CamaraTamano.CHICA) }
    var menuAbierto by remember { mutableStateOf(false) }
    var escribirCodigo by remember { mutableStateOf(false) }

    val trabajando = state.stage == SalidaStage.IDLE || state.stage == SalidaStage.ACTIVE || state.stage == SalidaStage.DESCARGA
    val camaraActiva = state.captureMode == SalidaCaptureMode.CAMERA && trabajando
    val alternarCamara = {
        viewModel.setCaptureMode(
            if (state.captureMode == SalidaCaptureMode.CAMERA) SalidaCaptureMode.PHYSICAL else SalidaCaptureMode.CAMERA
        )
    }

    if (escribirCodigo) {
        EscribirCodigoDialog(
            onDismiss = { escribirCodigo = false },
            onSubmit = {
                escribirCodigo = false
                viewModel.onScannerInput(it)
            }
        )
    }

    val acciones = buildList {
        if (state.stage == SalidaStage.IDLE || state.stage == SalidaStage.ACTIVE) {
            add(MenuAccion("Escribir el código") { escribirCodigo = true })
        }
        if (state.stage == SalidaStage.ACTIVE) {
            if (state.mode == SalidaMode.NORMAL) add(MenuAccion("Pedir autorización") { viewModel.requestAuthorization() })
            add(MenuAccion("Cancelar la carga", peligro = true) { viewModel.requestCancel() })
        }
        if (state.stage == SalidaStage.DESCARGA) {
            add(MenuAccion("Cancelar la carga", peligro = true) { viewModel.requestCancel() })
        }
    }

    val rel = state.relacion
    val titulo = when (state.stage) {
        SalidaStage.ACTIVE, SalidaStage.DESCARGA, SalidaStage.BLOCKED -> rel?.relno?.takeIf(String::isNotBlank) ?: "Cargar mercancía"
        SalidaStage.FINISHED -> "Carga terminada"
        else -> "Cargar mercancía"
    }
    val subtitulo = when (state.stage) {
        SalidaStage.IDLE -> "Con relación o entrada directa"
        SalidaStage.LOADING -> "Buscando la relación…"
        SalidaStage.ACTIVE ->
            if (state.mode == SalidaMode.SIN_RELACION) {
                listOfNotNull("Sin relación", rel?.cliente?.takeIf(String::isNotBlank)).joinToString(" · ")
            } else {
                listOfNotNull(rel?.caja?.takeIf(String::isNotBlank)?.let { "Caja $it" }, rel?.cliente?.takeIf(String::isNotBlank))
                    .joinToString(" · ").ifBlank { "Cargando" }
            }
        SalidaStage.DESCARGA -> "Descarga pendiente"
        SalidaStage.BLOCKED -> "Bloqueada"
        SalidaStage.FINISHED -> state.usuario
    }

    var ahora by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.ultimoEscaneo) {
        while (state.ultimoEscaneo != null) {
            ahora = System.currentTimeMillis()
            delay(1_000)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = PmmBgPrimary,
            snackbarHost = { PmmSnackbarHost(snackbarHostState) },
            topBar = {
                SalidaBarraSuperior(
                    titulo = titulo,
                    subtitulo = subtitulo,
                    tituloEsCodigo = rel?.relno?.isNotBlank() == true &&
                        (state.stage == SalidaStage.ACTIVE || state.stage == SalidaStage.DESCARGA || state.stage == SalidaStage.BLOCKED),
                    camaraActiva = camaraActiva,
                    menuAbierto = menuAbierto,
                    acciones = acciones,
                    onBack = onNavigateBack,
                    onCamara = alternarCamara,
                    onMenu = { menuAbierto = it }
                )
            },
            bottomBar = {
                when (state.stage) {
                    SalidaStage.ACTIVE -> SalidaBarraAcciones(
                        state = state,
                        onPausar = viewModel::requestPause,
                        onFinalizar = viewModel::attemptFinalize
                    )
                    SalidaStage.DESCARGA -> DescargaBottomBar(
                        processing = state.isProcessingAction,
                        enabled = state.etiquetasDescarga.all { it.escaneado } && state.descargaTotal > 0,
                        onConfirm = viewModel::finalizeDescarga
                    )
                    else -> Unit
                }
            }
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val ancha = maxWidth >= 600.dp
                // En tableta la cámara va como bloque junto al avance; en un lector compacto flota encima de la lista.
                val camaraEnLinea = camaraActiva && (state.stage == SalidaStage.IDLE || (ancha && state.stage == SalidaStage.ACTIVE))
                val camaraFlotante = camaraActiva && !camaraEnLinea && state.stage != SalidaStage.IDLE

                when (state.stage) {
                    SalidaStage.IDLE -> SalidaReposo(
                        camaraActiva = camaraActiva,
                        onUsarCamara = { viewModel.setCaptureMode(SalidaCaptureMode.CAMERA) },
                        camara = {
                            SalidaCamaraEnLinea(
                                ultimo = state.ultimoEscaneo,
                                alto = 240.dp,
                                onEscaneo = viewModel::onScannerInput,
                                onCerrar = { viewModel.setCaptureMode(SalidaCaptureMode.PHYSICAL) }
                            )
                        }
                    )
                    SalidaStage.LOADING -> LoadingContent()
                    SalidaStage.ACTIVE -> CargaActiva(
                        state = state,
                        ahora = ahora,
                        ancha = ancha,
                        camaraEnLinea = camaraEnLinea,
                        onEscaneo = viewModel::onScannerInput,
                        onCerrarCamara = { viewModel.setCaptureMode(SalidaCaptureMode.PHYSICAL) }
                    )
                    SalidaStage.DESCARGA -> Column(modifier = Modifier.fillMaxSize()) {
                        if (state.errorBanner != null) ErrorBanner(text = state.errorBanner!!)
                        SalidaDescarga(state = state, modifier = Modifier.weight(1f))
                    }
                    SalidaStage.BLOCKED -> BlockedContent(state, viewModel::limpiar)
                    SalidaStage.FINISHED -> FinishedContent(state, viewModel::nuevaOperacion)
                }

                if (camaraFlotante) {
                    SalidaCamaraFlotante(
                        tamano = camaraTamano,
                        ultimo = state.ultimoEscaneo,
                        onEscaneo = viewModel::onScannerInput,
                        onTamano = {
                            camaraTamano = if (camaraTamano == CamaraTamano.CHICA) CamaraTamano.GRANDE else CamaraTamano.CHICA
                        },
                        onCerrar = { viewModel.setCaptureMode(SalidaCaptureMode.PHYSICAL) }
                    )
                }
            }
        }

        FlashOverlay(flash = state.flash)
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Cargando: la relación, el avance, el último escaneo y los bultos
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CargaActiva(
    state: SalidaUiState,
    ahora: Long,
    ancha: Boolean,
    camaraEnLinea: Boolean,
    onEscaneo: (String) -> Unit,
    onCerrarCamara: () -> Unit
) {
    var pestanaElegida by rememberSaveable { mutableStateOf(PestanaBultos.PENDIENTES) }
    var masRecientesPrimero by rememberSaveable { mutableStateOf(true) }
    // Con todo escaneado ya no hay nada pendiente que mirar: se muestra lo escaneado.
    val pestana = if (state.total > 0 && state.pendientes == 0) PestanaBultos.ESCANEADOS else pestanaElegida

    val avisos: @Composable () -> Unit = {
        if (state.errorBanner != null) ErrorBanner(text = state.errorBanner)
        if (state.estatusMsg.isNotBlank()) StatusAlert(text = state.estatusMsg)
    }
    val lista: @Composable (Modifier) -> Unit = { modifier ->
        SalidaListaBultos(
            pestana = pestana,
            state = state,
            masRecientesPrimero = masRecientesPrimero,
            onCambiarOrden = { masRecientesPrimero = !masRecientesPrimero },
            modifier = modifier
        )
    }

    if (ancha) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxSize()
            ) {
                avisos()
                SalidaResumen(state)
                SalidaUltimoEscaneoCard(state, ahora, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp))
                if (camaraEnLinea) {
                    SalidaCamaraEnLinea(
                        ultimo = state.ultimoEscaneo,
                        alto = 200.dp,
                        onEscaneo = onEscaneo,
                        onCerrar = onCerrarCamara,
                        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(0.54f).fillMaxSize()) {
                SalidaPestanas(
                    pestana = pestana,
                    pendientes = state.pendientes,
                    escaneados = state.escaneados,
                    onSelect = { pestanaElegida = it },
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                )
                lista(Modifier.weight(1f))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            avisos()
            SalidaResumen(state)
            SalidaUltimoEscaneoCard(state, ahora, Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp))
            SalidaPestanas(
                pestana = pestana,
                pendientes = state.pendientes,
                escaneados = state.escaneados,
                onSelect = { pestanaElegida = it },
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
            )
            lista(Modifier.weight(1f))
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Escribir el código a mano (por si un QR no lee)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun EscribirCodigoDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escribir el código", fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Solo para cuando un QR no lee. Acepta la relación, la entrada o la etiqueta de un bulto.",
                    color = PmmTextSecondary,
                    fontSize = 15.sp,
                    lineHeight = 21.sp
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = MonoFamily, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = PmmTextPrimary),
                    placeholder = { Text("01I26105252", fontFamily = MonoFamily, fontSize = 17.sp) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { if (texto.isNotBlank()) onSubmit(texto) }),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PmmBlueDeep, cursorColor = PmmBlueDeep),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(texto) },
                enabled = texto.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Usar código", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Cancelar", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// Componentes auxiliares
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun DescargaBottomBar(processing: Boolean, enabled: Boolean, onConfirm: () -> Unit) {
    Surface(color = PmmBgSurface, shadowElevation = 10.dp) {
        Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Button(
                onClick = onConfirm,
                enabled = enabled && !processing,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmNavyDark)
            ) {
                if (processing) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = PmmNavyDark)
                } else {
                    Text("Confirmar re-escaneo", fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1)
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
        Text("Buscando la relación…", color = PmmTextSecondary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                Text("ESCANEO DETENIDO", color = PmmRedDark, fontSize = 14.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(4.dp))
                Text("Relación bloqueada", color = Color(0xFF7F1D1D), fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = b?.motivo ?: "Escaneo rápido detectado. Contacta al supervisor.",
                    color = Color(0xFF4B1F1F),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Volver al inicio", fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
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
            Text("Carga terminada", color = PmmTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (state.mode == SalidaMode.SIN_RELACION) {
                    "La entrada ${state.relacion?.relno.orEmpty()} quedó cargada."
                } else {
                    "La relación ${state.relacion?.relno.orEmpty()} quedó cargada y espera la autorización del supervisor."
                },
                color = PmmTextSecondary,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onNueva,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Nueva carga", fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
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
                Text(confirm.title, color = PmmTextPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text(confirm.message, color = PmmTextSecondary, fontSize = 16.sp, lineHeight = 23.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Volver", fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Button(
                        onClick = onConfirm,
                        enabled = !processing,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                    ) {
                        if (processing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text(
                                confirm.confirmLabel,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 18.sp
                            )
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
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
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
            Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = PmmAmber, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text = text,
                color = Color(0xFF92400E),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
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
