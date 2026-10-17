package com.example.pmmovil

import android.app.Application
import androidx.camera.core.CameraXConfig
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.pmmovil.data.diagnostics.PmmCrashReporter
import com.example.pmmovil.data.diagnostics.RxJavaErrorHandler
import com.example.pmmovil.data.notifications.PmmFirebaseMessagingService
import com.example.pmmovil.data.security.SecurePreferencesFactory
import com.example.pmmovil.ui.camera.CameraOwner
import com.example.pmmovil.ui.camera.CameraXSetup
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient

@HiltAndroidApp
class PMMovilApplication : Application(), ImageLoaderFactory, CameraXConfig.Provider {

    override fun onCreate() {
        super.onCreate()
        // Primero de todo: un error de RxJava que nadie pueda recibir (el cliente
        // de SignalR tiene uno conocido al detener una conexión que no llegó a
        // negociar) mata el proceso si no hay quien lo registre.
        RxJavaErrorHandler.install()
        PDFBoxResourceLoader.init(this)
        PmmCrashReporter.install(this)
        PmmFirebaseMessagingService.ensureChannel(this)

        // La primera llamada a CameraX enumera las cámaras del equipo y lee
        // las características de cada una. En la Armor Pad Pro eso se pagaba
        // completo la primera vez que alguien abría el lector, con la persona
        // esperando enfrente. Aquí se paga mientras todavía está apareciendo
        // la pantalla de acceso. No bloquea: se resuelve en su propio scope.
        CameraOwner.prewarm(this)
    }

    /**
     * CameraX lee esta configuración en su primera inicialización. Ver
     * [CameraXSetup]: cierre inmediato del dispositivo al desenlazar.
     */
    override fun getCameraXConfig(): CameraXConfig = CameraXSetup.config()

    /**
     * ImageLoader global de Coil con autenticación JWT.
     *
     * Las fotos de bodega se sirven desde endpoints protegidos (`Foto/...`),
     * así que el cargador de imágenes debe enviar el mismo `Bearer` que el
     * resto de la app. Reutilizamos el token persistido por la capa de red.
     */
    override fun newImageLoader(): ImageLoader {
        val prefs = SecurePreferencesFactory.create(this)
        val authClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = prefs.getString("AUTH_TOKEN", null)
                val request = chain.request().newBuilder().apply {
                    if (!token.isNullOrBlank()) addHeader("Authorization", "Bearer $token")
                }.build()
                chain.proceed(request)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(authClient)
            .crossfade(true)
            .build()
    }
}
