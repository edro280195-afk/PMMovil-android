package com.example.pmmovil.ui.printer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.data.model.MobileModuleIds
import com.example.pmmovil.data.model.ZebraPrinterDto
import com.example.pmmovil.data.model.ZebraPrinterSaveRequest
import com.example.pmmovil.data.printing.LabelZplFactory
import com.example.pmmovil.data.printing.ZebraLabelLayoutPreferences
import com.example.pmmovil.data.printing.ZebraLabelType
import com.example.pmmovil.data.printing.ZebraPrinterConfig
import com.example.pmmovil.data.printing.ZebraPrinterLiveSettings
import com.example.pmmovil.data.printing.ZebraPrinterPreferences
import com.example.pmmovil.data.printing.ZebraPrinterPrintSettings
import com.example.pmmovil.data.printing.ZebraPrinterService
import com.example.pmmovil.data.printing.ZebraPrinterSettingsResult
import com.example.pmmovil.data.printing.ZebraPrinterSettingsValidation
import com.example.pmmovil.data.printing.ZebraPrintResult
import com.example.pmmovil.data.printing.ZebraZplBuilder
import com.example.pmmovil.data.repository.AuthRepository
import com.example.pmmovil.data.repository.ZebraPrinterRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PrinterDetailState(
    val printerId: Int? = null,
    val printerName: String = "",
    val host: String = "",
    val port: String = "9100",
    val location: String = "",
    val terminalName: String = "",
    val connectTimeoutMillis: String = "4000",
    val writeTimeoutMillis: String = "8000",
    val isActive: Boolean = true,
    val defaultForBodega: Boolean = false,
    val defaultForCaseta: Boolean = false,
    val isLoading: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val lastSuccess: Boolean? = null,
    // --- Preferencias avanzadas (colapsadas por default; requieren permiso especial) ---
    val advancedExpanded: Boolean = false,
    val liveSettings: ZebraPrinterLiveSettings? = null,
    val printDarkness: String = "",
    val printSpeedIps: String = "",
    val printMethod: String = "",
    val mediaType: String = "",
    val labelTop: String = "",
    val leftPosition: String = "",
    val canModifyPrinterSettings: Boolean = false,
    val isLoadingPermission: Boolean = true
) {
    val isNew: Boolean get() = printerId == null
}

/**
 * Una sola impresora: alta/edición básica, a cuáles tipos de etiqueta se asigna como
 * predeterminada (por dispositivo, no en el servidor — ver [ZebraPrinterPreferences]), pruebas
 * de conexión/impresión, y las preferencias físicas avanzadas (oscuridad, velocidad, offsets)
 * detrás de una sección colapsable que requiere permiso "Modificar" en Sistemas (módulo 160).
 */
@HiltViewModel
class PrinterDetailViewModel @Inject constructor(
    private val repository: ZebraPrinterRepository,
    private val preferences: ZebraPrinterPreferences,
    private val printerService: ZebraPrinterService,
    private val authRepository: AuthRepository,
    private val labelZplFactory: LabelZplFactory,
    private val layoutPreferences: ZebraLabelLayoutPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PrinterDetailState())
    val uiState: StateFlow<PrinterDetailState> = _uiState.asStateFlow()

    init {
        loadModifyPermission()
    }

    /** Se llama desde la pantalla con el id de la ruta; sin id (0 o null) queda en modo "nueva". */
    fun loadPrinter(printerId: Int) {
        if (printerId <= 0) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, message = null, lastSuccess = null) }
            repository.getAllPrinters()
                .onSuccess { printers ->
                    val printer = printers.firstOrNull { it.id == printerId }
                    if (printer == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                message = "No se encontró la impresora.",
                                lastSuccess = false
                            )
                        }
                    } else {
                        applyPrinterToForm(printer)
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            message = error.message ?: "No se pudo cargar la impresora.",
                            lastSuccess = false
                        )
                    }
                }
        }
    }

    private fun applyPrinterToForm(printer: ZebraPrinterDto) {
        val printSettings = printer.toSavedPrintSettings()
        _uiState.update {
            it.copy(
                printerId = printer.id,
                printerName = printer.name,
                host = printer.host,
                port = printer.port.toString(),
                location = printer.location.orEmpty(),
                terminalName = printer.terminalName.orEmpty(),
                connectTimeoutMillis = printer.connectTimeoutMillis.toString(),
                writeTimeoutMillis = printer.writeTimeoutMillis.toString(),
                isActive = printer.isActive,
                defaultForBodega = preferences.isDefault(ZebraLabelType.ENTRADA_BODEGA, printer.id, printer.host, printer.port),
                defaultForCaseta = preferences.isDefault(ZebraLabelType.CASETA, printer.id, printer.host, printer.port),
                printDarkness = printSettings.darkness?.toString().orEmpty(),
                printSpeedIps = printSettings.speedIps?.toString().orEmpty(),
                printMethod = printSettings.printMethod.orEmpty(),
                mediaType = printSettings.mediaType.orEmpty(),
                labelTop = printSettings.labelTop?.toString().orEmpty(),
                leftPosition = printSettings.leftPosition?.toString().orEmpty(),
                isLoading = false,
                message = null,
                lastSuccess = null
            )
        }
    }

    fun updateHost(value: String) {
        _uiState.update { it.copy(host = value, message = null, lastSuccess = null) }
    }

    fun updatePort(value: String) {
        _uiState.update { it.copy(port = value.filter(Char::isDigit), message = null, lastSuccess = null) }
    }

    fun updatePrinterName(value: String) {
        _uiState.update { it.copy(printerName = value, message = null, lastSuccess = null) }
    }

    fun updateLocation(value: String) {
        _uiState.update { it.copy(location = value, message = null, lastSuccess = null) }
    }

    fun updateTerminalName(value: String) {
        _uiState.update { it.copy(terminalName = value, message = null, lastSuccess = null) }
    }

    fun updateConnectTimeout(value: String) {
        _uiState.update { it.copy(connectTimeoutMillis = value.filter(Char::isDigit), message = null, lastSuccess = null) }
    }

    fun updateWriteTimeout(value: String) {
        _uiState.update { it.copy(writeTimeoutMillis = value.filter(Char::isDigit), message = null, lastSuccess = null) }
    }

    fun updateIsActive(value: Boolean) {
        _uiState.update { it.copy(isActive = value, message = null, lastSuccess = null) }
    }

    fun updateDefaultForBodega(value: Boolean) {
        _uiState.update { it.copy(defaultForBodega = value, message = null, lastSuccess = null) }
    }

    fun updateDefaultForCaseta(value: Boolean) {
        _uiState.update { it.copy(defaultForCaseta = value, message = null, lastSuccess = null) }
    }

    fun toggleAdvancedExpanded() {
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }
    }

    fun updatePrintDarkness(value: String) {
        _uiState.update { it.copy(printDarkness = value.decimalInput(), message = null, lastSuccess = null) }
    }

    fun updatePrintSpeed(value: String) {
        _uiState.update { it.copy(printSpeedIps = value.decimalInput(), message = null, lastSuccess = null) }
    }

    fun updatePrintMethod(value: String) {
        _uiState.update { it.copy(printMethod = value, message = null, lastSuccess = null) }
    }

    fun updateMediaType(value: String) {
        _uiState.update { it.copy(mediaType = value, message = null, lastSuccess = null) }
    }

    fun updateLabelTop(value: String) {
        _uiState.update { it.copy(labelTop = value.integerInput(), message = null, lastSuccess = null) }
    }

    fun updateLeftPosition(value: String) {
        _uiState.update { it.copy(leftPosition = value.integerInput(), message = null, lastSuccess = null) }
    }

    fun saveConfig() {
        val request = currentSaveRequest() ?: return
        val wantedBodega = _uiState.value.defaultForBodega
        val wantedCaseta = _uiState.value.defaultForCaseta
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, lastSuccess = null) }
            repository.savePrinter(request)
                .onSuccess { printer ->
                    persistDefaultAssignments(printer, wantedBodega, wantedCaseta)
                    applyPrinterToForm(printer)
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "Impresora ${printer.displayName} guardada.",
                            lastSuccess = true
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = error.message ?: "No se pudo guardar la impresora Zebra.",
                            lastSuccess = false
                        )
                    }
                }
        }
    }

    fun testConnection() {
        val config = currentConfig() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, lastSuccess = null) }
            val result = printerService.testConnection(config)
            _uiState.update {
                it.copy(
                    isBusy = false,
                    message = result.toConnectionMessage(config),
                    lastSuccess = result.isSuccess
                )
            }
        }
    }

    /** Prueba de impresión ligada a un tipo de etiqueta explícito: ya no adivina la plantilla. */
    fun testPrint(type: ZebraLabelType) {
        val config = currentConfig() ?: return
        val testLabel = ZebraZplBuilder.buildTestLabel()
        viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, message = "Enviando prueba de ${type.title} a ${config.host}:${config.port}...", lastSuccess = null)
            }
            val result = if (BuildConfig.USE_IMAGE_LABELS) {
                val resolution = printerService.readPrintheadResolutionDpi(config)
                val targetDpi = resolution.getOrNull()
                if (targetDpi == null) {
                    val errorMessage = resolution.exceptionOrNull()?.message
                        ?: "No se pudo leer la resolución de la Zebra."
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "${type.title}: No se imprimió. $errorMessage",
                            lastSuccess = false
                        )
                    }
                    return@launch
                }
                val zpl = try {
                    labelZplFactory.buildLabels(
                        listOf(testLabel),
                        layoutOverride = layoutPreferences.getLayout(type),
                        targetDpi = targetDpi
                    )
                } catch (error: com.example.pmmovil.data.printing.LabelQrUnavailableException) {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            message = "${type.title}: No se imprimió. ${error.message}",
                            lastSuccess = false
                        )
                    }
                    return@launch
                }
                printerService.printZpl(config, zpl, labelCount = 1)
            } else {
                printerService.printLabels(config, listOf(testLabel))
            }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    message = "${type.title}: ${result.userMessage()}",
                    lastSuccess = result.isSuccess
                )
            }
        }
    }

    fun refreshPrintSettings() {
        val config = currentConfig() ?: return
        readPrintSettings(config)
    }

    fun applyPrintSettings() {
        val config = currentConfig() ?: return
        val state = _uiState.value
        if (!state.canModifyPrinterSettings) {
            _uiState.update {
                it.copy(message = "Tu usuario no tiene permiso Modificar en Sistemas (módulo 160).", lastSuccess = false)
            }
            return
        }
        if (state.printerId == null) {
            _uiState.update {
                it.copy(message = "Guarda primero la impresora y despues aplica sus preferencias.", lastSuccess = false)
            }
            return
        }
        if (state.liveSettings == null) {
            _uiState.update {
                it.copy(message = "Primero usa \"Leer de impresora\". No aplicamos valores sin conocer los actuales.", lastSuccess = false)
            }
            return
        }

        val settings = currentPrintSettings() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true, message = null, lastSuccess = null) }
            when (val result = printerService.applyPrintSettings(config, settings)) {
                is ZebraPrinterSettingsResult.Failure -> {
                    _uiState.update { it.copy(isBusy = false, message = result.message, lastSuccess = false) }
                }

                is ZebraPrinterSettingsResult.Success -> {
                    _uiState.update { it.copy(liveSettings = result.settings).withLiveSettingsFields(result.settings) }
                    val request = currentSaveRequest()
                    if (request == null) {
                        _uiState.update { it.copy(isBusy = false) }
                        return@launch
                    }
                    val wantedBodega = _uiState.value.defaultForBodega
                    val wantedCaseta = _uiState.value.defaultForCaseta
                    repository.savePrinter(request)
                        .onSuccess { printer ->
                            persistDefaultAssignments(printer, wantedBodega, wantedCaseta)
                            _uiState.update {
                                it.copy(
                                    printerId = printer.id,
                                    isBusy = false,
                                    message = "Preferencias aplicadas en ${printer.displayName} y guardadas.",
                                    lastSuccess = true
                                )
                            }
                        }
                        .onFailure { error ->
                            _uiState.update {
                                it.copy(
                                    isBusy = false,
                                    message = "La Zebra ya se actualizo, pero no se pudo guardar el perfil: " +
                                        (error.message ?: "error desconocido."),
                                    lastSuccess = false
                                )
                            }
                        }
                }
            }
        }
    }

    private fun persistDefaultAssignments(printer: ZebraPrinterDto, wantedBodega: Boolean, wantedCaseta: Boolean) {
        val config = printer.toConfig()
        applyDefaultAssignment(ZebraLabelType.ENTRADA_BODEGA, wantedBodega, printer, config)
        applyDefaultAssignment(ZebraLabelType.CASETA, wantedCaseta, printer, config)
    }

    private fun applyDefaultAssignment(type: ZebraLabelType, wanted: Boolean, printer: ZebraPrinterDto, config: ZebraPrinterConfig) {
        val wasDefault = preferences.isDefault(type, printer.id, printer.host, printer.port)
        when {
            wanted -> preferences.saveDefaultConfig(type, config)
            wasDefault -> preferences.clearDefaultConfig(type)
        }
    }

    private fun currentConfig(): ZebraPrinterConfig? {
        val state = _uiState.value
        val host = state.host.trim()
        if (host.isBlank()) {
            _uiState.update { it.copy(message = "Captura la IP de la impresora Zebra.", lastSuccess = false) }
            return null
        }

        val port = state.port.trim().toIntOrNull()
        if (port == null || port !in 1..65_535) {
            _uiState.update { it.copy(message = "El puerto debe estar entre 1 y 65535.", lastSuccess = false) }
            return null
        }

        return ZebraPrinterConfig(
            printerId = state.printerId,
            host = host,
            port = port,
            printerName = state.printerName.trim().ifBlank { "Zebra ZT411" }
                .ifBlank { state.terminalName.trim() },
            connectTimeoutMillis = state.connectTimeoutMillis.toIntOrNull()?.coerceIn(1000, 30000) ?: 4000,
            writeTimeoutMillis = state.writeTimeoutMillis.toIntOrNull()?.coerceIn(1000, 60000) ?: 8000
        )
    }

    private fun currentSaveRequest(): ZebraPrinterSaveRequest? {
        val state = _uiState.value
        val config = currentConfig() ?: return null
        val name = state.printerName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(message = "Captura el nombre de la impresora.", lastSuccess = false) }
            return null
        }

        val printSettings = currentStoredPrintSettings()
        return ZebraPrinterSaveRequest(
            id = state.printerId ?: 0,
            name = name,
            host = config.host,
            port = config.port,
            location = state.location.trim().ifBlank { null },
            terminalName = state.terminalName.trim().ifBlank { null },
            connectTimeoutMillis = config.connectTimeoutMillis,
            writeTimeoutMillis = config.writeTimeoutMillis,
            printDarkness = printSettings.darkness,
            printSpeedIps = printSettings.speedIps,
            printMethod = ZebraPrinterSettingsValidation.normalizePrintMethod(printSettings.printMethod),
            mediaType = ZebraPrinterSettingsValidation.normalizeMediaType(printSettings.mediaType),
            labelTop = printSettings.labelTop,
            leftPosition = printSettings.leftPosition,
            isActive = state.isActive
        )
    }

    private fun currentPrintSettings(): ZebraPrinterPrintSettings? {
        val settings = currentStoredPrintSettings()
        val validation = ZebraPrinterSettingsValidation.validate(settings)
        if (validation != null) {
            _uiState.update { it.copy(message = validation, lastSuccess = false) }
            return null
        }
        return settings
    }

    private fun currentStoredPrintSettings(): ZebraPrinterPrintSettings {
        val state = _uiState.value
        return ZebraPrinterPrintSettings(
            darkness = state.printDarkness.toFloatOrNull(),
            speedIps = state.printSpeedIps.toFloatOrNull(),
            printMethod = state.printMethod.trim().ifBlank { null },
            mediaType = state.mediaType.trim().ifBlank { null },
            labelTop = state.labelTop.toIntOrNull(),
            leftPosition = state.leftPosition.toIntOrNull()
        )
    }

    private fun ZebraPrintResult.toConnectionMessage(config: ZebraPrinterConfig): String {
        return when (this) {
            is ZebraPrintResult.Success ->
                "Conexion correcta con ${config.printerName} (${stats.host}:${stats.port}) en ${stats.elapsedMillis} ms."
            else -> userMessage()
        }
    }

    private fun loadModifyPermission() {
        viewModelScope.launch {
            authRepository.getPermissions()
                .onSuccess { permissions ->
                    val canModify = permissions.any { permission ->
                        permission.accesoTotal ||
                            (permission.moduloId == MobileModuleIds.SYSTEMS_MONITOR && permission.modificar)
                    }
                    _uiState.update { it.copy(canModifyPrinterSettings = canModify, isLoadingPermission = false) }
                }
                .onFailure {
                    _uiState.update { it.copy(canModifyPrinterSettings = false, isLoadingPermission = false) }
                }
        }
    }

    private fun readPrintSettings(config: ZebraPrinterConfig) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isBusy = true, message = "Leyendo preferencias actuales de ${config.printerName}...", lastSuccess = null)
            }

            when (val result = printerService.readPrintSettings(config)) {
                is ZebraPrinterSettingsResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            liveSettings = null,
                            printDarkness = "",
                            printSpeedIps = "",
                            printMethod = "",
                            mediaType = "",
                            labelTop = "",
                            leftPosition = "",
                            message = result.message,
                            lastSuccess = false
                        )
                    }
                }

                is ZebraPrinterSettingsResult.Success -> {
                    val unavailable = result.settings.unavailableSettings
                    _uiState.update {
                        it.copy(liveSettings = result.settings)
                            .withLiveSettingsFields(result.settings)
                            .copy(
                                isBusy = false,
                                message = if (unavailable.isEmpty()) {
                                    "Preferencias leidas directamente de la Zebra. No se aplico ningun cambio."
                                } else {
                                    "Preferencias leidas. No disponibles en esta Zebra: ${unavailable.joinToString()}."
                                },
                                lastSuccess = true
                            )
                    }
                }
            }
        }
    }

    private fun PrinterDetailState.withLiveSettingsFields(liveSettings: ZebraPrinterLiveSettings): PrinterDetailState {
        val printSettings = liveSettings.printSettings
        return copy(
            printDarkness = printSettings.darkness?.toString().orEmpty(),
            printSpeedIps = printSettings.speedIps?.toString().orEmpty(),
            printMethod = printSettings.printMethod.orEmpty(),
            mediaType = printSettings.mediaType.orEmpty(),
            labelTop = printSettings.labelTop?.toString().orEmpty(),
            leftPosition = printSettings.leftPosition?.toString().orEmpty()
        )
    }

    private fun String.decimalInput(): String = filter { it.isDigit() || it == '.' }

    private fun String.integerInput(): String = filter { it.isDigit() || it == '-' }
}
