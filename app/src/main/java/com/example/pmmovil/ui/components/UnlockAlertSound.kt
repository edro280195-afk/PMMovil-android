package com.example.pmmovil.ui.components

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Pitido corto + voz (p.ej. "Entrada autorizada") para avisos de
 * desbloqueo/autorización aprobados por un supervisor. Mismo patrón de
 * ToneGenerator + TextToSpeech que ya usa RevisionBarcodeScreen para el
 * conteo por código de barras, pero reutilizable desde cualquier pantalla
 * (aquí: lista de pendientes y detalle de revisión).
 */
@Composable
fun rememberUnlockApprovedAlert(): (String) -> Unit {
    val context = LocalContext.current
    val appLocale = LocalConfiguration.current.locales[0]
    var speaker by remember { mutableStateOf<TextToSpeech?>(null) }
    val tone = remember { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }

    DisposableEffect(appLocale) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.let { tts ->
                    val result = tts.setLanguage(appLocale)
                    if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts.setLanguage(
                            if (appLocale.language == "en") {
                                Locale.ENGLISH
                            } else {
                                Locale.forLanguageTag("es-MX")
                            }
                        )
                    }
                    speaker = tts
                }
            }
        }
        onDispose {
            speaker = null
            instance?.stop()
            instance?.shutdown()
            tone.release()
        }
    }

    return { mensaje ->
        // Pitido sencillo primero, la voz entra justo después.
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        speaker?.speak(mensaje, TextToSpeech.QUEUE_FLUSH, null, "unlock-approved")
    }
}
