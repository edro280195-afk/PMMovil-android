package com.example.pmmovil.ui.asignaciones

import com.example.pmmovil.data.model.EntradaPendienteDto
import com.example.pmmovil.data.model.TomarCarpetaResponse

/** Lo que pasó al tocar «Tomar carpeta», ya interpretado para la pantalla. */
sealed interface TomaResultado {
    /** La carpeta quedó a nombre del revisador (o ya lo estaba: se conserva la hora de la toma original). */
    data class Tomada(val mensaje: String?, val yaEraTuya: Boolean) : TomaResultado

    /** Otra persona ya la tenía tomada: hay que decir quién y preguntar si se la quiere quedar. */
    data class Conflicto(val tomadaPor: String, val desde: String?, val mensaje: String?) : TomaResultado

    /** No se puede tomar (ya revisada, la está revisando otra persona, no existe…): se muestra el motivo. */
    data class NoSePuede(val mensaje: String?) : TomaResultado
}

/**
 * «Tomar carpeta»: el revisador registra que agarró la carpeta física de una entrada sin abrir la revisión ni arrancar el
 * cronómetro. El Monitor de revisadores muestra quién la trae y cuánto lleva sin iniciar, así que la libreta ya no hace
 * falta. Sin Android, para poder probarse.
 */
object CarpetaToma {
    /** Código del servidor cuando otra persona ya la tenía tomada y hay que confirmar. */
    const val CODIGO_TOMADA_POR_OTRO = "TOMADA_POR_OTRO"

    /** Código del servidor cuando el mismo revisador ya la tenía tomada (no se reinicia el reloj). */
    const val CODIGO_YA_ERA_TUYA = "YA_ERA_TUYA"

    fun interpretar(respuesta: TomarCarpetaResponse): TomaResultado = when {
        respuesta.success ->
            TomaResultado.Tomada(respuesta.mensaje, respuesta.codigo == CODIGO_YA_ERA_TUYA)

        respuesta.codigo == CODIGO_TOMADA_POR_OTRO ->
            TomaResultado.Conflicto(
                tomadaPor = respuesta.tomadaPor?.trim().orEmpty(),
                desde = respuesta.desde,
                mensaje = respuesta.mensaje
            )

        else -> TomaResultado.NoSePuede(respuesta.mensaje)
    }

    /** ¿La carpeta la trae el usuario? Las claves llegan a veces con relleno de espacios y sin distinguir mayúsculas. */
    fun esMia(tomadaPor: String?, usuario: String?): Boolean {
        val dueno = tomadaPor?.trim().orEmpty()
        val yo = usuario?.trim().orEmpty()
        return dueno.isNotEmpty() && dueno.equals(yo, ignoreCase = true)
    }

    /**
     * ¿Esta entrada de «mis pendientes» es una carpeta que el propio revisador tomó? El servidor lo dice con `tomadaPor`;
     * la copia local de la asignación (que sigue viéndose sin red) solo trae quién asignó, y una toma es una asignación
     * hecha por uno mismo.
     */
    fun esTomaPropia(entry: EntradaPendienteDto, usuario: String?): Boolean =
        esMia(entry.tomadaPor, usuario) || (entry.esAsignada == true && esMia(entry.asignSupervisor, usuario))

    /**
     * Si el botón «Tomar carpeta» tiene sentido para esta entrada: no es ya suya, no está terminada y nadie más la está
     * revisando en este momento (esa la bloquea [isEntryLocked]). Si otra persona la tomó sin iniciar sí se ofrece: el
     * servidor le avisa quién la tiene y le deja quedársela.
     */
    fun puedeTomar(entry: EntradaPendienteDto, usuario: String?): Boolean =
        !esMia(entry.tomadaPor, usuario) &&
            calculateEntryStatus(entry).category != EntryCategory.COMPLETED &&
            !isEntryLocked(entry, usuario)

    /**
     * Cuánto lleva sin iniciar, medido por el servidor: «3 h 20 min», «12 min», «menos de 1 min». Sin dato, `null`. No se
     * calcula con el reloj de la tableta para no decir «hace 5 horas» por una diferencia de reloj con el servidor.
     */
    fun tiempoSinIniciar(segundos: Long?): String? {
        if (segundos == null) return null
        val minutos = segundos.coerceAtLeast(0) / 60
        return when {
            minutos < 1 -> "<1 min"
            minutos < 60 -> "$minutos min"
            minutos < 24 * 60 -> {
                val horas = minutos / 60
                val resto = minutos % 60
                if (resto == 0L) "$horas h" else "$horas h $resto min"
            }
            else -> "${minutos / (24 * 60)} d"
        }
    }
}
