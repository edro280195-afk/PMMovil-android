package com.example.pmmovil.data.notifications

import java.net.URLEncoder

/**
 * Avisos push de la carga de mercancía: una relación se bloqueó (escaneo rápido o etiqueta reimpresa) o un supervisor
 * la desbloqueó. Los textos y los datos los arma el API (`SalidaReglas.TextoPushBloqueo`, `TextoPushDesbloqueo` y
 * `DatosPush`); aquí vive lo que decide la app: en qué canal se muestra, a qué pantalla abre el toque y cuándo no hace
 * falta mostrarlo porque el aviso en vivo ya está a la vista. Sin Android ni Compose, para probarlo con pruebas unitarias.
 */
object PushSalida {
    /** Mismos valores que `SalidaReglas.PushTipoBloqueo` y `PushTipoDesbloqueo` del API: son el contrato entre los dos. */
    const val TIPO_BLOQUEO = "salida_bloqueo"
    const val TIPO_DESBLOQUEO = "salida_desbloqueo"

    /** Canal propio: se puede silenciar o ajustar aparte de las asignaciones de revisión. */
    const val CANAL_BLOQUEOS = "pmm_bloqueos"

    fun esDeSalida(type: String?): Boolean = type == TIPO_BLOQUEO || type == TIPO_DESBLOQUEO

    /** Canal de la notificación: el de bloqueos para los avisos de salida y [canalPorOmision] para todo lo demás. */
    fun canalPara(type: String?, canalPorOmision: String): String =
        if (esDeSalida(type)) CANAL_BLOQUEOS else canalPorOmision

    /**
     * Ruta que abre el toque en la notificación. Al supervisor lo lleva a «Autorizar relaciones» con la relación ya
     * abierta; al cargador, a «Cargar mercancía» con la relación cargada, lista para escanear. null si no es un aviso de
     * salida o no trae la relación.
     */
    fun rutaDestino(type: String?, relno: String?): String? {
        val relacion = relno?.trim().orEmpty()
        if (relacion.isEmpty()) return null
        val codificada = URLEncoder.encode(relacion, "UTF-8").replace("+", "%20")
        return when (type) {
            TIPO_BLOQUEO -> "relacion?barcode=$codificada"
            TIPO_DESBLOQUEO -> "salida?barcode=$codificada"
            else -> null
        }
    }

    /**
     * True si no hace falta mostrar la notificación porque el aviso en vivo (SignalR) de ese mismo suceso ya está en
     * pantalla: el bloqueo, si el supervisor tiene abierta «Autorizar relaciones»; el desbloqueo, si el cargador está
     * viendo la pantalla de bloqueo de esa misma relación. En cualquier otra pantalla el push es el único aviso.
     */
    fun debeSuprimir(
        type: String?,
        relno: String?,
        relacionAbierta: Boolean,
        salidaBloqueadaRelno: String?
    ): Boolean = when (type) {
        TIPO_BLOQUEO -> relacionAbierta
        TIPO_DESBLOQUEO -> {
            val visible = salidaBloqueadaRelno?.trim().orEmpty()
            visible.isNotEmpty() && visible.equals(relno?.trim().orEmpty(), ignoreCase = true)
        }
        else -> false
    }
}

/**
 * Qué avisos en vivo están a la vista ahora. Lo escriben las pantallas al reanudarse y pausarse; lo lee el servicio de
 * push para no repetir como notificación del sistema lo que el usuario ya está viendo.
 */
object AvisosEnPantalla {
    /** «Autorizar relaciones» está al frente: ahí el aviso de bloqueo sale como mensaje con tono y vibración. */
    @Volatile
    var relacionAbierta: Boolean = false

    /** La pantalla de bloqueo de esta relación está al frente en «Cargar mercancía»; null si no hay ninguna. */
    @Volatile
    var salidaBloqueadaRelno: String? = null
}
