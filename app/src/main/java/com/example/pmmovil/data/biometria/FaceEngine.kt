package com.example.pmmovil.data.biometria

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.pmmovil.data.camera.CameraTrace
import com.paddle.ocr.util.OpenCVUtils
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Motor de reconocimiento facial.
 *
 * Se apoya en dos modelos ONNX que corren dentro de OpenCV -- el mismo OpenCV
 * que el proyecto ya arrastraba por el modulo ppocr-sdk, asi que no agrega ni
 * una dependencia:
 *
 *  - YuNet  (FaceDetectorYN)   detecta el rostro y devuelve 5 puntos de
 *                              referencia (ojos, nariz, comisuras).
 *  - SFace  (FaceRecognizerSF) alinea el recorte con esos puntos y produce un
 *                              vector de 128 floats que representa la cara.
 *
 * Dos caras se comparan por similitud coseno entre sus vectores: 1.0 seria
 * identico y 0.0 sin relacion. El umbral que recomiendan los autores de SFace
 * es 0.363 para comparar una cara contra UNA plantilla; el acceso de la app
 * compara contra todo el banco, asi que usa un umbral mas alto y ademas exige
 * margen contra el segundo lugar. Ese umbral lo decide el SERVIDOR: aqui solo
 * se produce el vector.
 *
 * Los modelos viven en app/src/main/assets, o sea que existen en todas las
 * variantes. Son ~39 MB de assets; si algun dia estorban en el APK, el unico
 * cambio necesario esta en [copiarAsset]: bajar el .onnx del servidor la
 * primera vez en vez de leerlo de los assets. Todo lo demas ya trabaja contra
 * un archivo en filesDir.
 */
class FaceEngine private constructor(
    private val detector: FaceDetectorYN,
    private val recognizer: FaceRecognizerSF
) {
    private var detectorWidth = 0
    private var detectorHeight = 0

    /** Lo que se saca de una captura: el vector mas las condiciones en que se tomo. */
    data class Captura(
        val vector: FloatArray,
        val calidad: Calidad,
        val msDeteccion: Int,
        val msEmbedding: Int
    ) {
        val msTotal: Int get() = msDeteccion + msEmbedding

        // FloatArray no tiene equals estructural; se genera para callar al linter
        // y para que dos capturas distintas nunca se confundan en una lista.
        override fun equals(other: Any?): Boolean =
            this === other || (other is Captura && vector.contentEquals(other.vector))

        override fun hashCode(): Int = vector.contentHashCode()
    }

    /**
     * Condiciones medidas del rostro detectado. Estas son las variables que de
     * verdad deciden si el reconocimiento sirve o no en bodega.
     */
    data class Calidad(
        /** Confianza de YuNet, 0..1. */
        val scoreDeteccion: Float,
        /** Ancho del rostro en pixeles: proxy directo de que tan lejos esta la persona. */
        val rostroPx: Int,
        /** Brillo medio del recorte facial, 0..255. Debajo de ~40 ya es penumbra. */
        val luma: Float,
        /** Varianza del Laplaciano: mide enfoque. Valores bajos = movida o desenfocada. */
        val nitidez: Float,
        /** Giro izquierda/derecha en grados. Positivo = gira hacia la derecha del cuadro. */
        val yaw: Float,
        /** Cabeceo arriba/abajo en grados. Positivo = mira hacia ARRIBA. */
        val pitch: Float,
        /** Inclinacion de la cabeza en grados, del angulo de la linea entre ojos. */
        val roll: Float,
        /**
         * Marco del rostro NORMALIZADO (0..1) respecto al cuadro analizado, para
         * poder dibujarlo encima del preview sin arrastrar la resolucion del
         * analisis hasta la UI. Se guarda tambien la proporcion del cuadro,
         * porque el preview recorta al centro (FILL_CENTER) y sin ella el marco
         * quedaria corrido cuando las dos proporciones no coinciden.
         */
        val marco: Marco
    )

    /**
     * Pose de la cabeza en grados.
     *
     * Convencion: [yaw] positivo = la cara gira hacia la DERECHA del cuadro,
     * [pitch] positivo = mira hacia ARRIBA. Si en el dispositivo sale al reves,
     * se voltea con las constantes [YAW_DERECHA] / [PITCH_ARRIBA] y no hay que
     * tocar nada mas -- la pantalla de enrolamiento muestra los dos numeros en
     * vivo justamente para poder comprobarlo en cinco segundos.
     */
    data class Pose(val yaw: Float, val pitch: Float, val roll: Float)

    /** Rectangulo normalizado 0..1, mas la proporcion del cuadro del que salio. */
    data class Marco(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val aspectoCuadro: Float
    ) {
        companion object {
            val VACIO = Marco(0f, 0f, 0f, 0f, 1f)
        }
    }

    sealed interface Resultado {
        data class Ok(val captura: Captura) : Resultado
        data object SinRostro : Resultado
        data object VariasCaras : Resultado
        data class Error(val causa: String) : Resultado
    }

    /**
     * Detecta exactamente un rostro en [bitmap] y produce su vector.
     *
     * Si aparece una segunda persona se rechaza el cuadro completo para no
     * alimentar el enrolamiento o el login con una identidad ambigua.
     */
    @Synchronized
    fun procesar(bitmap: Bitmap): Resultado {
        var rgba: Mat? = null
        var bgr: Mat? = null
        var caras: Mat? = null
        var alineada: Mat? = null
        var rasgos: Mat? = null

        return try {
            rgba = Mat()
            Utils.bitmapToMat(bitmap, rgba)
            bgr = Mat()
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)

            val inicioDeteccion = SystemClock.elapsedRealtime()
            if (detectorWidth != bgr.cols() || detectorHeight != bgr.rows()) {
                detector.setInputSize(Size(bgr.cols().toDouble(), bgr.rows().toDouble()))
                detectorWidth = bgr.cols()
                detectorHeight = bgr.rows()
            }
            caras = Mat()
            detector.detect(bgr, caras)
            val msDeteccion = (SystemClock.elapsedRealtime() - inicioDeteccion).toInt()

            if (caras.rows() == 0) return Resultado.SinRostro
            if (caras.rows() > 1) return Resultado.VariasCaras

            val indiceMayor = (0 until caras.rows()).maxByOrNull { fila ->
                caras.get(fila, 2)[0] * caras.get(fila, 3)[0]
            } ?: return Resultado.SinRostro

            // Cada fila trae 15 valores: x, y, w, h, luego 5 pares (x,y) de
            // puntos de referencia, y al final el score de deteccion.
            val fila = FloatArray(15)
            for (i in 0 until 15) {
                fila[i] = caras.get(indiceMayor, i)[0].toFloat()
            }

            rasgos = Mat(1, 15, CvType.CV_32F)
            rasgos.put(0, 0, fila)

            val inicioEmbedding = SystemClock.elapsedRealtime()
            alineada = Mat()
            recognizer.alignCrop(bgr, rasgos, alineada)

            val vectorMat = Mat()
            recognizer.feature(alineada, vectorMat)
            val msEmbedding = (SystemClock.elapsedRealtime() - inicioEmbedding).toInt()

            val vector = FloatArray(vectorMat.cols())
            vectorMat.get(0, 0, vector)
            vectorMat.release()

            val calidad = medirCalidad(alineada, fila, bgr.cols(), bgr.rows())
            CameraTrace.d(
                "sface frame=${bitmap.width}x${bitmap.height} detectorMs=$msDeteccion " +
                "embeddingMs=$msEmbedding facePx=${calidad.rostroPx} " +
                    "luma=${"%.1f".format(java.util.Locale.US, calidad.luma)} " +
                    "sharpness=${"%.1f".format(java.util.Locale.US, calidad.nitidez)} " +
                    "yaw=${"%.1f".format(java.util.Locale.US, calidad.yaw)} " +
                    "pitch=${"%.1f".format(java.util.Locale.US, calidad.pitch)} " +
                    "roll=${"%.1f".format(java.util.Locale.US, calidad.roll)}"
            )
            Resultado.Ok(Captura(vector, calidad, msDeteccion, msEmbedding))
        } catch (e: Exception) {
            Log.e(TAG, "Fallo al procesar el rostro", e)
            Resultado.Error(e.message ?: e::class.java.simpleName)
        } finally {
            listOfNotNull(rgba, bgr, caras, alineada, rasgos).forEach { it.release() }
        }
    }

    /**
     * Las metricas se calculan sobre el recorte YA ALINEADO (112x112), no sobre
     * el cuadro completo: lo que importa es como se ve la cara, no si la nave
     * de atras esta iluminada.
     */
    private fun medirCalidad(alineada: Mat, fila: FloatArray, anchoCuadro: Int, altoCuadro: Int): Calidad {
        val gris = Mat()
        Imgproc.cvtColor(alineada, gris, Imgproc.COLOR_BGR2GRAY)

        val mediaLuma = MatOfDouble()
        val desviacion = MatOfDouble()
        Core.meanStdDev(gris, mediaLuma, desviacion)
        val luma = mediaLuma.toArray().firstOrNull()?.toFloat() ?: 0f

        // Varianza del Laplaciano: el clasico para medir enfoque. Una imagen
        // nitida tiene bordes fuertes (mucha varianza); una movida los pierde.
        val laplaciano = Mat()
        Imgproc.Laplacian(gris, laplaciano, CvType.CV_64F)
        val mediaLap = MatOfDouble()
        val desvLap = MatOfDouble()
        Core.meanStdDev(laplaciano, mediaLap, desvLap)
        val sigma = desvLap.toArray().firstOrNull() ?: 0.0
        val nitidez = (sigma * sigma).toFloat()

        listOf(gris, laplaciano, mediaLuma, desviacion, mediaLap, desvLap).forEach { it.release() }

        val anchoRostro = fila[2].toInt()
        val pose = estimarPose(fila, anchoCuadro, altoCuadro)

        val marco = if (anchoCuadro > 0 && altoCuadro > 0) {
            Marco(
                left = (fila[0] / anchoCuadro).coerceIn(0f, 1f),
                top = (fila[1] / altoCuadro).coerceIn(0f, 1f),
                right = ((fila[0] + fila[2]) / anchoCuadro).coerceIn(0f, 1f),
                bottom = ((fila[1] + fila[3]) / altoCuadro).coerceIn(0f, 1f),
                aspectoCuadro = anchoCuadro.toFloat() / altoCuadro.toFloat()
            )
        } else {
            Marco.VACIO
        }

        return Calidad(
            scoreDeteccion = fila[14],
            rostroPx = anchoRostro,
            luma = luma,
            nitidez = nitidez,
            yaw = pose.yaw,
            pitch = pose.pitch,
            roll = pose.roll,
            marco = marco
        )
    }

    /**
     * Estima la pose de la cabeza resolviendo el problema de perspectiva
     * (solvePnP) entre los 5 puntos que da YuNet y un modelo 3D canonico de
     * esos mismos 5 puntos.
     *
     * Se hace asi y no con la heuristica de "que tan corrida esta la nariz"
     * porque esa no da PITCH, y el anillo del enrolamiento necesita las dos
     * dimensiones para saber hacia donde esta mirando la persona.
     *
     * La camara se aproxima sin calibrar: distancia focal = ancho del cuadro y
     * centro optico al centro de la imagen. Para repartir tomas en 24 sectores
     * esa aproximacion sobra; no sirve para medir angulos de precision.
     *
     * Si solvePnP falla o devuelve algo absurdo, cae a [poseAproximada], que es
     * mas burda pero no falla nunca.
     */
    private fun estimarPose(fila: FloatArray, anchoCuadro: Int, altoCuadro: Int): Pose {
        // El roll no necesita solvePnP: es el angulo de la linea entre los ojos.
        val roll = Math.toDegrees(
            atan2((fila[7] - fila[5]).toDouble(), (fila[6] - fila[4]).toDouble())
        ).toFloat()

        if (anchoCuadro <= 0 || altoCuadro <= 0) return poseAproximada(fila, roll)

        var puntos2d: MatOfPoint2f? = null
        var camara: Mat? = null
        var distorsion: MatOfDouble? = null
        var rvec: Mat? = null
        var tvec: Mat? = null
        var rotacion: Mat? = null

        return try {
            // Mismo orden que MODELO_3D: nariz, ojo derecho, ojo izquierdo,
            // comisura derecha, comisura izquierda.
            puntos2d = MatOfPoint2f(
                Point(fila[8].toDouble(), fila[9].toDouble()),
                Point(fila[4].toDouble(), fila[5].toDouble()),
                Point(fila[6].toDouble(), fila[7].toDouble()),
                Point(fila[10].toDouble(), fila[11].toDouble()),
                Point(fila[12].toDouble(), fila[13].toDouble())
            )

            val focal = anchoCuadro.toDouble()
            camara = Mat.zeros(3, 3, CvType.CV_64F)
            camara.put(0, 0, focal)
            camara.put(0, 2, anchoCuadro / 2.0)
            camara.put(1, 1, focal)
            camara.put(1, 2, altoCuadro / 2.0)
            camara.put(2, 2, 1.0)
            distorsion = MatOfDouble(0.0, 0.0, 0.0, 0.0)

            rvec = Mat()
            tvec = Mat()
            val resuelto = Calib3d.solvePnP(
                MODELO_3D, puntos2d, camara, distorsion, rvec, tvec,
                false, Calib3d.SOLVEPNP_EPNP
            )
            if (!resuelto) return poseAproximada(fila, roll)

            rotacion = Mat()
            Calib3d.Rodrigues(rvec, rotacion)

            // Angulos de Euler de la matriz de rotacion. El modelo 3D esta
            // escrito en la convencion de OpenCV (+X derecha, +Y abajo, +Z
            // alejandose), asi que una cara de frente sale cerca de cero y no
            // hay que andar desenrollando valores pegados a 180.
            val r00 = rotacion.get(0, 0)[0]
            val r10 = rotacion.get(1, 0)[0]
            val r20 = rotacion.get(2, 0)[0]
            val r21 = rotacion.get(2, 1)[0]
            val r22 = rotacion.get(2, 2)[0]

            val sy = sqrt(r00 * r00 + r10 * r10)
            if (sy < 1e-6) return poseAproximada(fila, roll)

            val yaw = (YAW_DERECHA * Math.toDegrees(atan2(-r20, sy))).toFloat()
            val pitch = (PITCH_ARRIBA * Math.toDegrees(atan2(r21, r22))).toFloat()

            // Mas alla de esto la solucion deja de ser confiable, y vale mas una
            // aproximacion honesta que un numero con cara de exacto.
            if (abs(yaw) > 60f || abs(pitch) > 60f) return poseAproximada(fila, roll)

            Pose(yaw, pitch, roll)
        } catch (e: Exception) {
            Log.w(TAG, "solvePnP fallo, se usa la aproximacion: " + (e.message ?: ""))
            poseAproximada(fila, roll)
        } finally {
            listOfNotNull(puntos2d, camara, distorsion, rvec, tvec, rotacion)
                .forEach { it.release() }
        }
    }

    /**
     * Plan B sin solvePnP, a puras proporciones entre los 5 puntos.
     *
     *  - Yaw:   de frente la nariz cae a media distancia entre los ojos; al
     *           girar la cabeza se corre hacia uno de los dos.
     *  - Pitch: al mirar hacia arriba la punta de la nariz se proyecta mas
     *           abajo, acercandose a la linea de la boca; al mirar hacia abajo
     *           se sube hacia la linea de los ojos.
     *
     * Los factores a grados son burdos a proposito: sirven para decidir a que
     * sector del anillo pertenece la toma, no para medir.
     */
    private fun poseAproximada(fila: FloatArray, roll: Float): Pose {
        val ojoDerX = fila[4]
        val ojoDerY = fila[5]
        val ojoIzqX = fila[6]
        val ojoIzqY = fila[7]
        val narizX = fila[8]
        val narizY = fila[9]
        val bocaY = (fila[11] + fila[13]) / 2f

        val centroOjosX = (ojoDerX + ojoIzqX) / 2f
        val centroOjosY = (ojoDerY + ojoIzqY) / 2f

        val separacion = abs(ojoIzqX - ojoDerX).takeIf { it > 1f } ?: 1f
        val yaw = ((narizX - centroOjosX) / separacion * 90f).coerceIn(-60f, 60f)

        val alturaCara = abs(bocaY - centroOjosY).takeIf { it > 1f } ?: 1f
        val proporcion = (narizY - centroOjosY) / alturaCara
        val pitch = ((proporcion - PROPORCION_NARIZ_FRONTAL) * 120f).coerceIn(-60f, 60f)

        return Pose(yaw, pitch, roll)
    }

    fun cerrar() {
        // FaceDetectorYN/FaceRecognizerSF liberan su nativo en el finalizador de
        // OpenCV; no exponen close(). Se deja el gancho por claridad del ciclo
        // de vida en el ViewModel.
    }

    companion object {
        private const val TAG = "PMM-FACE"

        /** Umbral coseno recomendado por los autores de SFace. Punto de partida, no verdad. */
        const val UMBRAL_SUGERIDO = 0.363f

        /**
         * Signos de la pose. Se dejan como constantes con nombre porque son lo
         * unico que hay que voltear si en el dispositivo el anillo se llena al
         * reves. Ver [Pose].
         */
        private const val YAW_DERECHA = 1.0
        private const val PITCH_ARRIBA = -1.0

        /**
         * Donde cae la punta de la nariz entre la linea de los ojos y la de la
         * boca cuando la cara esta de frente. Solo lo usa [poseAproximada].
         */
        private const val PROPORCION_NARIZ_FRONTAL = 0.45f

        /**
         * Modelo 3D canonico de los 5 puntos de YuNet, en milimetros y en la
         * convencion de OpenCV: +X a la derecha del cuadro, +Y hacia ABAJO,
         * +Z alejandose de la camara. La punta de la nariz es el origen y es
         * lo que mas sobresale, por eso los demas puntos tienen Z positiva.
         *
         * Escribirlo en la convencion de OpenCV (y no con +Y hacia arriba, que
         * es lo intuitivo) hace que una cara de frente resuelva cerca de cero
         * en vez de cerca de 180 grados, y ahorra tener que desenrollar los
         * angulos despues.
         */
        private val MODELO_3D: MatOfPoint3f by lazy {
            MatOfPoint3f(
                Point3(0.0, 0.0, 0.0),        // punta de la nariz
                Point3(-35.0, -32.0, 28.0),   // ojo derecho del sujeto
                Point3(35.0, -32.0, 28.0),    // ojo izquierdo del sujeto
                Point3(-28.0, 28.0, 25.0),    // comisura derecha
                Point3(28.0, 28.0, 25.0)      // comisura izquierda
            )
        }

        const val MODELO = "sface-2021dec"
        const val DIMS = 128

        private const val ASSET_DETECTOR = "models/face/yunet.onnx"
        private const val ASSET_RECONOCEDOR = "models/face/sface.onnx"

        /**
         * Los .onnx viven en main/assets, asi que en condiciones normales esto
         * siempre es true. Se conserva la verificacion porque un APK recortado
         * (o un split mal armado) los dejaria fuera, y es mejor apagar el acceso
         * facial que reventar al abrir la pantalla.
         */
        fun isAvailable(context: Context): Boolean = runCatching {
            context.assets.list("models/face")?.toSet()?.containsAll(
                setOf("yunet.onnx", "sface.onnx")
            ) == true
        }.getOrDefault(false)

        /**
         * OpenCV lee los modelos por ruta de archivo, no desde un stream, asi que
         * hay que volcarlos a almacenamiento interno la primera vez.
         */
        fun crear(context: Context): FaceEngine? {
            if (!OpenCVUtils.init(context)) {
                Log.e(TAG, "OpenCV no pudo inicializarse")
                return null
            }

            return try {
                val detectorPath = copiarAsset(context, ASSET_DETECTOR, "yunet.onnx")
                val reconocedorPath = copiarAsset(context, ASSET_RECONOCEDOR, "sface.onnx")

                val detector = FaceDetectorYN.create(
                    detectorPath,
                    "",
                    Size(320.0, 320.0),
                    0.6f,   // score: por debajo de esto ni lo reporta
                    0.3f,   // NMS
                    5000    // top-k antes de NMS
                )
                val recognizer = FaceRecognizerSF.create(reconocedorPath, "")
                FaceEngine(detector, recognizer)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudieron cargar los modelos faciales", e)
                null
            }
        }

        private fun copiarAsset(context: Context, assetPath: String, nombre: String): String {
            val destino = File(context.filesDir, "face_models/$nombre")
            if (destino.exists() && destino.length() > 0) return destino.absolutePath

            destino.parentFile?.mkdirs()
            context.assets.open(assetPath).use { entrada ->
                destino.outputStream().use { salida -> entrada.copyTo(salida) }
            }
            return destino.absolutePath
        }

        /**
         * Similitud coseno entre dos vectores: 1.0 identicos, 0.0 sin relacion.
         * Es la misma cuenta que hace FaceRecognizerSF.match con FR_COSINE, pero
         * hecha aqui para poder comparar contra el banco descargado del servidor
         * sin tener que reconstruir un Mat por cada plantilla.
         */
        fun similitudCoseno(a: FloatArray, b: FloatArray): Float {
            if (a.size != b.size || a.isEmpty()) return 0f
            var producto = 0.0
            var normaA = 0.0
            var normaB = 0.0
            for (i in a.indices) {
                producto += a[i] * b[i]
                normaA += a[i] * a[i]
                normaB += b[i] * b[i]
            }
            val divisor = sqrt(normaA) * sqrt(normaB)
            return if (divisor <= 0.0) 0f else (producto / divisor).toFloat()
        }
    }
}
