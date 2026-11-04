package com.example.pmmovil.ui.salidacaseta

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Respaldo en la tableta de las fotos que el guardia ya tomó para cada salida.
 *
 * Las fotos viven como archivos en la tableta, pero la lista de cuáles son de qué relación vivía solo en memoria: si el
 * guardia salía de la pantalla, la app se reiniciaba o la red tardaba tanto que prefería salirse, las fotos quedaban
 * huérfanas y había que tomarlas de nuevo. Con este respaldo vuelven a aparecer en su relación al volver a entrar.
 */
object SalidaFotosBorrador {

    private val gson = Gson()
    private val tipo = object : TypeToken<Map<String, List<String>>>() {}.type

    /** Relación (o id del renglón) -> rutas de sus fotos. Las relaciones sin fotos no se guardan. */
    fun serializar(fotos: Map<String, List<String>>): String =
        gson.toJson(fotos.filterValues { it.isNotEmpty() })

    /**
     * Lo guardado, quedándose solo con las fotos cuyo archivo todavía existe ([existe]). Un respaldo ilegible se
     * ignora: perder el respaldo es mejor que impedir que la pantalla abra.
     */
    fun leer(json: String?, existe: (String) -> Boolean): Map<String, List<String>> {
        if (json.isNullOrBlank()) return emptyMap()
        val guardado: Map<String, List<String>> = runCatching { gson.fromJson<Map<String, List<String>>>(json, tipo) }
            .getOrNull()
            .orEmpty()
        return guardado
            .mapValues { (_, rutas) -> rutas.filter(existe) }
            .filterValues { it.isNotEmpty() }
    }
}
