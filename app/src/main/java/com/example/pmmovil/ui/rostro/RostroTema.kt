package com.example.pmmovil.ui.rostro

import androidx.compose.ui.graphics.Color
import com.example.pmmovil.ui.theme.PmmAmber
import com.example.pmmovil.ui.theme.PmmBgCard
import com.example.pmmovil.ui.theme.PmmBgPrimary
import com.example.pmmovil.ui.theme.PmmBgSurface
import com.example.pmmovil.ui.theme.PmmBlueDeep
import com.example.pmmovil.ui.theme.PmmBlueLight
import com.example.pmmovil.ui.theme.PmmGreen
import com.example.pmmovil.ui.theme.PmmNavyBorder
import com.example.pmmovil.ui.theme.PmmRed
import com.example.pmmovil.ui.theme.PmmTextMuted
import com.example.pmmovil.ui.theme.PmmTextPrimary
import com.example.pmmovil.ui.theme.PmmTextSecondary

/**
 * Paleta de las superficies de reconocimiento facial.
 *
 * Es la misma paleta clara de PMMovil. La identidad facial toma la pantalla
 * completa para concentrar la atención, pero no cambia de producto ni de
 * lenguaje visual: sigue siendo azul PMM, superficie blanca y amarillo señal.
 */
object RostroTema {

    /** Fondo base del sistema PMMovil. */
    val Fondo = PmmBgPrimary

    /** Arriba del degradado: una superficie ligeramente más limpia. */
    val FondoAlto = PmmBgSurface

    /** Abajo del degradado: azul muy suave para dar profundidad. */
    val FondoBajo = PmmBgPrimary

    /** Fondo de tarjetas dentro de la superficie de identidad. */
    val Superficie = PmmBgSurface
    val SuperficieAlta = PmmBgCard

    val Linea = PmmNavyBorder

    /** Azul institucional PMM para el glifo y el aro. */
    val Glifo = PmmBlueDeep

    /** Azul claro para el barrido y el halo funcional. */
    val Brillo = PmmBlueLight

    val Ok = PmmGreen
    val Aviso = PmmAmber
    val Malo = PmmRed

    val Texto = PmmTextPrimary
    val TextoTenue = PmmTextSecondary
    val TextoMudo = PmmTextMuted
}
