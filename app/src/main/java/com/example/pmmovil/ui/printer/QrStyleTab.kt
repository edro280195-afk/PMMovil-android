package com.example.pmmovil.ui.printer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.printing.LabelImageAsset
import com.example.pmmovil.data.printing.QrEyeShape
import com.example.pmmovil.data.printing.QrLogoPlate
import com.example.pmmovil.data.printing.QrModuleShape
import com.example.pmmovil.data.printing.QrRenderReport
import com.example.pmmovil.data.printing.QrStyleOptions
import com.example.pmmovil.data.printing.QrStylePreset
import com.example.pmmovil.data.printing.StyledQrRenderer
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Valor de ejemplo para las miniaturas: mismo largo típico de un folio real de PMM. */
private const val PREVIEW_QR_VALUE = "356398-00"
private const val PREVIEW_SIDE_DOTS = 210

/**
 * Pestaña de estilo del código QR. En vez de listas desplegables con nombres abstractos, cada
 * opción se muestra como una miniatura del QR real generada con el mismo motor que imprime
 * ([StyledQrRenderer]): lo que se ve en el selector es exactamente lo que sale en la etiqueta.
 */
@Composable
fun QrStyleTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    val style = state.layout.qrStyle
    val context = LocalContext.current
    val logoBitmap = remember {
        runCatching {
            BitmapFactory.decodeResource(context.resources, LabelImageAsset.IMEX_LOGO.drawableRes)
        }.getOrNull()
    }

    DesignerCard(title = "Estilo del código QR", icon = Icons.Default.QrCode2) {
        QrVerificationBadge(report = state.qrReport)

        Text(
            "Cada estilo se verifica decodificando el QR ya dibujado, igual que lo vería un lector. " +
                "Si alguno no pasa, se ajusta solo antes de imprimir.",
            color = PmmTextSecondary,
            fontSize = 11.sp
        )

        // --- Presets de un toque ---
        Text("Estilos rápidos", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(QrStylePreset.values().toList(), key = { it.name }) { preset ->
                QrOptionTile(
                    label = preset.title,
                    caption = preset.description,
                    selected = preset.matches(style),
                    style = preset.apply(style).copy(logoEnabled = false),
                    logoBitmap = null,
                    tileWidth = 118,
                    onClick = { viewModel.applyQrPreset(preset) }
                )
            }
        }

        HorizontalDivider(color = PmmNavyBorder)

        // --- Forma de los módulos ---
        Text("Forma de los módulos", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(QrModuleShape.values().toList(), key = { it.name }) { shape ->
                QrOptionTile(
                    label = shape.title,
                    caption = shape.description,
                    selected = style.moduleShape == shape,
                    style = style.copy(moduleShape = shape, logoEnabled = false),
                    logoBitmap = null,
                    tileWidth = 112,
                    onClick = { viewModel.setQrModuleShape(shape) }
                )
            }
        }

        HorizontalDivider(color = PmmNavyBorder)

        // --- Ojos: marco y bola por separado ---
        Text("Marco de las esquinas", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(
            "Los 3 cuadros grandes que el lector busca primero para orientarse.",
            color = PmmTextMuted,
            fontSize = 10.sp
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(QrEyeShape.values().toList(), key = { it.name }) { shape ->
                QrOptionTile(
                    label = shape.title,
                    caption = null,
                    selected = style.eyeFrameShape == shape,
                    style = style.copy(eyeFrameShape = shape, logoEnabled = false),
                    logoBitmap = null,
                    tileWidth = 96,
                    onClick = { viewModel.setQrEyeFrameShape(shape) }
                )
            }
        }

        Text("Centro de las esquinas", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(QrEyeShape.values().toList(), key = { it.name }) { shape ->
                QrOptionTile(
                    label = shape.title,
                    caption = null,
                    selected = style.eyeBallShape == shape,
                    style = style.copy(eyeBallShape = shape, logoEnabled = false),
                    logoBitmap = null,
                    tileWidth = 96,
                    onClick = { viewModel.setQrEyeBallShape(shape) }
                )
            }
        }

        HorizontalDivider(color = PmmNavyBorder)

        // --- Logo al centro ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Logo IMEX al centro", color = PmmTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(
                    "Sube la corrección de errores automáticamente para compensar los módulos tapados.",
                    color = PmmTextMuted,
                    fontSize = 10.sp
                )
            }
            Switch(checked = style.logoEnabled, onCheckedChange = viewModel::setQrLogoEnabled)
        }

        if (style.logoEnabled) {
            Text("Fondo del logo", color = PmmTextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(QrLogoPlate.values().toList(), key = { it.name }) { plate ->
                    QrOptionTile(
                        label = plate.title,
                        caption = plate.description,
                        selected = style.logoPlate == plate,
                        style = style.copy(logoPlate = plate, logoEnabled = true),
                        logoBitmap = logoBitmap,
                        tileWidth = 118,
                        onClick = { viewModel.setQrLogoPlate(plate) }
                    )
                }
            }

            QrSlider(
                label = "Tamaño del logo",
                valueText = "${(style.logoScale * 100).roundToInt()}% del ancho",
                value = style.logoScale,
                valueRange = QrStyleOptions.LOGO_SCALE_MIN..QrStyleOptions.LOGO_SCALE_MAX,
                onValueChange = viewModel::setQrLogoScale
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Limpiar módulos bajo el logo", color = PmmTextSecondary, fontSize = 12.sp)
                    Text(
                        "Evita que asomen medios módulos en el borde del logo.",
                        color = PmmTextMuted,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = style.clearModulesUnderLogo,
                    onCheckedChange = viewModel::setQrClearModulesUnderLogo
                )
            }
        }

        HorizontalDivider(color = PmmNavyBorder)

        QrSlider(
            label = "Margen blanco alrededor",
            valueText = "${style.quietZoneModules} módulo(s)",
            value = style.quietZoneModules.toFloat(),
            valueRange = QrStyleOptions.QUIET_ZONE_MIN.toFloat()..QrStyleOptions.QUIET_ZONE_MAX.toFloat(),
            steps = QrStyleOptions.QUIET_ZONE_MAX - QrStyleOptions.QUIET_ZONE_MIN - 1,
            onValueChange = { viewModel.setQrQuietZone(it.roundToInt()) }
        )
        Text(
            "Un margen más ancho ayuda cuando la etiqueta queda pegada a otro elemento impreso.",
            color = PmmTextMuted,
            fontSize = 10.sp
        )
    }
}

/**
 * Badge de verificación en vivo. Es la pieza que responde a "¿garantizamos que se lea?": muestra
 * el resultado de decodificar el QR que se está viendo, no una promesa teórica.
 */
@Composable
private fun QrVerificationBadge(report: QrRenderReport?) {
    val (background, accent, icon, title) = when {
        report == null -> Quad(PmmBgPrimary, PmmTextMuted, Icons.Default.Info, "Generando vista previa...")
        !report.verified -> Quad(
            PmmRed.copy(alpha = 0.10f),
            PmmRed,
            Icons.Default.Warning,
            "No se pudo verificar la lectura"
        )
        report.downgraded -> Quad(
            PmmYellow.copy(alpha = 0.18f),
            PmmTextPrimary,
            Icons.Default.Warning,
            "Estilo ajustado para garantizar la lectura"
        )
        else -> Quad(
            PmmGreen.copy(alpha = 0.12f),
            PmmGreen,
            Icons.Default.CheckCircle,
            "Verificado: este QR se lee correctamente"
        )
    }

    Surface(
        color = background,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (report != null) {
                    val detail = buildString {
                        append("${report.moduleCount}×${report.moduleCount} módulos")
                        append(" · ${report.moduleSizeDots} dots por módulo")
                        if (report.downgraded) {
                            append(" · se aplicó ${report.applied.moduleShape.title.lowercase()}")
                        }
                    }
                    Text(detail, color = PmmTextMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/**
 * Tarjeta seleccionable con una miniatura del QR generada de verdad con [style]. Renderizar el
 * QR real (en vez de un ícono ilustrativo) es lo que permite decidir de un vistazo: no hay que
 * imaginarse cómo se verá "fluido" contra "redondeado".
 */
@Composable
private fun QrOptionTile(
    label: String,
    caption: String?,
    selected: Boolean,
    style: QrStyleOptions,
    logoBitmap: Bitmap?,
    tileWidth: Int,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) PmmBlueDeep.copy(alpha = 0.10f) else PmmBgPrimary,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) PmmBlueDeep else PmmNavyBorder),
        modifier = Modifier
            .width(tileWidth.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(1.dp, PmmNavyBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                QrThumbnail(style = style, logoBitmap = logoBitmap)
            }
            Text(
                label,
                color = if (selected) PmmBlueDeep else PmmTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1
            )
            if (caption != null) {
                Text(caption, color = PmmTextMuted, fontSize = 9.sp, maxLines = 3)
            }
        }
    }
}

/** Slider con lectura del valor en palabras, no solo el número crudo. */
@Composable
private fun QrSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PmmTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(valueText, color = PmmBlueDeep, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

/** Miniatura del QR renderizada fuera del hilo principal y liberada al salir de composición. */
@Composable
private fun QrThumbnail(style: QrStyleOptions, logoBitmap: Bitmap?) {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(style, logoBitmap) {
        bitmap = withContext(Dispatchers.Default) {
            StyledQrRenderer().render(
                value = PREVIEW_QR_VALUE,
                targetSideDots = PREVIEW_SIDE_DOTS,
                style = style,
                logoBitmap = logoBitmap,
                logoAspectRatio = LabelImageAsset.IMEX_LOGO.aspectRatio,
                verify = false
            )?.bitmap
        }
    }

    DisposableEffect(bitmap) {
        val toRelease = bitmap
        onDispose {
            if (toRelease != null && !toRelease.isRecycled) toRelease.recycle()
        }
    }

    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(4.dp).fillMaxWidth()
        )
    }
}
