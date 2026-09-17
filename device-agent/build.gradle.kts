import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val agentKeystorePropertiesFile = rootProject.file("device-agent/keystore.properties")
val agentKeystoreProperties = Properties().apply {
    if (agentKeystorePropertiesFile.isFile) {
        agentKeystorePropertiesFile.inputStream().use(::load)
    }
}
val agentReleaseKeystoreConfigured = agentKeystorePropertiesFile.isFile

android {
    namespace = "com.example.pmmovil.agent"
    compileSdk = 36

val configuredUpdateBaseUrl = providers.gradleProperty("pmmAgentUpdateBaseUrl").orNull
val debugUpdateBaseUrl = configuredUpdateBaseUrl ?: "https://updates.invalid/api/v1/"
val releaseUpdateBaseUrl = configuredUpdateBaseUrl ?: "https://updates.grupopmm.com/api/v1/"
    val pmmovilSigningCertificate = providers.gradleProperty("pmmovilSigningCertificateSha256")
        .orElse("8d32c40f47602f4f9acb8cbec23fa945d16228deae4dc444566e8a32d216e041")
        .get()
    val deviceToken = providers.gradleProperty("pmmAgentDeviceToken")
        .orElse("")
        .get()
    val agentReleaseSigningConfig = if (agentReleaseKeystoreConfigured) {
        signingConfigs.create("agentRelease") {
            val storeFilePath = agentKeystoreProperties.getProperty("storeFile")
            check(!storeFilePath.isNullOrBlank()) {
                "device-agent/keystore.properties debe definir storeFile"
            }
            val keystoreFile = rootProject.file(storeFilePath)
            check(keystoreFile.isFile) {
                "No existe la keystore del agente: ${keystoreFile.absolutePath}"
            }
            storeFile = keystoreFile
            storePassword = agentKeystoreProperties.getProperty("storePassword")
            keyAlias = agentKeystoreProperties.getProperty("keyAlias")
            keyPassword = agentKeystoreProperties.getProperty("keyPassword")
            check(!storePassword.isNullOrBlank()) {
                "device-agent/keystore.properties debe definir storePassword"
            }
            check(!keyAlias.isNullOrBlank()) {
                "device-agent/keystore.properties debe definir keyAlias"
            }
            check(!keyPassword.isNullOrBlank()) {
                "device-agent/keystore.properties debe definir keyPassword"
            }
        }
    } else {
        null
    }

    defaultConfig {
        applicationId = "com.example.pmmovil.agent"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "PMMOVIL_SIGNING_CERTIFICATE_SHA256",
            "\"${pmmovilSigningCertificate.replace("\\\"", "\\\\\\\"")}\""
        )
        buildConfigField(
            "String",
            "PMM_DEVICE_TOKEN",
            "\"${deviceToken.replace("\\\"", "\\\\\\\"")}\""
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "PMM_UPDATE_BASE_URL",
                "\"${debugUpdateBaseUrl.replace("\\\"", "\\\\\\\"")}\""
            )
        }
        release {
            isMinifyEnabled = false
            agentReleaseSigningConfig?.let { signingConfig = it }
            buildConfigField(
                "String",
                "PMM_UPDATE_BASE_URL",
                "\"${releaseUpdateBaseUrl.replace("\\\"", "\\\\\\\"")}\""
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
        buildConfig = true
    }
}

val verifyAgentReleaseSigning = tasks.register("verifyAgentReleaseSigning") {
    doLast {
        check(agentReleaseKeystoreConfigured) {
            "Falta device-agent/keystore.properties. No se permite distribuir " +
                "el agente release sin una keystore permanente."
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyAgentReleaseSigning)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
