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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
 * Lo que se puede hacer con un código leído: la etiqueta con su tipo y, debajo, solo las acciones que el puesto del
 * usuario permite. Leer nunca registra nada; cada acción abre una pantalla que ya existe con el código cargado.
 *
 * @param onAction recibe la ruta de la pantalla elegida; quien la llama decide cómo navegar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanActionsScreen(
    onNavigateBack: () -> Unit,
    onAction: (route: String) -> Unit,
    viewModel: ScanActionsViewModel = hiltViewModel()
) {
    val state = viewModel.state
    val code = state.code

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
                CodeLabel(code)

                if (state.actions.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.scan_actions_section).uppercase(),
                            color = PmmTextSecondary,
                            style = SignageType.Caption
                        )
                        state.actions.forEach { action ->
                            ActionButton(action = action, onClick = { onAction(action.route) })
                        }
                    }
                } else if (ScanActionResolver.actionsFor(code).isNotEmpty()) {
                    // El código sí lleva a algo, pero este puesto no puede hacerlo: se dice, no se esconde.
                    Text(
                        text = stringResource(R.string.scan_actions_none),
                        color = PmmTextSecondary,
                        fontSize = 14.sp
                    )
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
private fun CodeLabel(code: ScannedCode) {
    val signal = when (code) {
        is ScannedCode.Candidato, is ScannedCode.Desconocido -> SignalKind.SinDato
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
                Text(text = kindHint(code), color = PmmTextSecondary, fontSize = 14.sp)
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
