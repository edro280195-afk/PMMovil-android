package com.example.pmmovil.data.repository

import android.content.Context
import android.util.Base64
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.camera.PhotoPipeline
import com.example.pmmovil.data.db.LabelCaptureDao
import com.example.pmmovil.data.model.ConfirmLabelCaptureRequest
import com.example.pmmovil.data.model.LabelCaptureDraftEntity
import com.example.pmmovil.data.model.LabelCaptureResponse
import com.example.pmmovil.data.model.LabelCaptureStatus
import com.example.pmmovil.worker.LabelCaptureSyncPolicy
import com.example.pmmovil.worker.LabelCaptureSyncWorker
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Conserva foto, borrador y confirmación hasta recibir un resultado verificable. */
@Singleton
class LabelCaptureRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LabelCaptureDao,
    private val api: PMMovilApi,
    private val auth: AuthRepository
) {
    private val gson = Gson()
    private val captureLocks = ConcurrentHashMap<String, Mutex>()
    private val managedDirectory: File
        get() = File(context.filesDir, "label_captures").apply { mkdirs() }

    private fun lockFor(captureId: String): Mutex = captureLocks.getOrPut(captureId) { Mutex() }

    private fun currentSession(): AuthRepository.SessionCredentials? = auth.getSessionCredentials()?.let { session ->
        val username = session.username.substringBefore(';').trim()
        if (username.isBlank() || session.token.isBlank()) null
        else AuthRepository.SessionCredentials(username, session.token)
    }

    private fun normalizedOwner(username: String): String = username.trim().lowercase(Locale.ROOT)

    fun observe(reference: String): Flow<List<LabelCaptureDraftEntity>> =
        dao.observe(reference, currentSession()?.username?.let(::normalizedOwner).orEmpty())

    suspend fun createDraft(reference: String, sourcePath: String, stateJson: String): LabelCaptureDraftEntity =
        withContext(Dispatchers.IO) {
            val session = requireNotNull(currentSession()) { "Inicia sesión antes de guardar la captura." }
            val directory = managedDirectory
            check(directory.isDirectory) { "No se pudo preparar el almacenamiento privado de la captura." }
            val captureId = UUID.randomUUID().toString()
            val photo = File(directory, "$captureId-original.jpg")
            val draft = LabelCaptureDraftEntity(
                captureId = captureId,
                reference = reference,
                owner = normalizedOwner(session.username),
                photoPath = photo.absolutePath,
                stateJson = stateJson
            )
            try {
                copyAtomically(File(sourcePath), photo)
                dao.save(draft)
                draft
            } catch (error: Exception) {
                photo.delete()
                throw error
            }
        }

    suspend fun saveState(captureId: String, stateJson: String): Int = withContext(Dispatchers.IO) {
        val draft = requireNotNull(dao.get(captureId)) { "No se encontró el borrador de captura." }
        requireOwner(draft)
        dao.updateState(captureId, draft.owner, stateJson, System.currentTimeMillis())
    }

    suspend fun getContext(idRevdet: Int, reference: String): Result<LabelCaptureResponse> =
        withContext(Dispatchers.IO) {
            try {
                val session = requireNotNull(currentSession()) { "Inicia sesión para consultar la partida." }
                val response = api.getLabelCaptureContext(
                    idRevdet,
                    reference,
                    authorizationHeader(session.token)
                )
                val body = response.body() ?: response.errorBody()?.string()?.let(::parseResponse)
                if (response.isSuccessful && body?.success == true && body.snapshot != null && body.idRevdet == idRevdet) {
                    Result.success(body)
                } else {
                    Result.failure(IllegalStateException(body?.message ?: "No se pudieron consultar los datos vigentes de la partida."))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    /** Congela una petición. Los reintentos siempre usan la misma imagen y valores. */
    suspend fun queue(captureId: String, request: ConfirmLabelCaptureRequest) =
        lockFor(captureId).withLock {
            withContext(Dispatchers.IO) {
                val draft = requireNotNull(dao.get(captureId)) { "No se encontró el borrador de captura." }
                requireOwner(draft)
                check(draft.status == LabelCaptureStatus.DRAFT &&
                    LabelCaptureSyncPolicy.canFreeze(draft.status, draft.requestJson, draft.uploadPath)) {
                    "Esta captura ya se envió; espera su resultado."
                }
                require(request.captureId == draft.captureId && request.crefAgrupada == draft.reference) {
                    "La captura y la petición no corresponden."
                }
                require(LabelCaptureSyncPolicy.isValidCaptureId(captureId)) { "El identificador de captura no es válido." }
                val encoded = requireNotNull(PhotoPipeline.aBase64(draft.photoPath)) {
                    "No se pudo preparar la foto de evidencia."
                }
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                require(bytes.isNotEmpty()) { "La foto preparada está vacía." }
                val upload = File(managedDirectory, "$captureId-evidence.jpg")
                try {
                    writeAtomically(upload, bytes)
                    val requestJson = gson.toJson(request.copy(photoBase64 = ""))
                    val frozen = dao.freeze(
                        captureId,
                        draft.owner,
                        requestJson,
                        upload.absolutePath,
                        System.currentTimeMillis()
                    )
                    check(frozen == 1) { "El borrador cambió mientras se preparaba el envío. Vuelve a revisar la captura." }
                } catch (error: Exception) {
                    upload.delete()
                    throw error
                }
                schedule(captureId)
            }
        }

    /** El nombre único impide dos envíos simultáneos de la misma captura. */
    fun schedule(captureId: String) {
        if (!LabelCaptureSyncPolicy.isValidCaptureId(captureId)) return
        val work = OneTimeWorkRequestBuilder<LabelCaptureSyncWorker>()
            .setInputData(workDataOf("captureId" to captureId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "label-capture-$captureId",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            work
        )
    }

    /** Devuelve true cuando WorkManager debe volver a intentar por red o servidor. */
    suspend fun sync(captureId: String): Boolean = lockFor(captureId).withLock {
        withContext(Dispatchers.IO) {
            val draft = dao.get(captureId) ?: return@withContext false
            if (draft.status != LabelCaptureStatus.PENDING) return@withContext false
            val session = currentSession() ?: return@withContext false
            if (!LabelCaptureSyncPolicy.isOwner(draft.owner, session.username)) return@withContext false

            val request = runCatching {
                gson.fromJson(requireNotNull(draft.requestJson), ConfirmLabelCaptureRequest::class.java)
            }.getOrNull()
            val upload = draft.uploadPath?.let(::File)
            if (request == null || upload == null ||
                !LabelCaptureSyncPolicy.isDirectChild(managedDirectory, upload) || !upload.isFile || upload.length() == 0L) {
                transition(captureId, draft.owner, LabelCaptureStatus.BLOCKED,
                    "No se encontró una petición o foto local válida. Conserva la captura y vuelve a tomar la foto.", null)
                return@withContext false
            }
            if (request.captureId != captureId || request.crefAgrupada != draft.reference) {
                transition(captureId, draft.owner, LabelCaptureStatus.BLOCKED,
                    "La petición guardada no corresponde a esta captura. Conserva el registro para revisión.", null)
                return@withContext false
            }

            val bytes = try {
                upload.readBytes()
            } catch (error: IOException) {
                transition(captureId, draft.owner, LabelCaptureStatus.BLOCKED,
                    "No se pudo leer la foto guardada: ${error.message.orEmpty()}", null)
                return@withContext false
            }
            try {
                val response = api.confirmLabelCapture(
                    request.copy(photoBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)),
                    authorizationHeader(session.token)
                )
                val body = response.body() ?: response.errorBody()?.string()?.let(::parseResponse)
                if (response.isSuccessful && body?.success == true) {
                    val valid = LabelCaptureSyncPolicy.matchesConfirmation(
                        expectedCaptureId = captureId,
                        expectedPartId = request.idRevdet,
                        actualCaptureId = body.captureId,
                        actualPartId = body.idRevdet,
                        photoId = body.photoId,
                        hasSnapshot = body.snapshot != null
                    )
                    if (valid) {
                        transition(captureId, draft.owner, LabelCaptureStatus.CONFIRMED, null, gson.toJson(body))
                        false
                    } else {
                        transition(captureId, draft.owner, LabelCaptureStatus.PENDING,
                            "El servidor respondió sin confirmar todos los datos de la captura. Se reintentará con el mismo identificador.", null)
                        true
                    }
                } else {
                    val code = body?.code
                    // Una sesión vencida requiere iniciar sesión de nuevo. Evita
                    // dejar un Worker reintentando en segundo plano sin fin.
                    val retryable = LabelCaptureSyncPolicy.isRetryableHttp(response.code(), code)
                    transition(captureId, draft.owner,
                        if (retryable) LabelCaptureStatus.PENDING else LabelCaptureStatus.BLOCKED,
                        body?.message ?: "No se pudo confirmar la captura (HTTP ${response.code()}).",
                        body?.let(gson::toJson))
                    retryable
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                val retryable = LabelCaptureSyncPolicy.isRetryableNetwork(error)
                transition(captureId, draft.owner,
                    if (retryable) LabelCaptureStatus.PENDING else LabelCaptureStatus.BLOCKED,
                    if (retryable) "Captura pendiente de sincronizar. Se conservaron la foto y la petición."
                    else "No se pudo establecer una conexión segura: ${error.message.orEmpty()}", null)
                retryable
            } catch (error: Exception) {
                transition(captureId, draft.owner, LabelCaptureStatus.BLOCKED,
                    "No se pudo procesar la confirmación de la captura: ${error.message.orEmpty()}", null)
                false
            }
        }
    }

    suspend fun retry(captureId: String) = lockFor(captureId).withLock {
        withContext(Dispatchers.IO) {
            val draft = dao.get(captureId) ?: return@withContext
            requireOwner(draft)
            check(draft.requestJson != null && draft.uploadPath != null) {
                "Revisa y confirma el borrador antes de enviarlo."
            }
            if (draft.status == LabelCaptureStatus.PENDING) {
                schedule(captureId)
                return@withContext
            }
            check(draft.status == LabelCaptureStatus.BLOCKED) { "Esta captura ya fue confirmada." }
            val response = draft.responseJson?.let(::parseResponse)
            check(!LabelCaptureSyncPolicy.requiresNewComparison(response?.code)) {
                "Los datos cambiaron. Abre la captura y vuelve a cotejar la partida."
            }
            val changed = dao.transition(captureId, draft.owner, LabelCaptureStatus.BLOCKED,
                LabelCaptureStatus.PENDING, null, null, System.currentTimeMillis())
            check(changed == 1) { "El estado de la captura cambió. Vuelve a consultar su resultado." }
            schedule(captureId)
        }
    }

    suspend fun discard(captureId: String) = lockFor(captureId).withLock {
        withContext(Dispatchers.IO) {
            val draft = dao.get(captureId) ?: return@withContext
            requireOwner(draft)
            check(draft.status != LabelCaptureStatus.PENDING) {
                "Espera la confirmación antes de retirar esta captura."
            }
            deleteDraft(draft)
        }
    }

    /** Reabre una captura bloqueada con otro UUID y conserva su foto para cotejar de nuevo. */
    suspend fun reopenConflict(captureId: String): LabelCaptureDraftEntity = lockFor(captureId).withLock {
        withContext(Dispatchers.IO) {
            val old = requireNotNull(dao.get(captureId)) { "No se encontró la captura bloqueada." }
            requireOwner(old)
            check(old.status == LabelCaptureStatus.BLOCKED)
            val replacement = createDraft(old.reference, old.photoPath, old.stateJson)
            deleteDraft(old)
            replacement
        }
    }

    private suspend fun transition(
        captureId: String,
        owner: String,
        status: String,
        message: String?,
        responseJson: String?
    ) {
        dao.transition(captureId, owner, LabelCaptureStatus.PENDING, status, message, responseJson,
            System.currentTimeMillis())
    }

    private suspend fun deleteDraft(draft: LabelCaptureDraftEntity) {
        val deleted = dao.delete(draft.captureId, draft.owner, draft.status)
        check(deleted == 1) { "La captura cambió mientras se retiraba. Vuelve a consultar el estado." }
        listOfNotNull(draft.photoPath, draft.uploadPath).forEach(::deleteOwnedFile)
    }

    private fun requireOwner(draft: LabelCaptureDraftEntity) {
        val session = requireNotNull(currentSession()) { "Inicia sesión con el usuario que creó esta captura." }
        check(LabelCaptureSyncPolicy.isOwner(draft.owner, session.username)) {
            "La captura pertenece a otro usuario."
        }
    }

    private fun authorizationHeader(token: String): String = "Bearer ${token.trim()}"

    private fun parseResponse(json: String): LabelCaptureResponse? =
        runCatching { gson.fromJson(json, LabelCaptureResponse::class.java) }.getOrNull()

    private fun copyAtomically(source: File, destination: File) {
        require(source.isFile) { "No se encontró la foto tomada." }
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            source.inputStream().use { input ->
                FileOutputStream(temporary).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            }
            check(temporary.renameTo(destination)) { "No se pudo guardar la foto de forma segura." }
        } finally {
            temporary.delete()
        }
    }

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(temporary.renameTo(destination)) { "No se pudo congelar la foto para enviarla." }
        } finally {
            temporary.delete()
        }
    }

    private fun deleteOwnedFile(path: String) {
        val directory = managedDirectory.canonicalFile
        val file = File(path).canonicalFile
        if (LabelCaptureSyncPolicy.isDirectChild(directory, file)) file.delete()
    }
}
