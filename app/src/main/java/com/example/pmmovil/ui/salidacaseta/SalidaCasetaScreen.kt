package com.example.pmmovil.ui.salidacaseta

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.pmmovil.data.model.SalidaCarril
import com.example.pmmovil.data.model.SalidaPatioItem
import com.example.pmmovil.ui.camera.LocalEvidenceCameraScreen
import com.example.pmmovil.ui.camera.LocalEvidencePhoto
import com.example.pmmovil.ui.components.AnimatedCounter
import com.example.pmmovil.ui.components.PmmSnackbarHost
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.components.pmmTap
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import kotlinx.coroutines.delay

internal val Mono = FontFamily.Monospace

/**
 * Salida de caseta, una sola pantalla.
 *
 * Cada caja del patio vive en un carril (lista / sin relación / en proceso / por
 * cerrar) decidido por el servidor; la pantalla solo ofrece la acción que le
 * corresponde a ese carril. Reemplaza a «Salida de relación» y «Salida física».
 */
@OptIn(ExperimentalAnimationApi::class)
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
    var cameraVisible by rememberSaveable { mutableStateOf(false) }

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

    // El patio cambia solo (alguien autoriza una relación, otro guardia da una
    // salida): se vuelve a leer cada 30 s mientras la pantalla está a la vista.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(30_000)
                viewModel.refresh(silencioso = true)
            }
        }
    }

    // En teléfono el detalle ocupa toda la pantalla; en tableta convive con la lista.
    // En ambos casos «atrás» primero cierra el detalle.
    BackHandler(enabled = state.selectedId != null && !cameraVisible) { viewModel.closeDetail() }

    val seleccionado = state.selected
    if (cameraVisible && seleccionado != null) {
        val fotos = state.fotosDe(seleccionado)
        LocalEvidenceCameraScreen(
            title = "Fotografías de salida",
            subtitle = "Evidencia de la unidad y los sellos",
            photos = fotos.mapIndexed { i, p ->
                LocalEvidencePhoto(
                    id = p,
                    model = File(p),
                    label = "Foto ${i + 1}",
                    status = "Lista",
                    canDelete = state.relBorrar
                )
            },
            // La petición lleva todas las fotos juntas; sin tope un solo envío
            // podía pesar decenas de MB justo al confirmar.
            maxPhotos = 20,
            directoryName = "salidas",
            filePrefix = "salida_",
            canCapture = state.relAgregar,
            onPhotoCaptured = viewModel::addPhoto,
            onDeletePhoto = { photo -> viewModel.removePhoto(photo.id) },
            onClose = { cameraVisible = false }
        )
        return
    }

    SalidaCasetaContenido(
        state = state,
        snackbar = snackbar,
        acciones = SalidaAcciones(
            onBack = onNavigateBack,
            onScan = onNavigateToScanner,
            onRefresh = { viewModel.refresh() },
            onQuery = viewModel::setQuery,
            onClearQuery = viewModel::clearQuery,
            onCarril = viewModel::setCarrilFiltro,
            onSelect = viewModel::select,
            onCloseDetail = viewModel::closeDetail,
            onObservaciones = viewModel::updateObservaciones,
            onAbrirCamara = { cameraVisible = true },
            onQuitarFoto = viewModel::removePhoto,
            onConfirmar = viewModel::requestConfirm,
            onDismissConfirm = viewModel::dismissConfirm,
            onConfirm = viewModel::confirm
        )
    )
}

/** Acciones que la pantalla puede disparar; así el contenido es puro estado y se puede probar sin ViewModel. */
internal class SalidaAcciones(
    val onBack: () -> Unit,
    val onScan: () -> Unit,
    val onRefresh: () -> Unit,
    val onQuery: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onCarril: (SalidaCarril?) -> Unit,
    val onSelect: (String) -> Unit,
    val onCloseDetail: () -> Unit,
    val onObservaciones: (String) -> Unit,
    val onAbrirCamara: () -> Unit,
    val onQuitarFoto: (String) -> Unit,
    val onConfirmar: () -> Unit,
    val onDismissConfirm: () -> Unit,
    val onConfirm: () -> Unit
)

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun SalidaCasetaContenido(
    state: SalidaUiState,
    snackbar: SnackbarHostState,
    acciones: SalidaAcciones
) {
    Scaffold(
        containerColor = PmmBgPrimary,
        snackbarHost = { PmmSnackbarHost(snackbar) },
        topBar = {
            BoxWithConstraints {
                BarraSuperior(
                    ancha = maxWidth >= 840.dp,
                    subtitulo = if (state.actualizado.isBlank()) "Cajas del patio" else "Actualizado ${state.actualizado}",
                    conteoHoy = state.hoy.conRelacion + state.hoy.manuales,
                    hoyRelacion = state.hoy.conRelacion,
                    hoyManual = state.hoy.manuales,
                    refrescando = state.isRefreshing || state.isLoading,
                    mostrarHoy = state.tieneAcceso,
                    onBack = if (state.selectedId != null && maxWidth < 840.dp) acciones.onCloseDetail else acciones.onBack,
                    onScan = acciones.onScan,
                    onRefresh = acciones.onRefresh
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val ancha = maxWidth >= 840.dp
            if (!state.tieneAcceso) {
                SinAcceso()
            } else {
                val visibles = remember(state.items, state.query, state.carrilFiltro) { state.visibles() }
                if (ancha) {
                    Column(Modifier.fillMaxSize()) {
                        BarraFiltros(state, acciones.onQuery, acciones.onClearQuery, acciones.onCarril, ancha = true)
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ListaPatio(
                                state = state,
                                visibles = visibles,
                                onOpen = acciones.onSelect,
                                modifier = Modifier
                                    .width(420.dp)
                                    .fillMaxHeight()
                            )
                            PanelDetalle(
                                state = state,
                                ancha = true,
                                onObservaciones = acciones.onObservaciones,
                                onAbrirCamara = acciones.onAbrirCamara,
                                onQuitarFoto = acciones.onQuitarFoto,
                                onConfirmar = acciones.onConfirmar,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = state.selectedId != null,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) { it / 3 } + fadeIn(tween(Motion.DurationMedium))) togetherWith
                                    (slideOutHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedAccelerate)) { -it / 6 } + fadeOut(tween(Motion.DurationQuick)))
                            } else {
                                (slideInHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) { -it / 6 } + fadeIn(tween(Motion.DurationMedium))) togetherWith
                                    (slideOutHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedAccelerate)) { it / 3 } + fadeOut(tween(Motion.DurationQuick)))
                            }
                        },
                        label = "telefono-lista-detalle"
                    ) { enDetalle ->
                        if (enDetalle) {
                            PanelDetalle(
                                state = state,
                                ancha = false,
                                onObservaciones = acciones.onObservaciones,
                                onAbrirCamara = acciones.onAbrirCamara,
                                onQuitarFoto = acciones.onQuitarFoto,
                                onConfirmar = acciones.onConfirmar,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                BarraFiltros(state, acciones.onQuery, acciones.onClearQuery, acciones.onCarril, ancha = false)
                                ListaPatio(
                                    state = state,
                                    visibles = visibles,
                                    onOpen = acciones.onSelect,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (state.isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.62f))
                        .pointerInput(Unit) { detectTapGestures { } },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PmmBlueDeep)
                }
            }
        }
    }

    val aConfirmar = state.selected
    if (state.confirmVisible && aConfirmar != null) {
        DialogoConfirmar(
            item = aConfirmar,
            observacion = state.observacionDe(aConfirmar),
            fotos = state.fotosDe(aConfirmar).size,
            onDismiss = acciones.onDismissConfirm,
            onConfirm = acciones.onConfirm
        )
    }
}


// ── Barra superior ───────────────────────────────────────────────────────────

@Composable
private fun BarraSuperior(
    ancha: Boolean,
    subtitulo: String,
    conteoHoy: Int,
    hoyRelacion: Int,
    hoyManual: Int,
    refrescando: Boolean,
    mostrarHoy: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(PmmNavyDark, PmmBlueDeep)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .pmmTap(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Salida de caseta", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(subtitulo, color = Color(0xFFB9C9E8), fontSize = 11.sp, maxLines = 1)
            }
            if (ancha && mostrarHoy) {
                ResumenHoy(conteoHoy, hoyRelacion, hoyManual)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(PmmYellow)
                    .pmmTap(onClick = onScan)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "Escanear", tint = Color(0xFF1B1500), modifier = Modifier.size(19.dp))
                if (ancha) Text("Escanear QR", color = Color(0xFF1B1500), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
            }
            val giro = if (refrescando) {
                val infinite = rememberInfiniteTransition(label = "refrescar")
                infinite.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
                    label = "giro"
                ).value
            } else 0f
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = Color.White, modifier = Modifier.rotate(giro))
            }
        }
    }
}

@Composable
private fun ResumenHoy(total: Int, conRelacion: Int, manuales: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ResumenHoyCelda(total, "Salidas hoy", Color.White)
        ResumenHoyCelda(conRelacion, "Con relación", Color(0xFF86EFAC))
        ResumenHoyCelda(manuales, "Manuales", Color(0xFFFCD34D))
    }
}

@Composable
private fun ResumenHoyCelda(valor: Int, etiqueta: String, color: Color) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedCounter(
            target = valor,
            style = TextStyle(color = color, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        )
        Text(etiqueta.uppercase(), color = Color(0xFFB9C9E8), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
    }
}

// ── Búsqueda y carriles ──────────────────────────────────────────────────────

@Composable
private fun BarraFiltros(
    state: SalidaUiState,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onCarril: (SalidaCarril?) -> Unit,
    ancha: Boolean
) {
    val chips: @Composable () -> Unit = {
        ChipCarril("Todas", null, state.conteo(null), state.carrilFiltro == null, Color(0xFF0F172A)) { onCarril(null) }
        SalidaCarril.entries.forEach { carril ->
            ChipCarril(
                etiqueta = carril.style().chip,
                carril = carril,
                conteo = state.conteo(carril),
                seleccionado = state.carrilFiltro == carril,
                color = carril.style().color
            ) { onCarril(if (state.carrilFiltro == carril) null else carril) }
        }
    }
    if (ancha) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CampoBusqueda(state.query, onQuery, onClear, Modifier.width(420.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            ResumenHoyTelefono(state)
            CampoBusqueda(state.query, onQuery, onClear, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) { chips() }
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ResumenHoyTelefono(state: SalidaUiState) {
    val total = state.hoy.conRelacion + state.hoy.manuales
    Row(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        AnimatedCounter(target = total, style = TextStyle(color = PmmTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold))
        Text("salidas hoy", color = PmmTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("·  ${state.hoy.conRelacion} con relación  ·  ${state.hoy.manuales} manuales", color = PmmTextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun CampoBusqueda(
    valor: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.5.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(19.dp))
            Box(Modifier.weight(1f)) {
                if (valor.isEmpty()) {
                    Text("Caja, relación, placas, camión o cliente…", color = PmmTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = valor,
                    onValueChange = onChange,
                    singleLine = true,
                    textStyle = TextStyle(color = PmmTextPrimary, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (valor.isNotEmpty()) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Limpiar búsqueda",
                    tint = PmmTextMuted,
                    modifier = Modifier
                        .size(19.dp)
                        .pmmTap(onClick = onClear)
                )
            }
        }
    }
}

@Composable
private fun ChipCarril(
    etiqueta: String,
    carril: SalidaCarril?,
    conteo: Int,
    seleccionado: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    val fondo by animateColorAsState(
        targetValue = if (seleccionado) (carril?.style()?.color ?: color) else PmmBgSurface,
        animationSpec = tween(Motion.DurationMedium),
        label = "chip-fondo"
    )
    val texto by animateColorAsState(
        targetValue = if (seleccionado) Color.White else PmmTextSecondary,
        animationSpec = tween(Motion.DurationMedium),
        label = "chip-texto"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(fondo)
            .pmmTap(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (carril != null) {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(if (seleccionado) Color.White else color)
            )
        }
        Text(etiqueta, color = texto, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(if (seleccionado) Color.White.copy(alpha = 0.22f) else PmmBgCard)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        ) {
            AnimatedCounter(target = conteo, style = TextStyle(color = texto, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold))
        }
    }
}

// ── Lista del patio ──────────────────────────────────────────────────────────
// Sin animaciones por fila a propósito: el patio llega a 100+ cajas y animar cada
// renglón causó jank medido en la tableta (ver memoria compose-listas-largas).

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListaPatio(
    state: SalidaUiState,
    visibles: List<SalidaPatioItem>,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        when {
            state.isLoading -> EsqueletoLista()
            visibles.isEmpty() -> EstadoVacio(filtrado = state.items.isNotEmpty())
            else -> {
                val porCarril = remember(visibles) { visibles.groupBy { it.carrilEnum } }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SalidaCarril.entries.forEach { carril ->
                        val grupo = porCarril[carril].orEmpty()
                        if (grupo.isNotEmpty()) {
                            stickyHeader(key = "h-${carril.clave}") {
                                EncabezadoCarril(carril, grupo.size)
                            }
                            items(grupo, key = { it.id }) { item ->
                                FilaPatio(item, seleccionada = item.id == state.selectedId, onClick = { onOpen(item.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EncabezadoCarril(carril: SalidaCarril, total: Int) {
    val estilo = carril.style()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PmmBgPrimary)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(estilo.color))
        Text(
            "${estilo.encabezado} · $total",
            color = PmmTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp
        )
        Box(Modifier.weight(1f).height(1.dp).background(PmmNavyBorder))
    }
}

@Composable
private fun FilaPatio(item: SalidaPatioItem, seleccionada: Boolean, onClick: () -> Unit) {
    val carril = item.carrilEnum
    val estilo = carril.style()
    val multiple = item.cajas.size > 1
    val primera = item.cajas.firstOrNull()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pmmTap(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (seleccionada) Color(0xFFEFF6FF) else PmmBgSurface,
        border = BorderStroke(if (seleccionada) 2.dp else 1.dp, if (seleccionada) PmmBlueMid else PmmNavyBorder)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(estilo.color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (multiple) "${item.cajas.size} cajas" else etiquetaCaja(primera?.numCaja.orEmpty()),
                        fontFamily = Mono,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        color = PmmTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.tipoOp.isNotBlank()) PildoraTipoOp(item.tipoOp)
                    if (item.esTractocamion) Pildora("Tractocamión", Color(0xFFE2E8F0), PmmTextSecondary)
                    if (item.sinCajaEnCaseta) Pildora("SIN CAJA EN CASETA", Color(0xFFFEF3C7), Color(0xFF92400E))
                }
                val clientes = when {
                    item.clientes.isEmpty() -> "Sin cliente"
                    item.clientes.size == 1 -> item.clientes.first()
                    else -> "${item.clientes.first()} +${item.clientes.size - 1}"
                }
                Text(
                    text = listOf(clientes, primera?.lineaCaja.orEmpty()).filter { it.isNotBlank() }.joinToString(" — "),
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (carril) {
                        SalidaCarril.LISTA -> {
                            Pildora(item.relno, estilo.soft, estilo.ink, mono = true)
                            item.minutosEspera?.let {
                                Text(
                                    "Espera ${formatoMinutos(it)}",
                                    color = if (it > 1440) PmmAmber else PmmGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            if (multiple) Spacer(Modifier.weight(1f))
                            if (multiple) Pildora("${item.cajas.size} cajas", PmmBgCard, PmmTextSecondary)
                        }
                        SalidaCarril.PROCESO -> {
                            Pildora(item.estatusCarga.ifBlank { "SIN CARGA" }, estilo.soft, estilo.ink, mono = true)
                            if (item.bultosTotal > 0) {
                                BarraAvance(item.bultosCargados, item.bultosTotal, Modifier.width(80.dp))
                                Text("${item.bultosCargados}/${item.bultosTotal}", color = PmmTextSecondary, fontSize = 11.sp)
                            }
                        }
                        SalidaCarril.MANUAL -> {
                            Pildora("SIN RELACIÓN", estilo.soft, estilo.ink)
                            Text(
                                item.tipoMovimiento.replace("/", " / ").ifBlank { "—" },
                                color = PmmTextMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Text(
                                if (item.diasEnPatio == 0) "${item.horasEnPatio} h en patio" else "${item.diasEnPatio} d en patio",
                                color = PmmTextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        SalidaCarril.CERRAR -> {
                            Pildora(item.relno, estilo.soft, estilo.ink, mono = true)
                            Text("Salió ${formatoFechaCorta(item.salidaCaseta)}", color = PmmTextSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Pildora(texto: String, fondo: Color, tinta: Color, mono: Boolean = false) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(fondo)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            texto,
            color = tinta,
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = if (mono) Mono else null,
            letterSpacing = 0.3.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun PildoraTipoOp(tipoOp: String) {
    val impo = tipoOp.equals("IMPO", ignoreCase = true)
    Pildora(tipoOp, if (impo) Color(0xFFDBEAFE) else Color(0xFFFFEDD5), if (impo) Color(0xFF1E40AF) else Color(0xFF9A3412))
}

/** Barra fija (sin animar): vive dentro de las filas de la lista. */
@Composable
internal fun BarraAvance(actual: Int, total: Int, modifier: Modifier = Modifier) {
    val fraccion = if (total <= 0) 0f else (actual.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(5.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xFFE2E8F0))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraccion)
                .background(PmmBlueMid)
        )
    }
}

// ── Estados: cargando, vacío, sin acceso ─────────────────────────────────────

@Composable
private fun EsqueletoLista() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        repeat(6) {
            Skeleton(modifier = Modifier.fillMaxWidth(), height = 78.dp, shape = RoundedCornerShape(16.dp))
        }
    }
}

@Composable
private fun EstadoVacio(filtrado: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(44.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (filtrado) "Sin resultados" else "El patio está vacío",
                color = PmmTextPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (filtrado) "Prueba con otra caja, placas, cliente o relación, o quita el filtro de carril."
                else "No hay cajas esperando salida en esta planta.",
                color = PmmTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SinAcceso() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = PmmAmber, modifier = Modifier.size(46.dp))
            Spacer(Modifier.height(12.dp))
            Text("Sin acceso", color = PmmTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tu perfil no tiene permiso de Lectura en la salida con relación ni en la salida manual.",
                color = PmmTextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
