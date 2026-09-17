package com.example.pmmovil.agent.update

import android.content.Context
import com.example.pmmovil.agent.AgentConstants
import com.example.pmmovil.agent.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import org.json.JSONObject

class UpdateApiClient(private val context: Context) {

    private val identityStore = AgentIdentityStore(context)

    fun fetchReleaseManifest(): ReleaseManifest {
        val endpoint = buildEndpoint("mobile-updates/manifest")
        val connection = executeGet(endpoint)
        return try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            ReleaseManifest.fromJson(body)
        } finally {
            connection.disconnect()
        }
    }

    fun downloadApk(release: ReleaseManifest): File = synchronized(downloadLock) {
        val targetDirectory = File(context.filesDir, "updates").apply { mkdirs() }
        val temporaryFile = File(
            targetDirectory,
            "pmmovil-${release.versionCode}-${UUID.randomUUID()}.apk.download"
        )
        val targetFile = File(targetDirectory, "pmmovil-${release.versionCode}.apk")

        if (targetFile.exists() && !targetFile.delete()) {
            throw IOException("No se pudo reemplazar el APK anterior")
        }

        try {
            val connection = executeGet(release.apkUrl)
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(temporaryFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } finally {
                connection.disconnect()
            }
            if (!temporaryFile.renameTo(targetFile)) {
                throw IOException("No se pudo preparar el APK descargado")
            }
            return@synchronized targetFile
        } catch (exception: Exception) {
            temporaryFile.delete()
            throw exception
        }
    }

    fun sendHeartbeat(versionCode: Long, versionName: String?) {
        val connection = executePost(buildEndpoint("devices/heartbeat"))
        try {
            val body = JSONObject().apply {
                put("packageName", AgentConstants.PMMOVIL_PACKAGE)
                put("versionCode", versionCode)
                put("versionName", versionName ?: JSONObject.NULL)
            }.toString()
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
            }
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("Servidor de actualizaciones respondió HTTP $responseCode")
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun buildEndpoint(path: String): String {
        val baseUrl = BuildConfig.PMM_UPDATE_BASE_URL.trimEnd('/')
        return "$baseUrl/$path"
    }

    private fun executeGet(rawUrl: String): HttpURLConnection {
        val uri = runCatching { URI(rawUrl) }
            .getOrElse { throw IOException("URL inválida", it) }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("El agente solo permite conexiones HTTPS")
        }

        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CONNECTION_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json, application/vnd.android.package-archive")
        addAuthenticationHeaders(connection)

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            connection.disconnect()
            throw IOException("Servidor de actualizaciones respondió HTTP $responseCode")
        }
        return connection
    }

    private fun executePost(rawUrl: String): HttpURLConnection {
        val uri = runCatching { URI(rawUrl) }
            .getOrElse { throw IOException("URL inválida", it) }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            throw IOException("El agente solo permite conexiones HTTPS")
        }

        return (URL(rawUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            addAuthenticationHeaders(this)
        }
    }

    private fun addAuthenticationHeaders(connection: HttpURLConnection) {
        connection.setRequestProperty("Authorization", "Bearer ${BuildConfig.PMM_DEVICE_TOKEN}")
        connection.setRequestProperty("X-PMM-Device-Id", identityStore.deviceId)
    }

    companion object {
        private val downloadLock = Any()
        private const val CONNECTION_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 120_000
    }
}
