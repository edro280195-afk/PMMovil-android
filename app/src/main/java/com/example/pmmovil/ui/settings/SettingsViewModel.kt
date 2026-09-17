package com.example.pmmovil.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.FaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val faceRepository: FaceRepository
) : ViewModel() {

    /**
     * Estado del acceso facial para la fila de Ajustes. Null mientras se
     * consulta o si el servidor no contesta: la fila entonces no promete nada.
     */
    data class RostroInfo(
        val enrolado: Boolean,
        val plantillas: Int,
        val enroladoEl: String?
    )

    private val _rostro = MutableStateFlow<RostroInfo?>(null)
    val rostro: StateFlow<RostroInfo?> = _rostro.asStateFlow()

    fun getUserName(): String? = authRepository.getName()
    fun getUserUsername(): String? = authRepository.getUsername()
    fun getUserArea(): String? = authRepository.getArea()
    fun getUserLugar(): String? = authRepository.getLugar()

    fun getNfcAssociationInfo(): AuthRepository.NfcAssociationInfo? =
        authRepository.getNfcAssociationInfo()

    /** Se llama al entrar y al volver del enrolamiento, para reflejar el alta. */
    fun refrescarRostro() {
        viewModelScope.launch {
            faceRepository.getEstado()
                .onSuccess { estado ->
                    _rostro.value = RostroInfo(
                        enrolado = estado.Enrolado,
                        plantillas = estado.Plantillas,
                        enroladoEl = estado.EnroladoEl
                    )
                }
                .onFailure { _rostro.value = null }
        }
    }
}
