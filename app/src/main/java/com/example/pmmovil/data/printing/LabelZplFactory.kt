package com.example.pmmovil.data.printing

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Construye el ZPL de la etiqueta de entrada como imagen embebida (^GF/Z64) en vez del
 * ZPL de texto hand-coded de [ZebraZplBuilder].
 *
 * Flujo por etiqueta: [LabelBitmapRenderer] dibuja el bitmap de la plantilla -> [ZplGraphicEncoder]
 * lo comprime a un campo ^GF -> se envuelve en un ZPL minimo con el tamano propio de la etiqueta.
 * La velocidad y oscuridad pertenecen al perfil real de cada Zebra y no se fuerzan por trabajo.
 * El resultado viaja por el mismo socket TCP:9100 via
 * [ZebraPrinterService.printZpl], sin tocar impresora, picker ni preferencias.
 *
 * El render/compresion corre en Dispatchers.Default (los bitmaps ocupan ~8 MB cada uno).
 *
 * No se mandan ^PR ni ^MD porque cambiarian preferencias compartidas del equipo en cada lote.
 * A diferencia del ZPL de texto de ZebraZplBuilder, este bloque no manda ^CI (juego de
 * caracteres) porque el ^GF es binario puro y no hay texto ^FD que codificar.
 */
@Singleton
class LabelZplFactory @Inject constructor(
    private val renderer: LabelBitmapRenderer,
    private val layoutPreferences: ZebraLabelLayoutPreferences
) {

    /** Une varias etiquetas (1 CARPETA + N parciales) en un unico bloque ZPL. */
    suspend fun buildLabels(
        labels: List<ZebraLabelData>,
        compress: Boolean = true,
        layoutOverride: ZebraLabelLayout? = null,
        targetDpi: Int = ZebraLabelLayout.REFERENCE_PRINTHEAD_DPI,
        /** Se llama tras dibujar cada etiqueta con (hechas, total), desde un hilo de fondo. */
        onLabelRendered: ((done: Int, total: Int) -> Unit)? = null
    ): String {
        // Una instantánea evita mezclar dos diseños si el usuario guarda mientras se imprime un lote.
        val layout = (layoutOverride ?: layoutPreferences.getLayout())
            .scaledForPrintheadDpi(targetDpi)
        return withContext(Dispatchers.Default) {
            labels.mapIndexed { index, label ->
                buildSingleLabel(label, layout, compress).also { onLabelRendered?.invoke(index + 1, labels.size) }
            }.joinToString(separator = "\n")
        }
    }

    private fun buildSingleLabel(
        label: ZebraLabelData,
        layout: ZebraLabelLayout,
        compress: Boolean
    ): String {
        val bitmap = renderer.render(label, layout)
        val graphic = try {
            ZplGraphicEncoder.encode(bitmap, compress = compress)
        } finally {
            bitmap.recycle()
        }
        val copies = label.copies.coerceAtLeast(1)
        return buildString {
            append("^XA\n")
            // Sin ^CI: el ^GF es binario, no hay ^FD de texto que dependa del juego de caracteres.
            val zplWidth = if (layout.rotate) layout.heightDots else layout.widthDots
            val zplHeight = if (layout.rotate) layout.widthDots else layout.heightDots
            append("^PW").append(zplWidth).append('\n')
            append("^LL").append(zplHeight).append('\n')
            append("^LH0,0\n")
            append("^FO0,0").append(graphic).append("^FS\n")
            append("^PQ").append(copies).append('\n')
            append("^XZ")
        }
    }

}
