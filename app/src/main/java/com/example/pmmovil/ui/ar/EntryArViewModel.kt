package com.example.pmmovil.ui.ar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pmmovil.data.entryar.ArCodeResolver
import com.example.pmmovil.data.entryar.ArItemKind
import com.example.pmmovil.data.entryar.EntryArQrCode
import com.example.pmmovil.data.model.EntryArCardDto
import com.example.pmmovil.data.model.TraceCompactItemDto
import com.example.pmmovil.data.repository.EntryArRepository
import com.example.pmmovil.data.repository.TraceCompactRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EntryArOverlayItem(
    val key: String,
    val code: EntryArQrCode,
    val card: EntryArCardDto,
    /** Qué clase de QR es: una entrada usa [card]; una caja o una relación usan [compact]. */
    val kind: ArItemKind = ArItemKind.Entry,
    /** La ficha compacta de una caja o una relación; `null` mientras llega o si no se pudo consultar. */
    val compact: TraceCompactItemDto? = null,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val fromCache: Boolean = false,
    val isPrimary: Boolean = false,
    val hasProjectedPosition: Boolean = false
)

data class EntryArUiState(
    val items: List<EntryArOverlayItem> = emptyList(),
    val isLoading: Boolean = false,
    val isOffline: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class EntryArViewModel @Inject constructor(
    private val repository: EntryArRepository,
    private val compactRepository: TraceCompactRepository
) : ViewModel() {

    private val mutableState = MutableStateFlow(EntryArUiState())
    val state: StateFlow<EntryArUiState> = mutableState.asStateFlow()

    private var refreshJob: Job? = null

    /** Sube con cada consulta lanzada: una respuesta vieja que llega tarde no debe pisar a la nueva. */
    private var requestSeq = 0
    private val rejectedCodes = mutableSetOf<String>()
    private val ignoredAfterClear = mutableSetOf<String>()
    private val lastSeenAt = mutableMapOf<String, Long>()
    private val lastObservedAt = mutableMapOf<String, Long>()

    /** Cuándo se leyó cada QR en la imagen por última vez (no confundir con «visto por el ancla»). */
    private val lastDirectDetectionAt = mutableMapOf<String, Long>()
    private var primaryKey: String? = null
    private var pendingPrimaryKey: String? = null
    private var pendingPrimarySince = 0L

    init {
        viewModelScope.launch {
            while (true) {
                delay(QR_VISIBILITY_CHECK_INTERVAL_MS)
                removeInvisibleItems(System.currentTimeMillis())
            }
        }
    }

    fun onQrSeen(rawValue: String, x: Float, y: Float) {
        updateQr(rawValue, x, y, spatial = false)
    }

    fun onSpatialQrSeen(rawValue: String, x: Float, y: Float) {
        updateQr(rawValue, x, y, spatial = true)
    }

    /** Actualiza presencia sin competir con la proyección espacial del ancla. */
    fun onSpatialQrObserved(normalizedCode: String) {
        val now = System.currentTimeMillis()
        if (isIgnoredAfterClear(normalizedCode, now)) return
        if (mutableState.value.items.any { it.key == normalizedCode }) {
            lastObservedAt[normalizedCode] = now
        }
    }

    private fun updateQr(rawValue: String, x: Float, y: Float, spatial: Boolean) {
        // Entrada, caja o relación; todo lo demás (un gafete incluido) se descarta aquí.
        val resolved = ArCodeResolver.resolve(rawValue) ?: return
        val code = resolved.code
        val now = System.currentTimeMillis()
        if (isIgnoredAfterClear(code.normalized, now)) return
        val currentItems = mutableState.value.items
        val existing = currentItems.firstOrNull { it.key == code.normalized }
        if (existing == null && currentItems.size >= MAX_VISIBLE_QR) {
            if (rejectedCodes.add(code.normalized)) {
                mutableState.value = mutableState.value.copy(
                    message = "Ya hay seis QR anclados. Limpia las anclas para leer otros."
                )
            }
            return
        }
        lastObservedAt[code.normalized] = now
        lastDirectDetectionAt[code.normalized] = now

        val updatedItems = if (existing == null) {
                currentItems + EntryArOverlayItem(
                    key = code.normalized,
                    code = code,
                    card = EntryArCardDto(reference = code.reference, exists = false),
                    kind = resolved.kind,
                    x = x.coerceIn(0f, 1f),
                    y = y.coerceIn(0f, 1f)
                )
            } else {
                currentItems.map { item ->
                    if (item.key == code.normalized) {
                        // La lectura real del QR manda siempre: es lo único que no es una suposición.
                        item.copy(
                            x = filterPosition(item.x, x.coerceIn(0f, 1f)),
                            y = filterPosition(item.y, y.coerceIn(0f, 1f))
                        )
                    } else item
                }
            }
        val offlineMessage = mutableState.value.takeIf { it.isOffline }?.message
        mutableState.value = mutableState.value.copy(
            items = markPrimary(updatedItems),
            // Cada cuadro de la cámara pasa por aquí: si borrara el aviso de «Sin conexión», el banner quedaba encendido
            // y vacío al instante. Ese aviso solo lo cambia el resultado de una consulta.
            message = offlineMessage
        )

        if (existing == null) scheduleRefresh()
    }

    /**
     * Posición que sale de proyectar el ancla 3D. **Solo manda cuando el QR no se está viendo**: si la cámara lo tiene
     * delante, la lectura de la imagen es exacta y el ancla puede ser una suposición (cuando el hit-test falla se coloca
     * a 1.5 m a ojo, y entonces la proyección se desplaza al moverse).
     */
    fun updateProjectedPosition(key: String, x: Float, y: Float) {
        val current = mutableState.value
        val item = current.items.firstOrNull { it.key == key } ?: return
        val detectadoHaceNada = lastDirectDetectionAt[key]?.let {
            System.currentTimeMillis() - it < DETECTION_WINS_MS
        } ?: false
        if (detectadoHaceNada) return
        val safeX = x.coerceIn(0f, 1f)
        val safeY = y.coerceIn(0f, 1f)
        val filteredX = if (item.hasProjectedPosition) filterPosition(item.x, safeX) else safeX
        val filteredY = if (item.hasProjectedPosition) filterPosition(item.y, safeY) else safeY
        if (
            item.hasProjectedPosition &&
            kotlin.math.abs(item.x - filteredX) < MIN_POSITION_UPDATE &&
            kotlin.math.abs(item.y - filteredY) < MIN_POSITION_UPDATE
        ) return
        val updatedItems = current.items.map {
                if (it.key == key) {
                    it.copy(x = filteredX, y = filteredY, hasProjectedPosition = true)
                } else it
            }
        mutableState.value = current.copy(
            items = markPrimary(updatedItems)
        )
    }

    fun clear() {
        refreshJob?.cancel()
        val now = System.currentTimeMillis()
        mutableState.value.items.forEach { item ->
            ignoredAfterClear.add(item.key)
            lastSeenAt[item.key] = now
        }
        lastObservedAt.clear()
        rejectedCodes.clear()
        primaryKey = null
        pendingPrimaryKey = null
        pendingPrimarySince = 0L
        mutableState.value = EntryArUiState()
    }

    private fun isIgnoredAfterClear(key: String, now: Long): Boolean {
        if (key !in ignoredAfterClear) return false
        val previousSeenAt = lastSeenAt.put(key, now)
        if (previousSeenAt == null || now - previousSeenAt > CLEAR_RESCAN_DELAY_MS) {
            ignoredAfterClear.remove(key)
            lastSeenAt.remove(key)
            return false
        }
        return true
    }

    private fun filterPosition(current: Float, target: Float): Float {
        val delta = target - current
        val distance = kotlin.math.abs(delta)
        if (distance < POSITION_DEADBAND) return current
        val alpha = when {
            distance > FAST_POSITION_THRESHOLD -> FAST_POSITION_FILTER_ALPHA
            distance > MEDIUM_POSITION_THRESHOLD -> MEDIUM_POSITION_FILTER_ALPHA
            else -> POSITION_FILTER_ALPHA
        }
        return current + delta * alpha
    }

    private fun removeInvisibleItems(now: Long) {
        val expiredKeys = lastObservedAt.entries
            .asSequence()
            .filter { (_, observedAt) -> now - observedAt > QR_VISIBILITY_TIMEOUT_MS }
            .map { it.key }
            .toSet()
        if (expiredKeys.isEmpty()) return

        expiredKeys.forEach { key -> lastObservedAt.remove(key) }
        val current = mutableState.value
        val visibleItems = current.items.filterNot { it.key in expiredKeys }
        if (visibleItems.size == current.items.size) return
        if (visibleItems.isEmpty()) {
            refreshJob?.cancel()
            primaryKey = null
            pendingPrimaryKey = null
            pendingPrimarySince = 0L
            mutableState.value = EntryArUiState()
            return
        }
        mutableState.value = current.copy(items = markPrimary(visibleItems))
    }

    /**
     * Espera a que dejen de aparecer QR nuevos y entonces consulta. **La espera se cancela; la consulta no.**
     *
     * Antes era un solo trabajo y `refreshJob?.cancel()` mataba la petición que ya iba en camino. OkHttp la cerraba con
     * `IOException: Canceled`, eso caía en la rama de error y la pantalla anunciaba «Sin conexión» teniendo red.
     * Medido en el Pixel el 22-oct: **6 peticiones canceladas contra 5 buenas** en un minuto de uso.
     */
    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(350)
            consultar()
        }
    }

    private fun consultar() {
        val visible = mutableState.value.items
        val references = visible.filter { it.kind == ArItemKind.Entry }
            .map { it.code.reference }.distinct().take(MAX_VISIBLE_QR)
        val compactCodes = visible.filter { it.kind != ArItemKind.Entry }
            .map { it.code.normalized }.distinct().take(MAX_VISIBLE_QR)
        if (references.isEmpty() && compactCodes.isEmpty()) return
        val seq = ++requestSeq
        // Trabajo propio: ningún refresco posterior lo cancela. Si llega otra consulta más nueva, esta se descarta sola.
        viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)

            // Las dos consultas salen a la vez y cada una se resuelve por su cuenta: que falle la de cajas no deja sin
            // tarjeta a las entradas ni al revés.
            val entriesCall = async { if (references.isEmpty()) null else repository.getCards(references) }
            val compactCall = async { if (compactCodes.isEmpty()) null else compactRepository.get(compactCodes) }
            val entriesResult = entriesCall.await()
            val compactResult = compactCall.await()
            if (seq != requestSeq) return@launch

            val entries = when {
                entriesResult == null -> ArLookup<EntryArCardDto>()
                entriesResult.isSuccess -> ArLookup(
                    entriesResult.getOrThrow().associateBy { it.reference },
                    ArFetchOutcome.Fresh
                )
                else -> ArLookup(
                    repository.getCachedCards(references).associateBy { it.reference },
                    ArFetchOutcome.Offline
                )
            }
            val compact = when {
                compactResult == null -> ArLookup<TraceCompactItemDto>()
                compactResult.isSuccess -> ArLookup(compactResult.getOrThrow(), ArFetchOutcome.Fresh)
                else -> ArLookup(compactRepository.latest(compactCodes), ArFetchOutcome.Offline)
            }

            val message = arOfflineMessage(entries, compact)
            mutableState.value = mutableState.value.copy(
                items = mergeArItems(mutableState.value.items, entries, compact),
                isLoading = false,
                isOffline = message != null,
                message = message
            )
        }
    }

    private fun markPrimary(items: List<EntryArOverlayItem>): List<EntryArOverlayItem> {
        val nearestKey = items.minByOrNull { item ->
            val dx = item.x - 0.5f
            val dy = item.y - 0.5f
            dx * dx + dy * dy
        }?.key
        if (nearestKey == null) {
            primaryKey = null
            pendingPrimaryKey = null
            pendingPrimarySince = 0L
            return emptyList()
        }

        val currentPrimary = items.firstOrNull { it.key == primaryKey }
        if (currentPrimary == null) {
            primaryKey = nearestKey
            pendingPrimaryKey = null
            pendingPrimarySince = 0L
        } else if (nearestKey == primaryKey) {
            pendingPrimaryKey = null
            pendingPrimarySince = 0L
        } else {
            val candidate = items.first { it.key == nearestKey }
            val currentDistance = distanceToCenter(currentPrimary)
            val candidateDistance = distanceToCenter(candidate)
            if (currentDistance - candidateDistance >= PRIMARY_SWITCH_MARGIN) {
                if (pendingPrimaryKey != nearestKey) {
                    pendingPrimaryKey = nearestKey
                    pendingPrimarySince = System.currentTimeMillis()
                } else if (System.currentTimeMillis() - pendingPrimarySince >= PRIMARY_SWITCH_HOLD_MS) {
                    primaryKey = nearestKey
                    pendingPrimaryKey = null
                    pendingPrimarySince = 0L
                }
            } else {
                pendingPrimaryKey = null
                pendingPrimarySince = 0L
            }
        }

        return items.map { it.copy(isPrimary = it.key == primaryKey) }
    }

    private fun distanceToCenter(item: EntryArOverlayItem): Float {
        val dx = item.x - 0.5f
        val dy = item.y - 0.5f
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private companion object {
        const val MAX_VISIBLE_QR = 6
        const val CLEAR_RESCAN_DELAY_MS = 2_500L
        const val QR_VISIBILITY_CHECK_INTERVAL_MS = 300L
        const val QR_VISIBILITY_TIMEOUT_MS = 1_000L

        /** Tras leer un QR en la imagen, la proyección del ancla no toca su posición durante este rato. */
        const val DETECTION_WINS_MS = 500L
        const val POSITION_DEADBAND = 0.0025f
        const val POSITION_FILTER_ALPHA = 0.18f
        const val MEDIUM_POSITION_FILTER_ALPHA = 0.38f
        const val FAST_POSITION_FILTER_ALPHA = 0.65f
        const val MEDIUM_POSITION_THRESHOLD = 0.035f
        const val FAST_POSITION_THRESHOLD = 0.08f
        const val MIN_POSITION_UPDATE = 0.0007f
        const val PRIMARY_SWITCH_MARGIN = 0.035f
        const val PRIMARY_SWITCH_HOLD_MS = 450L
    }
}
