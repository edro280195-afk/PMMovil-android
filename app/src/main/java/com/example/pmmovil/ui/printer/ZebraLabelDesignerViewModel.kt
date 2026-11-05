package com.example.pmmovil.ui.printer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.data.printing.LabelAlignment
import com.example.pmmovil.data.printing.LabelBitmapRenderer
import com.example.pmmovil.data.printing.LabelFontFamily
import com.example.pmmovil.data.printing.LabelImageAsset
import com.example.pmmovil.data.printing.LabelSnapEngine
import com.example.pmmovil.data.printing.LabelZplFactory
import com.example.pmmovil.data.printing.SnapBox
import com.example.pmmovil.data.printing.SnapResult
import com.example.pmmovil.data.printing.QrEyeShape
import com.example.pmmovil.data.printing.QrLogoPlate
import com.example.pmmovil.data.printing.QrModuleShape
import com.example.pmmovil.data.printing.QrRenderReport
import com.example.pmmovil.data.printing.QrStyleOptions
import com.example.pmmovil.data.printing.QrStylePreset
import com.example.pmmovil.data.printing.ZebraLabelData
import com.example.pmmovil.data.printing.ZebraLabelElement
import com.example.pmmovil.data.printing.ZebraLabelElementId
import com.example.pmmovil.data.printing.ZebraLabelLayout
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraCustomImage
import com.example.pmmovil.data.printing.ZebraCustomText
import com.example.pmmovil.data.printing.ZebraPrinterPreferences
import com.example.pmmovil.data.printing.ZebraPrinterService
import com.example.pmmovil.data.printing.ZebraZplBuilder
import com.example.pmmovil.data.printing.ZplGraphicEncoder
import com.example.pmmovil.data.printing.deserializeZebraLabelLayout
import com.google.gson.GsonBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SampleDataMode(val title: String) {
    STANDARD("Estándar (356398-00)"),
    LONG_TEXT("Texto Largo (Prueba ajuste)"),
    FOLDER("Carpeta (Resumen)"),
    SINGLE_BOX("Caja Única (1/1)")
}

data class ZebraLabelDesignerState(
    val labelType: ZebraLabelType = ZebraLabelType.ENTRADA_BODEGA,
    val layout: ZebraLabelLayout = ZebraLabelLayout.reference(),
    val selectedElement: ZebraLabelElementId = ZebraLabelElementId.REFERENCE,
    val selectedCustomTextId: String? = null,
    val selectedCustomImageId: String? = null,
    val sampleDataMode: SampleDataMode = SampleDataMode.STANDARD,
    val previewLabel: ZebraLabelData = ZebraZplBuilder.buildTestLabel(),
    val isPrinting: Boolean = false,
    val message: String? = null,
    val lastActionSucceeded: Boolean? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val showGrid: Boolean = true,
    val zoomLevel: Float = 1.0f,
    val showJsonDialog: Boolean = false,
    /** Resultado de la última verificación de lectura del QR; null mientras no se ha renderizado. */
    val qrReport: QrRenderReport? = null,
    /** Guías de alineación a dibujar mientras se arrastra un elemento (en dots). */
    val activeGuides: SnapResult? = null
)

/** Qué se está arrastrando en el lienzo, para poder aplicarle alineación asistida. */
sealed interface DragTarget {
    data class Fixed(val id: ZebraLabelElementId) : DragTarget
    data class Text(val id: String) : DragTarget
    data class Image(val id: String) : DragTarget
}

/** Estado, historial undo/redo y herramientas de edición para el diseñador visual de etiquetas. */
@HiltViewModel
class ZebraLabelDesignerViewModel @Inject constructor(
    private val layoutPreferences: ZebraLabelLayoutPreferences,
    private val printerPreferences: ZebraPrinterPreferences,
    private val printerService: ZebraPrinterService,
    private val labelZplFactory: LabelZplFactory,
    private val labelBitmapRenderer: LabelBitmapRenderer
) : ViewModel() {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val undoStack = ArrayDeque<ZebraLabelLayout>()
    private val redoStack = ArrayDeque<ZebraLabelLayout>()

    private val _uiState = MutableStateFlow(
        ZebraLabelDesignerState(
            layout = layoutPreferences.getLayout(ZebraLabelType.ENTRADA_BODEGA)
        )
    )
    val uiState: StateFlow<ZebraLabelDesignerState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val syncResult = layoutPreferences.syncWithServer()
            if (syncResult.isSuccess && syncResult.getOrNull() == true) {
                _uiState.update {
                    it.copy(
                        layout = layoutPreferences.getLayout(it.labelType)
                    )
                }
            }
        }
    }

    fun setLabelType(type: ZebraLabelType) {
        if (_uiState.value.labelType == type) return
        undoStack.clear()
        redoStack.clear()
        _uiState.update {
            it.copy(
                labelType = type,
                layout = layoutPreferences.getLayout(type),
                selectedElement = if (type == ZebraLabelType.CASETA) {
                    ZebraLabelElementId.QR
                } else {
                    ZebraLabelElementId.REFERENCE
                },
                selectedCustomTextId = null,
                selectedCustomImageId = null,
                canUndo = false,
                canRedo = false,
                message = "Editando formato de ${type.title.lowercase()}",
                lastActionSucceeded = true
            )
        }
    }

    fun selectElement(elementId: ZebraLabelElementId) {
        _uiState.update {
            it.copy(
                selectedElement = elementId,
                selectedCustomTextId = null,
                selectedCustomImageId = null,
                message = null,
                lastActionSucceeded = null
            )
        }
    }

    fun selectCustomText(id: String) {
        if (_uiState.value.layout.customTexts.none { it.id == id }) return
        _uiState.update {
            it.copy(selectedCustomTextId = id, selectedCustomImageId = null, message = null, lastActionSucceeded = null)
        }
    }

    fun addCustomText() {
        val layout = _uiState.value.layout
        val id = nextCustomTextId(layout)
        val element = ZebraLabelElement(
            x = 20,
            y = (layout.heightDots / 2 - 40).coerceAtLeast(0),
            width = (layout.widthDots - 40).coerceAtLeast(80),
            height = 80,
            fontHeight = 54,
            fontWidth = 48,
            alignment = LabelAlignment.CENTER
        ).constrained(layout.widthDots, layout.heightDots)
        pushUndoState()
        _uiState.update {
            it.copy(
                layout = layout.copy(
                    customTexts = layout.customTexts + ZebraCustomText(id, "Texto nuevo", element)
                ),
                selectedCustomTextId = id,
                selectedCustomImageId = null,
                message = "Texto libre agregado. Edítalo desde este panel.",
                lastActionSucceeded = true
            )
        }
    }

    fun updateCustomText(id: String, text: String) {
        updateCustomTextLayer(id) { it.copy(text = text) }
    }

    /**
     * Duplica un texto libre con todo su formato, desplazado para que no quede encima del
     * original. Copiar el estilo a mano (fuente, tamaño, alineación, borde) era el camino largo
     * para algo tan común como repetir una leyenda en dos partes de la etiqueta.
     */
    fun duplicateCustomText(id: String) {
        val layout = _uiState.value.layout
        val source = layout.customTexts.firstOrNull { it.id == id } ?: return
        val newId = nextCustomTextId(layout)
        val element = source.element
            .copy(
                x = source.element.x + DUPLICATE_OFFSET_DOTS,
                y = source.element.y + DUPLICATE_OFFSET_DOTS
            )
            .constrained(layout.widthDots, layout.heightDots)
        pushUndoState()
        _uiState.update {
            it.copy(
                layout = layout.copy(customTexts = layout.customTexts + source.copy(id = newId, element = element)),
                selectedCustomTextId = newId,
                selectedCustomImageId = null,
                message = "Texto duplicado.",
                lastActionSucceeded = true
            )
        }
    }

    fun removeCustomText(id: String) {
        if (_uiState.value.layout.customTexts.none { it.id == id }) return
        pushUndoState()
        _uiState.update { state ->
            state.copy(
                layout = state.layout.copy(
                    customTexts = state.layout.customTexts.filterNot { it.id == id }
                ),
                selectedCustomTextId = state.selectedCustomTextId.takeUnless { it == id },
                message = "Texto libre eliminado.",
                lastActionSucceeded = true
            )
        }
    }

    fun moveCustomText(id: String, deltaX: Int, deltaY: Int) {
        updateCustomTextElement(id) { element ->
            element.copy(x = element.x + deltaX, y = element.y + deltaY)
        }
    }

    fun updateCustomTextPosition(id: String, x: Int? = null, y: Int? = null) {
        updateCustomTextElement(id) { element ->
            element.copy(x = x ?: element.x, y = y ?: element.y)
        }
    }

    fun updateCustomTextWidth(id: String, width: Int) {
        updateCustomTextElement(id) { it.copy(width = width) }
    }

    fun updateCustomTextHeight(id: String, height: Int) {
        updateCustomTextElement(id) { it.copy(height = height) }
    }

    fun updateCustomTextFontHeight(id: String, fontHeight: Int) {
        updateCustomTextElement(id) { it.copy(fontHeight = fontHeight) }
    }

    fun updateCustomTextFontWidth(id: String, fontWidth: Int) {
        updateCustomTextElement(id) { it.copy(fontWidth = fontWidth) }
    }

    fun updateCustomTextFontFamily(id: String, fontFamily: LabelFontFamily) {
        updateCustomTextElement(id) { it.copy(fontFamily = fontFamily) }
    }

    fun updateCustomTextAlignment(id: String, alignment: LabelAlignment) {
        updateCustomTextElement(id) { it.copy(alignment = alignment) }
    }

    fun updateCustomTextBold(id: String, bold: Boolean) {
        updateCustomTextElement(id) { it.copy(bold = bold) }
    }

    fun updateCustomTextItalic(id: String, italic: Boolean) {
        updateCustomTextElement(id) { it.copy(italic = italic) }
    }

    fun updateCustomTextUnderline(id: String, underline: Boolean) {
        updateCustomTextElement(id) { it.copy(underline = underline) }
    }

    fun updateCustomTextLetterSpacing(id: String, letterSpacing: Float) {
        updateCustomTextElement(id) { it.copy(letterSpacing = letterSpacing) }
    }

    fun updateCustomTextVisibility(id: String, visible: Boolean) {
        updateCustomTextElement(id) { it.copy(visible = visible) }
    }

    fun updateCustomTextInverted(id: String, inverted: Boolean) {
        updateCustomTextElement(id) { it.copy(inverted = inverted) }
    }

    fun updateCustomTextBorderWidth(id: String, borderWidth: Int) {
        updateCustomTextElement(id) { it.copy(borderWidth = borderWidth.coerceIn(0, 20)) }
    }

    // --- Imágenes libres (p. ej. logo de IMEX) ---

    fun selectCustomImage(id: String) {
        if (_uiState.value.layout.customImages.none { it.id == id }) return
        _uiState.update {
            it.copy(selectedCustomImageId = id, selectedCustomTextId = null, message = null, lastActionSucceeded = null)
        }
    }

    /** Agrega una imagen del catálogo [LabelImageAsset] centrada, conservando su proporción original. */
    fun addCustomImage(asset: LabelImageAsset = LabelImageAsset.IMEX_LOGO) {
        val layout = _uiState.value.layout
        val id = nextCustomImageId(layout)
        val width = (layout.widthDots * 0.32f).roundToInt().coerceAtLeast(80)
        val height = (width / asset.aspectRatio).roundToInt().coerceAtLeast(32)
        val element = ZebraLabelElement(
            x = ((layout.widthDots - width) / 2).coerceAtLeast(0),
            y = ((layout.heightDots - height) / 2).coerceAtLeast(0),
            width = width,
            height = height
        ).constrained(layout.widthDots, layout.heightDots)
        pushUndoState()
        _uiState.update {
            it.copy(
                layout = layout.copy(customImages = layout.customImages + ZebraCustomImage(id, asset.id, element)),
                selectedCustomImageId = id,
                selectedCustomTextId = null,
                message = "Imagen agregada. Ajusta su posición y tamaño desde este panel.",
                lastActionSucceeded = true
            )
        }
    }

    /** Duplica una imagen libre conservando tamaño y proporción, desplazada del original. */
    fun duplicateCustomImage(id: String) {
        val layout = _uiState.value.layout
        val source = layout.customImages.firstOrNull { it.id == id } ?: return
        val newId = nextCustomImageId(layout)
        val element = source.element
            .copy(
                x = source.element.x + DUPLICATE_OFFSET_DOTS,
                y = source.element.y + DUPLICATE_OFFSET_DOTS
            )
            .constrained(layout.widthDots, layout.heightDots)
        pushUndoState()
        _uiState.update {
            it.copy(
                layout = layout.copy(customImages = layout.customImages + source.copy(id = newId, element = element)),
                selectedCustomImageId = newId,
                selectedCustomTextId = null,
                message = "Imagen duplicada.",
                lastActionSucceeded = true
            )
        }
    }

    fun removeCustomImage(id: String) {
        if (_uiState.value.layout.customImages.none { it.id == id }) return
        pushUndoState()
        _uiState.update { state ->
            state.copy(
                layout = state.layout.copy(
                    customImages = state.layout.customImages.filterNot { it.id == id }
                ),
                selectedCustomImageId = state.selectedCustomImageId.takeUnless { it == id },
                message = "Imagen eliminada.",
                lastActionSucceeded = true
            )
        }
    }

    fun moveCustomImage(id: String, deltaX: Int, deltaY: Int) {
        updateCustomImageElement(id) { _, element ->
            element.copy(x = element.x + deltaX, y = element.y + deltaY)
        }
    }

    fun updateCustomImagePosition(id: String, x: Int? = null, y: Int? = null) {
        updateCustomImageElement(id) { _, element ->
            element.copy(x = x ?: element.x, y = y ?: element.y)
        }
    }

    /** Al cambiar el ancho, el alto se recalcula con la proporción original de la imagen. */
    fun updateCustomImageWidth(id: String, width: Int) {
        updateCustomImageElement(id) { customImage, element ->
            val ratio = LabelImageAsset.fromId(customImage.assetId)?.aspectRatio
            val height = if (ratio != null && ratio > 0f) {
                (width / ratio).roundToInt().coerceAtLeast(32)
            } else {
                element.height
            }
            element.copy(width = width, height = height)
        }
    }

    /** Al cambiar el alto, el ancho se recalcula con la proporción original de la imagen. */
    fun updateCustomImageHeight(id: String, height: Int) {
        updateCustomImageElement(id) { customImage, element ->
            val ratio = LabelImageAsset.fromId(customImage.assetId)?.aspectRatio
            val width = if (ratio != null && ratio > 0f) {
                (height * ratio).roundToInt().coerceAtLeast(80)
            } else {
                element.width
            }
            element.copy(width = width, height = height)
        }
    }

    fun updateCustomImageVisibility(id: String, visible: Boolean) {
        updateCustomImageElement(id) { _, element -> element.copy(visible = visible) }
    }

    // --- Estilo del código QR ---

    /** Aplica un cambio al estilo del QR conservando el resto de los ajustes. */
    private fun updateQrStyle(transform: (QrStyleOptions) -> QrStyleOptions) {
        pushUndoState()
        _uiState.update { state ->
            state.copy(
                layout = state.layout.copy(qrStyle = transform(state.layout.qrStyle).sanitized()),
                message = null,
                lastActionSucceeded = null
            )
        }
    }

    fun applyQrPreset(preset: QrStylePreset) {
        updateQrStyle { preset.apply(it) }
        _uiState.update {
            it.copy(message = "Estilo de QR: ${preset.title}", lastActionSucceeded = true)
        }
    }

    fun setQrModuleShape(shape: QrModuleShape) = updateQrStyle { it.copy(moduleShape = shape) }

    fun setQrEyeFrameShape(shape: QrEyeShape) = updateQrStyle { it.copy(eyeFrameShape = shape) }

    fun setQrEyeBallShape(shape: QrEyeShape) = updateQrStyle { it.copy(eyeBallShape = shape) }

    fun setQrLogoPlate(plate: QrLogoPlate) = updateQrStyle { it.copy(logoPlate = plate) }

    fun setQrLogoScale(scale: Float) = updateQrStyle { it.copy(logoScale = scale) }

    fun setQrQuietZone(modules: Int) = updateQrStyle { it.copy(quietZoneModules = modules) }

    fun setQrClearModulesUnderLogo(clear: Boolean) = updateQrStyle { it.copy(clearModulesUnderLogo = clear) }

    /** Activa/desactiva el logo de IMEX al centro del código QR. */
    fun setQrLogoEnabled(enabled: Boolean) = updateQrStyle { it.copy(logoEnabled = enabled) }

    fun setSampleDataMode(mode: SampleDataMode) {
        val label = when (mode) {
            SampleDataMode.STANDARD -> ZebraZplBuilder.buildTestLabel()
            SampleDataMode.LONG_TEXT -> ZebraZplBuilder.buildTestLabel().copy(
                numeroEntrada = "987654-99",
                cliente = "DISTRIBUIDORA DE ABARROTES Y MERCANCIAS GENERALES S.A. DE C.V.",
                caja = "PMM-CAJA-NORTE-99",
                guia = "MEX-GUIA-EXPRESS-998877",
                ubicaciones = "B01-A2, B01-A3, B01-A4",
                parcial = "100/100"
            )
            SampleDataMode.FOLDER -> ZebraZplBuilder.buildTestLabel().copy(
                tipoEtiqueta = "CARPETA",
                parcial = ""
            )
            SampleDataMode.SINGLE_BOX -> ZebraZplBuilder.buildTestLabel().copy(
                parcial = "1/1"
            )
        }
        _uiState.update { it.copy(sampleDataMode = mode, previewLabel = label) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun setZoomLevel(zoom: Float) {
        _uiState.update { it.copy(zoomLevel = zoom.coerceIn(0.75f, 2.0f)) }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.layout
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                layout = previous,
                canUndo = undoStack.isNotEmpty(),
                canRedo = true,
                message = "Deshecho",
                lastActionSucceeded = true
            )
        }
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.layout
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                layout = next,
                canUndo = true,
                canRedo = redoStack.isNotEmpty(),
                message = "Rehecho",
                lastActionSucceeded = true
            )
        }
    }

    // --- Arrastre con alineación asistida ---

    /**
     * Posición "cruda" del elemento durante el arrastre, sin el ajuste de alineación. Se conserva
     * aparte para que al pegarse a una guía el elemento no quede atrapado: los movimientos del
     * dedo se siguen acumulando aquí y en cuanto superan la tolerancia el elemento se despega.
     */
    private var dragRawX: Int = 0
    private var dragRawY: Int = 0

    /** Arranca un arrastre: registra UN solo paso de deshacer para todo el gesto. */
    fun beginDrag(target: DragTarget) {
        val element = elementOf(target) ?: return
        dragRawX = element.x
        dragRawY = element.y
        pushUndoState()
        when (target) {
            is DragTarget.Fixed -> selectElement(target.id)
            is DragTarget.Text -> selectCustomText(target.id)
            is DragTarget.Image -> selectCustomImage(target.id)
        }
    }

    /** Aplica el desplazamiento del dedo y ajusta la posición a las guías cercanas. */
    fun dragBy(target: DragTarget, deltaX: Int, deltaY: Int) {
        val element = elementOf(target) ?: return
        val layout = _uiState.value.layout
        dragRawX += deltaX
        dragRawY += deltaY

        val result = LabelSnapEngine.snap(
            moving = SnapBox(dragRawX, dragRawY, element.width, element.height),
            others = otherBoxes(target),
            canvasWidth = layout.widthDots,
            canvasHeight = layout.heightDots,
            margin = marginDots()
        )

        applyDragPosition(target, result.x, result.y)
        _uiState.update { it.copy(activeGuides = result.takeIf { guides -> guides.snapped }) }
    }

    /** Termina el arrastre y quita las guías de la pantalla. */
    fun endDrag() {
        _uiState.update { it.copy(activeGuides = null) }
    }

    private fun elementOf(target: DragTarget): ZebraLabelElement? {
        val layout = _uiState.value.layout
        return when (target) {
            is DragTarget.Fixed -> layout.element(target.id)
            is DragTarget.Text -> layout.customTexts.firstOrNull { it.id == target.id }?.element
            is DragTarget.Image -> layout.customImages.firstOrNull { it.id == target.id }?.element
        }
    }

    /** Cajas del resto de elementos visibles: son las referencias contra las que se alinea. */
    private fun otherBoxes(target: DragTarget): List<SnapBox> {
        val layout = _uiState.value.layout
        val boxes = mutableListOf<SnapBox>()
        ZebraLabelElementId.values().forEach { id ->
            if (target is DragTarget.Fixed && target.id == id) return@forEach
            val element = layout.element(id)
            if (!element.visible) return@forEach
            boxes += SnapBox(element.x, element.y, element.width, element.height)
        }
        layout.customTexts.forEach { customText ->
            if (target is DragTarget.Text && target.id == customText.id) return@forEach
            if (!customText.element.visible) return@forEach
            boxes += with(customText.element) { SnapBox(x, y, width, height) }
        }
        layout.customImages.forEach { customImage ->
            if (target is DragTarget.Image && target.id == customImage.id) return@forEach
            if (!customImage.element.visible) return@forEach
            boxes += with(customImage.element) { SnapBox(x, y, width, height) }
        }
        return boxes
    }

    /** Mueve sin tocar el historial: el paso de deshacer ya se registró en [beginDrag]. */
    private fun applyDragPosition(target: DragTarget, x: Int, y: Int) {
        _uiState.update { state ->
            val layout = state.layout
            val updated = when (target) {
                is DragTarget.Fixed -> layout.replaceElement(
                    target.id,
                    constrain(layout.element(target.id).copy(x = x, y = y))
                )
                is DragTarget.Text -> layout.copy(
                    customTexts = layout.customTexts.map {
                        if (it.id == target.id) it.copy(element = constrain(it.element.copy(x = x, y = y))) else it
                    }
                )
                is DragTarget.Image -> layout.copy(
                    customImages = layout.customImages.map {
                        if (it.id == target.id) it.copy(element = constrain(it.element.copy(x = x, y = y))) else it
                    }
                )
            }
            state.copy(layout = updated, message = null, lastActionSucceeded = null)
        }
    }

    fun moveSelected(deltaX: Int, deltaY: Int) {
        updateSelected { element ->
            constrain(element.copy(x = element.x + deltaX, y = element.y + deltaY))
        }
    }

    fun updateSelectedPosition(x: Int? = null, y: Int? = null) {
        updateSelected { element ->
            constrain(element.copy(x = x ?: element.x, y = y ?: element.y))
        }
    }

    fun updateSelectedWidth(width: Int) {
        val isSquare = _uiState.value.selectedElement.isSquare
        updateSelected { element ->
            if (isSquare) constrain(element.copy(width = width, height = width))
            else constrain(element.copy(width = width))
        }
    }

    fun updateSelectedHeight(height: Int) {
        updateSelected { constrain(it.copy(height = height)) }
    }

    fun updateSelectedFontHeight(fontHeight: Int) {
        updateSelected { constrain(it.copy(fontHeight = fontHeight)) }
    }

    fun updateSelectedFontWidth(fontWidth: Int) {
        updateSelected { constrain(it.copy(fontWidth = fontWidth)) }
    }

    fun updateSelectedFontFamily(fontFamily: LabelFontFamily) {
        updateSelected { it.copy(fontFamily = fontFamily) }
    }

    fun updateSelectedAlignment(alignment: LabelAlignment) {
        updateSelected { it.copy(alignment = alignment) }
    }

    fun updateSelectedBold(bold: Boolean) {
        updateSelected { it.copy(bold = bold) }
    }

    fun updateSelectedItalic(italic: Boolean) {
        updateSelected { it.copy(italic = italic) }
    }

    fun updateSelectedUnderline(underline: Boolean) {
        updateSelected { it.copy(underline = underline) }
    }

    fun updateSelectedLetterSpacing(letterSpacing: Float) {
        updateSelected { it.copy(letterSpacing = letterSpacing) }
    }

    fun updateSelectedVisibility(visible: Boolean) {
        updateSelected { it.copy(visible = visible) }
    }

    fun updateSelectedInverted(inverted: Boolean) {
        updateSelected { it.copy(inverted = inverted) }
    }

    fun updateSelectedBorderWidth(borderWidth: Int) {
        updateSelected { it.copy(borderWidth = borderWidth.coerceIn(0, 20)) }
    }

    fun updateSelectedRotation(rotation: Int) {
        updateSelected { it.copy(rotation = rotation) }
    }

    // --- Matrices de alineación rápida ---

    fun alignSelectedLeft() {
        updateSelected { constrain(it.copy(x = marginDots())) }
    }

    fun alignSelectedCenterH() {
        updateSelected { element ->
            val centerX = (_uiState.value.layout.widthDots - element.width) / 2
            constrain(element.copy(x = centerX))
        }
    }

    fun alignSelectedRight() {
        updateSelected { element ->
            val rightX = _uiState.value.layout.widthDots - marginDots() - element.width
            constrain(element.copy(x = rightX))
        }
    }

    fun alignSelectedTop() {
        updateSelected { constrain(it.copy(y = marginDots())) }
    }

    fun alignSelectedCenterV() {
        updateSelected { element ->
            val centerY = (_uiState.value.layout.heightDots - element.height) / 2
            constrain(element.copy(y = centerY))
        }
    }

    fun alignSelectedBottom() {
        updateSelected { element ->
            val bottomY = _uiState.value.layout.heightDots - marginDots() - element.height
            constrain(element.copy(y = bottomY))
        }
    }

    // --- Plantillas y JSON ---

    fun applyPreset(presetLayout: ZebraLabelLayout, presetName: String) {
        pushUndoState()
        _uiState.update {
            it.copy(
                layout = presetLayout,
                message = "Se aplicó la plantilla: $presetName",
                lastActionSucceeded = true
            )
        }
    }

    fun toggleJsonDialog(show: Boolean) {
        _uiState.update { it.copy(showJsonDialog = show) }
    }

    fun getLayoutJson(): String {
        return gson.toJson(_uiState.value.layout)
    }

    fun importLayoutJson(json: String): Boolean {
        return try {
            val layout = deserializeZebraLabelLayout(json, _uiState.value.labelType)
            pushUndoState()
            _uiState.update {
                it.copy(
                layout = layout,
                    selectedCustomTextId = null,
                    selectedCustomImageId = null,
                    showJsonDialog = false,
                    message = "Plantilla JSON importada correctamente",
                    lastActionSucceeded = true
                )
            }
            true
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    message = "Error al deserializar JSON: ${e.message}",
                    lastActionSucceeded = false
                )
            }
            false
        }
    }

    /**
     * Se invoca fuera del hilo principal desde Compose; no conserva bitmaps en el ViewModel.
     * De paso captura el reporte de verificación del QR para mostrarlo en vivo junto al lienzo.
     */
    fun renderPreview(layout: ZebraLabelLayout, label: ZebraLabelData): Bitmap {
        var report: QrRenderReport? = null
        val reading = labelBitmapRenderer.renderReadingOrientation(label, layout) { report = it }
        return try {
            ZplGraphicEncoder.toMonochromeBitmap(reading)
        } finally {
            reading.recycle()
            _uiState.update { it.copy(qrReport = report) }
        }
    }

    fun restoreReference() {
        pushUndoState()
        val type = _uiState.value.labelType
        _uiState.update {
            it.copy(
                layout = type.defaultLayout(),
                selectedCustomTextId = null,
                selectedCustomImageId = null,
                selectedElement = if (type == ZebraLabelType.CASETA) {
                    ZebraLabelElementId.QR
                } else {
                    ZebraLabelElementId.REFERENCE
                },
                message = "Se restauró el formato de ${type.title.lowercase()}. Guarda para aplicarlo.",
                lastActionSucceeded = true
            )
        }
    }

    fun saveLayout() {
        val state = _uiState.value
        layoutPreferences.saveLayout(state.layout, state.labelType)
        _uiState.update {
            it.copy(
                message = "Guardando formato de ${state.labelType.title.lowercase()} en el servidor...",
                lastActionSucceeded = true
            )
        }
        viewModelScope.launch {
            val result = layoutPreferences.saveLayoutToServer(state.layout, state.labelType)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        message = "Formato de ${state.labelType.title.lowercase()} guardado y sincronizado para todos los dispositivos.",
                        lastActionSucceeded = true
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        message = "Guardado localmente. Error al sincronizar con el servidor: ${result.exceptionOrNull()?.message}",
                        lastActionSucceeded = false
                    )
                }
            }
        }
    }

    fun printTest() {
        val layout = _uiState.value.layout
        val testLabel = _uiState.value.previewLabel
        val labelType = _uiState.value.labelType
        layoutPreferences.saveLayout(layout, labelType)
        val config = printerPreferences.getConfig(labelType)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isPrinting = true,
                    message = "Guardando formato y enviando prueba a ${config.host}:${config.port}...",
                    lastActionSucceeded = null
                )
            }
            val resolution = printerService.readPrintheadResolutionDpi(config)
            val targetDpi = resolution.getOrNull()
            if (targetDpi == null) {
                val errorMessage = resolution.exceptionOrNull()?.message
                    ?: "No se pudo leer la resolución de la Zebra."
                _uiState.update {
                    it.copy(
                        isPrinting = false,
                        message = "No se imprimió. $errorMessage",
                        lastActionSucceeded = false
                    )
                }
                return@launch
            }
            val result = if (BuildConfig.USE_IMAGE_LABELS) {
                val zpl = try {
                    labelZplFactory.buildLabels(
                        listOf(testLabel),
                        layoutOverride = layout,
                        targetDpi = targetDpi
                    )
                } catch (error: com.example.pmmovil.data.printing.LabelQrUnavailableException) {
                    _uiState.update {
                        it.copy(
                            isPrinting = false,
                            message = "No se imprimió: ${error.message}",
                            lastActionSucceeded = false
                        )
                    }
                    return@launch
                }
                printerService.printZpl(config, zpl, labelCount = 1)
            } else {
                printerService.printLabels(config, listOf(testLabel))
            }
            _uiState.update {
                it.copy(
                    isPrinting = false,
                    message = result.userMessage(),
                    lastActionSucceeded = result.isSuccess
                )
            }
        }
    }

    private fun pushUndoState() {
        undoStack.addLast(_uiState.value.layout)
        if (undoStack.size > 30) undoStack.removeFirst()
        redoStack.clear()
        _uiState.update { it.copy(canUndo = true, canRedo = false) }
    }

    private fun updateSelected(transform: (ZebraLabelElement) -> ZebraLabelElement) {
        pushUndoState()
        _uiState.update { state ->
            val selected = state.selectedElement
            state.copy(
                layout = state.layout.replaceElement(selected, transform(state.layout.element(selected))),
                message = null,
                lastActionSucceeded = null
            )
        }
    }

    private fun constrain(element: ZebraLabelElement): ZebraLabelElement {
        val layout = _uiState.value.layout
        return element.constrained(layout.widthDots, layout.heightDots)
    }

    private fun marginDots(): Int = if (_uiState.value.labelType == ZebraLabelType.CASETA) 20 else 65

    private fun updateCustomTextLayer(
        id: String,
        transform: (ZebraCustomText) -> ZebraCustomText
    ) {
        if (_uiState.value.layout.customTexts.none { it.id == id }) return
        pushUndoState()
        _uiState.update { state ->
            state.copy(
                layout = state.layout.copy(
                    customTexts = state.layout.customTexts.map {
                        if (it.id == id) transform(it) else it
                    }
                ),
                selectedCustomTextId = id,
                message = null,
                lastActionSucceeded = null
            )
        }
    }

    private fun updateCustomTextElement(
        id: String,
        transform: (ZebraLabelElement) -> ZebraLabelElement
    ) {
        updateCustomTextLayer(id) { customText ->
            customText.copy(element = constrain(transform(customText.element)))
        }
    }

    private fun nextCustomTextId(layout: ZebraLabelLayout): String {
        var index = layout.customTexts.size + 1
        var id = "custom-$index"
        while (layout.customTexts.any { it.id == id }) {
            index++
            id = "custom-$index"
        }
        return id
    }

    private fun updateCustomImageLayer(
        id: String,
        transform: (ZebraCustomImage) -> ZebraCustomImage
    ) {
        if (_uiState.value.layout.customImages.none { it.id == id }) return
        pushUndoState()
        _uiState.update { state ->
            state.copy(
                layout = state.layout.copy(
                    customImages = state.layout.customImages.map {
                        if (it.id == id) transform(it) else it
                    }
                ),
                selectedCustomImageId = id,
                message = null,
                lastActionSucceeded = null
            )
        }
    }

    private fun updateCustomImageElement(
        id: String,
        transform: (ZebraCustomImage, ZebraLabelElement) -> ZebraLabelElement
    ) {
        updateCustomImageLayer(id) { customImage ->
            customImage.copy(element = constrain(transform(customImage, customImage.element)))
        }
    }

    private fun nextCustomImageId(layout: ZebraLabelLayout): String {
        var index = layout.customImages.size + 1
        var id = "image-$index"
        while (layout.customImages.any { it.id == id }) {
            index++
            id = "image-$index"
        }
        return id
    }

    private companion object {
        /** Desplazamiento de la copia respecto al original, para que se vea que son dos. */
        const val DUPLICATE_OFFSET_DOTS = 30
    }
}
