package com.example.pmmovil.ui.ar

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.pmmovil.ui.scanner.BarcodeAnalyzer
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Vista 2D de respaldo para dispositivos que no pueden iniciar una sesión ARCore. */
@Composable
internal fun EntryArAssistedCamera(
    modifier: Modifier = Modifier,
    onQrSeen: (String, Float, Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnQrSeen = rememberUpdatedState(onQrSeen)
    val previewView = remember(context) {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(previewView, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val disposed = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null
        var analyzer: BarcodeAnalyzer? = null
        var previewUseCase: Preview? = null
        var analysisUseCase: ImageAnalysis? = null
        val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
        cameraProviderFuture.addListener({
            if (disposed.get()) return@addListener
            runCatching {
                cameraProvider = cameraProviderFuture.get()
                previewUseCase = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                analyzer = BarcodeAnalyzer(
                    barcodeFormats = Barcode.FORMAT_QR_CODE,
                    continuous = true,
                    repeatCooldownMillis = 1_000L,
                    minConsecutiveReads = 2,
                    onPotentialBarcodes = { candidates ->
                        candidates.asSequence()
                            .filter { it.hasDecodedValue && !it.value.isNullOrBlank() }
                            .take(6)
                            .forEach { candidate ->
                                currentOnQrSeen.value(
                                    candidate.value.orEmpty(),
                                    candidate.centerX,
                                    candidate.centerY
                                )
                            }
                    },
                    onBarcodeScanned = { }
                )
                analysisUseCase = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer!!) }
                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    previewUseCase!!,
                    analysisUseCase!!
                )
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed.set(true)
            runCatching {
                val preview = previewUseCase
                val analysis = analysisUseCase
                if (preview != null && analysis != null) cameraProvider?.unbind(preview, analysis)
            }
            analyzer?.close()
            analysisExecutor.shutdownNow()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier
    )
}
