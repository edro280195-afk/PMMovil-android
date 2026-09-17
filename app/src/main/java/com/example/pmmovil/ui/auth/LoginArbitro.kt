package com.example.pmmovil.ui.auth

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Los cinco caminos para entrar a PMMovil.
 *
 * El orden de la lista es el de la jerarquía de la pantalla, no el de una
 * cascada: los tres primeros escuchan AL MISMO TIEMPO y el que dispare primero
 * entra. Los dos últimos son desvíos que la persona elige a propósito.
 */
enum class LoginCanal {
    /** Cámara frontal. Requiere el gesto de "Ingresar" porque cuesta hardware. */
    ROSTRO,

    /** Gafete NFC. Armado siempre: no cuesta nada tenerlo escuchando. */
    NFC,

    /** Lector físico del equipo (gatillo). Armado siempre, es un receiver. */
    LECTOR,

    /** Cámara trasera leyendo el QR del gafete. Desvío manual. */
    QR_CAMARA,

    /** Usuario y contraseña. Desvío manual. */
    MANUAL
}

/** Etiqueta corta para los avisos de la pantalla. */
val LoginCanal.nombreCorto: String
    get() = when (this) {
        LoginCanal.ROSTRO -> "rostro"
        LoginCanal.NFC -> "gafete"
        LoginCanal.LECTOR -> "lector"
        LoginCanal.QR_CAMARA -> "código QR"
        LoginCanal.MANUAL -> "usuario"
    }

/**
 * Árbitro de los canales de acceso.
 *
 * EL PROBLEMA QUE RESUELVE. Rostro, gafete y lector escuchan en paralelo, y
 * cada uno tiene su propio camino hasta el servidor: el rostro va por
 * `RostroLoginViewModel`, los otros dos por `AuthViewModel`. Sin nadie en
 * medio, acercar el gafete mientras la cara ya se estaba verificando lanzaba
 * DOS peticiones de login a la vez, cada una capaz de navegar al home por su
 * cuenta. Dos máquinas de estado ciegas la una a la otra.
 *
 * LA REGLA. Sólo hay un turno. El primero que lo toma lo conserva hasta que su
 * intento se resuelve; mientras tanto los demás rebotan. No es una cola: lo
 * que llega tarde se descarta, porque un gafete acercado medio segundo después
 * de que la cara ya fue reconocida no es una petición pendiente, es ruido.
 *
 * EL HILO. Todo esto se toca únicamente desde el hilo principal. Los tres
 * canales llegan ahí de todos modos -- el lector por el receiver, el rostro
 * por su ViewModel, y el NFC hace el salto explícito después de leer la
 * etiqueta -- así que un solo hilo decide y no hacen falta atómicos.
 */
@Stable
class LoginArbitro {

    /** Quién tiene el turno ahora mismo, o null si la pantalla está esperando. */
    var canalEnCurso: LoginCanal? by mutableStateOf(null)
        private set

    /** Último canal que llegó a intentar, para poder decir qué falló. */
    var ultimoCanal: LoginCanal? by mutableStateOf(null)
        private set

    val ocupado: Boolean get() = canalEnCurso != null

    /**
     * Pide el turno para [canal].
     *
     * Devuelve true sólo si lo consiguió; el llamador no debe disparar nada si
     * recibe false.
     */
    fun tomarTurno(canal: LoginCanal): Boolean {
        if (canalEnCurso != null) return false
        canalEnCurso = canal
        ultimoCanal = canal
        return true
    }

    /** El intento terminó (bien o mal). La pantalla vuelve a escuchar a todos. */
    fun liberar() {
        canalEnCurso = null
    }
}
