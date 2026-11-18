package com.example.pmmovil.ui.caseta

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.CasetaDetalle
import com.example.pmmovil.data.model.CasetaPendiente
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.CasetaRepository
import com.example.pmmovil.ui.salidacaseta.SalidaFotosEnvio
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.pmmovil.data.camera.FotoParaSubir
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

data class CasetaUiState(
    val pending: List<CasetaPendiente> = emptyList(),
    val query: String = "",
    val selectedRelno: String? = null,
    val detail: CasetaDetalle? = null,
    val blockingMessage: String? = null,
    val isLoading: Boolean = true,
    val isProcessing: Boolean = false,
    /** Qué está haciendo la app mientras [isProcessing] ("Enviando fotos: 3 de 8"): el guardia nunca ve un spinner mudo. */
    val progresoEnvio: String? = null,
    val confirmVisible: Boolean = false,
    val confirmObservations: String = "",
    val currentUser: String = "",
    val puedeAgregar: Boolean = false,
    val puedeModificar: Boolean = false,
    val puedeBorrar: Boolean = false,
    val message: String? = null,
    val feedbackSeq: Int = 0,
    val localPhotoPaths: List<String> = emptyList()
) {
    val filteredPending: List<CasetaPendiente>
        get() {
            val clean = query.trim()
            if (clean.isBlank()) return pending
            return pending.filter { item ->
                item.relno.contains(clean, ignoreCase = true) ||
                    item.caja.contains(clean, ignoreCase = true) ||
                    item.cliente.contains(clean, ignoreCase = true) ||
                    item.usuAutoriza.contains(clean, ignoreCase = true)
            }
        }
}

@HiltViewModel
class CasetaViewModel @Inject constructor(
    private val repository: CasetaRepository,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CasetaUiState(
            currentUser = authRepository.getUsername().orEmpty(),
            puedeAgregar = authRepository.hasPermission(165, "Agregar"),
            puedeModificar = authRepository.hasPermission(165, "Modificar"),
            puedeBorrar = authRepository.hasPermission(165, "Borrar")
        )
    )
    val uiState: StateFlow<CasetaUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null

    // Cada foto se prepara para subir apenas se toma (ver FotoParaSubir: queda en disco, junto a la original), no hasta
    // que se presiona "Confirmar salida". Scope propio (no viewModelScope) para que un cierre de pantalla a medio preparar
    // no tire el trabajo si el usuario vuelve; un hilo a la vez para no competir con la cámara.
    private val photoPrepScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val preparando = mutableMapOf<String, Job>()

    // Las fotos suben en segundo plano en cuanto se toman, por lotes y como archivos; al confirmar solo se manda lo que
    // falte. Antes iban TODAS en una sola peticion, sin avance y sin forma de salir si la red se atoraba.
    private val envioFotos = SalidaFotosEnvio(repository, "PMM-CASETA")

    /** La confirmacion en curso: se puede cancelar sin perder las fotos. */
    private var confirmacionJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        searchJob?.cancel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedRelno = null,
                    detail = null,
                    blockingMessage = null,
                    confirmVisible = false,
                    confirmObservations = ""
                )
            }
            repository.getPendientes()
                .onSuccess { pending ->
                    _uiState.update {
                        it.copy(
                            pending = pending,
                            isLoading = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudieron cargar las salidas de caseta."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun setQuery(value: String) {
        _uiState.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(220)
            val clean = _uiState.value.query.trim()
            val exact = _uiState.value.pending.firstOrNull {
                it.relno.equals(clean, ignoreCase = true)
            }
            if (clean.length >= 6 && exact != null) {
                openDetail(exact.relno)
            }
        }
    }

    fun clearQuery() {
        _uiState.update { it.copy(query = "") }
    }

    fun onScannerInput(raw: String) {
        val relno = CasetaRules.normalizeScannedRelation(raw)
        if (relno.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "⚠ Lectura vacía. Vuelve a escanear la relación.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(query = relno) }
        openDetail(relno)
    }

    fun openDetail(relno: String) {
        val clean = relno.trim()
        if (clean.isBlank() || _uiState.value.isProcessing) return
        searchJob?.cancel()
        // Sin esto, fotos tomadas para una relacion que se abandono (se
        // cerro el detalle sin confirmar) se quedaban en el estado y podian
        // terminar adjuntas a la siguiente relacion que se abriera.
        clearLocalPhotos()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    selectedRelno = clean,
                    detail = null,
                    blockingMessage = null,
                    confirmVisible = false,
                    confirmObservations = "",
                    isLoading = true
                )
            }
            repository.getDetalle(clean)
                .onSuccess { response ->
                    _uiState.update {
                        if (response.success && response.detalle != null) {
                            it.copy(
                                detail = response.detalle,
                                blockingMessage = null,
                                isLoading = false,
                                message = "✓ Relación lista para confirmar salida.",
                                feedbackSeq = it.feedbackSeq + 1
                            )
                        } else {
                            it.copy(
                                detail = null,
                                blockingMessage = response.message?.takeIf(String::isNotBlank)
                                    ?: "No se puede confirmar la salida de esta relación.",
                                isLoading = false,
                                message = "⚠ ${response.message ?: "Relación no disponible para salida."}",
                                feedbackSeq = it.feedbackSeq + 1
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            detail = null,
                            blockingMessage = error.message ?: "No se pudo consultar la relación.",
                            isLoading = false,
                            message = "✗ ${error.message ?: "No se pudo consultar la relación."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun closeDetail() {
        clearLocalPhotos()
        _uiState.update {
            it.copy(
                selectedRelno = null,
                detail = null,
                blockingMessage = null,
                confirmVisible = false,
                confirmObservations = ""
            )
        }
    }

    fun requestConfirm() {
        if (!_uiState.value.puedeModificar) {
            _uiState.update {
                it.copy(
                    message = "⚠ No tienes permiso para confirmar salidas de relación.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        val detail = _uiState.value.detail
        if (!CasetaRules.isReadyForExit(detail)) {
            _uiState.update {
                it.copy(
                    message = "⚠ La relación no está completa para salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(confirmVisible = true, confirmObservations = "") }
    }

    fun dismissConfirm() {
        _uiState.update { it.copy(confirmVisible = false) }
    }

    fun updateConfirmObservations(value: String) {
        _uiState.update { it.copy(confirmObservations = value.take(500)) }
    }

    fun confirmSalida() {
        if (!_uiState.value.puedeModificar) {
            _uiState.update {
                it.copy(
                    confirmVisible = false,
                    message = "✗ No tienes permiso para confirmar salidas de relación.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        if (_uiState.value.isProcessing) return
        val detail = _uiState.value.detail ?: return
        val usuario = _uiState.value.currentUser
        if (usuario.isBlank()) {
            _uiState.update {
                it.copy(
                    confirmVisible = false,
                    message = "✗ No se encontró el usuario de sesión. Inicia sesión nuevamente.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        val rutas = _uiState.value.localPhotoPaths
        val observaciones = _uiState.value.confirmObservations.trim()

        confirmacionJob = viewModelScope.launch {
            _uiState.update {
                it.copy(isProcessing = true, confirmVisible = false, progresoEnvio = "Registrando la salida…")
            }

            val confirmacion = try {
                envioFotos.confirmarConFotos(
                    relno = detail.relno,
                    usuario = usuario,
                    observaciones = observaciones,
                    rutas = rutas,
                    alAvanzar = ::publicarProgreso
                )
            } catch (e: CancellationException) {
                // La cancelacion la informa cancelarConfirmacion(); aqui solo se deja pasar.
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progresoEnvio = null,
                        message = "✗ ${e.message ?: "No se pudieron preparar todas las fotos."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                return@launch
            }

            when (confirmacion) {
                // Las fotos no se pudieron preparar o enviar: nada se confirmo y siguen en la tableta.
                is SalidaFotosEnvio.Confirmacion.SinConfirmar -> _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progresoEnvio = null,
                        message = "✗ ${confirmacion.mensaje}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                is SalidaFotosEnvio.Confirmacion.Terminada -> confirmacion.resultado
                    .onSuccess { response ->
                        if (response.success) {
                            preparando.clear()
                            envioFotos.olvidarTodas()
                            rutas.forEach(FotoParaSubir::olvidar)
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    progresoEnvio = null,
                                    selectedRelno = null,
                                    detail = null,
                                    blockingMessage = null,
                                    confirmObservations = "",
                                    localPhotoPaths = emptyList(),
                                    message = "✓ ${response.message ?: "Salida confirmada correctamente."}",
                                    feedbackSeq = it.feedbackSeq + 1
                                )
                            }
                            refresh()
                        } else {
                            _uiState.update {
                                it.copy(
                                    isProcessing = false,
                                    progresoEnvio = null,
                                    message = "✗ ${response.message ?: "No se pudo confirmar la salida."}",
                                    feedbackSeq = it.feedbackSeq + 1
                                )
                            }
                        }
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                isProcessing = false,
                                progresoEnvio = null,
                                message = "✗ ${error.message ?: "Error al confirmar salida."}",
                                feedbackSeq = it.feedbackSeq + 1
                            )
                        }
                    }
            }
        }
    }

    private fun publicarProgreso(texto: String) = _uiState.update { it.copy(progresoEnvio = texto) }

    /** Corta el envio en curso sin perder nada: las fotos siguen en la tableta y se puede volver a confirmar. */
    fun cancelarConfirmacion() {
        val job = confirmacionJob ?: return
        if (!_uiState.value.isProcessing) return
        job.cancel()
        confirmacionJob = null
        _uiState.update {
            it.copy(
                isProcessing = false,
                progresoEnvio = null,
                message = "Envío cancelado. Tus fotos siguen guardadas en la tableta.",
                feedbackSeq = it.feedbackSeq + 1
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun addPhoto(path: String) {
        if (!_uiState.value.puedeAgregar) {
            runCatching { File(path).delete() }
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para agregar fotografías de salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(localPhotoPaths = it.localPhotoPaths + path) }
        // Se prepara ya y sube en segundo plano, en paralelo a que se sigan tomando mas fotos: para cuando se presione
        // "Confirmar salida" ya estan en el servidor (o casi) en vez de tener que mandarse de golpe en ese momento.
        preparando[path] = photoPrepScope.launch { FotoParaSubir.preparar(path) }
        subirFotosEnFondo()
    }

    /** Sube lo que falte de la relacion abierta mientras el guardia sigue tomando fotos. */
    private fun subirFotosEnFondo() {
        val relno = _uiState.value.detail?.relno?.takeIf(String::isNotBlank) ?: return
        if (_uiState.value.localPhotoPaths.isEmpty()) return
        envioFotos.subirEnSegundoPlano(viewModelScope, relno) { _uiState.value.localPhotoPaths }
    }

    fun removePhoto(path: String) {
        if (!_uiState.value.puedeBorrar) {
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para borrar fotografías de salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(localPhotoPaths = it.localPhotoPaths - path) }
        preparando.remove(path)?.cancel()
        envioFotos.olvidar(path)
        FotoParaSubir.olvidar(path)
        // Las que estaban despues se recorrieron una posicion: el envio de fondo las vuelve a mandar donde ahora van.
        subirFotosEnFondo()
    }

    /** Descarta fotos y preparaciones en curso de una relacion que se abandona sin confirmar. */
    private fun clearLocalPhotos() {
        envioFotos.detenerEnvioDeFondo()
        preparando.values.forEach { it.cancel() }
        preparando.clear()
        envioFotos.olvidarTodas()
        _uiState.value.localPhotoPaths.forEach(FotoParaSubir::olvidar)
        _uiState.update { it.copy(localPhotoPaths = emptyList()) }
    }

    override fun onCleared() {
        super.onCleared()
        photoPrepScope.cancel()
    }
}
