package com.example.pmmovil.ui.salida.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.ui.salida.SalidaCaptureStep
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

private val MonoFamily = FontFamily.Monospace

enum class SheetDisplayMode {
    PEEK,     // Colapsado: la cámara domina el 65% de la pantalla, sheet muestra HUD y controles
    HALF,     // Mitad: 50% cámara y 50% lista
    EXPANDED  // Expandido: auditoría completa de bultos
}

/**
 * Persistent Bottom Sheet siguiendo los lineamientos de Google Material 3 y Scandit.
 */
@Composable
fun SalidaPersistentBottomSheet(
    sheetMode: SheetDisplayMode,
    onToggleSheetMode: () -> Unit,
    onSetSheetMode: (SheetDisplayMode) -> Unit,
    sheetHeight: Dp,
    escaneados: Int,
    faltan: Int,
    total: Int,
    progreso: Float,
    lastScanned: String?,
    isProcessingScan: Boolean,
    captureStep: SalidaCaptureStep,
    casetaQrSecondsRemaining: Int,
    etiquetas: List<SalidaEtiqueta>,
    todoEscaneado: Boolean,
    isProcessingAction: Boolean,
    onManualSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onFinalize: () -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilterTab by remember { mutableIntStateOf(0) } // 0: Faltan, 1: Listos, 2: Todos
    var showManualInput by remember { mutableStateOf(false) }

    val filteredList = remember(etiquetas, searchQuery, selectedFilterTab) {
        val baseList = when (selectedFilterTab) {
            0 -> etiquetas.filter { !it.escaneado }
            1 -> etiquetas.filter { it.escaneado }.reversed()
            else -> etiquetas
        }
        if (searchQuery.isBlank()) {
            baseList
        } else {
            val q = searchQuery.trim().lowercase()
            baseList.filter {
                it.textoEtiqueta.lowercase().contains(q) ||
                it.textoPagina.lowercase().contains(q) ||
                it.creferencia.lowercase().contains(q)
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(sheetHeight),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = PmmBgPrimary,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Manija superior deslizable (Drag Handle)
            SheetDragHandle(
                sheetMode = sheetMode,
                onToggle = onToggleSheetMode,
                onDragUp = {
                    if (sheetMode == SheetDisplayMode.PEEK) onSetSheetMode(SheetDisplayMode.HALF)
                    else if (sheetMode == SheetDisplayMode.HALF) onSetSheetMode(SheetDisplayMode.EXPANDED)
                },
                onDragDown = {
                    if (sheetMode == SheetDisplayMode.EXPANDED) onSetSheetMode(SheetDisplayMode.HALF)
                    else if (sheetMode == SheetDisplayMode.HALF) onSetSheetMode(SheetDisplayMode.PEEK)
                }
            )

            // Contenido principal del Sheet
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // KPI Compacto integrado en el sheet
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$escaneados de $total Cargados",
                        fontFamily = MonoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = PmmBlueDeep
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (faltan > 0) PmmAmber.copy(alpha = 0.15f) else PmmGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (faltan > 0) "Faltan $faltan" else "¡Listo!",
                            color = if (faltan > 0) PmmAmber else PmmGreenDark,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Barra de progreso ultra-slim
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progreso.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (todoEscaneado) PmmGreen else PmmYellow,
                    trackColor = PmmNavyVariant
                )

                // HUD del Último Escaneo (Feedback visual instantáneo compacto)
                LastScanHudCard(
                    processing = isProcessingScan,
                    lastScanned = lastScanned,
                    captureStep = captureStep,
                    secondsRemaining = casetaQrSecondsRemaining
                )

                // Pestañas de Filtro (Siempre visibles)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(PmmNavyVariant)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val countFaltan = etiquetas.count { !it.escaneado }
                    val countListos = etiquetas.count { it.escaneado }

                    SheetFilterPill("Faltan", countFaltan, PmmAmber, selected = selectedFilterTab == 0, onClick = { selectedFilterTab = 0 }, modifier = Modifier.weight(1f))
                    SheetFilterPill("Listos", countListos, PmmGreen, selected = selectedFilterTab == 1, onClick = { selectedFilterTab = 1 }, modifier = Modifier.weight(1f))
                    SheetFilterPill("Todos", etiquetas.size, PmmBlueDeep, selected = selectedFilterTab == 2, onClick = { selectedFilterTab = 2 }, modifier = Modifier.weight(1f))
                }

                // Buscador si la lista está expandida
                if (sheetMode == SheetDisplayMode.EXPANDED) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        placeholder = { Text("Buscar bulto o guía...", fontSize = 12.sp, color = PmmTextMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = PmmTextSecondary, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Limpiar", tint = PmmTextSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = PmmBgSurface,
                            unfocusedContainerColor = PmmBgSurface,
                            focusedBorderColor = PmmBlueDeep,
                            unfocusedBorderColor = PmmNavyBorder
                        ),
                        textStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    )
                }

                // Lista de bultos (Siempre visible en el BottomSheet)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = PmmBgSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder)
                ) {
                    if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "Sin coincidencias"
                                else if (selectedFilterTab == 0) "¡Excelente! No faltan bultos"
                                else "Aún no hay bultos registrados",
                                color = PmmTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(items = filteredList, key = { it.textoEtiqueta }) { etiqueta ->
                                SheetEtiquetaItem(etiqueta = etiqueta)
                            }
                        }
                    }
                }

                // Entrada manual colapsable
                AnimatedVisibility(visible = showManualInput) {
                    ManualScanRow(
                        onSubmit = {
                            onManualSubmit(it)
                            showManualInput = false
                        },
                        onClose = { showManualInput = false }
                    )
                }

                // Barra de Acciones y Finalizar Carga
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!showManualInput) {
                        IconButton(
                            onClick = { showManualInput = true },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PmmBgSurface)
                                .border(1.dp, PmmNavyBorder, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Filled.Keyboard, contentDescription = "Captura manual", tint = PmmTextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }

                    OutlinedButton(
                        onClick = onPause,
                        enabled = !isProcessingAction,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmBlueDeep)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pausar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Button(
                        onClick = onFinalize,
                        enabled = !isProcessingAction,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (todoEscaneado) PmmGreen else PmmBlueDeep,
                            contentColor = Color.White
                        )
                    ) {
                        if (isProcessingAction) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (todoEscaneado) "Finalizar Carga" else "Terminar",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Manija de arrastre con indicador de dirección y detector de gestos.
 */
@Composable
private fun SheetDragHandle(
    sheetMode: SheetDisplayMode,
    onToggle: () -> Unit,
    onDragUp: () -> Unit,
    onDragDown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    if (dragAmount < -15f) onDragUp()
                    else if (dragAmount > 15f) onDragDown()
                }
            }
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PmmTextMuted.copy(alpha = 0.6f))
            )
            Spacer(Modifier.height(3.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (sheetMode == SheetDisplayMode.EXPANDED) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                    contentDescription = null,
                    tint = PmmTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = when (sheetMode) {
                        SheetDisplayMode.PEEK -> "Desliza para ver la lista de bultos"
                        SheetDisplayMode.HALF -> "Toca para pantalla completa"
                        SheetDisplayMode.EXPANDED -> "Desliza hacia abajo para escanear"
                    },
                    color = PmmTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Tarjeta de Último Escaneo con alto contraste para confirmación sin mirar la lista.
 */
@Composable
private fun LastScanHudCard(
    processing: Boolean,
    lastScanned: String?,
    captureStep: SalidaCaptureStep,
    secondsRemaining: Int
) {
    val esperandoQr = captureStep == SalidaCaptureStep.CASETA_QR
    val hasLast = !esperandoQr && !lastScanned.isNullOrBlank()

    val cardBg = when {
        processing -> PmmBlueDeep
        hasLast -> PmmGreenDark
        esperandoQr -> PmmAmber
        else -> PmmNavyDark
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardBg,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    processing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = PmmYellow)
                    hasLast -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                    esperandoQr -> Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    else -> Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = PmmYellow, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        processing -> "REGISTRANDO BULTO..."
                        hasLast -> "ÚLTIMO BULTO OK"
                        esperandoQr -> "ESCANEA QR DE CASETA"
                        else -> "LISTO PARA ESCANEAR"
                    },
                    color = if (hasLast && !processing) PmmYellow else Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )

                Spacer(Modifier.height(2.dp))

                Text(
                    text = when {
                        esperandoQr -> "Enfoca la etiqueta dentro de la caja"
                        lastScanned?.isNotBlank() == true -> lastScanned
                        else -> "Apunta al código o usa el lector"
                    },
                    color = Color.White,
                    fontSize = if (hasLast) 15.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (hasLast) MonoFamily else FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!esperandoQr && secondsRemaining > 0) {
                    Text(
                        text = "Tiempo restante para bulto: ${secondsRemaining}s",
                        color = if (secondsRemaining <= 3) PmmYellow else Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Píldora de pestaña de filtro.
 */
@Composable
private fun SheetFilterPill(
    label: String,
    count: Int,
    accent: Color,
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
            modifier = Modifier.padding(vertical = 7.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = if (selected) PmmTextPrimary else PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) accent else accent.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "$count",
                    color = if (selected) Color.White else accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = MonoFamily
                )
            }
        }
    }
}

/**
 * Fila de bulto individual con badges claros de estado.
 */
@Composable
private fun SheetEtiquetaItem(etiqueta: SalidaEtiqueta) {
    val scanned = etiqueta.escaneado
    val bg = if (scanned) Color(0xFFF0FDF4) else Color(0xFFFEFCE8)
    val border = if (scanned) PmmGreen.copy(alpha = 0.35f) else PmmAmber.copy(alpha = 0.40f)
    val dotColor = if (scanned) PmmGreen else PmmAmber
    val textColor = if (scanned) PmmGreenDark else Color(0xFF713F12)

    Surface(
        color = bg,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (scanned) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = dotColor, modifier = Modifier.size(16.dp))
            } else {
                Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            }

            Spacer(Modifier.width(9.dp))

            Text(
                text = etiqueta.textoPagina.ifBlank { etiqueta.textoEtiqueta },
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MonoFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            val ts = etiqueta.timeScan
            if (scanned && !ts.isNullOrBlank()) {
                val formattedTime = if (ts.contains('T')) ts.substringAfter('T').take(5) else ts.take(5)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(PmmGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = formattedTime,
                        color = PmmGreenDark,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MonoFamily
                    )
                }
            }
        }
    }
}

/**
 * Fila de captura manual con teclado.
 */
@Composable
private fun ManualScanRow(
    onSubmit: (String) -> Unit,
    onClose: () -> Unit
) {
    var value by remember { mutableStateOf("") }
    val submitAction = {
        if (value.isNotBlank()) {
            onSubmit(value.trim())
            value = ""
        }
    }

    Surface(
        color = PmmBgSurface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PmmBlueDeep)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe el código...", fontSize = 12.sp) },
                singleLine = true,
                textStyle = TextStyle(fontFamily = MonoFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitAction() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            IconButton(onClick = submitAction, enabled = value.isNotBlank()) {
                Icon(Icons.Filled.Add, contentDescription = "Enviar", tint = if (value.isNotBlank()) PmmBlueDeep else PmmTextMuted)
            }

            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = PmmTextSecondary)
            }
        }
    }
}
