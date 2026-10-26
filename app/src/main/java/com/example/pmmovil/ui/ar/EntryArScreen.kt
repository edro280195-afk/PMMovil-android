package com.example.pmmovil.ui.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pmmovil.data.entryar.ArItemKind
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryArScreen(
    onNavigateBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenReview: (String) -> Unit,
    /** Abre la ficha completa de una caja o de una relación (los QR que no son entradas). */
    onOpenCode: (String) -> Unit = {},
    entryArViewModel: EntryArViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by entryArViewModel.state.collectAsState()
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var useSpatialAr by remember { mutableStateOf<Boolean?>(null) }
    var selectedItemKey by remember { mutableStateOf<String?>(null) }
    var selectedItemSnapshot by remember { mutableStateOf<EntryArOverlayItem?>(null) }
    val liveSelectedItem = uiState.items.firstOrNull { it.key == selectedItemKey }
    val selectedItem = liveSelectedItem ?: selectedItemSnapshot?.takeIf { it.key == selectedItemKey }
    val actionSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(uiState.items, selectedItemKey) {
        if (selectedItemKey != null) {
            uiState.items.firstOrNull { it.key == selectedItemKey }?.let { selectedItemSnapshot = it }
        }
    }
    val arView = remember(context) {
        EntryArSpatialView(
            context = context,
            onQrSeen = entryArViewModel::onSpatialQrSeen,
            onQrObserved = entryArViewModel::onSpatialQrObserved,
            onProjected = entryArViewModel::updateProjectedPosition,
            unavailableListener = { useSpatialAr = false }
        )
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraGranted = granted
    }

    BackHandler(onBack = onNavigateBack)
    val tryStartSpatial = rememberUpdatedState<(Activity) -> Unit> { activity ->
        if (cameraGranted) {
            when (arView.resumeAr(activity)) {
                ArStartResult.STARTED -> useSpatialAr = true
                ArStartResult.INSTALLING -> useSpatialAr = null
                ArStartResult.UNAVAILABLE -> useSpatialAr = false
            }
        }
    }
    val currentCameraGranted = rememberUpdatedState(cameraGranted)

    LaunchedEffect(cameraGranted) {
        if (!cameraGranted) {
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            context.findActivity()?.let { tryStartSpatial.value(it) }
        }
    }

    DisposableEffect(lifecycleOwner, arView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (currentCameraGranted.value) {
                        context.findActivity()?.let { tryStartSpatial.value(it) }
                    }
                }
                Lifecycle.Event.ON_PAUSE -> arView.pauseAr()
                Lifecycle.Event.ON_STOP -> arView.pauseAr()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            arView.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !cameraGranted -> PermissionRequired(
                onRequest = { requestCamera.launch(Manifest.permission.CAMERA) }
            )
            useSpatialAr == true -> AndroidView(
                factory = { arView },
                modifier = Modifier.fillMaxSize()
            )
            useSpatialAr == false -> EntryArAssistedCamera(
                modifier = Modifier.fillMaxSize(),
                onQrSeen = entryArViewModel::onQrSeen
            )
            else -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = androidx.compose.ui.platform.LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val cardWidth = minOf(228.dp, (maxWidth - 32.dp) / 2f).coerceAtLeast(156.dp)
            val cardHeight = ENTRY_AR_CARD_HEIGHT
            val cardWidthPx = with(density) { cardWidth.toPx() }.roundToInt()
            val cardHeightPx = with(density) { cardHeight.toPx() }.roundToInt()
            val edgePx = with(density) { 8.dp.toPx() }.roundToInt()
            val safeTopPx = with(density) { 116.dp.toPx() }.roundToInt()
            val safeBottomPx = with(density) { 36.dp.toPx() }.roundToInt()
            val gapPx = with(density) { 8.dp.toPx() }.roundToInt()
            // Todas las entradas a la vista muestran SU tarjeta, y cada tarjeta va unida a su propio QR por un hilo
            // del mismo color que el marco de ese QR. El color y el hilo son lo que dice cuál es de cuál: sin ellos,
            // seis tarjetas de 228×160 dp no caben en 420×900, el colocador las manda a huecos libres y acaban a
            // ~121 dp de su propio QR (medido en `docs/mockup-ar-anclaje.html`).
            val markerPx = with(density) { QR_MARKER_SIZE.toPx() }
            val slotByKey = remember { mutableMapOf<String, Int>() }
            val activeKeys = uiState.items.mapTo(mutableSetOf()) { it.key }
            slotByKey.keys.retainAll(activeKeys)
            val occupied = mutableListOf<OverlayCardBounds>()

            // Ninguna tarjeta puede taparle el QR a nadie: las marcas (con su chip) reservan su sitio ANTES de
            // repartir las tarjetas. Si no, una tarjeta se pone encima de su propio código y deja de verse qué bulto es.
            val chipPx = with(density) { 30.dp.toPx() }
            uiState.items.forEach { item ->
                // Se reserva solo el CUADRO del QR, no su chip: el chip repite lo que dice la tarjeta, así que puede
                // quedar debajo de una sin perder nada. Reservarlo también dejaba sin huecos libres y las tarjetas
                // acababan encimadas unas sobre otras (64 % de superficie, medido en el mockup).
                occupied += OverlayCardBounds(
                    left = (item.x * widthPx - markerPx / 2f).roundToInt(),
                    top = (item.y * heightPx - markerPx / 2f).roundToInt(),
                    width = markerPx.roundToInt(),
                    height = markerPx.roundToInt(),
                    marker = true
                )
            }

            // Primero se resuelve dónde cae cada tarjeta: los hilos necesitan saberlo antes de dibujarse.
            val colocadas = uiState.items.map { item ->
                val position = resolveCardPosition(
                    preferredLeft = (item.x * widthPx - cardWidthPx / 2f).roundToInt(),
                    preferredTop = (item.y * heightPx + markerPx / 2f + with(density) { 26.dp.toPx() }).roundToInt(),
                    viewportWidth = widthPx.roundToInt(),
                    viewportHeight = heightPx.roundToInt(),
                    cardWidth = cardWidthPx,
                    cardHeight = cardHeightPx,
                    edge = edgePx,
                    safeTop = safeTopPx,
                    safeBottom = safeBottomPx,
                    gap = gapPx,
                    occupied = occupied,
                    previousSlot = slotByKey[item.key]
                )
                slotByKey[item.key] = position.slot
                occupied += OverlayCardBounds(
                    left = position.left,
                    top = position.top,
                    width = cardWidthPx,
                    height = cardHeightPx
                )
                item to position
            }

            // Los hilos van primero para que queden por debajo de marcas y tarjetas.
            val strokePx = with(density) { 3.dp.toPx() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                colocadas.forEach { (item, position) ->
                    val qx = item.x * size.width
                    val qy = item.y * size.height
                    // Punto de la tarjeta más cercano al QR: así el hilo entra por el lado que toca, venga de donde venga.
                    val hx = qx.coerceIn(position.left.toFloat(), (position.left + cardWidthPx).toFloat())
                    val hy = qy.coerceIn(position.top.toFloat(), (position.top + cardHeightPx).toFloat())
                    val dx = hx - qx
                    val dy = hy - qy
                    val largo = kotlin.math.hypot(dx, dy)
                    if (largo < markerPx / 2f) return@forEach
                    val desde = Offset(qx + dx / largo * markerPx / 2f, qy + dy / largo * markerPx / 2f)
                    drawLine(
                        color = markerColor(item.key),
                        start = desde,
                        end = Offset(hx, hy),
                        strokeWidth = if (item.isPrimary) strokePx * 1.4f else strokePx,
                        cap = StrokeCap.Round
                    )
                }
            }

            uiState.items.forEach { item ->
                key(item.key) {
                    val markerOffset by animateIntOffsetAsState(
                        targetValue = IntOffset(
                            (item.x * widthPx - markerPx / 2f).roundToInt(),
                            (item.y * heightPx - markerPx / 2f).roundToInt()
                        ),
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                        label = "posición de marca AR"
                    )
                    EntryArQrMarker(
                        item = item,
                        onClick = {
                            selectedItemSnapshot = item
                            selectedItemKey = item.key
                        },
                        modifier = Modifier.offset { markerOffset }
                    )
                }
            }

            colocadas.forEach { (item, position) ->
                key(item.key) {
                    val animatedOffset by animateIntOffsetAsState(
                        targetValue = IntOffset(position.left, position.top),
                        animationSpec = spring(dampingRatio = 0.92f, stiffness = 520f),
                        label = "posición de tarjeta AR"
                    )
                    EntryArFloatingCard(
                        item = item,
                        offline = uiState.isOffline,
                        width = cardWidth,
                        onClick = {
                            selectedItemSnapshot = item
                            selectedItemKey = item.key
                        },
                        modifier = Modifier.offset { animatedOffset }
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xCC111827))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("AR de entradas", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Entradas, cajas y relaciones · hasta seis QR",
                        color = Color(0xFFD1D5DB),
                        fontSize = 11.sp
                    )
                }
                IconButton(onClick = {
                    arView.clearAnchors()
                    entryArViewModel.clear()
                }) {
                    Icon(Icons.Default.DeleteSweep, "Limpiar anclas", tint = Color.White)
                }
            }

            if (uiState.isOffline) {
                Text(
                    text = uiState.message.orEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xDD374151))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )
            } else if (uiState.isLoading) {
                Row(
                    modifier = Modifier
                        .background(Color(0xCC111827), RoundedCornerShape(bottomEnd = 12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                    Text("Consultando…", color = Color.White, fontSize = 12.sp)
                }
            }

        }

        selectedItem?.let { item ->
            ModalBottomSheet(
                onDismissRequest = {
                    selectedItemKey = null
                    selectedItemSnapshot = null
                },
                sheetState = actionSheetState,
                containerColor = Color(0xFFF8FAFC)
            ) {
                if (item.kind != ArItemKind.Entry) {
                    EntryArCompactActionSheet(
                        item = item,
                        offline = uiState.isOffline,
                        onOpenFicha = {
                            coroutineScope.launch {
                                actionSheetState.hide()
                                selectedItemKey = null
                                selectedItemSnapshot = null
                                onOpenCode(item.code.normalized)
                            }
                        }
                    )
                } else EntryArActionSheet(
                    item = item,
                    offline = uiState.isOffline,
                    onOpenEntry = {
                        coroutineScope.launch {
                            actionSheetState.hide()
                            selectedItemKey = null
                            selectedItemSnapshot = null
                            onOpenEntry(item.code.reference)
                        }
                    },
                    onOpenReview = {
                        coroutineScope.launch {
                            actionSheetState.hide()
                            selectedItemKey = null
                            selectedItemSnapshot = null
                            onOpenReview(item.code.reference)
                        }
                    }
                )
            }
        }
    }
}

@Composable
internal fun EntryArFloatingCard(
    item: EntryArOverlayItem,
    offline: Boolean,
    width: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cached = offline && item.fromCache
    val card = item.card
    // El borde lleva el color de la marca de SU QR, igual que el hilo: es lo que dice cuál tarjeta es de cuál código.
    val borderColor = markerColor(item.key)
    Card(
        modifier = modifier
            .width(width)
            .height(ENTRY_AR_CARD_HEIGHT)
            .alpha(if (cached) 0.96f else 1f)
            .border(
                width = if (item.isPrimary) 4.dp else 3.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (cached) Color(0xFFF1F5F9) else Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isPrimary) 10.dp else 7.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .background(if (cached) Color(0xFF334155) else Color(0xFF12345A))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.code.reference,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (item.kind != ArItemKind.Entry) arKindLabel(item.kind)
                        else if (item.code.printedOrdinal != null && item.code.declaredTotal != null)
                            "Bulto ${item.code.printedOrdinal} de ${item.code.declaredTotal}"
                        else "Carpeta",
                        color = Color(0xFFD5E2F0),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (cached) {
                    Text("CACHÉ", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Bold, fontSize = 8.sp)
                } else if (item.isPrimary) {
                    Surface(color = Color(0xFFFFC928), shape = RoundedCornerShape(50)) {
                        Text(
                            "CENTRO",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            color = Color(0xFF172B45),
                            fontWeight = FontWeight.Black,
                            fontSize = 8.sp
                        )
                    }
                }
            }

            if (item.kind != ArItemKind.Entry) {
                CompactCardBody(item = item, offline = offline)
                return@Column
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 9.dp, top = 5.dp, end = 9.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
            Text(
                if (card.exists) card.customerName.ifBlank { "Cliente sin nombre" } else "Entrada no encontrada",
                color = Color(0xFF172B45),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.description.ifBlank { "Sin descripción disponible" },
                color = Color(0xFF475569),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp), tint = Color(0xFF475569))
                Text(
                    card.location.ifBlank { "Ubicación pendiente" },
                    modifier = Modifier.weight(1f),
                    color = Color(0xFF475569),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.hasHazmat || card.hasDamage) {
                    Icon(Icons.Default.Warning, "Entrada con alertas", modifier = Modifier.size(13.dp), tint = Color(0xFFB45309))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = reviewStatusColor(card.reviewStatus),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        reviewStatusCompactLabel(card.reviewStatus),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "${card.reviewedLines}/${card.totalLines}",
                    color = Color(0xFF475569),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(5.dp))
                Surface(
                    color = if (card.totalInvoices > 0) Color(0xFFFFEAA1) else Color(0xFFE2E8F0),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (card.totalInvoices > 0) "${card.totalInvoices} FACT." else "SIN FACT.",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = Color(0xFF27364B),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun EntryArActionSheet(
    item: EntryArOverlayItem,
    offline: Boolean,
    onOpenEntry: () -> Unit,
    onOpenReview: () -> Unit
) {
    val card = item.card
    val hasInvoices = card.totalInvoices > 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(item.code.reference, color = Color(0xFF12345A), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(
            if (card.exists) card.customerName.ifBlank { "Cliente sin nombre" } else "Entrada no encontrada",
            color = Color(0xFF172B45),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(card.description.ifBlank { "Sin descripción disponible" }, color = Color(0xFF475569), fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = Color(0xFF475569))
            Text(card.location.ifBlank { "Ubicación pendiente" }, color = Color(0xFF475569), fontSize = 12.sp)
        }
        Text(
            "${reviewStatusLabel(card.reviewStatus)} · ${card.reviewedLines}/${card.totalLines} partidas · " +
                "${card.totalInvoices} facturas",
            color = Color(0xFF334155),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        card.arrivalDate?.take(10)?.takeIf { it.isNotBlank() }?.let { date ->
            Text("Fecha de ingreso: $date", color = Color(0xFF64748B), fontSize = 11.sp)
        }
        if (card.hasHazmat || card.hasDamage) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp), tint = Color(0xFFB45309))
                Text(
                    listOfNotNull(
                        if (card.hasHazmat) "Mercancía peligrosa" else null,
                        if (card.hasDamage) "Daño: ${card.damageDescription.ifBlank { "reportado" }}" else null
                    ).joinToString(" · "),
                    color = Color(0xFF92400E),
                    fontSize = 11.sp
                )
            }
        }
        if (offline) {
            Text(
                if (item.fromCache) "Información guardada sin conexión" else "Sin conexión: no hay información guardada",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }

        Button(
            onClick = onOpenEntry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC928), contentColor = Color(0xFF172B45))
        ) {
            Text("Abrir entrada", fontWeight = FontWeight.Bold)
        }
        OutlinedButton(
            onClick = onOpenReview,
            enabled = hasInvoices,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Abrir revisión", fontWeight = FontWeight.Bold)
        }
        if (!hasInvoices) {
            Text(
                "La revisión se habilita cuando la entrada tiene una factura vinculada.",
                color = Color(0xFF64748B),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PermissionRequired(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("La cámara permite leer los QR y colocar sus tarjetas.", color = Color.White)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("Permitir cámara") }
    }
}

/** Tamaño del marco que se pinta sobre el QR. */
private val QR_MARKER_SIZE = 76.dp

/**
 * Alto de la tarjeta. Era 160 dp y con seis a la vista no cabían sin taparse unas a otras y tapar los propios QR:
 * 6 × 194×160 dp no entran en 420×900 dejando cada código despejado. A 120 dp caben seis filas en vez de cuatro.
 * No se quitó ningún dato: se recortó la holgura (cabecera de 44 a 36 y el cuerpo deja de repartir el hueco sobrante).
 */
private val ENTRY_AR_CARD_HEIGHT = 120.dp

/**
 * Colores de identidad de cada QR. No dicen el estado de revisión (eso lo dice el texto del chip): su único trabajo es
 * que se vea de un golpe qué marca va con qué tarjeta, que es justo lo que faltaba.
 */
private val MARKER_COLORS = listOf(
    Color(0xFFEF4444),
    Color(0xFFF59E0B),
    Color(0xFF10B981),
    Color(0xFF3B82F6),
    Color(0xFFA855F7),
    Color(0xFFEC4899)
)

private fun markerColor(key: String): Color =
    MARKER_COLORS[(kotlin.math.abs(key.hashCode().toLong()) % MARKER_COLORS.size).toInt()]

/** Qué dice el chip de un QR: siempre algo, nunca una marca muda. */
private fun markerLabel(item: EntryArOverlayItem): String = when {
    item.code.isFolderLabel -> "Carpeta"
    !item.card.exists -> "No existe"
    else -> reviewStatusCompactLabel(item.card.reviewStatus)
}

/**
 * Marco de color pegado al QR, con un chip encima que lo identifica. Va en TODOS los QR a la vista, tengan o no la
 * tarjeta completa abierta.
 */
@Composable
internal fun EntryArQrMarker(
    item: EntryArOverlayItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = markerColor(item.key)
    Box(modifier = modifier.size(QR_MARKER_SIZE)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (item.isPrimary) 4.dp else 3.dp,
                    color = color,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable(onClick = onClick)
        )
        Text(
            text = item.code.reference + " · " +
                if (item.kind == ArItemKind.Entry) markerLabel(item) else compactMarkerLabel(item),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-26).dp)
                // Sin esto el chip se recorta al ancho del marco (76 dp) y la referencia sale a medias.
                .wrapContentWidth(unbounded = true)
                .background(color, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

private fun reviewStatusLabel(status: String): String = when (status.uppercase()) {
    "EN_PROGRESO" -> "En progreso"
    "COMPLETA" -> "Completa"
    "COMPLETA_CON_DISCREPANCIAS" -> "Completa con diferencias"
    else -> "Sin revisar"
}

private fun reviewStatusCompactLabel(status: String): String = when (status.uppercase()) {
    "EN_PROGRESO" -> "En curso"
    "COMPLETA" -> "Completa"
    "COMPLETA_CON_DISCREPANCIAS" -> "Diferencias"
    else -> "Sin revisar"
}

private fun reviewStatusColor(status: String): Color = when (status.uppercase()) {
    "EN_PROGRESO" -> Color(0xFF2563EB)
    "COMPLETA" -> Color(0xFF15803D)
    "COMPLETA_CON_DISCREPANCIAS" -> Color(0xFFB45309)
    else -> Color(0xFF475569)
}

private data class OverlayCardBounds(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    /** true si es el marco de un QR. Tapar un código es peor que encimar dos tarjetas. */
    val marker: Boolean = false
)

private data class OverlayCardPosition(
    val slot: Int,
    val left: Int,
    val top: Int
)

private data class OverlayCandidate(
    val slot: Int,
    val left: Int,
    val top: Int
)

private fun resolveCardPosition(
    preferredLeft: Int,
    preferredTop: Int,
    viewportWidth: Int,
    viewportHeight: Int,
    cardWidth: Int,
    cardHeight: Int,
    edge: Int,
    safeTop: Int,
    safeBottom: Int,
    gap: Int,
    occupied: List<OverlayCardBounds>,
    previousSlot: Int?
): OverlayCardPosition {
    val minLeft = edge
    val maxLeft = (viewportWidth - cardWidth - edge).coerceAtLeast(minLeft)
    val minTop = safeTop
    val maxTop = (viewportHeight - cardHeight - safeBottom).coerceAtLeast(minTop)
    val horizontalStep = cardWidth + gap
    val verticalStep = cardHeight + gap
    val offsets = listOf(
        0 to 0,
        0 to -1, 0 to 1, 1 to 0, -1 to 0,
        1 to -1, -1 to -1, 1 to 1, -1 to 1,
        0 to -2, 0 to 2, 2 to 0, -2 to 0,
        1 to -2, -1 to -2, 1 to 2, -1 to 2,
        2 to -1, -2 to -1, 2 to 1, -2 to 1
    )
    val relativeCandidates = offsets.mapIndexed { index, (column, row) ->
        OverlayCandidate(
            slot = index,
            left = (preferredLeft + column * horizontalStep).coerceIn(minLeft, maxLeft),
            top = (preferredTop + row * verticalStep).coerceIn(minTop, maxTop)
        )
    }.distinctBy { it.left to it.top }

    val columns = ((viewportWidth - edge * 2 + gap) / horizontalStep).coerceAtLeast(1)
    val rows = ((maxTop - minTop + verticalStep) / verticalStep).coerceAtLeast(1)
    val gridCandidates = (0 until rows).flatMap { row ->
        (0 until columns).map { column ->
            OverlayCandidate(
                slot = GRID_SLOT_BASE + row * columns + column,
                left = (edge + column * horizontalStep).coerceIn(minLeft, maxLeft),
                top = (minTop + row * verticalStep).coerceIn(minTop, maxTop)
            )
        }
    }.distinctBy { it.left to it.top }

    fun isAvailable(candidate: OverlayCandidate): Boolean = occupied.none { other ->
        candidate.left < other.left + other.width + gap &&
            candidate.left + cardWidth + gap > other.left &&
            candidate.top < other.top + other.height + gap &&
            candidate.top + cardHeight + gap > other.top
    }

    val previousCandidate = previousSlot?.let { slot ->
        (relativeCandidates + gridCandidates).firstOrNull { it.slot == slot }
    }
    // Cuando no queda ningún hueco libre (seis tarjetas y seis marcas no caben en una pantalla de teléfono) hay que
    // encimar algo. Se elige qué: antes se caía en la posición preferida a ciegas, que está justo sobre el QR y lo
    // tapaba. Ahora se prefiere el sitio que MENOS superficie de marcas tape, aunque se encime con otra tarjeta:
    // dos tarjetas encimadas se leen (y el hilo dice de quién es cada una); un QR tapado no se ve.
    fun markerOverlap(candidate: OverlayCandidate): Long {
        var area = 0L
        for (other in occupied) {
            if (!other.marker) continue
            val w = minOf(candidate.left + cardWidth, other.left + other.width) - maxOf(candidate.left, other.left)
            val h = minOf(candidate.top + cardHeight, other.top + other.height) - maxOf(candidate.top, other.top)
            if (w > 0 && h > 0) area += w.toLong() * h.toLong()
        }
        return area
    }

    val selected = previousCandidate?.takeIf(::isAvailable)
        ?: relativeCandidates.firstOrNull(::isAvailable)
        ?: gridCandidates
            .sortedBy { candidate ->
                val dx = candidate.left - preferredLeft
                val dy = candidate.top - preferredTop
                dx * dx + dy * dy
            }
            .firstOrNull(::isAvailable)
        ?: (relativeCandidates + gridCandidates).minByOrNull { candidate ->
            val dx = (candidate.left - preferredLeft).toLong()
            val dy = (candidate.top - preferredTop).toLong()
            // Primero no tapar marcas; a igualdad, lo más cerca posible de su propio QR.
            markerOverlap(candidate) * 1_000_000L + dx * dx + dy * dy
        }
        ?: relativeCandidates.first()

    return OverlayCardPosition(selected.slot, selected.left, selected.top)
}

private const val GRID_SLOT_BASE = 1_000

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

