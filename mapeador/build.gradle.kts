// Mapeador de bodega: PROTOTIPO DE OFICINA. Módulo APARTE de :app, igual que :arlab y
// :mapspike: no comparte código ni dependencias con la app de producción, así que no la
// toca ni le sube el peso. Se instala como "com.example.pmmovil.mapeador" y convive con
// PMMovil en el mismo teléfono.
//
// Ver docs/mapeador-bodega.md y mapeador/README.md.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.pmmovil.mapeador"
    compileSdk = 36
    compileSdkExtension = 19

    defaultConfig {
        applicationId = "com.example.pmmovil.mapeador"
        // Mismo piso que :app y :arlab (ARCore pide 24+).
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-prototipo"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Solo arm64 (el Pixel): el APK pesa casi la mitad y se instala por Wi-Fi mucho más rápido.
            // Las pruebas con OpenCV corren en el teléfono, no en un emulador.
            abiFilters += setOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            // Sin minificar a propósito: es un prototipo de laboratorio.
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
    packaging {
        jniLibs {
            // OpenCV y ARCore pueden traer libc++_shared.so; solo debe quedar uno.
            pickFirsts += setOf("lib/**/libc++_shared.so")
        }
    }
    testOptions {
        unitTests {
            // android.util.Log y similares devuelven valores por defecto en pruebas JVM.
            isReturnDefaultValues = true
        }
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
    implementation(libs.kotlinx.coroutines.android)

    // ARCore 1.56.0 = misma versión que :arlab (el Pixel trae Play Services for AR más nuevo).
    implementation("com.google.ar:core:1.56.0")

    // OpenCV oficial de Maven Central (rasgos ORB, emparejado, solvePnPRansac, JPEG).
    implementation(libs.opencv.android)

    testImplementation(libs.junit)
    // org.json real para las pruebas JVM (en el SDK de Android viene sin implementar).
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
