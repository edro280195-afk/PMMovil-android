package com.example.pmmovil.ui.revision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.preferences.ModePreferences
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Selector único para abrir una entrada en modo normal o por códigos de barras.
 *
 * Se usa tanto desde la lista de asignaciones como desde la bandeja de
 * notificaciones para que ambos flujos presenten exactamente la misma decisión.
 */
@Composable
fun RevisionModeSelectorContent(
    crefAgrupada: String,
    contextLabel: String,
    supportingText: String? = null,
    facturas: String? = null,
    partidas: String? = null,
    preferredMode: ModePreferences.Mode,
    isOpening: Boolean,
    onModeSelected: (ModePreferences.Mode) -> Unit,
    onBack: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Entrada seleccionada",
                    color = PmmTextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = crefAgrupada.ifBlank { "Sin referencia" },
                    color = PmmTextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contextLabel.ifBlank { "Cliente no disponible" },
                    color = PmmTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                supportingText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { text ->
                        Text(
                            text = text,
                            color = PmmTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
            }

            onBack?.let { goBack ->
                IconButton(onClick = goBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Regresar a notificaciones",
                        tint = PmmBlueDeep
                    )
                }
            }
        }

        if (facturas != null && partidas != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RevisionSheetMetric(
                    modifier = Modifier.weight(1f),
                    label = "Facturas",
                    value = facturas
                )
                RevisionSheetMetric(
                    modifier = Modifier.weight(1f),
                    label = "Partidas",
                    value = partidas
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "¿Cómo quieres revisar esta entrada?",
            color = PmmTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "Puedes cambiar de modo más adelante si el proceso lo requiere.",
            color = PmmTextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(2.dp))

        RevisionModeOption(
            title = "Revisión normal",
            description = "Factura por factura, con cantidades, series y discrepancias.",
            icon = Icons.AutoMirrored.Filled.Assignment,
            accent = PmmBlueDeep,
            recommended = preferredMode == ModePreferences.Mode.NORMAL,
            enabled = !isOpening,
            onClick = { onModeSelected(ModePreferences.Mode.NORMAL) }
        )
        RevisionModeOption(
            title = "Por códigos de barras",
            description = "Escaneo rápido con pistola o cámara para productos identificados.",
            icon = Icons.Default.QrCodeScanner,
            accent = PmmAmberDark,
            recommended = preferredMode == ModePreferences.Mode.BARCODE,
            enabled = !isOpening,
            onClick = { onModeSelected(ModePreferences.Mode.BARCODE) }
        )

        if (isOpening) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = PmmBlueDeep,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Preparando la revisión…",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RevisionSheetMetric(
    modifier: Modifier,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = PmmNavyVariant
    ) {
        Column(modifier = Modifier.padding(13.dp)) {
            Text(
                text = label,
                color = PmmTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                color = PmmBlueDeep,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun RevisionModeOption(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    recommended: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 92.dp),
        shape = RoundedCornerShape(18.dp),
        color = if (recommended) Color(0xFFEFF6FF) else Color(0xFFFFFFFF),
        border = BorderStroke(
            1.5.dp,
            if (recommended) accent.copy(alpha = 0.55f) else Color(0xFFE2E8F0)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(accent.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(25.dp)
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = PmmTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (recommended) {
                        Spacer(Modifier.width(7.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = accent.copy(alpha = 0.13f)
                        ) {
                            Text(
                                text = "PREFERIDO",
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                color = accent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    color = PmmTextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Abrir $title",
                tint = accent,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}
