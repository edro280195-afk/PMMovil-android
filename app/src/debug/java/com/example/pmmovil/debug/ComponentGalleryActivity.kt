package com.example.pmmovil.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.entryar.ArItemKind
import com.example.pmmovil.data.entryar.EntryArQrCode
import com.example.pmmovil.data.model.EntryArCardDto
import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.ui.ar.EntryArCompactActionSheet
import com.example.pmmovil.ui.ar.EntryArFloatingCard
import com.example.pmmovil.ui.ar.EntryArOverlayItem
import com.example.pmmovil.ui.ar.EntryArQrMarker
import com.example.pmmovil.ui.components.FlipCounter
import com.example.pmmovil.ui.components.LabelCard
import com.example.pmmovil.ui.components.MiniStationLine
import com.example.pmmovil.ui.components.SignalChip
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.StationNode
import com.example.pmmovil.ui.components.StationState
import com.example.pmmovil.ui.theme.PMMovilTheme
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.SignageType

/**
 * Galería de las piezas de la señalética, solo en debug: sirve para verlas en el Pixel y en la tableta con el tamaño
 * de fuente grande y con las animaciones del sistema apagadas, antes de armar el inicio con ellas.
 *
 * Se abre con:
 *   adb shell am start -n com.example.pmmovil/.debug.ComponentGalleryActivity
 */
class ComponentGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PMMovilTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PmmBgPrimary)
                        .statusBarsPadding()
                ) { Gallery() }
            }
        }
    }
}

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        Section("Señales") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalChip(SignalKind.Alto)
                SignalChip(SignalKind.Atencion)
                SignalChip(SignalKind.Listo)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalChip(SignalKind.Aviso)
                SignalChip(SignalKind.Cerrado)
                SignalChip(SignalKind.SinDato)
            }
            SignalChip(SignalKind.Atencion, detail = "En patio sin entrada")
            SignalChip(SignalKind.Listo, detail = "Lista para salir", large = true)
            SignalChip(SignalKind.SinDato, detail = "Sin caja vinculada", large = true)
        }

        Section("Contador de aletas") {
            var value by remember { mutableIntStateOf(9) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                FlipCounter(value = value)
                FlipCounter(value = value * 7 % 100)
                FlipCounter(value = value * 31 % 1000)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { value = (value + 1) % 100 }) { Text("+1") }
                Button(onClick = { value = (value + 7) % 100 }) { Text("+7") }
                Button(onClick = { value = 0 }) { Text("0") }
            }
        }

        Section("Estaciones") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                StationState.entries.forEachIndexed { index, state ->
                    StationNode(number = index + 1, state = state)
                }
            }
            MiniStationLine(
                listOf(
                    StationState.Done, StationState.Done, StationState.Warn,
                    StationState.Now, StationState.Todo
                )
            )
            MiniStationLine(
                listOf(
                    StationState.Done, StationState.Unknown, StationState.Todo,
                    StationState.Todo, StationState.Todo
                )
            )
        }

        Section("Etiqueta viva") {
            LabelCard(
                kindLabel = "CAJA",
                body = {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("E-017799/26", style = SignageType.Code, color = PmmTextPrimary)
                        Text(
                            "MSKU 337104 · Importación · directo",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = PmmTextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        MiniStationLine(
                            listOf(
                                StationState.Done, StationState.Done, StationState.Warn,
                                StationState.Now, StationState.Now
                            )
                        )
                    }
                },
                stub = {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        SignalChip(SignalKind.Listo, detail = "Lista para salir", large = true)
                        Text("Dar salida en caseta.", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        Section("AR · caja y relación (tarjeta compacta)") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF1F2937), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Los dos anchos extremos que usa el AR: 156 dp (el mínimo) y 228 dp (el máximo).
                EntryArFloatingCard(item = arSample("E-17840/26", ArItemKind.Caja, "AVISO", listOf("HECHA", "HECHA", "SIN_DATO", "EN_CURSO", "PENDIENTE"), listOf("HAZMAT")), offline = false, width = 156.dp, onClick = {})
                EntryArFloatingCard(item = arSample("01I26104549", ArItemKind.Relacion, "LISTO", listOf("HECHA", "HECHA", "HECHA", "HECHA", "EN_CURSO"), emptyList(), primary = true), offline = false, width = 228.dp, onClick = {})
                EntryArFloatingCard(item = arSample("E-17835/26", ArItemKind.Caja, "CERRADO", listOf("HECHA", "HECHA", "SIN_DATO", "HECHA", "HECHA"), emptyList()), offline = false, width = 156.dp, onClick = {})
                EntryArFloatingCard(item = arSample("E-17848/26", ArItemKind.Caja, "ATENCION", listOf("HECHA", "SIN_DATO", "SIN_DATO", "PENDIENTE", "PENDIENTE"), listOf("SIN_ENTRADA_VINCULADA")), offline = false, width = 156.dp, onClick = {})
                EntryArFloatingCard(item = arSample("E-99999/26", ArItemKind.Caja, "SIN_DATO", emptyList(), emptyList(), found = false), offline = false, width = 156.dp, onClick = {})
                EntryArFloatingCard(item = arSample("E-17840/26", ArItemKind.Caja, "AVISO", emptyList(), emptyList(), withCompact = false), offline = false, width = 156.dp, onClick = {})
                EntryArFloatingCard(item = arSample("01I26104549", ArItemKind.Relacion, "AVISO", emptyList(), emptyList(), withCompact = false), offline = true, width = 156.dp, onClick = {})
            }
            Spacer(Modifier.height(6.dp))
            Text("Marcas sobre el QR", fontSize = 11.sp, color = PmmTextSecondary)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF1F2937), RoundedCornerShape(12.dp))
                    .padding(start = 30.dp, top = 44.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(70.dp)
            ) {
                EntryArQrMarker(item = arSample("E-17840/26", ArItemKind.Caja, "AVISO", listOf("HECHA"), emptyList()), onClick = {})
                EntryArQrMarker(item = arSample("01I26104549", ArItemKind.Relacion, "LISTO", listOf("HECHA"), emptyList(), primary = true), onClick = {})
            }
            Spacer(Modifier.height(6.dp))
            Text("Hoja al tocar una marca", fontSize = 11.sp, color = PmmTextSecondary)
            EntryArCompactActionSheet(
                item = arSample("E-17840/26", ArItemKind.Caja, "AVISO", listOf("HECHA", "HECHA", "SIN_DATO", "EN_CURSO", "PENDIENTE"), listOf("ENTRADA_POR_RELACION", "HAZMAT")),
                offline = false,
                onOpenFicha = {}
            )
        }

        Section("Tipografía") {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("14", style = SignageType.Numeral, color = PmmTextPrimary)
                Spacer(Modifier.width(10.dp))
                Text("pendientes", fontSize = 12.sp, color = PmmTextSecondary)
            }
            Text("01C263582800000  3-12", style = SignageType.Code.copy(fontSize = 18.sp), color = PmmTextPrimary)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title.uppercase(),
            style = SignageType.Caption,
            color = PmmTextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/** Un QR de AR de mentira para ver su tarjeta, marca y hoja sin cámara. */
private fun arSample(
    code: String,
    kind: ArItemKind,
    signal: String,
    states: List<String>,
    flags: List<String>,
    found: Boolean = true,
    withCompact: Boolean = true,
    primary: Boolean = false
) = EntryArOverlayItem(
    key = code,
    code = EntryArQrCode(code, code, code, null, null, false),
    card = EntryArCardDto(reference = code, exists = false),
    kind = kind,
    compact = if (!withCompact) null else TraceCompactItemDto(
        code = code,
        kind = if (kind == ArItemKind.Caja) "CAJA" else "RELACION",
        found = found,
        signal = signal,
        states = states,
        flags = flags,
        customer = if (found) "KATHION CHEMIE DE MEXICO S. DE R.L. DE C.V." else "",
        box = "1657-2",
        relno = "01I26104549"
    ),
    isPrimary = primary
)
