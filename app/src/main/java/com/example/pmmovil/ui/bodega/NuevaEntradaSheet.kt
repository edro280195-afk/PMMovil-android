package com.example.pmmovil.ui.bodega

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Modal de «Registrar nueva entrada»: pregunta cómo empezar sin llevar al usuario a una pantalla aparte. Es una hoja
 * inferior, igual que la del detalle de una entrada en este mismo listado (en tableta Material la centra y la limita a
 * 640 dp de ancho).
 *
 * @param onScan lee primero el QR de caseta: la captura abre directo el lector.
 * @param onManual abre la captura vacía.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NuevaEntradaSheet(
    onScan: () -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = PmmBgSurface,
        dragHandle = {
            Surface(
                modifier = Modifier.padding(vertical = 11.dp),
                color = PmmTextMuted.copy(alpha = 0.4f),
                shape = RoundedCornerShape(5.dp)
            ) {
                Spacer(modifier = Modifier.size(width = 36.dp, height = 4.dp))
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "¿Cómo quieres empezar?",
                    color = PmmTextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Elige una forma de capturar la entrada",
                    color = PmmTextSecondary,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(2.dp))
            NuevaEntradaOption(
                icon = Icons.Default.QrCodeScanner,
                title = "Escanear QR de caseta",
                description = "Carga cliente, transporte, caja y planta",
                onClick = onScan
            )
            NuevaEntradaOption(
                icon = Icons.Default.Keyboard,
                title = "Capturar manual",
                description = "Llena la entrada desde cero",
                onClick = onManual
            )
        }
    }
}

@Composable
private fun NuevaEntradaOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = PmmBgSurface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.5.dp, PmmNavyBorder),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PmmNavyVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PmmNavyDark, modifier = Modifier.size(30.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                Text(description, color = PmmTextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PmmTextMuted
            )
        }
    }
}
