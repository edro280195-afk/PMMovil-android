package com.example.pmmovil.data.preferences

import android.content.Context
import androidx.core.content.edit
import com.example.pmmovil.data.model.EntradaDraft
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntradaDraftStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val gson = Gson()
    private val preferences = context.getSharedPreferences(
        "PMMovilEntradaDrafts",
        Context.MODE_PRIVATE
    )
    private val listType = object : TypeToken<List<EntradaDraft>>() {}.type

    fun loadAll(): List<EntradaDraft> {
        val raw = preferences.getString(KEY_DRAFTS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<EntradaDraft>>(raw, listType)
        }.getOrDefault(emptyList()).sortedByDescending { it.lastEditAt }
    }

    fun save(draft: EntradaDraft) {
        val updated = loadAll()
            .filterNot { it.id == draft.id }
            .plus(draft)
            .sortedByDescending { it.lastEditAt }
            .take(MAX_DRAFTS)
        preferences.edit { putString(KEY_DRAFTS, gson.toJson(updated)) }
    }

    fun delete(id: String) {
        preferences.edit {
            putString(KEY_DRAFTS, gson.toJson(loadAll().filterNot { it.id == id }))
        }
    }

    companion object {
        private const val KEY_DRAFTS = "drafts"
        private const val MAX_DRAFTS = 10
    }
}
