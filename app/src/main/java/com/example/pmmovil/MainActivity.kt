package com.example.pmmovil

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.pmmovil.ui.revision.MODO_ETIQUETAS
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.scanner.ScanActionsRoute
import com.example.pmmovil.ui.scanactions.ScanActionsScreen
import com.example.pmmovil.ui.scanner.ScanProfile
import com.example.pmmovil.ui.scanner.UniversalScannerScreen
import com.example.pmmovil.data.scanner.HardwareBarcodeDecoder
import com.example.pmmovil.data.scanner.ScanEventBus
import com.example.pmmovil.data.scanner.isLikelyBarcodeScanner
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.example.pmmovil.ui.auth.LoginScreen
import com.example.pmmovil.ui.theme.PMMovilTheme
import com.example.pmmovil.ui.home.HomeScreen
import com.example.pmmovil.ui.home.resolveHomeModuleDestination
import com.example.pmmovil.ui.ar.EntryArScreen
import dagger.hilt.android.AndroidEntryPoint
import android.view.KeyEvent
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.example.pmmovil.data.security.SessionEventBus
import com.example.pmmovil.data.scanner.HardwareScannerIntents
import com.example.pmmovil.data.nfc.NfcBadgeManager
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject lateinit var scanBus: ScanEventBus
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var sessionEventBus: SessionEventBus
    @Inject lateinit var warehouseHubService: WarehouseHubService
    @Inject lateinit var fcmTokenRepository: com.example.pmmovil.data.repository.FcmTokenRepository

    private val barcodeDecoder = HardwareBarcodeDecoder()

    private companion object {
        // Tras la última tecla de una ráfaga sin Enter, espera esto antes de cerrar el código.
        const val VIRTUAL_BURST_FLUSH_MILLIS = 150L
    }

    override fun onResume() {
        super.onResume()
        if (authRepository.hasActiveSession()) {
            lifecycleScope.launch { fcmTokenRepository.registerCurrentToken() }
        }
        // El modo lector pertenece a la actividad completa, no a LoginScreen.
        // De esta forma sigue interceptando etiquetas al navegar a Home y
        // Android nunca recupera el gafete para mostrar su diálogo genérico.
        NfcBadgeManager.startForegroundGuard(this)
    }

    override fun onPause() {
        NfcBadgeManager.stopForegroundGuard(this)
        super.onPause()
    }

    // Referencia pendiente de abrir por haber tocado una notificación (push
    // de "Revisión asignada" / "Entrada autorizada"). Es un mutableStateOf de
    // clase -- no de la Composable -- porque onNewIntent() llega por fuera de
    // Compose (app ya abierta, en segundo plano) y necesita poder empujar el
    // valor desde ahí igual que en el arranque en frío.
    private var pendingNotificationCref by mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationCref = intent.getStringExtra("crefAgrupada")
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val device = event.device
        val isVirtualSource = device == null || device.isVirtual
        val isScanner = isLikelyBarcodeScanner(
            deviceName = device?.name,
            isVirtual = device?.isVirtual ?: true
        )

        // Los lectores integrados (Honeywell, etc.) inyectan las teclas desde un
        // dispositivo virtual. Antes se ignoraban por completo; ahora solo se
        // tratan como lector si llegan en ráfaga, y el teclado en pantalla
        // (que tecleado a mano nunca llega a esa velocidad) queda intacto.
        if (isVirtualSource && event.action == KeyEvent.ACTION_UP && scanBurstActive) {
            return true
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            val isTerminator = event.keyCode == KeyEvent.KEYCODE_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                event.keyCode == KeyEvent.KEYCODE_TAB

            if (isTerminator) {
                val code = if (isScanner) {
                    barcodeDecoder.complete()
                } else {
                    barcodeDecoder.completeIfLikelyScan()
                }
                if (isVirtualSource) {
                    scanFlushHandler.removeCallbacks(flushVirtualBurst)
                    scanBurstActive = false
                }
                if (!code.isNullOrBlank()) {
                    sendHardwareScan(code)
                    return true
                }
            } else {
                val characters = event.characters
                if (!characters.isNullOrEmpty()) {
                    characters.forEach { character ->
                        barcodeDecoder.append(
                            character = character,
                            eventTimeMillis = event.eventTime
                        )
                    }
                } else {
                    val unicodeChar = event.getUnicodeChar(event.metaState)
                    if (unicodeChar != 0) {
                        barcodeDecoder.append(
                            character = unicodeChar.toChar(),
                            eventTimeMillis = event.eventTime
                        )
                    }
                }
                if (isVirtualSource && barcodeDecoder.isBurstSoFar()) {
                    // Ya es ráfaga de lector: se consumen las teclas restantes y,
                    // si el lector no manda Enter, se cierra por inactividad.
                    scanBurstActive = true
                    scanFlushHandler.removeCallbacks(flushVirtualBurst)
                    scanFlushHandler.postDelayed(flushVirtualBurst, VIRTUAL_BURST_FLUSH_MILLIS)
                }
            }
        }

        // Un lector HID reconocido se consume siempre para que no escriba en inputs.
        // Un teclado externo generico solo dispara scan si el patron fue rapido.
        return when {
            isScanner -> true
            isVirtualSource && scanBurstActive -> true
            else -> super.dispatchKeyEvent(event)
        }
    }

    private val scanFlushHandler = Handler(Looper.getMainLooper())
    private var scanBurstActive = false

    private val flushVirtualBurst = Runnable {
        scanBurstActive = false
        val code = barcodeDecoder.completeIfLikelyScan()
        if (!code.isNullOrBlank()) {
            sendHardwareScan(code)
        }
    }

    private fun sendHardwareScan(code: String) {
        val scanIntent = Intent(HardwareScannerIntents.ACTION_PMMOVIL_SCAN).apply {
            putExtra("data", code)
            putExtra(HardwareScannerIntents.EXTRA_PMMOVIL_SOURCE, "HID")
            putExtra(HardwareScannerIntents.EXTRA_PMMOVIL_LABEL_TYPE, "HID")
            setPackage(packageName)
        }
        sendBroadcast(scanIntent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingNotificationCref = intent?.getStringExtra("crefAgrupada")
        setContent {
            PMMovilTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = LocalContext.current

                    // Android 13+ exige permiso en runtime para mostrar
                    // notificaciones del sistema (Revisión asignada, Entrada
                    // autorizada vía FCM cuando la app está en segundo plano).
                    val notificationPermissionLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { /* si lo niega, el push igual llega -- solo sin bandeja */ }

                    // Tap en la notificación (push de FCM) -> ir directo a la
                    // entrada, en vez de dejar al usuario en el home a que la
                    // busque en su lista. Reacciona tanto al arranque en frío
                    // (onCreate) como a la app ya abierta en segundo plano
                    // (onNewIntent), porque ambos escriben en el mismo estado.
                    LaunchedEffect(pendingNotificationCref) {
                        val cref = pendingNotificationCref
                        if (cref != null && authRepository.hasActiveSession()) {
                            // Una notificación ya es una intención concreta:
                            // entra directo a la revisión normal y no deja un
                            // selector intermedio en el historial de atrás.
                            navController.navigate("detalle/$cref")
                        }
                        pendingNotificationCref = null
                    }

                    LaunchedEffect(Unit) {
                        if (authRepository.hasActiveSession()) {
                            warehouseHubService.startConnection()
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    LaunchedEffect(Unit) {
                        warehouseHubService.preregistroCreadoFlow.collect { preregistro ->
                            Toast.makeText(
                                context,
                                "Nuevo pre-registro: ${preregistro.folio} · ${preregistro.nunidad} (${preregistro.placas})",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    LaunchedEffect(Unit) {
                        sessionEventBus.sessionExpired.collect {
                            warehouseHubService.stopConnection()
                            Toast.makeText(
                                context,
                                "Tu sesión ha expirado. Por favor, inicia sesión de nuevo.",
                                Toast.LENGTH_LONG
                            ).show()
                            navController.navigate("login") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }

                    val startDestination = if (authRepository.hasActiveSession()) "home" else "login"

                    NavHost(
                        navController    = navController,
                        startDestination = startDestination,
                        enterTransition  = {
                            slideInHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(320))
                        },
                        exitTransition   = {
                            slideOutHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeOut(tween(200))
                        },
                        popEnterTransition = {
                            slideInHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { -it / 3 } +
                            fadeIn(tween(320))
                        },
                        popExitTransition = {
                            slideOutHorizontally(animationSpec = tween(320, easing = FastOutSlowInEasing)) { it } +
                            fadeOut(tween(200))
                        }
                    ) {

                        composable("login") {
                            LoginScreen(
                                onNavigateToHome = {
                                    warehouseHubService.startConnection()
                                    navController.navigate("home") {
                                        popUpTo("login") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("home") { backStackEntry ->
                            // Solo lo usa el inicio nuevo: la cámara devuelve el código por el mismo
                            // savedStateHandle["barcode"] que las demás pantallas que escanean.
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            HomeScreen(
                                onNavigateToScanner = { navController.navigate("scanner_universal") },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                // Se clasifica antes de navegar: la ruta nunca lleva un gafete (ver ScanActionsRoute).
                                onScanned = { raw -> navController.navigate(ScanActionsRoute.forScan(raw)) },
                                onNavigateToAsignaciones = {
                                    navController.navigate("asignaciones")
                                },
                                onOpenRevisionFromNotification = { crefAgrupada, mode ->
                                    val destination = when (mode) {
                                        ModePreferences.Mode.BARCODE -> "barcode/$crefAgrupada"
                                        ModePreferences.Mode.LABELS -> "detalle/$crefAgrupada?modo=$MODO_ETIQUETAS"
                                        ModePreferences.Mode.NORMAL -> "detalle/$crefAgrupada"
                                    }
                                    navController.navigate(destination)
                                },
                                onNavigateToActiveSession = { crefAgrupada ->
                                    navController.navigate("detalle/$crefAgrupada")
                                },
                                onLogout = {
                                    warehouseHubService.stopConnection()
                                    navController.navigate("login") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onOpenPrinterSettings = {
                                    navController.navigate("printing_hub")
                                },
                                onOpenSettings = {
                                    navController.navigate("ajustes")
                                },
                                onModuleTap = { module ->
                                    val destination = resolveHomeModuleDestination(module)
                                    if (destination != null) {
                                        navController.navigate(destination)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "El módulo todavía no tiene una pantalla configurada.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }

                        // Lo que se puede hacer con un código leído. Al elegir una acción esta pantalla se reemplaza por
                        // la de destino, de modo que «atrás» regresa a donde se escaneó y no a la hoja de acciones.
                        composable(ScanActionsRoute.PATTERN) {
                            ScanActionsScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onAction = { route ->
                                    navController.navigate(route) {
                                        popUpTo(ScanActionsRoute.PATTERN) { inclusive = true }
                                    }
                                },
                                onReviewModeSelected = { reference, mode ->
                                    val encodedReference = Uri.encode(reference)
                                    val destination = when (mode) {
                                        ModePreferences.Mode.NORMAL -> "detalle/$encodedReference"
                                        ModePreferences.Mode.LABELS -> "detalle/$encodedReference?modo=$MODO_ETIQUETAS"
                                        ModePreferences.Mode.BARCODE -> "barcode/$encodedReference"
                                    }
                                    navController.navigate(destination) {
                                        popUpTo(ScanActionsRoute.PATTERN) { inclusive = true }
                                    }
                                },
                                // Una pieza relacionada abre su propia ficha encima de esta: «atrás» vuelve a la anterior.
                                onOpenCode = { related -> navController.navigate(ScanActionsRoute.forScan(related)) }
                            )
                        }

                        composable("ar_entradas") {
                            EntryArScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onOpenEntry = { reference ->
                                    navController.navigate("entrada_editar/${Uri.encode(reference)}")
                                },
                                onOpenReview = { reference ->
                                    navController.navigate("detalle/${Uri.encode(reference)}")
                                },
                                // Una caja o una relación abren su ficha completa encima del AR: «atrás» vuelve a la cámara.
                                onOpenCode = { code -> navController.navigate(ScanActionsRoute.forScan(code)) }
                            )
                        }

                        composable("bodega_lista") { backStackEntry ->
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.bodega.BodegaListadoScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNuevaEntradaManual = { navController.navigate("entrada_nueva") },
                                onNuevaEntradaConQr = { navController.navigate("entrada_nueva?start=scan") },
                                onEntradaSeleccionada = { cref ->
                                    navController.navigate("entrada_editar/$cref")
                                },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.QrOnly.name}"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable(
                            "entrada_nueva?barcode={barcode}&start={start}",
                            arguments = listOf(
                                optionalBarcodeArgument,
                                // start=scan: se eligió «Escanear QR de caseta» en el modal del listado.
                                navArgument("start") {
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.entrada.EntradaScreen(
                                startWithScan = backStackEntry.arguments?.getString("start") == "scan",
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    // Perfil QR-only + zoom inicial 1.8x para
                                    // detectar etiquetas de bodega desde mas distancia.
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.EntradaBodegaQR.name}&zoom=1.8"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                onNavigateToList = {
                                    // Tras guardar + imprimir/cancelar etiquetas: al listado de
                                    // entradas, no de vuelta a lo que haya antes en el backstack.
                                    navController.navigate("bodega_lista") {
                                        popUpTo("bodega_lista") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            )
                        }

                        composable("entrada_editar/{cref}") { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("cref") ?: ""
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.entrada.EntradaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.EntradaBodegaQR.name}&zoom=1.8"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                crefToLoad = cref
                            )
                        }

                        // Compatibilidad hacia atrás por si algo quedó apuntando a "entrada"
                        composable("entrada") {
                            navController.navigate("bodega_lista") {
                                popUpTo("entrada") { inclusive = true }
                            }
                        }

                        composable("mapa_bodega") {
                            com.example.pmmovil.ui.warehouse.WarehouseMapScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToEntrada = { cref ->
                                    navController.navigate("entrada_editar/$cref")
                                }
                            )
                        }

                        composable(
                            "salida?barcode={barcode}",
                            arguments = listOf(optionalBarcodeArgument)
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.salida.SalidaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable(
                            "relacion?barcode={barcode}",
                            arguments = listOf(optionalBarcodeArgument)
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.relacion.RelacionScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.QrOnly.name}"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable("caseta") { backStackEntry ->
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.caseta.CasetaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = { navController.navigate("scanner") },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable(
                            "caseta_hub?barcode={barcode}",
                            arguments = listOf(optionalBarcodeArgument)
                        ) { backStackEntry ->
                            // El escaner del hub sirve para BUSCAR una caja, por eso
                            // acepta codigo de barras ademas de QR (a diferencia del
                            // de la pantalla de entrada, que es solo QR de pre-registro).
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.casetahub.CasetaHubScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onPreregistro = { navController.navigate("caseta_preregistro") },
                                onNuevaEntrada = { navController.navigate("caseta_entrada") },
                                onSalida = { navController.navigate("salida_caseta") },
                                onAbrirCaja = { casetaId ->
                                    navController.navigate("caseta_entrada_editar/${Uri.encode(casetaId)}")
                                },
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onVerMapaCamiones = { navController.navigate("caseta_en_camino_map") },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        // Pre-registro: es la misma pantalla de entrada, pero abre el
                        // lector de QR de una vez. El guardia escanea y el formulario
                        // ya viene con linea, unidad, placas y operador.
                        composable(
                            "caseta_preregistro",
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.casetaentrada.CasetaEntradaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.QrOnly.name}"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                onOpenLabelDesigner = { navController.navigate("zebra_caseta_label_designer") },
                                startWithCamera = false,
                                startWithPreregistroScan = true
                            )
                        }

                        composable(
                            "caseta_en_camino_map",
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) {
                            com.example.pmmovil.ui.encamino.EnCaminoMapScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable("caseta_buscar") { backStackEntry ->
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.casetasearch.CasetaSearchEditScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = { navController.navigate("scanner") },
                                onEditRecord = { casetaId ->
                                    navController.navigate("caseta_entrada_editar/${Uri.encode(casetaId)}")
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable(
                            "salida_caseta?barcode={barcode}",
                            arguments = listOf(optionalBarcodeArgument),
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.salidacaseta.SalidaCasetaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = { navController.navigate("scanner") },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                }
                            )
                        }

                        composable(
                            "caseta_entrada_editar/{casetaId}",
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) { backStackEntry ->
                            val casetaId = Uri.decode(backStackEntry.arguments?.getString("casetaId").orEmpty())
                            com.example.pmmovil.ui.casetaentrada.CasetaEntradaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = { },
                                casetaId = casetaId,
                                onSaved = { navController.popBackStack() },
                                onOpenLabelDesigner = { navController.navigate("zebra_caseta_label_designer") },
                                startWithCamera = false
                            )
                        }

                        composable(
                            "caseta_entrada?barcode={barcode}",
                            arguments = listOf(optionalBarcodeArgument),
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.rememberBarcodeResult()
                            com.example.pmmovil.ui.casetaentrada.CasetaEntradaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.QrOnly.name}"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                onOpenLabelDesigner = { navController.navigate("zebra_caseta_label_designer") },
                                startWithCamera = false
                            )
                        }

                        composable(
                            "caseta_entrada_camera",
                            enterTransition = { zEntra() },
                            exitTransition = { zSale() },
                            popEnterTransition = { zVuelve() },
                            popExitTransition = { zCierra() }
                        ) { backStackEntry ->
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            com.example.pmmovil.ui.casetaentrada.CasetaEntradaScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToScanner = {
                                    navController.navigate(
                                        "scanner?profile=${ScanProfile.QrOnly.name}"
                                    )
                                },
                                barcodeScanResult = barcodeResult,
                                onBarcodeScanConsumed = {
                                    backStackEntry.savedStateHandle["barcode"] = null
                                },
                                onOpenLabelDesigner = { navController.navigate("zebra_caseta_label_designer") },
                                startWithCamera = true
                            )
                        }

                // La identificación pertenece a cada usuario, no al área de
                // Sistemas: cualquier sesión autenticada puede configurar su
                // propio NFC y su propio rostro. Las herramientas de impresora
                // y administración conservan su restricción más abajo.
                composable("ajustes") {
                    com.example.pmmovil.ui.settings.SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onOpenNfcAssociation = { navController.navigate("ajustes/nfc") },
                        onOpenRostro = { navController.navigate("ajustes/rostro") }
                    )
                }

                // Alta del rostro. Solo se llega desde una sesión autenticada:
                // eso impide registrar una cara sobre otra cuenta.
                //
                // Las hijas de Ajustes entran en eje Z (crecen desde dentro) en
                // vez de deslizarse de lado: no es una pantalla "al lado", es
                // la misma tarjeta que se abre. El deslizamiento horizontal se
                // queda para moverse entre secciones hermanas.
                composable(
                    "ajustes/rostro",
                    enterTransition = { zEntra() },
                    exitTransition = { zSale() },
                    popEnterTransition = { zVuelve() },
                    popExitTransition = { zCierra() }
                ) {
                    com.example.pmmovil.ui.rostro.EnrolamientoRostroScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(
                    "ajustes/nfc",
                    enterTransition = { zEntra() },
                    exitTransition = { zSale() },
                    popEnterTransition = { zVuelve() },
                    popExitTransition = { zCierra() }
                ) {
                    com.example.pmmovil.ui.settings.nfc.NfcAssociationScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }

                composable("printing_hub") {
                    if (authRepository.isSystemsArea()) {
                        com.example.pmmovil.ui.printer.PrintingHubScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenLabelPicker = { navController.navigate("label_type_picker") },
                            onOpenPrinterCatalog = { navController.navigate("printer_catalog") }
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("label_type_picker") {
                    if (authRepository.isSystemsArea()) {
                        com.example.pmmovil.ui.printer.LabelTypePickerScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onOpenBodegaDesigner = { navController.navigate("zebra_label_designer") },
                            onOpenCasetaDesigner = { navController.navigate("zebra_caseta_label_designer") }
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("printer_catalog") {
                    if (authRepository.isSystemsArea()) {
                        com.example.pmmovil.ui.printer.PrinterCatalogScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNewPrinter = { navController.navigate("printer_detail/0") },
                            onSelectPrinter = { id -> navController.navigate("printer_detail/$id") }
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("printer_detail/{printerId}") { backStackEntry ->
                    if (authRepository.isSystemsArea()) {
                        val printerId = backStackEntry.arguments?.getString("printerId")?.toIntOrNull() ?: 0
                        com.example.pmmovil.ui.printer.PrinterDetailScreen(
                            onNavigateBack = { navController.popBackStack() },
                            printerId = printerId
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("zebra_label_designer") {
                    if (authRepository.isSystemsArea()) {
                        com.example.pmmovil.ui.printer.ZebraLabelDesignerScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("zebra_caseta_label_designer") {
                    if (authRepository.isSystemsArea()) {
                        com.example.pmmovil.ui.printer.ZebraLabelDesignerScreen(
                            onNavigateBack = { navController.popBackStack() },
                            labelType = com.example.pmmovil.data.printing.ZebraLabelType.CASETA
                        )
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                }

                composable("asignaciones") {
                    com.example.pmmovil.ui.asignaciones.AsignacionesScreen(
                                onNavigateToNormal = { crefAgrupada ->
                                    navController.navigate("detalle/$crefAgrupada")
                                },
                                onNavigateToLabels = { crefAgrupada ->
                                    // Misma pantalla y mismo cierre que la revisión normal; el argumento solo
                                    // le pide abrir la cámara de etiqueta al entrar.
                                    navController.navigate("detalle/$crefAgrupada?modo=$MODO_ETIQUETAS")
                                },
                                onNavigateToBarcode = { crefAgrupada ->
                                    navController.navigate("barcode/$crefAgrupada")
                                },
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onNavigateToScanner = {
                                    navController.navigate("scanner?profile=${com.example.pmmovil.ui.scanner.ScanProfile.WarehouseProduct.name}")
                                }
                            )
                        }

                        composable(
                            route = "detalle/{crefAgrupada}?modo={modo}",
                            arguments = listOf(
                                navArgument("modo") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                }
                            )
                        ) { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("crefAgrupada")
                            val barcodeResult by backStackEntry.savedStateHandle
                                .getStateFlow<String?>("barcode", null)
                                .collectAsState()
                            val photosChanged by backStackEntry.savedStateHandle
                                .getStateFlow("photosChanged", false)
                                .collectAsState()
                            if (cref != null) {
                                com.example.pmmovil.ui.revision.DetalleRevisionScreen(
                                    onNavigateBack       = { navController.popBackStack() },
                                    onNavigateAfterForcedClose = {
                                        navController.navigate("asignaciones") {
                                            popUpTo("home") { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    },
                                    onNavigateToCamera   = { cnofact, cclienteid, esDiscrepancia ->
                                        navController.navigate(
                                            // cnofact puede traer "/" (ej. SN-03335/26): hay que escaparlo
                                            // o la ruta queda con segmentos de más y no coincide con el grafo.
                                            "camera/${Uri.encode(cref)}/${Uri.encode(cnofact)}/${Uri.encode(cclienteid)}?esDiscrepancia=$esDiscrepancia"
                                        )
                                    },
                                    onNavigateToGallery  = { cnofact, cclienteid ->
                                        navController.navigate(
                                            "gallery/${Uri.encode(cref)}/${Uri.encode(cnofact)}/${Uri.encode(cclienteid)}"
                                        )
                                    },
                                    onNavigateToScanner  = {
                                        // Unica pantalla que lee mercancia de
                                        // cliente, no etiquetas de la empresa.
                                        navController.navigate(
                                            "scanner?profile=${ScanProfile.WarehouseProduct.name}"
                                        )
                                    },
                                    barcodeScanResult    = barcodeResult,
                                    onBarcodeScanConsumed = {
                                        backStackEntry.savedStateHandle["barcode"] = null
                                    },
                                    photosChanged = photosChanged,
                                    onPhotosChangedConsumed = {
                                        backStackEntry.savedStateHandle["photosChanged"] = false
                                    }
                                )
                            }
                        }

                        composable("barcode/{crefAgrupada}") { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("crefAgrupada") ?: ""
                            val photosChanged by backStackEntry.savedStateHandle
                                .getStateFlow("photosChanged", false)
                                .collectAsState()
                            com.example.pmmovil.ui.revision.RevisionBarcodeScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateAfterForcedClose = {
                                    navController.navigate("asignaciones") {
                                        popUpTo("home") { inclusive = false }
                                        launchSingleTop = true
                                    }
                                },
                                onNavigateToEvidence = { cclienteid ->
                                    navController.navigate(
                                        "camera_barcode/$cref/$cclienteid"
                                    )
                                },
                                photosChanged = photosChanged,
                                onPhotosChangedConsumed = {
                                    backStackEntry.savedStateHandle["photosChanged"] = false
                                }
                            )
                        }

                        composable("pdf/{creferencia}") { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("creferencia") ?: ""
                            com.example.pmmovil.ui.pdf.PdfViewerScreen(
                                creferencia = cref,
                                onClose     = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "camera/{crefAgrupada}/{cnofact}/{cclienteid}?esDiscrepancia={esDiscrepancia}",
                            arguments = listOf(
                                navArgument("esDiscrepancia") {
                                    type = NavType.BoolType
                                    defaultValue = false
                                }
                            )
                        ) { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("crefAgrupada") ?: ""
                            val cno  = backStackEntry.arguments?.getString("cnofact") ?: ""
                            val cli  = backStackEntry.arguments?.getString("cclienteid") ?: ""
                            val esDiscrepancia = backStackEntry.arguments?.getBoolean("esDiscrepancia") ?: false
                            com.example.pmmovil.ui.camera.CameraScreen(
                                crefAgrupada = cref,
                                cnofact      = cno,
                                cclienteid   = cli,
                                esDiscrepancia = esDiscrepancia,
                                onClose      = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("photosChanged", true)
                                    navController.popBackStack()
                                }
                            )
                        }

                        composable("gallery/{crefAgrupada}/{cnofact}/{cclienteid}") { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("crefAgrupada") ?: ""
                            val cno  = backStackEntry.arguments?.getString("cnofact") ?: ""
                            val cli  = backStackEntry.arguments?.getString("cclienteid") ?: ""
                            com.example.pmmovil.ui.camera.GalleryScreen(
                                crefAgrupada = cref,
                                cnofact      = cno,
                                cclienteid   = cli,
                                onClose      = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("photosChanged", true)
                                    navController.popBackStack()
                                },
                                onTakePhotos = {
                                    navController.navigate(
                                        "camera/${Uri.encode(cref)}/${Uri.encode(cno)}/${Uri.encode(cli)}?esDiscrepancia=false"
                                    )
                                }
                            )
                        }

                        composable("camera_barcode/{crefAgrupada}/{cclienteid}") { backStackEntry ->
                            val cref = backStackEntry.arguments?.getString("crefAgrupada") ?: ""
                            val cli = backStackEntry.arguments?.getString("cclienteid") ?: ""
                            com.example.pmmovil.ui.camera.CameraScreen(
                                crefAgrupada = cref,
                                cnofact = "",
                                cclienteid = cli,
                                onClose = {
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("photosChanged", true)
                                    navController.popBackStack()
                                }
                            )
                        }

                        // El escáner del inicio: solo QR, con el tipo del código sobre el visor y el cambio a «Varios
                        // (AR)». Devuelve la lectura igual que "scanner": por savedStateHandle["barcode"] del inicio.
                        composable("scanner_universal") {
                            UniversalScannerScreen(
                                onBarcodeScanned = { barcode ->
                                    navController.previousBackStackEntry
                                        ?.savedStateHandle
                                        ?.set("barcode", barcode)
                                    navController.popBackStack()
                                },
                                onClose = { navController.popBackStack() },
                                // El AR de entradas es de Revisión: sin ese módulo no se ofrece el modo.
                                onOpenAr = if (authRepository.hasPermission(MobileModuleIds.REVIEW, "Lectura")) {
                                    {
                                        navController.navigate("ar_entradas") {
                                            popUpTo("scanner_universal") { inclusive = true }
                                        }
                                    }
                                } else {
                                    null
                                }
                            )
                        }

                        // El perfil y el zoom son opcionales: navegar a
                        // "scanner" a secas sigue funcionando (PMM labels, 1x).
                        // Pantallas de bodega usan
                        // "scanner?profile=EntradaBodegaQR&zoom=1.8" para
                        // maximizar el rango de lectura.
                        composable(
                            route = "scanner?profile={profile}&zoom={zoom}",
                            arguments = listOf(
                                navArgument("profile") {
                                    type = NavType.StringType
                                    defaultValue = ScanProfile.PmmLabel.name
                                },
                                navArgument("zoom") {
                                    type = NavType.StringType
                                    defaultValue = "1.0"
                                }
                            )
                        ) { backStackEntry ->
                            val profile = runCatching {
                                ScanProfile.valueOf(
                                    backStackEntry.arguments?.getString("profile").orEmpty()
                                )
                            }.getOrDefault(ScanProfile.PmmLabel)

                            val startZoom = backStackEntry.arguments
                                ?.getString("zoom")
                                ?.toFloatOrNull()
                                ?.coerceIn(1f, 4f)
                                ?: 1f

                            com.example.pmmovil.ui.scanner.ScannerScreen(
                                profile = profile,
                                startZoomRatio = startZoom,
                                onBarcodeScanned = { barcode ->
                                    // Pendientes procesa por bus; revisión recibe el
                                    // valor directamente en su back stack.
                                    val previousEntry =
                                        navController.previousBackStackEntry
                                    previousEntry
                                        ?.savedStateHandle
                                        ?.set("barcode", barcode)
                                    if (previousEntry?.destination?.route == "asignaciones") {
                                        scanBus.emit(barcode)
                                    }
                                    navController.popBackStack()
                                },
                                onClose = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Transición en eje Z, para bajar un nivel dentro de una misma sección ─────
//
// Material la llama "shared Z-axis": lo que entra crece desde atrás y lo que
// queda se va hacia adelante. Comunica jerarquía (entré en esto) donde el
// deslizamiento lateral comunicaría hermandad (me moví al de al lado).

private const val Z_MS = 340

/**
 * Código que llega ya leído a una pantalla que escanea (viene de la hoja de acciones). La pantalla lo recibe por el mismo
 * `savedStateHandle["barcode"]` que usa al volver de la cámara, así que no necesita saber de dónde vino.
 */
private val optionalBarcodeArgument = navArgument("barcode") {
    type = NavType.StringType
    nullable = true
    defaultValue = null
}

private const val BARCODE_KEY = "barcode"

/**
 * El código que la pantalla debe procesar: primero el que trae la ruta (lo manda la hoja de acciones) y después los que
 * devuelva la cámara, todos por `savedStateHandle["barcode"]`.
 *
 * Navigation NO siembra el `savedStateHandle` de la entrada con los argumentos de la ruta (solo el de los ViewModels),
 * así que el argumento se copia aquí una sola vez. La bandera es `rememberSaveable` para que girar la pantalla no
 * vuelva a entregar un código que la pantalla ya consumió.
 */
@Composable
private fun NavBackStackEntry.rememberBarcodeResult(): State<String?> {
    val argument = remember(this) { arguments?.getString(BARCODE_KEY) }
    var delivered by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(id) {
        if (!delivered && argument != null) {
            delivered = true
            savedStateHandle[BARCODE_KEY] = argument
        }
    }
    return savedStateHandle.getStateFlow<String?>(BARCODE_KEY, null).collectAsState()
}

private fun zEntra(): EnterTransition =
    scaleIn(tween(Z_MS, easing = FastOutSlowInEasing), initialScale = 0.90f) +
        fadeIn(tween(Z_MS))

private fun zSale(): ExitTransition =
    scaleOut(tween(Z_MS, easing = FastOutSlowInEasing), targetScale = 1.08f) +
        fadeOut(tween(180))

private fun zVuelve(): EnterTransition =
    scaleIn(tween(Z_MS, easing = FastOutSlowInEasing), initialScale = 1.08f) +
        fadeIn(tween(Z_MS))

private fun zCierra(): ExitTransition =
    scaleOut(tween(Z_MS, easing = FastOutSlowInEasing), targetScale = 0.90f) +
        fadeOut(tween(180))
