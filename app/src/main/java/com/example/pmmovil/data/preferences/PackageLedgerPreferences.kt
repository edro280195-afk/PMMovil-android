package com.example.pmmovil.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.pmmovil.ui.revision.PackageLedgerCodec
import com.example.pmmovil.ui.revision.PackageTally
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * El libro de empaques contados, por entrada.
 *
 * Tiene que sobrevivir a que se cierre la app: una entrada de Freudenberg puede llevar decenas de
 * etiquetas y perder la cuenta a media revisión obligaría a empezar de nuevo. Se guarda como texto
 * plano con [PackageLedgerCodec] —un renglón por empaque, sin JSON ni reflexión— para que R8 no pueda
 * renombrarle nada en release, que es justo lo que ya rompió otras veces el guardado con Gson.
 *
 * **Límite conocido:** vive en este equipo. Si el revisador cambia de aparato, o se borran los datos,
 * el libro se pierde y la partida se volvería a contar desde cero. Evitarlo del todo pide guardar los
 * empaques en el servidor.
 *
 * Storage: `pmm_tally_{crefAgrupada}` → renglones de [PackageLedgerCodec].
 */
@Singleton
class PackageLedgerPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PMMovilPackageTally", Context.MODE_PRIVATE)

    fun get(crefAgrupada: String): PackageTally.Ledger =
        PackageLedgerCodec.decode(prefs.getString(key(crefAgrupada), null))

    fun set(crefAgrupada: String, ledger: PackageTally.Ledger) {
        prefs.edit {
            if (ledger.packages.isEmpty()) remove(key(crefAgrupada))
            else putString(key(crefAgrupada), PackageLedgerCodec.encode(ledger))
        }
    }

    /** Al cerrar la entrada: lo contado ya quedó escrito en las partidas. */
    fun clear(crefAgrupada: String) {
        prefs.edit { remove(key(crefAgrupada)) }
    }

    private fun key(crefAgrupada: String) = "pmm_tally_$crefAgrupada"
}
