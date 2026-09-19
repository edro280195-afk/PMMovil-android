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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.FilterAltOff
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.R
import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.ui.components.PmmStatusBar
import com.example.pmmovil.ui.components.SkeletonCircle
import com.example.pmmovil.ui.components.SkeletonLine
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.revision.RevisionModeSelectorContent
import com.example.pmmovil.ui.theme.PMMovilTheme
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
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
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.components.rememberUnlockApprovedAlert
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.localization.resolve
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsignacionesScreen(
    onNavigateToNormal: (String) -> Unit,
    onNavigateToBarcode: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    onNavigateToScanner: () -> Unit = {},
    viewModel: AsignacionesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val mine by viewModel.misRevisionesTotales.collectAsState()
    val available by viewModel.entradasFiltradas.collectAsState()
    val mineCount by viewModel.mineCount.collectAsState()
    val minePendingCount by viewModel.minePendingCount.collectAsState()
    val availableCount by viewModel.availableCount.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarKind by remember { mutableStateOf(PendingMessageKind.INFO) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val playUnlockApprovedAlert = rememberUnlockApprovedAlert()

    PmmStatusBar(color = PmmNavyDark, darkIcons = false)

    HardwareScannerListener(onBarcodeScanned = viewModel::searchExactReference)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AsignacionesEvent.NavigateToRevision -> {
                    when (event.mode) {
                        ModePreferences.Mode.NORMAL -> onNavigateToNormal(event.crefAgrupada)
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

    val currentEntries = if (uiState.drawerTab == DrawerTab.MIAS) mine else available

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            // OJO: debe ir en el slot topBar, no como contenido normal del
            // body -- Scaffold ya reserva el inset del status bar para su
            // contenido (scaffoldPadding), y PendingHeader aplica el suyo
            // propio (.statusBarsPadding()) -- puesto como body, el inset se
            // reservaba dos veces y dejaba una tira en blanco (el
            // containerColor claro del Scaffold) antes de que empezara el
            // azul del header. El slot topBar no sufre ese doble padding.
            PendingHeader(
                onBack = onNavigateBack,
                onRefresh = { viewModel.loadData(showLoading = true) },
                isSearching = uiState.isSearching,
                isRefreshing = uiState.isRefreshing
            )
        },
        snackbarHost = {
            PendingSnackbarHost(
                hostState = snackbarHostState,
                kind = snackbarKind
            )
        },
        floatingActionButton = {
            ScanButton(
                onClick = onNavigateToScanner,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 4.dp)
            )
        }
    ) { scaffoldPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 980.dp)
                ) {
                    PendingOverview(
                        mineCount = mineCount,
                        minePendingCount = minePendingCount,
                        availableCount = availableCount,
                        selectedTab = uiState.drawerTab
                    )

                    PendingTabs(
                        selectedTab = uiState.drawerTab,
                        mineCount = mineCount,
                        availableCount = availableCount,
                        onSelect = viewModel::setDrawerTab
                    )

                    PendingSearch(
                        value = uiState.filtroTexto,
                        onValueChange = viewModel::setFiltroTexto,
                        onSearch = {
                            focusManager.clearFocus()
                        },
                        onClear = {
                            focusManager.clearFocus()
                            viewModel.setFiltroTexto("")
                        }
                    )

                    StatusFilters(
                        selected = uiState.statusActivo,
                        counts = counts,
                        onSelect = viewModel::setStatusActivo
                    )

                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.loadData(showLoading = false) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            uiState.isLoading && currentEntries.isEmpty() -> PendingSkeletonGrid()
                            currentEntries.isEmpty() -> PendingEmptyState(
                                tab = uiState.drawerTab,
                                hasFilters = uiState.filtroTexto.isNotBlank() ||
                                    uiState.statusActivo != FilterStatus.TOTAL,
                                onClearFilters = viewModel::clearFilters
                            )

                            else -> PendingEntriesGrid(
                                entries = currentEntries,
                                currentUsername = viewModel.getUsername(),
                                waitingUnlock = uiState.unlockRequests,
                                selectedTab = uiState.drawerTab,
                                truncated = uiState.truncated,
                                onEntryClick = viewModel::selectEntry,
                                onUnlockClick = viewModel::showDesbloqueoDialog
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

@Composable
private fun PendingHeader(
    onBack: (() -> Unit)?,
    onRefresh: () -> Unit,
    isSearching: Boolean,
    isRefreshing: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(PmmNavyDark, PmmBlueDeep)
                )
            )
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
                    text = stringResource(R.string.assignments_title),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.4).sp
                )
                Text(
                    text = stringResource(R.string.assignments_subtitle),
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isSearching) PmmYellow else Color(0xFF86EFAC))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (isSearching) R.string.assignments_searching
                            else R.string.assignments_scanner_ready
                        ),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.width(8.dp))
            HeaderIconButton(
                icon = Icons.Default.Refresh,
                contentDescription = stringResource(R.string.assignments_refresh_content_description),
                enabled = !isRefreshing,
                onClick = onRefresh
            )
        }

        if (isSearching || isRefreshing) {
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
private fun PendingOverview(
    mineCount: Int,
    minePendingCount: Int,
    availableCount: Int,
    selectedTab: DrawerTab
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        color = PmmBgSurface,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selectedTab == DrawerTab.MIAS) {
                            PmmYellow.copy(alpha = 0.2f)
                        } else {
                            PmmNavyVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTab == DrawerTab.MIAS) {
                        Icons.AutoMirrored.Filled.FactCheck
                    } else {
                        Icons.Default.Inventory2
                    },
                    contentDescription = null,
                    tint = if (selectedTab == DrawerTab.MIAS) PmmAmberDark else PmmBlueDeep,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedTab == DrawerTab.MIAS) {
                        stringResource(R.string.assignments_my_work_title)
                    } else {
                        stringResource(R.string.assignments_available_title)
                    },
                    color = PmmTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (selectedTab == DrawerTab.MIAS) {
                        // "Requieren tu atención" solo tiene sentido para las
                        // pendientes/en progreso -- antes usaba mineCount (el
                        // total, incluyendo las ya finalizadas) y avisaba de
                        // trabajo urgente que en realidad ya estaba cerrado.
                        when {
                            minePendingCount == 0 && mineCount > 0 ->
                                stringResource(R.string.assignments_up_to_date)
                            minePendingCount == 1 ->
                                stringResource(R.string.assignments_one_attention)
                            else -> stringResource(
                                R.string.assignments_many_attention,
                                minePendingCount
                            )
                        }
                    } else {
                        if (availableCount == 1) {
                            stringResource(R.string.assignments_one_available)
                        } else {
                            stringResource(R.string.assignments_many_available, availableCount)
                        }
                    },
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Text(
                text = if (selectedTab == DrawerTab.MIAS) minePendingCount.toString() else availableCount.toString(),
                color = PmmBlueDeep,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PendingTabs(
    selectedTab: DrawerTab,
    mineCount: Int,
    availableCount: Int,
    onSelect: (DrawerTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PmmNavyVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        PendingTab(
            modifier = Modifier.weight(1f),
            selected = selectedTab == DrawerTab.MIAS,
            label = stringResource(R.string.assignments_my_work),
            count = mineCount,
            icon = Icons.Default.Person,
            onClick = { onSelect(DrawerTab.MIAS) }
        )
        PendingTab(
            modifier = Modifier.weight(1f),
            selected = selectedTab == DrawerTab.DISPO,
            label = stringResource(R.string.assignments_available),
            count = availableCount,
            icon = Icons.Default.Inventory2,
            onClick = { onSelect(DrawerTab.DISPO) }
        )
    }
}

@Composable
private fun PendingTab(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(11.dp),
        color = if (selected) Color.White else Color.Transparent,
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
                tint = if (selected) PmmBlueDeep else PmmTextMuted,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                color = if (selected) PmmTextPrimary else PmmTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.width(7.dp))
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) PmmNavyVariant else PmmNavyBorder
            ) {
                Text(
                    text = count.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    color = if (selected) PmmBlueDeep else PmmTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun PendingSearch(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = PmmTextSecondary
            )
        },
        trailingIcon = {
            if (value.isNotBlank()) {
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
                text = stringResource(R.string.assignments_search_hint),
                color = PmmTextMuted,
                fontSize = 13.sp
            )
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = PmmBgSurface,
            unfocusedContainerColor = PmmBgSurface,
            focusedBorderColor = PmmBlueDeep,
            unfocusedBorderColor = PmmNavyBorder,
            cursorColor = PmmBlueDeep
        )
    )
}

@Composable
private fun StatusFilters(
    selected: FilterStatus,
    counts: Map<FilterStatus, Int>,
    onSelect: (FilterStatus) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(FilterStatus.entries) { status ->
            val visual = filterVisual(status)
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = {
                    Text(
                        text = "${stringResource(visual.labelRes)} ${counts[status] ?: 0}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = PmmBgSurface,
                    labelColor = PmmTextSecondary,
                    iconColor = visual.color,
                    selectedContainerColor = visual.color,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = Color.White
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected == status,
                    borderColor = PmmNavyBorder,
                    selectedBorderColor = visual.color
                )
            )
        }
    }
}

private data class FilterVisual(
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: ImageVector,
    val color: Color
)

private fun filterVisual(status: FilterStatus): FilterVisual {
    return when (status) {
        FilterStatus.TOTAL -> FilterVisual(R.string.assignments_filter_all, Icons.Default.Inventory2, PmmBlueDeep)
        FilterStatus.PENDIENTE -> FilterVisual(R.string.assignments_filter_pending, Icons.Default.Description, PmmTextSecondary)
        FilterStatus.REVISION -> FilterVisual(R.string.assignments_filter_in_progress, Icons.Default.Refresh, PmmAmberDark)
        FilterStatus.PAUSADA -> FilterVisual(R.string.assignments_filter_paused, Icons.Default.HourglassTop, Color(0xFF4F46E5))
        FilterStatus.FINALIZADO -> FilterVisual(R.string.assignments_filter_completed, Icons.Default.TaskAlt, PmmGreenDark)
    }
}

@Composable
private fun PendingEntriesGrid(
    entries: List<EntradaPendienteDto>,
    currentUsername: String?,
    waitingUnlock: Set<String>,
    selectedTab: DrawerTab,
    truncated: Boolean,
    onEntryClick: (EntradaPendienteDto) -> Unit,
    onUnlockClick: (EntradaPendienteDto) -> Unit
) {
    val context = LocalContext.current
    val assigned = entries.filter { it.esAsignada == true }
    val regular = entries.filterNot { it.esAsignada == true }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 112.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (assigned.isNotEmpty()) {
            sectionHeader(
                title = context.getString(R.string.assignments_assigned_to_me),
                subtitle = context.getString(R.string.assignments_sorted_by_priority),
                count = assigned.size
            )
            items(assigned, key = { "assigned_${it.crefAgrupada}" }) { entry ->
                PendingEntryCard(
                    entry = entry,
                    currentUsername = currentUsername,
                    waitingUnlock = entry.crefAgrupada in waitingUnlock,
                    onEntryClick = { onEntryClick(entry) },
                    onUnlockClick = { onUnlockClick(entry) }
                )
            }
        }

        if (regular.isNotEmpty()) {
            sectionHeader(
                title = if (selectedTab == DrawerTab.MIAS) {
                    context.getString(R.string.assignments_in_progress)
                } else {
                    context.getString(R.string.assignments_entries)
                },
                subtitle = if (selectedTab == DrawerTab.MIAS) {
                    context.getString(R.string.assignments_opened_by_you)
                } else {
                    context.getString(R.string.assignments_choose_mode_hint)
                },
                count = regular.size
            )
            items(regular, key = { "regular_${it.crefAgrupada}" }) { entry ->
                PendingEntryCard(
                    entry = entry,
                    currentUsername = currentUsername,
                    waitingUnlock = entry.crefAgrupada in waitingUnlock,
                    onEntryClick = { onEntryClick(entry) },
                    onUnlockClick = { onUnlockClick(entry) }
                )
            }
        }

        if (truncated && selectedTab == DrawerTab.DISPO) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = PmmNavyVariant,
                    border = BorderStroke(1.dp, PmmBlueLight.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = PmmBlueDeep
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.assignments_truncated_hint),
                            color = PmmTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
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
    onEntryClick: () -> Unit,
    onUnlockClick: () -> Unit
) {
    val status = calculateEntryStatus(entry)
    val visual = entryVisual(status.lifecycle)
    val locked = isEntryLocked(entry, currentUsername)
    val haptic = LocalHapticFeedback.current

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
                if (entry.esAsignada == true) {
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
                    }
                    Spacer(Modifier.width(10.dp))
                    StatusBadge(status = status, visual = visual)
                }

                Spacer(Modifier.height(14.dp))
                EntryProgress(status = status, visual = visual)
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
    status: EntryStatusInfo,
    visual: EntryVisual
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.assignments_progress),
                color = PmmTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.assignments_progress_count,
                    status.completedInvoices,
                    status.totalInvoices
                ),
                color = PmmTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${status.progressPercent}%",
                color = visual.color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { status.progressPercent / 100f },
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
        RevisionModeSelectorContent(
            crefAgrupada = entry.crefAgrupada,
            contextLabel = entry.nombreCliente?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.common_customer_unavailable),
            facturas = "${entry.facturasCompletas}/${entry.totalFacturas}",
            partidas = "${entry.partidasRevisadas}/${entry.totalPartidas}",
            preferredMode = preferredMode,
            isOpening = isOpening,
            onModeSelected = onModeSelected
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
        items(6) {
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
private fun PendingEmptyState(
    tab: DrawerTab,
    hasFilters: Boolean,
    onClearFilters: () -> Unit
) {
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
                    imageVector = if (hasFilters) Icons.Default.Search else Icons.Default.TaskAlt,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = when {
                    hasFilters -> stringResource(R.string.assignments_no_matches)
                    tab == DrawerTab.MIAS -> stringResource(R.string.assignments_no_active_reviews)
                    else -> stringResource(R.string.assignments_no_available_entries)
                },
                color = PmmTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    hasFilters -> stringResource(R.string.assignments_try_another_filter)
                    tab == DrawerTab.MIAS -> stringResource(R.string.assignments_taken_items_here)
                    else -> stringResource(R.string.assignments_list_updates)
                },
                color = PmmTextSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
            if (hasFilters) {
                Spacer(Modifier.height(18.dp))
                OutlinedButton(
                    onClick = onClearFilters,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterAltOff,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.common_clear_filters),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = PmmYellow.copy(alpha = 0.3f),
                spotColor = PmmYellow.copy(alpha = 0.35f)
            )
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(18.dp),
        color = PmmYellow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = PmmNavyDark,
                modifier = Modifier.size(23.dp)
            )
            Spacer(Modifier.width(9.dp))
            Column {
                Text(
                    text = stringResource(R.string.assignments_scan_entry),
                    color = PmmNavyDark,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = stringResource(R.string.assignments_camera_or_gun),
                    color = PmmNavyDark.copy(alpha = 0.72f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
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
            .padding(start = 12.dp, end = 12.dp, bottom = 76.dp)
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
