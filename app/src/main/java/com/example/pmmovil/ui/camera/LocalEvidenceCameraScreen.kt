package com.example.pmmovil.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.NoPhotography
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.pmmovil.data.camera.FocusGatePolicy
import com.example.pmmovil.data.camera.SmartStillCapture
import com.example.pmmovil.data.camera.StillCaptureMode
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyDark
import com.example.pmmovil.ui.theme.PmmTextSecondary
import com.example.pmmovil.ui.theme.PmmYellow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

private const val TAG_LOCAL_EVIDENCE_CAMERA = "local-evidence-camera"

data class LocalEvidencePhoto(
    val id: String,
    val model: Any,
    val label: String,
    val status: String,
    val canDelete: Boolean = true
)

@Composable
fun LocalEvidenceCameraScreen(
    title: String,
    subtitle: String,
    photos: List<LocalEvidencePhoto>,
    maxPhotos: Int? = null,
    directoryName: String,
    filePrefix: String,
    canCapture: Boolean = true,
    onPhotoCaptured: (String) -> Unit,
    onDeletePhoto: (LocalEvidencePhoto) -> Unit,
    onClose: () -> Unit
) {
    // Sin esto, el boton de atras del sistema no lo intercepta esta pantalla:
    // sale de todo el modulo (ej. Entrada Caseta) y se pierde la captura en curso.
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedPhoto by remember { mutableStateOf<LocalEvidencePhoto?>(null) }
    var photoToDelete by remember { mutableStateOf<LocalEvidencePhoto?>(null) }
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

    LaunchedEffect(canCapture) {
        if (canCapture && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!canCapture) {
        LocalCaptureDisabledState(onClose = onClose)
        return
    }

    if (!hasCameraPermission && canCapture) {
        LocalCameraPermissionState(
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
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
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var isFlashOn by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var showCaptureFlash by remember { mutableStateOf(false) }

    // Tapa el visor hasta que la exposicion automatica converge tras abrir la
    // camara (ver CameraWarmupMonitor). Con plazo de seguridad.
    val warmup = remember { CameraWarmupMonitor("evidencia") }
    val warmupReady by warmup.isReady.collectAsState()
    val cameraRetry = rememberCameraRetry(warmup)
    LaunchedEffect(warmupReady, cameraRetry.attempt, cameraRetry.failed) {
        while (!warmupReady && !cameraRetry.failed) {
            delay(WARMUP_TICK_MS)
            warmup.tick()
        }
    }

    // El destello es un pulso corto, no un velo que dura lo que tarde la camara en procesar.
    LaunchedEffect(showCaptureFlash) {
        if (showCaptureFlash) {
            delay(CAPTURE_FLASH_MS)
            showCaptureFlash = false
        }
    }

    LaunchedEffect(cameraRetry.attempt) {
        try {
            val preview = Preview.Builder()
                .also { warmup.attachTo(it) }
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }
            // El modo sale de StillCaptureMode: por omisión captura rápida con espera de enfoque, que en
            // la prueba de cerca salió más nítida y más veloz que MAXIMIZE_QUALITY. En debug se puede
            // forzar otro para medir.
            val capture = ImageCapture.Builder()
                .setCaptureMode(StillCaptureMode.resolve(context))
                .setJpegQuality(LOCAL_EVIDENCE_JPEG_QUALITY)
                .build()
            val camera = CameraOwner.bind(
                tag = TAG_LOCAL_EVIDENCE_CAMERA,
                context = context,
                lifecycleOwner = lifecycleOwner,
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCases = listOf(preview, capture),
                onBeforeBind = { warmup.onBound() },
                onError = { error ->
                    Log.e(TAG_LOCAL_EVIDENCE_CAMERA, "Error al iniciar cámara local", error)
                    cameraRetry.forceFail()
                }
            )
            if (camera != null) {
                StillCaptureMode.logSupport(camera.cameraInfo)
                imageCapture = capture
                cameraControl = camera.cameraControl
                cameraInfo = camera.cameraInfo
                zoomRatio = 1f
                camera.cameraControl.setZoomRatio(1f)
                awaitCancellation()
            }
        } finally {
            withContext(NonCancellable) { CameraOwner.release(TAG_LOCAL_EVIDENCE_CAMERA) }
            imageCapture = null
            cameraControl = null
            cameraInfo = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val useSideGallery = maxWidth >= 840.dp

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(Color(0xFF090D16))
            ) {
                LocalCameraTopBar(
                    title = title,
                    subtitle = subtitle,
                    isFlashOn = isFlashOn,
                    onBack = onClose,
                    onDone = onClose,
                    onToggleFlash = {
                        isFlashOn = !isFlashOn
                        cameraControl?.enableTorch(isFlashOn)
                    }
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(cameraControl, cameraInfo) {
                                detectTransformGestures { _, _, zoom, _ ->
                                    val state = cameraInfo?.zoomState?.value
                                    val minZoom = state?.minZoomRatio ?: 1f
                                    val maxZoom = state?.maxZoomRatio ?: 8f
                                    val nextZoom = (zoomRatio * zoom).coerceIn(minZoom, maxZoom)
                                    zoomRatio = nextZoom
                                    cameraControl?.setZoomRatio(nextZoom)
                                }
                            },
                        factory = { previewView }
                    )

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
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF090D16))
                        .navigationBarsPadding()
                ) {
                    if (!useSideGallery) {
                        LocalEvidenceFilmstrip(
                            photos = photos,
                            onOpen = { selectedPhoto = it },
                            onDelete = { photoToDelete = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }

                    LocalCaptureControls(
                        isCapturing = isCapturing,
                        canCapture = canCapture,
                        totalPhotos = photos.size,
                        maxPhotos = maxPhotos,
                        zoomRatio = zoomRatio,
                        onCapture = {
                            if (!isCapturing && (maxPhotos == null || photos.size < maxPhotos)) {
                                captureLocalEvidencePhoto(
                                    context = context,
                                    imageCapture = imageCapture,
                                    directoryName = directoryName,
                                    filePrefix = filePrefix,
                                    afState = { warmup.currentAfState(FocusGatePolicy.MAX_STATE_AGE_MS) },
                                    // Al tocar solo se bloquea el boton (gira el circulo). El destello llega con el
                                    // obturador real y dura un instante: antes el velo blanco cubria el visor desde el
                                    // toque hasta guardar el JPEG y parecia que la app se quedaba pensando.
                                    onCaptureStarted = { isCapturing = true },
                                    onShutter = { showCaptureFlash = true },
                                    onSaved = { file ->
                                        showCaptureFlash = false
                                        isCapturing = false
                                        onPhotoCaptured(file.absolutePath)
                                    },
                                    onError = {
                                        showCaptureFlash = false
                                        isCapturing = false
                                    }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (useSideGallery) {
                LocalEvidenceSidePanel(
                    photos = photos,
                    maxPhotos = maxPhotos,
                    onOpen = { selectedPhoto = it },
                    onDelete = { photoToDelete = it },
                    modifier = Modifier
                        .width(320.dp)
                        .fillMaxHeight()
                )
            }
        }
    }

    selectedPhoto?.let { photo ->
        LocalEvidenceLightbox(
            photo = photo,
            onClose = { selectedPhoto = null },
            onDelete = {
                if (photo.canDelete) {
                    photoToDelete = photo
                }
            }
        )
    }

    photoToDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Eliminar foto", fontWeight = FontWeight.Bold) },
            text = { Text("Deseas eliminar esta evidencia?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePhoto(photo)
                        if (selectedPhoto?.id == photo.id) {
                            selectedPhoto = null
                        }
                        photoToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB42318),
                        contentColor = Color.White
                    )
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

}

@Composable
private fun LocalCaptureDisabledState(onClose: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(PmmNavyDark),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.NoPhotography, contentDescription = null, tint = PmmBlueDeep, modifier = Modifier.size(48.dp))
                Text("Captura no disponible", fontWeight = FontWeight.Bold)
                Text(
                    "Tu usuario no tiene permiso para agregar fotografías en este módulo.",
                    color = PmmTextSecondary,
                    textAlign = TextAlign.Center
                )
                TextButton(onClick = onClose) { Text("Volver") }
            }
        }
    }
}

@Composable
private fun LocalCameraPermissionState(
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
            shape = RoundedCornerShape(8.dp),
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
                    text = "Activa la camara",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "La evidencia se guarda localmente y se adjunta al registro.",
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

@Composable
private fun LocalCameraTopBar(
    title: String,
    subtitle: String,
    isFlashOn: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onToggleFlash: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.46f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar cámara", tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onToggleFlash) {
            Icon(
                imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flash",
                tint = if (isFlashOn) PmmBlueLight else Color.White
            )
        }
        TextButton(onClick = onDone) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("Listo", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LocalCaptureFrame(modifier: Modifier = Modifier) {
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
private fun LocalCaptureControls(
    isCapturing: Boolean,
    canCapture: Boolean,
    totalPhotos: Int,
    maxPhotos: Int?,
    zoomRatio: Float,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier
) {
    val limitReached = maxPhotos != null && totalPhotos >= maxPhotos
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black.copy(alpha = 0.60f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (maxPhotos != null) "$totalPhotos / $maxPhotos fotos" else "$totalPhotos fotos",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = if (limitReached) "Limite alcanzado" else "Pellizca la vista para zoom ${"%.1f".format(zoomRatio)}x",
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 10.sp
                )
            }
            Surface(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canCapture && !isCapturing && !limitReached, onClick = onCapture),
                shape = CircleShape,
                color = if (!canCapture || limitReached) Color.White.copy(alpha = 0.42f) else Color.White,
                border = BorderStroke(4.dp, PmmBlueLight)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = PmmBlueDeep,
                            strokeWidth = 3.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = "Tomar foto",
                            tint = PmmBlueDeep,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
        }
        Text(
            text = "Puedes tomar fotos continuas sin regresar al formulario.",
            color = Color.White.copy(alpha = 0.64f),
            fontSize = 10.sp
        )
    }
}

@Composable
private fun LocalEvidenceFilmstrip(
    photos: List<LocalEvidencePhoto>,
    onOpen: (LocalEvidencePhoto) -> Unit,
    onDelete: (LocalEvidencePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.54f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        if (photos.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White.copy(alpha = 0.72f))
                Text("Las fotos apareceran aqui.", color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    LocalPhotoThumbnail(
                        photo = photo,
                        onOpen = { onOpen(photo) },
                        onDelete = { onDelete(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun LocalEvidenceSidePanel(
    photos: List<LocalEvidencePhoto>,
    maxPhotos: Int?,
    onOpen: (LocalEvidencePhoto) -> Unit,
    onDelete: (LocalEvidencePhoto) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = Color(0xFF0B1730)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Evidencias", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                text = if (maxPhotos != null) "${photos.size} / $maxPhotos capturadas" else "${photos.size} capturadas",
                color = Color.White.copy(alpha = 0.66f),
                fontSize = 12.sp
            )
            if (photos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color.White.copy(alpha = 0.58f), modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Sin evidencias", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(
                        "Cada captura aparecera aqui para revisarla.",
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count = photos.size, key = { photos[it].id }) { index ->
                        val photo = photos[index]
                        LocalPhotoThumbnail(
                            photo = photo,
                            onOpen = { onOpen(photo) },
                            onDelete = { onDelete(photo) },
                            modifier = Modifier.aspectRatio(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPhotoThumbnail(
    photo: LocalEvidencePhoto,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(PmmNavyDark)
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = photo.model,
            contentDescription = photo.label,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp),
            color = if (photo.status.contains("Nueva", ignoreCase = true)) PmmBlueLight else PmmGreen,
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                photo.label,
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (photo.canDelete) {
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.66f), CircleShape)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar foto", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LocalEvidenceLightbox(
    photo: LocalEvidencePhoto,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF07101F))
        ) {
            ZoomableLocalEvidenceImage(
                model = photo.model,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 76.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.68f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Evidencia fotografica", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(photo.status, color = PmmYellow, fontSize = 11.sp)
                }
                if (photo.canDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar evidencia", tint = Color(0xFFFCA5A5))
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableLocalEvidenceImage(model: Any, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) {
                        scale = 1f
                        offset = Offset.Zero
                    } else {
                        scale = 2.5f
                    }
                })
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale > 1f) offset + pan else Offset.Zero
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Fotografia",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

private fun captureLocalEvidencePhoto(
    context: Context,
    imageCapture: ImageCapture?,
    directoryName: String,
    filePrefix: String,
    afState: () -> Int?,
    onCaptureStarted: () -> Unit,
    onShutter: () -> Unit,
    onSaved: (File) -> Unit,
    onError: () -> Unit
) {
    val capture = imageCapture ?: run {
        onError()
        return
    }
    val file = createLocalEvidenceFile(context, directoryName, filePrefix)
    onCaptureStarted()

    // Foto con reintento invisible: si sale movida o desenfocada, toma otra en el acto y entrega la mas nitida de
    // la misma escena, con el nombre de archivo de siempre. El operador solo toca el obturador una vez.
    SmartStillCapture.takeBest(
        context = context,
        imageCapture = capture,
        target = file,
        afState = afState,
        onShutter = onShutter,
        onSaved = onSaved,
        onError = {
            Log.e(TAG, "Error al tomar foto local")
            onError()
        }
    )
}

private fun createLocalEvidenceFile(
    context: Context,
    directoryName: String,
    filePrefix: String
): File {
    val directory = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        directoryName
    ).apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
        .format(System.currentTimeMillis())
    return File(directory, "${filePrefix}_$timestamp.jpg")
}

private const val TAG = "LocalEvidenceCamera"
/**
 * Calidad del JPEG que sale de la camara. Es el PRIMERO de dos pasos de
 * compresion -- el segundo es [PhotoPipeline] al subir -- y los artefactos del
 * primero se hornean en el segundo, asi que este va alto a proposito.
 */
private const val LOCAL_EVIDENCE_JPEG_QUALITY = 95

/** Cadencia con la que se vencen los plazos del arranque de camara. */
private const val WARMUP_TICK_MS = 200L

/** Cuanto dura el destello blanco al disparar. */
private const val CAPTURE_FLASH_MS = 140L
