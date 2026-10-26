package com.example.pmmovil.ui.scanactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.scanner.ScanAction
import com.example.pmmovil.data.scanner.ScanActionResolver
import com.example.pmmovil.data.scanner.ScanActionsRoute
import com.example.pmmovil.data.scanner.ScannedCode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lo que muestra la pantalla: qué se leyó y qué puede hacer con ello el puesto del usuario. */
data class ScanActionsUiState(
    val code: ScannedCode,
    val actions: List<ScanAction>
)

@HiltViewModel
class ScanActionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    authRepository: AuthRepository
) : ViewModel() {

    // Los permisos no cambian mientras la pantalla está abierta: se calcula una vez.
    val state: ScanActionsUiState = run {
        // Sin código en la ruta es un gafete: ScanActionsRoute.forScan lo omite a propósito para que la credencial no
        // viaje en la ruta. Una ruta armada a mano con un gafete adentro se trata igual y no se conserva.
        val code = ScanActionsRoute.codeFromArgument(savedStateHandle.get<String>(ScanActionsRoute.ARG_CODE))
        if (code == ScannedCode.Gafete) savedStateHandle.remove<String>(ScanActionsRoute.ARG_CODE)
        ScanActionsUiState(
            code = code,
            actions = ScanActionResolver.available(code, authRepository::hasPermission)
        )
    }
}
