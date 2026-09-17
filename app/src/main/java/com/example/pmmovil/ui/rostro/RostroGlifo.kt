package com.example.pmmovil.ui.rostro

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.example.pmmovil.ui.theme.Motion

/**
 * Estados del glifo de acceso facial. Son cinco y ninguno es decorativo:
 * cada uno dice algo distinto sobre lo que está pasando.
 */
enum class EstadoGlifo {
    /** Nadie ha pedido entrar todavía. */
    REPOSO,

    /** Cámara encendida, buscando una cara. También cubre el arranque del sensor. */
    BUSCANDO,

    /** Hay rostro y con calidad suficiente. Las esquinas se cierran. */
    DETECTADO,

    /** El vector va camino al servidor. El cometa da una sola vuelta. */
    VERIFICANDO,

    EXITO,
    FALLO
}

/**
 * El glifo de acceso facial: un marco de esquinas con una cara mínima adentro,
 * encendido sobre fondo oscuro.
 *
 * El brillo no es adorno. Sobre negro, un trazo plano de un píxel se ve barato;
 * lo que da sensación de que algo está ENCENDIDO es el desbordamiento de luz
 * alrededor del trazo. Se consigue dibujando el mismo camino tres veces --
 * ancho y casi transparente, medio, y el trazo limpio encima. No hay desenfoque
 * de por medio, así que no cuesta capas ni rompe la aceleración por hardware.
 *
 * Sigue siendo sobrio: nada de barrido láser, nada de malla de puntos, nada de
 * "procesando…". Un sistema de identidad que hace trucos parece menos serio.
 */
@Composable
fun RostroGlifo(
    estado: EstadoGlifo,
    modifier: Modifier = Modifier,
    /**
     * El glifo tambien vive en el aviso de enrolamiento, que va sobre blanco.
     * Ahi el azul levantado de [RostroTema] se ve lavado, asi que la pantalla
     * pasa el azul institucional. En oscuro no se toca nada.
     */
    colorBase: Color = RostroTema.Glifo,
    colorHalo: Color = RostroTema.Brillo
) {
    val color = when (estado) {
        EstadoGlifo.EXITO -> RostroTema.Ok
        EstadoGlifo.FALLO -> RostroTema.Aviso
        else -> colorBase
    }
    val colorBrillo = when (estado) {
        EstadoGlifo.EXITO -> RostroTema.Ok
        EstadoGlifo.FALLO -> RostroTema.Aviso
        else -> colorHalo
    }

    val ciclo = rememberInfiniteTransition(label = "ciclo")

    // Respiración lenta mientras busca.
    val respiracion by ciclo.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "respiracion"
    )

    // Brillo tenue que gira muy despacio sobre el aro. Es el único elemento
    // ambiental de la pantalla: da señal de vida sin pedir atención.
    val giro by ciclo.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "giro"
    )

    // Entrada: el glifo llega con un resorte en vez de aparecer de golpe.
    var entrada by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { entrada = 1f }
    val aparicion by animateFloatAsState(
        targetValue = entrada,
        animationSpec = spring(
            dampingRatio = Motion.SpringDampingLowBounce,
            stiffness = Motion.SpringStiffnessLow
        ),
        label = "aparicion"
    )

    val escalaObjetivo = when (estado) {
        EstadoGlifo.BUSCANDO -> 0.985f + 0.035f * respiracion
        EstadoGlifo.DETECTADO, EstadoGlifo.VERIFICANDO -> 0.93f
        else -> 1f
    }
    val escala by animateFloatAsState(
        targetValue = escalaObjetivo,
        animationSpec = spring(
            dampingRatio = Motion.SpringDampingMedBounce,
            stiffness = Motion.SpringStiffnessMedium
        ),
        label = "escala"
    )

    val intensidad by animateFloatAsState(
        targetValue = when (estado) {
            EstadoGlifo.REPOSO -> 0.30f
            EstadoGlifo.BUSCANDO -> 0.48f + 0.12f * respiracion
            EstadoGlifo.DETECTADO -> 0.92f
            EstadoGlifo.VERIFICANDO -> 1f
            EstadoGlifo.EXITO -> 0.55f
            EstadoGlifo.FALLO -> 0.9f
        },
        animationSpec = tween(Motion.DurationMedium, easing = FastOutSlowInEasing),
        label = "intensidad"
    )

    val alfaCara by animateFloatAsState(
        targetValue = when (estado) {
            EstadoGlifo.REPOSO -> 0.16f
            EstadoGlifo.BUSCANDO -> 0.42f
            EstadoGlifo.DETECTADO, EstadoGlifo.VERIFICANDO -> 0.95f
            EstadoGlifo.FALLO -> 0.55f
            EstadoGlifo.EXITO -> 0f
        },
        animationSpec = tween(Motion.DurationMedium),
        label = "alfaCara"
    )

    val alfaAro by animateFloatAsState(
        targetValue = when (estado) {
            EstadoGlifo.BUSCANDO -> 0.20f
            EstadoGlifo.DETECTADO -> 0.38f
            EstadoGlifo.VERIFICANDO -> 0.48f
            EstadoGlifo.EXITO -> 0.55f
            EstadoGlifo.FALLO -> 0.38f
            else -> 0.06f
        },
        animationSpec = tween(Motion.DurationMedium),
        label = "alfaAro"
    )

    // Una sola vuelta, no un spinner. Un spinner diría "esto puede tardar lo
    // que sea"; una vuelta única dice "esto termina", que es la verdad.
    val barrido by animateFloatAsState(
        targetValue = if (estado == EstadoGlifo.VERIFICANDO) 1f else 0f,
        animationSpec = if (estado == EstadoGlifo.VERIFICANDO) {
            tween(980, easing = Motion.SharpOut)
        } else {
            tween(Motion.DurationQuick)
        },
        label = "barrido"
    )

    val exito by animateFloatAsState(
        targetValue = if (estado == EstadoGlifo.EXITO) 1f else 0f,
        animationSpec = tween(620, easing = Motion.EmphasizedDecelerate),
        label = "exito"
    )

    val medidor = remember { PathMeasure() }

    Canvas(modifier = modifier) {
        val lado = size.minDimension
        val factor = lado / LIENZO
        val centro = Offset(LIENZO / 2f, LIENZO / 2f)

        translate((size.width - lado) / 2f, (size.height - lado) / 2f) {
            // `size` esta en pixeles y el lienzo usa 200 unidades. Escalar
            // alrededor de (100, 100) dejaba el centro logico en 100 px,
            // aunque el centro real de un Canvas de 170 dp fuera 127.5 px en
            // esta tableta. Por eso todo el glifo, incluida la cara, quedaba
            // arriba dentro del circulo. Desde el origen, 100 * factor cae
            // exactamente en el centro fisico del Canvas.
            scale(factor, factor, pivot = Offset.Zero) {
                scale(0.86f + 0.14f * aparicion, pivot = centro) {

                    // Halo propio del glifo, además del de la pantalla.
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colorBrillo.copy(alpha = 0.20f * intensidad * aparicion),
                                colorBrillo.copy(alpha = 0.06f * intensidad * aparicion),
                                Color.Transparent
                            ),
                            center = centro,
                            radius = LIENZO * 0.62f
                        ),
                        radius = LIENZO * 0.62f,
                        center = centro
                    )

                    if (alfaAro > 0.01f) {
                        conBrillo(color, colorBrillo, alfaAro * aparicion, 2.4f) { trazo, c, a ->
                            drawCircle(c, RADIO_ARO, centro, a, trazo)
                        }
                        // El destello que gira: un arco corto, muy tenue.
                        if (estado == EstadoGlifo.BUSCANDO || estado == EstadoGlifo.REPOSO) {
                            rotate(giro, pivot = centro) {
                                drawArc(
                                    color = colorBrillo,
                                    startAngle = -90f,
                                    sweepAngle = 46f,
                                    useCenter = false,
                                    topLeft = Offset(centro.x - RADIO_ARO, centro.y - RADIO_ARO),
                                    size = Size(RADIO_ARO * 2f, RADIO_ARO * 2f),
                                    alpha = 0.16f * intensidad * aparicion,
                                    style = Stroke(width = 2.4f, cap = StrokeCap.Round)
                                )
                            }
                        }
                    }

                    if (barrido > 0f && barrido < 1f) {
                        cometa(centro, barrido, colorBrillo)
                    }

                    // Estallido del acierto: un aro que se expande y se apaga.
                    if (exito > 0f && exito < 1f) {
                        drawCircle(
                            color = RostroTema.Ok,
                            radius = RADIO_ARO * (1f + 0.55f * exito),
                            center = centro,
                            alpha = (1f - exito) * 0.55f,
                            style = Stroke(width = 3f * (1f - exito) + 0.6f)
                        )
                    }

                    scale(escala, escala, pivot = centro) {
                        esquinas().forEach { camino ->
                            conBrillo(color, colorBrillo, intensidad * aparicion, 6.5f) { trazo, c, a ->
                                drawPath(camino, color = c, alpha = a, style = trazo)
                            }
                        }
                        if (alfaCara > 0.01f) {
                            // Las facciones tienen su propio peso visual: sus
                            // puntos van de y=82 a y=140 y, sin esta correccion,
                            // el centro de la cara cae debajo del centro del
                            // aro. Se sube el grupo completo, no cada trazo por
                            // separado, para conservar la proporcion del icono.
                            translate(top = -10f) {
                                cara().forEach { camino ->
                                    conBrillo(color, colorBrillo, alfaCara * intensidad, 5.8f) { trazo, c, a ->
                                        drawPath(camino, color = c, alpha = a, style = trazo)
                                    }
                                }
                            }
                        }
                    }

                    if (exito > 0f) {
                        val completo = Path().apply {
                            moveTo(66f, 101f); lineTo(89f, 125f); lineTo(136f, 76f)
                        }
                        medidor.setPath(completo, false)
                        val parcial = Path()
                        // La palomita se dibuja en el primer 70 % del gesto; el
                        // resto es el estallido, para que no compitan.
                        val trazado = (exito / 0.7f).coerceIn(0f, 1f)
                        medidor.getSegment(0f, medidor.length * trazado, parcial, true)
                        conBrillo(RostroTema.Ok, RostroTema.Ok, 1f, 8f) { trazo, c, a ->
                            drawPath(parcial, color = c, alpha = a, style = trazo)
                        }
                    }
                }
            }
        }
    }
}

/**
 * El barrido de verificación, como cometa.
 *
 * Un arco sólido se ve plano y barato. Éste se dibuja en segmentos con alfa y
 * grosor decrecientes hacia atrás: cabeza brillante y estela que se apaga. Es
 * la misma diferencia que hay entre una raya y un movimiento.
 */
private fun DrawScope.cometa(centro: Offset, avance: Float, color: Color) {
    val cabeza = -90f + 370f * avance
    val segmentos = 16
    val paso = 5.2f

    for (i in 0 until segmentos) {
        val t = i / (segmentos - 1f)          // 0 = cabeza, 1 = cola
        val alfa = (1f - t) * (1f - t)        // se apaga rápido, como una estela
        if (alfa < 0.02f) continue

        drawArc(
            color = color,
            startAngle = cabeza - paso * (i + 1),
            sweepAngle = paso + 0.6f,          // se traslapan para que no se vean costuras
            useCenter = false,
            topLeft = Offset(centro.x - RADIO_ARO, centro.y - RADIO_ARO),
            size = Size(RADIO_ARO * 2f, RADIO_ARO * 2f),
            alpha = alfa,
            style = Stroke(width = 1.6f + 2.6f * (1f - t), cap = StrokeCap.Round)
        )
    }

    // Un punto de luz en la punta: es lo que hace que se lea como algo que
    // viaja y no como un arco que crece.
    val rad = Math.toRadians(cabeza.toDouble())
    val punta = Offset(
        centro.x + (Math.cos(rad) * RADIO_ARO).toFloat(),
        centro.y + (Math.sin(rad) * RADIO_ARO).toFloat()
    )
    drawCircle(color, radius = 7f, center = punta, alpha = 0.14f)
    drawCircle(color, radius = 3.4f, center = punta, alpha = 0.9f)
}

/**
 * Pinta el mismo trazo tres veces -- ancho y casi transparente, intermedio, y
 * el limpio encima -- para que sobre negro se lea como luz y no como tinta.
 */
private inline fun DrawScope.conBrillo(
    color: Color,
    colorBrillo: Color,
    alfa: Float,
    grosor: Float,
    dibujo: (Stroke, Color, Float) -> Unit
) {
    if (alfa <= 0.01f) return
    dibujo(Stroke(grosor * 3.4f, cap = StrokeCap.Round, join = StrokeJoin.Round), colorBrillo, alfa * 0.10f)
    dibujo(Stroke(grosor * 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round), colorBrillo, alfa * 0.18f)
    dibujo(Stroke(grosor, cap = StrokeCap.Round, join = StrokeJoin.Round), color, alfa)
}

private const val LIENZO = 200f
private const val RADIO_ARO = 78f
private const val RADIO_ESQUINA = 32f

private fun esquinas(): List<Path> = listOf(
    Path().apply {
        moveTo(40f, 72f); lineTo(40f, 56f)
        arcTo(Rect(40f, 40f, 40f + RADIO_ESQUINA, 40f + RADIO_ESQUINA), 180f, 90f, false)
        lineTo(72f, 40f)
    },
    Path().apply {
        moveTo(128f, 40f); lineTo(144f, 40f)
        arcTo(Rect(160f - RADIO_ESQUINA, 40f, 160f, 40f + RADIO_ESQUINA), 270f, 90f, false)
        lineTo(160f, 72f)
    },
    Path().apply {
        moveTo(160f, 128f); lineTo(160f, 144f)
        arcTo(Rect(160f - RADIO_ESQUINA, 160f - RADIO_ESQUINA, 160f, 160f), 0f, 90f, false)
        lineTo(128f, 160f)
    },
    Path().apply {
        moveTo(72f, 160f); lineTo(56f, 160f)
        arcTo(Rect(40f, 160f - RADIO_ESQUINA, 40f + RADIO_ESQUINA, 160f), 90f, 90f, false)
        lineTo(40f, 128f)
    }
)

/** Dos ojos, nariz y boca. Lo mínimo para que se lea "cara" y nada más. */
private fun cara(): List<Path> = listOf(
    Path().apply {
        moveTo(79f, 82f); lineTo(79f, 98f)
        moveTo(121f, 82f); lineTo(121f, 98f)
    },
    Path().apply {
        moveTo(100f, 84f); lineTo(100f, 107f); lineTo(109f, 107f)
    },
    Path().apply {
        moveTo(80f, 126f); quadraticTo(100f, 140f, 120f, 126f)
    }
)
