package com.example.pmmovil.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.home.HomeModuleRouteKeys
import com.example.pmmovil.ui.home.tableroFocus
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.SignageType

/** Una fila del Tablero: la estación y el módulo que abre, o `null` si el puesto del usuario no lo tiene. */
data class BoardRow(
    val station: Station,
    val module: ModuleItem?,
    val focus: Boolean
) {
    val allowed: Boolean get() = module != null
}

/** El módulo que abre cada estación de La Línea, entre los que el usuario tiene. */
private fun stationModule(station: Station, modules: List<ModuleItem>): ModuleItem? = when (station) {
    Station.Caseta -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.GATE_HUB }
    Station.Entrada -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.WAREHOUSE_ENTRIES }
    // El módulo de Revisión no tiene clave de ruta propia: lo distingue el que no es el AR.
    Station.Revision -> modules.firstOrNull {
        it.moduleId == MobileModuleIds.REVIEW && it.routeKey != HomeModuleRouteKeys.ENTRY_AR
    }
    Station.Relacion -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.WAREHOUSE_RELATION }
    Station.Salida -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.GATE_RELATION_EXIT }
}

@StringRes
private fun stationDescription(station: Station): Int = when (station) {
    Station.Caseta -> R.string.home_new_station_caseta_desc
    Station.Entrada -> R.string.home_new_station_entrada_desc
    Station.Revision -> R.string.home_new_station_revision_desc
    Station.Relacion -> R.string.home_new_station_relacion_desc
    Station.Salida -> R.string.home_new_station_salida_desc
}

/**
 * El inicio nuevo: el visor de escaneo universal, La Línea con las cinco estaciones y, debajo, los módulos que no son
 * una estación. Por ahora el Tablero es solo navegación; las cifras de cada estación llegan en un paso aparte.
 *
 * @param modules los módulos que el usuario puede abrir (los mismos que pinta el inicio de siempre).
 * @param wide tableta: el visor y La Línea van lado a lado.
 * @param onBlocked se llama con el nombre de la estación cuando el usuario toca una que su puesto no tiene.
 */
@Composable
fun NewHomeContent(
    modules: List<ModuleItem>,
    wide: Boolean,
    onScan: () -> Unit,
    onModuleClick: (ModuleItem) -> Unit,
    onBlocked: (stationName: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = remember(modules) {
        val byStation = Station.entries.associateWith { stationModule(it, modules) }
        val focus = tableroFocus(Station.entries.filter { byStation[it] != null })
        Station.entries.map { BoardRow(it, byStation[it], it == focus) }
    }
    val more = remember(modules, rows) {
        val onTheLine = rows.mapNotNull { it.module }.toSet()
        modules.filter { it !in onTheLine }
    }
    val stationNames = Station.entries.associateWith { stringResource(it.nameRes) }

    val board: @Composable () -> Unit = {
        Column {
            SectionHeader(
                title = stringResource(R.string.home_new_board_title),
                subtitle = stringResource(R.string.home_new_board_subtitle)
            )
            HomeBoard(
                rows = rows,
                onTap = { row ->
                    val module = row.module
                    if (module != null) onModuleClick(module) else onBlocked(stationNames.getValue(row.station))
                }
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (wide) 20.dp else 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HomeScanViewer(onScan = onScan, modifier = Modifier.weight(0.9f))
                Box(Modifier.weight(1.1f)) { board() }
            }
        } else {
            HomeScanViewer(onScan = onScan)
            board()
        }

        if (more.isNotEmpty()) {
            Column {
                SectionHeader(
                    title = stringResource(R.string.home_new_more_title),
                    subtitle = stringResource(R.string.home_new_more_subtitle)
                )
                HomeMoreChips(items = more, onClick = onModuleClick)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = PmmTextPrimary,
            style = SignageType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.5.sp)
        )
        Text(text = subtitle, color = PmmTextMuted, fontSize = 11.sp)
    }
}

// ── El visor de escaneo ──────────────────────────────────────────────────────

/**
 * El visor: toda la tarjeta abre la cámara. El lector de mano ya está escuchando en el inicio, y la leyenda lo dice
 * para que quien trae uno sepa que no hace falta tocar nada.
 */
@Composable
fun HomeScanViewer(onScan: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.home_new_viewer_description)
    Surface(
        onClick = onScan,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = RoundedCornerShape(22.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            Modifier.background(Brush.verticalGradient(listOf(PmmNavyDark, PmmBlueDeep, PmmBlueMid)))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 262.dp)
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_new_viewer_caption).uppercase(),
                        color = Color.White.copy(alpha = 0.78f),
                        style = SignageType.Caption.copy(letterSpacing = 1.4.sp)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4ADE80))
                        )
                        Text(
                            text = stringResource(R.string.home_new_viewer_hardware),
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ViewerReticle()
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ViewerHint(stringResource(R.string.home_new_viewer_hint_caja))
                        ViewerHint(stringResource(R.string.home_new_viewer_hint_relacion))
                        ViewerHint(stringResource(R.string.home_new_viewer_hint_bulto))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(PmmYellow)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = PmmNavyDark,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(R.string.home_new_viewer_cta),
                            color = PmmNavyDark,
                            fontWeight = FontWeight.Black,
                            fontSize = 19.sp,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.home_new_viewer_trigger).uppercase(),
                        color = PmmNavyDark,
                        textAlign = TextAlign.End,
                        style = SignageType.Caption.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerHint(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = Color.White.copy(alpha = 0.85f), style = SignageType.Caption.copy(fontSize = 9.5.sp))
    }
}

/** Cuatro esquinas blancas con un QR tenue al centro: el marco donde se apunta el código. */
@Composable
private fun ViewerReticle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(width = 148.dp, height = 124.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCorners(
                color = Color.White,
                arm = 30.dp.toPx(),
                stroke = 3.dp.toPx(),
                radius = 11.dp.toPx()
            )
        }
        Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(64.dp)
        )
    }
}

private fun DrawScope.drawCorners(color: Color, arm: Float, stroke: Float, radius: Float) {
    val half = stroke / 2f
    val w = size.width
    val h = size.height
    val style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val diameter = 2 * radius

    drawPath(
        Path().apply {
            moveTo(half, arm)
            lineTo(half, half + radius)
            arcTo(Rect(half, half, half + diameter, half + diameter), 180f, 90f, false)
            lineTo(arm, half)
        },
        color,
        style = style
    )
    drawPath(
        Path().apply {
            moveTo(w - arm, half)
            lineTo(w - half - radius, half)
            arcTo(Rect(w - half - diameter, half, w - half, half + diameter), 270f, 90f, false)
            lineTo(w - half, arm)
        },
        color,
        style = style
    )
    drawPath(
        Path().apply {
            moveTo(w - half, h - arm)
            lineTo(w - half, h - half - radius)
            arcTo(Rect(w - half - diameter, h - half - diameter, w - half, h - half), 0f, 90f, false)
            lineTo(w - arm, h - half)
        },
        color,
        style = style
    )
    drawPath(
        Path().apply {
            moveTo(arm, h - half)
            lineTo(half + radius, h - half)
            arcTo(Rect(half, h - half - diameter, half + diameter, h - half), 90f, 90f, false)
            lineTo(half, h - arm)
        },
        color,
        style = style
    )
}

// ── La Línea ─────────────────────────────────────────────────────────────────

/**
 * Las cinco estaciones unidas por una línea, como un tablero de metro. La del puesto del usuario va resaltada en azul
 * marino y las que su puesto no tiene van atenuadas, con el nodo punteado, pero siguen ahí: se ve el recorrido
 * completo aunque cada quien solo abra su tramo.
 */
@Composable
fun HomeBoard(
    rows: List<BoardRow>,
    onTap: (BoardRow) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // El hilo pasa por el centro de los nodos (columna de 44 dp) desde el primero hasta el último.
                val x = 22.dp.toPx()
                drawLine(
                    color = PmmNavyDark.copy(alpha = 0.28f),
                    start = Offset(x, 30.dp.toPx()),
                    end = Offset(x, size.height - 30.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
    ) {
        rows.forEachIndexed { index, row ->
            BoardRowItem(number = index + 1, row = row, onClick = { onTap(row) })
        }
    }
}

@Composable
private fun BoardRowItem(number: Int, row: BoardRow, onClick: () -> Unit) {
    val name = stringResource(row.station.nameRes)
    val detail = if (row.allowed) {
        stringResource(stationDescription(row.station))
    } else {
        stringResource(R.string.home_new_station_blocked_state)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (row.focus) Modifier.background(PmmNavyDark) else Modifier)
            .alpha(if (row.allowed) 1f else 0.55f)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "$name. $detail" }
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
            BoardNode(number = number, row = row)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 6.dp)
        ) {
            Text(
                text = name,
                color = if (row.focus) Color.White else PmmTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = 16.sp
            )
            Text(
                text = detail,
                color = if (row.focus) Color.White.copy(alpha = 0.82f) else PmmTextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (row.focus) Color.White else PmmTextMuted
        )
    }
}

/** Círculo blanco con borde marino y el número de la estación; punteado y gris si el puesto no la tiene. */
@Composable
private fun BoardNode(number: Int, row: BoardRow) {
    val ring = when {
        row.focus -> Color.White
        row.allowed -> PmmNavyDark
        else -> PmmTextMuted
    }
    val numberColor = if (row.allowed) PmmNavyDark else PmmTextMuted
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = 3.dp.toPx()
            drawCircle(Color.White, radius = size.minDimension / 2)
            drawCircle(
                color = ring,
                radius = size.minDimension / 2 - stroke / 2,
                style = Stroke(
                    width = stroke,
                    pathEffect = if (row.allowed) null else PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                )
            )
        }
        Text(
            text = number.toString(),
            color = numberColor,
            style = SignageType.StationNumber.copy(fontSize = 14.sp, lineHeight = 14.sp)
        )
    }
}

// ── Más ──────────────────────────────────────────────────────────────────────

@Composable
fun HomeMoreChips(items: List<ModuleItem>, onClick: (ModuleItem) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                onClick = { onClick(item) },
                shape = RoundedCornerShape(50),
                color = Color.White,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .padding(start = 12.dp, end = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        item.icon,
                        contentDescription = null,
                        tint = PmmBlueDeep,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(item.titleRes),
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
