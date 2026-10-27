package com.example.pmmovil.ui.home

import com.example.pmmovil.data.scanner.LastScan
import com.example.pmmovil.data.scanner.LastScanElapsed
import com.example.pmmovil.ui.components.SignalKind

/**
 * Lo que pinta la píldora «Último escaneo» del inicio: qué se leyó, hace cuánto y, si ya llegó, la señal ACTUAL de la
 * pieza. La señal nunca viene de lo guardado: una de hace dos horas diría «Listo» de algo que ya cambió.
 */
data class HomeLastScanUi(
    val scan: LastScan,
    val elapsed: LastScanElapsed,
    val signal: SignalKind? = null
)
