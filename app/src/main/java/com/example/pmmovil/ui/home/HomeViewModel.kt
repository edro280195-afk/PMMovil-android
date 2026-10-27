package com.example.pmmovil.ui.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.R
import com.example.pmmovil.data.model.ModuloPermisoDto
import com.example.pmmovil.data.preferences.LastScanStore
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.HomeSummaryRepository
import com.example.pmmovil.data.repository.NotificationInboxRepository
import com.example.pmmovil.data.repository.TraceCompactRepository
import com.example.pmmovil.data.scanner.LastScanKind
import com.example.pmmovil.data.scanner.LastScanRules
import com.example.pmmovil.ui.scanactions.signalKindOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    private val notificationInboxRepository: NotificationInboxRepository,
    private val homeSummaryRepository: HomeSummaryRepository,
    private val lastScanStore: LastScanStore,
    private val traceCompactRepository: TraceCompactRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    // Las cifras del Tablero son aparte de los permisos: si fallan o tardan, el inicio sigue siendo navegable.
    private val _summary = MutableStateFlow(HomeSummaryUiState())
    val summary: StateFlow<HomeSummaryUiState> = _summary.asStateFlow()
    private var summaryJob: Job? = null
    private var lastSummaryRequestAt: Long? = null

    // «Último escaneo»: lo guardado sale al instante; la señal fresca llega aparte y, si falla, la píldora sale sin ella.
    private val _lastScan = MutableStateFlow<HomeLastScanUi?>(null)
    val lastScan: StateFlow<HomeLastScanUi?> = _lastScan.asStateFlow()
    private var lastScanSignalJob: Job? = null

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

    /**
     * Pide las cifras del Tablero. Se llama cada vez que el inicio vuelve a primer plano, pero no más de una vez por
     * [SUMMARY_MIN_INTERVAL_MILLIS] ni mientras haya una petición en vuelo; [force] (jalar para actualizar) se salta el
     * intervalo. Si falla y ya había cifras, se conservan las anteriores: un número de hace un minuto es mejor que un guion.
     */
    fun refreshSummary(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (summaryJob?.isActive == true) return
        if (!force && !shouldRefreshSummary(lastSummaryRequestAt, now)) return
        lastSummaryRequestAt = now
        summaryJob = viewModelScope.launch {
            homeSummaryRepository.get()
                .onSuccess { dto ->
                    _summary.value = HomeSummaryUiState(loaded = true, failed = false, figures = boardFigures(dto))
                }
                .onFailure {
                    _summary.value = _summary.value.let { previous ->
                        if (previous.loaded) previous else previous.copy(failed = true)
                    }
                }
        }
    }

    /**
     * Lee el último escaneo del usuario y, para caja y relación, le pide al servidor su señal de ahora. Se llama cada vez
     * que el inicio vuelve a primer plano: es la única forma de que aparezca tras escanear y regresar.
     */
    fun refreshLastScan() {
        val now = System.currentTimeMillis()
        val scan = LastScanRules.visibleTo(lastScanStore.read(), authRepository.getUsername(), now)
        lastScanSignalJob?.cancel()
        if (scan == null) {
            _lastScan.value = null
            return
        }
        // Mientras llega la señal fresca se conserva la de la misma pieza, para que la píldora no parpadee.
        val previousSignal = _lastScan.value?.takeIf { it.scan.code == scan.code }?.signal
        _lastScan.value = HomeLastScanUi(scan, LastScanRules.elapsed(scan, now), previousSignal)

        // La ficha compacta solo cubre caja y relación; carpeta y bulto se quedan sin señal.
        if (scan.kind != LastScanKind.Caja && scan.kind != LastScanKind.Relacion) return
        lastScanSignalJob = viewModelScope.launch {
            traceCompactRepository.get(listOf(scan.code)).onSuccess { items ->
                val item = items[scan.code]?.takeIf { it.found } ?: return@onSuccess
                _lastScan.value = _lastScan.value
                    ?.takeIf { it.scan.code == scan.code }
                    ?.copy(signal = signalKindOf(item.signal))
            }
        }
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
fun smartGreetingRes(): Int {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11  -> R.string.home_greeting_morning
        in 12..18 -> R.string.home_greeting_afternoon
        in 19..23 -> R.string.home_greeting_evening
        else      -> R.string.home_greeting_late_night
    }
}
