package com.example.pmmovil.ui.revision

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.BuildConfig
import com.example.pmmovil.data.model.LabelCaptureDraftEntity
import com.example.pmmovil.data.model.LabelCaptureResponse
import com.example.pmmovil.data.model.LabelCaptureSnapshot
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.data.model.PaisDto
import com.example.pmmovil.data.model.PartidaRevisionDto
import com.example.pmmovil.data.ocr.CountryCatalogMatcher
import com.example.pmmovil.data.ocr.LabelBarcodeInterpreter
import com.example.pmmovil.data.ocr.LabelFieldExtractor
import com.example.pmmovil.data.ocr.LineaDeTexto
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
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

    /** GUARDADO: la evidencia ya quedó congelada en el equipo y la pantalla puede seguir con la siguiente etiqueta. */
    enum class Envio { NINGUNO, GUARDANDO, GUARDADO }

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
        /** Qué hizo el revisador con cada diferencia. Se pierde al cambiar de partida o de valor. */
        val decisiones: Map<LabelFieldExtractor.Campo, LabelCaptureCotejo.Decision> = emptyMap(),
        /** Partidas que el revisador ya dijo que no son: dejan de aparecer entre las sugerencias. */
        val rechazadas: Set<Int> = emptySet(),
        val syncStatus: String = LabelCaptureStatus.DRAFT,
        val syncMessage: String? = null,
        /** Solo describe el guardado que el revisador acaba de hacer en esta pantalla; un borrador restaurado empieza en [Envio.NINGUNO]. */
        val envio: Envio = Envio.NINGUNO,
        /** Lo que pidió el último guardado: marcar la partida como revisada o solo conservar el avance. */
        val marcadaRevisada: Boolean = false,
        val readingMillis: Long? = null
    ) {
        val huboLectura: Boolean get() = campos.isNotEmpty()
        val unresolvedConflicts: List<LabelFieldExtractor.FieldConflict>
            get() = conflicts.filter { it.field !in resolvedConflicts }
        val canSave: Boolean get() = !analizando && !refinando && !loadingContext && canEdit &&
            selectedPartida != null && snapshot != null && identityAccepted &&
            syncStatus == LabelCaptureStatus.DRAFT && captureId != null
        /** Marcar la partida como revisada exige que no quede ninguna diferencia sin decidir. */
        val canComplete: Boolean get() = canSave && canMarkReviewed &&
            LabelCaptureCotejo.pendientes(LabelCaptureCotejo.comparar(this)) == 0
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
    private val retirando: MutableSet<String> = ConcurrentHashMap.newKeySet()

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
                retirarConfirmadas(records)
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

    /**
     * Una captura que el servidor ya confirmó no necesita su copia local: la evidencia está allá. Sin esto,
     * al pasar sola a la siguiente etiqueta (la confirmación llega después) se acumulaban filas y ~5 MB de
     * fotos por etiqueta en el equipo. La activa, la que el revisador tiene abierta, no se toca.
     */
    private fun retirarConfirmadas(records: List<LabelCaptureDraftEntity>) {
        val activa = _estado.value.captureId
        records.filter { it.status == LabelCaptureStatus.CONFIRMED && it.captureId != activa }.forEach { confirmada ->
            if (!retirando.add(confirmada.captureId)) return@forEach
            applicationScope.launch {
                // Un respiro para que la pantalla alcance a refrescar la partida con lo recién confirmado.
                delay(RETIRO_MILLIS)
                runCatching { repository.discard(confirmada.captureId) }
                retirando.remove(confirmada.captureId)
            }
        }
    }

    /** Los códigos del preview solo guían. La evidencia se extrae de la foto actual. */
    /** Los códigos que el video confirmó mientras se encuadraba; se suman a los de la foto. */
    private var codigosDelVideo: List<String> = emptyList()

    fun analizar(path: String, codigosEnVivo: List<String> = emptyList(), catalogoPaises: List<PaisDto> = countries,
                 initialPartidaId: Int? = null) {
        countries = catalogoPaises
        cancelReading()
        val token = generation
        codigosDelVideo = codigosEnVivo
        Log.i(TAG, "foto recibida · códigos del video=${codigosEnVivo.size}${codigosParaLog(codigosEnVivo)} · partidas=${partidas.size}")
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
        val (codesFoto, mlKit) = coroutineScope {
            val barcodeTask = async { photoBarcodeScanner.leer(path, BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS).getOrDefault(emptyList()) }
            val textTask = async { localTextOcr.readFromFile(path).getOrNull() }
            barcodeTask.await() to textTask.await()
        }
        // A la foto se le suman los códigos que el video confirmó en dos cuadros. Medido en el Pixel el
        // 20-oct con la etiqueta de envío VDA: el video decodificó «Q20000» y la foto no lo trajo, así
        // que la cantidad se perdía y el empaque no se podía contar. Los que ya trae la foto mandan;
        // si el video contradice a la foto en un campo, el lector de identificadores descarta ese campo
        // y nadie se queda con el valor equivocado.
        val codes = (codesFoto + codigosDelVideo.filterNot { it in codesFoto }).distinct()
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        // Los códigos de barras mandan: son exactos. En una etiqueta de códigos por campo (la bolsa de
        // Freudenberg) el texto no decide ni parte, ni lote, ni cantidad, porque sus columnas giradas salen
        // del OCR con los valores desemparejados de sus rótulos.
        val interpretacion = interpretarCodigos(codes, mlKit?.lineas.orEmpty())
        val initial = LabelFieldExtractor.extractDetailed(
            mlKit?.rawText.orEmpty(), codes, interpretacion.campos, interpretacion.camposSinOcr
        )
        val textResult = if (improve || initial.fields.isEmpty() && initial.conflicts.isEmpty()) {
            paddleOcrEngine.readFromFile(path).getOrNull() ?: mlKit
        } else mlKit
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (token != generation) return
        val rawText = textResult?.rawText.orEmpty()
        val extraction = LabelFieldExtractor.extractDetailed(rawText, codes, interpretacion.campos, interpretacion.camposSinOcr)
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
        // Una decisión solo vale para el valor que el revisador tuvo delante al tomarla.
        val decisionesVigentes = previous.decisiones.filterKeys { campo ->
            val antes = previous.campos.firstOrNull { it.leido.campo == campo }?.valorAEscribir
            antes != null && antes == proposed.firstOrNull { it.leido.campo == campo }?.valorAEscribir
        }
        update(_estado.value.copy(
            analizando = false, refinando = false, campos = proposed, textoCrudo = rawText,
            rawCodes = codes, observations = extraction.observations, conflicts = extraction.conflicts,
            resolvedConflicts = resolvedAfterImprove, candidates = matching.candidates,
            selectedPartida = selectedBefore, readingMillis = SystemClock.elapsedRealtime() - started,
            identityAccepted = if (improve) previous.identityAccepted else false,
            decisiones = decisionesVigentes,
            error = if (proposed.isEmpty()) "No se identificaron campos. Puedes elegir la partida y conservar la foto como avance." else null
        ))
        val selectedId = initialPartidaId ?: selectedBefore?.IdRevdet ?: matching.suggestedPartidaId
        Log.i(
            TAG,
            "lectura lista en ${SystemClock.elapsedRealtime() - started} ms · códigos de la foto=${codes.size}${codigosParaLog(codes)} · " +
                "campos=${proposed.joinToString(",") { it.leido.campo.name }} · conflictos=${extraction.conflicts.size} · " +
                "candidatas=${matching.candidates.size} · sugerida=${matching.suggestedPartidaId} · mejorar=$improve"
        )
        // Si el revisador abrió la cámara desde una partida, esa elección ya es suya.
        if (selectedId != null && selectedId != selectedBefore?.IdRevdet) {
            elegirPartida(selectedId, porUsuario = initialPartidaId != null)
        }
    }

    /** El revisador eligió la partida con el dedo: eso basta, salvo que la clave de la etiqueta diga otra. */
    /** Ubica con las claves de las partidas (y los países del catálogo) los códigos de barras simples de la foto. */
    private fun interpretarCodigos(codes: List<String>, lineas: List<LineaDeTexto>): LabelBarcodeInterpreter.Interpretacion {
        val claves = partidas.flatMap { listOfNotNull(it.CnoParte, it.ParteCte) }
        val catalogo = catalogoDePaises()
        return LabelBarcodeInterpreter.interpretar(
            codes, claves, esPais = { valor -> CountryCatalogMatcher.emparejar(valor, catalogo) != null }, lineas = lineas
        )
    }

    private fun catalogoDePaises(): List<CountryCatalogMatcher.PaisCatalogo> = countries.mapNotNull { country ->
        val code = country.codigo?.trim().orEmpty()
        val name = (country.descrip ?: country.cnombre).orEmpty()
        if (code.isEmpty()) null else CountryCatalogMatcher.PaisCatalogo(code, name)
    }

    fun selectPartida(idRevdet: Int) = elegirPartida(idRevdet, porUsuario = true)

    /**
     * La identidad queda verificada sola solo si la clave de la etiqueta es la de la partida. Sin clave
     * legible la da por buena únicamente una elección humana; con una clave distinta hace falta que el
     * revisador diga «sí, es esta» ([acceptIdentity]).
     */
    private fun elegirPartida(idRevdet: Int, porUsuario: Boolean, conservarIdentidad: Boolean = false) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        contextJob?.cancel()
        val selected = partidas.firstOrNull { it.IdRevdet == idRevdet }
        val verificada = when (selected?.let { LabelCaptureCotejo.identidad(_estado.value.campos, it) }) {
            LabelCaptureCotejo.Identidad.CLAVE_EXACTA -> true
            LabelCaptureCotejo.Identidad.SIN_CLAVE -> porUsuario || conservarIdentidad
            LabelCaptureCotejo.Identidad.CLAVE_DISTINTA -> conservarIdentidad
            null -> false
        }
        update(_estado.value.copy(selectedPartida = selected, snapshot = null, loadingContext = selected != null,
            identityAccepted = verificada, decisiones = emptyMap(), canEdit = false, canMarkReviewed = false))
        if (selected == null) return
        val captureId = _estado.value.captureId
        contextJob = viewModelScope.launch {
            val result = repository.getContext(selected.IdRevdet, reference)
            if (_estado.value.captureId != captureId || _estado.value.selectedPartida?.IdRevdet != selected.IdRevdet) return@launch
            result.onSuccess { response ->
                Log.i(TAG, "contexto de la partida ${selected.IdRevdet}: canEdit=${response.canEdit} canMarkReviewed=${response.canMarkReviewed}${response.message?.let { " · $it" }.orEmpty()}")
                update(_estado.value.copy(snapshot = response.snapshot, loadingContext = false, canEdit = response.canEdit,
                    canMarkReviewed = response.canMarkReviewed, error = if (response.canEdit) null else response.message))
            }.onFailure { error ->
                Log.w(TAG, "contexto de la partida ${selected.IdRevdet} falló: ${error.message}")
                update(_estado.value.copy(loadingContext = false, error = error.message))
            }
        }
    }

    /** Resuelve una diferencia: conservar lo declarado o usar lo que dice la etiqueta. */
    fun decidir(campo: LabelFieldExtractor.Campo, decision: LabelCaptureCotejo.Decision) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        update(_estado.value.copy(decisiones = _estado.value.decisiones + (campo to decision)))
    }

    fun editField(field: LabelFieldExtractor.Campo, value: String) {
        if (_estado.value.syncStatus != LabelCaptureStatus.DRAFT) return
        val current = _estado.value.campos.firstOrNull { it.leido.campo == field } ?: return
        val corrected = aPropuesto(current.leido.copy(valor = value.trim()))
        // Corregir cambia el valor que se compara: la decisión anterior ya no aplica.
        update(_estado.value.copy(campos = _estado.value.campos.map {
            if (it.leido.campo == field) corrected.copy(leido = current.leido) else it
        }, resolvedConflicts = _estado.value.resolvedConflicts + field, decisiones = _estado.value.decisiones - field))
        if (field == LabelFieldExtractor.Campo.NUMERO_PARTE) {
            val fields = _estado.value.campos.map { it.leido.copy(valor = it.valorAEscribir) }
            val match = PartidaMatcher.match(partidas, fields, _estado.value.textoCrudo)
            update(_estado.value.copy(candidates = match.candidates, identityAccepted = false))
        }
    }

    fun acceptIdentity(accepted: Boolean) = update(_estado.value.copy(identityAccepted = accepted))

    /** El revisador dice que esta no es la partida: se vuelve a la lista de posibles para elegir otra. */
    fun rejectPartida() {
        val id = _estado.value.selectedPartida?.IdRevdet ?: return
        update(_estado.value.copy(rechazadas = _estado.value.rechazadas + id))
        elegirPartida(0, porUsuario = false)
    }

    /**
     * Marcar la partida como revisada es el botón principal de la tarjeta, así que tocarlo es la
     * confirmación de la revisión física que el servidor exige. Qué datos viajan lo decide el cotejo.
     */
    fun save(markReviewed: Boolean) {
        val state = _estado.value
        val request = LabelCaptureCotejo.armarPeticion(state, reference, markReviewed) ?: return
        val captureId = request.captureId
        Log.i(TAG, "guardar: partida=${request.idRevdet} marcarRevisada=$markReviewed captura=${captureId.toString().take(8)}")
        _estado.value = state.copy(syncStatus = LabelCaptureStatus.PENDING, syncMessage = "Preparando la evidencia…",
            envio = Envio.GUARDANDO, marcadaRevisada = markReviewed)
        viewModelScope.launch {
            try {
                persistenceJob?.join()
                repository.saveState(captureId, gson.toJson(state.copy(availablePartidas = emptyList(), candidates = emptyList())))
                repository.queue(captureId, request)
                if (_estado.value.captureId == captureId) _estado.value = _estado.value.copy(envio = Envio.GUARDADO)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _estado.value = _estado.value.copy(syncStatus = LabelCaptureStatus.DRAFT, syncMessage = null,
                    envio = Envio.NINGUNO, error = error.message)
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
            syncStatus = draft.status, syncMessage = draft.errorMessage, envio = Envio.NINGUNO)
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
        // Volver a consultar la misma partida no le quita al revisador lo que ya había confirmado de su identidad.
        if (state.syncStatus == LabelCaptureStatus.DRAFT) {
            elegirPartida(id, porUsuario = true, conservarIdentidad = state.identityAccepted)
            return
        }
        if (state.syncStatus != LabelCaptureStatus.BLOCKED) return
        viewModelScope.launch {
            try {
                val draft = repository.reopenConflict(requireNotNull(state.captureId))
                _estado.value = state.copy(captureId = draft.captureId, fotoPath = draft.photoPath,
                    syncStatus = LabelCaptureStatus.DRAFT, syncMessage = null, snapshot = null,
                    decisiones = emptyMap(), envio = Envio.NINGUNO)
                elegirPartida(id, porUsuario = true, conservarIdentidad = state.identityAccepted)
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
            val resolved = CountryCatalogMatcher.emparejar(leido.valor, catalogoDePaises())
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

    /** Los valores de los códigos solo se escriben en builds de depuración: son datos de la mercancía del cliente. */
    private fun codigosParaLog(codes: List<String>): String =
        if (BuildConfig.DEBUG && codes.isNotEmpty()) " [" + codes.joinToString(" | ") { it.take(40) }.take(420) + "]" else ""

    companion object {
        private const val TAG = "LABEL_CAPTURE"
        private const val RETIRO_MILLIS = 3_000L

        val CAMPOS_ESCRIBIBLES = mapOf(
            LabelFieldExtractor.Campo.LOTE to "numLote", LabelFieldExtractor.Campo.NUMERO_SERIE to "numSer",
            LabelFieldExtractor.Campo.PAIS_ORIGEN to "cPaisOrigen", LabelFieldExtractor.Campo.MARCA to "marca",
            LabelFieldExtractor.Campo.MODELO to "modelo"
        )
        val CAMPOS_HAZMAT = setOf(LabelFieldExtractor.Campo.UN_HAZMAT,
            LabelFieldExtractor.Campo.CLASE_HAZMAT, LabelFieldExtractor.Campo.GRUPO_EMBALAJE)
    }
}
