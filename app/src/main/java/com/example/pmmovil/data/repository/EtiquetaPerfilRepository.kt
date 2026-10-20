package com.example.pmmovil.data.repository

import com.example.pmmovil.data.api.PMMovilApi
import com.example.pmmovil.data.model.EtiquetaPerfilCliente
import kotlinx.coroutines.CancellationException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resuelve si a un cliente se le ofrece leer las etiquetas con la cámara.
 *
 * El catálogo lo administra el panel y cambia una vez cada tantos meses, así que la respuesta se
 * guarda en memoria mientras viva el proceso: preguntarlo en cada apertura de entrada no aportaría
 * nada y sí metería una espera de red en el camino del revisador.
 *
 * **Si la consulta falla, el perfil no aplica.** Que el servidor no conteste no puede impedir
 * revisar: el revisador sigue con la revisión normal de siempre, que es lo que hacía antes de que
 * esto existiera. El fallo no se cachea, para que el siguiente intento lo vuelva a preguntar.
 */
@Singleton
class EtiquetaPerfilRepository @Inject constructor(
    private val api: PMMovilApi
) {
    private val cache = ConcurrentHashMap<String, EtiquetaPerfilCliente>()

    /**
     * Se pregunta por la referencia de la entrada, no por el cliente: al abrir una entrada siempre se
     * tiene la referencia, mientras que el número de cliente no viaja por todos los caminos (desde la
     * bandeja de notificaciones no está). El servidor resuelve el cliente.
     */
    suspend fun perfilDeEntrada(crefAgrupada: String?): EtiquetaPerfilCliente {
        val clave = crefAgrupada?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (clave.isEmpty()) return NO_APLICA

        cache[clave]?.let { return it }

        return try {
            val respuesta = api.getEtiquetaPerfilDeEntrada(clave)
            val perfil = respuesta.body()?.perfil
            if (!respuesta.isSuccessful || perfil == null) {
                NO_APLICA
            } else {
                // Solo se cachea una respuesta de verdad; un 404 o un 500 se vuelve a preguntar.
                cache[clave] = perfil
                perfil
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            NO_APLICA
        }
    }

    /** Para cuando el catálogo cambie mientras la app está abierta. */
    fun olvidar() = cache.clear()

    private companion object {
        val NO_APLICA = EtiquetaPerfilCliente(aplica = false)
    }
}
