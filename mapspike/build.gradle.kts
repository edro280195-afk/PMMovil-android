// Spike de mapa con MapLibre Native. Módulo APARTE de :app: no comparte código ni
// dependencias con la app de producción, así que no la afecta. Se instala como
// "com.example.pmmovil.mapspike". Ver mapspike/README.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    // Mismo plugin de Compose que :app (Kotlin 2.2.0) para probar la integración real.
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pmmovil.mapspike"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.example.pmmovil.mapspike"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"

        // La flota es arm64 (Armor Pad Pro); el resto de ABIs solo agrandaría el APK.
        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // Sin minificar a propósito: el spike mide el motor, no R8.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose con las MISMAS versiones que :app (BOM 2024.09.00): la prueba de integración
    // en ComposeMapActivity solo vale si no se sube ninguna versión.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // MapLibre Native, variante con AMBOS motores (Vulkan y OpenGL ES) para compararlos en
    // el mismo APK. En producción se elegiría uno solo: android-sdk (solo Vulkan, la
    // variante estándar) o android-sdk-opengl.
    implementation("org.maplibre.gl:android-sdk-vulkan-opengl:13.6.1")

    // Mismo OkHttp que la app. Solo se usa para CONTAR/BLOQUEAR peticiones de red
    // del mapa y así demostrar que funciona sin conexión.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
