package com.example.pmmovil.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Genera el Baseline Profile de PMMovil.
 *
 * Para producir/actualizar el perfil, con un dispositivo o emulador conectado:
 *   ./gradlew :app:generateBaselineProfile
 *
 * El perfil resultante queda en `app/src/release/generated/baselineProfiles/`
 * y se compila AOT al instalar, acelerando el arranque en frío y el primer scroll.
 *
 * El recorrido mínimo (arranque) ya optimiza lo más costoso: el cold start.
 * Si quieres cubrir más rutas calientes, navega aquí por las pantallas clave
 * (Entradas, escaneo, etc.) usando `device` de UI Automator.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.example.pmmovil") {
        pressHome()
        startActivityAndWait()
        // Espera a que la primera pantalla (login/home) quede estable.
        device.waitForIdle()
    }
}
