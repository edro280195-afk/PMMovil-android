package com.example.pmmovil.ui.scanactions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.data.model.TraceResultDto
import com.example.pmmovil.ui.components.SignalKind
import com.example.pmmovil.ui.components.SignalShape
import com.example.pmmovil.ui.components.SkeletonLine
import com.example.pmmovil.ui.components.StationNode
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.SignageType

/** Los datos de la etiqueta (cliente, línea, caja…), uno por renglón; los vacíos no salen. */
@Composable
internal fun TraceFields(result: TraceResultDto, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val fields = traceFields(result) { id, args -> resources.getString(id, *args.toTypedArray()) }
    if (fields.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        fields.forEach { field ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Text(
                    text = stringResource(field.label),
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.width(96.dp)
                )
                Text(
                    text = field.value,
                    color = PmmTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** «Ojo con esto»: lo que la ficha encontró y conviene saber antes de actuar (daño, sin relación, discrepancias…). */
@Composable
internal fun TraceFlags(flags: List<Int>, modifier: Modifier = Modifier) {
    if (flags.isEmpty()) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(1.dp, PmmAmberDark.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.trace_section_flags).uppercase(),
                color = PmmAmberDark,
                style = SignageType.Caption
            )
            flags.forEach { flag ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                    SignalShape(kind = SignalKind.Atencion, size = 16.dp, modifier = Modifier.padding(top = 1.dp))
                    Text(text = stringResource(flag), color = PmmTextPrimary, fontSize = 14.sp)
                }
            }
        }
    }
}

/**
 * La Línea en vertical: cinco estaciones unidas por un hilo, cada una con su estado y, si se sabe, cuándo pasó. Cada
 * nodo dice lo mismo con forma, símbolo y palabra, nunca solo con color.
 */
@Composable
internal fun StationTimeline(rows: List<StationRowUi>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            val isLast = index == rows.lastIndex
            val name = stringResource(row.station.nameRes)
            val stateText = stringResource(row.state.descriptionRes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .semantics(mergeDescendants = true) {
                        contentDescription = listOfNotNull(name, stateText, row.at).joinToString(", ")
                    }
            ) {
                Column(
                    modifier = Modifier
                        .width(26.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StationNode(number = index + 1, state = row.state, size = 26.dp)
                    if (!isLast) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .weight(1f)
                                .background(PmmNavyBorder)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp, bottom = if (isLast) 0.dp else 14.dp)
                ) {
                    Text(
                        text = name,
                        color = PmmTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = listOfNotNull(stateText, row.at).joinToString(" · "),
                        color = PmmTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

/** Las piezas que se relacionan con lo leído; tocar una abre su propia ficha. */
@Composable
internal fun RelatedList(
    rows: List<RelatedRowUi>,
    onOpenCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (rows.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            val type = stringResource(row.typeRes)
            val status = row.statusRes?.let { stringResource(it) } ?: row.statusText
            val openDescription = stringResource(R.string.trace_related_open, row.code)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = openDescription },
                shape = RoundedCornerShape(12.dp),
                color = androidx.compose.ui.graphics.Color.White,
                border = BorderStroke(1.dp, PmmNavyBorder),
                onClick = { onOpenCode(row.code) }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(text = type.uppercase(), color = PmmTextSecondary, style = SignageType.Caption)
                        Text(
                            text = row.code,
                            color = PmmTextPrimary,
                            fontFamily = SignageType.Mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!status.isNullOrBlank()) {
                            Text(text = status, color = PmmTextSecondary, fontSize = 12.sp)
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = PmmTextSecondary
                    )
                }
            }
        }
    }
}

/** Mientras llega la ficha: el lugar de La Línea y de las acciones, en gris, sin mover nada cuando lleguen. */
@Composable
internal fun TraceLoading(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.trace_loading)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(3) {
            SkeletonLine(Modifier.fillMaxWidth())
        }
        SkeletonLine(Modifier.fillMaxWidth(0.6f))
    }
}
