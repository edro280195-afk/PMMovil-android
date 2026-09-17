package com.example.pmmovil.data.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bitácora acotada para investigar problemas de la revisión normal en equipos
 * release. No registra tokens, valores capturados ni cuerpos de respuesta.
 */
object RevisionDiagnostics {
    private const val TAG = "RevisionNormal"
    private const val MAX_LOG_BYTES = 512 * 1024L

    fun record(context: Context, event: String, throwable: Throwable? = null) {
        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd HH:mm:ss.SSS",
            Locale.US
        ).format(Date())
        val entry = buildString {
            append(timestamp)
            append(" | ")
            append(event)
            throwable?.let {
                appendLine()
                append(it.stackTraceToString())
            }
            appendLine()
        }

        runCatching {
            val file = diagnosticsFile(context)
            file.parentFile?.mkdirs()
            if (file.exists() && file.length() > MAX_LOG_BYTES) {
                val tail = file.readText().takeLast((MAX_LOG_BYTES / 2).toInt())
                file.writeText(tail)
            }
            file.appendText(entry)
        }.onFailure { writeError ->
            Log.e(TAG, "No se pudo escribir la bitácora local", writeError)
        }

        if (throwable == null) {
            Log.i(TAG, event)
        } else {
            Log.e(TAG, event, throwable)
        }
    }

    fun diagnosticsFile(context: Context): File {
        val externalDirectory = context.getExternalFilesDir("diagnostics")
        return File(
            externalDirectory ?: File(context.filesDir, "diagnostics"),
            "revision.log"
        )
    }
}
