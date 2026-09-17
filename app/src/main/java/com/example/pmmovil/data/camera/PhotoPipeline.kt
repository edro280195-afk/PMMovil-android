package com.example.pmmovil.data.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.pmmovil.data.ocr.applyExifOrientation
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

/**
 * Cómo se prepara una foto antes de subirla. Uno solo para toda la app.
 *
 * ANTES había seis. Entrada Bodega bajaba a 1600 px con calidad 82; Caseta a
 * 1200 px con calidad 80 que podía desplomarse hasta 40; Caseta Entrada a 1280
 * con 70; y el worker de sincronización aplicaba `inSampleSize = 2` fijo, o sea
 * "la mitad de lo que midiera", sin mirar cuánto medía. La misma caja
 * fotografiada desde dos módulos llegaba distinta al servidor.
 *
 * Medido sobre una foto real de la tableta (12 MP, 3.2 MB), lo que se guardaba
 * eran entre 0.8 y 1.4 MP: se tiraba el 90 % de los píxeles. Y el tope de peso
 * no era el culpable -- Caseta tenía tope de 400 KB y producía 101 KB. Lo que
 * estaba bajo eran la resolución y la calidad, no el límite.
 *
 * 2026-09-07: subido de 2048/88 a 2560/92 (pedido explícito de mejorar
 * nitidez en Revisión, Entrada, Entrada Caseta y Salida a la vez, ya que las
 * cuatro pasan por aquí). Sobre la misma foto de referencia pesa ~650 KB --
 * sigue lejísimos de [MAX_BYTES], que se deja igual a propósito: es la
 * pared de seguridad para no producir un JPEG gigante en una foto rara con
 * muchísimo detalle, y una sola foto nunca debería acercarse a ella.
 */
object PhotoPipeline {

    /**
     * Lado mayor de la foto guardada.
     *
     * El sensor de la tableta da 48 MP y la cámara del sistema entrega 12 MP;
     * 2560 px (~3.7 MP para una foto 4:3) deja hacer zoom a un sello o a un
     * número de serie sin que el archivo se vuelva un problema para la red
     * de bodega.
     */
    const val LADO_MAX = 2560

    /** Calidad JPEG. De 92 para arriba la ganancia ya casi no se ve y el peso sí. */
    const val CALIDAD = 92

    /**
     * Piso de calidad.
     *
     * Sólo se baja si la foto no cabe en [MAX_BYTES]. El piso es alto a
     * propósito: los pipelines viejos permitían caer a 40 y a 54, y a esa altura
     * los bloques del JPEG se comen justo lo que se fue a fotografiar.
     */
    const val CALIDAD_MINIMA = 82

    /**
     * Techo por foto, antes de base64.
     *
     * Base64 infla un 34 %, así que 1.1 MB aquí son ~1.5 MB en el JSON. Con las
     * fotos normales de bodega no se llega: la referencia medida pesa 343 KB.
     * Esto es para la foto rara con muchísimo detalle (una tarima entera de
     * cajas impresas), no un objetivo.
     */
    const val MAX_BYTES = 1_100_000

    private const val TAG = "PMM-PHOTO"

    /**
     * Lee la foto del disco a un bitmap listo para comprimir: escalado a
     * [ladoMax] y con la rotación del EXIF ya aplicada.
     *
     * El escalado va en DOS pasos y no en uno:
     *
     *  1. `inSampleSize` (potencias de 2) para no traer 48 MP a memoria -- eso
     *     es lo que evita el OutOfMemory que se arriesgaba al decodificar la
     *     foto completa;
     *  2. un ajuste fino con filtro al tamaño exacto.
     *
     * El paso 1 se queda deliberadamente CORTO (`>=` contra el doble del
     * objetivo) para que el paso 2 siempre tenga píxeles de sobra con los que
     * promediar. Bajar de golpe con `inSampleSize` deja escalones en los bordes
     * -- que es exactamente donde está el texto de una etiqueta.
     */
    fun decodificarEscalada(path: String, ladoMax: Int = LADO_MAX): Bitmap? {
        val archivo = File(path)
        if (!archivo.exists()) {
            Log.w(TAG, "No existe la foto: $path")
            return null
        }

        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) {
            Log.w(TAG, "No se pudo leer el tamaño de la foto: $path")
            return null
        }

        val objetivo = ladoMax * 2
        var muestreo = 1
        while (limites.outWidth / (muestreo * 2) >= objetivo &&
            limites.outHeight / (muestreo * 2) >= objetivo
        ) {
            muestreo *= 2
        }

        val opciones = BitmapFactory.Options().apply { inSampleSize = muestreo }
        val decodificada = BitmapFactory.decodeFile(path, opciones) ?: return null

        val orientada = applyExifOrientation(decodificada, path)
        if (orientada !== decodificada) decodificada.recycle()

        val mayor = maxOf(orientada.width, orientada.height)
        if (mayor <= ladoMax) return orientada

        val razon = ladoMax.toFloat() / mayor
        val escalada = Bitmap.createScaledBitmap(
            orientada,
            (orientada.width * razon).roundToInt().coerceAtLeast(1),
            (orientada.height * razon).roundToInt().coerceAtLeast(1),
            true
        )
        if (escalada !== orientada) orientada.recycle()
        return escalada
    }

    /**
     * Comprime a JPEG, bajando la calidad sólo si hace falta para caber en
     * [maxBytes]. Nunca por debajo de [calidadMinima]: más vale un archivo
     * grande que una evidencia ilegible.
     */
    fun comprimir(
        bitmap: Bitmap,
        calidad: Int = CALIDAD,
        calidadMinima: Int = CALIDAD_MINIMA,
        maxBytes: Int = MAX_BYTES
    ): ByteArray {
        val salida = ByteArrayOutputStream()
        var q = calidad.coerceIn(calidadMinima, 100)
        while (true) {
            salida.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, salida)
            if (salida.size() <= maxBytes || q <= calidadMinima) return salida.toByteArray()
            q = (q - 6).coerceAtLeast(calidadMinima)
        }
    }

    /**
     * Foto lista para el API: base64 con el prefijo `data:image/jpeg;base64,`
     * que espera el servidor.
     */
    fun aDataUri(
        path: String,
        ladoMax: Int = LADO_MAX,
        calidad: Int = CALIDAD,
        calidadMinima: Int = CALIDAD_MINIMA,
        maxBytes: Int = MAX_BYTES
    ): String? = aBase64(path, ladoMax, calidad, calidadMinima, maxBytes)
        ?.let { "data:image/jpeg;base64,$it" }

    /** Igual, pero sin el prefijo: hay endpoints que reciben el base64 pelón. */
    fun aBase64(
        path: String,
        ladoMax: Int = LADO_MAX,
        calidad: Int = CALIDAD,
        calidadMinima: Int = CALIDAD_MINIMA,
        maxBytes: Int = MAX_BYTES
    ): String? {
        val bitmap = decodificarEscalada(path, ladoMax) ?: return null
        return try {
            val bytes = comprimir(bitmap, calidad, calidadMinima, maxBytes)
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo comprimir la foto $path", e)
            null
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}
