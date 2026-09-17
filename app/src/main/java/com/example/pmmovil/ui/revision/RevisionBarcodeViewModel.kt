package com.example.pmmovil.ui.revision

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.db.FotoDao
import com.example.pmmovil.data.barcode.BarcodeTrace
import com.example.pmmovil.data.model.BarcodeConfigResponse
import com.example.pmmovil.data.model.CorreoInfo
import com.example.pmmovil.data.model.FinalizarBarcodeResponse
import com.example.pmmovil.data.model.LecturaResponse
import com.example.pmmovil.data.model.PendingBarcodeScan
import com.example.pmmovil.data.model.ProductoResumen
import com.example.pmmovil.data.model.ResumenBarcodeResponse
import com.example.pmmovil.data.preferences.BarcodeSessionStore
import com.example.pmmovil.data.repository.BarcodeApiException
import com.example.pmmovil.data.repository.BarcodeRepository
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.RevisionRepository
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.data.scanner.DataWedgeProfileStatus
import com.example.pmmovil.data.scanner.HardwareScanEvent
import com.example.pmmovil.data.scanner.HardwareScanSource
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.google.gson.JsonElement
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class BarcodeCaptureMode { PHYSICAL, CAMERA, MANUAL }

enum class BarcodeProductFilter { ALL, PENDING, RECOGNIZED, UNKNOWN }

enum class BarcodeInputSource { PHYSICAL, DATAWEDGE, CAMERA, MANUAL }

enum class BarcodeDataWedgeState { AVAILABLE, READY, ERROR }

data class BarcodeScanDiagnostic(
    val source: BarcodeInputSource? = null,
    val sourceLabel: String = "Sin lecturas todavía",
    val rawCode: String? = null,
    val normalizedCode: String? = null,
    val rawLength: Int = 0,
    val normalizedLength: Int = 0,
    val expectedLength: Int = 0,
    val labelType: String? = null,
    val accepted: Boolean = false,
    val reason: String? = null,
    val atMillis: Long = 0L
)

data class BarcodeDataWedgeDiagnostic(
    val state: BarcodeDataWedgeState = BarcodeDataWedgeState.AVAILABLE,
    val message: String = "Perfil automático disponible en equipos Zebra",
    val details: String? = null
)

data class BarcodeRemovalRequest(
    val product: ProductoResumen,
    val allowRemoveAll: Boolean
)

data class BarcodeUiState(
    val isLoading: Boolean = true,
    val isProcessingQueue: Boolean = false,
    val puedeModificar: Boolean = false,
    val puedeBorrar: Boolean = false,
    val isOffline: Boolean = false,
    val isFinalizing: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val sessionId: Int? = null,
    val config: BarcodeConfigResponse? = null,
    val products: List<ProductoResumen> = emptyList(),
    val pendingCount: Int = 0,
    val lastReading: LecturaResponse? = null,
    val lastReadingSeq: Long = 0L,
    val lastCode: String? = null,
    val captureMode: BarcodeCaptureMode = BarcodeCaptureMode.PHYSICAL,
    val filter: BarcodeProductFilter = BarcodeProductFilter.ALL,
    val scanDiagnostic: BarcodeScanDiagnostic = BarcodeScanDiagnostic(),
    val dataWedgeDiagnostic: BarcodeDataWedgeDiagnostic = BarcodeDataWedgeDiagnostic(),
    val elapsedSeconds: Long = 0,
    val remoteEvidenceCount: Int = 0,
    val finalizePreview: ResumenBarcodeResponse? = null,
    val finalizeResult: FinalizarBarcodeResponse? = null,
    val removalRequest: BarcodeRemovalRequest? = null,
    val cierreForzoso: com.example.pmmovil.data.signalr.CierreForzosoMessage? = null,
    val isSharingLink: Boolean = false,
    /** Evento de un solo uso: al llenarse, la pantalla abre el share sheet nativo y lo consume. */
    val shareUrl: String? = null
)

@HiltViewModel
class RevisionBarcodeViewModel @Inject constructor(
    private val repository: BarcodeRepository,
    private val revisionRepository: RevisionRepository,
    private val entradaRepository: com.example.pmmovil.data.repository.EntradaRepository,
    private val fotoDao: FotoDao,
    private val sessionStore: BarcodeSessionStore,
    private val hubService: WarehouseHubService,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val crefAgrupada: String = checkNotNull(savedStateHandle["crefAgrupada"])

    private val _uiState = MutableStateFlow(
        BarcodeUiState(
            puedeModificar = authRepository.hasPermission(159, "Modificar"),
            puedeBorrar = authRepository.hasPermission(159, "Borrar")
        )
    )
    val uiState: StateFlow<BarcodeUiState> = _uiState.asStateFlow()

    val localEvidenceCount: StateFlow<Int> = fotoDao
        .getFotosLocales(crefAgrupada, "")
        .map { photos -> photos.count { !it.synced || it.remotePhotoId == null } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    private var confirmedProducts: List<ProductoResumen> = emptyList()
    private var pendingScans: List<PendingBarcodeScan> =
        sessionStore.loadPending(crefAgrupada)
    private var queueJob: Job? = null
    private var summaryJob: Job? = null
    private var timerJob: Job? = null
    private var emailPollingJob: Job? = null
    private val syncMutex = Mutex()
    private val recentScans = mutableMapOf<String, Long>()
    private var lastAcceptedAt = 0L
    private var sessionStartedAt = System.currentTimeMillis()

    init {
        BarcodeTrace.d(
            "VM init cref=$crefAgrupada pending=${pendingScans.size}"
        )
        loadSession()
        refreshEvidenceCount()
        observeForcedClose()
    }

    private fun observeForcedClose() {
        viewModelScope.launch {
            hubService.cierreForzosoFlow.collect { message ->
                val isSameReference = message.creferencia.isNullOrBlank() ||
                    message.creferencia.equals(crefAgrupada, ignoreCase = true)
                if (!isSameReference || _uiState.value.cierreForzoso != null) return@collect

                timerJob?.cancel()
                queueJob?.cancel()
                summaryJob?.cancel()
                emailPollingJob?.cancel()
                _uiState.value = _uiState.value.copy(
                    isProcessingQueue = false,
                    cierreForzoso = message
                )
            }
        }
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val config = repository.getConfig(crefAgrupada).getOrElse { error ->
                BarcodeTrace.w("VM config failure cref=$crefAgrupada mensaje=${error.message}", error)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = if (error is IOException) {
                        "Servidor fuera de línea. Revisa tu conexión e inténtalo de nuevo."
                    } else {
                        error.message ?: "No se pudo cargar la configuración de barcode."
                    }
                )
                return@launch
            }
            if (!config.configurado || config.barcodeLong <= 0) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    config = config,
                    error = "Este cliente/proveedor no tiene configurada la longitud de código " +
                        "(barcode_long en cteprov). Pide al administrador que configure el valor."
                )
                return@launch
            }

            val estado = repository.getEstado(crefAgrupada).getOrNull()
            BarcodeTrace.d(
                "VM estado cref=$crefAgrupada activa=${estado?.activa} " +
                    "sesion=${estado?.idSesion} total=${estado?.lecturas.orEmpty().sumOf { it.cantidad }} " +
                    "pendingBeforeReconcile=${pendingScans.size} " +
                    "productos=${traceProducts(estado?.lecturas.orEmpty())}"
            )
            if (estado?.activa == true && estado.idSesion != null) {
                confirmedProducts = estado.lecturas.orEmpty()
                sessionStartedAt = parseDateMillis(estado.fechaInicio)
                    ?: System.currentTimeMillis()
                preparePendingForSession(estado.idSesion)
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    sessionId = estado.idSesion,
                    config = config.copy(
                        cidcte = estado.cidcte ?: config.cidcte,
                        cidprov = estado.cidprov ?: config.cidprov,
                        barcodeLong = estado.barcodeLong.takeIf { it > 0 }
                            ?: config.barcodeLong,
                        nombreCliente = estado.nombreCliente
                            ?.takeIf(String::isNotBlank)
                            ?: config.nombreCliente,
                        nombreProveedor = estado.nombreProveedor
                            ?.takeIf(String::isNotBlank)
                            ?: config.nombreProveedor
                    )
                )
                // Una sesión activa puede ser consultada por usuarios con
                // Lectura, pero reconciliar pendientes implica registrar
                // lecturas (INSERT/UPDATE). Solo Modificar puede ejecutar
                // esa sincronización y mantener el cronómetro de trabajo.
                val puedeModificar = authRepository.hasPermission(159, "Modificar")
                if (puedeModificar) {
                    reconcilePendingOnLoad(estado.idSesion)
                }
                BarcodeTrace.d(
                    "VM estado reconciliado sesion=${estado.idSesion} " +
                        "confirmed=${confirmedProducts.sumOf { it.cantidad }} pending=${pendingScans.size}"
                )
                publishProducts()
                if (puedeModificar) {
                    startTimer()
                }
                _uiState.value = _uiState.value.copy(isLoading = false)
                if (puedeModificar) {
                    processQueue()
                }
                return@launch
            }

            if (!authRepository.hasPermission(159, "Modificar")) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Tienes acceso de consulta, pero no permiso para iniciar o editar una revisión."
                )
                return@launch
            }

            repository.iniciarSesion(crefAgrupada)
                .onSuccess { session ->
                    BarcodeTrace.d(
                        "VM sesion iniciada cref=$crefAgrupada id=${session.idSesion}"
                    )
                    sessionStartedAt = System.currentTimeMillis()
                    confirmedProducts = emptyList()
                    preparePendingForSession(session.idSesion)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        sessionId = session.idSesion,
                        config = config.copy(
                            cidcte = session.cidcte ?: config.cidcte,
                            cidprov = session.cidprov ?: config.cidprov,
                            barcodeLong = session.barcodeLong.takeIf { it > 0 }
                                ?: config.barcodeLong,
                            nombreCliente = session.nombreCliente
                                ?.takeIf(String::isNotBlank)
                                ?: config.nombreCliente,
                            nombreProveedor = session.nombreProveedor
                                ?.takeIf(String::isNotBlank)
                                ?: config.nombreProveedor
                        )
                    )
                    publishProducts()
                    startTimer()
                    processQueue()
                    refreshSummary()
                }
                .onFailure { error ->
                    BarcodeTrace.w("VM iniciar sesión failure cref=$crefAgrupada mensaje=${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = if (error is IOException) {
                            "Servidor fuera de línea. Revisa tu conexión e inténtalo de nuevo."
                        } else {
                            error.message ?: "No se pudo iniciar la sesión de barcode."
                        }
                    )
                }
        }
    }

    fun retryLoad() {
        loadSession()
        refreshEvidenceCount()
    }

    fun registerReading(rawCode: String) {
        registerReadingFromSource(
            rawCode = rawCode,
            source = BarcodeInputSource.PHYSICAL,
            sourceLabel = "Lector físico"
        )
    }

    fun registerHardwareScan(event: HardwareScanEvent) {
        val source = when (event.source) {
            HardwareScanSource.DATAWEDGE -> BarcodeInputSource.DATAWEDGE
            HardwareScanSource.HONEYWELL,
            HardwareScanSource.HID_BRIDGE,
            HardwareScanSource.GENERIC_INTENT -> BarcodeInputSource.PHYSICAL
        }
        registerReadingFromSource(
            rawCode = event.value,
            source = source,
            sourceLabel = event.sourceLabel,
            labelType = event.labelType
        )
    }

    fun registerReadingFromSource(
        rawCode: String,
        source: BarcodeInputSource,
        sourceLabel: String,
        labelType: String? = null
    ) {
        if (!authRepository.hasPermission(159, "Modificar")) {
            showMessage("No tienes permiso para registrar lecturas de revisión.")
            return
        }
        BarcodeTrace.d(
            "VM lectura input source=$source label=$sourceLabel raw=${rawCode.take(80)} " +
                "rawLen=${rawCode.length} session=${_uiState.value.sessionId}"
        )
        val code = BarcodeValueNormalizer.normalize(rawCode)
        if (code.isEmpty()) {
            BarcodeTrace.d("VM lectura rejected reason=empty")
            publishScanDiagnostic(
                rawCode = rawCode,
                source = source,
                sourceLabel = sourceLabel,
                labelType = labelType,
                accepted = false,
                reason = "Lectura vacía"
            )
            return
        }
        if (_uiState.value.sessionId == null) {
            BarcodeTrace.d("VM lectura rejected reason=session_not_ready code=${code.take(48)}")
            publishScanDiagnostic(
                rawCode = code,
                source = source,
                sourceLabel = sourceLabel,
                labelType = labelType,
                accepted = false,
                reason = "Sesión no lista"
            )
            return
        }

        val normalized = normalizeCode(code)
        val now = System.currentTimeMillis()
        val duplicateTooSoon = isBarcodeDuplicateTooSoon(
            normalizedCode = normalized,
            nowMillis = now,
            previousSameCodeAtMillis = recentScans[normalized],
            lastAcceptedAtMillis = lastAcceptedAt
        )
        if (duplicateTooSoon) {
            BarcodeTrace.d(
                "VM lectura rejected reason=duplicate_window code=$normalized " +
                    "duplicateTooSoon=$duplicateTooSoon " +
                    "age=${now - (recentScans[normalized] ?: 0L)} lastAcceptedAge=${now - lastAcceptedAt}"
            )
            publishScanDiagnostic(
                rawCode = code,
                source = source,
                sourceLabel = sourceLabel,
                labelType = labelType,
                accepted = false,
                reason = "Duplicado ignorado"
            )
            return
        }

        recentScans[normalized] = now
        lastAcceptedAt = now
        recentScans.entries.removeAll { now - it.value > 5_000L }
        pendingScans = pendingScans + PendingBarcodeScan(
            id = UUID.randomUUID().toString(),
            code = code,
            createdAt = now,
            sessionId = _uiState.value.sessionId ?: 0
        )
        BarcodeTrace.d(
            "VM lectura accepted raw=${code.take(80)} normalized=$normalized " +
                "scanId=${pendingScans.last().id} session=${pendingScans.last().sessionId} " +
                "pending=${pendingScans.size} pendingIds=${tracePending(pendingScans)}"
        )
        persistPending()
        publishScanDiagnostic(
            rawCode = code,
            source = source,
            sourceLabel = sourceLabel,
            labelType = labelType,
            accepted = true,
            reason = null
        )
        _uiState.value = _uiState.value.copy(lastCode = normalized, message = null)
        publishProducts()
        processQueue()
    }

    private fun processQueue() {
        if (!authRepository.hasPermission(159, "Modificar")) return
        if (queueJob?.isActive == true || pendingScans.isEmpty()) return
        val sessionId = _uiState.value.sessionId ?: return

        queueJob = viewModelScope.launch {
            syncMutex.withLock {
                _uiState.value = _uiState.value.copy(isProcessingQueue = true)
                while (isActive && pendingScans.isNotEmpty()) {
                    val scan = pendingScans.first()
                    val pendingBefore = pendingScans.size
                    val confirmedBefore = confirmedProducts.sumOf { it.cantidad }
                    BarcodeTrace.d(
                        "VM queue send sesion=$sessionId scanSession=${scan.sessionId} " +
                            "scanId=${scan.id} code=${scan.code.take(80)} rawLen=${scan.code.length} " +
                            "pendingBefore=$pendingBefore confirmedBefore=$confirmedBefore " +
                            "pendingIds=${tracePending(pendingScans)}"
                    )
                    val result = repository.registrarLectura(
                        idSesion = sessionId,
                        codigoRaw = scan.code,
                        clienteScanId = scan.id
                    )

                    val response = result.getOrNull()
                    if (response != null) {
                        val normalized = normalizeCode(scan.code)
                        val productId = response.cidproduct
                            ?.takeIf(String::isNotBlank)
                            ?: normalized

                        pendingScans = pendingScans.drop(1)
                        persistPending()

                        if (response.duplicada) {
                            // La fila ya existía en el servidor. La consulta se
                            // espera bajo el mismo mutex para que no pueda
                            // sobrescribir una actualización local en curso.
                            refreshSummaryFromServer(sessionId)
                        } else {
                            val index = confirmedProducts.indexOfFirst {
                                it.cidproduct.equals(productId, ignoreCase = true)
                            }
                            confirmedProducts = if (index >= 0) {
                                confirmedProducts.mapIndexed { productIndex, product ->
                                    if (productIndex == index) {
                                        product.copy(
                                            cantidad = product.cantidad + 1,
                                            descripcion = response.descripcion
                                                ?.takeIf(String::isNotBlank)
                                                ?: product.descripcion,
                                            existe = response.existe,
                                            codigoRaw = scan.code,
                                            ultimaLectura = System.currentTimeMillis()
                                        )
                                    } else {
                                        product
                                    }
                                }
                            } else {
                                listOf(
                                    ProductoResumen(
                                        cidproduct = productId,
                                        descripcion = response.descripcion,
                                        cantidad = 1,
                                        existe = response.existe,
                                        codigoRaw = scan.code,
                                        ultimaLectura = System.currentTimeMillis()
                                    )
                                ) + confirmedProducts
                            }
                        }

                        _uiState.value = _uiState.value.copy(
                            isOffline = false,
                            lastReading = response.copy(cidproduct = productId),
                            lastReadingSeq = _uiState.value.lastReadingSeq + 1,
                            lastCode = productId
                        )
                        publishProducts()
                        BarcodeTrace.d(
                            "VM queue result scanId=${scan.id} duplicada=${response.duplicada} " +
                                "pendingAfter=${pendingScans.size} " +
                                "confirmedAfter=${confirmedProducts.sumOf { it.cantidad }} " +
                                "uiTotal=${_uiState.value.products.sumOf { it.cantidad }} " +
                                "productos=${traceProducts(confirmedProducts)} " +
                                "pendingIds=${tracePending(pendingScans)}"
                        )
                    } else {
                        val error = result.exceptionOrNull()
                        BarcodeTrace.w(
                            "VM queue failure sesion=$sessionId scanId=${scan.id} " +
                                "pending=$pendingBefore mensaje=${error?.message}",
                            error
                        )
                        if (
                            error is BarcodeApiException &&
                            error.statusCode in setOf(400, 404, 409)
                        ) {
                            // Estos errores no se corrigen reintentando la misma
                            // lectura. En especial, 409 significa que la sesión
                            // se cerró mientras la cola estaba sincronizando; si
                            // conservamos el pendiente, una nueva sesión podría
                            // arrastrarlo desde SharedPreferences.
                            pendingScans = pendingScans.drop(1)
                            persistPending()
                            _uiState.value = _uiState.value.copy(
                                isProcessingQueue = false,
                                isOffline = false,
                                error = error.message ?: "Sesión inválida o expirada."
                            )
                            break
                        }
                        val isNetworkError = error is IOException
                        _uiState.value = _uiState.value.copy(
                            isOffline = isNetworkError,
                            message = if (isNetworkError) {
                                "Sin conexión. Las lecturas quedan guardadas y se reintentarán."
                            } else {
                                error?.message
                                    ?: "El servidor no pudo registrar la lectura. Se reintentará."
                            }
                        )
                        delay(10_000)
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isProcessingQueue = false,
                    isOffline = false
                )
                BarcodeTrace.d(
                    "VM queue end sesion=$sessionId pending=${pendingScans.size} " +
                        "confirmed=${confirmedProducts.sumOf { it.cantidad }}"
                )
            }
        }
    }

    private suspend fun reconcilePendingOnLoad(sessionId: Int) {
        if (!authRepository.hasPermission(159, "Modificar")) return
        syncMutex.withLock {
            // Al regresar a la pantalla, una petición anterior pudo haber
            // llegado al servidor justo cuando la Activity se destruía.
            // Reenvía el mismo identificador antes de pintar el contador.
            BarcodeTrace.d(
                "VM reconcile start sesion=$sessionId pending=${pendingScans.size} " +
                    "pendingIds=${tracePending(pendingScans)}"
            )
            while (currentCoroutineContext().isActive && pendingScans.isNotEmpty()) {
                val scan = pendingScans.first()
                BarcodeTrace.d(
                    "VM reconcile send sesion=$sessionId scanId=${scan.id} " +
                        "code=${scan.code.take(48)}"
                )
                val result = repository.registrarLectura(
                    idSesion = sessionId,
                    codigoRaw = scan.code,
                    clienteScanId = scan.id
                )
                val response = result.getOrNull()
                if (response == null) {
                    BarcodeTrace.w(
                        "VM reconcile failure scanId=${scan.id} " +
                            "mensaje=${result.exceptionOrNull()?.message}",
                        result.exceptionOrNull()
                    )
                    break
                }
                BarcodeTrace.d(
                    "VM reconcile response scanId=${scan.id} scanSession=${scan.sessionId} " +
                        "duplicada=${response.duplicada} cid=${response.cidproduct}"
                )
                pendingScans = pendingScans.drop(1)
                persistPending()
            }

            refreshSummaryFromServer(sessionId)
            BarcodeTrace.d(
                "VM reconcile end sesion=$sessionId pending=${pendingScans.size} " +
                    "confirmed=${confirmedProducts.sumOf { it.cantidad }} " +
                    "productos=${traceProducts(confirmedProducts)}"
            )
        }
    }

    fun requestRemove(product: ProductoResumen) {
        if (!authRepository.hasPermission(159, "Borrar")) {
            showMessage("No tienes permiso para borrar lecturas de revisión.")
            return
        }
        if (product.pendientes > 0) {
            removePending(product.cidproduct)
            return
        }
        when {
            product.cantidad <= 1 -> {
                _uiState.value = _uiState.value.copy(
                    removalRequest = BarcodeRemovalRequest(product, false)
                )
            }
            product.cantidad > 5 -> {
                _uiState.value = _uiState.value.copy(
                    removalRequest = BarcodeRemovalRequest(product, true)
                )
            }
            else -> removeOne(product)
        }
    }

    fun removeOne(product: ProductoResumen) {
        if (!authRepository.hasPermission(159, "Borrar")) {
            showMessage("No tienes permiso para borrar lecturas de revisión.")
            return
        }
        dismissRemoval()
        val sessionId = _uiState.value.sessionId ?: return
        val before = confirmedProducts
        confirmedProducts = confirmedProducts.mapNotNull { item ->
            if (!item.cidproduct.equals(product.cidproduct, ignoreCase = true)) {
                item
            } else if (item.cantidad <= 1) {
                null
            } else {
                item.copy(cantidad = item.cantidad - 1)
            }
        }
        publishProducts()

        viewModelScope.launch {
            repository.restarLectura(
                idSesion = sessionId,
                cidproduct = if (product.existe) product.cidproduct else "",
                codigoRaw = if (product.existe) "" else product.codigoRaw
                    ?.takeIf(String::isNotBlank)
                    ?: product.cidproduct
            ).onFailure {
                confirmedProducts = before
                publishProducts()
                showMessage("No se pudo corregir la lectura.")
            }
        }
    }

    fun removeAll(product: ProductoResumen) {
        if (!authRepository.hasPermission(159, "Borrar")) {
            showMessage("No tienes permiso para borrar lecturas de revisión.")
            return
        }
        dismissRemoval()
        val sessionId = _uiState.value.sessionId ?: return
        val before = confirmedProducts
        pendingScans = pendingScans.filterNot {
            normalizeCode(it.code).equals(product.cidproduct, ignoreCase = true)
        }
        persistPending()
        confirmedProducts = confirmedProducts.filterNot {
            it.cidproduct.equals(product.cidproduct, ignoreCase = true)
        }
        publishProducts()

        viewModelScope.launch {
            repository.eliminarProducto(
                idSesion = sessionId,
                cidproduct = if (product.existe) product.cidproduct else "",
                codigoRaw = if (product.existe) "" else product.codigoRaw
                    ?.takeIf(String::isNotBlank)
                    ?: product.cidproduct
            ).onFailure {
                confirmedProducts = before
                refreshSummary()
                showMessage("No se pudo eliminar el producto.")
            }
        }
    }

    private fun removePending(productId: String) {
        val index = pendingScans.indexOfLast {
            normalizeCode(it.code).equals(productId, ignoreCase = true)
        }
        if (index < 0) return
        pendingScans = pendingScans.filterIndexed { current, _ -> current != index }
        persistPending()
        publishProducts()
    }

    fun dismissRemoval() {
        _uiState.value = _uiState.value.copy(removalRequest = null)
    }

    fun setCaptureMode(mode: BarcodeCaptureMode) {
        _uiState.value = _uiState.value.copy(captureMode = mode)
    }

    fun setFilter(filter: BarcodeProductFilter) {
        _uiState.value = _uiState.value.copy(filter = filter)
    }

    fun undoLastReading() {
        val lastProduct = _uiState.value.products.firstOrNull() ?: return
        if (lastProduct.pendientes > 0) {
            removePending(lastProduct.cidproduct)
        } else {
            removeOne(lastProduct)
        }
    }

    fun onDataWedgeStatus(status: DataWedgeProfileStatus) {
        _uiState.value = _uiState.value.copy(
            dataWedgeDiagnostic = BarcodeDataWedgeDiagnostic(
                state = if (status.configured) {
                    BarcodeDataWedgeState.READY
                } else {
                    BarcodeDataWedgeState.ERROR
                },
                message = status.message,
                details = listOfNotNull(status.result, status.details)
                    .joinToString(" · ")
                    .takeIf(String::isNotBlank)
            )
        )
    }

    fun prepareFinalize() {
        if (!authRepository.hasPermission(159, "Modificar")) {
            showMessage("No tienes permiso para finalizar revisiones.")
            return
        }
        if (pendingScans.isNotEmpty()) {
            showMessage("Espera a que se sincronicen las lecturas pendientes.")
            processQueue()
            return
        }
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getResumen(sessionId)
                .onSuccess { preview ->
                    confirmedProducts = preview.productos.orEmpty()
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        finalizePreview = preview
                    )
                    publishProducts()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = if (error is IOException) {
                            "Error de red al obtener el resumen."
                        } else {
                            error.message ?: "No se pudo obtener el resumen."
                        }
                    )
                }
        }
    }

    fun closeFinalizePreview() {
        _uiState.value = _uiState.value.copy(finalizePreview = null)
    }

    fun finalizeSession(sendEmail: Boolean, observations: String) {
        if (!authRepository.hasPermission(159, "Modificar")) {
            showMessage("No tienes permiso para finalizar revisiones.")
            return
        }
        val sessionId = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isFinalizing = true,
                finalizePreview = null
            )
            repository.finalizar(sessionId, sendEmail, observations)
                .onSuccess { result ->
                    sessionStore.clear(crefAgrupada)
                    pendingScans = emptyList()
                    _uiState.value = _uiState.value.copy(
                        isFinalizing = false,
                        finalizeResult = result
                    )
                    if (result.correo?.enProceso == true) {
                        pollEmailStatus(sessionId)
                    }
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isFinalizing = false,
                        message = if (error is IOException) {
                            "Sin conexión al servidor."
                        } else {
                            error.message ?: "Error al finalizar la sesión."
                        }
                    )
                }
        }
    }

    private fun pollEmailStatus(sessionId: Int) {
        emailPollingJob?.cancel()
        emailPollingJob = viewModelScope.launch {
            repeat(20) {
                delay(3_000)
                val status = repository.getEstadoCorreo(sessionId).getOrNull()
                    ?: return@repeat
                if (status.emailEnviado) {
                    val current = _uiState.value.finalizeResult ?: return@launch
                    _uiState.value = _uiState.value.copy(
                        finalizeResult = current.copy(
                            correo = CorreoInfo(
                                intentado = true,
                                enviado = true,
                                enProceso = false,
                                destinatarios = status.destinatarios
                            )
                        )
                    )
                    return@launch
                }
            }
        }
    }

    fun refreshEvidenceCount() {
        viewModelScope.launch {
            revisionRepository.getFotos(crefAgrupada)
                .onSuccess { photos ->
                    _uiState.value = _uiState.value.copy(
                        remoteEvidenceCount = photos.distinctBy { it.idFotos }.size
                    )
                }
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun consumeCierreForzoso() {
        _uiState.value = _uiState.value.copy(cierreForzoso = null)
    }

    private fun refreshSummary() {
        val sessionId = _uiState.value.sessionId ?: return
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch {
            syncMutex.withLock {
                refreshSummaryFromServer(sessionId)
            }
        }
    }

    private suspend fun refreshSummaryFromServer(sessionId: Int) {
        repository.getResumen(sessionId)
            .onSuccess { summary ->
                val before = confirmedProducts.sumOf { it.cantidad }
                confirmedProducts = summary.productos.orEmpty()
                summary.sesion?.fechaInicio?.let { date ->
                    parseDateMillis(date)?.let { sessionStartedAt = it }
                }
                publishProducts()
                BarcodeTrace.d(
                    "VM summary applied sesion=$sessionId before=$before " +
                        "after=${confirmedProducts.sumOf { it.cantidad }} " +
                        "pending=${pendingScans.size} uiTotal=${_uiState.value.products.sumOf { it.cantidad }}"
                )
            }
            .onFailure { error ->
                BarcodeTrace.w(
                    "VM summary failure sesion=$sessionId mensaje=${error.message}",
                    error
                )
            }
    }

    private fun publishProducts() {
        _uiState.value = _uiState.value.copy(
            products = confirmedProducts,
            pendingCount = pendingScans.size
        )
        BarcodeTrace.d(
            "VM publish confirmed=${confirmedProducts.sumOf { it.cantidad }} " +
                "pending=${pendingScans.size} uiTotal=${confirmedProducts.sumOf { it.cantidad }} " +
                "productos=${traceProducts(confirmedProducts)}"
        )
    }

    private fun persistPending() {
        sessionStore.savePending(crefAgrupada, pendingScans)
    }

    private fun preparePendingForSession(sessionId: Int) {
        val prepared = pendingScansForSession(
            pendingScans = pendingScans,
            sessionId = sessionId
        )
        if (prepared != pendingScans) {
            pendingScans = prepared
            persistPending()
        }
    }

    private fun normalizeCode(rawCode: String): String {
        val length = _uiState.value.config?.barcodeLong ?: 0
        val clean = BarcodeValueNormalizer.normalize(rawCode)
        return if (length > 0 && clean.length >= length) {
            clean.take(length)
        } else {
            clean
        }
    }

    private fun publishScanDiagnostic(
        rawCode: String,
        source: BarcodeInputSource,
        sourceLabel: String,
        labelType: String?,
        accepted: Boolean,
        reason: String?
    ) {
        val normalized = normalizeCode(rawCode)
        val expectedLength = _uiState.value.config?.barcodeLong ?: 0
        _uiState.value = _uiState.value.copy(
            scanDiagnostic = BarcodeScanDiagnostic(
                source = source,
                sourceLabel = sourceLabel,
                rawCode = rawCode,
                normalizedCode = normalized,
                rawLength = rawCode.length,
                normalizedLength = normalized.length,
                expectedLength = expectedLength,
                labelType = labelType,
                accepted = accepted,
                reason = reason,
                atMillis = System.currentTimeMillis()
            )
        )
    }

    private fun startTimer() {
        if (!authRepository.hasPermission(159, "Modificar")) return
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                _uiState.value = _uiState.value.copy(
                    elapsedSeconds = (
                        (System.currentTimeMillis() - sessionStartedAt) / 1_000L
                        ).coerceAtLeast(0)
                )
                delay(1_000)
            }
        }
    }

    private fun parseDateMillis(value: JsonElement?): Long? {
        if (value == null || value.isJsonNull) return null
        if (value.isJsonObject) {
            val date = value.asJsonObject
            val year = date.get("year")?.asInt ?: return null
            val month = date.get("month")?.asInt ?: return null
            val day = date.get("day")?.asInt ?: return null
            val hour = date.get("hour")?.asInt ?: 0
            val minute = date.get("minute")?.asInt ?: 0
            val second = date.get("second")?.asInt ?: 0
            return LocalDateTime.of(
                year,
                month,
                day,
                hour,
                minute,
                second
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        val text = value.asString.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            OffsetDateTime.parse(text).toInstant().toEpochMilli()
        }.recoverCatching {
            LocalDateTime.parse(
                text.substringBefore("."),
                DateTimeFormatter.ISO_LOCAL_DATE_TIME
            ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }

    private fun traceProducts(products: List<ProductoResumen>): String =
        products.joinToString(separator = " | ", limit = 50, truncated = " | ...") {
            "${it.cidproduct}=${it.cantidad},ex=${it.existe},raw=${it.codigoRaw?.take(48)}"
        }.ifBlank { "<sin-productos>" }

    private fun tracePending(scans: List<PendingBarcodeScan>): String =
        scans.joinToString(separator = ",", limit = 50, truncated = ",...") {
            "${it.id.take(8)}@s${it.sessionId}"
        }.ifBlank { "<vacia>" }

    private fun showMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    /** Mismo "Seguimiento de entrada" que en la revisión normal -- comparten cref_agrupada. */
    fun compartirEntrada() {
        _uiState.value = _uiState.value.copy(isSharingLink = true)
        viewModelScope.launch {
            entradaRepository.compartir(crefAgrupada)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isSharingLink = false, shareUrl = response.url)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(isSharingLink = false)
                    showMessage(error.message ?: "No se pudo generar el enlace.")
                }
        }
    }

    fun consumeShareUrl() {
        _uiState.value = _uiState.value.copy(shareUrl = null)
    }

    override fun onCleared() {
        BarcodeTrace.d(
            "VM cleared cref=$crefAgrupada session=${_uiState.value.sessionId} " +
                "pending=${pendingScans.size} pendingIds=${tracePending(pendingScans)} " +
                "confirmed=${confirmedProducts.sumOf { it.cantidad }}"
        )
        queueJob?.cancel()
        summaryJob?.cancel()
        timerJob?.cancel()
        emailPollingJob?.cancel()
        super.onCleared()
    }
}
