package com.example.pmmovil.agent.update

import java.net.URI

object ReleaseManifestValidator {

    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun validate(
        release: ReleaseManifest,
        currentVersionCode: Long,
        expectedPackageName: String,
        expectedHost: String
    ) {
        require(release.packageName == expectedPackageName) {
            "El manifiesto apunta a otro paquete"
        }
        require(release.versionCode > currentVersionCode) {
            "La versión remota no es superior a la instalada"
        }
        require(release.versionName.isNotBlank()) {
            "El manifiesto no contiene versionName"
        }
        require(release.sha256.matches(sha256Pattern)) {
            "El manifiesto contiene un SHA-256 inválido"
        }

        val apkUri = runCatching { URI(release.apkUrl) }
            .getOrElse { throw IllegalArgumentException("URL del APK inválida", it) }
        require(apkUri.scheme.equals("https", ignoreCase = true)) {
            "El APK debe descargarse mediante HTTPS"
        }
        require(apkUri.host.equals(expectedHost, ignoreCase = true)) {
            "El APK debe provenir del mismo servidor de actualizaciones"
        }
    }
}
