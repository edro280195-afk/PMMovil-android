package com.example.pmmovil.ui.camera

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.model.FotoRevisionDto
import com.example.pmmovil.data.model.FotoSyncEntity
import com.example.pmmovil.data.preferences.CameraPreferences
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.data.sync.OfflineSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

data class EvidenceUiState(
    val remotePhotos: List<FotoRevisionDto> = emptyList(),
    val localPhotos: List<FotoSyncEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
    // Claves ("local-<id>" / "remote-<id>") de fotos que en este momento
    // están en proceso de eliminarse en el servidor: mientras una clave esté
    // aquí, su miniatura queda bloqueada (spinner propio) para no permitir
    // reintentos concurrentes -- eso era justo lo que causaba que hubiera
    // que tocar "Eliminar" 2-3 veces antes de que de verdad desapareciera.
    val deletingKeys: Set<String> = emptySet(),
    // Claves que terminaron en error al eliminar: se les muestra un badge de
    // error + botón "Reintentar" en vez de fallar en silencio.
    val errorKeys: Set<String> = emptySet()
)

fun localPhotoKey(id: Int) = "local-$id"
fun remotePhotoKey(id: Int) = "remote-$id"

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val fotoDao: FotoDao,
    private val revisionRepository: RevisionRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: OfflineSyncScheduler,
    private val cameraPreferences: CameraPreferences,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(EvidenceUiState())
    val uiState: StateFlow<EvidenceUiState> = _uiState.asStateFlow()

    private val _shutterSide = MutableStateFlow(cameraPreferences.getShutterSide())
    val shutterSide: StateFlow<CameraPreferences.ShutterSide> = _shutterSide.asStateFlow()

    private var pendingPhotosJob: Job? = null
    private var remotePhotosJob: Job? = null

    fun start(crefAgrupada: String, cnofact: String) {
        limpiarHuerfanasDeVersionesAnteriores()
        loadRemotePhotos(crefAgrupada, cnofact)
        pendingPhotosJob?.cancel()
        pendingPhotosJob = viewModelScope.launch {
            var previousIds = emptySet<Int>()
            fotoDao.getFotosLocales(crefAgrupada, cnofact).collect { photos ->
                val currentIds = photos
                    .filterNot(FotoSyncEntity::synced)
                    .mapTo(mutableSetOf()) { it.id }
                val synchronizedIds = previousIds - currentIds
                _uiState.value = _uiState.value.copy(localPhotos = photos)
                if (synchronizedIds.isNotEmpty()) {
                    loadRemotePhotos(
                        crefAgrupada = crefAgrupada,
                        cnofact = cnofact,
                        showLoading = false
                    )
                }
                previousIds = currentIds
            }
        }
    }

    // Se ejecuta una sola vez por apertura de cámara/galería: borra archivo +
    // registro de cualquier foto que haya quedado marcada synced=true de
    // versiones anteriores (antes de que FotoSyncWorker borrara al confirmar
    // la subida). Ver FotoDao.getFotosSincronizadasHuerfanas.
    private fun limpiarHuerfanasDeVersionesAnteriores() {
        viewModelScope.launch {
            fotoDao.getFotosSincronizadasHuerfanas().forEach { huerfana ->
                runCatching { File(huerfana.localFilePath).delete() }
            }
            fotoDao.limpiarSincronizadasHuerfanas()
        }
    }

    fun toggleShutterSide() {
        val next = if (_shutterSide.value == CameraPreferences.ShutterSide.RIGHT) {
            CameraPreferences.ShutterSide.LEFT
        } else {
            CameraPreferences.ShutterSide.RIGHT
        }
        cameraPreferences.setShutterSide(next)
        _shutterSide.value = next
    }

    fun savePhoto(
        crefAgrupada: String,
        cnofact: String,
        cclienteid: String,
        localFilePath: String,
        esDiscrepancia: Boolean = false
    ) {
        if (!canAddPhoto()) {
            runCatching { File(localFilePath).delete() }
            _uiState.value = _uiState.value.copy(
                message = "✗ No tienes permiso para agregar fotografías de revisión.",
                isError = true
            )
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, message = null, isError = false)
            val entity = FotoSyncEntity(
                crefAgrupada = crefAgrupada,
                cnofact = cnofact,
                cclienteid = cclienteid,
                localFilePath = localFilePath,
                synced = false,
                esDiscrepancia = esDiscrepancia
            )
            fotoDao.insertFoto(entity)

            syncScheduler.scheduleRevisionPhotos()
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                message = "Foto guardada. Se sincronizará automáticamente."
            )
        }
    }

    /**
     * Elimina una foto que aún vive solo localmente (o ya se sincronizó pero
     * seguimos teniendo su copia en Room). Marca la clave como "deleting"
     * mientras dura la operación real -- la UI usa eso para bloquear la
     * miniatura y no dejar reintentos concurrentes -- y solo la limpia
     * cuando el servidor (si aplica) ya respondió.
     */
    fun deleteLocalPhoto(
        photo: FotoSyncEntity,
        crefAgrupada: String,
        cnofact: String
    ) {
        if (!canDeletePhoto()) {
            _uiState.value = _uiState.value.copy(
                message = "✗ No tienes permiso para borrar fotografías de revisión.",
                isError = true
            )
            return
        }
        val key = localPhotoKey(photo.id)
        markDeleting(key)
        viewModelScope.launch {
            photo.remotePhotoId?.let { remoteId ->
                val deleteResult = revisionRepository.eliminarFoto(remoteId)
                if (deleteResult.isFailure) {
                    markError(key, "No se pudo eliminar la foto. Intenta de nuevo.")
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    remotePhotos = _uiState.value.remotePhotos.filterNot {
                        it.idFotos == remoteId
                    }
                )
            }
            fotoDao.deleteFoto(photo.id)
            runCatching { File(photo.localFilePath).delete() }
            clearDeleting(key)
            _uiState.value = _uiState.value.copy(message = "Foto eliminada.", isError = false)
            loadRemotePhotos(
                crefAgrupada = crefAgrupada,
                cnofact = cnofact,
                showLoading = false
            )
        }
    }

    fun deleteRemotePhoto(
        photoId: Int,
        crefAgrupada: String,
        cnofact: String
    ) {
        if (!canDeletePhoto()) {
            _uiState.value = _uiState.value.copy(
                message = "✗ No tienes permiso para borrar fotografías de revisión.",
                isError = true
            )
            return
        }
        val key = remotePhotoKey(photoId)
        markDeleting(key)
        viewModelScope.launch {
            revisionRepository.eliminarFoto(photoId)
                .onSuccess {
                    clearDeleting(key)
                    // Si esta foto se tomó y sincronizó desde este mismo
                    // dispositivo, limpiar también su registro local huérfano
                    // (ver comentario en FotoDao.deleteFotoByRemotePhotoId).
                    fotoDao.getFotoByRemotePhotoId(photoId)?.let { local ->
                        runCatching { File(local.localFilePath).delete() }
                        fotoDao.deleteFotoByRemotePhotoId(photoId)
                    }
                    _uiState.value = _uiState.value.copy(
                        remotePhotos = _uiState.value.remotePhotos.filterNot {
                            it.idFotos == photoId
                        },
                        message = "Foto eliminada.",
                        isError = false
                    )
                }
                .onFailure {
                    markError(key, "No se pudo eliminar la foto. Intenta de nuevo.")
                }
            loadRemotePhotos(
                crefAgrupada = crefAgrupada,
                cnofact = cnofact,
                showLoading = false
            )
        }
    }

    private fun markDeleting(key: String) {
        _uiState.value = _uiState.value.copy(
            deletingKeys = _uiState.value.deletingKeys + key,
            errorKeys = _uiState.value.errorKeys - key
        )
    }

    private fun clearDeleting(key: String) {
        _uiState.value = _uiState.value.copy(
            deletingKeys = _uiState.value.deletingKeys - key
        )
    }

    private fun markError(key: String, message: String) {
        _uiState.value = _uiState.value.copy(
            deletingKeys = _uiState.value.deletingKeys - key,
            errorKeys = _uiState.value.errorKeys + key,
            message = message,
            isError = true
        )
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null, isError = false)
    }

    fun canAddPhoto(): Boolean = authRepository.hasPermission(159, "Agregar")

    fun canDeletePhoto(): Boolean = authRepository.hasPermission(159, "Borrar")

    fun getToken(): String = authRepository.getToken().orEmpty()

    private fun loadRemotePhotos(
        crefAgrupada: String,
        cnofact: String,
        showLoading: Boolean = true
    ) {
        remotePhotosJob?.cancel()
        remotePhotosJob = viewModelScope.launch {
            if (showLoading) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }
            val references = listOf(crefAgrupada, cnofact)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            val results = references.map { revisionRepository.getFotos(it) }
            val successfulPhotos = results
                .mapNotNull { it.getOrNull() }
                .flatten()
                .distinctBy(FotoRevisionDto::idFotos)

            _uiState.value = _uiState.value.copy(
                remotePhotos = successfulPhotos,
                isLoading = false,
                message = if (results.all { it.isFailure }) {
                    "No se pudieron cargar las fotos existentes."
                } else {
                    _uiState.value.message
                }
            )
        }
    }
}
