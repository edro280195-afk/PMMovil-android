package com.example.pmmovil.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.ModuloPermisoDto
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.NotificationInboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * Estado del Home.
 *
 * El home es navegación, no información: lo único que necesita del servidor
 * son los permisos, para saber qué módulos puede ver el usuario. No hay
 * contadores ni feeds aquí — cada módulo tiene su propio dashboard.
 *
 *  - loading: primera carga
 *  - refreshing: pull-to-refresh (sólo el spinner)
 *  - network: estado de la última llamada
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val networkAvailable: Boolean = true,
    val lastSyncMillis: Long? = null,
    val errorMessage: String? = null,
    val permissions: List<ModuloPermisoDto> = emptyList(),
    val permissionsResolved: Boolean = false,
    val permissionsFailed: Boolean = false,
    val notificationUnreadCount: Int = 0,
    val phase: HomeLoadPhase = HomeLoadPhase.Sesion
) {
    /**
     * El home sólo debe mostrar el estado de carga cuando todavía no sabe qué
     * módulos pintar. Si ya hay permisos (de caché o de red), la pantalla se
     * dibuja completa aunque siga revalidando en segundo plano.
     */
    val isPreparingModules: Boolean
        get() = !permissionsResolved
}

/**
 * Etapas del arranque del home. Sirven para decirle al usuario qué está
 * pasando en lugar de dejarle la pantalla vacía.
 */
enum class HomeLoadPhase { Sesion, Permisos, Listo }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val notificationInboxRepository: NotificationInboxRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    fun getUserName(): String? = authRepository.getName()
    fun getUserUsername(): String? = authRepository.getUsername()
    fun getUserLugar(): String? = authRepository.getLugar()
    fun getUserArea(): String? = authRepository.getArea()

    fun isSystemsArea(): Boolean = authRepository.isSystemsArea()

    init {
        // No se muestran permisos guardados de una sesión anterior: una baja
        // de permisos debe reflejarse desde el primer frame del home.
        load(showLoadingSkeleton = true)
        refreshNotificationUnreadCount()
    }

    fun refresh() {
        load(showLoadingSkeleton = false)
        refreshNotificationUnreadCount()
    }

    fun refreshNotificationUnreadCount() {
        viewModelScope.launch {
            notificationInboxRepository.getInbox(onlyUnread = true, limit = 1)
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        notificationUnreadCount = response.unreadCount
                    )
                }
        }
    }

    /**
     * El home sólo pide permisos. Antes también traía entradas pendientes y
     * revisiones activas —de forma secuencial y ANTES de los permisos—, así que
     * el usuario esperaba la suma de tres latencias para ver los botones,
     * cuando ninguna de esas dos listas se muestra en esta pantalla.
     */
    private fun load(showLoadingSkeleton: Boolean) {
        viewModelScope.launch {
            // "Resuelto" no basta: tras un fallo también lo está, pero sin nada
            // que pintar. Sólo cuenta como resuelto si hubo respuesta útil.
            val yaHayPermisos = _state.value.permissionsResolved && !_state.value.permissionsFailed
            if (showLoadingSkeleton) {
                _state.value = _state.value.copy(
                    isLoading = !yaHayPermisos,
                    phase = if (yaHayPermisos) HomeLoadPhase.Listo else HomeLoadPhase.Permisos
                )
            } else {
                // Al reintentar sin permisos cargados se vuelve al indicador de
                // carga; si ya hay módulos en pantalla sólo gira el spinner.
                _state.value = _state.value.copy(
                    isRefreshing = true,
                    errorMessage = null,
                    permissionsResolved = yaHayPermisos,
                    permissionsFailed = false,
                    phase = if (yaHayPermisos) HomeLoadPhase.Listo else HomeLoadPhase.Permisos
                )
            }
            try {
                val permissionsResult = authRepository.getPermissions()
                val permissions = permissionsResult.getOrNull()

                _state.value = if (permissions != null) {
                    _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        permissions = permissions,
                        permissionsResolved = true,
                        permissionsFailed = false,
                        networkAvailable = true,
                        lastSyncMillis = System.currentTimeMillis(),
                        errorMessage = null,
                        phase = HomeLoadPhase.Listo
                    )
                } else {
                    // Sin red: si hay caché se conserva lo que ya está en
                    // pantalla; si no, se explica el fallo en vez de dejar el
                    // home vacío.
                    _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        permissionsResolved = true,
                        permissionsFailed = !yaHayPermisos,
                        networkAvailable = false,
                        errorMessage = permissionsResult.exceptionOrNull()?.message.takeIf { yaHayPermisos },
                        phase = HomeLoadPhase.Listo
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    permissionsResolved = true,
                    permissionsFailed = !yaHayPermisos,
                    networkAvailable = false,
                    errorMessage = e.message,
                    phase = HomeLoadPhase.Listo
                )
            }
        }
    }
}

// ── Helper para saludo time-aware (buenos días/tardes/noches) ───────────────
fun smartGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        in 19..23 -> "Buenas noches"
        else      -> "Buenas madrugadas"
    }
}
