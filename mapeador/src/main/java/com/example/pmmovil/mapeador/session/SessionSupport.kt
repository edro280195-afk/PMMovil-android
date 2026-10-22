package com.example.pmmovil.mapeador.session

import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

const val TAG = "MAPEADOR"

/** Resultado de auditar una ubicación frente a lo que dice el sistema. */
enum class AuditResult(val label: String) {
    OK("Está todo"),
    MISSING("Falta algo"),
    EXTRA("Hay otra cosa")
}

/** Bitácora CSV mínima: crea el archivo con su encabezado la primera vez. */
class CsvLog(private val file: File, private val header: String) {
    @Synchronized
    fun append(line: String) {
        runCatching {
            file.parentFile?.mkdirs()
            val isNew = !file.exists()
            file.appendText((if (isNew) header + "\n" else "") + line + "\n")
        }
    }
}

/** Un solo hilo de trabajo para la visión, con prioridad baja para no estorbar al dibujo. */
fun visionExecutor(name: String): ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, name).apply { priority = Thread.NORM_PRIORITY - 1 }
}

/** Escapa comas y comillas para una celda CSV. */
fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
