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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.pmmovil.data.camera.PhotoEncodingRules
import com.example.pmmovil.data.camera.PhotoPipeline
import com.example.pmmovil.data.ocr.applyExifOrientation
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

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

    // Comprime cada foto apenas se toma, no hasta que se presiona "Confirmar
    // salida": sin esto el usuario esperaba a que se comprimieran TODAS de golpe
    // justo en el peor momento. Scope propio (no viewModelScope) para que un
    // cierre de pantalla a medio comprimir no tire el trabajo si el usuario vuelve.
    private val photoEncodingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pendingEncodedPhotos = mutableMapOf<String, Deferred<String?>>()

    // Las fotos viajan al servidor por lotes (como en Entrada) y luego se confirma la salida; antes iban TODAS en una
    // sola peticion, sin avance y sin forma de salir si la red se atoraba.
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
                    codificar = ::encodePhotos,
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
                            pendingEncodedPhotos.clear()
                            envioFotos.olvidarTodas()
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
        // Arranca la compresion ya mismo, en paralelo a que se sigan tomando
        // mas fotos. Para cuando se presione "Confirmar salida" esto ya esta
        // listo (o casi) en vez de tener que hacerse de golpe en ese momento.
        pendingEncodedPhotos[path] = photoEncodingScope.async { encodePhotoForSave(path) }
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
        pendingEncodedPhotos.remove(path)?.cancel()
        envioFotos.olvidar(path)
    }

    /** Descarta fotos y compresiones en curso de una relacion que se abandona sin confirmar. */
    private fun clearLocalPhotos() {
        pendingEncodedPhotos.values.forEach { it.cancel() }
        pendingEncodedPhotos.clear()
        envioFotos.olvidarTodas()
        _uiState.update { it.copy(localPhotoPaths = emptyList()) }
    }

    private suspend fun encodePhotos(paths: List<String>): List<String> =
        withContext(Dispatchers.Default) {
            // Reutiliza la compresion que ya viene corriendo desde addPhoto();
            // solo si por algo no existe (ej. proceso reiniciado) se comprime
            // aqui mismo como respaldo, igual que antes.
            val encoded = paths.map { path ->
                val deferred = pendingEncodedPhotos.getOrPut(path) {
                    photoEncodingScope.async { encodePhotoForSave(path) }
                }
                val photo = deferred.await()
                if (photo.isNullOrBlank()) {
                    pendingEncodedPhotos.remove(path)?.cancel()
                }
                photo
            }
            PhotoEncodingRules.requireAllEncoded(encoded)
        }

    /**
     * Foto lista para el API.
     *
     * Antes vivia aqui su propia version del algoritmo: 1200 px de lado y un
     * bucle que bajaba la calidad de 80 hasta 40 con tal de caber en 400 KB.
     * Era el peor pipeline de la app -- 0.8 MP y, cuando el bucle se disparaba,
     * bloques de compresion encima de lo que se fue a documentar. Y el tope ni
     * se alcanzaba: la foto de referencia medida pesaba 101 KB.
     */
    private fun encodePhotoForSave(path: String): String? = PhotoPipeline.aBase64(path)

    override fun onCleared() {
        super.onCleared()
        photoEncodingScope.cancel()
    }
}
