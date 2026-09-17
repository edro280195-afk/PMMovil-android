package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistencia de desbloqueos de facturas (equivalente a localStorage en
 * la web). La web guarda `pmm_unlocks_{user}_{cref}` con entries que
 * tienen fecha de expiración:
 *
 *   { "01C2635625400000": "2026-06-12T20:30:00Z", "ENTRY_...": "..." }
 *
 * Aquí replicamos ese mismo formato para mantener la misma semántica:
 *  - `save(cnofact, expiryEpochMs)`: guarda un desbloqueo hasta una fecha
 *  - `isUnlocked(cnofact, now)`: consulta si un desbloqueo sigue vigente
 *  - `clear(cnofact)`: borra un desbloqueo
 */
@Singleton
class UnlockStore @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PMMovilUnlocks", Context.MODE_PRIVATE)

    private fun key(usuario: String, crefAgrupada: String) = "pmm_unlocks_${usuario}_${crefAgrupada}"

    fun save(usuario: String, crefAgrupada: String, cnofact: String, expiryEpochMs: Long) {
        val current = readAll(usuario, crefAgrupada)
        current[cnofact] = expiryEpochMs
        writeAll(usuario, crefAgrupada, current)
    }

    fun isUnlocked(usuario: String, crefAgrupada: String, cnofact: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = readAll(usuario, crefAgrupada)
        val expiry = current[cnofact] ?: current["ENTRY_$crefAgrupada"] ?: return false
        return expiry > nowMs
    }

    fun clear(usuario: String, crefAgrupada: String, cnofact: String) {
        val current = readAll(usuario, crefAgrupada)
        current.remove(cnofact)
        writeAll(usuario, crefAgrupada, current)
    }

    fun clearAll(usuario: String, crefAgrupada: String) {
        prefs.edit { remove(key(usuario, crefAgrupada)) }
    }

    private fun readAll(usuario: String, crefAgrupada: String): MutableMap<String, Long> {
        val raw = prefs.getString(key(usuario, crefAgrupada), "{}") ?: "{}"
        return try {
            val obj = JSONObject(raw)
            val map = mutableMapOf<String, Long>()
            obj.keys().forEach { k -> map[k] = obj.optLong(k, 0L) }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    private fun writeAll(usuario: String, crefAgrupada: String, map: Map<String, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit { putString(key(usuario, crefAgrupada), obj.toString()) }
    }
}
