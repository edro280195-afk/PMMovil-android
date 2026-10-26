package com.example.pmmovil.ui.revision

/** Lo que se concluye de las fotos de evidencia cuando el revisor pide terminar la revisión. */
internal enum class VeredictoDeFotos {
    /** Hay al menos una foto, ya en el servidor o todavía por subir: se puede terminar. */
    Suficiente,

    /** El servidor respondió completo y no hay ninguna foto: no se puede terminar. */
    SinFotos,

    /** Alguna consulta falló y no hay otra foto que lo compense: no se sabe, y no se termina a ciegas. */
    SinVerificar
}

/**
 * Decide si la revisión tiene evidencia fotográfica a partir de la consulta MÁS RECIENTE al servidor.
 *
 * @param totalEnServidor fotos que devolvió el servidor (entrada y facturas, sin repetir).
 * @param consultaCompleta true si todas las consultas respondieron; con una caída, un cero no prueba nada.
 * @param pendientesPorSubir fotos tomadas en este aparato que aún no suben.
 */
internal fun veredictoDeFotos(
    totalEnServidor: Int,
    consultaCompleta: Boolean,
    pendientesPorSubir: Int
): VeredictoDeFotos = when {
    totalEnServidor > 0 || pendientesPorSubir > 0 -> VeredictoDeFotos.Suficiente
    consultaCompleta -> VeredictoDeFotos.SinFotos
    else -> VeredictoDeFotos.SinVerificar
}
