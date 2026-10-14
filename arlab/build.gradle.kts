// Laboratorio de realidad aumentada. Módulo APARTE de :app, igual que :mapspike:
// no comparte código ni dependencias con la app de producción, así que no la toca
// ni le sube el peso. Se instala como "com.example.pmmovil.arlab" y convive con
// PMMovil en el mismo teléfono.
//
// Solo tiene sentido en un equipo certificado por ARCore (Pixel 9 Pro XL). En la
// flota actual (Ulefone Armor Pad Pro) ARCore no corre: eso es justo lo que este
// módulo sirve para demostrar con datos en vez de con suposiciones.
//
// Ver arlab/README.md y docs/ia-revision-fisica.md (sección 12).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pmmovil.arlab"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.example.pmmovil.arlab"
        // ARCore pide 24+; mantenemos el mismo piso que :app para no introducir
        // una diferencia más entre los dos módulos.
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-arlab"

        ndk {
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // Sin minificar a propósito: el laboratorio mide capacidades, no R8.
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

    // Mismas versiones de Compose que :app (BOM 2024.09.00).
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ARCore 1.56.0 = la misma versión de Google Play Services for AR que ya trae
    // el Pixel 9 Pro XL (verificado por adb: com.google.ar.core 1.56.262080393).
    implementation("com.google.ar:core:1.56.0")
}
