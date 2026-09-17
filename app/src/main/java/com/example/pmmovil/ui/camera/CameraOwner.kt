package com.example.pmmovil.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import com.example.pmmovil.data.camera.CameraTrace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Dueño único de la cámara del proceso.
 *
 * Tres reglas, y ninguna más:
 *
 * 1. **Los cambios de dueño van en fila.** Un mutex garantiza que nunca haya
 *    dos pantallas enlazando a la vez; se recuerda qué use cases enlazó cada
 *    quien para soltar sólo lo propio. `unbindAll()` no aparece aquí a
 *    propósito: soltaba también los use cases de la pantalla entrante y
 *    producía un carrusel de aperturas en el login.
 *
 * 2. **No se reabre UN DISPOSITIVO mientras su propio cierre anterior sigue
 *    en vuelo.** Antes esto se resolvía con un temporizador fijo de 1 s, y
 *    ese segundo caía exactamente sobre el final del cierre asíncrono del
 *    dispositivo: la apertura llegaba 12 ms después de que el HAL de
 *    MediaTek terminara de cerrar, y una de cada diez veces llegaba ANTES,
 *    con lo que el HAL aceptaba la sesión pero no entregaba ni un cuadro.
 *    Ahora se observa el cierre real con `CameraManager.AvailabilityCallback`
 *    (ver [CameraDeviceLedger]) y se abre en cuanto Android confirma que ESE
 *    dispositivo quedó libre: normalmente ~100 ms, y cero cuando la pantalla
 *    anterior cerró hace rato. El bloqueo es por dispositivo, no global: el
 *    relevo rostro (cámara frontal) → QR (trasera) del login no tiene que
 *    esperar el cierre de la frontal, son sensores independientes. Con
 *    [CameraXSetup] el cierre es inmediato al desenlazar, así que nunca hay
 *    una ventana ambigua de "abierta pero sin dueño".
 *
 * 3. **Soltar es condicional.** Cuando dos pantallas se turnan, la que sale
 *    suele soltar DESPUÉS de que la que entra ya tomó la cámara (Compose no
 *    garantiza el orden). [release] sólo actúa si [tag] sigue siendo el dueño.
 */
object CameraOwner {

    private val mutex = Mutex()

    /**
     * El precalentado va en IO, NO en Main. Medido en la Armor Pad Pro:
     * inicializar CameraX cuesta ~3 s de trabajo real (enumerar cámaras y
     * leer sus características); en el hilo principal el arranque pasaba de
     * 0.6 s a 5 s.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var provider: ProcessCameraProvider? = null

    /** Quién tiene la cámara ahora mismo, para la traza. */
    private var duenoActual: String? = null

    /** Lo que enlazó el dueño actual. Es lo único que se le puede soltar. */
    private var useCasesActuales: List<UseCase> = emptyList()

    /** Id Camera2 del dispositivo que enlazó el dueño actual. */
    private var cameraIdActual: String? = null

    private val ledger = CameraDeviceLedger()
    private val ledgerLock = Any()

    /** Cambia con cada aviso de disponibilidad; despierta a quien espera. */
    private val ledgerVersion = MutableStateFlow(0L)

    @Volatile
    private var availabilityRegistered = false

    /**
     * Adelanta la inicialización de CameraX al arranque de la app y registra
     * el observador de disponibilidad de dispositivos.
     *
     * Es seguro llamarlo siempre: si CameraX ya estaba listo, no hace nada, y
     * si el equipo no tiene cámara, el fallo se traga aquí en vez de tumbar el
     * arranque.
     */
    fun prewarm(context: Context) {
        val appContext = context.applicationContext
        registerAvailability(appContext)
        if (provider != null) return
        scope.launch {
            val inicio = SystemClock.elapsedRealtime()
            runCatching { obtenerProveedor(appContext) }
                .onSuccess {
                    CameraTrace.d(
                        "CameraOwner: CameraX listo en ${SystemClock.elapsedRealtime() - inicio}ms (prewarm)"
                    )
                }
                .onFailure { CameraTrace.w("CameraOwner: prewarm falló: ${it.message}") }
        }
    }

    private fun registerAvailability(context: Context) {
        if (availabilityRegistered) return
        synchronized(ledgerLock) {
            if (availabilityRegistered) return
            availabilityRegistered = true
        }
        runCatching {
            val manager = context.getSystemService(CameraManager::class.java) ?: return
            val thread = HandlerThread("pmm-camera-availability").apply { start() }
            manager.registerAvailabilityCallback(
                object : CameraManager.AvailabilityCallback() {
                    override fun onCameraAvailable(cameraId: String) {
                        synchronized(ledgerLock) { ledger.onAvailable(cameraId) }
                        CameraTrace.d("CameraOwner: camara $cameraId disponible")
                        ledgerVersion.value += 1
                    }

                    override fun onCameraUnavailable(cameraId: String) {
                        synchronized(ledgerLock) { ledger.onUnavailable(cameraId) }
                        CameraTrace.d("CameraOwner: camara $cameraId ocupada")
                        ledgerVersion.value += 1
                    }
                },
                Handler(thread.looper)
            )
        }.onFailure {
            CameraTrace.w("CameraOwner: no se pudo observar la disponibilidad de camaras: ${it.message}")
        }
    }

    /**
     * El `ProcessCameraProvider`, esperando a que CameraX termine de arrancar
     * si hiciera falta. Se cachea porque `getInstance()` devuelve siempre el
     * mismo, pero pasar por el future cada vez cuesta un salto de hilo.
     */
    suspend fun obtenerProveedor(context: Context): ProcessCameraProvider {
        provider?.let { return it }
        val obtenido = esperarProveedor(context.applicationContext)
        provider = obtenido
        return obtenido
    }

    private suspend fun esperarProveedor(context: Context): ProcessCameraProvider =
        suspendCancellableCoroutine { continuacion ->
            val futuro = ProcessCameraProvider.getInstance(context)
            val ejecutor = Executor { it.run() }
            futuro.addListener({
                try {
                    continuacion.resume(futuro.get())
                } catch (e: Exception) {
                    continuacion.resumeWithException(e)
                }
            }, ejecutor)
        }

    /**
     * Toma la cámara para [tag] y enlaza [useCases].
     *
     * Antes de enlazar suelta lo del dueño anterior y espera a que el
     * dispositivo quede realmente libre. [onBeforeBind] corre con la cámara
     * ya libre y justo antes del enlace: es donde va el `warmup.onBound()`,
     * para que el reloj del calentamiento empiece a contar cuando de verdad
     * empieza la apertura.
     *
     * Devuelve null si el enlace falló; el motivo va en la traza y en
     * [onError], porque cada pantalla lo enseña a su manera.
     */
    suspend fun bind(
        tag: String,
        context: Context,
        lifecycleOwner: LifecycleOwner,
        selector: CameraSelector,
        useCases: List<UseCase>,
        onBeforeBind: () -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ): Camera? = mutex.withLock {
        val pedido = SystemClock.elapsedRealtime()
        val proveedor = runCatching { obtenerProveedor(context) }
            .getOrElse {
                CameraTrace.w("[$tag] no se pudo obtener el proveedor: ${it.message}")
                onError(it)
                return@withLock null
            }

        soltarDuenoActual(proveedor, "cede a $tag")

        val cameraId = resolverCameraId(proveedor, selector)
        val esperado = esperarDispositivoLibre(tag, cameraId)

        onBeforeBind()

        return@withLock runCatching {
            proveedor.bindToLifecycle(
                lifecycleOwner,
                selector,
                *useCases.toTypedArray()
            )
        }.onSuccess {
            duenoActual = tag
            useCasesActuales = useCases
            cameraIdActual = cameraId
            CameraTrace.d(
                "[$tag] cámara ${cameraId ?: "?"} tomada (${useCases.size} use cases," +
                    " espero ${esperado}ms, ${SystemClock.elapsedRealtime() - pedido}ms desde la peticion)"
            )
        }.onFailure {
            // Que falle el enlace no puede dejar el registro mintiendo: si se
            // quedara apuntando a este tag, el siguiente en pedir la cámara
            // intentaría soltar use cases que nunca llegaron a enlazarse.
            duenoActual = null
            useCasesActuales = emptyList()
            cameraIdActual = null
            CameraTrace.w("[$tag] no se pudo enlazar la cámara: ${it.message}")
            onError(it)
        }.getOrNull()
    }

    /**
     * Suelta la cámara si [tag] todavía la tiene. Ver la regla 3 arriba.
     *
     * Pensado para llamarse desde el `finally` de la corrutina que enlazó,
     * envuelto en `withContext(NonCancellable)`: una corrutina ya cancelada
     * no puede suspender en el mutex, y sin ese envoltorio la cámara se
     * quedaría tomada. [releaseAsync] ya lo hace por ti.
     */
    suspend fun release(tag: String) = mutex.withLock {
        if (duenoActual != tag) {
            CameraTrace.d("[$tag] soltar ignorado: la cámara ya es de ${duenoActual ?: "nadie"}")
            return@withLock
        }
        val proveedor = provider ?: return@withLock
        soltarDuenoActual(proveedor, "$tag suelta")
    }

    /**
     * [release] para los sitios que no tienen corrutina propia (`onDispose`).
     * Corre en el hilo principal, que es donde CameraX quiere que se desenlace.
     */
    fun releaseAsync(tag: String) {
        mainScope.launch { withContext(NonCancellable) { release(tag) } }
    }

    private fun soltarDuenoActual(proveedor: ProcessCameraProvider, motivo: String) {
        val previos = useCasesActuales
        val cameraId = cameraIdActual
        if (previos.isEmpty()) {
            duenoActual = null
            cameraIdActual = null
            return
        }
        // Sólo los suyos. Nunca unbindAll(): ver el encabezado de la clase.
        runCatching { proveedor.unbind(*previos.toTypedArray()) }
            .onFailure { CameraTrace.w("Fallo al soltar la cámara ($motivo): ${it.message}") }
        CameraTrace.d("Cámara ${cameraId ?: "?"} liberada por ${duenoActual ?: "?"} ($motivo)")
        duenoActual = null
        useCasesActuales = emptyList()
        cameraIdActual = null
        // Nada que tocar en el libro aquí: el dispositivo sigue "ocupado"
        // hasta que Android mismo avise onCameraAvailable cuando el HAL
        // termine de cerrarlo (ver CameraDeviceLedger). Esa es la señal, no
        // el momento en que nosotros llamamos a unbind().
    }

    /**
     * Espera a que [cameraId] quede realmente libre. Devuelve cuánto esperó,
     * para la traza.
     *
     * El bloqueo es SOLO sobre ese dispositivo concreto -- ver el porqué en
     * [CameraDeviceLedger]. Si el aviso de Android no llega en
     * [DEVICE_CLOSE_TIMEOUT_MS] se sigue adelante de todos modos (puede que
     * otra app tenga la cámara: el enlace fallará y la pantalla lo mostrará)
     * y se olvida ese cierre pendiente para no volver a esperarlo en la
     * siguiente apertura de ese mismo dispositivo.
     */
    private suspend fun esperarDispositivoLibre(tag: String, cameraId: String?): Long {
        if (!availabilityRegistered) return 0L
        if (synchronized(ledgerLock) { ledger.readyToOpen(cameraId) }) return 0L

        val inicio = SystemClock.elapsedRealtime()
        CameraTrace.d(
            "[$tag] esperando a que el HAL suelte (${synchronized(ledgerLock) { ledger.describeBlockers(cameraId) }})"
        )
        val libre = withTimeoutOrNull(DEVICE_CLOSE_TIMEOUT_MS) {
            ledgerVersion.first { synchronized(ledgerLock) { ledger.readyToOpen(cameraId) } }
        } != null
        val esperado = SystemClock.elapsedRealtime() - inicio
        if (!libre) {
            CameraTrace.w(
                "[$tag] Android no confirmo el cierre de ${cameraId ?: "?"} en ${esperado}ms; se abre igual"
            )
            synchronized(ledgerLock) { ledger.forget(cameraId) }
        }
        return esperado
    }

    @SuppressLint("UnsafeOptInUsageError")
    @OptIn(ExperimentalCamera2Interop::class)
    private fun resolverCameraId(proveedor: ProcessCameraProvider, selector: CameraSelector): String? =
        runCatching {
            val info = selector.filter(proveedor.availableCameraInfos).firstOrNull() ?: return null
            Camera2CameraInfo.from(info).cameraId
        }.getOrNull()

    /**
     * Cota superior para la confirmación del cierre. El cierre real en la
     * Armor Pad Pro tarda ~110 ms; esto sólo actúa si Android no avisa.
     */
    private const val DEVICE_CLOSE_TIMEOUT_MS = 2_000L
}
