package com.example.pmmovil.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedVisibility
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.R
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.ui.auth.AuthViewModel
import com.example.pmmovil.ui.home.components.BrandedHeroHeader
import com.example.pmmovil.ui.home.components.ModuleGrid
import com.example.pmmovil.ui.home.components.ModuleItem
import com.example.pmmovil.ui.home.components.NewHomeContent
import com.example.pmmovil.ui.home.components.SectionHeader
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.home.components.SystemFooter
import com.example.pmmovil.ui.home.components.adminModules
import com.example.pmmovil.ui.home.components.casetaModules
import com.example.pmmovil.ui.home.components.defaultModules
import com.example.pmmovil.ui.home.components.CasetaFeaturedCard
import com.example.pmmovil.ui.home.components.CasetaLayoutType
import com.example.pmmovil.ui.home.components.HomeModulesError
import com.example.pmmovil.ui.home.components.HomeNoModulesAssigned
import com.example.pmmovil.ui.home.components.HomePreparingModules
import com.example.pmmovil.ui.home.components.HorizontalModuleCard
import com.example.pmmovil.ui.home.components.PlaceholderDottedCard
import com.example.pmmovil.ui.home.components.ModuleCardV2
import androidx.compose.ui.graphics.Color
import com.example.pmmovil.ui.home.components.BentoHeroCard
import com.example.pmmovil.ui.home.components.BentoSatelliteCard
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.notifications.NotificationInboxScreen
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * HOME — Hub de módulos Bento Grid 2026.
 *
 * Filosofía 2026:
 *  - PMMovil es multi-módulo y dinámico según permisos.
 *  - El módulo operativo principal (Revisión o Caseta) toma rol de Hero Card.
 *  - Los módulos secundarios se acomodan en un Bento Grid adaptativo y equilibrado.
 *  - Cero huecos vacíos: la interfaz se re-calcula simétricamente para 100% del ancho.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAsignaciones: () -> Unit,
    onOpenRevisionFromNotification: (String, ModePreferences.Mode) -> Unit = { _, _ -> },
    onNavigateToActiveSession: (String) -> Unit = {},
    onLogout: () -> Unit,
    onOpenPrinterSettings: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onModuleTap: (String) -> Unit = {},
    // Solo los usa el inicio nuevo (BuildConfig.USE_NEW_HOME): abrir la cámara, y recibir el código leído, ya sea de la
    // cámara (barcodeScanResult) o del lector de mano (onScanned).
    onNavigateToScanner: () -> Unit = {},
    barcodeScanResult: String? = null,
    onBarcodeScanConsumed: () -> Unit = {},
    onScanned: (String) -> Unit = {},
    authViewModel: AuthViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val state by homeViewModel.state.collectAsState()
    val summary by homeViewModel.summary.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showNotifications) {
        showNotifications = false
    }

    if (BuildConfig.USE_NEW_HOME) {
        // Sin permisos resueltos la hoja de acciones diría «tu puesto no tiene acciones» de algo que sí puede hacer.
        val scanReady = state.permissionsResolved && !state.permissionsFailed && !showNotifications
        HardwareScannerListener { raw -> if (scanReady) onScanned(raw) }
        LaunchedEffect(barcodeScanResult) {
            barcodeScanResult?.takeIf(String::isNotBlank)?.let {
                onScanned(it)
                onBarcodeScanConsumed()
            }
        }
    }
    val permissions = state.permissions
    val isSystemsArea = homeViewModel.isSystemsArea()
    val hasFullAccess = isSystemsArea

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeViewModel.refreshNotificationUnreadCount()
                // Las cifras del Tablero solo existen en el inicio nuevo; el viejo no las pide.
                if (BuildConfig.USE_NEW_HOME) homeViewModel.refreshSummary()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val allActiveModules = remember(hasFullAccess, permissions) {
        val list = mutableListOf<ModuleItem>()

        // 1. Revisión (Mercancía)
        val reviewPerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.REVIEW && it.canRead }
        if (reviewPerm) {
            list.add(ModuleItem(MobileModuleIds.REVIEW, R.string.home_module_review_title, R.string.home_module_review_subtitle, Icons.Default.QrCode, com.example.pmmovil.ui.theme.PmmBlueDeep, Color(0xFFEEF3FF), enabled = true))
            list.add(ModuleItem(MobileModuleIds.REVIEW, R.string.home_module_ar_title, R.string.home_module_ar_subtitle, Icons.Default.QrCodeScanner, com.example.pmmovil.ui.theme.PmmBlueMid, Color(0xFFE0EBFF), enabled = true, routeKey = HomeModuleRouteKeys.ENTRY_AR))
        }

        // 2. Entradas (Recepción)
        val entriesPerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.WAREHOUSE_OPERATIONS && it.canRead }
        if (entriesPerm) {
            list.add(ModuleItem(MobileModuleIds.WAREHOUSE_OPERATIONS, R.string.home_module_entries_title, R.string.home_module_entries_subtitle, Icons.Default.Login, com.example.pmmovil.ui.theme.PmmBlueMid, Color(0xFFE0EBFF), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_ENTRIES))
        }

        // 3. Cargar mercancía
        val exitsPerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.WAREHOUSE_EXIT && it.canRead }
        if (exitsPerm) {
            list.add(ModuleItem(MobileModuleIds.WAREHOUSE_EXIT, R.string.home_module_load_title, R.string.home_module_load_subtitle, Icons.Default.Logout, com.example.pmmovil.ui.theme.PmmBlueLight, Color(0xFFDBEAFE), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_LOADS))
        }

        // 4. Relación (Supervisar)
        val relationPerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.WAREHOUSE_RELATION && it.canRead }
        if (relationPerm) {
            list.add(ModuleItem(MobileModuleIds.WAREHOUSE_RELATION, R.string.home_module_relation_title, R.string.home_module_relation_subtitle, Icons.Default.ViewModule, com.example.pmmovil.ui.theme.PmmNavyDark, Color(0xFFE0E7FF), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_RELATION))
        }

        // 5. Mapa (Ubicaciones)
        val mapPerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.WAREHOUSE_OPERATIONS && it.canRead }
        if (mapPerm) {
            list.add(ModuleItem(MobileModuleIds.WAREHOUSE_OPERATIONS, R.string.home_module_map_title, R.string.home_module_map_subtitle, Icons.Default.Map, com.example.pmmovil.ui.theme.PmmGreenDark, Color(0xFFD1FAE5), enabled = true, routeKey = HomeModuleRouteKeys.WAREHOUSE_MAP))
        }

        // 6. Caseta (Vigilancia)
        val gatePerm = hasFullAccess || permissions.any { it.moduloId == MobileModuleIds.GATE && it.canRead }
        if (gatePerm) {
            list.add(ModuleItem(MobileModuleIds.GATE, R.string.home_module_gate_operation_title, R.string.home_module_gate_operation_subtitle, Icons.Default.LocalShipping, com.example.pmmovil.ui.theme.PmmAmberDark, Color(0xFFFEF3C7), enabled = true, routeKey = HomeModuleRouteKeys.GATE_HUB))
        }

        // Una sola pantalla de salida de caseta. Los permisos siguen separados
        // (165 salida con relación, 166 salida manual): la tarjeta se ve con
        // Lectura en cualquiera de los dos y la pantalla ofrece solo lo permitido.
        val relationExitPerm = hasFullAccess || permissions.any {
            (it.moduloId == MobileModuleIds.GATE_RELATION_EXIT || it.moduloId == MobileModuleIds.GATE_PHYSICAL_EXIT) && it.canRead
        }
        if (relationExitPerm) {
            list.add(ModuleItem(MobileModuleIds.GATE_RELATION_EXIT, R.string.home_module_relation_exit_title, R.string.home_module_relation_exit_subtitle, Icons.Default.ArrowUpward, com.example.pmmovil.ui.theme.PmmBlueDeep, Color(0xFFE0E7FF), enabled = true, routeKey = HomeModuleRouteKeys.GATE_RELATION_EXIT))
        }

        // 7. Sistemas (Administración)
        val adminPerm = isSystemsArea
        if (adminPerm) {
            list.add(ModuleItem(MobileModuleIds.SYSTEMS_MONITOR, R.string.home_module_systems_title, R.string.home_module_systems_subtitle, Icons.Default.Settings, com.example.pmmovil.ui.theme.PmmRed, Color(0xFFFEE2E2), enabled = true, routeKey = HomeModuleRouteKeys.SYSTEMS_ADMIN))
        }

        list
    }

    val heroModule = remember(allActiveModules) {
        allActiveModules.firstOrNull { it.moduleId == MobileModuleIds.REVIEW }
            ?: allActiveModules.firstOrNull { it.moduleId == MobileModuleIds.GATE }
            ?: allActiveModules.firstOrNull()
    }

    val satelliteModules = remember(allActiveModules, heroModule) {
        allActiveModules.filter { it != heroModule }
    }

    val onModuleClick: (ModuleItem) -> Unit = { item ->
        if (item.enabled) {
            when {
                item.routeKey == HomeModuleRouteKeys.ENTRY_AR -> onModuleTap(item.routeKey)
                item.moduleId == MobileModuleIds.REVIEW -> onNavigateToAsignaciones()
                else -> onModuleTap(item.routeKey)
            }
        } else {
            scope.launch {
                snackbar.showSnackbar(
                    context.getString(
                        R.string.home_coming_soon,
                        context.getString(item.titleRes)
                    )
                )
            }
        }
    }

    val userName = homeViewModel.getUserName()
    val username = homeViewModel.getUserUsername()

    LaunchedEffect(state.errorMessage) {
        val msg = state.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbar.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        }
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PmmBgPrimary
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    homeViewModel.refresh()
                    if (BuildConfig.USE_NEW_HOME) homeViewModel.refreshSummary(force = true)
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // ── 1) HEADER HERO
                    item("header") {
                        BrandedHeroHeader(
                            userName = userName,
                            username = username,
                            notificationUnreadCount = state.notificationUnreadCount,
                            onOpenNotifications = {
                                homeViewModel.refreshNotificationUnreadCount()
                                showNotifications = true
                            },
                            onOpenPrinterSettings = onOpenPrinterSettings,
                            onOpenSettings = onOpenSettings,
                            showSystemTools = isSystemsArea,
                            onLogout = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                authViewModel.logout()
                                onLogout()
                            }
                        )
                    }

                    if (state.isPreparingModules) {
                        item("preparing") {
                            HomePreparingModules(phase = state.phase)
                        }
                    } else if (state.permissionsFailed) {
                        item("permissions-error") {
                            HomeModulesError(onRetry = { homeViewModel.refresh() })
                        }
                    } else if (allActiveModules.isEmpty()) {
                        item("empty-modules") {
                            HomeNoModulesAssigned()
                        }
                    } else if (BuildConfig.USE_NEW_HOME) {
                        item("new-home") {
                            NewHomeContent(
                                modules = allActiveModules,
                                summary = summary,
                                wide = screenWidth >= 720,
                                onScan = onNavigateToScanner,
                                onModuleClick = onModuleClick,
                                onBlocked = { stationName ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            context.getString(R.string.home_new_station_blocked, stationName)
                                        )
                                    }
                                }
                            )
                        }
                    } else if (screenWidth >= 900) {
                        // ═════════════════════════════════════════════════════════
                        // TABLETA LANDSCAPE — BENTO GRID 2026
                        // ═════════════════════════════════════════════════════════
                        item("bento-landscape") {
                            Spacer(Modifier.height(20.dp))
                            val bentoHeight = (screenHeight * 0.56f).dp.coerceIn(350.dp, 460.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(bentoHeight),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // 1) HERO CARD (Izquierda)
                                heroModule?.let { hero ->
                                    FadeInStagger(
                                        index = 1,
                                        modifier = Modifier
                                            .weight(0.42f)
                                            .fillMaxHeight()
                                    ) {
                                        BentoHeroCard(
                                            item = hero,
                                            onClick = { onModuleClick(hero) },
                                            isLandscapeTablet = true,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                // 2) SATELLITE CARDS (Derecha - Grid 2x2 o balanceado)
                                Column(
                                    modifier = Modifier
                                        .weight(0.58f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    val itemsPerRow = if (satelliteModules.size > 4) 3 else 2
                                    val rows = satelliteModules.chunked(itemsPerRow)

                                    rows.forEachIndexed { rowIndex, rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEachIndexed { colIndex, satItem ->
                                                val staggerIndex = 2 + (rowIndex * itemsPerRow) + colIndex
                                                FadeInStagger(
                                                    index = staggerIndex,
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxHeight()
                                                ) {
                                                    BentoSatelliteCard(
                                                        item = satItem,
                                                        onClick = { onModuleClick(satItem) },
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                            repeat(itemsPerRow - rowItems.size) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (screenWidth >= 600) {
                        // ═════════════════════════════════════════════════════════
                        // TABLETA PORTRAIT — BENTO GRID VERTICAL
                        // ═════════════════════════════════════════════════════════
                        item("bento-portrait-hero") {
                            Spacer(Modifier.height(18.dp))
                            heroModule?.let { hero ->
                                FadeInStagger(
                                    index = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp)
                                        .height(230.dp)
                                ) {
                                    BentoHeroCard(
                                        item = hero,
                                        onClick = { onModuleClick(hero) },
                                        isLandscapeTablet = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        item("bento-portrait-satellites") {
                            Spacer(Modifier.height(14.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                satelliteModules.chunked(2).forEachIndexed { rowIndex, rowItems ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEachIndexed { colIndex, satItem ->
                                            val staggerIndex = 2 + (rowIndex * 2) + colIndex
                                            FadeInStagger(
                                                index = staggerIndex,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                BentoSatelliteCard(
                                                    item = satItem,
                                                    onClick = { onModuleClick(satItem) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                        repeat(2 - rowItems.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // ═════════════════════════════════════════════════════════
                        // MÓVIL (Compact)
                        // ═════════════════════════════════════════════════════════
                        item("bento-mobile-hero") {
                            Spacer(Modifier.height(14.dp))
                            heroModule?.let { hero ->
                                FadeInStagger(
                                    index = 1,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .height(195.dp)
                                ) {
                                    BentoHeroCard(
                                        item = hero,
                                        onClick = { onModuleClick(hero) },
                                        isLandscapeTablet = false,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        item("bento-mobile-satellites") {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                satelliteModules.forEachIndexed { index, satItem ->
                                    FadeInStagger(
                                        index = index + 2,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        BentoSatelliteCard(
                                            item = satItem,
                                            onClick = { onModuleClick(satItem) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 3) FOOTER
                    item("footer") {
                        Spacer(Modifier.height(18.dp))
                        SystemFooter(
                            lastSyncMillis = state.lastSyncMillis,
                            onTap = { homeViewModel.refresh() }
                        )
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }

            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = PmmRed,
                    contentColor = PmmBgSurface
                )
            }

            if (showNotifications) {
                Dialog(
                    onDismissRequest = { showNotifications = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showNotifications = false }
                            )
                            .statusBarsPadding()
                            .padding(
                                top = if (screenWidth >= 900) 64.dp else 56.dp,
                                end = if (screenWidth >= 900) 24.dp else 20.dp,
                                start = 16.dp,
                                bottom = 20.dp
                            ),
                        contentAlignment = Alignment.TopEnd
                    ) {
                        Box(
                            modifier = Modifier
                                .widthIn(min = 320.dp, max = 460.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    enabled = true,
                                    onClick = {}
                                )
                        ) {
                            NotificationInboxScreen(
                                onNavigateBack = { showNotifications = false },
                                onOpenRevision = { crefAgrupada, mode ->
                                    showNotifications = false
                                    onOpenRevisionFromNotification(crefAgrupada, mode)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Animación de entrada: fade + slide-up con stagger ───────────────────────
// IMPORTANTE: usamos `rememberSaveable` (no `remember`) para que el estado
// `visible` PERSISTA cuando el LazyColumn mueve el item fuera de pantalla
// y lo trae de vuelta. Con `remember`, la animación se disparaba de nuevo
// en cada recomposición, causando "pausa" al scrollear rápido.
@Composable
private fun FadeInStagger(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val visible = rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * Motion.DurationStagger).toLong())
        visible.value = true
    }
    AnimatedVisibility(
        visible = visible.value,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(Motion.DurationEmphasized, easing = Motion.EmphasizedDecelerate)) +
                slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) { it / 4 },
        exit = fadeOut()
    ) {
        content()
    }
}
