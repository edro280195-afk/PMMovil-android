package com.example.pmmovil.ui.inventory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.repository.RackCensusScanOutcome
import com.example.pmmovil.ui.components.rememberPmmHaptics
import com.example.pmmovil.ui.scanner.ScanProfile
import com.example.pmmovil.ui.scanner.SmartBarcodeCameraView
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.warehouse.WarehouseMapCanvas
import com.example.pmmovil.ui.warehouse.WarehouseMapViewModel

/**
 * Censo de racks: escanear ubicación → barrer etiquetas → conciliar.
 *
 * El `UBICA` impreso en la etiqueta no se lee en ningún paso: la ubicación se
 * identifica con el QR fijo del rack o confirmando la zona en el mapa.
 */
@Composable
fun RackCensusScreen(
    onNavigateBack: () -> Unit,
    viewModel: RackCensusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(enabled = state.step != RackCensusStep.LOCATION) {
        if (state.step == RackCensusStep.RECONCILE) viewModel.backToSweep() else onNavigateBack()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = PmmBgPrimary) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            RackCensusHeader(
                step = state.step,
                locations = state.selectedLocations,
                onBack = {
                    if (state.step == RackCensusStep.RECONCILE) viewModel.backToSweep() else onNavigateBack()
                }
            )
            state.error?.let { MessageBanner(it, PmmRed) { viewModel.consumeMessages() } }
            state.info?.let { MessageBanner(it, PmmGreen) { viewModel.consumeMessages() } }

            when (state.step) {
                RackCensusStep.LOCATION -> LocationStep(state, viewModel, Modifier.weight(1f))
                RackCensusStep.SWEEP -> SweepStep(state, viewModel, Modifier.weight(1f))
                RackCensusStep.RECONCILE -> RackCensusReconcileContent(state, viewModel, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RackCensusHeader(
    step: RackCensusStep,
    locations: List<String>,
    onBack: () -> Unit
) {
    Surface(color = PmmBgSurface, shadowElevation = 2.dp) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = PmmTextPrimary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Censo de racks", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PmmTextPrimary)
                    Text(
                        text = when (step) {
                            RackCensusStep.LOCATION -> "Paso 1 de 3 · Identificar la ubicación"
                            RackCensusStep.SWEEP -> "Paso 2 de 3 · Barrer etiquetas"
                            RackCensusStep.RECONCILE -> "Paso 3 de 3 · Conciliar"
                        },
                        fontSize = 13.sp, color = PmmTextSecondary
                    )
                }
                if (locations.isNotEmpty() && step != RackCensusStep.LOCATION) {
                    Text(
                        text = locations.joinToString(", "),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PmmBlueDeep,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBanner(message: String, accent: Color, onDismiss: () -> Unit) {
    Surface(color = accent.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, fontSize = 13.sp, color = PmmTextPrimary, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Entendido", color = accent, fontSize = 13.sp) }
        }
    }
}

// ── Paso 1 ───────────────────────────────────────────────────────────────────

@Composable
private fun LocationStep(
    state: RackCensusUiState,
    viewModel: RackCensusViewModel,
    modifier: Modifier = Modifier
) {
    var scanning by remember { mutableStateOf(false) }
    val mapViewModel: WarehouseMapViewModel = hiltViewModel()
    val mapState by mapViewModel.uiState.collectAsState()

    if (scanning) {
        BackHandler { scanning = false }
        SmartBarcodeCameraView(
            onBarcodeScanned = { value ->
                scanning = false
                viewModel.onLocationScanned(value)
            },
            title = "QR de la ubicación",
            subtitle = "Enfoca el QR fijo del rack",
            barcodeFormats = ScanProfile.QrOnly.formats,
            continuous = false,
            minConsecutiveReads = 2,
            showClose = true,
            onClose = { scanning = false },
            modifier = modifier.fillMaxSize()
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.activeSessions.isNotEmpty()) {
            item {
                SectionCard("Recorridos sin terminar") {
                    state.activeSessions.forEach { session ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.scopeJson.trim('[', ']').replace("\"", ""),
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PmmTextPrimary
                                )
                                Text(
                                    text = "Estado: ${session.status}",
                                    fontSize = 12.sp, color = PmmTextSecondary
                                )
                            }
                            OutlinedButton(onClick = { viewModel.resumeSession(session.sessionId) }) {
                                Text("Continuar")
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Identificar la ubicación") {
                Text(
                    text = "Escanea el QR fijo del rack, o selecciónalo en el mapa y confírmalo. " +
                        "La ubicación impresa en la etiqueta de mercancía no se usa: puede estar desactualizada.",
                    fontSize = 13.sp, color = PmmTextSecondary
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { scanning = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Escanear QR del rack", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                if (state.selectedLocations.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Ubicaciones confirmadas", fontSize = 12.sp, color = PmmTextMuted)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.selectedLocations.forEach { location ->
                            Surface(
                                color = PmmBlueDeep.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(location, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PmmBlueDeep)
                                    IconButton(
                                        onClick = { viewModel.toggleLocation(location) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close, contentDescription = "Quitar $location",
                                            tint = PmmBlueDeep, modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard("Mapa de bodega") {
                val layout = mapState.layout
                when {
                    mapState.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Cargando el mapa...", fontSize = 13.sp, color = PmmTextSecondary)
                    }
                    layout == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Map, contentDescription = null, tint = PmmTextMuted)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = mapState.error ?: state.zonesError ?: "El mapa de bodega no está disponible.",
                            fontSize = 13.sp, color = PmmTextSecondary
                        )
                    }
                    else -> Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        WarehouseMapCanvas(
                            layout = layout,
                            selectedZoneId = state.selectedLocations.lastOrNull().orEmpty(),
                            occupancy = mapState.occupancy,
                            claseModels = mapState.claseModels,
                            onZoneTap = { zone -> viewModel.toggleLocation(zone.id) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = { viewModel.openSession() },
                enabled = state.canOpenCensus && state.selectedLocations.isNotEmpty() && !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmGreen)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        text = if (state.canOpenCensus) "Abrir censo con instantánea"
                        else "Sin permiso para abrir censos",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// ── Paso 2 ───────────────────────────────────────────────────────────────────

@Composable
private fun SweepStep(
    state: RackCensusUiState,
    viewModel: RackCensusViewModel,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPmmHaptics()
    // La clave lleva el contador: sin él, dos lecturas iguales seguidas no
    // vuelven a disparar el aviso (el mismo bug que se corrigió en escaneo).
    LaunchedEffect(state.feedbackToken) {
        val feedback = state.lastFeedback ?: return@LaunchedEffect
        when (feedback.outcome) {
            RackCensusScanOutcome.NEW_IN_PLACE -> haptics.confirm()
            RackCensusScanOutcome.ALREADY_SEEN -> haptics.tap()
            else -> haptics.reject()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
            SmartBarcodeCameraView(
                onBarcodeScanned = { value -> viewModel.onLabelScanned(value) },
                title = "Barrido de etiquetas",
                subtitle = "Enfoca el QR de cada bulto",
                barcodeFormats = ScanProfile.QrOnly.formats,
                continuous = true,
                scanCooldownMillis = 600L,
                minConsecutiveReads = 2,
                showClose = false,
                compact = true,
                showStatusOverlay = false,
                modifier = Modifier.fillMaxSize()
            )
        }

        state.lastFeedback?.let { feedback -> ScanFeedbackRow(feedback) }

        CountersRow(state)

        val codes = state.seenCodes.asReversed()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (codes.isEmpty()) {
                item {
                    Text(
                        text = "Las lecturas aparecen aquí marcadas. Repetir una etiqueta no la cuenta dos veces.",
                        fontSize = 13.sp, color = PmmTextSecondary
                    )
                }
            }
            items(codes, key = { it.normalizedCode + it.locationId }) { code ->
                SeenCodeRow(code)
            }
        }

        Surface(color = PmmBgSurface, shadowElevation = 8.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (state.pendingSync > 0) {
                    OutlinedButton(onClick = { viewModel.syncNow() }) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${state.pendingSync} por subir", fontSize = 13.sp)
                    }
                }
                Button(
                    onClick = { viewModel.goToReconcile() },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Text("Terminar barrido y conciliar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScanFeedbackRow(feedback: com.example.pmmovil.data.repository.RackCensusScanFeedback) {
    val accent = when (feedback.outcome) {
        RackCensusScanOutcome.NEW_IN_PLACE -> PmmGreen
        RackCensusScanOutcome.NEW_ELSEWHERE, RackCensusScanOutcome.CONFLICTING_LOCATION -> PmmAmber
        RackCensusScanOutcome.ALREADY_SEEN -> PmmTextMuted
        else -> PmmRed
    }
    Surface(color = accent.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (feedback.outcome == RackCensusScanOutcome.NEW_IN_PLACE) Icons.Default.CheckCircle
                else Icons.Default.ErrorOutline,
                contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = feedback.normalizedCode.ifBlank { "Lectura ilegible" },
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace, color = PmmTextPrimary
                )
                if (feedback.message.isNotBlank()) {
                    Text(feedback.message, fontSize = 12.sp, color = PmmTextSecondary)
                }
            }
        }
    }
}

@Composable
private fun CountersRow(state: RackCensusUiState) {
    val report = state.report
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Counter("Esperados", state.expectedInScope.toString(), PmmTextSecondary, Modifier.weight(1f))
        Counter("Vistos", state.uniqueObserved.toString(), PmmGreen, Modifier.weight(1f))
        Counter("Otra ubicación", (report?.counts?.foundElsewhere ?: 0).toString(), PmmAmber, Modifier.weight(1f))
        Counter("Sin identificar", (report?.counts?.unidentifiedCodes ?: 0).toString(), PmmRed, Modifier.weight(1f))
    }
}

@Composable
private fun Counter(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier, color = PmmBgSurface, shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accent)
            Text(label, fontSize = 10.sp, color = PmmTextMuted)
        }
    }
}

@Composable
private fun SeenCodeRow(code: RackCensusSeenCode) {
    val accent = if (code.resolved) PmmGreen else PmmRed
    Surface(color = PmmBgSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = code.normalizedCode,
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = PmmTextPrimary
                )
                Text(
                    text = if (code.resolved) "Identificado en ${code.locationId}"
                    else "Sin identificar en ${code.locationId}",
                    fontSize = 11.sp, color = PmmTextSecondary
                )
            }
            if (!code.synced) {
                Icon(
                    Icons.Default.Sync, contentDescription = "Pendiente de subir",
                    tint = PmmTextMuted, modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(), color = PmmBgSurface, shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PmmTextPrimary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
