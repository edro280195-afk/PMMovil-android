package com.example.pmmovil.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import kotlinx.coroutines.flow.first

/** A partir de este zoom se trae el original: antes, la vista de pantalla se ve igual de nítida. */
const val EVIDENCE_ZOOM_ORIGINAL = 1.5f

/**
 * La foto de un visor en dos capas que se mueven juntas: [model] (ligera: la vista de pantalla) siempre, y encima
 * [zoomModel] (el original a resolución completa) solo cuando la persona se acerca de verdad. Abrir el visor no baja el
 * original, pero al acercarse a un sello o a un número se ve todo el detalle y no la vista estirada. Mientras el original
 * llega se sigue viendo la vista; ambas se ajustan igual, así que al aparecer encajan exactas.
 *
 * [scale] y [offset] son lambdas para que el gesto no recomponga en cada cuadro (se leen dentro de graphicsLayer).
 */
@Composable
fun EvidenceZoomLayers(
    model: Any,
    zoomModel: Any?,
    contentDescription: String,
    scale: () -> Float,
    offset: () -> Offset
) {
    var traerOriginal by remember(zoomModel) { mutableStateOf(false) }
    if (zoomModel != null) {
        LaunchedEffect(zoomModel) {
            snapshotFlow { scale() >= EVIDENCE_ZOOM_ORIGINAL }.first { it }
            traerOriginal = true
        }
    }

    val transformada = Modifier
        .fillMaxSize()
        .graphicsLayer {
            val s = scale()
            val o = offset()
            scaleX = s
            scaleY = s
            translationX = o.x
            translationY = o.y
        }

    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = transformada
    )

    if (zoomModel != null && traerOriginal) {
        val context = LocalContext.current
        // Size.ORIGINAL: sin esto Coil la decodifica al tamaño de la pantalla y el zoom volvería a verse borroso.
        val original = remember(zoomModel) {
            ImageRequest.Builder(context)
                .data(zoomModel)
                .size(Size.ORIGINAL)
                .build()
        }
        AsyncImage(
            model = original,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = transformada
        )
    }
}
