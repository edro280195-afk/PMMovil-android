package com.example.pmmovil.ui.camera

import android.annotation.SuppressLint
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig
import androidx.camera.core.impl.Quirk
import androidx.camera.core.impl.QuirkSettings
import com.example.pmmovil.data.camera.CameraTrace

/**
 * Configuración de CameraX para toda la app (la aplica `PMMovilApplication`
 * como `CameraXConfig.Provider`).
 *
 * Lo único que cambia respecto al valor por defecto es forzar la quirk
 * `CloseCameraDeviceOnCameraGraphCloseQuirk` del backend CameraPipe. Sin
 * ella, al desenlazar los use cases CameraPipe mantiene el dispositivo
 * físico abierto durante 1 s "por si otra pantalla lo reutiliza" y lo cierra
 * después, en segundo plano. Ese cierre diferido es la raíz de la
 * inestabilidad medida en la Armor Pad Pro: la siguiente apertura caía
 * dentro o justo al final de ese cierre y el HAL de MediaTek devolvía una
 * sesión sin cuadros. Con la quirk el dispositivo se cierra en el acto al
 * desenlazar, y [CameraOwner] sabe exactamente cuándo terminó gracias a
 * `CameraManager.AvailabilityCallback`.
 *
 * Reutilizar el dispositivo no ahorraba nada real aquí: el HAL reinicia el
 * sensor de todos modos al reconfigurar la sesión, y abrir el dispositivo
 * cuesta ~30 ms.
 */
@SuppressLint("RestrictedApi")
object CameraXSetup {

    private const val QUIRK_CIERRE_INMEDIATO =
        "androidx.camera.camera2.compat.quirk.CloseCameraDeviceOnCameraGraphCloseQuirk"

    fun config(): CameraXConfig {
        val builder = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
        val quirk = quirkCierreInmediato()
        if (quirk != null) {
            builder.setQuirkSettings(QuirkSettings.withQuirksForceEnabled(setOf(quirk)))
        }
        return builder.build()
    }

    /**
     * La quirk se resuelve por nombre: es una clase interna de la librería y
     * referenciarla directamente rompería la compilación en una actualización
     * que la mueva. Si no existe, CameraX sigue con su comportamiento por
     * defecto y [CameraOwner] lo tolera (espera acotada).
     */
    private fun quirkCierreInmediato(): Class<out Quirk>? =
        runCatching { Class.forName(QUIRK_CIERRE_INMEDIATO).asSubclass(Quirk::class.java) }
            .onFailure { CameraTrace.w("CameraXSetup: quirk de cierre inmediato no disponible: ${it.message}") }
            .getOrNull()
}
