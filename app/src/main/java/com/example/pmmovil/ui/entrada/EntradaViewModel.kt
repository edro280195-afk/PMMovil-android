package com.example.pmmovil.ui.entrada

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.data.db.EntradaPhotoSyncDao
import com.example.pmmovil.data.model.CasetaPhotoInfo
import com.example.pmmovil.data.model.CasetaScanResponse
import com.example.pmmovil.data.model.EntradaCatalogs
import com.example.pmmovil.data.model.EntradaDetail
import com.example.pmmovil.data.model.EntradaDraft
import com.example.pmmovil.data.model.EntradaFormSnapshot
import com.example.pmmovil.data.model.EntradaFrequentItem
import com.example.pmmovil.data.model.EntradaLookupItem
import com.example.pmmovil.data.model.EntradaPhotoInfo
import com.example.pmmovil.data.model.EntradaPhotoKind
import com.example.pmmovil.data.model.EntradaPhotoSyncEntity
import com.example.pmmovil.data.model.EntradaReferenceResponse
import com.example.pmmovil.data.model.EntradaSaveRequest
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.model.toPrinterDto
import com.example.pmmovil.data.preferences.EntradaDraftStore
import com.example.pmmovil.data.printing.LabelZplFactory
import com.example.pmmovil.data.printing.ZebraPrinterPreferences
import com.example.pmmovil.data.printing.ZebraPrinterService
import com.example.pmmovil.data.printing.ZebraZplBuilder
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.EntradaRepository
import com.example.pmmovil.data.repository.ZebraPrinterRepository
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.example.pmmovil.data.scanner.BarcodeValueNormalizer
import com.example.pmmovil.ui.common.SavingProgressState
import com.example.pmmovil.ui.common.SavingProgressStep
import com.example.pmmovil.ui.common.SavingStepStatus
import com.example.pmmovil.data.sync.OfflineSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class EntradaLookupType { CUSTOMER, TRANSPORT, BOX }
enum class EntradaScanStatus { READY, SCANNING, SUCCESS, ERROR, DUPLICATE }

/** Camino elegido en la pantalla de arranque de Nueva Entrada (ver EntradaStartChoiceScreen). */
enum class EntradaEntryMode { CHOOSING, SCAN, MANUAL }

data class EntradaPrinterPickerState(
    val form: EntradaFormSnapshot,
    val printers: List<ZebraPrinterDto>,
    val defaultPrinterId: Int? = null,
    val messagePrefix: String? = null,
    val catalogMessage: String? = null,
    // true solo para el picker que se abre automáticamente justo después de guardar una
    // entrada nueva: al imprimir o cancelar ahí, se regresa al listado de entradas.
    // false para reimpresiones manuales posteriores (requestPrint), donde el usuario
    // sigue revisando/editando la entrada y no debe salir de la pantalla.
    val autoNavigateOnComplete: Boolean = false
)

data class EntradaUiState(
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val puedeAgregar: Boolean = false,
    val puedeModificar: Boolean = false,
    val puedeBorrar: Boolean = false,
    val catalogs: EntradaCatalogs = EntradaCatalogs(),
    val boxes: List<EntradaLookupItem> = emptyList(),
    val form: EntradaFormSnapshot = EntradaFormSnapshot(),
    val section: EntradaSection = EntradaSection.REFERENCE,
    val invalidFields: Set<String> = emptySet(),
    val drafts: List<EntradaDraft> = emptyList(),
    val showDraftRecovery: Boolean = false,
    val lookupType: EntradaLookupType? = null,
    val lookupItems: List<EntradaLookupItem> = emptyList(),
    val isLookupLoading: Boolean = false,
    val scanStatus: EntradaScanStatus = EntradaScanStatus.READY,
    val scanMessage: String = "Listo para escanear",
    val entryStartMode: EntradaEntryMode = EntradaEntryMode.CHOOSING,
    val gatePhotos: List<CasetaPhotoInfo> = emptyList(),
    val remotePhotos: List<EntradaPhotoInfo> = emptyList(),
    val queuedPhotos: List<EntradaPhotoSyncEntity> = emptyList(),
    val remoteDamagePhotos: List<EntradaPhotoInfo> = emptyList(),
    val queuedDamagePhotos: List<EntradaPhotoSyncEntity> = emptyList(),
    val frequentCustomers: List<EntradaFrequentItem> = emptyList(),
    val frequentTransports: List<EntradaFrequentItem> = emptyList(),
    val previewImagePath: String? = null,
    val isPreviewLoading: Boolean = false,
    val galleryOpenIndex: Int? = null,
    val damageGalleryOpenIndex: Int? = null,
    val printerPicker: EntradaPrinterPickerState? = null,
    val message: String? = null,
    val lastDraftSavedAt: Long? = null,
    val isEditMode: Boolean = false,
    val consecutiveGenerated: Boolean = false,
    val navigateToListEvent: Boolean = false,
    val savingProgress: SavingProgressState = SavingProgressState(),
    val isSharingLink: Boolean = false,
    /** Evento de un solo uso: al llenarse, la pantalla abre el share sheet nativo y lo consume. */
    val shareUrl: String? = null
)

@HiltViewModel
class EntradaViewModel @Inject constructor(
    private val repository: EntradaRepository,
    private val draftStore: EntradaDraftStore,
    private val authRepository: AuthRepository,
    private val entradaPhotoSyncDao: EntradaPhotoSyncDao,
    private val syncScheduler: OfflineSyncScheduler,
    private val zebraPrinterPreferences: ZebraPrinterPreferences,
    private val zebraPrinterService: ZebraPrinterService,
    private val zebraPrinterRepository: ZebraPrinterRepository,
    private val labelZplFactory: LabelZplFactory,
    private val warehouseHubService: WarehouseHubService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        EntradaUiState(
            form = emptyForm(),
            puedeAgregar = authRepository.hasPermission(153, "Agregar"),
            puedeModificar = authRepository.hasPermission(153, "Modificar"),
            puedeBorrar = authRepository.hasPermission(153, "Borrar")
        )
    )
    val uiState: StateFlow<EntradaUiState> = _uiState.asStateFlow()

    private var draftId = UUID.randomUUID().toString()
    private var draftCreatedAt = System.currentTimeMillis()
    private var draftJob: Job? = null
    private var lookupJob: Job? = null
    private var queuedPhotosJob: Job? = null
    private var lastScanCode = ""
    private var lastScanAt = 0L

    fun canCreate(): Boolean = authRepository.hasPermission(153, "Agregar")

    fun canEdit(): Boolean = authRepository.hasPermission(153, "Modificar")

    fun canDelete(): Boolean = authRepository.hasPermission(153, "Borrar")

    private fun denyEdit() {
        _uiState.value = _uiState.value.copy(
            message = "✗ No tienes permiso para modificar entradas."
        )
    }

    init {
        loadInitialData()
    }

    private fun emptyForm() = EntradaFormSnapshot(
        ref3 = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy")),
        fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")),
        recibio = authRepository.getName()
            ?.takeIf(String::isNotBlank)
            ?: authRepository.getUsername().orEmpty()
    )

    private fun loadInitialData() {
        viewModelScope.launch {
            val drafts = draftStore.loadAll()
            _uiState.value = _uiState.value.copy(
                drafts = drafts,
                showDraftRecovery = drafts.isNotEmpty()
            )

            val catalogsRequest = async { repository.getCatalogs() }
            val boxesRequest = async { repository.getAvailableBoxes() }
            val frequentCustomersRequest = async { repository.getFrequentItems("cliente") }
            val frequentTransportsRequest = async { repository.getFrequentItems("transporte") }
            val catalogs = catalogsRequest.await()
            val boxes = boxesRequest.await()
            val frequentCustomers = frequentCustomersRequest.await()
            val frequentTransports = frequentTransportsRequest.await()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                catalogs = catalogs.getOrDefault(EntradaCatalogs()),
                boxes = boxes.getOrDefault(emptyList()),
                frequentCustomers = frequentCustomers.getOrDefault(emptyList()),
                frequentTransports = frequentTransports.getOrDefault(emptyList()),
                message = catalogs.exceptionOrNull()?.message
            )
        }
    }

    fun updateForm(transform: (EntradaFormSnapshot) -> EntradaFormSnapshot) {
        val currentForm = _uiState.value.form
        val allowed = if (currentForm.isExisting || _uiState.value.isEditMode) {
            canEdit()
        } else {
            canCreate()
        }
        if (!allowed) {
            denyEdit()
            return
        }
        val updated = transform(_uiState.value.form)
        _uiState.value = _uiState.value.copy(
            form = updated,
            invalidFields = emptySet()
        )
        scheduleDraftSave()
    }

    fun setSection(section: EntradaSection) {
        _uiState.value = _uiState.value.copy(section = section)
    }

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /** Genera el link público de "Seguimiento de entrada" y lo entrega como evento de un solo uso. */
    fun compartirEntrada() {
        val cref = _uiState.value.form.reference()
        if (cref.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Guarda la entrada antes de compartirla.")
            return
        }
        _uiState.value = _uiState.value.copy(isSharingLink = true)
        viewModelScope.launch {
            repository.compartir(cref)
                .onSuccess { response ->
                    _uiState.value = _uiState.value.copy(isSharingLink = false, shareUrl = response.url)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isSharingLink = false,
                        message = error.message ?: "No se pudo generar el enlace."
                    )
                }
        }
    }

    fun consumeShareUrl() {
        _uiState.value = _uiState.value.copy(shareUrl = null)
    }

    fun startNewEntry() {
        if (!canCreate()) {
            denyEdit()
            return
        }
        draftJob?.cancel()
        queuedPhotosJob?.cancel()
        draftId = UUID.randomUUID().toString()
        draftCreatedAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            form = emptyForm(),
            section = EntradaSection.REFERENCE,
            invalidFields = emptySet(),
            showDraftRecovery = false,
            remotePhotos = emptyList(),
            queuedPhotos = emptyList(),
            remoteDamagePhotos = emptyList(),
            queuedDamagePhotos = emptyList(),
            gatePhotos = emptyList(),
            scanStatus = EntradaScanStatus.READY,
            scanMessage = "Listo para escanear",
            entryStartMode = EntradaEntryMode.CHOOSING,
            lookupType = null,
            lookupItems = emptyList(),
            printerPicker = null,
            message = null,
            isEditMode = false,
            consecutiveGenerated = false
        )
    }

    /** Camino "Capturar manual" de la pantalla de arranque: formulario limpio, sin lanzar cámara. */
    fun beginManualEntry() {
        startNewEntry()
        _uiState.value = _uiState.value.copy(entryStartMode = EntradaEntryMode.MANUAL)
    }

    fun resumeDraft(draft: EntradaDraft) {
        draftId = draft.id
        draftCreatedAt = draft.createdAt
        _uiState.value = _uiState.value.copy(
            form = draft.form,
            showDraftRecovery = false,
            section = firstIncompleteSection(draft.form),
            consecutiveGenerated = draft.form.ref4.isNotBlank() || draft.form.isExisting,
            entryStartMode = if (draft.form.casetaId != null) {
                EntradaEntryMode.SCAN
            } else {
                EntradaEntryMode.MANUAL
            },
            message = "Borrador recuperado."
        )
        if (draft.form.isExisting) {
            loadRemotePhotos()
            loadRemoteDamagePhotos()
            observeQueuedPhotos(draft.form.reference())
        }
    }

    fun deleteDraft(draft: EntradaDraft) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        draft.form.localPhotoPaths.forEach { path ->
            runCatching { File(path).delete() }
        }
        draft.form.localDamagePhotoPaths.forEach { path ->
            runCatching { File(path).delete() }
        }
        draftStore.delete(draft.id)
        val drafts = draftStore.loadAll()
        _uiState.value = _uiState.value.copy(
            drafts = drafts,
            showDraftRecovery = drafts.isNotEmpty()
        )
    }

    fun closeDraftRecovery() {
        _uiState.value = _uiState.value.copy(showDraftRecovery = false)
    }

    fun generateConsecutive() {
        if (!canCreate()) {
            denyEdit()
            return
        }
        val currentState = _uiState.value
        if (
            currentState.isBusy ||
            currentState.consecutiveGenerated ||
            currentState.form.ref4.isNotBlank() ||
            currentState.form.isExisting
        ) {
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            repository.getConsecutive()
                .onSuccess { response ->
                        updateForm {
                            it.copy(ref4 = response.numero, isExisting = false)
                        }
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        section = EntradaSection.ARRIVAL,
                        consecutiveGenerated = true,
                        message = "Entrada ${response.numero} generada."
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = error.message ?: "No se pudo generar la entrada."
                    )
                }
        }
    }

    fun searchReference() {
        val reference = _uiState.value.form.ref4.trim()
        if (reference.isBlank() || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            repository.getReference(reference)
                .onSuccess(::applyReference)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = error.message ?: "Referencia no encontrada."
                    )
                }
        }
    }

    /**
     * Carga una entrada existente (vino del listado de bodega) y la marca como modo edición.
     * Reutiliza applyReference() para mapear el header + detalles al form.
     */
    fun loadEntradaExistente(cref: String) {
        val reference = cref.trim()
        if (reference.isBlank() || _uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            repository.getReference(reference)
                .onSuccess { response ->
                    applyReference(response)
                    _uiState.value = _uiState.value.copy(
                        isEditMode = true,
                        isBusy = false
                    )
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = error.message ?: "No se pudo cargar la entrada."
                    )
                }
        }
    }

    private fun applyReference(response: EntradaReferenceResponse) {
        val header = response.encabezado ?: return
        val parts = splitReference(header.creferencia.orEmpty())
        val form = _uiState.value.form.copy(
            ref1 = parts[0],
            ref2 = parts[1],
            ref3 = parts[2],
            ref4 = parts[3],
            ref5 = parts[4],
            ref6 = parts[5],
            fecha = header.dfechaarribo?.take(16) ?: _uiState.value.form.fecha,
            bodega = header.planta ?: header.bodega ?: "1",
            codCliente = header.cclienteidtraf.orEmpty(),
            nomCliente = header.nom_cliente.orEmpty(),
            codTransporte = header.cidtransportraf.orEmpty(),
            nomTransporte = header.nom_transporte.orEmpty(),
            manejo = header.cidmanejo.orEmpty(),
            guia = header.cnoguia.orEmpty(),
            cajaArribo = header.cnocaja.orEmpty(),
            hazmat = header.lhazmat == true,
            claseHazmat = header.cmarcas2.orEmpty(),
            un = header.cmarcas1.orEmpty(),
            danada = header.danada == true,
            danadaDescripcion = header.danadaDescripcion.orEmpty(),
            inBond = header.linbond == true,
            inBondVal = header.cinbondno.orEmpty(),
            detalles = response.detalles,
            pesoLbs = header.nsubpeso ?: 0.0,
            pesoKgs = header.ntotpeso ?: 0.0,
            observaciones = header.mobserv.orEmpty(),
            recibio = header.crecibio ?: _uiState.value.form.recibio,
            maderaSello = header.maderaSello == true,
            maderaSelloCant = header.maderaSelloCant ?: 0,
            maderaSinSello = header.maderaSinSello == true,
            maderaSinSelloCant = header.maderaSinSelloCant ?: 0,
            aserrin = header.aserrin == true,
            aserrinCant = header.aserrinCant ?: 0,
            noSello = header.noSello == true,
            noSelloCant = header.noSelloCant ?: 0,
            localPhotoPaths = emptyList(),
            localDamagePhotoPaths = emptyList(),
            isExisting = true
        )
        draftId = UUID.randomUUID().toString()
        draftCreatedAt = System.currentTimeMillis()
        _uiState.value = _uiState.value.copy(
            isBusy = false,
            form = form,
            isEditMode = true,
            section = EntradaSection.ARRIVAL,
            entryStartMode = EntradaEntryMode.MANUAL,
            message = "Entrada cargada correctamente."
        )
        scheduleDraftSave()
        loadRemotePhotos()
        loadRemoteDamagePhotos()
        observeQueuedPhotos(form.reference())
    }

    fun processGateScan(rawCode: String) {
        val code = BarcodeValueNormalizer.normalize(rawCode)
        val currentState = _uiState.value
        if (code.isBlank() || currentState.isBusy) return
        if (
            currentState.consecutiveGenerated ||
            currentState.form.ref4.isNotBlank() ||
            currentState.form.isExisting
        ) {
            _uiState.value = currentState.copy(
                message = "Ya hay una entrada cargada. Pulsa Nueva entrada para escanear otra caseta."
            )
            return
        }
        val now = System.currentTimeMillis()
        if (code == lastScanCode && now - lastScanAt < 1_200) return
        lastScanCode = code
        lastScanAt = now

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                scanStatus = EntradaScanStatus.SCANNING,
                scanMessage = "Buscando en caseta..."
            )
            repository.scanGate(code)
                .onSuccess(::applyGateData)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        scanStatus = EntradaScanStatus.ERROR,
                        scanMessage = error.message ?: "No se encontró el registro en caseta.",
                        message = error.message
                    )
                    resetScanStatusLater()
                }
        }
    }

    private fun applyGateData(response: CasetaScanResponse) {
        if (response.entradaExistente) {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                scanStatus = EntradaScanStatus.DUPLICATE,
                scanMessage = "Ya tiene entrada: ${response.referenciaExistente.orEmpty()}",
                message = "Esta caja ya tiene entrada registrada: ${response.referenciaExistente.orEmpty()}."
            )
            resetScanStatusLater(3_500)
            return
        }

        updateForm { form ->
            val plateNote = response.placas
                ?.takeIf(String::isNotBlank)
                ?.let { "Placas: $it" }
            form.copy(
                cajaArribo = response.numCaja ?: form.cajaArribo,
                codCliente = response.clienteId ?: form.codCliente,
                nomCliente = response.nombreCliente ?: form.nomCliente,
                codTransporte = response.transporteId ?: form.codTransporte,
                nomTransporte = response.nombreTransporte ?: form.nomTransporte,
                bodega = mapGatePlantToWarehouse(response.planta) ?: form.bodega,
                casetaId = response.casetaId ?: form.casetaId,
                observaciones = listOf(form.observaciones, plateNote.orEmpty())
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString(" | ")
            )
        }
        _uiState.value = _uiState.value.copy(
            isBusy = !response.entradaExistente,
            section = EntradaSection.ARRIVAL,
            gatePhotos = response.fotos,
            scanStatus = EntradaScanStatus.SUCCESS,
            scanMessage = "Caja ${response.numCaja ?: lastScanCode} aplicada",
            entryStartMode = EntradaEntryMode.SCAN,
            message = "Datos de caseta aplicados. Generando consecutivo..."
        )
        resetScanStatusLater(2_500)
        viewModelScope.launch { generateConsecutiveAfterGate() }
    }

    private suspend fun generateConsecutiveAfterGate() {
        repository.getConsecutive()
            .onSuccess { response ->
                updateForm { it.copy(ref4 = response.numero, isExisting = false) }
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    section = EntradaSection.ARRIVAL,
                    consecutiveGenerated = true,
                    message = "Entrada ${response.numero} lista con datos de caseta."
                )
            }
            .onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = error.message
                        ?: "Datos de caseta aplicados, pero no se pudo generar el consecutivo."
                )
            }
    }

    private fun mapGatePlantToWarehouse(planta: String?): String? {
        return when (planta?.trim()?.uppercase(Locale.ROOT)) {
            "MAQUILA" -> "1"
            "ATLANTA" -> "2"
            "BODEGA 3" -> "3"
            "TRADE CTR" -> "4"
            else -> null
        }
    }

    private fun resetScanStatusLater(delayMillis: Long = 3_000) {
        viewModelScope.launch {
            delay(delayMillis)
            _uiState.value = _uiState.value.copy(
                scanStatus = EntradaScanStatus.READY,
                scanMessage = "Listo para escanear"
            )
        }
    }

    fun openLookup(type: EntradaLookupType) {
        _uiState.value = _uiState.value.copy(
            lookupType = type,
            lookupItems = if (type == EntradaLookupType.BOX) _uiState.value.boxes else emptyList()
        )
    }

    fun closeLookup() {
        lookupJob?.cancel()
        _uiState.value = _uiState.value.copy(lookupType = null, lookupItems = emptyList())
    }

    fun searchLookup(type: EntradaLookupType, filter: String) {
        _uiState.value = _uiState.value.copy(lookupType = type)
        if (type == EntradaLookupType.BOX) {
            val clean = filter.trim()
            _uiState.value = _uiState.value.copy(
                lookupItems = _uiState.value.boxes.filter {
                    it.id.contains(clean, true) ||
                        it.nombre.contains(clean, true) ||
                        it.extra.contains(clean, true)
                }
            )
            return
        }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            delay(250)
            _uiState.value = _uiState.value.copy(isLookupLoading = true)
            val result = when (type) {
                EntradaLookupType.CUSTOMER -> repository.searchCustomers(filter.ifBlank { null })
                EntradaLookupType.TRANSPORT -> repository.searchTransports(filter.ifBlank { null })
                EntradaLookupType.BOX -> Result.success(emptyList())
            }
            _uiState.value = _uiState.value.copy(
                isLookupLoading = false,
                lookupItems = result.getOrDefault(emptyList()),
                message = result.exceptionOrNull()?.message
            )
        }
    }

    fun clearLookupSelection(type: EntradaLookupType) {
        when (type) {
            EntradaLookupType.CUSTOMER -> updateForm {
                it.copy(codCliente = "", nomCliente = "")
            }
            EntradaLookupType.TRANSPORT -> updateForm {
                it.copy(codTransporte = "", nomTransporte = "")
            }
            EntradaLookupType.BOX -> updateForm { it.copy(cajaArribo = "") }
        }
        closeLookup()
    }

    fun selectLookup(item: EntradaLookupItem) {
        when (_uiState.value.lookupType) {
            EntradaLookupType.CUSTOMER -> updateForm {
                it.copy(codCliente = item.id, nomCliente = item.nombre)
            }
            EntradaLookupType.TRANSPORT -> updateForm {
                it.copy(codTransporte = item.id, nomTransporte = item.nombre)
            }
            EntradaLookupType.BOX -> updateForm { it.copy(cajaArribo = item.id) }
            null -> Unit
        }
        closeLookup()
    }

    fun addDetail(detail: EntradaDetail, editIndex: Int? = null): Boolean {
        if (!(if (editIndex == null) canCreate() else canEdit())) {
            denyEdit()
            return false
        }
        val error = EntradaRules.validateDetail(detail)
        if (error != null) {
            _uiState.value = _uiState.value.copy(message = error)
            return false
        }
        updateForm { form ->
            val details = if (editIndex != null && editIndex in form.detalles.indices) {
                form.detalles.mapIndexed { index, current ->
                    if (index == editIndex) detail else current
                }
            } else {
                form.detalles + detail
            }
            form.copy(detalles = details)
        }
        return true
    }

    fun deleteDetail(index: Int) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        updateForm { form ->
            form.copy(detalles = form.detalles.filterIndexed { current, _ -> current != index })
        }
    }

    fun updatePounds(value: Double) {
        updateForm {
            it.copy(
                pesoLbs = value,
                pesoKgs = roundWeight(EntradaRules.kilosFromPounds(value))
            )
        }
    }

    fun updateKilos(value: Double) {
        updateForm {
            it.copy(
                pesoLbs = roundWeight(EntradaRules.poundsFromKilos(value)),
                pesoKgs = value
            )
        }
    }

    fun addLocalPhoto(path: String) {
        if (!canCreate()) {
            runCatching { File(path).delete() }
            denyEdit()
            return
        }
        val form = _uiState.value.form
        _uiState.value = _uiState.value.copy(
            form = if (path in form.localPhotoPaths) form
            else form.copy(localPhotoPaths = form.localPhotoPaths + path),
            invalidFields = emptySet()
        )
        scheduleDraftSave()
        _uiState.value = _uiState.value.copy(message = "Foto agregada.")
    }

    fun deleteLocalPhoto(path: String) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        runCatching { File(path).delete() }
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(
                localPhotoPaths = _uiState.value.form.localPhotoPaths - path
            ),
            invalidFields = emptySet()
        )
        scheduleDraftSave()
    }

    fun addLocalDamagePhoto(path: String) {
        if (!canCreate()) {
            runCatching { File(path).delete() }
            denyEdit()
            return
        }
        val form = _uiState.value.form
        _uiState.value = _uiState.value.copy(
            form = if (path in form.localDamagePhotoPaths) form
            else form.copy(localDamagePhotoPaths = form.localDamagePhotoPaths + path),
            invalidFields = emptySet()
        )
        scheduleDraftSave()
        _uiState.value = _uiState.value.copy(message = "Foto de daño agregada.")
    }

    fun deleteLocalDamagePhoto(path: String) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        runCatching { File(path).delete() }
        _uiState.value = _uiState.value.copy(
            form = _uiState.value.form.copy(
                localDamagePhotoPaths = _uiState.value.form.localDamagePhotoPaths - path
            ),
            invalidFields = emptySet()
        )
        scheduleDraftSave()
    }

    fun executePrimaryAction() {
        val form = _uiState.value.form
        when {
            form.ref4.isBlank() && _uiState.value.consecutiveGenerated -> {
                _uiState.value = _uiState.value.copy(
                    message = "Ya se generó un consecutivo. Pulsa Nueva entrada para generar otro."
                )
            }
            form.ref4.isBlank() -> generateConsecutive()
            _uiState.value.isEditMode -> if (canEdit()) update() else denyEdit()
            form.isExisting && (
                form.localPhotoPaths.isNotEmpty() ||
                    form.localDamagePhotoPaths.isNotEmpty()
                ) -> if (canCreate()) uploadPhotos() else denyEdit()
            form.isExisting -> if (canEdit()) requestPrint() else denyEdit()
            else -> if (canCreate()) save() else denyEdit()
        }
    }

    fun closeSavingOverlay() {
        _uiState.value = _uiState.value.copy(savingProgress = SavingProgressState(isVisible = false))
    }

    private fun save() {
        if (!canCreate()) {
            denyEdit()
            return
        }
        val form = _uiState.value.form
        val validations = EntradaRules.validate(form)
        if (validations.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                invalidFields = validations.map { it.field }.toSet(),
                section = validations.first().section,
                message = validations.first().message
            )
            return
        }

        viewModelScope.launch {
            val initialSteps = listOf(
                SavingProgressStep("val", "Validando recepción", "Verificando peso y proveedor", SavingStepStatus.SUCCESS),
                SavingProgressStep("sig", "Notificación SignalR", "Reportando actividad a bodega", SavingStepStatus.ACTIVE),
                SavingProgressStep("photos", "Evidencia fotográfica", "Codificando imágenes", SavingStepStatus.PENDING),
                SavingProgressStep("save", "Guardando recepción", "Generando folios", SavingStepStatus.PENDING),
                SavingProgressStep("finish", "Finalizando", "Impresión y confirmación", SavingStepStatus.PENDING)
            )

            _uiState.value = _uiState.value.copy(
                isBusy = true,
                message = null,
                savingProgress = SavingProgressState(
                    isVisible = true,
                    percent = 20,
                    currentTitle = "Notificando vía SignalR...",
                    steps = initialSteps
                )
            )

            try {
                warehouseHubService.startConnection()
                val refBox = form.cajaArribo.ifBlank { form.ref4.ifBlank { "ENTRADA" } }
                val user = authRepository.getUsername().orEmpty().ifBlank { "Recepcion" }
                warehouseHubService.registrarActividad(refBox, user, form.codCliente)
            } catch (ex: Exception) {
                android.util.Log.w("EntradaViewModel", "SignalR broadcast warning: ${ex.message}")
            }

            _uiState.value = _uiState.value.copy(
                savingProgress = _uiState.value.savingProgress.copy(
                    percent = 45,
                    currentTitle = "Procesando fotografías...",
                    steps = _uiState.value.savingProgress.steps.map { s ->
                        when (s.id) {
                            "sig" -> s.copy(status = SavingStepStatus.SUCCESS)
                            "photos" -> s.copy(status = SavingStepStatus.ACTIVE)
                            else -> s
                        }
                    }
                )
            )

            _uiState.value = _uiState.value.copy(
                savingProgress = _uiState.value.savingProgress.copy(
                    percent = 70,
                    currentTitle = "Guardando recepción...",
                    steps = _uiState.value.savingProgress.steps.map { s ->
                        when (s.id) {
                            "photos" -> s.copy(
                                status = SavingStepStatus.SUCCESS,
                                detail = "Se enviarán después en lotes pequeños"
                            )
                            "save" -> s.copy(status = SavingStepStatus.ACTIVE)
                            else -> s
                        }
                    }
                )
            )

            // Las fotos no forman parte del request principal. El registro de
            // entrada debe quedar liviano y la evidencia se sube por la cola
            // local, en lotes pequeños, después de obtener la referencia.
            val request = form.toRequest(emptyList(), emptyList())
            val response = repository.save(request).getOrElse { error ->
                val errMsg = error.message ?: "No se pudo guardar la recepcion."
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    message = errMsg,
                    savingProgress = _uiState.value.savingProgress.copy(
                        hasError = true,
                        errorMessage = errMsg,
                        currentTitle = "Error al guardar",
                        steps = _uiState.value.savingProgress.steps.map { s ->
                            if (s.id == "save") s.copy(status = SavingStepStatus.ERROR) else s
                        }
                    )
                )
                return@launch
            }

            val queuedEvidence = enqueueEntradaPhotoFiles(
                reference = form.reference(),
                paths = form.localPhotoPaths,
                kind = EntradaPhotoKind.EVIDENCE
            )
            val queuedDamage = enqueueEntradaPhotoFiles(
                reference = form.reference(),
                paths = form.localDamagePhotoPaths,
                kind = EntradaPhotoKind.DAMAGE
            )
            if (queuedEvidence > 0 || queuedDamage > 0) {
                syncScheduler.scheduleEntradaPhotos()
            }

            draftStore.delete(draftId)
            val savedForm = form.copy(
                localPhotoPaths = emptyList(),
                localDamagePhotoPaths = emptyList(),
                isExisting = true
            )
            val photoMessage = if (queuedEvidence + queuedDamage > 0) {
                " Las $queuedEvidence fotos de entrada y $queuedDamage de daño quedaron en cola para sincronizar."
            } else {
                ""
            }
            val saveMessage = response.message.ifBlank { "Recepción guardada correctamente." } + photoMessage
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                form = savedForm,
                drafts = draftStore.loadAll(),
                message = saveMessage,
                savingProgress = _uiState.value.savingProgress.copy(
                    percent = 100,
                    isFinished = true,
                    currentTitle = "¡Recepción guardada correctamente!",
                    steps = _uiState.value.savingProgress.steps.map { s ->
                        s.copy(status = SavingStepStatus.SUCCESS)
                    }
                )
            )
            loadRemotePhotos()
            loadRemoteDamagePhotos()
            observeQueuedPhotos(savedForm.reference())
            delay(1400)
            _uiState.value = _uiState.value.copy(savingProgress = SavingProgressState(isVisible = false))
            openPrinterPicker(savedForm, saveMessage, autoNavigateOnComplete = true)
        }
    }

    /**
     * Actualiza los datos de una entrada existente (modo edición).
     * Las fotos NO se envían en el body — se manejan por separado
     * con uploadPhotos() (nuevas) y deleteRemotePhoto() (borradas).
     */
    private fun update() {
        if (!canEdit()) {
            denyEdit()
            return
        }
        val currentState = _uiState.value
        val form = currentState.form
        val damagePhotoCount = form.localDamagePhotoPaths.size +
            currentState.remoteDamagePhotos.size +
            currentState.queuedDamagePhotos.size
        val validations = EntradaRules.validate(form, damagePhotoCount)
        if (validations.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                invalidFields = validations.map { it.field }.toSet(),
                section = validations.first().section,
                message = validations.first().message
            )
            return
        }

        val cref = form.reference()
        if (cref.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "Referencia inválida.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            // No enviamos fotos en el body del PUT — se manejan aparte
            val request = form.toRequest(emptyList())
            repository.update(cref, request)
                .onSuccess { response ->
                    val updatedForm = form.copy(isExisting = true)
                    val queuedEvidence = enqueueEntradaPhotoFiles(
                        reference = cref,
                        paths = form.localPhotoPaths,
                        kind = EntradaPhotoKind.EVIDENCE
                    )
                    val queuedDamage = enqueueEntradaPhotoFiles(
                        reference = cref,
                        paths = form.localDamagePhotoPaths,
                        kind = EntradaPhotoKind.DAMAGE
                    )
                    if (queuedEvidence + queuedDamage > 0) {
                        syncScheduler.scheduleEntradaPhotos()
                    }
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        form = updatedForm.copy(
                            localPhotoPaths = emptyList(),
                            localDamagePhotoPaths = emptyList()
                        ),
                        message = response.message.ifBlank { "Entrada actualizada correctamente." } +
                            if (queuedEvidence + queuedDamage > 0) {
                                " Las fotos quedaron en cola para sincronizar."
                            } else {
                                ""
                            }
                    )
                    observeQueuedPhotos(cref)
                    loadRemotePhotos()
                    loadRemoteDamagePhotos()
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = error.message ?: "No se pudo actualizar la entrada."
                    )
                }
        }
    }

    fun requestPrint() {
        val form = _uiState.value.form
        if (form.ref4.isBlank() || _uiState.value.isBusy) return
        if (!canEdit()) {
            denyEdit()
            return
        }
        viewModelScope.launch {
            openPrinterPicker(form, null)
        }
    }

    fun dismissPrinterPicker() {
        val picker = _uiState.value.printerPicker
        _uiState.value = _uiState.value.copy(
            printerPicker = null,
            navigateToListEvent = picker?.autoNavigateOnComplete == true
        )
    }

    fun printToSelectedPrinter(printer: ZebraPrinterDto) {
        val picker = _uiState.value.printerPicker ?: return
        if (_uiState.value.isBusy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isBusy = true,
                printerPicker = null,
                message = null
            )
            val printMessage = sendZebraLabels(picker.form, printer)
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = listOfNotNull(
                    picker.messagePrefix?.takeIf(String::isNotBlank),
                    printMessage.takeIf(String::isNotBlank)
                ).joinToString("\n"),
                navigateToListEvent = picker.autoNavigateOnComplete
            )
        }
    }

    /** El composable consume el evento (navega) y lo apaga aquí para que no se dispare de nuevo. */
    fun consumeNavigateToListEvent() {
        _uiState.value = _uiState.value.copy(navigateToListEvent = false)
    }

    private suspend fun openPrinterPicker(
        form: EntradaFormSnapshot,
        messagePrefix: String?,
        autoNavigateOnComplete: Boolean = false
    ) {
        _uiState.value = _uiState.value.copy(
            isBusy = true,
            message = null,
            printerPicker = null
        )

        val localConfig = zebraPrinterPreferences.getConfig(
            com.example.pmmovil.data.printing.ZebraLabelType.ENTRADA_BODEGA
        )
        val localFallback = localConfig.takeIf { it.isHostConfigured }?.toPrinterDto()
        // El catálogo es una ayuda para elegir impresora, no una dependencia
        // del guardado. Si el servidor no responde, la configuración local
        // debe aparecer en segundos y permitir continuar.
        val catalogResult = withTimeoutOrNull(PRINTER_CATALOG_TIMEOUT_MILLIS) {
            zebraPrinterRepository.getPrinters()
        } ?: Result.failure(Exception("El catálogo de impresoras tardó demasiado."))
        val catalogPrinters = catalogResult.getOrElse { emptyList() }
        val localDefault = catalogPrinters.firstOrNull { printer ->
            (localConfig.printerId != null && printer.id == localConfig.printerId) ||
                (printer.host.equals(localConfig.host, ignoreCase = true) && printer.port == localConfig.port)
        }
        val printers = if (localDefault != null) {
            listOf(localDefault) + catalogPrinters.filterNot { it.id == localDefault.id }
        } else {
            catalogPrinters.ifEmpty { localFallback?.let(::listOf).orEmpty() }
        }
        val catalogMessage = catalogResult.exceptionOrNull()?.message
            ?.takeIf(String::isNotBlank)
            ?.let { "$it Se usara la configuracion local si esta disponible." }
            ?: if (catalogResult.isSuccess && printers.isEmpty()) {
                "No hay impresoras Zebra activas configuradas."
            } else {
                null
            }

        if (printers.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = listOfNotNull(
                    messagePrefix?.takeIf(String::isNotBlank),
                    catalogMessage ?: "Configura al menos una impresora Zebra."
                ).joinToString("\n"),
                navigateToListEvent = autoNavigateOnComplete
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            isBusy = false,
            printerPicker = EntradaPrinterPickerState(
                form = form,
                printers = printers,
                defaultPrinterId = localDefault?.id ?: localConfig.printerId,
                messagePrefix = messagePrefix,
                catalogMessage = catalogMessage,
                autoNavigateOnComplete = autoNavigateOnComplete
            )
        )
    }

    private companion object {
        const val PRINTER_CATALOG_TIMEOUT_MILLIS = 2_500L
    }

    private suspend fun sendZebraLabels(
        form: EntradaFormSnapshot,
        printer: ZebraPrinterDto
    ): String {
        val config = printer.toConfig()
        val labels = ZebraZplBuilder.fromEntradaForm(form)
        val result = if (BuildConfig.USE_IMAGE_LABELS) {
            // Etiqueta renderizada como imagen (^GF): mismo socket, mismo modo que el ZPL de texto.
            val zpl = labelZplFactory.buildLabels(labels)
            val labelCount = labels.sumOf { it.copies.coerceAtLeast(1) }
            zebraPrinterService.printZpl(config, zpl, labelCount)
        } else {
            zebraPrinterService.printLabels(config, labels)
        }
        return result.userMessage()
    }

    private fun uploadPhotos() {
        if (!canCreate()) {
            denyEdit()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, message = null)
            val form = _uiState.value.form
            val reference = form.reference()
            val evidenceCount = enqueueEntradaPhotoFiles(
                reference = reference,
                paths = form.localPhotoPaths,
                kind = EntradaPhotoKind.EVIDENCE
            )
            val damageCount = enqueueEntradaPhotoFiles(
                reference = reference,
                paths = form.localDamagePhotoPaths,
                kind = EntradaPhotoKind.DAMAGE
            )
            if (evidenceCount + damageCount == 0) {
                _uiState.value = _uiState.value.copy(isBusy = false)
                return@launch
            }
            syncScheduler.scheduleEntradaPhotos()
            _uiState.value = _uiState.value.copy(
                form = _uiState.value.form.copy(
                    localPhotoPaths = emptyList(),
                    localDamagePhotoPaths = emptyList()
                ),
                invalidFields = emptySet()
            )
            scheduleDraftSave()
            observeQueuedPhotos(reference)
            _uiState.value = _uiState.value.copy(
                isBusy = false,
                message = "${evidenceCount + damageCount} fotos quedaron en cola para sincronizar."
            )
        }
    }

    private suspend fun enqueueEntradaPhotoFiles(
        reference: String,
        paths: List<String>,
        kind: String
    ): Int {
        if (reference.isBlank() || paths.isEmpty()) return 0
        paths.distinct().forEach { path ->
            entradaPhotoSyncDao.insert(
                EntradaPhotoSyncEntity(
                    reference = reference,
                    localFilePath = path,
                    kind = kind,
                    synced = false
                )
            )
        }
        return paths.distinct().size
    }

    private fun loadRemotePhotos() {
        val reference = _uiState.value.form.reference()
        if (reference.isBlank()) return
        viewModelScope.launch {
            repository.getPhotos(reference).onSuccess { photos ->
                _uiState.value = _uiState.value.copy(remotePhotos = photos)
            }
        }
    }

    private fun loadRemoteDamagePhotos() {
        val reference = _uiState.value.form.reference()
        if (reference.isBlank()) return
        viewModelScope.launch {
            repository.getDamagePhotos(reference).onSuccess { photos ->
                _uiState.value = _uiState.value.copy(remoteDamagePhotos = photos)
            }
        }
    }

    private fun observeQueuedPhotos(reference: String) {
        if (reference.isBlank()) return
        queuedPhotosJob?.cancel()
        queuedPhotosJob = viewModelScope.launch {
            var previousEvidenceIds = emptySet<Int>()
            launch {
                entradaPhotoSyncDao.observeByReference(reference, EntradaPhotoKind.EVIDENCE).collect { photos ->
                    val currentIds = photos.mapTo(mutableSetOf()) { it.id }
                    if (previousEvidenceIds.isNotEmpty() && previousEvidenceIds != currentIds) {
                        loadRemotePhotos()
                    }
                    previousEvidenceIds = currentIds
                    _uiState.value = _uiState.value.copy(queuedPhotos = photos)
                }
            }
            var previousDamageIds = emptySet<Int>()
            launch {
                entradaPhotoSyncDao.observeByReference(reference, EntradaPhotoKind.DAMAGE).collect { photos ->
                    val currentIds = photos.mapTo(mutableSetOf()) { it.id }
                    if (previousDamageIds.isNotEmpty() && previousDamageIds != currentIds) {
                        loadRemoteDamagePhotos()
                    }
                    previousDamageIds = currentIds
                    _uiState.value = _uiState.value.copy(queuedDamagePhotos = photos)
                }
            }
        }
    }

    fun deleteQueuedPhoto(photo: EntradaPhotoSyncEntity) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        viewModelScope.launch {
            entradaPhotoSyncDao.delete(photo.id)
            runCatching { File(photo.localFilePath).delete() }
            _uiState.value = _uiState.value.copy(message = "Foto en cola eliminada.")
        }
    }

    fun deleteRemotePhoto(photo: EntradaPhotoInfo) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            repository.deletePhoto(photo.idFotos)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        remotePhotos = _uiState.value.remotePhotos.filterNot {
                            it.idFotos == photo.idFotos
                        },
                        message = "Foto eliminada."
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = it.message ?: "No se pudo eliminar la foto."
                    )
                }
        }
    }

    fun deleteRemoteDamagePhoto(photo: EntradaPhotoInfo) {
        if (!canDelete()) {
            denyEdit()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true)
            repository.deletePhoto(photo.idFotos)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        remoteDamagePhotos = _uiState.value.remoteDamagePhotos.filterNot {
                            it.idFotos == photo.idFotos
                        },
                        message = "Foto de daño eliminada."
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        message = it.message ?: "No se pudo eliminar la foto de daño."
                    )
                }
        }
    }

    fun openDamageGallery(index: Int) {
        _uiState.value = _uiState.value.copy(damageGalleryOpenIndex = index)
    }

    fun closeDamageGallery() {
        _uiState.value = _uiState.value.copy(damageGalleryOpenIndex = null)
    }

    fun previewLocalPhoto(path: String) {
        _uiState.value = _uiState.value.copy(previewImagePath = path)
    }

    fun previewRemotePhoto(photo: EntradaPhotoInfo) {
        val reference = _uiState.value.form.reference()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreviewLoading = true)
            repository.downloadPhoto(reference, photo.nLineNo)
                .onSuccess { bytes -> showDownloadedPhoto(bytes, "entrada_${photo.idFotos}.jpg") }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        message = error.message
                    )
                }
        }
    }

    fun previewGatePhoto(photo: CasetaPhotoInfo) {
        val casetaId = _uiState.value.form.casetaId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isPreviewLoading = true)
            repository.downloadGatePhoto(casetaId, photo.indice)
                .onSuccess { bytes -> showDownloadedPhoto(bytes, "caseta_${photo.indice}.jpg") }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isPreviewLoading = false,
                        message = error.message
                    )
                }
        }
    }

    private suspend fun showDownloadedPhoto(bytes: ByteArray, name: String) {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "entrada_preview").apply { mkdirs() }
                .resolve(name)
                .apply { writeBytes(bytes) }
        }
        _uiState.value = _uiState.value.copy(
            isPreviewLoading = false,
            previewImagePath = file.absolutePath
        )
    }

    fun closePhotoPreview() {
        _uiState.value = _uiState.value.copy(previewImagePath = null)
    }

    fun openGallery(index: Int) {
        _uiState.value = _uiState.value.copy(galleryOpenIndex = index)
    }

    fun closeGallery() {
        _uiState.value = _uiState.value.copy(galleryOpenIndex = null)
    }

    private fun scheduleDraftSave() {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            delay(1_500)
            persistDraft()
        }
    }

    private fun persistDraft() {
        val form = _uiState.value.form
        if (!hasMeaningfulData(form)) return
        val now = System.currentTimeMillis()
        draftStore.save(
            EntradaDraft(
                id = draftId,
                createdAt = draftCreatedAt,
                lastEditAt = now,
                form = form
            )
        )
        _uiState.value = _uiState.value.copy(
            drafts = draftStore.loadAll(),
            lastDraftSavedAt = now
        )
    }

    private fun hasMeaningfulData(form: EntradaFormSnapshot): Boolean =
        if (form.isExisting) {
            form.localPhotoPaths.isNotEmpty() || form.localDamagePhotoPaths.isNotEmpty()
        } else {
            form.ref4.isNotBlank() ||
                form.codCliente.isNotBlank() ||
                form.cajaArribo.isNotBlank() ||
                form.guia.isNotBlank() ||
                form.detalles.isNotEmpty() ||
                form.localPhotoPaths.isNotEmpty() ||
                    form.localDamagePhotoPaths.isNotEmpty()
        }

    private fun firstIncompleteSection(form: EntradaFormSnapshot): EntradaSection =
        EntradaRules.validate(form).firstOrNull()?.section ?: EntradaSection.CLOSE

    private fun splitReference(reference: String): List<String> {
        val withSeparators = reference.split("-")
        if (withSeparators.size >= 6) return withSeparators.take(6)
        val clean = reference.trim()
        if (clean.length >= 11) {
            return listOf(
                clean.take(2),
                clean.substring(2, 3),
                clean.substring(3, 5),
                clean.substring(5, clean.length - 4),
                clean.substring(clean.length - 4, clean.length - 2),
                clean.takeLast(2)
            )
        }
        return listOf("01", "C", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy")), clean, "00", "00")
    }

    private fun roundWeight(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0

    override fun onCleared() {
        draftJob?.cancel()
        persistDraft()
        super.onCleared()
    }
}

fun EntradaFormSnapshot.reference(): String =
    "$ref1$ref2$ref3$ref4$ref5$ref6"

fun EntradaFormSnapshot.referenceDisplay(): String =
    if (ref4.isBlank()) "Sin entrada" else "$ref1-$ref2-$ref3-$ref4-$ref5-$ref6"

fun EntradaFormSnapshot.totalReceived(): Double = detalles.sumOf { it.cantidad }

private fun EntradaFormSnapshot.toRequest(photos: List<String>, photosDanio: List<String> = emptyList()) = EntradaSaveRequest(
    ref1 = ref1,
    ref2 = ref2,
    ref3 = ref3,
    ref4 = ref4,
    ref5 = ref5,
    ref6 = ref6,
    fecha = fecha,
    bodega = bodega,
    codCliente = codCliente,
    nomCliente = nomCliente,
    hazmat = hazmat,
    clase = claseHazmat,
    un = un,
    danada = danada,
    danadaDescripcion = danadaDescripcion,
    codTransporte = codTransporte,
    nomTransporte = nomTransporte,
    manejo = manejo,
    guia = guia,
    cajaArribo = cajaArribo,
    casetaId = casetaId,
    inBond = inBond,
    inBondVal = inBondVal,
    totalRecibido = totalReceived(),
    pesoLbs = pesoLbs,
    pesoKgs = pesoKgs,
    observaciones = observaciones,
    recibio = recibio,
    maderaSello = maderaSello,
    maderaSelloCant = maderaSelloCant,
    maderaSinSello = maderaSinSello,
    maderaSinSelloCant = maderaSinSelloCant,
    aserrin = aserrin,
    aserrinCant = aserrinCant,
    noSello = noSello,
    noSelloCant = noSelloCant,
    detalles = detalles,
    fotos = photos,
    fotosDanio = photosDanio
)
