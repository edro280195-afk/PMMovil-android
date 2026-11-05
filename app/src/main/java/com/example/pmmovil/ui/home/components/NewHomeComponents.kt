package com.example.pmmovil.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.ui.components.FlipCounter
import com.example.pmmovil.ui.components.SignalChip
import com.example.pmmovil.ui.components.Skeleton
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.home.BoardFigure
import com.example.pmmovil.ui.home.HomeForm
import com.example.pmmovil.ui.home.HomeLastScanUi
import com.example.pmmovil.ui.home.HomeModuleRouteKeys
import com.example.pmmovil.ui.home.HomeSummaryUiState
import com.example.pmmovil.ui.home.stationUnit
import com.example.pmmovil.ui.home.tableroFocus
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.SignageType

/** Cómo va la cifra de una fila: todavía no llega, llegó, o no se pudo calcular. */
enum class FigureState { Loading, Ready, Unavailable }

/**
 * Una fila del Tablero: la estación y el módulo que abre, o `null` si el puesto del usuario no lo tiene, más su cifra.
 */
data class BoardRow(
    val station: Station,
    val module: ModuleItem?,
    val focus: Boolean,
    val figure: BoardFigure? = null,
    val figureState: FigureState = FigureState.Loading
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
    Station.Carga -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.WAREHOUSE_LOADS }
    Station.Relacion -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.WAREHOUSE_RELATION }
    Station.Salida -> modules.firstOrNull { it.routeKey == HomeModuleRouteKeys.GATE_RELATION_EXIT }
}

@StringRes
private fun stationDescription(station: Station): Int = when (station) {
    Station.Caseta -> R.string.home_new_station_caseta_desc
    Station.Entrada -> R.string.home_new_station_entrada_desc
    Station.Revision -> R.string.home_new_station_revision_desc
    Station.Carga -> R.string.home_new_station_carga_desc
    Station.Relacion -> R.string.home_new_station_relacion_desc
    Station.Salida -> R.string.home_new_station_salida_desc
}

/**
 * El inicio nuevo: el visor de escaneo universal, La Línea con las seis estaciones, cada una con su cifra y su caso más
 * fuerte, y debajo los módulos que no son una estación.
 *
 * @param modules los módulos que el usuario puede abrir (los mismos que pinta el inicio de siempre).
 * @param summary las cifras del Tablero. Llegan aparte de los módulos: mientras no llegan, cada fila pinta un esqueleto.
 * @param form el aparato. El teléfono conserva sus medidas (de pie, uno bajo otro; acostado, lado a lado). La tableta
 * llena el alto que le dan: el llamador le pasa un `modifier` con alto definido (por ejemplo `weight(1f)`) y ella
 * reparte ese alto entre el visor, La Línea y «Más», con medidas propias.
 * @param onBlocked se llama con el nombre de la estación cuando el usuario toca una que su puesto no tiene.
 */
@Composable
fun NewHomeContent(
    modules: List<ModuleItem>,
    form: HomeForm,
    onScan: () -> Unit,
    onModuleClick: (ModuleItem) -> Unit,
    onBlocked: (stationName: String) -> Unit,
    modifier: Modifier = Modifier,
    summary: HomeSummaryUiState = HomeSummaryUiState(),
    lastScan: HomeLastScanUi? = null,
    onOpenLastScan: (code: String) -> Unit = {}
) {
    val rows = remember(modules, summary) {
        val byStation = Station.entries.associateWith { stationModule(it, modules) }
        val focus = tableroFocus(Station.entries.filter { byStation[it] != null })
        Station.entries.map { station ->
            val figure = summary.figures[station]
            BoardRow(
                station = station,
                module = byStation[station],
                focus = station == focus,
                figure = figure,
                figureState = when {
                    figure != null -> FigureState.Ready
                    summary.loading -> FigureState.Loading
                    else -> FigureState.Unavailable
                }
            )
        }
    }
    val more = remember(modules, rows) {
        val onTheLine = rows.mapNotNull { it.module }.toSet()
        modules.filter { it !in onTheLine }
    }
    val stationNames = Station.entries.associateWith { stringResource(it.nameRes) }
    val tablet = form.isTablet
    val dense = form.dense

    // En tableta La Línea se estira con el alto que le toque (sus filas se reparten parejo); en teléfono mide lo suyo.
    val board: @Composable (Modifier) -> Unit = { boardModifier ->
        Column(modifier = boardModifier) {
            SectionHeader(
                title = stringResource(R.string.home_new_board_title),
                subtitle = stringResource(R.string.home_new_board_subtitle),
                large = tablet,
                dense = dense
            )
            HomeBoard(
                rows = rows,
                tablet = tablet,
                dense = dense,
                modifier = if (tablet) Modifier.weight(1f) else Modifier,
                onTap = { row ->
                    val module = row.module
                    if (module != null) onModuleClick(module) else onBlocked(stationNames.getValue(row.station))
                }
            )
        }
    }

    // Bajo el visor, como en el mockup: es lo que se quiere a la mano justo después de escanear.
    val lastScanPill: @Composable () -> Unit = {
        lastScan?.let {
            HomeLastScanPill(state = it, large = tablet, dense = dense, onClick = { onOpenLastScan(it.scan.code) })
        }
    }

    // Los módulos que no son estación: píldoras que se desplazan en teléfono, mosaicos que llenan el ancho en tableta.
    val moreSection: @Composable () -> Unit = {
        if (more.isNotEmpty()) {
            Column {
                SectionHeader(
                    title = stringResource(R.string.home_new_more_title),
                    subtitle = stringResource(R.string.home_new_more_subtitle),
                    large = tablet,
                    dense = dense
                )
                if (tablet) {
                    HomeMoreTiles(items = more, onClick = onModuleClick, dense = dense)
                } else {
                    HomeMoreChips(items = more, onClick = onModuleClick)
                }
            }
        }
    }

    when {
        form.isTabletLandscape -> Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = if (dense) 20.dp else 24.dp, vertical = if (dense) 10.dp else 16.dp),
            horizontalArrangement = Arrangement.spacedBy(if (dense) 20.dp else 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 14.dp)
            ) {
                HomeScanViewer(onScan = onScan, form = form, modifier = Modifier.weight(1f))
                lastScanPill()
            }
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 18.dp)
            ) {
                board(Modifier.weight(1f))
                moreSection()
            }
        }

        form.isTabletPortrait -> Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = if (dense) 20.dp else 24.dp, vertical = if (dense) 12.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 14.dp)) {
                HomeScanViewer(onScan = onScan, form = form)
                lastScanPill()
            }
            board(Modifier.weight(1f))
            moreSection()
        }

        else -> {
            val wide = form == HomeForm.Side
            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) 20.dp else 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Column(modifier = Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HomeScanViewer(onScan = onScan)
                            lastScanPill()
                        }
                        Box(Modifier.weight(1.1f)) { board(Modifier) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeScanViewer(onScan = onScan)
                        lastScanPill()
                    }
                    board(Modifier)
                }

                moreSection()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String, large: Boolean = false, dense: Boolean = false) {
    val fontSize = when {
        dense -> 12.sp
        large -> 13.sp
        else -> 11.sp
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (dense) 6.dp else if (large) 10.dp else 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            color = PmmTextPrimary,
            style = if (large) {
                SignageType.Caption.copy(fontSize = fontSize, lineHeight = 16.sp, letterSpacing = 1.8.sp)
            } else {
                SignageType.Caption.copy(fontSize = 11.sp, letterSpacing = 1.5.sp)
            }
        )
        Text(text = subtitle, color = PmmTextSecondary, fontSize = fontSize)
    }
}

// ── El visor de escaneo ──────────────────────────────────────────────────────

/**
 * El visor: toda la tarjeta abre la cámara. El lector de mano ya está escuchando en el inicio, y la leyenda lo dice
 * para que quien trae uno sepa que no hace falta tocar nada.
 */
@Composable
fun HomeScanViewer(onScan: () -> Unit, modifier: Modifier = Modifier, form: HomeForm = HomeForm.Compact) {
    val description = stringResource(R.string.home_new_viewer_description)
    Surface(
        onClick = onScan,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        shape = RoundedCornerShape(if (form.isTablet) 28.dp else 22.dp),
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(
            Modifier
                // Acostada, la tableta le da al visor todo el alto de su columna; de pie, mide lo que su contenido pida.
                .then(if (form.isTabletLandscape) Modifier.fillMaxSize() else Modifier)
                .background(Brush.verticalGradient(listOf(PmmNavyDark, PmmBlueDeep, PmmBlueMid)))
        ) {
            when {
                form.isTabletLandscape -> ViewerTall(dense = form.dense)
                form.isTabletPortrait -> ViewerWide(dense = form.dense)
                else -> ViewerPhone()
            }
        }
    }
}

/** El visor del teléfono: rótulos arriba, marco al centro y el botón abajo. */
@Composable
private fun ViewerPhone() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 262.dp)
            .padding(14.dp),
        // Con SpaceBetween los bloques se pegaban entre sí en cuanto el contenido llenaba el alto mínimo
        // (rótulos contra el botón, marco contra el encabezado); con separación fija siempre respiran.
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ViewerCaption(large = false)

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ViewerReticle()
            ViewerHints(large = false)
        }

        ViewerCta(large = false)
    }
}

/**
 * El visor de la tableta acostada: una columna alta. El marco crece con el alto que le sobra entre los rótulos y el
 * botón, sin pasarse de lo que cabe a lo ancho, para que el visor se vea lleno y no un teléfono flotando en la tarjeta.
 */
@Composable
private fun ViewerTall(dense: Boolean) {
    // Con menos de 400 dp de tarjeta el botón no alcanza para su rótulo «o jala el gatillo»: se queda solo «Escanear».
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val showTrigger = maxWidth >= 400.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (dense) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 16.dp)
        ) {
            ViewerCaption(large = true, dense = dense)

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                // 52 dp: las pastillas y el espacio que llevan bajo el marco.
                val scale = minOf((maxHeight - 52.dp) / RETICLE_HEIGHT, (maxWidth - 24.dp) / RETICLE_WIDTH, 2f)
                    .coerceAtLeast(1f)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 14.dp)
                ) {
                    ViewerReticle(scale = scale)
                    ViewerHints(large = true, dense = dense)
                }
            }

            ViewerCta(large = true, dense = dense, showTrigger = showTrigger)
        }
    }
}

/** El visor de la tableta de pie: una banda ancha, con el marco a la izquierda y el botón a la derecha. */
@Composable
private fun ViewerWide(dense: Boolean) {
    // Con menos de 670 dp de tarjeta el botón, al lado del marco, no alcanza para «o jala el gatillo».
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val showTrigger = maxWidth >= 670.dp
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (dense) 16.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 18.dp)
        ) {
            ViewerCaption(large = true, dense = dense)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (dense) 24.dp else 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ViewerReticle(scale = if (dense) 0.9f else 1.4f)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (dense) 12.dp else 18.dp)
                ) {
                    ViewerHints(large = true, dense = dense)
                    ViewerCta(large = true, dense = dense, showTrigger = showTrigger)
                }
            }
        }
    }
}

@Composable
private fun ViewerCaption(large: Boolean, dense: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_new_viewer_caption).uppercase(),
            color = Color.White.copy(alpha = 0.78f),
            style = if (large) {
                SignageType.Caption.copy(fontSize = if (dense) 12.sp else 13.sp, lineHeight = 16.sp, letterSpacing = 1.6.sp)
            } else {
                SignageType.Caption.copy(letterSpacing = 1.4.sp)
            }
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(if (large) 10.dp else 7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF4ADE80))
            )
            Text(
                text = stringResource(R.string.home_new_viewer_hardware),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = if (large) (if (dense) 13.sp else 14.sp) else 11.sp,
                modifier = Modifier.padding(start = if (large) 8.dp else 6.dp)
            )
        }
    }
}

@Composable
private fun ViewerHints(large: Boolean, dense: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(if (large) 8.dp else 6.dp)) {
        ViewerHint(stringResource(R.string.home_new_viewer_hint_caja), large, dense)
        ViewerHint(stringResource(R.string.home_new_viewer_hint_relacion), large, dense)
        ViewerHint(stringResource(R.string.home_new_viewer_hint_bulto), large, dense)
    }
}

@Composable
private fun ViewerHint(text: String, large: Boolean, dense: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(if (large) 8.dp else 5.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(
                horizontal = if (large) (if (dense) 9.dp else 11.dp) else 7.dp,
                vertical = if (large) (if (dense) 5.dp else 6.dp) else 3.dp
            )
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.85f),
            style = if (large) {
                SignageType.Caption.copy(fontSize = if (dense) 11.sp else 12.sp, lineHeight = 14.sp)
            } else {
                SignageType.Caption.copy(fontSize = 9.5.sp)
            }
        )
    }
}

/** El botón amarillo «Escanear». Toda la tarjeta es el botón: este solo es lo que se ve. */
@Composable
private fun ViewerCta(large: Boolean, dense: Boolean = false, showTrigger: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (large) (if (dense) 60.dp else 76.dp) else 58.dp)
            .clip(RoundedCornerShape(if (large) 20.dp else 15.dp))
            .background(PmmYellow)
            .padding(horizontal = if (large) (if (dense) 18.dp else 24.dp) else 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = PmmNavyDark,
                modifier = Modifier.size(if (large) (if (dense) 28.dp else 34.dp) else 24.dp)
            )
            Text(
                text = stringResource(R.string.home_new_viewer_cta),
                color = PmmNavyDark,
                fontWeight = FontWeight.Black,
                fontSize = if (large) (if (dense) 22.sp else 26.sp) else 19.sp,
                lineHeight = if (large) (if (dense) 28.sp else 32.sp) else TextUnit.Unspecified,
                modifier = Modifier.padding(start = if (large) 14.dp else 10.dp)
            )
        }
        if (showTrigger) {
            Text(
                text = stringResource(R.string.home_new_viewer_trigger).uppercase(),
                color = PmmNavyDark,
                textAlign = TextAlign.End,
                style = if (large) {
                    SignageType.Caption.copy(fontSize = 12.sp, lineHeight = 15.sp, letterSpacing = 0.8.sp)
                } else {
                    SignageType.Caption.copy(fontSize = 10.sp, letterSpacing = 0.6.sp)
                },
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

private val RETICLE_WIDTH = 148.dp
private val RETICLE_HEIGHT = 124.dp

/**
 * Cuatro esquinas blancas con un QR tenue al centro: el marco donde se apunta el código.
 *
 * @param scale 1 es el tamaño del teléfono; la tableta lo agranda y el trazo engorda a la mitad de ritmo, para que el
 * marco crezca sin volverse una cruz de brocha gorda.
 */
@Composable
private fun ViewerReticle(modifier: Modifier = Modifier, scale: Float = 1f) {
    Box(
        modifier = modifier.size(width = RETICLE_WIDTH * scale, height = RETICLE_HEIGHT * scale),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCorners(
                color = Color.White,
                arm = 30.dp.toPx() * scale,
                stroke = 3.dp.toPx() * (1f + (scale - 1f) / 2f),
                radius = 11.dp.toPx() * scale
            )
        }
        Icon(
            Icons.Default.QrCode,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.32f),
            modifier = Modifier.size(64.dp * scale)
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
    modifier: Modifier = Modifier,
    tablet: Boolean = false,
    dense: Boolean = false
) {
    val nodeColumn = when {
        dense -> DENSE_NODE_COLUMN
        tablet -> TABLET_NODE_COLUMN
        else -> 44.dp
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // El hilo pasa por el centro de los nodos desde el primero hasta el último. En teléfono las filas
                // miden distinto (el caso cuelga bajo la estación), así que el primer nodo queda a ~30 dp; en tableta
                // las filas son parejas y el primer nodo está a media fila.
                val x = nodeColumn.toPx() / 2
                val inset = if (tablet) size.height / (2 * rows.size.coerceAtLeast(1)) else 30.dp.toPx()
                drawLine(
                    color = PmmNavyDark.copy(alpha = 0.28f),
                    start = Offset(x, inset),
                    end = Offset(x, size.height - inset),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
    ) {
        rows.forEachIndexed { index, row ->
            if (tablet) {
                BoardRowTablet(
                    number = index + 1,
                    row = row,
                    dense = dense,
                    onClick = { onTap(row) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                BoardRowItem(number = index + 1, row = row, onClick = { onTap(row) })
            }
        }
    }
}

/** Lo que dice una fila del Tablero, igual en teléfono y en tableta: nombre, renglón de apoyo, cifra y su caso. */
private class BoardRowText(
    val name: String,
    val detail: String,
    val figure: BoardFigure?,
    val exceptionText: String?,
    val spoken: String
)

@Composable
private fun boardRowText(row: BoardRow): BoardRowText {
    val resources = LocalContext.current.resources
    val name = stringResource(row.station.nameRes)
    val figure = row.figure.takeIf { row.allowed && row.figureState == FigureState.Ready }
    // Con cifra, el renglón de abajo dice qué cuenta; sin ella, sigue diciendo de qué trata la estación.
    val detail = when {
        !row.allowed -> stringResource(R.string.home_new_station_blocked_state)
        figure != null -> stringResource(stationUnit(row.station))
        else -> stringResource(stationDescription(row.station))
    }
    val exceptionText = figure?.exception?.let { resources.getQuantityString(it.code.phrase, it.count, it.count) }
    val spoken = buildString {
        append("$name. $detail")
        if (figure != null) append(". ${figure.count}")
        if (exceptionText != null) append(". $exceptionText")
    }
    return BoardRowText(name, detail, figure, exceptionText, spoken)
}

@Composable
private fun BoardRowItem(number: Int, row: BoardRow, onClick: () -> Unit) {
    val text = boardRowText(row)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (row.focus) Modifier.background(PmmNavyDark) else Modifier)
            .alpha(if (row.allowed) 1f else 0.55f)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text.spoken }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
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
                    text = text.name,
                    color = if (row.focus) Color.White else PmmTextPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp
                )
                Text(
                    text = text.detail,
                    color = if (row.focus) Color.White.copy(alpha = 0.82f) else PmmTextSecondary,
                    fontSize = 12.sp
                )
            }
            if (row.allowed) BoardFigureSlot(row = row)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (row.focus) Color.White else PmmTextMuted
            )
        }
        val exception = text.figure?.exception
        if (exception != null && text.exceptionText != null) {
            // El caso más fuerte cuelga como un ramal bajo la estación, alineado con su texto.
            SignalChip(
                kind = exception.signal,
                detail = text.exceptionText,
                modifier = Modifier.padding(start = 44.dp, end = 12.dp, bottom = 10.dp)
            )
        }
    }
}

/** Ancho de la columna de los nodos en tableta; el hilo de La Línea pasa por su centro. */
private val TABLET_NODE_COLUMN = 56.dp

/** Ancho de la columna de las cifras en tableta: cabe una de cuatro dígitos a 44 sp con su aire. */
private val TABLET_FIGURE_COLUMN = 108.dp

/** Lo mismo en la tableta de 8": nodos y cifras más chicos. */
private val DENSE_NODE_COLUMN = 48.dp
private val DENSE_FIGURE_COLUMN = 80.dp

/**
 * Una fila de La Línea en tableta. Mide lo que su `modifier` le dé (HomeBoard las reparte parejas) y, como sobra ancho,
 * el caso más fuerte va en la misma fila, junto a la cifra, en lugar de colgar debajo: así todas las filas miden igual
 * y el nombre no queda a un lado de la pantalla con su cifra al otro.
 *
 * @param dense tableta de 8": letras, nodo y etiqueta más chicos para que las cinco filas quepan sin desplazarse.
 */
@Composable
private fun BoardRowTablet(
    number: Int,
    row: BoardRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    val text = boardRowText(row)
    val exception = text.figure?.exception

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(if (dense) 14.dp else 16.dp))
            .then(if (row.focus) Modifier.background(PmmNavyDark) else Modifier)
            .alpha(if (row.allowed) 1f else 0.55f)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = text.spoken }
            .padding(end = if (dense) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(if (dense) DENSE_NODE_COLUMN else TABLET_NODE_COLUMN), contentAlignment = Alignment.Center) {
            BoardNode(
                number = number,
                row = row,
                diameter = if (dense) 34.dp else 38.dp,
                numberSize = if (dense) 15.sp else 17.sp
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = if (dense) 3.dp else 6.dp)
        ) {
            Text(
                text = text.name,
                color = if (row.focus) Color.White else PmmTextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = if (dense) 19.sp else 22.sp,
                lineHeight = if (dense) 23.sp else 28.sp
            )
            Text(
                text = text.detail,
                color = if (row.focus) Color.White.copy(alpha = 0.82f) else PmmTextSecondary,
                fontSize = if (dense) 13.sp else 14.sp,
                lineHeight = if (dense) 16.sp else 18.sp
            )
        }
        // Tres columnas fijas (estación, caso y cifra) para que las etiquetas arranquen todas en el mismo punto y las
        // cifras queden alineadas a la derecha, sin importar lo ancha que sea la etiqueta de cada fila. Si falta ancho,
        // el detalle del caso se parte en dos renglones en vez de aplastar el nombre de la estación.
        Box(
            modifier = Modifier
                .weight(if (dense) 1.9f else 1.6f)
                .padding(start = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (exception != null && text.exceptionText != null) {
                SignalChip(kind = exception.signal, detail = text.exceptionText, large = !dense)
            }
        }
        Box(
            Modifier.width(if (dense) DENSE_FIGURE_COLUMN else TABLET_FIGURE_COLUMN),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (row.allowed) BoardFigureSlot(row = row, large = true, dense = dense)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = if (row.focus) Color.White else PmmTextMuted,
            modifier = Modifier.size(if (dense) 24.dp else 28.dp)
        )
    }
}

/** La cifra de una estación: rueda al llegar, un esqueleto mientras se espera y un guion si no se pudo calcular. */
@Composable
private fun BoardFigureSlot(row: BoardRow, large: Boolean = false, dense: Boolean = false) {
    val color = if (row.focus) Color.White else PmmTextPrimary
    val figureSize = when {
        dense -> 36.sp
        large -> 44.sp
        else -> 32.sp
    }
    Box(
        modifier = Modifier.padding(horizontal = if (dense) 8.dp else if (large) 12.dp else 8.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        when (row.figureState) {
            FigureState.Ready -> row.figure?.let {
                FlipCounter(
                    value = it.count,
                    style = SignageType.Numeral.copy(fontSize = figureSize, lineHeight = figureSize),
                    color = color
                )
            }

            FigureState.Loading ->
                if (large) Skeleton(width = 52.dp, height = 32.dp) else Skeleton(width = 38.dp, height = 24.dp)

            FigureState.Unavailable -> Text(
                text = "—",
                color = if (row.focus) Color.White.copy(alpha = 0.6f) else PmmTextMuted,
                style = SignageType.Numeral.copy(
                    fontSize = if (large) 38.sp else 28.sp,
                    lineHeight = if (large) 38.sp else 28.sp
                )
            )
        }
    }
}

/** Círculo blanco con borde marino y el número de la estación; punteado y gris si el puesto no la tiene. */
@Composable
private fun BoardNode(number: Int, row: BoardRow, diameter: Dp = 30.dp, numberSize: TextUnit = 14.sp) {
    val ring = when {
        row.focus -> Color.White
        row.allowed -> PmmNavyDark
        else -> PmmTextMuted
    }
    val numberColor = if (row.allowed) PmmNavyDark else PmmTextMuted
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
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
            style = SignageType.StationNumber.copy(fontSize = numberSize, lineHeight = numberSize)
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

/**
 * «Más» en tableta: mosaicos que reparten el ancho en partes iguales en vez de píldoras pegadas a la izquierda con
 * media pantalla vacía a su lado. Hasta cuatro caben en una fila; con más se parten en dos filas parejas.
 */
@Composable
fun HomeMoreTiles(
    items: List<ModuleItem>,
    onClick: (ModuleItem) -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    if (items.isEmpty()) return
    val perRow = if (items.size <= 4) items.size else (items.size + 1) / 2
    // Con 680 dp o más cuatro mosaicos le dejan al título unos 90 dp y el ícono va a su lado; con menos, el ícono se
    // pone encima del título para que éste conserve todo el ancho del mosaico.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val stacked = maxWidth < 680.dp
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 12.dp)
        ) {
            items.chunked(perRow).forEach { rowItems ->
                // IntrinsicSize.Min: si un título se parte en dos renglones, los mosaicos de la fila igualan su alto.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(if (dense) 10.dp else 12.dp)
                ) {
                    rowItems.forEach { item ->
                        HomeMoreTile(
                            item = item,
                            stacked = stacked,
                            dense = dense,
                            onClick = { onClick(item) },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                    repeat(perRow - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun HomeMoreTile(
    item: ModuleItem,
    stacked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dense: Boolean = false
) {
    val title = stringResource(item.titleRes)
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        if (stacked) {
            Column(
                modifier = Modifier
                    .heightIn(min = if (dense) 78.dp else 96.dp)
                    .padding(horizontal = 6.dp, vertical = if (dense) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (dense) 4.dp else 6.dp, Alignment.CenterVertically)
            ) {
                TileIcon(item, size = if (dense) 30.dp else 36.dp)
                Text(
                    text = title,
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (dense) 13.sp else 14.sp,
                    lineHeight = if (dense) 16.sp else 17.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .heightIn(min = 76.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TileIcon(item, size = 40.dp)
                // A 15 sp «mercancía» cabe en los ~90 dp que deja una fila de cuatro mosaicos.
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TileIcon(item: ModuleItem, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(PmmNavyVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = PmmBlueDeep,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}
