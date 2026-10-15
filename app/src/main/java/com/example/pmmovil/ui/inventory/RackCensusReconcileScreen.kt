package com.example.pmmovil.ui.inventory

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.inventory.RackCensusMovementKind
import com.example.pmmovil.data.inventory.RackCensusMovementReview
import com.example.pmmovil.data.inventory.RackCensusNotSeenDecision
import com.example.pmmovil.data.inventory.RackCensusPackageFinding
import com.example.pmmovil.data.inventory.RackCensusResolution
import com.example.pmmovil.data.inventory.RackCensusUnidentifiedFinding
import com.example.pmmovil.data.model.RackCensusStatus
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Conciliación. Las cuatro categorías se presentan separadas porque significan
 * cosas distintas:
 *
 * - **Coincidencias**: esperado aquí y visto aquí.
 * - **En otra ubicación**: visto, pero se esperaba en otro lado. Deja constancia;
 *   no reescribe `trafdet.ubicacion`, que es de la partida completa.
 * - **No vistos**: sin lectura. No es lo mismo que faltante, y exige decidir.
 * - **Sin identificar**: se leyó algo que no corresponde a ningún bulto.
 *
 * Arriba de todas va la compuerta de movimientos: lo que cambió durante el
 * recorrido se revisa antes de conciliar, y una lectura nunca revierte una
 * salida ya registrada.
 */
@Composable
internal fun RackCensusReconcileContent(
    state: RackCensusUiState,
    viewModel: RackCensusViewModel,
    modifier: Modifier = Modifier
) {
    val report = state.report
    val session = state.session

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (report == null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Armando la conciliación...", fontSize = 13.sp, color = PmmTextSecondary)
                    }
                }
                return@LazyColumn
            }

            item { CountsSummary(report) }

            if (report.dataWarnings.isNotEmpty()) {
                item {
                    SectionCard("Etiquetas fuera del censo") {
                        report.dataWarnings.forEach {
                            Text("• $it", fontSize = 13.sp, color = PmmTextSecondary)
                        }
                    }
                }
            }

            item {
                MovementGate(
                    movements = report.movements,
                    isChecking = state.isCheckingMovements,
                    error = state.movementsError,
                    onRefresh = { viewModel.checkMovements() },
                    onAcknowledge = viewModel::acknowledgeMovement
                )
            }

            bucketSection(
                title = "Coincidencias",
                subtitle = "Esperado aquí y visto aquí.",
                accent = PmmGreen,
                findings = report.matched
            )

            bucketSection(
                title = "Encontrados en otra ubicación",
                subtitle = "Se vieron, pero el inventario los esperaba en otro lado. " +
                    "Queda el hallazgo registrado; la ubicación de la partida no se reescribe.",
                accent = PmmAmber,
                findings = report.foundElsewhere
            )

            if (report.notSeen.isNotEmpty()) {
                item {
                    SectionCard("No vistos (${report.notSeen.size})") {
                        Text(
                            text = "No haberlos visto no demuestra que falten. Confirma el faltante solo si te " +
                                "haces responsable de ese registro; si no, déjalo pendiente a propósito.",
                            fontSize = 12.sp, color = PmmTextSecondary
                        )
                    }
                }
                items(report.notSeen, key = { "not-seen-${it.packageId}" }) { finding ->
                    NotSeenRow(finding, viewModel::decide)
                }
            }

            if (report.unidentified.isNotEmpty()) {
                item {
                    SectionCard("Sin identificar (${report.unidentified.size})") {
                        Text(
                            text = "Códigos leídos que no corresponden a ninguna etiqueta del inventario. " +
                                "No se cuentan como bultos vistos ni se adivina a qué pertenecen.",
                            fontSize = 12.sp, color = PmmTextSecondary
                        )
                    }
                }
                items(report.unidentified, key = { "unknown-${it.normalizedCode}" }) { finding ->
                    UnidentifiedRow(finding)
                }
            }

            if (report.adjustments.isNotEmpty()) {
                item {
                    SectionCard("Lo que se va a registrar") {
                        report.adjustments.forEach { adjustment ->
                            Text(
                                text = "• ${adjustment.packageId}: ${adjustment.description}",
                                fontSize = 12.sp, color = PmmTextSecondary
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "El censo registra observaciones y hallazgos. No mueve mercancía ni descuenta " +
                                "existencias: eso sigue siendo una decisión de bodega con su propio flujo.",
                            fontSize = 12.sp, color = PmmTextMuted
                        )
                    }
                }
            }

            if (report.blockReasons.isNotEmpty()) {
                item {
                    Surface(
                        color = PmmRed.copy(alpha = 0.08f), shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Falta antes de conciliar", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PmmRed)
                            Spacer(Modifier.height(6.dp))
                            report.blockReasons.forEach {
                                Text("• $it", fontSize = 12.sp, color = PmmTextPrimary)
                            }
                        }
                    }
                }
            }
        }

        Surface(color = PmmBgSurface, shadowElevation = 8.dp) {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                when (session?.status) {
                    RackCensusStatus.RECONCILING -> StatusLine(
                        "Conciliación enviada. Se reintenta con el mismo identificador hasta confirmarla.",
                        PmmBlueDeep
                    )
                    RackCensusStatus.RECONCILED -> StatusLine("Censo conciliado en el servidor.", PmmGreen)
                    RackCensusStatus.BLOCKED -> StatusLine(
                        session.errorMessage ?: "La conciliación no se aceptó. Corrige y vuelve a enviarla.",
                        PmmRed
                    )
                    else -> Unit
                }
                Spacer(Modifier.height(if (session?.status == RackCensusStatus.OPEN) 0.dp else 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (session?.status == RackCensusStatus.BLOCKED ||
                        session?.status == RackCensusStatus.RECONCILING
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.retryReconciliation() },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text(
                                text = if (session.status == RackCensusStatus.BLOCKED) "Corregir y reenviar"
                                else "Reintentar ahora",
                                fontSize = 14.sp
                            )
                        }
                    }
                    Button(
                        onClick = { viewModel.confirmReconciliation() },
                        enabled = state.canReconcile && report?.canConfirm == true &&
                            session?.status == RackCensusStatus.OPEN && state.pendingSync == 0,
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PmmGreen)
                    ) {
                        Text(
                            text = when {
                                !state.canReconcile -> "Sin permiso para conciliar"
                                state.pendingSync > 0 -> "${state.pendingSync} lecturas por subir"
                                else -> "Conciliar el censo"
                            },
                            fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(message: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(message, fontSize = 12.sp, color = PmmTextSecondary)
    }
}

@Composable
private fun CountsSummary(report: com.example.pmmovil.data.inventory.RackCensusReport) {
    SectionCard("Resumen del recorrido") {
        val counts = report.counts
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SummaryLine("Bultos esperados en el alcance", counts.expectedInScope.toString(), PmmTextPrimary)
            SummaryLine("Identidades distintas observadas", counts.uniqueObservedPackages.toString(), PmmGreen)
            SummaryLine("Coincidencias", counts.matched.toString(), PmmGreen)
            SummaryLine("En otra ubicación", counts.foundElsewhere.toString(), PmmAmber)
            SummaryLine("No vistos", counts.notSeen.toString(), PmmTextSecondary)
            SummaryLine("Faltantes confirmados", counts.missingConfirmed.toString(), PmmRed)
            SummaryLine("Códigos sin identificar", counts.unidentifiedCodes.toString(), PmmRed)
            SummaryLine("Lecturas totales", counts.totalScans.toString(), PmmTextMuted)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Los conteos salen de identidades físicas distintas. El «14/17» impreso en una etiqueta es " +
                "su posición dentro de un total declarado, no una prueba de cuántos bultos se vieron.",
            fontSize = 11.sp, color = PmmTextMuted
        )
    }
}

@Composable
private fun SummaryLine(label: String, value: String, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = PmmTextSecondary, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
    }
}

@Composable
private fun MovementGate(
    movements: List<RackCensusMovementReview>,
    isChecking: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onAcknowledge: (String, Boolean) -> Unit
) {
    SectionCard("Movimientos durante el recorrido") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    isChecking -> "Consultando movimientos..."
                    error != null -> error
                    movements.isEmpty() -> "No se detectaron movimientos posteriores a la instantánea."
                    else -> "Hay ${movements.size} bulto(s) que cambiaron después de abrir el censo. " +
                        "Revísalos: una lectura no revierte una salida ya registrada."
                },
                fontSize = 13.sp,
                color = if (error != null) PmmRed else PmmTextSecondary,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onRefresh, enabled = !isChecking) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Revisar", fontSize = 13.sp)
            }
        }
        movements.forEach { movement ->
            Spacer(Modifier.height(8.dp))
            Surface(
                color = if (movement.acknowledged) PmmGreen.copy(alpha = 0.07f) else PmmAmber.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SwapHoriz, contentDescription = null,
                        tint = if (movement.acknowledged) PmmGreen else PmmAmber, modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            movement.packageId, fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold, color = PmmTextPrimary
                        )
                        Text(movementLabel(movement), fontSize = 11.sp, color = PmmTextSecondary)
                        if (movement.wasObserved) {
                            Text(
                                text = "Se leyó durante el recorrido. Queda la observación; el movimiento no se revierte.",
                                fontSize = 11.sp, color = PmmTextMuted
                            )
                        }
                    }
                    TextButton(onClick = { onAcknowledge(movement.packageId, !movement.acknowledged) }) {
                        Text(
                            text = if (movement.acknowledged) "Deshacer" else "Revisado",
                            fontSize = 13.sp,
                            color = if (movement.acknowledged) PmmTextSecondary else PmmBlueDeep
                        )
                    }
                }
            }
        }
    }
}

private fun movementLabel(movement: RackCensusMovementReview): String = when (movement.kind) {
    RackCensusMovementKind.LOCATION_CHANGED ->
        "Cambió de ubicación: ${movement.fromLocationId.orEmpty()} → ${movement.toLocationId.orEmpty()}"
    RackCensusMovementKind.LEFT_WAREHOUSE -> "Quedó cargado o salió después de la instantánea."
    RackCensusMovementKind.LABEL_REMOVED -> "Su etiqueta ya no existe en el sistema."
    RackCensusMovementKind.STATUS_CHANGED -> "Cambió de estatus después de la instantánea."
}

private fun androidx.compose.foundation.lazy.LazyListScope.bucketSection(
    title: String,
    subtitle: String,
    accent: Color,
    findings: List<RackCensusPackageFinding>
) {
    item {
        SectionCard("$title (${findings.size})") {
            Text(subtitle, fontSize = 12.sp, color = PmmTextSecondary)
            if (findings.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text("Sin casos.", fontSize = 12.sp, color = PmmTextMuted)
            }
        }
    }
    items(findings, key = { "$title-${it.packageId}" }) { finding ->
        FindingRow(finding, accent)
    }
}

@Composable
private fun FindingRow(finding: RackCensusPackageFinding, accent: Color) {
    Surface(
        color = PmmBgSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    finding.packageId, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold, color = PmmTextPrimary
                )
                Text(
                    text = buildString {
                        append("Esperado en ${finding.expectedLocationId}")
                        if (finding.observedLocationIds.isNotEmpty()) {
                            append(" · visto en ${finding.observedLocationIds.sorted().joinToString(", ")}")
                        }
                    },
                    fontSize = 11.sp, color = PmmTextSecondary
                )
                if (finding.descripcion.isNotBlank() || finding.cliente.isNotBlank()) {
                    Text(
                        text = listOf(finding.cliente, finding.descripcion).filter { it.isNotBlank() }
                            .joinToString(" · "),
                        fontSize = 11.sp, color = PmmTextMuted
                    )
                }
                finding.declaredTotal?.let { total ->
                    Text(
                        text = "Etiqueta impresa: ${finding.printedOrdinal ?: "?"} de $total (dato de la etiqueta)",
                        fontSize = 10.sp, color = PmmTextMuted
                    )
                }
                if (finding.scanCount > 1) {
                    Text(
                        text = "${finding.scanCount} lecturas de la misma identidad; cuenta una vez.",
                        fontSize = 10.sp, color = PmmTextMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun NotSeenRow(
    finding: RackCensusPackageFinding,
    onDecide: (String, RackCensusNotSeenDecision) -> Unit
) {
    Surface(
        color = PmmBgSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            1.dp,
            when (finding.decision) {
                RackCensusNotSeenDecision.CONFIRM_MISSING -> PmmRed
                RackCensusNotSeenDecision.KEEP_PENDING -> PmmAmber
                RackCensusNotSeenDecision.UNDECIDED -> PmmNavyBorder
            }
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                finding.packageId, fontSize = 14.sp, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold, color = PmmTextPrimary
            )
            Text("Esperado en ${finding.expectedLocationId}", fontSize = 11.sp, color = PmmTextSecondary)
            if (finding.cliente.isNotBlank() || finding.descripcion.isNotBlank()) {
                Text(
                    text = listOf(finding.cliente, finding.descripcion).filter { it.isNotBlank() }.joinToString(" · "),
                    fontSize = 11.sp, color = PmmTextMuted
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecisionChip(
                    label = "Confirmo faltante",
                    selected = finding.decision == RackCensusNotSeenDecision.CONFIRM_MISSING,
                    accent = PmmRed,
                    icon = Icons.Default.ErrorOutline,
                    modifier = Modifier.weight(1f)
                ) {
                    onDecide(
                        finding.packageId,
                        if (finding.decision == RackCensusNotSeenDecision.CONFIRM_MISSING)
                            RackCensusNotSeenDecision.UNDECIDED else RackCensusNotSeenDecision.CONFIRM_MISSING
                    )
                }
                DecisionChip(
                    label = "Dejar pendiente",
                    selected = finding.decision == RackCensusNotSeenDecision.KEEP_PENDING,
                    accent = PmmAmber,
                    icon = Icons.Default.CheckCircle,
                    modifier = Modifier.weight(1f)
                ) {
                    onDecide(
                        finding.packageId,
                        if (finding.decision == RackCensusNotSeenDecision.KEEP_PENDING)
                            RackCensusNotSeenDecision.UNDECIDED else RackCensusNotSeenDecision.KEEP_PENDING
                    )
                }
            }
            if (finding.decision == RackCensusNotSeenDecision.CONFIRM_MISSING) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Se registra el faltante a tu nombre. No descuenta existencia.",
                    fontSize = 11.sp, color = PmmRed
                )
            }
        }
    }
}

@Composable
private fun DecisionChip(
    label: String,
    selected: Boolean,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else PmmNavyBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) accent.copy(alpha = 0.10f) else Color.Transparent
        )
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else PmmTextMuted, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = if (selected) accent else PmmTextSecondary)
    }
}

@Composable
private fun UnidentifiedRow(finding: RackCensusUnidentifiedFinding) {
    Surface(
        color = PmmBgSurface, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PmmRed))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    finding.normalizedCode.ifBlank { "(lectura ilegible)" },
                    fontSize = 14.sp, fontFamily = FontFamily.Monospace, color = PmmTextPrimary
                )
                Text(
                    text = when {
                        finding.pendingOnlineResolution ->
                            "Fuera del catálogo que cupo en el equipo. Falta resolverlo con el servidor."
                        finding.resolution == RackCensusResolution.FOLDER_LABEL ->
                            "Es la etiqueta de carpeta de la referencia, no un bulto."
                        finding.resolution == RackCensusResolution.MALFORMED ->
                            "La lectura no tiene la forma de una etiqueta IMEX."
                        else -> "Ninguna etiqueta vigente tiene este código impreso."
                    },
                    fontSize = 11.sp, color = PmmTextSecondary
                )
                if (finding.occurrences > 1) {
                    Text("${finding.occurrences} lecturas del mismo código.", fontSize = 10.sp, color = PmmTextMuted)
                }
            }
        }
    }
}
