package com.example.pmmovil.ui.revision

import com.example.pmmovil.data.model.TimerFinalizarResponse

/**
 * Qué le dice la app al revisor cuando toca «Terminar».
 *
 * El servidor cierra la sesión siempre, pero la entrada solo queda marcada como REVISADA FÍSICAMENTE (en el sistema de
 * escritorio: `traf.status = 2` y los tiempos de `traf3`) cuando todas sus facturas están completas y no queda ninguna
 * partida sin revisar. Antes la app tomaba cualquier `success` como «quedó revisada»: celebraba, volvía atrás y nadie
 * se enteraba de que la entrada seguía sin marcar (casos 01C263614350000 y 01C263616880000).
 */
object RevisionCierre {

    /**
     * `true` si la entrada quedó marcada como revisada. Un servidor anterior que no manda `revisionCompleta` se trata
     * como antes (quedó marcada): lo único que cambia es que un `false` explícito ya no se ignora.
     */
    fun entradaQuedoMarcada(respuesta: TimerFinalizarResponse): Boolean = respuesta.revisionCompleta != false

    /** El motivo que explica el servidor, o `null` si no mandó ninguno (la app usa su texto propio). */
    fun motivoDelServidor(respuesta: TimerFinalizarResponse): String? =
        respuesta.message?.trim()?.takeIf { it.isNotEmpty() }
}
