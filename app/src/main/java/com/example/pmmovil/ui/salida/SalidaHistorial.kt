package com.example.pmmovil.ui.salida

import com.example.pmmovil.data.model.SalidaEtiqueta

/** Un bulto que se escaneó en esta pantalla: cuál y a qué hora (HH:mm:ss del aparato). */
data class SalidaEscaneoLocal(val textoEtiqueta: String, val hora: String)

/** Un bulto ya escaneado, con su número de orden (1 = el primero que se escaneó). */
data class SalidaEscaneado(val etiqueta: SalidaEtiqueta, val numero: Int, val hora: String?)

/**
 * Los bultos escaneados en el orden en que se escanearon. Los que ya venían escaneados del servidor (se pausó y se
 * reanudó, o los cargó otra sesión) van primero, por su hora de escaneo y, si no la traen, en el orden de la lista; los
 * escaneados aquí van después, en el orden en que se leyeron.
 */
internal fun escaneadosEnOrden(
    etiquetas: List<SalidaEtiqueta>,
    escaneosLocales: List<SalidaEscaneoLocal>
): List<SalidaEscaneado> {
    val localPorTexto = escaneosLocales.associateBy { it.textoEtiqueta }
    val previos = etiquetas
        .withIndex()
        .filter { (_, etiqueta) -> etiqueta.escaneado && etiqueta.textoEtiqueta !in localPorTexto }
        .sortedWith(compareBy({ (_, etiqueta) -> etiqueta.timeScan.orEmpty() }, { it.index }))
        .map { it.value }
    val locales = escaneosLocales.mapNotNull { escaneo ->
        etiquetas.firstOrNull { it.textoEtiqueta == escaneo.textoEtiqueta && it.escaneado }?.let { it to escaneo.hora }
    }

    return buildList {
        previos.forEach { add(it to horaDeEscaneo(it.timeScan)) }
        locales.forEach { (etiqueta, hora) -> add(etiqueta to hora) }
    }.mapIndexed { index, (etiqueta, hora) -> SalidaEscaneado(etiqueta, index + 1, hora) }
}
