package com.example.pmmovil.ui.salida

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CargarRelacionResponse
import com.example.pmmovil.data.model.SalidaEtiqueta
import com.example.pmmovil.data.model.SalidaRelacion
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.SalidaRepository
import com.example.pmmovil.data.signalr.WarehouseHubService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

/** Modo de carga: con relación (normal) o sin relación (entrada directa / transbordo). */
enum class SalidaMode { NORMAL, SIN_RELACION }

/** Etapa de la máquina de estados de la pantalla. */
enum class SalidaStage { IDLE, LOADING, ACTIVE, DESCARGA, BLOCKED, FINISHED }

/** Paso obligatorio de carga: QR de caseta y luego un solo bulto. */
enum class SalidaCaptureStep { CASETA_QR, BULTO }

/** Cómo entra el código: lector físico/bluetooth (HID) o cámara integrada. */
enum class SalidaCaptureMode { PHYSICAL, CAMERA }

/** Flash de pantalla para feedback periférico (sin mirar). */
enum class SalidaFlash { NONE, OK, ERROR }

enum class SalidaConfirmKind { NEUTRAL, WARNING, DANGER }

enum class SalidaConfirmAction { REQUEST_AUTH, PAUSE, CANCEL }

data class SalidaBloqueo(
    val relno: String,
    val cliente: String,
    val caja: String,
    val motivo: String,
    val etiquetasBloqueadas: Int
)

data class SalidaConfirm(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val kind: SalidaConfirmKind,
    val action: SalidaConfirmAction
)

data class SalidaUiState(
    val mode: SalidaMode = SalidaMode.NORMAL,
    val stage: SalidaStage = SalidaStage.IDLE,
    val captureMode: SalidaCaptureMode = SalidaCaptureMode.PHYSICAL,
    val captureStep: SalidaCaptureStep = SalidaCaptureStep.CASETA_QR,
    val casetaQrToken: String? = null,
    val casetaQrSecondsRemaining: Int = 0,
    val relacion: SalidaRelacion? = null,
    val etiquetas: List<SalidaEtiqueta> = emptyList(),
    val etiquetasDescarga: List<SalidaEtiqueta> = emptyList(),
    val relnoInput: String = "",
    val entradaInput: String = "",
    val estatusMsg: String = "",
    val isLoadingRelacion: Boolean = false,
    val isProcessingScan: Boolean = false,
    val isProcessingAction: Boolean = false,
    val bloqueo: SalidaBloqueo? = null,
    val confirm: SalidaConfirm? = null,
    val errorBanner: String? = null,
    val flash: SalidaFlash = SalidaFlash.NONE,
    /** Se incrementa en cada evento de feedback para que la UI dispare tono/vibración. */
    val feedbackSeq: Int = 0,
    /** Última etiqueta escaneada con éxito (para animación de transición). */
    val lastScanned: String? = null,
    /** Mensaje para snackbar; prefijos ✓ ⚠ ✗ definen el color (ver PmmSnackbarHost). */
    val message: String? = null,
    val usuario: String = ""
) {
    val total: Int get() = etiquetas.size
    val escaneados: Int get() = etiquetas.count { it.escaneado }
    val pendientes: Int get() = total - escaneados
    val progreso: Float get() = if (total == 0) 0f else escaneados.toFloat() / total
    val todoEscaneado: Boolean get() = total > 0 && escaneados == total
    val descargaTotal: Int get() = etiquetasDescarga.size
    val descargados: Int get() = etiquetasDescarga.count { it.escaneado }
}

@HiltViewModel
class SalidaViewModel @Inject constructor(
    private val repository: SalidaRepository,
    private val authRepository: AuthRepository,
    private val hubService: WarehouseHubService
) : ViewModel() {

    private val _state = MutableStateFlow(SalidaUiState())
    val uiState: StateFlow<SalidaUiState> = _state.asStateFlow()

    private val recentScans = mutableMapOf<String, Long>()
    private var lastAcceptedAt = 0L
    private var flashJob: Job? = null
    private var bannerJob: Job? = null
    private var casetaQrTimerJob: Job? = null

    init {
        val usuario = authRepository.getUsername()
            ?: authRepository.getName()
            ?: ""
        _state.update { it.copy(usuario = usuario) }

        // Un supervisor desbloqueó la relación que este cargador tiene bloqueada: se le avisa en vivo.
        viewModelScope.launch {
            hubService.salidaDesbloqueadaFlow.collect { aviso ->
                val bloqueo = _state.value.bloqueo
                if (_state.value.stage == SalidaStage.BLOCKED && bloqueo != null && bloqueo.relno == aviso.relno) {
                    limpiar()
                    toast("✓ El supervisor desbloqueó la relación ${aviso.relno}. Escanéala de nuevo para continuar.")
                }
            }
        }
    }

    // ── Configuración ────────────────────────────────────────────────────────

    fun setCaptureMode(mode: SalidaCaptureMode) {
        _state.update { it.copy(captureMode = mode) }
    }

    fun setMode(mode: SalidaMode) {
        if (_state.value.stage != SalidaStage.IDLE) return
        _state.update { it.copy(mode = mode, relnoInput = "", entradaInput = "", estatusMsg = "") }
    }

    fun onRelnoInput(value: String) = _state.update { it.copy(relnoInput = value) }
    fun onEntradaInput(value: String) = _state.update { it.copy(entradaInput = value) }

    fun submitRelacion() = loadRelacion(_state.value.relnoInput)
    fun submitEntrada() = loadEntrada(_state.value.entradaInput)

    // ── Entrada unificada del escáner (HID/bluetooth + cámara + manual) ────────

    fun onScannerInput(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        when (_state.value.stage) {
            SalidaStage.IDLE ->
                if (_state.value.mode == SalidaMode.NORMAL) loadRelacion(code) else loadEntrada(code)
            SalidaStage.ACTIVE ->
                if (_state.value.captureStep == SalidaCaptureStep.CASETA_QR) {
                    onCasetaQrScan(code)
                } else {
                    onScan(code)
                }
            SalidaStage.DESCARGA -> onScanDescarga(code)
            else -> Unit // LOADING / BLOCKED / FINISHED ignoran escaneos
        }
    }

    // ── Cargar relación (modo normal) ──────────────────────────────────────────

    private fun loadRelacion(raw: String) {
        val value = raw.trim()
        if (value.length < 5) return
        if (_state.value.isLoadingRelacion) return
        if (looksLikeCasetaQr(value)) {
            loadRelacionFromCasetaQr(value)
            return
        }
        if (value.length > 15) {
            toast("✗ Número de relación inválido.")
            return
        }
        if (value.getOrNull(2)?.uppercaseChar() != 'I') {
            toast("✗ No es un número de relación válido.")
            _state.update { it.copy(relnoInput = "") }
            return
        }
        if (value.last().uppercaseChar() == 'P') {
            toast("⚠ Esta relación está en proceso de captura.")
            _state.update { it.copy(relnoInput = "") }
            return
        }

        val parts = value.split("-")
        val relno = parts[0]
        val version = parts.getOrNull(1) ?: "1"

        _state.update { it.copy(relnoInput = value, stage = SalidaStage.LOADING, isLoadingRelacion = true, estatusMsg = "") }
        viewModelScope.launch {
            repository.cargarRelacion(relno, version)
                .onSuccess { res ->
                    if (!res.success) {
                        _state.update {
                            it.copy(
                                stage = SalidaStage.IDLE,
                                isLoadingRelacion = false,
                                relnoInput = "",
                                message = "✗ ${res.message ?: "No se pudo cargar la relación."}"
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            isLoadingRelacion = false,
                            relacion = res.relacion,
                            etiquetas = res.etiquetas ?: emptyList()
                        )
                    }
                    procesarEstadoRelacion(res)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            stage = SalidaStage.IDLE,
                            isLoadingRelacion = false,
                            relnoInput = "",
                            message = networkMessage(error, "Error al cargar la relación.")
                        )
                    }
                }
        }
    }

    private fun looksLikeCasetaQr(value: String): Boolean =
        value.length > 15 || (value.contains('-') && value.contains('/'))

    private fun loadRelacionFromCasetaQr(casetaId: String) {
        _state.update {
            it.copy(
                relnoInput = casetaId,
                stage = SalidaStage.LOADING,
                isLoadingRelacion = true,
                estatusMsg = ""
            )
        }

        viewModelScope.launch {
            repository.validarCasetaQr("", "", casetaId)
                .onSuccess { qr ->
                    if (!qr.success || qr.relacion == null || qr.autorizacionToken.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                stage = SalidaStage.IDLE,
                                isLoadingRelacion = false,
                                relnoInput = "",
                                message = "✕ ${qr.message ?: "No se pudo validar el QR de caseta."}"
                            )
                        }
                        return@onSuccess
                    }

                    val carga = CargarRelacionResponse(
                        success = qr.success,
                        message = qr.message,
                        relacion = qr.relacion,
                        etiquetas = qr.etiquetas,
                        estatusCarga = qr.estatusCarga,
                        totBultos = qr.totBultos,
                        versionHoja = qr.versionHoja
                    )
                    _state.update {
                        it.copy(
                            isLoadingRelacion = false,
                            relacion = qr.relacion,
                            etiquetas = qr.etiquetas ?: emptyList()
                        )
                    }
                    procesarEstadoRelacion(carga, qr.autorizacionToken, qr.expiraEnSegundos)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            stage = SalidaStage.IDLE,
                            isLoadingRelacion = false,
                            relnoInput = "",
                            message = networkMessage(error, "Error al validar el QR de caseta.")
                        )
                    }
                }
        }
    }

    private fun procesarEstadoRelacion(
        res: CargarRelacionResponse,
        initialQrToken: String? = null,
        initialQrSeconds: Int = 0
    ) {
        val rel = res.relacion
        val estatusCarga = rel?.estatusCarga?.takeIf(String::isNotBlank) ?: res.estatusCarga.orEmpty()

        if (rel == null) {
            limpiar()
            toast("✗ No se pudo leer la relación.")
            return
        }
        val estatusMercancia = rel.estatus
        if (estatusMercancia != "COMPLETA") {
            if (estatusMercancia in ESTATUS_QUE_PERMITEN_CAPTURA) {
                // Igual que el legacy: con diferencia de bultos se puede escanear, y sin etiquetas se puede pedir autorización.
                activarCapturaCarga(null, 0)
                _state.update { it.copy(estatusMsg = estatusMercancia) }
            } else {
                // Cualquier otro estado ("INFORMACION INCORRECTA…", "EN PROCESO", "ERROR…"): el legacy esconde el escáner
                // y el servidor ya no dejó la relación tomada. Solo se muestra el motivo.
                limpiar()
                toast("✗ ${estatusMercancia.ifBlank { "Estado de la relación desconocido." }}")
            }
            return
        }

        if (estatusCarga in setOf("CARGADA", "AUTORIZADA", "SALIDA", "DEMASIADO RAPIDO")) {
            _state.update { it.copy(stage = SalidaStage.IDLE, relnoInput = "", message = "⚠ Esta relación ya fue cargada.") }
            return
        }

        if (estatusCarga == "BLOQUEO" || estatusCarga == "BLOQUEO REIMPRESION") {
            activarBloqueo(
                rel,
                if (estatusCarga == "BLOQUEO REIMPRESION")
                    "Relación bloqueada por reimpresión. Contacta al supervisor."
                else
                    "Escaneo rápido detectado. Contacta al supervisor.",
                _state.value.etiquetas.count(::esBloqueada)
            )
            return
        }

        if (estatusCarga == "EN ESPERA") {
            viewModelScope.launch {
                repository.getEtiquetasDescarga(rel.relno)
                    .onSuccess { d ->
                        if (d.success && !d.etiquetas.isNullOrEmpty()) {
                            _state.update {
                                it.copy(
                                    etiquetasDescarga = d.etiquetas,
                                    stage = SalidaStage.DESCARGA,
                                    message = "⚠ Hay ${d.etiquetas.size} bulto(s) que ya no pertenecen a la relación. Bájalos del camión y escanéalos para continuar."
                                )
                            }
                        } else {
                            activarCapturaCarga(null, 0)
                        }
                    }
                    .onFailure { activarCapturaCarga(null, 0) }
            }
        } else {
            activarCapturaCarga(initialQrToken, initialQrSeconds)
        }
    }

    // ── Cargar sin relación (entrada directa / transbordo) ─────────────────────

    private fun loadEntrada(raw: String) {
        val value = raw.trim()
        if (value.length < 5) return
        if (_state.value.isLoadingRelacion) return
        if (value.getOrNull(2)?.uppercaseChar() != 'C') {
            toast("✗ No es un número de entrada válido.")
            _state.update { it.copy(entradaInput = "") }
            return
        }

        _state.update { it.copy(entradaInput = value, stage = SalidaStage.LOADING, isLoadingRelacion = true, estatusMsg = "") }
        viewModelScope.launch {
            repository.cargarSinRelacion(value)
                .onSuccess { res ->
                    if (!res.success) {
                        _state.update {
                            it.copy(
                                stage = SalidaStage.IDLE,
                                isLoadingRelacion = false,
                                entradaInput = "",
                                message = "✗ ${res.message ?: "No se encontró la entrada."}"
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            isLoadingRelacion = false,
                            relacion = res.relacion,
                            etiquetas = res.etiquetas ?: emptyList()
                        )
                    }
                    val rel = res.relacion
                    when {
                        rel?.estatus != "COMPLETA" -> {
                            // El legacy esconde el escáner de «sin relación» cuando la entrada no está lista.
                            limpiar()
                            toast("✗ ${rel?.estatus?.ifBlank { null } ?: "La entrada no está disponible."}")
                        }
                        rel.estatusCarga == "BLOQUEO" ->
                            activarBloqueo(rel, "Escaneo rápido detectado. Contacta al supervisor.", 0)
                        rel.estatusCarga == "CARGADA" -> {
                            toast("⚠ Ya fue cargada.")
                            limpiar()
                        }
                        else -> activarCapturaCarga(null, 0)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            stage = SalidaStage.IDLE,
                            isLoadingRelacion = false,
                            entradaInput = "",
                            message = networkMessage(error, "Error al cargar la entrada.")
                        )
                    }
                }
        }
    }

    // ── Escaneo de bultos (carga) ──────────────────────────────────────────────

    private fun activarCapturaCarga(token: String?, seconds: Int) {
        casetaQrTimerJob?.cancel()
        val tieneAutorizacion = !token.isNullOrBlank() && seconds > 0
        _state.update {
            it.copy(
                stage = SalidaStage.ACTIVE,
                captureStep = if (tieneAutorizacion) SalidaCaptureStep.BULTO else SalidaCaptureStep.CASETA_QR,
                casetaQrToken = token?.takeIf { tieneAutorizacion },
                casetaQrSecondsRemaining = if (tieneAutorizacion) seconds else 0,
                lastScanned = if (tieneAutorizacion) null else it.lastScanned
            )
        }
        if (tieneAutorizacion) iniciarTemporizadorCasetaQr(seconds)
    }

    private fun iniciarTemporizadorCasetaQr(seconds: Int) {
        val deadline = System.currentTimeMillis() + seconds * 1_000L
        casetaQrTimerJob = viewModelScope.launch {
            while (true) {
                val remainingMs = deadline - System.currentTimeMillis()
                val remainingSeconds = ((remainingMs + 999L) / 1_000L).toInt()
                if (remainingSeconds <= 0) break
                _state.update {
                    if (it.captureStep == SalidaCaptureStep.BULTO) {
                        it.copy(casetaQrSecondsRemaining = remainingSeconds)
                    } else {
                        it
                    }
                }
                delay(250)
            }

            _state.update {
                if (it.captureStep == SalidaCaptureStep.BULTO) {
                    it.copy(
                        captureStep = SalidaCaptureStep.CASETA_QR,
                        casetaQrToken = null,
                        casetaQrSecondsRemaining = 0,
                        message = "⚠ Se vencieron los 10 segundos. Escanea otra vez el QR de caseta."
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun clearCasetaQrAuthorization() {
        casetaQrTimerJob?.cancel()
        casetaQrTimerJob = null
        _state.update {
            it.copy(
                captureStep = SalidaCaptureStep.CASETA_QR,
                casetaQrToken = null,
                casetaQrSecondsRemaining = 0
            )
        }
    }

    private fun onCasetaQrScan(code: String) {
        val rel = _state.value.relacion ?: return
        if (_state.value.isProcessingScan) return
        if (!acceptScan(code)) return

        _state.update { it.copy(isProcessingScan = true) }
        val relno = rel.relno
        val entrada = if (_state.value.mode == SalidaMode.SIN_RELACION) relno else ""
        viewModelScope.launch {
            repository.validarCasetaQr(
                relno = if (_state.value.mode == SalidaMode.NORMAL) relno else "",
                entrada = entrada,
                casetaId = code
            )
                .onSuccess { qr ->
                    _state.update { it.copy(isProcessingScan = false) }
                    if (qr.success && !qr.autorizacionToken.isNullOrBlank()) {
                        activarCapturaCarga(qr.autorizacionToken, qr.expiraEnSegundos)
                        flash(SalidaFlash.OK)
                        toast("✓ QR válido. Escanea el bulto en ${qr.expiraEnSegundos} segundos.")
                    } else {
                        flash(SalidaFlash.ERROR)
                        showErrorBanner(qr.message ?: "QR de caseta no válido")
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isProcessingScan = false) }
                    flash(SalidaFlash.ERROR)
                    toast(networkMessage(error, "Error de conexión al validar el QR."))
                }
        }
    }

    private fun onScan(code: String) {
        val rel = _state.value.relacion ?: return
        if (_state.value.isProcessingScan) return
        if (!acceptScan(code)) return

        val autorizacionToken = _state.value.casetaQrToken
        if (autorizacionToken.isNullOrBlank() || _state.value.casetaQrSecondsRemaining <= 0) {
            clearCasetaQrAuthorization()
            flash(SalidaFlash.ERROR)
            toast("⚠ Primero escanea el QR de caseta y después el bulto.")
            return
        }

        val existing = _state.value.etiquetas.firstOrNull { it.textoEtiqueta == code }
        if (existing?.escaneado == true) {
            flash(SalidaFlash.OK)
            toast("✓ Bulto ya escaneado")
            return
        }

        _state.update { it.copy(isProcessingScan = true) }
        viewModelScope.launch {
            val result = if (_state.value.mode == SalidaMode.NORMAL) {
                repository.scan(rel.relno, code, autorizacionToken)
            } else {
                repository.scanSinRelacion(rel.relno, code, autorizacionToken)
            }
            result
                .onSuccess { res ->
                    _state.update { it.copy(isProcessingScan = false) }
                    when (res.tipo?.uppercase()) {
                        "QR_EXPIRADO", "SECUENCIA_INVALIDA", "QR_REQUERIDO" -> {
                            clearCasetaQrAuthorization()
                            flash(SalidaFlash.ERROR)
                            toast("⚠ ${res.message ?: "Escanea de nuevo el QR de caseta."}")
                        }
                        "NO_PERTENECE" -> {
                            clearCasetaQrAuthorization()
                            flash(SalidaFlash.ERROR)
                            showErrorBanner("NO PERTENECE")
                        }
                        "ETIQUETA_OTRA_RELACION" -> {
                            clearCasetaQrAuthorization()
                            flash(SalidaFlash.ERROR)
                            showErrorBanner(res.message ?: "El bulto pertenece a otra relación")
                        }
                        "BLOQUEADO" -> {
                            clearCasetaQrAuthorization()
                            marcarEscaneado(code, nuevoEstatus = "CARGADO")
                            activarBloqueo(
                                rel,
                                res.bloqueoMotivo ?: "Escaneo rápido detectado. Contacta al supervisor.",
                                res.etiquetasBloqueadas ?: _state.value.etiquetas.count(::esBloqueada)
                            )
                        }
                        else -> if (res.success) {
                            clearCasetaQrAuthorization()
                            marcarEscaneado(code)
                            flash(SalidaFlash.OK)
                            _state.update { it.copy(lastScanned = code) }
                            if (_state.value.todoEscaneado) {
                                toast("✓ ¡Completo! ${_state.value.escaneados}/${_state.value.total} — Finaliza ahora")
                            }
                        } else {
                            clearCasetaQrAuthorization()
                            flash(SalidaFlash.ERROR)
                            toast("✗ ${res.message ?: "Error al escanear."}")
                        }
                    }
                }
                .onFailure {
                    _state.update { it.copy(isProcessingScan = false) }
                    flash(SalidaFlash.ERROR)
                    toast(networkMessage(it, "Error de conexión al escanear."))
                }
        }
    }

    // ── Escaneo de descarga (re-escaneo de bultos previos) ─────────────────────

    private fun onScanDescarga(code: String) {
        val rel = _state.value.relacion ?: return
        if (_state.value.isProcessingScan) return
        if (!acceptScan(code)) return

        val existing = _state.value.etiquetasDescarga.firstOrNull { it.textoEtiqueta == code }
        if (existing?.escaneado == true) return

        _state.update { it.copy(isProcessingScan = true) }
        viewModelScope.launch {
            repository.descargar(rel.relno, code)
                .onSuccess { res ->
                    _state.update { it.copy(isProcessingScan = false) }
                    if (res.success) {
                        _state.update { st ->
                            st.copy(
                                etiquetasDescarga = st.etiquetasDescarga.map {
                                    if (it.textoEtiqueta == code) it.copy(escaneado = true) else it
                                }
                            )
                        }
                        flash(SalidaFlash.OK)
                        if (_state.value.etiquetasDescarga.all { it.escaneado }) {
                            toast("✓ ¡Todos descargados! Finaliza la descarga.")
                        }
                    } else {
                        flash(SalidaFlash.ERROR)
                        showErrorBanner("NO SE DESCARGA")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isProcessingScan = false) }
                    flash(SalidaFlash.ERROR)
                    toast(networkMessage(it, "Error de conexión."))
                }
        }
    }

    // ── Finalizar carga ────────────────────────────────────────────────────────

    fun attemptFinalize() {
        val rel = _state.value.relacion ?: return
        val sinRelacion = _state.value.mode == SalidaMode.SIN_RELACION
        if (_state.value.pendientes > 0 && sinRelacion) {
            // Una entrada directa/transbordo no tiene supervisor que autorice: hay que escanear todo (legacy).
            toast("⚠ Faltan ${_state.value.pendientes} bulto(s) por escanear.")
            return
        }
        if (_state.value.pendientes > 0) {
            _state.update {
                it.copy(
                    confirm = SalidaConfirm(
                        title = "Faltan bultos",
                        message = "Aún faltan ${it.pendientes} bulto(s) sin escanear. ¿Deseas solicitar autorización para continuar?",
                        confirmLabel = "Solicitar autorización",
                        kind = SalidaConfirmKind.WARNING,
                        action = SalidaConfirmAction.REQUEST_AUTH
                    )
                )
            }
            return
        }
        if (rel.estatus != "COMPLETA") {
            _state.update {
                it.copy(
                    confirm = SalidaConfirm(
                        title = "Requiere autorización",
                        message = "Esta relación requiere autorización del supervisor para ser finalizada.",
                        confirmLabel = "Solicitar autorización",
                        kind = SalidaConfirmKind.WARNING,
                        action = SalidaConfirmAction.REQUEST_AUTH
                    )
                )
            }
            return
        }

        _state.update { it.copy(isProcessingAction = true) }
        viewModelScope.launch {
            // La entrada de «sin relación» no es una relación: tiene su propio cierre en el servidor.
            val resultado = if (sinRelacion) repository.finalizarSinRelacion(rel.relno) else repository.finalizar(rel.relno)
            resultado
                .onSuccess { res ->
                    _state.update { it.copy(isProcessingAction = false) }
                    if (res.success) {
                        emitFeedback()
                        _state.update { it.copy(stage = SalidaStage.FINISHED) }
                    } else {
                        toast("✗ ${res.message ?: "No se pudo finalizar."}")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isProcessingAction = false) }
                    toast(networkMessage(it, "Error al finalizar."))
                }
        }
    }

    fun finalizeDescarga() {
        val rel = _state.value.relacion ?: return
        val pendientes = _state.value.etiquetasDescarga.count { !it.escaneado }
        if (pendientes > 0) {
            toast("✗ Faltan $pendientes bulto(s) por descargar.")
            return
        }
        _state.update { it.copy(isProcessingAction = true) }
        viewModelScope.launch {
            repository.finalizarDescarga(rel.relno)
                .onSuccess { res ->
                    _state.update { it.copy(isProcessingAction = false) }
                    if (res.success) {
                        _state.update {
                            it.copy(
                                etiquetasDescarga = emptyList(),
                                message = "✓ Descarga completada. Continúa con la carga."
                            )
                        }
                        activarCapturaCarga(null, 0)
                    } else {
                        toast("✗ ${res.message ?: "Error al finalizar descarga."}")
                    }
                }
                .onFailure {
                    _state.update { it.copy(isProcessingAction = false) }
                    toast(networkMessage(it, "Error al finalizar descarga."))
                }
        }
    }

    // ── Acciones con confirmación (pausar / cancelar / autorización) ───────────

    fun requestPause() {
        _state.update {
            it.copy(
                confirm = SalidaConfirm(
                    title = "Pausar operación",
                    message = "Se guardará el progreso y la relación quedará disponible para continuar después.",
                    confirmLabel = "Sí, pausar",
                    kind = SalidaConfirmKind.NEUTRAL,
                    action = SalidaConfirmAction.PAUSE
                )
            )
        }
    }

    fun requestCancel() {
        _state.update {
            it.copy(
                confirm = SalidaConfirm(
                    title = "Cancelar operación",
                    message = "Se liberará la relación sin guardar cambios.",
                    confirmLabel = "Sí, cancelar",
                    kind = SalidaConfirmKind.DANGER,
                    action = SalidaConfirmAction.CANCEL
                )
            )
        }
    }

    fun dismissConfirm() = _state.update { it.copy(confirm = null) }

    fun confirmAction() {
        val action = _state.value.confirm?.action ?: return
        _state.update { it.copy(confirm = null) }
        when (action) {
            SalidaConfirmAction.REQUEST_AUTH -> pedirAutorizacion()
            SalidaConfirmAction.PAUSE -> pausar()
            SalidaConfirmAction.CANCEL -> cancelar()
        }
    }

    private fun pedirAutorizacion() {
        val rel = _state.value.relacion ?: return
        _state.update { it.copy(isProcessingAction = true) }
        viewModelScope.launch {
            repository.solicitarAutorizacion(rel.relno)
                .onSuccess {
                    _state.update { it.copy(isProcessingAction = false, message = "✓ Solicitud de autorización enviada al supervisor.") }
                    limpiar()
                }
                .onFailure {
                    _state.update { it.copy(isProcessingAction = false) }
                    toast(networkMessage(it, "Error al solicitar autorización."))
                }
        }
    }

    private fun pausar() {
        val rel = _state.value.relacion ?: return
        _state.update { it.copy(isProcessingAction = true) }
        viewModelScope.launch {
            repository.pausar(rel.relno)
                .onSuccess {
                    _state.update { it.copy(isProcessingAction = false, message = "Operación pausada.") }
                    limpiar()
                }
                .onFailure {
                    _state.update { it.copy(isProcessingAction = false) }
                    toast(networkMessage(it, "Error al pausar."))
                }
        }
    }

    private fun cancelar() {
        val rel = _state.value.relacion ?: return
        _state.update { it.copy(isProcessingAction = true) }
        viewModelScope.launch {
            // Si la relación llegó pausada (EN ESPERA) se le devuelve ese estado, como hacía el legacy al cancelar.
            repository.cancelar(rel.relno, rel.estatusCarga.takeIf { it == "EN ESPERA" })
                .onSuccess {
                    _state.update { it.copy(isProcessingAction = false, message = "Operación cancelada.") }
                    limpiar()
                }
                .onFailure {
                    _state.update { it.copy(isProcessingAction = false) }
                    toast(networkMessage(it, "Error al cancelar."))
                }
        }
    }

    // ── Reinicio ────────────────────────────────────────────────────────────────

    fun limpiar() {
        casetaQrTimerJob?.cancel()
        casetaQrTimerJob = null
        recentScans.clear()
        lastAcceptedAt = 0L
        _state.update {
            SalidaUiState(
                mode = it.mode,
                captureMode = it.captureMode,
                usuario = it.usuario
            )
        }
    }

    fun nuevaOperacion() = limpiar()

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private fun marcarEscaneado(code: String, nuevoEstatus: String? = null) {
        _state.update { st ->
            st.copy(
                etiquetas = st.etiquetas.map {
                    if (it.textoEtiqueta == code) {
                        it.copy(escaneado = true, estatus = nuevoEstatus ?: it.estatus)
                    } else {
                        it
                    }
                }
            )
        }
    }

    private fun activarBloqueo(rel: SalidaRelacion?, motivo: String, etiquetasBloqueadas: Int) {
        flash(SalidaFlash.ERROR)
        _state.update {
            it.copy(
                stage = SalidaStage.BLOCKED,
                isProcessingScan = false,
                isProcessingAction = false,
                errorBanner = null,
                bloqueo = SalidaBloqueo(
                    relno = rel?.relno ?: it.relnoInput,
                    cliente = rel?.cliente.orEmpty(),
                    caja = rel?.caja.orEmpty(),
                    motivo = motivo,
                    etiquetasBloqueadas = etiquetasBloqueadas
                )
            )
        }
    }

    /** Replica la heurística del legacy/Angular para detectar etiquetas bloqueadas por historial. */
    private fun esBloqueada(et: SalidaEtiqueta): Boolean {
        val h = et.historial ?: ""
        val partes = h.split(",")
        val ultimo = partes.getOrNull(partes.size - 2) ?: ""
        return (h.contains("BLOQUEO") && !h.contains("DESBLOQUEO")) ||
            (partes.size > 4 && ultimo.contains("BLOQUEO") && !ultimo.contains("DESBLOQUEO"))
    }

    /** Dedupe anti-rebote: el mismo código <800ms o cualquiera <350ms se ignora (clave para cámara continua). */
    private fun acceptScan(code: String): Boolean {
        val now = System.currentTimeMillis()
        val sameTooSoon = now - (recentScans[code] ?: 0L) < 800L
        val anyTooSoon = now - lastAcceptedAt < 350L
        if (sameTooSoon || anyTooSoon) return false
        recentScans[code] = now
        lastAcceptedAt = now
        recentScans.entries.removeAll { now - it.value > 5_000L }
        return true
    }

    private fun flash(kind: SalidaFlash) {
        flashJob?.cancel()
        _state.update { it.copy(flash = kind, feedbackSeq = it.feedbackSeq + 1) }
        flashJob = viewModelScope.launch {
            delay(380)
            _state.update { it.copy(flash = SalidaFlash.NONE) }
        }
    }

    private fun emitFeedback() = _state.update { it.copy(feedbackSeq = it.feedbackSeq + 1) }

    private fun showErrorBanner(text: String) {
        bannerJob?.cancel()
        _state.update { it.copy(errorBanner = text) }
        bannerJob = viewModelScope.launch {
            delay(2_200)
            _state.update { it.copy(errorBanner = null) }
        }
    }

    private fun toast(message: String) = _state.update { it.copy(message = message) }

    private fun networkMessage(error: Throwable, fallback: String): String =
        if (error is IOException) "✗ Sin conexión con el servidor." else "✗ ${error.message ?: fallback}"

    private companion object {
        /** Estados de mercancía con los que el legacy deja trabajar al cargador además de COMPLETA. */
        val ESTATUS_QUE_PERMITEN_CAPTURA = setOf("DIFERENCIA EN BULTOS-ETIQUETAS", "NO DISP ETIQUETAS EN MCIA")
    }

    override fun onCleared() {
        flashJob?.cancel()
        bannerJob?.cancel()
        casetaQrTimerJob?.cancel()
        super.onCleared()
    }
}
