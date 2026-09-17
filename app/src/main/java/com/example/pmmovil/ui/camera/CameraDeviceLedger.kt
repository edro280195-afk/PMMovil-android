package com.example.pmmovil.ui.camera

/**
 * Libro de estado de los dispositivos de cámara del equipo, alimentado por
 * `CameraManager.AvailabilityCallback`.
 *
 * Sustituye a los temporizadores fijos que había antes ("esperar 1 s después
 * de soltar la cámara"). Un temporizador adivina; esto observa: Android avisa
 * cuando un dispositivo de cámara termina de cerrarse (`onCameraAvailable`), y
 * ese es exactamente el momento en que es seguro volver a abrirlo.
 *
 * POR QUÉ IMPORTA EN LA ARMOR PAD PRO. CameraX (CameraPipe) cierra el
 * dispositivo físico de forma asíncrona después de desenlazar, y el HAL de
 * MediaTek necesita ~110 ms para terminar ese cierre. Si la siguiente
 * apertura DEL MISMO DISPOSITIVO llega en medio de ese cierre, el HAL acepta
 * la sesión pero no entrega ni un cuadro: la pantalla se queda "Preparando
 * cámara" hasta vencer el plazo y después enseña un visor sobreexpuesto.
 *
 * EL BLOQUEO ES POR DISPOSITIVO, A PROPÓSITO. La primera versión bloqueaba
 * cualquier apertura mientras hubiera CUALQUIER cierre en vuelo, sin importar
 * el dispositivo. Eso rompía el relevo rostro→QR del login: la cámara
 * trasera (dispositivo 0) tenía que esperar a que la frontal (dispositivo 1)
 * terminara de cerrarse, aunque son sensores físicamente independientes y
 * Android los administra por separado. Medido en la Armor Pad Pro: ese
 * acoplamiento cruzado agregaba 2-3 s extra al abrir el QR justo después de
 * salir del rostro. `unavailable`, que ya es por dispositivo, es la única
 * señal que hace falta -- ni siquiera hace falta un libro de "cierres
 * pendientes" aparte: Android marca el dispositivo no disponible en cuanto
 * alguien lo abre y disponible otra vez cuando el HAL termina de cerrarlo,
 * sea quien sea quien lo abrió o lo cerró.
 *
 * Lógica pura (sin Android) para poder probarla. La sincronización la pone el
 * dueño ([CameraOwner]); aquí no hay hilos.
 */
class CameraDeviceLedger {

    /** Dispositivos que hoy no se pueden abrir (abiertos por alguien, o cerrándose). */
    private val unavailable = LinkedHashSet<String>()

    /** `onCameraAvailable`: el dispositivo terminó de cerrarse. */
    fun onAvailable(cameraId: String) {
        unavailable.remove(cameraId)
    }

    /** `onCameraUnavailable`: alguien (normalmente nosotros) lo abrió. */
    fun onUnavailable(cameraId: String) {
        unavailable.add(cameraId)
    }

    /**
     * Es seguro abrir [cameraId] ahora mismo. Con `null` (id desconocido) se
     * asume que sí, porque no hay nada concreto que esperar.
     */
    fun readyToOpen(cameraId: String?): Boolean =
        cameraId == null || cameraId !in unavailable

    /** Para la traza: qué está impidiendo abrir. */
    fun describeBlockers(cameraId: String?): String =
        if (cameraId != null && cameraId in unavailable) "ocupada=$cameraId" else "libre"

    /**
     * Cuando la espera de [cameraId] vence sin confirmación, el libro deja de
     * creer en ese cierre: de lo contrario cada apertura posterior de ESE
     * dispositivo volvería a esperar el plazo completo por un aviso que ya no
     * va a llegar (por ejemplo, si otra app se quedó con la cámara).
     */
    fun forget(cameraId: String?) {
        if (cameraId != null) unavailable.remove(cameraId)
    }
}
