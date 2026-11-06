package com.example.pmmovil.data.printing

/**
 * Estado de una Zebra leído con el comando de host `~HS` (ZPL II Programming Guide, "Host Status Return").
 *
 * Por qué existe: "Probar conexión" solo abre y cierra el puerto 9100, y eso responde aunque la impresora esté
 * en pausa, sin etiquetas, con el cabezal abierto o con un envío anterior atorado. Con este estado la app puede
 * decir QUÉ le pasa a la Zebra en vez de quedarse esperando hasta un "timeout".
 */
data class ZebraPrinterStatus(
    val paperOut: Boolean,
    val paused: Boolean,
    val formatsInBuffer: Int,
    val bufferFull: Boolean,
    /** La impresora quedó con una etiqueta a medias (recibió ^XA pero no el ^XZ). */
    val partialFormat: Boolean,
    val headOpen: Boolean,
    val ribbonOut: Boolean,
    val thermalTransfer: Boolean,
    val labelsRemaining: Int
) {

    /**
     * Condición física por la que la Zebra NO va a imprimir; null si puede consumir trabajos.
     *
     * Solo incluye las que el propio equipo marca como impedimento. `partialFormat`, `bufferFull` y los
     * formatos en cola NO bloquean: son informativos y un falso positivo dejaría a la planta sin imprimir.
     */
    fun blockingProblem(): String? = when {
        headOpen ->
            "La Zebra tiene el cabezal abierto. Ciérralo y vuelve a imprimir."
        paperOut ->
            "La Zebra se quedó sin etiquetas o no detecta el rollo. Revisa el rollo y la pantalla de la impresora."
        ribbonOut && thermalTransfer ->
            "A la Zebra se le acabó el ribbon (la cinta). Cámbialo y vuelve a imprimir."
        paused ->
            "La Zebra está en pausa. Presiona el botón de pausa de la impresora para reanudarla y vuelve a imprimir."
        else -> null
    }

    /** Resumen para anexar al mensaje cuando un envío falla; siempre devuelve algo útil. */
    fun describe(): String {
        val facts = buildList {
            blockingProblem()?.let { add(it.removeSuffix(".")) }
            if (partialFormat) {
                add("tiene un envío anterior a medias (una etiqueta sin terminar de recibir)")
            }
            if (bufferFull) add("su memoria de recepción está llena")
            if (formatsInBuffer > 0) add("tiene $formatsInBuffer formato(s) pendientes en cola")
            if (labelsRemaining > 0) add("le faltan $labelsRemaining etiqueta(s) por imprimir")
        }
        return if (facts.isEmpty()) {
            "La Zebra reporta que está lista (sin pausa ni faltantes) y sin trabajos pendientes."
        } else {
            "Estado de la Zebra: ${facts.joinToString("; ")}."
        }
    }
}

/** Lee la respuesta de `~HS`: tres líneas entre STX/ETX, campos separados por comas. */
object ZebraHostStatusParser {

    /**
     * Devuelve null si la respuesta no tiene la forma documentada. Es deliberado: ante la duda se prefiere
     * "no sé el estado" (y se intenta imprimir) a inventar un impedimento y bloquear la impresión.
     */
    fun parse(raw: String): ZebraPrinterStatus? {
        val lines = raw
            .split('\r', '\n')
            .map { line -> line.filter { it.code >= 0x20 }.trim() } // quita STX (0x02) y ETX (0x03)
            .filter { it.isNotEmpty() }
        if (lines.size < 2) return null

        val first = lines[0].split(',').map(String::trim)
        val second = lines[1].split(',').map(String::trim)
        if (first.size < 8 || second.size < 8) return null

        // Línea 1: aaa,b,c,dddd,eee,f,g,h,iii,j,k,l
        val paperOut = first.flag(1) ?: return null
        val paused = first.flag(2) ?: return null
        val formats = first[4].toIntOrNull() ?: return null
        val bufferFull = first.flag(5) ?: return null
        val partialFormat = first.flag(7) ?: return null

        // Línea 2: mmm,n,o,p,q,r,s,t,uuuuuuuu,v,www
        val headOpen = second.flag(1) ?: return null
        val ribbonOut = second.flag(2) ?: return null
        val thermalTransfer = second.flag(3) ?: return null
        val labelsRemaining = second[7].toIntOrNull() ?: 0

        return ZebraPrinterStatus(
            paperOut = paperOut,
            paused = paused,
            formatsInBuffer = formats,
            bufferFull = bufferFull,
            partialFormat = partialFormat,
            headOpen = headOpen,
            ribbonOut = ribbonOut,
            thermalTransfer = thermalTransfer,
            labelsRemaining = labelsRemaining
        )
    }

    private fun List<String>.flag(index: Int): Boolean? = when (getOrNull(index)) {
        "0" -> false
        "1" -> true
        else -> null
    }
}
