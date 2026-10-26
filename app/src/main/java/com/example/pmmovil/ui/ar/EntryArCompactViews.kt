package com.example.pmmovil.ui.ar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.entryar.ArItemKind
import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.ui.components.MiniStationLine
import com.example.pmmovil.ui.components.SignalChip
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.SignalShape
import com.example.pmmovil.ui.components.Station
import com.example.pmmovil.ui.components.StationState
import com.example.pmmovil.ui.scanactions.flagTextRes
import com.example.pmmovil.ui.scanactions.signalKindOf
import com.example.pmmovil.ui.scanactions.stationStateOf

/**
 * Las marcas de una caja o de una relación en el AR: una ficha compacta con la señal y La Línea. Una entrada tiene su
 * propia tarjeta (revisión, facturas, partidas) y no pasa por aquí.
 */

/** El estado de las cinco estaciones, siempre cinco: lo que el servidor no mande queda «sin dato». */
internal fun compactStates(compact: TraceCompactItemDto): List<StationState> =
    List(Station.entries.size) { index -> compact.states.getOrNull(index)?.let(::stationStateOf) ?: StationState.Unknown }

/** Lo que dice un QR de caja o de relación sobre sí mismo cuando todavía no hay ficha. */
internal fun arKindLabel(kind: ArItemKind): String = when (kind) {
    ArItemKind.Entry -> "Entrada"
    ArItemKind.Caja -> "Caja"
    ArItemKind.Relacion -> "Relación"
}

/** La palabra de la señal para el chip que va sobre el QR: «Aviso», «Listo»…, o «No existe» si el servidor no la conoce. */
@Composable
internal fun compactMarkerLabel(item: EntryArOverlayItem): String {
    val compact = item.compact ?: return "…"
    if (!compact.found) return "No existe"
    return stringResource(signalKindOf(compact.signal).wordRes)
}

/** Cápsula pequeña con la forma y la palabra de la señal; cabe en la tarjeta del AR (16 dp de alto). */
@Composable
private fun ArSignalCapsule(kind: SignalKind, modifier: Modifier = Modifier) {
    // «Sin dato» es blanco con borde punteado en el resto de la app; sobre una tarjeta blanca no se vería.
    val background = if (kind.outlined) Color(0xFFE2E8F0) else kind.container
    Row(
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SignalShape(kind = kind, size = 10.dp)
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(kind.wordRes).uppercase(),
            color = kind.content,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * El cuerpo de la tarjeta flotante de una caja o una relación: el cliente, La Línea en miniatura y la señal. Ocupa el
 * mismo espacio que el de una entrada para que el colocador de tarjetas no tenga que distinguirlas.
 */
@Composable
internal fun ColumnScope.CompactCardBody(item: EntryArOverlayItem, offline: Boolean) {
    val compact = item.compact
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .padding(start = 9.dp, top = 5.dp, end = 9.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        when {
            // El tema pone 24 sp de alto de línea a todo texto: sin lineHeight propio cada renglón mide el doble de lo
            // que dice su tamaño y la cápsula de abajo se sale de la tarjeta.
            compact == null -> Text(
                if (offline) "Sin conexión: sin ficha guardada" else "Consultando…",
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                maxLines = 2
            )

            !compact.found -> Text(
                "No existe en el sistema",
                color = Color(0xFF475569),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Bold
            )

            else -> {
                Text(
                    compact.customer.ifBlank { "Cliente sin nombre" },
                    color = Color(0xFF172B45),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                // Cinco columnas de 26 dp caben en el ancho mínimo de la tarjeta (156 dp menos sus márgenes).
                MiniStationLine(states = compactStates(compact), nodeSize = 11.dp, columnWidth = 26.dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArSignalCapsule(signalKindOf(compact.signal))
                    Spacer(Modifier.weight(1f))
                    if (compact.flags.any { flagTextRes(it) != null }) {
                        Icon(Icons.Default.Warning, "Con avisos", modifier = Modifier.size(13.dp), tint = Color(0xFFB45309))
                    }
                }
            }
        }
    }
}

/** La hoja que se abre al tocar una caja o una relación en el AR: lo mismo en grande y la salida a su ficha completa. */
@Composable
internal fun EntryArCompactActionSheet(
    item: EntryArOverlayItem,
    offline: Boolean,
    onOpenFicha: () -> Unit
) {
    val compact = item.compact
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // El AR va en horizontal: la hoja mide ~400 dp de alto y la información no cabe. Se desplaza sola y el botón
        // queda fuera de ella, siempre entero; sin esto Compose le quita el alto al botón y solo queda una franja.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(item.code.normalized, color = Color(0xFF12345A), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text(
                if (item.kind == ArItemKind.Caja) "Caja de caseta" else "Relación de salida",
                color = Color(0xFF475569),
                fontSize = 12.sp
            )

            if (compact != null && compact.found) {
                Text(
                    compact.customer.ifBlank { "Cliente sin nombre" },
                    color = Color(0xFF172B45),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                val details = listOfNotNull(
                    compact.box.takeIf { it.isNotBlank() }?.let { "Caja $it" },
                    compact.relno.takeIf { it.isNotBlank() && it != item.code.normalized }?.let { "Relación $it" }
                ).joinToString(" · ")
                if (details.isNotBlank()) Text(details, color = Color(0xFF475569), fontSize = 12.sp)

                SignalChip(kind = signalKindOf(compact.signal), large = true)
                MiniStationLine(states = compactStates(compact), modifier = Modifier.padding(top = 4.dp))

                compact.flags.mapNotNull(::flagTextRes).distinct().forEach { phrase ->
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Warning, null, modifier = Modifier.size(16.dp), tint = Color(0xFFB45309))
                        Text(stringResource(phrase), color = Color(0xFF92400E), fontSize = 12.sp)
                    }
                }
            } else if (compact != null) {
                Text("Este código no existe en el sistema.", color = Color(0xFF475569), fontSize = 13.sp)
            } else {
                Text(
                    if (offline) "Sin conexión: la ficha completa mostrará lo que dice el código." else "Consultando…",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            }

            if (offline && item.fromCache) {
                Text("Información guardada sin conexión", color = Color(0xFF64748B), fontSize = 11.sp)
            }
        }

        Button(
            onClick = onOpenFicha,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC928), contentColor = Color(0xFF172B45))
        ) {
            Text("Abrir ficha", fontWeight = FontWeight.Bold)
        }
    }
}
