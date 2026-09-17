package com.example.pmmovil.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Guarda listas de texto (hoy: rutas de fotos locales de un borrador) en una
 * sola columna TEXT como JSON. Se usa JSON y no un separador simple porque una
 * ruta de archivo puede traer casi cualquier caracter.
 */
class StringListConverter {
    private val gson = Gson()
    private val listType = object : TypeToken<List<String>>() {}.type

    @TypeConverter
    fun fromList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return runCatching {
            gson.fromJson<List<String>>(value, listType)
        }.getOrNull() ?: emptyList()
    }
}
