package com.example.pmmovil.data.scanner

/**
 * Guarda un código que llegó antes de que la pantalla terminara de cargar y lo entrega cuando ya puede usarlo.
 *
 * Pasa al abrir una pantalla con el código ya cargado (desde el escáner universal): llega en la primera composición,
 * cuando la lista del patio o de relaciones todavía se está pidiendo, y buscar en ese momento daba «no encontré» o se
 * descartaba en silencio. Si llegan dos códigos antes de que se libere, gana el último: es el que el usuario ve.
 */
class PendingCode {
    private var value: String? = null

    /** Retiene el código hasta que se libere. */
    fun hold(code: String) {
        value = code
    }

    /** Entrega el código retenido, si hay, y lo olvida. */
    fun take(): String? = value.also { value = null }
}
