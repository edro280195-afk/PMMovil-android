package com.example.pmmovil.worker

/**
 * Cuándo y cómo se avisa al servidor que terminó la captura de una entrada (el API manda entonces el aviso de PRUEBA que
 * llega solo a desarrollo3). Sin Android de por medio, para probarlo con pruebas unitarias.
 *
 * Reglas de fondo:
 * - Primero las fotos: el aviso se manda cuando ya terminaron de subir (así el correo y el enlace las traen), pero sin
 *   esperar para siempre: pasados 15 minutos se manda igual y el enlace se completa solo.
 * - Un servidor que todavía no tiene el endpoint (API sin publicar: 404) o que lo rechaza no se reintenta: no hay nada que
 *   un reintento arregle.
 * - Un tropiezo de red o un 5xx sí se reintenta, con tope de 6 horas, porque el aviso no debe perderse por un tropiezo.
 */
internal object EntradaCaptureNoticePolicy {
    const val MAX_WAIT_FOR_PHOTOS_MILLIS = 15 * 60_000L
    const val MAX_RETRY_MILLIS = 6 * 60 * 60_000L

    enum class Outcome { DONE, RETRY, GIVE_UP }

    /** ¿Todavía conviene esperar a que suban las fotos de la entrada? */
    fun shouldWaitForPhotos(pendingPhotos: Int, elapsedMillis: Long): Boolean =
        pendingPhotos > 0 && elapsedMillis < MAX_WAIT_FOR_PHOTOS_MILLIS

    /** Qué hacer con la respuesta HTTP del servidor. */
    fun outcomeForHttp(code: Int, elapsedMillis: Long): Outcome = when {
        code in 200..299 -> Outcome.DONE
        PhotoSyncPolicy.isRetryableHttp(code) -> retryWhileInTime(elapsedMillis)
        else -> Outcome.GIVE_UP
    }

    /** Qué hacer si ni siquiera hubo respuesta (sin red, tiempo agotado). */
    fun outcomeForNetworkFailure(elapsedMillis: Long): Outcome = retryWhileInTime(elapsedMillis)

    private fun retryWhileInTime(elapsedMillis: Long): Outcome =
        if (elapsedMillis < MAX_RETRY_MILLIS) Outcome.RETRY else Outcome.GIVE_UP
}
