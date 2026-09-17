package com.example.pmmovil.ui.warehouse

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.pmmovil.data.model.LocationOccupancy
import com.example.pmmovil.data.model.ClaseModelSpec
import com.example.pmmovil.data.model.WarehouseLayout
import com.example.pmmovil.data.model.WarehouseStructure
import com.example.pmmovil.data.model.WarehouseZone
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val MAX_ZOOM_MULTIPLIER = 6f
private const val VIEWPORT_EDGE_MARGIN_PX = 64f

/**
 * Lienzo operativo de solo lectura para el mapa de bodega (vista plana 2D).
 *
 * Comparte el mismo contrato que la web: usa `shape`, `fill` y `status` de
 * cada zona. El tap solo selecciona; la confirmacion ocurre en la pantalla
 * que lo consume.
 */
@Composable
fun WarehouseMapCanvas(
    layout: WarehouseLayout,
    selectedZoneId: String,
    occupancy: Map<String, LocationOccupancy> = emptyMap(),
    claseModels: List<ClaseModelSpec> = emptyList(),
    focusZoneId: String? = null,
    focusToken: Int = 0,
    focusInsetBottom: Dp = 0.dp,
    onZoneTap: (WarehouseZone) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember(layout) { mutableStateOf(1f) }
    var offset by remember(layout) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var hasFitted by remember(layout) { mutableStateOf(false) }
    val localDensity = LocalDensity.current
    val density = localDensity.density
    val insetBottomPx = with(localDensity) { focusInsetBottom.toPx() }
    val claseIndex = remember(claseModels) { claseModelIndex(claseModels) }

    val pulseTransition = rememberInfiniteTransition(label = "warehouseZonePulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    fun fit() {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return

        val bounds = planWorldBounds(layout)
        val nextScale = fitScaleForBounds(bounds, size, insetBottomPx)

        scale = nextScale
        offset = centeredOffset(bounds, size, nextScale, insetBottomPx)
    }

    fun clampScale(value: Float): Float {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return value

        val fitScale = fitScaleForBounds(planWorldBounds(layout), size, insetBottomPx)
        return value.coerceIn(fitScale, fitScale * MAX_ZOOM_MULTIPLIER)
    }

    fun clampViewportOffset(proposedOffset: Offset, proposedScale: Float): Offset {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return proposedOffset

        return clampOffsetToBounds(
            offset = proposedOffset,
            scale = proposedScale,
            bounds = planWorldBounds(layout),
            canvasSize = size
        )
    }

    fun zoomBy(factor: Float) {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return

        val pivot = Offset(size.width / 2f, size.height / 2f)
        val newScale = clampScale(scale * factor)
        val world = Offset(
            x = (pivot.x - offset.x) / scale,
            y = (pivot.y - offset.y) / scale
        )
        val proposedOffset = Offset(
            x = pivot.x - world.x * newScale,
            y = pivot.y - world.y * newScale
        )
        offset = clampViewportOffset(proposedOffset, newScale)
        scale = newScale
    }

    fun focusZone(zone: WarehouseZone) {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return

        // La altura "visible" descuenta lo que tapa el panel flotante, para que
        // la zona enfocada nunca quede centrada detras de el.
        val visibleHeight = (size.height - insetBottomPx).coerceAtLeast(1f)

        val bounds = planZoneBounds(zone)
        val pad = 76f
        val targetScale = min(
            (size.width - pad).coerceAtLeast(1f) / bounds.width.coerceAtLeast(1f),
            (visibleHeight - pad).coerceAtLeast(1f) / bounds.height.coerceAtLeast(1f)
        )
        val nextScale = clampScale(targetScale)

        val proposedOffset = Offset(
            x = size.width / 2f - bounds.center.x * nextScale,
            y = visibleHeight / 2f - bounds.center.y * nextScale
        )
        offset = clampViewportOffset(proposedOffset, nextScale)
        scale = nextScale
    }

    // Solo se hace fit-to-bounds la PRIMERA vez que este layout obtiene un tamano
    // valido. Cambios de tamano posteriores (el panel de detalle crece/encoge,
    // el cajon se colapsa, etc.) NO deben resetear el zoom del usuario: solo se
    // re-clampea para que siga siendo valido en el nuevo tamano.
    LaunchedEffect(canvasSize, layout) {
        val size = canvasSize
        if (size.width <= 0 || size.height <= 0) return@LaunchedEffect
        if (!hasFitted) {
            fit()
            hasFitted = true
        } else {
            val clampedScale = clampScale(scale)
            offset = clampViewportOffset(offset, clampedScale)
            scale = clampedScale
        }
    }

    LaunchedEffect(focusZoneId, focusToken, canvasSize, layout) {
        val zoneId = focusZoneId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val zone = layout.zones.firstOrNull { it.id.equals(zoneId, ignoreCase = true) }
            ?: return@LaunchedEffect
        focusZone(zone)
    }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val newScale = clampScale(scale * zoom)
                        val worldX = (centroid.x - offset.x) / scale
                        val worldY = (centroid.y - offset.y) / scale
                        val proposedOffset = Offset(
                            centroid.x - worldX * newScale + pan.x,
                            centroid.y - worldY * newScale + pan.y
                        )
                        offset = clampViewportOffset(proposedOffset, newScale)
                        scale = newScale
                    }
                }
                .pointerInput(layout) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val zone = zoneAtScreenPoint(layout, tap, offset, scale)
                            if (zone != null) {
                                focusZone(zone)
                            } else {
                                fit()
                            }
                        },
                        onTap = { tap ->
                            val zone = zoneAtScreenPoint(layout, tap, offset, scale)
                            if (zone != null) onZoneTap(zone)
                        }
                    )
                }
        ) {
            val hasSelection = selectedZoneId.isNotBlank()

            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = Offset.Zero)
            }) {
                drawPlanScene(
                    layout = layout,
                    occupancy = occupancy,
                    claseIndex = claseIndex,
                    selectedZoneId = selectedZoneId,
                    hasSelection = hasSelection,
                    pulse = pulse
                )
            }

            drawPlanLabels(
                layout = layout,
                occupancy = occupancy,
                selectedZoneId = selectedZoneId,
                hasSelection = hasSelection,
                viewOffset = offset,
                viewScale = scale,
                density = density
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            shape = RoundedCornerShape(13.dp),
            color = Color.White,
            shadowElevation = 6.dp,
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column {
                IconButton(onClick = { zoomBy(1.18f) }) {
                    Icon(Icons.Default.Add, contentDescription = "Acercar", tint = PmmTextSecondary)
                }
                Box(Modifier.size(width = 48.dp, height = 1.dp).background(PmmNavyBorder))
                IconButton(onClick = { fit() }) {
                    Icon(Icons.Default.CenterFocusStrong, contentDescription = "Ajustar vista", tint = PmmBlueDeep)
                }
                Box(Modifier.size(width = 48.dp, height = 1.dp).background(PmmNavyBorder))
                IconButton(onClick = { zoomBy(1f / 1.18f) }) {
                    Icon(Icons.Default.Remove, contentDescription = "Alejar", tint = PmmTextSecondary)
                }
            }
        }
    }
}

// --- Fit y hit-test ---

private fun planWorldBounds(layout: WarehouseLayout): Rect = Rect(
    left = 0f,
    top = 0f,
    right = layout.canvas.width.coerceAtLeast(1f),
    bottom = layout.canvas.height.coerceAtLeast(1f)
)

private fun planZoneBounds(zone: WarehouseZone): Rect =
    Rect(zone.x, zone.y, zone.x + zone.width, zone.y + zone.height)

private fun fitScaleForBounds(bounds: Rect, canvasSize: IntSize, insetBottom: Float = 0f): Float {
    val pad = 44f
    val availableWidth = max(1f, canvasSize.width - pad * 2f)
    val availableHeight = max(1f, canvasSize.height - insetBottom - pad * 2f)

    return min(
        availableWidth / bounds.width.coerceAtLeast(1f),
        availableHeight / bounds.height.coerceAtLeast(1f)
    ).coerceAtLeast(0.06f)
}

private fun centeredOffset(bounds: Rect, canvasSize: IntSize, scale: Float, insetBottom: Float = 0f): Offset {
    val visibleHeight = canvasSize.height - insetBottom
    return Offset(
        x = (canvasSize.width - bounds.width * scale) / 2f - bounds.left * scale,
        y = (visibleHeight - bounds.height * scale) / 2f - bounds.top * scale
    )
}

private fun clampOffsetToBounds(
    offset: Offset,
    scale: Float,
    bounds: Rect,
    canvasSize: IntSize
): Offset {
    fun clampAxis(
        proposed: Float,
        viewportSize: Float,
        worldStart: Float,
        worldEnd: Float
    ): Float {
        val scaledSize = (worldEnd - worldStart) * scale
        if (scaledSize <= viewportSize - VIEWPORT_EDGE_MARGIN_PX * 2f) {
            return (viewportSize - scaledSize) / 2f - worldStart * scale
        }

        val minOffset = viewportSize - VIEWPORT_EDGE_MARGIN_PX - worldEnd * scale
        val maxOffset = VIEWPORT_EDGE_MARGIN_PX - worldStart * scale
        return proposed.coerceIn(minOffset, maxOffset)
    }

    return Offset(
        x = clampAxis(
            proposed = offset.x,
            viewportSize = canvasSize.width.toFloat(),
            worldStart = bounds.left,
            worldEnd = bounds.right
        ),
        y = clampAxis(
            proposed = offset.y,
            viewportSize = canvasSize.height.toFloat(),
            worldStart = bounds.top,
            worldEnd = bounds.bottom
        )
    )
}

private fun zoneAtScreenPoint(
    layout: WarehouseLayout,
    screenPoint: Offset,
    offset: Offset,
    scale: Float
): WarehouseZone? {
    val worldPoint = Offset(
        x = (screenPoint.x - offset.x) / scale,
        y = (screenPoint.y - offset.y) / scale
    )
    return planHitTest(layout, worldPoint)
}

private fun planHitTest(layout: WarehouseLayout, worldPoint: Offset): WarehouseZone? =
    layout.zones.lastOrNull { zone -> containsPlanPoint(zone, worldPoint) }

private fun containsPlanPoint(zone: WarehouseZone, point: Offset): Boolean {
    val rotation = zone.rotation ?: 0f
    val testPoint = if (rotation == 0f) {
        point
    } else {
        val center = Offset(zone.x + zone.width / 2f, zone.y + zone.height / 2f)
        val radians = Math.toRadians((-rotation).toDouble())
        val dx = point.x - center.x
        val dy = point.y - center.y
        Offset(
            x = center.x + dx * cos(radians).toFloat() - dy * sin(radians).toFloat(),
            y = center.y + dx * sin(radians).toFloat() + dy * cos(radians).toFloat()
        )
    }

    return testPoint.x >= zone.x &&
        testPoint.x <= zone.x + zone.width &&
        testPoint.y >= zone.y &&
        testPoint.y <= zone.y + zone.height
}

// --- Plano 2D ---

private fun DrawScope.drawPlanScene(
    layout: WarehouseLayout,
    occupancy: Map<String, LocationOccupancy>,
    claseIndex: Map<String, ClaseModelSpec>,
    selectedZoneId: String,
    hasSelection: Boolean,
    pulse: Float
) {
    val cw = layout.canvas.width.coerceAtLeast(1f)
    val ch = layout.canvas.height.coerceAtLeast(1f)

    drawRect(color = Color(0xFFFAF9F6), topLeft = Offset.Zero, size = Size(cw, ch))
    layout.structures
        .filter { it.id == "outer-wall" || it.type == "wall" }
        .forEach { drawPlanStructure(it) }
    drawPlanGrid(cw, ch)
    layout.structures
        .filterNot { it.id == "outer-wall" || it.type == "wall" }
        .forEach { drawPlanStructure(it) }
    layout.zones.forEach { zone ->
        drawPlanZone(
            zone = zone,
            occupancy = occupancy[zone.id.trim().uppercase()],
            claseIndex = claseIndex,
            selected = zone.id == selectedZoneId,
            hasSelection = hasSelection,
            pulse = pulse
        )
    }
}

private fun DrawScope.drawPlanGrid(cw: Float, ch: Float) {
    val step = 40f
    var x = step
    while (x < cw) {
        var y = step
        while (y < ch) {
            drawCircle(Color(0xFFCBD5E1).copy(alpha = 0.62f), radius = 1.45f, center = Offset(x, y))
            y += step
        }
        x += step
    }
}

private fun DrawScope.drawPlanStructure(structure: WarehouseStructure) {
    val strokeColor = parseHexColor(
        hex = if (structure.stroke == "#111827") "#94a3b8" else structure.stroke,
        fallback = Color(0xFF94A3B8)
    )
    val strokeWidth = structure.strokeWidth ?: 3f

    if (structure.shape == "line") {
        val points = structure.points ?: return
        var i = 0
        while (i + 3 < points.size) {
            drawLine(
                color = strokeColor,
                start = Offset(points[i], points[i + 1]),
                end = Offset(points[i + 2], points[i + 3]),
                strokeWidth = strokeWidth
            )
            i += 2
        }
        return
    }

    if (structure.shape != "rect") return

    val x = structure.x ?: 0f
    val y = structure.y ?: 0f
    val width = structure.width ?: 0f
    val height = structure.height ?: 0f
    val isWall = structure.id == "outer-wall" || structure.type == "wall"
    val radius = CornerRadius(if (isWall) 22f else 14f)

    drawRoundRect(
        color = parseHexColor(
            hex = if (isWall) "#ffffff" else structure.fill,
            fallback = Color(0xFFFBFAF7)
        ),
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = radius
    )
    drawRoundRect(
        color = if (isWall) Color(0xFFE2E8F0) else strokeColor,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = radius,
        style = Stroke(width = if (isWall) 1.5f else strokeWidth)
    )
}

private fun DrawScope.drawPlanZone(
    zone: WarehouseZone,
    occupancy: LocationOccupancy?,
    claseIndex: Map<String, ClaseModelSpec>,
    selected: Boolean,
    hasSelection: Boolean,
    pulse: Float
) {
    val status = zone.status
    val alpha = if (hasSelection && !selected) 0.36f else 0.92f
    val clase = dominantClase(occupancy)
    val classColor = clase.takeIf { it.isNotBlank() }?.let { claseColor(it, claseIndex) }
    val fill = (classColor?.copy(alpha = 0.24f) ?: zoneFillColor(zone.fill, status)).copy(alpha = alpha)
    val stroke = if (selected) {
        MAP_SELECTED_STROKE
    } else {
        classColor ?: zoneStrokeColor(zone.fill, status)
    }
    val strokeWidth = if (selected) 4f else 2f
    val haloWidth = 5f + pulse * 7f
    val haloAlpha = 0.30f - pulse * 0.16f
    val rotation = zone.rotation ?: 0f

    withTransform({
        if (rotation != 0f) {
            rotate(rotation, pivot = Offset(zone.x + zone.width / 2f, zone.y + zone.height / 2f))
        }
    }) {
        when (zone.shape) {
            "circle" -> {
                if (selected) {
                    drawOval(
                        color = MAP_SELECTED_STROKE.copy(alpha = haloAlpha),
                        topLeft = Offset(zone.x, zone.y),
                        size = Size(zone.width, zone.height),
                        style = Stroke(haloWidth)
                    )
                }
                drawOval(color = fill, topLeft = Offset(zone.x, zone.y), size = Size(zone.width, zone.height))
                drawOval(
                    color = stroke,
                    topLeft = Offset(zone.x, zone.y),
                    size = Size(zone.width, zone.height),
                    style = Stroke(strokeWidth)
                )
            }
            "diamond" -> {
                val path = Path().apply {
                    moveTo(zone.x + zone.width / 2f, zone.y)
                    lineTo(zone.x + zone.width, zone.y + zone.height / 2f)
                    lineTo(zone.x + zone.width / 2f, zone.y + zone.height)
                    lineTo(zone.x, zone.y + zone.height / 2f)
                    close()
                }
                if (selected) drawPath(path, MAP_SELECTED_STROKE.copy(alpha = haloAlpha), style = Stroke(haloWidth))
                drawPath(path, fill)
                drawPath(path, stroke, style = Stroke(strokeWidth))
            }
            else -> {
                val radius = CornerRadius(12f)
                if (selected) {
                    drawRoundRect(
                        color = MAP_SELECTED_STROKE.copy(alpha = haloAlpha),
                        topLeft = Offset(zone.x, zone.y),
                        size = Size(zone.width, zone.height),
                        cornerRadius = radius,
                        style = Stroke(haloWidth)
                    )
                }
                drawRoundRect(
                    color = fill,
                    topLeft = Offset(zone.x, zone.y),
                    size = Size(zone.width, zone.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = stroke,
                    topLeft = Offset(zone.x, zone.y),
                    size = Size(zone.width, zone.height),
                    cornerRadius = radius,
                    style = Stroke(strokeWidth)
                )
            }
        }
    }
}

/**
 * Pinta codigo + meta (bultos/estatus) centrados DENTRO de cada zona (sin chip
 * flotante). Cada linea solo se dibuja si mide/cabe en el rectangulo real de
 * la zona a la escala actual, para que el texto nunca se desborde ni invada
 * zonas vecinas cuando estan muy juntas o el zoom es bajo.
 */
private fun DrawScope.drawPlanLabels(
    layout: WarehouseLayout,
    occupancy: Map<String, LocationOccupancy>,
    selectedZoneId: String,
    hasSelection: Boolean,
    viewOffset: Offset,
    viewScale: Float,
    density: Float
) {
    val canvas = drawContext.canvas.nativeCanvas
    val codeTextSize = 13f * density
    val metaTextSize = 9.5f * density
    val lineGap = 3f * density
    val margin = 6f * density

    val codePaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
        textSize = codeTextSize
    }
    val metaPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        textSize = metaTextSize
    }

    for (zone in layout.zones.sortedBy { it.y + it.x }) {
        val selected = zone.id == selectedZoneId
        val text = zone.label.ifBlank { zone.id }
        if (text.isBlank()) continue

        val sx = viewOffset.x + (zone.x + zone.width / 2f) * viewScale
        val sy = viewOffset.y + (zone.y + zone.height / 2f) * viewScale
        if (sx < -80f || sx > size.width + 80f || sy < -40f || sy > size.height + 40f) continue

        val occ = occupancy[zone.id.trim().uppercase()]
        val status = occ?.status ?: zone.status ?: "libre"
        val textColor = if (selected) MAP_SELECTED_STROKE else zoneStrokeColor(zone.fill, status)
        val metaText = if (occ != null && occ.bultos > 0) "${occ.bultos} bulto(s)" else statusLabel(status)

        val alpha = if (hasSelection && !selected) 96 else 255
        codePaint.color = textColor.toArgb()
        codePaint.alpha = alpha
        metaPaint.color = textColor.toArgb()
        metaPaint.alpha = if (hasSelection && !selected) 80 else 210

        val boxWidthPx = zone.width * viewScale
        val boxHeightPx = zone.height * viewScale

        val codeWidth = codePaint.measureText(text)
        val fitsCode = boxWidthPx >= codeWidth + margin * 2f && boxHeightPx >= codeTextSize + margin * 2f
        if (!fitsCode) continue

        val metaWidth = metaPaint.measureText(metaText)
        val fitsMeta = metaText.isNotBlank() &&
            boxWidthPx >= metaWidth + margin * 2f &&
            boxHeightPx >= codeTextSize + metaTextSize + lineGap + margin * 2f

        if (fitsMeta) {
            val codeCenterY = sy - (metaTextSize + lineGap) / 2f
            val metaCenterY = sy + (codeTextSize + lineGap) / 2f
            canvas.drawText(text, sx, codeCenterY + codeTextSize * 0.34f, codePaint)
            canvas.drawText(metaText, sx, metaCenterY + metaTextSize * 0.34f, metaPaint)
        } else {
            canvas.drawText(text, sx, sy + codeTextSize * 0.34f, codePaint)
        }
    }
}
