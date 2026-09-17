package com.example.pmmovil.data.nfc

import android.app.Activity
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.util.Log
import java.io.IOException
import java.nio.charset.Charset

/**
 * Lectura/escritura de gafetes NFC para PMMovil.
 *
 * Usa NfcAdapter en modo "reader" (enableReaderMode) mientras la pantalla de
 * asociación o de login están visibles -- no requiere foreground dispatch ni
 * entradas de intent-filter en el manifest, y evita que el sistema muestre su
 * propio sonido/animación de "tag detectado" antes de que la app reaccione.
 *
 * El payload que se lee/escribe es siempre texto plano "usuario;pass_qr" --
 * el mismo par que ya va impreso en el QR del gafete. Nunca la contraseña
 * real de acceso: ver PMMovil.Api Controllers/AuthController.cs (mi-gafete).
 */
object NfcBadgeManager {

    private const val LOG_TAG = "PMM-NFC"

    @Volatile
    private var tagHandler: ((Tag) -> Unit)? = null

    private var guardedActivity: Activity? = null
    private var readerModeEnabled = false

    // PMMovil recibe la etiqueta directamente mediante Reader Mode, por lo
    // que Android no la despacha a "Tags" aunque la etiqueta sea NDEF. La
    // comprobación NDEF sí se conserva: los gafetes de PMM llevan el acceso
    // dentro de un registro NDEF y algunos equipos no lo exponen si se omite
    // esa fase. El sonido del sistema sí se suprime.
    private const val READER_FLAGS =
        NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

    fun isAvailable(activity: Activity): Boolean =
        NfcAdapter.getDefaultAdapter(activity) != null

    fun isEnabled(activity: Activity): Boolean =
        NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    /**
     * Toma el lector mientras la actividad completa de PMMovil está en primer
     * plano. No se detiene cuando LoginScreen navega a Home: eso evita que
     * Android entregue el mismo gafete a su manejador genérico.
     */
    fun startForegroundGuard(activity: Activity): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (guardedActivity === activity && readerModeEnabled) return true

        return runCatching {
            adapter.enableReaderMode(activity, { tag ->
                Log.i(LOG_TAG, "Etiqueta detectada: ${tagTech(tag)} ${tagUid(tag)}")
                tagHandler?.invoke(tag)
            }, READER_FLAGS, null)
            Log.i(LOG_TAG, "Reader Mode activo para ${activity.localClassName}")
            guardedActivity = activity
            readerModeEnabled = true
            true
        }.getOrElse { error ->
            Log.e(LOG_TAG, "No se pudo activar Reader Mode", error)
            false
        }
    }

    /** Libera el lector únicamente cuando PMMovil deja de estar en primer plano. */
    fun stopForegroundGuard(activity: Activity) {
        if (guardedActivity !== activity) return
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
        guardedActivity = null
        readerModeEnabled = false
        tagHandler = null
    }

    /** Registra el consumidor de la etiqueta para la pantalla visible. */
    fun setTagHandler(onTag: ((Tag) -> Unit)?) {
        tagHandler = onTag
    }

    /**
     * Compatibilidad para las pantallas NFC existentes. El guard global de
     * MainActivity es el dueño del modo lector; esta función sólo cambia el
     * consumidor actual.
     */
    fun startListening(activity: Activity, onTag: (Tag) -> Unit): Boolean {
        setTagHandler(onTag)
        return if (guardedActivity === activity && readerModeEnabled) {
            true
        } else {
            startForegroundGuard(activity)
        }
    }

    fun stopListening(activity: Activity) {
        if (guardedActivity === activity) {
            setTagHandler(null)
        } else {
            runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
        }
    }

    fun tagUid(tag: Tag): String =
        tag.id.joinToString(":") { byte -> "%02X".format(byte) }

    fun tagTech(tag: Tag): String = when {
        tag.techList.any { it.endsWith("NfcA") } -> "NFC-A"
        tag.techList.any { it.endsWith("NfcB") } -> "NFC-B"
        tag.techList.any { it.endsWith("NfcF") } -> "NFC-F"
        tag.techList.any { it.endsWith("NfcV") } -> "NFC-V"
        else -> "NFC"
    }

    /** Texto ya grabado en la etiqueta, o null si está en blanco / ilegible / sin NDEF. */
    fun readText(tag: Tag): String? {
        val ndef = Ndef.get(tag)
        if (ndef == null) {
            Log.w(LOG_TAG, "La etiqueta no expone NDEF. Tech=${tag.techList.joinToString()}")
            return null
        }
        return try {
            ndef.connect()
            val message = ndef.cachedNdefMessage ?: ndef.ndefMessage
            val text = message?.records?.firstOrNull()?.let(::decodeTextRecord)
            Log.i(LOG_TAG, if (text.isNullOrBlank()) "NDEF sin texto PMM" else "NDEF leído correctamente")
            text
        } catch (e: Exception) {
            Log.w(LOG_TAG, "readText falló: ${e::class.java.simpleName}")
            null
        } finally {
            runCatching { ndef.close() }
        }
    }

    sealed interface WriteResult {
        data object Success : WriteResult
        data object ReadOnly : WriteResult
        data object TooSmall : WriteResult
        data object Unsupported : WriteResult
        data class Failed(val reason: String) : WriteResult
    }

    /** Graba [text] como único registro NDEF de texto, sobrescribiendo lo que hubiera. */
    fun writeText(tag: Tag, text: String): WriteResult {
        val record = NdefRecord.createTextRecord("es", text)
        val message = NdefMessage(arrayOf(record))

        val ndef = Ndef.get(tag)
        if (ndef != null) {
            return try {
                ndef.connect()
                when {
                    !ndef.isWritable -> WriteResult.ReadOnly
                    ndef.maxSize < message.toByteArray().size -> WriteResult.TooSmall
                    else -> {
                        ndef.writeNdefMessage(message)
                        WriteResult.Success
                    }
                }
            } catch (e: IOException) {
                WriteResult.Failed(e.message ?: "Error de E/S al escribir")
            } catch (e: Exception) {
                WriteResult.Failed(e.message ?: "No se pudo escribir la etiqueta")
            } finally {
                runCatching { ndef.close() }
            }
        }

        // Etiqueta nueva/en blanco que todavía no trae formato NDEF.
        val formatable = NdefFormatable.get(tag)
        if (formatable != null) {
            return try {
                formatable.connect()
                formatable.format(message)
                WriteResult.Success
            } catch (e: Exception) {
                WriteResult.Failed(e.message ?: "No se pudo formatear la etiqueta")
            } finally {
                runCatching { formatable.close() }
            }
        }

        return WriteResult.Unsupported
    }

    private fun decodeTextRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) {
            return null
        }
        val payload = record.payload
        if (payload.isEmpty()) return null
        val languageCodeLength = payload[0].toInt() and 0x3F
        val isUtf16 = payload[0].toInt() and 0x80 != 0
        val charset = if (isUtf16) Charset.forName("UTF-16") else Charsets.UTF_8
        val start = 1 + languageCodeLength
        if (start > payload.size) return null
        return String(payload, start, payload.size - start, charset)
    }
}
