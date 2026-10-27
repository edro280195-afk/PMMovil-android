package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.pmmovil.data.scanner.LastScan
import com.example.pmmovil.data.scanner.LastScanKind
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El último código que el usuario escaneó, para el «Último escaneo» del inicio. Guarda uno solo.
 *
 * Son claves planas y no un JSON a propósito: así no hay una clase más que Gson o R8 tengan que conocer. Lo que se escriba
 * aquí lo decide [com.example.pmmovil.data.scanner.LastScanRules]; este almacén no filtra nada.
 *
 * Storage ("PMMovilLastScan"):
 *   "code" → "E-017846/26"   "kind" → nombre de [LastScanKind]   "user" → clave del usuario   "at" → milisegundos
 */
@Singleton
class LastScanStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PMMovilLastScan", Context.MODE_PRIVATE)

    fun save(scan: LastScan) {
        prefs.edit {
            putString(KEY_CODE, scan.code)
            putString(KEY_KIND, scan.kind.name)
            putString(KEY_USER, scan.user)
            putLong(KEY_AT, scan.scannedAtMillis)
        }
    }

    /** Lo guardado, o `null` si no hay nada o quedó a medias. */
    fun read(): LastScan? {
        val code = prefs.getString(KEY_CODE, null)?.takeIf(String::isNotBlank) ?: return null
        val kind = prefs.getString(KEY_KIND, null)
            ?.let { name -> runCatching { LastScanKind.valueOf(name) }.getOrNull() } ?: return null
        val user = prefs.getString(KEY_USER, null)?.takeIf(String::isNotBlank) ?: return null
        val at = prefs.getLong(KEY_AT, -1L).takeIf { it >= 0 } ?: return null
        return LastScan(code = code, kind = kind, user = user, scannedAtMillis = at)
    }

    private companion object {
        const val KEY_CODE = "code"
        const val KEY_KIND = "kind"
        const val KEY_USER = "user"
        const val KEY_AT = "at"
    }
}
