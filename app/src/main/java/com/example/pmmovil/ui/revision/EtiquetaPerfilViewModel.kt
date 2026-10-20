package com.example.pmmovil.ui.revision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.repository.EtiquetaPerfilRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resuelve el grupo de etiquetas de una entrada para que la hoja de modos sepa si ofrecer el escaneo.
 *
 * Vive aparte de los ViewModel de las pantallas que abren la hoja (asignaciones y notificaciones)
 * porque las dos necesitan lo mismo y ninguna de las dos es su dueña.
 *
 * Mientras no se sepa, el grupo es null y la opción no aparece: es preferible que el modo se sume un
 * instante después a que se ofrezca y luego resulte que el cliente no imprime códigos.
 */
@HiltViewModel
class EtiquetaPerfilViewModel @Inject constructor(
    private val repositorio: EtiquetaPerfilRepository
) : ViewModel() {

    private val _grupo = MutableStateFlow<String?>(null)

    /** Nombre del grupo de etiquetas, o null si el cliente no tiene uno activo. */
    val grupo: StateFlow<String?> = _grupo.asStateFlow()

    private var ultimaReferencia: String? = null

    fun resolver(crefAgrupada: String?) {
        val referencia = crefAgrupada?.trim().orEmpty()
        if (referencia.isEmpty()) {
            ultimaReferencia = null
            _grupo.value = null
            return
        }
        if (referencia == ultimaReferencia) return
        ultimaReferencia = referencia
        // Al cambiar de entrada se limpia primero: si no, la hoja de la entrada nueva mostraría un
        // instante el grupo de la anterior.
        _grupo.value = null
        viewModelScope.launch {
            val perfil = repositorio.perfilDeEntrada(referencia)
            if (ultimaReferencia == referencia) {
                _grupo.value = perfil.nombre?.takeIf { perfil.aplica && it.isNotBlank() }
            }
        }
    }
}
