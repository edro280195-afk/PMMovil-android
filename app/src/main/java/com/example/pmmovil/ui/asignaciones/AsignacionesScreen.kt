package com.example.pmmovil.ui.asignaciones

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.R
import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.ui.components.PmmStatusBar
import com.example.pmmovil.ui.components.SkeletonCircle
import com.example.pmmovil.ui.components.SkeletonLine
import com.example.pmmovil.ui.components.rememberUnlockApprovedAlert
import com.example.pmmovil.ui.localization.resolve
import com.example.pmmovil.ui.revision.EtiquetaPerfilViewModel
import com.example.pmmovil.ui.revision.RevisionModeSelectorContent
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.theme.PMMovilTheme
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
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
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import java.time.LocalDateTime


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsignacionesScreen(
    onNavigateToNormal: (String) -> Unit,
    onNavigateToLabels: (String) -> Unit,
    onNavigateToBarcode: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToScanner: () -> Unit = {},
    viewModel: AsignacionesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val hub by viewModel.pendingHub.collectAsState()
    val localResults by viewModel.localResults.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarKind by remember { mutableStateOf(PendingMessageKind.INFO) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val playUnlockApprovedAlert = rememberUnlockApprovedAlert()
    val currentUsername = viewModel.getUsername()
    val searching = searchTokens(uiState.query).isNotEmpty()

    PmmStatusBar(color = PmmNavyDark, darkIcons = false)

    HardwareScannerListener(onBarcodeScanned = viewModel::searchExactReference)

    // Al regresar de una revisión o de la cámara se renuevan los pendientes: así el avance que se ve es el real.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshOnResume() }

    // «Atrás» con algo escrito borra la búsqueda y vuelve a los pendientes; solo con el buscador vacío sale de la pantalla.
    BackHandler(enabled = uiState.query.isNotEmpty()) {
        focusManager.clearFocus()
        viewModel.clearQuery()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AsignacionesEvent.NavigateToRevision -> {
                    when (event.mode) {
                        ModePreferences.Mode.NORMAL -> onNavigateToNormal(event.crefAgrupada)
                        ModePreferences.Mode.LABELS -> onNavigateToLabels(event.crefAgrupada)
                        ModePreferences.Mode.BARCODE -> onNavigateToBarcode(event.crefAgrupada)
                    }
                }

                is AsignacionesEvent.ShowMessage -> {
                    snackbarKind = event.kind
                    snackbarHostState.currentSnackbarData?.dismiss()
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = event.message.resolve(context),
                            duration = SnackbarDuration.Long
                        )
                    }
                }

                is AsignacionesEvent.UnlockApproved -> {
                    playUnlockApprovedAlert(context.getString(R.string.notification_entry_authorized))
                }

                is AsignacionesEvent.RevisionAssigned -> {
                    playUnlockApprovedAlert(context.getString(R.string.notification_review_assigned))
                }
            }
        }
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            // OJO: debe ir en el slot topBar, no como contenido normal del
            // body -- Scaffold ya reserva el inset del status bar para su
            // contenido (scaffoldPadding), y HubHeader aplica el suyo
            // propio (.statusBarsPadding()) -- puesto como body, el inset se
            // reservaba dos veces y dejaba una tira en blanco (el
            // containerColor claro del Scaffold) antes de que empezara el
            // azul del header. El slot topBar no sufre ese doble padding.
            HubHeader(
                pendingCount = hub.total,
                hasLoaded = uiState.hasLoadedMine,
                query = uiState.query,
                isLookingUp = uiState.isSearching,
                isRefreshing = uiState.isRefreshing,
                onBack = onNavigateBack,
                onRefresh = { viewModel.loadData(showLoading = true) },
                onQueryChange = viewModel::setQuery,
                onSubmit = {
                    focusManager.clearFocus()
                    viewModel.submitQuery()
                },
                onClear = viewModel::clearQuery,
                onScan = onNavigateToScanner
            )
        },
        snackbarHost = {
            PendingSnackbarHost(
                hostState = snackbarHostState,
                kind = snackbarKind
            )
        }
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 980.dp)
            ) {
                if (searching) {
                    SearchResultsPane(
                        query = uiState.query,
                        localResults = localResults,
                        search = uiState.search,
                        currentUsername = currentUsername,
                        takingReference = uiState.takingReference,
                        onEntryClick = {
                            focusManager.clearFocus()
                            viewModel.selectEntry(it)
                        },
                        onTake = {
                            focusManager.clearFocus()
                            viewModel.takeFolder(it)
                        }
                    )
                } else {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.loadData(showLoading = false) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            !uiState.hasLoadedMine && hub.isEmpty -> PendingSkeletonGrid()
                            hub.isEmpty -> HubEmptyState(onScan = onNavigateToScanner)
                            else -> PendingHubList(
                                hub = hub,
                                currentUsername = currentUsername,
                                waitingUnlock = uiState.unlockRequests,
                                takingReference = uiState.takingReference,
                                onEntryClick = viewModel::selectEntry,
                                onUnlockClick = viewModel::showDesbloqueoDialog,
                                onRelease = viewModel::releaseFolder
                            )
                        }
                    }
                }
            }
        }
    }

    uiState.selectedEntry?.let { entry ->
        ModeSelectorSheet(
            entry = entry,
            preferredMode = viewModel.lastModeFor(entry.crefAgrupada),
            isOpening = uiState.isOpeningEntry,
            onDismiss = viewModel::dismissModeSelector,
            onModeSelected = viewModel::openSelectedEntry
        )
    }

    // Otra persona ya tenía tomada la carpeta: se dice quién y desde qué hora, y se pregunta si se la quiere quedar.
    uiState.takeConflict?.let { conflict ->
        val since = formatActivity(conflict.desde, remember { LocalDateTime.now() })
        AlertDialog(
            onDismissRequest = viewModel::dismissTakeConflict,
            containerColor = PmmBgSurface,
            title = {
                Text(
                    text = stringResource(R.string.assignments_take_conflict_title),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.assignments_take_conflict_message,
                        conflict.tomadaPor.ifBlank { stringResource(R.string.assignments_take_someone) },
                        since ?: "—",
                        conflict.entry.crefAgrupada
                    ),
                    color = PmmTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmTakeOver,
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Text(stringResource(R.string.assignments_take_conflict_confirm), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissTakeConflict) {
                    Text(stringResource(R.string.assignments_take_conflict_cancel))
                }
            }
        )
    }

    uiState.desbloqueoDialog?.let { entry ->
        UnlockRequestSheet(
            entry = entry,
            isSubmitting = uiState.isSubmittingUnlock,
            isWaiting = entry.crefAgrupada in uiState.unlockRequests,
            onDismiss = viewModel::hideDesbloqueoDialog,
            onSubmit = viewModel::solicitarDesbloqueo
        )
    }
}

/**
 * Arriba siempre está lo que el revisador necesita para llegar a una entrada: el buscador y, a su lado, el botón grande
 * de la cámara. La pistola no necesita nada: escucha en toda la pantalla, y la leyenda de abajo lo confirma.
 */
@Composable
private fun HubHeader(
    pendingCount: Int,
    hasLoaded: Boolean,
    query: String,
    isLookingUp: Boolean,
    isRefreshing: Boolean,
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onScan: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val subtitle = when {
        !hasLoaded -> stringResource(R.string.assignments_hub_subtitle_loading)
        pendingCount == 0 -> stringResource(R.string.assignments_hub_subtitle_none)
        else -> context.resources.getQuantityString(R.plurals.assignments_hub_subtitle_count, pendingCount, pendingCount)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(PmmNavyDark, PmmBlueDeep)))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                HeaderIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    onClick = onBack
                )
                Spacer(Modifier.width(8.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.assignments_hub_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            HeaderIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.assignments_refresh_content_description),
                enabled = !isRefreshing,
                onClick = onRefresh
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = PmmTextSecondary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.common_clear_search),
                                tint = PmmTextSecondary
                            )
                        }
                    }
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.assignments_search_placeholder),
                        color = PmmTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = PmmTextPrimary,
                    unfocusedTextColor = PmmTextPrimary,
                    focusedBorderColor = PmmYellow,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = PmmBlueDeep
                )
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onScan()
                },
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = PmmYellow
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.assignments_scan_with_camera),
                        tint = PmmNavyDark,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (isLookingUp) PmmYellow else Color(0xFF86EFAC))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(
                    if (isLookingUp) R.string.assignments_searching else R.string.assignments_scanner_hint
                ),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isLookingUp || isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = PmmYellow,
                trackColor = Color.White.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.12f else 0.06f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

@Composable
private fun PendingHubList(
    hub: PendingHub,
    currentUsername: String?,
    waitingUnlock: Set<String>,
    takingReference: String?,
    onEntryClick: (EntradaPendienteDto) -> Unit,
    onUnlockClick: (EntradaPendienteDto) -> Unit,
    onRelease: (EntradaPendienteDto) -> Unit
) {
    val context = LocalContext.current

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HubSummary(hub)
        }

        if (hub.assigned.isNotEmpty()) {
            sectionHeader(
                title = context.getString(R.string.assignments_assigned_to_me),
                subtitle = context.getString(R.string.assignments_sorted_by_priority),
                count = hub.assigned.size
            )
            items(hub.assigned, key = { "assigned_${it.crefAgrupada}" }) { entry ->
                PendingEntryCard(
                    entry = entry,
                    currentUsername = currentUsername,
                    waitingUnlock = entry.crefAgrupada in waitingUnlock,
                    busy = takingReference == entry.crefAgrupada,
                    onEntryClick = { onEntryClick(entry) },
                    onUnlockClick = { onUnlockClick(entry) },
                    onRelease = { onRelease(entry) }
                )
            }
        }

        if (hub.open.isNotEmpty()) {
            sectionHeader(
                title = context.getString(R.string.assignments_open_title),
                subtitle = context.getString(R.string.assignments_open_subtitle),
                count = hub.open.size
            )
            items(hub.open, key = { "open_${it.crefAgrupada}" }) { entry ->
                PendingEntryCard(
                    entry = entry,
                    currentUsername = currentUsername,
                    waitingUnlock = entry.crefAgrupada in waitingUnlock,
                    busy = takingReference == entry.crefAgrupada,
                    onEntryClick = { onEntryClick(entry) },
                    onUnlockClick = { onUnlockClick(entry) },
                    onRelease = { onRelease(entry) }
                )
            }
        }
    }
}

/** De un vistazo: cuántas asignadas, cuántas en curso y cuántas pausadas. Solo salen las que tienen algo. */
@Composable
private fun HubSummary(hub: PendingHub) {
    val resources = LocalContext.current.resources
    val chips = buildList {
        if (hub.assigned.isNotEmpty()) {
            add(resources.getQuantityString(R.plurals.assignments_hub_chip_assigned, hub.assigned.size, hub.assigned.size))
        }
        if (hub.inProgressCount > 0) {
            add(resources.getQuantityString(R.plurals.assignments_hub_chip_in_progress, hub.inProgressCount, hub.inProgressCount))
        }
        if (hub.pausedCount > 0) {
            add(resources.getQuantityString(R.plurals.assignments_hub_chip_paused, hub.pausedCount, hub.pausedCount))
        }
    }
    if (chips.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { label ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PmmBgSurface,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

/** Sin pendientes: la pantalla lo dice y ofrece lo que sigue, que es escanear. */
@Composable
private fun HubEmptyState(onScan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(PmmNavyVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.assignments_hub_empty_title),
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.assignments_hub_empty_body),
                color = PmmTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onScan,
                modifier = Modifier.heightIn(min = 52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmNavyDark),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.assignments_scan_with_camera),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ── Resultados de la búsqueda ────────────────────────────────────────────────

/**
 * Lo que se ve mientras hay algo escrito. Arriba, al instante, las coincidencias entre las revisiones del propio
 * revisador (ya están en la tableta). Debajo, lo que contesta el servidor sobre TODAS las entradas, ocho como máximo y
 * con su estado real; mientras llega se ven renglones de espera, no una pantalla en blanco.
 */
@Composable
private fun SearchResultsPane(
    query: String,
    localResults: List<EntradaPendienteDto>,
    search: SearchState,
    currentUsername: String?,
    takingReference: String?,
    onEntryClick: (EntradaPendienteDto) -> Unit,
    onTake: (EntradaPendienteDto) -> Unit
) {
    val context = LocalContext.current
    val tokens = remember(query) { searchTokens(query) }
    val localReferences = remember(localResults) { localResults.map { it.crefAgrupada }.toSet() }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (localResults.isNotEmpty()) {
            resultsHeader(
                title = context.getString(R.string.assignments_results_mine),
                chip = context.getString(R.string.assignments_results_instant),
                chipIsFinal = true
            )
            items(localResults, key = { "local_${it.crefAgrupada}" }) { entry ->
                ResultRow(
                    entry = entry,
                    approximate = false,
                    tokens = tokens,
                    currentUsername = currentUsername,
                    busy = takingReference == entry.crefAgrupada,
                    onClick = { onEntryClick(entry) },
                    onTake = { onTake(entry) }
                )
            }
        }

        when (search) {
            SearchState.Idle -> item(span = { GridItemSpan(maxLineSpan) }) {
                SearchNote(stringResource(R.string.assignments_results_min_chars))
            }

            SearchState.Searching -> {
                resultsHeader(
                    title = context.getString(R.string.assignments_results_all),
                    chip = context.getString(R.string.assignments_results_waiting),
                    chipIsFinal = false
                )
                items(3, key = { "wait_$it" }) { ResultSkeletonRow() }
            }

            is SearchState.Results -> {
                val shown = search.hits.filter { it.item.crefAgrupada !in localReferences }
                resultsHeader(
                    title = context.getString(R.string.assignments_results_all),
                    chip = context.getString(R.string.assignments_results_ms, search.elapsedMillis.toInt()),
                    chipIsFinal = true
                )
                when {
                    shown.isEmpty() && localResults.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        NoResultsState(query = query.trim())
                    }

                    shown.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        SearchNote(stringResource(R.string.assignments_results_no_more))
                    }

                    else -> {
                        items(shown, key = { "hit_${it.item.crefAgrupada}" }) { hit ->
                            ResultRow(
                                entry = hit.item,
                                approximate = hit.aproximada,
                                tokens = tokens,
                                currentUsername = currentUsername,
                                busy = takingReference == hit.item.crefAgrupada,
                                onClick = { onEntryClick(hit.item) },
                                onTake = { onTake(hit.item) }
                            )
                        }
                        if (search.total > search.hits.size) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SearchNote(
                                    stringResource(
                                        R.string.assignments_results_footer,
                                        search.hits.size,
                                        search.total
                                    )
                                )
                            }
                        }
                    }
                }
            }

            is SearchState.Unavailable -> {
                resultsHeader(
                    title = context.getString(R.string.assignments_results_all),
                    chip = null,
                    chipIsFinal = false
                )
                item(span = { GridItemSpan(maxLineSpan) }) {
                    UnavailableNote(message = search.message.resolve(context))
                }
            }
        }
    }
}

private fun LazyGridScope.resultsHeader(title: String, chip: String?, chipIsFinal: Boolean) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = PmmTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )
            if (chip != null) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (chipIsFinal) Color(0xFFF0FDF4) else Color(0xFFFFFBEB)
                ) {
                    Text(
                        text = chip,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        color = if (chipIsFinal) PmmGreenDark else PmmAmberDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberHighlighted(text: String, tokens: List<String>): AnnotatedString =
    remember(text, tokens) {
        buildAnnotatedString {
            var cursor = 0
            for (range in matchRanges(text, tokens)) {
                append(text.substring(cursor, range.first))
                withStyle(SpanStyle(background = PmmYellow)) {
                    append(text.substring(range.first, range.last + 1))
                }
                cursor = range.last + 1
            }
            append(text.substring(cursor))
        }
    }

/** Un resultado compacto: lo justo para reconocer la entrada y su estado antes de abrirla. */
@Composable
private fun ResultRow(
    entry: EntradaPendienteDto,
    approximate: Boolean,
    tokens: List<String>,
    currentUsername: String?,
    busy: Boolean = false,
    onClick: () -> Unit,
    onTake: () -> Unit = {}
) {
    val status = calculateEntryStatus(entry)
    val visual = entryVisual(status.lifecycle)
    val locked = isEntryLocked(entry, currentUsername)
    val owner = entry.usuarioRevisando?.takeIf { it.isNotBlank() } ?: entry.usuarioTomoDocumento
    val haptic = LocalHapticFeedback.current
    val canTake = CarpetaToma.puedeTomar(entry, currentUsername)
    val takenByMe = CarpetaToma.esMia(entry.tomadaPor, currentUsername)
    val takenBy = entry.tomadaPor?.trim()?.takeIf { it.isNotEmpty() }
    val takenAt = formatActivity(entry.tomadaDesde, remember { LocalDateTime.now() })

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        shape = RoundedCornerShape(16.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, if (locked) PmmRed.copy(alpha = 0.3f) else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(if (locked) PmmRed else visual.color)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rememberHighlighted(entry.crefAgrupada, tokens),
                        color = PmmTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1
                    )
                    if (approximate) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, PmmAmber.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = stringResource(R.string.assignments_results_approximate),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = PmmAmberDark,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                Text(
                    text = rememberHighlighted(
                        entry.nombreCliente?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.common_customer_unavailable),
                        tokens
                    ),
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.revision_invoices)} ${entry.facturasCompletas}/${entry.totalFacturas}",
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${stringResource(R.string.revision_parts)} ${entry.partidasRevisadas}/${entry.totalPartidas}",
                        color = PmmTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (locked && !owner.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PmmRedDark,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                text = owner,
                                color = PmmRedDark,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                // «¿Quién trae la 123?»: quien la tomó y desde qué hora.
                if (takenBy != null) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (takenByMe) {
                            stringResource(R.string.assignments_taken_by_me, takenAt ?: "—")
                        } else {
                            stringResource(R.string.assignments_taken_by_other, takenBy, takenAt ?: "—")
                        },
                        color = if (takenByMe) PmmBlueDeep else PmmAmberDark,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .align(Alignment.CenterVertically)
            ) {
              Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                if (locked) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, PmmRed.copy(alpha = 0.22f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = PmmRedDark,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.assignments_in_use_short),
                                color = PmmRedDark,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    StatusBadge(status = status, visual = visual)
                }
                // Agarró la carpeta sin abrir la revisión: queda a su nombre y el Monitor lo ve desde este momento.
                if (canTake) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTake()
                        },
                        enabled = !busy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, PmmBlueDeep.copy(alpha = 0.45f)),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.assignments_take_action),
                            color = PmmBlueDeep,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1
                        )
                    }
                }
              }
            }
        }
    }
}

@Composable
private fun ResultSkeletonRow() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = RoundedCornerShape(16.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Center
        ) {
            SkeletonLine(modifier = Modifier.fillMaxWidth(0.5f))
            Spacer(Modifier.height(10.dp))
            SkeletonLine(modifier = Modifier.fillMaxWidth(0.8f))
        }
    }
}

@Composable
private fun SearchNote(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        color = PmmTextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun UnavailableNote(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFFEF2F2),
        border = BorderStroke(1.dp, PmmRed.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = PmmRedDark
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = message,
                    color = PmmRedDark,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = stringResource(R.string.assignments_results_unavailable_hint),
                    color = PmmRedDark,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PmmNavyVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(34.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.assignments_results_none_title, query),
            color = PmmTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.assignments_results_none_body),
            modifier = Modifier.widthIn(max = 380.dp),
            color = PmmTextSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center
        )
    }
}

private fun LazyGridScope.sectionHeader(
    title: String,
    subtitle: String,
    count: Int
) {
    item(span = { GridItemSpan(maxLineSpan) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subtitle,
                    color = PmmTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = PmmNavyVariant
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    color = PmmBlueDeep,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PendingEntryCard(
    entry: EntradaPendienteDto,
    currentUsername: String?,
    waitingUnlock: Boolean,
    busy: Boolean = false,
    onEntryClick: () -> Unit,
    onUnlockClick: () -> Unit,
    onRelease: () -> Unit = {}
) {
    val status = calculateEntryStatus(entry)
    val visual = entryVisual(status.lifecycle)
    val locked = isEntryLocked(entry, currentUsername)
    val haptic = LocalHapticFeedback.current
    val now = remember { LocalDateTime.now() }
    // Cuándo fue lo último que pasó con esta entrada, para que el revisador sepa si la dejó hace una hora o hace tres días.
    val activity: String? = when {
        status.category == EntryCategory.IN_PROGRESS ->
            formatActivity(entry.inicioRevision, now)?.let { stringResource(R.string.assignments_activity_in_progress, it) }
        status.category == EntryCategory.PAUSED ->
            formatActivity(entry.finRevision, now)?.let { stringResource(R.string.assignments_activity_paused, it) }
        // Una carpeta tomada ya dice su hora en su propio banner.
        CarpetaToma.esTomaPropia(entry, currentUsername) -> null
        entry.esAsignada == true ->
            formatActivity(entry.asignFecha, now)?.let { stringResource(R.string.assignments_activity_assigned, it) }
        else -> null
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            if (locked) onUnlockClick() else onEntryClick()
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 206.dp),
        shape = RoundedCornerShape(20.dp),
        color = PmmBgSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(
            width = 1.dp,
            color = if (locked) PmmRed.copy(alpha = 0.3f) else PmmNavyBorder
        )
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .heightIn(min = 206.dp)
                    .background(
                        when {
                            locked -> PmmRed
                            entry.asignPrioridad == 2 -> PmmRed
                            entry.asignPrioridad == 1 -> PmmAmber
                            else -> visual.color
                        }
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                if (CarpetaToma.esTomaPropia(entry, currentUsername)) {
                    // Una carpeta que el propio revisador tomó no es una asignación del supervisor: se dice desde cuándo la
                    // trae y se le deja devolverla si la tomó por error.
                    TakenBanner(entry = entry, now = now, busy = busy, onRelease = onRelease)
                    Spacer(Modifier.height(12.dp))
                } else if (entry.esAsignada == true) {
                    AssignmentBanner(entry)
                    Spacer(Modifier.height(12.dp))
                }

                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.crefAgrupada,
                            color = PmmTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = entry.nombreCliente?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.common_customer_unavailable),
                            color = PmmTextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activity != null) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = activity,
                                color = visual.color,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(status = status, visual = visual)
                }

                Spacer(Modifier.height(14.dp))
                EntryProgress(entry = entry, status = status, visual = visual)
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Description,
                        label = stringResource(R.string.revision_invoices),
                        value = "${entry.facturasCompletas}/${entry.totalFacturas}"
                    )
                    MetricChip(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Inventory2,
                        label = stringResource(R.string.revision_parts),
                        value = "${entry.partidasRevisadas}/${entry.totalPartidas}"
                    )
                }

                entry.asignNota?.takeIf { it.isNotBlank() }?.let { note ->
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, PmmAmber.copy(alpha = 0.25f))
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = PmmAmberDark,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = note,
                                color = Color(0xFF78350F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = PmmNavyBorder)
                Spacer(Modifier.height(10.dp))

                EntryActionRow(
                    entry = entry,
                    locked = locked,
                    waitingUnlock = waitingUnlock,
                    currentUsername = currentUsername,
                    onEntryClick = onEntryClick,
                    onUnlockClick = onUnlockClick
                )
            }
        }
    }
}

@Composable
private fun AssignmentBanner(entry: EntradaPendienteDto) {
    val priority = when (entry.asignPrioridad) {
        2 -> Triple(R.string.assignments_urgent, PmmRed, Color(0xFFFEF2F2))
        1 -> Triple(R.string.assignments_high_priority, PmmAmberDark, Color(0xFFFFFBEB))
        else -> Triple(R.string.assignments_assigned, PmmBlueDeep, PmmNavyVariant)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = priority.third,
            border = BorderStroke(1.dp, priority.second.copy(alpha = 0.25f))
        ) {
            Text(
                    text = stringResource(priority.first),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = priority.second,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
        if (entry.asignEstatus == 0) {
            Spacer(Modifier.width(7.dp))
            Surface(
                shape = RoundedCornerShape(7.dp),
                color = PmmYellow
            ) {
                Text(
                    text = stringResource(R.string.assignments_new),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                    color = PmmNavyDark,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }
        Spacer(Modifier.weight(1f))
        entry.asignSupervisor?.takeIf { it.isNotBlank() }?.let { supervisor ->
            Text(
                text = stringResource(R.string.assignments_by_supervisor, supervisor),
                color = PmmTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * El banner de una carpeta que el propio revisador tomó: «TOMADA · 09:30 · 3 h 20 min sin iniciar» y un botón para
 * devolverla si la tomó por error. El tiempo lo mide el servidor; no se calcula con el reloj de la tableta.
 */
@Composable
private fun TakenBanner(
    entry: EntradaPendienteDto,
    now: LocalDateTime,
    busy: Boolean,
    onRelease: () -> Unit
) {
    val since = formatActivity(entry.tomadaDesde ?: entry.asignFecha, now)
    val elapsed = CarpetaToma.tiempoSinIniciar(entry.segundosDesdeToma)
    val detail = listOfNotNull(
        since,
        elapsed?.let { stringResource(R.string.assignments_taken_elapsed, it) }
    ).joinToString(" · ")

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(7.dp),
            color = PmmNavyVariant,
            border = BorderStroke(1.dp, PmmBlueDeep.copy(alpha = 0.25f))
        ) {
            Text(
                text = stringResource(R.string.assignments_taken_badge),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = PmmBlueDeep,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
        }
        if (detail.isNotEmpty()) {
            Spacer(Modifier.width(7.dp))
            Text(
                text = detail,
                color = PmmTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = onRelease,
            enabled = !busy,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = stringResource(R.string.assignments_release_action),
                color = PmmTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private data class EntryVisual(
    val color: Color,
    val container: Color,
    val icon: ImageVector
)

private fun entryVisual(lifecycle: EntryLifecycle): EntryVisual {
    return when (lifecycle) {
        EntryLifecycle.PROCESS_COMPLETED ->
            EntryVisual(PmmGreenDark, Color(0xFFF0FDF4), Icons.Default.CheckCircle)

        EntryLifecycle.REVIEW_STOPPED ->
            EntryVisual(Color(0xFF4F46E5), Color(0xFFEEF2FF), Icons.Default.HourglassTop)

        EntryLifecycle.RELATION_CLOSED ->
            EntryVisual(PmmAmberDark, Color(0xFFFFFBEB), Icons.AutoMirrored.Filled.FactCheck)

        EntryLifecycle.REVIEW_COMPLETE ->
            EntryVisual(PmmGreen, Color(0xFFF0FDF4), Icons.Default.TaskAlt)

        EntryLifecycle.IN_PROGRESS ->
            EntryVisual(PmmBlueDeep, PmmNavyVariant, Icons.Default.Refresh)

        EntryLifecycle.PENDING ->
            EntryVisual(PmmTextSecondary, Color(0xFFF8FAFC), Icons.Default.Description)
    }
}

@Composable
private fun StatusBadge(
    status: EntryStatusInfo,
    visual: EntryVisual
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = visual.container,
        border = BorderStroke(1.dp, visual.color.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = visual.icon,
                contentDescription = null,
                tint = visual.color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = stringResource(status.labelRes),
                color = visual.color,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun EntryProgress(
    entry: EntradaPendienteDto,
    status: EntryStatusInfo,
    visual: EntryVisual
) {
    // Con partidas se cuenta por partidas revisadas; sin ellas, por facturas completas (ver progressPercent).
    val byParts = progressIsByParts(entry)
    val percent = progressPercent(entry)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(
                    if (byParts) R.string.assignments_progress_real else R.string.assignments_progress
                ),
                color = PmmTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (byParts) {
                    stringResource(R.string.assignments_progress_parts, entry.partidasRevisadas, entry.totalPartidas)
                } else {
                    stringResource(R.string.assignments_progress_count, status.completedInvoices, status.totalInvoices)
                },
                color = PmmTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$percent%",
                color = visual.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { percent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(999.dp)),
            color = visual.color,
            trackColor = PmmNavyBorder
        )
    }
}

@Composable
private fun MetricChip(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = label,
                    color = PmmTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = value,
                    color = PmmTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun EntryActionRow(
    entry: EntradaPendienteDto,
    locked: Boolean,
    waitingUnlock: Boolean,
    currentUsername: String?,
    onEntryClick: () -> Unit,
    onUnlockClick: () -> Unit
) {
    val reviewer = entry.usuarioRevisando
    val documentOwner = entry.usuarioTomoDocumento
    val activeOwner = reviewer?.takeIf { it.isNotBlank() } ?: documentOwner
    val isMineReview = !reviewer.isNullOrBlank() &&
        reviewer.equals(currentUsername, ignoreCase = true)
    val isMineDocument = !documentOwner.isNullOrBlank() &&
        documentOwner.equals(currentUsername, ignoreCase = true)
    val isMine = isMineReview || isMineDocument
    // A diferencia de "revisión finalizada", una relación ya cerrada es un
    // cierre físico/logístico definitivo: no tiene sentido "solicitar
    // acceso" a algo que nadie puede reabrir. El backend también lo
    // rechazaría (ver RevisionService.SolicitarDesbloqueoAsync).
    val relacionCerrada = entry.todasRelacionCompleta

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when {
                locked -> Icons.Default.Lock
                isMineReview -> Icons.Default.Person
                isMineDocument -> Icons.Default.Description
                else -> Icons.AutoMirrored.Filled.Assignment
            },
            contentDescription = null,
            tint = if (locked) PmmRed else PmmTextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = when {
                locked && relacionCerrada -> stringResource(
                    R.string.assignments_in_use_closed,
                    activeOwner.orEmpty()
                )
                locked -> stringResource(R.string.assignments_in_use, activeOwner.orEmpty())
                isMineReview -> stringResource(R.string.assignments_in_review_by_you)
                isMineDocument -> stringResource(R.string.assignments_documents_by_you)
                !documentOwner.isNullOrBlank() -> stringResource(
                    R.string.assignments_documents_by,
                    documentOwner
                )
                !entry.finRevision.isNullOrBlank() -> stringResource(R.string.assignments_paused_session)
                else -> stringResource(R.string.assignments_ready_to_start)
            },
            modifier = Modifier.weight(1f),
            color = if (locked) PmmRedDark else PmmTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (locked && relacionCerrada) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.FactCheck,
                contentDescription = stringResource(R.string.assignments_closed_no_request),
                tint = PmmTextMuted,
                modifier = Modifier.size(18.dp).padding(end = 12.dp)
            )
        } else if (locked) {
            TextButton(
                onClick = onUnlockClick,
                enabled = !waitingUnlock,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = if (waitingUnlock) Icons.Default.HourglassTop else Icons.Default.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(
                        if (waitingUnlock) R.string.assignments_waiting
                        else R.string.assignments_request_access
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            TextButton(
                onClick = onEntryClick,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isMine) R.string.assignments_continue
                        else R.string.assignments_choose_mode
                    ),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelectorSheet(
    entry: EntradaPendienteDto,
    preferredMode: ModePreferences.Mode,
    isOpening: Boolean,
    onDismiss: () -> Unit,
    onModeSelected: (ModePreferences.Mode) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PmmBgSurface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(PmmNavyBorder)
            )
        }
    ) {
        val perfilEtiquetas: EtiquetaPerfilViewModel = hiltViewModel()
        LaunchedEffect(entry.crefAgrupada) { perfilEtiquetas.resolver(entry.crefAgrupada) }
        val grupoDeEtiquetas by perfilEtiquetas.grupo.collectAsState()

        RevisionModeSelectorContent(
            crefAgrupada = entry.crefAgrupada,
            contextLabel = entry.nombreCliente?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.common_customer_unavailable),
            facturas = "${entry.facturasCompletas}/${entry.totalFacturas}",
            partidas = "${entry.partidasRevisadas}/${entry.totalPartidas}",
            preferredMode = preferredMode,
            isOpening = isOpening,
            onModeSelected = onModeSelected,
            grupoDeEtiquetas = grupoDeEtiquetas
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnlockRequestSheet(
    entry: EntradaPendienteDto,
    isSubmitting: Boolean,
    isWaiting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var reason by rememberSaveable(entry.crefAgrupada) { mutableStateOf("") }
    val valid = reason.trim().length >= 5

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PmmBgSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PmmRed.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = PmmRed,
                        modifier = Modifier.size(25.dp)
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column {
                    Text(
                        text = stringResource(
                            if (isWaiting) R.string.assignments_request_pending
                            else R.string.assignments_request_access
                        ),
                        color = PmmTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = entry.crefAgrupada,
                        color = PmmBlueDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFFFEF2F2),
                border = BorderStroke(1.dp, PmmRed.copy(alpha = 0.18f))
            ) {
                Row(
                    modifier = Modifier.padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = PmmRed,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = stringResource(
                            R.string.assignments_current_reviewer,
                            entry.usuarioRevisando
                                ?: stringResource(R.string.assignments_other_user)
                        ),
                        color = PmmRedDark,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isWaiting) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.assignments_request_exists),
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) {
                    Text(
                        stringResource(R.string.common_understood),
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 116.dp),
                    enabled = !isSubmitting,
                    label = { Text(stringResource(R.string.assignments_reason)) },
                    supportingText = {
                        Text(
                            text = stringResource(
                                R.string.assignments_reason_counter,
                                reason.trim().length
                            ),
                            color = if (reason.isNotEmpty() && !valid) PmmRed else PmmTextMuted
                        )
                    },
                    placeholder = {
                        Text(stringResource(R.string.assignments_reason_hint))
                    },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PmmBlueDeep,
                        cursorColor = PmmBlueDeep
                    )
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                    ) {
                        Text(
                            stringResource(R.string.common_cancel),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Button(
                        onClick = { onSubmit(reason) },
                        enabled = valid && !isSubmitting,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                stringResource(R.string.common_request),
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingSkeletonGrid() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(2) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = RoundedCornerShape(20.dp),
                color = PmmBgSurface,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SkeletonCircle(size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            SkeletonLine(modifier = Modifier.fillMaxWidth(0.65f))
                            Spacer(Modifier.height(8.dp))
                            SkeletonLine(modifier = Modifier.fillMaxWidth(0.85f))
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    SkeletonLine()
                    Spacer(Modifier.height(12.dp))
                    SkeletonLine(modifier = Modifier.fillMaxWidth(0.72f))
                    Spacer(Modifier.height(22.dp))
                    SkeletonLine()
                }
            }
        }
    }
}

@Composable
private fun PendingSnackbarHost(
    hostState: SnackbarHostState,
    kind: PendingMessageKind
) {
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .imePadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
    ) { data ->
        val visual = when (kind) {
            PendingMessageKind.INFO -> Triple(PmmNavyDark, Icons.Default.Info, Color.White)
            PendingMessageKind.SUCCESS -> Triple(PmmGreenDark, Icons.Default.CheckCircle, Color.White)
            PendingMessageKind.WARNING -> Triple(PmmAmberDark, Icons.Default.Warning, Color.White)
            PendingMessageKind.ERROR -> Triple(PmmRedDark, Icons.Default.Error, Color.White)
        }
        Snackbar(
            modifier = Modifier.fillMaxWidth(),
            containerColor = visual.first,
            contentColor = visual.third,
            shape = RoundedCornerShape(16.dp),
            dismissAction = {
                IconButton(onClick = data::dismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_close_message),
                        tint = Color.White
                    )
                }
            }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = visual.second,
                    contentDescription = null,
                    tint = visual.third,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = data.visuals.message,
                    color = visual.third,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun PendingEntryCardPreview() {
    PMMovilTheme {
        Box(
            modifier = Modifier
                .background(PmmBgPrimary)
                .padding(16.dp)
        ) {
            PendingEntryCard(
                entry = EntradaPendienteDto(
                    crefAgrupada = "01C263562540000",
                    nombreReferencia = "01C263562540000",
                    clienteId = "PMM-1024",
                    nombreCliente = "INDUSTRIAS DEL NORTE S.A. DE C.V.",
                    dFecha = "2026-06-13T09:30:00",
                    totalFacturas = 8,
                    facturasCompletas = 3,
                    totalPartidas = 42,
                    partidasRevisadas = 18,
                    inicioRevision = null,
                    finRevision = null,
                    usuarioRevisando = null,
                    usuarioTomoDocumento = null,
                    todasRelacionCompleta = false,
                    esAsignada = true,
                    asignPrioridad = 2,
                    idAsignacion = 12,
                    asignSupervisor = "SUPERVISOR01",
                    asignNota = "Priorizar antes del corte de las 14:00.",
                    asignEstatus = 0,
                    asignFecha = "2026-06-13T09:30:00"
                ),
                currentUsername = "EDUARDO",
                waitingUnlock = false,
                onEntryClick = {},
                onUnlockClick = {}
            )
        }
    }
}
