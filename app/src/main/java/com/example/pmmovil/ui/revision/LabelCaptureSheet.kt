package com.example.pmmovil.ui.revision

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pmmovil.R
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import java.io.File

/** La foto propone; la selección de partida, la edición y la revisión física requieren confirmación humana. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelCaptureSheet(
    estado: LabelCaptureViewModel.Estado,
    onAlternar: (Campo) -> Unit,
    onEdit: (Campo, String) -> Unit,
    onSelectPartida: (Int) -> Unit,
    onIdentityAccepted: (Boolean) -> Unit,
    onPhysicalReviewConfirmed: (Boolean) -> Unit,
    onSave: (Boolean) -> Unit,
    onImprove: () -> Unit,
    onRepetir: () -> Unit,
    onCerrar: () -> Unit,
    onRetry: () -> Unit,
    onReloadContext: () -> Unit,
    onDiscard: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDraft = estado.syncStatus == LabelCaptureStatus.DRAFT
    val busy = estado.analizando || estado.refinando || estado.loadingContext
    val editable = isDraft && estado.canEdit && !busy
    val unresolvedFields = estado.unresolvedConflicts.map { it.field }.toSet()

    ModalBottomSheet(onDismissRequest = onCerrar, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth()
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.92f)
            .navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_capture_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onCerrar) { Text(stringResource(R.string.label_capture_close)) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                CapturePhoto(estado.fotoPath)
                if (!isDraft) {
                    CaptureSyncResult(estado.syncStatus, estado.syncMessage ?: estado.error, onRetry, onReloadContext)
                } else {
                    Text(stringResource(R.string.label_capture_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(when {
                                estado.analizando -> R.string.label_capture_reading
                                estado.refinando -> R.string.label_capture_improving
                                else -> R.string.label_capture_loading_context
                            }), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    estado.error?.let { CaptureNotice(it, isError = true) }
                    CapturePartidaSelector(estado, enabled = !busy, onSelectPartida = onSelectPartida)
                    if (estado.selectedPartida != null) {
                        CaptureCheck(estado.identityAccepted, editable && estado.snapshot != null,
                            stringResource(R.string.label_capture_identity_check), onIdentityAccepted)
                    }
                    if (estado.conflicts.isNotEmpty()) {
                        Text(stringResource(R.string.label_capture_conflicts_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.label_capture_conflicts_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        estado.conflicts.forEach { conflict ->
                            CaptureConflict(conflict, conflict.field in estado.resolvedConflicts, editable) { onEdit(conflict.field, it) }
                        }
                    }
                    if (estado.campos.isEmpty() && !busy && estado.conflicts.isEmpty()) {
                        CaptureNotice(stringResource(R.string.label_capture_no_fields), isError = false)
                    }
                    if (estado.escribibles.isNotEmpty()) {
                        Text(stringResource(R.string.label_capture_fields_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.label_capture_fields_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        estado.escribibles.forEach { proposed ->
                            CaptureEditableField(proposed, estado.snapshot, editable && estado.snapshot != null,
                                proposed.leido.campo in unresolvedFields,
                                onToggle = { onAlternar(proposed.leido.campo) },
                                onEdit = { onEdit(proposed.leido.campo, it) })
                        }
                    }
                    val referenceFields = estado.campos.filterNot { it.escribible }
                    if (referenceFields.isNotEmpty()) {
                        HorizontalDivider()
                        Text(stringResource(R.string.label_capture_reference_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        referenceFields.forEach { CaptureReferenceField(it, estado.selectedPartida) }
                    }
                    if (!estado.canEdit && !busy && estado.selectedPartida != null) {
                        CaptureNotice(stringResource(R.string.label_capture_read_only), isError = false)
                    }
                    if (estado.snapshot != null && estado.selectedPartida != null) {
                        HorizontalDivider()
                        CaptureCheck(estado.physicalReviewConfirmed, editable && estado.canMarkReviewed,
                            stringResource(R.string.label_capture_physical_check), onPhysicalReviewConfirmed)
                        Text(stringResource(R.string.label_capture_physical_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (estado.textoCrudo.isNotBlank() || estado.rawCodes.isNotEmpty()) {
                        CaptureRawReading(estado.textoCrudo, estado.rawCodes)
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider()
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isDraft) {
                    Button(onClick = { onSave(true) }, enabled = estado.canComplete, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.label_capture_confirm_review))
                    }
                    OutlinedButton(onClick = { onSave(false) }, enabled = estado.canSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.label_capture_save_progress))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onImprove, enabled = !busy && estado.fotoPath != null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.label_capture_improve_action))
                        }
                        TextButton(onClick = onRepetir, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.label_capture_take_another))
                        }
                    }
                    TextButton(onClick = onDiscard, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.label_capture_discard), color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    Button(onClick = onCerrar, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.label_capture_close)) }
                }
            }
        }
    }
}

@Composable
private fun CapturePhoto(path: String?) {
    var expanded by remember(path) { mutableStateOf(false) }
    if (path.isNullOrBlank()) return
    AsyncImage(model = File(path), contentDescription = stringResource(R.string.label_capture_photo_description), contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth().height(if (expanded) 360.dp else 180.dp).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = stringResource(R.string.label_capture_photo_resize)) { expanded = !expanded })
}

@Composable
private fun CapturePartidaSelector(estado: LabelCaptureViewModel.Estado, enabled: Boolean, onSelectPartida: (Int) -> Unit) {
    var expanded by remember(estado.captureId) { mutableStateOf(false) }
    Text(stringResource(R.string.label_capture_partida_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    val selected = estado.selectedPartida
    val candidate = estado.candidates.firstOrNull { it.partida.IdRevdet == selected?.IdRevdet }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled && estado.availablePartidas.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(selected?.let { partidaTitle(it) } ?: stringResource(R.string.label_capture_choose_partida), maxLines = 2, overflow = TextOverflow.Ellipsis)
                selected?.cDescripcion?.takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.label_capture_no_partida)) }, onClick = { expanded = false; onSelectPartida(0) })
            estado.availablePartidas.forEach { partida ->
                DropdownMenuItem(text = {
                    Column {
                        Text(partidaTitle(partida), fontWeight = FontWeight.SemiBold)
                        partida.cDescripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                    }
                }, onClick = { expanded = false; onSelectPartida(partida.IdRevdet) })
            }
        }
    }
    if (candidate != null) {
        Text(stringResource(if (candidate.exactKeyMatch) R.string.label_capture_key_matches else R.string.label_capture_suggestion_only),
            style = MaterialTheme.typography.labelLarge,
            color = if (candidate.exactKeyMatch) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        candidate.reasons.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else if (selected != null) {
        Text(stringResource(R.string.label_capture_manual_selection), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (selected == null && estado.candidates.isNotEmpty()) {
        Text(stringResource(R.string.label_capture_candidates), style = MaterialTheme.typography.bodySmall)
        estado.candidates.take(3).forEach { suggestion ->
            OutlinedButton(onClick = { onSelectPartida(suggestion.partida.IdRevdet) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(partidaTitle(suggestion.partida), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CaptureEditableField(
    proposed: LabelCaptureViewModel.CampoPropuesto,
    snapshot: LabelCaptureSnapshot?,
    enabled: Boolean,
    unresolved: Boolean,
    onToggle: () -> Unit,
    onEdit: (String) -> Unit
) {
    val declaredValue = snapshotValue(snapshot, proposed.leido.campo).orEmpty().ifBlank {
        stringResource(if (snapshot == null) R.string.label_capture_context_unavailable else R.string.label_capture_no_declared_value)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = proposed.aceptado, enabled = enabled && !unresolved, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(fieldName(proposed.leido.campo), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(R.string.label_capture_declared, declaredValue), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(value = proposed.valorAEscribir, onValueChange = onEdit, enabled = enabled && !unresolved,
            modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_capture_observed_value)) }, minLines = 1, maxLines = 3,
            isError = unresolved || (proposed.aceptado && proposed.valorAEscribir.isBlank()),
            supportingText = { Text(stringResource(R.string.label_capture_original_value, proposed.leido.rawValue, readingSource(proposed.leido))) })
        if (proposed.destino == LabelCaptureViewModel.Destino.HAZMAT) {
            Text(stringResource(R.string.label_capture_hazmat_scope), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            proposed.detalle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun CaptureConflict(conflict: LabelFieldExtractor.FieldConflict, resolved: Boolean, enabled: Boolean, onChoose: (String) -> Unit) {
    Surface(color = if (resolved) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(fieldName(conflict.field), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(if (resolved) R.string.label_capture_conflict_resolved else R.string.label_capture_conflict_choose), style = MaterialTheme.typography.bodySmall)
            conflict.observations.distinctBy { it.valor }.forEach { reading ->
                OutlinedButton(onClick = { onChoose(reading.valor) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(reading.valor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                        Text(readingSource(reading), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptureReferenceField(proposed: LabelCaptureViewModel.CampoPropuesto, partida: PartidaRevisionDto?) {
    val reading = proposed.leido
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(fieldName(reading.campo), style = MaterialTheme.typography.labelLarge)
        Text(reading.rawValue, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        if (reading.quantityScope == LabelFieldExtractor.QuantityScope.PACKAGE_ORDINAL) {
            Text(stringResource(R.string.label_capture_package_ordinal, reading.valor, reading.packageTotal.orEmpty()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (reading.campo == Campo.CANTIDAD || reading.campo == Campo.PIEZAS_POR_EMPAQUE) {
            Text(stringResource(R.string.label_capture_quantity_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            partida?.let { Text(stringResource(R.string.label_capture_invoice_quantity, it.nCantFactura.toString(), it.UnidadFactura.orEmpty()), style = MaterialTheme.typography.bodySmall) }
        }
        Text(readingSource(reading), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CaptureCheck(checked: Boolean, enabled: Boolean, text: String, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled) { onCheckedChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = if (enabled) onCheckedChange else null, enabled = enabled)
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CaptureNotice(message: String, isError: Boolean) {
    Surface(color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(message, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CaptureSyncResult(status: String, message: String?, onRetry: () -> Unit, onReloadContext: () -> Unit) {
    Text(stringResource(when (status) {
        LabelCaptureStatus.CONFIRMED -> R.string.label_capture_confirmed_title
        LabelCaptureStatus.BLOCKED -> R.string.label_capture_blocked_title
        else -> R.string.label_capture_pending_title
    }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(stringResource(when (status) {
        LabelCaptureStatus.CONFIRMED -> R.string.label_capture_confirmed_help
        LabelCaptureStatus.BLOCKED -> R.string.label_capture_blocked_help
        else -> R.string.label_capture_pending_help
    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    message?.takeIf(String::isNotBlank)?.let { CaptureNotice(it, isError = status == LabelCaptureStatus.BLOCKED) }
    if (status == LabelCaptureStatus.BLOCKED) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.label_capture_retry)) }
        TextButton(onClick = onReloadContext, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.label_capture_reload)) }
    }
}

@Composable
private fun CaptureRawReading(rawText: String, rawCodes: List<String>) {
    var expanded by remember(rawText, rawCodes) { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.label_capture_hide_reading else R.string.label_capture_show_reading)) }
        if (expanded) {
            if (rawCodes.isNotEmpty()) {
                Text(stringResource(R.string.label_capture_codes_title), style = MaterialTheme.typography.labelLarge)
                rawCodes.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
            }
            if (rawText.isNotBlank()) Text(rawText, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

@Composable
private fun partidaTitle(partida: PartidaRevisionDto): String = stringResource(R.string.label_capture_partida_label,
    partida.Cnofact, partida.CnoParte.orEmpty().ifBlank { stringResource(R.string.label_capture_no_part_number) })

private fun snapshotValue(snapshot: LabelCaptureSnapshot?, field: Campo): String? = when (field) {
    Campo.LOTE -> snapshot?.numLote
    Campo.NUMERO_SERIE -> snapshot?.numSer
    Campo.MARCA -> snapshot?.marca
    Campo.MODELO -> snapshot?.modelo
    Campo.PAIS_ORIGEN -> snapshot?.cPaisOrigen
    Campo.UN_HAZMAT -> snapshot?.hazmatUn
    Campo.CLASE_HAZMAT -> snapshot?.hazmatClase
    Campo.GRUPO_EMBALAJE -> snapshot?.hazmatGrupo
    else -> null
}

@Composable
private fun fieldName(field: Campo): String = stringResource(when (field) {
    Campo.LOTE -> R.string.label_capture_field_lot
    Campo.NUMERO_SERIE -> R.string.label_capture_field_serial
    Campo.NUMERO_PARTE -> R.string.label_capture_field_part
    Campo.CANTIDAD -> R.string.label_capture_field_quantity
    Campo.PIEZAS_POR_EMPAQUE -> R.string.label_capture_field_package
    Campo.PAIS_ORIGEN -> R.string.label_capture_field_country
    Campo.CADUCIDAD -> R.string.label_capture_field_expiry
    Campo.UN_HAZMAT -> R.string.label_capture_field_un
    Campo.CLASE_HAZMAT -> R.string.label_capture_field_class
    Campo.GRUPO_EMBALAJE -> R.string.label_capture_field_group
    Campo.MARCA -> R.string.label_capture_field_brand
    Campo.MODELO -> R.string.label_capture_field_model
})

@Composable
private fun readingSource(reading: LabelFieldExtractor.CampoLeido): String = when (reading.origen) {
    LabelFieldExtractor.Origen.GS1 -> stringResource(R.string.label_capture_source_gs1, reading.ancla)
    LabelFieldExtractor.Origen.STRUCTURED_BARCODE -> stringResource(R.string.label_capture_source_code, reading.ancla)
    LabelFieldExtractor.Origen.KNOWN_BRAND -> stringResource(R.string.label_capture_source_brand)
    LabelFieldExtractor.Origen.ROMBO_HAZMAT -> stringResource(R.string.label_capture_source_hazmat)
    LabelFieldExtractor.Origen.ANCLA_LINEA_SIGUIENTE -> stringResource(R.string.label_capture_source_next_line, reading.ancla)
    LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA -> reading.ancla
}
