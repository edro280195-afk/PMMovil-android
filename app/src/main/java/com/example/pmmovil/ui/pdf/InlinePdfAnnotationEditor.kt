package com.example.pmmovil.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pmmovil.data.model.DocumentoPdfDto
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class PdfPagePreview(
    val index: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val bitmap: Bitmap
)

private data class PdfInkPoint(
    val x: Float,
    val y: Float
)

private data class PdfInkStroke(
    val pageIndex: Int,
    val points: List<PdfInkPoint>,
    val color: Int,
    val widthFraction: Float
)

@Composable
fun InlinePdfAnnotationEditor(
    file: File,
    selectedDocument: DocumentoPdfDto?,
    onDirtyChanged: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSaved: (File) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pages by remember(file) { mutableStateOf<List<PdfPagePreview>>(emptyList()) }
    var strokes by remember(file) { mutableStateOf<List<PdfInkStroke>>(emptyList()) }
    var currentStroke by remember(file) { mutableStateOf<PdfInkStroke?>(null) }
    var selectedColor by remember { mutableStateOf(android.graphics.Color.RED) }
    var selectedWidth by remember { mutableStateOf(0.006f) }
    var isDrawingEnabled by remember { mutableStateOf(true) }
    var isLoading by remember(file) { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var loadError by remember(file) { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        isLoading = true
        loadError = null
        runCatching {
            withContext(Dispatchers.IO) {
                renderPdfPreviews(file)
            }
        }.onSuccess {
            pages = it
        }.onFailure {
            loadError = it.message ?: "No se pudo preparar el documento para anotar."
        }
        isLoading = false
    }

    LaunchedEffect(strokes.isNotEmpty()) {
        onDirtyChanged(strokes.isNotEmpty())
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar anotaciones") },
            text = {
                Text("Los trazos que no hayas guardado se perderán.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onDirtyChanged(false)
                        onCancel()
                    }
                ) {
                    Text("Descartar", color = Color(0xFFB91C1C))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Continuar editando")
                }
            }
        )
    }

    Column(
        modifier = modifier.background(Color(0xFFD7DEE8))
    ) {
        AnnotationToolbar(
            hasStrokes = strokes.isNotEmpty(),
            isSaving = isSaving,
            selectedColor = selectedColor,
            selectedWidth = selectedWidth,
            isDrawingEnabled = isDrawingEnabled,
            onColorSelected = { selectedColor = it },
            onWidthSelected = { selectedWidth = it },
            onDrawingModeChanged = { isDrawingEnabled = it },
            onUndo = {
                if (strokes.isNotEmpty()) {
                    strokes = strokes.dropLast(1)
                }
            },
            onCancel = {
                if (strokes.isEmpty()) {
                    onCancel()
                } else {
                    showDiscardDialog = true
                }
            },
            onSave = {
                if (strokes.isEmpty() || isSaving) return@AnnotationToolbar
                isSaving = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            saveAnnotatedPdfCopy(
                                context = context,
                                sourceFile = file,
                                strokes = strokes,
                                selectedDocument = selectedDocument
                            )
                        }
                    }.onSuccess { savedFile ->
                        onDirtyChanged(false)
                        onSaved(savedFile)
                    }.onFailure {
                        onError(
                            it.message
                                ?: "No se pudo guardar la copia anotada."
                        )
                    }
                    isSaving = false
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = PmmBlueDeep)
                        Text(
                            text = "Preparando páginas...",
                            color = PmmTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                loadError != null -> {
                    Text(
                        text = loadError.orEmpty(),
                        color = Color(0xFFB91C1C),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 18.dp,
                            vertical = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        items(
                            items = pages,
                            key = PdfPagePreview::index
                        ) { page ->
                            AnnotatablePdfPage(
                                page = page,
                                strokes = strokes.filter {
                                    it.pageIndex == page.index
                                },
                                currentStroke = currentStroke
                                    ?.takeIf { it.pageIndex == page.index },
                                selectedColor = selectedColor,
                                selectedWidth = selectedWidth,
                                isDrawingEnabled = isDrawingEnabled,
                                onStrokeChanged = { currentStroke = it },
                                onStrokeCompleted = {
                                    strokes = strokes + it
                                    currentStroke = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationToolbar(
    hasStrokes: Boolean,
    isSaving: Boolean,
    selectedColor: Int,
    selectedWidth: Float,
    isDrawingEnabled: Boolean,
    onColorSelected: (Int) -> Unit,
    onWidthSelected: (Float) -> Unit,
    onDrawingModeChanged: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit
) {
    Surface(
        color = Color(0xFF111C33),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Anotar documento",
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Dibuja sobre la página",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = { onDrawingModeChanged(!isDrawingEnabled) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isDrawingEnabled) {
                            PmmYellow
                        } else {
                            Color.White
                        }
                    )
                ) {
                    Icon(
                        imageVector = if (isDrawingEnabled) {
                            Icons.Default.Draw
                        } else {
                            Icons.Default.PanToolAlt
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isDrawingEnabled) "Dibujar" else "Mover",
                        fontSize = 10.sp
                    )
                }

                IconButton(
                    onClick = onUndo,
                    enabled = hasStrokes && !isSaving
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Deshacer último trazo",
                        tint = if (hasStrokes) {
                            Color.White
                        } else {
                            Color.White.copy(alpha = 0.3f)
                        }
                    )
                }

                TextButton(
                    onClick = onCancel,
                    enabled = !isSaving
                ) {
                    Text("Cancelar", color = Color.White, fontSize = 10.sp)
                }

                Button(
                    onClick = onSave,
                    enabled = hasStrokes && !isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmYellow,
                        contentColor = PmmTextPrimary,
                        disabledContainerColor = Color.White.copy(alpha = 0.12f),
                        disabledContentColor = Color.White.copy(alpha = 0.35f)
                    )
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = PmmTextPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Guardar", fontSize = 10.sp)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Color",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
                listOf(
                    android.graphics.Color.RED,
                    android.graphics.Color.BLUE,
                    android.graphics.Color.BLACK,
                    0xFFFFB300.toInt()
                ).forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(25.dp)
                            .background(Color(color), CircleShape)
                            .border(
                                width = if (selectedColor == color) 3.dp else 1.dp,
                                color = if (selectedColor == color) {
                                    PmmYellow
                                } else {
                                    Color.White.copy(alpha = 0.5f)
                                },
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                    )
                }

                Spacer(Modifier.weight(1f))
                Text(
                    text = "Trazo",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
                listOf(0.0035f, 0.006f, 0.01f).forEach { width ->
                    Surface(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onWidthSelected(width) },
                        color = if (selectedWidth == width) {
                            PmmYellow.copy(alpha = 0.18f)
                        } else {
                            Color.White.copy(alpha = 0.07f)
                        },
                        shape = RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selectedWidth == width) {
                                PmmYellow
                            } else {
                                Color.White.copy(alpha = 0.16f)
                            }
                        )
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .width(17.dp)
                                    .height(
                                        when (width) {
                                            0.0035f -> 2.dp
                                            0.006f -> 4.dp
                                            else -> 6.dp
                                        }
                                    )
                                    .background(Color.White, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotatablePdfPage(
    page: PdfPagePreview,
    strokes: List<PdfInkStroke>,
    currentStroke: PdfInkStroke?,
    selectedColor: Int,
    selectedWidth: Float,
    isDrawingEnabled: Boolean,
    onStrokeChanged: (PdfInkStroke) -> Unit,
    onStrokeCompleted: (PdfInkStroke) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Página ${page.index + 1}",
            color = PmmTextSecondary,
            fontSize = 10.sp
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(
                    page.sourceWidth.toFloat() /
                        page.sourceHeight.toFloat()
                ),
            color = Color.White,
            shape = RoundedCornerShape(3.dp),
            shadowElevation = 5.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = page.bitmap.asImageBitmap(),
                    contentDescription = "Página ${page.index + 1} del PDF",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("pdf-page-canvas-${page.index}")
                        .then(
                            if (isDrawingEnabled) {
                                Modifier.pointerInput(
                                    page.index,
                                    selectedColor,
                                    selectedWidth
                                ) {
                                    var activeStroke: PdfInkStroke? = null
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            activeStroke = PdfInkStroke(
                                                pageIndex = page.index,
                                                points = listOf(
                                                    offset.toNormalized(
                                                        size.width,
                                                        size.height
                                                    )
                                                ),
                                                color = selectedColor,
                                                widthFraction = selectedWidth
                                            )
                                            onStrokeChanged(activeStroke!!)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val current = activeStroke
                                                ?: PdfInkStroke(
                                                    pageIndex = page.index,
                                                    points = emptyList(),
                                                    color = selectedColor,
                                                    widthFraction = selectedWidth
                                                )
                                            activeStroke = current.copy(
                                                points = current.points +
                                                    change.position.toNormalized(
                                                        size.width,
                                                        size.height
                                                    )
                                            )
                                            onStrokeChanged(activeStroke!!)
                                        },
                                        onDragEnd = {
                                            activeStroke?.let(onStrokeCompleted)
                                            activeStroke = null
                                        },
                                        onDragCancel = {
                                            activeStroke?.let(onStrokeCompleted)
                                            activeStroke = null
                                        }
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    strokes.forEach { stroke ->
                        drawInkStroke(stroke)
                    }
                    currentStroke?.let { drawInkStroke(it) }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkStroke(
    stroke: PdfInkStroke
) {
    if (stroke.points.isEmpty()) return
    val color = Color(stroke.color)
    val strokeWidth = (stroke.widthFraction * size.width)
        .coerceAtLeast(2f)
    if (stroke.points.size == 1) {
        val point = stroke.points.first()
        drawCircle(
            color = color,
            radius = strokeWidth / 2f,
            center = Offset(point.x * size.width, point.y * size.height)
        )
        return
    }
    val path = Path().apply {
        val first = stroke.points.first()
        moveTo(first.x * size.width, first.y * size.height)
        stroke.points.drop(1).forEach { point ->
            lineTo(point.x * size.width, point.y * size.height)
        }
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun Offset.toNormalized(width: Int, height: Int): PdfInkPoint {
    return PdfInkPoint(
        x = (x / width.coerceAtLeast(1)).coerceIn(0f, 1f),
        y = (y / height.coerceAtLeast(1)).coerceIn(0f, 1f)
    )
}

private fun renderPdfPreviews(file: File): List<PdfPagePreview> {
    ParcelFileDescriptor.open(
        file,
        ParcelFileDescriptor.MODE_READ_ONLY
    ).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            return (0 until renderer.pageCount).map { pageIndex ->
                renderer.openPage(pageIndex).use { page ->
                    val targetWidth = 900
                    val targetHeight = (
                        page.height * (targetWidth.toFloat() / page.width)
                        ).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config.ARGB_8888
                    ).apply {
                        eraseColor(android.graphics.Color.WHITE)
                    }
                    page.render(
                        bitmap,
                        null,
                        null,
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )
                    PdfPagePreview(
                        index = pageIndex,
                        sourceWidth = page.width,
                        sourceHeight = page.height,
                        bitmap = bitmap
                    )
                }
            }
        }
    }
}

private fun saveAnnotatedPdfCopy(
    context: Context,
    sourceFile: File,
    strokes: List<PdfInkStroke>,
    selectedDocument: DocumentoPdfDto?
): File {
    val directory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
        "PMMovil"
    ).apply { mkdirs() }
    val timestamp = SimpleDateFormat(
        "yyyyMMdd_HHmmss",
        Locale.US
    ).format(Date())
    val reference = selectedDocument?.creferencia
        ?.replace(Regex("[^A-Za-z0-9_-]"), "_")
        ?: "documento"
    val outputFile = File(
        directory,
        "${reference}_anotado_$timestamp.pdf"
    )

    ParcelFileDescriptor.open(
        sourceFile,
        ParcelFileDescriptor.MODE_READ_ONLY
    ).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            val outputDocument = PdfDocument()
            try {
                for (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { sourcePage ->
                        val renderScale = 2
                        val bitmap = Bitmap.createBitmap(
                            sourcePage.width * renderScale,
                            sourcePage.height * renderScale,
                            Bitmap.Config.ARGB_8888
                        ).apply {
                            eraseColor(android.graphics.Color.WHITE)
                        }
                        try {
                            sourcePage.render(
                                bitmap,
                                null,
                                null,
                                PdfRenderer.Page.RENDER_MODE_FOR_PRINT
                            )
                            val pageInfo = PdfDocument.PageInfo.Builder(
                                sourcePage.width,
                                sourcePage.height,
                                pageIndex + 1
                            ).create()
                            val outputPage = outputDocument.startPage(pageInfo)
                            val canvas = outputPage.canvas
                            val bitmapPaint = Paint(
                                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
                            )
                            canvas.drawBitmap(
                                bitmap,
                                null,
                                android.graphics.RectF(
                                    0f,
                                    0f,
                                    sourcePage.width.toFloat(),
                                    sourcePage.height.toFloat()
                                ),
                                bitmapPaint
                            )
                            drawStrokesOnPdfPage(
                                canvas = canvas,
                                pageWidth = sourcePage.width,
                                pageHeight = sourcePage.height,
                                strokes = strokes.filter {
                                    it.pageIndex == pageIndex
                                }
                            )
                            outputDocument.finishPage(outputPage)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                FileOutputStream(outputFile).use(outputDocument::writeTo)
            } finally {
                outputDocument.close()
            }
        }
    }
    return outputFile
}

private fun drawStrokesOnPdfPage(
    canvas: android.graphics.Canvas,
    pageWidth: Int,
    pageHeight: Int,
    strokes: List<PdfInkStroke>
) {
    strokes.forEach { stroke ->
        if (stroke.points.isEmpty()) return@forEach
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = (stroke.widthFraction * pageWidth).coerceAtLeast(1f)
        }
        if (stroke.points.size == 1) {
            val point = stroke.points.first()
            paint.style = Paint.Style.FILL
            canvas.drawCircle(
                point.x * pageWidth,
                point.y * pageHeight,
                paint.strokeWidth / 2f,
                paint
            )
            return@forEach
        }
        val path = android.graphics.Path().apply {
            val first = stroke.points.first()
            moveTo(first.x * pageWidth, first.y * pageHeight)
            stroke.points.drop(1).forEach { point ->
                lineTo(point.x * pageWidth, point.y * pageHeight)
            }
        }
        canvas.drawPath(path, paint)
    }
}
