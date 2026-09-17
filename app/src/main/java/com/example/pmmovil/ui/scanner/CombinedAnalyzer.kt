package com.example.pmmovil.ui.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * Combina dos analyzers para que operen sobre el mismo [ImageProxy]
 * con un unico `ImageAnalysis` registrado en CameraX.
 *
 * Es necesario porque CameraX no resuelve bien dos `ImageAnalysis` con
 * `setTargetResolution` diferentes en el mismo `bindToLifecycle` (la
 * camara queda sin enlazar). Este wrapper reparte el mismo frame a
 * los dos delegates. El cierre del proxy lo hace [BarcodeAnalyzer]
 * cuando termina ML Kit, porque esa lectura es asincrona.
 *
 * El orden es importante:
 * 1. Primero se procesa la calidad del frame (sincrono, rapido).
 * 2. Despues OCR con ML Kit (asincrono, pesado).
 * Asi el [BarcodeAnalyzer] puede consultar el `latestFrameState` que
 * ya escribio el [FrameQualityAnalyzer] cuando evalue el idle-zoom.
 */
class CombinedAnalyzer(
    private val frameQualityAnalyzer: ImageAnalysis.Analyzer,
    private val barcodeAnalyzer: ImageAnalysis.Analyzer
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        runPipeline(
            onQuality = { frameQualityAnalyzer.analyze(imageProxy) },
            onBarcode = { barcodeAnalyzer.analyze(imageProxy) },
            closer = {}
        )
    }

    companion object {
        /**
         * Logica pura testeable: ejecuta primero `onQuality`, despues
         * `onBarcode` (incluso si `onQuality` lanza), y siempre llama
         * a `closer`. Permite cubrir el contrato sin instanciar un
         * [ImageProxy] real.
         */
        fun runPipeline(
            onQuality: () -> Unit,
            onBarcode: () -> Unit,
            closer: () -> Unit
        ) {
            var qualityError: Throwable? = null
            try {
                onQuality()
            } catch (t: Throwable) {
                qualityError = t
            }
            try {
                onBarcode()
            } catch (t: Throwable) {
                qualityError?.let { t.addSuppressed(it) }
                throw t
            } finally {
                closer()
            }
            qualityError?.let { throw it }
        }
    }
}
