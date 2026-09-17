package com.example.pmmovil.ui.rostro

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.biometria.FaceEngine
import com.example.pmmovil.data.camera.CameraTrace
import com.example.pmmovil.data.model.FaceAparienciaDto
import com.example.pmmovil.data.model.FaceEnrolamientoRequest
import com.example.pmmovil.data.model.FacePlantillaDto
import com.example.pmmovil.data.model.VectorCodec
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.FaceRepository
import com.example.pmmovil.ui.rostro.FaceOrientationTracker.Companion.CELDAS_ARO
import com.example.pmmovil.ui.rostro.FaceOrientationTracker.Companion.SECTORES_PLANTILLA
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.abs

/** En que va el alta del rostro. */
enum class FaseEnrolamiento {
    /** Aviso de datos biometricos. Sin aceptarlo no se enciende la camara. */
    AVISO,

    /**
     * Firma del aviso. Va DESPUES de aceptar y ANTES de la camara: firmar algo
     * que todavia no se ha leido no es consentimiento, y capturar 26 tomas para
     * despues pedir la firma seria hacerle perder el tiempo a quien decida no
     * darla.
     */
    FIRMA,

    /**
     * Como funciona esto. Tres cosas y afuera: a que distancia ponerse, que el
     * giro es lento, y que al final hay que quedarse quieto un momento.
     *
     * Existe porque el sellado confundia: la gente terminaba el aro, veia
     * "Sellando" y se movia o se retiraba, justo cuando lo que hace falta es lo
     * contrario. Un enrolamiento que se cae al final es de los mas caros: son
     * 30 segundos tirados y la persona ya no lo intenta otra vez.
     */
    TUTORIAL,

    /** La toma de frente, que es la que mas pesa en el reconocimiento. */
    FRONTAL,

    /** Se dibuja el aro girando la cabeza. El numero de vuelta va en el estado. */
    VUELTA,

    /** El barrido final: comprueba que sigue siendo la misma cara y cierra. */
    SELLANDO,

    GUARDANDO,
    LISTO
}

data class EnrolamientoUiState(
    val cargando: Boolean = true,
    val motorListo: Boolean = false,
    val disponible: Boolean = true,
    val yaEnrolado: Boolean = false,
    val plantillasPrevias: Int = 0,

    /** Las apariencias ya dadas de alta: "Principal", "Con lentes"... */
    val apariencias: List<FaceAparienciaDto> = emptyList(),

    /** Tope que manda el servidor. Al llegar, no se ofrece agregar mas. */
    val maxApariencias: Int = 3,

    /** Como se va a llamar la que se esta capturando ahora. */
    val etiqueta: String = "",

    /**
     * true = esta alta borra las anteriores. Solo se enciende cuando la persona
     * lo pide expresamente ("Empezar de cero"); agregar es el camino normal.
     */
    val reemplazar: Boolean = false,

    /** Firma ya trazada, lista para viajar. Null mientras no haya firmado. */
    val firmaPngBase64: String? = null,

    val fase: FaseEnrolamiento = FaseEnrolamiento.AVISO,

    /** Tramos del aro ya pintados en la vuelta en curso. */
    val cobertura: Set<Int> = emptySet(),

    /** 1 o 2. Cual de las dos pasadas se esta dibujando. */
    val vuelta: Int = 1,

    /** Marca del aro hacia la que apunta la cara. Null = de frente. */
    val celdaActual: Int? = null,

    /** Se enciende cuando el aro lleva rato sin avanzar y ya casi esta cerrado. */
    val puedeDarPorBueno: Boolean = false,

    val hayRostro: Boolean = false,
    val calidadOk: Boolean = false,
    val pista: String = "",

    /** Progreso de la toma frontal, 0..1: se sostiene un momento de frente. */
    val progresoFrontal: Float = 0f,

    /** Confirmaciones hechas durante el sellado. */
    val confirmaciones: Int = 0,

    /** Plantillas guardadas en total. Es un dato del resumen, no una meta visible. */
    val plantillasTomadas: Int = 0,

    /** Contador de evento: la pantalla vibra una vez por plantilla nueva. */
    val hapticTick: Int = 0,

    val error: String? = null,
    val aviso: String? = null
) {
    /** Lo cerrado que esta el aro de la vuelta en curso, 0..1. */
    val progresoAro: Float
        get() = cobertura.size.toFloat() / CELDAS_ARO

    /** Avance de toda la sesion, para la barra de etapas. */
    val progreso: Float
        get() = when (fase) {
            FaseEnrolamiento.FRONTAL -> progresoFrontal * 0.10f
            FaseEnrolamiento.VUELTA ->
                0.10f + 0.75f * ((vuelta - 1 + progresoAro) / VUELTAS_PEDIDAS).coerceIn(0f, 1f)
            FaseEnrolamiento.SELLANDO -> 0.85f
            FaseEnrolamiento.GUARDANDO, FaseEnrolamiento.LISTO -> 1f
            else -> 0f
        }
}

/** Confirmaciones del sellado. Dos bastan para cachar a otra persona. */
const val CONFIRMACIONES_PEDIDAS = 2

/** Pasadas del aro. Dos, como Face ID: la segunda enriquece el banco. */
const val VUELTAS_PEDIDAS = 2

/**
 * Enrolamiento facial guiado.
 *
 * **Una sola magnitud.** Las dos versiones anteriores contaban una cosa y
 * dibujaban otra: la primera pedia llenar 24 sectores en grados absolutos --y
 * era posible quedarse atorado sin salida, porque el estimador comprime el
 * cabeceo--; la segunda dibujaba 48 marcas pero terminaba a las 10, dejando 38
 * apagadas sin explicacion. Las dos veces el problema fue el mismo: lo que se
 * veia no era lo que se pedia.
 *
 * Aqui el aro se pinta por donde ya paso la cara y se termina cuando cierra. Si
 * queda un hueco, el hueco es la instruccion; no hay un contador aparte que
 * pueda contradecirlo.
 *
 * Dentro conviven dos resoluciones que NO son dos progresos:
 *
 *  - la **cobertura** ([CELDAS_ARO] marcas de 10 grados) es el progreso, y es
 *    lo unico que se ensena;
 *  - las **plantillas** ([SECTORES_PLANTILLA] por vuelta, una por sector de 30
 *    grados) son lo que se guarda. Nunca se muestran como avance, asi que no
 *    pueden desalinearse de lo que ve el usuario.
 *
 * Las direcciones siguen siendo **relativas** a lo que esa persona alcanza
 * ([GuiaRostro.Calibracion]), que es lo que arreglo el callejon sin salida
 * original: "arriba" es lo mas arriba que puedes, no un umbral fijo en grados.
 */
@HiltViewModel
class EnrolamientoRostroViewModel @Inject constructor(
    application: Application,
    private val faceRepository: FaceRepository,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(EnrolamientoUiState())
    val uiState: StateFlow<EnrolamientoUiState> = _uiState.asStateFlow()

    private var engine: FaceEngine? = null
    private val analizando = AtomicBoolean(false)

    private data class Muestra(
        val vector: FloatArray,
        val calidad: FaceEngine.Calidad,
        val poseSector: Int,
        val puntaje: Float
    ) {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Muestra && vector.contentEquals(other.vector))

        override fun hashCode(): Int = vector.contentHashCode()
    }

    private var calibracion = GuiaRostro.Calibracion()

    private var frontal: Muestra? = null

    /**
     * Clave = vuelta * SECTORES_PLANTILLA + sector, para que la vuelta 2 no pise a la 1.
     *
     * Concurrente porque se consulta desde el hilo del analizador de la camara
     * ([debeCapturar]) y se escribe desde el de analisis ([procesarVuelta]).
     */
    private val porSector = java.util.concurrent.ConcurrentHashMap<Int, Muestra>()
    private val confirmaciones = mutableListOf<Muestra>()

    /**
     * Protege el aro y el rastreador. Los cuadros pueden llegar por dos vias --
     * el filtro ML Kit en el hilo del analizador y el analisis pesado en
     * Dispatchers.Default -- y las dos mueven este estado.
     */
    private val candadoAro = Any()

    private val cobertura = mutableSetOf<Int>()
    private var ultimaCelda: Int? = null
    private var ultimoAvanceMs = 0L
    private var selloDesdeMs = 0L
    private var guardadoProgramado = false

    private var cuadrosFrontales = 0
    private var consistenciaOk = 0
    private var consistenciaFalla = 0
    private val orientationTracker = FaceOrientationTracker()
    private var ultimaActualizacionPoseMs = 0L
    private var ultimaCapturaSfaceMs = 0L

    private val nombreDispositivo: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    init {
        cargar()
    }

    private fun cargar() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }

            val contexto = getApplication<Application>()
            if (!FaceEngine.isAvailable(contexto)) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        motorListo = false,
                        error = "Este equipo no trae los modelos de reconocimiento facial."
                    )
                }
                return@launch
            }

            val motor = withContext(Dispatchers.IO) { FaceEngine.crear(contexto) }
            if (motor == null) {
                _uiState.update {
                    it.copy(
                        cargando = false,
                        motorListo = false,
                        error = "No se pudieron cargar los modelos faciales."
                    )
                }
                return@launch
            }
            engine = motor

            faceRepository.getEstado()
                .onSuccess { estado ->
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            motorListo = true,
                            disponible = estado.Disponible,
                            yaEnrolado = estado.Enrolado,
                            plantillasPrevias = estado.Plantillas,
                            apariencias = estado.Apariencias,
                            maxApariencias = estado.MaxApariencias
                        )
                    }
                }
                .onFailure { fallo ->
                    _uiState.update {
                        it.copy(cargando = false, motorListo = true, error = fallo.message)
                    }
                }
        }
    }

    /**
     * Aceptado el aviso, toca firmarlo. La camara sigue apagada: no se enciende
     * hasta que el consentimiento este completo.
     */
    fun aceptarAviso() {
        _uiState.update { it.copy(fase = FaseEnrolamiento.FIRMA, error = null, pista = "") }
    }

    /**
     * Guarda el trazo y pasa al tutorial.
     *
     * El PNG se comprime aqui y no en la pantalla porque el tamano es una regla
     * del contrato con el servidor (256 KB), no una decision de dibujo.
     */
    fun firmar(firma: Bitmap) {
        val base64 = runCatching {
            ByteArrayOutputStream().use { salida ->
                firma.compress(Bitmap.CompressFormat.PNG, 100, salida)
                Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
            }
        }.getOrNull()

        if (base64.isNullOrBlank()) {
            _uiState.update { it.copy(error = "No se pudo guardar la firma. Intenta de nuevo.") }
            return
        }

        _uiState.update {
            it.copy(
                firmaPngBase64 = base64,
                fase = FaseEnrolamiento.TUTORIAL,
                error = null
            )
        }
    }

    /** Volver del tutorial o de la firma sin perder lo ya aceptado. */
    fun regresarAFirma() {
        _uiState.update { it.copy(fase = FaseEnrolamiento.FIRMA, error = null) }
    }

    /**
     * Leido el tutorial: se enciende la camara y empieza la captura.
     *
     * Aqui es donde se reinician las capturas y no al aceptar el aviso: entre
     * el aviso y este punto hay dos pantallas en las que la persona puede
     * volverse atras, y no tiene sentido tener el aro a medias mientras tanto.
     */
    fun comenzarCaptura() {
        if (_uiState.value.firmaPngBase64.isNullOrBlank()) {
            _uiState.update { it.copy(fase = FaseEnrolamiento.FIRMA, error = "Falta tu firma.") }
            return
        }
        reiniciarCapturas()
        _uiState.update {
            it.copy(
                fase = FaseEnrolamiento.FRONTAL,
                error = null,
                pista = ""
            )
        }
    }

    /** Nombre de la apariencia que se esta por capturar. */
    fun cambiarEtiqueta(valor: String) {
        _uiState.update { it.copy(etiqueta = valor.take(40)) }
    }

    /**
     * Empieza un alta nueva desde la pantalla de apariencias.
     *
     * [reemplazar] en true es "empezar de cero": borra lo anterior. En false --
     * el camino normal -- suma una apariencia a las que ya hay.
     */
    fun nuevaApariencia(reemplazar: Boolean) {
        val estado = _uiState.value
        if (!reemplazar && estado.apariencias.size >= estado.maxApariencias) {
            _uiState.update {
                it.copy(
                    error = "Ya tienes ${estado.maxApariencias} apariencias. " +
                        "Borra una antes de agregar otra."
                )
            }
            return
        }
        reiniciarCapturas()
        _uiState.update {
            it.copy(
                // Directo a la firma: el aviso ya lo tiene leido y sigue a un
                // toque de distancia arriba. Lo que si se vuelve a pedir es la
                // firma -- cada alta lleva su propia autorizacion, porque cada
                // una es un dato biometrico nuevo que se guarda.
                fase = FaseEnrolamiento.FIRMA,
                reemplazar = reemplazar,
                etiqueta = "",
                firmaPngBase64 = null,
                error = null,
                aviso = null
            )
        }
    }

    /** Relee la lista del servidor sin tocar la fase en curso. */
    private fun refrescarApariencias() {
        viewModelScope.launch {
            faceRepository.getEstado().onSuccess { estado ->
                _uiState.update {
                    it.copy(
                        apariencias = estado.Apariencias,
                        maxApariencias = estado.MaxApariencias,
                        yaEnrolado = estado.Enrolado,
                        plantillasPrevias = estado.Plantillas
                    )
                }
            }
        }
    }

    /** Borra una apariencia y deja las demas. */
    fun revocarApariencia(id: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            faceRepository.revocarApariencia(id)
                .onSuccess { resultado ->
                    val restantes = _uiState.value.apariencias.filterNot { it.Id == id }
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            apariencias = restantes,
                            yaEnrolado = restantes.isNotEmpty(),
                            plantillasPrevias = restantes.sumOf { a -> a.Plantillas },
                            aviso = resultado.Message ?: "Apariencia borrada."
                        )
                    }
                }
                .onFailure { fallo ->
                    _uiState.update { it.copy(cargando = false, error = fallo.message) }
                }
        }
    }

    /**
     * Salida de emergencia: cierra la vuelta con lo que haya.
     *
     * Solo se ofrece cuando el aro lleva rato parado y ya esta casi completo, y
     * la pantalla deja el hueco a la vista. Un enrolamiento del que no se puede
     * salir no es dificil, es uno roto -- pero fingir que se cerro cuando no,
     * tambien.
     */
    fun darPorBueno() {
        if (_uiState.value.fase != FaseEnrolamiento.VUELTA) return
        if (!_uiState.value.puedeDarPorBueno) return
        synchronized(candadoAro) { cerrarVuelta() }
    }

    fun reportarErrorCamara(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun reiniciar() {
        reiniciarCapturas()
        _uiState.update {
            EnrolamientoUiState(
                cargando = false,
                motorListo = it.motorListo,
                disponible = it.disponible,
                yaEnrolado = it.yaEnrolado,
                plantillasPrevias = it.plantillasPrevias,
                apariencias = it.apariencias,
                maxApariencias = it.maxApariencias,
                etiqueta = it.etiqueta,
                reemplazar = it.reemplazar,
                // La firma NO se conserva: reiniciar es volver a empezar el
                // alta, y el consentimiento se firma para el alta que se hace,
                // no se arrastra de un intento anterior.
                firmaPngBase64 = null,
                fase = FaseEnrolamiento.AVISO
            )
        }
    }

    fun limpiarMensajes() = _uiState.update { it.copy(error = null, aviso = null) }

    private fun reiniciarCapturas() = synchronized(candadoAro) {
        calibracion = GuiaRostro.Calibracion()
        frontal = null
        porSector.clear()
        confirmaciones.clear()
        cobertura.clear()
        ultimaCelda = null
        ultimoAvanceMs = SystemClock.elapsedRealtime()
        selloDesdeMs = 0L
        guardadoProgramado = false
        cuadrosFrontales = 0
        consistenciaOk = 0
        consistenciaFalla = 0
        orientationTracker.reset()
        ultimaActualizacionPoseMs = 0L
        ultimaCapturaSfaceMs = 0L
        _uiState.update {
            it.copy(
                cobertura = emptySet(),
                vuelta = 1,
                celdaActual = null,
                puedeDarPorBueno = false,
                plantillasTomadas = 0,
                confirmaciones = 0,
                progresoFrontal = 0f,
                hapticTick = 0
            )
        }
    }

    // ── Análisis en vivo ─────────────────────────────────────────────────────

    fun analizarCuadro(frame: RostroFrame) {
        val motor = engine ?: return
        val fase = _uiState.value.fase
        if (fase != FaseEnrolamiento.FRONTAL &&
            fase != FaseEnrolamiento.VUELTA &&
            fase != FaseEnrolamiento.SELLANDO
        ) {
            return
        }

        if (frame.gate.status != FaceGateStatus.READY) {
            actualizarDesdeGate(frame.gate)
            return
        }

        val posePrevia = if (frame.bitmap == null) prepararPoseMlKit(frame.gate) else null
        // Durante el sellado se procesa siempre: es donde se mide la
        // consistencia, y filtrar aqui dejaba la pantalla trabada para siempre
        // en cuanto se juntaban las confirmaciones.
        if (frame.bitmap == null &&
            fase != FaseEnrolamiento.SELLANDO &&
            !debeCapturar(posePrevia)
        ) return

        val ahora = SystemClock.elapsedRealtime()
        val intervaloMinimo = when (_uiState.value.fase) {
            FaseEnrolamiento.FRONTAL -> INTERVALO_SFACE_FRONTAL_MS
            // En el sellado se procesan todos los cuadros (es donde se mide la
            // consistencia), asi que se espacian mas: con dos confirmaciones
            // que juntar en 2.4 s, ocho oportunidades sobran y no se carga la
            // pantalla justo cuando la persona la esta mirando fijo.
            FaseEnrolamiento.SELLANDO -> INTERVALO_SFACE_SELLO_MS
            else -> INTERVALO_SFACE_MS
        }
        if (analizando.get() || ahora - ultimaCapturaSfaceMs < intervaloMinimo) {
            return
        }
        if (!analizando.compareAndSet(false, true)) {
            return
        }
        ultimaCapturaSfaceMs = ahora

        // Convertir el cuadro (YUV a RGB + rotacion) es lo mas caro de todo el
        // ciclo. La traza deja ver cuanto tarda y, sobre todo, EN QUE HILO: si
        // vuelve a decir "main", la pantalla va a dar tirones otra vez.
        val inicioBitmap = SystemClock.elapsedRealtime()
        val bitmap = frame.bitmap ?: frame.bitmapProvider?.invoke()
        val msBitmap = SystemClock.elapsedRealtime() - inicioBitmap
        if (msBitmap > 8) {
            CameraTrace.d("enrol bitmap ${msBitmap}ms hilo=${Thread.currentThread().name}")
        }
        if (bitmap == null) {
            analizando.set(false)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (val resultado = motor.procesar(bitmap)) {
                    is FaceEngine.Resultado.Ok -> procesar(
                        captura = fusionarPoseMlKit(resultado.captura, frame.gate),
                        gate = frame.gate,
                        posePrevia = posePrevia
                    )
                    FaceEngine.Resultado.SinRostro -> _uiState.update {
                        orientationTracker.resetCandidate()
                        it.copy(
                            hayRostro = false,
                            calidadOk = false,
                            celdaActual = null,
                            pista = "No se detecta el rostro"
                        )
                    }
                    FaceEngine.Resultado.VariasCaras -> _uiState.update {
                        orientationTracker.resetCandidate()
                        it.copy(
                            hayRostro = true,
                            calidadOk = false,
                            celdaActual = null,
                            pista = "Solo debe aparecer una persona"
                        )
                    }
                    is FaceEngine.Resultado.Error -> _uiState.update {
                        orientationTracker.resetCandidate()
                        it.copy(
                            hayRostro = false,
                            calidadOk = false,
                            celdaActual = null,
                            error = "No se pudo analizar el rostro: ${resultado.causa}"
                        )
                    }
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
                analizando.set(false)
            }
        }
    }

    private fun actualizarDesdeGate(gate: FaceGateResult) {
        if (analizando.get()) return

        when (gate.status) {
            FaceGateStatus.NO_FACE -> _uiState.update {
                orientationTracker.resetCandidate()
                ultimaCelda = null
                it.copy(
                    hayRostro = false,
                    calidadOk = false,
                    celdaActual = null,
                    pista = "No se detecta el rostro"
                )
            }
            FaceGateStatus.MULTIPLE_FACES -> _uiState.update {
                orientationTracker.resetCandidate()
                it.copy(
                    hayRostro = true,
                    calidadOk = false,
                    celdaActual = null,
                    pista = "Solo debe aparecer una persona"
                )
            }
            FaceGateStatus.TOO_SMALL,
            FaceGateStatus.TOO_LARGE,
            FaceGateStatus.OUT_OF_FRAME -> _uiState.update {
                orientationTracker.resetCandidate()
                it.copy(
                    hayRostro = true,
                    calidadOk = false,
                    celdaActual = null,
                    pista = gate.detail ?: "Acomódate dentro del círculo"
                )
            }
            FaceGateStatus.ERROR -> _uiState.update {
                orientationTracker.resetCandidate()
                it.copy(
                    hayRostro = false,
                    calidadOk = false,
                    celdaActual = null,
                    error = "No se pudo analizar el rostro: ${gate.detail ?: "error desconocido"}"
                )
            }
            FaceGateStatus.READY -> Unit
        }
    }

    private fun fusionarPoseMlKit(
        captura: FaceEngine.Captura,
        gate: FaceGateResult
    ): FaceEngine.Captura {
        val calidad = captura.calidad
        return captura.copy(
            calidad = calidad.copy(
                // La pose de ML Kit es más estable en esta tableta que la
                // solvePnP sin calibrar. Se conserva YuNet/SFace para el
                // detector, calidad y vector de identidad.
                yaw = -gate.yaw,
                pitch = gate.pitch,
                roll = gate.roll
            )
        )
    }

    private fun prepararPoseMlKit(gate: FaceGateResult): FaceOrientationTracker.Sample =
        synchronized(candadoAro) {
            val yaw = -gate.yaw
            val pitch = gate.pitch
            calibracion = calibracion.con(yaw, pitch)

            val pose = orientationTracker.update(
                yaw = yaw,
                pitch = pitch,
                yawReference = calibracion.yawRef,
                pitchReference = calibracion.pitchRef
            )
            pintarAro(pose)
            actualizarPoseUi(pose)
            pose
        }

    private fun debeCapturar(pose: FaceOrientationTracker.Sample?): Boolean {
        if (pose == null) return false
        return when (_uiState.value.fase) {
            FaseEnrolamiento.FRONTAL ->
                frontal == null && GuiaRostro.esFrontal(pose.yaw, pose.pitch, calibracion)
            FaseEnrolamiento.VUELTA ->
                orientationTracker.isStable(pose) &&
                    pose.sector != null &&
                    !porSector.containsKey(claveSector(pose.sector))
            FaseEnrolamiento.SELLANDO ->
                confirmaciones.size < CONFIRMACIONES_PEDIDAS &&
                    GuiaRostro.esFrontal(pose.yaw, pose.pitch, calibracion)
            else -> false
        }
    }

    private fun procesar(
        captura: FaceEngine.Captura,
        gate: FaceGateResult,
        posePrevia: FaceOrientationTracker.Sample?
    ) {
        val calidad = captura.calidad
        val motivo = motivoNoUsable(calidad)

        // El preview de la cámara frontal va espejado. Los cuadros que analiza
        // YuNet no lo están, así que sin voltear el yaw la guía pediría un lado
        // y el aro se pintaría del otro.
        val yaw = -calidad.yaw
        val pitch = calidad.pitch

        if (motivo != null) {
            orientationTracker.resetCandidate()
            _uiState.update {
                it.copy(
                    hayRostro = true,
                    calidadOk = false,
                    pista = motivo
                )
            }
            return
        }

        CameraTrace.d(
            "enrol fase=${_uiState.value.fase} vuelta=${_uiState.value.vuelta} " +
                "aro=${cobertura.size}/$CELDAS_ARO " +
                "poseYaw=${"%.1f".format(java.util.Locale.US, yaw)} " +
                "posePitch=${"%.1f".format(java.util.Locale.US, pitch)}"
        )

        // Cuando el cuadro vino del filtro ML Kit ya calculamos la pose antes
        // de convertir el Bitmap. Así no se actualiza dos veces el rastreador.
        val pose = posePrevia ?: prepararPoseMlKit(gate)

        when (_uiState.value.fase) {
            FaseEnrolamiento.FRONTAL -> procesarFrontal(captura, pose.yaw, pose.pitch)
            FaseEnrolamiento.VUELTA -> procesarVuelta(captura, pose)
            FaseEnrolamiento.SELLANDO -> procesarSello(captura, pose.yaw, pose.pitch)
            else -> Unit
        }
    }

    /**
     * El trazo del aro. Es lo unico que ve el usuario como progreso, asi que va
     * pegado a la cara y NO espera a que se quede quieta: pinta la celda actual
     * y el camino que hizo desde la anterior.
     */
    private fun pintarAro(pose: FaceOrientationTracker.Sample) {
        if (_uiState.value.fase != FaseEnrolamiento.VUELTA) return
        val celda = pose.celda
        if (celda == null) {
            ultimaCelda = null
            return
        }

        val antes = cobertura.size
        ultimaCelda?.let { cobertura.addAll(FaceOrientationTracker.caminoEntre(it, celda)) }
        cobertura.add(celda)
        ultimaCelda = celda

        if (cobertura.size > antes) {
            ultimoAvanceMs = SystemClock.elapsedRealtime()
            if (_uiState.value.puedeDarPorBueno) {
                _uiState.update { it.copy(puedeDarPorBueno = false) }
            }
        }

        if (cobertura.size >= CELDAS_ARO) cerrarVuelta()
    }

    /**
     * El estado de Compose no tiene por que cambiar tan rapido como llegan los
     * cuadros: el preview va por su cuenta. Se refresca cuando de verdad cambio
     * algo, o como maximo cada 90 ms para que el cometa se vea fluido.
     */
    private fun actualizarPoseUi(pose: FaceOrientationTracker.Sample) {
        val ahora = SystemClock.elapsedRealtime()
        val estado = _uiState.value
        val ofrecerSalida = estado.fase == FaseEnrolamiento.VUELTA &&
            !estado.puedeDarPorBueno &&
            cobertura.size >= (CELDAS_ARO * COBERTURA_PARA_OFRECER_SALIDA).toInt() &&
            ahora - ultimoAvanceMs > MS_PARA_OFRECER_SALIDA

        // Sólo lo que la pantalla dibuja de verdad. Antes entraban tambien el
        // yaw y el pitch, que cambian en cada cuadro y no los lee nadie: era
        // una recomposicion completa 15 veces por segundo para nada.
        val cambioRelevante = ofrecerSalida ||
            estado.cobertura.size != cobertura.size ||
            estado.celdaActual != pose.celda

        if (!cambioRelevante) return

        ultimaActualizacionPoseMs = ahora
        _uiState.update {
            it.copy(
                cobertura = cobertura.toSet(),
                celdaActual = pose.celda,
                puedeDarPorBueno = it.puedeDarPorBueno || ofrecerSalida
            )
        }
    }

    private fun procesarFrontal(captura: FaceEngine.Captura, yaw: Float, pitch: Float) {
        val esFrontal = GuiaRostro.esFrontal(yaw, pitch, calibracion)

        if (!esFrontal) {
            cuadrosFrontales = 0
            _uiState.update {
                it.copy(
                    hayRostro = true,
                    calidadOk = true,
                    progresoFrontal = 0f,
                    pista = "Ponte de frente, dentro del círculo"
                )
            }
            return
        }

        cuadrosFrontales++
        val muestra = Muestra(captura.vector, captura.calidad, -1, puntaje(captura.calidad))
        if ((frontal?.puntaje ?: -1f) < muestra.puntaje) frontal = muestra

        val progreso = (cuadrosFrontales.toFloat() / CUADROS_FRONTALES).coerceIn(0f, 1f)
        _uiState.update {
            it.copy(
                hayRostro = true,
                calidadOk = true,
                progresoFrontal = progreso,
                plantillasTomadas = it.plantillasTomadas + 1,
                pista = "",
                hapticTick = it.hapticTick + 1
            )
        }

        if (cuadrosFrontales >= CUADROS_FRONTALES) {
            ultimoAvanceMs = SystemClock.elapsedRealtime()
            ultimaCelda = null
            _uiState.update { it.copy(fase = FaseEnrolamiento.VUELTA, pista = "") }
        }
    }

    /**
     * Guarda una plantilla por sector de 30 grados. El aro ya se pinto en
     * [pintarAro]; aqui solo se decide que tomas valen la pena conservar.
     */
    private fun procesarVuelta(
        captura: FaceEngine.Captura,
        pose: FaceOrientationTracker.Sample
    ) {
        val sector = pose.sector ?: return

        _uiState.update {
            it.copy(hayRostro = true, calidadOk = true, pista = "")
        }

        if (!orientationTracker.isStable(pose)) return
        val clave = claveSector(sector)
        if (porSector.containsKey(clave)) return

        porSector[clave] = Muestra(
            captura.vector,
            captura.calidad,
            sector,
            puntaje(captura.calidad)
        )

        _uiState.update {
            it.copy(
                plantillasTomadas = it.plantillasTomadas + 1,
                hapticTick = it.hapticTick + 1
            )
        }
    }

    /**
     * Cierra la vuelta en curso. Si queda otra, la arranca sin pantalla
     * intermedia: la pausa modal cortaba el movimiento justo cuando la persona
     * ya le habia agarrado el ritmo.
     */
    private fun cerrarVuelta() {
        val estado = _uiState.value
        if (estado.fase != FaseEnrolamiento.VUELTA) return

        val cerrada = cobertura.toSet()
        if (estado.vuelta < VUELTAS_PEDIDAS) {
            cobertura.clear()
            ultimaCelda = null
            ultimoAvanceMs = SystemClock.elapsedRealtime()
            orientationTracker.resetCandidate()
            _uiState.update {
                it.copy(
                    vuelta = it.vuelta + 1,
                    cobertura = emptySet(),
                    puedeDarPorBueno = false
                )
            }
        } else {
            selloDesdeMs = SystemClock.elapsedRealtime()
            guardadoProgramado = false
            _uiState.update {
                it.copy(
                    fase = FaseEnrolamiento.SELLANDO,
                    cobertura = cerrada,
                    puedeDarPorBueno = false,
                    pista = ""
                )
            }
            // Red de seguridad: si la cara no vuelve a quedar de frente, el
            // sello se cierra igual con lo que haya. La consistencia se midio
            // en todos los cuadros del sellado, no solo en las confirmaciones.
            viewModelScope.launch {
                delay(MS_SELLO_MAXIMO)
                if (_uiState.value.fase == FaseEnrolamiento.SELLANDO) guardar()
            }
        }
    }

    private fun procesarSello(captura: FaceEngine.Captura, yaw: Float, pitch: Float) {
        val banco = plantillasDeReferencia()
        if (banco.isEmpty()) {
            guardar()
            return
        }

        val mejor = banco.maxOf { FaceEngine.similitudCoseno(captura.vector, it.vector) }
        if (mejor >= UMBRAL_CONSISTENCIA) consistenciaOk++ else consistenciaFalla++

        // El sello es de frente: es la pose más comparable y la más rápida de
        // conseguir. Además es donde el usuario ya está mirando.
        if (!GuiaRostro.esFrontal(yaw, pitch, calibracion)) {
            _uiState.update {
                it.copy(
                    hayRostro = true,
                    calidadOk = true,
                    pista = "Mira a la pantalla"
                )
            }
            return
        }

        if (confirmaciones.size < CONFIRMACIONES_PEDIDAS) {
            confirmaciones.add(
                Muestra(captura.vector, captura.calidad, -1, puntaje(captura.calidad))
            )
            _uiState.update {
                it.copy(
                    hayRostro = true,
                    calidadOk = true,
                    confirmaciones = confirmaciones.size,
                    pista = ""
                )
            }
        }

        if (confirmaciones.size >= CONFIRMACIONES_PEDIDAS) programarGuardado()
    }

    /**
     * Cierra el sello por reloj, no por cuadros.
     *
     * No se guarda antes de que termine el barrido -- se veria un salto a media
     * vuelta -- pero tampoco se espera a otro cuadro para darse cuenta de que ya
     * pasó el tiempo. Depender de eso fue justo lo que dejaba la pantalla
     * trabada en "Sellando".
     */
    private fun programarGuardado() {
        if (guardadoProgramado) return
        guardadoProgramado = true
        viewModelScope.launch {
            val restante = MS_SELLO - (SystemClock.elapsedRealtime() - selloDesdeMs)
            if (restante > 0) delay(restante)
            if (_uiState.value.fase == FaseEnrolamiento.SELLANDO) guardar()
        }
    }

    private fun claveSector(sector: Int): Int =
        (_uiState.value.vuelta - 1) * SECTORES_PLANTILLA + sector

    private fun plantillasDeReferencia(): List<Muestra> =
        buildList {
            frontal?.let { add(it) }
            addAll(porSector.values)
        }

    private fun guardar() {
        if (_uiState.value.fase == FaseEnrolamiento.GUARDANDO) return

        val total = consistenciaOk + consistenciaFalla
        if (total >= MIN_MUESTRAS_CONSISTENCIA && consistenciaFalla > consistenciaOk) {
            reiniciarCapturas()
            _uiState.update {
                it.copy(
                    fase = FaseEnrolamiento.AVISO,
                    error = "La confirmación no coincidió. Empieza de nuevo, y que sea " +
                        "siempre la misma persona frente a la cámara."
                )
            }
            return
        }

        val mejorConfirmacion = confirmaciones.maxByOrNull { it.puntaje }
        val todas = buildList {
            addAll(plantillasDeReferencia())
            mejorConfirmacion?.let { add(it) }
        }

        if (todas.isEmpty()) {
            _uiState.update { it.copy(error = "No se capturó ninguna toma utilizable.") }
            return
        }

        // Sin firma no se manda nada. No deberia poder llegarse hasta aqui sin
        // ella -- la fase FIRMA va antes de encender la camara -- pero el alta
        // se rechazaria del lado del servidor, y es mejor decirlo con las 26
        // tomas todavia en memoria que despues de tirarlas.
        val firma = _uiState.value.firmaPngBase64
        if (firma.isNullOrBlank()) {
            _uiState.update {
                it.copy(fase = FaseEnrolamiento.FIRMA, error = "Falta tu firma del aviso.")
            }
            return
        }

        _uiState.update { it.copy(fase = FaseEnrolamiento.GUARDANDO, error = null) }

        viewModelScope.launch {
            val request = FaceEnrolamientoRequest(
                AceptacionTexto = TEXTO_AVISO,
                FirmaPngBase64 = firma,
                Etiqueta = _uiState.value.etiqueta.trim().ifBlank { null },
                Reemplazar = _uiState.value.reemplazar,
                Dispositivo = nombreDispositivo,
                Planta = authRepository.getPlanta(),
                Plantillas = todas.map { muestra ->
                    FacePlantillaDto(
                        VectorBase64 = VectorCodec.aBase64(muestra.vector),
                        Dims = muestra.vector.size,
                        PoseSector = muestra.poseSector,
                        Yaw = muestra.calidad.yaw,
                        Pitch = muestra.calidad.pitch,
                        Roll = muestra.calidad.roll,
                        Luma = muestra.calidad.luma,
                        Nitidez = muestra.calidad.nitidez,
                        ScoreDeteccion = muestra.calidad.scoreDeteccion,
                        RostroPx = muestra.calidad.rostroPx
                    )
                }
            )

            faceRepository.enrolar(request)
                .onSuccess { resultado ->
                    _uiState.update {
                        it.copy(
                            fase = FaseEnrolamiento.LISTO,
                            yaEnrolado = true,
                            plantillasPrevias = resultado.Plantillas,
                            aviso = resultado.Message
                        )
                    }
                    // La lista de apariencias se vuelve a pedir al servidor en
                    // vez de armarla aqui: el nombre definitivo lo decide el, y
                    // puede no ser el que se propuso (si ya existia, lo numera).
                    refrescarApariencias()
                }
                .onFailure { fallo ->
                    guardadoProgramado = false
                    _uiState.update {
                        it.copy(
                            fase = FaseEnrolamiento.AVISO,
                            error = fallo.message
                                ?: "No se pudo guardar tu rostro. Intenta de nuevo."
                        )
                    }
                }
        }
    }

    fun revocar() {
        viewModelScope.launch {
            _uiState.update { it.copy(cargando = true, error = null) }
            faceRepository.revocar()
                .onSuccess {
                    reiniciarCapturas()
                    _uiState.update {
                        it.copy(
                            cargando = false,
                            yaEnrolado = false,
                            plantillasPrevias = 0,
                            fase = FaseEnrolamiento.AVISO,
                            aviso = "Se eliminó tu rostro."
                        )
                    }
                }
                .onFailure { fallo ->
                    _uiState.update { it.copy(cargando = false, error = fallo.message) }
                }
        }
    }

    /**
     * Compuerta de calidad. Más suelta que la primera versión en el giro y la
     * inclinación: pedir la cabeza derecha mientras se pide girarla era una
     * contradicción que sólo servía para trabar el avance.
     */
    private fun motivoNoUsable(c: FaceEngine.Calidad): String? {
        val centroX = (c.marco.left + c.marco.right) / 2f
        val centroY = (c.marco.top + c.marco.bottom) / 2f

        return when {
            c.rostroPx < 110 -> "Acércate un poco"
            c.rostroPx > 430 -> "Aléjate un poco"
            abs(centroX - 0.5f) > 0.22f ||
                abs(centroY - 0.5f) > 0.25f -> "Mantén el rostro dentro del círculo"
            c.luma < 35f -> "Falta luz"
            c.nitidez < 40f -> "No te muevas tan rápido"
            abs(c.roll) > 35f -> "Endereza un poco la cabeza"
            c.scoreDeteccion < 0.7f -> "Acomódate dentro del círculo"
            else -> null
        }
    }

    private fun puntaje(c: FaceEngine.Calidad): Float =
        c.scoreDeteccion * 100f +
            c.nitidez.coerceAtMost(400f) / 4f +
            c.rostroPx / 10f

    override fun onCleared() {
        engine?.cerrar()
        engine = null
        super.onCleared()
    }

    companion object {
        /** Cuadros buenos de frente antes de dar por buena la toma frontal. */
        private const val CUADROS_FRONTALES = 1

        private const val INTERVALO_SFACE_FRONTAL_MS = 180L
        private const val INTERVALO_SFACE_MS = 220L
        private const val INTERVALO_SFACE_SELLO_MS = 300L

        private const val UMBRAL_CONSISTENCIA = 0.35f
        private const val MIN_MUESTRAS_CONSISTENCIA = 6

        /** El barrido tarda esto en dar la vuelta; el sello no acaba antes. */
        const val MS_SELLO = 2_400L

        /** ...ni se queda esperando mas que esto a una cara de frente. */
        private const val MS_SELLO_MAXIMO = 7_000L

        /**
         * Refresco de la marca activa. Ya no alimenta una animacion continua,
         * solo cambia de marca, asi que no hace falta ir a ritmo de cuadro.
         */
        private const val MS_REFRESCO_POSE = 150L

        /** Sin avance durante este rato, se ofrece cerrar la vuelta como esté. */
        private const val MS_PARA_OFRECER_SALIDA = 14_000L

        /** ...y sólo si ya está casi cerrada. */
        private const val COBERTURA_PARA_OFRECER_SALIDA = 0.85f

        /**
         * Aviso que se sella junto con el alta. Si cambia el texto, cambia lo
         * que quedó registrado como aceptado -- por eso vive aquí y no en la
         * pantalla.
         */
        const val TEXTO_AVISO =
            "Autorizo a Grupo PMM a tratar mi dato biométrico facial, en los " +
                "siguientes términos:\n\n" +
                "QUÉ SE GUARDA. Un modelo matemático de 128 números derivado de mi " +
                "rostro, y la firma de este aviso. No se guardan fotografías ni video, " +
                "y del modelo no se puede reconstruir mi cara.\n\n" +
                "PARA QUÉ. Únicamente para identificarme al iniciar sesión en PMMovil. " +
                "Para ningún otro fin: ni control de asistencia, ni productividad, ni " +
                "supervisión.\n\n" +
                "DÓNDE. En los servidores de Grupo PMM. No se comparte con terceros ni " +
                "sale de la empresa. Cada intento de acceso queda registrado con fecha " +
                "y equipo.\n\n" +
                "POR CUÁNTO TIEMPO. Mientras yo lo quiera y siga siendo empleado. Al " +
                "causar baja se elimina.\n\n" +
                "MIS DERECHOS. Puedo borrarlo cuando quiera desde Ajustes, sin dar " +
                "explicaciones y sin perder el acceso: el gafete y la contraseña " +
                "siguen funcionando igual. Dar de alta mi rostro es voluntario y " +
                "negarme no tiene ninguna consecuencia laboral.\n\n" +
                "(v2)"
    }
}
