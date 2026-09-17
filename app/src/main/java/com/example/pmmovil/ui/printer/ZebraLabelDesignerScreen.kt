package com.example.pmmovil.ui.printer

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalLeft
import androidx.compose.material.icons.automirrored.filled.AlignHorizontalRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.pmmovil.data.printing.LabelAlignment
import com.example.pmmovil.data.printing.LabelFontFamily
import com.example.pmmovil.data.printing.LabelImageAsset
import com.example.pmmovil.data.printing.SnapResult
import com.example.pmmovil.data.printing.ZebraLabelData
import com.example.pmmovil.data.printing.ZebraLabelElement
import com.example.pmmovil.data.printing.ZebraLabelElementId
import com.example.pmmovil.data.printing.ZebraLabelLayout
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraCustomImage
import com.example.pmmovil.data.printing.ZebraCustomText
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZebraLabelDesignerScreen(
    onNavigateBack: () -> Unit,
    labelType: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA,
    viewModel: ZebraLabelDesignerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(labelType) {
        viewModel.setLabelType(labelType)
    }

    if (state.showJsonDialog) {
        JsonEditorDialog(
            jsonString = viewModel.getLayoutJson(),
            onDismiss = { viewModel.toggleJsonDialog(false) },
            onSave = { viewModel.importLayoutJson(it) }
        )
    }

    Scaffold(
        containerColor = PmmBgPrimary,
        topBar = {
            Surface(color = PmmNavyDark, shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Column {
                            Text("Diseñador de etiqueta", fontWeight = FontWeight.Black)
                            Text(
                                "ZT411 · ${state.labelType.dimensionsText} · 300 dpi",
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
                    actions = {
                        IconButton(
                            onClick = viewModel::undo,
                            enabled = state.canUndo
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Deshacer",
                                tint = if (state.canUndo) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(
                            onClick = viewModel::redo,
                            enabled = state.canRedo
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Rehacer",
                                tint = if (state.canRedo) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(onClick = viewModel::toggleGrid) {
                            Icon(
                                Icons.Default.GridOn,
                                contentDescription = "Cuadrícula",
                                tint = if (state.showGrid) PmmYellow else Color.White.copy(alpha = 0.6f)
                            )
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
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 700dp deja el lienzo (peso 1.5) con margen razonable a la par de los controles
            // (peso 1.0) — a 920dp la vista lado-a-lado no se activaba en tablets normales
            // (p. ej. 800dp de ancho), obligando a hacer scroll para llegar a los controles.
            val isWide = maxWidth >= 700.dp
            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                LabelPreviewPanel(
                    state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(1.5f)
                            .verticalScroll(rememberScrollState())
                    )
                    // El lienzo de la izquierda se queda fijo; pestañas y "Aplicar formato"
                    // quedan ancladas arriba/abajo y solo el contenido de la pestaña activa
                    // se desplaza (ver pinTabsAndActions), aunque tenga varias filas expandidas.
                    DesignerTabbedControls(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxHeight(),
                        pinTabsAndActions = true
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LabelPreviewPanel(state = state, viewModel = viewModel)
                    DesignerTabbedControls(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
private fun LabelPreviewPanel(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel,
    modifier: Modifier = Modifier
) {
    DesignerCard(
        title = "Vista fiel de impresión",
        icon = Icons.Default.Print,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Mismo raster de 1-bit enviado a la Zebra. Toca y arrastra para mover/estirar.",
                color = PmmTextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Selector de datos de prueba
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(SampleDataMode.values().toList(), key = { it.name }) { mode ->
                FilterChip(
                    selected = state.sampleDataMode == mode,
                    onClick = { viewModel.setSampleDataMode(mode) },
                    label = { Text(mode.title, fontSize = 11.sp) }
                )
            }
        }

        LabelPreview(
            layout = state.layout,
            previewLabel = state.previewLabel,
            selected = state.selectedElement,
            showGrid = state.showGrid,
            renderPreview = viewModel::renderPreview,
            onSelect = viewModel::selectElement,
            selectedCustomTextId = state.selectedCustomTextId,
            onSelectCustomText = viewModel::selectCustomText,
            selectedCustomImageId = state.selectedCustomImageId,
            onSelectCustomImage = viewModel::selectCustomImage,
            onResizeCustomImageWidth = viewModel::updateCustomImageWidth,
            onUpdateSize = { w, h ->
                viewModel.updateSelectedWidth(w)
                viewModel.updateSelectedHeight(h)
            },
            activeGuides = state.activeGuides,
            onBeginDrag = viewModel::beginDrag,
            onDragBy = viewModel::dragBy,
            onEndDrag = viewModel::endDrag
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Área: ${state.layout.widthDots} × ${state.layout.heightDots} dots",
                color = PmmTextMuted,
                fontSize = 11.sp
            )
            Text(
                "Sel: ${state.selectedElement.title} (X:${state.layout.element(state.selectedElement).x}, Y:${state.layout.element(state.selectedElement).y})",
                color = PmmBlueDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun LabelPreview(
    layout: ZebraLabelLayout,
    previewLabel: ZebraLabelData,
    selected: ZebraLabelElementId,
    selectedCustomTextId: String?,
    showGrid: Boolean,
    renderPreview: (ZebraLabelLayout, ZebraLabelData) -> Bitmap,
    onSelect: (ZebraLabelElementId) -> Unit,
    onSelectCustomText: (String) -> Unit,
    selectedCustomImageId: String?,
    onSelectCustomImage: (String) -> Unit,
    onResizeCustomImageWidth: (String, Int) -> Unit,
    onUpdateSize: (Int, Int) -> Unit,
    activeGuides: SnapResult?,
    onBeginDrag: (DragTarget) -> Unit,
    onDragBy: (DragTarget, Int, Int) -> Unit,
    onEndDrag: () -> Unit
) {
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(layout, previewLabel) {
        delay(PREVIEW_DEBOUNCE_MILLIS)
        previewBitmap = withContext(Dispatchers.Default) {
            renderPreview(layout, previewLabel)
        }
    }

    DisposableEffect(previewBitmap) {
        val bitmapToRelease = previewBitmap
        onDispose {
            if (bitmapToRelease != null && !bitmapToRelease.isRecycled) {
                bitmapToRelease.recycle()
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(layout.widthDots.toFloat() / layout.heightDots.toFloat())
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(BorderStroke(1.dp, PmmNavyBorder), RoundedCornerShape(12.dp))
    ) {
        val scale = maxWidth.value / layout.widthDots.toFloat()
        val height = maxHeight
        val width = maxWidth

        // Renderizado del bitmap raster de 1-bit
        previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Vista previa real de la etiqueta Zebra",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Overlay de Cuadrícula Grid (puntos/líneas cada 100 dots)
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val gridStepPx = 100f * scale
                var x = gridStepPx
                while (x < size.width) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    x += gridStepPx
                }
                var y = gridStepPx
                while (y < size.height) {
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
                    )
                    y += gridStepPx
                }
            }
        }

        // Renderizado de elementos interactivos
        ZebraLabelElementId.values().forEach { id ->
            PreviewElementContainer(
                id = id,
                element = layout.element(id),
                selected = selected,
                scale = scale,
                layoutWidthDots = layout.widthDots,
                layoutHeightDots = layout.heightDots,
                canvasWidth = width,
                canvasHeight = height,
                onSelect = onSelect,
                onBeginDrag = onBeginDrag,
                onDragBy = onDragBy,
                onEndDrag = onEndDrag,
                onUpdateSize = onUpdateSize
            )
        }
        layout.customTexts.forEach { customText ->
            CustomTextPreviewElementContainer(
                customText = customText,
                selected = customText.id == selectedCustomTextId,
                scale = scale,
                layoutWidthDots = layout.widthDots,
                layoutHeightDots = layout.heightDots,
                canvasWidth = width,
                canvasHeight = height,
                onSelect = onSelectCustomText,
                onBeginDrag = onBeginDrag,
                onDragBy = onDragBy,
                onEndDrag = onEndDrag
            )
        }
        layout.customImages.forEach { customImage ->
            CustomImagePreviewElementContainer(
                customImage = customImage,
                selected = customImage.id == selectedCustomImageId,
                scale = scale,
                layoutWidthDots = layout.widthDots,
                layoutHeightDots = layout.heightDots,
                canvasWidth = width,
                canvasHeight = height,
                onSelect = onSelectCustomImage,
                onBeginDrag = onBeginDrag,
                onDragBy = onDragBy,
                onEndDrag = onEndDrag,
                onResizeWidth = onResizeCustomImageWidth
            )
        }

        // Guías de alineación: se pintan encima de todo mientras dura el arrastre, para que se
        // vea POR QUÉ el elemento se pegó (borde compartido, centro del lienzo, etc.).
        if (activeGuides != null) {
            Canvas(modifier = Modifier.fillMaxSize().zIndex(20f)) {
                val guideColor = Color(0xFFFF2D95)
                activeGuides.verticalGuides.forEach { dots ->
                    val x = dots * scale * density
                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f
                    )
                }
                activeGuides.horizontalGuides.forEach { dots ->
                    val y = dots * scale * density
                    drawLine(
                        color = guideColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomTextPreviewElementContainer(
    customText: ZebraCustomText,
    selected: Boolean,
    scale: Float,
    layoutWidthDots: Int,
    layoutHeightDots: Int,
    canvasWidth: Dp,
    canvasHeight: Dp,
    onSelect: (String) -> Unit,
    onBeginDrag: (DragTarget) -> Unit,
    onDragBy: (DragTarget, Int, Int) -> Unit,
    onEndDrag: () -> Unit
) {
    val element = customText.element
    if (!element.visible && !selected) return
    val dragTarget = remember(customText.id) { DragTarget.Text(customText.id) }

    Box(
        modifier = Modifier
            .zIndex(if (selected) 6f else 2f)
            .offset(x = (element.x * scale).dp, y = (element.y * scale).dp)
            .width((element.width * scale).dp)
            .height((element.height * scale).dp)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 0.5.dp,
                    color = if (selected) PmmYellow else Color.Transparent
                ),
                RoundedCornerShape(3.dp)
            )
            .pointerInput(customText.id, canvasWidth, canvasHeight) {
                detectDragGestures(
                    onDragStart = { onBeginDrag(dragTarget) },
                    onDragEnd = onEndDrag,
                    onDragCancel = onEndDrag
                ) { change, dragAmount ->
                    change.consume()
                    val deltaX = (dragAmount.x / canvasWidth.toPx() * layoutWidthDots).roundToInt()
                    val deltaY = (dragAmount.y / canvasHeight.toPx() * layoutHeightDots).roundToInt()
                    if (deltaX != 0 || deltaY != 0) onDragBy(dragTarget, deltaX, deltaY)
                }
            }
            .clickable { onSelect(customText.id) }
    ) {
        if (selected) {
            Surface(
                color = PmmNavyDark,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "Texto: ${customText.text}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun CustomImagePreviewElementContainer(
    customImage: ZebraCustomImage,
    selected: Boolean,
    scale: Float,
    layoutWidthDots: Int,
    layoutHeightDots: Int,
    canvasWidth: Dp,
    canvasHeight: Dp,
    onSelect: (String) -> Unit,
    onBeginDrag: (DragTarget) -> Unit,
    onDragBy: (DragTarget, Int, Int) -> Unit,
    onEndDrag: () -> Unit,
    onResizeWidth: (String, Int) -> Unit
) {
    val element = customImage.element
    if (!element.visible && !selected) return
    val asset = LabelImageAsset.fromId(customImage.assetId)
    val dragTarget = remember(customImage.id) { DragTarget.Image(customImage.id) }

    Box(
        modifier = Modifier
            .zIndex(if (selected) 7f else 3f)
            .offset(x = (element.x * scale).dp, y = (element.y * scale).dp)
            .width((element.width * scale).dp)
            .height((element.height * scale).dp)
            .border(
                BorderStroke(
                    width = if (selected) 2.dp else 0.5.dp,
                    color = when {
                        selected -> PmmYellow
                        !element.visible -> Color.Red.copy(alpha = 0.4f)
                        else -> Color.Transparent
                    }
                ),
                RoundedCornerShape(3.dp)
            )
            .pointerInput(customImage.id, canvasWidth, canvasHeight) {
                detectDragGestures(
                    onDragStart = { onBeginDrag(dragTarget) },
                    onDragEnd = onEndDrag,
                    onDragCancel = onEndDrag
                ) { change, dragAmount ->
                    change.consume()
                    val deltaX = (dragAmount.x / canvasWidth.toPx() * layoutWidthDots).roundToInt()
                    val deltaY = (dragAmount.y / canvasHeight.toPx() * layoutHeightDots).roundToInt()
                    if (deltaX != 0 || deltaY != 0) onDragBy(dragTarget, deltaX, deltaY)
                }
            }
            .clickable { onSelect(customImage.id) }
    ) {
        if (selected) {
            Surface(
                color = PmmNavyDark,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "Imagen: ${asset?.title ?: customImage.assetId}",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Manija de redimensionado: el alto se recalcula solo para no deformar el logo.
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(CircleShape)
                    .background(PmmYellow)
                    .border(1.dp, PmmNavyDark, CircleShape)
                    .pointerInput(customImage.id, element.width, canvasWidth) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaW = (dragAmount.x / canvasWidth.toPx() * layoutWidthDots).roundToInt()
                            if (deltaW != 0) {
                                val newWidth = (element.width + deltaW).coerceAtLeast(80)
                                onResizeWidth(customImage.id, newWidth)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun PreviewElementContainer(
    id: ZebraLabelElementId,
    element: ZebraLabelElement,
    selected: ZebraLabelElementId,
    scale: Float,
    layoutWidthDots: Int,
    layoutHeightDots: Int,
    canvasWidth: Dp,
    canvasHeight: Dp,
    onSelect: (ZebraLabelElementId) -> Unit,
    onBeginDrag: (DragTarget) -> Unit,
    onDragBy: (DragTarget, Int, Int) -> Unit,
    onEndDrag: () -> Unit,
    onUpdateSize: (Int, Int) -> Unit
) {
    val isSelected = id == selected
    if (!element.visible && !isSelected) return
    val dragTarget = remember(id) { DragTarget.Fixed(id) }

    val widthDp = (element.width * scale).dp
    val heightDp = (element.height * scale).dp

    Box(
        modifier = Modifier
            .zIndex(if (isSelected) 5f else 1f)
            .offset(x = (element.x * scale).dp, y = (element.y * scale).dp)
            .width(widthDp)
            .height(heightDp)
            .border(
                BorderStroke(
                    width = if (isSelected) 2.dp else 0.5.dp,
                    color = when {
                        isSelected -> PmmBlueDeep
                        !element.visible -> Color.Red.copy(alpha = 0.4f)
                        else -> Color.Transparent
                    }
                ),
                RoundedCornerShape(3.dp)
            )
            // El gesto ya no depende de element.x/element.y: reiniciar el pointerInput a media
            // arrastre perdía la posición cruda acumulada y hacía que el elemento saltara al
            // despegarse de una guía.
            .pointerInput(id, canvasWidth, canvasHeight) {
                detectDragGestures(
                    onDragStart = { onBeginDrag(dragTarget) },
                    onDragEnd = onEndDrag,
                    onDragCancel = onEndDrag
                ) { change, dragAmount ->
                    change.consume()
                    val deltaX = (dragAmount.x / canvasWidth.toPx() * layoutWidthDots).roundToInt()
                    val deltaY = (dragAmount.y / canvasHeight.toPx() * layoutHeightDots).roundToInt()
                    if (deltaX != 0 || deltaY != 0) onDragBy(dragTarget, deltaX, deltaY)
                }
            }
            .clickable { onSelect(id) }
    ) {
        if (isSelected) {
            // Etiqueta flotante con el título del elemento activo
            Surface(
                color = PmmBlueDeep,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .offset(y = (-20).dp)
                    .align(Alignment.TopStart)
            ) {
                Text(
                    text = "${id.title} [${element.x}, ${element.y}]",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }

            // Manija de redimensionado en la esquina inferior derecha (SE handle)
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 5.dp, y = 5.dp)
                    .clip(CircleShape)
                    .background(PmmYellow)
                    .border(1.dp, PmmNavyDark, CircleShape)
                    .pointerInput(id, element.width, element.height, canvasWidth, canvasHeight) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val deltaW = (dragAmount.x / canvasWidth.toPx() * layoutWidthDots).roundToInt()
                            val deltaH = (dragAmount.y / canvasHeight.toPx() * layoutHeightDots).roundToInt()
                            if (deltaW != 0 || deltaH != 0) {
                                val newW = (element.width + deltaW).coerceAtLeast(80)
                                val newH = (element.height + deltaH).coerceAtLeast(32)
                                onUpdateSize(newW, newH)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun DesignerTabbedControls(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel,
    modifier: Modifier = Modifier,
    pinTabsAndActions: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    val tabs = listOf(
        "🧩 Elementos",
        "🎨 Estilo",
        "▦ QR",
        "✍ Textos",
        "🖼 Imágenes",
        "📂 Plantillas"
    )

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("¿Restaurar formato de fábrica?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Se pierde el diseño personalizado de esta etiqueta (textos, imágenes, posiciones). " +
                        "Si te arrepientes, puedes deshacerlo con el botón de deshacer de arriba."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreReference()
                        showRestoreConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                ) {
                    Text("Restaurar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    // Se separan en piezas para poder anclar pestañas/acciones en pantallas anchas (el panel
    // tiene su propio scroll, ver ZebraLabelDesignerScreen) sin duplicar el contenido real.
    val tabRow = @Composable {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = PmmBgSurface,
            contentColor = PmmBlueDeep,
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }
    }

    val tabContent = @Composable {
        when (selectedTab) {
            0 -> ElementsTab(state = state, viewModel = viewModel)
            1 -> StyleTypographyTab(state = state, viewModel = viewModel)
            2 -> QrStyleTab(state = state, viewModel = viewModel)
            3 -> CustomTextsTab(state = state, viewModel = viewModel)
            4 -> CustomImagesTab(state = state, viewModel = viewModel)
            5 -> PresetsTab(state = state, viewModel = viewModel)
        }
    }

    val actionsCard = @Composable {
        DesignerCard(title = "Aplicar formato", icon = Icons.Default.Save) {
            Button(
                onClick = viewModel::saveLayout,
                enabled = !state.isPrinting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PmmYellow, contentColor = PmmNavyDark),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Guardar formato", fontWeight = FontWeight.Black)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showRestoreConfirm = true },
                    enabled = !state.isPrinting,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Restaurar", maxLines = 1, fontSize = 12.sp)
                }
                Button(
                    onClick = viewModel::printTest,
                    enabled = !state.isPrinting,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    if (state.isPrinting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Imprimir prueba", maxLines = 1, fontSize = 12.sp)
                }
            }
            DesignerStatus(state)
        }
    }

    if (pinTabsAndActions) {
        // Pantalla ancha: pestañas arriba y "Aplicar formato" abajo quedan fijos; solo el
        // contenido de la pestaña activa se desplaza. Así nunca se pierde de vista cómo
        // cambiar de pestaña o guardar, sin importar qué tan larga esté la lista de elementos.
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabRow()
            Box(modifier = Modifier.weight(1f)) {
                // key(selectedTab) recrea el scroll state al cambiar de pestaña: sin esto,
                // cambiar de pestaña conservaba el scroll de la anterior y podía abrir la
                // nueva a la mitad, tapando el badge "Editando: X" de hasta arriba.
                key(selectedTab) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        tabContent()
                    }
                }
            }
            actionsCard()
        }
    } else {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            tabRow()
            tabContent()
            actionsCard()
        }
    }
}

/**
 * Fusiona lo que antes eran las pestañas "Posición" y "Capas": una sola lista de elementos
 * fijos de la etiqueta donde tocar un renglón lo selecciona Y expande ahí mismo sus controles
 * de posición/alineación (mismo patrón que ya usan los textos e imágenes libres). Antes había
 * tres formas distintas de seleccionar un elemento (chips, árbol de capas, lienzo) repartidas
 * en pestañas separadas sin relación visible entre ellas.
 */
@Composable
private fun ElementsTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    DesignerCard(title = "Elementos de la etiqueta", icon = Icons.Default.Layers) {
        Text(
            "Toca un elemento para ajustar su posición y tamaño. El ojo lo oculta o muestra.",
            color = PmmTextSecondary,
            fontSize = 11.sp
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ZebraLabelElementId.values().forEach { id ->
                key(id.name) {
                    val element = state.layout.element(id)
                    val isSelected = id == state.selectedElement
                    Surface(
                        color = if (isSelected) PmmBlueDeep.copy(alpha = 0.10f) else PmmBgPrimary,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, if (isSelected) PmmBlueDeep else PmmNavyBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectElement(id) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(id.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PmmTextPrimary)
                                    Text(
                                        "Pos: (${element.x}, ${element.y}) · ${element.width}x${element.height}",
                                        fontSize = 10.sp,
                                        color = PmmTextMuted
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        viewModel.selectElement(id)
                                        viewModel.updateSelectedVisibility(!element.visible)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (element.visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Visibilidad",
                                        tint = if (element.visible) PmmBlueDeep else Color.Gray
                                    )
                                }
                            }

                            if (isSelected) {
                                HorizontalDivider(color = PmmNavyBorder, modifier = Modifier.padding(vertical = 10.dp))
                                ElementPositionEditor(state = state, element = element, viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Controles de posición/alineación del elemento seleccionado; se muestran expandidos en [ElementsTab]. */
@Composable
private fun ElementPositionEditor(
    state: ZebraLabelDesignerState,
    element: ZebraLabelElement,
    viewModel: ZebraLabelDesignerViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Alineación rápida en canvas", color = PmmTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AlignButton(Icons.AutoMirrored.Filled.AlignHorizontalLeft, "Izquierda", viewModel::alignSelectedLeft)
            AlignButton(Icons.Default.AlignHorizontalCenter, "Centro H", viewModel::alignSelectedCenterH)
            AlignButton(Icons.AutoMirrored.Filled.AlignHorizontalRight, "Derecha", viewModel::alignSelectedRight)
            AlignButton(Icons.Default.AlignVerticalTop, "Arriba", viewModel::alignSelectedTop)
            AlignButton(Icons.Default.AlignVerticalCenter, "Centro V", viewModel::alignSelectedCenterV)
            AlignButton(Icons.Default.AlignVerticalBottom, "Abajo", viewModel::alignSelectedBottom)
        }

        HorizontalDivider(color = PmmNavyBorder)

        PositionSlider(
            label = "Horizontal (X)",
            value = element.x,
            valueRange = 0f..(state.layout.widthDots - element.width).coerceAtLeast(0).toFloat(),
            onValueChange = { viewModel.updateSelectedPosition(x = it.roundToInt()) },
            onMinus = { viewModel.moveSelected(-1, 0) },
            onPlus = { viewModel.moveSelected(1, 0) }
        )
        PositionSlider(
            label = "Vertical (Y)",
            value = element.y,
            valueRange = 0f..(state.layout.heightDots - element.height).coerceAtLeast(0).toFloat(),
            onValueChange = { viewModel.updateSelectedPosition(y = it.roundToInt()) },
            onMinus = { viewModel.moveSelected(0, -1) },
            onPlus = { viewModel.moveSelected(0, 1) }
        )
        PositionSlider(
            label = "Ancho (W)",
            value = element.width,
            valueRange = 80f..state.layout.widthDots.toFloat(),
            onValueChange = { viewModel.updateSelectedWidth(it.roundToInt()) },
            onMinus = { viewModel.updateSelectedWidth((element.width - 5).coerceAtLeast(80)) },
            onPlus = { viewModel.updateSelectedWidth(element.width + 5) }
        )
        PositionSlider(
            label = "Alto (H)",
            value = element.height,
            valueRange = 32f..state.layout.heightDots.toFloat(),
            onValueChange = { viewModel.updateSelectedHeight(it.roundToInt()) },
            onMinus = { viewModel.updateSelectedHeight((element.height - 5).coerceAtLeast(32)) },
            onPlus = { viewModel.updateSelectedHeight(element.height + 5) }
        )
    }
}

/** Botón de alineación rápida con etiqueta de texto — antes eran solo íconos sin nombre. */
@Composable
private fun AlignButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(icon, label, tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
        }
        Text(label, fontSize = 8.sp, color = PmmTextMuted, maxLines = 1)
    }
}

@Composable
private fun StyleTypographyTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    val selected = state.selectedElement
    val element = state.layout.element(selected)

    DesignerCard(title = "Estilo y Formato", icon = Icons.Default.Palette) {
        Surface(
            color = PmmBlueDeep.copy(alpha = 0.08f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "Editando: ${selected.title}",
                color = PmmBlueDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Visible en etiqueta", color = PmmTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = element.visible, onCheckedChange = viewModel::updateSelectedVisibility)
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Invertir color (Fondo negro)", color = PmmTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(checked = element.inverted, onCheckedChange = viewModel::updateSelectedInverted)
        }

        PositionSlider(
            label = "Grosor de borde marco (px)",
            value = element.borderWidth,
            valueRange = 0f..20f,
            onValueChange = { viewModel.updateSelectedBorderWidth(it.roundToInt()) },
            onMinus = { viewModel.updateSelectedBorderWidth((element.borderWidth - 1).coerceAtLeast(0)) },
            onPlus = { viewModel.updateSelectedBorderWidth((element.borderWidth + 1).coerceAtMost(20)) }
        )

        if (selected == ZebraLabelElementId.QR) {
            HorizontalDivider(color = PmmNavyBorder)
            Surface(
                color = PmmBlueDeep.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "El aspecto del QR (forma de módulos, esquinas, logo al centro) se ajusta en la " +
                        "pestaña ▦ QR, con vista previa de cada estilo.",
                    color = PmmBlueDeep,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }

        if (selected.supportsTypography) {
            HorizontalDivider(color = PmmNavyBorder)
            PositionSlider(
                label = "Alto de letra",
                value = element.fontHeight,
                valueRange = 12f..300f,
                onValueChange = { viewModel.updateSelectedFontHeight(it.roundToInt()) },
                onMinus = { viewModel.updateSelectedFontHeight((element.fontHeight - 1).coerceAtLeast(12)) },
                onPlus = { viewModel.updateSelectedFontHeight((element.fontHeight + 1).coerceAtMost(300)) }
            )
            PositionSlider(
                label = "Ancho de letra",
                value = element.fontWidth,
                valueRange = 10f..300f,
                onValueChange = { viewModel.updateSelectedFontWidth(it.roundToInt()) },
                onMinus = { viewModel.updateSelectedFontWidth((element.fontWidth - 1).coerceAtLeast(10)) },
                onPlus = { viewModel.updateSelectedFontWidth((element.fontWidth + 1).coerceAtMost(300)) }
            )
            Text("Estilo", color = PmmTextSecondary, fontSize = 12.sp)
            TextStyleToggleRow(
                bold = element.bold,
                italic = element.italic,
                underline = element.underline,
                onBoldChange = viewModel::updateSelectedBold,
                onItalicChange = viewModel::updateSelectedItalic,
                onUnderlineChange = viewModel::updateSelectedUnderline
            )
            LetterSpacingSlider(
                value = element.letterSpacing,
                onValueChange = viewModel::updateSelectedLetterSpacing
            )
            Text("Familia tipográfica", color = PmmTextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LabelFontFamily.values().toList(), key = { it.name }) { family ->
                    FilterChip(
                        selected = element.fontFamily == family,
                        onClick = { viewModel.updateSelectedFontFamily(family) },
                        label = { Text(family.title, fontFamily = FontFamily(family.previewTypeface())) }
                    )
                }
            }
            Text("Alineación de texto", color = PmmTextSecondary, fontSize = 12.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LabelAlignment.values().toList(), key = { it.name }) { alignment ->
                    FilterChip(
                        selected = element.alignment == alignment,
                        onClick = { viewModel.updateSelectedAlignment(alignment) },
                        label = { Text(alignment.title) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomTextsTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    DesignerCard(title = "Textos libres", icon = Icons.Default.Style) {
        Text(
            "Agrega textos independientes de los datos de entrada. Se imprimen exactamente como se ven en el raster.",
            color = PmmTextSecondary,
            fontSize = 11.sp
        )
        Button(
            onClick = viewModel::addCustomText,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("Agregar texto", fontWeight = FontWeight.Black)
        }

        if (state.layout.customTexts.isEmpty()) {
            Text(
                "Todavía no hay textos libres. Presiona Agregar texto para crear el primero.",
                color = PmmTextMuted,
                fontSize = 12.sp
            )
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.layout.customTexts.forEach { customText ->
                    key(customText.id) {
                        val selected = customText.id == state.selectedCustomTextId
                        Surface(
                            color = if (selected) PmmBlueDeep.copy(alpha = 0.10f) else PmmBgPrimary,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) PmmBlueDeep else PmmNavyBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCustomText(customText.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            customText.text.ifBlank { "Texto vacío" },
                                            color = PmmTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${customText.element.fontFamily.title} · (${customText.element.x}, ${customText.element.y})",
                                            color = PmmTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.duplicateCustomText(customText.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Duplicar texto", tint = PmmBlueDeep)
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeCustomText(customText.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Eliminar texto", tint = PmmRed)
                                    }
                                }

                                if (selected) {
                                    CustomTextEditor(
                                        customText = customText,
                                        layoutWidthDots = state.layout.widthDots,
                                        layoutHeightDots = state.layout.heightDots,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomTextEditor(
    customText: ZebraCustomText,
    layoutWidthDots: Int,
    layoutHeightDots: Int,
    viewModel: ZebraLabelDesignerViewModel
) {
    val id = customText.id
    val element = customText.element

    OutlinedTextField(
        value = customText.text,
        onValueChange = { viewModel.updateCustomText(id, it) },
        label = { Text("Texto a imprimir") },
        placeholder = { Text("Ej. REVISADO, FRÁGIL, RUTA 4") },
        minLines = 2,
        maxLines = 4,
        modifier = Modifier.fillMaxWidth()
    )

    PositionSlider(
        label = "Horizontal (X)",
        value = element.x,
        valueRange = 0f..(layoutWidthDots - element.width).coerceAtLeast(0).toFloat(),
        onValueChange = { viewModel.updateCustomTextPosition(id, x = it.roundToInt()) },
        onMinus = { viewModel.moveCustomText(id, -1, 0) },
        onPlus = { viewModel.moveCustomText(id, 1, 0) }
    )
    PositionSlider(
        label = "Vertical (Y)",
        value = element.y,
        valueRange = 0f..layoutHeightDots.toFloat(),
        onValueChange = { viewModel.updateCustomTextPosition(id, y = it.roundToInt()) },
        onMinus = { viewModel.moveCustomText(id, 0, -1) },
        onPlus = { viewModel.moveCustomText(id, 0, 1) }
    )
    PositionSlider(
        label = "Ancho (W)",
        value = element.width,
        valueRange = 80f..layoutWidthDots.toFloat(),
        onValueChange = { viewModel.updateCustomTextWidth(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomTextWidth(id, (element.width - 5).coerceAtLeast(80)) },
        onPlus = { viewModel.updateCustomTextWidth(id, element.width + 5) }
    )
    PositionSlider(
        label = "Alto (H)",
        value = element.height,
        valueRange = 32f..layoutHeightDots.toFloat(),
        onValueChange = { viewModel.updateCustomTextHeight(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomTextHeight(id, (element.height - 5).coerceAtLeast(32)) },
        onPlus = { viewModel.updateCustomTextHeight(id, element.height + 5) }
    )
    PositionSlider(
        label = "Alto de letra",
        value = element.fontHeight,
        valueRange = 12f..300f,
        onValueChange = { viewModel.updateCustomTextFontHeight(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomTextFontHeight(id, (element.fontHeight - 1).coerceAtLeast(12)) },
        onPlus = { viewModel.updateCustomTextFontHeight(id, (element.fontHeight + 1).coerceAtMost(300)) }
    )
    PositionSlider(
        label = "Ancho de letra",
        value = element.fontWidth,
        valueRange = 10f..300f,
        onValueChange = { viewModel.updateCustomTextFontWidth(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomTextFontWidth(id, (element.fontWidth - 1).coerceAtLeast(10)) },
        onPlus = { viewModel.updateCustomTextFontWidth(id, (element.fontWidth + 1).coerceAtMost(300)) }
    )

    Text("Estilo", color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    TextStyleToggleRow(
        bold = element.bold,
        italic = element.italic,
        underline = element.underline,
        onBoldChange = { viewModel.updateCustomTextBold(id, it) },
        onItalicChange = { viewModel.updateCustomTextItalic(id, it) },
        onUnderlineChange = { viewModel.updateCustomTextUnderline(id, it) }
    )
    LetterSpacingSlider(
        value = element.letterSpacing,
        onValueChange = { viewModel.updateCustomTextLetterSpacing(id, it) }
    )
    Text("Fuente", color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(LabelFontFamily.values().toList(), key = { it.name }) { family ->
            FilterChip(
                selected = element.fontFamily == family,
                onClick = { viewModel.updateCustomTextFontFamily(id, family) },
                label = { Text(family.title, fontSize = 10.sp, fontFamily = FontFamily(family.previewTypeface())) }
            )
        }
    }
    Text("Alineación", color = PmmTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(LabelAlignment.values().toList(), key = { it.name }) { alignment ->
            FilterChip(
                selected = element.alignment == alignment,
                onClick = { viewModel.updateCustomTextAlignment(id, alignment) },
                label = { Text(alignment.title, fontSize = 10.sp) }
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Visible", color = PmmTextSecondary, modifier = Modifier.weight(1f))
        Switch(
            checked = element.visible,
            onCheckedChange = { viewModel.updateCustomTextVisibility(id, it) }
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Fondo negro", color = PmmTextSecondary, modifier = Modifier.weight(1f))
        Switch(
            checked = element.inverted,
            onCheckedChange = { viewModel.updateCustomTextInverted(id, it) }
        )
    }
}

@Composable
private fun CustomImagesTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    DesignerCard(title = "Imágenes", icon = Icons.Default.Image) {
        Text(
            "Agrega imágenes del proyecto (como el logo de IMEX) y colócalas donde quieras en la etiqueta.",
            color = PmmTextSecondary,
            fontSize = 11.sp
        )

        Text("Imágenes disponibles", color = PmmTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(LabelImageAsset.values().toList(), key = { it.id }) { asset ->
                Surface(
                    color = PmmBgPrimary,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder),
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { viewModel.addCustomImage(asset) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(asset.drawableRes),
                            contentDescription = asset.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                        Text(asset.title, fontSize = 10.sp, color = PmmTextPrimary, textAlign = TextAlign.Center, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, null, tint = PmmBlueDeep, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Agregar", fontSize = 9.sp, color = PmmBlueDeep, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (state.layout.customImages.isEmpty()) {
            Text(
                "Todavía no hay imágenes en la etiqueta. Toca una imagen de arriba para agregarla.",
                color = PmmTextMuted,
                fontSize = 12.sp
            )
        } else {
            HorizontalDivider(color = PmmNavyBorder)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.layout.customImages.forEach { customImage ->
                    key(customImage.id) {
                        val asset = LabelImageAsset.fromId(customImage.assetId)
                        val selected = customImage.id == state.selectedCustomImageId
                        Surface(
                            color = if (selected) PmmBlueDeep.copy(alpha = 0.10f) else PmmBgPrimary,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (selected) PmmBlueDeep else PmmNavyBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectCustomImage(customImage.id) }
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (asset != null) {
                                        Image(
                                            painter = painterResource(asset.drawableRes),
                                            contentDescription = asset.title,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .padding(end = 8.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            asset?.title ?: "Imagen",
                                            color = PmmTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                        Text(
                                            "(${customImage.element.x}, ${customImage.element.y}) · ${customImage.element.width}x${customImage.element.height}",
                                            color = PmmTextMuted,
                                            fontSize = 10.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.duplicateCustomImage(customImage.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, "Duplicar imagen", tint = PmmBlueDeep)
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeCustomImage(customImage.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, "Eliminar imagen", tint = PmmRed)
                                    }
                                }

                                if (selected) {
                                    CustomImageEditor(
                                        customImage = customImage,
                                        layoutWidthDots = state.layout.widthDots,
                                        layoutHeightDots = state.layout.heightDots,
                                        viewModel = viewModel
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomImageEditor(
    customImage: ZebraCustomImage,
    layoutWidthDots: Int,
    layoutHeightDots: Int,
    viewModel: ZebraLabelDesignerViewModel
) {
    val id = customImage.id
    val element = customImage.element

    PositionSlider(
        label = "Horizontal (X)",
        value = element.x,
        valueRange = 0f..(layoutWidthDots - element.width).coerceAtLeast(0).toFloat(),
        onValueChange = { viewModel.updateCustomImagePosition(id, x = it.roundToInt()) },
        onMinus = { viewModel.moveCustomImage(id, -1, 0) },
        onPlus = { viewModel.moveCustomImage(id, 1, 0) }
    )
    PositionSlider(
        label = "Vertical (Y)",
        value = element.y,
        valueRange = 0f..layoutHeightDots.toFloat(),
        onValueChange = { viewModel.updateCustomImagePosition(id, y = it.roundToInt()) },
        onMinus = { viewModel.moveCustomImage(id, 0, -1) },
        onPlus = { viewModel.moveCustomImage(id, 0, 1) }
    )
    PositionSlider(
        label = "Ancho (W)",
        value = element.width,
        valueRange = 80f..layoutWidthDots.toFloat(),
        onValueChange = { viewModel.updateCustomImageWidth(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomImageWidth(id, (element.width - 5).coerceAtLeast(80)) },
        onPlus = { viewModel.updateCustomImageWidth(id, element.width + 5) }
    )
    PositionSlider(
        label = "Alto (H)",
        value = element.height,
        valueRange = 32f..layoutHeightDots.toFloat(),
        onValueChange = { viewModel.updateCustomImageHeight(id, it.roundToInt()) },
        onMinus = { viewModel.updateCustomImageHeight(id, (element.height - 5).coerceAtLeast(32)) },
        onPlus = { viewModel.updateCustomImageHeight(id, element.height + 5) }
    )
    Text(
        "El tamaño conserva la proporción original de la imagen para que no se deforme.",
        color = PmmTextMuted,
        fontSize = 10.sp
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Visible", color = PmmTextSecondary, modifier = Modifier.weight(1f))
        Switch(
            checked = element.visible,
            onCheckedChange = { viewModel.updateCustomImageVisibility(id, it) }
        )
    }
}

@Composable
private fun PresetsTab(
    state: ZebraLabelDesignerState,
    viewModel: ZebraLabelDesignerViewModel
) {
    DesignerCard(title = "Biblioteca de Plantillas", icon = Icons.Default.Style) {
        Text("Selecciona una plantilla predefinida:", color = PmmTextSecondary, fontSize = 11.sp)

        val presets = if (state.labelType == ZebraLabelType.CASETA) {
            listOf(
                Triple(
                    "Caseta 2.25 × 1.25",
                    "QR y caja dentro de una etiqueta individual",
                    ZebraLabelLayout.casetaCompact()
                )
            )
        } else {
            listOf(
                Triple("Estándar 4x6", "Diseño original de referencia balanceado", ZebraLabelLayout.reference()),
                Triple("Compacta Cajas", "Enfoque en código QR amplio y número de caja", ZebraLabelLayout.compact()),
                Triple("Folio Gigante", "Referencia de entrada destacada a tamaño máximo", ZebraLabelLayout.giantReference()),
                Triple("Enfoque Cliente", "Cabeceras invertidas y datos de cliente destacados", ZebraLabelLayout.customerFocused()),
                Triple("Marcos y Divisiones", "Estructura con marcos y cajas invertidas", ZebraLabelLayout.framedDividers())
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { (name, desc, layout) ->
                Surface(
                    color = PmmBgPrimary,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PmmNavyBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.applyPreset(layout, name) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Style, null, tint = PmmBlueDeep, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = PmmTextPrimary)
                            Text(desc, fontSize = 11.sp, color = PmmTextSecondary)
                        }
                        Text("Aplicar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PmmBlueDeep)
                    }
                }
            }
        }

        HorizontalDivider(color = PmmNavyBorder)
        Text("Avanzado", color = PmmTextMuted, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "Copia o pega el formato como texto JSON. Pensado para respaldar o compartir una plantilla entre etiquetas, no para uso diario.",
            color = PmmTextMuted,
            fontSize = 11.sp
        )
        OutlinedButton(
            onClick = { viewModel.toggleJsonDialog(true) },
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            Icon(Icons.Default.Code, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Ver / editar código JSON", fontSize = 12.sp)
        }
    }
}

@Composable
private fun PositionSlider(
    label: String,
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = PmmTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onMinus, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) {
                Text("−", fontWeight = FontWeight.Black)
            }
            // El número es editable: en una tablet, acertar un valor exacto con el slider es
            // imposible, y los botones ± obligan a decenas de toques para recorrer 100 dots.
            EditableDotsValue(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                modifier = Modifier.width(52.dp)
            )
            OutlinedButton(onClick = onPlus, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp), modifier = Modifier.height(28.dp)) {
                Text("+", fontWeight = FontWeight.Black)
            }
        }
        Slider(
            value = value.toFloat().coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

/**
 * Campo numérico compacto para escribir un valor en dots directamente.
 *
 * Mientras se escribe, el texto vive en estado local para no pelear con el valor de afuera (si
 * se reflejara al instante, borrar el último dígito lo convertiría en 0 y el elemento saltaría).
 * Solo se propaga cuando lo tecleado es un número válido dentro del rango; al salir del campo se
 * resincroniza con el valor real por si quedó a medias.
 */
@Composable
private fun EditableDotsValue(
    value: Int,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf(value.toString()) }
    var focused by remember { mutableStateOf(false) }

    // Cambios que vienen de afuera (slider, botones ±, arrastre en el lienzo) se reflejan aquí,
    // salvo mientras el campo tiene el foco para no pisar lo que se está escribiendo.
    LaunchedEffect(value) {
        if (!focused && text.toIntOrNull() != value) text = value.toString()
    }

    BasicTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            text = digits
            val parsed = digits.toIntOrNull()
            if (parsed != null && parsed.toFloat() in valueRange) onValueChange(parsed.toFloat())
        },
        singleLine = true,
        textStyle = LocalTextStyle.current.copy(
            color = PmmTextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        modifier = modifier
            .height(28.dp)
            .onFocusChanged { focusState ->
                focused = focusState.isFocused
                if (!focusState.isFocused) text = value.toString()
            },
        decorationBox = { innerTextField ->
            Surface(
                color = PmmBgPrimary,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, if (focused) PmmBlueDeep else PmmNavyBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp)
                ) { innerTextField() }
            }
        }
    )
}

/** Mini toolbar de N/K/S — negrita, cursiva y subrayado, independientes de la familia tipográfica. */
@Composable
private fun TextStyleToggleRow(
    bold: Boolean,
    italic: Boolean,
    underline: Boolean,
    onBoldChange: (Boolean) -> Unit,
    onItalicChange: (Boolean) -> Unit,
    onUnderlineChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StyleToggleButton(
            icon = Icons.Default.FormatBold,
            label = "Negrita",
            selected = bold,
            onClick = { onBoldChange(!bold) },
            modifier = Modifier.weight(1f)
        )
        StyleToggleButton(
            icon = Icons.Default.FormatItalic,
            label = "Cursiva",
            selected = italic,
            onClick = { onItalicChange(!italic) },
            modifier = Modifier.weight(1f)
        )
        StyleToggleButton(
            icon = Icons.Default.FormatUnderlined,
            label = "Subrayado",
            selected = underline,
            onClick = { onUnderlineChange(!underline) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StyleToggleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) PmmBlueDeep else PmmBgPrimary,
        contentColor = if (selected) Color.White else PmmTextSecondary,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) PmmBlueDeep else PmmNavyBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(icon, label, modifier = Modifier.size(18.dp))
            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/**
 * Tracking del texto (Paint.letterSpacing en unidades "em"). Es control propio agregado además
 * de lo pedido: en etiquetas de bodega ayuda tanto a "apretar" un folio largo para que quepa
 * sin bajarle el tamaño de letra, como a separar un texto corto (p. ej. "FRÁGIL") para que
 * resalte más a distancia.
 */
@Composable
private fun LetterSpacingSlider(
    value: Float,
    onValueChange: (Float) -> Unit
) {
    val range = ZebraLabelElement.LETTER_SPACING_MIN..ZebraLabelElement.LETTER_SPACING_MAX
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Espaciado entre letras", color = PmmTextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text(
                String.format(java.util.Locale.US, "%.2f", value),
                color = PmmTextPrimary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                modifier = Modifier.width(48.dp)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun DesignerStatus(state: ZebraLabelDesignerState) {
    val message = state.message ?: return
    val success = state.lastActionSucceeded
    val color = when (success) {
        true -> PmmGreen
        false -> PmmRed
        null -> PmmBlueDeep
    }
    Surface(
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (success == false) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(16.dp)
            )
            Text(message, color = PmmTextPrimary, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun DesignerCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PmmBgSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, PmmNavyBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = PmmBlueDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = PmmTextPrimary, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
            HorizontalDivider(color = PmmNavyBorder)
            content()
        }
    }
}

@Composable
private fun JsonEditorDialog(
    jsonString: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Boolean
) {
    var text by remember { mutableStateOf(jsonString) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Código JSON de la Plantilla", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; error = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                error?.let { Text(it, color = PmmRed, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!onSave(text)) {
                    error = "JSON inválido. Revisa la sintaxis."
                }
            }) {
                Text("Importar JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/**
 * Peso usado solo para la muestra de fuente en la chip del selector (no es el peso real de
 * impresión: eso ahora lo decide el toggle de Negrita, independiente de la familia).
 */
private fun LabelFontFamily.previewTypeface(): Typeface = when (this) {
    LabelFontFamily.SANS_LIGHT, LabelFontFamily.SANS_THIN -> Typeface.create(familyName, Typeface.NORMAL)
    else -> Typeface.create(familyName, Typeface.BOLD)
}

private const val PREVIEW_DEBOUNCE_MILLIS = 200L
