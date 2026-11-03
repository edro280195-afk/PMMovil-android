package com.example.pmmovil.ui.casetaentrada

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.pmmovil.data.camera.PhotoPipeline
import com.example.pmmovil.data.ocr.applyExifOrientation
import com.example.pmmovil.data.ocr.LocalTextOcr
import com.example.pmmovil.data.ocr.LocalTextOcrResult
import com.example.pmmovil.data.ocr.mergeLocalResults
import com.example.pmmovil.data.ocr.PaddleOcrEngine
import com.example.pmmovil.data.db.CasetaEntradaDraftDao
import com.example.pmmovil.data.db.CasetaEntradaPhotoSyncDao
import com.example.pmmovil.data.model.CasetaEntradaBusquedaItem
import com.example.pmmovil.data.model.CasetaEntradaCatalogItem
import com.example.pmmovil.data.model.CasetaEntradaDetalle
import com.example.pmmovil.data.model.CasetaEntradaDraftEntity
import com.example.pmmovil.data.model.CasetaEntradaFotoInfo
import com.example.pmmovil.data.model.CasetaEntradaFrequentItem
import com.example.pmmovil.data.model.CasetaEntradaGuardarRequest
import com.example.pmmovil.data.model.CasetaEntradaOcrCandidate
import com.example.pmmovil.data.model.CasetaEntradaPhotoSyncEntity
import com.example.pmmovil.data.model.CasetaPreregistroValidarResponse
import com.example.pmmovil.data.model.TipoRegistro
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.di.ApplicationScope
import com.example.pmmovil.data.catalog.CatalogIndex
import com.example.pmmovil.data.catalog.CatalogoItem
import com.example.pmmovil.data.catalog.CatalogoRepository
import com.example.pmmovil.data.catalog.ResultadoSync
import com.example.pmmovil.data.repository.CasetaEntradaRepository
import com.example.pmmovil.data.signalr.WarehouseHubService
import com.example.pmmovil.data.sync.OfflineSyncScheduler
import com.example.pmmovil.ui.common.SavingProgressState
import com.example.pmmovil.ui.common.SavingProgressStep
import com.example.pmmovil.ui.common.SavingStepStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class CasetaEntradaLookupType {
    Customer,
    TruckLine,
    BoxLine
}

enum class CasetaEntradaHistorySearchType(val apiValue: String) {
    General("general"),
    Box("box"),
    Plate("plate")
}

enum class CasetaEntradaCaptureMode {
    Plate,
    Box
}

enum class CasetaEntradaOcrPhase(val label: String, val description: String) {
    Idle("En espera", "Toma una foto o escoge una existente"),
    Captured("Foto recibida", "Recibimos la imagen"),
    PreparingImage("Mejorando imagen", "Ajustando contraste, rotacion y recortes"),
    ReadingLocal("Leyendo texto visible", "OCR local con ML Kit"),
    ValidatingCandidates("Validando candidatos", "Filtrando marcas, modelos y ruido"),
    ValidatingServer("Reforzando con servidor", "Gemini devuelve candidatos"),
    SearchingHistory("Buscando historico", "Coincidencias en registros previos"),
    Ready("Listo", "Caja o placa identificada"),
    Failed("Sin coincidencias", "Pide captura adicional o captura manual")
}

enum class CasetaEntradaOcrOutcome {
    Pending,
    AutoApplied,
    NeedsChoice,
    ManualRequired,
    Error
}

data class CasetaEntradaOcrState(
    val isLoading: Boolean = false,
    val sourceId: String = "",
    val mode: CasetaEntradaCaptureMode? = null,
    val phase: CasetaEntradaOcrPhase = CasetaEntradaOcrPhase.Idle,
    val primaryValue: String = "",
    val confidence: Double? = null,
    val message: String? = null,
    val source: String = "",
    val candidates: List<CasetaEntradaOcrCandidate> = emptyList(),
    val outcome: CasetaEntradaOcrOutcome = CasetaEntradaOcrOutcome.Pending
) {
    companion object {
        val Idle = CasetaEntradaOcrState()
    }
}

data class CasetaEntradaForm(
    val casetaId: String = "",
    val idCaseta: String = "",
    val idBodega: String = "01",
    val bodega: String = "Laredo, TX",
    val operation: String = "IMPORTACION",
    val movement: String = "",
    val idBoxType: String = "",
    val boxType: String = "",
    val boxTypeDescription: String = "",
    val truckNumber: String = "",
    val idTruckLine: String = "",
    val truckLineName: String = "",
    val boxNumber: String = "",
    val idBoxLine: String = "",
    val boxLineName: String = "",
    val idCustomer: String = "",
    val customerName: String = "",
    val operatorName: String = "",
    val plates: String = "",
    val seals: String = "",
    val idCountry: String = "",
    val countryName: String = "",
    val observations: String = "",
    val temperature: String = "",
    val fuel: String = "",
    val pedimento: String = "",
    val guideCount: String = "",
    val requiresImport: Boolean = false,
    val updated: Boolean = false,
    val localPhotoPaths: List<String> = emptyList(),
    val remotePhotos: List<CasetaEntradaRemotePhoto> = emptyList(),
    // Dato de procedencia (vino de un QR de pre-registro escaneado en garita).
    // Ya NO decide que campos son obligatorios -- eso lo hace tipoRegistro.
    val origenPreRegistro: Boolean = false,
    // TipoRegistro.CAJA (default, flujo actual) o TipoRegistro.TRACTOCAMION
    // (solo tractocamion, sin caja). Lo define la pantalla de eleccion al
    // iniciar la captura, o se hereda automaticamente al promover un
    // pre-registro QR. Exime caja/cliente de la validacion, tanto en cliente
    // como en el backend.
    val tipoRegistro: String = TipoRegistro.CAJA
) {
    val esTractocamion: Boolean
        get() = tipoRegistro == TipoRegistro.TRACTOCAMION

    val customerLabel: String
        get() = buildCatalogLabel(idCustomer, customerName)

    val truckLineLabel: String
        get() = buildCatalogLabel(idTruckLine, truckLineName)

    val boxLineLabel: String
        get() = buildCatalogLabel(idBoxLine, boxLineName)

    val countryLabel: String
        get() = buildCatalogLabel(idCountry, countryName)

    val photoCount: Int
        get() = localPhotoPaths.size + remotePhotos.size
}

data class CasetaEntradaRemotePhoto(
    val key: String,
    val label: String,
    val model: Any,
    val base64: String = "",
    val sourceIndex: Int? = null
)

// Conversion hacia/desde la fila de Room que respalda la captura en curso.
// remotePhotos se descarta a proposito (ver CasetaEntradaDraftEntity).
private fun CasetaEntradaForm.toDraftEntity(
    id: String,
    createdAt: Long,
    lastEditAt: Long
): CasetaEntradaDraftEntity = CasetaEntradaDraftEntity(
    id = id,
    createdAt = createdAt,
    lastEditAt = lastEditAt,
    casetaId = casetaId,
    idCaseta = idCaseta,
    idBodega = idBodega,
    bodega = bodega,
    operation = operation,
    movement = movement,
    idBoxType = idBoxType,
    boxType = boxType,
    boxTypeDescription = boxTypeDescription,
    truckNumber = truckNumber,
    idTruckLine = idTruckLine,
    truckLineName = truckLineName,
    boxNumber = boxNumber,
    idBoxLine = idBoxLine,
    boxLineName = boxLineName,
    idCustomer = idCustomer,
    customerName = customerName,
    operatorName = operatorName,
    plates = plates,
    seals = seals,
    idCountry = idCountry,
    countryName = countryName,
    observations = observations,
    temperature = temperature,
    fuel = fuel,
    pedimento = pedimento,
    guideCount = guideCount,
    requiresImport = requiresImport,
    updated = updated,
    localPhotoPaths = localPhotoPaths,
    origenPreRegistro = origenPreRegistro,
    tipoRegistro = tipoRegistro,
    isExisting = casetaId.isNotBlank()
)

private fun CasetaEntradaDraftEntity.toForm(): CasetaEntradaForm = CasetaEntradaForm(
    casetaId = casetaId,
    idCaseta = idCaseta,
    idBodega = idBodega,
    bodega = bodega,
    operation = operation,
    movement = movement,
    idBoxType = idBoxType,
    boxType = boxType,
    boxTypeDescription = boxTypeDescription,
    truckNumber = truckNumber,
    idTruckLine = idTruckLine,
    truckLineName = truckLineName,
    boxNumber = boxNumber,
    idBoxLine = idBoxLine,
    boxLineName = boxLineName,
    idCustomer = idCustomer,
    customerName = customerName,
    operatorName = operatorName,
    plates = plates,
    seals = seals,
    idCountry = idCountry,
    countryName = countryName,
    observations = observations,
    temperature = temperature,
    fuel = fuel,
    pedimento = pedimento,
    guideCount = guideCount,
    requiresImport = requiresImport,
    updated = updated,
    localPhotoPaths = localPhotoPaths,
    remotePhotos = emptyList(),
    origenPreRegistro = origenPreRegistro,
    tipoRegistro = tipoRegistro
)

data class CasetaEntradaUiState(
    val form: CasetaEntradaForm = CasetaEntradaForm(),
    val currentUser: String = "",
    val currentUserName: String = "",
    val currentPlant: String = "",
    val puedeAgregar: Boolean = false,
    val puedeModificar: Boolean = false,
    val puedeBorrar: Boolean = false,
    val isLoadingCatalogs: Boolean = true,
    // true cuando clientes, lineas y tipos de caja salen de los catalogos guardados en la tableta: buscar ya no usa la red.
    val catalogosLocales: Boolean = false,
    // Los tipos de caja se piden aparte del catalogo grande: si estan vacios, la pantalla dice por que y deja reintentar.
    val isLoadingBoxTypes: Boolean = false,
    val boxTypesError: Boolean = false,
    val isSaving: Boolean = false,
    val isSearching: Boolean = false,
    val isLoadingRecord: Boolean = false,
    val showImportQuestion: Boolean = false,
    val showConfirm: Boolean = false,
    val fieldErrors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val recordLoadError: String? = null,
    val recordLoadingHint: String = "",
    val feedbackSeq: Int = 0,
    val savedSeq: Int = 0,
    val savedCasetaId: String? = null,
    val savedCaja: String? = null,
    val bodegas: List<CasetaEntradaCatalogItem> = emptyList(),
    val boxTypes: List<CasetaEntradaCatalogItem> = emptyList(),
    val truckLines: List<CasetaEntradaCatalogItem> = emptyList(),
    val boxLines: List<CasetaEntradaCatalogItem> = emptyList(),
    val customers: List<CasetaEntradaCatalogItem> = emptyList(),
    val countries: List<CasetaEntradaCatalogItem> = emptyList(),
    val customerResults: List<CasetaEntradaCatalogItem> = emptyList(),
    val truckLineResults: List<CasetaEntradaCatalogItem> = emptyList(),
    val boxLineResults: List<CasetaEntradaCatalogItem> = emptyList(),
    val frequentCustomers: List<CasetaEntradaFrequentItem> = emptyList(),
    val frequentTruckLines: List<CasetaEntradaFrequentItem> = emptyList(),
    val frequentBoxLines: List<CasetaEntradaFrequentItem> = emptyList(),
    val capture: CasetaEntradaOcrState = CasetaEntradaOcrState.Idle,
    val historySuggestions: List<CasetaEntradaBusquedaItem> = emptyList(),
    val isLoadingHistory: Boolean = false,
    val historySearch: String = "",
    val templateSource: String = "",
    val recordSearch: String = "",
    val savingProgress: SavingProgressState = SavingProgressState(),
    // Pre-registro escaneado en garita (camion sin caja).
    val isValidatingPreregistro: Boolean = false,
    val preregistroFolio: String = "",
    // null = el guardia aun no cotejo las placas fisicas contra las del QR.
    val preregistroPlacasConfirmadas: Boolean? = null,
    // Pre-registro ya validado (VIGENTE) pero pendiente de que el guardia coteje
    // las placas en el modal -- el formulario NO se llena hasta esa respuesta.
    val preregistroPendiente: CasetaPreregistroValidarResponse? = null,
    // Respaldo local: capturas sin terminar que sobreviven a que se cierre la app.
    val drafts: List<CasetaEntradaDraftEntity> = emptyList(),
    val showDraftRecovery: Boolean = false,
    val lastDraftSavedAt: Long? = null,
    // Se pregunta una sola vez por sesion de pantalla, y solo si habia algo que ofrecer.
    val draftRecoveryAsked: Boolean = false
) {
    val availableMovements: List<String>
        get() = CasetaEntradaRules.movementsFor(form.operation)

    val requiresCountry: Boolean
        get() = CasetaEntradaRules.requiresCountry(form.movement)

    val requiresGuide: Boolean
        get() = CasetaEntradaRules.requiresGuide(form.movement)

    val isRefrigerated: Boolean
        get() = CasetaEntradaRules.isRefrigerated(form.boxTypeDescription.ifBlank { form.boxType })

    // Datos del tractor que la plantilla ya no hereda: se resaltan en ambar mientras
    // sigan vacios. No son obligatorios, solo un recordatorio de captura.
    val pendingTruckFields: Set<String>
        get() = if (templateSource.isBlank()) emptySet() else buildSet {
            if (form.idTruckLine.isBlank() && form.truckLineName.isBlank()) add("truckLine")
            if (form.truckNumber.isBlank()) add("truckNumber")
            if (form.operatorName.isBlank()) add("operatorName")
            if (form.seals.isBlank()) add("seals")
        }
}

@HiltViewModel
class CasetaEntradaViewModel @Inject constructor(
    private val repository: CasetaEntradaRepository,
    private val catalogos: CatalogoRepository,
    private val localTextOcr: LocalTextOcr,
    private val paddleOcrEngine: PaddleOcrEngine,
    private val warehouseHubService: WarehouseHubService,
    private val zebraPrinterPreferences: com.example.pmmovil.data.printing.ZebraPrinterPreferences,
    private val zebraPrinterService: com.example.pmmovil.data.printing.ZebraPrinterService,
    private val labelZplFactory: com.example.pmmovil.data.printing.LabelZplFactory,
    private val labelBitmapRenderer: com.example.pmmovil.data.printing.LabelBitmapRenderer,
    private val zebraLabelLayoutPreferences: com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences,
    private val draftDao: CasetaEntradaDraftDao,
    private val casetaPhotoSyncDao: CasetaEntradaPhotoSyncDao,
    private val syncScheduler: OfflineSyncScheduler,
    @ApplicationScope private val applicationScope: CoroutineScope,
    private val authRepository: AuthRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        CasetaEntradaUiState(
            currentUser = authRepository.getUsername().orEmpty(),
            currentUserName = authRepository.getName().orEmpty(),
            currentPlant = authRepository.getPlanta().orEmpty(),
            puedeAgregar = authRepository.hasPermission(MobileModuleIds.GATE, "Agregar"),
            puedeModificar = authRepository.hasPermission(MobileModuleIds.GATE, "Modificar"),
            puedeBorrar = authRepository.hasPermission(MobileModuleIds.GATE, "Borrar"),
            // Los de la ultima vez que el servidor respondio: el selector nunca arranca vacio por una red lenta.
            boxTypes = repository.tiposCajaGuardados()
        )
    )
    val uiState: StateFlow<CasetaEntradaUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var boxTypesJob: Job? = null

    // Buscadores en memoria: se arman una vez al recibir los catalogos (antes se normalizaba todo con cada letra tecleada).
    private var customerIndex = emptyLookupIndex()
    private var truckLineIndex = emptyLookupIndex()
    private var boxLineIndex = emptyLookupIndex()
    // La bodega por omision se asigna una sola vez: si los catalogos se aplican de nuevo (una revision de version que
    // trajo cambios) no se le quita al guardia la bodega que ya eligio.
    private var defaultWarehouseApplied = false
    private var historyJob: Job? = null
    private var captureJob: Job? = null

    // Identidad del borrador que se autoguarda mientras el usuario captura. Se
    // renueva al empezar de cero, al cargar un registro existente o al guardar.
    private var draftId = UUID.randomUUID().toString()
    private var draftCreatedAt = System.currentTimeMillis()

    // Con lo que arrancó la captura, o lo último que se guardó (un registro cargado, una entrada recién guardada).
    // Sirve para saber si hay cambios sin guardar; ver isUnsaved.
    private var baselineForm = CasetaEntradaForm()

    // El usuario confirmó que descarta lo capturado: ni el autoguardado ni onCleared deben revivir su borrador.
    private var draftDiscarded = false

    fun canAdd(): Boolean = authRepository.hasPermission(MobileModuleIds.GATE, "Agregar")

    fun canModify(): Boolean = authRepository.hasPermission(MobileModuleIds.GATE, "Modificar")

    fun canDelete(): Boolean = authRepository.hasPermission(MobileModuleIds.GATE, "Borrar")

    /** El formulario nuevo crea; un registro cargado actualiza. */
    fun canEditCurrentForm(): Boolean =
        if (_uiState.value.form.casetaId.isBlank()) canAdd() else canModify()

    private fun denyPermission(action: String) {
        _uiState.update {
            it.copy(
                message = "✗ No tienes permiso para $action en Entradas de caseta.",
                feedbackSeq = it.feedbackSeq + 1
            )
        }
    }

    init {
        loadCatalogs()
        loadFrequentCatalogs()
        observeDrafts()
        observeFormChangesForAutosave()
        viewModelScope.launch {
            zebraLabelLayoutPreferences.syncWithServer()
        }
    }

    // La lista de borradores la manda Room: cualquier alta/baja se refleja sola
    // en la UI sin tener que releer a mano despues de cada operacion. El aviso
    // de recuperacion se ofrece una sola vez, y solo si de entrada habia algo.
    private fun observeDrafts() {
        viewModelScope.launch {
            var isFirstEmission = true
            draftDao.observeAll().collect { drafts ->
                _uiState.update { state ->
                    val showRecovery = if (isFirstEmission) {
                        drafts.isNotEmpty()
                    } else {
                        state.showDraftRecovery && drafts.isNotEmpty()
                    }
                    isFirstEmission = false
                    state.copy(
                        drafts = drafts,
                        showDraftRecovery = showRecovery,
                        draftRecoveryAsked = true
                    )
                }
            }
        }
    }

    // Autoguardado reactivo: escucha CUALQUIER cambio a `form` (sin importar que
    // funcion lo produjo) y lo respalda localmente tras 1.5s de inactividad. Evita
    // tener que acordarse de llamar un "scheduleDraftSave()" en cada setter.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeFormChangesForAutosave() {
        viewModelScope.launch {
            uiState
                .map { it.form }
                .distinctUntilChanged()
                .drop(1)
                .debounce(1_500)
                .collect { form -> persistDraft(form) }
        }
    }

    private suspend fun persistDraft(form: CasetaEntradaForm) {
        if (draftDiscarded) return
        if (!isUnsaved(form)) {
            // Quedó igual a lo guardado (o nunca hubo nada): no hay qué recuperar, y un borrador viejo de esta misma
            // captura ya sobra. Antes bastaba con abrir un registro para que dejara un «pendiente» sin editarlo.
            draftDao.deleteById(draftId)
            return
        }
        val now = System.currentTimeMillis()
        draftDao.upsert(
            form.toDraftEntity(id = draftId, createdAt = draftCreatedAt, lastEditAt = now)
        )
        prunePhotosAndOldDrafts()
        _uiState.update { it.copy(lastDraftSavedAt = now) }
    }

    // Los borradores viejos se tiran junto con sus fotos: si solo se borrara la
    // fila, los JPG quedarian ocupando espacio en el dispositivo para siempre.
    private suspend fun prunePhotosAndOldDrafts() {
        draftDao.getBeyondLimit(MAX_DRAFTS).forEach { old ->
            deleteDraftPhotos(old)
            draftDao.deleteById(old.id)
        }
    }

    private suspend fun deleteDraftPhotos(draft: CasetaEntradaDraftEntity) {
        withContext(Dispatchers.IO) {
            draft.localPhotoPaths.forEach { path ->
                runCatching { File(path).delete() }
            }
        }
    }

    /** Hay cambios que todavía no se guardan (la regla está en [CasetaEntradaDraftRules]). */
    private fun isUnsaved(form: CasetaEntradaForm): Boolean =
        CasetaEntradaDraftRules.isUnsaved(form, baselineForm)

    /** Salir (o empezar otra captura) debe preguntar antes: hay algo capturado que se perdería. */
    fun hasUnsavedChanges(): Boolean = isUnsaved(_uiState.value.form)

    /**
     * El usuario confirmó que descarta lo que llevaba: se borra su borrador y las fotos locales que aún no se encolan,
     * para que el aviso de «capturas sin terminar» no vuelva a salir por algo que él mismo descartó. Ese aviso queda
     * para cuando la app se cerró sola con la captura a medias, que es cuando el borrador sobrevive. Va en
     * `applicationScope` porque quien llama navega enseguida y `viewModelScope` se cancelaría a medio borrar.
     */
    fun discardCurrentDraft() {
        draftDiscarded = true
        val id = draftId
        val photoPaths = _uiState.value.form.localPhotoPaths
        applicationScope.launch {
            withContext(Dispatchers.IO) {
                photoPaths.forEach { path -> runCatching { File(path).delete() } }
            }
            draftDao.deleteById(id)
        }
    }

    fun resumeDraft(draft: CasetaEntradaDraftEntity) {
        if (!(if (draft.isExisting) canModify() else canAdd())) {
            denyPermission("continuar esta captura")
            return
        }
        draftId = draft.id
        draftCreatedAt = draft.createdAt
        draftDiscarded = false
        // Lo recuperado no se ha guardado: su línea base es una captura vacía, así que salir pregunta.
        baselineForm = CasetaEntradaForm()
        _uiState.update {
            it.copy(
                form = draft.toForm(),
                showDraftRecovery = false,
                message = "Borrador recuperado."
            )
        }
        if (draft.isExisting && draft.casetaId.isNotBlank()) {
            refreshRemotePhotosOnly(draft.casetaId)
        }
    }

    fun deleteDraft(draft: CasetaEntradaDraftEntity) {
        if (!canDelete()) {
            denyPermission("borrar borradores y sus fotos")
            return
        }
        viewModelScope.launch {
            deleteDraftPhotos(draft)
            draftDao.deleteById(draft.id)
        }
    }

    fun closeDraftRecovery() {
        _uiState.update { it.copy(showDraftRecovery = false) }
    }

    fun startNewCapture() {
        if (!canAdd()) {
            denyPermission("crear una entrada de caseta")
            return
        }
        resetForm()
        _uiState.update { it.copy(showDraftRecovery = false) }
    }

    // Solo trae de nuevo las fotos guardadas en servidor (no se persisten en el
    // borrador porque llegan como Bitmap). El resto de los campos ya vino del
    // borrador reanudado y no se debe pisar con lo que haya en el servidor.
    private fun refreshRemotePhotosOnly(casetaId: String) {
        viewModelScope.launch {
            repository.getRecord(casetaId, photosBase64 = false)
                .onSuccess { response ->
                    val remotePhotos = loadRemotePhotos(
                        casetaId = casetaId,
                        photosBase64 = response.fotosBase64,
                        photoInfos = response.fotos
                    )
                    _uiState.update { it.copy(form = it.form.copy(remotePhotos = remotePhotos)) }
                }
        }
    }

    /**
     * Pinta de inmediato los catalogos que la tableta ya tiene (clientes, lineas, tipos de caja...) y revisa su version en
     * segundo plano: solo se baja lo que cambio. Asi ni abrir la pantalla ni GUARDAR esperan a la red. El camino de siempre
     * (pedir todo al servidor) queda para la primera vez sin red o para un servidor anterior sin catalogos maestros.
     */
    fun loadCatalogs(force: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingCatalogs = true) }

            var hayLocales = applyLocalCatalogs()
            if (hayLocales) _uiState.update { it.copy(isLoadingCatalogs = false) }

            val sync = catalogos.sincronizar(CatalogoRepository.CASETA, forzar = force)
            if (sync is ResultadoSync.Actualizado) hayLocales = applyLocalCatalogs() || hayLocales

            if (hayLocales) {
                _uiState.update { it.copy(isLoadingCatalogs = false) }
            } else {
                loadCatalogsFromServer()
            }
        }
    }

    /** Aplica los catalogos guardados en la tableta. Devuelve false si todavia no hay lo indispensable. */
    private suspend fun applyLocalCatalogs(): Boolean {
        val customers = catalogos.items(CatalogoRepository.CLIENTES)
            .filter { it.extra == CatalogoRepository.CLIENTE_DE_CASETA }
            .map(::toCasetaCatalogItem)
        val lines = catalogos.items(CatalogoRepository.LINEAS).map(::toCasetaCatalogItem)
        val boxTypes = catalogos.items(CatalogoRepository.TIPOS_CAJA).map(::toCasetaCatalogItem)
        if (customers.isEmpty() || lines.isEmpty() || boxTypes.isEmpty()) return false

        applyCatalogs(
            warehouses = catalogos.items(CatalogoRepository.BODEGAS_CASETA).map(::toCasetaCatalogItem),
            boxTypes = boxTypes,
            truckLines = lines,
            boxLines = lines,
            customers = customers,
            countries = catalogos.items(CatalogoRepository.PAISES_CASETA).map(::toCasetaCatalogItem),
            local = true
        )
        return true
    }

    private fun toCasetaCatalogItem(item: CatalogoItem) = CasetaEntradaCatalogItem(id = item.id, nombre = item.nombre)

    private fun applyCatalogs(
        warehouses: List<CasetaEntradaCatalogItem>,
        boxTypes: List<CasetaEntradaCatalogItem>,
        truckLines: List<CasetaEntradaCatalogItem>,
        boxLines: List<CasetaEntradaCatalogItem>,
        customers: List<CasetaEntradaCatalogItem>,
        countries: List<CasetaEntradaCatalogItem>,
        local: Boolean
    ) {
        customerIndex = lookupIndex(customers)
        truckLineIndex = lookupIndex(truckLines)
        boxLineIndex = if (boxLines == truckLines) truckLineIndex else lookupIndex(boxLines)

        val selectedWarehouse = warehouses.firstOrNull()?.takeIf { !defaultWarehouseApplied }
        if (selectedWarehouse != null) defaultWarehouseApplied = true
        _uiState.update { state ->
            state.copy(
                catalogosLocales = local,
                bodegas = warehouses,
                boxTypes = boxTypes.ifEmpty { state.boxTypes },
                boxTypesError = false,
                truckLines = truckLines,
                boxLines = boxLines,
                customers = customers,
                countries = countries,
                customerResults = customers.take(25),
                truckLineResults = truckLines.take(25),
                boxLineResults = boxLines.take(25),
                form = selectedWarehouse?.takeIf { state.form.casetaId.isBlank() }?.let {
                    state.form.copy(idBodega = it.id, bodega = it.nombre)
                } ?: state.form
            )
        }
    }

    /** El camino de siempre: todo el catalogo desde el servidor. Solo si la tableta no tiene nada guardado. */
    private suspend fun loadCatalogsFromServer() {
        loadBoxTypes()
        repository.getCatalogs()
            .onSuccess { catalogs ->
                applyCatalogs(
                    warehouses = catalogs.bodegas,
                    boxTypes = catalogs.tiposCaja,
                    truckLines = catalogs.lineasCamion,
                    boxLines = catalogs.lineasCaja,
                    customers = catalogs.clientes,
                    countries = catalogs.paises,
                    local = false
                )
                _uiState.update { it.copy(isLoadingCatalogs = false) }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingCatalogs = false,
                        message = error.message ?: "No se pudieron cargar los catalogos.",
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            }
    }

    /**
     * Pide solo los tipos de caja (ocho renglones) sin esperar al catalogo completo, y reintenta solo: antes un fallo
     * del catalogo grande dejaba el selector de «Tipo caja» vacío y el guardia no podía registrar la entrada. Si no hay
     * manera de traerlos se queda con los últimos conocidos; sin ninguno, la pantalla ofrece reintentar.
     */
    fun loadBoxTypes() {
        if (boxTypesJob?.isActive == true) return
        // Con los catalogos guardados en la tableta los tipos de caja ya estan: no hay nada que pedir.
        if (_uiState.value.catalogosLocales && _uiState.value.boxTypes.isNotEmpty()) return
        boxTypesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBoxTypes = true, boxTypesError = false) }
            repeat(BOX_TYPES_ATTEMPTS) { attempt ->
                val result = repository.getBoxTypes()
                val tipos = result.getOrNull()
                if (tipos != null) {
                    _uiState.update { it.copy(boxTypes = tipos, isLoadingBoxTypes = false, boxTypesError = false) }
                    return@launch
                }
                Log.w("CasetaEntradaViewModel", "Tipos de caja: intento ${attempt + 1}/$BOX_TYPES_ATTEMPTS fallo (${result.exceptionOrNull()?.message})")
                if (attempt < BOX_TYPES_ATTEMPTS - 1) delay(BOX_TYPES_RETRY_MS * (attempt + 1))
            }
            _uiState.update { it.copy(isLoadingBoxTypes = false, boxTypesError = it.boxTypes.isEmpty()) }
        }
    }

    fun loadFrequentCatalogs() {
        viewModelScope.launch {
            val customers = repository.getFrequentItems("cliente", limit = 8, days = 90)
                .getOrElse { emptyList() }
            val truckLines = repository.getFrequentItems("linea-camion", limit = 8, days = 90)
                .getOrElse { emptyList() }
            val boxLines = repository.getFrequentItems("linea-caja", limit = 8, days = 90)
                .getOrElse { emptyList() }

            _uiState.update {
                it.copy(
                    frequentCustomers = customers,
                    frequentTruckLines = truckLines,
                    frequentBoxLines = boxLines
                )
            }
        }
    }

    fun updateOperation(value: String) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(
                    operation = value,
                    movement = "",
                    guideCount = "",
                    pedimento = if (value == CasetaEntradaRules.OP_EXPORT) it.form.pedimento else "",
                    requiresImport = false
                ),
                fieldErrors = it.fieldErrors - "operation" - "movement" - "guideCount"
            )
        }
    }

    /**
     * Fija el tipo de registro (CAJA/TRACTOCAMION) al iniciar una captura nueva --
     * lo llama la pantalla de eleccion (o la promocion de un pre-registro QR, ver
     * applyPreregistro). Si al elegir TRACTOCAMION ya habia datos de caja/cliente
     * capturados, se limpian: dejarlos a medio llenar solo confundiria, ya que la
     * UI los oculta por completo en ese modo.
     */
    fun setTipoRegistro(value: String) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        val normalizado = if (value == TipoRegistro.TRACTOCAMION) TipoRegistro.TRACTOCAMION else TipoRegistro.CAJA
        _uiState.update { st ->
            if (st.form.tipoRegistro == normalizado) return@update st
            st.copy(
                form = st.form.copy(
                    tipoRegistro = normalizado,
                    idBoxType = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.idBoxType,
                    boxType = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.boxType,
                    boxTypeDescription = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.boxTypeDescription,
                    boxNumber = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.boxNumber,
                    idBoxLine = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.idBoxLine,
                    boxLineName = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.boxLineName,
                    idCustomer = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.idCustomer,
                    customerName = if (normalizado == TipoRegistro.TRACTOCAMION) "" else st.form.customerName
                ),
                fieldErrors = st.fieldErrors - "idBoxType" - "customer" - "boxLine" - "boxNumber"
            )
        }
    }

    fun updateMovement(value: String) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            val clearCountry = !CasetaEntradaRules.requiresCountry(value)
            it.copy(
                form = it.form.copy(
                    movement = value,
                    idCountry = if (clearCountry) "" else it.form.idCountry,
                    countryName = if (clearCountry) "" else it.form.countryName,
                    guideCount = if (CasetaEntradaRules.requiresGuide(value)) it.form.guideCount else ""
                ),
                fieldErrors = it.fieldErrors - "movement" - "idCountry" - "plates" - "guideCount"
            )
        }
    }

    fun updateText(field: String, value: String) {
        // Los campos de búsqueda solo consultan; no requieren Add/Modificar.
        if (field != "historySearch" && field != "recordSearch" && !canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        val normalizedHistoryTerm = when (field) {
            "boxNumber", "plates", "historySearch" -> value.uppercase()
            else -> null
        }
        val historySearchType = when (field) {
            "boxNumber" -> CasetaEntradaHistorySearchType.Box
            "plates" -> CasetaEntradaHistorySearchType.Plate
            else -> CasetaEntradaHistorySearchType.General
        }
        _uiState.update { state ->
            val form = when (field) {
                "truckNumber" -> state.form.copy(truckNumber = value.uppercase())
                "boxNumber" -> state.form.copy(boxNumber = value.uppercase())
                "operatorName" -> state.form.copy(operatorName = value.uppercase())
                "plates" -> state.form.copy(plates = value.uppercase())
                "seals" -> state.form.copy(seals = value.uppercase())
                "observations" -> state.form.copy(observations = value)
                "temperature" -> state.form.copy(temperature = value)
                "fuel" -> state.form.copy(fuel = value)
                "pedimento" -> state.form.copy(pedimento = value.filter(Char::isDigit).take(7))
                "guideCount" -> state.form.copy(guideCount = value.filter(Char::isDigit).take(4))
                "historySearch" -> {
                    return@update state.copy(historySearch = value.uppercase())
                }
                "recordSearch" -> {
                    return@update state.copy(recordSearch = value)
                }
                else -> state.form
            }
            state.copy(form = form, fieldErrors = state.fieldErrors - field)
        }
        normalizedHistoryTerm?.let { term ->
            queueHistorySuggestions(
                term = term,
                debounce = true,
                showEmptyMessage = false,
                searchType = historySearchType
            )
        }
    }

    fun selectBoxType(item: CasetaEntradaCatalogItem) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(
                    idBoxType = item.id,
                    boxType = item.id,
                    boxTypeDescription = item.nombre,
                    temperature = if (CasetaEntradaRules.isRefrigerated(item.nombre)) it.form.temperature else "",
                    fuel = if (CasetaEntradaRules.isRefrigerated(item.nombre)) it.form.fuel else ""
                ),
                fieldErrors = it.fieldErrors - "idBoxType" - "temperature" - "fuel"
            )
        }
    }

    fun selectCustomer(item: CasetaEntradaCatalogItem) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(idCustomer = item.id, customerName = item.nombre),
                fieldErrors = it.fieldErrors - "customer"
            )
        }
    }

    fun selectTruckLine(item: CasetaEntradaCatalogItem) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(idTruckLine = item.id, truckLineName = item.nombre),
                fieldErrors = it.fieldErrors - "truckLine"
            )
        }
    }

    fun selectBoxLine(item: CasetaEntradaCatalogItem) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(idBoxLine = item.id, boxLineName = item.nombre),
                fieldErrors = it.fieldErrors - "boxLine"
            )
        }
    }

    fun selectCountry(item: CasetaEntradaCatalogItem) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(idCountry = item.id, countryName = item.nombre),
                fieldErrors = it.fieldErrors - "idCountry"
            )
        }
    }

    fun clearSelection(type: CasetaEntradaLookupType) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        _uiState.update {
            when (type) {
                CasetaEntradaLookupType.Customer -> it.copy(
                    form = it.form.copy(idCustomer = "", customerName = ""),
                    fieldErrors = it.fieldErrors - "customer"
                )
                CasetaEntradaLookupType.TruckLine -> it.copy(
                    form = it.form.copy(idTruckLine = "", truckLineName = ""),
                    fieldErrors = it.fieldErrors - "truckLine"
                )
                CasetaEntradaLookupType.BoxLine -> it.copy(
                    form = it.form.copy(idBoxLine = "", boxLineName = ""),
                    fieldErrors = it.fieldErrors - "boxLine"
                )
            }
        }
    }

    private fun lookupIndexFor(type: CasetaEntradaLookupType) = when (type) {
        CasetaEntradaLookupType.Customer -> customerIndex
        CasetaEntradaLookupType.TruckLine -> truckLineIndex
        CasetaEntradaLookupType.BoxLine -> boxLineIndex
    }

    fun searchLookup(type: CasetaEntradaLookupType, query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            val cleanQuery = query.trim()
            if (_uiState.value.catalogosLocales) {
                // Todo el catalogo esta en la tableta: se busca aqui, al instante y sin red.
                val matches = lookupIndexFor(type).search(cleanQuery, 50)
                _uiState.update {
                    when (type) {
                        CasetaEntradaLookupType.Customer -> it.copy(customerResults = matches, isSearching = false)
                        CasetaEntradaLookupType.TruckLine -> it.copy(truckLineResults = matches, isSearching = false)
                        CasetaEntradaLookupType.BoxLine -> it.copy(boxLineResults = matches, isSearching = false)
                    }
                }
                return@launch
            }

            val localMatches = lookupIndexFor(type).search(cleanQuery, 25)
            _uiState.update {
                when (type) {
                    CasetaEntradaLookupType.Customer -> it.copy(customerResults = localMatches, isSearching = true)
                    CasetaEntradaLookupType.TruckLine -> it.copy(truckLineResults = localMatches, isSearching = true)
                    CasetaEntradaLookupType.BoxLine -> it.copy(boxLineResults = localMatches, isSearching = true)
                }
            }
            delay(250)
            val result = when (type) {
                CasetaEntradaLookupType.Customer -> repository.searchCustomers(cleanQuery)
                CasetaEntradaLookupType.TruckLine,
                CasetaEntradaLookupType.BoxLine -> repository.searchTransports(cleanQuery)
            }
            result
                .onSuccess { items ->
                    val merged = mergeCatalogItems(items, localMatches).take(50)
                    _uiState.update {
                        when (type) {
                            CasetaEntradaLookupType.Customer -> it.copy(
                                customerResults = merged,
                                isSearching = false
                            )
                            CasetaEntradaLookupType.TruckLine -> it.copy(
                                truckLineResults = merged,
                                isSearching = false
                            )
                            CasetaEntradaLookupType.BoxLine -> it.copy(
                                boxLineResults = merged,
                                isSearching = false
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            message = error.message ?: "No se pudo buscar.",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun addPhoto(path: String) {
        if (!canAdd()) {
            denyPermission("agregar fotos de caseta")
            return
        }
        if (path.isBlank()) return
        _uiState.update { state ->
            if (state.form.photoCount >= 15) {
                state.copy(
                    message = "Maximo 15 fotos por registro.",
                    feedbackSeq = state.feedbackSeq + 1
                )
            } else {
                state.copy(
                    form = state.form.copy(localPhotoPaths = state.form.localPhotoPaths + path),
                    fieldErrors = state.fieldErrors - "photos"
                )
            }
        }
    }

    fun removeLocalPhoto(path: String) {
        if (!canDelete()) {
            denyPermission("borrar fotos de caseta")
            return
        }
        _uiState.update {
            it.copy(form = it.form.copy(localPhotoPaths = it.form.localPhotoPaths - path))
        }
        runCatching { File(path).delete() }
    }

    fun removeRemotePhoto(index: Int) {
        if (!canDelete()) {
            denyPermission("borrar fotos de caseta")
            return
        }
        _uiState.update { state ->
            state.copy(
                form = state.form.copy(
                    remotePhotos = state.form.remotePhotos.filterIndexed { i, _ -> i != index }
                )
            )
        }
    }

    fun runSmartCaptureFromLocalPhoto(mode: CasetaEntradaCaptureMode, path: String) {
        if (path.isBlank()) return
        val sourceId = "local-$path"
        runSmartCapture(
            mode = mode,
            sourceId = sourceId,
            localSource = { readLocalFileWithPaddleFallback(path) },
            serverSource = { withContext(Dispatchers.Default) { encodePhotoForOcr(path) } }
        )
    }

    fun runSmartCaptureFromBase64(
        mode: CasetaEntradaCaptureMode,
        base64Image: String,
        index: Int
    ) {
        if (base64Image.isBlank()) return
        val sourceId = "remote-$index"
        runSmartCapture(
            mode = mode,
            sourceId = sourceId,
            localSource = { readBase64WithPaddleFallback(base64Image) },
            serverSource = { base64Image }
        )
    }

    // Corren en paralelo y se fusionan: un acierto parcial de Paddle no debe tapar la red de seguridad de ML Kit (dígitos verticales).
    private suspend fun readLocalFileWithPaddleFallback(path: String): Result<LocalTextOcrResult> =
        mergeEngineResults(
            paddle = { paddleOcrEngine.readFromFile(path) },
            mlKit = { localTextOcr.readFromFile(path) }
        )

    private suspend fun readBase64WithPaddleFallback(base64Image: String): Result<LocalTextOcrResult> =
        mergeEngineResults(
            paddle = { paddleOcrEngine.readFromBase64(base64Image) },
            mlKit = { localTextOcr.readFromBase64(base64Image) }
        )

    // Secuencial a propósito: correr ambos motores a la vez duplicaba el pico de CPU y dejaba
    // al HAL de la cámara sin buffers ("Failed to get stream buffer", preview a 3-4fps).
    private suspend fun mergeEngineResults(
        paddle: suspend () -> Result<LocalTextOcrResult>,
        mlKit: suspend () -> Result<LocalTextOcrResult>
    ): Result<LocalTextOcrResult> {
        val paddleResult = paddle().getOrNull()
        val mlKitResult = mlKit().getOrNull()
        Log.d(
            "OCR_ENGINE",
            "paddle=${paddleResult?.candidates?.size ?: -1} mlkit=${mlKitResult?.candidates?.size ?: -1} candidatos"
        )
        val merged = mergeLocalResults(
            paddleResult ?: LocalTextOcrResult("", emptyList()),
            mlKitResult ?: LocalTextOcrResult("", emptyList())
        )
        return if (merged.candidates.isNotEmpty() || merged.rawText.isNotBlank()) {
            Result.success(merged)
        } else {
            Result.failure(IllegalStateException("No se detectó texto en la imagen."))
        }
    }

    fun applyCaptureCandidate(value: String) {
        if (!canEditCurrentForm()) {
            denyPermission("editar la entrada de caseta")
            return
        }
        val state = _uiState.value
        val mode = state.capture.mode ?: return
        val clean = value.trim().uppercase()
        if (clean.isBlank()) return

        val historyType = if (mode == CasetaEntradaCaptureMode.Box) {
            CasetaEntradaHistorySearchType.Box
        } else {
            CasetaEntradaHistorySearchType.Plate
        }

        _uiState.update { current ->
            val updatedForm = when (mode) {
                CasetaEntradaCaptureMode.Box -> current.form.copy(boxNumber = clean)
                CasetaEntradaCaptureMode.Plate -> current.form.copy(plates = clean)
            }
            current.copy(
                form = updatedForm,
                historySearch = clean,
                fieldErrors = current.fieldErrors - when (mode) {
                    CasetaEntradaCaptureMode.Box -> "boxNumber"
                    CasetaEntradaCaptureMode.Plate -> "plates"
                },
                capture = current.capture.copy(
                    isLoading = false,
                    primaryValue = clean,
                    phase = CasetaEntradaOcrPhase.Ready,
                    outcome = CasetaEntradaOcrOutcome.AutoApplied,
                    message = if (mode == CasetaEntradaCaptureMode.Box) {
                        "Caja encontrada."
                    } else {
                        "Placa encontrada."
                    }
                )
            )
        }
        queueHistorySuggestions(clean, debounce = false, showEmptyMessage = false, searchType = historyType)
    }

    fun dismissCapture() {
        _uiState.update { it.copy(capture = CasetaEntradaOcrState.Idle) }
    }

    fun retryCapture() {
        val state = _uiState.value
        val mode = state.capture.mode ?: return
        val sourceId = state.capture.sourceId
        when {
            sourceId.startsWith("local-") -> runSmartCaptureFromLocalPhoto(mode, sourceId.removePrefix("local-"))
            sourceId.startsWith("remote-") -> {
                val index = sourceId.removePrefix("remote-").toIntOrNull() ?: return
                state.form.remotePhotos.getOrNull(index)?.base64?.takeIf(String::isNotBlank)?.let { base64 ->
                    runSmartCaptureFromBase64(mode, base64, index)
                }
            }
        }
    }

    /**
     * El escaner de esta pantalla es EXCLUSIVO para QR de pre-registro (folio
     * "PMMPR|..."). No se usa para llenar Numero de caja desde cualquier QR --
     * eso no se practica en bodega, se quito a proposito. Si se lee algo que no
     * es un pre-registro, solo se avisa; no se toca ningun campo del formulario.
     */
    fun onScannerInput(raw: String) {
        val current = _uiState.value
        if (current.isValidatingPreregistro || current.preregistroPendiente != null) {
            return
        }
        val trimmedRaw = raw.trim()
        if (trimmedRaw.startsWith("PMMPR|")) {
            applyPreregistro(trimmedRaw)
            return
        }

        _uiState.update {
            it.copy(
                message = "Ese codigo no es un QR de pre-registro.",
                feedbackSeq = it.feedbackSeq + 1
            )
        }
    }

    /**
     * Escaneo de un QR de pre-registro (camion sin caja capturado por la linea
     * transportista desde el portal). Solo VALIDA y deja el resultado en
     * `preregistroPendiente` -- el formulario NO se llena todavia. La pantalla
     * abre un modal bloqueante con las placas del QR; solo al responder ahi
     * (ver confirmarPreregistroPlacasCoinciden/RechazarPreregistroPlacasNoCoinciden)
     * se decide que datos entran al formulario. Antes, el formulario se
     * llenaba de inmediato y los botones de cotejo aparecian ya con todo
     * capturado -- el cotejo dejaba de ser un filtro real.
     */
    fun applyPreregistro(codigo: String) {
        val current = _uiState.value
        if (current.isValidatingPreregistro || current.preregistroPendiente != null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingPreregistro = true) }
            repository.validatePreregistro(codigo)
                .onSuccess { response ->
                    _uiState.update { st ->
                        when (response.estatus) {
                            "VIGENTE" -> st.copy(
                                isValidatingPreregistro = false,
                                preregistroPendiente = response,
                                feedbackSeq = st.feedbackSeq + 1
                            )
                            else -> st.copy(
                                isValidatingPreregistro = false,
                                message = mensajePreregistroNoVigente(response),
                                feedbackSeq = st.feedbackSeq + 1
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isValidatingPreregistro = false,
                            message = error.message ?: "No se pudo validar el codigo de pre-registro.",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    /**
     * El guardia confirma en el modal que las placas fisicas SI coinciden con
     * las del QR: recien aqui se llena el formulario completo (camion, linea,
     * operador, placas), igual que antes hacia applyPreregistro de una vez.
     */
    fun confirmarPreregistroPlacasCoinciden() {
        if (!canAdd()) {
            denyPermission("crear una entrada de caseta")
            return
        }
        _uiState.update { st ->
            val response = st.preregistroPendiente ?: return@update st
            st.copy(
                form = CasetaEntradaForm(
                    idBodega = st.form.idBodega,
                    bodega = st.form.bodega,
                    // RECOLECCION/PICKUP vive en exportMovements dentro de las
                    // reglas de esta app (CasetaEntradaRules) -- se fija la
                    // operacion a EXPORTACION para que coincida con el picker
                    // si alguien abre el registro despues.
                    operation = CasetaEntradaRules.OP_EXPORT,
                    movement = "RECOLECCION/PICKUP",
                    truckNumber = response.nunidad,
                    idTruckLine = response.idTransporu,
                    truckLineName = response.nombreLinea,
                    operatorName = response.operador,
                    plates = response.placas,
                    localPhotoPaths = st.form.localPhotoPaths,
                    origenPreRegistro = true,
                    tipoRegistro = TipoRegistro.TRACTOCAMION
                ),
                fieldErrors = emptyMap(),
                templateSource = "",
                preregistroFolio = response.folio,
                preregistroPlacasConfirmadas = true,
                preregistroPendiente = null,
                message = "Placas confirmadas. Datos del pre-registro cargados.",
                feedbackSeq = st.feedbackSeq + 1
            )
        }
    }

    /**
     * El guardia confirma en el modal que las placas fisicas NO coinciden con
     * las del QR. La entrada NO se niega: se deja la pantalla lista en modo
     * Tractocamion para captura manual (el guardia teclea lo que ve de
     * verdad), pero SIN precargar unidad/linea/operador/placas del QR --
     * llenar el formulario con datos que ya se sabe que no corresponden al
     * camion fisico serviria de poco. El folio sigue viajando (origenPreRegistro
     * + preregistroFolio) para que al guardar se marque ese pre-registro como
     * usado con placasNoCoinciden=true, y quede el reporte para que el
     * encargado de bodega le de seguimiento.
     */
    fun rechazarPreregistroPlacasNoCoinciden() {
        if (!canAdd()) {
            denyPermission("crear una entrada de caseta")
            return
        }
        _uiState.update { st ->
            val response = st.preregistroPendiente ?: return@update st
            st.copy(
                form = CasetaEntradaForm(
                    idBodega = st.form.idBodega,
                    bodega = st.form.bodega,
                    operation = CasetaEntradaRules.OP_EXPORT,
                    movement = "RECOLECCION/PICKUP",
                    localPhotoPaths = st.form.localPhotoPaths,
                    origenPreRegistro = true,
                    tipoRegistro = TipoRegistro.TRACTOCAMION
                ),
                fieldErrors = emptyMap(),
                templateSource = "",
                preregistroFolio = response.folio,
                preregistroPlacasConfirmadas = false,
                preregistroPendiente = null,
                message = "Placas no coinciden. Captura los datos reales del camión y avisa al encargado de bodega.",
                feedbackSeq = st.feedbackSeq + 1
            )
        }
    }

    /** Cierra el modal sin aplicar nada -- como si nunca se hubiera escaneado el QR. */
    fun cancelarPreregistroPendiente() {
        _uiState.update { it.copy(preregistroPendiente = null) }
    }

    private fun mensajePreregistroNoVigente(response: CasetaPreregistroValidarResponse): String = when (response.estatus) {
        "USADO" -> "Este codigo ya se uso" + (response.fechaUso?.let { " (a las $it)" } ?: "") + ". Captura a mano si es necesario."
        "EXPIRADO" -> "Este codigo ya vencio. Captura a mano o pide un codigo nuevo al chofer."
        "CANCELADO" -> "Este pre-registro fue cancelado. Captura a mano si es necesario."
        else -> "No existe este pre-registro. Captura a mano."
    }

    fun loadRecordBySearch() {
        val casetaId = _uiState.value.recordSearch.trim()
        if (casetaId.isBlank()) {
            _uiState.update {
                it.copy(
                    message = "Captura el CasetaId que quieres cargar.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }
        loadRecord(casetaId)
    }

    fun loadRecord(casetaId: String) {
        val clean = casetaId.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingRecord = true,
                    recordLoadError = null,
                    recordLoadingHint = "Cargando registro..."
                )
            }
            // fotosBase64 = false: el detalle debe responder rapido con solo datos + metadata de fotos.
            // Las fotos se traen aparte (una por una, en paralelo) via loadRemotePhotos para no
            // arrastrar todas las imagenes originales en un solo JSON y provocar timeout.
            repository.getRecord(clean, photosBase64 = false)
                .onSuccess { response ->
                    val data = response.datos
                    val detailCasetaId = data?.casetaId?.takeIf(String::isNotBlank) ?: clean
                    val photoCount = response.fotosBase64.count(String::isNotBlank)
                        .takeIf { it > 0 }
                        ?: response.fotos.size
                    _uiState.update {
                        it.copy(
                            recordLoadingHint = if (photoCount > 0) {
                                "Cargando $photoCount fotos de evidencia..."
                            } else {
                                "Preparando formulario..."
                            }
                        )
                    }
                    val remotePhotos = loadRemotePhotos(
                        casetaId = detailCasetaId,
                        photosBase64 = response.fotosBase64,
                        photoInfos = response.fotos
                    )
                    // Registro existente cargado: arranca su propio borrador, separado
                    // de cualquier captura nueva que se haya estado llenando antes.
                    draftId = UUID.randomUUID().toString()
                    draftCreatedAt = System.currentTimeMillis()
                    draftDiscarded = false
                    _uiState.update { state ->
                        // El registro tal como llegó es la línea base: abrirlo y salir no cuenta como cambio.
                        val loadedForm = data?.toForm(remotePhotos) ?: state.form
                        baselineForm = loadedForm
                        state.copy(
                            isLoadingRecord = false,
                            recordLoadError = null,
                            recordLoadingHint = "",
                            form = loadedForm,
                            templateSource = "",
                            message = "Registro cargado para edicion.",
                            feedbackSeq = state.feedbackSeq + 1
                        )
                    }
                }
                .onFailure { error ->
                    val message = error.message ?: "No se pudo cargar el registro."
                    _uiState.update {
                        it.copy(
                            isLoadingRecord = false,
                            recordLoadError = message,
                            recordLoadingHint = "",
                            message = message,
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun searchHistoryNow() {
        val state = _uiState.value
        val term = state.historySearch
            .ifBlank { state.form.boxNumber }
            .ifBlank { state.form.plates }
            .trim()

        if (term.length < 3) {
            _uiState.update {
                it.copy(
                    message = "Captura caja, placa, cliente o CasetaId para buscar historico.",
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }

        val historyType = when {
            state.form.boxNumber.equals(term, ignoreCase = true) -> CasetaEntradaHistorySearchType.Box
            state.form.plates.equals(term, ignoreCase = true) -> CasetaEntradaHistorySearchType.Plate
            else -> CasetaEntradaHistorySearchType.General
        }
        queueHistorySuggestions(term, debounce = false, showEmptyMessage = true, searchType = historyType)
    }

    fun applyHistorySuggestion(casetaId: String, onApplied: () -> Unit = {}) {
        if (!canAdd()) {
            denyPermission("crear una entrada desde el histórico")
            return
        }
        val clean = casetaId.trim()
        if (clean.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingRecord = true) }
            repository.getRecord(clean, photosBase64 = false)
                .onSuccess { response ->
                    val data = response.datos
                    _uiState.update { state ->
                        if (data == null) {
                            state.copy(
                                isLoadingRecord = false,
                                message = "No se pudo cargar la sugerencia seleccionada.",
                                feedbackSeq = state.feedbackSeq + 1
                            )
                        } else {
                            state.copy(
                                isLoadingRecord = false,
                                form = data.toTemplateForm(state.form.localPhotoPaths),
                                fieldErrors = emptyMap(),
                                templateSource = clean,
                                message = "Datos sugeridos cargados. Revisa y guarda una nueva entrada.",
                                feedbackSeq = state.feedbackSeq + 1
                            )
                        }
                    }
                    if (data != null) onApplied()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingRecord = false,
                            message = error.message ?: "No se pudo cargar la sugerencia.",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    fun requestReview(updateMode: Boolean = false) {
        val allowed = if (updateMode) canModify() else canAdd()
        if (!allowed) {
            denyPermission(if (updateMode) "modificar la entrada de caseta" else "crear una entrada de caseta")
            return
        }
        val errors = CasetaEntradaRules.validate(_uiState.value.form)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    fieldErrors = errors,
                    message = errors.values.first(),
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }

        _uiState.update {
            if (!updateMode && it.form.operation == CasetaEntradaRules.OP_EXPORT) {
                it.copy(showImportQuestion = true, fieldErrors = emptyMap())
            } else {
                it.copy(showConfirm = true, fieldErrors = emptyMap())
            }
        }
    }

    fun answerRequiresImport(required: Boolean) {
        if (!canAdd()) {
            denyPermission("crear una entrada de caseta")
            return
        }
        _uiState.update {
            it.copy(
                form = it.form.copy(requiresImport = required),
                showImportQuestion = false,
                showConfirm = true
            )
        }
    }

    fun dismissDialogs() {
        _uiState.update { it.copy(showImportQuestion = false, showConfirm = false) }
    }

    fun closeSavingOverlay() {
        _uiState.update { it.copy(savingProgress = SavingProgressState(isVisible = false)) }
    }

    fun confirmSave() {
        val state = _uiState.value
        val allowed = if (state.form.casetaId.isBlank()) canAdd() else canModify()
        if (!allowed) {
            denyPermission(if (state.form.casetaId.isBlank()) "crear una entrada de caseta" else "modificar la entrada de caseta")
            return
        }
        val errors = CasetaEntradaRules.validate(state.form)
        if (errors.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    showConfirm = false,
                    fieldErrors = errors,
                    message = errors.values.first(),
                    feedbackSeq = it.feedbackSeq + 1
                )
            }
            return
        }

        viewModelScope.launch {
            val initialSteps = listOf(
                SavingProgressStep("val", "Validando formulario", "Verificando datos requeridos", SavingStepStatus.SUCCESS),
                SavingProgressStep("sig", "Notificación SignalR", "Reportando actividad en tiempo real", SavingStepStatus.ACTIVE),
                SavingProgressStep("photos", "Fotos en cola", "Se sincronizarán en lotes de 4", SavingStepStatus.PENDING),
                SavingProgressStep("save", "Guardando en servidor", "Registrando folios", SavingStepStatus.PENDING),
                SavingProgressStep("finish", "Finalizando", "Confirmando registro de caseta", SavingStepStatus.PENDING)
            )

            _uiState.update {
                it.copy(
                    isSaving = true,
                    showConfirm = false,
                    savingProgress = SavingProgressState(
                        isVisible = true,
                        percent = 20,
                        currentTitle = "Notificando vía SignalR...",
                        steps = initialSteps
                    )
                )
            }

            // Reportar via SignalR
            try {
                warehouseHubService.startConnection()
                val refBox = state.form.boxNumber.ifBlank { "ENTRADA" }
                val user = state.currentUser.ifBlank { "Caseta" }
                warehouseHubService.registrarActividad(refBox, user, state.form.idCustomer)
            } catch (ex: Exception) {
                Log.w("CasetaEntradaViewModel", "SignalR broadcast warning: ${ex.message}")
            }

            // Avanzar paso SignalR -> Éxito, activar paso Fotos
            _uiState.update { st ->
                st.copy(
                    savingProgress = st.savingProgress.copy(
                        percent = 40,
                        currentTitle = "Preparando evidencia para sincronización...",
                        steps = st.savingProgress.steps.map { s ->
                            when (s.id) {
                                "sig" -> s.copy(status = SavingStepStatus.SUCCESS)
                                "photos" -> s.copy(status = SavingStepStatus.ACTIVE)
                                else -> s
                            }
                        }
                    )
                )
            }

            // Las fotos nuevas no viajan en el guardado principal. Se conservan
            // como archivos locales y se mandan despues en lotes pequenos. Los
            // marcadores keep preservan las fotos remotas cuando se edita.
            val existingPhotoMarkers = state.form.remotePhotos.mapNotNull { remote ->
                remote.sourceIndex?.takeIf { it > 0 }?.let { "keep:$it" }
            }

            // La evidencia queda respaldada localmente; activar el guardado del folio.
            _uiState.update { st ->
                st.copy(
                    savingProgress = st.savingProgress.copy(
                        percent = 70,
                        currentTitle = "Guardando entrada en servidor...",
                        steps = st.savingProgress.steps.map { s ->
                            when (s.id) {
                                "photos" -> s.copy(status = SavingStepStatus.SUCCESS)
                                "save" -> s.copy(status = SavingStepStatus.ACTIVE)
                                else -> s
                            }
                        }
                    )
                )
            }

            val request = state.form.toRequest(
                photos = existingPhotoMarkers,
                user = state.currentUser,
                userName = state.currentUserName.ifBlank { state.currentUser },
                plant = state.currentPlant,
                diferirCorreo = state.form.localPhotoPaths.isNotEmpty(),
                preregistroFolio = state.preregistroFolio,
                placasNoCoinciden = state.preregistroPlacasConfirmadas == false
            )

            repository.save(request)
                .onSuccess { response ->
                    val succMessage = buildSuccessMessage(response.casetaIds, response.casetaId, response.displayMessage)

                    val casetaIds = (response.casetaIds + response.casetaId)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .distinct()
                    val queuedPhotos = enqueueCasetaPhotos(
                        casetaIds = casetaIds,
                        paths = state.form.localPhotoPaths,
                        firstPhotoIndex = state.form.remotePhotos.mapNotNull { it.sourceIndex }.maxOrNull()?.plus(1) ?: 1,
                        isUpdate = state.form.updated
                    )

                    // El folio ya quedo guardado. Los archivos se borran solo
                    // despues de que el worker confirme cada foto; asi una falla
                    // de red o un cierre de la app no pierde evidencia.
                    draftDao.deleteById(draftId)
                    draftId = UUID.randomUUID().toString()
                    draftCreatedAt = System.currentTimeMillis()
                    draftDiscarded = false
                    baselineForm = CasetaEntradaForm()

                    _uiState.update { st ->
                        st.copy(
                            isSaving = false,
                            form = CasetaEntradaForm(),
                            fieldErrors = emptyMap(),
                            capture = CasetaEntradaOcrState.Idle,
                            historySuggestions = emptyList(),
                            historySearch = "",
                            templateSource = "",
                            preregistroFolio = "",
                            preregistroPlacasConfirmadas = null,
                            message = buildString {
                                append(succMessage)
                                if (queuedPhotos > 0) {
                                    append(" Fotos en cola para sincronizar: $queuedPhotos.")
                                }
                            },
                            feedbackSeq = st.feedbackSeq + 1,
                            savedSeq = st.savedSeq + 1,
                            savedCasetaId = response.casetaId,
                            savedCaja = request.numCaja,
                            lastDraftSavedAt = null,
                            savingProgress = st.savingProgress.copy(
                                percent = 100,
                                isFinished = true,
                                currentTitle = "¡Entrada guardada correctamente!",
                                steps = st.savingProgress.steps.map { s ->
                                    s.copy(status = SavingStepStatus.SUCCESS)
                                }
                            )
                        )
                    }

                    // Auto-cerrar overlay tras 1.6s
                    delay(1600)
                    _uiState.update { st ->
                        if (st.savingProgress.isFinished) {
                            st.copy(savingProgress = SavingProgressState(isVisible = false))
                        } else st
                    }
                }
                .onFailure { error ->
                    val errMsg = error.message ?: "No se pudo guardar la entrada."
                    _uiState.update { st ->
                        st.copy(
                            isSaving = false,
                            message = errMsg,
                            feedbackSeq = st.feedbackSeq + 1,
                            savingProgress = st.savingProgress.copy(
                                hasError = true,
                                errorMessage = errMsg,
                                currentTitle = "Error al guardar",
                                steps = st.savingProgress.steps.map { s ->
                                    if (s.id == "save") s.copy(status = SavingStepStatus.ERROR) else s
                                }
                            )
                        )
                    }
                }
        }
    }

    fun resetForm() {
        if (!canAdd() && _uiState.value.form.casetaId.isBlank()) {
            denyPermission("limpiar una captura de caseta")
            return
        }
        // El usuario decidio empezar de cero: el borrador viejo ya no aplica. Sus fotos locales tampoco: el aviso de
        // «Limpiar» ya dice que se quitan, y sin borrarlas quedaban en el disco para siempre sin dueño. Va en
        // `applicationScope` para que termine aunque la pantalla se cierre enseguida.
        val abandoned = draftId
        val abandonedPhotos = _uiState.value.form.localPhotoPaths
        applicationScope.launch {
            withContext(Dispatchers.IO) {
                abandonedPhotos.forEach { path -> runCatching { File(path).delete() } }
            }
            draftDao.deleteById(abandoned)
        }
        draftId = UUID.randomUUID().toString()
        draftCreatedAt = System.currentTimeMillis()
        draftDiscarded = false
        baselineForm = CasetaEntradaForm()
        _uiState.update {
            it.copy(
                form = CasetaEntradaForm(),
                fieldErrors = emptyMap(),
                capture = CasetaEntradaOcrState.Idle,
                historySuggestions = emptyList(),
                isLoadingHistory = false,
                historySearch = "",
                templateSource = "",
                showConfirm = false,
                showImportQuestion = false,
                recordLoadError = null,
                recordLoadingHint = "",
                lastDraftSavedAt = null
            )
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun renderLabelPreview(casetaId: String, caja: String): Bitmap {
        val labelData = com.example.pmmovil.data.printing.ZebraLabelData(
            qrValue = casetaId,
            numeroEntrada = caja,
            fechaArribo = "",
            cliente = ""
        )
        val layout = zebraLabelLayoutPreferences.getLayout(
            com.example.pmmovil.data.printing.ZebraLabelType.CASETA
        )
        return labelBitmapRenderer.renderReadingOrientation(labelData, layout)
    }

    fun printCasetaLabel(casetaId: String, caja: String, copies: Int = 1) {
        viewModelScope.launch {
            val config = zebraPrinterPreferences.getConfig(
                com.example.pmmovil.data.printing.ZebraLabelType.CASETA
            )
            if (!config.isHostConfigured) {
                _uiState.update { it.copy(message = "Impresora no configurada", feedbackSeq = it.feedbackSeq + 1) }
                return@launch
            }
            try {
                val labelData = com.example.pmmovil.data.printing.ZebraLabelData(
                    qrValue = casetaId,
                    numeroEntrada = caja, // Usamos esto para la referencia (nro de caja)
                    fechaArribo = "",
                    cliente = "",
                    copies = copies
                )
                val layout = zebraLabelLayoutPreferences.getLayout(
                    com.example.pmmovil.data.printing.ZebraLabelType.CASETA
                )
                val zpl = labelZplFactory.buildLabels(listOf(labelData), compress = true, layoutOverride = layout)
                val result = zebraPrinterService.printZpl(config, zpl, copies)
                _uiState.update {
                    it.copy(
                        message = result.userMessage(),
                        feedbackSeq = it.feedbackSeq + 1
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Error de impresión: ${e.message}", feedbackSeq = it.feedbackSeq + 1) }
            }
        }
    }

    private suspend fun loadRemotePhotos(
        casetaId: String,
        photosBase64: List<String>,
        photoInfos: List<CasetaEntradaFotoInfo>
    ): List<CasetaEntradaRemotePhoto> {
        // Decodificamos a Bitmap (con limite de tamano) para poder mostrarlas: Coil no sabe
        // renderizar un modelo String "data:image/...;base64,..." al no traer un Fetcher para
        // ese esquema, y las fotos llegan sin redimensionar desde el servidor.
        val directPhotos = withContext(Dispatchers.Default) {
            photosBase64
                .mapIndexedNotNull { index, value ->
                    normalizeRemotePhotoBase64(value)?.let { normalized ->
                        CasetaEntradaRemotePhoto(
                            key = "remote-base64-$index",
                            label = "Guardada ${index + 1}",
                            model = decodeDataUrlToBitmap(normalized) ?: normalized,
                            base64 = normalized,
                            sourceIndex = photoInfos.getOrNull(index)?.indice
                        )
                    }
                }
                .take(MAX_REMOTE_PHOTOS)
        }

        if (directPhotos.isNotEmpty() || casetaId.isBlank() || photoInfos.isEmpty()) {
            return directPhotos
        }

        return coroutineScope {
            photoInfos.take(MAX_REMOTE_PHOTOS).mapIndexed { index, info ->
                async {
                    val bytes = repository.downloadGatePhoto(casetaId, info.indice).getOrNull()
                        ?: return@async null
                    withContext(Dispatchers.Default) {
                        val dataUrl = photoBytesToDataUrl(bytes)
                        CasetaEntradaRemotePhoto(
                            key = "remote-${info.indice}-$index",
                            label = info.nombreArchivo.ifBlank { "Guardada ${info.indice}" },
                            model = decodeRemotePhotoBitmap(bytes) ?: dataUrl,
                            base64 = dataUrl,
                            sourceIndex = info.indice
                        )
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun runSmartCapture(
        mode: CasetaEntradaCaptureMode,
        sourceId: String,
        localSource: suspend () -> Result<LocalTextOcrResult>,
        serverSource: suspend () -> String?
    ) {
        captureJob?.cancel()
        captureJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    capture = CasetaEntradaOcrState(
                        isLoading = true,
                        sourceId = sourceId,
                        mode = mode,
                        phase = CasetaEntradaOcrPhase.Captured,
                        outcome = CasetaEntradaOcrOutcome.Pending,
                        message = "Foto recibida."
                    )
                )
            }
            delay(180)
            _uiState.update {
                it.copy(
                    capture = it.capture.copy(
                        phase = CasetaEntradaOcrPhase.PreparingImage,
                        message = "Mejorando contraste y aplicando rotaciones."
                    )
                )
            }
            delay(260)

            val localResult = localSource().getOrNull()

            val localCandidates = if (mode == CasetaEntradaCaptureMode.Box) {
                buildBoxCandidates(localResult)
            } else {
                buildPlateCandidates(localResult)
            }

            _uiState.update {
                it.copy(
                    capture = it.capture.copy(
                        phase = CasetaEntradaOcrPhase.ReadingLocal,
                        message = if (localCandidates.isNotEmpty()) {
                            "Leimos ${localCandidates.size} candidatos visibles."
                        } else {
                            "No se detectaron coincidencias claras en el dispositivo."
                        }
                    )
                )
            }
            delay(280)
            _uiState.update {
                it.copy(
                    capture = it.capture.copy(
                        phase = CasetaEntradaOcrPhase.ValidatingCandidates,
                        message = "Descartando marcas, modelos y ruido."
                    )
                )
            }
            delay(180)

            val topLocal = localCandidates.firstOrNull()
            if (topLocal != null && (topLocal.source == "barcode" || (topLocal.confidence ?: 0.0) >= LOCAL_FAST_PATH_THRESHOLD)) {
                finalizeAutoApplied(
                    mode = mode,
                    sourceId = sourceId,
                    primary = topLocal,
                    candidates = localCandidates,
                    origin = topLocal.source.ifBlank { "local" }
                )
                return@launch
            }

            val base64 = serverSource()
            if (base64.isNullOrBlank()) {
                finalizeManual(
                    mode = mode,
                    sourceId = sourceId,
                    primary = localCandidates.firstOrNull(),
                    candidates = localCandidates,
                    message = "No se pudo preparar la imagen para el servidor."
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    capture = it.capture.copy(
                        phase = CasetaEntradaOcrPhase.ValidatingServer,
                        message = "Pidiendo refuerzos al servidor de OCR."
                    )
                )
            }

            val serverCandidates: List<CasetaEntradaOcrCandidate> = if (mode == CasetaEntradaCaptureMode.Box) {
                repository.readBox(base64).fold(
                    onSuccess = { response ->
                        buildBoxCandidatesFromServer(response.candidates, response.numCaja, response.confidence)
                    },
                    onFailure = { emptyList() }
                )
            } else {
                repository.readPlate(base64).fold(
                    onSuccess = { response ->
                        buildPlateCandidatesFromServer(response.candidates, response.placa, response.confidence)
                    },
                    onFailure = { emptyList() }
                )
            }

            val merged = mergeCandidates(
                primary = localCandidates,
                secondary = serverCandidates
            )

            val highMerged = merged.firstOrNull { (it.confidence ?: 0.0) >= HIGH_CONFIDENCE }
            if (highMerged != null) {
                finalizeAutoApplied(
                    mode = mode,
                    sourceId = sourceId,
                    primary = highMerged,
                    candidates = merged,
                    origin = if (highMerged.source.isNotBlank()) highMerged.source else "server"
                )
                return@launch
            }

            _uiState.update {
                it.copy(
                    capture = it.capture.copy(
                        phase = CasetaEntradaOcrPhase.SearchingHistory,
                        message = "Buscando coincidencias en registros previos."
                    )
                )
            }
            delay(260)

            val historyResult = repository.searchRecords(
                term = merged.firstOrNull()?.value.orEmpty(),
                type = if (mode == CasetaEntradaCaptureMode.Box) "box" else "plate",
                limit = 5
            )
            val historyMatches = historyResult.getOrDefault(emptyList())

            if (merged.isEmpty()) {
                finalizeManual(
                    mode = mode,
                    sourceId = sourceId,
                    primary = null,
                    candidates = emptyList(),
                    message = "No encontre un numero confiable. Intenta acercarte al costado o parte trasera."
                )
                return@launch
            }

            val boostedCandidates = boostCandidatesWithHistory(merged, historyMatches)
            val finalCandidates = boostedCandidates.take(MAX_SUGGESTIONS)
            val top = finalCandidates.firstOrNull()
            if (top == null) {
                finalizeManual(
                    mode = mode,
                    sourceId = sourceId,
                    primary = null,
                    candidates = finalCandidates,
                    message = "No encontre coincidencias claras."
                )
                return@launch
            }

            if ((top.confidence ?: 0.0) >= HIGH_CONFIDENCE) {
                finalizeAutoApplied(
                    mode = mode,
                    sourceId = sourceId,
                    primary = top,
                    candidates = finalCandidates,
                    origin = top.source.ifBlank { "history" }
                )
            } else {
                _uiState.update {
                    it.copy(
                        capture = it.capture.copy(
                            isLoading = false,
                            primaryValue = top.value,
                            confidence = top.confidence,
                            candidates = finalCandidates,
                            phase = CasetaEntradaOcrPhase.Ready,
                            outcome = CasetaEntradaOcrOutcome.NeedsChoice,
                            source = top.source,
                            message = if (mode == CasetaEntradaCaptureMode.Box) {
                                "Tengo varias opciones de caja. Elige la correcta."
                            } else {
                                "Tengo varias opciones de placa. Elige la correcta."
                            }
                        )
                    )
                }
            }
        }
    }

    private fun finalizeAutoApplied(
        mode: CasetaEntradaCaptureMode,
        sourceId: String,
        primary: CasetaEntradaOcrCandidate,
        candidates: List<CasetaEntradaOcrCandidate>,
        origin: String
    ) {
        val clean = primary.value.uppercase()
        val historyType = if (mode == CasetaEntradaCaptureMode.Box) {
            CasetaEntradaHistorySearchType.Box
        } else {
            CasetaEntradaHistorySearchType.Plate
        }
        _uiState.update { state ->
            val updatedForm = when (mode) {
                CasetaEntradaCaptureMode.Box -> state.form.copy(boxNumber = clean)
                CasetaEntradaCaptureMode.Plate -> state.form.copy(plates = clean)
            }
            state.copy(
                form = updatedForm,
                historySearch = clean,
                fieldErrors = state.fieldErrors - when (mode) {
                    CasetaEntradaCaptureMode.Box -> "boxNumber"
                    CasetaEntradaCaptureMode.Plate -> "plates"
                },
                capture = state.capture.copy(
                    isLoading = false,
                    primaryValue = clean,
                    confidence = primary.confidence,
                    candidates = candidates.take(MAX_SUGGESTIONS),
                    phase = CasetaEntradaOcrPhase.Ready,
                    outcome = CasetaEntradaOcrOutcome.AutoApplied,
                    source = origin,
                    message = if (mode == CasetaEntradaCaptureMode.Box) {
                        "Caja encontrada."
                    } else {
                        "Placa encontrada."
                    }
                )
            )
        }
        queueHistorySuggestions(clean, debounce = false, showEmptyMessage = false, searchType = historyType)
    }

    private fun finalizeManual(
        mode: CasetaEntradaCaptureMode,
        sourceId: String,
        primary: CasetaEntradaOcrCandidate?,
        candidates: List<CasetaEntradaOcrCandidate>,
        message: String
    ) {
        _uiState.update {
            it.copy(
                capture = it.capture.copy(
                    isLoading = false,
                    primaryValue = primary?.value.orEmpty(),
                    confidence = primary?.confidence,
                    candidates = candidates.take(MAX_SUGGESTIONS),
                    phase = CasetaEntradaOcrPhase.Failed,
                    outcome = if (candidates.isEmpty()) {
                        CasetaEntradaOcrOutcome.ManualRequired
                    } else {
                        CasetaEntradaOcrOutcome.NeedsChoice
                    },
                    source = primary?.source.orEmpty(),
                    message = message
                )
            )
        }
    }

    private fun queueHistorySuggestions(
        term: String,
        debounce: Boolean,
        showEmptyMessage: Boolean,
        searchType: CasetaEntradaHistorySearchType = CasetaEntradaHistorySearchType.General
    ) {
        historyJob?.cancel()
        val clean = term.trim().uppercase()
        if (clean.length < 3) {
            _uiState.update {
                it.copy(
                    isLoadingHistory = false,
                    historySuggestions = if (clean.isBlank()) emptyList() else it.historySuggestions
                )
            }
            return
        }

        _uiState.update { it.copy(isLoadingHistory = true, historySearch = clean) }
        historyJob = viewModelScope.launch {
            if (debounce) delay(350)
            repository.searchRecords(clean, type = searchType.apiValue, limit = 8)
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            historySuggestions = items,
                            isLoadingHistory = false,
                            message = if (showEmptyMessage && items.isEmpty()) {
                                "Sin coincidencias historicas para $clean."
                            } else {
                                it.message
                            },
                            feedbackSeq = if (showEmptyMessage && items.isEmpty()) {
                                it.feedbackSeq + 1
                            } else {
                                it.feedbackSeq
                            }
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingHistory = false,
                            message = error.message ?: "No se pudo consultar el historico.",
                            feedbackSeq = it.feedbackSeq + 1
                        )
                    }
                }
        }
    }

    private fun buildBoxCandidates(result: LocalTextOcrResult?): List<CasetaEntradaOcrCandidate> {
        return BoxOcrRanker.rank(result).take(MAX_SUGGESTIONS)
    }

    private fun buildPlateCandidates(result: LocalTextOcrResult?): List<CasetaEntradaOcrCandidate> {
        return PlateOcrRanker.rank(result).take(MAX_SUGGESTIONS)
    }

    private fun buildBoxCandidatesFromServer(
        serverCandidates: List<CasetaEntradaOcrCandidate>,
        fallback: String,
        confidence: Double?
    ): List<CasetaEntradaOcrCandidate> {
        val fromList = serverCandidates.mapNotNull { candidate ->
            val normalized = CasetaEntradaRules.normalizeBoxCandidate(candidate.value)
            if (normalized.isBlank()) return@mapNotNull null
            CasetaEntradaOcrCandidate(
                value = normalized,
                confidence = candidate.confidence ?: confidence,
                source = candidate.source.ifBlank { "server" },
                reason = candidate.reason ?: "Gemini"
            )
        }
        if (fromList.isNotEmpty()) return fromList
        val normalized = CasetaEntradaRules.normalizeBoxCandidate(fallback)
        if (normalized.isBlank()) return emptyList()
        return listOf(
            CasetaEntradaOcrCandidate(
                value = normalized,
                confidence = confidence,
                source = "server",
                reason = "Gemini"
            )
        )
    }

    private fun buildPlateCandidatesFromServer(
        serverCandidates: List<CasetaEntradaOcrCandidate>,
        fallback: String,
        confidence: Double?
    ): List<CasetaEntradaOcrCandidate> {
        val fromList = serverCandidates.mapNotNull { candidate ->
            val normalized = bestPlateCandidateFromText(candidate.value)
            if (normalized.isBlank()) return@mapNotNull null
            CasetaEntradaOcrCandidate(
                value = normalized,
                confidence = candidate.confidence ?: confidence,
                source = candidate.source.ifBlank { "server" },
                reason = candidate.reason ?: "Gemini"
            )
        }
        if (fromList.isNotEmpty()) return fromList
        val normalized = bestPlateCandidateFromText(fallback)
        if (normalized.isBlank()) return emptyList()
        return listOf(
            CasetaEntradaOcrCandidate(
                value = normalized,
                confidence = confidence,
                source = "server",
                reason = "Gemini"
            )
        )
    }

    private fun mergeCandidates(
        primary: List<CasetaEntradaOcrCandidate>,
        secondary: List<CasetaEntradaOcrCandidate>
    ): List<CasetaEntradaOcrCandidate> {
        if (primary.isEmpty()) return secondary
        if (secondary.isEmpty()) return primary
        val byValue = linkedMapOf<String, CasetaEntradaOcrCandidate>()
        (primary + secondary).forEach { candidate ->
            val key = candidate.value.uppercase()
            val existing = byValue[key]
            byValue[key] = if (existing == null) {
                candidate
            } else {
                val bestConfidence = listOf(existing.confidence, candidate.confidence)
                    .mapNotNull { it }
                    .maxOrNull()
                val source = if (existing.source == candidate.source) existing.source else "${existing.source}+${candidate.source}"
                candidate.copy(
                    confidence = bestConfidence ?: candidate.confidence,
                    source = source
                )
            }
        }
        return byValue.values.sortedByDescending { it.confidence ?: 0.0 }
    }

    private fun boostCandidatesWithHistory(
        candidates: List<CasetaEntradaOcrCandidate>,
        history: List<CasetaEntradaBusquedaItem>
    ): List<CasetaEntradaOcrCandidate> {
        if (candidates.isEmpty() || history.isEmpty()) return candidates
        val historyByBox = history.mapNotNull { it.nequipo.uppercase().takeIf(String::isNotBlank) }
        val historyByPlate = history.mapNotNull { it.placas.uppercase().takeIf(String::isNotBlank) }
        return candidates.map { candidate ->
            val value = candidate.value.uppercase()
            val match = when {
                value in historyByBox || value in historyByPlate -> true
                history.any { item ->
                    item.nequipo.uppercase().contains(value) ||
                        item.placas.uppercase().contains(value) ||
                        value.contains(item.nequipo.uppercase().takeIf(String::isNotBlank).orEmpty()) ||
                        value.contains(item.placas.uppercase().takeIf(String::isNotBlank).orEmpty())
                } -> true
                else -> false
            }
            if (match) {
                candidate.copy(
                    confidence = ((candidate.confidence ?: 0.6) + HISTORY_BOOST).coerceAtMost(1.0),
                    source = if (candidate.source.isBlank()) "history" else "${candidate.source}+history"
                )
            } else {
                candidate
            }
        }.sortedByDescending { it.confidence ?: 0.0 }
    }

    private suspend fun enqueueCasetaPhotos(
        casetaIds: List<String>,
        paths: List<String>,
        firstPhotoIndex: Int,
        isUpdate: Boolean
    ): Int {
        if (casetaIds.isEmpty() || paths.isEmpty()) return 0

        var queued = 0
        casetaIds.forEachIndexed { casetaPosition, casetaId ->
            paths.forEachIndexed { pathPosition, path ->
                casetaPhotoSyncDao.insert(
                    CasetaEntradaPhotoSyncEntity(
                        casetaId = casetaId,
                        localFilePath = path,
                        photoIndex = firstPhotoIndex + pathPosition,
                        // Solo el ultimo folio debe disparar el correo cuando
                        // RequiereImpo genera dos entradas para la misma captura.
                        notifyWhenFinished = casetaPosition == casetaIds.lastIndex &&
                            pathPosition == paths.lastIndex,
                        notifyAsUpdate = isUpdate
                    )
                )
                queued++
            }
        }

        syncScheduler.scheduleCasetaEntradaPhotos()
        return queued
    }

    private fun encodePhotoForOcr(path: String): String? =
        encodePhoto(
            path = path,
            maxWidth = OCR_PHOTO_MAX_SIDE,
            maxHeight = OCR_PHOTO_MAX_SIDE,
            initialQuality = OCR_PHOTO_QUALITY,
            minQuality = OCR_PHOTO_MIN_QUALITY,
            maxBytes = OCR_PHOTO_MAX_BYTES
        )

    private fun encodePhoto(
        path: String,
        maxWidth: Int,
        maxHeight: Int,
        initialQuality: Int,
        minQuality: Int,
        maxBytes: Int
    ): String? {
        val file = File(path)
        if (!file.exists()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val sample = calculateInSampleSize(bounds, maxWidth, maxHeight)
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return null
        val bitmap = applyExifOrientation(decoded, path)
        if (bitmap !== decoded) decoded.recycle()
        val bytes = bitmap.useCompat {
            compressJpegToTarget(
                bitmap = it,
                initialQuality = initialQuality,
                minQuality = minQuality,
                maxBytes = maxBytes
            )
        }
        return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun compressJpegToTarget(
        bitmap: Bitmap,
        initialQuality: Int,
        minQuality: Int,
        maxBytes: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        var quality = initialQuality.coerceIn(minQuality, 100)
        while (true) {
            output.reset()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
            if (output.size() <= maxBytes || quality <= minQuality) {
                return output.toByteArray()
            }
            quality = (quality - 8).coerceAtLeast(minQuality)
        }
    }

    // Ultima red de seguridad: si el ViewModel se destruye (ej. el usuario sale
    // de la pantalla) con cambios mas nuevos que el ultimo debounce, se respaldan
    // de inmediato. Va en `applicationScope` a proposito: aqui `viewModelScope`
    // YA viene cancelado y un launch sobre el no correria nunca.
    override fun onCleared() {
        super.onCleared()
        val form = _uiState.value.form
        val id = draftId
        val createdAt = draftCreatedAt
        // Solo se conserva el borrador si hay algo sin guardar y el usuario no lo descartó a propósito. Si la app se
        // cierra de golpe esto ni siquiera corre, y es justo el borrador que se autoguardó el que se ofrece después.
        if (draftDiscarded || !isUnsaved(form)) return
        applicationScope.launch {
            draftDao.upsert(
                form.toDraftEntity(
                    id = id,
                    createdAt = createdAt,
                    lastEditAt = System.currentTimeMillis()
                )
            )
        }
    }

    companion object {
        private const val HIGH_CONFIDENCE = 0.78
        private const val LOCAL_FAST_PATH_THRESHOLD = 0.90
        private const val HISTORY_BOOST = 0.12
        private const val MAX_SUGGESTIONS = 4
        private const val MAX_REMOTE_PHOTOS = 15
        private const val MAX_DRAFTS = 10
        private const val BOX_TYPES_ATTEMPTS = 3
        private const val BOX_TYPES_RETRY_MS = 1_500L
        // Alineados con [PhotoPipeline]. La foto de OCR conserva su propio
        // juego porque ahi el destinatario es un motor de texto y no una
        // persona, pero ya no baja de 2048 px: la placa suele ocupar una franja
        // pequena del cuadro, y cada pixel que se le quita a la foto se le
        // quita al renglon que hay que leer.
        private const val OCR_PHOTO_MAX_SIDE = PhotoPipeline.LADO_MAX
        private const val OCR_PHOTO_QUALITY = PhotoPipeline.CALIDAD
        private const val OCR_PHOTO_MIN_QUALITY = PhotoPipeline.CALIDAD_MINIMA
        private const val OCR_PHOTO_MAX_BYTES = PhotoPipeline.MAX_BYTES

    }
}

object CasetaEntradaRules {
    const val OP_IMPORT = "IMPORTACION"
    const val OP_EXPORT = "EXPORTACION"

    private val importMovements = listOf(
        "CONSOLIDADO/MENSAJERIA",
        "COLOCACION/VACIO",
        "DIRECTO",
        "DESCARGAS",
        "TRANSBORDO"
    )
    private val exportMovements = listOf(
        "RECOLECCION/PICKUP",
        "TRANSBORDO"
    )

    fun movementsFor(operation: String): List<String> = when (operation) {
        OP_IMPORT -> importMovements
        OP_EXPORT -> exportMovements
        else -> emptyList()
    }

    fun requiresCountry(movement: String): Boolean =
        movement == "COLOCACION/VACIO" || movement == "DIRECTO"

    fun requiresGuide(movement: String): Boolean =
        movement == "CONSOLIDADO/MENSAJERIA"

    fun isRefrigerated(boxType: String): Boolean =
        boxType.equals("CAJA REFRIGERADA 53'", ignoreCase = true)

    /**
     * Registros guardados cuando el combo aun decia «-- Selecciona --» traen ese texto (y el id 0) en lugar de un tipo:
     * al abrirlos para editar se veian como si ya tuvieran uno y la validacion los dejaba pasar sin tipo de caja.
     */
    fun cleanCatalogSelection(value: String): String {
        val text = value.trim()
        return if (text == "0" || text.startsWith("-- Selecciona", ignoreCase = true)) "" else text
    }

    fun normalizePlateCandidate(value: String): String {
        val compact = value.uppercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^A-Z0-9-]"), "")
        return if (Regex("^[A-Z0-9-]{4,15}$").matches(compact)) compact else ""
    }

    fun normalizeBoxCandidate(value: String): String {
        val compact = value.uppercase()
            .replace(Regex("\\s+"), "")
            .replace(Regex("[^A-Z0-9-]"), "")
        return if (Regex("^[A-Z0-9-]{3,20}$").matches(compact)) compact else ""
    }

    fun validate(form: CasetaEntradaForm): Map<String, String> {
        val errors = linkedMapOf<String, String>()

        if (form.operation.isBlank()) {
            errors["operation"] = "Selecciona el tipo de operacion."
        }
        if (form.idBodega.isBlank()) {
            errors["idBodega"] = "Selecciona la bodega."
        }
        // Un tractocamion sin caja (tipoRegistro=TRACTOCAMION, venga de un QR de
        // pre-registro o capturado a mano en garita) no tiene tipo de caja,
        // cliente, linea de caja ni numero de caja: son datos de una carga que
        // ese camion no trae. Exime las cuatro a la vez, igual que el backend
        // (CasetaService.ValidarEntradaCaseta).
        if (!form.esTractocamion) {
            if (form.idBoxType.isBlank() && form.boxTypeDescription.isBlank()) {
                errors["idBoxType"] = "Selecciona el tipo de caja."
            }
            if (form.idCustomer.isBlank() && form.customerName.isBlank()) {
                errors["customer"] = "Selecciona el cliente."
            }
            if (form.idBoxLine.isBlank() && form.boxLineName.isBlank()) {
                errors["boxLine"] = "Selecciona la linea de caja."
            }
            if (form.boxNumber.isBlank()) {
                errors["boxNumber"] = "Captura el numero de caja."
            }
        }
        // Linea de camion, No camion, Operador y Sellos son sugeridos, no obligatorios:
        // la pantalla los resalta en ambar cuando faltan, pero no bloquean el guardado.
        if (form.movement.isBlank()) {
            errors["movement"] = "Selecciona el tipo de movimiento."
        }
        if (requiresCountry(form.movement)) {
            if (form.plates.isBlank()) {
                errors["plates"] = "Captura las placas."
            }
            if (form.idCountry.isBlank()) {
                errors["idCountry"] = "Selecciona el pais de origen."
            }
        }
        if (requiresGuide(form.movement)) {
            val guides = form.guideCount.toIntOrNull()
            if (guides == null || guides < 1) {
                errors["guideCount"] = "Guia debe ser mayor a 0."
            }
        } else if (form.guideCount.isNotBlank() && form.guideCount.toIntOrNull() == null) {
            errors["guideCount"] = "Guia debe ser numerica."
        }
        if (form.pedimento.isNotBlank() && !Regex("^\\d{7}$").matches(form.pedimento.trim())) {
            errors["pedimento"] = "Pedimento debe tener 7 digitos."
        }
        if (isRefrigerated(form.boxTypeDescription.ifBlank { form.boxType })) {
            if (form.temperature.isBlank()) {
                errors["temperature"] = "Captura la temperatura de la caja refrigerada."
            }
            if (form.fuel.isBlank()) {
                errors["fuel"] = "Captura el combustible de la caja refrigerada."
            }
        }
        if (form.photoCount > 15) {
            errors["photos"] = "Maximo 15 fotos por registro."
        }

        return errors
    }
}

private fun CasetaEntradaDetalle.toForm(remotePhotos: List<CasetaEntradaRemotePhoto>): CasetaEntradaForm {
    return CasetaEntradaForm(
        casetaId = casetaId,
        idCaseta = idCaseta.takeIf { it > 0 }?.toString().orEmpty(),
        idBodega = idBodega.ifBlank { "01" },
        bodega = bodega.ifBlank { "Laredo, TX" },
        operation = tipoOp,
        movement = tipoMov,
        tipoRegistro = tipoRegistro.ifBlank { TipoRegistro.CAJA },
        idBoxType = CasetaEntradaRules.cleanCatalogSelection(idUnidad),
        boxType = CasetaEntradaRules.cleanCatalogSelection(idUnidad),
        boxTypeDescription = CasetaEntradaRules.cleanCatalogSelection(tipouni),
        truckNumber = numUnidad,
        idTruckLine = idTransporteTractor,
        truckLineName = transpor,
        boxNumber = nequipo,
        idBoxLine = idTransporteCaja,
        boxLineName = transporte,
        idCustomer = idCliente,
        customerName = nombreCliente,
        operatorName = operador,
        plates = placas,
        seals = sellos,
        idCountry = idPais,
        countryName = pais,
        observations = observaciones,
        temperature = temperatura,
        fuel = combustible,
        pedimento = pedimento,
        guideCount = guias.takeIf { it > 0 }?.toString().orEmpty(),
        updated = true,
        remotePhotos = remotePhotos
    )
}

private fun CasetaEntradaDetalle.toTemplateForm(localPhotoPaths: List<String>): CasetaEntradaForm {
    // Los datos del tractor cambian en cada viaje (otro camion, otro chofer, otros
    // sellos), asi que NO se heredan de la plantilla: se dejan vacios y la pantalla
    // los marca como pendientes. Lo que si se reutiliza es caja, cliente y linea de caja.
    return toForm(emptyList()).copy(
        casetaId = "",
        idCaseta = "",
        updated = false,
        requiresImport = false,
        idTruckLine = "",
        truckLineName = "",
        truckNumber = "",
        operatorName = "",
        seals = "",
        localPhotoPaths = localPhotoPaths,
        remotePhotos = emptyList()
    )
}

private fun CasetaEntradaForm.toRequest(
    photos: List<String>,
    user: String,
    userName: String,
    plant: String,
    diferirCorreo: Boolean = false,
    preregistroFolio: String = "",
    placasNoCoinciden: Boolean = false
): CasetaEntradaGuardarRequest {
    return CasetaEntradaGuardarRequest(
        usuario = user,
        nombreUsuario = userName,
        planta = plant,
        idBodega = idBodega,
        bodega = bodega,
        cliente = customerLabel,
        idCliente = idCustomer,
        nombreCliente = customerName,
        operacion = operation,
        movimiento = movement,
        idTipoCaja = idBoxType,
        tipoCaja = boxType,
        descTipoCaja = boxTypeDescription,
        operador = operatorName,
        noCamion = truckNumber,
        lineaCamion = truckLineLabel,
        idLineaCamion = idTruckLine,
        nombreLineaCamion = truckLineName,
        numCaja = boxNumber,
        lineaCaja = boxLineLabel,
        idLineaCaja = idBoxLine,
        nombreLineaCaja = boxLineName,
        idPais = idCountry,
        pais = countryName,
        placas = plates,
        sellos = seals,
        observaciones = observations,
        combustible = fuel,
        temperaturaOculta = temperature,
        pedimento = pedimento,
        guia = guideCount,
        fotos = photos,
        diferirCorreo = diferirCorreo,
        requiereImpo = requiresImport,
        casetaid = casetaId,
        idcaseta = idCaseta,
        actualizado = updated,
        origenPreRegistro = origenPreRegistro,
        preregistroFolio = preregistroFolio,
        placasNoCoinciden = placasNoCoinciden,
        tipoRegistro = tipoRegistro
    )
}

private fun buildCatalogLabel(id: String, name: String): String {
    return listOf(id, name)
        .filter { it.isNotBlank() }
        .joinToString(" - ")
}

private fun lookupIndex(items: List<CasetaEntradaCatalogItem>) = CatalogIndex(items, { it.id }, { it.nombre })

private fun emptyLookupIndex() = lookupIndex(emptyList())

private fun mergeCatalogItems(
    primary: List<CasetaEntradaCatalogItem>,
    secondary: List<CasetaEntradaCatalogItem>
): List<CasetaEntradaCatalogItem> {
    val seen = linkedSetOf<String>()
    return (primary + secondary).filter { item ->
        val key = "${item.id.trim().uppercase()}|${item.nombre.trim().uppercase()}"
        key !in seen && seen.add(key)
    }
}

private fun bestPlateCandidateFromText(value: String): String =
    PlateOcrRanker.normalizeSingle(value)

private fun normalizeRemotePhotoBase64(value: String): String? {
    val clean = value.trim().trim('"')
    if (clean.isBlank()) return null
    if (clean.startsWith("data:image", ignoreCase = true)) return clean
    if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
        return clean
    }

    val payload = clean.substringAfter("base64,", clean).replace(Regex("\\s+"), "")
    if (payload.isBlank()) return null
    val mime = when {
        payload.startsWith("iVBOR", ignoreCase = true) -> "image/png"
        payload.startsWith("UklGR", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
    }
    return "data:$mime;base64,$payload"
}

private const val REMOTE_PHOTO_MAX_SIDE = 1280

private fun decodeDataUrlToBitmap(dataUrl: String): Bitmap? {
    val payload = dataUrl.substringAfter("base64,", "")
    if (payload.isBlank()) return null
    val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return null
    return decodeRemotePhotoBitmap(bytes)
}

private fun decodeRemotePhotoBitmap(bytes: ByteArray): Bitmap? {
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = calculateInSampleSize(bounds, REMOTE_PHOTO_MAX_SIDE, REMOTE_PHOTO_MAX_SIDE)
            .coerceAtLeast(1)
    }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}

private fun photoBytesToDataUrl(bytes: ByteArray): String {
    val mime = when {
        bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 12 &&
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() -> "image/webp"
        else -> "image/jpeg"
    }
    return "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        var halfHeight = height / 2
        var halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

private inline fun <T> Bitmap.useCompat(block: (Bitmap) -> T): T {
    try {
        return block(this)
    } finally {
        recycle()
    }
}

private fun buildSuccessMessage(ids: List<String>, singleId: String, serverMessage: String): String {
    val cleanIds = ids.filter(String::isNotBlank).ifEmpty { listOf(singleId).filter(String::isNotBlank) }
    return when {
        cleanIds.isNotEmpty() -> "Entrada guardada: ${cleanIds.joinToString(", ")}"
        serverMessage.isNotBlank() -> serverMessage
        else -> "Entrada de caseta guardada correctamente."
    }
}
