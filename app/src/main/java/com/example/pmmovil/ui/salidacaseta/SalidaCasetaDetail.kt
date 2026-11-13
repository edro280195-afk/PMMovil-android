package com.example.pmmovil.ui.salidacaseta

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.pmmovil.data.model.SalidaCarril
import com.example.pmmovil.data.model.SalidaPatioItem
import com.example.pmmovil.di.NetworkModule
import com.example.pmmovil.ui.components.AnimatedCounter
import com.example.pmmovil.ui.components.EvidenceGalleryDialog
import com.example.pmmovil.ui.components.evidenceGalleryStartIndex
import com.example.pmmovil.ui.components.pmmPressable
import com.example.pmmovil.ui.components.pmmTap
import com.example.pmmovil.ui.theme.Motion
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import java.io.File

// ═══════════════════════════════════════════════════════════════════════════
//  Panel de detalle: banner del carril, datos, avisos, fotos y la ÚNICA acción
//  que le corresponde a ese carril.
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun PanelDetalle(
    state: SalidaUiState,
    ancha: Boolean,
    onObservaciones: (String) -> Unit,
    onAbrirCamara: () -> Unit,
    onQuitarFoto: (String) -> Unit,
    onConfirmar: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Guarda el último estado conocido de cada renglón: mientras el panel se cierra
    // (por ejemplo, tras confirmar) el renglón ya no está en la lista, pero la
    // animación de salida todavía necesita dibujarlo.
    val ultimos = remember { mutableMapOf<String, SalidaPatioItem>() }
    state.items.forEach { ultimos[it.id] = it }

    Surface(
        modifier = modifier,
        shape = if (ancha) RoundedCornerShape(20.dp) else RoundedCornerShape(0.dp),
        color = PmmBgSurface,
        border = if (ancha) BorderStroke(1.5.dp, PmmNavyBorder) else null
    ) {
        AnimatedContent(
            targetState = state.selectedId,
            transitionSpec = {
                (fadeIn(tween(Motion.DurationMedium, delayMillis = 60)) +
                    slideInHorizontally(tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)) { it / 14 }) togetherWith
                    fadeOut(tween(Motion.DurationQuick))
            },
            label = "detalle"
        ) { id ->
            val item = id?.let { ultimos[it] }
            if (item == null) {
                SinSeleccion()
            } else {
                ContenidoDetalle(
                    item = item,
                    state = state,
                    ancha = ancha,
                    onObservaciones = onObservaciones,
                    onAbrirCamara = onAbrirCamara,
                    onQuitarFoto = onQuitarFoto,
                    onConfirmar = onConfirmar
                )
            }
        }
    }
}

@Composable
private fun SinSeleccion() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.LocalShipping, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(14.dp))
            Text("Elige una caja", color = PmmTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Verás su carril, sus datos y la única acción que le corresponde.\nO escanea el QR de la caja para llegar directo.",
                color = PmmTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContenidoDetalle(
    item: SalidaPatioItem,
    state: SalidaUiState,
    ancha: Boolean,
    onObservaciones: (String) -> Unit,
    onAbrirCamara: () -> Unit,
    onQuitarFoto: (String) -> Unit,
    onConfirmar: () -> Unit
) {
    val carril = item.carrilEnum
    val estilo = carril.style()
    val multiple = item.cajas.size > 1
    val primera = item.cajas.firstOrNull()

    Column(Modifier.fillMaxSize()) {
        BannerCarril(item)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Héroe: la caja, grande y en monoespaciada (es lo que el guardia compara con la placa física).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (multiple) "${item.cajas.size} cajas" else etiquetaCaja(primera?.numCaja.orEmpty()),
                        fontFamily = Mono,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (ancha) 32.sp else 28.sp,
                        color = PmmTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.tipoOp.isNotBlank()) PildoraTipoOp(item.tipoOp)
                }
                Text(
                    text = listOfNotNull(
                        if (item.esTractocamion) "Tractocamión sin caja" else null,
                        primera?.lineaCaja?.takeIf { it.isNotBlank() },
                        primera?.tipoUnidad?.takeIf { it.isNotBlank() },
                        nombrePlanta(item.planta)?.let { "Planta $it" }
                    ).joinToString(" · "),
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
                if (multiple) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.cajas.forEach {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PmmBgCard)
                                    .padding(horizontal = 9.dp, vertical = 4.dp)
                            ) {
                                Text(it.numCaja, fontFamily = Mono, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = PmmTextPrimary)
                            }
                        }
                    }
                }
            }

            // Ciclo de la relación (solo carriles que tienen relación).
            if (carril != SalidaCarril.MANUAL) {
                Seccion("CICLO DE LA RELACIÓN ${item.relno}") {
                    PasosDeRelacion(item)
                }
            }

            // Datos
            Seccion(null) { DatosDelCarril(item, ancha) }

            // Bultos
            if (carril == SalidaCarril.LISTA || carril == SalidaCarril.PROCESO) {
                if (item.bultosTotal > 0) BultosAvance(item.bultosCargados, item.bultosTotal)
            }

            // Avisos informativos (nunca bloquean: las reglas las define la gerencia)
            val avisos = remember(item) { avisosDe(item) }
            AnimatedVisibility(visible = avisos.isNotEmpty(), enter = fadeIn() + androidx.compose.animation.expandVertically()) {
                Seccion("AVISOS") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        avisos.forEach { Aviso(it) }
                    }
                }
            }

            // Fotos de salida (solo con relación) / fotos de llegada (manual)
            if (carril == SalidaCarril.LISTA) {
                Seccion("FOTOS DE SALIDA") {
                    FotosDeSalida(
                        rutas = state.fotosDe(item),
                        puedeAgregar = state.relAgregar,
                        puedeBorrar = state.relBorrar,
                        onAbrirCamara = onAbrirCamara,
                        onQuitar = onQuitarFoto
                    )
                }
            }
            if (carril == SalidaCarril.MANUAL && primera != null && primera.fotosEntrada.isNotEmpty()) {
                Seccion("FOTOS DE LA LLEGADA") {
                    FotosDeLlegada(primera.casetaId, primera.fotosEntrada)
                }
            }

            // Observaciones
            if (carril == SalidaCarril.LISTA || carril == SalidaCarril.MANUAL) {
                Seccion(if (carril == SalidaCarril.MANUAL) "MOTIVO / OBSERVACIONES" else "OBSERVACIONES") {
                    CampoObservaciones(
                        valor = state.observacionDe(item),
                        placeholder = if (carril == SalidaCarril.MANUAL) "Motivo de la salida manual (opcional)…" else "Observaciones de la salida (opcional)…",
                        habilitado = state.puedeActuar(item),
                        onChange = onObservaciones
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        BarraAccion(item, state, estilo.color, onConfirmar)
    }
}

// ── Banner ───────────────────────────────────────────────────────────────────

@Composable
private fun BannerCarril(item: SalidaPatioItem) {
    val carril = item.carrilEnum
    val estilo = carril.style()
    val (titulo, detalle) = when (carril) {
        SalidaCarril.LISTA -> "LISTA PARA SALIR · con relación" to buildString {
            append("Relación ${item.relno} autorizada")
            if (item.usuAutoriza.isNotBlank()) append(" por ${item.usuAutoriza}")
            item.minutosEspera?.let { append(" hace ${formatoMinutos(it)}") }
            if (item.sinCajaEnCaseta) append(". No hay una caja suya registrada en caseta: al confirmar solo se cierra la relación.")
        }
        SalidaCarril.MANUAL -> "SALIDA MANUAL · esta caja no tiene relación" to
            "Se registra como salida manual: no cierra ninguna relación ni avisa al cliente."
        SalidaCarril.PROCESO -> "NO PUEDE SALIR · relación en proceso" to item.motivo.ifBlank { "La relación todavía no está autorizada." }
        SalidaCarril.CERRAR -> "YA SALIÓ CON SU RELACIÓN · falta cerrar el patio" to
            "La relación ${item.relno} salió ${formatoFechaCorta(item.salidaCaseta)}. La caja sigue figurando en el patio."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(estilo.soft)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(estilo.icon, contentDescription = null, tint = estilo.ink, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titulo, color = estilo.ink, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Text(detalle, color = estilo.ink.copy(alpha = 0.85f), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun Seccion(titulo: String?, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titulo != null) {
            Text(titulo, color = PmmTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        }
        contenido()
    }
}

// ── Ciclo de la relación ─────────────────────────────────────────────────────

@Composable
private fun PasosDeRelacion(item: SalidaPatioItem) {
    val (actual, conProblema) = posicionPaso(item.estatusCarga, item.carrilEnum)
    // Los tramos se "llenan" en cascada al abrir el detalle: empieza en gris y se pinta paso a paso.
    var armado by remember(item.id) { mutableStateOf(false) }
    LaunchedEffect(item.id) { armado = true }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        PasosRelacion.forEachIndexed { i, nombre ->
            val hecho = i < actual || (item.carrilEnum == SalidaCarril.CERRAR && i <= actual)
            val esActual = i == actual && item.carrilEnum != SalidaCarril.CERRAR
            val problema = conProblema && esActual
            val objetivoCirculo = when {
                !armado -> Color(0xFFE2E8F0)
                problema -> Color(0xFFDC2626)
                hecho -> PmmGreen
                esActual -> PmmBlueMid
                else -> Color(0xFFE2E8F0)
            }
            val color by animateColorAsState(objetivoCirculo, tween(320, delayMillis = i * 110), label = "paso-$i")
            val colorTramoIzq by animateColorAsState(
                if (armado && i <= actual) PmmGreen else Color(0xFFE2E8F0),
                tween(320, delayMillis = i * 110),
                label = "tramo-$i"
            )
            Box(Modifier.weight(1f)) {
                if (i > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 10.dp)
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .background(colorTramoIzq)
                    )
                }
                if (i < PasosRelacion.lastIndex) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 10.dp)
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .background(if (armado && i < actual) PmmGreen else Color(0xFFE2E8F0))
                    )
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(23.dp)
                            .clip(CircleShape)
                            .background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (armado && hecho && !problema) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        } else if (problema) {
                            Text("!", color = Color.White, fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        nombre,
                        color = if (hecho || esActual) PmmTextPrimary else PmmTextMuted,
                        fontSize = 8.sp,
                        fontWeight = if (esActual) FontWeight.Black else FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Datos ────────────────────────────────────────────────────────────────────

private data class CampoDato(
    val etiqueta: String,
    val valor: String,
    val mono: Boolean = false,
    val falta: Boolean = false,
    val ancho: Int = 1
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DatosDelCarril(item: SalidaPatioItem, ancha: Boolean) {
    val columnas = if (ancha) 3 else 2
    val primera = item.cajas.firstOrNull()
    val campos: List<CampoDato> = when (item.carrilEnum) {
        SalidaCarril.LISTA -> listOf(
            CampoDato("Transportista", item.transfer.ifBlank { "—" }),
            CampoDato("Operador", item.operador.ifBlank { "Falta capturar" }, falta = item.operador.isBlank()),
            CampoDato("Tractor", item.tractor.ifBlank { "Falta capturar" }, mono = true, falta = item.tractor.isBlank()),
            CampoDato("Gafete", item.gafete.ifBlank { "Falta capturar" }, mono = true, falta = item.gafete.isBlank()),
            CampoDato("Sellos", item.sellos.ifBlank { "Sin sellos" }, mono = true, falta = item.sellos.isBlank(), ancho = 2)
        )
        SalidaCarril.MANUAL -> listOf(
            CampoDato("Cliente", item.clientes.joinToString(", ").ifBlank { "—" }, ancho = if (ancha) 1 else 2),
            CampoDato("Tipo de movimiento", item.tipoMovimiento.replace("/", " / ").ifBlank { "—" }),
            CampoDato("En patio", if (item.diasEnPatio == 0) "${item.horasEnPatio} h" else "${item.diasEnPatio} días"),
            CampoDato("Llegó", formatoFechaCorta(item.fechaArribo)),
            CampoDato("Línea", primera?.lineaCaja?.ifBlank { "—" } ?: "—"),
            CampoDato("Camión / placas", listOfNotNull(primera?.numCamion?.takeIf { it.isNotBlank() }, primera?.placas?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "—" }, mono = true)
        )
        SalidaCarril.PROCESO -> listOf(
            CampoDato("Clientes", item.clientes.joinToString(", ").ifBlank { "—" }, ancho = 2),
            CampoDato("Estatus", item.estatusCarga.ifBlank { "Sin carga iniciada" }, mono = true),
            CampoDato("Trabaja", item.usuEdit.ifBlank { item.usuCarga.ifBlank { "—" } }),
            CampoDato("Cargando desde", item.minutosCargando?.let { "hace ${formatoMinutos(it)}" } ?: "—"),
            CampoDato("Transportista", item.transfer.ifBlank { "—" })
        )
        SalidaCarril.CERRAR -> listOf(
            CampoDato("Clientes", item.clientes.joinToString(", ").ifBlank { "—" }, ancho = 2),
            CampoDato("Salió", formatoFechaCorta(item.salidaCaseta)),
            CampoDato("En patio", "${item.diasEnPatio} días"),
            CampoDato("Transportista", item.transfer.ifBlank { "—" }, ancho = 2)
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Reparte los campos en filas respetando el ancho (span) de cada uno.
        val filas = mutableListOf<List<CampoDato>>()
        var fila = mutableListOf<CampoDato>()
        var usado = 0
        campos.forEach { c ->
            val span = c.ancho.coerceAtMost(columnas)
            if (usado + span > columnas) { filas += fila; fila = mutableListOf(); usado = 0 }
            fila += c; usado += span
        }
        if (fila.isNotEmpty()) filas += fila
        filas.forEach { f ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                f.forEach { c -> TarjetaDato(c, Modifier.weight(c.ancho.coerceAtMost(columnas).toFloat())) }
                val sobrante = columnas - f.sumOf { it.ancho.coerceAtMost(columnas) }
                if (sobrante > 0) Spacer(Modifier.weight(sobrante.toFloat()))
            }
        }
        if (item.carrilEnum == SalidaCarril.LISTA && item.clientes.isNotEmpty()) {
            Text("CLIENTES", color = PmmTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.clientes.forEach {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFFE0E7FF))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) { Text(it, color = Color(0xFF3730A3), fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@Composable
private fun TarjetaDato(campo: CampoDato, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PmmBgCard)
            .padding(horizontal = 11.dp, vertical = 8.dp)
    ) {
        Text(campo.etiqueta.uppercase(), color = PmmTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 0.9.sp)
        Text(
            text = campo.valor,
            color = if (campo.falta) PmmAmber else PmmTextPrimary,
            fontFamily = if (campo.mono) Mono else null,
            fontWeight = if (campo.falta) FontWeight.ExtraBold else FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun BultosAvance(cargados: Int, total: Int) {
    val fraccion by animateFloatAsState(
        targetValue = if (total <= 0) 0f else (cargados.toFloat() / total).coerceIn(0f, 1f),
        animationSpec = tween(700, easing = Motion.EmphasizedDecelerate),
        label = "bultos"
    )
    val completo = cargados >= total
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Bultos", color = PmmTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Box(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE2E8F0))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraccion)
                    .background(if (completo) PmmGreen else PmmBlueMid)
            )
        }
        Text("$cargados/$total", color = PmmTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
    }
}

// ── Avisos informativos ──────────────────────────────────────────────────────
// Solo muestran hechos que ya están en los datos; ninguno bloquea la salida.
// Las reglas de la gerencia se enchufarán aquí cuando las defina.

private fun avisosDe(item: SalidaPatioItem): List<String> {
    if (item.carrilEnum != SalidaCarril.LISTA) return emptyList()
    val avisos = mutableListOf<String>()
    val faltan = listOf(item.tractor, item.operador, item.gafete).count { it.isBlank() }
    if (faltan > 0) avisos += "Faltan $faltan de 3 datos de transporte (tractor, operador, gafete). Los captura el sistema de escritorio."
    if (item.sellos.isBlank()) avisos += "La relación no tiene sellos capturados."
    val cajaPatio = item.cajas.singleOrNull()?.numCaja.orEmpty()
    fun norm(s: String) = s.filter { it.isLetterOrDigit() }.uppercase()
    if (cajaPatio.isNotBlank() && item.cajaRelacion.isNotBlank() && norm(cajaPatio) != norm(item.cajaRelacion)) {
        avisos += "La caja del patio ($cajaPatio) no coincide con la de la relación (${item.cajaRelacion}). Verifica la unidad."
    }
    item.minutosEspera?.takeIf { it > 1440 }?.let {
        avisos += "Lleva ${formatoMinutos(it)} autorizada sin salir. Confirma que la unidad sigue en el patio."
    }
    return avisos
}

@Composable
private fun Aviso(texto: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFEF3C7))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.WarningAmber, contentDescription = null, tint = PmmAmber, modifier = Modifier.size(17.dp))
        Text(texto, color = Color(0xFF78350F), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

// ── Fotos ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FotosDeSalida(
    rutas: List<String>,
    puedeAgregar: Boolean,
    puedeBorrar: Boolean,
    onAbrirCamara: () -> Unit,
    onQuitar: (String) -> Unit
) {
    // Ruta de la foto abierta en el visor grande (null = cerrado). Es la misma pantalla que usa Entrada Caseta.
    var abierta by remember { mutableStateOf<String?>(null) }
    val galeria = remember(rutas) { SalidaFotosGaleria.deSalida(rutas) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rutas.forEach { ruta ->
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PmmNavyVariant)
                    .pmmTap { abierta = ruta }
            ) {
                AsyncImage(model = File(ruta), contentDescription = "Ver foto en grande", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (puedeBorrar) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .pmmTap { onQuitar(ruta) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Quitar foto", tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        if (puedeAgregar) {
            Column(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.5.dp, PmmBlueMid.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .pmmTap(onClick = onAbrirCamara),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PmmBlueMid, modifier = Modifier.size(22.dp))
                Text(if (rutas.isEmpty()) "Agregar" else "Más", color = PmmBlueMid, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    if (!puedeAgregar) {
        Text("Tu perfil no permite agregar fotografías de salida.", color = PmmTextMuted, fontSize = 11.sp)
    } else if (rutas.isEmpty()) {
        Text("Aún no hay fotos. La evidencia de la unidad y los sellos se guarda con la salida.", color = PmmTextMuted, fontSize = 11.sp)
    }
    if (rutas.isNotEmpty()) {
        Text("Toca una foto para verla en grande.", color = PmmTextMuted, fontSize = 11.sp)
    }

    abierta?.let { ruta ->
        EvidenceGalleryDialog(
            items = galeria,
            startIndex = evidenceGalleryStartIndex(galeria, ruta),
            onDismiss = { abierta = null },
            // Sin permiso de borrar el visor no ofrece el botón.
            onDelete = if (puedeBorrar) ({ foto -> onQuitar(foto.id) }) else null,
            deleteMessage = "Esta evidencia se quitará de la salida."
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FotosDeLlegada(casetaId: String, indices: List<Int>) {
    val base = NetworkModule.BASE_URL
    val urlDe = { indice: Int -> "${base}Foto/caseta/ver?casetaId=${Uri.encode(casetaId)}&indice=$indice" }
    // Índice (en el servidor) de la foto abierta en el visor grande; null = cerrado.
    var abierta by remember { mutableStateOf<Int?>(null) }
    val galeria = remember(casetaId, indices) { SalidaFotosGaleria.deLlegada(indices, urlDe) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        indices.forEach { indice ->
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PmmNavyVariant)
                    .pmmTap { abierta = indice }
            ) {
                AsyncImage(
                    model = urlDe(indice),
                    contentDescription = "Foto de llegada $indice",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    Text("Ayudan a reconocer la caja en el patio. Toca una para verla en grande.", color = PmmTextMuted, fontSize = 11.sp)

    abierta?.let { indice ->
        EvidenceGalleryDialog(
            items = galeria,
            startIndex = evidenceGalleryStartIndex(galeria, "llegada-$indice"),
            onDismiss = { abierta = null }
            // Sin onDelete: estas fotos viven en el servidor y se tomaron en la Entrada.
        )
    }
}

// ── Observaciones ────────────────────────────────────────────────────────────

@Composable
private fun CampoObservaciones(valor: String, placeholder: String, habilitado: Boolean, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = PmmBgCard,
        border = BorderStroke(1.5.dp, PmmNavyBorder)
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (valor.isEmpty()) Text(placeholder, color = PmmTextMuted, fontSize = 13.sp)
            BasicTextField(
                value = valor,
                onValueChange = onChange,
                enabled = habilitado,
                textStyle = TextStyle(color = PmmTextPrimary, fontSize = 14.sp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            )
            Text(
                "${valor.length}/100",
                color = PmmTextMuted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

// ── Barra de acción ──────────────────────────────────────────────────────────

@Composable
private fun BarraAccion(item: SalidaPatioItem, state: SalidaUiState, color: Color, onConfirmar: () -> Unit) {
    val carril = item.carrilEnum
    val puede = state.puedeActuar(item)
    val habilitado = puede && !state.isProcessing
    val fondo by animateColorAsState(
        targetValue = if (habilitado) color else Color(0xFFCBD5E1),
        animationSpec = tween(Motion.DurationMedium),
        label = "accion-fondo"
    )
    val fotos = state.fotosDe(item).size

    Surface(color = PmmBgSurface, shadowElevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(Modifier.weight(1f).animateContentSize()) {
                if (!puede && carril != SalidaCarril.PROCESO) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = PmmTextMuted, modifier = Modifier.size(14.dp))
                        Text(
                            "Tu perfil solo permite consultar ${if (carril == SalidaCarril.MANUAL) "(salida manual · módulo 166)" else "(salida con relación · módulo 165)"}.",
                            color = PmmTextSecondary, fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                } else {
                    Text(efectoDe(item, fotos), color = PmmTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(fondo)
                    .pmmPressable(enabled = habilitado, onClick = onConfirmar)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    carril.style().accion,
                    color = if (habilitado) Color.White else Color(0xFF64748B),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
            }
        }
    }
}

private fun efectoDe(item: SalidaPatioItem, fotos: Int): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString {
        fun negrita(texto: String) = withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append(texto) }
        when (item.carrilEnum) {
            SalidaCarril.LISTA -> {
                negrita("Al confirmar: ")
                append("cierra la relación ${item.relno}")
                if (item.cajas.size > 1) append(" y sus ${item.cajas.size} cajas")
                append(" · avisa a ${if (item.clientes.size > 1) "${item.clientes.size} clientes" else item.clientes.firstOrNull() ?: "los clientes"}")
                append(" · guarda $fotos foto${if (fotos == 1) "" else "s"}")
            }
            SalidaCarril.MANUAL -> {
                negrita("Al confirmar: ")
                append("registra la salida como ")
                negrita("MANUAL")
                append(" · no avisa al cliente · queda marcada como sin relación")
            }
            SalidaCarril.CERRAR -> {
                negrita("Al confirmar: ")
                append("cierra el registro de patio con la salida de la relación (${formatoFechaCorta(item.salidaCaseta)}) · no genera una salida nueva")
            }
            SalidaCarril.PROCESO -> {
                negrita("Sin acción: ")
                append("cuando la relación quede autorizada pasará sola a «Listas».")
            }
        }
    }

// ── Diálogo de confirmación ──────────────────────────────────────────────────

@Composable
internal fun DialogoConfirmar(
    item: SalidaPatioItem,
    observacion: String,
    fotos: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val carril = item.carrilEnum
    val estilo = carril.style()
    val titulo = when (carril) {
        SalidaCarril.LISTA -> "Confirmar salida con relación"
        SalidaCarril.MANUAL -> "Confirmar salida MANUAL"
        SalidaCarril.CERRAR -> "Cerrar registro de patio"
        SalidaCarril.PROCESO -> "No disponible"
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Dialog(onDismissRequest = onDismiss) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Motion.DurationMedium)) + scaleIn(
                initialScale = 0.9f,
                animationSpec = tween(Motion.DurationMedium, easing = Motion.EmphasizedDecelerate)
            )
        ) {
            Surface(shape = RoundedCornerShape(24.dp), color = PmmBgSurface) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(estilo.color)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(estilo.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(titulo, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Revisa antes de continuar. Esta acción no se puede deshacer.", color = PmmTextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.height(2.dp))
                        resumenDe(item, observacion, fotos).forEach { linea ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("•", color = estilo.color, fontWeight = FontWeight.Black)
                                Text(linea, color = PmmTextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PmmBgCard)
                                .pmmTap(onClick = onDismiss)
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Cancelar", color = PmmTextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(estilo.color)
                                .pmmPressable(onClick = onConfirm)
                                .padding(vertical = 13.dp),
                            contentAlignment = Alignment.Center
                        ) { Text("Confirmar", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                    }
                }
            }
        }
    }
}

private fun resumenDe(item: SalidaPatioItem, observacion: String, fotos: Int): List<String> = when (item.carrilEnum) {
    SalidaCarril.LISTA -> buildList {
        add(
            if (item.sinCajaEnCaseta) "La relación ${item.relno} pasa a SALIDA. No hay una caja suya registrada en caseta: solo se cierra la relación."
            else if (item.cajas.size > 1) "La relación ${item.relno} pasa a SALIDA con sus ${item.cajas.size} cajas (${item.cajas.joinToString(", ") { it.numCaja }})."
            else "La relación ${item.relno} pasa a SALIDA · caja ${item.cajas.firstOrNull()?.numCaja.orEmpty()}."
        )
        add("Se avisa a: ${item.clientes.joinToString(", ").ifBlank { "los clientes de la relación" }}.")
        add("$fotos foto${if (fotos == 1) "" else "s"}${if (observacion.isNotBlank()) " · observación: «$observacion»" else ""}.")
    }
    SalidaCarril.MANUAL -> listOf(
        "La caja ${etiquetaCaja(item.cajas.firstOrNull()?.numCaja.orEmpty())} (${item.cajas.firstOrNull()?.lineaCaja.orEmpty()}) sale SIN relación.",
        "No se avisa al cliente.",
        if (observacion.isNotBlank()) "Motivo: «$observacion»." else "Sin motivo escrito."
    )
    SalidaCarril.CERRAR -> listOf(
        "Se cierra el registro de patio con la salida de la relación ${item.relno} (${formatoFechaCorta(item.salidaCaseta)}).",
        "No se genera una segunda salida."
    )
    SalidaCarril.PROCESO -> emptyList()
}
