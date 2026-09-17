package com.example.pmmovil.agent.update

import org.junit.Assert.fail
import org.junit.Test

class ReleaseManifestValidatorTest {

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Se esperaba una IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // La excepción es el resultado esperado.
        }
    }

    private val validRelease = ReleaseManifest(
        packageName = "com.example.pmmovil",
        versionCode = 2,
        versionName = "1.0.1",
        apkUrl = "https://updates.pmm.local/releases/pmmovil-2.apk",
        sha256 = "a".repeat(64),
        mandatory = false,
        releaseNotes = "Prueba"
    )

    @Test
    fun `acepta un manifiesto válido`() {
        ReleaseManifestValidator.validate(
            release = validRelease,
            currentVersionCode = 1,
            expectedPackageName = "com.example.pmmovil",
            expectedHost = "updates.pmm.local"
        )
    }

    @Test
    fun `rechaza un paquete diferente`() {
        assertIllegalArgument {
            ReleaseManifestValidator.validate(
                release = validRelease.copy(packageName = "com.example.otro"),
                currentVersionCode = 1,
                expectedPackageName = "com.example.pmmovil",
                expectedHost = "updates.pmm.local"
            )
        }
    }

    @Test
    fun `rechaza una versión que no es superior`() {
        assertIllegalArgument {
            ReleaseManifestValidator.validate(
                release = validRelease.copy(versionCode = 1),
                currentVersionCode = 1,
                expectedPackageName = "com.example.pmmovil",
                expectedHost = "updates.pmm.local"
            )
        }
    }

    @Test
    fun `rechaza APK por HTTP`() {
        assertIllegalArgument {
            ReleaseManifestValidator.validate(
                release = validRelease.copy(apkUrl = "http://updates.pmm.local/releases/pmmovil-2.apk"),
                currentVersionCode = 1,
                expectedPackageName = "com.example.pmmovil",
                expectedHost = "updates.pmm.local"
            )
        }
    }

    @Test
    fun `rechaza hash inválido`() {
        assertIllegalArgument {
            ReleaseManifestValidator.validate(
                release = validRelease.copy(sha256 = "invalid"),
                currentVersionCode = 1,
                expectedPackageName = "com.example.pmmovil",
                expectedHost = "updates.pmm.local"
            )
        }
    }
}
