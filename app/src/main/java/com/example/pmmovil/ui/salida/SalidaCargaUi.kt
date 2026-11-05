package com.example.pmmovil.ui.salida

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.ui.revision.BarcodeCameraPanel
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmRedDark
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.google.mlkit.vision.barcode.common.Barcode

/*
 * Piezas de la pantalla Cargar mercancía rediseñada para los lectores compactos (Honeywell CT70 y CT60) y la tableta.
 *
 * Reglas de lectura, pedidas por Eduardo el 5-nov-2026: letra grande y marcada (nada de 10 u 11 sp), y ningún texto que
 * se parta feo en una pantalla de 320 a 360 dp. Por eso cada texto declara cuántas líneas admite y qué hace si no cabe
 * (puntos suspensivos o segunda línea), los textos junto a un número ceden el ancho con `weight`, y las etiquetas de los
 * botones se acortan en pantallas angostas en lugar de partirse.
 */

private val Mono = FontFamily.Monospace
private val VerdeFondo = Color(0xFFE7F7ED)
private val RojoFondo = Color(0xFFFDECEB)
private val AmbarFondo = Color(0xFFFFF6E0)
private val AmbarTexto = Color(0xFF92400E)

/** Cuánto ocupa la cámara flotante sobre la pantalla de carga. */
internal enum class CamaraTamano { CHICA, GRANDE }

/** Qué lista de bultos se ve. */
internal enum class PestanaBultos { PENDIENTES, ESCANEADOS }

internal data class MenuAccion(val texto: String, val peligro: Boolean = false, val onClick: () -> Unit)

/** Pantalla angosta (CT70 y la mayoría de los teléfonos): las etiquetas largas de los botones se acortan. */
@Composable
internal fun esPantallaAngosta(): Boolean = LocalConfiguration.current.screenWidthDp < 360

// ═══════════════════════════════════════════════════════════════════════════
// Barra superior
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaBarraSuperior(
    titulo: String,
    subtitulo: String,
    /** El título es un número de relación o de entrada: se dibuja en monoespaciada para leer bien cada dígito. */
    tituloEsCodigo: Boolean,
    camaraActiva: Boolean,
    menuAbierto: Boolean,
    acciones: List<MenuAccion>,
    onBack: () -> Unit,
    onCamara: () -> Unit,
    onMenu: (Boolean) -> Unit
) {
    Surface(color = PmmBlueDeep, contentColor = Color.White) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    text = titulo,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = if (tituloEsCodigo) Mono else FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitulo,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onCamara,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (camaraActiva) Color.White.copy(alpha = 0.24f) else Color.Transparent)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Cámara", tint = Color.White)
            }
            if (acciones.isNotEmpty()) {
                Box {
                    IconButton(onClick = { onMenu(true) }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones", tint = Color.White)
                    }
                    DropdownMenu(expanded = menuAbierto, onDismissRequest = { onMenu(false) }) {
                        acciones.forEach { accion ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        accion.texto,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (accion.peligro) PmmRed else PmmTextPrimary
                                    )
                                },
                                onClick = {
                                    onMenu(false)
                                    accion.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Reposo: esperando la relación
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaReposo(
    camaraActiva: Boolean,
    onUsarCamara: () -> Unit,
    camara: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (camaraActiva) camara()

        Surface(color = VerdeFondo, shape = RoundedCornerShape(999.dp)) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(PmmGreen))
                Text(
                    text = if (camaraActiva) "Cámara lista" else "Lector listo",
                    color = PmmGreenDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
        Text(
            text = "Escanea la relación",
            color = PmmTextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 31.sp
        )
        Text(
            text = "Aprieta el gatillo del lector. La orden de carga y la requisición sirven; para una entrada directa, escanea su carpeta.",
            color = PmmTextSecondary,
            fontSize = 16.sp,
            lineHeight = 23.sp
        )
        if (!camaraActiva) {
            OutlinedButton(
                onClick = onUsarCamara,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, PmmBlueDeep)
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, tint = PmmBlueDeep)
                Spacer(Modifier.width(10.dp))
                Text("Usar la cámara", color = PmmBlueDeep, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Relación y avance
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaResumen(state: SalidaUiState) {
    val faltan = state.pendientes
    val completa = state.todoEscaneado
    // La relación, el cliente y la caja ya van en la barra de arriba: aquí solo el avance. En un lector de 640 dp de alto
    // cada renglón que se repite es un bulto menos a la vista. El estado de la mercancía solo se muestra si no es COMPLETA.
    val anomalia = state.relacion?.estatus?.takeIf { it.isNotBlank() && it != "COMPLETA" }

    Surface(color = PmmBgSurface, shadowElevation = 2.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${state.escaneados}", color = PmmTextPrimary, fontSize = 36.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text("/${state.total}", color = PmmTextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(bottom = 4.dp))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFDFE5EF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.progreso.coerceIn(0f, 1f))
                            .fillMaxSize()
                            .clip(RoundedCornerShape(6.dp))
                            .background(PmmGreen)
                    )
                }
                Text(
                    text = if (completa) "Completa" else "Faltan $faltan",
                    color = if (completa) PmmGreenDark else PmmAmber,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
            }
            if (anomalia != null) {
                Chip(texto = anomalia, fondo = AmbarFondo, color = AmbarTexto)
            }
        }
    }
}

@Composable
private fun Chip(texto: String, fondo: Color, color: Color) {
    Surface(color = fondo, shape = RoundedCornerShape(999.dp)) {
        Text(
            text = texto,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Último escaneo
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaUltimoEscaneoCard(state: SalidaUiState, ahoraMillis: Long, modifier: Modifier = Modifier) {
    // Paso previo de caseta: antes de cualquier bulto hay que leer el QR de la caseta.
    if (state.requiereCasetaQr && state.captureStep == SalidaCaptureStep.CASETA_QR) {
        TarjetaEscaneo(
            modifier = modifier,
            fondo = AmbarFondo,
            borde = PmmAmber,
            icono = null,
            etiqueta = "PRIMER PASO",
            etiquetaColor = AmbarTexto,
            titulo = "Escanea el QR de caseta",
            detalle = "Después tienes 10 segundos para escanear el bulto.",
            numero = null,
            detalleEsCodigo = false
        )
        return
    }

    val ultimo = state.ultimoEscaneo
    if (ultimo == null && state.historial.isNotEmpty()) {
        // Se reanudó una carga: aún no se lee nada en esta pantalla, pero ya hay bultos. Se muestra el último que quedó.
        val ultimoGuardado = state.historial.last()
        val texto = textoDeEtiqueta(ultimoGuardado.etiqueta)
        TarjetaEscaneo(
            modifier = modifier,
            fondo = VerdeFondo,
            borde = PmmGreen,
            icono = true,
            etiqueta = "ÚLTIMO ESCANEO",
            etiquetaColor = PmmGreenDark,
            titulo = texto.titulo,
            detalle = texto.detalle,
            numero = ultimoGuardado.numero to state.total
        )
        return
    }
    if (ultimo == null) {
        TarjetaEscaneo(
            modifier = modifier,
            fondo = Color.Transparent,
            borde = PmmNavyBorder,
            icono = null,
            etiqueta = "ÚLTIMO ESCANEO",
            etiquetaColor = PmmTextSecondary,
            titulo = "Esperando el primer bulto",
            detalle = "",
            numero = null,
            detalleEsCodigo = false
        )
        return
    }

    val texto = textoDeCodigo(ultimo.texto)
    val hace = hace(ahoraMillis - ultimo.atMillis)
    if (!ultimo.aceptado) {
        TarjetaEscaneo(
            modifier = modifier,
            fondo = RojoFondo,
            borde = PmmRed,
            icono = false,
            etiqueta = "NO PERTENECE · NO SE CUENTA",
            etiquetaColor = PmmRedDark,
            titulo = ultimo.motivo ?: "No pertenece",
            detalle = "${texto.titulo} · $hace",
            numero = null
        )
        return
    }

    val numero = state.historial.firstOrNull { it.etiqueta.textoEtiqueta == ultimo.texto }?.numero
    val etiqueta = state.etiquetas.firstOrNull { it.textoEtiqueta == ultimo.texto }?.let(::textoDeEtiqueta) ?: texto
    val segundos = if (state.captureStep == SalidaCaptureStep.BULTO && state.casetaQrSecondsRemaining > 0) {
        " · quedan ${state.casetaQrSecondsRemaining} s"
    } else {
        ""
    }
    TarjetaEscaneo(
        modifier = modifier,
        fondo = VerdeFondo,
        borde = PmmGreen,
        icono = true,
        // El «hace N s» va en el rótulo de arriba, que es de una sola línea: abajo, junto a la referencia, se partía en
        // dos líneas («hace 1» / «s») en una pantalla de 360 dp.
        etiqueta = (if (ultimo.yaEscaneado) "YA ESTABA ESCANEADO" else "ÚLTIMO ESCANEO") + " · " + hace + segundos,
        etiquetaColor = PmmGreenDark,
        titulo = etiqueta.titulo,
        detalle = etiqueta.detalle,
        numero = numero?.let { it to state.total }
    )
}

/** icono: `true` palomita, `false` equis, `null` sin icono. */
@Composable
private fun TarjetaEscaneo(
    modifier: Modifier,
    fondo: Color,
    borde: Color,
    icono: Boolean?,
    etiqueta: String,
    etiquetaColor: Color,
    titulo: String,
    detalle: String,
    numero: Pair<Int, Int>?,
    detalleEsCodigo: Boolean = true
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = fondo,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, borde)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icono != null) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(if (icono) PmmGreen else PmmRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (icono) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(etiqueta, color = etiquetaColor, fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(titulo, color = PmmTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
                if (detalle.isNotBlank()) {
                    Text(
                        detalle,
                        color = PmmTextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = if (detalleEsCodigo) Mono else FontFamily.Default,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (numero != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("#${numero.first}", color = PmmTextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    Text("de ${numero.second}", color = PmmTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

private fun hace(milisegundos: Long): String {
    val segundos = (milisegundos / 1_000L).coerceAtLeast(0L)
    // Espacios que no se parten: «hace 17 s» siempre queda junto en una línea.
    return if (segundos < 60) "hace $segundos s" else "hace ${segundos / 60} min"
}

// ═══════════════════════════════════════════════════════════════════════════
// Pestañas y lista
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaPestanas(
    pestana: PestanaBultos,
    pendientes: Int,
    escaneados: Int,
    onSelect: (PestanaBultos) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pestana("Pendientes", pendientes, pestana == PestanaBultos.PENDIENTES, { onSelect(PestanaBultos.PENDIENTES) }, Modifier.weight(1f))
        Pestana("Escaneados", escaneados, pestana == PestanaBultos.ESCANEADOS, { onSelect(PestanaBultos.ESCANEADOS) }, Modifier.weight(1f))
    }
}

@Composable
private fun Pestana(texto: String, cuenta: Int, activa: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier.height(48.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (activa) Color(0xFFE8EFFC) else PmmBgSurface,
        border = BorderStroke(2.dp, if (activa) PmmBlueDeep else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = texto,
                color = if (activa) PmmBlueDeep else PmmTextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$cuenta",
                color = if (activa) PmmBlueDeep else PmmTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
internal fun SalidaListaBultos(
    pestana: PestanaBultos,
    state: SalidaUiState,
    masRecientesPrimero: Boolean,
    onCambiarOrden: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (pestana == PestanaBultos.PENDIENTES) {
            val faltan = state.porEscanear
            if (faltan.isEmpty()) {
                item { VacioLista(if (state.total == 0) "Esta relación no trae bultos." else "No falta ningún bulto.") }
            }
            items(faltan, key = { it.textoEtiqueta }) { etiqueta ->
                val texto = textoDeEtiqueta(etiqueta)
                BultoFila(texto.titulo, texto.detalle, escaneado = false, destacado = false, numero = null, hora = null)
            }
        } else {
            val historial = state.historial
            val ultimo = state.ultimoEscaneo?.takeIf { it.aceptado }?.texto
            if (historial.isEmpty()) {
                item { VacioLista("Todavía no hay bultos escaneados.") }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (masRecientesPrimero) "Del más reciente al más antiguo" else "En el orden en que se escanearon",
                            color = PmmTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            modifier = Modifier.clickable(onClick = onCambiarOrden),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE8EFFC)
                        ) {
                            Text(
                                text = "Invertir",
                                color = PmmBlueDeep,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
            val ordenado = if (masRecientesPrimero) historial.asReversed() else historial
            items(ordenado, key = { it.etiqueta.textoEtiqueta }) { escaneado ->
                val texto = textoDeEtiqueta(escaneado.etiqueta)
                BultoFila(
                    titulo = texto.titulo,
                    detalle = texto.detalle,
                    escaneado = true,
                    destacado = escaneado.etiqueta.textoEtiqueta == ultimo,
                    numero = escaneado.numero,
                    hora = escaneado.hora
                )
            }
        }
    }
}

@Composable
private fun VacioLista(texto: String) {
    Text(
        text = texto,
        color = PmmTextSecondary,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 8.dp),
        lineHeight = 22.sp
    )
}

@Composable
internal fun BultoFila(
    titulo: String,
    detalle: String,
    escaneado: Boolean,
    destacado: Boolean,
    numero: Int?,
    hora: String?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (destacado) VerdeFondo else PmmBgSurface,
        border = BorderStroke(if (destacado) 2.dp else 1.dp, if (destacado) PmmGreen else PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).defaultMinSize(minHeight = 44.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (escaneado) {
                Box(
                    modifier = Modifier.size(28.dp).clip(CircleShape).background(PmmGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Escaneado", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(modifier = Modifier.size(28.dp).border(3.dp, PmmAmber, CircleShape))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(titulo, color = PmmTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 22.sp)
                if (detalle.isNotBlank()) {
                    Text(detalle, color = PmmTextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (numero != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("#$numero", color = PmmGreenDark, fontSize = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, softWrap = false)
                    if (hora != null) {
                        Text(hora, color = PmmTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Acciones
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaBarraAcciones(
    state: SalidaUiState,
    onPausar: () -> Unit,
    onFinalizar: () -> Unit
) {
    val angosta = esPantallaAngosta()
    // Sin bultos que contar (diferencia de bultos o sin etiquetas) el cierre pasa por la autorización: se deja tocar.
    val puedeFinalizar = (state.todoEscaneado || state.total == 0) && !state.isProcessingAction
    val etiqueta = when {
        state.todoEscaneado || state.total == 0 -> if (angosta) "Finalizar" else "Finalizar carga"
        angosta -> "Faltan ${state.pendientes}"
        else -> "Finalizar · faltan ${state.pendientes}"
    }
    Surface(color = PmmBgSurface, shadowElevation = 10.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onPausar,
                enabled = !state.isProcessingAction,
                modifier = Modifier.weight(0.42f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(2.dp, PmmBlueDeep),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Filled.Pause, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Pausar", color = PmmBlueDeep, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            Button(
                onClick = onFinalizar,
                enabled = puedeFinalizar,
                modifier = Modifier.weight(0.58f).height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmYellow,
                    contentColor = PmmNavyDark,
                    disabledContainerColor = Color(0xFFD7DDE8),
                    disabledContentColor = Color(0xFF556078)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                if (state.isProcessingAction) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = PmmNavyDark)
                } else {
                    Text(etiqueta, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Cámara
// ═══════════════════════════════════════════════════════════════════════════

/** La cámara como bloque dentro de la pantalla (reposo y tableta): no tapa nada. */
@Composable
internal fun SalidaCamaraEnLinea(
    ultimo: SalidaUltimoEscaneo?,
    alto: androidx.compose.ui.unit.Dp,
    onEscaneo: (String) -> Unit,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(alto)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070B14))
    ) {
        BarcodeCameraPanel(
            onBarcodeScanned = onEscaneo,
            barcodeFormats = Barcode.FORMAT_ALL_FORMATS,
            subtitle = "",
            modifier = Modifier.fillMaxSize()
        )
        // Arriba a la izquierda: la lectura inteligente pone su botón de linterna arriba a la derecha.
        BotonCamara(Icons.Filled.Close, "Cerrar cámara", onCerrar, Modifier.align(Alignment.TopStart).padding(8.dp))
        ResultadoEnCamara(ultimo)
    }
}

/**
 * La cámara flotando sobre la lista: chica (esquina, para no tapar el último escaneo) o grande (arriba, con el resultado
 * del último escaneo encima del video para verlo sin soltar la cámara).
 */
@Composable
internal fun BoxScope.SalidaCamaraFlotante(
    tamano: CamaraTamano,
    ultimo: SalidaUltimoEscaneo?,
    onEscaneo: (String) -> Unit,
    onTamano: () -> Unit,
    onCerrar: () -> Unit
) {
    val modificador = when (tamano) {
        CamaraTamano.CHICA -> Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 12.dp)
            .size(width = 160.dp, height = 120.dp)
        CamaraTamano.GRANDE -> Modifier
            .align(Alignment.TopCenter)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(220.dp)
    }
    Box(
        modifier = modificador
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF070B14))
            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
    ) {
        BarcodeCameraPanel(
            onBarcodeScanned = onEscaneo,
            barcodeFormats = Barcode.FORMAT_ALL_FORMATS,
            subtitle = "",
            modifier = Modifier.fillMaxSize()
        )
        Row(modifier = Modifier.align(Alignment.TopStart).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BotonCamara(
                if (tamano == CamaraTamano.CHICA) Icons.Filled.FitScreen else Icons.Filled.ZoomInMap,
                if (tamano == CamaraTamano.CHICA) "Ampliar cámara" else "Reducir cámara",
                onTamano,
                Modifier
            )
            BotonCamara(Icons.Filled.Close, "Cerrar cámara", onCerrar, Modifier)
        }
        if (tamano == CamaraTamano.GRANDE) ResultadoEnCamara(ultimo)
    }
}

@Composable
private fun BotonCamara(
    icono: androidx.compose.ui.graphics.vector.ImageVector,
    descripcion: String,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icono, contentDescription = descripcion, tint = Color.White, modifier = Modifier.size(22.dp))
    }
}

/** El último escaneo encima del video, para verlo sin quitar la vista de la cámara. */
@Composable
private fun BoxScope.ResultadoEnCamara(ultimo: SalidaUltimoEscaneo?) {
    if (ultimo == null) return
    val texto = textoDeCodigo(ultimo.texto)
    Surface(
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(10.dp),
        color = if (ultimo.aceptado) PmmGreenDark.copy(alpha = 0.95f) else PmmRed.copy(alpha = 0.96f)
    ) {
        Text(
            text = if (ultimo.aceptado) "✓ ${texto.titulo}" else "✗ ${ultimo.motivo ?: "No pertenece"}",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Descarga (re-escaneo de bultos que ya no pertenecen)
// ═══════════════════════════════════════════════════════════════════════════

@Composable
internal fun SalidaDescarga(
    state: SalidaUiState,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Surface(color = PmmRedDark) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${state.descargaTotal - state.descargados}", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black, fontFamily = Mono, maxLines = 1)
                        Text(
                            "POR BAJAR Y ESCANEAR",
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(bottom = 6.dp)
                        )
                    }
                    Text(
                        text = "${state.relacion?.relno.orEmpty()} · relación pausada",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = Mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val pendientes = state.etiquetasDescarga.filterNot { it.escaneado }
            val hechas = state.etiquetasDescarga.filter { it.escaneado }
            items(pendientes + hechas, key = { it.textoEtiqueta }) { etiqueta: SalidaEtiqueta ->
                val texto = textoDeEtiqueta(etiqueta)
                BultoFila(texto.titulo, texto.detalle, escaneado = etiqueta.escaneado, destacado = false, numero = null, hora = null)
            }
        }
    }
}
