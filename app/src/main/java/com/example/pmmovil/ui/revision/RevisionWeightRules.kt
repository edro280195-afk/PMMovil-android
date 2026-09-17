package com.example.pmmovil.ui.revision

/**
 * Conversión usada por Revisión Normal para conservar la regla del formulario
 * Visual FoxPro: el valor persistido en la partida está en libras.
 */
object RevisionWeightRules {
    const val LIBRAS_A_KILOS = 0.4536

    fun kilosFromLibras(libras: Double): Double = libras * LIBRAS_A_KILOS

    fun librasFromKilos(kilos: Double): Double = kilos / LIBRAS_A_KILOS
}
