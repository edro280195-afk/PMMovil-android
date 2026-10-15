package com.example.pmmovil.worker

import com.example.pmmovil.data.model.LabelCaptureStatus
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Casos de aceptacion de la seccion 8 de docs/ia-revision-fisica-evaluacion.md:
 * doble toque, respuesta perdida, caida de red, cierre del proceso y reintento
 * sin duplicados. Si alguna falla, una confirmacion puede duplicarse, perderse
 * o aplicarse sobre la partida equivocada.
 */
class LabelCaptureSyncPolicyTest {

    @get:Rule
    val carpeta = TemporaryFolder()

    private val capturaValida = "7f3c1a2e-9b48-4d61-8f05-2a7c6e1d4b90"

    // ── Clave de idempotencia ────────────────────────────────────────────────

    @Test
    fun `solo un UUID sirve de clave de idempotencia`() {
        assertTrue(LabelCaptureSyncPolicy.isValidCaptureId(capturaValida))
        assertTrue(LabelCaptureSyncPolicy.isValidCaptureId(capturaValida.uppercase()))
    }

    @Test
    fun `un identificador improvisado no se acepta como captura`() {
        listOf(
            "",
            "   ",
            "captura-1",
            "7f3c1a2e9b484d618f052a7c6e1d4b90",
            "7f3c1a2e-9b48-4d61-8f05-2a7c6e1d4b9",
            "7f3c1a2e-9b48-4d61-8f05-2a7c6e1d4b90 ",
            "7f3c1a2e-9b48-4d61-8f05-2a7c6e1d4b9z",
            "../../etc/passwd"
        ).forEach { assertFalse(it, LabelCaptureSyncPolicy.isValidCaptureId(it)) }
    }

    // ── Dueño de la captura ──────────────────────────────────────────────────

    @Test
    fun `la captura se envia con la sesion de quien la tomo`() {
        assertTrue(LabelCaptureSyncPolicy.isOwner("eder.magana", "eder.magana"))
        assertTrue(LabelCaptureSyncPolicy.isOwner("Eder.Magana", " eder.magana "))
    }

    @Test
    fun `otro usuario en el equipo no envia la captura ajena`() {
        assertFalse(LabelCaptureSyncPolicy.isOwner("eder.magana", "israel.magana"))
        assertFalse(LabelCaptureSyncPolicy.isOwner("eder.magana", null))
        assertFalse(LabelCaptureSyncPolicy.isOwner("", "eder.magana"))
        assertFalse(LabelCaptureSyncPolicy.isOwner("   ", "eder.magana"))
    }

    // ── Congelar la peticion: el reintento manda lo mismo ────────────────────

    @Test
    fun `solo un borrador sin peticion previa se congela`() {
        assertTrue(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.DRAFT, null, null))
    }

    @Test
    fun `una peticion ya congelada no se vuelve a armar`() {
        // Si se rearmara, el reintento mandaria otra foto u otros datos con el mismo UUID.
        assertFalse(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.DRAFT, "{}", null))
        assertFalse(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.DRAFT, null, "/datos/foto.jpg"))
        assertFalse(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.PENDING, null, null))
        assertFalse(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.CONFIRMED, null, null))
        assertFalse(LabelCaptureSyncPolicy.canFreeze(LabelCaptureStatus.BLOCKED, null, null))
    }

    // ── Reintentos ───────────────────────────────────────────────────────────

    @Test
    fun `lo transitorio se reintenta`() {
        listOf(408, 429, 500, 502, 503, 504).forEach {
            assertTrue(it.toString(), LabelCaptureSyncPolicy.isRetryableHttp(it, null))
        }
    }

    @Test
    fun `lo que no cambia con esperar no se reintenta`() {
        listOf(400, 401, 403, 404, 409, 410, 413, 422).forEach {
            assertFalse(it.toString(), LabelCaptureSyncPolicy.isRetryableHttp(it, null))
        }
    }

    @Test
    fun `falta la migracion y el reintento no ayuda`() {
        // La captura se conserva pendiente, pero sin golpear al servidor en bucle.
        listOf("MIGRATION_REQUIRED", "IMAGE_VALIDATION_UNAVAILABLE", "RECOVERY_REQUIRED").forEach { code ->
            assertFalse(code, LabelCaptureSyncPolicy.isRetryableHttp(500, code))
            assertFalse(code, LabelCaptureSyncPolicy.isRetryableHttp(503, code))
            assertFalse(code, LabelCaptureSyncPolicy.requiresNewComparison(code))
        }
    }

    @Test
    fun `editaron la partida en otro equipo y hay que volver a comparar`() {
        listOf("SNAPSHOT_CONFLICT", "CAPTURE_CONFLICT").forEach { code ->
            assertTrue(code, LabelCaptureSyncPolicy.requiresNewComparison(code))
            assertFalse(code, LabelCaptureSyncPolicy.isRetryableHttp(409, code))
            assertFalse(code, LabelCaptureSyncPolicy.isRetryableHttp(500, code))
        }
        assertFalse(LabelCaptureSyncPolicy.requiresNewComparison(null))
        assertFalse(LabelCaptureSyncPolicy.requiresNewComparison("OTRO_CODIGO"))
    }

    @Test
    fun `se cayo la red y se vuelve a intentar`() {
        assertTrue(LabelCaptureSyncPolicy.isRetryableNetwork(IOException("unexpected end of stream")))
        assertTrue(LabelCaptureSyncPolicy.isRetryableNetwork(SocketTimeoutException("timeout")))
        assertTrue(LabelCaptureSyncPolicy.isRetryableNetwork(IOException("canceled", IOException("reset"))))
    }

    @Test
    fun `un certificado o una respuesta corrupta no se reintentan a ciegas`() {
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(SSLHandshakeException("handshake")))
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(SSLPeerUnverifiedException("peer")))
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(IllegalStateException("estado")))
        // El motivo real suele venir envuelto en un IOException de la capa de red.
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(IOException(SSLHandshakeException("handshake"))))
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(IOException(JsonParseException("json"))))
        assertFalse(LabelCaptureSyncPolicy.isRetryableNetwork(IOException(MalformedJsonException("json"))))
    }

    // ── Respuesta perdida o cruzada ──────────────────────────────────────────

    @Test
    fun `la confirmacion se acepta cuando habla de esta captura`() {
        assertTrue(
            LabelCaptureSyncPolicy.matchesConfirmation(
                expectedCaptureId = capturaValida,
                expectedPartId = 4821,
                actualCaptureId = capturaValida,
                actualPartId = 4821,
                photoId = 90312,
                hasSnapshot = true
            )
        )
    }

    @Test
    fun `una respuesta de otra captura o partida no cuenta como exito`() {
        val otra = "11111111-2222-3333-4444-555555555555"
        fun confirma(
            actualCaptureId: String? = capturaValida,
            actualPartId: Int = 4821,
            photoId: Int? = 90312,
            hasSnapshot: Boolean = true
        ) = LabelCaptureSyncPolicy.matchesConfirmation(
            capturaValida, 4821, actualCaptureId, actualPartId, photoId, hasSnapshot
        )

        assertFalse("captura ajena", confirma(actualCaptureId = otra))
        assertFalse("sin captura", confirma(actualCaptureId = null))
        assertFalse("otra partida", confirma(actualPartId = 4822))
        assertFalse("partida en cero", confirma(actualPartId = 0))
        assertFalse("sin foto", confirma(photoId = null))
        assertFalse("foto en cero", confirma(photoId = 0))
        assertFalse("foto negativa", confirma(photoId = -1))
        assertFalse("sin snapshot", confirma(hasSnapshot = false))
    }

    // ── La foto no sale del directorio privado ───────────────────────────────

    @Test
    fun `la foto de la captura vive en el directorio privado`() {
        val directorio = carpeta.newFolder("capturas")
        val foto = File(directorio, "$capturaValida.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertTrue(LabelCaptureSyncPolicy.isDirectChild(directorio, foto))
    }

    @Test
    fun `un salto con dos puntos no saca el archivo del directorio`() {
        val directorio = carpeta.newFolder("capturas")
        val afuera = carpeta.newFile("secreto.jpg")
        assertFalse(LabelCaptureSyncPolicy.isDirectChild(directorio, afuera))
        assertFalse(
            LabelCaptureSyncPolicy.isDirectChild(directorio, File(directorio, "../secreto.jpg"))
        )
        // Un subdirectorio tampoco es hijo directo: la ruta debe ser plana.
        val anidada = File(carpeta.newFolder("capturas", "adentro"), "foto.jpg")
        assertFalse(LabelCaptureSyncPolicy.isDirectChild(directorio, anidada))
    }

    @Test
    fun `el nombre de archivo canonico es el que decide`() {
        val directorio = carpeta.newFolder("capturas")
        val foto = File(directorio, "foto.jpg").apply { writeBytes(byteArrayOf(9)) }
        val rodeo = File(directorio, "adentro/../foto.jpg")
        assertEquals(foto.canonicalFile, rodeo.canonicalFile)
        assertTrue(LabelCaptureSyncPolicy.isDirectChild(directorio, rodeo))
    }
}
