package com.example.pmmovil.ui.revision

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.model.ConfirmLabelCaptureRequest
import com.example.pmmovil.data.model.LabelCaptureDraftEntity
import com.example.pmmovil.data.model.LabelCaptureFields
import com.example.pmmovil.data.model.LabelCaptureHazmat
import com.example.pmmovil.data.model.LabelCaptureObservation
import com.example.pmmovil.data.model.LabelCaptureResponse
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.data.model.PaisDto
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.CountryCatalogMatcher
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LocalTextOcr
import com.example.pmmovil.data.ocr.LocalTextOcrResult
import com.example.pmmovil.data.ocr.PaddleOcrEngine
import com.example.pmmovil.data.ocr.PartidaMatcher
import com.example.pmmovil.data.ocr.PhotoBarcodeScanner
import com.example.pmmovil.data.repository.LabelCaptureRepository
import com.example.pmmovil.di.ApplicationScope
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Conserva una captura hasta que el servidor confirma datos y evidencia juntos. */
@HiltViewModel
class LabelCaptureViewModel @Inject constructor(
    private val paddleOcrEngine: PaddleOcrEngine,
    private val localTextOcr: LocalTextOcr,
    private val photoBarcodeScanner: PhotoBarcodeScanner,
    private val repository: LabelCaptureRepository,
    @ApplicationScope private val applicationScope: CoroutineScope
) : ViewModel() {
    enum class Destino { PARTIDA, HAZMAT, NINGUNO }

    data class CampoPropuesto(
        val leido: LabelFieldExtractor.CampoLeido,
        val escribible: Boolean,
        val aceptado: Boolean,
        val destino: Destino = Destino.NINGUNO,
        val valorAEscribir: String = leido.valor,
        val detalle: String? = null
    )

    data class Estado(
        val analizando: Boolean = false,
        val refinando: Boolean = false,
        val fotoPath: String? = null,
        val campos: List<CampoPropuesto> = emptyList(),
        val textoCrudo: String = "",
        val error: String? = null,
        val captureId: String? = null,
        val rawCodes: List<String> = emptyList(),
        val observations: List<LabelFieldExtractor.CampoLeido> = emptyList(),
        val conflicts: List<LabelFieldExtractor.FieldConflict> = emptyList(),
        val resolvedConflicts: Set<LabelFieldExtractor.Campo> = emptySet(),
        val availablePartidas: List<PartidaRevisionDto> = emptyList(),
        val candidates: List<PartidaMatcher.Candidate> = emptyList(),
        val selectedPartida: PartidaRevisionDto? = null,
        val snapshot: LabelCaptureSnapshot? = null,
        val loadingContext: Boolean = false,
        val canEdit: Boolean = false,
        val canMarkReviewed: Boolean = false,
        val identityAccepted: Boolean = false,
        val physicalReviewConfirmed: Boolean = false,
        val syncStatus: String = LabelCaptureStatus.DRAFT,
        val syncMessage: String? = null,
        val readingMillis: Long? = null
    ) {
        val huboLectura: Boolean get() = campos.isNotEmpty()
        val escribibles: List<CampoPropuesto> get() = campos.filter { it.escribible }
        val aceptados: List<CampoPropuesto> get() = campos.filter { it.escribible && it.aceptado }
        val unresolvedConflicts: List<LabelFieldExtractor.FieldConflict>
            get() = conflicts.filter { it.field !in resolvedConflicts }
        val canSave: Boolean get() = !analizando && !refinando && !loadingContext && canEdit &&
            selectedPartida != null && snapshot != null && identityAccepted &&
            syncStatus == LabelCaptureStatus.DRAFT && captureId != null
        val canComplete: Boolean get() = canSave && canMarkReviewed && physicalReviewConfirmed && unresolvedConflicts.isEmpty()
    }

    private val gson = Gson()
    private val _estado = MutableStateFlow(Estado())
    val estado = _estado.asStateFlow()
    private val _drafts = MutableStateFlow<List<LabelCaptureDraftEntity>>(emptyList())
    val drafts = _drafts.asStateFlow()
    private var reference = ""
    private var countries: List<PaisDto> = emptyList()
    private var partidas: List<PartidaRevisionDto> = emptyList()
    private var analysisJob: Job? = null
    private var contextJob: Job? = null
    private var observationJob: Job? = null
    private var persistenceJob: Job? = null
    private var generation = 0L

    fun configure(crefAgrupada: String, available: List<PartidaRevisionDto>, catalog: List<PaisDto>) {
        partidas = available
        countries = catalog
        _estado.value = _estado.value.copy(availablePartidas = available)
        if (reference == crefAgrupada) return
        reference = crefAgrupada
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            repository.observe(crefAgrupada).collect { records ->
                _drafts.value = records
                val active = records.firstOrNull { it.captureId == _estado.value.captureId } ?: return@collect
                val response = active.responseJson?.let { gson.fromJson(it, LabelCaptureResponse::class.java) }
                _estado.value = _estado.value.copy(
                    syncStatus = active.status,
                    syncMessage = active.errorMessage ?: response?.message,
                    snapshot = if (active.status == LabelCaptureStatus.CONFIRMED) response?.snapshot else _estado.value.snapshot
                )
            }
        }
    }

    /** Los códigos del preview solo guían. La evidencia se extrae de la foto actual. */
    fun analizar(path: String, codigosEnVivo: List<String> = emptyList(), catalogoPaises: List<PaisDto> = countries,
                 initialPartidaId: Int? = null) {
        countries = catalogoPaises
        cancelReading()
        val token = generation
        _estado.value = Estado(analizando = true, fotoPath = path, availablePartidas = partidas)
        analysisJob = viewModelScope.launch {
            try {
                check(reference.isNotBlank()) { "No se identificó la referencia de revisión." }
                val draft = repository.createDraft(reference, path, gson.toJson(_estado.value))
                ensureActive()
                if (token != generation) return@launch
                _estado.value = _estado.value.copy(captureId = draft.captureId, fotoPath = draft.photoPath)
                // Se retira únicamente la copia temporal creada por esta pantalla.
                val source = File(path)
                if (source.parentFile?.name == "revision_label_capture") source.delete()
                readPhoto(draft.photoPath, token, improve = false, initialPartidaId = initialPartidaId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (token == generation) update(_estado.value.copy(analizando = false, error = error.message ?: "No se pudo leer la etiqueta."))
            }
        }
    }

    private suspend fun readPhoto(path: String, token: Long, improve: Boolean, initialPartidaId: Int? = null) {
        val previous = _estado.value
        val started = SystemClock.elapsedRealtime()
        val (codes, mlKit) = coroutineScope {
            val barcodeTask = async { photoBarcodeScanner.leer(path, BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS).getOrDefault(emptyList()) }
            val textTask = async { localTextOcr.readFromFile(path).getOrNull() }
            barcodeTask.await() to textTask.await()
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val initial = LabelFieldExtractor.extractDetailed(mlKit?.rawText.orEmpty(), codes)
        val textResult = if (improve || initial.fields.isEmpty() && initial.conflicts.isEmpty()) {
            paddleOcrEngine.readFromFile(path).getOrNull() ?: mlKit
        } else mlKit
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (token != generation) return
        val rawText = textResult?.rawText.orEmpty()
        val extraction = LabelFieldExtractor.extractDetailed(rawText, codes)
        val oldByField = previous.campos.associateBy { it.leido.campo }
        val proposed = extraction.fields.map { freshReading ->
            val fresh = aPropuesto(freshReading)
            val old = oldByField[freshReading.campo]
            when {
                old == null -> fresh
                old.valorAEscribir != old.leido.valor || fresh.valorAEscribir == old.leido.valor ->
                    old.copy(aceptado = old.aceptado, detalle = fresh.detalle ?: old.detalle)
                else -> fresh
            }
        }.toMutableList()
        val resolvedAfterImprove = previous.resolvedConflicts.toMutableSet()
        extraction.conflicts.forEach { conflict ->
            val previousChoice = oldByField[conflict.field]
            proposed.removeAll { it.leido.campo == conflict.field }
            if (improve && conflict.field in previous.resolvedConflicts && previousChoice != null) {
                proposed.add(previousChoice)
            } else {
                conflict.observations.firstOrNull()?.let { first ->
                    proposed.add(aPropuesto(first).copy(aceptado = false, detalle = "Hay lecturas diferentes. Elige el valor correcto."))
                }
                resolvedAfterImprove.remove(conflict.field)
            }
        }
        if (improve) {
            previous.campos.filter { old -> old.valorAEscribir != old.leido.valor && proposed.none { it.leido.campo == old.leido.campo } }
                .forEach(proposed::add)
        }
        val matching = PartidaMatcher.match(partidas, proposed.map { it.leido.copy(valor = it.valorAEscribir) }, rawText)
        val selectedBefore = previous.selectedPartida
        update(_estado.value.copy(
            analizando = false, refinando = false, campos = proposed, textoCrudo = rawText,
            rawCodes = codes, observations = extraction.observations, conflicts = extraction.conflicts,
            resolvedConflicts = resolvedAfterImprove, candidates = matching.candidates,
            selectedPartida = selectedBefore, readingMillis = SystemClock.elapsedRealtime() - started,
            identityAccepted = if (improve) previous.identityAccepted else false,
            physicalReviewConfirmed = if (improve) previous.physicalReviewConfirmed else false,
            error = if (proposed.isEmpty()) "No se identificaron campos. Puedes elegir la partida y conservar la foto como avance." else null
        ))
        val selectedId = initialPartidaId ?: selectedBefore?.IdRevdet ?: matching.suggestedPartidaId
        if (selectedId != null && selectedId != selectedBefore?.IdRevdet) selectPartida(selectedId)
    }

    fun selectPartida(idRevdet: Int) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        contextJob?.cancel()
        val selected = partidas.firstOrNull { it.IdRevdet == idRevdet }
        update(_estado.value.copy(selectedPartida = selected, snapshot = null, loadingContext = selected != null,
            identityAccepted = false, physicalReviewConfirmed = false, canEdit = false, canMarkReviewed = false))
        if (selected == null) return
        val captureId = _estado.value.captureId
        contextJob = viewModelScope.launch {
            val result = repository.getContext(selected.IdRevdet, reference)
            if (_estado.value.captureId != captureId || _estado.value.selectedPartida?.IdRevdet != selected.IdRevdet) return@launch
            result.onSuccess { response ->
                update(_estado.value.copy(snapshot = response.snapshot, loadingContext = false, canEdit = response.canEdit,
                    canMarkReviewed = response.canMarkReviewed, error = if (response.canEdit) null else response.message))
            }.onFailure { error ->
                update(_estado.value.copy(loadingContext = false, error = error.message))
            }
        }
    }

    fun alternar(campo: LabelFieldExtractor.Campo) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        if (_estado.value.unresolvedConflicts.any { it.field == campo }) return
        update(_estado.value.copy(campos = _estado.value.campos.map {
            if (it.leido.campo == campo && it.escribible) it.copy(aceptado = !it.aceptado) else it
        }))
    }

    fun editField(field: LabelFieldExtractor.Campo, value: String) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        val current = _estado.value.campos.firstOrNull { it.leido.campo == field } ?: return
        val corrected = aPropuesto(current.leido.copy(valor = value.trim()))
        update(_estado.value.copy(campos = _estado.value.campos.map {
            if (it.leido.campo == field) corrected.copy(leido = current.leido) else it
        }, resolvedConflicts = _estado.value.resolvedConflicts + field))
        if (field == LabelFieldExtractor.Campo.NUMERO_PARTE) {
            val fields = _estado.value.campos.map { it.leido.copy(valor = it.valorAEscribir) }
            val match = PartidaMatcher.match(partidas, fields, _estado.value.textoCrudo)
            update(_estado.value.copy(candidates = match.candidates, identityAccepted = false))
        }
    }

    fun acceptIdentity(accepted: Boolean) = update(_estado.value.copy(identityAccepted = accepted))
    fun confirmPhysicalReview(confirmed: Boolean) = update(_estado.value.copy(physicalReviewConfirmed = confirmed))

    fun save(markReviewed: Boolean) {
        val state = _estado.value
        if (!state.canSave || markReviewed && !state.canComplete) return
        val selected = state.selectedPartida ?: return
        val captureId = state.captureId ?: return
        val snapshot = state.snapshot ?: return
        fun accepted(field: LabelFieldExtractor.Campo): String? = state.aceptados.firstOrNull { it.leido.campo == field }?.valorAEscribir
        val hazmat = LabelCaptureHazmat(accepted(LabelFieldExtractor.Campo.UN_HAZMAT),
            accepted(LabelFieldExtractor.Campo.CLASE_HAZMAT), accepted(LabelFieldExtractor.Campo.GRUPO_EMBALAJE))
        val request = ConfirmLabelCaptureRequest(
            captureId = captureId, crefAgrupada = reference, idRevdet = selected.IdRevdet, expectedSnapshot = snapshot,
            fields = LabelCaptureFields(accepted(LabelFieldExtractor.Campo.LOTE), accepted(LabelFieldExtractor.Campo.NUMERO_SERIE),
                accepted(LabelFieldExtractor.Campo.MARCA), accepted(LabelFieldExtractor.Campo.MODELO), accepted(LabelFieldExtractor.Campo.PAIS_ORIGEN)),
            hazmat = hazmat.takeIf { it.un != null || it.clase != null || it.grupo != null },
            markReviewed = markReviewed, physicalReviewConfirmed = state.physicalReviewConfirmed,
            observations = state.observations.take(64).map { reading ->
                val proposal = state.campos.firstOrNull { it.leido.campo == reading.campo }
                LabelCaptureObservation(
                    field = reading.campo.name,
                    value = reading.valor,
                    rawValue = reading.rawValue,
                    source = reading.origen.name,
                    accepted = proposal?.let {
                        it.escribible && it.aceptado && it.valorAEscribir.equals(reading.valor, ignoreCase = true)
                    } == true
                )
            },
            rawText = state.textoCrudo.take(16000), rawCodes = state.rawCodes.take(64).map { it.take(1024) }
        )
        _estado.value = state.copy(syncStatus = LabelCaptureStatus.PENDING, syncMessage = "Preparando la evidencia…")
        viewModelScope.launch {
            try {
                persistenceJob?.join()
                repository.saveState(captureId, gson.toJson(state.copy(availablePartidas = emptyList(), candidates = emptyList())))
                repository.queue(captureId, request)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.value = _estado.value.copy(syncStatus = LabelCaptureStatus.DRAFT, syncMessage = null, error = error.message)
            }
        }
    }

    fun improveReading() {
        val state = _estado.value
        if (state.syncStatus != LabelCaptureStatus.DRAFT || state.analizando || state.refinando) return
        val path = state.fotoPath ?: return
        cancelReading()
        val token = generation
        update(state.copy(refinando = true))
        analysisJob = viewModelScope.launch {
            try { readPhoto(path, token, improve = true) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { if (token == generation) update(_estado.value.copy(refinando = false, error = error.message)) }
        }
    }

    fun restoreDraft(captureId: String) {
        val draft = _drafts.value.firstOrNull { it.captureId == captureId } ?: return
        cancelReading()
        val restored = runCatching { gson.fromJson(draft.stateJson, Estado::class.java) }.getOrNull() ?: Estado()
        _estado.value = restored.copy(captureId = draft.captureId, fotoPath = draft.photoPath,
            availablePartidas = partidas, analizando = false, refinando = false, loadingContext = false,
            syncStatus = draft.status, syncMessage = draft.errorMessage)
        val match = PartidaMatcher.match(partidas, restored.campos.map { it.leido.copy(valor = it.valorAEscribir) }, restored.textoCrudo)
        _estado.value = _estado.value.copy(candidates = match.candidates)
        if (draft.status == LabelCaptureStatus.PENDING) repository.schedule(captureId)
    }

    fun retry() {
        val id = _estado.value.captureId ?: return
        viewModelScope.launch {
            try { repository.retry(id) }
            catch (error: Exception) { _estado.value = _estado.value.copy(error = error.message) }
        }
    }

    fun discardDraft(onDiscarded: () -> Unit = {}) {
        val state = _estado.value
        if (state.syncStatus == LabelCaptureStatus.PENDING) return
        cancelReading()
        viewModelScope.launch {
            try {
                persistenceJob?.join()
                state.captureId?.let { repository.discard(it) }
                _estado.value = Estado(availablePartidas = partidas)
                onDiscarded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) { _estado.value = _estado.value.copy(error = error.message) }
        }
    }

    fun reloadContext() {
        val state = _estado.value
        val id = state.selectedPartida?.IdRevdet ?: return
        if (state.syncStatus == LabelCaptureStatus.DRAFT) { selectPartida(id); return }
        if (state.syncStatus != LabelCaptureStatus.BLOCKED) return
        viewModelScope.launch {
            try {
                val draft = repository.reopenConflict(requireNotNull(state.captureId))
                _estado.value = state.copy(captureId = draft.captureId, fotoPath = draft.photoPath,
                    syncStatus = LabelCaptureStatus.DRAFT, syncMessage = null, snapshot = null, identityAccepted = false,
                    physicalReviewConfirmed = false)
                selectPartida(id)
            } catch (error: Exception) { _estado.value = _estado.value.copy(error = error.message) }
        }
    }

    /** Cerrar conserva el borrador; una confirmación ya recibida puede retirarse localmente. */
    fun limpiar() {
        val state = _estado.value
        cancelReading()
        if (state.captureId != null) {
            val previous = persistenceJob
            persistenceJob = applicationScope.launch {
                previous?.join()
                runCatching {
                    if (state.syncStatus == LabelCaptureStatus.CONFIRMED) repository.discard(state.captureId)
                    else repository.saveState(state.captureId, gson.toJson(state.copy(availablePartidas = emptyList(), candidates = emptyList())))
                }
            }
        }
        _estado.value = Estado(availablePartidas = partidas)
    }

    private fun cancelReading() {
        generation++
        analysisJob?.cancel()
        contextJob?.cancel()
    }

    private fun update(state: Estado) {
        _estado.value = state
        val id = state.captureId ?: return
        if (state.syncStatus == LabelCaptureStatus.DRAFT) {
            val previous = persistenceJob
            val json = gson.toJson(state.copy(availablePartidas = emptyList(), candidates = emptyList()))
            persistenceJob = applicationScope.launch {
                previous?.join()
                runCatching { repository.saveState(id, json) }
            }
        }
    }

    private fun aPropuesto(leido: LabelFieldExtractor.CampoLeido): CampoPropuesto {
        if (leido.campo == LabelFieldExtractor.Campo.PAIS_ORIGEN) {
            val catalog = countries.mapNotNull { country ->
                val code = country.codigo?.trim().orEmpty()
                val name = (country.descrip ?: country.cnombre).orEmpty()
                if (code.isEmpty()) null else CountryCatalogMatcher.PaisCatalogo(code, name)
            }
            val resolved = CountryCatalogMatcher.emparejar(leido.valor, catalog)
            return CampoPropuesto(leido, resolved != null, resolved != null, Destino.PARTIDA,
                resolved?.codigo ?: leido.valor, resolved?.let { "${it.codigo} · ${it.nombre}" } ?: "Elige un país válido del catálogo.")
        }
        if (leido.campo in CAMPOS_HAZMAT) {
            val valid = when (leido.campo) {
                LabelFieldExtractor.Campo.UN_HAZMAT -> Regex("\\d{4}").matches(leido.valor)
                LabelFieldExtractor.Campo.CLASE_HAZMAT -> Regex("[1-9](?:\\.[1-9])?").matches(leido.valor)
                LabelFieldExtractor.Campo.GRUPO_EMBALAJE -> leido.valor in setOf("I", "II", "III")
                else -> false
            }
            return CampoPropuesto(leido, valid, valid, Destino.HAZMAT,
                detalle = if (valid) "Se coteja con el Hazmat vigente de la referencia." else "Verifica el formato antes de guardar.")
        }
        val writable = leido.campo in CAMPOS_ESCRIBIBLES && leido.valor.isNotBlank()
        return CampoPropuesto(leido, writable, writable, if (writable) Destino.PARTIDA else Destino.NINGUNO)
    }

    companion object {
        val CAMPOS_ESCRIBIBLES = mapOf(
            LabelFieldExtractor.Campo.LOTE to "numLote", LabelFieldExtractor.Campo.NUMERO_SERIE to "numSer",
            LabelFieldExtractor.Campo.PAIS_ORIGEN to "cPaisOrigen", LabelFieldExtractor.Campo.MARCA to "marca",
            LabelFieldExtractor.Campo.MODELO to "modelo"
        )
        val CAMPOS_HAZMAT = setOf(LabelFieldExtractor.Campo.UN_HAZMAT,
            LabelFieldExtractor.Campo.CLASE_HAZMAT, LabelFieldExtractor.Campo.GRUPO_EMBALAJE)
    }
}
