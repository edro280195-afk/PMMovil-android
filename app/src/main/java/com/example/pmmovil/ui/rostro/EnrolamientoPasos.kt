package com.example.pmmovil.ui.rostro

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas as ComposeCanvas

/**
 * Los pasos que rodean a la captura: firmar el aviso, entender qué va a pasar,
 * y administrar las apariencias ya dadas de alta.
 *
 * Viven aparte de [EnrolamientoRostroScreen] porque ésa ya carga con el aro, y
 * porque son pantallas de lectura y decisión -- nada que ver con el ritmo de la
 * captura, que es lo único que pasa en la otra.
 */

// ── Firma ────────────────────────────────────────────────────────────────────

/**
 * Firma del aviso con el dedo.
 *
 * El trazo se guarda en coordenadas de pantalla y se rasteriza al aceptar, no
 * mientras se dibuja: dibujar sobre un bitmap en cada movimiento del dedo se
 * siente pegajoso en la tableta, y aquí el retraso se nota más que en ningún
 * otro lado porque la referencia es la punta del propio dedo.
 */
@Composable
fun FirmaAviso(
    nombre: String?,
    onFirmar: (Bitmap) -> Unit,
    onVolver: () -> Unit
) {
    // Trazos terminados y el que se está dibujando. Separados para no copiar la
    // lista completa en cada movimiento del dedo.
    val trazos = remember { mutableStateListOf<List<androidx.compose.ui.geometry.Offset>>() }
    var enCurso by remember { mutableStateOf<List<androidx.compose.ui.geometry.Offset>>(emptyList()) }
    var lienzo by remember { mutableStateOf(IntSize.Zero) }
    var version by remember { mutableIntStateOf(0) }

    val hayFirma = trazos.isNotEmpty() || enCurso.size > 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) {
            BotonVolver(onClick = onVolver)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Firma tu autorización",
            color = EnrolamientoTema.Texto,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Aceptaste el aviso en la pantalla anterior. Tu firma queda guardada " +
                "junto con él como constancia.",
            color = EnrolamientoTema.TextoTenue,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 460.dp)
        )

        Spacer(Modifier.height(18.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = EnrolamientoTema.SuperficieAlta,
            border = BorderStroke(1.dp, EnrolamientoTema.Linea),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .onSizeChanged { lienzo = it }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { punto -> enCurso = listOf(punto) },
                                onDragEnd = {
                                    if (enCurso.size > 1) trazos.add(enCurso)
                                    enCurso = emptyList()
                                    version++
                                },
                                onDragCancel = { enCurso = emptyList() }
                            ) { cambio, _ ->
                                cambio.consume()
                                enCurso = enCurso + cambio.position
                            }
                        }
                ) {
                    ComposeCanvas(Modifier.fillMaxSize()) {
                        // Renglón guía, como en un recibo de papel.
                        val y = size.height * 0.74f
                        drawLine(
                            color = EnrolamientoTema.Linea,
                            start = androidx.compose.ui.geometry.Offset(28f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width - 28f, y),
                            strokeWidth = 2f
                        )
                        (trazos + listOf(enCurso)).forEach { trazo ->
                            if (trazo.size < 2) return@forEach
                            val camino = Path().apply {
                                moveTo(trazo.first().x, trazo.first().y)
                                trazo.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = camino,
                                color = EnrolamientoTema.Texto,
                                style = Stroke(
                                    width = 4.5f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (!hayFirma) {
                        Text(
                            "Firma aquí con el dedo",
                            color = EnrolamientoTema.TextoMudo,
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        nombre?.takeIf { it.isNotBlank() } ?: "Tu firma",
                        color = EnrolamientoTema.TextoMudo,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            trazos.clear()
                            enCurso = emptyList()
                            version++
                        },
                        enabled = hayFirma
                    ) {
                        Icon(
                            Icons.Default.Refresh, null,
                            modifier = Modifier.size(15.dp),
                            tint = if (hayFirma) EnrolamientoTema.Glifo else EnrolamientoTema.TextoMudo
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Borrar y repetir",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (hayFirma) EnrolamientoTema.Glifo else EnrolamientoTema.TextoMudo
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Button(
            onClick = {
                val bitmap = rasterizarFirma(trazos.toList(), lienzo) ?: return@Button
                onFirmar(bitmap)
            },
            enabled = hayFirma && lienzo.width > 0,
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EnrolamientoTema.Glifo),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Firmar y continuar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Sin firma no se guarda tu rostro.",
            color = EnrolamientoTema.TextoMudo,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Pasa los trazos a un PNG con fondo transparente.
 *
 * Se dibuja a la resolución del lienzo en pantalla y no a una fija: reescalar
 * un trazo hecho a mano alzada le mete escalones justo en las curvas, que es
 * todo lo que una firma tiene.
 */
private fun rasterizarFirma(
    trazos: List<List<androidx.compose.ui.geometry.Offset>>,
    lienzo: IntSize
): Bitmap? {
    if (trazos.isEmpty() || lienzo.width <= 0 || lienzo.height <= 0) return null

    val bitmap = Bitmap.createBitmap(lienzo.width, lienzo.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val pincel = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    trazos.forEach { trazo ->
        if (trazo.size < 2) return@forEach
        val camino = AndroidPath().apply {
            moveTo(trazo.first().x, trazo.first().y)
            trazo.drop(1).forEach { lineTo(it.x, it.y) }
        }
        canvas.drawPath(camino, pincel)
    }
    return bitmap
}

// ── Tutorial ─────────────────────────────────────────────────────────────────

/**
 * Lo que hay que saber antes de empezar, en tres puntos.
 *
 * El tercero es el que motivó la pantalla: al terminar el aro aparece
 * "Sellando" y la gente se movía o se retiraba, justo cuando hay que hacer lo
 * contrario. Perder el enrolamiento en el último paso es lo más caro que puede
 * pasar aquí -- son 30 segundos tirados y la persona ya no lo vuelve a intentar.
 */
@Composable
fun TutorialEnrolamiento(
    etiqueta: String,
    esAparienciaExtra: Boolean,
    onEtiquetaCambio: (String) -> Unit,
    onEmpezar: () -> Unit,
    onVolver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 6.dp)
        ) {
            BotonVolver(onClick = onVolver)
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Cómo va a ser",
            color = EnrolamientoTema.Texto,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Tres cosas y empezamos. Dura unos 30 segundos y la captura es automática: " +
                "no vas a tener que tocar nada.",
            color = EnrolamientoTema.TextoTenue,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier.widthIn(max = 460.dp)
        )

        Spacer(Modifier.height(18.dp))

        PasoTutorial(
            numero = "1",
            icono = Icons.Default.Straighten,
            fondo = EnrolamientoTema.AzulTenue,
            titulo = "A un brazo de distancia",
            detalle = "Sostén la tableta como para leerla, más o menos a 40 cm. " +
                "Si te acercas o te alejas de más, la pantalla te lo dice."
        )
        Spacer(Modifier.height(10.dp))
        PasoTutorial(
            numero = "2",
            icono = Icons.Default.Face,
            fondo = EnrolamientoTema.VerdeTenue,
            titulo = "Gira la cabeza despacio",
            detalle = "Primero de frente. Luego mueve la cabeza en círculo, lento, " +
                "como diciendo que no y que sí a la vez. El aro se va pintando por " +
                "donde ya pasaste; donde quede hueco, ahí falta."
        )
        Spacer(Modifier.height(10.dp))
        PasoTutorial(
            numero = "3",
            icono = Icons.Default.Verified,
            fondo = EnrolamientoTema.AmbarTenue,
            titulo = "Al final, quédate quieto",
            detalle = "Cuando el aro cierre verás \"Sellando\": es la comprobación " +
                "final y dura unos segundos. No te muevas ni retires la cara hasta " +
                "que aparezca la palomita, o hay que empezar de nuevo."
        )

        if (esAparienciaExtra) {
            Spacer(Modifier.height(18.dp))
            Text(
                "¿Cómo le ponemos a esta apariencia?",
                color = EnrolamientoTema.Texto,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Para reconocerla después en tu lista.",
                color = EnrolamientoTema.TextoTenue,
                fontSize = 12.5.sp
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = etiqueta,
                onValueChange = onEtiquetaCambio,
                placeholder = { Text("Con lentes", fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Con lentes", "Sin lentes", "Sin barba").forEach { sugerencia ->
                    Surface(
                        shape = RoundedCornerShape(99.dp),
                        color = EnrolamientoTema.Superficie,
                        border = BorderStroke(1.dp, EnrolamientoTema.Linea),
                        onClick = { onEtiquetaCambio(sugerencia) }
                    ) {
                        Text(
                            sugerencia,
                            color = EnrolamientoTema.Glifo,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onEmpezar,
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EnrolamientoTema.Glifo),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Text("Entendido, empezar", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PasoTutorial(
    numero: String,
    icono: ImageVector,
    fondo: Color,
    titulo: String,
    detalle: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = EnrolamientoTema.SuperficieAlta,
        border = BorderStroke(1.dp, EnrolamientoTema.Linea),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(fondo, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icono, null,
                    tint = EnrolamientoTema.Glifo,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        numero,
                        color = EnrolamientoTema.TextoMudo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        titulo,
                        color = EnrolamientoTema.Texto,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    detalle,
                    color = EnrolamientoTema.TextoTenue,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

// ── Apariencias ──────────────────────────────────────────────────────────────

/**
 * Las apariencias dadas de alta y qué se puede hacer con ellas.
 *
 * Existe porque el rostro cambia: quien se enroló con lentes deja de parecerse
 * a sí mismo el día que llega sin ellos, y rasurarse una barba tupida mueve el
 * vector lo suficiente para caer por debajo del umbral. Antes había que
 * reemplazar el alta completa y elegir una sola versión de uno mismo; ahora se
 * suman, y al entrar el servidor se queda con la que mejor coincida.
 */
@Composable
fun ListaApariencias(
    apariencias: List<com.example.pmmovil.data.model.FaceAparienciaDto>,
    maxApariencias: Int,
    onAgregar: () -> Unit,
    onBorrar: (Long) -> Unit
) {
    var porBorrar by remember { mutableStateOf<Long?>(null) }
    val quedaLugar = apariencias.size < maxApariencias

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Tus apariencias",
                color = EnrolamientoTema.Texto,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${apariencias.size} de $maxApariencias",
                color = EnrolamientoTema.TextoMudo,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Puedes registrarte con lentes y sin lentes, o volver a hacerlo si te " +
                "rasuras. Al entrar se compara contra todas.",
            color = EnrolamientoTema.TextoTenue,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(10.dp))

        apariencias.forEach { apariencia ->
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = EnrolamientoTema.SuperficieAlta,
                border = BorderStroke(1.dp, EnrolamientoTema.Linea),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle, null,
                        tint = EnrolamientoTema.Ok,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            apariencia.Etiqueta ?: "Principal",
                            color = EnrolamientoTema.Texto,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${apariencia.Plantillas} tomas" +
                                if (apariencia.Firmado) " · firmada" else "",
                            color = EnrolamientoTema.TextoMudo,
                            fontSize = 11.5.sp
                        )
                    }
                    TextButton(onClick = { porBorrar = apariencia.Id }) {
                        Icon(
                            Icons.Default.Delete, null,
                            tint = EnrolamientoTema.Malo,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Borrar",
                            color = EnrolamientoTema.Malo,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (quedaLugar) EnrolamientoTema.AzulTenue else EnrolamientoTema.Superficie,
            border = BorderStroke(1.dp, EnrolamientoTema.Linea),
            onClick = onAgregar,
            enabled = quedaLugar,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Edit, null,
                    tint = if (quedaLugar) EnrolamientoTema.Glifo else EnrolamientoTema.TextoMudo,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (quedaLugar) "Agregar otra apariencia" else "Llegaste al máximo",
                        color = if (quedaLugar) EnrolamientoTema.Glifo else EnrolamientoTema.TextoMudo,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (quedaLugar) {
                            "Con lentes, sin barba, lo que necesites."
                        } else {
                            "Borra una para poder agregar otra."
                        },
                        color = EnrolamientoTema.TextoMudo,
                        fontSize = 11.5.sp
                    )
                }
            }
        }
    }

    porBorrar?.let { id ->
        val nombre = apariencias.firstOrNull { it.Id == id }?.Etiqueta ?: "esta apariencia"
        AlertDialog(
            onDismissRequest = { porBorrar = null },
            title = { Text("¿Borrar \"$nombre\"?") },
            text = {
                Text(
                    if (apariencias.size <= 1) {
                        "Es la única que tienes: al borrarla dejas de poder entrar con " +
                            "el rostro. El gafete y la contraseña siguen igual."
                    } else {
                        "Se borra sólo ésta. Las demás siguen funcionando."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onBorrar(id)
                    porBorrar = null
                }) {
                    Text("Borrar", color = EnrolamientoTema.Malo, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { porBorrar = null }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Botón de volver. Es gemelo del de [EnrolamientoRostroScreen], que es privado
 * de ese archivo; duplicar catorce líneas cuesta menos que abrir aquel a todo
 * el paquete sólo por esto.
 */
@Composable
private fun BotonVolver(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = EnrolamientoTema.SuperficieAlta,
        border = BorderStroke(1.dp, EnrolamientoTema.Linea),
        onClick = onClick,
        modifier = Modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.ArrowBack, "Volver",
                tint = EnrolamientoTema.Texto,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
