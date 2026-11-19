package com.example.pmmovil.ui.salidacaseta

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.camera.FotoParaSubir
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.model.SalidaCarril
import com.example.pmmovil.data.model.SalidaPatioHoy
import com.example.pmmovil.data.model.SalidaPatioItem
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.CasetaRepository
import com.example.pmmovil.data.repository.SalidaCasetaRepository
import com.example.pmmovil.data.scanner.OfficialQrParser
import com.example.pmmovil.data.scanner.PendingCode
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pantalla unificada de salida de caseta.
 *
 * Una sola pantalla reemplaza a "Salida de relación" (módulo 165) y a "Salida
 * física de caja" (módulo 166) SIN revolverlas: el servidor clasifica cada caja
 * del patio en un carril y cada carril solo ofrece la acción que le toca.
 * Los permisos se conservan separados: 165 confirma relaciones y cierra
 * registros (Agregar = fotos), 166 da las salidas manuales.
 */
data class SalidaUiState(
    val items: List<SalidaPatioItem> = emptyList(),
    val hoy: SalidaPatioHoy = SalidaPatioHoy(),
    val query: String = "",
    val carrilFiltro: SalidaCarril? = null,
    val selectedId: String? = null,
    val observaciones: Map<String, String> = emptyMap(),
    val fotos: Map<String, List<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isProcessing: Boolean = false,
    val confirmVisible: Boolean = false,
    val currentUser: String = "",
    val tieneAcceso: Boolean = true,
    val relModificar: Boolean = false,
    val relAgregar: Boolean = false,
    val relBorrar: Boolean = false,
    val manualModificar: Boolean = false,
    val actualizado: String = "",
    /** Qué está haciendo la app mientras [isProcessing] ("Enviando fotos: 3 de 8"): el guardia nunca ve un spinner mudo. */
    val progresoEnvio: String? = null,
    val message: String? = null,
    val feedbackSeq: Int = 0
) {
    val selected: SalidaPatioItem? get() = items.firstOrNull { it.id == selectedId }

    fun conteo(carril: SalidaCarril?): Int =
        if (carril == null) items.size else items.count { it.carrilEnum == carril }

    /** ¿El perfil puede ejecutar la acción propia del carril de este renglón? */
    fun puedeActuar(item: SalidaPatioItem): Boolean = when (item.carrilEnum) {
        SalidaCarril.LISTA, SalidaCarril.CERRAR -> relModificar
        SalidaCarril.MANUAL -> manualModificar
        SalidaCarril.PROCESO -> false
    }

    fun observacionDe(item: SalidaPatioItem): String = observaciones[item.id].orEmpty()
    fun fotosDe(item: SalidaPatioItem): List<String> = fotos[item.id].orEmpty()

    /** Lista visible: filtro de carril + búsqueda, ordenada por carril y por urgencia. */
    fun visibles(): List<SalidaPatioItem> {
        val clean = query.trim()
        return items
            .asSequence()
            .filter { carrilFiltro == null || it.carrilEnum == carrilFiltro }
            .filter { clean.isBlank() || it.coincideCon(clean) }
            .sortedWith(
                compareBy<SalidaPatioItem> { it.carrilEnum.ordinal }
                    .thenByDescending { it.urgencia() }
            )
            .toList()
    }
}

private fun SalidaPatioItem.coincideCon(texto: String): Boolean =
    id.contains(texto, ignoreCase = true) ||
        relno.contains(texto, ignoreCase = true) ||
        tipoMovimiento.contains(texto, ignoreCase = true) ||
        clientes.any { it.contains(texto, ignoreCase = true) } ||
        cajas.any {
            it.numCaja.contains(texto, ignoreCase = true) ||
                it.placas.contains(texto, ignoreCase = true) ||
                it.numCamion.contains(texto, ignoreCase = true) ||
                it.lineaCaja.contains(texto, ignoreCase = true) ||
                it.casetaId.contains(texto, ignoreCase = true)
        }

/** Más grande = más arriba dentro de su carril (lo que lleva más tiempo esperando). */
private fun SalidaPatioItem.urgencia(): Long = when (carrilEnum) {
    SalidaCarril.LISTA -> (minutosEspera ?: 0).toLong()
    SalidaCarril.MANUAL -> horasEnPatio.toLong()
    SalidaCarril.PROCESO -> (minutosCargando ?: 0).toLong()
    SalidaCarril.CERRAR -> diasEnPatio.toLong()
}

@HiltViewModel
class SalidaCasetaViewModel @Inject constructor(
    private val repository: SalidaCasetaRepository,
    private val casetaRepository: CasetaRepository,
    private val authRepository: AuthRepository,
    private val prefs: SharedPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SalidaUiState(
            currentUser = authRepository.getUsername().orEmpty(),
            tieneAcceso = authRepository.hasPermission(MobileModuleIds.GATE_RELATION_EXIT, "Lectura") ||
                authRepository.hasPermission(MobileModuleIds.GATE_PHYSICAL_EXIT, "Lectura"),
            relModificar = authRepository.hasPermission(MobileModuleIds.GATE_RELATION_EXIT, "Modificar"),
            relAgregar = authRepository.hasPermission(MobileModuleIds.GATE_RELATION_EXIT, "Agregar"),
            relBorrar = authRepository.hasPermission(MobileModuleIds.GATE_RELATION_EXIT, "Borrar"),
            manualModificar = authRepository.hasPermission(MobileModuleIds.GATE_PHYSICAL_EXIT, "Modificar")
        )
    )
    val uiState: StateFlow<SalidaUiState> = _uiState.asStateFlow()

    // Cada foto se prepara para subir apenas se toma (ver FotoParaSubir: queda en disco, junto a la original) y sube en
    // segundo plano (envioFotos.subirEnSegundoPlano): al confirmar ya no hay que esperarlas. Un hilo a la vez para no
    // competir con la cámara, que sigue abierta mientras el guardia toma las demás.
    private val photoPrepScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val preparando = mutableMapOf<String, Job>()

    private val pendingCode = PendingCode()

    /** La confirmación en curso: se puede cancelar sin perder las fotos. */
    private var confirmacionJob: Job? = null

    /** Manda las fotos al servidor por lotes y recuerda cuáles ya llegaron, para que un reintento no las repita. */
    private val envioFotos = SalidaFotosEnvio(casetaRepository, TAG)

    /**
     * Fotos que el guardia ya había tomado antes de salir de la pantalla o de que la app se reiniciara; se devuelven a
     * su relación con la primera lectura del patio (ver [SalidaFotosBorrador]).
     */
    private var fotosGuardadas: Map<String, List<String>> = SalidaFotosBorrador.leer(
        prefs.getString(CLAVE_FOTOS_BORRADOR, null)
    ) { File(it).exists() }

    init {
        if (_uiState.value.tieneAcceso) refresh() else _uiState.update { it.copy(isLoading = false) }
    }

    // ── Carga ───────────────────────────────────────────────────────────────

    /** [silencioso] = refresco automático: no parpadea ni borra lo que el guardia está haciendo. */
    fun refresh(silencioso: Boolean = false) {
        if (!_uiState.value.tieneAcceso) return
        if (silencioso && (_uiState.value.isProcessing || _uiState.value.confirmVisible)) return
        viewModelScope.launch {
            _uiState.update {
                if (silencioso) it.copy(isRefreshing = true) else it.copy(isLoading = it.items.isEmpty(), isRefreshing = true)
            }
            repository.getPatio()
                .onSuccess { respuesta ->
                    aplicarPatio(respuesta.items, respuesta.hoy)
                    pendingCode.take()?.let(::onScannerInput)
                }
                .onFailure { error ->
                    // Si el patio no cargó, buscar el código daría un «no encontré» falso: ya se muestra el error.
                    pendingCode.take()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            // El refresco automático no molesta con errores de red pasajeros.
                            message = if (silencioso) it.message else "✗ ${error.message ?: "No se pudo cargar el patio."}",
                            feedbackSeq = if (silencioso) it.feedbackSeq else it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    private fun aplicarPatio(nuevos: List<SalidaPatioItem>, hoy: SalidaPatioHoy) {
        // Con la primera lectura del patio: las fotos respaldadas de una relación que ya no está (otro guardia le dio
        // salida) se borran, y las demás se devuelven a su relación dentro de la actualización de abajo. Va fuera de
        // `update` porque esa lambda puede repetirse y esto debe pasar una sola vez.
        val porRestaurar = fotosGuardadas
        fotosGuardadas = emptyMap()
        val idsVigentes = nuevos.map { it.id }.toSet()
        porRestaurar
            .filterKeys { it !in idsVigentes }
            .values.flatten()
            .forEach {
                runCatching { File(it).delete() }
                FotoParaSubir.olvidar(it)
            }

        _uiState.update { actual ->
            val vigentes = nuevos.map { it.id }.toSet()
            val anterior = actual.selected
            val nuevoSeleccionado = nuevos.firstOrNull { it.id == actual.selectedId }

            // Si lo que el guardia tenía abierto cambió de carril o ya no está, se le avisa:
            // jamás se le deja confirmar sobre una pantalla que ya no es cierta.
            val aviso = when {
                anterior == null -> null
                nuevoSeleccionado == null -> "⚠ ${tituloCorto(anterior)} ya no está en el patio."
                nuevoSeleccionado.carrilEnum != anterior.carrilEnum ->
                    "⚠ ${tituloCorto(anterior)} cambió de estado: ahora está en «${nombreCarril(nuevoSeleccionado.carrilEnum)}»."
                else -> null
            }

            // Fotos y observaciones de renglones que ya no existen se descartan.
            val descartados = actual.fotos.keys.filter { it !in vigentes }
            descartados.forEach { id -> actual.fotos[id].orEmpty().forEach(::descartarFoto) }

            // Las fotos respaldadas de una sesión anterior vuelven a su relación (ver porRestaurar).
            val restauradas = porRestaurar
                .filter { (id, _) -> id in vigentes && actual.fotos[id].isNullOrEmpty() }

            actual.copy(
                items = nuevos,
                hoy = hoy,
                actualizado = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                isLoading = false,
                isRefreshing = false,
                selectedId = nuevoSeleccionado?.id,
                confirmVisible = if (nuevoSeleccionado == null || aviso != null) false else actual.confirmVisible,
                observaciones = actual.observaciones.filterKeys { it in vigentes },
                fotos = actual.fotos.filterKeys { it in vigentes } + restauradas,
                message = aviso ?: actual.message,
                feedbackSeq = if (aviso != null) actual.feedbackSeq + 1 else actual.feedbackSeq
            )
        }
        guardarFotosBorrador()
    }

    /** Deja en la tableta qué fotos lleva cada relación, para recuperarlas si el guardia sale o la app se reinicia. */
    private fun guardarFotosBorrador() {
        prefs.edit().putString(CLAVE_FOTOS_BORRADOR, SalidaFotosBorrador.serializar(_uiState.value.fotos)).apply()
    }

    private fun tituloCorto(item: SalidaPatioItem): String =
        if (item.cajas.size > 1 || item.sinCajaEnCaseta) "La relación ${item.relno}" else "La caja ${item.cajas.firstOrNull()?.numCaja.orEmpty()}"

    private fun nombreCarril(carril: SalidaCarril): String = when (carril) {
        SalidaCarril.LISTA -> "Listas"
        SalidaCarril.MANUAL -> "Sin relación"
        SalidaCarril.PROCESO -> "En proceso"
        SalidaCarril.CERRAR -> "Por cerrar"
    }

    // ── Navegación y búsqueda ───────────────────────────────────────────────

    fun setQuery(value: String) = _uiState.update { it.copy(query = value) }
    fun clearQuery() = _uiState.update { it.copy(query = "") }
    fun setCarrilFiltro(carril: SalidaCarril?) = _uiState.update { it.copy(carrilFiltro = carril) }

    fun select(id: String) {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(selectedId = id, confirmVisible = false) }
        // Fotos que volvieron del respaldo (la app se cerró o el guardia salió): empiezan a subir desde que se abre.
        _uiState.value.selected?.let(::subirFotosEnFondo)
    }

    fun closeDetail() {
        if (_uiState.value.isProcessing) return
        _uiState.update { it.copy(selectedId = null, confirmVisible = false) }
    }

    /** Lector de QR/código de barras: caja, relación, placas o id de caseta. */
    fun onScannerInput(raw: String) {
        // La requisición de cruce (`R-01I26106070  0000  101`) y la orden de carga (`01I26106418-1`) traen la relación
        // con más texto alrededor: se busca la relación, no el primer pedazo del QR.
        val code = OfficialQrParser.relacionOf(raw)
            ?: raw.trim().split(Regex("\\s+")).firstOrNull().orEmpty().trim(',', ';')
        if (code.isBlank()) {
            _uiState.update {
                it.copy(message = "⚠ Lectura vacía. Vuelve a escanear la caja.", feedbackSeq = it.feedbackSeq + 1)
            }
            return
        }
        if (_uiState.value.isLoading) {
            // El patio aún no llega (por ejemplo, abierta desde el escáner universal): buscar ahora diría
            // «no encontré» de algo que sí está. Se busca al terminar de cargar.
            pendingCode.hold(code)
            return
        }
        val coincidencias = _uiState.value.items.filter { item ->
            item.relno.equals(code, ignoreCase = true) ||
                item.id.equals(code, ignoreCase = true) ||
                item.cajas.any { it.numCaja.equals(code, ignoreCase = true) || it.casetaId.equals(code, ignoreCase = true) }
        }
        when (coincidencias.size) {
            1 -> _uiState.update {
                it.copy(selectedId = coincidencias.first().id, carrilFiltro = null, query = "", confirmVisible = false)
            }
            0 -> _uiState.update {
                it.copy(
                    query = code,
                    carrilFiltro = null,
                    message = "⚠ No encontré «$code» en el patio.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            else -> _uiState.update { it.copy(query = code, carrilFiltro = null) }
        }
    }

    // ── Observaciones y fotos ───────────────────────────────────────────────

    fun updateObservaciones(value: String) {
        val id = _uiState.value.selectedId ?: return
        _uiState.update { it.copy(observaciones = it.observaciones + (id to value.take(MAX_OBSERVACION))) }
    }

    fun addPhoto(path: String) {
        val estado = _uiState.value
        val item = estado.selected
        if (item == null || item.carrilEnum != SalidaCarril.LISTA || !estado.relAgregar) {
            descartarFoto(path)
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para agregar fotografías de salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(fotos = it.fotos + (item.id to it.fotosDeId(item.id) + path)) }
        guardarFotosBorrador()
        // Se prepara ya y sube en segundo plano, en paralelo a que se sigan tomando fotos.
        preparando[path] = photoPrepScope.launch { FotoParaSubir.preparar(path) }
        subirFotosEnFondo(item)
    }

    /** Sube lo que falte de [item] mientras el guardia sigue; solo las relaciones listas llevan fotos de salida. */
    private fun subirFotosEnFondo(item: SalidaPatioItem) {
        if (item.carrilEnum != SalidaCarril.LISTA || !_uiState.value.relAgregar) return
        if (_uiState.value.fotosDe(item).isEmpty()) return
        envioFotos.subirEnSegundoPlano(viewModelScope, item.relno) { _uiState.value.fotos[item.id].orEmpty() }
    }

    fun removePhoto(path: String) {
        val estado = _uiState.value
        val item = estado.selected ?: return
        if (!estado.relBorrar) {
            _uiState.update {
                it.copy(
                    message = "✗ No tienes permiso para borrar fotografías de salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(fotos = it.fotos + (item.id to it.fotosDeId(item.id) - path)) }
        descartarFoto(path)
        guardarFotosBorrador()
        // Las que estaban después se recorrieron una posición: el envío de fondo las vuelve a mandar donde ahora van.
        subirFotosEnFondo(item)
    }

    private fun SalidaUiState.fotosDeId(id: String): List<String> = fotos[id].orEmpty()

    private fun descartarFoto(path: String) {
        preparando.remove(path)?.cancel()
        envioFotos.olvidar(path)
        runCatching { File(path).delete() }
        FotoParaSubir.olvidar(path)
    }

    // ── Confirmación ────────────────────────────────────────────────────────

    fun requestConfirm() {
        val estado = _uiState.value
        val item = estado.selected ?: return
        if (estado.isProcessing) return
        if (!estado.puedeActuar(item)) {
            _uiState.update {
                it.copy(
                    message = "✗ Tu perfil no permite este tipo de salida.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        _uiState.update { it.copy(confirmVisible = true) }
    }

    fun dismissConfirm() = _uiState.update { it.copy(confirmVisible = false) }

    fun confirm() {
        val estado = _uiState.value
        val item = estado.selected ?: return
        if (estado.isProcessing || !estado.puedeActuar(item)) return
        if (estado.currentUser.isBlank()) {
            _uiState.update {
                it.copy(
                    confirmVisible = false,
                    message = "✗ No se encontró el usuario de sesión. Inicia sesión nuevamente.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        val observacion = estado.observacionDe(item).trim()

        confirmacionJob = viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, confirmVisible = false, progresoEnvio = "Registrando la salida…") }
            val inicio = System.currentTimeMillis()

            try {
                val resultado: Result<Pair<Boolean, String?>> = when (item.carrilEnum) {
                    // null = ya se avisó que las fotos no se pudieron preparar o enviar (siguen en la tableta).
                    SalidaCarril.LISTA -> confirmarRelacion(item, estado.currentUser, observacion, estado.fotosDe(item))
                        ?: return@launch
                    SalidaCarril.MANUAL -> repository
                        .registrarSalida(item.cajas.first().casetaId, observacion)
                        .map { it.success to it.message }
                    SalidaCarril.CERRAR -> repository
                        .cerrarRegistro(item.relno)
                        .map { it.success to it.message }
                    SalidaCarril.PROCESO -> Result.failure(IllegalStateException("Esta relación todavía no puede salir."))
                }
                Log.i(TAG, "salida ${item.relno} terminó en ${System.currentTimeMillis() - inicio} ms")
                terminarConfirmacion(item, estado.fotosDe(item), resultado)
            } catch (e: CancellationException) {
                // La cancelación la informa cancelarConfirmacion(); aquí solo se deja pasar.
                throw e
            } catch (e: Throwable) {
                Log.e(TAG, "salida ${item.relno} falló tras ${System.currentTimeMillis() - inicio} ms", e)
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progresoEnvio = null,
                        message = "✗ ${e.message ?: "Error al registrar la salida."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
        }
    }

    /** Resultado de la confirmación: éxito o rechazo, el patio pudo cambiar, así que se vuelve a leer del servidor. */
    private fun terminarConfirmacion(
        item: SalidaPatioItem,
        fotosDelItem: List<String>,
        resultado: Result<Pair<Boolean, String?>>
    ) {
        resultado
            .onSuccess { (ok, mensaje) ->
                if (ok) {
                    fotosDelItem.forEach(::descartarFoto)
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            progresoEnvio = null,
                            items = it.items.filterNot { x -> x.id == item.id },
                            selectedId = null,
                            observaciones = it.observaciones - item.id,
                            fotos = it.fotos - item.id,
                            hoy = when (item.carrilEnum) {
                                SalidaCarril.LISTA -> it.hoy.copy(conRelacion = it.hoy.conRelacion + 1)
                                SalidaCarril.MANUAL -> it.hoy.copy(manuales = it.hoy.manuales + 1)
                                else -> it.hoy
                            },
                            message = "✓ ${mensaje ?: "Salida registrada correctamente."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                    guardarFotosBorrador()
                } else {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            progresoEnvio = null,
                            message = "✗ ${mensaje ?: "No se pudo registrar la salida."}",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
                refresh(silencioso = true)
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isProcessing = false,
                        progresoEnvio = null,
                        message = "✗ ${error.message ?: "Error al registrar la salida."}",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
                // Si se cortó justo al confirmar, la salida pudo haberse registrado: se revisa el patio antes de reintentar.
                if (item.carrilEnum == SalidaCarril.LISTA) refresh(silencioso = true)
            }
    }

    /**
     * Salida con relación: manda por lotes, con avance visible, lo que el envío de fondo no alcanzó a subir y confirma
     * (ver [SalidaFotosEnvio]). Devuelve `null` cuando las fotos no se pudieron preparar o enviar (ya se avisó al
     * guardia; siguen guardadas).
     */
    private suspend fun confirmarRelacion(
        item: SalidaPatioItem,
        usuario: String,
        observacion: String,
        fotos: List<String>
    ): Result<Pair<Boolean, String?>>? =
        when (val confirmacion = envioFotos.confirmarConFotos(
            relno = item.relno,
            usuario = usuario,
            observaciones = observacion,
            rutas = fotos,
            alAvanzar = ::publicarProgreso
        )) {
            is SalidaFotosEnvio.Confirmacion.Terminada -> confirmacion.resultado.map { it.success to it.message }
            is SalidaFotosEnvio.Confirmacion.SinConfirmar -> {
                avisarFalla(confirmacion.mensaje)
                null
            }
        }

    private fun publicarProgreso(texto: String) = _uiState.update { it.copy(progresoEnvio = texto) }

    private fun avisarFalla(texto: String) = _uiState.update {
        it.copy(
            isProcessing = false,
            progresoEnvio = null,
            message = "✗ $texto",
            feedbackSeq = it.feedbackSeq + 1
        )
    }

    /**
     * Corta el envío en curso sin perder nada: las fotos siguen en la tableta y se vuelve a leer el patio por si la
     * salida alcanzó a registrarse.
     */
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
        refresh(silencioso = true)
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    override fun onCleared() {
        super.onCleared()
        photoPrepScope.cancel()
    }

    companion object {
        private const val TAG = "PMM-SALIDA"
        private const val MAX_OBSERVACION = 100 // caseta.obs_salida es varchar(100)
        private const val CLAVE_FOTOS_BORRADOR = "SALIDA_FOTOS_BORRADOR"
    }
}
