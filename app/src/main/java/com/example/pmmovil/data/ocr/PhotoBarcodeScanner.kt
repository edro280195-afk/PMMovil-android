package com.example.pmmovil.data.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Busca codigos de barras y 2D en una **foto a resolucion completa**.
 *
 * ## Por que hacia falta
 * El camino de la foto no miraba codigos en absoluto: [LocalTextOcr.readFromFile]
 * solo corre el reconocedor de texto y [PaddleOcrEngine] tampoco. La unica
 * fuente de codigos era el analisis en vivo, y ahi el cuadro va a 640x480, donde
 * un DataMatrix chico de etiqueta **no tiene pixeles suficientes para
 * decodificarse**.
 *
 * Resultado medido con la etiqueta de allnex SETAL impresa en papel: `codigos=0`
 * en todas las lecturas, pese a que la etiqueta trae un DataMatrix a la vista, y
 * 6,034 ms de espera reconociendo texto para sacar lo que el codigo tenia
 * exacto. La foto son 4080x3072: ahi el mismo codigo se decodifica sin esfuerzo.
 *
 * ## Por que va primero
 * Un codigo decodificado no tiene margen de error y cuesta una fraccion de lo
 * que cuesta reconocer texto. Es lo mas barato y lo mas confiable del pipeline,
 * asi que corre antes que cualquier OCR.
 */
@Singleton
class PhotoBarcodeScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * @param formatos mascara de simbologias, de `BarcodeFormatSets`. Conviene
     *        acotarla: cada formato habilitado es un detector mas por imagen.
     */
    suspend fun leer(path: String, formatos: Int): Result<List<String>> = runCatching {
        withContext(Dispatchers.Default) {
            val archivo = File(path)
            require(archivo.exists()) { "La foto no existe." }

            // Cliente de usar y tirar: esto corre una vez por captura, no por
            // cuadro, y asi no se queda un detector vivo con las simbologias de
            // producto cargadas cuando la pantalla ya se cerro.
            val escaner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder().setBarcodeFormats(formatos).build()
            )
            try {
                val imagen = InputImage.fromFilePath(context, Uri.fromFile(archivo))
                escaner.process(imagen).esperar()
                    .mapNotNull { it.rawValue ?: it.displayValue }
                    .map(BarcodeValueNormalizer::normalize)
                    .filter { it.isNotBlank() }
                    .distinct()
            } finally {
                escaner.close()
            }
        }
    }.onFailure { Log.e("OCR_CODIGO", "Error buscando codigos en la foto", it) }
}

private suspend fun <T> Task<T>.esperar(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { resultado ->
            if (continuation.isActive) continuation.resume(resultado)
        }
        addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWithException(error)
        }
        addOnCanceledListener {
            if (continuation.isActive) {
                continuation.cancel(CancellationException("ML Kit cancelo la lectura."))
            }
        }
    }
