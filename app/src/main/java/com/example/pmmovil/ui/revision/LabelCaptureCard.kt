package com.example.pmmovil.ui.revision

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.pmmovil.R
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LabelFieldExtractor.Campo
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Decision
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Fila
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Identidad
import com.example.pmmovil.ui.revision.LabelCaptureCotejo.Tipo
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/** Lo que dura el «listo» antes de volver a la cámara para la siguiente etiqueta. */
private const val AVANCE_MILLIS = 1100L

/**
 * La foto propone y el revisador confirma. Una sola tarjeta con lo leído cotejado contra lo declarado:
 * si todo cuadra, un toque; solo las diferencias piden una decisión.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelCaptureCard(
    estado: LabelCaptureViewModel.Estado,
    onSelectPartida: (Int) -> Unit,
    onRejectPartida: () -> Unit,
    onAcceptIdentity: () -> Unit,
    onDecide: (Campo, Decision) -> Unit,
    onEdit: (Campo, String) -> Unit,
    onSave: (Boolean) -> Unit,
    onImprove: () -> Unit,
    onRetake: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onReloadContext: () -> Unit,
    onDiscard: () -> Unit,
    onNext: () -> Unit,
    /**
     * Lo que contar esta etiqueta deja en la partida cuando la cuenta queda **abierta** (faltan empaques o se
     * pasó): mientras exista, el botón principal guarda el avance y no marca la partida como revisada.
     */
    conteoAbierto: PackageTally.Result.Added? = null,
    /** La foto trae dos etiquetas distintas: no se ofrece guardar, se pide otra con una sola. */
    variasEtiquetas: Boolean = false
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isDraft = estado.syncStatus == LabelCaptureStatus.DRAFT
    val busy = estado.analizando || estado.refinando || estado.loadingContext
    val filas = remember(estado) { LabelCaptureCotejo.comparar(estado) }
    val pendientes = LabelCaptureCotejo.pendientes(filas)
    val guardando = estado.envio == LabelCaptureViewModel.Envio.GUARDANDO
    val guardada = estado.envio == LabelCaptureViewModel.Envio.GUARDADO && estado.syncStatus != LabelCaptureStatus.BLOCKED
    val sinDatos = isDraft && !busy && estado.campos.isEmpty() && estado.conflicts.isEmpty()
    var mostrarLectura by remember(estado.captureId) { mutableStateOf(false) }

    // Si el servidor rechaza la captura mientras se muestra el «listo», no se avanza: se enseña el motivo.
    val siguiente by rememberUpdatedState(onNext)
    LaunchedEffect(guardada, estado.captureId) {
        if (guardada) {
            delay(AVANCE_MILLIS)
            siguiente()
        }
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        // Con menos del 90 % la hoja no llega a pisar la barra de estado cuando hay muchas filas.
        Column(
            Modifier.fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.86f)
                .navigationBarsPadding().imePadding()
        ) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when {
                    guardando -> EnvioEnCurso()
                    guardada -> EnvioListo(estado)
                    !isDraft -> {
                        FotoMiniatura(estado.fotoPath, grande = true, onToggle = {})
                        ResultadoDeEnvio(estado.syncStatus, estado.syncMessage ?: estado.error, onRetry, onReloadContext)
                    }
                    estado.analizando -> {
                        FotoMiniatura(estado.fotoPath, grande = true, onToggle = {})
                        Progreso(stringResource(R.string.label_capture_reading))
                    }
                    estado.selectedPartida == null -> VistaSinPartida(
                        estado = estado, menu = {
                            MenuOpciones(estado, busy, sinDatos, mostrarLectura, { mostrarLectura = !mostrarLectura },
                                onImprove, onSave, onDiscard)
                        }, onSelectPartida = onSelectPartida
                    )
                    else -> VistaCotejo(
                        estado = estado, filas = filas, busy = busy, sinDatos = sinDatos,
                        menu = {
                            MenuOpciones(estado, busy, sinDatos, mostrarLectura, { mostrarLectura = !mostrarLectura },
                                onImprove, onSave, onDiscard, guardarPermitido = !variasEtiquetas)
                        },
                        onAcceptIdentity = onAcceptIdentity, onDecide = onDecide, onEdit = onEdit, onReloadContext = onReloadContext
                    )
                }
                if (mostrarLectura && isDraft) LecturaOriginal(estado.textoCrudo, estado.rawCodes)
                Spacer(Modifier.height(12.dp))
            }
            if (!guardando && !guardada) {
                HorizontalDivider()
                Pie(
                    estado = estado, isDraft = isDraft, busy = busy, sinDatos = sinDatos, pendientes = pendientes,
                    conteoAbierto = conteoAbierto, variasEtiquetas = variasEtiquetas,
                    onSave = onSave, onRetake = onRetake, onRejectPartida = onRejectPartida, onClose = onClose
                )
            }
        }
    }
}

// ---- Vistas ----

@Composable
private fun VistaSinPartida(
    estado: LabelCaptureViewModel.Estado,
    menu: @Composable () -> Unit,
    onSelectPartida: (Int) -> Unit
) {
    var fotoGrande by remember(estado.captureId) { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!fotoGrande) FotoMiniatura(estado.fotoPath, grande = false, onToggle = { fotoGrande = true })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(if (estado.campos.isEmpty() && estado.conflicts.isEmpty()) R.string.label_capture_nothing_read_title
                else R.string.label_capture_unknown_title),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(if (estado.campos.isEmpty() && estado.conflicts.isEmpty()) R.string.label_capture_nothing_read_help
                else R.string.label_capture_unknown_help),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        menu()
    }
    if (fotoGrande) FotoMiniatura(estado.fotoPath, grande = true, onToggle = { fotoGrande = false })
    if (estado.refinando) Progreso(stringResource(R.string.label_capture_improving))
    estado.error?.takeIf { estado.campos.isNotEmpty() || estado.conflicts.isNotEmpty() }?.let { Aviso(it, esError = true) }

    val sugeridas = estado.candidates.filter { it.partida.IdRevdet !in estado.rechazadas }.take(3)
    sugeridas.forEach { sugerencia ->
        OpcionDePartida(sugerencia.partida, sugerencia.reasons.firstOrNull()) { onSelectPartida(sugerencia.partida.IdRevdet) }
    }
    val sugeridasIds = sugeridas.map { it.partida.IdRevdet }.toSet()
    val otras = estado.availablePartidas.filter { it.IdRevdet !in sugeridasIds }
    if (otras.isNotEmpty()) {
        var todas by remember(estado.captureId) { mutableStateOf(sugeridas.isEmpty()) }
        if (todas) {
            otras.forEach { OpcionDePartida(it, null) { onSelectPartida(it.IdRevdet) } }
        } else {
            TextButton(onClick = { todas = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.label_capture_see_all, otras.size))
            }
        }
    }
}

@Composable
private fun VistaCotejo(
    estado: LabelCaptureViewModel.Estado,
    filas: List<Fila>,
    busy: Boolean,
    sinDatos: Boolean,
    menu: @Composable () -> Unit,
    onAcceptIdentity: () -> Unit,
    onDecide: (Campo, Decision) -> Unit,
    onEdit: (Campo, String) -> Unit,
    onReloadContext: () -> Unit
) {
    val partida = requireNotNull(estado.selectedPartida)
    var fotoGrande by remember(estado.captureId) { mutableStateOf(false) }
    var editando by remember(estado.captureId) { mutableStateOf<Campo?>(null) }
    val identidad = remember(estado.campos, partida) { LabelCaptureCotejo.identidad(estado.campos, partida) }

    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!fotoGrande) FotoMiniatura(estado.fotoPath, grande = false, onToggle = { fotoGrande = true })
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                partida.cDescripcion?.takeIf(String::isNotBlank) ?: tituloDePartida(partida),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(tituloDePartida(partida), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val (texto, color) = when {
                !estado.identityAccepted -> stringResource(R.string.label_capture_pill_unverified) to PmmAmberDark
                identidad == Identidad.CLAVE_EXACTA -> stringResource(R.string.label_capture_pill_exact) to PmmGreenDark
                else -> stringResource(R.string.label_capture_pill_chosen) to PmmBlueDeep
            }
            Pastilla(texto, color)
        }
        menu()
    }
    if (fotoGrande) FotoMiniatura(estado.fotoPath, grande = true, onToggle = { fotoGrande = false })

    when {
        estado.loadingContext -> Progreso(stringResource(R.string.label_capture_loading_context))
        estado.snapshot == null -> {
            estado.error?.let { Aviso(it, esError = true) }
            OutlinedButton(onClick = onReloadContext, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.label_capture_retry))
            }
        }
        sinDatos -> Aviso(stringResource(R.string.label_capture_nothing_read_selected), esError = false)
        else -> {
            if (estado.refinando) Progreso(stringResource(R.string.label_capture_improving))
            estado.error?.let { Aviso(it, esError = true) }
            Text(stringResource(R.string.label_capture_compare_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Surface(
                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, PmmNavyBorder)
            ) {
                Column {
                    filas.forEachIndexed { index, fila ->
                        if (index > 0) HorizontalDivider(color = PmmNavyBorder)
                        FilaDeCotejo(
                            fila = fila,
                            lectura = estado.campos.firstOrNull { it.leido.campo == fila.campo }?.leido,
                            identidadAceptada = estado.identityAccepted,
                            habilitada = estado.canEdit && !busy,
                            editando = editando == fila.campo,
                            onToggleEdicion = { editando = if (editando == fila.campo) null else fila.campo },
                            onDecide = { onDecide(fila.campo, it) },
                            onEdit = { onEdit(fila.campo, it) },
                            onAcceptIdentity = onAcceptIdentity
                        )
                    }
                }
            }
            // La fila de cantidad ya compara con la factura; la línea suelta solo queda para lo que no se compara
            // (número de bulto o contenido por empaque).
            if (filas.none { it.campo == Campo.CANTIDAD }) LineaDeCantidad(estado, partida)
            if (filas.any { it.campo in CAMPOS_PELIGROSOS }) {
                Aviso(stringResource(R.string.label_capture_hazmat_scope), esError = false)
            }
            Resumen(filas)
            if (!estado.canEdit) {
                Aviso(estado.error ?: stringResource(R.string.label_capture_read_only), esError = false)
            } else if (!estado.canMarkReviewed) {
                Aviso(stringResource(R.string.label_capture_cannot_mark), esError = false)
            }
        }
    }
}

@Composable
private fun FilaDeCotejo(
    fila: Fila,
    lectura: LabelFieldExtractor.CampoLeido?,
    identidadAceptada: Boolean,
    habilitada: Boolean,
    editando: Boolean,
    onToggleEdicion: () -> Unit,
    onDecide: (Decision) -> Unit,
    onEdit: (String) -> Unit,
    onAcceptIdentity: () -> Unit
) {
    val esParte = fila.campo == Campo.NUMERO_PARTE
    val parteSinConfirmar = esParte && !identidadAceptada && fila.tipo != Tipo.COINCIDE
    val atencion = fila.pendiente || parteSinConfirmar
    val textoCantidad = fila.cantidad?.let { textoDeCantidad(it) }
    val (glifo, colorGlifo) = when {
        fila.tipo == Tipo.COINCIDE -> "✓" to PmmGreen
        fila.tipo == Tipo.NUEVO -> "+" to PmmBlueMid
        fila.tipo == Tipo.PARCIAL -> "…" to PmmBlueMid
        fila.tipo == Tipo.DIFERENTE && (esParte && identidadAceptada || fila.decision != null) -> "✓" to PmmGreen
        fila.tipo == Tipo.DIFERENTE -> "≠" to PmmAmber
        fila.tipo == Tipo.CONFLICTO -> "?" to PmmAmber
        fila.tipo == Tipo.NO_VALIDO -> "!" to PmmRed
        else -> "–" to PmmTextMuted
    }
    val sinLectura = stringResource(if (esParte) R.string.label_capture_row_not_read else R.string.label_capture_row_no_data)
    val elegirLectura = stringResource(R.string.label_capture_conflict_pick)
    val tachado = SpanStyle(textDecoration = TextDecoration.LineThrough, color = PmmTextMuted, fontWeight = FontWeight.Normal)
    val visible = if (fila.campo == Campo.PAIS_ORIGEN) fila.detalle ?: fila.leido else fila.leido
    val valor = buildAnnotatedString {
        if (textoCantidad != null) {
            append(textoCantidad)
            return@buildAnnotatedString
        }
        when (fila.tipo) {
            Tipo.COINCIDE -> append(visible)
            Tipo.PARCIAL -> append(visible)
            Tipo.NUEVO -> {
                if (fila.suma) append("+ ") else { withStyle(tachado) { append("—") }; append(" ") }
                append(visible)
            }
            Tipo.DIFERENTE -> when {
                !fila.escribible -> { withStyle(tachado) { append(fila.declarado.ifBlank { "—" }) }; append(" "); append(fila.leido) }
                fila.decision == Decision.USAR -> append(visible)
                fila.decision == Decision.CONSERVAR -> append(fila.declarado)
                else -> { withStyle(tachado) { append(fila.declarado) }; append(" "); append(visible) }
            }
            Tipo.NO_VALIDO -> append(fila.leido)
            Tipo.CONFLICTO -> append(elegirLectura)
            Tipo.SIN_LECTURA -> withStyle(SpanStyle(color = PmmTextMuted, fontWeight = FontWeight.Normal)) { append(sinLectura) }
        }
    }

    Column(
        Modifier.fillMaxWidth()
            .background(if (atencion) PmmAmber.copy(alpha = 0.12f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(colorGlifo.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { Text(glifo, color = colorGlifo, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        nombreDeCampo(fila.campo) + if (!fila.escribible) " · " + stringResource(R.string.label_capture_row_reference_only) else "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (fila.escribible && fila.tipo != Tipo.CONFLICTO) {
                        Text(" · ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            stringResource(if (editando) R.string.label_capture_row_fix_close else R.string.label_capture_row_fix),
                            style = MaterialTheme.typography.labelMedium, color = PmmBlueDeep, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(enabled = habilitada, onClick = onToggleEdicion)
                                .heightIn(min = 32.dp).padding(vertical = 6.dp)
                        )
                    }
                }
                Text(valor, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                when {
                    fila.suma -> Nota(stringResource(R.string.label_capture_row_adds_to, fila.declarado))
                    fila.tipo == Tipo.PARCIAL -> Nota(stringResource(R.string.label_capture_qty_partial_note))
                    fila.tipo == Tipo.NO_VALIDO -> Nota(stringResource(R.string.label_capture_row_invalid, fila.detalle.orEmpty()))
                    esParte && fila.detalle != null -> Nota(fila.detalle)
                    esParte && fila.tipo != Tipo.COINCIDE && identidadAceptada -> Nota(stringResource(R.string.label_capture_identity_confirmed))
                }
            }
        }
        if (parteSinConfirmar) {
            Button(
                onClick = onAcceptIdentity, enabled = habilitada,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) { Text(stringResource(R.string.label_capture_confirm_identity)) }
        }
        if (fila.tipo == Tipo.DIFERENTE && fila.escribible) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BotonDeDecision(
                    stringResource(R.string.label_capture_keep_value, fila.declarado), fila.decision == Decision.CONSERVAR,
                    habilitada, Modifier.weight(1f)
                ) { onDecide(Decision.CONSERVAR) }
                BotonDeDecision(
                    stringResource(R.string.label_capture_use_value, visible), fila.decision == Decision.USAR,
                    habilitada, Modifier.weight(1f)
                ) { onDecide(Decision.USAR) }
            }
        }
        if (fila.tipo == Tipo.CONFLICTO) {
            fila.opciones.forEach { opcion ->
                OutlinedButton(
                    onClick = { onEdit(opcion) }, enabled = habilitada,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) { Text(opcion, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        if (editando && fila.escribible) EditorDeValor(fila, lectura, habilitada, onEdit)
    }
}

@Composable
private fun EditorDeValor(
    fila: Fila,
    lectura: LabelFieldExtractor.CampoLeido?,
    habilitada: Boolean,
    onEdit: (String) -> Unit
) {
    // El texto vive aquí: si viniera del estado, un país escrito a medias se reescribiría al validarse.
    var texto by remember(fila.campo) { mutableStateOf(fila.leido) }
    OutlinedTextField(
        value = texto, onValueChange = { texto = it; onEdit(it) }, enabled = habilitada, singleLine = true,
        modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.label_capture_edit_label)) },
        supportingText = {
            Text(
                listOfNotNull(
                    lectura?.let { stringResource(R.string.label_capture_original_value, it.rawValue, descripcionDeLectura(it)) },
                    stringResource(R.string.label_capture_edit_hint)
                ).joinToString("\n")
            )
        }
    )
}

@Composable
private fun BotonDeDecision(texto: String, elegido: Boolean, habilitado: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val contenido: @Composable () -> Unit = {
        Text(texto, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge)
    }
    if (elegido) {
        Button(onClick = onClick, enabled = habilitado, modifier = modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PmmGreenDark)) { contenido() }
    } else {
        OutlinedButton(onClick = onClick, enabled = habilitado, modifier = modifier.heightIn(min = 48.dp)) { contenido() }
    }
}

@Composable
private fun LineaDeCantidad(estado: LabelCaptureViewModel.Estado, partida: PartidaRevisionDto) {
    val cantidad = LabelCaptureCotejo.cantidadEtiqueta(estado.campos) ?: return
    val factura = formatoDeCantidad(partida.nCantFactura)
    val unidad = partida.UnidadFactura.orEmpty()
    val texto = if (cantidad.bulto != null) {
        stringResource(R.string.label_capture_package_ordinal, cantidad.bulto, cantidad.bultoTotal.orEmpty()) +
            " " + stringResource(R.string.label_capture_invoice_quantity, factura, unidad)
    } else {
        stringResource(R.string.label_capture_quantity_ref, factura, unidad, cantidad.texto)
    }
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Resumen(filas: List<Fila>) {
    // Solo baja la inicial: «Número UN» queda «número UN», no «número un».
    val guarda = LabelCaptureCotejo.aGuardar(filas).map { nombreDeCampo(it).replaceFirstChar { c -> c.lowercase(Locale.getDefault()) } }
    val conserva = LabelCaptureCotejo.sinCambio(filas).map { nombreDeCampo(it).replaceFirstChar { c -> c.lowercase(Locale.getDefault()) } } +
        stringResource(R.string.label_capture_summary_invoice_quantity)
    val tituloGuarda = stringResource(R.string.label_capture_summary_saves)
    val tituloConserva = stringResource(R.string.label_capture_summary_keeps)
    val nadaNuevo = stringResource(R.string.label_capture_summary_saves_nothing)
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tituloGuarda) }
                append(" ")
                append(if (guarda.isEmpty()) nadaNuevo else guarda.joinToString(", "))
                append(". ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(tituloConserva) }
                append(" ")
                append(conserva.joinToString(", "))
                append(".")
            },
            Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium
        )
    }
}

// ---- Pie ----

@Composable
private fun Pie(
    estado: LabelCaptureViewModel.Estado,
    isDraft: Boolean,
    busy: Boolean,
    sinDatos: Boolean,
    pendientes: Int,
    conteoAbierto: PackageTally.Result.Added?,
    variasEtiquetas: Boolean,
    onSave: (Boolean) -> Unit,
    onRetake: () -> Unit,
    onRejectPartida: () -> Unit,
    onClose: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (!isDraft) {
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text(stringResource(R.string.label_capture_close))
            }
            return@Column
        }
        // Con la cuenta abierta (faltan empaques o se pasó) no se ofrece marcar la partida como revisada.
        val confirma = estado.canMarkReviewed && conteoAbierto == null && !variasEtiquetas
        val (texto, habilitado, alTocar) = when {
            busy -> Triple(stringResource(R.string.label_capture_primary_busy), false, {})
            // Dos etiquetas en la foto: sus datos se mezclarían, así que no se guarda; se pide otra con una sola.
            variasEtiquetas -> Triple(stringResource(R.string.label_capture_take_another), true, onRetake)
            estado.selectedPartida == null && sinDatos -> Triple(stringResource(R.string.label_capture_take_another), true, onRetake)
            estado.selectedPartida == null -> Triple(stringResource(R.string.label_capture_primary_pick), false, {})
            estado.snapshot == null -> Triple(stringResource(R.string.label_capture_primary_no_context), false, {})
            sinDatos -> Triple(stringResource(R.string.label_capture_take_another), true, onRetake)
            !estado.canEdit -> Triple(stringResource(R.string.label_capture_primary_read_only), false, {})
            !estado.identityAccepted -> Triple(stringResource(R.string.label_capture_primary_identity), false, {})
            pendientes == 1 -> Triple(stringResource(R.string.label_capture_primary_resolve_one), false, {})
            pendientes > 1 -> Triple(stringResource(R.string.label_capture_primary_resolve_many, pendientes), false, {})
            confirma -> Triple(stringResource(R.string.label_capture_primary_confirm), true, { onSave(true) })
            else -> Triple(stringResource(R.string.label_capture_primary_save), true, { onSave(false) })
        }
        if (variasEtiquetas && !busy) {
            Text(
                stringResource(R.string.label_capture_several_labels),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
        if (conteoAbierto != null && habilitado && !sinDatos && !variasEtiquetas) {
            val kilos = conteoAbierto.dimension == CountUnits.Dimension.KILOS
            Text(
                stringResource(
                    if (kilos) R.string.label_capture_count_open_kg else R.string.label_capture_count_open_pieces,
                    LabelCaptureCotejo.numero(conteoAbierto.totalAfter), LabelCaptureCotejo.numero(conteoAbierto.expected)
                ),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
        }
        Button(
            onClick = alTocar, enabled = habilitado,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = if (confirma && !sinDatos) PmmGreenDark else MaterialTheme.colorScheme.primary)
        ) { Text(texto, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
        if (confirma && estado.selectedPartida != null && estado.snapshot != null && !sinDatos) {
            Text(
                stringResource(R.string.label_capture_fine_print), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            )
        }
        // Con «no se leyó nada» el botón principal ya es «Tomar otra foto»: no se repite abajo.
        if (estado.selectedPartida != null || !sinDatos) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                if (estado.selectedPartida != null) {
                    TextButton(onClick = onRejectPartida, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.label_capture_not_this))
                    }
                }
                if (!sinDatos) {
                    TextButton(onClick = onRetake, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.label_capture_take_another))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuOpciones(
    estado: LabelCaptureViewModel.Estado,
    busy: Boolean,
    sinDatos: Boolean,
    mostrandoLectura: Boolean,
    onToggleLectura: () -> Unit,
    onImprove: () -> Unit,
    onSave: (Boolean) -> Unit,
    onDiscard: () -> Unit,
    guardarPermitido: Boolean = true
) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { abierto = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.label_capture_menu_more))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_capture_improve_action)) },
                enabled = !busy && estado.fotoPath != null,
                onClick = { abierto = false; onImprove() }
            )
            if (estado.canMarkReviewed || sinDatos) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (sinDatos) R.string.label_capture_menu_save_photo else R.string.label_capture_menu_save_only)) },
                    enabled = estado.canSave && guardarPermitido,
                    onClick = { abierto = false; onSave(false) }
                )
            }
            if (estado.textoCrudo.isNotBlank() || estado.rawCodes.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text(stringResource(if (mostrandoLectura) R.string.label_capture_hide_reading else R.string.label_capture_show_reading)) },
                    onClick = { abierto = false; onToggleLectura() }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.label_capture_discard), color = MaterialTheme.colorScheme.error) },
                enabled = !busy,
                onClick = { abierto = false; onDiscard() }
            )
        }
    }
}

// ---- Envío ----

@Composable
private fun EnvioEnCurso() {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CircularProgressIndicator()
        Text(stringResource(R.string.label_capture_saving), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EnvioListo(estado: LabelCaptureViewModel.Estado) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(PmmGreen), contentAlignment = Alignment.Center) {
            Text("✓", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(if (estado.marcadaRevisada) R.string.label_capture_success_reviewed else R.string.label_capture_success_saved),
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
        )
        estado.selectedPartida?.let {
            Text(it.cDescripcion?.takeIf(String::isNotBlank) ?: tituloDePartida(it), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        Text(
            stringResource(R.string.label_capture_success_help), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultadoDeEnvio(status: String, message: String?, onRetry: () -> Unit, onReloadContext: () -> Unit) {
    Text(
        stringResource(
            when (status) {
                LabelCaptureStatus.CONFIRMED -> R.string.label_capture_confirmed_title
                LabelCaptureStatus.BLOCKED -> R.string.label_capture_blocked_title
                else -> R.string.label_capture_pending_title
            }
        ), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
    )
    Text(
        stringResource(
            when (status) {
                LabelCaptureStatus.CONFIRMED -> R.string.label_capture_confirmed_help
                LabelCaptureStatus.BLOCKED -> R.string.label_capture_blocked_help
                else -> R.string.label_capture_pending_help
            }
        ), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    message?.takeIf(String::isNotBlank)?.let { Aviso(it, esError = status == LabelCaptureStatus.BLOCKED) }
    if (status == LabelCaptureStatus.BLOCKED) {
        OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.label_capture_retry)) }
        TextButton(onClick = onReloadContext, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(R.string.label_capture_reload)) }
    }
}

// ---- Piezas ----

@Composable
private fun OpcionDePartida(partida: PartidaRevisionDto, motivo: String?, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, PmmNavyBorder), modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                partida.cDescripcion?.takeIf(String::isNotBlank) ?: tituloDePartida(partida),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                tituloDePartida(partida) + " · " + formatoDeCantidad(partida.nCantFactura) + " " + partida.UnidadFactura.orEmpty(),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            motivo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PmmBlueDeep, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FotoMiniatura(path: String?, grande: Boolean, onToggle: () -> Unit) {
    if (path.isNullOrBlank()) return
    AsyncImage(
        model = File(path), contentDescription = stringResource(R.string.label_capture_photo_description),
        contentScale = if (grande) ContentScale.Fit else ContentScale.Crop,
        modifier = (if (grande) Modifier.fillMaxWidth().height(220.dp) else Modifier.size(64.dp))
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = stringResource(R.string.label_capture_photo_resize), onClick = onToggle)
    )
}

@Composable
private fun Pastilla(texto: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(texto, Modifier.padding(horizontal = 10.dp, vertical = 3.dp), style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Nota(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Aviso(mensaje: String, esError: Boolean) {
    Surface(color = if (esError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
        Text(mensaje, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Progreso(mensaje: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(mensaje, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LecturaOriginal(texto: String, codigos: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (codigos.isNotEmpty()) {
            Text(stringResource(R.string.label_capture_codes_title), style = MaterialTheme.typography.labelLarge)
            codigos.forEach { Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
        }
        if (texto.isNotBlank()) Text(texto, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}

@Composable
private fun tituloDePartida(partida: PartidaRevisionDto): String = stringResource(
    R.string.label_capture_partida_label, partida.Cnofact,
    partida.CnoParte.orEmpty().ifBlank { stringResource(R.string.label_capture_no_part_number) }
)

@Composable
private fun nombreDeCampo(campo: Campo): String = stringResource(
    when (campo) {
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
        Campo.PESO_NETO -> R.string.label_capture_field_weight
        Campo.PESO_BRUTO -> R.string.label_capture_field_gross_weight
    }
)

/** «210 pzas = factura 2.1 CNT», «200 de 9820 pzas», etc.: la etiqueta y la factura en la misma unidad. */
@Composable
private fun textoDeCantidad(c: LabelCaptureCotejo.Cantidad): String {
    val etiqueta = LabelCaptureCotejo.numero(c.etiqueta) + " " + c.unidadBase
    val declaradaEnBase = LabelCaptureCotejo.numero(c.declaradaEnBase) + " " + c.unidadBase
    val factura = LabelCaptureCotejo.numero(c.declaradaFactura) + " " + c.unidadFactura
    return when (c.resultado) {
        LabelCaptureCotejo.ResultadoCantidad.IGUAL -> stringResource(R.string.label_capture_qty_equal, etiqueta, factura)
        LabelCaptureCotejo.ResultadoCantidad.PARCIAL -> stringResource(R.string.label_capture_qty_partial, etiqueta, declaradaEnBase)
        LabelCaptureCotejo.ResultadoCantidad.MAYOR -> stringResource(R.string.label_capture_qty_more, etiqueta, declaradaEnBase)
        LabelCaptureCotejo.ResultadoCantidad.NO_COMPARABLE ->
            stringResource(R.string.label_capture_qty_not_comparable, LabelCaptureCotejo.numero(c.etiqueta), factura)
    }
}

@Composable
private fun descripcionDeLectura(lectura: LabelFieldExtractor.CampoLeido): String = when (lectura.origen) {
    LabelFieldExtractor.Origen.CODIGO_PLANO -> stringResource(R.string.label_capture_source_barcode)
    LabelFieldExtractor.Origen.GS1 -> stringResource(R.string.label_capture_source_gs1, lectura.ancla)
    LabelFieldExtractor.Origen.STRUCTURED_BARCODE -> stringResource(R.string.label_capture_source_code, lectura.ancla)
    LabelFieldExtractor.Origen.KNOWN_BRAND -> stringResource(R.string.label_capture_source_brand)
    LabelFieldExtractor.Origen.ROMBO_HAZMAT -> stringResource(R.string.label_capture_source_hazmat)
    LabelFieldExtractor.Origen.ANCLA_LINEA_SIGUIENTE -> stringResource(R.string.label_capture_source_next_line, lectura.ancla)
    LabelFieldExtractor.Origen.ANCLA_MISMA_LINEA -> lectura.ancla
}

private fun formatoDeCantidad(valor: Double): String =
    if (valor % 1.0 == 0.0) valor.toLong().toString() else String.format(Locale.US, "%.2f", valor)

private val CAMPOS_PELIGROSOS = setOf(Campo.UN_HAZMAT, Campo.CLASE_HAZMAT, Campo.GRUPO_EMBALAJE)
