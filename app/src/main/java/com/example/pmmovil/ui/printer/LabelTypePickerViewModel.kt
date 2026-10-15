package com.example.pmmovil.ui.printer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.printing.LabelBitmapRenderer
import com.example.pmmovil.data.printing.ZebraLabelData
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraZplBuilder
import com.example.pmmovil.data.printing.ZplGraphicEncoder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/** Miniatura fiel de una plantilla: mismo raster de 1-bit que se manda a la Zebra. */
data class LabelThumbnail(
    val bitmap: Bitmap,
    val widthDots: Int,
    val heightDots: Int
)

/**
 * Renderiza la miniatura de cada tipo de etiqueta (Bodega/Caseta) con el formato ya guardado
 * por el usuario, para que elegir "qué etiqueta editar" sea visual en vez de un botón de texto
 * sin contexto.
 */
@HiltViewModel
class LabelTypePickerViewModel @Inject constructor(
    private val labelBitmapRenderer: LabelBitmapRenderer,
    private val layoutPreferences: ZebraLabelLayoutPreferences
) : ViewModel() {

    init {
        viewModelScope.launch {
            layoutPreferences.syncWithServer()
        }
    }

    private val sampleLabel: ZebraLabelData = ZebraZplBuilder.buildTestLabel()

    /** Se invoca fuera del hilo principal desde Compose; no conserva bitmaps en el ViewModel. */
    fun renderThumbnail(type: ZebraLabelType): LabelThumbnail {
        val layout = layoutPreferences.getLayout(type)
        val reading = labelBitmapRenderer.renderReadingOrientation(sampleLabel, layout)
        val monochrome = try {
            ZplGraphicEncoder.toMonochromeBitmap(reading)
        } finally {
            reading.recycle()
        }
        return LabelThumbnail(monochrome, layout.widthDots, layout.heightDots)
    }
}
