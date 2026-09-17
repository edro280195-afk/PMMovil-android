package com.example.pmmovil.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.ui.rostro.EstadoGlifo
import com.example.pmmovil.ui.rostro.RostroGlifo
import com.example.pmmovil.ui.rostro.RostroLoginUiState
import com.example.pmmovil.ui.rostro.RostroTema
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import kotlinx.coroutines.delay

/**
 * La superficie de acceso. Dos estados y ningún paso.
 *
 * EN REPOSO la cámara está apagada: sólo el logo, el botón de entrar y los dos
 * desvíos. El gafete y el lector ya están escuchando, y la pantalla lo dice --
 * quien llega con gafete no tiene que tocar nada.
 *
 * ARMADA la cámara frontal se enciende y los tres canales esperan juntos. El
 * rostro manda visualmente porque es el único que necesita que mires la
 * pantalla; gafete y lector viven abajo como dos pastillas que respiran. En
 * cuanto uno dispara, ése se enciende y los otros se apagan: nunca hay dos
 * cosas pidiendo atención al mismo tiempo.
 *
 * Lo que NO hay aquí, a propósito: pasos, flechas de "volver" a un método
 * anterior, ni ningún indicador de progreso entre canales. No es un trámite en
 * etapas, son cinco puertas a la misma habitación.
 */
@Composable
fun PantallaAcceso(
    fase: LoginFase,
    rostro: RostroLoginUiState,
    arbitro: LoginArbitro,
    validando: Boolean,
    rostroDisponible: Boolean,
    nfcDisponible: Boolean,
    onIngresar: () -> Unit,
    onReintentarRostro: () -> Unit,
    onAbrirQr: () -> Unit,
    onAbrirManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Coreografía de entrada: la marca primero, el resto detrás. Cuesta tres
    // líneas y es la diferencia entre "apareció una pantalla" y "esto se abrió".
    var entrada by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        delay(60)
        entrada = 1f
    }
    val aparicion by animateFloatAsState(
        targetValue = entrada,
        animationSpec = spring(
            dampingRatio = Motion.SpringDampingNoBounce,
            stiffness = Motion.SpringStiffnessLow
        ),
        label = "aparicionAcceso"
    )

    Column(
        modifier = modifier.widthIn(max = 460.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // El bloque central cambia de reposo a armado sin mover nada de lo que
        // está alrededor: los dos desvíos de abajo se quedan exactamente donde
        // estaban, que es lo que hace que esto no se sienta una navegación.
        AnimatedContent(
            targetState = fase,
            transitionSpec = {
                (fadeIn(tween(Motion.DurationMedium)) +
                    scaleIn(tween(Motion.DurationEmphasized, easing = Motion.EmphasizedDecelerate), 0.92f))
                    .togetherWith(
                        fadeOut(tween(Motion.DurationQuick)) +
                            scaleOut(tween(Motion.DurationMedium), 1.04f)
                    )
            },
            label = "faseAcceso"
        ) { faseActual ->
            when (faseActual) {
                LoginFase.REPOSO -> BloqueReposo(
                    aparicion = aparicion,
                    rostroDisponible = rostroDisponible,
                    validando = validando,
                    canalEnCurso = arbitro.canalEnCurso,
                    onIngresar = onIngresar
                )

                LoginFase.ARMADO -> BloqueArmado(
                    rostro = rostro,
                    arbitro = arbitro,
                    validando = validando,
                    nfcDisponible = nfcDisponible,
                    onReintentarRostro = onReintentarRostro
                )
            }
        }

        DesviosDeAcceso(
            habilitados = !validando,
            onAbrirQr = onAbrirQr,
            onAbrirManual = onAbrirManual,
            modifier = Modifier.graphicsLayer { alpha = aparicion }
        )
    }
}

/** Qué está haciendo la pantalla. Dos valores: no hay estados intermedios. */
enum class LoginFase {
    /** Cámara apagada. Gafete y lector escuchando. */
    REPOSO,

    /** Cámara frontal encendida. Los tres canales esperando juntos. */
    ARMADO
}

// ─── REPOSO ───────────────────────────────────────────────────────────────────

@Composable
private fun BloqueReposo(
    aparicion: Float,
    rostroDisponible: Boolean,
    validando: Boolean,
    canalEnCurso: LoginCanal?,
    onIngresar: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        MarcaPMMovil(aparicion = aparicion)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.graphicsLayer {
                alpha = aparicion
                translationY = (1f - aparicion) * 18.dp.toPx()
            }
        ) {
            Text(
                text = "PMMovil",
                color = PmmTextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.8).sp
            )
            Text(
                text = "Grupo PMM · Bodega",
                color = PmmTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Este renglón es el que convierte la pantalla en algo pasivo: quien
        // trae gafete no tiene que tocar el botón, y si no lo decimos nunca
        // lo va a descubrir.
        AvisoCanalesArmados(
            validando = validando,
            canalEnCurso = canalEnCurso,
            modifier = Modifier.graphicsLayer { alpha = aparicion }
        )

        Button(
            onClick = onIngresar,
            enabled = rostroDisponible && !validando,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .graphicsLayer {
                    alpha = aparicion
                    translationY = (1f - aparicion) * 26.dp.toPx()
                },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = PmmYellow,
                contentColor = PmmTextPrimary,
                disabledContainerColor = PmmNavyBorder,
                disabledContentColor = PmmTextMuted
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)
        ) {
            Text(
                text = if (rostroDisponible) "INGRESAR" else "ACCESO POR ROSTRO NO DISPONIBLE",
                fontWeight = FontWeight.ExtraBold,
                fontSize = if (rostroDisponible) 15.sp else 11.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * La marca, con la apertura. El halo crece una vez al entrar y se queda: es un
 * gesto de bienvenida, no una animación permanente que consuma cuadros toda la
 * sesión.
 */
@Composable
private fun MarcaPMMovil(aparicion: Float) {
    Box(
        modifier = Modifier.size(150.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val radio = size.minDimension / 2f
            listOf(1f to 0.10f, 0.82f to 0.16f).forEach { (escala, alfa) ->
                drawCircle(
                    color = PmmBlueDeep.copy(alpha = alfa * aparicion),
                    radius = radio * escala * (0.75f + aparicion * 0.25f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }
        }

        Surface(
            modifier = Modifier
                .size(104.dp)
                .graphicsLayer {
                    val escala = 0.86f + aparicion * 0.14f
                    scaleX = escala
                    scaleY = escala
                    alpha = aparicion
                },
            shape = RoundedCornerShape(26.dp),
            color = PmmBgSurface,
            border = BorderStroke(1.dp, PmmNavyBorder),
            shadowElevation = 6.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(R.drawable.pmmovil_symbol_vector),
                    contentDescription = "PMMovil",
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 18.dp)
                        .fillMaxWidth()
                        .aspectRatio(108f / 70f)
                )
            }
        }
    }
}

@Composable
private fun AvisoCanalesArmados(
    validando: Boolean,
    canalEnCurso: LoginCanal?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = if (validando) PmmBlueDeep.copy(alpha = 0.08f) else RostroTema.SuperficieAlta,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (validando) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = PmmBlueDeep,
                    strokeWidth = 2.dp
                )
                Text(
                    text = "Validando tu ${canalEnCurso?.nombreCorto ?: "acceso"}…",
                    color = PmmBlueDeep,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else {
                PuntoVivo()
                Text(
                    text = "Gafete y lector activos",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/** Un punto que late despacio. Lo único que se mueve en toda la fase de reposo. */
@Composable
private fun PuntoVivo() {
    val transicion = rememberInfiniteTransition(label = "puntoVivo")
    val pulso by transicion.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = Motion.Standard),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulsoPunto"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .graphicsLayer { alpha = pulso }
            .clip(CircleShape)
            .background(RostroTema.Ok)
    )
}

// ─── ARMADO ───────────────────────────────────────────────────────────────────

@Composable
private fun BloqueArmado(
    rostro: RostroLoginUiState,
    arbitro: LoginArbitro,
    validando: Boolean,
    nfcDisponible: Boolean,
    onReintentarRostro: () -> Unit
) {
    val canalEnCurso = arbitro.canalEnCurso

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // El rostro manda: es el único canal que necesita que mires la pantalla.
        Box(
            modifier = Modifier
                .size(196.dp)
                .clickable(
                    enabled = rostro.retirado && !validando,
                    role = Role.Button,
                    onClick = onReintentarRostro
                ),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = PmmBgSurface,
                border = BorderStroke(1.dp, PmmNavyBorder),
                shadowElevation = 5.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    RostroGlifo(
                        estado = rostro.estado,
                        modifier = Modifier.size(174.dp),
                        colorBase = PmmBlueDeep
                    )
                }
            }
        }

        TextoDeEstado(
            rostro = rostro,
            validando = validando,
            canalEnCurso = canalEnCurso
        )

        // Dos puntos, uno por intento. Sólo aparecen cuando ya hay algo que
        // contar: mostrar "1 de 2" desde el arranque sería anunciar un fracaso
        // que todavía no pasa.
        if (rostro.intento > 0 && !rostro.exito && !validando) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(2) { indice ->
                    Box(
                        modifier = Modifier
                            .size(if (indice < rostro.intento) 7.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (indice < rostro.intento) RostroTema.Aviso else RostroTema.Linea
                            )
                    )
                }
            }
        }

        PastillasDeCanal(
            nfcDisponible = nfcDisponible,
            canalEnCurso = canalEnCurso
        )
    }
}

@Composable
private fun TextoDeEstado(
    rostro: RostroLoginUiState,
    validando: Boolean,
    canalEnCurso: LoginCanal?
) {
    // Cuando el gafete o el lector tomaron el turno, el texto habla de ELLOS.
    // Dejar el "Mírame" del rostro mientras se valida un gafete es la clase de
    // desajuste que hace sentir que dos cosas pelean por la pantalla.
    val titulo = when {
        validando && canalEnCurso == LoginCanal.NFC -> "Gafete leído"
        validando && canalEnCurso == LoginCanal.LECTOR -> "Código leído"
        validando -> "Validando"
        else -> rostro.titulo
    }
    val nota = when {
        validando && canalEnCurso != LoginCanal.ROSTRO -> "Un momento…"
        validando -> ""
        else -> rostro.nota
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.heightIn(min = 62.dp)
    ) {
        AnimatedContent(
            targetState = titulo,
            transitionSpec = {
                fadeIn(tween(Motion.DurationQuick)) togetherWith fadeOut(tween(Motion.DurationQuick))
            },
            label = "tituloAcceso"
        ) { texto ->
            Text(
                text = texto,
                color = when (rostro.estado) {
                    EstadoGlifo.EXITO -> RostroTema.Ok
                    EstadoGlifo.FALLO -> RostroTema.Aviso
                    else -> RostroTema.Texto
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.6).sp,
                textAlign = TextAlign.Center
            )
        }

        if (nota.isNotBlank()) {
            Text(
                text = nota,
                color = RostroTema.TextoTenue,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Gafete y lector, uno al lado del otro.
 *
 * Respiran con UNA sola animación compartida y desfasada, no con una por
 * pastilla: son dos lectores del mismo reloj, así que la tableta anima un
 * valor y no dos.
 */
@Composable
private fun PastillasDeCanal(
    nfcDisponible: Boolean,
    canalEnCurso: LoginCanal?
) {
    val transicion = rememberInfiniteTransition(label = "pastillas")
    val pulso by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsoPastillas"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (nfcDisponible) "TAMBIÉN ESCUCHANDO" else "TAMBIÉN ESCUCHA EL LECTOR",
            color = PmmTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 330.dp)
    ) {
        if (nfcDisponible) {
            PastillaCanal(
                modifier = Modifier.weight(1f),
                icono = Icons.Default.Nfc,
                etiqueta = "Gafete",
                pulso = pulso,
                desfase = 0f,
                gano = canalEnCurso == LoginCanal.NFC,
                perdio = canalEnCurso != null && canalEnCurso != LoginCanal.NFC
            )
        }
        PastillaCanal(
            modifier = Modifier.weight(1f),
            icono = Icons.Default.QrCodeScanner,
            etiqueta = "Lector",
            pulso = pulso,
            desfase = 0.5f,
            gano = canalEnCurso == LoginCanal.LECTOR,
            perdio = canalEnCurso != null && canalEnCurso != LoginCanal.LECTOR
        )
    }
    }
}

@Composable
private fun PastillaCanal(
    modifier: Modifier = Modifier,
    icono: ImageVector,
    etiqueta: String,
    pulso: Float,
    desfase: Float,
    gano: Boolean,
    perdio: Boolean
) {
    val escala by animateFloatAsState(
        targetValue = when {
            gano -> 1.05f
            perdio -> 0.94f
            else -> 1f
        },
        animationSpec = tween(Motion.DurationMedium, easing = Motion.Standard),
        label = "escalaPastilla"
    )
    val opacidad by animateFloatAsState(
        targetValue = if (perdio) 0.34f else 1f,
        animationSpec = tween(Motion.DurationMedium),
        label = "opacidadPastilla"
    )

    // Deliberadamente NO parece un botón: cápsula muy redondeada, fondo azul
    // tenue, sin sombra y con el texto en el gris secundario. Con la misma
    // tarjeta blanca de los desvíos de abajo, la pantalla se leía como cuatro
    // botones en dos filas y nadie sabía cuáles se tocan -- verificado en la
    // tableta. Un indicador tiene que verse como un indicador.
    Surface(
        modifier = modifier.graphicsLayer {
            scaleX = escala
            scaleY = escala
            alpha = opacidad
        },
        shape = RoundedCornerShape(999.dp),
        color = if (gano) PmmBlueDeep else PmmNavyVariant,
        border = if (gano) null else BorderStroke(1.dp, PmmNavyBorder),
        shadowElevation = if (gano) 4.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                // El aro sólo se dibuja mientras la pastilla espera. Cuando
                // alguien ya ganó el turno no hay nada que anunciar, y dejar de
                // leer `pulso` aquí es lo que detiene la animación de verdad.
                if (!gano && !perdio) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val p = (pulso + desfase) % 1f
                        drawCircle(
                            color = PmmBlueDeep.copy(alpha = (1f - p) * 0.35f),
                            radius = (size.minDimension / 2f) * (0.55f + p * 0.45f),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                }
                Icon(
                    imageVector = icono,
                    contentDescription = null,
                    tint = if (gano) Color.White else PmmBlueDeep,
                    modifier = Modifier.size(17.dp)
                )
            }
            Text(
                text = etiqueta,
                color = if (gano) Color.White else PmmTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── DESVÍOS ──────────────────────────────────────────────────────────────────

/**
 * Los dos caminos que la persona elige a propósito.
 *
 * Viven fuera del bloque que cambia entre reposo y armado, así que no se mueven
 * ni un pixel al tocar "Ingresar". Esa quietud es la que dice "esto no es un
 * paso siguiente, es otra puerta".
 */
@Composable
private fun DesviosDeAcceso(
    habilitados: Boolean,
    onAbrirQr: () -> Unit,
    onAbrirManual: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BotonDesvio(
            modifier = Modifier.weight(1f),
            icono = Icons.Default.QrCodeScanner,
            etiqueta = "Escanear QR",
            habilitado = habilitados,
            onClick = onAbrirQr
        )
        BotonDesvio(
            modifier = Modifier.weight(1f),
            icono = Icons.Default.Keyboard,
            etiqueta = "Credenciales",
            habilitado = habilitados,
            onClick = onAbrirManual
        )
    }
}

@Composable
private fun BotonDesvio(
    modifier: Modifier = Modifier,
    icono: ImageVector,
    etiqueta: String,
    habilitado: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = PmmBgSurface,
            contentColor = PmmBlueDeep,
            disabledContentColor = PmmTextMuted
        ),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(imageVector = icono, contentDescription = null, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(etiqueta, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
    }
}
