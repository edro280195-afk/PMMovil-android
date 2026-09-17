package com.example.pmmovil.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Tokens de motion para 2026: duraciones, easings y springs consistentes
 * en TODA la app. Usar siempre estos tokens en vez de números hardcoded.
 *
 *  - Emphasized: cambios de pantalla, hero
 *  - Medium:     enter/exit de listas, FAB reveal
 *  - Quick:      micro-interacciones (botón, toggle)
 *  - Slow:       loaders, shimmer
 *
 *  - EmphasizedDecelerate: entrada (se asienta con peso)
 *  - EmphasizedAccelerate: salida (se va con decisión)
 *  - Standard:             la mayoría de cosas
 */
object Motion {
    // ── Durations ──────────────────────────────────────────────────────────
    const val DurationQuick      = 150
    const val DurationMedium     = 280
    const val DurationEmphasized = 420
    const val DurationSlow       = 1400   // para shimmer/skeleton
    const val DurationStagger    = 45     // delay entre items en stagger

    // ── Easings (curvas Material 3 / iOS-style) ──────────────────────────
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard             = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate   = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
    val StandardAccelerate   = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val SharpOut             = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    // ── Springs (para movimientos orgánicos) ─────────────────────────────
    //   stiffness baja = más rebote
    //   damping ratio medium-bouncy = rebote sutil, no exagerado
    const val SpringStiffnessLow      = 200f
    const val SpringStiffnessMedium   = 500f
    const val SpringStiffnessHigh     = 1000f
    const val SpringDampingNoBounce   = 1.0f
    const val SpringDampingLowBounce  = 0.75f
    const val SpringDampingMedBounce  = 0.5f
    const val SpringDampingHighBounce = 0.15f
}
