package com.example.pmmovil.mapeador.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.mapeador.core.DemoEntry
import com.example.pmmovil.mapeador.core.ProjectedLocation
import com.example.pmmovil.mapeador.session.AuditResult
import com.example.pmmovil.mapeador.session.GuideState
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class CardState { FREE, OCCUPIED, PARTIAL }

/** Lo que dice una tarjeta flotante. */
class CardData(
    val code: String,
    val summary: String,
    val topClient: String,
    val state: CardState,
    val audit: AuditResult?,
    val demo: Boolean
)

/** Mira central para apuntar al lugar que se va a marcar. */
@Composable
fun Reticle(modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(64.dp)) {
        val c = center
        val r = size.minDimension / 2f
        val w = 2.5.dp.toPx()
        drawCircle(color.copy(alpha = 0.9f), radius = r * 0.5f, style = Stroke(width = w))
        drawLine(color, Offset(c.x - r, c.y), Offset(c.x - r * 0.62f, c.y), w)
        drawLine(color, Offset(c.x + r * 0.62f, c.y), Offset(c.x + r, c.y), w)
        drawLine(color, Offset(c.x, c.y - r), Offset(c.x, c.y - r * 0.62f), w)
        drawLine(color, Offset(c.x, c.y + r * 0.62f), Offset(c.x, c.y + r), w)
        drawCircle(color, radius = 3.dp.toPx())
    }
}

/** Interruptor de una capa de GL (superficies, anclas…) con un punto del color con el que se dibuja. */
@Composable
fun LayerChip(text: String, dot: Color, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (selected) dot else Muted, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(text, fontSize = 12.sp)
            }
        }
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(color = color.copy(alpha = 0.9f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            text,
            color = Color.Black,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun LocationCard(
    data: CardData,
    selected: Boolean,
    guided: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = when {
        guided -> Accent
        selected -> Color.White
        data.audit == AuditResult.OK -> Good
        data.audit == AuditResult.MISSING -> Bad
        data.audit == AuditResult.EXTRA -> Warn
        else -> Color(0x66FFFFFF)
    }
    val dot = when (data.state) {
        CardState.FREE -> Muted
        CardState.OCCUPIED -> Good
        CardState.PARTIAL -> Warn
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = PanelColor,
        border = BorderStroke(2.dp, border),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(data.code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (data.demo) Text("DEMO", color = Warn, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                data.audit?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when (it) {
                            AuditResult.OK -> "✓"
                            AuditResult.MISSING -> "✗"
                            AuditResult.EXTRA -> "+"
                        },
                        color = border, fontWeight = FontWeight.Bold, fontSize = 16.sp
                    )
                }
            }
            Text(data.summary, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!compact) {
                Text(data.topClient, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * Tallos y tarjetas de las ubicaciones proyectadas. Cada tarjeta flota sobre su ubicación y
 * se achica con la distancia; las cercanas quedan encima de las lejanas.
 */
@Composable
fun LocationMarkers(
    items: List<ProjectedLocation>,
    cardFor: (String) -> CardData,
    selectedCode: String?,
    guideCode: String?,
    compact: Boolean,
    dimmed: Boolean,
    onTap: (String) -> Unit,
    /** Alto de lo que ocupan el HUD de arriba y los botones de abajo: las tarjetas no se encimen con ellos. */
    topReserved: Dp = 170.dp,
    bottomReserved: Dp = 150.dp
) {
    val visible = items.filter { it.visible }.sortedByDescending { it.distanceM }
    val density = LocalDensity.current
    val cardW = 176.dp
    val cardH = if (compact) 48.dp else 64.dp
    val cardWpx = with(density) { cardW.toPx() }
    val cardHpx = with(density) { cardH.toPx() }
    val alpha = if (dimmed) 0.5f else 1f

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val maxW = constraints.maxWidth.toFloat()
        val minY = with(density) { topReserved.toPx() } + cardHpx
        val maxY = (constraints.maxHeight - with(density) { bottomReserved.toPx() } - cardHpx).coerceAtLeast(minY)
        Canvas(Modifier.fillMaxSize()) {
            for (p in visible) {
                val color = if (p.code == guideCode) Accent else Color.White.copy(alpha = 0.8f * alpha)
                drawLine(color, Offset(p.baseX, p.baseY), Offset(p.headX, p.headY), 2.dp.toPx())
                drawCircle(color, 5.dp.toPx(), Offset(p.baseX, p.baseY))
            }
        }
        for (p in visible) {
            val scale = (3.0f / p.distanceM.coerceAtLeast(1.5f)).coerceIn(0.6f, 1.15f)
            val x = (p.headX - cardWpx / 2f).coerceIn(0f, (maxW - cardWpx).coerceAtLeast(0f))
            val y = (p.headY - cardHpx).coerceIn(minY - cardHpx, maxY)
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        this.alpha = alpha
                    }
                    .size(cardW, cardH)
            ) {
                LocationCard(
                    data = cardFor(p.code),
                    selected = p.code == selectedCode,
                    guided = p.code == guideCode,
                    compact = compact,
                    onClick = { onTap(p.code) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Flecha de «llévame a…». */
@Composable
fun GuideBadge(guide: GuideState, modifier: Modifier = Modifier) {
    Surface(
        color = PanelColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(2.dp, if (guide.arrived) Good else Accent),
        modifier = modifier
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (guide.arrived) {
                Text("✓", color = Good, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            } else {
                Canvas(Modifier.size(44.dp).rotate(guide.angleDeg)) {
                    val path = Path().apply {
                        moveTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        lineTo(size.width / 2f, size.height * 0.72f)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, Accent)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    if (guide.arrived) "Aquí está «${guide.code}»" else "Llévame a «${guide.code}»",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
                if (!guide.arrived) {
                    Text(
                        String.format(Locale.US, "%.1f m%s", guide.distanceM, if (guide.onScreen) " · al frente" else ""),
                        color = Muted, fontSize = 13.sp
                    )
                }
            }
        }
    }
}

private val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.forLanguageTag("es-MX"))

/** Detalle de una ubicación: entradas que el sistema dice que están ahí y botones de auditoría. */
@Composable
fun LocationDetailPanel(
    code: String,
    distanceM: Float?,
    entries: List<DemoEntry>,
    audit: AuditResult?,
    guided: Boolean,
    onAudit: (AuditResult) -> Unit,
    onGuide: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = PanelColor, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(code, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Spacer(Modifier.width(8.dp))
                Text("DEMO", color = Warn, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                if (distanceM != null) Text(String.format(Locale.US, "a %.1f m", distanceM), color = Muted, fontSize = 14.sp)
                TextButton(onClick = onClose) { Text("Cerrar") }
            }
            if (entries.isEmpty()) {
                Text("El sistema no tiene entradas en esta ubicación.", color = Muted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                LazyColumn(Modifier.heightIn(max = 190.dp)) {
                    items(entries) { e ->
                        Column(Modifier.padding(vertical = 5.dp)) {
                            Text("${e.reference} · ${e.client}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(e.description, color = Color.White, fontSize = 13.sp)
                            Text(
                                "${e.packages} bultos · ${e.state} · llegó ${e.arrival.format(dateFormat)} · ${e.cargoClass}",
                                color = Muted, fontSize = 12.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuditButton("✓ Está todo", Good, audit == AuditResult.OK, Modifier.weight(1f)) { onAudit(AuditResult.OK) }
                AuditButton("✗ Falta algo", Bad, audit == AuditResult.MISSING, Modifier.weight(1f)) { onAudit(AuditResult.MISSING) }
                AuditButton("+ Hay otra cosa", Warn, audit == AuditResult.EXTRA, Modifier.weight(1f)) { onAudit(AuditResult.EXTRA) }
            }
            Spacer(Modifier.size(6.dp))
            OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth()) {
                Text(if (guided) "Dejar de guiar" else "Llévame aquí")
            }
        }
    }
}

@Composable
private fun AuditButton(text: String, color: Color, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.Black)) {
            Text(text, fontSize = 12.sp, maxLines = 1)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.5.dp, color)) {
            Text(text, fontSize = 12.sp, maxLines = 1, color = color)
        }
    }
}

/** Crea los datos de una tarjeta a partir de las entradas de demostración de la ubicación. */
fun cardDataFor(code: String, entries: List<DemoEntry>, audit: AuditResult?): CardData {
    val state = when {
        entries.isEmpty() -> CardState.FREE
        entries.any { it.state == "parcial" } -> CardState.PARTIAL
        else -> CardState.OCCUPIED
    }
    return CardData(
        code = code,
        summary = com.example.pmmovil.mapeador.core.DemoData.summary(entries),
        topClient = entries.firstOrNull()?.client.orEmpty(),
        state = state,
        audit = audit,
        demo = true
    )
}

@Composable
fun FullScreenBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) { content() }
}
