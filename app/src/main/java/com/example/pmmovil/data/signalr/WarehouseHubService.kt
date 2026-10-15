package com.example.pmmovil.data.signalr

import android.util.Log
import com.example.pmmovil.data.repository.AuthRepository
import com.microsoft.signalr.HubConnection
import com.microsoft.signalr.HubConnectionBuilder
import com.microsoft.signalr.HubConnectionState
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.core.Completable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

@Singleton
class WarehouseHubService @Inject constructor(
    private val authRepository: AuthRepository
) {
    private val stateLock = Any()
    private val sendMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var hubConnection: HubConnection? = null
    private var startJob: Job? = null
    private var reconnectJob: Job? = null
    private var shouldBeConnected = false

    private val _actividadFlow = MutableSharedFlow<ActividadMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val actividadFlow = _actividadFlow.asSharedFlow()

    private val _desbloqueoAprobadoFlow = MutableSharedFlow<DesbloqueoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val desbloqueoAprobadoFlow = _desbloqueoAprobadoFlow.asSharedFlow()

    private val _desbloqueoRechazadoFlow = MutableSharedFlow<DesbloqueoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val desbloqueoRechazadoFlow = _desbloqueoRechazadoFlow.asSharedFlow()

    private val _nuevaAsignacionFlow = MutableSharedFlow<AsignacionMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val nuevaAsignacionFlow = _nuevaAsignacionFlow.asSharedFlow()

    private val _asignacionRetiradaFlow = MutableSharedFlow<DesasignacionMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val asignacionRetiradaFlow = _asignacionRetiradaFlow.asSharedFlow()
    
    private val _revisionCompletadaFlow = MutableSharedFlow<CompletadaMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val revisionCompletadaFlow = _revisionCompletadaFlow.asSharedFlow()

    private val _revisionEstadoFlow = MutableSharedFlow<RevisionEstadoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val revisionEstadoFlow = _revisionEstadoFlow.asSharedFlow()

    private val _revisionActualizadaFlow = MutableSharedFlow<RevisionActualizadaMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val revisionActualizadaFlow = _revisionActualizadaFlow.asSharedFlow()

    private val _connectionReadyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val connectionReadyFlow = _connectionReadyFlow.asSharedFlow()

    private val _supervisorObservandoFlow = MutableSharedFlow<SupervisorObservandoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val supervisorObservandoFlow = _supervisorObservandoFlow.asSharedFlow()

    private val _cierreForzosoFlow = MutableSharedFlow<CierreForzosoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val cierreForzosoFlow = _cierreForzosoFlow.asSharedFlow()

    private val _preregistroCreadoFlow = MutableSharedFlow<PreregistroCreadoMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val preregistroCreadoFlow = _preregistroCreadoFlow.asSharedFlow()

    private val _preregistroArriboFlow = MutableSharedFlow<PreregistroArriboMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val preregistroArriboFlow = _preregistroArriboFlow.asSharedFlow()

    private val _preregistroUbicacionFlow = MutableSharedFlow<PreregistroUbicacionMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val preregistroUbicacionFlow = _preregistroUbicacionFlow.asSharedFlow()

    private val _preregistroAlertaFlow = MutableSharedFlow<PreregistroAlertaMessage>(extraBufferCapacity = EVENT_BUFFER_CAPACITY)
    val preregistroAlertaFlow = _preregistroAlertaFlow.asSharedFlow()

    fun isConnected(): Boolean {
        return hubConnection?.connectionState == HubConnectionState.CONNECTED
    }

    fun startConnection() {
        val connectionToStart: HubConnection
        synchronized(stateLock) {
            shouldBeConnected = true
            if (hubConnection?.connectionState == HubConnectionState.CONNECTED) return
            if (startJob?.isActive == true) return

            val token = authRepository.getToken() ?: run {
                shouldBeConnected = false
                return
            }
            val baseUrl = com.example.pmmovil.di.NetworkModule.BASE_URL
                .removeSuffix("api/")
                .removeSuffix("api") + "hubs/warehouse"

            connectionToStart = HubConnectionBuilder.create(baseUrl)
                // Se consulta el token en cada handshake para que una
                // reconexión no reutilice un JWT vencido.
                .withAccessTokenProvider(
                    Single.defer {
                        authRepository.getToken()?.let { currentToken -> Single.just(currentToken) }
                            ?: Single.error(IllegalStateException("Sesión sin token"))
                    }
                )
                .build()

            hubConnection = connectionToStart
            addListeners(connectionToStart)
            connectionToStart.onClosed {
                Log.w("SignalR", "WarehouseHub cerrado")
                synchronized(stateLock) {
                    if (hubConnection === connectionToStart) {
                        hubConnection = null
                    }
                }
                scheduleReconnect()
            }

            startJob = scope.launch {
                try {
                    awaitCompletion(connectionToStart.start())
                    synchronized(stateLock) {
                        if (hubConnection !== connectionToStart || !shouldBeConnected) return@launch
                    }
                    Log.d("SignalR", "Connected to WarehouseHub")
                    authRepository.getUsername()?.takeIf { it.isNotBlank() }?.let { user ->
                        runCatching {
                            // Van los DOS argumentos a proposito. El hub declara
                            // UnirseASesion(usuario, recibeBloqueos = false), y SignalR
                            // enlaza por posicion y cantidad: el valor por omision de C#
                            // no cubre un argumento que el cliente nunca mando, asi que
                            // mandar solo el usuario hacia fallar la invocacion.
                            //
                            // false porque el grupo de bloqueos es para el panel de
                            // supervisores; el movil no lo escucha.
                            sendNow(connectionToStart, "UnirseASesion", user, false)
                        }.onFailure {
                            // Igual que las otras dos suscripciones: no poder unirse a
                            // un grupo NO es un fallo de conexion. Sin este runCatching
                            // la excepcion caia al catch de abajo, que llamaba a
                            // scheduleReconnect(): conectaba bien, volvia a fallar el
                            // join, reconectaba... 8,780 negotiate medidos en un rato,
                            // ~8 por segundo. Se registra como advertencia, no como
                            // dato de depuracion, porque aqui si significa que el
                            // usuario no recibira sus eventos dirigidos.
                            Log.w("SignalR", "No se pudo unir a la sesion del usuario", it)
                        }
                    }
                    runCatching {
                        sendNow(connectionToStart, "UnirseARevision")
                    }.onFailure {
                        Log.d("SignalR", "La cuenta no se suscribió a revisión")
                    }
                    runCatching {
                        sendNow(connectionToStart, "UnirseAPreregistro")
                    }.onFailure {
                        // El hub es compartido por todos los modulos. Solo
                        // caseta y trafico deben recibir estos eventos; una
                        // cuenta sin ese permiso no convierte esto en un
                        // fallo de conexion.
                        Log.d("SignalR", "La cuenta no se suscribio a pre-registros")
                    }
                    // Se refresca también en la primera conexión: la carga
                    // inicial puede haber terminado antes de que el grupo
                    // de revisión quedara unido.
                    _connectionReadyFlow.tryEmit(Unit)
                    reconnectJob?.cancel()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.e("SignalR", "Error connecting", error)
                    synchronized(stateLock) {
                        if (hubConnection === connectionToStart) hubConnection = null
                    }
                    scheduleReconnect()
                } finally {
                    synchronized(stateLock) {
                        if (startJob === coroutineContext[Job]) startJob = null
                    }
                }
            }
        }
    }

    private fun addListeners(connection: HubConnection) {
        connection.apply {
            on("onActividadRecibida", { cref: String, usr: String, cno: String, itm: String ->
                _actividadFlow.tryEmit(ActividadMessage(cref, usr, cno, itm))
            }, String::class.java, String::class.java, String::class.java, String::class.java)

            on("onDesbloqueoAprobado", { msg: DesbloqueoMessage ->
                _desbloqueoAprobadoFlow.tryEmit(msg)
            }, DesbloqueoMessage::class.java)

            on("onDesbloqueoRechazado", { msg: DesbloqueoMessage ->
                _desbloqueoRechazadoFlow.tryEmit(msg)
            }, DesbloqueoMessage::class.java)

            on("onRevisionAsignada", { msg: AsignacionMessage ->
                _nuevaAsignacionFlow.tryEmit(msg)
            }, AsignacionMessage::class.java)

            on("onRevisionDesasignada", { msg: DesasignacionMessage ->
                _asignacionRetiradaFlow.tryEmit(msg)
            }, DesasignacionMessage::class.java)
            
            on("onRevisionCompletada", { msg: CompletadaMessage ->
                _revisionCompletadaFlow.tryEmit(msg)
            }, CompletadaMessage::class.java)

            on("onRevisionEstadoCambiado", { msg: RevisionEstadoMessage ->
                _revisionEstadoFlow.tryEmit(msg)
            }, RevisionEstadoMessage::class.java)

            on("onRevisionActualizada", { msg: RevisionActualizadaMessage ->
                _revisionActualizadaFlow.tryEmit(msg)
            }, RevisionActualizadaMessage::class.java)

            on("onSupervisorObservando", { msg: SupervisorObservandoMessage ->
                _supervisorObservandoFlow.tryEmit(msg)
            }, SupervisorObservandoMessage::class.java)

            on("onCierreForzoso", { msg: CierreForzosoMessage ->
                // El monitor transmite el evento a todos los clientes
                // conectados para no depender de que el revisor siga dentro
                // del grupo de la entrada. El app solo debe reaccionar si el
                // cierre corresponde al usuario autenticado.
                val currentUser = authRepository.getUsername()
                val isForCurrentUser = msg.usuario.isNullOrBlank() ||
                    msg.usuario.equals(currentUser, ignoreCase = true)
                if (isForCurrentUser) {
                    _cierreForzosoFlow.tryEmit(msg)
                }
            }, CierreForzosoMessage::class.java)

            on("onPreregistroCreado", { msg: PreregistroCreadoMessage ->
                _preregistroCreadoFlow.tryEmit(msg)
            }, PreregistroCreadoMessage::class.java)

            on("onPreregistroArribo", { msg: PreregistroArriboMessage ->
                _preregistroArriboFlow.tryEmit(msg)
            }, PreregistroArriboMessage::class.java)

            on("onPreregistroUbicacion", { msg: PreregistroUbicacionMessage ->
                _preregistroUbicacionFlow.tryEmit(msg)
            }, PreregistroUbicacionMessage::class.java)

            on("onPreregistroAlerta", { msg: PreregistroAlertaMessage ->
                _preregistroAlertaFlow.tryEmit(msg)
            }, PreregistroAlertaMessage::class.java)
        }
    }

    fun registrarActividad(cref: String, user: String, cnofact: String, item: String = "") {
        send("RegistrarActividad", cref, user, cnofact, item)
    }

    fun registrarPresencia(cref: String, user: String) {
        send("RegistrarPresencia", cref, user)
    }

    /**
     * Avisa al Monitor si el revisor va a tomar la asignación ahora o si se
     * queda en espera. El panel web ya lo hace (warehouse-hub.service.ts);
     * la app nunca lo invocaba, así que la columna "Asignaciones abiertas"
     * del Monitor se quedaba en "Pendiente de respuesta" para siempre aunque
     * el revisor ya la hubiera abierto.
     */
    fun informarDecisionAsignacion(crefAgrupada: String, usuario: String, decision: String) {
        send("InformarDecisionAsignacion", crefAgrupada, usuario, decision)
    }

    fun stopConnection() {
        val connectionToStop: HubConnection?
        synchronized(stateLock) {
            shouldBeConnected = false
            reconnectJob?.cancel()
            reconnectJob = null
            startJob?.cancel()
            startJob = null
            connectionToStop = hubConnection
            hubConnection = null
        }
        connectionToStop?.let { connection ->
            scope.launch {
                runCatching { awaitCompletion(connection.stop()) }
                    .onFailure { Log.w("SignalR", "Error cerrando WarehouseHub", it) }
            }
        }
    }

    private fun send(methodName: String, vararg args: Any) {
        scope.launch {
            repeat(MAX_SEND_ATTEMPTS) { attempt ->
                if (!isWanted()) return@launch
                val connection = waitForConnected() ?: run {
                    Log.w("SignalR", "No se pudo enviar $methodName: conexión no disponible")
                    return@launch
                }
                try {
                    sendMutex.withLock {
                        if (connection.connectionState != HubConnectionState.CONNECTED) {
                            throw IllegalStateException("WarehouseHub dejó de estar conectado")
                        }
                        awaitCompletion(connection.invoke(methodName, *args))
                    }
                    return@launch
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    Log.w(
                        "SignalR",
                        "Error enviando $methodName (intento ${attempt + 1}/$MAX_SEND_ATTEMPTS)",
                        error
                    )
                    if (attempt < MAX_SEND_ATTEMPTS - 1) {
                        delay(SEND_RETRY_DELAY_MILLIS * (attempt + 1))
                    }
                }
            }
        }
    }

    private suspend fun waitForConnected(): HubConnection? {
        val deadline = System.currentTimeMillis() + CONNECT_WAIT_TIMEOUT_MILLIS
        while (isWanted() && System.currentTimeMillis() < deadline) {
            hubConnection?.takeIf { it.connectionState == HubConnectionState.CONNECTED }?.let {
                return it
            }
            startConnection()
            delay(CONNECTION_POLL_DELAY_MILLIS)
        }
        return hubConnection?.takeIf { it.connectionState == HubConnectionState.CONNECTED }
    }

    private fun scheduleReconnect() {
        synchronized(stateLock) {
            if (!shouldBeConnected || reconnectJob?.isActive == true) return
            reconnectJob = scope.launch {
                var waitMillis = INITIAL_RECONNECT_DELAY_MILLIS
                while (isActive && isWanted()) {
                    delay(waitMillis)
                    if (isConnected()) break
                    startConnection()
                    waitMillis = min(waitMillis * 2, MAX_RECONNECT_DELAY_MILLIS)
                }
                synchronized(stateLock) { reconnectJob = null }
            }
        }
    }

    private fun isWanted(): Boolean = synchronized(stateLock) { shouldBeConnected }

    private suspend fun sendNow(connection: HubConnection, methodName: String, vararg args: Any) {
        sendMutex.withLock {
            awaitCompletion(connection.invoke(methodName, *args))
        }
    }

    private suspend fun awaitCompletion(completable: Completable) {
        suspendCancellableCoroutine { continuation ->
            val disposable = completable.subscribe(
                { continuation.resume(Unit) },
                { error -> continuation.resumeWithException(error) }
            )
            continuation.invokeOnCancellation { disposable.dispose() }
        }
    }

    private companion object {
        const val EVENT_BUFFER_CAPACITY = 64
        const val MAX_SEND_ATTEMPTS = 3
        const val SEND_RETRY_DELAY_MILLIS = 500L
        const val CONNECTION_POLL_DELAY_MILLIS = 250L
        const val CONNECT_WAIT_TIMEOUT_MILLIS = 8_000L
        const val INITIAL_RECONNECT_DELAY_MILLIS = 1_000L
        const val MAX_RECONNECT_DELAY_MILLIS = 30_000L
    }
}
