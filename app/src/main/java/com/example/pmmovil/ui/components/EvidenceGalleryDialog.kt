package com.example.pmmovil.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import kotlinx.coroutines.launch

/**
 * Una foto del visor. [id] debe ser estable (la ruta del archivo, el índice en el servidor…): con él el visor
 * reconoce la foto aunque la lista cambie al borrar. [model] es lo que entiende Coil (un `File`, una URL…) y es lo que
 * se ve en grande. Para fotos del servidor: [thumbModel] es la miniatura de la tira de abajo y [zoomModel] el original,
 * que solo se trae al acercarse (ver [EvidenceZoomLayers]).
 */
data class EvidenceGalleryItem(
    val id: String,
    val model: Any,
    val label: String,
    val thumbModel: Any = model,
    val zoomModel: Any? = null
)

/** Posición donde debe abrir el visor al tocar la foto [id]; si no está, la primera. */
fun evidenceGalleryStartIndex(items: List<EvidenceGalleryItem>, id: String): Int =
    items.indexOfFirst { it.id == id }.coerceAtLeast(0)

/**
 * Visor grande de fotos de evidencia: se desliza entre fotos, se acerca con pellizco o doble toque y abajo trae la
 * tira de miniaturas. Es el mismo que usa Entrada Caseta al tocar una foto, pero sin lo de leer placa/caja.
 *
 * [items] se vuelve a leer en cada recomposición: si el que llama quita una foto de su lista, el visor sigue abierto
 * en la siguiente y solo se cierra solo cuando ya no queda ninguna. Con [onDelete] nulo no se ofrece borrar (por
 * ejemplo las fotos de la llegada, que viven en el servidor); con [canDelete] falso se muestra deshabilitado.
 */
@Composable
fun EvidenceGalleryDialog(
    items: List<EvidenceGalleryItem>,
    startIndex: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((EvidenceGalleryItem) -> Unit)? = null,
    canDelete: Boolean = true,
    deleteMessage: String = "Esta evidencia se quitará del registro."
) {
    var pendingDelete by remember { mutableStateOf<EvidenceGalleryItem?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(
            initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        ) { items.size }
        val scope = rememberCoroutineScope()

        LaunchedEffect(items.isEmpty()) {
            if (items.isEmpty()) onDismiss()
        }
        if (items.isEmpty()) return@Dialog

        val currentPhoto = items[pagerState.currentPage.coerceIn(0, items.size - 1)]

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .widthIn(max = 900.dp)
                .fillMaxHeight(0.92f),
            color = PmmBgSurface,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PmmNavyBorder)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        key = { items[it].id }
                    ) { page ->
                        ZoomableEvidenceImage(
                            model = items[page].model,
                            contentDescription = items[page].label,
                            modifier = Modifier.fillMaxSize(),
                            zoomModel = items[page].zoomModel
                        )
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (items.size > 1) "${pagerState.currentPage + 1} / ${items.size} · ${currentPhoto.label}" else currentPhoto.label,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Pellizca o toca dos veces para acercar",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        if (items.size > 1) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(items, key = { _, it -> "thumb-${it.id}" }) { i, item ->
                                    val selected = i == pagerState.currentPage
                                    AsyncImage(
                                        model = item.thumbModel,
                                        contentDescription = item.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                2.dp,
                                                if (selected) Color.White else Color.White.copy(alpha = 0.3f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { scope.launch { pagerState.animateScrollToPage(i) } }
                                    )
                                }
                            }
                        } else {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
                if (onDelete != null) {
                    OutlinedButton(
                        onClick = { pendingDelete = currentPhoto },
                        enabled = canDelete,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .height(52.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PmmRed)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Eliminar")
                    }
                }
            }
        }
    }

    pendingDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = PmmRed) },
            title = { Text("Eliminar foto") },
            text = { Text(deleteMessage) },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDelete?.invoke(photo)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PmmRed)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

/** Foto con zoom por pellizco y doble toque. */
@Composable
private fun ZoomableEvidenceImage(
    model: Any,
    contentDescription: String,
    modifier: Modifier = Modifier,
    zoomModel: Any? = null
) {
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
                // Detección manual (no detectTransformGestures): esa función consume CUALQUIER arrastre desde el
                // primer dedo, incluso a 1x, y le gana el gesto al HorizontalPager antes de que pueda pasar de
                // foto. Aquí solo se consume el arrastre cuando hay pellizco (2+ dedos) o cuando ya hay zoom; un
                // arrastre de un dedo a 1x se deja sin consumir para que el pager lo reciba.
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()
                        if (event.changes.size > 1 || scale > 1.02f) {
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + panChange else Offset.Zero
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center
    ) {
        EvidenceZoomLayers(
            model = model,
            zoomModel = zoomModel,
            contentDescription = contentDescription,
            scale = { scale },
            offset = { offset }
        )
    }
}
