import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.example.pmmovil"
    compileSdk = 36
    compileSdkExtension = 19
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.isFile) {
            keystorePropertiesFile.inputStream().use(::load)
        }
    }
    val releaseSigningConfig = if (keystorePropertiesFile.isFile) {
        signingConfigs.create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            check(!storeFilePath.isNullOrBlank()) {
                "keystore.properties debe definir storeFile"
            }
            val uploadKeystore = rootProject.file(storeFilePath)
            check(uploadKeystore.isFile) {
                "No existe la keystore de release: ${uploadKeystore.absolutePath}"
            }
            storeFile = uploadKeystore
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
            check(!storePassword.isNullOrBlank()) {
                "keystore.properties debe definir storePassword"
            }
            check(!keyAlias.isNullOrBlank()) {
                "keystore.properties debe definir keyAlias"
            }
            check(!keyPassword.isNullOrBlank()) {
                "keystore.properties debe definir keyPassword"
            }
        }
    } else {
        null
    }
    val sentryDsnValue = providers.gradleProperty("PMMOVIL_SENTRY_DSN")
        .orElse(providers.environmentVariable("PMMOVIL_SENTRY_DSN"))
        .orElse("")
        .get()
    // Maps SDK for Android: mapa de "Camiones en camino" en Centro de Caseta.
    // Key propia de este producto -- NO es la misma que usa el backend para
    // Google Routes API (esa calcula ETA server-side, esta pinta el mapa en
    // el dispositivo). Restringida en GCP Console por SHA-1 + applicationId.
    val mapsApiKeyValue = providers.gradleProperty("PMMOVIL_MAPS_API_KEY")
        .orElse(providers.environmentVariable("PMMOVIL_MAPS_API_KEY"))
        .orElse("")
        .get()
    // Debug local: el API se ejecuta con el perfil Development en el puerto 5000.
    // Con `adb reverse tcp:5000 tcp:5000`, la tableta alcanza la PC por localhost.
    // Se puede sobrescribir con -PpmmApiBaseUrl=... para otra configuración.
    val debugApiBaseUrl = providers.gradleProperty("pmmApiBaseUrl")
        .orElse("http://localhost:5000/api/")
        .get()
    val releaseApiBaseUrl = providers.gradleProperty("pmmReleaseApiBaseUrl")
        .orElse("https://revisionapi.grupopmm.com/api/")
        .get()
    val networkTraceEnabled = providers.gradleProperty("pmmNetworkTrace")
        .orElse("false")
        .get()
        .toBoolean()
    // Inicio nuevo (visor de escaneo universal + Tablero de La Línea). Es el inicio por defecto desde la 1.1.10;
    // para volver al Bento de módulos clásico se compila con -PpmmNewHome=false sin tocar el código.
    val newHomeEnabled = providers.gradleProperty("pmmNewHome")
        .orElse("true")
        .get()
        .toBoolean()

    // Umbral de nitidez bajo el cual la camara de evidencia reintenta la foto (ver StillBurstPolicy). 200 esta calibrado
    // con fotos reales de tableta (borrosas de 10 a 67, normales de 500 a 10,000). En un telefono con buena camara casi
    // nada cae ahi, asi que para probar el reintento se sube con -PpmmStillRetryBelow=500 sin tocar el codigo.
    val stillRetryBelow = providers.gradleProperty("pmmStillRetryBelow")
        .orElse("200.0")
        .get()
        .toDouble()

    defaultConfig {
        applicationId = "com.example.pmmovil"
        minSdk = 28
        targetSdk = 36
        versionCode = 30
        versionName = "1.1.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["sentryDsn"] = sentryDsnValue
        manifestPlaceholders["mapsApiKey"] = mapsApiKeyValue

        // Etiqueta de entrada renderizada como imagen (^GF/Z64) en vez del ZPL de texto.
        // Cambiar a "false" para volver al path hand-coded de ZebraZplBuilder.
        buildConfigField("boolean", "USE_IMAGE_LABELS", "true")

        // Inicio nuevo: visor de escaneo universal y Tablero de La Línea en lugar del Bento de módulos.
        buildConfigField("boolean", "USE_NEW_HOME", newHomeEnabled.toString())

        // Reintento invisible de fotos de evidencia: umbral de nitidez (ver arriba).
        buildConfigField("double", "STILL_RETRY_BELOW", stillRetryBelow.toString())

        // Las lecturas pueden contener datos operativos; la traza de red se
        // habilita explícitamente con -PpmmNetworkTrace=true cuando se requiere
        // diagnosticar una instalación release.
        buildConfigField("boolean", "BARCODE_TRACE_ENABLED", "false")
        buildConfigField("boolean", "NETWORK_TRACE_ENABLED", networkTraceEnabled.toString())
    }

    buildTypes {
        debug {
            // El debug apunta al API local. En un emulador Android Studio usa
            // normalmente http://10.0.2.2:5000/api/ para llegar a la PC; con
            // adb reverse sí puede utilizarse http://localhost:5000/api/.
            buildConfigField(
                "String",
                "PMM_API_BASE_URL",
                "\"${debugApiBaseUrl.replace("\\\"", "\\\\\"")}\""
            )
        }
        release {
            // R8: había 78 MB de dex sin minificar. Lo que R8 puede borrar es
            // todo lo que no ve usado -- y lo que no ve son los usos por
            // reflexión, que están enumerados uno por uno en proguard-rules.pro.
            // Si algo falla SÓLO en release, el archivo de reglas es el primer
            // lugar donde mirar.
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseSigningConfig != null) {
                signingConfig = releaseSigningConfig
            }
            // Sólo arm64 en producción.
            //
            // El APK traía las cuatro ABIs: las de Intel pesaban 219 MB de los
            // 528 totales -- el 42 % del archivo que se instala en las tabletas
            // -- y sólo sirven en emuladores. armeabi-v7a se quita porque la
            // flota es toda Armor Pad Pro, que reporta arm64-v8a como ABI
            // principal; ningún equipo de campo es de 32 bits.
            //
            // OJO si algún día entra un equipo de 32 bits: no es que vaya lento,
            // es que no podrá instalar el APK. Habría que devolver
            // "armeabi-v7a" a esta lista.
            //
            // El debug conserva las cuatro para poder seguir usando el emulador
            // de Android Studio, que es x86_64.
            ndk {
                abiFilters += setOf("arm64-v8a")
            }
            // Las instalaciones de producción deben usar el API publicado.
            // Se puede sobrescribir para una prueba controlada con
            // -PpmmReleaseApiBaseUrl=..., pero el valor por defecto debe ser HTTPS.
            buildConfigField(
                "String",
                "PMM_API_BASE_URL",
                "\"${releaseApiBaseUrl.replace("\\\"", "\\\\\\\"")}\""
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }
    packaging {
        jniLibs {
            // OpenCV y Pdfium incluyen libc++_shared.so; ambos binarios arm64
            // son compatibles con páginas de 16 KB y solo debe quedar uno.
            pickFirsts += setOf("lib/**/libc++_shared.so")
        }
    }

}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.ahmer.pdfium)
    implementation(libs.ahmer.pdfviewer)
    implementation(libs.pdfbox.android)
    implementation(libs.material)
    implementation(libs.androidx.security.crypto)
    implementation(libs.sentry.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // ML Kit
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)
    // Detección facial local y empaquetada: no depende de descargar un modelo
    // la primera vez que se usa una tableta de bodega.
    implementation(libs.mlkit.face.detection)

    // ZXing — genera el QR embebido en el bitmap de la etiqueta (^GF)
    implementation(libs.zxing.core)

    // PaddleOCR local (on-device, ONNX Runtime + OpenCV)
    implementation(project(":ppocr-sdk"))

    // OpenCV: ya llegaba en runtime por ppocr-sdk, pero ese modulo lo declara
    // como implementation y por eso no lo expone. Se repite aqui para que el
    // laboratorio biometrico facial pueda usar FaceDetectorYN/FaceRecognizerSF
    // en tiempo de compilacion. Es el mismo artefacto: no agrega peso al APK.
    implementation(libs.opencv.android)

    // Coil
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // SignalR
    implementation(libs.signalr)

    // Firebase Cloud Messaging — push nativo (Revisión asignada, Entrada autorizada, etc.)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.kotlinx.coroutines.play.services)

    // Google Maps — mapa de "Camiones en camino" (pre-registro) en Centro de Caseta
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // Mapa 3D nativo de bodega -- DESACTIVADO
    //
    // Nunca llegó a usarse: no hay una sola referencia a SceneView en el
    // código, ni un .glb en assets. Pero arrastraba Filament + gltfio +
    // filament-utils, que son 27.8 MB de binarios nativos en el APK.
    // Descomentar el día que el mapa 3D exista de verdad.
    // implementation(libs.sceneview)

    // ARCore es opcional: Pixel y otros equipos compatibles usan anclas espaciales;
    // el CT60 o Armor conservan el lector CameraX con tarjetas 2D.
    implementation("com.google.ar:core:1.56.0")

    // Baseline Profiles — instala el perfil AOT generado por el módulo :baselineprofile
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
