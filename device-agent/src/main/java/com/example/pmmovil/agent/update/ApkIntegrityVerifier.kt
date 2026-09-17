package com.example.pmmovil.agent.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.example.pmmovil.agent.AgentConstants
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class ApkIntegrityVerifier(private val context: Context) {

    fun verify(apkFile: File, release: ReleaseManifest, expectedCertificateSha256: String) {
        require(apkFile.isFile) { "No existe el APK descargado" }
        require(calculateSha256(apkFile).equals(release.sha256, ignoreCase = true)) {
            "El hash SHA-256 del APK no coincide"
        }

        val packageInfo = packageInfoFromArchive(apkFile)
        require(packageInfo.packageName == AgentConstants.PMMOVIL_PACKAGE) {
            "El APK descargado pertenece a otro paquete"
        }
        require(packageInfo.longVersionCode == release.versionCode) {
            "El versionCode del APK no coincide con el manifiesto"
        }

        val certificateSha256 = certificateSha256(packageInfo)
        if (!certificateSha256.equals(expectedCertificateSha256, ignoreCase = true)) {
            throw IllegalArgumentException("El certificado del APK no coincide con el certificado aprobado")
        }
    }

    private fun packageInfoFromArchive(apkFile: File): PackageInfo {
        return context.packageManager.getPackageArchiveInfo(
            apkFile.absolutePath,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
            ?: throw IllegalArgumentException("Android no pudo leer el APK descargado")
    }

    private fun certificateSha256(packageInfo: PackageInfo): String {
        val signatures = packageInfo.signingInfo?.apkContentsSigners.orEmpty()
        val signature = signatures.firstOrNull()
            ?: throw IllegalArgumentException("El APK no tiene certificado de firma")
        return sha256Hex(
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        )
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var bytesRead: Int
            do {
                bytesRead = input.read(buffer)
                if (bytesRead > 0) digest.update(buffer, 0, bytesRead)
            } while (bytesRead >= 0)
        }
        return sha256Hex(digest.digest())
    }

    private fun sha256Hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            append("%02x".format(byte.toInt() and 0xff))
        }
    }
}
