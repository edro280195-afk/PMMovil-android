package com.example.pmmovil.ui.home.components

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.R
import com.example.pmmovil.data.scanner.LastScanElapsed
import com.example.pmmovil.data.scanner.LastScanKind
import com.example.pmmovil.ui.components.SignalChip
import com.example.pmmovil.ui.home.HomeLastScanUi
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.SignageType

@StringRes
private fun kindLabel(kind: LastScanKind): Int = when (kind) {
    LastScanKind.Caja -> R.string.scan_kind_caja
    LastScanKind.Relacion -> R.string.scan_kind_relacion
    LastScanKind.Carpeta -> R.string.scan_kind_carpeta
    LastScanKind.Bulto -> R.string.scan_kind_bulto
}

@Composable
private fun elapsedText(elapsed: LastScanElapsed): String = when (elapsed) {
    LastScanElapsed.JustNow -> stringResource(R.string.home_new_last_just_now)
    is LastScanElapsed.Minutes -> stringResource(R.string.home_new_last_minutes, elapsed.count)
    is LastScanElapsed.Hours -> stringResource(R.string.home_new_last_hours, elapsed.count)
}

/**
 * «Último escaneo»: el atajo para volver a lo que se acaba de leer sin escanearlo otra vez. Tocarla abre su ficha, que
 * se pide de nuevo al servidor: la píldora solo recuerda qué era.
 */
@Composable
fun HomeLastScanPill(state: HomeLastScanUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.home_new_last_title)
    val kind = stringResource(kindLabel(state.scan.kind))
    val ago = elapsedText(state.elapsed)
    val signalWord = state.signal?.let { stringResource(it.wordRes) }
    val spoken = buildString {
        append(stringResource(R.string.home_new_last_spoken, kind, state.scan.code, ago))
        if (signalWord != null) append(" $signalWord.")
        append(" ").append(stringResource(R.string.home_new_last_spoken_open))
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                role = Role.Button
            },
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 56.dp)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "${title.uppercase()} · $ago",
                    color = PmmTextSecondary,
                    style = SignageType.Caption.copy(fontSize = 9.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = kind,
                        color = PmmBlueDeep,
                        style = SignageType.Caption.copy(fontSize = 9.5.sp),
                        maxLines = 1
                    )
                    Text(
                        text = state.scan.code,
                        modifier = Modifier.weight(1f, fill = false),
                        color = PmmTextPrimary,
                        fontFamily = SignageType.Mono,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            state.signal?.let { SignalChip(kind = it) }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PmmTextMuted
            )
        }
    }
}
