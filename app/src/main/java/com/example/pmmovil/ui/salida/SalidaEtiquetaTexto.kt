package com.example.pmmovil.ui.salida

import com.example.pmmovil.data.model.SalidaEtiqueta

/**
 * Cómo se lee un bulto en la lista de carga: un título corto y grande («Bulto 9 de 17») y, debajo, la referencia de la
 * entrada. La etiqueta de PMM trae `referencia posición-total`; cualquier otra (de proveedor, de un formato que esta
 * versión no conoce) se muestra tal cual, para no inventar un «bulto N de M» que no existe.
 */
internal data class EtiquetaTexto(val titulo: String, val detalle: String)

private val POSICION = Regex("""^(\S+)\s+(\d{1,6})-(\d{1,6})$""")

internal fun textoDeEtiqueta(etiqueta: SalidaEtiqueta): EtiquetaTexto {
    val crudo = etiqueta.textoEtiqueta.trim()
    val posicion = POSICION.matchEntire(crudo)
    if (posicion != null) {
        val (referencia, numero, total) = posicion.destructured
        return EtiquetaTexto(titulo = "Bulto $numero de $total", detalle = referencia)
    }
    val mostrado = etiqueta.textoPagina.trim().ifBlank { crudo }
    val referencia = etiqueta.creferencia.trim()
    return EtiquetaTexto(titulo = mostrado, detalle = referencia.takeIf { it.isNotEmpty() && it != mostrado }.orEmpty())
}

/**
 * Los bultos en el orden en que se buscan en la tarima: por entrada y, dentro de ella, por número (1, 2 … 10) y no por
 * texto (1, 10, 2 …), que es como los manda el servidor. Una etiqueta de otro formato va al final de su entrada.
 */
internal fun enOrdenNatural(etiquetas: List<SalidaEtiqueta>): List<SalidaEtiqueta> =
    etiquetas.sortedWith(
        compareBy<SalidaEtiqueta>(
            { POSICION.matchEntire(it.textoEtiqueta.trim())?.groupValues?.get(1) ?: it.creferencia.ifBlank { it.textoEtiqueta } },
            { POSICION.matchEntire(it.textoEtiqueta.trim())?.groupValues?.get(2)?.toIntOrNull() ?: Int.MAX_VALUE },
            { it.textoEtiqueta }
        )
    )

/** Lo mismo para un código que el escáner leyó y que no es de la relación (no hay `SalidaEtiqueta` de donde sacarlo). */
internal fun textoDeCodigo(codigo: String): EtiquetaTexto {
    val crudo = codigo.trim()
    // Un texto con «;» puede ser un gafete (usuario;clave): nunca se dibuja tal cual.
    if (';' in crudo) return EtiquetaTexto(titulo = "Código no reconocido", detalle = "")
    val posicion = POSICION.matchEntire(crudo)
    return if (posicion != null) {
        val (referencia, numero, total) = posicion.destructured
        EtiquetaTexto(titulo = "Bulto $numero de $total", detalle = referencia)
    } else {
        EtiquetaTexto(titulo = crudo, detalle = "")
    }
}

/** «2026-11-05T09:41:07» o «2026-11-05 09:41:07» → «09:41:07»; cualquier otra cosa → `null` (no se muestra nada). */
internal fun horaDeEscaneo(texto: String?): String? {
    val valor = texto?.trim().orEmpty()
    if (valor.isEmpty()) return null
    val hora = valor.substringAfter('T', valor.substringAfter(' ', ""))
    return Regex("""^\d{2}:\d{2}(:\d{2})?""").find(hora)?.value
}
