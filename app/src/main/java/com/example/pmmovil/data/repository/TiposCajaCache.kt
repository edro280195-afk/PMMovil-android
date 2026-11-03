package com.example.pmmovil.data.repository

import android.content.SharedPreferences
import com.example.pmmovil.data.model.CasetaEntradaCatalogItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Los últimos tipos de caja que el servidor entregó, guardados en la tableta.
 *
 * Son apenas ocho renglones que casi nunca cambian, pero la pantalla de Entrada los esperaba junto con el catálogo
 * completo (más de mil líneas de transporte y los clientes): si esa respuesta tardaba o fallaba, el selector de «Tipo
 * caja» quedaba vacío y el guardia no podía registrar la entrada. Con este respaldo el selector siempre tiene opciones,
 * aunque la red no responda; cuando sí responde, se actualiza.
 */
@Singleton
class TiposCajaCache @Inject constructor(
    private val prefs: SharedPreferences
) {
    fun leer(): List<CasetaEntradaCatalogItem> = deserializar(prefs.getString(CLAVE, null))

    fun guardar(tipos: List<CasetaEntradaCatalogItem>) {
        val validos = tipos.filter { it.id.isNotBlank() && it.nombre.isNotBlank() }
        // Un catálogo vacío es una respuesta anómala, no un cambio real: no debe borrar lo que sí funcionaba.
        if (validos.isEmpty()) return
        prefs.edit().putString(CLAVE, serializar(validos)).apply()
    }

    companion object {
        private const val CLAVE = "CASETA_TIPOS_CAJA"
        private val gson = Gson()
        private val tipo = object : TypeToken<List<CasetaEntradaCatalogItem>>() {}.type

        fun serializar(tipos: List<CasetaEntradaCatalogItem>): String = gson.toJson(tipos)

        /** Un respaldo ilegible se ignora: es mejor no tener respaldo que impedir que la pantalla abra. */
        fun deserializar(json: String?): List<CasetaEntradaCatalogItem> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching { gson.fromJson<List<CasetaEntradaCatalogItem>>(json, tipo) }
                .getOrNull()
                .orEmpty()
                .filter { it.id.isNotBlank() && it.nombre.isNotBlank() }
        }
    }
}
