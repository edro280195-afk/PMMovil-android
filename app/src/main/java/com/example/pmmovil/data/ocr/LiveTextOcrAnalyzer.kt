package com.example.pmmovil.data.ocr

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.google.android.gms.tasks.Tasks
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LiveTextOcrAnalyzer(
    private val shouldSkipFrame: () -> Boolean = { false },
    /**
     * Se llama con CADA imagen real que entrega CameraX a este analizador,
     * antes de cualquier descarte por `shouldSkipFrame` o el limitador de
     * cadencia. Es la prueba de vida de la sesión que no depende de los
     * metadatos de captura de Camera2 -- ver la nota en
     * `CameraWarmupTracker.onLuminance` sobre por qué eso importa en el HAL
     * de MediaTek de la Armor Pad Pro.
     */
    private val onFrameSeen: () -> Unit = {},
    /**
     * Simbologias que se buscan por cuadro. Por omision las de **nuestras**
     * etiquetas (QR + Code 128), que es lo que lee Entrada Caseta.
     *
     * Una etiqueta de mercancia de cliente es otra cosa: trae la simbologia que
     * se le ocurrio al proveedor. La de allnex SETAL, por ejemplo, trae un
     * **DataMatrix**, que con el set propio no se mira siquiera -- y leer el
     * codigo impreso es exacto e instantaneo, muy superior a reconocer el texto.
     * Para ese caso esta [BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS].
     *
     * No usar FORMAT_ALL_FORMATS: son trece detectores por cuadro y ahogan al
     * HAL en la pantalla mas pesada de la app.
     */
    private val formatosDeCodigo: Int = BarcodeFormatSets.PMM_LABELS,
    /**
     * Analisis vertical de texto rotado (stencil). Sirve para el numero de caja
     * pintado de arriba a abajo en un contenedor -- el caso de Entrada Caseta --
     * y **no sirve para una etiqueta de producto**, donde el texto es horizontal.
     *
     * Sigue sin ser gratis, pero ya no se paga en el hilo de UI: el camino caro
     * (plano Y a bitmap gris, enderezarlo, partirlo en tiras) corre en
     * [stencilExecutor]. Medido en el Pixel 9 Pro XL con la imagen de analisis
     * en 1280x960 eran **12 rafagas de 93 a 121 ms cada 3.6 s en el hilo
     * principal**, unos 13 cuadros perdidos de golpe a 120 Hz; de ahi el
     * cambio de hilo.
     *
     * Queda apagable igual, porque donde no aporta nada lo mejor sigue siendo
     * no gastar la CPU. Encendido por omision para no cambiarle el
     * comportamiento a Entrada Caseta, que es quien lo necesita.
     */
    private val analizarTextoVertical: Boolean = true,
    /**
     * Donde quedo cada linea de texto y cada codigo dentro del cuadro, para
     * poder marcarlo encima de la preview. Se emite en el MISMO cuadro que
     * [onResult], asi que lo que se dibuja es exactamente lo que se leyo y no
     * la lectura anterior.
     *
     * Es opcional a proposito: la captura de placa y caja de Entrada Caseta no
     * dibuja nada, y asi no paga el costo de armar la lista en cada cuadro.
     */
    private val onGeometria: (GeometriaEnVivo) -> Unit = {},
    private val onResult: (LocalTextOcrResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // Esta pantalla ya corre OCR de texto sobre cada frame, asi que el
    // escaner de codigos se limita a las simbologias que imprime la empresa.
    // Con FORMAT_ALL_FORMATS se pagaban trece detectores por frame en la
    // pantalla mas pesada de la app, que es justo donde el HAL se ahogaba.
    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(formatosDeCodigo)
            .build()
    )

    @Volatile
    private var lastProcessedTime = 0L

    @Volatile
    private var closed = false

    /**
     * Solo lo toca el hilo del analizador (el executor que le pasa la pantalla
     * a `setAnalyzer`), asi que no necesita proteccion. Antes tambien se leia
     * desde el callback de ML Kit en el hilo principal, donde el contador ya
     * podia haber avanzado: la cadencia de "uno de cada seis" era aproximada y
     * ahora es exacta.
     */
    private var frameCount = 0

    /**
     * El camino caro del stencil vertical corre AQUI, nunca en el hilo de UI.
     *
     * Un hilo y cola de uno a proposito: dos pasadas de 100 ms encimadas no
     * sirven de nada -- el limitador deja pasar un cuadro cada 250 ms y el
     * stencil mira uno de cada seis, o sea una pasada cada ~1.5 s -- y si
     * alguna vez se encimaran, contra una preview en vivo lo correcto es
     * rechazar la nueva, no acumular trabajo que ya nacio viejo. Cuando la
     * rechaza, el cuadro se emite igual sin stencil (ver [lanzarStencil]).
     */
    private val stencilExecutor = ThreadPoolExecutor(
        1,
        1,
        0L,
        TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(1),
        ThreadFactory { tarea -> Thread(tarea, "pmm-ocr-stencil") }
    )

    /**
     * [onResult] y [onGeometria] se siguen emitiendo en el hilo principal, que
     * es donde se emitian antes de mover el stencil de hilo.
     *
     * No es por capricho: los llamadores cuentan los votos del candado de
     * estabilidad en estado sin sincronizar -- la cola de `StabilityLock` en
     * Entrada Caseta, los contadores de `EstabilidadDeCampos` en la lectura de
     * etiqueta -- y lo reinician desde el hilo principal (botones, zoom).
     * Emitir desde otro hilo pondria a competir ese reinicio con el conteo, y
     * el precio de fijar un numero equivocado es mucho mas alto que el de un
     * post al hilo principal.
     */
    private val principal = Handler(Looper.getMainLooper())

    @ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }

        onFrameSeen()

        // Con captura/análisis estático en curso, soltar el frame DE INMEDIATO: si se encola
        // en ML Kit (ocupado con la foto), el ImageProxy queda retenido, el stream de
        // ImageAnalysis se queda sin buffers y el HAL desploma la preview completa
        // ("Failed to get stream buffer from SBC manager" → cámara a 3-4fps).
        if (shouldSkipFrame()) {
            imageProxy.close()
            return
        }

        // Throttle: procesar maximo 4 veces por segundo
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < 250L) {
            imageProxy.close()
            return
        }

        val media = imageProxy.image
        if (media == null) {
            imageProxy.close()
            return
        }

        lastProcessedTime = now
        frameCount++

        val rotation = imageProxy.imageInfo.rotationDegrees

        // Se leen AQUI y no dentro del callback: cuando ML Kit contesta, la
        // imagen pudo cerrarse ya. Y van enderezadas porque los boundingBox que
        // devuelve ML Kit tambien vienen enderezados -- usar el ancho del sensor
        // con rotacion de 90 grados es justo lo que corre las marcas de lugar.
        val vertical = rotation == 90 || rotation == 270
        val anchoUpright = if (vertical) media.height else media.width
        val altoUpright = if (vertical) media.width else media.height

        // Los pixeles del stencil se copian AQUI, y por dos razones distintas.
        //
        // Una: cuando ML Kit contesta, CameraX ya pudo recuperar la imagen, y
        // el stencil corre despues de eso y en otro hilo. Sin copia leeria un
        // buffer ajeno.
        //
        // Dos: hay que copiar ANTES de `process()`. ML Kit recibe esta MISMA
        // imagen y la lee cuando le toca, asi que recorrer el plano en paralelo
        // seria moverle el cursor del buffer por debajo -- de ahi tambien el
        // `duplicate()` de [copiarPlanoY].
        //
        // Se paga en el hilo del analizador (de fondo), no en el de UI, y solo
        // en uno de cada seis cuadros. Es una copia de bloque del plano Y:
        // ~1.2 MB en 1280x960, nada al lado de los 93-121 ms que cuesta
        // interpretarla.
        val cuadroStencil = if (analizarTextoVertical && frameCount % 6 == 0) {
            copiarPlanoY(media, rotation)
        } else {
            null
        }

        // Analizar texto y barcode en paralelo (sin copias de bitmap)
        val input = InputImage.fromMediaImage(media, rotation)
        val textTask = recognizer.process(input)
        val barcodeTask = barcodeScanner.process(input)

        Tasks.whenAllComplete(textTask, barcodeTask)
            .addOnCompleteListener {
                try {
                    if (closed) return@addOnCompleteListener

                    val textResult = textTask.result
                    val barcodes = barcodeTask.result

                    val normalResult = textResult?.toLocalResult()
                        ?: LocalTextOcrResult("", emptyList())
                    val barcodeValues = barcodes
                        ?.mapNotNull { it.rawValue ?: it.displayValue }
                        ?.map { value ->
                            if (formatosDeCodigo == BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS) {
                                BarcodeValueNormalizer.normalizeLabel(value)
                            } else BarcodeValueNormalizer.normalize(value)
                        }
                        ?.filter(String::isNotBlank)
                        ?: emptyList()

                    val combinedResult = normalResult.copy(barcodes = barcodeValues)
                    Log.d("OCR_LIVE", "candidates=${normalResult.candidates.size} barcodes=${barcodeValues.size}")

                    runCatching {
                        onGeometria(
                            construirGeometria(textResult, barcodes, anchoUpright, altoUpright)
                        )
                    }

                    // Analisis vertical de texto rotado (stencil) -- uno de
                    // cada seis cuadros, y en su propio hilo. Si logra encolarse
                    // se queda con la emision de [onResult] para este cuadro,
                    // igual que antes se la quedaba el reconocimiento del
                    // compuesto; si no, se emite aqui como siempre.
                    if (cuadroStencil != null && barcodeValues.isEmpty()) {
                        if (lanzarStencil(cuadroStencil, combinedResult)) {
                            return@addOnCompleteListener
                        }
                    }

                    runCatching { onResult(combinedResult) }
                } catch (e: Exception) {
                    Log.e("OCR_LIVE", "Error en análisis de frame", e)
                } finally {
                    imageProxy.close()
                }
            }
    }

    fun close() {
        closed = true
        // Sin remover lo que ya este posteado al hilo principal: esos envios
        // ven `closed` y reciclan el compuesto que traigan (ver
        // [reconocerCompuesto]). Cancelarlos a ciegas dejaria el bitmap suelto.
        runCatching { stencilExecutor.shutdownNow() }
        runCatching { recognizer.close() }
        runCatching { barcodeScanner.close() }
    }

    /**
     * Encola el camino caro del stencil y, si lo consigue, se queda con la
     * obligacion de emitir [onResult] para este cuadro -- **una sola vez**, con
     * la mezcla cuando el compuesto aporta algo y con el resultado normal
     * cuando no. Eso es justo lo que hacia antes el reconocimiento del
     * compuesto, y mantenerlo asi importa: un segundo [onResult] por cuadro
     * seria un voto extra en el candado de estabilidad del llamador y el numero
     * se fijaria antes de lo que se fija hoy.
     *
     * Devuelve `false` cuando no pudo encolar (cola llena o analizador ya
     * cerrado) para que el llamador emita el resultado normal como siempre.
     */
    private fun lanzarStencil(
        cuadro: CuadroGris,
        resultadoNormal: LocalTextOcrResult
    ): Boolean {
        val tarea = Runnable {
            if (closed) return@Runnable
            val compuesto = runCatching { construirCompuestoVertical(cuadro) }
                .onFailure { Log.e("OCR_LIVE", "Error en el stencil vertical", it) }
                .getOrNull()
            principal.post { reconocerCompuesto(compuesto, resultadoNormal) }
        }
        return runCatching { stencilExecutor.execute(tarea) }.isSuccess
    }

    /**
     * Plano Y -> bitmap gris -> enderezado -> tira compuesta. Esto es lo que se
     * midio en 93-121 ms y lo unico que se movio de hilo; corre en
     * [stencilExecutor] y no vuelve a pisar el hilo de UI.
     */
    private fun construirCompuestoVertical(cuadro: CuadroGris): Bitmap? {
        val gris = aBitmapGris(cuadro) ?: return null
        val enderezado = rotateBitmap(gris, cuadro.rotacion.toFloat())
        return try {
            // El compuesto que devuelve es un bitmap propio, sin pixeles
            // compartidos con el origen, asi que se puede reciclar lo de aqui
            // en cuanto vuelve.
            extractVerticalNumberAsHorizontal(enderezado)
        } finally {
            if (enderezado !== gris) enderezado.recycle()
            gris.recycle()
        }
    }

    /**
     * Ya de vuelta en el hilo principal: reconocer el compuesto, mezclar y
     * emitir, exactamente como se hacia cuando todo el camino corria aqui. Es
     * la parte barata -- un `process()` que solo encola y una mezcla de listas
     * de candidatos -- y por eso se deja donde estaba.
     */
    private fun reconocerCompuesto(
        compuesto: Bitmap?,
        resultadoNormal: LocalTextOcrResult
    ) {
        if (closed) {
            compuesto?.recycle()
            return
        }
        if (compuesto == null) {
            runCatching { onResult(resultadoNormal) }
            return
        }

        val vInput = InputImage.fromBitmap(compuesto, 0)
        recognizer.process(vInput)
            .addOnSuccessListener { vText ->
                if (closed) return@addOnSuccessListener
                val vResult = vText.toLocalResult()
                val merged = mergeLocalResults(resultadoNormal, vResult)
                runCatching { onResult(merged) }
            }
            .addOnFailureListener {
                runCatching { onResult(resultadoNormal) }
            }
            .addOnCompleteListener {
                compuesto.recycle()
            }
    }

    /**
     * Una caja por **linea** de texto, no por bloque ni por palabra: el bloque
     * se come varios renglones y marca de mas (el nombre del producto junto con
     * el lote), y la palabra parte `LOT 2504298-135` en dos marcas sueltas. La
     * linea es la unidad con la que ademas trabajan las anclas de
     * [LabelFieldExtractor], asi que lo que se pinta coincide con lo que se
     * extrae.
     */
    private fun construirGeometria(
        texto: Text?,
        codigos: List<Barcode>?,
        ancho: Int,
        alto: Int
    ): GeometriaEnVivo {
        val cajas = ArrayList<CajaTexto>()
        texto?.textBlocks?.forEach { bloque ->
            bloque.lines.forEach { linea ->
                val r = linea.boundingBox ?: return@forEach
                cajas += CajaTexto(
                    texto = linea.text,
                    izquierda = r.left.toFloat(),
                    arriba = r.top.toFloat(),
                    derecha = r.right.toFloat(),
                    abajo = r.bottom.toFloat()
                )
            }
        }
        codigos?.forEach { codigo ->
            val r = codigo.boundingBox ?: return@forEach
            val valor = codigo.rawValue ?: codigo.displayValue ?: return@forEach
            cajas += CajaTexto(
                texto = valor,
                izquierda = r.left.toFloat(),
                arriba = r.top.toFloat(),
                derecha = r.right.toFloat(),
                abajo = r.bottom.toFloat(),
                esCodigo = true
            )
        }
        return GeometriaEnVivo(ancho, alto, cajas)
    }

    /**
     * Saca el plano de luminancia del cuadro a memoria propia. Es una copia de
     * bloque y nada mas: interpretarla cuesta cien veces mas, y eso se hace en
     * [aBitmapGris], ya en [stencilExecutor].
     */
    private fun copiarPlanoY(image: android.media.Image, rotacion: Int): CuadroGris? {
        if (image.format != ImageFormat.YUV_420_888) return null
        val planes = image.planes
        if (planes.size < 3) return null

        val plano = planes[0]
        // duplicate(): `get` mueve la posicion del buffer, y este buffer es el
        // de la imagen que ML Kit esta por leer. El duplicado tiene posicion
        // propia sobre la misma memoria, asi que no se paga una copia extra ni
        // se le toca el cursor a nadie.
        val buffer: ByteBuffer = plano.buffer.duplicate()
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        return CuadroGris(
            bytes = bytes,
            ancho = image.width,
            alto = image.height,
            pasoDeFila = plano.rowStride,
            pasoDePixel = plano.pixelStride,
            rotacion = rotacion
        )
    }

    /** Expande la luminancia copiada a un bitmap en gris. Hilo de fondo. */
    private fun aBitmapGris(cuadro: CuadroGris): Bitmap? {
        val w = cuadro.ancho
        val h = cuadro.alto
        if (w <= 0 || h <= 0) return null

        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val fila = y * cuadro.pasoDeFila
            val destino = y * w
            for (x in 0 until w) {
                val origen = fila + x * cuadro.pasoDePixel
                // La ultima fila del plano suele venir corta: el buffer acaba en
                // el ultimo pixel util, no al final del paso de fila.
                if (origen >= cuadro.bytes.size) break
                val gris = cuadro.bytes[origen].toInt() and 0xFF
                pixels[destino + x] = 0xFF000000.toInt() or (gris shl 16) or (gris shl 8) or gris
            }
        }

        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }
}

/**
 * Copia del plano de luminancia de un cuadro, con sus pasos de fila y de pixel,
 * para poder armar el bitmap gris despues de que CameraX recupere la imagen.
 */
private class CuadroGris(
    val bytes: ByteArray,
    val ancho: Int,
    val alto: Int,
    val pasoDeFila: Int,
    val pasoDePixel: Int,
    val rotacion: Int
)
