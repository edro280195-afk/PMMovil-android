package com.example.pmmovil.ui.rostro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HideImage
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pmmovil.ui.theme.Motion

@Composable
fun EnrolamientoRostroScreen(
    onBack: () -> Unit,
    viewModel: EnrolamientoRostroViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.fase) {
        if (state.fase == FaseEnrolamiento.LISTO) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    LaunchedEffect(state.hapticTick) {
        if (state.hapticTick > 0) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    EnrolamientoFondo {
        when {
            state.cargando -> Centrado { Cargando("Preparando reconocimiento facial…") }
            !state.motorListo -> Centrado {
                NoDisponible(
                    detalle = state.error ?: "Este equipo no puede usar reconocimiento facial.",
                    onAction = onBack
                )
            }
            state.fase == FaseEnrolamiento.AVISO -> Aviso(
                state = state,
                // Ya enrolado, el botón principal es "Empezar de cero": borra
                // lo anterior. Agregar una apariencia va por la lista.
                onAceptar = {
                    if (state.yaEnrolado) {
                        viewModel.nuevaApariencia(reemplazar = true)
                    } else {
                        viewModel.aceptarAviso()
                    }
                },
                onRevocar = viewModel::revocar,
                onAgregarApariencia = { viewModel.nuevaApariencia(reemplazar = false) },
                onBorrarApariencia = viewModel::revocarApariencia,
                onBack = onBack
            )
            state.fase == FaseEnrolamiento.FIRMA -> FirmaAviso(
                nombre = null,
                onFirmar = viewModel::firmar,
                onVolver = viewModel::reiniciar
            )
            state.fase == FaseEnrolamiento.TUTORIAL -> TutorialEnrolamiento(
                etiqueta = state.etiqueta,
                // Sólo se pide nombre cuando ya hay algo que distinguir: para
                // la primera, "Principal" lo pone el servidor y preguntar sería
                // un trámite sin motivo.
                esAparienciaExtra = state.apariencias.isNotEmpty() && !state.reemplazar,
                onEtiquetaCambio = viewModel::cambiarEtiqueta,
                onEmpezar = viewModel::comenzarCaptura,
                onVolver = viewModel::regresarAFirma
            )
            state.fase == FaseEnrolamiento.LISTO -> Listo(
                state = state,
                onAction = onBack
            )
            else -> Captura(
                state = state,
                onFrame = viewModel::analizarCuadro,
                onCameraError = viewModel::reportarErrorCamara,
                onDarPorBueno = viewModel::darPorBueno,
                onBack = onBack
            )
        }
    }
}

// ── Captura ──────────────────────────────────────────────────────────────────

/**
 * El aro manda. Arriba, tres etapas que dicen dónde vas; en medio, el aro; y
 * abajo una sola línea de ayuda. No hay contador: el aro ya es el contador, y
 * dos números para una misma cosa siempre acaban contradiciéndose.
 */
@Composable
private fun Captura(
    state: EnrolamientoUiState,
    onFrame: (RostroFrame) -> Unit,
    onCameraError: (String) -> Unit,
    onDarPorBueno: () -> Unit,
    onBack: () -> Unit
) {
    val sellando = state.fase == FaseEnrolamiento.SELLANDO ||
        state.fase == FaseEnrolamiento.GUARDANDO
    val sello = remember { Animatable(0f) }

    // El barrido sólo avanza mientras haya cara delante. Si se deja correr
    // solo, el aro termina de sellarse mientras el sistema todavía espera la
    // confirmación -- justo la contradicción que este rediseño vino a quitar.
    val avanzaSello = rememberUpdatedState(
        (state.hayRostro && state.pista.isBlank()) || state.fase == FaseEnrolamiento.GUARDANDO
    )
    LaunchedEffect(sellando) {
        sello.snapTo(0f)
        if (!sellando) return@LaunchedEffect
        var anterior = withFrameMillis { it }
        while (sello.value < 1f) {
            val ahora = withFrameMillis { it }
            val delta = (ahora - anterior).coerceIn(0L, 64L)
            anterior = ahora
            if (avanzaSello.value) {
                val paso = delta.toFloat() / EnrolamientoRostroViewModel.MS_SELLO
                sello.snapTo((sello.value + paso).coerceAtMost(1f))
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BotonCircular(onClick = onBack)
            Text(
                "REGISTRO DE ROSTRO",
                color = EnrolamientoTema.TextoMudo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.3.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(38.dp))
        }

        Etapas(state)

        Spacer(Modifier.height(20.dp))

        AnimatedContent(
            targetState = titulo(state),
            transitionSpec = {
                (fadeIn(tween(Motion.DurationMedium)) +
                    slideInVertically(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) { it / 3 })
                    .togetherWith(fadeOut(tween(Motion.DurationQuick)))
            },
            label = "titulo-captura"
        ) { texto ->
            Text(
                texto,
                color = EnrolamientoTema.Texto,
                fontSize = 27.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )
        }

        Spacer(Modifier.height(9.dp))

        Text(
            subtitulo(state),
            color = EnrolamientoTema.TextoTenue,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp)
                .padding(horizontal = 32.dp)
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // En tableta el aro no debe crecer hasta comerse la pantalla: a
            // partir de cierto tamaño deja de leerse mejor y sólo aleja el
            // texto de la cara.
            val lado = minOf(maxWidth * 0.92f, maxHeight, 430.dp)
            Box(Modifier.size(lado), contentAlignment = Alignment.Center) {
                // Grande a proposito: un circulo pequeno invita a acercarse, y
                // acercarse era justo lo que el filtro castigaba con "alejate".
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.76f)
                        .clip(CircleShape)
                        .background(EnrolamientoTema.Preview)
                ) {
                    CamaraRostroConPreview(
                        onCuadro = onFrame,
                        onError = onCameraError,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                AroEnrolamiento(
                    cobertura = state.cobertura,
                    celdaActual = if (state.fase == FaseEnrolamiento.VUELTA) state.celdaActual else null,
                    progresoSello = sello.value,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp)
                .padding(top = 16.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                ayuda(state),
                color = if (state.pista.isNotBlank()) EnrolamientoTema.Aviso else EnrolamientoTema.TextoMudo,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            // La salida de emergencia sólo aparece cuando de verdad hace falta,
            // y dice exactamente qué va a pasar con lo que falta.
            AnimatedVisibility(visible = state.puedeDarPorBueno) {
                OutlinedButton(
                    onClick = onDarPorBueno,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EnrolamientoTema.Linea),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = EnrolamientoTema.Superficie,
                        contentColor = EnrolamientoTema.Glifo
                    )
                ) {
                    Text(
                        "Dar por bueno lo que llevo " +
                            "(${(state.progresoAro * 100).toInt()}% del aro)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                }
            }

            state.error?.let {
                Nota(it, EnrolamientoTema.Malo)
            }
        }
    }
}

/** Tres etapas, tres barras. Es el único sitio donde se cuenta algo. */
@Composable
private fun Etapas(state: EnrolamientoUiState) {
    val etapas = listOf("Centrar", "Dibujar el aro", "Sellar")
    val actual = when (state.fase) {
        FaseEnrolamiento.FRONTAL -> 0
        FaseEnrolamiento.VUELTA -> 1
        else -> 2
    }
    val avance = when (state.fase) {
        FaseEnrolamiento.FRONTAL -> state.progresoFrontal
        FaseEnrolamiento.VUELTA ->
            ((state.vuelta - 1 + state.progresoAro) / VUELTAS_PEDIDAS).coerceIn(0f, 1f)
        else -> 1f
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            etapas.forEachIndexed { indice, _ ->
                val objetivo = when {
                    indice < actual -> 1f
                    indice == actual -> avance
                    else -> 0f
                }
                val ancho by animateFloatAsState(
                    targetValue = objetivo,
                    animationSpec = tween(Motion.DurationEmphasized, easing = Motion.EmphasizedDecelerate),
                    label = "etapa-$indice"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(EnrolamientoTema.Riel)
                ) {
                    Box(
                        Modifier
                            // fillMaxWidth(0f) revienta: la fraccion tiene que ser > 0.
                            .fillMaxWidth(ancho.coerceAtLeast(0.0001f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(99.dp))
                            .background(if (indice < actual) EnrolamientoTema.Ok else EnrolamientoTema.Glifo)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            etapas.forEachIndexed { indice, nombre ->
                Text(
                    nombre.uppercase(),
                    color = when {
                        indice < actual -> EnrolamientoTema.Ok
                        indice == actual -> EnrolamientoTema.Glifo
                        else -> EnrolamientoTema.TextoMudo
                    },
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun titulo(state: EnrolamientoUiState): String = when (state.fase) {
    FaseEnrolamiento.FRONTAL -> "Acerca tu cara al centro"
    FaseEnrolamiento.VUELTA ->
        if (state.vuelta == 1) "Dibuja un círculo con la nariz" else "Una vuelta más, ya la sabes"
    FaseEnrolamiento.GUARDANDO -> "Guardando tu rostro"
    else -> "Sellando tu registro"
}

private fun subtitulo(state: EnrolamientoUiState): String = when (state.fase) {
    FaseEnrolamiento.FRONTAL -> "Ponte de frente y quédate quieto un momento."
    FaseEnrolamiento.VUELTA ->
        if (state.progresoAro >= 0.86f) "Ya casi: cierra el pedazo que falta."
        else "La luz azul sigue tu cara: llévala hasta cerrar el aro."
    FaseEnrolamiento.GUARDANDO -> "Un segundo."
    else -> "Mira la pantalla un segundo más."
}

private fun ayuda(state: EnrolamientoUiState): String = when {
    state.pista.isNotBlank() -> state.pista
    state.fase == FaseEnrolamiento.FRONTAL -> "La captura es automática, no toques nada."
    state.fase == FaseEnrolamiento.VUELTA -> "El trazo azul es lo que ya quedó guardado."
    else -> "Comprobando que eres tú en todo el recorrido."
}

// ── Aviso ────────────────────────────────────────────────────────────────────

/**
 * Lo que de verdad frena a la gente no es el trámite, es la duda de si le están
 * guardando la cara. Por eso los tres hechos van arriba, con el aviso legal
 * completo debajo y a un toque -- palabra por palabra el mismo texto que se
 * sella con el alta.
 */
@Composable
private fun Aviso(
    state: EnrolamientoUiState,
    onAceptar: () -> Unit,
    onRevocar: () -> Unit,
    onAgregarApariencia: () -> Unit,
    onBorrarApariencia: (Long) -> Unit,
    onBack: () -> Unit
) {
    var legalAbierto by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 6.dp)
            ) {
                BotonCircular(onClick = onBack)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(112.dp).padding(top = 6.dp)) {
                    RostroGlifo(
                        estado = EstadoGlifo.BUSCANDO,
                        colorBase = EnrolamientoTema.Glifo,
                        colorHalo = EnrolamientoTema.Glifo,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Entra con tu rostro",
                    color = EnrolamientoTema.Texto,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(9.dp))
                Text(
                    "Una mirada y estás dentro. El gafete y el QR siguen funcionando igual que hoy.",
                    color = EnrolamientoTema.TextoTenue,
                    fontSize = 14.5.sp,
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 330.dp)
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    color = EnrolamientoTema.Superficie,
                    border = BorderStroke(1.dp, EnrolamientoTema.Linea)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            Icons.Default.Schedule, null,
                            tint = EnrolamientoTema.TextoTenue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            "Toma unos 30 segundos",
                            color = EnrolamientoTema.TextoTenue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Quien ya tiene rostro entra aquí a administrar, no a leer otra vez
            // el aviso: la lista de apariencias va primero.
            if (state.yaEnrolado && state.apariencias.isNotEmpty()) {
                ListaApariencias(
                    apariencias = state.apariencias,
                    maxApariencias = state.maxApariencias,
                    onAgregar = onAgregarApariencia,
                    onBorrar = onBorrarApariencia
                )
                Spacer(Modifier.height(14.dp))
            } else if (state.yaEnrolado) {
                TarjetaEstado(state.plantillasPrevias)
                Spacer(Modifier.height(12.dp))
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = EnrolamientoTema.SuperficieAlta,
                border = BorderStroke(1.dp, EnrolamientoTema.Linea)
            ) {
                Column(Modifier.padding(6.dp)) {
                    Hecho(
                        icono = Icons.Default.HideImage,
                        fondo = EnrolamientoTema.AzulTenue,
                        tinte = EnrolamientoTema.Glifo,
                        titulo = "No se guarda ninguna fotografía",
                        detalle = "Sólo un modelo matemático de 512 números. Con él no se " +
                            "puede reconstruir tu cara."
                    )
                    Hecho(
                        icono = Icons.Default.Storage,
                        fondo = EnrolamientoTema.VerdeTenue,
                        tinte = EnrolamientoTema.OkOscuro,
                        titulo = "Se compara en el servidor de PMM",
                        detalle = "Nunca sale de la empresa y se busca sólo entre la gente " +
                            "de tu planta."
                    )
                    Hecho(
                        icono = Icons.Default.Delete,
                        fondo = EnrolamientoTema.AmbarTenue,
                        tinte = EnrolamientoTema.AvisoOscuro,
                        titulo = "Lo borras cuando quieras",
                        detalle = "Desde esta misma pantalla, y sigues entrando con gafete " +
                            "o contraseña."
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = EnrolamientoTema.SuperficieAlta,
                border = BorderStroke(1.dp, EnrolamientoTema.Linea),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { legalAbierto = !legalAbierto }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Ver el aviso de privacidad completo",
                            color = EnrolamientoTema.Glifo,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        val giro by animateFloatAsState(
                            targetValue = if (legalAbierto) 180f else 0f,
                            animationSpec = tween(Motion.DurationMedium, easing = Motion.Standard),
                            label = "giro-acordeon"
                        )
                        Icon(
                            Icons.Default.ExpandMore, null,
                            tint = EnrolamientoTema.Glifo,
                            modifier = Modifier.size(18.dp).rotate(giro)
                        )
                    }
                    AnimatedVisibility(visible = legalAbierto) {
                        Text(
                            EnrolamientoRostroViewModel.TEXTO_AVISO,
                            color = EnrolamientoTema.TextoTenue,
                            fontSize = 12.5.sp,
                            lineHeight = 19.5.sp,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            }

            if (!state.disponible) {
                Spacer(Modifier.height(12.dp))
                Nota(
                    "Aún no está encendido en el servidor: puedes registrarlo hoy y quedará " +
                        "listo para cuando se active.",
                    EnrolamientoTema.Aviso
                )
            }
            state.error?.let { Spacer(Modifier.height(12.dp)); Nota(it, EnrolamientoTema.Malo) }
            state.aviso?.let { Spacer(Modifier.height(12.dp)); Nota(it, EnrolamientoTema.Ok) }

            if (state.yaEnrolado) {
                TextButton(
                    onClick = onRevocar,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = EnrolamientoTema.Malo,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminar mi rostro", color = EnrolamientoTema.Malo, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(10.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                // El pie se queda fijo, así que necesita fondo propio: sin él
                // el contenido que sigue scrolleando se ve cortado detrás.
                .background(
                    Brush.verticalGradient(
                        0f to EnrolamientoTema.Fondo.copy(alpha = 0f),
                        0.28f to EnrolamientoTema.Fondo
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onAceptar,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EnrolamientoTema.Glifo,
                    contentColor = Color.White
                )
            ) {
                Text(
                    // Con apariencias, "volver a registrar" ya no es lo normal
                    // -- lo normal es agregar, y eso se hace desde la lista de
                    // arriba. Este botón es el camino destructivo, y su texto
                    // tiene que decirlo.
                    if (state.yaEnrolado) "Empezar de cero" else "Acepto y continuar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp
                )
            }
            TextButton(onClick = onBack) {
                Text("Ahora no", color = EnrolamientoTema.TextoTenue, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Hecho(
    icono: ImageVector,
    fondo: Color,
    tinte: Color,
    titulo: String,
    detalle: String
) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(fondo),
            contentAlignment = Alignment.Center
        ) {
            Icon(icono, null, tint = tinte, modifier = Modifier.size(17.dp))
        }
        Column {
            Text(titulo, color = EnrolamientoTema.Texto, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(detalle, color = EnrolamientoTema.TextoTenue, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun TarjetaEstado(plantillas: Int) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = EnrolamientoTema.VerdeTenue,
        border = BorderStroke(1.dp, EnrolamientoTema.OkBorde),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                    .background(EnrolamientoTema.VerdeMedio),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, null, tint = EnrolamientoTema.OkOscuro, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(
                    "Tu rostro ya está registrado",
                    color = EnrolamientoTema.OkOscuro,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "$plantillas ${if (plantillas == 1) "toma" else "tomas"} guardadas",
                    color = EnrolamientoTema.OkTexto,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ── Listo ────────────────────────────────────────────────────────────────────

/**
 * El aro no desaparece para que salga una palomita: se convierte en ella. Es lo
 * que cierra el ciclo — el mismo objeto con el que trabajaste todo el rato.
 */
@Composable
private fun Listo(state: EnrolamientoUiState, onAction: () -> Unit) {
    val cierre = remember { Animatable(0f) }
    LaunchedEffect(Unit) { cierre.animateTo(1f, tween(1000, easing = Motion.EmphasizedDecelerate)) }

    Centrado {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val e = cierre.value
                    val radio = size.minDimension * (0.40f - 0.03f * e)
                    val centro = Offset(size.width / 2f, size.height / 2f)
                    val grosor = size.minDimension * 0.068f

                    drawArc(
                        color = EnrolamientoTema.Ok,
                        startAngle = -90f,
                        sweepAngle = 360f * (e * 1.15f).coerceAtMost(1f),
                        useCenter = false,
                        topLeft = Offset(centro.x - radio, centro.y - radio),
                        size = Size(radio * 2f, radio * 2f),
                        style = Stroke(width = grosor, cap = StrokeCap.Round)
                    )

                    if (e > 0.5f) {
                        val k = ((e - 0.5f) / 0.5f * 1.6f).coerceAtMost(1f)
                        val p0 = Offset(centro.x - radio * 0.46f, centro.y + radio * 0.03f)
                        val p1 = Offset(centro.x - radio * 0.13f, centro.y + radio * 0.38f)
                        val p2 = Offset(centro.x + radio * 0.50f, centro.y - radio * 0.35f)
                        val trazo = Stroke(width = grosor, cap = StrokeCap.Round)
                        if (k <= 0.5f) {
                            val t = k / 0.5f
                            drawLine(
                                EnrolamientoTema.Ok, p0,
                                Offset(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t),
                                strokeWidth = trazo.width, cap = StrokeCap.Round
                            )
                        } else {
                            val t = (k - 0.5f) / 0.5f
                            drawLine(EnrolamientoTema.Ok, p0, p1, strokeWidth = trazo.width, cap = StrokeCap.Round)
                            drawLine(
                                EnrolamientoTema.Ok, p1,
                                Offset(p1.x + (p2.x - p1.x) * t, p1.y + (p2.y - p1.y) * t),
                                strokeWidth = trazo.width, cap = StrokeCap.Round
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "Listo, ya entras con tu cara",
                color = EnrolamientoTema.Texto,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "La próxima vez basta con mirar la pantalla. El gafete y el QR siguen ahí " +
                    "por si acaso.",
                color = EnrolamientoTema.TextoTenue,
                fontSize = 14.5.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Resumen("$VUELTAS_PEDIDAS", if (VUELTAS_PEDIDAS == 1) "Vuelta" else "Vueltas")
                Resumen("${state.plantillasPrevias}", "Tomas")
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EnrolamientoTema.Glifo,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 36.dp, vertical = 16.dp)
            ) {
                Text("Terminar", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun Resumen(valor: String, etiqueta: String) {
    Surface(
        shape = RoundedCornerShape(15.dp),
        color = EnrolamientoTema.SuperficieAlta,
        border = BorderStroke(1.dp, EnrolamientoTema.Linea)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(valor, color = EnrolamientoTema.Texto, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(3.dp))
            Text(
                etiqueta.uppercase(),
                color = EnrolamientoTema.TextoMudo,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ── Piezas sueltas ───────────────────────────────────────────────────────────

@Composable
private fun BotonCircular(onClick: () -> Unit) {
    Surface(
        shape = CircleShape,
        color = EnrolamientoTema.SuperficieAlta,
        border = BorderStroke(1.dp, EnrolamientoTema.Linea),
        modifier = Modifier.size(38.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Default.ArrowBack, "Volver",
                tint = EnrolamientoTema.Glifo,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun Nota(texto: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = color.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(texto, modifier = Modifier.padding(14.dp), color = color, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun Centrado(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(30.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Cargando(mensaje: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = EnrolamientoTema.Glifo, strokeWidth = 2.5.dp)
        Spacer(Modifier.height(16.dp))
        Text(mensaje, color = EnrolamientoTema.TextoTenue, fontSize = 14.sp)
    }
}

@Composable
private fun NoDisponible(detalle: String, onAction: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "No disponible",
            color = EnrolamientoTema.Texto,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            detalle,
            color = EnrolamientoTema.TextoTenue,
            fontSize = 14.5.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EnrolamientoTema.Glifo,
                contentColor = Color.White
            ),
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = 15.dp)
        ) {
            Text("Volver", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
