package com.example.pmmovil.ui.scanactions

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.R
import com.example.pmmovil.data.scanner.ScanAction
import com.example.pmmovil.data.scanner.ScanActionId
import com.example.pmmovil.data.scanner.ScanActionResolver
import com.example.pmmovil.data.scanner.ScannedCode
import com.example.pmmovil.ui.components.LabelCard
import com.example.pmmovil.ui.components.SignalChip
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import com.example.pmmovil.ui.theme.SignageType

/**
 * La ficha de un código leído: la etiqueta con su tipo y sus datos, lo que conviene saber, las acciones que el estado de
 * la pieza y el puesto del usuario permiten, La Línea (en qué punto del recorrido va) y las piezas relacionadas. Leer
 * nunca registra nada; cada acción abre una pantalla que ya existe con el código cargado.
 *
 * La ficha viene del servidor. Si no hay red se muestra lo que dice el código y las acciones que se deducen de él.
 *
 * @param onAction recibe la ruta de la pantalla elegida; quien la llama decide cómo navegar.
 * @param onOpenCode recibe el código de una pieza relacionada (la caja, la relación, una entrada) para abrir su ficha.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanActionsScreen(
    onNavigateBack: () -> Unit,
    onAction: (route: String) -> Unit,
    onOpenCode: (code: String) -> Unit,
    viewModel: ScanActionsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val code = state.code
    val trace = state.trace

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text(stringResource(R.string.scan_actions_title), fontWeight = FontWeight.Black)
                            Text(
                                stringResource(R.string.scan_actions_subtitle),
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.scan_actions_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PmmNavyDark,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CodeLabel(code = code, trace = trace)

                val result = (trace as? TraceUiState.Ready)?.result
                if (result != null) TraceFlags(flags = knownFlags(result))

                when (trace) {
                    TraceUiState.Loading -> TraceLoading()

                    TraceUiState.NotFound -> Text(
                        text = stringResource(R.string.trace_not_found),
                        color = PmmTextSecondary,
                        fontSize = 14.sp
                    )

                    is TraceUiState.Ready, TraceUiState.Unavailable, TraceUiState.NotRequested -> {
                        if (trace == TraceUiState.Unavailable) {
                            UnavailableNote(onRetry = viewModel::retry)
                        }
                        ActionsSection(
                            actions = state.actions,
                            // Con ficha, «sin acciones» es que el estado no permite ninguna; sin ella, que el puesto no puede.
                            emptyText = when {
                                result != null -> R.string.trace_no_actions
                                ScanActionResolver.actionsFor(code).isNotEmpty() -> R.string.scan_actions_none
                                else -> null
                            },
                            onAction = onAction
                        )
                    }
                }

                if (result != null) {
                    SectionTitle(R.string.trace_section_line)
                    StationTimeline(rows = stationRows(result))

                    val related = relatedRows(result)
                    if (related.isNotEmpty()) {
                        SectionTitle(R.string.trace_section_related)
                        RelatedList(rows = related, onOpenCode = onOpenCode)
                    }
                }

                Text(
                    text = stringResource(R.string.scan_actions_read_only),
                    color = PmmTextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CodeLabel(code: ScannedCode, trace: TraceUiState) {
    val result = (trace as? TraceUiState.Ready)?.result
    // Con ficha, la señal la dice el servidor; sin ella, lo único que se sabe es si el código tiene forma de algo.
    val signal = when {
        result != null -> signalKindOf(result.signal)
        trace == TraceUiState.NotFound -> SignalKind.SinDato
        code is ScannedCode.Candidato || code is ScannedCode.Desconocido -> SignalKind.SinDato
        else -> SignalKind.Aviso
    }
    LabelCard(
        kindLabel = stringResource(kindLabel(code)),
        body = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .semantics(mergeDescendants = true) { },
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // El gafete no tiene valor que mostrar: nunca se conserva.
                if (code.normalized.isNotEmpty()) {
                    Text(
                        text = code.normalized,
                        color = PmmTextPrimary,
                        style = SignageType.Code.copy(
                            fontSize = if (code.normalized.length > 16) 20.sp else 26.sp,
                            lineHeight = if (code.normalized.length > 16) 22.sp else 28.sp
                        ),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result != null) {
                    TraceFields(result = result, modifier = Modifier.padding(top = 4.dp))
                } else {
                    Text(text = kindHint(code), color = PmmTextSecondary, fontSize = 14.sp)
                }
            }
        },
        stub = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SignalChip(kind = signal)
            }
        }
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text).uppercase(),
        color = PmmTextSecondary,
        style = SignageType.Caption
    )
}

/** Las acciones disponibles; la principal en amarillo. Sin ninguna, dice por qué en vez de dejar un hueco. */
@Composable
private fun ActionsSection(
    actions: List<ScanAction>,
    @StringRes emptyText: Int?,
    onAction: (route: String) -> Unit
) {
    if (actions.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(R.string.scan_actions_section)
            actions.forEach { action ->
                ActionButton(action = action, onClick = { onAction(action.route) })
            }
        }
    } else if (emptyText != null) {
        Text(text = stringResource(emptyText), color = PmmTextSecondary, fontSize = 14.sp)
    }
}

/** Aviso de que no se pudo consultar al servidor, con la salida a la mano. */
@Composable
private fun UnavailableNote(onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.trace_unavailable),
            color = PmmTextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.trace_retry), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionButton(action: ScanAction, onClick: () -> Unit) {
    val containerColor = if (action.primary) PmmYellow else Color.White
    val contentColor = if (action.primary) PmmBlueDeep else PmmTextPrimary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = if (action.primary) null else BorderStroke(1.dp, PmmNavyBorder),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(actionTitle(action.id)),
                    color = contentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(actionDetail(action.id)),
                    color = if (action.primary) PmmBlueDeep else PmmTextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = contentColor)
        }
    }
}

/** Rótulo del tipo de código («CAJA», «BULTO»…); también lo usa el escáner universal sobre el visor. */
@StringRes
internal fun kindLabel(code: ScannedCode): Int = when (code) {
    is ScannedCode.Caja -> R.string.scan_kind_caja
    is ScannedCode.Relacion -> R.string.scan_kind_relacion
    is ScannedCode.Carpeta -> R.string.scan_kind_carpeta
    is ScannedCode.Bulto -> R.string.scan_kind_bulto
    is ScannedCode.PreRegistro -> R.string.scan_kind_preregistro
    ScannedCode.Gafete -> R.string.scan_kind_gafete
    is ScannedCode.ProductoCliente -> R.string.scan_kind_producto
    is ScannedCode.Candidato -> R.string.scan_kind_candidato
    is ScannedCode.Desconocido -> R.string.scan_kind_desconocido
}

@Composable
private fun kindHint(code: ScannedCode): String = when (code) {
    is ScannedCode.Caja -> stringResource(R.string.scan_hint_caja)
    is ScannedCode.Relacion -> stringResource(R.string.scan_hint_relacion)
    is ScannedCode.Carpeta -> stringResource(R.string.scan_hint_carpeta)
    is ScannedCode.Bulto -> stringResource(R.string.scan_hint_bulto, code.ordinal, code.total)
    is ScannedCode.PreRegistro -> stringResource(R.string.scan_hint_preregistro)
    ScannedCode.Gafete -> stringResource(R.string.scan_hint_gafete)
    is ScannedCode.ProductoCliente -> stringResource(R.string.scan_hint_producto)
    is ScannedCode.Candidato -> stringResource(R.string.scan_hint_candidato)
    is ScannedCode.Desconocido -> stringResource(R.string.scan_hint_desconocido)
}

@StringRes
private fun actionTitle(id: ScanActionId): Int = when (id) {
    ScanActionId.CreateEntry -> R.string.scan_action_create_entry
    ScanActionId.ExitGate -> R.string.scan_action_exit_gate
    ScanActionId.FindInYard -> R.string.scan_action_find_in_yard
    ScanActionId.OpenRelation -> R.string.scan_action_open_relation
    ScanActionId.OpenEntry -> R.string.scan_action_open_entry
    ScanActionId.ReviewEntry -> R.string.scan_action_review_entry
    ScanActionId.ShowOnMap -> R.string.scan_action_show_on_map
    ScanActionId.RegisterPreRegistro -> R.string.scan_action_register_prereg
}

@StringRes
private fun actionDetail(id: ScanActionId): Int = when (id) {
    ScanActionId.CreateEntry -> R.string.scan_action_create_entry_detail
    ScanActionId.ExitGate -> R.string.scan_action_exit_gate_detail
    ScanActionId.FindInYard -> R.string.scan_action_find_in_yard_detail
    ScanActionId.OpenRelation -> R.string.scan_action_open_relation_detail
    ScanActionId.OpenEntry -> R.string.scan_action_open_entry_detail
    ScanActionId.ReviewEntry -> R.string.scan_action_review_entry_detail
    ScanActionId.ShowOnMap -> R.string.scan_action_show_on_map_detail
    ScanActionId.RegisterPreRegistro -> R.string.scan_action_register_prereg_detail
}
