package com.example.pmmovil.data.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import com.example.pmmovil.BuildConfig
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Foto de evidencia con reintento invisible: toma la foto y, si sale blanda (movida o desenfocada), toma otra en el
 * acto y se queda con la mas nitida de la misma escena. El operador toca el obturador una vez y no hace nada mas.
 *
 * Reemplaza a una llamada directa a `ImageCapture.takePicture` con el mismo contrato: [onSaved] recibe el mismo
 * archivo que se paso en [takeBest], una sola vez. La logica esta en [StillBurst] (probada sin Android).
 *
 * Por que existe: probados Real-ESRGAN, DarkIR y NAFNet con fotos reales de bodega, ninguno recupera una foto ya
 * desenfocada. Solo se puede evitar al tomarla. Ver tambien el mejorador clasico del API (FotoMejoraService).
 */
object SmartStillCapture {
    private const val TAG = "PMM-STILL"
    private const val STRAY_AGE_MS = 10 * 60 * 1000L

    /** Hilo aparte con prioridad baja: medir una foto no debe quitarle fluidez al visor. */
    private val workExecutor: Executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "pmm-still-analysis").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    /**
     * @param onFirstShot la primera foto ya quedo guardada: la pantalla puede quitar el destello blanco. El obturador
     * debe seguir bloqueado hasta [onSaved] porque un reintento puede seguir en curso.
     * @param onSaved la foto elegida quedo en [target].
     * @param onError el primer disparo fallo.
     */
    fun takeBest(
        context: Context,
        imageCapture: ImageCapture,
        target: File,
        onFirstShot: () -> Unit = {},
        onSaved: (File) -> Unit,
        onError: () -> Unit
    ) {
        val main = ContextCompat.getMainExecutor(context)
        val shooter = CameraXStillShooter(imageCapture, main)

        if (!StillBurstPolicy.ENABLED) {
            shooter.shoot(target) { ok ->
                if (ok) {
                    onFirstShot()
                    onSaved(target)
                } else {
                    onError()
                }
            }
            return
        }

        val folder = target.parentFile
        workExecutor.execute { sweepStrays(folder) }
        StillBurst(
            shooter = shooter,
            analyzer = BitmapStillAnalyzer,
            main = main,
            work = workExecutor,
            retryBelow = BuildConfig.STILL_RETRY_BELOW,
            // Solo en debug: las extras descartadas se guardan para poder ver despues si el filtro de parecido rechazo
            // escenas que si eran la misma. En release se borran como siempre.
            discard = { file -> if (BuildConfig.DEBUG) keepRejected(file) else file.delete() },
            log = { Log.i(TAG, it) }
        ).capture(
            target = target,
            extraFile = { n -> File(folder, "${target.name}.r$n.tmp") },
            onFirstShot = onFirstShot,
            onSaved = onSaved,
            onError = onError
        )
    }

    /** Debug: mueve la extra descartada a `still_rechazadas/` como `<foto>.rN.jpg` (de `<foto>.jpg.rN.tmp`). */
    private fun keepRejected(file: File) {
        val folder = File(file.parentFile, "still_rechazadas").apply { mkdirs() }
        val name = file.name.removeSuffix(".tmp").replace(".jpg.r", ".r") + ".jpg"
        if (!file.renameTo(File(folder, name))) file.delete()
    }

    /** Borra disparos extra que quedaron huerfanos (la app se cerro a medio reintento). */
    private fun sweepStrays(folder: File?) {
        val limit = System.currentTimeMillis() - STRAY_AGE_MS
        folder?.listFiles { f -> f.name.endsWith(".r1.tmp") || f.name.endsWith(".r2.tmp") }
            ?.filter { it.lastModified() < limit }
            ?.forEach { it.delete() }
    }
}

private class CameraXStillShooter(
    private val imageCapture: ImageCapture,
    private val executor: Executor
) : StillShooter {
    override fun shoot(target: File, onResult: (Boolean) -> Unit) {
        try {
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(target).build(),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        onResult(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e("PMM-STILL", "Error al tomar foto: ${exception.message}", exception)
                        onResult(false)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e("PMM-STILL", "No se pudo iniciar la foto: ${e.message}", e)
            onResult(false)
        }
    }
}

/**
 * Lee la foto reducida (nunca los 12 MP completos) y la mide con [StillQuality]. Cuesta decenas de milisegundos,
 * en un hilo de fondo.
 */
private object BitmapStillAnalyzer : StillAnalyzer {
    override fun analyze(file: File): StillSample? {
        val started = SystemClock.elapsedRealtime()
        val path = file.absolutePath
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // Reduce por potencias de 2 sin bajar de 1280 px: mismo criterio que PhotoPipeline y que el medidor del servidor.
        val longSide = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longSide / (sample * 2) >= StillQuality.MEASURE_LONG_SIDE) sample *= 2

        val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val gray = try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            GrayPlane.fromArgb(pixels, bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }

        val measured = gray.scaledToLongSide(StillQuality.MEASURE_LONG_SIDE)
        val result = StillSample(StillQuality.sharpness(measured), StillQuality.thumbnail(measured))
        Log.d("PMM-STILL", "${file.name}: nitidez=${result.sharpness.toInt()} (${SystemClock.elapsedRealtime() - started} ms)")
        return result
    }
}
