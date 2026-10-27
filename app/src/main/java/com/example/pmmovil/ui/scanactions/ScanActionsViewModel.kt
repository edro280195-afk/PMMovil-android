package com.example.pmmovil.ui.scanactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.TraceResultDto
import com.example.pmmovil.data.preferences.LastScanStore
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.TraceOutcome
import com.example.pmmovil.data.repository.TraceRepository
import com.example.pmmovil.data.scanner.LastScanRules
import com.example.pmmovil.data.scanner.ScanAction
import com.example.pmmovil.data.scanner.ScanActionResolver
import com.example.pmmovil.data.scanner.ScanActionsRoute
import com.example.pmmovil.data.scanner.ScannedCode
import com.example.pmmovil.data.scanner.TraceActionResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Cómo va la consulta de la ficha al servidor. */
sealed interface TraceUiState {
    /** Este código no se consulta (gafete, etiqueta de producto, referencia dudosa…) o el servidor no lo cubre. */
    data object NotRequested : TraceUiState

    /** Esperando al servidor. */
    data object Loading : TraceUiState

    /** Hay ficha. */
    data class Ready(val result: TraceResultDto) : TraceUiState

    /** El código tiene forma válida pero no existe en el sistema. */
    data object NotFound : TraceUiState

    /** No se pudo consultar (sin red, sin sesión, tiempo agotado): se muestra solo lo que dice el código. */
    data object Unavailable : TraceUiState
}

/** Lo que muestra la pantalla: qué se leyó, la ficha si ya llegó y qué puede hacer con ello el puesto del usuario. */
data class ScanActionsUiState(
    val code: ScannedCode,
    val trace: TraceUiState,
    val actions: List<ScanAction>
)

/** Solo estos tipos tienen ficha en el servidor; los demás se quedan con lo que dice el código. */
internal fun hasTrace(code: ScannedCode): Boolean = when (code) {
    is ScannedCode.Caja,
    is ScannedCode.Relacion,
    is ScannedCode.Carpeta,
    is ScannedCode.Bulto,
    is ScannedCode.PreRegistro -> true

    ScannedCode.Gafete,
    is ScannedCode.ProductoCliente,
    is ScannedCode.Candidato,
    is ScannedCode.Desconocido -> false
}

/**
 * Si esta lectura puede quedar como «Último escaneo»: cuando la ficha llegó, o cuando no se pudo consultar por falta de
 * red (el guardia sigue trabajando sin señal y quiere volver a la caja que acaba de leer). Un código que el servidor dice
 * que no existe no se guarda: nadie quiere reabrir un error de captura.
 */
internal fun shouldRemember(outcome: TraceOutcome): Boolean =
    outcome is TraceOutcome.Found || outcome == TraceOutcome.Unavailable

/**
 * El estado de la pantalla una vez que el servidor contestó. Con ficha, las acciones salen del estado de la pieza; sin
 * ella (sin red, sin permiso, código que el servidor no cubre) se cae a las acciones que se deducen del código, para que
 * una caída de la red no deje al guardia sin poder dar salida a una caja. Si el servidor dice que el código no existe, no
 * se ofrece nada: no hay a qué abrir.
 */
internal fun stateAfter(
    code: ScannedCode,
    rawCode: String,
    outcome: TraceOutcome,
    hasPermission: (moduleId: Int, level: String) -> Boolean
): ScanActionsUiState {
    val local = ScanActionResolver.available(code, hasPermission)
    return when (outcome) {
        is TraceOutcome.Found -> ScanActionsUiState(
            code,
            TraceUiState.Ready(outcome.result),
            TraceActionResolver.available(outcome.result, rawCode, hasPermission)
        )

        TraceOutcome.NotFound -> ScanActionsUiState(code, TraceUiState.NotFound, emptyList())
        TraceOutcome.Unavailable -> ScanActionsUiState(code, TraceUiState.Unavailable, local)
        TraceOutcome.Rejected, TraceOutcome.Forbidden -> ScanActionsUiState(code, TraceUiState.NotRequested, local)
    }
}

@HiltViewModel
class ScanActionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val traceRepository: TraceRepository,
    private val lastScanStore: LastScanStore
) : ViewModel() {

    // Sin código en la ruta es un gafete: ScanActionsRoute.forScan lo omite a propósito para que la credencial no
    // viaje en la ruta. Una ruta armada a mano con un gafete adentro se trata igual y no se conserva.
    private val code: ScannedCode =
        ScanActionsRoute.codeFromArgument(savedStateHandle.get<String>(ScanActionsRoute.ARG_CODE))

    // Lo que se leyó, tal cual, para consultar al servidor: nunca el gafete.
    private val rawCode: String =
        if (code == ScannedCode.Gafete) "" else savedStateHandle.get<String>(ScanActionsRoute.ARG_CODE).orEmpty().trim()

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<ScanActionsUiState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        if (code == ScannedCode.Gafete) savedStateHandle.remove<String>(ScanActionsRoute.ARG_CODE)
        if (hasTrace(code)) load()
    }

    /** Vuelve a pedir la ficha cuando la anterior no se pudo consultar. */
    fun retry() {
        if (_state.value.trace == TraceUiState.Unavailable) load()
    }

    private fun initialState(): ScanActionsUiState =
        if (hasTrace(code)) {
            // Mientras llega la ficha no se ofrece nada: las acciones dependen del estado de la pieza y mostrarlas
            // para cambiarlas medio segundo después invita a tocar la equivocada.
            ScanActionsUiState(code, TraceUiState.Loading, emptyList())
        } else {
            ScanActionsUiState(code, TraceUiState.NotRequested, ScanActionResolver.available(code, authRepository::hasPermission))
        }

    private fun load() {
        loading?.cancel()
        _state.update { it.copy(trace = TraceUiState.Loading, actions = emptyList()) }
        loading = viewModelScope.launch {
            val outcome = withTimeoutOrNull(TRACE_TIMEOUT_MS) { traceRepository.resolve(rawCode) }
                ?: TraceOutcome.Unavailable
            if (shouldRemember(outcome)) {
                // LastScanRules decide qué tipos se recuerdan: el gafete y el pre-registro nunca llegan a guardarse.
                LastScanRules.remember(code, authRepository.getUsername(), System.currentTimeMillis())
                    ?.let(lastScanStore::save)
            }
            _state.value = stateAfter(code, rawCode, outcome, authRepository::hasPermission)
        }
    }

    private companion object {
        /** Más de esto sin respuesta y se sigue sin ficha: el guardia no espera a una red que no contesta. */
        const val TRACE_TIMEOUT_MS = 6_000L
    }
}
