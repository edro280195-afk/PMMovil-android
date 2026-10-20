package com.example.pmmovil.ui.revision

/**
 * Junta los códigos que el video va decodificando mientras se encuadra una etiqueta, y solo da por
 * buenos los que aparecen en **dos cuadros distintos**.
 *
 * Hace falta por dos cosas medidas en el Pixel el 20-oct-2026, leyendo la etiqueta de envío VDA:
 *
 *  - **El video ve códigos que la foto no.** En una lectura el video decodificó `Q20000` y la foto no
 *    lo trajo, así que la cantidad se perdió y el empaque no se pudo contar. Cada cuadro reemplazaba
 *    la lista del anterior, de modo que lo visto un segundo antes se tiraba.
 *  - **El video se equivoca en los caracteres repetidos del Code 39.** En la misma sesión salieron
 *    `V0000305` donde el valor real es `V0000000305`, y basura suelta como `RTTT` o `2730101111111`.
 *
 * Pedir dos apariciones resuelve las dos: lo bueno se ve cuadro tras cuadro y la basura aparece una
 * sola vez. Es el resguardo más barato que distingue una lectura estable de un tropiezo del
 * decodificador, sin inventar reglas sobre el contenido.
 *
 * No es un acumulador eterno: [limpiar] lo vacía al cerrar la cámara o al pasar a otra etiqueta, para
 * no arrastrar códigos de un empaque al siguiente.
 */
class CodigosDelVideo(
    /** Cuántos cuadros distintos deben traer el código antes de darlo por bueno. */
    private val minimoCuadros: Int = 2
) {
    private val vistos = LinkedHashMap<String, Int>()

    /** Suma un cuadro. Devuelve true si cambió el conjunto de códigos confirmados. */
    fun agregar(codigos: List<String>): Boolean {
        val antes = confirmados()
        codigos.distinct().forEach { codigo ->
            if (codigo.isNotBlank()) vistos[codigo] = (vistos[codigo] ?: 0) + 1
        }
        return confirmados() != antes
    }

    /** Los códigos vistos al menos [minimoCuadros] veces, en el orden en que aparecieron. */
    fun confirmados(): List<String> =
        vistos.entries.filter { it.value >= minimoCuadros }.map { it.key }

    /** Todo lo visto, incluso una sola vez. Solo para la bitácora de depuración. */
    fun todos(): List<String> = vistos.keys.toList()

    fun limpiar() = vistos.clear()
}
