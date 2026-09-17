package com.example.pmmovil.data.scanner

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bus centralizado de eventos de escaneo.
 *
 * Unifica los 3 caminos por donde llega un código a la app:
 *  1. Lector físico integrado (Zebra / Honeywell / etc.) — vía dispatchKeyEvent
 *  2. Lector Bluetooth (HID keyboard) — mismo camino que el físico
 *  3. Cámara QR (ScannerScreen) — desde el FAB de escanear
 *
 * El `AsignacionesViewModel` se suscribe a este bus. Cuando llega un código:
 *  1. Lo limpia (trim + uppercase + primer token)
 *  2. Llama al backend (buscarPorCreferencia)
 *  3. Si existe → emite un evento de navegación para abrir el modal de modo
 *  4. Si no existe → errorMessage para que la UI muestre un snackbar
 *
 * Se usa un SharedFlow con buffer para no perder eventos si la pantalla
 * no está activa en el momento del scan.
 */
@Singleton
class ScanEventBus @Inject constructor() {

    private val _scans = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /** Stream de códigos escaneados. Replay = 0 para no re-disparar al re-suscribirse. */
    val scans: SharedFlow<String> = _scans.asSharedFlow()

    /**
     * Emite un código al bus. Cualquier ViewModel suscrito lo recibe.
     * Devuelve true si se enqueueó, false si el bus está cerrado.
     */
    fun emit(code: String) {
        _scans.tryEmit(code)
    }
}
