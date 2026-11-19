package com.example.pmmovil.data.camera

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * La foto tal como viaja al API ([PhotoPipeline]: 2560 px, calidad 92), preparada UNA vez y guardada junto a la original.
 *
 * Reducir y comprimir una foto de 12 MP cuesta alrededor de un segundo en una tableta. Antes se hacía en el peor momento:
 * la Entrada Caseta la preparaba al subirla (después de Guardar, con el guardia ya en otra cosa y la caja esperando su
 * correo) y la Salida la guardaba en memoria como texto base64, que se perdía al salir de la pantalla. Ahora se prepara en
 * segundo plano en cuanto se toma y la subida solo lee el archivo listo.
 *
 * La preparada vive en `<carpeta de la foto>/.para-subir/` con el tamaño y la fecha de la original en el nombre: si la
 * original cambiara, el nombre ya no coincidiría y se prepararía otra.
 */
object FotoParaSubir {

    private const val TAG = "PMM-PHOTO"
    private const val CARPETA = ".para-subir"

    // Un candado por foto: si la captura y la subida piden la misma a la vez, la segunda espera a la primera y lee el
    // archivo listo en vez de decodificar 12 MP otra vez.
    private val candados = ConcurrentHashMap<String, Any>()

    /** Los bytes JPEG listos para el API, preparándolos si todavía no lo están. Null si la foto no existe o no se puede leer. */
    fun bytes(path: String): ByteArray? = synchronized(candados.getOrPut(path) { Any() }) {
        val original = File(path)
        if (!original.isFile) return null

        val preparada = preparadaDe(original)
        if (preparada.isFile && preparada.length() > 0) {
            runCatching { return preparada.readBytes() }
                .onFailure { Log.w(TAG, "No se pudo leer la foto preparada de $path; se prepara otra vez", it) }
        }

        val bytes = PhotoPipeline.aBytes(path) ?: return null
        guardar(preparada, bytes)
        bytes
    }

    /** Para los servidores que todavía solo reciben texto: la misma foto preparada, en base64 con su prefijo. */
    fun dataUri(path: String): String? =
        bytes(path)?.let { "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(it) }

    /** Prepara la foto sin devolverla: se llama en segundo plano al tomarla. */
    fun preparar(path: String): Boolean = bytes(path) != null

    // De una en una y fuera del hilo de la pantalla: dos a la vez solo pelearían por la misma CPU. Vive lo que vive la app,
    // así una foto tomada justo antes de cerrar la pantalla termina de prepararse.
    private val segundoPlano by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)) }

    /**
     * Prepara la foto en segundo plano en cuanto se toma (no espera ni falla): cuando toque subirla, el envío solo lee el
     * archivo listo. Si la subida llega antes, [bytes] espera a que termine esta (mismo candado) en vez de repetirla.
     */
    fun prepararEnSegundoPlano(path: String) {
        segundoPlano.launch { runCatching { preparar(path) } }
    }

    /** La foto ya subió o se descartó: su versión preparada sobra. */
    fun olvidar(path: String) {
        candados.remove(path)
        val original = File(path)
        val carpeta = File(original.parentFile ?: return, CARPETA)
        carpeta.listFiles()
            ?.filter { it.name.startsWith(original.name + ".") }
            ?.forEach { runCatching { it.delete() } }
    }

    /**
     * Borra las preparadas cuya foto original ya no existe (un cierre de la app entre subir y borrar). Barato: solo mira
     * la carpeta `.para-subir` de [carpetaDeFotos].
     */
    fun limpiarHuerfanas(carpetaDeFotos: File) {
        val carpeta = File(carpetaDeFotos, CARPETA)
        val haceUnaHora = System.currentTimeMillis() - 60 * 60 * 1000L
        carpeta.listFiles()?.forEach { preparada ->
            if (preparada.name.endsWith(".tmp")) {
                if (preparada.lastModified() < haceUnaHora) runCatching { preparada.delete() }
                return@forEach
            }
            val nombreOriginal = nombreOriginalDe(preparada.name) ?: return@forEach
            if (!File(carpetaDeFotos, nombreOriginal).exists()) runCatching { preparada.delete() }
        }
    }

    internal fun preparadaDe(original: File): File =
        File(File(original.parentFile, CARPETA), nombrePreparado(original.name, original.length(), original.lastModified()))

    internal fun nombrePreparado(nombre: String, largo: Long, modificada: Long): String = "$nombre.$largo-$modificada.jpg"

    /** "foto.jpg.123-456.jpg" -> "foto.jpg"; null si no es una preparada. */
    internal fun nombreOriginalDe(preparada: String): String? {
        if (!preparada.endsWith(".jpg")) return null
        val sinExtension = preparada.removeSuffix(".jpg")
        val punto = sinExtension.lastIndexOf('.')
        if (punto <= 0) return null
        val firma = sinExtension.substring(punto + 1)
        return if (Regex("""\d+-\d+""").matches(firma)) sinExtension.substring(0, punto) else null
    }

    private fun guardar(destino: File, bytes: ByteArray) {
        runCatching {
            destino.parentFile?.mkdirs()
            val temporal = File(destino.parentFile, destino.name + ".tmp")
            temporal.writeBytes(bytes)
            if (!temporal.renameTo(destino)) {
                temporal.delete()
            }
        }.onFailure { Log.w(TAG, "No se pudo guardar la foto preparada ${destino.name}", it) }
    }
}
