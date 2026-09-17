package com.example.pmmovil.ui.rostro

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.biometria.FaceEngine
import com.example.pmmovil.data.model.FaceLoginRequest
import com.example.pmmovil.data.model.VectorCodec
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RostroNoHabilitadoException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class RostroLoginUiState(
    /** El motor cargó y los modelos existen en este APK. */
    val motorListo: Boolean = false,

    /** La persona pidió entrar con la cara y la cámara está enlazada. */
    val activo: Boolean = false,

    val estado: EstadoGlifo = EstadoGlifo.REPOSO,
    val titulo: String = "Entrar con tu rostro",
    val nota: String = "",

    /** Intentos gastados en esta presencia frente a la cámara. */
    val intento: Int = 0,

    /**
     * La cara se retiró: se acabaron los intentos. NO es un error -- el gafete
     * y el lector nunca dejaron de escuchar.
     */
    val retirado: Boolean = false,

    val exito: Boolean = false,
    val nombre: String? = null,

    /**
     * El servidor contestó 503: el módulo facial está apagado. La pantalla
     * deja de ofrecer el rostro por lo que resta de la sesión y se queda con
     * gafete, lector, QR y manual -- que nunca dejaron de escuchar.
     */
    val deshabilitadoEnServidor: Boolean = false,

    /**
     * La cámara sigue encendida, pero el análisis está detenido porque otro
     * canal (gafete, lector) tomó el turno.
     *
     * Es distinto de [activo], y la diferencia importa: apagar la cámara para
     * volver a encenderla en cuanto el gafete falle costaría el ciclo completo
     * del HAL -- un segundo de respiro más la convergencia de la exposición --
     * cada vez que alguien acerca un gafete que no era. Pausar el análisis es
     * gratis y la cámara ya está caliente cuando toca reanudar.
     */
    val enPausa: Boolean = false
)

/**
 * Entrar con la cara.
 *
 * Dos cosas que conviene tener presentes al leer esto:
 *
 * 1. Este ViewModel NUNCA decide quién eres. Produce un vector y lo manda; el
 *    servidor busca contra el banco y firma el token. Si el teléfono pudiera
 *    afirmar una identidad, bastaría con descompilar el APK.
 *
 * 2. La política de reintento es de SEGURIDAD, no de paciencia. Un caso
 *    limítrofe -- 0.44 contra un umbral de 0.45 -- es una moneda al aire, y
 *    cada reintento la vuelve a lanzar. Reintentar sin tope convertiría un
 *    riesgo de 1 en N en uno de 1 en N/k, justo con la población que menos
 *    queremos dejar pasar. Por eso el tope es dos, y el segundo sólo sale si
 *    el cuadro cambió de verdad.
 */
@HiltViewModel
class RostroLoginViewModel @Inject constructor(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(RostroLoginUiState())
    val uiState: StateFlow<RostroLoginUiState> = _uiState.asStateFlow()

    private var engine: FaceEngine? = null
    private val analizando = AtomicBoolean(false)

    /** Cuadros buenos seguidos. Se exigen varios para no mandar un golpe de suerte. */
    private var buenosSeguidos = 0

    /** Último instante en que se vio una cara, para reiniciar el contador. */
    private var ultimoRostroMs = 0L

    /** Vector del último intento: sirve para no repreguntar lo mismo. */
    private var ultimoVectorEnviado: FloatArray? = null

    private val nombreDispositivo: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    init {
        viewModelScope.launch {
            val contexto = getApplication<Application>()
            if (!FaceEngine.isAvailable(contexto)) return@launch
            val motor = withContext(Dispatchers.IO) { FaceEngine.crear(contexto) }
            engine = motor
            _uiState.update { it.copy(motorListo = motor != null) }
        }
    }

    /** La persona tocó el glifo. Enciende la cámara. */
    fun iniciar() {
        if (!_uiState.value.motorListo) return
        // Si el servidor ya dijo que el módulo está apagado, encender la
        // cámara sólo serviría para volver a preguntarle lo mismo.
        if (_uiState.value.deshabilitadoEnServidor) return
        buenosSeguidos = 0
        ultimoRostroMs = SystemClock.elapsedRealtime()
        ultimoVectorEnviado = null
        _uiState.update {
            it.copy(
                activo = true,
                estado = EstadoGlifo.BUSCANDO,
                titulo = "Mírame",
                nota = "Acércate un poco",
                intento = 0,
                retirado = false,
                exito = false,
                nombre = null,
                enPausa = false
            )
        }
    }

    /**
     * Otro canal tomó el turno: deja de analizar, pero conserva la cámara.
     * Ver [RostroLoginUiState.enPausa].
     */
    fun pausar() {
        val estado = _uiState.value
        if (!estado.activo || estado.enPausa) return
        buenosSeguidos = 0
        _uiState.update {
            it.copy(
                enPausa = true,
                estado = EstadoGlifo.REPOSO,
                titulo = "Entrar con tu rostro",
                nota = ""
            )
        }
    }

    /** El otro canal no era: se retoma el análisis con la cámara ya caliente. */
    fun reanudar() {
        val estado = _uiState.value
        if (!estado.activo || !estado.enPausa || estado.exito) return
        buenosSeguidos = 0
        ultimoRostroMs = SystemClock.elapsedRealtime()
        _uiState.update {
            it.copy(
                enPausa = false,
                estado = EstadoGlifo.BUSCANDO,
                titulo = "Mírame",
                nota = "",
                intento = 0,
                retirado = false
            )
        }
    }

    /** Apaga la cámara y vuelve al reposo. */
    fun detener() {
        buenosSeguidos = 0
        _uiState.update {
            it.copy(
                activo = false,
                estado = EstadoGlifo.REPOSO,
                titulo = "Entrar con tu rostro",
                nota = "",
                intento = 0,
                retirado = false,
                enPausa = false
            )
        }
    }

    fun analizarCuadro(frame: RostroFrame) {
        val motor = engine ?: return
        val estado = _uiState.value
        if (!estado.activo || estado.enPausa || estado.retirado || estado.exito) return
        if (estado.estado == EstadoGlifo.VERIFICANDO) return
        if (frame.gate.status != FaceGateStatus.READY) {
            actualizarDesdeGate(frame.gate)
            return
        }

        if (!analizando.compareAndSet(false, true)) return

        val bitmap = frame.bitmap ?: frame.bitmapProvider?.invoke()
        if (bitmap == null) {
            analizando.set(false)
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            try {
                when (val resultado = motor.procesar(bitmap)) {
                    is FaceEngine.Resultado.Ok -> evaluar(resultado.captura)
                    FaceEngine.Resultado.SinRostro -> sinRostro()
                    FaceEngine.Resultado.VariasCaras -> variasCaras()
                    is FaceEngine.Resultado.Error -> sinRostro()
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
            FaceGateStatus.NO_FACE -> sinRostro()
            FaceGateStatus.MULTIPLE_FACES -> variasCaras()
            FaceGateStatus.TOO_SMALL,
            FaceGateStatus.TOO_LARGE,
            FaceGateStatus.OUT_OF_FRAME -> {
                buenosSeguidos = 0
                _uiState.update {
                    it.copy(
                        estado = EstadoGlifo.BUSCANDO,
                        titulo = "Mírame",
                        nota = gate.detail ?: "Acomódate dentro del círculo"
                    )
                }
            }
            FaceGateStatus.ERROR -> {
                buenosSeguidos = 0
                _uiState.update {
                    it.copy(
                        estado = EstadoGlifo.BUSCANDO,
                        titulo = "Mírame",
                        nota = gate.detail ?: "No se pudo analizar el rostro"
                    )
                }
            }
            FaceGateStatus.READY -> Unit
        }
    }

    private fun sinRostro() {
        buenosSeguidos = 0
        val ahora = SystemClock.elapsedRealtime()

        // Si la cara se fue del cuadro, la siguiente persona empieza de cero.
        // Es una tableta compartida: sería absurdo que a quien llega después le
        // cuenten los intentos que gastó alguien más.
        if (ahora - ultimoRostroMs > AUSENCIA_PARA_REINICIAR_MS) {
            if (_uiState.value.intento > 0 || _uiState.value.retirado) {
                ultimoVectorEnviado = null
                _uiState.update {
                    it.copy(
                        intento = 0,
                        retirado = false,
                        estado = if (it.activo) EstadoGlifo.BUSCANDO else EstadoGlifo.REPOSO,
                        titulo = if (it.activo) "Mírame" else it.titulo,
                        nota = ""
                    )
                }
            }
        }

        if (_uiState.value.estado == EstadoGlifo.DETECTADO) {
            _uiState.update {
                it.copy(estado = EstadoGlifo.BUSCANDO, titulo = "Mírame", nota = "")
            }
        }
    }

    private fun variasCaras() {
        buenosSeguidos = 0
        _uiState.update {
            it.copy(estado = EstadoGlifo.BUSCANDO, titulo = "Mírame", nota = "Solo debe aparecer una persona")
        }
    }

    private fun evaluar(captura: FaceEngine.Captura) {
        ultimoRostroMs = SystemClock.elapsedRealtime()
        val calidad = captura.calidad
        val pista = motivoNoUsable(calidad)

        if (pista != null) {
            // Esto NO cuenta como fallo. Es un problema de encuadre, no de
            // identidad: la persona todavía no ha sido rechazada por nadie.
            buenosSeguidos = 0
            _uiState.update {
                it.copy(estado = EstadoGlifo.BUSCANDO, titulo = "Mírame", nota = pista)
            }
            return
        }

        buenosSeguidos++
        if (buenosSeguidos < CUADROS_BUENOS_SEGUIDOS) {
            _uiState.update {
                it.copy(estado = EstadoGlifo.DETECTADO, titulo = "Te veo", nota = "No te muevas")
            }
            return
        }

        // En el reintento, exigir que el cuadro sea de verdad distinto. Volver a
        // mandar prácticamente el mismo vector es repetir la misma pregunta.
        val anterior = ultimoVectorEnviado
        if (anterior != null) {
            val parecido = FaceEngine.similitudCoseno(captura.vector, anterior)
            if (parecido >= PARECIDO_MAXIMO_REINTENTO) {
                _uiState.update {
                    it.copy(
                        estado = EstadoGlifo.DETECTADO,
                        titulo = "Otra vez",
                        nota = "Muévete un poco"
                    )
                }
                return
            }
        }

        buenosSeguidos = 0
        verificar(captura)
    }

    private fun verificar(captura: FaceEngine.Captura) {
        val intentoActual = _uiState.value.intento + 1
        ultimoVectorEnviado = captura.vector

        _uiState.update {
            it.copy(
                estado = EstadoGlifo.VERIFICANDO,
                titulo = "Verificando",
                nota = "",
                intento = intentoActual
            )
        }

        viewModelScope.launch {
            val calidad = captura.calidad
            val peticion = FaceLoginRequest(
                VectorBase64 = VectorCodec.aBase64(captura.vector),
                Dims = captura.vector.size,
                Planta = authRepository.getPlanta(),
                Dispositivo = nombreDispositivo,
                Intento = intentoActual,
                Luma = calidad.luma,
                Nitidez = calidad.nitidez,
                ScoreDeteccion = calidad.scoreDeteccion,
                Yaw = calidad.yaw,
                Pitch = calidad.pitch,
                Roll = calidad.roll,
                RostroPx = calidad.rostroPx,
                MsDispositivo = captura.msTotal
            )

            authRepository.loginConRostro(peticion)
                .onSuccess { respuesta ->
                    val nombre = respuesta.User?.Nombre?.trim()?.substringBefore(" ")
                    _uiState.update {
                        it.copy(
                            estado = EstadoGlifo.EXITO,
                            titulo = if (nombre.isNullOrBlank()) "Adelante" else "Hola, $nombre",
                            nota = "Entrando…",
                            exito = true,
                            nombre = nombre
                        )
                    }
                }
                .onFailure { fallo ->
                    if (fallo is RostroNoHabilitadoException) {
                        // El módulo está apagado en el servidor. No es un
                        // rechazo de identidad, así que no cuesta un intento ni
                        // merece la sacudida de error: se apaga la cámara y la
                        // pantalla se queda con los canales que sí sirven.
                        _uiState.update {
                            it.copy(
                                activo = false,
                                deshabilitadoEnServidor = true,
                                estado = EstadoGlifo.REPOSO,
                                titulo = "Entrar con tu rostro",
                                nota = "",
                                intento = 0,
                                retirado = false
                            )
                        }
                        return@onFailure
                    }

                    val agotado = intentoActual >= MAX_INTENTOS
                    val mensaje = fallo.message?.trim().orEmpty()
                    _uiState.update { estado ->
                        estado.copy(
                            estado = EstadoGlifo.FALLO,
                            titulo = if (agotado) "No se pudo verificar" else "Intenta de nuevo",
                            nota = when {
                                agotado -> if (mensaje.isNotBlank()) mensaje else "Usa tu gafete"
                                mensaje.isNotBlank() -> mensaje
                                else -> "Acomódate un poco"
                            }
                        )
                    }

                    delay(PAUSA_TRAS_FALLO_MS)

                    if (agotado) {
                        // La cara se retira. Nada de diálogos ni regaños: el
                        // NFC y el lector nunca dejaron de escuchar.
                        _uiState.update {
                            it.copy(
                                activo = false,
                                retirado = true,
                                estado = EstadoGlifo.REPOSO,
                                titulo = "Entrar con tu rostro",
                                nota = "Acerca tu gafete o jala el gatillo"
                            )
                        }
                    } else {
                        buenosSeguidos = 0
                        _uiState.update {
                            it.copy(estado = EstadoGlifo.BUSCANDO, titulo = "Mírame", nota = "")
                        }
                    }
                }
        }
    }

    /**
     * Mismos mínimos que el enrolamiento, salvo el roll: al entrar la gente
     * llega con prisa y ladear un poco la cabeza no debe costar un intento.
     */
    private fun motivoNoUsable(c: FaceEngine.Calidad): String? = when {
        c.rostroPx < 120 -> "Acércate un poco"
        c.luma < 40f -> "Hace falta luz"
        c.nitidez < 50f -> "No te muevas"
        c.scoreDeteccion < 0.7f -> "Acomódate frente a la pantalla"
        else -> null
    }

    override fun onCleared() {
        engine?.cerrar()
        engine = null
        super.onCleared()
    }

    private companion object {
        /**
         * Tope de intentos por presencia. Ver la nota de seguridad en el
         * encabezado de la clase: no es impaciencia, es no volver a lanzar la
         * moneda en los casos limítrofes.
         */
        const val MAX_INTENTOS = 2

        /** Cuadros buenos seguidos antes de gastar un intento. */
        const val CUADROS_BUENOS_SEGUIDOS = 3

        /**
         * Arriba de esto el cuadro nuevo es prácticamente el mismo de antes y
         * volver a preguntarle al servidor daría la misma respuesta.
         */
        const val PARECIDO_MAXIMO_REINTENTO = 0.98f

        /** Sin cara durante este rato, el contador de intentos vuelve a cero. */
        const val AUSENCIA_PARA_REINICIAR_MS = 1_500L

        const val PAUSA_TRAS_FALLO_MS = 1_100L
    }
}
