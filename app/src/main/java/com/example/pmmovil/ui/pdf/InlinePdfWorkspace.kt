package com.example.pmmovil.ui.pdf

import android.content.Context
import android.content.Intent
import android.print.PrintManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pmmovil.data.model.DocumentoPdfDto
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PdfPendingAction {
    SAVE,
    SHARE,
    PRINT
}

@Composable
fun InlinePdfWorkspace(
    crefAgrupada: String,
    initialReference: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    showExpandButton: Boolean = true,
    onToggleExpanded: () -> Unit = {},
    viewModel: PdfWorkspaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var isDocumentMenuOpen by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PdfPendingAction?>(null) }
    var textPage by remember { mutableStateOf(0) }
    var textPoint by remember { mutableStateOf<PdfNormalizedPoint?>(null) }
    var textValue by remember { mutableStateOf("") }
    var toolName by rememberSaveable { mutableStateOf(PdfAnnotationTool.PAN.name) }
    val selectedTool = PdfAnnotationTool.valueOf(toolName)
    val visibleFile = state.annotatedFile ?: state.documentFile
    var isMoreMenuOpen by remember { mutableStateOf(false) }
    var fitName by rememberSaveable { mutableStateOf(PdfFitMode.WIDTH.name) }
    val fitMode = PdfFitMode.valueOf(fitName)

    // En el panel dividido manda el ancho: el panel es apaisado y la factura
    // vertical, así que encajar la hoja completa la deja diminuta. A pantalla
    // completa se invierte, ahí lo que sirve es ver la página entera.
    LaunchedEffect(isExpanded) {
        fitName = if (isExpanded) PdfFitMode.PAGE.name else PdfFitMode.WIDTH.name
    }

    val toggleFit = {
        fitName = if (fitMode == PdfFitMode.WIDTH) {
            PdfFitMode.PAGE.name
        } else {
            PdfFitMode.WIDTH.name
        }
    }
    val fitIcon = if (fitMode == PdfFitMode.WIDTH) {
        Icons.Default.FitScreen
    } else {
        Icons.Default.SwapHoriz
    }
    val fitLabel = if (fitMode == PdfFitMode.WIDTH) {
        "Ver la página completa"
    } else {
        "Ajustar al ancho del panel"
    }

    LaunchedEffect(crefAgrupada) {
        viewModel.start(crefAgrupada, initialReference)
    }

    fun showMessage(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun runAction(action: PdfPendingAction, file: File) {
        when (action) {
            PdfPendingAction.SAVE -> showMessage("Copia anotada lista en Documentos/PMMovil.")
            PdfPendingAction.SHARE -> sharePdf(context, file)
            PdfPendingAction.PRINT -> printPdf(context, file)
        }
    }

    fun export(action: PdfPendingAction) {
        val sourceFile = visibleFile ?: return
        if (isExporting) return
        if (state.annotations.isEmpty()) {
            if (action == PdfPendingAction.SAVE) {
                showMessage("Este documento no tiene marcas para exportar.")
            } else {
                runAction(action, sourceFile)
            }
            return
        }

        isExporting = true
        pendingAction = action
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    saveAnnotatedPdfCopy(
                        context = context,
                        sourceFile = sourceFile,
                        annotations = state.annotations,
                        selectedDocument = state.selectedDocument
                    )
                }
            }.onSuccess { savedFile ->
                viewModel.markAnnotatedFileSaved(savedFile)
                val actionToRun = pendingAction ?: PdfPendingAction.SAVE
                pendingAction = null
                isExporting = false
                runAction(actionToRun, savedFile)
            }.onFailure { error ->
                pendingAction = null
                isExporting = false
                showMessage(error.message ?: "No se pudo guardar la copia anotada.")
            }
        }
    }

    textPoint?.let { point ->
        AlertDialog(
            onDismissRequest = {
                textPoint = null
                textValue = ""
            },
            title = { Text("Agregar nota al documento") },
            text = {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Texto") },
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textValue.isNotBlank()) {
                            viewModel.addAnnotation(
                                PdfAnnotation(
                                    pageIndex = textPage,
                                    tool = PdfAnnotationTool.TEXT,
                                    points = listOf(point),
                                    color = android.graphics.Color.rgb(17, 24, 39),
                                    text = textValue.trim()
                                )
                            )
                        }
                        textPoint = null
                        textValue = ""
                    },
                    enabled = textValue.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
                ) { Text("Agregar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    textPoint = null
                    textValue = ""
                }) { Text("Cancelar") }
            }
        )
    }

    Surface(
        modifier = modifier.clipToBounds(),
        color = Color(0xFF111827),
        border = BorderStroke(1.dp, PmmNavyBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // La barra se adapta al ancho del panel. Antes medía 734 dp fijos
            // dentro de un panel que suele tener 514, así que Compartir, Ampliar
            // y Cerrar quedaban fuera de pantalla, alcanzables solo desplazando
            // una fila que nadie sabía que se desplazaba.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val paneWidth = maxWidth
                // El reparto por omision deja el panel en 514 dp: el umbral va
                // debajo de esa cifra para que ahi se vean las cuatro
                // herramientas. Lo que cede terreno es el nombre del documento,
                // que siempre se puede consultar en el selector.
                val showAllTools = paneWidth >= 500.dp
                val showFitAndUndo = paneWidth >= 430.dp
                val canUse = visibleFile != null && !isExporting

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1730))
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box {
                        PdfToolbarButton(
                            icon = Icons.Default.PictureAsPdf,
                            label = "Elegir documento",
                            enabled = state.documents.isNotEmpty() && !isExporting,
                            onClick = { isDocumentMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = isDocumentMenuOpen,
                            onDismissRequest = { isDocumentMenuOpen = false },
                            modifier = Modifier
                                .background(PmmBgSurface)
                                .width(310.dp)
                        ) {
                            state.documents.forEach { document ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = document.label
                                                    ?: document.creferencia.orEmpty(),
                                                color = PmmTextPrimary,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = if (document.tipo == 16) {
                                                    "Bill of Lading"
                                                } else {
                                                    "Factura comercial"
                                                },
                                                color = PmmTextSecondary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    },
                                    onClick = {
                                        // Cada documento conserva sus propias
                                        // marcas, asi que se puede saltar entre
                                        // ellos sin exportar nada.
                                        isDocumentMenuOpen = false
                                        viewModel.selectDocument(document)
                                    }
                                )
                            }
                        }
                    }

                    // El nombre se queda con el espacio que sobre: si el panel se
                    // angosta desaparece antes de empujar a los botones fuera.
                    Text(
                        text = state.selectedDocument?.label ?: "Documento comercial",
                        color = Color.White,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    )

                    PdfToolbarButton(
                        icon = Icons.Default.PanToolAlt,
                        label = "Mover y hacer zoom",
                        enabled = canUse,
                        active = selectedTool == PdfAnnotationTool.PAN,
                        onClick = { toolName = PdfAnnotationTool.PAN.name }
                    )
                    PdfToolbarButton(
                        icon = Icons.Default.Draw,
                        label = "Dibujar sobre el PDF",
                        enabled = canUse,
                        active = selectedTool == PdfAnnotationTool.INK,
                        onClick = { toolName = PdfAnnotationTool.INK.name }
                    )
                    if (showAllTools) {
                        PdfToolbarButton(
                            icon = Icons.Default.Highlight,
                            label = "Resaltar una zona",
                            enabled = canUse,
                            active = selectedTool == PdfAnnotationTool.HIGHLIGHT,
                            onClick = { toolName = PdfAnnotationTool.HIGHLIGHT.name }
                        )
                        PdfToolbarButton(
                            icon = Icons.Default.Edit,
                            label = "Agregar texto",
                            enabled = canUse,
                            active = selectedTool == PdfAnnotationTool.TEXT,
                            onClick = { toolName = PdfAnnotationTool.TEXT.name }
                        )
                    }
                    if (showFitAndUndo) {
                        PdfToolbarButton(
                            icon = Icons.AutoMirrored.Filled.Undo,
                            label = "Deshacer anotación",
                            enabled = state.annotations.isNotEmpty() && !isExporting,
                            onClick = viewModel::undoLastAnnotation
                        )
                        PdfToolbarButton(
                            icon = fitIcon,
                            label = fitLabel,
                            enabled = canUse,
                            onClick = toggleFit
                        )
                    }

                    Box {
                        PdfToolbarButton(
                            icon = Icons.Default.MoreVert,
                            label = "Más acciones",
                            enabled = canUse,
                            onClick = { isMoreMenuOpen = true }
                        )
                        DropdownMenu(
                            expanded = isMoreMenuOpen,
                            onDismissRequest = { isMoreMenuOpen = false },
                            modifier = Modifier
                                .background(PmmBgSurface)
                                .width(268.dp)
                        ) {
                            if (!showAllTools) {
                                PdfMenuItem(Icons.Default.Highlight, "Resaltar una zona") {
                                    toolName = PdfAnnotationTool.HIGHLIGHT.name
                                    isMoreMenuOpen = false
                                }
                                PdfMenuItem(Icons.Default.Edit, "Agregar texto") {
                                    toolName = PdfAnnotationTool.TEXT.name
                                    isMoreMenuOpen = false
                                }
                            }
                            if (!showFitAndUndo) {
                                PdfMenuItem(
                                    icon = Icons.AutoMirrored.Filled.Undo,
                                    label = "Deshacer anotación",
                                    enabled = state.annotations.isNotEmpty()
                                ) {
                                    viewModel.undoLastAnnotation()
                                    isMoreMenuOpen = false
                                }
                                PdfMenuItem(fitIcon, fitLabel) {
                                    toggleFit()
                                    isMoreMenuOpen = false
                                }
                            }
                            PdfMenuItem(Icons.Default.Save, "Exportar copia anotada") {
                                isMoreMenuOpen = false
                                export(PdfPendingAction.SAVE)
                            }
                            PdfMenuItem(Icons.Default.Print, "Imprimir documento") {
                                isMoreMenuOpen = false
                                export(PdfPendingAction.PRINT)
                            }
                            PdfMenuItem(Icons.Default.Share, "Compartir documento") {
                                isMoreMenuOpen = false
                                export(PdfPendingAction.SHARE)
                            }
                        }
                    }

                    if (showExpandButton) {
                        PdfToolbarButton(
                            icon = if (isExpanded) {
                                Icons.Default.CloseFullscreen
                            } else {
                                Icons.Default.OpenInFull
                            },
                            label = if (isExpanded) {
                                "Salir de pantalla completa"
                            } else {
                                "Ver a pantalla completa"
                            },
                            enabled = canUse,
                            active = isExpanded,
                            onClick = onToggleExpanded
                        )
                    }
                    // Cerrar ya no advierte nada: las anotaciones se guardan
                    // solas conforme se dibujan y siguen ahi al volver.
                    IconButton(
                        onClick = onClose,
                        enabled = !isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar documento",
                            tint = Color.White
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFCBD5E1))
                    .clipToBounds()
            ) {
                when {
                    state.isLoadingDocuments || state.isLoadingPdf -> {
                        PdfLoadingState(isLoadingDocuments = state.isLoadingDocuments)
                    }

                    state.error != null -> {
                        PdfErrorState(message = state.error.orEmpty(), onRetry = viewModel::retry)
                    }

                    visibleFile != null -> {
                        AhmerPdfViewer(
                            file = visibleFile,
                            annotations = state.annotations,
                            selectedTool = selectedTool,
                            fitMode = fitMode,
                            initialPage = state.currentPage,
                            onPageChanged = viewModel::setCurrentPage,
                            onLoaded = { },
                            onError = ::showMessage,
                            onAnnotationCreated = viewModel::addAnnotation,
                            onTextRequested = { page, point ->
                                textPage = page
                                textPoint = point
                                textValue = ""
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> PdfEmptyState()
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfLoadingState(isLoadingDocuments: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = PmmBlueDeep)
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isLoadingDocuments) "Buscando documentos..." else "Preparando PDF...",
            color = PmmTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PdfErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = PmmTextSecondary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(text = message, color = PmmTextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = PmmBlueDeep)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Reintentar")
        }
    }
}

@Composable
private fun PdfEmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PictureAsPdf,
            contentDescription = null,
            tint = PmmTextSecondary,
            modifier = Modifier.size(44.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("Selecciona un documento para abrirlo.", color = PmmTextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfToolbarButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF1E293B)) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (active) PmmYellow.copy(alpha = 0.18f) else Color.Transparent,
                contentColor = if (active) PmmYellow else Color.White,
                disabledContentColor = Color.White.copy(alpha = 0.28f)
            )
        ) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun PdfMenuItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(text = label, color = PmmTextPrimary, fontSize = 13.sp) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) PmmBlueDeep else PmmTextSecondary,
                modifier = Modifier.size(19.dp)
            )
        },
        enabled = enabled,
        onClick = onClick
    )
}

private fun printPdf(context: Context, file: File) {
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
    printManager.print(file.nameWithoutExtension, PmmPdfPrintAdapter(file), null)
}

private fun sharePdf(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Compartir documento PDF"))
}
