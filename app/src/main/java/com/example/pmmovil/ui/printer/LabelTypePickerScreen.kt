package com.example.pmmovil.ui.printer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Toll
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reemplaza los dos botones de texto que antes vivían al fondo de "Impresora Zebra"
 * ("Entrada 4x6" / "Caseta"): aquí se elige visualmente, con la miniatura real de cómo
 * se ve la etiqueta ahora mismo, y se entra directo al diseñador.
 *
 * Grid de 1-2 columnas (según ancho disponible) en vez de una Column apilada: antes, cada
 * tarjeta llevaba su miniatura a `aspectRatio` del ancho completo, así que en tablet horizontal
 * (mucho ancho, poca altura) se volvían enormes y la de Caseta quedaba fuera de pantalla. El
 * patrón viene de las galerías de plantillas de Canva/Google Docs: la miniatura vive en un
 * recuadro de altura fija (no ligada a la proporción física de la etiqueta), así todas las
 * tarjetas miden lo mismo sin importar cuántos "dots" tenga cada plantilla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelTypePickerScreen(
    onNavigateBack: () -> Unit,
    onOpenBodegaDesigner: () -> Unit,
    onOpenCasetaDesigner: () -> Unit,
    viewModel: LabelTypePickerViewModel = hiltViewModel()
) {
    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Editar etiqueta", fontWeight = FontWeight.Black)
                            Text(
                                "¿Cuál quieres modificar?",
                                color = Color.White.copy(alpha = 0.72f),
                                fontSize = 11.sp
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Breakpoint estándar de Material (600dp = compact/medium): 1 columna en celular
            // vertical, 2 en tablet (vertical u horizontal) y en celular acostado. Con solo 2
            // tipos de etiqueta, 2 columnas significa que siempre caben en un único renglón.
            val columns = if (maxWidth >= 600.dp) 2 else 1
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Toca la que quieras editar. Se abre directo en el diseñador visual, sin pasar por el catálogo de impresoras.",
                        color = PmmTextSecondary,
                        fontSize = 12.sp
                    )
                }
                items(ZebraLabelType.values().toList(), key = { it.name }) { type ->
                    LabelTypeCard(
                        type = type,
                        viewModel = viewModel,
                        onClick = when (type) {
                            ZebraLabelType.ENTRADA_BODEGA -> onOpenBodegaDesigner
                            ZebraLabelType.CASETA -> onOpenCasetaDesigner
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LabelTypeCard(
    type: ZebraLabelType,
    viewModel: LabelTypePickerViewModel,
    onClick: () -> Unit
) {
    var thumbnail by remember(type) { mutableStateOf<LabelThumbnail?>(null) }

    LaunchedEffect(type) {
        thumbnail = withContext(Dispatchers.Default) { viewModel.renderThumbnail(type) }
    }

    DisposableEffect(thumbnail) {
        val bitmapToRelease = thumbnail?.bitmap
        onDispose {
            if (bitmapToRelease != null && !bitmapToRelease.isRecycled) {
                bitmapToRelease.recycle()
            }
        }
    }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Recuadro de altura fija: a diferencia del diseño anterior (aspectRatio ligado a
            // los dots reales de la plantilla), aquí TODAS las miniaturas miden lo mismo sin
            // importar si la etiqueta es 4x6 o 2.25x1.25 — por eso ContentScale.Fit en vez de
            // FillBounds, para no estirar/deformar el raster dentro de un recuadro que ya no
            // comparte su proporción.
            val current = thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(BorderStroke(1.dp, PmmNavyBorder.copy(alpha = 0.4f)), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (current != null) {
                    Image(
                        bitmap = current.bitmap.asImageBitmap(),
                        contentDescription = "Vista previa de ${type.title}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PmmBlueDeep)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    type.pickerIcon(),
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(18.dp)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                ) {
                    Text(type.title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                    Text(type.dimensionsText, color = PmmTextMuted, fontSize = 10.sp)
                }
                Text("Editar", color = PmmBlueDeep, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = PmmBlueDeep,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** Icono de contexto para reconocer el tipo de etiqueta de un vistazo, sin leer el título. */
private fun ZebraLabelType.pickerIcon(): ImageVector = when (this) {
    ZebraLabelType.ENTRADA_BODEGA -> Icons.Default.Warehouse
    ZebraLabelType.CASETA -> Icons.Default.Toll
}
