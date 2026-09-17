package com.example.pmmovil.ui.revision

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.pmmovil.ui.scanner.BarcodeFormatSets
import com.example.pmmovil.ui.scanner.BarcodeVisualCandidate
import com.example.pmmovil.ui.scanner.ScanDistance
import com.example.pmmovil.ui.scanner.SmartBarcodeCameraView

@Composable
fun BarcodeCameraPanel(
    onBarcodeScanned: (String) -> Unit,
    onPotentialBarcodes: ((List<BarcodeVisualCandidate>) -> Unit)? = null,
    onCandidateSelectionRequired: ((Int) -> Unit)? = null,
    scanCooldownMillis: Long = 0L,
    barcodeFormats: Int = BarcodeFormatSets.WAREHOUSE_PRODUCT_LABELS,
    subtitle: String = "Busca etiquetas 1D, QR, DataMatrix o PDF417",
    modifier: Modifier = Modifier
) {
    SmartBarcodeCameraView(
        onBarcodeScanned = onBarcodeScanned,
        onPotentialBarcodes = onPotentialBarcodes,
        onCandidateSelectionRequired = onCandidateSelectionRequired,
        title = "Lectura inteligente",
        subtitle = subtitle,
        barcodeFormats = barcodeFormats,
        continuous = true,
        scanCooldownMillis = scanCooldownMillis,
        minConsecutiveReads = 2,
        requireCandidateSelectionForMultiple = true,
        showClose = false,
        compact = true,
        // El estado y la confirmación viven fuera del video en la pantalla
        // de revisión; así el centro de la cámara permanece despejado.
        showStatusOverlay = false,
        startZoomRatio = 1f,
        // Etiquetas de caja a distancia variable: 1080p de analisis para
        // poder leerlas sin tener que acercarse a la estanteria.
        scanDistance = ScanDistance.Far,
        modifier = modifier
    )
}
