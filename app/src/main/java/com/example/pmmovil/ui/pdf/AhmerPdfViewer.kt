package com.example.pmmovil.ui.pdf

import android.graphics.Canvas as PdfCanvas
import android.graphics.Paint
import android.graphics.Path as PdfPath
import android.graphics.Typeface
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ahmer.pdfviewer.PDFView
import com.ahmer.pdfviewer.listener.OnDrawListener
import com.ahmer.pdfviewer.listener.OnErrorListener
import com.ahmer.pdfviewer.listener.OnLoadCompleteListener
import com.ahmer.pdfviewer.listener.OnPageChangeListener
import com.ahmer.pdfviewer.listener.OnPageScrollListener
import com.ahmer.pdfviewer.util.FitPolicy
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Guarda el trazo que el usuario está dibujando en este momento.
 *
 * No es estado de Compose a propósito: el trazo se pinta dentro del propio
 * `PDFView`, así que basta con invalidar la vista para que se repinte en el
 * mismo fotograma que la página. Así el trazo temporal y el definitivo salen
 * siempre del mismo dibujante y no pueden quedar desalineados.
 */
private class PdfDraftHolder {
    var annotation: PdfAnnotation? = null
}

@Composable
fun AhmerPdfViewer(
    file: File,
    annotations: List<PdfAnnotation>,
    selectedTool: PdfAnnotationTool,
    fitMode: PdfFitMode,
    initialPage: Int,
    onPageChanged: (Int) -> Unit,
    onLoaded: (Int) -> Unit,
    onError: (String) -> Unit,
    onAnnotationCreated: (PdfAnnotation) -> Unit,
    onTextRequested: (Int, PdfNormalizedPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestAnnotations by rememberUpdatedState(annotations)
    val latestPage by rememberUpdatedState(initialPage)
    val latestOnPageChanged by rememberUpdatedState(onPageChanged)
    val latestOnLoaded by rememberUpdatedState(onLoaded)
    val latestOnError by rememberUpdatedState(onError)
    val latestOnAnnotationCreated by rememberUpdatedState(onAnnotationCreated)
    val latestOnTextRequested by rememberUpdatedState(onTextRequested)
    var pdfView by remember { mutableStateOf<PDFView?>(null) }
    val draft = remember(file) { PdfDraftHolder() }

    LaunchedEffect(pdfView, file, fitMode) {
        val view = pdfView ?: return@LaunchedEffect
        draft.annotation = null
        view.fromFile(file)
            .enableSwipe(selectedTool == PdfAnnotationTool.PAN)
            .swipeHorizontal(false)
            .enableDoubleTap(true)
            .enableAnnotationRendering(true)
            .enableAntialiasing(true)
            .pageFitPolicy(
                if (fitMode == PdfFitMode.WIDTH) FitPolicy.WIDTH else FitPolicy.BOTH
            )
            .fitEachPage(true)
            .autoSpacing(true)
            .spacing(12)
            .pageSnap(true)
            .pageFling(true)
            .defaultPage(latestPage.coerceAtLeast(0))
            .onDrawAll(object : OnDrawListener {
                override fun onLayerDrawn(
                    canvas: PdfCanvas?,
                    pageWidth: Float,
                    pageHeight: Float,
                    currentPage: Int
                ) {
                    val target = canvas ?: return
                    val persisted = latestAnnotations.filter { it.pageIndex == currentPage }
                    // El borrador se sigue pintando hasta que la anotación aparece
                    // en la lista persistente; se descarta si ya está ahí para no
                    // dibujarla dos veces en el mismo fotograma.
                    val pageAnnotations = persisted + listOfNotNull(
                        draft.annotation?.takeIf {
                            it.pageIndex == currentPage && it !in persisted
                        }
                    )
                    if (pageAnnotations.isEmpty()) return
                    val viewport = view.pageViewport(currentPage) ?: return

                    // AhmerPdfViewer 2.0.1 invoca este listener DESPUÉS de deshacer
                    // el desplazamiento del scroll (`canvas.translate(-currentXOffset,
                    // -currentYOffset)` en PDFView.onDraw) y solo deja aplicado el
                    // offset de la página sobre el eje de avance. Devolvemos el lienzo
                    // a coordenadas de pantalla para que la anotación quede exactamente
                    // en el mismo pixel donde el usuario la dibujó.
                    val applied = view.listenerCanvasOrigin(currentPage)
                    val saved = target.save()
                    target.translate(-applied.x, -applied.y)
                    target.clipRect(
                        viewport.left,
                        viewport.top,
                        viewport.right,
                        viewport.bottom
                    )
                    drawPdfAnnotations(target, viewport, pageAnnotations)
                    target.restoreToCount(saved)
                }
            })
            .onLoad(object : OnLoadCompleteListener {
                override fun loadComplete(numberOfPages: Int) {
                    view.syncPagingWithFit()
                    latestOnLoaded(numberOfPages)
                }
            })
            .onPageChange(object : OnPageChangeListener {
                override fun onPageChanged(page: Int, pageCount: Int) {
                    latestOnPageChanged(page)
                }
            })
            .onPageScroll(object : OnPageScrollListener {
                override fun onPageScrolled(page: Int, positionOffset: Float) {
                    view.syncPagingWithFit()
                }
            })
            .onError(object : OnErrorListener {
                override fun onError(t: Throwable?) {
                    latestOnError(t?.message ?: "No se pudo renderizar el PDF.")
                }
            })
            .load()
    }

    LaunchedEffect(selectedTool, pdfView) {
        pdfView?.let { view ->
            view.setSwipeEnabled(selectedTool == PdfAnnotationTool.PAN)
            if (selectedTool != PdfAnnotationTool.PAN) {
                view.stopFling()
            }
            draft.annotation = null
            view.invalidate()
        }
    }

    LaunchedEffect(annotations, pdfView) {
        // La anotación recién terminada ya viaja en la lista persistente,
        // así que el borrador puede liberarse sin que parpadee el trazo.
        draft.annotation = null
        pdfView?.invalidate()
    }

    Box(modifier = modifier.clipToBounds()) {
        AndroidView(
            factory = {
                PDFView(context, null).also { view ->
                    view.setBackgroundColor(android.graphics.Color.rgb(203, 213, 225))
                    view.clipChildren = true
                    view.clipToPadding = true
                    view.setPadding(0, 0, 0, 0)
                    view.setRenderDuringScale(true)
                    view.setMinZoom(1f)
                    view.setMidZoom(1.75f)
                    view.setMaxZoom(3f)
                    pdfView = view
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    when (selectedTool) {
                        PdfAnnotationTool.INK,
                        PdfAnnotationTool.HIGHLIGHT -> Modifier.pointerInput(
                            file,
                            selectedTool,
                            pdfView
                        ) {
                            val view = pdfView ?: return@pointerInput
                            detectAnnotationStrokes(
                                view = view,
                                tool = selectedTool,
                                onUpdate = { annotation ->
                                    draft.annotation = annotation
                                    view.invalidate()
                                },
                                onCommit = { annotation ->
                                    // El borrador se mantiene hasta que la anotación
                                    // llegue por la lista persistente (ver LaunchedEffect).
                                    draft.annotation = annotation
                                    view.invalidate()
                                    latestOnAnnotationCreated(annotation)
                                },
                                onCancel = {
                                    draft.annotation = null
                                    view.invalidate()
                                }
                            )
                        }

                        PdfAnnotationTool.TEXT -> Modifier.pointerInput(file, pdfView) {
                            val view = pdfView ?: return@pointerInput
                            detectTapGestures { offset ->
                                val viewport = view.pageViewportAt(offset)
                                    ?: return@detectTapGestures
                                latestOnTextRequested(
                                    viewport.pageIndex,
                                    viewport.normalize(offset)
                                )
                            }
                        }

                        PdfAnnotationTool.PAN -> Modifier
                    }
                )
        )
    }

    DisposableEffect(pdfView) {
        val view = pdfView
        onDispose { view?.recycle() }
    }
}

/**
 * Rectángulo que ocupa una página dentro del visor, en coordenadas de pantalla.
 *
 * Es la única fuente de verdad de la geometría: la usan la captura táctil, el
 * trazo temporal y el trazo persistente.
 */
private data class PageViewport(
    val pageIndex: Int,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height

    fun contains(point: Offset): Boolean =
        point.x >= left && point.x <= right && point.y >= top && point.y <= bottom

    fun normalize(point: Offset): PdfNormalizedPoint = PdfNormalizedPoint(
        x = ((point.x - left) / width).coerceIn(0f, 1f),
        y = ((point.y - top) / height).coerceIn(0f, 1f)
    )

    fun screenX(normalizedX: Float): Float = left + normalizedX * width

    fun screenY(normalizedY: Float): Float = top + normalizedY * height
}

/**
 * Posición real de la página en pantalla usando los offsets internos del visor,
 * los mismos que `PDFView.drawPart` aplica al pintar los mosaicos de la página.
 */
private fun PDFView.pageViewport(pageIndex: Int): PageViewport? {
    val file = pdfFile ?: return null
    if (pageIndex < 0 || pageIndex >= file.pagesCount) return null
    val currentZoom = zoom
    val size = runCatching { file.getScaledPageSize(pageIndex, currentZoom) }.getOrNull()
        ?: return null
    if (size.width <= 0f || size.height <= 0f) return null
    val primary = runCatching { file.getPageOffset(pageIndex, currentZoom) }.getOrNull()
        ?: return null
    val secondary = runCatching {
        file.getSecondaryPageOffset(pageIndex, currentZoom)
    }.getOrNull() ?: return null

    return if (isSwipeVertical) {
        PageViewport(
            pageIndex = pageIndex,
            left = currentXOffset + secondary,
            top = currentYOffset + primary,
            width = size.width,
            height = size.height
        )
    } else {
        PageViewport(
            pageIndex = pageIndex,
            left = currentXOffset + primary,
            top = currentYOffset + secondary,
            width = size.width,
            height = size.height
        )
    }
}

/**
 * Página que está debajo del punto tocado. No se usa la página "actual" porque
 * en pantalla pueden verse dos páginas a la vez y el trazo debe pertenecer a la
 * que realmente se tocó.
 */
private fun PDFView.pageViewportAt(position: Offset): PageViewport? {
    val file = pdfFile ?: return null
    val currentZoom = zoom
    val documentOffset = if (isSwipeVertical) {
        position.y - currentYOffset
    } else {
        position.x - currentXOffset
    }
    val hint = runCatching {
        file.getPageAtOffset(documentOffset, currentZoom)
    }.getOrNull() ?: currentPage

    return sequenceOf(hint, hint - 1, hint + 1, currentPage)
        .distinct()
        .mapNotNull { pageViewport(it) }
        .firstOrNull { it.contains(position) }
}

/**
 * Activa el avance página por página solo cuando la página cabe completa.
 *
 * `pageSnap` y `pageFling` son cómodos para hojear, pero en cuanto la hoja es
 * más grande que la pantalla convierten cada arrastre en un salto: al soltar el
 * dedo, `PDFView.performPageSnap` vuelve a pegar el borde de la página contra
 * el borde de la pantalla, y `DragPinchManager.onBoundedFling` encierra el
 * impulso dentro de la página actual. Pasaba al ampliar, y pasa igual con el
 * ajuste al ancho, donde la hoja no cabe a lo alto ni siquiera sin zoom.
 */
private fun PDFView.syncPagingWithFit() {
    val file = pdfFile
    val pageLength = file?.let {
        runCatching { it.getPageLength(currentPage, zoom) }.getOrNull()
    }
    val available = (if (isSwipeVertical) height else width).toFloat()
    val pageByPage = pageLength == null ||
        available <= 0f ||
        pageLength <= available + FIT_TOLERANCE
    if (isPageSnap != pageByPage) setPageSnap(pageByPage)
    if (isPageFlingEnabled != pageByPage) setPageFling(pageByPage)
}

/** Margen en píxeles para no encender y apagar el avance por página en el límite. */
private const val FIT_TOLERANCE = 1f

/**
 * Traslación que la librería ya dejó aplicada sobre el lienzo antes de llamar a
 * `onLayerDrawn`. Se resta para volver a coordenadas de pantalla.
 */
private fun PDFView.listenerCanvasOrigin(pageIndex: Int): Offset {
    val file = pdfFile ?: return Offset.Zero
    val offset = runCatching { file.getPageOffset(pageIndex, zoom) }.getOrElse { 0f }
    return if (isSwipeVertical) Offset(0f, offset) else Offset(offset, 0f)
}

private suspend fun PointerInputScope.detectAnnotationStrokes(
    view: PDFView,
    tool: PdfAnnotationTool,
    onUpdate: (PdfAnnotation) -> Unit,
    onCommit: (PdfAnnotation) -> Unit,
    onCancel: () -> Unit
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // El trazo arranca en el punto exacto donde bajó el dedo. Usar
        // detectDragGestures perdía los primeros milímetros del trazo porque
        // solo avisa cuando ya se superó el umbral de arrastre.
        val viewport = view.pageViewportAt(down.position) ?: return@awaitEachGesture
        val points = mutableListOf(viewport.normalize(down.position))
        var moved = false

        while (true) {
            val event = awaitPointerEvent()
            if (event.changes.size > 1) {
                // Dos dedos: el usuario quiere hacer zoom, no anotar.
                onCancel()
                return@awaitEachGesture
            }
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null) {
                onCancel()
                return@awaitEachGesture
            }
            if (!change.pressed) break
            if (change.positionChange() != Offset.Zero) {
                moved = true
                change.historical.forEach { historical ->
                    points.addSampled(viewport.normalize(historical.position), viewport)
                }
                points.addSampled(viewport.normalize(change.position), viewport)
                change.consume()
                onUpdate(buildAnnotation(tool, viewport.pageIndex, points))
            }
        }

        if (!moved || points.size < 2) {
            onCancel()
            return@awaitEachGesture
        }
        onCommit(buildAnnotation(tool, viewport.pageIndex, points))
    }
}

/** Descarta puntos casi idénticos para que un trazo largo no crezca sin límite. */
private fun MutableList<PdfNormalizedPoint>.addSampled(
    point: PdfNormalizedPoint,
    viewport: PageViewport
) {
    val last = lastOrNull()
    if (last != null) {
        val dx = (point.x - last.x) * viewport.width
        val dy = (point.y - last.y) * viewport.height
        if (dx * dx + dy * dy < 4f) return
    }
    add(point)
}

private fun buildAnnotation(
    tool: PdfAnnotationTool,
    pageIndex: Int,
    points: List<PdfNormalizedPoint>
): PdfAnnotation {
    val isHighlight = tool == PdfAnnotationTool.HIGHLIGHT
    return PdfAnnotation(
        pageIndex = pageIndex,
        tool = tool,
        points = if (isHighlight && points.size > 1) {
            listOf(points.first(), points.last())
        } else {
            points.toList()
        },
        color = if (isHighlight) {
            android.graphics.Color.YELLOW
        } else {
            android.graphics.Color.RED
        },
        widthFraction = if (isHighlight) 0.08f else 0.006f
    )
}

/**
 * Dibujante único de anotaciones. Recibe siempre el rectángulo real de la
 * página, así que produce el mismo resultado mientras se traza y una vez
 * guardado el trazo.
 */
private fun drawPdfAnnotations(
    canvas: PdfCanvas,
    viewport: PageViewport,
    annotations: List<PdfAnnotation>
) {
    if (annotations.isEmpty()) return
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val path = PdfPath()

    annotations.forEach { annotation ->
        val points = annotation.points
        if (points.isEmpty()) return@forEach

        val strokeWidth = (annotation.widthFraction * viewport.width).coerceAtLeast(2f)
        paint.reset()
        paint.isAntiAlias = true
        paint.color = annotation.color
        paint.alpha = 255
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = strokeWidth

        when (annotation.tool) {
            PdfAnnotationTool.INK -> {
                if (points.size == 1) {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(
                        viewport.screenX(points.first().x),
                        viewport.screenY(points.first().y),
                        strokeWidth / 2f,
                        paint
                    )
                } else {
                    paint.style = Paint.Style.STROKE
                    path.reset()
                    val first = points.first()
                    path.moveTo(viewport.screenX(first.x), viewport.screenY(first.y))
                    points.drop(1).forEach { point ->
                        path.lineTo(viewport.screenX(point.x), viewport.screenY(point.y))
                    }
                    canvas.drawPath(path, paint)
                }
            }

            PdfAnnotationTool.HIGHLIGHT -> {
                val first = points.first()
                val last = points.getOrElse(1) { first }
                val left = viewport.screenX(min(first.x, last.x))
                val top = viewport.screenY(min(first.y, last.y))
                val right = viewport.screenX(max(first.x, last.x))
                val bottom = viewport.screenY(max(first.y, last.y))
                paint.style = Paint.Style.FILL
                paint.alpha = 95
                canvas.drawRect(
                    left,
                    top,
                    max(right, left + 4f),
                    max(bottom, top + 8f),
                    paint
                )
            }

            PdfAnnotationTool.TEXT -> {
                val text = annotation.text ?: return@forEach
                val point = points.first()
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = (viewport.width * PDF_TEXT_SIZE_FRACTION)
                    .coerceAtLeast(10f)
                canvas.drawText(
                    text,
                    viewport.screenX(point.x),
                    viewport.screenY(point.y),
                    paint
                )
            }

            PdfAnnotationTool.PAN -> Unit
        }
    }
}

/** Tamaño del texto de una nota, como fracción del ancho de la página. */
internal const val PDF_TEXT_SIZE_FRACTION = 0.025f
