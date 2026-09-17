package com.example.pmmovil.ui.revision

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.ui.components.PmmStatusBar
import com.example.pmmovil.ui.theme.PmmAmberDark
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmBlueMid
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

@Composable
fun ModoRevisionScreen(
    crefAgrupada: String,
    onNavigateBack: () -> Unit,
    onModoManual: () -> Unit,
    onModoBarcode: () -> Unit
) {
    PmmStatusBar(color = PmmNavyDark, darkIcons = false)

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            RevisionModeTopBar(
                crefAgrupada = crefAgrupada,
                onNavigateBack = onNavigateBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            RevisionContextPanel(crefAgrupada = crefAgrupada)

            WorkflowHintStrip()

            ModeActionCard(
                icon = Icons.Default.QrCodeScanner,
                title = "Conteo por Barcode",
                eyebrow = "Recomendado para conteo físico",
                description = "Escanea códigos con pistola o cámara. Ideal cuando la mercancía trae etiqueta y necesitas avanzar rápido.",
                accentColor = PmmBlueDeep,
                surfaceColor = PmmNavyVariant,
                chips = listOf("Escáner físico", "Cámara", "Conteo"),
                primary = true,
                actionText = "Usar Barcode",
                onClick = onModoBarcode
            )

            ModeActionCard(
                icon = Icons.AutoMirrored.Filled.Assignment,
                title = "Revisión Manual",
                eyebrow = "Control detallado",
                description = "Úsalo para validar facturas, revisar partidas, ajustar cantidades y documentar discrepancias.",
                accentColor = PmmGreenDark,
                surfaceColor = Color(0xFFEAF8EF),
                chips = listOf("Facturas", "Partidas", "Ajustes"),
                primary = false,
                actionText = "Usar Manual",
                onClick = onModoManual
            )

            ReviewReadinessPanel()
        }
    }
}

@Composable
private fun RevisionModeTopBar(
    crefAgrupada: String,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(PmmNavyDark, PmmBlueMid)
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Regresar",
                    tint = Color(0xFFF8FBFF)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Modo de revisión",
                    color = Color(0xFFF8FBFF),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
                Text(
                    text = crefAgrupada,
                    color = Color(0xFFDCE8FF),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RevisionContextPanel(crefAgrupada: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = PmmBlueDeep,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(PmmBlueDeep, PmmNavyDark)
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x1AF8FBFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFF8FBFF),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Entrada activa",
                        color = Color(0xFFCFE0FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = crefAgrupada.ifBlank { "Sin referencia" },
                        color = Color(0xFFF8FBFF),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusMiniPill(text = "Lista")
            }

            Text(
                text = "Selecciona el flujo antes de abrir la revisión. El modo elegido solo cambia cómo capturas el avance, no cambia la entrada.",
                color = Color(0xFFE6EEFF),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun WorkflowHintStrip() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFF7D6)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = PmmAmberDark,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Decisión rápida",
                    color = PmmTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "Barcode para contar; Manual para revisar documentos y diferencias.",
                    color = PmmTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ModeActionCard(
    icon: ImageVector,
    title: String,
    eyebrow: String,
    description: String,
    accentColor: Color,
    surfaceColor: Color,
    chips: List<String>,
    primary: Boolean,
    actionText: String,
    onClick: () -> Unit
) {
    val cardColor = if (primary) PmmBgSurface else Color(0xFFFBFDFF)
    val borderColor = if (primary) accentColor.copy(alpha = 0.28f) else PmmNavyBorder

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = if (primary) 5.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(surfaceColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eyebrow,
                        color = accentColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = title,
                        color = PmmTextPrimary,
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        color = PmmTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chips.forEach { chip ->
                    ModeChip(
                        text = chip,
                        accentColor = accentColor
                    )
                }
            }

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (primary) accentColor else Color(0xFFEAF1FF),
                    contentColor = if (primary) Color(0xFFF8FBFF) else PmmBlueDeep
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 13.dp)
            ) {
                Text(
                    text = actionText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ModeChip(
    text: String,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accentColor.copy(alpha = 0.09f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = accentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ReviewReadinessPanel() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = PmmBgSurface,
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Antes de iniciar",
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            ReadinessRow(
                icon = Icons.Default.Description,
                title = "Documentos",
                description = "Valida facturas y partidas dentro de la revisión."
            )
            ReadinessRow(
                icon = Icons.Default.Camera,
                title = "Evidencia",
                description = "Las fotos pendientes se conservan por referencia."
            )
            ReadinessRow(
                icon = Icons.Default.CheckCircle,
                title = "Cierre",
                description = "El sistema valida pendientes antes de finalizar."
            )
        }
    }
}

@Composable
private fun ReadinessRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PmmNavyVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PmmBlueLight,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = PmmTextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StatusMiniPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = Color(0x1AF8FBFF),
        border = BorderStroke(1.dp, Color(0x33F8FBFF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF83F3A3))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                color = Color(0xFFF8FBFF),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
