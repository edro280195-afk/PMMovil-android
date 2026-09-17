package com.example.pmmovil.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.FcmTokenRepository
import com.example.pmmovil.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val fcmTokenRepository: FcmTokenRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    fun login(username: String, password: String = "", isQR: Boolean = false) {
        if (username.isBlank() && !isQR) {
            _authState.value = AuthState.Error("El usuario no puede estar vacío")
            return
        }

        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.login(username, password, isQR)
            
            result.onSuccess {
                _authState.value = AuthState.Success
            }.onFailure { exception ->
                _authState.value = AuthState.Error(exception.message ?: "Error desconocido: ${exception::class.java.simpleName}")
            }
        }
    }

    /**
     * Borra un error ya mostrado para volver al estado de espera.
     *
     * La pantalla de acceso deja los tres canales armados todo el tiempo, así
     * que el aviso de un intento fallido no puede quedarse pegado: el siguiente
     * gafete llega segundos después y tiene que encontrar la pantalla limpia.
     */
    fun limpiarError() {
        if (_authState.value is AuthState.Error) {
            _authState.value = AuthState.Idle
        }
    }

    fun checkAuthStatus(): Boolean {
        return repository.hasActiveSession()
    }

    fun getUsername(): String? = repository.getUsername()
    fun getName(): String? = repository.getName()
    fun getLugar(): String? = repository.getLugar()

    fun logout() {
        // Debe retirarse ANTES de borrar el token de sesión: el endpoint de
        // baja está autenticado, así que necesita el Bearer todavía vigente.
        applicationScope.launch {
            fcmTokenRepository.unregisterCurrentToken()
            repository.logout()
        }
        _authState.value = AuthState.Idle
    }
}
