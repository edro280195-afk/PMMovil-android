package com.example.pmmovil.ui.pdf

import android.content.Context
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.db.PdfAnnotationDraftDao
import com.example.pmmovil.data.model.PdfAnnotationDraftEntity
import com.example.pmmovil.di.ApplicationScope
import com.example.pmmovil.data.model.DocumentoPdfDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PdfWorkspaceUiState(
    val documents: List<DocumentoPdfDto> = emptyList(),
    val selectedDocument: DocumentoPdfDto? = null,
    val documentFile: File? = null,
    val annotatedFile: File? = null,
    val annotations: List<PdfAnnotation> = emptyList(),
    val currentPage: Int = 0,
    val isLoadingDocuments: Boolean = false,
    val isLoadingPdf: Boolean = false,
    val error: String? = null
)

/**
 * Mantiene la lista de documentos y el PDF activo dentro de la revisión.
 */
@HiltViewModel
class PdfWorkspaceViewModel @Inject constructor(
    private val api: PMMovilApi,
    private val draftDao: PdfAnnotationDraftDao,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfWorkspaceUiState())
    val uiState: StateFlow<PdfWorkspaceUiState> = _uiState.asStateFlow()

    private var currentEntry = ""
    private var requestedReference: String? = null
    private var downloadJob: Job? = null
    private val annotationsByDocument = mutableMapOf<String, List<PdfAnnotation>>()
    private val annotatedFilesByDocument = mutableMapOf<String, File>()
    private val currentPageByDocument = mutableMapOf<String, Int>()

    fun start(crefAgrupada: String, initialReference: String?) {
        requestedReference = initialReference?.trim()?.takeIf(String::isNotEmpty)
        if (currentEntry == crefAgrupada && _uiState.value.documents.isNotEmpty()) {
            synchronizeWithInvoice(initialReference)
            return
        }

        currentEntry = crefAgrupada
        annotationsByDocument.clear()
        annotatedFilesByDocument.clear()
        currentPageByDocument.clear()
        viewModelScope.launch {
            _uiState.value = PdfWorkspaceUiState(isLoadingDocuments = true)
            // Primero el borrador local: si se lee despues, el documento ya se
            // habria abierto sin las marcas que el revisor dejo la vez pasada.
            restoreDrafts(crefAgrupada)
            runCatching { api.getDocumentosPdf(crefAgrupada) }
                .onSuccess { response ->
                    val body = response.body()
                    if (!response.isSuccessful || body?.success != true) {
                        _uiState.update {
                            it.copy(
                                isLoadingDocuments = false,
                                error = body?.message
                                    ?: "No se pudo obtener la lista de documentos."
                            )
                        }
                        return@onSuccess
                    }

                    val available = body.items
                        .orEmpty()
                        .filter(DocumentoPdfDto::disponible)
                    val selected = chooseDocument(
                        documents = available,
                        reference = requestedReference
                    )
                    _uiState.update {
                        it.copy(
                            documents = available,
                            selectedDocument = selected,
                            isLoadingDocuments = false,
                            error = if (available.isEmpty()) {
                                "No hay documentos PDF disponibles para esta entrada."
                            } else {
                                null
                            }
                        )
                    }
                    selected?.let(::downloadDocument)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingDocuments = false,
                            error = error.message
                                ?: "No se pudo conectar con el servidor de documentos."
                        )
                    }
                }
        }
    }

    fun synchronizeWithInvoice(reference: String?) {
        val normalized = reference?.trim()?.takeIf(String::isNotEmpty) ?: return
        requestedReference = normalized
        val selected = chooseDocument(_uiState.value.documents, normalized) ?: return
        if (selectedKey(selected) != selectedKey(_uiState.value.selectedDocument)) {
            selectDocument(selected)
        }
    }

    fun selectDocument(document: DocumentoPdfDto) {
        if (selectedKey(document) == selectedKey(_uiState.value.selectedDocument) &&
            (_uiState.value.documentFile != null || _uiState.value.annotatedFile != null)
        ) {
            return
        }
        val key = selectedKey(document)
        _uiState.update {
            it.copy(
                selectedDocument = document,
                documentFile = null,
                annotatedFile = key?.let(annotatedFilesByDocument::get),
                annotations = key?.let(annotationsByDocument::get).orEmpty(),
                currentPage = key?.let(currentPageByDocument::get) ?: 0,
                error = null
            )
        }
        if (_uiState.value.annotatedFile == null) {
            downloadDocument(document)
        }
    }

    fun retry() {
        _uiState.value.selectedDocument?.let(::downloadDocument)
            ?: start(currentEntry, requestedReference)
    }

    fun getShareUri(file: File) = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )

    fun addAnnotation(annotation: PdfAnnotation) {
        val key = selectedKey(_uiState.value.selectedDocument) ?: return
        val updated = annotationsByDocument[key].orEmpty() + annotation
        annotationsByDocument[key] = updated
        _uiState.update { it.copy(annotations = updated) }
        persistDraft(key)
    }

    fun undoLastAnnotation() {
        val key = selectedKey(_uiState.value.selectedDocument) ?: return
        val current = annotationsByDocument[key].orEmpty()
        if (current.isEmpty()) return
        val updated = current.dropLast(1)
        if (updated.isEmpty()) {
            annotationsByDocument.remove(key)
        } else {
            annotationsByDocument[key] = updated
        }
        _uiState.update { it.copy(annotations = updated) }
        persistDraft(key)
    }

    fun markAnnotatedFileSaved(file: File) {
        val key = selectedKey(_uiState.value.selectedDocument) ?: return
        annotatedFilesByDocument[key] = file
        // Las marcas ya quedaron grabadas en la copia: si se conservaran aqui
        // se dibujarian encima de si mismas.
        annotationsByDocument.remove(key)
        _uiState.update {
            it.copy(
                annotatedFile = file,
                annotations = emptyList()
            )
        }
        persistDraft(key)
    }

    fun discardUnsavedAnnotations() {
        val key = selectedKey(_uiState.value.selectedDocument) ?: return
        annotationsByDocument.remove(key)
        _uiState.update { it.copy(annotations = emptyList()) }
        persistDraft(key)
    }

    fun setCurrentPage(page: Int) {
        val key = selectedKey(_uiState.value.selectedDocument) ?: return
        val normalizedPage = page.coerceAtLeast(0)
        currentPageByDocument[key] = normalizedPage
        _uiState.update { it.copy(currentPage = normalizedPage) }
    }

    /**
     * Trae lo que el revisor llevaba hecho en esta entrada. Un borrador que no
     * se pueda leer se ignora: perder unas marcas incomoda, pero tumbar la
     * pantalla de revision por eso es peor.
     */
    private suspend fun restoreDrafts(crefAgrupada: String) {
        val drafts = runCatching {
            withContext(Dispatchers.IO) { draftDao.forEntry(crefAgrupada) }
        }.getOrNull().orEmpty()

        drafts.forEach { draft ->
            decodePdfAnnotations(draft.annotationsJson)
                .takeIf { it.isNotEmpty() }
                ?.let { annotationsByDocument[draft.documentKey] = it }
            // La copia anotada pudo borrarse del dispositivo por fuera.
            draft.annotatedFilePath
                ?.let(::File)
                ?.takeIf(File::exists)
                ?.let { annotatedFilesByDocument[draft.documentKey] = it }
        }
    }

    /**
     * Guarda el borrador fuera de [viewModelScope]: si el revisor sale de la
     * pantalla justo despues de un trazo, ese scope se cancela y la escritura
     * se quedaria a medias, que es justo el caso que este respaldo evita.
     */
    private fun persistDraft(key: String) {
        val annotations = annotationsByDocument[key].orEmpty()
        val annotatedPath = annotatedFilesByDocument[key]?.absolutePath
        val entry = currentEntry
        applicationScope.launch {
            runCatching {
                if (annotations.isEmpty() && annotatedPath == null) {
                    draftDao.deleteByKey(key)
                } else {
                    draftDao.upsert(
                        PdfAnnotationDraftEntity(
                            documentKey = key,
                            crefAgrupada = entry,
                            annotationsJson = encodePdfAnnotations(annotations),
                            annotatedFilePath = annotatedPath,
                            lastEditAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }

    private fun downloadDocument(document: DocumentoPdfDto) {
        val reference = document.creferencia?.trim().orEmpty()
        if (reference.isEmpty()) {
            _uiState.update { it.copy(error = "El documento no tiene referencia.") }
            return
        }

        downloadJob?.cancel()
        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingPdf = true,
                    documentFile = null,
                    annotatedFile = annotatedFilesByDocument[selectedKey(document)],
                    annotations = annotationsByDocument[selectedKey(document)].orEmpty(),
                    currentPage = currentPageByDocument[selectedKey(document)] ?: 0,
                    error = null
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val response = api.downloadPdf(reference, document.tipo)
                    val body = response.body()
                    if (!response.isSuccessful || body == null) {
                        error("PDF no encontrado o no disponible.")
                    }

                    val directory = File(context.cacheDir, "pdf").apply {
                        mkdirs()
                    }
                    val safeReference = reference.replace(
                        Regex("[^A-Za-z0-9_-]"),
                        "_"
                    )
                    val file = File(
                        directory,
                        "${safeReference}_${document.tipo}.pdf"
                    )
                    body.byteStream().use { input ->
                        file.outputStream().use(input::copyTo)
                    }
                    file
                }
            }.onSuccess { file ->
                _uiState.update {
                    it.copy(
                        documentFile = file,
                        isLoadingPdf = false,
                        error = null
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        documentFile = null,
                        isLoadingPdf = false,
                        error = error.message ?: "No se pudo cargar el PDF."
                    )
                }
            }
        }
    }

    private fun chooseDocument(
        documents: List<DocumentoPdfDto>,
        reference: String?
    ): DocumentoPdfDto? {
        val normalized = reference?.trim()
        return documents.firstOrNull {
            it.creferencia?.trim() == normalized && it.tipo == 15
        } ?: documents.firstOrNull {
            it.creferencia?.trim() == normalized
        } ?: documents.firstOrNull()
    }

    private fun selectedKey(document: DocumentoPdfDto?): String? {
        document ?: return null
        // Lleva la entrada porque ahora tambien es la llave primaria en Room.
        return "$currentEntry|${document.creferencia?.trim()}|${document.tipo}"
    }
}
