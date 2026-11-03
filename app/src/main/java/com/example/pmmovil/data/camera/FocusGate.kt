package com.example.pmmovil.data.camera

import android.hardware.camera2.CaptureResult

/**
 * Reglas de la espera de enfoque antes de disparar.
 *
 * El modo de captura rapido (MINIMIZE_LATENCY) dispara a los ~130 ms del toque porque se salta la secuencia de enfoque
 * y exposicion que hace MAXIMIZE_QUALITY (387 a 1345 ms en el Pixel). Pero eso lo deja disparar con el lente todavia
 * moviendose a enfocar, y en una etiqueta de cerca sale borrosa. El enfoque continuo ya trabaja en el visor: si el
 * estado ya esta asentado se dispara en el acto, y solo si el lente sigue buscando se espera, con tope.
 */
object FocusGatePolicy {
    /** Lo mas que se espera a que el lente deje de buscar. Pasado esto se dispara igual: mejor una foto que ninguna. */
    const val MAX_WAIT_MS = 450L

    /** Cada cuanto se vuelve a mirar el estado mientras se espera. */
    const val POLL_MS = 30L

    /**
     * El resultado de la vista previa mas viejo que esto no refleja el lente de ahora (el visor se atoro o la camara
     * se acaba de abrir): no se espera por un dato que no es de fiar.
     */
    const val MAX_STATE_AGE_MS = 500L

    /**
     * true mientras el lente esta buscando. Los demas estados no merecen espera: enfocado (pasivo o fijo), sin AF
     * (INACTIVE: lente fijo) o que ya se dio por vencido (NOT_FOCUSED/UNFOCUSED: esperar no lo arregla).
     */
    fun isScanning(afState: Int?): Boolean =
        afState == CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN || afState == CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN

    fun name(afState: Int?): String = when (afState) {
        null -> "?"
        CaptureResult.CONTROL_AF_STATE_INACTIVE -> "INACTIVE"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN -> "PASSIVE_SCAN"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED -> "PASSIVE_FOCUSED"
        CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN -> "ACTIVE_SCAN"
        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED -> "FOCUSED_LOCKED"
        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED -> "NOT_FOCUSED_LOCKED"
        CaptureResult.CONTROL_AF_STATE_PASSIVE_UNFOCUSED -> "PASSIVE_UNFOCUSED"
        else -> afState.toString()
    }
}

/**
 * Espera a que el lente deje de buscar y entonces llama a [then], una sola vez. Todo corre en el hilo que da [postDelayed]
 * (el principal): no bloquea, vuelve a mirar cada [FocusGatePolicy.POLL_MS].
 *
 * @param afState ultimo estado de enfoque conocido, o null si no hay dato fresco.
 */
class FocusGate(
    private val afState: () -> Int?,
    private val now: () -> Long,
    private val postDelayed: (delayMs: Long, action: () -> Unit) -> Unit,
    private val log: (String) -> Unit = {}
) {
    fun awaitSettled(then: () -> Unit) {
        val startedAt = now()
        fun step() {
            val state = afState()
            val waited = now() - startedAt
            val scanning = FocusGatePolicy.isScanning(state)
            if (scanning && waited < FocusGatePolicy.MAX_WAIT_MS) {
                postDelayed(FocusGatePolicy.POLL_MS) { step() }
                return
            }
            log("enfoque: estado=${FocusGatePolicy.name(state)} espera=$waited ms${if (scanning) " (vencio el plazo)" else ""}")
            then()
        }
        step()
    }
}
