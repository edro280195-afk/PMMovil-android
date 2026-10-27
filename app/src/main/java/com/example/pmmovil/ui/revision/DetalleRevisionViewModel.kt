package com.example.pmmovil.ui.revision

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.R
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.diagnostics.RevisionDiagnostics
import com.example.pmmovil.data.model.*
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.preferences.UnlockStore
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.data.scanner.StructuredLabelBarcodeParser
import com.example.pmmovil.data.signalr.CierreForzosoMessage
import com.example.pmmovil.data.signalr.RevisionActualizadaMessage
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.example.pmmovil.di.ApplicationScope
import com.example.pmmovil.ui.localization.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class CompletaValidation {
    data class BultosDiferencia(val cnofact: String, val bultos: Int, val bultosDoc: Int) : CompletaValidation()
    data class HazmatAlerta(val cnofact: String, val problemas: List<String>) : CompletaValidation()
    // Se tomaron fotos de evidencia de discrepancia (nTipoDocto=103) para esta
    // factura, pero el usuario está intentando marcarla COMPLETA. Pedimos
    // confirmación por si en realidad debería quedar como DISCREPANCIA.
    data class EvidenciaDiscrepanciaSinMarcar(val cnofact: String, val cantidadFotos: Int) : CompletaValidation()
    data class PartidasSinRevisar(val cnofact: String, val totalSinRevisar: Int, val ejemplos: List<String>) : CompletaValidation()
    data class DiscrepanciasAclarar(val cnofact: String, val discrepancias: String) : CompletaValidation()
}

enum class TipoResultadoBusqueda {
    FACTURA,
    PRODUCTO
}

/** Resultado del único buscador global de la revisión. */
data class ResultadoBusquedaRevision(
    val tipo: TipoResultadoBusqueda,
    val factura: FacturaRevisionDto,
    val partida: PartidaRevisionDto?,
    val facturaIndex: Int,
    val posicion: Int?,
    val totalFactura: Int,
    val score: Int
)

typealias CierreForzosoNotice = CierreForzosoMessage

/** Valor del argumento `modo` de la ruta `detalle/{crefAgrupada}` cuando se entra por «Escaneo de etiquetas». */
const val MODO_ETIQUETAS = "etiquetas"

/**
 * Evento de "se acumuló cantidad por un escaneo de etiqueta segmentada,
 * todavía se puede deshacer". La Screen lo muestra como snackbar con botón
 * "Deshacer" -- ver [DetalleRevisionViewModel.deshacerAcumulacion].
 */
data class DeshacerAcumulacion(
    val idRevdet: Int,
    val cantidadPrevia: Double,
    // Lo que debería seguir marcando nCantOut si nadie más lo tocó desde el
    // escaneo. Si ya no coincide al momento de deshacer, alguien (otro
    // escaneo, una edición manual) cambió el valor mientras tanto y no se
    // pisa a ciegas.
    val cantidadEsperadaActual: Double,
    val mensaje: UiText,
    /**
     * El empaque que se agregó al libro, cuando la suma vino de contar una etiqueta. Deshacer tiene
     * que quitarlo también: si solo se restituyera `nCantOut`, el empaque seguiría contado y el
     * siguiente sumaría encima de un total que ya no existe.
     */
    val empaque: PackageTally.CountedPackage? = null
)

@HiltViewModel
class DetalleRevisionViewModel @Inject constructor(
    private val repository: RevisionRepository,
    private val authRepository: AuthRepository,
    private val hubService: WarehouseHubService,
    private val unlockStore: UnlockStore,
    private val fotoDao: FotoDao,
    private val labelCaptureDao: com.example.pmmovil.data.db.LabelCaptureDao,
    private val ledgerPreferences: com.example.pmmovil.data.preferences.PackageLedgerPreferences,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val crefAgrupada: String = checkNotNull(savedStateHandle["crefAgrupada"])

    /**
     * Verdadero cuando el revisador entró por el modo «Escaneo de etiquetas» (ruta `detalle/{cref}?modo=etiquetas`):
     * es esta misma revisión, con la cámara de etiqueta abierta desde el primer momento.
     */
    val abrirEtiquetasAlEntrar: Boolean = savedStateHandle.get<String>("modo") == MODO_ETIQUETAS

    private var aperturaEtiquetasReclamada = false

    /** Se entró por «Escaneo de etiquetas» y la cámara todavía no se abrió. */
    val aperturaEtiquetasPendiente: Boolean get() = abrirEtiquetasAlEntrar && !aperturaEtiquetasReclamada

    /**
     * Devuelve verdadero una sola vez por pantalla: la cámara se abre al entrar, no cada vez que se regresa de la
     * galería o de la cámara de evidencia (la pantalla se recompone y sin esto se reabriría sola).
     */
    fun reclamarAperturaEtiquetas(): Boolean {
        if (!abrirEtiquetasAlEntrar || aperturaEtiquetasReclamada) return false
        aperturaEtiquetasReclamada = true
        return true
    }

    init {
        Log.i("MODO_ETIQUETAS", "revisión $crefAgrupada abierta · modo=${if (abrirEtiquetasAlEntrar) "etiquetas" else "normal"}")
    }

    private val _facturas = MutableStateFlow<List<FacturaRevisionDto>>(emptyList())
    val facturas: StateFlow<List<FacturaRevisionDto>> = _facturas.asStateFlow()

    private val _partidas = MutableStateFlow<List<PartidaRevisionDto>>(emptyList())
    val partidas: StateFlow<List<PartidaRevisionDto>> = _partidas.asStateFlow()
    val todasLasPartidas: StateFlow<List<PartidaRevisionDto>> = _partidas.asStateFlow()

    // Observación general de TODA la revisión (traf3.observacion_revision) --
    // una sola nota por entrada, no por factura (para eso ya está Obsrev).
    private val _observacionGeneral = MutableStateFlow("")
    val observacionGeneral: StateFlow<String> = _observacionGeneral.asStateFlow()

    fun onObservacionGeneralChange(valor: String) {
        _observacionGeneral.value = valor
    }

    /** Se guarda al perder foco (mismo patrón que obsrev por factura vía RevisionInput). */
    fun guardarObservacionGeneral() {
        if (!canEdit()) return
        val valor = _observacionGeneral.value.trim()
        viewModelScope.launch {
            repository.guardarObservacionEntrada(crefAgrupada, valor)
                .onFailure { _toast.value = resource(R.string.message_general_observation_failed) }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<UiText?>(null)
    val error: StateFlow<UiText?> = _error.asStateFlow()

    private val _toast = MutableStateFlow<UiText?>(null)
    val toast: StateFlow<UiText?> = _toast.asStateFlow()

    // Evento de un solo uso para el snackbar con "Deshacer" tras un escaneo
    // que acumuló cantidad automáticamente (ver procesarCodigoEscaneado).
    private val _ultimaAcumulacion = MutableStateFlow<DeshacerAcumulacion?>(null)
    val ultimaAcumulacion: StateFlow<DeshacerAcumulacion?> = _ultimaAcumulacion.asStateFlow()

    /**
     * El libro de empaques contados de esta entrada. Sobrevive a que se cierre la app porque una
     * entrada de Freudenberg lleva decenas de etiquetas y perder la cuenta a media revisión obligaría
     * a empezar de nuevo.
     */
    private val _libroEmpaques = MutableStateFlow(ledgerPreferences.get(crefAgrupada))
    val libroEmpaques: StateFlow<PackageTally.Ledger> = _libroEmpaques.asStateFlow()

    private fun guardarLibro(ledger: PackageTally.Ledger) {
        _libroEmpaques.value = ledger
        ledgerPreferences.set(crefAgrupada, ledger)
    }

    fun consumeUltimaAcumulacion() {
        _ultimaAcumulacion.value = null
    }

    private val _isSharingLink = MutableStateFlow(false)
    val isSharingLink: StateFlow<Boolean> = _isSharingLink.asStateFlow()

    /** Evento de un solo uso: al llenarse, la pantalla abre el share sheet nativo y lo consume. */
    private val _shareUrl = MutableStateFlow<String?>(null)
    val shareUrl: StateFlow<String?> = _shareUrl.asStateFlow()

    /**
     * "Seguimiento de revisión" -- página pública separada de "Seguimiento
     * de entrada" (esta última la genera EntradaViewModel.compartirEntrada).
     * Antes este método reusaba EntradaRepository.compartir(crefAgrupada) y
     * por eso el botón Compartir de Revisión abría exactamente el mismo
     * enlace y contenido que el de Entrada.
     */
    fun compartirEntrada() {
        _isSharingLink.value = true
        viewModelScope.launch {
            repository.compartir(crefAgrupada)
                .onSuccess { response ->
                    _isSharingLink.value = false
                    _shareUrl.value = response.url
                }
                .onFailure { error ->
                    _isSharingLink.value = false
                    _toast.value = error.message?.takeIf(String::isNotBlank)?.let(UiText::Raw)
                        ?: resource(R.string.message_share_failed)
                }
        }
    }

    fun consumeShareUrl() {
        _shareUrl.value = null
    }

    private val _cierreForzoso = MutableStateFlow<CierreForzosoNotice?>(null)
    val cierreForzoso: StateFlow<CierreForzosoNotice?> = _cierreForzoso.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _timerActivo = MutableStateFlow(false)
    val timerActivo: StateFlow<Boolean> = _timerActivo.asStateFlow()

    private val _selectedFacturaIndex = MutableStateFlow(0)
    val selectedFacturaIndex: StateFlow<Int> = _selectedFacturaIndex.asStateFlow()

    private val _navegarAtras = MutableStateFlow(false)
    val navegarAtras: StateFlow<Boolean> = _navegarAtras.asStateFlow()

    // Distingue "terminé de verdad" (Finalizar) de un cierre forzoso por
    // supervisor -- ambos navegan atrás, pero solo el primero merece
    // celebración. La Screen lo lee junto con navegarAtras.
    private val _celebrateOnExit = MutableStateFlow(false)
    val celebrateOnExit: StateFlow<Boolean> = _celebrateOnExit.asStateFlow()

    // --- Fase 3 State ---
    private val _paises = MutableStateFlow<List<PaisDto>>(emptyList())
    val paises: StateFlow<List<PaisDto>> = _paises.asStateFlow()

    private val _paisesFrecuentes = MutableStateFlow<List<PaisFrecuenteDto>>(emptyList())
    val paisesFrecuentes: StateFlow<List<PaisFrecuenteDto>> = _paisesFrecuentes.asStateFlow()

    private val _tiposMaterial = MutableStateFlow(REVISION_TIPOS_MATERIAL)
    val tiposMaterial: StateFlow<List<TipoMaterialDto>> = _tiposMaterial.asStateFlow()

    private val _unidadesComerciales = MutableStateFlow<List<UnidadComercialDto>>(emptyList())
    val unidadesComerciales: StateFlow<List<UnidadComercialDto>> = _unidadesComerciales.asStateFlow()

    private val _fotos = MutableStateFlow<List<FotoRevisionDto>>(emptyList())
    val fotos: StateFlow<List<FotoRevisionDto>> = _fotos.asStateFlow()

    // Fotos agrupadas por Cnofact, para saber -por factura- si ya se tomó
    // evidencia de discrepancia (nTipoDocto=103) y avisar si intentan cerrar
    // como COMPLETA de todas formas.
    private val _fotosPorFactura = MutableStateFlow<Map<String, List<FotoRevisionDto>>>(emptyMap())
    val fotosPorFactura: StateFlow<Map<String, List<FotoRevisionDto>>> = _fotosPorFactura.asStateFlow()

    val fotosDiscrepanciaFacturaActual: StateFlow<Int> = combine(
        _fotosPorFactura,
        _selectedFacturaIndex,
        _facturas
    ) { porFactura, index, facturas ->
        val cnofact = facturas.getOrNull(index)?.Cnofact ?: return@combine 0
        porFactura[cnofact]?.count { it.esDiscrepancia } ?: 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val pendingPhotoCount: StateFlow<Int> = fotoDao
        .getFotosPendientes(crefAgrupada, "")
        .map { photos -> photos.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private val labelCaptureOwner = authRepository.getUsername()?.trim()?.lowercase(Locale.ROOT).orEmpty()
    private val pendingCaptureCount = labelCaptureDao.observePendingCount(crefAgrupada, labelCaptureOwner)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _isUploadingFoto = MutableStateFlow(false)
    val isUploadingFoto: StateFlow<Boolean> = _isUploadingFoto.asStateFlow()

    private val _partidaActivaIndex = MutableStateFlow(1)
    val partidaActivaIndex: StateFlow<Int> = _partidaActivaIndex.asStateFlow()

    private val _navDirection = MutableStateFlow("next")
    val navDirection: StateFlow<String> = _navDirection.asStateFlow()

    private val _filtroPartidas = MutableStateFlow("todas")
    val filtroPartidas: StateFlow<String> = _filtroPartidas.asStateFlow()

    // Buscador global de la revisión. Busca sobre TODAS las facturas y
    // partidas cargadas, porque el revisor puede tener el bulto enfrente sin
    // saber todavía en qué factura se encuentra.
    private val _busquedaParte = MutableStateFlow("")
    val busquedaParte: StateFlow<String> = _busquedaParte.asStateFlow()

    // Se incrementa cada vez que un salto de búsqueda aterriza en una
    // partida; la Screen lo usa como disparador de un LaunchedEffect para el
    // resaltado momentáneo del formulario (mismo patrón que unlockApprovedSeq).
    private val _jumpPulseSeq = MutableStateFlow(0)
    val jumpPulseSeq: StateFlow<Int> = _jumpPulseSeq.asStateFlow()

    private val _usuarioAsignado = MutableStateFlow<String?>(null)
    val usuarioAsignado: StateFlow<String?> = _usuarioAsignado.asStateFlow()

    private val _tomaDocumentoRegistrada = MutableStateFlow(false)
    private val _entradaCerrada = MutableStateFlow(false)
    val entradaCerrada: StateFlow<Boolean> = _entradaCerrada.asStateFlow()

    private val _desbloqueoSolicitado = MutableStateFlow(false)
    val desbloqueoSolicitado: StateFlow<Boolean> = _desbloqueoSolicitado.asStateFlow()

    private val _desbloqueoAprobado = MutableStateFlow(false)
    val desbloqueoAprobado: StateFlow<Boolean> = _desbloqueoAprobado.asStateFlow()

    private val _desbloqueoAprobadoPor = MutableStateFlow("")
    val desbloqueoAprobadoPor: StateFlow<String> = _desbloqueoAprobadoPor.asStateFlow()

    // Se incrementa cada vez que llega una aprobación real por SignalR; la
    // Screen lo usa como disparador de un LaunchedEffect para el pitido +
    // voz "Entrada autorizada" (separado del toast, que es solo texto).
    private val _unlockApprovedSeq = MutableStateFlow(0)
    val unlockApprovedSeq: StateFlow<Int> = _unlockApprovedSeq.asStateFlow()

    private val _isCheckingHazmat = MutableStateFlow(false)
    val isCheckingHazmat: StateFlow<Boolean> = _isCheckingHazmat.asStateFlow()

    private val _validationState = MutableStateFlow<CompletaValidation?>(null)
    val validationState: StateFlow<CompletaValidation?> = _validationState.asStateFlow()

    private val _supervisorObservando = MutableStateFlow<String?>(null)
    val supervisorObservando: StateFlow<String?> = _supervisorObservando.asStateFlow()

    // true = todas las facturas y partidas quedaron completas/revisadas justo
    // ahora; se le pregunta al usuario si quiere finalizar en vez de cerrar
    // la revisión en automático y sin avisar.
    private val _autoFinishPrompt = MutableStateFlow(false)
    val autoFinishPrompt: StateFlow<Boolean> = _autoFinishPrompt.asStateFlow()

    // Computed spectator status
    val isEspectador: StateFlow<Boolean> = combine(
        _usuarioAsignado,
        _tomaDocumentoRegistrada
    ) { assigned, tomo ->
        val user = authRepository.getUsername()
        if (assigned != null && user != null && assigned != user) true
        else if (assigned == null && !tomo) true
        else false
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // RelacionCompleta viene por factura pero refleja el estado de la
    // relación de TODA la entrada (se calcula por cref_agrupada en el
    // backend, igual para cada factura) -- de ahí que baste con revisar
    // cualquiera de ellas. A diferencia de "revisión finalizada"
    // (entradaCerrada), una relación cerrada es un cierre físico/logístico
    // definitivo: no se puede solicitar edición de la entrada aunque estés
    // en modo espectador. Ver comentario espejo en RevisionService.
    // SolicitarDesbloqueoAsync (backend) y en detalle.ts (web).
    val relacionCerrada: StateFlow<Boolean> = _facturas
        .map { facturas -> facturas.any { it.RelacionCompleta } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Filtered Partidas
    val partidasFiltradas: StateFlow<List<PartidaRevisionDto>> = combine(
        _partidas,
        _filtroPartidas,
        _selectedFacturaIndex,
        _facturas
    ) { partidas, filtro, selectedIndex, facturas ->
        val cnofact = facturas.getOrNull(selectedIndex)?.Cnofact?.trim()
        val list = partidas.filter { cnofact == null || it.Cnofact.trim() == cnofact }
        when (filtro) {
            "pendientes" -> list.filter { !it.Revisada }
            "revisadas" -> list.filter { it.Revisada }
            else -> list
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Resultados ordenados por qué tan buena es la coincidencia. Se consideran
    // factura, proveedor, número de parte y descripción de producto; cliente
    // queda deliberadamente fuera del buscador.
    val resultadosBusquedaParte: StateFlow<List<ResultadoBusquedaRevision>> = combine(
        _partidas,
        _facturas,
        _busquedaParte
    ) { partidas, facturas, query ->
        val normalizedQuery = normalizarNumeroParte(query)
        if (normalizedQuery.isEmpty()) return@combine emptyList()

        val indexPorCnofact = facturas.withIndex().associate { (idx, f) -> f.Cnofact.trim() to idx }

        // Posición 1-based dentro de "Todas" las partidas de su factura (sin
        // filtro de estatus): así el salto siempre aterriza en el índice real
        // del carrusel, sin importar si Pendientes/Revisadas estaba activo.
        val posicionPorId = HashMap<Int, Int>()
        val totalPorCnofact = HashMap<String, Int>()
        partidas.groupBy { it.Cnofact.trim() }.forEach { (cnofact, items) ->
            totalPorCnofact[cnofact] = items.size
            items.forEachIndexed { i, p -> posicionPorId[p.IdRevdet] = i + 1 }
        }

        val invoiceResults = facturas.withIndex().mapNotNull { indexed ->
            val invoice = indexed.value
            val score = listOfNotNull(
                scoreCoincidencia(invoice.Cnofact, normalizedQuery),
                scoreCoincidencia(invoice.NombreProveedor.orEmpty(), normalizedQuery)
            ).minOrNull() ?: return@mapNotNull null

            ResultadoBusquedaRevision(
                tipo = TipoResultadoBusqueda.FACTURA,
                factura = invoice,
                partida = null,
                facturaIndex = indexed.index,
                posicion = null,
                totalFactura = partidas.count { it.Cnofact.trim() == invoice.Cnofact.trim() },
                score = score
            )
        }

        val productResults = partidas.mapNotNull { p ->
            val partScore = scoreCoincidencia(p.CnoParte.orEmpty(), normalizedQuery)
            val descriptionScore = scoreCoincidencia(p.cDescripcion.orEmpty(), normalizedQuery)?.plus(3)
            val score = listOfNotNull(partScore, descriptionScore).minOrNull()
                ?: return@mapNotNull null
            val facturaIndex = indexPorCnofact[p.Cnofact.trim()] ?: return@mapNotNull null
            val posicion = posicionPorId[p.IdRevdet] ?: return@mapNotNull null
            val factura = facturas.getOrNull(facturaIndex) ?: return@mapNotNull null
            ResultadoBusquedaRevision(
                tipo = TipoResultadoBusqueda.PRODUCTO,
                factura = factura,
                partida = p,
                facturaIndex = facturaIndex,
                posicion = posicion,
                totalFactura = totalPorCnofact[p.Cnofact.trim()] ?: 0,
                score = score
            )
        }

        (invoiceResults + productResults).sortedWith(compareBy({ it.score }, { it.facturaIndex }, { it.posicion ?: 0 }))
            .take(20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var timerJob: Job? = null
    private var pollingJob: Job? = null
    private var presenceJob: Job? = null
    private var photosJob: Job? = null
    private var isReviewSessionInitializing = true
    private var revisionFinalizada = false

    init {
        loadData()
        initializeReviewSession()
        cargarPaises()
        cargarPaisesFrecuentes()
        cargarUnidadesComerciales()
        cargarFotos()
        observeSignalR()
        verificarAccesoPrevio()
    }

    /**
     * Al abrir la pantalla, revisa si YA existe un desbloqueo aprobado para
     * esta entrada (a nivel ENTRY_) -- sin que el usuario tenga que tocar
     * "Solicitar" primero. Cubre el caso de que un supervisor haya asignado
     * la entrada desde el panel: eso ya cuenta como autorización (ver
     * AsignacionesController.Asignar), así que al entrar debe encontrarse
     * ya desbloqueada, no con el banner de "Solicitar acceso".
     */
    private fun verificarAccesoPrevio() {
        viewModelScope.launch {
            repository.verificarDesbloqueo("ENTRY_$crefAgrupada").onSuccess { res ->
                if (res.success && res.autorizado) {
                    aplicarDesbloqueoAprobado("ENTRY_$crefAgrupada", res.autorizadoPor ?: "", res.fechaExpiracion, mostrarToast = false)
                }
            }
        }
    }

    /**
     * Punto único donde un desbloqueo pasa a estar "aprobado" en esta
     * ViewModel, sin importar si llegó por SignalR en vivo, por el polling
     * de respaldo, o por la revisión al abrir la pantalla. Además de marcar
     * el estado en memoria, lo persiste en UnlockStore (con la fecha de
     * expiración real que manda el servidor) para que sobreviva a salir y
     * volver a entrar mientras siga vigente -- antes UnlockStore.save()
     * nunca se llamaba desde ningún lado, así que el desbloqueo se perdía
     * en cuanto se recreaba la ViewModel.
     */
    private fun aplicarDesbloqueoAprobado(cnofact: String, supervisor: String, fechaExpiracion: String?, mostrarToast: Boolean) {
        _desbloqueoAprobado.value = true
        _desbloqueoAprobadoPor.value = supervisor
        _desbloqueoSolicitado.value = false
        pollingJob?.cancel()
        _unlockApprovedSeq.value++

        val user = authRepository.getUsername()
        if (!user.isNullOrEmpty()) {
            val expiryMs = parseFechaExpiracion(fechaExpiracion)
                ?: (System.currentTimeMillis() + 72 * 60 * 60 * 1000L)
            unlockStore.save(user, crefAgrupada, cnofact, expiryMs)
        }

        if (mostrarToast) {
            _toast.value = if (supervisor.isBlank()) {
                resource(R.string.notification_entry_authorized)
            } else {
                resource(R.string.message_unlock_approved, supervisor)
            }
        }
    }

    /** Formato "yyyy-MM-dd'T'HH:mm:ss" -- el mismo que devuelven VerificarDesbloqueoAsync y el resto de fechas de la API. */
    private fun parseFechaExpiracion(fecha: String?): Long? {
        if (fecha.isNullOrBlank()) return null
        return runCatching {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(fecha)?.time
        }.getOrNull()
    }

    fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getEntradaCompleta(crefAgrupada)
                .onSuccess { entrada ->
                    // OJO: a diferencia de runCatching, Result.onSuccess NO atrapa
                    // excepciones lanzadas dentro de este bloque -- se propagan tal
                    // cual y, como viewModelScope no tiene un CoroutineExceptionHandler,
                    // tronaban toda la app (ver el caso de KATHION E-359357: la
                    // tablet caía justo al "preparar" la entrada). Una entrada con
                    // un dato inesperado no debe poder derribar la app entera; que
                    // muestre el error y deje reintentar, como con una falla de red.
                    try {
                        val facturas = entrada.facturas.orEmpty()
                        val partidas = entrada.partidas.orEmpty()
                        _facturas.value = facturas
                        _partidas.value = partidas
                        _observacionGeneral.value = entrada.observacionRevision.orEmpty()
                        cargarFotos()
                        if (canEdit()) {
                            facturas.getOrNull(_selectedFacturaIndex.value)?.let {
                                repository.registrarEditor(it.Cnofact)
                            }
                        }
                    } catch (e: Exception) {
                        _error.value = resource(
                            R.string.message_data_processing_failed,
                            crefAgrupada
                        )
                    }
                }
                .onFailure {
                _error.value = resource(R.string.message_load_failed)
            }
            _isLoading.value = false
        }
    }

    /** Serializa toma + cronómetro para no competir al abrir el escáner. */
    private fun initializeReviewSession() {
        viewModelScope.launch {
            try {
                if (canEdit()) {
                    repository.registrarTomaDocumento(crefAgrupada)
                        .onSuccess { _tomaDocumentoRegistrada.value = true }
                }

                initializeTimerState()
            } finally {
                isReviewSessionInitializing = false
                sincronizarEstadoTimer()
            }
        }
    }

    private suspend fun initializeTimerState() {
        repository.getEstadoTimer(crefAgrupada).onSuccess { estado ->
                _timerSeconds.value = estado.segundosAcumulados
                _usuarioAsignado.value = estado.usuarioAsignado
                _entradaCerrada.value = estado.finRevision
                if (!estado.finRevision) {
                    val user = authRepository.getUsername()
                    // Auto-iniciar si no hay asignación o es la de este usuario
                    if (canEdit() && (estado.usuarioAsignado == null || estado.usuarioAsignado == user)) {
                        repository.iniciarTimer(crefAgrupada).onSuccess {
                            _timerActivo.value = true
                            startTimerTick()
                        }
                    }
                }
        }
    }

    private fun startTimerTick() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _timerSeconds.value++
            }
        }
    }

    private fun registrarActividadFactura(cnofact: String, actividad: String) {
        if (!canEdit()) return
        authRepository.getUsername()?.takeIf { it.isNotBlank() }?.let { user ->
            hubService.registrarActividad(crefAgrupada, user, cnofact, actividad)
        }
    }

    private fun cargarPaises() {
        viewModelScope.launch {
            repository.getPaises().onSuccess { response ->
                _paises.value = response.items.orEmpty()
                _paisesFrecuentes.value = response.frecuentes.orEmpty()
                RevisionDiagnostics.record(
                    appContext,
                    "Catálogo países cargado items=${response.items?.size ?: 0} " +
                        "frecuentes=${response.frecuentes?.size ?: 0}"
                )
            }.onFailure { error ->
                RevisionDiagnostics.record(appContext, "Error cargando catálogo de países", error)
                _toast.value = resource(
                    R.string.message_countries_failed,
                    error.message ?: appContext.getString(R.string.message_server_review_api)
                )
            }
        }
    }

    private fun cargarPaisesFrecuentes() {
        viewModelScope.launch {
            repository.getPaisesFrecuentes().onSuccess {
                _paisesFrecuentes.value = it
                RevisionDiagnostics.record(appContext, "Catálogo países frecuentes cargado items=${it.size}")
            }.onFailure { error ->
                RevisionDiagnostics.record(appContext, "Error cargando países frecuentes", error)
                // Es una ayuda opcional: el buscador principal de países debe
                // seguir disponible aunque esta consulta falle o tarde.
            }
        }
    }

    private fun cargarUnidadesComerciales() {
        viewModelScope.launch {
            repository.getUnidadesComerciales()
                .onSuccess { items ->
                    _unidadesComerciales.value = items
                    RevisionDiagnostics.record(
                        appContext,
                        "Catálogo UMC cargado items=${items.size}"
                    )
                }
                .onFailure { error ->
                    RevisionDiagnostics.record(appContext, "Error cargando catálogo UMC", error)
                    _toast.value = resource(
                        R.string.message_units_failed,
                        error.message ?: appContext.getString(R.string.message_server_review_api)
                    )
                }
        }
    }

    fun cargarFotos() {
        photosJob?.cancel()
        photosJob = viewModelScope.launch {
            val cnofacts = _facturas.value.map(FacturaRevisionDto::Cnofact)
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
            val references = (listOf(crefAgrupada) + cnofacts).distinct()

            // Las fotos de la revisión son independientes. Cargarlas en
            // paralelo evita que una factura lenta bloquee todas las demás.
            val resultados = references
                .map { reference ->
                    async { reference to repository.getFotos(reference) }
                }
                .awaitAll()
                .toMap()

            _fotosPorFactura.value = cnofacts.associateWith { cnofact ->
                resultados[cnofact]?.getOrNull() ?: emptyList()
            }
            _fotos.value = resultados.values
                .mapNotNull { it.getOrNull() }
                .flatten()
                .distinctBy(FotoRevisionDto::idFotos)
        }
    }

    private fun observeSignalR() {
        presenceJob?.cancel()
        presenceJob = viewModelScope.launch {
            while (isActive) {
                authRepository.getUsername()?.takeIf { it.isNotBlank() }?.let { user ->
                    hubService.registrarPresencia(crefAgrupada, user)
                }
                delay(20_000)
            }
        }
        
        // Registrar actividad de edición al iniciar o cambiar de factura
        viewModelScope.launch {
            combine(_selectedFacturaIndex, _facturas) { index, list ->
                list.getOrNull(index)?.Cnofact
            }.collect { cnofact ->
                val user = authRepository.getUsername() ?: "S/U"
                if (cnofact != null) {
                    hubService.registrarActividad(crefAgrupada, user, cnofact)
                }
            }
        }

        // Supervisor observando
        viewModelScope.launch {
            hubService.supervisorObservandoFlow.collect { msg ->
                if (msg.creferencia == crefAgrupada) {
                    _supervisorObservando.value = msg.supervisor
                    delay(5000)
                    _supervisorObservando.value = null
                }
            }
        }

        // Aprobaciones de desbloqueo -- ya NO exige que _desbloqueoSolicitado
        // esté activo: un supervisor puede otorgar acceso sin que hayamos
        // pedido nada primero (por ejemplo, al asignar la entrada desde el
        // panel -- ver AsignacionesController.Asignar), y ese aviso debe
        // reflejarse igual.
        viewModelScope.launch {
            hubService.desbloqueoAprobadoFlow.collect { msg ->
                val currentFac = _facturas.value.getOrNull(_selectedFacturaIndex.value)?.Cnofact
                if (msg.cnofact == currentFac || msg.cnofact == "ENTRY_$crefAgrupada") {
                    // El mensaje de SignalR no trae fecha_expiracion; se
                    // confirma contra el servidor para persistir la fecha
                    // real en UnlockStore.
                    repository.verificarDesbloqueo(msg.cnofact).onSuccess { res ->
                        aplicarDesbloqueoAprobado(msg.cnofact, msg.supervisor, res.fechaExpiracion, mostrarToast = true)
                    }.onFailure {
                        aplicarDesbloqueoAprobado(msg.cnofact, msg.supervisor, null, mostrarToast = true)
                    }
                }
            }
        }

        // Rechazo / retiro de desbloqueo
        viewModelScope.launch {
            hubService.desbloqueoRechazadoFlow.collect { msg ->
                val currentFac = _facturas.value.getOrNull(_selectedFacturaIndex.value)?.Cnofact
                val esEstaEntrada = msg.cnofact == currentFac || msg.cnofact == "ENTRY_$crefAgrupada"
                if (!esEstaEntrada) return@collect

                if (_desbloqueoSolicitado.value) {
                    _desbloqueoSolicitado.value = false
                    pollingJob?.cancel()
                    _toast.value = resource(R.string.message_unlock_rejected, msg.supervisor)
                }

                // El permiso también puede retirarse cuando ya estaba
                // APROBADO: el supervisor lo revoca desde el Monitor, o el
                // backend lo cierra solo porque la revisión volvió a
                // finalizarse (ver RevisionService.RevocarDesbloqueosDeEntradaAsync,
                // que llega aquí como "SYSTEM"). Antes este flujo solo
                // reaccionaba a una solicitud pendiente, así que un permiso
                // ya otorgado se quedaba "aprobado" en memoria y en
                // UnlockStore aunque el servidor ya lo hubiera cerrado.
                if (_desbloqueoAprobado.value) {
                    _desbloqueoAprobado.value = false
                    authRepository.getUsername()?.let { user ->
                        unlockStore.clear(user, crefAgrupada, msg.cnofact)
                    }
                    _toast.value = if (msg.supervisor.equals("SYSTEM", ignoreCase = true)) {
                        UiText.Resource(R.string.revision_finished)
                    } else {
                        resource(R.string.message_unlock_revoked, msg.supervisor)
                    }
                }
            }
        }

        // Cierre forzoso por supervisor
        viewModelScope.launch {
            hubService.cierreForzosoFlow.collect { msg ->
                val isSameReference = msg.creferencia.isNullOrBlank() ||
                    msg.creferencia.equals(crefAgrupada, ignoreCase = true)
                if (isSameReference && _cierreForzoso.value == null) {
                    revisionFinalizada = true
                    _timerActivo.value = false
                    timerJob?.cancel()
                    pollingJob?.cancel()
                    presenceJob?.cancel()
                    _cierreForzoso.value = msg
                }
            }
        }

        // Los eventos de SignalR usan snapshots completos publicados por el
        // API después de guardar. Se aplican al estado local sin volver a
        // escribirlos para evitar ciclos entre clientes.
        viewModelScope.launch {
            hubService.revisionActualizadaFlow.collect(::aplicarRevisionActualizada)
        }

        viewModelScope.launch {
            hubService.revisionEstadoFlow.collect { message ->
                if (!message.creferencia.equals(crefAgrupada, ignoreCase = true)) return@collect

                if (message.estado.equals("FINALIZADA", ignoreCase = true)) {
                    marcarRevisionFinalizada()
                } else if (
                    message.estado.equals("PAUSADA", ignoreCase = true) &&
                    message.usuario.equals(authRepository.getUsername(), ignoreCase = true)
                ) {
                    _timerActivo.value = false
                    timerJob?.cancel()
                }

                recargarSnapshotRevision()
                if (!isReviewSessionInitializing) sincronizarEstadoTimer()
            }
        }

        viewModelScope.launch {
            hubService.revisionCompletadaFlow.collect { message ->
                if (message.creferencia.equals(crefAgrupada, ignoreCase = true)) {
                    marcarRevisionFinalizada()
                }
            }
        }

        // SignalR no guarda eventos mientras la conexión está pendiente o
        // fuera de línea. Al quedar listo se lee el estado persistido para
        // recuperar cualquier cambio ocurrido en ese intervalo.
        viewModelScope.launch {
            hubService.connectionReadyFlow.collect {
                recargarSnapshotRevision()
                if (!isReviewSessionInitializing) sincronizarEstadoTimer()
            }
        }

        // Los collectors quedan instalados antes de iniciar/reusar el hub;
        // así no se pierde el primer evento en una conexión ya activa.
        hubService.startConnection()
    }

    private fun aplicarRevisionActualizada(message: RevisionActualizadaMessage) {
        if (!message.creferencia.equals(crefAgrupada, ignoreCase = true)) return

        when (message.entidad.uppercase(Locale.ROOT)) {
            "FACTURA" -> {
                val factura = message.factura ?: return
                _facturas.update { facturas ->
                    facturas.map { current ->
                        if (current.Cnofact.equals(factura.Cnofact, ignoreCase = true)) factura else current
                    }
                }
            }
            "PARTIDA" -> {
                val partida = message.partida ?: return
                _partidas.update { partidas ->
                    (partidas.filterNot { it.IdRevdet == partida.IdRevdet } + partida)
                        .sortedWith(compareBy<PartidaRevisionDto>({ it.Cnofact }, { it.IdRevdet }))
                }
            }
            "PARTIDA_ELIMINADA" -> {
                val idRevdet = message.idRevdet ?: return
                _partidas.update { partidas -> partidas.filterNot { it.IdRevdet == idRevdet } }
            }
            "ENTRADA" -> _observacionGeneral.value = message.observacionRevision.orEmpty()
            "EDITORES_ENTRADA" -> {
                val facturasLiberadas = message.cnofacts.orEmpty().map { it.trim() }.toSet()
                if (facturasLiberadas.isEmpty()) return
                _facturas.update { facturas ->
                    facturas.map { factura ->
                        if (facturasLiberadas.contains(factura.Cnofact.trim())) factura.copy(UsuEdit = "") else factura
                    }
                }
            }
            "FOTOS" -> cargarFotos()
        }
    }

    private fun recargarSnapshotRevision() {
        viewModelScope.launch {
            repository.getEntradaCompleta(crefAgrupada)
                .onSuccess { response ->
                    if (!response.success) return@onSuccess

                    val cnofactSeleccionada = _facturas.value
                        .getOrNull(_selectedFacturaIndex.value)
                        ?.Cnofact
                    val facturasActualizadas = response.facturas.orEmpty()
                    _facturas.value = facturasActualizadas
                    _partidas.value = response.partidas.orEmpty()
                    _observacionGeneral.value = response.observacionRevision.orEmpty()
                    cargarFotos()

                    val nuevoIndice = cnofactSeleccionada?.let { cnofact ->
                        facturasActualizadas.indexOfFirst { it.Cnofact.equals(cnofact, ignoreCase = true) }
                    } ?: -1
                    _selectedFacturaIndex.value = if (nuevoIndice >= 0) {
                        nuevoIndice
                    } else {
                        _selectedFacturaIndex.value.coerceIn(0, (facturasActualizadas.size - 1).coerceAtLeast(0))
                    }
                    _partidaActivaIndex.value = _partidaActivaIndex.value.coerceAtLeast(1)
                }
                .onFailure { error ->
                    RevisionDiagnostics.record(appContext, "Error actualizando revisión en tiempo real", error)
                }
        }
    }

    private fun marcarRevisionFinalizada() {
        revisionFinalizada = true
        _entradaCerrada.value = true
        _timerActivo.value = false
        timerJob?.cancel()
        pollingJob?.cancel()
    }

    private fun sincronizarEstadoTimer() {
        viewModelScope.launch {
            repository.getEstadoTimer(crefAgrupada)
                .onSuccess { estado ->
                    revisionFinalizada = estado.finRevision
                    _entradaCerrada.value = estado.finRevision
                    _usuarioAsignado.value = estado.usuarioAsignado

                    if (estado.finRevision) {
                        marcarRevisionFinalizada()
                    } else if (estado.activo) {
                        _timerActivo.value = true
                        if (timerJob?.isActive != true) {
                            _timerSeconds.value = estado.segundosAcumulados
                            startTimerTick()
                        }
                    } else {
                        _timerActivo.value = false
                        timerJob?.cancel()
                        _timerSeconds.value = estado.segundosAcumulados
                    }
                }
                .onFailure { error ->
                    RevisionDiagnostics.record(appContext, "Error sincronizando el cronómetro de revisión", error)
                }
        }
    }

    fun selectFactura(index: Int) {
        if (index == _selectedFacturaIndex.value) return
        val prev = _facturas.value.getOrNull(_selectedFacturaIndex.value)
        val next = _facturas.value.getOrNull(index)
        _selectedFacturaIndex.value = index
        _partidaActivaIndex.value = 1 // Resetear carrusel de partidas
        
        // R13: el candado de edición también es una escritura. Un usuario con
        // sólo Lectura puede cambiar de factura sin registrar actividad de
        // edición en el servidor.
        if (canEdit()) {
            prev?.let { viewModelScope.launch { repository.limpiarEditor(it.Cnofact) } }
            next?.let { viewModelScope.launch { repository.registrarEditor(it.Cnofact) } }
        }
    }

    fun canCreate(): Boolean = authRepository.hasPermission(159, "Agregar")

    fun canEdit(): Boolean = authRepository.hasPermission(159, "Modificar")

    fun canDelete(): Boolean = authRepository.hasPermission(159, "Borrar")

    private fun denyPermission(@StringRes actionRes: Int) {
        _toast.value = resource(R.string.message_permission_denied, appContext.getString(actionRes))
    }

    // --- Facturas Logic ---
    fun guardarCampoFactura(cnofact: String, campo: String, valor: Any) {
        if (!canEdit()) {
            denyPermission(R.string.permission_modify_fields)
            return
        }
        viewModelScope.launch {
            // Guardar localmente
            val modelField = when (campo.lowercase()) {
                "totbultos" -> "TotBultos"
                "totbultosdoc" -> "TotBultosDoc"
                "marcas" -> "Marcas"
                "numeros" -> "Numeros"
                "obsrev" -> "Obsrev"
                "discrepancias" -> "Discrepancias"
                "npesolbs" -> "nPesoLbs"
                "npesokgs" -> "nPesoKgs"
                else -> campo
            }

            _facturas.value = _facturas.value.map { f ->
                    if (f.Cnofact == cnofact) {
                        when (modelField) {
                            "TotBultos" -> f.copy(TotBultos = (valor as? String)?.toIntOrNull() ?: (valor as? Int) ?: 0)
                            "TotBultosDoc" -> f.copy(TotBultosDoc = (valor as? String)?.toIntOrNull() ?: (valor as? Int) ?: 0)
                            "Marcas" -> f.copy(Marcas = valor as? String)
                        "Numeros" -> f.copy(Numeros = valor as? String)
                        "Obsrev" -> f.copy(Obsrev = valor as? String)
                        "Discrepancias" -> f.copy(Discrepancias = valor as? String)
                        "nPesoLbs" -> f.copy(nPesoLbs = (valor as? String)?.toDoubleOrNull() ?: (valor as? Double) ?: 0.0)
                        "nPesoKgs" -> f.copy(nPesoKgs = (valor as? String)?.toDoubleOrNull() ?: (valor as? Double) ?: 0.0)
                        else -> f
                    }
                } else f
            }

            // La conversión libras -> kilos (mismo factor que VFP, 0.4536) ya
            // la hace el API en una sola actualización atómica al guardar
            // "npesolbs" -- por eso el refreshFactura() de abajo basta para
            // traer el kg recalculado, sin un segundo guardado desde aquí.
            repository.guardarCampoFactura(cnofact, campo, valor)
                .onSuccess {
                    registrarActividadFactura(cnofact, "Campo actualizado: $campo")
                    refreshFactura(cnofact)
                }
                .onFailure { _toast.value = resource(R.string.message_save_field_error) }
        }
    }

    private fun refreshFactura(cnofact: String) {
        viewModelScope.launch {
            repository.getRevisionFactura(cnofact).onSuccess { updated ->
                _facturas.value = _facturas.value.map { f ->
                    if (f.Cnofact == cnofact) updated else f
                }
            }
        }
    }

    // --- Validation Completa flow ---
    fun setStatusFactura(cnofact: String, status: Int, skipChecks: Boolean = false) {
        if (!canEdit()) {
            denyPermission(R.string.permission_update_status)
            return
        }
        val fac = _facturas.value.find { it.Cnofact == cnofact } ?: return
        if (status == 1 && !skipChecks) {
            viewModelScope.launch {
                // 1. Diferencia de bultos
                if (fac.TotBultosDoc > 0 && fac.TotBultos != fac.TotBultosDoc) {
                    _validationState.value = CompletaValidation.BultosDiferencia(cnofact, fac.TotBultos, fac.TotBultosDoc)
                    return@launch
                }
                // 2. Hazmat
                checkHazmatValidation(cnofact)
            }
        } else {
            saveStatusFactura(cnofact, status)
        }
    }

    fun onConfirmBultosDiferencia(cnofact: String) {
        _validationState.value = null

        // DESACTIVADO A PROPÓSITO (14/sept, feedback de Eduardo: "no está bien
        // hecho y estorba un poco, hay que aterrizarlo mejor"). La idea sigue
        // siendo válida -- "Bultos físicos" (fact.totbultos) es justo lo que
        // frmopera_factura.scx muestra junto a "Bultos documento", y si se
        // queda en 0 porque nadie tocó el campo, esa porción de la ficha se ve
        // permanentemente en blanco (doc=3, físicos=0 fue el caso reportado)
        // -- pero el auto-guardado silencioso aquí no quedó bien afinado.
        // Antes de reactivarlo: revisar con Eduardo en qué casos molesta y si
        // conviene avisar/confirmar con el revisor en vez de guardar solo.
        // Mientras tanto el diálogo vuelve a ser solo informativo, como
        // estaba antes de este cambio.
        // val fac = _facturas.value.find { it.Cnofact == cnofact }
        // if (fac != null && fac.TotBultos == 0 && fac.TotBultosDoc > 0) {
        //     viewModelScope.launch {
        //         repository.guardarCampoFactura(cnofact, "totbultos", fac.TotBultosDoc)
        //             .onSuccess { refreshFactura(cnofact) }
        //     }
        // }

        checkHazmatValidation(cnofact)
    }

    private fun checkHazmatValidation(cnofact: String) {
        _isCheckingHazmat.value = true
        viewModelScope.launch {
            repository.validarHazmat(cnofact).onSuccess { problemas ->
                _isCheckingHazmat.value = false
                if (problemas.isNotEmpty()) {
                    val list = problemas.map { "${it.cnoparte ?: ""}: ${it.problema ?: ""}" }
                    _validationState.value = CompletaValidation.HazmatAlerta(cnofact, list)
                } else {
                    checkEvidenciaDiscrepanciaValidation(cnofact)
                }
            }.onFailure {
                _isCheckingHazmat.value = false
                checkEvidenciaDiscrepanciaValidation(cnofact)
            }
        }
    }

    fun onConfirmHazmatAlerta(cnofact: String) {
        if (!canEdit()) {
            denyPermission(R.string.permission_update_status)
            return
        }
        _validationState.value = null
        checkEvidenciaDiscrepanciaValidation(cnofact)
    }

    private fun checkEvidenciaDiscrepanciaValidation(cnofact: String) {
        val cantidad = _fotosPorFactura.value[cnofact]?.count { it.esDiscrepancia } ?: 0
        if (cantidad > 0) {
            _validationState.value = CompletaValidation.EvidenciaDiscrepanciaSinMarcar(cnofact, cantidad)
        } else {
            checkPartidasSinRevisarValidation(cnofact)
        }
    }

    // "Continuar como completa": el usuario confirma que, aunque tomó fotos de
    // evidencia de discrepancia, la factura sí quedó bien y sigue el flujo normal.
    fun onConfirmEvidenciaDiscrepancia(cnofact: String) {
        if (!canEdit()) {
            denyPermission(R.string.permission_update_status)
            return
        }
        _validationState.value = null
        checkPartidasSinRevisarValidation(cnofact)
    }

    private fun checkPartidasSinRevisarValidation(cnofact: String) {
        val sinRevisar = _partidas.value.filter { !it.Revisada && it.Cnofact == cnofact }
        if (sinRevisar.isNotEmpty()) {
            val ejemplos = sinRevisar.map { it.CnoParte ?: "Sin SKU" }.take(5)
            _validationState.value = CompletaValidation.PartidasSinRevisar(cnofact, sinRevisar.size, ejemplos)
        } else {
            checkDiscrepanciasValidation(cnofact)
        }
    }

    fun onConfirmPartidasSinRevisar(cnofact: String, motivo: String) {
        if (!canEdit()) {
            denyPermission(R.string.permission_request_unlock)
            return
        }
        _validationState.value = null
        solicitarDesbloqueo("[CIERRE INCOMPLETO] $motivo")
    }

    private fun checkDiscrepanciasValidation(cnofact: String) {
        val fac = _facturas.value.find { it.Cnofact == cnofact } ?: return
        if (!fac.Discrepancias.isNullOrBlank()) {
            _validationState.value = CompletaValidation.DiscrepanciasAclarar(cnofact, fac.Discrepancias)
        } else {
            saveStatusFactura(cnofact, 1)
        }
    }

    fun onConfirmAclararDiscrepancias(cnofact: String, autorizada: Boolean) {
        if (!canEdit()) {
            denyPermission(R.string.permission_clarify_discrepancies)
            return
        }
        _validationState.value = null
        if (autorizada) {
            viewModelScope.launch {
                repository.aclararDiscrepancias(cnofact).onSuccess {
                    registrarActividadFactura(cnofact, "Discrepancias aclaradas")
                    val fac = _facturas.value.find { it.Cnofact == cnofact }
                    val usuario = authRepository.getName() ?: "S/U"
                    val notaAclaracion = "${fac?.Discrepancias} AUTORIZO: $usuario"
                    val nuevoObs2 = if (!fac?.Obsrev2.isNullOrBlank()) "${fac.Obsrev2} | $notaAclaracion" else notaAclaracion
                    
                    repository.guardarCampoFactura(cnofact, "obsrev2", nuevoObs2)
                    repository.guardarCampoFactura(cnofact, "discrepancias", "")
                    
                    saveStatusFactura(cnofact, 1)
                }
            }
        } else {
            saveStatusFactura(cnofact, 2)
        }
    }

    fun dismissValidationDialog() {
        _validationState.value = null
    }

    private fun saveStatusFactura(cnofact: String, status: Int) {
        if (!canEdit()) {
            denyPermission(R.string.permission_update_status)
            return
        }
        viewModelScope.launch {
            repository.guardarCampoFactura(cnofact, "status_rev", status)
                .onSuccess {
                    registrarActividadFactura(cnofact, if (status == 1) "Factura completada" else "Factura marcada con discrepancia")
                    refreshFactura(cnofact)
                    checkAutoFinish()
                }
                .onFailure { _toast.value = resource(R.string.message_update_status_failed) }
        }
    }

    private fun checkAutoFinish() {
        val total = _facturas.value.size
        val completas = _facturas.value.count { it.StatusRev == 1 }
        val partidasTotales = _partidas.value.size
        val partidasRevisadas = _partidas.value.count { it.Revisada }
        val es100 = completas == total && (partidasTotales == 0 || partidasRevisadas == partidasTotales)

        if (es100) {
            _autoFinishPrompt.value = true
        }
    }

    fun confirmAutoFinish() {
        _autoFinishPrompt.value = false
        terminarRevision()
    }

    fun dismissAutoFinish() {
        _autoFinishPrompt.value = false
    }

    // --- Partidas Logic ---
    fun setFiltroPartidas(filtro: String) {
        _filtroPartidas.value = filtro
        _partidaActivaIndex.value = 1
    }

    fun setBusquedaParte(query: String) {
        _busquedaParte.value = query
    }

    fun limpiarBusquedaParte() {
        _busquedaParte.value = ""
    }

    /**
     * Salta directo a la partida de un resultado de búsqueda: cambia de
     * factura si hace falta, quita el filtro Pendientes/Revisadas (para que
     * la partida encontrada no quede oculta) y mueve el índice activo.
     * Dispara jumpPulseSeq para que la Screen resalte el formulario.
     */
    fun saltarAResultadoBusqueda(resultado: ResultadoBusquedaRevision) {
        if (resultado.facturaIndex != _selectedFacturaIndex.value) {
            selectFactura(resultado.facturaIndex)
        }
        if (resultado.tipo == TipoResultadoBusqueda.PRODUCTO && _filtroPartidas.value != "todas") {
            _filtroPartidas.value = "todas"
        }
        resultado.posicion?.let { _partidaActivaIndex.value = it }
        _busquedaParte.value = ""
        _jumpPulseSeq.value++
        _toast.value = if (resultado.tipo == TipoResultadoBusqueda.PRODUCTO) {
            resource(
                R.string.message_search_part_selected,
                resultado.posicion ?: 0,
                resultado.totalFactura,
                resultado.factura.Cnofact
            )
        } else {
            resource(R.string.message_search_invoice_selected, resultado.factura.Cnofact)
        }
    }

    // Normaliza para comparar números de parte de forma tolerante: mismas
    // mayúsculas y sin espacios/guiones/puntos, porque el bulto físico casi
    // nunca trae el código transcrito idéntico al del documento.
    private fun normalizarNumeroParte(valor: String): String =
        valor.trim().uppercase(Locale.ROOT).replace(Regex("[\\s\\-._]"), "")

    // Para comparar contra el artículo de una etiqueta estructurada (ver
    // StructuredLabelBarcodeParser) además se ignoran ceros a la izquierda:
    // el ERP del proveedor suele traer el número interno con padding
    // (ej. "0049106577") mientras que revdet.cnoparte típicamente se captura
    // con el número corto real ("49106577").
    private fun normalizarArticulo(valor: String): String =
        normalizarNumeroParte(valor).trimStart('0').ifEmpty { "0" }

    /**
     * Punto de entrada de todo escaneo que llega al buscador global
     * (buscarParte). Si el código trae la forma de una etiqueta segmentada
     * conocida (ver [StructuredLabelBarcodeParser]) -- ej. las etiquetas de
     * compuesto químico de Freudenberg -- extrae artículo y cantidad,
     * salta directo a la partida que coincide y ACUMULA la cantidad
     * escaneada sobre lo que ya llevaba contado (una partida puede llegar
     * repartida en varias tarimas/etiquetas). Si el código no tiene esa
     * forma, o no se encuentra ninguna partida con ese artículo, cae de
     * vuelta al comportamiento anterior: buscar el texto tal cual en el
     * buscador normal.
     */
    fun procesarCodigoEscaneado(rawCode: String) {
        val etiqueta = StructuredLabelBarcodeParser.parse(rawCode)
        val articulo = etiqueta?.articulo
        if (articulo == null) {
            setBusquedaParte(rawCode)
            return
        }

        val articuloNormalizado = normalizarArticulo(articulo)
        val partidasActuales = _partidas.value
        val candidatos = partidasActuales.filter {
            normalizarArticulo(it.CnoParte.orEmpty()) == articuloNormalizado
        }
        // A propósito NO se elige automáticamente cuando hay más de una
        // partida con el mismo artículo (dos lotes de un mismo proveedor, o
        // -peor- una coincidencia casual entre dos proveedores distintos
        // después de quitar ceros a la izquierda). Actuar solo -- sin que el
        // revisor confirme nada -- exige que el match sea inequívoco; si no,
        // se cae al comportamiento de siempre (buscador de texto) para que
        // la persona elija a mano cuál es la correcta.
        val partida = candidatos.singleOrNull()

        val facturaIndex = partida?.let { p ->
            _facturas.value.indexOfFirst { it.Cnofact.trim() == p.Cnofact.trim() }
        } ?: -1
        val factura = if (facturaIndex >= 0) _facturas.value.getOrNull(facturaIndex) else null

        if (partida == null || factura == null) {
            // Se busca por el artículo ya limpio (sin el resto de la
            // etiqueta) en vez del código crudo, para que al menos el
            // buscador normal muestre coincidencias parciales.
            setBusquedaParte(articulo)
            _toast.value = if (candidatos.size > 1) {
                resource(R.string.message_search_multiple_parts, candidatos.size, articulo)
            } else {
                resource(R.string.message_search_no_part, articulo)
            }
            return
        }

        val partidasFactura = partidasActuales.filter { it.Cnofact.trim() == partida.Cnofact.trim() }
        val posicion = partidasFactura.indexOfFirst { it.IdRevdet == partida.IdRevdet } + 1

        saltarAResultadoBusqueda(
            ResultadoBusquedaRevision(
                tipo = TipoResultadoBusqueda.PRODUCTO,
                factura = factura,
                partida = partida,
                facturaIndex = facturaIndex,
                posicion = posicion.takeIf { it > 0 },
                totalFactura = partidasFactura.size,
                score = 0
            )
        )

        val cantidad = etiqueta.cantidad
        if (cantidad != null && cantidad > 0) {
            val cantidadPrevia = partida.nCantOut ?: 0.0
            val nuevoTotal = cantidadPrevia + cantidad
            actualizarDatoPartida(partida, "nCantOut", nuevoTotal)
            // Se limpia el toast del salto (saltarAResultadoBusqueda ya puso el
            // suyo arriba) para que no se amontonen dos snackbars por el mismo
            // escaneo -- el de abajo, con "Deshacer", ya incluye a dónde saltó.
            _toast.value = null
            // La etiqueta cuenta piezas y la factura puede ir en cientos o millares («2.1 CNT» son 210 piezas):
            // el aviso compara en piezas, no un 210 contra un 2.1.
            val enPiezas = CountUnits.toPieces(partida.nCantFactura, partida.UnidadFactura)
            _ultimaAcumulacion.value = DeshacerAcumulacion(
                idRevdet = partida.IdRevdet,
                cantidadPrevia = cantidadPrevia,
                cantidadEsperadaActual = nuevoTotal,
                mensaje = if (enPiezas != null) {
                    resource(
                        R.string.message_scan_accumulated_pieces,
                        posicion.takeIf { it > 0 } ?: "?",
                        articulo,
                        quantityText(cantidad),
                        quantityText(nuevoTotal),
                        quantityText(enPiezas)
                    )
                } else {
                    resource(
                        R.string.message_scan_accumulated,
                        posicion.takeIf { it > 0 } ?: "?",
                        articulo,
                        quantityText(cantidad),
                        quantityText(nuevoTotal),
                        quantityText(partida.nCantFactura)
                    )
                }
            )
        }
    }

    /**
     * Cuenta el empaque de una etiqueta recién leída: lo suma a su partida, escribe el total en la
     * cantidad recibida y deja listo el «Deshacer».
     *
     * La diferencia con el «escanear y sumar» de [procesarCodigoEscaneado] es el **libro**: las
     * partidas llegan con la cantidad recibida precargada con la de la factura, así que sumar encima
     * daría de más. Aquí el primer empaque de una partida **reemplaza** ese valor y los siguientes se
     * suman; y un empaque con identificador no se cuenta dos veces aunque se relea su etiqueta.
     *
     * @param multiple cuántos empaques idénticos representa la lectura («son 50 cajas iguales»).
     */
    fun contarEmpaque(
        campos: List<LabelFieldExtractor.CampoLeido>,
        codigos: List<String>,
        textoImpreso: String,
        multiple: Int = 1
    ) {
        if (!canEdit()) {
            denyPermission(R.string.permission_modify_parts)
            return
        }
        val lectura = PackageTally.read(campos, codigos, textoImpreso)
        if (lectura !is PackageTally.Reading.Ready) {
            // Sin clave de un código de barras no se cuenta solo: el revisador sigue por la tarjeta.
            when (lectura) {
                is PackageTally.Reading.SeveralLabels ->
                    _toast.value = resource(R.string.message_tally_several_labels)
                is PackageTally.Reading.QuantityNotCorroborated ->
                    _toast.value = resource(R.string.message_tally_quantity_not_corroborated, lectura.fromBarcode)
                else -> Unit
            }
            return
        }

        when (val resultado = PackageTally.process(_partidas.value, _libroEmpaques.value, lectura.label, multiple)) {
            is PackageTally.Result.Added -> aplicarConteo(resultado)
            is PackageTally.Result.AlreadyCounted ->
                _toast.value = resource(R.string.message_tally_already_counted, resultado.where?.CnoParte.orEmpty())
            is PackageTally.Result.AlreadyReviewed ->
                _toast.value = resource(R.string.message_tally_already_reviewed, resultado.partida.CnoParte.orEmpty())
            is PackageTally.Result.MissingQuantity ->
                _toast.value = resource(R.string.message_tally_missing_quantity, resultado.partida.CnoParte.orEmpty())
            is PackageTally.Result.NotInEntry ->
                _toast.value = resource(R.string.message_tally_not_in_entry, resultado.key)
            is PackageTally.Result.SeveralPartidas ->
                _toast.value = resource(R.string.message_tally_several_partidas, resultado.candidates.size)
            is PackageTally.Result.UnsupportedUnit ->
                _toast.value = resource(R.string.message_tally_unsupported_unit, resultado.unit)
            is PackageTally.Result.UnitNotConfirmed ->
                _toast.value = resource(R.string.message_tally_unit_not_confirmed)
            is PackageTally.Result.QuantityOutOfRange ->
                _toast.value = resource(
                    if (resultado.dimension == CountUnits.Dimension.KILOS) R.string.message_tally_quantity_out_of_range_kg
                    else R.string.message_tally_quantity_out_of_range,
                    quantityText(resultado.perPackage),
                    quantityText(resultado.expected)
                )
        }
    }

    private fun aplicarConteo(resultado: PackageTally.Result.Added) {
        guardarLibro(_libroEmpaques.value.plus(resultado.counted))
        actualizarDatoPartida(resultado.partida, "nCantOut", resultado.totalAfter)
        _toast.value = null

        val pesoLeido = PesoDeEtiqueta.avisoAcumuladoRecurso(
            empaquesPesados = _libroEmpaques.value.weighedCount(resultado.partida.IdRevdet),
            empaquesContados = _libroEmpaques.value.ofPartida(resultado.partida.IdRevdet).size,
            pesoAcumuladoKg = resultado.totalWeightKg,
            pesoDeclaradoKg = resultado.expectedWeightKg
        )?.let { (id, args) -> resource(id, *args) }
        _ultimaAcumulacion.value = DeshacerAcumulacion(
            idRevdet = resultado.partida.IdRevdet,
            cantidadPrevia = resultado.previousReceived ?: 0.0,
            cantidadEsperadaActual = resultado.totalAfter,
            mensaje = resource(
                if (resultado.dimension == CountUnits.Dimension.KILOS) R.string.message_tally_added_kg
                else R.string.message_tally_added,
                resultado.partida.CnoParte.orEmpty(),
                quantityText(resultado.counted.pieces),
                quantityText(resultado.totalAfter),
                quantityText(resultado.expected)
            ),
            empaque = resultado.counted
        )
        // El peso va en su propio aviso: es información de cotejo, no cambia nada de la partida.
        pesoLeido?.let { _toast.value = it }
    }

    /**
     * Revierte la última acumulación automática de un escaneo, solo si nadie
     * más tocó `nCantOut` desde entonces (otro escaneo, una edición manual).
     * Si ya cambió, se avisa en vez de pisar a ciegas lo que haya ahora.
     */
    fun deshacerAcumulacion(evento: DeshacerAcumulacion) {
        val partida = _partidas.value.find { it.IdRevdet == evento.idRevdet }
        if (partida == null) {
            _toast.value = resource(R.string.message_undo_not_loaded)
            return
        }
        val actual = partida.nCantOut ?: 0.0
        if (kotlin.math.abs(actual - evento.cantidadEsperadaActual) > 0.0001) {
            _toast.value = resource(R.string.message_undo_changed)
            return
        }
        // Quitar el empaque del libro ANTES de decidir qué cantidad dejar: si era el único de la
        // partida vuelve lo que traía, y si quedan otros vuelve lo que esos suman.
        val cantidadQueVuelve = evento.empaque?.let { empaque ->
            val sinEl = _libroEmpaques.value.minus(empaque)
            guardarLibro(sinEl)
            PackageTally.receivedAfterUndo(sinEl, empaque, evento.cantidadPrevia)
        } ?: evento.cantidadPrevia

        actualizarDatoPartida(partida, "nCantOut", cantidadQueVuelve)
        _toast.value = resource(R.string.message_undone, quantityText(cantidadQueVuelve))
    }

    private fun scoreCoincidencia(valor: String, query: String): Int? {
        val normalizedValue = normalizarNumeroParte(valor)
        if (normalizedValue.isEmpty()) return null
        return when {
            normalizedValue == query -> 0
            normalizedValue.startsWith(query) -> 1
            normalizedValue.contains(query) -> 2
            else -> null
        }
    }

    fun saltarAPartida(delta: Int) {
        val list = partidasFiltradas.value
        if (list.isEmpty()) return
        val actual = _partidaActivaIndex.value
        val objetivo = (actual + delta).coerceIn(1, list.size)
        if (objetivo != actual) {
            _navDirection.value = if (delta > 0) "next" else "prev"
            _partidaActivaIndex.value = objetivo
        }
    }

    fun actualizarDatoPartida(partida: PartidaRevisionDto, campo: String, valor: Any) {
        if (!canEdit()) {
            denyPermission(R.string.permission_modify_parts)
            return
        }
        viewModelScope.launch {
            // Actualizar localmente
            _partidas.value = _partidas.value.map { p ->
                if (p.IdRevdet == partida.IdRevdet) {
                    when (campo) {
                        "marca" -> p.copy(Marca = valor as? String)
                        "modelo" -> p.copy(Modelo = valor as? String)
                        "numSer" -> p.copy(NumSer = valor as? String)
                        "numLote" -> p.copy(NumLote = valor as? String)
                        "nCantOut" -> p.copy(nCantOut = (valor as? String)?.toDoubleOrNull() ?: (valor as? Double) ?: 0.0)
                        "cPaisOrigen" -> p.copy(cPaisOrigen = valor as? String)
                        "tipo_material" -> p.copy(TipoMaterial = valor as? String)
                        "cpt_cve_tmateria" -> p.copy(CptCveTmateria = valor as? String)
                        "cpt_desc_tmateria" -> p.copy(CptDescTmateria = valor as? String)
                        "revisada" -> p.copy(Revisada = valor as? Boolean ?: false)
                        "obsrevpartida" -> p.copy(Obsrevpartida = valor as? String)
                        "mnotapartida" -> p.copy(Mnotapartida = valor as? String)
                        "npeso" -> p.copy(nPeso = (valor as? String)?.toDoubleOrNull() ?: (valor as? Double) ?: 0.0)
                        // "npesolibras" es virtual (no hay campo local que actualizar,
                        // solo dispara la regla legacy de ncanttarifa en el API).
                        else -> p
                    }
                } else p
            }

            repository.guardarCampoPartida(partida.IdRevdet, campo, valor)
                .onSuccess { registrarActividadFactura(partida.Cnofact, "Partida · $campo actualizado") }
                .onFailure { error ->
                    RevisionDiagnostics.record(
                        appContext,
                        "Error guardando partida id=${partida.IdRevdet} campo=$campo",
                        error
                    )
                    _toast.value = resource(
                        R.string.message_save_field_failed,
                        campo,
                        error.message ?: appContext.getString(R.string.message_server_review_api)
                    )
                    loadData()
                }
        }
    }

    /**
     * Guarda el par cantidad + unidad con la misma validación del servidor
     * que usa PMM legacy. La unidad visible (por ejemplo PIES) se transforma
     * en el API a su UMC interna (LF) y a cunidadfactura en una sola operación.
     */
    fun actualizarCantidadComercial(
        partida: PartidaRevisionDto,
        cantidad: Double,
        codigoUnidad: String
    ) {
        if (!canEdit()) {
            denyPermission(R.string.permission_modify_parts)
            return
        }

        val unidad = _unidadesComerciales.value.firstOrNull {
            it.codigo.equals(codigoUnidad.trim(), ignoreCase = true)
        }
        if (unidad == null) {
            _toast.value = resource(R.string.message_invalid_commercial_unit)
            return
        }
        if (!cantidad.isFinite() || cantidad < 0) {
            _toast.value = resource(R.string.message_negative_commercial_quantity)
            return
        }

        viewModelScope.launch {
            _partidas.value = _partidas.value.map { p ->
                if (p.IdRevdet == partida.IdRevdet) {
                    p.copy(
                        CantidadComercial = cantidad,
                        UnidadComercial = unidad.codigoInterno,
                        UnidadFactura = unidad.codigo
                    )
                } else {
                    p
                }
            }

            repository.guardarCantidadComercial(partida.IdRevdet, cantidad, unidad.codigo)
                .onSuccess {
                    registrarActividadFactura(
                        partida.Cnofact,
                        "Partida · cantidad comercial actualizada"
                    )
                }
                .onFailure { error ->
                    RevisionDiagnostics.record(
                        appContext,
                        "Error guardando cantidad comercial id=${partida.IdRevdet}",
                        error
                    )
                    _toast.value = resource(
                        R.string.message_save_commercial_failed,
                        error.message ?: appContext.getString(R.string.message_server_review_api)
                    )
                    loadData()
                }
        }
    }

    fun quickFillPartida(partida: PartidaRevisionDto, campo: String, valor: String) {
        actualizarDatoPartida(partida, campo, valor)
    }

    fun toggleRevisadoPartida(partida: PartidaRevisionDto) {
        if (!canEdit()) {
            denyPermission(R.string.permission_mark_parts)
            return
        }
        val nuevoEstado = !partida.Revisada
        viewModelScope.launch {
            _partidas.value = _partidas.value.map { p ->
                if (p.IdRevdet == partida.IdRevdet) p.copy(Revisada = nuevoEstado) else p
            }

            repository.guardarCampoPartida(partida.IdRevdet, "revisada", nuevoEstado)
                .onSuccess {
                    registrarActividadFactura(partida.Cnofact, if (nuevoEstado) "Partida revisada" else "Partida reabierta")
                    if (nuevoEstado) {
                        delay(300)
                        saltarAPartida(1)
                    }
                }
                .onFailure {
                    _toast.value = resource(R.string.message_part_update_failed)
                    loadData()
                }
        }
    }

    fun agregarPartida(cnofact: String) {
        if (!canCreate()) {
            denyPermission(R.string.permission_add_parts)
            return
        }
        viewModelScope.launch {
            repository.agregarPartida(cnofact, crefAgrupada)
                .onSuccess {
                    registrarActividadFactura(cnofact, "Partida agregada")
                    repository.getPartidas(crefAgrupada).onSuccess { list ->
                        _partidas.value = list
                        // Encontrar la nueva y seleccionarla
                        list.maxByOrNull { it.IdRevdet }?.let { nueva ->
                            val filterList = list.filter { it.Cnofact == cnofact }
                            val idx = filterList.indexOfFirst { it.IdRevdet == nueva.IdRevdet }
                            if (idx != -1) _partidaActivaIndex.value = idx + 1
                        }
                    }
                    _toast.value = resource(R.string.message_part_added)
                }
                .onFailure { _toast.value = resource(R.string.message_part_add_failed) }
        }
    }

    fun eliminarPartida(id: Int) {
        if (!canDelete()) {
            denyPermission(R.string.permission_delete_parts)
            return
        }
        _partidas.value = _partidas.value.filter { it.IdRevdet != id }
        viewModelScope.launch {
            repository.eliminarPartida(id)
                .onSuccess {
                    _facturas.value.getOrNull(_selectedFacturaIndex.value)?.Cnofact?.let { cnofact ->
                        registrarActividadFactura(cnofact, "Partida eliminada")
                    }
                    _partidaActivaIndex.value = 1
                    _toast.value = resource(R.string.message_part_deleted)
                }
                .onFailure {
                    repository.getPartidas(crefAgrupada).onSuccess { _partidas.value = it }
                    _toast.value = resource(R.string.message_part_delete_failed)
                }
        }
    }

    // --- Desbloqueos ---
    fun solicitarDesbloqueo(motivo: String) {
        if (!canEdit()) {
            denyPermission(R.string.permission_request_unlock)
            return
        }
        val fac = _facturas.value.getOrNull(_selectedFacturaIndex.value)
        val cnofact = fac?.Cnofact ?: "ENTRY_$crefAgrupada"
        _desbloqueoSolicitado.value = true
        viewModelScope.launch {
            repository.solicitarDesbloqueo(cnofact, motivo)
                .onSuccess {
                    _toast.value = resource(R.string.message_unlock_sent)
                    startDesbloqueoPolling(cnofact)
                }
                .onFailure {
                    _desbloqueoSolicitado.value = false
                    _toast.value = it.message?.takeIf(String::isNotBlank)?.let(UiText::Raw)
                        ?: resource(R.string.message_unlock_failed)
                }
        }
    }

    private fun startDesbloqueoPolling(cnofact: String) {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (_desbloqueoSolicitado.value) {
                delay(8000)
                repository.verificarDesbloqueo(cnofact).onSuccess { res ->
                    if (res.success && res.autorizado) {
                        aplicarDesbloqueoAprobado(cnofact, res.autorizadoPor ?: "", res.fechaExpiracion, mostrarToast = true)
                    }
                }
            }
        }
    }

    fun cancelarDesbloqueo() {
        _desbloqueoSolicitado.value = false
        pollingJob?.cancel()
    }

    // --- Fotos Gallery ---
    fun subirFoto(base64Image: String, extension: String, cclienteid: String) {
        if (!canCreate()) {
            denyPermission(R.string.permission_add_photos)
            return
        }
        _isUploadingFoto.value = true
        viewModelScope.launch {
            repository.subirFoto(crefAgrupada, cclienteid, base64Image, extension)
                .onSuccess {
                    _isUploadingFoto.value = false
                    cargarFotos()
                    _toast.value = resource(R.string.message_photo_uploaded)
                }
                .onFailure {
                    _isUploadingFoto.value = false
                    _toast.value = resource(R.string.message_photo_upload_failed)
                }
        }
    }

    fun eliminarFoto(fotoId: Int) {
        if (!canDelete()) {
            denyPermission(R.string.permission_delete_photos)
            return
        }
        viewModelScope.launch {
            repository.eliminarFoto(fotoId)
                .onSuccess {
                    _fotos.value = _fotos.value.filter { it.idFotos != fotoId }
                    _toast.value = resource(R.string.message_photo_deleted)
                }
                .onFailure { _toast.value = resource(R.string.message_photo_delete_failed) }
        }
    }

    fun terminarRevision() {
        if (!canEdit()) {
            denyPermission(R.string.permission_close_reviews)
            return
        }
        if (pendingCaptureCount.value > 0) {
            _toast.value = resource(R.string.label_capture_finish_pending)
            return
        }
        // Sin al menos 1 foto (ya subida o todavía pendiente de subir) no se
        // deja terminar la revisión (feedback de Eduardo, 14/sept): sin
        // evidencia fotográfica no hay forma de sustentar que la revisión
        // física de verdad ocurrió. _fotos ya trae TODAS las fotos de la
        // entrada (cargarFotos junta cref + cada cnofact, ver arriba), y
        // pendingPhotoCount cubre lo que se tomó pero aún no sincroniza --
        // sin ese segundo check, tomar una foto y darle "Terminar" de
        // inmediato bloquearía en falso mientras sube.
        if (_fotos.value.isEmpty() && pendingPhotoCount.value == 0) {
            _toast.value = resource(R.string.message_finish_without_photo)
            return
        }
        viewModelScope.launch {
            // El flujo observado da respuesta inmediata en pantalla, pero la
            // consulta a Room dentro de la corrutina cierra la ventana entre
            // el último valor observado y el inicio del cierre.
            if (labelCaptureDao.pendingCount(crefAgrupada, labelCaptureOwner) > 0) {
                _toast.value = resource(R.string.label_capture_finish_pending)
                return@launch
            }
            repository.validarCierre(crefAgrupada)
                .onSuccess { resp ->
                    if (!resp.facturasSinRevisor.isNullOrEmpty()) {
                        _toast.value = resource(
                            R.string.message_no_reviewer,
                            resp.facturasSinRevisor.joinToString(", ")
                        )
                        return@launch
                    }
                    val completas = _facturas.value.count { it.StatusRev == 1 }
                    val totalFacts = _facturas.value.size
                    val es100 = totalFacts > 0 && completas == totalFacts
                    val tieneDiscrepancias = _facturas.value.any { it.StatusRev == 2 }
                    val statusFinal = if (es100 && !tieneDiscrepancias) 1 else 2

                    repository.finalizarTimer(crefAgrupada, statusFinal, true)
                        .onSuccess {
                            revisionFinalizada = true
                            timerJob?.cancel()
                            _timerActivo.value = false

                            // La revisión vuelve a quedar cerrada: cualquier desbloqueo
                            // aprobado anteriormente ya no debe seguir vigente. El
                            // backend revoca el permiso en rev_desbloqueos al recibir
                            // finalizarTimer, pero UnlockStore es un caché LOCAL que
                            // nunca se releía contra el servidor -- sin este clearAll,
                            // isBloqueada() seguía leyendo el TTL viejo y esta misma
                            // entrada aparecía editable de nuevo en este dispositivo.
                            _desbloqueoAprobado.value = false
                            authRepository.getUsername()?.let { user ->
                                unlockStore.clearAll(user, crefAgrupada)
                            }

                            repository.limpiarEditoresEntrada(crefAgrupada)
                            _celebrateOnExit.value = true
                            _navegarAtras.value = true
                        }
                        .onFailure { _toast.value = resource(R.string.message_close_failed) }
                }
                .onFailure { _toast.value = resource(R.string.message_close_failed) }
        }
    }

    fun clearToast() { _toast.value = null }
    fun consumeNavBack() { _navegarAtras.value = false }
    fun consumeCierreForzoso() { _cierreForzoso.value = null }

    fun getToken(): String = authRepository.getToken() ?: ""
    fun isHubConnected(): Boolean = hubService.isConnected()

    private fun resource(@StringRes id: Int, vararg args: Any): UiText =
        UiText.Resource(id, args.toList())

    // Sin ceros sobrantes: «210», no «210.0»; «2.1», no «2.100».
    private fun quantityText(value: Double): String = LabelCaptureCotejo.numero(value)

    /**
     * Reglas de bloqueo de campos de una factura — alineadas con la web
     * (detalle.ts:647-668):
     *   1. Desbloqueo aprobado por supervisor (SignalR o persistente) → no bloquea
     *   2. statusRev == 1 → factura ya completada → bloquea
     *   3. relacionCompleta → la relación agrupada ya se cerró → bloquea
     *   4. usuedit != usuario actual → otro usuario editando → bloquea
     *   5. entradaCerrada + no espectador + no desbloqueado → bloquea
     *
     * NOTA: la versión anterior tenía un check extra `isSpectatorMode` que
     * NO existe en la web. Ese check hacía que cualquier usuario que no fuera
     * el revisor asignado (incluso sin haber tomado el documento) viera TODO
     * bloqueado, lo cual es incorrecto.
     */
    fun isBloqueada(mfac: FacturaRevisionDto): Boolean {
        val user = authRepository.getUsername() ?: ""
        val enUsoPorOtro = !mfac.UsuEdit.isNullOrEmpty() && mfac.UsuEdit != user

        // 1. Si esta factura fue aprobada para desbloqueo (SignalR o persistente),
        //    NO bloquea. Replicamos la lógica de la web que también consulta
        //    SharedPreferences con TTL.
        if (_desbloqueoAprobado.value &&
            _facturas.value.getOrNull(_selectedFacturaIndex.value)?.Cnofact == mfac.Cnofact) {
            return false
        }
        if (user.isNotEmpty() &&
            unlockStore.isUnlocked(user, crefAgrupada, mfac.Cnofact)) {
            return false
        }

        // 2-4. Reglas duras
        if (mfac.StatusRev == 1) return true
        if (mfac.RelacionCompleta) return true
        if (enUsoPorOtro) return true

        // 5. Entrada cerrada: bloquea, pero solo si NO es espectador
        //    (un espectador puede seguir viendo aunque esté cerrada).
        if (_entradaCerrada.value && !isEspectador.value) return true

        return false
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        pollingJob?.cancel()
        presenceJob?.cancel()
        // OJO: viewModelScope ya viene cancelado para cuando onCleared() se
        // ejecuta (AndroidX cierra los Closeables -incluido el Job de
        // viewModelScope- ANTES de llamar onCleared()). Un
        // viewModelScope.launch aquí nunca llega a correr su cuerpo -- por
        // eso "salir de una entrada" no liberaba el timer ni el usuedit
        // (nunca se mandaba la llamada, no es que tardara). Se usa
        // applicationScope (vive lo que vive el proceso) para que sí corran.

        // Cerrar cualquier sesión abierta al salir, incluso si el timer aún
        // no alcanzó a iniciar. RegistrarTomaDocumento puede crear una fila
        // con solo ftomadocumento cuando el usuario entra y sale muy rápido.
        // El endpoint es idempotente y también cubre ese caso.
        if (canEdit() && !revisionFinalizada) {
            applicationScope.launch { repository.finalizarTimer(crefAgrupada, 0, false) }
        }

        if (canEdit()) {
            _facturas.value.getOrNull(_selectedFacturaIndex.value)?.let { f ->
                applicationScope.launch { repository.limpiarEditor(f.Cnofact) }
            }
            applicationScope.launch { repository.limpiarEditoresEntrada(crefAgrupada) }
        }
    }
}
