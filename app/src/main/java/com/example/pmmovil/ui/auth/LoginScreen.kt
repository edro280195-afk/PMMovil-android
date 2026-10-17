package com.example.pmmovil.ui.auth

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pmmovil.data.nfc.NfcBadgeManager
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.data.biometria.FaceEngine
import com.example.pmmovil.ui.rostro.CamaraRostroSinPreview
import com.example.pmmovil.ui.rostro.EstadoGlifo
import com.example.pmmovil.ui.rostro.RostroFondo
import com.example.pmmovil.ui.rostro.RostroLoginViewModel
import com.example.pmmovil.ui.scanner.HardwareScannerListener
import com.example.pmmovil.ui.scanner.ScanDistance
import com.example.pmmovil.ui.scanner.SmartBarcodeCameraView
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dónde está parada la pantalla de acceso.
 *
 * Es una RUTA, no un paso. Nada avanza solo de una a otra: `ACCESO` es la casa
 * y las otras dos son desvíos que la persona elige tocando un botón y de los
 * que siempre se vuelve al mismo sitio. Por eso no hay "siguiente" ni
 * "anterior", y por eso el botón físico de atrás siempre regresa a `ACCESO`.
 */
private enum class LoginRuta { ACCESO, CAMARA_QR, MANUAL }

/**
 * El acceso a PMMovil.
 *
 * CINCO PUERTAS, NINGÚN PASILLO. Rostro, gafete NFC y lector físico escuchan
 * EN PARALELO; QR por cámara y usuario/contraseña son desvíos manuales. Ningún
 * fallo empuja al siguiente método: si el gafete no era, la pantalla vuelve a
 * quedarse escuchando a los tres. Quien decide con qué entra es la persona,
 * no la aplicación.
 *
 * QUIÉN CUESTA QUÉ. El gafete y el lector no cuestan nada: el modo lector de
 * NFC lo toma la Activity completa y el lector físico es un receiver, así que
 * los dos están armados desde que aparece la pantalla y quien llega con gafete
 * entra sin tocar nada. La cámara frontal sí cuesta -- abrirla son ~1 s de
 * respiro del HAL más la convergencia de la exposición -- así que sólo se
 * enciende cuando alguien pulsa "Ingresar", y se apaga al salir.
 *
 * EL ÁRBITRO. Los canales tienen dos caminos distintos hasta el servidor (el
 * rostro por [RostroLoginViewModel], los demás por [AuthViewModel]) y sin nadie
 * en medio podían disparar dos logins simultáneos. [LoginArbitro] reparte un
 * único turno; ver ahí el detalle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onNavigateToHome: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    rostroViewModel: RostroLoginViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    val rostro by rostroViewModel.uiState.collectAsStateWithLifecycle()

    val contexto = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val arbitro = remember { LoginArbitro() }
    var ruta by rememberSaveable { mutableStateOf(LoginRuta.ACCESO) }

    val sacudida = remember { Animatable(0f) }

    // El acceso facial sólo se ofrece si los modelos vienen en este APK, y si
    // el servidor no ha contestado ya que el módulo está apagado. No se
    // consulta nada de más: esta pantalla es anónima.
    val motorEnElApk = remember(contexto) { FaceEngine.isAvailable(contexto) }
    val rostroDisponible = motorEnElApk && !rostro.deshabilitadoEnServidor

    val actividad = contexto as? Activity
    val nfcDisponible = remember(actividad) {
        actividad != null && NfcBadgeManager.isAvailable(actividad)
    }

    val validando = authState is AuthState.Loading ||
        rostro.estado == EstadoGlifo.VERIFICANDO

    // ── Fase ──────────────────────────────────────────────────────────────────

    /**
     * La fase NO es un estado propio: se lee del ViewModel del rostro, que es
     * quien sabe de verdad si la cámara está abierta.
     *
     * Tenerla aparte se veía más simple y mentía en un caso concreto: si el
     * proceso moría con la pantalla armada, al volver se restauraba "ARMADO"
     * con la cámara apagada -- un glifo inerte esperando una cara que nadie
     * estaba mirando. Derivarla vuelve ese desajuste imposible de escribir.
     *
     * `retirado` cuenta como armada a propósito: cuando se agotan los dos
     * intentos el ViewModel suelta la cámara, pero la pantalla se queda donde
     * está, con el glifo listo para tocar y los otros canales escuchando.
     * Nadie es empujado a otro método por haber fallado.
     */
    val fase = if (rostro.activo || rostro.retirado) LoginFase.ARMADO else LoginFase.REPOSO

    /** Enciende la cámara frontal y pone los tres canales a escuchar juntos. */
    fun armar() {
        if (!rostroDisponible) return
        viewModel.limpiarError()
        rostroViewModel.iniciar()
    }

    // La cámara frontal es de la ruta de acceso. Al abrir el QR o el formulario
    // se suelta, para no obligar al dueño de la cámara a un relevo que cuesta
    // segundo y medio de HAL en la tableta.
    LaunchedEffect(ruta) {
        if (ruta != LoginRuta.ACCESO) rostroViewModel.detener()
    }

    // ── El turno del rostro ───────────────────────────────────────────────────

    LaunchedEffect(rostro.estado) {
        when (rostro.estado) {
            EstadoGlifo.VERIFICANDO -> arbitro.tomarTurno(LoginCanal.ROSTRO)
            EstadoGlifo.FALLO,
            EstadoGlifo.BUSCANDO,
            EstadoGlifo.REPOSO -> {
                if (arbitro.canalEnCurso == LoginCanal.ROSTRO) arbitro.liberar()
            }
            else -> Unit
        }
    }

    // ── Gafete NFC ────────────────────────────────────────────────────────────

    val latestRuta by rememberUpdatedState(ruta)

    // Mientras el gafete sigue encimado en la antena, el chip entra y sale del
    // campo varias veces por microvibraciones de la mano, y Android reporta
    // cada una como una llegada NUEVA. Un candado por sesión no basta: lo que
    // funciona es un enfriamiento por tiempo real, que no depende del estado
    // de Compose (siempre va un paso atrás del hilo del callback).
    val ultimoToqueNfcMs = remember { java.util.concurrent.atomic.AtomicLong(0L) }

    DisposableEffect(actividad, nfcDisponible, lifecycleOwner) {
        var escuchando = false

        val manejarEtiqueta: (android.nfc.Tag) -> Unit = { tag ->
            // Tambien mientras alguien teclea sus credenciales: el formulario
            // promete que el gafete sigue activo y tiene que ser cierto. La
            // unica ruta que lo apaga es la camara QR, donde la persona esta
            // apuntando a proposito y un tag rozado seria una sorpresa.
            if (latestRuta != LoginRuta.CAMARA_QR) {
                val ahora = android.os.SystemClock.elapsedRealtime()
                val previo = ultimoToqueNfcMs.getAndSet(ahora)
                if (ahora - previo > ENFRIAMIENTO_NFC_MS) {
                    // readText hace I/O sobre la etiqueta: fuera del hilo de UI.
                    val carga = NfcBadgeManager.readText(tag)
                    if (carga != null) {
                        // La decisión SÍ vuelve al hilo principal: el árbitro se
                        // toca desde un solo hilo, por diseño.
                        scope.launch(Dispatchers.Main.immediate) {
                            if (arbitro.tomarTurno(LoginCanal.NFC)) {
                                rostroViewModel.pausar()
                                viewModel.limpiarError()
                                viewModel.login(carga, isQR = true)
                            }
                        }
                    } else {
                        // No se pudo leer: no cuenta como intento, libera el
                        // enfriamiento para el siguiente toque.
                        ultimoToqueNfcMs.set(previo)
                    }
                }
            }
        }

        fun iniciar() {
            if (escuchando || actividad == null || !nfcDisponible) return
            escuchando = NfcBadgeManager.startListening(actividad, manejarEtiqueta)
        }

        fun detener() {
            if (!escuchando || actividad == null) return
            NfcBadgeManager.stopListening(actividad)
            escuchando = false
        }

        val observador = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_RESUME -> iniciar()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_DESTROY -> detener()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observador)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            iniciar()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observador)
            detener()
        }
    }

    // ── Lector físico ─────────────────────────────────────────────────────────

    if (ruta != LoginRuta.CAMARA_QR) {
        // Se filtra en MainActivity.dispatchKeyEvent contra el tecleo humano,
        // así que convive sin problema con el formulario manual montado.
        HardwareScannerListener { codigo ->
            if (arbitro.tomarTurno(LoginCanal.LECTOR)) {
                rostroViewModel.pausar()
                viewModel.limpiarError()
                viewModel.login(BarcodeValueNormalizer.normalizeScan(codigo), isQR = true)
            }
        }
    }

    // ── Desenlace ─────────────────────────────────────────────────────────────

    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Success -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                rostroViewModel.detener()
                onNavigateToHome()
            }

            is AuthState.Error -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                arbitro.liberar()
                scope.launch {
                    sacudida.animateTo(
                        targetValue = 0f,
                        animationSpec = keyframes {
                            durationMillis = 400
                            -15f at 50 using FastOutLinearInEasing
                            15f at 100 using LinearOutSlowInEasing
                            -10f at 150 using FastOutLinearInEasing
                            10f at 200 using LinearOutSlowInEasing
                            -5f at 250 using FastOutLinearInEasing
                            5f at 300 using LinearOutSlowInEasing
                            0f at 350
                        }
                    )
                }
                // Ese gafete no era. La cámara sigue caliente, así que reanudar
                // el análisis es inmediato -- por eso se pausó en vez de
                // apagarse. Y el aviso se retira solo: la siguiente persona
                // tiene que encontrar la pantalla limpia.
                rostroViewModel.reanudar()
                delay(DURACION_AVISO_ERROR_MS)
                viewModel.limpiarError()
            }

            else -> Unit
        }
    }

    LaunchedEffect(rostro.exito) {
        if (rostro.exito) {
            delay(PAUSA_ANTES_DE_ENTRAR_MS)
            onNavigateToHome()
        }
    }

    // Se apaga siempre al salir de la pantalla, pase lo que pase.
    DisposableEffect(Unit) { onDispose { rostroViewModel.detener() } }

    // ── Iluminación ───────────────────────────────────────────────────────────

    // No tenemos infrarrojo como Face ID, pero sí una pantalla de diez pulgadas
    // a veinte centímetros de la cara. En un pasillo de bodega ésa es la
    // diferencia entre ver la cara y no verla. Se restituye SIEMPRE: dejar la
    // tableta clavada al 100 % se comería la batería de todo el turno.
    val iluminando = rostro.activo && !rostro.enPausa
    val ventana = remember(contexto) { actividad?.window }
    DisposableEffect(iluminando, ventana) {
        val parametros = ventana?.attributes
        val brilloPrevio = parametros?.screenBrightness
        if (iluminando && ventana != null && parametros != null) {
            parametros.screenBrightness = 1f
            ventana.attributes = parametros
        }
        onDispose {
            if (ventana != null && brilloPrevio != null) {
                val restaurar = ventana.attributes
                restaurar.screenBrightness = brilloPrevio
                ventana.attributes = restaurar
            }
        }
    }

    // El fondo se aclara durante la captura. No es un efecto: es el iluminador.
    val lampara by animateFloatAsState(
        targetValue = when {
            !iluminando -> 0f
            rostro.estado == EstadoGlifo.VERIFICANDO -> 1f
            rostro.estado == EstadoGlifo.DETECTADO -> 0.45f
            rostro.estado == EstadoGlifo.EXITO -> 0.30f
            else -> 0f
        },
        animationSpec = tween(Motion.DurationEmphasized),
        label = "lampara"
    )

    // Hápticos sólo en las transiciones que significan algo. Si todo vibra,
    // nada avisa.
    LaunchedEffect(rostro.estado) {
        when (rostro.estado) {
            EstadoGlifo.DETECTADO -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            EstadoGlifo.EXITO -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            EstadoGlifo.FALLO -> {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                sacudida.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 440
                        0f at 0
                        -13f at 60
                        13f at 130
                        -8f at 210
                        6f at 300
                        0f at 440
                    }
                )
            }
            else -> Unit
        }
    }

    // ── Botón atrás ───────────────────────────────────────────────────────────

    // Un desvío regresa a la casa; la casa armada se desarma; la casa en reposo
    // no intercepta nada y deja salir de la aplicación, que es lo que la
    // persona espera del botón de atrás en la primera pantalla.
    BackHandler(enabled = ruta != LoginRuta.ACCESO || fase == LoginFase.ARMADO) {
        when {
            ruta == LoginRuta.CAMARA_QR -> ruta = LoginRuta.ACCESO
            ruta != LoginRuta.ACCESO -> ruta = LoginRuta.ACCESO
            else -> rostroViewModel.detener()
        }
    }

    // ── La cámara frontal ─────────────────────────────────────────────────────

    CamaraRostroSinPreview(
        activa = ruta == LoginRuta.ACCESO && rostro.activo,
        onCuadro = rostroViewModel::analizarCuadro
    )

    // ── Pintura ───────────────────────────────────────────────────────────────

    when (ruta) {
        LoginRuta.CAMARA_QR -> RutaCamaraQr(
            authState = authState,
            onCodigo = { codigo ->
                if (arbitro.tomarTurno(LoginCanal.QR_CAMARA)) {
                    viewModel.limpiarError()
                    viewModel.login(BarcodeValueNormalizer.normalizeScan(codigo), isQR = true)
                }
            },
            // Salir es inmediato: el dueño de la cámara ya serializa el
            // relevo con la frontal y espera el cierre real del dispositivo.
            onCerrar = { ruta = LoginRuta.ACCESO }
        )

        LoginRuta.ACCESO,
        LoginRuta.MANUAL -> RostroFondo(
            resplandor = lampara,
            modifier = Modifier.fillMaxSize()
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                val alturaHolgada = maxHeight >= 620.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (alturaHolgada) Modifier
                            else Modifier.verticalScroll(rememberScrollState())
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .graphicsLayer { translationX = sacudida.value },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = if (alturaHolgada) Arrangement.Center else Arrangement.Top
                ) {
                    when (ruta) {
                        LoginRuta.MANUAL -> RutaManual(
                            authState = authState,
                            onLogin = { usuario, clave ->
                                if (arbitro.tomarTurno(LoginCanal.MANUAL)) {
                                    viewModel.limpiarError()
                                    viewModel.login(usuario, clave)
                                }
                            },
                            onVolver = { ruta = LoginRuta.ACCESO }
                        )

                        else -> PantallaAcceso(
                            fase = fase,
                            rostro = rostro,
                            arbitro = arbitro,
                            validando = validando,
                            rostroDisponible = rostroDisponible,
                            nfcDisponible = nfcDisponible,
                            onIngresar = ::armar,
                            onReintentarRostro = { rostroViewModel.iniciar() },
                            onAbrirQr = { ruta = LoginRuta.CAMARA_QR },
                            onAbrirManual = { ruta = LoginRuta.MANUAL }
                        )
                    }

                    val estado = authState
                    if (estado is AuthState.Error) {
                        Spacer(Modifier.height(14.dp))
                        AvisoDeError(
                            mensaje = estado.message,
                            modifier = Modifier.widthIn(max = 460.dp)
                        )
                    }

                    if (alturaHolgada) Spacer(Modifier.height(28.dp))
                    else Spacer(Modifier.height(20.dp))

                    Text(
                        text = "Sistema interno de Grupo PMM",
                        color = PmmTextMuted,
                        fontSize = 10.sp
                    )
                }

                // El punto verde de Android va a salir de todos modos; que la
                // pantalla lo asuma es más honesto que fingir que no hay cámara.
                if (rostro.activo) {
                    IndicadorCamaraViva(modifier = Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

// ─── RUTA: CÁMARA QR ──────────────────────────────────────────────────────────

@Composable
private fun RutaCamaraQr(
    authState: AuthState,
    onCodigo: (String) -> Unit,
    onCerrar: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        SmartBarcodeCameraView(
            onBarcodeScanned = onCodigo,
            title = "Acceso por cámara",
            subtitle = "Escanea tu gafete QR",
            barcodeFormats = Barcode.FORMAT_QR_CODE,
            continuous = false,
            isPaused = authState is AuthState.Loading,
            pausedMessage = "Validando gafete...",
            showClose = false,
            compact = false,
            startZoomRatio = 1.6f,
            // El gafete se sostiene en la mano: prima la fluidez sobre el
            // alcance, así que basta con 720p de análisis.
            scanDistance = ScanDistance.Near,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.22f to Color.Transparent,
                        0.72f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.62f)
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                color = Color.White.copy(alpha = 0.94f),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextButton(
                        onClick = onCerrar,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = PmmBlueDeep,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Volver al acceso", color = PmmBlueDeep, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Escanea tu gafete",
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = "Centra el código QR dentro del visor. El escáner ajusta el zoom e ilumina si hace falta.",
                        color = PmmTextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    if (authState is AuthState.Loading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEFF6FF))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = PmmBlueDeep,
                                strokeWidth = 2.dp
                            )
                            Text("VALIDANDO ACCESO...", color = PmmBlueDeep, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val estado = authState
                    if (estado is AuthState.Error) {
                        AvisoDeError(estado.message)
                    }
                }
            }
        }
    }
}

// ─── RUTA: MANUAL ─────────────────────────────────────────────────────────────

@Composable
private fun RutaManual(
    authState: AuthState,
    onLogin: (String, String) -> Unit,
    onVolver: () -> Unit
) {
    var usuario by rememberSaveable { mutableStateOf("") }
    // El usuario sobrevive a la rotacion; la contrasena NO. `rememberSaveable`
    // la escribiria al bundle de estado guardado, que el sistema puede volcar a
    // disco. Volver a teclearla tras rotar cuesta dos segundos, y ese es el
    // precio correcto.
    var clave by remember { mutableStateOf("") }
    var claveVisible by remember { mutableStateOf(false) }

    val focoClave = remember { FocusRequester() }
    val gestorDeFoco = LocalFocusManager.current
    val cargando = authState is AuthState.Loading

    Column(
        modifier = Modifier.widthIn(max = 460.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(onClick = onVolver, contentPadding = PaddingValues(0.dp)) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = null,
                tint = PmmBlueDeep,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("Volver al acceso", color = PmmBlueDeep, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Entrar con tus credenciales",
                    color = PmmTextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )

                CampoDeAcceso(
                    valor = usuario,
                    onCambio = { usuario = it },
                    etiqueta = "Usuario",
                    icono = Icons.Default.Person,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(onNext = { focoClave.requestFocus() })
                )

                CampoDeAcceso(
                    valor = clave,
                    onCambio = { clave = it },
                    etiqueta = "Contraseña",
                    icono = Icons.Default.Lock,
                    visualTransformation = if (claveVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    iconoFinal = {
                        val icono = if (claveVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { claveVisible = !claveVisible }) {
                            Icon(icono, contentDescription = null, tint = PmmTextSecondary, modifier = Modifier.size(18.dp))
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            gestorDeFoco.clearFocus()
                            if (usuario.isNotBlank()) onLogin(usuario.trim(), clave.trim())
                        }
                    ),
                    modifier = Modifier.focusRequester(focoClave)
                )

                Button(
                    onClick = {
                        gestorDeFoco.clearFocus()
                        onLogin(usuario.trim(), clave.trim())
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = !cargando && usuario.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmYellow,
                        contentColor = PmmTextPrimary,
                        disabledContainerColor = PmmNavyBorder,
                        disabledContentColor = PmmTextMuted
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 0.dp)
                ) {
                    if (cargando) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PmmBlueDeep,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("INGRESAR", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
                    }
                }

                // Se dice a propósito: el formulario no apaga los otros canales,
                // y quien se cansó de teclear puede acercar el gafete sin salir
                // de aquí.
                Text(
                    text = "El gafete y el lector siguen activos.",
                    color = PmmTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CampoDeAcceso(
    valor: String,
    onCambio: (String) -> Unit,
    etiqueta: String,
    icono: ImageVector,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    iconoFinal: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var enfocado by remember { mutableStateOf(false) }
    val brillo by animateFloatAsState(
        targetValue = if (enfocado) 0.12f else 0f,
        animationSpec = tween(200),
        label = "brilloCampo"
    )
    val colorBrillo = PmmBlueDeep.copy(alpha = brillo)

    OutlinedTextField(
        value = valor,
        onValueChange = onCambio,
        label = { Text(etiqueta, color = PmmTextSecondary, fontSize = 12.sp) },
        leadingIcon = { Icon(icono, null, tint = PmmTextMuted, modifier = Modifier.size(18.dp)) },
        trailingIcon = iconoFinal,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PmmBlueDeep,
            unfocusedBorderColor = PmmNavyBorder,
            focusedTextColor = PmmTextPrimary,
            unfocusedTextColor = PmmTextPrimary,
            cursorColor = PmmBlueDeep,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color(0xFFF8FAFC),
            focusedLabelColor = PmmBlueDeep
        ),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { enfocado = it.isFocused }
            .shadow(
                elevation = if (enfocado) 4.dp else 0.dp,
                shape = RoundedCornerShape(14.dp),
                clip = false,
                ambientColor = colorBrillo,
                spotColor = colorBrillo
            )
    )
}

// ─── PIEZAS COMUNES ───────────────────────────────────────────────────────────

@Composable
private fun AvisoDeError(mensaje: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFEE2E2))
            .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = "Error",
            tint = PmmRedDark,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = mensaje,
            color = PmmRedDark,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun IndicadorCamaraViva(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(top = 12.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(50))
                .background(com.example.pmmovil.ui.rostro.RostroTema.Ok.copy(alpha = 0.75f))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "ACTIVA · SIN FOTOS",
            color = PmmTextMuted,
            fontSize = 9.5.sp,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Cuánto dura el aviso de un intento fallido antes de retirarse solo.
 *
 * No es decoración: los tres canales siguen armados detrás, y la siguiente
 * persona llega en segundos. Un banner pegado sería un error ajeno colgando
 * sobre su intento.
 */
private const val DURACION_AVISO_ERROR_MS = 4_000L

/** Lo que tarda el gafete en volver a contar tras un toque, para no leerlo dos veces. */
private const val ENFRIAMIENTO_NFC_MS = 2_500L

/** El "Hola, Eduardo" tiene que alcanzar a leerse antes de que la pantalla cambie. */
private const val PAUSA_ANTES_DE_ENTRAR_MS = 750L
