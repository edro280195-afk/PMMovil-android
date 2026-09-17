package com.example.pmmovil.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.pmmovil.data.barcode.BarcodeTrace
import com.example.pmmovil.data.model.PendingBarcodeScan
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import java.util.UUID

@Singleton
class BarcodeSessionStore @Inject constructor(
    private val preferences: SharedPreferences
) {
    private val gson = Gson()
    private val pendingType = object : TypeToken<List<PendingBarcodeScan>>() {}.type

    fun loadPending(crefAgrupada: String): List<PendingBarcodeScan> {
        val json = preferences.getString(pendingKey(crefAgrupada), null)
            ?: return emptyList()
        val parsedScans = runCatching {
            gson.fromJson<List<PendingBarcodeScan>>(json, pendingType)
        }.getOrDefault(emptyList())
        val scans = parsedScans.map { scan ->
            val safeId = scan.id.orEmpty().ifBlank { UUID.randomUUID().toString() }
            if (safeId == scan.id) scan else scan.copy(id = safeId)
        }
        BarcodeTrace.d(
            "STORE loadPending cref=$crefAgrupada count=${scans.size} " +
                "missingIds=${parsedScans.count { it.id.orEmpty().isBlank() }}"
        )
        return scans
    }

    fun savePending(crefAgrupada: String, scans: List<PendingBarcodeScan>) {
        BarcodeTrace.d(
            "STORE savePending cref=$crefAgrupada count=${scans.size} " +
                "ids=${scans.joinToString(separator = ",") { it.id.take(8) }}"
        )
        preferences.edit {
            if (scans.isEmpty()) {
                remove(pendingKey(crefAgrupada))
            } else {
                putString(pendingKey(crefAgrupada), gson.toJson(scans, pendingType))
            }
        }
    }

    fun clear(crefAgrupada: String) {
        BarcodeTrace.d("STORE clear cref=$crefAgrupada")
        preferences.edit {
            remove(pendingKey(crefAgrupada))
            remove(snapshotKey(crefAgrupada))
        }
    }

    private fun pendingKey(crefAgrupada: String) =
        "BARCODE_PENDING_${crefAgrupada.trim().uppercase()}"

    private fun snapshotKey(crefAgrupada: String) =
        "BARCODE_SNAPSHOT_${crefAgrupada.trim().uppercase()}"
}
