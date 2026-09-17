package com.example.pmmovil.ui.salida.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.SalidaRelacion
import com.example.pmmovil.ui.salida.SalidaCaptureMode
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmGreenDark
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmNavyVariant
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow

private val MonoFamily = FontFamily.Monospace

/**
 * Top App Bar institucional PMM con controles flotantes de escáner y usuario.
 */
@Composable
fun SalidaTopHeader(
    titulo: String,
    usuario: String,
    captureMode: SalidaCaptureMode,
    onToggleCaptureMode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PmmNavyDark,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Botón retroceso minimalista
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // PMM Brand Badge compacto
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(PmmYellow)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PMM",
                    color = PmmNavyDark,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Título compacto
            Text(
                text = titulo,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(Modifier.width(6.dp))

            // Selector Rápido de Modo de Captura (Zebra / Cámara)
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onToggleCaptureMode),
                color = if (captureMode == SalidaCaptureMode.CAMERA) PmmYellow else Color.White.copy(alpha = 0.15f),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (captureMode == SalidaCaptureMode.CAMERA) PmmYellow else Color.White.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (captureMode == SalidaCaptureMode.CAMERA) Icons.Filled.CameraAlt else Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        tint = if (captureMode == SalidaCaptureMode.CAMERA) PmmNavyDark else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (captureMode == SalidaCaptureMode.CAMERA) "Cámara" else "Zebra",
                        color = if (captureMode == SalidaCaptureMode.CAMERA) PmmNavyDark else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Badge de Operador sutil
            if (usuario.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = usuario,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 85.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.10f))
                        .padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Banner de KPI compacto y glanceable para la pantalla de detalle de carga.
 */
@Composable
fun SalidaKpiBanner(
    relacion: SalidaRelacion?,
    fallbackRelno: String,
    escaneados: Int,
    faltan: Int,
    total: Int,
    progreso: Float,
    completo: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progreso.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "kpi-progress"
    )

    val bannerBg = Brush.verticalGradient(
        listOf(
            PmmNavyDark,
            PmmBlueDeep
        )
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Box(modifier = Modifier.background(bannerBg).padding(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Fila Superior: Folio + Porcentaje
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = relacion?.relno?.ifBlank { fallbackRelno } ?: fallbackRelno,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = MonoFamily
                        )

                        if (!relacion?.caja.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.18f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Caja: ${relacion?.caja}",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = MonoFamily
                                )
                            }
                        }
                    }

                    // Porcentaje o Badge de Completo
                    if (completo) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(PmmGreen)
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "100% LISTO",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    } else {
                        val pct = (progreso * 100).toInt()
                        Text(
                            text = "$pct% COMPLETADO",
                            color = PmmYellow,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Cliente
                val clienteNombre = relacion?.cliente?.takeIf { it.isNotBlank() } ?: "Carga directa sin cliente"
                Text(
                    text = clienteNombre,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Barra de progreso estilizada
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (completo) PmmGreen else PmmYellow,
                    trackColor = Color.White.copy(alpha = 0.20f),
                    strokeCap = StrokeCap.Round
                )

                // Fila Inferior: Contadores Glanceable (Faltan / Cargados / Total)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KpiStatBadge(
                        label = "FALTAN",
                        value = "$faltan",
                        accentColor = if (faltan > 0) PmmAmber else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatBadge(
                        label = "CARGADOS",
                        value = "$escaneados",
                        accentColor = PmmGreen,
                        modifier = Modifier.weight(1f)
                    )
                    KpiStatBadge(
                        label = "TOTAL",
                        value = "$total",
                        accentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiStatBadge(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                color = accentColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MonoFamily
            )
        }
    }
}
