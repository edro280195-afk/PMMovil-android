package com.example.pmmovil.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.example.pmmovil.data.model.FotoRevisionDto
import com.example.pmmovil.data.model.FotoSyncEntity
import com.example.pmmovil.data.preferences.CameraPreferences
import com.example.pmmovil.di.NetworkModule
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

private enum class CameraViewMode { CAMERA, GALLERY }
private const val TAG_REVISION_CAMERA = "revision-camera"

sealed interface EvidenceSelection {
    data class Local(val photo: FotoSyncEntity) : EvidenceSelection
    data class Remote(val photo: FotoRevisionDto) : EvidenceSelection
}

fun EvidenceSelection.key(): String = when (this) {
    is EvidenceSelection.Local -> localPhotoKey(photo.id)
    is EvidenceSelection.Remote -> remotePhotoKey(photo.idFotos)
}

fun EvidenceSelection.esDiscrepancia(): Boolean = when (this) {
    is EvidenceSelection.Local -> photo.esDiscrepancia
    is EvidenceSelection.Remote -> photo.esDiscrepancia
}

@Composable
fun CameraScreen(
    crefAgrupada: String,
    cnofact: String,
    cclienteid: String,
    onClose: () -> Unit,
    esDiscrepancia: Boolean = false,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val shutterSide by viewModel.shutterSide.collectAsState()
    val canAddPhotos = viewModel.canAddPhoto()
    val canDeletePhotos = viewModel.canDeletePhoto()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val localPhotos = uiState.localPhotos.filter { localPhoto ->
        localPhoto.remotePhotoId == null ||
            uiState.remotePhotos.none {
                it.idFotos == localPhoto.remotePhotoId
            }
    }
    val allPhotos: List<EvidenceSelection> =
        localPhotos.map(EvidenceSelection::Local) +
            uiState.remotePhotos.map(EvidenceSelection::Remote)
    val pendingPhotoCount = localPhotos.count { !it.synced }

    val snackbarHostState = remember { SnackbarHostState() }
    var viewMode by remember(canAddPhotos) {
        mutableStateOf(if (canAddPhotos) CameraViewMode.CAMERA else CameraViewMode.GALLERY)
    }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }
    var evidenceToDelete by remember { mutableStateOf<EvidenceSelection?>(null) }
    var deleteRequested by remember { mutableStateOf(false) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(canAddPhotos) {
        if (canAddPhotos && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(crefAgrupada, cnofact) {
        viewModel.start(crefAgrupada, cnofact)
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearMessage()
        }
    }

    // El diálogo de "Eliminar" ya NO se cierra al tocar el botón: se queda
    // vivo mostrando "Eliminando..." hasta que la clave salga de
    // deletingKeys (éxito o error), sin importar cuánto tarde la red.
    LaunchedEffect(evidenceToDelete) { deleteRequested = false }
    LaunchedEffect(uiState.deletingKeys) {
        val selection = evidenceToDelete ?: return@LaunchedEffect
        if (deleteRequested && selection.key() !in uiState.deletingKeys) {
            evidenceToDelete = null
            if (viewerIndex != null) {
                val stillExists = allPhotos.any { it.key() == selection.key() }
                if (!stillExists) viewerIndex = null
            }
        }
    }

    if (!hasCameraPermission && canAddPhotos && viewMode == CameraViewMode.CAMERA) {
        CameraPermissionState(
            onRequestPermission = {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onClose = onClose
        )
        return
    }

    // Creada en la composición: el efecto de enlace no puede depender de un
    // estado que el factory del AndroidView rellene después (eso producía dos
    // enlaces por apertura; ver SmartBarcodeCameraView).
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }

    // Tapa el visor hasta que la exposicion automatica converge tras abrir la
    // camara (ver CameraWarmupMonitor). Con plazo de seguridad.
    val warmup = remember { CameraWarmupMonitor("revision") }
    val warmupReady by warmup.isReady.collectAsState()
    val cameraRetry = rememberCameraRetry(warmup)
    LaunchedEffect(warmupReady, cameraRetry.attempt, cameraRetry.failed) {
        while (!warmupReady && !cameraRetry.failed) {
            delay(CAMERA_WARMUP_TICK_MS)
            warmup.tick()
        }
    }

    // La cámara física solo permanece encendida en modo CAMERA. Al pasar a
    // "Mis fotos" se libera (unbind) para no gastar batería ni bloquear el
    // sensor mientras el usuario solo está revisando lo ya tomado. Se
    // relanza también al reintentar una sesión muerta (cameraRetry.attempt).
    LaunchedEffect(viewMode, cameraRetry.attempt) {
        try {
            if (viewMode == CameraViewMode.CAMERA) {
                val preview = Preview.Builder()
                    .also { warmup.attachTo(it) }
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                // La foto es evidencia de aduana, así que la captura se pide
                // buena de origen: MINIMIZE_LATENCY le decía al HAL que
                // recortara procesamiento -- menos reducción de ruido y menos
                // nitidez -- para soltar el disparo antes. Aquí el cuello de
                // botella nunca fue el obturador.
                //
                // La calidad JPEG se fija explícitamente en vez de dejar el
                // default: es el primero de dos pasos de compresión (el segundo
                // es PhotoPipeline al subir), y cada uno hornea sus artefactos
                // en el siguiente.
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(97)
                    .build()
                val camera = CameraOwner.bind(
                    tag = TAG_REVISION_CAMERA,
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    selector = CameraSelector.DEFAULT_BACK_CAMERA,
                    useCases = listOf(preview, capture),
                    onBeforeBind = { warmup.onBound() },
                    onError = { error ->
                        Log.e("CameraScreen", "Error al iniciar cámara", error)
                        cameraRetry.forceFail()
                    }
                )
                if (camera != null) {
                    cameraControl = camera.cameraControl
                    // El zoom de CameraX persiste entre pantallas: se parte de 1x.
                    camera.cameraControl.setZoomRatio(1f)
                    imageCapture = capture
                    awaitCancellation()
                }
            }
        } finally {
            // NonCancellable: release() suspende en un mutex y este finally
            // corre con la corrutina ya cancelada.
            withContext(NonCancellable) { CameraOwner.release(TAG_REVISION_CAMERA) }
            imageCapture = null
            cameraControl = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView }
        )

        if (viewMode == CameraViewMode.CAMERA) {
            CameraWarmupCurtain(
                visible = !warmupReady && !cameraRetry.failed,
                modifier = Modifier.fillMaxSize()
            )

            if (cameraRetry.failed) {
                CameraErrorView(
                    onRetry = cameraRetry.retry,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (showCaptureFlash) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.72f))
                )
            }

            CameraCaptureOverlay(
                crefAgrupada = crefAgrupada,
                cnofact = cnofact,
                esDiscrepancia = esDiscrepancia,
                isFlashOn = isFlashOn,
                isLandscape = isLandscape,
                shutterSide = shutterSide,
                isCapturing = isCapturing || uiState.isSaving,
                canCapture = canAddPhotos,
                totalPhotos = allPhotos.size,
                pendingPhotos = pendingPhotoCount,
                lastPhoto = allPhotos.lastOrNull(),
                onBack = onClose,
                onDone = onClose,
                onToggleFlash = {
                    isFlashOn = !isFlashOn
                    cameraControl?.enableTorch(isFlashOn)
                },
                onOpenGallery = { viewMode = CameraViewMode.GALLERY },
                onOpenLastPhoto = {
                    if (allPhotos.isNotEmpty()) viewerIndex = allPhotos.lastIndex
                },
                onSwapSide = { viewModel.toggleShutterSide() },
                onCapture = {
                    if (!isCapturing) {
                        capturePhoto(
                            context = context,
                            imageCapture = imageCapture,
                            onCaptureStarted = {
                                isCapturing = true
                                showCaptureFlash = true
                            },
                            onSaved = { file ->
                                showCaptureFlash = false
                                isCapturing = false
                                viewModel.savePhoto(
                                    crefAgrupada = crefAgrupada,
                                    cnofact = cnofact,
                                    cclienteid = cclienteid,
                                    localFilePath = file.absolutePath,
                                    esDiscrepancia = esDiscrepancia
                                )
                            },
                            onError = {
                                showCaptureFlash = false
                                isCapturing = false
                            }
                        )
                    }
                }
            )
        } else {
            PhotoGridScreen(
                photos = allPhotos,
                isLoading = uiState.isLoading,
                deletingKeys = uiState.deletingKeys,
                errorKeys = uiState.errorKeys,
                onBack = { viewMode = CameraViewMode.CAMERA },
                onOpenPhoto = { selection ->
                    viewerIndex = allPhotos.indexOfFirst { it.key() == selection.key() }
                        .takeIf { it >= 0 }
                },
                onDelete = { evidenceToDelete = it },
                canDelete = canDeletePhotos,
                modifier = Modifier.fillMaxSize()
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 72.dp)
        )
    }

    viewerIndex?.let { index ->
        if (allPhotos.isNotEmpty()) {
            EvidenceViewerPager(
                photos = allPhotos,
                initialIndex = index.coerceIn(0, allPhotos.lastIndex),
                deletingKeys = uiState.deletingKeys,
                onClose = { viewerIndex = null },
                onDelete = { evidenceToDelete = it },
                canDelete = canDeletePhotos
            )
        }
    }

    evidenceToDelete?.let { selection ->
        val isDeleting = deleteRequested && selection.key() in uiState.deletingKeys
        val hasFailed = selection.key() in uiState.errorKeys
        EvidenceDeleteDialog(
            isDeleting = isDeleting,
            hasFailed = hasFailed,
            onConfirmDelete = {
                deleteRequested = true
                when (selection) {
                    is EvidenceSelection.Local -> {
                        viewModel.deleteLocalPhoto(
                            photo = selection.photo,
                            crefAgrupada = crefAgrupada,
                            cnofact = cnofact
                        )
                    }

                    is EvidenceSelection.Remote -> {
                        viewModel.deleteRemotePhoto(
                            photoId = selection.photo.idFotos,
                            crefAgrupada = crefAgrupada,
                            cnofact = cnofact
                        )
                    }
                }
                // El cierre real ocurre en el LaunchedEffect de arriba,
                // cuando la clave sale de deletingKeys.
            },
            onDismiss = { evidenceToDelete = null }
        )
    }

}

@Composable
private fun CameraPermissionState(
    onRequestPermission: () -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PmmNavyDark),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF8FAFC),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NoPhotography,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = PmmBlueDeep
                )
                Text(
                    text = "Activa la cámara",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "La evidencia se guarda localmente y se sincroniza cuando haya conexión.",
                    color = PmmTextSecondary,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PmmYellow,
                        contentColor = PmmBlueDeep
                    )
                ) {
                    Text("Conceder permiso", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onClose) {
                    Text("Volver")
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// CÁMARA: overlay de controles (se adapta a horizontal + lado del disparador)
// ════════════════════════════════════════════════════════════════════════
@Composable
private fun CameraCaptureOverlay(
    crefAgrupada: String,
    cnofact: String,
    esDiscrepancia: Boolean,
    isFlashOn: Boolean,
    isLandscape: Boolean,
    shutterSide: CameraPreferences.ShutterSide,
    isCapturing: Boolean,
    canCapture: Boolean,
    totalPhotos: Int,
    pendingPhotos: Int,
    lastPhoto: EvidenceSelection?,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onToggleFlash: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenLastPhoto: () -> Unit,
    onSwapSide: () -> Unit,
    onCapture: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        CameraTopBar(
            crefAgrupada = crefAgrupada,
            cnofact = cnofact,
            isFlashOn = isFlashOn,
            esDiscrepancia = esDiscrepancia,
            totalPhotos = totalPhotos,
            isLandscape = isLandscape,
            shutterSide = shutterSide,
            onBack = onBack,
            onOpenGallery = onOpenGallery,
            onToggleFlash = onToggleFlash,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        CaptureFrame(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(if (isLandscape) 0.5f else 0.84f)
                .aspectRatio(4f / 3f)
        )

        if (isLandscape) {
            IconButton(
                onClick = onSwapSide,
                modifier = Modifier
                    .align(if (shutterSide == CameraPreferences.ShutterSide.RIGHT) Alignment.CenterStart else Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Mover controles al otro lado",
                    tint = Color.White
                )
            }
        }

        CaptureBottomBar(
            isLandscape = isLandscape,
            shutterSide = shutterSide,
            isCapturing = isCapturing,
            canCapture = canCapture,
            pendingPhotos = pendingPhotos,
            lastPhoto = lastPhoto,
            totalPhotos = totalPhotos,
            onDone = onDone,
            onOpenLastPhoto = onOpenLastPhoto,
            onCapture = onCapture,
            modifier = if (isLandscape) {
                Modifier
                    .align(
                        if (shutterSide == CameraPreferences.ShutterSide.RIGHT) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        }
                    )
                    .fillMaxHeight()
            } else {
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            }
        )
    }
}

@Composable
private fun CameraTopBar(
    crefAgrupada: String,
    cnofact: String,
    isFlashOn: Boolean,
    esDiscrepancia: Boolean,
    totalPhotos: Int,
    isLandscape: Boolean,
    shutterSide: CameraPreferences.ShutterSide,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (esDiscrepancia) Color(0xFFB42318) else Color.White.copy(alpha = 0.14f)
    // En horizontal, el raíl de controles vive pegado a uno de los lados
    // (~130dp con su propio margen): sin este hueco, el texto de referencia
    // quedaba tapado detrás del raíl.
    val railClearance = if (isLandscape) 130.dp else 0.dp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(
                start = 14.dp + if (isLandscape && shutterSide == CameraPreferences.ShutterSide.LEFT) railClearance else 0.dp,
                end = 14.dp + if (isLandscape && shutterSide == CameraPreferences.ShutterSide.RIGHT) railClearance else 0.dp,
                top = 10.dp,
                bottom = 10.dp
            )
            .clip(RoundedCornerShape(24.dp))
            .background(
                if (esDiscrepancia) Color(0xFF4A0F0F).copy(alpha = 0.72f) else Color.Black.copy(alpha = 0.46f)
            )
            .border(1.dp, accentColor.copy(alpha = if (esDiscrepancia) 0.55f else 0.14f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (esDiscrepancia) "Evidencia de discrepancia" else "Evidencia fotográfica",
                color = if (esDiscrepancia) Color(0xFFFCA5A5) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$crefAgrupada · Factura $cnofact · $totalPhotos foto${if (totalPhotos == 1) "" else "s"}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenGallery) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Mis fotos",
                tint = Color.White
            )
        }
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (isFlashOn) PmmBlueLight else Color.White
            )
        }
    }
}

@Composable
private fun CaptureFrame(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.58f), shape)
            .padding(14.dp)
    ) {
        val cornerLength = 34.dp
        val stroke = 3.dp

        Box(Modifier.size(cornerLength, stroke).align(Alignment.TopStart).background(PmmBlueLight, RoundedCornerShape(50)))
        Box(Modifier.size(stroke, cornerLength).align(Alignment.TopStart).background(PmmBlueLight, RoundedCornerShape(50)))
        Box(Modifier.size(cornerLength, stroke).align(Alignment.TopEnd).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.size(stroke, cornerLength).align(Alignment.TopEnd).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.size(cornerLength, stroke).align(Alignment.BottomStart).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.size(stroke, cornerLength).align(Alignment.BottomStart).background(Color.White, RoundedCornerShape(50)))
        Box(Modifier.size(cornerLength, stroke).align(Alignment.BottomEnd).background(PmmBlueLight, RoundedCornerShape(50)))
        Box(Modifier.size(stroke, cornerLength).align(Alignment.BottomEnd).background(PmmBlueLight, RoundedCornerShape(50)))
    }
}

@Composable
private fun CaptureBottomBar(
    isLandscape: Boolean,
    shutterSide: CameraPreferences.ShutterSide,
    isCapturing: Boolean,
    canCapture: Boolean,
    pendingPhotos: Int,
    lastPhoto: EvidenceSelection?,
    totalPhotos: Int,
    onDone: () -> Unit,
    onOpenLastPhoto: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerModifier = if (isLandscape) {
        Modifier
            .navigationBarsPadding()
            .padding(vertical = 14.dp, horizontal = 10.dp)
            .width(108.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.60f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .padding(vertical = 20.dp, horizontal = 10.dp)
    } else {
        Modifier
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.60f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
    }

    if (isLandscape) {
        Column(
            modifier = modifier.then(containerModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            LastPhotoThumb(photo = lastPhoto, onClick = onOpenLastPhoto)
            ShutterButton(isCapturing = isCapturing, canCapture = canCapture, onClick = onCapture)
            DoneChip(totalPhotos = totalPhotos, onClick = onDone)
        }
    } else {
        Column(
            modifier = modifier.then(containerModifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LastPhotoThumb(photo = lastPhoto, onClick = onOpenLastPhoto)
                ShutterButton(isCapturing = isCapturing, canCapture = canCapture, onClick = onCapture)
                DoneChip(totalPhotos = totalPhotos, onClick = onDone)
            }
            // La altura queda reservada desde el principio, pero el texto ya
            // no forma parte de la celda del boton. Asi el numero de fotos
            // pendientes nunca cambia el ancho de la fila ni mueve el disparo.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                contentAlignment = Alignment.Center
            ) {
                if (pendingPhotos > 0) {
                    Text(
                        text = "$pendingPhotos pendientes de sincronizar",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 9.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LastPhotoThumb(
    photo: EvidenceSelection?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(enabled = photo != null, onClick = onClick)
    ) {
        if (photo == null) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = "Sin fotos",
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.align(Alignment.Center).size(22.dp)
            )
        } else {
            EvidenceThumbnailImage(photo = photo, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ShutterButton(isCapturing: Boolean, canCapture: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .clickable(enabled = canCapture && !isCapturing, onClick = onClick),
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(4.dp, Color.White)
    ) {
            Box(
                modifier = Modifier.padding(5.dp).clip(CircleShape).background(
                    if (canCapture) PmmYellow else Color.White.copy(alpha = 0.25f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = PmmBlueDeep,
                    strokeWidth = 3.dp
                )
            }
        }
    }
}

@Composable
private fun DoneChip(
    totalPhotos: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (totalPhotos > 0) PmmBlueDeep else Color.White.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Listo", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            if (totalPhotos > 0) {
                Spacer(Modifier.width(6.dp))
                Surface(color = Color.White, shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = totalPhotos.toString(),
                        color = PmmBlueDeep,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// MIS FOTOS: grid de sesión, agrupado por tipo, con estados por foto
// ════════════════════════════════════════════════════════════════════════
@Composable
fun PhotoGridScreen(
    photos: List<EvidenceSelection>,
    isLoading: Boolean,
    deletingKeys: Set<String>,
    errorKeys: Set<String>,
    onBack: () -> Unit,
    onOpenPhoto: (EvidenceSelection) -> Unit,
    onDelete: (EvidenceSelection) -> Unit,
    canDelete: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.background(Color(0xFF0C0F16))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver a la cámara",
                    tint = Color.White
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Fotos de esta factura",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${photos.size} foto${if (photos.size == 1) "" else "s"}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp
                )
            }
        }

        if (isLoading && photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PmmYellow)
            }
        } else if (photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Aún no hay fotos. Regresa a la cámara para tomar la primera.",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val normales = photos.filter { !it.esDiscrepancia() }
            val discrepancias = photos.filter { it.esDiscrepancia() }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                if (normales.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel("Evidencia normal · ${normales.size}")
                    }
                    items(normales, key = { it.key() }) { photo ->
                        PhotoTile(
                            photo = photo,
                            isDeleting = photo.key() in deletingKeys,
                            hasError = photo.key() in errorKeys,
                            onClick = { onOpenPhoto(photo) },
                            onDelete = { onDelete(photo) },
                            onRetryDelete = { onDelete(photo) },
                            canDelete = canDelete
                        )
                    }
                }
                if (discrepancias.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel("Evidencia de discrepancia · ${discrepancias.size}")
                    }
                    items(discrepancias, key = { it.key() }) { photo ->
                        PhotoTile(
                            photo = photo,
                            isDeleting = photo.key() in deletingKeys,
                            hasError = photo.key() in errorKeys,
                            onClick = { onOpenPhoto(photo) },
                            onDelete = { onDelete(photo) },
                            onRetryDelete = { onDelete(photo) },
                            canDelete = canDelete
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
fun PhotoTile(
    photo: EvidenceSelection,
    isDeleting: Boolean,
    hasError: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRetryDelete: () -> Unit,
    canDelete: Boolean
) {
    val syncError = (photo as? EvidenceSelection.Local)?.photo?.errorMessage
    val isUploading = photo is EvidenceSelection.Local && !photo.photo.synced && syncError == null
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(PmmNavyDark)
            .border(
                width = if (hasError) 2.dp else 0.dp,
                color = if (hasError) PmmRed else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = !isDeleting, onClick = onClick)
    ) {
        EvidenceThumbnailImage(photo = photo, modifier = Modifier.fillMaxSize())

        if (isUploading) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = Color.White)
            }
        } else {
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
                color = if (photo.esDiscrepancia()) PmmRed else PmmGreen,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (photo.esDiscrepancia()) Icons.Default.Warning else Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp).size(12.dp)
                )
            }
        }

        if (!isDeleting && !hasError) {
            IconButton(
                onClick = onDelete,
                enabled = canDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Eliminar foto",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (syncError != null && !isDeleting) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.dp),
                color = PmmRed,
                shape = RoundedCornerShape(9.dp)
            ) {
                Text(
                    text = "No se pudo sincronizar",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 5.dp, horizontal = 3.dp)
                )
            }
        }

        // Estado "eliminando": congela la miniatura entera para que no se
        // pueda volver a tocar Eliminar mientras el servidor responde.
        AnimatedVisibility(
            visible = isDeleting,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = Color.White)
            }
        }

        if (hasError && !isDeleting) {
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(4.dp),
                color = PmmRed,
                shape = RoundedCornerShape(9.dp),
                onClick = onRetryDelete
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color.White, modifier = Modifier.size(11.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Reintentar", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EvidenceThumbnailImage(
    photo: EvidenceSelection,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Antes, cuando Coil fallaba en cargar la foto remota (401/404/red caída),
    // AsyncImage no dejaba ningún rastro visual: la miniatura se quedaba en el
    // fondo azul marino del Box de PhotoTile, indistinguible de "todavía
    // cargando" -- el usuario veía solo un recuadro azul vacío sin ninguna
    // pista de qué pasó. Ahora se rastrea el estado y, si falla, se muestra un
    // ícono explícito con botón para reintentar.
    var retryTick by remember(photo.key()) { mutableIntStateOf(0) }
    var loadState by remember(photo.key(), retryTick) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val model = when (photo) {
        is EvidenceSelection.Local -> photo.photo.localFilePath.let(::File)
        is EvidenceSelection.Remote -> ImageRequest.Builder(context)
            .data("${NetworkModule.BASE_URL}Foto/ver/${photo.photo.idFotos}")
            // NO agregar aquí el header Authorization: el ImageLoader global
            // de Coil (ver PMMovilApplication.newImageLoader) ya lo inyecta a
            // toda petición vía su propio OkHttpClient. Agregarlo también
            // aquí (como estaba antes) mandaba DOS headers "Authorization" en
            // la misma petición -- el servidor no lo interpreta como un
            // Bearer válido y la imagen nunca carga. Esta era la causa real
            // del recuadro azul vacío en la galería de evidencia.
            //
            // Cache-buster: sin esto, un reintento manual reutilizaría el
            // mismo resultado fallido que Coil ya tiene en su cache de claves.
            .setParameter("retry", retryTick)
            .crossfade(true)
            .build()
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = model,
            contentDescription = if (photo.esDiscrepancia()) "Evidencia de discrepancia" else "Evidencia fotográfica",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            onState = { loadState = it }
        )
        if (loadState is AsyncImagePainter.State.Error) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { retryTick++ },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = "No se pudo cargar la foto. Toca para reintentar.",
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Reintentar",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// VISOR: swipe entre fotos + zoom (doble toque, arrastre mientras hay zoom)
// ════════════════════════════════════════════════════════════════════════
@Composable
fun EvidenceViewerPager(
    photos: List<EvidenceSelection>,
    initialIndex: Int,
    deletingKeys: Set<String>,
    onClose: () -> Unit,
    onDelete: (EvidenceSelection) -> Unit,
    canDelete: Boolean
) {
    val pagerState = rememberPagerState(initialPage = initialIndex) { photos.size }
    var zoomedPage by remember { mutableStateOf(-1) }
    var showHint by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2200)
        showHint = false
    }
    LaunchedEffect(pagerState.currentPage) { zoomedPage = -1 }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = zoomedPage != pagerState.currentPage,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photo = photos.getOrNull(page) ?: return@HorizontalPager
            ZoomablePage(
                onZoomChanged = { zoomed -> zoomedPage = if (zoomed) page else -1 }
            ) {
                EvidenceThumbnailImage(
                    photo = photo,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        AnimatedVisibility(
            visible = showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(999.dp)) {
                Text(
                    text = "doble toque para zoom",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        val currentPhoto = photos.getOrNull(pagerState.currentPage)
        val isDeletingCurrent = currentPhoto?.let { it.key() in deletingKeys } ?: false

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp, ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cerrar", tint = Color.White)
            }
            Surface(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                color = Color.Transparent
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(
                onClick = { currentPhoto?.let(onDelete) },
                enabled = canDelete && !isDeletingCurrent
            ) {
                if (isDeletingCurrent) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Eliminar evidencia",
                        tint = Color(0xFFFCA5A5)
                    )
                }
            }
        }

        if (photos.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                photos.indices.forEach { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 16.dp else 6.dp, 6.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.35f))
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(i) }
                            }
                    )
                }
            }
        }
    }
}

/** Diálogo de confirmación compartido entre CameraScreen y GalleryScreen: no
 * se cierra al tocar "Eliminar" -- se queda con spinner ("Eliminando…") hasta
 * que la operación real termine (éxito o error), para no volver a inducir el
 * "tócalo varias veces" que causaba el bug de fotos fantasma. */
@Composable
fun EvidenceDeleteDialog(
    isDeleting: Boolean,
    hasFailed: Boolean,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        title = {
            Text(
                text = if (hasFailed) "Reintentar eliminación" else "Eliminar evidencia",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(text = "¿Deseas eliminar esta foto? Esta acción no se puede deshacer.")
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PmmRed,
                    contentColor = Color.White,
                    disabledContainerColor = PmmRed.copy(alpha = 0.5f),
                    disabledContentColor = Color.White
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Eliminando…")
                } else {
                    Text(if (hasFailed) "Reintentar" else "Eliminar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) {
                Text("Cancelar")
            }
        }
    )
}

/** Zoom con doble toque (1x <-> 2.4x); mientras hay zoom, se puede arrastrar
 * con un dedo para ver el resto de la foto. */
@Composable
fun ZoomablePage(
    onZoomChanged: (Boolean) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        onZoomChanged(false)
                    } else {
                        scale = 2.4f
                        onZoomChanged(true)
                    }
                })
            }
            .pointerInput(scale > 1f) {
                if (scale > 1f) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        offsetX += drag.x
                        offsetY += drag.y
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
        ) {
            content()
        }
    }
}

private fun capturePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    onCaptureStarted: () -> Unit,
    onSaved: (File) -> Unit,
    onError: () -> Unit
) {
    val capture = imageCapture ?: run {
        onError()
        return
    }
    val file = createTempPictureFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    onCaptureStarted()

    capture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(
                outputFileResults: ImageCapture.OutputFileResults
            ) {
                onSaved(file)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e(
                    "CameraScreen",
                    "Error al tomar foto: ${exception.message}",
                    exception
                )
                onError()
            }
        }
    )
}

private fun createTempPictureFile(context: Context): File {
    val timestamp = SimpleDateFormat(
        "yyyyMMdd_HHmmss_SSS",
        Locale.US
    ).format(System.currentTimeMillis())
    val directory = context.getExternalFilesDir(
        android.os.Environment.DIRECTORY_PICTURES
    )
    return File(directory, "PMM_$timestamp.jpg")
}

/** Cadencia con la que se vencen los plazos del arranque de camara. */
private const val CAMERA_WARMUP_TICK_MS = 200L
