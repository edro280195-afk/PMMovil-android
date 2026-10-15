package com.example.pmmovil.ui.revision

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.util.Log
import android.util.Size as AndroidSize
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pmmovil.data.ocr.CajaTexto
import com.example.pmmovil.data.ocr.GeometriaEnVivo
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LiveTextOcrAnalyzer
import com.example.pmmovil.ui.camera.CameraErrorView
import com.example.pmmovil.ui.camera.CameraOwner
import com.example.pmmovil.ui.camera.CameraWarmupCurtain
import com.example.pmmovil.ui.camera.CameraWarmupMonitor
import com.example.pmmovil.ui.camera.rememberCameraRetry
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Lectura de etiqueta **guiada en vivo**: la cámara marca en pantalla el texto
 * que va encontrando, y la foto sale cuando ya reconoció algo.
 *
 * ## Por qué existe esta pantalla
 * La primera versión usaba `LocalEvidenceCameraScreen`, que es la cámara de
 * evidencias: una foto a ciegas. El revisador no sabía si había encuadrado el
 * lote o el nombre del producto hasta **después** de disparar, y cuando no salía
 * nada no tenía forma de saber por qué. Tres causas medidas, y ninguna se
 * arregla con mejor OCR:
 *
 *  1. **Sin retroalimentación.** No se veía si la cámara estaba leyendo algo.
 *  2. **Zoom clavado en 1x.** En el Pixel 9 Pro XL el lente de 6.9 mm no enfoca
 *     más cerca de **10.5 cm** (`minimumFocusDistance = 9.52` dioptrías, medido
 *     con `dumpsys media.camera`). El lente que sí hace macro -- 2 cm -- es el
 *     gran angular, y se alcanza bajando el zoom a **0.5x**
 *     (`zoomRatioRange = [0.5078 .. 30.0]`). Clavar 1x dejaba el macro
 *     inalcanzable justo para el caso que importa: un lote impreso chiquito.
 *  3. **El código impreso se ignoraba.** En 10 de 13 revisiones de la evidencia
 *     real había un código de barras legible en la propia foto. Decodificarlo es
 *     exacto, instantáneo y offline: no hace falta OCR.
 *
 * ## La arquitectura es la de docs/ia-revision-fisica.md §8
 * **Video para guiar, foto para leer.** El video va a 4 fps y downscaled: sirve
 * para encontrar la etiqueta, decodificar códigos y avisar que ya hay algo, pero
 * no para leer el lote impreso. Esa lectura la hace PaddleOCR sobre la foto a
 * resolución completa. El revisador no distingue las dos capas: apunta, ve
 * marcas, y la foto sale sola.
 *
 * Las marcas se dibujan con la geometría que entrega [LiveTextOcrAnalyzer], y el
 * resaltado verde lo decide **el mismo** [LabelFieldExtractor] que luego corre
 * sobre la foto. Así lo que se pinta en vivo no es un adorno: es una vista
 * previa real de lo que se va a extraer.
 */
@Composable
fun LabelCaptureLiveScreen(
    titulo: String,
    subtitulo: String,
    onFotoTomada: (path: String, codigosEnVivo: List<String>) -> Unit,
    onCerrar: () -> Unit
) {
    // Sin esto el botón atrás sale de todo el módulo de revisión y se pierde la
    // partida en edición (misma lección que en la cámara de Entrada Caseta).
    BackHandler(onBack = onCerrar)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permisoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido -> tienePermiso = concedido }

    LaunchedEffect(Unit) {
        if (!tienePermiso) permisoLauncher.launch(Manifest.permission.CAMERA)
    }

    // Creada en la composición, no en el factory del AndroidView: si el efecto
    // de enlace depende de un estado que el factory rellena después, se producen
    // dos enlaces por apertura (ver SmartBarcodeCameraView).
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val warmup = remember { CameraWarmupMonitor("etiqueta-vivo") }
    val warmupListo by warmup.isReady.collectAsState()
    val reintento = rememberCameraRetry(warmup)

    var geometria by remember { mutableStateOf(GeometriaEnVivo.VACIA) }
    var camposEnVivo by remember { mutableStateOf<List<LabelFieldExtractor.CampoLeido>>(emptyList()) }
    var codigosVistos by remember { mutableStateOf<List<String>>(emptyList()) }
    var capturando by remember { mutableStateOf(false) }
    var listoParaDisparar by remember { mutableStateOf(false) }
    var autoDisparo by remember { mutableStateOf(true) }
    var tamanoVista by remember { mutableStateOf(IntSize.Zero) }

    var camara by remember { mutableStateOf<Camera?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var zoomActual by remember { mutableStateOf(1f) }
    var zoomMin by remember { mutableStateOf(1f) }
    var zoomMax by remember { mutableStateOf(1f) }
    var lamparaEncendida by remember { mutableStateOf(false) }

    val capturandoState = rememberUpdatedState(capturando)
    val executor = remember { Executors.newSingleThreadExecutor() }
    val estabilidad = remember { EstabilidadDeCampos() }

    LaunchedEffect(warmupListo, reintento.attempt, reintento.failed) {
        while (!warmupListo && !reintento.failed) {
            delay(WARMUP_TICK_MS)
            warmup.tick()
        }
    }

    val analizador = remember {
        LiveTextOcrAnalyzer(
            // Durante la captura estática hay que soltar los cuadros de
            // inmediato: si se encolan en ML Kit mientras la foto se procesa, el
            // stream de ImageAnalysis se queda sin buffers y el HAL desploma la
            // preview. Medido en la Armor Pad Pro.
            shouldSkipFrame = { capturandoState.value },
            onFrameSeen = { warmup.onFrameLuminance(LUMINANCIA_NEUTRA) },
            onGeometria = { nueva -> if (!capturandoState.value) geometria = nueva }
        ) { resultado ->
            if (capturandoState.value) return@LiveTextOcrAnalyzer

            // Los códigos se acumulan: el decodificador los ve un cuadro y al
            // siguiente ya no, y perder un código exacto por un parpadeo sería
            // tirar el dato más confiable que trae la etiqueta.
            if (resultado.barcodes.isNotEmpty()) {
                codigosVistos = (codigosVistos + resultado.barcodes).distinct()
            }
            val campos = LabelFieldExtractor.extraer(resultado.rawText, codigosVistos)
            camposEnVivo = campos
            listoParaDisparar = estabilidad.ofrecer(campos)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analizador.close()
            executor.shutdown()
        }
    }

    if (!tienePermiso) {
        PermisoCamaraFaltante(
            onPedir = { permisoLauncher.launch(Manifest.permission.CAMERA) },
            onCerrar = onCerrar
        )
        return
    }

    LaunchedEffect(reintento.attempt) {
        try {
            // Una sola relación de aspecto para las tres rutas -- preview,
            // análisis y foto -- por dos razones: las marcas caen en su lugar
            // sólo si el cuadro analizado y el mostrado recortan igual, y lo que
            // el revisador encuadra tiene que ser lo que sale en la foto. 4:3 es
            // además el formato nativo del sensor, así que no se tira alto.
            val aspecto = AspectRatioStrategy(
                AspectRatio.RATIO_4_3,
                AspectRatioStrategy.FALLBACK_RULE_AUTO
            )
            val preview = Preview.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setAspectRatioStrategy(aspecto).build()
                )
                .also { warmup.attachTo(it) }
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val analisis = ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setAspectRatioStrategy(aspecto)
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                RESOLUCION_ANALISIS,
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, analizador) }

            // La etiqueta está quieta y el dato es texto chico: calidad por
            // encima de latencia, igual que en la cámara de evidencias.
            val captura = ImageCapture.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setAspectRatioStrategy(aspecto).build()
                )
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(CALIDAD_JPEG_ETIQUETA)
                .build()

            val enlazada = CameraOwner.bind(
                tag = TAG_CAMARA_ETIQUETA,
                context = context,
                lifecycleOwner = lifecycleOwner,
                selector = CameraSelector.DEFAULT_BACK_CAMERA,
                useCases = listOf(preview, analisis, captura),
                onBeforeBind = { warmup.onBound() },
                onError = { error ->
                    Log.e(TAG_CAMARA_ETIQUETA, "Error al iniciar la cámara", error)
                    reintento.forceFail()
                }
            )
            if (enlazada != null) {
                imageCapture = captura
                camara = enlazada
                val estadoZoom = enlazada.cameraInfo.zoomState.value
                zoomMin = estadoZoom?.minZoomRatio ?: 1f
                zoomMax = estadoZoom?.maxZoomRatio ?: 1f
                zoomActual = estadoZoom?.zoomRatio ?: 1f
                awaitCancellation()
            }
        } finally {
            withContext(NonCancellable) { CameraOwner.release(TAG_CAMARA_ETIQUETA) }
            imageCapture = null
            camara = null
        }
    }

    val disparar: () -> Unit = disparar@{
        val captura = imageCapture ?: return@disparar
        if (capturando) return@disparar
        capturando = true
        val codigos = codigosVistos
        tomarFotoEtiqueta(
            context = context,
            captura = captura,
            onGuardada = { archivo ->
                capturando = false
                onFotoTomada(archivo.absolutePath, codigos)
            },
            onError = { capturando = false }
        )
    }

    // Disparo automático: el revisador apunta y la foto sale cuando la lectura
    // ya se repitió. Si no se diera, siempre queda el botón.
    LaunchedEffect(listoParaDisparar, autoDisparo, warmupListo) {
        if (!listoParaDisparar || !autoDisparo || !warmupListo || capturando) return@LaunchedEffect
        delay(ESPERA_ANTES_DEL_DISPARO_MS)
        if (listoParaDisparar && autoDisparo && !capturando) disparar()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { tamanoVista = it }
        ) {
            val mapeo = mapeoDe(geometria, tamanoVista)
            val valoresLeidos = remember(camposEnVivo) {
                camposEnVivo.map { normalizarParaComparar(it.valor) }.filter { it.length >= 3 }
            }
            if (mapeo != null) {
                MarcasDeTexto(cajas = geometria.cajas, mapeo = mapeo, valoresLeidos = valoresLeidos)
                EtiquetasDeCampo(cajas = geometria.cajas, mapeo = mapeo, campos = camposEnVivo)
            }
        }

        CameraWarmupCurtain(
            visible = !warmupListo && !reintento.failed,
            modifier = Modifier.fillMaxSize()
        )

        if (reintento.failed) {
            CameraErrorView(
                onRetry = reintento.retry,
                modifier = Modifier.fillMaxSize(),
                title = "La cámara no respondió",
                detail = "Se intentó abrirla varias veces sin recibir imagen. " +
                    "Puedes capturar el dato a mano."
            )
            return@Box
        }

        EncabezadoCaptura(
            titulo = titulo,
            subtitulo = subtitulo,
            onCerrar = onCerrar,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        PanelInferior(
            campos = camposEnVivo,
            codigos = codigosVistos,
            pista = pistaDeEncuadre(geometria, camposEnVivo, codigosVistos),
            listo = listoParaDisparar,
            capturando = capturando,
            autoDisparo = autoDisparo,
            onAlternarAuto = { autoDisparo = !autoDisparo },
            zoomActual = zoomActual,
            zoomMin = zoomMin,
            zoomMax = zoomMax,
            onZoom = { objetivo ->
                camara?.cameraControl?.setZoomRatio(objetivo)
                zoomActual = objetivo
                // Cambiar de lente reencuadra: lo leído ya no corresponde.
                estabilidad.reiniciar()
                listoParaDisparar = false
            },
            lamparaEncendida = lamparaEncendida,
            onAlternarLampara = {
                val nuevo = !lamparaEncendida
                camara?.cameraControl?.enableTorch(nuevo)
                lamparaEncendida = nuevo
            },
            onDisparar = disparar,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Las marcas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarcasDeTexto(
    cajas: List<CajaTexto>,
    mapeo: Mapeo,
    valoresLeidos: List<String>
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        cajas.forEach { caja ->
            val reconocida = caja.esCodigo || contieneAlgunValor(caja.texto, valoresLeidos)
            val color = when {
                caja.esCodigo -> COLOR_CODIGO
                reconocida -> COLOR_RECONOCIDO
                else -> COLOR_TEXTO_SUELTO
            }
            val izquierda = mapeo.x(caja.izquierda)
            val arriba = mapeo.y(caja.arriba)
            val ancho = caja.ancho * mapeo.escala
            val alto = caja.alto * mapeo.escala
            if (ancho <= 1f || alto <= 1f) return@forEach

            if (reconocida) {
                drawRoundRect(
                    color = color.copy(alpha = 0.18f),
                    topLeft = Offset(izquierda, arriba),
                    size = Size(ancho, alto),
                    cornerRadius = CornerRadius(RADIO_MARCA, RADIO_MARCA)
                )
            }
            drawRoundRect(
                color = color,
                topLeft = Offset(izquierda, arriba),
                size = Size(ancho, alto),
                cornerRadius = CornerRadius(RADIO_MARCA, RADIO_MARCA),
                style = Stroke(width = if (reconocida) 3.5f else 1.5f)
            )
        }
    }
}

/**
 * El nombre del campo encima de la caja que lo produjo. Va como composable y no
 * como texto dibujado en el Canvas para que herede tipografía del sistema y para
 * que no se deforme con la escala del mapeo.
 */
@Composable
private fun EtiquetasDeCampo(
    cajas: List<CajaTexto>,
    mapeo: Mapeo,
    campos: List<LabelFieldExtractor.CampoLeido>
) {
    campos.forEach { campo ->
        val valor = normalizarParaComparar(campo.valor)
        if (valor.length < 3) return@forEach
        val caja = cajas.firstOrNull { !it.esCodigo && contieneAlgunValor(it.texto, listOf(valor)) }
            ?: return@forEach

        val x = mapeo.x(caja.izquierda).roundToInt()
        val y = (mapeo.y(caja.arriba) - ALTURA_ETIQUETA_PX).roundToInt()
        Text(
            text = nombreCortoDeCampo(campo.campo),
            color = Color.Black,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
            modifier = Modifier
                .offset { IntOffset(x, y) }
                .clip(RoundedCornerShape(5.dp))
                .background(COLOR_RECONOCIDO)
                .padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mapeo de coordenadas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Lleva coordenadas de la imagen analizada a píxeles de la vista.
 *
 * `PreviewView` usa `FILL_CENTER`: escala al **mayor** de los dos factores y
 * recorta lo que sobra, centrado. Si aquí se usara el menor (`FIT_CENTER`) las
 * marcas quedarían encogidas y corridas hacia el centro. Es el error clásico y
 * se nota poco: lo suficiente para no verlo hasta que alguien confía en una
 * marca que está señalando el renglón de al lado.
 */
private data class Mapeo(val escala: Float, val dx: Float, val dy: Float) {
    fun x(valor: Float): Float = valor * escala + dx
    fun y(valor: Float): Float = valor * escala + dy
}

private fun mapeoDe(geometria: GeometriaEnVivo, vista: IntSize): Mapeo? {
    if (!geometria.valida || vista.width == 0 || vista.height == 0) return null
    val escala = max(
        vista.width.toFloat() / geometria.anchoImagen,
        vista.height.toFloat() / geometria.altoImagen
    )
    return Mapeo(
        escala = escala,
        dx = (vista.width - geometria.anchoImagen * escala) / 2f,
        dy = (vista.height - geometria.altoImagen * escala) / 2f
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Estabilidad y pistas
// ─────────────────────────────────────────────────────────────────────────────

/**
 * No dispara con la primera lectura. El OCR en vivo titubea -- un cuadro lee
 * `2504298-135` y el siguiente `25O4298-135` -- así que se exige que la lectura
 * completa se repita [vecesRequeridas] veces seguidas antes de dar por buena la
 * foto. Es el mismo criterio del candado de placas en Entrada Caseta.
 */
private class EstabilidadDeCampos(private val vecesRequeridas: Int = 3) {
    private var ultimaFirma: String? = null
    private var repeticiones = 0

    fun ofrecer(campos: List<LabelFieldExtractor.CampoLeido>): Boolean {
        val firma = campos
            .sortedBy { it.campo.name }
            .joinToString("|") { "${it.campo.name}=${it.valor}" }
        if (firma.isBlank()) {
            reiniciar()
            return false
        }
        if (firma == ultimaFirma) {
            repeticiones++
        } else {
            ultimaFirma = firma
            repeticiones = 1
        }
        return repeticiones >= vecesRequeridas
    }

    fun reiniciar() {
        ultimaFirma = null
        repeticiones = 0
    }
}

/**
 * Qué decirle al revisador según lo que la cámara está viendo de verdad. Nada de
 * mensajes genéricos: cada caso lleva a una acción distinta.
 */
private fun pistaDeEncuadre(
    geometria: GeometriaEnVivo,
    campos: List<LabelFieldExtractor.CampoLeido>,
    codigos: List<String>
): String {
    if (codigos.isNotEmpty() && campos.isNotEmpty()) {
        return "Código decodificado. El dato sale del código: es exacto."
    }
    if (campos.isNotEmpty()) return "Ya reconocí el dato. Mantén el pulso."
    if (codigos.isNotEmpty()) return "Veo un código pero no trae los campos. Encuadra el texto."

    val cajas = geometria.cajas
    if (cajas.isEmpty()) {
        return "No veo texto. Acércate a la etiqueta o prende la lámpara."
    }
    if (!geometria.valida) return "Encuadrando…"

    // Si el renglón más alto no llega a este porcentaje del cuadro, el texto es
    // demasiado chico para que el OCR lo resuelva, y acercarse es la única
    // solución real: subir la resolución del análisis no lo arregla.
    val altoRelativo = cajas.maxOf { it.alto } / geometria.altoImagen
    if (altoRelativo < ALTO_MINIMO_RELATIVO) {
        return "Veo texto pero muy chico. Acércate, o usa Macro si ya estás pegado."
    }
    return "Buscando las anclas (LOT, PART, Qty, COO, UN)…"
}

private fun normalizarParaComparar(valor: String): String =
    valor.uppercase().filter { it.isLetterOrDigit() }

private fun contieneAlgunValor(texto: String, valores: List<String>): Boolean {
    if (valores.isEmpty()) return false
    val normalizado = normalizarParaComparar(texto)
    if (normalizado.isEmpty()) return false
    return valores.any { it.isNotEmpty() && normalizado.contains(it) }
}

private fun nombreCortoDeCampo(campo: LabelFieldExtractor.Campo): String = when (campo) {
    LabelFieldExtractor.Campo.LOTE -> "LOTE"
    LabelFieldExtractor.Campo.NUMERO_PARTE -> "PARTE"
    LabelFieldExtractor.Campo.NUMERO_SERIE -> "SERIE"
    LabelFieldExtractor.Campo.CANTIDAD -> "CANTIDAD"
    LabelFieldExtractor.Campo.PIEZAS_POR_EMPAQUE -> "PZS/EMPAQUE"
    LabelFieldExtractor.Campo.PAIS_ORIGEN -> "PAÍS"
    LabelFieldExtractor.Campo.CADUCIDAD -> "CADUCIDAD"
    LabelFieldExtractor.Campo.UN_HAZMAT -> "UN"
    LabelFieldExtractor.Campo.CLASE_HAZMAT -> "CLASE"
    LabelFieldExtractor.Campo.GRUPO_EMBALAJE -> "GRUPO"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Interfaz
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EncabezadoCaptura(
    titulo: String,
    subtitulo: String,
    onCerrar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f))
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(titulo, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitulo, color = Color.White.copy(alpha = 0.7f), fontSize = 11.5.sp)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = onCerrar),
            contentAlignment = Alignment.Center
        ) {
            Text("✕", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PanelInferior(
    campos: List<LabelFieldExtractor.CampoLeido>,
    codigos: List<String>,
    pista: String,
    listo: Boolean,
    capturando: Boolean,
    autoDisparo: Boolean,
    onAlternarAuto: () -> Unit,
    zoomActual: Float,
    zoomMin: Float,
    zoomMax: Float,
    onZoom: (Float) -> Unit,
    lamparaEncendida: Boolean,
    onAlternarLampara: () -> Unit,
    onDisparar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.68f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (campos.isNotEmpty()) {
            campos.take(MAX_CAMPOS_VISIBLES).forEach { campo ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        nombreCortoDeCampo(campo.campo),
                        color = COLOR_RECONOCIDO,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp,
                        modifier = Modifier.width(86.dp)
                    )
                    Text(
                        campo.valor,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    if (campo.origen == LabelFieldExtractor.Origen.GS1) {
                        Text("código", color = COLOR_CODIGO, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Text(
            pista,
            color = if (listo) COLOR_RECONOCIDO else Color.White.copy(alpha = 0.78f),
            fontSize = 12.5.sp,
            fontWeight = if (listo) FontWeight.SemiBold else FontWeight.Normal
        )

        if (codigos.isNotEmpty() && campos.isEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                "Código: ${codigos.first().take(44)}",
                color = COLOR_CODIGO,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presetsDeZoom(zoomMin, zoomMax).forEach { (etiqueta, valor) ->
                ChipControl(
                    texto = etiqueta,
                    activo = abs(zoomActual - valor) < TOLERANCIA_ZOOM,
                    onClick = { onZoom(valor) }
                )
            }
            Spacer(Modifier.weight(1f))
            ChipControl(texto = "Lámpara", activo = lamparaEncendida, onClick = onAlternarLampara)
            ChipControl(texto = "Auto", activo = autoDisparo, onClick = onAlternarAuto)
        }

        Spacer(Modifier.height(12.dp))

        if (capturando) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(12.dp))
                Text("Tomando la foto a resolución completa…", color = Color.White, fontSize = 13.sp)
            }
        } else if (listo) {
            Button(onClick = onDisparar, modifier = Modifier.fillMaxWidth()) {
                Text("Leer esta etiqueta")
            }
        } else {
            OutlinedButton(onClick = onDisparar, modifier = Modifier.fillMaxWidth()) {
                Text("Tomar la foto de todos modos")
            }
        }
    }
}

@Composable
private fun ChipControl(texto: String, activo: Boolean, onClick: () -> Unit) {
    Text(
        text = texto,
        color = if (activo) Color.Black else Color.White,
        fontSize = 11.5.sp,
        fontWeight = if (activo) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (activo) COLOR_RECONOCIDO else Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * Los lentes que de verdad tiene el equipo, no una lista fija.
 *
 * **Macro** es el que faltaba: en el Pixel aparece porque el rango de zoom baja
 * de 1x (0.5078x), y ese valor cambia al gran angular, que enfoca a 2 cm. En un
 * equipo cuyo rango empieza en 1x el chip no se dibuja, en vez de ofrecer un
 * botón que no hace nada.
 */
private fun presetsDeZoom(minimo: Float, maximo: Float): List<Pair<String, Float>> {
    val presets = mutableListOf<Pair<String, Float>>()
    if (minimo < UMBRAL_MACRO) presets += "Macro" to minimo
    presets += "1x" to 1f
    if (maximo >= 2f) presets += "2x" to 2f
    if (maximo >= 5f) presets += "5x" to 5f
    return presets
}

@Composable
private fun PermisoCamaraFaltante(onPedir: () -> Unit, onCerrar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .padding(26.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Hace falta el permiso de cámara",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sin cámara no se puede leer la etiqueta. El dato se puede capturar a mano.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onPedir) { Text("Dar permiso") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onCerrar) { Text("Capturar a mano") }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Captura
// ─────────────────────────────────────────────────────────────────────────────

private fun tomarFotoEtiqueta(
    context: Context,
    captura: ImageCapture,
    onGuardada: (File) -> Unit,
    onError: () -> Unit
) {
    val directorio = File(
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        DIRECTORIO_ETIQUETAS
    ).apply { mkdirs() }
    val marca = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(System.currentTimeMillis())
    val archivo = File(directorio, "etiqueta_$marca.jpg")

    captura.takePicture(
        ImageCapture.OutputFileOptions.Builder(archivo).build(),
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                onGuardada(archivo)
            }

            override fun onError(excepcion: ImageCaptureException) {
                Log.e(TAG_CAMARA_ETIQUETA, "Error al tomar la foto: ${excepcion.message}", excepcion)
                onError()
            }
        }
    )
}

private val COLOR_RECONOCIDO = Color(0xFF6BF178)
private val COLOR_CODIGO = Color(0xFF4FD8FF)
private val COLOR_TEXTO_SUELTO = Color(0x66FFFFFF)

private const val RADIO_MARCA = 6f
private const val ALTURA_ETIQUETA_PX = 34f
private const val ALTO_MINIMO_RELATIVO = 0.035f
private const val UMBRAL_MACRO = 0.95f
private const val TOLERANCIA_ZOOM = 0.05f
private const val MAX_CAMPOS_VISIBLES = 4
private const val LUMINANCIA_NEUTRA = 0.5
private const val WARMUP_TICK_MS = 120L
private const val ESPERA_ANTES_DEL_DISPARO_MS = 350L
private const val CALIDAD_JPEG_ETIQUETA = 95
private const val DIRECTORIO_ETIQUETAS = "revision_label_capture"
private const val TAG_CAMARA_ETIQUETA = "etiqueta-vivo-camera"

/**
 * El análisis en vivo no necesita resolución de lectura -- esa la da la foto --
 * pero sí la suficiente para que ML Kit *encuentre* renglones chicos y para que
 * las marcas se vean finas. 1280x960 es 4:3 exacto. Si alguna vez el HAL de una
 * tableta se ahoga aquí, este es el número que hay que bajar: 640x480 es el
 * valor que aguanta la Armor Pad Pro en Entrada Caseta.
 */
private val RESOLUCION_ANALISIS = AndroidSize(1280, 960)
